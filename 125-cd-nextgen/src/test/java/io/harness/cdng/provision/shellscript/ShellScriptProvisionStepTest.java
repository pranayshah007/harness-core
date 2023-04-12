/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.shellscript;

import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.ProvisionerOutputHelper;
import io.harness.cdng.ssh.SshCommandStepHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.shell.provisioner.ShellScriptProvisionTaskNGRequest;
import io.harness.delegate.task.shell.provisioner.ShellScriptProvisionTaskNGResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

@RunWith(MockitoJUnitRunner.class)
@PrepareForTest({StepUtils.class})
@OwnedBy(HarnessTeam.CDP)
public class ShellScriptProvisionStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private KryoSerializer kryoSerializer;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private StepHelper stepHelper;
  @Mock private SshCommandStepHelper sshCommandStepHelper;
  @Mock private ProvisionerOutputHelper provisionerOutputHelper;
  @InjectMocks private ShellScriptProvisionStep shellScriptProvisionStep;

  @Before
  public void setUpMocks() {
    doNothing().when(provisionerOutputHelper).saveProvisionerOutputByStepIdentifier(any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbac() {
    String script = "test";
    Map<String, Object> environmentVariables = new HashMap<>();
    environmentVariables.put("key1", "value1");
    environmentVariables.put("key2", ParameterField.builder().value("value2").build());
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("stepIdentifier")
            .spec(ShellScriptProvisionStepParameters.infoBuilder().environmentVariables(environmentVariables).build())
            .build();
    doReturn(script).when(sshCommandStepHelper).getShellScript(any(), any());
    Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenAnswer(invocation -> TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    TaskRequest taskRequest = shellScriptProvisionStep.obtainTaskAfterRbac(
        getAmbiance(), stepElementParameters, StepInputPackage.builder().build());

    assertThat(taskRequest).isNotNull();
    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(), any(), any(), any());
    TaskData taskData = taskDataArgumentCaptor.getValue();
    ShellScriptProvisionTaskNGRequest taskNGRequest = (ShellScriptProvisionTaskNGRequest) taskData.getParameters()[0];
    assertThat(taskNGRequest.getScriptBody()).isEqualTo(script);
    assertThat(taskNGRequest.getVariables().size()).isEqualTo(2);
    assertThat(taskNGRequest.getVariables().get("key1")).isEqualTo("value1");
    assertThat(taskNGRequest.getVariables().get("key2")).isEqualTo("value2");
    assertThat(taskNGRequest.getExecutionId()).isEqualTo("planExecutionId-stageExecutionId-stepIdentifier");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacEmptyScript() {
    Map<String, Object> environmentVariables = new HashMap<>();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .spec(ShellScriptProvisionStepParameters.infoBuilder().environmentVariables(environmentVariables).build())
            .build();
    doReturn("").when(sshCommandStepHelper).getShellScript(any(), any());

    assertThatThrownBy(()
                           -> shellScriptProvisionStep.obtainTaskAfterRbac(
                               getAmbiance(), stepElementParameters, StepInputPackage.builder().build()))
        .hasMessageContaining("Script cannot be empty or null");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testObtainTaskAfterRbacFailedNullVariable() {
    Map<String, Object> environmentVariables = new HashMap<>();
    environmentVariables.put("key1", ParameterField.builder().value(null).build());
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .identifier("stepIdentifier")
            .spec(ShellScriptProvisionStepParameters.infoBuilder().environmentVariables(environmentVariables).build())
            .build();
    doReturn("test").when(sshCommandStepHelper).getShellScript(any(), any());

    assertThatThrownBy(()
                           -> shellScriptProvisionStep.obtainTaskAfterRbac(
                               getAmbiance(), stepElementParameters, StepInputPackage.builder().build()))
        .hasMessageContaining("Env. variable [key1] value found to be null");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContext() throws Exception {
    ShellScriptProvisionTaskNGResponse shellScriptProvisionTaskNGResponse =
        ShellScriptProvisionTaskNGResponse.builder()
            .unitProgressData(UnitProgressData.builder().unitProgresses(new ArrayList<>()).build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .output(
                "{\"Instances\":[{\"Hostname\": \"host1\",\"value\": \"value1\"},{\"Hostname\": \"host2\",\"value\": \"value2\"}]}")
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .identifier("stepIdentifier")
                                                      .spec(ShellScriptProvisionStepParameters.infoBuilder().build())
                                                      .build();

    StepResponse response = shellScriptProvisionStep.handleTaskResultWithSecurityContext(
        getAmbiance(), stepElementParameters, () -> shellScriptProvisionTaskNGResponse);

    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(((StepResponse.StepOutcome) response.getStepOutcomes().toArray()[0]).getOutcome())
        .isInstanceOf(ShellScriptProvisionOutcome.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContextFailure() throws Exception {
    ShellScriptProvisionTaskNGResponse shellScriptProvisionTaskNGResponse =
        ShellScriptProvisionTaskNGResponse.builder()
            .unitProgressData(UnitProgressData.builder().unitProgresses(new ArrayList<>()).build())
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage("errorMessage")
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .identifier("stepIdentifier")
                                                      .spec(ShellScriptProvisionStepParameters.infoBuilder().build())
                                                      .build();

    StepResponse response = shellScriptProvisionStep.handleTaskResultWithSecurityContext(
        getAmbiance(), stepElementParameters, () -> shellScriptProvisionTaskNGResponse);

    assertThat(response.getStatus()).isEqualTo(Status.FAILED);
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions("accountId", "test-account")
        .putSetupAbstractions("projectIdentifier", "test-project")
        .putSetupAbstractions("orgIdentifier", "test-org")
        .setPlanExecutionId("planExecutionId")
        .setStageExecutionId("stageExecutionId")
        .build();
  }
}
