/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.rollback;

import static io.harness.beans.EnvironmentType.NON_PROD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static software.wings.common.InfrastructureConstants.RC_INFRA_STEP_NAME;
import static software.wings.service.impl.workflow.WorkflowServiceTestHelper.constructBasicWorkflowWithRollbackForAMI;
import static software.wings.sm.StateType.AWS_AMI_ROLLBACK_SWITCH_ROUTES;
import static software.wings.sm.StateType.AWS_AMI_SERVICE_DEPLOY;
import static software.wings.sm.StateType.AWS_AMI_SERVICE_ROLLBACK;
import static software.wings.sm.StateType.AWS_AMI_SERVICE_SETUP;
import static software.wings.sm.StateType.AWS_AMI_SWITCH_ROUTES;
import static software.wings.sm.StateType.PHASE;
import static software.wings.sm.StateType.PHASE_STEP;
import static software.wings.sm.StateType.RESOURCE_CONSTRAINT;
import static software.wings.sm.StateType.STAGING_ORIGINAL_EXECUTION;
import static software.wings.sm.StateType.SUB_WORKFLOW;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.CustomOrchestrationWorkflow;
import software.wings.beans.GraphNode;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.exception.InvalidRollbackException;
import software.wings.service.impl.workflow.queuing.WorkflowConcurrencyHelper;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.StateMachine;
import software.wings.sm.StateTypeDescriptor;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class RollbackStateMachineGeneratorTest extends WingsBaseTest {
  @Mock private WorkflowService workflowService;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private WorkflowConcurrencyHelper workflowConcurrencyHelper;
  @InjectMocks @Inject private RollbackStateMachineGenerator stateMachineGenerator;

  private final Map<String, StateTypeDescriptor> stencilMap =
      ImmutableMap.<String, StateTypeDescriptor>builder()
          .put("PHASE", PHASE)
          .put("PHASE_STEP", PHASE_STEP)
          .put("SUB_WORK_FLOW", SUB_WORKFLOW)
          .put("AWS_AMI_SERVICE_SETUP", AWS_AMI_SERVICE_SETUP)
          .put("AWS_AMI_SERVICE_DEPLOY", AWS_AMI_SERVICE_DEPLOY)
          .put("AWS_AMI_SWITCH_ROUTES", AWS_AMI_SWITCH_ROUTES)
          .put("AWS_AMI_ROLLBACK_SWITCH_ROUTES", AWS_AMI_ROLLBACK_SWITCH_ROUTES)
          .put("AWS_AMI_SERVICE_ROLLBACK", AWS_AMI_SERVICE_ROLLBACK)
          .put("STAGING_ORIGINAL_EXECUTION", STAGING_ORIGINAL_EXECUTION)
          .put("RESOURCE_CONSTRAINT", RESOURCE_CONSTRAINT)
          .build();

  private final WorkflowExecution execution = WorkflowExecution.builder()
                                                  .appId(APP_ID)
                                                  .envType(NON_PROD)
                                                  .status(ExecutionStatus.SUCCESS)
                                                  .workflowType(WorkflowType.ORCHESTRATION)
                                                  .uuid(generateUuid())
                                                  .workflowId(WORKFLOW_ID)
                                                  .build();

  private final WorkflowExecution runningExecution = WorkflowExecution.builder()
                                                         .appId(APP_ID)
                                                         .envType(NON_PROD)
                                                         .status(ExecutionStatus.RUNNING)
                                                         .workflowType(WorkflowType.ORCHESTRATION)
                                                         .uuid(generateUuid())
                                                         .workflowId(WORKFLOW_ID)
                                                         .build();

  @Before
  public void setUp() throws Exception {
    when(workflowConcurrencyHelper.getResourceConstraintStep(any(), any()))
        .thenReturn(
            GraphNode.builder().id(generateUuid()).type(RESOURCE_CONSTRAINT.name()).name(RC_INFRA_STEP_NAME).build());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldGenerateForRollbackExecution() {
    Workflow workflow = constructBasicWorkflowWithRollbackForAMI();
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(execution);
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowService.stencilMap(any())).thenReturn(stencilMap);
    StateMachine originalStateMachine = new StateMachine(workflow, workflow.getDefaultVersion(),
        ((CustomOrchestrationWorkflow) workflow.getOrchestrationWorkflow()).getGraph(), stencilMap, false);
    assertThat(originalStateMachine).isNotNull();
    assertThat(originalStateMachine.getStates()).hasSize(4);
    assertThat(originalStateMachine.getChildStateMachines()).hasSize(5);
    StateMachine sm = stateMachineGenerator.generateForRollbackExecution(APP_ID, WORKFLOW_EXECUTION_ID);
    assertThat(sm).isNotNull();
    assertThat(sm.getStates()).hasSize(4);
    assertThat(sm.getTransitions()).hasSize(1);
    assertThat(sm.getChildStateMachines()).hasSize(4);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldNotGenerateForRollback() {
    Workflow workflow = constructBasicWorkflowWithRollbackForAMI();
    when(workflowExecutionService.getWorkflowExecution(APP_ID, WORKFLOW_EXECUTION_ID)).thenReturn(runningExecution);
    when(workflowService.readWorkflow(APP_ID, WORKFLOW_ID)).thenReturn(workflow);
    when(workflowService.stencilMap(any())).thenReturn(stencilMap);
    assertThatThrownBy(() -> stateMachineGenerator.generateForRollbackExecution(APP_ID, WORKFLOW_EXECUTION_ID))
        .isInstanceOf(InvalidRollbackException.class);
  }
}
