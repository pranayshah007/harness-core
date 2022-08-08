/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class YamlSubMapExtractor {
  /**
   * For a particular FQN, extract all the key value pairs from the given map that has the given FQN as
   * a prefix
   */
  public Map<FQN, Object> getFQNToObjectSubMap(Map<FQN, Object> fullMap, FQN baseFQN) {
    Map<FQN, Object> res = new LinkedHashMap<>();
    fullMap.keySet().forEach(key -> {
      if (key.contains(baseFQN)) {
        res.put(key, fullMap.get(key));
      }
    });
    return res;
  }

  /**
   * The given FQN refers to a key in the pipeline config. This method extracts the value corresponding
   * to this FQN. This value can be a list or a map as well
   */
  public JsonNode getNodeForFQN(YamlConfig config, FQN baseFQN) {
    JsonNode curr = config.getYamlMap();
    boolean prevWasParallel = false;
    for (FQNNode node : baseFQN.getFqnList()) {
      if (prevWasParallel) {
        curr = getObjectFromParallel((ArrayNode) curr, node);
        prevWasParallel = false;
      } else if (node.getNodeType() == FQNNode.NodeType.KEY) {
        curr = curr.get(node.getKey());
      } else if (node.getNodeType() == FQNNode.NodeType.KEY_WITH_UUID) {
        curr = getObjectFromArrayNode((ArrayNode) curr, node);
      } else if (node.getNodeType() == FQNNode.NodeType.PARALLEL) {
        prevWasParallel = true;
      } else if (node.getNodeType() == FQNNode.NodeType.UUID) {
        curr = getObjectFromArrayNode((ArrayNode) curr, node);
      }
    }
    return curr;
  }

  private JsonNode getObjectFromArrayNode(ArrayNode curr, FQNNode node) {
    int size = curr.size();
    for (int i = 0; i < size; i++) {
      JsonNode elem =
          node.getNodeType() == FQNNode.NodeType.KEY_WITH_UUID ? curr.get(i).get(node.getKey()) : curr.get(i);
      String identifier = elem.get(node.getUuidKey()).asText();
      if (identifier.equals(node.getUuidValue())) {
        return elem;
      }
    }
    throw new InvalidRequestException("Could not find node in the list");
  }

  private JsonNode getObjectFromParallel(ArrayNode curr, FQNNode node) {
    int size = curr.size();
    for (int i = 0; i < size; i++) {
      JsonNode elem = curr.get(i);
      if (!elem.has(YAMLFieldNameConstants.PARALLEL)) {
        continue;
      }
      ArrayNode innerList = (ArrayNode) elem.get(YAMLFieldNameConstants.PARALLEL);
      int sizeOfInnerList = innerList.size();
      for (int j = 0; j < sizeOfInnerList; j++) {
        JsonNode element = innerList.get(j).get(node.getKey());
        String identifier = element.get(node.getUuidKey()).asText();
        if (identifier.equals(node.getUuidValue())) {
          return element;
        }
      }
    }
    throw new InvalidRequestException("Could not find node in the list");
  }
}
