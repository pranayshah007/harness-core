/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.bamboo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class BambooBuildStepVariableCreator extends GenericStepVariableCreator<BambooBuildStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    Set<String> strings = new HashSet<>();
    strings.add(StepSpecTypeConstants.BAMBOO_BUILD);
    return strings;
  }

  @Override
  public Class<BambooBuildStepNode> getFieldClass() {
    return BambooBuildStepNode.class;
  }

  @Override
  protected void addVariablesInComplexObject(Map<String, YamlProperties> yamlPropertiesMap,
      Map<String, YamlOutputProperties> yamlOutputPropertiesMap, YamlNode yamlNode) {
    List<String> complexFields = new ArrayList<>();
    complexFields.add("fields");

    List<YamlField> fields = yamlNode.fields();
    fields.forEach(field -> {
      if (!field.getName().equals(YAMLFieldNameConstants.UUID) && !complexFields.contains(field.getName())) {
        addFieldToPropertiesMapUnderStep(field, yamlPropertiesMap);
      }
    });

    YamlField yamlField = yamlNode.getField("fields");
    if (VariableCreatorHelper.isNotYamlFieldEmpty(yamlField)) {
      addVariablesForFields(yamlField, yamlPropertiesMap);
    }
  }

  private void addVariablesForFields(YamlField yamlField, Map<String, YamlProperties> yamlPropertiesMap) {
    List<YamlNode> yamlNodes = yamlField.getNode().asArray();
    yamlNodes.forEach(yamlNode -> {
      YamlField uuidNode = yamlNode.getField(YAMLFieldNameConstants.UUID);
      if (uuidNode != null) {
        addFieldToPropertiesMapUnderStep(uuidNode, yamlPropertiesMap);
      }
    });
  }

  @Override
  public YamlExtraProperties getStepExtraProperties(
      String fqnPrefix, String localNamePrefix, BambooBuildStepNode config) {
    YamlExtraProperties stepExtraProperties = super.getStepExtraProperties(fqnPrefix, localNamePrefix, config);

    // empty map so that expressions are added for this even if no variables are added
    Map<String, String> outputVariablesMap = new HashMap<>();

    BambooBuildOutcome bambooBuildOutcome = BambooBuildOutcome.builder()
                                                .executionStatus(ExecutionStatus.SUCCESS)
                                                .buildNumber("")
                                                .buildStatus("")
                                                .buildUrl("")
                                                .parameters(Collections.emptyMap())
                                                .delegateMetaInfo(DelegateMetaInfo.builder().build())
                                                .errorMessage("")
                                                .planUrl("")
                                                .projectName("")
                                                .planName("")
                                                .build();

    List<String> outputExpressions = VariableCreatorHelper.getExpressionsInObject(bambooBuildOutcome, "build");
    List<YamlProperties> outputProperties = new LinkedList<>();
    for (String outputExpression : outputExpressions) {
      outputProperties.add(YamlProperties.newBuilder()
                               .setFqn(fqnPrefix + "." + outputExpression)
                               .setLocalName(localNamePrefix + "." + outputExpression)
                               .setVisible(true)
                               .build());
    }

    return YamlExtraProperties.newBuilder()
        .addAllProperties(stepExtraProperties.getPropertiesList())
        .addAllOutputProperties(outputProperties)
        .build();
  }
}
