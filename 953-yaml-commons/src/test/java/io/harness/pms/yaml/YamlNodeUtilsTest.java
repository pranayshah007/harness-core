/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGES;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.YamlException;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(PIPELINE)
public class YamlNodeUtilsTest extends CategoryTest {
  @Mock JsonNode jsonNode;
  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testWithParallelNode() throws IOException {
    String pipelineYaml = readFile("pipeline-with-parallel-stages.yaml");
    YamlNode pipelineNode = YamlNode.fromYamlPath(pipelineYaml, "pipeline");

    YamlNode node = pipelineNode.getField(STAGES).getNode();
    YamlNodeUtils.getNextNodeFromArray(node, "DeploytoDEV");
    assertThat(pipelineNode).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGoToPathUsingFqnInvalidFQN() throws IOException {
    String pipelineYaml = readFile("pipeline-with-parallel-stages.yaml");
    YamlNode pipelineNode = YamlNode.fromYamlPath(pipelineYaml, "pipeline");

    YamlNode yamlNode = YamlNodeUtils.goToPathUsingFqn(pipelineNode, "");
    assertThat(yamlNode).isNotNull();
    assertThat(yamlNode).isEqualTo(pipelineNode);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGoToPathUsingFqn() throws IOException {
    String pipelineYaml = readFile("pipeline-with-inputs.yaml");
    YamlNode pipelineNode = YamlNode.fromYamlPath(pipelineYaml, "pipeline");

    YamlNode yamlNode = YamlNodeUtils.goToPathUsingFqn(pipelineNode, "stages.qaStage.spec.service");
    assertThat(yamlNode).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testFindFieldNameInArray() throws IOException {
    String pipelineYaml = readFile("pipeline-with-inputs.yaml");
    YamlNode pipelineNode = YamlNode.fromYamlPath(pipelineYaml, "pipeline");

    YamlNode yamlNode = YamlNodeUtils.findFieldNameInArray(pipelineNode, "");
    assertThat(yamlNode).isNull();

    yamlNode = YamlNodeUtils.findFieldNameInArray(pipelineNode, "Rollout Deployment");
    assertThat(yamlNode).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testfindFieldNameInObject() throws IOException {
    String pipelineYaml = readFile("pipeline-with-inputs.yaml");
    YamlNode pipelineNode = YamlNode.fromYamlPath(pipelineYaml, "pipeline");

    YamlNode yamlNode = YamlNodeUtils.findFieldNameInObject(pipelineNode, "");
    assertThat(yamlNode).isNull();

    yamlNode = YamlNodeUtils.findFieldNameInObject(pipelineNode, "Rollout Deployment");
    assertThat(yamlNode).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testFindFirstNodeMatchingFieldName() throws IOException {
    String pipelineYaml = readFile("pipeline-with-inputs.yaml");
    YamlNode pipelineNode = YamlNode.fromYamlPath(pipelineYaml, "pipeline");

    YamlNode yamlNode = YamlNodeUtils.findFirstNodeMatchingFieldName(pipelineNode, "Rollout Deployment");
    assertThat(yamlNode).isNotNull();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testAddToPathThrowsExceptionForNonArray() throws IOException {
    String pipelineYaml = readFile("pipeline-with-inputs.yaml");
    YamlNode pipelineNode = YamlNode.fromYamlPath(pipelineYaml, "pipeline");

    assertThatThrownBy(() -> {
      YamlNodeUtils.addToPath(pipelineNode, "[pipeline.stages", jsonNode);
    }).isInstanceOf(YamlException.class);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testAddToPathThrowsExceptionForIncorrectIndex() throws IOException {
    String pipelineYaml = readFile("pipeline-with-inputs.yaml");
    YamlNode pipelineNode = YamlNode.fromYamlPath(pipelineYaml, "pipeline");

    YamlNode node = pipelineNode.getField(STAGES).getNode();
    assertThatThrownBy(() -> { YamlNodeUtils.addToPath(node, "[5", jsonNode); }).isInstanceOf(YamlException.class);
  }
}
