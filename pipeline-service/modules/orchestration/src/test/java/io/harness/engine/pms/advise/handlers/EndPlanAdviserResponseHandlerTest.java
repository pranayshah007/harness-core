/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.interrupts.InterruptManager;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.advisers.AdviseType;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.EndPlanAdvise;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.interrupts.AdviserIssuer;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class EndPlanAdviserResponseHandlerTest extends OrchestrationTestBase {
  @Mock private InterruptManager interruptManager;
  @InjectMocks @Inject private EndPlanAdviserResponseHandler endPlanAdviserResponseHandler;

  @Inject private PlanExecutionService planExecutionService;
  @Inject @InjectMocks private NodeExecutionService nodeExecutionService;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;

  private static final String PLAN_EXECUTION_ID = generateUuid();
  private static final String NODE_EXECUTION_ID = generateUuid();
  private static final String NODE_SETUP_ID = generateUuid();

  private NodeExecution nodeExecution;
  private EndPlanAdvise advise;

  @Before
  public void setup() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(PLAN_EXECUTION_ID)
                            .addAllLevels(Collections.singletonList(Level.newBuilder()
                                                                        .setRuntimeId(NODE_EXECUTION_ID)
                                                                        .setNodeType(NodeType.PLAN_NODE.name())
                                                                        .setSetupId(NODE_SETUP_ID)
                                                                        .build()))
                            .build();

    when(planExecutionMetadataService.findByPlanExecutionId(any()))
        .thenReturn(Optional.of(PlanExecutionMetadata.builder().build()));

    planExecutionService.save(PlanExecution.builder().uuid(PLAN_EXECUTION_ID).status(Status.RUNNING).build());

    nodeExecution = NodeExecution.builder()
                        .uuid(NODE_EXECUTION_ID)
                        .ambiance(ambiance)
                        .nodeId(NODE_SETUP_ID)
                        .name("DUMMY")
                        .identifier("dummy")
                        .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                        .module("CD")
                        .startTs(System.currentTimeMillis())
                        .status(Status.FAILED)
                        .build();
    nodeExecutionService.save(nodeExecution);
    advise = EndPlanAdvise.newBuilder().build();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestHandleAdviseWithEndTransition() {
    endPlanAdviserResponseHandler.handleAdvise(
        nodeExecution, AdviserResponse.newBuilder().setEndPlanAdvise(advise).setType(AdviseType.END_PLAN).build());
    Status planExecutionStatus = planExecutionService.getStatus(PLAN_EXECUTION_ID);
    assertThat(planExecutionStatus).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestHandlerAdviseWithAbort() {
    advise = EndPlanAdvise.newBuilder().setIsAbort(true).build();

    ArgumentCaptor<InterruptPackage> interruptPackageArgumentCaptor = ArgumentCaptor.forClass(InterruptPackage.class);
    when(interruptManager.register(interruptPackageArgumentCaptor.capture())).thenReturn(null);

    endPlanAdviserResponseHandler.handleAdvise(
        nodeExecution, AdviserResponse.newBuilder().setEndPlanAdvise(advise).setType(AdviseType.END_PLAN).build());

    InterruptPackage interruptPackage = interruptPackageArgumentCaptor.getValue();
    assertThat(interruptPackage.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(interruptPackage.getInterruptType()).isEqualTo(InterruptType.ABORT);
    assertThat(interruptPackage.getNodeExecutionId()).isEqualTo(nodeExecution.getUuid());
    assertThat(interruptPackage.getInterruptConfig().getIssuedBy().getAdviserIssuer())
        .isEqualTo(AdviserIssuer.newBuilder().setAdviserType(AdviseType.END_PLAN).build());
    verify(interruptManager, times(1)).register(any());
  }
}
