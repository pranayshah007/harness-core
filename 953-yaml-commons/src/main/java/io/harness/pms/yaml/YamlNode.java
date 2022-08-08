/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.YamlException;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Value;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
public class YamlNode implements Visitable {
  public static final String UUID_FIELD_NAME = "__uuid";
  public static final String IDENTIFIER_FIELD_NAME = "identifier";
  public static final String TYPE_FIELD_NAME = "type";
  public static final String NAME_FIELD_NAME = "name";
  public static final String KEY_FIELD_NAME = "key";
  public static final String TEMPLATE_FIELD_NAME = "template";

  public static final String PATH_SEP = "/";

  String fieldName;
  YamlNode parentNode;
  @NotNull JsonNode currJsonNode;

  public YamlNode(JsonNode currJsonNode) {
    this(null, currJsonNode, null);
  }

  public YamlNode(String name, JsonNode currJsonNode) {
    this(name, currJsonNode, null);
  }

  @JsonCreator
  public YamlNode(@JsonProperty("fieldName") String fieldName, @JsonProperty("currJsonNode") JsonNode currJsonNode,
      @JsonProperty("parentNode") YamlNode parentNode) {
    this.fieldName = fieldName;
    this.currJsonNode = currJsonNode;
    this.parentNode = parentNode;
  }

  public static YamlNode fromYamlPath(String yaml, String path) throws IOException {
    if (EmptyPredicate.isEmpty(yaml)) {
      return null;
    }

    YamlField field = YamlUtils.readTree(yaml);
    return field.getNode().gotoPath(path);
  }

  public static YamlNode fromYamlPath(YamlField field, String path) throws IOException {
    return field.getNode().gotoPath(path);
  }

  @Override
  public String toString() {
    return currJsonNode.toString();
  }

  public String getYamlPath() {
    List<String> path = new ArrayList<>();
    YamlNode curr = this;
    while (curr != null && curr.getParentNode() != null) {
      path.add(curr.getFieldName());
      curr = curr.parentNode;
    }
    Collections.reverse(path);
    return String.join(PATH_SEP, path);
  }

  public String extractStageLocalYamlPath() {
    String fullYamlPath = getYamlPath();
    List<String> localFQNSplit;
    List<String> split = Arrays.stream(fullYamlPath.split(PATH_SEP)).collect(Collectors.toList());
    if (split.size() < 4) {
      throw new InvalidRequestException("Yaml node is not a node inside a stage.");
    }
    String stageOrParallel = split.get(3);
    if (stageOrParallel.equals(YAMLFieldNameConstants.PARALLEL)) {
      if (split.size() < 6 || !split.get(5).equals(YAMLFieldNameConstants.STAGE)) {
        throw new InvalidRequestException("Yaml node is not a node inside a parallel stage.");
      }
      localFQNSplit = split.subList(5, split.size());
    } else {
      localFQNSplit = split.subList(3, split.size());
    }

    return String.join(PATH_SEP, localFQNSplit);
  }

  public static String getLastKeyInPath(String path) {
    if (EmptyPredicate.isEmpty(path)) {
      throw new InvalidRequestException("Path cannot be empty");
    }
    String[] keys = path.split(PATH_SEP);
    return keys[keys.length - 1];
  }

  public YamlNode gotoPath(String path) {
    if (EmptyPredicate.isEmpty(path)) {
      return this;
    }

    List<String> pathList = Arrays.asList(path.split(PATH_SEP));
    if (EmptyPredicate.isEmpty(pathList)) {
      return this;
    }

    YamlNode curr = this;
    for (String currName : pathList) {
      if (curr == null) {
        return null;
      }

      JsonNode next;
      if (currName.charAt(0) == '[') {
        if (!curr.isArray()) {
          throw new YamlException(String.format("Trying to use index path (%s) on non-array node", currName));
        }

        int idx;
        try {
          idx = Integer.parseInt(currName.substring(1, currName.length() - 1));
        } catch (Exception ex) {
          throw new YamlException(String.format("Incorrect index path (%s) on array node", currName));
        }

        next = curr.getCurrJsonNode().get(idx);
      } else {
        next = curr.getValueInternal(currName);
      }

      curr = next == null ? null : new YamlNode(currName, next, curr);
    }

    return curr;
  }

  public void replacePath(String path, JsonNode newNode) {
    if (EmptyPredicate.isEmpty(path)) {
      return;
    }

    List<String> pathList = Arrays.asList(path.split(PATH_SEP));
    if (EmptyPredicate.isEmpty(pathList)) {
      return;
    }

    JsonNode curr = this.currJsonNode;
    for (int i = 0; i < pathList.size() - 1; i++) {
      String currName = pathList.get(i);
      if (curr == null) {
        return;
      }

      if (currName.charAt(0) == '[') {
        if (!curr.isArray()) {
          throw new YamlException(String.format("Trying to use index path (%s) on non-array node", currName));
        }
        try {
          int idx = Integer.parseInt(currName.substring(1, currName.length() - 1));
          curr = curr.get(idx);
        } catch (Exception ex) {
          throw new YamlException(String.format("Incorrect index path (%s) on array node", currName));
        }
      } else {
        curr = curr.get(currName);
      }
    }
    String lastName = pathList.get(pathList.size() - 1);
    if (lastName.charAt(0) == '[') {
      if (!curr.isArray()) {
        throw new YamlException(String.format("Trying to use index path (%s) on non-array node", lastName));
      }
      try {
        int idx = Integer.parseInt(lastName.substring(1, lastName.length() - 1));
        ArrayNode arrayNode = (ArrayNode) curr;
        arrayNode.set(idx, newNode);
      } catch (Exception ex) {
        throw new YamlException(String.format("Incorrect index path (%s) on array node", lastName));
      }
    } else {
      ObjectNode objectNode = (ObjectNode) curr;
      objectNode.set(lastName, newNode);
    }
  }

  // todo(@NamanVerma): write test
  public void removePath(String path) {
    if (EmptyPredicate.isEmpty(path)) {
      return;
    }

    List<String> pathList = Arrays.asList(path.split(PATH_SEP));
    if (EmptyPredicate.isEmpty(pathList)) {
      return;
    }

    JsonNode curr = this.currJsonNode;
    for (int i = 0; i < pathList.size() - 1; i++) {
      String currName = pathList.get(i);
      if (curr == null) {
        return;
      }

      if (currName.charAt(0) == '[') {
        if (!curr.isArray()) {
          throw new YamlException(String.format("Trying to use index path (%s) on non-array node", currName));
        }
        try {
          int idx = Integer.parseInt(currName.substring(1, currName.length() - 1));
          curr = curr.get(idx);
        } catch (Exception ex) {
          throw new YamlException(String.format("Incorrect index path (%s) on array node", currName));
        }
      } else {
        curr = curr.get(currName);
      }
    }
    String lastName = pathList.get(pathList.size() - 1);
    if (lastName.charAt(0) == '[') {
      if (!curr.isArray()) {
        throw new YamlException(String.format("Trying to use index path (%s) on non-array node", lastName));
      }
      try {
        int idx = Integer.parseInt(lastName.substring(1, lastName.length() - 1));
        ArrayNode arrayNode = (ArrayNode) curr;
        arrayNode.remove(idx);
      } catch (Exception ex) {
        throw new YamlException(String.format("Incorrect index path (%s) on array node", lastName));
      }
    } else {
      ObjectNode objectNode = (ObjectNode) curr;
      objectNode.remove(lastName);
    }
  }

  public boolean isObject() {
    return currJsonNode.isObject();
  }

  public boolean isArray() {
    return currJsonNode.isArray();
  }

  public String asText() {
    return currJsonNode.asText();
  }

  /**
   * Returns the sibling with the types mentioned in possibleSiblingFieldNames.
   * For example: if we have are on one stage and we want the next stage in the yaml, we can use this function
   */
  public YamlField nextSiblingFromParentArray(String currentFieldName, List<String> possibleSiblingFieldNames) {
    if (parentNode == null || parentNode.parentNode == null || parentNode.parentNode.isObject()) {
      return null;
    }
    List<YamlNode> yamlNodes = parentNode.parentNode.asArray();
    for (int i = 0; i < yamlNodes.size() - 1; i++) {
      YamlField givenNode = yamlNodes.get(i).getField(currentFieldName);
      if (givenNode != null && givenNode.getNode().getUuid() != null
          && givenNode.getNode().getUuid().equals(this.getUuid())) {
        return getMatchingFieldNameFromParent(yamlNodes.get(i + 1), new HashSet<>(possibleSiblingFieldNames));
      }
    }
    return null;
  }

  public YamlField nextSiblingNodeFromParentObject(String siblingFieldName) {
    if (parentNode == null || parentNode.isArray()) {
      return null;
    }
    return getMatchingFieldNameFromParent(parentNode, Collections.singleton(siblingFieldName));
  }

  private YamlField getMatchingFieldNameFromParent(YamlNode parent, Set<String> fieldNames) {
    for (YamlField field : parent.fields()) {
      if (fieldNames.contains(field.getName()) && !field.getNode().getCurrJsonNode().isNull()) {
        return field;
      }
    }
    return null;
  }

  public List<YamlNode> asArray() {
    List<YamlNode> entries = new ArrayList<>();
    int idx = 0;
    for (Iterator<JsonNode> i = currJsonNode.elements(); i.hasNext();) {
      JsonNode node = i.next();
      entries.add(new YamlNode(String.format("[%d]", idx), node, this));
      idx++;
    }
    return entries;
  }

  public List<YamlField> fields() {
    List<YamlField> entries = new ArrayList<>();
    currJsonNode.fields().forEachRemaining(
        el -> entries.add(new YamlField(new YamlNode(el.getKey(), el.getValue(), this))));
    return entries;
  }

  public String getUuid() {
    String uuidValue = getStringValue(UUID_FIELD_NAME);
    // This means that current node is of array type
    if (uuidValue == null && parentNode != null && parentNode.isObject()) {
      List<YamlField> childFields = parentNode.fields();
      for (YamlField childField : childFields) {
        if (compareFirstChildOfArrayNode(childField.getNode(), this)) {
          return parentNode.getUuid() + childField.getName();
        }
      }
      return null;
    }
    return uuidValue;
  }

  private boolean compareFirstChildOfArrayNode(YamlNode firstParent, YamlNode secondParent) {
    List<YamlNode> firstParentChildNodes = firstParent.asArray();
    List<YamlNode> secondParentChildNodes = secondParent.asArray();
    if (firstParentChildNodes.isEmpty() || secondParentChildNodes.isEmpty()
        || firstParentChildNodes.get(0).getUuid() == null || secondParentChildNodes.get(0).getUuid() == null) {
      return false;
    }
    return Objects.equals(firstParentChildNodes.get(0).getUuid(), secondParentChildNodes.get(0).getUuid());
  }

  public String getIdentifier() {
    return getStringValue(IDENTIFIER_FIELD_NAME);
  }

  public String getType() {
    return getStringValue(TYPE_FIELD_NAME);
  }

  public String getName() {
    return getStringValue(NAME_FIELD_NAME);
  }

  public String getKey() {
    return getStringValue(KEY_FIELD_NAME);
  }

  public JsonNode getTemplate() {
    return getValueInternal(TEMPLATE_FIELD_NAME);
  }

  public String getNameOrIdentifier() {
    return Optional.ofNullable(getName()).orElse(getIdentifier());
  }

  public String getArrayUniqueIdentifier() {
    return Optional.ofNullable(getIdentifier()).orElse(Optional.ofNullable(getName()).orElse(getKey()));
  }

  public String getStringValue(String name) {
    JsonNode value = getValueInternal(name);
    return (value == null || !value.isTextual()) ? null : value.asText();
  }

  public YamlField getField(String name) {
    JsonNode valueFromField = getValueInternal(name);
    if (valueFromField != null) {
      return new YamlField(new YamlNode(name, valueFromField, this));
    }
    return null;
  }

  public YamlField getFieldOrThrow(String name) {
    JsonNode valueFromField = getValueInternal(name);
    if (valueFromField != null) {
      return new YamlField(new YamlNode(name, valueFromField, this));
    }
    throw new InvalidRequestException("Field for key [" + name + "] does not exist");
  }

  private JsonNode getValueInternal(String key) {
    JsonNode value = !currJsonNode.isObject() ? null : currJsonNode.get(key);
    if (value != null && value.isNull()) {
      value = null;
    }
    return value;
  }

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren visitableChildren = VisitableChildren.builder().build();
    if (isArray()) {
      for (YamlNode node : asArray()) {
        visitableChildren.add(UUIDGenerator.generateUuid(), node);
      }
    } else if (isObject()) {
      Map<String, YamlNode> yamlNodeFields =
          fields().stream().collect(Collectors.toMap(YamlField::getName, YamlField::getNode));
      for (Map.Entry<String, YamlNode> yamlNodeEntry : yamlNodeFields.entrySet()) {
        visitableChildren.add(yamlNodeEntry.getKey(), yamlNodeEntry.getValue());
      }
    }
    return visitableChildren;
  }

  public List<String> fetchKeys() {
    List<String> keys = new ArrayList<>();
    Iterator<String> keysIterator = currJsonNode.fieldNames();
    keysIterator.forEachRemaining(keys::add);
    return keys;
  }
}
