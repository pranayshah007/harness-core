/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ApplicationManifest.ApplicationManifestKeys;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.settings.helm.AmazonS3HelmRepoConfig;
import software.wings.beans.settings.helm.GCSHelmRepoConfig;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(CDP)
public class HelmConnectorPathMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  private static final String DEBUG_LINE = "HELM_CONNECTOR_PATH_MIGRATION: ";

  @Override
  public void migrate() {
    log.info(DEBUG_LINE + "Starting migration for Helm Connector Path");
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();

        if (account == null) {
          log.info(DEBUG_LINE + "account is null, continuing");
          continue;
        }

        Map<String, SettingAttribute> settingAttributeIdObjectMap = getSettingAttributeIdObjectMap(account);

        log.info(format("%s found %s setting attributes of GCS or S3 type for account id : %s", DEBUG_LINE,
            settingAttributeIdObjectMap.size(), account.getUuid()));

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

            try (HIterator<ApplicationManifest> applicationManifests =
                     new HIterator<>(wingsPersistence.createQuery(ApplicationManifest.class)
                                         .filter("appId", application.getUuid())
                                         .filter(ApplicationManifestKeys.storeType, StoreType.HelmChartRepo)
                                         .fetch())) {
              while (applicationManifests.hasNext()) {
                ApplicationManifest applicationManifest = applicationManifests.next();

                if (applicationManifest == null) {
                  log.info(DEBUG_LINE + "Application Manifest is null, skipping");
                  continue;
                }

                updateApplicationManifest(settingAttributeIdObjectMap, applicationManifest);
              }
            }
          }
        }
      }
    }
    log.info(DEBUG_LINE + "Ended migration for Helm Connector Path");
  }

  private void updateApplicationManifest(
      Map<String, SettingAttribute> settingAttributeIdObjectMap, @Nonnull ApplicationManifest applicationManifest) {
    try {
      if (applicationManifest.getHelmChartConfig() != null
          && applicationManifest.getHelmChartConfig().getConnectorId() != null) {
        if (isNotEmpty(applicationManifest.getHelmChartConfig().getBasePath())) {
          log.info(format("%s Found existing base path %s for Application Manifest Id: %s , skipping", DEBUG_LINE,
              applicationManifest.getHelmChartConfig().getBasePath(), applicationManifest.getUuid()));
          return;
        }

        String connectorId = applicationManifest.getHelmChartConfig().getConnectorId();
        String folderPath = "";

        if (isEmpty(connectorId)) {
          // Helm inline scenario
          return;
        }

        if (!settingAttributeIdObjectMap.containsKey(connectorId)) {
          // Helm HTTP ConnectorDisconnectHandler or somehow ConnectorDisconnectHandler got deleted
          return;
        } else {
          SettingAttribute settingAttribute = settingAttributeIdObjectMap.get(connectorId);
          if (settingAttribute.getValue() instanceof AmazonS3HelmRepoConfig) {
            folderPath = ((AmazonS3HelmRepoConfig) settingAttribute.getValue()).getFolderPath();
          } else if (settingAttribute.getValue() instanceof GCSHelmRepoConfig) {
            folderPath = ((GCSHelmRepoConfig) settingAttribute.getValue()).getFolderPath();
          }
        }

        wingsPersistence.updateFields(ApplicationManifest.class, applicationManifest.getUuid(),
            Collections.singletonMap("helmChartConfig.basePath", folderPath));
        log.info(format("%s Updated base path to %s for application manifest Id : %s", DEBUG_LINE, folderPath,
            applicationManifest.getUuid()));
      }
    } catch (Exception ex) {
      log.error(format("%s Failure occurred in updating application manifest id : %s", DEBUG_LINE,
                    applicationManifest.getUuid()),
          ex);
    }
  }

  @NotNull
  private Map<String, SettingAttribute> getSettingAttributeIdObjectMap(Account account) {
    Map<String, SettingAttribute> settingAttributeIdObjectMap = new HashMap<>();
    try (HIterator<SettingAttribute> settingAttributes =
             new HIterator<>(wingsPersistence.createQuery(SettingAttribute.class)
                                 .filter(SettingAttributeKeys.accountId, account.getUuid())
                                 .filter(SettingAttributeKeys.category, SettingCategory.HELM_REPO)
                                 .fetch())) {
      while (settingAttributes.hasNext()) {
        SettingAttribute settingAttribute = settingAttributes.next();

        if (settingAttribute == null) {
          log.info(DEBUG_LINE + "setting attribute is null, skipping");
          continue;
        }

        if (settingAttribute.getValue() instanceof AmazonS3HelmRepoConfig
            || settingAttribute.getValue() instanceof GCSHelmRepoConfig) {
          settingAttributeIdObjectMap.put(settingAttribute.getUuid(), settingAttribute);
        }
      }
    }
    return settingAttributeIdObjectMap;
  }
}
