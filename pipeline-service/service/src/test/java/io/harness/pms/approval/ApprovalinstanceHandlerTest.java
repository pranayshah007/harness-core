package io.harness.pms.approval;

import static io.harness.rule.OwnerRule.vivekveman;
import static io.harness.steps.approval.step.beans.ApprovalType.JIRA_APPROVAL;
import static io.harness.steps.approval.step.beans.ApprovalType.SERVICENOW_APPROVAL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.PipelineServiceIteratorsConfig;
import io.harness.category.element.UnitTests;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator.MongoPersistenceIteratorBuilder;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.jira.JiraApprovalHelperService;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalHelperService;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.mongodb.core.MongoTemplate;

public class ApprovalinstanceHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private PipelineServiceIteratorsConfig iteratorsConfig;
  @Mock private JiraApprovalHelperService jiraApprovalHelperService;
  @Mock private MongoTemplate mongoTemplate;
  @Mock private PersistenceIteratorFactory persistenceIteratorFactory;
  @Mock private ServiceNowApprovalHelperService serviceNowApprovalHelperService;

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testregisterIterators() {
    ArgumentCaptor<MongoPersistenceIteratorBuilder> captor =
        ArgumentCaptor.forClass(MongoPersistenceIteratorBuilder.class);
    ApprovalInstanceHandler approvalInstanceHandler = new ApprovalInstanceHandler(jiraApprovalHelperService,
        mongoTemplate, persistenceIteratorFactory, iteratorsConfig, serviceNowApprovalHelperService);
    approvalInstanceHandler.registerIterators();
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(ApprovalInstanceHandler.class), captor.capture());
    MongoPersistenceIteratorBuilder mongoPersistenceIteratorBuilder = captor.getValue();
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testhandle() {
    ApprovalInstanceHandler approvalInstanceHandler = new ApprovalInstanceHandler(jiraApprovalHelperService,
        mongoTemplate, persistenceIteratorFactory, iteratorsConfig, serviceNowApprovalHelperService);
    JiraApprovalInstance jiraApprovalInstance = JiraApprovalInstance.builder().build();
    ApprovalInstance entity = (ApprovalInstance) jiraApprovalInstance;
    entity.setType(JIRA_APPROVAL);
    approvalInstanceHandler.handle(entity);
    verify(jiraApprovalHelperService, times(1)).handlePollingEvent(jiraApprovalInstance);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testhandleservice() {
    ApprovalInstanceHandler approvalInstanceHandler = new ApprovalInstanceHandler(jiraApprovalHelperService,
        mongoTemplate, persistenceIteratorFactory, iteratorsConfig, serviceNowApprovalHelperService);
    ServiceNowApprovalInstance serviceNowApprovalInstance = ServiceNowApprovalInstance.builder().build();
    ApprovalInstance entity = (ApprovalInstance) serviceNowApprovalInstance;
    entity.setType(SERVICENOW_APPROVAL);
    approvalInstanceHandler.handle(entity);
    verify(serviceNowApprovalHelperService, times(1)).handlePollingEvent(serviceNowApprovalInstance);
  }
}
