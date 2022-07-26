/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.publisher;

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
import io.harness.execution.NodeExecution;
import io.harness.interrupts.InterruptEffect;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.TimeoutIssuer;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class RedisNodeAdviseEventPublisherTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsEventSender eventSender;

  @Inject @InjectMocks private RedisNodeAdviseEventPublisher publisher;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestPublishEvent() {
    String planExecutionId = generateUuid();
    PlanNode planNode =
        PlanNode.builder()
            .uuid(generateUuid())
            .identifier("IDENTIFIER")
            .adviserObtainment(
                AdviserObtainment.newBuilder().setType(AdviserType.newBuilder().setType("type").buildPartial()).build())
            .serviceName("serviceName")
            .build();
    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(generateUuid())
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
            .failureInfo(
                FailureInfo.newBuilder().addFailureData(FailureData.newBuilder().setCode("200").build()).build())
            .interruptHistories(ImmutableList.of(
                InterruptEffect.builder()
                    .interruptType(InterruptType.ABORT)
                    .interruptConfig(
                        InterruptConfig.newBuilder()
                            .setIssuedBy(
                                IssuedBy.newBuilder()
                                    .setTimeoutIssuer(
                                        TimeoutIssuer.newBuilder().setTimeoutInstanceId(generateUuid()).build())
                                    .buildPartial())
                            .build())
                    .build()))
            .status(Status.RUNNING)
            .retryIds(new ArrayList<>())
            .planNode(planNode)
            .build();

    String eventId = generateUuid();

    when(eventSender.sendEvent(any(), any(), any(), anyString(), anyBoolean())).thenReturn(eventId);

    String actualEventId = publisher.publishEvent(nodeExecution, planNode, Status.ABORTED);

    assertThat(actualEventId).isEqualTo(eventId);

    verify(eventSender).sendEvent(any(), any(), any(), anyString(), anyBoolean());
  }
}
