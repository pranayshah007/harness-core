/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dto.converter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.GraphVertex;
import io.harness.data.structure.CollectionUtils;
import io.harness.dto.GraphVertexDTO;
import io.harness.dto.GraphVertexDTO.GraphVertexDTOBuilder;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;

import java.util.function.Function;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class GraphVertexDTOConverter {
  public Function<GraphVertex, GraphVertexDTO> toGraphVertexDTO = graphVertex -> {
    Level level = AmbianceUtils.obtainCurrentLevel(graphVertex.getAmbiance());
    GraphVertexDTOBuilder builder =
        GraphVertexDTO.builder()
            .uuid(graphVertex.getUuid())
            .ambiance(AmbianceDTOConverter.toAmbianceDTO.apply(graphVertex.getAmbiance()))
            .planNodeId(graphVertex.getPlanNodeId())
            .identifier(graphVertex.getIdentifier())
            .name(graphVertex.getName())
            .startTs(graphVertex.getStartTs())
            .endTs(graphVertex.getEndTs())
            .initialWaitDuration(graphVertex.getInitialWaitDuration())
            .lastUpdatedAt(graphVertex.getLastUpdatedAt())
            .stepType(graphVertex.getStepType())
            .status(graphVertex.getStatus())
            .failureInfo(FailureInfoDTOConverter.toFailureInfoDTO(graphVertex.getFailureInfo()))
            .skipInfo(graphVertex.getSkipInfo())
            .nodeRunInfo(graphVertex.getNodeRunInfo())
            .stepParameters(graphVertex.getPmsStepParameters())
            .mode(graphVertex.getMode())
            .executableResponses(CollectionUtils.emptyIfNull(graphVertex.getExecutableResponses()))
            .graphDelegateSelectionLogParams(
                CollectionUtils.emptyIfNull(graphVertex.getGraphDelegateSelectionLogParams()))
            .interruptHistories(graphVertex.getInterruptHistories())
            .retryIds(graphVertex.getRetryIds())
            .skipType(graphVertex.getSkipType())
            .outcomes(graphVertex.getPmsOutcomes())
            .unitProgresses(graphVertex.getUnitProgresses())
            .progressData(graphVertex.getPmsProgressData())
            .executionInputConfigured(graphVertex.getExecutionInputConfigured())
            .logBaseKey(graphVertex.getLogBaseKey())
            .stepDetails(graphVertex.getStepDetails());
    if (level != null && level.hasStrategyMetadata()) {
      builder.strategyMetadata(level.getStrategyMetadata());
    }
    return builder.build();
  };
}
