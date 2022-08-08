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
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
public class VerificationTaskCleanupSideKickExecutor implements SideKickExecutor<VerificationTaskCleanupSideKickData> {
  @VisibleForTesting
  static final Collection<? extends Class<? extends PersistentEntity>> ENTITIES_DELETE_BLACKLIST_BY_VERIFICATION_ID =
      Arrays.asList(DeploymentLogAnalysis.class, DeploymentTimeSeriesAnalysis.class);
  @VisibleForTesting
  static final List<Class<? extends PersistentEntity>> ENTITIES_TO_DELETE_BY_VERIFICATION_ID = Arrays.asList(
      ClusteredLog.class, TimeSeriesShortTermHistory.class, TimeSeriesRecord.class, AnalysisOrchestrator.class,
      AnalysisStateMachine.class, LearningEngineTask.class, LogRecord.class, HostRecord.class, LogAnalysisRecord.class,
      LogAnalysisResult.class, LogAnalysisCluster.class, TimeSeriesRiskSummary.class, TimeSeriesAnomalousPatterns.class,
      DataCollectionTask.class, TimeSeriesCumulativeSums.class, CVNGDemoDataIndex.class, SLIRecord.class);
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
      ENTITIES_TO_DELETE_BY_VERIFICATION_ID.forEach(entity
          -> hPersistence.delete(
              hPersistence.createQuery(entity).filter(VerificationTask.VERIFICATION_TASK_ID_KEY, verificationTaskId)));
      CVConfig cvConfig = sideKickInfo.getCvConfig();
      if (Objects.nonNull(cvConfig)
          && verificationTaskService.get(verificationTaskId)
                 .getTaskInfo()
                 .getTaskType()
                 .equals(VerificationTask.TaskType.LIVE_MONITORING)
          && cvConfigService
                 .list(cvConfig.getAccountId(), cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier(),
                     cvConfig.getFullyQualifiedIdentifier())
                 .isEmpty()) {
        deleteMonitoringSourcePerpetualTasks(cvConfig);
      }
      verificationTaskService.deleteVerificationTask(verificationTaskId);
      log.info("Cleanup complete for VerificationTask {}", verificationTaskId);
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
        cvConfig.getProjectIdentifier(), cvConfig.getFullyQualifiedIdentifier());
  }
}
