/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.states;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODE_BASE_CONNECTOR_REF;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.steps.StepUtils.buildAbstractions;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
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
import io.harness.beans.sweepingoutputs.DliteVmStageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
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
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.ci.CIExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmExecuteStepTaskParams;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.delegate.task.HDelegateTask;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.delegate.task.stepstatus.artifact.Artifact;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.encryption.Scope;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.plugin.CommonAbstractStepExecutable;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.steps.StepUtils;
import io.harness.tasks.ResponseData;
import io.harness.vm.VmExecuteStepUtils;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CI)
public abstract class AbstractStepExecutable extends CommonAbstractStepExecutable {
  public static final String CI_EXECUTE_STEP = "CI_EXECUTE_STEP";
  @Inject private RunStepProtobufSerializer runStepProtobufSerializer;
  @Inject private BackgroundStepProtobufSerializer backgroundStepProtobufSerializer;
  @Inject private PluginStepProtobufSerializer pluginStepProtobufSerializer;
  @Inject private RunTestsStepProtobufSerializer runTestsStepProtobufSerializer;
  @Inject private PluginCompatibleStepSerializer pluginCompatibleStepSerializer;
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

  public List<TaskSelector> fetchDelegateSelector(Ambiance ambiance) {
    return connectorUtils.fetchDelegateSelector(ambiance, executionSweepingOutputResolver);
  }

  public OSType getK8OS(Infrastructure infrastructure) {
    return IntegrationStageUtils.getK8OS(infrastructure);
  }

  @Override
  public String getCompleteStepIdentifier(Ambiance ambiance, String stepIdentifier) {
    return CIStepGroupUtils.getUniqueStepIdentifier(ambiance.getLevelsList(), stepIdentifier);
  }

  @Override
  public AsyncExecutableResponse executeVmAsyncAfterRbac(Ambiance ambiance, String completeStepIdentifier,
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
        stageDetails.getExecutionSource(), vmDetailsOutcome.getDelegateId());
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

  @Override
  public void resolveGitAppFunctor(Ambiance ambiance, CIStepInfo ciStepInfo) {
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
  public StepResponse handleVmStepResponse(Ambiance ambiance, String stepIdentifier,
      StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
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

      ArtifactMetadata artifactMetadata = null;
      if (taskResponse.getArtifact() != null) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
          Artifact artifact = objectMapper.readValue(taskResponse.getArtifact(), Artifact.class);
          artifactMetadata = artifact.toArtifactMetadata();
        } catch (Exception e) {
          log.error("Unable to parse artifact data", e);
        }
      }

      StepArtifacts stepArtifacts = handleArtifactForVm(artifactMetadata, stepParameters, ambiance);
      buildArtifacts(ambiance, stepIdentifier, stepArtifacts, stepResponseBuilder);
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

  @Override
  public String getUniqueStepIdentifier(Ambiance ambiance, String stepIdentifier) {
    return CIStepGroupUtils.getUniqueStepIdentifier(ambiance.getLevelsList(), stepIdentifier);
  }

  protected StepArtifacts handleArtifact(ArtifactMetadata artifactMetadata, StepElementParameters stepParameters) {
    return null;
  }

  protected StepArtifacts handleArtifactForVm(
      ArtifactMetadata artifactMetadata, StepElementParameters stepParameters, Ambiance ambiance) {
    return handleArtifact(artifactMetadata, stepParameters);
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, AsyncExecutableResponse executableResponse) {}

  @Override
  public UnitStep serialiseStep(CIStepInfo ciStepInfo, String taskId, String logKey, String stepIdentifier,
      Integer port, String accountId, String stepName, String timeout, OSType os, Ambiance ambiance,
      StageDetails stageDetails) {
    switch (ciStepInfo.getNonYamlInfo().getStepInfoType()) {
      case RUN:
        return runStepProtobufSerializer.serializeStepWithStepParameters((RunStepInfo) ciStepInfo, port, taskId, logKey,
            stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId, stepName,
            ambiance);
      case BACKGROUND:
        return backgroundStepProtobufSerializer.serializeStepWithStepParameters(
            (BackgroundStepInfo) ciStepInfo, port, taskId, logKey, stepIdentifier, accountId, stepName, ambiance);
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
      case SSCA_ENFORCEMENT:
        return pluginCompatibleStepSerializer.serializeStepWithStepParameters((PluginCompatibleStep) ciStepInfo, port,
            taskId, logKey, stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId,
            stepName, os, ambiance);
      case RUN_TESTS:
        return runTestsStepProtobufSerializer.serializeStepWithStepParameters((RunTestsStepInfo) ciStepInfo, port,
            taskId, logKey, stepIdentifier, ParameterField.createValueField(Timeout.fromString(timeout)), accountId,
            stepName, ambiance);
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

  @Override
  protected boolean getIsLocal(Ambiance ambiance) {
    return ciExecutionServiceConfig.isLocal();
  }

  @Override
  protected String getDelegateSvcEndpoint(Ambiance ambiance) {
    return ciExecutionServiceConfig.getDelegateServiceEndpointVariableValue();
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

    return executor.queueTask(abstractions, task, taskSelectors, eligibleToExecuteDelegateIds,
        executeOnHarnessHostedDelegates, ambiance.getStageExecutionId());
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

  private void buildArtifacts(
      Ambiance ambiance, String stepIdentifier, StepArtifacts stepArtifacts, StepResponseBuilder stepResponseBuilder) {
    if (stepArtifacts != null) {
      String uniqueStepIdentifier = CIStepGroupUtils.getUniqueStepIdentifier(ambiance.getLevelsList(), stepIdentifier);
      StepResponse.StepOutcome stepArtifactOutcome =
          StepResponse.StepOutcome.builder()
              .outcome(CIStepArtifactOutcome.builder().stepArtifacts(stepArtifacts).build())
              .group(StepOutcomeGroup.STAGE.name())
              .name("artifact_" + uniqueStepIdentifier)
              .build();
      stepResponseBuilder.stepOutcome(stepArtifactOutcome);
    }
  }
}
