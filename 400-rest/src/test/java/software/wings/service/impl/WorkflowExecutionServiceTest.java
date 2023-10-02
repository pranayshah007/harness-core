/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.PAUSED;
import static io.harness.beans.ExecutionStatus.REJECTED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.FeatureName.AUTO_REJECT_PREVIOUS_APPROVALS;
import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.GE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.POOJA;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.RAFAEL;
import static io.harness.rule.OwnerRule.RAMA;
import static io.harness.rule.OwnerRule.SRINIVAS;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.api.EnvStateExecutionData.Builder.anEnvStateExecutionData;
import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PipelineExecution.Builder.aPipelineExecution;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.beans.Variable.VariableBuilder.aVariable;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import static software.wings.beans.WorkflowExecution.builder;
import static software.wings.beans.deployment.DeploymentMetadata.Include.ARTIFACT_SERVICE;
import static software.wings.beans.deployment.DeploymentMetadata.Include.DEPLOYMENT_TYPE;
import static software.wings.beans.deployment.DeploymentMetadata.Include.ENVIRONMENT;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructCanaryWorkflowWithPhase;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateMachine.StateMachineBuilder.aStateMachine;
import static software.wings.sm.StateType.ARTIFACT_COLLECT_LOOP_STATE;
import static software.wings.sm.StateType.ENV_STATE;
import static software.wings.utils.WingsTestConstants.ACCOUNT1_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACCOUNT_NAME;
import static software.wings.utils.WingsTestConstants.APPROVAL_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.COMPANY_NAME;
import static software.wings.utils.WingsTestConstants.DEFAULT_VERSION;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.HELM_CHART_ID;
import static software.wings.utils.WingsTestConstants.INFRA_DEFINITION_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_ID;
import static software.wings.utils.WingsTestConstants.PIPELINE_STAGE_ELEMENT_ID;
import static software.wings.utils.WingsTestConstants.SERVICE1_ID;
import static software.wings.utils.WingsTestConstants.SERVICE2_ID;
import static software.wings.utils.WingsTestConstants.SERVICE3_ID;
import static software.wings.utils.WingsTestConstants.SERVICE4_ID;
import static software.wings.utils.WingsTestConstants.SERVICE5_ID;
import static software.wings.utils.WingsTestConstants.SERVICE6_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_INSTANCE_ID;
import static software.wings.utils.WingsTestConstants.STATE_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.TRIGGER_ID;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_GROUP_ID;
import static software.wings.utils.WingsTestConstants.USER_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_NAME;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.WingsBaseTest;
import software.wings.api.ApprovalStateExecutionData;
import software.wings.api.DeploymentType;
import software.wings.api.EnvStateExecutionData;
import software.wings.api.InstanceElement;
import software.wings.app.GeneralNotifyEventListener;
import software.wings.beans.Account;
import software.wings.beans.Account.Builder;
import software.wings.beans.ApiKeyEntry;
import software.wings.beans.ApprovalDetails;
import software.wings.beans.ApprovalDetails.Action;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.ElementExecutionSummary.ElementExecutionSummaryBuilder;
import software.wings.beans.EntityType;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GraphNode;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep;
import software.wings.beans.Pipeline;
import software.wings.beans.PipelineExecution;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.RequiredExecutionArgs;
import software.wings.beans.Service;
import software.wings.beans.User;
import software.wings.beans.Variable;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.approval.ApprovalInfo;
import software.wings.beans.approval.PreviousApprovalDetails;
import software.wings.beans.artifact.ArtifactInput;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.security.UserGroup;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.url.SubdomainUrlHelper;
import software.wings.persistence.artifact.Artifact;
import software.wings.rules.Listeners;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.deployment.checks.AccountExpirationChecker;
import software.wings.service.impl.security.auth.DeploymentAuthHandler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.ExecutionInterruptManager;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.StateMachineExecutionSimulator;
import software.wings.sm.StateMachineExecutor;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.ArtifactCollectLoopState.ArtifactCollectLoopStateKeys;
import software.wings.sm.states.ForkState.ForkStateExecutionData;
import software.wings.utils.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.DBCursor;
import com.mongodb.WriteResult;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.FindOptions;
import dev.morphia.query.MorphiaIterator;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

/**
 * The Class workflowExecutionServiceTest.
 *
 * @author Rishi
 */
@OwnedBy(CDC)
@Listeners(GeneralNotifyEventListener.class)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowExecutionServiceTest extends WingsBaseTest {
  @InjectMocks @Inject private WorkflowExecutionService workflowExecutionService;
  @InjectMocks
  @Spy
  private software.wings.service.impl.WorkflowExecutionServiceImpl workflowExecutionServiceSpy =
      spy(software.wings.service.impl.WorkflowExecutionServiceImpl.class);

  @Mock private WingsPersistence wingsPersistence;
  @Mock private ExecutionInterruptManager executionInterruptManager;
  @Mock private WorkflowService workflowService;
  @Mock private PipelineService pipelineService;
  @Mock private UserGroupService userGroupService;
  @Mock private DeploymentAuthHandler deploymentAuthHandler;

  @Mock private ServiceResourceService serviceResourceServiceMock;
  @Mock private StateMachineExecutionSimulator stateMachineExecutionSimulator;
  @Mock private MorphiaIterator<WorkflowExecution, WorkflowExecution> executionIterator;
  @Mock private DBCursor dbCursor;
  @Mock private AppService appService;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock software.wings.service.impl.WorkflowExecutionServiceHelper workflowExecutionServiceHelper;
  @Mock AuthService authService;
  @Mock private AccountExpirationChecker accountExpirationChecker;
  @Mock private HelmChartService helmChartService;
  @Mock private ArtifactService artifactService;
  @Mock private StateMachineExecutor stateMachineExecutor;
  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private SubdomainUrlHelper subdomainUrlHelper;
  @Mock private ExecutorService executorService;

  @Inject private WingsPersistence wingsPersistence1;

  private Workflow workflow =
      aWorkflow()
          .uuid(WORKFLOW_ID)
          .appId(APP_ID)
          .name(WORKFLOW_NAME)
          .defaultVersion(DEFAULT_VERSION)
          .orchestrationWorkflow(
              aCanaryOrchestrationWorkflow()
                  .withRequiredEntityTypes(Sets.newHashSet(EntityType.SSH_USER, EntityType.SSH_PASSWORD))
                  .build())
          .build();

  @Mock Query<WorkflowExecution> query;
  @Mock private FieldEnd end;
  @Mock private UpdateOperations<WorkflowExecution> updateOperations;
  @Mock private UpdateResults updateResults;
  @Mock WriteResult writeResult;
  @Mock Query<StateExecutionInstance> statequery;
  @Mock PageResponse<StateExecutionInstance> pageResponseQuery;
  @Mock StateExecutionInstance stateExecutionInstance;
  @Mock PipelineExecution pipeleineExecution;

  /**
   * test setup.
   */
  @Before
  public void setUp() {
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(query.field(any())).thenReturn(end);
    when(end.in(any())).thenReturn(query);
    when(end.greaterThanOrEq(any())).thenReturn(query);
    when(end.hasAnyOf(any())).thenReturn(query);
    when(end.doesNotExist()).thenReturn(query);
    when(query.filter(any(), any())).thenReturn(query);

    when(wingsPersistence.createUpdateOperations(WorkflowExecution.class)).thenReturn(updateOperations);
    when(updateOperations.set(anyString(), any())).thenReturn(updateOperations);
    when(updateOperations.addToSet(anyString(), any())).thenReturn(updateOperations);
    when(wingsPersistence.update(query, updateOperations)).thenReturn(updateResults);
    when(updateResults.getWriteResult()).thenReturn(writeResult);
    when(writeResult.getN()).thenReturn(1);

    when(wingsPersistence.createQuery(eq(StateExecutionInstance.class))).thenReturn(statequery);
    when(workflowExecutionServiceHelper.fetchFailureDetails(APP_ID, WORKFLOW_EXECUTION_ID))
        .thenReturn("failureDetails");
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testRejectWithRollback() {
    Query mockQuery = mock(Query.class);
    WorkflowExecution workflowExecution = WorkflowExecution.builder().uuid("executionId").build();
    doReturn(mockQuery).when(wingsPersistence).createQuery(WorkflowExecution.class);
    doReturn(mockQuery).when(mockQuery).filter(any(), any());
    doReturn(mockQuery).when(mockQuery).filter(any(), any());
    doReturn(mockQuery).when(mockQuery).project(WorkflowExecutionKeys.appId, true);
    doReturn(mockQuery).when(mockQuery).project(WorkflowExecutionKeys.uuid, true);
    doReturn(workflowExecution).when(mockQuery).get();
    doReturn(workflowExecution).when(wingsPersistence).getWithAppId(eq(WorkflowExecution.class), any(), any());
    doReturn(mock(ExecutionInterrupt.class)).when(executionInterruptManager).registerExecutionInterrupt(any());

    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.ROLLBACK);

    boolean success = workflowExecutionService.approveOrRejectExecution(APP_ID, asList(), approvalDetails, "workflow");
    assertThat(success).isEqualTo(true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testRejectWithRollbackShouldFailWhenPipelineExecution() {
    Query mockQuery = mock(Query.class);
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder().workflowType(WorkflowType.PIPELINE).uuid("executionId").build();
    doReturn(mockQuery).when(wingsPersistence).createQuery(WorkflowExecution.class);
    doReturn(mockQuery).when(mockQuery).filter(any(), any());
    doReturn(mockQuery).when(mockQuery).filter(any(), any());
    doReturn(mockQuery).when(mockQuery).project(WorkflowExecutionKeys.appId, true);
    doReturn(mockQuery).when(mockQuery).project(WorkflowExecutionKeys.uuid, true);
    doReturn(workflowExecution).when(mockQuery).get();
    doReturn(workflowExecution).when(wingsPersistence).getWithAppId(eq(WorkflowExecution.class), any(), any());
    doReturn(mock(ExecutionInterrupt.class)).when(executionInterruptManager).registerExecutionInterrupt(any());

    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.ROLLBACK);

    workflowExecutionService.approveOrRejectExecution(APP_ID, asList(), approvalDetails, "workflow");
  }

  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void shouldListExecutions() {
    PageRequest<WorkflowExecution> pageRequest = aPageRequest().build();
    PageResponse<WorkflowExecution> pageResponse = aPageResponse().build();
    when(wingsPersistence.query(WorkflowExecution.class, pageRequest)).thenReturn(pageResponse);
    PageResponse<WorkflowExecution> pageResponse2 =
        workflowExecutionService.listExecutions(pageRequest, false, true, false, true, false, false);
    assertThat(pageResponse2).isNotNull().isEqualTo(pageResponse);
    verify(wingsPersistence).query(WorkflowExecution.class, pageRequest);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void shouldGetActiveServiceCount() throws IllegalAccessException {
    //    when(wingsPersistence.createQuery(eq(WorkflowExecution.class), any())).thenReturn(query);
    WorkflowExecutionServiceImpl workflowExecutionServiceImpl = new WorkflowExecutionServiceImpl();
    FieldUtils.writeField(workflowExecutionServiceImpl, "wingsPersistence", wingsPersistence1, true);
    saveWorkflowExecution(ACCOUNT_ID, Arrays.asList(SERVICE1_ID, SERVICE2_ID));
    saveWorkflowExecution(ACCOUNT_ID, Arrays.asList(SERVICE3_ID));
    saveWorkflowExecution(ACCOUNT_ID, Arrays.asList(SERVICE2_ID));
    saveWorkflowExecution(ACCOUNT1_ID, Arrays.asList(SERVICE4_ID, SERVICE5_ID));
    saveWorkflowExecution(ACCOUNT1_ID, Arrays.asList(SERVICE6_ID));
    int activeServiceCount = workflowExecutionServiceImpl.getActiveServiceCount(ACCOUNT_ID);
    assertThat(activeServiceCount).isEqualTo(3);
  }

  private void saveWorkflowExecution(String accountId, List<String> serviceIds) {
    wingsPersistence1.save(createNewWorkflowExecution(accountId, serviceIds));
  }

  /**
   * Required execution args for orchestrated workflow.
   */
  @Test
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void requiredExecutionArgsForOrchestratedWorkflow() {
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowService.readStateMachine(APP_ID, WORKFLOW_ID, DEFAULT_VERSION)).thenReturn(aStateMachine().build());

    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setOrchestrationId(WORKFLOW_ID);

    when(stateMachineExecutionSimulator.getInfrastructureRequiredEntityType(
             APP_ID, Lists.newArrayList(SERVICE_INSTANCE_ID)))
        .thenReturn(Sets.newHashSet(EntityType.SSH_USER, EntityType.SSH_PASSWORD));

    RequiredExecutionArgs required = workflowExecutionService.getRequiredExecutionArgs(APP_ID, ENV_ID, executionArgs);
    assertThat(required).isNotNull().hasFieldOrPropertyWithValue(
        "entityTypes", workflow.getOrchestrationWorkflow().getRequiredEntityTypes());
  }

  /**
   * Should throw workflowType is null
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowWorkflowNull() {
    assertThatThrownBy(() -> {
      ExecutionArgs executionArgs = new ExecutionArgs();
      RequiredExecutionArgs required = workflowExecutionService.getRequiredExecutionArgs(APP_ID, ENV_ID, executionArgs);
      failBecauseExceptionWasNotThrown(WingsException.class);
    }).isInstanceOf(GeneralException.class);
  }

  /**
   * Should throw orchestrationId is null for an orchestrated execution.
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowNullOrchestrationId() {
    assertThatThrownBy(() -> {
      ExecutionArgs executionArgs = new ExecutionArgs();
      executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);

      RequiredExecutionArgs required = workflowExecutionService.getRequiredExecutionArgs(APP_ID, ENV_ID, executionArgs);
      failBecauseExceptionWasNotThrown(WingsException.class);
    }).isInstanceOf(GeneralException.class);
  }

  /*
   * Should throw invalid orchestration
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowInvalidOrchestration() {
    try {
      ExecutionArgs executionArgs = new ExecutionArgs();
      executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
      executionArgs.setOrchestrationId(WORKFLOW_ID);

      RequiredExecutionArgs required = workflowExecutionService.getRequiredExecutionArgs(APP_ID, ENV_ID, executionArgs);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage("OrchestrationWorkflow not found");
      assertThat(exception.getParams()).containsEntry("message", "OrchestrationWorkflow not found");
    }
  }

  /*
   * Should throw Associated state machine not found
   */
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldThrowNoStateMachine() {
    try {
      when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);

      ExecutionArgs executionArgs = new ExecutionArgs();
      executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
      executionArgs.setOrchestrationId(WORKFLOW_ID);

      RequiredExecutionArgs required = workflowExecutionService.getRequiredExecutionArgs(APP_ID, ENV_ID, executionArgs);
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage("Associated state machine not found");
      assertThat(exception.getParams()).containsEntry("message", "Associated state machine not found");
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowExecution() {
    when(query.order(Sort.descending(any()))).thenReturn(query);
    when(query.get(any(FindOptions.class))).thenReturn(builder().appId(APP_ID).build());
    WorkflowExecution workflowExecution =
        workflowExecutionService.fetchWorkflowExecution(APP_ID, asList(SERVICE_ID), asList(ENV_ID), WORKFLOW_ID);
    assertThat(workflowExecution).isNotNull();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testRejectWithUserGroup() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.REJECT);

    User user = createUser(USER_ID);
    saveUserToPersistence(user);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    UserThreadLocal.set(user);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(any(), any())).thenReturn(true);

    boolean success = workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, (String) null);
    assertThat(success).isEqualTo(true);
    UserThreadLocal.unset();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testRejectWithUserGroupNegative() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.REJECT);

    User user = createUser(USER_ID);
    saveUserToPersistence(user);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    UserThreadLocal.set(user);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(anyString(), anyList())).thenReturn(false);

    boolean success = workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, (String) null);
    assertThat(success).isEqualTo(false);
    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testRejectWithUserGroupWithApiKeyEntry() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.REJECT);

    User user = createUser(USER_ID);
    saveUserToPersistence(user);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    ApiKeyEntry apiKeyEntry =
        ApiKeyEntry.builder().accountId(ACCOUNT_ID).name("API_KEY").userGroupIds(asList(userGroup.getUuid())).build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(userGroupService.verifyApiKeyAuthorizedToAcceptOrRejectApproval(anyList(), anyList())).thenReturn(true);

    boolean success = workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, apiKeyEntry);
    assertThat(success).isEqualTo(true);
    UserThreadLocal.unset();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testRejectWithUserGroupNegativeWithApiKeyEntry() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.REJECT);

    User user = createUser(USER_ID);
    saveUserToPersistence(user);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    ApiKeyEntry apiKeyEntry =
        ApiKeyEntry.builder().accountId(ACCOUNT_ID).name("API_KEY").userGroupIds(asList(userGroup.getUuid())).build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(userGroupService.verifyApiKeyAuthorizedToAcceptOrRejectApproval(anyList(), anyList())).thenReturn(false);

    boolean success = workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, apiKeyEntry);
    assertThat(success).isEqualTo(false);
    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC0_testVerifyAuthorizedToAcceptOrReject() {
    User user = createUser(USER_ID);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));

    UserThreadLocal.set(null);

    boolean success =
        workflowExecutionService.verifyAuthorizedToAcceptOrReject(asList(userGroup.getUuid()), APP_ID, generateUuid());
    assertThat(success).isEqualTo(true);
    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC1_testVerifyAuthorizedToAcceptOrReject() {
    User user = createUser(USER_ID);

    UserThreadLocal.set(user);
    when(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(null, null)).thenReturn(true);
    boolean success = workflowExecutionService.verifyAuthorizedToAcceptOrReject(null, APP_ID, generateUuid());
    assertThat(success).isEqualTo(true);
    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC2_testVerifyAuthorizedToAcceptOrReject() {
    User user = createUser(USER_ID);

    UserThreadLocal.set(user);
    doNothing().when(deploymentAuthHandler).authorizeWorkflowOrPipelineForExecution(any(), anyString());

    when(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(null, null)).thenReturn(false);
    boolean success = workflowExecutionService.verifyAuthorizedToAcceptOrReject(null, APP_ID, generateUuid());
    assertThat(success).isEqualTo(true);
    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC3_testVerifyAuthorizedToAcceptOrReject() {
    User user = createUser(USER_ID);
    doNothing().when(deploymentAuthHandler).authorizeWorkflowOrPipelineForExecution(any(), anyString());

    UserThreadLocal.set(user);
    when(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(anyString(), anyList())).thenReturn(false);

    boolean success = workflowExecutionService.verifyAuthorizedToAcceptOrReject(null, null, generateUuid());
    assertThat(success).isEqualTo(true);
    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC4_testVerifyAuthorizedToAcceptOrReject() {
    User user = createUser(USER_ID);

    UserThreadLocal.set(user);
    when(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(anyString(), anyList())).thenReturn(true);

    boolean success = workflowExecutionService.verifyAuthorizedToAcceptOrReject(null, APP_ID, null);
    assertThat(success).isEqualTo(true);
    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC5_testVerifyAuthorizedToAcceptOrReject() {
    User user = createUser(USER_ID);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));

    UserThreadLocal.set(user);
    String entityId = generateUuid();
    when(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(any(), any())).thenReturn(true);

    doNothing().when(deploymentAuthHandler).authorizeWorkflowOrPipelineForExecution(any(), anyString());
    boolean success =
        workflowExecutionService.verifyAuthorizedToAcceptOrReject(asList(userGroup.getUuid()), APP_ID, entityId);
    assertThat(success).isEqualTo(true);
    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void TC6_testVerifyAuthorizedToAcceptOrReject() {
    User user = createUser(USER_ID);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));

    UserThreadLocal.set(user);
    String entityId = generateUuid();
    when(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(anyString(), anyList())).thenReturn(false);

    doNothing().when(deploymentAuthHandler).authorizeWorkflowOrPipelineForExecution(any(), anyString());
    boolean success =
        workflowExecutionService.verifyAuthorizedToAcceptOrReject(asList(userGroup.getUuid()), APP_ID, entityId);
    assertThat(success).isEqualTo(false);
    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testApproveWithUserGroup() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    User user = createUser(USER_ID);
    saveUserToPersistence(user);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    UserThreadLocal.set(user);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(any(), any())).thenReturn(true);

    boolean success = workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, (String) null);
    assertThat(success).isEqualTo(true);
    UserThreadLocal.unset();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testApproveWithUserGroupNegative() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    User user = createUser(USER_ID);
    saveUserToPersistence(user);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    UserThreadLocal.set(user);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(userGroupService.verifyUserAuthorizedToAcceptOrRejectApproval(anyString(), anyList())).thenReturn(false);

    boolean success = workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, (String) null);
    assertThat(success).isEqualTo(false);
    UserThreadLocal.unset();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testRejectWithUserGroupException() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.REJECT);

    User user = createUser(USER_ID);
    User user1 = createUser(USER_ID + "1");
    saveUserToPersistence(user);
    saveUserToPersistence(user1);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    UserThreadLocal.set(user1);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, (String) null);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testApproveWithUserGroupException() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    User user = createUser(USER_ID);
    User user1 = createUser(USER_ID + "1");
    saveUserToPersistence(user);
    saveUserToPersistence(user1);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    UserThreadLocal.set(user1);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, (String) null);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testApproveWithUserGroupWithApiKeyEntry() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    User user = createUser(USER_ID);
    saveUserToPersistence(user);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    ApiKeyEntry apiKeyEntry =
        ApiKeyEntry.builder().accountId(ACCOUNT_ID).name("API_KEY").userGroupIds(asList(userGroup.getUuid())).build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(userGroupService.verifyApiKeyAuthorizedToAcceptOrRejectApproval(anyList(), anyList())).thenReturn(true);

    boolean success = workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, apiKeyEntry);
    assertThat(success).isEqualTo(true);
    UserThreadLocal.unset();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testApproveWithUserGroupNegativeWithApiKeyEntry() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    User user = createUser(USER_ID);
    saveUserToPersistence(user);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    ApiKeyEntry apiKeyEntry =
        ApiKeyEntry.builder().accountId(ACCOUNT_ID).name("API_KEY").userGroupIds(asList(userGroup.getUuid())).build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(userGroupService.verifyApiKeyAuthorizedToAcceptOrRejectApproval(anyList(), anyList())).thenReturn(false);

    boolean success = workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, apiKeyEntry);
    assertThat(success).isEqualTo(false);
    UserThreadLocal.unset();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testRejectWithUserGroupExceptionWithApiKeyEntry() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.REJECT);

    User user = createUser(USER_ID);
    User user1 = createUser(USER_ID + "1");
    saveUserToPersistence(user);
    saveUserToPersistence(user1);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    ApiKeyEntry apiKeyEntry =
        ApiKeyEntry.builder().accountId(ACCOUNT_ID).name("API_KEY").userGroupIds(asList(userGroup.getUuid())).build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, apiKeyEntry);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testApproveWithUserGroupExceptionWithApiKeyEntry() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    User user = createUser(USER_ID);
    User user1 = createUser(USER_ID + "1");
    saveUserToPersistence(user);
    saveUserToPersistence(user1);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    ApiKeyEntry apiKeyEntry =
        ApiKeyEntry.builder().accountId(ACCOUNT_ID).name("API_KEY").userGroupIds(asList(userGroup.getUuid())).build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    workflowExecutionService.approveOrRejectExecution(
        APP_ID, asList(userGroup.getUuid()), approvalDetails, apiKeyEntry);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testFetchApprovalStateExecutionDataForPipeline() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    User user = createUser(USER_ID);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId(approvalId).userGroups(asList(userGroup.getUuid())).build();
    approvalStateExecutionData.setStatus(ExecutionStatus.PAUSED);
    WorkflowExecution workflowExecution = createNewWorkflowExecution();
    workflowExecution.setWorkflowType(WorkflowType.PIPELINE);
    workflowExecution.setPipelineExecution(createPipelineExecution(approvalStateExecutionData));

    when(workflowExecutionServiceSpy.getWorkflowExecution(
             eq(APP_ID), eq(workflowExecution.getUuid()), any(String[].class)))
        .thenReturn(workflowExecution);
    doNothing().when(workflowExecutionServiceSpy).refreshPipelineExecution(any());

    ApprovalStateExecutionData returnedExecutionData =
        workflowExecutionServiceSpy.fetchApprovalStateExecutionDataFromWorkflowExecution(
            APP_ID, workflowExecution.getUuid(), null, approvalDetails);
    assertThat(returnedExecutionData.getApprovalId()).isEqualTo(approvalId);
    assertThat(returnedExecutionData.getUserGroups()).isEqualTo(asList(userGroup.getUuid()));
    verify(workflowExecutionServiceSpy).refreshPipelineExecution(workflowExecution);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testFetchApprovalStateExecutionDataWithEmptyStateExecution() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    User user = createUser(USER_ID);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));

    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId(approvalId).userGroups(asList(userGroup.getUuid())).build();
    approvalStateExecutionData.setStatus(ExecutionStatus.PAUSED);
    WorkflowExecution workflowExecution = createNewWorkflowExecution();

    Map<String, StateExecutionData> hashMap = new HashMap();
    hashMap.put("Approval", approvalStateExecutionData);
    final StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().displayName("Approval").stateExecutionMap(hashMap).build();

    PageResponse<StateExecutionInstance> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(stateExecutionInstance));
    pageResponse.setTotal(1l);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(statequery.get()).thenReturn(stateExecutionInstance);
    when(statequery.filter(any(), any())).thenReturn(statequery);
    when(wingsPersistence.createQuery(StateExecutionInstance.class)).thenReturn(statequery);
    when(wingsPersistence.query(eq(StateExecutionInstance.class), any(PageRequest.class))).thenReturn(pageResponse);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, workflowExecution.getUuid()))
        .thenReturn(workflowExecution);

    ApprovalStateExecutionData returnedExecutionData =
        workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
            APP_ID, workflowExecution.getUuid(), null, approvalDetails);
    assertThat(returnedExecutionData.getApprovalId()).isEqualTo(approvalId);
    assertThat(returnedExecutionData.getUserGroups()).isEqualTo(asList(userGroup.getUuid()));
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testFetchApprovalStateExecutionDataForPipelineWithException() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    WorkflowExecution workflowExecution = createNewWorkflowExecution();
    workflowExecution.setWorkflowType(WorkflowType.PIPELINE);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, workflowExecution.getUuid()))
        .thenReturn(workflowExecution);

    workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
        APP_ID, workflowExecution.getUuid(), null, approvalDetails);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testFetchApprovalStateExecutionDataForPipelineWithNoApprovalDataException() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    WorkflowExecution workflowExecution = createNewWorkflowExecution();
    workflowExecution.setWorkflowType(WorkflowType.PIPELINE);
    workflowExecution.setPipelineExecution(createPipelineExecution(null));

    when(workflowExecutionServiceSpy.getWorkflowExecution(APP_ID, workflowExecution.getUuid()))
        .thenReturn(workflowExecution);

    doNothing().when(workflowExecutionServiceSpy).refreshPipelineExecution(any());
    workflowExecutionServiceSpy.fetchApprovalStateExecutionDataFromWorkflowExecution(
        APP_ID, workflowExecution.getUuid(), null, approvalDetails);
    verify(workflowExecutionServiceSpy).refreshPipelineExecution(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testFetchApprovalStateExecutionDataForWorkflow() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    User user = createUser(USER_ID);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));

    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId(approvalId).userGroups(asList(userGroup.getUuid())).build();
    approvalStateExecutionData.setStatus(ExecutionStatus.PAUSED);
    WorkflowExecution workflowExecution = createNewWorkflowExecution();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(statequery.get()).thenReturn(stateExecutionInstance);
    when(statequery.filter(any(), any())).thenReturn(statequery);
    when(wingsPersistence.createQuery(StateExecutionInstance.class)).thenReturn(statequery);
    when(stateExecutionInstance.fetchStateExecutionData()).thenReturn(approvalStateExecutionData);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, workflowExecution.getUuid()))
        .thenReturn(workflowExecution);

    ApprovalStateExecutionData returnedExecutionData =
        workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
            APP_ID, workflowExecution.getUuid(), generateUuid(), approvalDetails);
    assertThat(returnedExecutionData.getApprovalId()).isEqualTo(approvalId);
    assertThat(returnedExecutionData.getUserGroups()).isEqualTo(asList(userGroup.getUuid()));
  }

  @Test(expected = WingsException.class)
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testFetchApprovalStateExecutionDataForWorkflowWithNoApprovalDataException() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    WorkflowExecution workflowExecution = createNewWorkflowExecution();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(statequery.get()).thenReturn(stateExecutionInstance);
    when(statequery.filter(any(), any())).thenReturn(statequery);
    when(wingsPersistence.createQuery(StateExecutionInstance.class)).thenReturn(statequery);
    when(stateExecutionInstance.fetchStateExecutionData()).thenReturn(null);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, workflowExecution.getUuid()))
        .thenReturn(workflowExecution);

    workflowExecutionService.fetchApprovalStateExecutionDataFromWorkflowExecution(
        APP_ID, workflowExecution.getUuid(), generateUuid(), approvalDetails);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testFetchApprovalStateExecutionsDataForPipeline() {
    String approvalId = generateUuid();

    User user = createUser(USER_ID);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId(approvalId).userGroups(asList(userGroup.getUuid())).build();
    approvalStateExecutionData.setStatus(ExecutionStatus.PAUSED);
    WorkflowExecution workflowExecution = createNewWorkflowExecution();
    workflowExecution.setWorkflowType(WorkflowType.PIPELINE);
    workflowExecution.setPipelineExecution(createPipelineExecution(approvalStateExecutionData));

    when(workflowExecutionServiceSpy.getWorkflowExecution(
             eq(APP_ID), eq(workflowExecution.getUuid()), any(String[].class)))
        .thenReturn(workflowExecution);
    doNothing().when(workflowExecutionServiceSpy).refreshPipelineExecution(any());
    doReturn(null).when(workflowExecutionServiceSpy).getStageNameForApprovalStateExecutionData(any(), any());

    List<ApprovalStateExecutionData> returnedExecutionData =
        workflowExecutionServiceSpy.fetchApprovalStateExecutionsDataFromWorkflowExecution(
            APP_ID, workflowExecution.getUuid());
    assertThat(returnedExecutionData.get(0).getApprovalId()).isEqualTo(approvalId);
    assertThat(returnedExecutionData.get(0).getUserGroups()).isEqualTo(asList(userGroup.getUuid()));
    verify(workflowExecutionServiceSpy).refreshPipelineExecution(workflowExecution);
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testFetchApprovalStateExecutionsDataWithEmptyStateExecution() {
    String approvalId = generateUuid();

    User user = createUser(USER_ID);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));

    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId(approvalId).userGroups(asList(userGroup.getUuid())).build();
    approvalStateExecutionData.setStatus(ExecutionStatus.PAUSED);
    WorkflowExecution workflowExecution = createNewWorkflowExecution();

    Map<String, StateExecutionData> hashMap = new HashMap();
    hashMap.put("Approval", approvalStateExecutionData);
    final StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().displayName("Approval").stateExecutionMap(hashMap).build();

    PageResponse<StateExecutionInstance> pageResponse = new PageResponse<>();
    pageResponse.setResponse(asList(stateExecutionInstance));
    pageResponse.setTotal(1l);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(statequery.get()).thenReturn(stateExecutionInstance);
    when(statequery.filter(any(), any())).thenReturn(statequery);
    when(wingsPersistence.createQuery(StateExecutionInstance.class)).thenReturn(statequery);
    when(wingsPersistence.query(eq(StateExecutionInstance.class), any(PageRequest.class))).thenReturn(pageResponse);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, workflowExecution.getUuid()))
        .thenReturn(workflowExecution);

    List<ApprovalStateExecutionData> returnedExecutionData =
        workflowExecutionService.fetchApprovalStateExecutionsDataFromWorkflowExecution(
            APP_ID, workflowExecution.getUuid());
    assertThat(returnedExecutionData.get(0).getApprovalId()).isEqualTo(approvalId);
    assertThat(returnedExecutionData.get(0).getUserGroups()).isEqualTo(asList(userGroup.getUuid()));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testFetchApprovalStateExecutionsDataForPipelineWithException() {
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    WorkflowExecution workflowExecution = createNewWorkflowExecution();
    workflowExecution.setWorkflowType(WorkflowType.PIPELINE);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, workflowExecution.getUuid()))
        .thenReturn(workflowExecution);

    workflowExecutionService.fetchApprovalStateExecutionsDataFromWorkflowExecution(APP_ID, workflowExecution.getUuid());
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testFetchApprovalStateExecutionsDataForWorkflow() {
    String approvalId = generateUuid();

    User user = createUser(USER_ID);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));

    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId(approvalId).userGroups(asList(userGroup.getUuid())).build();
    approvalStateExecutionData.setStatus(ExecutionStatus.PAUSED);
    WorkflowExecution workflowExecution = createNewWorkflowExecution();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    when(statequery.get()).thenReturn(stateExecutionInstance);
    when(statequery.filter(any(), any())).thenReturn(statequery);

    PageRequest<StateExecutionInstance> req =
        aPageRequest()
            .withLimit(UNLIMITED)
            .addFilter("appId", EQ, workflowExecution.getAppId())
            .addFilter("executionUuid", EQ, workflowExecution.getUuid())
            .addFilter(StateExecutionInstanceKeys.createdAt, GE, workflowExecution.getCreatedAt())
            .build();

    when(wingsPersistence.query(StateExecutionInstance.class, req)).thenReturn(pageResponseQuery);
    when(pageResponseQuery.getResponse()).thenReturn(asList(stateExecutionInstance));
    when(stateExecutionInstance.fetchStateExecutionData()).thenReturn(approvalStateExecutionData);
    when(workflowExecutionService.getWorkflowExecution(APP_ID, workflowExecution.getUuid()))
        .thenReturn(workflowExecution);

    List<ApprovalStateExecutionData> returnedExecutionData =
        workflowExecutionService.fetchApprovalStateExecutionsDataFromWorkflowExecution(
            APP_ID, workflowExecution.getUuid());
    assertThat(returnedExecutionData.get(0).getApprovalId()).isEqualTo(approvalId);
    assertThat(returnedExecutionData.get(0).getUserGroups()).isEqualTo(asList(userGroup.getUuid()));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testInstancesDeployedFromExecution() {
    WorkflowExecution workflowExecution = createNewWorkflowExecution();
    int instancesDeployed = workflowExecutionService.getInstancesDeployedFromExecution(workflowExecution);
    assertThat(instancesDeployed).isEqualTo(1);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchDeploymentMetadataFFOn() {
    validateFetchDeploymentMetadata(false, true);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchDeploymentMetadataPipelineFFOn() {
    validateFetchDeploymentMetadata(true, true);
  }

  private void validateFetchDeploymentMetadata(boolean isPipeline, boolean ffOn) {
    ExecutionArgs executionArgs = new ExecutionArgs();
    if (isPipeline) {
      executionArgs.setWorkflowType(WorkflowType.PIPELINE);
      executionArgs.setPipelineId(PIPELINE_ID);
    } else {
      executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
      executionArgs.setOrchestrationId(WORKFLOW_ID);
    }
    Workflow workflow = aWorkflow().build();
    DeploymentMetadata deploymentMetadata =
        DeploymentMetadata.builder()
            .artifactVariables(singletonList(ArtifactVariable.builder().name("art1").build()))
            .artifactRequiredServiceIds(singletonList(SERVICE_ID))
            .build();
    DeploymentMetadata.Include[] includes =
        new DeploymentMetadata.Include[] {ENVIRONMENT, ARTIFACT_SERVICE, DEPLOYMENT_TYPE};
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowService.fetchDeploymentMetadata(APP_ID, workflow, null, null, null, false, null, includes))
        .thenReturn(deploymentMetadata);
    when(pipelineService.fetchDeploymentMetadata(APP_ID, executionArgs.getPipelineId(),
             executionArgs.getWorkflowVariables(), null, null, false, null, includes))
        .thenReturn(deploymentMetadata);
    DeploymentMetadata finalDeploymentMetadata =
        workflowExecutionService.fetchDeploymentMetadata(APP_ID, executionArgs);
    assertThat(finalDeploymentMetadata).isNotNull();
    assertThat(finalDeploymentMetadata.getArtifactVariables()).isNotNull();
    assertThat(finalDeploymentMetadata.getArtifactVariables().size()).isEqualTo(1);
    assertThat(finalDeploymentMetadata.getArtifactVariables().get(0).getName()).isEqualTo("art1");
    if (ffOn) {
      verify(serviceResourceService, never()).fetchServicesByUuids(any(), any());
    } else {
      verify(serviceResourceService).fetchServicesByUuids(any(), any());
    }
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFetchWorkflowVariables() {
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowType(WorkflowType.ORCHESTRATION);
    executionArgs.setOrchestrationId(WORKFLOW_ID);
    workflowExecutionService.fetchWorkflowVariables(APP_ID, executionArgs, null, null);
    verify(workflowExecutionServiceHelper).fetchWorkflowVariables(APP_ID, executionArgs, null);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testInstancesDeployedFromPipelineExecution() {
    WorkflowExecution workflowExecution = createNewWorkflowExecution();
    PipelineStageExecution pipelineStageExecution =
        PipelineStageExecution.builder().workflowExecutions(asList(workflowExecution)).build();

    PipelineExecution pipelineExecution = PipelineExecution.Builder.aPipelineExecution()
                                              .withWorkflowType(WorkflowType.PIPELINE)
                                              .withPipelineStageExecutions(asList(pipelineStageExecution))
                                              .build();
    WorkflowExecution parentWorkflowExecution =
        builder().pipelineExecution(pipelineExecution).workflowType(WorkflowType.PIPELINE).build();
    int instancesDeployed = workflowExecutionService.getInstancesDeployedFromExecution(parentWorkflowExecution);
    assertThat(instancesDeployed).isEqualTo(1);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void shouldNotExecuteRollingWorkflowWithK8sV1Service() {
    Service service = Service.builder()
                          .uuid(SERVICE_ID)
                          .appId(APP_ID)
                          .accountId(ACCOUNT_ID)
                          .deploymentType(DeploymentType.KUBERNETES)
                          .isK8sV2(true)
                          .build();
    Workflow workflow =
        aWorkflow()
            .appId(APP_ID)
            .accountId(ACCOUNT_ID)
            .serviceId(SERVICE_ID)
            .services(asList(service))
            .orchestrationWorkflow(
                aCanaryOrchestrationWorkflow().withOrchestrationWorkflowType(OrchestrationWorkflowType.ROLLING).build())
            .build();

    when(workflowService.getResolvedServices(any(), any())).thenReturn(asList(service));

    ExecutionArgs executionArgs = new ExecutionArgs();
    workflowExecutionServiceSpy.validateWorkflowTypeAndService(workflow, executionArgs);

    service.setK8sV2(false);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> workflowExecutionServiceSpy.validateWorkflowTypeAndService(workflow, executionArgs));
  }

  private PipelineExecution createPipelineExecution(ApprovalStateExecutionData approvalStateExecutionData) {
    PipelineStageExecution pipelineStageExecution = PipelineStageExecution.builder()
                                                        .status(ExecutionStatus.PAUSED)
                                                        .stateExecutionData(approvalStateExecutionData)
                                                        .build();
    return aPipelineExecution()
        .withPipelineStageExecutions(com.google.common.collect.Lists.newArrayList(pipelineStageExecution))
        .build();
  }

  private User createUser(String userId) {
    Account account = Builder.anAccount()
                          .withUuid(ACCOUNT_ID)
                          .withCompanyName(COMPANY_NAME)
                          .withAccountName(ACCOUNT_NAME)
                          .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                          .build();
    return anUser().uuid(userId).appId(APP_ID).emailVerified(true).email(USER_EMAIL).accounts(asList(account)).build();
  }

  private UserGroup createUserGroup(List<String> memberIds) {
    return UserGroup.builder().accountId(ACCOUNT_ID).uuid(USER_GROUP_ID).memberIds(memberIds).build();
  }

  private WorkflowExecution createNewWorkflowExecution() {
    return builder()
        .appId(APP_ID)
        .appName(APP_NAME)
        .envType(NON_PROD)
        .status(ExecutionStatus.PAUSED)
        .workflowType(WorkflowType.ORCHESTRATION)
        .uuid(generateUuid())
        .serviceExecutionSummaries(
            asList(ElementExecutionSummaryBuilder.anElementExecutionSummary()
                       .withInstanceStatusSummaries(asList(
                           anInstanceStatusSummary()
                               .withInstanceElement(
                                   InstanceElement.Builder.anInstanceElement().uuid("id1").podName("pod").build())
                               .build()))
                       .build()))
        .build();
  }

  private WorkflowExecution createNewWorkflowExecution(String accountId, List<String> serviceIds) {
    return builder()
        .accountId(accountId)
        .appId(APP_ID)
        .appName(APP_NAME)
        .envType(NON_PROD)
        .startTs(System.currentTimeMillis() - 10000)
        .status(ExecutionStatus.PAUSED)
        .workflowType(WorkflowType.ORCHESTRATION)
        .uuid(generateUuid())
        .serviceIds(serviceIds)
        .build();
  }

  private void saveUserToPersistence(User user) {
    wingsPersistence1.save(user);
  }

  private void saveUserGroupToPersistence(UserGroup userGroup) {
    wingsPersistence1.save(userGroup);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testAddTagFilterToMatchKeysToPageRequest() {
    PageRequest<WorkflowExecution> pageRequest = new PageRequest<>();
    workflowExecutionService.addTagFilterToPageRequest(pageRequest,
        "{\"harnessTagFilter\":{\"matchAll\":false,\"conditions\":[{\"name\":\"label\",\"operator\":\"EXISTS\"}]}}");
    assertThat(pageRequest.getFilters().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testAddTagFilterToMatchKeyValuePairsToPageRequest() {
    PageRequest<WorkflowExecution> pageRequest = new PageRequest<>();
    workflowExecutionService.addTagFilterToPageRequest(pageRequest,
        "{\"harnessTagFilter\":{\"matchAll\":false,\"conditions\":[{\"name\":\"feature\",\"operator\":\"IN\",\"values\":[\"copy\"]}]}}");
    assertThat(pageRequest.getFilters().size()).isEqualTo(1);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = POOJA)
  @Category(UnitTests.class)
  public void testPipelineAuthorization() {
    Variable envVariable = aVariable().name("Environment").entityType(EntityType.ENVIRONMENT).build();
    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).build();
    pipeline.setEnvIds(Collections.singletonList("Environment"));
    pipeline.setPipelineVariables(Collections.singletonList(envVariable));
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowVariables(ImmutableMap.of("Environment", ENV_ID));
    when(pipelineService.readPipelineResolvedVariablesLoopedInfo(any(), any(), any())).thenReturn(pipeline);
    User user = anUser().build();
    UserThreadLocal.set(user);
    workflowExecutionService.triggerPipelineExecution(APP_ID, PIPELINE_ID, executionArgs, null);
    verify(deploymentAuthHandler).authorizePipelineExecution(eq(APP_ID), eq(PIPELINE_ID));
    verify(authService).checkIfUserAllowedToDeployPipelineToEnv(eq(APP_ID), eq(ENV_ID));
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testPipelineAuthorizationWithWebhookTriggerAuthorizationFfOn() {
    Variable envVariable = aVariable().name("Environment").entityType(EntityType.ENVIRONMENT).build();
    Pipeline pipeline = Pipeline.builder().uuid(PIPELINE_ID).build();
    pipeline.setEnvIds(Collections.singletonList("Environment"));
    pipeline.setPipelineVariables(Collections.singletonList(envVariable));
    ExecutionArgs executionArgs = new ExecutionArgs();
    executionArgs.setWorkflowVariables(ImmutableMap.of("Environment", ENV_ID));
    when(pipelineService.readPipelineResolvedVariablesLoopedInfo(any(), any(), any())).thenReturn(pipeline);
    when(featureFlagService.isEnabled(eq(WEBHOOK_TRIGGER_AUTHORIZATION), any())).thenReturn(true);
    User user = anUser().build();
    UserThreadLocal.set(user);

    assertThatThrownBy(()
                           -> workflowExecutionService.triggerPipelineExecution(APP_ID, PIPELINE_ID, executionArgs,
                               Trigger.builder().uuid(TRIGGER_ID).condition(new WebHookTriggerCondition()).build()))
        .isInstanceOf(WingsException.class)
        .hasMessage("You can not deploy an empty pipeline.");
    verify(deploymentAuthHandler).authorizePipelineExecution(eq(APP_ID), eq(PIPELINE_ID));
    verify(authService).checkIfUserAllowedToDeployPipelineToEnv(eq(APP_ID), eq("Environment"));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateHelmChartsInWorkflowExecution() {
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    WorkflowExecution workflowExecution = createNewWorkflowExecution();
    workflowExecution.setServiceIds(asList(SERVICE_ID + 1, SERVICE_ID + 2));

    Set<String> keywords = new HashSet<>();
    ExecutionArgs executionArgs = new ExecutionArgs();
    HelmChart helmChart1 = generateHelmChart(1);
    HelmChart helmChart2 = generateHelmChart(2);
    HelmChart helmChart3 = generateHelmChart(3);
    executionArgs.setHelmCharts(asList(HelmChart.builder().uuid(HELM_CHART_ID + 1).build(),
        HelmChart.builder().uuid(HELM_CHART_ID + 2).build(), HelmChart.builder().uuid(HELM_CHART_ID + 3).build()));

    when(helmChartService.listByIds(ACCOUNT_ID, asList(HELM_CHART_ID + 1, HELM_CHART_ID + 2, HELM_CHART_ID + 3)))
        .thenReturn(asList(helmChart1, helmChart2, helmChart3));
    workflowExecutionServiceSpy.populateArtifactsAndServices(
        workflowExecution, new WorkflowStandardParams(), keywords, executionArgs, ACCOUNT_ID);

    assertThat(executionArgs.getHelmCharts()).containsExactly(helmChart1, helmChart2, helmChart3);
    assertThat(workflowExecution.getHelmCharts()).containsExactly(helmChart1, helmChart2);
    assertThat(keywords).containsExactlyInAnyOrder("chart", "description", "v1", "v2");
    verify(helmChartService, times(1)).listByIds(anyString(), anyList());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForInvalidHelmChart() {
    when(featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, ACCOUNT_ID)).thenReturn(true);
    WorkflowExecution workflowExecution = createNewWorkflowExecution();
    workflowExecution.setServiceIds(asList(SERVICE_ID + 1, SERVICE_ID + 2));

    Set<String> keywords = new HashSet<>();
    ExecutionArgs executionArgs = new ExecutionArgs();
    HelmChart helmChart1 = generateHelmChart(1);
    HelmChart helmChart2 = generateHelmChart(2);
    executionArgs.setHelmCharts(asList(HelmChart.builder().uuid(HELM_CHART_ID + 1).build(),
        HelmChart.builder().uuid(HELM_CHART_ID + 2).build(), HelmChart.builder().uuid(HELM_CHART_ID + 3).build()));

    when(helmChartService.listByIds(ACCOUNT_ID, asList(HELM_CHART_ID + 1, HELM_CHART_ID + 2, HELM_CHART_ID + 3)))
        .thenReturn(asList(helmChart1, helmChart2));
    workflowExecutionServiceSpy.populateArtifactsAndServices(
        workflowExecution, new WorkflowStandardParams(), keywords, executionArgs, ACCOUNT_ID);
    verify(helmChartService, times(1)).listByIds(anyString(), anyList());
  }

  private HelmChart generateHelmChart(int version) {
    return HelmChart.builder()
        .uuid(HELM_CHART_ID + version)
        .name("chart")
        .description("description")
        .serviceId(SERVICE_ID + version)
        .version("v" + version)
        .build();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testDeploymentMetadataRunningPipeline() {
    final String appID = "nCLN8c84SqWPr44sqg65JQ";
    final String pipelineStageElementId = "iibzVUjNTlWsv23lQIrkWw";
    final String pipelineExecutionId = "3v2FfeZUTvqSnz7djyGMqQ";
    final Map<String, String> wfVariables = new HashMap<>();
    wfVariables.put("pipelineInfra", "62pv3U26RnmaLYFiZxjiTg");
    wfVariables.put("service2", "NA2uRPKLTqm9VU3dPENb-g");

    Workflow workflow = JsonUtils.readResourceFile("workflows/k8s_workflow.json", Workflow.class);
    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/runtime_pipeline_execution_stage2.json", WorkflowExecution.class);
    Pipeline pipelineWithResolvedVars =
        JsonUtils.readResourceFile("pipeline/k8s_two_stage_pipeline_resolved_vars.json", Pipeline.class);
    Pipeline pipeline = JsonUtils.readResourceFile("pipeline/k8s_two_stage_pipeline_without_vars.json", Pipeline.class);
    List<String> emptyList = null;

    Query<WorkflowExecution> query = mock(Query.class);
    when(wingsPersistence.createQuery(WorkflowExecution.class)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.appId, appID)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.uuid, pipelineExecutionId)).thenReturn(query);
    when(query.get()).thenReturn(workflowExecution);
    when(pipelineService.readPipelineResolvedVariablesLoopedInfo(eq(appID), anyString(), any(Map.class)))
        .thenReturn(pipelineWithResolvedVars);
    when(pipelineService.getPipeline(eq(appID), anyString())).thenReturn(pipeline);
    when(workflowService.readWorkflow(eq(appID), anyString())).thenReturn(workflow);

    Map<String, String> expectedWFVars =
        JsonUtils.readResourceFile("expected_wf_variables.json", new TypeReference<Map<String, String>>() {});
    workflowExecutionService.fetchDeploymentMetadataRunningPipeline(
        appID, wfVariables, true, pipelineExecutionId, pipelineStageElementId);

    verify(workflowService)
        .fetchDeploymentMetadata(eq(appID), eq(workflow), eq(expectedWFVars), eq(emptyList), eq(emptyList), eq(true),
            eq(workflowExecution), eq(ENVIRONMENT), eq(ARTIFACT_SERVICE), eq(DEPLOYMENT_TYPE));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testDeploymentMetadataRunningPipelineWithBuildWF() {
    final String appID = "nCLN8c84SqWPr44sqg65JQ";
    final String pipelineStageElementId = "iibzVUjNTlWsv23lQIrkWw";
    final String pipelineExecutionId = "3v2FfeZUTvqSnz7djyGMqQ";
    final Map<String, String> wfVariables = new HashMap<>();
    wfVariables.put("pipelineInfra", "62pv3U26RnmaLYFiZxjiTg");
    wfVariables.put("service2", "NA2uRPKLTqm9VU3dPENb-g");

    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/runtime_pipeline_execution_stage2.json", WorkflowExecution.class);
    Pipeline pipelineWithResolvedVars =
        JsonUtils.readResourceFile("pipeline/k8s_two_stage_pipeline_resolved_vars.json", Pipeline.class);
    pipelineWithResolvedVars.setHasBuildWorkflow(true);
    Pipeline pipeline = JsonUtils.readResourceFile("pipeline/k8s_two_stage_pipeline_without_vars.json", Pipeline.class);
    // Set pipeline as buildPipeline
    List<String> emptyList = null;

    Query<WorkflowExecution> query = mock(Query.class);
    when(wingsPersistence.createQuery(WorkflowExecution.class)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.appId, appID)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.uuid, pipelineExecutionId)).thenReturn(query);
    when(query.get()).thenReturn(workflowExecution);
    when(pipelineService.readPipelineResolvedVariablesLoopedInfo(eq(appID), anyString(), any(Map.class)))
        .thenReturn(pipelineWithResolvedVars);
    when(pipelineService.getPipeline(eq(appID), anyString())).thenReturn(pipeline);
    DeploymentMetadata actual = workflowExecutionService.fetchDeploymentMetadataRunningPipeline(
        appID, wfVariables, true, pipelineExecutionId, pipelineStageElementId);

    assertThat(actual).isEqualTo(DeploymentMetadata.builder().build());
    verify(workflowService, never()).readWorkflow(anyString(), anyString());

    verify(workflowService, never())
        .fetchDeploymentMetadata(
            anyString(), any(), anyMap(), anyList(), eq(emptyList), anyBoolean(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testContinueExecutionOfPausedPipelineExecution() {
    final String appID = "nCLN8c84SqWPr44sqg65JQ";
    final String pipelineStageElementId = "iibzVUjNTlWsv23lQIrkWw";
    final String pipelineExecutionId = "3v2FfeZUTvqSnz7djyGMqQ";

    ExecutionArgs executionArgs =
        JsonUtils.readResourceFile("execution_args/execution_args_continue_pipeline.json", ExecutionArgs.class);
    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/runtime_pipeline_execution_stage2.json", WorkflowExecution.class);
    Pipeline pipelineWithVars =
        JsonUtils.readResourceFile("pipeline/k8s_two_stage_runtime_pipeline.json", Pipeline.class);
    Pipeline pipeline = JsonUtils.readResourceFile("pipeline/k8s_two_stage_pipeline_without_vars.json", Pipeline.class);
    Workflow workflow = JsonUtils.readResourceFile("workflows/k8s_workflow.json", Workflow.class);
    Artifact artifact = JsonUtils.readResourceFile("artifacts/artifacts.json", Artifact.class);
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .uuid("D7fBZxZyQniDAhWTHdnYHQ")
                                                        .appId("nCLN8c84SqWPr44sqg65JQ")
                                                        .status(ExecutionStatus.PAUSED)
                                                        .executionUuid("dTFHGyWOTMSHXGQoI5kVKw")
                                                        .contextElements(new LinkedList<>())
                                                        .build();

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams params = new WorkflowStandardParams();

    when(artifactService.get(anyString(), anyString())).thenReturn(artifact);
    when(wingsPersistence.getWithAppId(eq(WorkflowExecution.class), eq(appID), eq(pipelineExecutionId)))
        .thenReturn(workflowExecution);
    when(pipelineService.readPipelineWithVariables(eq(appID), anyString())).thenReturn(pipelineWithVars);
    when(pipelineService.getPipeline(eq(appID), anyString())).thenReturn(pipeline);
    doNothing().when(deploymentAuthHandler).authorizePipelineExecution(anyString(), anyString());
    doNothing().when(authService).checkIfUserAllowedToDeployPipelineToEnv(anyString(), anyString());
    when(workflowService.readWorkflow(eq(appID), anyString())).thenReturn(workflow);

    Query query = mock(Query.class);
    when(wingsPersistence.createQuery(eq(StateExecutionInstance.class))).thenReturn(query);
    when(query.filter(anyString(), any())).thenReturn(query);
    when(query.get()).thenReturn(stateExecutionInstance);
    when(stateMachineExecutor.getExecutionContext(anyString(), anyString(), anyString())).thenReturn(context);
    when(context.getContextElement(eq(ContextElementType.STANDARD))).thenReturn(params);
    when(wingsPersistence.createUpdateOperations(eq(StateExecutionInstance.class)))
        .thenReturn(mock(UpdateOperations.class));

    // Test that updates are correct
    UpdateOperations updateOperations = mock(UpdateOperations.class);
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(wingsPersistence.createUpdateOperations(eq(WorkflowExecution.class))).thenReturn(updateOperations);
    when(updateOperations.set(anyString(), any())).thenReturn(updateOperations);
    when(featureFlagService.isEnabled(
             eq(FeatureName.SPG_ALLOW_REFRESH_PIPELINE_EXECUTION_BEFORE_CONTINUE_PIPELINE), any()))
        .thenReturn(false);
    workflowExecutionServiceSpy.continuePipelineStage(
        appID, pipelineExecutionId, pipelineStageElementId, executionArgs);

    List<Artifact> expectedArtifacts = JsonUtils.readResourceFile(
        "artifacts/expected_artifacts_continue_pipeline.json", new TypeReference<List<Artifact>>() {});

    List<ArtifactVariable> expectedArtifactVars = JsonUtils.readResourceFile(
        "artifacts/expected_artifact_variables_continue_pipeline.json", new TypeReference<List<ArtifactVariable>>() {});

    verify(updateOperations).set(eq(WorkflowExecutionKeys.startTs), anyLong());
    verify(updateOperations).set(eq(WorkflowExecutionKeys.executionArgs_artifact_variables), eq(expectedArtifactVars));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.artifacts), eq(expectedArtifacts));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.executionArgs_artifacts), eq(expectedArtifacts));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.serviceIds), eq(asList("NA2uRPKLTqm9VU3dPENb-g")));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.infraDefinitionIds), eq(asList("62pv3U26RnmaLYFiZxjiTg")));
    verify(workflowExecutionServiceSpy, never()).refreshPipelineExecution(any());
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testContinueExecutionOfPausedPipelineExecutionWithRefresh() {
    final String appID = "nCLN8c84SqWPr44sqg65JQ";
    final String pipelineStageElementId = "iibzVUjNTlWsv23lQIrkWw";
    final String pipelineExecutionId = "3v2FfeZUTvqSnz7djyGMqQ";

    ExecutionArgs executionArgs =
        JsonUtils.readResourceFile("execution_args/execution_args_continue_pipeline.json", ExecutionArgs.class);
    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/runtime_pipeline_execution_stage2.json", WorkflowExecution.class);
    Pipeline pipelineWithVars =
        JsonUtils.readResourceFile("pipeline/k8s_two_stage_runtime_pipeline.json", Pipeline.class);
    Pipeline pipeline = JsonUtils.readResourceFile("pipeline/k8s_two_stage_pipeline_without_vars.json", Pipeline.class);
    Workflow workflow = JsonUtils.readResourceFile("workflows/k8s_workflow.json", Workflow.class);
    Artifact artifact = JsonUtils.readResourceFile("artifacts/artifacts.json", Artifact.class);
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .uuid("D7fBZxZyQniDAhWTHdnYHQ")
                                                        .appId("nCLN8c84SqWPr44sqg65JQ")
                                                        .stateName("k8s-stage-2")
                                                        .stateType(ENV_STATE.getType())
                                                        .status(ExecutionStatus.PAUSED)
                                                        .displayName("k8s-stage-2")
                                                        .executionUuid("dTFHGyWOTMSHXGQoI5kVKw")
                                                        .contextElements(new LinkedList<>())
                                                        .build();

    StateExecutionInstance stateExecutionInstanceStep2 = aStateExecutionInstance()
                                                             .uuid("D7fBZxZyQniDAhWTHdnYHE")
                                                             .appId("nCLN8c84SqWPr44sqg65JQ")
                                                             .stateName("k8s-stage-1")
                                                             .stateType(ENV_STATE.getType())
                                                             .status(SUCCESS)
                                                             .displayName("k8s-stage-1")
                                                             .executionUuid("dTFHGyWOTMSHXGQoI5kVKw")
                                                             .contextElements(new LinkedList<>())
                                                             .build();

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams params = new WorkflowStandardParams();

    when(artifactService.get(anyString(), anyString())).thenReturn(artifact);
    when(wingsPersistence.getWithAppId(eq(WorkflowExecution.class), eq(appID), eq(pipelineExecutionId)))
        .thenReturn(workflowExecution);
    when(pipelineService.readPipelineWithVariables(eq(appID), anyString())).thenReturn(pipelineWithVars);
    when(pipelineService.getPipeline(eq(appID), anyString())).thenReturn(pipeline);
    doNothing().when(deploymentAuthHandler).authorizePipelineExecution(anyString(), anyString());
    doNothing().when(authService).checkIfUserAllowedToDeployPipelineToEnv(anyString(), anyString());
    when(workflowService.readWorkflow(eq(appID), anyString())).thenReturn(workflow);

    Query query = mock(Query.class);
    when(wingsPersistence.createQuery(eq(StateExecutionInstance.class))).thenReturn(query);
    when(query.filter(anyString(), any())).thenReturn(query);
    when(query.get()).thenReturn(stateExecutionInstance);
    when(stateMachineExecutor.getExecutionContext(anyString(), anyString(), anyString())).thenReturn(context);
    when(context.getContextElement(eq(ContextElementType.STANDARD))).thenReturn(params);
    when(wingsPersistence.createUpdateOperations(eq(StateExecutionInstance.class)))
        .thenReturn(mock(UpdateOperations.class));

    // Test that updates are correct
    UpdateOperations updateOperations = mock(UpdateOperations.class);
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(wingsPersistence.createUpdateOperations(eq(WorkflowExecution.class))).thenReturn(updateOperations);
    when(updateOperations.set(anyString(), any())).thenReturn(updateOperations);
    when(featureFlagService.isEnabled(
             eq(FeatureName.SPG_ALLOW_REFRESH_PIPELINE_EXECUTION_BEFORE_CONTINUE_PIPELINE), any()))
        .thenReturn(true);
    when(wingsPersistence.query(eq(StateExecutionInstance.class), any()))
        .thenReturn(aPageResponse().withResponse(List.of(stateExecutionInstance, stateExecutionInstanceStep2)).build());
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    workflowExecutionServiceSpy.continuePipelineStage(
        appID, pipelineExecutionId, pipelineStageElementId, executionArgs);

    List<Artifact> expectedArtifacts = JsonUtils.readResourceFile(
        "artifacts/expected_artifacts_continue_pipeline.json", new TypeReference<List<Artifact>>() {});

    List<ArtifactVariable> expectedArtifactVars = JsonUtils.readResourceFile(
        "artifacts/expected_artifact_variables_continue_pipeline.json", new TypeReference<List<ArtifactVariable>>() {});

    verify(updateOperations).set(eq(WorkflowExecutionKeys.startTs), anyLong());
    verify(updateOperations).set(eq(WorkflowExecutionKeys.executionArgs_artifact_variables), eq(expectedArtifactVars));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.artifacts), eq(expectedArtifacts));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.executionArgs_artifacts), eq(expectedArtifacts));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.serviceIds), eq(asList("NA2uRPKLTqm9VU3dPENb-g")));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.infraDefinitionIds), eq(asList("62pv3U26RnmaLYFiZxjiTg")));
    verify(workflowExecutionServiceSpy, times(1)).refreshPipelineExecution(any());
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void testContinueExecutionOfPausedBuildPipelineExecutionWithRefresh() {
    final String appID = "nCLN8c84SqWPr44sqg65JQ";
    final String pipelineStageElementId = "iibzVUjNTlWsv23lQIrkWw";
    final String pipelineExecutionId = "3v2FfeZUTvqSnz7djyGMqQ";

    ExecutionArgs executionArgs =
        JsonUtils.readResourceFile("execution_args/execution_args_continue_pipeline.json", ExecutionArgs.class);
    executionArgs.setWorkflowVariables(Collections.singletonMap("wf_variable", "value1"));
    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/runtime_pipeline_execution_stage2.json", WorkflowExecution.class);
    workflowExecution.setServiceIds(null);
    workflowExecution.setInfraDefinitionIds(null);
    workflowExecution.setEnvIds(null);
    workflowExecution.getExecutionArgs().setWorkflowVariables(new HashMap<>());

    Pipeline pipeline = JsonUtils.readResourceFile("pipeline/k8s_two_stage_pipeline_without_vars.json", Pipeline.class);
    pipeline.getPipelineStages().get(1).getPipelineStageElements().get(0).getProperties().remove("envId");
    Workflow workflow = JsonUtils.readResourceFile("workflows/k8s_workflow.json", Workflow.class);
    ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).setUserVariables(new ArrayList<>());
    Artifact artifact = JsonUtils.readResourceFile("artifacts/artifacts.json", Artifact.class);
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .uuid("D7fBZxZyQniDAhWTHdnYHQ")
                                                        .appId("nCLN8c84SqWPr44sqg65JQ")
                                                        .stateName("k8s-stage-2")
                                                        .stateType(ENV_STATE.getType())
                                                        .status(ExecutionStatus.PAUSED)
                                                        .displayName("k8s-stage-2")
                                                        .executionUuid("dTFHGyWOTMSHXGQoI5kVKw")
                                                        .contextElements(new LinkedList<>())
                                                        .build();

    StateExecutionInstance stateExecutionInstanceStep2 = aStateExecutionInstance()
                                                             .uuid("D7fBZxZyQniDAhWTHdnYHE")
                                                             .appId("nCLN8c84SqWPr44sqg65JQ")
                                                             .stateName("k8s-stage-1")
                                                             .stateType(ENV_STATE.getType())
                                                             .status(SUCCESS)
                                                             .displayName("k8s-stage-1")
                                                             .executionUuid("dTFHGyWOTMSHXGQoI5kVKw")
                                                             .contextElements(new LinkedList<>())
                                                             .build();

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams params = new WorkflowStandardParams();

    when(artifactService.get(anyString(), anyString())).thenReturn(artifact);
    when(wingsPersistence.getWithAppId(eq(WorkflowExecution.class), eq(appID), eq(pipelineExecutionId)))
        .thenReturn(workflowExecution);
    when(pipelineService.readPipelineWithVariables(eq(appID), anyString())).thenReturn(pipeline);
    when(pipelineService.getPipeline(eq(appID), anyString())).thenReturn(pipeline);
    doNothing().when(deploymentAuthHandler).authorizePipelineExecution(anyString(), anyString());
    doNothing().when(authService).checkIfUserAllowedToDeployPipelineToEnv(anyString(), anyString());
    when(workflowService.readWorkflow(eq(appID), anyString())).thenReturn(workflow);

    Query query = mock(Query.class);
    when(wingsPersistence.createQuery(eq(StateExecutionInstance.class))).thenReturn(query);
    when(query.filter(anyString(), any())).thenReturn(query);
    when(query.get()).thenReturn(stateExecutionInstance);
    when(stateMachineExecutor.getExecutionContext(anyString(), anyString(), anyString())).thenReturn(context);
    when(context.getContextElement(eq(ContextElementType.STANDARD))).thenReturn(params);
    when(wingsPersistence.createUpdateOperations(eq(StateExecutionInstance.class)))
        .thenReturn(mock(UpdateOperations.class));
    // Test that updates are correct
    UpdateOperations updateOperations = mock(UpdateOperations.class);
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(wingsPersistence.createUpdateOperations(eq(WorkflowExecution.class))).thenReturn(updateOperations);
    when(updateOperations.set(anyString(), any())).thenReturn(updateOperations);
    when(featureFlagService.isEnabled(
             eq(FeatureName.SPG_ALLOW_REFRESH_PIPELINE_EXECUTION_BEFORE_CONTINUE_PIPELINE), any()))
        .thenReturn(true);
    when(wingsPersistence.query(eq(StateExecutionInstance.class), any()))
        .thenReturn(aPageResponse().withResponse(List.of(stateExecutionInstance, stateExecutionInstanceStep2)).build());
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    workflowExecutionServiceSpy.continuePipelineStage(
        appID, pipelineExecutionId, pipelineStageElementId, executionArgs);

    List<Artifact> expectedArtifacts = JsonUtils.readResourceFile(
        "artifacts/expected_artifacts_continue_pipeline.json", new TypeReference<List<Artifact>>() {});

    List<ArtifactVariable> expectedArtifactVars = JsonUtils.readResourceFile(
        "artifacts/expected_artifact_variables_continue_pipeline.json", new TypeReference<List<ArtifactVariable>>() {});

    verify(updateOperations).set(eq(WorkflowExecutionKeys.startTs), anyLong());
    verify(updateOperations).set(eq(WorkflowExecutionKeys.executionArgs_artifact_variables), eq(expectedArtifactVars));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.artifacts), eq(expectedArtifacts));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.executionArgs_artifacts), eq(expectedArtifacts));
    verify(updateOperations, never()).set(eq(WorkflowExecutionKeys.serviceIds), anyList());
    verify(updateOperations, never()).set(eq(WorkflowExecutionKeys.infraDefinitionIds), anyList());
    verify(updateOperations, never()).set(eq(WorkflowExecutionKeys.envIds), anyList());
    verify(workflowExecutionServiceSpy, times(1)).refreshPipelineExecution(any());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testContinueExecutionOfPausedBuildPipelineExecution() {
    final String appID = "nCLN8c84SqWPr44sqg65JQ";
    final String pipelineStageElementId = "iibzVUjNTlWsv23lQIrkWw";
    final String pipelineExecutionId = "3v2FfeZUTvqSnz7djyGMqQ";

    ExecutionArgs executionArgs =
        JsonUtils.readResourceFile("execution_args/execution_args_continue_pipeline.json", ExecutionArgs.class);
    executionArgs.setWorkflowVariables(Collections.singletonMap("wf_variable", "value1"));
    WorkflowExecution workflowExecution =
        JsonUtils.readResourceFile("execution/runtime_pipeline_execution_stage2.json", WorkflowExecution.class);
    workflowExecution.setServiceIds(null);
    workflowExecution.setInfraDefinitionIds(null);
    workflowExecution.setEnvIds(null);
    workflowExecution.getExecutionArgs().setWorkflowVariables(new HashMap<>());

    Pipeline pipeline = JsonUtils.readResourceFile("pipeline/k8s_two_stage_pipeline_without_vars.json", Pipeline.class);
    pipeline.getPipelineStages().get(1).getPipelineStageElements().get(0).getProperties().remove("envId");
    Workflow workflow = JsonUtils.readResourceFile("workflows/k8s_workflow.json", Workflow.class);
    ((CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).setUserVariables(new ArrayList<>());
    Artifact artifact = JsonUtils.readResourceFile("artifacts/artifacts.json", Artifact.class);
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                        .uuid("D7fBZxZyQniDAhWTHdnYHQ")
                                                        .appId("nCLN8c84SqWPr44sqg65JQ")
                                                        .status(ExecutionStatus.PAUSED)
                                                        .executionUuid("dTFHGyWOTMSHXGQoI5kVKw")
                                                        .contextElements(new LinkedList<>())
                                                        .build();

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams params = new WorkflowStandardParams();

    when(artifactService.get(anyString(), anyString())).thenReturn(artifact);
    when(wingsPersistence.getWithAppId(eq(WorkflowExecution.class), eq(appID), eq(pipelineExecutionId)))
        .thenReturn(workflowExecution);
    when(pipelineService.readPipelineWithVariables(eq(appID), anyString())).thenReturn(pipeline);
    when(pipelineService.getPipeline(eq(appID), anyString())).thenReturn(pipeline);
    doNothing().when(deploymentAuthHandler).authorizePipelineExecution(anyString(), anyString());
    doNothing().when(authService).checkIfUserAllowedToDeployPipelineToEnv(anyString(), anyString());
    when(workflowService.readWorkflow(eq(appID), anyString())).thenReturn(workflow);

    Query query = mock(Query.class);
    when(wingsPersistence.createQuery(eq(StateExecutionInstance.class))).thenReturn(query);
    when(query.filter(anyString(), any())).thenReturn(query);
    when(query.get()).thenReturn(stateExecutionInstance);
    when(stateMachineExecutor.getExecutionContext(anyString(), anyString(), anyString())).thenReturn(context);
    when(context.getContextElement(eq(ContextElementType.STANDARD))).thenReturn(params);
    when(wingsPersistence.createUpdateOperations(eq(StateExecutionInstance.class)))
        .thenReturn(mock(UpdateOperations.class));
    // Test that updates are correct
    UpdateOperations updateOperations = mock(UpdateOperations.class);
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(wingsPersistence.createUpdateOperations(eq(WorkflowExecution.class))).thenReturn(updateOperations);
    when(updateOperations.set(anyString(), any())).thenReturn(updateOperations);
    doNothing().when(workflowExecutionServiceSpy).refreshPipelineExecution(workflowExecution);
    when(featureFlagService.isEnabled(
             eq(FeatureName.SPG_ALLOW_REFRESH_PIPELINE_EXECUTION_BEFORE_CONTINUE_PIPELINE), any()))
        .thenReturn(false);
    workflowExecutionServiceSpy.continuePipelineStage(
        appID, pipelineExecutionId, pipelineStageElementId, executionArgs);

    List<Artifact> expectedArtifacts = JsonUtils.readResourceFile(
        "artifacts/expected_artifacts_continue_pipeline.json", new TypeReference<List<Artifact>>() {});

    List<ArtifactVariable> expectedArtifactVars = JsonUtils.readResourceFile(
        "artifacts/expected_artifact_variables_continue_pipeline.json", new TypeReference<List<ArtifactVariable>>() {});

    verify(updateOperations).set(eq(WorkflowExecutionKeys.startTs), anyLong());
    verify(updateOperations).set(eq(WorkflowExecutionKeys.executionArgs_artifact_variables), eq(expectedArtifactVars));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.artifacts), eq(expectedArtifacts));
    verify(updateOperations).set(eq(WorkflowExecutionKeys.executionArgs_artifacts), eq(expectedArtifacts));
    verify(updateOperations, never()).set(eq(WorkflowExecutionKeys.serviceIds), anyList());
    verify(updateOperations, never()).set(eq(WorkflowExecutionKeys.infraDefinitionIds), anyList());
    verify(updateOperations, never()).set(eq(WorkflowExecutionKeys.envIds), anyList());
    verify(workflowExecutionServiceSpy, never()).refreshPipelineExecution(any());
  }

  @Test
  @Owner(developers = {DEEPAK_PUTHRAYA})
  @Category(UnitTests.class)
  public void testGetWFVariablesForPipelineVars() {
    final String pipelineStageElementId = "KZPqXENuRbKqkuISe07JAQ";
    final String appID = "nCLN8c84SqWPr44sqg65JQ";

    Workflow workflow = JsonUtils.readResourceFile("workflows/k8s_workflow.json", Workflow.class);
    Pipeline pipeline = JsonUtils.readResourceFile("pipeline/k8s_two_stage_pipeline_without_vars.json", Pipeline.class);
    final Map<String, String> pipelineVars = ImmutableMap.<String, String>builder()
                                                 .put("pipelineInfra", "CzcfKUN2Q_eCrlrV66r4ug")
                                                 .put("service1", "NA2uRPKLTqm9VU3dPENb-g")
                                                 .build();
    when(pipelineService.getPipeline(eq(appID), anyString())).thenReturn(pipeline);

    final Map<String, String> wfVars = ImmutableMap.<String, String>builder()
                                           .put("env", "imRBOGz2ReyY89dr4K-vrQ")
                                           .put("pipelineInfra", "CzcfKUN2Q_eCrlrV66r4ug")
                                           .put("pipelineBuildNumber", "123456")
                                           .put("xxz", "hello world")
                                           .build();

    WorkflowExecution wfExecution =
        WorkflowExecution.builder().executionArgs(ExecutionArgs.builder().workflowVariables(wfVars).build()).build();

    Map<String, String> actual = workflowExecutionServiceSpy.getWFVarFromPipelineVar(pipelineVars, wfExecution,
        Pipeline.builder().appId(appID).uuid(PIPELINE_ID).build(), workflow, pipelineStageElementId);
    Map<String, String> expected = ImmutableMap.<String, String>builder()
                                       .put("AppDynamics_Server", "1E9uuNwoTKq6go8vOz6oRA")
                                       .put("AppDynamics_Tier", "214198")
                                       .put("Environment", "imRBOGz2ReyY89dr4K-vrQ")
                                       .put("InfraDefinition_KUBERNETES", "CzcfKUN2Q_eCrlrV66r4ug")
                                       .put("xxz", "hello world")
                                       .put("message", "Stage 1 message")
                                       .put("AppDynamics_Application", "15588")
                                       .put("buildNumber", "123456")
                                       .put("ServiceId", "NA2uRPKLTqm9VU3dPENb-g")
                                       .build();

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = {SRINIVAS})
  @Category(UnitTests.class)
  public void shouldCheckWorkflowExecutionStatusInFinalStatus() {
    Query query = mock(Query.class);
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(query.filter(anyString(), anyString())).thenReturn(query);
    when(query.project(anyString(), anyBoolean())).thenReturn(query);

    when(query.get()).thenReturn(WorkflowExecution.builder().status(ExecutionStatus.SUCCESS).build());
    assertThat(workflowExecutionService.checkWorkflowExecutionInFinalStatus(APP_ID, WORKFLOW_EXECUTION_ID)).isTrue();

    when(query.get()).thenReturn(WorkflowExecution.builder().status(ExecutionStatus.RUNNING).build());
    assertThat(workflowExecutionService.checkWorkflowExecutionInFinalStatus(APP_ID, WORKFLOW_EXECUTION_ID)).isFalse();
  }

  @Test
  @Owner(developers = {SRINIVAS})
  @Category(UnitTests.class)
  public void shouldFetchWorkflowExecutionStatus() {
    Query query = mock(Query.class);
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(query.filter(anyString(), anyString())).thenReturn(query);
    when(query.project(anyString(), anyBoolean())).thenReturn(query);

    when(query.get()).thenReturn(WorkflowExecution.builder().status(ExecutionStatus.SUCCESS).build());
    assertThat(workflowExecutionService.fetchWorkflowExecutionStatus(APP_ID, WORKFLOW_EXECUTION_ID))
        .isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = {SRINIVAS})
  @Category(UnitTests.class)
  public void shouldFetchWorkflowExecutionWithFilter() {
    Query query = mock(Query.class);
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(query.filter(anyString(), anyString())).thenReturn(query);
    when(query.project(anyString(), anyBoolean())).thenReturn(query);

    when(query.get()).thenReturn(WorkflowExecution.builder().status(ExecutionStatus.SUCCESS).envId(ENV_ID).build());
    assertThat(workflowExecutionService.fetchWorkflowExecution(
                   APP_ID, WORKFLOW_EXECUTION_ID, WorkflowExecutionKeys.status, WorkflowExecutionKeys.envId))
        .isNotNull()
        .extracting(WorkflowExecutionKeys.envId)
        .isEqualTo(ENV_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = {SRINIVAS})
  @Category(UnitTests.class)
  public void shouldFetchWorkflowExecutionThrowException() {
    Query query = mock(Query.class);
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(query.filter(anyString(), anyString())).thenReturn(query);
    when(query.project(anyString(), anyBoolean())).thenReturn(query);

    when(query.get()).thenReturn(null);
    workflowExecutionService.fetchWorkflowExecution(
        APP_ID, WORKFLOW_EXECUTION_ID, WorkflowExecutionKeys.status, WorkflowExecutionKeys.envId);
  }

  @Test
  @Owner(developers = {SRINIVAS})
  @Category(UnitTests.class)
  public void shouldFetchWorkflowExecutionWithoutProjectedFields() {
    Query query = mock(Query.class);
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(query.filter(anyString(), anyString())).thenReturn(query);
    when(query.project(anyString(), anyBoolean())).thenReturn(query);

    when(query.get()).thenReturn(WorkflowExecution.builder().status(ExecutionStatus.SUCCESS).envId(ENV_ID).build());
    assertThat(workflowExecutionService.fetchWorkflowExecution(
                   APP_ID, WORKFLOW_EXECUTION_ID, WorkflowExecutionKeys.status, WorkflowExecutionKeys.envId))
        .isNotNull()
        .extracting(WorkflowExecutionKeys.envId)
        .isEqualTo(ENV_ID);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldFetchFailureDetails() {
    assertThat(workflowExecutionService.fetchFailureDetails(APP_ID, WORKFLOW_EXECUTION_ID)).isEqualTo("failureDetails");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldPopulateFailureDetailsForASingleWorkflowExecution() {
    WorkflowExecution workflowExecution = getFailedOrchestrationWorkflowExecution();
    workflowExecutionService.populateFailureDetails(workflowExecution);
    assertThat(workflowExecution.getFailureDetails()).isEqualTo("failureDetails");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotPopulateFailureDetailsForSuccessfulWorkflowExecution() {
    WorkflowExecution workflowExecution = getSuccessfulOrchestrationWorkflowExecution();
    workflowExecutionService.populateFailureDetails(workflowExecution);
    assertThat(workflowExecution.getFailureDetails()).isNull();
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldPopulateFailureDetailsForEachWorkflowExecutionWithinPipeline() {
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .uuid(WORKFLOW_EXECUTION_ID)
            .appId(APP_ID)
            .workflowType(WorkflowType.PIPELINE)
            .pipelineExecution(PipelineExecution.Builder.aPipelineExecution()
                                   .withPipelineStageExecutions(singletonList(
                                       PipelineStageExecution.builder()
                                           .workflowExecutions(asList(getFailedOrchestrationWorkflowExecution(),
                                               getFailedOrchestrationWorkflowExecution()))
                                           .build()))
                                   .build())
            .build();
    workflowExecutionService.populateFailureDetails(workflowExecution);
    assertThat(workflowExecution.getPipelineExecution().getPipelineStageExecutions().get(0).getWorkflowExecutions())
        .allMatch(execution -> execution.getFailureDetails().equals("failureDetails"));
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldNotPopulateFailureDetailsForSuccessfulExecutionWithinPipeline() {
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .uuid(WORKFLOW_EXECUTION_ID)
            .appId(APP_ID)
            .workflowType(WorkflowType.PIPELINE)
            .pipelineExecution(PipelineExecution.Builder.aPipelineExecution()
                                   .withPipelineStageExecutions(singletonList(
                                       PipelineStageExecution.builder()
                                           .workflowExecutions(asList(getFailedOrchestrationWorkflowExecution(),
                                               getSuccessfulOrchestrationWorkflowExecution()))
                                           .build()))
                                   .build())
            .build();
    workflowExecutionService.populateFailureDetails(workflowExecution);
    WorkflowExecution failedExecution =
        workflowExecution.getPipelineExecution().getPipelineStageExecutions().get(0).getWorkflowExecutions().get(0);
    WorkflowExecution successfulExecution =
        workflowExecution.getPipelineExecution().getPipelineStageExecutions().get(0).getWorkflowExecutions().get(1);
    assertThat(failedExecution.getFailureDetails()).isEqualTo("failureDetails");
    assertThat(successfulExecution.getFailureDetails()).isNull();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void shouldPopulateFailureDetailsForRejectedExecutionWithinPipeline() {
    WorkflowExecution workflowExecution =
        WorkflowExecution.builder()
            .uuid(WORKFLOW_EXECUTION_ID)
            .appId(APP_ID)
            .workflowType(WorkflowType.PIPELINE)
            .pipelineExecution(PipelineExecution.Builder.aPipelineExecution()
                                   .withPipelineStageExecutions(
                                       singletonList(PipelineStageExecution.builder()
                                                         .workflowExecutions(asList(getRejectedWorkflowExecution()))
                                                         .build()))
                                   .build())
            .build();
    workflowExecutionService.populateFailureDetails(workflowExecution);
    WorkflowExecution rejectedExecution =
        workflowExecution.getPipelineExecution().getPipelineStageExecutions().get(0).getWorkflowExecutions().get(0);
    assertThat(rejectedExecution.getFailureDetails()).isEqualTo("failureDetails");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldNotPopulateTriggeredByForEnvLoopState() {
    ForkStateExecutionData forkStateExecutionData = new ForkStateExecutionData();
    forkStateExecutionData.setForkStateNames(Collections.singletonList("stage_1"));
    EnvStateExecutionData envStateExecutionData =
        anEnvStateExecutionData().withStatus(FAILED).withWorkflowExecutionId(WORKFLOW_EXECUTION_ID).build();
    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().addStateExecutionData(envStateExecutionData).build();

    doReturn(WorkflowExecution.builder()
                 .status(FAILED)
                 .triggeredBy(EmbeddedUser.builder().name("admin").email("admin@harness.io").build())
                 .build())
        .when(workflowExecutionServiceSpy)
        .getExecutionDetailsWithoutGraph(APP_ID, WORKFLOW_EXECUTION_ID);
    List<PipelineStageExecution> stageExecutionList = new ArrayList<>();
    workflowExecutionServiceSpy.handleEnvLoopStateExecutionData(APP_ID,
        ImmutableMap.of("stage_1", stateExecutionInstance), stageExecutionList, forkStateExecutionData,
        mock(PipelineStageElement.class), null);
    assertThat(stageExecutionList).hasSize(1);
    assertThat(stageExecutionList.get(0).getStatus()).isEqualTo(FAILED);
    assertThat(stageExecutionList.get(0).getTriggeredBy().getEmail()).isEqualTo("admin@harness.io");
    assertThat(stageExecutionList.get(0).getTriggeredBy().getName()).isEqualTo("admin");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetPreviousApprovalDetails() {
    String approvalId = generateUuid();
    WorkflowExecution currentWorkflowExecution =
        builder()
            .infraDefinitionIds(asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2))
            .serviceIds(asList(SERVICE1_ID, SERVICE2_ID))
            .pipelineExecution(
                aPipelineExecution()
                    .withPipelineStageExecutions(
                        asList(PipelineStageExecution.builder()
                                   .status(PAUSED)
                                   .stateType(StateType.APPROVAL.name())
                                   .stateExecutionData(
                                       ApprovalStateExecutionData.builder().approvalId(APPROVAL_EXECUTION_ID).build())
                                   .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID)
                                   .build()))
                    .withStatus(PAUSED)
                    .build())
            .build();

    WorkflowExecution previousWorkflowExecution1 =
        builder()
            .infraDefinitionIds(asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2))
            .serviceIds(asList(SERVICE1_ID))
            .pipelineExecution(
                aPipelineExecution()
                    .withPipelineStageExecutions(asList(PipelineStageExecution.builder()
                                                            .status(PAUSED)
                                                            .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID)
                                                            .build()))
                    .withStatus(PAUSED)
                    .build())
            .build();

    WorkflowExecution previousWorkflowExecution2 =
        builder()
            .infraDefinitionIds(asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2))
            .serviceIds(asList(SERVICE1_ID, SERVICE2_ID))
            .pipelineExecution(
                aPipelineExecution()
                    .withPipelineStageExecutions(asList(PipelineStageExecution.builder()
                                                            .status(PAUSED)
                                                            .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID + 1)
                                                            .build()))
                    .withStatus(PAUSED)
                    .build())
            .build();

    WorkflowExecution previousWorkflowExecution3 =
        builder()
            .infraDefinitionIds(asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2))
            .serviceIds(asList(SERVICE1_ID, SERVICE2_ID))
            .pipelineExecution(
                aPipelineExecution()
                    .withPipelineStageExecutions(
                        asList(PipelineStageExecution.builder()
                                   .status(PAUSED)
                                   .pipelineStageElementId(PIPELINE_STAGE_ELEMENT_ID)
                                   .stateType(StateType.APPROVAL.name())
                                   .stateExecutionData(
                                       ApprovalStateExecutionData.builder().approvalId(APPROVAL_EXECUTION_ID).build())
                                   .build()))
                    .withStatus(PAUSED)
                    .build())
            .build();

    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);

    Query query = mock(Query.class);
    FieldEnd fieldEnd = mock(FieldEnd.class);
    when(wingsPersistence.createQuery(eq(WorkflowExecution.class))).thenReturn(query);
    when(query.filter(anyString(), anyString())).thenReturn(query);
    when(query.project(anyString(), anyBoolean())).thenReturn(query);
    when(query.order(any(Sort.class))).thenReturn(query);
    when(query.limit(anyInt())).thenReturn(query);
    when(query.field(anyString())).thenReturn(fieldEnd);
    when(fieldEnd.lessThan(anyLong())).thenReturn(query);
    when(fieldEnd.in(any())).thenReturn(query);
    when(query.get()).thenReturn(currentWorkflowExecution);
    when(query.asList())
        .thenReturn(asList(previousWorkflowExecution1, previousWorkflowExecution2, previousWorkflowExecution3));

    PreviousApprovalDetails previousApprovalDetails = workflowExecutionService.getPreviousApprovalDetails(
        APP_ID, WORKFLOW_EXECUTION_ID, PIPELINE_ID, APPROVAL_EXECUTION_ID);
    assertThat(previousApprovalDetails.getSize()).isEqualTo(1);
    assertThat(previousApprovalDetails.getPreviousApprovals().get(0).getApprovalId()).isEqualTo(APPROVAL_EXECUTION_ID);
    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldApproveAndRejectPreviousExecutions() {
    String approvalId = generateUuid();
    ApprovalDetails approvalDetails = new ApprovalDetails();
    approvalDetails.setApprovalId(approvalId);
    approvalDetails.setAction(Action.APPROVE);

    PreviousApprovalDetails previousApprovalDetails =
        PreviousApprovalDetails.builder()
            .size(2)
            .previousApprovals(asList(ApprovalInfo.builder().approvalId(approvalId + 2).build(),
                ApprovalInfo.builder().approvalId(approvalId + 3).build()))
            .build();

    User user = createUser(USER_ID);
    saveUserToPersistence(user);
    UserGroup userGroup = createUserGroup(asList(user.getUuid()));
    saveUserGroupToPersistence(userGroup);

    when(wingsPersistence.query(eq(StateExecutionInstance.class), any()))
        .thenReturn(aPageResponse().withResponse(Collections.emptyList()).build());

    ApprovalStateExecutionData stateExecutionData =
        ApprovalStateExecutionData.builder().currentStatus(PAUSED.name()).approvalId(approvalId).build();
    stateExecutionData.setStatus(PAUSED);
    UserThreadLocal.set(user);
    when(appService.getAccountIdByAppId(APP_ID)).thenReturn(ACCOUNT_ID);
    WorkflowExecution workflowExecution =
        builder()
            .workflowType(WorkflowType.PIPELINE)
            .status(PAUSED)
            .pipelineExecution(
                aPipelineExecution()
                    .withPipelineStageExecutions(asList(
                        PipelineStageExecution.builder().status(PAUSED).stateExecutionData(stateExecutionData).build()))
                    .build())
            .build();
    Query<WorkflowExecution> query = mock(Query.class);
    when(wingsPersistence.createQuery(WorkflowExecution.class)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.appId, APP_ID)).thenReturn(query);
    when(query.filter(WorkflowExecutionKeys.uuid, WORKFLOW_EXECUTION_ID)).thenReturn(query);
    when(query.get()).thenReturn(workflowExecution);
    when(featureFlagService.isEnabled(AUTO_REJECT_PREVIOUS_APPROVALS, ACCOUNT_ID)).thenReturn(true);
    when(subdomainUrlHelper.getPortalBaseUrl(anyString())).thenReturn("https://dummyurl");

    doNothing().when(workflowExecutionServiceSpy).refreshPipelineExecution(workflowExecution);
    workflowExecutionServiceSpy.approveAndRejectPreviousExecutions(
        ACCOUNT_ID, APP_ID, WORKFLOW_EXECUTION_ID, STATE_EXECUTION_ID, approvalDetails, previousApprovalDetails);
    ArgumentCaptor<ResponseData> captor1 = ArgumentCaptor.forClass(ResponseData.class);
    ArgumentCaptor<ResponseData> captor2 = ArgumentCaptor.forClass(ResponseData.class);
    ArgumentCaptor<ResponseData> captor3 = ArgumentCaptor.forClass(ResponseData.class);
    verify(waitNotifyEngine).doneWith(eq(approvalId), captor1.capture());
    verify(waitNotifyEngine).doneWith(eq(approvalId + 2), captor2.capture());
    verify(waitNotifyEngine).doneWith(eq(approvalId + 3), captor3.capture());
    assertThat(captor1.getValue()).isInstanceOf(ApprovalStateExecutionData.class);
    assertThat(captor2.getValue()).isInstanceOf(ApprovalStateExecutionData.class);
    assertThat(captor3.getValue()).isInstanceOf(ApprovalStateExecutionData.class);
    assertThat(((ApprovalStateExecutionData) captor1.getValue()).getStatus()).isEqualTo(SUCCESS);
    assertThat(((ApprovalStateExecutionData) captor2.getValue()).getStatus()).isEqualTo(REJECTED);
    assertThat(((ApprovalStateExecutionData) captor3.getValue()).getStatus()).isEqualTo(REJECTED);
    assertThat(((ApprovalStateExecutionData) captor2.getValue()).getComments())
        .isEqualTo(
            "Pipeline rejected when the following execution was approved: https://dummyurl/#/account/ACCOUNT_ID/app/APP_ID/pipeline-execution/WORKFLOW_EXECUTION_ID/workflow-execution/undefined/details");
    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldNotAllowWorkflowExecutionWhenEnvNotAvailableForNonBuildWorkflow() {
    Workflow workflow = aWorkflow()
                            .appId("appId")
                            .name("workflowName")
                            .description("Sample Workflow")
                            .orchestrationWorkflow(aCanaryOrchestrationWorkflow().build())
                            .workflowType(WorkflowType.ORCHESTRATION)
                            .uuid("uuid")
                            .build();
    when(workflowExecutionServiceHelper.obtainWorkflow(workflow.getUuid(), workflow.getUuid())).thenReturn(workflow);

    assertThatThrownBy(()
                           -> workflowExecutionService.triggerOrchestrationWorkflowExecution(workflow.getUuid(), null,
                               workflow.getUuid(), null, ExecutionArgs.builder().build(), null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Environment is not provided in the workflow");
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldUpdateWorkflowWithArtifactCollectionSteps() {
    Workflow workflow = constructCanaryWorkflowWithPhase();
    assertThat(workflow).isNotNull().hasFieldOrProperty("uuid");
    List<ArtifactInput> artifactInputs =
        Collections.singletonList(ArtifactInput.builder().buildNo("build1").artifactStreamId("id").build());

    OrchestrationWorkflow orchestrationWorkflow =
        workflowExecutionServiceSpy.updateWorkflowWithArtifactCollectionSteps(workflow, artifactInputs, null);
    assertThat(orchestrationWorkflow).isNotNull().isInstanceOf(CanaryOrchestrationWorkflow.class);
    CanaryOrchestrationWorkflow canaryOrchestrationWorkflow = (CanaryOrchestrationWorkflow) orchestrationWorkflow;
    PhaseStep preDeploymentSteps = canaryOrchestrationWorkflow.getPreDeploymentSteps();
    assertThat(preDeploymentSteps).isNotNull();
    assertThat(preDeploymentSteps.getSteps()).isNotNull().isNotEmpty();

    GraphNode graphNode = preDeploymentSteps.getSteps().get(0);
    assertThat(graphNode.getType()).isEqualTo(ARTIFACT_COLLECT_LOOP_STATE.getType());
    assertThat(graphNode.getName()).isEqualTo("Artifact/Manifest Collection");
    assertThat(graphNode.getProperties()).isNotNull().isNotEmpty();
    assertThat(graphNode.getProperties().get(ArtifactCollectLoopStateKeys.artifactInputList)).isEqualTo(artifactInputs);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldAddArtifactInputsToContext() {
    ArtifactVariable artifactVariable =
        ArtifactVariable.builder()
            .artifactInput(ArtifactInput.builder().buildNo("1").artifactStreamId(ARTIFACT_STREAM_ID + 1).build())
            .build();
    ArtifactVariable artifactVariable2 =
        ArtifactVariable.builder()
            .artifactInput(ArtifactInput.builder().buildNo("2").artifactStreamId(ARTIFACT_STREAM_ID + 2).build())
            .build();
    ArtifactVariable artifactVariable3 = ArtifactVariable.builder().build();
    WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    List<ArtifactVariable> artifactVariables = asList(artifactVariable, artifactVariable2, artifactVariable3);
    workflowExecutionServiceSpy.addArtifactInputsToContext(artifactVariables, workflowStandardParams);
    assertThat(workflowStandardParams.getArtifactInputs()).isNotNull().isNotEmpty().hasSize(2);
    assertThat(workflowStandardParams.getArtifactInputs())
        .isEqualTo(asList(artifactVariable.getArtifactInput(), artifactVariable2.getArtifactInput()));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetPreviousApprovalDetailsForWorkflow() {
    String approvalStateIdentifier = generateUuid();
    WorkflowExecution currentWorkflowExecution =
        builder()
            .uuid(WORKFLOW_EXECUTION_ID)
            .infraDefinitionIds(asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2))
            .serviceIds(asList(SERVICE1_ID, SERVICE2_ID))
            .appId(APP_ID)
            .build();

    WorkflowExecution previousWorkflowExecution1 =
        builder()
            .appId(APP_ID)
            .uuid(WORKFLOW_EXECUTION_ID + 2)
            .infraDefinitionIds(asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2))
            .serviceIds(asList(SERVICE1_ID))
            .build();

    WorkflowExecution previousWorkflowExecution2 = builder()
                                                       .appId(APP_ID)
                                                       .uuid(WORKFLOW_EXECUTION_ID + 3)
                                                       .infraDefinitionIds(asList(INFRA_DEFINITION_ID))
                                                       .serviceIds(asList(SERVICE1_ID, SERVICE2_ID))
                                                       .build();

    WorkflowExecution previousWorkflowExecution3 =
        builder()
            .appId(APP_ID)
            .uuid(WORKFLOW_EXECUTION_ID + 4)
            .infraDefinitionIds(asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2))
            .serviceIds(asList(SERVICE1_ID, SERVICE2_ID))
            .build();
    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId(APPROVAL_EXECUTION_ID).build();
    approvalStateExecutionData.setApprovalStateIdentifier(approvalStateIdentifier);
    doReturn(asList(approvalStateExecutionData,
                 ApprovalStateExecutionData.builder().approvalId(APPROVAL_EXECUTION_ID).build()))
        .when(workflowExecutionServiceSpy)
        .fetchApprovalStateExecutionsDataFromWorkflowExecution(any(), any());

    List<String> approvalIds = workflowExecutionServiceSpy.getPreviousApprovalIdsWithSameServicesAndInfraForWorkflow(
        currentWorkflowExecution,
        asList(previousWorkflowExecution1, previousWorkflowExecution2, previousWorkflowExecution3),
        asList(SERVICE1_ID, SERVICE2_ID), asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2), APPROVAL_EXECUTION_ID);
    assertThat(approvalIds).hasSize(1);
    assertThat(approvalIds.get(0)).isEqualTo(APPROVAL_EXECUTION_ID);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testShouldNotGetApprovalIdsForWorkflowInSamePipelineExecution() {
    String approvalStateIdentifier = generateUuid();
    WorkflowExecution currentWorkflowExecution =
        builder()
            .uuid(WORKFLOW_EXECUTION_ID)
            .infraDefinitionIds(asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2))
            .serviceIds(asList(SERVICE1_ID, SERVICE2_ID))
            .pipelineExecutionId(PIPELINE_EXECUTION_ID)
            .appId(APP_ID)
            .build();

    WorkflowExecution previousWorkflowExecution1 =
        builder()
            .appId(APP_ID)
            .uuid(WORKFLOW_EXECUTION_ID + 2)
            .infraDefinitionIds(asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2))
            .serviceIds(asList(SERVICE1_ID, SERVICE2_ID))
            .pipelineExecutionId(PIPELINE_EXECUTION_ID)
            .build();

    WorkflowExecution previousWorkflowExecution2 =
        builder()
            .appId(APP_ID)
            .uuid(WORKFLOW_EXECUTION_ID + 3)
            .infraDefinitionIds(asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2))
            .serviceIds(asList(SERVICE1_ID, SERVICE2_ID))
            .build();

    WorkflowExecution previousWorkflowExecution3 =
        builder()
            .appId(APP_ID)
            .uuid(WORKFLOW_EXECUTION_ID + 4)
            .infraDefinitionIds(asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2))
            .serviceIds(asList(SERVICE1_ID, SERVICE2_ID))
            .build();
    ApprovalStateExecutionData approvalStateExecutionData =
        ApprovalStateExecutionData.builder().approvalId(APPROVAL_EXECUTION_ID).build();
    approvalStateExecutionData.setApprovalStateIdentifier(approvalStateIdentifier);
    doReturn(asList(approvalStateExecutionData,
                 ApprovalStateExecutionData.builder().approvalId(APPROVAL_EXECUTION_ID + 4).build()))
        .when(workflowExecutionServiceSpy)
        .fetchApprovalStateExecutionsDataFromWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID);
    doReturn(asList(approvalStateExecutionData,
                 ApprovalStateExecutionData.builder().approvalId(APPROVAL_EXECUTION_ID + 3).build()))
        .when(workflowExecutionServiceSpy)
        .fetchApprovalStateExecutionsDataFromWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID + 2);
    doReturn(asList(ApprovalStateExecutionData.builder().approvalId(APPROVAL_EXECUTION_ID + 2).build()))
        .when(workflowExecutionServiceSpy)
        .fetchApprovalStateExecutionsDataFromWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID + 3);
    doReturn(asList(approvalStateExecutionData,
                 ApprovalStateExecutionData.builder().approvalId(APPROVAL_EXECUTION_ID).build()))
        .when(workflowExecutionServiceSpy)
        .fetchApprovalStateExecutionsDataFromWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID + 4);

    List<String> approvalIds = workflowExecutionServiceSpy.getPreviousApprovalIdsWithSameServicesAndInfraForWorkflow(
        currentWorkflowExecution,
        asList(previousWorkflowExecution1, previousWorkflowExecution2, previousWorkflowExecution3),
        asList(SERVICE1_ID, SERVICE2_ID), asList(INFRA_DEFINITION_ID, INFRA_DEFINITION_ID + 2), APPROVAL_EXECUTION_ID);
    assertThat(approvalIds).hasSize(1);
    assertThat(approvalIds.get(0)).isEqualTo(APPROVAL_EXECUTION_ID);
  }

  private WorkflowExecution getFailedOrchestrationWorkflowExecution() {
    return WorkflowExecution.builder()
        .uuid(WORKFLOW_EXECUTION_ID)
        .appId(APP_ID)
        .status(FAILED)
        .workflowType(WorkflowType.ORCHESTRATION)
        .build();
  }

  private WorkflowExecution getSuccessfulOrchestrationWorkflowExecution() {
    return WorkflowExecution.builder()
        .uuid(WORKFLOW_EXECUTION_ID)
        .appId(APP_ID)
        .status(SUCCESS)
        .workflowType(WorkflowType.ORCHESTRATION)
        .build();
  }

  private WorkflowExecution getRejectedWorkflowExecution() {
    return WorkflowExecution.builder()
        .uuid(WORKFLOW_EXECUTION_ID)
        .appId(APP_ID)
        .status(REJECTED)
        .workflowType(WorkflowType.PIPELINE)
        .build();
  }
}
