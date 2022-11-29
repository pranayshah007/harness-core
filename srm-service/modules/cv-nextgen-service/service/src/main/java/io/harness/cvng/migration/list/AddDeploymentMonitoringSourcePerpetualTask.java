/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.cvng.beans.DataCollectionExecutionStatus.QUEUED;
import static io.harness.cvng.beans.DataCollectionExecutionStatus.RUNNING;
import static io.harness.persistence.HPersistence.DEFAULT_STORE;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.DataCollectionTask.DataCollectionTaskKeys;
import io.harness.cvng.core.entities.DataCollectionTask.Type;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask.MonitoringSourcePerpetualTaskKeys;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HPersistence;

import com.google.api.client.util.Charsets;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.mongodb.MongoCommandException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.query.UpdateResults;

@Slf4j
public class AddDeploymentMonitoringSourcePerpetualTask implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Override
  public void migrate() {
    try {
      hPersistence.getCollection(DEFAULT_STORE, MonitoringSourcePerpetualTask.class.getAnnotation(Entity.class).value())
          .dropIndex("insert_index");
    } catch (MongoCommandException e) {
      log.warn("Index not found", e);
    }

    List<MonitoringSourcePerpetualTask> monitoringSourcePerpetualTasks =
        hPersistence.createQuery(MonitoringSourcePerpetualTask.class, excludeAuthority).asList();
    log.info("Trying to migrate {}", monitoringSourcePerpetualTasks);
    Map<String, String> oldToNewWorkerIdmap = new HashMap<>();
    // set iterator to not execute for next 5 mins
    monitoringSourcePerpetualTasks.forEach(monitoringSourcePerpetualTask -> {
      try {
        log.info("Starting migration for {}", monitoringSourcePerpetualTask);
        hPersistence.update(monitoringSourcePerpetualTask,
            hPersistence.createUpdateOperations(MonitoringSourcePerpetualTask.class)
                .set(MonitoringSourcePerpetualTaskKeys.dataCollectionTaskIteration,
                    Instant.now().plus(5, ChronoUnit.MINUTES).toEpochMilli()));
        oldToNewWorkerIdmap.put(
            monitoringSourcePerpetualTask.getDataCollectionWorkerId(), getNewWorkerId(monitoringSourcePerpetualTask));
        createTask(monitoringSourcePerpetualTask.getAccountId(), monitoringSourcePerpetualTask.getOrgIdentifier(),
            monitoringSourcePerpetualTask.getProjectIdentifier(),
            monitoringSourcePerpetualTask.getConnectorIdentifier(),
            monitoringSourcePerpetualTask.getMonitoringSourceIdentifier());
        hPersistence.delete(monitoringSourcePerpetualTask);
        log.info("Finished migration for {}", monitoringSourcePerpetualTask);
      } catch (Exception e) {
        log.error("Failed to migrate {}", monitoringSourcePerpetualTask, e);
      }
    });
    oldToNewWorkerIdmap.forEach((oldWorkerId, newWorkerId) -> {
      UpdateResults updateResults =
          hPersistence.update(hPersistence.createQuery(DataCollectionTask.class)
                                  .filter(DataCollectionTaskKeys.dataCollectionWorkerId, oldWorkerId)
                                  .filter(DataCollectionTaskKeys.type, Type.SERVICE_GUARD)
                                  .field(DataCollectionTaskKeys.status)
                                  .in(Arrays.asList(QUEUED, RUNNING)),
              hPersistence.createUpdateOperations(DataCollectionTask.class)
                  .set(DataCollectionTaskKeys.dataCollectionWorkerId, newWorkerId));
      log.info("Updated data collections tasks for worker ID: {} to newWorkerId {}, updateResults: {}", oldWorkerId,
          newWorkerId, updateResults.getUpdatedCount());
    });
  }

  private String getNewWorkerId(MonitoringSourcePerpetualTask monitoringSourcePerpetualTask) {
    StringJoiner stringJoiner = new StringJoiner(":")
                                    .add(monitoringSourcePerpetualTask.getAccountId())
                                    .add(monitoringSourcePerpetualTask.getOrgIdentifier())
                                    .add(monitoringSourcePerpetualTask.getProjectIdentifier())
                                    .add(monitoringSourcePerpetualTask.getMonitoringSourceIdentifier())
                                    .add(monitoringSourcePerpetualTask.getConnectorIdentifier())
                                    .add(MonitoringSourcePerpetualTask.VerificationType.LIVE_MONITORING.name());
    return UUID.nameUUIDFromBytes(stringJoiner.toString().getBytes(Charsets.UTF_8)).toString();
  }

  public void createTask(String accountId, String orgIdentifier, String projectIdentifier, String connectorIdentifier,
      String monitoringSourceIdentifier) {
    hPersistence.save(
        Lists.newArrayList(MonitoringSourcePerpetualTask.builder()
                               .accountId(accountId)
                               .orgIdentifier(orgIdentifier)
                               .projectIdentifier(projectIdentifier)
                               .connectorIdentifier(connectorIdentifier)
                               .monitoringSourceIdentifier(monitoringSourceIdentifier)
                               .verificationType(MonitoringSourcePerpetualTask.VerificationType.LIVE_MONITORING)
                               .build(),
            MonitoringSourcePerpetualTask.builder()
                .accountId(accountId)
                .orgIdentifier(orgIdentifier)
                .projectIdentifier(projectIdentifier)
                .connectorIdentifier(connectorIdentifier)
                .monitoringSourceIdentifier(monitoringSourceIdentifier)
                .verificationType(MonitoringSourcePerpetualTask.VerificationType.DEPLOYMENT)
                .build()));
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
