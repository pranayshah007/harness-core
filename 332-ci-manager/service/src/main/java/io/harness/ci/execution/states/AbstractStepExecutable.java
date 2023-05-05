/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODE_BASE_CONNECTOR_REF;
import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.CIExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.ci.commonconstants.CIExecutionConstants.TMP_PATH;
import static io.harness.ci.commonconstants.CIExecutionConstants.UNDERSCORE_SEPARATOR;
import static io.harness.ci.states.InitializeTaskStep.LE_STATUS_TASK_TYPE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
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
import io.harness.beans.steps.stepinfo.BackgroundStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.RunTestsStepInfo;
import io.harness.beans.sweepingoutputs.CodeBaseConnectorRefSweepingOutput;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.StepArtifactSweepingOutput;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.executable.CiAsyncExecutable;
import io.harness.ci.integrationstage.CIStepGroupUtils;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.serializer.BackgroundStepProtobufSerializer;
import io.harness.ci.serializer.PluginCompatibleStepSerializer;
import io.harness.ci.serializer.PluginStepProtobufSerializer;
import io.harness.ci.serializer.RunStepProtobufSerializer;
import io.harness.ci.serializer.RunTestsStepProtobufSerializer;
import io.harness.ci.serializer.vm.VmStepSerializer;
import io.harness.ci.utils.GithubApiFunctor;
import io.harness.ci.utils.GithubApiTokenEvaluator;
import io.harness.ci.utils.HostedVmSecretResolver;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIExecuteStepTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8ExecuteStepTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.CIVmExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmExecuteStepTaskParams;
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
import io.harness.helper.SerializedResponseDataHelper;
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
import io.harness.steps.StepUtils;
import io.harness.tasks.ResponseData;
import io.harness.vm.VmExecuteStepUtils;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import io.fabric8.utils.Strings;
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
public abstract class AbstractStepExecutable extends CiAsyncExecutable {
  public static final String CI_EXECUTE_STEP = "CI_EXECUTE_STEP";
  public static final long bufferTimeMillis =
      5 * 1000; // These additional 5 seconds are approx time spent on creating delegate ask and receiving response
  @Inject private RunStepProtobufSerializer runStepProtobufSerializer;
  @Inject private BackgroundStepProtobufSerializer backgroundStepProtobufSerializer;
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
  @Inject private VmExecuteStepUtils vmExecuteStepUtils;
  @Inject private HostedVmSecretResolver hostedVmSecretResolver;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;

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
    responseData = serializedResponseDataHelper.deserialize(responseData);
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
    String stepGroupIdentifier = AmbianceUtils.obtainStepGroupIdentifier(ambiance);
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    String completeStepIdentifier = CIStepGroupUtils.getUniqueStepIdentifier(ambiance.getLevelsList(), stepIdentifier);
    if (Strings.isNotBlank(stepGroupIdentifier)) {
      stepIdentifier = stepGroupIdentifier + UNDERSCORE_SEPARATOR + stepIdentifier;
    }
    String accountId = AmbianceUtils.getAccountId(ambiance);
    ParameterField<String> timeout = stepParameters.getTimeout();
    String stepParametersName = stepParameters.getName();

    CIStepInfo ciStepInfo = (CIStepInfo) stepParameters.getSpec();

    log.info("Received step {} for execution with type {}", stepIdentifier,
        ((CIStepInfo) stepParameters.getSpec()).getStepType().getType());

    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage details sweeping output cannot be empty");
    }

    StageDetails stageDetails = (StageDetails) optionalSweepingOutput.getOutput();
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
      return executeK8AsyncAfterRbac(ambiance, completeStepIdentifier, runtimeId, ciStepInfo, stepParametersName,
          accountId, logKey, timeoutInMillis, stringTimeout, (K8StageInfraDetails) stageInfraDetails, stageDetails);
    } else if (stageInfraType == StageInfraDetails.Type.VM || stageInfraType == StageInfraDetails.Type.DLITE_VM) {
      return executeVmAsyncAfterRbac(ambiance, completeStepIdentifier, stepIdentifier, runtimeId, ciStepInfo, accountId,
          logKey, timeoutInMillis, stringTimeout, stageInfraDetails, stageDetails);
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
      String stringTimeout, K8StageInfraDetails k8StageInfraDetails, StageDetails stageDetails) {
    String parkedTaskId = queueParkedDelegateTask(ambiance, timeoutInMillis, accountId, ciDelegateTaskExecutor);
    OSType os = IntegrationStageUtils.getK8OS(k8StageInfraDetails.getInfrastructure());
    UnitStep unitStep = serialiseStep(ciStepInfo, parkedTaskId, logKey, stepIdentifier,
        getPort(ambiance, stepIdentifier), accountId, stepParametersName, stringTimeout, os, ambiance, stageDetails);
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

  private AsyncExecutableResponse executeVmAsyncAfterRbac(Ambiance ambiance, String completeStepIdentifier,
      String stepIdentifier, String runtimeId, CIStepInfo ciStepInfo, String accountId, String logKey,
      long timeoutInMillis, String stringTimeout, StageInfraDetails stageInfraDetails, StageDetails stageDetails) {
    OptionalOutcome optionalOutput = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(VmDetailsOutcome.VM_DETAILS_OUTCOME));
    if (!optionalOutput.isFound()) {
      throw new CIStageExecutionException("Initialise outcome cannot be empty");
    }
    VmDetailsOutcome vmDetailsOutcome = (VmDetailsOutcome) optionalOutput.getOutcome();
    if (isEmpty(vmDetailsOutcome.getIpAddress())) {
      throw new CIStageExecutionException("Ip address in initialise outcome cannot be empty");
    }

    logBackgroundStepForBackwardCompatibility(
        ciStepInfo, completeStepIdentifier, stepIdentifier, ambiance.getPlanExecutionId());
    Set<String> stepPreProcessSecrets =
        vmStepSerializer.preProcessStep(ambiance, ciStepInfo, stageInfraDetails, stepIdentifier);
    VmStepInfo vmStepInfo = vmStepSerializer.serialize(ambiance, ciStepInfo, stageInfraDetails, stepIdentifier,
        ParameterField.createValueField(Timeout.fromString(stringTimeout)), stageDetails.getRegistries(),
        stageDetails.getExecutionSource());
    Set<String> secrets = vmStepSerializer.getStepSecrets(vmStepInfo, ambiance);
    secrets.addAll(stepPreProcessSecrets);
    CIExecuteStepTaskParams params = getVmTaskParams(ambiance, vmStepInfo, secrets, stageInfraDetails, stageDetails,
        vmDetailsOutcome, runtimeId, stepIdentifier, logKey);

    List<String> eligibleToExecuteDelegateIds = new ArrayList<>();
    if (isNotEmpty(vmDetailsOutcome.getDelegateId())) {
      eligibleToExecuteDelegateIds.add(vmDetailsOutcome.getDelegateId());
    }
    String taskId = queueDelegateTask(ambiance, timeoutInMillis, accountId, ciDelegateTaskExecutor, params,
        new ArrayList<>(), eligibleToExecuteDelegateIds);

    log.info("Created VM task {} for step {}", taskId, stepIdentifier);

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(taskId)
        .addAllLogKeys(CollectionUtils.emptyIfNull(singletonList(logKey)))
        .build();
  }

  private CIExecuteStepTaskParams getVmTaskParams(Ambiance ambiance, VmStepInfo vmStepInfo, Set<String> secrets,
      StageInfraDetails stageInfraDetails, StageDetails stageDetails, VmDetailsOutcome vmDetailsOutcome,
      String runtimeId, String stepIdentifier, String logKey) {
    StageInfraDetails.Type type = stageInfraDetails.getType();
    if (type != StageInfraDetails.Type.VM && type != StageInfraDetails.Type.DLITE_VM) {
      throw new CIStageExecutionException("Invalid stage infra details type for vm or docker");
    }

    String workingDir;
    Map<String, String> volToMountPath;
    String poolId;
    // InfraInfo infraInfo;
    CIVmInitializeTaskParams.Type infraInfo;
    if (type == StageInfraDetails.Type.VM) {
      VmStageInfraDetails infraDetails = (VmStageInfraDetails) stageInfraDetails;
      poolId = infraDetails.getPoolId();
      volToMountPath = infraDetails.getVolToMountPathMap();
      workingDir = infraDetails.getWorkDir();
      infraInfo = infraDetails.getInfraInfo();
    } else {
      DliteVmStageInfraDetails infraDetails = (DliteVmStageInfraDetails) stageInfraDetails;
      poolId = infraDetails.getPoolId();
      volToMountPath = infraDetails.getVolToMountPathMap();
      workingDir = infraDetails.getWorkDir();
      infraInfo = infraDetails.getInfraInfo();
    }

    CIVmExecuteStepTaskParams ciVmExecuteStepTaskParams = CIVmExecuteStepTaskParams.builder()
                                                              .ipAddress(vmDetailsOutcome.getIpAddress())
                                                              .poolId(poolId)
                                                              .volToMountPath(volToMountPath)
                                                              .stageRuntimeId(stageDetails.getStageRuntimeID())
                                                              .stepRuntimeId(runtimeId)
                                                              .stepId(stepIdentifier)
                                                              .stepInfo(vmStepInfo)
                                                              .secrets(new ArrayList<>(secrets))
                                                              .logKey(logKey)
                                                              .workingDir(workingDir)
                                                              .infraInfo(infraInfo)
                                                              .build();
    if (type == StageInfraDetails.Type.VM) {
      return ciVmExecuteStepTaskParams;
    }

    DliteVmExecuteStepTaskParams dliteVmExecuteStepTaskParams =
        DliteVmExecuteStepTaskParams.builder()
            .executeStepRequest(vmExecuteStepUtils.convertStep(ciVmExecuteStepTaskParams).build())
            .build();
    hostedVmSecretResolver.resolve(ambiance, dliteVmExecuteStepTaskParams);
    return dliteVmExecuteStepTaskParams;
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
  public StepResponse handleAsyncResponseInternal(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // If any of the responses are in serialized format, deserialize them
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      entry.setValue(serializedResponseDataHelper.deserialize(entry.getValue()));
    }
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    log.info("Received response for step {}", stepIdentifier);

    StageInfraDetails stageInfraDetails = getStageInfra(ambiance);
    StageInfraDetails.Type stageInfraType = stageInfraDetails.getType();
    if (stageInfraType == StageInfraDetails.Type.K8) {
      return handleK8AsyncResponse(ambiance, stepParameters, responseDataMap);
    } else if (stageInfraType == StageInfraDetails.Type.VM || stageInfraType == StageInfraDetails.Type.DLITE_VM) {
      return handleVmStepResponse(stepIdentifier, responseDataMap);
    } else {
      throw new CIStageExecutionException(format("Invalid infra type: %s", stageInfraType));
    }
  }

  private StepResponse handleK8AsyncResponse(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
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

  protected void modifyStepStatus(Ambiance ambiance, StepStatus stepStatus, String stepIdentifier) {
    return;
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
      modifyStepStatus(ambiance, stepStatus, stepIdentifier);
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
        // since jexl doesn't understand - therefore we are adding a new outcome with artifact_ appended
        // Also to have backward compatibility we'll save the old outcome as an output variable.
        String artifactOutputVariableKey = "artifact-" + stepIdentifier;
        OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
            ambiance, RefObjectUtils.getSweepingOutputRefObject(artifactOutputVariableKey));
        if (!optionalSweepingOutput.isFound()) {
          executionSweepingOutputResolver.consume(ambiance, artifactOutputVariableKey,
              StepArtifactSweepingOutput.builder().stepArtifacts(stepArtifacts).build(), StepOutcomeGroup.STAGE.name());
        }

        // we found a bug CI-7115 due to which we had to change the outcome identifier from artifact_+stepId to
        // artifact_+stepGroupId+stepId. But customers might be using older expression with only step Id, hence to make
        // it backward compatible, we are saving older expression into sweepingOutput
        optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
            ambiance, RefObjectUtils.getSweepingOutputRefObject("artifact_" + stepIdentifier));
        if (!optionalSweepingOutput.isFound()) {
          executionSweepingOutputResolver.consume(ambiance, "artifact_" + stepIdentifier,
              StepArtifactSweepingOutput.builder().stepArtifacts(stepArtifacts).build(), StepOutcomeGroup.STAGE.name());
        }

        String uniqueStepIdentifier =
            CIStepGroupUtils.getUniqueStepIdentifier(ambiance.getLevelsList(), stepIdentifier);
        StepResponse.StepOutcome stepArtifactOutcomeOld =
            StepResponse.StepOutcome.builder()
                .outcome(CIStepArtifactOutcome.builder().stepArtifacts(stepArtifacts).build())
                .group(StepOutcomeGroup.STAGE.name())
                .name("artifact_" + uniqueStepIdentifier)
                .build();
        stepResponseBuilder.stepOutcome(stepArtifactOutcomeOld);
      }

      return stepResponseBuilder.status(Status.SUCCEEDED).build();
    } else if (stepStatus.getStepExecutionStatus() == StepExecutionStatus.SKIPPED) {
      return stepResponseBuilder.status(Status.SKIPPED).build();
    } else if (stepStatus.getStepExecutionStatus() == StepExecutionStatus.ABORTED) {
      return stepResponseBuilder.status(Status.ABORTED).build();
    } else {
      String maskedError = maskTransportExceptionError(stepStatus.getError());
      return stepResponseBuilder.status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .setErrorMessage(maskedError)
                           .addAllFailureTypes(EnumSet.of(FailureType.APPLICATION_FAILURE))
                           .build())
          .build();
    }
  }

  private String maskTransportExceptionError(String errorMessage) {
    final String defaultTransportExceptionMessage =
        "Communication between Container and Lite-engine seems to be broken. Please review the resources allocated to the Step";
    final String transportExceptionString = "connection error: desc = \"transport: Error while dialing dial tcp";
    if (errorMessage != null && errorMessage.contains(transportExceptionString)) {
      return defaultTransportExceptionMessage;
    } else {
      return errorMessage;
    }
  }

  protected StepArtifacts handleArtifact(ArtifactMetadata artifactMetadata, StepElementParameters stepParameters) {
    return null;
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {}

  private UnitStep serialiseStep(CIStepInfo ciStepInfo, String taskId, String logKey, String stepIdentifier,
      Integer port, String accountId, String stepName, String timeout, OSType os, Ambiance ambiance,
      StageDetails stageDetails) {
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return runStepProtobufSerializer.serializeStepWithStepParameters((RunStepInfo) ciStepInfo, port, taskId, logKey,
            stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId, stepName,
            ambiance);
      case BACKGROUND:
        return backgroundStepProtobufSerializer.serializeStepWithStepParameters(
            (BackgroundStepInfo) ciStepInfo, port, taskId, logKey, stepIdentifier, accountId, stepName);
      case PLUGIN:
        return pluginStepProtobufSerializer.serializeStepWithStepParameters((PluginStepInfo) ciStepInfo, port, taskId,
            logKey, stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId, stepName,
            stageDetails.getExecutionSource());
      case GCR:
      case DOCKER:
      case ECR:
      case ACR:
      case UPLOAD_ARTIFACTORY:
      case UPLOAD_GCS:
      case UPLOAD_S3:
      case SAVE_CACHE_GCS:
      case RESTORE_CACHE_GCS:
      case SAVE_CACHE_S3:
      case SECURITY:
      case RESTORE_CACHE_S3:
      case GIT_CLONE:
      case SSCA_ORCHESTRATION:
        return pluginCompatibleStepSerializer.serializeStepWithStepParameters((PluginCompatibleStep) ciStepInfo, port,
            taskId, logKey, stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId,
            stepName, os, ambiance);
      case RUN_TESTS:
        return runTestsStepProtobufSerializer.serializeStepWithStepParameters((RunTestsStepInfo) ciStepInfo, port,
            taskId, logKey, stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId,
            stepName);
      case CLEANUP:
      case TEST:
      case BUILD:
      case SETUP_ENV:
      case INITIALIZE_TASK:
      default:
        log.info("serialisation is not implemented");
        return null;
    }
  }

  // Todo: Merge with PluginUtil#getDelegateTaskForPluginStep when PR#47033 is merged.
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
    String taskType = CI_EXECUTE_STEP;
    boolean executeOnHarnessHostedDelegates = false;
    SerializationFormat serializationFormat = SerializationFormat.KRYO;
    if (ciExecuteStepTaskParams.getType() == CIExecuteStepTaskParams.Type.DLITE_VM) {
      taskType = TaskType.DLITE_CI_VM_EXECUTE_TASK.getDisplayName();
      serializationFormat = SerializationFormat.JSON;
      executeOnHarnessHostedDelegates = true;
    }
    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .parked(false)
                                  .taskType(taskType)
                                  .serializationFormat(serializationFormat)
                                  .parameters(new Object[] {ciExecuteStepTaskParams})
                                  .timeout(timeout)
                                  .expressionFunctorToken((int) ambiance.getExpressionFunctorToken())
                                  .build();

    Map<String, String> abstractions = buildAbstractions(ambiance, Scope.PROJECT);

    HDelegateTask task = (HDelegateTask) StepUtils.prepareDelegateTaskInput(accountId, taskData, abstractions);

    return executor.queueTask(
        abstractions, task, taskSelectors, eligibleToExecuteDelegateIds, executeOnHarnessHostedDelegates);
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

    return executor.queueTask(abstractions, task, new ArrayList<>(), new ArrayList<>(), false);
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

  private void logBackgroundStepForBackwardCompatibility(
      CIStepInfo stepInfo, String completeIdentifier, String identifier, String planExecutionId) {
    // Right now background step only takes stepGroup id upto 1 level. Logging this to check which all pipelines
    // are using the complex case of multi stepGroup level configuration for background step.
    if (stepInfo.getNonYamlInfo().getStepInfoType() == CIStepInfoType.BACKGROUND) {
      if (isNotEmpty(completeIdentifier) && isNotEmpty(identifier) && !completeIdentifier.equals(identifier)) {
        log.warn("Step identifier {} is not complete for background step. Complete identifier {}, planId {}",
            identifier, completeIdentifier, planExecutionId);
      }
    }
  }
}
