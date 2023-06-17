/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CLOUDFORMATION_CHANGE_SET;
import static io.harness.beans.FeatureName.CLOUDFORMATION_SKIP_WAIT_FOR_RESOURCES;
import static io.harness.beans.FeatureName.SKIP_BASED_ON_STACK_STATUSES;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_BODY;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_URL;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.JELENA;
import static io.harness.rule.OwnerRule.NAVNEET;
import static io.harness.rule.OwnerRule.PRAKHAR;
import static io.harness.rule.OwnerRule.RAGHVENDRA;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.beans.CloudFormationSourceType.GIT;
import static software.wings.beans.CloudFormationSourceType.TEMPLATE_BODY;
import static software.wings.beans.CloudFormationSourceType.TEMPLATE_URL;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.TaskType.CLOUD_FORMATION_TASK;
import static software.wings.beans.TaskType.FETCH_S3_FILE_TASK;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.TAG_NAME;
import static software.wings.utils.WingsTestConstants.TEMPLATE_FILE_PATH;
import static software.wings.utils.WingsTestConstants.UUID;

import static com.amazonaws.services.cloudformation.model.StackStatus.UPDATE_ROLLBACK_COMPLETE;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ScriptStateExecutionData;
import software.wings.api.cloudformation.CloudFormationElement;
import software.wings.api.cloudformation.CloudFormationOutputInfoElement;
import software.wings.api.cloudformation.CloudFormationRollbackInfoElement;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFetchFilesConfig;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TemplateExpression;
import software.wings.beans.s3.FetchS3FilesCommandParams;
import software.wings.beans.s3.FetchS3FilesExecutionResponse;
import software.wings.beans.s3.S3Bucket;
import software.wings.beans.s3.S3FetchFileResult;
import software.wings.beans.s3.S3File;
import software.wings.beans.s3.S3FileRequest;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitFetchFilesFromMultipleRepoResult;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCreateStackResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationRollbackInfo;
import software.wings.helpers.ext.cloudformation.response.ExistingStackInfo;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.impl.yaml.GitClientHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.settings.SettingVariableTypes;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.utils.GitUtilsManager;
import software.wings.utils.WingsTestConstants;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class CloudFormationCreateStackStateTest extends WingsBaseTest {
  @Mock private AppService appService;
  @Mock private GitUtilsManager gitUtilsManager;
  @Mock private ExecutionContextImpl mockContext;
  @Mock private InfrastructureProvisionerService mockInfrastructureProvisionerService;
  @Mock private SecretManager mockSecretManager;
  @Mock private TemplateExpressionProcessor templateExpressionProcessor;
  @Mock private WingsPersistence wingsPersistence;
  @Mock private ActivityService activityService;
  @Mock private SettingsService settingsService;
  @Mock private DelegateService delegateService;
  @Spy private GitClientHelper gitClientHelper;
  @Spy private GitConfigHelperService gitConfigHelperService;
  @Spy private GitFileConfigHelperService gitFileConfigHelperService;
  @Spy private S3UriParser s3UriParser;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private LogService logService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @InjectMocks @Spy private CloudFormationCreateStackState state = new CloudFormationCreateStackState("stateName");

  private AwsConfig awsConfig = AwsConfig.builder().tag(TAG_NAME).build();
  private static final String repoUrl = "http://xyz.com/z.git";
  private static final String PARAMETERS_FILE_CONTENT =
      "[{\"ParameterKey\": \"name\",\"ParameterValue\": \"value\"},{\"ParameterKey\": \"name2\",\"ParameterValue\": \"value2\"}]";

  @Before
  public void setUp() {
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockParams).when(mockContext).fetchWorkflowStandardParamsFromContext();
    Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(env).when(workflowStandardParamsExtensionService).fetchRequiredEnv(mockParams);

    Application application = new Application();
    application.setAppId(APP_ID);
    application.setAccountId(ACCOUNT_ID);
    application.setUuid(UUID);
    when(mockContext.getApp()).thenReturn(application);
    when(mockContext.getEnv()).thenReturn(env);

    Answer<String> doReturnSameValue = invocation -> invocation.getArgument(0, String.class);
    doAnswer(doReturnSameValue).when(mockContext).renderExpression(any());

    SettingAttribute settingAttribute = aSettingAttribute().withValue(awsConfig).build();
    doReturn(settingAttribute).when(settingsService).get(any());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void buildDelegateTaskProvisionByUrlWithParametersFile() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_URL.name())
                                                              .templateFilePath(TEMPLATE_FILE_PATH)
                                                              .build();
    state.setFileFetched(false);
    state.setUseParametersFile(true);
    state.setParametersFilePaths(
        Collections.singletonList("https://harness-test-bucket.s3.amazonaws.com/parameters.json"));
    state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(delegateTask.getData().getParameters().length).isEqualTo(1);
    assertThat(delegateTask.getData().getTaskType()).isEqualTo(FETCH_S3_FILE_TASK.name());
    FetchS3FilesCommandParams fetchS3FilesCommandParams =
        (FetchS3FilesCommandParams) delegateTask.getData().getParameters()[0];
    assertThat(fetchS3FilesCommandParams.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(fetchS3FilesCommandParams.getAppId()).isEqualTo(APP_ID);
    assertThat(fetchS3FilesCommandParams.getAwsConfig()).isEqualTo(awsConfig);
    List<S3FileRequest> s3FileRequests = fetchS3FilesCommandParams.getS3FileRequests();
    assertThat(s3FileRequests.get(0).getBucketName()).isEqualTo("harness-test-bucket");
    assertThat(s3FileRequests.get(0).getFileKeys().get(0)).isEqualTo("parameters.json");
  }

  @Test(expected = WingsException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void buildDelegateTaskProvisionByUrlWithInvalidParametersFileUrl() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_URL.name())
                                                              .templateFilePath(TEMPLATE_FILE_PATH)
                                                              .build();
    state.setFileFetched(false);
    state.setUseParametersFile(true);
    state.setParametersFilePaths(
        Collections.singletonList("https://harness-test-bucket.s.amazonaws.com/parameters.json"));
    state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void buildDelegateTaskProvisionByGitRepoWithParametersFile() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(GIT.name())
                                                              .gitFileConfig(GitFileConfig.builder()
                                                                                 .connectorId("sourceRepoSettingId")
                                                                                 .branch("gitBranch")
                                                                                 .commitId("commitId")
                                                                                 .build())
                                                              .build();

    GitConfig gitConfig = GitConfig.builder().urlType(GitConfig.UrlType.REPO).repoUrl(repoUrl).build();
    when(gitUtilsManager.getGitConfig("sourceRepoSettingId")).thenReturn(gitConfig);
    when(mockInfrastructureProvisionerService.get(any(), any())).thenReturn(provisioner);
    state.setFileFetched(false);
    state.setUseParametersFile(true);
    state.setParametersFilePaths(Collections.singletonList("parameters.json"));
    state.executeInternal(mockContext, ACTIVITY_ID);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);

    GitFetchFilesTaskParams request = (GitFetchFilesTaskParams) delegateTask.getData().getParameters()[0];
    assertThat(request.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(request.getAppId()).isEqualTo(APP_ID);
    assertThat(request.getActivityId()).isEqualTo(ACTIVITY_ID);

    GitFetchFilesConfig gitFetchFilesConfig = request.getGitFetchFilesConfigMap().get("Cloud Formation parameters");
    GitConfig fileMapGitConfig = gitFetchFilesConfig.getGitConfig();
    assertThat(fileMapGitConfig.getBranch()).isEqualTo("gitBranch");
    assertThat(fileMapGitConfig.getReference()).isEqualTo("commitId");
    assertThat(fileMapGitConfig.getRepoName()).isNull();
    assertThat(fileMapGitConfig.getRepoUrl()).isEqualTo(repoUrl);

    GitFileConfig gitFileConfig = gitFetchFilesConfig.getGitFileConfig();
    assertThat(gitFileConfig).isEqualTo(provisioner.getGitFileConfig());
    assertThat(gitFileConfig.getFilePathList().get(0)).isEqualTo("parameters.json");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void buildDelegateTaskProvisionByUrl() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_URL.name())
                                                              .templateFilePath(TEMPLATE_FILE_PATH)
                                                              .build();

    state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    verifyDelegateTask(delegateTask, true);
    CloudFormationCreateStackRequest request =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(request.getData()).isEqualTo(TEMPLATE_FILE_PATH);
    assertThat(request.getCreateType()).isEqualTo(CLOUDFORMATION_STACK_CREATE_URL);
    assertThat(request.getCustomStackName()).isEqualTo(StringUtils.EMPTY);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void buildDelegateTaskProvisionByBody() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_BODY.name())
                                                              .templateBody(WingsTestConstants.TEMPLATE_BODY)
                                                              .build();
    state.customStackName = "customStackName";
    state.useCustomStackName = true;
    state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    verifyDelegateTask(delegateTask, true);
    CloudFormationCreateStackRequest request =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(request.getData()).isEqualTo(WingsTestConstants.TEMPLATE_BODY);
    assertThat(request.getCreateType()).isEqualTo(CLOUDFORMATION_STACK_CREATE_BODY);
    assertThat(request.getCustomStackName()).isEqualTo("customStackName");
    assertThat(request.getCapabilities()).isNull();
    assertThat(request.getTags()).isNull();
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void buildDelegateTaskProvisionByBodyWithStackStatusToIgnore() {
    when(featureFlagService.isEnabled(SKIP_BASED_ON_STACK_STATUSES, ACCOUNT_ID)).thenReturn(true);
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_BODY.name())
                                                              .templateBody(WingsTestConstants.TEMPLATE_BODY)
                                                              .build();
    state.customStackName = "customStackName";
    state.useCustomStackName = true;
    state.setSkipBasedOnStackStatus(true);
    state.setStackStatusesToMarkAsSuccess(singletonList("UPDATE_ROLLBACK_COMPLETE"));
    state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());

    DelegateTask delegateTask = captor.getValue();
    verifyDelegateTask(delegateTask, true);

    CloudFormationCreateStackRequest request =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(request.getData()).isEqualTo(WingsTestConstants.TEMPLATE_BODY);
    assertThat(request.getCreateType()).isEqualTo(CLOUDFORMATION_STACK_CREATE_BODY);
    assertThat(request.getCustomStackName()).isEqualTo("customStackName");
    assertThat(request.getStackStatusesToMarkAsSuccess()).containsExactly(UPDATE_ROLLBACK_COMPLETE);
  }

  @Test
  @Owner(developers = NAVNEET)
  @Category(UnitTests.class)
  public void buildDelegateTaskProvisionByGitRepo() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(GIT.name())
                                                              .gitFileConfig(GitFileConfig.builder()
                                                                                 .connectorId("sourceRepoSettingId")
                                                                                 .branch("gitBranch")
                                                                                 .commitId("commitId")
                                                                                 .filePath("template.json")
                                                                                 .build())
                                                              .build();

    GitConfig gitConfig = GitConfig.builder().urlType(GitConfig.UrlType.REPO).repoUrl(repoUrl).build();
    when(gitUtilsManager.getGitConfig("sourceRepoSettingId")).thenReturn(gitConfig);
    when(mockInfrastructureProvisionerService.get(any(), any())).thenReturn(provisioner);
    state.setFileFetched(false);
    state.executeInternal(mockContext, ACTIVITY_ID);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);

    GitFetchFilesTaskParams request = (GitFetchFilesTaskParams) delegateTask.getData().getParameters()[0];
    assertThat(request.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(request.getAppId()).isEqualTo(APP_ID);
    assertThat(request.getActivityId()).isEqualTo(ACTIVITY_ID);

    GitFetchFilesConfig gitFetchFilesConfig = request.getGitFetchFilesConfigMap().get("Cloud Formation parameters");
    GitConfig fileMapGitConfig = gitFetchFilesConfig.getGitConfig();
    assertThat(fileMapGitConfig.getBranch()).isEqualTo("gitBranch");
    assertThat(fileMapGitConfig.getReference()).isEqualTo("commitId");
    assertThat(fileMapGitConfig.getRepoName()).isNull();
    assertThat(fileMapGitConfig.getRepoUrl()).isEqualTo(repoUrl);

    GitFileConfig gitFileConfig = gitFetchFilesConfig.getGitFileConfig();
    assertThat(gitFileConfig).isEqualTo(provisioner.getGitFileConfig());
    assertThat(gitFileConfig.getFilePathList().get(0)).isEqualTo("template.json");
  }

  @Test
  @Owner(developers = NAVNEET)
  @Category(UnitTests.class)
  public void buildDelegateTaskProvisionByGitAccount() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(GIT.name())
                                                              .gitFileConfig(GitFileConfig.builder()
                                                                                 .connectorId("sourceRepoSettingId")
                                                                                 .branch("gitBranch")
                                                                                 .commitId("commitId")
                                                                                 .repoName("z.git")
                                                                                 .filePath("template.json")
                                                                                 .build())
                                                              .build();

    GitConfig gitConfig = GitConfig.builder().urlType(GitConfig.UrlType.ACCOUNT).repoUrl("http://xyz.com").build();
    when(gitUtilsManager.getGitConfig("sourceRepoSettingId")).thenReturn(gitConfig);
    when(mockInfrastructureProvisionerService.get(any(), any())).thenReturn(provisioner);
    state.setFileFetched(false);
    state.executeInternal(mockContext, ACTIVITY_ID);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);

    GitFetchFilesTaskParams request = (GitFetchFilesTaskParams) delegateTask.getData().getParameters()[0];
    assertThat(request.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(request.getAppId()).isEqualTo(APP_ID);
    assertThat(request.getActivityId()).isEqualTo(ACTIVITY_ID);

    GitFetchFilesConfig gitFetchFilesConfig = request.getGitFetchFilesConfigMap().get("Cloud Formation parameters");
    GitConfig fileMapGitConfig = gitFetchFilesConfig.getGitConfig();
    assertThat(fileMapGitConfig.getBranch()).isEqualTo("gitBranch");
    assertThat(fileMapGitConfig.getReference()).isEqualTo("commitId");
    assertThat(fileMapGitConfig.getRepoName()).isEqualTo("z.git");
    assertThat(fileMapGitConfig.getRepoUrl()).isEqualTo(repoUrl);

    GitFileConfig gitFileConfig = gitFetchFilesConfig.getGitFileConfig();
    assertThat(gitFileConfig).isEqualTo(provisioner.getGitFileConfig());
    assertThat(gitFileConfig.getFilePathList().get(0)).isEqualTo("template.json");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleResponse() {
    CloudFormationRollbackInfo cloudFormationRollbackInfo =
        CloudFormationRollbackInfo.builder()
            .url(TEMPLATE_FILE_PATH)
            .skipBasedOnStackStatus(true)
            .stackStatusesToMarkAsSuccess(singletonList(UPDATE_ROLLBACK_COMPLETE.name()))
            .build();
    ExistingStackInfo existingStackInfo = ExistingStackInfo.builder().oldStackBody("oldStackBody").build();
    Map<String, Object> cloudFormationOutputMap = new HashMap<>();
    cloudFormationOutputMap.put("key1", CloudFormationOutputInfoElement.builder().build());
    CloudFormationCreateStackResponse createStackResponse =
        new CloudFormationCreateStackResponse(CommandExecutionStatus.SUCCESS, "output", cloudFormationOutputMap,
            "stackId", existingStackInfo, cloudFormationRollbackInfo, "UPDATE_ROLLBACK_COMPLETE");

    TemplateExpression templateExpression = TemplateExpression.builder().build();
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    doReturn(ScriptStateExecutionData.builder().build()).when(mockContext).getStateExecutionData();
    when(templateExpressionProcessor.getTemplateExpression(anyList(), any())).thenReturn(templateExpression);

    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    when(templateExpressionProcessor.resolveSettingAttributeByNameOrId(
             eq(mockContext), eq(templateExpression), eq(SettingVariableTypes.AWS)))
        .thenReturn(settingAttribute);
    when(settingAttribute.getUuid()).thenReturn(UUID);

    state.setTemplateExpressions(Arrays.asList(templateExpression));
    state.customStackName = "customStackName";
    state.useCustomStackName = true;

    List<CloudFormationElement> cloudFormationElementList = state.handleResponse(createStackResponse, mockContext);
    verifyResponse(cloudFormationElementList, true, UUID);
    assertThat(((CloudFormationRollbackInfoElement) cloudFormationElementList.get(0)).isSkipBasedOnStackStatus())
        .isTrue();
    assertThat(((CloudFormationRollbackInfoElement) cloudFormationElementList.get(0)).getStackStatusesToMarkAsSuccess())
        .containsExactly(UPDATE_ROLLBACK_COMPLETE.name());

    // no outputs
    createStackResponse = new CloudFormationCreateStackResponse(CommandExecutionStatus.SUCCESS, "output", null,
        "stackId", existingStackInfo, cloudFormationRollbackInfo, "UPDATE_ROLLBACK_COMPLETE");
    when(mockContext.getContextElement(ContextElementType.CLOUD_FORMATION_PROVISION))
        .thenReturn(CloudFormationOutputInfoElement.builder().build());
    when(templateExpressionProcessor.getTemplateExpression(anyList(), any())).thenReturn(null);
    state.setAwsConfigId("awsConfigId");
    cloudFormationElementList = state.handleResponse(createStackResponse, mockContext);
    verifyResponse(cloudFormationElementList, false, "awsConfigId");

    testHandleAsyncResponse(createStackResponse);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandAsyncResponseForGitFetchFiles() {
    CloudFormationInfrastructureProvisioner cloudFormationInfrastructureProvisioner =
        CloudFormationInfrastructureProvisioner.builder()
            .sourceType(GIT.name())
            .gitFileConfig(GitFileConfig.builder()
                               .filePath("filePath")
                               .connectorId("sourceRepoSettingId")
                               .commitId("commitId")
                               .build())
            .build();

    GitConfig gitConfig = GitConfig.builder().urlType(GitConfig.UrlType.REPO).repoUrl(repoUrl).build();
    when(gitUtilsManager.getGitConfig("sourceRepoSettingId")).thenReturn(gitConfig);

    when(mockInfrastructureProvisionerService.get(any(), any())).thenReturn(cloudFormationInfrastructureProvisioner);

    state.setUseParametersFile(true);
    state.setParametersFilePaths(Collections.singletonList("filePath"));
    WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    workflowStandardParams.setAppId(APP_ID);
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    doReturn(workflowStandardParams).when(mockContext).getContextElement(ContextElementType.STANDARD);
    doReturn(ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build())
        .when(mockContext)
        .getStateExecutionData();
    doReturn(ExecutionResponse.builder().build()).when(state).executeInternal(any(), any());
    Map<String, ResponseData> delegateResponse = ImmutableMap.of(ACTIVITY_ID,
        GitCommandExecutionResponse.builder()
            .gitCommandStatus(GitCommandExecutionResponse.GitCommandStatus.SUCCESS)
            .gitCommandResult(
                GitFetchFilesFromMultipleRepoResult.builder()
                    .filesFromMultipleRepo(Collections.singletonMap("Cloud Formation parameters",
                        GitFetchFilesResult.builder()
                            .files(Collections.singletonList(
                                GitFile.builder().filePath("filePath").fileContent(PARAMETERS_FILE_CONTENT).build()))
                            .build()))
                    .build())
            .build());
    state.handleAsyncResponse(mockContext, delegateResponse);
    assertThat(state.getVariables().size()).isEqualTo(2);
    assertThat(state.isFileFetched()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandAsyncFailureResponseForGitFetchFiles() {
    WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    workflowStandardParams.setAppId(APP_ID);
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn("workfloExecutionId").when(mockContext).getWorkflowExecutionId();
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    doReturn(workflowStandardParams).when(mockContext).getContextElement(ContextElementType.STANDARD);
    doReturn(ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build())
        .when(mockContext)
        .getStateExecutionData();
    doReturn(ExecutionResponse.builder().build()).when(state).executeInternal(any(), any());
    Map<String, ResponseData> delegateResponse = ImmutableMap.of(ACTIVITY_ID,
        GitCommandExecutionResponse.builder()
            .gitCommandStatus(GitCommandExecutionResponse.GitCommandStatus.FAILURE)
            .errorMessage("anErrorMessage")
            .build());
    ExecutionResponse response = state.handleAsyncResponse(mockContext, delegateResponse);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getErrorMessage()).isEqualTo("anErrorMessage");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandAsyncResponseForS3FetchFiles() {
    state.setParametersFilePaths(
        Collections.singletonList("https://harness-test-bucket.s3.amazonaws.com/parameters.json"));
    WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    workflowStandardParams.setAppId(APP_ID);
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    doReturn(workflowStandardParams).when(mockContext).getContextElement(ContextElementType.STANDARD);
    doReturn(ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build())
        .when(mockContext)
        .getStateExecutionData();
    doReturn(ExecutionResponse.builder().build()).when(state).executeInternal(any(), any());
    FetchS3FilesExecutionResponse fetchS3FilesExecutionResponse =
        FetchS3FilesExecutionResponse.builder()
            .commandStatus(FetchS3FilesExecutionResponse.FetchS3FilesCommandStatus.SUCCESS)
            .s3FetchFileResult(S3FetchFileResult.builder()
                                   .s3Buckets(Collections.singletonList(
                                       S3Bucket.builder()
                                           .name("harness-test-bucket")
                                           .s3Files(Collections.singletonList(S3File.builder()
                                                                                  .fileKey("parameters.json")
                                                                                  .fileContent(PARAMETERS_FILE_CONTENT)
                                                                                  .build()))
                                           .build()))
                                   .build())
            .build();
    Map<String, ResponseData> delegateResponse = ImmutableMap.of(ACTIVITY_ID, fetchS3FilesExecutionResponse);
    state.handleAsyncResponse(mockContext, delegateResponse);
    assertThat(state.getVariables().size()).isEqualTo(2);
    assertThat(state.isFileFetched()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandAsyncFailureResponseFors3FetchFiles() {
    WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    workflowStandardParams.setAppId(APP_ID);
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn("workfloExecutionId").when(mockContext).getWorkflowExecutionId();
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    doReturn(workflowStandardParams).when(mockContext).getContextElement(ContextElementType.STANDARD);
    doReturn(ScriptStateExecutionData.builder().activityId(ACTIVITY_ID).build())
        .when(mockContext)
        .getStateExecutionData();
    doReturn(ExecutionResponse.builder().build()).when(state).executeInternal(any(), any());
    Map<String, ResponseData> delegateResponse = ImmutableMap.of(ACTIVITY_ID,
        FetchS3FilesExecutionResponse.builder()
            .commandStatus(FetchS3FilesExecutionResponse.FetchS3FilesCommandStatus.FAILURE)
            .errorMessage("any error message")
            .build());
    ExecutionResponse response = state.handleAsyncResponse(mockContext, delegateResponse);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getErrorMessage()).isNotEmpty();
  }

  private void testHandleAsyncResponse(CloudFormationCreateStackResponse createStackResponse) {
    // no template expressions
    state.setTemplateExpressions(null);
    state.setAwsConfigId("awsConfigId");
    state.setSkipBasedOnStackStatus(true);
    state.setStackStatusesToMarkAsSuccess(singletonList(UPDATE_ROLLBACK_COMPLETE.name()));

    Map<String, ResponseData> delegateResponse = ImmutableMap.of(ACTIVITY_ID,
        CloudFormationCommandExecutionResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .commandResponse(createStackResponse)
            .build());

    ExecutionResponse executionResponse = state.handleAsyncResponse(mockContext, delegateResponse);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    List<CloudFormationElement> cloudFormationElementList = new ArrayList<>();
    executionResponse.getContextElements().forEach(
        contextElement -> cloudFormationElementList.add((CloudFormationElement) contextElement));
    verifyResponse(cloudFormationElementList, false, "awsConfigId");
    assertThat(((CloudFormationRollbackInfoElement) cloudFormationElementList.get(0)).isSkipBasedOnStackStatus())
        .isTrue();
    assertThat(((CloudFormationRollbackInfoElement) cloudFormationElementList.get(0)).getStackStatusesToMarkAsSuccess())
        .containsExactly(UPDATE_ROLLBACK_COMPLETE.name());
    verify(sweepingOutputService).save(any());
  }

  private void verifyResponse(
      List<CloudFormationElement> cloudFormationElementList, boolean checkOutputs, String configId) {
    assertThat(cloudFormationElementList.size()).isEqualTo(2);
    CloudFormationElement cloudFormationElement0 = cloudFormationElementList.get(0);
    assertThat(cloudFormationElement0).isInstanceOf(CloudFormationRollbackInfoElement.class);
    CloudFormationRollbackInfoElement cloudFormationRollbackInfoElement =
        (CloudFormationRollbackInfoElement) cloudFormationElement0;
    assertThat(cloudFormationRollbackInfoElement.getOldStackBody()).isEqualTo("oldStackBody");
    assertThat(cloudFormationRollbackInfoElement.getCustomStackName()).isEqualTo("customStackName");
    assertThat(cloudFormationRollbackInfoElement.getAwsConfigId()).isEqualTo(configId);

    CloudFormationElement cloudFormationElement1 = cloudFormationElementList.get(1);
    assertThat(cloudFormationElement1).isInstanceOf(CloudFormationOutputInfoElement.class);
    if (checkOutputs) {
      CloudFormationOutputInfoElement cloudFormationOutputInfoElement =
          (CloudFormationOutputInfoElement) cloudFormationElement1;
      assertThat(cloudFormationOutputInfoElement.getNewStackOutputs()).containsKeys("key1");
    }
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseFailure() {
    Map<String, ResponseData> delegateResponse = ImmutableMap.of(ACTIVITY_ID,
        CloudFormationCommandExecutionResponse.builder()
            .commandExecutionStatus(FAILURE)
            .commandResponse(CloudFormationCreateStackResponse.builder().commandExecutionStatus(FAILURE).build())
            .build());
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    ExecutionResponse response = state.handleAsyncResponse(mockContext, delegateResponse);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleResponseFailure() {
    CloudFormationCreateStackResponse cloudFormationCreateStackResponse =
        CloudFormationCreateStackResponse.builder().commandExecutionStatus(FAILURE).build();
    List<CloudFormationElement> cloudFormationElements =
        state.handleResponse(cloudFormationCreateStackResponse, mockContext);
    assertThat(cloudFormationElements).isEqualTo(Collections.emptyList());
  }

  @Test
  @Owner(developers = NAVNEET)
  @Category(UnitTests.class)
  public void testExecuteInternalProvisionByGitEmptyBranch() {
    CloudFormationInfrastructureProvisioner cloudFormationInfrastructureProvisioner =
        CloudFormationInfrastructureProvisioner.builder()
            .sourceType(GIT.name())
            .gitFileConfig(GitFileConfig.builder().connectorId("sourceRepoSettingId").commitId("commitId").build())
            .build();

    GitConfig gitConfig = GitConfig.builder().urlType(GitConfig.UrlType.REPO).repoUrl(repoUrl).build();
    when(gitUtilsManager.getGitConfig("sourceRepoSettingId")).thenReturn(gitConfig);

    when(mockInfrastructureProvisionerService.get(any(), any())).thenReturn(cloudFormationInfrastructureProvisioner);

    state.setFileFetched(false);
    state.setTemplateExpressions(emptyList());
    state.setAwsConfigId("awsConfigId");
    ExecutionResponse executionResponse = state.executeInternal(mockContext, ACTIVITY_ID);

    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();

    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);

    GitFetchFilesTaskParams request = (GitFetchFilesTaskParams) delegateTask.getData().getParameters()[0];
    assertThat(request.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(request.getAppId()).isEqualTo(APP_ID);
    assertThat(request.getActivityId()).isEqualTo(ACTIVITY_ID);

    GitFetchFilesConfig gitFetchFilesConfig = request.getGitFetchFilesConfigMap().get("Cloud Formation parameters");
    GitConfig fileMapGitConfig = gitFetchFilesConfig.getGitConfig();
    assertThat(fileMapGitConfig.getReference()).isEqualTo("commitId");
    assertThat(fileMapGitConfig.getRepoName()).isNull();
    assertThat(fileMapGitConfig.getRepoUrl()).isEqualTo(repoUrl);

    GitFileConfig gitFileConfig = gitFetchFilesConfig.getGitFileConfig();
    assertThat(gitFileConfig).isEqualTo(cloudFormationInfrastructureProvisioner.getGitFileConfig());
  }

  private void verifyDelegateTask(DelegateTask delegateTask, boolean checkTags) {
    assertThat(delegateTask.getData().getTaskType()).isEqualTo(CLOUD_FORMATION_TASK.name());
    assertThat(delegateTask.getWaitId()).isEqualTo(ACTIVITY_ID);
    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    if (checkTags) {
      assertThat(delegateTask.getTags()).isNotEmpty();
      assertThat(delegateTask.getTags().get(0)).isEqualTo(TAG_NAME);
    }

    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(delegateTask.getData().getParameters().length).isEqualTo(2);
    CloudFormationCreateStackRequest request =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(request.getCommandType()).isEqualTo(CloudFormationCommandRequest.CloudFormationCommandType.CREATE_STACK);
    assertThat(request.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(request.getAwsConfig()).isEqualTo(awsConfig);
  }

  @Test
  @Owner(developers = PRAKHAR)
  @Category(UnitTests.class)
  public void buildDelegateTaskProvisionByBodyWithTagsAndCapabilities() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_BODY.name())
                                                              .templateBody(WingsTestConstants.TEMPLATE_BODY)
                                                              .build();
    state.customStackName = "customStackName";
    state.useCustomStackName = true;
    state.setAddTags(true);
    String tags =
        "[{\r\n\t\"key\": \"tagKey1\",\r\n\t\"value\": \"tagValue1\"\r\n}, {\r\n\t\"key\": \"tagKey2\",\r\n\t\"value\": \"tagValue2\"\r\n}]";
    state.setTags(tags);
    state.setSpecifyCapabilities(true);
    List<String> capabilities = Collections.singletonList("CAPABILITY_AUTO_EXPAND");
    state.setCapabilities(capabilities);
    state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    verifyDelegateTask(delegateTask, true);
    CloudFormationCreateStackRequest request =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(request.getData()).isEqualTo(WingsTestConstants.TEMPLATE_BODY);
    assertThat(request.getCreateType()).isEqualTo(CLOUDFORMATION_STACK_CREATE_BODY);
    assertThat(request.getCustomStackName()).isEqualTo("customStackName");
    assertThat(request.getCapabilities()).isEqualTo(capabilities);
    assertThat(request.getTags()).isEqualTo(tags);
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void verifyDelegateIsNoTimeoutIsFalse() {
    when(featureFlagService.isEnabled(CLOUDFORMATION_SKIP_WAIT_FOR_RESOURCES, mockContext.getAccountId()))
        .thenReturn(false);
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_URL.name())
                                                              .templateFilePath(TEMPLATE_FILE_PATH)
                                                              .build();

    state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    CloudFormationCreateStackRequest request =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(request.isSkipWaitForResources()).isFalse();
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void verifyDelegateIsNoTimeoutIsTrue() {
    when(featureFlagService.isEnabled(CLOUDFORMATION_SKIP_WAIT_FOR_RESOURCES, mockContext.getAccountId()))
        .thenReturn(true);
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_URL.name())
                                                              .templateFilePath(TEMPLATE_FILE_PATH)
                                                              .build();

    state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    CloudFormationCreateStackRequest request =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(request.isSkipWaitForResources()).isTrue();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void verifyDeployIsFalse() {
    when(featureFlagService.isEnabled(CLOUDFORMATION_CHANGE_SET, mockContext.getAccountId())).thenReturn(false);
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_URL.name())
                                                              .templateFilePath(TEMPLATE_FILE_PATH)
                                                              .build();

    state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    CloudFormationCreateStackRequest request =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(request.isDeploy()).isFalse();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void verifyDeployIsTrue() {
    when(featureFlagService.isEnabled(CLOUDFORMATION_CHANGE_SET, mockContext.getAccountId())).thenReturn(true);
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_URL.name())
                                                              .templateFilePath(TEMPLATE_FILE_PATH)
                                                              .build();

    state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    CloudFormationCreateStackRequest request =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(request.isDeploy()).isTrue();
  }

  @Test
  @Owner(developers = NAVNEET)
  @Category(UnitTests.class)
  public void verifyCommandUnitsForGitFileFetch() {
    CloudFormationInfrastructureProvisioner provisionerGit =
        CloudFormationInfrastructureProvisioner.builder().sourceType(GIT.name()).build();
    List<String> commandsGit = state.commandUnits(provisionerGit);

    assertThat(commandsGit.size()).isEqualTo(2);
    assertThat(commandsGit).contains("Fetch Files");

    CloudFormationInfrastructureProvisioner provisionerInline =
        CloudFormationInfrastructureProvisioner.builder().sourceType(TEMPLATE_BODY.name()).build();
    List<String> commandsInline = state.commandUnits(provisionerInline);

    assertThat(commandsInline.size()).isEqualTo(1);
    assertThat(commandsInline).doesNotContain("Fetch Files");

    CloudFormationInfrastructureProvisioner provisionerS3 =
        CloudFormationInfrastructureProvisioner.builder().sourceType(TEMPLATE_URL.name()).build();
    List<String> commandsS3 = state.commandUnits(provisionerS3);

    assertThat(commandsS3.size()).isEqualTo(1);
    assertThat(commandsS3).doesNotContain("Fetch Files");

    state.setUseParametersFile(true);
    List<String> commandsS3withParams = state.commandUnits(provisionerS3);

    assertThat(commandsS3withParams.size()).isEqualTo(2);
    assertThat(commandsS3withParams).contains("Fetch Files");
  }

  @Test
  @Owner(developers = NAVNEET)
  @Category(UnitTests.class)
  public void verifyDuplicateKeyErrorHandlingForTextVariables() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_URL.name())
                                                              .templateFilePath(TEMPLATE_FILE_PATH)
                                                              .build();
    when(mockInfrastructureProvisionerService.extractTextVariables(any(), any()))
        .thenThrow(new IllegalStateException("Duplicate key"));

    ExecutionResponse response = state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getErrorMessage()).isEqualTo("Duplicate key");
  }

  @Test
  @Owner(developers = NAVNEET)
  @Category(UnitTests.class)
  public void verifyDuplicateKeyErrorHandlingForEncryptedTextVariables() {
    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .sourceType(TEMPLATE_URL.name())
                                                              .templateFilePath(TEMPLATE_FILE_PATH)
                                                              .build();
    when(mockInfrastructureProvisionerService.extractEncryptedTextVariables(any(), any(), any()))
        .thenThrow(new IllegalStateException("Duplicate encrypted key"));

    ExecutionResponse response = state.buildAndQueueDelegateTask(mockContext, provisioner, awsConfig, ACTIVITY_ID);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getErrorMessage()).isEqualTo("Duplicate encrypted key");
  }
}
