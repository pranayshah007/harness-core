package io.harness.cvng.core.services.impl;

import static io.harness.cvng.core.entities.DataCollectionTask.Type.SERVICE_GUARD;
import static io.harness.cvng.core.services.CVNextGenConstants.CVNG_MAX_PARALLEL_THREADS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.cvng.beans.DataCollectionExecutionStatus;
import io.harness.cvng.beans.DataCollectionTaskDTO;
import io.harness.cvng.beans.DataCollectionTaskDTO.DataCollectionTaskResult;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.DataCollectionTask.DataCollectionTaskKeys;
import io.harness.cvng.core.entities.DeploymentDataCollectionTask;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.services.api.DataCollectionTaskManagementService;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.metrics.CVNGMetricsUtils;
import io.harness.cvng.metrics.services.impl.MetricContextBuilder;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance.DataCollectionProgressLog;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.metrics.AutoMetricContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import io.fabric8.utils.Lists;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;

@Slf4j
public class DataCollectionTaskServiceImpl implements DataCollectionTaskService {
  @Inject private HPersistence hPersistence;
  @Inject private Clock clock;
  @Inject private MetricPackService metricPackService;
  @Inject private OrchestrationService orchestrationService;
  @Inject private MetricService metricService;
  @Inject private MetricContextBuilder metricContextBuilder;
  @Inject
  private Map<DataCollectionTask.Type, DataCollectionTaskManagementService>
      dataCollectionTaskManagementServiceMapBinder;

  // TODO: this is creating reverse dependency. Find a way to get rid of this dependency.
  // Probabally by moving ProgressLog concept to a separate service and model.
  @Inject private VerificationJobInstanceService verificationJobInstanceService;

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
                                          .order(Sort.ascending(DataCollectionTaskKeys.lastUpdatedAt));
    query.or(query.criteria(DataCollectionTaskKeys.status).equal(DataCollectionExecutionStatus.QUEUED),
        query.and(query.criteria(DataCollectionTaskKeys.status).equal(DataCollectionExecutionStatus.RUNNING),
            query.criteria(DataCollectionTaskKeys.lastUpdatedAt)
                .lessThan(clock.millis() - TimeUnit.MINUTES.toMillis(5))));
    query.or(query.criteria(DataCollectionTaskKeys.type).equal(SERVICE_GUARD),
        query.criteria(DataCollectionTaskKeys.retryCount).lessThanOrEq(DeploymentDataCollectionTask.MAX_RETRY_COUNT));
    UpdateOperations<DataCollectionTask> updateOperations =
        hPersistence.createUpdateOperations(DataCollectionTask.class)
            .set(DataCollectionTaskKeys.status, DataCollectionExecutionStatus.RUNNING)
            .inc(DataCollectionTaskKeys.retryCount)
            .set(DataCollectionTaskKeys.lastPickedAt, clock.instant())
            .set(DataCollectionTaskKeys.lastUpdatedAt, clock.millis());

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
    log.info("Updating status {}", result);
    UpdateOperations<DataCollectionTask> updateOperations =
        hPersistence.createUpdateOperations(DataCollectionTask.class)
            .set(DataCollectionTaskKeys.status, result.getStatus());
    if (result.getStacktrace() != null) {
      updateOperations.set(DataCollectionTaskKeys.exception, result.getException());
      updateOperations.set(DataCollectionTaskKeys.stacktrace, result.getStacktrace());
    }
    Query<DataCollectionTask> query = hPersistence.createQuery(DataCollectionTask.class)
                                          .filter(DataCollectionTaskKeys.uuid, result.getDataCollectionTaskId())
                                          .filter(DataCollectionTaskKeys.status, DataCollectionExecutionStatus.RUNNING);
    UpdateResults updateResults = hPersistence.update(query, updateOperations);
    if (updateResults.getUpdatedCount() == 0) {
      // https://harness.atlassian.net/browse/CVNG-1601
      log.info("Task is not in running state. Skipping the update {}", result);
      return;
    }

    DataCollectionTask dataCollectionTask = getDataCollectionTask(result.getDataCollectionTaskId());
    recordMetricsOnUpdateStatus(dataCollectionTask);
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

  private void recordMetricsOnUpdateStatus(DataCollectionTask dataCollectionTask) {
    try (AutoMetricContext ignore = metricContextBuilder.getContext(dataCollectionTask, DataCollectionTask.class)) {
      metricService.incCounter(CVNGMetricsUtils.getDataCollectionTaskStatusMetricName(dataCollectionTask.getStatus()));
      metricService.recordDuration(
          CVNGMetricsUtils.DATA_COLLECTION_TASK_TOTAL_TIME, dataCollectionTask.totalTime(clock.instant()));
      metricService.recordDuration(CVNGMetricsUtils.DATA_COLLECTION_TASK_WAIT_TIME, dataCollectionTask.waitTime());
      metricService.recordDuration(
          CVNGMetricsUtils.DATA_COLLECTION_TASK_RUNNING_TIME, dataCollectionTask.runningTime(clock.instant()));
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

  private void markDependentTasksFailed(DataCollectionTask task) {
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
    return hPersistence.save(dataCollectionTasks);
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
