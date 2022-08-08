/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YamlNode.UUID_FIELD_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class FQNMapGenerator {
  public Map<FQN, Object> generateFQNMap(JsonNode yamlMap) {
    HashSet<String> expressions = new HashSet<>();
    Set<String> fieldNames = new LinkedHashSet<>();
    yamlMap.fieldNames().forEachRemaining(fieldNames::add);
    String topKey = fieldNames.iterator().next();

    FQNNode startNode = FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(topKey).build();
    FQN currentFQN = FQN.builder().fqnList(Collections.singletonList(startNode)).build();

    Map<FQN, Object> res = new LinkedHashMap<>();

    generateFQNMap(yamlMap.get(topKey), currentFQN, res, expressions);
    return res;
  }

  public void generateFQNMap(JsonNode map, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions) {
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
        generateFQNMapFromList(arrayNode, currFQN, res, expressions);
      } else if (value.getNodeType() == JsonNodeType.OBJECT) {
        if (value.size() == 0) {
          FQNHelper.validateUniqueFqn(currFQN, value, res, expressions);
          continue;
        }
        generateFQNMap(value, currFQN, res, expressions);
      } else {
        FQNHelper.validateUniqueFqn(currFQN, value, res, expressions);
      }
    }
  }

  public void generateFQNMapFromList(ArrayNode list, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions) {
    if (list == null || list.get(0) == null) {
      FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
      return;
    }
    JsonNode firstNode = list.get(0);
    if (firstNode.getNodeType() != JsonNodeType.OBJECT) {
      FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
      return;
    }

    // Remove __uuid key if it contains in the json object
    if (firstNode.isObject() && firstNode.get(UUID_FIELD_NAME) != null) {
      ObjectNode objectNode = (ObjectNode) firstNode;
      objectNode.remove(UUID_FIELD_NAME);
      firstNode = objectNode;
    }
    int noOfKeys = firstNode.size();
    if (noOfKeys == 1) {
      generateFQNMapFromListOfSingleKeyMaps(list, baseFQN, res, expressions);
    } else {
      generateFQNMapFromListOfMultipleKeyMaps(list, baseFQN, res, expressions);
    }
  }

  public void generateFQNMapFromListOfSingleKeyMaps(
      ArrayNode list, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions) {
    if (FQNHelper.checkIfListHasNoIdentifier(list)) {
      FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
      return;
    }
    list.forEach(element -> {
      if (element.has(YAMLFieldNameConstants.PARALLEL)) {
        FQN currFQN = FQN.duplicateAndAddNode(baseFQN, FQNNode.builder().nodeType(FQNNode.NodeType.PARALLEL).build());
        ArrayNode listOfMaps = (ArrayNode) element.get(YAMLFieldNameConstants.PARALLEL);
        generateFQNMapFromList(listOfMaps, currFQN, res, expressions);
      } else {
        Set<String> fieldNames = new LinkedHashSet<>();
        element.fieldNames().forEachRemaining(fieldNames::add);
        String topKey = fieldNames.iterator().next();
        JsonNode innerMap = element.get(topKey);
        String identifier = innerMap.get(YAMLFieldNameConstants.IDENTIFIER).asText();
        FQN currFQN = FQN.duplicateAndAddNode(baseFQN,
            FQNNode.builder()
                .nodeType(FQNNode.NodeType.KEY_WITH_UUID)
                .key(topKey)
                .uuidKey(YAMLFieldNameConstants.IDENTIFIER)
                .uuidValue(identifier)
                .build());
        generateFQNMap(innerMap, currFQN, res, expressions);
      }
    });
  }

  public void generateFQNMapFromListOfMultipleKeyMaps(
      ArrayNode list, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions) {
    String uuidKey = FQNHelper.getUuidKey(list);
    if (EmptyPredicate.isEmpty(uuidKey)) {
      FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
      return;
    }

    list.forEach(element -> {
      JsonNode jsonNode = element.get(uuidKey);
      if (jsonNode == null) {
        throw new InvalidRequestException("Invalid Yaml found");
      }
      FQN currFQN = FQN.duplicateAndAddNode(baseFQN,
          FQNNode.builder().nodeType(FQNNode.NodeType.UUID).uuidKey(uuidKey).uuidValue(jsonNode.asText()).build());
      if (uuidKey.equals(YAMLFieldNameConstants.IDENTIFIER)) {
        generateFQNMap(element, currFQN, res, expressions);
      } else {
        Set<String> fieldNames = new LinkedHashSet<>();
        element.fieldNames().forEachRemaining(fieldNames::add);
        for (String key : fieldNames) {
          FQN finalFQN =
              FQN.duplicateAndAddNode(currFQN, FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(key).build());
          FQNHelper.validateUniqueFqn(finalFQN, element.get(key), res, expressions);
        }
      }
    });
  }
}
