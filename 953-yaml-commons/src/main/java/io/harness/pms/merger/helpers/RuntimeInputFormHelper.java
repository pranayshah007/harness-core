/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import lombok.experimental.UtilityClass;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.expression.EngineExpressionEvaluator.EXPR_END_ESC;
import static io.harness.expression.EngineExpressionEvaluator.EXPR_START;

@OwnedBy(PIPELINE)
@UtilityClass
public class RuntimeInputFormHelper {
  public String createTemplateFromYaml(String templateYaml) {
    return createRuntimeInputForm(templateYaml, true);
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
