/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.inputset.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class FQNUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testIsStageIdentifier() {
    String yaml = "topKey:\n"
        + "  field1: val\n"
        + "  stagesAndOtherThings:\n"
        + "  - stage:\n"
        + "      identifier: f1\n"
        + "  - stage:\n"
        + "      identifier: f2\n"
        + "  - stage:\n"
        + "      identifier: f3\n"
        + "      manifests:\n"
        + "      - manifest:\n"
        + "         identifier: m1\n"
        + "         name: m1\n"
        + "  - somethingElseIdkWhat:\n"
        + "      identifier: f4\n"
        + "  - parallel:\n"
        + "    - stage:\n"
        + "        identifier: f5\n"
        + "    - stage:\n"
        + "        identifier: f6";
    YamlConfig config = new YamlConfig(yaml);
    Set<FQN> fqnSet = config.getFqnToValueMap().keySet();
    assertThat(fqnSet).hasSize(9);
    List<FQN> stageIdentifiers = fqnSet.stream().filter(FQN::isStageIdentifier).collect(Collectors.toList());
    assertThat(stageIdentifiers).hasSize(5);
    List<String> fqnStrings = stageIdentifiers.stream().map(FQN::display).collect(Collectors.toList());
    assertThat(fqnStrings)
        .contains("topKey.stagesAndOtherThings.stage[identifier:f1].identifier.",
            "topKey.stagesAndOtherThings.stage[identifier:f2].identifier.",
            "topKey.stagesAndOtherThings.stage[identifier:f3].identifier.",
            "topKey.stagesAndOtherThings.PARALLEL.stage[identifier:f5].identifier.",
            "topKey.stagesAndOtherThings.PARALLEL.stage[identifier:f6].identifier.");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testYamlMap() {
    String yaml = "pipeline:\n"
        + "   name: \"parent pipeline\"\n"
        + "   identifier: \"rc-" + generateUuid() + "\"\n"
        + "   timeout: \"1w\"\n"
        + "   type: \"Pipeline\"\n"
        + "   spec:\n"
        + "     pipeline: \"childPipeline\"\n"
        + "     org: \"org\"\n"
        + "     project: \"project\"\n"
        + "     outputs:\n"
        + "       - name: var1\n"
        + "         __uuid: uuid1\n"
        + "         value: value1\n"
        + "       - name: var2\n"
        + "         __uuid: uuid1\n"
        + "         value: value2\n";
    Map<String, Object> dataMap = FQNMapGenerator.generateYamlMapWithFqnExpression(yaml);
    assertThat(dataMap).isNotNull();
    assertThat(dataMap.get("pipeline")).isNotNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGenerateFQNMap() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();

    List<String> filenames = Arrays.asList("test1.yaml", "test2.yaml", "test3.yaml", "test4.yaml");
    List<String> resFiles = Arrays.asList("res1.txt", "res2.txt", "res3.txt", "res4.txt");

    for (int i = 0; i < 4; i++) {
      String filename = filenames.get(i);
      String yaml =
          Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
      YamlConfig config = new YamlConfig(yaml);
      List<String> keys = config.getFqnToValueMap().keySet().stream().map(FQN::display).collect(Collectors.toList());

      String resFile = resFiles.get(i);
      String res = Resources.toString(Objects.requireNonNull(classLoader.getResource(resFile)), StandardCharsets.UTF_8);
      List<String> resKeys = YamlUtils.read(res, ArrayList.class);
      assertThat(keys).isEqualTo(resKeys);
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGenerateYamlMap() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();

    List<String> filenames = Arrays.asList("test1.yaml", "test2.yaml", "test3.yaml", "test4.yaml");
    for (int i = 0; i < 4; i++) {
      String filename = filenames.get(i);
      String yaml =
          Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
      YamlConfig config = new YamlConfig(yaml);

      YamlConfig reverseConfig = new YamlConfig(config.getFqnToValueMap(), config.getYamlMap());
      assertThat(reverseConfig.getYaml().replace("\"", "")).isEqualTo(yaml);
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGenerateFQNAndYamlMapOnFailureStrategiesYaml() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "failure-strategy.yaml";
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    YamlConfig config = new YamlConfig(yaml);
    assertThat(config).isNotNull();
    YamlConfig configRes = new YamlConfig(config.getFqnToValueMap(), config.getYamlMap());
    assertThat(configRes.getYaml().replace("\"", "")).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGenerateFQNAndYamlMapOnCIPipelineWithReports() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String filename = "ci-pipeline-with-reports.yaml";
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    YamlConfig config = new YamlConfig(yaml);
    assertThat(config).isNotNull();
    YamlConfig configRes = new YamlConfig(config.getFqnToValueMap(), config.getYamlMap());
    assertThat(configRes.getYaml().replace("\"", "")).isEqualTo(yaml);
  }

  private String toYaml(String fileName) throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    return Resources.toString(Objects.requireNonNull(classLoader.getResource(fileName)), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testUniqueFQN() throws IOException {
    String ValidFqnYaml = "fqnUniqueTest1.yaml";
    String yaml1 = toYaml(ValidFqnYaml);
    assertThat(FQNMapGenerator.generateFQNMap(YamlUtils.readTree(yaml1).getNode().getCurrJsonNode()))
        .isNotInstanceOf(InvalidRequestException.class);

    String sameVariableYaml = "fqnUniqueSameVariableNameTest.yaml";
    String yaml2 = toYaml(sameVariableYaml);
    assertThatThrownBy(() -> FQNMapGenerator.generateFQNMap(YamlUtils.readTree(yaml2).getNode().getCurrJsonNode()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(" This element is coming twice in yaml a.d.h.[name:name1]");

    String sameStageIdentifierYaml = "fqnUniqueSameStageIdentifierTest.yaml";
    String yaml3 = toYaml(sameStageIdentifierYaml);
    assertThatThrownBy(() -> FQNMapGenerator.generateFQNMap(YamlUtils.readTree(yaml3).getNode().getCurrJsonNode()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(" This element is coming twice in yaml a.d.stage[identifier:id1]");

    String sameBarrierIdentifierYaml = "fqnUniqueSameBarrierIdentifierTest.yaml";
    String yaml4 = toYaml(sameBarrierIdentifierYaml);
    assertThatThrownBy(() -> FQNMapGenerator.generateFQNMap(YamlUtils.readTree(yaml4).getNode().getCurrJsonNode()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            " This element is coming twice in yaml a.d.stage[identifier:id1].h.execution.step[identifier:barr1]");

    String failureStrategyYaml = "fqnUniqueFailureStrategyTest.yaml";
    String yaml5 = toYaml(failureStrategyYaml);
    assertThat(FQNMapGenerator.generateFQNMap(YamlUtils.readTree(yaml5).getNode().getCurrJsonNode()))
        .isNotInstanceOf(InvalidRequestException.class);

    String parallelStepsFailedFqn = "fqnUniqueTestForParallelSteps.yaml";
    String parallelStepsFailedFqnYaml = toYaml(parallelStepsFailedFqn);
    assertThatThrownBy(()
                           -> FQNMapGenerator.generateFQNMap(
                               YamlUtils.readTree(parallelStepsFailedFqnYaml).getNode().getCurrJsonNode()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testKeepUuidAndRemoveFromJsonNode() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    String filename = "yamlWithList.yaml";
    ClassLoader classLoader = getClass().getClassLoader();
    String yaml = Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    String yamlWithUUID = YamlUtils.injectUuid(yaml);
    JsonNode jsonNode = objectMapper.readTree(yamlWithUUID);
    JsonNode originalJsonNode = jsonNode.deepCopy();
    // Will not remove the uuid from stages' first child.
    new YamlConfig(originalJsonNode, true);

    assertThat(jsonNode).isEqualTo(originalJsonNode);
    assertThat(originalJsonNode.get("pipeline").get("stages").get(0).get("__uuid")).isNotNull();
    assertThat(originalJsonNode.get("pipeline").get("stages").get(1).get("__uuid")).isNotNull();

    // Will remove the uuid from stages' first child. This is current logic.
    new YamlConfig(originalJsonNode);
    assertThat(jsonNode).isNotEqualTo(originalJsonNode);
    assertThat(originalJsonNode.get("pipeline").get("stages").get(0).get("__uuid")).isNull();
    assertThat(originalJsonNode.get("pipeline").get("stages").get(1).get("__uuid")).isNotNull();
  }
}
