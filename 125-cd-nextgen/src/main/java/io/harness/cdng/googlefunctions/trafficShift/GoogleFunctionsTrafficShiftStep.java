/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.googlefunctions.trafficShift;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.googlefunctions.GoogleFunctionsHelper;
import io.harness.cdng.googlefunctions.GoogleFunctionsStepPassThroughData;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionPrepareRollbackOutcome;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionStepOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionCommandTypeNG;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionTrafficShiftRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionTrafficShiftResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.SkipTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class GoogleFunctionsTrafficShiftStep extends CdTaskExecutable<GoogleFunctionCommandResponse> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder()
          .setType(ExecutionNodeType.GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT.getYamlType())
          .setStepCategory(StepCategory.STEP)
          .build();

  public static final String GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT_COMMAND_NAME = "CloudFunctionTrafficShift";
  public static final String GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC_STEP_MISSING =
      "Google Function Deploy Without Traffic step is not configured.";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private GoogleFunctionsHelper googleFunctionsHelper;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepParameters, ThrowingSupplier<GoogleFunctionCommandResponse> responseDataSupplier)
      throws Exception {
    StepResponse stepResponse = null;
    try {
      GoogleFunctionTrafficShiftResponse googleFunctionTrafficShiftResponse =
          (GoogleFunctionTrafficShiftResponse) responseDataSupplier.get();

      StepResponseBuilder stepResponseBuilder = StepResponse.builder().unitProgressList(
          googleFunctionTrafficShiftResponse.getUnitProgressData().getUnitProgresses());
      stepResponse =
          googleFunctionsHelper.generateStepResponse(googleFunctionTrafficShiftResponse, stepResponseBuilder, ambiance);
    } catch (Exception e) {
      log.error("Error while processing google function traffic shift response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
    return stepResponse;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    GoogleFunctionsTrafficShiftStepParameters googleFunctionsTrafficShiftStepParameters =
        (GoogleFunctionsTrafficShiftStepParameters) stepParameters.getSpec();
    if (EmptyPredicate.isEmpty(
            googleFunctionsTrafficShiftStepParameters.getGoogleFunctionDeployWithoutTrafficStepFnq())) {
      return skipTaskRequest(GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC_STEP_MISSING);
    }

    OptionalSweepingOutput googleFunctionPrepareRollbackDataOptional =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                googleFunctionsTrafficShiftStepParameters.getGoogleFunctionDeployWithoutTrafficStepFnq() + "."
                + OutcomeExpressionConstants.GOOGLE_FUNCTION_PREPARE_ROLLBACK_OUTCOME));

    OptionalSweepingOutput googleFunctionDeployWithoutTrafficDataOptional =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                googleFunctionsTrafficShiftStepParameters.getGoogleFunctionDeployWithoutTrafficStepFnq() + "."
                + OutcomeExpressionConstants.GOOGLE_FUNCTION_DEPLOY_WITHOUT_TRAFFIC_OUTCOME));

    if (!googleFunctionPrepareRollbackDataOptional.isFound()
        || !googleFunctionDeployWithoutTrafficDataOptional.isFound()) {
      return skipTaskRequest(GOOGLE_CLOUD_FUNCTIONS_DEPLOY_WITHOUT_TRAFFIC_STEP_MISSING);
    }
    GoogleFunctionPrepareRollbackOutcome googleFunctionPrepareRollbackOutcome =
        (GoogleFunctionPrepareRollbackOutcome) googleFunctionPrepareRollbackDataOptional.getOutput();

    GoogleFunctionStepOutcome googleFunctionDeployWithoutTrafficOutcome =
        (GoogleFunctionStepOutcome) googleFunctionDeployWithoutTrafficDataOptional.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    GoogleFunctionTrafficShiftRequest googleFunctionTrafficShiftRequest =
        GoogleFunctionTrafficShiftRequest.builder()
            .googleFunctionCommandType(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_TRAFFIC_SHIFT)
            .commandName(GOOGLE_CLOUD_FUNCTIONS_TRAFFIC_SHIFT_COMMAND_NAME)
            .googleFunctionInfraConfig(googleFunctionsHelper.getInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .googleCloudRunServiceAsString(googleFunctionPrepareRollbackOutcome.getCloudRunServiceAsString())
            .isFirstDeployment(googleFunctionPrepareRollbackOutcome.isFirstDeployment())
            .googleFunctionAsString(googleFunctionPrepareRollbackOutcome.getCloudFunctionAsString())
            .targetRevision(googleFunctionDeployWithoutTrafficOutcome.getCloudRunService().getRevision())
            .targetTrafficPercent(googleFunctionsTrafficShiftStepParameters.getTrafficPercent().getValue())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .build();

    return googleFunctionsHelper
        .queueTask(stepParameters, googleFunctionTrafficShiftRequest, ambiance,
            GoogleFunctionsStepPassThroughData.builder().infrastructureOutcome(infrastructureOutcome).build(), true,
            TaskType.GOOGLE_FUNCTION_TRAFFIC_SHIFT_TASK)
        .getTaskRequest();
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private TaskRequest skipTaskRequest(String message) {
    return TaskRequest.newBuilder()
        .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage(message).build())
        .build();
  }
}
