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
import io.harness.cvng.analysis.entities.VerificationTaskBase.VerificationTaskBaseKeys;
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
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
public class VerificationTaskCleanupSideKickExecutor implements SideKickExecutor<VerificationTaskCleanupSideKickData> {
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
      VerificationTask task = verificationTaskService.get(verificationTaskId);

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
      // we want to add 6 hours to the end time since we anticipate some in-progress analyses that might come in while
      // we're deleting.
      cleanUpData(
          verificationTaskId, Instant.ofEpochMilli(task.getCreatedAt()), clock.instant().plus(6, ChronoUnit.HOURS));
      verificationTaskService.deleteVerificationTask(verificationTaskId);
      log.info("Cleanup complete for VerificationTask {}", verificationTaskId);
    }
  }

  // clean up data 2 days at a time
  private void cleanUpData(String verificationTaskId, Instant startTime, Instant endTime) {
    for (Instant curStartTime = startTime; endTime.isAfter(curStartTime);) {
      Instant currEndTime = curStartTime.plus(2, ChronoUnit.DAYS);
      if (currEndTime.isAfter(endTime)) {
        currEndTime = endTime;
      }
      for (Class<? extends PersistentEntity> clazz : ENTITIES_TO_DELETE_BY_VERIFICATION_ID) {
        hPersistence.delete(hPersistence.createQuery(clazz)
                                .filter(VerificationTask.VERIFICATION_TASK_ID_KEY, verificationTaskId)
                                .field(VerificationTaskBaseKeys.createdAt)
                                .greaterThanOrEq(curStartTime.toEpochMilli())
                                .field(VerificationTaskBaseKeys.createdAt)
                                .lessThanOrEq(currEndTime.toEpochMilli()));
        log.info("Deleted all the records for {} from {} until {}", verificationTaskId, curStartTime, currEndTime);
      }
      curStartTime = currEndTime.plusMillis(1);
    }
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
}
