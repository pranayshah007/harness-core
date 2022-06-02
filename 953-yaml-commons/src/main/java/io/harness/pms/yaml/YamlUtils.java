/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.serializer.AnnotationAwareJsonSubtypeResolver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Preconditions;
import io.serializer.jackson.NGHarnessJacksonModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PIPELINE)
public class YamlUtils {
  private static final List<String> ignorableStringForQualifiedName = Arrays.asList("step", "parallel");

  private final ObjectMapper mapper;

  static {
    mapper = new ObjectMapper(new YAMLFactory());
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.setSubtypeResolver(AnnotationAwareJsonSubtypeResolver.newInstance(mapper.getSubtypeResolver()));
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new NGHarnessJacksonModule());
  }

  public <T> T read(String yaml, Class<T> cls) throws IOException {
    return mapper.readValue(yaml, cls);
  }
  public <T> T read(String yaml, TypeReference<T> valueTypeRef) throws IOException {
    return mapper.readValue(yaml, valueTypeRef);
  }

  public String write(Object object) {
    try {
      return mapper.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Couldn't convert object to Yaml");
    }
  }

  public YamlField readTree(String content) throws IOException {
    JsonNode rootJsonNode = mapper.readTree(content);
    YamlNode rootYamlNode = new YamlNode(rootJsonNode);
    return new YamlField(rootYamlNode);
  }

  public YamlField readTree(Object obj, String currentFieldName, YamlNode parentNode) {
    try {
      JsonNode currentJsonNode = mapper.valueToTree(obj);
      YamlNode currentYamlNode = new YamlNode(currentFieldName, currentJsonNode, parentNode);
      return new YamlField(currentYamlNode);
    } catch (Exception e) {
      throw new InvalidRequestException("Couldn't convert object to Yaml", e);
    }
  }

  public YamlField toByteString(String content) throws IOException {
    JsonNode rootJsonNode = mapper.readTree(content);
    YamlNode rootYamlNode = new YamlNode(rootJsonNode);
    return new YamlField(rootYamlNode);
  }

  public YamlField extractPipelineField(String content) throws IOException {
    YamlField rootYamlField = readTree(content);
    YamlNode rootYamlNode = rootYamlField.getNode();
    return Preconditions.checkNotNull(
        getPipelineField(rootYamlNode), "Invalid pipeline YAML: root of the yaml needs to be an object");
  }

  public YamlField getPipelineField(YamlNode rootYamlNode) {
    return (rootYamlNode == null || !rootYamlNode.isObject()) ? null : rootYamlNode.getField("pipeline");
  }

  public YamlField getTopRootFieldInYaml(String content) throws IOException {
    YamlField rootYamlField = readTree(content);
    YamlNode rootYamlNode = rootYamlField.getNode();
    return Preconditions.checkNotNull(
        getTopRootFieldInYamlField(rootYamlNode), "Invalid pipeline YAML: root of the yaml needs to be an object");
  }

  public YamlField getTopRootFieldInYamlField(YamlNode rootYamlNode) {
    if (rootYamlNode == null || !rootYamlNode.isObject()) {
      return null;
    }
    for (YamlField field : rootYamlNode.fields()) {
      if (field.getName().equals(YamlNode.UUID_FIELD_NAME)) {
        continue;
      }
      return field;
    }
    throw new InvalidRequestException("No Top root node available in the yaml.");
  }

  public YamlField injectUuidWithLeafUuid(String content) throws IOException {
    JsonNode rootJsonNode = mapper.readTree(content);
    if (rootJsonNode == null) {
      return null;
    }
    injectUuidWithLeafUuid(rootJsonNode);
    YamlNode rootYamlNode = new YamlNode(rootJsonNode);
    return new YamlField(rootYamlNode);
  }

  private void injectUuidWithLeafUuid(JsonNode node) {
    if (node.isObject()) {
      injectUuidInObjectWithLeafValues(node);
    } else if (node.isArray()) {
      injectUuidInArrayWithLeafUuid(node);
    }
  }

  private void injectUuidInArrayWithLeafUuid(JsonNode node) {
    ArrayNode arrayNode = (ArrayNode) node;
    for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext();) {
      injectUuidWithLeafUuid(it.next());
    }
  }

  private void injectUuidInObjectWithLeafValues(JsonNode node) {
    ObjectNode objectNode = (ObjectNode) node;
    objectNode.put(YamlNode.UUID_FIELD_NAME, generateUuid());
    boolean isIdentifierPresent = false;

    Entry<String, JsonNode> nameField = null;
    Entry<String, JsonNode> keyField = null;

    for (Iterator<Entry<String, JsonNode>> it = objectNode.fields(); it.hasNext();) {
      Entry<String, JsonNode> field = it.next();
      if (field.getValue().isValueNode()) {
        switch (field.getKey()) {
          case YamlNode.IDENTIFIER_FIELD_NAME:
            isIdentifierPresent = true;
            break;
          case YamlNode.NAME_FIELD_NAME:
            nameField = field;
            break;
          case YamlNode.KEY_FIELD_NAME:
            keyField = field;
            break;
          case YamlNode.UUID_FIELD_NAME:
          case YamlNode.TYPE_FIELD_NAME:
            break;
          default:
            objectNode.put(field.getKey(), generateUuid());
            break;
        }
      } else if (checkIfNodeIsArrayWithPrimitiveTypes(field.getValue())) {
        objectNode.put(field.getKey(), generateUuid());
      } else {
        injectUuidWithLeafUuid(field.getValue());
      }
    }

    if (isIdentifierPresent && nameField != null) {
      objectNode.put(nameField.getKey(), generateUuid());
    }
    if (keyField != null && (isIdentifierPresent || nameField != null)) {
      objectNode.put(keyField.getKey(), generateUuid());
    }
  }

  public boolean checkIfNodeIsArrayWithPrimitiveTypes(JsonNode jsonNode) {
    if (jsonNode.isArray()) {
      ArrayNode arrayNode = (ArrayNode) jsonNode;
      // Empty array is not primitive array
      if (arrayNode.size() == 0) {
        return false;
      }
      for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext();) {
        if (!it.next().isValueNode()) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  public String injectUuid(String content) throws IOException {
    YamlField yamlField = injectUuidInYamlField(content);
    return yamlField.getNode().toString();
  }

  public YamlField injectUuidInYamlField(String content) throws IOException {
    JsonNode rootJsonNode = mapper.readTree(content);
    if (rootJsonNode == null) {
      return null;
    }

    injectUuid(rootJsonNode);
    YamlNode rootYamlNode = new YamlNode(rootJsonNode);
    return new YamlField(rootYamlNode);
  }

  private void injectUuid(JsonNode node) {
    if (node.isObject()) {
      injectUuidInObject(node);
    } else if (node.isArray()) {
      injectUuidInArray(node);
    }
  }

  /**
   * Get Qualified Name till the pipeline level
   * @param yamlNode
   * @return
   */
  public String getFullyQualifiedName(YamlNode yamlNode) {
    return String.join(".", getQualifiedNameList(yamlNode, "pipeline"));
  }

  /**
   * Get Qualified Name till the root level
   * @param yamlNode
   * @return
   */
  public String getFullyQualifiedNameTillRoot(YamlNode yamlNode) {
    return String.join(".", getQualifiedNameList(yamlNode, "root"));
  }

  /**
   *
   * Gives the qualified Name till the given field name.
   *
   * @param yamlNode
   * @param fieldName - the field till which we want the qualified name
   * @return
   */
  public String getQualifiedNameTillGivenField(YamlNode yamlNode, String fieldName) {
    return String.join(".", getQualifiedNameList(yamlNode, fieldName));
  }

  /**
   * Gets qualified Name between from and to. Starting from the given yamlNode
   * @param yamlNode
   * @param from
   * @param to
   * @return
   */
  public String getQNBetweenTwoFields(YamlNode yamlNode, String from, String to) {
    List<String> qualifiedNames = getQualifiedNameList(yamlNode, "pipeline");
    StringBuilder response = new StringBuilder();
    for (String qualifiedName : qualifiedNames) {
      if (qualifiedName.equals(from)) {
        response.append(qualifiedName).append('.');
      }
      if (qualifiedName.equals(to)) {
        response.append(qualifiedName);
        break;
      }
    }
    return response.toString();
  }

  private List<String> getQualifiedNameList(YamlNode yamlNode, String fieldName) {
    if (yamlNode.getParentNode() == null) {
      List<String> qualifiedNameList = new ArrayList<>();
      String qnForNode = getQNForNode(yamlNode, null);
      if (EmptyPredicate.isNotEmpty(qnForNode)) {
        qualifiedNameList.add(qnForNode);
      }
      return qualifiedNameList;
    }
    String qualifiedName = getQNForNode(yamlNode, yamlNode.getParentNode());
    if (isEmpty(qualifiedName)) {
      return getQualifiedNameList(yamlNode.getParentNode(), fieldName);
    }
    if (qualifiedName.equals(fieldName)) {
      List<String> qualifiedNameList = new ArrayList<>();
      qualifiedNameList.add(qualifiedName);
      return qualifiedNameList;
    }
    if (yamlNode.getUuid() != null
        && fieldName.equals(
            getMatchingFieldNameFromParentUsingUUID(yamlNode.getParentNode(), yamlNode.getUuid()).getName())) {
      List<String> qualifiedNameList = new ArrayList<>();
      qualifiedNameList.add(qualifiedName);
      return qualifiedNameList;
    }
    List<String> qualifiedNameList = getQualifiedNameList(yamlNode.getParentNode(), fieldName);
    qualifiedNameList.add(qualifiedName);
    return qualifiedNameList;
  }

  public String getStageFqnPath(YamlNode yamlNode) {
    List<String> qualifiedNames = getQualifiedNameList(yamlNode, "pipeline");
    if (qualifiedNames.size() <= 2) {
      return String.join(".", qualifiedNames);
    }

    return qualifiedNames.get(0) + "." + qualifiedNames.get(1) + "." + qualifiedNames.get(2);
  }

  private String getQNForNode(YamlNode yamlNode, YamlNode parentNode) {
    if (parentNode == null) {
      return "";
    }
    if (parentNode.getParentNode() != null && parentNode.getParentNode().isArray()) {
      if (yamlNode.getIdentifier() != null) {
        return yamlNode.getIdentifier();
      } else if (parentNode.getName() != null) {
        return parentNode.getName();
      } else if (parentNode.getKey() != null) {
        return parentNode.getKey();
      } else {
        return "";
      }
    }
    YamlField field;
    // Because UUID won't be there in leaf objects
    if (yamlNode.getUuid() != null) {
      field = getMatchingFieldNameFromParentUsingUUID(parentNode, yamlNode.getUuid());
    } else {
      field = getMatchingFieldNameFromParentUsingValueAsText(parentNode, yamlNode.getCurrJsonNode().asText());
    }

    if (field == null || shouldNotIncludeInQualifiedName(field.getName())) {
      return "";
    }

    return field.getName();
  }

  public boolean shouldNotIncludeInQualifiedName(String fieldName) {
    return ignorableStringForQualifiedName.contains(fieldName);
  }

  private void injectUuidInObject(JsonNode node) {
    ObjectNode objectNode = (ObjectNode) node;
    objectNode.put(YamlNode.UUID_FIELD_NAME, generateUuid());
    for (Iterator<Entry<String, JsonNode>> it = objectNode.fields(); it.hasNext();) {
      Entry<String, JsonNode> field = it.next();
      injectUuid(field.getValue());
    }
  }

  public YamlNode getGivenYamlNodeFromParentPath(YamlNode currentNode, String fieldName) {
    if (currentNode == null) {
      return null;
    }
    YamlNode requiredNode;
    if (currentNode.getParentNode() == null) {
      return null;
    }
    if (currentNode.getParentNode().isArray()) {
      requiredNode = checkNodeIfParentIsArray(currentNode.getParentNode(), fieldName);
    } else {
      requiredNode = checkNodeIfParentIsObject(currentNode.getParentNode(), fieldName);
    }
    if (requiredNode == null) {
      return getGivenYamlNodeFromParentPath(currentNode.getParentNode(), fieldName);
    }
    return requiredNode;
  }

  public YamlNode findParentNode(YamlNode currentNode, String parentName) {
    if (isEmpty(currentNode.getUuid())) {
      return null;
    }
    YamlNode parentNode = getGivenYamlNodeFromParentPath(currentNode, parentName);
    if (parentNode == null) {
      return null;
    }
    if (currentNode.getCurrJsonNode().isArray()) {
      if (!parentNode.toString().contains(currentNode.toString())) {
        return null;
      }
    } else {
      if (!parentNode.toString().contains(currentNode.getUuid())) {
        return null;
      }
    }

    return parentNode;
  }

  private YamlNode checkNodeIfParentIsObject(YamlNode parentNode, String fieldName) {
    List<YamlField> fields = parentNode.fields();
    for (YamlField currentField : fields) {
      if (currentField.getName().equals(fieldName)) {
        return currentField.getNode();
      }
    }
    return null;
  }

  private YamlNode checkNodeIfParentIsArray(YamlNode parentNode, String fieldName) {
    List<YamlNode> yamlNodes = parentNode.getParentNode().asArray();
    for (YamlNode yamlNode : yamlNodes) {
      List<YamlField> currentNodeFields = yamlNode.fields();
      for (YamlField currentNodeField : currentNodeFields) {
        if (currentNodeField.getName().equals(fieldName)) {
          return currentNodeField.getNode();
        }
      }
    }
    return null;
  }

  private void injectUuidInArray(JsonNode node) {
    ArrayNode arrayNode = (ArrayNode) node;
    for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext();) {
      injectUuid(it.next());
    }
  }

  private YamlField getMatchingFieldNameFromParentUsingUUID(YamlNode parent, String uuid) {
    for (YamlField field : parent.fields()) {
      if (parent.getField(field.getName()) == null) {
        continue;
      }
      if (uuid.equals(parent.getField(field.getName()).getNode().getUuid())) {
        return field;
      }
    }
    return null;
  }

  public YamlField getMatchingFieldNameFromParentUsingValueAsText(YamlNode parent, String value) {
    for (YamlField field : parent.fields()) {
      if (parent.getField(field.getName()) == null) {
        continue;
      }
      if (value.equals(parent.getField(field.getName()).getNode().getCurrJsonNode().asText())) {
        return field;
      }
    }
    return null;
  }

  public String writeYamlString(YamlField yamlField) throws IOException {
    JsonNode jsonNode = yamlField.getNode().getCurrJsonNode();
    return mapper.writeValueAsString(jsonNode);
  }

  public String getStageIdentifierFromFqn(String fqn) {
    String[] strings = fqn.split("\\.");
    if (strings.length <= 2) {
      return null;
    }

    if (strings[1].equals("stages")) {
      return strings[2];
    }
    return null;
  }

  /**
   * returns only the variable at pipeline level from the fqn
   */
  public String getPipelineVariableNameFromFqn(String fqn) {
    String[] strings = fqn.split("\\.");
    if (strings.length <= 2) {
      return null;
    }
    if (strings[1].equals("variables")) {
      return strings[2];
    }
    return null;
  }

  /**
   * returns only the pipeline Identifier at pipeline level from the fqn
   */
  public String getPipelineTemplateNameFromFqn(String fqn) {
    // TODO : Add Proper Implementation
    String[] strings = fqn.split("\\.");
    if (strings.length < 2) {
      return null;
    }
    if (strings[1].equals("template")) {
      return "pipelineIdentifier";
    }
    return null;
  }

  private String getErrorNodePartialFQN(String startingFQN, IOException e) {
    if (!(e.getClass().isAssignableFrom(JsonMappingException.class))) {
      return startingFQN;
    }

    JsonMappingException ex = (JsonMappingException) e;
    List<JsonMappingException.Reference> path = ex.getPath();
    StringBuilder partialFQN = new StringBuilder(startingFQN);
    for (JsonMappingException.Reference pathNode : path) {
      if (pathNode.getFieldName() == null) {
        break;
      }
      partialFQN.append('.').append(pathNode.getFieldName());
    }
    return partialFQN.toString();
  }

  public String getErrorNodePartialFQN(YamlNode yamlNode, IOException e) {
    String startingFQN = getFullyQualifiedName(yamlNode);
    return getErrorNodePartialFQN(startingFQN, e);
  }

  public String getErrorNodePartialFQN(IOException e) {
    return getErrorNodePartialFQN("", e);
  }

  public void removeUuid(JsonNode node) {
    if (node.isObject()) {
      removeUuidInObject(node);
    } else if (node.isArray()) {
      removeUuidInArray(node);
    }
  }

  private void removeUuidInObject(JsonNode node) {
    ObjectNode objectNode = (ObjectNode) node;
    for (Iterator<Entry<String, JsonNode>> it = objectNode.fields(); it.hasNext();) {
      Entry<String, JsonNode> field = it.next();
      if (field.getKey().equals(YamlNode.UUID_FIELD_NAME)) {
        objectNode.remove(field.getKey());
      } else {
        removeUuid(field.getValue());
      }
    }
  }

  private void removeUuidInArray(JsonNode node) {
    ArrayNode arrayNode = (ArrayNode) node;
    for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext();) {
      removeUuid(it.next());
    }
  }

  public void setStringValueForField(String fieldName, String value, YamlField yamlField) {
    YamlNode yamlNode = yamlField.getNode();
    ObjectNode currJsonNode = (ObjectNode) yamlNode.getCurrJsonNode();
    currJsonNode.set(fieldName, new TextNode(value));
  }
}
