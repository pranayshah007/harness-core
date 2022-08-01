/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlschema;

import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.PROPERTIES_NODE;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.jackson.JsonNodeUtils;
import io.harness.plancreator.stages.parallel.ParallelStageElementConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.helpers.PmsFeatureFlagHelper;
import io.harness.yaml.schema.SchemaGeneratorUtils;
import io.harness.yaml.schema.YamlSchemaGenerator;
import io.harness.yaml.schema.YamlSchemaTransientHelper;
import io.harness.yaml.schema.beans.FieldEnumData;
import io.harness.yaml.schema.beans.FieldSubtypeData;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.SchemaConstants;
import io.harness.yaml.schema.beans.SubtypeClassMap;
import io.harness.yaml.schema.beans.SwaggerDefinitionsMetaInfo;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.harness.yaml.utils.YamlSchemaUtils;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class PmsYamlSchemaHelper {
  public static final String STEP_ELEMENT_CONFIG =
      io.harness.yaml.utils.YamlSchemaUtils.getSwaggerName(StepElementConfig.class);
  private static final Class<StepElementConfig> STEP_ELEMENT_CONFIG_CLASS = StepElementConfig.class;

  public static final String STAGE_ELEMENT_CONFIG = YamlSchemaUtils.getSwaggerName(StageElementConfig.class);
  public static final Class<StageElementConfig> STAGE_ELEMENT_CONFIG_CLASS = StageElementConfig.class;

  private final Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes;
  private final List<YamlSchemaRootClass> yamlSchemaRootClasses;
  private final YamlSchemaGenerator yamlSchemaGenerator;
  private final PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Inject
  public PmsYamlSchemaHelper(@Named("yaml-schema-subtypes") Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes,
      List<YamlSchemaRootClass> yamlSchemaRootClasses, YamlSchemaGenerator yamlSchemaGenerator,
      PmsFeatureFlagHelper pmsFeatureFlagHelper) {
    this.yamlSchemaSubtypes = yamlSchemaSubtypes;
    this.yamlSchemaRootClasses = yamlSchemaRootClasses;
    this.yamlSchemaGenerator = yamlSchemaGenerator;
    this.pmsFeatureFlagHelper = pmsFeatureFlagHelper;
  }

  public void modifyStepElementSchema(ObjectNode jsonNode) {
    if (jsonNode == null) {
      return;
    }
    ObjectMapper mapper = SchemaGeneratorUtils.getObjectMapperForSchemaGeneration();
    Map<String, SwaggerDefinitionsMetaInfo> swaggerDefinitionsMetaInfoMap = new HashMap<>();
    Field typedField = io.harness.yaml.utils.YamlSchemaUtils.getTypedField(STEP_ELEMENT_CONFIG_CLASS);
    Set<Class<?>> cachedSubtypes = yamlSchemaSubtypes.get(typedField.getType());
    Set<SubtypeClassMap> mapOfSubtypes = io.harness.yaml.utils.YamlSchemaUtils.toSetOfSubtypeClassMap(cachedSubtypes);
    Set<FieldEnumData> fieldEnumData = getFieldEnumData(typedField, mapOfSubtypes);
    swaggerDefinitionsMetaInfoMap.put(
        STEP_ELEMENT_CONFIG, SwaggerDefinitionsMetaInfo.builder().fieldEnumData(fieldEnumData).build());
    yamlSchemaGenerator.convertSwaggerToJsonSchema(
        swaggerDefinitionsMetaInfoMap, mapper, STEP_ELEMENT_CONFIG, jsonNode);
  }

  public static void flatten(ObjectNode objectNode) {
    JsonNode sections = objectNode.get(PROPERTIES_NODE).get("sections");
    if (sections.isObject()) {
      objectNode.removeAll();
      objectNode.setAll((ObjectNode) sections);
      objectNode.put(SchemaConstants.SCHEMA_NODE, SchemaConstants.JSON_SCHEMA_7);
    }
  }

  public static void flattenParallelElementConfig(ObjectNode definitions) {
    // flatten stage
    JsonNode jsonNode = definitions.get(ParallelStageElementConfig.class.getSimpleName());
    if (jsonNode.isObject()) {
      PmsYamlSchemaHelper.flatten((ObjectNode) jsonNode);
    }

    // flatten step
    jsonNode = definitions.get(ParallelStepElementConfig.class.getSimpleName());
    if (jsonNode.isObject()) {
      PmsYamlSchemaHelper.flatten((ObjectNode) jsonNode);
    }
  }

  public void processPartialStageSchema(ObjectNode pipelineDefinitions, ObjectNode pipelineSteps,
      ObjectNode stageElementConfig, PartialSchemaDTO partialSchemaDTO) {
    YamlSchemaTransientHelper.removeV2StepEnumsFromStepElementConfig(partialSchemaDTO.getSchema()
                                                                         .get(DEFINITIONS_NODE)
                                                                         .get(partialSchemaDTO.getNamespace())
                                                                         .get(STEP_ELEMENT_CONFIG));
    SubtypeClassMap subtypeClassMap =
        SubtypeClassMap.builder()
            .subTypeDefinitionKey(partialSchemaDTO.getNamespace() + "/" + partialSchemaDTO.getNodeName())
            .subtypeEnum(partialSchemaDTO.getNodeType())
            .build();

    ObjectNode stageDefinitionsNode = moveRootNodeToDefinitions(
        partialSchemaDTO.getNodeName(), (ObjectNode) partialSchemaDTO.getSchema(), partialSchemaDTO.getNamespace());

    mergePipelineStepsIntoStage(stageDefinitionsNode, pipelineSteps, partialSchemaDTO);
    if (!partialSchemaDTO.isSkipStageSchema()) {
      mergeStageElementConfig(stageElementConfig, subtypeClassMap);
    }

    pipelineDefinitions.set(partialSchemaDTO.getNamespace(), stageDefinitionsNode.get(partialSchemaDTO.getNamespace()));
  }

  private void mergePipelineStepsIntoStage(
      ObjectNode stageDefinitionsNode, ObjectNode pipelineSteps, PartialSchemaDTO partialSchemaDTO) {
    ObjectNode pipelineStepsCopy = obtainPipelineStepsCopyWithCorrectNamespace(pipelineSteps, partialSchemaDTO);
    JsonNodeUtils.merge(stageDefinitionsNode.get(partialSchemaDTO.getNamespace()), pipelineStepsCopy);
  }

  private void mergeStageElementConfig(ObjectNode src, SubtypeClassMap subtypeClassMap) {
    ObjectNode stageElementCopy = src.deepCopy();
    modifyStageElementConfig(stageElementCopy, subtypeClassMap);

    JsonNodeUtils.merge(src, stageElementCopy);
  }

  private ObjectNode obtainPipelineStepsCopyWithCorrectNamespace(
      ObjectNode pipelineSteps, PartialSchemaDTO partialSchemaDTO) {
    ObjectNode pipelineStepsCopy = pipelineSteps.deepCopy();
    yamlSchemaGenerator.modifyRefsNamespace(pipelineStepsCopy, partialSchemaDTO.getNamespace());
    return pipelineStepsCopy;
  }

  private void modifyStageElementConfig(ObjectNode stageElementConfig, SubtypeClassMap subtypeClassMap) {
    ObjectMapper mapper = SchemaGeneratorUtils.getObjectMapperForSchemaGeneration();
    Map<String, SwaggerDefinitionsMetaInfo> swaggerDefinitionsMetaInfoMap = new HashMap<>();
    List<Field> fields = YamlSchemaUtils.getTypedFields(STAGE_ELEMENT_CONFIG_CLASS);
    Set<FieldSubtypeData> classFieldSubtypeData =
        fields.stream()
            .map(field -> YamlSchemaUtils.getFieldSubtypeData(field, ImmutableSet.of(subtypeClassMap)))
            .filter(subtype -> subtype.getDiscriminatorType().equals(JsonTypeInfo.As.EXTERNAL_PROPERTY))
            .collect(Collectors.toSet());
    Set<FieldEnumData> fieldEnumData = getFieldEnumData(subtypeClassMap);
    swaggerDefinitionsMetaInfoMap.put(STAGE_ELEMENT_CONFIG,
        SwaggerDefinitionsMetaInfo.builder()
            .fieldEnumData(fieldEnumData)
            .subtypeClassMap(classFieldSubtypeData)
            .build());
    yamlSchemaGenerator.convertSwaggerToJsonSchema(
        swaggerDefinitionsMetaInfoMap, mapper, STAGE_ELEMENT_CONFIG, stageElementConfig);
  }

  private ObjectNode moveRootNodeToDefinitions(String nodeName, ObjectNode nodeSchema, String namespace) {
    ObjectNode definitions = (ObjectNode) nodeSchema.remove(DEFINITIONS_NODE);
    ObjectNode namespaceNode = (ObjectNode) definitions.get(namespace);
    namespaceNode.set(nodeName, nodeSchema);
    return definitions;
  }

  public void removeUnwantedNodes(JsonNode definitions, Set<String> unwantedNodeNames) {
    if (definitions.isObject()) {
      Iterator<JsonNode> elements = definitions.elements();
      while (elements.hasNext()) {
        JsonNode jsonNode = elements.next();
        unwantedNodeNames.forEach(unwanted -> yamlSchemaGenerator.removeUnwantedNodes(jsonNode, unwanted));
      }
    }
  }

  private Set<FieldEnumData> getFieldEnumData(Field typedField, Set<SubtypeClassMap> mapOfSubtypes) {
    String fieldName = YamlSchemaUtils.getJsonTypeInfo(typedField).property();

    return ImmutableSet.of(
        FieldEnumData.builder()
            .fieldName(fieldName)
            .enumValues(ImmutableSortedSet.copyOf(
                mapOfSubtypes.stream().map(SubtypeClassMap::getSubtypeEnum).collect(Collectors.toList())))
            .build());
  }

  private Set<FieldEnumData> getFieldEnumData(SubtypeClassMap subtypeClassMap) {
    return ImmutableSet.of(FieldEnumData.builder()
                               .fieldName("type")
                               .enumValues(ImmutableSet.of(subtypeClassMap.getSubtypeEnum()))
                               .build());
  }

  public List<EntityType> getNodeEntityTypesByYamlGroup(String yamlGroup) {
    return YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, yamlGroup);
  }

  public Set<String> getEnabledFeatureFlags(
      String accountIdentifier, List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    Set<String> enabledFeatureFlags = new HashSet<>();
    for (YamlSchemaWithDetails yamlSchemaWithDetails : yamlSchemaWithDetailsList) {
      List<String> featureFlags = yamlSchemaWithDetails.getYamlSchemaMetadata().getFeatureFlags();
      if (featureFlags != null) {
        enabledFeatureFlags.addAll(featureFlags.stream()
                                       .filter(o -> pmsFeatureFlagHelper.isEnabled(accountIdentifier, o))
                                       .collect(Collectors.toList()));
      }
    }
    return enabledFeatureFlags;
  }

  public boolean isFeatureFlagEnabled(FeatureName featureName, String accountId) {
    return pmsFeatureFlagHelper.isEnabled(accountId, featureName);
  }

  public void processStageSchema(List<YamlSchemaWithDetails> allSchemaDetails, ObjectNode pipelineDefinitions) {
    try {
      List<YamlSchemaWithDetails> stageSchemaWithDetails =
          allSchemaDetails.stream()
              .filter(o -> o.getYamlSchemaMetadata().getYamlGroup().getGroup().equals(StepCategory.STAGE.name()))
              .filter(o
                  -> EmptyPredicate.isEmpty(o.getYamlSchemaMetadata().getNamespace())
                      || pipelineDefinitions.get(o.getYamlSchemaMetadata().getNamespace()) != null)
              .collect(Collectors.toList());

      YamlSchemaUtils.addOneOfInStageElementWrapperConfig(pipelineDefinitions, stageSchemaWithDetails);
      YamlSchemaTransientHelper.removeV2StagesFromStageElementConfig(pipelineDefinitions.get(STAGE_ELEMENT_CONFIG));
    } catch (Exception e) {
      log.error("[PMS] Failed to merge Stage schema", e);
    }
  }
}
