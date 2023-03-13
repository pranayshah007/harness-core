/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.lambda.deploy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.aws.lambda.AwsLambdaHelper;
import io.harness.cdng.aws.lambda.AwsLambdaStepExceptionPassThroughData;
import io.harness.cdng.aws.lambda.AwsLambdaStepPassThroughData;
import io.harness.cdng.aws.lambda.beans.AwsLambdaStepOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.task.aws.lambda.AwsLambdaFunctionsInfraConfig;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaDeployResponse;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AwsLambdaDeployStep extends TaskChainExecutableWithRollbackAndRbac {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AWS_LAMBDA_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private AwsLambdaHelper awsLambdaHelper;
  @Inject private InstanceInfoService instanceInfoService;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // nothing
  }

  @Override
  public TaskChainResponse executeNextLinkWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseSupplier)
      throws Exception {
    log.info("Calling executeNextLink");
    return awsLambdaHelper.executeNextLink(ambiance, stepParameters, passThroughData, responseSupplier);
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    if (passThroughData instanceof AwsLambdaStepExceptionPassThroughData) {
      return awsLambdaHelper.handleStepExceptionFailure((AwsLambdaStepExceptionPassThroughData) passThroughData);
    }

    log.info("Finalizing execution with passThroughData: " + passThroughData.getClass().getName());
    AwsLambdaStepPassThroughData awsLambdaStepPassThroughData = (AwsLambdaStepPassThroughData) passThroughData;
    AwsLambdaDeployResponse awsLambdaDeployResponse;

    try {
      awsLambdaDeployResponse = (AwsLambdaDeployResponse) responseDataSupplier.get();
    } catch (Exception e) {
      log.error("Error while processing AWS Lambda Function response: {}", e.getCause(), e);
      return awsLambdaHelper.handleStepFailureException(ambiance, awsLambdaStepPassThroughData, e);
    }
    StepResponseBuilder stepResponseBuilder =
        StepResponse.builder().unitProgressList(awsLambdaDeployResponse.getUnitProgressData().getUnitProgresses());
    if (awsLambdaDeployResponse.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
      return AwsLambdaHelper.getFailureResponseBuilder(awsLambdaDeployResponse, stepResponseBuilder).build();
    }

    AwsLambdaStepOutcome awsLambdaStepOutcome =
        awsLambdaHelper.getAwsLambdaStepOutcome(awsLambdaDeployResponse.getAwsLambda());

    InfrastructureOutcome infrastructureOutcome = awsLambdaStepPassThroughData.getInfrastructureOutcome();
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig =
        awsLambdaHelper.getInfraConfig(infrastructureOutcome, ambiance);
    List<ServerInstanceInfo> serverInstanceInfoList = awsLambdaHelper.getServerInstanceInfo(
        awsLambdaDeployResponse, awsLambdaFunctionsInfraConfig, infrastructureOutcome.getInfrastructureKey());
    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);

    return stepResponseBuilder.status(Status.SUCCEEDED)
        .stepOutcome(stepOutcome)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.OUTPUT)
                         .outcome(awsLambdaStepOutcome)
                         .build())
        .build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return awsLambdaHelper.startChainLink(ambiance, stepParameters);
  }
}
