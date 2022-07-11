/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dto.converter;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.converter.EdgeListConverter;
import io.harness.beans.internal.OrchestrationAdjacencyListInternal;
import io.harness.dto.OrchestrationAdjacencyListDTO;

import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class OrchestrationAdjacencyListDTOConverter {
  public OrchestrationAdjacencyListDTO convertFrom(OrchestrationAdjacencyListInternal adjacencyListInternal) {
    return OrchestrationAdjacencyListDTO.builder()
        .graphVertexMap(adjacencyListInternal.getGraphVertexMap().entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, m -> GraphVertexDTOConverter.toGraphVertexDTO.apply(m.getValue()))))
        .adjacencyMap(adjacencyListInternal.getAdjacencyMap().entrySet().stream().collect(Collectors.toMap(
            Map.Entry::getKey, edgeListInternal -> EdgeListConverter.convertFrom(edgeListInternal.getValue()))))
        .build();
  }
}
