package io.harness.steps.matrix;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.NGCommonUtilitiesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class StrategyHelperTest extends NGCommonUtilitiesTestBase {
  private static final String JSON_FOR_STEP_EXPANSION = "---\n"
      + "- step:\n"
      + "    type: \"Http\"\n"
      + "    spec:\n"
      + "      url: \"https://www.google.com\"\n"
      + "      method: \"GET\"\n"
      + "    timeout: \"1m\"\n"
      + "    identifier: \"0_0\"\n"
      + "    name: \"4_0\"\n"
      + "- step:\n"
      + "    type: \"Http\"\n"
      + "    spec:\n"
      + "      url: \"https://www.google.com\"\n"
      + "      method: \"GET\"\n"
      + "    timeout: \"1m\"\n"
      + "    identifier: \"1_1\"\n"
      + "    name: \"4_1\"\n"
      + "- step:\n"
      + "    type: \"Http\"\n"
      + "    spec:\n"
      + "      url: \"https://www.google.com\"\n"
      + "      method: \"GET\"\n"
      + "    timeout: \"1m\"\n"
      + "    identifier: \"2_2\"\n"
      + "    name: \"4_2\"\n"
      + "- step:\n"
      + "    type: \"Http\"\n"
      + "    spec:\n"
      + "      url: \"https://www.google.com\"\n"
      + "      method: \"GET\"\n"
      + "    timeout: \"1m\"\n"
      + "    identifier: \"3_3\"\n"
      + "    name: \"4_3\"\n";

  private static final String JSON_FOR_STEP_GROUP_EXPANSION = "---\n"
      + "- stepGroup:\n"
      + "    identifier: \"searching\"\n"
      + "    name: \"searching\"\n"
      + "    steps:\n"
      + "    - parallel:\n"
      + "      - step:\n"
      + "          type: \"Http\"\n"
      + "          spec:\n"
      + "            url: \"https://www.google.com\"\n"
      + "            method: \"GET\"\n"
      + "          timeout: \"1m\"\n"
      + "          identifier: \"0_0\"\n"
      + "          name: \"4_0\"\n"
      + "      - step:\n"
      + "          type: \"Http\"\n"
      + "          spec:\n"
      + "            url: \"https://www.google.com\"\n"
      + "            method: \"GET\"\n"
      + "          timeout: \"1m\"\n"
      + "          identifier: \"1_1\"\n"
      + "          name: \"4_1\"\n"
      + "      - step:\n"
      + "          type: \"Http\"\n"
      + "          spec:\n"
      + "            url: \"https://www.google.com\"\n"
      + "            method: \"GET\"\n"
      + "          timeout: \"1m\"\n"
      + "          identifier: \"2_2\"\n"
      + "          name: \"4_2\"\n"
      + "      - step:\n"
      + "          type: \"Http\"\n"
      + "          spec:\n"
      + "            url: \"https://www.google.com\"\n"
      + "            method: \"GET\"\n"
      + "          timeout: \"1m\"\n"
      + "          identifier: \"3_3\"\n"
      + "          name: \"4_3\"\n"
      + "      - step:\n"
      + "          type: \"Http\"\n"
      + "          name: \"bing\"\n"
      + "          identifier: \"bing\"\n"
      + "          spec:\n"
      + "            url: \"https://www.bing.com\"\n"
      + "            method: \"GET\"\n"
      + "            headers: []\n"
      + "            outputVariables: []\n"
      + "          timeout: \"10s\"\n"
      + "    - step:\n"
      + "        type: \"Http\"\n"
      + "        spec:\n"
      + "          url: \"https://www.google.com\"\n"
      + "          method: \"GET\"\n"
      + "        timeout: \"1m\"\n"
      + "        identifier: \"0_0\"\n"
      + "        name: \"4_0\"\n"
      + "    - step:\n"
      + "        type: \"Http\"\n"
      + "        spec:\n"
      + "          url: \"https://www.google.com\"\n"
      + "          method: \"GET\"\n"
      + "        timeout: \"1m\"\n"
      + "        identifier: \"1_1\"\n"
      + "        name: \"4_1\"\n"
      + "    - step:\n"
      + "        type: \"Http\"\n"
      + "        spec:\n"
      + "          url: \"https://www.google.com\"\n"
      + "          method: \"GET\"\n"
      + "        timeout: \"1m\"\n"
      + "        identifier: \"2_2\"\n"
      + "        name: \"4_2\"\n"
      + "    - step:\n"
      + "        type: \"Http\"\n"
      + "        spec:\n"
      + "          url: \"https://www.google.com\"\n"
      + "          method: \"GET\"\n"
      + "        timeout: \"1m\"\n"
      + "        identifier: \"3_3\"\n"
      + "        name: \"4_3\"\n";

  private static final String EXPECTED_JSON_FOR_PARALLEL = "---\n"
      + "- parallel:\n"
      + "  - step:\n"
      + "      type: \"Http\"\n"
      + "      spec:\n"
      + "        url: \"https://www.google.com\"\n"
      + "        method: \"GET\"\n"
      + "      timeout: \"1m\"\n"
      + "      identifier: \"0_0\"\n"
      + "      name: \"4_0\"\n"
      + "  - step:\n"
      + "      type: \"Http\"\n"
      + "      spec:\n"
      + "        url: \"https://www.google.com\"\n"
      + "        method: \"GET\"\n"
      + "      timeout: \"1m\"\n"
      + "      identifier: \"1_1\"\n"
      + "      name: \"4_1\"\n"
      + "  - step:\n"
      + "      type: \"Http\"\n"
      + "      spec:\n"
      + "        url: \"https://www.google.com\"\n"
      + "        method: \"GET\"\n"
      + "      timeout: \"1m\"\n"
      + "      identifier: \"2_2\"\n"
      + "      name: \"4_2\"\n"
      + "  - step:\n"
      + "      type: \"Http\"\n"
      + "      spec:\n"
      + "        url: \"https://www.google.com\"\n"
      + "        method: \"GET\"\n"
      + "      timeout: \"1m\"\n"
      + "      identifier: \"3_3\"\n"
      + "      name: \"4_3\"\n"
      + "  - step:\n"
      + "      type: \"Http\"\n"
      + "      name: \"bing\"\n"
      + "      identifier: \"bing\"\n"
      + "      spec:\n"
      + "        url: \"https://www.bing.com\"\n"
      + "        method: \"GET\"\n"
      + "        headers: []\n"
      + "        outputVariables: []\n"
      + "      timeout: \"10s\"\n";

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
    List<JsonNode> jsonNodes = strategyHelper.expandJsonNodes(approvalStageYamlField.getNode().getCurrJsonNode());
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
    List<JsonNode> jsonNodes = strategyHelper.expandJsonNodes(approvalStageYamlField.getNode().getCurrJsonNode());
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
    List<JsonNode> jsonNodes = strategyHelper.expandJsonNodes(approvalStageYamlField.getNode().getCurrJsonNode());
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
    List<JsonNode> jsonNodes = strategyHelper.expandJsonNodes(approvalStageYamlField.getNode().getCurrJsonNode());
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
    List<JsonNode> jsonNodes = strategyHelper.expandJsonNodes(stepYamlField.getNode().getCurrJsonNode());
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
    List<JsonNode> jsonNodes = strategyHelper.expandJsonNodes(stepYamlField.getNode().getCurrJsonNode());
    int current = 0;
    assertThat(jsonNodes.size()).isEqualTo(10);

    for (JsonNode jsonNode : jsonNodes) {
      assertThat(jsonNode.get("identifier").asText()).isEqualTo(current + "_" + current);
      assertThat(jsonNode.get("name").asText()).isEqualTo("10_" + current);
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
    List<JsonNode> jsonNodes = strategyHelper.expandJsonNodes(stepYamlField.getNode().getCurrJsonNode());
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
    List<ExecutionWrapperConfig> executionWrapperConfigs =
        strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig);
    String yaml = YamlUtils.write(executionWrapperConfigs);
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
        strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig);
    String yaml = YamlUtils.write(executionWrapperConfigs);
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
        strategyHelper.expandExecutionWrapperConfig(executionWrapperConfig);
    String yaml = YamlUtils.write(executionWrapperConfigs);
    assertThat(yaml).isEqualTo(EXPECTED_JSON_FOR_PARALLEL);
  }
}