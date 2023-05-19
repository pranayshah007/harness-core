/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.pipeline;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.mongo.changestreams.ChangeEvent;
import io.harness.mongo.changestreams.ChangeType;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.audit.AuditHeader;
import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Event.Type;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.search.entities.application.ApplicationEntityTestUtils;
import software.wings.search.entities.environment.EnvironmentEntityTestUtils;
import software.wings.search.entities.pipeline.PipelineView.PipelineViewKeys;
import software.wings.search.entities.related.RelatedAuditEntityTestUtils;
import software.wings.search.entities.related.RelatedDeploymentEntityTestUtils;
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

public class PipelineChangeHandlerTest extends WingsBaseTest {
  @Mock private SearchDao searchDao;
  @Inject @InjectMocks private PipelineChangeHandler pipelineChangeHandler;
  @Inject private HPersistence persistence;

  private static final String APP_NAME = "PipelineHandlerTestForApplication" + System.currentTimeMillis();
  private static final String SERVICE_NAME = "PipelineHandlerTestForService" + System.currentTimeMillis();
  private static final String ENVIRONMENT_NAME = "PipelineHandlerTestForEnvironment" + System.currentTimeMillis();
  private static final String WORKFLOW_NAME = "PipelineHandlerTestForWorkflow" + System.currentTimeMillis();
  private static final String PIPELINE_NAME = "PipelineHandlerTestForPipeline" + System.currentTimeMillis();

  private Application application;
  private Service service;
  private Environment environment;
  private Workflow workflow;
  private Pipeline pipeline;
  private WorkflowExecution workflowExecution;
  private AuditHeader deleteAuditHeader;
  private AuditHeader nonDeleteAuditHeader;
  private AuditHeader nonResourceTypeAuditHeader;
  private ChangeEvent deleteAuditHeaderChangeEvent;
  private ChangeEvent nonDeleteAuditHeaderChangeEvent;
  private ChangeEvent nonResourceTypeAuditHeaderChangeEvent;
  private String accountId = getAccount(AccountType.PAID).getUuid();
  private String nonDeleteAuditHeaderId = generateUuid();
  private String deleteAuditHeaderId = generateUuid();
  private String appId = generateUuid();
  private String serviceId = generateUuid();
  private String environmentId = generateUuid();
  private String workflowId = generateUuid();
  private String pipelineId = generateUuid();
  private String workflowExecutionId = generateUuid();

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
    assertThat(environment).isNotNull();
    persistence.save(workflow);

    pipeline =
        PipelineEntityTestUtils.createPipeline(accountId, appId, pipelineId, PIPELINE_NAME, environmentId, workflowId);
    assertThat(pipeline).isNotNull();
    persistence.save(pipeline);

    workflowExecution = RelatedDeploymentEntityTestUtils.createWorkflowExecution(
        workflowExecutionId, appId, serviceId, environmentId, workflowId, pipelineId, WorkflowType.PIPELINE);
    assertThat(workflowExecution).isNotNull();
    persistence.save(workflowExecution);

    deleteAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(
        deleteAuditHeaderId, accountId, appId, pipelineId, EntityType.PIPELINE.name(), Type.DELETE.name(), true);
    assertThat(deleteAuditHeader).isNotNull();
    persistence.save(deleteAuditHeader);

    nonDeleteAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(
        nonDeleteAuditHeaderId, accountId, appId, pipelineId, EntityType.PIPELINE.name(), Type.UPDATE.name(), true);
    assertThat(nonDeleteAuditHeader).isNotNull();
    persistence.save(nonDeleteAuditHeader);

    deleteAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        deleteAuditHeader, appId, ChangeType.UPDATE, pipelineId, Type.DELETE.name());
    assertThat(deleteAuditHeaderChangeEvent).isNotNull();

    nonDeleteAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        nonDeleteAuditHeader, appId, ChangeType.UPDATE, pipelineId, Type.UPDATE.name());
    assertThat(nonDeleteAuditHeaderChangeEvent).isNotNull();

    nonResourceTypeAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(
        nonDeleteAuditHeaderId, accountId, appId, pipelineId, EntityType.PIPELINE.name(), Type.UPDATE.name(), false);
    assertThat(nonResourceTypeAuditHeader).isNotNull();
    persistence.save(nonResourceTypeAuditHeader);

    nonResourceTypeAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        nonResourceTypeAuditHeader, appId, ChangeType.UPDATE, pipelineId, Type.UPDATE.name());
    assertThat(nonResourceTypeAuditHeaderChangeEvent).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testAuditRelatedChange() {
    boolean isSuccessful = pipelineChangeHandler.handleChange(deleteAuditHeaderChangeEvent);
    assertThat(isSuccessful).isTrue();

    when(searchDao.addTimestamp(eq(PipelineSearchEntity.TYPE), eq(PipelineViewKeys.auditTimestamps), anyString(),
             eq(nonDeleteAuditHeader.getCreatedAt()), eq(7)))
        .thenReturn(true);
    when(searchDao.appendToListInSingleDocument(
             eq(PipelineSearchEntity.TYPE), eq(PipelineViewKeys.audits), anyString(), anyMap(), eq(3)))
        .thenReturn(true);
    boolean result = pipelineChangeHandler.handleChange(nonDeleteAuditHeaderChangeEvent);
    assertThat(result).isTrue();

    boolean isTrue = pipelineChangeHandler.handleChange(nonResourceTypeAuditHeaderChangeEvent);
    assertThat(isTrue).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testPipelineInsertChange() {
    Pipeline pipeline =
        PipelineEntityTestUtils.createPipeline(accountId, appId, pipelineId, PIPELINE_NAME, environmentId, workflowId);
    assertThat(pipeline).isNotNull();
    ChangeEvent pipelineInsertChangeEvent =
        PipelineEntityTestUtils.createPipelineChangeEvent(pipeline, ChangeType.INSERT);
    when(searchDao.upsertDocument(eq(PipelineSearchEntity.TYPE), eq(pipelineId), any())).thenReturn(true);
    boolean isInsertSuccessful = pipelineChangeHandler.handleChange(pipelineInsertChangeEvent);
    assertThat(isInsertSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testPipelineDeleteChange() {
    Pipeline pipeline =
        PipelineEntityTestUtils.createPipeline(accountId, appId, pipelineId, PIPELINE_NAME, environmentId, workflowId);
    assertThat(pipeline).isNotNull();
    ChangeEvent pipelineDeleteChangeEvent =
        PipelineEntityTestUtils.createPipelineChangeEvent(pipeline, ChangeType.DELETE);
    when(searchDao.deleteDocument(PipelineSearchEntity.TYPE, pipelineId)).thenReturn(true);
    boolean isDeleteSuccessful = pipelineChangeHandler.handleChange(pipelineDeleteChangeEvent);
    assertThat(isDeleteSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testPipelineUpdateChange() {
    Pipeline pipeline =
        PipelineEntityTestUtils.createPipeline(accountId, appId, pipelineId, PIPELINE_NAME, environmentId, workflowId);
    assertThat(pipeline).isNotNull();
    ChangeEvent pipelineUpdateChangeEvent =
        PipelineEntityTestUtils.createPipelineChangeEvent(pipeline, ChangeType.UPDATE);
    when(searchDao.upsertDocument(eq(PipelineSearchEntity.TYPE), eq(pipelineId), any())).thenReturn(true);
    boolean isUpdateSuccessful = pipelineChangeHandler.handleChange(pipelineUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testServiceUpdateChange() {
    ChangeEvent serviceUpdateChangeEvent = ServiceEntityTestUtils.createServiceChangeEvent(service, ChangeType.UPDATE);
    when(searchDao.updateListInMultipleDocuments(
             eq(PipelineSearchEntity.TYPE), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(true);
    boolean isUpdateSuccessful = pipelineChangeHandler.handleChange(serviceUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testEnvironmentUpdateChange() {
    ChangeEvent environmentUpdateChangeEvent =
        EnvironmentEntityTestUtils.createEnvironmentChangeEvent(environment, ChangeType.UPDATE);
    when(searchDao.updateKeyInMultipleDocuments(
             eq(PipelineSearchEntity.TYPE), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = pipelineChangeHandler.handleChange(environmentUpdateChangeEvent);
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
             eq(PipelineSearchEntity.TYPE), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = pipelineChangeHandler.handleChange(applicationUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowUpdateChange() {
    ChangeEvent workflowUpdateChangeEvent =
        WorkflowEntityTestUtils.createWorkflowChangeEvent(workflow, ChangeType.UPDATE);
    when(searchDao.updateListInMultipleDocuments(
             eq(PipelineSearchEntity.TYPE), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(true);
    boolean isUpdateSuccessful = pipelineChangeHandler.handleChange(workflowUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowExecutionInsertChange() {
    ChangeEvent workflowExecutionInsertChangeEvent =
        RelatedDeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(workflowExecution, ChangeType.INSERT);

    when(searchDao.addTimestamp(eq(PipelineSearchEntity.TYPE), eq(PipelineViewKeys.deploymentTimestamps), anyString(),
             anyLong(), anyInt()))
        .thenReturn(true);
    when(searchDao.appendToListInSingleDocument(
             eq(PipelineSearchEntity.TYPE), eq(PipelineViewKeys.deployments), anyString(), anyMap(), anyInt()))
        .thenReturn(true);

    boolean isUpdateSuccessful = pipelineChangeHandler.handleChange(workflowExecutionInsertChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowExecutionUpdateChange() {
    ChangeEvent workflowExecutionUpdateChangeEvent =
        RelatedDeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(workflowExecution, ChangeType.UPDATE);

    when(searchDao.updateListInMultipleDocuments(
             eq(PipelineSearchEntity.TYPE), eq(PipelineViewKeys.deployments), anyString(), anyString(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = pipelineChangeHandler.handleChange(workflowExecutionUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowExecutionChange() {
    ChangeEvent workflowExecutionDeleteChangeEvent =
        RelatedDeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(workflowExecution, ChangeType.DELETE);

    boolean isUpdateSuccessful = pipelineChangeHandler.handleChange(workflowExecutionDeleteChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }
}
