/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.jira.JiraIssueNG;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.repositories.ApprovalInstanceRepository;
import io.harness.rule.Owner;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;
import io.harness.steps.approval.step.harness.beans.ApproverInput;
import io.harness.steps.approval.step.harness.beans.ApproverInputInfoDTO;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance.JiraApprovalInstanceKeys;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance.ServiceNowApprovalInstanceKeys;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import dev.morphia.mapping.Mapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
public class ApprovalInstanceServiceTest extends CategoryTest {
  @Mock private ApprovalInstanceRepository approvalInstanceRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private PlanExecutionService planExecutionService;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Inject @InjectMocks private ApprovalInstanceServiceImpl approvalInstanceServiceImpl;

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testsave() {
    ApprovalInstance serviceNowApprovalInstance = ServiceNowApprovalInstance.builder().build();

    ApprovalInstance entity = (ApprovalInstance) serviceNowApprovalInstance;

    when(approvalInstanceRepository.save(serviceNowApprovalInstance)).thenReturn(entity);

    assertThat(approvalInstanceServiceImpl.save(serviceNowApprovalInstance)).isEqualTo(entity);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testget() {
    ServiceNowApprovalInstance serviceNowApprovalInstance = ServiceNowApprovalInstance.builder().build();
    ApprovalInstance entity = (ApprovalInstance) serviceNowApprovalInstance;
    Optional<ApprovalInstance> optional = Optional.of(entity);
    when(approvalInstanceRepository.findById(any())).thenReturn(optional);

    assertThat(approvalInstanceServiceImpl.get("hello")).isEqualTo(entity);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetHarnessApprovalInstance() {
    HarnessApprovalInstance harnessApprovalInstance = HarnessApprovalInstance.builder().build();
    ApprovalInstance entity = (ApprovalInstance) harnessApprovalInstance;
    Optional<ApprovalInstance> optional = Optional.of(entity);
    when(approvalInstanceRepository.findById(any())).thenReturn(optional);
    entity.setType(ApprovalType.HARNESS_APPROVAL);

    assertThat(approvalInstanceServiceImpl.getHarnessApprovalInstance("hello")).isEqualTo(entity);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testdelete() {
    HarnessApprovalInstance harnessApprovalInstance = HarnessApprovalInstance.builder().build();

    ApprovalInstance entity = (ApprovalInstance) harnessApprovalInstance;
    entity.setType(ApprovalType.HARNESS_APPROVAL);
    Optional<ApprovalInstance> optional = Optional.of(entity);
    when(approvalInstanceRepository.findById(any())).thenReturn(optional);

    approvalInstanceServiceImpl.delete("hello");
    verify(approvalInstanceRepository, times(1)).deleteById(anyString());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testabortByNodeExecutionId() {
    approvalInstanceServiceImpl.abortByNodeExecutionId("hello");
    verify(approvalInstanceRepository, times(1))
        .updateFirst(eq(new Query(Criteria.where(ApprovalInstanceKeys.nodeExecutionId).is("hello"))
                             .addCriteria(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING))),
            eq(new Update().set(ApprovalInstanceKeys.status, ApprovalStatus.ABORTED)));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testexpireByNodeExecutionId() {
    approvalInstanceServiceImpl.expireByNodeExecutionId("hello");
    verify(approvalInstanceRepository, times(1))
        .updateFirst(eq(new Query(Criteria.where(ApprovalInstanceKeys.nodeExecutionId).is("hello"))
                             .addCriteria(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING))),
            eq(new Update().set(ApprovalInstanceKeys.status, ApprovalStatus.EXPIRED)));
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testmarkExpiredInstances() {
    UpdateResult updateResult = mock(UpdateResult.class);

    when(approvalInstanceRepository.updateMulti(any(), any())).thenReturn(updateResult);
    when(updateResult.getModifiedCount()).thenReturn(0L);
    approvalInstanceServiceImpl.markExpiredInstances();
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    verify(approvalInstanceRepository, times(1))
        .updateMulti(queryArgumentCaptor.capture(), updateArgumentCaptor.capture());
    Query query = queryArgumentCaptor.getValue();
    Update update = updateArgumentCaptor.getValue();
    assertThat(query.getQueryObject()).hasSize(2);
    assertThat(update.getUpdateObject().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testfinalizeStatusServicenow() {
    ServiceNowApprovalInstance serviceNowApprovalInstance = ServiceNowApprovalInstance.builder().build();
    ApprovalInstance entity = (ApprovalInstance) serviceNowApprovalInstance;
    entity.setType(ApprovalType.SERVICENOW_APPROVAL);

    when(approvalInstanceRepository.updateFirst(any(), any())).thenReturn(entity);

    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("PlanExecutionId").build();
    entity.setAmbiance(ambiance);
    when(planExecutionService.calculateStatusExcluding(any(), any())).thenReturn(Status.SUCCEEDED);
    approvalInstanceServiceImpl.finalizeStatus("hello", ApprovalStatus.APPROVED, "errormessage", null);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    verify(approvalInstanceRepository).updateFirst(queryArgumentCaptor.capture(), updateArgumentCaptor.capture());
    Query query = queryArgumentCaptor.getValue();
    Update update = updateArgumentCaptor.getValue();
    assertThat(query.getQueryObject()).hasSize(2);
    assertThat(update.getUpdateObject().size()).isEqualTo(1);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testfinalizeStatusJira() {
    JiraApprovalInstance jiraApprovalInstance = JiraApprovalInstance.builder().build();
    ApprovalInstance entity = (ApprovalInstance) jiraApprovalInstance;
    entity.setType(ApprovalType.JIRA_APPROVAL);
    when(approvalInstanceRepository.updateFirst(any(), any())).thenReturn(entity);
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("PlanExecutionId").build();
    entity.setAmbiance(ambiance);
    when(planExecutionService.calculateStatusExcluding(any(), any())).thenReturn(Status.SUCCEEDED);
    approvalInstanceServiceImpl.finalizeStatus("hello", ApprovalStatus.APPROVED, "errormessage", null);
    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    verify(approvalInstanceRepository).updateFirst(queryArgumentCaptor.capture(), updateArgumentCaptor.capture());
    Query query = queryArgumentCaptor.getValue();
    Update update = updateArgumentCaptor.getValue();
    assertThat(query.getQueryObject()).hasSize(2);
    assertThat(update.getUpdateObject().size()).isEqualTo(1);
  }
  // eq captor
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testaddHarnessApprovalActivity() throws Exception {
    ApproversDTO approversDTO = ApproversDTO.builder().minimumCount(0).build();
    HarnessApprovalInstance harnessApprovalInstance =
        HarnessApprovalInstance.builder()
            .approvers(approversDTO)
            .approverInputs(Collections.singletonList(
                ApproverInputInfoDTO.builder().name("NAME").defaultValue("DEFAULT_VAL").build()))
            .build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("PlanExecutionId").build();
    harnessApprovalInstance.setAmbiance(ambiance);
    ApprovalInstance entity = (ApprovalInstance) harnessApprovalInstance;
    entity.setDeadline(Long.MAX_VALUE);
    entity.setType(ApprovalType.HARNESS_APPROVAL);
    entity.setStatus(ApprovalStatus.WAITING);
    Optional<ApprovalInstance> optional = Optional.of(entity);
    when(approvalInstanceRepository.findById(any())).thenReturn(optional);
    EmbeddedUser embeddedUser = EmbeddedUser.builder().name("embeddedUser").build();

    HarnessApprovalActivityRequestDTO harnessApprovalActivityRequestDTO =
        HarnessApprovalActivityRequestDTO.builder().build();
    HarnessApprovalActivityRequestDTO harnessApprovalActivityRequestDTO1 =
        HarnessApprovalActivityRequestDTO.builder()
            .action(HarnessApprovalAction.APPROVE)
            .comments("comment")
            .approverInputs(Collections.singletonList(ApproverInput.builder().name("NAME").value("CUSTOM_VAL").build()))
            .build();
    HarnessApprovalInstance instance = HarnessApprovalInstance.builder().build();

    instance.setStatus(ApprovalStatus.APPROVED);
    when(approvalInstanceRepository.save(any())).thenReturn(instance);
    try (MockedConstruction<NGLogCallback> ngLogCallback = mockConstruction(NGLogCallback.class)) {
      // default value taken for approver input variable
      approvalInstanceServiceImpl.addHarnessApprovalActivityInTransaction(
          "hello", embeddedUser, harnessApprovalActivityRequestDTO);
      verify(approvalInstanceRepository, times(1)).save(entity);
      verify(ngLogCallback.constructed().get(0)).saveExecutionLog(anyString());
      when(transactionTemplate.execute(any())).thenReturn(harnessApprovalInstance);
      // set instance to waiting to avoid already complete failure
      harnessApprovalInstance.setStatus(ApprovalStatus.WAITING);
      // custom value set for approver input variable
      approvalInstanceServiceImpl.addHarnessApprovalActivityInTransaction(
          "hello", embeddedUser, harnessApprovalActivityRequestDTO1);
      verify(approvalInstanceRepository, times(2)).save(entity);
      verify(ngLogCallback.constructed().get(1), times(1)).saveExecutionLog(anyString());
      assertThat(approvalInstanceServiceImpl.addHarnessApprovalActivity(
                     "hello", embeddedUser, harnessApprovalActivityRequestDTO))
          .isEqualTo(harnessApprovalInstance);
    }
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testdeleteByNodeExecutionIdsWhenLessThanBatchSize() {
    Set<String> nodeExecutionIds = new HashSet<>();
    nodeExecutionIds.add(UUIDGenerator.generateUuid());
    nodeExecutionIds.add(UUIDGenerator.generateUuid());
    when(approvalInstanceRepository.deleteAllByNodeExecutionIdIn(any())).thenReturn(2L);

    approvalInstanceServiceImpl.deleteByNodeExecutionIds(nodeExecutionIds);
    ArgumentCaptor<Set<String>> setArgumentCaptor = ArgumentCaptor.forClass(Set.class);
    verify(approvalInstanceRepository, times(1)).deleteAllByNodeExecutionIdIn(setArgumentCaptor.capture());
    Set<String> setArgs = setArgumentCaptor.getValue();
    assertThat(setArgs).isEqualTo(nodeExecutionIds);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testdeleteByNodeExecutionIdsWhenEqualToBatchSize() {
    Set<String> nodeExecutionIds = new HashSet<>();
    for (int i = 0; i < 500; i++) {
      nodeExecutionIds.add(UUIDGenerator.generateUuid());
    }
    // only return value if called with valid query else throw exception
    when(approvalInstanceRepository.deleteAllByNodeExecutionIdIn(any())).thenAnswer((Answer<Long>) invocation -> {
      Object[] args = invocation.getArguments();
      Set<String> setNodeExecutionId = (Set<String>) args[0];
      if (setNodeExecutionId.equals(nodeExecutionIds)) {
        return 500L;
      }
      throw new Exception();
    });

    approvalInstanceServiceImpl.deleteByNodeExecutionIds(nodeExecutionIds);
    verify(approvalInstanceRepository, times(1)).deleteAllByNodeExecutionIdIn(any());
    verifyNoMoreInteractions(approvalInstanceRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testdeleteByNodeExecutionIdsWhenMoreThanBatchSize() {
    Set<String> nodeExecutionIds = new HashSet<>();
    for (int i = 0; i < 600; i++) {
      nodeExecutionIds.add(UUIDGenerator.generateUuid());
    }
    // only return value if called with valid query else throw exception
    when(approvalInstanceRepository.deleteAllByNodeExecutionIdIn(any())).thenAnswer((Answer<Long>) invocation -> {
      Object[] args = invocation.getArguments();
      Set<String> setNodeExecutionId = (Set<String>) args[0];
      if (setNodeExecutionId.size() == 500) {
        return 500L;
      } else if (setNodeExecutionId.size() == 100) {
        return 100L;
      }
      throw new Exception();
    });

    approvalInstanceServiceImpl.deleteByNodeExecutionIds(nodeExecutionIds);
    verify(approvalInstanceRepository, times(2)).deleteAllByNodeExecutionIdIn(any());
    verifyNoMoreInteractions(approvalInstanceRepository);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testIsNodeExecutionOfApprovalStepType() {
    assertThat(approvalInstanceServiceImpl.isNodeExecutionOfApprovalStepType(null)).isFalse();
    assertThat(approvalInstanceServiceImpl.isNodeExecutionOfApprovalStepType(NodeExecution.builder().build()))
        .isFalse();
    assertThat(approvalInstanceServiceImpl.isNodeExecutionOfApprovalStepType(
                   NodeExecution.builder().stepType(StepType.newBuilder().build()).build()))
        .isFalse();
    assertThat(approvalInstanceServiceImpl.isNodeExecutionOfApprovalStepType(
                   NodeExecution.builder()
                       .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP_GROUP).build())
                       .build()))
        .isFalse();
    assertThat(
        approvalInstanceServiceImpl.isNodeExecutionOfApprovalStepType(
            NodeExecution.builder().stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build()).build()))
        .isFalse();
    assertThat(
        approvalInstanceServiceImpl.isNodeExecutionOfApprovalStepType(
            NodeExecution.builder()
                .stepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).setType("notApproval").build())
                .build()))
        .isFalse();
    assertThat(approvalInstanceServiceImpl.isNodeExecutionOfApprovalStepType(
                   NodeExecution.builder()
                       .stepType(StepType.newBuilder()
                                     .setStepCategory(StepCategory.STEP)
                                     .setType(StepSpecTypeConstants.CUSTOM_APPROVAL)
                                     .build())
                       .build()))
        .isTrue();
    assertThat(approvalInstanceServiceImpl.isNodeExecutionOfApprovalStepType(
                   NodeExecution.builder()
                       .stepType(StepType.newBuilder()
                                     .setStepCategory(StepCategory.STEP)
                                     .setType(StepSpecTypeConstants.JIRA_APPROVAL)
                                     .build())
                       .build()))
        .isTrue();
    assertThat(approvalInstanceServiceImpl.isNodeExecutionOfApprovalStepType(
                   NodeExecution.builder()
                       .stepType(StepType.newBuilder()
                                     .setStepCategory(StepCategory.STEP)
                                     .setType(StepSpecTypeConstants.SERVICENOW_APPROVAL)
                                     .build())
                       .build()))
        .isTrue();
    assertThat(approvalInstanceServiceImpl.isNodeExecutionOfApprovalStepType(
                   NodeExecution.builder()
                       .stepType(StepType.newBuilder()
                                     .setStepCategory(StepCategory.STEP)
                                     .setType(StepSpecTypeConstants.HARNESS_APPROVAL)
                                     .build())
                       .build()))
        .isTrue();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUpdateTicketFieldsInServiceNowApprovalInstance() {
    ServiceNowApprovalInstance serviceNowApprovalInstance = ServiceNowApprovalInstance.builder().build();
    serviceNowApprovalInstance.setId("id");

    assertThatCode(() -> {
      approvalInstanceServiceImpl.updateTicketFieldsInServiceNowApprovalInstance(serviceNowApprovalInstance, null);
    }).doesNotThrowAnyException();
    assertThatCode(() -> {
      approvalInstanceServiceImpl.updateTicketFieldsInServiceNowApprovalInstance(
          serviceNowApprovalInstance, ServiceNowTicketNG.builder().build());
    }).doesNotThrowAnyException();
    assertThatCode(() -> {
      approvalInstanceServiceImpl.updateTicketFieldsInServiceNowApprovalInstance(
          serviceNowApprovalInstance, ServiceNowTicketNG.builder().fields(new HashMap<>()).build());
    }).doesNotThrowAnyException();
    Map<String, ServiceNowFieldValueNG> serviceNowTicketFields = new HashMap<>();
    serviceNowTicketFields.put(
        "dummyField", ServiceNowFieldValueNG.builder().displayValue("displayValue").value("value").build());
    assertThatCode(() -> {
      approvalInstanceServiceImpl.updateTicketFieldsInServiceNowApprovalInstance(
          serviceNowApprovalInstance, ServiceNowTicketNG.builder().fields(serviceNowTicketFields).build());
    }).doesNotThrowAnyException();
    verify(approvalInstanceRepository, times(0)).updateFirst(any(), any());

    serviceNowTicketFields.put("state", ServiceNowFieldValueNG.builder().displayValue("Open").value("open").build());

    Map<String, ServiceNowFieldValueNG> updatedApprovalInstanceTicketFields = new HashMap<>();
    updatedApprovalInstanceTicketFields.put(
        "state", ServiceNowFieldValueNG.builder().displayValue("Open").value("open").build());

    approvalInstanceServiceImpl.updateTicketFieldsInServiceNowApprovalInstance(
        serviceNowApprovalInstance, ServiceNowTicketNG.builder().fields(serviceNowTicketFields).build());
    verify(approvalInstanceRepository, times(1))
        .updateFirst(new Query(Criteria.where(Mapper.ID_KEY).is(serviceNowApprovalInstance.getId()))
                         .addCriteria(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING)),
            new Update().set(ServiceNowApprovalInstanceKeys.ticketFields, updatedApprovalInstanceTicketFields));

    // settings service now instance fields to the same value
    serviceNowApprovalInstance.setTicketFields(updatedApprovalInstanceTicketFields);
    approvalInstanceServiceImpl.updateTicketFieldsInServiceNowApprovalInstance(
        serviceNowApprovalInstance, ServiceNowTicketNG.builder().fields(serviceNowTicketFields).build());
    verify(approvalInstanceRepository, times(1)).updateFirst(any(), any());

    // settings service now instance fields to different value
    updatedApprovalInstanceTicketFields.put(
        "state", ServiceNowFieldValueNG.builder().displayValue("Closed").value("closed").build());

    serviceNowApprovalInstance.setTicketFields(updatedApprovalInstanceTicketFields);
    approvalInstanceServiceImpl.updateTicketFieldsInServiceNowApprovalInstance(
        serviceNowApprovalInstance, ServiceNowTicketNG.builder().fields(serviceNowTicketFields).build());
    verify(approvalInstanceRepository, times(2)).updateFirst(any(), any());
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testUpdateTicketFieldsInJiraApprovalInstance() {
    JiraApprovalInstance jiraApprovalInstance = JiraApprovalInstance.builder().build();
    jiraApprovalInstance.setId("id");
    JiraIssueNG jiraIssueNG = new JiraIssueNG();

    assertThatCode(() -> {
      approvalInstanceServiceImpl.updateTicketFieldsInJiraApprovalInstance(jiraApprovalInstance, null);
    }).doesNotThrowAnyException();
    assertThatCode(() -> {
      approvalInstanceServiceImpl.updateTicketFieldsInJiraApprovalInstance(jiraApprovalInstance, jiraIssueNG);
    }).doesNotThrowAnyException();

    jiraIssueNG.setFields(new HashMap<>());

    assertThatCode(() -> {
      approvalInstanceServiceImpl.updateTicketFieldsInJiraApprovalInstance(jiraApprovalInstance, jiraIssueNG);
    }).doesNotThrowAnyException();
    Map<String, Object> jiraTicketFields = new HashMap<>();
    jiraTicketFields.put("dummyField", "dummyValue");
    jiraIssueNG.setFields(jiraTicketFields);

    assertThatCode(() -> {
      approvalInstanceServiceImpl.updateTicketFieldsInJiraApprovalInstance(jiraApprovalInstance, jiraIssueNG);
    }).doesNotThrowAnyException();
    verify(approvalInstanceRepository, times(0)).updateFirst(any(), any());

    jiraTicketFields.put("Status", "To Do");
    jiraIssueNG.setFields(jiraTicketFields);

    Map<String, Object> updatedApprovalInstanceTicketFields = new HashMap<>();
    updatedApprovalInstanceTicketFields.put("Status", "To Do");

    approvalInstanceServiceImpl.updateTicketFieldsInJiraApprovalInstance(jiraApprovalInstance, jiraIssueNG);
    verify(approvalInstanceRepository, times(1))
        .updateFirst(new Query(Criteria.where(Mapper.ID_KEY).is(jiraApprovalInstance.getId()))
                         .addCriteria(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING)),
            new Update().set(JiraApprovalInstanceKeys.ticketFields, updatedApprovalInstanceTicketFields));

    // settings service now instance fields to the same value
    jiraApprovalInstance.setTicketFields(updatedApprovalInstanceTicketFields);
    approvalInstanceServiceImpl.updateTicketFieldsInJiraApprovalInstance(jiraApprovalInstance, jiraIssueNG);
    verify(approvalInstanceRepository, times(1)).updateFirst(any(), any());

    // settings service now instance fields to different value
    updatedApprovalInstanceTicketFields.put("Status", "In Progress");

    jiraApprovalInstance.setTicketFields(updatedApprovalInstanceTicketFields);
    approvalInstanceServiceImpl.updateTicketFieldsInJiraApprovalInstance(jiraApprovalInstance, jiraIssueNG);
    verify(approvalInstanceRepository, times(2)).updateFirst(any(), any());
  }
}
