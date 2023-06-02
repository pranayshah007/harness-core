/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.beans.SweepingOutputInstance.Scope.PIPELINE;
import static io.harness.beans.SweepingOutputInstance.Scope.WORKFLOW;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.NAVNEET;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.shell.AccessType.KEY;
import static io.harness.shell.ScriptType.BASH;
import static io.harness.shell.ScriptType.POWERSHELL;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.ConnectionType.SSH;
import static software.wings.beans.ConnectionType.WINRM;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.HostConnectionAttributes.Builder.aHostConnectionAttributes;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode.OBTAIN_VALUE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.SimpleEncryption;
import io.harness.serializer.KryoSerializer;
import io.harness.shell.ShellExecutionData;

import software.wings.WingsBaseTest;
import software.wings.api.ScriptStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.Type;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.command.CommandType;
import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.beans.template.TemplateUtils;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.exception.ShellScriptException;
import software.wings.expression.ShellScriptEnvironmentVariables;
import software.wings.expression.ShellScriptFunctor;
import software.wings.service.impl.ActivityHelperService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SSHVaultService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ShellScriptStateTest extends WingsBaseTest {
  private static final Activity ACTIVITY_WITH_ID = Activity.builder().build();

  static {
    ACTIVITY_WITH_ID.setUuid(ACTIVITY_ID);
  }

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ActivityHelperService activityHelperService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private ExecutionContextImpl executionContext;
  @Mock private SweepingOutputService sweepingOutputService;
  @InjectMocks private ShellScriptFunctor shellScriptFunctor;
  @Mock private TemplateUtils templateUtils;
  @Mock private DelegateService delegateService;
  @Mock private SettingsService settingsService;
  @Mock private SecretManager secretManager;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Mock private WorkflowStandardParams workflowStandardParams;
  @Mock private TemplateExpressionProcessor templateExpressionProcessor;
  @Mock private TemplateExpression templateExpression;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private SSHVaultService sshVaultService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @InjectMocks private ShellScriptState shellScriptState = new ShellScriptState("ShellScript");

  private ExecutionResponse asyncExecutionResponse;

  @Inject KryoSerializer kryoSerializer;

  @Before
  public void setUp() throws Exception {
    shellScriptState.setSweepingOutputName("test");
    shellScriptState.setSweepingOutputScope(PIPELINE);
    when(executionContext.getApp()).thenReturn(anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build());
    when(executionContext.fetchRequiredApp()).thenReturn(anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build());
    when(executionContext.getAppId()).thenReturn(APP_ID);
    when(executionContext.getEnv()).thenReturn(anEnvironment().uuid(ENV_ID).appId(APP_ID).build());
    HostConnectionAttributes hostConnectionAttributes =
        aHostConnectionAttributes()
            .withAccessType(KEY)
            .withAccountId(UUIDGenerator.generateUuid())
            .withConnectionType(HostConnectionAttributes.ConnectionType.SSH)
            .withKey("Test Private Key".toCharArray())
            .withKeyless(false)
            .withUserName("TestUser")
            .build();
    when(executionContext.getGlobalSettingValue(ACCOUNT_ID, SETTING_ID)).thenReturn(hostConnectionAttributes);
    when(executionContext.renderExpression(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class));
    when(activityHelperService.createAndSaveActivity(executionContext, Type.Verification, shellScriptState.getName(),
             shellScriptState.getStateType(),
             asList(aCommand().withName(ShellScriptParameters.CommandUnit).withCommandType(CommandType.OTHER).build())))
        .thenReturn(ACTIVITY_WITH_ID);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponseOnShellScriptSuccessAndSaveSweepingOutput() {
    Reflect.on(shellScriptState).set("kryoSerializer", kryoSerializer);
    when(executionContext.getStateExecutionData())
        .thenReturn(ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build());
    when(executionContext.prepareSweepingOutputBuilder(any(SweepingOutputInstance.Scope.class)))
        .thenReturn(SweepingOutputInstance.builder());

    Map<String, String> map = new HashMap<>();
    map.put("A", "aaa");
    ExecutionResponse executionResponse = shellScriptState.handleAsyncResponse(executionContext,
        ImmutableMap.of(ACTIVITY_ID,
            CommandExecutionResult.builder()
                .status(CommandExecutionStatus.SUCCESS)
                .commandExecutionData(ShellExecutionData.builder().sweepingOutputEnvVariables(map).build())
                .build()));
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
    verify(sweepingOutputService, times(1)).save(any(SweepingOutputInstance.class));
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(
        ((ScriptStateExecutionData) (executionResponse.getStateExecutionData())).getSweepingOutputEnvVariables().size())
        .isEqualTo(1);
    assertThat(((ScriptStateExecutionData) (executionResponse.getStateExecutionData())).getSweepingOutputEnvVariables())
        .containsKey("A");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponseOnShellScriptFailureAndNotSaveSweepingOutput() {
    when(executionContext.getStateExecutionData())
        .thenReturn(ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build());
    Map<String, String> map = new HashMap<>();
    map.put("A", "aaa");
    ExecutionResponse executionResponse = shellScriptState.handleAsyncResponse(executionContext,
        ImmutableMap.of(ACTIVITY_ID,
            CommandExecutionResult.builder()
                .status(CommandExecutionStatus.FAILURE)
                .commandExecutionData(ShellExecutionData.builder().sweepingOutputEnvVariables(map).build())
                .build()));
    verify(activityHelperService).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
    verify(sweepingOutputService, times(0)).save(any(SweepingOutputInstance.class));
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(((ScriptStateExecutionData) (executionResponse.getStateExecutionData())).getSweepingOutputEnvVariables())
        .isNull();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldFailShellScriptStateOnErrorResponse() {
    ExecutionResponse executionResponse = shellScriptState.handleAsyncResponse(executionContext,
        ImmutableMap.of(ACTIVITY_ID, ErrorNotifyResponseData.builder().errorMessage("Failed").build()));
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldGetPatternsForRequiredContextElementType() {
    shellScriptState.setScriptString("echo \"Hello world\"");
    shellScriptState.setHost("somehost");
    List<String> strings = shellScriptState.getPatternsForRequiredContextElementType();
    assertThat(strings).isNotEmpty();
    assertThat(strings).contains("echo \"Hello world\"", "somehost");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldExecuteOnDelegate() throws IllegalAccessException {
    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("var1", "John Doe");
    setFieldsInShellScriptState(true);
    FieldUtils.writeField(shellScriptState, "executeOnDelegate", true, true);

    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(aServiceTemplate().withUuid(TEMPLATE_ID).build());
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null, OBTAIN_VALUE))
        .thenReturn(emptyList());

    when(templateUtils.processTemplateVariables(any(), any())).thenReturn(variableMap);
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(executionContext.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(executionContext.renderExpression(eq("echo ${var1}"), any(StateExecutionContext.class)))
        .thenReturn("echo \"John Doe\"");
    when(workflowStandardParams.getAppId()).thenReturn(APP_ID);
    when(workflowStandardParams.getEnvId()).thenReturn(ENV_ID);
    when(workflowStandardParamsExtensionService.getApp(workflowStandardParams))
        .thenReturn(anApplication().uuid(APP_ID).name(APP_NAME).build());
    when(workflowStandardParamsExtensionService.getEnv(workflowStandardParams))
        .thenReturn(anEnvironment().uuid(ENV_ID).name(ENV_NAME).environmentType(NON_PROD).build());

    doReturn("TASKID").when(delegateService).queueTaskV2(any());
    ExecutionResponse response = shellScriptState.execute(executionContext);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(ScriptStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(
            ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build(), "activityId");
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getDelegateTaskId()).isEqualTo("TASKID");
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    ShellScriptParameters shellScriptParameters = (ShellScriptParameters) delegateTask.getData().getParameters()[0];
    assertThat(shellScriptParameters.getScript()).isEqualTo("echo \"John Doe\"");
    assertThat(shellScriptParameters.getOutputVars()).isEqualTo("var1");
    assertThat(shellScriptParameters.getConnectionType()).isEqualTo(SSH);
    assertThat(shellScriptParameters.getScriptType()).isEqualTo(BASH);
    assertThat(shellScriptParameters.isExecuteOnDelegate()).isTrue();
    assertThat(delegateTask.getTags()).contains("T1", "T2");

    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(stateExecutionService).appendDelegateTaskDetails(any(), any(DelegateTaskDetails.class));
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void shouldExecuteOnSpecificDelegate() throws IllegalAccessException {
    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("var1", "John Doe");
    setFieldsInShellScriptState(true);
    FieldUtils.writeField(shellScriptState, "executeOnDelegate", true, true);
    FieldUtils.writeField(
        shellScriptState, "mustExecuteOnDelegateId", "${workflow.variables.mustExecuteOnDelegateId}", true);

    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(aServiceTemplate().withUuid(TEMPLATE_ID).build());
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null, OBTAIN_VALUE))
        .thenReturn(emptyList());

    when(templateUtils.processTemplateVariables(any(), any())).thenReturn(variableMap);
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(executionContext.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(executionContext.renderExpression(eq("echo ${var1}"), any(StateExecutionContext.class)))
        .thenReturn("echo \"John Doe\"");

    when(executionContext.renderExpression(eq("${workflow.variables.mustExecuteOnDelegateId}")))
        .thenReturn("delegateId");

    when(workflowStandardParams.getAppId()).thenReturn(APP_ID);
    when(workflowStandardParams.getEnvId()).thenReturn(ENV_ID);
    when(workflowStandardParamsExtensionService.getApp(workflowStandardParams))
        .thenReturn(anApplication().uuid(APP_ID).name(APP_NAME).build());
    when(workflowStandardParamsExtensionService.getEnv(workflowStandardParams))
        .thenReturn(anEnvironment().uuid(ENV_ID).name(ENV_NAME).environmentType(NON_PROD).build());

    doReturn("TASKID").when(delegateService).queueTaskV2(any());
    ExecutionResponse response = shellScriptState.execute(executionContext);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(ScriptStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(
            ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build(), "activityId");
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getDelegateTaskId()).isEqualTo("TASKID");
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    ShellScriptParameters shellScriptParameters = (ShellScriptParameters) delegateTask.getData().getParameters()[0];
    assertThat(shellScriptParameters.getScript()).isEqualTo("echo \"John Doe\"");
    assertThat(shellScriptParameters.getOutputVars()).isEqualTo("var1");
    assertThat(shellScriptParameters.getConnectionType()).isEqualTo(SSH);
    assertThat(shellScriptParameters.getScriptType()).isEqualTo(BASH);
    assertThat(shellScriptParameters.isExecuteOnDelegate()).isTrue();
    assertThat(delegateTask.getTags()).contains("T1", "T2");

    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
    verify(stateExecutionService).appendDelegateTaskDetails(any(), any(DelegateTaskDetails.class));

    // Test when expression could not be evaluated

    when(executionContext.renderExpression(eq("${workflow.variables.mustExecuteOnDelegateId}"))).thenReturn("null");
    shellScriptState.execute(executionContext);
    captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(2)).queueTaskV2(captor.capture());
    delegateTask = captor.getValue();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void shouldExecuteOnTargetHostSSh() throws IllegalAccessException {
    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("var1", "John Doe");
    setFieldsForSSH();
    FieldUtils.writeField(shellScriptState, "sshKeyRef", "SSH_KEY_REF", true);

    when(templateUtils.processTemplateVariables(any(), any())).thenReturn(variableMap);
    shellScriptConditions("echo ${var1}", "echo \"John Doe\"");

    doReturn("TASKID").when(delegateService).queueTaskV2(any());
    HostConnectionAttributes hostConnectionAttributes = HostConnectionAttributes.Builder.aHostConnectionAttributes()
                                                            .withUserName("TestUser")
                                                            .withKeyPath("KEY_PATH")
                                                            .withAccessType(KEY)
                                                            .withAccountId(ACCOUNT_ID)
                                                            .build();
    when(settingsService.get(any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withName("SETTING")
                        .withValue(hostConnectionAttributes)
                        .withUuid("UUID")
                        .build());
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(asList());
    ExecutionResponse response = shellScriptState.execute(executionContext);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(ScriptStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(
            ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build(), "activityId");
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getDelegateTaskId()).isEqualTo("TASKID");

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    assertSSH(delegateTask);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldExecuteOnTemplateTargetHostSSh() throws IllegalAccessException {
    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("var1", "John Doe");
    setFieldsForSSH();

    List<TemplateExpression> templateExpressions = new ArrayList<>();
    templateExpression.setFieldName("sshKeyRef");
    templateExpression.setExpression("${SSH_ConnectionAttribute}");
    templateExpressions.add(templateExpression);
    on(shellScriptState).set("templateExpressions", templateExpressions);

    shellScriptConditions("echo ${var1}", "echo \"John Doe\"");
    when(templateUtils.processTemplateVariables(any(), any())).thenReturn(variableMap);
    when(templateExpressionProcessor.getTemplateExpression(any(), eq("sshKeyRef"))).thenReturn(templateExpression);

    doReturn("TASKID").when(delegateService).queueTaskV2(any());
    HostConnectionAttributes hostConnectionAttributes = HostConnectionAttributes.Builder.aHostConnectionAttributes()
                                                            .withUserName("TestUser")
                                                            .withKeyPath("KEY_PATH")
                                                            .withAccessType(KEY)
                                                            .withAccountId(ACCOUNT_ID)
                                                            .build();
    when(templateExpressionProcessor.resolveSettingAttribute(any(), any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withName("SETTING")
                        .withValue(hostConnectionAttributes)
                        .withUuid("UUID")
                        .build());

    when(templateExpressionProcessor.resolveTemplateExpression(any(), any())).thenReturn("sshKey");
    when(settingsService.get(any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withName("SETTING")
                        .withValue(hostConnectionAttributes)
                        .withUuid("UUID")
                        .build());
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(asList());
    ExecutionResponse response = shellScriptState.execute(executionContext);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(ScriptStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(
            ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build(), "activityId");
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getDelegateTaskId()).isEqualTo("TASKID");

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    assertSSH(delegateTask);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldExecuteOnTargetWinrm() throws IllegalAccessException {
    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("var1", "John Doe");
    setFieldsForWinRm();
    FieldUtils.writeField(shellScriptState, "connectionAttributes", "CONNECTION_ATTRIBUTE", true);

    when(templateUtils.processTemplateVariables(any(), any())).thenReturn(variableMap);
    shellScriptConditions("Write-Host ${var1}", "Write-Host \"John Doe\"");

    doReturn("TASKID").when(delegateService).queueTaskV2(any());
    WinRmConnectionAttributes winRmConnectionAttributes = new WinRmConnectionAttributes(
        null, "", "TestUser", new char[10], true, 80, true, false, null, true, ACCOUNT_ID, "", null);

    when(settingsService.get(any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withName("SETTING")
                        .withValue(winRmConnectionAttributes)
                        .withUuid("UUID")
                        .build());
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(asList());
    ExecutionResponse response = shellScriptState.execute(executionContext);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(ScriptStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(
            ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build(), "activityId");
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getDelegateTaskId()).isEqualTo("TASKID");

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    assertWinRm(delegateTask);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldExecuteOnTemplateTargetWinrm() throws IllegalAccessException {
    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("var1", "John Doe");
    setFieldsInShellScriptState(false);
    setFieldsForWinRm();

    List<TemplateExpression> templateExpressions = new ArrayList<>();
    templateExpression.setFieldName("connectionAttributes");
    templateExpression.setExpression("${WinRM_ConnectionAttribute}");
    templateExpressions.add(templateExpression);
    on(shellScriptState).set("templateExpressions", templateExpressions);

    shellScriptConditions("Write-Host ${var1}", "Write-Host \"John Doe\"");
    when(templateUtils.processTemplateVariables(any(), any())).thenReturn(variableMap);

    when(templateExpressionProcessor.getTemplateExpression(any(), eq("connectionAttributes")))
        .thenReturn(templateExpression);

    doReturn("TASKID").when(delegateService).queueTaskV2(any());
    WinRmConnectionAttributes winRmConnectionAttributes = new WinRmConnectionAttributes(
        null, "", "TestUser", new char[10], true, 80, true, false, null, true, ACCOUNT_ID, "", null);
    when(templateExpressionProcessor.resolveSettingAttribute(any(), any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withName("SETTING")
                        .withValue(winRmConnectionAttributes)
                        .withUuid("UUID")
                        .build());
    when(templateExpressionProcessor.resolveTemplateExpression(any(), any())).thenReturn("winrmKey");
    when(settingsService.get(any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withName("SETTING")
                        .withValue(winRmConnectionAttributes)
                        .withUuid("UUID")
                        .build());
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(asList());
    ExecutionResponse response = shellScriptState.execute(executionContext);
    assertThat(response).isNotNull().extracting(ExecutionResponse::isAsync).isEqualTo(true);
    assertThat(response.getStateExecutionData())
        .isNotNull()
        .isInstanceOf(ScriptStateExecutionData.class)
        .isEqualToComparingOnlyGivenFields(
            ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build(), "activityId");
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getDelegateTaskId()).isEqualTo("TASKID");

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    assertWinRm(delegateTask);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testSelectionLogsTrackingForTasksEnabled() {
    assertThat(shellScriptState.isSelectionLogsTrackingForTasksEnabled()).isTrue();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldRaiseErrorOnWinRm() throws IllegalAccessException {
    setFieldsForWinRm();

    shellScriptConditions("Write-Host ${var1}", "Write-Host \"John Doe\"");

    when(templateExpressionProcessor.getTemplateExpression(any(), eq("connectionAttributes")))
        .thenReturn(templateExpression);

    doReturn("TASKID").when(delegateService).queueTaskV2(any());
    WinRmConnectionAttributes winRmConnectionAttributes = new WinRmConnectionAttributes(
        null, "", "TestUser", new char[10], true, 80, true, false, null, true, ACCOUNT_ID, "", null);
    when(templateExpressionProcessor.resolveSettingAttribute(any(), any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withName("SETTING")
                        .withValue(winRmConnectionAttributes)
                        .withUuid("UUID")
                        .build());
    when(settingsService.get(any()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withName("SETTING")
                        .withValue(winRmConnectionAttributes)
                        .withUuid("UUID")
                        .build());
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(asList());
    assertThatThrownBy(() -> shellScriptState.execute(executionContext))
        .isInstanceOf(ShellScriptException.class)
        .hasMessage("WinRM Connection Attribute not provided in Shell Script Step");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void shouldSaveSweepingOutputWithSecretAndStringEnvVariables() {
    shellScriptState.setOutputVars("A");
    shellScriptState.setSecretOutputVars("secretVar");

    SimpleEncryption simpleEncryption = new SimpleEncryption();

    Map<String, String> map = new HashMap<>();
    map.put("A", "aaa");
    map.put("secretVar", "secretValue");

    ShellScriptEnvironmentVariables shellScriptEnvironmentVariables = shellScriptState.buildSweepingOutput(map);

    assertThat(shellScriptEnvironmentVariables.getOutputVars()).isEqualTo(ImmutableMap.of("A", "aaa"));
    // save secret variables as encrypted
    String decryptedValue = new String(simpleEncryption.decrypt(
        Base64.getDecoder().decode(shellScriptEnvironmentVariables.getSecretOutputVars().get("secretVar"))));
    assertThat(decryptedValue).isEqualTo("secretValue");
  }

  private void setFieldsInShellScriptState(boolean ssh) throws IllegalAccessException {
    on(shellScriptState).set("templateUtils", templateUtils);
    on(shellScriptState).set("activityHelperService", activityHelperService);
    on(shellScriptState).set("infrastructureMappingService", infrastructureMappingService);
    on(shellScriptState).set("serviceTemplateService", serviceTemplateService);
    on(shellScriptState).set("delegateService", delegateService);
    if (ssh) {
      FieldUtils.writeField(shellScriptState, "scriptString", "echo ${var1}", true);
    } else {
      FieldUtils.writeField(shellScriptState, "scriptString", "Write-Host ${var1}", true);
    }
    FieldUtils.writeField(shellScriptState, "sweepingOutputName", "out1", true);
    FieldUtils.writeField(shellScriptState, "outputVars", "var1", true);
    FieldUtils.writeField(shellScriptState, "sweepingOutputScope", WORKFLOW, true);
    FieldUtils.writeField(shellScriptState, "tags", asList("T1", "T2"), true);
  }

  private void setFieldsForWinRm() throws IllegalAccessException {
    on(shellScriptState).set("settingsService", settingsService);
    on(shellScriptState).set("secretManager", secretManager);
    on(shellScriptState).set("connectionType", WINRM);
    on(shellScriptState).set("scriptType", POWERSHELL);
    setFieldsInShellScriptState(false);
    FieldUtils.writeField(shellScriptState, "executeOnDelegate", false, true);
    FieldUtils.writeField(shellScriptState, "host", "localhost", true);
  }

  private void assertWinRm(DelegateTask delegateTask) {
    ShellScriptParameters shellScriptParameters = (ShellScriptParameters) delegateTask.getData().getParameters()[0];
    assertThat(shellScriptParameters.getScript()).isEqualTo("Write-Host \"John Doe\"");
    assertThat(shellScriptParameters.getOutputVars()).isEqualTo("var1");
    assertThat(shellScriptParameters.getHost()).isEqualTo("localhost");
    assertThat(shellScriptParameters.getConnectionType()).isEqualTo(WINRM);
    assertThat(shellScriptParameters.getScriptType()).isEqualTo(POWERSHELL);
    assertThat(shellScriptParameters.isExecuteOnDelegate()).isFalse();
    assertThat(shellScriptParameters.getWinrmConnectionAttributes().getUsername()).isEqualTo("TestUser");
    assertThat(shellScriptParameters.getWinrmConnectionAttributes().getPort()).isEqualTo(80);
    assertThat(delegateTask.getTags()).contains("T1", "T2");
    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
  }

  private void setFieldsForSSH() throws IllegalAccessException {
    on(shellScriptState).set("settingsService", settingsService);
    on(shellScriptState).set("secretManager", secretManager);
    setFieldsInShellScriptState(true);
    FieldUtils.writeField(shellScriptState, "executeOnDelegate", false, true);
    FieldUtils.writeField(shellScriptState, "host", "localhost", true);
  }

  private void assertSSH(DelegateTask delegateTask) {
    ShellScriptParameters shellScriptParameters = (ShellScriptParameters) delegateTask.getData().getParameters()[0];
    assertThat(shellScriptParameters.getScript()).isEqualTo("echo \"John Doe\"");
    assertThat(shellScriptParameters.getOutputVars()).isEqualTo("var1");
    assertThat(shellScriptParameters.getHost()).isEqualTo("localhost");
    assertThat(shellScriptParameters.getConnectionType()).isEqualTo(SSH);
    assertThat(shellScriptParameters.getScriptType()).isEqualTo(BASH);
    assertThat(shellScriptParameters.isExecuteOnDelegate()).isFalse();
    assertThat(shellScriptParameters.getHostConnectionAttributes().getUserName()).isEqualTo("TestUser");
    assertThat(shellScriptParameters.getHostConnectionAttributes().getKeyPath()).isEqualTo("KEY_PATH");
    assertThat(shellScriptParameters.getHostConnectionAttributes().getAccessType()).isEqualTo(KEY);
    assertThat(shellScriptParameters.getHostConnectionAttributes().getSshPort()).isEqualTo(22);
    assertThat(delegateTask.getTags()).contains("T1", "T2");
    verify(activityHelperService).createAndSaveActivity(any(), any(), any(), any(), any());
  }

  private void shellScriptConditions(String script, String output) {
    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(aServiceTemplate().withUuid(TEMPLATE_ID).build());
    when(serviceTemplateService.computeServiceVariables(APP_ID, ENV_ID, TEMPLATE_ID, null, OBTAIN_VALUE))
        .thenReturn(emptyList());

    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(executionContext.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(executionContext.renderExpression(eq(script), any(StateExecutionContext.class))).thenReturn(output);
    when(executionContext.renderExpression(eq("localhost"), any(StateExecutionContext.class))).thenReturn("localhost");
    when(workflowStandardParams.getAppId()).thenReturn(APP_ID);
    when(workflowStandardParams.getEnvId()).thenReturn(ENV_ID);
    when(workflowStandardParamsExtensionService.getApp(workflowStandardParams))
        .thenReturn(anApplication().uuid(APP_ID).name(APP_NAME).build());
    when(workflowStandardParamsExtensionService.getEnv(workflowStandardParams))
        .thenReturn(anEnvironment().uuid(ENV_ID).name(ENV_NAME).environmentType(NON_PROD).build());
  }

  @Test
  @Owner(developers = NAVNEET)
  @Category(UnitTests.class)
  public void shouldSkipAddCloudProviderDelegateTag() throws IllegalAccessException {
    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("var", "Sample Variable");

    setFieldsInShellScriptState(true);
    FieldUtils.writeField(shellScriptState, "executeOnDelegate", true, true);
    FieldUtils.writeField(shellScriptState, "includeInfraSelectors", false, true);

    when(templateUtils.processTemplateVariables(any(), any())).thenReturn(variableMap);
    when(executionContext.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(executionContext.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(executionContext.renderExpression(eq("echo ${var}"), any(StateExecutionContext.class)))
        .thenReturn("echo \"Sample Variable\"");
    when(workflowStandardParams.getAppId()).thenReturn(APP_ID);
    when(workflowStandardParamsExtensionService.getApp(workflowStandardParams))
        .thenReturn(anApplication().uuid(APP_ID).name(APP_NAME).build());
    when(workflowStandardParamsExtensionService.getEnv(workflowStandardParams))
        .thenReturn(anEnvironment().uuid(ENV_ID).name(ENV_NAME).environmentType(NON_PROD).build());
    doReturn("TASKID").when(delegateService).queueTaskV2(any());

    ShellScriptState newShellScriptState = spy(shellScriptState);

    newShellScriptState.execute(executionContext);
    verify(newShellScriptState, never()).getTagFromCloudProvider(any());
  }
}
