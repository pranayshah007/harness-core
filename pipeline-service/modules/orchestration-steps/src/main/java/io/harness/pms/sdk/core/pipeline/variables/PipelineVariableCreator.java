/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.variables;

import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.pms.contracts.plan.YamlExtraProperties;
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
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.google.common.annotations.VisibleForTesting;
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

public class PipelineVariableCreator extends ChildrenVariableCreator<PipelineInfoConfig> {
  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();
    YamlField stagesYamlNode = config.getNode().getField(YAMLFieldNameConstants.STAGES);
    if (stagesYamlNode != null) {
      getStageYamlFields(stagesYamlNode, responseMap);
    }
    return responseMap;
  }

  @Override
  public VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config) {
    YamlNode node = config.getNode();
    String pipelineUUID = node.getUuid();
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();
    yamlPropertiesMap.put(pipelineUUID,
        YamlProperties.newBuilder()
            .setLocalName(YAMLFieldNameConstants.PIPELINE)
            .setFqn(YamlUtils.getFullyQualifiedName(node))
            .build());
    addVariablesForPipeline(yamlPropertiesMap, node);
    return VariableCreationResponse.builder().yamlProperties(yamlPropertiesMap).build();
  }

  @VisibleForTesting
  void addVariablesForPipeline(Map<String, YamlProperties> yamlPropertiesMap, YamlNode yamlNode) {
    YamlField nameField = yamlNode.getField(YAMLFieldNameConstants.NAME);
    if (nameField != null) {
      String nameFQN = YamlUtils.getFullyQualifiedName(nameField.getNode());
      yamlPropertiesMap.put(nameField.getNode().getCurrJsonNode().textValue(),
          YamlProperties.newBuilder()
              .setLocalName(nameFQN)
              .setFqn(nameFQN)
              .setVariableName(YAMLFieldNameConstants.NAME)
              .setVisible(true)
              .build());
    }
    YamlField descriptionField = yamlNode.getField(YAMLFieldNameConstants.DESCRIPTION);
    if (descriptionField != null) {
      String descriptionFQN = YamlUtils.getFullyQualifiedName(descriptionField.getNode());
      yamlPropertiesMap.put(descriptionField.getNode().getCurrJsonNode().textValue(),
          YamlProperties.newBuilder()
              .setLocalName(descriptionFQN)
              .setFqn(descriptionFQN)
              .setVariableName(YAMLFieldNameConstants.DESCRIPTION)
              .setVisible(true)
              .build());
    }
    YamlField variablesField = yamlNode.getField(YAMLFieldNameConstants.VARIABLES);
    if (variablesField != null) {
      VariableCreatorHelper.addVariablesForVariables(
          variablesField, yamlPropertiesMap, YAMLFieldNameConstants.PIPELINE);
    }
    YamlField propertiesField = yamlNode.getField(YAMLFieldNameConstants.PROPERTIES);
    if (propertiesField != null) {
      VariableCreatorHelper.addPropertiesForSchemaAutocomplete(propertiesField, yamlPropertiesMap);
    }

    YamlField tagsField = yamlNode.getField(YAMLFieldNameConstants.TAGS);
    if (tagsField != null) {
      List<YamlField> fields = tagsField.getNode().fields();
      fields.forEach(field -> {
        if (!field.getName().equals(YAMLFieldNameConstants.UUID)) {
          VariableCreatorHelper.addFieldToPropertiesMap(field, yamlPropertiesMap, YAMLFieldNameConstants.PIPELINE);
        } else {
          yamlPropertiesMap.put(field.getNode().getCurrJsonNode().textValue(),
              YamlProperties.newBuilder()
                  .setLocalName(YAMLFieldNameConstants.PIPELINE + ".tags")
                  .setFqn(YAMLFieldNameConstants.PIPELINE + ".tags")
                  .setVariableName("tags")
                  .setVisible(true)
                  .build());
        }
      });
    }
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.PIPELINE, Collections.singleton("__any__"));
  }

  @Override
  public Class<PipelineInfoConfig> getFieldClass() {
    return PipelineInfoConfig.class;
  }

  @VisibleForTesting
  void getStageYamlFields(YamlField stagesYamlNode, LinkedHashMap<String, VariableCreationResponse> responseMap) {
    List<YamlNode> yamlNodes = Optional.of(stagesYamlNode.getNode().asArray()).orElse(Collections.emptyList());
    List<YamlField> stageFields = new LinkedList<>();

    yamlNodes.forEach(yamlNode -> {
      YamlField stageField = yamlNode.getField(YAMLFieldNameConstants.STAGE);
      YamlField parallelStageField = yamlNode.getField(YAMLFieldNameConstants.PARALLEL);
      if (stageField != null) {
        stageFields.add(stageField);
      } else if (parallelStageField != null) {
        List<YamlField> childYamlFields = Optional.of(parallelStageField.getNode().asArray())
                                              .orElse(Collections.emptyList())
                                              .stream()
                                              .map(el -> el.getField(YAMLFieldNameConstants.STAGE))
                                              .filter(Objects::nonNull)
                                              .collect(Collectors.toList());
        if (EmptyPredicate.isNotEmpty(childYamlFields)) {
          stageFields.addAll(childYamlFields);
        }
      }
    });

    for (YamlField stageYamlField : stageFields) {
      Map<String, YamlField> stageYamlFieldMap = new HashMap<>();
      stageYamlFieldMap.put(stageYamlField.getNode().getUuid(), stageYamlField);
      responseMap.put(stageYamlField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(stageYamlFieldMap))
              .build());
    }
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, PipelineInfoConfig config) {
    return createVariablesForChildrenNodes(ctx, ctx.getCurrentField());
  }

  @Override
  public VariableCreationResponse createVariablesForParentNodeV2(
      VariableCreationContext ctx, PipelineInfoConfig config) {
    Map<String, YamlExtraProperties> yamlExtraPropertiesMap = new HashMap<>();
    Map<String, YamlProperties> yamlPropertiesMap = new HashMap<>();

    VariableCreatorHelper.collectVariableExpressions(config, yamlPropertiesMap, yamlExtraPropertiesMap,
        YAMLFieldNameConstants.PIPELINE, YAMLFieldNameConstants.PIPELINE);

    // pipeline section extra properties
    YamlExtraProperties pipelineExtraProperties = getPipelineExtraProperties(config);
    VariableCreatorHelper.addYamlExtraPropertyToMap(config.getUuid(), yamlExtraPropertiesMap, pipelineExtraProperties);

    return VariableCreationResponse.builder()
        .yamlExtraProperties(yamlExtraPropertiesMap)
        .yamlProperties(yamlPropertiesMap)
        .yamlUpdates(YamlUpdates.newBuilder()
                         .putFqnToYaml(ctx.getCurrentField().getYamlPath(), JsonPipelineUtils.getJsonString(config))
                         .build())
        .build();
  }

  @VisibleForTesting
  YamlExtraProperties getPipelineExtraProperties(PipelineInfoConfig config) {
    // Adding sequenceId expression (not part of the yaml, thus visible - false)
    YamlProperties sequenceIdProperty =
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".sequenceId").build();
    // executionId Property
    YamlProperties executionIdProperty =
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".executionId").build();

    YamlProperties triggerTypeProperty =
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".triggerType").build();
    YamlProperties triggeredByNameProperty =
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".triggeredBy.name").build();
    YamlProperties triggeredByEmailProperty =
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".triggeredBy.email").build();
    YamlProperties triggeredByTriggerNameProperty =
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".triggeredBy.triggerName").build();

    YamlProperties startTsProperty =
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".startTs").build();
    YamlProperties endTsProperty =
        YamlProperties.newBuilder().setFqn(YAMLFieldNameConstants.PIPELINE + ".endTs").build();
    YamlExtraProperties.Builder yamlExtraPropertyBuilder = YamlExtraProperties.newBuilder();

    // ci build properties
    if (config.getProperties() != null) {
      List<String> expressionsAvailable = Build.getExpressionsAvailable();
      for (String fqn : expressionsAvailable) {
        String propertiesExpressionPath = "pipeline.properties.ci.codebase.build";
        yamlExtraPropertyBuilder.addProperties(
            YamlProperties.newBuilder().setFqn(propertiesExpressionPath + "." + fqn).build());
      }
    }

    return yamlExtraPropertyBuilder.addProperties(sequenceIdProperty)
        .addProperties(executionIdProperty)
        .addProperties(startTsProperty)
        .addProperties(endTsProperty)
        .addProperties(triggerTypeProperty)
        .addProperties(triggeredByNameProperty)
        .addProperties(triggeredByEmailProperty)
        .addProperties(triggeredByTriggerNameProperty)
        .build();
  }
}
