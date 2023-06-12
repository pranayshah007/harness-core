/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.expression.LateBindingValue;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.utils.JsonPipelineUtils;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 This Functor is invoked when an expression starts with inputs.
 Should return a map of input values based on the type of input.
 *
 */

@OwnedBy(PIPELINE)
@Slf4j
public class InputsFunctor implements LateBindingValue {
  private final JsonNode inputSetJsonNode;
  private final JsonNode pipelineJsonNodeV1;

  public InputsFunctor(JsonNode inputSetJsonNode, JsonNode pipelineJsonNodeV1) {
    this.inputSetJsonNode = inputSetJsonNode;
    this.pipelineJsonNodeV1 = pipelineJsonNodeV1;
  }

  @Override
  public Object bind() {
    YamlNode pipelineNode = new YamlNode(pipelineJsonNodeV1);
    JsonNode versionField = pipelineNode.getField("version").getNode().getCurrJsonNode();
    if (versionField == null || PipelineVersion.V0.equals(versionField.asText())) {
      log.warn("InputsFunctor is invoked for the PipelineYaml Version {}.", PipelineVersion.V0);
      return Collections.emptyMap();
    }

    // This is the inputsYamlNode from pipeline yaml. It contains all metadata of the inputs.
    YamlNode inputsYamlNode = pipelineNode.getField(YAMLFieldNameConstants.INPUTS).getNode();

    Map<String, Object> inputsMap = getMergedInputsMap(inputsYamlNode);
    for (Map.Entry<String, Object> entry : inputsMap.entrySet()) {
      String key = entry.getKey();
      if (inputsYamlNode.getField(key) == null
          || inputsYamlNode.getField(key).getNode().getField(YAMLFieldNameConstants.TYPE) == null) {
        continue;
      }
      if (inputsYamlNode.getField(key)
              .getNode()
              .getField(YAMLFieldNameConstants.TYPE)
              .getNode()
              .getCurrJsonNode()
              .asText()
              .equals(YAMLFieldNameConstants.SECRET)) {
        entry.setValue(NGVariablesUtils.fetchSecretExpression((String) entry.getValue()));
      }
    }
    return inputsMap;
  }

  private Map<String, Object> getMergedInputsMap(YamlNode inputsYamlNode) {
    // Generate the Map of default values from the inputsYamlNode of pipeline yaml.
    Map<String, Object> inputsMap = getDefaultValuesMap(inputsYamlNode);
    // Generate map for inputSet values provided by user in execute API.
    Map<String, Object> inputSetMap =
        JsonPipelineUtils.jsonNodeToMap(inputSetJsonNode.get(YAMLFieldNameConstants.INPUTS));

    if (!EmptyPredicate.isEmpty(inputSetMap)) {
      inputsMap.putAll(inputSetMap);
    }
    return inputsMap;
  }

  private Map<String, Object> getDefaultValuesMap(YamlNode inputsYamlNode) {
    Map<String, Object> defaultValuesMap = new HashMap<>();
    Map<String, Object> inputsFromPipelineYaml = JsonPipelineUtils.jsonNodeToMap(inputsYamlNode.getCurrJsonNode());
    if (EmptyPredicate.isEmpty(inputsFromPipelineYaml)) {
      return defaultValuesMap;
    }
    for (Map.Entry<String, Object> entry : inputsFromPipelineYaml.entrySet()) {
      Map<String, String> entityInfoMap = (Map<String, String>) entry.getValue();
      if (entityInfoMap.get(YAMLFieldNameConstants.DEFAULT) != null) {
        defaultValuesMap.put(entry.getKey(), entityInfoMap.get(YAMLFieldNameConstants.DEFAULT));
      }
    }
    return defaultValuesMap;
  }
}
