/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.HINGER;

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
import io.harness.steps.servicenow.update.ServiceNowUpdateStepNode;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ServiceNowUpdateStepVariableCreatorTest extends CategoryTest {
  ServiceNowUpdateStepVariableCreator serviceNowUpdateStepVariableCreator = new ServiceNowUpdateStepVariableCreator();

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void getClassType() {
    assertThat(serviceNowUpdateStepVariableCreator.getFieldClass()).isEqualTo(ServiceNowUpdateStepNode.class);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void createVariablesForParentNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("serviceNowVariableCreatorUuidJson.yaml");
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
                              .get(1)
                              .getField("step");

    // yaml input expressions
    VariableCreationResponse variablesForParentNodeV2 =
        serviceNowUpdateStepVariableCreator.createVariablesForParentNodeV2(
            VariableCreationContext.builder().currentField(stepField).build(),
            YamlUtils.read(stepField.getNode().toString(), ServiceNowUpdateStepNode.class));

    List<String> fqnPropertiesList = variablesForParentNodeV2.getYamlProperties()
                                         .values()
                                         .stream()
                                         .map(YamlProperties::getFqn)
                                         .collect(Collectors.toList());
    assertThat(fqnPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.snow_update.name",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.description",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.timeout",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.spec.ticketType",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.spec.connectorRef",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.spec.delegateSelectors",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.spec.useServiceNowTemplate",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.spec.templateName",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.spec.ticketNumber",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.spec.fields.short_description",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.spec.fields.description",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.spec.fields.priority",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.when");

    // yaml extra properties
    List<String> fqnExtraPropertiesList = variablesForParentNodeV2.getYamlExtraProperties()
                                              .get("WKlNrJ8FTtKAazSDLog2DA") // pipeline uuid
                                              .getPropertiesList()
                                              .stream()
                                              .map(YamlProperties::getFqn)
                                              .collect(Collectors.toList());
    assertThat(fqnExtraPropertiesList)
        .containsOnly("pipeline.stages.stage1.spec.execution.steps.snow_update.identifier",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.type",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.startTs",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.endTs",
            "pipeline.stages.stage1.spec.execution.steps.snow_update.status");

    List<YamlProperties> outputProperties = variablesForParentNodeV2.getYamlExtraProperties()
                                                .get("WKlNrJ8FTtKAazSDLog2DA") // uuid for step node
                                                .getOutputPropertiesList();
    List<YamlProperties> properties = variablesForParentNodeV2.getYamlExtraProperties()
                                          .get("WKlNrJ8FTtKAazSDLog2DA") // uuid for step node
                                          .getPropertiesList();
    assertThat(outputProperties).hasSize(4);
    assertThat(properties).hasSize(5);
    assertYamlProperties(outputProperties.get(0),
        "pipeline.stages.stage1.spec.execution.steps.snow_update.ticket.ticketUrl",
        "execution.steps.snow_update.ticket.ticketUrl", "", true);
    assertYamlProperties(outputProperties.get(1),
        "pipeline.stages.stage1.spec.execution.steps.snow_update.ticket.ticketNumber",
        "execution.steps.snow_update.ticket.ticketNumber", "", true);
    assertYamlProperties(outputProperties.get(2),
        "pipeline.stages.stage1.spec.execution.steps.snow_update.ticket.fields",
        "execution.steps.snow_update.ticket.fields", "", true);
    assertYamlProperties(properties.get(0), "pipeline.stages.stage1.spec.execution.steps.snow_update.type",
        "execution.steps.snow_update.type", "type", true);
    assertYamlProperties(properties.get(1), "pipeline.stages.stage1.spec.execution.steps.snow_update.identifier",
        "execution.steps.snow_update.identifier", "identifier", true);
    assertYamlProperties(properties.get(2), "pipeline.stages.stage1.spec.execution.steps.snow_update.startTs",
        "execution.steps.snow_update.startTs", "", false);
    assertYamlProperties(properties.get(3), "pipeline.stages.stage1.spec.execution.steps.snow_update.endTs",
        "execution.steps.snow_update.endTs", "", false);
    assertYamlProperties(properties.get(4), "pipeline.stages.stage1.spec.execution.steps.snow_update.status",
        "execution.steps.snow_update.status", "", false);
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
