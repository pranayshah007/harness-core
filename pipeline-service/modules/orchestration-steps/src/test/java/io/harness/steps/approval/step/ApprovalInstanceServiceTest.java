/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.repositories.ApprovalInstanceRepository;
import io.harness.rule.Owner;
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
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.waiter.WaitNotifyEngine;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.Collections;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(CDC)
@RunWith(PowerMockRunner.class)
@PrepareForTest({NGLogCallback.class, ApprovalInstanceServiceImpl.class})
@PowerMockIgnore({"javax.net.ssl.*"})
public class ApprovalInstanceServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
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
    NGLogCallback ngLogCallback = Mockito.mock(NGLogCallback.class);
    PowerMockito.whenNew(NGLogCallback.class).withAnyArguments().thenReturn(ngLogCallback);
    // default value taken for approver input variable
    approvalInstanceServiceImpl.addHarnessApprovalActivityInTransaction(
        "hello", embeddedUser, harnessApprovalActivityRequestDTO);
    verify(approvalInstanceRepository, times(1)).save(entity);
    verify(ngLogCallback).saveExecutionLog(anyString());
    when(transactionTemplate.execute(any())).thenReturn(harnessApprovalInstance);
    // set instance to waiting to avoid already complete failure
    harnessApprovalInstance.setStatus(ApprovalStatus.WAITING);
    // custom value set for approver input variable
    approvalInstanceServiceImpl.addHarnessApprovalActivityInTransaction(
        "hello", embeddedUser, harnessApprovalActivityRequestDTO1);
    verify(approvalInstanceRepository, times(2)).save(entity);
    verify(ngLogCallback, times(2)).saveExecutionLog(anyString());
    assertThat(approvalInstanceServiceImpl.addHarnessApprovalActivity(
                   "hello", embeddedUser, harnessApprovalActivityRequestDTO))
        .isEqualTo(harnessApprovalInstance);
  }
}
