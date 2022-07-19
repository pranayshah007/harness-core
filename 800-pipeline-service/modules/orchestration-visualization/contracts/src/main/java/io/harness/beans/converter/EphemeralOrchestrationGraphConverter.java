/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans.converter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EphemeralOrchestrationGraph;
import io.harness.beans.OrchestrationGraph;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class EphemeralOrchestrationGraphConverter {
  public EphemeralOrchestrationGraph convertFrom(OrchestrationGraph orchestrationGraph) {
    return EphemeralOrchestrationGraph.builder()
        .startTs(orchestrationGraph.getStartTs())
        .endTs(orchestrationGraph.getEndTs())
        .status(orchestrationGraph.getStatus())
        .rootNodeIds(orchestrationGraph.getRootNodeIds())
        .planExecutionId(orchestrationGraph.getPlanExecutionId())
        .adjacencyList(orchestrationGraph.getAdjacencyList())
        .build();
  }
}
