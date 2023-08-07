/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.NGCommonUtilitiesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class StrategyHelperTest extends NGCommonUtilitiesTestBase {
  private static final String JSON_FOR_STEP_EXPANSION = "  - __uuid: step1\n"
      + "    step:\n"
      + "      type: Http\n"
      + "      strategy:\n"
      + "        parallelism: 4\n"
      + "      spec:\n"
      + "        url: https://www.google.com\n"
      + "        method: GET\n"
      + "        headers: []\n"
      + "        outputVariables: []\n"
      + "      timeout: 1m\n"
      + "      identifier: \"0_0\"\n"
      + "      name: \"4_0\"\n"
      + "  - __uuid: step1\n"
      + "    step:\n"
      + "      type: Http\n"
      + "      strategy:\n"
      + "        parallelism: 4\n"
      + "      spec:\n"
      + "        url: https://www.google.com\n"
      + "        method: GET\n"
      + "        headers: []\n"
      + "        outputVariables: []\n"
      + "      timeout: 1m\n"
      + "      identifier: \"1_1\"\n"
      + "      name: \"4_1\"\n"
      + "  - __uuid: step1\n"
      + "    step:\n"
      + "      type: Http\n"
      + "      strategy:\n"
      + "        parallelism: 4\n"
      + "      spec:\n"
      + "        url: https://www.google.com\n"
      + "        method: GET\n"
      + "        headers: []\n"
      + "        outputVariables: []\n"
      + "      timeout: 1m\n"
      + "      identifier: \"2_2\"\n"
      + "      name: \"4_2\"\n"
      + "  - __uuid: step1\n"
      + "    step:\n"
      + "      type: Http\n"
      + "      strategy:\n"
      + "        parallelism: 4\n"
      + "      spec:\n"
      + "        url: https://www.google.com\n"
      + "        method: GET\n"
      + "        headers: []\n"
      + "        outputVariables: []\n"
      + "      timeout: 1m\n"
      + "      identifier: \"3_3\"\n"
      + "      name: \"4_3\"\n";

  private static final String JSON_FOR_STEP_GROUP_EXPANSION = "  - __uuid: stepGroupParent\n"
      + "    stepGroup:\n"
      + "      __uuid: sg1\n"
      + "      identifier: searching\n"
      + "      name: searching\n"
      + "      steps:\n"
      + "        - parallel:\n"
      + "            - __uuid: parallel1\n"
      + "              step:\n"
      + "                __uuid: step1\n"
      + "                type: Http\n"
      + "                strategy:\n"
      + "                  parallelism: 4\n"
      + "                spec:\n"
      + "                  url: https://www.google.com\n"
      + "                  method: GET\n"
      + "                  headers: []\n"
      + "                  outputVariables: []\n"
      + "                timeout: 1m\n"
      + "                identifier: \"0_0\"\n"
      + "                name: \"4_0\"\n"
      + "            - __uuid: parallel1\n"
      + "              step:\n"
      + "                __uuid: step1\n"
      + "                type: Http\n"
      + "                strategy:\n"
      + "                  parallelism: 4\n"
      + "                spec:\n"
      + "                  url: https://www.google.com\n"
      + "                  method: GET\n"
      + "                  headers: []\n"
      + "                  outputVariables: []\n"
      + "                timeout: 1m\n"
      + "                identifier: \"1_1\"\n"
      + "                name: \"4_1\"\n"
      + "            - __uuid: parallel1\n"
      + "              step:\n"
      + "                __uuid: step1\n"
      + "                type: Http\n"
      + "                strategy:\n"
      + "                  parallelism: 4\n"
      + "                spec:\n"
      + "                  url: https://www.google.com\n"
      + "                  method: GET\n"
      + "                  headers: []\n"
      + "                  outputVariables: []\n"
      + "                timeout: 1m\n"
      + "                identifier: \"2_2\"\n"
      + "                name: \"4_2\"\n"
      + "            - __uuid: parallel1\n"
      + "              step:\n"
      + "                __uuid: step1\n"
      + "                type: Http\n"
      + "                strategy:\n"
      + "                  parallelism: 4\n"
      + "                spec:\n"
      + "                  url: https://www.google.com\n"
      + "                  method: GET\n"
      + "                  headers: []\n"
      + "                  outputVariables: []\n"
      + "                timeout: 1m\n"
      + "                identifier: \"3_3\"\n"
      + "                name: \"4_3\"\n"
      + "            - __uuid: parallel2\n"
      + "              step:\n"
      + "                __uuid: step3\n"
      + "                type: Http\n"
      + "                name: bing\n"
      + "                identifier: bing\n"
      + "                spec:\n"
      + "                  url: https://www.bing.com\n"
      + "                  method: GET\n"
      + "                  headers: []\n"
      + "                  outputVariables: []\n"
      + "                timeout: 10s\n"
      + "        - __uuid: step4\n"
      + "          step:\n"
      + "            type: Http\n"
      + "            strategy:\n"
      + "              parallelism: 4\n"
      + "            spec:\n"
      + "              url: https://www.google.com\n"
      + "              method: GET\n"
      + "              headers: []\n"
      + "              outputVariables: []\n"
      + "            timeout: 1m\n"
      + "            identifier: \"0_0\"\n"
      + "            name: \"4_0\"\n"
      + "        - __uuid: step4\n"
      + "          step:\n"
      + "            type: Http\n"
      + "            strategy:\n"
      + "              parallelism: 4\n"
      + "            spec:\n"
      + "              url: https://www.google.com\n"
      + "              method: GET\n"
      + "              headers: []\n"
      + "              outputVariables: []\n"
      + "            timeout: 1m\n"
      + "            identifier: \"1_1\"\n"
      + "            name: \"4_1\"\n"
      + "        - __uuid: step4\n"
      + "          step:\n"
      + "            type: Http\n"
      + "            strategy:\n"
      + "              parallelism: 4\n"
      + "            spec:\n"
      + "              url: https://www.google.com\n"
      + "              method: GET\n"
      + "              headers: []\n"
      + "              outputVariables: []\n"
      + "            timeout: 1m\n"
      + "            identifier: \"2_2\"\n"
      + "            name: \"4_2\"\n"
      + "        - __uuid: step4\n"
      + "          step:\n"
      + "            type: Http\n"
      + "            strategy:\n"
      + "              parallelism: 4\n"
      + "            spec:\n"
      + "              url: https://www.google.com\n"
      + "              method: GET\n"
      + "              headers: []\n"
      + "              outputVariables: []\n"
      + "            timeout: 1m\n"
      + "            identifier: \"3_3\"\n"
      + "            name: \"4_3\"\n";

  private static final String EXPECTED_JSON_FOR_PARALLEL = "  - parallel:\n"
      + "      - __uuid: parallel1\n"
      + "        step:\n"
      + "          __uuid: test\n"
      + "          type: Http\n"
      + "          strategy:\n"
      + "            parallelism: 4\n"
      + "          spec:\n"
      + "            url: https://www.google.com\n"
      + "            method: GET\n"
      + "            headers: []\n"
      + "            outputVariables: []\n"
      + "          timeout: 1m\n"
      + "          identifier: \"0_0\"\n"
      + "          name: \"4_0\"\n"
      + "      - __uuid: parallel1\n"
      + "        step:\n"
      + "          __uuid: test\n"
      + "          type: Http\n"
      + "          strategy:\n"
      + "            parallelism: 4\n"
      + "          spec:\n"
      + "            url: https://www.google.com\n"
      + "            method: GET\n"
      + "            headers: []\n"
      + "            outputVariables: []\n"
      + "          timeout: 1m\n"
      + "          identifier: \"1_1\"\n"
      + "          name: \"4_1\"\n"
      + "      - __uuid: parallel1\n"
      + "        step:\n"
      + "          __uuid: test\n"
      + "          type: Http\n"
      + "          strategy:\n"
      + "            parallelism: 4\n"
      + "          spec:\n"
      + "            url: https://www.google.com\n"
      + "            method: GET\n"
      + "            headers: []\n"
      + "            outputVariables: []\n"
      + "          timeout: 1m\n"
      + "          identifier: \"2_2\"\n"
      + "          name: \"4_2\"\n"
      + "      - __uuid: parallel1\n"
      + "        step:\n"
      + "          __uuid: test\n"
      + "          type: Http\n"
      + "          strategy:\n"
      + "            parallelism: 4\n"
      + "          spec:\n"
      + "            url: https://www.google.com\n"
      + "            method: GET\n"
      + "            headers: []\n"
      + "            outputVariables: []\n"
      + "          timeout: 1m\n"
      + "          identifier: \"3_3\"\n"
      + "          name: \"4_3\"\n"
      + "      - __uuid: parallel2\n"
      + "        step:\n"
      + "          _uuid: test1\n"
      + "          type: Http\n"
      + "          name: bing\n"
      + "          identifier: bing\n"
      + "          spec:\n"
      + "            url: https://www.bing.com\n"
      + "            method: GET\n"
      + "            headers: []\n"
      + "            outputVariables: []\n"
      + "          timeout: 10s\n";

  @Inject MatrixConfigService matrixConfigService;
  @Inject ForLoopStrategyConfigService forLoopStrategyConfigService;
  @Inject ParallelismStrategyConfigService parallelismStrategyConfigService;

  @Inject @InjectMocks StrategyHelper strategyHelper;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExpandStageJsonNodesMatrix() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(0).getField("stage");
    List<JsonNode> jsonNodes =
        strategyHelper.expandJsonNodes(approvalStageYamlField.getNode().getCurrJsonNode(), Optional.empty())
            .getExpandedJsonNodes();
    assertThat(jsonNodes.size()).isEqualTo(8);
    List<String> appendValues = Lists.newArrayList("0_1", "0_2", "1_0", "1_1", "1_2", "2_0", "2_1", "2_2");
    List<String> variableAValues = Lists.newArrayList("1", "1", "2", "2", "2", "3", "3", "3");
    List<String> variableBValues = Lists.newArrayList("3", "4", "2", "3", "4", "2", "3", "4");

    int current = 0;
    for (JsonNode jsonNode : jsonNodes) {
      assertThat(jsonNode.get("identifier").asText()).isEqualTo("a11_" + appendValues.get(current));
      assertThat(jsonNode.get("variables").get(0).get("value").asText()).isEqualTo(variableAValues.get(current));
      assertThat(jsonNode.get("variables").get(1).get("value").asText()).isEqualTo(variableBValues.get(current));
      assertThat(jsonNode.get("variables").get(2).get("value").asText()).isEqualTo(String.valueOf(current));
      assertThat(jsonNode.get("variables").get(3).get("value").asText()).isEqualTo(String.valueOf(8));
      current++;
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExpandStageJsonNodesMatrixHavingExpression() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy-having-one-expression.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(0).getField("stage");
    List<JsonNode> jsonNodes =
        strategyHelper.expandJsonNodes(approvalStageYamlField.getNode().getCurrJsonNode(), Optional.empty())
            .getExpandedJsonNodes();
    assertThat(jsonNodes.size()).isEqualTo(8);
    List<String> appendValues = Lists.newArrayList("0_1", "0_2", "1_0", "1_1", "1_2", "2_0", "2_1", "2_2");
    List<String> variableAValues = Lists.newArrayList(
        "1", "1", "2", "2", "2", "<+pipeline.variables.a>", "<+pipeline.variables.a>", "<+pipeline.variables.a>");
    List<String> variableBValues = Lists.newArrayList("3", "4", "2", "3", "4", "2", "3", "4");

    int current = 0;
    for (JsonNode jsonNode : jsonNodes) {
      assertThat(jsonNode.get("identifier").asText()).isEqualTo("a11_" + appendValues.get(current));
      assertThat(jsonNode.get("variables").get(0).get("value").asText()).isEqualTo(variableAValues.get(current));
      assertThat(jsonNode.get("variables").get(1).get("value").asText()).isEqualTo(variableBValues.get(current));
      assertThat(jsonNode.get("variables").get(2).get("value").asText()).isEqualTo(String.valueOf(current));
      assertThat(jsonNode.get("variables").get(3).get("value").asText()).isEqualTo(String.valueOf(8));
      current++;
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExpandStageJsonNodesFor() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(2).getField("stage");
    List<JsonNode> jsonNodes =
        strategyHelper.expandJsonNodes(approvalStageYamlField.getNode().getCurrJsonNode(), Optional.empty())
            .getExpandedJsonNodes();
    assertThat(jsonNodes.size()).isEqualTo(10);
    int current = 0;
    for (JsonNode jsonNode : jsonNodes) {
      assertThat(jsonNode.get("identifier").asText()).isEqualTo("a11_" + current);
      assertThat(jsonNode.get("variables").get(0).get("value").asText()).isEqualTo(String.valueOf(current));
      assertThat(jsonNode.get("variables").get(1).get("value").asText()).isEqualTo(String.valueOf(10));
      current++;
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExpandStageJsonNodesForItems() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(5).getField("stage");
    List<JsonNode> jsonNodes =
        strategyHelper.expandJsonNodes(approvalStageYamlField.getNode().getCurrJsonNode(), Optional.empty())
            .getExpandedJsonNodes();
    assertThat(jsonNodes.size()).isEqualTo(1);
    int current = 0;
    List<String> expectedValues = Lists.newArrayList("a", "b", "c");
    for (JsonNode jsonNode : jsonNodes) {
      assertThat(jsonNode.get("identifier").asText()).isEqualTo("a11_" + current);
      assertThat(jsonNode.get("variables").get(0).get("value").asText()).isEqualTo(String.valueOf(current));
      assertThat(jsonNode.get("variables").get(1).get("value").asText()).isEqualTo(String.valueOf(1));
      assertThat(jsonNode.get("variables").get(2).get("value").asText()).isEqualTo(expectedValues.get(current));
      current++;
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExpandStageJsonNodesParallelism() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(3).getField("stage");
    List<JsonNode> jsonNodes =
        strategyHelper.expandJsonNodes(approvalStageYamlField.getNode().getCurrJsonNode(), Optional.empty())
            .getExpandedJsonNodes();
    assertThat(jsonNodes.size()).isEqualTo(4);
    int current = 0;
    for (JsonNode jsonNode : jsonNodes) {
      assertThat(jsonNode.get("identifier").asText()).isEqualTo("a12_" + current);
      assertThat(jsonNode.get("variables").get(0).get("value").asText()).isEqualTo(String.valueOf(current));
      assertThat(jsonNode.get("variables").get(1).get("value").asText()).isEqualTo(String.valueOf(4));
      current++;
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExpandStepJsonNodesMatrix() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(0).getField("stage");
    YamlField stepYamlField = approvalStageYamlField.getNode()
                                  .getField("spec")
                                  .getNode()
                                  .getField("execution")
                                  .getNode()
                                  .getField("steps")
                                  .getNode()
                                  .asArray()
                                  .get(0)
                                  .getField("stepGroup")
                                  .getNode()
                                  .getField("steps")
                                  .getNode()
                                  .asArray()
                                  .get(0)
                                  .getField("step");
    List<JsonNode> jsonNodes =
        strategyHelper.expandJsonNodes(stepYamlField.getNode().getCurrJsonNode(), Optional.empty())
            .getExpandedJsonNodes();
    int current = 0;
    assertThat(jsonNodes.size()).isEqualTo(8);
    List<String> appendValues = Lists.newArrayList("0_1", "0_2", "1_0", "1_1", "1_2", "2_0", "2_1", "2_2");
    List<String> variableAValues = Lists.newArrayList("1", "1", "2", "2", "2", "3", "3", "3");
    List<String> variableBValues = Lists.newArrayList("3", "4", "2", "3", "4", "2", "3", "4");

    for (JsonNode jsonNode : jsonNodes) {
      assertThat(jsonNode.get("identifier").asText())
          .isEqualTo(variableAValues.get(current) + "_" + appendValues.get(current));
      assertThat(jsonNode.get("name").asText())
          .isEqualTo(variableBValues.get(current) + "_" + appendValues.get(current));
      current++;
    }

    // Testing the matrix with objects configuration.
    jsonNodes = strategyHelper
                    .expandJsonNodes(approvalStageYamlField.getNode()
                                         .getField("spec")
                                         .getNode()
                                         .getField("execution")
                                         .getNode()
                                         .getField("steps")
                                         .getNode()
                                         .asArray()
                                         .get(2)
                                         .getField("step")
                                         .getNode()
                                         .getCurrJsonNode(),
                        Optional.empty())
                    .getExpandedJsonNodes();

    current = 0;
    List<String> images = Arrays.asList("linux", "window");
    List<String> tags = Arrays.asList("stable", "latest");
    for (JsonNode jsonNode : jsonNodes) {
      assertThat(jsonNode.get("spec").get("image").asText()).isEqualTo(images.get(current));
      assertThat(jsonNode.get("spec").get("tag").asText()).isEqualTo(tags.get(current));
      assertThat(jsonNode.get("identifier").asText()).isEqualTo("google_again_" + current);
      current++;
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExpandStepJsonNodesFor() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(2).getField("stage");
    YamlField stepYamlField = approvalStageYamlField.getNode()
                                  .getField("spec")
                                  .getNode()
                                  .getField("execution")
                                  .getNode()
                                  .getField("steps")
                                  .getNode()
                                  .asArray()
                                  .get(0)
                                  .getField("stepGroup")
                                  .getNode()
                                  .getField("steps")
                                  .getNode()
                                  .asArray()
                                  .get(0)
                                  .getField("step");
    List<JsonNode> jsonNodes =
        strategyHelper.expandJsonNodes(stepYamlField.getNode().getCurrJsonNode(), Optional.empty())
            .getExpandedJsonNodes();
    int current = 0;
    assertThat(jsonNodes.size()).isEqualTo(10);

    for (JsonNode jsonNode : jsonNodes) {
      assertThat(jsonNode.get("identifier").asText()).isEqualTo(current + "_" + current);
      assertThat(jsonNode.get("name").asText()).isEqualTo("10_" + current);
      current++;
    }
    stepYamlField = approvalStageYamlField.getNode()
                        .getField("spec")
                        .getNode()
                        .getField("execution")
                        .getNode()
                        .getField("steps")
                        .getNode()
                        .asArray()
                        .get(0)
                        .getField("stepGroup")
                        .getNode()
                        .getField("steps")
                        .getNode()
                        .asArray()
                        .get(1)
                        .getField("step");
    jsonNodes = strategyHelper.expandJsonNodes(stepYamlField.getNode().getCurrJsonNode(), Optional.empty())
                    .getExpandedJsonNodes();
    current = 0;
    assertThat(jsonNodes.size()).isEqualTo(1);

    for (JsonNode jsonNode : jsonNodes) {
      assertThat(jsonNode.get("identifier").asText()).isEqualTo("0");
      assertThat(jsonNode.get("name").asText()).isEqualTo("1");
      current++;
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExpandStepJsonNodesParallelism() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(3).getField("stage");
    YamlField stepYamlField = approvalStageYamlField.getNode()
                                  .getField("spec")
                                  .getNode()
                                  .getField("execution")
                                  .getNode()
                                  .getField("steps")
                                  .getNode()
                                  .asArray()
                                  .get(0)
                                  .getField("stepGroup")
                                  .getNode()
                                  .getField("steps")
                                  .getNode()
                                  .asArray()
                                  .get(0)
                                  .getField("step");
    List<JsonNode> jsonNodes =
        strategyHelper.expandJsonNodes(stepYamlField.getNode().getCurrJsonNode(), Optional.empty())
            .getExpandedJsonNodes();
    int current = 0;
    assertThat(jsonNodes.size()).isEqualTo(4);

    for (JsonNode jsonNode : jsonNodes) {
      assertThat(jsonNode.get("identifier").asText()).isEqualTo(current + "_" + current);
      assertThat(jsonNode.get("name").asText()).isEqualTo("4_" + current);
      current++;
    }
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testForExecutionElementConfigExpansionNormalStep() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("step-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String stepYaml = Resources.toString(testFile, Charsets.UTF_8);
    ExecutionWrapperConfig executionWrapperConfig = YamlUtils.read(stepYaml, ExecutionWrapperConfig.class);
    ExpandedExecutionWrapperInfo expandedExecutionWrapperInfo =
        strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig, Optional.empty());
    assertThat(
        expandedExecutionWrapperInfo.getUuidToStrategyExpansionData().containsKey(executionWrapperConfig.getUuid()))
        .isTrue();
    List<ExecutionWrapperConfig> executionWrapperConfigs = expandedExecutionWrapperInfo.getExpandedExecutionConfigs();
    String yaml = YamlUtils.writeYamlString(executionWrapperConfigs);
    assertThat(yaml).isEqualTo(JSON_FOR_STEP_EXPANSION);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testForExecutionElementConfigExpansionStepGroup() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("stepGroup-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String stepYaml = Resources.toString(testFile, Charsets.UTF_8);
    ExecutionWrapperConfig executionWrapperConfig = YamlUtils.read(stepYaml, ExecutionWrapperConfig.class);
    List<ExecutionWrapperConfig> executionWrapperConfigs =
        strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig, Optional.empty())
            .getExpandedExecutionConfigs();
    String yaml = YamlUtils.writeYamlString(executionWrapperConfigs);
    assertThat(yaml).isEqualTo(JSON_FOR_STEP_GROUP_EXPANSION);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testForExecutionElementConfigExpansionParallel() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("parallel-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String stepYaml = Resources.toString(testFile, Charsets.UTF_8);
    ExecutionWrapperConfig executionWrapperConfig = YamlUtils.read(stepYaml, ExecutionWrapperConfig.class);
    List<ExecutionWrapperConfig> executionWrapperConfigs =
        strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig, Optional.empty())
            .getExpandedExecutionConfigs();
    String yaml = YamlUtils.writeYamlString(executionWrapperConfigs);
    assertThat(yaml).isEqualTo(EXPECTED_JSON_FOR_PARALLEL);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testParallelExpansionWithLimit() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("matrix-with-expansion-limit.yaml");
    assertThat(testFile).isNotNull();
    String stepYaml = Resources.toString(testFile, Charsets.UTF_8);
    ExecutionWrapperConfig executionWrapperConfig = YamlUtils.read(stepYaml, ExecutionWrapperConfig.class);

    assertThatThrownBy(()
                           -> strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig, Optional.of(2))
                                  .getExpandedExecutionConfigs())
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Iteration count is beyond the supported limit of 2");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testParallelExpansionWithLoop() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("loop-with-expansion-limit.yaml");
    assertThat(testFile).isNotNull();
    String stepYaml = Resources.toString(testFile, Charsets.UTF_8);
    ExecutionWrapperConfig executionWrapperConfig = YamlUtils.read(stepYaml, ExecutionWrapperConfig.class);

    assertThatThrownBy(()
                           -> strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig, Optional.of(2))
                                  .getExpandedExecutionConfigs())
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Iteration count is beyond the supported limit of 2");
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testMatrixExpansionWithLimit() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("parallel-with-expansion-limit.yaml");
    assertThat(testFile).isNotNull();
    String stepYaml = Resources.toString(testFile, Charsets.UTF_8);
    ExecutionWrapperConfig executionWrapperConfig = YamlUtils.read(stepYaml, ExecutionWrapperConfig.class);

    assertThatThrownBy(()
                           -> strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig, Optional.of(100))
                                  .getExpandedExecutionConfigs())
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Parallelism count is beyond the supported limit of 100");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testForExecutionElementConfigExpansionParallelWrongParallelism() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("strategy-with-wrong-axis.yaml");
    assertThat(testFile).isNotNull();
    String stepYaml = Resources.toString(testFile, Charsets.UTF_8);
    ExecutionWrapperConfig executionWrapperConfig = YamlUtils.read(stepYaml, ExecutionWrapperConfig.class);
    assertThatThrownBy(() -> strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig, Optional.empty()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage(
            "Cannot deserialize value of type `java.lang.Integer` from String \"as\": not a valid `java.lang.Integer` value");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testForExecutionElementConfigExpansionParallelWrongAxis() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("strategy-with-wrong-axis1.yaml");
    assertThat(testFile).isNotNull();
    String stepYaml = Resources.toString(testFile, Charsets.UTF_8);
    ExecutionWrapperConfig executionWrapperConfig = YamlUtils.read(stepYaml, ExecutionWrapperConfig.class);
    assertThatThrownBy(() -> strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig, Optional.empty()))
        .isInstanceOf(InvalidYamlException.class)
        .hasMessage("Value provided for axes [b] is string. It should either be a List or an Expression.");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void usageExampleForExpandedExecutionWrapperInfo() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("stepGroup-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String stepYaml = Resources.toString(testFile, Charsets.UTF_8);
    ExecutionWrapperConfig executionWrapperConfig = YamlUtils.read(stepYaml, ExecutionWrapperConfig.class);
    ExpandedExecutionWrapperInfo expandedExecutionWrapperInfo =
        strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig, Optional.empty());

    for (ExecutionWrapperConfig executionWrapperConfigEntry :
        expandedExecutionWrapperInfo.getExpandedExecutionConfigs()) {
      // Check if step is defined in executionWrapperConfig
      if (executionWrapperConfigEntry.getStep() != null) {
        // Extract uuid of the executionWrapperConfig
        String uuid = executionWrapperConfig.getUuid();
        // Fetch Max-Concurrency for this uuid.
        // WrapperConfigs belonging to same uuid are part of same matrix configuration.
        // If maxConcurrency is 1 then strategy is not defined.
        assertThat(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData().containsKey(uuid)).isTrue();
      }
      if (executionWrapperConfig.getStepGroup() != null) {
        // Extract uuid of the executionWrapperConfig
        String uuid = executionWrapperConfig.getUuid();
        // This will give us the strategy information on step group, to traverse on each children, we will need to
        // recursively call the function
        assertThat(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData().containsKey(uuid)).isTrue();
      }
      if (executionWrapperConfig.getParallel() != null) {
        // Extract uuid of the executionWrapperConfig
        String uuid = executionWrapperConfig.getUuid();
        // For parallel, uuid will not be there in the map as strategy cannot be defined on parallel
        assertThat(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData().containsKey(uuid)).isFalse();
      }
    }
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void nullStepTest() throws IOException {
    JsonNode step = NullNode.getInstance();
    ExecutionWrapperConfig executionWrapperConfig = ExecutionWrapperConfig.builder().step(step).build();
    ExpandedExecutionWrapperInfo expandedExecutionWrapperInfo =
        strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig, Optional.of(10));
    assertThat(expandedExecutionWrapperInfo.getUuidToStrategyExpansionData()).isEmpty();
    assertThat(expandedExecutionWrapperInfo.getExpandedExecutionConfigs().get(0).getUuid()).isNull();
  }
}
