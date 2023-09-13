/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.pms.contracts.execution.Status.INPUT_WAITING;
import static io.harness.pms.contracts.execution.Status.PAUSED;
import static io.harness.pms.contracts.execution.Status.QUEUED;
import static io.harness.pms.contracts.execution.Status.RUNNING;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import java.util.EnumSet;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResumeStepStatusUpdateTest extends OrchestrationTestBase {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PlanExecutionService planExecutionService;
  @InjectMocks @Spy ResumeStepStatusUpdate resumeStepStatusUpdate;

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testHandleNodeStatusUpdate() {
    NodeExecution nodeExecution = NodeExecution.builder()
                                      .uuid("nodeExecutionId")
                                      .ambiance(Ambiance.newBuilder().setPlanExecutionId("planExecutionId").build())
                                      .build();
    doReturn(false).when(resumeStepStatusUpdate).resumeParents(nodeExecution);
    resumeStepStatusUpdate.handleNodeStatusUpdate(NodeUpdateInfo.builder().nodeExecution(nodeExecution).build());
    verify(planExecutionService, times(0)).calculateAndUpdateRunningStatusUnderLock(anyString(), any());
    doReturn(true).when(resumeStepStatusUpdate).resumeParents(nodeExecution);
    resumeStepStatusUpdate.handleNodeStatusUpdate(NodeUpdateInfo.builder().nodeExecution(nodeExecution).build());
    verify(planExecutionService, times(1)).calculateAndUpdateRunningStatusUnderLock(anyString(), any());
    resumeStepStatusUpdate.handleNodeStatusUpdate(NodeUpdateInfo.builder().nodeExecution(nodeExecution).build());
    verify(planExecutionService, times(2)).calculateAndUpdateRunningStatusUnderLock(anyString(), any());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testResumeParents() {
    String planExecutionId = "planExecutionId";
    boolean res = resumeStepStatusUpdate.resumeParents(
        NodeExecution.builder()
            .uuid("nodeExecutionId")
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
            .build());
    verify(nodeExecutionService, times(0)).updateStatusWithOps("parentId", RUNNING, null, EnumSet.of(PAUSED));
    assertTrue(res);

    // Since current planStatus is InputWaiting, return true and do not resume parents.
    doReturn(INPUT_WAITING).when(planExecutionService).getStatus(planExecutionId);
    res = resumeStepStatusUpdate.resumeParents(
        NodeExecution.builder()
            .uuid("nodeExecutionId")
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
            .build());
    assertTrue(res);
    // Already returned true. No interactions to resume parent nodeExecution status.
    verify(nodeExecutionService, times(0)).updateStatusWithOps(any(), any(), any(), any());

    doReturn(QUEUED).when(planExecutionService).getStatus(planExecutionId);
    doReturn(null).when(nodeExecutionService).updateStatusWithOps("parentId", RUNNING, null, EnumSet.of(PAUSED));
    res = resumeStepStatusUpdate.resumeParents(
        NodeExecution.builder()
            .uuid("nodeExecutionId")
            .parentId("parentId")
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
            .build());
    verify(nodeExecutionService, times(1)).updateStatusWithOps("parentId", RUNNING, null, EnumSet.of(PAUSED));
    assertFalse(res);

    doReturn(NodeExecution.builder().build())
        .when(nodeExecutionService)
        .updateStatusWithOps("parentId", RUNNING, null, EnumSet.of(PAUSED));
    res = resumeStepStatusUpdate.resumeParents(
        NodeExecution.builder()
            .uuid("nodeExecutionId")
            .parentId("parentId")
            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
            .build());
    verify(nodeExecutionService, times(2)).updateStatusWithOps("parentId", RUNNING, null, EnumSet.of(PAUSED));
    assertTrue(res);
  }
}
