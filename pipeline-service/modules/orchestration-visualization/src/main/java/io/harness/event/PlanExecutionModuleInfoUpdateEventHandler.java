/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.execution.beans.GraphUpdateInfo;
import io.harness.repositories.executions.GraphUpdateInfoRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionModuleInfoUpdateEventHandler {
  @Inject private GraphUpdateInfoRepository graphUpdateInfoRepository;
  private static final String PIPELINE_MODULE_INFO_UPDATE_KEY = "moduleInfo";
  private static final String STAGE_MODULE_INFO_UPDATE_KEY = "layoutNodeMap.%s.moduleInfo";

  public void handlePipelineInfoUpdate(String planExecutionId, Update update) {
    try {
      Optional<GraphUpdateInfo> graphUpdateInfoOptional =
          graphUpdateInfoRepository.findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategory(
              planExecutionId, StepCategory.PIPELINE);
      if (graphUpdateInfoOptional.isPresent()) {
        createUpdateData(update, graphUpdateInfoOptional, PIPELINE_MODULE_INFO_UPDATE_KEY);
      } else {
        log.error("Graph Update Info not found for planExecutionId {}", planExecutionId);
      }
    } catch (Exception e) {
      log.error("Pipeline info update failed for plan ExecutionId {}", planExecutionId, e);
    }
  }

  public void handleStageInfoUpdate(String planExecutionId, String nodeExecutionId, Update update) {
    try {
      Optional<GraphUpdateInfo> graphUpdateInfoOptional =
          graphUpdateInfoRepository.findByPlanExecutionIdAndExecutionSummaryUpdateInfo_StepCategoryAndNodeExecutionId(
              planExecutionId, StepCategory.STAGE, nodeExecutionId);
      if (graphUpdateInfoOptional.isPresent()) {
        String stageUuid = graphUpdateInfoOptional.get().getExecutionSummaryUpdateInfo().getStageUuid();
        String baseKey = String.format(STAGE_MODULE_INFO_UPDATE_KEY, stageUuid);
        createUpdateData(update, graphUpdateInfoOptional, baseKey);
      } else {
        log.error("Graph Update Info not found for planExecutionId {}", planExecutionId);
      }
    } catch (Exception e) {
      log.error("Pipeline info update failed for plan ExecutionId {}", planExecutionId, e);
    }
  }

  private void createUpdateData(Update update, Optional<GraphUpdateInfo> graphUpdateInfoOptional, String baseKey) {
    Map<String, LinkedHashMap<String, Object>> moduleInfo =
        graphUpdateInfoOptional.get().getExecutionSummaryUpdateInfo().getModuleInfo();
    for (Map.Entry<String, LinkedHashMap<String, Object>> entry : moduleInfo.entrySet()) {
      String moduleName = entry.getKey();
      if (entry.getValue() != null) {
        for (Map.Entry<String, Object> entry1 : entry.getValue().entrySet()) {
          String key = baseKey + "." + moduleName + "." + entry1.getKey();
          if (entry1.getValue() != null && Collection.class.isAssignableFrom(entry1.getValue().getClass())) {
            Collection<Object> values = (Collection<Object>) entry1.getValue();
            update.addToSet(key).each(values);
          } else {
            if (entry1.getValue() != null) {
              update.set(key, entry1.getValue());
            }
          }
        }
      }
    }
  }
}
