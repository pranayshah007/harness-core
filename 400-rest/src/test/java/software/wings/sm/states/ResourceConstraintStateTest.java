/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ResourceConstraint;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.distribution.constraint.Constraint;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.PhaseElement;
import software.wings.api.ResourceConstraintExecutionData;
import software.wings.api.WorkflowElement;
import software.wings.beans.Application;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ResourceConstraintService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.ResourceConstraintState.AcquireMode;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ResourceConstraintStateTest extends WingsBaseTest {
  @Mock ExecutionContextImpl executionContext;
  @Mock ResourceConstraintService resourceConstraintService;
  @Mock AppService applicationService;
  @Mock FeatureFlagService featureFlagService;

  @InjectMocks @Spy ResourceConstraintState state = new ResourceConstraintState("rcs");

  private String phaseName;

  private static final String PIPELINE_DEPLOYMENT_UUID = "PIPE_UUID";

  @Before
  public void setUp() {
    phaseName = "phase-name";
    Application app = Application.Builder.anApplication().uuid(APP_ID).accountId(ACCOUNT_ID).build();
    when(executionContext.fetchRequiredApp()).thenReturn(app);
    when(executionContext.getWorkflowExecutionId()).thenReturn(WORKFLOW_EXECUTION_ID);
    when(executionContext.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
        .thenReturn(PhaseElement.builder().phaseName(phaseName).build());
    when(executionContext.getContextElement(ContextElementType.STANDARD))
        .thenReturn(
            WorkflowStandardParams.Builder.aWorkflowStandardParams()
                .withWorkflowElement(WorkflowElement.builder().pipelineDeploymentUuid(PIPELINE_DEPLOYMENT_UUID).build())
                .build());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void alreadyAcquiredPermits() {
    when(resourceConstraintService.getAllCurrentlyAcquiredPermits(
             HoldingScope.WORKFLOW.name(), ResourceConstraintService.releaseEntityId(WORKFLOW_EXECUTION_ID), APP_ID))
        .thenReturn(0, 1);
    when(resourceConstraintService.getAllCurrentlyAcquiredPermits(HoldingScope.PHASE.name(),
             ResourceConstraintService.releaseEntityId(WORKFLOW_EXECUTION_ID, phaseName), APP_ID))
        .thenReturn(0, 1);
    when(resourceConstraintService.getAllCurrentlyAcquiredPermits(
             HoldingScope.PIPELINE.name(), ResourceConstraintService.releaseEntityId(PIPELINE_DEPLOYMENT_UUID), APP_ID))
        .thenReturn(0, 1);
    int permits_1 = state.alreadyAcquiredPermits(HoldingScope.WORKFLOW.name(), executionContext);
    int permits_2 = state.alreadyAcquiredPermits(HoldingScope.WORKFLOW.name(), executionContext);
    assertThat(permits_1).isEqualTo(0);
    assertThat(permits_2).isEqualTo(1);

    int permits_3 = state.alreadyAcquiredPermits(HoldingScope.PIPELINE.name(), executionContext);
    int permits_4 = state.alreadyAcquiredPermits(HoldingScope.PIPELINE.name(), executionContext);
    assertThat(permits_3).isEqualTo(0);
    assertThat(permits_4).isEqualTo(1);

    int permits_5 = state.alreadyAcquiredPermits(HoldingScope.PHASE.name(), executionContext);
    int permits_6 = state.alreadyAcquiredPermits(HoldingScope.PHASE.name(), executionContext);
    assertThat(permits_5).isEqualTo(2);
    assertThat(permits_6).isEqualTo(3);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void alreadyAcquiredPermitsPipeline() {
    when(resourceConstraintService.getAllCurrentlyAcquiredPermits(
             HoldingScope.PIPELINE.name(), ResourceConstraintService.releaseEntityId(PIPELINE_DEPLOYMENT_UUID), APP_ID))
        .thenReturn(1);
    int permits = state.alreadyAcquiredPermits(HoldingScope.PIPELINE.name(), executionContext);
    assertThat(permits).isEqualTo(1);
  }

  @Test(expected = IllegalArgumentException.class)
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testExecute_error() {
    doReturn("accountid").when(applicationService).getAccountIdByAppId(nullable(String.class));
    doReturn(Constraint.builder().build())
        .when(resourceConstraintService)
        .createAbstraction(any(ResourceConstraint.class));

    doReturn(mock(ResourceConstraint.class))
        .when(resourceConstraintService)
        .get(nullable(String.class), nullable(String.class));
    doReturn(2).when(state).alreadyAcquiredPermits(any(), any());

    state.setAcquireMode(AcquireMode.ENSURE);
    state.setHoldingScope("UNMAPPED_SCOPE");
    state.execute(executionContext);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testExecutionResponseBuilder() {
    final ResourceConstraint resourceConstraint = ResourceConstraint.builder().build();
    state.setAcquireMode(AcquireMode.ENSURE);
    state.setPermits(10);

    final ExecutionResponseBuilder executionResponseBuilder =
        state.executionResponseBuilder(resourceConstraint, "resoourceunit");
    final ResourceConstraintExecutionData stateExecutionData =
        (ResourceConstraintExecutionData) executionResponseBuilder.build().getStateExecutionData();
    assertThat(stateExecutionData.getUsage()).isEqualTo(10);
  }
}
