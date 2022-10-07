/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customDeployment;

import static io.harness.rule.OwnerRule.RISHABH;

import static software.wings.beans.TaskType.SHELL_SCRIPT_TASK_NG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.CustomDeploymentInfrastructureOutcome;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.shell.ScriptType;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.environment.EnvironmentOutcome;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class FetchInstanceScriptStepTest extends CDNGTestBase {
  @Mock private CDStepHelper cdStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @InjectMocks private FetchInstanceScriptStep fetchInstanceScriptStep;

  private FetchInstanceScriptStepParameters parameters =
      FetchInstanceScriptStepParameters.infoBuilder()
          .delegateSelectors(ParameterField.createValueField(List.of(new TaskSelectorYaml("selector-1"))))
          .build();
  private final StepElementParameters stepElementParameters = StepElementParameters.builder().spec(parameters).build();

  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();
  private final CustomDeploymentInfrastructureOutcome infrastructure =
      CustomDeploymentInfrastructureOutcome.builder()
          .instancesListPath("hosts")
          .instanceAttributes(Map.of("hostname", "host"))
          .instanceFetchScript("")
          .build();
  private final Ambiance ambiance = getAmbiance();
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Before
  public void setup() {
    ILogStreamingStepClient logStreamingStepClient;
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    Mockito.mockStatic(StepUtils.class);
    when(StepUtils.prepareCDTaskRequest(
             any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    doReturn(infrastructure).when(cdStepHelper).getInfrastructureOutcome(ambiance);
    CustomDeploymentInfrastructureOutcome customDeploymentInfrastructureOutcome =
        CustomDeploymentInfrastructureOutcome.builder()
            .infrastructureKey("1234")
            .environment(EnvironmentOutcome.builder().identifier("environmentId").build())
            .instanceFetchScript(infrastructure.getInstanceFetchScript())
            .instanceAttributes(infrastructure.getInstanceAttributes())
            .instancesListPath(infrastructure.getInstancesListPath())
            .variables(new HashMap<>())
            .build();

    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);
    doReturn(customDeploymentInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(any());

    TaskRequest taskRequest =
        fetchInstanceScriptStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    assertThat(taskRequest).isNotNull();
    log.info("{}", taskRequest);

    ShellScriptTaskParametersNG requestParameters =
        (ShellScriptTaskParametersNG) taskDataArgumentCaptor.getValue().getParameters()[0];
    log.info("{}", taskDataArgumentCaptor.getValue());

    assertThat(taskDataArgumentCaptor.getValue().getTaskType()).isEqualTo(SHELL_SCRIPT_TASK_NG.toString());
    assertThat(requestParameters.getScript()).isEqualTo(infrastructure.getInstanceFetchScript());
    assertThat(requestParameters.isExecuteOnDelegate()).isEqualTo(true);
    assertThat(requestParameters.getOutputVars())
        .isEqualTo(Collections.singletonList(FetchInstanceScriptStep.OUTPUT_PATH_KEY));
    assertThat(requestParameters.getAccountId()).isEqualTo("account");
    assertThat(requestParameters.getWorkingDirectory()).isEqualTo("/tmp");
    assertThat(requestParameters.getScriptType()).isEqualTo(ScriptType.BASH);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateResourcesFFEnabled() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_DEPLOYMENT_TEMPLATE));
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().type("FetchInstanceScript").spec(parameters).build();
    fetchInstanceScriptStep.validateResources(ambiance, stepElementParameters);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testValidateResourcesFFDisabled() {
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_DEPLOYMENT_TEMPLATE));
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().type("FetchInstanceScript").spec(parameters).build();
    assertThatThrownBy(() -> fetchInstanceScriptStep.validateResources(ambiance, stepElementParameters))
        .hasMessage(
            "Custom Deployment Template NG is not enabled for this account. Please contact harness customer care.");
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(fetchInstanceScriptStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }
}
