/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationTestHelper;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.State;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public class AbortAllInterruptHandlerTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanExecutionService planExecutionService;
  @Inject @InjectMocks private AbortAllInterruptHandler abortAllInterruptHandler;
  @Inject private MongoTemplate mongoTemplate;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleInterruptNoLeaves() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    mongoTemplate.save(interrupt);
    when(nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
             planExecutionId, StatusUtils.finalizableStatuses()))
        .thenReturn(0L);
    Interrupt handledInterrupt = abortAllInterruptHandler.handleInterrupt(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSING);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestHandleInterruptError() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    mongoTemplate.save(interrupt);
    when(nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
             planExecutionId, StatusUtils.abortAndExpireStatuses()))
        .thenReturn(-1L);
    Interrupt handledInterrupt = abortAllInterruptHandler.handleInterrupt(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_UNSUCCESSFULLY);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterrupt() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    when(nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
             planExecutionId, StatusUtils.finalizableStatuses()))
        .thenReturn(0L);
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.RUNNING);
    Interrupt handledInterrupt = abortAllInterruptHandler.registerInterrupt(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSING);

    // Interrupt with node execution id
    planExecutionId = generateUuid();
    interruptUuid = generateUuid();
    Interrupt interruptWithNodeExecutionId = Interrupt.builder()
                                                 .uuid(interruptUuid)
                                                 .nodeExecutionId("nodeExecutionId")
                                                 .type(InterruptType.ABORT_ALL)
                                                 .interruptConfig(InterruptConfig.newBuilder().build())
                                                 .planExecutionId(planExecutionId)
                                                 .state(State.REGISTERED)
                                                 .build();

    when(nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
             planExecutionId, StatusUtils.finalizableStatuses()))
        .thenReturn(0L);
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(Collections.emptyListIterator());
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             interruptWithNodeExecutionId.getPlanExecutionId(), StatusUtils.abortAndExpireStatuses(),
             NodeProjectionUtils.fieldsForInterruptPropagatorHandler))
        .thenReturn(iterator);
    handledInterrupt = abortAllInterruptHandler.registerInterrupt(interruptWithNodeExecutionId);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSING);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shouldTestRegisterInterruptWhenAlreadyPresent() {
    String planExecutionId = generateUuid();
    // Interrupt with node execution id
    Interrupt interruptWithNodeExecutionId = Interrupt.builder()
                                                 .uuid(generateUuid())
                                                 .nodeExecutionId("nodeExecutionId")
                                                 .type(InterruptType.ABORT_ALL)
                                                 .interruptConfig(InterruptConfig.newBuilder().build())
                                                 .planExecutionId(planExecutionId)
                                                 .state(State.REGISTERED)
                                                 .build();

    when(nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
             planExecutionId, StatusUtils.finalizableStatuses()))
        .thenReturn(0L);
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(Collections.emptyListIterator());
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             interruptWithNodeExecutionId.getPlanExecutionId(), StatusUtils.abortAndExpireStatuses(),
             NodeProjectionUtils.fieldsForInterruptPropagatorHandler))
        .thenReturn(iterator);
    Interrupt handledInterrupt = abortAllInterruptHandler.registerInterrupt(interruptWithNodeExecutionId);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptWithNodeExecutionId.getUuid());
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSING);

    // Another interrupt on nodeExecution. But nodeExecutionId is different so it will be saved.
    Interrupt interruptWithDiffNodeExecutionId = Interrupt.builder()
                                                     .uuid(generateUuid())
                                                     .nodeExecutionId("nodeExecutionId2")
                                                     .type(InterruptType.ABORT_ALL)
                                                     .interruptConfig(InterruptConfig.newBuilder().build())
                                                     .planExecutionId(planExecutionId)
                                                     .state(State.REGISTERED)
                                                     .build();
    handledInterrupt = abortAllInterruptHandler.registerInterrupt(interruptWithDiffNodeExecutionId);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptWithDiffNodeExecutionId.getUuid());
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSING);

    // Another interrupt on nodeExecution with same nodeExecutionId. So it will not be saved.
    Interrupt interruptWithSameNodeExecutionId = Interrupt.builder()
                                                     .uuid(generateUuid())
                                                     .nodeExecutionId("nodeExecutionId")
                                                     .type(InterruptType.ABORT_ALL)
                                                     .interruptConfig(InterruptConfig.newBuilder().build())
                                                     .planExecutionId(planExecutionId)
                                                     .state(State.REGISTERED)
                                                     .build();
    assertThatThrownBy(() -> abortAllInterruptHandler.registerInterrupt(interruptWithSameNodeExecutionId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Execution already has ABORT_ALL interrupt");

    // Plan level interrupts.
    Interrupt interrupt = Interrupt.builder()
                              .uuid(generateUuid())
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    when(nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
             planExecutionId, StatusUtils.finalizableStatuses()))
        .thenReturn(0L);
    when(planExecutionService.getStatus(planExecutionId)).thenReturn(Status.RUNNING);
    handledInterrupt = abortAllInterruptHandler.registerInterrupt(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interrupt.getUuid());
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSING);

    Interrupt interrupt2 = Interrupt.builder()
                               .uuid(generateUuid())
                               .type(InterruptType.ABORT_ALL)
                               .interruptConfig(InterruptConfig.newBuilder().build())
                               .planExecutionId(planExecutionId)
                               .state(State.REGISTERED)
                               .build();
    assertThatThrownBy(() -> abortAllInterruptHandler.registerInterrupt(interrupt2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Execution already has ABORT_ALL interrupt");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleAllNodes() {
    String planExecutionId = generateUuid();
    String interruptUuid = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .uuid(interruptUuid)
                              .type(InterruptType.ABORT_ALL)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .planExecutionId(planExecutionId)
                              .state(State.REGISTERED)
                              .build();

    mongoTemplate.save(interrupt);

    // case1: updatedCount = 0
    when(nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
             planExecutionId, StatusUtils.finalizableStatuses()))
        .thenReturn(0L);
    Interrupt handledInterrupt = abortAllInterruptHandler.handleAllNodes(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSING);

    // case2: updatedCount < 0
    when(nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
             planExecutionId, StatusUtils.abortAndExpireStatuses()))
        .thenReturn(-1L);
    handledInterrupt = abortAllInterruptHandler.handleAllNodes(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_UNSUCCESSFULLY);

    // case3: updatedCount > 0
    when(nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
             planExecutionId, StatusUtils.abortAndExpireStatuses()))
        .thenReturn(1L);
    CloseableIterator<NodeExecution> iterator =
        OrchestrationTestHelper.createCloseableIterator(Collections.emptyListIterator());
    when(nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusInIterator(
             planExecutionId, EnumSet.of(DISCONTINUING), NodeProjectionUtils.fieldsForDiscontinuingNodes))
        .thenReturn(iterator);
    handledInterrupt = abortAllInterruptHandler.handleAllNodes(interrupt);
    assertThat(handledInterrupt).isNotNull();
    assertThat(handledInterrupt.getUuid()).isEqualTo(interruptUuid);
    assertThat(handledInterrupt.getState()).isEqualTo(State.PROCESSED_SUCCESSFULLY);
  }
}
