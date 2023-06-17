/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.deployment;

import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.mongo.changestreams.ChangeEvent;
import io.harness.mongo.changestreams.ChangeType;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.search.entities.application.ApplicationEntityTestUtils;
import software.wings.search.entities.deployment.DeploymentView.DeploymentViewKeys;
import software.wings.search.entities.environment.EnvironmentEntityTestUtils;
import software.wings.search.entities.pipeline.PipelineEntityTestUtils;
import software.wings.search.entities.service.ServiceEntityTestUtils;
import software.wings.search.entities.workflow.WorkflowEntityTestUtils;
import software.wings.search.framework.SearchDao;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class DeploymentChangeHandlerTest extends WingsBaseTest {
  @Mock private SearchDao searchDao;
  @Inject @InjectMocks DeploymentChangeHandler deploymentChangeHandler;
  @Inject private HPersistence persistence;

  private static final String APP_NAME = "DeploymentHandlerTestForApplication" + System.currentTimeMillis();
  private static final String SERVICE_NAME = "DeploymentHandlerTestForService" + System.currentTimeMillis();
  private static final String ENVIRONMENT_NAME = "DeploymentHandlerTestForEnvironment" + System.currentTimeMillis();
  private static final String WORKFLOW_NAME = "DeploymentHandlerTestForWorkflow" + System.currentTimeMillis();
  private static final String PIPELINE_NAME = "DeploymentHandlerTestForPipeline" + System.currentTimeMillis();

  private Application application;
  private Service service;
  private Environment environment;
  private Workflow workflow;
  private Pipeline pipeline;
  private WorkflowExecution workflowExecutionPipeline;
  private WorkflowExecution workflowExecutionOrchestration;
  private ExecutionArgs executionArgs;
  private ChangeEvent workflowExecutionPipelineInsertChangeEvent;
  private ChangeEvent workflowExecutionPipelineUpdateChangeEvent;
  private ChangeEvent workflowExecutionPipelineDeleteChangeEvent;
  private ChangeEvent workflowExecutionOrchestrationInsertChangeEvent;
  private ChangeEvent workflowExecutionOrchestrationUpdateChangeEvent;
  private ChangeEvent workflowExecutionOrchestrationDeleteChangeEvent;
  private String workflowExecutionId = generateUuid();
  private String accountId = generateUuid();
  private String appId = generateUuid();
  private String serviceId = generateUuid();
  private String environmentId = generateUuid();
  private String workflowId = generateUuid();
  private String pipelineId = generateUuid();

  @Before
  public void setup() throws IOException {
    application = ApplicationEntityTestUtils.createApplication(accountId, appId, APP_NAME);
    assertThat(application).isNotNull();
    persistence.save(application);

    service = ServiceEntityTestUtils.createService(accountId, appId, serviceId, SERVICE_NAME);
    assertThat(service).isNotNull();
    persistence.save(service);

    environment = EnvironmentEntityTestUtils.createEnvironment(accountId, appId, environmentId, ENVIRONMENT_NAME);
    assertThat(environment).isNotNull();
    persistence.save(environment);

    workflow = WorkflowEntityTestUtils.createWorkflow(
        accountId, appId, workflowId, environmentId, serviceId, service, WORKFLOW_NAME);
    assertThat(workflow).isNotNull();
    persistence.save(workflow);

    pipeline =
        PipelineEntityTestUtils.createPipeline(accountId, appId, pipelineId, PIPELINE_NAME, environmentId, workflowId);
    assertThat(pipeline).isNotNull();
    persistence.save(pipeline);

    executionArgs = DeploymentEntityTestUtils.createExecutionArgs(WorkflowType.PIPELINE);
    assertThat(executionArgs).isNotNull();

    workflowExecutionPipeline = DeploymentEntityTestUtils.createWorkflowExecution(workflowExecutionId, appId, APP_NAME,
        environmentId, serviceId, workflowId, pipelineId, executionArgs, WorkflowType.PIPELINE, SUCCESS);
    assertThat(workflowExecutionPipeline).isNotNull();
    persistence.save(workflowExecutionPipeline);

    workflowExecutionOrchestration = DeploymentEntityTestUtils.createWorkflowExecution(workflowExecutionId, appId,
        APP_NAME, environmentId, serviceId, workflowId, pipelineId, executionArgs, WorkflowType.PIPELINE, SUCCESS);
    assertThat(workflowExecutionOrchestration).isNotNull();
    persistence.save(workflowExecutionOrchestration);

    workflowExecutionPipelineInsertChangeEvent = DeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(
        WorkflowExecution.class, workflowExecutionPipeline, ChangeType.INSERT);
    assertThat(workflowExecutionPipelineInsertChangeEvent).isNotNull();

    workflowExecutionPipelineUpdateChangeEvent = DeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(
        WorkflowExecution.class, workflowExecutionPipeline, ChangeType.UPDATE);
    assertThat(workflowExecutionPipelineUpdateChangeEvent).isNotNull();

    workflowExecutionPipelineDeleteChangeEvent = DeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(
        WorkflowExecution.class, workflowExecutionPipeline, ChangeType.DELETE);
    assertThat(workflowExecutionPipelineDeleteChangeEvent).isNotNull();

    workflowExecutionOrchestrationInsertChangeEvent = DeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(
        WorkflowExecution.class, workflowExecutionOrchestration, ChangeType.INSERT);
    assertThat(workflowExecutionOrchestrationInsertChangeEvent).isNotNull();

    workflowExecutionOrchestrationUpdateChangeEvent = DeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(
        WorkflowExecution.class, workflowExecutionOrchestration, ChangeType.UPDATE);
    assertThat(workflowExecutionOrchestrationUpdateChangeEvent).isNotNull();

    workflowExecutionOrchestrationDeleteChangeEvent = DeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(
        WorkflowExecution.class, workflowExecutionOrchestration, ChangeType.DELETE);
    assertThat(workflowExecutionOrchestrationDeleteChangeEvent).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowExecutionInsertChange() {
    when(searchDao.upsertDocument(eq(DeploymentSearchEntity.TYPE), eq(workflowExecutionId), any())).thenReturn(true);
    boolean isSuccessful = deploymentChangeHandler.handleChange(workflowExecutionPipelineInsertChangeEvent);

    assertThat(isSuccessful).isTrue();

    when(searchDao.upsertDocument(eq(DeploymentSearchEntity.TYPE), eq(workflowExecutionId), any())).thenReturn(true);
    boolean isSuccess = deploymentChangeHandler.handleChange(workflowExecutionOrchestrationInsertChangeEvent);
    assertThat(isSuccess).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowExecutionUpdateChange() {
    when(searchDao.upsertDocument(eq(DeploymentSearchEntity.TYPE), eq(workflowExecutionId), any())).thenReturn(true);
    boolean isSuccessful = deploymentChangeHandler.handleChange(workflowExecutionPipelineUpdateChangeEvent);
    assertThat(isSuccessful).isTrue();

    when(searchDao.upsertDocument(eq(DeploymentSearchEntity.TYPE), eq(workflowExecutionId), any())).thenReturn(true);
    boolean isSuccess = deploymentChangeHandler.handleChange(workflowExecutionOrchestrationUpdateChangeEvent);
    assertThat(isSuccess).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowExecutionDeleteChange() {
    when(searchDao.deleteDocument(eq(DeploymentSearchEntity.TYPE), anyString())).thenReturn(true);
    boolean isSuccessful = deploymentChangeHandler.handleChange(workflowExecutionPipelineDeleteChangeEvent);
    assertThat(isSuccessful).isTrue();

    when(searchDao.upsertDocument(eq(DeploymentSearchEntity.TYPE), eq(workflowExecutionId), any())).thenReturn(true);
    boolean isSuccess = deploymentChangeHandler.handleChange(workflowExecutionOrchestrationDeleteChangeEvent);
    assertThat(isSuccess).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowUpdateChange() {
    ChangeEvent workflowUpdateChangeEvent =
        WorkflowEntityTestUtils.createWorkflowChangeEvent(workflow, ChangeType.UPDATE);
    when(searchDao.updateListInMultipleDocuments(
             eq(DeploymentSearchEntity.TYPE), eq(DeploymentViewKeys.workflows), anyString(), anyString(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = deploymentChangeHandler.handleChange(workflowUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testServiceUpdateChange() {
    ChangeEvent serviceUpdateChangeEvent = ServiceEntityTestUtils.createServiceChangeEvent(service, ChangeType.UPDATE);
    when(searchDao.updateListInMultipleDocuments(
             eq(DeploymentSearchEntity.TYPE), eq(DeploymentViewKeys.services), anyString(), anyString(), anyString()))
        .thenReturn(true);
    boolean isUpdateSuccessful = deploymentChangeHandler.handleChange(serviceUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testEnvironmentUpdateChange() {
    ChangeEvent environmentUpdateChangeEvent =
        EnvironmentEntityTestUtils.createEnvironmentChangeEvent(environment, ChangeType.UPDATE);
    when(searchDao.updateListInMultipleDocuments(eq(DeploymentSearchEntity.TYPE), eq(DeploymentViewKeys.environments),
             anyString(), anyString(), anyString()))
        .thenReturn(true);
    boolean isUpdateSuccessful = deploymentChangeHandler.handleChange(environmentUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testApplicationUpdateChange() {
    ChangeEvent applicationUpdateChangeEvent =
        ApplicationEntityTestUtils.createApplicationChangeEvent(application, ChangeType.UPDATE);
    when(searchDao.updateKeyInMultipleDocuments(
             eq(DeploymentSearchEntity.TYPE), eq(DeploymentViewKeys.appName), anyString(), anyString(), anyString()))
        .thenReturn(true);
    boolean isUpdateSuccessful = deploymentChangeHandler.handleChange(applicationUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }
}
