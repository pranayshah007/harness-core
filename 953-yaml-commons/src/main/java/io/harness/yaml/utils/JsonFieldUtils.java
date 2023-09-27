/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.utils;

import io.harness.pms.yaml.YamlSchemaFieldConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import java.util.Iterator;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JsonFieldUtils {
  private final String EMPTY_STRING = "";

  public boolean isPresent(JsonNode jsonNode, YamlSchemaFieldConstants field) {
    return get(jsonNode, field) != null;
  }

  public boolean isPresent(JsonNode jsonNode, String field) {
    return get(jsonNode, field) != null;
  }

  public String getText(JsonNode jsonNode, YamlSchemaFieldConstants field) {
    return getText(jsonNode, field.name);
  }

  public String getText(JsonNode jsonNode, String field) {
    return get(jsonNode, field).asText();
  }

  public String getTextOrEmpty(JsonNode jsonNode, YamlSchemaFieldConstants field) {
    if (isPresent(jsonNode, field)) {
      return getText(jsonNode, field);
    }
    return EMPTY_STRING;
  }

  public ArrayNode getArrayNode(JsonNode jsonNode, YamlSchemaFieldConstants field) {
    return (ArrayNode) get(jsonNode, field);
  }

  public JsonNode get(JsonNode jsonNode, YamlSchemaFieldConstants field) {
    return get(jsonNode, field.name);
  }

  public JsonNode get(JsonNode jsonNode, String field) {
    return jsonNode.get(field);
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

  public boolean isArrayNode(JsonNode jsonNode) {
    if (jsonNode == null) {
      return false;
    }
    return jsonNode.isArray();
  }

  public JsonNode getFieldNode(JsonNode jsonNode, String targetFieldName) {
    if (jsonNode == null) {
      return null;
    }

    if (isArrayNode(jsonNode)) {
      for (int i = 0; i < jsonNode.size(); i++) {
        JsonNode resultNode = getFieldNode(jsonNode.get(i), targetFieldName);
        if (resultNode != null) {
          return resultNode;
        }
      }
    }

    Iterator<String> fieldNames = jsonNode.fieldNames();
    for (Iterator<String> it = fieldNames; it.hasNext();) {
      String fieldName = it.next();
      if (fieldName.equals(targetFieldName)) {
        return jsonNode.get(fieldName);
      }
      if (JsonPipelineUtils.isObjectTypeField(jsonNode, fieldName)) {
        JsonNode resultNode = getFieldNode(jsonNode.get(fieldName), targetFieldName);
        if (resultNode != null) {
          return resultNode;
        }
      }
      if (JsonPipelineUtils.isArrayNodeField(jsonNode, fieldName)) {
        ArrayNode elements = (ArrayNode) jsonNode.get(fieldName);
        for (int i = 0; i < elements.size(); i++) {
          JsonNode resultNode = getFieldNode(elements.get(i), targetFieldName);
          if (resultNode != null) {
            return resultNode;
          }
        }
      }
    }
    return null;
  }

  private boolean checkNodeType(JsonNode jsonNode, String field, JsonNodeType jsonNodeType) {
    if (isPresent(jsonNode, field)) {
      return jsonNode.get(field).getNodeType() == jsonNodeType;
    }
    return false;
  }
}
