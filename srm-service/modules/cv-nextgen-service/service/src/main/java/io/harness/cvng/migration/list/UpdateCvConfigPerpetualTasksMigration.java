/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofMillis;

import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.DataCollectionTask.DataCollectionTaskKeys;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateCvConfigPerpetualTasksMigration implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;

  @Override
  public void migrate() {
    log.info("migration started");
    List<CVConfig> cvConfigs = hPersistence.createQuery(CVConfig.class, excludeAuthority).asList();
    Set<CVConfigKey> cvConfigKeys = new HashSet<>();
    cvConfigs.forEach(cvConfig -> {
      try {
        log.info("deleting perpetual task for {}", cvConfig);
        // set iterator to not execute for next 5 mins
        hPersistence.update(cvConfig,
            hPersistence.createUpdateOperations(CVConfig.class)
                .set("dataCollectionTaskIteration", Instant.now().plus(5, ChronoUnit.MINUTES).toEpochMilli()));

        hPersistence.update(cvConfig,
            hPersistence.createUpdateOperations(CVConfig.class)
                .set("firstTaskQueued", true) // This flag is removed now.
                .unset("perpetualTaskId"));
        cvConfigKeys.add(CVConfigKey.builder()
                             .accountId(cvConfig.getAccountId())
                             .orgIdentifier(cvConfig.getOrgIdentifier())
                             .projectIdentifier(cvConfig.getProjectIdentifier())
                             .monitoringSourceIdentifier(cvConfig.getIdentifier())
                             .connectorIdentifier(cvConfig.getConnectorIdentifier())
                             .build());
        String dataCollectionWorkerId = monitoringSourcePerpetualTaskService.getLiveMonitoringWorkerId(
            cvConfig.getAccountId(), cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier(),
            cvConfig.getConnectorIdentifier(), cvConfig.getIdentifier());
        hPersistence.update(hPersistence.createQuery(DataCollectionTask.class, excludeAuthority)
                                .filter(DataCollectionTaskKeys.dataCollectionWorkerId, cvConfig.getUuid())
                                .field(DataCollectionTaskKeys.status)
                                .notEqual(DataCollectionExecutionStatus.SUCCESS),
            hPersistence.createUpdateOperations(DataCollectionTask.class)
                .set(DataCollectionTaskKeys.dataCollectionWorkerId, dataCollectionWorkerId));
        sleep(ofMillis(100));
      } catch (Exception e) {
        log.error("error deleting perpetual task for {}", cvConfig, e);
      }
    });

    cvConfigKeys.forEach(cvConfigKey
        -> monitoringSourcePerpetualTaskService.createTask(cvConfigKey.getAccountId(), cvConfigKey.getOrgIdentifier(),
            cvConfigKey.getProjectIdentifier(), cvConfigKey.getConnectorIdentifier(),
            cvConfigKey.getMonitoringSourceIdentifier(), false));
    log.info("migration done");
  }
  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }

  @Value
  @Builder
  private static class CVConfigKey {
    private String accountId;
    private String orgIdentifier;
    private String projectIdentifier;
    private String connectorIdentifier;
    private String monitoringSourceIdentifier;
  }
}
