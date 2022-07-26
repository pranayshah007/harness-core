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
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.repositories.ApprovalInstanceRepository;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.waiter.WaitNotifyEngine;

import com.mongodb.client.result.UpdateResult;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;
@OwnedBy(CDC)
public class ApprovalInstanceServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private ApprovalInstanceRepository approvalInstanceRepository;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private PlanExecutionService planExecutionService;
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testsave() {
    ApprovalInstanceServiceImpl approvalInstanceServiceImpl = new ApprovalInstanceServiceImpl(
        approvalInstanceRepository, transactionTemplate, waitNotifyEngine, planExecutionService);
    ApprovalInstance serviceNowApprovalInstance = ServiceNowApprovalInstance.builder().build();

    ApprovalInstance entity = (ApprovalInstance) serviceNowApprovalInstance;

    when(approvalInstanceRepository.save(serviceNowApprovalInstance)).thenReturn(entity);

    assertThat(approvalInstanceServiceImpl.save(serviceNowApprovalInstance)).isEqualTo(entity);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testget() {
    ApprovalInstanceServiceImpl approvalInstanceServiceImpl = new ApprovalInstanceServiceImpl(
        approvalInstanceRepository, transactionTemplate, waitNotifyEngine, planExecutionService);
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
    ApprovalInstanceServiceImpl approvalInstanceServiceImpl = new ApprovalInstanceServiceImpl(
        approvalInstanceRepository, transactionTemplate, waitNotifyEngine, planExecutionService);
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
    ApprovalInstanceServiceImpl approvalInstanceServiceImpl = new ApprovalInstanceServiceImpl(
        approvalInstanceRepository, transactionTemplate, waitNotifyEngine, planExecutionService);
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
    ApprovalInstanceServiceImpl approvalInstanceServiceImpl = new ApprovalInstanceServiceImpl(
        approvalInstanceRepository, transactionTemplate, waitNotifyEngine, planExecutionService);

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
    ApprovalInstanceServiceImpl approvalInstanceServiceImpl = new ApprovalInstanceServiceImpl(
        approvalInstanceRepository, transactionTemplate, waitNotifyEngine, planExecutionService);

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
    ApprovalInstanceServiceImpl approvalInstanceServiceImpl = new ApprovalInstanceServiceImpl(
        approvalInstanceRepository, transactionTemplate, waitNotifyEngine, planExecutionService);

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
    ApprovalInstanceServiceImpl approvalInstanceServiceImpl = new ApprovalInstanceServiceImpl(
        approvalInstanceRepository, transactionTemplate, waitNotifyEngine, planExecutionService);
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
    ApprovalInstanceServiceImpl approvalInstanceServiceImpl = new ApprovalInstanceServiceImpl(
        approvalInstanceRepository, transactionTemplate, waitNotifyEngine, planExecutionService);
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
  public void testaddHarnessApprovalActivity() {
    ApprovalInstanceServiceImpl approvalInstanceServiceImpl = new ApprovalInstanceServiceImpl(
        approvalInstanceRepository, transactionTemplate, waitNotifyEngine, planExecutionService);
    ApproversDTO approversDTO = ApproversDTO.builder().minimumCount(0).build();
    HarnessApprovalInstance harnessApprovalInstance = HarnessApprovalInstance.builder().approvers(approversDTO).build();
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

    HarnessApprovalInstance instance = HarnessApprovalInstance.builder().build();

    instance.setStatus(ApprovalStatus.APPROVED);
    when(approvalInstanceRepository.save(any())).thenReturn(instance);
    approvalInstanceServiceImpl.addHarnessApprovalActivityInTransaction(
        "hello", embeddedUser, harnessApprovalActivityRequestDTO);
    verify(approvalInstanceRepository, times(1)).save(entity);

    when(transactionTemplate.execute(any())).thenReturn(harnessApprovalInstance);
    assertThat(approvalInstanceServiceImpl.addHarnessApprovalActivity(
                   "hello", embeddedUser, harnessApprovalActivityRequestDTO))
        .isEqualTo(harnessApprovalInstance);
  }
}
