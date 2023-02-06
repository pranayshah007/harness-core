/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.googlefunctions.rollback;

import static io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.googlefunctions.GoogleFunctionsHelper;
import io.harness.cdng.googlefunctions.GoogleFunctionsStepPassThroughData;
import io.harness.cdng.googlefunctions.beans.GoogleFunctionPrepareRollbackOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunctionCommandTypeNG;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionRollbackRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionRollbackResponse;
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
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class GoogleFunctionsRollbackStep extends CdTaskExecutable<GoogleFunctionCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GOOGLE_CLOUD_FUNCTIONS_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  public static final String GOOGLE_CLOUD_FUNCTIONS_ROLLBACK_COMMAND_NAME = "CloudFunctionRollback";
  public static final String GOOGLE_CLOUD_FUNCTIONS_DEPLOYMENT_STEP_MISSING =
      "Google Function Deployment Step was not executed. Skipping Rollback...";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private GoogleFunctionsHelper googleFunctionsHelper;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<GoogleFunctionCommandResponse> responseDataSupplier) throws Exception {
    StepResponse stepResponse = null;
    try {
      GoogleFunctionRollbackResponse googleFunctionRollbackResponse =
          (GoogleFunctionRollbackResponse) responseDataSupplier.get();

      StepResponseBuilder stepResponseBuilder = StepResponse.builder().unitProgressList(
          googleFunctionRollbackResponse.getUnitProgressData().getUnitProgresses());
      InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
          ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
      GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig =
          (GcpGoogleFunctionInfraConfig) googleFunctionsHelper.getInfraConfig(infrastructureOutcome, ambiance);
      List<ServerInstanceInfo> serverInstanceInfoList = googleFunctionsHelper.getServerInstanceInfo(
          googleFunctionRollbackResponse, gcpGoogleFunctionInfraConfig, infrastructureOutcome.getInfrastructureKey());
      StepResponse.StepOutcome stepOutcome =
          instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);
      stepResponseBuilder.stepOutcome(stepOutcome);
      stepResponse = googleFunctionsHelper.generateStepResponse(googleFunctionRollbackResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing google function rollback response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
    return stepResponse;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    GoogleFunctionsRollbackStepParameters googleFunctionsRollbackStepParameters =
        (GoogleFunctionsRollbackStepParameters) stepParameters.getSpec();
    if (EmptyPredicate.isEmpty(googleFunctionsRollbackStepParameters.getGoogleFunctionDeployWithoutTrafficStepFnq())
        && EmptyPredicate.isEmpty(googleFunctionsRollbackStepParameters.getGoogleFunctionDeployStepFnq())) {
      return skipTaskRequest(GOOGLE_CLOUD_FUNCTIONS_DEPLOYMENT_STEP_MISSING);
    }

    String stepFnq = googleFunctionsRollbackStepParameters.getGoogleFunctionDeployWithoutTrafficStepFnq();
    if (EmptyPredicate.isEmpty(stepFnq)) {
      stepFnq = googleFunctionsRollbackStepParameters.getGoogleFunctionDeployStepFnq();
    }
    OptionalSweepingOutput googleFunctionPrepareRollbackDataOptional =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                stepFnq + "." + OutcomeExpressionConstants.GOOGLE_FUNCTION_PREPARE_ROLLBACK_OUTCOME));

    GoogleFunctionPrepareRollbackOutcome googleFunctionPrepareRollbackOutcome =
        (GoogleFunctionPrepareRollbackOutcome) googleFunctionPrepareRollbackDataOptional.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    GoogleFunctionRollbackRequest googleFunctionRollbackRequest =
        GoogleFunctionRollbackRequest.builder()
            .googleFunctionCommandType(GoogleFunctionCommandTypeNG.GOOGLE_FUNCTION_ROLLBACK)
            .commandName(GOOGLE_CLOUD_FUNCTIONS_ROLLBACK_COMMAND_NAME)
            .googleFunctionInfraConfig(googleFunctionsHelper.getInfraConfig(infrastructureOutcome, ambiance))
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .googleCloudRunServiceAsString(googleFunctionPrepareRollbackOutcome.getCloudRunServiceAsString())
            .googleFunctionAsString(googleFunctionPrepareRollbackOutcome.getCloudFunctionAsString())
            .isFirstDeployment(googleFunctionPrepareRollbackOutcome.isFirstDeployment())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .build();

    return googleFunctionsHelper
        .queueTask(stepParameters, googleFunctionRollbackRequest, ambiance,
            GoogleFunctionsStepPassThroughData.builder().infrastructureOutcome(infrastructureOutcome).build(), true)
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
