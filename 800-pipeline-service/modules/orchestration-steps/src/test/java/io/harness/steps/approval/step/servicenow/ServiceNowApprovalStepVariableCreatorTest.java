/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow;

import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceNowApprovalStepVariableCreatorTest extends CategoryTest {
  String serviceNowApprovalYaml = "pipeline:\n"
      + " step:\n"
      + "                                type: ServiceNowApproval\n"
      + "                                name: snow\n"
      + "                                identifier: snow\n"
      + "                                spec:\n"
      + "                                    connectorRef: nowservice\n"
      + "                                    ticketNumber: PRB0066229\n"
      + "                                    ticketType: PROBLEM\n"
      + "                                    approvalCriteria:\n"
      + "                                        type: KeyValues\n"
      + "                                        spec:\n"
      + "                                            matchAnyCondition: true\n"
      + "                                            conditions:\n"
      + "                                                - key: state\n"
      + "                                                  operator: equals\n"
      + "                                                  value: New\n"
      + "                                                  __uuid: UUID2\n"
      + "                                    rejectionCriteria:\n"
      + "                                        type: KeyValues\n"
      + "                                        spec:\n"
      + "                                            matchAnyCondition: true\n"
      + "                                            conditions:\n"
      + "                                                - key: state\n"
      + "                                                  operator: equals\n"
      + "                                                  value: Scheduled\n"
      + "                                                  __uuid: UUID1\n"
      + "                                timeout: 1d";

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetSupportedStepTypes() {
    Set<String> supportedStepTypes = new ServiceNowApprovalStepVariableCreator().getSupportedStepTypes();
    assertThat(supportedStepTypes).hasSize(1);
    assertThat(supportedStepTypes).contains("ServiceNowApproval");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testAddVariablesInComplexObject() throws IOException {
    ServiceNowApprovalStepVariableCreator variableCreator = new ServiceNowApprovalStepVariableCreator();

    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();
    Map<String, YamlOutputProperties> yamlOutputPropertiesMap = new HashMap<>();

    YamlNode step1 = getStepNode(serviceNowApprovalYaml);
    variableCreator.addVariablesInComplexObject(yamlPropertiesMap, yamlOutputPropertiesMap, step1);
    assertThat(yamlOutputPropertiesMap).hasSize(0);
    assertThat(yamlPropertiesMap).hasSize(5);
    assertThat(yamlPropertiesMap).containsKeys("UUID1", "UUID2", "nowservice", "PROBLEM", "PRB0066229");
    assertThat(yamlPropertiesMap.values().stream().map(YamlProperties::getFqn))
        .containsExactlyInAnyOrder("pipeline.spec.ticketNumber", "pipeline.spec.ticketType",
            "pipeline.spec.connectorRef", "pipeline.spec.approvalCriteria.spec.conditions.state",
            "pipeline.spec.approvalCriteria.spec.conditions.state");
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
}
