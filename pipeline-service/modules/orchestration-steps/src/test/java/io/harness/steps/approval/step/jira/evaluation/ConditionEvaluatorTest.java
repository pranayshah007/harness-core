/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.jira.evaluation;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.evaluation.ConditionEvaluator.EqualsOperatorEvaluator;
import io.harness.steps.approval.step.evaluation.ConditionEvaluator.InOperatorEvaluator;
import io.harness.steps.approval.step.evaluation.ConditionEvaluator.NegateOperatorEvaluator;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
public class ConditionEvaluatorTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testEqualsOperatorEvaluator() {
    EqualsOperatorEvaluator evaluator = new EqualsOperatorEvaluator();
    assertThatThrownBy(() -> evaluator.evaluate(1000, "abc")).isNotNull();

    assertThat(evaluator.evaluate(null, null)).isTrue();
    assertThat(evaluator.evaluate(null, "abc")).isFalse();
    assertThat(evaluator.evaluate("abc", null)).isFalse();
    assertThat(evaluator.evaluate("abc", "abc")).isTrue();
    assertThat(evaluator.evaluate("abc", "def")).isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testInOperatorEvaluator() {
    InOperatorEvaluator evaluator = new InOperatorEvaluator();
    assertThatThrownBy(() -> evaluator.evaluate(1000, "abc")).isNotNull();

    assertThat(evaluator.evaluate(null, null)).isFalse();
    assertThat(evaluator.evaluate(null, "abc")).isFalse();
    assertThat(evaluator.evaluate("abc", null)).isFalse();
    assertThat(evaluator.evaluate("abc", "abc")).isTrue();
    assertThat(evaluator.evaluate("abc", "abc,")).isTrue();
    assertThat(evaluator.evaluate("abc", "abc,def")).isTrue();
    assertThat(evaluator.evaluate("abc", "\"abc,def\"")).isFalse();
    assertThat(evaluator.evaluate("abc", "\"abc,def\",  abc")).isTrue();
    assertThat(evaluator.evaluate("abc", "def")).isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testNegateOperatorEvaluator() {
    NegateOperatorEvaluator evaluator = new NegateOperatorEvaluator(new InOperatorEvaluator());
    assertThatThrownBy(() -> evaluator.evaluate(1000, "abc")).isNotNull();

    assertThat(evaluator.evaluate(null, null)).isTrue();
    assertThat(evaluator.evaluate(null, "abc")).isTrue();
    assertThat(evaluator.evaluate("abc", null)).isTrue();
    assertThat(evaluator.evaluate("abc", "abc")).isFalse();
    assertThat(evaluator.evaluate("abc", "abc,")).isFalse();
    assertThat(evaluator.evaluate("abc", "abc,def")).isFalse();
    assertThat(evaluator.evaluate("abc", "\"abc,def\"")).isTrue();
    assertThat(evaluator.evaluate("abc", "\"abc,def\",  abc")).isFalse();
    assertThat(evaluator.evaluate("abc", "def")).isTrue();
  }
}
