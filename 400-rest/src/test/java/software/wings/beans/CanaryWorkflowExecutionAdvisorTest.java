/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.ENABLE_EXPERIMENTAL_STEP_FAILURE_STRATEGIES;
import static io.harness.beans.FeatureName.LOG_APP_DEFAULTS;
import static io.harness.exception.FailureType.APPLICATION_ERROR;
import static io.harness.exception.FailureType.TIMEOUT_ERROR;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.LUCAS_SALES;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static software.wings.beans.CanaryOrchestrationWorkflow.CanaryOrchestrationWorkflowBuilder.aCanaryOrchestrationWorkflow;
import static software.wings.beans.PhaseStep.PhaseStepBuilder.aPhaseStep;
import static software.wings.beans.WorkflowPhase.WorkflowPhaseBuilder.aWorkflowPhase;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionInterruptType;
import io.harness.beans.RepairActionCode;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.api.PhaseElement;
import software.wings.beans.workflow.StepSkipStrategy;
import software.wings.service.impl.WorkflowExecutionServiceImpl;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionEvent;
import software.wings.sm.ExecutionEventAdvice;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.State;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParams.Builder;
import software.wings.sm.states.ShellScriptState;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.jexl3.JexlException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDC)
@RunWith(MockitoJUnitRunner.class)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class CanaryWorkflowExecutionAdvisorTest extends CategoryTest {
  private static final long VALID_TIMEOUT = 60000L;
  @Mock FeatureFlagService featureFlagService;
  @Mock WorkflowExecutionServiceImpl executionService;
  @InjectMocks CanaryWorkflowExecutionAdvisor canaryWorkflowExecutionAdvisor = new CanaryWorkflowExecutionAdvisor();

  @Before
  public void setUp() {
    when(featureFlagService.isEnabled(LOG_APP_DEFAULTS, ACCOUNT_ID)).thenReturn(false);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSelectTopMatchingStrategyWithNulls() {
    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(
                   null, null, "dummy", null, FailureStrategyLevel.WORKFLOW, false))
        .isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSelectTopMatchingStrategyWithNullStrategy() {
    final List<FailureStrategy> failureStrategies =
        asList(FailureStrategy.builder().executionScope(ExecutionScope.WORKFLOW).failureTypes(null).build(),
            FailureStrategy.builder().executionScope(ExecutionScope.WORKFLOW).failureTypes(null).build());

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(
                   failureStrategies, null, "dummy", null, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(0));

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(APPLICATION_ERROR), "dummy", null, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(0));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSelectTopMatchingStrategyWithNullError() {
    final List<FailureStrategy> failureStrategies = asList(FailureStrategy.builder()
                                                               .executionScope(ExecutionScope.WORKFLOW)
                                                               .failureTypes(asList(APPLICATION_ERROR))
                                                               .build(),
        FailureStrategy.builder().executionScope(ExecutionScope.WORKFLOW).failureTypes(null).build());

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(
                   failureStrategies, null, "dummy", null, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(0));

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.noneOf(FailureType.class), "dummy", null, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(0));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSelectTopMatchingStrategyFindMatching() {
    final List<FailureStrategy> failureStrategies = asList(FailureStrategy.builder()
                                                               .executionScope(ExecutionScope.WORKFLOW)
                                                               .failureTypes(asList(APPLICATION_ERROR))
                                                               .build(),
        FailureStrategy.builder()
            .executionScope(ExecutionScope.WORKFLOW)
            .failureTypes(asList(FailureType.CONNECTIVITY))
            .build());

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(FailureType.CONNECTIVITY), "dummy", null, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(1));

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(APPLICATION_ERROR), "dummy", null, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(0));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSelectTopMatchingStepWithNull() {
    final List<FailureStrategy> failureStrategies =
        asList(FailureStrategy.builder().specificSteps(null).build(), FailureStrategy.builder().build());

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(
                   failureStrategies, null, "dummy", null, FailureStrategyLevel.STEP, false))
        .isEqualTo(failureStrategies.get(0));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSelectTopMatchingStepWithNone() {
    final List<FailureStrategy> failureStrategies =
        asList(FailureStrategy.builder().specificSteps(asList("n/a")).build());

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(
                   failureStrategies, null, "dummy", null, FailureStrategyLevel.WORKFLOW, false))
        .isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSelectTopMatchingStep() {
    final List<FailureStrategy> failureStrategies =
        asList(FailureStrategy.builder().specificSteps(asList("dummy")).build());

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(
                   failureStrategies, null, "dummy", null, FailureStrategyLevel.STEP, false))
        .isEqualTo(failureStrategies.get(0));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSelectTopNonMatchingCombo() {
    final List<FailureStrategy> failureStrategies = asList(
        FailureStrategy.builder().specificSteps(asList("n/a")).build(),
        FailureStrategy.builder().failureTypes(asList(APPLICATION_ERROR)).build(),
        FailureStrategy.builder().specificSteps(asList("dummy")).failureTypes(asList(APPLICATION_ERROR)).build(),
        FailureStrategy.builder().specificSteps(asList("n/a")).failureTypes(asList(FailureType.CONNECTIVITY)).build());

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(FailureType.CONNECTIVITY), "dummy", null, FailureStrategyLevel.WORKFLOW, false))
        .isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSelectTopMatchingCombo() {
    final List<FailureStrategy> failureStrategies = asList(
        FailureStrategy.builder().specificSteps(asList("n/a")).build(),
        FailureStrategy.builder().failureTypes(asList(APPLICATION_ERROR)).build(),
        FailureStrategy.builder()
            .executionScope(ExecutionScope.WORKFLOW)
            .specificSteps(asList("dummy"))
            .failureTypes(asList(APPLICATION_ERROR))
            .build(),
        FailureStrategy.builder().specificSteps(asList("n/a")).failureTypes(asList(FailureType.CONNECTIVITY)).build(),
        FailureStrategy.builder()
            .executionScope(ExecutionScope.WORKFLOW)
            .specificSteps(asList("dummy"))
            .failureTypes(asList(FailureType.CONNECTIVITY))
            .build());

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(FailureType.CONNECTIVITY), "dummy", null, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(4));
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldReturnTrueWhenExecutionHostsPresent() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams workflowStandardParams =
        Builder.aWorkflowStandardParams().withExecutionHosts(Collections.singletonList("host1")).build();
    doReturn(workflowStandardParams).when(context).getContextElement(ContextElementType.STANDARD);

    boolean executionHostsPresent = canaryWorkflowExecutionAdvisor.isExecutionHostsPresent(context);

    assertThat(executionHostsPresent).isTrue();
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldReturnFalseWhenExecutionHostsNotPresent() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams workflowStandardParams = Builder.aWorkflowStandardParams().withExecutionHosts(null).build();
    doReturn(workflowStandardParams).when(context).getContextElement(ContextElementType.STANDARD);

    boolean executionHostsPresent = canaryWorkflowExecutionAdvisor.isExecutionHostsPresent(context);

    assertThat(executionHostsPresent).isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFindPhaseStep() {
    State state = new ShellScriptState("script-tmp");

    // Args are invalid/null.
    assertThat(CanaryWorkflowExecutionAdvisor.findPhaseStep(null, null, null)).isNull();
    assertThat(CanaryWorkflowExecutionAdvisor.findPhaseStep(aCanaryOrchestrationWorkflow().build(), null, state))
        .isNull();

    // State parent id is null.
    assertThat(CanaryWorkflowExecutionAdvisor.findPhaseStep(
                   aCanaryOrchestrationWorkflow().build(), PhaseElement.builder().build(), state))
        .isNull();

    // Unknown phase.
    String parentId = "parentId";
    state.setParentId(parentId);
    CanaryOrchestrationWorkflow orchestrationWorkflow =
        aCanaryOrchestrationWorkflow()
            .withPreDeploymentSteps(aPhaseStep(PhaseStepType.PRE_DEPLOYMENT).build())
            .withPostDeploymentSteps(aPhaseStep(PhaseStepType.POST_DEPLOYMENT).build())
            .withWorkflowPhaseIdMap(new HashMap<>())
            .build();
    PhaseElement phaseElement = PhaseElement.builder().uuid(parentId).build();
    assertThat(CanaryWorkflowExecutionAdvisor.findPhaseStep(orchestrationWorkflow, phaseElement, state)).isNull();

    // Phase doesn't contains any phase step.
    WorkflowPhase phase = aWorkflowPhase().build();
    orchestrationWorkflow.setWorkflowPhaseIdMap(Collections.singletonMap(parentId, phase));
    assertThat(CanaryWorkflowExecutionAdvisor.findPhaseStep(orchestrationWorkflow, phaseElement, state)).isNull();

    // Phase doesn't contain required phase step.
    PhaseStep phaseStep = aPhaseStep(PhaseStepType.ENABLE_SERVICE).build();
    phaseStep.setUuid(parentId + "_tmp");
    phase.setPhaseSteps(Collections.singletonList(phaseStep));
    assertThat(CanaryWorkflowExecutionAdvisor.findPhaseStep(orchestrationWorkflow, phaseElement, state)).isNull();

    phaseStep.setUuid(parentId);
    phase.setPhaseSteps(Collections.singletonList(phaseStep));
    assertThat(CanaryWorkflowExecutionAdvisor.findPhaseStep(orchestrationWorkflow, phaseElement, state))
        .isEqualTo(phaseStep);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testShouldSkipStep() {
    // Args are invalid/null.
    assertThat(CanaryWorkflowExecutionAdvisor.shouldSkipStep(null, null, null, featureFlagService)).isNull();
    assertThat(CanaryWorkflowExecutionAdvisor.shouldSkipStep(
                   null, aPhaseStep(PhaseStepType.ENABLE_SERVICE).build(), null, featureFlagService))
        .isNull();

    String expr = "${expr.name} == \"qa\"";
    String stateId = "stateId";
    State state = new ShellScriptState("script-tmp");
    state.setId(stateId);

    // No step skip strategy found for step id.
    PhaseStep phaseStep =
        aPhaseStep(PhaseStepType.ENABLE_SERVICE)
            .withStepSkipStrategies(Collections.singletonList(new StepSkipStrategy(
                StepSkipStrategy.Scope.SPECIFIC_STEPS, Collections.singletonList(stateId + "_tmp"), expr)))
            .build();
    assertThat(CanaryWorkflowExecutionAdvisor.shouldSkipStep(null, phaseStep, state, featureFlagService)).isNull();

    ExecutionContextImpl context = spy(new ExecutionContextImpl(null));
    doReturn("renderedExp").when(context).renderExpression(anyString());

    // Assertion expression evaluating to false.
    doReturn(ACCOUNT_ID).when(context).getAccountId();
    doReturn(false).when(context).evaluateExpression(any());
    assertThat(CanaryWorkflowExecutionAdvisor.shouldSkipStep(context, phaseStep, state, featureFlagService)).isNull();

    // Assertion expression evaluating to true.
    phaseStep.setStepSkipStrategies(asList(
        new StepSkipStrategy(StepSkipStrategy.Scope.SPECIFIC_STEPS, Collections.singletonList(stateId + "_tmp"), expr),
        new StepSkipStrategy(StepSkipStrategy.Scope.SPECIFIC_STEPS, Collections.singletonList(stateId), expr)));
    doReturn(true).when(context).evaluateExpression(any());
    ExecutionEventAdvice advice =
        CanaryWorkflowExecutionAdvisor.shouldSkipStep(context, phaseStep, state, featureFlagService);
    assertThat(advice).isNotNull();
    assertThat(advice.isSkipState()).isTrue();
    assertThat(advice.getSkipExpression()).isEqualTo(expr);
    assertThat(advice.getSkipError()).isNull();

    doThrow(new InvalidRequestException("error evaluating expression")).when(context).evaluateExpression(any());
    advice = CanaryWorkflowExecutionAdvisor.shouldSkipStep(context, phaseStep, state, featureFlagService);
    assertThat(advice).isNotNull();
    assertThat(advice.isSkipState()).isTrue();
    assertThat(advice.getSkipExpression()).isEqualTo(expr);
    assertThat(advice.getSkipError()).isNotBlank();

    // When Secret script output variables are used in Skip Assertion. This exception will be thrown. This block is to
    // test Error Message
    doThrow(new JexlException.Variable(null, "sweepingOutputSecrets", true)).when(context).evaluateExpression(any());
    advice = CanaryWorkflowExecutionAdvisor.shouldSkipStep(context, phaseStep, state, featureFlagService);
    assertThat(advice).isNotNull();
    assertThat(advice.isSkipState()).isTrue();
    assertThat(advice.getSkipExpression()).isEqualTo(expr);
    assertThat(advice.getSkipError())
        .contains("Secret Variables defined in Script output of shell scripts cannot be used in skip assertions");
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetDefaultTimeoutWhenIssuingAdviceForManualIntervention() {
    FailureStrategy failureStrategy = FailureStrategy.builder()
                                          .repairActionCode(RepairActionCode.MANUAL_INTERVENTION)
                                          .actionAfterTimeout(ExecutionInterruptType.END_EXECUTION)
                                          .build();
    ExecutionEventAdvice executionEventAdvice =
        canaryWorkflowExecutionAdvisor.issueManualInterventionAdvice(failureStrategy, Collections.emptyMap());

    assertThat(executionEventAdvice.getTimeout()).isEqualTo(CanaryWorkflowExecutionAdvisor.DEFAULT_TIMEOUT);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetDefaultActionAfterTimeoutWhenIssuingAdviceForManualIntervention() {
    FailureStrategy failureStrategy = FailureStrategy.builder()
                                          .manualInterventionTimeout(VALID_TIMEOUT)
                                          .repairActionCode(RepairActionCode.MANUAL_INTERVENTION)
                                          .build();
    ExecutionEventAdvice executionEventAdvice =
        canaryWorkflowExecutionAdvisor.issueManualInterventionAdvice(failureStrategy, Collections.emptyMap());

    assertThat(executionEventAdvice.getActionAfterManualInterventionTimeout())
        .isEqualTo(CanaryWorkflowExecutionAdvisor.DEFAULT_ACTION_AFTER_TIMEOUT);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetProperlyDefinedTimeoutAndActionWhenIssuingAdviceForManualIntervention() {
    FailureStrategy failureStrategy = FailureStrategy.builder()
                                          .repairActionCode(RepairActionCode.MANUAL_INTERVENTION)
                                          .actionAfterTimeout(ExecutionInterruptType.MARK_SUCCESS)
                                          .manualInterventionTimeout(VALID_TIMEOUT)
                                          .build();
    ExecutionEventAdvice executionEventAdvice =
        canaryWorkflowExecutionAdvisor.issueManualInterventionAdvice(failureStrategy, Collections.emptyMap());

    assertThat(executionEventAdvice.getTimeout()).isEqualTo(VALID_TIMEOUT);
    assertThat(executionEventAdvice.getActionAfterManualInterventionTimeout())
        .isEqualTo(ExecutionInterruptType.MARK_SUCCESS);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetProperlyDefinedTimeoutAndActionForManualInterventionAfterRetry() {
    FailureStrategy failureStrategy = FailureStrategy.builder()
                                          .repairActionCodeAfterRetry(RepairActionCode.MANUAL_INTERVENTION)
                                          .actionAfterTimeout(ExecutionInterruptType.MARK_SUCCESS)
                                          .manualInterventionTimeout(VALID_TIMEOUT)
                                          .build();
    FailureStrategy failureStrategyAfterRetry =
        canaryWorkflowExecutionAdvisor.getFailureStrategyAfterRetry(failureStrategy);

    assertThat(failureStrategyAfterRetry.getManualInterventionTimeout()).isEqualTo(VALID_TIMEOUT);
    assertThat(failureStrategyAfterRetry.getActionAfterTimeout()).isEqualTo(ExecutionInterruptType.MARK_SUCCESS);
    assertThat(failureStrategyAfterRetry.getRepairActionCode()).isEqualTo(RepairActionCode.MANUAL_INTERVENTION);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetDefaultTimeoutForManualInterventionAfterRetry() {
    FailureStrategy failureStrategy = FailureStrategy.builder()
                                          .repairActionCodeAfterRetry(RepairActionCode.MANUAL_INTERVENTION)
                                          .actionAfterTimeout(ExecutionInterruptType.MARK_SUCCESS)
                                          .build();
    FailureStrategy failureStrategyAfterRetry =
        canaryWorkflowExecutionAdvisor.getFailureStrategyAfterRetry(failureStrategy);

    assertThat(failureStrategyAfterRetry.getManualInterventionTimeout())
        .isEqualTo(CanaryWorkflowExecutionAdvisor.DEFAULT_TIMEOUT);
    assertThat(failureStrategyAfterRetry.getActionAfterTimeout()).isEqualTo(ExecutionInterruptType.MARK_SUCCESS);
    assertThat(failureStrategyAfterRetry.getRepairActionCode()).isEqualTo(RepairActionCode.MANUAL_INTERVENTION);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldSetDefaultActionAfterTimeoutForManualInterventionAfterRetry() {
    FailureStrategy failureStrategy = FailureStrategy.builder()
                                          .repairActionCodeAfterRetry(RepairActionCode.MANUAL_INTERVENTION)
                                          .manualInterventionTimeout(VALID_TIMEOUT)
                                          .build();
    FailureStrategy failureStrategyAfterRetry =
        canaryWorkflowExecutionAdvisor.getFailureStrategyAfterRetry(failureStrategy);

    assertThat(failureStrategyAfterRetry.getManualInterventionTimeout()).isEqualTo(VALID_TIMEOUT);
    assertThat(failureStrategyAfterRetry.getActionAfterTimeout())
        .isEqualTo(CanaryWorkflowExecutionAdvisor.DEFAULT_ACTION_AFTER_TIMEOUT);
    assertThat(failureStrategyAfterRetry.getRepairActionCode()).isEqualTo(RepairActionCode.MANUAL_INTERVENTION);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testSelectWFLevelStrategyWithPhaseScopeOnNonPhaseStep() {
    final List<FailureStrategy> failureStrategies = asList(FailureStrategy.builder()
                                                               .failureTypes(asList(APPLICATION_ERROR))
                                                               .executionScope(ExecutionScope.WORKFLOW_PHASE)
                                                               .build(),
        FailureStrategy.builder().failureTypes(null).build());

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(APPLICATION_ERROR), "stateName", null, FailureStrategyLevel.WORKFLOW, false))
        .isNull();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testSelectWFLevelStrategyWithPhaseScopeOnPhaseStep() {
    final List<FailureStrategy> failureStrategies = asList(FailureStrategy.builder()
                                                               .failureTypes(asList(APPLICATION_ERROR))
                                                               .executionScope(ExecutionScope.WORKFLOW_PHASE)
                                                               .build(),
        FailureStrategy.builder().failureTypes(null).build());

    final PhaseElement phaseElement = PhaseElement.builder().phaseName("phaseName").build();

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(APPLICATION_ERROR), "stateName", phaseElement, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(0));
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldRenderSkipConditionExpression() {
    String expr = "${expr.name} == \"someValue\"";
    String stateId = "stateId";
    State state = new ShellScriptState(STATE_NAME);
    state.setId(stateId);
    PhaseStep phaseStep = aPhaseStep(PhaseStepType.ENABLE_SERVICE)
                              .withStepSkipStrategies(Collections.singletonList(new StepSkipStrategy(
                                  StepSkipStrategy.Scope.SPECIFIC_STEPS, Collections.singletonList(stateId), expr)))
                              .build();
    ExecutionContextImpl context = spy(new ExecutionContextImpl(null));
    doReturn(ACCOUNT_ID).when(context).getAccountId();
    CanaryWorkflowExecutionAdvisor.shouldSkipStep(context, phaseStep, state, featureFlagService);

    verify(context).renderExpression(expr);
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testSelectWFLevelStrategyWithWorkflowScopeOnPhaseStep() {
    final List<FailureStrategy> failureStrategies = asList(FailureStrategy.builder()
                                                               .failureTypes(asList(APPLICATION_ERROR))
                                                               .executionScope(ExecutionScope.WORKFLOW)
                                                               .build(),
        FailureStrategy.builder().failureTypes(null).build());

    final PhaseElement phaseElement = PhaseElement.builder().phaseName("phaseName").build();

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(APPLICATION_ERROR), "stateName", phaseElement, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(0));
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testSelectWFLevelStrategyWithWorkflowScopeOnNonPhaseStep() {
    final List<FailureStrategy> failureStrategies = asList(FailureStrategy.builder()
                                                               .failureTypes(asList(APPLICATION_ERROR))
                                                               .executionScope(ExecutionScope.WORKFLOW)
                                                               .build(),
        FailureStrategy.builder().failureTypes(null).build());

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(APPLICATION_ERROR), "stateName", null, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(0));
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testSelectWFLevelStrategyFromMultipleWFLevelStrategies() {
    final List<FailureStrategy> failureStrategies = asList(FailureStrategy.builder()
                                                               .failureTypes(asList(APPLICATION_ERROR))
                                                               .executionScope(ExecutionScope.WORKFLOW_PHASE)
                                                               .build(),
        FailureStrategy.builder()
            .failureTypes(asList(APPLICATION_ERROR))
            .executionScope(ExecutionScope.WORKFLOW)
            .build());

    final PhaseElement phaseElement = PhaseElement.builder().phaseName("phaseName").build();

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(APPLICATION_ERROR), "stateName", phaseElement, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(0));

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(APPLICATION_ERROR), "stateName", null, FailureStrategyLevel.WORKFLOW, false))
        .isEqualTo(failureStrategies.get(1));
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testNoMatchingWFLevelStrategyOnPhaseStep() {
    final List<FailureStrategy> failureStrategies =
        asList(FailureStrategy.builder().failureTypes(asList(APPLICATION_ERROR)).build(),
            FailureStrategy.builder().failureTypes(null).build());

    final PhaseElement phaseElement = PhaseElement.builder().phaseName("phaseName").build();

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(APPLICATION_ERROR), "stateName", phaseElement, FailureStrategyLevel.WORKFLOW, false))
        .isNull();
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void testNoMatchingWFLevelStrategyOnNonPhaseStep() {
    final List<FailureStrategy> failureStrategies =
        asList(FailureStrategy.builder().failureTypes(asList(APPLICATION_ERROR)).build(),
            FailureStrategy.builder().failureTypes(null).build());

    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(failureStrategies,
                   EnumSet.of(APPLICATION_ERROR), "stateName", null, FailureStrategyLevel.WORKFLOW, false))
        .isNull();
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnFailureStrategyContainingTimeoutFailureType() {
    List<FailureStrategy> failureStrategies =
        asList(FailureStrategy.builder().failureTypes(Collections.singletonList(APPLICATION_ERROR)).build(),
            FailureStrategy.builder().failureTypes(Collections.singletonList(TIMEOUT_ERROR)).build());
    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(
                   failureStrategies, EnumSet.of(TIMEOUT_ERROR), "stateName", null, FailureStrategyLevel.STEP, false))
        .isEqualTo(failureStrategies.get(1));
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldReturnNonTimeoutStrategy() {
    List<FailureStrategy> failureStrategies =
        asList(FailureStrategy.builder().failureTypes(Collections.singletonList(APPLICATION_ERROR)).build(),
            FailureStrategy.builder().failureTypes(Collections.singletonList(TIMEOUT_ERROR)).build());
    assertThat(CanaryWorkflowExecutionAdvisor.selectTopMatchingStrategy(
                   failureStrategies, null, "stateName", null, FailureStrategyLevel.STEP, false))
        .isEqualTo(failureStrategies.get(0));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldReturnCorrectStrategyForOnDemandRollback() {
    FailureStrategy failureStrategy = FailureStrategy.builder()
                                          .failureTypes(Collections.singletonList(APPLICATION_ERROR))
                                          .repairActionCode(RepairActionCode.END_EXECUTION)
                                          .build();
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance().build();
    ExecutionEvent executionEvent =
        ExecutionEvent.builder().context(new ExecutionContextImpl(stateExecutionInstance)).build();

    ExecutionEventAdvice advice = canaryWorkflowExecutionAdvisor.computeExecutionEventAdvice(
        null, failureStrategy, executionEvent, null, null, null);
    assertThat(advice.getExecutionInterruptType()).isEqualTo(ExecutionInterruptType.END_EXECUTION);
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldReturnCorrectStrategyInStep_RollbackWorkflow() {
    doReturn(true).when(featureFlagService).isEnabled(ENABLE_EXPERIMENTAL_STEP_FAILURE_STRATEGIES, "accountId");

    FailureStrategy failureStrategy = FailureStrategy.builder()
                                          .failureTypes(Collections.singletonList(APPLICATION_ERROR))
                                          .repairActionCode(RepairActionCode.ROLLBACK_WORKFLOW)
                                          .build();
    StateExecutionInstance stateExecutionInstance = aStateExecutionInstance().accountId("accountId").build();
    ExecutionEvent executionEvent =
        ExecutionEvent.builder().context(new ExecutionContextImpl(stateExecutionInstance)).build();

    ExecutionEventAdvice advice = canaryWorkflowExecutionAdvisor.computeExecutionEventAdvice(
        null, failureStrategy, executionEvent, null, stateExecutionInstance, null);
    verify(executionService).triggerExecutionInterrupt(any(ExecutionInterrupt.class));
    assertThat(advice.getExecutionInterruptType()).isEqualTo(ExecutionInterruptType.ROLLBACK);
  }
}
