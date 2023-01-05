/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Application;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.yaml.errorhandling.GitSyncError;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class UpdateGitSyncErrorMigration implements Migration {
  @Inject WingsPersistence wingsPersistence;
  @Inject YamlHelper yamlHelper;
  @Inject AppService appService;
  private Map<String, String> appIdMap = new HashMap<>();
  @Override
  public void migrate() {
    Query<GitSyncError> query = wingsPersistence.createQuery(GitSyncError.class);

    try (HIterator<GitSyncError> records = new HIterator<>(query.fetch())) {
      while (records.hasNext()) {
        GitSyncError syncError = records.next();

        // AppId is already stamped
        if (StringUtils.isNotBlank(syncError.getAppId())) {
          continue;
        }

        String applicationId = GLOBAL_APP_ID;

        // Fetch appName from yamlPath, e.g. Setup/Applications/App1/Services/S1/index.yaml -> App1,
        // Setup/Artifact Servers/server.yaml -> null
        String appName = yamlHelper.getAppName(syncError.getYamlFilePath());

        if (StringUtils.isNotBlank(appName)) {
          String key = new StringBuilder(syncError.getAccountId()).append(":").append(appName).toString();

          if (!appIdMap.containsKey(key)) {
            Application application = appService.getAppByName(syncError.getAccountId(), appName);
            if (application != null) {
              appIdMap.put(key, application.getUuid());
            }
          }

          if (StringUtils.isNotBlank(appIdMap.get(key))) {
            applicationId = appIdMap.get(key);
          }
        }
        syncError.setAppId(applicationId);

        wingsPersistence.saveAndGet(GitSyncError.class, syncError);
        log.info(new StringBuilder()
                     .append("Updated GitSyncError with Id:< ")
                     .append(syncError.getUuid())
                     .append(">, with appId: <")
                     .append(applicationId)
                     .append(" >")
                     .toString());
      }
    }
  }
}
