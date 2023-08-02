/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.execution.CIDelegateTaskExecutor;
import io.harness.helper.SerializedResponseDataHelper;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.sdk.core.plugin.ContainerPortHelper;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;
import io.harness.utils.PluginUtils;
import io.harness.waiter.WaitNotifyEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;

@OwnedBy(HarnessTeam.CDP)
public class AwsCdkDeployStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private CIDelegateTaskExecutor taskExecutor;
  @Mock private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;
  @Mock private KryoSerializer referenceFalseKryoSerializer;
  @Mock OutcomeService outcomeService;
  @Mock ContainerPortHelper containerPortHelper;
  @Mock Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock PluginUtils pluginUtils;
  @InjectMocks AwsCdkDeployStep awsCdkDeployStep;

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSerialisedStep() {
    Ambiance ambiance = getAmbiance();
    UnitStep unitStep = UnitStep.newBuilder().build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("identifier")
            .name("stepName")
            .timeout(ParameterField.<String>builder().value("20m").build())
            .spec(AwsCdkDeployStepParameters.infoBuilder()
                      .image(ParameterField.<String>builder().value("image").build())
                      .build())
            .build();
    MockedStatic<ContainerUnitStepUtils> containerUnitStepUtils = mockStatic(ContainerUnitStepUtils.class);
    PowerMockito
        .when(ContainerUnitStepUtils.serializeStepWithStepParameters(
            any(), any(), any(), any(), anyLong(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(unitStep);

    UnitStep result =
        awsCdkDeployStep.getSerialisedStep(ambiance, stepElementParameters, "accountId", "logKey", 6000, "taskId");

    containerUnitStepUtils.verify(
        ()
            -> ContainerUnitStepUtils.serializeStepWithStepParameters(eq(0), any(), eq("logKey"), eq("identifier"),
                eq(1200000L), eq("accountId"), eq("stepName"), any(), eq(ambiance), anyMap(), eq("image"), anyList()));
    containerUnitStepUtils.close();
    assertThat(result).isEqualTo(unitStep);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeout() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("identifier")
            .name("stepName")
            .timeout(ParameterField.<String>builder().value("20m").build())
            .spec(AwsCdkDeployStepParameters.infoBuilder()
                      .image(ParameterField.<String>builder().value("image").build())
                      .build())
            .build();

    assertThat(awsCdkDeployStep.getTimeout(ambiance, stepElementParameters)).isEqualTo(1200000L);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("identifier")
            .name("stepName")
            .timeout(ParameterField.<String>builder().value("20m").build())
            .spec(AwsCdkDeployStepParameters.infoBuilder()
                      .image(ParameterField.<String>builder().value("image").build())
                      .build())
            .build();
    ArgumentCaptor<Map<String, ResponseData>> captor = ArgumentCaptor.forClass(Map.class);
    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .output(StepMapOutput.builder().output("test", "dGVzdHZhbHVlZQ--").build())
                            .build())
            .build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("test", stepStatusTaskResponseData);
    doReturn(StepResponse.builder().status(Status.SUCCEEDED).build())
        .when(containerStepExecutionResponseHelper)
        .handleAsyncResponseInternal(any(), any(), any());
    doReturn(stepStatusTaskResponseData).when(containerStepExecutionResponseHelper).filterK8StepResponse(any());

    awsCdkDeployStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);

    verify(containerStepExecutionResponseHelper).handleAsyncResponseInternal(any(), captor.capture(), any());
    assertThat(
        ((StepMapOutput) ((StepStatusTaskResponseData) captor.getValue().get("test")).getStepStatus().getOutput())
            .getMap()
            .get("test"))
        .isEqualTo("testvaluee");
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "test-account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }
}
