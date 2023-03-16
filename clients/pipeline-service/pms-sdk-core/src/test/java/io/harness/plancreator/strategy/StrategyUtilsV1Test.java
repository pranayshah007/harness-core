/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import static io.harness.plancreator.strategy.StrategyConstants.CURRENT_GLOBAL_ITERATION;
import static io.harness.plancreator.strategy.StrategyConstants.TOTAL_GLOBAL_ITERATIONS;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.category.element.UnitTests;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext.PlanCreationContextBuilder;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StrategyUtilsV1Test extends PmsSdkCoreTestBase {
  @Mock KryoSerializer kryoSerializer;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testIsWrappedUnderStrategy() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy-v1.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid);
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField stageWithStrategy = new YamlField(stageYamlNodes.get(0));
    YamlField stageWithoutStrategy = new YamlField(stageYamlNodes.get(1));
    assertThat(StrategyUtilsV1.isWrappedUnderStrategy(stageWithStrategy)).isTrue();
    assertThat(StrategyUtilsV1.isWrappedUnderStrategy(stageWithoutStrategy)).isFalse();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSwappedPlanNodeId() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy-v1.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid);
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField stageWithStrategy = new YamlField(stageYamlNodes.get(0));
    YamlField stageWithoutStrategy = new YamlField(stageYamlNodes.get(1));

    PlanCreationContextBuilder contextBuilder = PlanCreationContext.builder();
    assertThat(StrategyUtilsV1.getSwappedPlanNodeId(
                   contextBuilder.currentField(stageWithStrategy).build(), "originalPlanNodeId"))
        .isEqualTo(stageWithStrategy.getNode().getField("strategy").getNode().getUuid());
    assertThat(StrategyUtilsV1.getSwappedPlanNodeId(
                   contextBuilder.currentField(stageWithoutStrategy).build(), "originalPlanNodeId"))
        .isEqualTo("originalPlanNodeId");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetIdentifierWithExpression() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy-v1.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid);
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField stageWithStrategy = new YamlField(stageYamlNodes.get(0));
    YamlField stageWithoutStrategy = new YamlField(stageYamlNodes.get(1));

    PlanCreationContextBuilder contextBuilder = PlanCreationContext.builder();
    assertThat(
        StrategyUtilsV1.getIdentifierWithExpression(contextBuilder.currentField(stageWithStrategy).build(), "id"))
        .isEqualTo("id<+strategy.identifierPostFix>");
    assertThat(
        StrategyUtilsV1.getIdentifierWithExpression(contextBuilder.currentField(stageWithoutStrategy).build(), "id"))
        .isEqualTo("id");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testModifyStageLayoutNodeGraph() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy-v1.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid);
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();
    YamlField stageWithStrategy = new YamlField(stageYamlNodes.get(0));

    assertThat(StrategyUtilsV1.isWrappedUnderStrategy(stageWithStrategy)).isTrue();
    assertThat(StrategyUtilsV1.modifyStageLayoutNodeGraph(stageWithStrategy, "nextNodeId").size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testAddStrategyFieldDependencyIfPresent() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy-v1.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid);
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();
    LinkedHashMap<String, YamlField> dependenciesMap = new LinkedHashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();
    YamlField stageWithStrategy = new YamlField(stageYamlNodes.get(0));
    YamlField stageWithoutStrategy = new YamlField(stageYamlNodes.get(1));
    doReturn("".getBytes(StandardCharsets.UTF_8)).when(kryoSerializer).asDeflatedBytes(any());
    // Stage does not have any strategy. So metadata map and dependencyMap both will be empty.
    StrategyUtilsV1.addStrategyFieldDependencyIfPresent(kryoSerializer,
        PlanCreationContext.builder().currentField(stageWithoutStrategy).build(), stageWithStrategy.getNode().getName(),
        dependenciesMap, metadataMap, new ArrayList<>());
    assertThat(metadataMap.size()).isEqualTo(0);
    assertThat(dependenciesMap.size()).isEqualTo(0);

    // Stage has the strategy. So metadata map and dependencyMap will have size 1.
    StrategyUtilsV1.addStrategyFieldDependencyIfPresent(kryoSerializer,
        PlanCreationContext.builder().currentField(stageWithStrategy).build(), stageWithStrategy.getNode().getName(),
        dependenciesMap, metadataMap, new ArrayList<>());
    assertThat(metadataMap.size()).isEqualTo(1);
    assertThat(dependenciesMap.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testFetchGlobalIterationsVariablesForStrategyObjectMap() {
    Map<String, Object> strategyObjectMap = new HashMap<>();
    List<IterationVariables> levels = new ArrayList<>();
    levels.add(IterationVariables.builder().currentIteration(1).totalIterations(3).build());
    levels.add(IterationVariables.builder().currentIteration(1).totalIterations(3).build());
    levels.add(IterationVariables.builder().currentIteration(1).totalIterations(3).build());

    StrategyUtils.fetchGlobalIterationsVariablesForStrategyObjectMap(strategyObjectMap, levels);

    assertThat(strategyObjectMap.get(CURRENT_GLOBAL_ITERATION)).isEqualTo(13);
    assertThat(strategyObjectMap.get(TOTAL_GLOBAL_ITERATIONS)).isEqualTo(27);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testFetchGlobalIterationsVariablesForStrategyObjectMapFirstItr() {
    Map<String, Object> strategyObjectMap = new HashMap<>();
    List<IterationVariables> levels = new ArrayList<>();
    levels.add(IterationVariables.builder().currentIteration(0).totalIterations(3).build());
    levels.add(IterationVariables.builder().currentIteration(0).totalIterations(3).build());
    levels.add(IterationVariables.builder().currentIteration(0).totalIterations(3).build());

    StrategyUtils.fetchGlobalIterationsVariablesForStrategyObjectMap(strategyObjectMap, levels);

    assertThat(strategyObjectMap.get(CURRENT_GLOBAL_ITERATION)).isEqualTo(0);
    assertThat(strategyObjectMap.get(TOTAL_GLOBAL_ITERATIONS)).isEqualTo(27);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testFetchGlobalIterationsVariablesForStrategyObjectMapRandomItr() {
    Map<String, Object> strategyObjectMap = new HashMap<>();
    List<IterationVariables> levels = new ArrayList<>();
    levels.add(IterationVariables.builder().currentIteration(0).totalIterations(3).build());
    levels.add(IterationVariables.builder().currentIteration(1).totalIterations(3).build());
    levels.add(IterationVariables.builder().currentIteration(2).totalIterations(3).build());

    StrategyUtils.fetchGlobalIterationsVariablesForStrategyObjectMap(strategyObjectMap, levels);

    assertThat(strategyObjectMap.get(CURRENT_GLOBAL_ITERATION)).isEqualTo(5);
    assertThat(strategyObjectMap.get(TOTAL_GLOBAL_ITERATIONS)).isEqualTo(27);
  }
}
