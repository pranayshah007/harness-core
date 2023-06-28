/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.helpers;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.expressions.AmbianceExpressionEvaluator;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.InputSetValidatorFactory;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class YamlExpressionResolveHelperTest extends CategoryTest {
  @Mock private PlanExecutionService planExecutionService;
  @Inject private InputSetValidatorFactory inputSetValidatorFactory;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private PmsEngineExpressionService pmsEngineExpressionService;

  @InjectMocks YamlExpressionResolveHelper yamlExpressionResolveHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void resolveExpressionForArrayElement() throws IOException {
    String arrayTypeString = "pipeline: \n"
        + " name: pipelineName\n"
        + " delegateSelector: \n"
        + "   - value1\n"
        + "   - <+pipeline.name>\n";

    EngineExpressionEvaluator expressionEvaluator =
        prepareEngineExpressionEvaluator(YamlUtils.read(arrayTypeString, Map.class));
    YamlField yamlField = YamlUtils.readTree(arrayTypeString);
    YamlNode parentNode = yamlField.getNode().getField("pipeline").getNode().getField("delegateSelector").getNode();
    ArrayNode parentArrayNode = (ArrayNode) parentNode.getCurrJsonNode();

    // case1: value passed is not expression
    yamlExpressionResolveHelper.resolveExpressionForArrayElement(
        parentNode, 0, parentArrayNode.get(0).textValue(), expressionEvaluator);
    assertThat(parentArrayNode.get(0).textValue()).isEqualTo("value1");

    // case2: value passed is expression
    yamlExpressionResolveHelper.resolveExpressionForArrayElement(
        parentNode, 1, parentArrayNode.get(1).textValue(), expressionEvaluator);
    assertThat(parentArrayNode.get(1).textValue()).isEqualTo("pipelineName");
    assertThat(parentArrayNode.get(0).textValue()).isEqualTo("value1");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void resolveExpressionsInYamlTestWithNullNodeExecution() {
    Optional<NodeExecution> nodeExecution = Optional.ofNullable(null);
    doReturn(nodeExecution).when(nodeExecutionService).getPipelineNodeExecutionWithProjections(any(), any());
    Assertions
        .assertThatCode(()
                            -> yamlExpressionResolveHelper.resolveExpressionsInYaml(
                                "", "planExecutionId", ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid request : No execution details found");
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void resolveExpressionsInYamlTest() throws IOException {
    String arrayTypeString = "pipeline: \n"
        + " name: pipelineName\n"
        + " delegateSelector: \n"
        + "   - value1\n"
        + "   - <+pipeline.name>\n";
    EngineExpressionEvaluator expressionEvaluator =
        prepareEngineExpressionEvaluator(YamlUtils.read(arrayTypeString, Map.class));
    Optional<NodeExecution> nodeExecution =
        Optional.ofNullable(NodeExecution.builder().ambiance(Ambiance.newBuilder().build()).build());
    doReturn(nodeExecution).when(nodeExecutionService).getPipelineNodeExecutionWithProjections(any(), any());
    doReturn(expressionEvaluator).when(pmsEngineExpressionService).prepareExpressionEvaluator(any());
    assertThatCode(()
                       -> yamlExpressionResolveHelper.resolveExpressionsInYaml(
                           arrayTypeString, "planExecutionId", ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS))
        .doesNotThrowAnyException();
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
      evaluator.addToContextMap(entry.getKey(), entry.getValue());
    }
    return evaluator;
  }

  public static class SampleEngineExpressionEvaluator extends AmbianceExpressionEvaluator {
    public SampleEngineExpressionEvaluator() {
      super(null, Ambiance.newBuilder().build(), null, false, null);
    }

    public void addToContextMap(@NotNull String name, @NotNull Object object) {
      addToContext(name, object);
    }
    @Override
    protected void initialize() {
      super.initialize();
    }

    @NotNull
    protected List<String> fetchPrefixes() {
      return ImmutableList.of("obj", "");
    }
  }
}
