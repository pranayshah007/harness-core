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
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.k8s.K8sRollingRollbackBaseStepInfo.K8sRollingRollbackBaseStepInfoKeys;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.k8s.beans.K8sRollingReleaseOutput;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.NGTimeConversionHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.instancesync.mapper.K8sPodToServiceInstanceInfoMapper;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sRollingDeployRollbackResponse;
import io.harness.delegate.task.k8s.K8sRollingRollbackDeployRequest;
import io.harness.delegate.task.k8s.K8sRollingRollbackDeployRequest.K8sRollingRollbackDeployRequestBuilder;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(CDP)
public class K8sRollingRollbackStep extends CdTaskExecutable<K8sDeployResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_ROLLBACK_ROLLING.getYamlType())
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

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Noop
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    K8sRollingRollbackStepParameters rollingRollbackStepParameters =
        (K8sRollingRollbackStepParameters) stepElementParameters.getSpec();
    if (EmptyPredicate.isEmpty(rollingRollbackStepParameters.getRollingStepFqn())) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("K8s Rollout Deploy step was not executed. Skipping rollback.")
                                  .build())
          .build();
    }

    OptionalSweepingOutput k8sRollingReleaseOptionalOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            rollingRollbackStepParameters.getRollingStepFqn() + "." + K8sRollingReleaseOutput.OUTPUT_NAME));
    OptionalSweepingOutput k8sRollingOptionalOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(
            rollingRollbackStepParameters.getRollingStepFqn() + "." + OutcomeExpressionConstants.K8S_ROLL_OUT));

    if (!k8sRollingReleaseOptionalOutput.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("K8s Rollout Deploy step was not executed. Skipping rollback.")
                                  .build())
          .build();
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    K8sRollingRollbackStepParameters k8sRollingRollbackStepParameters =
        (K8sRollingRollbackStepParameters) stepElementParameters.getSpec();
    boolean pruningEnabled =
        CDStepHelper.getParameterFieldBooleanValue(k8sRollingRollbackStepParameters.getPruningEnabled(),
            K8sRollingRollbackBaseStepInfoKeys.pruningEnabled, stepElementParameters);
    K8sRollingRollbackDeployRequestBuilder rollbackRequestBuilder = K8sRollingRollbackDeployRequest.builder();
    InfrastructureOutcome infrastructure = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    Map<String, String> k8sCommandFlag =
        k8sStepHelper.getDelegateK8sCommandFlag(k8sRollingRollbackStepParameters.getCommandFlags());
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
    ManifestOutcome k8sManifestOutcome = k8sStepHelper.getK8sSupportedManifestOutcome(manifestsOutcome.values());

    rollbackRequestBuilder.commandName(K8S_DEPLOYMENT_ROLLING_ROLLBACK_COMMAND_NAME)
        .taskType(K8sTaskType.DEPLOYMENT_ROLLING_ROLLBACK)
        .timeoutIntervalInMin(
            NGTimeConversionHelper.convertTimeStringToMinutes(stepElementParameters.getTimeout().getValue()))
        .k8sInfraDelegateConfig(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
        .useNewKubectlVersion(cdStepHelper.isUseNewKubectlVersion(accountId))
        .pruningEnabled(pruningEnabled)
        .useDeclarativeRollback(k8sStepHelper.isDeclarativeRollbackEnabled(k8sManifestOutcome))
        .build();

    return k8sStepHelper
        .queueK8sTask(stepElementParameters, rollbackRequestBuilder.build(), ambiance,
            K8sExecutionPassThroughData.builder().infrastructure(infrastructure).build())
        .getTaskRequest();
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<K8sDeployResponse> responseSupplier)
      throws Exception {
    StepResponse stepResponse = null;
    try {
      K8sDeployResponse executionResponse = responseSupplier.get();
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
          ambiance, K8sPodToServiceInstanceInfoMapper.toServerInstanceInfoList(response.getK8sPodList()));

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
}
