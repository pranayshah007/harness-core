/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StrategyExpressionEvaluatorTest extends CategoryTest {
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testResolve() throws IOException {
    Map<String, String> combinations = Map.of("config", "{\"a\":\"AA\",\"b\":\"BB\"}");
    EngineExpressionEvaluator evaluator = new StrategyExpressionEvaluator(combinations, 3, 5, "10", null);

    assertThat(evaluator.resolve("<+matrix.config.a>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("AA");
    assertThat(evaluator.resolve("<+matrix.config.b>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("BB");

    assertThat(evaluator.resolve("<+strategy.matrix.config.a>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo("AA");
    assertThat(evaluator.resolve("<+strategy.matrix.config.b>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo("BB");

    assertThat(evaluator.resolve("<+matrix.config.c>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("null");

    assertThat(evaluator.resolve("<+step.iteration>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("3");
    assertThat(evaluator.resolve("<+step.iterations>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("5");
    assertThat(evaluator.resolve("<+step.totalIterations>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("5");

    assertThat(evaluator.resolve("<+strategy.step.iteration>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo("3");
    assertThat(evaluator.resolve("<+strategy.step.iterations>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo("5");
    assertThat(evaluator.resolve("<+strategy.step.totalIterations>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo("5");

    assertThat(evaluator.resolve("<+strategy.iteration>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("3");
    assertThat(evaluator.resolve("<+strategy.iterations>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("5");

    assertThat(evaluator.resolve("<+strategy.repeat.item>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("10");
    assertThat(evaluator.resolve("<+repeat.item>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("10");

    combinations = Map.of("a", "AAA", "b", "BBB");
    evaluator = new StrategyExpressionEvaluator(combinations, 3, 5, "10", null);

    assertThat(evaluator.resolve("<+matrix.a>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("AAA");
    assertThat(evaluator.resolve("<+matrix.b>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("BBB");
    assertThat(evaluator.resolve("<+strategy.matrix.a>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("AAA");
    assertThat(evaluator.resolve("<+strategy.matrix.b>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("BBB");
    assertThat(evaluator.resolve("<+matrix.c>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("null");
  }
}
