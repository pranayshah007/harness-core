/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dto.converter;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EphemeralOrchestrationGraph;
import io.harness.dto.OrchestrationGraphDTO;

import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class OrchestrationGraphDTOConverter {
  public OrchestrationGraphDTO convertFrom(EphemeralOrchestrationGraph ephemeralOrchestrationGraph) {
    return OrchestrationGraphDTO.builder()
        .startTs(ephemeralOrchestrationGraph.getStartTs())
        .endTs(ephemeralOrchestrationGraph.getEndTs())
        .status(ephemeralOrchestrationGraph.getStatus())
        .rootNodeIds(ephemeralOrchestrationGraph.getRootNodeIds())
        .planExecutionId(ephemeralOrchestrationGraph.getPlanExecutionId())
        .adjacencyList(
            OrchestrationAdjacencyListDTOConverter.convertFrom(ephemeralOrchestrationGraph.getAdjacencyList()))
        .build();
  }
}
