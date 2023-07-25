/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.CIExecutionConstants.UNDERSCORE_SEPARATOR;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.TMP_PATH;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;
import static io.harness.steps.StepUtils.buildAbstractions;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

import io.harness.beans.FeatureName;
import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfo;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.CIStepOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.steps.output.CIStageOutput;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.StepArtifactSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.executable.CiAsyncExecutable;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIExecuteStepTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8ExecuteStepTaskParams;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.task.HDelegateTask;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.encryption.Scope;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.ngexception.CILiteEngineException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plugin.service.BasePluginCompatibleSerializer;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.AmbianceUtils;
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
import io.harness.product.ci.engine.proto.PluginStep;
import io.harness.product.ci.engine.proto.RunStep;
import io.harness.product.ci.engine.proto.RunTestsStep;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.repositories.CIStageOutputRepository;
import io.harness.steps.StepUtils;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.inject.Inject;
import io.fabric8.utils.Strings;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class CommonAbstractStepExecutable extends CiAsyncExecutable {
  public static final String CI_EXECUTE_STEP = "CI_EXECUTE_STEP";

  @Inject private CIDelegateTaskExecutor ciDelegateTaskExecutor;
  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;
  @Inject private OutcomeService outcomeService;

  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Inject private ExceptionManager exceptionManager;

  public static final long bufferTimeMillis =
      5 * 1000; // These additional 5 seconds are approx time spent on creating delegate ask and receiving response

  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;

  @Inject private BasePluginCompatibleSerializer basePluginCompatibleSerializer;
  @Inject protected CIStageOutputRepository ciStageOutputRepository;
  @Inject protected CIFeatureFlagService featureFlagService;

  @Override
  public AsyncExecutableResponse executeAsyncAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    String runtimeId = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    String logKey = getLogKey(ambiance);
    String stepGroupIdentifier = AmbianceUtils.obtainStepGroupIdentifier(ambiance);
    String stepIdentifier = AmbianceUtils.obtainStepIdentifier(ambiance);
    String completeStepIdentifier = getCompleteStepIdentifier(ambiance, stepIdentifier);
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

  private StageInfraDetails getStageInfra(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage infra details sweeping output cannot be empty");
    }

    return (StageInfraDetails) optionalSweepingOutput.getOutput();
  }

  public abstract AsyncExecutableResponse executeVmAsyncAfterRbac(Ambiance ambiance, String completeStepIdentifier,
      String stepIdentifier, String runtimeId, CIStepInfo ciStepInfo, String accountId, String logKey,
      long timeoutInMillis, String stringTimeout, StageInfraDetails stageInfraDetails, StageDetails stageDetails);

  private String getLogKey(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance);
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }

  public abstract void resolveGitAppFunctor(Ambiance ambiance, CIStepInfo ciStepInfo);

  private AsyncExecutableResponse executeK8AsyncAfterRbac(Ambiance ambiance, String stepIdentifier, String runtimeId,
      CIStepInfo ciStepInfo, String stepParametersName, String accountId, String logKey, long timeoutInMillis,
      String stringTimeout, K8StageInfraDetails k8StageInfraDetails, StageDetails stageDetails) {
    String parkedTaskId = ciDelegateTaskExecutor.queueParkedDelegateTask(ambiance, timeoutInMillis, accountId);
    OSType os = getK8OS(k8StageInfraDetails.getInfrastructure());
    UnitStep unitStep = serialiseStepWrapper(ciStepInfo, parkedTaskId, logKey, stepIdentifier,
        getPort(ambiance, stepIdentifier), accountId, stepParametersName, stringTimeout, os, ambiance, stageDetails);
    unitStep = injectOutputVarsAsEnvVars(unitStep, accountId, ambiance.getStageExecutionId());
    LiteEnginePodDetailsOutcome liteEnginePodDetailsOutcome = (LiteEnginePodDetailsOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(LiteEnginePodDetailsOutcome.POD_DETAILS_OUTCOME));
    String ip = "";
    if (liteEnginePodDetailsOutcome == null) {
      log.info("Failed to get pod local ipAddress details");
    } else {
      ip = liteEnginePodDetailsOutcome.getIpAddress();
    }
    String liteEngineTaskId =
        queueK8DelegateTask(ambiance, timeoutInMillis, accountId, ciDelegateTaskExecutor, unitStep, runtimeId, ip);

    log.info("Created parked task {} and lite engine task {} for  step {} with ip {}", parkedTaskId, liteEngineTaskId,
        stepIdentifier, ip);

    return AsyncExecutableResponse.newBuilder()
        .addCallbackIds(parkedTaskId)
        .addCallbackIds(liteEngineTaskId)
        .addAllLogKeys(CollectionUtils.emptyIfNull(singletonList(logKey)))
        .build();
  }

  public UnitStep injectOutputVarsAsEnvVars(UnitStep stepInfo, String accountId, String stageExecutionId) {
    if (!featureFlagService.isEnabled(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV, accountId)) {
      return stepInfo;
    }

    Optional<CIStageOutput> ciStageOutputResponse =
        ciStageOutputRepository.findFirstByStageExecutionId(stageExecutionId);
    if (ciStageOutputResponse.isPresent()) {
      CIStageOutput ciStageOutput = ciStageOutputResponse.get();
      Map<String, String> outputs = ciStageOutput.getOutputs();
      if (outputs.isEmpty()) {
        return stepInfo;
      }
      if (stepInfo.hasRun()) {
        outputs.putAll(stepInfo.getRun().getEnvironmentMap());
        RunStep runStep = RunStep.newBuilder(stepInfo.getRun()).putAllEnvironment(outputs).build();
        return UnitStep.newBuilder(stepInfo).setRun(runStep).build();
      } else if (stepInfo.hasPlugin()) {
        outputs.putAll(stepInfo.getPlugin().getEnvironmentMap());
        PluginStep pluginStep = PluginStep.newBuilder(stepInfo.getPlugin()).putAllEnvironment(outputs).build();
        return UnitStep.newBuilder(stepInfo).setPlugin(pluginStep).build();
      } else if (stepInfo.hasRunTests()) {
        outputs.putAll(stepInfo.getRunTests().getEnvironmentMap());
        RunTestsStep runTestsStep = RunTestsStep.newBuilder(stepInfo.getRunTests()).putAllEnvironment(outputs).build();
        return UnitStep.newBuilder(stepInfo).setRunTests(runTestsStep).build();
      }
    }
    return stepInfo;
  }

  public void populateCIStageOutputs(Map<String, String> outputVariables, String accountId, String stageExecutionId) {
    if (!featureFlagService.isEnabled(FeatureName.CI_OUTPUT_VARIABLES_AS_ENV, accountId)) {
      return;
    }
    if (Objects.isNull(outputVariables) || outputVariables.isEmpty()) {
      return;
    }

    Optional<CIStageOutput> ciStageOutputResponse =
        ciStageOutputRepository.findFirstByStageExecutionId(stageExecutionId);
    CIStageOutput ciStageOutput =
        CIStageOutput.builder().outputs(new HashMap<String, String>()).stageExecutionId(stageExecutionId).build();
    if (ciStageOutputResponse.isPresent()) {
      ciStageOutput = ciStageOutputResponse.get();
    }
    Map<String, String> outputs = ciStageOutput.getOutputs();
    outputVariables.entrySet().stream().forEach(entry -> outputs.put(entry.getKey(), entry.getValue()));
    ciStageOutputRepository.save(ciStageOutput);
  }

  private UnitStep serialiseStepWrapper(CIStepInfo ciStepInfo, String taskId, String logKey, String stepIdentifier,
      Integer port, String accountId, String stepName, String timeout, OSType os, Ambiance ambiance,
      StageDetails stageDetails) {
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case GIT_CLONE:
        return basePluginCompatibleSerializer.serializeStepWithStepParameters((PluginCompatibleStep) ciStepInfo, port,
            taskId, logKey, stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId,
            stepName, os, ambiance);
      default:
        return serialiseStep(
            ciStepInfo, taskId, logKey, stepIdentifier, port, accountId, stepName, timeout, os, ambiance, stageDetails);
    }
  }

  public abstract UnitStep serialiseStep(CIStepInfo ciStepInfo, String taskId, String logKey, String stepIdentifier,
      Integer port, String accountId, String stepName, String timeout, OSType os, Ambiance ambiance,
      StageDetails stageDetails);

  public Integer getPort(Ambiance ambiance, String stepIdentifier) {
    // Ports are assigned in lite engine step
    ContainerPortDetails containerPortDetails = (ContainerPortDetails) executionSweepingOutputResolver.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(PORT_DETAILS));

    List<Integer> ports = containerPortDetails.getPortDetails().get(stepIdentifier);

    if (ports.size() != 1) {
      throw new CIStageExecutionException(format("Step [%s] should map to single port", stepIdentifier));
    }

    return ports.get(0);
  }
  public abstract String getCompleteStepIdentifier(Ambiance ambiance, String stepIdentifier);

  public abstract OSType getK8OS(Infrastructure infrastructure);

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
      return handleVmStepResponse(ambiance, stepIdentifier, stepParameters, responseDataMap);
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

  private StepStatusTaskResponseData filterK8StepResponse(Map<String, ResponseData> responseDataMap) {
    // Filter final response from step
    return responseDataMap.entrySet()
        .stream()
        .filter(entry -> entry.getValue() instanceof StepStatusTaskResponseData)
        .findFirst()
        .map(obj -> (StepStatusTaskResponseData) obj.getValue())
        .orElse(null);
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

    if (shouldPublishArtifact(stepStatus)) {
      publishArtifact(ambiance, stepParameters, stepIdentifier, stepStatus, stepResponseBuilder);
    }
    if (stepStatus.getStepExecutionStatus() == StepExecutionStatus.SUCCESS) {
      if (stepStatus.getOutput() != null) {
        populateCIStageOutputs(((StepMapOutput) stepStatus.getOutput()).getMap(), AmbianceUtils.getAccountId(ambiance),
            ambiance.getStageExecutionId());
        StepResponse.StepOutcome stepOutcome =
            StepResponse.StepOutcome.builder()
                .outcome(
                    CIStepOutcome.builder().outputVariables(((StepMapOutput) stepStatus.getOutput()).getMap()).build())
                .name("output")
                .build();
        stepResponseBuilder.stepOutcome(stepOutcome);
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

  private void publishArtifact(Ambiance ambiance, StepElementParameters stepParameters, String stepIdentifier,
      StepStatus stepStatus, StepResponseBuilder stepResponseBuilder) {
    modifyStepStatus(ambiance, stepStatus, stepIdentifier);

    StepArtifacts stepArtifacts = handleArtifact(stepStatus.getArtifactMetadata(), stepParameters);
    if (stepArtifacts != null) {
      // since jexl doesn't understand - therefore we are adding a new outcome with artifact_ appended
      // Also to have backward compatibility we'll save the old outcome as an output variable.
      String artifactOutputVariableKey = "artifact-" + stepIdentifier;
      OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(artifactOutputVariableKey));
      if (!optionalSweepingOutput.isFound()) {
        try {
          executionSweepingOutputResolver.consume(ambiance, artifactOutputVariableKey,
              StepArtifactSweepingOutput.builder().stepArtifacts(stepArtifacts).build(), StepOutcomeGroup.STAGE.name());
        } catch (Exception e) {
          log.error("Error while consuming artifact sweeping output", e);
        }
      }

      // we found a bug CI-7115 due to which we had to change the outcome identifier from artifact_+stepId to
      // artifact_+stepGroupId+stepId. But customers might be using older expression with only step Id, hence to make
      // it backward compatible, we are saving older expression into sweepingOutput
      optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject("artifact_" + stepIdentifier));
      if (!optionalSweepingOutput.isFound()) {
        try {
          executionSweepingOutputResolver.consume(ambiance, "artifact_" + stepIdentifier,
              StepArtifactSweepingOutput.builder().stepArtifacts(stepArtifacts).build(), StepOutcomeGroup.STAGE.name());
        } catch (Exception e) {
          log.error("Error while consuming artifact sweeping output", e);
        }
      }

      String uniqueStepIdentifier = getUniqueStepIdentifier(ambiance, stepIdentifier);
      StepResponse.StepOutcome stepArtifactOutcomeOld =
          StepResponse.StepOutcome.builder()
              .outcome(CIStepArtifactOutcome.builder().stepArtifacts(stepArtifacts).build())
              .group(StepOutcomeGroup.STAGE.name())
              .name("artifact_" + uniqueStepIdentifier)
              .build();
      stepResponseBuilder.stepOutcome(stepArtifactOutcomeOld);
    }
  }

  public String queueK8DelegateTask(Ambiance ambiance, long timeout, String accountId, CIDelegateTaskExecutor executor,
      UnitStep unitStep, String executionId, String ip) {
    ExecuteStepRequest executeStepRequest =
        ExecuteStepRequest.newBuilder().setExecutionId(executionId).setStep(unitStep).setTmpFilePath(TMP_PATH).build();
    CIK8ExecuteStepTaskParams params = CIK8ExecuteStepTaskParams.builder()
                                           .ip(ip)
                                           .port(LITE_ENGINE_PORT)
                                           .serializedStep(executeStepRequest.toByteArray())
                                           .isLocal(getIsLocal(ambiance))
                                           .delegateSvcEndpoint(getDelegateSvcEndpoint(ambiance))
                                           .build();
    List<TaskSelector> taskSelectors = fetchDelegateSelector(ambiance);
    return queueDelegateTask(ambiance, timeout, accountId, executor, params,
        taskSelectors.stream().map(TaskSelector::getSelector).collect(Collectors.toList()), new ArrayList<>());
  }

  protected abstract String getDelegateSvcEndpoint(Ambiance ambiance);

  protected abstract boolean getIsLocal(Ambiance ambiance);

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

    return executor.queueTask(abstractions, task, taskSelectors, eligibleToExecuteDelegateIds,
        executeOnHarnessHostedDelegates, ambiance.getStageExecutionId());
  }

  protected void modifyStepStatus(Ambiance ambiance, StepStatus stepStatus, String stepIdentifier) {
    // Do Nothing
  }

  protected boolean shouldPublishArtifact(StepStatus stepStatus) {
    return stepStatus.getStepExecutionStatus() == StepExecutionStatus.SUCCESS;
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

  public abstract List<TaskSelector> fetchDelegateSelector(Ambiance ambiance);

  public abstract StepResponse handleVmStepResponse(Ambiance ambiance, String stepIdentifier,
      StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap);

  public abstract String getUniqueStepIdentifier(Ambiance ambiance, String stepIdentifier);
}
