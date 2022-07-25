/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.plancreator.stages.stage.StageInfoConfig;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChildrenVariableCreatorTest extends CategoryTest {
  DummyStageVariableCreation dummyStageVariableCreation = new DummyStageVariableCreation();
  private static final String STAGE_ID = "NnmWEe_TRXCba1-R2EsDrw";

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void createVariablesForChildrenNodes() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("approval_stage.json");
    String json = Resources.toString(testFile, Charsets.UTF_8);
    JsonNode jsonNode = JsonUtils.asObject(json, JsonNode.class);
    YamlNode approvalYamlNode = new YamlNode("stage", jsonNode);
    YamlField yamlField = new YamlField(approvalYamlNode);
    LinkedHashMap<String, VariableCreationResponse> variablesMap =
        dummyStageVariableCreation.createVariablesForChildrenNodes(null, yamlField);
    assertThat(variablesMap.get(STAGE_ID)).isNull();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void getSupportedTypes() {
    assertThat(dummyStageVariableCreation.getSupportedTypes()).isNull();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(dummyStageVariableCreation.getFieldClass()).isEqualTo(DummyStageNode.class);
  }

  @Test
  @Owner(developers = SAHIL)
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
    VariableCreationResponse variablesForParentNodeV2 = dummyStageVariableCreation.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stageField).build(),
        YamlUtils.read(stageField.getNode().toString(), DummyStageNode.class));
    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList).isEmpty();
  }

  public static class DummyStageVariableCreation extends ChildrenVariableCreator<DummyStageNode> {
    @Override
    public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
        VariableCreationContext ctx, YamlField config) {
      return new LinkedHashMap<>();
    }

    @Override
    public VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config) {
      return VariableCreationResponse.builder().build();
    }

    @Override
    public Map<String, Set<String>> getSupportedTypes() {
      return null;
    }

    @Override
    public Class<DummyStageNode> getFieldClass() {
      return DummyStageNode.class;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("DummyStageNode")
  public static class DummyStageNode extends AbstractStageNode {
    @Override
    public String getType() {
      return "DummyStageNode";
    }

    @Override
    public StageInfoConfig getStageInfoConfig() {
      return null;
    }
  }

  public static class DummyStageInfoConfig implements StageInfoConfig {
    @Override
    public ExecutionElementConfig getExecution() {
      return ExecutionElementConfig.builder().build();
    }
  }
}
