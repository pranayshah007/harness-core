/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.custom;

import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.steps.approval.step.custom.evaluation.CustomApprovalCriteriaEvaluator.evaluateCriteria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.ApprovalStepNGException;
import io.harness.exception.HarnessCustomApprovalException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.servicenow.misc.TicketNG;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ShellExecutionData;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.beans.ConditionDTO;
import io.harness.steps.approval.step.beans.CriteriaSpecType;
import io.harness.steps.approval.step.beans.CriteriaSpecWrapperDTO;
import io.harness.steps.approval.step.beans.JexlCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.Operator;
import io.harness.steps.approval.step.custom.CustomApprovalInstanceHandler;
import io.harness.steps.approval.step.custom.beans.CustomApprovalTicketNG;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance;
import io.harness.steps.shellscript.ShellScriptHelperService;
import io.harness.steps.shellscript.ShellScriptOutcome;
import io.harness.steps.shellscript.ShellType;
import io.harness.tasks.BinaryResponseData;
import io.harness.yaml.core.timeout.Timeout;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

@OwnedBy(HarnessTeam.CDC)
public class CustomApprovalCallbackTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private KryoSerializer referenceFalseKryoSerializer;
  @Mock private ShellScriptHelperService shellScriptHelperService;
  @Mock private CustomApprovalInstanceHandler customApprovalInstanceHandler;
  @Mock private ApprovalInstanceService approvalInstanceService;
  @Mock private NGErrorHelper ngErrorHelper;
  private MockedStatic<ShellScriptHelperService> shellScriptHelperServiceMockedStatic;
  private CustomApprovalCallback customApprovalCallback;

  ILogStreamingStepClient logStreamingStepClient;
  Ambiance ambiance = Ambiance.newBuilder()
                          .putSetupAbstractions(SetupAbstractionKeys.accountId, "__ACCOUNT_ID__")
                          .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "__ORG__")
                          .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "__PROJ__")
                          .build();

  private final String APPROVAL_INSTANCE_ID = "__INSTANCE_ID__";

  @Before
  public void setup() {
    customApprovalCallback = spy(CustomApprovalCallback.builder().approvalInstanceId(APPROVAL_INSTANCE_ID).build());
    shellScriptHelperServiceMockedStatic = Mockito.mockStatic(ShellScriptHelperService.class, CALLS_REAL_METHODS);
    on(customApprovalCallback).set("logStreamingStepClientFactory", logStreamingStepClientFactory);
    on(customApprovalCallback).set("kryoSerializer", kryoSerializer);
    on(customApprovalCallback).set("referenceFalseKryoSerializer", referenceFalseKryoSerializer);
    on(customApprovalCallback).set("shellScriptHelperService", shellScriptHelperService);
    on(customApprovalCallback).set("customApprovalInstanceHandler", customApprovalInstanceHandler);
    on(customApprovalCallback).set("approvalInstanceService", approvalInstanceService);
    on(customApprovalCallback).set("ngErrorHelper", ngErrorHelper);

    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @After
  public void cleanup() {
    shellScriptHelperServiceMockedStatic.close();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testCallbackExpiry() {
    CustomApprovalInstance instance = CustomApprovalInstance.builder()
                                          .shellType(ShellType.Bash)
                                          .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(0);
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    try (MockedConstruction<NGLogCallback> mocked = mockConstruction(NGLogCallback.class)) {
      customApprovalCallback.push(null);
      NGLogCallback logCallback = mocked.constructed().get(0);
      verify(logCallback)
          .saveExecutionLog(LogHelper.color("Approval instance has expired", LogColor.Red), LogLevel.INFO,
              CommandExecutionStatus.FAILURE);
    }

    verify(approvalInstanceService).finalizeStatus(anyString(), eq(ApprovalStatus.EXPIRED), nullable(TicketNG.class));
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testCallbackAbort() {
    CustomApprovalInstance instance = CustomApprovalInstance.builder()
                                          .shellType(ShellType.Bash)
                                          .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .build();
    instance.setStatus(ApprovalStatus.ABORTED);
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(0);
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    customApprovalCallback.push(null);
    verify(approvalInstanceService, never()).finalizeStatus(anyString(), any(), nullable(TicketNG.class));
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testCallbackWithError() {
    CustomApprovalInstance instance = CustomApprovalInstance.builder()
                                          .shellType(ShellType.Bash)
                                          .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(Long.MAX_VALUE);
    ErrorNotifyResponseData errorNotifyResponseData =
        ErrorNotifyResponseData.builder().errorMessage("Script failed!").build();
    when(kryoSerializer.asInflatedObject(any())).thenReturn(errorNotifyResponseData);
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    customApprovalCallback.push(ImmutableMap.of("xyz", BinaryResponseData.builder().build()));
    verify(approvalInstanceService, never()).finalizeStatus(anyString(), any(), nullable(TicketNG.class));
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCallbackWithErrorUsingKryoWithoutReference() {
    CustomApprovalInstance instance = CustomApprovalInstance.builder()
                                          .shellType(ShellType.Bash)
                                          .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
                                          .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(Long.MAX_VALUE);
    ErrorNotifyResponseData errorNotifyResponseData =
        ErrorNotifyResponseData.builder().errorMessage("Script failed!").build();
    when(referenceFalseKryoSerializer.asInflatedObject(any())).thenReturn(errorNotifyResponseData);
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    customApprovalCallback.push(Map.of("xyz", BinaryResponseData.builder().usingKryoWithoutReference(true).build()));
    verify(approvalInstanceService, never()).finalizeStatus(anyString(), any(), nullable(TicketNG.class));
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testCallbackApproved() {
    Map<String, Object> outputVars = ImmutableMap.of("Status", ParameterField.createValueField("status"));
    CustomApprovalInstance instance =
        CustomApprovalInstance.builder()
            .shellType(ShellType.Bash)
            .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
            .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
            .outputVariables(outputVars)
            .approvalCriteria(
                CriteriaSpecWrapperDTO.builder()
                    .type(CriteriaSpecType.KEY_VALUES)
                    .criteriaSpecDTO(
                        KeyValuesCriteriaSpecDTO.builder()
                            .matchAnyCondition(false)
                            .conditions(Collections.singletonList(
                                ConditionDTO.builder().key("Status").operator(Operator.EQ).value("APPROVED").build()))
                            .build())
                    .build())
            .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(Long.MAX_VALUE);
    Map<String, String> sweepingOutput = ImmutableMap.of("status", "APPROVED");
    ShellScriptTaskResponseNG response =
        ShellScriptTaskResponseNG.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .executeCommandResponse(
                ExecuteCommandResponse.builder()
                    .commandExecutionData(
                        ShellExecutionData.builder().sweepingOutputEnvVariables(sweepingOutput).build())
                    .build())
            .build();

    when(kryoSerializer.asInflatedObject(any())).thenReturn(response);
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    customApprovalCallback.push(ImmutableMap.of("xyz", BinaryResponseData.builder().build()));
    verify(approvalInstanceService).finalizeStatus(anyString(), eq(ApprovalStatus.APPROVED), any(TicketNG.class));
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCallbackWhenReceiveInflatedResponse() {
    Map<String, Object> outputVars = ImmutableMap.of("Status", ParameterField.createValueField("status"));
    CustomApprovalInstance instance =
        CustomApprovalInstance.builder()
            .shellType(ShellType.Bash)
            .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
            .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
            .outputVariables(outputVars)
            .approvalCriteria(
                CriteriaSpecWrapperDTO.builder()
                    .type(CriteriaSpecType.KEY_VALUES)
                    .criteriaSpecDTO(
                        KeyValuesCriteriaSpecDTO.builder()
                            .matchAnyCondition(false)
                            .conditions(Collections.singletonList(
                                ConditionDTO.builder().key("Status").operator(Operator.EQ).value("APPROVED").build()))
                            .build())
                    .build())
            .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(Long.MAX_VALUE);
    Map<String, String> sweepingOutput = ImmutableMap.of("status", "APPROVED");
    ShellScriptTaskResponseNG response =
        ShellScriptTaskResponseNG.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .executeCommandResponse(
                ExecuteCommandResponse.builder()
                    .commandExecutionData(
                        ShellExecutionData.builder().sweepingOutputEnvVariables(sweepingOutput).build())
                    .build())
            .build();

    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    customApprovalCallback.push(ImmutableMap.of("xyz", response));
    verify(approvalInstanceService).finalizeStatus(anyString(), eq(ApprovalStatus.APPROVED), any(TicketNG.class));

    customApprovalCallback.push(ImmutableMap.of("xyz", ErrorNotifyResponseData.builder().build()));
    verify(approvalInstanceService, times(2)).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler, times(2)).wakeup();
    verifyNoInteractions(kryoSerializer);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCallbackApprovedUsingKryoWithoutReference() {
    Map<String, Object> outputVars = Map.of("Status", ParameterField.createValueField("status"));
    CustomApprovalInstance instance =
        CustomApprovalInstance.builder()
            .shellType(ShellType.Bash)
            .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
            .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
            .outputVariables(outputVars)
            .approvalCriteria(
                CriteriaSpecWrapperDTO.builder()
                    .type(CriteriaSpecType.KEY_VALUES)
                    .criteriaSpecDTO(
                        KeyValuesCriteriaSpecDTO.builder()
                            .matchAnyCondition(false)
                            .conditions(Collections.singletonList(
                                ConditionDTO.builder().key("Status").operator(Operator.EQ).value("APPROVED").build()))
                            .build())
                    .build())
            .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(Long.MAX_VALUE);
    Map<String, String> sweepingOutput = Map.of("status", "APPROVED");
    ShellScriptTaskResponseNG response =
        ShellScriptTaskResponseNG.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .executeCommandResponse(
                ExecuteCommandResponse.builder()
                    .commandExecutionData(
                        ShellExecutionData.builder().sweepingOutputEnvVariables(sweepingOutput).build())
                    .build())
            .build();

    when(referenceFalseKryoSerializer.asInflatedObject(any())).thenReturn(response);
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    customApprovalCallback.push(Map.of("xyz", BinaryResponseData.builder().usingKryoWithoutReference(true).build()));
    verify(approvalInstanceService).finalizeStatus(anyString(), eq(ApprovalStatus.APPROVED), any(TicketNG.class));
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testCallbackFatalException() {
    Map<String, Object> outputVars = ImmutableMap.of("Status", ParameterField.createValueField("status"));
    CustomApprovalInstance instance =
        CustomApprovalInstance.builder()
            .shellType(ShellType.Bash)
            .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
            .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
            .outputVariables(outputVars)
            .approvalCriteria(
                CriteriaSpecWrapperDTO.builder()
                    .type(CriteriaSpecType.KEY_VALUES)
                    .criteriaSpecDTO(
                        KeyValuesCriteriaSpecDTO.builder().matchAnyCondition(false).conditions(null).build())
                    .build())
            .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(Long.MAX_VALUE);
    Map<String, String> sweepingOutput = ImmutableMap.of("status", "APPROVED");
    ShellScriptTaskResponseNG response =
        ShellScriptTaskResponseNG.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .executeCommandResponse(
                ExecuteCommandResponse.builder()
                    .commandExecutionData(
                        ShellExecutionData.builder().sweepingOutputEnvVariables(sweepingOutput).build())
                    .build())
            .build();

    when(kryoSerializer.asInflatedObject(any())).thenReturn(response);
    when(shellScriptHelperService.prepareShellScriptOutcome(eq(sweepingOutput), eq(outputVars)))
        .thenReturn(ShellScriptOutcome.builder().outputVariables(ImmutableMap.of("Status", "APPROVED")).build());
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    customApprovalCallback.push(ImmutableMap.of("xyz", BinaryResponseData.builder().build()));
    verify(approvalInstanceService).finalizeStatus(anyString(), eq(ApprovalStatus.FAILED), anyString());
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCallbackFatalExceptionUsignKryoReferenceFalse() {
    Map<String, Object> outputVars = Map.of("Status", ParameterField.createValueField("status"));
    CustomApprovalInstance instance =
        CustomApprovalInstance.builder()
            .shellType(ShellType.Bash)
            .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
            .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
            .outputVariables(outputVars)
            .approvalCriteria(
                CriteriaSpecWrapperDTO.builder()
                    .type(CriteriaSpecType.KEY_VALUES)
                    .criteriaSpecDTO(
                        KeyValuesCriteriaSpecDTO.builder().matchAnyCondition(false).conditions(null).build())
                    .build())
            .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(Long.MAX_VALUE);
    Map<String, String> sweepingOutput = Map.of("status", "APPROVED");
    ShellScriptTaskResponseNG response =
        ShellScriptTaskResponseNG.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .executeCommandResponse(
                ExecuteCommandResponse.builder()
                    .commandExecutionData(
                        ShellExecutionData.builder().sweepingOutputEnvVariables(sweepingOutput).build())
                    .build())
            .build();

    when(referenceFalseKryoSerializer.asInflatedObject(any())).thenReturn(response);
    when(shellScriptHelperService.prepareShellScriptOutcome(eq(sweepingOutput), eq(outputVars)))
        .thenReturn(ShellScriptOutcome.builder().outputVariables(Map.of("Status", "APPROVED")).build());
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    customApprovalCallback.push(Map.of("xyz", BinaryResponseData.builder().usingKryoWithoutReference(true).build()));
    verify(approvalInstanceService).finalizeStatus(anyString(), eq(ApprovalStatus.FAILED), anyString());
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testCallbackNonFatalException() {
    Map<String, Object> outputVars = ImmutableMap.of("Status", "status");
    CustomApprovalInstance instance =
        CustomApprovalInstance.builder()
            .shellType(ShellType.Bash)
            .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
            .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
            .outputVariables(outputVars)
            .approvalCriteria(
                CriteriaSpecWrapperDTO.builder()
                    .type(CriteriaSpecType.KEY_VALUES)
                    .criteriaSpecDTO(
                        KeyValuesCriteriaSpecDTO.builder().matchAnyCondition(false).conditions(null).build())
                    .build())
            .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(Long.MAX_VALUE);
    Map<String, String> sweepingOutput = new HashMap<>();
    ShellScriptTaskResponseNG response =
        ShellScriptTaskResponseNG.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .executeCommandResponse(
                ExecuteCommandResponse.builder()
                    .commandExecutionData(
                        ShellExecutionData.builder().sweepingOutputEnvVariables(sweepingOutput).build())
                    .build())
            .build();

    when(kryoSerializer.asInflatedObject(any())).thenReturn(response);
    shellScriptHelperServiceMockedStatic
        .when(() -> ShellScriptHelperService.prepareShellScriptOutcome(eq(sweepingOutput), eq(outputVars), eq(null)))
        .thenAnswer(
            (Answer<ShellScriptOutcome>) invocation -> ShellScriptOutcome.builder().outputVariables(null).build());
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    assertThatThrownBy(() -> customApprovalCallback.push(ImmutableMap.of("xyz", BinaryResponseData.builder().build())))
        .isInstanceOf(HarnessCustomApprovalException.class)
        .hasMessageContaining("Error while evaluating approval/rejection criteria");
    verify(approvalInstanceService, never()).finalizeStatus(anyString(), any(), any(TicketNG.class));
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCallbackNonFatalExceptionUsingKryoWithoutReference() {
    Map<String, Object> outputVars = Map.of("Status", "status");
    CustomApprovalInstance instance =
        CustomApprovalInstance.builder()
            .shellType(ShellType.Bash)
            .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
            .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
            .outputVariables(outputVars)
            .approvalCriteria(
                CriteriaSpecWrapperDTO.builder()
                    .type(CriteriaSpecType.KEY_VALUES)
                    .criteriaSpecDTO(
                        KeyValuesCriteriaSpecDTO.builder().matchAnyCondition(false).conditions(null).build())
                    .build())
            .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(Long.MAX_VALUE);
    Map<String, String> sweepingOutput = new HashMap<>();
    ShellScriptTaskResponseNG response =
        ShellScriptTaskResponseNG.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .executeCommandResponse(
                ExecuteCommandResponse.builder()
                    .commandExecutionData(
                        ShellExecutionData.builder().sweepingOutputEnvVariables(sweepingOutput).build())
                    .build())
            .build();

    when(referenceFalseKryoSerializer.asInflatedObject(any())).thenReturn(response);
    shellScriptHelperServiceMockedStatic
        .when(() -> ShellScriptHelperService.prepareShellScriptOutcome(eq(sweepingOutput), eq(outputVars), eq(null)))
        .thenAnswer(
            (Answer<ShellScriptOutcome>) invocation -> ShellScriptOutcome.builder().outputVariables(null).build());
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    assertThatThrownBy(()
                           -> customApprovalCallback.push(
                               Map.of("xyz", BinaryResponseData.builder().usingKryoWithoutReference(true).build())))
        .isInstanceOf(HarnessCustomApprovalException.class)
        .hasMessageContaining("Error while evaluating approval/rejection criteria");
    verify(approvalInstanceService, never()).finalizeStatus(anyString(), any(), any(TicketNG.class));
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testCallbackRejected() {
    Map<String, Object> outputVars = ImmutableMap.of("Status", ParameterField.createValueField("status"));
    CustomApprovalInstance instance =
        CustomApprovalInstance.builder()
            .shellType(ShellType.Bash)
            .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
            .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
            .outputVariables(outputVars)
            .approvalCriteria(
                CriteriaSpecWrapperDTO.builder()
                    .type(CriteriaSpecType.KEY_VALUES)
                    .criteriaSpecDTO(
                        KeyValuesCriteriaSpecDTO.builder()
                            .matchAnyCondition(false)
                            .conditions(Collections.singletonList(
                                ConditionDTO.builder().key("Status").operator(Operator.EQ).value("APPROVED").build()))
                            .build())
                    .build())
            .rejectionCriteria(
                CriteriaSpecWrapperDTO.builder()
                    .type(CriteriaSpecType.KEY_VALUES)
                    .criteriaSpecDTO(
                        KeyValuesCriteriaSpecDTO.builder()
                            .matchAnyCondition(false)
                            .conditions(Collections.singletonList(
                                ConditionDTO.builder().key("Status").operator(Operator.EQ).value("REJECTED").build()))
                            .build())
                    .build())
            .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(Long.MAX_VALUE);
    Map<String, String> sweepingOutput = ImmutableMap.of("status", "REJECTED");
    ShellScriptTaskResponseNG response =
        ShellScriptTaskResponseNG.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .executeCommandResponse(
                ExecuteCommandResponse.builder()
                    .commandExecutionData(
                        ShellExecutionData.builder().sweepingOutputEnvVariables(sweepingOutput).build())
                    .build())
            .build();

    when(kryoSerializer.asInflatedObject(any())).thenReturn(response);
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    customApprovalCallback.push(ImmutableMap.of("xyz", BinaryResponseData.builder().build()));
    verify(approvalInstanceService).finalizeStatus(anyString(), eq(ApprovalStatus.REJECTED), any(TicketNG.class));
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testCallbackRejectedUsingKryoWithoutReference() {
    Map<String, Object> outputVars = Map.of("Status", ParameterField.createValueField("status"));
    CustomApprovalInstance instance =
        CustomApprovalInstance.builder()
            .shellType(ShellType.Bash)
            .retryInterval(ParameterField.createValueField(Timeout.fromString("1m")))
            .scriptTimeout(ParameterField.createValueField(Timeout.fromString("1m")))
            .outputVariables(outputVars)
            .approvalCriteria(
                CriteriaSpecWrapperDTO.builder()
                    .type(CriteriaSpecType.KEY_VALUES)
                    .criteriaSpecDTO(
                        KeyValuesCriteriaSpecDTO.builder()
                            .matchAnyCondition(false)
                            .conditions(Collections.singletonList(
                                ConditionDTO.builder().key("Status").operator(Operator.EQ).value("APPROVED").build()))
                            .build())
                    .build())
            .rejectionCriteria(
                CriteriaSpecWrapperDTO.builder()
                    .type(CriteriaSpecType.KEY_VALUES)
                    .criteriaSpecDTO(
                        KeyValuesCriteriaSpecDTO.builder()
                            .matchAnyCondition(false)
                            .conditions(Collections.singletonList(
                                ConditionDTO.builder().key("Status").operator(Operator.EQ).value("REJECTED").build()))
                            .build())
                    .build())
            .build();
    instance.setId(APPROVAL_INSTANCE_ID);
    instance.setType(ApprovalType.CUSTOM_APPROVAL);
    instance.setAmbiance(ambiance);
    instance.setDeadline(Long.MAX_VALUE);
    Map<String, String> sweepingOutput = Map.of("status", "REJECTED");
    ShellScriptTaskResponseNG response =
        ShellScriptTaskResponseNG.builder()
            .status(CommandExecutionStatus.SUCCESS)
            .executeCommandResponse(
                ExecuteCommandResponse.builder()
                    .commandExecutionData(
                        ShellExecutionData.builder().sweepingOutputEnvVariables(sweepingOutput).build())
                    .build())
            .build();

    when(referenceFalseKryoSerializer.asInflatedObject(any())).thenReturn(response);
    when(approvalInstanceService.get(eq(APPROVAL_INSTANCE_ID))).thenReturn(instance);
    customApprovalCallback.push(Map.of("xyz", BinaryResponseData.builder().usingKryoWithoutReference(true).build()));
    verify(approvalInstanceService).finalizeStatus(anyString(), eq(ApprovalStatus.REJECTED), any(TicketNG.class));
    verify(approvalInstanceService).resetNextIterations(eq(APPROVAL_INSTANCE_ID), any());
    verify(customApprovalInstanceHandler).wakeup();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testCustomCriteriaEvaluator() {
    // Missing output variables from shell script
    assertThatThrownBy(() -> evaluateCriteria(CustomApprovalTicketNG.builder().build(), null))
        .isInstanceOf(ApprovalStepNGException.class)
        .extracting("fatal")
        .isEqualTo(false);

    boolean result = evaluateCriteria(
        CustomApprovalTicketNG.builder()
            .fields(ImmutableMap.<String, String>builder().put("Status", "Done").put("ApprovedBy", "Random").build())
            .build(),
        KeyValuesCriteriaSpecDTO.builder()
            .matchAnyCondition(true)
            .conditions(Lists.newArrayList(
                ConditionDTO.builder().key("ApprovedBy").value("Admin").operator(Operator.EQ).build(),
                ConditionDTO.builder().key("Status").value("Done").operator(Operator.EQ).build()))
            .build());
    Assertions.assertThat(result).isTrue();

    assertThat(
        evaluateCriteria(
            CustomApprovalTicketNG.builder()
                .fields(
                    ImmutableMap.<String, String>builder().put("Status", "Done").put("ApprovedBy", "Random").build())
                .build(),
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(false)
                .conditions(
                    Lists.newArrayList(ConditionDTO.builder().key("Status").value("Done").operator(Operator.EQ).build(),
                        ConditionDTO.builder().key("ApprovedBy").value("Admin").operator(Operator.EQ).build()))
                .build()))
        .isFalse();

    assertThat(
        evaluateCriteria(
            CustomApprovalTicketNG.builder()
                .fields(
                    ImmutableMap.<String, String>builder().put("Status", "To Do").put("ApprovedBy", "Random").build())
                .build(),
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(true)
                .conditions(
                    Lists.newArrayList(ConditionDTO.builder().key("Status").value("Done").operator(Operator.EQ).build(),
                        ConditionDTO.builder().key("ApprovedBy").value("Admin").operator(Operator.EQ).build()))
                .build()))
        .isFalse();

    assertThat(
        evaluateCriteria(
            CustomApprovalTicketNG.builder()
                .fields(ImmutableMap.<String, String>builder().put("Status", "Done").put("ApprovedBy", "Admin").build())
                .build(),
            KeyValuesCriteriaSpecDTO.builder()
                .matchAnyCondition(false)
                .conditions(
                    Lists.newArrayList(ConditionDTO.builder().key("Status").value("Done").operator(Operator.EQ).build(),
                        ConditionDTO.builder().key("ApprovedBy").value("Admin").operator(Operator.EQ).build()))
                .build()))
        .isTrue();

    assertThat(
        evaluateCriteria(
            CustomApprovalTicketNG.builder()
                .fields(
                    ImmutableMap.<String, String>builder().put("Status", "Done").put("ApprovedBy", "Random").build())
                .build(),
            JexlCriteriaSpecDTO.builder().expression("<+output.Status> == \"Done\"").build()))
        .isTrue();

    assertThat(
        evaluateCriteria(
            CustomApprovalTicketNG.builder()
                .fields(
                    ImmutableMap.<String, String>builder().put("Status", "Todo").put("ApprovedBy", "Random").build())
                .build(),
            JexlCriteriaSpecDTO.builder().expression("<+output.Status> == \"Done\"").build()))
        .isFalse();

    assertThatThrownBy(()
                           -> evaluateCriteria(CustomApprovalTicketNG.builder()
                                                   .fields(ImmutableMap.<String, String>builder()
                                                               .put("Status", "Todo")
                                                               .put("ApprovedBy", "Random")
                                                               .build())
                                                   .build(),
                               JexlCriteriaSpecDTO.builder().expression("<+output.Status>").build()))
        .isInstanceOf(ApprovalStepNGException.class)
        .extracting("fatal")
        .isEqualTo(true);

    assertThatThrownBy(()
                           -> evaluateCriteria(CustomApprovalTicketNG.builder()
                                                   .fields(ImmutableMap.<String, String>builder()
                                                               .put("Status", "Todo")
                                                               .put("ApprovedBy", "Random")
                                                               .build())
                                                   .build(),
                               JexlCriteriaSpecDTO.builder().expression("   ").build()))
        .isInstanceOf(ApprovalStepNGException.class)
        .extracting("fatal")
        .isEqualTo(true);

    assertThatThrownBy(
        ()
            -> evaluateCriteria(
                CustomApprovalTicketNG.builder()
                    .fields(
                        ImmutableMap.<String, String>builder().put("Status", "Done").put("ApprovedBy", "Admin").build())
                    .build(),
                KeyValuesCriteriaSpecDTO.builder().matchAnyCondition(false).conditions(new ArrayList<>()).build()))
        .isInstanceOf(ApprovalStepNGException.class)
        .extracting("fatal")
        .isEqualTo(true);
  }
}
