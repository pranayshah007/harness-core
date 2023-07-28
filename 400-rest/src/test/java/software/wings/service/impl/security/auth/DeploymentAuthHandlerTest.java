/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security.auth;

import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Pipeline;
import software.wings.beans.User;
import software.wings.beans.User.Builder;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.security.UserRequestContext;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.PipelineSummary;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeploymentAuthHandlerTest extends WingsBaseTest {
  @Mock private AuthService authService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private WorkflowService workflowService;
  @Mock private PipelineService pipelineService;
  @Mock private FeatureFlagService featureFlagService;
  @InjectMocks @Inject private DeploymentAuthHandler deploymentAuthHandler;

  private static final String appId = generateUuid();
  private static final String envId = generateUuid();
  private static final String entityId = generateUuid();

  User user;

  @Before
  public void setUp() {
    UserRequestContext userRequestContext = UserRequestContext.builder().accountId(generateUuid()).build();
    user = Builder.anUser().uuid(generateUuid()).userRequestContext(userRequestContext).build();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowExecutionWithId() {
    try {
      UserThreadLocal.set(user);
      PipelineSummary pipelineSummary =
          PipelineSummary.builder().pipelineId(entityId).pipelineName("pipeline-name").build();
      WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                                .uuid(generateUuid())
                                                .pipelineSummary(pipelineSummary)
                                                .workflowId(entityId)
                                                .envId(generateUuid())
                                                .build();

      when(workflowExecutionService.getExecutionDetailsWithoutGraph(appId, workflowExecution.getUuid()))
          .thenReturn(workflowExecution);
      doNothing().when(authService).checkIfUserAllowedToDeployPipelineToEnv(appId, workflowExecution.getEnvId());
      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());

      deploymentAuthHandler.authorize(appId, workflowExecution.getUuid());
    } catch (Exception e) {
      assertThat(e).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowExecutionWithIdNullEnvId() {
    try {
      UserThreadLocal.set(user);
      PipelineSummary pipelineSummary =
          PipelineSummary.builder().pipelineId(entityId).pipelineName("pipeline-name").build();
      WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                                .uuid(generateUuid())
                                                .workflowType(PIPELINE)
                                                .pipelineSummary(pipelineSummary)
                                                .workflowId(entityId)
                                                .build();

      when(workflowExecutionService.getExecutionDetailsWithoutGraph(appId, workflowExecution.getUuid()))
          .thenReturn(workflowExecution);
      doNothing().when(authService).checkIfUserAllowedToDeployPipelineToEnv(appId, workflowExecution.getEnvId());
      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());

      deploymentAuthHandler.authorize(appId, workflowExecution.getUuid());
    } catch (Exception e) {
      assertThat(e).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowExecutionWithIdNullSummary() {
    try {
      UserThreadLocal.set(user);
      WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                                .uuid(generateUuid())
                                                .workflowType(ORCHESTRATION)
                                                .workflowId(entityId)
                                                .envId(generateUuid())
                                                .build();

      when(workflowExecutionService.getExecutionDetailsWithoutGraph(appId, workflowExecution.getUuid()))
          .thenReturn(workflowExecution);
      doNothing().when(authService).checkIfUserAllowedToDeployWorkflowToEnv(appId, workflowExecution.getEnvId());
      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());

      deploymentAuthHandler.authorize(appId, workflowExecution.getUuid());
    } catch (Exception e) {
      assertThat(e).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowExecutionWithIdNullEnvIdNullSummary() {
    try {
      UserThreadLocal.set(user);
      WorkflowExecution workflowExecution =
          WorkflowExecution.builder().uuid(generateUuid()).workflowId(entityId).build();

      when(workflowExecutionService.getExecutionDetailsWithoutGraph(appId, workflowExecution.getUuid()))
          .thenReturn(workflowExecution);
      doNothing().when(authService).checkIfUserAllowedToDeployWorkflowToEnv(appId, workflowExecution.getEnvId());
      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());

      deploymentAuthHandler.authorize(appId, workflowExecution.getUuid());
    } catch (Exception e) {
      assertThat(e).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowExecution() {
    try {
      UserThreadLocal.set(user);
      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorizeWorkflowExecution(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowExecutionWithNullUser() {
    try {
      UserThreadLocal.set(null);
      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorizeWorkflowExecution(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowExecutionWithUserNotAuthorized() {
    try {
      UserThreadLocal.set(user);
      doThrow(new Exception(ErrorCode.ACCESS_DENIED.getDescription()))
          .when(authService)
          .authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorizeWorkflowExecution(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNotNull();
      assertThat(e.getMessage()).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizePipelineExecution() {
    try {
      UserThreadLocal.set(user);
      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorizePipelineExecution(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizePipelineExecutionWithNullUser() {
    try {
      UserThreadLocal.set(null);
      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorizePipelineExecution(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizePipelineExecutionWithUserNotAuthorized() {
    try {
      UserThreadLocal.set(user);
      doThrow(new Exception(ErrorCode.ACCESS_DENIED.getDescription()))
          .when(authService)
          .authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorizePipelineExecution(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNotNull();
      assertThat(e.getMessage()).isNotNull();

    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowOrPipelineForExecutionForWorkflowUnauthorizedUser() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);
    try {
      UserThreadLocal.set(user);
      when(workflowService.getWorkflow(appId, entityId)).thenReturn(workflow);

      doThrow(new Exception(ErrorCode.ACCESS_DENIED.getDescription()))
          .when(authService)
          .authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorizeWorkflowOrPipelineForExecution(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNotNull();
      assertThat(e.getMessage()).isNotNull();

    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowOrPipelineForExecutionForWorkflowNullUser() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);
    try {
      UserThreadLocal.set(null);
      when(workflowService.getWorkflow(appId, entityId)).thenReturn(workflow);

      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorizeWorkflowOrPipelineForExecution(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNull();
      assertThat(e.getMessage()).isNull();

    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowOrPipelineForExecutionForWorkflowAuthlUser() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);
    try {
      UserThreadLocal.set(user);
      when(workflowService.getWorkflow(appId, entityId)).thenReturn(workflow);

      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorizeWorkflowOrPipelineForExecution(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNull();
      assertThat(e.getMessage()).isNull();

    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowOrPipelineForExecutionForPipelineWorkflowNull() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);

    Pipeline pipeline = new Pipeline();
    pipeline.setUuid(entityId);

    try {
      UserThreadLocal.set(user);
      when(workflowService.getWorkflow(appId, entityId)).thenReturn(null);
      when(pipelineService.getPipeline(appId, entityId)).thenReturn(null);
      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());

      deploymentAuthHandler.authorizeWorkflowOrPipelineForExecution(appId, entityId);
    } catch (InvalidRequestException e) {
      assertThat(e).isNotNull();
      assertThat(e.getMessage()).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowOrPipelineForExecutionForPipelineAuthUser() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);

    Pipeline pipeline = new Pipeline();
    pipeline.setUuid(entityId);

    try {
      UserThreadLocal.set(user);
      when(workflowService.getWorkflow(appId, entityId)).thenReturn(null);
      when(pipelineService.getPipeline(appId, entityId)).thenReturn(pipeline);
      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      doReturn(false)
          .when(featureFlagService)
          .isEnabled(eq(FeatureName.PIPELINE_PER_ENV_DEPLOYMENT_PERMISSION), anyString());

      deploymentAuthHandler.authorizeWorkflowOrPipelineForExecution(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNull();
      assertThat(e.getMessage()).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeWorkflowOrPipelineForExecutionForPipelineUnauthUser() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);

    Pipeline pipeline = new Pipeline();
    pipeline.setUuid(entityId);

    try {
      UserThreadLocal.set(user);
      when(workflowService.getWorkflow(appId, entityId)).thenReturn(null);
      when(pipelineService.getPipeline(appId, entityId)).thenReturn(pipeline);

      doThrow(new Exception(ErrorCode.ACCESS_DENIED.getDescription()))
          .when(authService)
          .authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorizeWorkflowOrPipelineForExecution(appId, entityId);
    } catch (Exception e) {
      assertThat(e).isNotNull();
      assertThat(e.getMessage()).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeFromWorkflowExecutionUnAuthuser() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);

    Pipeline pipeline = new Pipeline();
    pipeline.setUuid(entityId);

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .appId(appId)
                                              .uuid(generateUuid())
                                              .workflowType(ORCHESTRATION)
                                              .workflowId(entityId)
                                              .build();

    try {
      UserThreadLocal.set(user);
      when(workflowService.getWorkflow(appId, entityId)).thenReturn(null);
      when(pipelineService.getPipeline(appId, entityId)).thenReturn(pipeline);

      doThrow(new Exception(ErrorCode.ACCESS_DENIED.getDescription()))
          .when(authService)
          .authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorize(appId, workflowExecution);
      verify(authService, times(0)).checkIfUserAllowedToDeployWorkflowToEnv(eq(appId), anyString());
    } catch (Exception e) {
      assertThat(e).isNotNull();
      assertThat(e.getMessage()).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeFromWorkflowExecutionUnAuthuserForPipeline() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);

    Pipeline pipeline = new Pipeline();
    pipeline.setUuid(entityId);

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .appId(appId)
                                              .uuid(generateUuid())
                                              .workflowType(PIPELINE)
                                              .workflowId(entityId)
                                              .build();

    try {
      UserThreadLocal.set(user);
      when(workflowService.getWorkflow(appId, entityId)).thenReturn(null);
      when(pipelineService.getPipeline(appId, entityId)).thenReturn(pipeline);

      doThrow(new Exception(ErrorCode.ACCESS_DENIED.getDescription()))
          .when(authService)
          .authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      deploymentAuthHandler.authorize(appId, workflowExecution);
      verify(authService, times(0)).checkIfUserAllowedToDeployWorkflowToEnv(eq(appId), anyString());
    } catch (Exception e) {
      assertThat(e).isNotNull();
      assertThat(e.getMessage()).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeFromWorkflowExecutionIdUnAuthuser() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);

    Pipeline pipeline = new Pipeline();
    pipeline.setUuid(entityId);

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .appId(appId)
                                              .uuid(generateUuid())
                                              .workflowType(ORCHESTRATION)
                                              .workflowId(entityId)
                                              .build();

    try {
      UserThreadLocal.set(user);
      when(workflowService.getWorkflow(appId, entityId)).thenReturn(null);
      when(pipelineService.getPipeline(appId, entityId)).thenReturn(pipeline);

      doThrow(new Exception(ErrorCode.ACCESS_DENIED.getDescription()))
          .when(authService)
          .authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      when(workflowExecutionService.getWorkflowExecution(appId, workflowExecution.getUuid()))
          .thenReturn(workflowExecution);
      deploymentAuthHandler.authorizeWithWorkflowExecutionId(appId, workflowExecution.getUuid());
      verify(authService, times(0)).checkIfUserAllowedToDeployWorkflowToEnv(eq(appId), anyString());
    } catch (Exception e) {
      assertThat(e).isNotNull();
      assertThat(e.getMessage()).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeFromWorkflowExecutionIdAuthuser() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);

    Pipeline pipeline = new Pipeline();
    pipeline.setUuid(entityId);

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .envId(envId)
                                              .appId(appId)
                                              .uuid(generateUuid())
                                              .workflowType(ORCHESTRATION)
                                              .workflowId(entityId)
                                              .build();

    try {
      UserThreadLocal.set(user);
      when(workflowService.getWorkflow(appId, entityId)).thenReturn(null);
      when(pipelineService.getPipeline(appId, entityId)).thenReturn(pipeline);

      doThrow(new Exception(ErrorCode.ACCESS_DENIED.getDescription()))
          .when(authService)
          .authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      when(workflowExecutionService.getWorkflowExecution(appId, workflowExecution.getUuid()))
          .thenReturn(workflowExecution);
      doNothing().when(authService).checkIfUserAllowedToDeployWorkflowToEnv(appId, workflowExecution.getEnvId());
      deploymentAuthHandler.authorize(appId, workflowExecution);
      verify(authService, atLeastOnce())
          .checkIfUserAllowedToDeployWorkflowToEnv(eq(appId), eq(workflowExecution.getEnvId()));
    } catch (Exception e) {
      assertThat(e).isNotNull();
      assertThat(e.getMessage()).isNotNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testAuthorizeFromWorkflowExecutionIdAuthusers() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);

    Pipeline pipeline = new Pipeline();
    pipeline.setUuid(entityId);

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .envId(envId)
                                              .appId(appId)
                                              .uuid(generateUuid())
                                              .workflowType(ORCHESTRATION)
                                              .workflowId(entityId)
                                              .build();

    try {
      UserThreadLocal.set(user);
      when(workflowService.getWorkflow(appId, entityId)).thenReturn(null);
      when(pipelineService.getPipeline(appId, entityId)).thenReturn(pipeline);

      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());
      when(workflowExecutionService.getWorkflowExecution(
               eq(appId), eq(workflowExecution.getUuid()), any(String[].class)))
          .thenReturn(workflowExecution);
      doNothing().when(authService).checkIfUserAllowedToDeployWorkflowToEnv(appId, workflowExecution.getEnvId());
      deploymentAuthHandler.authorizeWithWorkflowExecutionId(appId, workflowExecution.getUuid());
      verify(authService, atLeastOnce())
          .checkIfUserAllowedToDeployWorkflowToEnv(eq(appId), eq(workflowExecution.getEnvId()));
    } catch (Exception e) {
      assertThat(e).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testAuthorizeFromRollbackWorkflowExecutionUnauthorizedUser() {
    Workflow workflow = new Workflow();
    workflow.setUuid(entityId);

    Pipeline pipeline = new Pipeline();
    pipeline.setUuid(entityId);

    WorkflowExecution workflowExecution = WorkflowExecution.builder()
                                              .appId(appId)
                                              .uuid(generateUuid())
                                              .workflowType(ORCHESTRATION)
                                              .workflowId(entityId)
                                              .build();

    UserThreadLocal.set(user);
    when(workflowService.getWorkflow(appId, entityId)).thenReturn(null);
    when(pipelineService.getPipeline(appId, entityId)).thenReturn(pipeline);

    doThrow(new AccessDeniedException("Not authorized", USER))
        .when(authService)
        .authorize(anyString(), anyList(), eq(entityId), any(), anyList());

    assertThatThrownBy(() -> deploymentAuthHandler.authorizeRollback(appId, workflowExecution))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Not authorized");
    // Verify that we do not reach here
    verify(authService, times(0)).checkIfUserAllowedToRollbackWorkflowToEnv(eq(appId), anyString());

    UserThreadLocal.unset();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testAuthorizeRollbackWorkflowExecutionWithId() {
    try {
      UserThreadLocal.set(user);
      WorkflowExecution workflowExecution =
          WorkflowExecution.builder().uuid(generateUuid()).workflowId(entityId).build();

      when(workflowExecutionService.getExecutionDetailsWithoutGraph(appId, workflowExecution.getUuid()))
          .thenReturn(workflowExecution);
      doNothing().when(authService).checkIfUserAllowedToDeployWorkflowToEnv(appId, workflowExecution.getEnvId());
      doNothing().when(authService).authorize(anyString(), anyList(), eq(entityId), any(), anyList());

      deploymentAuthHandler.authorizeRollback(appId, workflowExecution.getUuid());
    } catch (Exception e) {
      assertThat(e).isNull();
    } finally {
      UserThreadLocal.unset();
    }
  }
}
