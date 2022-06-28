/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ShellExecutionData;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({StepUtils.class})
@OwnedBy(HarnessTeam.PIPELINE)
public class ShellScriptStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private KryoSerializer kryoSerializer;
  @Mock private StepHelper stepHelper;
  @Mock private ShellScriptHelperService shellScriptHelperService;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @InjectMocks private ShellScriptStep shellScriptStep;

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
        .build();
  }

  @Before
  public void setup() {
    LogStreamingStepClientImpl logClient = mock(LogStreamingStepClientImpl.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logClient);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testObtainTask() {
    Ambiance ambiance = buildAmbiance();
    ShellScriptStepParameters stepParameters = ShellScriptStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();
    ShellScriptTaskParametersNG taskParametersNG = ShellScriptTaskParametersNG.builder().build();
    doReturn(taskParametersNG)
        .when(shellScriptHelperService)
        .buildShellScriptTaskParametersNG(ambiance, stepParameters);
    MockedStatic<StepUtils> aStatic = Mockito.mockStatic(StepUtils.class);
    aStatic.when(() -> StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());

    TaskRequest taskRequest = shellScriptStep.obtainTask(ambiance, stepElementParameters, null);
    assertThat(taskRequest).isNotNull();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testHandleTaskResultForFailedTask() throws Exception {
    Ambiance ambiance = buildAmbiance();
    ShellScriptStepParameters stepParameters = ShellScriptStepParameters.infoBuilder().build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();
    ShellScriptTaskResponseNG taskResponseNG =
        ShellScriptTaskResponseNG.builder().status(CommandExecutionStatus.FAILURE).errorMessage("Failed").build();

    StepResponse stepResponse = shellScriptStep.handleTaskResult(ambiance, stepElementParameters, () -> taskResponseNG);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    assertThat(stepResponse.getFailureInfo().getErrorMessage()).isEqualTo("Failed");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testHandleTaskResultForSuccessTask() throws Exception {
    Ambiance ambiance = buildAmbiance();
    Map<String, Object> outputVariables = new HashMap<>();
    ShellScriptStepParameters stepParameters =
        ShellScriptStepParameters.infoBuilder().outputVariables(outputVariables).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();
    Map<String, String> envVariables = new HashMap<>();

    ExecuteCommandResponse executeCommandResponse =
        ExecuteCommandResponse.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .commandExecutionData(ShellExecutionData.builder().sweepingOutputEnvVariables(envVariables).build())
            .build();
    ShellScriptTaskResponseNG successResponse = ShellScriptTaskResponseNG.builder()
                                                    .status(CommandExecutionStatus.SUCCESS)
                                                    .executeCommandResponse(executeCommandResponse)
                                                    .build();
    doReturn(null).when(shellScriptHelperService).prepareShellScriptOutcome(envVariables, outputVariables);

    StepResponse stepResponse =
        shellScriptStep.handleTaskResult(ambiance, stepElementParameters, () -> successResponse);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isEmpty();

    ShellScriptOutcome shellScriptOutcome = ShellScriptOutcome.builder().build();
    doReturn(shellScriptOutcome)
        .when(shellScriptHelperService)
        .prepareShellScriptOutcome(envVariables, outputVariables);
    stepResponse = shellScriptStep.handleTaskResult(ambiance, stepElementParameters, () -> successResponse);
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    assertThat(((List<StepOutcome>) stepResponse.getStepOutcomes()).get(0).getOutcome()).isEqualTo(shellScriptOutcome);
  }
}
