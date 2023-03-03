/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.schema;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.yaml.schema.beans.SchemaConstants.ADDITIONAL_PROPERTIES_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.ALL_OF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.ARRAY_TYPE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.BOOL_TYPE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.DEFINITIONS_NAMESPACE_STRING_PATTERN;
import static io.harness.yaml.schema.beans.SchemaConstants.ENUM_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.EXPRESSION_PATTERN;
import static io.harness.yaml.schema.beans.SchemaConstants.INPUT_SET_PATTERN;
import static io.harness.yaml.schema.beans.SchemaConstants.INTEGER_TYPE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.ITEMS_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.MIN_LENGTH_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.NUMBER_STRING_WITH_EXPRESSION_PATTERN;
import static io.harness.yaml.schema.beans.SchemaConstants.NUMBER_STRING_WITH_EXPRESSION_PATTERN_WITH_EMPTY_VALUE;
import static io.harness.yaml.schema.beans.SchemaConstants.NUMBER_TYPE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.OBJECT_TYPE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.ONE_OF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.PATTERN_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.PROPERTIES_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.REF_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.REQUIRED_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN;
import static io.harness.yaml.schema.beans.SchemaConstants.RUNTIME_INPUT_PATTERN;
import static io.harness.yaml.schema.beans.SchemaConstants.RUNTIME_INPUT_PATTERN_EMPTY_STRING_ALLOWED;
import static io.harness.yaml.schema.beans.SchemaConstants.SCHEMA_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.STRING_TYPE_NODE;
import static io.harness.yaml.schema.beans.SchemaConstants.TYPE_NODE;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.reflection.CodeUtils;
import io.harness.yaml.schema.beans.FieldEnumData;
import io.harness.yaml.schema.beans.FieldSubtypeData;
import io.harness.yaml.schema.beans.FieldTypesMetadata;
import io.harness.yaml.schema.beans.OneOfMapping;
import io.harness.yaml.schema.beans.OneOfSetMapping;
import io.harness.yaml.schema.beans.SchemaConstants;
import io.harness.yaml.schema.beans.StringFieldTypeMetadata;
import io.harness.yaml.schema.beans.SubtypeClassMap;
import io.harness.yaml.schema.beans.SupportedPossibleFieldTypes;
import io.harness.yaml.schema.beans.SwaggerDefinitionsMetaInfo;
import io.harness.yaml.schema.beans.YamlSchemaConfiguration;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.schema.helper.FieldSubTypeComparator;
import io.harness.yaml.schema.helper.SubtypeClassMapComparator;
import io.harness.yaml.schema.helper.SupportedPossibleFieldTypesComparator;
import io.harness.yaml.utils.YamlConstants;
import io.harness.yaml.utils.YamlSchemaUtils;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.models.Model;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(DX)
public class YamlSchemaGenerator {
  JacksonClassHelper jacksonSubtypeHelper;
  SwaggerGenerator swaggerGenerator;
  List<YamlSchemaRootClass> rootClasses;

  public Map<EntityType, JsonNode> generateYamlSchema() {
    Map<EntityType, JsonNode> schema = new ConcurrentHashMap<>();
    rootClasses.forEach(rootSchemaClass -> {
      final Map<String, JsonNode> stringJsonNodeMap = generateJsonSchemaForRootClass(
          YamlSchemaConfiguration.builder().build(), swaggerGenerator, rootSchemaClass.getClazz());
      if (stringJsonNodeMap.size() != 1 || stringJsonNodeMap.get(YamlConstants.SCHEMA_FILE_NAME) == null) {
        throw new YamlSchemaException("Issue occurred while generation of schema.");
      }
      schema.put(rootSchemaClass.getEntityType(), stringJsonNodeMap.get(YamlConstants.SCHEMA_FILE_NAME));
    });
    return schema;
  }

  @VisibleForTesting
  Map<String, JsonNode> generateJsonSchemaForRootClass(
      YamlSchemaConfiguration yamlSchemaConfiguration, SwaggerGenerator swaggerGenerator, Class<?> rootSchemaClass) {
    log.info("Generating swagger for {}", rootSchemaClass.toString());
    final Map<String, Model> stringModelMap = swaggerGenerator.generateDefinitions(rootSchemaClass);
    log.info("Generated swagger");

    final Map<String, SwaggerDefinitionsMetaInfo> swaggerDefinitionsMetaInfoMap = getSwaggerMetaInfo(rootSchemaClass);
    log.info("Generated metainfo");

    final String entitySwaggerName = YamlSchemaUtils.getSwaggerName(rootSchemaClass);
    final String entityName =
        rootClasses.stream()
            .map(rootClazz
                -> rootClazz.getClazz().equals(rootSchemaClass) ? rootClazz.getEntityType().getYamlName() : null)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);

    log.info("Generating yaml schema for {}", entityName);
    final String pathForSchemaStorageForEntity =
        getPathToStoreSchema(yamlSchemaConfiguration, rootSchemaClass, entityName);
    final Map<String, JsonNode> stringJsonNodeMap = generateDefinitions(stringModelMap, pathForSchemaStorageForEntity,
        swaggerDefinitionsMetaInfoMap, entitySwaggerName, yamlSchemaConfiguration.isGenerateOnlyRootFile());
    if (yamlSchemaConfiguration.isGenerateFiles()) {
      ObjectWriter jsonWriter = getObjectWriter();
      stringJsonNodeMap.forEach((filePath, content) -> {
        try {
          writeContentsToFile(filePath, content, jsonWriter);
        } catch (IOException e) {
          throw new InvalidRequestException("Cannot save file", e);
        }
      });
      log.info("Saved files");
    }
    return stringJsonNodeMap;
  }

  @VisibleForTesting
  public ObjectWriter getObjectWriter() {
    ObjectMapper mapper = SchemaGeneratorUtils.getObjectMapperForSchemaGeneration();
    DefaultPrettyPrinter defaultPrettyPrinter = new SchemaGeneratorUtils.SchemaPrinter();
    return mapper.writer(defaultPrettyPrinter);
  }

  @VisibleForTesting
  String getPathToStoreSchema(
      YamlSchemaConfiguration yamlSchemaConfiguration, Class<?> rootSchemaClass, String entityName) {
    if (!yamlSchemaConfiguration.isGenerateFiles()) {
      return "";
    }
    List<String> locationList =
        Arrays.asList(Preconditions.checkNotNull(CodeUtils.location(rootSchemaClass)).split("/"));
    String moduleBasePath = "";
    // Bazel do not have target folder, so tha path is directly coming from .m2.
    // Instead of target we are using 0.0.1-SNAPSHOT to handle bazel build modules.
    if (locationList.contains("0.0.1-SNAPSHOT")) {
      moduleBasePath = locationList.get(locationList.indexOf("0.0.1-SNAPSHOT") - 1) + "/src/main/resources/";
    } else {
      moduleBasePath = locationList.get(locationList.indexOf("target") - 1) + "/src/main/resources/";
    }

    return moduleBasePath + yamlSchemaConfiguration.getGeneratedPathRoot() + File.separator + entityName
        + File.separator;
  }

  /**
   * @param definitions                   swagger generated definitions.
   * @param basePath                      path where schema will be stored.
   * @param swaggerDefinitionsMetaInfoMap extra info which will be added to definitions.
   * @param baseNodeKey                   The root yaml entity name in definition for which schema is being generated.
   * @return map of file path and the the json node of schema.
   */
  @VisibleForTesting
  Map<String, JsonNode> generateDefinitions(Map<String, Model> definitions, String basePath,
      Map<String, SwaggerDefinitionsMetaInfo> swaggerDefinitionsMetaInfoMap, String baseNodeKey,
      boolean generateRootOnly) {
    Map<String, JsonNode> filePathContentMap = new HashMap<>();
    ObjectMapper mapper = SchemaGeneratorUtils.getObjectMapperForSchemaGeneration();
    try {
      final String definitionsJson = mapper.writeValueAsString(definitions.entrySet());

      ArrayNode definitionsArrayNode = (ArrayNode) mapper.readTree(definitionsJson);
      ObjectNode modifiedNode = mapper.createObjectNode();

      for (JsonNode node : definitionsArrayNode) {
        // Assuming only single node in next
        final String name = node.fieldNames().next();
        ObjectNode value = (ObjectNode) node.get(name);
        convertSwaggerToJsonSchema(swaggerDefinitionsMetaInfoMap, mapper, name, value);
        if (!generateRootOnly) {
          filePathContentMap.put(basePath + name + YamlConstants.JSON_EXTENSION, value);
        }
        modifiedNode.with(name).setAll(value);
      }

      ObjectNode outputNode = mapper.createObjectNode();
      generateCompleteSchema(baseNodeKey, modifiedNode, outputNode);
      filePathContentMap.put(basePath + YamlConstants.SCHEMA_FILE_NAME, outputNode);
    } catch (IOException e) {
      log.error("Error in generating Yaml Schema.", e);
    }
    return filePathContentMap;
  }

  @VisibleForTesting
  void writeContentsToFile(@NotNull String fileName, @NotNull Object content, @NotNull ObjectWriter jsonWriter)
      throws IOException {
    Files.createDirectories(Paths.get(fileName).getParent());
    FileUtils.write(new File(fileName), jsonWriter.writeValueAsString(content), StandardCharsets.UTF_8);
    log.info("Saved Json Schema at: {}", fileName);
  }

  /**
   * @param baseNodeKey  Node Key which will act as root for complete schema.
   * @param modifiedNode The modified swagger spec which contains all json schema fields and also conditionals.
   * @param outputNode   Node in which final output will be dumped.
   */
  private void generateCompleteSchema(String baseNodeKey, ObjectNode modifiedNode, ObjectNode outputNode) {
    final JsonNode value = modifiedNode.findValue(baseNodeKey);
    if (value.isArray()) {
      outputNode.setAll((ObjectNode) value.get(0));
    } else {
      outputNode.setAll((ObjectNode) value);
    }
    outputNode.with(SchemaConstants.DEFINITIONS_NODE).setAll(modifiedNode);
  }

  public void convertSwaggerToJsonSchema(Map<String, SwaggerDefinitionsMetaInfo> swaggerDefinitionsMetaInfoMap,
      ObjectMapper mapper, String name, ObjectNode value) {
    value.put(SchemaConstants.SCHEMA_NODE, SchemaConstants.JSON_SCHEMA_7);
    if (swaggerDefinitionsMetaInfoMap.containsKey(name)) {
      final SwaggerDefinitionsMetaInfo swaggerDefinitionsMetaInfo = swaggerDefinitionsMetaInfoMap.get(name);
      List<ObjectNode> allOfNodeContents = new ArrayList<>();
      // conditionals
      if (!isEmpty(swaggerDefinitionsMetaInfo.getSubtypeClassMap())
          && swaggerDefinitionsMetaInfo.getOneOfSetMapping() == null) {
        addConditionalFields(swaggerDefinitionsMetaInfoMap, mapper, name, value, allOfNodeContents);
      }
      // oneof mapping
      if (!isEmpty(swaggerDefinitionsMetaInfo.getOneOfMappings())) {
        addExtraRequiredNodes(mapper, swaggerDefinitionsMetaInfo, allOfNodeContents);
      }
      // field multiple property
      if (!isEmpty(swaggerDefinitionsMetaInfo.getFieldPossibleTypes())) {
        addPossibleValuesInFields(mapper, value, swaggerDefinitionsMetaInfo);
      }
      // enum property
      if (isNotEmpty(swaggerDefinitionsMetaInfo.getFieldEnumData())) {
        addEnumProperty(value, swaggerDefinitionsMetaInfo.getFieldEnumData());
      }
      // non Empty fields
      if (!isEmpty(swaggerDefinitionsMetaInfo.getNotEmptyStringFields())) {
        addNotEmptyFields(value, swaggerDefinitionsMetaInfo.getNotEmptyStringFields());
      }

      if (swaggerDefinitionsMetaInfo.getOneOfSetMapping() != null) {
        addOneOfSetNodes(value, swaggerDefinitionsMetaInfo, mapper);
      }

      addAllOfNodeContents(value, allOfNodeContents);
    }

    removeUnwantedNodes(value, "originalRef");
  }

  private void addAllOfNodeContents(ObjectNode value, List<ObjectNode> allOfNodeContents) {
    if (isNotEmpty(allOfNodeContents)) {
      if (value.has(SchemaConstants.ALL_OF_NODE)) {
        final ArrayNode allOfNode = (ArrayNode) value.findValue(SchemaConstants.ALL_OF_NODE);
        allOfNode.addAll(allOfNodeContents);
      } else {
        value.putArray(SchemaConstants.ALL_OF_NODE).addAll(allOfNodeContents);
      }
    }
  }

  private void addOneOfSetNodes(
      ObjectNode value, SwaggerDefinitionsMetaInfo swaggerDefinitionsMetaInfo, ObjectMapper mapper) {
    OneOfSetMapping oneOfSetMapping = swaggerDefinitionsMetaInfo.getOneOfSetMapping();
    ObjectNode nodeWithProperties = getNodeWithPropertiesFromDefinitionNode(value);
    addRequiredNodes(nodeWithProperties, oneOfSetMapping.getRequiredFieldNames(), mapper);

    Map<String, List<ObjectNode>> externalPropertyFieldNamesToAllOfNodeMap = new HashMap<>();
    if (!isEmpty(swaggerDefinitionsMetaInfo.getSubtypeClassMap())) {
      addConditionalNodes(swaggerDefinitionsMetaInfo, mapper, value, externalPropertyFieldNamesToAllOfNodeMap);
    }

    List<ObjectNode> oneOfSetList = getOneOfSetList(
        mapper, oneOfSetMapping.getOneOfSets(), nodeWithProperties, externalPropertyFieldNamesToAllOfNodeMap);
    removePropertiesAndRequiredFieldsFromOriginalNode(nodeWithProperties);

    value.putArray(SchemaConstants.ONE_OF_NODE).addAll(oneOfSetList);
  }

  private void addRequiredNodes(ObjectNode nodeWithProperties, Set<String> requiredFieldNames, ObjectMapper mapper) {
    ArrayNode requiredFieldNamesArrayNode = mapper.createArrayNode();
    requiredFieldNames.forEach(requiredFieldNamesArrayNode::add);
    if (isNotEmpty(requiredFieldNames)) {
      if (nodeWithProperties.has(REQUIRED_NODE)) {
        ((ArrayNode) nodeWithProperties.get(REQUIRED_NODE)).addAll(requiredFieldNamesArrayNode);
      } else {
        nodeWithProperties.putArray(REQUIRED_NODE).addAll(requiredFieldNamesArrayNode);
      }
    }
  }

  private List<ObjectNode> getOneOfSetList(ObjectMapper mapper, Set<Set<String>> oneOfSets,
      ObjectNode nodeWithProperties, Map<String, List<ObjectNode>> externalPropertyFieldNamesToAllOfNodeMap) {
    List<ObjectNode> oneOfSetList = new ArrayList<>();
    Set<String> oneOfSetFieldNames = getAllOneOfSetFieldNames(oneOfSets);
    for (Set<String> oneOfSet : oneOfSets) {
      ObjectNode oneOfSetNode = nodeWithProperties.deepCopy();
      ObjectNode newPropertiesNode = (ObjectNode) oneOfSetNode.get(PROPERTIES_NODE);
      Set<String> fieldsToRemove = new HashSet<>(oneOfSetFieldNames);
      fieldsToRemove.removeAll(oneOfSet);
      newPropertiesNode.remove(fieldsToRemove);
      oneOfSetNode.set(PROPERTIES_NODE, newPropertiesNode);
      oneOfSetNode.put(ADDITIONAL_PROPERTIES_NODE, false);
      oneOfSetNode.remove(SCHEMA_NODE);
      ArrayNode requiredNode = (ArrayNode) oneOfSetNode.get(REQUIRED_NODE);
      if (requiredNode != null) {
        ArrayNode oneOfSetRequiredNode = mapper.createArrayNode();
        requiredNode.forEach(n -> {
          if (!fieldsToRemove.contains(n.asText())) {
            oneOfSetRequiredNode.add(n.asText());
          }
        });
        if (oneOfSetRequiredNode.size() > 0) {
          oneOfSetNode.set(REQUIRED_NODE, oneOfSetRequiredNode);
        } else {
          oneOfSetNode.remove(REQUIRED_NODE);
        }
      }
      externalPropertyFieldNamesToAllOfNodeMap.forEach((externalPropertyFieldName, allOfNodeList) -> {
        if (!fieldsToRemove.contains(externalPropertyFieldName)) {
          addAllOfNodeContents(oneOfSetNode, allOfNodeList);
        }
      });
      oneOfSetList.add(oneOfSetNode);
    }
    return oneOfSetList;
  }

  private Set<String> getAllOneOfSetFieldNames(Set<Set<String>> oneOfSets) {
    Set<String> oneOfSetFieldNames = new HashSet<>();
    oneOfSets.forEach(oneOfSetFieldNames::addAll);
    return oneOfSetFieldNames;
  }

  private void removePropertiesAndRequiredFieldsFromOriginalNode(ObjectNode nodeWithProperties) {
    nodeWithProperties.remove(PROPERTIES_NODE);
    nodeWithProperties.remove(REQUIRED_NODE);
    nodeWithProperties.remove(TYPE_NODE);
  }

  private ObjectNode getNodeWithPropertiesFromDefinitionNode(ObjectNode value) {
    // In case of child classes they contain parent node information in 0 index of all of.
    // assuming index 1 to have properties node.
    // later if we find a corner case we will have to iterate over all the nodes to find properties node and see if it
    // works.
    if (value.get(ALL_OF_NODE) != null && value.get(ALL_OF_NODE).get(1) != null
        && value.get(ALL_OF_NODE).get(1).has(PROPERTIES_NODE)) {
      return (ObjectNode) value.get(ALL_OF_NODE).get(1);
    } else {
      return value;
    }
  }

  private void addConditionalNodes(SwaggerDefinitionsMetaInfo swaggerDefinitionsMetaInfoMap, ObjectMapper mapper,
      ObjectNode value, Map<String, List<ObjectNode>> externalPropertyFieldNamesToAllOfNodeMap) {
    List<FieldSubtypeData> fieldSubtypeDatas = new ArrayList<>(swaggerDefinitionsMetaInfoMap.getSubtypeClassMap());
    fieldSubtypeDatas.sort(new FieldSubTypeComparator());
    for (FieldSubtypeData fieldSubtypeData : fieldSubtypeDatas) {
      if (fieldSubtypeData.getDiscriminatorType() == JsonTypeInfo.As.EXTERNAL_PROPERTY) {
        List<ObjectNode> allOfNodeContents = new ArrayList<>();
        addConditionalBlock(mapper, allOfNodeContents, fieldSubtypeData);
        externalPropertyFieldNamesToAllOfNodeMap.put(fieldSubtypeData.getFieldName(), allOfNodeContents);
        emptyFieldWithRefFromSchema(value, fieldSubtypeData.getFieldName());
      } else {
        addInternalConditionalBlock(value, mapper, fieldSubtypeData);
      }
    }
  }

  private void emptyFieldWithRefFromSchema(ObjectNode value, String fieldName) {
    ObjectNode propertiesNode = (ObjectNode) value.findValue(PROPERTIES_NODE);
    if (propertiesNode != null && propertiesNode.has(fieldName)) {
      ((ObjectNode) propertiesNode.get(fieldName)).removeAll();
    }
  }

  private void addNotEmptyFields(ObjectNode value, Set<String> nonEmptyFields) {
    ObjectNode properties = getPropertiesNodeFromDefinitionNode(value);
    if (properties != null) {
      addNonEmptyFieldsInPropertiesNode(nonEmptyFields, properties);
    } else {
      ArrayNode oneOfNodes = (ArrayNode) value.get(ONE_OF_NODE);
      if (oneOfNodes != null) {
        oneOfNodes.forEach(oneOfNode -> {
          if (oneOfNode.has(PROPERTIES_NODE)) {
            addNonEmptyFieldsInPropertiesNode(nonEmptyFields, (ObjectNode) oneOfNode.get(PROPERTIES_NODE));
          }
        });
      }
    }
  }

  private void addNonEmptyFieldsInPropertiesNode(Set<String> nonEmptyFields, ObjectNode properties) {
    // iterate over the created set
    for (String fieldName : nonEmptyFields) {
      ObjectNode objectNode = (ObjectNode) properties.get(fieldName);
      if (objectNode != null) {
        objectNode.put(MIN_LENGTH_NODE, 1);
      }
    }
  }

  private void addEnumProperty(ObjectNode value, Set<FieldEnumData> fieldEnumData) {
    ObjectNode properties = getPropertiesNodeFromDefinitionNode(value);
    // properties can be null if oneOfSet annotation is added on the class.
    if (properties != null) {
      addEnumPropertyInPropertiesNode(fieldEnumData, properties);
    } else {
      ArrayNode oneOfNodes = (ArrayNode) value.get(ONE_OF_NODE);
      if (oneOfNodes != null) {
        oneOfNodes.forEach(oneOfNode -> {
          if (oneOfNode.has(PROPERTIES_NODE)) {
            addEnumPropertyInPropertiesNode(fieldEnumData, (ObjectNode) oneOfNode.get(PROPERTIES_NODE));
          }
        });
      }
    }
  }

  private void addEnumPropertyInPropertiesNode(Set<FieldEnumData> fieldEnumData, ObjectNode propertiesNode) {
    for (FieldEnumData enumData : fieldEnumData) {
      ObjectNode type = (ObjectNode) propertiesNode.get(enumData.getFieldName());
      if (type == null) {
        continue;
      }
      if (type.get(ENUM_NODE) == null) {
        type.putArray(ENUM_NODE);
      }
      ArrayNode enumNode = (ArrayNode) type.get(ENUM_NODE);
      enumData.getEnumValues().forEach(enumNode::add);
    }
  }

  private void addPossibleValuesInFields(
      ObjectMapper mapper, ObjectNode value, SwaggerDefinitionsMetaInfo swaggerDefinitionsMetaInfo) {
    ObjectNode propertiesNode = getPropertiesNodeFromDefinitionNode(value);
    // properties node can be null if one of set annotation is present on class.
    if (propertiesNode != null) {
      addPossibleValuesInPropertiesNode(mapper, swaggerDefinitionsMetaInfo, propertiesNode);
    } else {
      ArrayNode oneOfNodes = (ArrayNode) value.get(ONE_OF_NODE);
      if (oneOfNodes != null) {
        oneOfNodes.forEach(oneOfNode -> {
          if (oneOfNode.has(PROPERTIES_NODE)) {
            addPossibleValuesInPropertiesNode(
                mapper, swaggerDefinitionsMetaInfo, (ObjectNode) oneOfNode.get(PROPERTIES_NODE));
          }
        });
      }
    }
  }

  private void addPossibleValuesInPropertiesNode(
      ObjectMapper mapper, SwaggerDefinitionsMetaInfo swaggerDefinitionsMetaInfo, ObjectNode propertiesNode) {
    swaggerDefinitionsMetaInfo.getFieldPossibleTypes().forEach(fieldPossibleTypes -> {
      final String fieldName = fieldPossibleTypes.getFieldName();
      final SupportedPossibleFieldTypes defaultFieldType = fieldPossibleTypes.getDefaultFieldType();
      final ObjectNode fieldNode = (ObjectNode) propertiesNode.get(fieldName);
      final FieldTypesMetadata fieldTypesMetadata = fieldPossibleTypes.getFieldTypesMetadata();
      List<ObjectNode> fieldOneOfNodes = new ArrayList<>();
      if (fieldNode != null) {
        final ObjectNode currentFieldNodeValue = fieldNode.deepCopy();
        final List<SupportedPossibleFieldTypes> fieldTypes = new ArrayList<>(fieldPossibleTypes.getFieldTypes());
        fieldTypes.sort(new SupportedPossibleFieldTypesComparator());
        fieldTypes.forEach(type -> {
          final ObjectNode nodeFromType = getNodeFromType(type, mapper);
          if (nodeFromType == null) {
            return;
          }
          addOtherPropertiesInNode(type, fieldTypesMetadata, nodeFromType);
          if (type.equals(defaultFieldType)) {
            fieldOneOfNodes.add(0, nodeFromType);
          } else {
            fieldOneOfNodes.add(nodeFromType);
          }
        });

        if (defaultFieldType == SupportedPossibleFieldTypes.none) {
          fieldOneOfNodes.add(0, currentFieldNodeValue);
        } else {
          fieldOneOfNodes.add(currentFieldNodeValue);
        }
        fieldNode.removeAll();
        fieldNode.putArray(ONE_OF_NODE).addAll(fieldOneOfNodes);
      }
    });
  }

  private void addOtherPropertiesInNode(
      SupportedPossibleFieldTypes type, FieldTypesMetadata fieldTypesMetadata, ObjectNode nodeFromType) {
    switch (type) {
      case string:
        if (fieldTypesMetadata != null) {
          final StringFieldTypeMetadata stringFieldTypeMetadata = (StringFieldTypeMetadata) fieldTypesMetadata;
          final int minLength = stringFieldTypeMetadata.getMinLength();
          final String pattern = stringFieldTypeMetadata.getPattern();
          if (minLength > 0) {
            nodeFromType.put(MIN_LENGTH_NODE, minLength);
          }
          if (isNotEmpty(pattern)) {
            nodeFromType.put(PATTERN_NODE, pattern);
          }
        }
        break;
      default:
        break;
    }
  }

  private ObjectNode getPropertiesNodeFromDefinitionNode(ObjectNode value) {
    // In case of child classes they contain parent node information in 0 index of all of.
    // assuming index 1 to have properties node.
    // later if we find a corner case we will have to iterate over all the nodes to find properties node and see if it
    // works.
    ObjectNode propertiesNode;
    if (value.get(ALL_OF_NODE) != null && value.get(ALL_OF_NODE).get(1) != null
        && value.get(ALL_OF_NODE).get(1).has(PROPERTIES_NODE)) {
      propertiesNode = (ObjectNode) value.get(ALL_OF_NODE).get(1).get(PROPERTIES_NODE);
    } else {
      propertiesNode = (ObjectNode) value.get(PROPERTIES_NODE);
    }
    return propertiesNode;
  }

  private ObjectNode getNodeFromType(SupportedPossibleFieldTypes type, ObjectMapper mapper) {
    ObjectNode objectNode = mapper.createObjectNode();
    switch (type) {
      case string:
        objectNode.put(TYPE_NODE, STRING_TYPE_NODE);
        return objectNode;
      case number:
        objectNode.put(TYPE_NODE, NUMBER_TYPE_NODE);
        return objectNode;
      case integer:
        objectNode.put(TYPE_NODE, INTEGER_TYPE_NODE);
        return objectNode;
      case bool:
        objectNode.put(TYPE_NODE, BOOL_TYPE_NODE);
        return objectNode;
      case map:
        objectNode.put(TYPE_NODE, OBJECT_TYPE_NODE);
        objectNode.putObject(SchemaConstants.ADDITIONAL_PROPERTIES_NODE).put(TYPE_NODE, STRING_TYPE_NODE);
        return objectNode;
      case list:
        objectNode.put(TYPE_NODE, ARRAY_TYPE_NODE);
        return objectNode;
      case numberString:
        objectNode.put(TYPE_NODE, STRING_TYPE_NODE);
        objectNode.put(PATTERN_NODE, NUMBER_STRING_WITH_EXPRESSION_PATTERN);
        return objectNode;
      case numberStringWithEmptyValue:
        objectNode.put(TYPE_NODE, STRING_TYPE_NODE);
        objectNode.put(PATTERN_NODE, NUMBER_STRING_WITH_EXPRESSION_PATTERN_WITH_EMPTY_VALUE);
        return objectNode;

      case runtimeButNotExecutionTime:
        /*
        added to support runtime field type, like <+input>. This includes allowedValues and default value. But the value
        can not be execution time input.
         */
        objectNode.put(TYPE_NODE, STRING_TYPE_NODE);
        objectNode.put(PATTERN_NODE, RUNTIME_BUT_NOT_EXECUTION_TIME_PATTERN);
        objectNode.put(MIN_LENGTH_NODE, 1);
        return objectNode;
      case expression:
        /*
        added to support expression pattern. like <+exp.val>. This includes runtime input pattern as well(<+input>).
         */
        objectNode.put(TYPE_NODE, STRING_TYPE_NODE);
        objectNode.put(PATTERN_NODE, EXPRESSION_PATTERN);
        objectNode.put(MIN_LENGTH_NODE, 1);
        return objectNode;
      case runtime:
        /*
        added to support runtime field type, like <+input>. This includes allowedValues and default value. It also
        included the execution time fields, like<+input>.executionInput()
         */
        objectNode.put(TYPE_NODE, STRING_TYPE_NODE);
        objectNode.put(PATTERN_NODE, RUNTIME_INPUT_PATTERN);
        objectNode.put(MIN_LENGTH_NODE, 1);
        return objectNode;
      case runtimeEmptyStringAllowed:
        objectNode.put(TYPE_NODE, STRING_TYPE_NODE);
        objectNode.put(PATTERN_NODE, RUNTIME_INPUT_PATTERN_EMPTY_STRING_ALLOWED);
        return objectNode;
      // only <+input> is allowed
      case onlyRuntimeInputAllowed:
        objectNode.put(TYPE_NODE, STRING_TYPE_NODE);
        objectNode.put(PATTERN_NODE, INPUT_SET_PATTERN);
        objectNode.put(MIN_LENGTH_NODE, 1);
        return objectNode;
      case none:
        return null;
      default:
        throw new InvalidRequestException("Unknown type for generating object node for a type");
    }
  }

  private void addExtraRequiredNodes(
      ObjectMapper mapper, SwaggerDefinitionsMetaInfo swaggerDefinitionsMetaInfo, List<ObjectNode> allOfNodeContents) {
    final Set<OneOfMapping> oneOfMappings = swaggerDefinitionsMetaInfo.getOneOfMappings();
    final List<ObjectNode> allOfNodeRequiredContent =
        oneOfMappings.stream()
            .map(mapping -> {
              final List<ObjectNode> requiredNodes =
                  mapping.getOneOfFieldNames()
                      .stream()
                      .map(field -> {
                        ObjectNode objectNode = mapper.createObjectNode();
                        objectNode.putArray(SchemaConstants.REQUIRED_NODE).add(field);
                        return objectNode;
                      })
                      .collect(Collectors.toList());

              //  mapping isNullable not handled.
              return requiredNodes;
            })
            .map(oneOfMapContent -> {
              ObjectNode oneOfNode = mapper.createObjectNode();
              oneOfNode.putArray(SchemaConstants.ONE_OF_NODE).addAll(oneOfMapContent);
              return oneOfNode;
            })
            .collect(Collectors.toList());
    allOfNodeContents.addAll(allOfNodeRequiredContent);
  }

  private void addConditionalFields(Map<String, SwaggerDefinitionsMetaInfo> swaggerDefinitionsMetaInfoMap,
      ObjectMapper mapper, String name, ObjectNode value, List<ObjectNode> allOfNodeContents) {
    ObjectNode propertiesNodeFromDefinitionNode = getPropertiesNodeFromDefinitionNode(value);
    // properties might not exist if oneOfSet annotation is added on the class.
    if (propertiesNodeFromDefinitionNode != null) {
      addConditionalAndCleanupFields(swaggerDefinitionsMetaInfoMap, mapper, name, value, allOfNodeContents);
    } else {
      List<FieldSubtypeData> fieldSubtypeDatas =
          new ArrayList<>(swaggerDefinitionsMetaInfoMap.get(name).getSubtypeClassMap());
      Map<String, List<ObjectNode>> externalPropertyFieldNamesToAllOfNodeMap =
          getConditionalNodesForExternalProperties(fieldSubtypeDatas, mapper);
      ArrayNode oneOfNodes = (ArrayNode) value.get(ONE_OF_NODE);
      if (oneOfNodes != null) {
        oneOfNodes.forEach(oneOfNode -> {
          if (oneOfNode.has(PROPERTIES_NODE)) {
            ObjectNode propertiesNode = (ObjectNode) oneOfNode.get(PROPERTIES_NODE);
            externalPropertyFieldNamesToAllOfNodeMap.forEach((fieldName, allOfNodeList) -> {
              if (propertiesNode.has(fieldName)) {
                addAllOfNodeContents((ObjectNode) oneOfNode, allOfNodeList);
              }
            });

            addConditionalNodesForOtherJsonTypeProperties(fieldSubtypeDatas, mapper, (ObjectNode) oneOfNode);
          }
        });
      }
    }
  }

  private Map<String, List<ObjectNode>> getConditionalNodesForExternalProperties(
      List<FieldSubtypeData> fieldSubtypeDatas, ObjectMapper mapper) {
    Map<String, List<ObjectNode>> externalPropertyFieldNamesToAllOfNodeMap = new HashMap<>();
    fieldSubtypeDatas.sort(new FieldSubTypeComparator());
    for (FieldSubtypeData fieldSubtypeData : fieldSubtypeDatas) {
      if (fieldSubtypeData.getDiscriminatorType() == JsonTypeInfo.As.EXTERNAL_PROPERTY) {
        List<ObjectNode> allOfNodeContents = new ArrayList<>();
        addConditionalBlock(mapper, allOfNodeContents, fieldSubtypeData);
        externalPropertyFieldNamesToAllOfNodeMap.put(fieldSubtypeData.getFieldName(), allOfNodeContents);
      }
    }
    return externalPropertyFieldNamesToAllOfNodeMap;
  }

  private void addConditionalNodesForOtherJsonTypeProperties(
      List<FieldSubtypeData> fieldSubtypeDatas, ObjectMapper mapper, ObjectNode value) {
    fieldSubtypeDatas.sort(new FieldSubTypeComparator());
    for (FieldSubtypeData fieldSubtypeData : fieldSubtypeDatas) {
      if (fieldSubtypeData.getDiscriminatorType() != JsonTypeInfo.As.EXTERNAL_PROPERTY) {
        if (value.has(PROPERTIES_NODE) && value.get(PROPERTIES_NODE).has(fieldSubtypeData.getFieldName())) {
          addInternalConditionalBlock(value, mapper, fieldSubtypeData);
        }
      }
    }
  }

  private void addConditionalAndCleanupFields(Map<String, SwaggerDefinitionsMetaInfo> swaggerDefinitionsMetaInfoMap,
      ObjectMapper mapper, String name, ObjectNode value, List<ObjectNode> allOfNodeContents) {
    List<FieldSubtypeData> fieldSubtypeDatas =
        new ArrayList<>(swaggerDefinitionsMetaInfoMap.get(name).getSubtypeClassMap());
    fieldSubtypeDatas.sort(new FieldSubTypeComparator());
    for (FieldSubtypeData fieldSubtypeData : fieldSubtypeDatas) {
      if (fieldSubtypeData.getDiscriminatorType() == JsonTypeInfo.As.EXTERNAL_PROPERTY) {
        addConditionalBlock(mapper, allOfNodeContents, fieldSubtypeData);
        removeFieldWithRefFromSchema(value, fieldSubtypeData);
      } else {
        addInternalConditionalBlock(value, mapper, fieldSubtypeData);
      }
    }
  }

  // todo(abhinav): solve it to handle conditional better.
  private void addInternalConditionalBlock(ObjectNode value, ObjectMapper mapper, FieldSubtypeData fieldSubtypeData) {
    ObjectNode propertiesNodeFromDefinitionNode = getPropertiesNodeFromDefinitionNode(value);
    final String fieldName = fieldSubtypeData.getFieldName();
    final ObjectNode fieldNode = (ObjectNode) propertiesNodeFromDefinitionNode.get(fieldName);
    if (fieldNode == null) {
      log.warn("We can have some error in schema of node {} with {}.", fieldName, fieldSubtypeData);
      return;
    }
    if (fieldNode.get(ONE_OF_NODE) != null) {
      throw new InvalidRequestException("Both Subtype and one of not handled for a single field");
    }
    final List<SubtypeClassMap> subtypesMapping = new ArrayList<>(fieldSubtypeData.getSubtypesMapping());
    subtypesMapping.sort(new SubtypeClassMapComparator());
    List<ObjectNode> possibleNodes =
        subtypesMapping.stream()
            .map(fieldData
                -> mapper.createObjectNode().put(
                    REF_NODE, SchemaConstants.DEFINITIONS_STRING_PREFIX + fieldData.getSubTypeDefinitionKey()))
            .collect(Collectors.toList());
    final JsonNode refNode = fieldNode.get(REF_NODE);
    // In Case of non list variables case 1 will happen else case 2 will happen
    if (refNode != null) {
      fieldNode.remove(REF_NODE);
      fieldNode.putArray(ONE_OF_NODE).addAll(possibleNodes);
    } else {
      final ObjectNode itemsNode = (ObjectNode) fieldNode.get(ITEMS_NODE);
      itemsNode.remove(REF_NODE);
      itemsNode.putArray(ONE_OF_NODE).addAll(possibleNodes);
    }
  }

  private void removeFieldWithRefFromSchema(ObjectNode value, FieldSubtypeData fieldSubtypeData) {
    final String fieldName = fieldSubtypeData.getFieldName();
    ObjectNode propertiesNode = (ObjectNode) value.findValue(PROPERTIES_NODE);
    propertiesNode.remove(fieldName);
  }

  public void removeUnwantedNodes(JsonNode objectNode, String unwantedNode) {
    if (objectNode.isArray()) {
      final Iterator<JsonNode> elements = objectNode.elements();
      while (elements.hasNext()) {
        final JsonNode node = elements.next();
        if (node.isArray()) {
          ArrayNode arrayNode = (ArrayNode) node;
          final Iterator<JsonNode> nodeIterator = arrayNode.elements();
          while (nodeIterator.hasNext()) {
            JsonNode nextNode = nodeIterator.next();
            removeUnwantedNodes(nextNode, unwantedNode);
          }
        } else if (node.isObject()) {
          removeUnwantedNodes(node, unwantedNode);
        }
      }
    } else if (objectNode.isObject()) {
      ObjectNode node = (ObjectNode) objectNode;
      node.remove(unwantedNode);
      final Iterator<JsonNode> elements = node.elements();
      while (elements.hasNext()) {
        removeUnwantedNodes(elements.next(), unwantedNode);
      }
    }
  }

  public void modifyRefsNamespace(JsonNode objectNode, String namespace) {
    if (objectNode.isArray()) {
      final Iterator<JsonNode> elements = objectNode.elements();
      while (elements.hasNext()) {
        final JsonNode node = elements.next();
        if (node.isArray()) {
          ArrayNode arrayNode = (ArrayNode) node;
          final Iterator<JsonNode> nodeIterator = arrayNode.elements();
          while (nodeIterator.hasNext()) {
            JsonNode nextNode = nodeIterator.next();
            modifyRefsNamespace(nextNode, namespace);
          }
        } else if (node.isObject()) {
          modifyRefsNamespace(node, namespace);
        }
      }
    } else if (objectNode.isObject()) {
      ObjectNode node = (ObjectNode) objectNode;
      JsonNode jsonNode = node.remove(REF_NODE);
      if (jsonNode != null && jsonNode.isTextual()) {
        String refValue = jsonNode.textValue();
        refValue = refValue.substring(refValue.lastIndexOf('/') + 1);
        node.put(REF_NODE, String.format(DEFINITIONS_NAMESPACE_STRING_PATTERN, namespace, refValue));
      }
      final Iterator<JsonNode> elements = node.elements();
      while (elements.hasNext()) {
        modifyRefsNamespace(elements.next(), namespace);
      }
    }
  }

  /**
   * @param mapper            object mapper.
   * @param allOfNodeContents the conditional block in which new condition is to be added.
   * @param fieldSubtypeData  the subtype mapping for fields which will be added to conditionals.
   *                          Adds conditional block to the single definition.
   *                          Currently only handled for {@link JsonTypeInfo.As#EXTERNAL_PROPERTY}
   */
  public void addConditionalBlock(
      ObjectMapper mapper, List<ObjectNode> allOfNodeContents, FieldSubtypeData fieldSubtypeData) {
    final List<SubtypeClassMap> fieldSubtypeDataList = fieldSubtypeData.getSubtypesMapping() == null
        ? new ArrayList<>()
        : new ArrayList<>(fieldSubtypeData.getSubtypesMapping());
    fieldSubtypeDataList.sort(new SubtypeClassMapComparator());
    for (SubtypeClassMap subtypeClassMap : fieldSubtypeDataList) {
      ObjectNode ifElseBlock = mapper.createObjectNode();
      ifElseBlock.with(SchemaConstants.IF_NODE)
          .with(PROPERTIES_NODE)
          .with(fieldSubtypeData.getDiscriminatorName())
          .put(SchemaConstants.CONST_NODE, subtypeClassMap.getSubtypeEnum());
      ifElseBlock.with(SchemaConstants.THEN_NODE)
          .with(PROPERTIES_NODE)
          .with(fieldSubtypeData.getFieldName())
          .put(SchemaConstants.REF_NODE,
              SchemaConstants.DEFINITIONS_STRING_PREFIX + subtypeClassMap.getSubTypeDefinitionKey());
      allOfNodeContents.add(ifElseBlock);
    }
  }

  @VisibleForTesting
  Map<String, SwaggerDefinitionsMetaInfo> getSwaggerMetaInfo(Class<?> clazz) {
    Map<String, SwaggerDefinitionsMetaInfo> swaggerDefinitionsMetaInfoMap = new HashMap<>();
    jacksonSubtypeHelper.getRequiredMappings(clazz, swaggerDefinitionsMetaInfoMap);
    return swaggerDefinitionsMetaInfoMap;
  }
}
