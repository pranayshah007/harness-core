/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.variables;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.yaml.core.properties.NGProperties;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineVariableCreatorTest extends CategoryTest {
  PipelineVariableCreator pipelineVariableCreator = new PipelineVariableCreator();

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void createVariablesForChildrenNodesV2() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline_creator.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField pipelineField = fullYamlField.getNode().getField("pipeline");
    String stage1UUid = "J79wvZyYTFacTmbV-98-HQ";
    String stage2UUid = "Z7hglsxLT2GDzpYs9QMnWQ";

    // Check if children stage node are added to children dependencies
    LinkedHashMap<String, VariableCreationResponse> variablesForChildrenNodesV2 =
        pipelineVariableCreator.createVariablesForChildrenNodesV2(
            VariableCreationContext.builder().currentField(pipelineField).build(),
            YamlUtils.read(pipelineField.getNode().toString(), PipelineInfoConfig.class));
    assertThat(variablesForChildrenNodesV2.containsKey(stage1UUid)).isTrue();
    assertThat(variablesForChildrenNodesV2.containsKey(stage2UUid)).isTrue();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void createVariablesForParentNodesV2() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline_creator.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField pipelineField = fullYamlField.getNode().getField("pipeline");
    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = pipelineVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(pipelineField).build(),
        YamlUtils.read(pipelineField.getNode().toString(), PipelineInfoConfig.class));
    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsAll(Arrays.asList("pipeline.description", "pipeline.name", "pipeline.tags.tag1", "pipeline.tags.tag2",
            "pipeline.variables.var1"));

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("Isxcs6g9RdalBS6gPTPwWg") // pipeline uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.identifier", "pipeline.sequenceId", "pipeline.executionId", "pipeline.startTs",
            "pipeline.endTs", "pipeline.properties", "pipeline.triggerType", "pipeline.triggeredBy.name",
            "pipeline.triggeredBy.email", "pipeline.triggeredBy.triggerIdentifier");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void getSupportedTypes() {
    assertThat(pipelineVariableCreator.getSupportedTypes())
        .containsEntry(YAMLFieldNameConstants.PIPELINE, Collections.singleton("__any__"));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertEquals(pipelineVariableCreator.getFieldClass(), PipelineInfoConfig.class);
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetPipelineExtraProperties() {
    YamlExtraProperties yamlExtraProperties = pipelineVariableCreator.getPipelineExtraProperties(
        PipelineInfoConfig.builder().properties(NGProperties.builder().build()).build());
    assertEquals(yamlExtraProperties.getPropertiesList().size(), 12);
    assertThat(yamlExtraProperties.getPropertiesList().contains(
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".sequenceId").build()));
    assertThat(yamlExtraProperties.getPropertiesList().contains(
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".executionId").build()));
    assertThat(yamlExtraProperties.getPropertiesList().contains(
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".triggerType").build()));
    assertThat(yamlExtraProperties.getPropertiesList().contains(
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".triggeredBy.name").build()));
    assertThat(yamlExtraProperties.getPropertiesList().contains(
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".triggeredBy.email").build()));
    assertThat(yamlExtraProperties.getPropertiesList().contains(
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".triggeredBy.triggerName").build()));
    assertThat(yamlExtraProperties.getPropertiesList().contains(
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".startTs").build()));
    assertThat(yamlExtraProperties.getPropertiesList().contains(
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".endTs").build()));
    String propertiesExpressionPath = "pipeline.properties.ci.codebase.build";
    assertThat(yamlExtraProperties.getPropertiesList().contains(YamlProperties.newBuilder()
                                                                    .setFqn(propertiesExpressionPath + "."
                                                                        + "build.type")
                                                                    .build()));
    assertThat(yamlExtraProperties.getPropertiesList().contains(YamlProperties.newBuilder()
                                                                    .setFqn(propertiesExpressionPath + "."
                                                                        + "build.spec.branch")
                                                                    .build()));
    assertThat(yamlExtraProperties.getPropertiesList().contains(YamlProperties.newBuilder()
                                                                    .setFqn(propertiesExpressionPath + "."
                                                                        + "build.spec.tag")
                                                                    .build()));
    assertThat(yamlExtraProperties.getPropertiesList().contains(YamlProperties.newBuilder()
                                                                    .setFqn(propertiesExpressionPath + "."
                                                                        + "build.spec.number")
                                                                    .build()));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetStageYamlFields() throws IOException {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline_creator.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField stagesYamlNode = YamlUtils.readTree(pipelineJson)
                                   .getNode()
                                   .getField(YAMLFieldNameConstants.PIPELINE)
                                   .getNode()
                                   .getField(YAMLFieldNameConstants.STAGES);
    pipelineVariableCreator.getStageYamlFields(stagesYamlNode, responseMap);
    assertEquals(responseMap.size(), 2);
    assertThat(responseMap.containsKey("J79wvZyYTFacTmbV-98-HQ"));
    assertThat(responseMap.containsKey("Z7hglsxLT2GDzpYs9QMnWQ"));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testAddVariablesForPipeline() throws IOException {
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline_creator.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    pipelineVariableCreator.addVariablesForPipeline(yamlPropertiesMap,
        YamlUtils.readTree(pipelineJson).getNode().getField(YAMLFieldNameConstants.PIPELINE).getNode());
    assertEquals(yamlPropertiesMap.size(), 5);
    assertThat(yamlPropertiesMap.containsKey("pipeline1"));
    assertThat(yamlPropertiesMap.containsKey("var"));
    assertThat(yamlPropertiesMap.containsKey("this is test pipeline"));
    assertThat(yamlPropertiesMap.containsKey("tag1"));
    assertThat(yamlPropertiesMap.containsKey("tag2"));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline_creator.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    VariableCreationResponse variableCreationResponse = pipelineVariableCreator.createVariablesForParentNode(
        null, YamlUtils.readTree(pipelineJson).getNode().getField(YAMLFieldNameConstants.PIPELINE));
    assertEquals(variableCreationResponse.getYamlProperties().size(), 6);
    assertThat(variableCreationResponse.getYamlProperties().containsKey("pipeline1"));
    assertThat(variableCreationResponse.getYamlProperties().containsKey("var"));
    assertThat(variableCreationResponse.getYamlProperties().containsKey("this is test pipeline"));
    assertThat(variableCreationResponse.getYamlProperties().containsKey("tag1"));
    assertThat(variableCreationResponse.getYamlProperties().containsKey("tag2"));
    assertThat(variableCreationResponse.getYamlProperties().containsKey("Isxcs6g9RdalBS6gPTPwWg"));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testCreateVariablesForChildrenNodes() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline_creator.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    String stage1UUid = "J79wvZyYTFacTmbV-98-HQ";
    String stage2UUid = "Z7hglsxLT2GDzpYs9QMnWQ";
    LinkedHashMap<String, VariableCreationResponse> variables = pipelineVariableCreator.createVariablesForChildrenNodes(
        null, YamlUtils.readTree(pipelineJson).getNode().getField(YAMLFieldNameConstants.PIPELINE));
    assertThat(variables.containsKey(stage1UUid)).isTrue();
    assertThat(variables.containsKey(stage2UUid)).isTrue();
  }
}
