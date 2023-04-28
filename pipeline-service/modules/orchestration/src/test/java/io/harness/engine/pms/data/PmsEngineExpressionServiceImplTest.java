/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;

import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;
import io.harness.utils.AmbianceTestUtils;
import io.harness.utils.DummyOrchestrationOutcome;
import io.harness.utils.DummySweepingOutput;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PmsEngineExpressionServiceImplTest extends OrchestrationTestBase {
  @Inject PmsEngineExpressionService pmsEngineExpressionService;
  @Inject PmsOutcomeService pmsOutcomeService;
  @Inject PmsSweepingOutputService pmsSweepingOutputService;
  @Inject PlanExecutionService planExecutionService;

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
  public void shouldTestRenderExpressionOutcome() {
    String resolvedExpression =
        pmsEngineExpressionService.renderExpression(ambiance, "<+dummyOutcome.test> == \"harness\"");
    assertThat(resolvedExpression).isNotNull();
    assertThat(resolvedExpression).isEqualTo("harness == \"harness\"");
  }

  @Test

  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRenderExpressionOutput() {
    String resolvedExpression =
        pmsEngineExpressionService.renderExpression(ambiance, "<+dummyOutput.test> == \"harness\"");
    assertThat(resolvedExpression).isNotNull();
    assertThat(resolvedExpression).isEqualTo("harness == \"harness\"");
  }

  @Test

  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestEvaluateExpression() {
    Object value = pmsEngineExpressionService.evaluateExpression(ambiance, "<+dummyOutcome.test> == \"harness\"");
    assertThat(value).isNotNull();
    assertThat(value).isEqualTo(RecastOrchestrationUtils.toJson(true));
  }
}
