/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.pms.pipeline.mappers.PipelineExecutionSummaryDtoMapper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.RollbackExecutionInfo;
import io.harness.pms.plan.execution.beans.dto.ChildExecutionDetailDTO;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionSummaryDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({PipelineExecutionSummaryDtoMapper.class})
public class RollbackGraphGeneratorTest extends CategoryTest {
  RollbackGraphGenerator rollbackGraphGenerator;
  @Mock PMSExecutionService executionService;
  Map<String, GraphLayoutNodeDTO> layoutNodeMap;

  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";
  String rollbackId = "rollbackModeId";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    rollbackGraphGenerator = new RollbackGraphGenerator(executionService);
    GraphLayoutNodeDTO deploymentGraph = GraphLayoutNodeDTO.builder().nodeType("Deployment").build();
    GraphLayoutNodeDTO prbStageGraph = GraphLayoutNodeDTO.builder().nodeType("PipelineRollback").build();
    layoutNodeMap = new HashMap<>();
    layoutNodeMap.put("id1", deploymentGraph);
    layoutNodeMap.put("id2", prbStageGraph);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCheckAndBuildRollbackGraph() {
    PipelineExecutionSummaryEntity noRollbackId = PipelineExecutionSummaryEntity.builder().build();
    ChildExecutionDetailDTO noRollbackIdResponse =
        rollbackGraphGenerator.checkAndBuildRollbackGraph(null, null, null, noRollbackId, null, null, null, null);
    assertThat(noRollbackIdResponse).isNull();
    PipelineExecutionSummaryEntity parentExecutionSummary =
        PipelineExecutionSummaryEntity.builder()
            .rollbackExecutionInfo(RollbackExecutionInfo.builder().rollbackModeExecutionId(rollbackId).build())
            .layoutNodeMap(layoutNodeMap)
            .build();
    PipelineExecutionSummaryEntity childExecutionSummary =
        PipelineExecutionSummaryEntity.builder().planExecutionId(rollbackId).build();
    doReturn(childExecutionSummary)
        .when(executionService)
        .getPipelineExecutionSummaryEntity(accountId, orgId, projectId, rollbackId);
    MockedStatic<PipelineExecutionSummaryDtoMapper> mockSettings1 =
        Mockito.mockStatic(PipelineExecutionSummaryDtoMapper.class);
    PipelineExecutionSummaryDTO rollbackExecutionDTO =
        PipelineExecutionSummaryDTO.builder().planExecutionId(rollbackId).build();
    when(PipelineExecutionSummaryDtoMapper.toDto(childExecutionSummary, null)).thenReturn(rollbackExecutionDTO);
    ChildExecutionDetailDTO responseWithoutDetails = rollbackGraphGenerator.checkAndBuildRollbackGraph(
        accountId, orgId, projectId, parentExecutionSummary, null, null, null, "id1");
    assertThat(responseWithoutDetails.getExecutionGraph()).isNull();
    assertThat(responseWithoutDetails.getPipelineExecutionSummary()).isEqualTo(rollbackExecutionDTO);
    mockSettings1.close();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testIsPipelineRollbackStageSelected() {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().layoutNodeMap(layoutNodeMap).build();
    assertThat(rollbackGraphGenerator.isPipelineRollbackStageSelected(executionSummaryEntity, "id1")).isFalse();
    assertThat(rollbackGraphGenerator.isPipelineRollbackStageSelected(executionSummaryEntity, "id2")).isTrue();
  }
}