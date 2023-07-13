/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.beans.FeatureName.CDS_QUERY_OPTIMIZATION;
import static io.harness.beans.FeatureName.NOTIFY_GIT_SYNC_ERRORS_PER_APP;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.microservice.NotifyEngineTarget.GENERAL;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.EntityType.ACCOUNT;
import static software.wings.beans.EntityType.APPLICATION;
import static software.wings.beans.GitCommit.GIT_COMMIT_ALL_STATUS_LIST;
import static software.wings.beans.GitCommit.GIT_COMMIT_PROCESSED_STATUS;
import static software.wings.beans.yaml.GitCommandRequest.gitRequestTimeout;
import static software.wings.beans.yaml.YamlConstants.APPLICATIONS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.APPLICATION_FOLDER_PATH;
import static software.wings.beans.yaml.YamlConstants.ARTIFACT_SOURCES_FOLDER;
import static software.wings.beans.yaml.YamlConstants.CLOUD_PROVIDERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.COLLABORATION_PROVIDERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.DEFAULTS_YAML;
import static software.wings.beans.yaml.YamlConstants.GIT_YAML_LOG_PREFIX;
import static software.wings.beans.yaml.YamlConstants.GLOBAL_TEMPLATE_LIBRARY_FOLDER;
import static software.wings.beans.yaml.YamlConstants.LOAD_BALANCERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.NOTIFICATION_GROUPS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.beans.yaml.YamlConstants.SETUP_FOLDER;
import static software.wings.beans.yaml.YamlConstants.SOURCE_REPO_PROVIDERS_FOLDER;
import static software.wings.beans.yaml.YamlConstants.VERIFICATION_PROVIDERS_FOLDER;
import static software.wings.service.impl.GitConfigHelperService.cleanupRepositoryName;
import static software.wings.service.impl.yaml.YamlProcessingLogContext.BRANCH_NAME;
import static software.wings.service.impl.yaml.YamlProcessingLogContext.CHANGESET_ID;
import static software.wings.service.impl.yaml.YamlProcessingLogContext.GIT_CONNECTOR_ID;
import static software.wings.service.impl.yaml.YamlProcessingLogContext.WEBHOOK_TOKEN;
import static software.wings.yaml.gitSync.beans.YamlGitConfig.BRANCH_NAME_KEY;
import static software.wings.yaml.gitSync.beans.YamlGitConfig.GIT_CONNECTOR_ID_KEY;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.endsWith;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.startsWith;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SortOrder.OrderType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.NullSafeImmutableMap;
import io.harness.delegate.beans.TaskData;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.ChangeType;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ProcessTimeLogContext;
import io.harness.mongo.index.BasicDBUtils;
import io.harness.persistence.HIterator;
import io.harness.rest.RestResponse;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.CGConstants;
import software.wings.beans.EntityType;
import software.wings.beans.GitCommit;
import software.wings.beans.GitCommit.GitCommitKeys;
import software.wings.beans.GitConfig;
import software.wings.beans.GitConfig.UrlType;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.TaskType;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.GitConnectionErrorAlert;
import software.wings.beans.alert.GitSyncErrorAlert;
import software.wings.beans.trigger.WebhookSource;
import software.wings.beans.yaml.FullSyncChangeset;
import software.wings.beans.yaml.FullSyncError;
import software.wings.beans.yaml.GitCommand.GitCommandType;
import software.wings.beans.yaml.GitCommitRequest;
import software.wings.beans.yaml.GitDiffRequest;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.GitFileChange.Builder;
import software.wings.beans.yaml.YamlConstants;
import software.wings.beans.yaml.YamlType;
import software.wings.dl.WingsPersistence;
import software.wings.exception.YamlProcessingException.ChangeWithErrorMsg;
import software.wings.service.impl.AppLogContext;
import software.wings.service.impl.EntityTypeLogContext;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.trigger.WebhookEventUtils;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.impl.yaml.sync.GitSyncFailureAlertDetails;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.sync.GitSyncErrorService;
import software.wings.service.intfc.yaml.sync.GitSyncService;
import software.wings.service.intfc.yaml.sync.YamlService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.CryptoUtils;
import software.wings.yaml.YamlVersion;
import software.wings.yaml.directory.DirectoryPath;
import software.wings.yaml.directory.FolderNode;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.errorhandling.GitSyncError.GitSyncErrorKeys;
import software.wings.yaml.gitSync.GitSyncWebhook;
import software.wings.yaml.gitSync.GitSyncWebhook.GitSyncWebhookKeys;
import software.wings.yaml.gitSync.GitWebhookRequestAttributes;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.YamlChangeSet.Status;
import software.wings.yaml.gitSync.beans.YamlGitConfig;
import software.wings.yaml.gitSync.beans.YamlGitConfig.SyncMode;
import software.wings.yaml.gitSync.beans.YamlGitConfig.YamlGitConfigKeys;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import javax.ws.rs.core.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
/**
 * The type Yaml git sync service.
 */
@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class YamlGitServiceImpl implements YamlGitService {
  /**
   * The constant SETUP_ENTITY_ID.
   */
  public static final String SETUP_ENTITY_ID = "setup";
  public static final Integer PUSH_IF_NOT_HEAD_MAX_RETRY_COUNT = 3;

  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private YamlDirectoryService yamlDirectoryService;
  @Inject private YamlService yamlService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private YamlChangeSetService yamlChangeSetService;
  @Inject private SecretManager secretManager;
  @Inject private ExecutorService executorService;
  @Inject private DelegateService delegateService;
  @Inject private AlertService alertService;
  @Inject private SettingsService settingsService;
  @Inject private ManagerDecryptionService managerDecryptionService;
  @Inject private AppService appService;
  @Inject private YamlGitService yamlGitSyncService;
  @Inject YamlHelper yamlHelper;
  @Inject private WebhookEventUtils webhookEventUtils;
  @Inject private FeatureFlagService featureFlagService;
  @Inject GitSyncService gitSyncService;
  @Inject GitSyncErrorService gitSyncErrorService;
  @Inject GitConfigHelperService gitConfigHelperService;

  /**
   * Gets the yaml git sync info by entityId
   *
   * @return the rest response
   */
  @Override
  public YamlGitConfig get(String accountId, String entityId, EntityType entityType) {
    return wingsPersistence.createQuery(YamlGitConfig.class)
        .filter(YamlGitConfigKeys.accountId, accountId)
        .filter(YamlGitConfig.ENTITY_ID_KEY, entityId)
        .filter(YamlGitConfig.ENTITY_TYPE_KEY, entityType)
        .get();
  }

  @Override
  public PageResponse<YamlGitConfig> list(PageRequest<YamlGitConfig> req) {
    return wingsPersistence.query(YamlGitConfig.class, req);
  }

  @Override
  public YamlGitConfig save(YamlGitConfig ygs) {
    return save(ygs, true);
  }

  @Override
  public YamlGitConfig save(YamlGitConfig ygs, boolean performFullSync) {
    notNullCheck("application id cannot be empty", ygs.getAppId());

    GitConfig gitConfig = getGitConfig(ygs);
    notNullCheck("Git config does not exist", gitConfig);

    if (UrlType.ACCOUNT == gitConfig.getUrlType() && isBlank(ygs.getRepositoryName())) {
      throw new GeneralException("Account level git connector must have repository name set");
    }
    if (UrlType.ACCOUNT != gitConfig.getUrlType() && isNotBlank(ygs.getRepositoryName())) {
      throw new GeneralException("Repository level git connector must not have repository name set");
    }

    if (UrlType.ACCOUNT != gitConfig.getUrlType() && null != ygs.getRepositoryName()) {
      ygs.setRepositoryName(null);
    }

    ygs.setSyncMode(SyncMode.BOTH);
    String yamlGitConfigId = wingsPersistence.save(ygs);
    if (performFullSync) {
      executorService.submit(() -> fullSync(ygs.getAccountId(), ygs.getEntityId(), ygs.getEntityType(), true));
    }

    return wingsPersistence.get(YamlGitConfig.class, yamlGitConfigId);
  }

  @Override
  public GitConfig getGitConfig(YamlGitConfig ygs) {
    GitConfig gitConfig = null;
    if (EmptyPredicate.isNotEmpty(ygs.getGitConnectorId())) {
      SettingAttribute settingAttributeForGitConnector = settingsService.get(ygs.getGitConnectorId());
      if (settingAttributeForGitConnector == null) {
        log.info(GIT_YAML_LOG_PREFIX + "Setting attribute deleted with connector Id [{}]", ygs.getGitConnectorId());
        return null;
      }
      gitConfig = (GitConfig) settingAttributeForGitConnector.getValue();
      if (gitConfig != null) {
        gitConfig.setBranch(ygs.getBranchName());
        if (EmptyPredicate.isNotEmpty(gitConfig.getSshSettingId())) {
          SettingAttribute settingAttributeForSshKey = getAndDecryptSettingAttribute(gitConfig.getSshSettingId());
          gitConfig.setSshSettingAttribute(settingAttributeForSshKey.toDTO());
        }
      }
    } else {
      // This is to support backward compatibility. Should be removed once we move to using gitConnector completely
      if (EmptyPredicate.isNotEmpty(ygs.getSshSettingId())) {
        SettingAttribute settingAttributeForSshKey = getAndDecryptSettingAttribute(ygs.getSshSettingId());
        gitConfig = ygs.getGitConfig(settingAttributeForSshKey.toDTO());
      } else {
        gitConfig = ygs.getGitConfig(null);
      }
    }

    return gitConfig;
  }

  @Override
  public SettingAttribute getAndDecryptSettingAttribute(String sshSettingId) {
    SettingAttribute settingAttributeForSshKey = settingsService.get(sshSettingId);
    if (settingAttributeForSshKey != null) {
      HostConnectionAttributes attributeValue = (HostConnectionAttributes) settingAttributeForSshKey.getValue();
      List<EncryptedDataDetail> encryptionDetails =
          secretManager.getEncryptionDetails(attributeValue, GLOBAL_APP_ID, null);
      managerDecryptionService.decrypt(attributeValue, encryptionDetails);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(attributeValue, encryptionDetails);
      return settingAttributeForSshKey;
    }

    log.warn(GIT_YAML_LOG_PREFIX + "Could not find setting attribute");
    return null;
  }

  /**
   * Updates the yaml git sync info by object type and entitytId (uuid)
   *
   * @param ygs the yamlGitSync info
   * @return the rest response
   */
  @Override
  public YamlGitConfig update(YamlGitConfig ygs) {
    return save(ygs);
  }

  @Override
  public void fullSync(String accountId, String entityId, EntityType entityType, boolean forcePush) {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    log.info(GIT_YAML_LOG_PREFIX + "Performing git full-sync for account [{}] and entity [{}]", accountId, entityId);

    String appId = accountId.equals(entityId) ? GLOBAL_APP_ID : entityId;
    YamlGitConfig yamlGitConfig = yamlDirectoryService.weNeedToPushChanges(accountId, appId);

    if (yamlGitConfig != null) {
      try {
        FullSyncChangeset fullSyncChangeset = null;
        List<GitFileChange> deletedGitFileChanges = new ArrayList<>();

        if (EntityType.ACCOUNT == entityType) {
          // Handle everything except for application
          fullSyncChangeset = obtainAccountOnlyGitFileChanges(accountId, true);
          deletedGitFileChanges = obtainAccountOnlyGitFileChangeForDelete(accountId);

        } else if (APPLICATION == entityType) {
          // Fetch application changeSets. The reason for special handling is that with application level yamlGitConfig,
          // each app can refer to different yamlGitConfig
          Application app = appService.get(appId);
          if (app != null) {
            fullSyncChangeset = obtainApplicationYamlGitFileChanges(accountId, app);
            deletedGitFileChanges = asList(generateGitFileChangeForApplicationDelete(accountId, app.getName()));
          }
        }

        final List<GitFileChange> gitFileChanges = fullSyncChangeset.getGitFileChanges();
        if (gitFileChanges.size() > 0 && forcePush) {
          for (GitFileChange gitFileChange : deletedGitFileChanges) {
            gitFileChanges.add(0, gitFileChange);
          }
        }
        YamlChangeSet yamlChangeSet = obtainYamlChangeSet(accountId, appId, gitFileChanges, forcePush);

        discardGitSyncErrorForFullSync(accountId, appId);
        insertErrorDuringYamlCollection(accountId, appId, fullSyncChangeset.getYamlErrors());

        yamlChangeSetService.save(yamlChangeSet);
        final long processingTimeMs = stopwatch.elapsed(MILLISECONDS);
        try (ProcessTimeLogContext ignore = new ProcessTimeLogContext(processingTimeMs, OVERRIDE_ERROR);
             EntityTypeLogContext ignore1 = new EntityTypeLogContext(entityType, entityId, accountId, OVERRIDE_ERROR)) {
          log.info(GIT_YAML_LOG_PREFIX + "Performed git full-sync successfully");
        }
      } catch (Exception ex) {
        log.error(GIT_YAML_LOG_PREFIX + "Failed to perform git full-sync for account {} and entity {}",
            yamlGitConfig.getAccountId(), entityId, ex);
      }
    }
  }

  private void insertErrorDuringYamlCollection(String accountId, String appId, List<FullSyncError> yamlErrors) {
    emptyIfNull(yamlErrors).forEach(yamlError -> {
      gitSyncErrorService.upsertGitSyncErrors(yamlError.getGitFileChange(), yamlError.getError(), true, false);
    });
  }

  @Override
  public void syncForTemplates(String accountId, String appId) {
    YamlGitConfig yamlGitConfig = yamlDirectoryService.weNeedToPushChanges(accountId, appId);
    if (yamlGitConfig != null) {
      try {
        List<GitFileChange> gitFileChanges = new ArrayList<>();
        if (GLOBAL_APP_ID.equals(appId)) {
          gitFileChanges = obtainGlobalTemplates(accountId, true);
        } else {
          Application app = appService.get(appId);
          if (app != null) {
            gitFileChanges = obtainAppTemplateChanges(accountId, app);
          }
        }
        YamlChangeSet yamlChangeSet = obtainYamlChangeSetForNonFullSync(accountId, appId, gitFileChanges, true);
        yamlChangeSetService.save(yamlChangeSet);
      } catch (Exception ex) {
        log.error(
            GIT_YAML_LOG_PREFIX + "Failed to perform template sync for account {} and app {}", accountId, appId, ex);
      }
    } else {
      log.info("YamlGitConfig null for app {}", appId);
    }
  }

  private List<GitFileChange> obtainAccountOnlyGitFileChangeForDelete(String accountId) {
    List<GitFileChange> gitFileChanges = new ArrayList<>();

    gitFileChanges.add(generateGitFileChangeForDelete(accountId, CLOUD_PROVIDERS_FOLDER));
    gitFileChanges.add(generateGitFileChangeForDelete(accountId, ARTIFACT_SOURCES_FOLDER));
    gitFileChanges.add(generateGitFileChangeForDelete(accountId, COLLABORATION_PROVIDERS_FOLDER));
    gitFileChanges.add(generateGitFileChangeForDelete(accountId, LOAD_BALANCERS_FOLDER));
    gitFileChanges.add(generateGitFileChangeForDelete(accountId, VERIFICATION_PROVIDERS_FOLDER));
    gitFileChanges.add(generateGitFileChangeForDelete(accountId, NOTIFICATION_GROUPS_FOLDER));
    gitFileChanges.add(generateGitFileChangeForDelete(accountId, GLOBAL_TEMPLATE_LIBRARY_FOLDER));
    gitFileChanges.add(generateGitFileChangeForDelete(accountId, SOURCE_REPO_PROVIDERS_FOLDER));
    gitFileChanges.add(generateGitFileChangeForDelete(accountId, YamlConstants.GOVERNANCE_FOLDER));
    gitFileChanges.add(generateGitFileChangeForDelete(accountId, DEFAULTS_YAML));

    return gitFileChanges;
  }

  private GitFileChange generateGitFileChangeForDelete(String accountId, String entity) {
    return Builder.aGitFileChange()
        .withAccountId(accountId)
        .withChangeType(ChangeType.DELETE)
        .withFilePath(SETUP_FOLDER + "/" + entity)
        .build();
  }

  private GitFileChange generateGitFileChangeForApplicationDelete(String accountId, String appName) {
    return Builder.aGitFileChange()
        .withAccountId(accountId)
        .withChangeType(ChangeType.DELETE)
        .withFilePath(SETUP_FOLDER + "/" + APPLICATIONS_FOLDER + "/" + appName)
        .build();
  }

  private YamlChangeSet obtainYamlChangeSet(
      String accountId, String appId, List<GitFileChange> gitFileChangeList, boolean forcePush) {
    return YamlChangeSet.builder()
        .accountId(accountId)
        .status(Status.QUEUED)
        .queuedOn(System.currentTimeMillis())
        .forcePush(forcePush)
        .gitFileChanges(gitFileChangeList)
        .appId(appId)
        .fullSync(true)
        .retryCount(0)
        .build();
  }

  private YamlChangeSet obtainYamlChangeSetForNonFullSync(
      String accountId, String appId, List<GitFileChange> gitFileChangeList, boolean forcePush) {
    return YamlChangeSet.builder()
        .accountId(accountId)
        .status(Status.QUEUED)
        .queuedOn(System.currentTimeMillis())
        .forcePush(forcePush)
        .gitFileChanges(gitFileChangeList)
        .appId(appId)
        .fullSync(false)
        .retryCount(0)
        .build();
  }

  @Override
  public FullSyncChangeset obtainApplicationYamlGitFileChanges(String accountId, Application app) {
    DirectoryPath directoryPath = new DirectoryPath(SETUP_FOLDER);

    FolderNode applicationsFolder =
        new FolderNode(accountId, APPLICATIONS_FOLDER, Application.class, directoryPath.add(APPLICATIONS_FOLDER));

    yamlDirectoryService.doApplication(app.getUuid(), false, null, applicationsFolder, directoryPath);

    List<GitFileChange> gitFileChanges = new ArrayList<>();
    List<FullSyncError> fulllSyncError = new ArrayList<>();
    gitFileChanges = yamlDirectoryService.traverseDirectory(
        gitFileChanges, accountId, applicationsFolder, SETUP_FOLDER, true, false, fulllSyncError);

    return FullSyncChangeset.builder().gitFileChanges(gitFileChanges).yamlErrors(fulllSyncError).build();
  }

  private List<GitFileChange> obtainGlobalTemplates(String accountId, boolean includeFiles) {
    List<GitFileChange> gitFileChanges = new ArrayList<>();
    DirectoryPath directoryPath = new DirectoryPath(SETUP_FOLDER);
    FolderNode templateFolder = yamlDirectoryService.doTemplateLibrary(accountId, directoryPath.clone(), GLOBAL_APP_ID,
        GLOBAL_TEMPLATE_LIBRARY_FOLDER, YamlVersion.Type.GLOBAL_TEMPLATE_LIBRARY, false, Collections.EMPTY_SET);
    gitFileChanges = yamlDirectoryService.traverseDirectory(
        gitFileChanges, accountId, templateFolder, SETUP_FOLDER, includeFiles, true, new ArrayList<>());

    return gitFileChanges;
  }

  private List<GitFileChange> obtainAppTemplateChanges(String accountId, Application app) {
    DirectoryPath directoryPath = new DirectoryPath(SETUP_FOLDER);
    directoryPath.add(APPLICATIONS_FOLDER);
    DirectoryPath appPath = directoryPath.clone();
    appPath.add(app.getName());
    FolderNode appTemplates =
        yamlDirectoryService.doTemplateLibraryForApp(app, appPath.clone(), false, Collections.EMPTY_SET);

    List<GitFileChange> gitFileChanges = new ArrayList<>();
    return yamlDirectoryService.traverseDirectory(
        gitFileChanges, accountId, appTemplates, appPath.getPath(), true, false, new ArrayList<>());
  }

  private List<YamlChangeSet> obtainAllApplicationYamlChangeSet(
      String accountId, boolean forcePush, boolean onlyGitSyncConfiguredApps) {
    List<YamlChangeSet> yamlChangeSets = new ArrayList<>();
    List<Application> apps = appService.getAppsByAccountId(accountId);

    if (isEmpty(apps)) {
      return yamlChangeSets;
    }
    for (Application app : apps) {
      if (!onlyGitSyncConfiguredApps || gitSyncConfiguredForApp(app.getAppId(), accountId)) {
        final YamlChangeSet yamlChangeSet = obtainAppYamlChangeSet(accountId, forcePush, app);
        yamlChangeSets.add(yamlChangeSet);
      } else {
        log.info("Git Sync not configured for appId =[{}]. Skip generating changeset.", app.getAppId());
      }
    }

    return yamlChangeSets;
  }

  @Override
  public YamlChangeSet obtainAppYamlChangeSet(String accountId, boolean forcePush, Application app) {
    List<GitFileChange> gitFileChanges = obtainApplicationYamlGitFileChanges(accountId, app).getGitFileChanges();
    return obtainYamlChangeSet(accountId, app.getUuid(), gitFileChanges, forcePush);
  }

  private boolean gitSyncConfiguredForApp(String appId, String accountId) {
    return yamlDirectoryService.weNeedToPushChanges(accountId, appId) != null;
  }

  @Override
  public List<GitFileChange> performFullSyncDryRun(String accountId) {
    List<GitFileChange> gitFileChanges = new ArrayList<>();

    List<YamlChangeSet> yamlChangeSets = obtainChangeSetFromFullSyncDryRun(accountId, false, false);
    for (YamlChangeSet yamlChangeSet : yamlChangeSets) {
      gitFileChanges.addAll(yamlChangeSet.getGitFileChanges());
    }

    return gitFileChanges;
  }

  @Override
  public List<YamlChangeSet> obtainChangeSetFromFullSyncDryRun(
      String accountId, boolean onlyGitSyncConfiguredEntities, boolean skipAppLevel) {
    try {
      log.info("Performing full-sync dry-run for account {}", accountId);
      List<YamlChangeSet> yamlChangeSets = new ArrayList<>();

      if (!onlyGitSyncConfiguredEntities || isGitSyncConfiguredForAccount(accountId)) {
        List<GitFileChange> gitFileChanges = obtainAccountOnlyGitFileChanges(accountId, false).getGitFileChanges();
        yamlChangeSets.add(obtainYamlChangeSet(accountId, GLOBAL_APP_ID, gitFileChanges, false));
      } else {
        log.info("Git Sync not configured for accountId =[{}]. Skip generating changeset.", accountId);
      }
      if (!skipAppLevel) {
        log.info("Not Skipping app level dryrun");
        yamlChangeSets.addAll(obtainAllApplicationYamlChangeSet(accountId, false, onlyGitSyncConfiguredEntities));
      }
      log.info("Performed full-sync dry-run for account {}", accountId);
      return yamlChangeSets;
    } catch (Exception ex) {
      log.error(format("Failed to perform full-sync dry-run for account %s", accountId), ex);
    }

    return new ArrayList<>();
  }
  private boolean isGitSyncConfiguredForAccount(String accountId) {
    return yamlDirectoryService.weNeedToPushChanges(accountId, GLOBAL_APP_ID) != null;
  }

  private FullSyncChangeset obtainAccountOnlyGitFileChanges(String accountId, boolean includeFiles) {
    List<GitFileChange> gitFileChanges = new ArrayList<>();
    List<FullSyncError> errorYamls = new ArrayList<>();
    FolderNode top = yamlDirectoryService.getDirectory(accountId, SETUP_ENTITY_ID, false, null);
    gitFileChanges =
        yamlDirectoryService.traverseDirectory(gitFileChanges, accountId, top, "", includeFiles, false, errorYamls);

    return FullSyncChangeset.builder().gitFileChanges(gitFileChanges).yamlErrors(errorYamls).build();
  }

  @Override
  public List<String> getAllYamlErrorsForAccount(String accountId) {
    try {
      log.info("Getting all Yaml errors for account {}", accountId);
      FolderNode top = yamlDirectoryService.getDirectory(accountId, SETUP_ENTITY_ID, false, null);
      List<GitFileChange> gitFileChanges = new ArrayList<>();
      List<FullSyncError> errorLog = new ArrayList<>();
      yamlDirectoryService.traverseDirectory(gitFileChanges, accountId, top, "", false, false, errorLog);
      log.info("Got all Yaml errors for account {}", accountId);
      return errorLog.stream().map(error -> error.getError()).collect(Collectors.toList());
    } catch (Exception ex) {
      log.error(format("Failed to get all Yaml errors for account %s", accountId), ex);
    }
    return new ArrayList<>();
  }

  private List<Account> getAllAccounts() {
    PageRequest<Account> request =
        aPageRequest().withLimit(UNLIMITED).addFieldsIncluded("uuid").addFilter("appId", EQ, GLOBAL_APP_ID).build();
    return accountService.list(request);
  }

  @Override
  public void performFullSyncDryRunOnAllAccounts() {
    List<Account> accounts = getAllAccounts();
    accounts.forEach(account -> performFullSyncDryRun(account.getUuid()));
  }

  @Override
  public List<String> getAllYamlErrorsForAllAccounts() {
    List<Account> accounts = getAllAccounts();
    List<String> allErrors = new ArrayList<>();
    accounts.forEach(account -> allErrors.addAll(getAllYamlErrorsForAccount(account.getUuid())));
    return allErrors;
  }

  @Override
  public YamlGitConfig getYamlGitConfigForHarnessToGitChangeSet(YamlChangeSet harnessToGitChangeSet) {
    return yamlDirectoryService.weNeedToPushChanges(
        harnessToGitChangeSet.getAccountId(), harnessToGitChangeSet.getAppId());
  }

  @Override
  public void handleHarnessChangeSet(YamlChangeSet yamlChangeSet, String accountId) {
    final Stopwatch stopwatch = Stopwatch.createStarted();

    String appId = yamlChangeSet.getAppId();
    String yamlChangeSetId = yamlChangeSet.getUuid();
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AppLogContext ignore2 = new AppLogContext(appId, OVERRIDE_NESTS);
         YamlProcessingLogContext ignore3 =
             YamlProcessingLogContext.builder().changeSetId(yamlChangeSetId).build(OVERRIDE_ERROR)) {
      log.info(GIT_YAML_LOG_PREFIX + "Started handling harness -> git change set");

      List<GitFileChange> gitFileChanges = yamlChangeSet.getGitFileChanges();
      YamlGitConfig yamlGitConfig = getYamlGitConfigForHarnessToGitChangeSet(yamlChangeSet);
      GitConfig gitConfig = yamlGitConfig != null ? getGitConfig(yamlGitConfig) : null;

      if (yamlGitConfig == null || gitConfig == null) {
        throw new GeneralException(
            format(GIT_YAML_LOG_PREFIX
                    + "YamlGitConfig: [%s] and gitConfig: [%s]  shouldn't be null for accountId [%s] and entity [%s]",
                yamlGitConfig, gitConfig, accountId, appId),
            USER);
      }

      ensureValidNameSyntax(gitFileChanges, yamlChangeSet);

      gitConfigHelperService.convertToRepoGitConfig(gitConfig, yamlGitConfig.getRepositoryName());

      String lastProcessedGitCommitId =
          Optional
              .ofNullable(fetchLastProcessedGitCommitId(accountId, Collections.singletonList(yamlGitConfig.getUuid())))
              .map(GitCommit::getCommitId)
              .orElse(null);
      boolean pushOnlyIfHeadSeen = shouldPushOnlyIfHeadSeen(yamlChangeSet, lastProcessedGitCommitId);

      String waitId = generateUuid();
      List<String> yamlChangeSetIds = new ArrayList<>();
      yamlChangeSetIds.add(yamlChangeSetId);
      DelegateTask delegateTask = DelegateTask.builder()
                                      .accountId(accountId)
                                      .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
                                      .waitId(waitId)
                                      .data(TaskData.builder()
                                                .async(true)
                                                .taskType(TaskType.GIT_COMMAND.name())
                                                .parameters(new Object[] {GitCommandType.COMMIT_AND_PUSH, gitConfig,
                                                    secretManager.getEncryptionDetails(gitConfig, GLOBAL_APP_ID, null),
                                                    GitCommitRequest.builder()
                                                        .gitFileChanges(gitFileChanges)
                                                        .forcePush(true)
                                                        .yamlChangeSetIds(yamlChangeSetIds)
                                                        .yamlGitConfig(yamlGitConfig.toDTO())
                                                        .lastProcessedGitCommit(lastProcessedGitCommitId)
                                                        .pushOnlyIfHeadSeen(pushOnlyIfHeadSeen)
                                                        .build()})
                                                .timeout(gitRequestTimeout)
                                                .build())
                                      .build();

      waitNotifyEngine.waitForAllOn(GENERAL,
          new GitCommandCallback(accountId, yamlChangeSetId, GitCommandType.COMMIT_AND_PUSH,
              yamlGitConfig.getGitConnectorId(), yamlGitConfig.getRepositoryName(), yamlGitConfig.getBranchName()),
          waitId);
      final String taskId = delegateService.queueTaskV2(delegateTask);
      try (ProcessTimeLogContext ignore4 = new ProcessTimeLogContext(stopwatch.elapsed(MILLISECONDS), OVERRIDE_ERROR)) {
        log.info(GIT_YAML_LOG_PREFIX
                + "Successfully queued harness->git change set for processing with delegate taskId=[{}]",
            taskId);
      }
    }
  }

  @VisibleForTesting
  boolean shouldPushOnlyIfHeadSeen(YamlChangeSet yamlChangeSet, String lastProcessedGitCommitId) {
    return !PUSH_IF_NOT_HEAD_MAX_RETRY_COUNT.equals(yamlChangeSet.getPushRetryCount()) && !yamlChangeSet.isFullSync()
        && !isEmpty(lastProcessedGitCommitId);
  }

  /**
   * Check filePath is valid.
   *
   * @param gitFileChanges
   * @param yamlChangeSet
   */
  @VisibleForTesting
  void ensureValidNameSyntax(List<GitFileChange> gitFileChanges, YamlChangeSet yamlChangeSet) {
    if (isEmpty(gitFileChanges)) {
      return;
    }
    // Get all yamlTypes having non-empty filepath prefixes (these yaml types represent different file paths)
    List<YamlType> folderYamlTypes =
        Arrays.stream(YamlType.values()).filter(yamlType -> isNotEmpty(yamlType.getPathExpression())).collect(toList());

    // make sure, all filepaths to be synced with git are in proper format
    // e.g. Setup/Application/app_name/index.yaml is valid one, but
    // Setup/Application/app/name/index.yaml is invalid. (this case is happening id app was names as "app/name")
    // we do not want to allow this scenario.
    gitFileChanges.forEach(gitFileChange
        -> matchPathPrefix(gitFileChange.getFilePath().charAt(0) == '/' ? gitFileChange.getFilePath().substring(1)
                                                                        : gitFileChange.getFilePath(),
            folderYamlTypes, yamlChangeSet, gitFileChange));
  }

  private void matchPathPrefix(
      String filePath, List<YamlType> folderYamlTypes, YamlChangeSet yamlChangeSet, GitFileChange gitFileChange) {
    // only check for file and not directories

    if (Pattern.compile(YamlType.MANIFEST_FILE.getPathExpression()).matcher(filePath).matches()
        || Pattern.compile(YamlType.MANIFEST_FILE_APP_SERVICE.getPathExpression()).matcher(filePath).matches()
        || Pattern.compile(YamlType.MANIFEST_FILE_VALUES_ENV_OVERRIDE.getPathExpression()).matcher(filePath).matches()
        || Pattern.compile(YamlType.MANIFEST_FILE_VALUES_ENV_SERVICE_OVERRIDE.getPathExpression())
               .matcher(filePath)
               .matches()
        || Pattern.compile(YamlType.MANIFEST_FILE_PCF_OVERRIDE_ENV_OVERRIDE.getPathExpression())
               .matcher(filePath)
               .matches()
        || Pattern.compile(YamlType.MANIFEST_FILE_PCF_OVERRIDE_ENV_SERVICE_OVERRIDE.getPathExpression())
               .matcher(filePath)
               .matches()
        || Pattern.compile(YamlType.MANIFEST_FILE_APP_SETTINGS_ENV_OVERRIDE.getPathExpression())
               .matcher(filePath)
               .matches()
        || Pattern.compile(YamlType.MANIFEST_FILE_APP_SETTINGS_ENV_SERVICE_OVERRIDE.getPathExpression())
               .matcher(filePath)
               .matches()
        || Pattern.compile(YamlType.MANIFEST_FILE_CONN_STRINGS_ENV_OVERRIDE.getPathExpression())
               .matcher(filePath)
               .matches()
        || Pattern.compile(YamlType.MANIFEST_FILE_CONN_STRINGS_ENV_SERVICE_OVERRIDE.getPathExpression())
               .matcher(filePath)
               .matches()) {
      return;
    }

    if (filePath.endsWith(YamlConstants.YAML_EXTENSION)) {
      if (folderYamlTypes.stream().noneMatch(
              yamlType -> Pattern.compile(yamlType.getPathExpression()).matcher(filePath).matches())) {
        String message =
            "Invalid entity name, entity can not contain / in the name. Caused invalid file path: " + filePath;
        gitSyncErrorService.upsertGitSyncErrors(
            gitFileChange, message, yamlChangeSet.isFullSync(), yamlChangeSet.isGitToHarness());
        throw new WingsException(message, USER);
      }
    }
  }

  @Override
  public String validateAndQueueWebhookRequest(
      String accountId, String webhookToken, String yamlWebHookPayload, HttpHeaders headers) {
    final Stopwatch startedStopWatch = Stopwatch.createStarted();
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         YamlProcessingLogContext ignore2 =
             YamlProcessingLogContext.builder().webhookToken(webhookToken).build(OVERRIDE_ERROR)) {
      log.info(GIT_YAML_LOG_PREFIX + "Started processing webhook request");
      List<SettingAttribute> settingAttributes =
          wingsPersistence.createQuery(SettingAttribute.class)
              .filter(SettingAttributeKeys.accountId, accountId)
              .filter(SettingAttributeKeys.value_type, SettingVariableTypes.GIT.name())
              .asList();

      if (isEmpty(settingAttributes)) {
        log.info(GIT_YAML_LOG_PREFIX + "Git connector not found for account");
        throw new InvalidRequestException("Git connector not found with webhook token " + webhookToken, USER);
      }

      String gitConnectorId = null;
      GitConfig gitConfig = null;
      for (SettingAttribute settingAttribute : settingAttributes) {
        SettingValue settingValue = settingAttribute.getValue();

        if (settingValue instanceof GitConfig && webhookToken.equals(((GitConfig) settingValue).getWebhookToken())) {
          gitConnectorId = settingAttribute.getUuid();
          gitConfig = (GitConfig) settingValue;
          break;
        }
      }

      if (isEmpty(gitConnectorId) || gitConfig == null) {
        throw new InvalidRequestException("Git connector not found with webhook token " + webhookToken, USER);
      }

      boolean gitPingEvent = webhookEventUtils.isGitPingEvent(headers);
      if (gitPingEvent) {
        log.info(GIT_YAML_LOG_PREFIX + "Ping event found. Skip processing");
        return "Found ping event. Only push events are supported";
      }

      WebhookSource webhookSource = webhookEventUtils.obtainWebhookSource(headers);
      /* When a webhook  for pull request merged event is created at Azure Devops end, it first sends an event with
       * 'active' status. We need to ignore this and process the next event it sends with status 'completed' at the time
       * of actual pull request merge. Processing  is skipped for event with 'active' status since pull request event is
       * still open and and unmerged yet. If we don't skip it, current code flow throws an exception for 'active' event,
       * which restricts/disables webhook from Azure end */
      if (webhookSource != null && webhookSource.equals(WebhookSource.AZURE_DEVOPS)
          && webhookEventUtils.shouldIgnorePullRequestMergeEventWithActiveStatusFromAzure(yamlWebHookPayload)) {
        log.info(GIT_YAML_LOG_PREFIX
            + "Merge pull request event having active status received from Azure DevOps. Skipped processing.");
        return "Skipped processing for merge pull request event since pull request has active status and is still unmerged.";
      }

      final String branchName = obtainBranchFromPayload(yamlWebHookPayload, headers);

      if (isEmpty(branchName)) {
        log.info(GIT_YAML_LOG_PREFIX + "Branch not found. webhookToken: {}, yamlWebHookPayload: {}, headers: {}",
            webhookToken, yamlWebHookPayload, headers);
        throw new InvalidRequestException("Branch not found from webhook payload", USER);
      }

      String repositoryFullName = null;
      if (gitConfig.getUrlType() == UrlType.ACCOUNT) {
        repositoryFullName =
            obtainRepositoryFullNameFromPayload(yamlWebHookPayload, headers)
                .filter(EmptyPredicate::isNotEmpty)
                .orElseThrow(() -> {
                  log.info(GIT_YAML_LOG_PREFIX
                          + "Repository full name not found. webhookToken: {}, yamlWebHookPayload: {}, headers: {}",
                      webhookToken, yamlWebHookPayload, headers);
                  return new InvalidRequestException("Repository full name not found from webhook payload", USER);
                });
      }

      String headCommitId = obtainCommitIdFromPayload(yamlWebHookPayload, headers);

      if (isNotEmpty(headCommitId) && isCommitAlreadyProcessed(accountId, headCommitId)) {
        log.info(GIT_YAML_LOG_PREFIX + "CommitId: [{}] already processed.", headCommitId);
        return "Commit already processed";
      }

      log.info(GIT_YAML_LOG_PREFIX + " Found branch name =[{}], headCommitId=[{}]", branchName, headCommitId);
      YamlChangeSet yamlChangeSet =
          YamlChangeSet.builder()
              .appId(GLOBAL_APP_ID)
              .accountId(accountId)
              .gitToHarness(true)
              .status(Status.QUEUED)
              .gitWebhookRequestAttributes(GitWebhookRequestAttributes.builder()
                                               .webhookBody(yamlWebHookPayload)
                                               .gitConnectorId(gitConnectorId)
                                               .webhookHeaders(convertHeadersToJsonString(headers))
                                               .repositoryFullName(repositoryFullName)
                                               .branchName(branchName)
                                               .headCommitId(headCommitId)
                                               .build())
              .gitFileChanges(new ArrayList<>())
              .retryCount(0)
              .build();
      final YamlChangeSet savedYamlChangeSet = yamlChangeSetService.save(yamlChangeSet);
      try (ProcessTimeLogContext ignore3 =
               new ProcessTimeLogContext(startedStopWatch.elapsed(MILLISECONDS), OVERRIDE_ERROR)) {
        log.info(GIT_YAML_LOG_PREFIX + "Successfully accepted webhook request for processing as yamlChangeSetId=[{}]",
            savedYamlChangeSet.getUuid());
      }

      return "Successfully accepted webhook request for processing";
    }
  }

  private String convertHeadersToJsonString(HttpHeaders headers) {
    try {
      return JsonUtils.asJson(headers.getRequestHeaders());
    } catch (Exception ex) {
      log.warn("Failed to convert request headers in json string", ex);
      return null;
    }
  }

  @Override
  public List<YamlGitConfig> getYamlGitConfigsForGitToHarnessChangeSet(YamlChangeSet gitToHarnessChangeSet) {
    final String accountId = gitToHarnessChangeSet.getAccountId();
    checkNotNull(gitToHarnessChangeSet.getGitWebhookRequestAttributes(),
        "GitWebhookRequestAttributes not available in changeset = [%s]", gitToHarnessChangeSet.getUuid());

    final String gitConnectorId = gitToHarnessChangeSet.getGitWebhookRequestAttributes().getGitConnectorId();
    final String branchName = gitToHarnessChangeSet.getGitWebhookRequestAttributes().getBranchName();
    final String repositoryFullName = gitToHarnessChangeSet.getGitWebhookRequestAttributes().getRepositoryFullName();

    return getYamlGitConfigs(accountId, gitConnectorId, branchName, repositoryFullName);
  }

  @Override
  public List<YamlGitConfig> getYamlGitConfigs(
      String accountId, String gitConnectorId, String branchName, String repositoryName) {
    checkState(isNotEmpty(gitConnectorId), "gitConnectorId should not be empty");
    checkState(isNotEmpty(branchName), "branchName should not be empty");
    List<YamlGitConfig> list = wingsPersistence.createQuery(YamlGitConfig.class)
                                   .filter(YamlGitConfigKeys.accountId, accountId)
                                   .filter(GIT_CONNECTOR_ID_KEY, gitConnectorId)
                                   .filter(BRANCH_NAME_KEY, branchName)
                                   .asList();

    SettingAttribute settingAttribute = settingsService.get(gitConnectorId);
    if (settingAttribute != null && settingAttribute.getValue() instanceof GitConfig
        && UrlType.ACCOUNT == ((GitConfig) settingAttribute.getValue()).getUrlType()) {
      if (isEmpty(repositoryName)) {
        throw new IllegalArgumentException(
            "Missing repository name when using account level git connector in webhook request attributes");
      }

      return list.stream()
          .filter(ygc
              -> matchesRepositoryFullName(
                  (GitConfig) settingAttribute.getValue(), ygc.getRepositoryName(), repositoryName))
          .collect(toList());
    }

    return list;
  }

  @Override
  public List<YamlGitConfig> getYamlGitConfigByConnector(String accountId, String connectorId) {
    return wingsPersistence.createQuery(YamlGitConfig.class)
        .filter(YamlGitConfigKeys.accountId, accountId)
        .filter(GIT_CONNECTOR_ID_KEY, connectorId)
        .asList();
  }

  @Override
  public List<String> getYamlGitConfigIds(
      String accountId, String gitConnectorId, String branchName, String repositoryName) {
    List<String> yamlGitConfigIds = new ArrayList<>();
    List<YamlGitConfig> yamlGitConfigs = getYamlGitConfigs(accountId, gitConnectorId, branchName, repositoryName);
    if (isNotEmpty(yamlGitConfigs)) {
      yamlGitConfigIds = yamlGitConfigs.stream().map(YamlGitConfig::getUuid).collect(Collectors.toList());
    }
    return yamlGitConfigIds;
  }

  private boolean matchesRepositoryFullName(GitConfig gitConfig, String repositoryName, String repositoryFullName) {
    String processedUrl = gitConfigHelperService.getRepositoryUrl(gitConfig, repositoryName);
    processedUrl = cleanupRepositoryName(processedUrl);
    String processedFullName = cleanupRepositoryName(repositoryFullName);

    return endsWith(processedUrl, processedFullName);
  }

  @Override
  public void handleGitChangeSet(YamlChangeSet yamlChangeSet, String accountId) {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    GitWebhookRequestAttributes gitWebhookRequestAttributes = yamlChangeSet.getGitWebhookRequestAttributes();
    String gitConnectorId = gitWebhookRequestAttributes.getGitConnectorId();
    String branchName = gitWebhookRequestAttributes.getBranchName();
    String headCommitId = gitWebhookRequestAttributes.getHeadCommitId();

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         YamlProcessingLogContext ignore3 =
             getYamlProcessingLogContext(gitConnectorId, branchName, null, yamlChangeSet.getUuid())) {
      log.info(
          GIT_YAML_LOG_PREFIX + "Started handling Git -> harness change set with headCommit Id =[{}]", headCommitId);

      if (isNotEmpty(headCommitId) && isCommitAlreadyProcessed(accountId, headCommitId)) {
        log.info(GIT_YAML_LOG_PREFIX + "CommitId: [{}] already processed.", headCommitId);
        yamlChangeSetService.updateStatus(accountId, yamlChangeSet.getUuid(), Status.SKIPPED);
        return;
      }

      final List<YamlGitConfig> yamlGitConfigs = getYamlGitConfigsForGitToHarnessChangeSet(yamlChangeSet);

      if (isEmpty(yamlGitConfigs)) {
        log.info(GIT_YAML_LOG_PREFIX + "Git sync configuration not found");
        throw new InvalidRequestException("Git sync configuration not found with branch " + branchName, USER);
      }

      YamlGitConfig yamlGitConfig = yamlGitConfigs.get(0);
      List<String> yamlGitConfigIds = yamlGitConfigs.stream().map(YamlGitConfig::getUuid).collect(toList());
      final GitCommit lastProcessedGitCommitId = fetchLastProcessedGitCommitId(accountId, yamlGitConfigIds);

      final String processedCommit = lastProcessedGitCommitId == null ? null : lastProcessedGitCommitId.getCommitId();
      log.info(GIT_YAML_LOG_PREFIX + "Last processed git commit found =[{}]", processedCommit);

      String waitId = generateUuid();
      GitConfig gitConfig = getGitConfig(yamlGitConfig);
      gitConfigHelperService.convertToRepoGitConfig(gitConfig, yamlGitConfig.getRepositoryName());
      DelegateTask delegateTask = DelegateTask.builder()
                                      .accountId(accountId)
                                      .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
                                      .waitId(waitId)
                                      .data(TaskData.builder()
                                                .async(true)
                                                .taskType(TaskType.GIT_COMMAND.name())
                                                .parameters(new Object[] {GitCommandType.DIFF, gitConfig,
                                                    secretManager.getEncryptionDetails(gitConfig, GLOBAL_APP_ID, null),
                                                    GitDiffRequest.builder()
                                                        .lastProcessedCommitId(processedCommit)
                                                        .endCommitId(getEndCommitId(headCommitId, accountId))
                                                        .yamlGitConfig(yamlGitConfig.toDTO())
                                                        .build(),
                                                    true /*excludeFilesOutsideSetupFolder */})
                                                .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                                                .build())
                                      .build();

      waitNotifyEngine.waitForAllOn(GENERAL,
          new GitCommandCallback(accountId, yamlChangeSet.getUuid(), GitCommandType.DIFF,
              yamlGitConfig.getGitConnectorId(), yamlGitConfig.getRepositoryName(), yamlGitConfig.getBranchName()),
          waitId);
      final String taskId = delegateService.queueTaskV2(delegateTask);
      try (ProcessTimeLogContext ignore2 = new ProcessTimeLogContext(stopwatch.elapsed(MILLISECONDS), OVERRIDE_ERROR)) {
        log.info(GIT_YAML_LOG_PREFIX
                + "Successfully queued git->harness change set for processing with delegate taskId=[{}]",
            taskId);
      }

    } catch (Exception ex) {
      log.error(format(GIT_YAML_LOG_PREFIX + "Unexpected error while processing git->harness change set [%s]",
                    yamlChangeSet.getUuid()),
          ex);
      yamlChangeSetService.updateStatus(accountId, yamlChangeSet.getUuid(), Status.SKIPPED);
    }
  }
  private String getEndCommitId(String headCommitId, String accountId) {
    if (isEmpty(headCommitId)) {
      log.warn("headCommitId cannot be deciphered from payload. Using HEAD for taking diff");
    }
    return isNotEmpty(headCommitId) ? headCommitId : null;
  }
  private YamlProcessingLogContext getYamlProcessingLogContext(
      String gitConnectorId, String branch, String webhookToken, String yamlChangeSetId) {
    return new YamlProcessingLogContext(NullSafeImmutableMap.<String, String>builder()
                                            .putIfNotNull(GIT_CONNECTOR_ID, gitConnectorId)
                                            .putIfNotNull(BRANCH_NAME, branch)
                                            .putIfNotNull(WEBHOOK_TOKEN, webhookToken)
                                            .putIfNotNull(CHANGESET_ID, yamlChangeSetId)
                                            .build(),
        OVERRIDE_NESTS);
  }

  @Override
  public boolean isCommitAlreadyProcessed(String accountId, String headCommit) {
    final Query<GitCommit> query = wingsPersistence.createQuery(GitCommit.class)
                                       .filter(GitCommitKeys.accountId, accountId)
                                       .filter(GitCommitKeys.commitId, headCommit)
                                       .field(GitCommitKeys.status)
                                       .in(GIT_COMMIT_ALL_STATUS_LIST);

    final GitCommit gitCommit = query.get();
    if (gitCommit != null) {
      log.info(GIT_YAML_LOG_PREFIX + "Commit [id:{}] already processed [status:{}] on [date:{}] mode:[{}]",
          gitCommit.getCommitId(), gitCommit.getStatus(), gitCommit.getLastUpdatedAt(),
          gitCommit.getYamlChangeSet().isGitToHarness());
      return true;
    }
    return false;
  }

  private List<GitCommit.Status> getProcessedGitCommitStatusList(String accountId) {
    return GIT_COMMIT_PROCESSED_STATUS;
  }

  @Override
  public GitSyncWebhook getWebhook(String entityId, String accountId) {
    GitSyncWebhook gsw = wingsPersistence.createQuery(GitSyncWebhook.class)
                             .filter(GitSyncWebhookKeys.entityId, entityId)
                             .filter(GitSyncWebhookKeys.accountId, accountId)
                             .get();

    if (gsw != null) {
      return gsw;
    } else {
      // create a new GitSyncWebhook, save to Mongo and return it
      String newWebhookToken = CryptoUtils.secureRandAlphaNumString(40);
      gsw = GitSyncWebhook.builder().accountId(accountId).entityId(entityId).webhookToken(newWebhookToken).build();
      return wingsPersistence.saveAndGet(GitSyncWebhook.class, gsw);
    }
  }

  @Override
  public GitCommit saveCommit(GitCommit gitCommit) {
    return wingsPersistence.saveAndGet(GitCommit.class, gitCommit);
  }

  @Override
  public void processFailedChanges(
      String accountId, Map<String, ChangeWithErrorMsg> failedYamlFileChangeMap, boolean gitToHarness) {
    if (failedYamlFileChangeMap.size() > 0) {
      failedYamlFileChangeMap.values().forEach(changeWithErrorMsg
          -> gitSyncErrorService.upsertGitSyncErrors(
              changeWithErrorMsg.getChange(), changeWithErrorMsg.getErrorMsg(), false, gitToHarness));
      if (featureFlagService.isEnabled(NOTIFY_GIT_SYNC_ERRORS_PER_APP, accountId)) {
        Set<String> uniqueAppIds = yamlHelper.getAppIdsFromYamlFilePaths(failedYamlFileChangeMap.keySet(), accountId);
        uniqueAppIds.forEach(appId
            -> alertService.openAlert(accountId, appId, AlertType.GitSyncError,
                getGitSyncErrorAlert(accountId,
                    format("Unable to process changes from Git to application: %s", appService.get(appId).getName()))));
      } else {
        alertService.openAlert(accountId, GLOBAL_APP_ID, AlertType.GitSyncError, getGitSyncErrorAlert(accountId));
      }
    }
  }

  @Override
  public void raiseAlertForGitFailure(
      String accountId, String appId, GitSyncFailureAlertDetails gitSyncFailureAlertDetails) {
    if (ErrorCode.GIT_DIFF_COMMIT_NOT_IN_ORDER == gitSyncFailureAlertDetails.getErrorCode()) {
      return;
    }
    // We get some exception in delegate while processing git commands, out of those
    // exceptions, we show the git connection error in UI and create alert so that
    // the user is directed to the git connectivity issue page
    if (ErrorCode.GIT_CONNECTION_ERROR == gitSyncFailureAlertDetails.getErrorCode()
        || (ErrorCode.GENERAL_YAML_ERROR == gitSyncFailureAlertDetails.getErrorCode())) {
      alertService.openAlert(accountId, appId, AlertType.GitConnectionError,
          getGitConnectionErrorAlert(accountId, gitSyncFailureAlertDetails));
    } else {
      // If we got some other error when delegate was trying the git commands then
      // we don't create alert as this error won't be visible in the UI
      log.info("Not Raising Alert: Encountered the error [{}] while performing git operation for account [{}]",
          gitSyncFailureAlertDetails.getErrorMessage(), accountId);
    }
  }

  @Override
  public void closeAlertForGitFailureIfOpen(String accountId, String appId, AlertType alertType, AlertData alertData) {
    alertService.closeAlert(accountId, appId, alertType, alertData);
  }

  private GitSyncErrorAlert getGitSyncErrorAlert(String accountId) {
    return GitSyncErrorAlert.builder().accountId(accountId).message("Unable to process changes from Git").build();
  }

  private GitSyncErrorAlert getGitSyncErrorAlert(String accountId, String errorMessage) {
    return GitSyncErrorAlert.builder().accountId(accountId).message(errorMessage).build();
  }

  private GitConnectionErrorAlert getGitConnectionErrorAlert(
      String accountId, GitSyncFailureAlertDetails gitFailureDetails) {
    if (gitFailureDetails == null) {
      throw new UnexpectedException("The git error details supplied for the connection error is empty");
    }
    return GitConnectionErrorAlert.builder()
        .accountId(accountId)
        .gitConnectorId(gitFailureDetails.getGitConnectorId())
        .branchName(gitFailureDetails.getBranchName())
        .message(gitFailureDetails.getErrorMessage())
        .repositoryName(gitFailureDetails.getRepositoryName())
        .build();
  }

  @Override
  public void removeGitSyncErrors(String accountId, List<GitFileChange> gitFileChangeList, boolean gitToHarness) {
    List<String> yamlFilePathList = gitFileChangeList.stream().map(GitFileChange::getFilePath).collect(toList());
    Query query = wingsPersistence.createAuthorizedQuery(GitSyncError.class);
    query.filter("accountId", accountId);
    query.field(GitSyncErrorKeys.yamlFilePath).in(yamlFilePathList);
    wingsPersistence.delete(query);
    if (featureFlagService.isEnabled(NOTIFY_GIT_SYNC_ERRORS_PER_APP, accountId)) {
      Set<String> appIdsFromYamlFilePaths =
          yamlHelper.getAppIdsFromYamlFilePaths(new HashSet<>(yamlFilePathList), accountId);
      appIdsFromYamlFilePaths.forEach(appId -> closeAlertIfApplicable(accountId, appId));
    }
    closeAlertIfApplicable(accountId);
  }

  @Override
  public RestResponse<List<GitSyncError>> listGitSyncErrors(String accountId) {
    PageRequest<GitSyncError> pageRequest = aPageRequest()
                                                .addFilter("accountId", EQ, accountId)
                                                .withLimit("500")
                                                .addOrder(GitSyncError.CREATED_AT_KEY, OrderType.ASC)
                                                .build();
    PageResponse<GitSyncError> response = wingsPersistence.query(GitSyncError.class, pageRequest);
    return RestResponse.Builder.aRestResponse().withResource(response.getResponse()).build();
  }

  @Override
  public RestResponse discardGitSyncError(String accountId, String errorId) {
    Query query = wingsPersistence.createAuthorizedQuery(GitSyncError.class);
    query.filter("accountId", accountId);
    query.filter("_id", errorId);
    GitSyncError gitSyncError = wingsPersistence.get(GitSyncError.class, errorId);
    wingsPersistence.delete(query);
    if (featureFlagService.isEnabled(NOTIFY_GIT_SYNC_ERRORS_PER_APP, accountId)) {
      closeAlertIfApplicable(accountId, gitSyncError.getAppId());
    }
    closeAlertIfApplicable(accountId);
    return RestResponse.Builder.aRestResponse().build();
  }

  @Override
  public RestResponse discardGitSyncErrorForFilePath(String accountId, String yamlFilePath) {
    Query<GitSyncError> query = wingsPersistence.createAuthorizedQuery(GitSyncError.class);
    query.filter("accountId", accountId);
    query.filter(GitSyncErrorKeys.yamlFilePath, yamlFilePath);
    wingsPersistence.delete(query);
    if (featureFlagService.isEnabled(NOTIFY_GIT_SYNC_ERRORS_PER_APP, accountId)) {
      closeAlertIfApplicable(accountId, yamlHelper.getAppId(accountId, yamlFilePath));
    }
    closeAlertIfApplicable(accountId);
    return RestResponse.Builder.aRestResponse().build();
  }

  @Override
  public RestResponse discardGitSyncErrorsForGivenPaths(String accountId, List<String> yamlFilePaths) {
    Query<GitSyncError> query = wingsPersistence.createAuthorizedQuery(GitSyncError.class);
    query.filter("accountId", accountId);
    query.field(GitSyncErrorKeys.yamlFilePath).in(yamlFilePaths);
    wingsPersistence.delete(query);
    if (featureFlagService.isEnabled(NOTIFY_GIT_SYNC_ERRORS_PER_APP, accountId)) {
      Set<String> appIdsFromYamlFilePaths =
          yamlHelper.getAppIdsFromYamlFilePaths(new HashSet<>(yamlFilePaths), accountId);
      appIdsFromYamlFilePaths.forEach(appId -> closeAlertIfApplicable(accountId, appId));
    }
    closeAlertIfApplicable(accountId);
    return RestResponse.Builder.aRestResponse().build();
  }

  @Override
  public RestResponse discardAllGitSyncError(String accountId) {
    Query query = wingsPersistence.createAuthorizedQuery(GitSyncError.class);
    query.filter("accountId", accountId);
    wingsPersistence.delete(query);
    if (featureFlagService.isEnabled(NOTIFY_GIT_SYNC_ERRORS_PER_APP, accountId)) {
      Set<String> appIds = appService.getAppIdsAsSetByAccountId(accountId);
      appIds.forEach(appId -> closeAlertIfApplicable(accountId, appId));
    }
    closeAlertIfApplicable(accountId);
    return RestResponse.Builder.aRestResponse().build();
  }

  @Override
  public RestResponse discardGitSyncErrorForFullSync(String accountId, String appId) {
    Query query = wingsPersistence.createAuthorizedQuery(GitSyncError.class).disableValidation();
    query.filter("accountId", accountId);
    query.filter(GitSyncErrorKeys.fullSyncPath, true);
    query.filter(ApplicationKeys.appId, appId);
    wingsPersistence.delete(query);
    if (featureFlagService.isEnabled(NOTIFY_GIT_SYNC_ERRORS_PER_APP, accountId)) {
      closeAlertIfApplicable(accountId, appId);
    }
    closeAlertIfApplicable(accountId);
    return RestResponse.Builder.aRestResponse().build();
  }

  private void closeAlertIfApplicable(String accountId) {
    if (gitSyncErrorService.getGitSyncErrorCount(accountId, false) == 0) {
      alertService.closeAlert(accountId, GLOBAL_APP_ID, AlertType.GitSyncError, getGitSyncErrorAlert(accountId));
    }
  }
  private void closeAlertIfApplicable(String accountId, String appId) {
    if (gitSyncErrorService.getGitSyncErrorCount(accountId, false) == 0) {
      if (featureFlagService.isEnabled(NOTIFY_GIT_SYNC_ERRORS_PER_APP, accountId)) {
        alertService.closeAlert(accountId, appId, AlertType.GitSyncError,
            getGitSyncErrorAlert(accountId,
                String.format(
                    "Unable to process changes from Git to application: %s", appService.get(appId).getName())));
      } else {
        alertService.closeAlert(accountId, appId, AlertType.GitSyncError, getGitSyncErrorAlert(accountId));
      }
    }
  }

  @Override
  public boolean checkApplicationChange(GitFileChange gitFileChange) {
    return startsWith(gitFileChange.getFilePath(), APPLICATION_FOLDER_PATH);
  }

  @Override
  public String obtainAppNameFromGitFileChange(GitFileChange gitFileChange) {
    String filePath = gitFileChange.getFilePath();

    String appFolderPath = APPLICATION_FOLDER_PATH + PATH_DELIMITER;
    String appPath = filePath.substring(appFolderPath.length());
    return appPath.substring(0, appPath.indexOf('/'));
  }

  @Override
  public boolean checkApplicationNameIsValid(GitFileChange gitFileChange) {
    String filePath = gitFileChange.getFilePath();

    String appFolderPath = APPLICATION_FOLDER_PATH + PATH_DELIMITER;
    if (!filePath.contains(appFolderPath)) {
      return false;
    }
    String appPath = filePath.substring(appFolderPath.length());
    return appPath.contains("/");
  }

  @Override
  public void delete(String accountId, String entityId, EntityType entityType) {
    YamlGitConfig yamlGitConfig = get(accountId, entityId, entityType);
    if (yamlGitConfig == null) {
      return;
    }

    wingsPersistence.delete(yamlGitConfig);
  }

  private String obtainCommitIdFromPayload(String yamlWebHookPayload, HttpHeaders headers) {
    if (headers == null) {
      log.info("Empty header found");
      return null;
    }

    WebhookSource webhookSource = webhookEventUtils.obtainWebhookSource(headers);
    if (webhookSource == WebhookSource.AZURE_DEVOPS) {
      webhookEventUtils.validatePushEventForAzureDevOps(yamlWebHookPayload);
    } else {
      webhookEventUtils.validatePushEvent(webhookSource, headers);
    }

    Map<String, Object> payLoadMap =
        JsonUtils.asObject(yamlWebHookPayload, new TypeReference<Map<String, Object>>() {});

    return webhookEventUtils.obtainCommitId(webhookSource, headers, payLoadMap);
  }

  private String obtainBranchFromPayload(String yamlWebHookPayload, HttpHeaders headers) {
    if (headers == null) {
      log.info("Empty header found");
      return null;
    }

    WebhookSource webhookSource = webhookEventUtils.obtainWebhookSource(headers);
    if (webhookSource == WebhookSource.AZURE_DEVOPS) {
      webhookEventUtils.validatePushEventForAzureDevOps(yamlWebHookPayload);
    } else {
      webhookEventUtils.validatePushEvent(webhookSource, headers);
    }
    Map<String, Object> payLoadMap = webhookEventUtils.obtainPayloadMap(yamlWebHookPayload, headers);

    return webhookEventUtils.obtainBranchName(webhookSource, headers, payLoadMap);
  }

  private Optional<String> obtainRepositoryFullNameFromPayload(String yamlWebHookPayload, HttpHeaders headers) {
    if (headers == null) {
      log.info("Empty header found");
      return Optional.empty();
    }

    WebhookSource webhookSource = webhookEventUtils.obtainWebhookSource(headers);
    webhookEventUtils.validatePushEvent(webhookSource, headers);
    Map<String, Object> payLoadMap = webhookEventUtils.obtainPayloadMap(yamlWebHookPayload, headers);

    return webhookEventUtils.obtainRepositoryFullName(webhookSource, headers, payLoadMap);
  }

  @Override
  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  public void asyncFullSyncForEntireAccount(String accountId) {
    log.info(GIT_YAML_LOG_PREFIX + "Triggered async full git sync for account {}", accountId);
    executorService.submit(() -> {
      try {
        fullSyncForEntireAccount(accountId);
      } catch (WingsException ex) {
        ExceptionLogger.logProcessedMessages(ex, MANAGER, log);
      } catch (Throwable e) {
        log.error("Exception while performing async full git sync for account {}", accountId, e);
      }
    });
  }

  @Override
  @SuppressWarnings("PMD.AvoidCatchingThrowable")
  public void fullSyncForEntireAccount(String accountId) {
    try {
      log.info(GIT_YAML_LOG_PREFIX + "Performing full sync for account {}", accountId);

      // Perform fullsync for account level entities
      fullSync(accountId, accountId, EntityType.ACCOUNT, false);

      FindOptions findOptions = new FindOptions();
      if (featureFlagService.isEnabled(CDS_QUERY_OPTIMIZATION, accountId)) {
        findOptions.readPreference(ReadPreference.secondaryPreferred());
      }

      try (HIterator<Application> apps = new HIterator<>(wingsPersistence.createQuery(Application.class)
                                                             .filter(ApplicationKeys.accountId, accountId)
                                                             .fetch(findOptions))) {
        for (Application application : apps) {
          fullSync(accountId, application.getUuid(), APPLICATION, false);
        }
      }
      log.info(GIT_YAML_LOG_PREFIX + "Performed full sync for account {}", accountId);
    } catch (Throwable t) {
      // any thread that faces an error should continue to perform full sync for other accountIds
      // if possible.
      log.error("Error occured in full sync for account {}", accountId, t);
    }
  }

  private GitCommit fetchLastProcessedGitCommitId(String accountId, List<String> yamlGitConfigIds) {
    // After MultiGit support gitCommit record would have list of yamlGitConfigs.

    GitCommit gitCommit = wingsPersistence.createQuery(GitCommit.class)
                              .filter(GitCommitKeys.accountId, accountId)
                              .field(GitCommitKeys.status)
                              .in(getProcessedGitCommitStatusList(accountId))
                              .field(GitCommitKeys.yamlGitConfigIds)
                              .hasAnyOf(yamlGitConfigIds)
                              .order("-lastUpdatedAt")
                              .get();

    // This is to handle the old git commit records which doesn't have yamlGitConfigId
    if (gitCommit == null) {
      FindOptions findOptions_1 = new FindOptions();
      findOptions_1.hint(
          BasicDBUtils.getIndexObject(GitCommit.mongoIndexes(), "gitCommitAccountIdStatusYgLastUpdatedIdx"));

      gitCommit = wingsPersistence.createQuery(GitCommit.class)
                      .filter(GitCommitKeys.accountId, accountId)
                      .filter(GitCommitKeys.yamlGitConfigId, yamlGitConfigIds.get(0))
                      .field(GitCommitKeys.status)
                      .in(getProcessedGitCommitStatusList(accountId))
                      .order("-lastUpdatedAt")
                      .get(findOptions_1);
    }

    return gitCommit;
  }

  @Override
  public boolean retainYamlGitConfigsOfSelectedGitConnectorsAndDeleteRest(
      String accountId, List<String> selectedGitConnectors) {
    if (EmptyPredicate.isNotEmpty(selectedGitConnectors)) {
      // Delete yamlGitConfig documents whose gitConnectorId is not among
      // the list of selected git connectors
      wingsPersistence.delete(wingsPersistence.createQuery(YamlGitConfig.class)
                                  .filter(CGConstants.ACCOUNT_ID_KEY, accountId)
                                  .field(YamlGitConfig.GIT_CONNECTOR_ID_KEY)
                                  .hasNoneOf(selectedGitConnectors));
      return true;
    }
    return false;
  }

  @Override
  public RestResponse discardGitSyncErrorsForGivenIds(String accountId, List<String> errorIds) {
    Query query = wingsPersistence.createAuthorizedQuery(GitSyncError.class);
    query.filter("accountId", accountId);
    query.field("_id").in(errorIds);
    wingsPersistence.delete(query);
    if (featureFlagService.isEnabled(NOTIFY_GIT_SYNC_ERRORS_PER_APP, accountId)) {
      List<String> appIds = errorIds.stream()
                                .map(errorId -> wingsPersistence.get(GitSyncError.class, errorId).getAppId())
                                .collect(toList());
      appIds.forEach(appId -> closeAlertIfApplicable(accountId, appId));
    }
    closeAlertIfApplicable(accountId);
    return RestResponse.Builder.aRestResponse().build();
  }

  @Override
  public YamlGitConfig fetchYamlGitConfig(String appId, String accountId) {
    if (isNotEmpty(appId) && isNotEmpty(accountId)) {
      final String entityId = GLOBAL_APP_ID.equals(appId) ? accountId : appId;
      final EntityType entityType = GLOBAL_APP_ID.equals(appId) ? ACCOUNT : APPLICATION;
      return get(accountId, entityId, entityType);
    }
    return null;
  }
}
