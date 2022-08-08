/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.PRABU;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.Workflow.WorkflowBuilder.aWorkflow;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionEvent;
import software.wings.sm.ExecutionEventAdvice;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.states.EnvState;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PipelineStageExecutionAdvisorTest extends WingsBaseTest {
  @Mock WorkflowExecutionService workflowExecutionService;
  @Mock WorkflowService workflowService;
  @Inject @InjectMocks PipelineStageExecutionAdvisor pipelineStageExecutionAdvisor;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldSkipStageFirstIfContinueWithDefaultValuesSelected() {
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
}
