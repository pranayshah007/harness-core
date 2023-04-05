/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.HashSet;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ELKMigrationCreateVerificationTaskLiveMonitoring extends CVNGBaseMigration {
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;

  @Override
  public void migrate() {
    log.info("starting ELKMigrationCreateVerificationTaskLiveMonitoring migration");
    Query<NextGenLogCVConfig> elkcvConfigQuery = getElkCVConfigs();
    try (HIterator<NextGenLogCVConfig> iterator = new HIterator<>(elkcvConfigQuery.fetch())) {
      while (iterator.hasNext()) {
        NextGenLogCVConfig cvConfig = iterator.next();
        VerificationTask liveMonitoringTask =
            verificationTaskService.getLiveMonitoringTask(cvConfig.getAccountId(), cvConfig.getUuid());
        if (Objects.isNull(liveMonitoringTask)) {
          log.info("starting ELKMigrationCreateVerificationTaskLiveMonitoring migration for cvConfigId: "
              + cvConfig.getUuid());
          String liveMonitoringVerificationTask = verificationTaskService.createLiveMonitoringVerificationTask(
              cvConfig.getAccountId(), cvConfig.getUuid(), cvConfig.getVerificationTaskTags());
          log.info(
              "Created verification task with {} for CVConfig: {}", liveMonitoringVerificationTask, cvConfig.getUuid());
        }
      }
    }
    log.info("completed ELKMigrationCreateVerificationTaskLiveMonitoring migration");
  }

  private Query<NextGenLogCVConfig> getElkCVConfigs() {
    return hPersistence.createQuery(NextGenLogCVConfig.class, new HashSet<>())
        .filter("className", NextGenLogCVConfig.class.getCanonicalName())
        .filter("dataSourceType", DataSourceType.ELASTICSEARCH);
  }
}
