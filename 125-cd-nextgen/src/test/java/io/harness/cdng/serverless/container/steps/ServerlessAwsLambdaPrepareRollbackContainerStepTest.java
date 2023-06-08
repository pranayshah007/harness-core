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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.cdng.serverless.beans.StackDetails;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.HashMap;
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
public class ServerlessAwsLambdaPrepareRollbackContainerStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ServerlessStepCommonHelper serverlessStepCommonHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @InjectMocks
  @Spy
  private ServerlessAwsLambdaPrepareRollbackContainerStep serverlessAwsLambdaPrepareRollbackContainerStep;

  @Before
  public void setup() {}

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetAnyOutComeForStep() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    ServerlessAwsLambdaPrepareRollbackContainerStepParameters stepParameters =
        ServerlessAwsLambdaPrepareRollbackContainerStepParameters.infoBuilder()
            .image(ParameterField.<String>builder().value("sdaf").build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    Map<String, String> resultMap = new HashMap<>();
    String contentBase64 = "content64";
    String content = "content";
    resultMap.put("stackDetails", contentBase64);
    StepMapOutput stepMapOutput = StepMapOutput.builder().map(resultMap).build();
    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(
                StepStatus.builder().stepExecutionStatus(StepExecutionStatus.SUCCESS).output(stepMapOutput).build())
            .build();
    responseDataMap.put("key", stepStatusTaskResponseData);

    StackDetails stackDetails = StackDetails.builder().build();
    when(serverlessStepCommonHelper.convertByte64ToString(contentBase64)).thenReturn(content);
    when(serverlessStepCommonHelper.getStackDetails(content)).thenReturn(stackDetails);

    serverlessAwsLambdaPrepareRollbackContainerStep.getAnyOutComeForStep(
        ambiance, stepElementParameters, responseDataMap);
    verify(executionSweepingOutputService, times(1)).consume(any(), any(), any(), any());
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

    doReturn(1).when(serverlessAwsLambdaPrepareRollbackContainerStep).getPort(any(), any());
    doReturn(122L).when(serverlessAwsLambdaPrepareRollbackContainerStep).getTimeout(any(), any());
    UnitStep unitStep = mock(UnitStep.class);
    doReturn(accountId).when(unitStep).getAccountId();
    doReturn(port).when(unitStep).getContainerPort();
    doReturn(callbackToken).when(unitStep).getCallbackToken();
    doReturn(displayName).when(unitStep).getDisplayName();
    doReturn(id).when(unitStep).getId();
    doReturn(logKey).when(unitStep).getLogKey();
    doReturn(unitStep)
        .when(serverlessAwsLambdaPrepareRollbackContainerStep)
        .getUnitStep(any(), any(), any(), any(), any(), any());

    ServerlessAwsLambdaPrepareRollbackContainerStepParameters stepParameters =
        ServerlessAwsLambdaPrepareRollbackContainerStepParameters.infoBuilder()
            .image(ParameterField.<String>builder().value("sdaf").build())
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    long timeout = 1000;
    String parkedTaskId = "parkedTaskId";
    UnitStep unit = serverlessAwsLambdaPrepareRollbackContainerStep.getSerialisedStep(
        ambiance, stepElementParameters, accountId, logKey, timeout, parkedTaskId);
    assertThat(unit.getContainerPort()).isEqualTo(port);
    assertThat(unit.getAccountId()).isEqualTo(accountId);
    assertThat(unit.getCallbackToken()).isEqualTo(callbackToken);
    assertThat(unit.getDisplayName()).isEqualTo(displayName);
    assertThat(unit.getId()).isEqualTo(id);
    assertThat(unit.getLogKey()).isEqualTo(logKey);
  }
}