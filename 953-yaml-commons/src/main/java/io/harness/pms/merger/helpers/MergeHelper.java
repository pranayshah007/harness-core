/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.InputSetValidatorType.REGEX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.YamlException;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.merger.fqn.FQNNode.NodeType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.validation.InputSetValidator;
import io.harness.pms.yaml.validation.RuntimeInputValuesValidator;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
  public static final String PATH_SEP = "/";
  public static final Pattern nonAsciiCharactersPattern = Pattern.compile("[^\\x00-\\x7F]");

  public String mergeInputSetFormatYamlToOriginYaml(String originYaml, String inputSetFormatYaml) {
    return mergeRuntimeInputValuesIntoOriginalYaml(originYaml, inputSetFormatYaml, false);
  }

  public String mergeRuntimeInputValuesIntoOriginalYaml(
      String originalYaml, String inputSetPipelineCompYaml, boolean appendInputSetValidator) {
    YamlConfig mergedYamlConfig = mergeRuntimeInputValuesIntoOriginalYamlInternal(
        originalYaml, inputSetPipelineCompYaml, appendInputSetValidator, false);

    return mergedYamlConfig.getYaml();
  }

  public JsonNode mergeRuntimeInputValuesIntoOriginalJsonNode(
      JsonNode originalJsonNode, List<JsonNode> inputSetPipelineCompJsonNodes, boolean appendInputSetValidator) {
    return mergeRuntimeInputValuesIntoOriginalYamlInternal(
        originalJsonNode, inputSetPipelineCompJsonNodes, appendInputSetValidator, false, false);
  }

  public JsonNode mergeRuntimeInputValuesIntoOriginalJsonNode(JsonNode originalJsonNode,
      JsonNode inputSetPipelineCompJsonNode, boolean appendInputSetValidator, boolean checkIfPipelineValueIsRuntime) {
    return mergeRuntimeInputValuesIntoOriginalYamlInternal(
        originalJsonNode, inputSetPipelineCompJsonNode, appendInputSetValidator, false, checkIfPipelineValueIsRuntime);
  }

  public String mergeRuntimeInputValuesAndCheckForRuntimeInOriginalYaml(String baseYaml, String runtimeInputYaml,
      boolean appendInputSetValidator, boolean checkIfPipelineValueIsRuntime) {
    YamlConfig baseConfig = new YamlConfig(baseYaml);
    YamlConfig runtimeConfig = new YamlConfig(runtimeInputYaml);
    return mergeRuntimeInputValuesIntoOriginalYamlInternal(
        baseConfig, runtimeConfig, appendInputSetValidator, false, checkIfPipelineValueIsRuntime)
        .getYaml();
  }

  // Merge the executionInput values in the originalJsonNode when called by the execution-input flow during execution.
  public JsonNode mergeExecutionInputIntoOriginalJsonNode(
      JsonNode originalJsonNode, JsonNode inputSetPipelineCompJsonNode, boolean appendInputSetValidator) {
    return mergeRuntimeInputValuesIntoOriginalYamlInternal(
        originalJsonNode, inputSetPipelineCompJsonNode, appendInputSetValidator, true, false);
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
    return mergeRuntimeInputValuesIntoOriginalYamlInternal(
        originalYamlConfig, inputSetConfig, appendInputSetValidator, isAtExecutionTime, false);
  }

  // checkIfPipelineValueIsRuntime is supposed to be true if the values from inputSetConfig are to be merged only if the
  // corresponding value in originalYamlConfig are <+input>. For example, if the originalYamlConfig is a pipeline yaml,
  // then the runtime input values from inputSetConfig should be merged only if the corresponding value in
  // originalYamlConfig is <+input>, because of which in this case the value of checkIfPipelineValueIsRuntime should be
  // true. On the other hand, if originalYamlConfig is the merged yaml of a pipeline and an input set, and
  // inputSetConfig is that of a second input set, then the values from inputSetConfig can override those from
  // originalYamlConfig, because this second input set should be allowed to override the values given by the first input
  // set. Hence, in this case checkIfPipelineValueIsRuntime is false
  private YamlConfig mergeRuntimeInputValuesIntoOriginalYamlInternal(YamlConfig originalYamlConfig,
      YamlConfig inputSetConfig, boolean appendInputSetValidator, boolean isAtExecutionTime,
      boolean checkIfPipelineValueIsRuntime) {
    JsonNode mergedYamlMap = mergeRuntimeInputValuesIntoOriginalYamlInternal(originalYamlConfig.getYamlMap(),
        inputSetConfig.getYamlMap(), appendInputSetValidator, isAtExecutionTime, checkIfPipelineValueIsRuntime);
    return new YamlConfig(mergedYamlMap);
  }

  private JsonNode mergeRuntimeInputValuesIntoOriginalYamlInternal(JsonNode originalYamlJsonNode,
      JsonNode inputSetJsonNode, boolean appendInputSetValidator, boolean isAtExecutionTime,
      boolean checkIfPipelineValueIsRuntime) {
    return mergeRuntimeInputValuesIntoOriginalYamlInternal(originalYamlJsonNode,
        Collections.singletonList(inputSetJsonNode), appendInputSetValidator, isAtExecutionTime,
        checkIfPipelineValueIsRuntime);
  }

  // First element has more precedence than the later elements in the inputSetJsonNodes list.
  private JsonNode mergeRuntimeInputValuesIntoOriginalYamlInternal(JsonNode originalYamlJsonNode,
      List<JsonNode> inputSetJsonNodes, boolean appendInputSetValidator, boolean isAtExecutionTime,
      boolean checkIfPipelineValueIsRuntime) {
    if (EmptyPredicate.isEmpty(inputSetJsonNodes)) {
      return originalYamlJsonNode;
    }
    Map<FQN, Object> mergedInputSetFqnMap = new HashMap<>();
    List<Map<FQN, Object>> inputSetFqnMapList = new ArrayList<>();

    // Populating the inputSetFqnMaps for all inputSet JsonNodes.
    for (JsonNode inputSetJsonNode : inputSetJsonNodes) {
      Map<FQN, Object> inputSetFQNMap = FQNMapGenerator.generateFQNMap(inputSetJsonNode);
      inputSetFqnMapList.add(inputSetFQNMap);
    }
    // Merging the inputSetFqnMapList into one mergedInputSetFqnMap such that fqnMap coming first in the list will
    // override the later fqnMap values for same fqn.
    for (int index = inputSetFqnMapList.size() - 1; index >= 0; index--) {
      mergedInputSetFqnMap.putAll(inputSetFqnMapList.get(index));
    }
    Map<FQN, Object> pipelineYamlFQNMap = FQNMapGenerator.generateFQNMap(originalYamlJsonNode);
    Map<FQN, Object> mergedYamlFQNMap = new LinkedHashMap<>(pipelineYamlFQNMap);
    pipelineYamlFQNMap.keySet().forEach(key -> {
      for (int index = 0; index < inputSetJsonNodes.size(); index++) {
        JsonNode inputSetJsonNode = inputSetJsonNodes.get(index);
        Map<FQN, Object> inputSetFQNMap = inputSetFqnMapList.get(index);
        if (inputSetFQNMap.containsKey(key)) {
          Object valueFromRuntimeInputYaml = inputSetFQNMap.get(key);
          Object valueFromPipelineYaml = pipelineYamlFQNMap.get(key);
          if (checkIfPipelineValueIsRuntime
              && (!(valueFromPipelineYaml instanceof TextNode)
                  || !NGExpressionUtils.matchesInputSetPattern(((TextNode) valueFromPipelineYaml).asText()))) {
            // if the value from the pipeline YAML is fixed, then we need to ignore the value from the runtime input
            // yaml. The above if condition is true if the value from the pipeline YAML is fixed
            break;
            // break because the field to be ignored for all inputSets.
          }
          // input sets can now have <+input> in them as we will not remove those fields anymore. So if the first input
          // set provides some value and the second does not, then the first value should and not be overriden by the
          // <+input> in the second input set
          if (valueFromRuntimeInputYaml instanceof TextNode
              && NGExpressionUtils.matchesInputSetPattern(((TextNode) valueFromRuntimeInputYaml).asText())
              && !NGExpressionUtils.matchesExecutionInputPattern(((TextNode) valueFromRuntimeInputYaml).asText())) {
            // Continue because any next inputSet might have the input value that should be applied.
            continue;
          }
          if (key.isType() || key.isIdentifierOrVariableName()) {
            if (!valueFromRuntimeInputYaml.toString().equals(valueFromPipelineYaml.toString())) {
              continue;
            }
          }
          if (isAtExecutionTime) {
            String templateValueText = ((JsonNode) valueFromPipelineYaml).asText();
            if (NGExpressionUtils.matchesExecutionInputPattern(templateValueText)) {
              ParameterField<?> inputSetParameterField = RuntimeInputValuesValidator.getInputSetParameterField(
                  ((JsonNode) valueFromRuntimeInputYaml).asText());
              if (inputSetParameterField != null && inputSetParameterField.getValue() != null) {
                valueFromRuntimeInputYaml = inputSetParameterField.getValue();
              }
            }
          }
          if (appendInputSetValidator) {
            valueFromRuntimeInputYaml = checkForRuntimeInputExpressions(
                valueFromRuntimeInputYaml, pipelineYamlFQNMap.get(key), key.getExpressionFqn());
          }
          mergedYamlFQNMap.put(key, valueFromRuntimeInputYaml);
          break;
        } else {
          Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(inputSetFQNMap, key);
          if (!subMap.isEmpty()) {
            mergedYamlFQNMap.put(key, YamlSubMapExtractor.getNodeForFQN(inputSetJsonNode, key));
            break;
          }
        }
      }
    });
    Map<FQN, Object> nonIgnorableKeys = getNonIgnorableKeys(pipelineYamlFQNMap, mergedInputSetFqnMap, mergedYamlFQNMap);
    mergedYamlFQNMap.putAll(nonIgnorableKeys);
    JsonNode modifiedOriginalMap = null;
    for (JsonNode inputSetJsonNode : inputSetJsonNodes) {
      modifiedOriginalMap =
          addNonIgnorableBaseKeys(originalYamlJsonNode, mergedYamlFQNMap, nonIgnorableKeys, inputSetJsonNode);
    }
    // merging mergedYamlFQNMap into modifiedOriginalMap to get merged yaml map
    return YamlMapGenerator.generateYamlMap(mergedYamlFQNMap, modifiedOriginalMap, false);
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
      // parent will have its key as one of the fields in acceptAllChildrenKeys. In case this parent itself is a runtime
      // input, such as when "service" is an axis name, in that case we can ignore this part as all fields will be added
      // anyway.
      FQN parent = key.getParent();
      JsonNode jsonNodeForParentFQN = YamlSubMapExtractor.getNodeForFQN(yamlMap, parent);
      if (jsonNodeForParentFQN instanceof TextNode) {
        continue;
      }
      ObjectNode objectNodeForParentFQN = (ObjectNode) jsonNodeForParentFQN;
      if (!objectNodeForParentFQN.has(key.getFieldName())) {
        objectNodeForParentFQN.putIfAbsent(key.getFieldName(), new TextNode("<+input>"));
        mergedYamlFQNMap.put(key, YamlSubMapExtractor.getNodeForFQN(runtimeInputYamlMap, key));
      }
    }
    return yamlMap;
  }

  // mergedYamlFQNMap can have the validator from pipelineYamlFQNMap merged into the value from inputSetFQNMap, and
  // hence if some key is present in mergedYamlFQNMap, then that key's value should be taken. If some key is not
  // present in the merged map, then the key value pair from the input set fqn map should be taken
  private Map<FQN, Object> getNonIgnorableKeys(
      Map<FQN, Object> pipelineYamlFQNMap, Map<FQN, Object> inputSetFQNMap, Map<FQN, Object> mergedYamlFQNMap) {
    Map<FQN, Object> nonIgnorableKeys = new LinkedHashMap<>();
    Set<FQN> baseFQNs = new HashSet<>();
    pipelineYamlFQNMap.keySet().forEach(key -> {
      FQN baseFQN = key.getBaseFQNTillOneOfGivenFields(acceptAllChildrenKeys);
      if (baseFQN != null) {
        baseFQNs.add(baseFQN);
      }
    });
    baseFQNs.forEach(baseFQN -> {
      Map<FQN, Object> subMapFromInputSet = YamlSubMapExtractor.getFQNToObjectSubMap(inputSetFQNMap, baseFQN);
      subMapFromInputSet.keySet().forEach(inputSetKey -> {
        if (pipelineYamlFQNMap.containsKey(inputSetKey)
            || EmptyPredicate.isNotEmpty(YamlSubMapExtractor.getFQNToObjectSubMap(pipelineYamlFQNMap, inputSetKey))) {
          // if some key is present in pipeline yaml, either as a leaf node or otherwise, then the value from the
          // runtime input yaml needs to be ignored. The first if condition checks if the key is present as a leaf node,
          // and the second one checks if the key is present otherwise
          return;
        }
        if (mergedYamlFQNMap.containsKey(inputSetKey)) {
          nonIgnorableKeys.put(inputSetKey, mergedYamlFQNMap.get(inputSetKey));
        } else {
          nonIgnorableKeys.put(inputSetKey, subMapFromInputSet.get(inputSetKey));
        }
      });
    });
    return nonIgnorableKeys;
  }

  private Object checkForRuntimeInputExpressions(Object inputSetValue, Object pipelineValue, String expressionFqn) {
    String validationMsg =
        RuntimeInputValuesValidator.validateStaticValues(pipelineValue, inputSetValue, expressionFqn);
    if (EmptyPredicate.isNotEmpty(validationMsg)) {
      throw new InvalidRequestException(validationMsg);
    }
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
      ParameterField<String> inputSetParameterField =
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

      // pipelineValue can be <+input>.allowedValues(a,b), while inputSetValue can be b.allowedValued(a,b) if
      // inputSetValue is from a merged Yaml with appendValidators true. In this, we only need to append the validator
      // once, hence the value "b" is extracted from inputSetValueText
      return ParameterField.createExpressionField(true,
          HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone(
              inputSetParameterField == null ? inputSetValueText : inputSetParameterField.fetchFinalValue().toString()),
          parameterField.getInputSetValidator(), ((JsonNode) inputSetValue).getNodeType() != JsonNodeType.STRING);
    } catch (IOException e) {
      log.error("", e);
      return inputSetValue;
    }
  }

  public String mergeUpdatesIntoJson(String pipelineJson, Map<String, String> fqnToJsonMap) {
    return mergeUpdatesIntoJsonParametrisedOnPathSeparator(pipelineJson, fqnToJsonMap, PATH_SEP);
  }

  public String mergeUpdatesIntoJsonParametrisedOnPathSeparator(
      String pipelineJson, Map<String, String> fqnToJsonMap, String pathSeparator) {
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
        pipelineNode.replacePathParametrisedOnPathSeparator(
            fqn, YamlUtils.readTree(content).getNode().getCurrJsonNode(), pathSeparator);
      } catch (IOException e) {
        log.error("Could not read json provided for the fqn: " + fqn + ". Json:\n" + content, e);
        throw new YamlException("Could not read json provided for the fqn: " + fqn);
      }
    });
    return JsonUtils.asJson(pipelineNode.getCurrJsonNode());
  }

  // Yaml Object Mapper can't handle emojis and non ascii characters
  public String removeNonASCII(String content) {
    return nonAsciiCharactersPattern.matcher(content).replaceAll(EMPTY);
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

  public JsonNode mergeOptionsRuntimeInput(JsonNode pipelineJsonNode, JsonNode runtimeInputJsonNode) {
    if (isEmpty(runtimeInputJsonNode)) {
      return pipelineJsonNode;
    }
    Map<String, Object> runtimeInputMap = JsonPipelineUtils.jsonNodeToMap(runtimeInputJsonNode);
    if (EmptyPredicate.isNotEmpty(runtimeInputMap) && runtimeInputMap.containsKey(YAMLFieldNameConstants.OPTIONS)) {
      Map<String, Object> optionsMap = new HashMap<>();
      optionsMap.put(YAMLFieldNameConstants.OPTIONS, runtimeInputMap.get(YAMLFieldNameConstants.OPTIONS));
      // TODO: improve this method by directly replacing options value in map of pipeline json.
      pipelineJsonNode = YamlUtils.replaceYamlInJsonNode(pipelineJsonNode, YamlUtils.writeYamlString(optionsMap));
    }
    return pipelineJsonNode;
  }
}
