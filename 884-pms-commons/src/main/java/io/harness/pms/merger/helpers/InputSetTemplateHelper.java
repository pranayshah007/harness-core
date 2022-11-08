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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class InputSetTemplateHelper {
  public String createTemplateFromPipeline(String pipelineYaml) {
    return RuntimeInputFormHelper.createRuntimeInputForm(pipelineYaml, true);
  }

  public String createTemplateFromPipelineForGivenStages(String pipelineYaml, List<String> stageIdentifiers) {
    String template = RuntimeInputFormHelper.createRuntimeInputForm(pipelineYaml, true);
    if (EmptyPredicate.isEmpty(template)) {
      return null;
    }
    return removeNonRequiredStages(template, pipelineYaml, stageIdentifiers);
  }

  public String removeRuntimeInputFromYaml(String pipelineYaml, String runtimeInputYaml) {
    return RuntimeInputFormHelper.removeRuntimeInputsFromYaml(pipelineYaml, runtimeInputYaml, false);
  }

  public String removeNonRequiredStages(String template, String pipelineYaml, List<String> stageIdentifiers) {
    YamlConfig pipelineYamlConfig = new YamlConfig(pipelineYaml);
    YamlConfig templateConfig = new YamlConfig(template);
    Map<FQN, Object> templateFQNMap = templateConfig.getFqnToValueMap();
    Set<FQN> nonRuntimeInputFQNs = new HashSet<>();
    templateFQNMap.keySet().forEach(key -> {
      String value = templateFQNMap.get(key).toString().replace("\"", "");
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
}
