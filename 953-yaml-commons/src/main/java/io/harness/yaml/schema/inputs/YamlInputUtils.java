/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.schema.inputs;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.yaml.YamlSchemaFieldConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.schema.inputs.beans.InputDetails;
import io.harness.yaml.schema.inputs.beans.InputDetails.InputDetailsBuilder;
import io.harness.yaml.schema.inputs.beans.SchemaInputType;
import io.harness.yaml.utils.JsonFieldUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(PIPELINE)
public class YamlInputUtils {
  public List<InputDetails> getYamlInputList(String yaml) {
    List<InputDetails> inputDetails = new ArrayList<>();
    ObjectNode inputsJsonNode;

    JsonNode yamlJsonNode = YamlUtils.readAsJsonNode(yaml);
    // should pick hardcoded values from some constants, need to change it

    inputsJsonNode = getInputsNode(yamlJsonNode);
    if (inputsJsonNode == null) {
      return inputDetails;
    }

    Iterator<String> inputNames = inputsJsonNode.fieldNames();
    for (Iterator<String> it = inputNames; it.hasNext();) {
      String inputName = it.next();
      inputDetails.add(buildInputJsonNode(inputsJsonNode.get(inputName), inputName));
    }

    return inputDetails;
  }

  public String prepareYamlInputExpression(InputDetails inputDetails) {
    return "<+inputs." + inputDetails.getName() + ">";
  }

  public Map<String, InputDetails> prepareYamlInputExpressionToYamlInputMap(List<InputDetails> inputDetailsList) {
    Map<String, InputDetails> yamlInputMap = new HashMap<>();
    inputDetailsList.forEach(inputDetails -> yamlInputMap.put(prepareYamlInputExpression(inputDetails), inputDetails));
    return yamlInputMap;
  }

  public Map<String, List<FQN>> parseFQNsForAllInputsInYaml(
      Map<FQN, Object> fqnToValueMap, Set<String> inputExpressionsList) {
    Map<String, List<FQN>> FQNListForEachInput = new HashMap<>();
    fqnToValueMap.forEach((fqn, v) -> {
      String value = v.toString();
      // remove double quoted string if present
      if (value.charAt(0) == '"') {
        value = value.substring(1);
      }
      if (value.charAt(value.length() - 1) == '"') {
        value = value.substring(0, value.length() - 1);
      }
      if (inputExpressionsList.contains(value)) {
        if (!FQNListForEachInput.containsKey(fqn)) {
          FQNListForEachInput.put(value, new ArrayList<>());
        }
        FQNListForEachInput.get(value).add(fqn);
      }
    });
    return FQNListForEachInput;
  }

  private InputDetails buildInputJsonNode(JsonNode inputNode, String inputName) {
    InputDetailsBuilder builder =
        InputDetails.builder().name(inputName).type(SchemaInputType.valueOf(inputNode.get("type").asText()));
    if (inputNode.get("description") != null) {
      builder.description(inputNode.get("description").asText());
    }
    if (inputNode.get("required") != null) {
      builder.required(inputNode.get("required").asBoolean());
    }
    if (!JsonFieldUtils.isPresent(inputNode, "default")) {
      builder.defaultValue(inputNode.get("default"));
    }
    return builder.build();
  }

  private ObjectNode getInputsNode(JsonNode rootJsonNode) {
    JsonNode firstNode = JsonFieldUtils.get(rootJsonNode, YamlSchemaFieldConstants.PIPELINE);
    if (firstNode == null) {
      firstNode = JsonFieldUtils.get(rootJsonNode, YamlSchemaFieldConstants.TEMPLATE);
    }
    if (firstNode != null && JsonFieldUtils.isPresent(firstNode, YamlSchemaFieldConstants.INPUTS)) {
      return (ObjectNode) JsonFieldUtils.get(firstNode, YamlSchemaFieldConstants.INPUTS);
    }
    return null;
  }
}
