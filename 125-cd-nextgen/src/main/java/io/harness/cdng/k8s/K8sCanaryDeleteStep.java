/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.k8s.beans.K8sCanaryExecutionOutput;
import io.harness.cdng.k8s.beans.K8sExecutionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.common.NGTimeConversionHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.k8s.K8sCanaryDeleteRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CDP)
public class K8sCanaryDeleteStep extends CdTaskExecutable<K8sDeployResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.K8S_CANARY_DELETE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String K8S_CANARY_DELETE_COMMAND_NAME = "Canary Delete";
  public static final String SKIP_K8S_CANARY_DELETE_STEP_EXECUTION =
      "No canary workload was deployed in the forward phase. Skipping delete canary workload in rollback.";
  public static final String K8S_CANARY_DELETE_ALREADY_DELETED =
      "Canary workload has already been deleted. Skipping delete canary workload in rollback.";
  public static final String K8S_CANARY_STEP_MISSING = "Canary Deploy step is not configured.";

  @Inject private K8sStepHelper k8sStepHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Noop
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    K8sCanaryDeleteStepParameters k8sCanaryDeleteStepParameters =
        (K8sCanaryDeleteStepParameters) stepElementParameters.getSpec();
    String canaryStepFqn = k8sCanaryDeleteStepParameters.getCanaryStepFqn();
    InfrastructureOutcome infrastructure = cdStepHelper.getInfrastructureOutcome(ambiance);
    String releaseName = cdStepHelper.getReleaseName(ambiance, infrastructure);
    if (EmptyPredicate.isEmpty(canaryStepFqn)) {
      throw new InvalidRequestException(K8S_CANARY_STEP_MISSING, USER);
    }

    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(canaryStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_OUTCOME));
    if (optionalSweepingOutput.isFound()) {
      K8sCanaryOutcome k8sCanaryOutcome = (K8sCanaryOutcome) optionalSweepingOutput.getOutput();
      return obtainTaskBasedOnCanaryOutcome(
          stepElementParameters, ambiance, infrastructure, k8sCanaryOutcome, releaseName);
    }

    optionalSweepingOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(canaryStepFqn + "." + K8sCanaryExecutionOutput.OUTPUT_NAME));
    if (!optionalSweepingOutput.isFound()) {
      return skipTaskRequestOrThrowException(ambiance);
    }

    return obtainTaskBasedOnReleaseName(stepElementParameters, ambiance, infrastructure, releaseName);
  }

  private TaskRequest obtainTaskBasedOnCanaryOutcome(StepElementParameters stepElementParameters, Ambiance ambiance,
      InfrastructureOutcome infrastructure, K8sCanaryOutcome canaryOutcome, String releaseName) {
    if (StepUtils.isStepInRollbackSection(ambiance) && !canaryOutcome.isCanaryWorkloadDeployed()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(SKIP_K8S_CANARY_DELETE_STEP_EXECUTION).build())
          .build();
    }

    K8sCanaryDeleteRequest request =
        K8sCanaryDeleteRequest.builder()
            .canaryWorkloads(canaryOutcome.getCanaryWorkload())
            .commandName(K8S_CANARY_DELETE_COMMAND_NAME)
            .k8sInfraDelegateConfig(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
            .timeoutIntervalInMin(
                NGTimeConversionHelper.convertTimeStringToMinutes(stepElementParameters.getTimeout().getValue()))
            .useNewKubectlVersion(cdStepHelper.isUseNewKubectlVersion(AmbianceUtils.getAccountId(ambiance)))
            .useDeclarativeRollback(k8sStepHelper.isDeclarativeRollbackEnabled(ambiance))
            .enabledSupportHPAAndPDB(cdStepHelper.isEnabledSupportHPAAndPDB(AmbianceUtils.getAccountId(ambiance)))
            .build();

    return queueCanaryDeleteRequest(stepElementParameters, request, ambiance, infrastructure, releaseName);
  }

  private TaskRequest obtainTaskBasedOnReleaseName(StepElementParameters stepElementParameters, Ambiance ambiance,
      InfrastructureOutcome infrastructure, String releaseName) {
    K8sCanaryDeleteRequest request =
        K8sCanaryDeleteRequest.builder()
            .releaseName(releaseName)
            .commandName(K8S_CANARY_DELETE_COMMAND_NAME)
            .k8sInfraDelegateConfig(cdStepHelper.getK8sInfraDelegateConfig(infrastructure, ambiance))
            .timeoutIntervalInMin(
                NGTimeConversionHelper.convertTimeStringToMinutes(stepElementParameters.getTimeout().getValue()))
            .useNewKubectlVersion(cdStepHelper.isUseNewKubectlVersion(AmbianceUtils.getAccountId(ambiance)))
            .useDeclarativeRollback(k8sStepHelper.isDeclarativeRollbackEnabled(ambiance))
            .enabledSupportHPAAndPDB(cdStepHelper.isEnabledSupportHPAAndPDB(AmbianceUtils.getAccountId(ambiance)))
            .build();

    return queueCanaryDeleteRequest(stepElementParameters, request, ambiance, infrastructure, releaseName);
  }

  private TaskRequest queueCanaryDeleteRequest(StepElementParameters stepElementParameters,
      K8sCanaryDeleteRequest request, Ambiance ambiance, InfrastructureOutcome infrastructure, String releaseName) {
    K8sCanaryDeleteStepParameters k8sCanaryDeleteStepParameters =
        (K8sCanaryDeleteStepParameters) stepElementParameters.getSpec();
    if (StepUtils.isStepInRollbackSection(ambiance)) {
      String canaryDeleteStepFqn = k8sCanaryDeleteStepParameters.getCanaryDeleteStepFqn();
      if (EmptyPredicate.isNotEmpty(canaryDeleteStepFqn)) {
        OptionalSweepingOutput existingCanaryDeleteOutput = executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                canaryDeleteStepFqn + "." + OutcomeExpressionConstants.K8S_CANARY_DELETE_OUTCOME));
        if (existingCanaryDeleteOutput.isFound()) {
          return TaskRequest.newBuilder()
              .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(K8S_CANARY_DELETE_ALREADY_DELETED).build())
              .build();
        }
      }
    }

    k8sStepHelper.publishReleaseNameStepDetails(ambiance, releaseName);
    return k8sStepHelper
        .queueK8sTask(stepElementParameters, request, ambiance,
            K8sExecutionPassThroughData.builder().infrastructure(infrastructure).build())
        .getTaskRequest();
  }

  private TaskRequest skipTaskRequestOrThrowException(Ambiance ambiance) {
    if (StepUtils.isStepInRollbackSection(ambiance)) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(SKIP_K8S_CANARY_DELETE_STEP_EXECUTION).build())
          .build();
    }

    throw new InvalidRequestException(K8S_CANARY_STEP_MISSING, USER);
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<K8sDeployResponse> responseSupplier)
      throws Exception {
    K8sDeployResponse k8sTaskExecutionResponse = responseSupplier.get();
    StepResponseBuilder responseBuilder =
        StepResponse.builder().unitProgressList(k8sTaskExecutionResponse.getCommandUnitsProgress().getUnitProgresses());

    if (k8sTaskExecutionResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return K8sStepHelper.getFailureResponseBuilder(k8sTaskExecutionResponse, responseBuilder).build();
    }

    if (!StepUtils.isStepInRollbackSection(ambiance)) {
      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.K8S_CANARY_DELETE_OUTCOME,
          K8sCanaryDeleteOutcome.builder().build(), StepOutcomeGroup.STEP.name());
    }

    return responseBuilder.status(Status.SUCCEEDED).build();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
