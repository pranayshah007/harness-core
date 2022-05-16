/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.common.NGExpressionUtils.matchesInputSetPattern;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class YamlRefreshHelper {
  private final String DUMMY_NODE = "dummy";
  /**
   * Given two jsonNodes, refresh firstNode with respect to sourceNode.
   * RuntimeInputFormHelper.createTemplateFromYaml() requires only one root node. So, added dummy root to source node.
   * Same thing has to be done for nodeToRefresh, so that both are in sync.
   * @param nodeToRefresh
   * @param sourceNode
   * @return refreshed jsonNode with updated values
   */
  public JsonNode refreshNodeFromSourceNode(JsonNode nodeToRefresh, JsonNode sourceNode) {
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
  private JsonNode refreshYamlFromSourceYaml(String nodeToRefreshYaml, String sourceNodeInputSetFormatYaml) {
    YamlConfig sourceNodeYamlConfig = new YamlConfig(sourceNodeInputSetFormatYaml);
    Map<FQN, Object> sourceNodeFqnToValueMap = sourceNodeYamlConfig.getFqnToValueMap();

    YamlConfig nodeToRefreshYamlConfig = new YamlConfig(nodeToRefreshYaml);
    Map<FQN, Object> nodeToRefreshFqnToValueMap = new LinkedHashMap<>(nodeToRefreshYamlConfig.getFqnToValueMap());

    // Iterating all the Runtime Inputs in the sourceNodeFqnToValueMap and replacing the updated values of the runtime
    // inputs with those in the nodeToRefreshFqnToValueMap.
    // TODO: This is incorrect. This doesn't cover the case when the replaced value is an object.
    sourceNodeFqnToValueMap.keySet().forEach(key -> {
      if (nodeToRefreshFqnToValueMap.containsKey(key)) {
        Object value = nodeToRefreshFqnToValueMap.get(key);
        if (matchesInputSetPattern(sourceNodeFqnToValueMap.get(key).toString())) {
          sourceNodeFqnToValueMap.replace(key, value);
        }
      }
    });

    return new YamlConfig(sourceNodeFqnToValueMap, sourceNodeYamlConfig.getYamlMap()).getYamlMap();
  }

  private String convertToYaml(Object object) {
    try {
      return YamlPipelineUtils.getYamlString(object);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Exception occurred while converting object to yaml.");
    }
  }
}
