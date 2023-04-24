/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
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
import software.wings.search.entities.service.ServiceView.ServiceViewKeys;
import software.wings.search.entities.workflow.WorkflowEntityTestUtils;
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

public class ServiceChangeHandlerTest extends WingsBaseTest {
  @Mock private SearchDao searchDao;
  @Inject private RelatedAuditViewBuilder relatedAuditViewBuilder;
  @Inject @InjectMocks private ServiceChangeHandler serviceChangeHandler;
  @Inject private HPersistence persistence;

  private static final String APP_NAME = "ServiceHandlerTestForApplication" + System.currentTimeMillis();
  private static final String SERVICE_NAME = "ServiceHandlerTestForService" + System.currentTimeMillis();
  private static final String ENVIRONMENT_NAME = "ServiceHandlerTestForEnvironment" + System.currentTimeMillis();
  private static final String WORKFLOW_NAME = "ServiceHandlerTestForWorkflow" + System.currentTimeMillis();
  private static final String PIPELINE_NAME = "ServiceHandlerTestForPipeline" + System.currentTimeMillis();

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

    workflowExecution = RelatedDeploymentEntityTestUtils.createWorkflowExecution(
        workflowExecutionId, appId, serviceId, environmentId, workflowId, pipelineId, WorkflowType.ORCHESTRATION);
    assertThat(workflowExecution).isNotNull();
    persistence.save(workflowExecution);

    deleteAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(
        deleteAuditHeaderId, accountId, appId, serviceId, EntityType.SERVICE.name(), Type.DELETE.name(), true);
    assertThat(deleteAuditHeader).isNotNull();
    persistence.save(deleteAuditHeader);

    nonDeleteAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(
        nonDeleteAuditHeaderId, accountId, appId, serviceId, EntityType.SERVICE.name(), Type.UPDATE.name(), true);
    assertThat(nonDeleteAuditHeader).isNotNull();
    persistence.save(nonDeleteAuditHeader);

    deleteAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        deleteAuditHeader, appId, ChangeType.UPDATE, serviceId, Type.DELETE.name());
    assertThat(deleteAuditHeaderChangeEvent).isNotNull();

    nonDeleteAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        nonDeleteAuditHeader, appId, ChangeType.UPDATE, serviceId, Type.UPDATE.name());
    assertThat(nonDeleteAuditHeaderChangeEvent).isNotNull();

    nonResourceTypeAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(
        nonDeleteAuditHeaderId, accountId, appId, serviceId, EntityType.SERVICE.name(), Type.UPDATE.name(), false);
    assertThat(nonResourceTypeAuditHeader).isNotNull();
    persistence.save(nonResourceTypeAuditHeader);

    nonResourceTypeAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        nonResourceTypeAuditHeader, appId, ChangeType.UPDATE, serviceId, Type.UPDATE.name());
    assertThat(nonResourceTypeAuditHeaderChangeEvent).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testAuditRelatedChange() {
    boolean isSuccessful = serviceChangeHandler.handleChange(deleteAuditHeaderChangeEvent);
    assertThat(isSuccessful).isNotNull();
    assertThat(isSuccessful).isTrue();

    when(searchDao.addTimestamp(eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.auditTimestamps), anyString(),
             eq(nonDeleteAuditHeader.getCreatedAt()), eq(7)))
        .thenReturn(true);
    when(searchDao.appendToListInSingleDocument(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.audits), anyString(), anyMap(), eq(3)))
        .thenReturn(true);
    boolean result = serviceChangeHandler.handleChange(nonDeleteAuditHeaderChangeEvent);
    assertThat(result).isTrue();

    boolean isTrue = serviceChangeHandler.handleChange(nonResourceTypeAuditHeaderChangeEvent);
    assertThat(isTrue).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testServiceInsertChange() {
    Service service = ServiceEntityTestUtils.createService(accountId, appId, serviceId, SERVICE_NAME);
    assertThat(service).isNotNull();
    ChangeEvent serviceInsertChangeEvent = ServiceEntityTestUtils.createServiceChangeEvent(service, ChangeType.INSERT);
    when(searchDao.upsertDocument(eq(ServiceSearchEntity.TYPE), eq(serviceId), any())).thenReturn(true);
    boolean isInsertSuccessful = serviceChangeHandler.handleChange(serviceInsertChangeEvent);
    assertThat(isInsertSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testServiceDeleteChange() {
    Service service = ServiceEntityTestUtils.createService(accountId, appId, serviceId, SERVICE_NAME);
    assertThat(service).isNotNull();
    ChangeEvent serviceDeleteChangeEvent = ServiceEntityTestUtils.createServiceChangeEvent(service, ChangeType.DELETE);
    when(searchDao.deleteDocument(ServiceSearchEntity.TYPE, serviceId)).thenReturn(true);
    boolean isDeleteSuccessful = serviceChangeHandler.handleChange(serviceDeleteChangeEvent);
    assertThat(isDeleteSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testServiceUpdateChange() {
    Service service = ServiceEntityTestUtils.createService(accountId, appId, serviceId, SERVICE_NAME);
    assertThat(service).isNotNull();
    ChangeEvent serviceUpdateChangeEvent = ServiceEntityTestUtils.createServiceChangeEvent(service, ChangeType.UPDATE);
    when(searchDao.upsertDocument(eq(ServiceSearchEntity.TYPE), eq(serviceId), any())).thenReturn(true);
    boolean isUpdateSuccessful = serviceChangeHandler.handleChange(serviceUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testPipelineUpdateChange() {
    ChangeEvent serviceUpdateChangeEvent =
        PipelineEntityTestUtils.createPipelineChangeEvent(pipeline, ChangeType.UPDATE);

    List<String> stringList = new ArrayList<>();
    stringList.add("value1");

    when(searchDao.nestedQuery(eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.pipelines), anyString()))
        .thenReturn(stringList);
    when(searchDao.appendToListInMultipleDocuments(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.pipelines), anyList(), anyMap()))
        .thenReturn(true);
    when(searchDao.removeFromListInMultipleDocuments(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.pipelines), anyList(), anyString()))
        .thenReturn(true);
    when(searchDao.updateListInMultipleDocuments(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.pipelines), anyString(), anyString(), anyString()))
        .thenReturn(true);
    boolean isUpdateSuccessful = serviceChangeHandler.handleChange(serviceUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testPipelineDeleteChange() {
    ChangeEvent serviceDeleteChangeEvent =
        PipelineEntityTestUtils.createPipelineChangeEvent(pipeline, ChangeType.DELETE);

    List<String> stringList = new ArrayList<>();
    stringList.add("value1");

    when(searchDao.nestedQuery(eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.pipelines), anyString()))
        .thenReturn(stringList);
    when(searchDao.removeFromListInMultipleDocuments(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.pipelines), anyList(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = serviceChangeHandler.handleChange(serviceDeleteChangeEvent);
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
             eq(ServiceSearchEntity.TYPE), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = serviceChangeHandler.handleChange(applicationUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowUpdateChange() {
    ChangeEvent workflowUpdateChangeEvent =
        WorkflowEntityTestUtils.createWorkflowChangeEvent(workflow, ChangeType.UPDATE);

    List<String> stringList = new ArrayList<>();
    stringList.add("value1");

    when(searchDao.nestedQuery(eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.workflows), anyString()))
        .thenReturn(stringList);
    when(searchDao.appendToListInMultipleDocuments(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.workflows), anyList(), anyMap()))
        .thenReturn(true);
    when(searchDao.removeFromListInMultipleDocuments(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.workflows), anyList(), anyString()))
        .thenReturn(true);
    when(searchDao.updateListInMultipleDocuments(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.workflows), anyString(), anyString(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = serviceChangeHandler.handleChange(workflowUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowInsertChange() {
    ChangeEvent workflowInsertChangeEvent =
        WorkflowEntityTestUtils.createWorkflowChangeEvent(workflow, ChangeType.INSERT);

    when(searchDao.appendToListInMultipleDocuments(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.workflows), anyList(), anyMap()))
        .thenReturn(true);

    boolean isUpdateSuccessful = serviceChangeHandler.handleChange(workflowInsertChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowDeleteChange() {
    ChangeEvent workflowDeleteChangeEvent =
        WorkflowEntityTestUtils.createWorkflowChangeEvent(workflow, ChangeType.DELETE);

    List<String> stringList = new ArrayList<>();
    stringList.add("value1");

    when(searchDao.nestedQuery(eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.workflows), anyString()))
        .thenReturn(stringList);
    when(searchDao.removeFromListInMultipleDocuments(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.workflows), anyList(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = serviceChangeHandler.handleChange(workflowDeleteChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowExecutionInsertChange() {
    ChangeEvent workflowExecutionInsertChangeEvent =
        RelatedDeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(workflowExecution, ChangeType.INSERT);

    when(searchDao.addTimestamp(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.deploymentTimestamps), anyList(), anyLong(), anyInt()))
        .thenReturn(true);
    when(searchDao.appendToListInMultipleDocuments(
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.deployments), anyList(), anyMap(), anyInt()))
        .thenReturn(true);

    boolean isUpdateSuccessful = serviceChangeHandler.handleChange(workflowExecutionInsertChangeEvent);
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
             eq(ServiceSearchEntity.TYPE), eq(ServiceViewKeys.deployments), anyString(), anyString(), anyString()))
        .thenReturn(true);

    boolean isUpdateSuccessful = serviceChangeHandler.handleChange(workflowExecutionUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowExecutionChange() {
    ChangeEvent workflowExecutionDeleteChangeEvent =
        RelatedDeploymentEntityTestUtils.createWorkflowExecutionChangeEvent(workflowExecution, ChangeType.DELETE);

    boolean isUpdateSuccessful = serviceChangeHandler.handleChange(workflowExecutionDeleteChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }
}
