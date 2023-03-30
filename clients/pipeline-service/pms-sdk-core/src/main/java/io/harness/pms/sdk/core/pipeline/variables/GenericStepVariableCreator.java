/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.variables;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STRATEGY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.sdk.core.variables.ChildrenVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public abstract class GenericStepVariableCreator<T extends AbstractStepNode> extends ChildrenVariableCreator<T> {
  public abstract Set<String> getSupportedStepTypes();

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    Set<String> stepTypes = getSupportedStepTypes();
    if (EmptyPredicate.isEmpty(stepTypes)) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(STEP, stepTypes);
  }

  @Override
  public VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config) {
    YamlNode node = config.getNode();
    String stepUUID = node.getUuid();
    Map<String, YamlProperties> yamlPropertiesMap = new LinkedHashMap<>();
    Map<String, YamlOutputProperties> yamlOutputPropertiesMap = new LinkedHashMap<>();

    yamlPropertiesMap.put(stepUUID,
        YamlProperties.newBuilder()
            .setLocalName(YAMLFieldNameConstants.STEP)
            .setFqn(YamlUtils.getFullyQualifiedName(node))
            .build());
    addVariablesForStep(yamlPropertiesMap, yamlOutputPropertiesMap, node);
    return VariableCreationResponse.builder()
        .yamlOutputProperties(yamlOutputPropertiesMap)
        .yamlProperties(yamlPropertiesMap)
        .build();
  }

  private void addVariablesForStep(Map<String, YamlProperties> yamlPropertiesMap,
      Map<String, YamlOutputProperties> yamlOutputPropertiesMap, YamlNode yamlNode) {
    YamlField nameField = yamlNode.getField(YAMLFieldNameConstants.NAME);
    if (nameField != null) {
      addFieldToPropertiesMapUnderStep(nameField, yamlPropertiesMap);
    }
    YamlField descriptionField = yamlNode.getField(YAMLFieldNameConstants.DESCRIPTION);
    if (descriptionField != null) {
      addFieldToPropertiesMapUnderStep(descriptionField, yamlPropertiesMap);
    }

    YamlField timeoutField = yamlNode.getField(YAMLFieldNameConstants.TIMEOUT);
    if (timeoutField != null) {
      addFieldToPropertiesMapUnderStep(timeoutField, yamlPropertiesMap);
    }

    YamlField specField = yamlNode.getField(YAMLFieldNameConstants.SPEC);
    if (specField != null) {
      addVariablesInComplexObject(yamlPropertiesMap, yamlOutputPropertiesMap, specField.getNode());
    }
  }

  protected void addVariablesInComplexObject(Map<String, YamlProperties> yamlPropertiesMap,
      Map<String, YamlOutputProperties> yamlOutputPropertiesMap, YamlNode yamlNode) {
    List<String> extraFields = new ArrayList<>();
    extraFields.add(YAMLFieldNameConstants.UUID);
    extraFields.add(YAMLFieldNameConstants.IDENTIFIER);
    extraFields.add(YAMLFieldNameConstants.TYPE);
    List<YamlField> fields = yamlNode.fields();
    fields.forEach(field -> {
      if (field.getNode().isObject()) {
        addVariablesInComplexObject(yamlPropertiesMap, yamlOutputPropertiesMap, field.getNode());
      } else if (field.getNode().isArray()) {
        List<YamlNode> innerFields = field.getNode().asArray();
        innerFields.forEach(f -> addVariablesInComplexObject(yamlPropertiesMap, yamlOutputPropertiesMap, f));
      } else if (!extraFields.contains(field.getName())) {
        addFieldToPropertiesMapUnderStep(field, yamlPropertiesMap);
      }
    });
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    LinkedHashMap<String, VariableCreationResponse> responseLinkedHashMap = new LinkedHashMap<>();
    YamlField strategyField = config.getNode().getField(STRATEGY);

    if (strategyField != null) {
      Map<String, YamlField> strategyDependencyMap = new HashMap<>();
      strategyDependencyMap.put(strategyField.getNode().getUuid(), strategyField);
      responseLinkedHashMap.put(strategyField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(strategyDependencyMap))
              .build());
    }
    return responseLinkedHashMap;
  }

  protected void addFieldToPropertiesMapUnderStep(YamlField fieldNode, Map<String, YamlProperties> yamlPropertiesMap) {
    String fqn = YamlUtils.getFullyQualifiedName(fieldNode.getNode());
    String finalFqn = fqn;
    String localName = fqn;
    if (fqn.contains(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)) {
      localName =
          YamlUtils.getQualifiedNameTillGivenField(fieldNode.getNode(), YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE);
    } else if (fqn.contains(YAMLFieldNameConstants.ENVIRONMENT) && fqn.contains(YAMLFieldNameConstants.PROVISIONER)) {
      // remove environment. from the path
      // provisioner path should be stage.spec.provisioner.
      finalFqn = fqn.replace(YAMLFieldNameConstants.ENVIRONMENT + ".", "");
      localName = finalFqn;
    } else {
      localName = YamlUtils.getQualifiedNameTillGivenField(fieldNode.getNode(), YAMLFieldNameConstants.EXECUTION);
    }

    yamlPropertiesMap.put(fieldNode.getNode().getCurrJsonNode().textValue(),
        YamlProperties.newBuilder().setLocalName(localName).setFqn(finalFqn).build());
  }

  protected void addVariablesForVariables(YamlField variablesField, Map<String, YamlProperties> yamlPropertiesMap) {
    List<YamlNode> variableNodes = variablesField.getNode().asArray();
    variableNodes.forEach(variableNode -> {
      YamlField uuidNode = variableNode.getField(YAMLFieldNameConstants.UUID);
      if (uuidNode != null) {
        String fqn = YamlUtils.getFullyQualifiedName(uuidNode.getNode());
        String localName =
            YamlUtils.getQualifiedNameTillGivenField(uuidNode.getNode(), YAMLFieldNameConstants.EXECUTION);
        YamlField valueNode = variableNode.getField(YAMLFieldNameConstants.VALUE);
        String variableName =
            Objects.requireNonNull(variableNode.getField(YAMLFieldNameConstants.NAME)).getNode().asText();
        if (valueNode == null) {
          throw new InvalidRequestException(
              "Variable with name \"" + variableName + "\" added without any value. Fqn: " + fqn);
        }
        yamlPropertiesMap.put(valueNode.getNode().getCurrJsonNode().textValue(),
            YamlProperties.newBuilder().setLocalName(localName).setFqn(fqn).build());
      }
    });
  }

  protected void addVariablesForOutputVariables(
      YamlField variablesField, Map<String, YamlOutputProperties> yamlOutputPropertiesMap) {
    List<YamlNode> variableNodes = variablesField.getNode().asArray();
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
        YamlField valueNode = variableNode.getField(YAMLFieldNameConstants.VALUE);
        String variableName =
            Objects.requireNonNull(variableNode.getField(YAMLFieldNameConstants.NAME)).getNode().asText();
        if (valueNode == null) {
          throw new InvalidRequestException(
              "Variable with name \"" + variableName + "\" added without any value. Fqn: " + fqn);
        }
        yamlOutputPropertiesMap.put(valueNode.getNode().getCurrJsonNode().textValue(),
            YamlOutputProperties.newBuilder().setLocalName(localName).setFqn(fqn).build());
      }
    });
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, T config) {
    return createVariablesForChildrenNodes(ctx, ctx.getCurrentField());
  }

  @Override
  public VariableCreationResponse createVariablesForParentNodeV2(VariableCreationContext ctx, T config) {
    Map<String, YamlExtraProperties> yamlExtraPropertiesMap = new HashMap<>();
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();

    final YamlNode fieldNode = ctx.getCurrentField().getNode();
    String fqnPrefix = YamlUtils.getFullyQualifiedName(fieldNode);
    String finalFqnPrefix = fqnPrefix;
    String localNamePrefix = fqnPrefix;
    if (fqnPrefix.contains(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)) {
      localNamePrefix =
          YamlUtils.getQualifiedNameTillGivenField(fieldNode, YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE);
    } else if (fqnPrefix.contains(YAMLFieldNameConstants.ENVIRONMENT)
        && fqnPrefix.contains(YAMLFieldNameConstants.PROVISIONER)) {
      // remove environment. from the path
      // provisioner path should be stage.spec.provisioner.
      finalFqnPrefix = fqnPrefix.replace(YAMLFieldNameConstants.ENVIRONMENT + ".", "");
      localNamePrefix = finalFqnPrefix;
    } else if (fqnPrefix.contains(YAMLFieldNameConstants.EXECUTION)) {
      localNamePrefix = YamlUtils.getQualifiedNameTillGivenField(fieldNode, YAMLFieldNameConstants.EXECUTION);
    }

    VariableCreatorHelper.collectVariableExpressions(
        config, yamlPropertiesMap, yamlExtraPropertiesMap, finalFqnPrefix, localNamePrefix);

    VariableCreatorHelper.addYamlExtraPropertyToMap(
        config.getUuid(), yamlExtraPropertiesMap, getStepExtraProperties(fqnPrefix, localNamePrefix, config));

    return VariableCreationResponse.builder()
        .yamlExtraProperties(yamlExtraPropertiesMap)
        .yamlProperties(yamlPropertiesMap)
        .yamlUpdates(YamlUpdates.newBuilder()
                         .putFqnToYaml(ctx.getCurrentField().getYamlPath(), JsonPipelineUtils.getJsonString(config))
                         .build())
        .build();
  }

  public YamlExtraProperties getStepExtraProperties(String fqnPrefix, String localNamePrefix, T config) {
    YamlProperties startTsProperty =
        YamlProperties.newBuilder().setFqn(fqnPrefix + ".startTs").setLocalName(localNamePrefix + ".startTs").build();
    YamlProperties endTsProperty =
        YamlProperties.newBuilder().setFqn(fqnPrefix + ".endTs").setLocalName(localNamePrefix + ".endTs").build();
    return YamlExtraProperties.newBuilder().addProperties(startTsProperty).addProperties(endTsProperty).build();
  }
}
