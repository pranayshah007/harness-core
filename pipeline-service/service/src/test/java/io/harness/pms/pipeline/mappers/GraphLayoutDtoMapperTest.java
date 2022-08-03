/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.plan.execution.beans.dto.EdgeLayoutListDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class GraphLayoutDtoMapperTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToDto() {
    GraphLayoutNode graphLayoutNode =
        GraphLayoutNode.newBuilder()
            .setNodeIdentifier("nodeIdentifier")
            .setNodeType("Approval")
            .setNodeUUID("aBcDeFgH")
            .setName("Node name")
            .setNodeGroup("goodNodes")
            .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds("nextId").addCurrentNodeChildren("child").build())
            .build();
    GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(graphLayoutNode);
    assertThat(graphLayoutNodeDTO.getNodeIdentifier()).isEqualTo("nodeIdentifier");
    assertThat(graphLayoutNodeDTO.getNodeType()).isEqualTo("Approval");
    assertThat(graphLayoutNodeDTO.getNodeUuid()).isEqualTo("aBcDeFgH");
    assertThat(graphLayoutNodeDTO.getName()).isEqualTo("Node name");
    assertThat(graphLayoutNodeDTO.getNodeGroup()).isEqualTo("goodNodes");
    assertThat(graphLayoutNodeDTO.getStatus()).isEqualTo(ExecutionStatus.NOTSTARTED);
    EdgeLayoutListDTO edgeLayoutList = graphLayoutNodeDTO.getEdgeLayoutList();
    assertThat(edgeLayoutList.getCurrentNodeChildren()).hasSize(1);
    assertThat(edgeLayoutList.getCurrentNodeChildren()).contains("child");
    assertThat(edgeLayoutList.getNextIds()).hasSize(1);
    assertThat(edgeLayoutList.getNextIds()).contains("nextId");
  }
}
