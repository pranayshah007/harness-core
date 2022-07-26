/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.variables;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.yaml.core.VariableExpression.IteratePolicy.REGULAR_WITH_CUSTOM_FIELD;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.yaml.core.VariableExpression;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.swagger.annotations.ApiModelProperty;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class VariableCreatorHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void createVariablesForChildrenNodesV2WithNullMapAndList() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineCreatorBase.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField pipelineField = fullYamlField.getNode().getField("pipeline");

    PipelineInfoConfig pipelineInfoConfig =
        YamlUtils.read(pipelineField.getNode().toString(), PipelineInfoConfig.class);
    Map<String, YamlExtraProperties> yamlExtraPropertiesMap = new HashMap<>();
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();

    VariableCreatorHelper.collectVariableExpressions(
        pipelineInfoConfig, yamlPropertiesMap, yamlExtraPropertiesMap, "pipeline", "pipeline");

    // check for extra properties expressions
    assertThat(yamlExtraPropertiesMap.containsKey(pipelineInfoConfig.getUuid())).isTrue();
    List<String> fqnExtraPropertiesList = yamlExtraPropertiesMap.get(pipelineInfoConfig.getUuid())
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsAll(Arrays.asList("pipeline.variables", "pipeline.identifier", "pipeline.tags", "pipeline.properties",
            "pipeline.delegateSelectors"));

    // check for name and description expressions
    assertThat(yamlPropertiesMap.containsKey(pipelineInfoConfig.getName())).isTrue();
    assertThat(yamlPropertiesMap.get(pipelineInfoConfig.getName()).getFqn()).isEqualTo("pipeline.name");

    assertThat(yamlPropertiesMap.containsKey(pipelineInfoConfig.getDescription().getResponseField())).isTrue();
    assertThat(yamlPropertiesMap.get(pipelineInfoConfig.getDescription().getResponseField()).getFqn())
        .isEqualTo("pipeline.description");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void createVariablesForChildrenNodesV2WithNullMapAndListForStage() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineCreatorBase.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField pipelineField = fullYamlField.getNode().getField("pipeline");

    PipelineInfoConfig pipelineInfoConfig =
        YamlUtils.read(pipelineField.getNode().toString(), PipelineInfoConfig.class);
    Map<String, YamlExtraProperties> yamlExtraPropertiesMap = new HashMap<>();
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();

    StageElementConfig stageElementConfig =
        YamlUtils.read(pipelineInfoConfig.getStages().get(0).getStage().toString(), StageElementConfig.class);

    VariableCreatorHelper.collectVariableExpressions(
        stageElementConfig, yamlPropertiesMap, yamlExtraPropertiesMap, "pipeline.stages.stage1", "stage");

    // check for extra properties expressions
    assertThat(yamlExtraPropertiesMap.containsKey(stageElementConfig.getUuid())).isTrue();
    List<String> fqnExtraPropertiesList = yamlExtraPropertiesMap.get(stageElementConfig.getUuid())
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsAll(Arrays.asList("pipeline.stages.stage1.variables", "pipeline.stages.stage1.identifier",
            "pipeline.stages.stage1.tags", "pipeline.stages.stage1.type"));

    // enum when condition expression
    fqnExtraPropertiesList = yamlExtraPropertiesMap.get(stageElementConfig.getWhen().getUuid())
                                 .getPropertiesList()
                                 .stream()
                                 .map(YamlProperties::getFqn)
                                 .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsAll(Collections.singletonList("pipeline.stages.stage1.when.pipelineStatus"));

    // check for name and description and when condition expressions
    assertThat(yamlPropertiesMap.containsKey(stageElementConfig.getName())).isTrue();
    assertThat(yamlPropertiesMap.get(stageElementConfig.getName()).getFqn()).isEqualTo("pipeline.stages.stage1.name");

    assertThat(yamlPropertiesMap.containsKey(stageElementConfig.getDescription().getResponseField())).isTrue();
    assertThat(yamlPropertiesMap.get(stageElementConfig.getDescription().getResponseField()).getFqn())
        .isEqualTo("pipeline.stages.stage1.description");

    assertThat(yamlPropertiesMap.containsKey(stageElementConfig.getWhen().getCondition().getResponseField())).isTrue();
    assertThat(yamlPropertiesMap.get(stageElementConfig.getWhen().getCondition().getResponseField()).getFqn())
        .isEqualTo("pipeline.stages.stage1.when.condition");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void createVariablesForChildrenNodesV2WithMapAndListValues() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineWithTagsAndVariables.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField pipelineField = fullYamlField.getNode().getField("pipeline");

    PipelineInfoConfig pipelineInfoConfig =
        YamlUtils.read(pipelineField.getNode().toString(), PipelineInfoConfig.class);
    Map<String, YamlExtraProperties> yamlExtraPropertiesMap = new HashMap<>();
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();

    VariableCreatorHelper.collectVariableExpressions(
        pipelineInfoConfig, yamlPropertiesMap, yamlExtraPropertiesMap, "pipeline", "pipeline");

    // check for extra properties expressions
    assertThat(yamlExtraPropertiesMap.containsKey(pipelineInfoConfig.getUuid())).isTrue();
    List<String> fqnExtraPropertiesList = yamlExtraPropertiesMap.get(pipelineInfoConfig.getUuid())
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList).containsOnly("pipeline.identifier");
    // null resources field in extraProperties
    assertThat(yamlExtraPropertiesMap.containsKey(pipelineInfoConfig.getProperties().getCi().getCodebase().getUuid()))
        .isTrue();

    // check for name expression
    assertThat(yamlPropertiesMap.containsKey(pipelineInfoConfig.getName())).isTrue();
    assertThat(yamlPropertiesMap.get(pipelineInfoConfig.getName()).getFqn()).isEqualTo("pipeline.name");

    // check for description expression
    assertThat(yamlPropertiesMap.containsKey(pipelineInfoConfig.getDescription().getResponseField())).isTrue();
    assertThat(yamlPropertiesMap.get(pipelineInfoConfig.getDescription().getResponseField()).getFqn())
        .isEqualTo("pipeline.description");

    // variables field (list type)
    assertThat(
        yamlPropertiesMap.containsKey(pipelineInfoConfig.getVariables().get(0).getCurrentValue().getResponseField()))
        .isTrue();
    assertThat(
        yamlPropertiesMap.get(pipelineInfoConfig.getVariables().get(0).getCurrentValue().getResponseField()).getFqn())
        .isEqualTo("pipeline.variables.test1");
    assertThat(
        yamlPropertiesMap.containsKey(pipelineInfoConfig.getVariables().get(0).getCurrentValue().getResponseField()))
        .isTrue();
    assertThat(
        yamlPropertiesMap.get(pipelineInfoConfig.getVariables().get(1).getCurrentValue().getResponseField()).getFqn())
        .isEqualTo("pipeline.variables.test2");

    // Map object type expressions
    assertThat(yamlPropertiesMap.containsKey(pipelineInfoConfig.getTags().get("t1"))).isTrue();
    assertThat(yamlPropertiesMap.get(pipelineInfoConfig.getTags().get("t1")).getFqn()).isEqualTo("pipeline.tags.t1");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void createVariablesForParameterFieldHavingObject() throws IOException {
    // Null value for dummyB
    DummyA dummyA = DummyA.builder().build();
    Map<String, YamlExtraProperties> yamlExtraPropertiesMap = new HashMap<>();
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();

    VariableCreatorHelper.collectVariableExpressions(dummyA, yamlPropertiesMap, yamlExtraPropertiesMap, "test", "test");
    assertThat(yamlPropertiesMap.containsKey(dummyA.getDummyB().getResponseField())).isTrue();
    assertThat(yamlPropertiesMap.get(dummyA.getDummyB().getResponseField()).getFqn()).isEqualTo("test.dummyB");

    // dummyB field as an expression.
    dummyA = DummyA.builder().dummyB(ParameterField.createExpressionField(true, "<+b>", null, false)).build();
    yamlExtraPropertiesMap = new HashMap<>();
    yamlPropertiesMap = new HashMap<>();
    VariableCreatorHelper.collectVariableExpressions(dummyA, yamlPropertiesMap, yamlExtraPropertiesMap, "test", "test");
    assertThat(yamlPropertiesMap.containsKey(dummyA.getDummyB().getResponseField())).isTrue();
    assertThat(yamlPropertiesMap.get(dummyA.getDummyB().getResponseField()).getFqn()).isEqualTo("test.dummyB");

    DummyD dummyD = DummyD.builder().key("d1").value("v1").build();
    DummyC dummyC = DummyC.builder().a("aValue").b(ParameterField.createValueField("b")).c(1).build();
    Map<String, String> m1 = new HashMap<>();
    m1.put("m1", "v1");
    DummyB dummyB = DummyB.builder()
                        .dummyC(ParameterField.createValueField(dummyC))
                        .fieldWithListString(ParameterField.createValueField(Arrays.asList("a", "b")))
                        .listDummyD(ParameterField.createValueField(Collections.singletonList(dummyD)))
                        .listDummyC(ParameterField.createValueField(Collections.singletonList(dummyC)))
                        .mapField(ParameterField.createValueField(m1))
                        .build();
    DummyF dummyF = DummyF.builder().identifier("dummyF1").value("v1").build();
    DummyE dummyE = DummyE.builder().dummyF(dummyF).build();
    List<DummyE> dummyEList = Collections.singletonList(dummyE);
    dummyA = DummyA.builder().dummyB(ParameterField.createValueField(dummyB)).listDummyE(dummyEList).build();
    yamlExtraPropertiesMap = new HashMap<>();
    yamlPropertiesMap = new HashMap<>();
    VariableCreatorHelper.collectVariableExpressions(dummyA, yamlPropertiesMap, yamlExtraPropertiesMap, "test", "test");
    assertThat(yamlExtraPropertiesMap.containsKey(dummyA.getDummyB().getValue().getDummyC().getValue().getUuid()))
        .isTrue();
    assertThat(yamlExtraPropertiesMap.get(dummyA.getDummyB().getValue().getDummyC().getValue().getUuid())
                   .getProperties(0)
                   .getFqn())
        .isEqualTo("test.dummyB.dummyC.c");
    assertThat(yamlExtraPropertiesMap.containsKey(dummyA.getDummyB().getValue().getUuid())).isTrue();
    assertThat(yamlExtraPropertiesMap.get(dummyA.getDummyB().getValue().getUuid()).getProperties(0).getFqn())
        .isEqualTo("test.dummyB.listDummyC");

    assertThat(yamlPropertiesMap.containsKey(dummyA.getDummyB().getValue().getFieldWithListString().getResponseField()))
        .isTrue();
    assertThat(
        yamlPropertiesMap.get(dummyA.getDummyB().getValue().getFieldWithListString().getResponseField()).getFqn())
        .isEqualTo("test.dummyB.fieldWithListString");
    assertThat(yamlPropertiesMap.containsKey(dummyA.getDummyB().getValue().getMapField().getValue().get("m1")))
        .isTrue();
    assertThat(yamlPropertiesMap.get(dummyA.getDummyB().getValue().getMapField().getValue().get("m1")).getFqn())
        .isEqualTo("test.dummyB.mapField.m1");
    assertThat(
        yamlPropertiesMap.containsKey(dummyA.getDummyB().getValue().getListDummyD().getValue().get(0).getValue()))
        .isTrue();
    assertThat(
        yamlPropertiesMap.get(dummyA.getDummyB().getValue().getListDummyD().getValue().get(0).getValue()).getFqn())
        .isEqualTo("test.dummyB.listDummyD.d1");
    assertThat(yamlPropertiesMap.containsKey(dummyA.getDummyB().getValue().getDummyC().getValue().getA())).isTrue();
    assertThat(yamlPropertiesMap.get(dummyA.getDummyB().getValue().getDummyC().getValue().getA()).getFqn())
        .isEqualTo("test.dummyB.dummyC.a");
    assertThat(
        yamlPropertiesMap.containsKey(dummyA.getDummyB().getValue().getDummyC().getValue().getB().getResponseField()))
        .isTrue();
    assertThat(
        yamlPropertiesMap.get(dummyA.getDummyB().getValue().getDummyC().getValue().getB().getResponseField()).getFqn())
        .isEqualTo("test.dummyB.dummyC.b");
  }

  @Data
  @Builder
  private static class DummyA {
    @ApiModelProperty(hidden = true) String uuid;
    ParameterField<DummyB> dummyB;
    List<DummyE> listDummyE;
  }

  @Data
  @Builder
  private static class DummyB {
    @ApiModelProperty(hidden = true) String uuid;
    ParameterField<DummyC> dummyC;
    ParameterField<List<String>> fieldWithListString;
    ParameterField<List<DummyD>> listDummyD;
    ParameterField<List<DummyC>> listDummyC;
    ParameterField<Map<String, String>> mapField;
  }

  @Data
  @Builder
  private static class DummyC {
    @ApiModelProperty(hidden = true) String uuid;
    String a;
    ParameterField<String> b;
    Integer c;
  }

  @Data
  @Builder
  private static class DummyD {
    @VariableExpression(skipVariableExpression = true) String key;
    @VariableExpression(policy = REGULAR_WITH_CUSTOM_FIELD) String value;
  }

  @Data
  @Builder
  private static class DummyE {
    DummyF dummyF;
  }

  @Data
  @Builder
  private static class DummyF {
    @VariableExpression(skipVariableExpression = true) String identifier;
    String value;
  }
}
