/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.APPLY;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand.DESTROY;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENTITY_ID;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.SOURCE_REPO_SETTINGS_ID;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKSPACE;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ScriptStateExecutionData;
import software.wings.api.terragrunt.TerragruntApplyMarkerParam;
import software.wings.api.terragrunt.TerragruntExecutionData;
import software.wings.app.MainConfiguration;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.TerragruntInfrastructureProvisioner;
import software.wings.beans.delegation.TerragruntProvisionParameters;
import software.wings.beans.infrastructure.TerraformConfig;
import software.wings.beans.infrastructure.instance.TerragruntConfig;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.terragrunt.TerragruntStateHelper;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.utils.GitUtilsManager;

import dev.morphia.query.Query;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collector;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class TerragruntRollbackStateTest extends WingsBaseTest {
  @Mock TerragruntConfig configParameter;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) ExecutionContextImpl executionContext;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) MainConfiguration configuration;
  @Mock private InfrastructureProvisionerService infrastructureProvisionerService;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private FileService fileService;
  @Mock private GitUtilsManager gitUtilsManager;
  @Mock private SecretManager secretManager;
  @Mock private DelegateService delegateService;
  @Mock private GitConfigHelperService gitConfigHelperService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private TerragruntStateHelper terragruntStateHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;

  @InjectMocks
  TerragruntRollbackState terragruntRollbackState = new TerragruntRollbackState("Rollback Terragrunt Test");

  @Before
  public void setup() {
    Answer<String> doReturnSameValue = invocation -> invocation.getArgument(0, String.class);
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
    doAnswer(doExtractTextVariables).when(infrastructureProvisionerService).extractUnresolvedTextVariables(anyList());
    doAnswer(doExtractEncryptedVariables)
        .when(infrastructureProvisionerService)
        .extractEncryptedTextVariables(anyList(), anyString(), any());
    doAnswer(doReturnSameValue).when(executionContext).renderExpression(anyString());
    when(executionContext.getAppId()).thenReturn(APP_ID);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteInternalNoApply() {
    terragruntRollbackState.setProvisionerId(PROVISIONER_ID);
    terragruntRollbackState.setPathToModule("module1");
    TerragruntInfrastructureProvisioner terragruntInfrastructureProvisioner =
        TerragruntInfrastructureProvisioner.builder().name("Terragrunt Provisioner").build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terragruntInfrastructureProvisioner);
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    SweepingOutputInstance sweepingOutputInstance =
        SweepingOutputInstance.builder()
            .value(TerragruntApplyMarkerParam.builder().applyCompleted(false).build())
            .build();
    when(sweepingOutputService.find(any(SweepingOutputInquiry.class))).thenReturn(sweepingOutputInstance);
    ExecutionResponse executionResponse = terragruntRollbackState.executeInternal(executionContext, ACTIVITY_ID);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    verify(sweepingOutputService, times(1)).find(any(SweepingOutputInquiry.class));

    // we didn't find a result in sweeping output
    when(sweepingOutputService.find(any(SweepingOutputInquiry.class))).thenReturn(null);
    executionResponse = terragruntRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    verify(infrastructureProvisionerService, times(2)).get(APP_ID, PROVISIONER_ID);
    verify(sweepingOutputService, times(2)).find(any(SweepingOutputInquiry.class));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteInternal() {
    when(terragruntStateHelper.getGitConfigAndPopulate(any(TerragruntConfig.class), anyString()))
        .thenReturn(GitConfig.builder().branch("sourceRepoBranch").build());
    setUp("sourceRepoBranch", true, WORKFLOW_EXECUTION_ID);
    ExecutionResponse executionResponse = terragruntRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    verifyResponse(executionResponse, "sourceRepoBranch", true, 1, DESTROY);

    // no variables, no backend configs, no source repo branch
    when(terragruntStateHelper.getGitConfigAndPopulate(any(TerragruntConfig.class), any()))
        .thenReturn(GitConfig.builder().build());
    setUp(null, false, WORKFLOW_EXECUTION_ID);
    executionResponse = terragruntRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    verifyResponse(executionResponse, null, false, 2, DESTROY);

    // Inheriting terragrunt execution from last successful terragrunt execution
    when(terragruntStateHelper.getGitConfigAndPopulate(any(TerragruntConfig.class), anyString()))
        .thenReturn(GitConfig.builder().branch("sourceRepoBranch").build());
    setUp("sourceRepoBranch", true, null);
    executionResponse = terragruntRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    verifyResponse(executionResponse, "sourceRepoBranch", true, 3, APPLY);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testExecuteInternalPreviousExecutionFailed() {
    setUp("sourceRepoBranch", true, null);
    HIterator<TerraformConfig> configIterator = mock(HIterator.class);
    when(terragruntStateHelper.getSavedTerraformConfig(anyString(), anyString())).thenReturn(configIterator);
    when(configIterator.next()).thenReturn(null);
    terragruntRollbackState.setWorkspace(WORKSPACE);
    terragruntRollbackState.setPathToModule("module1");
    terragruntRollbackState.setProvisionerId(PROVISIONER_ID);
    ExecutionResponse executionResponse = terragruntRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    assertThat(executionResponse.getErrorMessage())
        .contains("No Rollback Required. Provisioning seems to have failed.");
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseApply() {
    when(executionContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);

    Environment environment = new Environment();
    environment.setUuid(UUID);
    when(executionContext.getEnv()).thenReturn(environment);
    terragruntRollbackState.setProvisionerId(PROVISIONER_ID);
    TerragruntInfrastructureProvisioner terragruntInfrastructureProvisioner =
        TerragruntInfrastructureProvisioner.builder()
            .name("Terragrunt Provisioner")
            .sourceRepoBranch("sourceRepoBranch")
            .sourceRepoSettingId(SOURCE_REPO_SETTINGS_ID)
            .build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terragruntInfrastructureProvisioner);
    Map<String, ResponseData> response = new HashMap<>();
    TerragruntExecutionData terragruntExecutionData = TerragruntExecutionData.builder()
                                                          .executionStatus(SUCCESS)
                                                          .stateFileId("stateFileId")
                                                          .commandExecuted(APPLY)
                                                          .pathToModule("module1")
                                                          .branch("master")
                                                          .entityId(ENTITY_ID)
                                                          .build();
    response.put("activityId", terragruntExecutionData);

    ExecutionResponse executionResponse = terragruntRollbackState.handleAsyncResponse(executionContext, response);
    verifyResponse(executionResponse, 1);
    verify(fileService, times(1))
        .updateParentEntityIdAndVersion(
            any(Class.class), anyString(), any(), anyString(), any(), any(FileBucket.class));

    // no state file
    terragruntExecutionData.setStateFileId(null);
    executionResponse = terragruntRollbackState.handleAsyncResponse(executionContext, response);
    verifyResponse(executionResponse, 2);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseDestroy() {
    terragruntRollbackState.setProvisionerId(PROVISIONER_ID);
    TerragruntInfrastructureProvisioner terragruntInfrastructureProvisioner =
        TerragruntInfrastructureProvisioner.builder().sourceRepoSettingId(SOURCE_REPO_SETTINGS_ID).build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terragruntInfrastructureProvisioner);
    Map<String, ResponseData> response = new HashMap<>();
    TerragruntExecutionData terragruntExecutionData = TerragruntExecutionData.builder()
                                                          .executionStatus(SUCCESS)
                                                          .stateFileId("stateFileId")
                                                          .commandExecuted(DESTROY)
                                                          .entityId(ENTITY_ID)
                                                          .build();
    response.put("activityId", terragruntExecutionData);

    Query<TerragruntConfig> query = mock(Query.class);
    when(wingsPersistence.createQuery(any(Class.class))).thenReturn(query);
    when(query.filter(anyString(), any(Object.class))).thenReturn(query);
    ExecutionResponse executionResponse = terragruntRollbackState.handleAsyncResponse(executionContext, response);

    verify(fileService, times(1))
        .updateParentEntityIdAndVersion(
            any(Class.class), anyString(), any(), anyString(), any(), any(FileBucket.class));
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    verify(terragruntStateHelper)
        .deleteTerragruntConfiguUsingOekflowExecutionId(any(ExecutionContext.class), anyString());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  private void setUp(String sourceRepoBranch, boolean setVarsAndBackendConfigs, String workflowExecutionId) {
    terragruntRollbackState.setWorkspace(WORKSPACE);
    terragruntRollbackState.setPathToModule("module1");
    terragruntRollbackState.setProvisionerId(PROVISIONER_ID);

    when(executionContext.getWorkflowExecutionId()).thenReturn(workflowExecutionId);

    TerragruntInfrastructureProvisioner terragruntInfrastructureProvisioner =
        TerragruntInfrastructureProvisioner.builder()
            .name("Terragrunt Provisioner")
            .sourceRepoBranch(sourceRepoBranch)
            .appId(APP_ID)
            .build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terragruntInfrastructureProvisioner);
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    SweepingOutputInstance sweepingOutputInstance =
        SweepingOutputInstance.builder()
            .value(TerragruntApplyMarkerParam.builder().applyCompleted(true).build())
            .build();
    when(sweepingOutputService.find(any(SweepingOutputInquiry.class))).thenReturn(sweepingOutputInstance);
    Environment environment = new Environment();
    environment.setUuid(UUID);
    doReturn(environment).when(executionContext).getEnv();

    HIterator<TerraformConfig> configIterator = mock(HIterator.class);
    when(terragruntStateHelper.getSavedTerraformConfig(anyString(), anyString())).thenReturn(configIterator);

    TerragruntConfig terragruntConfig =
        TerragruntConfig.builder()
            .workflowExecutionId(WORKFLOW_EXECUTION_ID)
            .sourceRepoSettingId("sourceRepoSettingsId")
            .sourceRepoReference("sourceRepoReference")
            .backendConfigs(setVarsAndBackendConfigs ? getTerragruntBackendConfigs() : null)
            .variables(setVarsAndBackendConfigs ? getTerragruntVariables() : null)
            .environmentVariables(setVarsAndBackendConfigs ? getTerragruntEnvironmentVariables() : null)
            .build();

    when(configIterator.hasNext()).thenReturn(true).thenReturn(true).thenReturn(false);
    when(configIterator.next()).thenReturn(terragruntConfig);

    when(fileService.getLatestFileId(anyString(), any(FileBucket.class))).thenReturn("fileId");

    when(infrastructureProvisionerService.getManagerExecutionCallback(anyString(), anyString(), anyString()))
        .thenReturn(mock(ManagerExecutionLogCallback.class));
  }

  private void verifyResponse(ExecutionResponse executionResponse, int i) {
    verify(infrastructureProvisionerService, times(i)).get(APP_ID, PROVISIONER_ID);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);

    ArgumentCaptor<TerragruntExecutionData> captor = ArgumentCaptor.forClass(TerragruntExecutionData.class);
    verify(terragruntStateHelper, times(i))
        .saveTerragruntConfig(any(ExecutionContext.class), anyString(), captor.capture(),
            eq(String.valueOf("PROVISIONER_IDmodule1masterUUID".hashCode())));
    TerragruntExecutionData executionData = captor.getValue();
    assertThat(executionData).isNotNull();
    assertThat(executionData.getPathToModule()).isEqualTo("module1");
    assertThat(executionData.getBranch()).isEqualTo("master");
  }

  private void verifyResponse(ExecutionResponse executionResponse, String branch, boolean checkVarsAndBackendConfigs,
      int i, TerragruntProvisionParameters.TerragruntCommand command) {
    verify(terragruntStateHelper, times(i)).getSavedTerraformConfig(anyString(), anyString());
    assertThat(executionResponse.getCorrelationIds().get(0)).isEqualTo(ACTIVITY_ID);
    assertThat(((ScriptStateExecutionData) executionResponse.getStateExecutionData()).getActivityId())
        .isEqualTo(ACTIVITY_ID);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(i)).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask.getData().getParameters().length).isEqualTo(1);
    TerragruntProvisionParameters parameters =
        (TerragruntProvisionParameters) delegateTask.getData().getParameters()[0];
    assertThat(parameters.getSourceRepoSettingId()).isEqualTo("sourceRepoSettingsId");
    assertThat(parameters.getCommand()).isEqualTo(command);
    if (checkVarsAndBackendConfigs) {
      assertThat(parameters.getVariables()).containsOnlyKeys("vpc_id", "region");
      assertThat(parameters.getEncryptedVariables()).containsOnlyKeys("access_key", "secret_key");
      assertThat(parameters.getEncryptedBackendConfigs()).containsOnlyKeys("access_token");
      assertThat(parameters.getBackendConfigs()).containsOnlyKeys("bucket", "key");
      assertThat(parameters.getEnvironmentVariables()).containsOnlyKeys("TF_LOG");
      assertThat(parameters.getEncryptedEnvironmentVariables()).containsOnlyKeys("secret_key");
    } else {
      assertThat(parameters.getVariables()).isNull();
      assertThat(parameters.getEncryptedVariables()).isNull();
      assertThat(parameters.getBackendConfigs()).isNull();
      assertThat(parameters.getEncryptedBackendConfigs()).isNull();
      assertThat(parameters.getEnvironmentVariables()).isNull();
      assertThat(parameters.getEncryptedEnvironmentVariables()).isNull();
    }
    GitConfig gitConfig = parameters.getSourceRepo();
    assertThat(gitConfig.getBranch()).isEqualTo(branch);
  }

  private List<NameValuePair> getTerragruntVariables() {
    return Arrays.asList(NameValuePair.builder().name("region").value("us-east").valueType("TEXT").build(),
        NameValuePair.builder().name("vpc_id").value("vpc-id").valueType("TEXT").build(),
        NameValuePair.builder().name("access_key").value("access_key").valueType("ENCRYPTED_TEXT").build(),
        NameValuePair.builder().name("secret_key").value("secret_key").valueType("ENCRYPTED_TEXT").build());
  }

  private List<NameValuePair> getTerragruntEnvironmentVariables() {
    return Arrays.asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
        NameValuePair.builder().name("secret_key").value("secret_key").valueType("ENCRYPTED_TEXT").build());
  }

  private List<NameValuePair> getTerragruntBackendConfigs() {
    return Arrays.asList(NameValuePair.builder().name("key").value("terraform.tfstate").valueType("TEXT").build(),
        NameValuePair.builder().name("bucket").value("tf-remote-state").valueType("TEXT").build(),
        NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testValidation() {
    assertThat(terragruntRollbackState.validateFields().size()).isNotEqualTo(0);
    terragruntRollbackState.setProvisionerId("test provisioner");
    assertThat(terragruntRollbackState.validateFields().size()).isEqualTo(0);
  }
}
