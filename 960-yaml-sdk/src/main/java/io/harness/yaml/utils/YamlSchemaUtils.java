/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.utils;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.yaml.schema.beans.SchemaConstants.EXECUTION_WRAPPER_CONFIG_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.ONE_OF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.PROPERTIES_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.REF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.STAGE_ELEMENT_CONFIG_REF_VALUE;
import static io.harness.yaml.schema.beans.SchemaConstants.STAGE_ELEMENT_WRAPPER_CONFIG;
import static io.harness.yaml.schema.beans.SchemaConstants.STAGE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.STEP_NODE;

import io.harness.EntityType;
import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.jackson.JsonNodeUtils;
import io.harness.packages.HarnessPackages;
import io.harness.yaml.schema.YamlSchemaIgnoreSubtype;
import io.harness.yaml.schema.beans.FieldSubtypeData;
import io.harness.yaml.schema.beans.SubtypeClassMap;
import io.harness.yaml.schema.beans.YamlSchemaMetadata;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.beans.YamlSchemaWithDetails;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

@UtilityClass
@OwnedBy(DX)
public class YamlSchemaUtils {
  /**
   * @param classLoader     {@link ClassLoader} object which will be used for reflection. If null default class loader
   *                        will be used.
   * @param annotationClass Annotation for which lookup will happen.
   * @return Classes which contains the annotation.
   */
  public Set<Class<?>> getClasses(@Nullable URLClassLoader classLoader, Class annotationClass) {
    Reflections reflections;
    if (classLoader != null) {
      FilterBuilder filter = new FilterBuilder().include(FilterBuilder.prefix("io.harness")).include("software.wings");
      reflections = new Reflections(new ConfigurationBuilder()
                                        .forPackages(HarnessPackages.IO_HARNESS, HarnessPackages.SOFTWARE_WINGS)
                                        .filterInputsBy(filter)
                                        .setUrls(classLoader.getURLs())
                                        .addClassLoader(classLoader));

    } else {
      reflections = new Reflections(HarnessPackages.IO_HARNESS, HarnessPackages.SOFTWARE_WINGS);
    }
    return reflections.getTypesAnnotatedWith(annotationClass, true);
  }

  public Set<Class<?>> getClasses(Class annotationClass) {
    return getClasses(null, annotationClass);
  }

  /**
   * @param clazz The class.
   * @return Name of the class in the swagger doc.
   */
  @SuppressWarnings("PMD")
  public String getSwaggerName(Class<?> clazz) {
    try {
      ApiModel declaredAnnotation = clazz.getDeclaredAnnotation(ApiModel.class);
      if (!isEmpty(declaredAnnotation.value())) {
        return declaredAnnotation.value();
      }
    } catch (NullPointerException e) {
      // do Nothing.
    }
    return clazz.getSimpleName();
  }

  /**
   * @param entityType     Entity type
   * @param schemaBasePath the base path inside which schema is stored.
   * @return The path which contains the complete schema for entityType.
   */
  public String getSchemaPathForEntityType(EntityType entityType, String schemaBasePath) {
    final String yamlName = entityType.getYamlName();
    String resourcePath = yamlName + File.separator + YamlConstants.SCHEMA_FILE_NAME;
    return isEmpty(schemaBasePath) ? resourcePath : schemaBasePath + File.separator + resourcePath;
  }

  public String getSnippetIndexPathForEntityType(
      EntityType entityType, String snippetBasePath, String snippetIndexFile) {
    return snippetBasePath + File.separator + entityType.getYamlName() + File.separator + snippetIndexFile;
  }

  /**
   * @param field the field for which we need json schema value.
   * @return the value of field.
   */
  public String getFieldName(Field field) {
    if (field.getAnnotation(ApiModelProperty.class) != null
        && isNotEmpty(field.getAnnotation(ApiModelProperty.class).name())) {
      return field.getAnnotation(ApiModelProperty.class).name();
    }
    if (field.getAnnotation(JsonProperty.class) != null) {
      return field.getAnnotation(JsonProperty.class).value();
    }
    return field.getName();
  }

  public Field getTypedField(Class<?> aClass) {
    for (Field declaredField : aClass.getDeclaredFields()) {
      JsonTypeInfo jsonTypeInfo = getJsonTypeInfo(declaredField);
      if (jsonTypeInfo != null) {
        return declaredField;
      }
    }
    return null;
  }

  public List<Field> getTypedFields(Class<?> aClass) {
    List<Field> typedFields = new ArrayList<>();

    for (Field declaredField : aClass.getDeclaredFields()) {
      JsonTypeInfo jsonTypeInfo = getJsonTypeInfo(declaredField);
      if (jsonTypeInfo != null) {
        typedFields.add(declaredField);
      }
    }
    return typedFields;
  }

  public FieldSubtypeData getFieldSubtypeData(Field typedField, Set<SubtypeClassMap> subtypeClassMaps) {
    if (isEmpty(subtypeClassMaps)) {
      return null;
    }
    JsonTypeInfo annotation = getJsonTypeInfo(typedField);
    if (annotation == null) {
      return null;
    }
    return FieldSubtypeData.builder()
        .fieldName(getFieldName(typedField))
        .discriminatorType(annotation.include())
        .discriminatorName(annotation.property())
        .subtypesMapping(subtypeClassMaps)
        .build();
  }

  public Set<SubtypeClassMap> getMapOfSubtypesUsingReflection(Field field) {
    JsonTypeInfo jsonTypeInfo = getJsonTypeInfo(field);
    if (jsonTypeInfo == null) {
      return null;
    }
    Reflections reflections = new Reflections("io.harness");
    Set<Class<?>> subTypesOf = reflections.getSubTypesOf((Class<Object>) field.getType());
    return subTypesOf.stream()
        .filter(c -> c.getAnnotation(JsonTypeName.class) != null)
        .filter(c -> c.getAnnotation(YamlSchemaIgnoreSubtype.class) == null)
        .map(aClass
            -> SubtypeClassMap.builder()
                   .subtypeEnum(aClass.getAnnotation(JsonTypeName.class).value())
                   .subTypeDefinitionKey(io.harness.yaml.utils.YamlSchemaUtils.getSwaggerName(aClass))
                   .subTypeClass(aClass)
                   .build())
        .collect(Collectors.toSet());
  }

  public Set<SubtypeClassMap> getMapOfSubtypesUsingObjectMapper(Field field, ObjectMapper objectMapper) {
    JsonTypeInfo jsonTypeInfo = getJsonTypeInfo(field);
    if (jsonTypeInfo == null) {
      return null;
    }
    Set<Class<?>> subTypesOf = new HashSet<>();
    MapperConfig<?> config = objectMapper.getDeserializationConfig();
    AnnotatedClass ac = AnnotatedClass.constructWithoutSuperTypes(field.getType(), config);
    final Collection<NamedType> namedTypes =
        objectMapper.getSubtypeResolver().collectAndResolveSubtypesByClass(config, ac);
    if (!namedTypes.isEmpty()) {
      for (NamedType namedType : namedTypes) {
        if (namedType.hasName()) {
          subTypesOf.add(namedType.getType());
        }
      }
    }
    if (subTypesOf.isEmpty()) {
      return null;
    }
    return subTypesOf.stream()
        .filter(c -> c.getAnnotation(JsonTypeName.class) != null)
        .filter(c -> c.getAnnotation(YamlSchemaIgnoreSubtype.class) == null)
        .map(aClass
            -> SubtypeClassMap.builder()
                   .subtypeEnum(aClass.getAnnotation(JsonTypeName.class).value())
                   .subTypeDefinitionKey(io.harness.yaml.utils.YamlSchemaUtils.getSwaggerName(aClass))
                   .subTypeClass(aClass)
                   .build())
        .collect(Collectors.toSet());
  }

  public Set<SubtypeClassMap> toSetOfSubtypeClassMap(Set<Class<?>> subtypes) {
    return subtypes.stream()
        .filter(c -> c.getAnnotation(JsonTypeName.class) != null)
        .filter(c -> c.getAnnotation(YamlSchemaIgnoreSubtype.class) == null)
        .map(aClass
            -> SubtypeClassMap.builder()
                   .subtypeEnum(aClass.getAnnotation(JsonTypeName.class).value())
                   .subTypeDefinitionKey(io.harness.yaml.utils.YamlSchemaUtils.getSwaggerName(aClass))
                   .subTypeClass(aClass)
                   .build())
        .collect(Collectors.toSet());
  }

  /**
   * @param field field for which subtypes are required, this method looks for JsonSubTypes annotation in field
   *              annotations than in filed's class annotations
   * @return JsonSubTypes annotation
   */
  public JsonSubTypes getJsonSubTypes(Field field) {
    JsonSubTypes annotation = field.getAnnotation(JsonSubTypes.class);
    if (annotation == null || isEmpty(annotation.value())) {
      annotation = field.getType().getAnnotation(JsonSubTypes.class);
    }
    if (checkIfClassIsCollection(field)) {
      ParameterizedType collectionType = (ParameterizedType) field.getGenericType();
      Class<?> collectionTypeClass = (Class<?>) collectionType.getActualTypeArguments()[0];
      annotation = collectionTypeClass.getAnnotation(JsonSubTypes.class);
    }
    if (annotation == null || isEmpty(annotation.value())) {
      return null;
    }
    return annotation;
  }

  public JsonTypeInfo getJsonTypeInfo(Field field) {
    JsonTypeInfo annotation = field.getAnnotation(JsonTypeInfo.class);
    if (annotation == null) {
      annotation = field.getType().getAnnotation(JsonTypeInfo.class);
    }

    if (checkIfClassIsCollection(field)) {
      ParameterizedType collectionType = (ParameterizedType) field.getGenericType();
      Class<?> collectionTypeClass = (Class<?>) collectionType.getActualTypeArguments()[0];
      if (checkIfClassShouldBeTraversed(collectionTypeClass)) {
        annotation = field.getAnnotation(JsonTypeInfo.class);
        if (annotation == null) {
          annotation = collectionTypeClass.getAnnotation(JsonTypeInfo.class);
        }
      }
    }
    return annotation;
  }

  public boolean checkIfClassIsCollection(Field declaredField) {
    return Collection.class.isAssignableFrom(declaredField.getType());
  }

  public boolean checkIfClassShouldBeTraversed(Field declaredField) {
    // Generating only for harness classes hence checking if package is software.wings or io.harness.
    return checkIfClassShouldBeTraversed(declaredField.getType());
  }

  public boolean checkIfClassShouldBeTraversed(Class clazz) {
    // Generating only for harness classes hence checking if package is software.wings or io.harness.
    return !clazz.isPrimitive() && !clazz.isEnum()
        && (clazz.getCanonicalName().startsWith("io.harness") || clazz.getCanonicalName().startsWith("software.wings"));
  }

  public void addOneOfInExecutionWrapperConfig(
      JsonNode pipelineSchema, Set<Class<?>> newYamlSchemaSubtypesToBeAdded, String namespace) {
    String nameSpaceString = "";
    if (isNotEmpty(namespace)) {
      nameSpaceString = namespace + "/";
    }
    JsonNode executionWrapperConfigProperties = pipelineSchema.get(EXECUTION_WRAPPER_CONFIG_NODE).get(PROPERTIES_NODE);
    ArrayNode oneOfNode = getOneOfNode(executionWrapperConfigProperties, STEP_NODE);
    JsonNode stepsNode = executionWrapperConfigProperties.get(STEP_NODE);

    for (Class<?> clazz : newYamlSchemaSubtypesToBeAdded) {
      oneOfNode.add(JsonNodeUtils.upsertPropertyInObjectNode(new ObjectNode(JsonNodeFactory.instance), REF_NODE,
          "#/definitions/" + nameSpaceString + clazz.getSimpleName()));
    }
    ((ObjectNode) stepsNode).set(ONE_OF_NODE, oneOfNode);
  }
  public void addOneOfInExecutionWrapperConfig(JsonNode pipelineSchema,
      List<YamlSchemaWithDetails> stepSchemaWithDetails, ModuleType moduleType, Set<String> enabledFeatureFlags,
      Map<String, Boolean> featureRestrictionsMap) {
    addOneOfInExecutionWrapperConfig(
        pipelineSchema, stepSchemaWithDetails, moduleType, enabledFeatureFlags, featureRestrictionsMap, true);
  }

  public void addOneOfInExecutionWrapperConfig(JsonNode pipelineSchema,
      List<YamlSchemaWithDetails> stepSchemaWithDetails, ModuleType moduleType, Set<String> enabledFeatureFlags,
      Map<String, Boolean> featureRestrictionsMap, boolean shouldFilter) {
    if (shouldFilter) {
      stepSchemaWithDetails =
          stepSchemaWithDetails.stream().filter(o -> o.getModuleType() != moduleType).collect(Collectors.toList());
    }
    JsonNode executionWrapperConfigProperties = pipelineSchema.get(EXECUTION_WRAPPER_CONFIG_NODE).get(PROPERTIES_NODE);
    ArrayNode oneOfNode = getOneOfNode(executionWrapperConfigProperties, STEP_NODE);
    JsonNode stepsNode = executionWrapperConfigProperties.get(STEP_NODE);

    for (YamlSchemaWithDetails schemaWithDetails : stepSchemaWithDetails) {
      // TODO: Use YamlSchemaMetadata.getNamespace instead of moduleType.
      String nameSpaceString = getNamespaceFromModuleType(schemaWithDetails.getModuleType());
      if (validateSchemaMetadata(schemaWithDetails, moduleType, enabledFeatureFlags, featureRestrictionsMap)) {
        oneOfNode.add(JsonNodeUtils.upsertPropertyInObjectNode(new ObjectNode(JsonNodeFactory.instance), REF_NODE,
            "#/definitions/" + nameSpaceString + schemaWithDetails.getSchemaClassName()));
      }
    }

    ((ObjectNode) stepsNode).set(ONE_OF_NODE, oneOfNode);
  }

  public void addOneOfInStageElementWrapperConfig(
      JsonNode pipelineSchema, List<YamlSchemaWithDetails> stepSchemaWithDetails) {
    JsonNode stageElementWrapperConfigProperties =
        pipelineSchema.get(STAGE_ELEMENT_WRAPPER_CONFIG).get(PROPERTIES_NODE);
    ArrayNode oneOfNode = getOneOfNode(stageElementWrapperConfigProperties, STAGE_NODE);
    JsonNode stepsNode = stageElementWrapperConfigProperties.get(STAGE_NODE);

    ArrayNode oneOfList = new ArrayNode(JsonNodeFactory.instance);
    for (JsonNode objectNode : oneOfNode) {
      if (objectNode.get(REF_NODE).asText().equals(STAGE_ELEMENT_CONFIG_REF_VALUE)) {
        continue;
      }
      oneOfList.add(objectNode);
    }
    oneOfNode = oneOfList;
    for (YamlSchemaWithDetails schemaWithDetails : stepSchemaWithDetails) {
      String nameSpaceString = getNamespace(schemaWithDetails.getYamlSchemaMetadata().getNamespace());
      oneOfNode.add(JsonNodeUtils.upsertPropertyInObjectNode(new ObjectNode(JsonNodeFactory.instance), REF_NODE,
          "#/definitions/" + nameSpaceString + schemaWithDetails.getSchemaClassName()));
    }

    ((ObjectNode) stepsNode).set(ONE_OF_NODE, oneOfNode);
  }

  private String getNamespace(String namespace) {
    if (namespace.isEmpty() || namespace.endsWith("/")) {
      return namespace;
    }
    return namespace + "/";
  }

  protected boolean validateSchemaMetadata(YamlSchemaWithDetails yamlSchemaWithDetails, ModuleType moduleType,
      Set<String> enabledFeatureFlags, Map<String, Boolean> featureRestrictionsMap) {
    YamlSchemaMetadata yamlSchemaMetadata = yamlSchemaWithDetails.getYamlSchemaMetadata();
    if (yamlSchemaMetadata == null) {
      return false;
    }
    List<ModuleType> supportedModules = CollectionUtils.emptyIfNull(yamlSchemaMetadata.getModulesSupported());
    if (!supportedModules.contains(moduleType)) {
      return false;
    }

    if (!validateByFeatureFlags(yamlSchemaMetadata, enabledFeatureFlags)) {
      return false;
    }

    return validateByFeatureRestrictions(yamlSchemaMetadata, featureRestrictionsMap);
  }

  protected boolean validateByFeatureFlags(YamlSchemaMetadata yamlSchemaMetadata, Set<String> enabledFeatureFlags) {
    List<String> requiredFeatureFlags = CollectionUtils.emptyIfNull(yamlSchemaMetadata.getFeatureFlags());
    for (String featureFlag : requiredFeatureFlags) {
      if (!enabledFeatureFlags.contains(featureFlag)) {
        return false;
      }
    }
    return true;
  }

  protected boolean validateByFeatureRestrictions(
      YamlSchemaMetadata yamlSchemaMetadata, Map<String, Boolean> featureRestrictionsMap) {
    List<String> requiredFeatureRestrictions = CollectionUtils.emptyIfNull(yamlSchemaMetadata.getFeatureRestrictions());
    for (String featureRestriction : requiredFeatureRestrictions) {
      if (featureRestrictionsMap.get(featureRestriction) == null || !featureRestrictionsMap.get(featureRestriction)) {
        return false;
      }
    }
    return true;
  }

  protected String getNamespaceFromModuleType(ModuleType moduleType) {
    if (moduleType == ModuleType.CD) {
      return "cd/";
    } else if (moduleType == ModuleType.CV) {
      return "cvng/";
    } else if (moduleType == ModuleType.SRM) {
      return "cvng/";
    } else if (moduleType == ModuleType.CI) {
      return "ci/";
    } else if (moduleType == ModuleType.CE) {
      return "ce/";
    } else if (moduleType == ModuleType.CF) {
      return "cf/";
    } else if (moduleType == ModuleType.STO) {
      return "sto/";
    }
    return "";
  }

  private ArrayNode getOneOfNode(JsonNode executionWrapperConfig, String nodeLevel) {
    ArrayNode oneOfList = new ArrayNode(JsonNodeFactory.instance);
    JsonNode stepsNode = executionWrapperConfig.get(nodeLevel);
    if (executionWrapperConfig.get(nodeLevel).get(REF_NODE) != null) {
      String stepElementConfigRef = executionWrapperConfig.get(nodeLevel).get(REF_NODE).toString().replace("\"", "");
      JsonNodeUtils.deletePropertiesInJsonNode((ObjectNode) stepsNode, REF_NODE);
      oneOfList.add(JsonNodeUtils.upsertPropertyInObjectNode(
          new ObjectNode(JsonNodeFactory.instance), REF_NODE, stepElementConfigRef));
    } else if (executionWrapperConfig.get(nodeLevel).get(ONE_OF_NODE) != null) {
      return (ArrayNode) executionWrapperConfig.get(nodeLevel).get(ONE_OF_NODE);
    }
    return oneOfList;
  }

  public Set<Class<?>> getNodeClassesByYamlGroup(List<YamlSchemaRootClass> yamlSchemaRootClasses, String yamlGroup,
      Set<String> enabledFeatureFlags, Map<String, Boolean> featureRestrictionsMap) {
    return filterRootClassesByYamlGroup(yamlSchemaRootClasses, yamlGroup)
        .stream()
        .filter(o -> validateByFeatureFlags(o.getYamlSchemaMetadata(), enabledFeatureFlags))
        .filter(o -> validateByFeatureRestrictions(o.getYamlSchemaMetadata(), featureRestrictionsMap))
        .map(YamlSchemaRootClass::getClazz)
        .collect(Collectors.toSet());
  }

  public List<EntityType> getNodeEntityTypesByYamlGroup(
      List<YamlSchemaRootClass> yamlSchemaRootClasses, String yamlGroup) {
    return filterRootClassesByYamlGroup(yamlSchemaRootClasses, yamlGroup)
        .stream()
        .map(YamlSchemaRootClass::getEntityType)
        .collect(Collectors.toList());
  }

  private List<YamlSchemaRootClass> filterRootClassesByYamlGroup(
      List<YamlSchemaRootClass> yamlSchemaRootClasses, String yamlGroup) {
    return yamlSchemaRootClasses.stream()
        .filter(yamlSchemaRootClass
            -> yamlSchemaRootClass.getYamlSchemaMetadata() != null
                && yamlSchemaRootClass.getYamlSchemaMetadata().getYamlGroup().getGroup().equals(yamlGroup))
        .collect(Collectors.toList());
  }
}
