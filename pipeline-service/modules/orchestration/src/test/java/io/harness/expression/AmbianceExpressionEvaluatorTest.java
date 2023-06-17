/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.text.resolver.ExpressionResolver.nullStringValue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.expressions.AmbianceExpressionEvaluator;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.functors.StrategyFunctor;
import io.harness.exception.UnresolvedExpressionsException;
import io.harness.expression.common.ExpressionMode;
import io.harness.expression.field.dummy.DummyOrchestrationField;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ForMetadata;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class AmbianceExpressionEvaluatorTest extends OrchestrationTestBase {
  private static final String ACCOUNT_ID = generateUuid();
  private static final String ORG_ID = generateUuid();
  private static final String PROJECT_ID = generateUuid();
  private static final String APP_ID = generateUuid();
  private static final String PLAN_EXECUTION_ID = generateUuid();
  private static final String PLAN_ID = generateUuid();
  private static final String PHASE_RUNTIME_ID = generateUuid();
  private static final String PHASE_SETUP_ID = generateUuid();
  private static final String SECTION_RUNTIME_ID = generateUuid();
  private static final String SECTION_SETUP_ID = generateUuid();
  @Mock private PlanExecutionService planExecutionService;
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock NodeExecutionsCache nodeExecutionsCache;

  @Before
  public void setup() {
    when(planExecutionService.getPlanExecutionMetadata(anyString())).thenReturn(null);
    when(pmsFeatureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testWithoutExpressions() {
    DummyB dummyB1 = DummyB.builder()
                         .cVal1(DummyC.builder().strVal("c11").build())
                         .cVal2(DummyOrchestrationField.createValueField(DummyC.builder().strVal("c12").build()))
                         .strVal1("b11")
                         .strVal2(DummyOrchestrationField.createValueField("b12"))
                         .intVal1(11)
                         .intVal2(DummyOrchestrationField.createValueField(12))
                         .build();
    DummyB dummyB2 = DummyB.builder()
                         .cVal1(DummyC.builder().strVal("c21").build())
                         .cVal2(DummyOrchestrationField.createValueField(DummyC.builder().strVal("c22").build()))
                         .strVal1("b21")
                         .strVal2(DummyOrchestrationField.createValueField("b22"))
                         .intVal1(21)
                         .intVal2(DummyOrchestrationField.createValueField(22))
                         .build();
    DummyA dummyA = DummyA.builder()
                        .bVal1(dummyB1)
                        .bVal2(DummyOrchestrationField.createValueField(dummyB2))
                        .strVal1("a1")
                        .strVal2(DummyOrchestrationField.createValueField("a2"))
                        .build();

    EngineExpressionEvaluator evaluator =
        prepareEngineExpressionEvaluator(ImmutableMap.of("obj", DummyOrchestrationField.createValueField(dummyA)));

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
                         .cVal2(DummyOrchestrationField.createExpressionField("<+c12>"))
                         .strVal1("b11")
                         .strVal2(DummyOrchestrationField.createValueField("b12"))
                         .intVal1(11)
                         .intVal2(DummyOrchestrationField.createValueField(12))
                         .build();
    DummyB dummyB2 = DummyB.builder()
                         .cVal1(DummyC.builder().strVal("c21").build())
                         .cVal2(DummyOrchestrationField.createExpressionField("<+c22>"))
                         .strVal1("<+b21>")
                         .strVal2(DummyOrchestrationField.createExpressionField("<+b22>"))
                         .intVal1(21)
                         .intVal2(DummyOrchestrationField.createExpressionField("<+i22>"))
                         .build();
    DummyA dummyA = DummyA.builder()
                        .bVal1(dummyB1)
                        .bVal2(DummyOrchestrationField.createValueField(dummyB2))
                        .strVal1("a1")
                        .strVal2(DummyOrchestrationField.createValueField("a2"))
                        .build();

    EngineExpressionEvaluator evaluator =
        prepareEngineExpressionEvaluator(new ImmutableMap.Builder<String, Object>()
                                             .put("obj", DummyOrchestrationField.createValueField(dummyA))
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
    validateSingleExpression(evaluator, "bVal2.strVal2", "null", true);
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

  private Ambiance buildAmbiance(StrategyMetadata metadata) {
    Level phaseLevel =
        Level.newBuilder()
            .setRuntimeId(PHASE_RUNTIME_ID)
            .setSetupId(PHASE_SETUP_ID)
            .setStartTs(1)
            .setIdentifier("i1")
            .setStepType(StepType.newBuilder().setType("PHASE").setStepCategory(StepCategory.STEP).build())
            .build();
    Level sectionLevel =
        Level.newBuilder()
            .setRuntimeId(SECTION_RUNTIME_ID)
            .setSetupId(SECTION_SETUP_ID)
            .setGroup("SECTION")
            .setStartTs(2)
            .setIdentifier("i2")
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STAGE).build())
            .build();
    Level strategyLevel =
        Level.newBuilder()
            .setRuntimeId("STRATEGY_RUNTIME_ID")
            .setSetupId("STRATEGY_SETUP_ID")
            .setGroup("STRATEGY")
            .setStartTs(2)
            .setIdentifier("i2")
            .setStepType(StepType.newBuilder().setType("STRATEGY").setStepCategory(StepCategory.STRATEGY).build())
            .build();
    Level stageLevel =
        Level.newBuilder()
            .setRuntimeId(SECTION_RUNTIME_ID)
            .setSetupId(SECTION_SETUP_ID)
            .setGroup("STAGE")
            .setStartTs(3)
            .setIdentifier("i3")
            .setStepType(StepType.newBuilder().setType("SECTION").setStepCategory(StepCategory.STAGE).build())
            .setStrategyMetadata(metadata)
            .build();
    List<Level> levels = new ArrayList<>();
    levels.add(phaseLevel);
    levels.add(sectionLevel);
    levels.add(strategyLevel);
    levels.add(stageLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId(PLAN_EXECUTION_ID)
        .setPlanId(PLAN_ID)
        .putAllSetupAbstractions(ImmutableMap.of(
            "accountId", ACCOUNT_ID, "orgIdentifier", ORG_ID, "projectIdentifier", PROJECT_ID, "appId", APP_ID))
        .addAllLevels(levels)
        .build();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testMatrixExpressions() {
    Ambiance ambiance = buildAmbiance(
        StrategyMetadata.newBuilder()
            .setMatrixMetadata(MatrixMetadata.newBuilder().addMatrixCombination(1).putMatrixValues("a", "1").build())
            .build());

    EngineExpressionEvaluator evaluator =
        prepareEngineExpressionEvaluator(new ImmutableMap.Builder<String, Object>()
                                             .put("strategy", new StrategyFunctor(ambiance, nodeExecutionsCache))
                                             .build());

    validateSingleExpression(evaluator, "strategy.matrix.a", "1", false);
    validateSingleExpression(evaluator, "strategy.iteration", 0, false);
    validateSingleExpression(evaluator, "strategy.iterations", 0, false);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testForExpressions() {
    Ambiance ambiance = buildAmbiance(StrategyMetadata.newBuilder()
                                          .setForMetadata(ForMetadata.newBuilder()
                                                              .addAllPartition(Arrays.asList("host1", "host2", "host3"))
                                                              .setValue("value")
                                                              .build())
                                          .build());

    EngineExpressionEvaluator evaluator =
        prepareEngineExpressionEvaluator(new ImmutableMap.Builder<String, Object>()
                                             .put("strategy", new StrategyFunctor(ambiance, nodeExecutionsCache))
                                             .build());

    validateSingleExpression(evaluator, "strategy.repeat.partition", Arrays.asList("host1", "host2", "host3"), false);
    validateSingleExpression(evaluator, "strategy.repeat.item", "value", false);
    validateSingleExpression(evaluator, "strategy.iteration", 0, false);
    validateSingleExpression(evaluator, "strategy.iterations", 0, false);
  }

  private void validateExpression(EngineExpressionEvaluator evaluator, String expression, Object expected) {
    validateExpression(evaluator, expression, expected, false);
  }

  private void validateExpression(
      EngineExpressionEvaluator evaluator, String expression, Object expected, boolean skipEvaluate) {
    validateExpressionWithObjExpression(evaluator, expression, expected, skipEvaluate);
  }

  private void validateExpressionWithObjExpression(
      EngineExpressionEvaluator evaluator, String expression, Object expected, boolean skipEvaluate) {
    validateSingleExpression(evaluator, expression, expected, skipEvaluate);
    validateSingleExpression(evaluator, "obj." + expression, expected, skipEvaluate);
  }

  public boolean isAnyCollection(Object value) {
    return value instanceof Map || value instanceof Collection || value instanceof String[] || value instanceof List
        || value instanceof Iterable;
  }

  private void validateSingleExpression(
      EngineExpressionEvaluator evaluator, String expression, Object expected, boolean skipEvaluate) {
    expression = "<+" + expression + ">";
    if (isAnyCollection(expected)) {
      assertThat(evaluator.renderExpression(expression)).isEqualTo(JsonUtils.asJson(expected));
    } else {
      assertThat(evaluator.renderExpression(expression)).isEqualTo(String.valueOf(expected));
    }
    if (skipEvaluate) {
      return;
    }
    assertThat(evaluator.evaluateExpression(expression)).isEqualTo(expected);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testParameterFieldResolution() {
    DummyD dummyD = DummyD.builder().dummyD(ParameterField.createExpressionField(true, "<+c>", null, false)).build();

    EngineExpressionEvaluator evaluator = prepareEngineExpressionEvaluator(ImmutableMap.of("a", "str1", "b", 100, "c",
        DummyD.builder()
            .strVal("p")
            .iVal(ParameterField.createExpressionField(true, "<+b>", null, false))
            .strVal2(ParameterField.createExpressionField(true, "<+a>", null, true))
            .dummyD(ParameterField.createExpressionField(true, "<+d>", null, false))
            .build(),
        "d",
        DummyD.builder().strVal("q").strVal2(ParameterField.createExpressionField(true, "<+a>", null, true)).build()));

    Pair<Map<String, Object>, Object> pair = executeResolve(evaluator, dummyD);
    Object resp = pair.getRight();
    assertThat(resp).isNotNull();
    assertThat(resp).isInstanceOf(Map.class);

    DummyD out = RecastOrchestrationUtils.fromMap(pair.getLeft(), DummyD.class);
    assertThat(out).isNotNull();

    ParameterField<DummyD> innerPF = out.getDummyD();
    assertThat(innerPF).isNotNull();
    assertThat(innerPF.isExpression()).isFalse();

    DummyD inner = innerPF.getValue();
    assertThat(inner).isNotNull();
    assertThat(inner.getStrVal()).isEqualTo("p");
    assertThat(inner.getIVal().getValue()).isEqualTo(100);
    assertThat(inner.getStrVal2().getValue()).isEqualTo("str1");

    ParameterField<DummyD> inner2PF = inner.getDummyD();
    assertThat(innerPF).isNotNull();
    assertThat(innerPF.isExpression()).isFalse();

    DummyD inner2 = inner2PF.getValue();
    assertThat(inner2).isNotNull();
    assertThat(inner2.getStrVal()).isEqualTo("q");
    assertThat(inner.getStrVal2().getValue()).isEqualTo("str1");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testResolveExpressionWithExpressionModes() {
    EngineExpressionEvaluator expressionEvaluator = prepareEngineExpressionEvaluator(ImmutableMap.of("key1", "val1"));

    // key1 is set in context, so for each mode the returned value would be val1.
    assertThat(expressionEvaluator.resolve("<+key1>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("val1");
    assertThat(expressionEvaluator.resolve("<+key1>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED)).isEqualTo("val1");
    assertThat(expressionEvaluator.resolve("<+key1>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED)).isEqualTo("val1");

    // expression will not be resolved, and mode is RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED, so original expression
    // will be returned.
    assertThat(expressionEvaluator.resolve("<+invalidKey>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("<+invalidKey>");
    // expression will not be resolved, and mode is RETURN_NULL_IF_UNRESOLVED, so null value would be returned.
    assertThat(expressionEvaluator.resolve("<+invalidKey>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo(nullStringValue);
    // expression will not be resolved, and mode is THROW_EXCEPTION_IF_UNRESOLVED, so it will throw
    // UnresolvedExpressionsException exception.
    assertThatThrownBy(() -> expressionEvaluator.resolve("<+invalidKey>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isInstanceOf(UnresolvedExpressionsException.class);

    // expression will be resolved, so val1 will replace the expression in input string.
    assertThat(expressionEvaluator.resolve("abc<+key1> def", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("abcval1 def");
    assertThat(expressionEvaluator.resolve("abc<+key1> def", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo("abcval1 def");
    assertThat(expressionEvaluator.resolve("abc<+key1> def", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("abcval1 def");

    // expression will not be resolved, and mode is RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED, so input string would be
    // as is.
    assertThat(
        expressionEvaluator.resolve("abc<+invalidKey> def", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("abc<+invalidKey> def");

    // expression will not be resolved, and mode is RETURN_NULL_IF_UNRESOLVED, so null value will replace the expression
    // in input string.
    assertThat(expressionEvaluator.resolve("abc<+invalidKey> def", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo("abc" + nullStringValue + " def");

    // expression will not be resolved, and mode is THROW_EXCEPTION_IF_UNRESOLVED, so it will throw
    // UnresolvedExpressionsException exception.
    assertThatThrownBy(
        () -> expressionEvaluator.resolve("abc<+invalidKey> def", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isInstanceOf(UnresolvedExpressionsException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testEvaluateExpressionWithExpressionModes() {
    EngineExpressionEvaluator expressionEvaluator = prepareEngineExpressionEvaluator(ImmutableMap.of("key1", "val1"));

    // key1 is set in context, so for each mode the returned value would be val1.
    assertThat(
        expressionEvaluator.evaluateExpression("<+key1>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("val1");
    assertThat(expressionEvaluator.evaluateExpression("<+key1>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo("val1");
    assertThat(expressionEvaluator.evaluateExpression("<+key1>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isEqualTo("val1");

    // expression will not be evaluated, and mode is RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED, so original expression
    // will be returned.
    assertThat(expressionEvaluator.evaluateExpression(
                   "<+invalidKey>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("<+invalidKey>");
    // One inner expression will be resolved and other will not be resolved. Since mode is
    // RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED so resolved value will be concatenated with unresolved expression.
    assertThat(expressionEvaluator.evaluateExpression(
                   "<+invalidKey>+<+key1>", ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED))
        .isEqualTo("<+invalidKey>val1");

    // expression will not be evaluated, and mode is RETURN_NULL_IF_UNRESOLVED, so null value would be returned.
    assertThat(expressionEvaluator.evaluateExpression("<+invalidKey>", ExpressionMode.RETURN_NULL_IF_UNRESOLVED))
        .isEqualTo(null);

    // expression will not be resolved, and mode is THROW_EXCEPTION_IF_UNRESOLVED, so it will throw
    // UnresolvedExpressionsException exception.
    assertThatThrownBy(() -> expressionEvaluator.resolve("<+invalidKey>", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isInstanceOf(UnresolvedExpressionsException.class);
    assertThatThrownBy(
        () -> expressionEvaluator.resolve("abc<+invalidKey> def", ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED))
        .isInstanceOf(UnresolvedExpressionsException.class);
  }

  private Pair<Map<String, Object>, Object> executeResolve(EngineExpressionEvaluator evaluator, Object o) {
    Map<String, Object> docOriginal = RecastOrchestrationUtils.toMap(o);
    evaluator.resolve(docOriginal, false);

    // using recaster to obtain deep copy of the object
    Map<String, Object> docCopy = RecastOrchestrationUtils.toMap(o);

    return Pair.of(docOriginal, NodeExecutionUtils.resolveObject(docCopy));
  }

  @Value
  @Builder
  public static class DummyA {
    DummyB bVal1;
    DummyOrchestrationField<DummyB> bVal2;
    String strVal1;
    DummyOrchestrationField<String> strVal2;
  }

  @Value
  @Builder
  public static class DummyB {
    DummyC cVal1;
    DummyOrchestrationField<DummyC> cVal2;
    String strVal1;
    DummyOrchestrationField<String> strVal2;
    int intVal1;
    DummyOrchestrationField<Integer> intVal2;
  }

  @Value
  @Builder
  public static class DummyC {
    String strVal;
  }

  @Value
  @Builder
  public static class DummyD {
    String strVal;
    ParameterField<DummyD> dummyD;
    ParameterField<Integer> iVal;
    ParameterField<String> strVal2;
    DummyD dummyD2;
  }

  private EngineExpressionEvaluator prepareEngineExpressionEvaluator(Map<String, Object> contextMap) {
    SampleEngineExpressionEvaluator evaluator = new SampleEngineExpressionEvaluator();
    on(evaluator).set("planExecutionService", planExecutionService);
    on(evaluator).set("inputSetValidatorFactory", inputSetValidatorFactory);
    on(evaluator).set("pmsFeatureFlagService", pmsFeatureFlagService);

    if (EmptyPredicate.isEmpty(contextMap)) {
      return evaluator;
    }

    for (Map.Entry<String, Object> entry : contextMap.entrySet()) {
      evaluator.addToContext(entry.getKey(), entry.getValue());
    }
    return evaluator;
  }

  public static class SampleEngineExpressionEvaluator extends AmbianceExpressionEvaluator {
    public SampleEngineExpressionEvaluator() {
      super(null, Ambiance.newBuilder().build(), null, false, null);
    }

    @Override
    protected void initialize() {
      super.initialize();
      addStaticAlias("bVal1CVal1", "bVal1.cVal1");
    }

    @NotNull
    protected List<String> fetchPrefixes() {
      return ImmutableList.of("obj", "");
    }

    @Override
    protected Object evaluateInternal(String expression, EngineJexlContext ctx) {
      Object value = super.evaluateInternal(expression, ctx);
      if (value instanceof DummyOrchestrationField) {
        return ((DummyOrchestrationField) value).fetchFinalValue();
      }
      return value;
    }
  }
}
