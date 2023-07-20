/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless.container.steps;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.aws.sam.AwsSamStepHelper;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunctionsWithServiceName;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ServerlessAwsLambdaDeployStepV2Test extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ServerlessStepCommonHelper serverlessStepCommonHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private InstanceInfoService instanceInfoService;

  @Mock private AwsSamStepHelper awsSamStepHelper;

  @Mock private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;

  @InjectMocks @Spy private ServerlessAwsLambdaDeployV2Step serverlessAwsLambdaDeployV2Step;

  @Before
  public void setup() {}

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetAnyOutComeForStep() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    ServerlessAwsLambdaDeployV2StepParameters stepParameters =
        ServerlessAwsLambdaDeployV2StepParameters.infoBuilder()
            .image(ParameterField.<String>builder().value("sdaf").build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    Map<String, String> resultMap = new HashMap<>();
    String instancesContentBase64 = "content64";
    String instanceContent = "content";

    List<ServerlessAwsLambdaFunction> serverlessAwsLambdaFunctions = Collections.emptyList();
    ServerlessAwsLambdaFunctionsWithServiceName serverlessAwsLambdaFunctionsWithServiceName =
        ServerlessAwsLambdaFunctionsWithServiceName.builder()
            .serviceName("service")
            .serverlessAwsLambdaFunctions(serverlessAwsLambdaFunctions)
            .build();
    resultMap.put("serverlessInstances", instancesContentBase64);
    StepMapOutput stepMapOutput = StepMapOutput.builder().map(resultMap).build();
    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(
                StepStatus.builder().stepExecutionStatus(StepExecutionStatus.SUCCESS).output(stepMapOutput).build())
            .build();
    doReturn(stepStatusTaskResponseData).when(containerStepExecutionResponseHelper).filterK8StepResponse(any());
    responseDataMap.put("key", stepStatusTaskResponseData);
    when(serverlessStepCommonHelper.convertByte64ToString(instancesContentBase64)).thenReturn(instanceContent);
    when(serverlessStepCommonHelper.getServerlessAwsLambdaFunctionsWithServiceName(instanceContent))
        .thenReturn(serverlessAwsLambdaFunctionsWithServiceName);

    ServerlessAwsLambdaInfrastructureOutcome serverlessAwsLambdaInfrastructureOutcome =
        ServerlessAwsLambdaInfrastructureOutcome.builder()
            .stage("stage")
            .region("regjion")
            .infrastructureKey("infraKey")
            .build();
    when(serverlessStepCommonHelper.getInfrastructureOutcome(ambiance))
        .thenReturn(serverlessAwsLambdaInfrastructureOutcome);

    List<ServerInstanceInfo> serverInstanceInfoList = Collections.emptyList();
    when(serverlessStepCommonHelper.getServerlessDeployFunctionInstanceInfo(any(), any(), any(), any(), any()))
        .thenReturn(serverInstanceInfoList);
    StepOutcome stepOutcome = mock(StepOutcome.class);
    when(instanceInfoService.saveServerInstancesIntoSweepingOutput(any(), any())).thenReturn(stepOutcome);
    assertThat(serverlessAwsLambdaDeployV2Step.getAnyOutComeForStep(ambiance, stepElementParameters, responseDataMap))
        .isEqualTo(stepOutcome);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetSerialisedStep() {
    String accountId = "accountId";
    int port = 1;
    String callbackToken = "token";
    String displayName = "name";
    String id = "id";
    String logKey = "logKey";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    doReturn(1).when(serverlessAwsLambdaDeployV2Step).getPort(any(), any());
    doReturn(122L).when(serverlessAwsLambdaDeployV2Step).getTimeout(any(), any());
    UnitStep unitStep = mock(UnitStep.class);
    doReturn(accountId).when(unitStep).getAccountId();
    doReturn(port).when(unitStep).getContainerPort();
    doReturn(callbackToken).when(unitStep).getCallbackToken();
    doReturn(displayName).when(unitStep).getDisplayName();
    doReturn(id).when(unitStep).getId();
    doReturn(logKey).when(unitStep).getLogKey();
    doReturn(unitStep).when(serverlessAwsLambdaDeployV2Step).getUnitStep(any(), any(), any(), any(), any(), any());

    ServerlessAwsLambdaDeployV2StepParameters stepParameters =
        ServerlessAwsLambdaDeployV2StepParameters.infoBuilder()
            .image(ParameterField.<String>builder().value("sdaf").build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();
    long timeout = 1000;
    String parkedTaskId = "parkedTaskId";
    UnitStep unit = serverlessAwsLambdaDeployV2Step.getSerialisedStep(
        ambiance, stepElementParameters, accountId, logKey, timeout, parkedTaskId);
    assertThat(unit.getContainerPort()).isEqualTo(port);
    assertThat(unit.getAccountId()).isEqualTo(accountId);
    assertThat(unit.getCallbackToken()).isEqualTo(callbackToken);
    assertThat(unit.getDisplayName()).isEqualTo(displayName);
    assertThat(unit.getId()).isEqualTo(id);
    assertThat(unit.getLogKey()).isEqualTo(logKey);
  }
}