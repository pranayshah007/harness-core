/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.PARDHA;
import static io.harness.rule.OwnerRule.SHUBHAM_MAHESHWARI;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.ShellScriptProvisionOutputVariables;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ShellScriptProvisionerOutputElement;
import software.wings.api.shellscript.provision.ShellScriptProvisionExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureProvisionerType;
import software.wings.beans.shellscript.provisioner.ShellScriptInfrastructureProvisioner;
import software.wings.beans.shellscript.provisioner.ShellScriptProvisionParameters;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.ManagerExecutionLogCallback;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class ShellScriptProvisionStateTest extends WingsBaseTest {
  @Mock private DelegateService delegateService;
  @Mock private ActivityService activityService;
  @Mock private InfrastructureProvisionerService infrastructureProvisionerService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private ExecutionContextImpl executionContext;
  @Mock private StateExecutionService stateExecutionService;
  @Mock protected FeatureFlagService featureFlagService;
  @Mock protected ManagerExecutionLogCallback logCallback;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Inject private KryoSerializer kryoSerializer;

  @InjectMocks
  private ShellScriptProvisionState state =
      new ShellScriptProvisionState(InfrastructureProvisionerType.SHELL_SCRIPT.name());

  @Before
  public void setUp() throws Exception {
    Reflect.on(state).set("kryoSerializer", kryoSerializer);
    state.setProvisionerId(PROVISIONER_ID);
    doReturn(logCallback)
        .when(infrastructureProvisionerService)
        .getManagerExecutionCallback(eq(APP_ID), eq(ACTIVITY_ID), any());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testParseOutput() {
    assertThat(state.parseOutput(null)).isEqualTo(Collections.emptyMap());
    assertThat(state.parseOutput("")).isEqualTo(Collections.emptyMap());

    String json = "{\n"
        + "\t\"key1\":\"val1\",\n"
        + "\t\"key2\":\"val2\"\n"
        + "}";
    Map<String, Object> expectedMap = new LinkedHashMap<>();
    expectedMap.put("key1", "val1");
    expectedMap.put("key2", "val2");
    assertThat(state.parseOutput(json)).isEqualTo(expectedMap);
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testProvisionerAsInvalidExpression() {
    when(activityService.save(any())).thenReturn(mock(Activity.class));
    when(executionContext.getApp()).thenReturn(mock(Application.class));
    when(executionContext.getEnv()).thenReturn(mock(Environment.class));
    when(executionContext.getAppId()).thenReturn(APP_ID);
    when(executionContext.renderExpression(PROVISIONER_ID)).thenReturn("Provisioner Name");
    when(infrastructureProvisionerService.getByName(APP_ID, "Provisioner Name")).thenReturn(null);
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Could not find a Shell Script Provisioner with resolved name: Provisioner Name");
  }

  @Test
  @Owner(developers = SHUBHAM_MAHESHWARI)
  @Category(UnitTests.class)
  public void testProvisionerAsExpressionInvalidName() {
    when(activityService.save(any())).thenReturn(mock(Activity.class));
    when(executionContext.getApp()).thenReturn(mock(Application.class));
    when(executionContext.getEnv()).thenReturn(mock(Environment.class));
    when(executionContext.getAppId()).thenReturn(APP_ID);
    when(executionContext.renderExpression(PROVISIONER_ID)).thenReturn("Provisioner Name");
    when(infrastructureProvisionerService.getByName(APP_ID, "Provisioner Name"))
        .thenReturn(CloudFormationInfrastructureProvisioner.builder().build());
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Resolved Provisioner with name: Provisioner Name, is not of type Shell Script");
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testValidation() {
    ShellScriptProvisionState provisionState =
        new ShellScriptProvisionState(InfrastructureProvisionerType.SHELL_SCRIPT.name());
    assertThat(provisionState.validateFields().size()).isNotEqualTo(0);
    provisionState.setProvisionerId("test provisioner");
    assertThat(provisionState.validateFields().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void shouldPopulateWorkflowExecutionIdParamFromExecutionContext() {
    ExecutionContextImpl executionContext = mock(ExecutionContextImpl.class);
    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);

    when(activityService.save(any())).thenReturn(mock(Activity.class));
    when(executionContext.getApp()).thenReturn(mock(Application.class));
    when(executionContext.getEnv()).thenReturn(mock(Environment.class));
    when(infrastructureProvisionerService.getShellScriptProvisioner(any(), any()))
        .thenReturn(mock(ShellScriptInfrastructureProvisioner.class));
    when(executionContext.getWorkflowExecutionId()).thenReturn("workflow-execution-id");
    when(executionContext.renderExpression(PROVISIONER_ID)).thenReturn(PROVISIONER_ID);
    state.execute(executionContext);

    verify(delegateService).queueTaskV2(delegateTaskArgumentCaptor.capture());
    ShellScriptProvisionParameters populatedParameters =
        (ShellScriptProvisionParameters) delegateTaskArgumentCaptor.getValue().getData().getParameters()[0];
    assertThat(populatedParameters.getWorkflowExecutionId()).isEqualTo("workflow-execution-id");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseFailedResponse() {
    ShellScriptProvisionExecutionData executionData = ShellScriptProvisionExecutionData.builder()
                                                          .executionStatus(ExecutionStatus.ERROR)
                                                          .errorMsg("Error during execution")
                                                          .build();
    Map<String, ResponseData> responseData = ImmutableMap.of(ACTIVITY_ID, executionData);

    doReturn(APP_ID).when(executionContext).getAppId();
    doReturn(null).when(executionContext).getContextElement(ContextElementType.SHELL_SCRIPT_PROVISION);
    when(executionContext.renderExpression(PROVISIONER_ID)).thenReturn(PROVISIONER_ID);
    ExecutionResponse response = state.handleAsyncResponse(executionContext, responseData);
    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.ERROR);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.ERROR);
    assertThat(response.getErrorMessage()).isEqualTo("Error during execution");
    // Should create new OutputElement
    assertThat(response.getContextElements().get(0)).isNotNull();
    // Should create new OutputElement
    assertThat(response.getNotifyElements().get(0)).isNotNull();

    ShellScriptProvisionerOutputElement outputElement = ShellScriptProvisionerOutputElement.builder().build();
    doReturn(outputElement).when(executionContext).getContextElement(ContextElementType.SHELL_SCRIPT_PROVISION);
    response = state.handleAsyncResponse(executionContext, responseData);
    // Reuse existing OutputElement
    assertThat(response.getContextElements().get(0)).isSameAs(outputElement);
    // Reuse existing OutputElement
    assertThat(response.getNotifyElements().get(0)).isSameAs(outputElement);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    state.setSweepingOutputName("${expression}");
    state.setSweepingOutputScope(Scope.PHASE);
    ShellScriptProvisionExecutionData executionData = ShellScriptProvisionExecutionData.builder()
                                                          .executionStatus(ExecutionStatus.SUCCESS)
                                                          .output("{\"key\": \"value\"}")
                                                          .build();
    Map<String, ResponseData> responseData = ImmutableMap.of(ACTIVITY_ID, executionData);
    Map<String, Object> expectedOutputMap = ImmutableMap.of("key", "value");
    ShellScriptProvisionerOutputElement outputElement = ShellScriptProvisionerOutputElement.builder().build();

    doReturn(outputElement).when(executionContext).getContextElement(ContextElementType.SHELL_SCRIPT_PROVISION);
    doReturn(APP_ID).when(executionContext).getAppId();
    doReturn("rendered-expression").when(executionContext).renderExpression("${expression}");
    doReturn(SweepingOutputInstance.builder()).when(executionContext).prepareSweepingOutputBuilder(Scope.PHASE);
    when(executionContext.renderExpression(PROVISIONER_ID)).thenReturn(PROVISIONER_ID);

    ExecutionResponse response = state.handleAsyncResponse(executionContext, responseData);

    ArgumentCaptor<SweepingOutputInstance> instanceCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(infrastructureProvisionerService, times(1))
        .regenerateInfrastructureMappings(
            PROVISIONER_ID, executionContext, expectedOutputMap, Optional.of(logCallback), Optional.empty());
    verify(sweepingOutputService, times(1)).save(instanceCaptor.capture());
    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);

    assertThat(instanceCaptor.getValue().getName()).isEqualTo("rendered-expression");
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getContextElements().get(0)).isEqualTo(outputElement);
    assertThat(response.getNotifyElements().get(0)).isEqualTo(outputElement);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateActivity() {
    ShellScriptInfrastructureProvisioner provisioner = ShellScriptInfrastructureProvisioner.builder().build();
    Environment env = Environment.Builder.anEnvironment()
                          .uuid(ENV_ID)
                          .name(ENV_NAME)
                          .environmentType(EnvironmentType.NON_PROD)
                          .build();

    doReturn(env).when(executionContext).getEnv();
    doReturn(APP_ID).when(executionContext).getAppId();
    doReturn(WorkflowType.ORCHESTRATION).when(executionContext).getWorkflowType();
    doReturn(WORKFLOW_EXECUTION_ID).when(executionContext).getWorkflowExecutionId();
    when(executionContext.renderExpression(PROVISIONER_ID)).thenReturn(PROVISIONER_ID);
    doReturn(WORKFLOW_NAME).when(executionContext).getWorkflowExecutionName();
    doReturn(Application.Builder.anApplication().uuid(APP_ID).build()).when(executionContext).getApp();
    doAnswer(invocation -> invocation.getArgument(0, Activity.class)).when(activityService).save(any(Activity.class));
    doReturn(provisioner).when(infrastructureProvisionerService).getShellScriptProvisioner(APP_ID, PROVISIONER_ID);
    ArgumentCaptor<Activity> activityCaptor = ArgumentCaptor.forClass(Activity.class);
    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);

    // When OrchestrationWorkflowType is BUILD
    doReturn(OrchestrationWorkflowType.BUILD).when(executionContext).getOrchestrationWorkflowType();
    state.execute(executionContext);
    verify(activityService, times(1)).save(activityCaptor.capture());
    assertCreatedActivity(activityCaptor.getValue(), GLOBAL_ENV_ID, GLOBAL_ENV_ID, ALL);

    verify(delegateService).queueTaskV2(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue().getData().getExpressionFunctorToken()).isNotNull();

    // When OrchestrationWorkflowType is other than BUILD
    doReturn(OrchestrationWorkflowType.BASIC).when(executionContext).getOrchestrationWorkflowType();
    state.execute(executionContext);
    // 1 time from previous invocation
    verify(activityService, times(2)).save(activityCaptor.capture());
    assertCreatedActivity(activityCaptor.getValue(), ENV_NAME, ENV_ID, EnvironmentType.NON_PROD);
  }

  private void assertCreatedActivity(Activity activity, String envName, String envId, EnvironmentType envType) {
    assertThat(activity.getWorkflowType()).isEqualTo(WorkflowType.ORCHESTRATION);
    assertThat(activity.getWorkflowExecutionId()).isEqualTo(WORKFLOW_EXECUTION_ID);
    assertThat(activity.getWorkflowExecutionName()).isEqualTo(WORKFLOW_NAME);
    assertThat(activity.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(activity.getEnvironmentName()).isEqualTo(envName);
    assertThat(activity.getEnvironmentId()).isEqualTo(envId);
    assertThat(activity.getEnvironmentType()).isEqualTo(envType);
  }

  @Test
  @Owner(developers = PARDHA)
  @Category(UnitTests.class)
  public void shouldPopulateRenderedDelegateSelectorsFromExecutionContext() {
    final String runTimeValueAbc = "runTimeValueAbc";
    state.setDelegateSelectors(Collections.singletonList("${workflow.variables.abc}"));
    ExecutionContextImpl executionContext = mock(ExecutionContextImpl.class);
    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);

    when(activityService.save(any())).thenReturn(mock(Activity.class));
    when(executionContext.getApp()).thenReturn(mock(Application.class));
    when(executionContext.getEnv()).thenReturn(mock(Environment.class));
    when(infrastructureProvisionerService.getShellScriptProvisioner(any(), any()))
        .thenReturn(mock(ShellScriptInfrastructureProvisioner.class));
    when(executionContext.renderExpression(any())).thenReturn(runTimeValueAbc);
    when(executionContext.renderExpression(PROVISIONER_ID)).thenReturn(PROVISIONER_ID);
    state.execute(executionContext);

    verify(delegateService).queueTaskV2(delegateTaskArgumentCaptor.capture());
    ShellScriptProvisionParameters populatedParameters =
        (ShellScriptProvisionParameters) delegateTaskArgumentCaptor.getValue().getData().getParameters()[0];
    assertThat(populatedParameters.getDelegateSelectors()).isEqualTo(Collections.singletonList(runTimeValueAbc));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveProvisionerOutputsOnResponse() {
    String provisionerOutput = "{  \"dockerLabels\": {\n"
        + "    \"com.datadoghq.tags.env\": \"poc1\",\n"
        + "    \"com.datadoghq.tags.service\": \"magicbus\",\n"
        + "    \"com.datadoghq.tags.version\": \"755626d45887ba25426c58972686b63d438c4239\"\n"
        + "  }}";

    when(executionContext.renderExpression(PROVISIONER_ID)).thenReturn(PROVISIONER_ID);

    Map<String, ResponseData> responseData =
        prepareTestWithResponseDataMap(provisionerOutput, Collections.emptyMap(), null);

    state.handleAsyncResponse(executionContext, responseData);

    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
    ArgumentCaptor<SweepingOutputInstance> instanceCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService, times(2)).save(instanceCaptor.capture());
    List<SweepingOutputInstance> outputInstances = instanceCaptor.getAllValues();
    assertThat(outputInstances).hasSize(2);
    assertThat(outputInstances.stream().map(SweepingOutputInstance::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("rendered-expression", "shellScriptProvisioner");

    assertThat(outputInstances.get(1).getValue()).isInstanceOf(ShellScriptProvisionOutputVariables.class);
    ShellScriptProvisionOutputVariables storedOutputVariables =
        (ShellScriptProvisionOutputVariables) outputInstances.get(1).getValue();
    assertThat(storedOutputVariables.get("dockerLabels")).isInstanceOf(Map.class);
    Map<String, String> complexVarValue = (Map<String, String>) storedOutputVariables.get("dockerLabels");
    assertThat(complexVarValue.keySet()).hasSize(3);
    assertThat(complexVarValue.keySet())
        .containsExactlyInAnyOrder(
            "com.datadoghq.tags.env", "com.datadoghq.tags.service", "com.datadoghq.tags.version");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveProvisionerOutputsOnResponseWithExistingOutputs() {
    when(executionContext.renderExpression(PROVISIONER_ID)).thenReturn(PROVISIONER_ID);
    String provisionerOutput = "{\"key\": \"value\"}";
    Map<String, Object> outputVariablesFromContextElement = new HashMap<>();
    outputVariablesFromContextElement.put("outputVariableFromContext", "value");
    ShellScriptProvisionOutputVariables existingOutputVariables = new ShellScriptProvisionOutputVariables();
    existingOutputVariables.put("existing", "value");
    SweepingOutputInstance existingVariablesOutputs =
        SweepingOutputInstance.builder()
            .name(ShellScriptProvisionOutputVariables.SWEEPING_OUTPUT_NAME)
            .value(existingOutputVariables)
            .uuid(UUID)
            .build();
    Map<String, ResponseData> responseData =
        prepareTestWithResponseDataMap(provisionerOutput, outputVariablesFromContextElement, existingVariablesOutputs);

    state.handleAsyncResponse(executionContext, responseData);

    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
    ArgumentCaptor<SweepingOutputInstance> instanceCaptor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService, times(2)).save(instanceCaptor.capture());
    List<SweepingOutputInstance> outputInstances = instanceCaptor.getAllValues();
    assertThat(outputInstances).hasSize(2);
    assertThat(outputInstances.stream().map(SweepingOutputInstance::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("rendered-expression", "shellScriptProvisioner");

    assertThat(outputInstances.get(1).getValue()).isInstanceOf(ShellScriptProvisionOutputVariables.class);
    ShellScriptProvisionOutputVariables storedOutputVariables =
        (ShellScriptProvisionOutputVariables) outputInstances.get(1).getValue();
    assertThat(storedOutputVariables.keySet())
        .containsExactlyInAnyOrder("existing", "outputVariableFromContext", "key");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveProvisionerOutputsWithNameShellScriptProvisioner() {
    when(executionContext.renderExpression(PROVISIONER_ID)).thenReturn(PROVISIONER_ID);
    String provisionerOutput = "{\"key\": \"value\"}";
    Map<String, ResponseData> responseData =
        prepareTestWithResponseDataMap(provisionerOutput, Collections.emptyMap(), null);
    state.setSweepingOutputName(ShellScriptProvisionOutputVariables.SWEEPING_OUTPUT_NAME);

    assertThatThrownBy(() -> state.handleAsyncResponse(executionContext, responseData))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("Output variables can not be exported in context with reserved name");
  }

  private Map<String, ResponseData> prepareTestWithResponseDataMap(String provisionerOutput,
      Map<String, Object> outputVariablesFromContextElement, SweepingOutputInstance existingSweepingOutput) {
    state.setSweepingOutputName("${expression}");
    state.setSweepingOutputScope(Scope.PHASE);
    ShellScriptProvisionExecutionData executionData = ShellScriptProvisionExecutionData.builder()
                                                          .executionStatus(ExecutionStatus.SUCCESS)
                                                          .output(provisionerOutput)
                                                          .build();
    Map<String, ResponseData> responseData = ImmutableMap.of(ACTIVITY_ID, executionData);

    ShellScriptProvisionerOutputElement outputElement =
        ShellScriptProvisionerOutputElement.builder().outputVariables(outputVariablesFromContextElement).build();

    doReturn(existingSweepingOutput).when(sweepingOutputService).find(any());
    doReturn(true)
        .when(featureFlagService)
        .isEnabled(eq(FeatureName.SAVE_SHELL_SCRIPT_PROVISION_OUTPUTS_TO_SWEEPING_OUTPUT), any());
    doReturn(SweepingOutputInquiry.builder()).when(executionContext).prepareSweepingOutputInquiryBuilder();
    doReturn(outputElement).when(executionContext).getContextElement(ContextElementType.SHELL_SCRIPT_PROVISION);
    doReturn(APP_ID).when(executionContext).getAppId();
    doReturn("rendered-expression").when(executionContext).renderExpression("${expression}");
    doReturn(SweepingOutputInstance.builder()).when(executionContext).prepareSweepingOutputBuilder(any());
    return responseData;
  }
}
