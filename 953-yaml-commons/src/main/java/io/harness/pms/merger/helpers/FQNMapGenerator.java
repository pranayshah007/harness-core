/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YamlNode.UUID_FIELD_NAME;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class FQNMapGenerator {
  public Map<FQN, Object> generateFQNMap(JsonNode yamlMap) {
    return generateFQNMap(yamlMap, false);
  }

  public Map<String, Object> generateYamlMapWithFqnExpression(String yaml) {
    HashMap<String, Object> mapData = new HashMap<>();
    Map<FQN, Object> fqnObjectMap = FQNMapGenerator.generateFQNMap(YamlUtils.readAsJsonNode(yaml));

    for (Map.Entry<FQN, Object> entry : fqnObjectMap.entrySet()) {
      FQN key = entry.getKey();
      String value = HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone(entry.getValue().toString());
      traverseMap(mapData, Arrays.asList(key.getExpressionFqn().split("\\.")), 0, value);
    }
    return mapData;
  }

  private static void traverseMap(Map<String, Object> mapData, List<String> fqnList, int index, String value) {
    if (index == fqnList.size() - 1) {
      mapData.put(fqnList.get(index), value);
      return;
    }
    if (!mapData.containsKey(fqnList.get(index))) {
      mapData.put(fqnList.get(index), new HashMap<>());
    }
    if (mapData.get(fqnList.get(index)) instanceof Map) {
      traverseMap((Map<String, Object>) mapData.get(fqnList.get(index)), fqnList, index + 1, value);
    } else {
      log.warn("Value {} not instance of map ", mapData.get(fqnList.get(index)));
    }
  }

  public Map<FQN, Object> generateFQNMap(JsonNode yamlMap, boolean keepUuidFields) {
    HashSet<String> expressions = new HashSet<>();
    Set<String> fieldNames = new LinkedHashSet<>();
    yamlMap.fieldNames().forEachRemaining(fieldNames::add);
    String topKey = "";
    Map<FQN, Object> res = new LinkedHashMap<>();
    // Generate fqn for each fieldName
    for (String fieldName : fieldNames) {
      topKey = fieldName;
      if (keepUuidFields && topKey.equals(UUID_FIELD_NAME)) {
        continue;
      }
      FQNNode startNode = FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(topKey).build();
      FQN currentFQN = FQN.builder().fqnList(Collections.singletonList(startNode)).build();

      JsonNode currentJsonNode = yamlMap.get(topKey);
      if (!currentJsonNode.isObject() && !currentJsonNode.isArray()) {
        FQNHelper.validateUniqueFqn(currentFQN, currentJsonNode, res, expressions);
      } else {
        generateFQNMap(currentJsonNode, currentFQN, res, expressions, keepUuidFields);
      }
    }
    return res;
  }

  public void generateFQNMap(
      JsonNode map, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions, boolean keepUuidFields) {
    Set<String> fieldNames = new LinkedHashSet<>();
    map.fieldNames().forEachRemaining(fieldNames::add);
    for (String key : fieldNames) {
      JsonNode value = map.get(key);
      FQN currFQN = FQN.duplicateAndAddNode(baseFQN, FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(key).build());
      if (value.getNodeType() == JsonNodeType.ARRAY) {
        if (value.size() == 0) {
          FQNHelper.validateUniqueFqn(currFQN, value, res, expressions);
          continue;
        }
        ArrayNode arrayNode = (ArrayNode) value;
        generateFQNMapFromList(arrayNode, currFQN, res, expressions, keepUuidFields);
      } else if (value.getNodeType() == JsonNodeType.OBJECT) {
        if (value.size() == 0) {
          FQNHelper.validateUniqueFqn(currFQN, value, res, expressions);
          continue;
        }
        generateFQNMap(value, currFQN, res, expressions, keepUuidFields);
      } else {
        FQNHelper.validateUniqueFqn(currFQN, value, res, expressions);
      }
    }
  }

  public void generateFQNMapFromList(
      ArrayNode list, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions, boolean keepUuidFields) {
    if (list == null || list.get(0) == null) {
      FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
      return;
    }
    JsonNode firstNode = list.get(0);
    if (firstNode.getNodeType() != JsonNodeType.OBJECT) {
      FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
      return;
    }
    generateFQNMapFromListInternal(list, baseFQN, res, expressions, keepUuidFields);
  }

  public void generateFQNMapFromListInternal(
      ArrayNode list, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions, boolean keepUuidFields) {
    for (JsonNode element : list) {
      String wrapperKey = FQNHelper.getWrapperKeyForArrayElement(element);
      // If wrapperKey is present and element does not have any uuid field then the element will be treated as a wrapper
      // object.
      if (EmptyPredicate.isNotEmpty(wrapperKey) && EmptyPredicate.isEmpty(FQNHelper.getUuidKey(element))) {
        String identifierKey = FQNHelper.getIdentifierKeyIfPresent(element);
        if (EmptyPredicate.isEmpty(identifierKey)) {
          FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
          return;
        }
        handleSingleKeysArrayElement(element, baseFQN, res, expressions, keepUuidFields);
      } else {
        String uuidKey = FQNHelper.getUuidKey(element);
        if (EmptyPredicate.isEmpty(uuidKey)) {
          FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
          return;
        }
        handleMultipleKeysArrayElement(element, baseFQN, res, expressions, keepUuidFields);
      }
    }
  }
  private void handleSingleKeysArrayElement(
      JsonNode element, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions, boolean keepUuidFields) {
    String identifierKey = FQNHelper.getIdentifierKeyIfPresent(element);
    if (element.has(YAMLFieldNameConstants.PARALLEL)) {
      FQN currFQN = FQN.duplicateAndAddNode(baseFQN, FQNNode.builder().nodeType(FQNNode.NodeType.PARALLEL).build());
      ArrayNode listOfMaps = (ArrayNode) element.get(YAMLFieldNameConstants.PARALLEL);
      generateFQNMapFromList(listOfMaps, currFQN, res, expressions, keepUuidFields);
    } else {
      // topKey will always be a wrapperKey because we have already checked before coming into this flow.
      String topKey = FQNHelper.getWrapperKeyForArrayElement(element);
      if (!topKey.equals(UUID_FIELD_NAME)) {
        JsonNode innerMap = element.get(topKey);
        String identifierValue = innerMap.get(identifierKey).asText();

        FQN currFQN = FQN.duplicateAndAddNode(baseFQN,
            FQNNode.builder()
                .nodeType(FQNNode.NodeType.KEY_WITH_UUID)
                .key(topKey)
                .uuidKey(identifierKey)
                .uuidValue(identifierValue)
                .build());
        generateFQNMap(innerMap, currFQN, res, expressions, keepUuidFields);
      } else {
        log.warn("element {} only contains the field {}", element, UUID_FIELD_NAME);
      }
    }
  }

  private void handleMultipleKeysArrayElement(
      JsonNode element, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions, boolean keepUuidFields) {
    String uuidKey = FQNHelper.getUuidKey(element);

    JsonNode jsonNode = element.get(uuidKey);
    if (jsonNode == null) {
      throw new InvalidRequestException("Invalid Yaml found");
    }
    FQN currFQN = FQN.duplicateAndAddNode(baseFQN,
        FQNNode.builder().nodeType(FQNNode.NodeType.UUID).uuidKey(uuidKey).uuidValue(jsonNode.asText()).build());
    if (FQNHelper.isKeyInsideUUIdsToIdentityElementInList(uuidKey)) {
      generateFQNMap(element, currFQN, res, expressions, keepUuidFields);
    } else {
      Set<String> fieldNames = new LinkedHashSet<>();
      element.fieldNames().forEachRemaining(fieldNames::add);
      for (String key : fieldNames) {
        FQN finalFQN =
            FQN.duplicateAndAddNode(currFQN, FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(key).build());
        FQNHelper.validateUniqueFqn(finalFQN, element.get(key), res, expressions);
      }
    }
  }
}
