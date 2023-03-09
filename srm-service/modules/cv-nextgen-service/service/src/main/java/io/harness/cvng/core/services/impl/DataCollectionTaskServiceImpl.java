/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.cvng.core.entities.DataCollectionTask.Type.SERVICE_GUARD;
import static io.harness.cvng.core.services.CVNextGenConstants.CVNG_MAX_PARALLEL_THREADS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.cvng.analysis.entities.VerificationTaskBase.VerificationTaskBaseKeys;
import io.harness.cvng.beans.CVNGPerpetualTaskDTO;
import io.harness.cvng.beans.CVNGPerpetualTaskState;
import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.DataCollectionTaskDTO;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult.ExecutionLog;
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
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.metrics.services.impl.MetricContextBuilder;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.DataCollectionProgressLog;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.metrics.service.api.MetricService;
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
  @Inject private MetricService metricService;
  @Inject private MetricContextBuilder metricContextBuilder;
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Inject
  private Map<DataCollectionTask.Type, DataCollectionTaskManagementService>
      dataCollectionTaskManagementServiceMapBinder;
  @Inject private ExecutionLogService executionLogService;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;

  // TODO: this is creating reverse dependency. Find a way to get rid of this dependency.
  // Probabally by moving ProgressLog concept to a separate service and model.
  @Inject private VerificationJobInstanceService verificationJobInstanceService;

  @Inject private VerificationTaskService verificationTaskService;

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
        query.criteria(DataCollectionTaskKeys.retryCount).lessThanOrEq(DeploymentDataCollectionTask.MAX_RETRY_COUNT));
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
      if (nextTaskDTO.isPresent()) {
        dataCollectionTasks.add(nextTaskDTO.get());
      }
    } while (nextTaskDTO.isPresent() && dataCollectionTasks.size() < CVNG_MAX_PARALLEL_THREADS);
    return dataCollectionTasks;
  }

  @Override
  public DataCollectionTask getDataCollectionTask(String dataCollectionTaskId) {
    return hPersistence.get(DataCollectionTask.class, dataCollectionTaskId);
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
    executionLogger.log(
        dataCollectionTask.getLogLevel(), "Data collection task status: " + dataCollectionTask.getStatus());
    if (result.getException() != null) {
      executionLogger.log(
          dataCollectionTask.getLogLevel(), "Data collection task failed with exception: ", result.getException());
    }
    for (ExecutionLog executionLog : result.getExecutionLogs()) {
      executionLogger.log(executionLog.getLogLevel(), executionLog.getLog());
    }
    if (result.getStatus() == DataCollectionExecutionStatus.SUCCESS) {
      // TODO: make this an atomic operation
      if (dataCollectionTask.shouldCreateNextTask()) {
        dataCollectionTaskManagementServiceMapBinder.get(dataCollectionTask.getType())
            .createNextTask(dataCollectionTask);
      } else {
        enqueueNextTask(dataCollectionTask);
        if (dataCollectionTask instanceof DeploymentDataCollectionTask) {
          verificationJobInstanceService.logProgress(DataCollectionProgressLog.builder()
                                                         .executionStatus(dataCollectionTask.getStatus())
                                                         .isFinalState(false)
                                                         .startTime(dataCollectionTask.getStartTime())
                                                         .endTime(dataCollectionTask.getEndTime())
                                                         .verificationTaskId(dataCollectionTask.getVerificationTaskId())
                                                         .log("Data collection task successful")
                                                         .build());
        }
      }
      if (dataCollectionTask.shouldQueueAnalysis()) {
        orchestrationService.queueAnalysis(dataCollectionTask.getVerificationTaskId(),
            dataCollectionTask.getStartTime(), dataCollectionTask.getEndTime());
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
    if (task instanceof SLIDataCollectionTask) {
      ServiceLevelIndicator serviceLevelIndicator =
          serviceLevelIndicatorService.get(verificationTaskService.getSliId(task.getVerificationTaskId()));
      serviceLevelIndicatorService.enqueueDataCollectionFailureInstanceAndTriggerAnalysis(
          task.getVerificationTaskId(), task.getStartTime(), task.getEndTime(), serviceLevelIndicator);
    }
    if (task instanceof DeploymentDataCollectionTask) {
      verificationJobInstanceService.logProgress(
          DataCollectionProgressLog.builder()
              .executionStatus(task.getStatus())
              .isFinalState(false)
              .startTime(task.getStartTime())
              .endTime(task.getEndTime())
              .verificationTaskId(task.getVerificationTaskId())
              .log("Data collection failed with exception: " + task.getException())
              .build());
    }
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
      executionLogService.getLogger(task).log(
          task.getLogLevel(), "Data collection task status: " + DataCollectionExecutionStatus.QUEUED);
    }
  }

  private void retry(DataCollectionTask dataCollectionTask) {
    if (dataCollectionTask.eligibleForRetry(clock.instant())) {
      UpdateOperations<DataCollectionTask> updateOperations =
          hPersistence.createUpdateOperations(DataCollectionTask.class)
              .set(DataCollectionTaskKeys.status, DataCollectionExecutionStatus.QUEUED)
              .set(DataCollectionTaskKeys.validAfter, dataCollectionTask.getNextValidAfter(clock.instant()))
              .inc(DataCollectionTaskKeys.retryCount);
      Query<DataCollectionTask> query = hPersistence.createQuery(DataCollectionTask.class)
                                            .filter(DataCollectionTaskKeys.uuid, dataCollectionTask.getUuid());
      hPersistence.update(query, updateOperations);
      executionLogService.getLogger(dataCollectionTask)
          .log(
              dataCollectionTask.getLogLevel(), "Data collection task status: " + DataCollectionExecutionStatus.QUEUED);
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
