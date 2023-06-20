/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.FQNMapGenerator;
import io.harness.pms.merger.helpers.InputSetTemplateHelper;
import io.harness.pms.merger.helpers.InputSetYamlHelper;
import io.harness.pms.merger.helpers.YamlMapGenerator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class InputSetSanitizer {
  public String sanitizeInputSetAndUpdateInputSetYAML(String pipelineYaml, String inputSetYaml) {
    String sanitizedRuntimeInputYaml = sanitizeInputSet(pipelineYaml, inputSetYaml, true);
    if (EmptyPredicate.isEmpty(sanitizedRuntimeInputYaml)) {
      return sanitizedRuntimeInputYaml;
    }
    return InputSetYamlHelper.setPipelineComponent(inputSetYaml, sanitizedRuntimeInputYaml);
  }

  public String sanitizeInputSet(String pipelineYaml, String inputSetYaml) {
    return sanitizeInputSet(pipelineYaml, inputSetYaml, true);
  }

  public String sanitizeRuntimeInput(String pipelineYaml, String runtimeInputYaml) {
    return sanitizeInputSet(pipelineYaml, runtimeInputYaml, false);
  }

  private String sanitizeInputSet(String pipelineYaml, String runtimeInput, boolean isInputSet) {
    String templateYaml = InputSetTemplateHelper.createTemplateFromPipeline(pipelineYaml);

    if (templateYaml == null) {
      return "";
    }

    // Strip off inputSet top key from yaml.
    // when its false, its runtimeInput (may be coming from trigger)
    if (isInputSet) {
      runtimeInput = InputSetYamlHelper.getPipelineComponent(runtimeInput);
    }

    String filteredInputSetYaml = InputSetTemplateHelper.removeRuntimeInputFromYaml(pipelineYaml, runtimeInput);
    if (EmptyPredicate.isEmpty(filteredInputSetYaml)) {
      return "";
    }
    YamlConfig inputSetConfig = new YamlConfig(filteredInputSetYaml);

    Set<FQN> invalidFQNsInInputSet =
        InputSetErrorsHelper.getInvalidFQNsInInputSet(templateYaml, filteredInputSetYaml).keySet();

    Map<FQN, Object> filtered = inputSetConfig.getFqnToValueMap()
                                    .entrySet()
                                    .stream()
                                    .filter(entry -> !invalidFQNsInInputSet.contains(entry.getKey()))
                                    .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    return new YamlConfig(filtered, inputSetConfig.getYamlMap(), true).getYaml();
  }

  public String trimValues(String yaml) {
    YamlConfig config = new YamlConfig(yaml);
    YamlConfig config1 = trimValues(config);
    return config1.getYaml();
  }

  public YamlConfig trimValues(YamlConfig config) {
    return new YamlConfig(trimValues(config.getYamlMap()));
  }

  public JsonNode trimValues(JsonNode config) {
    Map<FQN, Object> fqnToValueMap = FQNMapGenerator.generateFQNMap(config);
    for (FQN fqn : fqnToValueMap.keySet()) {
      Object value = fqnToValueMap.get(fqn);
      if (value instanceof TextNode) {
        String trimValue = ((TextNode) value).textValue().trim();
        fqnToValueMap.put(fqn, new TextNode(trimValue));
      }
    }
    return YamlMapGenerator.generateYamlMap(fqnToValueMap, config, false);
  }
}
