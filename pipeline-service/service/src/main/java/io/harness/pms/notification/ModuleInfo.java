/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@OwnedBy(PIPELINE)
@Getter
@Builder
public class ModuleInfo {
  List<String> services;
  List<String> environments;
  List<String> envGroups;
  List<String> infrastructures;

  public static ModuleInfo getModuleInfo(Ambiance ambiance, PipelineExecutionSummaryEntity executionSummaryEntity) {
    Level currentLevel = AmbianceUtils.obtainCurrentLevel(ambiance);
    if (currentLevel == null || currentLevel.getStepType().getStepCategory() == StepCategory.PIPELINE) {
      return getModuleInfoForPipelineLevel(executionSummaryEntity);
    }
    if (currentLevel.getStepType().getStepCategory() == StepCategory.STAGE) {
      ModuleInfoBuilder moduleInfo = ModuleInfo.builder();
      // If the stage/step is child of looping strategy then the corresponding layoutNode will be with
      // currentLevel.runtimeId. Else it will be currentLevel.setupId.
      GraphLayoutNodeDTO layoutNodeDTO = executionSummaryEntity.getLayoutNodeMap().get(currentLevel.getRuntimeId());
      if (layoutNodeDTO == null) {
        layoutNodeDTO = executionSummaryEntity.getLayoutNodeMap().get(currentLevel.getSetupId());
      }
      if (layoutNodeDTO == null || layoutNodeDTO.getModuleInfo() == null
          || !layoutNodeDTO.getModuleInfo().containsKey("cd")) {
        return null;
      }
      Map<String, Object> cdModuleInfo = layoutNodeDTO.getModuleInfo().get("cd");
      Map<String, Object> serviceInfo = (Map<String, Object>) cdModuleInfo.get("serviceInfo");
      if (EmptyPredicate.isNotEmpty(serviceInfo)) {
        moduleInfo.services(Arrays.asList((String) serviceInfo.get("identifier")));
      }
      Map<String, Object> infraExecutionSummary = (Map<String, Object>) cdModuleInfo.get("infraExecutionSummary");
      if (EmptyPredicate.isNotEmpty(serviceInfo)) {
        if (infraExecutionSummary.containsKey("identifier")) {
          moduleInfo.environments(Arrays.asList((String) infraExecutionSummary.get("identifier")));
        }
        if (infraExecutionSummary.containsKey("envGroupId")) {
          moduleInfo.envGroups(Arrays.asList((String) infraExecutionSummary.get("envGroupId")));
        }
        if (infraExecutionSummary.containsKey("infrastructureIdentifier")) {
          moduleInfo.infrastructures(Arrays.asList((String) infraExecutionSummary.get("infrastructureIdentifier")));
        }
      }
      return moduleInfo.build();
    }
    return null;
  }
  private static ModuleInfo getModuleInfoForPipelineLevel(PipelineExecutionSummaryEntity executionSummaryEntity) {
    ModuleInfoBuilder moduleInfo = ModuleInfo.builder();
    Map<String, Object> moduleInfoMap = executionSummaryEntity.getModuleInfo().get("cd");
    if (EmptyPredicate.isEmpty(moduleInfoMap)) {
      return null;
    }
    if (moduleInfoMap.containsKey("infrastructureIdentifiers")) {
      moduleInfo.infrastructures((List<String>) moduleInfoMap.get("infrastructureIdentifiers"));
    }
    if (moduleInfoMap.containsKey("envIdentifiers")) {
      moduleInfo.environments((List<String>) moduleInfoMap.get("envIdentifiers"));
    }
    if (moduleInfoMap.containsKey("serviceIdentifiers")) {
      moduleInfo.services((List<String>) moduleInfoMap.get("serviceIdentifiers"));
    }
    if (moduleInfoMap.containsKey("envGroupIdentifiers")) {
      moduleInfo.envGroups((List<String>) moduleInfoMap.get("envGroupIdentifiers"));
    }
    return moduleInfo.build();
  }
}
