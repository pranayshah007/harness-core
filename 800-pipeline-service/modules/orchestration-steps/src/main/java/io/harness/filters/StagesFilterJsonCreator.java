/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filters;

import static io.harness.pms.yaml.YAMLFieldNameConstants.PARALLEL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class StagesFilterJsonCreator {
  public Map<String, YamlField> getDependencies(YamlField stagesYamlNode) {
    Map<String, YamlField> dependencies = new HashMap<>();
    if (stagesYamlNode.getNode() == null) {
      return dependencies;
    }
    List<YamlField> stageYamlFields = getStageYamlFields(stagesYamlNode);
    for (YamlField stageYamlField : stageYamlFields) {
      dependencies.put(stageYamlField.getNode().getUuid(), stageYamlField);
    }
    return dependencies;
  }

  public String getStartingNodeId(YamlField stagesYamlField) {
    return getStageYamlFields(stagesYamlField).get(0).getNode().getUuid();
  }

  public int getStagesCount(Collection<YamlField> stagesYamlField) {
    return (int) stagesYamlField.stream().filter(yamlField -> !yamlField.getName().equals(PARALLEL)).count();
  }

  public List<YamlField> getStageYamlFields(YamlField stagesYamlField) {
    List<YamlNode> yamlNodes = Optional.of(stagesYamlField.getNode().asArray()).orElse(Collections.emptyList());
    List<YamlField> stageFields = new LinkedList<>();

    yamlNodes.forEach(yamlNode -> {
      YamlField stageField = yamlNode.getField("stage");
      YamlField parallelStageField = yamlNode.getField("parallel");
      if (stageField != null) {
        stageFields.add(stageField);
      } else if (parallelStageField != null) {
        stageFields.add(parallelStageField);
      }
    });
    return stageFields;
  }
}
