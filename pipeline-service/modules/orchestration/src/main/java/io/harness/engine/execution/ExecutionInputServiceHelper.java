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
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class ExecutionInputServiceHelper {
  public Map<String, Object> getExecutionInputMap(JsonNode inputTemplate, JsonNode userInput) {
    Map<FQN, Object> templateFqnToValueMap = FQNMapGenerator.generateFQNMap(inputTemplate);
    YamlConfig inputConfig = new YamlConfig(userInput);
    Map<FQN, Object> fullUserInputMap = inputConfig.getFqnToValueMap();

    Map<String, Object> inputMap = new LinkedHashMap<>();
    // Iterating over all values in template and checking if it was an executionInput. if yes then add the corresponding
    // fqn expression to the inputMap.
    templateFqnToValueMap.keySet().forEach(key -> {
      String value = templateFqnToValueMap.get(key).toString().replace("\\\"", "").replace("\"", "");
      if (NGExpressionUtils.matchesExecutionInputPattern(value)) {
        Object rawInputValue = fullUserInputMap.get(key);
        Object inputValue = JsonNodeUtils.getValueFromJsonNode(rawInputValue);
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
}
