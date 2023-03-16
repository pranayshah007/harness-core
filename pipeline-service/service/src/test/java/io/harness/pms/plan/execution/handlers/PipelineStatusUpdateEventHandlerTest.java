/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.SHALINI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.beans.AbortedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.events.OrchestrationEventEmitter;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptService;
import io.harness.execution.PlanExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.ManualIssuer;
import io.harness.pms.contracts.interrupts.TimeoutIssuer;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class PipelineStatusUpdateEventHandlerTest extends PipelineServiceTestBase {
  @Mock private PlanExecutionService planExecutionService;
  @Mock private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock private OrchestrationEventEmitter eventEmitter;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private InterruptService interruptService;
  private PipelineStatusUpdateEventHandler pipelineStatusUpdateEventHandler;

  @Before
  public void setUp() throws Exception {
    pipelineStatusUpdateEventHandler = new PipelineStatusUpdateEventHandler(
        planExecutionService, pmsExecutionSummaryRepository, eventEmitter, waitNotifyEngine, interruptService);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestOnPlanStatusUpdate() {
    PlanExecution planExecution =
        PlanExecution.builder().uuid(generateUuid()).status(Status.SUCCEEDED).endTs(11L).build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecution.getUuid()).build();

    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);

    when(planExecutionService.get(anyString())).thenReturn(planExecution);

    when(pmsExecutionSummaryRepository.update(queryArgumentCaptor.capture(), updateArgumentCaptor.capture()))
        .thenReturn(null);

    pipelineStatusUpdateEventHandler.onPlanStatusUpdate(ambiance);

    Query query = queryArgumentCaptor.getValue();
    assertThat(query).isNotNull();
    assertThat(query.getQueryObject())
        .isEqualTo(new Document().append("planExecutionId", ambiance.getPlanExecutionId()));

    Update update = updateArgumentCaptor.getValue();
    assertThat(update).isNotNull();
    assertThat(update.getUpdateObject())
        .isEqualTo(new Document().append("$set",
            new Document()
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.internalStatus, Status.SUCCEEDED)
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.status, ExecutionStatus.SUCCESS)
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.endTs, planExecution.getEndTs())));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestOnPlanStatusUpdateWithAbortInterruptWithManualIssuer() {
    PlanExecution planExecution =
        PlanExecution.builder().uuid(generateUuid()).status(Status.ABORTED).endTs(11L).build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecution.getUuid()).build();

    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);

    when(planExecutionService.get(anyString())).thenReturn(planExecution);

    when(pmsExecutionSummaryRepository.update(queryArgumentCaptor.capture(), updateArgumentCaptor.capture()))
        .thenReturn(null);

    when(interruptService.fetchAbortAllPlanLevelInterrupt(planExecution.getUuid()))
        .thenReturn(List.of(
            Interrupt.builder()
                .interruptConfig(
                    InterruptConfig.newBuilder()
                        .setIssuedBy(IssuedBy.newBuilder()
                                         .setManualIssuer(
                                             ManualIssuer.newBuilder().setUserId("user1").setEmailId("email").build())
                                         .build())
                        .build())
                .type(InterruptType.ABORT)
                .planExecutionId(planExecution.getUuid())
                .build()));
    pipelineStatusUpdateEventHandler.onPlanStatusUpdate(ambiance);

    Query query = queryArgumentCaptor.getValue();
    assertThat(query).isNotNull();
    assertThat(query.getQueryObject())
        .isEqualTo(new Document().append("planExecutionId", ambiance.getPlanExecutionId()));

    Update update = updateArgumentCaptor.getValue();
    assertThat(update).isNotNull();
    assertThat(update.getUpdateObject())
        .isEqualTo(new Document().append("$set",
            new Document()
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.abortedBy,
                    AbortedBy.builder().email("email").userName("user1").build())
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.internalStatus, Status.ABORTED)
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.status, ExecutionStatus.ABORTED)
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.endTs, planExecution.getEndTs())));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void shouldTestOnPlanStatusUpdateWithAbortInterruptWithoutManualIssuer() {
    PlanExecution planExecution =
        PlanExecution.builder().uuid(generateUuid()).status(Status.ABORTED).endTs(11L).build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecution.getUuid()).build();

    ArgumentCaptor<Query> queryArgumentCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);

    when(planExecutionService.get(anyString())).thenReturn(planExecution);

    when(pmsExecutionSummaryRepository.update(queryArgumentCaptor.capture(), updateArgumentCaptor.capture()))
        .thenReturn(null);

    when(interruptService.fetchAbortAllPlanLevelInterrupt(planExecution.getUuid()))
        .thenReturn(List.of(
            Interrupt.builder()
                .interruptConfig(
                    InterruptConfig.newBuilder()
                        .setIssuedBy(
                            IssuedBy.newBuilder()
                                .setTimeoutIssuer(TimeoutIssuer.newBuilder().setTimeoutInstanceId("timeout1").build())
                                .build())
                        .build())
                .type(InterruptType.ABORT)
                .planExecutionId(planExecution.getUuid())
                .build()));
    pipelineStatusUpdateEventHandler.onPlanStatusUpdate(ambiance);

    Query query = queryArgumentCaptor.getValue();
    assertThat(query).isNotNull();
    assertThat(query.getQueryObject())
        .isEqualTo(new Document().append("planExecutionId", ambiance.getPlanExecutionId()));

    Update update = updateArgumentCaptor.getValue();
    assertThat(update).isNotNull();
    assertThat(update.getUpdateObject())
        .isEqualTo(new Document().append("$set",
            new Document()
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.abortedBy,
                    AbortedBy.builder().userName("systemUser").build())
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.internalStatus, Status.ABORTED)
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.status, ExecutionStatus.ABORTED)
                .append(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.endTs, planExecution.getEndTs())));
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldOnEndEmitEventsWhenExecutedModulesHasNullElements() {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = createPipelineExecutionSummaryEntity();
    when(pmsExecutionSummaryRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndPlanExecutionIdAndPipelineDeletedNot(
                 "accountId", "orgIdentifier", "projectIdentifier", "planExecutionId", true))
        .thenReturn(Optional.of(pipelineExecutionSummaryEntity));
    when(pmsExecutionSummaryRepository.update(notNull(), notNull())).thenReturn(pipelineExecutionSummaryEntity);

    pipelineStatusUpdateEventHandler.onEnd(createAmbiance());

    // IN THIS SCENARIO WE ARE ONLY VERIFYING THAT EVENT WAS EMITTED TWICE AND WITHOUT NPE
    // A NEW TEST CASE SHOULD BE CREATED TO ASSERT EMITTED EVENT PROPERTIES.
    verify(eventEmitter, times(2)).emitEvent(notNull());
  }

  private Ambiance createAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgIdentifier")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projectIdentifier")
        .setPlanExecutionId("planExecutionId")
        .build();
  }
  // CREATE ENTITY CONTAINING NULL MODULES TO FORCE THE NPE DURING THE EXECUTION.
  private PipelineExecutionSummaryEntity createPipelineExecutionSummaryEntity() {
    Map<String, GraphLayoutNodeDTO> layoutNode = new HashMap<>();
    layoutNode.put(
        "keyA", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module("moduleA").build());
    layoutNode.put(
        "keyB", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module("moduleB").build());
    layoutNode.put("keyC",
        GraphLayoutNodeDTO.builder()
            .status(ExecutionStatus.SUCCESS)
            .skipInfo(SkipInfo.newBuilder().setEvaluatedCondition(true).build())
            .module(null)
            .build());
    layoutNode.put(
        "keyD", GraphLayoutNodeDTO.builder().status(ExecutionStatus.SUCCESS).skipInfo(null).module(null).build());
    layoutNode.put(
        "keyE", GraphLayoutNodeDTO.builder().status(ExecutionStatus.WAITING).skipInfo(null).module("moduleE").build());

    Map<String, Document> moduleInfo = new HashMap<>();
    moduleInfo.put("moduleA", null);
    moduleInfo.put("moduleB", null);

    return PipelineExecutionSummaryEntity.builder()
        .layoutNodeMap(layoutNode)
        .moduleInfo(moduleInfo)
        .status(ExecutionStatus.SUCCESS)
        .parentStageInfo(PipelineStageInfo.newBuilder().setHasParentPipeline(true).build())
        .endTs(4321L)
        .build();
  }
}
