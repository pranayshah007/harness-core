/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.SERVICE_TEMPLATE;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;

import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.migrations.OnPrimaryManagerMigration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.ConfigFile;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.yaml.YamlChangeSetHelper;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.yaml.EntityUpdateService;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SyncNewFolderForConfigFiles implements OnPrimaryManagerMigration {
  String DEBUG_LINE = "CONFIG_FILE_SYNC: ";
  @Inject YamlChangeSetHelper yamlChangeSetHelper;
  @Inject ConfigService configService;
  @Inject WingsPersistence wingsPersistence;
  @Inject EntityUpdateService entityUpdateService;
  @Inject YamlDirectoryService yamlDirectoryService;
  @Inject YamlChangeSetService yamlChangeSetService;
  List<String> yamlChangeSetId;

  @Override
  public void migrate() {
    log.info(DEBUG_LINE + "Starting migration for Config File");
    yamlChangeSetId = new ArrayList<>();
    try (HIterator<Account> accounts =
             new HIterator<>(wingsPersistence.createQuery(Account.class, excludeAuthority).fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();

        if (account == null) {
          log.info(DEBUG_LINE + "Account is null, continuing");
          continue;
        }
        log.info(DEBUG_LINE + "Starting migration for account {}", account.getAccountName());

        try (HIterator<Application> applications =
                 new HIterator<>(wingsPersistence.createQuery(Application.class)
                                     .filter(ApplicationKeys.accountId, account.getUuid())
                                     .fetch())) {
          while (applications.hasNext()) {
            Application application = applications.next();

            if (application == null) {
              log.info(DEBUG_LINE + "Application is null, skipping");
              continue;
            }
            log.info(DEBUG_LINE + "Starting migration for application {}", application.getName());

            YamlGitConfig ygs = yamlDirectoryService.weNeedToPushChanges(account.getUuid(), application.getUuid());
            if (ygs != null) {
              List<GitFileChange> gitFileChanges = new ArrayList<>();
              try (HIterator<ConfigFile> configFiles = new HIterator<>(wingsPersistence.createQuery(ConfigFile.class)
                                                                           .filter("appId", application.getUuid())
                                                                           .filter("accountId", account.getUuid())
                                                                           .fetch())) {
                while (configFiles.hasNext()) {
                  ConfigFile configFile = configFiles.next();

                  if (configFile == null) {
                    log.info(DEBUG_LINE + "Config File is null, skipping");
                    continue;
                  }
                  if (configFile.getEntityType() == ENVIRONMENT || configFile.getEntityType() == SERVICE_TEMPLATE) {
                    try {
                      addToYamlChangeSetForCreatingConfigFilesOnGit(
                          configFile, application.getUuid(), account.getUuid(), gitFileChanges);
                    } catch (Exception e) {
                      log.error(DEBUG_LINE + "Exception occurred while adding new config file.", e);
                      continue;
                    }
                    addToYamlChangeSetForDeletingConfigFilesOnGit(
                        configFile, application.getUuid(), account.getUuid(), gitFileChanges);
                  }
                }
              }
              pushYamlChangeSet(account.getUuid(), application.getUuid(), gitFileChanges);
            }
          }
        }
        log.info(DEBUG_LINE + "Migration done for account {}", account.getAccountName());
      }
    }
    log.info(DEBUG_LINE + "Pushed yaml changes {}", String.join(",", yamlChangeSetId));
  }

  private void pushYamlChangeSet(String accountId, String appId, List<GitFileChange> gitFileChanges) {
    if (gitFileChanges.isEmpty()) {
      log.info(DEBUG_LINE + "Yamlchangeset for account {} and app {} not created as config files are not present.",
          accountId, appId);
      return;
    }

    YamlChangeSet yamlChangeSet = obtainYamlChangeSetForNonFullSync(accountId, appId, gitFileChanges, true);
    yamlChangeSet = yamlChangeSetService.save(yamlChangeSet);
    yamlChangeSetId.add(yamlChangeSet.getUuid());
    log.info(DEBUG_LINE + "Yamlchangeset for account {} and app {} created with id [{}].", accountId, appId,
        yamlChangeSet.getUuid());
  }

  private void addToYamlChangeSetForDeletingConfigFilesOnGit(
      ConfigFile entity, String appId, String accountId, List<GitFileChange> gitFileChanges) {
    try {
      gitFileChanges.addAll(obtainEntityChangeSet(accountId, entity, ChangeType.DELETE));
    } catch (Exception ex) {
      log.error(
          String.format(DEBUG_LINE
                  + "Failed to create changeset for some config file sync for account %s and app %s while deleting.",
              accountId, appId),
          ex);
    }
  }

  private void addToYamlChangeSetForCreatingConfigFilesOnGit(
      ConfigFile entity, String appId, String accountId, List<GitFileChange> gitFileChanges) {
    try {
      gitFileChanges.addAll(obtainEntityChangeSet(accountId, entity, ChangeType.ADD));
    } catch (Exception ex) {
      log.error(
          String.format(DEBUG_LINE
                  + "Failed to create changeset for some config file sync for account %s and app %s while creating.",
              accountId, appId),
          ex);
    }
  }

  private YamlChangeSet obtainYamlChangeSetForNonFullSync(
      String accountId, String appId, List<GitFileChange> gitFileChangeList, boolean forcePush) {
    return YamlChangeSet.builder()
        .accountId(accountId)
        .status(YamlChangeSet.Status.QUEUED)
        .queuedOn(System.currentTimeMillis())
        .forcePush(forcePush)
        .gitFileChanges(gitFileChangeList)
        .appId(appId)
        .fullSync(false)
        .build();
  }

  private <T> List<GitFileChange> obtainEntityChangeSet(String accountId, T entity, ChangeType changeType) {
    List<GitFileChange> gitFileChanges =
        entityUpdateService.obtainEntityGitSyncFileChangeSet(accountId, null, entity, changeType);
    if (changeType == ChangeType.DELETE) {
      try {
        gitFileChanges.forEach(gitFileChange -> gitFileChange.setFilePath(modifyFilePath(gitFileChange.getFilePath())));
      } catch (Exception ex) {
        log.error(DEBUG_LINE + "Error in modifying file path.", ex);
      }
    }
    return gitFileChanges;
  }

  private String modifyFilePath(String filePath) {
    Pattern pattern = Pattern.compile(YamlType.CONFIG_FILE_OVERRIDE.getPathExpression());
    Matcher matcher = pattern.matcher(filePath);
    Pattern pattern1 = Pattern.compile(YamlType.CONFIG_FILE_OVERRIDE_CONTENT.getPathExpression());
    Matcher matcher1 = pattern1.matcher(filePath);

    if (matcher.matches() || matcher1.matches()) {
      return removeServiceFolderFromPath(filePath);
    }

    log.error(
        DEBUG_LINE + "Failed to generate changeSet for entity as file path {} wasn't generated correctly. ", filePath);
    throw new InvalidRequestException("Wrong file path generated.");
  }

  private String removeServiceFolderFromPath(String filePath) {
    StringBuilder stringBuilder = new StringBuilder(filePath);
    StringBuilder secondStringBuilder =
        new StringBuilder(stringBuilder.substring(0, stringBuilder.lastIndexOf(PATH_DELIMITER)));
    return secondStringBuilder.substring(0, secondStringBuilder.lastIndexOf(PATH_DELIMITER))
        + stringBuilder.substring(stringBuilder.lastIndexOf(PATH_DELIMITER), stringBuilder.length());
  }
}
