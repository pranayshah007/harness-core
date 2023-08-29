/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.creator.variables;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.RunStepNode;
import io.harness.beans.steps.outcome.CIStepOutcome;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.core.variables.OutputNGVariable;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@OwnedBy(HarnessTeam.CI)
public class RunStepVariableCreator extends GenericStepVariableCreator<RunStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet("Run");
  }

  @Override
  protected void addVariablesInComplexObject(Map<String, YamlProperties> yamlPropertiesMap,
      Map<String, YamlOutputProperties> yamlOutputPropertiesMap, YamlNode yamlNode) {
    addVariablesInComplexObjectRecursive(yamlPropertiesMap, yamlNode);

    YamlField outputVariablesField = yamlNode.getField(YAMLFieldNameConstants.OUTPUT_VARIABLES);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(outputVariablesField)) {
      addVariablesForOutputVariables(outputVariablesField, yamlOutputPropertiesMap);
    }
  }

  @Override
  public Class<RunStepNode> getFieldClass() {
    return RunStepNode.class;
  }

  @Override
  public YamlExtraProperties getStepExtraProperties(String fqnPrefix, String localNamePrefix, RunStepNode config) {
    YamlExtraProperties stepExtraProperties = super.getStepExtraProperties(fqnPrefix, localNamePrefix, config);

    Map<String, String> outputVariablesMap = new HashMap<>();
    if (config.getRunStepInfo().getOutputVariables().getValue() != null) {
      List<OutputNGVariable> outputNGVariables = config.getRunStepInfo().getOutputVariables().getValue();
      for (OutputNGVariable outputVariable : outputNGVariables) {
        outputVariablesMap.put(outputVariable.getName(), "variable");
      }
    }

    CIStepOutcome ciStepOutcome = CIStepOutcome.builder().outputVariables(outputVariablesMap).build();

    List<String> outputExpressions = VariableCreatorHelper.getExpressionsInObject(ciStepOutcome, "output");
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

  protected void addVariablesForOutputVariables(
      YamlField outputVariablesField, Map<String, YamlOutputProperties> yamlOutputPropertiesMap) {
    List<YamlNode> variableNodes = outputVariablesField.getNode().asArray();
    variableNodes.forEach(variableNode -> {
      YamlField uuidNode = variableNode.getField(YAMLFieldNameConstants.UUID);
      if (uuidNode != null) {
        // replace 'spec.outputVariables' with 'output.outputVariables' for output variables paths.
        String original = YAMLFieldNameConstants.SPEC + "." + YAMLFieldNameConstants.OUTPUT_VARIABLES;
        String replacement = YAMLFieldNameConstants.OUTPUT + "." + YAMLFieldNameConstants.OUTPUT_VARIABLES;
        String fqn = YamlUtils.getFullyQualifiedName(uuidNode.getNode()).replace(original, replacement);
        String localName =
            YamlUtils.getQualifiedNameTillGivenField(uuidNode.getNode(), YAMLFieldNameConstants.EXECUTION)
                .replace(original, replacement);
        String variableName =
            Objects.requireNonNull(variableNode.getField(YAMLFieldNameConstants.NAME)).getNode().asText();
        yamlOutputPropertiesMap.put(
            uuidNode.getNode().asText(), YamlOutputProperties.newBuilder().setLocalName(localName).setFqn(fqn).build());
      }
    });
  }

  private void addVariablesInComplexObjectRecursive(Map<String, YamlProperties> yamlPropertiesMap, YamlNode yamlNode) {
    List<String> extraFields = new ArrayList<>();
    extraFields.add(YAMLFieldNameConstants.UUID);
    extraFields.add(YAMLFieldNameConstants.IDENTIFIER);
    extraFields.add(YAMLFieldNameConstants.TYPE);
    extraFields.add(YAMLFieldNameConstants.OUTPUT_VARIABLES);
    List<YamlField> fields = yamlNode.fields();
    fields.forEach(field -> {
      if (field.getNode().isObject()) {
        addVariablesInComplexObjectRecursive(yamlPropertiesMap, field.getNode());
      } else if (field.getNode().isArray()) {
        List<YamlNode> innerFields = field.getNode().asArray();
        if (!extraFields.contains(field.getName())) {
          innerFields.forEach(f -> {
            if (!extraFields.contains(f.getName())) {
              addVariablesInComplexObjectRecursive(yamlPropertiesMap, f);
            }
          });
        }
      } else if (!extraFields.contains(field.getName())) {
        addFieldToPropertiesMapUnderStep(field, yamlPropertiesMap);
      }
    });
  }
}
