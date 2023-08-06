/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JsonFieldUtil {
  private final String EMPTY_STRING = "";

  public boolean isPresent(JsonNode jsonNode, YamlFieldConstants field) {
    return get(jsonNode, field) != null;
  }

  public boolean isPresent(JsonNode jsonNode, String field) {
    return get(jsonNode, field) != null;
  }

  public String getText(JsonNode jsonNode, YamlFieldConstants field) {
    return get(jsonNode, field).asText();
  }

  public String getTextOrEmpty(JsonNode jsonNode, YamlFieldConstants field) {
    if (isPresent(jsonNode, field)) {
      return getText(jsonNode, field);
    }
    return EMPTY_STRING;
  }

  public ArrayNode getArrayNode(JsonNode jsonNode, YamlFieldConstants field) {
    return (ArrayNode) get(jsonNode, field);
  }

  public JsonNode get(JsonNode jsonNode, YamlFieldConstants field) {
    return get(jsonNode, field.name);
  }

  public JsonNode get(JsonNode jsonNode, String field) {
    return jsonNode.get(field);
  }

  public boolean isStringTypeField(JsonNode jsonNode, YamlFieldConstants field) {
    return isStringTypeField(jsonNode, field.name);
  }

  public boolean isStringTypeField(JsonNode jsonNode, String field) {
    return checkNodeType(jsonNode, field, JsonNodeType.STRING);
  }

  public boolean isObjectTypeField(JsonNode jsonNode, String field) {
    return checkNodeType(jsonNode, field, JsonNodeType.OBJECT);
  }

  public boolean isArrayNodeField(JsonNode jsonNode, String fieldName) {
    return checkNodeType(jsonNode, fieldName, JsonNodeType.ARRAY);
  }

  private boolean checkNodeType(JsonNode jsonNode, String field, JsonNodeType jsonNodeType) {
    if (isPresent(jsonNode, field)) {
      return jsonNode.get(field).getNodeType() == jsonNodeType;
    }
    return false;
  }
}
