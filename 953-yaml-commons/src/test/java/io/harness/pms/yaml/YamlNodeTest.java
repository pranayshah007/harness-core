/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class YamlNodeTest extends CategoryTest {
  String pipelineYaml;
  YamlNode pipelineNode;

  private String readFile() {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(
          Objects.requireNonNull(classLoader.getResource("pipeline-extensive.yml")), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: opa-pipeline.yaml");
    }
  }

  @Before
  public void setUp() throws IOException {
    pipelineYaml = readFile();
    pipelineNode = YamlUtils.readTree(pipelineYaml).getNode();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetFieldOrThrow() {
    assertThatThrownBy(() -> pipelineNode.getFieldOrThrow("notThere"))
        .hasMessage("Field for key [notThere] does not exist");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetLastKeyInPath() {
    assertThat(YamlNode.getLastKeyInPath("pipeline/stages/[1]/stage/spec/execution/steps/[1]/step/spec/connectorRef"))
        .isEqualTo("connectorRef");
    assertThat(YamlNode.getLastKeyInPath("pipeline/stages/[1]")).isEqualTo("[1]");
    assertThat(YamlNode.getLastKeyInPath("pipeline/stages/[1]/stage/spec/serviceConfig/serviceRef"))
        .isEqualTo("serviceRef");
    assertThat(YamlNode.getLastKeyInPath("pipeline")).isEqualTo("pipeline");
    assertThatThrownBy(() -> YamlNode.getLastKeyInPath("")).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> YamlNode.getLastKeyInPath(null)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStageLocalYamlPath() {
    assertThatThrownBy(pipelineNode::extractStageLocalYamlPath).hasMessage("Yaml node is not a node inside a stage.");
    YamlNode variables = pipelineNode.getFieldOrThrow("pipeline").getNode().getFieldOrThrow("variables").getNode();
    YamlNode var0 = variables.asArray().get(0);
    assertThatThrownBy(var0::extractStageLocalYamlPath).hasMessage("Yaml node is not a node inside a stage.");
    YamlNode stages = pipelineNode.getFieldOrThrow("pipeline").getNode().getFieldOrThrow("stages").getNode();
    YamlNode stage0 = stages.asArray().get(0);
    assertThatThrownBy(stage0::extractStageLocalYamlPath).hasMessage("Yaml node is not a node inside a stage.");
    YamlNode stageInternal = stage0.getFieldOrThrow("stage").getNode();
    assertThat(stageInternal.extractStageLocalYamlPath()).isEqualTo("stage");
    YamlNode stageName = stageInternal.getFieldOrThrow("name").getNode();
    assertThat(stageName.extractStageLocalYamlPath()).isEqualTo("stage/name");
    YamlNode serviceConfig = stageInternal.getFieldOrThrow("spec").getNode().getFieldOrThrow("execution").getNode();
    assertThat(serviceConfig.extractStageLocalYamlPath()).isEqualTo("stage/spec/execution");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetStageLocalYamlPathForParallel() {
    List<YamlNode> stages =
        pipelineNode.getFieldOrThrow("pipeline").getNode().getFieldOrThrow("stages").getNode().asArray();
    List<YamlNode> parallelStages = stages.get(1).getFieldOrThrow("parallel").getNode().asArray();
    YamlNode stage0Parallel = parallelStages.get(0).getFieldOrThrow("stage").getNode();
    YamlNode stage0ParallelName = stage0Parallel.getFieldOrThrow("name").getNode();
    assertThat(stage0ParallelName.extractStageLocalYamlPath()).isEqualTo("stage/name");
  }
}
