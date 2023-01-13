/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.execution.Status;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeStatusUpdateHandlerFactoryTest extends CategoryTest {
  @Mock ApprovalStepStatusUpdate approvalStepStatusUpdate;
  @Mock InterventionWaitStepStatusUpdate interventionWaitStepStatusUpdate;
  @Mock PausedStepStatusUpdate pausedStepStatusUpdate;
  @Mock InputWaitingStepStatusUpdate inputWaitingStepStatusUpdate;
  @Mock ResumeStepStatusUpdate resumeStepStatusUpdate;
  @Mock TerminalStepStatusUpdate terminalStepStatusUpdate;
  @Mock AbortAndRunningStepStatusUpdate abortAndRunningStepStatusUpdate;
  @Mock WaitStepStatusUpdate waitStepStatusUpdate;
  @Mock AsyncWaitingStatusUpdate asyncWaitingStatusUpdate;
  @InjectMocks NodeStatusUpdateHandlerFactory nodeStatusUpdateHandlerFactory;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testObtainStepStatusUpdate() {
    assertThat(nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(
                   NodeUpdateInfo.builder()
                       .nodeExecution(NodeExecution.builder().status(Status.APPROVAL_WAITING).build())
                       .build()))
        .isEqualTo(approvalStepStatusUpdate);
    assertThat(nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(
                   NodeUpdateInfo.builder()
                       .nodeExecution(NodeExecution.builder().status(Status.INTERVENTION_WAITING).build())
                       .build()))
        .isEqualTo(interventionWaitStepStatusUpdate);
    assertThat(
        nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(
            NodeUpdateInfo.builder().nodeExecution(NodeExecution.builder().status(Status.PAUSED).build()).build()))
        .isEqualTo(pausedStepStatusUpdate);
    assertThat(nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(
                   NodeUpdateInfo.builder()
                       .nodeExecution(NodeExecution.builder().status(Status.INPUT_WAITING).build())
                       .build()))
        .isEqualTo(inputWaitingStepStatusUpdate);
    assertThat(
        nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(
            NodeUpdateInfo.builder().nodeExecution(NodeExecution.builder().status(Status.QUEUED).build()).build()))
        .isEqualTo(resumeStepStatusUpdate);
    assertThat(
        nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(
            NodeUpdateInfo.builder().nodeExecution(NodeExecution.builder().status(Status.ABORTED).build()).build()))
        .isEqualTo(abortAndRunningStepStatusUpdate);
    assertThat(nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(
                   NodeUpdateInfo.builder()
                       .nodeExecution(NodeExecution.builder().status(Status.WAIT_STEP_RUNNING).build())
                       .build()))
        .isEqualTo(waitStepStatusUpdate);
    assertThat(nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(
                   NodeUpdateInfo.builder()
                       .nodeExecution(NodeExecution.builder().status(Status.ASYNC_WAITING).build())
                       .build()))
        .isEqualTo(asyncWaitingStatusUpdate);

    assertThat(
        nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(
            NodeUpdateInfo.builder().nodeExecution(NodeExecution.builder().status(Status.FAILED).build()).build()))
        .isEqualTo(terminalStepStatusUpdate);
    assertThat(
        nodeStatusUpdateHandlerFactory.obtainStepStatusUpdate(
            NodeUpdateInfo.builder().nodeExecution(NodeExecution.builder().status(Status.RUNNING).build()).build()))
        .isNull();
  }
}
