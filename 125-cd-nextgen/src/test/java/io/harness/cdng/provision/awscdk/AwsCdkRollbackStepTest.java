/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;

import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_STACK_NAMES;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.provision.awscdk.beans.AwsCdkConfig;
import io.harness.cdng.provision.awscdk.beans.AwsCdkOutcome;
import io.harness.cdng.provision.awscdk.beans.ContainerResourceConfig;
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

import java.util.Collections;
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
public class AwsCdkRollbackStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private SerializedResponseDataHelper serializedResponseDataHelper;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private CIDelegateTaskExecutor taskExecutor;
  @Mock private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;
  @Mock private KryoSerializer referenceFalseKryoSerializer;
  @Mock private OutcomeService outcomeService;
  @Mock private ContainerPortHelper containerPortHelper;
  @Mock private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private PluginUtils pluginUtils;
  @Mock private AwsCdkHelper awsCdkStepHelper;
  @Mock private AwsCdkConfigDAL awsCdkConfigDAL;
  @InjectMocks AwsCdkRollbackStep awsCdkRollbackStep;

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
            .spec(AwsCdkRollbackStepParameters.infoBuilder()
                      .provisionerIdentifier(ParameterField.<String>builder().value("provisionerIdentifier").build())
                      .build())
            .build();
    doReturn(AwsCdkConfig.builder().image("image").build()).when(awsCdkConfigDAL).getRollbackAwsCdkConfig(any(), any());
    MockedStatic<ContainerUnitStepUtils> containerUnitStepUtils = mockStatic(ContainerUnitStepUtils.class);
    PowerMockito
        .when(ContainerUnitStepUtils.serializeStepWithStepParameters(
            any(), any(), any(), any(), anyLong(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(unitStep);

    UnitStep result =
        awsCdkRollbackStep.getSerialisedStep(ambiance, stepElementParameters, "accountId", "logKey", 6000, "taskId");

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

    assertThat(awsCdkRollbackStep.getTimeout(ambiance, stepElementParameters)).isEqualTo(1200000L);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetAnyOutComeForStep() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("identifier")
            .name("stepName")
            .timeout(ParameterField.<String>builder().value("20m").build())
            .spec(AwsCdkRollbackStepParameters.infoBuilder()
                      .provisionerIdentifier(ParameterField.<String>builder().value("provisionerIdentifier").build())
                      .envVariables(ParameterField.<Map<String, String>>builder()
                                        .value(Collections.singletonMap("key1", "value1"))
                                        .build())
                      .build())
            .build();

    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .output(StepMapOutput.builder().output("test", "notEncodedValue").build())
                            .build())
            .build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("test", stepStatusTaskResponseData);
    Map<String, String> processedOutput = new HashMap<>();
    processedOutput.put("GIT_COMMIT_ID", "testvaluee");
    processedOutput.put("CDK_OUTPUT", "testvaluee");
    doReturn(StepResponse.builder().status(Status.SUCCEEDED).build())
        .when(containerStepExecutionResponseHelper)
        .handleAsyncResponseInternal(any(), any(), any());
    doReturn(stepStatusTaskResponseData).when(containerStepExecutionResponseHelper).filterK8StepResponse(any());
    doReturn(processedOutput).when(awsCdkStepHelper).processOutput(any());

    StepResponse.StepOutcome outcome =
        awsCdkRollbackStep.getAnyOutComeForStep(ambiance, stepElementParameters, responseDataMap);
    assertThat(((AwsCdkOutcome) outcome.getOutcome()).get("GIT_COMMIT_ID")).isEqualTo("testvaluee");
    assertThat(((AwsCdkOutcome) outcome.getOutcome()).get("CDK_OUTPUT")).isEqualTo("testvaluee");
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
            .spec(AwsCdkRollbackStepParameters.infoBuilder()
                      .provisionerIdentifier(ParameterField.<String>builder().value("provisionerIdentifier").build())
                      .envVariables(ParameterField.<Map<String, String>>builder()
                                        .value(Collections.singletonMap("key1", "value1"))
                                        .build())
                      .build())
            .build();
    ContainerResourceConfig containerResourceConfig = ContainerResourceConfig.builder().build();
    Map<String, String> envVariables = new HashMap<>();
    envVariables.put(PLUGIN_AWS_CDK_STACK_NAMES, "stack1 stack2");
    AwsCdkConfig savedAwsCdkConfig = AwsCdkConfig.builder()
                                         .image("image")
                                         .provisionerIdentifier("provisionerIdentifier")
                                         .connectorRef("connectorRef")
                                         .imagePullPolicy(ImagePullPolicy.ALWAYS)
                                         .privileged(true)
                                         .runAsUser(1)
                                         .resources(containerResourceConfig)
                                         .envVariables(envVariables)
                                         .commitId("testvaluee")
                                         .build();
    ArgumentCaptor<Map<String, ResponseData>> captor = ArgumentCaptor.forClass(Map.class);
    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .output(StepMapOutput.builder().output("test", "notEncodedValue").build())
                            .build())
            .build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("test", stepStatusTaskResponseData);

    doReturn(StepResponse.builder().status(Status.SUCCEEDED).build())
        .when(containerStepExecutionResponseHelper)
        .handleAsyncResponseInternal(any(), any(), any());
    doReturn(stepStatusTaskResponseData).when(containerStepExecutionResponseHelper).filterK8StepResponse(any());
    doReturn(savedAwsCdkConfig).when(awsCdkConfigDAL).getRollbackAwsCdkConfig(any(), any());

    awsCdkRollbackStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);

    verify(containerStepExecutionResponseHelper).handleAsyncResponseInternal(any(), captor.capture(), any());
    verify(awsCdkConfigDAL).deleteAwsCdkConfig(eq(ambiance), eq("provisionerIdentifier"));
    verify(awsCdkConfigDAL, times(1)).getRollbackAwsCdkConfig(any(), any());
    verify(awsCdkConfigDAL, times(1)).deleteAwsCdkConfig(any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseSkip() {
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("identifier")
            .name("stepName")
            .timeout(ParameterField.<String>builder().value("20m").build())
            .spec(AwsCdkRollbackStepParameters.infoBuilder()
                      .provisionerIdentifier(ParameterField.<String>builder().value("provisionerIdentifier").build())
                      .envVariables(ParameterField.<Map<String, String>>builder()
                                        .value(Collections.singletonMap("key1", "value1"))
                                        .build())
                      .build())
            .build();
    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(StepStatus.builder()
                            .stepExecutionStatus(StepExecutionStatus.SUCCESS)
                            .output(StepMapOutput.builder().output("test", "notEncodedValue").build())
                            .build())
            .build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    responseDataMap.put("test", stepStatusTaskResponseData);
    Map<String, String> processedOutput = new HashMap<>();
    processedOutput.put("GIT_COMMIT_ID", "testvaluee");
    processedOutput.put("CDK_OUTPUT", "testvaluee");
    doReturn(StepResponse.builder().status(Status.SUCCEEDED).build())
        .when(containerStepExecutionResponseHelper)
        .handleAsyncResponseInternal(any(), any(), any());
    doReturn(stepStatusTaskResponseData).when(containerStepExecutionResponseHelper).filterK8StepResponse(any());
    doReturn(processedOutput).when(awsCdkStepHelper).processOutput(any());
    doReturn(null).when(awsCdkConfigDAL).getRollbackAwsCdkConfig(any(), any());

    StepResponse stepResponse =
        awsCdkRollbackStep.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SKIPPED);
    verify(awsCdkConfigDAL).getRollbackAwsCdkConfig(any(), any());
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
