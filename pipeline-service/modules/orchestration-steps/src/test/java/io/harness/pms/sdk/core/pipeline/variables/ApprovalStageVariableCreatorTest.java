/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.variables;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.steps.approval.stage.ApprovalStageNode;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ApprovalStageVariableCreatorTest extends CategoryTest {
  ApprovalStageVariableCreator approvalStageVariableCreator = new ApprovalStageVariableCreator();
  private static final String STAGE_ID = "NnmWEe_TRXCba1-R2EsDrw";

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void createVariablesForChildrenNodes() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("approval_stage.json");
    String json = Resources.toString(testFile, Charsets.UTF_8);
    JsonNode jsonNode = JsonUtils.asObject(json, JsonNode.class);
    YamlNode approvalYamlNode = new YamlNode("stage", jsonNode);
    YamlField yamlField = new YamlField(approvalYamlNode);
    LinkedHashMap<String, VariableCreationResponse> variablesMap =
        approvalStageVariableCreator.createVariablesForChildrenNodes(null, yamlField);
    assertThat(variablesMap.get(STAGE_ID)).isNotNull();
    String yamlPath = variablesMap.get(STAGE_ID).getDependencies().getDependenciesMap().get(STAGE_ID);
    YamlField fullYamlField = YamlUtils.readTree(json);
    assertThat(fullYamlField).isNotNull();
    YamlField specYaml = fullYamlField.fromYamlPath(yamlPath);
    assertThat(yamlField.getNode().getFieldName()).isNotEmpty();
    assertThat(specYaml.getName()).isEqualTo("execution");
    assertThat(specYaml.getNode().fetchKeys()).containsExactlyInAnyOrder("steps", "rollbackSteps", "__uuid");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void getSupportedTypes() {
    assertThat(approvalStageVariableCreator.getSupportedTypes())
        .containsEntry(YAMLFieldNameConstants.STAGE, Collections.singleton("Approval"));
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(approvalStageVariableCreator.getFieldClass()).isEqualTo(ApprovalStageNode.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void createVariablesForParentNodes() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineVariableCreatorUuidJson.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField stageField = fullYamlField.getNode()
                               .getField("pipeline")
                               .getNode()
                               .getField("stages")
                               .getNode()
                               .asArray()
                               .get(0)
                               .getField("stage");
    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = approvalStageVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stageField).build(),
        YamlUtils.read(stageField.getNode().toString(), ApprovalStageNode.class));
    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsAll(Arrays.asList("pipeline.stages.stage1.description", "pipeline.stages.stage1.name",
            "pipeline.stages.stage1.when.condition"));

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("tGufMZnYTNCcFFLz74wtpA") // pipeline uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsAll(Arrays.asList("pipeline.stages.stage1.variables", "pipeline.stages.stage1.identifier",
            "pipeline.stages.stage1.startTs", "pipeline.stages.stage1.endTs", "pipeline.stages.stage1.tags",
            "pipeline.stages.stage1.type"));
  }
}
