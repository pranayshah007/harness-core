/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.mappers;

import io.harness.data.structure.EmptyPredicate;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.entity.FrozenExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class FrozenExecutionMapper {
  public FrozenExecution toFreezeWithExecution(Ambiance ambiance, List<FreezeSummaryResponseDTO> manualFreezeConfigs,
      List<FreezeSummaryResponseDTO> globalFreezeConfigs) {
    if (ambiance == null
        || (EmptyPredicate.isEmpty(manualFreezeConfigs) && EmptyPredicate.isEmpty(globalFreezeConfigs))) {
      return null;
    }
    return FrozenExecution.builder()
        .accountId(AmbianceUtils.getAccountId(ambiance))
        .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
        .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
        .pipelineId(AmbianceUtils.getPipelineIdentifier(ambiance))
        .planExecutionId(ambiance.getPlanExecutionId())
        .stageExecutionId(ambiance.getStageExecutionId())
        .stageNodeId(AmbianceUtils.getStageSetupIdAmbiance(ambiance))
        .stepNodeId(AmbianceUtils.obtainCurrentSetupId(ambiance))
        .stepExecutionId(AmbianceUtils.obtainCurrentRuntimeId(ambiance))
        .stepType(AmbianceUtils.getCurrentStepType(ambiance) == null
                ? null
                : AmbianceUtils.getCurrentStepType(ambiance).getType())
        .manualFreezeList(manualFreezeConfigs)
        .globalFreezeList(globalFreezeConfigs)
        .build();
  }
}
