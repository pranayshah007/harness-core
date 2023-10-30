/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanCreatorUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetStageConfig() throws IOException {
    // Pipeline Node
    YamlNode pipelineNode = getPipelineNode();

    // Stages Node
    YamlField stagesNode = pipelineNode.getField("stages");
    // Stage1 Node
    YamlField stage1Field = stagesNode.getNode().asArray().get(0).getField("stage");

    // Stage2 Node
    YamlNode stage2Node = stagesNode.getNode().asArray().get(1).getField("stage").getNode();
    // Stage1 Service Node
    YamlField serviceNode = stage2Node.getField("spec").getNode().getField("service");

    YamlField actualStage1Field = PlanCreatorUtils.getStageConfig(serviceNode, stage1Field.getNode().getIdentifier());
    assertThat(actualStage1Field).isEqualTo(stage1Field);

    assertThat(PlanCreatorUtils.getStageConfig(serviceNode, "")).isNull();
    assertThat(PlanCreatorUtils.getStageConfig(stagesNode, "any")).isNull();
    assertThat(PlanCreatorUtils.getStageConfig(serviceNode, "defNotExistingStageId")).isNull();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testSupportsField() throws IOException {
    YamlField stageField = getPipelineNode().getField("stages").getNode().asArray().get(0).getField("stage");
    assertThat(PlanCreatorUtils.supportsField(null, stageField, HarnessYamlVersion.V0)).isFalse();
    assertThat(PlanCreatorUtils.supportsField(
                   ImmutableMap.of("random", ImmutableSet.of("random")), stageField, HarnessYamlVersion.V0))
        .isFalse();
    assertThat(PlanCreatorUtils.supportsField(
                   ImmutableMap.of("stage", ImmutableSet.of("random")), stageField, HarnessYamlVersion.V0))
        .isFalse();
    assertThat(PlanCreatorUtils.supportsField(ImmutableMap.of("stage", ImmutableSet.of("random", "Deployment")),
                   stageField, HarnessYamlVersion.V0))
        .isTrue();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetStepYamlFields() throws IOException {
    List<YamlNode> stepNodes = getPipelineNode()
                                   .getField("stages")
                                   .getNode()
                                   .asArray()
                                   .get(0)
                                   .getField("stage")
                                   .getNode()
                                   .getField("spec")
                                   .getNode()
                                   .getField("execution")
                                   .getNode()
                                   .getField("steps")
                                   .getNode()
                                   .asArray();
    List<YamlField> stepFields = PlanCreatorUtils.getStepYamlFields(stepNodes);
    assertThat(stepFields.size()).isEqualTo(4);
    assertThat(stepFields.stream().map(YamlField::getName).collect(Collectors.toList()))
        .containsExactly("step", "step", "step", "parallel");
  }

  private YamlNode getPipelineNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    assertThat(testFile).isNotNull();
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    return yamlField.getNode().getField("pipeline").getNode();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void getDependencyNodeIdsForParallelNode() throws IOException {
    YamlField parallelField = getPipelineNode()
                                  .getField("stages")
                                  .getNode()
                                  .asArray()
                                  .get(0)
                                  .getField("stage")
                                  .getNode()
                                  .getField("spec")
                                  .getNode()
                                  .getField("execution")
                                  .getNode()
                                  .getField("steps")
                                  .getNode()
                                  .asArray()
                                  .get(3)
                                  .getField("parallel");
    assertThat(PlanCreatorUtils.getDependencyNodeIdsForParallelNode(parallelField)).isNotEmpty();
    assertThat(PlanCreatorUtils.getDependencyNodeIdsForParallelNode(parallelField).size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void getAutoLogContext() throws IOException {
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder().build();
    String accountId = "accountId";
    String orgId = "orgId";
    String projectIdentifier = "projectIdentifier";

    assertThat(PlanCreatorUtils.autoLogContext(executionMetadata, accountId, orgId, projectIdentifier)).isNotNull();
  }
}
