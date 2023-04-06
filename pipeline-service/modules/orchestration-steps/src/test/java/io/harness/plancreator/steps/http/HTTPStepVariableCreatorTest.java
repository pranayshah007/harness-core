/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.http;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class HTTPStepVariableCreatorTest extends CategoryTest {
  HTTPStepVariableCreator httpStepVariableCreator = new HTTPStepVariableCreator();

  String emptyVariablesAndHeaders = "pipeline:\n"
      + "  step:\n"
      + "    name: search\n"
      + "    identifier: search\n"
      + "    type: Http\n"
      + "    timeout: 10m\n"
      + "    spec:\n"
      + "      url: uuid1\n"
      + "      method: uuid2\n"
      + "      headers: []\n"
      + "      outputVariables: []\n"
      + "      requestBody: uuid3\n"
      + "      assertion: uuid4";

  String bareMinimum = "pipeline:\n"
      + "  step:\n"
      + "    name: search\n"
      + "    identifier: search\n"
      + "    type: Http\n"
      + "    timeout: 10m\n"
      + "    spec:\n"
      + "      url: uuid1\n"
      + "      method: uuid2";

  String headers = "pipeline:\n"
      + "  step:\n"
      + "    name: search\n"
      + "    identifier: search\n"
      + "    type: Http\n"
      + "    timeout: 10m\n"
      + "    spec:\n"
      + "      url: uuid1\n"
      + "      method: uuid2\n"
      + "      headers:\n"
      + "        - key: aq\n"
      + "          value: uuid3\n"
      + "        - key: aw\n"
      + "          value: uuid4";

  String missingHeaderKey = "pipeline:\n"
      + "  step:\n"
      + "    name: search\n"
      + "    identifier: search\n"
      + "    type: Http\n"
      + "    timeout: 10m\n"
      + "    spec:\n"
      + "      url: uuid1\n"
      + "      method: uuid2\n"
      + "      headers:\n"
      + "        - key: aq\n"
      + "          value: uuid3\n"
      + "        - value: uuid4";

  String outputVariables = "pipeline:\n"
      + "  step:\n"
      + "    name: search\n"
      + "    identifier: search\n"
      + "    type: Http\n"
      + "    timeout: 10m\n"
      + "    spec:\n"
      + "      url: uuid1\n"
      + "      method: uuid2\n"
      + "      outputVariables:\n"
      + "        - name: m\n"
      + "          value: uuid3\n"
      + "          type: String\n"
      + "          __uuid: eh";
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes =
        new io.harness.plancreator.steps.http.HTTPStepVariableCreator().getSupportedStepTypes();
    assertThat(supportedStepTypes).hasSize(1);
    assertThat(supportedStepTypes).contains("Http");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testAddVariablesInComplexObject() throws IOException {
    io.harness.plancreator.steps.http.HTTPStepVariableCreator variableCreator = new HTTPStepVariableCreator();

    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();
    Map<String, YamlOutputProperties> yamlOutputPropertiesMap = new HashMap<>();

    YamlNode step1 = getStepNode(emptyVariablesAndHeaders);
    variableCreator.addVariablesInComplexObject(yamlPropertiesMap, yamlOutputPropertiesMap, step1);
    assertThat(yamlOutputPropertiesMap).hasSize(0);
    assertThat(yamlPropertiesMap).hasSize(4);
    assertThat(yamlPropertiesMap).containsKeys("uuid1", "uuid2", "uuid3", "uuid4");

    yamlPropertiesMap.clear();
    yamlOutputPropertiesMap.clear();
    YamlNode step2 = getStepNode(bareMinimum);
    variableCreator.addVariablesInComplexObject(yamlPropertiesMap, yamlOutputPropertiesMap, step2);
    assertThat(yamlOutputPropertiesMap).hasSize(0);
    assertThat(yamlPropertiesMap).hasSize(2);
    assertThat(yamlPropertiesMap).containsKeys("uuid1", "uuid2");

    yamlPropertiesMap.clear();
    yamlOutputPropertiesMap.clear();
    YamlNode step3 = getStepNode(headers);
    variableCreator.addVariablesInComplexObject(yamlPropertiesMap, yamlOutputPropertiesMap, step3);
    assertThat(yamlOutputPropertiesMap).hasSize(0);
    assertThat(yamlPropertiesMap).hasSize(4);
    assertThat(yamlPropertiesMap).containsKeys("uuid1", "uuid2", "aq", "aw");

    yamlPropertiesMap.clear();
    yamlOutputPropertiesMap.clear();
    YamlNode step4 = getStepNode(missingHeaderKey);
    assertThatThrownBy(
        () -> variableCreator.addVariablesInComplexObject(yamlPropertiesMap, yamlOutputPropertiesMap, step4))
        .isInstanceOf(InvalidRequestException.class);

    yamlPropertiesMap.clear();
    yamlOutputPropertiesMap.clear();
    YamlNode step5 = getStepNode(outputVariables);
    variableCreator.addVariablesInComplexObject(yamlPropertiesMap, yamlOutputPropertiesMap, step5);
    assertThat(yamlOutputPropertiesMap).hasSize(1);
    assertThat(yamlOutputPropertiesMap).containsKeys("uuid3");
    assertThat(yamlPropertiesMap).hasSize(2);
    assertThat(yamlPropertiesMap).containsKeys("uuid1", "uuid2");
  }

  private YamlNode getStepNode(String yaml) throws IOException {
    return YamlUtils.readTree(yaml)
        .getNode()
        .getField("pipeline")
        .getNode()
        .getField("step")
        .getNode()
        .getField("spec")
        .getNode();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(httpStepVariableCreator.getFieldClass()).isEqualTo(HttpStepNode.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipelineVariableCreatorUuidJson.yaml");
    String pipelineJson = Resources.toString(testFile, Charsets.UTF_8);
    YamlField fullYamlField = YamlUtils.readTree(pipelineJson);

    // Pipeline Node
    YamlField stepField = fullYamlField.getNode()
                              .getField("pipeline")
                              .getNode()
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
                              .get(0)
                              .getField("step");
    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 = httpStepVariableCreator.createVariablesForParentNodeV2(
        VariableCreationContext.builder().currentField(stepField).build(),
        YamlUtils.read(stepField.getNode().toString(), HttpStepNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.step1.name",
            "pipeline.stages.stage1.spec.execution.steps.step1.description",
            "pipeline.stages.stage1.spec.execution.steps.step1.timeout",
            "pipeline.stages.stage1.spec.execution.steps.step1.spec.headers.head1",
            "pipeline.stages.stage1.spec.execution.steps.step1.spec.headers.head2",
            "pipeline.stages.stage1.spec.execution.steps.step1.spec.delegateSelectors",
            "pipeline.stages.stage1.spec.execution.steps.step1.spec.url",
            "pipeline.stages.stage1.spec.execution.steps.step1.spec.method",
            "pipeline.stages.stage1.spec.execution.steps.step1.spec.requestBody",
            "pipeline.stages.stage1.spec.execution.steps.step1.spec.assertion",
            "pipeline.stages.stage1.spec.execution.steps.step1.spec.certificate",
            "pipeline.stages.stage1.spec.execution.steps.step1.spec.certificateKey",
            "pipeline.stages.stage1.spec.execution.steps.step1.when");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("xtkQAaoNRkCgtI5mU8KnEQ") // pipeline uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.step1.identifier",
            "pipeline.stages.stage1.spec.execution.steps.step1.type",
            "pipeline.stages.stage1.spec.execution.steps.step1.startTs",
            "pipeline.stages.stage1.spec.execution.steps.step1.endTs");

    // yaml extra properties
    List<String> fqnOutputPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                               .get("xtkQAaoNRkCgtI5mU8KnEQ") // pipeline uuid
                                               .getOutputPropertiesList()
                                               .stream()
                                               .map(YamlProperties::getFqn)
                                               .collect(Collectors.toList());
    assertThat(fqnOutputPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.step1.output.httpUrl",
            "pipeline.stages.stage1.spec.execution.steps.step1.output.httpMethod",
            "pipeline.stages.stage1.spec.execution.steps.step1.output.httpResponseCode",
            "pipeline.stages.stage1.spec.execution.steps.step1.output.httpResponseBody",
            "pipeline.stages.stage1.spec.execution.steps.step1.output.status",
            "pipeline.stages.stage1.spec.execution.steps.step1.output.outputVariables.o1",
            "pipeline.stages.stage1.spec.execution.steps.step1.output.errorMsg");
  }
}
