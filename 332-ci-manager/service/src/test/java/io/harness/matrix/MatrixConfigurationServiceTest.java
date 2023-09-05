/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.matrix;

import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.matrix.MatrixConfigService;
import io.harness.steps.matrix.MatrixConfigServiceHelper;
import io.harness.steps.matrix.StrategyInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MatrixConfigurationServiceTest {
  MatrixConfigService matrixConfigService = new MatrixConfigService(new MatrixConfigServiceHelper());
  private Ambiance getAmbianceForTesting() {
    ExecutionMetadata metadata = ExecutionMetadata.newBuilder()
                                     .putSettingToValueMap("enable_matrix_label_by_name", "true")
                                     .putSettingToValueMap("enable_expression_engine_v2", "false")
                                     .putSettingToValueMap("default_image_pull_policy_for_add_on_container", "Always")
                                     .putSettingToValueMap("enable_node_execution_audit_events", "false")
                                     .build();

    Ambiance ambiance = Ambiance.newBuilder().setMetadata(metadata).build();
    return ambiance;
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassFirst() throws IOException {
    List<String> expectedStepIdentifiers = Arrays.asList("echo_dev", "echo_int");
    testExpandJsonNodeFromClassCommon("matrix-loop-pipeline-1.yaml", 0, 2, expectedStepIdentifiers);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassSecond() throws IOException {
    List<String> expectedStepIdentifiers = Arrays.asList("finalRun_uts_kqwkl_cry", "finalRun_uts_kqwkl_moz",
        "finalRun_uts_kqwkl_xmq", "finalRun_uts_jdewj_cry", "finalRun_uts_jdewj_moz", "finalRun_uts_jdewj_xmq",
        "finalRun_lqv_kqwkl_cry", "finalRun_lqv_kqwkl_moz", "finalRun_lqv_kqwkl_xmq", "finalRun_lqv_jdewj_cry",
        "finalRun_lqv_jdewj_moz", "finalRun_lqv_jdewj_xmq", "finalRun_syt_kqwkl_cry", "finalRun_syt_kqwkl_moz",
        "finalRun_syt_kqwkl_xmq", "finalRun_syt_jdewj_cry", "finalRun_syt_jdewj_moz", "finalRun_syt_jdewj_xmq");
    testExpandJsonNodeFromClassCommon("matrix-loop-pipeline-2.yaml", 4, 18, expectedStepIdentifiers);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassThird() throws IOException {
    List<String> expectedStepIdentifiers = Arrays.asList(
        "echo_cwq_qakx", "echo_cwq_aakq", "echo_brq_qakx", "echo_brq_aakq", "echo_xhgq_qakx", "echo_xhgq_aakq");
    testExpandJsonNodeFromClassCommonForParallelStep("matrix-loop-pipeline-3.yaml", 0, 0, 6, expectedStepIdentifiers);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassFourth() throws IOException {
    List<String> expectedStepIdentifiers =
        Arrays.asList("ExternalEcho_detto", "ExternalEcho_inplm", "ExternalEcho_longst");
    testExpandJsonNodeFromClassCommon("matrix-loop-pipeline-2.yaml", 2, 3, expectedStepIdentifiers);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassFifth() throws IOException {
    List<String> expectedStepIdentifiers =
        Arrays.asList("ShobhitRun_secd_ones", "ShobhitRun_secd_twos", "ShobhitRun_okmf_ones", "ShobhitRun_okmf_twos");

    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("matrix-loop-pipeline-2.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();

    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();
    YamlField stageYamlField = stageYamlNodes.get(0).getField("stage");
    YamlField specYamlField = stageYamlField.getNode().getField("spec");
    YamlField executionField = specYamlField.getNode().getField("execution");
    YamlField stepsField = executionField.getNode().getField("steps");

    List<YamlNode> stepYamlNodes = stepsField.getNode().asArray();
    YamlField parallelStepYamlField = stepYamlNodes.get(1).getField("parallel");
    List<YamlNode> parallelStepYamlNode = parallelStepYamlField.getNode().asArray();
    YamlField stepGroupYamlField = parallelStepYamlNode.get(0).getField("stepGroup");
    YamlField stepsFieldInsideStepGroup = stepGroupYamlField.getNode().getField("steps");
    List<YamlNode> stepsYamlNodeInsideStepGroup = stepsFieldInsideStepGroup.getNode().asArray();
    YamlField stepYamlField = stepsYamlNodeInsideStepGroup.get(1).getField("step");
    YamlField strategyField = stepYamlField.getNode().getField("strategy");
    StrategyConfig strategyConfig = YamlUtils.read(strategyField.getNode().toString(), StrategyConfig.class);

    Ambiance ambiance = getAmbianceForTesting();
    Optional<Integer> maxExpansion = Optional.of(10000);
    StrategyInfo strategyInfo = matrixConfigService.expandJsonNodeFromClass(
        strategyConfig, stepYamlField.getNode().getCurrJsonNode(), maxExpansion, false, RunStepNode.class, ambiance);
    assertThat(strategyInfo).isNotNull();
    assertThat(strategyInfo.getExpandedJsonNodes().size()).isEqualTo(4);
    List<String> stepIdentifiers = strategyInfo.getExpandedJsonNodes()
                                       .stream()
                                       .map(jsonField -> jsonField.get("identifier"))
                                       .map(JsonNode::asText)
                                       .collect(Collectors.toList());
    assertThat(expectedStepIdentifiers.equals(stepIdentifiers)).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassSixth() throws IOException {
    List<String> expectedStepIdentifiers = Arrays.asList("Run_doll_fan_apple", "Run_doll_fan_banana",
        "Run_doll_fan_coconut", "Run_doll_gun_apple", "Run_doll_gun_banana", "Run_doll_gun_coconut",
        "Run_elephant_fan_apple", "Run_elephant_fan_banana", "Run_elephant_fan_coconut", "Run_elephant_gun_apple",
        "Run_elephant_gun_banana", "Run_elephant_gun_coconut");
    testExpandJsonNodeFromClassCommonForParallelStep("matrix-loop-pipeline-2.yaml", 0, 0, 12, expectedStepIdentifiers);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassSeventh() throws IOException {
    List<String> expectedStepIdentifiers =
        Arrays.asList("echoParallel_ejq_djq", "echoParallel_wjh_djq", "echoParallel_ejq_lsa", "echoParallel_wjh_lsa");
    testExpandJsonNodeFromClassCommonForParallelStep("matrix-loop-pipeline-3.yaml", 0, 1, 4, expectedStepIdentifiers);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassEighth() throws IOException {
    List<String> expectedStepIdentifiers =
        Arrays.asList("Run_3_ejq_djq", "Run_3_wjh_djq", "Run_3_ejq_lsa", "Run_3_wjh_lsa");
    testExpandJsonNodeFromClassCommon("matrix-loop-pipeline-3.yaml", 1, 4, expectedStepIdentifiers);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassNinth() throws IOException {
    List<String> expectedStepIdentifiers = Arrays.asList("InnerRun_inting");

    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("matrix-loop-pipeline-2.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();

    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();
    YamlField stageYamlField = stageYamlNodes.get(0).getField("stage");
    YamlField specYamlField = stageYamlField.getNode().getField("spec");
    YamlField executionField = specYamlField.getNode().getField("execution");
    YamlField stepsField = executionField.getNode().getField("steps");

    List<YamlNode> stepYamlNodes = stepsField.getNode().asArray();
    YamlField stepGroupYamlField = stepYamlNodes.get(3).getField("stepGroup");
    YamlField stepsYamlField = stepGroupYamlField.getNode().getField("steps");
    List<YamlNode> stepsYamlNode = stepsYamlField.getNode().asArray();
    YamlField stepGroupYamlFieldInsideStepsYamlNode = stepsYamlNode.get(0).getField("stepGroup");
    YamlField stepsYamlFieldInsideStepGroup = stepGroupYamlFieldInsideStepsYamlNode.getNode().getField("steps");

    List<YamlNode> stepsYamlNodeInsideStepGroup = stepsYamlFieldInsideStepGroup.getNode().asArray();
    YamlField parallelYamlField = stepsYamlNodeInsideStepGroup.get(0).getField("parallel");
    List<YamlNode> parallelStepYamlNode = parallelYamlField.getNode().asArray();
    YamlField parallelStepYamlField = parallelStepYamlNode.get(0).getField("step");
    YamlField strategyField = parallelStepYamlField.getNode().getField("strategy");
    StrategyConfig strategyConfig = YamlUtils.read(strategyField.getNode().toString(), StrategyConfig.class);

    Ambiance ambiance = getAmbianceForTesting();
    Optional<Integer> maxExpansion = Optional.of(10000);
    StrategyInfo strategyInfo = matrixConfigService.expandJsonNodeFromClass(strategyConfig,
        parallelStepYamlField.getNode().getCurrJsonNode(), maxExpansion, false, RunStepNode.class, ambiance);
    assertThat(strategyInfo).isNotNull();
    assertThat(strategyInfo.getExpandedJsonNodes().size()).isEqualTo(1);
    List<String> stepIdentifiers = strategyInfo.getExpandedJsonNodes()
                                       .stream()
                                       .map(jsonField -> jsonField.get("identifier"))
                                       .map(JsonNode::asText)
                                       .collect(Collectors.toList());
    assertThat(expectedStepIdentifiers.equals(stepIdentifiers)).isEqualTo(true);
  }

  private void testExpandJsonNodeFromClassCommon(String yamlFile, int stepYamlNodeIndex, int sizeForAssertionCheck,
      List<String> expectedStepIdentifiers) throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource(yamlFile);
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();

    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();
    YamlField stageYamlField = stageYamlNodes.get(0).getField("stage");
    YamlField specYamlField = stageYamlField.getNode().getField("spec");
    YamlField executionField = specYamlField.getNode().getField("execution");
    YamlField stepsField = executionField.getNode().getField("steps");

    List<YamlNode> stepYamlNodes = stepsField.getNode().asArray();
    YamlField stepYamlField = stepYamlNodes.get(stepYamlNodeIndex).getField("step");
    YamlField strategyField = stepYamlField.getNode().getField("strategy");
    StrategyConfig strategyConfig = YamlUtils.read(strategyField.getNode().toString(), StrategyConfig.class);

    Ambiance ambiance = getAmbianceForTesting();
    Optional<Integer> maxExpansion = Optional.of(10000);
    StrategyInfo strategyInfo = matrixConfigService.expandJsonNodeFromClass(
        strategyConfig, stepYamlField.getNode().getCurrJsonNode(), maxExpansion, false, RunStepNode.class, ambiance);
    assertThat(strategyInfo).isNotNull();
    assertThat(strategyInfo.getExpandedJsonNodes().size()).isEqualTo(sizeForAssertionCheck);
    List<String> stepIdentifiers = strategyInfo.getExpandedJsonNodes()
                                       .stream()
                                       .map(jsonField -> jsonField.get("identifier"))
                                       .map(JsonNode::asText)
                                       .collect(Collectors.toList());
    assertThat(expectedStepIdentifiers.equals(stepIdentifiers)).isEqualTo(true);
  }

  private void testExpandJsonNodeFromClassCommonForParallelStep(String yamlFile, int stepYamlNodeIndex,
      int parallelStepYamlNodeIndex, int sizeForAssertionCheck, List<String> expectedStepIdentifiers)
      throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource(yamlFile);
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);
    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();

    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();
    YamlField stageYamlField = stageYamlNodes.get(0).getField("stage");
    YamlField specYamlField = stageYamlField.getNode().getField("spec");
    YamlField executionField = specYamlField.getNode().getField("execution");
    YamlField stepsField = executionField.getNode().getField("steps");

    List<YamlNode> stepYamlNodes = stepsField.getNode().asArray();
    YamlField parallelStepField = stepYamlNodes.get(stepYamlNodeIndex).getField("parallel");
    List<YamlNode> parallelStepYamlNode = parallelStepField.getNode().asArray();
    YamlField parallelStepYamlField = parallelStepYamlNode.get(parallelStepYamlNodeIndex).getField("step");
    YamlField strategyField = parallelStepYamlField.getNode().getField("strategy");
    StrategyConfig strategyConfig = YamlUtils.read(strategyField.getNode().toString(), StrategyConfig.class);

    Ambiance ambiance = getAmbianceForTesting();
    Optional<Integer> maxExpansion = Optional.of(10000);
    StrategyInfo strategyInfo = matrixConfigService.expandJsonNodeFromClass(strategyConfig,
        parallelStepYamlField.getNode().getCurrJsonNode(), maxExpansion, false, RunStepNode.class, ambiance);
    assertThat(strategyInfo).isNotNull();
    assertThat(strategyInfo.getExpandedJsonNodes().size()).isEqualTo(sizeForAssertionCheck);
    List<String> stepIdentifiers = strategyInfo.getExpandedJsonNodes()
                                       .stream()
                                       .map(jsonField -> jsonField.get("identifier"))
                                       .map(JsonNode::asText)
                                       .collect(Collectors.toList());
    assertThat(expectedStepIdentifiers.equals(stepIdentifiers)).isEqualTo(true);
  }
}