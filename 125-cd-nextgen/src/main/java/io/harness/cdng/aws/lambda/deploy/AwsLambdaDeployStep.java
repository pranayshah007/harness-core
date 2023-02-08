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
import io.harness.cdng.aws.lambda.AwsLambdaStepExecutor;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.task.aws.lambda.AwsLambdaCommandTypeNG;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaDeployRequest;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskChainExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AwsLambdaDeployStep extends TaskChainExecutableWithRollbackAndRbac implements AwsLambdaStepExecutor {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AWS_LAMBDA_DEPLOY.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private AwsLambdaHelper awsLambdaHelper;

  private final String AWS_LAMBDA_DEPLOY_COMMAND_NAME = "DeployAwsLambda";
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
    return TaskChainResponse.builder().build();
  }

  @Override
  public StepResponse finalizeExecutionWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      PassThroughData passThroughData, ThrowingSupplier<ResponseData> responseDataSupplier) throws Exception {
    return StepResponse.builder().build();
  }

  @Override
  public TaskChainResponse startChainLinkAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    return TaskChainResponse.builder().build();
  }

  @Override
  public TaskChainResponse executeTask(Ambiance ambiance, StepElementParameters stepParameters,
      AwsLambdaStepExceptionPassThroughData awsLambdaStepExceptionPassThroughData, UnitProgressData unitProgressData) {
    InfrastructureOutcome infrastructureOutcome = awsLambdaStepExceptionPassThroughData.getInfrastructureOutcome();

    AwsLambdaDeployRequest awsLambdaDeployRequest =
        AwsLambdaDeployRequest.builder()
            .awsLambdaCommandTypeNG(AwsLambdaCommandTypeNG.AWS_LAMBDA_DEPLOY)
            .commandName(AWS_LAMBDA_DEPLOY_COMMAND_NAME)
            .commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(unitProgressData))
            .awsLambdaFunctionsInfraConfig(awsLambdaHelper.getInfraConfig(infrastructureOutcome, ambiance))
            .build();

    return awsLambdaHelper.queueTask(
        stepParameters, awsLambdaDeployRequest, ambiance, awsLambdaStepExceptionPassThroughData, true);
  }
}
