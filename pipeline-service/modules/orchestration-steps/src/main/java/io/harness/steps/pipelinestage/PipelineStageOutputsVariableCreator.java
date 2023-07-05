/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.pipelinestage;

import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.sdk.core.variables.ChildrenVariableCreator;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class PipelineStageOutputsVariableCreator extends ChildrenVariableCreator<YamlField> {
  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    return new LinkedHashMap<>();
  }

  /*
  Input Yaml: outputs:
                  - name: var1
                    value: <+pipeline.variables.testA>
                  - name: var2
                    value: <+pipeline.variables.testB>

   Output yaml: outputs:
                  - name: var1
                    value: randomUuid1
                  - name: var2
                    value: randomUuid2
   */
  private void updateOutputsYamlWithUUID(YamlField variablesField) {
    List<YamlNode> variableNodes = variablesField.getNode().asArray();
    variableNodes.forEach(variableNode -> {
      YamlField uuidNode = variableNode.getField(YAMLFieldNameConstants.UUID);
      if (uuidNode != null) {
        YamlField valueNode = variableNode.getField(YAMLFieldNameConstants.VALUE);
        valueNode.getNode().setCurrJsonNode(
            TextNode.valueOf(UUIDGenerator.generateUuid()), valueNode.getNode().getFieldName());
      }
    });
  }

  @Override
  public VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config) {
    return null;
  }

  private void addVariablesForOutputVariables(
      YamlField variablesField, Map<String, YamlProperties> yamlPropertiesMap, String original, String replacement) {
    List<YamlNode> variableNodes = variablesField.getNode().asArray();
    variableNodes.forEach(variableNode -> {
      YamlField uuidNode = variableNode.getField(YAMLFieldNameConstants.UUID);
      if (uuidNode != null) {
        String fqn = YamlUtils.getFullyQualifiedName(uuidNode.getNode()).replace(original, replacement);
        String localName = YamlUtils.getQualifiedNameTillGivenField(uuidNode.getNode(), YAMLFieldNameConstants.STAGES)
                               .replace(original, replacement);
        YamlField valueNode = variableNode.getField(YAMLFieldNameConstants.VALUE);
        String variableName =
            Objects.requireNonNull(variableNode.getField(YAMLFieldNameConstants.NAME)).getNode().asText();
        if (valueNode == null) {
          throw new InvalidRequestException(
              "Variable with name \"" + variableName + "\" added without any value. Fqn: " + fqn);
        }
        yamlPropertiesMap.put(valueNode.getNode().getCurrJsonNode().textValue(),
            YamlProperties.newBuilder()
                .setLocalName(localName)
                .setFqn(fqn)
                .setVariableName(variableName)
                .setVisible(true)
                .build());
      }
    });
  }

  public VariableCreationResponse createVariablesForParentNodeV2(VariableCreationContext ctx, YamlField config) {
    Map<String, YamlProperties> yamlPropertiesHashMap = new HashMap<>();
    String original = YAMLFieldNameConstants.SPEC + "." + YAMLFieldNameConstants.OUTPUTS;
    String replacement = YAMLFieldNameConstants.OUTPUT;
    YamlField outputsField = ctx.getCurrentField();

    // replacing spec.outputs with output
    updateOutputsYamlWithUUID(outputsField);
    HashMap<String, YamlField> outputs = new HashMap<>();
    outputs.put(outputsField.getUuid(), outputsField);
    addVariablesForOutputVariables(outputsField, yamlPropertiesHashMap, original, replacement);
    try {
      return VariableCreationResponse.builder()
          .yamlProperties(yamlPropertiesHashMap)
          .yamlUpdates(YamlUpdates.newBuilder()
                           .putFqnToYaml(
                               outputsField.getYamlPath(), YamlUtils.writeYamlString(outputsField).replace("---\n", ""))
                           .build())
          .build();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.OUTPUTS, Collections.singleton("__any__"));
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, YamlField config) {
    return createVariablesForChildrenNodes(ctx, ctx.getCurrentField());
  }

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }
}
