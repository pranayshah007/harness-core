/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.workflow;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
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
import software.wings.search.entities.pipeline.PipelineEntityTestUtils;
import software.wings.search.entities.related.RelatedAuditEntityTestUtils;
import software.wings.search.entities.related.RelatedDeploymentEntityTestUtils;
import software.wings.search.entities.related.audit.RelatedAuditViewBuilder;
import software.wings.search.entities.service.ServiceEntityTestUtils;
import software.wings.search.entities.workflow.WorkflowView.WorkflowViewKeys;
import software.wings.search.framework.SearchDao;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class WorkflowChangeHandlerTest extends WingsBaseTest {
  @Mock private SearchDao searchDao;
  @Inject private RelatedAuditViewBuilder relatedAuditViewBuilder;
  @Inject @InjectMocks private WorkflowChangeHandler workflowChangeHandler;
  @Inject private HPersistence persistence;

  private static final String APP_NAME = "WorkflowHandlerTestForApplication" + System.currentTimeMillis();
  private static final String SERVICE_NAME = "WorkflowHandlerTestForService" + System.currentTimeMillis();
  private static final String ENVIRONMENT_NAME = "WorkflowHandlerTestForEnvironment" + System.currentTimeMillis();
  private static final String WORKFLOW_NAME = "WorkflowHandlerTestForWorkflow" + System.currentTimeMillis();
  private static final String PIPELINE_NAME = "WorkflowHandlerTestForPipeline" + System.currentTimeMillis();

  private Application application;
  private Service service;
  private Environment environment;
  private Workflow workflow;
  private Pipeline pipeline;
  private AuditHeader deleteAuditHeader;
  private WorkflowExecution workflowExecution;
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
    persistence.save(environment);
    assertThat(environment).isNotNull();

    pipeline =
        PipelineEntityTestUtils.createPipeline(accountId, appId, pipelineId, PIPELINE_NAME, environmentId, workflowId);
    assertThat(pipeline).isNotNull();
    persistence.save(pipeline);

    workflowExecution = RelatedDeploymentEntityTestUtils.createWorkflowExecution(
        workflowExecutionId, appId, serviceId, environmentId, workflowId, pipelineId, WorkflowType.ORCHESTRATION);
    assertThat(workflowExecution).isNotNull();
    persistence.save(workflowExecution);

    deleteAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(
        deleteAuditHeaderId, accountId, appId, workflowId, EntityType.WORKFLOW.name(), Type.DELETE.name(), true);
    persistence.save(deleteAuditHeader);
    assertThat(deleteAuditHeader).isNotNull();

    nonDeleteAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(
        nonDeleteAuditHeaderId, accountId, appId, workflowId, EntityType.WORKFLOW.name(), Type.UPDATE.name(), true);
    persistence.save(nonDeleteAuditHeader);
    assertThat(nonDeleteAuditHeader).isNotNull();

    deleteAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        deleteAuditHeader, appId, ChangeType.UPDATE, workflowId, Type.DELETE.name());
    assertThat(deleteAuditHeaderChangeEvent).isNotNull();

    nonDeleteAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        nonDeleteAuditHeader, appId, ChangeType.UPDATE, pipelineId, Type.UPDATE.name());
    assertThat(nonDeleteAuditHeaderChangeEvent).isNotNull();

    nonResourceTypeAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(
        nonDeleteAuditHeaderId, accountId, appId, workflowId, EntityType.WORKFLOW.name(), Type.UPDATE.name(), false);
    assertThat(nonResourceTypeAuditHeader).isNotNull();
    persistence.save(nonResourceTypeAuditHeader);

    nonResourceTypeAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        nonResourceTypeAuditHeader, appId, ChangeType.UPDATE, workflowId, Type.UPDATE.name());
    assertThat(nonResourceTypeAuditHeaderChangeEvent).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testAuditRelatedChange() {
    boolean isSuccessful = workflowChangeHandler.handleChange(deleteAuditHeaderChangeEvent);
    assertThat(isSuccessful).isNotNull();
    assertThat(isSuccessful).isTrue();

    when(searchDao.addTimestamp(eq(WorkflowSearchEntity.TYPE), eq(WorkflowViewKeys.auditTimestamps), anyString(),
             eq(nonDeleteAuditHeader.getCreatedAt()), eq(7)))
        .thenReturn(true);
    when(searchDao.appendToListInSingleDocument(
             eq(WorkflowSearchEntity.TYPE), eq(WorkflowViewKeys.audits), anyString(), anyMap(), eq(3)))
        .thenReturn(true);
    boolean result = workflowChangeHandler.handleChange(nonDeleteAuditHeaderChangeEvent);
    assertThat(result).isTrue();

    boolean isTrue = workflowChangeHandler.handleChange(nonResourceTypeAuditHeaderChangeEvent);
    assertThat(isTrue).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowInsertChange() {
    Workflow workflow = WorkflowEntityTestUtils.createWorkflow(
        accountId, appId, workflowId, environmentId, serviceId, service, WORKFLOW_NAME);
    assertThat(workflow).isNotNull();
    ChangeEvent workflowInsertChangeEvent =
        WorkflowEntityTestUtils.createWorkflowChangeEvent(workflow, ChangeType.INSERT);
    when(searchDao.upsertDocument(eq(WorkflowSearchEntity.TYPE), eq(workflowId), any())).thenReturn(true);
    boolean isInsertSuccessful = workflowChangeHandler.handleChange(workflowInsertChangeEvent);
    assertThat(isInsertSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowDeleteChange() {
    Workflow workflow = WorkflowEntityTestUtils.createWorkflow(
        accountId, appId, workflowId, environmentId, serviceId, service, WORKFLOW_NAME);
    assertThat(workflow).isNotNull();
    ChangeEvent workflowDeleteChangeEvent =
        WorkflowEntityTestUtils.createWorkflowChangeEvent(workflow, ChangeType.DELETE);
    when(searchDao.deleteDocument(WorkflowSearchEntity.TYPE, workflowId)).thenReturn(true);
    boolean isDeleteSuccessful = workflowChangeHandler.handleChange(workflowDeleteChangeEvent);
    assertThat(isDeleteSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowUpdateChange() {
    Workflow workflow = WorkflowEntityTestUtils.createWorkflow(
        accountId, appId, workflowId, environmentId, serviceId, service, WORKFLOW_NAME);
    assertThat(workflow).isNotNull();
    ChangeEvent workflowUpdateChangeEvent =
        WorkflowEntityTestUtils.createWorkflowChangeEvent(workflow, ChangeType.UPDATE);
    when(searchDao.upsertDocument(eq(WorkflowSearchEntity.TYPE), eq(workflowId), any())).thenReturn(true);
    boolean isUpdateSuccessful = workflowChangeHandler.handleChange(workflowUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testServiceUpdateChange() {
    ChangeEvent serviceUpdateChangeEvent = ServiceEntityTestUtils.createServiceChangeEvent(service, ChangeType.UPDATE);
    when(searchDao.updateListInMultipleDocuments(
             eq(WorkflowSearchEntity.TYPE), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(true);
    boolean isUpdateSuccessful = workflowChangeHandler.handleChange(serviceUpdateChangeEvent);
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
             eq(WorkflowSearchEntity.TYPE), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = workflowChangeHandler.handleChange(environmentUpdateChangeEvent);
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
             eq(WorkflowSearchEntity.TYPE), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = workflowChangeHandler.handleChange(applicationUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testPipelineUpdateChange() {
    ChangeEvent pipelineUpdateChangeEvent =
        PipelineEntityTestUtils.createPipelineChangeEvent(pipeline, ChangeType.UPDATE);

    List<String> stringList = new ArrayList<>();
    stringList.add("value1");

    when(searchDao.nestedQuery(eq(WorkflowSearchEntity.TYPE), eq(WorkflowViewKeys.pipelines), anyString()))
        .thenReturn(stringList);
    when(searchDao.appendToListInMultipleDocuments(
             eq(WorkflowSearchEntity.TYPE), eq(WorkflowViewKeys.pipelines), anyList(), anyMap()))
        .thenReturn(true);
    when(searchDao.removeFromListInMultipleDocuments(
             eq(WorkflowSearchEntity.TYPE), eq(WorkflowViewKeys.pipelines), anyList(), anyString()))
        .thenReturn(true);
    when(searchDao.updateListInMultipleDocuments(
             eq(WorkflowSearchEntity.TYPE), eq(WorkflowViewKeys.pipelines), anyString(), anyString(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = workflowChangeHandler.handleChange(pipelineUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testPipelineDeleteChange() {
    ChangeEvent pipelineDeleteChangeEvent =
        PipelineEntityTestUtils.createPipelineChangeEvent(pipeline, ChangeType.DELETE);
    List<String> listOfString = new ArrayList<>();
    listOfString.add("id1");
    listOfString.add("id2");

    when(searchDao.nestedQuery(eq(WorkflowSearchEntity.TYPE), anyString(), anyString())).thenReturn(listOfString);
    when(
        searchDao.removeFromListInMultipleDocuments(eq(WorkflowSearchEntity.TYPE), anyString(), anyList(), anyString()))
        .thenReturn(true);
    boolean isDeleteSuccessful = workflowChangeHandler.handleChange(pipelineDeleteChangeEvent);
    assertThat(isDeleteSuccessful).isNotNull();
    assertThat(isDeleteSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowExecutionInsertChange() {
    ChangeEvent workflowExecutionInsertChangeEvent =
        RelatedDeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(workflowExecution, ChangeType.INSERT);
    when(searchDao.addTimestamp(
             eq(WorkflowSearchEntity.TYPE), eq(WorkflowViewKeys.deploymentTimestamps), anyList(), anyLong(), anyInt()))
        .thenReturn(true);
    when(searchDao.appendToListInMultipleDocuments(
             eq(WorkflowSearchEntity.TYPE), eq(WorkflowViewKeys.deployments), anyList(), anyMap(), anyInt()))
        .thenReturn(true);
    boolean isUpdateSuccessful = workflowChangeHandler.handleChange(workflowExecutionInsertChangeEvent);
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
             eq(WorkflowSearchEntity.TYPE), eq(WorkflowViewKeys.deployments), anyString(), anyString(), anyString()))
        .thenReturn(true);
    boolean isUpdateSuccessful = workflowChangeHandler.handleChange(workflowExecutionUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowExecutionChange() {
    ChangeEvent workflowExecutionDeleteChangeEvent =
        RelatedDeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(workflowExecution, ChangeType.DELETE);

    boolean isUpdateSuccessful = workflowChangeHandler.handleChange(workflowExecutionDeleteChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }
}
