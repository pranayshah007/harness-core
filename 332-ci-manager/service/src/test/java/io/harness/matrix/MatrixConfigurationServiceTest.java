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

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassTenth() throws IOException {
    List<String> expectedIdentifiers = Arrays.asList(
        "Run_1___names____bla___abcd___ef______azsd____kaq___lale___nc______dca____wdpr___sder___ks____wqd",
        "Run_1___names____bla___abcd___ef____fsw___dca____wdpr___sder___ks____wqd",
        "Run_1___names____bla___abcd___ef______azsd____kaq___lale___nc____dkw_wqd",
        "Run_1___names____bla___abcd___ef____fsw_dkw_wqd",
        "Run_1___names____bla___abcd___ef______azsd____kaq___lale___nc______spl____s___blahvalue____wqd",
        "Run_1___names____bla___abcd___ef____fsw___spl____s___blahvalue____wqd",
        "Run_1___kdkq____kql___d______azsd____kaq___lale___nc______dca____wdpr___sder___ks____wqd",
        "Run_1___kdkq____kql___d____fsw___dca____wdpr___sder___ks____wqd",
        "Run_1___kdkq____kql___d______azsd____kaq___lale___nc____dkw_wqd", "Run_1___kdkq____kql___d____fsw_dkw_wqd",
        "Run_1___kdkq____kql___d______azsd____kaq___lale___nc______spl____s___blahvalue____wqd",
        "Run_1___kdkq____kql___d____fsw___spl____s___blahvalue____wqd",
        "Run_1___names____bla___abcd___ef______azsd____kaq___lale___nc______dca____wdpr___sder___ks____wple",
        "Run_1___names____bla___abcd___ef____fsw___dca____wdpr___sder___ks____wple",
        "Run_1___names____bla___abcd___ef______azsd____kaq___lale___nc____dkw_wple",
        "Run_1___names____bla___abcd___ef____fsw_dkw_wple",
        "Run_1___names____bla___abcd___ef______azsd____kaq___lale___nc______spl____s___blahvalue____wple",
        "Run_1___names____bla___abcd___ef____fsw___spl____s___blahvalue____wple",
        "Run_1___kdkq____kql___d______azsd____kaq___lale___nc______dca____wdpr___sder___ks____wple",
        "Run_1___kdkq____kql___d____fsw___dca____wdpr___sder___ks____wple",
        "Run_1___kdkq____kql___d______azsd____kaq___lale___nc____dkw_wple", "Run_1___kdkq____kql___d____fsw_dkw_wple",
        "Run_1___kdkq____kql___d______azsd____kaq___lale___nc______spl____s___blahvalue____wple",
        "Run_1___kdkq____kql___d____fsw___spl____s___blahvalue____wple");
    testExpandJsonNodeFromClassCommon("matrix-loop-pipeline-4.yaml", 0, 24, expectedIdentifiers);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassEleventh() throws IOException {
    List<String> expectedStepIdentifiers = Arrays.asList("Run_3_abc___qlo____s___q___", "Run_3_abc___qs____q___r___",
        "Run_3_abc_wsq", "Run_3_abca___qlo____s___q___", "Run_3_abca___qs____q___r___", "Run_3_abca_wsq",
        "Run_3_abcb___qlo____s___q___", "Run_3_abcb___qs____q___r___", "Run_3_abcb_wsq");
    testExpandJsonNodeFromClassCommonForParallelStep("matrix-loop-pipeline-4.yaml", 2, 1, 9, expectedStepIdentifiers);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassTwelfth() throws IOException {
    List<String> expectedStepIdentifiers = Arrays.asList("Run_5_aba_abd", "Run_5_abc_abd", "Run_5_abc_abd_0",
        "Run_5_aba_abc", "Run_5_abc_abc", "Run_5_abc_abc_0", "Run_5_aba_abc_0", "Run_5_abc_abc_1", "Run_5_abc_abc_2");
    testExpandJsonNodeFromClassCommon("matrix-loop-pipeline-4.yaml", 1, 9, expectedStepIdentifiers);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassThirteenth() throws IOException {
    List<String> expectedStepIdentifiers = Arrays.asList("Run_4_a_ed", "Run_4_a_ed_0", "Run_4_a_ed_1", "Run_4_a_ed_2",
        "Run_4_a_ed_3", "Run_4_a_ed_4", "Run_4_b_ed", "Run_4_b_ed_0", "Run_4_b_ed_1", "Run_4_b_ed_2", "Run_4_b_ed_3",
        "Run_4_b_ed_4", "Run_4_c_ed", "Run_4_c_ed_0", "Run_4_c_ed_1", "Run_4_c_ed_2", "Run_4_c_ed_3", "Run_4_c_ed_4",
        "Run_4_a_gf", "Run_4_a_gf_0", "Run_4_a_gf_1", "Run_4_a_gf_2", "Run_4_a_gf_3", "Run_4_a_gf_4", "Run_4_b_gf",
        "Run_4_b_gf_0", "Run_4_b_gf_1", "Run_4_b_gf_2", "Run_4_b_gf_3", "Run_4_b_gf_4", "Run_4_c_gf", "Run_4_c_gf_0",
        "Run_4_c_gf_1", "Run_4_c_gf_2", "Run_4_c_gf_3", "Run_4_c_gf_4");
    testExpandJsonNodeFromClassCommonForParallelStep("matrix-loop-pipeline-4.yaml", 3, 0, 36, expectedStepIdentifiers);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testExpandJsonNodeFromClassFourteenth() throws IOException {
    List<String> expectedStepIdentifiers = Arrays.asList("Run_6_harness_hello", "Run_6_google_hello",
        "Run_6_amazon_hello", "Run_6_harness___name____shobhit___dev___sahil___ved___",
        "Run_6_google___name____shobhit___dev___sahil___ved___",
        "Run_6_amazon___name____shobhit___dev___sahil___ved___", "Run_6_harness___age___23_26_28_25__",
        "Run_6_google___age___23_26_28_25__", "Run_6_amazon___age___23_26_28_25__",
        "Run_6_harness___gender____male___female___", "Run_6_google___gender____male___female___",
        "Run_6_amazon___gender____male___female___");
    testExpandJsonNodeFromClassCommonForParallelStep("matrix-loop-pipeline-4.yaml", 3, 1, 12, expectedStepIdentifiers);
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