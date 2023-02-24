/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam.validateBuildPackage;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.aws.sam.AwsSamStepCommonHelper;
import io.harness.cdng.aws.sam.AwsSamStepExecutor;
import io.harness.cdng.aws.sam.AwsSamStepPassThroughData;
import io.harness.cdng.aws.sam.beans.AwsSamExecutionPassThroughData;
import io.harness.cdng.aws.sam.beans.AwsSamStepExecutorParams;
import io.harness.cdng.aws.sam.beans.AwsSamValidateBuildPackageDataOutput;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.sam.AwsSamEntityHelper;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.aws.sam.AwsSamCommandType;
import io.harness.delegate.task.aws.sam.AwsSamFilePathContentConfig;
import io.harness.delegate.task.aws.sam.AwsSamInfraConfig;
import io.harness.delegate.task.aws.sam.AwsSamManifestConfig;
import io.harness.delegate.task.aws.sam.AwsSamValidateBuildPackageConfig;
import io.harness.delegate.task.aws.sam.request.AwsSamValidateBuildPackageRequest;
import io.harness.delegate.task.aws.sam.response.AwsSamPublishResponse;
import io.harness.delegate.task.aws.sam.response.AwsSamValidateBuildPackageResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AwsSamValidateBuildPackageStep
    extends TaskChainExecutableWithRollbackAndRbac implements AwsSamStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AWS_SAM_VALIDATE_BUILD_PACKAGE.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  private final String AWS_SAM_VALIDATE_BUILD_PACKAGE_COMMAND_NAME = "AWS_SAM_VALIDATE_BUILD_PACKAGE";

  @Inject AwsSamStepCommonHelper awsSamStepCommonHelper;
  @Inject private OutcomeService outcomeService;
  @Inject private AwsSamEntityHelper awsSamEntityHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink for AwsSamValidateBuildPackageStep");
    return awsSamStepCommonHelper.executeNextLink(this, ambiance, stepParameters, passThroughData, responseSupplier);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    StepResponse stepResponse = null;
    try {
      AwsSamValidateBuildPackageResponse awsSamValidateBuildPackageResponse =
          (AwsSamValidateBuildPackageResponse) responseDataSupplier.get();

      AwsSamValidateBuildPackageDataOutput awsSamValidateBuildPackageDataOutput =
          AwsSamValidateBuildPackageDataOutput.builder()
              .templateFileContent(awsSamValidateBuildPackageResponse.getTemplateContent())
              .configFileContent(awsSamValidateBuildPackageResponse.getConfigContent())
              .build();

      executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.AWS_SAM_VALIDATE_BUILD_PACKAGE_OUTPUT,
          awsSamValidateBuildPackageDataOutput, StepOutcomeGroup.STEP.name());

      StepResponse.StepResponseBuilder stepResponseBuilder = StepResponse.builder().unitProgressList(
          awsSamValidateBuildPackageResponse.getUnitProgressData().getUnitProgresses());

      stepResponse = generateStepResponse(ambiance, awsSamValidateBuildPackageResponse, stepResponseBuilder);
    } catch (Exception e) {
      log.error("Error while processing AWS SAM Publish response: {}", ExceptionUtils.getMessage(e), e);
      throw e;
    }
    return stepResponse;
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return awsSamStepCommonHelper.startChainLink(ambiance, stepParameters);
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  public TaskChainResponse prepareAwsSamTask(Ambiance ambiance, StepElementParameters stepParameters,
      AwsSamStepPassThroughData passThroughData, UnitProgressData unitProgressData,
      AwsSamStepExecutorParams awsSamStepExecutorParams) {
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    AwsSamInfraConfig awsSamInfraConfig =
        awsSamEntityHelper.getAwsSamInfraConfigFromOutcome(infrastructureOutcome, ambiance);
    AwsSamValidateBuildPackageStepParameters awsSamStepParameters =
        (AwsSamValidateBuildPackageStepParameters) stepParameters.getSpec();

    AwsSamManifestConfig awsSamManifestConfig = awsSamStepCommonHelper.getManifestConfig(
        ambiance, passThroughData.getManifestOutcome(), awsSamStepExecutorParams);

    AwsSamValidateBuildPackageConfig awsSamValidateBuildPackageConfig =
        AwsSamValidateBuildPackageConfig.builder()
            .validateCommandOptions(awsSamStepParameters.validateCommandOptions.getValue())
            .buildCommandOptions(awsSamStepParameters.buildCommandOptions.getValue())
            .packageCommandOptions(awsSamStepParameters.packageCommandOptions.getValue())
            .build();

    AwsSamValidateBuildPackageRequest awsSamValidateBuildPackageRequest =
        AwsSamValidateBuildPackageRequest.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .awsSamCommandType(AwsSamCommandType.AWS_SAM_VALIDATE_BUILD_PACKAGE)
            .commandName(AWS_SAM_VALIDATE_BUILD_PACKAGE_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .awsSamInfraConfig(awsSamInfraConfig)
            .timeoutIntervalInMin(CDStepHelper.getTimeoutInMin(stepParameters))
            .awsSamValidateBuildPackageConfig(awsSamValidateBuildPackageConfig)
            .awsSamManifestConfig(awsSamManifestConfig)
            .templateFileContent(awsSamStepExecutorParams.getTemplateFileContent())
            .configFileContent(awsSamStepExecutorParams.getConfigFileContent())
            .build();

    return awsSamStepCommonHelper.queueTask(stepParameters, awsSamValidateBuildPackageRequest, ambiance,
        AwsSamExecutionPassThroughData.builder().infrastructure(infrastructureOutcome).build(), true,
        TaskType.AWS_SAM_VALIDATE_BUILD_PACKAGE);
  }

  private StepResponse generateStepResponse(Ambiance ambiance,
      AwsSamValidateBuildPackageResponse awsSamValidateBuildPackageResponse,
      StepResponse.StepResponseBuilder stepResponseBuilder) {
    StepResponse stepResponse;

    if (awsSamValidateBuildPackageResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      stepResponse =
          stepResponseBuilder.status(Status.FAILED)
              .failureInfo(
                  FailureInfo.newBuilder()
                      .setErrorMessage(awsSamStepCommonHelper.getErrorMessage(awsSamValidateBuildPackageResponse))
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
}
