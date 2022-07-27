/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.observer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class OrchestrationObserverUtils {
  // Get list of all stage types that are executed
  public Set<String> getExecutedModulesInPipeline(PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {
    Set<String> executedModules = new HashSet<>();
    Map<String, GraphLayoutNodeDTO> layoutNodeMap = pipelineExecutionSummaryEntity.getLayoutNodeMap();
    for (GraphLayoutNodeDTO graphLayoutNode : layoutNodeMap.values()) {
      if (StatusUtils.isFinalStatus(graphLayoutNode.getStatus().getEngineStatus())) {
        if (graphLayoutNode.getSkipInfo() == null || !graphLayoutNode.getSkipInfo().getEvaluatedCondition()) {
          executedModules.add(graphLayoutNode.getModule());
        }
      }
    }
    return executedModules;
  }
}
