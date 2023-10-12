/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.awscdk;

import static io.harness.cdng.provision.awscdk.AwsCdkEnvironmentVariables.PLUGIN_AWS_CDK_STACK_NAMES;
import static io.harness.cdng.provision.awscdk.AwsCdkHelper.CDK_OUTPUT_OUTCOME_NAME;
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
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.provision.ProvisionerOutputHelper;
import io.harness.cdng.provision.awscdk.beans.AwsCdkConfig;
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
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;
import io.harness.utils.PluginUtils;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.extended.ci.container.ContainerResource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
  @Mock private OutcomeService outcomeService;
  @Mock private ContainerPortHelper containerPortHelper;
  @Mock private Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private PluginUtils pluginUtils;
  @Mock private AwsCdkHelper awsCdkStepHelper;
  @Mock private AwsCdkConfigDAL awsCdkConfigDAL;
  @Mock private ProvisionerOutputHelper provisionerOutputHelper;

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
  public void testGetAnyOutComeForStep() {
    Ambiance ambiance = getAmbiance();
    ContainerResource containerResource =
        ContainerResource.builder()
            .requests(ContainerResource.Limits.builder()
                          .memory(ParameterField.<String>builder().value("400").build())
                          .cpu(ParameterField.<String>builder().value("300").build())
                          .build())
            .limits(ContainerResource.Limits.builder()
                        .memory(ParameterField.<String>builder().value("200").build())
                        .cpu(ParameterField.<String>builder().value("100").build())
                        .build())
            .build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("identifier")
            .name("stepName")
            .timeout(ParameterField.<String>builder().value("20m").build())
            .spec(
                AwsCdkDeployStepParameters.infoBuilder()
                    .image(ParameterField.<String>builder().value("image").build())
                    .provisionerIdentifier(ParameterField.<String>builder().value("provisionerIdentifier").build())
                    .connectorRef(ParameterField.<String>builder().value("connectorRef").build())
                    .imagePullPolicy(ParameterField.<ImagePullPolicy>builder().value(ImagePullPolicy.ALWAYS).build())
                    .privileged(ParameterField.<Boolean>builder().value(true).build())
                    .runAsUser(ParameterField.<Integer>builder().value(1).build())
                    .resources(containerResource)
                    .stackNames(ParameterField.<List<String>>builder().value(Arrays.asList("stack1", "stack2")).build())
                    .build())
            .build();
    ArgumentCaptor<AwsCdkConfig> configCaptor = ArgumentCaptor.forClass(AwsCdkConfig.class);
    StepMapOutput stepMapOutput = StepMapOutput.builder().build();
    Map<String, String> stepMapOutputMap = new HashMap();
    stepMapOutputMap.put("GIT_COMMIT_ID", "testvaluee");
    stepMapOutputMap.put(CDK_OUTPUT_OUTCOME_NAME, "testvaluee");
    stepMapOutput.setMap(stepMapOutputMap);
    StepStatusTaskResponseData stepStatusTaskResponseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(
                StepStatus.builder().stepExecutionStatus(StepExecutionStatus.SUCCESS).output(stepMapOutput).build())
            .build();
    Map<String, ResponseData> responseDataMap = new HashMap<>();
    Map<String, String> processedOutput = new HashMap<>();
    Map<String, String> envVariables = new HashMap<>();
    envVariables.put("key1", "value1");
    responseDataMap.put("test", stepStatusTaskResponseData);
    doReturn(StepResponse.builder().status(Status.SUCCEEDED).build())
        .when(containerStepExecutionResponseHelper)
        .handleAsyncResponseInternal(any(), any(), any());
    doReturn(stepStatusTaskResponseData).when(containerStepExecutionResponseHelper).filterK8StepResponse(any());
    doReturn(processedOutput).when(awsCdkStepHelper).processOutput(any());
    doReturn(envVariables).when(awsCdkStepHelper).getCommonEnvVariables(any(), any(), any());

    StepOutcome outcome = awsCdkDeployStep.getAnyOutComeForStep(ambiance, stepElementParameters, responseDataMap);

    verify(awsCdkConfigDAL).saveAwsCdkConfig(configCaptor.capture());
    assertThat(outcome.getOutcome()).isEqualTo(processedOutput);
    verify(awsCdkConfigDAL).getRollbackAwsCdkConfig(any(), any());
    verify(awsCdkStepHelper).processOutput(any());
    verify(provisionerOutputHelper).saveProvisionerOutputByStepIdentifier(any(), any());

    AwsCdkConfig awsCdkConfig = configCaptor.getValue();
    assertThat(awsCdkConfig.getAccountId()).isEqualTo("test-account");
    assertThat(awsCdkConfig.getOrgId()).isEqualTo("org");
    assertThat(awsCdkConfig.getProjectId()).isEqualTo("project");
    assertThat(awsCdkConfig.getStageExecutionId()).isEqualTo("stageExecutionId");
    assertThat(awsCdkConfig.getProvisionerIdentifier()).isEqualTo("provisionerIdentifier");
    assertThat(awsCdkConfig.getResources().getLimits().getCpu()).isEqualTo("100");
    assertThat(awsCdkConfig.getResources().getLimits().getMemory()).isEqualTo("200");
    assertThat(awsCdkConfig.getResources().getRequests().getCpu()).isEqualTo("300");
    assertThat(awsCdkConfig.getResources().getRequests().getMemory()).isEqualTo("400");
    assertThat(awsCdkConfig.getRunAsUser()).isEqualTo(1);
    assertThat(awsCdkConfig.getConnectorRef()).isEqualTo("connectorRef");
    assertThat(awsCdkConfig.getImage()).isEqualTo("image");
    assertThat(awsCdkConfig.getImagePullPolicy()).isEqualTo(ImagePullPolicy.ALWAYS);
    assertThat(awsCdkConfig.getPrivileged()).isEqualTo(true);
    assertThat(awsCdkConfig.getEnvVariables().get("key1")).isEqualTo("value1");
    assertThat(awsCdkConfig.getEnvVariables().get(PLUGIN_AWS_CDK_STACK_NAMES)).isEqualTo("stack1 stack2");
    assertThat(awsCdkConfig.getCommitId()).isEqualTo("testvaluee");
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
