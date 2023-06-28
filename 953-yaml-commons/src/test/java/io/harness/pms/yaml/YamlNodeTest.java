/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.YamlException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
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

  private String readFile(String filePath) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filePath)), StandardCharsets.UTF_8);
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

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetNodeYaml() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .addLevels(Level.newBuilder().setOriginalIdentifier("pipeline").buildPartial())
                            .addLevels(Level.newBuilder().setOriginalIdentifier("stages").buildPartial())
                            .addLevels(Level.newBuilder().setOriginalIdentifier("qaStage").buildPartial())
                            .build();

    String yaml = YamlUtils.writeYamlString(
        YamlNode.getNodeYaml(YamlUtils.readYamlTree(pipelineYaml).getNode(), ambiance.getLevelsList()));
    String stageYaml = readFile("stageyaml.yml");
    assertThat(yaml).isEqualTo(stageYaml);

    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder().setOriginalIdentifier("pipeline").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("stages").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("qaStage").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("spec").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("execution").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("steps").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("httpStep1").buildPartial())
                   .build();

    yaml = YamlUtils.writeYamlString(
        YamlNode.getNodeYaml(YamlUtils.readYamlTree(pipelineYaml).getNode(), ambiance.getLevelsList()));
    assertThat(yaml).isEqualTo("step:\n"
        + "  name: http step 1\n"
        + "  identifier: httpStep1\n"
        + "  type: Http\n"
        + "  spec:\n"
        + "    socketTimeoutMillis: 1000\n"
        + "    method: GET\n"
        + "    url: <+input>\n");

    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder().setOriginalIdentifier("pipeline").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("stages").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("qaStage4").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("spec").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("execution").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("steps").buildPartial())
                   .addLevels(Level.newBuilder().setOriginalIdentifier("httpStep8").buildPartial())
                   .build();
    yaml = YamlUtils.writeYamlString(
        YamlNode.getNodeYaml(YamlUtils.readYamlTree(pipelineYaml).getNode(), ambiance.getLevelsList()));
    assertThat(yaml).isEqualTo("step:\n"
        + "  name: http step 8\n"
        + "  identifier: httpStep8\n"
        + "  type: Http\n"
        + "  spec:\n"
        + "    socketTimeoutMillis: 1000\n"
        + "    method: GET\n"
        + "    url: https://google.com\n");

    // Adding strategy level. it should be ignored while traversing to field node and should get the exact above yaml.
    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                                  .setOriginalIdentifier("pipeline")
                                  .buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).build())
                                  .setOriginalIdentifier("stages")
                                  .buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setOriginalIdentifier("qaStage4Strategy<+strategy.identifierPostFix>")
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STRATEGY).build())
                                  .buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                  .setOriginalIdentifier("qaStage4<+strategy.identifierPostFix>")
                                  .buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setOriginalIdentifier("spec")
                                  .buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setOriginalIdentifier("execution")
                                  .buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setOriginalIdentifier("steps")
                                  .buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STEP).build())
                                  .setOriginalIdentifier("httpStep8")
                                  .buildPartial())
                   .build();
    yaml = YamlUtils.writeYamlString(
        YamlNode.getNodeYaml(YamlUtils.readYamlTree(pipelineYaml).getNode(), ambiance.getLevelsList()));
    assertThat(yaml).isEqualTo("step:\n"
        + "  name: http step 8\n"
        + "  identifier: httpStep8\n"
        + "  type: Http\n"
        + "  spec:\n"
        + "    socketTimeoutMillis: 1000\n"
        + "    method: GET\n"
        + "    url: https://google.com\n");
    // Adding parallel level. It should still only return the stage yaml of qaStage2
    ambiance = Ambiance.newBuilder()
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.PIPELINE).build())
                                  .setOriginalIdentifier("pipeline")
                                  .buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGES).build())
                                  .setOriginalIdentifier("stages")
                                  .buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setOriginalIdentifier("parallel234er")
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.FORK).build())
                                  .buildPartial())
                   .addLevels(Level.newBuilder()
                                  .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).build())
                                  .setOriginalIdentifier("qaStage2")
                                  .buildPartial())
                   .build();
    // pipeline yaml ("pipeline-extensive.yml") has the stage qaStage2 inside parallel node, we need to return only the
    // yaml of qaStage2 here and not the other stages in parallel with it.
    yaml = YamlUtils.writeYamlString(
        YamlNode.getNodeYaml(YamlUtils.readYamlTree(pipelineYaml).getNode(), ambiance.getLevelsList()));
    stageYaml = readFile("stageYaml2.yaml");
    assertThat(yaml).isEqualTo(stageYaml);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testReplacePathForAddingArrayElements() throws IOException {
    String pipelineYaml = "pipeline:\n"
        + "  stages:\n"
        + "  - stage:\n"
        + "      identifier: s1\n"
        + "  - stage:\n"
        + "      identifier: s2\n"
        + "  - stage:\n"
        + "      identifier: s3\n";
    YamlNode pipelineNode = YamlUtils.readTree(pipelineYaml).getNode();
    String newStage = "stage:\n"
        + "  identifier: s4\n";
    YamlNode newStageNode = YamlUtils.readTree(newStage).getNode();
    pipelineNode.replacePath("pipeline/stages/[3]", newStageNode.getCurrJsonNode());
    assertThat(pipelineNode.getField("pipeline").getNode().getField("stages").getNode().asArray()).hasSize(4);
    assertThat(pipelineNode.toString())
        .isEqualTo(
            "{\"pipeline\":{\"stages\":[{\"stage\":{\"identifier\":\"s1\"}},{\"stage\":{\"identifier\":\"s2\"}},{\"stage\":{\"identifier\":\"s3\"}},{\"stage\":{\"identifier\":\"s4\"}}]}}");

    assertThatThrownBy(() -> pipelineNode.replacePath("pipeline/stages/[5]", newStageNode.getCurrJsonNode()))
        .isInstanceOf(YamlException.class)
        .hasMessage("Incorrect index path ([5]) on array node");

    String replacementStage = "stage:\n"
        + "  identifier: s1.1\n";
    YamlNode replacementStageNode = YamlUtils.readTree(replacementStage).getNode();
    pipelineNode.replacePath("pipeline/stages/[1]", replacementStageNode.getCurrJsonNode());
    assertThat(pipelineNode.getField("pipeline").getNode().getField("stages").getNode().asArray()).hasSize(4);
    assertThat(pipelineNode.toString())
        .isEqualTo(
            "{\"pipeline\":{\"stages\":[{\"stage\":{\"identifier\":\"s1\"}},{\"stage\":{\"identifier\":\"s1.1\"}},{\"stage\":{\"identifier\":\"s3\"}},{\"stage\":{\"identifier\":\"s4\"}}]}}");
  }
}
