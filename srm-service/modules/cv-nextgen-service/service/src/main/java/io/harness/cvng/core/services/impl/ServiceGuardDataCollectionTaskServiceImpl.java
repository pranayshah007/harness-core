/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.core.entities.DataCollectionTask.Type.SERVICE_GUARD;

import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.DeploymentDataCollectionTask;
import io.harness.cvng.core.entities.ServiceGuardDataCollectionTask;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.DataCollectionInfoMapper;
import io.harness.cvng.core.services.api.DataCollectionTaskManagementService;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServiceGuardDataCollectionTaskServiceImpl implements DataCollectionTaskManagementService<CVConfig> {
  @Inject private Map<DataSourceType, DataCollectionInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;
  @Inject private Clock clock;
  @Inject private CVConfigService cvConfigService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private DataCollectionTaskService dataCollectionTaskService;

  @Inject VerificationJobInstanceService verificationJobInstanceService;

  @Override
  public void handleCreateNextTask(CVConfig cvConfig) {
    Preconditions.checkState(cvConfig.isEnabled(), "CVConfig should be enabled");
    String serviceGuardVerificationTaskId =
        verificationTaskService.getServiceGuardVerificationTaskId(cvConfig.getAccountId(), cvConfig.getUuid());

    DataCollectionTask dataCollectionTask =
        dataCollectionTaskService.getLastDataCollectionTask(cvConfig.getAccountId(), serviceGuardVerificationTaskId);
    if (dataCollectionTask == null) {
      enqueueFirstTask(cvConfig);
    } else {
      if (Instant.ofEpochMilli(dataCollectionTask.getLastUpdatedAt())
              .isBefore(clock.instant().minus(Duration.ofMinutes(2)))) {
        if (dataCollectionTask.getStatus().equals(DataCollectionExecutionStatus.SUCCESS)) {
          createNextTask(dataCollectionTask);
          log.warn(
              "Recovered from next task creation issue. DataCollectionTask uuid: {}, account: {}, projectIdentifier: {}, orgIdentifier: {}, ",
              dataCollectionTask.getUuid(), cvConfig.getAccountId(), cvConfig.getProjectIdentifier(),
              cvConfig.getOrgIdentifier());
        } else if (!DataCollectionExecutionStatus.getNonFinalStatuses().contains(dataCollectionTask.getStatus())) {
          enqueueFirstTask(cvConfig);
        }
      }
    }
  }

  @Override
  public void createNextTask(DataCollectionTask prevTask) {
    ServiceGuardDataCollectionTask prevServiceGuardTask = (ServiceGuardDataCollectionTask) prevTask;

    CVConfig cvConfig =
        cvConfigService.get(verificationTaskService.getCVConfigId(prevServiceGuardTask.getVerificationTaskId()));
    if (cvConfig == null) {
      log.info("CVConfig no longer exists for verificationTaskId {}", prevServiceGuardTask.getVerificationTaskId());
      return;
    }
    if (!cvConfig.isEnabled()) {
      log.info("Not creating next task as CVConfig is disabled. cvConfigId: {}", cvConfig.getUuid());
      return;
    }
    dataCollectionTaskService.populateMetricPack(cvConfig);
    Instant nextTaskStartTime = prevServiceGuardTask.getEndTime();
    Instant currentTime = clock.instant();
    if (nextTaskStartTime.isBefore(prevServiceGuardTask.getDataCollectionPastTimeCutoff(currentTime))) {
      nextTaskStartTime = prevServiceGuardTask.getDataCollectionPastTimeCutoff(currentTime);
      log.info("Restarting Data collection startTime: {}", nextTaskStartTime);
    }
    DataCollectionTask dataCollectionTask =
        getDataCollectionTask(cvConfig, nextTaskStartTime, nextTaskStartTime.plus(5, ChronoUnit.MINUTES));
    if (prevServiceGuardTask.getStatus() != DataCollectionExecutionStatus.SUCCESS) {
      dataCollectionTask.setRetryCount(prevServiceGuardTask.getRetryCount());
      dataCollectionTask.setValidAfter(dataCollectionTask.getNextValidAfter(clock.instant()));
    }
    dataCollectionTaskService.validateIfAlreadyExists(dataCollectionTask);
    dataCollectionTaskService.save(dataCollectionTask);
  }

  @Override
  public void processDataCollectionSuccess(DataCollectionTask dataCollectionTask) {
    if (dataCollectionTask instanceof DeploymentDataCollectionTask) {
      verificationJobInstanceService.logProgress(VerificationJobInstance.DataCollectionProgressLog.builder()
                                                     .executionStatus(dataCollectionTask.getStatus())
                                                     .isFinalState(false)
                                                     .startTime(dataCollectionTask.getStartTime())
                                                     .endTime(dataCollectionTask.getEndTime())
                                                     .verificationTaskId(dataCollectionTask.getVerificationTaskId())
                                                     .log("Data collection task successful")
                                                     .build());
    }
  }

  @Override
  public void processDataCollectionFailure(DataCollectionTask dataCollectionTask) {
    if (dataCollectionTask instanceof DeploymentDataCollectionTask) {
      verificationJobInstanceService.logProgress(
          VerificationJobInstance.DataCollectionProgressLog.builder()
              .executionStatus(dataCollectionTask.getStatus())
              .isFinalState(false)
              .startTime(dataCollectionTask.getStartTime())
              .endTime(dataCollectionTask.getEndTime())
              .verificationTaskId(dataCollectionTask.getVerificationTaskId())
              .log("Data collection failed with exception: " + dataCollectionTask.getException())
              .build());
    }
  }

  private void enqueueFirstTask(CVConfig cvConfig) {
    dataCollectionTaskService.populateMetricPack(cvConfig);
    TimeRange dataCollectionRange = cvConfig.getFirstTimeDataCollectionTimeRange();
    DataCollectionTask dataCollectionTask =
        getDataCollectionTask(cvConfig, dataCollectionRange.getStartTime(), dataCollectionRange.getEndTime());
    dataCollectionTaskService.save(dataCollectionTask);
    log.info("Enqueued cvConfigId successfully: {}", cvConfig.getUuid());
  }

  private DataCollectionTask getDataCollectionTask(CVConfig cvConfig, Instant startTime, Instant endTime) {
    String dataCollectionWorkerId = monitoringSourcePerpetualTaskService.getLiveMonitoringWorkerId(
        cvConfig.getAccountId(), cvConfig.getOrgIdentifier(), cvConfig.getProjectIdentifier(),
        cvConfig.getConnectorIdentifier(), cvConfig.getIdentifier());
    return ServiceGuardDataCollectionTask.builder()
        .accountId(cvConfig.getAccountId())
        .type(SERVICE_GUARD)
        .dataCollectionWorkerId(dataCollectionWorkerId)
        .status(DataCollectionExecutionStatus.QUEUED)
        .startTime(startTime)
        .endTime(endTime)
        .verificationTaskId(
            verificationTaskService.getServiceGuardVerificationTaskId(cvConfig.getAccountId(), cvConfig.getUuid()))
        .dataCollectionInfo(dataSourceTypeDataCollectionInfoMapperMap.get(cvConfig.getType())
                                .toDataCollectionInfo(cvConfig, TaskType.LIVE_MONITORING))
        .build();
  }
}
