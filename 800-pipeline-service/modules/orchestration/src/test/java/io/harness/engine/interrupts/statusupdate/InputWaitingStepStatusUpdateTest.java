/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.pms.contracts.execution.Status.INPUT_WAITING;
import static io.harness.rule.OwnerRule.BRIJESH;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class InputWaitingStepStatusUpdateTest extends CategoryTest {
  @Mock private PlanExecutionService planExecutionService;
  @Mock private NodeExecutionService nodeExecutionService;
  @InjectMocks InputWaitingStepStatusUpdate inputWaitingStepStatusUpdate;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleNodeStatusUpdate() {
    String planExecutionId = "planExecutionId";
    String parentId = "parentId";
    NodeUpdateInfo nodeUpdateInfo =
        NodeUpdateInfo.builder()
            .nodeExecution(NodeExecution.builder()
                               .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                               .build())
            .build();

    inputWaitingStepStatusUpdate.handleNodeStatusUpdate(nodeUpdateInfo);

    verify(planExecutionService, times(1)).updateCalculatedStatus(planExecutionId);

    nodeUpdateInfo = NodeUpdateInfo.builder()
                         .nodeExecution(NodeExecution.builder()
                                            .parentId(parentId)
                                            .ambiance(Ambiance.newBuilder().setPlanExecutionId(planExecutionId).build())
                                            .build())
                         .build();

    doReturn(Collections.emptyList())
        .when(nodeExecutionService)
        .findByParentIdAndStatusIn(parentId, EnumSet.noneOf(Status.class));
    inputWaitingStepStatusUpdate.handleNodeStatusUpdate(nodeUpdateInfo);

    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(parentId, INPUT_WAITING, null, EnumSet.noneOf(Status.class));
  }
}
