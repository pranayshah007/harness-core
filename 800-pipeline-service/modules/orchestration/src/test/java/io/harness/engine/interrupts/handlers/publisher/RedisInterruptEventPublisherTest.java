/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers.publisher;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptEvent;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.events.base.PmsEventCategory;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class RedisInterruptEventPublisherTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsEventSender eventSender;

  @Inject @InjectMocks private RedisInterruptEventPublisher publisher;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void publishEvent() throws InvalidProtocolBufferException {
    String nodeExecutionId = generateUuid();
    String planExecutionId = generateUuid();
    Interrupt interrupt = Interrupt.builder()
                              .planExecutionId(planExecutionId)
                              .type(InterruptType.ABORT)
                              .interruptConfig(InterruptConfig.newBuilder().build())
                              .uuid(generateUuid())
                              .build();
    PlanNode planNode = PlanNode.builder()
                            .uuid(generateUuid())
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("STEP").setStepCategory(StepCategory.STEP).build())
                            .serviceName("serviceName")
                            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .planNode(planNode)
            .uuid(nodeExecutionId)
            .ambiance(Ambiance.newBuilder()
                          .setPlanExecutionId(planExecutionId)
                          .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                          .build())
            .resolvedStepParameters(RecastOrchestrationUtils.fromJson("{}"))
            .build();

    when(nodeExecutionService.get(any())).thenReturn(nodeExecution);
    when(eventSender.sendEvent(any(), any(), any(), any())).thenReturn(null);

    String notifyId = publisher.publishEvent(nodeExecutionId, interrupt, InterruptType.ABORT);

    ArgumentCaptor<ByteString> eventArgumentCaptor = ArgumentCaptor.forClass(ByteString.class);
    verify(eventSender)
        .sendEvent(
            any(Ambiance.class), eventArgumentCaptor.capture(), any(PmsEventCategory.class), anyString(), anyBoolean());

    ByteString eventByteString = eventArgumentCaptor.getValue();
    InterruptEvent actualInterruptEvent = InterruptEvent.parseFrom(eventByteString);

    assertThat(actualInterruptEvent.getNotifyId()).isEqualTo(notifyId);
  }
}
