/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import static io.harness.steps.StepUtils.buildAbstractions;

import static software.wings.beans.TaskType.CONTAINER_EXECUTE_STEP;
import static software.wings.beans.TaskType.CONTAINER_LE_STATUS;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.callback.DelegateCallbackToken;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.k8s.CIK8ExecuteStepTaskParams;
import io.harness.delegate.task.HDelegateTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.stepstatus.StepStatusTaskParameters;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.steps.StepUtils;

import software.wings.beans.SerializationFormat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerDelegateTaskHelper {
  private final DelegateServiceGrpcClient delegateServiceGrpcClient;
  private final Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;

  @Inject
  public ContainerDelegateTaskHelper(DelegateServiceGrpcClient delegateServiceGrpcClient,
      Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier) {
    this.delegateServiceGrpcClient = delegateServiceGrpcClient;
    this.delegateCallbackTokenSupplier = delegateCallbackTokenSupplier;
  }

  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;

  public String queueParkedDelegateTask(Ambiance ambiance, long timeout, String accountId) {
    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .parked(true)
                                  .taskType(CONTAINER_LE_STATUS.name())
                                  .parameters(new Object[] {StepStatusTaskParameters.builder().build()})
                                  .timeout(timeout)
                                  .build();

    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
    HDelegateTask task = (HDelegateTask) StepUtils.prepareDelegateTaskInput(accountId, taskData, abstractions);

    return queueTask(abstractions, task, new ArrayList<>());
  }

  public String queueTask(Ambiance ambiance, TaskData taskData, String accountId) {
    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
    HDelegateTask task = (HDelegateTask) StepUtils.prepareDelegateTaskInput(accountId, taskData, abstractions);
    return queueTask(abstractions, task, new ArrayList<>());
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  public String queueTask(
      Map<String, String> setupAbstractions, HDelegateTask task, List<String> eligibleToExecuteDelegateIds) {
    final DelegateTaskRequest delegateTaskRequest =
        buildDelegateTask(setupAbstractions, task, eligibleToExecuteDelegateIds);
    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to submit delegate task attempt: {}"),
            format("Failed to submit delegate task  after retrying {} times"));

    return Failsafe.with(retryPolicy)
        .get(()
                 -> delegateServiceGrpcClient.submitAsyncTaskV2(
                     delegateTaskRequest, delegateCallbackTokenSupplier.get(), Duration.ZERO, true));
  }

  private DelegateTaskRequest buildDelegateTask(
      Map<String, String> setupAbstractions, HDelegateTask task, List<String> eligibleToExecuteDelegateIds) {
    String accountId = task.getAccountId();
    TaskData taskData = task.getData();
    return DelegateTaskRequest.builder()
        .parked(taskData.isParked())
        .accountId(accountId)
        .serializationFormat(taskData.getSerializationFormat())
        .taskType(taskData.getTaskType())
        .taskParameters(extractTaskParameters(taskData))
        .executionTimeout(Duration.ofHours(12))
        .taskSetupAbstractions(setupAbstractions)
        .expressionFunctorToken(taskData.getExpressionFunctorToken())
        .eligibleToExecuteDelegateIds(eligibleToExecuteDelegateIds)
        .build();
  }

  private TaskParameters extractTaskParameters(TaskData taskData) {
    if (taskData == null || EmptyPredicate.isEmpty(taskData.getParameters())) {
      return null;
    }
    if (taskData.getParameters()[0] instanceof TaskParameters) {
      return (TaskParameters) taskData.getParameters()[0];
    }
    throw new InvalidRequestException("Task Execution not supported for type");
  }

  public TaskData getDelegateTaskDataForExecuteStep(
      Ambiance ambiance, long timeout, CIK8ExecuteStepTaskParams cik8ExecuteStepTaskParams) {
    String taskType = CONTAINER_EXECUTE_STEP.name();
    SerializationFormat serializationFormat = SerializationFormat.KRYO;

    return TaskData.builder()
        .async(true)
        .parked(false)
        .taskType(taskType)
        .serializationFormat(serializationFormat)
        .parameters(new Object[] {cik8ExecuteStepTaskParams})
        .timeout(timeout)
        .expressionFunctorToken((int) ambiance.getExpressionFunctorToken())
        .build();
  }
}
