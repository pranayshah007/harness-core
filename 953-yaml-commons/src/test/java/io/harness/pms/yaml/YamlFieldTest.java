/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlFieldBlob;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class YamlFieldTest extends CategoryTest {
  private static final Charset CHARSET = Charset.forName(StandardCharsets.UTF_8.name());

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetName() throws IOException {
    String yamlContent = fetchFileContent();
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    String name = yamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode().getName();

    assertThat(name).isEqualTo("Manager Service Deployment");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testToFieldBlob() throws IOException {
    String yamlContent = fetchFileContent();
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    YamlFieldBlob yaml = yamlField.toFieldBlob();
    YamlFieldBlob expectedResult =
        YamlFieldBlob.newBuilder().setBlob(ByteString.copyFrom(JsonUtils.asJson(yamlField), CHARSET)).build();
    assertThat(expectedResult).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCheckIfParentIsParallel() throws IOException {
    String yamlContent = fetchFileContent();
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    // Pipeline Node
    YamlNode pipelineNode = yamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode();

    // Stages Node
    YamlField stagesNode = pipelineNode.getField(YAMLFieldNameConstants.STAGES);
    // Stage1 Node
    YamlField stage1Field = stagesNode.getNode()
                                .asArray()
                                .get(2)
                                .getField(YAMLFieldNameConstants.PARALLEL)
                                .getNode()
                                .asArray()
                                .get(0)
                                .getField(YAMLFieldNameConstants.STAGE);

    // Since parallel stage, parent as stages will be true
    assertThat(stage1Field.checkIfParentIsParallel(YAMLFieldNameConstants.STAGES)).isTrue();
    assertThat(stage1Field.checkIfParentIsParallel(YAMLFieldNameConstants.STEPS)).isFalse();

    YamlField parallelStepField = stagesNode.getNode()
                                      .asArray()
                                      .get(0)
                                      .getField(YAMLFieldNameConstants.STAGE)
                                      .getNode()
                                      .getField(YAMLFieldNameConstants.SPEC)
                                      .getNode()
                                      .getField(YAMLFieldNameConstants.EXECUTION)
                                      .getNode()
                                      .getField(YAMLFieldNameConstants.STEPS)
                                      .getNode()
                                      .asArray()
                                      .get(3)
                                      .getField(YAMLFieldNameConstants.PARALLEL)
                                      .getNode()
                                      .asArray()
                                      .get(0)
                                      .getField(YAMLFieldNameConstants.STEP);

    // Since parallel step and its parent will have stages up the heirarchy hence returning true.
    assertThat(parallelStepField.checkIfParentIsParallel(YAMLFieldNameConstants.STAGES)).isTrue();
    assertThat(parallelStepField.checkIfParentIsParallel(YAMLFieldNameConstants.STEPS)).isTrue();

    YamlField normalStepField = stagesNode.getNode()
                                    .asArray()
                                    .get(0)
                                    .getField(YAMLFieldNameConstants.STAGE)
                                    .getNode()
                                    .getField(YAMLFieldNameConstants.SPEC)
                                    .getNode()
                                    .getField(YAMLFieldNameConstants.EXECUTION)
                                    .getNode()
                                    .getField(YAMLFieldNameConstants.STEPS)
                                    .getNode()
                                    .asArray()
                                    .get(0)
                                    .getField(YAMLFieldNameConstants.STEP);
    assertThat(normalStepField.checkIfParentIsParallel(YAMLFieldNameConstants.STAGES)).isFalse();
    assertThat(normalStepField.checkIfParentIsParallel(YAMLFieldNameConstants.STEPS)).isFalse();

    YamlField normalStepFieldUnderParallelStage = stage1Field.getNode()
                                                      .getField(YAMLFieldNameConstants.SPEC)
                                                      .getNode()
                                                      .getField(YAMLFieldNameConstants.EXECUTION)
                                                      .getNode()
                                                      .getField(YAMLFieldNameConstants.STEPS)
                                                      .getNode()
                                                      .asArray()
                                                      .get(0)
                                                      .getField(YAMLFieldNameConstants.STEP);
    assertThat(normalStepFieldUnderParallelStage.checkIfParentIsParallel(YAMLFieldNameConstants.STAGES)).isTrue();
    assertThat(normalStepFieldUnderParallelStage.checkIfParentIsParallel(YAMLFieldNameConstants.STEPS)).isFalse();
  }

  private String fetchFileContent() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    return yamlContent;
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testFromFieldBlob() throws IOException {
    String yamlContent = fetchFileContent();
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    YamlFieldBlob result =
        YamlFieldBlob.newBuilder().setBlob(ByteString.copyFrom(JsonUtils.asJson(yamlField), CHARSET)).build();
    YamlField yaml = yamlField.fromFieldBlob(result);
    YamlField expectedResult = JsonUtils.asObject(result.getBlob().toString(CHARSET), YamlField.class);
    assertThat(expectedResult).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetYamlPath() throws IOException {
    String yamlContent = fetchFileContent();
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    String path =
        yamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode().asArray().get(1).getYamlPath();
    assertThat(path).isEqualTo("pipeline/[1]");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetType() throws IOException {
    String yamlContent = fetchFileContent();
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    YamlNode pipelineNode = yamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode();
    YamlField stagesNode = pipelineNode.getField(YAMLFieldNameConstants.STAGES);
    String stage1Field = stagesNode.getNode().asArray().get(1).getField(YAMLFieldNameConstants.STAGE).getType();

    assertThat(stage1Field).isEqualTo("Deployment");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetNodeName() throws IOException {
    String yamlContent = fetchFileContent();
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    YamlNode pipelineNode = yamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode();
    YamlField stagesNode = pipelineNode.getField(YAMLFieldNameConstants.STAGES);
    String stage1Field = stagesNode.getNode().asArray().get(1).getField(YAMLFieldNameConstants.STAGE).getNodeName();

    assertThat(stage1Field).isEqualTo("prod stage");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetId() throws IOException {
    String yamlContent = fetchFileContent();
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    YamlNode pipelineNode = yamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode();
    YamlField stagesNode = pipelineNode.getField(YAMLFieldNameConstants.STAGES);
    String stage1Field = stagesNode.getNode().asArray().get(1).getField(YAMLFieldNameConstants.STAGE).getId();

    assertThat(stage1Field).isEqualTo("Deployment");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testFromYamlPath() throws IOException {
    String yamlContent = fetchFileContent();
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    YamlField pipeline = yamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE).fromYamlPath("stages/[1]/stage");
    String name = pipeline.getNodeName();

    assertThat(name).isEqualTo("prod stage");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testGetUuid() throws IOException {
    String yamlContent = fetchFileContent();
    YamlField yamlField = YamlUtils.readTree(YamlUtils.injectUuid(yamlContent));
    YamlNode pipelineNode = yamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode();
    String stagesNode = pipelineNode.getField(YAMLFieldNameConstants.STAGES).getUuid();

    assertThat(stagesNode).isNotNull();
  }
}
