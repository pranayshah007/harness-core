/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.ABORTED;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.PAUSED;
import static io.harness.beans.ExecutionStatus.REJECTED;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.DHRUV;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.YOGESH;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.ElementExecutionSummary.ElementExecutionSummaryBuilder.anElementExecutionSummary;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.NotificationGroup.NotificationGroupBuilder.aNotificationGroup;
import static software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import static software.wings.beans.WorkflowExecution.builder;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.APPROVAL_EXPIRED_WORKFLOW_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.WORKFLOW_ABORT_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.WORKFLOW_PAUSE_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.WORKFLOW_RESUME_NOTIFICATION;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;
import static software.wings.security.JWT_CATEGORY.EXTERNAL_SERVICE_SECRET;
import static software.wings.service.impl.slack.SlackApprovalUtils.createSlackApprovalMessage;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APPROVAL_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_SOURCE_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.BUILD_JOB_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.JIRA_ISSUE_ID;
import static software.wings.utils.WingsTestConstants.NOTIFICATION_GROUP_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.UUID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;
import static software.wings.utils.WingsTestConstants.WORKFLOW_URL;

import static java.util.Arrays.asList;
import static org.apache.commons.lang3.reflect.FieldUtils.writeField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ArtifactMetadata;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageResponse;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.WingsBaseTest;
import software.wings.api.AmiServiceDeployElement;
import software.wings.api.ApprovalStateExecutionData;
import software.wings.api.ApprovalStateExecutionData.ApprovalStateExecutionDataKeys;
import software.wings.api.ServiceNowExecutionData;
import software.wings.api.WorkflowElement;
import software.wings.api.jira.JiraExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.ElementExecutionSummary;
import software.wings.beans.EnvSummary;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.ExecutionScope;
import software.wings.beans.NameValuePair;
import software.wings.beans.NameValuePair.NameValuePairKeys;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.TemplateExpression;
import software.wings.beans.User;
import software.wings.beans.User.Builder;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.ApprovalNeededAlert;
import software.wings.beans.approval.ApprovalStateParams;
import software.wings.beans.approval.ApprovalStateParams.ApprovalStateParamsKeys;
import software.wings.beans.approval.ConditionalOperator;
import software.wings.beans.approval.Criteria;
import software.wings.beans.approval.JiraApprovalParams;
import software.wings.beans.approval.ServiceNowApprovalParams;
import software.wings.beans.approval.ShellScriptApprovalParams;
import software.wings.beans.approval.ShellScriptApprovalParams.ShellScriptApprovalParamsKeys;
import software.wings.beans.approval.SlackApprovalParams;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.security.UserGroup;
import software.wings.common.NotificationMessageResolver;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.persistence.artifact.Artifact;
import software.wings.security.SecretManager;
import software.wings.service.ApprovalUtils;
import software.wings.service.impl.JiraHelperService;
import software.wings.service.impl.notifications.SlackApprovalMessageKeys;
import software.wings.service.impl.workflow.WorkflowNotificationDetails;
import software.wings.service.impl.workflow.WorkflowNotificationHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.ApprovalPolingService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.servicenow.ServiceNowService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.ApprovalState.ApprovalStateKeys;
import software.wings.sm.states.ApprovalState.ApprovalStateType;
import software.wings.sm.states.EnvState.EnvStateKeys;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.sf.json.JSONArray;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.text.StringEscapeUtils;
import org.joor.Reflect;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Created by anubhaw on 11/3/16.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ApprovalStateTest extends WingsBaseTest {
  private static final WorkflowStandardParams WORKFLOW_STANDARD_PARAMS =
      aWorkflowStandardParams()
          .withArtifactIds(asList(ARTIFACT_ID))
          .withCurrentUser(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).email(USER_EMAIL).build())
          .build();
  private static final String USER_NAME_1_KEY = "UserName1";
  private static final String SERVICENOW_STATE = "state";
  private Integer DEFAULT_APPROVAL_STATE_TIMEOUT_MILLIS = 7 * 24 * 60 * 60 * 1000; // 7 days
  @Mock private ExecutionContextImpl context;
  @Mock private AlertService alertService;
  @Mock private NotificationService notificationService;
  @Mock private NotificationSetupService notificationSetupService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private NotificationMessageResolver notificationMessageResolver;
  @Mock private WorkflowNotificationHelper workflowNotificationHelper;
  @Mock private PipelineService pipelineService;
  @Mock private SecretManager secretManager;
  @Mock private SweepingOutputService sweepingOutputService;
  @Mock private JiraHelperService jiraHelperService;
  @Mock private ServiceNowService serviceNowService;
  @Mock private ApprovalPolingService approvalPolingService;
  @Mock private UserGroupService userGroupService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private State state;
  @Mock private TemplateExpressionProcessor templateExpressionProcessor;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ActivityService activityService;
  @Mock private FeatureFlagService featureFlagService;
  @InjectMocks private ApprovalState approvalState = new ApprovalState("ApprovalState");

  @Inject KryoSerializer kryoSerializer;

  private static JSONArray projects;
  private static Object statuses;
  private static WorkflowExecution execution =
      WorkflowExecution.builder()
          .status(ExecutionStatus.NEW)
          .appId(APP_ID)
          .triggeredBy(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).build())
          .serviceIds(Collections.singletonList(SERVICE_ID))
          .environments(Collections.singletonList(EnvSummary.builder().name(ENV_NAME).build()))
          .artifacts(Collections.singletonList(anArtifact()
                                                   .withArtifactSourceName(ARTIFACT_SOURCE_NAME)
                                                   .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                                   .build()))
          .createdAt(70L)
          .infraDefinitionIds(Collections.singletonList(INFRA_DEFINITION_ID))
          .build();

  @BeforeClass
  public static void readMockData() throws IOException {
    projects = new ObjectMapper().readValue(new File("400-rest/src/test/resources/mock_projects"), JSONArray.class);
    statuses = new ObjectMapper().readValue(new File("400-rest/src/test/resources/mock_statuses"), JSONArray.class);
  }

  @Before
  public void setUp() throws Exception {
    when(context.getApp()).thenReturn(anApplication().accountId(ACCOUNT_ID).uuid(APP_ID).build());
    when(context.getAppId()).thenReturn(APP_ID);
    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(WORKFLOW_STANDARD_PARAMS);
    when(context.getWorkflowExecutionId()).thenReturn(PIPELINE_WORKFLOW_EXECUTION_ID);
    when(context.getWorkflowExecutionName()).thenReturn(BUILD_JOB_NAME);
    when(context.getWorkflowId()).thenReturn(WORKFLOW_ID);
    when(secretManager.getJWTSecret(EXTERNAL_SERVICE_SECRET)).thenReturn("secret");
    when(activityService.save(any())).thenReturn(Activity.builder().uuid(UUID).build());
    writeField(approvalState, "secretManager", secretManager, true);

    PipelineStageElement pipelineStageElement = PipelineStageElement.builder().name("ApprovalState").build();
    PipelineStage pipelineStage = PipelineStage.builder()
                                      .pipelineStageElements(Collections.singletonList(pipelineStageElement))
                                      .name("Approval Needed")
                                      .build();
    Pipeline pipeline = Pipeline.builder()
                            .name("pipeline")
                            .appId(APP_ID)
                            .uuid(WORKFLOW_ID)
                            .pipelineStages(Collections.singletonList(pipelineStage))
                            .build();
    when(pipelineService.readPipeline(APP_ID, WORKFLOW_ID, true)).thenReturn(pipeline);

    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.buildNo, "2");
    Artifact artifact =
        anArtifact().withMetadata(new ArtifactMetadata(metadata)).withArtifactSourceName("Artifact").build();
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setArtifacts(Collections.singletonList(artifact));

    EnvSummary envsummary = EnvSummary.builder().name("Environment").build();

    List<ElementExecutionSummary> elementExecutionSummaries = new ArrayList<>();
    AmiServiceDeployElement amiServiceDeployElement = AmiServiceDeployElement.builder().build();

    elementExecutionSummaries.add(0, anElementExecutionSummary().withContextElement(amiServiceDeployElement).build());

    WorkflowExecution workflowExecution = builder()
                                              .executionArgs(executionArgs)
                                              .environments(Collections.singletonList(envsummary))
                                              .serviceExecutionSummaries(elementExecutionSummaries)
                                              .build();

    when(
        workflowExecutionService.getExecutionDetails(context.getAppId(), context.getWorkflowExecutionId(), true, false))
        .thenReturn(workflowExecution);

    String approvalId = generateUuid();
    Map<String, String> claims = new HashMap<>();
    claims.put("approvalId", approvalId);
    String secret = "secret";
    when(secretManager.generateJWTTokenWithCustomTimeOut(claims, secret, 60 * 60 * 1000 * 168)).thenReturn("token");

    when(workflowExecutionService.fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID,
             WorkflowExecutionKeys.createdAt, WorkflowExecutionKeys.triggeredBy, WorkflowExecutionKeys.status))
        .thenReturn(builder()
                        .status(ExecutionStatus.NEW)
                        .appId(APP_ID)
                        .triggeredBy(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).build())
                        .createdAt(70L)
                        .build());

    when(notificationSetupService.listDefaultNotificationGroup(any()))
        .thenReturn(asList(aNotificationGroup()
                               .withName(USER_NAME)
                               .withUuid(NOTIFICATION_GROUP_ID)
                               .withAccountId(ACCOUNT_ID)
                               .build()));

    Map<String, String> placeholders = new HashMap<>();
    when(notificationMessageResolver.getPlaceholderValues(
             any(), any(), any(Long.class), any(Long.class), any(), any(), any(), any(), any()))
        .thenReturn(placeholders);

    when(workflowExecutionService.fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID,
             WorkflowExecutionKeys.artifacts, WorkflowExecutionKeys.environments, WorkflowExecutionKeys.serviceIds,
             WorkflowExecutionKeys.infraDefinitionIds))
        .thenReturn(execution);
    when(serviceResourceService.fetchServiceNamesByUuids(APP_ID, asList(SERVICE_ID))).thenReturn(asList(SERVICE_NAME));
    when(workflowNotificationHelper.getArtifactsDetails(any(), any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().message("*Artifacts*: artifacts").name("artifacts").build());
    when(workflowNotificationHelper.calculateServiceDetailsForAllServices(any(), any(), any(), any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().message("services").name("services").build());
    when(workflowNotificationHelper.calculateInfraDetails(any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().message("infra").name("infra").build());
    when(workflowNotificationHelper.calculateApplicationDetails(any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().message("app").name("nameW/Omrkdwn").build());
    when(workflowNotificationHelper.calculateEnvDetails(any(), any(), any()))
        .thenReturn(WorkflowNotificationDetails.builder().message("*Environments:* env").build());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecutePipeline() {
    PageResponse pageResponse = new PageResponse();
    pageResponse.setResponse(asList(User.Builder.anUser().build()));
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.PIPELINE).build());

    when(context.getWorkflowType()).thenReturn(WorkflowType.PIPELINE);

    ExecutionResponse executionResponse = approvalState.execute(context);
    verify(alertService)
        .openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.ApprovalNeeded), any(ApprovalNeededAlert.class));
    assertThat(executionResponse.isAsync()).isTrue();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);

    Mockito.verify(workflowNotificationHelper, Mockito.times(1))
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_PAUSE_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.USER_GROUP));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);

    verify(workflowNotificationHelper, times(1))
        .calculateServiceDetailsForAllServices(ACCOUNT_ID, APP_ID, context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateInfraDetails(ACCOUNT_ID, APP_ID, execution);
    verify(workflowNotificationHelper, times(1)).calculateApplicationDetails(ACCOUNT_ID, APP_ID, context.getApp());
    verify(workflowNotificationHelper, times(1)).getArtifactsDetails(context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateEnvDetails(ACCOUNT_ID, APP_ID, execution.getEnvironments());
    verify(workflowExecutionService, times(1))
        .fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID, WorkflowExecutionKeys.artifacts,
            WorkflowExecutionKeys.environments, WorkflowExecutionKeys.serviceIds,
            WorkflowExecutionKeys.infraDefinitionIds);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldExecuteWorkflow() {
    PageResponse pageResponse = new PageResponse();
    pageResponse.setResponse(asList(User.Builder.anUser().build()));
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());

    when(context.getWorkflowType()).thenReturn(WorkflowType.ORCHESTRATION);
    ExecutionResponse executionResponse = approvalState.execute(context);
    verify(alertService)
        .openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.ApprovalNeeded), any(ApprovalNeededAlert.class));
    assertThat(executionResponse.isAsync()).isTrue();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);

    Mockito.verify(workflowNotificationHelper, Mockito.times(1))
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_PAUSE_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.USER_GROUP));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);

    verify(workflowNotificationHelper, times(1))
        .calculateServiceDetailsForAllServices(ACCOUNT_ID, APP_ID, context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateInfraDetails(ACCOUNT_ID, APP_ID, execution);
    verify(workflowNotificationHelper, times(1)).calculateApplicationDetails(ACCOUNT_ID, APP_ID, context.getApp());
    verify(workflowNotificationHelper, times(1)).getArtifactsDetails(context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateEnvDetails(ACCOUNT_ID, APP_ID, execution.getEnvironments());
    verify(workflowExecutionService, times(1))
        .fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID, WorkflowExecutionKeys.artifacts,
            WorkflowExecutionKeys.environments, WorkflowExecutionKeys.serviceIds,
            WorkflowExecutionKeys.infraDefinitionIds);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSkipDisabledStep() {
    PageResponse pageResponse = new PageResponse();
    pageResponse.setResponse(Collections.singletonList(Builder.anUser().build()));
    approvalState.setDisableAssertion("true");
    when(context.evaluateExpression(eq("true"), any())).thenReturn(true);
    ExecutionResponse executionResponse = approvalState.execute(context);
    verify(context).renderExpression("true");
    verify(alertService, times(0))
        .openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.ApprovalNeeded), any(ApprovalNeededAlert.class));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SKIPPED);
    assertThat(executionResponse.getErrorMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSkipDisabledStepWithAssertion() {
    String disableAssertion = "${app.name}==\"APP_NAME\"";
    approvalState.setDisableAssertion(disableAssertion);
    when(context.evaluateExpression(eq(disableAssertion), any())).thenReturn(true);
    ExecutionResponse executionResponse = approvalState.execute(context);
    verify(context).renderExpression(disableAssertion);
    verify(workflowExecutionService, times(0))
        .triggerOrchestrationExecution(
            eq(APP_ID), eq(null), eq(WORKFLOW_ID), eq(PIPELINE_WORKFLOW_EXECUTION_ID), any(), any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SKIPPED);
    assertThat(executionResponse.getErrorMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldFailIfAssertionException() {
    String disableAssertion = "${app.name]==\"APP_NAME\"";
    approvalState.setDisableAssertion(disableAssertion);
    when(context.evaluateExpression(eq(disableAssertion), any())).thenThrow(JexlException.class);
    ExecutionResponse executionResponse = approvalState.execute(context);
    verify(context).renderExpression(disableAssertion);
    verify(workflowExecutionService, times(0))
        .triggerOrchestrationExecution(
            eq(APP_ID), eq(null), eq(WORKFLOW_ID), eq(PIPELINE_WORKFLOW_EXECUTION_ID), any(), any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(executionResponse.getErrorMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldExecuteIfAssertionFailed() {
    String disableAssertion = "${app.name]==\"APP_NAM\"";
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());
    approvalState.setDisableAssertion(disableAssertion);
    when(context.evaluateExpression(eq(disableAssertion), any())).thenReturn(false);

    ExecutionResponse executionResponse = approvalState.execute(context);

    verify(context).renderExpression(disableAssertion);
    verify(workflowNotificationHelper, times(1))
        .calculateServiceDetailsForAllServices(ACCOUNT_ID, APP_ID, context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateInfraDetails(ACCOUNT_ID, APP_ID, execution);
    verify(workflowNotificationHelper, times(1)).calculateApplicationDetails(ACCOUNT_ID, APP_ID, context.getApp());
    verify(workflowNotificationHelper, times(1)).getArtifactsDetails(context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateEnvDetails(ACCOUNT_ID, APP_ID, execution.getEnvironments());
    verify(workflowExecutionService, times(1))
        .fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID, WorkflowExecutionKeys.artifacts,
            WorkflowExecutionKeys.environments, WorkflowExecutionKeys.serviceIds,
            WorkflowExecutionKeys.infraDefinitionIds);
    verify(workflowExecutionService, times(0))
        .triggerOrchestrationExecution(
            eq(APP_ID), eq(null), eq(WORKFLOW_ID), eq(PIPELINE_WORKFLOW_EXECUTION_ID), any(), any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetTimeout() {
    Integer timeoutMillis = approvalState.getTimeoutMillis();
    assertThat(timeoutMillis).isEqualTo(DEFAULT_APPROVAL_STATE_TIMEOUT_MILLIS);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldGetSetTimeout() {
    approvalState.setTimeoutMillis((int) (0.6 * TimeUnit.HOURS.toMillis(1)));
    Integer timeoutMillis = approvalState.getTimeoutMillis();
    assertThat(timeoutMillis).isEqualTo((int) (0.6 * TimeUnit.HOURS.toMillis(1)));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldHandlePipelineAbortWithTimeoutMsg() {
    approvalState.setTimeoutMillis((int) (0.6 * TimeUnit.HOURS.toMillis(1)));

    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.PIPELINE).uuid(generateUuid()).build());

    when(notificationMessageResolver.getApprovalType(any())).thenReturn(WorkflowType.PIPELINE.name());

    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId("APPROVAL_ID").build();
    approvalStateExecutionData.setStartTs((long) (System.currentTimeMillis() - (0.6 * TimeUnit.HOURS.toMillis(1))));
    when(context.getStateExecutionData()).thenReturn(approvalStateExecutionData);

    approvalState.handleAbortEvent(context);
    assertThat(context.getStateExecutionData()).isNotNull();
    assertThat(context.getStateExecutionData().getErrorMsg()).contains("Pipeline was not approved within 36m");

    Mockito.verify(workflowNotificationHelper, Mockito.times(1))
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(APPROVAL_EXPIRED_WORKFLOW_NOTIFICATION),
            Mockito.anyMap(), Mockito.any(), Mockito.eq(ApprovalStateType.USER_GROUP));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowAbortWithTimeoutMsg() {
    approvalState.setTimeoutMillis((int) (0.6 * TimeUnit.HOURS.toMillis(1)));

    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).uuid(generateUuid()).build());

    when(notificationMessageResolver.getApprovalType(any())).thenReturn(WorkflowType.ORCHESTRATION.name());

    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId("APPROVAL_ID").build();
    approvalStateExecutionData.setStartTs((long) (System.currentTimeMillis() - (0.6 * TimeUnit.HOURS.toMillis(1))));
    when(context.getStateExecutionData()).thenReturn(approvalStateExecutionData);
    approvalState.handleAbortEvent(context);
    assertThat(context.getStateExecutionData()).isNotNull();
    assertThat(context.getStateExecutionData().getErrorMsg()).contains("Workflow was not approved within 36m");

    Mockito.verify(workflowNotificationHelper, Mockito.times(1))
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(APPROVAL_EXPIRED_WORKFLOW_NOTIFICATION),
            Mockito.anyMap(), Mockito.any(), Mockito.eq(ApprovalStateType.USER_GROUP));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldHandlePipelineAbortWithAbortMsg() {
    approvalState.setTimeoutMillis((int) (0.6 * TimeUnit.HOURS.toMillis(1)));

    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.PIPELINE).uuid(generateUuid()).build());

    when(notificationMessageResolver.getApprovalType(any())).thenReturn(WorkflowType.PIPELINE.name());

    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId("APPROVAL_ID").build();
    approvalStateExecutionData.setStartTs(System.currentTimeMillis());

    when(context.getStateExecutionData()).thenReturn(approvalStateExecutionData);
    approvalState.handleAbortEvent(context);
    assertThat(context.getStateExecutionData()).isNotNull();
    assertThat(context.getStateExecutionData().getErrorMsg()).contains("Pipeline was aborted");
    Mockito.verify(workflowNotificationHelper, Mockito.times(1))
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_ABORT_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.USER_GROUP));
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldHandleWorkflowAbortWithAbortMsg() {
    approvalState.setTimeoutMillis((int) (0.6 * TimeUnit.HOURS.toMillis(1)));

    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).uuid(generateUuid()).build());

    when(notificationMessageResolver.getApprovalType(any())).thenReturn(WorkflowType.ORCHESTRATION.name());

    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId("APPROVAL_ID").build();
    approvalStateExecutionData.setStartTs(System.currentTimeMillis());

    when(context.getStateExecutionData()).thenReturn(approvalStateExecutionData);
    approvalState.handleAbortEvent(context);
    assertThat(context.getStateExecutionData()).isNotNull();
    assertThat(context.getStateExecutionData().getErrorMsg()).contains("Workflow was aborted");
    Mockito.verify(workflowNotificationHelper, Mockito.times(1))
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_ABORT_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.USER_GROUP));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testGetPlaceholderValues() {
    ApprovalStateExecutionData approvalStateExecutionData = ApprovalStateExecutionData.builder().build();
    approvalStateExecutionData.setStartTs(100L);
    approvalState.setTimeoutMillis(100);

    when(context.getStateExecutionData()).thenReturn(approvalStateExecutionData);

    approvalState.getPlaceholderValues(context, USER_NAME_1_KEY, ABORTED);
    verify(notificationMessageResolver)
        .getPlaceholderValues(any(), eq(USER_NAME_1_KEY), eq(100L), any(Long.class), eq("100"), eq("aborted"), any(),
            eq(ABORTED), eq(AlertType.ApprovalNeeded));

    approvalState.getPlaceholderValues(context, USER_NAME_1_KEY, PAUSED);
    verify(notificationMessageResolver)
        .getPlaceholderValues(any(), eq(USER_NAME), eq(70L), any(Long.class), eq("100"), eq("paused"), any(),
            eq(PAUSED), eq(AlertType.ApprovalNeeded));

    approvalState.getPlaceholderValues(context, USER_NAME_1_KEY, SUCCESS);
    verify(notificationMessageResolver)
        .getPlaceholderValues(any(), eq(USER_NAME_1_KEY), any(Long.class), any(Long.class), eq("100"), eq("approved"),
            any(), eq(SUCCESS), eq(AlertType.ApprovalNeeded));
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testApprovalNeededAlertParamsForWorkflow() {
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());
    when(context.getEnv()).thenReturn(anEnvironment().appId(APP_ID).uuid(ENV_ID).build());
    when(context.getWorkflowType()).thenReturn(WorkflowType.PIPELINE);
    approvalState.execute(context);
    ArgumentCaptor<ApprovalNeededAlert> argumentCaptor = ArgumentCaptor.forClass(ApprovalNeededAlert.class);
    verify(alertService).openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.ApprovalNeeded), argumentCaptor.capture());

    ApprovalNeededAlert approvalNeededAlert = argumentCaptor.getValue();
    assertThat(approvalNeededAlert.getEnvId()).isEqualTo(ENV_ID);
    assertThat(approvalNeededAlert.getWorkflowType()).isEqualTo(WorkflowType.ORCHESTRATION);
    assertThat(approvalNeededAlert.getWorkflowExecutionId()).isEqualTo(PIPELINE_WORKFLOW_EXECUTION_ID);
    assertThat(approvalNeededAlert.getPipelineExecutionId()).isEqualTo(null);

    verify(workflowNotificationHelper, times(1))
        .calculateServiceDetailsForAllServices(ACCOUNT_ID, APP_ID, context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateInfraDetails(ACCOUNT_ID, APP_ID, execution);
    verify(workflowNotificationHelper, times(1)).calculateApplicationDetails(ACCOUNT_ID, APP_ID, context.getApp());
    verify(workflowNotificationHelper, times(1)).getArtifactsDetails(context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateEnvDetails(ACCOUNT_ID, APP_ID, execution.getEnvironments());
    verify(workflowExecutionService, times(1))
        .fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID, WorkflowExecutionKeys.artifacts,
            WorkflowExecutionKeys.environments, WorkflowExecutionKeys.serviceIds,
            WorkflowExecutionKeys.infraDefinitionIds);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testApprovalNeededAlertParamsForPipelineWithApproval() {
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.PIPELINE).build());
    when(context.getWorkflowType()).thenReturn(WorkflowType.PIPELINE);
    approvalState.execute(context);
    ArgumentCaptor<ApprovalNeededAlert> argumentCaptor = ArgumentCaptor.forClass(ApprovalNeededAlert.class);
    verify(alertService).openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.ApprovalNeeded), argumentCaptor.capture());

    ApprovalNeededAlert approvalNeededAlert = argumentCaptor.getValue();
    assertThat(approvalNeededAlert.getEnvId()).isEqualTo(null);
    assertThat(approvalNeededAlert.getWorkflowType()).isEqualTo(WorkflowType.PIPELINE);
    assertThat(approvalNeededAlert.getWorkflowExecutionId()).isEqualTo(null);
    assertThat(approvalNeededAlert.getPipelineExecutionId()).isEqualTo(PIPELINE_WORKFLOW_EXECUTION_ID);
    verify(workflowNotificationHelper, times(1))
        .calculateServiceDetailsForAllServices(ACCOUNT_ID, APP_ID, context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateInfraDetails(ACCOUNT_ID, APP_ID, execution);
    verify(workflowNotificationHelper, times(1)).calculateApplicationDetails(ACCOUNT_ID, APP_ID, context.getApp());
    verify(workflowNotificationHelper, times(1)).getArtifactsDetails(context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateEnvDetails(ACCOUNT_ID, APP_ID, execution.getEnvironments());
    verify(workflowExecutionService, times(1))
        .fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID, WorkflowExecutionKeys.artifacts,
            WorkflowExecutionKeys.environments, WorkflowExecutionKeys.serviceIds,
            WorkflowExecutionKeys.infraDefinitionIds);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testApprovalNeededAlertParamsForPipelineWithWorkflowApproval() {
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());
    when(context.getEnv()).thenReturn(anEnvironment().appId(APP_ID).uuid(ENV_ID).build());
    when(context.getWorkflowType()).thenReturn(WorkflowType.PIPELINE);
    when(context.getContextElement(ContextElementType.STANDARD))
        .thenReturn(
            aWorkflowStandardParams()
                .withCurrentUser(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).email(USER_EMAIL).build())
                .withWorkflowElement(WorkflowElement.builder().pipelineDeploymentUuid(PIPELINE_EXECUTION_ID).build())
                .build());

    approvalState.execute(context);
    ArgumentCaptor<ApprovalNeededAlert> argumentCaptor = ArgumentCaptor.forClass(ApprovalNeededAlert.class);
    verify(alertService).openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.ApprovalNeeded), argumentCaptor.capture());

    ApprovalNeededAlert approvalNeededAlert = argumentCaptor.getValue();
    assertThat(approvalNeededAlert.getEnvId()).isEqualTo(ENV_ID);
    assertThat(approvalNeededAlert.getWorkflowType()).isEqualTo(WorkflowType.ORCHESTRATION);
    assertThat(approvalNeededAlert.getWorkflowExecutionId()).isEqualTo(PIPELINE_WORKFLOW_EXECUTION_ID);
    assertThat(approvalNeededAlert.getPipelineExecutionId()).isEqualTo(PIPELINE_EXECUTION_ID);

    verify(workflowNotificationHelper, times(1))
        .calculateServiceDetailsForAllServices(ACCOUNT_ID, APP_ID, context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateInfraDetails(ACCOUNT_ID, APP_ID, execution);
    verify(workflowNotificationHelper, times(1)).calculateApplicationDetails(ACCOUNT_ID, APP_ID, context.getApp());
    verify(workflowNotificationHelper, times(1)).getArtifactsDetails(context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateEnvDetails(ACCOUNT_ID, APP_ID, execution.getEnvironments());
    verify(workflowExecutionService, times(1))
        .fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID, WorkflowExecutionKeys.artifacts,
            WorkflowExecutionKeys.environments, WorkflowExecutionKeys.serviceIds,
            WorkflowExecutionKeys.infraDefinitionIds);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testParseProperties() {
    final Map<String, Object> properties = new HashMap<>();
    Map<String, String> var_1 = new HashMap<>();
    Map<String, String> var_2 = new HashMap<>();
    Map<String, String> var_3 = new HashMap<>();
    var_1.put(NameValuePairKeys.name, "var_1");
    var_2.put(NameValuePairKeys.name, "var_2");
    var_2.put(NameValuePairKeys.value, "val_2");
    var_3.put(NameValuePairKeys.name, "var_1"); // duplicate name
    var_3.put(NameValuePairKeys.value, "val_3");
    properties.put(ApprovalStateKeys.variables, new ArrayList<>(asList(var_1, var_2, var_3)));
    properties.put(EnvStateKeys.disable, true);
    approvalState.parseProperties(properties);
    assertThat(properties.get(EnvStateKeys.disableAssertion)).isEqualTo("true");
    assertThat(approvalState.getVariables()).isNotNull().hasSize(2);
    assertThat(approvalState.getVariables().stream().filter(val -> val.getValue() == null).collect(Collectors.toList()))
        .isEmpty();

    assertThat(approvalState.getVariables().stream().map(NameValuePair::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("var_1", "var_2");
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testParsePropertiesWithInvalidTimeout() {
    Integer maximumTimeout = 2147400049;
    final Map<String, Object> properties = new HashMap<>();
    properties.put("timeoutMillis", maximumTimeout + 50);
    assertThatThrownBy(() -> approvalState.parseProperties(properties)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testParsePropertiesWithTimeoutAsString() {
    final Map<String, Object> properties = new HashMap<>();
    Integer timeout = 50934043;
    properties.put("timeoutMillis", timeout.toString());
    approvalState.parseProperties(properties);
    assertThat(approvalState.getTimeoutMillis()).isEqualTo(timeout);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testParsePropertiesWithNegativeTimeout() {
    final Map<String, Object> properties = new HashMap<>();
    properties.put("timeoutMillis", "-543423");
    assertThatThrownBy(() -> approvalState.parseProperties(properties)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testSetPipelineVariables() {
    final ExecutionContext executionContextMock = mock(ExecutionContext.class);
    final WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    workflowStandardParams.setWorkflowVariables(ImmutableMap.of("key", "value"));
    doReturn(workflowStandardParams).when(executionContextMock).getContextElement(ContextElementType.STANDARD);
    approvalState.setPipelineVariables(executionContextMock);
    assertThat(workflowStandardParams.getWorkflowElement()).isNotNull();

    approvalState.setPipelineVariables(executionContextMock);
    assertThat(workflowStandardParams.getWorkflowElement().getVariables().get("key")).isEqualTo("value");
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSetPipelineVariables1() {
    final ExecutionContext executionContextMock = mock(ExecutionContext.class);
    final WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    workflowStandardParams.setWorkflowVariables(ImmutableMap.of("key", "value"));
    doReturn(workflowStandardParams).when(executionContextMock).getContextElement(ContextElementType.STANDARD);
    Map<String, Object> variableMap = new HashMap<>();
    variableMap.put("var1", "value1");
    variableMap.put("var2", "value2");
    variableMap.put("var3", "value3");
    WorkflowElement workflowElement = WorkflowElement.builder().variables(variableMap).build();
    workflowStandardParams.setWorkflowElement(workflowElement);
    approvalState.setPipelineVariables(executionContextMock);
    assertThat(workflowStandardParams.getWorkflowElement().getVariables().get("key")).isEqualTo("value");
    assertThat(workflowStandardParams.getWorkflowElement().getVariables().get("var1")).isEqualTo("value1");
    assertThat(workflowStandardParams.getWorkflowElement().getVariables().get("var2")).isEqualTo("value2");
    assertThat(workflowStandardParams.getWorkflowElement().getVariables().get("var3")).isEqualTo("value3");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testFillSweepingOutput() {
    when(context.renderExpression(any(), any(StateExecutionContext.class)))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    when(context.prepareSweepingOutputBuilder(any())).thenReturn(SweepingOutputInstance.builder());
    Reflect.on(approvalState).set("kryoSerializer", kryoSerializer);
    approvalState.setSweepingOutputName("test");
    approvalState.setVariables(asList(NameValuePair.builder().name("test").value("test").valueType("TEXT").build()));
    verifyUserGroupSweepingOutput();
    verifyJiraSweepingOutput();
    verifySnowSweepingOutput();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testExecuteJiraApprovalFailure() {
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    JiraApprovalParams jiraApprovalParams = new JiraApprovalParams();
    jiraApprovalParams.setIssueId("${a}");
    approvalStateParams.setJiraApprovalParams(jiraApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData =
        ApprovalStateExecutionData.builder().approvalStateType(ApprovalStateType.JIRA).build();

    ExecutionResponse executionResponse = approvalState.executeJiraApproval(context, executionData, "id");

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);

    when(jiraHelperService.fetchIssue(any(), any(), any(), any()))
        .thenReturn(JiraExecutionData.builder().executionStatus(FAILED).build());

    approvalStateParams.getJiraApprovalParams().setIssueId("issueId");
    executionResponse = approvalState.executeJiraApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testExecuteJiraApprovalIfAlreadyApproved() {
    String approvalValue = "DONE";
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    when(jiraHelperService.fetchIssue(any(), any(), any(), any()))
        .thenReturn(JiraExecutionData.builder().currentStatus(approvalValue).build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    JiraApprovalParams jiraApprovalParams = new JiraApprovalParams();
    jiraApprovalParams.setIssueId("issueId");
    jiraApprovalParams.setApprovalValue(approvalValue);
    approvalStateParams.setJiraApprovalParams(jiraApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData =
        ApprovalStateExecutionData.builder().approvalStateType(ApprovalStateType.JIRA).build();

    ExecutionResponse executionResponse = approvalState.executeJiraApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testExecuteJiraApprovalIfAlreadyRejected() {
    String rejectionValue = "REJECTED";
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    when(jiraHelperService.fetchIssue(any(), any(), any(), any()))
        .thenReturn(JiraExecutionData.builder().currentStatus(rejectionValue).build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    JiraApprovalParams jiraApprovalParams = new JiraApprovalParams();
    jiraApprovalParams.setIssueId("issueId");
    jiraApprovalParams.setRejectionValue(rejectionValue);
    approvalStateParams.setJiraApprovalParams(jiraApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData =
        ApprovalStateExecutionData.builder().approvalStateType(ApprovalStateType.JIRA).build();

    ExecutionResponse executionResponse = approvalState.executeJiraApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(REJECTED);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testExecuteJiraApprovalWithPollingService() {
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    when(jiraHelperService.fetchIssue(any(), any(), any(), any()))
        .thenReturn(JiraExecutionData.builder().currentStatus("TODO").build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    JiraApprovalParams jiraApprovalParams = new JiraApprovalParams();
    jiraApprovalParams.setIssueId("issueId");
    jiraApprovalParams.setApprovalValue("DONE");
    approvalStateParams.setJiraApprovalParams(jiraApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData =
        ApprovalStateExecutionData.builder().approvalStateType(ApprovalStateType.JIRA).build();

    ExecutionResponse executionResponse = approvalState.executeJiraApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);

    when(approvalPolingService.save(any())).thenThrow(new UnexpectedException());
    executionResponse = approvalState.executeJiraApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testExecuteSnowApprovalFailure() {
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    ServiceNowApprovalParams serviceNowApprovalParams = ServiceNowApprovalParams.builder().issueNumber("${a}").build();
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData =
        ApprovalStateExecutionData.builder().approvalStateType(ApprovalStateType.SERVICENOW).build();

    ExecutionResponse executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteSnowApprovalIfAlreadyApprovedWithMultipleValues() {
    String approvalValue = "Approved";
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    when(serviceNowService.getIssueUrl(any(), any(), any()))
        .thenReturn(ServiceNowExecutionData.builder()
                        .currentState(SERVICENOW_STATE)
                        .currentStatus(Collections.singletonMap("approval", approvalValue))
                        .build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    Criteria approvalCriteria = new Criteria();
    approvalCriteria.setConditions(
        Collections.singletonMap("approval", asList("Requested", "Canceled", approvalValue)));
    ServiceNowApprovalParams serviceNowApprovalParams =
        ServiceNowApprovalParams.builder().issueNumber("issueNumber").approval(approvalCriteria).build();
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalStateType(ApprovalStateType.SERVICENOW)
                                                   .snowApproval(approvalCriteria)
                                                   .build();

    ExecutionResponse executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteSnowApprovalIfAlreadyRejectedWithMultipleANDConditions() {
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    when(serviceNowService.getIssueUrl(any(), any(), any()))
        .thenReturn(ServiceNowExecutionData.builder()
                        .currentState(SERVICENOW_STATE)
                        .currentStatus(ImmutableMap.of(SERVICENOW_STATE, "Closed", "approval", "Approved"))
                        .build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    Criteria rejectionCriteria = new Criteria();
    rejectionCriteria.setConditions(
        ImmutableMap.of(SERVICENOW_STATE, asList("Closed", "Cancelled"), "approval", asList("Approved", "Requested")));
    Criteria approvalCriteria = new Criteria();
    approvalCriteria.setConditions(Collections.singletonMap("state", Collections.singletonList("Review")));
    ServiceNowApprovalParams serviceNowApprovalParams = ServiceNowApprovalParams.builder()
                                                            .issueNumber("issueNumber")
                                                            .approval(approvalCriteria)
                                                            .rejection(rejectionCriteria)
                                                            .build();
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalStateType(ApprovalStateType.SERVICENOW)
                                                   .snowApproval(approvalCriteria)
                                                   .snowRejection(rejectionCriteria)
                                                   .build();

    ExecutionResponse executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(REJECTED);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteSnowApprovalIfAlreadyRejectedWithMultipleORConditions() {
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    Date todayDate = new Date(System.currentTimeMillis());
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    when(serviceNowService.getIssueUrl(any(), any(), any()))
        .thenReturn(
            ServiceNowExecutionData.builder()
                .currentState(SERVICENOW_STATE)
                .currentStatus(ImmutableMap.of(SERVICENOW_STATE, "Scheduled", "approval", "Approved", "start",
                    dateFormat.format(todayDate) + " 23:59:59", "end", dateFormat.format(todayDate) + " 00:00:00"))
                .build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    Criteria rejectionCriteria = new Criteria();
    rejectionCriteria.setOperator(ConditionalOperator.OR);
    rejectionCriteria.setConditions(
        ImmutableMap.of(SERVICENOW_STATE, asList("Closed", "Cancelled"), "approval", asList("Approved", "Requested")));
    Criteria approvalCriteria = new Criteria();
    approvalCriteria.setConditions(Collections.singletonMap("state", Collections.singletonList("Review")));
    ServiceNowApprovalParams serviceNowApprovalParams = ServiceNowApprovalParams.builder()
                                                            .issueNumber("issueNumber")
                                                            .rejection(rejectionCriteria)
                                                            .approval(approvalCriteria)
                                                            .changeWindowEndField("end")
                                                            .changeWindowStartField("start")
                                                            .changeWindowPresent(true)
                                                            .build();
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalStateType(ApprovalStateType.SERVICENOW)
                                                   .snowApproval(approvalCriteria)
                                                   .snowRejection(rejectionCriteria)
                                                   .build();

    ExecutionResponse executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(REJECTED);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteSnowApprovalPausedIfConditionsNotMet() {
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    when(serviceNowService.getIssueUrl(any(), any(), any()))
        .thenReturn(ServiceNowExecutionData.builder()
                        .currentState(SERVICENOW_STATE)
                        .currentStatus(ImmutableMap.of(SERVICENOW_STATE, "Scheduled", "approval", "Approved"))
                        .build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    Criteria rejectionCriteria = new Criteria();
    rejectionCriteria.setConditions(
        ImmutableMap.of(SERVICENOW_STATE, asList("Closed", "Cancelled"), "approval", asList("Approved", "Requested")));
    Criteria approvalCriteria = new Criteria();
    approvalCriteria.setConditions(Collections.singletonMap("state", Collections.singletonList("Review")));
    ServiceNowApprovalParams serviceNowApprovalParams = ServiceNowApprovalParams.builder()
                                                            .issueNumber("issueNumber")
                                                            .approval(approvalCriteria)
                                                            .rejection(rejectionCriteria)
                                                            .build();
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalStateType(ApprovalStateType.SERVICENOW)
                                                   .snowApproval(approvalCriteria)
                                                   .snowRejection(rejectionCriteria)
                                                   .build();

    ExecutionResponse executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldApproveSnowApprovalIfBothCriteriaMet() {
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    when(serviceNowService.getIssueUrl(any(), any(), any()))
        .thenReturn(ServiceNowExecutionData.builder()
                        .currentState(SERVICENOW_STATE)
                        .currentStatus(ImmutableMap.of(SERVICENOW_STATE, "Scheduled", "approval", "Approved"))
                        .build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    Criteria approvalCriteria = new Criteria();
    approvalCriteria.setConditions(
        ImmutableMap.of(SERVICENOW_STATE, asList("Closed", "Scheduled"), "approval", asList("Approved", "Requested")));
    Criteria rejectionCriteria = new Criteria();
    rejectionCriteria.setConditions(ImmutableMap.of(
        SERVICENOW_STATE, asList("Scheduled", "Cancelled"), "approval", asList("Approved", "Rejected")));
    ServiceNowApprovalParams serviceNowApprovalParams = ServiceNowApprovalParams.builder()
                                                            .issueNumber("issueNumber")
                                                            .rejection(rejectionCriteria)
                                                            .approval(approvalCriteria)
                                                            .build();
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalStateType(ApprovalStateType.SERVICENOW)
                                                   .snowApproval(rejectionCriteria)
                                                   .build();

    ExecutionResponse executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testExecuteSnowApprovalIfAlreadyRejected() {
    String rejectionValue = "REJECTED";
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    when(serviceNowService.getIssueUrl(any(), any(), any()))
        .thenReturn(ServiceNowExecutionData.builder()
                        .currentStatus(Collections.singletonMap(SERVICENOW_STATE, rejectionValue))
                        .build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    ServiceNowApprovalParams serviceNowApprovalParams =
        ServiceNowApprovalParams.builder()
            .issueNumber("issueNumber")
            .rejection(
                new Criteria(Collections.singletonMap(SERVICENOW_STATE, Collections.singletonList(rejectionValue)),
                    ConditionalOperator.AND))
            .approval(new Criteria(Collections.singletonMap(SERVICENOW_STATE, Collections.singletonList("APPROVED")),
                ConditionalOperator.AND))
            .build();
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData =
        ApprovalStateExecutionData.builder().approvalStateType(ApprovalStateType.SERVICENOW).build();

    ExecutionResponse executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(REJECTED);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteSnowApprovalWithPollingServiceWithApprovalField() {
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    when(serviceNowService.getIssueUrl(any(), any(), any()))
        .thenReturn(ServiceNowExecutionData.builder()
                        .currentStatus(Collections.singletonMap(SERVICENOW_STATE, "REQUESTED"))
                        .build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    ServiceNowApprovalParams serviceNowApprovalParams =
        ServiceNowApprovalParams.builder()
            .issueNumber("issueNumber")
            .approval(new Criteria(
                Collections.singletonMap(SERVICENOW_STATE, asList("DONE", "IMPLEMENTED")), ConditionalOperator.OR))
            .build();
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData =
        ApprovalStateExecutionData.builder().approvalStateType(ApprovalStateType.SERVICENOW).build();

    ExecutionResponse executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);

    when(approvalPolingService.save(any())).thenThrow(new UnexpectedException());
    executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteSnowApprovalWithChangeWindow() {
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    Date todayDate = new Date(System.currentTimeMillis());
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    when(serviceNowService.getIssueUrl(any(), any(), any()))
        .thenReturn(ServiceNowExecutionData.builder()
                        .currentState(SERVICENOW_STATE)
                        .currentStatus(ImmutableMap.of(SERVICENOW_STATE, "Scheduled", "approval", "Approved", "start",
                            dateFormat.format(todayDate), "end", dateFormat.format(todayDate) + " 23:59:59"))
                        .build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    Criteria approvalCriteria = new Criteria();
    approvalCriteria.setConditions(
        ImmutableMap.of(SERVICENOW_STATE, asList("Closed", "Scheduled"), "approval", asList("Approved", "Requested")));
    Criteria rejectionCriteria = new Criteria();
    rejectionCriteria.setConditions(ImmutableMap.of(
        SERVICENOW_STATE, asList("Scheduled", "Cancelled"), "approval", asList("Approved", "Rejected")));
    ServiceNowApprovalParams serviceNowApprovalParams = ServiceNowApprovalParams.builder()
                                                            .issueNumber("issueNumber")
                                                            .rejection(rejectionCriteria)
                                                            .approval(approvalCriteria)
                                                            .changeWindowEndField("end")
                                                            .changeWindowStartField("start")
                                                            .changeWindowPresent(true)
                                                            .build();
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalStateType(ApprovalStateType.SERVICENOW)
                                                   .snowApproval(rejectionCriteria)
                                                   .build();

    ExecutionResponse executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteSnowApprovalWithChangeWindowNullValues() {
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    Date todayDate = new Date(System.currentTimeMillis());
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    when(serviceNowService.getIssueUrl(any(), any(), any()))
        .thenReturn(ServiceNowExecutionData.builder()
                        .currentState(SERVICENOW_STATE)
                        .currentStatus(ImmutableMap.of(SERVICENOW_STATE, "Scheduled", "approval", "Approved", "start",
                            dateFormat.format(todayDate)))
                        .build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    Criteria approvalCriteria = new Criteria();
    approvalCriteria.setConditions(
        ImmutableMap.of(SERVICENOW_STATE, asList("Closed", "Scheduled"), "approval", asList("Approved", "Requested")));
    Criteria rejectionCriteria = new Criteria();
    rejectionCriteria.setConditions(ImmutableMap.of(
        SERVICENOW_STATE, asList("Scheduled", "Cancelled"), "approval", asList("Approved", "Rejected")));
    ServiceNowApprovalParams serviceNowApprovalParams = ServiceNowApprovalParams.builder()
                                                            .issueNumber("issueNumber")
                                                            .rejection(rejectionCriteria)
                                                            .approval(approvalCriteria)
                                                            .changeWindowEndField("end")
                                                            .changeWindowStartField("start")
                                                            .changeWindowPresent(true)
                                                            .build();
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalStateType(ApprovalStateType.SERVICENOW)
                                                   .snowApproval(rejectionCriteria)
                                                   .build();

    ExecutionResponse executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("Change Window End Time value in Ticket is invalid");

    when(serviceNowService.getIssueUrl(any(), any(), any()))
        .thenReturn(
            ServiceNowExecutionData.builder()
                .currentState(SERVICENOW_STATE)
                .currentStatus(ImmutableMap.of(SERVICENOW_STATE, "Scheduled", "approval", "Approved", "start",
                    dateFormat.format(todayDate) + " 23:59:59", "end", dateFormat.format(todayDate) + " 00:00:00"))
                .build());

    executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("Start Window Time must be earlier than End Window Time");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteSnowApprovalWithChangeWindowNotSatisfied() {
    when(context.renderExpression(any())).thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, String.class));
    Date todayDate = new Date(System.currentTimeMillis());
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    when(serviceNowService.getIssueUrl(any(), any(), any()))
        .thenReturn(
            ServiceNowExecutionData.builder()
                .currentState(SERVICENOW_STATE)
                .currentStatus(ImmutableMap.of(SERVICENOW_STATE, "Scheduled", "approval", "Approved", "start",
                    dateFormat.format(todayDate) + " 23:59:58", "end", dateFormat.format(todayDate) + " 23:59:59"))
                .build());

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    Criteria approvalCriteria = new Criteria();
    approvalCriteria.setConditions(
        ImmutableMap.of(SERVICENOW_STATE, asList("Closed", "Scheduled"), "approval", asList("Approved", "Requested")));
    Criteria rejectionCriteria = new Criteria();
    rejectionCriteria.setConditions(ImmutableMap.of(
        SERVICENOW_STATE, asList("Scheduled", "Cancelled"), "approval", asList("Not yet Requested", "Rejected")));
    ServiceNowApprovalParams serviceNowApprovalParams = ServiceNowApprovalParams.builder()
                                                            .issueNumber("issueNumber")
                                                            .rejection(rejectionCriteria)
                                                            .approval(approvalCriteria)
                                                            .changeWindowEndField("end")
                                                            .changeWindowStartField("start")
                                                            .changeWindowPresent(true)
                                                            .build();
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalStateType(ApprovalStateType.SERVICENOW)
                                                   .snowApproval(rejectionCriteria)
                                                   .build();

    ExecutionResponse executionResponse = approvalState.executeServiceNowApproval(context, executionData, "id");
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldOutputCorrectMessageOnFailure() {
    StateExecutionData stateExecutionData = ApprovalStateExecutionData.builder().build();
    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance()
            .displayName(STATE_NAME)
            .stateExecutionMap(Collections.singletonMap(STATE_NAME, stateExecutionData))
            .build();
    when(stateExecutionService.getStateExecutionData(APP_ID, STATE_EXECUTION_ID)).thenReturn(stateExecutionInstance);
    ArgumentCaptor<ApprovalStateExecutionData> captor = ArgumentCaptor.forClass(ApprovalStateExecutionData.class);
    ApprovalStateExecutionData approvalData =
        ApprovalStateExecutionData.builder().appId(APP_ID).approvalId(APPROVAL_EXECUTION_ID).build();
    ApprovalUtils.checkApproval(stateExecutionService, waitNotifyEngine, WORKFLOW_EXECUTION_ID, STATE_EXECUTION_ID,
        "SOME_ERROR", FAILED, approvalData);
    verify(waitNotifyEngine).doneWith(any(), captor.capture());
    assertThat(captor.getValue()).isNotNull();
    assertThat(captor.getValue().getStatus()).isEqualTo(FAILED);
    assertThat(captor.getValue().getErrorMsg()).isEqualTo("Jira/ServiceNow approval failed: SOME_ERROR ticket: ");
  }

  @Test
  @Owner(developers = DHRUV)
  @Category(UnitTests.class)
  public void shouldExecuteWorkflowWithTemplateExpressionWithName() {
    PageResponse pageResponse = new PageResponse();
    pageResponse.setResponse(asList(User.Builder.anUser().build()));
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());

    List<TemplateExpression> templateExpressions = new ArrayList<>();
    TemplateExpression t = new TemplateExpression();
    t.setFieldName("userGroups");
    t.setExpression("${USER_GROUP}");
    templateExpressions.add(t);
    on(approvalState).set("templateExpressions", templateExpressions);
    UserGroup userGroup = new UserGroup();
    userGroup.setName("Account Administrator");
    userGroup.setUuid("dIyaCXXVRp65abGOlN5Fmg");

    when(templateExpressionProcessor.getTemplateExpression(any(), eq("userGroups"))).thenReturn(t);
    when(templateExpressionProcessor.resolveTemplateExpression(any(), any())).thenReturn("Account Administrator");
    when(state.getTemplateExpressions()).thenReturn(templateExpressions);
    when(userGroupService.get(any(), eq("Account Administrator"))).thenReturn(null);
    when(userGroupService.fetchUserGroupByName(any(), eq("Account Administrator"))).thenReturn(userGroup);

    when(context.getWorkflowType()).thenReturn(WorkflowType.ORCHESTRATION);
    ExecutionResponse executionResponse = approvalState.execute(context);
    verify(alertService)
        .openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.ApprovalNeeded), any(ApprovalNeededAlert.class));
    assertThat(executionResponse.isAsync()).isTrue();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);

    Mockito.verify(workflowNotificationHelper, Mockito.times(1))
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_PAUSE_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.USER_GROUP));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);

    verify(workflowNotificationHelper, times(1))
        .calculateServiceDetailsForAllServices(ACCOUNT_ID, APP_ID, context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateInfraDetails(ACCOUNT_ID, APP_ID, execution);
    verify(workflowNotificationHelper, times(1)).calculateApplicationDetails(ACCOUNT_ID, APP_ID, context.getApp());
    verify(workflowNotificationHelper, times(1)).getArtifactsDetails(context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateEnvDetails(ACCOUNT_ID, APP_ID, execution.getEnvironments());
    verify(workflowExecutionService, times(1))
        .fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID, WorkflowExecutionKeys.artifacts,
            WorkflowExecutionKeys.environments, WorkflowExecutionKeys.serviceIds,
            WorkflowExecutionKeys.infraDefinitionIds);
  }

  @Test
  @Owner(developers = DHRUV)
  @Category(UnitTests.class)
  public void shouldExecuteWorkflowWithTemplateExpressionWithValue() {
    PageResponse pageResponse = new PageResponse();
    pageResponse.setResponse(asList(User.Builder.anUser().build()));
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());

    List<TemplateExpression> templateExpressions = new ArrayList<>();
    TemplateExpression t = new TemplateExpression();
    t.setFieldName("userGroups");
    t.setExpression("${USER_GROUP}");
    templateExpressions.add(t);
    on(approvalState).set("templateExpressions", templateExpressions);
    UserGroup userGroup = new UserGroup();
    userGroup.setName("Account Administrator");
    userGroup.setUuid("dIyaCXXVRp65abGOlN5Fmg");

    when(templateExpressionProcessor.getTemplateExpression(any(), eq("userGroups"))).thenReturn(t);
    when(templateExpressionProcessor.resolveTemplateExpression(any(), any())).thenReturn("dIyaCXXVRp65abGOlN5Fmg");
    when(state.getTemplateExpressions()).thenReturn(templateExpressions);
    when(userGroupService.get(any(), eq("dIyaCXXVRp65abGOlN5Fmg"))).thenReturn(userGroup);

    when(context.getWorkflowType()).thenReturn(WorkflowType.ORCHESTRATION);
    ExecutionResponse executionResponse = approvalState.execute(context);
    verify(alertService)
        .openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.ApprovalNeeded), any(ApprovalNeededAlert.class));
    assertThat(executionResponse.isAsync()).isTrue();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);

    Mockito.verify(workflowNotificationHelper, Mockito.times(1))
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_PAUSE_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.USER_GROUP));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);
    verify(workflowNotificationHelper, times(1))
        .calculateServiceDetailsForAllServices(ACCOUNT_ID, APP_ID, context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateInfraDetails(ACCOUNT_ID, APP_ID, execution);
    verify(workflowNotificationHelper, times(1)).calculateApplicationDetails(ACCOUNT_ID, APP_ID, context.getApp());
    verify(workflowNotificationHelper, times(1)).getArtifactsDetails(context, execution, ExecutionScope.WORKFLOW, null);
    verify(workflowNotificationHelper, times(1)).calculateEnvDetails(ACCOUNT_ID, APP_ID, execution.getEnvironments());
    verify(workflowExecutionService, times(1))
        .fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID, WorkflowExecutionKeys.artifacts,
            WorkflowExecutionKeys.environments, WorkflowExecutionKeys.serviceIds,
            WorkflowExecutionKeys.infraDefinitionIds);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailProjectValidation() throws IOException {
    when(context.renderExpression("${project}")).thenReturn("UNKNOWN");
    when(jiraHelperService.getProjects(any(), any(), any())).thenReturn(projects);

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    JiraApprovalParams jiraApprovalParams = new JiraApprovalParams();
    jiraApprovalParams.setProject("${project}");

    approvalStateParams.setJiraApprovalParams(jiraApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData =
        ApprovalStateExecutionData.builder().approvalStateType(ApprovalStateType.JIRA).build();

    ExecutionResponse response = approvalState.executeJiraApproval(context, executionData, "id");

    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("Invalid project key [UNKNOWN]. Please, check out allowed values: [SPN, TPN, PN]");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailApprovalStatusValidation() throws IOException {
    when(context.renderExpression("${project}")).thenReturn("PN");
    when(context.renderExpression("${approvalValue}")).thenReturn("UNKNOWN");
    when(jiraHelperService.getProjects(any(), any(), any())).thenReturn(projects);
    when(jiraHelperService.getStatuses(any(), any(), any(), any())).thenReturn(statuses);

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    JiraApprovalParams jiraApprovalParams = new JiraApprovalParams();
    jiraApprovalParams.setProject("${project}");
    jiraApprovalParams.setApprovalValue("${approvalValue}");

    approvalStateParams.setJiraApprovalParams(jiraApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData =
        ApprovalStateExecutionData.builder().approvalStateType(ApprovalStateType.JIRA).build();

    ExecutionResponse response = approvalState.executeJiraApproval(context, executionData, "id");

    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("Invalid approval status [UNKNOWN]. Please, check out allowed values [To Do, In Progress, Done]");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailRejectionStatusValidation() throws IOException {
    when(context.renderExpression("${project}")).thenReturn("PN");
    when(context.renderExpression("${approvalValue}")).thenReturn("In Progress");
    when(context.renderExpression("${rejectionValue}")).thenReturn("UNKNOWN");
    when(jiraHelperService.getProjects(any(), any(), any())).thenReturn(projects);
    when(jiraHelperService.getStatuses(any(), any(), any(), any())).thenReturn(statuses);

    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    JiraApprovalParams jiraApprovalParams = new JiraApprovalParams();
    jiraApprovalParams.setProject("${project}");
    jiraApprovalParams.setApprovalValue("${approvalValue}");
    jiraApprovalParams.setRejectionValue("${rejectionValue}");

    approvalStateParams.setJiraApprovalParams(jiraApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ApprovalStateExecutionData executionData =
        ApprovalStateExecutionData.builder().approvalStateType(ApprovalStateType.JIRA).build();

    ExecutionResponse response = approvalState.executeJiraApproval(context, executionData, "id");

    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(response.getErrorMessage())
        .isEqualTo("Invalid rejection status [UNKNOWN]. Please, check out allowed values [To Do, In Progress, Done]");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldUpdatePlaceholderValuesForSlackApprovalNotification() {
    Map<String, String> placeholderValues = new HashMap<>();
    placeholderValues.put("START_TS_SECS", "1000");
    placeholderValues.put("END_TS_SECS", "1000");
    placeholderValues.put("EXPIRES_TS_SECS", "1000");
    placeholderValues.put("START_DATE", "StartDate");
    placeholderValues.put("END_DATE", "EndDate");
    placeholderValues.put("EXPIRES_DATE", "ExpiresDate");
    placeholderValues.put("DURATION", "5s");
    placeholderValues.put("VERB", "failed");
    placeholderValues.put("STATUS_CAMELCASE", "FAILED");
    placeholderValues.put("WORKFLOW_NAME", WORKFLOW_NAME);
    placeholderValues.put("WORKFLOW_URL", WORKFLOW_URL);
    placeholderValues.put("TIMEOUT", "1000");
    placeholderValues.put("APP_NAME", APP_NAME);
    placeholderValues.put("USER_NAME", USER_NAME);
    placeholderValues.put("STATUS", "statusMsg");
    placeholderValues.put("ENV", "env");
    placeholderValues.put("ARTIFACT", "artifactsMessage");
    when(context.getStateExecutionInstanceId()).thenReturn(STATE_EXECUTION_ID);
    SlackApprovalParams slackApprovalParams =
        SlackApprovalParams.builder()
            .appId(APP_ID)
            .appName("app")
            .nonFormattedAppName("nameW/Omrkdwn")
            .routingId(ACCOUNT_ID)
            .deploymentId(PIPELINE_WORKFLOW_EXECUTION_ID)
            .workflowId(WORKFLOW_ID)
            .workflowExecutionName(BUILD_JOB_NAME)
            .stateExecutionId(STATE_EXECUTION_ID)
            .stateExecutionInstanceName("Name")
            .approvalId(APPROVAL_EXECUTION_ID)
            .pausedStageName("Name")
            .servicesInvolved("*Services*: services")
            .environmentsInvolved("*Environments*: env")
            .artifactsInvolved("*Artifacts*: artifacts")
            .infraDefinitionsInvolved("*Infrastructure Definitions*: infra")
            .triggeredByUser("*Triggered By*: USER_NAME")
            .confirmation(false)
            .pipeline(false)
            .workflowUrl(WORKFLOW_URL)
            .jwtToken("token")
            .startTsSecs(placeholderValues.get(SlackApprovalMessageKeys.START_TS_SECS))
            .endTsSecs(placeholderValues.get(SlackApprovalMessageKeys.END_TS_SECS))
            .startDate(placeholderValues.get(SlackApprovalMessageKeys.START_DATE))
            .expiryTsSecs(placeholderValues.get(SlackApprovalMessageKeys.EXPIRES_TS_SECS))
            .endDate(placeholderValues.get(SlackApprovalMessageKeys.END_DATE))
            .expiryDate(placeholderValues.get(SlackApprovalMessageKeys.EXPIRES_DATE))
            .verb(placeholderValues.get(SlackApprovalMessageKeys.VERB))
            .build();
    Map<String, String> claims = new HashMap<>();
    claims.put("approvalId", APPROVAL_EXECUTION_ID);
    when(secretManager.generateJWTTokenWithCustomTimeOut(claims, "secret", DEFAULT_APPROVAL_STATE_TIMEOUT_MILLIS))
        .thenReturn("token");
    when(context.getWorkflowType()).thenReturn(WorkflowType.ORCHESTRATION);
    when(context.getStateExecutionInstanceName()).thenReturn("Name");
    when(workflowExecutionService.fetchWorkflowExecution(context.getAppId(), context.getWorkflowExecutionId(),
             WorkflowExecutionKeys.artifacts, WorkflowExecutionKeys.environments, WorkflowExecutionKeys.serviceIds,
             WorkflowExecutionKeys.infraDefinitionIds))
        .thenReturn(builder()
                        .artifacts(Collections.singletonList(anArtifact().build()))
                        .serviceIds(Collections.singletonList(SERVICE_ID))
                        .infraDefinitionIds(Collections.singletonList(INFRA_DEFINITION_ID))
                        .environments(Collections.singletonList(EnvSummary.builder().name("env").build()))
                        .build());

    JSONObject customData = new JSONObject(SlackApprovalParams.getExternalParams(slackApprovalParams));
    String buttonValue = StringEscapeUtils.escapeJson(customData.toString());
    String displayText = createSlackApprovalMessage(
        slackApprovalParams, ApprovalState.class.getResource("/slack/workflow-approval-message.txt"));
    approvalState.updatePlaceholderValuesForSlackApproval(
        APPROVAL_EXECUTION_ID, ACCOUNT_ID, placeholderValues, context);
    assertThat(placeholderValues.get(SlackApprovalMessageKeys.SLACK_APPROVAL_PARAMS)).isEqualTo(buttonValue);
    assertThat(placeholderValues.get(SlackApprovalMessageKeys.APPROVAL_MESSAGE)).isEqualTo(displayText);
    assertThat(placeholderValues.get(SlackApprovalMessageKeys.MESSAGE_IDENTIFIER))
        .isEqualTo("suppressTraditionalNotificationOnSlack");
    assertThat(customData.get("nonFormattedAppName")).isEqualTo("nameW/Omrkdwn");
    assertPlaceholdersAddedForEmailNotification(placeholderValues);
  }

  private CharSequence[] createCharSequence(CharSequence value, int length) {
    final CharSequence[] result = new CharSequence[length];
    for (int i = 0; i < length; i++) {
      result[i] = value;
    }
    return result;
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateMessageLengthWhenServiceDetailsNull() {
    String excessiveInfo = "first,second,third," + String.join(",", createCharSequence("lotsOfLongExcessiveInfo", 100));
    WorkflowNotificationDetails infraDetails = WorkflowNotificationDetails.builder().name(excessiveInfo).build();
    StringBuilder artifacts = new StringBuilder(String.format("*Artifacts*: %s", excessiveInfo.replace(",", ", ")));
    StringBuilder environments =
        new StringBuilder(String.format("*Environments*: %s", excessiveInfo.replace(",", ", ")));
    SlackApprovalParams slackApprovalParams =
        SlackApprovalParams.builder()
            .appId(APP_ID)
            .appName(APP_NAME)
            .routingId(ACCOUNT_ID)
            .deploymentId(WORKFLOW_EXECUTION_ID)
            .workflowId(WORKFLOW_ID)
            .workflowExecutionName(WORKFLOW_NAME)
            .stateExecutionId(STATE_EXECUTION_ID)
            .stateExecutionInstanceName(STATE_NAME)
            .approvalId(APPROVAL_EXECUTION_ID)
            .pausedStageName(WORKFLOW_NAME)
            .servicesInvolved("#Services#Involved#")
            .environmentsInvolved(environments.toString())
            .artifactsInvolved(artifacts.toString())
            .infraDefinitionsInvolved(String.format("*Infrastructure Definitions*: %s", infraDetails.getName()))
            .confirmation(false)
            .pipeline(true)
            .workflowUrl(WORKFLOW_URL)
            .jwtToken("token")
            .startTsSecs("mockEpochTimeMillis")
            .endTsSecs("mockEpochTimeMillis")
            .startDate("mockEpochTimeMillis")
            .expiryTsSecs("mockEpochTimeMillis")
            .endDate("mockEpochTimeMillis")
            .expiryDate("someMockDate")
            .verb("paused")
            .build();

    String displayText = createSlackApprovalMessage(slackApprovalParams,
        ApprovalState.class.getResource(SlackApprovalMessageKeys.PIPELINE_APPROVAL_MESSAGE_TEMPLATE));

    String trimmedMessage = approvalState.validateMessageLength(displayText, slackApprovalParams,
        ApprovalState.class.getResource(SlackApprovalMessageKeys.PIPELINE_APPROVAL_MESSAGE_TEMPLATE), null, artifacts,
        infraDetails, environments);
    assertThat(trimmedMessage.length()).isLessThanOrEqualTo(2000);
    assertThat(trimmedMessage).contains(slackApprovalParams.getServicesInvolved());
    assertThat(trimmedMessage).contains("*Artifacts*: first, second, third... 100 more");
    assertThat(trimmedMessage).contains("*Environments*: first, second, third... 100 more");
    assertThat(trimmedMessage).doesNotContain(slackApprovalParams.getArtifactsInvolved());
    assertThat(trimmedMessage).contains("*Infrastructure Definitions*: first, second, third... 100 more");
    assertThat(trimmedMessage).doesNotContain(slackApprovalParams.getInfraDefinitionsInvolved());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldValidateMessageLengthWhenEnvironmentsIsNull() {
    String excessiveInfo = "first,second,third," + String.join(",", createCharSequence("lotsOfLongExcessiveInfo", 100));
    WorkflowNotificationDetails serviceDetails = WorkflowNotificationDetails.builder().name(excessiveInfo).build();
    WorkflowNotificationDetails infraDetails = WorkflowNotificationDetails.builder().name(excessiveInfo).build();
    SlackApprovalParams slackApprovalParams =
        SlackApprovalParams.builder()
            .appId(APP_ID)
            .appName(APP_NAME)
            .routingId(ACCOUNT_ID)
            .deploymentId(WORKFLOW_EXECUTION_ID)
            .workflowId(WORKFLOW_ID)
            .workflowExecutionName(WORKFLOW_NAME)
            .stateExecutionId(STATE_EXECUTION_ID)
            .stateExecutionInstanceName(STATE_NAME)
            .approvalId(APPROVAL_EXECUTION_ID)
            .pausedStageName(WORKFLOW_NAME)
            .servicesInvolved(String.format("*Services*: %s", serviceDetails.getName()))
            .environmentsInvolved(ENV_NAME)
            .artifactsInvolved("#Artifacts#Involved#")
            .infraDefinitionsInvolved(String.format("*Infrastructure Definitions*: %s", infraDetails.getName()))
            .confirmation(false)
            .pipeline(true)
            .workflowUrl(WORKFLOW_URL)
            .jwtToken("token")
            .startTsSecs("mockEpochTimeMillis")
            .endTsSecs("mockEpochTimeMillis")
            .startDate("mockEpochTimeMillis")
            .expiryTsSecs("mockEpochTimeMillis")
            .endDate("mockEpochTimeMillis")
            .expiryDate("someMockDate")
            .verb("paused")
            .build();

    String displayText = createSlackApprovalMessage(slackApprovalParams,
        ApprovalState.class.getResource(SlackApprovalMessageKeys.PIPELINE_APPROVAL_MESSAGE_TEMPLATE));

    String trimmedMessage = approvalState.validateMessageLength(displayText, slackApprovalParams,
        ApprovalState.class.getResource(SlackApprovalMessageKeys.PIPELINE_APPROVAL_MESSAGE_TEMPLATE), serviceDetails,
        null, infraDetails, null);
    assertThat(trimmedMessage.length()).isLessThanOrEqualTo(2000);
    assertThat(trimmedMessage).contains("*Services*: first, second, third... 100 more");
    assertThat(trimmedMessage).doesNotContain(slackApprovalParams.getServicesInvolved());
    assertThat(trimmedMessage).contains(slackApprovalParams.getArtifactsInvolved());
    assertThat(trimmedMessage).contains("*Infrastructure Definitions*: first, second, third... 100 more");
    assertThat(trimmedMessage).doesNotContain(slackApprovalParams.getInfraDefinitionsInvolved());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateMessageLengthWhenArtifactsIsNull() {
    String excessiveInfo = "first,second,third," + String.join(",", createCharSequence("lotsOfLongExcessiveInfo", 100));
    WorkflowNotificationDetails serviceDetails = WorkflowNotificationDetails.builder().name(excessiveInfo).build();
    WorkflowNotificationDetails infraDetails = WorkflowNotificationDetails.builder().name(excessiveInfo).build();
    SlackApprovalParams slackApprovalParams =
        SlackApprovalParams.builder()
            .appId(APP_ID)
            .appName(APP_NAME)
            .routingId(ACCOUNT_ID)
            .deploymentId(WORKFLOW_EXECUTION_ID)
            .workflowId(WORKFLOW_ID)
            .workflowExecutionName(WORKFLOW_NAME)
            .stateExecutionId(STATE_EXECUTION_ID)
            .stateExecutionInstanceName(STATE_NAME)
            .approvalId(APPROVAL_EXECUTION_ID)
            .pausedStageName(WORKFLOW_NAME)
            .servicesInvolved(String.format("*Services*: %s", serviceDetails.getName()))
            .environmentsInvolved(ENV_NAME)
            .artifactsInvolved("#Artifacts#Involved#")
            .infraDefinitionsInvolved(String.format("*Infrastructure Definitions*: %s", infraDetails.getName()))
            .confirmation(false)
            .pipeline(true)
            .workflowUrl(WORKFLOW_URL)
            .jwtToken("token")
            .startTsSecs("mockEpochTimeMillis")
            .endTsSecs("mockEpochTimeMillis")
            .startDate("mockEpochTimeMillis")
            .expiryTsSecs("mockEpochTimeMillis")
            .endDate("mockEpochTimeMillis")
            .expiryDate("someMockDate")
            .verb("paused")
            .build();

    String displayText = createSlackApprovalMessage(slackApprovalParams,
        ApprovalState.class.getResource(SlackApprovalMessageKeys.PIPELINE_APPROVAL_MESSAGE_TEMPLATE));

    String trimmedMessage = approvalState.validateMessageLength(displayText, slackApprovalParams,
        ApprovalState.class.getResource(SlackApprovalMessageKeys.PIPELINE_APPROVAL_MESSAGE_TEMPLATE), serviceDetails,
        null, infraDetails, null);
    assertThat(trimmedMessage.length()).isLessThanOrEqualTo(2000);
    assertThat(trimmedMessage).contains("*Services*: first, second, third... 100 more");
    assertThat(trimmedMessage).doesNotContain(slackApprovalParams.getServicesInvolved());
    assertThat(trimmedMessage).contains(slackApprovalParams.getArtifactsInvolved());
    assertThat(trimmedMessage).contains("*Infrastructure Definitions*: first, second, third... 100 more");
    assertThat(trimmedMessage).doesNotContain(slackApprovalParams.getInfraDefinitionsInvolved());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldValidateMessageLengthWhenInfraDetailsIsNull() {
    String excessiveInfo = "first,second,third," + String.join(",", createCharSequence("lotsOfLongExcessiveInfo", 100));
    WorkflowNotificationDetails serviceDetails = WorkflowNotificationDetails.builder().name(excessiveInfo).build();
    StringBuilder artifacts = new StringBuilder(String.format("*Artifacts*: %s", excessiveInfo.replace(",", ", ")));
    StringBuilder environments =
        new StringBuilder(String.format("*Environments*: %s", excessiveInfo.replace(",", ", ")));
    SlackApprovalParams slackApprovalParams =
        SlackApprovalParams.builder()
            .appId(APP_ID)
            .appName(APP_NAME)
            .routingId(ACCOUNT_ID)
            .deploymentId(WORKFLOW_EXECUTION_ID)
            .workflowId(WORKFLOW_ID)
            .workflowExecutionName(WORKFLOW_NAME)
            .stateExecutionId(STATE_EXECUTION_ID)
            .stateExecutionInstanceName(STATE_NAME)
            .approvalId(APPROVAL_EXECUTION_ID)
            .pausedStageName(WORKFLOW_NAME)
            .servicesInvolved(String.format("*Services*: %s", serviceDetails.getName()))
            .environmentsInvolved(ENV_NAME)
            .artifactsInvolved("#Artifacts#Involved#")
            .infraDefinitionsInvolved("#Infra#Definitions#Involved")
            .confirmation(false)
            .pipeline(true)
            .workflowUrl(WORKFLOW_URL)
            .jwtToken("token")
            .startTsSecs("mockEpochTimeMillis")
            .endTsSecs("mockEpochTimeMillis")
            .startDate("mockEpochTimeMillis")
            .expiryTsSecs("mockEpochTimeMillis")
            .endDate("mockEpochTimeMillis")
            .expiryDate("someMockDate")
            .verb("paused")
            .build();

    String displayText = createSlackApprovalMessage(slackApprovalParams,
        ApprovalState.class.getResource(SlackApprovalMessageKeys.PIPELINE_APPROVAL_MESSAGE_TEMPLATE));

    String trimmedMessage = approvalState.validateMessageLength(displayText, slackApprovalParams,
        ApprovalState.class.getResource(SlackApprovalMessageKeys.PIPELINE_APPROVAL_MESSAGE_TEMPLATE), serviceDetails,
        artifacts, null, environments);
    assertThat(trimmedMessage.length()).isLessThanOrEqualTo(2000);
    assertThat(trimmedMessage).contains("*Services*: first, second, third... 100 more");
    assertThat(trimmedMessage).doesNotContain(slackApprovalParams.getServicesInvolved());
    assertThat(trimmedMessage).contains("*Artifacts*: first, second, third... 100 more");
    assertThat(trimmedMessage).contains("*Environments*: first, second, third... 100 more");
    assertThat(trimmedMessage).doesNotContain(slackApprovalParams.getArtifactsInvolved());
    assertThat(trimmedMessage).contains(slackApprovalParams.getInfraDefinitionsInvolved());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldTrimExcessiveServicesAndArtifactsDetails() {
    String excessiveInfo =
        "first,second,third,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo,lotsOfLongExcessiveInfo";
    WorkflowNotificationDetails serviceDetails = WorkflowNotificationDetails.builder().name(excessiveInfo).build();
    WorkflowNotificationDetails infraDetails = WorkflowNotificationDetails.builder().name(excessiveInfo).build();
    StringBuilder artifacts = new StringBuilder(String.format("*Artifacts*: %s", excessiveInfo.replace(",", ", ")));
    SlackApprovalParams slackApprovalParams =
        SlackApprovalParams.builder()
            .appId(APP_ID)
            .appName(APP_NAME)
            .routingId(ACCOUNT_ID)
            .deploymentId(WORKFLOW_EXECUTION_ID)
            .workflowId(WORKFLOW_ID)
            .workflowExecutionName(WORKFLOW_NAME)
            .stateExecutionId(STATE_EXECUTION_ID)
            .stateExecutionInstanceName(STATE_NAME)
            .approvalId(APPROVAL_EXECUTION_ID)
            .pausedStageName(WORKFLOW_NAME)
            .servicesInvolved(String.format("*Services*: %s", serviceDetails.getName()))
            .environmentsInvolved(ENV_NAME)
            .artifactsInvolved(artifacts.toString())
            .infraDefinitionsInvolved(String.format("*Infrastructure Definitions*: %s", infraDetails.getName()))
            .confirmation(false)
            .pipeline(true)
            .workflowUrl(WORKFLOW_URL)
            .jwtToken("token")
            .startTsSecs("mockEpochTimeMillis")
            .endTsSecs("mockEpochTimeMillis")
            .startDate("mockEpochTimeMillis")
            .expiryTsSecs("mockEpochTimeMillis")
            .endDate("mockEpochTimeMillis")
            .expiryDate("someMockDate")
            .verb("paused")
            .build();
    String displayText = createSlackApprovalMessage(slackApprovalParams,
        ApprovalState.class.getResource(SlackApprovalMessageKeys.PIPELINE_APPROVAL_MESSAGE_TEMPLATE));

    String trimmedMessage = approvalState.validateMessageLength(displayText, slackApprovalParams,
        ApprovalState.class.getResource(SlackApprovalMessageKeys.PIPELINE_APPROVAL_MESSAGE_TEMPLATE), serviceDetails,
        artifacts, infraDetails, null);
    assertThat(trimmedMessage.length()).isLessThanOrEqualTo(2000);
    assertThat(trimmedMessage).contains("*Services*: first, second, third... 25 more");
    assertThat(trimmedMessage).doesNotContain(slackApprovalParams.getServicesInvolved());
    assertThat(trimmedMessage).contains("*Artifacts*: first, second, third... 25 more");
    assertThat(trimmedMessage).doesNotContain(slackApprovalParams.getArtifactsInvolved());
    assertThat(trimmedMessage).contains("*Infrastructure Definitions*: first, second, third... 25 more");
    assertThat(trimmedMessage).doesNotContain(slackApprovalParams.getInfraDefinitionsInvolved());
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldExecuteShellScriptApproval() {
    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    ShellScriptApprovalParams shellScriptApprovalParams = new ShellScriptApprovalParams();
    shellScriptApprovalParams.setScriptString("script");
    shellScriptApprovalParams.setRetryInterval(30000);
    approvalStateParams.setShellScriptApprovalParams(shellScriptApprovalParams);
    approvalState.setApprovalStateType(ApprovalStateType.SHELL_SCRIPT);
    approvalState.setApprovalStateParams(approvalStateParams);
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());

    when(context.getWorkflowType()).thenReturn(WorkflowType.ORCHESTRATION);
    ExecutionResponse executionResponse = approvalState.execute(context);
    verify(workflowNotificationHelper)
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_PAUSE_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.SHELL_SCRIPT));
    verify(alertService)
        .openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.ApprovalNeeded), any(ApprovalNeededAlert.class));
    verify(approvalPolingService).save(any());
    assertThat(executionResponse.isAsync()).isTrue();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(PAUSED);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFailApprovalWhenExceptionThrownOnSavingPollingJob() {
    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    ShellScriptApprovalParams shellScriptApprovalParams = new ShellScriptApprovalParams();
    shellScriptApprovalParams.setScriptString("script");
    shellScriptApprovalParams.setRetryInterval(30000);
    approvalStateParams.setShellScriptApprovalParams(shellScriptApprovalParams);
    approvalState.setApprovalStateType(ApprovalStateType.SHELL_SCRIPT);
    approvalState.setApprovalStateParams(approvalStateParams);
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());
    when(context.getWorkflowType()).thenReturn(WorkflowType.ORCHESTRATION);
    when(approvalPolingService.save(any())).thenThrow(new InvalidArgumentsException(""));

    ExecutionResponse executionResponse = approvalState.execute(context);

    verify(workflowNotificationHelper)
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_PAUSE_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.SHELL_SCRIPT));
    verify(alertService)
        .openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.ApprovalNeeded), any(ApprovalNeededAlert.class));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(executionResponse.getErrorMessage()).isEqualTo("Failed to schedule Approval");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponseForJiraApproval() {
    approvalState.setApprovalStateType(ApprovalStateType.JIRA);
    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    JiraApprovalParams jiraApprovalParams = new JiraApprovalParams();
    jiraApprovalParams.setIssueId(JIRA_ISSUE_ID);
    approvalStateParams.setJiraApprovalParams(jiraApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ResponseData responseData = ApprovalStateExecutionData.builder()
                                    .approvedBy(EmbeddedUser.builder().name(USER_NAME).build())
                                    .approvalId(UUID)
                                    .build();
    ((StateExecutionData) responseData).setStatus(SUCCESS);
    Map<String, ResponseData> response = Collections.singletonMap("key", responseData);
    ApprovalStateExecutionData approvalStateExecutionData = ApprovalStateExecutionData.builder().build();
    approvalStateExecutionData.setStartTs(123456L);
    approvalStateExecutionData.setApprovalStateType(ApprovalStateType.JIRA);

    when(context.getWorkflowType()).thenReturn(WorkflowType.ORCHESTRATION);
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());
    when(context.getStateExecutionData()).thenReturn(approvalStateExecutionData);
    when(workflowExecutionService.fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID,
             WorkflowExecutionKeys.createdAt, WorkflowExecutionKeys.triggeredBy, WorkflowExecutionKeys.status))
        .thenReturn(builder()
                        .status(ExecutionStatus.NEW)
                        .appId(APP_ID)
                        .triggeredBy(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).build())
                        .createdAt(70L)
                        .build());

    ExecutionResponse executionResponse = approvalState.handleAsyncResponse(context, response);
    verify(alertService).closeAlert(any(), any(), any(), any());
    verify(workflowNotificationHelper)
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_RESUME_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.JIRA));
    verify(approvalPolingService).delete(any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponseForSnowApproval() {
    approvalState.setApprovalStateType(ApprovalStateType.SERVICENOW);
    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    ServiceNowApprovalParams serviceNowApprovalParams = new ServiceNowApprovalParams();
    serviceNowApprovalParams.setIssueNumber(UUID);
    approvalStateParams.setServiceNowApprovalParams(serviceNowApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ResponseData responseData = ApprovalStateExecutionData.builder()
                                    .approvedBy(EmbeddedUser.builder().name(USER_NAME).build())
                                    .approvalId(UUID)
                                    .build();
    ((StateExecutionData) responseData).setStatus(SUCCESS);
    Map<String, ResponseData> response = Collections.singletonMap("key", responseData);
    ApprovalStateExecutionData approvalStateExecutionData = ApprovalStateExecutionData.builder().build();
    approvalStateExecutionData.setStartTs(123456L);
    approvalStateExecutionData.setApprovalStateType(ApprovalStateType.SERVICENOW);

    when(context.getWorkflowType()).thenReturn(WorkflowType.ORCHESTRATION);
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());
    when(context.getStateExecutionData()).thenReturn(approvalStateExecutionData);
    when(workflowExecutionService.fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID,
             WorkflowExecutionKeys.createdAt, WorkflowExecutionKeys.triggeredBy, WorkflowExecutionKeys.status))
        .thenReturn(builder()
                        .status(ExecutionStatus.NEW)
                        .appId(APP_ID)
                        .triggeredBy(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).build())
                        .createdAt(70L)
                        .build());

    ExecutionResponse executionResponse = approvalState.handleAsyncResponse(context, response);
    verify(alertService).closeAlert(any(), any(), any(), any());
    verify(workflowNotificationHelper)
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_RESUME_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.SERVICENOW));
    verify(approvalPolingService).delete(any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponseShellScriptApproval() {
    approvalState.setApprovalStateType(ApprovalStateType.SHELL_SCRIPT);
    ApprovalStateParams approvalStateParams = new ApprovalStateParams();
    ShellScriptApprovalParams shellScriptApprovalParams = new ShellScriptApprovalParams();
    shellScriptApprovalParams.setScriptString("script");
    shellScriptApprovalParams.setRetryInterval(30000);
    approvalStateParams.setShellScriptApprovalParams(shellScriptApprovalParams);
    approvalState.setApprovalStateParams(approvalStateParams);
    ResponseData responseData = ApprovalStateExecutionData.builder()
                                    .approvedBy(EmbeddedUser.builder().name(USER_NAME).build())
                                    .approvalId(UUID)
                                    .build();
    ((StateExecutionData) responseData).setStatus(SUCCESS);
    Map<String, ResponseData> response = Collections.singletonMap("key", responseData);
    ApprovalStateExecutionData approvalStateExecutionData = ApprovalStateExecutionData.builder().build();
    approvalStateExecutionData.setStartTs(123456L);
    approvalStateExecutionData.setApprovalStateType(ApprovalStateType.SHELL_SCRIPT);

    when(context.getWorkflowType()).thenReturn(WorkflowType.ORCHESTRATION);
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());
    when(context.getStateExecutionData()).thenReturn(approvalStateExecutionData);
    when(workflowExecutionService.fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID,
             WorkflowExecutionKeys.createdAt, WorkflowExecutionKeys.triggeredBy, WorkflowExecutionKeys.status))
        .thenReturn(builder()
                        .status(ExecutionStatus.NEW)
                        .appId(APP_ID)
                        .triggeredBy(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).build())
                        .createdAt(70L)
                        .build());

    ExecutionResponse executionResponse = approvalState.handleAsyncResponse(context, response);
    verify(alertService).closeAlert(any(), any(), any(), any());
    verify(workflowNotificationHelper)
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_RESUME_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.SHELL_SCRIPT));
    verify(approvalPolingService).delete(any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldHandleAsyncResponseUserGroupApproval() {
    approvalState.setApprovalStateType(ApprovalStateType.USER_GROUP);
    ResponseData responseData = ApprovalStateExecutionData.builder()
                                    .approvedBy(EmbeddedUser.builder().name(USER_NAME).build())
                                    .approvalId(UUID)
                                    .build();
    ((StateExecutionData) responseData).setStatus(SUCCESS);
    Map<String, ResponseData> response = Collections.singletonMap("key", responseData);
    ApprovalStateExecutionData approvalStateExecutionData = ApprovalStateExecutionData.builder().build();
    approvalStateExecutionData.setStartTs(123456L);
    approvalStateExecutionData.setApprovalStateType(ApprovalStateType.USER_GROUP);

    when(context.getWorkflowType()).thenReturn(WorkflowType.ORCHESTRATION);
    when(context.getStateExecutionInstance())
        .thenReturn(aStateExecutionInstance().executionType(WorkflowType.ORCHESTRATION).build());
    when(context.getStateExecutionData()).thenReturn(approvalStateExecutionData);
    when(workflowExecutionService.fetchWorkflowExecution(APP_ID, PIPELINE_WORKFLOW_EXECUTION_ID,
             WorkflowExecutionKeys.createdAt, WorkflowExecutionKeys.triggeredBy, WorkflowExecutionKeys.status))
        .thenReturn(builder()
                        .status(ExecutionStatus.NEW)
                        .appId(APP_ID)
                        .triggeredBy(EmbeddedUser.builder().name(USER_NAME).uuid(USER_NAME).build())
                        .createdAt(70L)
                        .build());

    ExecutionResponse executionResponse = approvalState.handleAsyncResponse(context, response);
    verify(alertService).closeAlert(any(), any(), any(), any());
    verify(workflowNotificationHelper)
        .sendApprovalNotification(Mockito.eq(ACCOUNT_ID), Mockito.eq(WORKFLOW_RESUME_NOTIFICATION), Mockito.anyMap(),
            Mockito.any(), Mockito.eq(ApprovalStateType.USER_GROUP));
    verify(notificationService).sendNotificationAsync(any(), any());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  private void assertPlaceholdersAddedForEmailNotification(Map<String, String> placeholderValues) {
    assertThat(placeholderValues.containsKey("APPROVAL_STEP"));
    assertThat(placeholderValues.containsKey("WORKFLOW"));
    assertThat(placeholderValues.containsKey("APP"));
    assertThat(placeholderValues.containsKey("SERVICE_NAMES"));
    assertThat(placeholderValues.containsKey("ARTIFACTS"));
    assertThat(placeholderValues.containsKey("ENV"));
    assertThat(placeholderValues.containsKey("INFRA_NAMES"));
  }
  private void verifyJiraSweepingOutput() {
    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalStateType(ApprovalStateType.JIRA)
                                                   .approvedBy(EmbeddedUser.builder().name(USER_NAME).build())
                                                   .build();
    final List<String> keys = GetSweepingOutputKeys(captor, executionData);
    assertJiraKeysInSweepingOutput(keys);
  }

  private void verifyUserGroupSweepingOutput() {
    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalStateType(ApprovalStateType.USER_GROUP)
                                                   .approvedBy(EmbeddedUser.builder().name(USER_NAME).build())
                                                   .build();
    final List<String> keys = GetSweepingOutputKeys(captor, executionData);
    assertUserGroupKeysInSweepingOutput(keys);
  }

  private void verifySnowSweepingOutput() {
    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalStateType(ApprovalStateType.SERVICENOW)
                                                   .approvedBy(EmbeddedUser.builder().name(USER_NAME).build())
                                                   .build();
    final List<String> keys = GetSweepingOutputKeys(captor, executionData);
    assertSnowKeysInSweepingOutput(keys);
  }

  private List<String> GetSweepingOutputKeys(
      ArgumentCaptor<SweepingOutputInstance> captor, ApprovalStateExecutionData executionData) {
    approvalState.fillSweepingOutput(context, executionData, null);

    verify(sweepingOutputService, times(1)).save(captor.capture());

    SweepingOutputInstance sweepingOutput = captor.getValue();
    Map<String, Object> data = (Map<String, Object>) kryoSerializer.asInflatedObject(sweepingOutput.getOutput());

    reset(sweepingOutputService);
    return data.keySet().stream().collect(Collectors.toList());
  }

  private void assertUserGroupKeysInSweepingOutput(List<String> keys) {
    assertThat(keys).isNotNull().containsExactlyInAnyOrder(ApprovalState.APPROVAL_STATUS_KEY,
        ApprovalStateExecutionDataKeys.variables, ApprovalStateExecutionDataKeys.comments, "test",
        ApprovalStateExecutionDataKeys.approvedOn, ApprovalStateExecutionDataKeys.approvalFromSlack,
        ApprovalStateExecutionDataKeys.timeoutMillis, ApprovalStateExecutionDataKeys.approvalStateType,
        ApprovalStateExecutionDataKeys.approvedBy, ApprovalStateExecutionDataKeys.approvalFromGraphQL);
  }

  private void assertJiraKeysInSweepingOutput(List<String> keys) {
    assertThat(keys).isNotNull().containsExactlyInAnyOrder(ApprovalState.APPROVAL_STATUS_KEY,
        ApprovalStateExecutionDataKeys.variables, "test", ApprovalStateExecutionDataKeys.issueKey,
        ApprovalStateExecutionDataKeys.issueUrl, ApprovalStateExecutionDataKeys.currentStatus,
        ApprovalStateExecutionDataKeys.approvalField, ApprovalStateExecutionDataKeys.approvalValue,
        ApprovalStateExecutionDataKeys.rejectionField, ApprovalStateExecutionDataKeys.rejectionValue,
        ApprovalStateExecutionDataKeys.approvalFromSlack, ApprovalStateExecutionDataKeys.timeoutMillis,
        ApprovalStateExecutionDataKeys.approvalStateType);
  }

  private void assertSnowKeysInSweepingOutput(List<String> keys) {
    assertThat(keys).isNotNull().containsExactlyInAnyOrder(ApprovalState.APPROVAL_STATUS_KEY,
        ApprovalStateExecutionDataKeys.variables, "test", ApprovalStateExecutionDataKeys.ticketType,
        ApprovalStateExecutionDataKeys.ticketUrl, ApprovalStateExecutionDataKeys.approvalFromSlack,
        ApprovalStateExecutionDataKeys.timeoutMillis, ApprovalStateExecutionDataKeys.approvalStateType);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void mapDelegateSelectorsCustomShellScriptApproval() throws Exception {
    // build linked hash map
    Map<String, Object> map = Maps.newLinkedHashMap();
    Map<String, Object> approvalStateParams = Maps.newLinkedHashMap();
    Map<String, Object> shellScriptParams = Maps.newLinkedHashMap();

    shellScriptParams.put("scriptString", "echo 451");
    shellScriptParams.put("retryInterval", 30000);
    shellScriptParams.put("delegateSelectors", Arrays.asList("delegate1234"));

    approvalStateParams.put("shellScriptApprovalParams", shellScriptParams);

    map.put("templateUuid", null);
    map.put("approvalStateType", "SHELL_SCRIPT");
    map.put("approvalStateParams", approvalStateParams);
    map.put("templateVersion", null);
    map.put("timeoutMillis", 86400);
    map.put("parentId", "PARENT_ID");
    map.put("templateVariables", null);

    ApprovalState approvalState = new ApprovalState("approvalState");

    approvalState.mapApprovalObject(map, approvalState);
    assertThat(approvalState.getApprovalStateType()).isEqualTo(ApprovalStateType.SHELL_SCRIPT);
    assertThat(approvalState.approvalStateParams.getShellScriptApprovalParams().fetchDelegateSelectors())
        .contains("delegate1234");
    LinkedHashMap<String, Object> shellScriptApprovalParams =
        (LinkedHashMap<String, Object>) ((LinkedHashMap<String, Object>) map.get(ApprovalStateKeys.approvalStateParams))
            .get(ApprovalStateParamsKeys.shellScriptApprovalParams);
    // repopulated delegate selector in map
    assertThat(shellScriptApprovalParams.containsKey(ShellScriptApprovalParamsKeys.delegateSelectors));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void mapWithoutDelegateSelectorsCustomShellScriptApproval() throws Exception {
    // build linked hash map
    Map<String, Object> map = Maps.newLinkedHashMap();
    Map<String, Object> approvalStateParams = Maps.newLinkedHashMap();
    Map<String, Object> shellScriptParams = Maps.newLinkedHashMap();

    shellScriptParams.put("scriptString", "echo 451");
    shellScriptParams.put("retryInterval", 30000);

    approvalStateParams.put("shellScriptApprovalParams", shellScriptParams);

    map.put("approvalStateType", "SHELL_SCRIPT");
    map.put("approvalStateParams", approvalStateParams);
    map.put("timeoutMillis", 86400);
    map.put("parentId", "PARENT_ID");

    ApprovalState approvalState = new ApprovalState("approvalState");

    approvalState.mapApprovalObject(map, approvalState);
    assertThat(approvalState.approvalStateParams.getShellScriptApprovalParams().fetchDelegateSelectors()).isNull();
    assertThat(approvalState.getApprovalStateType()).isEqualTo(ApprovalStateType.SHELL_SCRIPT);
    LinkedHashMap<String, Object> shellScriptApprovalParams =
        (LinkedHashMap<String, Object>) ((LinkedHashMap<String, Object>) map.get(ApprovalStateKeys.approvalStateParams))
            .get(ApprovalStateParamsKeys.shellScriptApprovalParams);
    // no delegate selector in map
    assertThat(!shellScriptApprovalParams.containsKey(ShellScriptApprovalParamsKeys.delegateSelectors));
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void mapPropertiesServiceNowApproval() throws Exception {
    // build linked hash map
    Map<String, Object> map = Maps.newLinkedHashMap();
    Map<String, Object> approvalStateParams = Maps.newLinkedHashMap();
    Map<String, Object> serviceNowApprovalParams = Maps.newLinkedHashMap();

    serviceNowApprovalParams.put("issueNumber", "issueNumber");
    serviceNowApprovalParams.put("changeWindowEndField", "end");
    serviceNowApprovalParams.put("changeWindowStartField", "start");
    serviceNowApprovalParams.put("changeWindowPresent", "true");

    Map<String, Object> approvalCriteria = Maps.newLinkedHashMap();
    approvalCriteria.put("operator", "AND");
    approvalCriteria.put("conditions",
        ImmutableMap.of(SERVICENOW_STATE, asList("Closed", "Cancelled"), "approval", asList("Approved", "Requested")));

    serviceNowApprovalParams.put("approval", approvalCriteria);
    approvalStateParams.put("serviceNowApprovalParams", serviceNowApprovalParams);

    map.put("approvalStateType", "SERVICENOW");
    map.put("approvalStateParams", approvalStateParams);
    map.put("timeoutMillis", 86400);
    map.put("parentId", "PARENT_ID");

    ApprovalState approvalState = new ApprovalState("approvalState");

    approvalState.mapApprovalObject(map, approvalState);
    assertThat(approvalState.approvalStateParams.getServiceNowApprovalParams().getChangeWindowEndField())
        .contains("end");
    assertThat(approvalState.approvalStateParams.getServiceNowApprovalParams().getApproval().fetchConditions())
        .containsKeys("approval", "state");
    assertThat(approvalState.getApprovalStateType()).isEqualTo(ApprovalStateType.SERVICENOW);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldResolveUserGroupExpression() {
    String expression = "${userGroupExpression}";
    approvalState.setUserGroupAsExpression(true);
    approvalState.setUserGroupExpression(expression);

    when(context.renderExpression(expression)).thenReturn("group1, group2");
    when(context.getAccountId()).thenReturn(ACCOUNT_ID);
    when(userGroupService.get(ACCOUNT_ID, "group1")).thenReturn(UserGroup.builder().uuid("A1").build());
    when(userGroupService.get(ACCOUNT_ID, "group2")).thenReturn(UserGroup.builder().uuid("B2").build());

    approvalState.resolveUserGroupFromExpression(context);

    assertThat(approvalState.getUserGroups()).containsOnly("A1", "B2");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestWhenUserGroupExpressionNull() {
    assertThatThrownBy(() -> {
      approvalState.setUserGroupExpression(null);
      approvalState.resolveUserGroupFromExpression(context);
    })
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User group expression is set but value is not provided")
        .hasFieldOrPropertyWithValue("reportTargets", USER);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestWhenUserGroupExpressionEmpty() {
    assertThatThrownBy(() -> {
      approvalState.setUserGroupExpression("");
      approvalState.resolveUserGroupFromExpression(context);
    })
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User group expression is set but value is not provided")
        .hasFieldOrPropertyWithValue("reportTargets", USER);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldThrowInvalidRequestWhenRenderedExpressionEmpty() {
    String expression = "${userGroupExpression}";
    approvalState.setUserGroupAsExpression(true);
    approvalState.setUserGroupExpression(expression);

    assertThatThrownBy(() -> {
      when(context.renderExpression(expression)).thenReturn("");
      approvalState.resolveUserGroupFromExpression(context);
    })
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("User group expression is invalid")
        .hasFieldOrPropertyWithValue("reportTargets", USER);
  }
}
