/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.core.entities.DataCollectionTask.Type.SLI;
import static io.harness.cvng.core.utils.DateTimeUtils.roundUpTo5MinBoundary;

import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.DataCollectionInfo;
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
import io.harness.cvng.downtime.beans.EntityType;
import io.harness.cvng.downtime.beans.EntityUnavailabilityStatus;
import io.harness.cvng.downtime.services.api.EntityUnavailabilityStatusesService;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

@Slf4j
public class SLIDataCollectionTaskServiceImpl implements DataCollectionTaskManagementService<ServiceLevelIndicator> {
  @Inject private Map<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;
  @Inject private Clock clock;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private DataCollectionTaskService dataCollectionTaskService;

  @Inject private HPersistence hPersistence;
  @Inject private EntityUnavailabilityStatusesService entityUnavailabilityStatusesService;

  @Override
  public void handleCreateNextTask(ServiceLevelIndicator serviceLevelIndicator) {
    Optional<String> sliVerificationTaskId = verificationTaskService.getSLIVerificationTaskId(
        serviceLevelIndicator.getAccountId(), serviceLevelIndicator.getUuid());

    if (sliVerificationTaskId.isPresent()) {
      DataCollectionTask dataCollectionTask = dataCollectionTaskService.getLastDataCollectionTask(
          serviceLevelIndicator.getAccountId(), sliVerificationTaskId.get());
      if (dataCollectionTask == null) {
        enqueueFirstTask(serviceLevelIndicator);
      } else {
        processDataCollectionSuccess(dataCollectionTask);
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
  }

  private void enqueueFirstTask(ServiceLevelIndicator serviceLevelIndicator) {
    List<CVConfig> cvConfigList = serviceLevelIndicatorService.fetchCVConfigForSLI(serviceLevelIndicator);
    cvConfigList.forEach(cvConfig -> dataCollectionTaskService.populateMetricPack(cvConfig));
    TimeRange dataCollectionRange = serviceLevelIndicator.getFirstTimeDataCollectionTimeRange();
    DataCollectionTask dataCollectionTask = getDataCollectionTaskForSLI(cvConfigList, serviceLevelIndicator, false,
        dataCollectionRange.getStartTime(), dataCollectionRange.getEndTime());
    if (dataCollectionTask != null) {
      dataCollectionTaskService.save(dataCollectionTask);
      log.info("Enqueued serviceLevelIndicator successfully: {}", serviceLevelIndicator.getUuid());
    }
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
    Instant nextTaskEndTime = roundUpTo5MinBoundary(currentTime);
    if (nextTaskStartTime.isBefore(prevSLITask.getDataCollectionPastTimeCutoff(currentTime))) {
      nextTaskStartTime = prevSLITask.getDataCollectionPastTimeCutoff(currentTime);
      serviceLevelIndicatorService.enqueueDataCollectionFailureInstanceAndTriggerAnalysis(
          prevSLITask.getVerificationTaskId(), prevSLITask.getEndTime(), nextTaskStartTime, serviceLevelIndicator);
      log.info("Restarting Data collection startTime for task {} : {}", prevSLITask.getVerificationTaskId(),
          nextTaskStartTime);
    }
    DataCollectionTask dataCollectionTask =
        getDataCollectionTaskForSLI(cvConfigList, serviceLevelIndicator, false, nextTaskStartTime, nextTaskEndTime);
    if (dataCollectionTask != null) {
      if (prevSLITask.getStatus() != DataCollectionExecutionStatus.SUCCESS) {
        dataCollectionTask.setValidAfter(dataCollectionTask.getNextValidAfter(nextTaskEndTime));
      }
      dataCollectionTaskService.validateIfAlreadyExists(dataCollectionTask);
      dataCollectionTaskService.save(dataCollectionTask);
      log.info("Created data collection task {}", dataCollectionTask);
    }
  }

  @Override
  public void processDataCollectionSuccess(DataCollectionTask dataCollectionTask) {
    SLIDataCollectionTask sliDataCollectionTask = (SLIDataCollectionTask) dataCollectionTask;
    String sliId = verificationTaskService.getSliId(dataCollectionTask.getVerificationTaskId());
    if (sliDataCollectionTask.isRestore()) {
      entityUnavailabilityStatusesService.updateStatusOfEntity(EntityType.SLO, sliId,
          dataCollectionTask.getStartTime().getEpochSecond(), dataCollectionTask.getEndTime().getEpochSecond(),
          EntityUnavailabilityStatus.DATA_COLLECTION_FAILED, EntityUnavailabilityStatus.DATA_RECOLLECTION_PASSED);
    }
  }

  @Override
  public void processDataCollectionFailure(DataCollectionTask dataCollectionTask) {
    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorService.get(verificationTaskService.getSliId(dataCollectionTask.getVerificationTaskId()));
    SLIDataCollectionTask sliDataCollectionTask = (SLIDataCollectionTask) dataCollectionTask;
    if (!sliDataCollectionTask.isRestore()) {
      serviceLevelIndicatorService.enqueueDataCollectionFailureInstanceAndTriggerAnalysis(
          dataCollectionTask.getVerificationTaskId(), dataCollectionTask.getStartTime(),
          dataCollectionTask.getEndTime(), serviceLevelIndicator);
    }
  }

  public void createRestoreTask(ServiceLevelIndicator serviceLevelIndicator, Instant startTime, Instant endTime) {
    List<CVConfig> cvConfigList = serviceLevelIndicatorService.fetchCVConfigForSLI(serviceLevelIndicator);
    cvConfigList.forEach(cvConfig -> dataCollectionTaskService.populateMetricPack(cvConfig));
    DataCollectionTask dataCollectionTask =
        getDataCollectionTaskForSLI(cvConfigList, serviceLevelIndicator, true, startTime, endTime);
    if (dataCollectionTask != null) {
      dataCollectionTaskService.save(dataCollectionTask);
      log.info("Enqueued Restore Task for serviceLevelIndicator successfully: {}", serviceLevelIndicator.getUuid());
    }
  }

  private DataCollectionTask getDataCollectionTaskForSLI(List<CVConfig> cvConfigList,
      ServiceLevelIndicator serviceLevelIndicator, boolean isRestore, Instant startTime, Instant endTime) {
    if (CollectionUtils.isEmpty(cvConfigList)) {
      log.warn("[ERROR]: CVConfig list is empty for serviceLevelIndicator {}", serviceLevelIndicator.getUuid());
    } else {
      CVConfig cvConfigForVerificationTask = cvConfigList.get(0);
      String dataCollectionWorkerId =
          monitoringSourcePerpetualTaskService.getLiveMonitoringWorkerId(cvConfigForVerificationTask.getAccountId(),
              cvConfigForVerificationTask.getOrgIdentifier(), cvConfigForVerificationTask.getProjectIdentifier(),
              cvConfigForVerificationTask.getConnectorIdentifier(), cvConfigForVerificationTask.getIdentifier());
      DataCollectionInfo dataCollectionInfo =
          dataSourceTypeDataCollectionInfoMapperMap.get(cvConfigList.get(0).getType())
              .toDataCollectionInfo(cvConfigList, serviceLevelIndicator);
      Optional<String> sliVerificationTaskId = verificationTaskService.getSLIVerificationTaskId(
          cvConfigForVerificationTask.getAccountId(), serviceLevelIndicator.getUuid());

      if (sliVerificationTaskId.isPresent() && Objects.nonNull(dataCollectionInfo)) {
        return SLIDataCollectionTask.builder()
            .accountId(serviceLevelIndicator.getAccountId())
            .type(SLI)
            .dataCollectionWorkerId(dataCollectionWorkerId)
            .status(DataCollectionExecutionStatus.QUEUED)
            .startTime(startTime)
            .endTime(endTime)
            .isRestore(isRestore)
            .queueAnalysis(!isRestore)
            .verificationTaskId(sliVerificationTaskId.get())
            .dataCollectionInfo(dataCollectionInfo)
            .build();
      }
    }
    return null;
  }
}
