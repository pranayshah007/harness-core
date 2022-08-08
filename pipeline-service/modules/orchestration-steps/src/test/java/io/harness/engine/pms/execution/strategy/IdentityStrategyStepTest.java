/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.execution.strategy.identity.IdentityStrategyStep;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.execution.NodeExecution;
import io.harness.persistence.UuidAccess;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.steps.StepUtils;
import io.harness.steps.http.HttpStep;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
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

@OwnedBy(HarnessTeam.PIPELINE)
@PrepareForTest(StepUtils.class)
public class IdentityStrategyStepTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanService planService;
  @Inject @InjectMocks private IdentityStrategyStep identityStrategyStep;

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
    Ambiance oldAmbiance = buildAmbiance();
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                            .build();
    IdentityStepParameters stepParameters =
        IdentityStepParameters.builder().originalNodeExecutionId(originalNodeExecutionId).build();
    List<NodeExecution> childrenNodeExecutions = new ArrayList<>();
    childrenNodeExecutions.add(NodeExecution.builder()
                                   .uuid("uuid1")
                                   .ambiance(oldAmbiance)
                                   .status(Status.SUCCEEDED)
                                   .planNode(PlanNode.builder()
                                                 .stepType(StepType.newBuilder()
                                                               .setType(HttpStep.STEP_TYPE.getType())
                                                               .setStepCategory(HttpStep.STEP_TYPE.getStepCategory())
                                                               .build())
                                                 .build())
                                   .build());
    childrenNodeExecutions.add(NodeExecution.builder()
                                   .uuid("uuid2")
                                   .ambiance(oldAmbiance)
                                   .status(Status.SUCCEEDED)
                                   .planNode(PlanNode.builder()
                                                 .stepType(StepType.newBuilder()
                                                               .setType(HttpStep.STEP_TYPE.getType())
                                                               .setStepCategory(HttpStep.STEP_TYPE.getStepCategory())
                                                               .build())
                                                 .build())
                                   .build());
    childrenNodeExecutions.add(
        NodeExecution.builder().uuid("uuid3").ambiance(oldAmbiance).status(Status.FAILED).build());
    childrenNodeExecutions.add(
        NodeExecution.builder().uuid("uuid4").ambiance(oldAmbiance).status(Status.ABORTED).build());

    NodeExecution strategyNodeExecution =
        NodeExecution.builder()
            .uuid("originalNodeExecutionId")
            .executableResponse(
                ExecutableResponse.newBuilder()
                    .setChildren(
                        ChildrenExecutableResponse.newBuilder()
                            .addChildren(
                                ChildrenExecutableResponse.Child.newBuilder().setChildNodeId("childId").build())
                            .build())
                    .build())
            .build();

    childrenNodeExecutions.add(strategyNodeExecution);

    doReturn(childrenNodeExecutions)
        .when(nodeExecutionService)
        .fetchNodeExecutionsByParentIdWithAmbianceAndNode(originalNodeExecutionId, true, true);

    ArgumentCaptor<List> identityNodesCaptor = ArgumentCaptor.forClass(List.class);

    ChildrenExecutableResponse response = identityStrategyStep.obtainChildren(ambiance, stepParameters, null);

    // -1 to exclude strategy node execution.
    assertEquals(response.getChildrenCount(), childrenNodeExecutions.size() - 1);
    assertEquals(response.getMaxConcurrency(), 0);
    verify(planService, times(1)).saveIdentityNodesForMatrix(identityNodesCaptor.capture(), any());
    assertChildrenResponse(response, identityNodesCaptor.getValue(), childrenNodeExecutions);

    strategyNodeExecution =
        NodeExecution.builder()
            .uuid("originalNodeExecutionId")
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

    childrenNodeExecutions.remove(childrenNodeExecutions.size() - 1);
    childrenNodeExecutions.add(strategyNodeExecution);

    doReturn(childrenNodeExecutions)
        .when(nodeExecutionService)
        .fetchNodeExecutionsByParentIdWithAmbianceAndNode(originalNodeExecutionId, true, true);

    doReturn(strategyNodeExecution).when(nodeExecutionService).get(originalNodeExecutionId);
    response = identityStrategyStep.obtainChildren(ambiance, stepParameters, null);
    // -1 to exclude strategy node execution.
    assertEquals(response.getChildrenCount(), childrenNodeExecutions.size() - 1);
    assertEquals(response.getMaxConcurrency(), 2);
    verify(planService, times(2)).saveIdentityNodesForMatrix(identityNodesCaptor.capture(), any());
    assertChildrenResponse(response, identityNodesCaptor.getValue(), childrenNodeExecutions);

    strategyNodeExecution =
        NodeExecution.builder()
            .uuid("originalNodeExecutionId")
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
    childrenNodeExecutions.remove(childrenNodeExecutions.size() - 1);
    childrenNodeExecutions.add(strategyNodeExecution);

    doReturn(childrenNodeExecutions)
        .when(nodeExecutionService)
        .fetchNodeExecutionsByParentIdWithAmbianceAndNode(originalNodeExecutionId, true, true);

    doReturn(strategyNodeExecution).when(nodeExecutionService).get(originalNodeExecutionId);
    response = identityStrategyStep.obtainChildren(ambiance, stepParameters, null);
    // -1 to exclude strategy node execution.
    assertEquals(response.getChildrenCount(), childrenNodeExecutions.size() - 1);
    assertEquals(response.getMaxConcurrency(), 4);
    verify(planService, times(3)).saveIdentityNodesForMatrix(identityNodesCaptor.capture(), any());
    assertChildrenResponse(response, identityNodesCaptor.getValue(), childrenNodeExecutions);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse() {
    StepResponse stepResponse = identityStrategyStep.handleChildrenResponse(null, null, new HashMap<>());
    assertNotNull(stepResponse);
  }

  private void assertChildrenResponse(ChildrenExecutableResponse childrenExecutableResponse, List<Node> identityNodes,
      List<NodeExecution> childrenNodeExecutions) {
    List<String> nodeIds = identityNodes.stream().map(UuidAccess::getUuid).collect(Collectors.toList());
    long successFulNodeExecutions = childrenNodeExecutions.stream()
                                        .filter(o -> !StatusUtils.brokeAndAbortedStatuses().contains(o.getStatus()))
                                        .count();
    int identityNodesCount = 0;
    for (ChildrenExecutableResponse.Child child : childrenExecutableResponse.getChildrenList()) {
      if (!child.getChildNodeId().equals("childId")) {
        identityNodesCount++;
        assertTrue(nodeIds.contains(child.getChildNodeId()));
      }
    }
    assertEquals(identityNodesCount, nodeIds.size());
    assertEquals(identityNodesCount, 2);
    // -1 to exclude strategy node execution.
    assertEquals(successFulNodeExecutions - 1, identityNodesCount);
  }
}
