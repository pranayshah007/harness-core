/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sSwapServiceSelectorsRequest;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.exception.InvalidRequestException;
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
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
public class K8sBGSwapServicesStep extends CdTaskExecutable<K8sDeployResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_BG_SWAP_SERVICES.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String K8S_BG_SWAP_SERVICES_COMMAND_NAME = "Blue/Green Swap Services";
  public static final String SKIP_BG_SWAP_SERVICES_STEP_EXECUTION =
      "Services were not swapped in the forward phase. Skipping swapping in rollback.";
  public static final String BG_STEP_MISSING_ERROR =
      "Stage Deployment (Blue Green Deploy) is not configured. \nHint: Add Stage Deployment in the execution step.";
  public static final String SKIP_BG_SWAP_WHEN_STAGE_DEPLOYMENT_SKIPPED =
      "Stage Deployment (Blue Green Deploy) was skipped because given manifest matches existing deployed manifest.";
  @Inject private K8sStepHelper k8sStepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private OutcomeService outcomeService;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Noop
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    K8sBGSwapServicesStepParameters k8sBGSwapServicesStepParameters =
        (K8sBGSwapServicesStepParameters) stepElementParameters.getSpec();
    String bgStepFqn = k8sBGSwapServicesStepParameters.getBlueGreenStepFqn();
    String bgSwapServicesFqn = k8sBGSwapServicesStepParameters.getBlueGreenSwapServicesStepFqn();
    if (EmptyPredicate.isNotEmpty(bgSwapServicesFqn) || EmptyPredicate.isNotEmpty(bgStepFqn)) {
      OptionalOutcome optionalOutcome = outcomeService.resolveOptional(ambiance,
          RefObjectUtils.getOutcomeRefObject(
              bgSwapServicesFqn + "." + OutcomeExpressionConstants.K8S_BG_SWAP_SERVICES_OUTCOME));

      boolean stepInRollbackSection = StepUtils.isStepInRollbackSection(ambiance);
      if (stepInRollbackSection && !optionalOutcome.isFound()) {
        return TaskRequest.newBuilder()
            .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(SKIP_BG_SWAP_SERVICES_STEP_EXECUTION).build())
            .build();
      }
    }

    if (EmptyPredicate.isEmpty(bgStepFqn)) {
      throw new InvalidRequestException(BG_STEP_MISSING_ERROR, USER);
    }

    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(bgStepFqn + "." + OutcomeExpressionConstants.K8S_BLUE_GREEN_OUTCOME));
    if (!optionalSweepingOutput.isFound()) {
      throw new InvalidRequestException(BG_STEP_MISSING_ERROR, USER);
    }
    K8sBlueGreenOutcome k8sBlueGreenOutcome = (K8sBlueGreenOutcome) optionalSweepingOutput.getOutput();
    Boolean stageDeploymentSkipped = k8sBlueGreenOutcome.getStageDeploymentSkipped() != null
        ? k8sBlueGreenOutcome.getStageDeploymentSkipped()
        : Boolean.FALSE;
    if (stageDeploymentSkipped) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage(SKIP_BG_SWAP_WHEN_STAGE_DEPLOYMENT_SKIPPED).build())
          .build();
    }
    InfrastructureOutcome infrastructure = cdStepHelper.getInfrastructureOutcome(ambiance);
    String releaseName = cdStepHelper.getReleaseName(ambiance, infrastructure);
    K8sSwapServiceSelectorsRequest swapServiceSelectorsRequest =
        K8sSwapServiceSelectorsRequest.builder()
            .service1(k8sBlueGreenOutcome.getPrimaryServiceName())
            .service2(k8sBlueGreenOutcome.getStageServiceName())
            .k8sInfraDelegateConfig(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
            .commandName(K8S_BG_SWAP_SERVICES_COMMAND_NAME)
            .taskType(K8sTaskType.SWAP_SERVICE_SELECTORS)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .useNewKubectlVersion(cdStepHelper.isUseNewKubectlVersion(AmbianceUtils.getAccountId(ambiance)))
            .useDeclarativeRollback(k8sStepHelper.isDeclarativeRollbackEnabled(ambiance))
            .releaseName(cdStepHelper.getReleaseName(ambiance, infrastructure))
            .build();

    k8sStepHelper.publishReleaseNameStepDetails(ambiance, releaseName);
    return k8sStepHelper
        .queueK8sTask(stepElementParameters, swapServiceSelectorsRequest, ambiance,
            K8sExecutionPassThroughData.builder().infrastructure(infrastructure).build())
        .getTaskRequest();
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<K8sDeployResponse> responseSupplier)
      throws Exception {
    K8sDeployResponse executionResponse = responseSupplier.get();
    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(executionResponse.getCommandUnitsProgress().getUnitProgresses());

    if (executionResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return stepResponseBuilder.status(Status.FAILED)
          .failureInfo(
              FailureInfo.newBuilder().setErrorMessage(K8sStepHelper.getErrorMessage(executionResponse)).build())
          .build();
    }

    // Save BGSwapServices Outcome only if you are in forward phase. We use this in rollback to check if we need to
    // run this step or not.
    if (!StepUtils.isStepInRollbackSection(ambiance)) {
      K8sBGSwapServicesOutcome bgSwapServicesOutcome = K8sBGSwapServicesOutcome.builder().build();
      stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.K8S_BG_SWAP_SERVICES_OUTCOME)
                                          .outcome(bgSwapServicesOutcome)
                                          .group(StepOutcomeGroup.STEP.name())
                                          .build());
    }

    return stepResponseBuilder.status(Status.SUCCEEDED).build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
