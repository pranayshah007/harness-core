/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.ACTIVITY_ID_BASED_TF_BASE_DIR;
import static io.harness.beans.FeatureName.CDS_TERRAFORM_S3_SUPPORT;
import static io.harness.beans.FeatureName.SYNC_GIT_CLONE_AND_COPY_TO_DEST_DIR;
import static io.harness.beans.FeatureName.TERRAFORM_AWS_CP_AUTHENTICATION;
import static io.harness.beans.FeatureName.TERRAFORM_REMOTE_BACKEND_CONFIG;
import static io.harness.rule.OwnerRule.AKHIL_PANDEY;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.service.impl.yaml.handler.infraprovisioner.TestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENTITY_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.PORTAL_URL;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.S3_URI;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ScriptStateExecutionData;
import software.wings.api.TerraformApplyMarkerParam;
import software.wings.api.TerraformExecutionData;
import software.wings.api.terraform.TfVarS3Source;
import software.wings.app.MainConfiguration;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.S3FileConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.TerraformSourceType;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.beans.infrastructure.TerraformConfig;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.utils.GitUtilsManager;
import software.wings.utils.WingsTestConstants;

import com.mongodb.DBCursor;
import dev.morphia.query.FieldEndImpl;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import dev.morphia.query.QueryImpl;
import dev.morphia.query.Sort;
import java.util.ArrayList;
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
public class TerraformRollbackStateTest extends WingsBaseTest {
  @Mock TerraformConfig configParameter;
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
  @Mock private FeatureFlagService featureFlagService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private SettingsService settingsService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @InjectMocks TerraformRollbackState terraformRollbackState = new TerraformRollbackState("Rollback Terraform Test");

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
    doAnswer(doExtractTextVariables)
        .when(infrastructureProvisionerService)
        .extractTextVariables(anyList(), any(ExecutionContext.class));
    doAnswer(doExtractTextVariables).when(infrastructureProvisionerService).extractUnresolvedTextVariables(anyList());
    doAnswer(doExtractEncryptedVariables)
        .when(infrastructureProvisionerService)
        .extractEncryptedTextVariables(anyList(), anyString(), any());
    doAnswer(doReturnSameValue).when(executionContext).renderExpression(anyString());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
    doReturn(STATE_EXECUTION_ID).when(executionContext).getStateExecutionInstanceId();
  }

  /**
   * Tests whether expected last successful workflow execution is returned.
   */
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldReturnValidLastSuccessfulWorkflowExecutionUrl() {
    when(configuration.getPortal().getUrl()).thenReturn(PORTAL_URL);
    when(configParameter.getAccountId()).thenReturn(ACCOUNT_ID);
    when(configParameter.getAppId()).thenReturn(APP_ID);
    when(executionContext.getEnv().getUuid()).thenReturn(ENV_ID);
    when(configParameter.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);

    final String expectedUrl = PORTAL_URL + "/#/account/" + ACCOUNT_ID + "/app/" + APP_ID + "/env/" + ENV_ID
        + "/executions/" + WORKFLOW_EXECUTION_ID + "/details";
    assertThat(terraformRollbackState.getLastSuccessfulWorkflowExecutionUrl(configParameter, executionContext)
                   .toString()
                   .equals(expectedUrl))
        .isTrue();
    // Check Url when env is null.
    when(executionContext.getEnv()).thenReturn(null);

    final String nullEnvExpectedUrl = PORTAL_URL + "/#/account/" + ACCOUNT_ID + "/app/" + APP_ID
        + "/env/null/executions/" + WORKFLOW_EXECUTION_ID + "/details";
    assertThat(terraformRollbackState.getLastSuccessfulWorkflowExecutionUrl(configParameter, executionContext)
                   .toString()
                   .equals(nullEnvExpectedUrl))
        .isTrue();
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteInternalNoApply() {
    when(executionContext.getAppId()).thenReturn(APP_ID);
    terraformRollbackState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner terraformInfrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder()
            .name("Terraform Provisioner")
            .terraformSourceType(TerraformSourceType.GIT)
            .build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terraformInfrastructureProvisioner);
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    SweepingOutputInstance sweepingOutputInstance =
        SweepingOutputInstance.builder()
            .value(TerraformApplyMarkerParam.builder().applyCompleted(false).build())
            .build();
    when(sweepingOutputService.find(any(SweepingOutputInquiry.class))).thenReturn(sweepingOutputInstance);
    ExecutionResponse executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    verify(sweepingOutputService, times(1)).find(any(SweepingOutputInquiry.class));

    // we didn't find a result in sweeping output
    when(sweepingOutputService.find(any(SweepingOutputInquiry.class))).thenReturn(null);
    executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    verify(infrastructureProvisionerService, times(2)).get(APP_ID, PROVISIONER_ID);
    verify(sweepingOutputService, times(2)).find(any(SweepingOutputInquiry.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteInternal() {
    setUp("sourceRepoBranch", true, WORKFLOW_EXECUTION_ID);
    ExecutionResponse executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    verifyResponse(executionResponse, "sourceRepoBranch", true, 1, TerraformCommand.DESTROY);
    verify(stateExecutionService).appendDelegateTaskDetails(anyString(), any());

    // no variables, no backend configs, no source repo branch
    setUp(null, false, WORKFLOW_EXECUTION_ID);
    executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    verifyResponse(executionResponse, null, false, 2, TerraformCommand.DESTROY);

    // Inheriting terraform execution from last successful terraform execution
    setUp("sourceRepoBranch", true, null);
    executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    verifyResponse(executionResponse, "sourceRepoBranch", true, 3, TerraformCommand.APPLY);
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testExecuteInternalForS3Workflow() {
    when(featureFlagService.isEnabled(TERRAFORM_REMOTE_BACKEND_CONFIG, ACCOUNT_ID)).thenReturn(true);
    when(featureFlagService.isEnabled(CDS_TERRAFORM_S3_SUPPORT, ACCOUNT_ID)).thenReturn(true);

    s3SetUp(true, WORKFLOW_EXECUTION_ID);

    ExecutionResponse executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);

    assertThat(executionResponse.getExecutionStatus().equals(ExecutionStatus.SUCCESS));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInternalAwsCPAuthEnabled() {
    setUp("sourceRepoBranch", true, WORKFLOW_EXECUTION_ID);
    List<EncryptedDataDetail> encryptionDetails = new ArrayList();
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setUuid("UUID");
    settingAttribute.setValue(new AwsConfig());
    doReturn(true).when(featureFlagService).isEnabled(eq(TERRAFORM_AWS_CP_AUTHENTICATION), any());
    doReturn(true).when(featureFlagService).isEnabled(eq(ACTIVITY_ID_BASED_TF_BASE_DIR), any());
    doReturn(true).when(featureFlagService).isEnabled(eq(SYNC_GIT_CLONE_AND_COPY_TO_DEST_DIR), any());

    doReturn(settingAttribute).when(settingsService).get(any());
    doReturn(encryptionDetails).when(secretManager).getEncryptionDetails(any(), any(), any());
    ExecutionResponse executionResponse = terraformRollbackState.executeInternal(executionContext, ACTIVITY_ID);
    verifyResponse(executionResponse, "sourceRepoBranch", true, 1, TerraformCommand.DESTROY);
    verify(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(1)).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask.getData().getParameters().length).isEqualTo(1);
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) delegateTask.getData().getParameters()[0];
    assertThat(parameters.getAwsConfigId()).isEqualTo("UUID");
    assertThat(parameters.getAwsConfig()).isEqualTo(settingAttribute.getValue());
    assertThat(parameters.getAwsRoleArn()).isEqualTo("arn");
    assertThat(parameters.getAwsRegion()).isEqualTo("region");

    assertThat(parameters.isUseActivityIdBasedTfBaseDir()).isTrue();
    assertThat(parameters.isSyncGitCloneAndCopyToDestDir()).isTrue();
  }

  private void s3SetUp(boolean setVarsAndBackendConfigs, String workflowExecutionId) {
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setUuid("UUID");
    settingAttribute.setValue(new AwsConfig());

    doReturn(settingAttribute).when(settingsService).get(any());
    terraformRollbackState.setWorkspace(WORKSPACE);
    when(executionContext.getWorkflowExecutionId()).thenReturn(workflowExecutionId);
    when(executionContext.getAppId()).thenReturn(APP_ID);
    terraformRollbackState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner terraformInfrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder()
            .name("Terraform Provisioner")
            .terraformSourceType(TerraformSourceType.S3)
            .s3URI(S3_URI)
            .awsConfigId(UUID)
            .appId("appId")
            .build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terraformInfrastructureProvisioner);
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    SweepingOutputInstance sweepingOutputInstance =
        SweepingOutputInstance.builder()
            .value(TerraformApplyMarkerParam.builder().applyCompleted(true).build())
            .build();
    when(sweepingOutputService.find(any(SweepingOutputInquiry.class))).thenReturn(sweepingOutputInstance);
    Environment environment = new Environment();
    environment.setUuid(UUID);
    doReturn(environment).when(executionContext).getEnv();
    QueryImpl<TerraformConfig> query = mock(QueryImpl.class);
    FieldEndImpl fieldEnd = mock(FieldEndImpl.class);
    when(fieldEnd.in(any())).thenReturn(query);
    when(wingsPersistence.createQuery(any(Class.class))).thenReturn(query);
    when(query.filter(anyString(), any(Object.class))).thenReturn(query);
    when(query.field(anyString())).thenReturn(fieldEnd);
    when(query.order(any(Sort.class))).thenReturn(query);

    S3FileConfig s3FileConfig = S3FileConfig.builder().awsConfigId(SETTING_ID).s3URI(WingsTestConstants.S3_URI).build();
    AwsConfig awsConfig = new AwsConfig();
    List<EncryptedDataDetail> encryptedRecordDataList = null;

    TfVarS3Source remoteS3BackendConfig = TfVarS3Source.builder()
                                              .s3FileConfig(s3FileConfig)
                                              .awsConfig(awsConfig)
                                              .encryptedDataDetails(encryptedRecordDataList)
                                              .build();

    TerraformConfig terraformConfig =
        TerraformConfig.builder()
            .workflowExecutionId(WORKFLOW_EXECUTION_ID)
            .s3BackendFileConfig(s3FileConfig)
            .tfVarS3FileConfig(s3FileConfig)
            .variables(setVarsAndBackendConfigs ? getTerraformVariables() : null)
            .environmentVariables(setVarsAndBackendConfigs ? getTerraformEnvironmentVariables() : null)
            .awsRegion("region")
            .awsConfigId("UUID")
            .awsRoleArn("arn")
            .build();

    MorphiaIterator<TerraformConfig, TerraformConfig> morphiaIterator = mock(MorphiaIterator.class);
    DBCursor dbCursor = mock(DBCursor.class);
    when(morphiaIterator.hasNext()).thenReturn(true).thenReturn(true).thenReturn(false);
    when(morphiaIterator.next()).thenReturn(terraformConfig);
    when(morphiaIterator.getCursor()).thenReturn(dbCursor);
    when(query.fetch()).thenReturn(morphiaIterator);

    when(fileService.getLatestFileId(anyString(), any(FileBucket.class))).thenReturn("fileId");
    when(gitUtilsManager.getGitConfig(anyString())).thenReturn(GitConfig.builder().build());
    when(infrastructureProvisionerService.getManagerExecutionCallback(anyString(), anyString(), anyString()))
        .thenReturn(mock(ManagerExecutionLogCallback.class));
  }
  private void setUp(String sourceRepoBranch, boolean setVarsAndBackendConfigs, String workflowExecutionId) {
    terraformRollbackState.setWorkspace(WORKSPACE);
    when(executionContext.getWorkflowExecutionId()).thenReturn(workflowExecutionId);
    when(executionContext.getAppId()).thenReturn(APP_ID);
    terraformRollbackState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner terraformInfrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder()
            .name("Terraform Provisioner")
            .terraformSourceType(TerraformSourceType.GIT)
            .sourceRepoBranch(sourceRepoBranch)
            .appId("appId")
            .build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terraformInfrastructureProvisioner);
    when(executionContext.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
    SweepingOutputInstance sweepingOutputInstance =
        SweepingOutputInstance.builder()
            .value(TerraformApplyMarkerParam.builder().applyCompleted(true).build())
            .build();
    when(sweepingOutputService.find(any(SweepingOutputInquiry.class))).thenReturn(sweepingOutputInstance);
    Environment environment = new Environment();
    environment.setUuid(UUID);
    doReturn(environment).when(executionContext).getEnv();
    QueryImpl<TerraformConfig> query = mock(QueryImpl.class);
    FieldEndImpl fieldEnd = mock(FieldEndImpl.class);
    when(fieldEnd.in(any())).thenReturn(query);
    when(wingsPersistence.createQuery(any(Class.class))).thenReturn(query);
    when(query.filter(anyString(), any(Object.class))).thenReturn(query);
    when(query.field(anyString())).thenReturn(fieldEnd);
    when(query.order(any(Sort.class))).thenReturn(query);

    TerraformConfig terraformConfig =
        TerraformConfig.builder()
            .workflowExecutionId(WORKFLOW_EXECUTION_ID)
            .sourceRepoSettingId("sourceRepoSettingsId")
            .sourceRepoReference("sourceRepoReference")
            .backendConfigs(setVarsAndBackendConfigs ? getTerraformBackendConfigs() : null)
            .variables(setVarsAndBackendConfigs ? getTerraformVariables() : null)
            .environmentVariables(setVarsAndBackendConfigs ? getTerraformEnvironmentVariables() : null)
            .awsRegion("region")
            .awsConfigId("UUID")
            .awsRoleArn("arn")
            .build();

    MorphiaIterator<TerraformConfig, TerraformConfig> morphiaIterator = mock(MorphiaIterator.class);
    DBCursor dbCursor = mock(DBCursor.class);
    when(morphiaIterator.hasNext()).thenReturn(true).thenReturn(true).thenReturn(false);
    when(morphiaIterator.next()).thenReturn(terraformConfig);
    when(morphiaIterator.getCursor()).thenReturn(dbCursor);
    when(query.fetch()).thenReturn(morphiaIterator);

    when(fileService.getLatestFileId(anyString(), any(FileBucket.class))).thenReturn("fileId");
    when(gitUtilsManager.getGitConfig(anyString())).thenReturn(GitConfig.builder().build());
    when(infrastructureProvisionerService.getManagerExecutionCallback(anyString(), anyString(), anyString()))
        .thenReturn(mock(ManagerExecutionLogCallback.class));
  }

  private void verifyResponse(ExecutionResponse executionResponse, String branch, boolean checkVarsAndBackendConfigs,
      int i, TerraformCommand command) {
    verify(wingsPersistence, times(i)).createQuery(TerraformConfig.class);
    assertThat(executionResponse.getCorrelationIds().get(0)).isEqualTo(ACTIVITY_ID);
    assertThat(((ScriptStateExecutionData) executionResponse.getStateExecutionData()).getActivityId())
        .isEqualTo(ACTIVITY_ID);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(i)).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask.getData().getParameters().length).isEqualTo(1);
    TerraformProvisionParameters parameters = (TerraformProvisionParameters) delegateTask.getData().getParameters()[0];
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

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseApply() {
    when(executionContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);
    when(executionContext.getAppId()).thenReturn(APP_ID);
    Environment environment = new Environment();
    environment.setUuid(UUID);
    when(executionContext.getEnv()).thenReturn(environment);
    terraformRollbackState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner terraformInfrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder()
            .name("Terraform Provisioner")
            .terraformSourceType(TerraformSourceType.GIT)
            .sourceRepoBranch("sourceRepoBranch")
            .build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terraformInfrastructureProvisioner);
    Map<String, ResponseData> response = new HashMap<>();
    TerraformExecutionData terraformExecutionData = TerraformExecutionData.builder()
                                                        .executionStatus(ExecutionStatus.SUCCESS)
                                                        .stateFileId("stateFileId")
                                                        .entityId(ENTITY_ID)
                                                        .commandExecuted(TerraformCommand.APPLY)
                                                        .build();
    response.put("activityId", terraformExecutionData);

    ExecutionResponse executionResponse = terraformRollbackState.handleAsyncResponse(executionContext, response);
    verifyResponse(executionResponse, 1);
    verify(fileService, times(1))
        .updateParentEntityIdAndVersion(
            any(Class.class), anyString(), any(), anyString(), any(), any(FileBucket.class));

    // no state file
    terraformExecutionData.setStateFileId(null);
    executionResponse = terraformRollbackState.handleAsyncResponse(executionContext, response);
    verifyResponse(executionResponse, 2);
  }

  private void verifyResponse(ExecutionResponse executionResponse, int i) {
    verify(infrastructureProvisionerService, times(i)).get(APP_ID, PROVISIONER_ID);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    ArgumentCaptor<TerraformConfig> captor = ArgumentCaptor.forClass(TerraformConfig.class);
    verify(wingsPersistence, times(i)).save(captor.capture());
    TerraformConfig terraformConfig = captor.getValue();
    assertThat(terraformConfig).isNotNull();
    assertThat(terraformConfig.getWorkflowExecutionId()).isEqualTo(WORKFLOW_EXECUTION_ID);
    assertThat(terraformConfig.getEntityId())
        .isEqualTo(String.format("%s-%s-%s", PROVISIONER_ID, UUID, "sourceRepoBranch".hashCode()));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseDestroy() {
    when(executionContext.getAppId()).thenReturn(APP_ID);
    terraformRollbackState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner terraformInfrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terraformInfrastructureProvisioner);
    Map<String, ResponseData> response = new HashMap<>();
    TerraformExecutionData terraformExecutionData = TerraformExecutionData.builder()
                                                        .executionStatus(ExecutionStatus.SUCCESS)
                                                        .stateFileId("stateFileId")
                                                        .commandExecuted(TerraformCommand.DESTROY)
                                                        .entityId(ENTITY_ID)
                                                        .build();
    response.put("activityId", terraformExecutionData);

    Query<TerraformConfig> query = mock(Query.class);
    when(wingsPersistence.createQuery(any(Class.class))).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    when(wingsPersistence.delete(any(Query.class))).thenReturn(true);
    ExecutionResponse executionResponse = terraformRollbackState.handleAsyncResponse(executionContext, response);

    verify(fileService, times(1))
        .updateParentEntityIdAndVersion(
            any(Class.class), anyString(), any(), anyString(), any(), any(FileBucket.class));
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    verify(wingsPersistence, times(2)).createQuery(TerraformConfig.class);
    verify(wingsPersistence, times(2)).delete(query);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseDestroyRetryDeleteWithOldEntityId() {
    when(executionContext.getAppId()).thenReturn(APP_ID);
    terraformRollbackState.setProvisionerId(PROVISIONER_ID);
    TerraformInfrastructureProvisioner terraformInfrastructureProvisioner =
        TerraformInfrastructureProvisioner.builder().build();
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(terraformInfrastructureProvisioner);
    Map<String, ResponseData> response = new HashMap<>();
    TerraformExecutionData terraformExecutionData = TerraformExecutionData.builder()
                                                        .executionStatus(ExecutionStatus.SUCCESS)
                                                        .stateFileId("stateFileId")
                                                        .entityId(ENTITY_ID)
                                                        .commandExecuted(TerraformCommand.DESTROY)
                                                        .build();
    response.put("activityId", terraformExecutionData);

    Query<TerraformConfig> query = mock(Query.class);
    when(wingsPersistence.createQuery(any(Class.class))).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);
    when(wingsPersistence.delete(any(Query.class))).thenReturn(false);
    ExecutionResponse executionResponse = terraformRollbackState.handleAsyncResponse(executionContext, response);

    verify(fileService, times(1))
        .updateParentEntityIdAndVersion(
            any(Class.class), anyString(), any(), anyString(), any(), any(FileBucket.class));
    verify(infrastructureProvisionerService, times(1)).get(APP_ID, PROVISIONER_ID);
    verify(wingsPersistence, times(2)).createQuery(TerraformConfig.class);
    verify(wingsPersistence, times(2)).delete(query);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  private List<NameValuePair> getTerraformVariables() {
    return Arrays.asList(NameValuePair.builder().name("region").value("us-east").valueType("TEXT").build(),
        NameValuePair.builder().name("vpc_id").value("vpc-id").valueType("TEXT").build(),
        NameValuePair.builder().name("access_key").value("access_key").valueType("ENCRYPTED_TEXT").build(),
        NameValuePair.builder().name("secret_key").value("secret_key").valueType("ENCRYPTED_TEXT").build());
  }

  private List<NameValuePair> getTerraformEnvironmentVariables() {
    return Arrays.asList(NameValuePair.builder().name("TF_LOG").value("TRACE").valueType("TEXT").build(),
        NameValuePair.builder().name("secret_key").value("secret_key").valueType("ENCRYPTED_TEXT").build());
  }

  private List<NameValuePair> getTerraformBackendConfigs() {
    return Arrays.asList(NameValuePair.builder().name("key").value("terraform.tfstate").valueType("TEXT").build(),
        NameValuePair.builder().name("bucket").value("tf-remote-state").valueType("TEXT").build(),
        NameValuePair.builder().name("access_token").value("access_token").valueType("ENCRYPTED_TEXT").build());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testValidation() {
    assertThat(terraformRollbackState.validateFields().size()).isNotEqualTo(0);
    terraformRollbackState.setProvisionerId("test provisioner");
    assertThat(terraformRollbackState.validateFields().size()).isEqualTo(0);
  }
}
