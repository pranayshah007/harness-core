/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdAsyncExecutable;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.k8s.K8sRollingRollbackBaseStepInfo.K8sRollingRollbackBaseStepInfoKeys;
import io.harness.cdng.k8s.beans.K8sRollingReleaseOutput;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.NGTimeConversionHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.instancesync.mapper.K8sPodToServiceInstanceInfoMapper;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sRollingDeployRollbackResponse;
import io.harness.delegate.task.k8s.K8sRollingRollbackDeployRequest;
import io.harness.delegate.task.k8s.K8sRollingRollbackDeployRequest.K8sRollingRollbackDeployRequestBuilder;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@OwnedBy(CDP)
public class K8sRollingRollbackStepV2 extends CdAsyncExecutable<K8sDeployResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_ROLLBACK_ROLLING_V2.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String K8S_DEPLOYMENT_ROLLING_ROLLBACK_COMMAND_NAME = "Rolling Deployment Rollback";

  @Inject K8sStepHelper k8sStepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private OutcomeService outcomeService;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private StepHelper stepHelper;
  @Inject private AccountService accountService;
  @Inject private CDExpressionResolver cdExpressionResolver;

  @Override
  public Class<StepBaseParameters> getStepParametersClass() {
    return StepBaseParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {
    // Noop
  }

  @Override
  public AsyncExecutableResponse executeAsync(Ambiance ambiance, StepBaseParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    String logKey = getLogKey(ambiance);
    K8sRollingRollbackStepParameters rollingRollbackStepParameters =
        (K8sRollingRollbackStepParameters) stepParameters.getSpec();
    List<String> callbackIds = new ArrayList<>();
    if (EmptyPredicate.isEmpty(rollingRollbackStepParameters.getRollingStepFqn())) {
      return AsyncExecutableResponse.newBuilder().build();
    }

    OptionalSweepingOutput k8sRollingReleaseOptionalOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            rollingRollbackStepParameters.getRollingStepFqn() + "." + K8sRollingReleaseOutput.OUTPUT_NAME));
    OptionalSweepingOutput k8sRollingOptionalOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            rollingRollbackStepParameters.getRollingStepFqn() + "." + OutcomeExpressionConstants.K8S_ROLL_OUT));

    if (!k8sRollingReleaseOptionalOutput.isFound()) {
      return AsyncExecutableResponse.newBuilder().build();
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    K8sRollingRollbackStepParameters k8sRollingRollbackStepParameters =
        (K8sRollingRollbackStepParameters) stepParameters.getSpec();
    boolean pruningEnabled =
        CDStepHelper.getParameterFieldBooleanValue(k8sRollingRollbackStepParameters.getPruningEnabled(),
            K8sRollingRollbackBaseStepInfoKeys.pruningEnabled, stepParameters);
    K8sRollingRollbackDeployRequestBuilder rollbackRequestBuilder = K8sRollingRollbackDeployRequest.builder();
    InfrastructureOutcome infrastructure = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    Map<String, String> k8sCommandFlag =
        k8sStepHelper.getDelegateK8sCommandFlag(k8sRollingRollbackStepParameters.getCommandFlags(), ambiance);
    rollbackRequestBuilder.k8sCommandFlags(k8sCommandFlag);
    if (k8sRollingOptionalOutput.isFound()) {
      K8sRollingOutcome k8sRollingOutcome = (K8sRollingOutcome) k8sRollingOptionalOutput.getOutput();
      rollbackRequestBuilder.releaseName(k8sRollingOutcome.getReleaseName())
          .releaseNumber(k8sRollingOutcome.getReleaseNumber())
          .prunedResourceIds(
              k8sStepHelper.getPrunedResourcesIds(pruningEnabled, k8sRollingOutcome.getPrunedResourceIds()));
    } else {
      K8sRollingReleaseOutput releaseOutput = (K8sRollingReleaseOutput) k8sRollingReleaseOptionalOutput.getOutput();
      rollbackRequestBuilder.releaseName(releaseOutput.getName());
    }

    ManifestsOutcome manifestsOutcome = k8sStepHelper.resolveManifestsOutcome(ambiance);

    // render manifests outcome
    cdExpressionResolver.updateExpressions(ambiance, manifestsOutcome);
    ManifestOutcome k8sManifestOutcome = k8sStepHelper.getK8sSupportedManifestOutcome(manifestsOutcome.values());

    rollbackRequestBuilder.commandName(K8S_DEPLOYMENT_ROLLING_ROLLBACK_COMMAND_NAME)
        .taskType(K8sTaskType.DEPLOYMENT_ROLLING_ROLLBACK)
        .timeoutIntervalInMin(NGTimeConversionHelper.convertTimeStringToMinutes(stepParameters.getTimeout().getValue()))
        .k8sInfraDelegateConfig(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
        .useNewKubectlVersion(cdStepHelper.isUseNewKubectlVersion(accountId))
        .pruningEnabled(pruningEnabled)
        .useDeclarativeRollback(k8sStepHelper.isDeclarativeRollbackEnabled(k8sManifestOutcome))
        .build();
    String taskId = k8sStepHelper.queueTask(stepParameters, rollbackRequestBuilder.build(), ambiance);
    if (taskId != null) {
      callbackIds.add(taskId);
    }
    return AsyncExecutableResponse.newBuilder()
        .addAllCallbackIds(callbackIds)
        .addAllLogKeys(CollectionUtils.emptyIfNull(Collections.singletonList(logKey)))
        .build();
  }

  @Override
  public StepResponse handleAsyncResponse(
      Ambiance ambiance, StepBaseParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    StepResponse stepResponse = null;
    try {
      if (responseDataMap.isEmpty()) {
        return StepResponse.builder().status(Status.SKIPPED).build();
      }
      List<K8sDeployResponse> executionResponses = responseDataMap.values()
                                                       .stream()
                                                       .filter(K8sDeployResponse.class ::isInstance)
                                                       .map(K8sDeployResponse.class ::cast)
                                                       .collect(Collectors.toList());
      K8sDeployResponse executionResponse = executionResponses.get(0);
      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(executionResponse.getCommandUnitsProgress().getUnitProgresses());

      stepResponse = generateStepResponse(ambiance, executionResponse, stepResponseBuilder);
    } finally {
      String accountName = accountService.getAccount(AmbianceUtils.getAccountId(ambiance)).getName();
      stepHelper.sendRollbackTelemetryEvent(
          ambiance, stepResponse == null ? Status.FAILED : stepResponse.getStatus(), accountName);
    }

    return stepResponse;
  }

  private StepResponse generateStepResponse(
      Ambiance ambiance, K8sDeployResponse executionResponse, StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse = null;

    if (executionResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse =
          stepResponseBuilder.status(Status.FAILED)
              .failureInfo(
                  FailureInfo.newBuilder().setErrorMessage(K8sStepHelper.getErrorMessage(executionResponse)).build())
              .build();
    } else {
      final K8sRollingDeployRollbackResponse response =
          (K8sRollingDeployRollbackResponse) executionResponse.getK8sNGTaskResponse();
      K8sRollingRollbackOutcome k8sRollingRollbackOutcome =
          K8sRollingRollbackOutcome.builder().recreatedResourceIds(response.getRecreatedResourceIds()).build();

      StepOutcome stepOutcome = instanceInfoService.saveServerInstancesIntoSweepingOutput(
          ambiance, K8sPodToServiceInstanceInfoMapper.toServerInstanceInfoList(response.getK8sPodList(), null));

      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED)
                         .stepOutcome(stepOutcome)
                         .stepOutcome(StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(k8sRollingRollbackOutcome)
                                          .build())
                         .build();
    }

    return stepResponse;
  }

  private String getLogKey(Ambiance ambiance) {
    LinkedHashMap<String, String> logAbstractions = StepUtils.generateLogAbstractions(ambiance);
    return LogStreamingHelper.generateLogBaseKey(logAbstractions);
  }
}
