/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class StaticSchemaParser {
  Map<String, ObjectNode> stageToNodeMap = new HashMap<>();
  Map<String, ObjectNode> stepToNodeMap = new HashMap<>();
  Map<String, ObjectNode> fqnToNodeMap = new HashMap<>();
  Map<String, ObjectNode> pipelineToNodeMap = new HashMap<>(); // It contains single entry for empty string as key

  private static final String PROPERTIES_KEY = "properties";
  private static final String REF_KEY = "$ref";
  private static final String TYPE_KEY = "type";
  private static final String ENUM_KEY = "enum";

  @SneakyThrows
  public StaticSchemaParser() {
    ClassLoader classLoader = this.getClass().getClassLoader();
    String resolvedPipelineYamlFilename = "pipeline.json";
    String pipelineJson = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(resolvedPipelineYamlFilename)), StandardCharsets.UTF_8);
    parseAndIngestSchema(pipelineJson);
  }

  public void parseAndIngestSchema(String pipelineJson) {
    JsonNode jsonNode = JsonUtils.readTree(pipelineJson);
    parseAndIngestSchema(jsonNode, "#");

    // compute nodes for each step and stage
    fqnToNodeMap.forEach((fqn, node) -> {
      if (fqn.equals("#/definitions/pipeline/pipeline")) {
        pipelineToNodeMap.put("", node);
      } else if (fqn.startsWith("#/definitions/pipeline/stages")) {
        List<String> possibleTypeValues = getPossibleTypeValues(node);
        possibleTypeValues.forEach(type -> stageToNodeMap.put(type, node));
        // version - entity (pipeline/template) - module - stage/step/pipeline - type
      } else {
        if (fqn.startsWith("#/definitions/pipeline/steps")) {
          List<String> possibleTypeValues = getPossibleTypeValues(node);
          possibleTypeValues.forEach(type -> stepToNodeMap.put(type, node));
        }
      }
    });
  }

  public JsonNode getFieldNode(InputFieldMetadata inputFieldMetadata) {
    String[] fqnParts = inputFieldMetadata.fqnFromParentNode.split("\\.");
    Map<String, ObjectNode> nodeMap = getApplicableNodeMap(fqnParts[0]);
    JsonNode targetFieldNode = nodeMap.get(inputFieldMetadata.parentNodeType);
    for (int i = 1; i < fqnParts.length; i++) {
      targetFieldNode = getFieldNode(targetFieldNode, fqnParts[i]);
      if (targetFieldNode.has(REF_KEY)) {
        String ref = targetFieldNode.get(REF_KEY).asText();
        targetFieldNode = fqnToNodeMap.get(ref);
      }
    }
    return targetFieldNode;
  }

  private void parseAndIngestSchema(JsonNode jsonNode, String currentFqn) {
    Iterator<String> fieldNames = jsonNode.fieldNames();
    fieldNames.forEachRemaining(fieldName -> {
      if (fieldName.equals(PROPERTIES_KEY)) {
        // skipping properties field as it seems redundant to capture it for now
        return;
      }
      if (JsonFieldUtil.isObjectTypeField(jsonNode, fieldName)) {
        String newFqn = currentFqn + "/" + fieldName;
        ObjectNode nextObjectNode = (ObjectNode) jsonNode.get(fieldName);
        fqnToNodeMap.put(newFqn, nextObjectNode);
        parseAndIngestSchema(nextObjectNode, newFqn);
      }
    });
  }

  // For given jsonNode, return possible enum values for "type" field if applicable
  private List<String> getPossibleTypeValues(JsonNode jsonNode) {
    Iterator<String> fieldNames = jsonNode.fieldNames();
    List<String> resultantTypes = new ArrayList<>();
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      if (fieldName.equals(PROPERTIES_KEY)) {
        return getPossibleTypeValues(jsonNode.get(PROPERTIES_KEY));
      }
      if (fieldName.equals(TYPE_KEY)) {
        ArrayNode typeValues = (ArrayNode) jsonNode.get(TYPE_KEY).get(ENUM_KEY);
        if (typeValues == null) {
          continue;
        }
        for (int i = 0; i < typeValues.size(); i++) {
          resultantTypes.add(typeValues.get(i).asText());
        }
      }
    }
    return resultantTypes;
  }

  private JsonNode getFieldNode(JsonNode jsonNode, String targetFieldName) {
    if (jsonNode == null) {
      return null;
    }
    Iterator<String> fieldNames = jsonNode.fieldNames();
    for (Iterator<String> it = fieldNames; it.hasNext();) {
      String fieldName = it.next();
      if (fieldName.equals(targetFieldName)) {
        return jsonNode.get(fieldName);
      }
      if (JsonFieldUtil.isObjectTypeField(jsonNode, fieldName)) {
        JsonNode resultNode = getFieldNode(jsonNode.get(fieldName), targetFieldName);
        if (resultNode != null) {
          return resultNode;
        }
      }
      if (JsonFieldUtil.isArrayNodeField(jsonNode, fieldName)) {
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

  private Map<String, ObjectNode> getApplicableNodeMap(String parentNode) {
    switch (parentNode) {
      case "pipeline":
        return pipelineToNodeMap;
      case "stage":
        return stageToNodeMap;
      default:
        return stepToNodeMap;
    }
  }
}
