/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.grpc;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.RunnerType.K8sRunnerExecEndpoint;
import static io.harness.delegate.beans.RunnerType.K8sRunnerInfraEndpoint;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskBuilder;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.callback.DelegateCallbackToken;
import io.harness.delegate.CancelTaskRequest;
import io.harness.delegate.CancelTaskResponse;
import io.harness.delegate.CreatePerpetualTaskRequest;
import io.harness.delegate.CreatePerpetualTaskResponse;
import io.harness.delegate.DelegateServiceGrpc.DelegateServiceImplBase;
import io.harness.delegate.DeletePerpetualTaskRequest;
import io.harness.delegate.DeletePerpetualTaskResponse;
import io.harness.delegate.ExecuteParkedTaskRequest;
import io.harness.delegate.ExecuteParkedTaskResponse;
import io.harness.delegate.Execution;
import io.harness.delegate.FetchParkedTaskStatusRequest;
import io.harness.delegate.FetchParkedTaskStatusResponse;
import io.harness.delegate.K8sInfraSpec;
import io.harness.delegate.LogConfig;
import io.harness.delegate.RegisterCallbackRequest;
import io.harness.delegate.RegisterCallbackResponse;
import io.harness.delegate.ResetPerpetualTaskRequest;
import io.harness.delegate.ResetPerpetualTaskResponse;
import io.harness.delegate.ScheduleTaskRequest;
import io.harness.delegate.SchedulingConfig;
import io.harness.delegate.SendTaskProgressRequest;
import io.harness.delegate.SendTaskProgressResponse;
import io.harness.delegate.SendTaskStatusRequest;
import io.harness.delegate.SendTaskStatusResponse;
import io.harness.delegate.SetupExecutionInfrastructureRequest;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.SubmitTaskResponse;
import io.harness.delegate.SupportedTaskTypeRequest;
import io.harness.delegate.SupportedTaskTypeResponse;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskExecutionStage;
import io.harness.delegate.TaskId;
import io.harness.delegate.TaskMode;
import io.harness.delegate.TaskProgressRequest;
import io.harness.delegate.TaskProgressResponse;
import io.harness.delegate.TaskProgressUpdatesRequest;
import io.harness.delegate.TaskProgressUpdatesResponse;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.WebsocketAPIRequest;
import io.harness.delegate.beans.DelegateProgressData;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.NoDelegatesException;
import io.harness.delegate.beans.RunnerType;
import io.harness.delegate.beans.SchedulingTaskEvent;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.core.beans.ContainerSpec;
import io.harness.delegate.core.beans.EmptyDirVolume;
import io.harness.delegate.core.beans.ExecutionMode;
import io.harness.delegate.core.beans.ExecutionPriority;
import io.harness.delegate.core.beans.K8SInfra;
import io.harness.delegate.core.beans.K8SStep;
import io.harness.delegate.core.beans.PluginSource;
import io.harness.delegate.core.beans.Resource;
import io.harness.delegate.core.beans.ResourceRequirements;
import io.harness.delegate.core.beans.ResourceType;
import io.harness.delegate.core.beans.SecurityContext;
import io.harness.delegate.core.beans.StepRuntime;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.executionInfra.ExecutionInfraLocation;
import io.harness.executionInfra.ExecutionInfrastructureService;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskClientContext.PerpetualTaskClientContextBuilder;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.taskclient.TaskClient;

import software.wings.beans.SerializationFormat;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import io.grpc.stub.StreamObserver;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

@Singleton
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@BreakDependencyOn("io.harness.delegate.beans.DelegateTaskResponse")
public class DelegateServiceGrpcImpl extends DelegateServiceImplBase {
  private DelegateCallbackRegistry delegateCallbackRegistry;
  private PerpetualTaskService perpetualTaskService;
  private DelegateService delegateService;
  private KryoSerializer kryoSerializer;

  private KryoSerializer referenceFalseKryoSerializer;
  private DelegateTaskService delegateTaskService;
  private DelegateTaskServiceClassic delegateTaskServiceClassic;

  private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  private TaskClient taskClient;
  private ExecutionInfrastructureService executionInfrastructureService;

  @Inject
  public DelegateServiceGrpcImpl(DelegateCallbackRegistry delegateCallbackRegistry,
      PerpetualTaskService perpetualTaskService, DelegateService delegateService,
      DelegateTaskService delegateTaskService, KryoSerializer kryoSerializer,
      @Named("referenceFalseKryoSerializer") KryoSerializer referenceFalseKryoSerializer,
      DelegateTaskServiceClassic delegateTaskServiceClassic, DelegateTaskMigrationHelper delegateTaskMigrationHelper,
      TaskClient taskClient, ExecutionInfrastructureService executionInfrastructureService) {
    this.delegateCallbackRegistry = delegateCallbackRegistry;
    this.perpetualTaskService = perpetualTaskService;
    this.delegateService = delegateService;
    this.kryoSerializer = kryoSerializer;
    this.referenceFalseKryoSerializer = referenceFalseKryoSerializer;
    this.delegateTaskService = delegateTaskService;
    this.delegateTaskServiceClassic = delegateTaskServiceClassic;
    this.delegateTaskMigrationHelper = delegateTaskMigrationHelper;
    this.taskClient = taskClient;
    this.executionInfrastructureService = executionInfrastructureService;
  }

  @Override
  public void createWebsocketAPIRequest(
      WebsocketAPIRequest request, StreamObserver<SubmitTaskResponse> responseObserver) {
    try {
      String taskId = delegateTaskMigrationHelper.generateDelegateTaskUUID();
      final SchedulingConfig networkMetadata = request.getTaskNetworkMetadata();
      scheduleTaskInternal(networkMetadata, request.getData().toByteArray(), null, SchedulingTaskEvent.Method.POST,
          "/execution/k8s", Optional.empty(), taskId, responseObserver);

    } catch (Exception ex) {
      if (ex instanceof NoDelegatesException) {
        log.warn("No delegate exception found while processing submit task request. reason {}",
            ExceptionUtils.getMessage(ex));
      } else {
        log.error("Unexpected error occurred while processing submit task request.", ex);
      }
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  // Helper function that will persist a delegate grpc request to task.
  private void scheduleTaskInternal(SchedulingConfig schedulingConfig, byte[] taskData, byte[] infraData,
      SchedulingTaskEvent.Method method, String requestUri, Optional<String> executionInfraRef, String taskId,
      StreamObserver<SubmitTaskResponse> responseObserver) {
    try {
      Map<String, String> setupAbstractions = schedulingConfig.getSetupAbstractions().getValuesMap();

      // Only selector capabilities
      List<ExecutionCapability> capabilities = new ArrayList<>();

      // only selector capabilities are kept
      if (isNotEmpty(schedulingConfig.getSelectorsList())) {
        capabilities = schedulingConfig.getSelectorsList()
                           .stream()
                           .filter(s -> isNotEmpty(s.getSelector()))
                           .map(this::toSelectorCapability)
                           .collect(Collectors.toList());
      }
      capabilities.add(SelectorCapability.builder().selectors(Set.of(schedulingConfig.getRunnerType())).build());
      // Read execution infrastructure location information.
      if (executionInfraRef.isPresent()) {
        // Enter here only when executing a task with given executionInfraRef
        ExecutionInfraLocation locationInfo =
            executionInfrastructureService.getExecutionInfrastructure(executionInfraRef.get());
        capabilities.add(SelectorCapability.builder().selectors(Set.of(locationInfo.getDelegateGroupName())).build());
      }

      DelegateTaskBuilder taskBuilder =
          DelegateTask.builder()
              .uuid(taskId)
              .requestMethod(method.name())
              .requestUri(requestUri)
              .runnerType(schedulingConfig.getRunnerType())
              .infraId(executionInfraRef.orElse(taskId))
              .driverId(schedulingConfig.hasCallbackToken() ? schedulingConfig.getCallbackToken().getToken() : null)
              .waitId(taskId)
              .accountId(schedulingConfig.getAccountId())
              .setupAbstractions(setupAbstractions)
              .workflowExecutionId(setupAbstractions.get(DelegateTaskKeys.workflowExecutionId))
              .executionCapabilities(capabilities)
              .selectionLogsTrackingEnabled(schedulingConfig.getSelectionTrackingLogEnabled())
              .taskData(taskData)
              .runnerData(infraData)
              .executionTimeout(Durations.toMillis(schedulingConfig.getExecutionTimeout()))
              .requestUri(requestUri)
              .requestMethod(method.name())
              // TODO: we keep these configs internal until we realize use cases to expose
              //  them through APIs
              .executeOnHarnessHostedDelegates(false)
              .emitEvent(false)
              .async(true)
              .forceExecute(false);

      DelegateTask task = taskBuilder.build();
      taskClient.sendTask(task);
      responseObserver.onNext(SubmitTaskResponse.newBuilder()
                                  .setTaskId(TaskId.newBuilder().setId(taskId).build())
                                  .setTotalExpiry(Timestamps.fromMillis(task.getExpiry() + task.getExecutionTimeout()))
                                  .build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      if (ex instanceof NoDelegatesException) {
        log.warn("No delegate exception found while processing submit task request. reason {}",
            ExceptionUtils.getMessage(ex));
      } else {
        log.error("Unexpected error occurred while processing submit task request.", ex);
      }
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void isTaskTypeSupported(
      SupportedTaskTypeRequest request, StreamObserver<SupportedTaskTypeResponse> responseObserver) {
    try {
      String accountId = request.getAccountId();
      String taskType = request.getTaskType();

      boolean isSupported = delegateTaskService.isTaskTypeSupportedByAllDelegates(accountId, taskType);

      responseObserver.onNext(SupportedTaskTypeResponse.newBuilder().setIsTaskTypeSupported(isSupported).build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Exception occurred during finding supported task type.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void submitTask(SubmitTaskRequest request, StreamObserver<SubmitTaskResponse> responseObserver) {
    try {
      String taskId = delegateTaskMigrationHelper.generateDelegateTaskUUID();
      TaskDetails taskDetails = request.getDetails();
      Map<String, String> setupAbstractions = request.getSetupAbstractions().getValuesMap();
      LinkedHashMap<String, String> logAbstractions =
          request.getLogAbstractions() == null || request.getLogAbstractions().getValuesMap() == null
          ? new LinkedHashMap<>()
          : new LinkedHashMap<>(request.getLogAbstractions().getValuesMap());
      List<ExecutionCapability> capabilities = request.getCapabilitiesList()
                                                   .stream()
                                                   .map(capability
                                                       -> (ExecutionCapability) kryoSerializer.asInflatedObject(
                                                           capability.getKryoCapability().toByteArray()))
                                                   .collect(Collectors.toList());

      if (isNotEmpty(request.getSelectorsList())) {
        List<SelectorCapability> selectorCapabilities = request.getSelectorsList()
                                                            .stream()
                                                            .filter(s -> isNotEmpty(s.getSelector()))
                                                            .map(this::toSelectorCapability)
                                                            .collect(Collectors.toList());
        capabilities.addAll(selectorCapabilities);
      }

      DelegateTaskBuilder taskBuilder =
          DelegateTask.builder()
              .uuid(taskId)
              .driverId(request.hasCallbackToken() ? request.getCallbackToken().getToken() : null)
              .waitId(taskId)
              .accountId(request.getAccountId().getId())
              .setupAbstractions(setupAbstractions)
              .logStreamingAbstractions(logAbstractions)
              .workflowExecutionId(setupAbstractions.get(DelegateTaskKeys.workflowExecutionId))
              .executionCapabilities(capabilities)
              .selectionLogsTrackingEnabled(request.getSelectionTrackingLogEnabled())
              .eligibleToExecuteDelegateIds(new LinkedList<>(request.getEligibleToExecuteDelegateIdsList()))
              .executeOnHarnessHostedDelegates(request.getExecuteOnHarnessHostedDelegates())
              .emitEvent(request.getEmitEvent())
              .stageId(request.getStageId())
              .forceExecute(request.getForceExecute())
              .data(createTaskData(taskDetails));

      if (request.hasQueueTimeout()) {
        taskBuilder.expiry(System.currentTimeMillis() + Durations.toMillis(request.getQueueTimeout()));
      }

      DelegateTask task = taskBuilder.build();

      if (task.getData().isParked()) {
        delegateTaskServiceClassic.processDelegateTask(task, DelegateTask.Status.PARKED);
      } else {
        if (task.getData().isAsync()) {
          delegateService.queueTask(task);
        } else {
          delegateService.scheduleSyncTask(task);
        }
      }
      responseObserver.onNext(SubmitTaskResponse.newBuilder()
                                  .setTaskId(TaskId.newBuilder().setId(taskId).build())
                                  .setTotalExpiry(Timestamps.fromMillis(task.getExpiry() + task.getData().getTimeout()))
                                  .build());
      responseObserver.onCompleted();

    } catch (Exception ex) {
      if (ex instanceof NoDelegatesException) {
        log.warn("No delegate exception found while processing submit task request. reason {}",
            ExceptionUtils.getMessage(ex));
      } else {
        log.error("Unexpected error occurred while processing submit task request.", ex);
      }
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  public void submitTaskV2(SubmitTaskRequest request, StreamObserver<SubmitTaskResponse> responseObserver) {
    try {
      String taskId = delegateTaskMigrationHelper.generateDelegateTaskUUID();
      TaskDetails taskDetails = request.getDetails();
      Map<String, String> setupAbstractions = request.getSetupAbstractions().getValuesMap();
      LinkedHashMap<String, String> logAbstractions =
          request.getLogAbstractions() == null || request.getLogAbstractions().getValuesMap() == null
          ? new LinkedHashMap<>()
          : new LinkedHashMap<>(request.getLogAbstractions().getValuesMap());
      String baseLogKey = "";
      boolean shouldSkipOpenStream = false;
      if (request.getLogAbstractions() != null) {
        baseLogKey =
            request.getLogAbstractions().getBaseLogKey() == null ? "" : request.getLogAbstractions().getBaseLogKey();
        shouldSkipOpenStream = request.getLogAbstractions().getShouldSkipOpenStream();
      }
      List<ExecutionCapability> capabilities =
          request.getCapabilitiesList()
              .stream()
              .map(capability
                  -> (ExecutionCapability) referenceFalseKryoSerializer.asInflatedObject(
                      capability.getKryoCapability().toByteArray()))
              .collect(Collectors.toList());

      if (isNotEmpty(request.getSelectorsList())) {
        List<SelectorCapability> selectorCapabilities = request.getSelectorsList()
                                                            .stream()
                                                            .filter(s -> isNotEmpty(s.getSelector()))
                                                            .map(this::toSelectorCapability)
                                                            .collect(Collectors.toList());
        capabilities.addAll(selectorCapabilities);
      }

      DelegateTaskBuilder taskBuilder =
          DelegateTask.builder()
              .uuid(taskId)
              .driverId(request.hasCallbackToken() ? request.getCallbackToken().getToken() : null)
              .waitId(taskId)
              .accountId(request.getAccountId().getId())
              .setupAbstractions(setupAbstractions)
              .logStreamingAbstractions(logAbstractions)
              .workflowExecutionId(setupAbstractions.get(DelegateTaskKeys.workflowExecutionId))
              .executionCapabilities(capabilities)
              .selectionLogsTrackingEnabled(request.getSelectionTrackingLogEnabled())
              .eligibleToExecuteDelegateIds(new LinkedList<>(request.getEligibleToExecuteDelegateIdsList()))
              .executeOnHarnessHostedDelegates(request.getExecuteOnHarnessHostedDelegates())
              .emitEvent(request.getEmitEvent())
              .stageId(request.getStageId())
              .forceExecute(request.getForceExecute())
              .baseLogKey(baseLogKey)
              .shouldSkipOpenStream(shouldSkipOpenStream)
              .taskDataV2(createTaskDataV2(taskDetails));

      if (request.hasQueueTimeout()) {
        taskBuilder.expiry(System.currentTimeMillis() + Durations.toMillis(request.getQueueTimeout()));
      }

      DelegateTask task = taskBuilder.build();

      if (task.getTaskDataV2().isParked()) {
        delegateTaskServiceClassic.processDelegateTaskV2(task, DelegateTask.Status.PARKED);
      } else {
        if (task.getTaskDataV2().isAsync()) {
          delegateService.queueTaskV2(task);
        } else {
          delegateService.scheduleSyncTaskV2(task);
        }
      }
      responseObserver.onNext(
          SubmitTaskResponse.newBuilder()
              .setTaskId(TaskId.newBuilder().setId(taskId).build())
              .setTotalExpiry(Timestamps.fromMillis(task.getExpiry() + task.getTaskDataV2().getTimeout()))
              .build());
      responseObserver.onCompleted();

    } catch (Exception ex) {
      if (ex instanceof NoDelegatesException) {
        log.warn("No delegate exception found while processing submit task request. reason {}",
            ExceptionUtils.getMessage(ex));
      } else {
        log.error("Unexpected error occurred while processing submit task request.", ex);
      }
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  /**
   * Method to submit a InitTask to delegate to setup
   * the necessary infra before executing the task.
   * @param setupExecutionInfrastructureRequest
   * @param responseObserver
   */
  @Override
  public void initTask(SetupExecutionInfrastructureRequest setupExecutionInfrastructureRequest,
      StreamObserver<SubmitTaskResponse> responseObserver) {
    SchedulingConfig schedulingConfig = setupExecutionInfrastructureRequest.getConfig();
    Optional<String> executionInfraRef = Optional.empty();
    if (schedulingConfig.getRunnerType().equals(RunnerType.RUNNER_TYPE_K8S)) {
      String taskId = delegateTaskMigrationHelper.generateDelegateTaskUUID();
      K8SInfra k8SInfra = buildK8sInfra(setupExecutionInfrastructureRequest, taskId);
      scheduleTaskInternal(setupExecutionInfrastructureRequest.getConfig(), null, k8SInfra.toByteArray(),
          SchedulingTaskEvent.Method.POST, K8sRunnerInfraEndpoint, executionInfraRef, taskId, responseObserver);
    } else {
      // TODO - Add for other Runners
    }
  }

  @Override
  public void executeTask(
      ScheduleTaskRequest scheduleTaskRequest, StreamObserver<SubmitTaskResponse> responseObserver) {
    Execution execution = scheduleTaskRequest.getExecution();
    if (StringUtils.isEmpty(execution.getInfraRefId())) {
      return;
    }

    // Optional<String> executionInfraRef = Optional.of(execution.getInfraRefId());
    Optional<String> executionInfraRef = Optional.empty();
    String taskId = delegateTaskMigrationHelper.generateDelegateTaskUUID();
    scheduleTaskInternal(scheduleTaskRequest.getConfig(), execution.getInput().getData().toByteArray(), null,
        SchedulingTaskEvent.Method.POST, K8sRunnerExecEndpoint, executionInfraRef, taskId, responseObserver);
  }

  private TaskData createTaskData(TaskDetails taskDetails) {
    Object[] parameters = null;
    byte[] data;
    SerializationFormat serializationFormat;
    if (taskDetails.getParametersCase().equals(TaskDetails.ParametersCase.KRYO_PARAMETERS)) {
      serializationFormat = SerializationFormat.KRYO;
      data = taskDetails.getKryoParameters().toByteArray();
      parameters = new Object[] {kryoSerializer.asInflatedObject(data)};
    } else if (taskDetails.getParametersCase().equals(TaskDetails.ParametersCase.JSON_PARAMETERS)) {
      serializationFormat = SerializationFormat.JSON;
      data = taskDetails.getJsonParameters().toStringUtf8().getBytes(StandardCharsets.UTF_8);
    } else {
      throw new InvalidRequestException("Invalid task response type.");
    }

    return TaskData.builder()
        .parked(taskDetails.getParked())
        .async(taskDetails.getMode() == TaskMode.ASYNC)
        .taskType(taskDetails.getType().getType())
        .parameters(parameters)
        .data(data)
        .timeout(Durations.toMillis(taskDetails.getExecutionTimeout()))
        .expressionFunctorToken((int) taskDetails.getExpressionFunctorToken())
        .serializationFormat(serializationFormat)
        .build();
  }

  private K8SInfra buildK8sInfra(
      SetupExecutionInfrastructureRequest setupExecutionInfrastructureRequest, String taskId) {
    K8SInfra.Builder k8SInfraBuilder = K8SInfra.newBuilder();
    LogConfig logConfig = setupExecutionInfrastructureRequest.getInfra().getLogConfig();
    K8sInfraSpec k8sInfraSpec = setupExecutionInfrastructureRequest.getInfra().getK8Infraspec();

    for (var task : k8sInfraSpec.getTasksList()) {
      ResourceRequirements resourceRequirements = ResourceRequirements.newBuilder()
                                                      .setCpu(task.getResource().getCpu())
                                                      .setMemory(task.getResource().getMemory())
                                                      .build();
      var securityContext = task.getSecurityContext();
      var securityContextCopy = SecurityContext.newBuilder()
                                    .setAllowPrivilegeEscalation(securityContext.getAllowPrivilegeEscalation())
                                    .setPrivileged(securityContext.getPrivileged())
                                    .setProcMount(securityContext.getProcMount())
                                    .setReadOnlyRootFilesystem(securityContext.getReadOnlyRootFilesystem())
                                    .setRunAsNonRoot(securityContext.getRunAsNonRoot())
                                    .setRunAsGroup(securityContext.getRunAsGroup())
                                    .setRunAsUser(securityContext.getRunAsUser())
                                    .addAllAddCapability(securityContext.getAddCapabilityList())
                                    .addAllDropCapability(securityContext.getDropCapabilityList())
                                    .build();
      StepRuntime stepRuntime = StepRuntime.newBuilder()
                                    .setResource(resourceRequirements)
                                    .setSource(PluginSource.SOURCE_IMAGE)
                                    .setImage(task.getImage())
                                    .addAllCommand(task.getCommandList())
                                    .addAllArg(task.getArgsList())
                                    .setSecurityContext(securityContextCopy)
                                    .build();

      // TODO - For now for CD there will be only one task and thus we will use the taskId arg.
      K8SStep k8SStep = K8SStep.newBuilder()
                            .setId(taskId)
                            .setMode(ExecutionMode.MODE_ONCE)
                            .setPriority(ExecutionPriority.PRIORITY_DEFAULT)
                            .setRuntime(stepRuntime)
                            .build();
      k8SInfraBuilder.addSteps(k8SStep);
    }

    ContainerSpec leContainerSpec = ContainerSpec
                                        .newBuilder()
                                        //.setImage("harness/ci-lite-engine:1.16.7")
                                        .setImage("raghavendramurali/ci-lite-engine:tag1.6")
                                        .setPort(20001)
                                        .setWorkingDir("/opt/harness")
                                        .build();
    // TODO - Get from Config
    List<String> addonCommand = List.of("sh", "-c", "--");
    List<String> addonArgs = List.of(
        "mkdir -p /addon/bin; mkdir -p /addon/tmp; chmod -R 776 /addon/tmp; if [ -e /usr/local/bin/ci-addon-linux-amd64 ];then cp /usr/local/bin/ci-addon-linux-amd64 /addon/bin/ci-addon;else cp /usr/local/bin/ci-addon-linux /addon/bin/ci-addon;fi; chmod +x /addon/bin/ci-addon; cp /usr/local/bin/tmate /addon/bin/tmate; chmod +x /addon/bin/tmate; cp /usr/local/bin/java-agent.jar /addon/bin/java-agent.jar; chmod +x /addon/bin/java-agent.jar; if [ -e /usr/local/bin/split_tests ];then cp /usr/local/bin/split_tests /addon/bin/split_tests; chmod +x /addon/bin/split_tests; export PATH=$PATH:/addon/bin; fi;");
    ContainerSpec addonContainerSpec = ContainerSpec
                                           .newBuilder()
                                           //.setImage("harness/ci-addon:1.16.7")
                                           .setImage("raghavendramurali/ci-addon:tag1.6")
                                           .setPort(20002)
                                           .setWorkingDir("/opt/harness")
                                           .addAllCommand(addonCommand)
                                           .addAllArgs(addonArgs)
                                           .build();
    ResourceRequirements resourceRequirements = ResourceRequirements.newBuilder()
                                                    .setCpu(k8sInfraSpec.getComputeResource().getCpu())
                                                    .setMemory(k8sInfraSpec.getComputeResource().getMemory())
                                                    .build();

    for (var resource : k8sInfraSpec.getResourcesList()) {
      ResourceType resourceType = ResourceType.RES_UNKNOWN;
      if (resource.getType().getNumber() == 1) {
        resourceType = ResourceType.RES_VOLUME;
      }
      EmptyDirVolume emptyDirVolume = EmptyDirVolume.newBuilder()
                                          .setName(resource.getEmptyDir().getName())
                                          .setPath(resource.getEmptyDir().getPath())
                                          .setSize(resource.getEmptyDir().getSize())
                                          .setMedium(resource.getEmptyDir().getMedium())
                                          .build();
      Any value = Any.pack(emptyDirVolume);
      Resource resourceCopy = Resource.newBuilder().setSpec(value).setType(resourceType).build();
      k8SInfraBuilder.addResources(resourceCopy);
    }

    final var emptyDir = EmptyDirVolume.newBuilder().setName("bijou-dir").setPath("/harness/bijou").build();
    K8SInfra k8SInfra = k8SInfraBuilder.setResource(resourceRequirements)
                            .setWorkingDir("/opt/harness")
                            .addResources(Resource.newBuilder().setSpec(Any.pack(emptyDir)).build())
                            .setLogPrefix(logConfig.getLogKey())
                            .setLogToken(logConfig.getToken())
                            .setLeContainer(leContainerSpec)
                            .setAddonContainer(addonContainerSpec)
                            .build();

    return k8SInfra;
  }

  private TaskDataV2 createTaskDataV2(TaskDetails taskDetails) {
    Object[] parameters = null;
    byte[] data;
    SerializationFormat serializationFormat;
    if (taskDetails.getParametersCase().equals(TaskDetails.ParametersCase.KRYO_PARAMETERS)) {
      serializationFormat = SerializationFormat.KRYO;
      data = taskDetails.getKryoParameters().toByteArray();
      parameters = new Object[] {referenceFalseKryoSerializer.asInflatedObject(data)};
    } else if (taskDetails.getParametersCase().equals(TaskDetails.ParametersCase.JSON_PARAMETERS)) {
      serializationFormat = SerializationFormat.JSON;
      data = taskDetails.getJsonParameters().toStringUtf8().getBytes(StandardCharsets.UTF_8);
    } else {
      throw new InvalidRequestException("Invalid task response type.");
    }

    return TaskDataV2.builder()
        .parked(taskDetails.getParked())
        .async(taskDetails.getMode() == TaskMode.ASYNC)
        .taskType(taskDetails.getType().getType())
        .parameters(parameters)
        .data(data)
        .timeout(Durations.toMillis(taskDetails.getExecutionTimeout()))
        .expressionFunctorToken((int) taskDetails.getExpressionFunctorToken())
        .serializationFormat(io.harness.beans.SerializationFormat.valueOf(serializationFormat.name()))
        .build();
  }

  @Override
  public void executeParkedTask(
      ExecuteParkedTaskRequest request, StreamObserver<ExecuteParkedTaskResponse> responseObserver) {
    try {
      delegateTaskServiceClassic.queueParkedTask(request.getAccountId().getId(), request.getTaskId().getId());

      responseObserver.onNext(ExecuteParkedTaskResponse.newBuilder()
                                  .setTaskId(TaskId.newBuilder().setId(request.getTaskId().getId()).build())
                                  .build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing execute parked task request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void fetchParkedTaskStatus(
      FetchParkedTaskStatusRequest request, StreamObserver<FetchParkedTaskStatusResponse> responseObserver) {
    try {
      byte[] delegateTaskResults = delegateTaskServiceClassic.getParkedTaskResults(
          request.getAccountId().getId(), request.getTaskId().getId(), request.getCallbackToken().getToken());
      if (delegateTaskResults.length > 0) {
        responseObserver.onNext(FetchParkedTaskStatusResponse.newBuilder()
                                    .setSerializedTaskResults(ByteString.copyFrom(delegateTaskResults))
                                    .setFetchResults(true)
                                    .build());
      } else {
        responseObserver.onNext(FetchParkedTaskStatusResponse.newBuilder().setFetchResults(false).build());
      }
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing fetch parked task status request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void sendTaskStatus(SendTaskStatusRequest request, StreamObserver<SendTaskStatusResponse> responseObserver) {
    try {
      DelegateTaskResponse delegateTaskResponse =
          DelegateTaskResponse.builder()
              .responseCode(DelegateTaskResponse.ResponseCode.OK)
              .accountId(request.getAccountId().getId())
              .response((DelegateResponseData) kryoSerializer.asInflatedObject(
                  request.getTaskResponseData().getKryoResultsData().toByteArray()))
              .build();
      delegateTaskService.processDelegateResponse(
          request.getAccountId().getId(), null, request.getTaskId().getId(), delegateTaskResponse);
      responseObserver.onNext(SendTaskStatusResponse.newBuilder().setSuccess(true).build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing send parked task status request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void sendTaskStatusV2(SendTaskStatusRequest request, StreamObserver<SendTaskStatusResponse> responseObserver) {
    try {
      DelegateTaskResponse delegateTaskResponse =
          DelegateTaskResponse.builder()
              .responseCode(DelegateTaskResponse.ResponseCode.OK)
              .accountId(request.getAccountId().getId())
              .response((DelegateResponseData) referenceFalseKryoSerializer.asInflatedObject(
                  request.getTaskResponseData().getKryoResultsData().toByteArray()))
              .build();
      delegateTaskService.processDelegateResponse(
          request.getAccountId().getId(), null, request.getTaskId().getId(), delegateTaskResponse);
      responseObserver.onNext(SendTaskStatusResponse.newBuilder().setSuccess(true).build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing send parked task status request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void sendTaskProgress(
      SendTaskProgressRequest request, StreamObserver<SendTaskProgressResponse> responseObserver) {
    try {
      delegateTaskServiceClassic.publishTaskProgressResponse(request.getAccountId().getId(),
          request.getCallbackToken().getToken(), request.getTaskId().getId(),
          (DelegateProgressData) kryoSerializer.asInflatedObject(
              request.getTaskResponseData().getKryoResultsData().toByteArray()));
      responseObserver.onNext(SendTaskProgressResponse.newBuilder().setSuccess(true).build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing send task progress status request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void sendTaskProgressV2(
      SendTaskProgressRequest request, StreamObserver<SendTaskProgressResponse> responseObserver) {
    try {
      delegateTaskServiceClassic.publishTaskProgressResponse(request.getAccountId().getId(),
          request.getCallbackToken().getToken(), request.getTaskId().getId(),
          (DelegateProgressData) referenceFalseKryoSerializer.asInflatedObject(
              request.getTaskResponseData().getKryoResultsData().toByteArray()));
      responseObserver.onNext(SendTaskProgressResponse.newBuilder().setSuccess(true).build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing send task progress status request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void cancelTask(CancelTaskRequest request, StreamObserver<CancelTaskResponse> responseObserver) {
    try {
      DelegateTask preAbortedTask =
          delegateTaskServiceClassic.abortTask(request.getAccountId().getId(), request.getTaskId().getId());
      if (preAbortedTask != null) {
        responseObserver.onNext(
            CancelTaskResponse.newBuilder()
                .setCanceledAtStage(DelegateTaskGrpcUtils.mapTaskStatusToTaskExecutionStage(preAbortedTask.getStatus()))
                .build());
        responseObserver.onCompleted();
        return;
      }

      responseObserver.onNext(
          CancelTaskResponse.newBuilder().setCanceledAtStage(TaskExecutionStage.TYPE_UNSPECIFIED).build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing cancel task request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void cancelTaskV2(CancelTaskRequest request, StreamObserver<CancelTaskResponse> responseObserver) {
    try {
      DelegateTask preAbortedTask =
          delegateTaskServiceClassic.abortTaskV2(request.getAccountId().getId(), request.getTaskId().getId());
      if (preAbortedTask != null) {
        responseObserver.onNext(
            CancelTaskResponse.newBuilder()
                .setCanceledAtStage(DelegateTaskGrpcUtils.mapTaskStatusToTaskExecutionStage(preAbortedTask.getStatus()))
                .build());
        responseObserver.onCompleted();
        return;
      }

      responseObserver.onNext(
          CancelTaskResponse.newBuilder().setCanceledAtStage(TaskExecutionStage.TYPE_UNSPECIFIED).build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing cancel task request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void taskProgress(TaskProgressRequest request, StreamObserver<TaskProgressResponse> responseObserver) {
    try {
      Optional<DelegateTask> delegateTaskOptional =
          delegateTaskServiceClassic.fetchDelegateTask(request.getAccountId().getId(), request.getTaskId().getId());

      if (delegateTaskOptional.isPresent()) {
        responseObserver.onNext(TaskProgressResponse.newBuilder()
                                    .setCurrentlyAtStage(DelegateTaskGrpcUtils.mapTaskStatusToTaskExecutionStage(
                                        delegateTaskOptional.get().getStatus()))
                                    .build());
        responseObserver.onCompleted();
        return;
      }

      responseObserver.onNext(
          TaskProgressResponse.newBuilder().setCurrentlyAtStage(TaskExecutionStage.TYPE_UNSPECIFIED).build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing task progress request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void taskProgressUpdates(
      TaskProgressUpdatesRequest request, StreamObserver<TaskProgressUpdatesResponse> responseObserver) {
    throw new NotImplementedException(
        "Temporarily removed the implementation until we find more effective way of doing this.");
  }

  @Override
  public void registerCallback(
      RegisterCallbackRequest request, StreamObserver<RegisterCallbackResponse> responseObserver) {
    try {
      String token = delegateCallbackRegistry.ensureCallback(request.getCallback());
      responseObserver.onNext(RegisterCallbackResponse.newBuilder()
                                  .setCallbackToken(DelegateCallbackToken.newBuilder().setToken(token))
                                  .build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing register callback request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void createPerpetualTask(
      CreatePerpetualTaskRequest request, StreamObserver<CreatePerpetualTaskResponse> responseObserver) {
    try {
      String accountId = request.getAccountId().getId();

      PerpetualTaskClientContextBuilder contextBuilder = PerpetualTaskClientContext.builder();

      if (!request.getClientTaskId().isEmpty()) {
        contextBuilder.clientId(request.getClientTaskId());
      }

      if (request.getContext().hasTaskClientParams()) {
        contextBuilder.clientParams(request.getContext().getTaskClientParams().getParamsMap());
      } else if (request.getContext().hasExecutionBundle()) {
        contextBuilder.executionBundle(request.getContext().getExecutionBundle().toByteArray());
      }

      if (request.getContext().getLastContextUpdated() != null) {
        contextBuilder.lastContextUpdated(Timestamps.toMillis(request.getContext().getLastContextUpdated()));
      }

      String perpetualTaskId = perpetualTaskService.createTask(request.getType(), accountId, contextBuilder.build(),
          request.getSchedule(), request.getAllowDuplicate(), request.getTaskDescription());

      responseObserver.onNext(CreatePerpetualTaskResponse.newBuilder()
                                  .setPerpetualTaskId(PerpetualTaskId.newBuilder().setId(perpetualTaskId).build())
                                  .build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing create perpetual task request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void deletePerpetualTask(
      DeletePerpetualTaskRequest request, StreamObserver<DeletePerpetualTaskResponse> responseObserver) {
    try {
      perpetualTaskService.deleteTask(request.getAccountId().getId(), request.getPerpetualTaskId().getId());

      responseObserver.onNext(DeletePerpetualTaskResponse.newBuilder().build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing delete perpetual task request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  @Override
  public void resetPerpetualTask(
      ResetPerpetualTaskRequest request, StreamObserver<ResetPerpetualTaskResponse> responseObserver) {
    try {
      perpetualTaskService.resetTask(
          request.getAccountId().getId(), request.getPerpetualTaskId().getId(), request.getTaskExecutionBundle());

      responseObserver.onNext(ResetPerpetualTaskResponse.newBuilder().build());
      responseObserver.onCompleted();
    } catch (Exception ex) {
      log.error("Unexpected error occurred while processing reset perpetual task request.", ex);
      responseObserver.onError(io.grpc.Status.INTERNAL.withDescription(ex.getMessage()).asRuntimeException());
    }
  }

  private SelectorCapability toSelectorCapability(TaskSelector taskSelector) {
    String origin = isNotEmpty(taskSelector.getOrigin()) ? taskSelector.getOrigin() : "default";
    return SelectorCapability.builder()
        .selectors(Sets.newHashSet(taskSelector.getSelector()))
        .selectorOrigin(origin)
        .build();
  }
}
