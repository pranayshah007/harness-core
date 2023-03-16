/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.RuntimeInputValuesValidator;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class YamlRefreshHelper {
  private final String DUMMY_NODE = "dummy";

  public final Set<String> oneOfKeysParent = new HashSet<>(List.of("service"));
  public final Set<String> ignorableKeysToOneOfAtSameLevel = new HashSet<>(List.of("service.serviceInputs"));

  private static final String USE_FROM_STAGE_NODE = "useFromStage";
  private static final String STAGE_NODE = "stage";
  private static final String CHILD_SERVICE_REF_NODE = "service.serviceRef";
  /**
   * Given two jsonNodes, refresh firstNode with respect to sourceNode.
   * RuntimeInputFormHelper.createTemplateFromYaml() requires only one root node. So, added dummy root to source node.
   * Same thing has to be done for nodeToRefresh, so that both are in sync.
   * @param nodeToRefresh
   * @param sourceNode
   * @return refreshed jsonNode with updated values
   */
  public JsonNode refreshNodeFromSourceNode(JsonNode nodeToRefresh, JsonNode sourceNode) {
    // if there is nothing to refresh from, return null
    if (sourceNode == null) {
      return null;
    }

    // Add dummy node to sourceNode and create template from it.
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode dummySourceNodeSpecNode = mapper.createObjectNode();
    dummySourceNodeSpecNode.set(DUMMY_NODE, sourceNode);
    String dummySourceNodeSpecYaml = convertToYaml(dummySourceNodeSpecNode);
    String sourceNodeInputSetFormatYaml = RuntimeInputFormHelper.createTemplateFromYaml(dummySourceNodeSpecYaml);

    // if there are no runtime inputs in source node, return null
    if (sourceNodeInputSetFormatYaml == null) {
      return null;
    }

    // if nodeToRefresh is null, then directly send sourceNodeInputSetFormatJsonNode back.
    if (nodeToRefresh == null) {
      try {
        JsonNode sourceInputSetFormatJsonNode =
            YamlUtils.readTree(sourceNodeInputSetFormatYaml).getNode().getCurrJsonNode();
        return sourceInputSetFormatJsonNode.get(DUMMY_NODE);
      } catch (IOException e) {
        throw new InvalidRequestException("Couldn't convert sourceNodeInputSetFormatYaml to JsonNode");
      }
    }

    // add dummy node to nodeToRefresh and convert to yaml.
    ObjectNode dummyNodeToRefreshNode = mapper.createObjectNode();
    dummyNodeToRefreshNode.set(DUMMY_NODE, nodeToRefresh);
    String dummyNodeToRefreshYaml = convertToYaml(dummyNodeToRefreshNode);

    JsonNode refreshedJsonNode = refreshYamlFromSourceYaml(dummyNodeToRefreshYaml, sourceNodeInputSetFormatYaml);
    return refreshedJsonNode.get(DUMMY_NODE);
  }

  /**
   * This generates refreshed yaml from sourceNodeInputSetFormatYaml.
   * Here, we iterate over sourceNodeInputSetFormatYaml, if the value to a field is runtime input and was present in
   * nodeToRefreshYaml, then replace the value with value present in nodeToRefreshYaml.
   * @param nodeToRefreshYaml
   * @param sourceNodeInputSetFormatYaml
   * @return refreshed jsonNode with updated values
   */
  public JsonNode refreshYamlFromSourceYaml(String nodeToRefreshYaml, String sourceNodeInputSetFormatYaml) {
    YamlConfig sourceNodeYamlConfig = new YamlConfig(sourceNodeInputSetFormatYaml);
    YamlConfig nodeToRefreshYamlConfig = new YamlConfig(nodeToRefreshYaml);
    return refreshYamlConfigFromSourceYamlConfig(sourceNodeYamlConfig, nodeToRefreshYamlConfig);
  }

  public JsonNode refreshYamlConfigFromSourceYamlConfig(
      YamlConfig sourceNodeYamlConfig, YamlConfig nodeToRefreshYamlConfig) {
    Map<FQN, Object> sourceNodeFqnToValueMap = sourceNodeYamlConfig.getFqnToValueMap();
    Map<FQN, Object> nodeToRefreshFqnToValueMap = nodeToRefreshYamlConfig.getFqnToValueMap();

    Map<FQN, Object> refreshedFqnToValueMap = new LinkedHashMap<>();
    // Iterating all the Runtime Inputs in the sourceNodeFqnToValueMap and adding the updated values of the runtime
    // inputs in refreshedFqnToValueMap.
    sourceNodeFqnToValueMap.keySet().forEach(key -> {
      Object sourceValue = sourceNodeFqnToValueMap.get(key);
      if (nodeToRefreshFqnToValueMap.containsKey(key)) {
        Object linkedValue = nodeToRefreshFqnToValueMap.get(key);
        if (key.isType() || key.isIdentifierOrVariableName()) {
          refreshedFqnToValueMap.put(key, sourceValue);
        } else {
          // validate if linkedValue can replace runtime input value or not.
          if (!RuntimeInputValuesValidator.validateInputValues(sourceValue, linkedValue)) {
            refreshedFqnToValueMap.put(key, sourceValue);
          } else {
            refreshedFqnToValueMap.put(key, linkedValue);
          }
        }
      } else {
        Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(nodeToRefreshFqnToValueMap, key);
        // If subMap is empty, add sourceValue since value is not present in nodeToValidateFqnToValueMap.
        if (isEmpty(subMap)) {
          refreshedFqnToValueMap.put(key, sourceValue);
        } else {
          // get object value and add complete object value to refreshedFqnToValueMap
          refreshedFqnToValueMap.put(key, YamlSubMapExtractor.getNodeForFQN(nodeToRefreshYamlConfig, key));
        }
      }
    });

    Set<FQN> useFromStageFQNKeysFromInputSet =
        getUseFromStageKeysFromInputSet(sourceNodeFqnToValueMap, nodeToRefreshFqnToValueMap);
    JsonNode modifiedOriginalMap =
        addOneOfKeysParentKeysRemovingOtherParallelChildren(sourceNodeYamlConfig.getYamlMap(), refreshedFqnToValueMap,
            useFromStageFQNKeysFromInputSet, nodeToRefreshYamlConfig.getYamlMap());
    return new YamlConfig(refreshedFqnToValueMap, modifiedOriginalMap).getYamlMap();
  }

  private Set<FQN> getUseFromStageKeysFromInputSet(Map<FQN, Object> sourceYamlFQNMap, Map<FQN, Object> inputSetFQNMap) {
    Set<FQN> nonIgnorableKeys = new LinkedHashSet<>();
    Set<FQN> baseServiceFQNs = new HashSet<>();
    sourceYamlFQNMap.keySet().forEach(key -> {
      FQN baseServiceFQN = key.getBaseFQNTillOneOfGivenFields(oneOfKeysParent);
      if (baseServiceFQN != null) {
        baseServiceFQNs.add(baseServiceFQN);
      }
    });

    baseServiceFQNs.forEach(baseSvcFQN -> {
      Set<FQN> inputSetFQNMapKeySet = inputSetFQNMap.keySet();
      FQNNode fqnNodeUseFromStage = FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(USE_FROM_STAGE_NODE).build();
      FQNNode fqnNodeStage = FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(STAGE_NODE).build();

      List<FQNNode> expandedFQNsForUseFromStageObjectNode = new ArrayList<>(baseSvcFQN.getFqnList());
      expandedFQNsForUseFromStageObjectNode.add(fqnNodeUseFromStage);

      List<FQNNode> expandedFQNsForUseFromStageLeafTextNode = new ArrayList<>(expandedFQNsForUseFromStageObjectNode);
      expandedFQNsForUseFromStageLeafTextNode.add(fqnNodeStage);

      FQN useFromStageFQNLeaf = FQN.builder().fqnList(expandedFQNsForUseFromStageLeafTextNode).build();
      FQN useFromStageFQNObject = FQN.builder().fqnList(expandedFQNsForUseFromStageObjectNode).build();

      if (inputSetFQNMapKeySet.contains(useFromStageFQNLeaf)) {
        nonIgnorableKeys.add(useFromStageFQNObject);
      }
    });
    return nonIgnorableKeys;
  }

  /***
   *
   * @param sourceNodeYamlMap
   * @param refreshedYamlNodeFqnToValueMap
   * @param oneOfKeysTypeNodeChild
   * @param runtimeInputYamlMap
   * @return
   * This method modifies original sourceNodeYaml by deleting original oneOfChild fields and adding new one
   * It also modifies refreshedYamlNodeFqnToValueMap in same way
   */
  private JsonNode addOneOfKeysParentKeysRemovingOtherParallelChildren(JsonNode sourceNodeYamlMap,
      Map<FQN, Object> refreshedYamlNodeFqnToValueMap, Set<FQN> oneOfKeysTypeNodeChild, JsonNode runtimeInputYamlMap) {
    for (FQN childKeyFQN : oneOfKeysTypeNodeChild) {
      FQN parent = childKeyFQN.getBaseFQNTillOneOfGivenFields(oneOfKeysParent);
      JsonNode jsonNodeForParentFQN = YamlSubMapExtractor.getNodeForFQN(sourceNodeYamlMap, parent);
      if (jsonNodeForParentFQN instanceof TextNode) {
        continue;
      }

      refreshedYamlNodeFqnToValueMap.entrySet().removeIf(
          next -> next.getKey().contains(parent) && !next.getKey().equals(parent));

      ObjectNode objectNodeForParentFQN = (ObjectNode) jsonNodeForParentFQN;
      for (Iterator<String> fieldNamesItr = objectNodeForParentFQN.fieldNames(); fieldNamesItr.hasNext();) {
        String childFieldName = fieldNamesItr.next();
        if (objectNodeForParentFQN.has(childFieldName) && !childFieldName.equals(childKeyFQN.getFieldName())) {
          fieldNamesItr.remove();
        }
      }

      if (!objectNodeForParentFQN.has(childKeyFQN.getFieldName())) {
        objectNodeForParentFQN.putIfAbsent(childKeyFQN.getFieldName(), new TextNode("<+input>"));
        refreshedYamlNodeFqnToValueMap.put(
            childKeyFQN, YamlSubMapExtractor.getNodeForFQN(runtimeInputYamlMap, childKeyFQN));
      }
    }
    return sourceNodeYamlMap;
  }

  private String convertToYaml(Object object) {
    try {
      return YamlPipelineUtils.getYamlString(object);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Exception occurred while converting object to yaml.");
    }
  }
}
