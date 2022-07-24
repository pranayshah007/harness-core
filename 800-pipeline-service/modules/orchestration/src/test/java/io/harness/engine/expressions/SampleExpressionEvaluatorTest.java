/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;
import io.harness.utils.AmbianceTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SampleExpressionEvaluatorTest extends CategoryTest {
  private Ambiance ambiance;

  @Before
  public void setup() {
    ambiance = AmbianceTestUtils.buildAmbiance();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testWithSupportStringUtils() {
    ExpressionEvaluatorProvider expressionEvaluatorProvider = new SampleExpressionEvaluatorProvider(true);
    EngineExpressionEvaluator expressionEvaluatorT = expressionEvaluatorProvider.get(null, ambiance, null, false);
    EngineExpressionEvaluator expressionEvaluator = expressionEvaluatorProvider.get(null, ambiance, null, false);

    validateEvaluateExpression(expressionEvaluator, "<+stringUtils.toUpper(\"Abc\")>", "ABC");
    validateEvaluateExpression(expressionEvaluator, "<+stringUtils.toLower(\"Abc\")>", "abc");
    validateEvaluateExpression(expressionEvaluator, "<+string.toUpper(\"Abc\")>", "ABC");
    validateEvaluateExpression(expressionEvaluator, "<+string.toLower(\"Abc\")>", "abc");
    validateEvaluateExpression(expressionEvaluator, "<+toUpper(\"Abc\")>", "ABC");
    validateEvaluateExpression(expressionEvaluator, "<+toLower(\"Abc\")>", "abc");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testWithoutSupportStringUtils() {
    ExpressionEvaluatorProvider expressionEvaluatorProvider = new SampleExpressionEvaluatorProvider(false);
    EngineExpressionEvaluator expressionEvaluator = expressionEvaluatorProvider.get(null, ambiance, null, false);

    assertThat(expressionEvaluator.evaluateExpression("<+stringUtils.toUpper(\"Abc\")>")).isNull();
  }

  private void validateEvaluateExpression(
      EngineExpressionEvaluator expressionEvaluator, String expression, String expected) {
    Object value = expressionEvaluator.evaluateExpression(expression);
    assertThat(value).isNotNull();
    if (expected == null) {
      assertThat(value).isInstanceOf(String.class);
      assertThat((String) value).isNotBlank();
    } else {
      assertThat(value).isEqualTo(expected);
    }
  }
}
