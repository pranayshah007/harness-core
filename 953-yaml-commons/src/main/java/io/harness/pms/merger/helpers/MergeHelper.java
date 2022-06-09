/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.YamlException;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.RuntimeInputValuesValidator;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class MergeHelper {
  public String mergeInputSetFormatYamlToOriginYaml(String originYaml, String inputSetFormatYaml) {
    return mergeRuntimeInputValuesIntoOriginalYaml(originYaml, inputSetFormatYaml, false);
  }

  public String mergeRuntimeInputValuesIntoOriginalYaml(
      String originalYaml, String inputSetPipelineCompYaml, boolean appendInputSetValidator) {
    YamlConfig mergedYamlConfig = mergeRuntimeInputValuesIntoOriginalYamlInternal(
        originalYaml, inputSetPipelineCompYaml, appendInputSetValidator, false);

    return mergedYamlConfig.getYaml();
  }

  public JsonNode mergeExecutionInputIntoOriginalYamlJsonNode(
      String originalYaml, String inputSetPipelineCompYaml, boolean appendInputSetValidator) {
    YamlConfig mergedYamlConfig = mergeRuntimeInputValuesIntoOriginalYamlInternal(
        originalYaml, inputSetPipelineCompYaml, appendInputSetValidator, true);

    return mergedYamlConfig.getYamlMap();
  }

  private YamlConfig mergeRuntimeInputValuesIntoOriginalYamlInternal(String originalYaml,
      String inputSetPipelineCompYaml, boolean appendInputSetValidator, boolean isAtExecutionTime) {
    YamlConfig inputSetConfig = new YamlConfig(inputSetPipelineCompYaml);
    Map<FQN, Object> inputSetFQNMap = inputSetConfig.getFqnToValueMap();

    YamlConfig originalYamlConfig = new YamlConfig(originalYaml);

    Map<FQN, Object> mergedYamlFQNMap = new LinkedHashMap<>(originalYamlConfig.getFqnToValueMap());
    originalYamlConfig.getFqnToValueMap().keySet().forEach(key -> {
      if (inputSetFQNMap.containsKey(key)) {
        Object value = inputSetFQNMap.get(key);
        Object templateValue = originalYamlConfig.getFqnToValueMap().get(key);
        if (key.isType() || key.isIdentifierOrVariableName()) {
          if (!value.toString().equals(templateValue.toString())) {
            throwUpdatedKeyException(key, templateValue, value);
          }
        }
        if (isAtExecutionTime) {
          String valueText = ((JsonNode) value).asText();
          if (NGExpressionUtils.matchesExecutionInputPattern(valueText)) {
            ParameterField<?> inputSetParameterField =
                RuntimeInputValuesValidator.getInputSetParameterField(((JsonNode) value).asText());
            if (inputSetParameterField != null) {
              value = inputSetParameterField.getValue();
            }
          }
        }
        if (appendInputSetValidator) {
          value = checkForRuntimeInputExpressions(value, originalYamlConfig.getFqnToValueMap().get(key));
        }
        mergedYamlFQNMap.put(key, value);
      } else {
        Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(inputSetFQNMap, key);
        if (!subMap.isEmpty()) {
          mergedYamlFQNMap.put(key, YamlSubMapExtractor.getNodeForFQN(inputSetConfig, key));
        }
      }
    });
    return new YamlConfig(mergedYamlFQNMap, originalYamlConfig.getYamlMap());
  }

  private void throwUpdatedKeyException(FQN key, Object templateValue, Object value) {
    throw new InvalidRequestException("The value for " + key.getExpressionFqn() + " is " + templateValue.toString()
        + "in the pipeline yaml, but the input set has it as " + value.toString());
  }

  private Object checkForRuntimeInputExpressions(Object inputSetValue, Object pipelineValue) {
    String pipelineValText = ((JsonNode) pipelineValue).asText();
    String inputSetValueText = ((JsonNode) inputSetValue).asText();
    if (!NGExpressionUtils.matchesInputSetPattern(pipelineValText)) {
      return inputSetValue;
    }
    try {
      ParameterField<?> parameterField = YamlUtils.read(pipelineValText, ParameterField.class);
      if (parameterField.getInputSetValidator() == null) {
        return inputSetValue;
      }
      /*
      this if block appends the input set validator on every element of a list of primitive types
       */
      if (inputSetValue instanceof ArrayNode) {
        ArrayNode inputSetArray = (ArrayNode) inputSetValue;
        List<ParameterField<?>> appendedValidator = new ArrayList<>();
        for (JsonNode element : inputSetArray) {
          String elementText = element.asText();
          appendedValidator.add(ParameterField.createExpressionField(
              true, elementText, parameterField.getInputSetValidator(), element.getNodeType() != JsonNodeType.STRING));
        }
        return appendedValidator;
      }
      ParameterField<?> inputSetParameterField =
          RuntimeInputValuesValidator.getInputSetParameterField(inputSetValueText);
      if (inputSetParameterField != null && inputSetParameterField.isExecutionInput()) {
        return inputSetValue;
      }
      return ParameterField.createExpressionField(true, ((JsonNode) inputSetValue).asText(),
          parameterField.getInputSetValidator(), ((JsonNode) inputSetValue).getNodeType() != JsonNodeType.STRING);
    } catch (IOException e) {
      log.error("", e);
      return inputSetValue;
    }
  }

  public String mergeUpdatesIntoJson(String pipelineJson, Map<String, String> fqnToJsonMap) {
    YamlNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(pipelineJson).getNode();
    } catch (IOException e) {
      log.error("Could not read the pipeline json:\n" + pipelineJson, e);
      throw new YamlException("Could not read the pipeline json");
    }
    if (EmptyPredicate.isEmpty(fqnToJsonMap)) {
      // the input pipelineJson could actually be a YAML. Need to ensure a JSON is sent
      return JsonUtils.asJson(pipelineNode.getCurrJsonNode());
    }
    fqnToJsonMap.keySet().forEach(fqn -> {
      String content = fqnToJsonMap.get(fqn);
      content = removeNonASCII(content);
      try {
        pipelineNode.replacePath(fqn, YamlUtils.readTree(content).getNode().getCurrJsonNode());
      } catch (IOException e) {
        log.error("Could not read json provided for the fqn: " + fqn + ". Json:\n" + content, e);
        throw new YamlException("Could not read json provided for the fqn: " + fqn);
      }
    });
    return JsonUtils.asJson(pipelineNode.getCurrJsonNode());
  }

  // Yaml Object Mapper can't handle emojis and non ascii characters
  public String removeNonASCII(String content) {
    return content.replaceAll("[^\\x00-\\x7F]", "");
  }

  public String removeFQNs(String json, List<String> toBeRemovedFQNs) {
    if (EmptyPredicate.isEmpty(toBeRemovedFQNs)) {
      return json;
    }
    YamlNode pipelineNode;
    try {
      pipelineNode = YamlUtils.readTree(json).getNode();
    } catch (IOException e) {
      log.error("Could not read the json:\n" + json, e);
      throw new YamlException("Could not read the json");
    }
    toBeRemovedFQNs.forEach(pipelineNode::removePath);
    return JsonUtils.asJson(pipelineNode.getCurrJsonNode());
  }
}
