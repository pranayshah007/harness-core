/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.InputSetValidatorType.REGEX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.exception.YamlException;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.merger.fqn.FQNNode.NodeType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.pms.yaml.validation.RuntimeInputValuesValidator;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class MergeHelper {
  public final Set<String> acceptAllChildrenKeys = new HashSet<>(
      Arrays.asList("service", "environment", "template", "services", "environments", "environmentGroup"));

  public String mergeInputSetFormatYamlToOriginYaml(String originYaml, String inputSetFormatYaml) {
    return mergeRuntimeInputValuesIntoOriginalYaml(originYaml, inputSetFormatYaml, false);
  }

  public String mergeRuntimeInputValuesIntoOriginalYaml(
      String originalYaml, String inputSetPipelineCompYaml, boolean appendInputSetValidator) {
    YamlConfig mergedYamlConfig = mergeRuntimeInputValuesIntoOriginalYamlInternal(
        originalYaml, inputSetPipelineCompYaml, appendInputSetValidator, false);

    return mergedYamlConfig.getYaml();
  }

  public YamlConfig mergeRuntimeInputValuesIntoOriginalYaml(
      YamlConfig originalYamlConfig, YamlConfig inputSetConfig, boolean appendInputSetValidator) {
    return mergeRuntimeInputValuesIntoOriginalYamlInternal(
        originalYamlConfig, inputSetConfig, appendInputSetValidator, false);
  }

  public JsonNode mergeExecutionInputIntoOriginalYamlJsonNode(
      String originalYaml, String inputSetPipelineCompYaml, boolean appendInputSetValidator) {
    YamlConfig mergedYamlConfig = mergeRuntimeInputValuesIntoOriginalYamlInternal(
        originalYaml, inputSetPipelineCompYaml, appendInputSetValidator, true);

    return mergedYamlConfig.getYamlMap();
  }

  private YamlConfig mergeRuntimeInputValuesIntoOriginalYamlInternal(String originalYaml,
      String inputSetPipelineCompYaml, boolean appendInputSetValidator, boolean isAtExecutionTime) {
    YamlConfig originalYamlConfig = new YamlConfig(originalYaml);
    YamlConfig inputSetConfig = new YamlConfig(inputSetPipelineCompYaml);
    return mergeRuntimeInputValuesIntoOriginalYamlInternal(
        originalYamlConfig, inputSetConfig, appendInputSetValidator, isAtExecutionTime);
  }

  private YamlConfig mergeRuntimeInputValuesIntoOriginalYamlInternal(YamlConfig originalYamlConfig,
      YamlConfig inputSetConfig, boolean appendInputSetValidator, boolean isAtExecutionTime) {
    Map<FQN, Object> inputSetFQNMap = inputSetConfig.getFqnToValueMap();

    Map<FQN, Object> pipelineYamlFQNMap = originalYamlConfig.getFqnToValueMap();
    Map<FQN, Object> mergedYamlFQNMap = new LinkedHashMap<>(pipelineYamlFQNMap);
    pipelineYamlFQNMap.keySet().forEach(key -> {
      if (inputSetFQNMap.containsKey(key)) {
        Object value = inputSetFQNMap.get(key);
        Object templateValue = pipelineYamlFQNMap.get(key);
        // input sets can now have <+input> in them as we will not remove those fields anymore. So if the first input
        // set provides some value and the second does not, then the first value should and not be overriden by the
        // <+input> in the second input set
        if (value instanceof TextNode && NGExpressionUtils.matchesInputSetPattern(((TextNode) value).asText())
            && !NGExpressionUtils.matchesExecutionInputPattern(((TextNode) value).asText())) {
          return;
        }
        if (key.isType() || key.isIdentifierOrVariableName()) {
          if (!value.toString().equals(templateValue.toString())) {
            return;
          }
        }
        if (isAtExecutionTime) {
          String templateValueText = ((JsonNode) templateValue).asText();
          if (NGExpressionUtils.matchesExecutionInputPattern(templateValueText)) {
            ParameterField<?> inputSetParameterField =
                RuntimeInputValuesValidator.getInputSetParameterField(((JsonNode) value).asText());
            if (inputSetParameterField != null && inputSetParameterField.getValue() != null) {
              value = inputSetParameterField.getValue();
            }
          }
        }
        if (appendInputSetValidator) {
          value = checkForRuntimeInputExpressions(value, pipelineYamlFQNMap.get(key));
        }
        mergedYamlFQNMap.put(key, value);
      } else {
        Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(inputSetFQNMap, key);
        if (!subMap.isEmpty()) {
          mergedYamlFQNMap.put(key, YamlSubMapExtractor.getNodeForFQN(inputSetConfig, key));
        }
      }
    });
    Map<FQN, Object> nonIgnorableKeys = getNonIgnorableKeys(pipelineYamlFQNMap, inputSetFQNMap);
    mergedYamlFQNMap.putAll(nonIgnorableKeys);
    JsonNode yamlMap = originalYamlConfig.getYamlMap();
    JsonNode modifiedOriginalMap =
        addNonIgnorableBaseKeys(yamlMap, mergedYamlFQNMap, nonIgnorableKeys, inputSetConfig.getYamlMap());
    return new YamlConfig(mergedYamlFQNMap, modifiedOriginalMap);
  }

  private JsonNode addNonIgnorableBaseKeys(JsonNode yamlMap, Map<FQN, Object> mergedYamlFQNMap,
      Map<FQN, Object> nonIgnorableKeys, JsonNode runtimeInputYamlMap) {
    Set<FQN> newKeys = new HashSet<>();
    for (FQN nonIgnorableKey : nonIgnorableKeys.keySet()) {
      List<FQNNode> fqnList = nonIgnorableKey.getFqnList();
      for (int i = 0; i < fqnList.size() - 1; i++) {
        FQNNode fqnNode = fqnList.get(i);
        if (fqnNode.getNodeType() == NodeType.KEY && acceptAllChildrenKeys.contains(fqnNode.getKey())) {
          newKeys.add(FQN.builder().fqnList(fqnList.subList(0, i + 2)).build());
          // if the nonIgnorableKey is environmentGroup.environments.values, then once environmentGroup.environments is
          // added as a new key, we should break as environmentGroup.environments.values is not needed anymore
          break;
        }
      }
    }
    for (FQN key : newKeys) {
      FQN parent = key.getParent();
      ObjectNode nodeForFQN = (ObjectNode) YamlSubMapExtractor.getNodeForFQN(yamlMap, parent);
      if (!nodeForFQN.has(key.getFieldName())) {
        nodeForFQN.putIfAbsent(key.getFieldName(), new TextNode("<+input>"));
        mergedYamlFQNMap.put(key, YamlSubMapExtractor.getNodeForFQN(runtimeInputYamlMap, key));
      }
    }
    return yamlMap;
  }

  private Map<FQN, Object> getNonIgnorableKeys(Map<FQN, Object> pipelineYamlFQNMap, Map<FQN, Object> inputSetFQNMap) {
    Map<FQN, Object> nonIgnorableKeys = new LinkedHashMap<>();
    Set<FQN> baseFQNs = new HashSet<>();
    pipelineYamlFQNMap.keySet().forEach(key -> {
      FQN baseFQN = key.getBaseFQNTillOneOfGivenFields(acceptAllChildrenKeys);
      if (baseFQN != null) {
        baseFQNs.add(baseFQN);
      }
    });
    baseFQNs.forEach(baseFQN -> {
      Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(inputSetFQNMap, baseFQN);
      if (!subMap.isEmpty()) {
        nonIgnorableKeys.putAll(subMap);
      }
    });
    return nonIgnorableKeys;
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
      InputSetValidator inputSetValidator = parameterField.getInputSetValidator();
      if (inputSetValidator.getValidatorType() == REGEX) {
        boolean matchesPattern =
            NGExpressionUtils.matchesPattern(Pattern.compile(inputSetValidator.getParameters()), inputSetValueText);

        if (matchesPattern) {
          return inputSetValue;
        }
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
        if (NGExpressionUtils.matchesExecutionInputPattern(inputSetValueText)
            && NGExpressionUtils.matchesInputSetPattern(pipelineValText)) {
          if (!NGExpressionUtils.matchesExecutionInputPattern(pipelineValText)) {
            return pipelineValText + ".executionInput()";
          }
          return pipelineValText;
        } else {
          return inputSetValue;
        }
      }

      return ParameterField.createExpressionField(true,
          HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone(((JsonNode) inputSetValue).asText()),
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
