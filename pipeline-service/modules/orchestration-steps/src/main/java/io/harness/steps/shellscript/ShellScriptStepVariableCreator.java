/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.pipeline.variables.GenericStepVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.YamlTypes;
import io.harness.yaml.core.variables.NGVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(PIPELINE)
public class ShellScriptStepVariableCreator extends GenericStepVariableCreator<ShellScriptStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Collections.singleton(StepSpecTypeConstants.SHELL_SCRIPT);
  }

  @Override
  protected void addVariablesInComplexObject(Map<String, YamlProperties> yamlPropertiesMap,
      Map<String, YamlOutputProperties> yamlOutputPropertiesMap, YamlNode yamlNode) {
    List<String> complexFields = new ArrayList<>();
    complexFields.add(YamlTypes.SOURCE);
    complexFields.add(YamlTypes.EXECUTION_TARGET);
    complexFields.add(YamlTypes.ENVIRONMENT_VARIABLES);
    complexFields.add(YamlTypes.OUTPUT_VARIABLES);

    List<YamlField> fields = yamlNode.fields();
    fields.forEach(field -> {
      if (!field.getName().equals(YAMLFieldNameConstants.UUID) && !complexFields.contains(field.getName())) {
        addFieldToPropertiesMapUnderStep(field, yamlPropertiesMap);
      }
    });

    YamlField sourceField = yamlNode.getField(YamlTypes.SOURCE);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(sourceField)) {
      addVariablesForSourceField(sourceField, yamlPropertiesMap);
    }

    YamlField executionTargetField = yamlNode.getField(YamlTypes.EXECUTION_TARGET);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(executionTargetField)) {
      addVariablesForExecutionTargetField(executionTargetField, yamlPropertiesMap);
    }

    YamlField environmentVariablesField = yamlNode.getField(YamlTypes.ENVIRONMENT_VARIABLES);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(environmentVariablesField)) {
      addVariablesForVariables(environmentVariablesField, yamlPropertiesMap);
    }

    YamlField outputVariablesField = yamlNode.getField(YamlTypes.OUTPUT_VARIABLES);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(outputVariablesField)) {
      addVariablesForOutputVariables(outputVariablesField, yamlOutputPropertiesMap);
    }
  }

  @Override
  public Class<ShellScriptStepNode> getFieldClass() {
    return ShellScriptStepNode.class;
  }

  private void addVariablesForSourceField(YamlField sourceField, Map<String, YamlProperties> yamlPropertiesMap) {
    YamlField typeField = sourceField.getNode().getField(YamlNode.TYPE_FIELD_NAME);
    if (typeField != null) {
      YamlField specField = sourceField.getNode().getField(YamlTypes.SPEC);
      switch (typeField.getNode().getCurrJsonNode().textValue()) {
        case ShellScriptSourceType.GIT:
        case ShellScriptSourceType.INLINE:
          if (specField != null) {
            List<YamlField> fields = specField.getNode().fields();
            fields.forEach(field -> {
              if (!field.getName().equals(YAMLFieldNameConstants.UUID)) {
                addFieldToPropertiesMapUnderStep(field, yamlPropertiesMap);
              }
            });
          }
          break;
        default:
          throw new InvalidRequestException("Invalid source type");
      }
    }
  }

  private void addVariablesForExecutionTargetField(
      YamlField executionTargetField, Map<String, YamlProperties> yamlPropertiesMap) {
    List<YamlField> fields = executionTargetField.getNode().fields();
    fields.forEach(field -> {
      if (!field.getName().equals(YAMLFieldNameConstants.UUID)) {
        addFieldToPropertiesMapUnderStep(field, yamlPropertiesMap);
      }
    });
  }

  @Override
  public YamlExtraProperties getStepExtraProperties(
      String fqnPrefix, String localNamePrefix, ShellScriptStepNode config) {
    YamlExtraProperties stepExtraProperties = super.getStepExtraProperties(fqnPrefix, localNamePrefix, config);

    // empty map so that expressions are added for this even if no variables are added
    Map<String, String> outputVariablesMap = new HashMap<>();

    if (config.getShellScriptStepInfo().getOutputVariables() != null) {
      for (NGVariable outputVariable : config.getShellScriptStepInfo().getOutputVariables()) {
        outputVariablesMap.put(outputVariable.getName(), "variable");
      }
    }

    ShellScriptOutcome shellScriptOutcome = ShellScriptOutcome.builder().outputVariables(outputVariablesMap).build();

    List<String> outputExpressions = VariableCreatorHelper.getExpressionsInObject(shellScriptOutcome, "output");
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
