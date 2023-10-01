/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml.validation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PmsCommonsTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.InputSetValidatorType;
import io.harness.category.element.UnitTests;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.expression.EngineExpressionEvaluatorResolver;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class InputSetValidatorFactoryTest extends PmsCommonsTestBase {
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;
  private final EngineExpressionEvaluator expressionEvaluator = new EngineExpressionEvaluator(null);

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testAllowedValuesValidator() {
    InputSetValidator validator = new InputSetValidator(InputSetValidatorType.ALLOWED_VALUES, "");
    RuntimeValidator runtimeValidator = inputSetValidatorFactory.obtainValidator(validator,
        new EngineExpressionEvaluatorResolver(expressionEvaluator), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    assertThat(runtimeValidator.isValidValue(null, "a,b,c").isValid()).isFalse();
    assertThat(runtimeValidator.isValidValue("a", "a,b,c").isValid()).isTrue();
    assertThat(runtimeValidator.isValidValue("ab", "a,b,c").isValid()).isFalse();
    assertThat(runtimeValidator.isValidValue("a", "jexl(\"a,b\")").isValid()).isTrue();
    assertThat(runtimeValidator.isValidValue("ab", "jexl(\"a,b\")").isValid()).isFalse();
    assertThat(runtimeValidator.isValidValue(Collections.emptyList(), "jexl(\"a,b\")").isValid()).isFalse();
    assertThat(runtimeValidator.isValidValue(ImmutableList.of(Collections.emptyList(), "b"), "jexl(\"a,b\")").isValid())
        .isFalse();
    assertThat(runtimeValidator.isValidValue(ImmutableList.of("a", "b"), "jexl(\"a,b\")").isValid()).isTrue();
    assertThat(runtimeValidator.isValidValue(ImmutableList.of("a", "ab"), "jexl(\"a,b\")").isValid()).isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testRegexValidator() {
    InputSetValidator validator = new InputSetValidator(InputSetValidatorType.REGEX, "");
    RuntimeValidator runtimeValidator = inputSetValidatorFactory.obtainValidator(validator,
        new EngineExpressionEvaluatorResolver(expressionEvaluator), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    assertThat(runtimeValidator.isValidValue(null, "abc*").isValid()).isFalse();
    assertThat(runtimeValidator.isValidValue("abc", "abc*").isValid()).isTrue();
    assertThat(runtimeValidator.isValidValue("a", "abc*").isValid()).isFalse();
    assertThat(runtimeValidator.isValidValue(Collections.emptyList(), "abc*").isValid()).isFalse();
  }
}
