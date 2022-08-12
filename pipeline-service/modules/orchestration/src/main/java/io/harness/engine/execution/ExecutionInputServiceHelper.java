/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NumericNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class ExecutionInputServiceHelper {
  public Map<String, Object> getExecutionInputMap(String inputTemplate, JsonNode userInput) {
    YamlConfig templateConfig = new YamlConfig(inputTemplate);
    Map<FQN, Object> fullTemplateMap = templateConfig.getFqnToValueMap();

    YamlConfig inputConfig = new YamlConfig(userInput);
    Map<FQN, Object> fullUserInputMap = inputConfig.getFqnToValueMap();

    Map<String, Object> inputMap = new LinkedHashMap<>();
    // Iterating over all values in template and checking if it was an executionInput. if yes then add the corresponding
    // fqn expression to the inputMap.
    fullTemplateMap.keySet().forEach(key -> {
      String value = fullTemplateMap.get(key).toString().replace("\\\"", "").replace("\"", "");
      if (NGExpressionUtils.matchesExecutionInputPattern(value)) {
        Object rawInputValue = fullUserInputMap.get(key);
        Object inputValue = getValueFromJsonNode(rawInputValue);
        inputMap.put(key.getExpressionFqnWithoutIgnoring(), inputValue);
      }
    });
    return convertFQNExpressionToMap(inputMap);
  }

  public Map<String, Object> convertFQNExpressionToMap(Map<String, Object> fqnMap) {
    Map<String, Object> finalMap = new HashMap<>();
    fqnMap.forEach((key, value) -> {
      String[] fqnComponents = key.split("\\.");
      Map<String, Object> currentMap = finalMap;
      // Keep adding emptyMap until last element in the fqn expression(if not created already).
      for (int i = 0; i < fqnComponents.length - 1; i++) {
        if (!currentMap.containsKey(fqnComponents[i])) {
          currentMap.put(fqnComponents[i], new HashMap<>());
        }
        // Updating the pointer to current map.
        currentMap = (Map<String, Object>) currentMap.get(fqnComponents[i]);
      }
      // Adding the actual value as this is last element in fqn expression.
      currentMap.put(fqnComponents[fqnComponents.length - 1], value);
    });
    return finalMap;
  }

  private Object getValueFromJsonNode(Object objectNode) {
    if (objectNode instanceof TextNode) {
      return ((TextNode) objectNode).asText();
    } else if (objectNode instanceof NumericNode) {
      return ((NumericNode) objectNode).doubleValue();
    } else if (objectNode instanceof BooleanNode) {
      return ((BooleanNode) objectNode).booleanValue();
    } else if (objectNode instanceof ArrayNode) {
      List<Object> response = new ArrayList<>();
      for (int i = 0; i < ((ArrayNode) objectNode).size(); i++) {
        response.add(getValueFromJsonNode(((ArrayNode) objectNode).get(i)));
      }
      return response;
    } else {
      return objectNode.toString().replace("\\\"", "").replace("\"", "");
    }
  }
}
