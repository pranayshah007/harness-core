/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.sidekickexecutors;

import io.harness.cvng.analysis.entities.ClusteredLog;
import io.harness.cvng.analysis.entities.DeploymentLogAnalysis;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LogAnalysisCluster;
import io.harness.cvng.analysis.entities.LogAnalysisRecord;
import io.harness.cvng.analysis.entities.LogAnalysisResult;
import io.harness.cvng.analysis.entities.TimeSeriesAnomalousPatterns;
import io.harness.cvng.analysis.entities.TimeSeriesCumulativeSums;
import io.harness.cvng.analysis.entities.TimeSeriesRiskSummary;
import io.harness.cvng.analysis.entities.TimeSeriesShortTermHistory;
import io.harness.cvng.core.beans.sidekick.VerificationTaskCleanupSideKickData;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.HostRecord;
import io.harness.cvng.core.entities.LogRecord;
import io.harness.cvng.core.entities.TimeSeriesRecord;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.demo.CVNGDemoDataIndex;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.SideKickExecutor;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.CVNGObjectUtils;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket;
import io.harness.cvng.servicelevelobjective.entities.SLIRecordBucket.SLIRecordBucketKeys;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.WriteResult;
import dev.morphia.AdvancedDatastore;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
public class VerificationTaskCleanupSideKickExecutor implements SideKickExecutor<VerificationTaskCleanupSideKickData> {
  @VisibleForTesting protected static final int RECORDS_TO_BE_DELETED_IN_SINGLE_BATCH = 100;
  @VisibleForTesting
  static final Collection<? extends Class<? extends PersistentEntity>> ENTITIES_DELETE_BLACKLIST_BY_VERIFICATION_ID =
      Arrays.asList(DeploymentLogAnalysis.class, DeploymentTimeSeriesAnalysis.class);
  @VisibleForTesting
  static final List<Class<? extends PersistentEntity>> ENTITIES_TO_DELETE_BY_VERIFICATION_ID =
      Arrays.asList(ClusteredLog.class, TimeSeriesShortTermHistory.class, TimeSeriesRecord.class,
          AnalysisOrchestrator.class, AnalysisStateMachine.class, LearningEngineTask.class, LogRecord.class,
          HostRecord.class, LogAnalysisRecord.class, LogAnalysisResult.class, LogAnalysisCluster.class,
          TimeSeriesRiskSummary.class, TimeSeriesAnomalousPatterns.class, DataCollectionTask.class,
          TimeSeriesCumulativeSums.class, CVNGDemoDataIndex.class, SLIRecord.class, CompositeSLORecord.class);

  @VisibleForTesting
  static final Map<Class<? extends PersistentEntity>, String> ENTITIES_TO_DELETE_BY_ID_MAP =
      Map.of(SLIRecordBucket.class, SLIRecordBucketKeys.sliId);
  @Inject private Clock clock;
  @Inject private HPersistence hPersistence;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private CVConfigService cvConfigService;

  @Override
  public void execute(VerificationTaskCleanupSideKickData sideKickInfo) {
    log.info("SidekickInfo {}", sideKickInfo);
    String verificationTaskId = sideKickInfo.getVerificationTaskId();
    if (StringUtils.isNotBlank(verificationTaskId)) {
      log.info("Triggering cleanup for VerificationTask {}", verificationTaskId);

      CVConfig cvConfig = sideKickInfo.getCvConfig();
      // delete perp tasks first. We do not want new data to come in when we're deleting old data.
      if (Objects.nonNull(cvConfig)
          && verificationTaskService.get(verificationTaskId)
                 .getTaskInfo()
                 .getTaskType()
                 .equals(VerificationTask.TaskType.LIVE_MONITORING)
          && CollectionUtils
                 .emptyIfNull(cvConfigService.list(cvConfig.getAccountId(), cvConfig.getOrgIdentifier(),
                     cvConfig.getProjectIdentifier(), cvConfig.getFullyQualifiedIdentifier()))
                 .stream()
                 .noneMatch(
                     cvConfigInDB -> cvConfigInDB.getConnectorIdentifier().equals(cvConfig.getConnectorIdentifier()))) {
        deleteMonitoringSourcePerpetualTasks(cvConfig);
      }
      cleanUpData(verificationTaskId);
      verificationTaskService.deleteVerificationTask(verificationTaskId);
      log.info("Cleanup complete for VerificationTask {}", verificationTaskId);
    }
  }

  private void cleanUpData(String verificationTaskId) {
    for (Class<? extends PersistentEntity> clazz : ENTITIES_TO_DELETE_BY_VERIFICATION_ID) {
      cleanUpDataForSingleEntity(verificationTaskId, clazz, VerificationTask.VERIFICATION_TASK_ID_KEY);
    }
    for (Class<? extends PersistentEntity> clazz : ENTITIES_TO_DELETE_BY_ID_MAP.keySet()) {
      cleanUpDataForSingleEntity(verificationTaskId, clazz, ENTITIES_TO_DELETE_BY_ID_MAP.get(clazz));
    }
  }

  private void cleanUpDataForSingleEntity(
      String verificationTaskId, Class<? extends PersistentEntity> entity, String field) {
    int numberOfRecordsDeleted;
    Query<? extends PersistentEntity> query =
        hPersistence.createQuery(entity).filter(field, verificationTaskId).project(UuidAware.UUID_KEY, true);
    FindOptions findOptions = new FindOptions().limit(RECORDS_TO_BE_DELETED_IN_SINGLE_BATCH);
    do {
      numberOfRecordsDeleted = deleteSingleBatch(entity, query, findOptions, verificationTaskId);
    } while (numberOfRecordsDeleted > 0);
  }

  private int deleteSingleBatch(Class<? extends PersistentEntity> entity, Query<? extends PersistentEntity> query,
      FindOptions findOptions, String verificationTaskId) {
    List<? extends PersistentEntity> recordsToBeDeleted = query.find(findOptions).toList();
    int numberOfRecordsToBeDeleted = recordsToBeDeleted.size();
    int numberOfRecordsDeleted = 0;
    if (numberOfRecordsToBeDeleted > 0) {
      Set<?> recordIdsTobeDeleted = recordsToBeDeleted.stream()
                                        .map(recordToBeDeleted -> ((UuidAware) recordToBeDeleted).getUuid())
                                        .map(CVNGObjectUtils::convertToObjectIdIfRequired)
                                        .collect(Collectors.toSet());
      Query<? extends PersistentEntity> queryToFindRecordsToBeDeleted =
          hPersistence.createQuery(entity).field(UuidAware.UUID_KEY).in(recordIdsTobeDeleted);
      log.info("Deleting {} records of entity {} for the verificationTaskId {}", numberOfRecordsToBeDeleted,
          entity.getSimpleName(), verificationTaskId);
      numberOfRecordsDeleted = deleteRecords(queryToFindRecordsToBeDeleted);
      log.info("Deleted {} records of entity {} for the verificationTaskId {}", numberOfRecordsDeleted,
          entity.getSimpleName(), verificationTaskId);
      if (numberOfRecordsToBeDeleted != numberOfRecordsDeleted) {
        log.warn(
            "Number of records deleted: {} is not equal to the number of records to be deleted: {} for entity {} for the verificationTaskId {}",
            numberOfRecordsDeleted, numberOfRecordsToBeDeleted, entity.getSimpleName(), verificationTaskId);
      }
    }
    return numberOfRecordsDeleted;
  }

  @Override
  public RetryData shouldRetry(int lastRetryCount) {
    if (lastRetryCount < 5) {
      return RetryData.builder().shouldRetry(true).nextRetryTime(clock.instant().plusSeconds(300)).build();
    }
    return RetryData.builder().shouldRetry(false).build();
  }

  private void deleteMonitoringSourcePerpetualTasks(CVConfig cvConfig) {
    monitoringSourcePerpetualTaskService.deleteTask(cvConfig.getAccountId(), cvConfig.getOrgIdentifier(),
        cvConfig.getProjectIdentifier(), cvConfig.getFullyQualifiedIdentifier(), cvConfig.getConnectorIdentifier());
  }

  @VisibleForTesting
  <T extends PersistentEntity> int deleteRecords(Query<T> query) {
    AdvancedDatastore datastore = hPersistence.getDatastore(query.getEntityClass());
    return HPersistence.retry(() -> {
      WriteResult result = datastore.delete(query);
      return result.getN();
    });
  }
}
