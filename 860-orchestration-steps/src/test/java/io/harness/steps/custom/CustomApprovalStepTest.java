/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.custom;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.exception.ApprovalStepNGException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.custom.CustomApprovalInstanceHandler;
import io.harness.steps.approval.step.custom.CustomApprovalOutcome;
import io.harness.steps.approval.step.custom.CustomApprovalSpecParameters;
import io.harness.steps.approval.step.custom.CustomApprovalStep;
import io.harness.steps.approval.step.custom.beans.CustomApprovalResponseData;
import io.harness.steps.approval.step.custom.beans.CustomApprovalTicketNG;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.steps.shellscript.ShellType;
import io.harness.tasks.ResponseData;
import io.harness.yaml.core.timeout.Timeout;

import com.google.common.collect.ImmutableMap;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDC)
@RunWith(MockitoJUnitRunner.class)
public class CustomApprovalStepTest extends CategoryTest {
  @Mock ApprovalInstanceService approvalInstanceService;
  @Mock private CustomApprovalInstanceHandler customApprovalInstanceHandler;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @InjectMocks private CustomApprovalStep customApprovalStep;
  private ILogStreamingStepClient logStreamingStepClient;

  @Before
  public void setup() {
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testExecuteSync() {
    StepElementParameters stepElementParameters =
        StepElementParameters.builder()
            .type(ApprovalType.CUSTOM_APPROVAL.name())
            .spec(CustomApprovalSpecParameters.builder()
                      .shellType(ShellType.Bash)
                      .source(ShellScriptSourceWrapper.builder()
                                  .spec(ShellScriptInlineSource.builder()
                                            .script(ParameterField.createValueField("echo 'HELLO WORLD!'"))
                                            .build())
                                  .build())
                      .scriptTimeout(ParameterField.createValueField(Timeout.fromString("10m")))
                      .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                      .build())
            .build();
    Ambiance ambiance = Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build();
    when(approvalInstanceService.save(any()))
        .thenReturn(CustomApprovalInstance.fromStepParameters(ambiance, stepElementParameters));

    AsyncExecutableResponse response = customApprovalStep.executeAsync(ambiance, stepElementParameters, null, null);
    verify(logStreamingStepClient).openStream(ShellScriptTaskNG.COMMAND_UNIT);
    verify(approvalInstanceService).save(any());
    verify(customApprovalInstanceHandler).wakeup();
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testSyncResponseSuccess() {
    ApprovalInstance instance = CustomApprovalInstance.builder().build();
    instance.setStatus(ApprovalStatus.APPROVED);
    when(approvalInstanceService.get(anyString())).thenReturn(instance);
    ResponseData responseData =
        CustomApprovalResponseData.builder()
            .instanceId(UUID.randomUUID().toString())
            .ticket(CustomApprovalTicketNG.builder().fields(ImmutableMap.of("status", "APPROVED")).build())
            .build();

    StepResponse stepResponse =
        customApprovalStep.handleAsyncResponse(null, null, ImmutableMap.of("xyz", responseData));
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
    assertThat(stepResponse).isNotNull();
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getFailureInfo()).isNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);
    assertThat(stepResponse.getStepOutcomes().stream().findFirst().orElseThrow(IllegalStateException::new).getName())
        .isEqualTo("output");
    assertThat(stepResponse.getStepOutcomes().stream().findFirst().orElseThrow(IllegalStateException::new).getOutcome())
        .isEqualTo(CustomApprovalOutcome.builder().outputVariables(ImmutableMap.of("status", "APPROVED")).build());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testSyncResponseFailure() {
    ApprovalInstance instance = CustomApprovalInstance.builder().build();
    instance.setStatus(ApprovalStatus.FAILED);
    instance.setErrorMessage("Custom Approval has no output fields. At least one output field must be set");
    when(approvalInstanceService.get(anyString())).thenReturn(instance);
    ResponseData responseData = CustomApprovalResponseData.builder().instanceId(UUID.randomUUID().toString()).build();

    assertThatThrownBy(() -> customApprovalStep.handleAsyncResponse(null, null, ImmutableMap.of("xyz", responseData)))
        .isInstanceOf(ApprovalStepNGException.class)
        .hasMessage("Custom Approval has no output fields. At least one output field must be set");
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testAbort() {
    Ambiance ambiance = Ambiance.newBuilder().setMetadata(ExecutionMetadata.newBuilder().build()).build();
    customApprovalStep.handleAbort(ambiance, null, null);
    verify(approvalInstanceService).expireByNodeExecutionId(any());
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }
}
