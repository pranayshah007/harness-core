/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.expressions.YamlExpressionEvaluator;
import io.harness.pms.plan.execution.beans.dto.ExpressionEvaluationDetail;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(PIPELINE)
public class ExpressionEvaluatorServiceImplTest extends CategoryTest {
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PmsEngineExpressionService engineExpressionService;

  @InjectMocks ExpressionEvaluatorServiceImpl expressionEvaluatorService;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PIPELINE_IDENTIFIER = "basichttpFail";
  private final String PLAN_EXECUTION_ID = "planId";
  private final List<String> PIPELINE_IDENTIFIER_LIST = Arrays.asList(PIPELINE_IDENTIFIER);

  String yaml = "pipeline:\n"
      + "  identifier: trialselective\n"
      + "  name: testName\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: Test1\n"
      + "        type: Custom\n"
      + "        spec:\n"
      + "          execution:\n"
      + "            steps:\n"
      + "              - step:\n"
      + "                  identifier: Wait_1\n"
      + "                  type: Wait\n"
      + "                  spec:\n"
      + "                    duration: 1m\n"
      + "    - parallel:\n"
      + "        - stage:\n"
      + "            identifier: test2\n"
      + "            type: Custom\n"
      + "            spec:\n"
      + "              execution:\n"
      + "                steps:\n"
      + "                  - step:\n"
      + "                      identifier: Wait_1\n"
      + "                      type: Wait\n"
      + "                      spec:\n"
      + "                        duration: 1m\n";

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testResolveFromYaml() {
    YamlExpressionEvaluator yamlExpressionEvaluator = new YamlExpressionEvaluator(yaml);
    String result = expressionEvaluatorService.resolveFromYaml(yamlExpressionEvaluator, "<+pipeline.name>");
    assertThat(result).isEqualTo("testName");

    result = expressionEvaluatorService.resolveFromYaml(
        yamlExpressionEvaluator, "<+pipeline.stages.Test1.spec.execution.steps.Wait_1.spec.duration>");
    assertThat(result).isEqualTo("1m");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testResolveFromAmbiance() {
    doReturn("dummy").when(engineExpressionService).resolve(any(), any(), any());
    String result =
        expressionEvaluatorService.resolveValueFromAmbiance(Ambiance.newBuilder().build(), "<+pipeline.name");
    assertThat(result).isEqualTo("dummy");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetExpressionForYamlEvaluator() {
    String fqn = "pipeline.stages.Test1.spec.execution.steps.Wait_2.spec.duration";
    String expression = "<+execution.steps.Wait_1.spec.duration>";

    // Test expression from part of yaml
    String result = expressionEvaluatorService.getExpressionForYamlEvaluator(fqn, expression);
    assertThat(result).isEqualTo("<+pipeline.stages.Test1.spec.execution.steps.Wait_1.spec.duration>");

    // Test alias expression
    result = expressionEvaluatorService.getExpressionForYamlEvaluator(fqn, "<+artifact.name>");
    assertThat(result).isEqualTo("<+artifact.name>");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testEvaluateExpression() {
    String fqn = "pipeline.stages.Test1.spec.execution.steps.Wait_2.spec.duration";
    String expression = "<+execution.steps.Wait_1.spec.duration>";
    String staticExpression = "<+artifact.name>";
    Map<String, ExpressionEvaluationDetail> detailMap = new HashMap<>();

    // Test resolveByYaml
    expressionEvaluatorService.evaluateExpression(
        detailMap, Ambiance.newBuilder().build(), fqn, expression, new YamlExpressionEvaluator(yaml));

    String key = fqn + "+" + expression;
    assertThat(detailMap.containsKey(key)).isTrue();
    assertThat(detailMap.get(key).isResolvedByYaml()).isTrue();

    // Test resolve by ambiance
    doReturn("dummy").when(engineExpressionService).resolve(any(), any(), any());
    detailMap = new HashMap<>();
    expressionEvaluatorService.evaluateExpression(
        detailMap, Ambiance.newBuilder().build(), fqn, staticExpression, new YamlExpressionEvaluator(yaml));
    key = fqn + "+" + staticExpression;
    assertThat(detailMap.containsKey(key)).isTrue();
    assertThat(detailMap.get(key).isResolvedByYaml()).isFalse();
    assertThat(detailMap.get(key).getResolvedValue()).isEqualTo("dummy");
  }

  public List<Level> prepareLevel() {
    List<Level> levels = new ArrayList<>();
    levels.add(Level.newBuilder().setIdentifier("pipeline").setGroup("pipeline").build());
    levels.add(Level.newBuilder().setIdentifier("stages").setGroup("stages").build());
    levels.add(Level.newBuilder().setIdentifier("stage1").setGroup("stage1").build());
    levels.add(Level.newBuilder().setIdentifier("execution").build());
    levels.add(Level.newBuilder().setIdentifier("steps").setGroup("steps").build());
    levels.add(Level.newBuilder().setIdentifier("step1").setGroup("step").build());
    levels.add(Level.newBuilder().setIdentifier("source").build());
    levels.add(Level.newBuilder().setIdentifier("timeout").build());

    return levels;
  }
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetFQNToAmbianceMap() {
    Ambiance ambiance = Ambiance.newBuilder().addAllLevels(prepareLevel()).build();
    String expectedFqn = "pipeline.stages.stage1.execution.steps.step1";
    Map<String, Ambiance> fqnToAmbianceMap = expressionEvaluatorService.getFQNToAmbianceMap(
        Collections.singletonList(NodeExecution.builder().ambiance(ambiance).build()));
    assertThat(fqnToAmbianceMap.containsKey(expectedFqn)).isTrue();
    assertThat(fqnToAmbianceMap.get(expectedFqn)).isEqualTo(ambiance);
  }
}
