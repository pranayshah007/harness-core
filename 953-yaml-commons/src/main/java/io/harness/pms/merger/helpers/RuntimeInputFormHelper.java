/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.expression.EngineExpressionEvaluator.EXPR_END_ESC;
import static io.harness.expression.EngineExpressionEvaluator.EXPR_START;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.JsonUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class RuntimeInputFormHelper {
  public String createTemplateFromYaml(String templateYaml) {
    try {
      ObjectNode templateJson = (ObjectNode) YamlUtils.readTree(templateYaml).getNode().getCurrJsonNode();
      JsonNode templateVariables = templateJson.findPath("templateVariables");
      JsonNode finalTemplateVariablesJson = new TextNode("");
      if (templateVariables != null && templateVariables.getNodeType() != JsonNodeType.MISSING) {
        JsonNode templateVariablesJsonList = createTemplateVariablesFromTemplate(templateVariables);
        finalTemplateVariablesJson = new ObjectNode(
            JsonNodeFactory.instance, Collections.singletonMap("templateVariables", templateVariablesJsonList));
        if (templateJson.get("templateVariables") != null) {
          templateJson.remove("templateVariables");
        }
      }
      return createRuntimeInputForm(templateJson.toString(), true)
          + YamlPipelineUtils.writeYamlString(finalTemplateVariablesJson);
    } catch (Exception e) {
      return createRuntimeInputForm(templateYaml, true);
    }
  }

  public String createRuntimeInputForm(String yaml, boolean keepInput) {
    YamlConfig runtimeInputFormYamlConfig = createRuntimeInputFormYamlConfig(yaml, keepInput);
    return runtimeInputFormYamlConfig.getYaml();
  }

  private YamlConfig createRuntimeInputFormYamlConfig(String yaml, boolean keepInput) {
    YamlConfig yamlConfig = new YamlConfig(yaml);
    return createRuntimeInputFormYamlConfig(yamlConfig, keepInput);
  }

  public YamlConfig createRuntimeInputFormYamlConfig(YamlConfig yamlConfig, boolean keepInput) {
    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();
    fullMap.keySet().forEach(key -> {
      String value = HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone(fullMap.get(key).toString());
      // keepInput can be considered always true if value matches executionInputPattern. As the input will be provided
      // at execution time.
      if (NGExpressionUtils.matchesExecutionInputPattern(value)
          || (keepInput && NGExpressionUtils.matchesInputSetPattern(value))
          || (!keepInput && !NGExpressionUtils.matchesInputSetPattern(value) && !key.isIdentifierOrVariableName()
              && !key.isType())) {
        templateMap.put(key, fullMap.get(key));
      }
    });

    return new YamlConfig(templateMap, yamlConfig.getYamlMap());
  }

  private JsonNode createTemplateVariablesFromTemplate(JsonNode templateVariableSpec) {
    if (templateVariableSpec.getNodeType() != JsonNodeType.ARRAY) {
      return null;
    }
    ArrayNode variableArray = (ArrayNode) templateVariableSpec;
    List<Map<String, Object>> templateVariableMap = new LinkedList<>();
    for (JsonNode variableNode : variableArray) {
      String variableValue = variableNode.get(YAMLFieldNameConstants.VALUE).asText();
      if (NGExpressionUtils.matchesExecutionInputPattern(variableValue)
          || NGExpressionUtils.matchesInputSetPattern(variableValue)) {
        Map<String, Object> variable = JsonUtils.jsonNodeToMap(variableNode);
        templateVariableMap.add(variable);
      }
    }
    return JsonUtils.asTree(templateVariableMap);
  }

  public String createExecutionInputFormAndUpdateYamlField(JsonNode jsonNode) {
    YamlConfig yamlConfig = new YamlConfig(jsonNode, true);
    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();
    fullMap.keySet().forEach(key -> {
      String value = fullMap.get(key).toString().replace("\\\"", "").replace("\"", "");
      if (NGExpressionUtils.matchesExecutionInputPattern(value)) {
        templateMap.put(key, fullMap.get(key));
        fullMap.put(key,
            EXPR_START + NGExpressionUtils.EXPRESSION_INPUT_CONSTANT + "." + key.getExpressionFqnWithoutIgnoring()
                + EXPR_END_ESC);
      } else if (NGExpressionUtils.matchesUpdatedExecutionInputPattern(value)) {
        templateMap.put(key, fullMap.get(key));
      }
    });
    // Updating the executionInput field to expression in jsonNode.
    JsonNodeUtils.merge(jsonNode, (new YamlConfig(fullMap, yamlConfig.getYamlMap())).getYamlMap());
    return (new YamlConfig(templateMap, yamlConfig.getYamlMap())).getYaml();
  }

  public String createExecutionInputFormAndUpdateYamlFieldForStage(JsonNode jsonNode) {
    JsonNode executionNode = jsonNode.get(YAMLFieldNameConstants.STAGE)
                                 .get(YAMLFieldNameConstants.SPEC)
                                 .get(YAMLFieldNameConstants.EXECUTION);

    JsonNodeUtils.deletePropertiesInJsonNode(
        (ObjectNode) jsonNode.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.SPEC),
        YAMLFieldNameConstants.EXECUTION);

    YamlConfig yamlConfig = new YamlConfig(jsonNode, true);

    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();

    fullMap.keySet().forEach(key -> {
      String value = fullMap.get(key).toString().replace("\\\"", "").replace("\"", "");
      if (NGExpressionUtils.matchesExecutionInputPattern(value)) {
        templateMap.put(key, fullMap.get(key));
        fullMap.put(key,
            EXPR_START + NGExpressionUtils.EXPRESSION_INPUT_CONSTANT + "." + key.getExpressionFqnWithoutIgnoring()
                + EXPR_END_ESC);
      } else if (NGExpressionUtils.matchesUpdatedExecutionInputPattern(value)) {
        templateMap.put(key, fullMap.get(key));
      }
    });
    ((ObjectNode) jsonNode.get(YAMLFieldNameConstants.STAGE).get(YAMLFieldNameConstants.SPEC))
        .set(YAMLFieldNameConstants.EXECUTION, executionNode);

    // Updating the executionInput field to expression in jsonNode.
    JsonNodeUtils.merge(jsonNode, (new YamlConfig(fullMap, yamlConfig.getYamlMap())).getYamlMap());
    return (new YamlConfig(templateMap, yamlConfig.getYamlMap())).getYaml();
  }
}
