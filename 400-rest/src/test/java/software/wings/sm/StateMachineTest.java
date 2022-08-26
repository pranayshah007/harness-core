/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.VIKAS_S;

import static software.wings.sm.states.RepeatState.Builder.aRepeatState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.GeneralNotifyEventListener;
import software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder;
import software.wings.beans.ExecutionStrategy;
import software.wings.beans.Graph;
import software.wings.beans.GraphNode;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.PhaseStep.PhaseStepBuilder;
import software.wings.beans.PhaseStepType;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowBuilder;
import software.wings.common.InstanceExpressionProcessor;
import software.wings.rules.Listeners;
import software.wings.sm.StateMachineTestBase.StateSync;
import software.wings.sm.states.ForkState;
import software.wings.sm.states.RepeatState;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * The Class StateMachineTest.
 */
@Listeners(GeneralNotifyEventListener.class)
@Slf4j
public class StateMachineTest extends WingsBaseTest {
  /**
   * Should validate.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldValidate() {
    StateMachine sm = new StateMachine();
    State state = new StateSync("StateA");
    sm.addState(state);
    state = new StateSync("StateB");
    sm.addState(state);
    state = new StateSync("StateC");
    sm.addState(state);
    sm.setInitialStateName("StateA");
    assertThat(sm.validate()).as("Validate result").isTrue();
  }

  /**
   * Should throw dup error code.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldThrowDupErrorCode() {
    try {
      StateMachine sm = new StateMachine();
      State state = new StateSync("StateA");
      sm.addState(state);
      state = new StateSync("StateB");
      sm.addState(state);
      state = new StateSync("StateC");
      sm.addState(state);
      sm.setInitialStateName("StateA");
      state = new StateSync("StateB");
      sm.addState(state);
      sm.validate();
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.DUPLICATE_STATE_NAMES.name());
    }
  }

  /**
   * Should throw null transition.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldThrowNullTransition() {
    try {
      StateMachine sm = new StateMachine();
      State stateA = new StateSync("StateA");
      sm.addState(stateA);
      StateSync stateB = new StateSync("StateB");
      sm.addState(stateB);
      sm.setInitialStateName("StateA");

      sm.addTransition(Transition.Builder.aTransition().withToState(stateA).withFromState(stateB).build());
      sm.validate();
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.TRANSITION_TYPE_NULL.name());
    }
  }
  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void stateMachineShouldBeMarkedInvalidIfGivenPropertiesAreInvalid_ApprovalState() {
    /**
     * This test Checks if properties for a given state are invalid, StateMachine should be marked invalid,
     * The orchestrationWorkflow for Generated StateMachine should also marked invalid with appropriate
     * validationMessage.
     *
     * This test specifically checks for APPROVAL state. In approval state timeoutMillis field is defined as
     * Integer. If provided properties contains a bigger number (i.e. Long). Corresponding mapping would fail
     * and generated StateMachine should contain an invalid orchestrationWorkflow.
     */
    Map<String, Object> properties = new HashMap<>();
    properties.put("userGroups", Collections.singletonList("qM90ydbbTfiAt-QF4ohD8Q"));
    properties.put("timeoutMillis", 7776000000L); // This is a long, Where as timeoutMillis field in ApprovalState is
                                                  // Integer.
    properties.put("approvalStateType", "USER_GROUP");
    properties.put("parentId", "mXgEfqzMQAGFptFi0-GM5A");

    GraphNode node = GraphNode.builder().name("Approval").type("APPROVAL").origin(true).properties(properties).build();

    Graph graph = Graph.Builder.aGraph().addNodes(node).build();

    OrchestrationWorkflow orchestrationWorkflow =
        CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow()
            .withPreDeploymentSteps(
                PhaseStepBuilder.aPhaseStep(PhaseStepType.PRE_DEPLOYMENT, "pre-deploy").addStep(node).build())
            .build();

    Workflow workflow = WorkflowBuilder.aWorkflow().envId("env").orchestrationWorkflow(orchestrationWorkflow).build();

    Map<String, StateTypeDescriptor> stencilMap = new HashMap<>();
    stencilMap.put("APPROVAL", StateType.APPROVAL);

    StateMachine sm = new StateMachine(workflow, 1, graph, stencilMap, false);
    assertThat(sm.getOrchestrationWorkflow().isValid()).isFalse();
    assertThat(sm.getOrchestrationWorkflow().getValidationMessage())
        .contains("Invalid value for timeoutMillis or value exceeded maximum timeout of 3w 3d 20h 30m.");
    assertThat(sm.isValid()).isFalse();
  }

  /**
   * Should throw transition not linked.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldThrowTransitionNotLinked() {
    try {
      StateMachine sm = new StateMachine();
      State stateA = new StateSync("StateA");
      sm.addState(stateA);
      StateSync stateB = new StateSync("StateB");
      sm.addState(stateB);
      sm.setInitialStateName("StateA");

      sm.addTransition(Transition.Builder.aTransition()
                           .withToState(stateA)
                           .withFromState(stateB)
                           .withTransitionType(TransitionType.SUCCESS)
                           .build());
      sm.addTransition(Transition.Builder.aTransition()
                           .withToState(stateB)
                           .withFromState(null)
                           .withTransitionType(TransitionType.SUCCESS)
                           .build());
      sm.validate();
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.TRANSITION_NOT_LINKED.name());
    }
  }

  /**
   * Should throw transition to incorrect state.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldThrowTransitionToIncorrectState() {
    try {
      StateMachine sm = new StateMachine();
      State stateA = new StateSync("StateA");
      sm.addState(stateA);
      StateSync stateB = new StateSync("StateB");
      sm.addState(stateB);
      sm.setInitialStateName("StateA");

      StateSync stateC = new StateSync("StateC");
      StateSync stateD = new StateSync("StateD");
      sm.addTransition(Transition.Builder.aTransition()
                           .withToState(stateA)
                           .withFromState(stateB)
                           .withTransitionType(TransitionType.SUCCESS)
                           .build());
      sm.addTransition(Transition.Builder.aTransition()
                           .withToState(stateD)
                           .withFromState(stateC)
                           .withTransitionType(TransitionType.SUCCESS)
                           .build());
      sm.validate();
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.TRANSITION_TO_INCORRECT_STATE.name());
      assertThat(exception.getParams()).hasSize(1);
      assertThat(exception.getParams()).containsKey("details");
      assertThat(exception.getParams().get("details")).asString().contains("StateC").contains("StateD");
    }
  }

  /**
   * Should throw states with Dup transitions.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldThrowStatesWithDupTransitions() {
    try {
      StateMachine sm = new StateMachine();
      State stateA = new StateSync("StateA");
      sm.addState(stateA);
      StateSync stateB = new StateSync("StateB");
      sm.addState(stateB);
      StateSync stateC = new StateSync("StateC");
      sm.addState(stateC);
      sm.setInitialStateName("StateA");

      sm.addTransition(Transition.Builder.aTransition()
                           .withToState(stateB)
                           .withFromState(stateA)
                           .withTransitionType(TransitionType.SUCCESS)
                           .build());
      sm.addTransition(Transition.Builder.aTransition()
                           .withToState(stateC)
                           .withFromState(stateA)
                           .withTransitionType(TransitionType.SUCCESS)
                           .build());
      sm.addTransition(Transition.Builder.aTransition()
                           .withToState(stateB)
                           .withFromState(stateC)
                           .withTransitionType(TransitionType.SUCCESS)
                           .build());
      sm.addTransition(Transition.Builder.aTransition()
                           .withToState(stateA)
                           .withFromState(stateC)
                           .withTransitionType(TransitionType.SUCCESS)
                           .build());
      sm.validate();
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.STATES_WITH_DUP_TRANSITIONS.name());
      assertThat(exception.getParams()).hasSize(1);
      assertThat(exception.getParams()).containsKey("details");
      assertThat(exception.getParams().get("details")).asString().contains("StateA").contains("StateC");
    }
  }

  /**
   * Should throw non-fork state transition.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldThrowNonForkStateTransitions() {
    try {
      StateMachine sm = new StateMachine();
      State stateA = new StateSync("StateA");
      sm.addState(stateA);
      StateSync stateB = new StateSync("StateB");
      sm.addState(stateB);
      StateSync stateC = new StateSync("StateC");
      sm.addState(stateC);
      StateSync stateD = new StateSync("StateD");
      sm.addState(stateD);
      ForkState fork1 = new ForkState("fork1");
      sm.addState(fork1);
      sm.setInitialStateName("StateA");

      sm.addTransition(Transition.Builder.aTransition()
                           .withFromState(stateA)
                           .withToState(stateB)
                           .withTransitionType(TransitionType.SUCCESS)
                           .build());
      sm.addTransition(Transition.Builder.aTransition()
                           .withFromState(stateB)
                           .withToState(fork1)
                           .withTransitionType(TransitionType.FORK)
                           .build());
      sm.addTransition(Transition.Builder.aTransition()
                           .withFromState(fork1)
                           .withToState(stateC)
                           .withTransitionType(TransitionType.FORK)
                           .build());
      sm.addTransition(Transition.Builder.aTransition()
                           .withFromState(stateC)
                           .withToState(stateD)
                           .withTransitionType(TransitionType.FORK)
                           .build());
      sm.validate();
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.NON_FORK_STATES.name());
      assertThat(exception.getParams()).hasSize(1);
      assertThat(exception.getParams()).containsKey("details");
      assertThat(exception.getParams().get("details")).asString().contains("StateB").contains("StateC");
    }
  }

  /**
   * Should throw non-repeat state transition.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldThrowNonRepeatStateTransitions() {
    try {
      StateMachine sm = new StateMachine();
      State stateA = new StateSync("StateA");
      sm.addState(stateA);
      StateSync stateB = new StateSync("StateB");
      sm.addState(stateB);
      StateSync stateC = new StateSync("StateC");
      sm.addState(stateC);
      StateSync stateD = new StateSync("StateD");
      sm.addState(stateD);
      RepeatState repeat1 = new RepeatState("repeat1");
      sm.addState(repeat1);
      sm.setInitialStateName("StateA");

      sm.addTransition(Transition.Builder.aTransition()
                           .withFromState(stateA)
                           .withToState(stateB)
                           .withTransitionType(TransitionType.SUCCESS)
                           .build());
      sm.addTransition(Transition.Builder.aTransition()
                           .withFromState(stateB)
                           .withToState(repeat1)
                           .withTransitionType(TransitionType.REPEAT)
                           .build());
      sm.addTransition(Transition.Builder.aTransition()
                           .withFromState(repeat1)
                           .withToState(stateC)
                           .withTransitionType(TransitionType.REPEAT)
                           .build());
      sm.addTransition(Transition.Builder.aTransition()
                           .withFromState(stateC)
                           .withToState(stateD)
                           .withTransitionType(TransitionType.REPEAT)
                           .build());
      sm.validate();
      failBecauseExceptionWasNotThrown(WingsException.class);
    } catch (WingsException exception) {
      assertThat(exception).hasMessage(ErrorCode.NON_REPEAT_STATES.name());
      assertThat(exception.getParams()).hasSize(1);
      assertThat(exception.getParams()).containsKey("details");
      assertThat(exception.getParams().get("details")).asString().contains("StateB").contains("StateC");
    }
  }

  /**
   * Should throw non-repeat state transition.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldExpandRepeatState() {
    StateMachine sm = new StateMachine();
    State starting = new StateSync("starting");
    sm.addState(starting);
    RepeatState repeatByService1 = aRepeatState()
                                       .withRepeatElementExpression("services()")
                                       .withName("RepeatByServices")
                                       .withExecutionStrategy(ExecutionStrategy.SERIAL)
                                       .withRepeatElementType(ContextElementType.SERVICE)
                                       .build();
    sm.addState(repeatByService1);
    StateSync runCommand = new StateSync("command");
    runCommand.setRequiredContextElementType(ContextElementType.INSTANCE);
    sm.addState(runCommand);
    StateSync finished = new StateSync("finished");
    sm.addState(finished);
    sm.setInitialStateName("starting");

    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(starting)
                         .withToState(repeatByService1)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(repeatByService1)
                         .withToState(runCommand)
                         .withTransitionType(TransitionType.REPEAT)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(repeatByService1)
                         .withToState(finished)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.validate();

    sm.addRepeatersForRequiredContextElement();

    sm.clearCache();

    sm.validate();

    RepeatState expectedNewState = aRepeatState()
                                       .withName("Repeat " + runCommand.getName())
                                       .withRepeatElementType(ContextElementType.INSTANCE)
                                       .withExecutionStrategy(ExecutionStrategy.PARALLEL)
                                       .withRepeatElementExpression(InstanceExpressionProcessor.DEFAULT_EXPRESSION)
                                       .withRepeatTransitionStateName(runCommand.getName())
                                       .build();

    assertThat(sm.getStates()).hasSize(5).contains(expectedNewState);
    assertThat(sm.getNextStates(expectedNewState.getName(), TransitionType.REPEAT)).hasSize(1).containsOnly(runCommand);
    assertThat(sm.getNextStates("RepeatByServices", TransitionType.SUCCESS)).hasSize(1).containsOnly(finished);
  }

  /**
   * Should expand repeat state in a complex scenario.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldExpandRepeatStateInMultiplePaths() {
    StateMachine sm = new StateMachine();
    State starting = new StateSync("starting");
    sm.addState(starting);
    RepeatState repeatByService1 = aRepeatState()
                                       .withRepeatElementExpression("services()")
                                       .withName("RepeatByServices")
                                       .withExecutionStrategy(ExecutionStrategy.SERIAL)
                                       .withRepeatElementType(ContextElementType.SERVICE)
                                       .build();
    sm.addState(repeatByService1);
    RepeatState repeatByHosts1 = aRepeatState()
                                     .withRepeatElementExpression("host()")
                                     .withName("RepeatByHosts")
                                     .withExecutionStrategy(ExecutionStrategy.SERIAL)
                                     .withRepeatElementType(ContextElementType.HOST)
                                     .build();
    sm.addState(repeatByHosts1);
    StateSync runCommand = new StateSync("command");
    runCommand.setRequiredContextElementType(ContextElementType.INSTANCE);
    sm.addState(runCommand);
    StateSync finished = new StateSync("finished");
    sm.addState(finished);
    sm.setInitialStateName("starting");

    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(starting)
                         .withToState(repeatByService1)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(starting)
                         .withToState(repeatByHosts1)
                         .withTransitionType(TransitionType.FAILURE)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(repeatByService1)
                         .withToState(runCommand)
                         .withTransitionType(TransitionType.REPEAT)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(repeatByService1)
                         .withToState(finished)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(repeatByHosts1)
                         .withToState(runCommand)
                         .withTransitionType(TransitionType.REPEAT)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(repeatByHosts1)
                         .withToState(finished)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.validate();

    sm.addRepeatersForRequiredContextElement();

    sm.clearCache();

    sm.validate();

    RepeatState expectedNewState = aRepeatState()
                                       .withName("Repeat " + runCommand.getName())
                                       .withRepeatElementType(ContextElementType.INSTANCE)
                                       .withExecutionStrategy(ExecutionStrategy.PARALLEL)
                                       .withRepeatElementExpression(InstanceExpressionProcessor.DEFAULT_EXPRESSION)
                                       .withRepeatTransitionStateName(runCommand.getName())
                                       .build();

    assertThat(sm.getStates()).hasSize(6).contains(expectedNewState);
    assertThat(sm.getNextStates("RepeatByServices", TransitionType.REPEAT)).hasSize(1).containsOnly(expectedNewState);
    assertThat(sm.getNextStates("RepeatByServices", TransitionType.SUCCESS)).hasSize(1).containsOnly(finished);
    assertThat(sm.getNextStates("RepeatByHosts", TransitionType.REPEAT)).hasSize(1).containsOnly(expectedNewState);
    assertThat(sm.getNextStates("RepeatByHosts", TransitionType.SUCCESS)).hasSize(1).containsOnly(finished);
    assertThat(sm.getNextStates("RepeatByServices", TransitionType.REPEAT).get(0))
        .isSameAs(sm.getNextStates("RepeatByHosts", TransitionType.REPEAT).get(0));
  }

  /**
   * Should expand repeat state for each node that needs it.
   */
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldExpandRepeatStateForEachNodeThatNeedsIt() {
    StateMachine sm = new StateMachine();
    State starting = new StateSync("starting");
    sm.addState(starting);
    RepeatState repeatByService1 = aRepeatState()
                                       .withRepeatElementExpression("services()")
                                       .withName("RepeatByServices")
                                       .withExecutionStrategy(ExecutionStrategy.SERIAL)
                                       .withRepeatElementType(ContextElementType.SERVICE)
                                       .build();
    sm.addState(repeatByService1);
    StateSync runCommand1 = new StateSync("command1");
    runCommand1.setRequiredContextElementType(ContextElementType.INSTANCE);
    sm.addState(runCommand1);
    StateSync runCommand2 = new StateSync("command2");
    runCommand2.setRequiredContextElementType(ContextElementType.INSTANCE);
    sm.addState(runCommand2);
    StateSync runCommand3 = new StateSync("command3");
    runCommand3.setRequiredContextElementType(ContextElementType.INSTANCE);
    sm.addState(runCommand3);
    StateSync finished = new StateSync("finished");
    finished.setRequiredContextElementType(ContextElementType.INSTANCE);
    sm.addState(finished);
    sm.setInitialStateName("RepeatByServices");

    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(repeatByService1)
                         .withToState(starting)
                         .withTransitionType(TransitionType.REPEAT)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(starting)
                         .withToState(runCommand1)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(runCommand1)
                         .withToState(runCommand2)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(runCommand2)
                         .withToState(runCommand3)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(runCommand3)
                         .withToState(finished)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.validate();

    sm.addRepeatersForRequiredContextElement();

    sm.clearCache();

    sm.validate();

    RepeatState expectedNewState = aRepeatState()
                                       .withName("Repeat " + runCommand1.getName())
                                       .withRepeatElementType(ContextElementType.INSTANCE)
                                       .withExecutionStrategy(ExecutionStrategy.PARALLEL)
                                       .withRepeatElementExpression(InstanceExpressionProcessor.DEFAULT_EXPRESSION)
                                       .withRepeatTransitionStateName(runCommand1.getName())
                                       .build();

    assertThat(sm.getStates()).hasSize(7);
    assertThat(sm.getNextStates("RepeatByServices", TransitionType.REPEAT)).hasSize(1).containsOnly(starting);
    assertThat(sm.getNextStates("starting", TransitionType.SUCCESS)).hasSize(1).containsOnly(expectedNewState);
    assertThat(sm.getNextStates(expectedNewState.getName(), TransitionType.REPEAT))
        .hasSize(1)
        .containsOnly(runCommand1);
    assertThat(sm.getNextStates("command1", TransitionType.SUCCESS)).hasSize(1).containsOnly(runCommand2);
    assertThat(sm.getNextStates("command2", TransitionType.SUCCESS)).hasSize(1).containsOnly(runCommand3);
    assertThat(sm.getNextStates("command3", TransitionType.SUCCESS)).hasSize(1).containsOnly(finished);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldBreakRepeatTransitionsWhenNoNeeded() {
    StateMachine sm = new StateMachine();
    State starting = new StateSync("starting");
    sm.addState(starting);
    StateSync runCommand1 = new StateSync("command1");
    runCommand1.setRequiredContextElementType(ContextElementType.INSTANCE);
    sm.addState(runCommand1);
    StateSync runCommand2 = new StateSync("command2");
    sm.addState(runCommand2);
    StateSync runCommand3 = new StateSync("command3");
    runCommand3.setRequiredContextElementType(ContextElementType.INSTANCE);
    sm.addState(runCommand3);
    StateSync finished = new StateSync("finished");
    sm.addState(finished);
    sm.setInitialStateName(starting.getName());

    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(starting)
                         .withToState(runCommand1)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(runCommand1)
                         .withToState(runCommand2)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(runCommand2)
                         .withToState(runCommand3)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.addTransition(Transition.Builder.aTransition()
                         .withFromState(runCommand3)
                         .withToState(finished)
                         .withTransitionType(TransitionType.SUCCESS)
                         .build());
    sm.validate();

    sm.addRepeatersForRequiredContextElement();

    sm.clearCache();

    sm.validate();

    RepeatState expectedNewState1 = aRepeatState()
                                        .withName("Repeat " + runCommand1.getName())
                                        .withRepeatElementType(ContextElementType.INSTANCE)
                                        .withExecutionStrategy(ExecutionStrategy.PARALLEL)
                                        .withRepeatElementExpression(InstanceExpressionProcessor.DEFAULT_EXPRESSION)
                                        .withRepeatTransitionStateName(runCommand1.getName())
                                        .build();

    assertThat(sm.getStates()).hasSize(7);
    assertThat(sm.getNextStates("starting", TransitionType.SUCCESS)).hasSize(1).containsOnly(expectedNewState1);
    assertThat(sm.getNextStates(expectedNewState1.getName(), TransitionType.REPEAT))
        .hasSize(1)
        .containsOnly(runCommand1);
    assertThat(sm.getNextStates("command1", TransitionType.SUCCESS)).isNull();

    RepeatState expectedNewState2 = aRepeatState()
                                        .withName("Repeat " + runCommand3.getName())
                                        .withRepeatElementType(ContextElementType.INSTANCE)
                                        .withExecutionStrategy(ExecutionStrategy.PARALLEL)
                                        .withRepeatElementExpression(InstanceExpressionProcessor.DEFAULT_EXPRESSION)
                                        .withRepeatTransitionStateName(runCommand3.getName())
                                        .build();

    assertThat(sm.getNextStates("command2", TransitionType.SUCCESS)).hasSize(1).containsOnly(expectedNewState2);

    assertThat(sm.getNextStates("command3", TransitionType.SUCCESS)).isNull();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void stateMachineShouldBeThreadSafe() throws InterruptedException, ExecutionException {
    int numOfThreads = 10;
    int numIterationsPerThread = 10;
    StateMachine stateMachine = new StateMachine();
    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(1);
    List<Future<Boolean>> futures = new LinkedList<>();
    for (int i = 0; i < numOfThreads; i++) {
      futures.add(executor.submit(() -> {
        String name = generateUuid();
        latch.await();
        for (int j = 0; j < numIterationsPerThread; j++) {
          name = name + j;
          State state = new StateSync(name);
          stateMachine.addState(state);
          State state1 = stateMachine.getState(null, name);
          assertThat(state1).isNotNull();
          assertThat(state1.getName()).isEqualTo(name);
        }
        return Boolean.TRUE;
      }));
    }

    latch.countDown();
    executor.shutdown();
    executor.awaitTermination(1, TimeUnit.MINUTES);
    for (int i = 0; i < numOfThreads; i++) {
      assertThat(futures.get(i).get()).isEqualTo(Boolean.TRUE);
    }
    assertThat(stateMachine.getStates().size()).isEqualTo(numIterationsPerThread * numOfThreads);
  }
}
