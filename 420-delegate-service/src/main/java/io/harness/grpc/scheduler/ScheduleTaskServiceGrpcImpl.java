/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.scheduler;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.stream.Collectors.toMap;

import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.delegate.Execution;
import io.harness.delegate.K8sInfraSpec;
import io.harness.delegate.LogConfig;
import io.harness.delegate.ScheduleTaskRequest;
import io.harness.delegate.ScheduleTaskResponse;
import io.harness.delegate.ScheduleTaskServiceGrpc.ScheduleTaskServiceImplBase;
import io.harness.delegate.SchedulingConfig;
import io.harness.delegate.SetupExecutionInfrastructureRequest;
import io.harness.delegate.SetupExecutionInfrastructureResponse;
import io.harness.delegate.StepSpec;
import io.harness.delegate.TaskId;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.NoDelegatesException;
import io.harness.delegate.beans.RunnerType;
import io.harness.delegate.beans.SchedulingTaskEvent;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.executionInfra.ExecutionInfrastructureService;
import io.harness.grpc.scheduler.mapper.K8sInfraMapper;
import io.harness.logstreaming.LogStreamingServiceRestClient;
import io.harness.network.SafeHttpCall;
import io.harness.taskclient.TaskClient;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.util.Durations;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ScheduleTaskServiceGrpcImpl extends ScheduleTaskServiceImplBase {
  private final DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  private final TaskClient taskClient;
  private final ExecutionInfrastructureService infraService;
  private final LogStreamingServiceRestClient logServiceClient;

  // TODO: Reuse cache once it's extracted from service classic, at which point this guice binding should be removed
  @Named("logServiceSecret") private final String logServiceSecret;

  @Override
  public void initTask(final SetupExecutionInfrastructureRequest request,
      final StreamObserver<SetupExecutionInfrastructureResponse> responseObserver) {
    if (!request.hasConfig()) {
      log.error("Scheduling config is empty");
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("Scheduling config is mandatory").asRuntimeException());
    }

    final SchedulingConfig schedulingConfig = request.getConfig();
    if (RunnerType.RUNNER_TYPE_K8S.equals(schedulingConfig.getRunnerType())) {
      try {
        responseObserver.onNext(
            sendInitK8SInfraTask(request.getInfra().getK8S(), request.getInfra().getLogConfig(), schedulingConfig));
        responseObserver.onCompleted();
      } catch (IOException e) {
        log.error(
            "Failed to fetch logging token for account {} while scheduling task", schedulingConfig.getAccountId(), e);
        responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
      } catch (NoDelegatesException e) {
        log.error("No delegate exception received while submitting the task request for account {}. Reason {}",
            request.getConfig().getAccountId(), ExceptionUtils.getMessage(e), e);
        responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
      } catch (Exception ex) {
        log.error("Unexpected error occurred while processing submit task request.", ex);
        responseObserver.onError(Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
      }
    } else {
      log.error("Unsupported runner type {}", schedulingConfig.getRunnerType());
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("Unsupported runner type " + schedulingConfig.getRunnerType())
              .asRuntimeException());
    }
  }

  @Override
  public void executeTask(
      final ScheduleTaskRequest request, final StreamObserver<ScheduleTaskResponse> responseObserver) {
    final Execution execution = request.getExecution();
    if (!request.hasExecution() && StringUtils.isEmpty(execution.getInfraRefId())) {
      log.error("Infra ref id is empty for account {}", request.getConfig().getAccountId());
      responseObserver.onError(
          Status.INVALID_ARGUMENT.withDescription("infra_ref_id is mandatory").asRuntimeException());
      return;
    }

    try {
      responseObserver.onNext(sendExecuteTask(execution, request.getConfig()));
      responseObserver.onCompleted();
    } catch (NoDelegatesException e) {
      log.error("No delegate exception found while processing submit task request for account {}. reason {}",
          request.getConfig().getAccountId(), ExceptionUtils.getMessage(e));
      responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing submit task request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  private SetupExecutionInfrastructureResponse sendInitK8SInfraTask(
      final K8sInfraSpec infra, final LogConfig logConfig, final SchedulingConfig config) throws IOException {
    final var loggingToken = getLoggingToken(config.getAccountId());
    final var taskId = delegateTaskMigrationHelper.generateDelegateTaskUUID();
    final var executionTaskIds =
        infra.getStepsList()
            .stream()
            .map(StepSpec::getStepId)
            .collect(toMap(Function.identity(), stepId -> delegateTaskMigrationHelper.generateDelegateTaskUUID()));

    infraService.createExecutionInfra(taskId, executionTaskIds, config.getRunnerType());

    final var k8SInfra = K8sInfraMapper.map(infra, executionTaskIds, logConfig, loggingToken);

    final var capabilities = mapSelectorCapability(config);
    capabilities.add(mapRunnerCapability(config));

    sendTask(config, null, k8SInfra.toByteArray(), SchedulingTaskEvent.EventType.SETUP, taskId, taskId, capabilities);

    return SetupExecutionInfrastructureResponse.newBuilder()
        .setTaskId(TaskId.newBuilder().setId(taskId).build())
        .setInfraRefId(taskId)
        .putAllStepTaskIds(executionTaskIds)
        .build();
  }

  private ScheduleTaskResponse sendExecuteTask(final Execution execution, final SchedulingConfig config) {
    final var taskId = delegateTaskMigrationHelper.generateDelegateTaskUUID();
    final var taskData = execution.getInput().getData().toByteArray();
    final var capabilities = mapSelectorCapability(config);
    capabilities.add(mapInfraCapability(execution.getInfraRefId()));

    sendTask(
        config, taskData, null, SchedulingTaskEvent.EventType.EXECUTE, execution.getInfraRefId(), taskId, capabilities);

    return ScheduleTaskResponse.newBuilder().setTaskId(TaskId.newBuilder().setId(taskId).build()).build();
  }

  private void sendTask(final SchedulingConfig schedulingConfig, final byte[] taskData, final byte[] infraData,
      final SchedulingTaskEvent.EventType eventType, final String executionInfraRef, final String taskId,
      final List<ExecutionCapability> capabilities) {

    final var task =
        DelegateTask.builder()
            .uuid(taskId)
            .eventType(eventType.name())
            .runnerType(schedulingConfig.getRunnerType())
            .infraId(executionInfraRef)
            .driverId(schedulingConfig.hasCallbackToken() ? schedulingConfig.getCallbackToken().getToken() : null)
            .waitId(taskId)
            .accountId(schedulingConfig.getAccountId())
            .executionCapabilities(capabilities)
            .selectionLogsTrackingEnabled(schedulingConfig.getSelectionTrackingLogEnabled())
            .taskData(taskData)
            .runnerData(infraData)
            .executionTimeout(Durations.toMillis(schedulingConfig.getExecutionTimeout()))
            .executeOnHarnessHostedDelegates(false)
            .emitEvent(false)
            .async(true)
            .forceExecute(false)
            .build();

    taskClient.sendTask(task);
  }

  private static SelectorCapability mapRunnerCapability(final SchedulingConfig schedulingConfig) {
    return SelectorCapability.builder().selectors(Set.of(schedulingConfig.getRunnerType())).build();
  }

  private List<ExecutionCapability> mapSelectorCapability(final SchedulingConfig schedulingConfig) {
    return schedulingConfig.getSelectorsList()
        .stream()
        .filter(s -> isNotEmpty(s.getSelector()))
        .map(this::toSelectorCapability)
        .collect(Collectors.toList());
  }

  private ExecutionCapability mapInfraCapability(final String infraRefId) {
    final var locationInfo = infraService.getExecutionInfra(infraRefId);
    return SelectorCapability.builder().selectors(Set.of(locationInfo.getDelegateGroupName())).build();
  }

  private String getLoggingToken(final String accountId) throws IOException {
    return SafeHttpCall.executeWithExceptions(logServiceClient.retrieveAccountToken(logServiceSecret, accountId));
  }

  private SelectorCapability toSelectorCapability(TaskSelector taskSelector) {
    final var origin = isNotEmpty(taskSelector.getOrigin()) ? taskSelector.getOrigin() : "default";
    return SelectorCapability.builder()
        .selectors(Sets.newHashSet(taskSelector.getSelector()))
        .selectorOrigin(origin)
        .build();
  }
}
