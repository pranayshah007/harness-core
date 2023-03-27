/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.core.entities.DataCollectionTask.Type.SLI;

import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.SLIDataCollectionTask;
import io.harness.cvng.core.services.api.DataCollectionSLIInfoMapper;
import io.harness.cvng.core.services.api.DataCollectionTaskManagementService;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SLIDataCollectionTaskServiceImpl implements DataCollectionTaskManagementService<ServiceLevelIndicator> {
  @Inject private Map<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;
  @Inject private Clock clock;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private DataCollectionTaskService dataCollectionTaskService;

  @Override
  public void handleCreateNextTask(ServiceLevelIndicator serviceLevelIndicator) {
    String sliVerificationTaskId = verificationTaskService.getSLIVerificationTaskId(
        serviceLevelIndicator.getAccountId(), serviceLevelIndicator.getUuid());

    DataCollectionTask dataCollectionTask = dataCollectionTaskService.getLastDataCollectionTask(
        serviceLevelIndicator.getAccountId(), sliVerificationTaskId);
    if (dataCollectionTask == null) {
      enqueueFirstTask(serviceLevelIndicator);
    } else {
      if (dataCollectionTask.shouldHandlerCreateNextTask(clock.instant())) {
        log.info("Creating next task for sliId: {}", sliVerificationTaskId);
        createNextTask(dataCollectionTask);
        log.warn(
            "Recovered from next task creation issue. DataCollectionTask uuid: {}, account: {}, projectIdentifier: {}, orgIdentifier: {}, ",
            dataCollectionTask.getUuid(), serviceLevelIndicator.getAccountId(),
            serviceLevelIndicator.getProjectIdentifier(), serviceLevelIndicator.getOrgIdentifier());
      }
    }
  }

  private void enqueueFirstTask(ServiceLevelIndicator serviceLevelIndicator) {
    List<CVConfig> cvConfigList = serviceLevelIndicatorService.fetchCVConfigForSLI(serviceLevelIndicator);
    cvConfigList.forEach(cvConfig -> dataCollectionTaskService.populateMetricPack(cvConfig));
    TimeRange dataCollectionRange = serviceLevelIndicator.getFirstTimeDataCollectionTimeRange();
    DataCollectionTask dataCollectionTask = getDataCollectionTaskForSLI(
        cvConfigList, serviceLevelIndicator, dataCollectionRange.getStartTime(), dataCollectionRange.getEndTime());
    dataCollectionTaskService.save(dataCollectionTask);
    log.info("Enqueued serviceLevelIndicator successfully: {}", serviceLevelIndicator.getUuid());
  }

  @Override
  public void createNextTask(DataCollectionTask prevTask) {
    SLIDataCollectionTask prevSLITask = (SLIDataCollectionTask) prevTask;
    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.get(verificationTaskService.getSliId(prevSLITask.getVerificationTaskId()));
    if (serviceLevelIndicator == null) {
      log.info("ServiceLevelIndicator no longer exists for verificationTaskId {}", prevSLITask.getVerificationTaskId());
      return;
    }
    List<CVConfig> cvConfigList = serviceLevelIndicatorService.fetchCVConfigForSLI(serviceLevelIndicator);
    cvConfigList.forEach(cvConfig -> dataCollectionTaskService.populateMetricPack(cvConfig));
    Instant nextTaskStartTime = prevSLITask.getEndTime();
    Instant currentTime = clock.instant();
    if (nextTaskStartTime.isBefore(prevSLITask.getDataCollectionPastTimeCutoff(currentTime))) {
      nextTaskStartTime = prevSLITask.getDataCollectionPastTimeCutoff(currentTime);
      serviceLevelIndicatorService.enqueueDataCollectionFailureInstanceAndTriggerAnalysis(
          prevSLITask.getVerificationTaskId(), prevSLITask.getEndTime().plus(1, ChronoUnit.MINUTES),
          nextTaskStartTime.minus(1, ChronoUnit.MINUTES), serviceLevelIndicator);
      log.info("Restarting Data collection startTime for task {} : {}", prevSLITask.getVerificationTaskId(),
          nextTaskStartTime);
    }
    DataCollectionTask dataCollectionTask = getDataCollectionTaskForSLI(
        cvConfigList, serviceLevelIndicator, nextTaskStartTime, nextTaskStartTime.plus(5, ChronoUnit.MINUTES));
    if (prevSLITask.getStatus() != DataCollectionExecutionStatus.SUCCESS) {
      dataCollectionTask.setValidAfter(dataCollectionTask.getNextValidAfter(clock.instant()));
    }
    dataCollectionTaskService.validateIfAlreadyExists(dataCollectionTask);
    dataCollectionTaskService.save(dataCollectionTask);
    log.info("Created data collection task {}", dataCollectionTask);
  }

  private DataCollectionTask getDataCollectionTaskForSLI(
      List<CVConfig> cvConfigList, ServiceLevelIndicator serviceLevelIndicator, Instant startTime, Instant endTime) {
    CVConfig cvConfigForVerificationTask = cvConfigList.get(0);
    String dataCollectionWorkerId =
        monitoringSourcePerpetualTaskService.getLiveMonitoringWorkerId(cvConfigForVerificationTask.getAccountId(),
            cvConfigForVerificationTask.getOrgIdentifier(), cvConfigForVerificationTask.getProjectIdentifier(),
            cvConfigForVerificationTask.getConnectorIdentifier(), cvConfigForVerificationTask.getIdentifier());
    return SLIDataCollectionTask.builder()
        .accountId(serviceLevelIndicator.getAccountId())
        .type(SLI)
        .dataCollectionWorkerId(dataCollectionWorkerId)
        .status(DataCollectionExecutionStatus.QUEUED)
        .startTime(startTime)
        .endTime(endTime)
        .verificationTaskId(verificationTaskService.getSLIVerificationTaskId(
            cvConfigForVerificationTask.getAccountId(), serviceLevelIndicator.getUuid()))
        .dataCollectionInfo(dataSourceTypeDataCollectionInfoMapperMap.get(cvConfigList.get(0).getType())
                                .toDataCollectionInfo(cvConfigList, serviceLevelIndicator))
        .build();
  }
}
