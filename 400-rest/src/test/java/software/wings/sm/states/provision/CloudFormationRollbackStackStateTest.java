/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CF_ROLLBACK_CONFIG_FILTER;
import static io.harness.beans.FeatureName.CF_ROLLBACK_CUSTOM_STACK_NAME;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_BODY;
import static io.harness.delegate.task.cloudformation.CloudformationBaseHelperImpl.CLOUDFORMATION_STACK_CREATE_URL;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.TaskType.CLOUD_FORMATION_TASK;
import static software.wings.beans.infrastructure.CloudFormationRollbackConfig.CloudFormationRollbackConfigKeys;
import static software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest.CloudFormationCommandType.CREATE_STACK;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.TAG_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.ScriptStateExecutionData;
import software.wings.api.cloudformation.CloudFormationElement;
import software.wings.api.cloudformation.CloudFormationRollbackInfoElement;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.NameValuePair;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.CloudFormationRollbackConfig;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.cloudformation.CloudFormationCompletionFlag;
import software.wings.helpers.ext.cloudformation.CloudFormationRollbackCompletionFlag;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCommandRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationCreateStackRequest;
import software.wings.helpers.ext.cloudformation.request.CloudFormationDeleteStackRequest;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCommandExecutionResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationCreateStackResponse;
import software.wings.helpers.ext.cloudformation.response.CloudFormationRollbackInfo;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.google.common.collect.ImmutableMap;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class CloudFormationRollbackStackStateTest extends WingsBaseTest {
  @Mock private ActivityService mockActivityService;
  @Mock private DelegateService mockDelegateService;
  @Mock private SettingsService mockSettingsService;
  @Mock private AppService mockAppService;
  @Mock private SecretManager mockSecretManager;
  @Mock private InfrastructureProvisionerService mockInfrastructureProvisionerService;
  @Mock private LogService mockLogService;
  @Mock private WingsPersistence mockWingsPersistence;
  @Mock private ExecutionContextImpl mockContext;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock protected FeatureFlagService featureFlagService;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @InjectMocks private CloudFormationRollbackStackState state = new CloudFormationRollbackStackState("stateName");

  private CloudFormationRollbackConfig cloudFormationRollbackConfig;
  private SettingAttribute awsConfig;

  private static final String AWS_CONFIG_ID = "awsConfigId";
  private static final String CUSTOM_STACK_NAME = "customStackName";

  @Before
  public void setUp() {
    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(mockWingsPersistence).createQuery(any());
    MorphiaIterator<CloudFormationRollbackConfig, CloudFormationRollbackConfig> morphiaIterator =
        mock(MorphiaIterator.class);
    when(morphiaIterator.hasNext()).thenReturn(true).thenReturn(true).thenReturn(false);
    cloudFormationRollbackConfig = CloudFormationRollbackConfig.builder()
                                       .workflowExecutionId(WORKFLOW_EXECUTION_ID)
                                       .createType(CLOUDFORMATION_STACK_CREATE_URL)
                                       .awsConfigId(AWS_CONFIG_ID)
                                       .url("url")
                                       .variables(Arrays.asList(NameValuePair.builder().build()))
                                       .build();
    when(morphiaIterator.next()).thenReturn(cloudFormationRollbackConfig);
    when(mockQuery.fetch()).thenReturn(morphiaIterator);
    when(mockQuery.filter(any(), any())).thenReturn(mockQuery);
    when(mockQuery.order(any(Sort.class))).thenReturn(mockQuery);

    Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();
    when(mockContext.getAccountId()).thenReturn(ACCOUNT_ID);
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockParams).when(mockContext).getContextElement(ContextElementType.STANDARD);
    doReturn(mockParams).when(mockContext).fetchWorkflowStandardParamsFromContext();
    doReturn(env).when(workflowStandardParamsExtensionService).fetchRequiredEnv(mockParams);

    awsConfig = aSettingAttribute().withValue(AwsConfig.builder().tag(TAG_NAME).build()).build();
    doReturn(awsConfig).when(mockSettingsService).get(any());

    when(featureFlagService.isEnabled(CF_ROLLBACK_CONFIG_FILTER, ACCOUNT_ID)).thenReturn(true);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecute() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Application app = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    doReturn(app).when(mockContext).fetchRequiredApp();
    doReturn(app).when(mockContext).getApp();
    Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();

    EmbeddedUser mockCurrentUser = mock(EmbeddedUser.class);
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockCurrentUser).when(mockParams).getCurrentUser();
    doReturn(mockParams).when(mockContext).getContextElement(ContextElementType.STANDARD);
    doReturn(mockParams).when(mockContext).fetchWorkflowStandardParamsFromContext();
    doReturn(env).when(workflowStandardParamsExtensionService).fetchRequiredEnv(mockParams);

    Query mockQuery = mock(Query.class);
    doReturn(mockQuery).when(mockWingsPersistence).createQuery(any());
    doReturn(mockQuery).when(mockQuery).filter(any(), any());
    doReturn(mockQuery).when(mockQuery).order(any(Sort.class));
    MorphiaIterator mockIterator = mock(MorphiaIterator.class);
    doReturn(mockIterator).when(mockQuery).fetch();
    doReturn(false).when(mockIterator).hasNext();
    doReturn(env).when(mockContext).getEnv();
    Activity activity = Activity.builder().build();
    activity.setUuid(ACTIVITY_ID);
    doReturn(activity).when(mockActivityService).save(any());
    SettingAttribute awsConfig = aSettingAttribute().withValue(AwsConfig.builder().build()).build();
    doReturn(awsConfig).when(mockSettingsService).get(AWS_CONFIG_ID);
    CloudFormationRollbackInfoElement stackElement = CloudFormationRollbackInfoElement.builder()
                                                         .stackExisted(true)
                                                         .oldStackBody("oldBody")
                                                         .provisionerId(PROVISIONER_ID)
                                                         .awsConfigId(AWS_CONFIG_ID)
                                                         .customStackName(CUSTOM_STACK_NAME)
                                                         .region("region")
                                                         .oldStackParameters(ImmutableMap.of("oldKey", "oldVal"))
                                                         .build();
    doReturn(singletonList(stackElement)).when(mockContext).getContextElementList(any());
    doReturn(true).when(featureFlagService).isEnabled(eq(CF_ROLLBACK_CUSTOM_STACK_NAME), any());
    state.setProvisionerId(PROVISIONER_ID);
    state.setTimeoutMillis(1000);
    state.setCustomStackName(CUSTOM_STACK_NAME);
    state.setRegion("region");
    state.setUseCustomStackName(true);
    doReturn(CUSTOM_STACK_NAME).when(mockContext).renderExpression(CUSTOM_STACK_NAME);
    doReturn("region").when(mockContext).renderExpression("region");
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    when(sweepingOutputService.find(any()))
        .thenReturn(SweepingOutputInstance.builder()
                        .value(CloudFormationCompletionFlag.builder().createStackCompleted(true).build())
                        .build())
        .thenReturn(null);
    ExecutionResponse response = state.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(2).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof CloudFormationCreateStackRequest).isTrue();
    CloudFormationCreateStackRequest createStackRequest =
        (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
    assertThat(CREATE_STACK).isEqualTo(createStackRequest.getCommandType());
    assertThat("oldBody").isEqualTo(createStackRequest.getData());
    assertThat(awsConfig.getValue()).isEqualTo(createStackRequest.getAwsConfig());
    assertThat(1000).isEqualTo(createStackRequest.getTimeoutInMs());
    Map<String, String> stackParam = createStackRequest.getVariables();
    assertThat(stackParam).isNotNull();
    assertThat(1).isEqualTo(stackParam.size());
    assertThat("oldVal").isEqualTo(stackParam.get("oldKey"));
    verify(mockQuery).filter(CloudFormationRollbackConfigKeys.customStackName, CUSTOM_STACK_NAME);
    verify(mockQuery).filter(CloudFormationRollbackConfigKeys.region, "region");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteInternalWithSavedElement() {
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    when(sweepingOutputService.find(any()))
        .thenReturn(SweepingOutputInstance.builder()
                        .value(CloudFormationCompletionFlag.builder().createStackCompleted(true).build())
                        .build())
        .thenReturn(null);
    ExecutionResponse response = state.executeInternal(mockContext, ACTIVITY_ID);
    ScriptStateExecutionData stateExecutionData = (ScriptStateExecutionData) response.getStateExecutionData();
    assertThat(stateExecutionData.getActivityId()).isEqualTo(ACTIVITY_ID);
    verifyDelegate(CLOUDFORMATION_STACK_CREATE_URL, "url", false, true, (AwsConfig) awsConfig.getValue());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteInternalTypeCreateBody() {
    //   no variables and create type is of type create body
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    when(sweepingOutputService.find(any()))
        .thenReturn(SweepingOutputInstance.builder()
                        .value(CloudFormationCompletionFlag.builder().createStackCompleted(true).build())
                        .build())
        .thenReturn(null);
    cloudFormationRollbackConfig.setVariables(null);
    cloudFormationRollbackConfig.setBody("body");
    cloudFormationRollbackConfig.setCreateType(CLOUDFORMATION_STACK_CREATE_BODY);
    cloudFormationRollbackConfig.setCapabilities(Arrays.asList("C1"));
    cloudFormationRollbackConfig.setTags("Tags");
    AwsConfig config = AwsConfig.builder().build();
    awsConfig.setValue(config);
    ExecutionResponse response = state.executeInternal(mockContext, ACTIVITY_ID);
    verifyDelegate(CLOUDFORMATION_STACK_CREATE_BODY, "body", false, false, config);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteInternal() {
    Application application = new Application();
    application.setAccountId(ACCOUNT_ID);
    when(mockContext.getApp()).thenReturn(application);

    when(mockContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);

    CloudFormationRollbackInfoElement stackElement = CloudFormationRollbackInfoElement.builder()
                                                         .stackExisted(false)
                                                         .provisionerId(PROVISIONER_ID)
                                                         .awsConfigId(AWS_CONFIG_ID)
                                                         .build();
    doReturn(singletonList(stackElement)).when(mockContext).getContextElementList(any());

    state.provisionerId = PROVISIONER_ID;
    state.setAwsConfigId(AWS_CONFIG_ID);
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    when(sweepingOutputService.find(any()))
        .thenReturn(SweepingOutputInstance.builder()
                        .value(CloudFormationCompletionFlag.builder().createStackCompleted(true).build())
                        .build())
        .thenReturn(null);
    ExecutionResponse response = state.executeInternal(mockContext, ACTIVITY_ID);
    ScriptStateExecutionData stateExecutionData = (ScriptStateExecutionData) response.getStateExecutionData();
    assertThat(stateExecutionData.getActivityId()).isEqualTo(ACTIVITY_ID);
    verifyDelegate(CLOUDFORMATION_STACK_CREATE_URL, "url", true, true, (AwsConfig) awsConfig.getValue());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInternalWhenRollBackSkipped() {
    Application application = new Application();
    application.setAccountId(ACCOUNT_ID);
    when(mockContext.getApp()).thenReturn(application);
    when(mockContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);

    CloudFormationRollbackInfoElement stackElement = CloudFormationRollbackInfoElement.builder()
                                                         .stackExisted(false)
                                                         .provisionerId(PROVISIONER_ID)
                                                         .awsConfigId("awsConfigId")
                                                         .build();
    doReturn(singletonList(stackElement)).when(mockContext).getContextElementList(any());

    state.provisionerId = PROVISIONER_ID;
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    ExecutionResponse response = state.executeInternal(mockContext, ACTIVITY_ID);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInternalWhenRollBackWithCustomStackNameSkipped() {
    Application application = new Application();
    application.setAccountId(ACCOUNT_ID);
    doReturn(true).when(featureFlagService).isEnabled(eq(CF_ROLLBACK_CUSTOM_STACK_NAME), anyString());
    when(mockContext.getApp()).thenReturn(application);
    when(mockContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);
    CloudFormationRollbackInfoElement stackElement = CloudFormationRollbackInfoElement.builder()
                                                         .stackExisted(false)
                                                         .provisionerId(PROVISIONER_ID)
                                                         .awsConfigId("awsConfigId")
                                                         .build();
    doReturn(singletonList(stackElement)).when(mockContext).getContextElementList(any());
    state.setRegion("region");
    state.provisionerId = PROVISIONER_ID;
    state.setAwsConfigId(AWS_CONFIG_ID);
    state.setUseCustomStackName(true);
    state.setCustomStackName(CUSTOM_STACK_NAME);
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    ArgumentCaptor<SweepingOutputInquiry> captor = ArgumentCaptor.forClass(SweepingOutputInquiry.class);

    ExecutionResponse response = state.executeInternal(mockContext, ACTIVITY_ID);

    verify(sweepingOutputService, times(1)).find(captor.capture());
    assertThat(captor.getValue().getName())
        .isEqualTo("CloudFormationCompletionFlag awsConfigId-PROVISIONER_ID-customStackName-region");
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    assertThat(response.getErrorMessage()).isEqualTo("Skipping rollback");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInternalWhenRollBackWithCustomStackNameSkippedBecauseRollbackDoneInAPreviousStep() {
    Application application = new Application();
    application.setAccountId(ACCOUNT_ID);
    doReturn(true).when(featureFlagService).isEnabled(eq(CF_ROLLBACK_CUSTOM_STACK_NAME), anyString());
    when(mockContext.getApp()).thenReturn(application);
    when(mockContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);
    CloudFormationRollbackInfoElement stackElement = CloudFormationRollbackInfoElement.builder()
                                                         .stackExisted(false)
                                                         .provisionerId(PROVISIONER_ID)
                                                         .awsConfigId("awsConfigId")
                                                         .build();
    doReturn(singletonList(stackElement)).when(mockContext).getContextElementList(any());
    state.setRegion("region");
    state.provisionerId = PROVISIONER_ID;
    state.setAwsConfigId(AWS_CONFIG_ID);
    state.setUseCustomStackName(true);
    state.setCustomStackName(CUSTOM_STACK_NAME);
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    when(sweepingOutputService.find(any()))
        .thenReturn(SweepingOutputInstance.builder()
                        .value(CloudFormationCompletionFlag.builder().createStackCompleted(true).build())
                        .build())
        .thenReturn(SweepingOutputInstance.builder()
                        .value(CloudFormationRollbackCompletionFlag.builder().rollbackCompleted(true).build())
                        .build());
    ArgumentCaptor<SweepingOutputInquiry> captor = ArgumentCaptor.forClass(SweepingOutputInquiry.class);

    ExecutionResponse response = state.executeInternal(mockContext, ACTIVITY_ID);

    verify(sweepingOutputService, times(2)).find(captor.capture());
    assertThat(captor.getAllValues().get(0).getName())
        .isEqualTo("CloudFormationCompletionFlag awsConfigId-PROVISIONER_ID-customStackName-region");
    assertThat(captor.getAllValues().get(1).getName())
        .isEqualTo("CloudFormationRollbackCompletionFlag awsConfigId-PROVISIONER_ID-customStackName-region");
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    assertThat(response.getErrorMessage()).isEqualTo("Rollback done in a previous step, skipping");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInternalWhenRollBackWithoutCustomStackNameSkippedBecauseRollbackDoneInAPreviousStep() {
    Application application = new Application();
    application.setAccountId(ACCOUNT_ID);
    doReturn(true).when(featureFlagService).isEnabled(eq(CF_ROLLBACK_CUSTOM_STACK_NAME), anyString());
    when(mockContext.getApp()).thenReturn(application);
    when(mockContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);
    CloudFormationRollbackInfoElement stackElement = CloudFormationRollbackInfoElement.builder()
                                                         .stackExisted(false)
                                                         .provisionerId(PROVISIONER_ID)
                                                         .awsConfigId("awsConfigId")
                                                         .build();
    doReturn(singletonList(stackElement)).when(mockContext).getContextElementList(any());
    state.setRegion("region");
    state.provisionerId = PROVISIONER_ID;
    state.setAwsConfigId(AWS_CONFIG_ID);
    state.setUseCustomStackName(false);
    state.setCustomStackName(CUSTOM_STACK_NAME);
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    when(sweepingOutputService.find(any()))
        .thenReturn(SweepingOutputInstance.builder()
                        .value(CloudFormationCompletionFlag.builder().createStackCompleted(true).build())
                        .build())
        .thenReturn(SweepingOutputInstance.builder()
                        .value(CloudFormationRollbackCompletionFlag.builder().rollbackCompleted(true).build())
                        .build());
    ArgumentCaptor<SweepingOutputInquiry> captor = ArgumentCaptor.forClass(SweepingOutputInquiry.class);

    ExecutionResponse response = state.executeInternal(mockContext, ACTIVITY_ID);

    verify(sweepingOutputService, times(2)).find(captor.capture());
    assertThat(captor.getAllValues().get(0).getName())
        .isEqualTo("CloudFormationCompletionFlag awsConfigId-PROVISIONER_ID");
    assertThat(captor.getAllValues().get(1).getName())
        .isEqualTo("CloudFormationRollbackCompletionFlag awsConfigId-PROVISIONER_ID");
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    assertThat(response.getErrorMessage()).isEqualTo("Rollback done in a previous step, skipping");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseNoRollbackElements() {
    Map<String, ResponseData> delegateResponse = ImmutableMap.of(ACTIVITY_ID,
        CloudFormationCommandExecutionResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .commandResponse(CloudFormationCreateStackResponse.builder().commandExecutionStatus(SUCCESS).build())
            .build());
    ExecutionResponse response = state.handleAsyncResponse(mockContext, delegateResponse);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getContextElements()).isEmpty();
    assertThat(response.getNotifyElements()).isEmpty();
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleResponseStackDidNotExist() {
    CloudFormationRollbackInfoElement stackElement = CloudFormationRollbackInfoElement.builder()
                                                         .stackExisted(false)
                                                         .awsConfigId(AWS_CONFIG_ID)
                                                         .provisionerId(PROVISIONER_ID)
                                                         .build();
    doReturn(singletonList(stackElement)).when(mockContext).getContextElementList(any());
    doReturn(CUSTOM_STACK_NAME).when(mockContext).renderExpression(eq(CUSTOM_STACK_NAME));
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    when(sweepingOutputService.find(any())).thenReturn(null);
    CloudFormationCreateStackResponse commandResponse = CloudFormationCreateStackResponse.builder().build();
    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    state.setProvisionerId(PROVISIONER_ID);
    state.setAwsConfigId(AWS_CONFIG_ID);
    List<CloudFormationElement> cloudFormationElementList = state.handleResponse(commandResponse, mockContext);
    assertThat(cloudFormationElementList).isEqualTo(emptyList());
    verify(sweepingOutputService).save(captor.capture());
    assertThat(captor.getValue().getName())
        .isEqualTo("CloudFormationRollbackCompletionFlag awsConfigId-PROVISIONER_ID");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleResponseStackDidNotExistCustomStackName() {
    CloudFormationRollbackInfoElement stackElement = CloudFormationRollbackInfoElement.builder()
                                                         .stackExisted(false)
                                                         .customStackName(CUSTOM_STACK_NAME)
                                                         .region("region")
                                                         .awsConfigId(AWS_CONFIG_ID)
                                                         .provisionerId(PROVISIONER_ID)
                                                         .build();
    doReturn(true).when(featureFlagService).isEnabled(eq(CF_ROLLBACK_CUSTOM_STACK_NAME), anyString());
    doReturn(singletonList(stackElement)).when(mockContext).getContextElementList(any());
    doReturn(CUSTOM_STACK_NAME).when(mockContext).renderExpression(eq(CUSTOM_STACK_NAME));
    doReturn("region").when(mockContext).renderExpression(eq("region"));
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    when(sweepingOutputService.find(any())).thenReturn(null);
    CloudFormationCreateStackResponse commandResponse = CloudFormationCreateStackResponse.builder().build();
    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    state.setProvisionerId(PROVISIONER_ID);
    state.setAwsConfigId(AWS_CONFIG_ID);
    state.setCustomStackName(CUSTOM_STACK_NAME);
    state.setUseCustomStackName(true);
    state.setRegion("region");

    List<CloudFormationElement> cloudFormationElementList = state.handleResponse(commandResponse, mockContext);

    verify(mockWingsPersistence, times(1)).delete(any(Query.class));
    assertThat(cloudFormationElementList).isEqualTo(emptyList());
    verify(sweepingOutputService, times(1)).save(captor.capture());
    assertThat(captor.getValue().getName())
        .isEqualTo("CloudFormationRollbackCompletionFlag awsConfigId-PROVISIONER_ID-customStackName-region");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleResponseStackDidNotExistClearRollbackConfig() {
    doReturn(true).when(featureFlagService).isEnabled(eq(CF_ROLLBACK_CUSTOM_STACK_NAME), anyString());
    CloudFormationRollbackInfoElement stackElement1 = CloudFormationRollbackInfoElement.builder()
                                                          .stackExisted(false)
                                                          .customStackName(CUSTOM_STACK_NAME)
                                                          .region("region")
                                                          .provisionerId(PROVISIONER_ID)
                                                          .awsConfigId(AWS_CONFIG_ID)
                                                          .build();
    CloudFormationRollbackInfoElement stackElement2 = CloudFormationRollbackInfoElement.builder()
                                                          .stackExisted(false)
                                                          .customStackName("customStackName2")
                                                          .region("region")
                                                          .provisionerId(PROVISIONER_ID)
                                                          .awsConfigId(AWS_CONFIG_ID)
                                                          .build();
    doReturn(Arrays.asList(stackElement1, stackElement2)).when(mockContext).getContextElementList(any());
    doReturn(CUSTOM_STACK_NAME).when(mockContext).renderExpression(eq(CUSTOM_STACK_NAME));
    doReturn("region").when(mockContext).renderExpression("region");
    CloudFormationCreateStackResponse commandResponse = CloudFormationCreateStackResponse.builder().build();
    Query query = mock(Query.class);
    doReturn(query).when(mockWingsPersistence).createQuery(any());
    doReturn(query).when(query).filter(any(), any());
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    when(sweepingOutputService.find(any())).thenReturn(null);
    state.setProvisionerId(PROVISIONER_ID);
    state.setAwsConfigId(AWS_CONFIG_ID);
    state.setUseCustomStackName(true);
    state.setCustomStackName(CUSTOM_STACK_NAME);
    state.setRegion("region");

    List<CloudFormationElement> cloudFormationElementList = state.handleResponse(commandResponse, mockContext);

    assertThat(cloudFormationElementList).isEqualTo(emptyList());
    verify(query).filter(CloudFormationRollbackConfigKeys.awsConfigId, AWS_CONFIG_ID);
    verify(query).filter(CloudFormationRollbackConfigKeys.customStackName, CUSTOM_STACK_NAME);
    verify(query).filter(CloudFormationRollbackConfigKeys.region, "region");
  }

  private void verifyDelegate(
      String createType, String data, boolean stackExisted, boolean checkTags, AwsConfig config) {
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(delegateTask.getData().getTaskType()).isEqualTo(CLOUD_FORMATION_TASK.name());
    assertThat(delegateTask.getWaitId()).isEqualTo(ACTIVITY_ID);
    if (checkTags) {
      assertThat(delegateTask.getTags()).isNotEmpty();
      assertThat(delegateTask.getTags().get(0)).isEqualTo(TAG_NAME);
    }
    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);

    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(delegateTask.getData().getParameters().length).isEqualTo(2);
    if (stackExisted) {
      CloudFormationDeleteStackRequest deleteRequest =
          (CloudFormationDeleteStackRequest) delegateTask.getData().getParameters()[0];
      assertThat(deleteRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(deleteRequest.getCommandType())
          .isEqualTo(CloudFormationCommandRequest.CloudFormationCommandType.DELETE_STACK);
      assertThat(deleteRequest.getAwsConfig()).isEqualTo(config);
    } else {
      CloudFormationCreateStackRequest createRequest =
          (CloudFormationCreateStackRequest) delegateTask.getData().getParameters()[0];
      assertThat(createRequest.getCreateType()).isEqualTo(createType);
      assertThat(createRequest.getData()).isEqualTo(data);
      assertThat(createRequest.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(createRequest.getAwsConfig()).isEqualTo(config);
      assertThat(createRequest.getCommandType())
          .isEqualTo(CloudFormationCommandRequest.CloudFormationCommandType.CREATE_STACK);
      assertThat(createRequest.getCapabilities()).isEqualTo(cloudFormationRollbackConfig.getCapabilities());
      assertThat(createRequest.getTags()).isEqualTo(cloudFormationRollbackConfig.getTags());
    }
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAsync() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    doReturn(APP_ID).when(mockContext).getAppId();
    CloudFormationRollbackInfoElement stackElement =
        CloudFormationRollbackInfoElement.builder().stackExisted(true).provisionerId(PROVISIONER_ID).build();
    doReturn(singletonList(stackElement)).when(mockContext).getContextElementList(any());
    doReturn(ScriptStateExecutionData.builder().build()).when(mockContext).getStateExecutionData();
    Map<String, ResponseData> delegateResponse = ImmutableMap.of(ACTIVITY_ID,
        CloudFormationCommandExecutionResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .commandResponse(CloudFormationCreateStackResponse.builder()
                                 .cloudFormationOutputMap(ImmutableMap.of("k1", "v1"))
                                 .rollbackInfo(CloudFormationRollbackInfo.builder().build())
                                 .commandExecutionStatus(SUCCESS)
                                 .build())
            .build());
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockParams).when(mockContext).fetchWorkflowStandardParamsFromContext();
    doReturn(SweepingOutputInquiry.builder()).when(mockContext).prepareSweepingOutputInquiryBuilder();
    doReturn(SweepingOutputInstance.builder()).when(mockContext).prepareSweepingOutputBuilder(any());
    when(sweepingOutputService.find(any())).thenReturn(null);
    Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(env).when(workflowStandardParamsExtensionService).fetchRequiredEnv(mockParams);
    state.setProvisionerId(PROVISIONER_ID);

    ExecutionResponse response = state.handleAsyncResponse(mockContext, delegateResponse);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    verify(mockActivityService).updateStatus(eq(ACTIVITY_ID), eq(APP_ID), eq(ExecutionStatus.SUCCESS));
    ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
    verify(mockInfrastructureProvisionerService)
        .regenerateInfrastructureMappings(any(), any(), captor.capture(), any(), any());
    Map<String, Object> map = captor.getValue();
    assertThat(1).isEqualTo(map.size());
    assertThat("v1").isEqualTo(map.get("k1"));
  }
}
