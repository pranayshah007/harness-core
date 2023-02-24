/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam.publish;

import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.aws.sam.AwsSamStepCommonHelper;
import io.harness.cdng.aws.sam.beans.AwsSamExecutionPassThroughData;
import io.harness.cdng.aws.sam.beans.AwsSamValidateBuildPackageDataOutput;
import io.harness.cdng.executables.CdTaskExecutable;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.sam.AwsSamEntityHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.aws.sam.AwsSamCommandType;
import io.harness.delegate.task.aws.sam.AwsSamInfraConfig;
import io.harness.delegate.task.aws.sam.AwsSamPublishConfig;
import io.harness.delegate.task.aws.sam.request.AwsSamPublishRequest;
import io.harness.delegate.task.aws.sam.response.AwsSamCommandResponse;
import io.harness.delegate.task.aws.sam.response.AwsSamPublishResponse;
import io.harness.exception.ExceptionUtils;
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
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AwsSamPublishStep extends CdTaskExecutable<AwsSamCommandResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AWS_SAM_PUBLISH.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  private final String AWS_SAM_PUBLISH_COMMAND_NAME = "AWS_SAM_PUBLISH";

  @Inject AwsSamStepCommonHelper awsSamStepCommonHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private OutcomeService outcomeService;
  @Inject private AwsSamEntityHelper awsSamEntityHelper;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // Nothing to validate
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<AwsSamCommandResponse> responseDataSupplier)
      throws Exception {
    StepResponse stepResponse = null;
    try {
      AwsSamPublishResponse awsSamPublishResponse = (AwsSamPublishResponse) responseDataSupplier.get();

      StepResponseBuilder stepResponseBuilder =
          StepResponse.builder().unitProgressList(awsSamPublishResponse.getUnitProgressData().getUnitProgresses());

      stepResponse = generateStepResponse(ambiance, awsSamPublishResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing AWS SAM Publish response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
    return stepResponse;
  }

  private StepResponse generateStepResponse(
      Ambiance ambiance, AwsSamPublishResponse awsSamPublishResponse, StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse;

    if (awsSamPublishResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse =
          stepResponseBuilder.status(Status.FAILED)
              .failureInfo(FailureInfo.newBuilder()
                               .setErrorMessage(awsSamStepCommonHelper.getErrorMessage(awsSamPublishResponse))
                               .build())
              .build();
    } else {
      // toDo outcome
      stepResponse =
          stepResponseBuilder.status(Status.SUCCEEDED)
              .stepOutcome(
                  StepResponse.StepOutcome.builder().name(OutcomeExpressionConstants.OUTPUT).outcome(null).build())
              .build();
    }
    return stepResponse;
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    final String accountId = AmbianceUtils.getAccountId(ambiance);

    AwsSamPublishStepParameters awsSamPublishStepParameters =
        (AwsSamPublishStepParameters) stepElementParameters.getSpec();

    if (EmptyPredicate.isEmpty(awsSamPublishStepParameters.getAwsSamValidateBuildPackageFqn())) {
      throw new InvalidRequestException("AWS SAM Build and Package Step Missing", USER);
    }

    OptionalSweepingOutput awsSamValidateBuildPackageDataOptionalOutput =
        executionSweepingOutputService.resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(awsSamPublishStepParameters.getAwsSamValidateBuildPackageFqn()
                + "." + OutcomeExpressionConstants.AWS_SAM_VALIDATE_BUILD_PACKAGE_OUTPUT));

    if (!awsSamValidateBuildPackageDataOptionalOutput.isFound()) {
      return skipTaskRequestOrThrowException(ambiance);
    }

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    AwsSamInfraConfig awsSamInfraConfig =
        awsSamEntityHelper.getAwsSamInfraConfigFromOutcome(infrastructureOutcome, ambiance);
    AwsSamPublishConfig awsSamPublishConfig =
        AwsSamPublishConfig.builder()
            .publishCommandOptions(awsSamPublishStepParameters.getPublishCommandOptions().getValue())
            .build();
    AwsSamValidateBuildPackageDataOutput awsSamValidateBuildPackageDataOutput =
        (AwsSamValidateBuildPackageDataOutput) awsSamValidateBuildPackageDataOptionalOutput.getOutput();

    AwsSamPublishRequest awsSamCommandRequest =
        AwsSamPublishRequest.builder()
            .accountId(accountId)
            .awsSamCommandType(AwsSamCommandType.AWS_SAM_PUBLISH)
            .commandName(AWS_SAM_PUBLISH_COMMAND_NAME)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .awsSamInfraConfig(awsSamInfraConfig)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepElementParameters))
            .awsSamPublishConfig(awsSamPublishConfig)
            .templateFileContent(awsSamValidateBuildPackageDataOutput.getTemplateFileContent())
            .configFileContent(awsSamValidateBuildPackageDataOutput.getConfigFileContent())
            .build();

    return awsSamStepCommonHelper
        .queueTask(stepElementParameters, awsSamCommandRequest, ambiance,
            AwsSamExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
            TaskType.AWS_SAM_PUBLISH)
        .getTaskRequest();
  }

  private TaskRequest skipTaskRequestOrThrowException(Ambiance ambiance) {
    if (StepUtils.isStepInRollbackSection(ambiance)) {
      return TaskRequest.newBuilder()
          .setSkipTaskRequest(SkipTaskRequest.newBuilder().setMessage("AWS SAM Publish Step Skipped").build())
          .build();
    }

    throw new InvalidRequestException("AWS SAM Publish Step Missing", USER);
  }
}
