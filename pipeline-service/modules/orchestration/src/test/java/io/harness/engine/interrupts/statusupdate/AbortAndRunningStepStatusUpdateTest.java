/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import java.util.EnumSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class AbortAndRunningStepStatusUpdateTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @InjectMocks AbortAndRunningStepStatusUpdate abortAndRunningStepStatusUpdate;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandleNodeStatusUpdate() {
    NodeUpdateInfo nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(NodeExecution.builder().build()).build();
    abortAndRunningStepStatusUpdate.handleNodeStatusUpdate(nodeUpdateInfo);
    verify(nodeExecutionService, times(0)).updateStatusWithOps(any(), any(), any(), any());

    String parentId = "parentId";
    nodeUpdateInfo = NodeUpdateInfo.builder().nodeExecution(NodeExecution.builder().parentId(parentId).build()).build();

    abortAndRunningStepStatusUpdate.handleNodeStatusUpdate(nodeUpdateInfo);
    verify(nodeExecutionService, times(1))
        .updateStatusWithOps(parentId, Status.RUNNING, null, EnumSet.noneOf(Status.class));
  }
}
