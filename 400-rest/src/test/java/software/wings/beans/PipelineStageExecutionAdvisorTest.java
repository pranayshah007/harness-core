/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.SPG_PIPELINE_ROLLBACK;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateMachine.StateMachineBuilder.aStateMachine;
import static software.wings.sm.Transition.Builder.aTransition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionEvent;
import software.wings.sm.ExecutionEventAdvice;
import software.wings.sm.PipelineSummary;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateMachine;
import software.wings.sm.TransitionType;
import software.wings.sm.states.EnvRollbackState;
import software.wings.sm.states.EnvState;
import software.wings.sm.states.ForkState;

import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PipelineStageExecutionAdvisorTest extends WingsBaseTest {
  @Mock WorkflowExecutionService workflowExecutionService;
  @Mock WorkflowService workflowService;
  @Mock PipelineService pipelineService;
  @Mock FeatureFlagService featureFlagService;
  @Inject Injector injector;
  @Inject @InjectMocks PipelineStageExecutionAdvisor pipelineStageExecutionAdvisor;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSkipStageFirstIfContinueWithDefaultValuesSelected() {
    WorkflowExecution mockExecution = mock(WorkflowExecution.class);
    PipelineSummary mockSummary = mock(PipelineSummary.class);
    doReturn(mockExecution).when(workflowExecutionService).getWorkflowExecution(any(), any(), any(String[].class));
    doReturn(mockSummary).when(mockExecution).getPipelineSummary();
    EnvState envState = new EnvState("workflow1");
    envState.setDisableAssertion("true");
    envState.setWorkflowId("WORKFLOW_ID");
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance().build();
    ExecutionEvent executionEvent =
        ExecutionEvent.builder().state(envState).context(new ExecutionContextImpl(stateExecutionInstance)).build();
    when(workflowService.readWorkflowWithoutServices(any(), any()))
        .thenReturn(aWorkflow().orchestrationWorkflow(aCanaryOrchestrationWorkflow().build()).build());
    ExecutionEventAdvice executionEventAdvice = pipelineStageExecutionAdvisor.onExecutionEvent(executionEvent);
    assertThat(executionEventAdvice).isNotNull();
    assertThat(executionEventAdvice.getExecutionResponse().getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testAdviceShouldBeRollback() {
    WorkflowExecution mockExecution = mock(WorkflowExecution.class);
    PipelineSummary mockSummary = mock(PipelineSummary.class);
    doReturn(mockExecution).when(workflowExecutionService).getWorkflowExecution(any(), any(), any(String[].class));
    doReturn(mockSummary).when(mockExecution).getPipelineSummary();
    doReturn("PIPELINE_ID").when(mockSummary).getPipelineId();

    Pipeline pipeline = Pipeline.builder().uuid("PIPELINE_ID").appId("APP_ID").rollbackPreviousStages(true).build();
    doReturn(pipeline).when(pipelineService).getPipeline(any(), eq("PIPELINE_ID"));
    doReturn(true).when(featureFlagService).isEnabled(eq(SPG_PIPELINE_ROLLBACK), any());
    EnvState envState1 = new EnvState("workflow1");
    envState1.setWorkflowId("WORKFLOW_ID");
    EnvState envState2 = new EnvState("workflow2");
    envState2.setWorkflowId("WORKFLOW2_ID");
    Map<String, String> rollbackPipelineMap = new HashMap();
    EnvRollbackState envRollbackState1 = new EnvRollbackState("rollback-workflow1");
    EnvRollbackState envRollbackState2 = new EnvRollbackState("rollback-workflow2");
    rollbackPipelineMap.put("workflow2", "rollback-workflow2");
    StateMachine sm = aStateMachine()
                          .addState(envState1)
                          .addState(envState2)
                          .addState(envRollbackState2)
                          .addState(envRollbackState1)
                          .addTransition(aTransition()
                                             .withFromState(envState1)
                                             .withToState(envState2)
                                             .withTransitionType(TransitionType.SUCCESS)
                                             .build())
                          .addTransition(aTransition()
                                             .withFromState(envRollbackState2)
                                             .withToState(envRollbackState1)
                                             .withTransitionType(TransitionType.SUCCESS)
                                             .build())
                          .withRollbackPipelineMap(rollbackPipelineMap)
                          .build();
    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().appId("APP_ID").status(ExecutionStatus.FAILED).build();
    ExecutionEvent executionEvent = ExecutionEvent.builder()
                                        .state(envState2)
                                        .context(new ExecutionContextImpl(stateExecutionInstance, sm, injector))
                                        .build();

    ExecutionEventAdvice advice = pipelineStageExecutionAdvisor.onExecutionEvent(executionEvent);
    assertThat(advice).isNotNull();
    assertThat(advice.getExecutionInterruptType())
        .isEqualTo(ExecutionInterruptType.ROLLBACK_PREVIOUS_STAGES_ON_PIPELINE);
    assertThat(advice.getNextStateName()).isEqualTo(envRollbackState2.getName());
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testAdviceShouldBeNull_whenPartOfFork() {
    doReturn(true).when(featureFlagService).isEnabled(eq(SPG_PIPELINE_ROLLBACK), any());

    WorkflowExecution mockExecution = mock(WorkflowExecution.class);
    PipelineSummary mockSummary = mock(PipelineSummary.class);
    doReturn(mockExecution).when(workflowExecutionService).getWorkflowExecution(any(), any(), any(String[].class));
    doReturn(mockSummary).when(mockExecution).getPipelineSummary();
    doReturn("PIPELINE_ID").when(mockSummary).getPipelineId();
    Pipeline pipeline = Pipeline.builder().uuid("PIPELINE_ID").appId("APP_ID").rollbackPreviousStages(true).build();
    doReturn(pipeline).when(pipelineService).getPipeline(any(), eq("PIPELINE_ID"));

    EnvState envState1 = new EnvState("workflow1");
    envState1.setWorkflowId("WORKFLOW_ID");
    EnvState envState2 = new EnvState("workflow2");
    envState2.setWorkflowId("WORKFLOW2_ID");

    ForkState forkState = new ForkState("fork-workflow1-1");
    forkState.addForkState(envState1);
    forkState.addForkState(envState2);

    EnvRollbackState envRollbackState1 = new EnvRollbackState("rollback-workflow1");
    EnvRollbackState envRollbackState2 = new EnvRollbackState("rollback-workflow2");
    ForkState rollbackForkState = new ForkState("rollback-fork-workflow1-1");
    forkState.addForkState(envRollbackState1);
    forkState.addForkState(envRollbackState2);

    StateMachine sm = aStateMachine()
                          .addState(envState1)
                          .addState(envState2)
                          .addState(forkState)
                          .addState(rollbackForkState)
                          .addState(envRollbackState2)
                          .addState(envRollbackState1)
                          .build();

    StateExecutionInstance stateExecutionInstance =
        aStateExecutionInstance().parentInstanceId("parentId").appId("APP_ID").status(ExecutionStatus.FAILED).build();
    ExecutionEvent executionEvent = ExecutionEvent.builder()
                                        .state(envState2)
                                        .context(new ExecutionContextImpl(stateExecutionInstance, sm, injector))
                                        .build();

    ExecutionEventAdvice advice = pipelineStageExecutionAdvisor.onExecutionEvent(executionEvent);
    assertThat(advice.getExecutionInterruptType()).isEqualTo(ExecutionInterruptType.MARK_FAILED);
  }
}
