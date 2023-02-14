/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
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
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NextStepHandlerTest extends CategoryTest {
  @Mock private OrchestrationEngine engine;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanService planService;

  @Inject @InjectMocks private NextStepHandler nextStepHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

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
                                      .planNode(PlanNode.builder().build())
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .parentId(generateUuid())
                                      .notifyId(generateUuid())
                                      .build();

    when(planService.fetchNode(planId, nextNodeId)).thenReturn(planNode);
    doNothing().when(nodeExecutionService).updateV2(eq(nodeExecutionId), any());

    ArgumentCaptor<Ambiance> ambianceArgumentCaptor = ArgumentCaptor.forClass(Ambiance.class);
    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);
    verify(engine).runNextNode(ambianceArgumentCaptor.capture(), eq(planNode), eq(nodeExecution), eq(null));

    assertThat(ambianceArgumentCaptor.getValue().getLevelsCount()).isEqualTo(1);
    assertThat(ambianceArgumentCaptor.getValue().getLevels(0).getSetupId()).isEqualTo(nextNodeId);
    verify(planService, times(0)).saveIdentityNodesForMatrix(any(), any());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void handleAdviseWithNextNodeIdForIdentityNodes() {
    String nodeExecutionId = generateUuid();
    String originalNodeExecutionId = generateUuid();
    String nextNodeId = generateUuid();
    String planId = generateUuid();
    String planExecutionId = generateUuid();
    AdviserResponse adviserResponse =
        AdviserResponse.newBuilder()
            .setNextStepAdvise(NextStepAdvise.newBuilder().setNextNodeId(nextNodeId).build())
            .build();

    Node node = IdentityPlanNode.builder()
                    .uuid(nextNodeId)
                    .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                    .identifier("DUMMY")
                    .serviceName("CD")
                    .build();
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId(planExecutionId).setPlanId(planId).build();
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid(nodeExecutionId)
                                      .planNode(IdentityPlanNode.builder().build())
                                      .ambiance(ambiance)
                                      .status(Status.QUEUED)
                                      .mode(ExecutionMode.TASK)
                                      .startTs(System.currentTimeMillis())
                                      .parentId(generateUuid())
                                      .notifyId(generateUuid())
                                      .originalNodeExecutionId(originalNodeExecutionId)
                                      .build();

    when(planService.fetchNode(planId, nextNodeId)).thenReturn(node);
    doNothing().when(nodeExecutionService).updateV2(eq(nodeExecutionId), any());

    ArgumentCaptor<Ambiance> ambianceArgumentCaptor = ArgumentCaptor.forClass(Ambiance.class);
    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);
    verify(engine).runNextNode(ambianceArgumentCaptor.capture(), eq(node), eq(nodeExecution), eq(null));

    assertThat(ambianceArgumentCaptor.getValue().getLevelsCount()).isEqualTo(1);
    assertThat(ambianceArgumentCaptor.getValue().getLevels(0).getSetupId()).isEqualTo(nextNodeId);
    verify(planService, times(0)).saveIdentityNodesForMatrix(any(), any());

    node = PlanNode.builder()
               .uuid(nextNodeId)
               .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
               .identifier("DUMMY")
               .name("DUMMY")
               .serviceName("CD")
               .build();

    when(planService.fetchNode(planId, nextNodeId)).thenReturn(node);
    doReturn(NodeExecution.builder().uuid(originalNodeExecutionId).nextId("nextId").build())
        .when(nodeExecutionService)
        .getWithFieldsIncluded(originalNodeExecutionId, Set.of("nextId"));
    nextStepHandler.handleAdvise(nodeExecution, adviserResponse);
    ArgumentCaptor<List> identityNodeArgCaptor = ArgumentCaptor.forClass(List.class);
    verify(planService, times(1)).saveIdentityNodesForMatrix(identityNodeArgCaptor.capture(), any());

    IdentityPlanNode savedIdentityNode = (IdentityPlanNode) identityNodeArgCaptor.getValue().get(0);
    assertThat(savedIdentityNode.getIdentifier()).isEqualTo(node.getIdentifier());
    assertThat(savedIdentityNode.getName()).isEqualTo(node.getName());
    assertThat(savedIdentityNode.getStepType()).isEqualTo(node.getStepType());

    ArgumentCaptor<Ambiance> ambianceArgumentCaptor1 = ArgumentCaptor.forClass(Ambiance.class);
    // The savedIdentityNode will be passed to engine to start the nextNode instead of node.
    verify(engine).runNextNode(ambianceArgumentCaptor1.capture(), eq(savedIdentityNode), eq(nodeExecution), eq(null));
    assertThat(ambianceArgumentCaptor1.getValue().getLevelsCount()).isEqualTo(1);
    // Ambiance will not have nextNodeId as its latest levels setupId. the setup id will be the uuid of saved
    // identityNode.
    assertThat(ambianceArgumentCaptor1.getValue().getLevels(0).getSetupId()).isNotEqualTo(nextNodeId);
    assertThat(ambianceArgumentCaptor1.getValue().getLevels(0).getSetupId()).isEqualTo(savedIdentityNode.getUuid());
  }
}
