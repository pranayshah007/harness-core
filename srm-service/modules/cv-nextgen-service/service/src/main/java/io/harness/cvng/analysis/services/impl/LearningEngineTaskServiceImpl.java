/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.CVConstants.SERVICE_BASE_URL;
import static io.harness.cvng.analysis.CVAnalysisConstants.LEARNING_RESOURCE;
import static io.harness.cvng.analysis.CVAnalysisConstants.MARK_FAILURE_PATH;
import static io.harness.cvng.analysis.entities.LearningEngineTask.TaskPriority.P0;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.cvng.analysis.beans.ExceptionInfo;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskKeys;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.VerificationTaskBase.VerificationTaskBaseKeys;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.beans.cvnglog.CVNGLogTag;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.entities.VerificationTask.TaskType;
import io.harness.cvng.core.jobs.StateMachineEventPublisherService;
import io.harness.cvng.core.services.api.ExecutionLogService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.utils.CVNGTaskMetadataUtils;
import io.harness.cvng.metrics.CVNGMetricsUtils;
import io.harness.cvng.metrics.services.impl.MetricContextBuilder;
import io.harness.metrics.AutoMetricContext;
import io.harness.metrics.beans.AccountMetricContext;
import io.harness.metrics.service.api.MetricService;
import io.harness.persistence.HPersistence;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;

@Slf4j
public class LearningEngineTaskServiceImpl implements LearningEngineTaskService {
  @Inject private HPersistence hPersistence;
  @Inject private MetricService metricService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private Clock clock;
  @Inject private MetricContextBuilder metricContextBuilder;
  @Inject private StateMachineEventPublisherService stateMachineEventPublisherService;
  @Inject private ExecutionLogService executionLogService;

  @Override
  public LearningEngineTask getNextAnalysisTask() {
    return getNextAnalysisTask(null);
  }

  @Override
  public LearningEngineTask getNextAnalysisTask(List<LearningEngineTaskType> taskType) {
    Query<LearningEngineTask> learningEngineTaskQuery =
        hPersistence.createQuery(LearningEngineTask.class)
            .filter(LearningEngineTaskKeys.taskStatus, ExecutionStatus.QUEUED)
            .order(Sort.ascending(LearningEngineTaskKeys.taskPriority),
                Sort.ascending(VerificationTaskBaseKeys.createdAt));
    if (isNotEmpty(taskType)) {
      learningEngineTaskQuery.field(LearningEngineTaskKeys.analysisType).in(taskType);
    }

    UpdateOperations<LearningEngineTask> updateOperations =
        hPersistence.createUpdateOperations(LearningEngineTask.class);
    updateOperations.set(LearningEngineTaskKeys.taskStatus, ExecutionStatus.RUNNING)
        .set(LearningEngineTaskKeys.pickedAt, clock.instant());
    LearningEngineTask learningEngineTask =
        hPersistence.findAndModify(learningEngineTaskQuery, updateOperations, new FindAndModifyOptions());
    if (learningEngineTask != null) {
      List<CVNGLogTag> cvngLogTags = CVNGTaskMetadataUtils.getCvngLogTagsForTask(learningEngineTask.getUuid());
      executionLogService.getLogger(learningEngineTask)
          .log(learningEngineTask.getLogLevel(), cvngLogTags,
              "Learning engine task status: " + learningEngineTask.getTaskStatus());
    }
    return learningEngineTask;
  }

  @Override
  public List<String> createLearningEngineTasks(List<LearningEngineTask> tasks) {
    Preconditions.checkNotNull(tasks, "tasks can not be null");
    Preconditions.checkArgument(tasks.size() > 0, "List size can not be zero");
    return tasks.stream().map(task -> createLearningEngineTask(task)).collect(Collectors.toList());
  }

  @Override
  public String createLearningEngineTask(LearningEngineTask learningEngineTask) {
    learningEngineTask.setTaskStatus(ExecutionStatus.QUEUED);
    VerificationTask verificationTask = verificationTaskService.get(learningEngineTask.getVerificationTaskId());
    learningEngineTask.setAccountId(verificationTask.getAccountId());
    if (verificationTask.getTaskInfo().getTaskType() == TaskType.DEPLOYMENT) {
      learningEngineTask.setTaskPriority(P0.getValue());
    }
    List<CVNGLogTag> cvngLogTags = CVNGTaskMetadataUtils.getCvngLogTagsForTask(learningEngineTask.getUuid());
    executionLogService.getLogger(learningEngineTask)
        .log(learningEngineTask.getLogLevel(), cvngLogTags,
            "Learning engine task status: " + learningEngineTask.getTaskStatus());
    return hPersistence.save(learningEngineTask);
  }

  @Override
  public Map<String, ExecutionStatus> getTaskStatus(Set<String> taskIds) {
    if (isNotEmpty(taskIds)) {
      Map<String, ExecutionStatus> taskStatuses = new HashMap<>();
      List<LearningEngineTask> tasks = hPersistence.createQuery(LearningEngineTask.class, excludeAuthority)
                                           .field(LearningEngineTaskKeys.uuid)
                                           .in(taskIds)
                                           .asList();

      List<String> timedOutTaskIds = new ArrayList<>();
      if (tasks != null) {
        tasks.forEach(task -> {
          if (hasTaskTimedOut(task)) {
            // TODO: this should be done using iterator.
            log.info("LearningEngineTask {} for verificationTaskId {} has TIMEDOUT", task.getUuid(),
                task.getVerificationTaskId());
            task.setTaskStatus(ExecutionStatus.TIMEOUT);
            incTaskStatusMetric(task.getAccountId(), ExecutionStatus.TIMEOUT);
            addTimeToFinishMetrics(task);
            List<CVNGLogTag> cvngLogTags = CVNGTaskMetadataUtils.getCvngLogTagsForTask(task.getUuid());
            executionLogService.getLogger(task).log(
                task.getLogLevel(), cvngLogTags, "Learning engine task status: " + task.getTaskStatus());
            timedOutTaskIds.add(task.getUuid());
          }
          taskStatuses.put(task.getUuid(), task.getTaskStatus());
        });
      }
      if (isNotEmpty(timedOutTaskIds)) {
        UpdateOperations updateOperations = hPersistence.createUpdateOperations(LearningEngineTask.class)
                                                .set(LearningEngineTaskKeys.taskStatus, ExecutionStatus.TIMEOUT);
        Query<LearningEngineTask> timeoutQuery =
            hPersistence.createQuery(LearningEngineTask.class).field(LearningEngineTaskKeys.uuid).in(timedOutTaskIds);

        hPersistence.update(timeoutQuery, updateOperations);
      }
      return taskStatuses;
    }
    return null;
  }

  private void incTaskStatusMetric(String accountId, ExecutionStatus status) {
    try (AccountMetricContext accountMetricContext = new AccountMetricContext(accountId)) {
      metricService.incCounter(CVNGMetricsUtils.getLearningEngineTaskStatusMetricName(status));
    }
  }

  private void addTimeToFinishMetrics(LearningEngineTask task) {
    try (AutoMetricContext ignore = metricContextBuilder.getContext(task, LearningEngineTask.class)) {
      metricService.recordDuration(CVNGMetricsUtils.LEARNING_ENGINE_TASK_TOTAL_TIME, task.totalTime(clock.instant()));
      metricService.recordDuration(CVNGMetricsUtils.LEARNING_ENGINE_TASK_WAIT_TIME, task.waitTime());
      metricService.recordDuration(
          CVNGMetricsUtils.LEARNING_ENGINE_TASK_RUNNING_TIME, task.runningTime(clock.instant()));
    }
  }

  @Override
  public String createFailureUrl(String taskId) {
    URIBuilder uriBuilder = new URIBuilder();
    uriBuilder.setPath(SERVICE_BASE_URL + "/" + LEARNING_RESOURCE + "/" + MARK_FAILURE_PATH);
    uriBuilder.addParameter("taskId", taskId);
    try {
      return uriBuilder.build().toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public LearningEngineTask get(String learningEngineTaskId) {
    return hPersistence.get(LearningEngineTask.class, learningEngineTaskId);
  }

  @Override
  public void markCompleted(String taskId) {
    Preconditions.checkNotNull(taskId, "taskId can not be null.");
    UpdateOperations<LearningEngineTask> updateOperations =
        hPersistence.createUpdateOperations(LearningEngineTask.class);
    // TODO: add metrics to capture total time taken from running to success.
    updateOperations.set(LearningEngineTaskKeys.taskStatus, ExecutionStatus.SUCCESS);
    hPersistence.update(hPersistence.createQuery(LearningEngineTask.class).filter(LearningEngineTaskKeys.uuid, taskId),
        updateOperations);
    LearningEngineTask task = get(taskId);
    incTaskStatusMetric(task.getAccountId(), ExecutionStatus.SUCCESS);
    addTimeToFinishMetrics(task);
    stateMachineEventPublisherService.registerTaskComplete(task.getAccountId(), task.getVerificationTaskId());
    List<CVNGLogTag> cvngLogTags = getCvngLogTagsForFinalState(task);
    executionLogService.getLogger(task).log(
        task.getLogLevel(), cvngLogTags, "Learning engine task status: " + task.getTaskStatus());
  }

  @Override
  public void markFailure(String taskId, ExceptionInfo exceptionInfo) {
    Preconditions.checkNotNull(taskId, "taskId can not be null.");
    UpdateOperations<LearningEngineTask> updateOperations =
        hPersistence.createUpdateOperations(LearningEngineTask.class);
    updateOperations.set(LearningEngineTaskKeys.taskStatus, ExecutionStatus.FAILED);
    if (isNotEmpty(exceptionInfo.getException())) {
      updateOperations.set(LearningEngineTaskKeys.exception, exceptionInfo.getException());
    }
    if (isNotEmpty(exceptionInfo.getStackTrace())) {
      updateOperations.set(LearningEngineTaskKeys.stackTrace, exceptionInfo.getStackTrace());
    }
    hPersistence.update(hPersistence.createQuery(LearningEngineTask.class).filter(LearningEngineTaskKeys.uuid, taskId),
        updateOperations);
    LearningEngineTask learningEngineTask = get(taskId);
    incTaskStatusMetric(learningEngineTask.getAccountId(), ExecutionStatus.FAILED);
    addTimeToFinishMetrics(learningEngineTask);
    List<CVNGLogTag> cvngLogTags = getCvngLogTagsForFinalState(learningEngineTask);
    executionLogService.getLogger(learningEngineTask)
        .log(ExecutionLogDTO.LogLevel.ERROR, cvngLogTags,
            "Learning engine task failed. Exception: ", learningEngineTask.getException());
  }

  private static List<CVNGLogTag> getCvngLogTagsForFinalState(LearningEngineTask learningEngineTask) {
    List<CVNGLogTag> cvngLogTags = CVNGTaskMetadataUtils.getCvngLogTagsForTask(learningEngineTask.getUuid());
    if (learningEngineTask.getPickedAt() != null) {
      cvngLogTags.addAll(CVNGTaskMetadataUtils.getTaskDurationTags(
          CVNGTaskMetadataUtils.DurationType.WAIT_DURATION, learningEngineTask.waitTime()));
      cvngLogTags.addAll(CVNGTaskMetadataUtils.getTaskDurationTags(
          CVNGTaskMetadataUtils.DurationType.RUNNING_DURATION, learningEngineTask.runningTime(Instant.now())));
    } else {
      cvngLogTags.addAll(CVNGTaskMetadataUtils.getTaskDurationTags(
          CVNGTaskMetadataUtils.DurationType.TOTAL_DURATION, learningEngineTask.totalTime(Instant.now())));
    }
    return cvngLogTags;
  }

  private boolean hasTaskTimedOut(LearningEngineTask task) {
    if (task != null && task.getTaskStatus().equals(ExecutionStatus.RUNNING)) {
      Instant tenMinsAgo = clock.instant().minus(10, ChronoUnit.MINUTES);
      if (Instant.ofEpochMilli(task.getLastUpdatedAt()).isBefore(tenMinsAgo)) {
        return true;
      }
    }
    return false;
  }
}
