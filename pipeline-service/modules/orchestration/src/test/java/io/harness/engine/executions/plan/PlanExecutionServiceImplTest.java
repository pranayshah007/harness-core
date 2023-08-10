/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.execution.PlanExecution.PlanExecutionKeys;
import static io.harness.pms.contracts.execution.Status.PAUSED;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationTestHelper;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.statusupdate.NodeStatusUpdateHandlerFactory;
import io.harness.engine.interrupts.statusupdate.PausedStepStatusUpdate;
import io.harness.engine.interrupts.statusupdate.QueuedLicenseLimitReachedStatusUpdate;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.engine.observers.PlanExecutionDeleteObserver;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.observer.Subject;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.PlanExecutionProjectionConstants;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.repositories.PlanExecutionRepository;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionServiceImplTest extends OrchestrationTestBase {
  @Mock NodeStatusUpdateHandlerFactory nodeStatusUpdateHandlerFactory;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PausedStepStatusUpdate pausedStepStatusUpdate;
  @Mock QueuedLicenseLimitReachedStatusUpdate queuedLicenseLimitReachedStatusUpdate;
  @Mock Subject<PlanExecutionDeleteObserver> planExecutionDeleteObserverSubject;
  @Spy @Inject @InjectMocks PlanExecutionService planExecutionService;

  @Test

  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestSave() {
    String planExecutionId = generateUuid();
    PlanExecution savedExecution = planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).build());
    assertThat(savedExecution.getUuid()).isEqualTo(planExecutionId);
  }

  @Test

  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestFindAllByPlanExecutionIdIn() {
    String planExecutionId = generateUuid();
    PlanExecution savedExecution = planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).build());
    assertThat(savedExecution.getUuid()).isEqualTo(planExecutionId);

    List<PlanExecution> planExecutions =
        planExecutionService.findAllByPlanExecutionIdIn(ImmutableList.of(planExecutionId));

    assertThat(planExecutions).isNotEmpty();
    assertThat(planExecutions.size()).isEqualTo(1);
    assertThat(planExecutions).extracting(PlanExecution::getUuid).containsExactly(planExecutionId);
  }

  @Test

  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestCalculateStatusExcluding() {
    String excludedNodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    PlanExecution savedExecution =
        planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).status(Status.PAUSED).build());
    assertThat(savedExecution.getUuid()).isEqualTo(planExecutionId);

    List<NodeExecution> nodeExecutionList =
        Arrays.asList(NodeExecution.builder().uuid(excludedNodeExecutionId).status(Status.QUEUED).build(),
            NodeExecution.builder().uuid(generateUuid()).status(Status.RUNNING).build());

    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(nodeExecutionList.iterator());
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesIterator(
             eq(planExecutionId), eq(NodeProjectionUtils.withStatus)))
        .thenReturn(iterator);

    Status status = planExecutionService.calculateStatusExcluding(planExecutionId, excludedNodeExecutionId);
    assertThat(status).isEqualTo(Status.RUNNING);
  }

  @Test

  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void shouldTestFindAllByAccountIdAndOrgIdAndProjectIdAndLastUpdatedAtInBetweenTimestamps() {
    String planExecutionId = generateUuid();
    String accountId = "TestAccountId";
    String orgId = "TestOrgId";
    String projectId = "TestProjectId";
    long startTS = System.currentTimeMillis();

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);

    PlanExecution savedExecution = planExecutionService.save(PlanExecution.builder()
                                                                 .uuid(planExecutionId)
                                                                 .setupAbstractions(setupAbstractions)
                                                                 .lastUpdatedAt(System.currentTimeMillis())
                                                                 .build());
    assertThat(savedExecution.getUuid()).isEqualTo(planExecutionId);
    assertThat(savedExecution.getSetupAbstractions().get(SetupAbstractionKeys.accountId)).isEqualTo(accountId);
    assertThat(savedExecution.getSetupAbstractions().get(SetupAbstractionKeys.orgIdentifier)).isEqualTo(orgId);
    assertThat(savedExecution.getSetupAbstractions().get(SetupAbstractionKeys.projectIdentifier)).isEqualTo(projectId);

    long endTS = System.currentTimeMillis() + 5 * 60 * 1000;

    List<PlanExecution> planExecutions =
        planExecutionService.findAllByAccountIdAndOrgIdAndProjectIdAndLastUpdatedAtInBetweenTimestamps(
            accountId, orgId, projectId, startTS, endTS);

    assertThat(planExecutions).isNotEmpty();
    assertThat(planExecutions.size()).isEqualTo(1);
    assertThat(planExecutions).extracting(PlanExecution::getUuid).containsExactly(planExecutionId);
    assertThat(planExecutions).extracting(PlanExecution::getSetupAbstractions).containsExactly(setupAbstractions);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestUpdateStatusWithoutOps() {
    String uuid = generateUuid();
    planExecutionService.updateStatus(uuid, Status.RUNNING);
    verify(planExecutionService, times(1)).updateStatus(uuid, Status.RUNNING, null);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestUpdateStatusWithOps() {
    String uuid = generateUuid();
    Consumer<Update> op = ops -> ops.set(PlanExecutionKeys.endTs, System.currentTimeMillis());
    planExecutionService.updateStatus(uuid, Status.RUNNING, op);
    verify(planExecutionService, times(1)).updateStatusForceful(uuid, Status.RUNNING, op, false);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestUpdateStatusForcefulReturningNull() {
    String planExecutionId = generateUuid();
    Consumer<Update> op = ops -> ops.set(PlanExecutionKeys.endTs, System.currentTimeMillis());
    PlanExecution planExecution = planExecutionService.updateStatusForceful(planExecutionId, Status.ABORTED, op, true);
    assertNull(planExecution);
    planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).status(SUCCEEDED).build());
    planExecution = planExecutionService.updateStatusForceful(planExecutionId, Status.ABORTED, op, false);
    assertNull(planExecution);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestUpdateStatusForceful() {
    String planExecutionId = generateUuid();
    long endTs = System.currentTimeMillis();
    Consumer<Update> op = ops -> ops.set(PlanExecutionKeys.endTs, endTs);
    planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).status(PAUSED).build());
    PlanExecution planExecution = planExecutionService.updateStatusForceful(planExecutionId, Status.ABORTED, op, false);
    assertEquals(planExecution.getUuid(), planExecutionId);
    assertEquals(planExecution.getStatus(), Status.ABORTED);
    assertEquals(planExecution.getEndTs().longValue(), endTs);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestGet() {
    String planExecutionId = generateUuid();
    planExecutionService.save(PlanExecution.builder().uuid(planExecutionId).build());
    PlanExecution planExecution = planExecutionService.get(planExecutionId);
    assertEquals(planExecution.getUuid(), planExecutionId);
  }

  @Test

  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldFetchPlanExecutionsByStatus() {
    String planExecutionId = generateUuid();
    String accountId = "TestAccountId";
    String orgId = "TestOrgId";
    String projectId = "TestProjectId";

    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, accountId);
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, orgId);
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, projectId);

    planExecutionService.save(PlanExecution.builder()
                                  .uuid(planExecutionId)
                                  .setupAbstractions(setupAbstractions)
                                  .status(Status.RUNNING)
                                  .lastUpdatedAt(System.currentTimeMillis())
                                  .build());
    planExecutionService.save(PlanExecution.builder()
                                  .uuid(generateUuid())
                                  .setupAbstractions(setupAbstractions)
                                  .status(Status.RUNNING)
                                  .lastUpdatedAt(System.currentTimeMillis())
                                  .build());
    planExecutionService.save(PlanExecution.builder()
                                  .uuid(generateUuid())
                                  .setupAbstractions(setupAbstractions)
                                  .status(Status.WAIT_STEP_RUNNING)
                                  .lastUpdatedAt(System.currentTimeMillis())
                                  .build());
    planExecutionService.save(PlanExecution.builder()
                                  .uuid(generateUuid())
                                  .setupAbstractions(setupAbstractions)
                                  .status(Status.APPROVAL_WAITING)
                                  .lastUpdatedAt(System.currentTimeMillis())
                                  .build());

    List<PlanExecution> finalList = new LinkedList<>();
    try (CloseableIterator<PlanExecution> iterator =
             planExecutionService.fetchPlanExecutionsByStatusFromAnalytics(StatusUtils.activeStatuses(),
                 ImmutableSet.of(PlanExecutionKeys.setupAbstractions, PlanExecutionKeys.metadata))) {
      while (iterator.hasNext()) {
        finalList.add(iterator.next());
      }
    }

    assertEquals(finalList.size(), 4);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestOnNodeStatusUpdate() {
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder().nodeExecution(NodeExecution.builder().status(Status.PAUSED).build()).build();
    doReturn(pausedStepStatusUpdate).when(nodeStatusUpdateHandlerFactory).obtainStepStatusUpdate(nodeUpdateInfo);
    planExecutionService.onNodeStatusUpdate(nodeUpdateInfo);
    verify(pausedStepStatusUpdate, times(1)).handleNodeStatusUpdate(nodeUpdateInfo);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestOnNodeStatusUpdateWithQueueLimit() {
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder().status(Status.QUEUED_LICENSE_LIMIT_REACHED).build())
            .build();
    doReturn(queuedLicenseLimitReachedStatusUpdate)
        .when(nodeStatusUpdateHandlerFactory)
        .obtainStepStatusUpdate(nodeUpdateInfo);
    planExecutionService.onNodeStatusUpdate(nodeUpdateInfo);
    verify(queuedLicenseLimitReachedStatusUpdate, times(1)).handleNodeStatusUpdate(nodeUpdateInfo);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestFindPrevUnTerminatedPlanExecutionsByExecutionTag() {
    String planExecutionId = generateUuid();
    String executionTag = "exec";
    long createdAt = System.currentTimeMillis();
    PlanExecution planExecution =
        PlanExecution.builder()
            .uuid(planExecutionId)
            .status(Status.RUNNING)
            .createdAt(createdAt)
            .metadata(
                ExecutionMetadata.newBuilder()
                    .setTriggerInfo(
                        ExecutionTriggerInfo.newBuilder()
                            .setTriggeredBy(TriggeredBy.newBuilder()
                                                .putExtraInfo("execution_trigger_tag_needed_for_abort", executionTag)
                                                .build())
                            .build())
                    .build())
            .build();
    planExecutionService.save(planExecution);
    List<PlanExecution> planExecution1 = planExecutionService.findPrevUnTerminatedPlanExecutionsByExecutionTag(
        PlanExecution.builder().createdAt(System.currentTimeMillis()).build(), executionTag);
    assertEquals(planExecution1.size(), 1);
    assertEquals(planExecutionId, planExecution1.get(0).getUuid());
    assertEquals(createdAt, planExecution1.get(0).getCreatedAt().longValue());
    assertEquals(Status.RUNNING, planExecution1.get(0).getStatus());
    assertEquals(executionTag,
        planExecution1.get(0).getMetadata().getTriggerInfo().getTriggeredBy().getExtraInfo().get(
            "execution_trigger_tag_needed_for_abort"));
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestCalculateStatus() {
    String planExecutionId = generateUuid();
    doReturn(ImmutableList.of(Status.RUNNING, Status.FAILED, Status.ABORTED))
        .when(nodeExecutionService)
        .fetchNodeExecutionsStatusesWithoutOldRetries(planExecutionId);
    Status status = planExecutionService.calculateStatus(planExecutionId);
    assertEquals(Status.ABORTED, status);
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestUpdateCalculatedStatus() {
    String planExecutionId = generateUuid();
    doReturn(ImmutableList.of(Status.RUNNING, Status.PAUSED))
        .when(nodeExecutionService)
        .fetchNodeExecutionsStatusesWithoutOldRetries(planExecutionId);
    Status status = planExecutionService.calculateStatus(planExecutionId);
    planExecutionService.save(PlanExecution.builder().status(Status.QUEUED).uuid(planExecutionId).build());
    PlanExecution planExecution = planExecutionService.updateCalculatedStatus(planExecutionId);
    verify(planExecutionService, times(1)).updateStatus(planExecutionId, status);
    assertEquals(Status.RUNNING, planExecution.getStatus());
    assertEquals(planExecutionId, planExecution.getUuid());
  }

  @Test

  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestFindByStatusWithProjections() {
    planExecutionService.save(PlanExecution.builder().status(Status.NO_OP).build());
    planExecutionService.save(PlanExecution.builder().status(Status.ABORTED).build());
    planExecutionService.save(PlanExecution.builder().status(Status.SUCCEEDED).build());
    List<PlanExecution> planExecutions = planExecutionService.findByStatusWithProjections(
        ImmutableSet.of(Status.ABORTED, Status.SUCCEEDED, Status.FAILED),
        ImmutableSet.of(PlanExecutionKeys.uuid, PlanExecutionKeys.status, PlanExecutionKeys.endTs));
    assertEquals(2, planExecutions.size());
    assertEquals(Status.ABORTED, planExecutions.get(0).getStatus());
    assertEquals(Status.SUCCEEDED, planExecutions.get(1).getStatus());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestDeleteAllPlanExecutionAndMetadata() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(planExecutionService).set("mongoTemplate", mongoTemplateMock);
    PlanExecutionRepository planExecutionRepositoryMock = Mockito.mock(PlanExecutionRepository.class);
    Reflect.on(planExecutionService).set("planExecutionRepository", planExecutionRepositoryMock);
    Reflect.on(planExecutionService).set("planExecutionDeleteObserverSubject", planExecutionDeleteObserverSubject);

    List<PlanExecution> planExecutionList = new LinkedList<>();
    Set<String> planExecutionIds = new HashSet<>();
    for (int i = 0; i < 1200; i++) {
      String uuid = generateUuid();
      planExecutionIds.add(uuid);
      planExecutionList.add(PlanExecution.builder().uuid(uuid).build());
    }

    CloseableIterator<PlanExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(planExecutionList.iterator());
    Query query = query(where(PlanExecutionKeys.uuid).in(planExecutionIds));
    for (String fieldName : PlanExecutionProjectionConstants.fieldsForPlanExecutionDelete) {
      query.fields().include(fieldName);
    }
    doReturn(iterator).when(planExecutionRepositoryMock).fetchPlanExecutionsFromAnalytics(query);

    planExecutionService.deleteAllPlanExecutionAndMetadata(planExecutionIds);

    verify(planExecutionDeleteObserverSubject, times(2)).fireInform(any(), any());
    verify(planExecutionRepositoryMock, times(1)).deleteAllByUuidIn(any());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestUpdateTTLForAllPlanExecutionAndMetadata() {
    MongoTemplate mongoTemplateMock = Mockito.mock(MongoTemplate.class);
    Reflect.on(planExecutionService).set("mongoTemplate", mongoTemplateMock);
    PlanExecutionRepository planExecutionRepositoryMock = Mockito.mock(PlanExecutionRepository.class);
    Reflect.on(planExecutionService).set("planExecutionRepository", planExecutionRepositoryMock);

    Date ttlExpiry = Date.from(OffsetDateTime.now().plus(Duration.ofMinutes(30)).toInstant());
    planExecutionService.updateTTL(generateUuid(), ttlExpiry);

    verify(planExecutionRepositoryMock, times(1)).multiUpdatePlanExecution(any(), any());
  }
}
