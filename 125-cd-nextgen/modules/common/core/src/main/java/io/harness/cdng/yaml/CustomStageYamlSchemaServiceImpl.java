/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.yaml;

import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.PROPERTIES_NODE;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.stage.CustomStageConfig;
import io.harness.cdng.creator.plan.stage.CustomStageNode;
import io.harness.encryption.Scope;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.steps.StepCategory;
// import io.harness.pms.pipeline.service.yamlschema.PmsYamlSchemaHelper;
import io.harness.utils.FeatureRestrictionsGetter;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.yaml.schema.SchemaGeneratorUtils;
import io.harness.yaml.schema.YamlSchemaGenerator;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.beans.FieldEnumData;
import io.harness.yaml.schema.beans.PartialSchemaDTO;
import io.harness.yaml.schema.beans.SchemaConstants;
import io.harness.yaml.schema.beans.SubtypeClassMap;
import io.harness.yaml.schema.beans.SwaggerDefinitionsMetaInfo;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;
import io.harness.yaml.utils.YamlSchemaUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.PIPELINE)
public class CustomStageYamlSchemaServiceImpl implements CustomStageYamlSchemaService {
  private static final String CUSTOM_NAMESPACE = "custom";
  private static final String CUSTOM_STAGE_NODE = YamlSchemaUtils.getSwaggerName(CustomStageNode.class);
  public static final String STEP_ELEMENT_CONFIG =
      io.harness.yaml.utils.YamlSchemaUtils.getSwaggerName(StepElementConfig.class);

  private Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes;
  private static final Class<StepElementConfig> STEP_ELEMENT_CONFIG_CLASS = StepElementConfig.class;

  @Inject private YamlSchemaProvider yamlSchemaProvider;
  //@Inject private PmsYamlSchemaHelper pmsYamlSchemaHelper;
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject private YamlSchemaGenerator yamlSchemaGenerator;
  @Inject private List<YamlSchemaRootClass> yamlSchemaRootClasses;
  @Inject private FeatureRestrictionsGetter featureRestrictionsGetter;

  @Override
  public PartialSchemaDTO getCustomStageYamlSchema(String accountIdentifier, String projectIdentifier,
      String orgIdentifier, Scope scope, List<YamlSchemaWithDetails> yamlSchemaWithDetailsList) {
    JsonNode customStageSchema =
        yamlSchemaProvider.getYamlSchema(EntityType.CUSTOM_STAGE, orgIdentifier, projectIdentifier, scope);

    JsonNode definitions = customStageSchema.get(DEFINITIONS_NODE);

    JsonNode jsonNode = definitions.get(StepElementConfig.class.getSimpleName());
    // pmsYamlSchemaHelper.modifyStepElementSchema((ObjectNode) jsonNode);
    modifyStepElementSchema((ObjectNode) jsonNode);

    jsonNode = definitions.get(ParallelStepElementConfig.class.getSimpleName());
    if (jsonNode.isObject()) {
      // PmsYamlSchemaHelper.flatten((ObjectNode) jsonNode);
      flatten((ObjectNode) jsonNode);
    }

    yamlSchemaProvider.mergeAllV2StepsDefinitions(projectIdentifier, orgIdentifier, scope, (ObjectNode) definitions,
        YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, StepCategory.STEP.name()));

    yamlSchemaGenerator.modifyRefsNamespace(customStageSchema, CUSTOM_NAMESPACE);

    Set<String> enabledFeatureFlags = getEnabledFeatureFlags(accountIdentifier, yamlSchemaWithDetailsList);
    Map<String, Boolean> featureRestrictionsMap =
        featureRestrictionsGetter.getFeatureRestrictionsAvailability(yamlSchemaWithDetailsList, accountIdentifier);

    // false is added to support cross service steps schema
    if (yamlSchemaWithDetailsList != null) {
      YamlSchemaUtils.addOneOfInExecutionWrapperConfig(customStageSchema.get(DEFINITIONS_NODE),
          yamlSchemaWithDetailsList, ModuleType.PMS, enabledFeatureFlags, featureRestrictionsMap, false);
    }

    ObjectMapper mapper = SchemaGeneratorUtils.getObjectMapperForSchemaGeneration();
    JsonNode node = mapper.createObjectNode().set(CUSTOM_NAMESPACE, definitions);

    JsonNode partialCustomStageSchema = ((ObjectNode) customStageSchema).set(DEFINITIONS_NODE, node);

    return PartialSchemaDTO.builder()
        .namespace("cd")
        .nodeName(CUSTOM_STAGE_NODE)
        .schema(partialCustomStageSchema)
        .nodeType(getCustomStageTypeName())
        .moduleType(ModuleType.CD)
        .skipStageSchema(false)
        .build();
  }

  private String getCustomStageTypeName() {
    JsonTypeName annotation = CustomStageConfig.class.getAnnotation(JsonTypeName.class);
    return annotation.value();
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

  private Set<FieldEnumData> getFieldEnumData(Field typedField, Set<SubtypeClassMap> mapOfSubtypes) {
    String fieldName = YamlSchemaUtils.getJsonTypeInfo(typedField).property();

    return ImmutableSet.of(
        FieldEnumData.builder()
            .fieldName(fieldName)
            .enumValues(ImmutableSortedSet.copyOf(
                mapOfSubtypes.stream().map(SubtypeClassMap::getSubtypeEnum).collect(Collectors.toList())))
            .build());
  }

  public static void flatten(ObjectNode objectNode) {
    JsonNode sections = objectNode.get(PROPERTIES_NODE).get("sections");
    if (sections.isObject()) {
      objectNode.removeAll();
      objectNode.setAll((ObjectNode) sections);
      objectNode.put(SchemaConstants.SCHEMA_NODE, SchemaConstants.JSON_SCHEMA_7);
    }
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
}
