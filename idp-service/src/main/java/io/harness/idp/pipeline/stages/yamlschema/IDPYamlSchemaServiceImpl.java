/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline.stages.yamlschema;

import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.PROPERTIES_NODE;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureFlag;
import io.harness.encryption.Scope;
import io.harness.idp.pipeline.stages.node.IDPStageConfigImpl;
import io.harness.idp.pipeline.stages.node.IDPStageNode;
import io.harness.jackson.JsonNodeUtils;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.remote.client.CGRestUtils;
import io.harness.yaml.schema.SchemaGeneratorUtils;
import io.harness.yaml.schema.YamlSchemaGenerator;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.schema.beans.*;
import io.harness.yaml.utils.YamlSchemaUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.IDP)
public class IDPYamlSchemaServiceImpl implements IDPYamlSchemaService {
  private static final String IDP_STAGE_NODE = YamlSchemaUtils.getSwaggerName(IDPStageNode.class);
  private static final String STEP_ELEMENT_CONFIG = YamlSchemaUtils.getSwaggerName(StepElementConfig.class);
  private static final Class<StepElementConfig> STEP_ELEMENT_CONFIG_CLASS = StepElementConfig.class;
  private static final String IDP_NAMESPACE = "idp";

  private final YamlSchemaProvider yamlSchemaProvider;
  private final YamlSchemaGenerator yamlSchemaGenerator;
  private final Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes;
  private final List<YamlSchemaRootClass> yamlSchemaRootClasses;
  private final AccountClient accountClient;

  @Inject
  public IDPYamlSchemaServiceImpl(YamlSchemaProvider yamlSchemaProvider, YamlSchemaGenerator yamlSchemaGenerator,
      @Named("yaml-schema-subtypes") Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes,
      List<YamlSchemaRootClass> yamlSchemaRootClasses, AccountClient accountClient) {
    this.yamlSchemaProvider = yamlSchemaProvider;
    this.yamlSchemaGenerator = yamlSchemaGenerator;
    this.yamlSchemaSubtypes = yamlSchemaSubtypes;
    this.yamlSchemaRootClasses = yamlSchemaRootClasses;
    this.accountClient = accountClient;
  }

  @Override
  public PartialSchemaDTO getMergedStageYamlSchema(String accountIdentifier, String projectIdentifier,
      String orgIdentifier, Scope scope, List<YamlSchemaWithDetails> stepSchemaWithDetails) {
    return getStageYamlSchemaUtil(accountIdentifier, projectIdentifier, orgIdentifier, scope, stepSchemaWithDetails);
  }

  @Override
  public List<YamlSchemaWithDetails> getStageYamlSchemaWithDetails(
      String accountIdentifier, String projectIdentifier, String orgIdentifier, Scope scope) {
    List<YamlSchemaWithDetails> yamlSchemaWithDetailsList = yamlSchemaProvider.getCrossFunctionalStepsSchemaDetails(
        projectIdentifier, orgIdentifier, scope,
        YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, StepCategory.STEP.name()), ModuleType.IDP);
    yamlSchemaWithDetailsList.addAll(
        yamlSchemaProvider.getCrossFunctionalStepsSchemaDetails(projectIdentifier, orgIdentifier, scope,
            YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, StepCategory.STAGE.name()),
            ModuleType.IDP));
    return yamlSchemaWithDetailsList;
  }

  @Override
  public JsonNode getIndividualYamlSchema(EntityType entityType, String orgId, String projectId, Scope scope) {
    return yamlSchemaProvider.getYamlSchema(entityType, null, null, null);
  }

  @Override
  public PartialSchemaDTO getStageYamlSchema(
      String accountIdentifier, String projectIdentifier, String orgIdentifier, Scope scope) {
    return getStageYamlSchemaUtil(accountIdentifier, projectIdentifier, orgIdentifier, scope, null);
  }

  public PartialSchemaDTO getStageYamlSchemaUtil(String accountIdentifier, String projectIdentifier,
      String orgIdentifier, Scope scope, List<YamlSchemaWithDetails> stepSchemaWithDetails) {
    JsonNode integrationStageSchema =
        yamlSchemaProvider.getYamlSchema(EntityType.IDP_STAGE, orgIdentifier, projectIdentifier, scope);
    JsonNode integrationStageSteps =
        yamlSchemaProvider.getYamlSchema(EntityType.IDP_STAGE, orgIdentifier, projectIdentifier, scope);

    JsonNode definitions = integrationStageSchema.get(DEFINITIONS_NODE);
    JsonNode integrationStepDefinitions = integrationStageSteps.get(DEFINITIONS_NODE);

    JsonNodeUtils.merge(definitions, integrationStepDefinitions);
    yamlSchemaProvider.mergeAllV2StepsDefinitions(projectIdentifier, orgIdentifier, scope, (ObjectNode) definitions,
        YamlSchemaUtils.getNodeEntityTypesByYamlGroup(yamlSchemaRootClasses, StepCategory.STEP.name()));

    JsonNode jsonNode = definitions.get(StepElementConfig.class.getSimpleName());
    modifyStepElementSchema((ObjectNode) jsonNode);

    jsonNode = definitions.get(ParallelStepElementConfig.class.getSimpleName());
    if (jsonNode.isObject()) {
      flattenParallelStepElementConfig((ObjectNode) jsonNode);
    }
    removeUnwantedNodes(definitions);

    yamlSchemaGenerator.modifyRefsNamespace(integrationStageSchema, IDP_NAMESPACE);
    Set<String> enabledFeatureFlags =
        CGRestUtils.getResponse(accountClient.listAllFeatureFlagsForAccount(accountIdentifier))
            .stream()
            .filter(FeatureFlag::isEnabled)
            .map(FeatureFlag::getName)
            .collect(Collectors.toSet());

    // Should be after this modifyRefsNamespace call.
    YamlSchemaUtils.addOneOfInExecutionWrapperConfig(integrationStageSchema.get(DEFINITIONS_NODE),
        YamlSchemaUtils.getNodeClassesByYamlGroup(
            yamlSchemaRootClasses, StepCategory.STEP.name(), enabledFeatureFlags, Collections.emptyMap()),
        IDP_NAMESPACE);
    if (stepSchemaWithDetails != null) {
      YamlSchemaUtils.addOneOfInExecutionWrapperConfig(integrationStageSchema.get(DEFINITIONS_NODE),
          stepSchemaWithDetails, ModuleType.CD, enabledFeatureFlags, Collections.emptyMap());
    }

    ObjectMapper mapper = SchemaGeneratorUtils.getObjectMapperForSchemaGeneration();
    JsonNode node = mapper.createObjectNode().set(IDP_NAMESPACE, definitions);

    JsonNode partialCiSchema = ((ObjectNode) integrationStageSchema).set(DEFINITIONS_NODE, node);

    return PartialSchemaDTO.builder()
        .namespace(IDP_NAMESPACE)
        .nodeName(IDP_STAGE_NODE)
        .schema(partialCiSchema)
        .nodeType(getIntegrationStageTypeName())
        .moduleType(ModuleType.IDP)
        .skipStageSchema(false)
        .build();
  }

  private void modifyStepElementSchema(ObjectNode jsonNode) {
    ObjectMapper mapper = SchemaGeneratorUtils.getObjectMapperForSchemaGeneration();
    Map<String, SwaggerDefinitionsMetaInfo> swaggerDefinitionsMetaInfoMap = new HashMap<>();
    Field typedField = YamlSchemaUtils.getTypedField(STEP_ELEMENT_CONFIG_CLASS);
    Set<Class<?>> cachedSubtypes = yamlSchemaSubtypes.get(typedField.getType());
    Set<SubtypeClassMap> mapOfSubtypes = YamlSchemaUtils.toSetOfSubtypeClassMap(cachedSubtypes);
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

  private void flattenParallelStepElementConfig(ObjectNode objectNode) {
    JsonNode sections = objectNode.get(PROPERTIES_NODE).get("sections");
    if (sections.isObject()) {
      objectNode.removeAll();
      objectNode.setAll((ObjectNode) sections);
      objectNode.put(SchemaConstants.SCHEMA_NODE, SchemaConstants.JSON_SCHEMA_7);
    }
  }

  private void removeUnwantedNodes(JsonNode definitions) {
    if (definitions.isObject()) {
      Iterator<JsonNode> elements = definitions.elements();
      while (elements.hasNext()) {
        JsonNode jsonNode = elements.next();
        yamlSchemaGenerator.removeUnwantedNodes(jsonNode, YAMLFieldNameConstants.ROLLBACK_STEPS);
        yamlSchemaGenerator.removeUnwantedNodes(jsonNode, YAMLFieldNameConstants.STEP_GROUP);
      }
    }
  }

  private String getIntegrationStageTypeName() {
    JsonTypeName annotation = IDPStageConfigImpl.class.getAnnotation(JsonTypeName.class);
    return annotation.value();
  }
}
