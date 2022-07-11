/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.NextStepAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class NextStepHandlerTest extends OrchestrationTestBase {
  @Mock private OrchestrationEngine engine;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanService planService;

  @Inject @InjectMocks private NextStepHandler nextStepHandler;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void handleAdviseWhenNextNodeIsIsEmpty() {
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder().setNextStepAdvise(NextStepAdvise.newBuilder().build()).build();
    doNothing().when(engine).endNodeExecution(any());
    nextStepHandler.handleAdvise(NodeExecution.builder().build(), adviserResponse);
    verify(engine).endNodeExecution(any());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void handleAdviseWithNextNodeId() {
    String nodeExecutionId = generateUuid();
    String nextNodeId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder()
            .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(nextNodeId).build())
            .build();

    PlanNode planNode = PlanNode.builder()
                            .uuid(nextNodeId)
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .identifier("DUMMY")
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).setPlanId(planId).build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .parentId(generateUuid())
                                      .notifyId(generateUuid())
                                      .build();

    when(planService.fetchNode(planId, nextNodeId)).thenReturn(planNode);
    when(nodeExecutionService.update(eq(nodeExecutionId), any())).thenReturn(nodeExecution);

    ArgumentCaptor<Ambiance> ambianceArgumentCaptor = ArgumentCaptor.forClass(Ambiance.class);
    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);
    verify(engine).runNextNode(ambianceArgumentCaptor.capture(), eq(planNode), eq(nodeExecution), eq(null));

    assertThat(ambianceArgumentCaptor.getValue().getLevelsCount()).isEqualTo(1);
    assertThat(ambianceArgumentCaptor.getValue().getLevels(0).getSetupId()).isEqualTo(nextNodeId);
  }
}
