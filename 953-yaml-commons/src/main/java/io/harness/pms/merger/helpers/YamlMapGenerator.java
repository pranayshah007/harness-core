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
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class YamlMapGenerator {
  /**
   * For a given fqn to values map, along with the JsonNode for a yaml, this method converts
   * the map to a json node that can then be used to get a yaml file
   *
   * For example, the fqnMap can be corresponding to an input set. In this case, the originalYaml
   * refers to the pipeline of this input set
   */
  public JsonNode generateYamlMap(Map<FQN, Object> fqnMap, JsonNode originalYaml, boolean isSanitiseFlow) {
    return generateYamlMap(fqnMap, originalYaml, isSanitiseFlow, false);
  }

  public JsonNode generateYamlMap(
      Map<FQN, Object> fqnMap, JsonNode originalYaml, boolean isSanitiseFlow, boolean keepUuidFields) {
    Set<String> fieldNames = new LinkedHashSet<>();
    originalYaml.fieldNames().forEachRemaining(fieldNames::add);
    String topKey = fieldNames.iterator().next();

    if (keepUuidFields && topKey.equals(YamlNode.UUID_FIELD_NAME) && fieldNames.size() > 1) {
      topKey = fieldNames.stream().filter(o -> !o.equals(YamlNode.UUID_FIELD_NAME)).findAny().get();
    }

    FQNNode startNode = FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(topKey).build();
    FQN currentFQN = FQN.builder().fqnList(Collections.singletonList(startNode)).build();

    Map<String, Object> tempMap = new LinkedHashMap<>();
    generateYamlMap(YamlSubMapExtractor.getFQNToObjectSubMap(fqnMap, currentFQN), currentFQN, originalYaml.get(topKey),
        tempMap, topKey, isSanitiseFlow);
    try {
      // TODO(Shalini): Use JsonPipelineUtils to convert map to jsonNode
      return YamlUtils.readTree(YamlUtils.writeYamlString(tempMap)).getNode().getCurrJsonNode();
    } catch (IOException e) {
      log.error("Could not generate JsonNode from FQN Map.", e);
      throw new InvalidRequestException("Could not generate JsonNode from FQN Map: " + e.getMessage());
    }
  }

  private void generateYamlMap(Map<FQN, Object> fqnMap, FQN baseFQN, JsonNode originalYaml, Map<String, Object> res,
      String topKey, boolean isSanitiseFlow) {
    Set<String> fieldNames = new LinkedHashSet<>();
    originalYaml.fieldNames().forEachRemaining(fieldNames::add);
    Map<String, Object> tempMap = new LinkedHashMap<>();
    for (String key : fieldNames) {
      JsonNode value = originalYaml.get(key);
      FQN currFQN = FQN.duplicateAndAddNode(baseFQN, FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(key).build());
      if (fqnMap.containsKey(currFQN)) {
        tempMap.put(key, fqnMap.get(currFQN));
      } else if (value.getNodeType() == JsonNodeType.ARRAY) {
        ArrayNode arrayNode = (ArrayNode) value;
        generateYamlMapFromList(arrayNode, currFQN, fqnMap, tempMap, key, isSanitiseFlow);
      } else if (value.getNodeType() == JsonNodeType.OBJECT) {
        generateYamlMap(
            YamlSubMapExtractor.getFQNToObjectSubMap(fqnMap, currFQN), currFQN, value, tempMap, key, isSanitiseFlow);
      }
    }
    if (!tempMap.isEmpty()) {
      if (isSanitiseFlow) {
        if (tempMap.size() == 2 && tempMap.containsKey(YAMLFieldNameConstants.IDENTIFIER)
            && tempMap.containsKey(YAMLFieldNameConstants.TYPE)) {
          return;
        }
        if (tempMap.size() == 1 && tempMap.containsKey(YAMLFieldNameConstants.NAME)) {
          return;
        }
      }
      Map<String, Object> newTempMap = new LinkedHashMap<>();
      if (fieldNames.contains(YAMLFieldNameConstants.IDENTIFIER)) {
        newTempMap.put(YAMLFieldNameConstants.IDENTIFIER, originalYaml.get(YAMLFieldNameConstants.IDENTIFIER));
      }
      if (originalYaml.has(YAMLFieldNameConstants.TYPE)) {
        newTempMap.put(YAMLFieldNameConstants.TYPE, originalYaml.get(YAMLFieldNameConstants.TYPE));
      }
      newTempMap.putAll(tempMap);
      res.put(topKey, newTempMap);
    }
  }

  private void generateYamlMapFromList(ArrayNode list, FQN baseFQN, Map<FQN, Object> fqnMap, Map<String, Object> res,
      String topKey, boolean isSanitiseFlow) {
    if (list == null || list.get(0) == null) {
      return;
    }
    JsonNode firstNode = list.get(0);
    if (firstNode.getNodeType() != JsonNodeType.OBJECT) {
      if (fqnMap.containsKey(baseFQN)) {
        res.put(topKey, list);
      }
      return;
    }
    int noOfKeys = firstNode.size();
    // UUID_FIELD_NAME is a generated Key. it should not be included in counting the number of keys in original field.
    if (noOfKeys > 1 && firstNode.get(UUID_FIELD_NAME) != null) {
      noOfKeys -= 1;
    }
    if (noOfKeys == 1 && EmptyPredicate.isEmpty(FQNHelper.getUuidKey(list))) {
      generateYamlMapFromListOfSingleKeyMaps(list, baseFQN, fqnMap, res, topKey, isSanitiseFlow);
    } else {
      generateYamlMapFromListOfMultipleKeyMaps(list, baseFQN, fqnMap, res, topKey, isSanitiseFlow);
    }
  }

  private void generateYamlMapFromListOfSingleKeyMaps(ArrayNode list, FQN baseFQN, Map<FQN, Object> fqnMap,
      Map<String, Object> res, String topKey, boolean isSanitiseFlow) {
    List<Object> topKeyList = new ArrayList<>();
    if (FQNHelper.checkIfListHasNoIdentifier(list)) {
      if (fqnMap.containsKey(baseFQN)) {
        topKeyList.add(list);
        res.put(topKey, topKeyList);
      }
      return;
    }
    list.forEach(element -> {
      if (element.has(YAMLFieldNameConstants.PARALLEL)) {
        FQN currFQN = FQN.duplicateAndAddNode(baseFQN, FQNNode.builder().nodeType(FQNNode.NodeType.PARALLEL).build());
        ArrayNode listOfMaps = (ArrayNode) element.get(YAMLFieldNameConstants.PARALLEL);
        Map<String, Object> tempMap = new LinkedHashMap<>();
        generateYamlMapFromList(listOfMaps, currFQN, YamlSubMapExtractor.getFQNToObjectSubMap(fqnMap, currFQN), tempMap,
            YAMLFieldNameConstants.PARALLEL, isSanitiseFlow);
        if (!tempMap.isEmpty()) {
          topKeyList.add(tempMap);
        }
      } else {
        Set<String> fieldNames = new LinkedHashSet<>();
        element.fieldNames().forEachRemaining(fieldNames::add);
        String topKeyOfInnerMap = fieldNames.iterator().next();
        JsonNode innerMap = element.get(topKeyOfInnerMap);
        String identifier = innerMap.get(YAMLFieldNameConstants.IDENTIFIER).asText();
        FQN currFQN = FQN.duplicateAndAddNode(baseFQN,
            FQNNode.builder()
                .nodeType(FQNNode.NodeType.KEY_WITH_UUID)
                .key(topKeyOfInnerMap)
                .uuidKey(YAMLFieldNameConstants.IDENTIFIER)
                .uuidValue(identifier)
                .build());
        Map<String, Object> tempMap = new LinkedHashMap<>();
        generateYamlMap(YamlSubMapExtractor.getFQNToObjectSubMap(fqnMap, currFQN), currFQN, innerMap, tempMap,
            topKeyOfInnerMap, isSanitiseFlow);
        if (!tempMap.isEmpty()) {
          topKeyList.add(tempMap);
        }
      }
    });
    if (!topKeyList.isEmpty()) {
      res.put(topKey, topKeyList);
    }
  }

  private void generateYamlMapFromListOfMultipleKeyMaps(ArrayNode list, FQN baseFQN, Map<FQN, Object> fqnMap,
      Map<String, Object> res, String topKey, boolean isSanitiseFlow) {
    List<Object> topKeyList = new ArrayList<>();
    String uuidKey = FQNHelper.getUuidKey(list);
    if (EmptyPredicate.isEmpty(uuidKey)) {
      if (fqnMap.containsKey(baseFQN)) {
        topKeyList.add(list);
        res.put(topKey, topKeyList);
      }
      return;
    }
    list.forEach(element -> {
      FQN currFQN = FQN.duplicateAndAddNode(baseFQN,
          FQNNode.builder()
              .nodeType(FQNNode.NodeType.UUID)
              .uuidKey(uuidKey)
              .uuidValue(element.get(uuidKey).asText())
              .build());
      Map<String, Object> tempRes = new LinkedHashMap<>();
      if (FQNHelper.isKeyInsideUUIdsToIdentityElementInList(uuidKey)) {
        generateYamlMap(fqnMap, currFQN, element, tempRes, topKey, isSanitiseFlow);
        if (tempRes.containsKey(topKey)) {
          Map<String, Object> map = (Map) tempRes.get(topKey);
          map.put(uuidKey, element.get(uuidKey));
          topKeyList.add(map);
        }
      } else {
        Map<String, Object> tempMap = new LinkedHashMap<>();
        Set<String> fieldNames = new LinkedHashSet<>();
        element.fieldNames().forEachRemaining(fieldNames::add);
        for (String key : fieldNames) {
          FQN finalFQN =
              FQN.duplicateAndAddNode(currFQN, FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(key).build());
          if (fqnMap.containsKey(finalFQN)) {
            tempMap.put(key, fqnMap.get(finalFQN));
          }
        }
        if (!tempMap.isEmpty()) {
          Map<String, Object> newTempMap = new LinkedHashMap<>();
          newTempMap.put(uuidKey, element.get(uuidKey));
          if (element.has(YAMLFieldNameConstants.TYPE)) {
            newTempMap.put(YAMLFieldNameConstants.TYPE, element.get(YAMLFieldNameConstants.TYPE));
          }
          newTempMap.putAll(tempMap);
          topKeyList.add(newTempMap);
        }
      }
    });
    if (!topKeyList.isEmpty()) {
      res.put(topKey, topKeyList);
    }
  }
}
