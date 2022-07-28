/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.plan.execution.beans.StagesExecutionInfo;
import io.harness.pms.stages.BasicStageInfo;
import io.harness.pms.stages.StageExecutionSelectorHelper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class StagesExecutionHelper {
  public void throwErrorIfAllStagesAreDeleted(String pipelineYaml, List<String> stagesToRun) {
    List<BasicStageInfo> stageInfoList = StageExecutionSelectorHelper.getStageInfoList(pipelineYaml);
    Set<String> remainingStages = stageInfoList.stream().map(BasicStageInfo::getIdentifier).collect(Collectors.toSet());
    int numDeletedStages = 0;
    for (String identifier : stagesToRun) {
      if (remainingStages.contains(identifier)) {
        continue;
      }
      numDeletedStages++;
    }
    if (stagesToRun.size() == numDeletedStages) {
      throw new InvalidRequestException(
          "All the stages asked to be executed either don't exist or they have been deleted from the pipeline");
    }
  }

  public StagesExecutionInfo getStagesExecutionInfo(
      String pipelineYaml, List<String> stagesToRun, Map<String, String> expressionValues) {
    String pipelineToRun = InputSetMergeHelper.removeNonRequiredStages(pipelineYaml, stagesToRun);
    return StagesExecutionInfo.builder()
        .isStagesExecution(true)
        .pipelineYamlToRun(pipelineToRun)
        .fullPipelineYaml(pipelineYaml)
        .stageIdentifiers(stagesToRun)
        .expressionValues(expressionValues)
        .allowStagesExecution(true)
        .build();
  }
}
