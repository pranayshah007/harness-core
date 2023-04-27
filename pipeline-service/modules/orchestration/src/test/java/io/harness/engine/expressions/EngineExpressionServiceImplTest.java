/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.execution.PlanExecution;
import io.harness.plan.ExpressionModeMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.utils.AmbianceTestUtils;
import io.harness.utils.DummyOrchestrationOutcome;
import io.harness.utils.DummySweepingOutput;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class EngineExpressionServiceImplTest extends OrchestrationTestBase {
  @Inject EngineExpressionService engineExpressionService;
  @Inject PmsOutcomeService pmsOutcomeService;
  @Inject PmsSweepingOutputService pmsSweepingOutputService;
  @Inject PlanExecutionService planExecutionService;

  @InjectMocks EngineExpressionServiceImpl engineExpressionServiceImpl;
  @Mock PmsEngineExpressionService pmsEngineExpressionService;

  private static final String OUTCOME_NAME = "dummyOutcome";
  private static final String OUTPUT_NAME = "dummyOutput";

  private Ambiance ambiance;

  @Before
  public void setup() {
    ambiance = AmbianceTestUtils.buildAmbiance();
    planExecutionService.save(PlanExecution.builder().uuid(ambiance.getPlanExecutionId()).build());
    pmsOutcomeService.consume(ambiance, OUTCOME_NAME,
        RecastOrchestrationUtils.toJson(DummyOrchestrationOutcome.builder().test("harness").build()), null);
    pmsSweepingOutputService.consume(ambiance, OUTPUT_NAME,
        RecastOrchestrationUtils.toJson(DummySweepingOutput.builder().test("harness").build()), null);
  }

  @Test

  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  @Ignore("Move to PmsServiceImpl Test")
  public void shouldTestRenderExpressionOutcome() {
    String resolvedExpression =
        engineExpressionService.renderExpression(ambiance, "${dummyOutcome.test} == \"harness\"");
    assertThat(resolvedExpression).isNotNull();
    assertThat(resolvedExpression).isEqualTo("harness == \"harness\"");
  }

  @Test

  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  @Ignore("Move to PmsServiceImpl Test")
  public void shouldTestRenderExpressionOutput() {
    String resolvedExpression =
        engineExpressionService.renderExpression(ambiance, "${dummyOutput.test} == \"harness\"");
    assertThat(resolvedExpression).isNotNull();
    assertThat(resolvedExpression).isEqualTo("harness == \"harness\"");
  }

  @Test

  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  @Ignore("Move to PmsServiceImpl Test")
  public void shouldTestEvaluateExpression() {
    Object value = engineExpressionService.evaluateExpression(ambiance, "${dummyOutcome.test} == \"harness\"");
    assertThat(value).isNotNull();
    assertThat(value).isEqualTo(true);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testRenderExpression() {
    doReturn("this value")
        .when(pmsEngineExpressionService)
        .renderExpression(Ambiance.getDefaultInstance(), "thisExpression", false);
    assertThat(engineExpressionServiceImpl.renderExpression(Ambiance.getDefaultInstance(), "thisExpression", false))
        .isEqualTo("this value");
    verify(pmsEngineExpressionService, times(1))
        .renderExpression(Ambiance.getDefaultInstance(), "thisExpression", false);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluateExpression() {
    doReturn("{\"this\" : \"withoutRecastKey\"}")
        .when(pmsEngineExpressionService)
        .evaluateExpression(Ambiance.getDefaultInstance(), "thisExpression");
    assertThat(engineExpressionServiceImpl.evaluateExpression(Ambiance.getDefaultInstance(), "thisExpression"))
        .isEqualTo("{\"this\" : \"withoutRecastKey\"}");
    verify(pmsEngineExpressionService, times(1)).evaluateExpression(Ambiance.getDefaultInstance(), "thisExpression");

    doReturn("{\"this\" : \"withRecastKey\", \"__recast\" : \"thisID\"}")
        .when(pmsEngineExpressionService)
        .evaluateExpression(Ambiance.getDefaultInstance(), "thisOtherExpression");
    assertThat(engineExpressionServiceImpl.evaluateExpression(Ambiance.getDefaultInstance(), "thisOtherExpression"))
        .isEqualTo("{\"this\" : \"withRecastKey\", \"__recast\" : \"thisID\"}");
    verify(pmsEngineExpressionService, times(1))
        .evaluateExpression(Ambiance.getDefaultInstance(), "thisOtherExpression");

    ExpressionMode expressionMode = ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED;
    doReturn("{\"this\" : \"withRecastKey\", \"__recast\" : \"thisID\"}")
        .when(pmsEngineExpressionService)
        .evaluateExpression(Ambiance.getDefaultInstance(), "expressionWithMode",
            ExpressionModeMapper.fromExpressionModeProto(expressionMode));
    assertThat(engineExpressionServiceImpl.evaluateExpression(
                   Ambiance.getDefaultInstance(), "expressionWithMode", expressionMode))
        .isEqualTo("{\"this\" : \"withRecastKey\", \"__recast\" : \"thisID\"}");
    // Passing the expression mode, So evaluateExpression will be invoked with expressionMode parameter.
    verify(pmsEngineExpressionService, times(0))
        .evaluateExpression(Ambiance.getDefaultInstance(), "expressionWithMode");
    verify(pmsEngineExpressionService, times(1))
        .evaluateExpression(Ambiance.getDefaultInstance(), "expressionWithMode",
            ExpressionModeMapper.fromExpressionModeProto(expressionMode));
  }
}
