/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.lambda.rollback;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.aws.lambda.AwsLambdaHelper;
import io.harness.cdng.aws.lambda.AwsLambdaStepPassThroughData;
import io.harness.cdng.aws.lambda.beans.AwsLambdaPrepareRollbackOutcome;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.googlefunctions.GoogleFunctionsHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.aws.lambda.AwsLambdaCommandTypeNG;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaRollbackRequest;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaCommandResponse;
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
public class AwsLambdaRollbackStep extends CdTaskExecutable<AwsLambdaCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AWS_LAMBDA_ROLLBACK.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  private final String AWS_LAMBDA_ROLLBACK_COMMAND_NAME = "RollbackAwsLambda";
  public static final String AWS_LAMBDA_DEPLOYMENT_STEP_MISSING =
      "Aws Lambda Deployment Step was not executed. Skipping Rollback...";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private GoogleFunctionsHelper googleFunctionsHelper;
  @Inject private InstanceInfoService instanceInfoService;
  @Inject private AwsLambdaHelper awsLambdaHelper;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<AwsLambdaCommandResponse> responseDataSupplier) throws Exception {
    StepResponse stepResponse = null;
    try {
      AwsLambdaCommandResponse awsLambdaCommandResponse = (AwsLambdaCommandResponse) responseDataSupplier.get();

      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(awsLambdaCommandResponse.getUnitProgressData().getUnitProgresses());
      stepResponse = awsLambdaHelper.generateStepResponse(awsLambdaCommandResponse, stepResponseBuilder, ambiance);
    } catch (Exception e) {
      log.error("Error while processing aws lambda function rollback response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
    return stepResponse;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    AwsLambdaRollbackStepParameters awsLambdaRollbackStepParameters =
        (AwsLambdaRollbackStepParameters) stepParameters.getSpec();
    if (EmptyPredicate.isEmpty(awsLambdaRollbackStepParameters.getAwsLambdaDeployStepFqn())) {
      return skipTaskRequest(AWS_LAMBDA_DEPLOYMENT_STEP_MISSING);
    }

    String stepFnq = awsLambdaRollbackStepParameters.getAwsLambdaDeployStepFqn();
    OptionalSweepingOutput awsLambdaPrepareRollbackDataOptional =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(
                stepFnq + "." + OutcomeExpressionConstants.AWS_LAMBDA_FUNCTION_PREPARE_ROLLBACK_OUTCOME));

    if (!awsLambdaPrepareRollbackDataOptional.isFound()) {
      return skipTaskRequest(AWS_LAMBDA_DEPLOYMENT_STEP_MISSING);
    }

    AwsLambdaPrepareRollbackOutcome awsLambdaPrepareRollbackOutcome =
        (AwsLambdaPrepareRollbackOutcome) awsLambdaPrepareRollbackDataOptional.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    AwsLambdaRollbackRequest awsLambdaRollbackRequest =
        AwsLambdaRollbackRequest.builder()
            .functionName(awsLambdaPrepareRollbackOutcome.getFunctionName())
            .qualifier(awsLambdaPrepareRollbackOutcome.getQualifier())
            .firstDeployment(awsLambdaPrepareRollbackOutcome.isFirstDeployment())
            .awsLambdaCommandTypeNG(AwsLambdaCommandTypeNG.AWS_LAMBDA_ROLLBACK)
            .commandName(AWS_LAMBDA_ROLLBACK_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .awsLambdaInfraConfig(awsLambdaHelper.getInfraConfig(infrastructureOutcome, ambiance))
            .awsLambdaArtifactConfig(awsLambdaPrepareRollbackOutcome.getAwsLambdaArtifactConfig())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .functionCode(awsLambdaPrepareRollbackOutcome.getFunctionCode())
            .functionConfiguration(awsLambdaPrepareRollbackOutcome.getFunctionConfiguration())
            .build();

    return awsLambdaHelper
        .queueTask(stepParameters, awsLambdaRollbackRequest, TaskType.AWS_LAMBDA_ROLLBACK_COMMAND_TASK_NG, ambiance,
            AwsLambdaStepPassThroughData.builder().infrastructureOutcome(infrastructureOutcome).build(), true)
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
