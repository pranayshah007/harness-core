/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.OrchestrationStepsTestHelper;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.execution.strategy.identity.IdentityStrategyInternalStep;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.execution.NodeExecution;
import io.harness.persistence.UuidAccess;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.steps.StepUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
@PrepareForTest(StepUtils.class)
public class IdentityStrategyInternalStepTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanService planService;
  @Mock private PmsOutcomeService pmsOutcomeService;
  @Inject @InjectMocks private IdentityStrategyInternalStep identityStrategyInternalStep;

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
        .addLevels(Level.newBuilder().setStrategyMetadata(StrategyMetadata.newBuilder().build()).build())
        .build();
  }

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testObtainChildren() {
    String originalNodeExecutionId = "originalNodeExecutionId";
    String ORIGINAL_PLAN_EXECUTION_ID = "originalPlanExecutionId";
    Ambiance oldAmbiance = buildAmbiance();
    Ambiance ambiance = Ambiance.newBuilder().setPlanId("planId").setPlanExecutionId("PLAN_EXECUTION_ID").build();

    IdentityStepParameters stepParameters =
        IdentityStepParameters.builder().originalNodeExecutionId(originalNodeExecutionId).build();
    List<NodeExecution> childrenNodeExecutions = new ArrayList<>();
    childrenNodeExecutions.add(NodeExecution.builder()
                                   .uuid("uuid1")
                                   .ambiance(oldAmbiance)
                                   .planNode(PlanNode.builder().uuid("planUuid1").build())
                                   .build());
    childrenNodeExecutions.add(NodeExecution.builder()
                                   .uuid("uuid2")
                                   .ambiance(oldAmbiance)
                                   .planNode(PlanNode.builder().uuid("planUuid2").build())
                                   .build());
    childrenNodeExecutions.add(NodeExecution.builder()
                                   .uuid("uuid3")
                                   .ambiance(oldAmbiance)
                                   .planNode(IdentityPlanNode.builder().uuid("identityUuid1").build())
                                   .build());
    childrenNodeExecutions.add(NodeExecution.builder()
                                   .uuid("uuid4")
                                   .ambiance(oldAmbiance)
                                   .planNode(IdentityPlanNode.builder().uuid("identityUuid2").build())
                                   .build());

    NodeExecution strategyNodeExecution =
        NodeExecution.builder()
            .uuid("originalNodeExecutionId")
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(ORIGINAL_PLAN_EXECUTION_ID).build())
            .executableResponse(
                ExecutableResponse.newBuilder()
                    .setChildren(
                        ChildrenExecutableResponse.newBuilder()
                            .addChildren(
                                ChildrenExecutableResponse.Child.newBuilder().setChildNodeId("childId").build())
                            .build())
                    .build())
            .build();

    CloseableIterator<NodeExecution> iterator =
        OrchestrationStepsTestHelper.createCloseableIterator(childrenNodeExecutions.listIterator());

    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchChildrenNodeExecutionsIterator(
            ORIGINAL_PLAN_EXECUTION_ID, originalNodeExecutionId, NodeProjectionUtils.fieldsForIdentityStrategyStep);

    doReturn(strategyNodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded(
            stepParameters.getOriginalNodeExecutionId(), NodeProjectionUtils.fieldsForIdentityStrategyStep);

    ArgumentCaptor<List> identityNodesCaptor = ArgumentCaptor.forClass(List.class);

    ChildrenExecutableResponse response = identityStrategyInternalStep.obtainChildren(ambiance, stepParameters, null);

    assertEquals(response.getChildrenCount(), childrenNodeExecutions.size());
    assertEquals(response.getMaxConcurrency(), 0);
    verify(planService, times(1)).saveIdentityNodesForMatrix(identityNodesCaptor.capture(), any());
    assertChildrenResponse(response, identityNodesCaptor.getValue(), childrenNodeExecutions);

    strategyNodeExecution =
        NodeExecution.builder()
            .uuid("originalNodeExecutionId")
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(ORIGINAL_PLAN_EXECUTION_ID).build())
            .executableResponse(
                ExecutableResponse.newBuilder()
                    .setChildren(
                        ChildrenExecutableResponse.newBuilder()
                            .setMaxConcurrency(2)
                            .addChildren(
                                ChildrenExecutableResponse.Child.newBuilder().setChildNodeId("childId").build())
                            .build())
                    .build())
            .build();

    doReturn(strategyNodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded(
            stepParameters.getOriginalNodeExecutionId(), NodeProjectionUtils.fieldsForIdentityStrategyStep);
    iterator = OrchestrationStepsTestHelper.createCloseableIterator(childrenNodeExecutions.listIterator());
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchChildrenNodeExecutionsIterator(ORIGINAL_PLAN_EXECUTION_ID, stepParameters.getOriginalNodeExecutionId(),
            NodeProjectionUtils.fieldsForIdentityStrategyStep);

    response = identityStrategyInternalStep.obtainChildren(ambiance, stepParameters, null);
    assertEquals(response.getChildrenCount(), childrenNodeExecutions.size());
    assertEquals(response.getMaxConcurrency(), 2);
    verify(planService, times(2)).saveIdentityNodesForMatrix(identityNodesCaptor.capture(), any());
    assertChildrenResponse(response, identityNodesCaptor.getValue(), childrenNodeExecutions);

    strategyNodeExecution =
        NodeExecution.builder()
            .uuid("originalNodeExecutionId")
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(ORIGINAL_PLAN_EXECUTION_ID).build())
            .executableResponse(
                ExecutableResponse.newBuilder()
                    .setChildren(
                        ChildrenExecutableResponse.newBuilder()
                            .setMaxConcurrency(4)
                            .addChildren(
                                ChildrenExecutableResponse.Child.newBuilder().setChildNodeId("childId").build())
                            .build())
                    .build())
            .build();

    doReturn(strategyNodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded(
            stepParameters.getOriginalNodeExecutionId(), NodeProjectionUtils.fieldsForIdentityStrategyStep);
    iterator = OrchestrationStepsTestHelper.createCloseableIterator(childrenNodeExecutions.listIterator());
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchChildrenNodeExecutionsIterator(ORIGINAL_PLAN_EXECUTION_ID, stepParameters.getOriginalNodeExecutionId(),
            NodeProjectionUtils.fieldsForIdentityStrategyStep);

    doReturn(strategyNodeExecution).when(nodeExecutionService).get(originalNodeExecutionId);
    response = identityStrategyInternalStep.obtainChildren(ambiance, stepParameters, null);
    assertEquals(response.getChildrenCount(), childrenNodeExecutions.size());
    assertEquals(response.getMaxConcurrency(), 4);
    verify(planService, times(3)).saveIdentityNodesForMatrix(identityNodesCaptor.capture(), any());
    assertChildrenResponse(response, identityNodesCaptor.getValue(), childrenNodeExecutions);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse() {
    StepResponse stepResponse = identityStrategyInternalStep.handleChildrenResponse(null, null, new HashMap<>());
    assertNotNull(stepResponse);
  }

  private void assertChildrenResponse(ChildrenExecutableResponse childrenExecutableResponse, List<Node> identityNodes,
      List<NodeExecution> childrenNodeExecutions) {
    List<String> newlyCreatedidentittNodeids =
        identityNodes.stream().map(UuidAccess::getUuid).collect(Collectors.toList());
    List<String> originalIdentityNodeIds = childrenNodeExecutions.stream()
                                               .filter(o -> o.getNode() instanceof IdentityPlanNode)
                                               .map(o -> o.getNode().getUuid())
                                               .collect(Collectors.toList());
    long planNodesCount = childrenNodeExecutions.stream().filter(o -> o.getNode() instanceof PlanNode).count();
    int identityNodesCount = 0;
    for (ChildrenExecutableResponse.Child child : childrenExecutableResponse.getChildrenList()) {
      if (!originalIdentityNodeIds.contains(child.getChildNodeId())) {
        identityNodesCount++;
        assertTrue(newlyCreatedidentittNodeids.contains(child.getChildNodeId()));
      } else {
        assertFalse(newlyCreatedidentittNodeids.contains(child.getChildNodeId()));
      }
    }
    assertEquals(identityNodesCount, newlyCreatedidentittNodeids.size());
    assertEquals(planNodesCount, identityNodesCount);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testObtainChild() {
    String originalNodeExecutionId = "originalNodeExecutionId";
    String ORIGINAL_PLAN_EXECUTION_ID = "originalPlanExecutionId";
    Ambiance oldAmbiance = buildAmbiance();
    Ambiance ambiance = Ambiance.newBuilder().setPlanId("planId").setPlanExecutionId("PLAN_EXECUTION_ID").build();
    IdentityStepParameters stepParameters =
        IdentityStepParameters.builder().originalNodeExecutionId(originalNodeExecutionId).build();

    ChildExecutableResponse childExecutableResponse = ChildExecutableResponse.newBuilder().build();
    NodeExecution originalNodeExecution =
        NodeExecution.builder()
            .uuid("originalNodeExecutionId")
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(ORIGINAL_PLAN_EXECUTION_ID).build())
            .executableResponse(ExecutableResponse.newBuilder().setChild(childExecutableResponse).build())
            .createdAt(50L)
            .build();
    doReturn(originalNodeExecution)
        .when(nodeExecutionService)
        .getWithFieldsIncluded(originalNodeExecutionId, NodeProjectionUtils.fieldsForIdentityStrategyStep);

    CloseableIterator<NodeExecution> iterator = OrchestrationStepsTestHelper.createCloseableIterator(
        Arrays
            .asList(NodeExecution.builder()
                        .uuid("uuid1")
                        .ambiance(oldAmbiance)
                        .planNode(IdentityPlanNode.builder().uuid("identityPlanUuid").build())
                        .createdAt(100L)
                        .build())
            .iterator());
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchChildrenNodeExecutionsIterator(ORIGINAL_PLAN_EXECUTION_ID, originalNodeExecutionId, Direction.ASC,
            NodeProjectionUtils.fieldsForIdentityStrategyStep);

    ChildExecutableResponse response = identityStrategyInternalStep.obtainChild(ambiance, stepParameters, null);

    assertEquals(response, childExecutableResponse);
    verify(planService, never()).saveIdentityNodesForMatrix(any(), any());

    // NodeExecution with lowest createdAt should be returned as child.
    iterator = OrchestrationStepsTestHelper.createCloseableIterator(
        Arrays
            .asList(NodeExecution.builder()
                        .uuid("uuid1")
                        .ambiance(oldAmbiance)
                        .createdAt(100L)
                        .planNode(PlanNode.builder().uuid("planUuid1").build())
                        .build(),
                NodeExecution.builder()
                    .uuid("uuid2")
                    .ambiance(oldAmbiance)
                    .planNode(PlanNode.builder().uuid("planUuid2").build())
                    .createdAt(200L)
                    .build())
            .iterator());
    doReturn(iterator)
        .when(nodeExecutionService)
        .fetchChildrenNodeExecutionsIterator(ORIGINAL_PLAN_EXECUTION_ID, originalNodeExecutionId, Direction.ASC,
            NodeProjectionUtils.fieldsForIdentityStrategyStep);

    ArgumentCaptor<List> argumentCaptor = ArgumentCaptor.forClass(List.class);

    response = identityStrategyInternalStep.obtainChild(ambiance, stepParameters, null);
    assertFalse(response.getChildNodeId().equals("planUuid1"));

    verify(planService, times(1)).saveIdentityNodesForMatrix(argumentCaptor.capture(), any());
    List<Node> childList = argumentCaptor.getValue();
    assertEquals(childList.size(), 1);
    IdentityPlanNode childIdentityPlanNode = (IdentityPlanNode) childList.get(0);
    // uuid1 because it is the first child. uuid2 will be started by uuid1 with help of nextStep Adviser.
    assertEquals(childIdentityPlanNode.getOriginalNodeExecutionId(), "uuid1");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleChildResponse() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    NodeExecution nodeExecution = NodeExecution.builder().uuid("nodeUuid").status(Status.ABORTED).build();
    doReturn(nodeExecution).when(nodeExecutionService).get(anyString());

    StepResponse stepResponse = identityStrategyInternalStep.handleChildResponse(ambiance, identityParams, null);
    verify(pmsOutcomeService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(stepResponse.getStatus()).isEqualTo(Status.ABORTED);
  }
}
