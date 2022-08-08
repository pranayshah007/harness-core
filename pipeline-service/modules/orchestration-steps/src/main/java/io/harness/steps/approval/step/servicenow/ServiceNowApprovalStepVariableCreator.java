/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class ServiceNowApprovalStepVariableCreator extends GenericStepVariableCreator<ServiceNowApprovalStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    Set<String> strings = new HashSet<>();
    strings.add(StepSpecTypeConstants.SERVICENOW_APPROVAL);
    return strings;
  }

  @Override
  protected void addVariablesInComplexObject(Map<String, YamlProperties> yamlPropertiesMap,
      Map<String, YamlOutputProperties> yamlOutputPropertiesMap, YamlNode yamlNode) {
    List<String> complexFields = new ArrayList<>();
    complexFields.add("approvalCriteria");
    complexFields.add("rejectionCriteria");

    List<YamlField> fields = yamlNode.fields();
    fields.forEach(field -> {
      if (!field.getName().equals(YAMLFieldNameConstants.UUID) && !complexFields.contains(field.getName())) {
        addFieldToPropertiesMapUnderStep(field, yamlPropertiesMap);
      }
    });

    complexFields.forEach(field -> {
      YamlField yamlField = yamlNode.getField(field);
      if (VariableCreatorHelper.isNotYamlFieldEmpty(yamlField)) {
        YamlField yamlField1 = yamlField.getNode().getField("spec");
        if (VariableCreatorHelper.isNotYamlFieldEmpty(yamlField1)) {
          YamlField yamlField2 = yamlField1.getNode().getField("conditions");
          if (VariableCreatorHelper.isNotYamlFieldEmpty(yamlField2)) {
            addVariablesForCriteria(yamlField2, yamlPropertiesMap);
          }
        }
      }
    });
  }

  @Override
  public Class<ServiceNowApprovalStepNode> getFieldClass() {
    return ServiceNowApprovalStepNode.class;
  }

  private void addVariablesForCriteria(YamlField yamlField, Map<String, YamlProperties> yamlPropertiesMap) {
    List<YamlNode> yamlNodes = yamlField.getNode().asArray();
    yamlNodes.forEach(yamlNode -> {
      YamlField uuidNode = yamlNode.getField(YAMLFieldNameConstants.UUID);
      if (uuidNode != null) {
        addFieldToPropertiesMapUnderStep(uuidNode, yamlPropertiesMap);
      }
    });
  }
}
