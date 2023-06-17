/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class InputSetTemplateHelper {
  // TODO(shalini): remove older methods with yaml string once all are moved to jsonNode
  public String createTemplateFromPipeline(String pipelineYaml) {
    return RuntimeInputFormHelper.createRuntimeInputForm(pipelineYaml, true);
  }

  public JsonNode createTemplateFromPipeline(JsonNode pipelineJsonNode) {
    return RuntimeInputFormHelper.createRuntimeInputFormWithJsonNode(pipelineJsonNode, true);
  }

  // only to be used for get runtime input form API, everywhere else the above method is to be used
  public String createTemplateWithDefaultValuesFromPipeline(String pipelineYaml) {
    return RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(pipelineYaml);
  }

  public JsonNode createTemplateWithDefaultValuesFromPipeline(JsonNode pipelineJsonNode) {
    return RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(pipelineJsonNode);
  }

  public String createTemplateFromPipelineForGivenStages(String pipelineYaml, List<String> stageIdentifiers) {
    String template = RuntimeInputFormHelper.createRuntimeInputForm(pipelineYaml, true);
    if (EmptyPredicate.isEmpty(template)) {
      return null;
    }
    return removeNonRequiredStages(template, pipelineYaml, stageIdentifiers, false);
  }

  public JsonNode createTemplateFromPipelineForGivenStages(JsonNode pipelineJsonNode, List<String> stageIdentifiers) {
    JsonNode template = RuntimeInputFormHelper.createRuntimeInputFormWithJsonNode(pipelineJsonNode, true);
    if (isEmpty(template)) {
      return null;
    }
    return removeNonRequiredStages(template, pipelineJsonNode, stageIdentifiers, false);
  }

  public JsonNode createTemplateWithDefaultValuesFromPipelineForGivenStages(
      JsonNode pipelineJsonNode, List<String> stageIdentifiers) {
    JsonNode template = RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(pipelineJsonNode);
    if (isEmpty(template)) {
      return null;
    }
    return removeNonRequiredStages(template, pipelineJsonNode, stageIdentifiers, true);
  }

  public String createTemplateWithDefaultValuesAndModifiedPropertiesFromPipelineForGivenStages(
      String mergedPipelineYaml, String pipelineYaml, List<String> stageIdentifiers) {
    String template = RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(pipelineYaml);
    if (EmptyPredicate.isEmpty(template)) {
      return null;
    }
    String resolvedTemplateYaml = removeNonRequiredStages(template, pipelineYaml, stageIdentifiers, true);
    return removePropertiesIfNotRequired(mergedPipelineYaml, resolvedTemplateYaml, pipelineYaml, stageIdentifiers);
  }

  public String removeRuntimeInputFromYaml(String pipelineYaml, String runtimeInputYaml) {
    return RuntimeInputFormHelper.removeRuntimeInputsFromYaml(pipelineYaml, runtimeInputYaml, false);
  }

  public String removeNonRequiredStages(
      String template, String pipelineYaml, List<String> stageIdentifiers, boolean keepDefaultValues) {
    JsonNode jsonNode = removeNonRequiredStages(YamlUtils.readAsJsonNode(template),
        YamlUtils.readAsJsonNode(pipelineYaml), stageIdentifiers, keepDefaultValues);
    if (isEmpty(jsonNode)) {
      return null;
    }
    return YamlUtils.writeYamlString(jsonNode);
  }

  public JsonNode removeNonRequiredStages(
      JsonNode template, JsonNode pipelineJsonNode, List<String> stageIdentifiers, boolean keepDefaultValues) {
    Map<FQN, Object> templateFQNMap = FQNMapGenerator.generateFQNMap(template);
    Set<FQN> nonRuntimeInputFQNs = new HashSet<>();
    templateFQNMap.keySet().forEach(key -> {
      String value = templateFQNMap.get(key).toString().replace("\"", "");
      if (keepDefaultValues && key.isDefault()) {
        return;
      }
      if (!NGExpressionUtils.matchesInputSetPattern(value)) {
        nonRuntimeInputFQNs.add(key);
      }
    });
    nonRuntimeInputFQNs.forEach(templateFQNMap::remove);
    if (EmptyPredicate.isNotEmpty(stageIdentifiers)) {
      FQNHelper.removeNonRequiredStages(templateFQNMap, stageIdentifiers);
    }
    return YamlMapGenerator.generateYamlMap(templateFQNMap, pipelineJsonNode, false);
  }

  public String removePropertiesIfNotRequired(
      String mergedPipelineYaml, String template, String pipelineYaml, List<String> stageIdentifiers) {
    YamlConfig mergedPipelineYamlConfig = new YamlConfig(mergedPipelineYaml);
    Map<FQN, Object> mergedPipelineYamlFQNMap = mergedPipelineYamlConfig.getFqnToValueMap();
    Set<FQN> filteredSet = mergedPipelineYamlFQNMap.keySet()
                               .stream()
                               .filter(key
                                   -> key.getFqnList().size() >= 5
                                       && key.getFqnList()
                                              .get(key.getFqnList().size() - 1)
                                              .getKey()
                                              .equals(YAMLFieldNameConstants.CLONE_CODEBASE)
                                       && mergedPipelineYamlFQNMap.get(key) == BooleanNode.TRUE)
                               .collect(Collectors.toSet());

    List<String> stageIdentifiersWithCloneEnabled =
        filteredSet.stream().map(FQN::getStageIdentifier).collect(Collectors.toList());

    if (EmptyPredicate.isNotEmpty(stageIdentifiersWithCloneEnabled)) {
      YamlConfig pipelineYamlConfig = new YamlConfig(pipelineYaml);
      YamlConfig templateConfig = new YamlConfig(template);
      Map<FQN, Object> templateFQNMap = templateConfig.getFqnToValueMap();
      FQNHelper.removeProperties(templateFQNMap, stageIdentifiers, stageIdentifiersWithCloneEnabled);
      return new YamlConfig(templateFQNMap, pipelineYamlConfig.getYamlMap()).getYaml();
    }
    return template;
  }
}
