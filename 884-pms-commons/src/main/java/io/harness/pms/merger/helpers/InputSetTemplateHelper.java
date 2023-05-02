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
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.yaml.YAMLFieldNameConstants;

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
  public String createTemplateFromPipeline(String pipelineYaml) {
    return RuntimeInputFormHelper.createRuntimeInputForm(pipelineYaml, true);
  }

  // only to be used for get runtime input form API, everywhere else the above method is to be used
  public String createTemplateWithDefaultValuesFromPipeline(String pipelineYaml) {
    return RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(pipelineYaml);
  }

  public String createTemplateFromPipelineForGivenStages(String pipelineYaml, List<String> stageIdentifiers) {
    String template = RuntimeInputFormHelper.createRuntimeInputForm(pipelineYaml, true);
    if (EmptyPredicate.isEmpty(template)) {
      return null;
    }
    return removeNonRequiredStages(template, pipelineYaml, stageIdentifiers, false);
  }

  public String createTemplateWithDefaultValuesFromPipelineForGivenStages(
      String pipelineYaml, List<String> stageIdentifiers) {
    String template = RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(pipelineYaml);
    if (EmptyPredicate.isEmpty(template)) {
      return null;
    }
    return removeNonRequiredStages(template, pipelineYaml, stageIdentifiers, true);
  }

  public String createTemplateWithDefaultValuesAndModifiedPropertiesFromPipelineForGivenStages(
      String mergedPipelineYaml, String pipelineYaml, List<String> stageIdentifiers) {
    String template = RuntimeInputFormHelper.createRuntimeInputFormWithDefaultValues(pipelineYaml);
    if (EmptyPredicate.isEmpty(template)) {
      return null;
    }
    String resolvedTemplateYaml = removeNonRequiredStages(template, pipelineYaml, stageIdentifiers, true);
    return removePropertiesIfNotRequired(mergedPipelineYaml, resolvedTemplateYaml, pipelineYaml);
  }

  public String removeRuntimeInputFromYaml(String pipelineYaml, String runtimeInputYaml) {
    return RuntimeInputFormHelper.removeRuntimeInputsFromYaml(pipelineYaml, runtimeInputYaml, false);
  }

  public String removeNonRequiredStages(
      String template, String pipelineYaml, List<String> stageIdentifiers, boolean keepDefaultValues) {
    YamlConfig pipelineYamlConfig = new YamlConfig(pipelineYaml);
    YamlConfig templateConfig = new YamlConfig(template);
    Map<FQN, Object> templateFQNMap = templateConfig.getFqnToValueMap();
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
    return new YamlConfig(templateFQNMap, pipelineYamlConfig.getYamlMap()).getYaml();
  }

  public String removePropertiesIfNotRequired(String mergedPipelineYaml, String template, String pipelineYaml) {
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

    Set<String> stageIdentifiersWithCloneEnabled =
        filteredSet.stream().map(FQN::getStageIdentifier).collect(Collectors.toSet());

    if (EmptyPredicate.isNotEmpty(stageIdentifiersWithCloneEnabled)) {
      YamlConfig pipelineYamlConfig = new YamlConfig(pipelineYaml);
      YamlConfig templateConfig = new YamlConfig(template);
      Map<FQN, Object> templateFQNMap = templateConfig.getFqnToValueMap();
      FQNHelper.removeProperties(templateFQNMap, stageIdentifiersWithCloneEnabled);
      return new YamlConfig(templateFQNMap, pipelineYamlConfig.getYamlMap()).getYaml();
    }
    return template;
  }
}
