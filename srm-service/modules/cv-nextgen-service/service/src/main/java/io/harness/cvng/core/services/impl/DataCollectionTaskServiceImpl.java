/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.core.entities.DataCollectionTask.Type.DEPLOYMENT;
import static io.harness.cvng.core.entities.DataCollectionTask.Type.SERVICE_GUARD;
import static io.harness.cvng.core.entities.DataCollectionTask.Type.SLI;
import static io.harness.cvng.core.services.CVNextGenConstants.CVNG_MAX_PARALLEL_THREADS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.cvng.analysis.entities.VerificationTaskBase.VerificationTaskBaseKeys;
import io.harness.cvng.beans.CVNGPerpetualTaskDTO;
import io.harness.cvng.beans.CVNGPerpetualTaskState;
import io.harness.cvng.beans.CVNGTaskMetadataConstants;
import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.DataCollectionTaskDTO;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult.ExecutionLog;
import io.harness.cvng.beans.cvnglog.CVNGLogTag;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.DataCollectionTask.DataCollectionTaskKeys;
import io.harness.cvng.core.entities.DeploymentDataCollectionTask;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.SLIDataCollectionTask;
import io.harness.cvng.core.services.api.DataCollectionTaskManagementService;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.ExecutionLogService;
import io.harness.cvng.core.services.api.ExecutionLogger;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.cvng.core.utils.CVNGTaskMetadataUtils;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import io.fabric8.utils.Lists;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataCollectionTaskServiceImpl implements DataCollectionTaskService {
  @Inject private HPersistence hPersistence;
  @Inject private Clock clock;
  @Inject private MetricPackService metricPackService;
  @Inject private OrchestrationService orchestrationService;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject
  private Map<DataCollectionTask.Type, DataCollectionTaskManagementService>
      dataCollectionTaskManagementServiceMapBinder;
  @Inject private ExecutionLogService executionLogService;

  @Override
  public void save(DataCollectionTask dataCollectionTask) {
    hPersistence.save(dataCollectionTask);
  }

  public Optional<DataCollectionTask> getNextTask(String accountId, String dataCollectionWorkerId) {
    Query<DataCollectionTask> query = hPersistence.createQuery(DataCollectionTask.class)
                                          .filter(DataCollectionTaskKeys.accountId, accountId)
                                          .filter(DataCollectionTaskKeys.dataCollectionWorkerId, dataCollectionWorkerId)
                                          .field(DataCollectionTaskKeys.validAfter)
                                          .lessThanOrEq(clock.instant())
                                          .order(Sort.ascending(VerificationTaskBaseKeys.lastUpdatedAt));
    query.or(query.criteria(DataCollectionTaskKeys.status).equal(DataCollectionExecutionStatus.QUEUED),
        query.and(query.criteria(DataCollectionTaskKeys.status).equal(DataCollectionExecutionStatus.RUNNING),
            query.criteria(VerificationTaskBaseKeys.lastUpdatedAt)
                .lessThan(clock.millis() - TimeUnit.MINUTES.toMillis(5))));
    query.or(query.criteria(DataCollectionTaskKeys.type).equal(SERVICE_GUARD),
        query.and(query.criteria(DataCollectionTaskKeys.type).equal(SLI),
            query.criteria(DataCollectionTaskKeys.retryCount).lessThanOrEq(SLIDataCollectionTask.MAX_RETRY_COUNT)),
        query.and(query.criteria(DataCollectionTaskKeys.type).equal(DEPLOYMENT),
            query.criteria(DataCollectionTaskKeys.retryCount)
                .lessThanOrEq(DeploymentDataCollectionTask.MAX_RETRY_COUNT)));
    UpdateOperations<DataCollectionTask> updateOperations =
        hPersistence.createUpdateOperations(DataCollectionTask.class)
            .set(DataCollectionTaskKeys.status, DataCollectionExecutionStatus.RUNNING)
            .inc(DataCollectionTaskKeys.retryCount)
            .set(DataCollectionTaskKeys.lastPickedAt, clock.instant())
            .set(VerificationTaskBaseKeys.lastUpdatedAt, clock.millis());

    DataCollectionTask task = hPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());
    return Optional.ofNullable(task);
  }

  @Override
  public Optional<DataCollectionTaskDTO> getNextTaskDTO(String accountId, String dataCollectionWorkerId) {
    Optional<DataCollectionTask> optionalTask = getNextTask(accountId, dataCollectionWorkerId);
    if (optionalTask.isPresent()) {
      DataCollectionTask task = optionalTask.get();
      return Optional.of(DataCollectionTaskDTO.builder()
                             .uuid(task.getUuid())
                             .accountId(task.getAccountId())
                             .verificationTaskId(task.getVerificationTaskId())
                             .dataCollectionMetadata(task.getDataCollectionMetadata())
                             .dataCollectionInfo(task.getDataCollectionInfo())
                             .startTime(task.getStartTime())
                             .endTime(task.getEndTime())
                             .build());
    } else {
      return Optional.empty();
    }
  }

  @Override
  public List<DataCollectionTaskDTO> getNextTaskDTOs(String accountId, String dataCollectionWorkerId) {
    List<DataCollectionTaskDTO> dataCollectionTasks = new ArrayList<>();
    Optional<DataCollectionTaskDTO> nextTaskDTO;
    do {
      nextTaskDTO = getNextTaskDTO(accountId, dataCollectionWorkerId);
      nextTaskDTO.ifPresent(dataCollectionTasks::add);
    } while (nextTaskDTO.isPresent() && dataCollectionTasks.size() < CVNG_MAX_PARALLEL_THREADS);
    return dataCollectionTasks;
  }

  @Override
  public DataCollectionTask getDataCollectionTask(String dataCollectionTaskId) {
    return hPersistence.get(DataCollectionTask.class, dataCollectionTaskId);
  }

  @Override
  public DataCollectionTask getFirstDataCollectionTaskWithStatusAfterStartTime(
      String verificationTaskId, DataCollectionExecutionStatus status, Instant startTime) {
    return hPersistence.createQuery(DataCollectionTask.class)
        .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
        .filter(DataCollectionTaskKeys.status, status)
        .field(DataCollectionTaskKeys.startTime)
        .greaterThanOrEq(startTime)
        .get();
  }

  @Override
  public void updateTaskStatus(DataCollectionTaskResult result) {
    updateTaskStatus(result, true);
  }

  @Override
  public DataCollectionTask updateRetry(ProjectParams projectParams, String identifier) {
    UpdateOperations<DataCollectionTask> updateOperations =
        hPersistence.createUpdateOperations(DataCollectionTask.class);
    Instant instant = clock.instant().plusSeconds(300);
    updateOperations.set(DataCollectionTaskKeys.validAfter, instant).set(DataCollectionTaskKeys.retryCount, 0);

    DataCollectionTask dataCollectionTask = getDataCollectionTask(identifier);

    hPersistence.update(dataCollectionTask, updateOperations);
    return dataCollectionTask;
  }

  private void updateTaskStatus(DataCollectionTaskResult result, boolean updateIfRunning) {
    log.info("Updating status {}", result);
    UpdateOperations<DataCollectionTask> updateOperations =
        hPersistence.createUpdateOperations(DataCollectionTask.class)
            .set(DataCollectionTaskKeys.status, result.getStatus());
    if (result.getStacktrace() != null) {
      updateOperations.set(DataCollectionTaskKeys.stacktrace, result.getStacktrace());
    }
    if (result.getException() != null) {
      updateOperations.set(DataCollectionTaskKeys.exception, result.getException());
    }
    Query<DataCollectionTask> query = hPersistence.createQuery(DataCollectionTask.class)
                                          .filter(DataCollectionTaskKeys.uuid, result.getDataCollectionTaskId());
    if (updateIfRunning) {
      query = query.filter(DataCollectionTaskKeys.status, DataCollectionExecutionStatus.RUNNING);
    }

    UpdateResults updateResults = hPersistence.update(query, updateOperations);
    if (updateResults.getUpdatedCount() == 0) {
      // https://harness.atlassian.net/browse/CVNG-1601
      log.info("Task is not in running state. Skipping the update {}", result);
      return;
    }
    DataCollectionTask dataCollectionTask = getDataCollectionTask(result.getDataCollectionTaskId());
    ExecutionLogger executionLogger = executionLogService.getLogger(dataCollectionTask);
    List<CVNGLogTag> cvngLogTags = CVNGTaskMetadataUtils.getCvngLogTagsForTask(dataCollectionTask.getUuid());
    if (!DataCollectionExecutionStatus.getNonFinalStatuses().contains(dataCollectionTask.getStatus())) {
      if (dataCollectionTask.getRetryCount() > 0) {
        cvngLogTags.add(CVNGTaskMetadataUtils.getCvngLogTag(
            CVNGTaskMetadataConstants.RETRY_COUNT, String.valueOf(dataCollectionTask.getRetryCount())));
      }
      if (dataCollectionTask.getLastPickedAt() != null) {
        cvngLogTags.addAll(CVNGTaskMetadataUtils.getTaskDurationTags(
            CVNGTaskMetadataUtils.DurationType.WAIT_DURATION, dataCollectionTask.waitTime()));
        cvngLogTags.addAll(CVNGTaskMetadataUtils.getTaskDurationTags(
            CVNGTaskMetadataUtils.DurationType.RUNNING_DURATION, dataCollectionTask.runningTime(Instant.now())));
      } else {
        cvngLogTags.addAll(CVNGTaskMetadataUtils.getTaskDurationTags(
            CVNGTaskMetadataUtils.DurationType.TOTAL_DURATION, dataCollectionTask.totalTime(Instant.now())));
      }
    }
    cvngLogTags.addAll(CVNGTaskMetadataUtils.getDataCollectionMetadataTags(result));
    String message = "Data collection task status: " + dataCollectionTask.getStatus();
    executionLogger.log(dataCollectionTask.getLogLevel(), cvngLogTags, message);
    if (result.getException() != null) {
      executionLogger.log(dataCollectionTask.getLogLevel(), cvngLogTags,
          "Data collection task failed with exception: ", result.getException());
    }
    for (ExecutionLog executionLog : result.getExecutionLogs()) {
      executionLogger.log(executionLog.getLogLevel(), executionLog.getLog());
    }
    if (result.getStatus() == DataCollectionExecutionStatus.SUCCESS) {
      dataCollectionTaskManagementServiceMapBinder.get(dataCollectionTask.getType())
          .processDataCollectionSuccess(dataCollectionTask);
      if (dataCollectionTask.shouldCreateNextTask()) {
        dataCollectionTaskManagementServiceMapBinder.get(dataCollectionTask.getType())
            .createNextTask(dataCollectionTask);
      } else {
        enqueueNextTask(dataCollectionTask);
      }
      if (dataCollectionTask.shouldQueueAnalysis()) {
        orchestrationService.queueAnalysis(AnalysisInput.builder()
                                               .verificationTaskId(dataCollectionTask.getVerificationTaskId())
                                               .startTime(dataCollectionTask.getStartTime())
                                               .endTime(dataCollectionTask.getEndTime())
                                               .build());
      }
    } else {
      retry(dataCollectionTask);
    }
  }
  @Override
  public DataCollectionTask getLastDataCollectionTask(String accountId, String verificationTaskId) {
    return hPersistence.createQuery(DataCollectionTask.class)
        .filter(DataCollectionTaskKeys.accountId, accountId)
        .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
        .order(Sort.descending(DataCollectionTaskKeys.startTime))
        .get();
  }

  @Override
  public List<DataCollectionTask> getLatestDataCollectionTasks(String accountId, String verificationTaskId, int count) {
    return hPersistence.createQuery(DataCollectionTask.class)
        .filter(DataCollectionTaskKeys.accountId, accountId)
        .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
        .order(Sort.descending(DataCollectionTaskKeys.startTime))
        .asList(new FindOptions().limit(count));
  }

  @Override
  public List<DataCollectionTask> getAllDataCollectionTasks(String accountId, String verificationTaskId) {
    return hPersistence.createQuery(DataCollectionTask.class)
        .filter(DataCollectionTaskKeys.accountId, accountId)
        .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
        .order(Sort.descending(DataCollectionTaskKeys.startTime))
        .asList();
  }

  @Override
  public List<DataCollectionTask> getAllNonFinalDataCollectionTasks(String accountId, String verificationTaskId) {
    return hPersistence.createQuery(DataCollectionTask.class)
        .filter(DataCollectionTaskKeys.accountId, accountId)
        .filter(DataCollectionTaskKeys.verificationTaskId, verificationTaskId)
        .field(DataCollectionTaskKeys.status)
        .in(DataCollectionExecutionStatus.getNonFinalStatuses())
        .order(Sort.descending(DataCollectionTaskKeys.startTime))
        .asList();
  }

  private void markDependentTasksFailed(DataCollectionTask task) {
    dataCollectionTaskManagementServiceMapBinder.get(task.getType()).processDataCollectionFailure(task);
    String exceptionMsg =
        task.getStatus() == DataCollectionExecutionStatus.EXPIRED ? "Previous task timed out" : "Previous task failed";
    log.info("Marking queued task failed for verificationTaskId {}", task.getVerificationTaskId());
    UpdateOperations<DataCollectionTask> updateOperations =
        hPersistence.createUpdateOperations(DataCollectionTask.class)
            .set(DataCollectionTaskKeys.status, DataCollectionExecutionStatus.FAILED)
            .set(DataCollectionTaskKeys.exception, exceptionMsg);
    Query<DataCollectionTask> query =
        hPersistence.createQuery(DataCollectionTask.class)
            .filter(DataCollectionTaskKeys.verificationTaskId, task.getVerificationTaskId());
    query.or(query.criteria(DataCollectionTaskKeys.status).equal(DataCollectionExecutionStatus.QUEUED),
        query.criteria(DataCollectionTaskKeys.status).equal(DataCollectionExecutionStatus.WAITING));
    hPersistence.update(query, updateOperations);
  }

  private void enqueueNextTask(DataCollectionTask task) {
    if (task.getNextTaskId() != null) {
      log.info("Enqueuing next task {}", task.getUuid());
      UpdateOperations<DataCollectionTask> updateOperations =
          hPersistence.createUpdateOperations(DataCollectionTask.class)
              .set(DataCollectionTaskKeys.status, DataCollectionExecutionStatus.QUEUED);
      hPersistence.update(hPersistence.createQuery(DataCollectionTask.class)
                              .filter(DataCollectionTaskKeys.uuid, task.getNextTaskId())
                              .filter(DataCollectionTaskKeys.status, DataCollectionExecutionStatus.WAITING),
          updateOperations);
      List<CVNGLogTag> cvngLogTags = CVNGTaskMetadataUtils.getCvngLogTagsForTask(task.getUuid());
      executionLogService.getLogger(task).log(
          task.getLogLevel(), cvngLogTags, "Data collection task status: " + DataCollectionExecutionStatus.QUEUED);
    }
  }

  private void retry(DataCollectionTask dataCollectionTask) {
    log.info("Retrying DataCollection task with id: {} and worker id: {}", dataCollectionTask.getUuid(),
        dataCollectionTask.getDataCollectionWorkerId());
    if (dataCollectionTask.eligibleForRetry(clock.instant())) {
      UpdateOperations<DataCollectionTask> updateOperations =
          hPersistence.createUpdateOperations(DataCollectionTask.class)
              .set(DataCollectionTaskKeys.status, DataCollectionExecutionStatus.QUEUED)
              .set(DataCollectionTaskKeys.validAfter, dataCollectionTask.getNextValidAfter(clock.instant()))
              .inc(DataCollectionTaskKeys.retryCount);
      Query<DataCollectionTask> query = hPersistence.createQuery(DataCollectionTask.class)
                                            .filter(DataCollectionTaskKeys.uuid, dataCollectionTask.getUuid());
      hPersistence.update(query, updateOperations);
      List<CVNGLogTag> cvngLogTags = CVNGTaskMetadataUtils.getCvngLogTagsForTask(dataCollectionTask.getUuid());
      executionLogService.getLogger(dataCollectionTask)
          .log(dataCollectionTask.getLogLevel(), cvngLogTags,
              "Data collection task status: " + DataCollectionExecutionStatus.QUEUED);
    } else {
      markDependentTasksFailed(dataCollectionTask);
      if (dataCollectionTask.shouldCreateNextTask()) {
        dataCollectionTaskManagementServiceMapBinder.get(dataCollectionTask.getType())
            .createNextTask(dataCollectionTask);
      }
      // TODO: handle this logic in a better way and setup alert.
      log.error("Task is in the past. Enqueuing next task with new data collection startTime. {}, {}, {}",
          dataCollectionTask.getUuid(), dataCollectionTask.getException(), dataCollectionTask.getStacktrace());
    }
  }

  @Override
  public void validateIfAlreadyExists(DataCollectionTask dataCollectionTask) {
    if (hPersistence.createQuery(DataCollectionTask.class)
            .filter(DataCollectionTaskKeys.accountId, dataCollectionTask.getAccountId())
            .filter(DataCollectionTaskKeys.verificationTaskId, dataCollectionTask.getVerificationTaskId())
            .filter(DataCollectionTaskKeys.startTime, dataCollectionTask.getStartTime())
            .get()
        != null) {
      log.error(
          "DataCollectionTask with same startTime already exist. This shouldn't be happening. Please check delegate logs. verificationTaskId={}, startTime={}",
          dataCollectionTask.getVerificationTaskId(), dataCollectionTask.getStartTime());
      throw new IllegalStateException(
          "DataCollectionTask with same startTime already exist. This shouldn't be happening. Please check delegate logs");
    }
  }

  @Override
  public void updatePerpetualTaskStatus(DataCollectionTask dataCollectionTask) {
    Optional<CVNGPerpetualTaskDTO> cvngPerpetualTaskDTO;
    try {
      cvngPerpetualTaskDTO =
          monitoringSourcePerpetualTaskService.getPerpetualTaskStatus(dataCollectionTask.getDataCollectionWorkerId());
    } catch (Exception exception) {
      DataCollectionTaskResult dataCollectionTaskResult =
          DataCollectionTaskResult.builder()
              .dataCollectionTaskId(dataCollectionTask.getUuid())
              .status(DataCollectionExecutionStatus.FAILED)
              .exception("Exception while getting MontioringSourcePerpetualTask status with workerId:"
                  + dataCollectionTask.getDataCollectionWorkerId() + ". " + exception.getMessage())
              .build();
      updateTaskStatus(dataCollectionTaskResult, false);
      log.error("Exception while getting MontioringSourcePerpetualTask status with workerId:"
              + dataCollectionTask.getDataCollectionWorkerId(),
          exception);
      return;
    }
    if (cvngPerpetualTaskDTO.isPresent()) {
      if (cvngPerpetualTaskDTO.get().getCvngPerpetualTaskUnassignedReason() != null) {
        DataCollectionTaskResult dataCollectionTaskResult =
            DataCollectionTaskResult.builder()
                .dataCollectionTaskId(dataCollectionTask.getUuid())
                .status(DataCollectionExecutionStatus.FAILED)
                .exception(
                    "Perpetual task unassigned:" + cvngPerpetualTaskDTO.get().getCvngPerpetualTaskUnassignedReason())
                .build();
        updateTaskStatus(dataCollectionTaskResult, false);
      } else if (cvngPerpetualTaskDTO.get().getCvngPerpetualTaskState() != null
          && !cvngPerpetualTaskDTO.get().getCvngPerpetualTaskState().equals(CVNGPerpetualTaskState.TASK_ASSIGNED)) {
        DataCollectionTaskResult dataCollectionTaskResult =
            DataCollectionTaskResult.builder()
                .dataCollectionTaskId(dataCollectionTask.getUuid())
                .status(DataCollectionExecutionStatus.FAILED)
                .exception("Perpetual task assigned but not in a valid state:"
                    + cvngPerpetualTaskDTO.get().getCvngPerpetualTaskState()
                    + " and is assigned to delegate:" + cvngPerpetualTaskDTO.get().getDelegateId())
                .build();
        updateTaskStatus(dataCollectionTaskResult, false);
      }
    }
  }

  @Override
  public void populateMetricPack(CVConfig cvConfig) {
    if (cvConfig instanceof MetricCVConfig) {
      // TODO: get rid of this. Adding it to unblock. We need to redesign how are we setting DSL.
      metricPackService.populateDataCollectionDsl(cvConfig.getType(), ((MetricCVConfig) cvConfig).getMetricPack());
      metricPackService.populatePaths(cvConfig.getAccountId(), cvConfig.getOrgIdentifier(),
          cvConfig.getProjectIdentifier(), cvConfig.getType(), ((MetricCVConfig) cvConfig).getMetricPack());
    }
  }

  @Override
  public List<String> createSeqTasks(List<DataCollectionTask> dataCollectionTasks) {
    DataCollectionTask lastTask = null;
    for (DataCollectionTask task : dataCollectionTasks) {
      task.setStatus(DataCollectionExecutionStatus.WAITING);
      task.setUuid(generateUuid());
      if (lastTask != null) {
        lastTask.setNextTaskId(task.getUuid());
      }
      lastTask = task;
    }
    if (dataCollectionTasks.size() > 0) {
      dataCollectionTasks.get(0).setStatus(DataCollectionExecutionStatus.QUEUED);
    }
    return hPersistence.saveBatch(dataCollectionTasks);
  }

  @Override
  public void abortDeploymentDataCollectionTasks(List<String> verificationTaskIds) {
    Query<DataCollectionTask> query =
        hPersistence.createQuery(DataCollectionTask.class)
            .filter(DataCollectionTaskKeys.type, DataCollectionTask.Type.DEPLOYMENT)
            .field(DataCollectionTaskKeys.verificationTaskId)
            .in(verificationTaskIds)
            .field(DataCollectionTaskKeys.status)
            .in(Lists.newArrayList(DataCollectionExecutionStatus.WAITING, DataCollectionExecutionStatus.QUEUED));
    UpdateOperations<DataCollectionTask> abortDCTaskOperation =
        hPersistence.createUpdateOperations(DataCollectionTask.class)
            .set(DataCollectionTaskKeys.status, DataCollectionExecutionStatus.ABORTED);
    hPersistence.update(query, abortDCTaskOperation);
  }
}
