/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.states;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODE_BASE_CONNECTOR_REF;
import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.common.CIExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.common.CIExecutionConstants.TMP_PATH;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.states.InitializeTaskStep.LE_STATUS_TASK_TYPE;
import static io.harness.steps.StepUtils.buildAbstractions;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.outcomes.VmDetailsOutcome;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.CIStepOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.sweepingoutputs.CodeBaseConnectorRefSweepingOutput;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.serializer.PluginCompatibleStepSerializer;
import io.harness.ci.serializer.PluginStepProtobufSerializer;
import io.harness.ci.serializer.RunStepProtobufSerializer;
import io.harness.ci.serializer.RunTestsStepProtobufSerializer;
import io.harness.ci.serializer.vm.VmStepSerializer;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIExecuteStepTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8ExecuteStepTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.CIVmExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.delegate.task.HDelegateTask;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskParameters;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.encryption.Scope;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.ngexception.CILiteEngineException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.ExecuteStepRequest;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.steps.StepUtils;
import io.harness.steps.executable.AsyncExecutableWithRbac;
import io.harness.tasks.ResponseData;
import io.harness.util.GithubApiFunctor;
import io.harness.util.GithubApiTokenEvaluator;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CI)
public abstract class AbstractStepExecutable implements AsyncExecutableWithRbac<StepElementParameters> {
  public static final String CI_EXECUTE_STEP = "CI_EXECUTE_STEP";
  public static final long bufferTimeMillis =
      5 * 1000; // These additional 5 seconds are approx time spent on creating delegate ask and receiving response
  @Inject private RunStepProtobufSerializer runStepProtobufSerializer;
  @Inject private PluginStepProtobufSerializer pluginStepProtobufSerializer;
  @Inject private RunTestsStepProtobufSerializer runTestsStepProtobufSerializer;
  @Inject private PluginCompatibleStepSerializer pluginCompatibleStepSerializer;
  @Inject private ExceptionManager exceptionManager;
  @Inject private OutcomeService outcomeService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private VmStepSerializer vmStepSerializer;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // No validation is require, all connectors will be validated in Lite Engine Step
  }

  @Override
  public void handleForCallbackId(Ambiance ambiance, StepElementParameters stepParameters, List<String> allCallbackIds,
      String callbackId, ResponseData responseData) {
    if (responseData instanceof VmTaskExecutionResponse) {
      VmTaskExecutionResponse vmTaskExecutionResponse = (VmTaskExecutionResponse) responseData;
      if (vmTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.FAILURE
          || vmTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SKIPPED) {
        abortTasks(allCallbackIds, callbackId, ambiance);
      }
    }
    if (responseData instanceof K8sTaskExecutionResponse) {
      K8sTaskExecutionResponse k8sTaskExecutionResponse = (K8sTaskExecutionResponse) responseData;
      if (k8sTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.FAILURE
          || k8sTaskExecutionResponse.getCommandExecutionStatus() == CommandExecutionStatus.SKIPPED) {
        abortTasks(allCallbackIds, callbackId, ambiance);
      }
    }

    if (responseData instanceof ErrorNotifyResponseData) {
      abortTasks(allCallbackIds, callbackId, ambiance);
    }
  }

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    String runtimeId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String logKey = getLogKey(ambiance);
    String stepIdentifier = AmbianceUtils.obtainOriginalStepIdentifier(ambiance);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    ParameterField<String> timeout = stepParameters.getTimeout();
    String stepParametersName = stepParameters.getName();

    CIStepInfo ciStepInfo = (CIStepInfo) stepParameters.getSpec();

    log.info("Received step {} for execution with type {}", stepIdentifier,
        ((CIStepInfo) stepParameters.getSpec()).getStepType().getType());

    resolveGitAppFunctor(ambiance, ciStepInfo);

    long timeoutInMillis = ciStepInfo.getDefaultTimeout();
    String stringTimeout = "2h";

    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      timeoutInMillis = Timeout.fromString((String) timeout.fetchFinalValue()).getTimeoutInMillis() + bufferTimeMillis;
      stringTimeout = (String) timeout.fetchFinalValue();
    }

    StageInfraDetails stageInfraDetails = getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
    if (stageInfraType == StageInfraDetails.Type.K8) {
      return executeK8AsyncAfterRbac(ambiance, stepIdentifier, runtimeId, ciStepInfo, stepParametersName, accountId,
          logKey, timeoutInMillis, stringTimeout, (K8StageInfraDetails) stageInfraDetails);
    } else if (stageInfraType == StageInfraDetails.Type.VM) {
      return executeVmAsyncAfterRbac(ambiance, stepIdentifier, runtimeId, ciStepInfo, accountId, logKey,
          timeoutInMillis, stringTimeout, (VmStageInfraDetails) stageInfraDetails);
    } else {
      throw new CIStageExecutionException(format("Invalid infra type: %s", stageInfraType));
    }
  }

  public List<TaskSelector> fetchDelegateSelector(Ambiance ambiance) {
    return connectorUtils.fetchDelegateSelector(ambiance, executionSweepingOutputResolver);
  }

  private void abortTasks(List<String> allCallbackIds, String callbackId, Ambiance ambiance) {
    List<String> callBackIds =
        allCallbackIds.stream().filter(cid -> !cid.equals(callbackId)).collect(Collectors.toList());
    callBackIds.forEach(callbackId1 -> {
      waitNotifyEngine.doneWith(callbackId1,
          ErrorNotifyResponseData.builder()
              .errorMessage("Delegate is not able to connect to created build farm")
              .build());
    });
  }

  private AsyncExecutableResponse executeK8AsyncAfterRbac(Ambiance ambiance, String stepIdentifier, String runtimeId,
      CIStepInfo ciStepInfo, String stepParametersName, String accountId, String logKey, long timeoutInMillis,
      String stringTimeout, K8StageInfraDetails k8StageInfraDetails) {
    String parkedTaskId = queueParkedDelegateTask(ambiance, timeoutInMillis, accountId, ciDelegateTaskExecutor);
    OSType os = IntegrationStageUtils.getK8OS(k8StageInfraDetails.getInfrastructure());
    UnitStep unitStep = serialiseStep(ciStepInfo, parkedTaskId, logKey, stepIdentifier,
        getPort(ambiance, stepIdentifier), accountId, stepParametersName, stringTimeout, os);
    String liteEngineTaskId =
        queueK8DelegateTask(ambiance, timeoutInMillis, accountId, ciDelegateTaskExecutor, unitStep, runtimeId);

    log.info(
        "Created parked task {} and lite engine task {} for  step {}", parkedTaskId, liteEngineTaskId, stepIdentifier);

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(parkedTaskId)
        .addCallbackIds(liteEngineTaskId)
        .addAllLogKeys(CollectionUtils.emptyIfNull(singletonList(logKey)))
        .build();
  }

  private AsyncExecutableResponse executeVmAsyncAfterRbac(Ambiance ambiance, String stepIdentifier, String runtimeId,
      CIStepInfo ciStepInfo, String accountId, String logKey, long timeoutInMillis, String stringTimeout,
      VmStageInfraDetails vmStageInfraDetails) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage details sweeping output cannot be empty");
    }
    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();

    OptionalOutcome optionalOutput = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME));
    if (!optionalOutput.isFound()) {
      throw new CIStageExecutionException("Initialise outcome cannot be empty");
    }
    VmDetailsOutcome vmDetailsOutcome = (VmDetailsOutcome) optionalOutput.getOutcome();
    if (isEmpty(vmDetailsOutcome.getIpAddress())) {
      throw new CIStageExecutionException("Ip address in initialise outcome cannot be empty");
    }

    VmStepInfo vmStepInfo = vmStepSerializer.serialize(ambiance, ciStepInfo, vmStageInfraDetails, stepIdentifier,
        ParameterField.createValueField(Timeout.fromString(stringTimeout)));
    Set<String> secrets = vmStepSerializer.getStepSecrets(vmStepInfo, ambiance);
    CIVmExecuteStepTaskParams params = CIVmExecuteStepTaskParams.builder()
                                           .ipAddress(vmDetailsOutcome.getIpAddress())
                                           .poolId(vmStageInfraDetails.getPoolId())
                                           .volToMountPath(vmStageInfraDetails.getVolToMountPathMap())
                                           .stageRuntimeId(stageDetails.getStageRuntimeID())
                                           .stepRuntimeId(runtimeId)
                                           .stepId(stepIdentifier)
                                           .stepInfo(vmStepInfo)
                                           .secrets(new ArrayList<>(secrets))
                                           .logKey(logKey)
                                           .workingDir(vmStageInfraDetails.getWorkDir())
                                           .build();
    List<String> eligibleToExecuteDelegateIds = new ArrayList<>();
    if (isNotEmpty(vmDetailsOutcome.getDelegateId())) {
      eligibleToExecuteDelegateIds.add(vmDetailsOutcome.getDelegateId());
    }
    String taskId = queueDelegateTask(ambiance, timeoutInMillis, accountId, ciDelegateTaskExecutor, params,
        new ArrayList<>(), eligibleToExecuteDelegateIds);

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(taskId)
        .addAllLogKeys(CollectionUtils.emptyIfNull(singletonList(logKey)))
        .build();
  }

  private void resolveGitAppFunctor(Ambiance ambiance, CIStepInfo ciStepInfo) {
    if (ciStepInfo.getNonYamlInfo().getStepInfoType() != CIStepInfoType.RUN) {
      return;
    }
    String codeBaseConnectorRef = null;
    OptionalSweepingOutput codeBaseConnectorRefOptionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(CODE_BASE_CONNECTOR_REF));
    if (codeBaseConnectorRefOptionalSweepingOutput.isFound()) {
      CodeBaseConnectorRefSweepingOutput codeBaseConnectorRefSweepingOutput =
          (CodeBaseConnectorRefSweepingOutput) codeBaseConnectorRefOptionalSweepingOutput.getOutput();
      codeBaseConnectorRef = codeBaseConnectorRefSweepingOutput.getCodeBaseConnectorRef();
    }

    GithubApiTokenEvaluator githubApiTokenEvaluator =
        GithubApiTokenEvaluator.builder()
            .githubApiFunctorConfig(GithubApiFunctor.Config.builder()
                                        .codeBaseConnectorRef(codeBaseConnectorRef)
                                        .fetchConnector(false)
                                        .build())
            .build();
    githubApiTokenEvaluator.resolve(
        ciStepInfo, AmbianceUtils.getNgAccess(ambiance), ambiance.getExpressionFunctorToken());
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainOriginalStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);

    StageInfraDetails stageInfraDetails = getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
    if (stageInfraType == StageInfraDetails.Type.K8) {
      return handleK8AsyncResponse(ambiance, stepParameters, responseDataMap);
    } else if (stageInfraType == StageInfraDetails.Type.VM) {
      return handleVmStepResponse(stepIdentifier, responseDataMap);
    } else {
      throw new CIStageExecutionException(format("Invalid infra type: %s", stageInfraType));
    }
  }

  private StepResponse handleK8AsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainOriginalStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData responseData = entry.getValue();
      if (responseData instanceof ErrorNotifyResponseData) {
        FailureData failureData =
            FailureData.newBuilder()
                .addFailureTypes(FailureType.APPLICATION_FAILURE)
                .setLevel(Level.ERROR.name())
                .setCode(GENERAL_ERROR.name())
                .setMessage(emptyIfNull(ExceptionUtils.getMessage(exceptionManager.processException(
                    new CILiteEngineException(((ErrorNotifyResponseData) responseData).getErrorMessage())))))
                .build();

        return StepResponse.builder()
            .status(Status.FAILED)
            .failureInfo(FailureInfo.newBuilder()
                             .setErrorMessage("Delegate is not able to connect to created build farm")
                             .addFailureData(failureData)
                             .build())
            .build();
      }
    }

    StepStatusTaskResponseData stepStatusTaskResponseData = filterK8StepResponse(responseDataMap);

    if (stepStatusTaskResponseData == null) {
      log.error("stepStatusTaskResponseData should not be null for step {}", stepIdentifier);
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().addAllFailureTypes(EnumSet.of(FailureType.APPLICATION_FAILURE)).build())
          .build();
    }
    return buildAndReturnStepResponse(stepStatusTaskResponseData, ambiance, stepParameters, stepIdentifier);
  }

  private StepResponse handleVmStepResponse(String stepIdentifier, Map<String, ResponseData> responseDataMap) {
    log.info("Received response for step {}", stepIdentifier);
    VmTaskExecutionResponse taskResponse = filterVmStepResponse(responseDataMap);
    if (taskResponse == null) {
      log.error("stepStatusTaskResponseData should not be null for step {}", stepIdentifier);
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder().addAllFailureTypes(EnumSet.of(FailureType.APPLICATION_FAILURE)).build())
          .build();
    }

    if (taskResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      StepResponseBuilder stepResponseBuilder = StepResponse.builder().status(Status.SUCCEEDED);
      if (isNotEmpty(taskResponse.getOutputVars())) {
        StepResponse.StepOutcome stepOutcome =
            StepResponse.StepOutcome.builder()
                .outcome(CIStepOutcome.builder().outputVariables(taskResponse.getOutputVars()).build())
                .name("output")
                .build();
        stepResponseBuilder.stepOutcome(stepOutcome);
      }
      return stepResponseBuilder.build();
    } else if (taskResponse.getCommandExecutionStatus() == CommandExecutionStatus.SKIPPED) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    } else {
      String errMsg = "";
      if (isNotEmpty(taskResponse.getErrorMessage())) {
        errMsg = taskResponse.getErrorMessage();
      }
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .setErrorMessage(errMsg)
                           .addAllFailureTypes(EnumSet.of(FailureType.APPLICATION_FAILURE))
                           .build())
          .build();
    }
  }

  private StepResponse buildAndReturnStepResponse(StepStatusTaskResponseData stepStatusTaskResponseData,
      Ambiance ambiance, StepElementParameters stepParameters, String stepIdentifier) {
    long startTime = AmbianceUtils.getCurrentLevelStartTs(ambiance);
    long currentTime = System.currentTimeMillis();

    StepStatus stepStatus = stepStatusTaskResponseData.getStepStatus();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();

    log.info("Received step {} response {} with type {} in {} milliseconds ", stepIdentifier,
        stepStatus.getStepExecutionStatus(), ((CIStepInfo) stepParameters.getSpec()).getStepType().getType(),
        (currentTime - startTime) / 1000);

    if (stepStatus.getStepExecutionStatus() == StepExecutionStatus.SUCCESS) {
      if (stepStatus.getOutput() != null) {
        StepResponse.StepOutcome stepOutcome =
            StepResponse.StepOutcome.builder()
                .outcome(
                    CIStepOutcome.builder().outputVariables(((StepMapOutput) stepStatus.getOutput()).getMap()).build())
                .name("output")
                .build();
        stepResponseBuilder.stepOutcome(stepOutcome);
      }

      StepArtifacts stepArtifacts = handleArtifact(stepStatus.getArtifactMetadata(), stepParameters);
      if (stepArtifacts != null) {
        StepResponse.StepOutcome stepArtifactOutcome =
            StepResponse.StepOutcome.builder()
                .outcome(CIStepArtifactOutcome.builder().stepArtifacts(stepArtifacts).build())
                .group(StepOutcomeGroup.STAGE.name())
                .name("artifact-" + stepIdentifier)
                .build();
        stepResponseBuilder.stepOutcome(stepArtifactOutcome);
      }

      return stepResponseBuilder.status(Status.SUCCEEDED).build();
    } else if (stepStatus.getStepExecutionStatus() == StepExecutionStatus.SKIPPED) {
      return stepResponseBuilder.status(Status.SKIPPED).build();
    } else if (stepStatus.getStepExecutionStatus() == StepExecutionStatus.ABORTED) {
      return stepResponseBuilder.status(Status.ABORTED).build();
    } else {
      return stepResponseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .setErrorMessage(stepStatus.getError())
                           .addAllFailureTypes(EnumSet.of(FailureType.APPLICATION_FAILURE))
                           .build())
          .build();
    }
  }

  protected StepArtifacts handleArtifact(ArtifactMetadata artifactMetadata, StepElementParameters stepParameters) {
    return null;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {}

  private UnitStep serialiseStep(CIStepInfo ciStepInfo, String taskId, String logKey, String stepIdentifier,
      Integer port, String accountId, String stepName, String timeout, OSType os) {
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return runStepProtobufSerializer.serializeStepWithStepParameters((RunStepInfo) ciStepInfo, port, taskId, logKey,
            stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId, stepName);
      case PLUGIN:
        return pluginStepProtobufSerializer.serializeStepWithStepParameters((PluginStepInfo) ciStepInfo, port, taskId,
            logKey, stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId, stepName);
      case GCR:
      case DOCKER:
      case ECR:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_GCS:
      case UPLOAD_S3:
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
      case SAVE_CACHE_S3:
      case SECURITY:
      case RESTORE_CACHE_S3:
        return pluginCompatibleStepSerializer.serializeStepWithStepParameters((PluginCompatibleStep) ciStepInfo, port,
            taskId, logKey, stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId,
            stepName, os);
      case RUN_TESTS:
        return runTestsStepProtobufSerializer.serializeStepWithStepParameters((RunTestsStepInfo) ciStepInfo, port,
            taskId, logKey, stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId,
            stepName);
      case CLEANUP:
      case TEST:
      case BUILD:
      case SETUP_ENV:
      case GIT_CLONE:
      case INITIALIZE_TASK:
      default:
        log.info("serialisation is not implemented");
        return null;
    }
  }

  private String queueK8DelegateTask(Ambiance ambiance, long timeout, String accountId, CIDelegateTaskExecutor executor,
      UnitStep unitStep, String executionId) {
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome = (LiteEnginePodDetailsOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME));
    String ip = liteEnginePodDetailsOutcome.getIpAddress();

    ExecuteStepRequest executeStepRequest =
        ExecuteStepRequest.newBuilder().setExecutionId(executionId).setStep(unitStep).setTmpFilePath(TMP_PATH).build();
    CIK8ExecuteStepTaskParams params =
        CIK8ExecuteStepTaskParams.builder()
            .ip(ip)
            .port(LITE_ENGINE_PORT)
            .serializedStep(executeStepRequest.toByteArray())
            .isLocal(ciExecutionServiceConfig.isLocal())
            .delegateSvcEndpoint(ciExecutionServiceConfig.getDelegateServiceEndpointVariableValue())
            .build();
    List<TaskSelector> taskSelectors = fetchDelegateSelector(ambiance);
    return queueDelegateTask(ambiance, timeout, accountId, executor, params,
        taskSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toList()), new ArrayList<>());
  }

  private String queueDelegateTask(Ambiance ambiance, long timeout, String accountId, CIDelegateTaskExecutor executor,
      CIExecuteStepTaskParams ciExecuteStepTaskParams, List<String> taskSelectors,
      List<String> eligibleToExecuteDelegateIds) {
    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .parked(false)
                                  .taskType(CI_EXECUTE_STEP)
                                  .parameters(new Object[] {ciExecuteStepTaskParams})
                                  .timeout(timeout)
                                  .expressionFunctorToken((int) ambiance.getExpressionFunctorToken())
                                  .build();

    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);

    HDelegateTask task = (HDelegateTask) StepUtils.prepareDelegateTaskInput(accountId, taskData, abstractions);

    return executor.queueTask(abstractions, task, taskSelectors, eligibleToExecuteDelegateIds);
  }

  private String queueParkedDelegateTask(
      Ambiance ambiance, long timeout, String accountId, CIDelegateTaskExecutor executor) {
    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .parked(true)
                                  .taskType(LE_STATUS_TASK_TYPE)
                                  .parameters(new Object[] {StepStatusTaskParameters.builder().build()})
                                  .timeout(timeout)
                                  .build();

    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);
    HDelegateTask task = (HDelegateTask) StepUtils.prepareDelegateTaskInput(accountId, taskData, abstractions);

    return executor.queueTask(abstractions, task, new ArrayList<>(), new ArrayList<>());
  }

  private String getLogKey(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance);
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }

  private Integer getPort(Ambiance ambiance, String stepIdentifier) {
    // Ports are assigned in lite engine step
    ContainerPortDetails containerPortDetails = (ContainerPortDetails) executionSweepingOutputResolver.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(PORT_DETAILS));

    List<Integer> ports = containerPortDetails.getPortDetails().get(stepIdentifier);

    if (ports.size() != 1) {
      throw new CIStageExecutionException(format("Step [%s] should map to single port", stepIdentifier));
    }

    return ports.get(0);
  }

  private StepStatusTaskResponseData filterK8StepResponse(Map<String, ResponseData> responseDataMap) {
    // Filter final response from step
    return responseDataMap.entrySet()
        .stream()
        .filter(entry -> entry.getValue() instanceof StepStatusTaskResponseData)
        .findFirst()
        .map(obj -> (StepStatusTaskResponseData) obj.getValue())
        .orElse(null);
  }

  private VmTaskExecutionResponse filterVmStepResponse(Map<String, ResponseData> responseDataMap) {
    // Filter final response from step
    return responseDataMap.entrySet()
        .stream()
        .filter(entry -> entry.getValue() instanceof VmTaskExecutionResponse)
        .findFirst()
        .map(obj -> (VmTaskExecutionResponse) obj.getValue())
        .orElse(null);
  }

  private StageInfraDetails getStageInfra(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage infra details sweeping output cannot be empty");
    }

    return (StageInfraDetails) optionalSweepingOutput.getOutput();
  }
}
