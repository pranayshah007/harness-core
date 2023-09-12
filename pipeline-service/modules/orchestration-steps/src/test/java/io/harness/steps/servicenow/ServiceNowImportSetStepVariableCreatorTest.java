/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.servicenow.importset.ServiceNowImportSetStepNode;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ServiceNowImportSetStepVariableCreatorTest extends CategoryTest {
  ServiceNowImportSetStepVariableCreator serviceNowImportSetStepVariableCreator =
      new ServiceNowImportSetStepVariableCreator();

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetClassType() {
    assertThat(serviceNowImportSetStepVariableCreator.getFieldClass()).isEqualTo(ServiceNowImportSetStepNode.class);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> stepTypes = serviceNowImportSetStepVariableCreator.getSupportedStepTypes();
    assertThat(stepTypes.size()).isEqualTo(1);
    assertThat(stepTypes.contains(StepSpecTypeConstants.SERVICENOW_IMPORT_SET)).isTrue();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testCreateVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("serviceNowImportSetVariableCreatorUuidJson.yaml");
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
    VariableCreationResponse variablesForParentNodeV2 =
        serviceNowImportSetStepVariableCreator.createVariablesForParentNodeV2(
            VariableCreationContext.builder().currentField(stepField).build(),
            YamlUtils.read(stepField.getNode().toString(), ServiceNowImportSetStepNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.name",
            "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.description",
            "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.timeout",
            "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.spec.stagingTableName",
            "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.spec.connectorRef",
            "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.spec.delegateSelectors",
            "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.when");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("-QisXuoqS_eJZTnix7M2PQ") // uuid for step node
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.identifier",
            "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.type",
            "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.startTs",
            "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.endTs",
            "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.status");
    List<YamlProperties> outputProperties = variablesForParentNodeV2.getYamlExtraProperties()
                                                .get("-QisXuoqS_eJZTnix7M2PQ") // uuid for step node
                                                .getOutputPropertiesList();
    List<YamlProperties> properties = variablesForParentNodeV2.getYamlExtraProperties()
                                          .get("-QisXuoqS_eJZTnix7M2PQ") // uuid for step node
                                          .getPropertiesList();
    assertThat(outputProperties).hasSize(5);
    assertThat(properties).hasSize(5);
    assertYamlProperties(outputProperties.get(0),
        "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.output.importSetNumber",
        "execution.steps.app.output.importSetNumber", "", true);
    assertYamlProperties(outputProperties.get(1),
        "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.output.stagingTable",
        "execution.steps.app.output.stagingTable", "", true);
    assertYamlProperties(outputProperties.get(2),
        "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.output.transformMapOutcomes",
        "execution.steps.app.output.transformMapOutcomes", "", true);
    assertYamlProperties(outputProperties.get(3),
        "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.output.transformMapOutcomes[0].transformMap",
        "execution.steps.app.output.transformMapOutcomes[0].transformMap", "", true);
    assertYamlProperties(outputProperties.get(4),
        "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.output.transformMapOutcomes[0].status",
        "execution.steps.app.output.transformMapOutcomes[0].status", "", true);
    assertYamlProperties(properties.get(0),
        "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.type", "execution.steps.app.type",
        "type", true);
    assertYamlProperties(properties.get(1),
        "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.identifier",
        "execution.steps.app.identifier", "identifier", true);
    assertYamlProperties(properties.get(2),
        "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.startTs",
        "execution.steps.app.startTs", "", false);
    assertYamlProperties(properties.get(3),
        "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.endTs", "execution.steps.app.endTs",
        "", false);
    assertYamlProperties(properties.get(4),
        "pipeline.stages.Service_Now_Import_Set_Identifier.spec.execution.steps.app.status",
        "execution.steps.app.status", "", false);
  }

  private void assertYamlProperties(
      YamlProperties yaml, String fqn, String localName, String variableName, boolean visible) {
    assertThat(yaml).isNotNull();
    assertThat(yaml.getFqn()).isEqualTo(fqn);
    assertThat(yaml.getLocalName()).isEqualTo(localName);
    assertThat(yaml.getVisible()).isEqualTo(visible);
    assertThat(yaml.getVariableName()).isEqualTo(variableName);
  }
}
