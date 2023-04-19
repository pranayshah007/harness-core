/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.callback;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.InterruptService;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.ManualIssuer;
import io.harness.rule.Owner;
import io.harness.waiter.StringNotifyResponseData;

import com.google.common.collect.ImmutableMap;
import java.util.EnumSet;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class FailureInterruptCallbackTest extends OrchestrationTestBase {
  @Mock InterruptService interruptService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock OrchestrationEngine orchestrationEngine;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestNotify() {
    String nodeExecutionId = generateUuid();
    String interruptId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecution ne = NodeExecution.builder()
                           .uuid(nodeExecutionId)
                           .status(Status.FAILED)
                           .parentId(generateUuid())
                           .planNode(PlanNode.builder().identifier(generateUuid()).build())
                           .ambiance(ambiance)
                           .version(1L)
                           .build();
    when(nodeExecutionService.update(eq(nodeExecutionId), any(), any())).thenReturn(ne);
    FailureInterruptCallback failureInterruptCallback =
        FailureInterruptCallback.builder()
            .interruptId(interruptId)
            .interruptType(InterruptType.CUSTOM_FAILURE)
            .nodeExecutionId(nodeExecutionId)
            .originalStatus(Status.INTERVENTION_WAITING)
            .interruptConfig(
                InterruptConfig.newBuilder()
                    .setIssuedBy(
                        IssuedBy.newBuilder()
                            .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("MANUAL").setType("").build())
                            .build())
                    .build())
            .build();
    Reflect.on(failureInterruptCallback).set("interruptService", interruptService);
    Reflect.on(failureInterruptCallback).set("nodeExecutionService", nodeExecutionService);
    Reflect.on(failureInterruptCallback).set("orchestrationEngine", orchestrationEngine);

    failureInterruptCallback.notify(
        ImmutableMap.of(generateUuid(), StringNotifyResponseData.builder().data("SOMEDATA").build()));

    ArgumentCaptor<Ambiance> ambianceArgumentCaptor = ArgumentCaptor.forClass(Ambiance.class);
    verify(nodeExecutionService).update(eq(nodeExecutionId), any(), any());
    verify(orchestrationEngine)
        .concludeNodeExecution(ambianceArgumentCaptor.capture(), eq(Status.FAILED), eq(Status.INTERVENTION_WAITING),
            eq(EnumSet.noneOf(Status.class)));
    verify(interruptService).markProcessed(eq(interruptId), eq(Interrupt.State.PROCESSED_SUCCESSFULLY));

    assertThat(ambianceArgumentCaptor.getValue()).isEqualTo(ambiance);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void shouldTestNotifyWithOriginalStatusNull() {
    String nodeExecutionId = generateUuid();
    String interruptId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build())
                            .build();
    NodeExecution ne = NodeExecution.builder()
                           .uuid(nodeExecutionId)
                           .status(Status.FAILED)
                           .parentId(generateUuid())
                           .planNode(PlanNode.builder().identifier(generateUuid()).build())
                           .ambiance(ambiance)
                           .version(1L)
                           .build();
    when(nodeExecutionService.update(eq(nodeExecutionId), any(), any())).thenReturn(ne);

    // Setting original status as null
    FailureInterruptCallback failureInterruptCallback =
        FailureInterruptCallback.builder()
            .interruptId(interruptId)
            .interruptType(InterruptType.CUSTOM_FAILURE)
            .nodeExecutionId(nodeExecutionId)
            .originalStatus(null)
            .interruptConfig(
                InterruptConfig.newBuilder()
                    .setIssuedBy(
                        IssuedBy.newBuilder()
                            .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("MANUAL").setType("").build())
                            .build())
                    .build())
            .build();
    Reflect.on(failureInterruptCallback).set("interruptService", interruptService);
    Reflect.on(failureInterruptCallback).set("nodeExecutionService", nodeExecutionService);
    Reflect.on(failureInterruptCallback).set("orchestrationEngine", orchestrationEngine);

    failureInterruptCallback.notify(
        ImmutableMap.of(generateUuid(), StringNotifyResponseData.builder().data("SOMEDATA").build()));

    ArgumentCaptor<Ambiance> ambianceArgumentCaptor = ArgumentCaptor.forClass(Ambiance.class);
    verify(nodeExecutionService).update(eq(nodeExecutionId), any(), any());
    verify(orchestrationEngine)
        .concludeNodeExecution(
            ambianceArgumentCaptor.capture(), eq(Status.FAILED), eq(Status.FAILED), eq(EnumSet.noneOf(Status.class)));
    verify(interruptService).markProcessed(eq(interruptId), eq(Interrupt.State.PROCESSED_SUCCESSFULLY));

    assertThat(ambianceArgumentCaptor.getValue()).isEqualTo(ambiance);
  }
}
