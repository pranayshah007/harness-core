/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless.container.steps;
import io.harness.account.services.AccountService;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.serverless.ServerlessAwsLambdaStepHelper;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaRollbackV2Config;
import io.harness.delegate.task.serverless.ServerlessCommandType;
import io.harness.delegate.task.serverless.request.ServerlessRollbackV2Request;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessRollbackResponse;
import io.harness.exception.ExceptionUtils;
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
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_SERVERLESS})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ServerlessAwsLambdaRollbackV2Step extends CdTaskExecutable<ServerlessCommandResponse> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder()
          .setType(ExecutionNodeType.SERVERLESS_AWS_LAMBDA_ROLLBACK_V2.getYamlType())
          .setStepCategory(StepCategory.STEP)
          .build();

  public static final String SERVERLESS_AWS_LAMBDA_ROLLBACK_COMMAND_NAME = "ServerlessAwsLambdaRollback";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private ServerlessStepCommonHelper serverlessStepCommonHelper;
  @Inject private OutcomeService outcomeService;
  @Inject private ServerlessAwsLambdaStepHelper serverlessAwsLambdaStepHelper;
  @Inject private StepHelper stepHelper;
  @Inject private AccountService accountService;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Nothing to validate
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    ServerlessAwsLambdaRollbackV2StepParameters rollbackStepParameters =
        (ServerlessAwsLambdaRollbackV2StepParameters) stepElementParameters.getSpec();
    if (EmptyPredicate.isEmpty(rollbackStepParameters.getServerlessAwsLambdaRollbackFnq())) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder()
                  .setMessage("Serverless Aws Lambda Prepare Rollback step was not executed. Skipping rollback.")
                  .build())
          .build();
    }
    OptionalSweepingOutput serverlessRollbackDataOptionalOutput =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(rollbackStepParameters.getServerlessAwsLambdaRollbackFnq() + "."
                + OutcomeExpressionConstants.SERVERLESS_AWS_LAMBDA_PREPARE_ROLLBACK_DATA_OUTCOME_V2));
    if (!serverlessRollbackDataOptionalOutput.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder()
                  .setMessage("Serverless Aws Lambda Prepare Rollback V2 step was not executed. Skipping rollback.")
                  .build())
          .build();
    }
    ServerlessAwsLambdaPrepareRollbackDataOutcome serverlessAwsLambdaPrepareRollbackDataOutcome =
        (ServerlessAwsLambdaPrepareRollbackDataOutcome) serverlessRollbackDataOptionalOutput.getOutput();

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    ServerlessAwsLambdaRollbackV2Config serverlessAwsLambdaRollbackConfig =
        ServerlessAwsLambdaRollbackV2Config.builder()
            .stackDetails(serverlessAwsLambdaPrepareRollbackDataOutcome.getStackDetails())
            .isFirstDeployment(serverlessAwsLambdaPrepareRollbackDataOutcome.isFirstDeployment())
            .build();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    ServerlessRollbackV2Request serverlessRollbackRequest =
        ServerlessRollbackV2Request.builder()
            .accountId(accountId)
            .serverlessCommandType(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_ROLLBACK)
            .serverlessInfraConfig(serverlessStepCommonHelper.getServerlessInfraConfig(infrastructureOutcome, ambiance))
            .serverlessRollbackConfig(serverlessAwsLambdaRollbackConfig)
            .commandName(SERVERLESS_AWS_LAMBDA_ROLLBACK_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .build();
    return serverlessStepCommonHelper
        .queueServerlessTaskWithTaskType(stepElementParameters, serverlessRollbackRequest, ambiance,
            ServerlessExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            TaskType.SERVERLESS_ROLLBACK_V2_TASK)
        .getTaskRequest();
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(Ambiance ambiance,
      StepElementParameters stepParameters, ThrowingSupplier<ServerlessCommandResponse> responseDataSupplier)
      throws Exception {
    StepResponse stepResponse = null;
    try {
      ServerlessRollbackResponse rollbackResponse = (ServerlessRollbackResponse) responseDataSupplier.get();
      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(rollbackResponse.getUnitProgressData().getUnitProgresses());

      stepResponse = generateStepResponse(ambiance, rollbackResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error(
          "Error while processing Serverless Aws Lambda rollback V2 response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    } finally {
      String accountName = accountService.getAccount(AmbianceUtils.getAccountId(ambiance)).getName();
      stepHelper.sendRollbackTelemetryEvent(
          ambiance, stepResponse == null ? Status.FAILED : stepResponse.getStatus(), accountName);
    }
    return stepResponse;
  }

  private StepResponse generateStepResponse(
      Ambiance ambiance, ServerlessRollbackResponse rollbackResponse, StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse = null;

    if (rollbackResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse = stepResponseBuilder.status(Status.FAILED)
                         .failureInfo(FailureInfo.newBuilder()
                                          .setErrorMessage(serverlessStepCommonHelper.getErrorMessage(rollbackResponse))
                                          .build())
                         .build();
    } else {
      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED).build();
    }
    return stepResponse;
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
