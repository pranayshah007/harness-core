/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless.container.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.cdng.aws.sam.AwsSamStepHelper;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunctionsWithServiceName;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepOutput;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plugin.AbstractContainerStepV2;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.tasks.ResponseData;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ServerlessAwsLambdaDeployV2Step extends AbstractContainerStepV2<StepElementParameters> {
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;

  @Inject ServerlessStepCommonHelper serverlessStepCommonHelper;

  @Inject AwsSamStepHelper awsSamStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Inject private InstanceInfoService instanceInfoService;
  @Inject private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.SERVERLESS_AWS_LAMBDA_DEPLOY_V2.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public long getTimeout(Ambiance ambiance, StepElementParameters stepElementParameters) {
    return Timeout.fromString((String) stepElementParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis();
  }

  @Override
  public UnitStep getSerialisedStep(Ambiance ambiance, StepElementParameters stepElementParameters, String accountId,
      String logKey, long timeout, String parkedTaskId) {
    ServerlessAwsLambdaDeployV2StepParameters serverlessAwsLambdaDeployV2StepParameters =
        (ServerlessAwsLambdaDeployV2StepParameters) stepElementParameters.getSpec();

    // Check if image exists
    serverlessStepCommonHelper.verifyPluginImageIsProvider(serverlessAwsLambdaDeployV2StepParameters.getImage());

    return getUnitStep(
        ambiance, stepElementParameters, accountId, logKey, parkedTaskId, serverlessAwsLambdaDeployV2StepParameters);
  }

  public UnitStep getUnitStep(Ambiance ambiance, StepElementParameters stepElementParameters, String accountId,
      String logKey, String parkedTaskId,
      ServerlessAwsLambdaDeployV2StepParameters serverlessAwsLambdaDeployV2StepParameters) {
    return ContainerUnitStepUtils.serializeStepWithStepParameters(
        getPort(ambiance, stepElementParameters.getIdentifier()), parkedTaskId, logKey,
        stepElementParameters.getIdentifier(), getTimeout(ambiance, stepElementParameters), accountId,
        stepElementParameters.getName(), delegateCallbackTokenSupplier, ambiance, new HashMap<>(),
        serverlessAwsLambdaDeployV2StepParameters.getImage().getValue(), Collections.EMPTY_LIST);
  }

  @Override
  public StepResponse.StepOutcome getAnyOutComeForStep(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    String instances = null;
    String serviceName = null;

    // If any of the responses are in serialized format, deserialize them
    containerStepExecutionResponseHelper.deserializeResponse(responseDataMap);

    StepStatusTaskResponseData stepStatusTaskResponseData =
        containerStepExecutionResponseHelper.filterK8StepResponse(responseDataMap);

    StepResponse.StepOutcome stepOutcome = null;

    if (stepStatusTaskResponseData == null) {
      log.info("Serverless Aws Lambda Deploy :  Received stepStatusTaskResponseData as null");
    } else {
      log.info(String.format("Serverless Aws Lambda Deploy V2:  Received stepStatusTaskResponseData with status %s",
          stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus()));
    }

    if (stepStatusTaskResponseData != null
        && stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus() == StepExecutionStatus.SUCCESS) {
      StepOutput stepOutput = stepStatusTaskResponseData.getStepStatus().getOutput();

      if (stepOutput instanceof StepMapOutput) {
        StepMapOutput stepMapOutput = (StepMapOutput) stepOutput;
        String instancesByte64 = stepMapOutput.getMap().get("serverlessInstances");
        log.info(String.format("Serverless Aws Lambda Deploy V2 instances byte64 %s", instancesByte64));
        instances = serverlessStepCommonHelper.convertByte64ToString(instancesByte64);
        log.info(String.format("Serverless Aws Lambda Deploy V2 instances %s", instances));
      }

      List<ServerInstanceInfo> serverInstanceInfoList = null;
      try {
        log.info(String.format("Serverless Aws Lambda Deploy V2: Parsing instances from JSON %s", instances));
        ServerlessAwsLambdaFunctionsWithServiceName serverlessAwsLambdaFunctionsWithServiceName =
            serverlessStepCommonHelper.getServerlessAwsLambdaFunctionsWithServiceName(instances);
        List<ServerlessAwsLambdaFunction> serverlessAwsLambdaFunctions =
            serverlessAwsLambdaFunctionsWithServiceName.getServerlessAwsLambdaFunctions();
        serviceName = serverlessAwsLambdaFunctionsWithServiceName.getServiceName();
        ServerlessAwsLambdaInfrastructureOutcome infrastructureOutcome =
            (ServerlessAwsLambdaInfrastructureOutcome) serverlessStepCommonHelper.getInfrastructureOutcome(ambiance);
        serverInstanceInfoList = serverlessStepCommonHelper.getServerlessDeployFunctionInstanceInfo(
            serverlessAwsLambdaFunctions, infrastructureOutcome.getRegion(), infrastructureOutcome.getStage(),
            serviceName, infrastructureOutcome.getInfrastructureKey());
      } catch (Exception e) {
        log.error("Error while parsing Serverless Aws Lambda Deploy V2 instances", e);
      }

      if (serverInstanceInfoList != null) {
        log.info("Saving Serverless Aws Lambda V2 server instances into sweeping output");
        stepOutcome = instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);
      } else {
        log.info("No instances were received in Serverless Aws Lambda Deploy V2 Response");
      }
    }

    return stepOutcome;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // we need to check if rbac check is req or not.
  }
}
