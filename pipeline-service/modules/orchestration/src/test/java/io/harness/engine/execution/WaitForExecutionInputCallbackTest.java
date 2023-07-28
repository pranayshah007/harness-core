/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.advise.NodeAdviseHelper;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.adviser.proceedwithdefault.ProceedWithDefaultValueAdviser;
import io.harness.rule.Owner;

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitForExecutionInputCallbackTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private OrchestrationEngine engine;
  @Mock private ExecutorService executorService;
  @Mock private NodeAdviseHelper adviseHelper;
  @Mock private PlanService planService;
  @InjectMocks private WaitForExecutionInputCallback waitForExecutionInputCallback;
  String nodeExecutionId = "nodeExecutionId";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    waitForExecutionInputCallback.setNodeExecutionId(nodeExecutionId);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testNotify() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("id").build();
    doReturn(NodeExecution.builder().ambiance(ambiance).build()).when(nodeExecutionService).get(nodeExecutionId);
    waitForExecutionInputCallback.notify(null);
    verify(executorService, times(1)).submit(any(Runnable.class));
  }

  // TODO (prashant): Refactor this test, this test should be broken into at-least 3 diff tests
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testNotifyTimeout() {
    Ambiance ambiance = Ambiance.newBuilder().setPlanExecutionId("id").build();
    PlanNodeBuilder planNodeBuilder = PlanNode.builder();
    doReturn(planNodeBuilder.build()).when(planService).fetchNode(any());
    doReturn(NodeExecution.builder().planNode(planNodeBuilder.build()).ambiance(ambiance).build())
        .when(nodeExecutionService)
        .get(nodeExecutionId);

    doReturn(NodeExecution.builder().planNode(planNodeBuilder.build()).ambiance(ambiance).build())
        .when(nodeExecutionService)
        .updateStatusWithOps(any(), eq(Status.EXPIRED), any(), any());

    waitForExecutionInputCallback.notifyTimeout(Map.of("key", ExecutionInputData.builder().build()));
    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(eq(nodeExecutionId), eq(Status.EXPIRED), any(), eq(EnumSet.noneOf(Status.class)));
    verify(engine, times(1)).endNodeExecution(ambiance);

    // Testing the ProceedWithDefaultValues.
    doReturn(
        NodeExecution.builder()
            .planNode(
                planNodeBuilder
                    .adviserObtainment(
                        AdviserObtainment.newBuilder().setType(ProceedWithDefaultValueAdviser.ADVISER_TYPE).build())
                    .build())
            .ambiance(ambiance)
            .build())
        .when(nodeExecutionService)
        .get(nodeExecutionId);
    doReturn(planNodeBuilder
                 .adviserObtainment(
                     AdviserObtainment.newBuilder().setType(ProceedWithDefaultValueAdviser.ADVISER_TYPE).build())
                 .build())
        .when(planService)
        .fetchNode(any());
    waitForExecutionInputCallback.notifyTimeout(Map.of("key", ExecutionInputData.builder().build()));
    // It will remain 1. Because ProceedWithDefault adviser is present to NodeExecution will not be marked as expired.
    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(eq(nodeExecutionId), eq(Status.EXPIRED), any(), eq(EnumSet.noneOf(Status.class)));

    ArgumentCaptor<FailureInfo> argumentCaptor = ArgumentCaptor.forClass(FailureInfo.class);

    doReturn(NodeExecution.builder()
                 .planNode(planNodeBuilder.clearAdviserObtainments()
                               .adviserObtainment(AdviserObtainment.getDefaultInstance())
                               .build())
                 .ambiance(ambiance)
                 .build())
        .when(nodeExecutionService)
        .get(nodeExecutionId);
    doReturn(
        planNodeBuilder.clearAdviserObtainments().adviserObtainment(AdviserObtainment.getDefaultInstance()).build())
        .when(planService)
        .fetchNode(any());
    NodeExecution updatedNodeExecution = NodeExecution.builder().status(Status.EXPIRED).build();
    doReturn(updatedNodeExecution)
        .when(nodeExecutionService)
        .updateStatusWithOps(eq(nodeExecutionId), eq(Status.EXPIRED), any(), eq(EnumSet.noneOf(Status.class)));

    waitForExecutionInputCallback.notifyTimeout(Map.of("key", ExecutionInputData.builder().build()));

    // endNodeExecution will not be called. So it's invocations should remain 1.
    verify(engine, times(1)).endNodeExecution(ambiance);

    verify(adviseHelper, times(1))
        .queueAdvisingEvent(eq(updatedNodeExecution), argumentCaptor.capture(), any(), eq(Status.EXPIRED));
    FailureInfo failureInfo = argumentCaptor.getValue();

    assertEquals(failureInfo.getFailureData(0).getCode(), ErrorCode.TIMEOUT_ENGINE_EXCEPTION.name());
    assertEquals(failureInfo.getFailureData(0).getMessage(),
        "Pipeline has passed the time limit to take the user input. Please check the timeout configuration");
    assertEquals(failureInfo.getFailureData(0).getLevel(), Level.ERROR.name());
    assertEquals(failureInfo.getFailureData(0).getFailureTypes(0), FailureType.INPUT_TIMEOUT_FAILURE);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testNotifyError() {
    // TODO(BRIJESH): will write after completing the implementation of the method.
  }
}
