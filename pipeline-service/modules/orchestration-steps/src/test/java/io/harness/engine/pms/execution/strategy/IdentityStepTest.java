/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.pms.execution.strategy.identity.IdentityStep;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityStepTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsSweepingOutputService pmsSweepingOutputService;
  @Mock private PmsOutcomeService pmsOutcomeService;
  @Inject @InjectMocks private IdentityStep identityStep;

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
        .build();
  }

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testObtainTask() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    ChildExecutableResponse expectedChildExecutable =
        ChildExecutableResponse.newBuilder().setChildNodeId("childId").build();
    ExecutableResponse executableResponse = ExecutableResponse.newBuilder().setChild(expectedChildExecutable).build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid("nodeUuid").executableResponse(executableResponse).build();
    doReturn(nodeExecution).when(nodeExecutionService).getWithFieldsIncluded(any(), any());

    ChildExecutableResponse childExecutableResponse = identityStep.obtainChild(ambiance, identityParams, null);
    verify(pmsSweepingOutputService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(childExecutableResponse.getChildNodeId()).isEqualTo("childId");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleChildResponse() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    NodeExecution nodeExecution = NodeExecution.builder().uuid("nodeUuid").status(Status.ABORTED).build();
    doReturn(nodeExecution).when(nodeExecutionService).get(anyString());

    StepResponse stepResponse = identityStep.handleChildResponse(ambiance, identityParams, null);
    verify(pmsOutcomeService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(stepResponse.getStatus()).isEqualTo(Status.ABORTED);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testObtainChildren() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    ChildrenExecutableResponse expectedChildrenExecutable = ChildrenExecutableResponse.newBuilder().build();
    ExecutableResponse executableResponse =
        ExecutableResponse.newBuilder().setChildren(expectedChildrenExecutable).build();
    NodeExecution nodeExecution =
        NodeExecution.builder().uuid("nodeUuid").executableResponse(executableResponse).build();
    doReturn(nodeExecution).when(nodeExecutionService).get(anyString());

    ChildrenExecutableResponse childrenExecutableResponse = identityStep.obtainChildren(ambiance, identityParams, null);
    verify(pmsSweepingOutputService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(expectedChildrenExecutable).isEqualTo(childrenExecutableResponse);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testHandleChildrenResponse() {
    Ambiance ambiance = buildAmbiance();
    IdentityStepParameters identityParams =
        IdentityStepParameters.builder().originalNodeExecutionId("nodeUuid").build();

    // nodeExecution formation
    NodeExecution nodeExecution = NodeExecution.builder().uuid("nodeUuid").status(Status.ABORTED).build();
    doReturn(nodeExecution).when(nodeExecutionService).get(anyString());

    StepResponse stepResponse = identityStep.handleChildrenResponse(ambiance, identityParams, null);
    verify(pmsOutcomeService, times(1)).cloneForRetryExecution(ambiance, "nodeUuid");
    assertThat(stepResponse.getStatus()).isEqualTo(Status.ABORTED);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetStepParameters() {
    assertThat(identityStep.getStepParametersClass()).isEqualTo(IdentityStepParameters.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testModifyAmbiance() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder()
                                           .setRuntimeId("RID")
                                           .setStepType(StepType.newBuilder().getDefaultInstanceForType())
                                           .build())
                            .build();
    Ambiance ambiance1 = IdentityStep.modifyAmbiance(ambiance);
    assertEquals(ambiance1.getLevels(0).getStepType().getType(), "IDENTITY_STEP");
    assertEquals(ambiance1.getLevels(0).getStepType().getStepCategory(), StepCategory.STEP);
    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("RID")
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY))
                                  .setNodeType("IDENTITY_PLAN_NODE")
                                  .build())
                   .build();
    ambiance1 = IdentityStep.modifyAmbiance(ambiance);
    assertEquals(ambiance1.getLevels(0).getStepType().getType(), "IDENTITY_STRATEGY");
    assertEquals(ambiance1.getLevels(0).getStepType().getStepCategory(), StepCategory.STRATEGY);
    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("RID")
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY))
                                  .setNodeType("IDENTITY_PLAN_NODE")
                                  .build())
                   .addLevels(Level.newBuilder()
                                  .setRuntimeId("RID")
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP))
                                  .build())
                   .build();
    ambiance1 = IdentityStep.modifyAmbiance(ambiance);
    assertEquals(ambiance1.getLevels(1).getStepType().getType(), "IDENTITY_STRATEGY_INTERNAL");
    assertEquals(ambiance1.getLevels(0).getStepType().getStepCategory(), StepCategory.STRATEGY);
  }
}
