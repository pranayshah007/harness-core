/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import io.harness.account.services.AccountService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.serverless.beans.ServerlessExecutionPassThroughData;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaRollbackResult;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaRollbackConfig;
import io.harness.delegate.task.serverless.ServerlessCommandType;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.delegate.task.serverless.request.ServerlessRollbackRequest;
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
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepHelper;
import io.harness.supplier.ThrowingSupplier;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ServerlessAwsLambdaRollbackStep extends CdTaskExecutable<ServerlessCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.SERVERLESS_AWS_LAMBDA_ROLLBACK.getYamlType())
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
    ServerlessAwsLambdaRollbackStepParameters rollbackStepParameters =
        (ServerlessAwsLambdaRollbackStepParameters) stepElementParameters.getSpec();
    if (EmptyPredicate.isEmpty(rollbackStepParameters.getServerlessAwsLambdaRollbackFnq())) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("Serverless Aws Lambda Deploy step was not executed. Skipping rollback.")
                                  .build())
          .build();
    }
    OptionalSweepingOutput serverlessRollbackDataOptionalOutput =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(rollbackStepParameters.getServerlessAwsLambdaRollbackFnq() + "."
                + OutcomeExpressionConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK_DATA_OUTCOME));
    OptionalSweepingOutput serverlessFetchFileOptionalOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(rollbackStepParameters.getServerlessAwsLambdaRollbackFnq() + "."
            + OutcomeExpressionConstants.SERVERLESS_FETCH_FILE_OUTCOME));
    if (!serverlessRollbackDataOptionalOutput.isFound() || !serverlessFetchFileOptionalOutput.isFound()) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder()
                                  .setMessage("Serverless Aws Lambda Deploy step was not executed. Skipping rollback.")
                                  .build())
          .build();
    }
    ServerlessAwsLambdaRollbackDataOutcome rollbackDataOutcome =
        (ServerlessAwsLambdaRollbackDataOutcome) serverlessRollbackDataOptionalOutput.getOutput();
    ServerlessFetchFileOutcome serverlessFetchFileOutcome =
        (ServerlessFetchFileOutcome) serverlessFetchFileOptionalOutput.getOutput();
    if (!rollbackDataOutcome.isFirstDeployment()
        && EmptyPredicate.isEmpty(rollbackDataOutcome.getPreviousVersionTimeStamp())) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(
              SkipTaskRequest.newBuilder().setMessage("No previous version exist. Skipping rollback.").build())
          .build();
    }
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    ManifestsOutcome manifestsOutcome = serverlessStepCommonHelper.resolveServerlessManifestsOutcome(ambiance);
    ManifestOutcome serverlessManifestOutcome = serverlessStepCommonHelper.getServerlessManifestOutcome(
        manifestsOutcome.values(), serverlessAwsLambdaStepHelper);
    Map<String, Object> manifestParams = new HashMap<>();
    manifestParams.put("manifestFileOverrideContent", serverlessFetchFileOutcome.getManifestFileOverrideContent());
    manifestParams.put("manifestFilePathContent", serverlessFetchFileOutcome.getManifestFilePathContent());
    ServerlessManifestConfig serverlessManifestConfig = serverlessStepCommonHelper.getServerlessManifestConfig(
        manifestParams, serverlessManifestOutcome, ambiance, serverlessAwsLambdaStepHelper);
    ServerlessAwsLambdaRollbackConfig serverlessAwsLambdaRollbackConfig =
        ServerlessAwsLambdaRollbackConfig.builder()
            .previousVersionTimeStamp(rollbackDataOutcome.getPreviousVersionTimeStamp())
            .isFirstDeployment(rollbackDataOutcome.isFirstDeployment())
            .build();
    final String accountId = AmbianceUtils.getAccountId(ambiance);
    ServerlessRollbackRequest serverlessRollbackRequest =
        ServerlessRollbackRequest.builder()
            .accountId(accountId)
            .serverlessCommandType(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_ROLLBACK)
            .serverlessInfraConfig(serverlessStepCommonHelper.getServerlessInfraConfig(infrastructureOutcome, ambiance))
            .serverlessManifestConfig(serverlessManifestConfig)
            .serverlessRollbackConfig(serverlessAwsLambdaRollbackConfig)
            .commandName(SERVERLESS_AWS_LAMBDA_ROLLBACK_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .manifestContent(serverlessFetchFileOutcome.getManifestFileOverrideContent())
            .build();
    return serverlessStepCommonHelper
        .queueServerlessTask(stepElementParameters, serverlessRollbackRequest, ambiance,
            ServerlessExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true)
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
      log.error("Error while processing Serverless Aws Lambda rollback response: {}", ExceptionUtils.getMessage(e), e);
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
      ServerlessAwsLambdaRollbackResult serverlessAwsLambdaRollbackResult =
          (ServerlessAwsLambdaRollbackResult) rollbackResponse.getServerlessRollbackResult();
      ServerlessAwsLambdaRollbackOutcome serverlessAwsLambdaRollbackOutcome =
          ServerlessAwsLambdaRollbackOutcome.builder()
              .stage(serverlessAwsLambdaRollbackResult.getStage())
              .rollbackTimeStamp(serverlessAwsLambdaRollbackResult.getRollbackTimeStamp())
              .region(serverlessAwsLambdaRollbackResult.getRegion())
              .service(serverlessAwsLambdaRollbackResult.getService())
              .build();
      executionSweepingOutputService.consume(ambiance,
          OutcomeExpressionConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK_OUTCOME, serverlessAwsLambdaRollbackOutcome,
          StepOutcomeGroup.STEP.name());
      stepResponse = stepResponseBuilder.status(Status.SUCCEEDED)
                         .stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(OutcomeExpressionConstants.OUTPUT)
                                          .outcome(serverlessAwsLambdaRollbackOutcome)
                                          .build())
                         .build();
    }
    return stepResponse;
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }
}
