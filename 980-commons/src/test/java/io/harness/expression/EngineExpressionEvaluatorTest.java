/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.HintException;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.expression.common.ExpressionMode;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class EngineExpressionEvaluatorTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testWithoutHarnessExpressions() {
    EngineExpressionEvaluator evaluator = prepareEngineExpressionEvaluator(null);
    assertThat(evaluator.renderExpression(null)).isEqualTo(null);
    assertThat(evaluator.renderExpression("")).isEqualTo("");
    assertThat(evaluator.renderExpression("true")).isEqualTo("true");
    assertThat(evaluator.renderExpression("true == false")).isEqualTo("true == false");
    assertThat(evaluator.evaluateExpression(null)).isNull();
    assertThat(evaluator.evaluateExpression("")).isEqualTo(null);
    assertThat(evaluator.evaluateExpression("true")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("true == false")).isEqualTo(false);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testWithoutExpressions() {
    DummyB dummyB1 = DummyB.builder()
                         .cVal1(DummyC.builder().strVal("c11").build())
                         .cVal2(DummyField.createValueField(DummyC.builder().strVal("c12").build()))
                         .strVal1("b11")
                         .strVal2(DummyField.createValueField("b12"))
                         .intVal1(11)
                         .intVal2(DummyField.createValueField(12))
                         .build();
    DummyB dummyB2 = DummyB.builder()
                         .cVal1(DummyC.builder().strVal("c21").build())
                         .cVal2(DummyField.createValueField(DummyC.builder().strVal("c22").build()))
                         .strVal1("b21")
                         .strVal2(DummyField.createValueField("b22"))
                         .intVal1(21)
                         .intVal2(DummyField.createValueField(22))
                         .build();
    DummyA dummyA = DummyA.builder()
                        .bVal1(dummyB1)
                        .bVal2(DummyField.createValueField(dummyB2))
                        .strVal1("a1")
                        .strVal2(DummyField.createValueField("a2"))
                        .build();
    EngineExpressionEvaluator evaluator =
        prepareEngineExpressionEvaluator(ImmutableMap.of("obj", DummyField.createValueField(dummyA)));

    validateExpression(evaluator, "bVal1.cVal1.strVal", "c11");
    validateExpression(evaluator, "bVal1.cVal2.strVal", "c12");
    validateExpression(evaluator, "bVal1.strVal1", "b11");
    validateExpression(evaluator, "bVal1.strVal2", "b12");
    validateExpression(evaluator, "bVal1.intVal1", 11);
    validateExpression(evaluator, "bVal1.intVal2", 12);
    validateExpression(evaluator, "bVal2.cVal1.strVal", "c21");
    validateExpression(evaluator, "bVal2.cVal2.strVal", "c22");
    validateExpression(evaluator, "bVal2.strVal1", "b21");
    validateExpression(evaluator, "bVal2.strVal2", "b22");
    validateExpression(evaluator, "bVal2.intVal1", 21);
    validateExpression(evaluator, "bVal2.intVal2", 22);
    validateExpression(evaluator, "strVal1", "a1");
    validateExpression(evaluator, "strVal2", "a2");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testWithExpressions() {
    DummyB dummyB1 = DummyB.builder()
                         .cVal1(DummyC.builder().strVal("c11").build())
                         .cVal2(DummyField.createExpressionField("<+c12>"))
                         .strVal1("b11")
                         .strVal2(DummyField.createValueField("b12"))
                         .intVal1(11)
                         .intVal2(DummyField.createValueField(12))
                         .build();
    DummyB dummyB2 = DummyB.builder()
                         .cVal1(DummyC.builder().strVal("c21").build())
                         .cVal2(DummyField.createExpressionField("<+c22>"))
                         .strVal1("<+b21>")
                         .strVal2(DummyField.createExpressionField("<+b22>"))
                         .intVal1(21)
                         .intVal2(DummyField.createExpressionField("<+i22>"))
                         .build();
    DummyA dummyA = DummyA.builder()
                        .bVal1(dummyB1)
                        .bVal2(DummyField.createValueField(dummyB2))
                        .strVal1("a1")
                        .strVal2(DummyField.createValueField("a2"))
                        .build();
    EngineExpressionEvaluator evaluator =
        prepareEngineExpressionEvaluator(new ImmutableMap.Builder<String, Object>()
                                             .put("obj", DummyField.createValueField(dummyA))
                                             .put("c12", "finalC12")
                                             .put("c22", "finalC22")
                                             .put("b21", "finalB21")
                                             .put("i22", 222)
                                             .build());

    validateSingleExpression(evaluator, "bVal1CVal1.strVal", "c11", false);
    validateExpression(evaluator, "bVal1.cVal1.strVal", "c11");
    validateExpression(evaluator, "bVal1.cVal2.strVal", "finalC12", true);
    validateExpression(evaluator, "bVal1.strVal1", "b11");
    validateExpression(evaluator, "bVal1.strVal2", "b12");
    validateExpression(evaluator, "bVal1.intVal1", 11);
    validateExpression(evaluator, "bVal1.intVal2", 12);
    validateExpression(evaluator, "bVal2.cVal1.strVal", "c21");
    validateExpression(evaluator, "bVal2.cVal2.strVal", "finalC22", true);
    validateExpression(evaluator, "bVal2.strVal1", "finalB21", true);
    validateSingleExpression(evaluator,
        "obj."
            + "bVal2.strVal2",
        null, false);
    assertThat(evaluator.evaluateExpression("<+bVal2.strVal2>")).isEqualTo(null);
    validateExpression(evaluator, "bVal2.intVal1", 21);
    validateExpression(evaluator, "bVal2.intVal2", 222, true);
    validateExpression(evaluator, "strVal1", "a1");
    validateExpression(evaluator, "strVal2", "a2");
  }

  private void validateExpression(EngineExpressionEvaluator evaluator, String expression, Object expected) {
    validateExpression(evaluator, expression, expected, false);
  }

  private void validateExpression(
      EngineExpressionEvaluator evaluator, String expression, Object expected, boolean shouldThrow) {
    validateExpressionWithObjExpression(evaluator, expression, expected, shouldThrow);
  }

  private void validateExpressionWithObjExpression(
      EngineExpressionEvaluator evaluator, String expression, Object expected, boolean skipEvaluate) {
    validateSingleExpression(evaluator, expression, expected, skipEvaluate);
    validateSingleExpression(evaluator, "obj." + expression, expected, skipEvaluate);
  }

  private void validateSingleExpression(
      EngineExpressionEvaluator evaluator, String expression, Object expected, boolean skipEvaluate) {
    expression = "<+" + expression + ">";
    assertThat(evaluator.renderExpression(expression)).isEqualTo(String.valueOf(expected));
    if (skipEvaluate) {
      return;
    }
    assertThat(evaluator.evaluateExpression(expression)).isEqualTo(expected);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testHasVariables() {
    assertThat(EngineExpressionEvaluator.hasExpressions(null)).isFalse();
    assertThat(EngineExpressionEvaluator.hasExpressions("abc")).isFalse();
    assertThat(EngineExpressionEvaluator.hasExpressions("abc <+")).isFalse();
    assertThat(EngineExpressionEvaluator.hasExpressions("abc <+>")).isTrue();
    assertThat(EngineExpressionEvaluator.hasExpressions("abc <+ab>")).isTrue();
    assertThat(EngineExpressionEvaluator.hasExpressions("abc <+ab> <+cd>")).isTrue();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFindExpressions() {
    assertThat(EngineExpressionEvaluator.findExpressions(null)).isEmpty();
    assertThat(EngineExpressionEvaluator.findExpressions("abc")).isEmpty();
    assertThat(EngineExpressionEvaluator.findExpressions("abc <+")).isEmpty();
    assertThat(EngineExpressionEvaluator.findExpressions("abc <+>")).containsExactly("<+>");
    assertThat(EngineExpressionEvaluator.findExpressions("abc <+ab>")).containsExactly("<+ab>");
    assertThat(EngineExpressionEvaluator.findExpressions("abc <+<+ab> <+cd>>")).containsExactly("<+<+ab> <+cd>>");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFindVariables() {
    assertThat(EngineExpressionEvaluator.findVariables(null)).isEmpty();
    assertThat(EngineExpressionEvaluator.findVariables("abc")).isEmpty();
    assertThat(EngineExpressionEvaluator.findVariables("abc <+")).isEmpty();
    assertThat(EngineExpressionEvaluator.findVariables("abc <+>")).containsExactly("<+>");
    assertThat(EngineExpressionEvaluator.findVariables("abc <+ab>")).containsExactly("<+ab>");
    assertThat(EngineExpressionEvaluator.findVariables("abc <+ab> <+cd>")).containsExactly("<+ab>", "<+cd>");
    assertThat(EngineExpressionEvaluator.findVariables("abc <+<+ab> <+cd>>")).containsExactly("<+ab>", "<+cd>");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testValidVariableFieldName() {
    assertThat(EngineExpressionEvaluator.validVariableFieldName(null)).isFalse();
    assertThat(EngineExpressionEvaluator.validVariableFieldName("")).isFalse();
    assertThat(EngineExpressionEvaluator.validVariableFieldName("abc_9")).isTrue();
    assertThat(EngineExpressionEvaluator.validVariableFieldName("__abc_9")).isTrue();
    assertThat(EngineExpressionEvaluator.validVariableFieldName("__abc-9")).isFalse();
    assertThat(EngineExpressionEvaluator.validVariableFieldName("__abc$9")).isFalse();
    assertThat(EngineExpressionEvaluator.validVariableFieldName("__abc{9")).isFalse();
    assertThat(EngineExpressionEvaluator.validVariableFieldName("__abc}9")).isFalse();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testValidNestedExpressions() {
    EngineExpressionEvaluator evaluator =
        prepareEngineExpressionEvaluator(new ImmutableMap.Builder<String, Object>()
                                             .put("a", 5)
                                             .put("b", 12)
                                             .put("c", "<+a> + 2 * <+b>")
                                             .put("d", "<+c> - <+a>")
                                             .put("e", "<+a>")
                                             .put("f", "abc")
                                             .put("g", "def")
                                             .put("v1", "<+v2>")
                                             .put("v2", "<+v3>")
                                             .put("v3", "<+lastPublished.tag>.regex()")
                                             .build());

    assertThat(evaluator.resolve("<+v1>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("<+lastPublished.tag>.regex()");
    assertThatThrownBy(() -> evaluator.resolve("<+v1>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isInstanceOf(HintException.class)
        .hasMessage(
            "Expression <+lastPublished.tag>.regex() might contain some unresolved expressions which could not be evaluated.");
    assertThat(evaluator.evaluateExpression("<+a> + <+b>")).isEqualTo(17);
    assertThat(evaluator.evaluateExpression("<+a> + <+b> == 10")).isEqualTo(false);
    assertThat(evaluator.evaluateExpression("<+a> + <+b> == 17")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+c> - 2 * <+b> == 5")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+c> - 2 * <+b> == <+a>")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+<+c> - 2 * <+b>> == 5")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+<+<+d>> * <+d>> == <+571 + <+<+a>>>")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+e> - <+<+e>> + 1 == 1")).isEqualTo(true);
    assertThat(evaluator.renderExpression("<+a> + <+b> = <+<+a> + <+b>>")).isEqualTo("5 + 12 = 17");
    assertThat(evaluator.renderExpression("<+<+a> > + <+ <+b>> = <+<+a> + <+b>>")).isEqualTo("5 + 12 = 17");
    assertThat(evaluator.renderExpression("<+f> + <+g> = <+<+f> + \" + \" + <+g>>")).isEqualTo("abc + def = abc + def");

    EngineExpressionEvaluator.PartialEvaluateResult result = evaluator.partialEvaluateExpression("<+a> + <+b>");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isFalse();
    assertThat(result.getValue()).isEqualTo(17);

    result = evaluator.partialEvaluateExpression("<+a> + <+b> == 10");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isFalse();
    assertThat(result.getValue()).isEqualTo(false);

    result = evaluator.partialEvaluateExpression("<+a> + <+b> == 17");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isFalse();
    assertThat(result.getValue()).isEqualTo(true);

    result = evaluator.partialEvaluateExpression("<+<+<+d>> * <+d>> == <+571 + <+<+a>>>");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isFalse();
    assertThat(result.getValue()).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidNestedExpressionsWithScriptFeatureFlag() {
    EngineExpressionEvaluator evaluator = prepareEngineExpressionEvaluator(
        new ImmutableMap.Builder<String, Object>()
            .put("a", 5)
            .put("b", 12)
            .put("c", "<+a> + 2 * <+b>")
            .put("d", "<+c> - <+a>")
            .put("e", "<+a>")
            .put("f", "abc")
            .put("g", "def")
            .put("productValues", Arrays.asList(1, 2, 3))
            .put(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY, Arrays.asList("PIE_EXECUTION_JSON_SUPPORT"))
            .build());
    assertThat(evaluator.evaluateExpression("<+ var traverse = function(key) {\n"
                   + "                              var result = 0;\n"
                   + "                              for(productValue: key) \n"
                   + "                              {\n"
                   + "                                  result = result + productValue;\n"
                   + "                               }\n"
                   + "                              return result\n"
                   + "                              };\n"
                   + "                              \n"
                   + "                          traverse(<+productValues>)  >"))
        .isEqualTo(6);
    assertThat(evaluator.evaluateExpression("<+a> + <+b>")).isEqualTo(17);
    assertThat(evaluator.evaluateExpression("<+a> + <+b> == 10")).isEqualTo(false);
    assertThat(evaluator.evaluateExpression("<+a> + <+b> == 17")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+c> - 2 * <+b> == 5")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+c> - 2 * <+b> == <+a>")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+<+c> - 2 * <+b>> == 5")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+<+<+d>> * <+d>> == <+571 + <+<+a>>>")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+e> - <+<+e>> + 1 == 1")).isEqualTo(true);
    assertThat(evaluator.renderExpression("<+a> + <+b> = <+<+a> + <+b>>")).isEqualTo("5 + 12 = 17");
    assertThat(evaluator.renderExpression("<+<+a> > + <+ <+b>> = <+<+a> + <+b>>")).isEqualTo("5 + 12 = 17");
    assertThat(evaluator.renderExpression("<+f> + <+g> = <+<+f> + \" + \" + <+g>>")).isEqualTo("abc + def = abc + def");

    EngineExpressionEvaluator.PartialEvaluateResult result = evaluator.partialEvaluateExpression("<+a> + <+b>");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isFalse();
    assertThat(result.getValue()).isEqualTo(17);

    result = evaluator.partialEvaluateExpression("<+a> + <+b> == 10");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isFalse();
    assertThat(result.getValue()).isEqualTo(false);

    result = evaluator.partialEvaluateExpression("<+a> + <+b> == 17");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isFalse();
    assertThat(result.getValue()).isEqualTo(true);

    result = evaluator.partialEvaluateExpression("<+<+<+d>> * <+d>> == <+571 + <+<+a>>>");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isFalse();
    assertThat(result.getValue()).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidNestedExpressionsForResolveAndEvaluateWithConcatenate() {
    EngineExpressionEvaluator evaluator = prepareEngineExpressionEvaluator(
        new ImmutableMap.Builder<String, Object>()
            .put("a", 5)
            .put("b", 12)
            .put("c", "<+a> + 2 * <+b>")
            .put("d", "<+c> - <+a>")
            .put("e", "<+a>")
            .put("f", "abc")
            .put("g", "def")
            .put("h", "v2")
            .put("company", "harness")
            .put("nested1", "<+nested2>")
            .put("nested2", "<+nested3>")
            .put("nested3", "<+lastPublished.tag>.regex('1')")
            .put("secret1", "<+secrets.getValue('org.v2')>")
            .put("variables",
                new ImmutableMap.Builder<String, Object>()
                    .put("v1", "<+f><+g>")
                    // At any level -> both type of concat if present will not combine as its a difficult problem to
                    // solve, so use either one
                    .put("v2", "'harness' + '<+f><+g>'")
                    .put("v3", "<+f><+g>harness")
                    .put("v4", "<+f> + <+g>")
                    .put("v5", "archit-harness")
                    .put("v6", "<+secrets.getValue('org.v2')>")
                    .put("v7", "<+secret1>")
                    .build())
            .put("var1", "'archit' + <+company>")
            .put("var2", "'archit<+f>' + <+company>")
            .put("var3", "concatenate1")
            .put(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY, Arrays.asList("PIE_EXPRESSION_CONCATENATION"))
            .build());
    // concat expressions
    assertThat(evaluator.resolve("archit-<+company>", true)).isEqualTo("archit-harness");
    assertThat(evaluator.evaluateExpression("archit-<+company>")).isEqualTo("archit-harness");
    assertThat(evaluator.resolve("archit<+company>", true)).isEqualTo("architharness");
    assertThat(evaluator.evaluateExpression("archit<+company>")).isEqualTo("architharness");
    assertThat(evaluator.resolve("archit + <+company>", true)).isEqualTo("archit + harness");
    assertThat(evaluator.evaluateExpression("'archit' + <+company>")).isEqualTo("architharness");
    assertThat(evaluator.resolve("<+company.concat('india')>", true)).isEqualTo("harnessindia");
    assertThat(evaluator.evaluateExpression("<+company.concat('india')>")).isEqualTo("harnessindia");

    // Nested concatenated expressions
    assertThat(evaluator.resolve("<+var1>", true)).isEqualTo("architharness");
    assertThat(evaluator.evaluateExpression("<+var1>")).isEqualTo("architharness");
    assertThat(evaluator.resolve("<+var2>", true)).isEqualTo("architabcharness");
    assertThat(evaluator.evaluateExpression("<+var2>")).isEqualTo("architabcharness");
    assertThat(evaluator.resolve("expression-<+var2>-<+g>", true)).isEqualTo("expression-architabcharness-def");
    assertThat(evaluator.evaluateExpression("expression-<+var2>-<+g>")).isEqualTo("expression-architabcharness-def");
    assertThat(evaluator.resolve("'expression' + <+var2>-<+g>", true)).isEqualTo("'expression' + architabcharness-def");
    assertThat(evaluator.evaluateExpression("'expression' + <+var2> + '-<+g>'"))
        .isEqualTo("expressionarchitabcharness-def");

    // Complex concatenate expressions
    assertThat(evaluator.resolve("<+variables.v1>", true)).isEqualTo("abcdef");
    assertThat(evaluator.evaluateExpression("<+variables.v1>")).isEqualTo("abcdef");
    assertThat(evaluator.resolve("<+variables.v2>", true)).isEqualTo("harnessabcdef");
    assertThat(evaluator.evaluateExpression("<+variables.v2>")).isEqualTo("harnessabcdef");
    // Nested expression contains string concatenation
    assertThat(evaluator.resolve("<+variables.v3>", true)).isEqualTo("abcdefharness");
    assertThat(evaluator.evaluateExpression("<+variables.v3>")).isEqualTo("abcdefharness");

    // OR operators
    assertThat(
        evaluator.evaluateExpression("<+c2.status> == \"SUCCEEDED1\" || <+c2.anotherStatus> == \"IGNORE_FAILED\""))
        .isEqualTo(true);
    // AND operator
    assertThat(evaluator.evaluateExpression("<+c2.status> == \"RUNNING\" && <+c2.anotherStatus> == \"IGNORE_FAILED\""))
        .isEqualTo(true);

    // Complex double nesting with concatenate expressions with prefix combinations
    assertThat(evaluator.resolve("<+c1.<+var3>>", true)).isEqualTo("harness");

    assertThat(evaluator.resolve("harness<+variables.v4><+variables.v3>", true))
        .isEqualTo("harnessabcdefabcdefharness");
    assertThat(evaluator.evaluateExpression("harness<+variables.v4><+variables.v3>"))
        .isEqualTo("harnessabcdefabcdefharness");

    // Functors having concat expressions should work
    assertThat(evaluator.resolve("<+secrets.getValue(\"abc\")>", true))
        .isEqualTo("${ngSecretManager.obtain(\"abc\", 123)}");
    assertThat(evaluator.resolve("<+secrets.getValue(\"<+f>\")>", true))
        .isEqualTo("${ngSecretManager.obtain(\"abc\", 123)}");
    assertThat(evaluator.resolve("<+secrets.getValue(\"harness_<+f>_india\")>", true))
        .isEqualTo("${ngSecretManager.obtain(\"harness_abc_india\", 123)}");
    assertThat(evaluator.resolve("<+secrets.getValue(\"harness_<+f>_india_<+g>\")>", true))
        .isEqualTo("${ngSecretManager.obtain(\"harness_abc_india_def\", 123)}");
    assertThat(evaluator.resolve("<+secrets.getValue(\"harness_\" + <+f> + \"_india_\" + <+g>)>", true))
        .isEqualTo("${ngSecretManager.obtain(\"harness_abc_india_def\", 123)}");
    assertThat(evaluator.resolve("<+secrets.getValue(<+f> + \"_india_\" + <+g>)>", true))
        .isEqualTo("${ngSecretManager.obtain(\"abc_india_def\", 123)}");
    assertThat(evaluator.resolve("<+secrets.getValue(\"<+f>_india_<+g>\")>", true))
        .isEqualTo("${ngSecretManager.obtain(\"abc_india_def\", 123)}");

    // Ternary operators
    assertThat(evaluator.resolve("<+ <+a>==5?<+f>:<+g> >", true)).isEqualTo("abc");
    assertThat(evaluator.resolve("<+ <+a>==5?<+<+f>harness>:<+<+g>def> >", true)).isEqualTo("abcharness");
    assertThat(evaluator.resolve("<+ <+a>==5?\"<+f>harness\":\"<+g>def\" >", true)).isEqualTo("abcharness");
    assertThat(evaluator.resolve("<+ <+a>==<+a>?<+<+f>harness>:<+<+g>def> >", true)).isEqualTo("abcharness");
    assertThat(evaluator.resolve("<+ <+a>==<+b>?<+<+f>harness>:<+<+g>def> >", true)).isEqualTo("defdef");
    assertThat(evaluator.resolve("<+ <+a>==5?<+<+f>+'harness'>:<+<+g>+'def'> >", true)).isEqualTo("abcharness");
    assertThat(evaluator.resolve("<+ <+a>==<+a>?<+<+f>+'harness'>:<+<+g>+'def'> >", true)).isEqualTo("abcharness");
    assertThat(evaluator.resolve("<+ <+a>==<+b>?<+<+f>+'harness'>:<+<+g>+'def'> >", true)).isEqualTo("defdef");

    // Method invocations
    assertThat(evaluator.resolve("<+<+variables.v5>.replace('-','')>", true)).isEqualTo("architharness");
    assertThat(evaluator.resolve("<+variables.v6>", true)).isEqualTo("${ngSecretManager.obtain(\"org.v2\", 123)}");
    assertThat(evaluator.resolve("<+variables.v7>", true)).isEqualTo("${ngSecretManager.obtain(\"org.v2\", 123)}");

    // an expression used in path of existing expression
    assertThat(evaluator.resolve("<+variables.<+h>>", true)).isEqualTo("harnessabcdef");
    assertThat(evaluator.evaluateExpression("<+variables.<+h>>")).isEqualTo("harnessabcdef");

    // Unresolved expressions partially should be resolved
    assertThat(evaluator.resolve("<+nested1>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("<+lastPublished.tag>.regex('1')");
    assertThat(evaluator.evaluateExpression("<+nested1>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("<+lastPublished.tag>.regex('1')");

    // Original expressions should still work with concatenate flag in evaluateInternal
    assertThat(evaluator.evaluateExpression("<+a> + <+b>")).isEqualTo(17);
    assertThat(evaluator.evaluateExpression("<+a> + <+b> == 10")).isEqualTo(false);
    assertThat(evaluator.evaluateExpression("<+a> + <+b> == 17")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+c> - 2 * <+b> == 5")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+c> - 2 * <+b> == <+a>")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+<+c> - 2 * <+b>> == 5")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+<+<+d>> * <+d>> == <+571 + <+<+a>>>")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+e> - <+<+e>> + 1 == 1")).isEqualTo(true);
    assertThat(evaluator.renderExpression("<+a> + <+b> = <+<+a> + <+b>>")).isEqualTo("5 + 12 = 17");
    assertThat(evaluator.renderExpression("<+<+a> > + <+ <+b>> = <+<+a> + <+b>>")).isEqualTo("5 + 12 = 17");
    assertThat(evaluator.renderExpression("<+f> + <+g> = <+<+f> + \" + \" + <+g>>")).isEqualTo("abc + def = abc + def");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testValidNestedExpressionsForResolveAndEvaluateWithConcatenateWithScriptFlag() {
    EngineExpressionEvaluator evaluator = prepareEngineExpressionEvaluator(
        new ImmutableMap.Builder<String, Object>()
            .put("a", 5)
            .put("b", 12)
            .put("c", "<+a> + 2 * <+b>")
            .put("d", "<+c> - <+a>")
            .put("e", "<+a>")
            .put("f", "abc")
            .put("g", "def")
            .put("h", "v2")
            .put("productValues", Arrays.asList(1, 2, 3))
            .put("variableValues", Arrays.asList("v1", "v2", "v3"))
            .put("company", "harness")
            .put("nested1", "<+nested2>")
            .put("nested2", "<+nested3>")
            .put("nested3", "<+lastPublished.tag>.regex('1')")
            .put("secret1", "<+secrets.getValue('org.v2')>")
            .put("variables",
                new ImmutableMap.Builder<String, Object>()
                    .put("v1", "<+f><+g>")
                    // At any level -> both type of concat if present will not combine as it's a difficult problem to
                    // solve, so use either one
                    .put("v2", "'harness' + '<+f><+g>'")
                    .put("v3", "<+f><+g>")
                    .put("v4", "<+f> + <+g>")
                    .put("v5", "archit-harness")
                    .put("v6", "<+secrets.getValue('org.v2')>")
                    .put("v7", "<+secret1>")
                    .build())
            .put("var1", "'archit' + <+company>")
            .put("var2", "'archit<+f>' + <+company>")
            .put("var3", "concatenate1")
            .put(EngineExpressionEvaluator.ENABLED_FEATURE_FLAGS_KEY,
                Arrays.asList("PIE_EXPRESSION_CONCATENATION", "PIE_EXECUTION_JSON_SUPPORT"))
            .build());
    // concat expressions
    assertThat(evaluator.resolve("archit-<+company>", true)).isEqualTo("archit-harness");
    assertThat(evaluator.evaluateExpression("archit-<+company>")).isEqualTo("archit-harness");
    assertThat(evaluator.resolve("archit<+company>", true)).isEqualTo("architharness");
    assertThat(evaluator.evaluateExpression("archit<+company>")).isEqualTo("architharness");
    assertThat(evaluator.resolve("archit + <+company>", true)).isEqualTo("archit + harness");
    assertThat(evaluator.evaluateExpression("'archit' + <+company>")).isEqualTo("architharness");
    assertThat(evaluator.resolve("<+company.concat('india')>", true)).isEqualTo("harnessindia");
    assertThat(evaluator.evaluateExpression("<+company.concat('india')>")).isEqualTo("harnessindia");

    // Nested concatenated expressions
    assertThat(evaluator.resolve("<+var1>", true)).isEqualTo("architharness");
    assertThat(evaluator.evaluateExpression("<+var1>")).isEqualTo("architharness");
    assertThat(evaluator.resolve("<+var2>", true)).isEqualTo("architabcharness");
    assertThat(evaluator.evaluateExpression("<+var2>")).isEqualTo("architabcharness");
    assertThat(evaluator.resolve("expression-<+var2>-<+g>", true)).isEqualTo("expression-architabcharness-def");
    assertThat(evaluator.evaluateExpression("expression-<+var2>-<+g>")).isEqualTo("expression-architabcharness-def");
    assertThat(evaluator.resolve("'expression' + <+var2>-<+g>", true)).isEqualTo("'expression' + architabcharness-def");
    assertThat(evaluator.evaluateExpression("'expression' + <+var2> + '-<+g>'"))
        .isEqualTo("expressionarchitabcharness-def");

    // Complex concatenate expressions
    assertThat(evaluator.resolve("<+variables.v1>", true)).isEqualTo("abcdef");
    assertThat(evaluator.evaluateExpression("<+variables.v1>")).isEqualTo("abcdef");
    assertThat(evaluator.resolve("<+variables.v2>", true)).isEqualTo("harnessabcdef");
    assertThat(evaluator.evaluateExpression("<+variables.v2>")).isEqualTo("harnessabcdef");
    assertThat(evaluator.resolve("<+variables.v3>", true)).isEqualTo("abcdef");
    assertThat(evaluator.evaluateExpression("<+variables.v3>")).isEqualTo("abcdef");
    assertThat(evaluator.resolve("harness<+variables.v4><+variables.v3>", true)).isEqualTo("harnessabcdefabcdef");
    assertThat(evaluator.evaluateExpression("harness<+variables.v4><+variables.v3>")).isEqualTo("harnessabcdefabcdef");
    // Nested concatenate expression in script
    // Note In script mode -> concatenate expressions can be using + operator between them only
    assertThat(evaluator.evaluateExpression("<+ var traverse = function(key) {\n"
                   + "                              var result = '';\n"
                   + "                              for(variableValue: key) \n"
                   + "                              {\n"
                   + "                                  result = result + variableValue + <+f> + <+g>;\n"
                   + "                               }\n"
                   + "                              return result\n"
                   + "                              };\n"
                   + "                              \n"
                   + "                          traverse(<+variableValues>)  >"))
        .isEqualTo("v1abcdefv2abcdefv3abcdef");

    // Functors having concat expressions should work
    assertThat(evaluator.resolve("<+secrets.getValue(\"<+f>\")>", true))
        .isEqualTo("${ngSecretManager.obtain(\"abc\", 123)}");
    assertThat(evaluator.resolve("<+secrets.getValue(\"harness_<+f>_india\")>", true))
        .isEqualTo("${ngSecretManager.obtain(\"harness_abc_india\", 123)}");
    assertThat(evaluator.resolve("<+secrets.getValue(\"harness_<+f>_india_<+g>\")>", true))
        .isEqualTo("${ngSecretManager.obtain(\"harness_abc_india_def\", 123)}");
    assertThat(evaluator.resolve("<+secrets.getValue(\"harness_\" + <+f> + \"_india_\" + <+g>)>", true))
        .isEqualTo("${ngSecretManager.obtain(\"harness_abc_india_def\", 123)}");
    assertThat(evaluator.resolve("<+secrets.getValue(<+f> + \"_india_\" + <+g>)>", true))
        .isEqualTo("${ngSecretManager.obtain(\"abc_india_def\", 123)}");
    assertThat(evaluator.resolve("<+secrets.getValue(\"<+f>_india_<+g>\")>", true))
        .isEqualTo("${ngSecretManager.obtain(\"abc_india_def\", 123)}");

    // Ternary operators
    assertThat(evaluator.resolve("<+ <+a>==5?<+f>:<+g> >", true)).isEqualTo("abc");
    assertThat(evaluator.resolve("<+ <+a>==5?<+<+f>harness>:<+<+g>def> >", true)).isEqualTo("abcharness");
    assertThat(evaluator.resolve("<+ <+a>==<+a>?<+<+f>harness>:<+<+g>def> >", true)).isEqualTo("abcharness");
    assertThat(evaluator.resolve("<+ <+a>==<+b>?<+<+f>harness>:<+<+g>def> >", true)).isEqualTo("defdef");
    assertThat(evaluator.resolve("<+ <+a>==5?<+<+f>+'harness'>:<+<+g>+'def'> >", true)).isEqualTo("abcharness");
    assertThat(evaluator.resolve("<+ <+a>==<+a>?<+<+f>+'harness'>:<+<+g>+'def'> >", true)).isEqualTo("abcharness");
    assertThat(evaluator.resolve("<+ <+a>==<+b>?<+<+f>+'harness'>:<+<+g>+'def'> >", true)).isEqualTo("defdef");

    // OR operators
    assertThat(
        evaluator.evaluateExpression("<+c2.status> == \"SUCCEEDED1\" || <+c2.anotherStatus> == \"IGNORE_FAILED\""))
        .isEqualTo(true);
    // AND operator
    assertThat(evaluator.evaluateExpression("<+c2.status> == \"RUNNING\" && <+c2.anotherStatus> == \"IGNORE_FAILED\""))
        .isEqualTo(true);

    // Complex double nesting with concatenate expressions with prefix combinations
    assertThat(evaluator.resolve("<+c1.<+var3>>", true)).isEqualTo("harness");

    // Method invocations
    assertThat(evaluator.resolve("<+<+variables.v5>.replace('-','')>", true)).isEqualTo("architharness");
    assertThat(evaluator.resolve("<+variables.v6>", true)).isEqualTo("${ngSecretManager.obtain(\"org.v2\", 123)}");
    assertThat(evaluator.resolve("<+variables.v7>", true)).isEqualTo("${ngSecretManager.obtain(\"org.v2\", 123)}");

    // an expression used in path of existing expression
    assertThat(evaluator.resolve("<+variables.<+h>>", true)).isEqualTo("harnessabcdef");
    assertThat(evaluator.evaluateExpression("<+variables.<+h>>")).isEqualTo("harnessabcdef");

    // Unresolved expressions partially should be resolved
    assertThat(evaluator.resolve("<+nested1>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("<+lastPublished.tag>.regex('1')");
    assertThat(evaluator.evaluateExpression("<+nested1>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("<+lastPublished.tag>.regex('1')");

    // Original expressions should still work with concatenate flag in evaluateInternal
    assertThat(evaluator.evaluateExpression("<+ var traverse = function(key) {\n"
                   + "                              var result = 0;\n"
                   + "                              for(productValue: key) \n"
                   + "                              {\n"
                   + "                                  result = result + productValue;\n"
                   + "                               }\n"
                   + "                              return result\n"
                   + "                              };\n"
                   + "                              \n"
                   + "                          traverse(<+productValues>)  >"))
        .isEqualTo(6);
    assertThat(evaluator.evaluateExpression("<+a> + <+b>")).isEqualTo(17);
    assertThat(evaluator.evaluateExpression("<+a> + <+b> == 10")).isEqualTo(false);
    assertThat(evaluator.evaluateExpression("<+a> + <+b> == 17")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+c> - 2 * <+b> == 5")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+c> - 2 * <+b> == <+a>")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+<+c> - 2 * <+b>> == 5")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+<+<+d>> * <+d>> == <+571 + <+<+a>>>")).isEqualTo(true);
    assertThat(evaluator.evaluateExpression("<+e> - <+<+e>> + 1 == 1")).isEqualTo(true);
    assertThat(evaluator.renderExpression("<+a> + <+b> = <+<+a> + <+b>>")).isEqualTo("5 + 12 = 17");
    assertThat(evaluator.renderExpression("<+<+a> > + <+ <+b>> = <+<+a> + <+b>>")).isEqualTo("5 + 12 = 17");
    assertThat(evaluator.renderExpression("<+f> + <+g> = <+<+f> + \" + \" + <+g>>")).isEqualTo("abc + def = abc + def");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testInvalidNestedExpressions() {
    EngineExpressionEvaluator evaluator = prepareEngineExpressionEvaluator(new ImmutableMap.Builder<String, Object>()
                                                                               .put("a", 5)
                                                                               .put("c", "<+a> + 2 * <+b>")
                                                                               .put("d", "<+c> - <+a>")
                                                                               .put("e", "<+a>")
                                                                               .put("f", "<+b>")
                                                                               .build());
    assertThat(evaluator.evaluateExpression("<+a> + <+a>")).isEqualTo(10);
    assertThatThrownBy(() -> evaluator.evaluateExpression("<+a> + <+b>"))
        .isInstanceOf(HintException.class)
        .hasMessage("Expression <+a> + <+b> might contain some unresolved expressions which could not be evaluated.");
    assertThatThrownBy(() -> evaluator.evaluateExpression("<+a> + <+<+b> + <+e>>"))
        .isInstanceOf(HintException.class)
        .hasMessage("Expression <+b> + <+e> might contain some unresolved expressions which could not be evaluated.");
    // parsing error
    assertThatThrownBy(() -> evaluator.evaluateExpression("<+a> + <+<+b>> + <+e>>"))
        .isInstanceOf(HintException.class)
        .hasMessage(
            "Please re-check the expression <+a> + <+<+b>> + <+e>> are written in correct format of <+...> as well as for embedded expressions.");
    assertThat(evaluator.evaluateExpression("<+a> + <+<+a> + <+e>>")).isEqualTo(15);
    assertThatThrownBy(() -> evaluator.renderExpression("<+<+a> + <+b>>"))
        .isInstanceOf(HintException.class)
        .hasMessage("Expression <+a> + <+b> might contain some unresolved expressions which could not be evaluated.");
    assertThatThrownBy(() -> evaluator.renderExpression("<+<+a> + <+b>>", true))
        .isInstanceOf(HintException.class)
        .hasMessage("Expression <+a> + <+b> might contain some unresolved expressions which could not be evaluated.");

    EngineExpressionEvaluator.PartialEvaluateResult result = evaluator.partialEvaluateExpression("<+a> + <+a>");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isFalse();
    assertThat(result.getValue()).isEqualTo(10);

    result = evaluator.partialEvaluateExpression("<+a> + <+b>");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isTrue();

    List<String> variables = EngineExpressionEvaluator.findVariables(result.getExpressionValue());
    assertThat(variables.get(0)).startsWith("<+" + EngineExpressionEvaluator.HARNESS_INTERNAL_VARIABLE_PREFIX);
    assertThat(variables.get(1)).isEqualTo("<+b>");

    result = evaluator.partialEvaluateExpression("<+a> + <+<+b> + <+e>>");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isTrue();
    assertThat(result.getExpressionValue()).contains("<+<+b>");

    variables = EngineExpressionEvaluator.findVariables(result.getExpressionValue());
    assertThat(variables.get(0)).startsWith("<+" + EngineExpressionEvaluator.HARNESS_INTERNAL_VARIABLE_PREFIX);
    assertThat(variables.get(1)).isEqualTo("<+b>");
    assertThat(variables.get(2)).startsWith("<+" + EngineExpressionEvaluator.HARNESS_INTERNAL_VARIABLE_PREFIX);

    result = evaluator.partialEvaluateExpression("<+a> + <+<+a> + <+e>>");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isFalse();
    assertThat(result.getValue()).isEqualTo(15);

    result = evaluator.partialRenderExpression("<+<+a> + <+<+b> + <+e>>>");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isTrue();
    assertThat(result.getExpressionValue())
        .startsWith("<+<+" + EngineExpressionEvaluator.HARNESS_INTERNAL_VARIABLE_PREFIX);
    assertThat(result.getExpressionValue()).contains("<+<+b>");

    result = evaluator.partialRenderExpression("<+<+a> + <+<+a> + <+e>>> abc <+<+a> + <+<+b> + <+e>>>");
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isTrue();
    assertThat(result.getExpressionValue())
        .startsWith("15 abc <+<+" + EngineExpressionEvaluator.HARNESS_INTERNAL_VARIABLE_PREFIX);
    assertThat(result.getExpressionValue()).contains("<+<+b>");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testPartialResolve() {
    EngineExpressionEvaluator evaluator = prepareEngineExpressionEvaluator(new ImmutableMap.Builder<String, Object>()
                                                                               .put("a", 5)
                                                                               .put("c", "<+a> + 2 * <+b>")
                                                                               .put("d", "<+c> - <+a>")
                                                                               .put("e", "<+a>")
                                                                               .put("f", "<+b>")
                                                                               .build());
    Map<String, Object> m =
        new HashMap<>(ImmutableMap.of("a", "<+a>", "b", "<+b>", "c", "<+a> < <+b> == <+<+a> < <+b>>", "d",
            new HashMap<>(ImmutableMap.of("a", "<+a> + <+<+a> + <+e>>", "b", "<+a> + <+<+b> + <+e>>"))));
    EngineExpressionEvaluator.PartialEvaluateResult result =
        evaluator.partialResolve(m, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    assertThat(result).isNotNull();
    assertThat(result.isPartial()).isFalse();

    Map<String, Object> value = (Map<String, Object>) result.getValue();
    assertThat(value).isNotNull();
    assertThat(value).isNotEmpty();
    assertThat(value.get("a")).isEqualTo("5");
    assertThat(value.get("b")).isEqualTo("<+b>");
    assertThat((String) value.get("c"))
        .startsWith("5 < <+b> == <+<+" + EngineExpressionEvaluator.HARNESS_INTERNAL_VARIABLE_PREFIX);
    assertThat((String) value.get("c")).endsWith("> < <+b>>");

    Map<String, Object> inner = (Map<String, Object>) value.get("d");
    assertThat(inner.get("a")).isEqualTo("5 + 10");
    assertThat((String) inner.get("b"))
        .startsWith("5 + <+<+b> + <+" + EngineExpressionEvaluator.HARNESS_INTERNAL_VARIABLE_PREFIX);
    assertThat((String) inner.get("b")).endsWith(">>");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testUnresolvedExpressionsCheck() {
    EngineExpressionEvaluator evaluator = prepareEngineExpressionEvaluator(
        new ImmutableMap.Builder<String, Object>().put("a", 5).put("c", "abc").put("d", "<+a>").build());
    assertThat(evaluator.evaluateExpression("<+a> + <+d>")).isEqualTo(10);
    assertThat(evaluator.evaluateExpression("<+b>")).isEqualTo(null);

    assertThat(evaluator.renderExpression("<+a> + <+d>")).isEqualTo("5 + 5");
    assertThat(evaluator.renderExpression("<+a> + <+b> + <+c> + <+d>")).isEqualTo("5 + null + abc + 5");
    assertThatThrownBy(() -> evaluator.renderExpression("<+a> + <+b> + <+c> + <+d>", false))
        .isInstanceOf(UnresolvedExpressionsException.class)
        .hasMessage("Unresolved expressions: b");
    assertThat(evaluator.renderExpression("<+a> + <+b> + <+c> + <+d>", true)).isEqualTo("5 + null + abc + 5");
    assertThatThrownBy(() -> evaluator.renderExpression("<+a> + <+b> + <+c> + <+d>>", false))
        .isInstanceOf(UnresolvedExpressionsException.class)
        .hasMessage("Unresolved expressions: b");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCalculateExecutionMode() {
    assertThat(EngineExpressionEvaluator.calculateExpressionMode(true))
        .isEqualTo(ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    assertThat(EngineExpressionEvaluator.calculateExpressionMode(false))
        .isEqualTo(ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
  }

  @Value
  @Builder
  public static class DummyA {
    DummyB bVal1;
    DummyField<DummyB> bVal2;
    String strVal1;
    DummyField<String> strVal2;
  }

  @Value
  @Builder
  public static class DummyB {
    DummyC cVal1;
    DummyField<DummyC> cVal2;
    String strVal1;
    DummyField<String> strVal2;
    int intVal1;
    DummyField<Integer> intVal2;
  }

  @Value
  @Builder
  public static class DummyC {
    String strVal;
  }

  private static EngineExpressionEvaluator prepareEngineExpressionEvaluator(Map<String, Object> contextMap) {
    SampleEngineExpressionEvaluator evaluator = new SampleEngineExpressionEvaluator();
    if (EmptyPredicate.isEmpty(contextMap)) {
      return evaluator;
    }

    for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
      evaluator.addToContext(entry.getKey(), entry.getValue());
    }
    return evaluator;
  }

  public static class SampleEngineExpressionEvaluator extends EngineExpressionEvaluator {
    public SampleEngineExpressionEvaluator() {
      super(null);
    }

    @Override
    protected void initialize() {
      super.initialize();
      addStaticAlias("bVal1CVal1", "bVal1.cVal1");
      addToContext("secrets", new SecretFunctor(123));
      addToContext("nestedConcatenate",
          new ImmutableMap.Builder<String, Object>()
              .put("c1", new ImmutableMap.Builder<String, Object>().put("concatenate1", "harness").build())
              .put("c2",
                  new ImmutableMap.Builder<String, Object>()
                      .put("status", "RUNNING")
                      .put("anotherStatus", "IGNORE_FAILED")
                      .build())
              .build());
    }

    @NotNull
    protected List<String> fetchPrefixes() {
      return ImmutableList.of("obj", "", "nestedConcatenate");
    }

    @Override
    protected Object evaluateInternal(String expression, EngineJexlContext ctx) {
      Object value = super.evaluateInternal(expression, ctx);
      if (value instanceof DummyField) {
        DummyField<?> field = (DummyField<?>) value;
        return field.isExpression() ? field.getExpressionValue() : field.getValue();
      }
      return value;
    }
  }

  public static class SecretFunctor implements ExpressionFunctor {
    long expressionFunctorToken;

    public SecretFunctor(long expressionFunctorToken) {
      this.expressionFunctorToken = expressionFunctorToken;
    }

    public Object getValue(String secretIdentifier) {
      return "${ngSecretManager.obtain(\"" + secretIdentifier + "\", " + expressionFunctorToken + ")}";
    }
  }
}
