/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.yaml.validation.RuntimeInputValuesValidator;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class RuntimeInputsValidator {
  private final String DUMMY_NODE = "dummy";

  public boolean areInputsValidAgainstSourceNode(JsonNode nodeToValidate, JsonNode sourceNode) {
    // if source node is null, should return true if nodeToValidate is null
    if (sourceNode == null) {
      return nodeToValidate == null;
    }

    // Add dummy node to sourceNode and create template from it.
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode dummySourceNodeSpecNode = mapper.createObjectNode();
    dummySourceNodeSpecNode.set(DUMMY_NODE, sourceNode);
    String dummySourceNodeSpecYaml = convertToYaml(dummySourceNodeSpecNode);
    String sourceNodeInputSetFormatYaml = RuntimeInputFormHelper.createTemplateFromYaml(dummySourceNodeSpecYaml);

    // if there are no runtime inputs in source node, return true if nodeToValidate is also null.
    if (isEmpty(sourceNodeInputSetFormatYaml)) {
      return nodeToValidate == null;
    }

    // if nodeToValidate is null and there exist runtime inputs in source node, return false.
    if (nodeToValidate == null) {
      return false;
    }

    // add dummy node to nodeToRefresh and convert to yaml.
    ObjectNode dummyNodeToValidate = mapper.createObjectNode();
    dummyNodeToValidate.set(DUMMY_NODE, nodeToValidate);
    String dummyNodeToValidateYaml = convertToYaml(dummyNodeToValidate);

    return validateInputsAgainstSourceNode(dummyNodeToValidateYaml, sourceNodeInputSetFormatYaml);
  }

  private boolean validateInputsAgainstSourceNode(String nodeToValidateYaml, String sourceNodeInputSetFormatYaml) {
    YamlConfig sourceNodeYamlConfig = new YamlConfig(sourceNodeInputSetFormatYaml);
    Map<FQN, Object> sourceNodeFqnToValueMap = sourceNodeYamlConfig.getFqnToValueMap();

    YamlConfig nodeToValidateYamlConfig = new YamlConfig(nodeToValidateYaml);
    Map<FQN, Object> nodeToValidateFqnToValueMap = new LinkedHashMap<>(nodeToValidateYamlConfig.getFqnToValueMap());

    for (Map.Entry<FQN, Object> entry : sourceNodeFqnToValueMap.entrySet()) {
      FQN key = entry.getKey();
      Object value = entry.getValue();
      if (nodeToValidateFqnToValueMap.containsKey(key)) {
        Object linkedValue = nodeToValidateFqnToValueMap.get(key);
        if (key.isType() || key.isIdentifierOrVariableName()) {
          // if key is type/variable/identifier and value does not match return false.
          if (!linkedValue.toString().equals(value.toString())) {
            return false;
          }
        } else {
          if (!RuntimeInputValuesValidator.validateInputValues(value, linkedValue)) {
            return false;
          }
        }

        // remove the key once validated. This is to check if there is a key in nodeToValidateFqnToValueMap but not in
        // sourceNodeFqnToValueMap
        nodeToValidateFqnToValueMap.remove(key);
      } else {
        Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(nodeToValidateFqnToValueMap, key);
        // If subMap is empty, return false since value is not present in nodeToValidateFqnToValueMap.
        if (isEmpty(subMap)) {
          return false;
        }
        // remove the subMap from nodeToValidateFqnToValueMap
        subMap.keySet().forEach(nodeToValidateFqnToValueMap::remove);
      }
    }

    // if nodeToValidateFqnToValueMap is not empty, return false.
    return !isNotEmpty(nodeToValidateFqnToValueMap);
  }

  private String convertToYaml(Object object) {
    try {
      return YamlPipelineUtils.getYamlString(object);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Exception occurred while converting object to yaml.");
    }
  }
}
