/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.dashboard;

import static io.harness.NGDateUtils.DAY_IN_MS;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.pipeline.service.PipelineDashboardService;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PipelineDashboardOverviewResourceImplTest {
  @InjectMocks PipelineDashboardOverviewResourceImpl pipelineDashboardOverviewResourceImpl;
  @Mock PipelineDashboardService pipelineDashboardService;

  private String ACC_ID = "acc_id";
  private String ORG_ID = "org_id";
  private String PRO_ID = "pro_id";
  private Long START_TIME = 3 * DAY_IN_MS;
  private Long END_TIME = 5 * DAY_IN_MS;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testFetchPipelinedHealth() {
    PipelineHealthInfo pipelineHealthInfo =
        PipelineHealthInfo.builder().total(TotalHealthInfo.builder().count(5L).build()).build();

    doReturn(DashboardPipelineHealthInfo.builder().executions(pipelineHealthInfo).build())
        .when(pipelineDashboardService)
        .getDashboardPipelineHealthInfo(ACC_ID, ORG_ID, PRO_ID, "pip", START_TIME, END_TIME,
            START_TIME - (END_TIME - START_TIME) - DAY_IN_MS, "cd");
    ResponseDTO<DashboardPipelineHealthInfo> healthDto = pipelineDashboardOverviewResourceImpl.getPipelinedHealth(
        ACC_ID, ORG_ID, PRO_ID, "pip", "cd", START_TIME, END_TIME);

    assertThat(healthDto.getData().getExecutions().getTotal().getCount()).isEqualTo(5L);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetPipelineDashboardExecution() {
    DashboardPipelineExecutionInfo pipelineExecutionInfo =
        DashboardPipelineExecutionInfo.builder()
            .pipelineExecutionInfoList(Collections.singletonList(
                PipelineExecutionInfo.builder()
                    .date(10L)
                    .count(PipelineCountInfo.builder().failure(2L).total(3L).success(1L).build())
                    .build()))
            .build();

    doReturn(pipelineExecutionInfo)
        .when(pipelineDashboardService)
        .getDashboardPipelineExecutionInfo(ACC_ID, ORG_ID, PRO_ID, "pip", START_TIME, END_TIME, "cd");
    ResponseDTO<DashboardPipelineExecutionInfo> executionDto =
        pipelineDashboardOverviewResourceImpl.getPipelineExecution(
            ACC_ID, ORG_ID, PRO_ID, "pip", "cd", START_TIME, END_TIME);
    assertThat(executionDto.getData().getPipelineExecutionInfoList().get(0).getDate()).isEqualTo(10L);
    assertThat(executionDto.getData().getPipelineExecutionInfoList().get(0).getCount().getTotal()).isEqualTo(3L);
  }
}
