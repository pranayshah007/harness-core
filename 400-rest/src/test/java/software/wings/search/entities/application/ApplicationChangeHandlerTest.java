/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.search.entities.application;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.mongo.changestreams.ChangeEvent;
import io.harness.mongo.changestreams.ChangeType;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.audit.AuditHeader;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Event.Type;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.search.entities.application.ApplicationView.ApplicationViewKeys;
import software.wings.search.entities.environment.EnvironmentEntityTestUtils;
import software.wings.search.entities.pipeline.PipelineEntityTestUtils;
import software.wings.search.entities.related.RelatedAuditEntityTestUtils;
import software.wings.search.entities.service.ServiceEntityTestUtils;
import software.wings.search.entities.workflow.WorkflowEntityTestUtils;
import software.wings.search.framework.EntityInfo.EntityInfoKeys;
import software.wings.search.framework.SearchDao;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ApplicationChangeHandlerTest extends WingsBaseTest {
  @Mock private SearchDao searchDao;

  @Inject @InjectMocks private ApplicationChangeHandler applicationChangeHandler;
  @Inject private HPersistence persistence;

  private static final String APP_NAME = "ApplicationHandlerTestForApplication" + System.currentTimeMillis();
  private static final String SERVICE_NAME = "ApplicationHandlerTestForService" + System.currentTimeMillis();
  private static final String ENVIRONMENT_NAME = "ApplicationHandlerTestForEnvironment" + System.currentTimeMillis();
  private static final String WORKFLOW_NAME = "ApplicationHandlerTestForWorkflow" + System.currentTimeMillis();
  private static final String PIPELINE_NAME = "ApplicationHandlerTestForPipeline" + System.currentTimeMillis();

  private Application application;
  private Service service;
  private Environment environment;
  private Workflow workflow;
  private Pipeline pipeline;
  private AuditHeader deleteAuditHeader;
  private AuditHeader nonDeleteAuditHeader;
  private AuditHeader nonResourceTypeAuditHeader;
  private ChangeEvent deleteAuditHeaderChangeEvent;
  private ChangeEvent nonDeleteAuditHeaderChangeEvent;
  private ChangeEvent nonResourceTypeAuditHeaderChangeEvent;
  private Account account = getAccount(AccountType.PAID);
  private String nonDeleteAuditHeaderId = generateUuid();
  private String deleteAuditHeaderId = generateUuid();
  private String appId = generateUuid();
  private String serviceId = generateUuid();
  private String environmentId = generateUuid();
  private String workflowId = generateUuid();
  private String pipelineId = generateUuid();

  @Before
  public void setup() throws IOException {
    application = ApplicationEntityTestUtils.createApplication(account.getUuid(), appId, APP_NAME);
    assertThat(application).isNotNull();
    persistence.save(application);

    service = ServiceEntityTestUtils.createService(account.getUuid(), appId, serviceId, SERVICE_NAME);
    assertThat(service).isNotNull();
    persistence.save(service);

    environment =
        EnvironmentEntityTestUtils.createEnvironment(account.getUuid(), appId, environmentId, ENVIRONMENT_NAME);
    assertThat(environment).isNotNull();
    persistence.save(environment);

    workflow = WorkflowEntityTestUtils.createWorkflow(
        account.getUuid(), appId, workflowId, environmentId, serviceId, service, WORKFLOW_NAME);
    assertThat(workflow).isNotNull();
    persistence.save(workflow);

    pipeline = PipelineEntityTestUtils.createPipeline(
        account.getUuid(), appId, pipelineId, PIPELINE_NAME, environmentId, workflowId);
    assertThat(pipeline).isNotNull();
    persistence.save(pipeline);

    deleteAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(
        deleteAuditHeaderId, account.getUuid(), appId, appId, EntityType.APPLICATION.name(), Type.DELETE.name(), true);
    assertThat(deleteAuditHeader).isNotNull();
    persistence.save(deleteAuditHeader);

    nonDeleteAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(nonDeleteAuditHeaderId, account.getUuid(),
        appId, appId, EntityType.APPLICATION.name(), Type.UPDATE.name(), true);
    assertThat(nonDeleteAuditHeader).isNotNull();
    persistence.save(nonDeleteAuditHeader);

    nonResourceTypeAuditHeader = RelatedAuditEntityTestUtils.createAuditHeader(nonDeleteAuditHeaderId,
        account.getUuid(), appId, appId, EntityType.APPLICATION.name(), Type.UPDATE.name(), false);
    assertThat(nonResourceTypeAuditHeader).isNotNull();
    persistence.save(nonResourceTypeAuditHeader);

    deleteAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        deleteAuditHeader, appId, ChangeType.UPDATE, application.getUuid(), Type.DELETE.name());
    assertThat(deleteAuditHeaderChangeEvent).isNotNull();

    nonDeleteAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        nonDeleteAuditHeader, appId, ChangeType.UPDATE, application.getUuid(), Type.UPDATE.name());
    assertThat(nonDeleteAuditHeaderChangeEvent).isNotNull();

    nonResourceTypeAuditHeaderChangeEvent = RelatedAuditEntityTestUtils.createAuditHeaderChangeEvent(
        nonResourceTypeAuditHeader, appId, ChangeType.UPDATE, application.getUuid(), Type.UPDATE.name());
    assertThat(nonResourceTypeAuditHeaderChangeEvent).isNotNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testAuditRelatedChange() {
    when(searchDao.addTimestamp(
             eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.auditTimestamps), anyList(), anyLong(), eq(7)))
        .thenReturn(true);
    when(searchDao.appendToListInMultipleDocuments(
             eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.audits), anyList(), anyMap(), eq(3)))
        .thenReturn(true);
    boolean isSuccessful = applicationChangeHandler.handleChange(deleteAuditHeaderChangeEvent);
    assertThat(isSuccessful).isTrue();

    when(searchDao.addTimestamp(
             eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.auditTimestamps), anyList(), anyLong(), eq(7)))
        .thenReturn(true);
    when(searchDao.appendToListInMultipleDocuments(
             eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.audits), anyList(), anyMap(), eq(3)))
        .thenReturn(true);
    boolean result = applicationChangeHandler.handleChange(nonDeleteAuditHeaderChangeEvent);
    assertThat(result).isTrue();

    boolean isTrue = applicationChangeHandler.handleChange(nonResourceTypeAuditHeaderChangeEvent);
    assertThat(isTrue).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testApplicationInsertChange() {
    ChangeEvent applicationInsertChangeEvent =
        ApplicationEntityTestUtils.createApplicationChangeEvent(application, ChangeType.INSERT);
    when(searchDao.upsertDocument(eq(ApplicationSearchEntity.TYPE), eq(appId), any())).thenReturn(true);
    boolean isInsertSuccessful = applicationChangeHandler.handleChange(applicationInsertChangeEvent);
    assertThat(isInsertSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testApplicationDeleteChange() {
    ChangeEvent applicationDeleteChangeEvent =
        ApplicationEntityTestUtils.createApplicationChangeEvent(application, ChangeType.DELETE);
    when(searchDao.deleteDocument(ApplicationSearchEntity.TYPE, appId)).thenReturn(true);
    boolean isDeleteSuccessful = applicationChangeHandler.handleChange(applicationDeleteChangeEvent);
    assertThat(isDeleteSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testApplicationUpdateChange() {
    ChangeEvent applicationUpdateChangeEvent =
        ApplicationEntityTestUtils.createApplicationChangeEvent(application, ChangeType.UPDATE);
    when(searchDao.upsertDocument(eq(ApplicationSearchEntity.TYPE), eq(appId), any())).thenReturn(true);
    boolean isUpdateSuccessful = applicationChangeHandler.handleChange(applicationUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testServiceInsertChange() {
    ChangeEvent serviceInsertChangeEvent = ServiceEntityTestUtils.createServiceChangeEvent(service, ChangeType.INSERT);
    when(searchDao.appendToListInSingleDocument(
             eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.services), eq(service.getAppId()), anyMap()))
        .thenReturn(true);
    boolean isInsertSuccessful = applicationChangeHandler.handleChange(serviceInsertChangeEvent);
    assertThat(isInsertSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testServiceDeleteChange() {
    ChangeEvent serviceDeleteChangeEvent = ServiceEntityTestUtils.createServiceChangeEvent(service, ChangeType.DELETE);
    when(searchDao.removeFromListInMultipleDocuments(
             eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.services), any()))
        .thenReturn(true);
    boolean isDeleteSuccessful = applicationChangeHandler.handleChange(serviceDeleteChangeEvent);
    assertThat(isDeleteSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testServiceUpdateChange() {
    ChangeEvent serviceUpdateChangeEvent = ServiceEntityTestUtils.createServiceChangeEvent(service, ChangeType.UPDATE);
    when(searchDao.updateListInMultipleDocuments(eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.services),
             anyString(), anyString(), eq(EntityInfoKeys.name)))
        .thenReturn(true);
    boolean isUpdateSuccessful = applicationChangeHandler.handleChange(serviceUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testEnvironmentInsertChange() {
    ChangeEvent environmentInsertChangeEvent =
        EnvironmentEntityTestUtils.createEnvironmentChangeEvent(environment, ChangeType.INSERT);
    when(searchDao.appendToListInSingleDocument(eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.environments),
             eq(environment.getAppId()), anyMap()))
        .thenReturn(true);
    boolean isInsertSuccessful = applicationChangeHandler.handleChange(environmentInsertChangeEvent);
    assertThat(isInsertSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testEnvironmentDeleteChange() {
    ChangeEvent environmentDeleteChangeEvent =
        EnvironmentEntityTestUtils.createEnvironmentChangeEvent(environment, ChangeType.DELETE);
    when(searchDao.removeFromListInMultipleDocuments(
             eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.environments), any()))
        .thenReturn(true);
    boolean isDeleteSuccessful = applicationChangeHandler.handleChange(environmentDeleteChangeEvent);
    assertThat(isDeleteSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testEnvironmentUpdateChange() {
    ChangeEvent environmentUpdateChangeEvent =
        EnvironmentEntityTestUtils.createEnvironmentChangeEvent(environment, ChangeType.UPDATE);
    when(searchDao.updateListInMultipleDocuments(eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.environments),
             anyString(), anyString(), eq(EntityInfoKeys.name)))
        .thenReturn(true);
    boolean isUpdateSuccessful = applicationChangeHandler.handleChange(environmentUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowInsertChange() {
    ChangeEvent workflowInsertChangeEvent =
        WorkflowEntityTestUtils.createWorkflowChangeEvent(workflow, ChangeType.INSERT);
    when(searchDao.appendToListInSingleDocument(
             eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.workflows), eq(workflow.getAppId()), anyMap()))
        .thenReturn(true);
    boolean isInsertSuccessful = applicationChangeHandler.handleChange(workflowInsertChangeEvent);
    assertThat(isInsertSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowDeleteChange() {
    ChangeEvent workflowDeleteChangeEvent =
        WorkflowEntityTestUtils.createWorkflowChangeEvent(workflow, ChangeType.DELETE);
    when(searchDao.removeFromListInMultipleDocuments(
             eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.workflows), any()))
        .thenReturn(true);
    boolean isDeleteSuccessful = applicationChangeHandler.handleChange(workflowDeleteChangeEvent);
    assertThat(isDeleteSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testWorkflowUpdateChange() {
    ChangeEvent workflowUpdateChangeEvent =
        WorkflowEntityTestUtils.createWorkflowChangeEvent(workflow, ChangeType.UPDATE);
    when(searchDao.updateListInMultipleDocuments(eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.workflows),
             anyString(), anyString(), eq(EntityInfoKeys.name)))
        .thenReturn(true);
    boolean isUpdateSuccessful = applicationChangeHandler.handleChange(workflowUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testPipelineInsertChange() {
    ChangeEvent pipelineInsertChangeEvent =
        PipelineEntityTestUtils.createPipelineChangeEvent(pipeline, ChangeType.INSERT);
    when(searchDao.appendToListInSingleDocument(
             eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.pipelines), eq(pipeline.getAppId()), anyMap()))
        .thenReturn(true);
    boolean isInsertSuccessful = applicationChangeHandler.handleChange(pipelineInsertChangeEvent);
    assertThat(isInsertSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testPipelineDeleteChange() {
    ChangeEvent pipelineDeleteChangeEvent =
        PipelineEntityTestUtils.createPipelineChangeEvent(pipeline, ChangeType.DELETE);
    when(searchDao.removeFromListInMultipleDocuments(
             eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.pipelines), any()))
        .thenReturn(true);
    boolean isDeleteSuccessful = applicationChangeHandler.handleChange(pipelineDeleteChangeEvent);
    assertThat(isDeleteSuccessful).isTrue();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void testPipelineUpdateChange() {
    ChangeEvent pipelineUpdateChangeEvent =
        PipelineEntityTestUtils.createPipelineChangeEvent(pipeline, ChangeType.UPDATE);
    when(searchDao.updateListInMultipleDocuments(eq(ApplicationSearchEntity.TYPE), eq(ApplicationViewKeys.pipelines),
             anyString(), anyString(), eq(EntityInfoKeys.name)))
        .thenReturn(true);
    boolean isUpdateSuccessful = applicationChangeHandler.handleChange(pipelineUpdateChangeEvent);
    assertThat(isUpdateSuccessful).isTrue();
  }
}
