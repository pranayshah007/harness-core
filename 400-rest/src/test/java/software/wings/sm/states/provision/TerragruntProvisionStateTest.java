/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.SweepingOutputInstance.builder;
import static io.harness.context.ContextElementType.TERRAGRUNT_INHERIT_PLAN;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.REPO_NAME;
import static software.wings.utils.WingsTestConstants.SOURCE_REPO_SETTINGS_ID;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.FileMetadata;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.provision.TfVarScriptRepositorySource;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ScriptStateExecutionData;
import software.wings.api.terragrunt.TerragruntExecutionData;
import software.wings.api.terragrunt.TerragruntProvisionInheritPlanElement;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.KmsConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.PhaseStep;
import software.wings.beans.TerragruntInfrastructureProvisioner;
import software.wings.beans.delegation.TerragruntProvisionParameters;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.terragrunt.TerragruntStateHelper;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.utils.GitUtilsManager;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collector;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class TerragruntProvisionStateTest extends WingsBaseTest {
  @Mock InfrastructureProvisionerService infrastructureProvisionerService;
  @Mock private DelegateService delegateService;
  @Mock private ActivityService activityService;
  @Mock private GitUtilsManager gitUtilsManager;
  @Mock private FileService fileService;
  @Mock private SecretManager secretManager;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private ExecutionContextImpl executionContext;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private GitConfigHelperService gitConfigHelperService;
  @Mock private TerraformPlanHelper terraformPlanHelper;
  @Mock private SecretManagerConfigService secretManagerConfigService;
  @Spy private GitFileConfigHelperService gitFileConfigHelperService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private ManagerExecutionLogCallback managerExecutionLogCallback;
  @Mock private TerragruntStateHelper terragruntStateHelper;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @InjectMocks private TerragruntProvisionState state = new TerragruntApplyState("tg");
  @InjectMocks private TerragruntProvisionState destroyProvisionState = new TerragruntDestroyState("tg");

  private final Answer<String> answer = invocation -> invocation.getArgument(0, String.class) + "-rendered";
  private final GitConfig gitConfig = GitConfig.builder().branch("master").build();
  @Captor private ArgumentCaptor<Map<String, Object>> argCaptor;

  @Before
  public void setUp() {
    initMocks(this);

    BiFunction<String, Collector, Answer> extractVariablesOfType = (type, collector) -> {
      return invocation -> {
        List<NameValuePair> input = invocation.getArgument(0, List.class);
        return input.stream().filter(value -> type.equals(value.getValueType())).collect(collector);
      };
    };
    Answer doExtractTextVariables =
        extractVariablesOfType.apply("TEXT", toMap(NameValuePair::getName, NameValuePair::getValue));
    Answer doExtractEncryptedVariables = extractVariablesOfType.apply("ENCRYPTED_TEXT",
        toMap(NameValuePair::getName, entry -> EncryptedDataDetail.builder().fieldName(entry.getName()).build()));
    Answer<String> doReturnSameValue = invocation -> invocation.getArgument(0, String.class);

    doAnswer(doExtractTextVariables).when(infrastructureProvisionerService).extractUnresolvedTextVariables(anyList());
    doAnswer(doExtractEncryptedVariables)
        .when(infrastructureProvisionerService)
        .extractEncryptedTextVariables(anyList(), anyString(), any());
    doAnswer(doReturnSameValue).when(executionContext).renderExpression(anyString());
    doAnswer(doReturnSameValue).when(executionContext).renderExpression(anyString(), any(StateExecutionContext.class));

    doReturn(Activity.builder().uuid("uuid").build()).when(activityService).save(any(Activity.class));

    doAnswer(doReturnSameValue).when(executionContext).renderExpression(anyString());
    doAnswer(doReturnSameValue).when(executionContext).renderExpression(anyString(), any(StateExecutionContext.class));
    doReturn(APP_ID).when(executionContext).getAppId();
    doReturn(ACCOUNT_ID).when(executionContext).getAccountId();
    doReturn(Environment.Builder.anEnvironment().build()).when(executionContext).getEnv();
    doReturn(Application.Builder.anApplication().appId(APP_ID).build()).when(executionContext).getApp();
    doReturn(WorkflowStandardParams.Builder.aWorkflowStandardParams()
                 .withCurrentUser(EmbeddedUser.builder().name("name").build())
                 .build())
        .when(executionContext)
        .getContextElement(any(ContextElementType.class));

    doReturn(null).when(executionContext).getContextElement(ContextElementType.TERRAFORM_PROVISION);
    doReturn(SweepingOutputInquiry.builder()).when(executionContext).prepareSweepingOutputInquiryBuilder();
    doReturn(builder()).when(executionContext).prepareSweepingOutputBuilder(any(SweepingOutputInstance.Scope.class));

    when(featureFlagService.isEnabled(eq(FeatureName.EXPORT_TF_PLAN), anyString())).thenReturn(true);
    doReturn(KmsConfig.builder().accountId(ACCOUNT_ID).name("name").build())
        .when(secretManagerConfigService)
        .getSecretManager(any(), any(), any());
    doReturn(KmsConfig.builder().accountId(ACCOUNT_ID).name("name").build())
        .when(secretManagerConfigService)
        .getDefaultSecretManager(any());
    doReturn(gitConfig).when(terragruntStateHelper).populateAndGetGitConfig(any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testStateTimeout() {
    testTimeoutInternal(new TerragruntApplyState("tg"));
    testTimeoutInternal(new TerragruntDestroyState("tg"));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteTerragruntDestroyStateWithConfiguration() {
    destroyProvisionState.setVariables(getTerragruntVariables());
    destroyProvisionState.setBackendConfigs(getTerragruntBackendConfigs());
    destroyProvisionState.setProvisionerId(PROVISIONER_ID);
    destroyProvisionState.setPathToModule("module1");
    TerragruntInfrastructureProvisioner provisioner = TerragruntInfrastructureProvisioner.builder()
                                                          .appId(APP_ID)
                                                          .path("current/working/directory")
                                                          .skipRefreshBeforeApplyingPlan(true)
                                                          .repoName(REPO_NAME)
                                                          .build();

    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTaskV2(any(DelegateTask.class));
    ExecutionResponse response = destroyProvisionState.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerragruntProvisionParameters parameters = (TerragruntProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getVariables()).isNotEmpty();
    assertThat(parameters.getBackendConfigs()).isNotEmpty();
    assertThat(parameters.isSkipRefreshBeforeApplyingPlan()).isTrue();
    assertParametersVariables(parameters);
    assertParametersBackendConfigs(parameters);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteTerragruntDestroyUsingFileMetaData() {
    destroyProvisionState.setProvisionerId(PROVISIONER_ID);
    destroyProvisionState.setPathToModule("module1");

    TerragruntInfrastructureProvisioner provisioner = TerragruntInfrastructureProvisioner.builder()
                                                          .appId(APP_ID)
                                                          .path("current/working/directory")
                                                          .skipRefreshBeforeApplyingPlan(true)
                                                          .repoName(REPO_NAME)
                                                          .build();
    FileMetadata fileMetadata = FileMetadata.builder()
                                    .metadata(ImmutableMap.<String, Object>builder()
                                                  .put("targets", asList("target1", "target2"))
                                                  .put("tf_var_files", asList("file1", "file2"))
                                                  .build())
                                    .build();

    doReturn("fileId")
        .when(fileService)
        .getLatestFileIdByQualifier(anyString(), eq(FileBucket.TERRAFORM_STATE), eq("apply"));
    doReturn(fileMetadata).when(fileService).getFileMetadata("fileId", FileBucket.TERRAFORM_STATE);
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("taskId").when(delegateService).queueTaskV2(any(DelegateTask.class));
    doAnswer(invocation -> invocation.getArgument(0, String.class) + "-rendered")
        .when(executionContext)
        .renderExpression(anyString());
    ExecutionResponse response = destroyProvisionState.execute(executionContext);

    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(taskCaptor.capture());
    DelegateTask createdTask = taskCaptor.getValue();
    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());

    verify(terragruntStateHelper, times(1)).extractBackendConfigs(any());
    verify(terragruntStateHelper, times(2)).extractData(any(), any());
    verify(terragruntStateHelper, times(3)).extractEncryptedData(any(), any(), any());

    assertThat(response.isAsync()).isTrue();
    assertThat(createdTask.getSetupAbstractions().get(Cd1SetupFields.APP_ID_FIELD)).isEqualTo(APP_ID);
    assertThat(createdTask.getData().getParameters()).isNotEmpty();
    TerragruntProvisionParameters parameters = (TerragruntProvisionParameters) createdTask.getData().getParameters()[0];
    assertThat(parameters.getTfVarFiles()).containsExactlyInAnyOrder("file1-rendered", "file2-rendered");
    assertThat(parameters.getTfVarSource()).isNotNull();
    TfVarScriptRepositorySource source = (TfVarScriptRepositorySource) parameters.getTfVarSource();
    assertThat(source.getTfVarFilePaths()).containsExactlyInAnyOrder("file1-rendered", "file2-rendered");
    assertThat(parameters.getTargets()).containsExactlyInAnyOrder("target1-rendered", "target2-rendered");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteInheritApprovedPlan() {
    state.setInheritApprovedPlan(true);
    state.setProvisionerId(PROVISIONER_ID);
    EncryptedRecordData encryptedPlan =
        EncryptedRecordData.builder().name("terraformPlan").encryptedValue("terraformPlan".toCharArray()).build();
    List<ContextElement> terragruntProvisionInheritPlanElements = new ArrayList<>();
    TerragruntProvisionInheritPlanElement terragruntProvisionInheritPlanElement =
        TerragruntProvisionInheritPlanElement.builder()
            .provisionerId(PROVISIONER_ID)
            .workspace("workspace")
            .sourceRepoReference("sourceRepoReference")
            .backendConfigs(getTerragruntBackendConfigs())
            .environmentVariables(getTerragruntEnvironmentVariables())
            .targets(Arrays.asList("target1"))
            .variables(getTerragruntVariables())
            .encryptedTfPlan(encryptedPlan)
            .pathToModule("module1")
            .sourceRepoSettingId(SOURCE_REPO_SETTINGS_ID)
            .build();
    terragruntProvisionInheritPlanElements.add(terragruntProvisionInheritPlanElement);
    when(executionContext.getContextElementList(TERRAGRUNT_INHERIT_PLAN))
        .thenReturn(terragruntProvisionInheritPlanElements);

    when(executionContext.getAppId()).thenReturn(APP_ID);
    doReturn(Environment.Builder.anEnvironment().build()).when(executionContext).getEnv();

    TerragruntInfrastructureProvisioner provisioner = TerragruntInfrastructureProvisioner.builder()
                                                          .appId(APP_ID)
                                                          .path("current/working/directory")
                                                          .sourceRepoBranch("sourceRepoBranch")
                                                          .repoName(REPO_NAME)
                                                          .secretManagerId("secretManagerID")
                                                          .build();

    doReturn(gitConfig).when(gitUtilsManager).getGitConfig(anyString());
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn("fileId").when(fileService).getLatestFileId(anyString(), eq(FileBucket.TERRAFORM_STATE));
    when(executionContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    when(secretManager.getEncryptionDetails(gitConfig, GLOBAL_APP_ID, WORKFLOW_EXECUTION_ID))
        .thenReturn(encryptedDataDetails);
    ExecutionResponse executionResponse = state.execute(executionContext);

    verify(gitConfigHelperService).convertToRepoGitConfig(any(GitConfig.class), anyString());
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    verify(fileService, times(1)).getLatestFileId(anyString(), any(FileBucket.class));
    verify(gitUtilsManager, times(1)).getGitConfig(anyString());
    // once for environment variables, once for variables, once for backend configs
    verify(infrastructureProvisionerService, times(3))
        .extractEncryptedTextVariables(anyList(), eq(APP_ID), anyString());
    // once for environment variables, once for variables
    verify(infrastructureProvisionerService, times(3)).extractUnresolvedTextVariables(anyList());
    verify(secretManager, times(1)).getEncryptionDetails(any(GitConfig.class), anyString(), anyString());
    verify(terragruntStateHelper, times(1)).getSecretManagerContainingTfPlan(any(), any());
    assertThat(executionResponse.getCorrelationIds().get(0)).isEqualTo("uuid");
    assertThat(((ScriptStateExecutionData) executionResponse.getStateExecutionData()).getActivityId())
        .isEqualTo("uuid");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteTerragruntDestroyRunAllWithFileMetaDataThrowsException() {
    destroyProvisionState.setProvisionerId(PROVISIONER_ID);
    destroyProvisionState.setPathToModule("module1");
    destroyProvisionState.setRunAll(true);

    TerragruntInfrastructureProvisioner provisioner = TerragruntInfrastructureProvisioner.builder()
                                                          .appId(APP_ID)
                                                          .path("current/working/directory")
                                                          .skipRefreshBeforeApplyingPlan(true)
                                                          .build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    assertThatThrownBy(() -> destroyProvisionState.execute(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Configuration Required, Local state file is not supported for Run-All commands");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteInheritApprovedPlanRunAllThrowsException() {
    state.setInheritApprovedPlan(true);
    state.setProvisionerId(PROVISIONER_ID);
    state.setPathToModule("module1");
    state.setRunAll(true);
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Terraform plan can't be exported or inherited while using run-all commands");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteInternalInheritedInvalid() {
    state.setInheritApprovedPlan(true);
    state.setProvisionerId(PROVISIONER_ID);
    TerragruntInfrastructureProvisioner provisioner = TerragruntInfrastructureProvisioner.builder()
                                                          .appId(APP_ID)
                                                          .path("current/working/directory")
                                                          .variables(getTerragruntVariables())
                                                          .build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);

    // No previous terraform plan executed
    doReturn(Collections.emptyList()).when(executionContext).getContextElementList(TERRAGRUNT_INHERIT_PLAN);
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No previous Terragrunt plan execution found");

    // No previous terraform plan executed for PROVISIONER_ID
    doReturn(
        Collections.singletonList(TerragruntProvisionInheritPlanElement.builder().provisionerId("NOT_THIS_ID").build()))
        .when(executionContext)
        .getContextElementList(TERRAGRUNT_INHERIT_PLAN);
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No Terragrunt provision command found with current provisioner");

    // Invalid provisioner path
    doReturn(null).when(executionContext).renderExpression("current/working/directory");
    doReturn(Collections.singletonList(
                 TerragruntProvisionInheritPlanElement.builder().provisionerId(PROVISIONER_ID).build()))
        .when(executionContext)
        .getContextElementList(TERRAGRUNT_INHERIT_PLAN);
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid Terragrunt script path");

    // Empty pathToModule
    doReturn("current/working/directory").when(executionContext).renderExpression("current/working/directory");
    doReturn(Collections.singletonList(TerragruntProvisionInheritPlanElement.builder()
                                           .sourceRepoReference(null)
                                           .provisionerId(PROVISIONER_ID)
                                           .build()))
        .when(executionContext)
        .getContextElementList(TERRAGRUNT_INHERIT_PLAN);
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Invalid Terragrunt module path");

    // Empty source repo reference
    doReturn("current/working/directory").when(executionContext).renderExpression("current/working/directory");
    doReturn(Collections.singletonList(TerragruntProvisionInheritPlanElement.builder()
                                           .sourceRepoReference(null)
                                           .provisionerId(PROVISIONER_ID)
                                           .pathToModule("module1")
                                           .build()))
        .when(executionContext)
        .getContextElementList(TERRAGRUNT_INHERIT_PLAN);
    assertThatThrownBy(() -> state.execute(executionContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No commit id found in context inherit terragrunt plan element");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternalRunPlanOnly() {
    state.setRunPlanOnly(true);
    state.setProvisionerId(PROVISIONER_ID);
    when(executionContext.getAppId()).thenReturn(APP_ID);
    Map<String, ResponseData> response = new HashMap<>();
    TerragruntExecutionData terragruntExecutionData =
        TerragruntExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).tfPlanJson("").build();
    response.put("activityId", terragruntExecutionData);
    TerragruntInfrastructureProvisioner provisioner =
        TerragruntInfrastructureProvisioner.builder().appId(APP_ID).build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(SweepingOutputInstance.builder())
        .when(executionContext)
        .prepareSweepingOutputBuilder(any(SweepingOutputInstance.Scope.class));

    ExecutionResponse executionResponse = state.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(terragruntExecutionData);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(
        ((TerragruntProvisionInheritPlanElement) executionResponse.getContextElements().get(0)).getProvisionerId())
        .isEqualTo(PROVISIONER_ID);
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    // once for saving the tf plan json variable
    verify(sweepingOutputService, times(1)).save(any(SweepingOutputInstance.class));
    verify(executionContext, times(1)).prepareSweepingOutputBuilder(eq(SweepingOutputInstance.Scope.PIPELINE));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternalRunPlanOnlyWithRunAll() {
    state.setRunPlanOnly(true);
    state.setProvisionerId(PROVISIONER_ID);
    state.setExportPlanToApplyStep(true);
    when(executionContext.getAppId()).thenReturn(APP_ID);
    Map<String, ResponseData> response = new HashMap<>();
    TerragruntExecutionData terragruntExecutionData =
        TerragruntExecutionData.builder().executionStatus(ExecutionStatus.SUCCESS).tfPlanJson("").runAll(true).build();
    response.put("activityId", terragruntExecutionData);
    TerragruntInfrastructureProvisioner provisioner =
        TerragruntInfrastructureProvisioner.builder().appId(APP_ID).build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(SweepingOutputInstance.builder())
        .when(executionContext)
        .prepareSweepingOutputBuilder(any(SweepingOutputInstance.Scope.class));

    ExecutionResponse executionResponse = state.handleAsyncResponse(executionContext, response);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(terragruntExecutionData);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(
        ((TerragruntProvisionInheritPlanElement) executionResponse.getContextElements().get(0)).getProvisionerId())
        .isEqualTo(PROVISIONER_ID);
    // state file should not be updated for run-all commands
    verify(fileService, never()).updateParentEntityIdAndVersion(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseSavePlan() {
    state.setRunPlanOnly(true);
    state.setExportPlanToApplyStep(true);
    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId",
        TerragruntExecutionData.builder()
            .encryptedTfPlan(EncryptedRecordData.builder().build())
            .tfPlanJson("{}")
            .build());
    doReturn("workflowExecutionId").when(executionContext).getWorkflowExecutionId();
    state.setProvisionerId(PROVISIONER_ID);
    doReturn(SweepingOutputInquiry.builder()).when(executionContext).prepareSweepingOutputInquiryBuilder();
    doReturn(TerragruntInfrastructureProvisioner.builder().build())
        .when(infrastructureProvisionerService)
        .get(APP_ID, PROVISIONER_ID);
    doReturn(SweepingOutputInstance.builder())
        .when(executionContext)
        .prepareSweepingOutputBuilder(any(SweepingOutputInstance.Scope.class));
    ExecutionResponse executionResponse = state.handleAsyncResponse(executionContext, response);
    assertThat(
        ((TerragruntProvisionInheritPlanElement) executionResponse.getContextElements().get(0)).getProvisionerId())
        .isEqualTo(PROVISIONER_ID);
    // for saving encrypted plan in sweepingOutput
    verify(terraformPlanHelper, times(1)).saveEncryptedTfPlanToSweepingOutput(any(), any(), any());
    // for saving the tfplan json variable
    verify(sweepingOutputService, times(1)).save(any(SweepingOutputInstance.class));
    verify(executionContext, times(1)).prepareSweepingOutputBuilder(eq(SweepingOutputInstance.Scope.PIPELINE));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternalRegularFail() {
    state.setProvisionerId(PROVISIONER_ID);
    when(executionContext.getAppId()).thenReturn(APP_ID);
    Map<String, ResponseData> response = new HashMap<>();
    TerragruntExecutionData terraformExecutionData =
        TerragruntExecutionData.builder().executionStatus(ExecutionStatus.FAILED).build();
    response.put("activityId", terraformExecutionData);
    TerragruntInfrastructureProvisioner provisioner =
        TerragruntInfrastructureProvisioner.builder().appId(APP_ID).build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);

    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    doReturn(SweepingOutputInstance.builder())
        .when(executionContext)
        .prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW);

    ExecutionResponse executionResponse = state.handleAsyncResponse(executionContext, response);

    verify(terragruntStateHelper, times(1)).markApplyExecutionCompleted(any(), any(), any());
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(terraformExecutionData);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternalRegularDestroy() {
    TerragruntProvisionState destroyProvisionStateSpy = spy(destroyProvisionState);
    destroyProvisionStateSpy.setProvisionerId(PROVISIONER_ID);
    destroyProvisionStateSpy.setTargets(Arrays.asList("target1"));
    when(executionContext.getAppId()).thenReturn(APP_ID);
    String outputs = "{\n"
        + "\"key\": {\n"
        + "\"value\": \"value1\"\n"
        + "}\n"
        + "}";
    Map<String, ResponseData> response = new HashMap<>();
    TerragruntExecutionData terragruntExecutionData = TerragruntExecutionData.builder()
                                                          .workspace("workspace")
                                                          .executionStatus(ExecutionStatus.SUCCESS)
                                                          .activityId(ACTIVITY_ID)
                                                          .outputs(outputs)
                                                          .build();
    response.put("activityId", terragruntExecutionData);
    TerragruntInfrastructureProvisioner provisioner =
        TerragruntInfrastructureProvisioner.builder().appId(APP_ID).build();
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);

    when(infrastructureProvisionerService.getManagerExecutionCallback(anyString(), anyString(), anyString()))
        .thenReturn(mock(ManagerExecutionLogCallback.class));
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());

    ExecutionResponse executionResponse = destroyProvisionStateSpy.handleAsyncResponse(executionContext, response);

    verify(infrastructureProvisionerService, times(1))
        .regenerateInfrastructureMappings(
            anyString(), any(ExecutionContext.class), anyMap(), any(Optional.class), any(Optional.class));
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(terragruntExecutionData);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseSaveUserInputs() {
    List<NameValuePair> nameValuePairList =
        asList(NameValuePair.builder().name("key").value("value").valueType("TEXT").build(),
            NameValuePair.builder().name("password").value("password").valueType("ENCRYPTED_TEXT").build(),
            NameValuePair.builder().name("nil").valueType("TEXT").build(),
            NameValuePair.builder().name("noValueType").value("value").valueType(null).build());

    TerragruntExecutionData responseData = TerragruntExecutionData.builder()
                                               .executionStatus(ExecutionStatus.SUCCESS)
                                               .workspace("workspace")
                                               .targets(Collections.emptyList())
                                               .backendConfigs(nameValuePairList)
                                               .variables(nameValuePairList)
                                               .tfVarFiles(asList("file-1", "file-2"))
                                               .targets(asList("target1", "target2"))
                                               .branch("master")
                                               .pathToModule("module")
                                               .build();

    TerragruntInfrastructureProvisioner provisioner = TerragruntInfrastructureProvisioner.builder()
                                                          .appId(APP_ID)
                                                          .path("current/working/directory")
                                                          .variables(getTerragruntVariables())
                                                          .sourceRepoSettingId(SOURCE_REPO_SETTINGS_ID)
                                                          .build();
    Map<String, ResponseData> responseMap = ImmutableMap.of(ACTIVITY_ID, responseData);
    state.setRunPlanOnly(false);
    state.setProvisionerId(PROVISIONER_ID);
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);

    doCallRealMethod()
        .when(terragruntStateHelper)
        .collectVariables(any(), any(), anyString(), anyString(), anyBoolean());

    state.handleAsyncResponse(executionContext, responseMap);
    ArgumentCaptor<Map> othersArgumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(terragruntStateHelper, times(1))
        .saveTerragruntConfig(
            any(ExecutionContext.class), anyString(), any(TerragruntExecutionData.class), anyString());
    //    verify(wingsPersistence, times(1)).save(any(TerragruntConfig.class));
    //    verify(wingsPersistence, times(1)).save(any(TerraformConfig.class));
    verify(fileService, times(1))
        .updateParentEntityIdAndVersion(eq(PhaseStep.class), isNull(String.class), isNull(Integer.class),
            isNull(String.class), othersArgumentCaptor.capture(), eq(FileBucket.TERRAFORM_STATE));
    Map<String, Object> storeOthersMap = (Map<String, Object>) othersArgumentCaptor.getValue();

    Map<String, String> expectedEncryptedNameValuePair = ImmutableMap.of("password", "password");
    assertThat(storeOthersMap.get("variables")).isEqualTo(ImmutableMap.of("key", "value", "noValueType", "value"));
    assertThat(storeOthersMap.get("encrypted_variables")).isEqualTo(expectedEncryptedNameValuePair);
    assertThat(storeOthersMap.get("backend_configs")).isEqualTo(ImmutableMap.of("key", "value"));
    assertThat(storeOthersMap.get("encrypted_backend_configs")).isEqualTo(expectedEncryptedNameValuePair);
    assertThat(storeOthersMap.get("targets")).isEqualTo(responseData.getTargets());
    assertThat(storeOthersMap.get("tf_var_files")).isEqualTo(responseData.getTfVarFiles());
    assertThat(storeOthersMap.get("tf_workspace")).isEqualTo(responseData.getWorkspace());

    // Don't save terraform config if status is not SUCCESS
    reset(terragruntStateHelper);
    responseData.setExecutionStatus(ExecutionStatus.FAILED);
    state.handleAsyncResponse(executionContext, responseMap);
    verify(terragruntStateHelper, never())
        .saveTerragruntConfig(
            any(ExecutionContext.class), anyString(), any(TerragruntExecutionData.class), anyString());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseSaveDestroyStepUserInputs() {
    TerragruntExecutionData responseData = TerragruntExecutionData.builder()
                                               .executionStatus(ExecutionStatus.SUCCESS)
                                               .workspace("workspace")
                                               .targets(asList("target1", "target2"))
                                               .branch("master")
                                               .pathToModule("module")
                                               .build();

    TerragruntInfrastructureProvisioner provisioner = TerragruntInfrastructureProvisioner.builder()
                                                          .appId(APP_ID)
                                                          .path("current/working/directory")
                                                          .variables(getTerragruntVariables())
                                                          .sourceRepoSettingId(SOURCE_REPO_SETTINGS_ID)
                                                          .build();
    Map<String, ResponseData> responseMap = ImmutableMap.of(ACTIVITY_ID, responseData);
    destroyProvisionState.setRunPlanOnly(false);
    destroyProvisionState.setProvisionerId(PROVISIONER_ID);
    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);

    // Save terraform config if there any targets
    destroyProvisionState.setTargets(asList("target1", "target2"));
    destroyProvisionState.handleAsyncResponse(executionContext, responseMap);
    verify(terragruntStateHelper, times(1))
        .saveTerragruntConfig(
            any(ExecutionContext.class), anyString(), any(TerragruntExecutionData.class), anyString());
    verify(terragruntStateHelper, never()).deleteTerragruntConfig(anyString());

    // Delete terraform config if no any targets
    reset(terragruntStateHelper);
    destroyProvisionState.setTargets(Collections.emptyList());
    destroyProvisionState.handleAsyncResponse(executionContext, responseMap);
    verify(terragruntStateHelper, never())
        .saveTerragruntConfig(
            any(ExecutionContext.class), anyString(), any(TerragruntExecutionData.class), anyString());
    verify(terragruntStateHelper, times(1)).deleteTerragruntConfig(anyString());

    // Don't do anything if the status is not SUCCESS
    reset(terragruntStateHelper);
    responseData.setExecutionStatus(ExecutionStatus.FAILED);
    destroyProvisionState.handleAsyncResponse(executionContext, responseMap);
    verify(terragruntStateHelper, never())
        .saveTerragruntConfig(
            any(ExecutionContext.class), anyString(), any(TerragruntExecutionData.class), anyString());
    verify(terragruntStateHelper, never()).deleteTerragruntConfig(anyString());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCreateActivityOnExecute() {
    TerragruntProvisionState spyState = spy(state);
    doReturn(Activity.builder().uuid(ACTIVITY_ID).build()).when(activityService).save(any(Activity.class));
    doReturn(null).when(spyState).executeInternal(executionContext, ACTIVITY_ID); // ignore execution

    // Missing STANDARD context element
    doReturn(null).when(executionContext).getContextElement(ContextElementType.STANDARD);
    assertThatThrownBy(() -> spyState.execute(executionContext)).hasMessageContaining("workflowStandardParams");

    // Missing current user in context
    WorkflowStandardParams standardParams = WorkflowStandardParams.Builder.aWorkflowStandardParams().build();
    doReturn(standardParams).when(executionContext).getContextElement(ContextElementType.STANDARD);
    assertThatThrownBy(() -> spyState.execute(executionContext)).hasMessageContaining("currentUser");

    standardParams.setCurrentUser(EmbeddedUser.builder().name(USER_NAME).email(USER_EMAIL).build());
    doReturn(WorkflowType.ORCHESTRATION).when(executionContext).getWorkflowType();
    doReturn(WORKFLOW_EXECUTION_ID).when(executionContext).getWorkflowExecutionId();
    doReturn(WORKFLOW_NAME).when(executionContext).getWorkflowExecutionName();
    ArgumentCaptor<Activity> activityCaptor = ArgumentCaptor.forClass(Activity.class);

    // Valid with BUILD orchestration type
    doReturn(OrchestrationWorkflowType.BUILD).when(executionContext).getOrchestrationWorkflowType();
    spyState.execute(executionContext);
    verify(activityService, times(1)).save(activityCaptor.capture());
    assertCreatedActivity(activityCaptor.getValue(), GLOBAL_ENV_ID, GLOBAL_ENV_ID, ALL);

    // Valid with not BUILD orchestration type
    Environment env = Environment.Builder.anEnvironment()
                          .environmentType(EnvironmentType.NON_PROD)
                          .uuid(ENV_ID)
                          .name(ENV_NAME)
                          .build();
    doReturn(OrchestrationWorkflowType.BASIC).when(executionContext).getOrchestrationWorkflowType();
    doReturn(env).when(executionContext).getEnv();
    spyState.execute(executionContext);
    // 1 time from previous invocation
    verify(activityService, times(2)).save(activityCaptor.capture());
    assertCreatedActivity(activityCaptor.getValue(), ENV_NAME, ENV_ID, EnvironmentType.NON_PROD);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testSaveProvisionerOutputsOnResponse() {
    when(featureFlagService.isEnabled(eq(FeatureName.SAVE_TERRAFORM_OUTPUTS_TO_SWEEPING_OUTPUT), anyString()))
        .thenReturn(true);
    TerragruntInfrastructureProvisioner provisioner =
        TerragruntInfrastructureProvisioner.builder().appId(APP_ID).build();
    TerragruntExecutionData terraformExecutionData =
        TerragruntExecutionData.builder()
            .executionStatus(ExecutionStatus.SUCCESS)
            .outputs(
                "{\"outputVar\": { \"value\" :\"outputVarValue\"}, \"complex\": { \"value\": { \"output\": \"value\"}}}")
            .build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put("activityId", terraformExecutionData);
    state.setProvisionerId(PROVISIONER_ID);

    doReturn(APP_ID).when(executionContext).getAppId();

    doReturn(provisioner).when(infrastructureProvisionerService).get(APP_ID, PROVISIONER_ID);
    doReturn(SweepingOutputInstance.builder())
        .when(executionContext)
        .prepareSweepingOutputBuilder(SweepingOutputInstance.Scope.WORKFLOW);
    doReturn(managerExecutionLogCallback)
        .when(infrastructureProvisionerService)
        .getManagerExecutionCallback(APP_ID, "activityId", state.commandUnit().name());

    state.handleAsyncResponse(executionContext, response);
    verify(terragruntStateHelper).saveOutputs(any(ExecutionContext.class), argCaptor.capture());
    Map<String, Object> outputs = argCaptor.getValue();
    assertThat(outputs).isNotEmpty();
    assertThat(outputs.get("outputVar")).isEqualTo("outputVarValue");
    Map<String, String> complexVarValue = (Map<String, String>) outputs.get("complex");
    assertThat(complexVarValue.get("output")).isEqualTo("value");
  }

  private void testTimeoutInternal(TerragruntProvisionState state) {
    state.setTimeoutMillis(null);
    assertThat(state.getTimeoutMillis()).isNull();

    state.setTimeoutMillis(500);
    assertThat(state.getTimeoutMillis()).isEqualTo(500);
  }

  private List<NameValuePair> getTerragruntVariables() {
    return asList(NameValuePair.builder().name("region").value("us-east").valueType("TEXT").build(),
        NameValuePair.builder().name("vpc_id").value("vpc-id").valueType("TEXT").build(),
        NameValuePair.builder().name("access_key").value("access_key").valueType("ENCRYPTED_TEXT").build(),
        NameValuePair.builder().name("secret_key").value("secret_key").valueType("ENCRYPTED_TEXT").build());
  }

  private List<NameValuePair> getTerragruntBackendConfigs() {
    return asList(NameValuePair.builder().name("key").value("terraform.tfstate").valueType("TEXT").build(),
        NameValuePair.builder().name("bucket").value("tf-remote-state").valueType("TEXT").build(),
        NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build());
  }

  private List<NameValuePair> getTerragruntEnvironmentVariables() {
    return asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
        NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build());
  }

  private void assertParametersVariables(TerragruntProvisionParameters parameters) {
    assertThat(parameters.getVariables().keySet()).containsExactlyInAnyOrder("region", "vpc_id");
    assertThat(parameters.getVariables().values()).containsExactlyInAnyOrder("us-east", "vpc-id");
    assertThat(parameters.getEncryptedVariables().keySet()).containsExactlyInAnyOrder("access_key", "secret_key");
  }

  private void assertParametersBackendConfigs(TerragruntProvisionParameters parameters) {
    assertThat(parameters.getBackendConfigs().keySet()).containsExactlyInAnyOrder("key", "bucket");
    assertThat(parameters.getBackendConfigs().values())
        .containsExactlyInAnyOrder("terraform.tfstate", "tf-remote-state");
    assertThat(parameters.getEncryptedBackendConfigs().keySet()).containsExactlyInAnyOrder("access_token");
  }

  private void assertParametersEnvironmentVariables(TerragruntProvisionParameters parameters) {
    assertThat(parameters.getEnvironmentVariables().keySet()).containsOnly("TF_LOG");
    assertThat(parameters.getEnvironmentVariables().values()).containsOnly("TRACE");
    assertThat(parameters.getEncryptedEnvironmentVariables().keySet()).containsExactlyInAnyOrder("access_token");
  }

  private void assertCreatedActivity(Activity activity, String envName, String envId, EnvironmentType envType) {
    assertThat(activity.getWorkflowType()).isEqualTo(WorkflowType.ORCHESTRATION);
    assertThat(activity.getWorkflowExecutionId()).isEqualTo(WORKFLOW_EXECUTION_ID);
    assertThat(activity.getWorkflowExecutionName()).isEqualTo(WORKFLOW_NAME);
    assertThat(activity.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(activity.getTriggeredBy().getName()).isEqualTo(USER_NAME);
    assertThat(activity.getTriggeredBy().getEmail()).isEqualTo(USER_EMAIL);
    assertThat(activity.getEnvironmentName()).isEqualTo(envName);
    assertThat(activity.getEnvironmentId()).isEqualTo(envId);
    assertThat(activity.getEnvironmentType()).isEqualTo(envType);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testValidation() {
    assertThat(state.validateFields().size()).isNotEqualTo(0);
    state.setProvisionerId("test provisioner");
    assertThat(state.validateFields().size()).isEqualTo(0);
  }
}
