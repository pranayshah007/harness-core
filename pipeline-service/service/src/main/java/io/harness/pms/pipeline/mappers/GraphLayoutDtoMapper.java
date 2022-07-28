/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.dto.EdgeLayoutListDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GraphLayoutDtoMapper {
  public GraphLayoutNodeDTO toDto(GraphLayoutNode graphLayoutNode) {
    return GraphLayoutNodeDTO.builder()
        .nodeIdentifier(graphLayoutNode.getNodeIdentifier())
        .nodeType(graphLayoutNode.getNodeType())
        .nodeUuid(graphLayoutNode.getNodeUUID())
        .status(ExecutionStatus.NOTSTARTED)
        .name(graphLayoutNode.getName())
        .nodeGroup(graphLayoutNode.getNodeGroup())
        .edgeLayoutList(toDto(graphLayoutNode.getEdgeLayoutList()))
        .build();
  }

  private EdgeLayoutListDTO toDto(EdgeLayoutList edgeLayoutList) {
    return EdgeLayoutListDTO.builder()
        .currentNodeChildren(edgeLayoutList.getCurrentNodeChildrenList())
        .nextIds(edgeLayoutList.getNextIdsList())
        .build();
  }
}
