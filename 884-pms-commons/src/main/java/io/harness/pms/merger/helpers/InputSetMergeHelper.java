/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class InputSetMergeHelper {
  public String mergeInputSetIntoPipeline(
      String pipelineYaml, String inputSetPipelineCompYaml, boolean appendInputSetValidator) {
    return MergeHelper.mergeRuntimeInputValuesIntoOriginalYaml(
        pipelineYaml, inputSetPipelineCompYaml, appendInputSetValidator);
  }

  public String mergeInputSetIntoPipelineForGivenStages(String pipelineYaml, String inputSetPipelineCompYaml,
      boolean appendInputSetValidator, List<String> stageIdentifiers) {
    return MergeHelper.mergeRuntimeInputValuesIntoOriginalYaml(
        pipelineYaml, removeNonRequiredStages(inputSetPipelineCompYaml, stageIdentifiers), appendInputSetValidator);
  }

  public String mergeInputSets(String template, List<String> inputSetYamlList, boolean appendInputSetValidator) {
    return mergeInputSetsForGivenStages(template, inputSetYamlList, appendInputSetValidator, null);
  }

  public String mergeInputSetsForGivenStages(
      String template, List<String> inputSetYamlList, boolean appendInputSetValidator, List<String> stageIdentifiers) {
    List<String> inputSetPipelineCompYamlList = inputSetYamlList.stream()
                                                    .map(yaml -> {
                                                      try {
                                                        return InputSetYamlHelper.getPipelineComponent(yaml);
                                                      } catch (InvalidRequestException e) {
                                                        return yaml;
                                                      }
                                                    })
                                                    .collect(Collectors.toList());
    String res = template;
    for (String yaml : inputSetPipelineCompYamlList) {
      String yamlWithRequiredStages = removeNonRequiredStages(yaml, stageIdentifiers);
      if (EmptyPredicate.isEmpty(yamlWithRequiredStages)) {
        continue;
      }
      res = MergeHelper.mergeRuntimeInputValuesIntoOriginalYaml(res, yamlWithRequiredStages, appendInputSetValidator);
    }
    return res;
  }

  public String removeNonRequiredStages(String inputSetPipelineCompYaml, List<String> stageIdentifiers) {
    YamlConfig inputSetConfig = new YamlConfig(inputSetPipelineCompYaml);
    Map<FQN, Object> inputSetFQNMap = inputSetConfig.getFqnToValueMap();
    if (EmptyPredicate.isNotEmpty(stageIdentifiers)) {
      FQNHelper.removeNonRequiredStages(inputSetFQNMap, stageIdentifiers);
    }
    return new YamlConfig(inputSetFQNMap, inputSetConfig.getYamlMap()).getYaml();
  }
}
