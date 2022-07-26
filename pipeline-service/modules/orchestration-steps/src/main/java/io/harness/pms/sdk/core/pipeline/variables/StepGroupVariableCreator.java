/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.variables;

import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.plan.creation.PlanCreatorUtils;
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

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class StepGroupVariableCreator extends ChildrenVariableCreator<StepGroupElementConfig> {
  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> stepYamlFields = getStepYamlFields(config);
    for (YamlField stepYamlField : stepYamlFields) {
      Map<String, YamlField> stepYamlFieldMap = new HashMap<>();
      stepYamlFieldMap.put(stepYamlField.getNode().getUuid(), stepYamlField);
      responseMap.put(stepYamlField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(stepYamlFieldMap))
              .build());
    }

    YamlField rollbackStepsField = config.getNode().getField(YAMLFieldNameConstants.ROLLBACK_STEPS);
    if (rollbackStepsField != null) {
      Map<String, YamlField> rollbackDependencyMap = new HashMap<>();
      rollbackDependencyMap.put(rollbackStepsField.getNode().getUuid(), rollbackStepsField);
      responseMap.put(rollbackStepsField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(rollbackDependencyMap))
              .build());
    }
    return responseMap;
  }

  @Override
  public VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config) {
    YamlNode node = config.getNode();
    String stepGroupUUID = node.getUuid();
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();
    yamlPropertiesMap.put(stepGroupUUID,
        YamlProperties.newBuilder()
            .setLocalName(YAMLFieldNameConstants.STEP_GROUP)
            .setFqn(YamlUtils.getFullyQualifiedName(node))
            .build());
    addVariablesForStepGroup(yamlPropertiesMap, node);
    return VariableCreationResponse.builder().yamlProperties(yamlPropertiesMap).build();
  }

  private void addVariablesForStepGroup(Map<String, YamlProperties> yamlPropertiesMap, YamlNode yamlNode) {
    YamlField nameField = yamlNode.getField(YAMLFieldNameConstants.NAME);
    if (nameField != null) {
      String nameFQN = YamlUtils.getFullyQualifiedName(nameField.getNode());
      String localName = YAMLFieldNameConstants.STEP_GROUP + "."
          + YamlUtils.getQualifiedNameTillGivenField(nameField.getNode(), YAMLFieldNameConstants.STEP_GROUP);
      yamlPropertiesMap.put(nameField.getNode().getCurrJsonNode().textValue(),
          YamlProperties.newBuilder().setLocalName(localName).setFqn(nameFQN).build());
    }
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STEP_GROUP, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  private List<YamlField> getStepYamlFields(YamlField config) {
    List<YamlField> childYamlFields = new LinkedList<>();
    List<YamlNode> yamlNodes =
        Optional
            .of(Preconditions.checkNotNull(config.getNode().getField(YAMLFieldNameConstants.STEPS)).getNode().asArray())
            .orElse(Collections.emptyList());
    yamlNodes.forEach(yamlNode -> {
      YamlField stepField = yamlNode.getField(YAMLFieldNameConstants.STEP);
      YamlField parallelStepField = yamlNode.getField(YAMLFieldNameConstants.PARALLEL);
      if (stepField != null) {
        childYamlFields.add(stepField);
      } else if (parallelStepField != null) {
        List<YamlField> childStepYamlFields = Optional.of(parallelStepField.getNode().asArray())
                                                  .orElse(Collections.emptyList())
                                                  .stream()
                                                  .map(el -> el.getField(YAMLFieldNameConstants.STEP))
                                                  .filter(Objects::nonNull)
                                                  .collect(Collectors.toList());
        if (EmptyPredicate.isNotEmpty(childStepYamlFields)) {
          childYamlFields.addAll(childStepYamlFields);
        }
      }
    });
    return childYamlFields;
  }

  @Override
  public Class<StepGroupElementConfig> getFieldClass() {
    return StepGroupElementConfig.class;
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, StepGroupElementConfig config) {
    return createVariablesForChildrenNodes(ctx, ctx.getCurrentField());
  }

  @Override
  public VariableCreationResponse createVariablesForParentNodeV2(
      VariableCreationContext ctx, StepGroupElementConfig config) {
    Map<String, YamlExtraProperties> yamlExtraPropertiesMap = new HashMap<>();
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();

    String fqnPrefix = YamlUtils.getFullyQualifiedName(ctx.getCurrentField().getNode());
    String localNamePrefix;
    if (fqnPrefix.contains(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE)) {
      localNamePrefix = YamlUtils.getQualifiedNameTillGivenField(
          ctx.getCurrentField().getNode(), YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE);
    } else {
      localNamePrefix =
          YamlUtils.getQualifiedNameTillGivenField(ctx.getCurrentField().getNode(), YAMLFieldNameConstants.EXECUTION);
    }

    VariableCreatorHelper.collectVariableExpressions(
        config, yamlPropertiesMap, yamlExtraPropertiesMap, fqnPrefix, localNamePrefix);

    VariableCreatorHelper.addYamlExtraPropertyToMap(
        config.getUuid(), yamlExtraPropertiesMap, getStepGroupExtraProperties(fqnPrefix, localNamePrefix));

    return VariableCreationResponse.builder()
        .yamlExtraProperties(yamlExtraPropertiesMap)
        .yamlProperties(yamlPropertiesMap)
        .yamlUpdates(YamlUpdates.newBuilder()
                         .putFqnToYaml(ctx.getCurrentField().getYamlPath(), JsonPipelineUtils.getJsonString(config))
                         .build())
        .build();
  }

  private YamlExtraProperties getStepGroupExtraProperties(String fqnPrefix, String localNamePrefix) {
    YamlProperties startTsProperty =
        YamlProperties.newBuilder().setFqn(fqnPrefix + ".startTs").setLocalName(localNamePrefix + ".startTs").build();
    YamlProperties endTsProperty =
        YamlProperties.newBuilder().setFqn(fqnPrefix + ".endTs").setLocalName(localNamePrefix + ".endTs").build();
    return YamlExtraProperties.newBuilder().addProperties(startTsProperty).addProperties(endTsProperty).build();
  }
}
