/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.dashboards;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.dashboard.DashboardPipelineExecutionInfo;
import io.harness.pms.dashboard.DashboardPipelineHealthInfo;
import io.harness.pms.dashboard.MeanAndMedian;
import io.harness.pms.dashboard.PipelineCountInfo;
import io.harness.pms.dashboard.PipelineExecutionInfo;
import io.harness.pms.dashboard.StatusAndTime;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.pipeline.service.PipelineDashboardQueryService;
import io.harness.pms.pipeline.service.PipelineDashboardServiceImpl;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class DashboardPipelineHealthAndExecutionInfoTest extends CategoryTest {
  @InjectMocks @Spy private PipelineDashboardServiceImpl pipelineDashboardService;
  @InjectMocks @Spy private PipelineDashboardQueryService pipelineDashboardQueryService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetDashboardPipelineHealthInfoTest() {
    // this test tests only total and success components of Executions  and duration part of mean and median under
    // DashboardPipelineHealthInfo

    long startInterval = 1617235200000L;
    long endInterval = 1619740800000L;
    long previousStartInterval = 1614556800000L;

    List<StatusAndTime> statusAndTime = Arrays.asList(new StatusAndTime(ExecutionStatus.SUCCESS.name(), 1617624465000L),
        new StatusAndTime(ExecutionStatus.FAILED.name(), 1619006865000L),
        new StatusAndTime(ExecutionStatus.SUCCESS.name(), 1619721000000L),
        new StatusAndTime(ExecutionStatus.SUCCESS.name(), 1617129000000L),
        new StatusAndTime(ExecutionStatus.SUCCESS.name(), 1617235200000L),
        new StatusAndTime(ExecutionStatus.ABORTED.name(), 1616610600000L),
        new StatusAndTime(ExecutionStatus.SUCCESS.name(), 1616697939000L));

    doReturn(statusAndTime)
        .when(pipelineDashboardQueryService)
        .getPipelineExecutionStatusAndTime(
            anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString());
    doReturn(new MeanAndMedian(0L, 0L))
        .when(pipelineDashboardQueryService)
        .getPipelineExecutionMeanAndMedianDuration(
            anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString());

    DashboardPipelineHealthInfo dashboardPipelineHealthInfo = pipelineDashboardService.getDashboardPipelineHealthInfo(
        "ac", "or", "pr", "pip", startInterval, endInterval, previousStartInterval, "CI");

    // total count = 4, Previous total count = 3, current success = 3, previous success = 2
    assertThat(dashboardPipelineHealthInfo.getExecutions().getTotal().getCount()).isEqualTo(4);
    assertThat(dashboardPipelineHealthInfo.getExecutions().getTotal().getRate()).isEqualTo((1 / (double) 3) * 100);
    assertThat(dashboardPipelineHealthInfo.getExecutions().getSuccess().getPercent()).isEqualTo((3 / (double) 4) * 100);
    assertThat(dashboardPipelineHealthInfo.getExecutions().getSuccess().getRate())
        .isEqualTo(((3 - 2) / (double) 2) * 100);

    // test for empty status and time
    doReturn(Arrays.asList(new StatusAndTime(null, 0)))
        .when(pipelineDashboardQueryService)
        .getPipelineExecutionStatusAndTime(
            anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString());

    DashboardPipelineHealthInfo dashboardPipelineHealthInfoEmptyList =
        pipelineDashboardService.getDashboardPipelineHealthInfo(
            "ac", "or", "pr", "pip", 1617235200000L, 1619740800000L, 1614556800000L, "CI");
    assertThat(dashboardPipelineHealthInfoEmptyList.getExecutions().getTotal().getCount()).isEqualTo(0);
    assertThat(dashboardPipelineHealthInfoEmptyList.getExecutions().getTotal().getRate()).isEqualTo(0.0);
    assertThat(dashboardPipelineHealthInfoEmptyList.getExecutions().getSuccess().getPercent()).isEqualTo(0.0);
    assertThat(dashboardPipelineHealthInfoEmptyList.getExecutions().getSuccess().getRate()).isEqualTo(0.0);

    // Mean And Median duration test
    String table = "pipeline_execution_summary_ci";

    // currentMean
    doReturn(new MeanAndMedian(100L, 150L))
        .when(pipelineDashboardQueryService)
        .getPipelineExecutionMeanAndMedianDuration("ac", "or", "pr", "pip", startInterval, 1619827200000L, table);

    // PreviousMean
    doReturn(new MeanAndMedian(40L, 180L))
        .when(pipelineDashboardQueryService)
        .getPipelineExecutionMeanAndMedianDuration(
            "ac", "or", "pr", "pip", previousStartInterval, startInterval, table);

    DashboardPipelineHealthInfo dashboardPipelineHealthInfoMeanMedian =
        pipelineDashboardService.getDashboardPipelineHealthInfo(
            "ac", "or", "pr", "pip", 1617235200000L, 1619740800000L, 1614556800000L, "CI");
    assertThat(dashboardPipelineHealthInfoMeanMedian.getExecutions().getMeanInfo().getDuration()).isEqualTo(100L);
    assertThat(dashboardPipelineHealthInfoMeanMedian.getExecutions().getMeanInfo().getRate()).isEqualTo(60L);
    assertThat(dashboardPipelineHealthInfoMeanMedian.getExecutions().getMedianInfo().getDuration()).isEqualTo(150L);
    assertThat(dashboardPipelineHealthInfoMeanMedian.getExecutions().getMedianInfo().getRate()).isEqualTo(-30L);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetDashboardPipelineExecutionInfo() {
    long startInterval = 1617235200000L;
    long endInterval = 1617580800000L;

    List<StatusAndTime> statusAndTime = Arrays.asList(new StatusAndTime(ExecutionStatus.SUCCESS.name(), 1617365265000L),
        new StatusAndTime(ExecutionStatus.IGNOREFAILED.name(), 1617365265000L),
        new StatusAndTime(ExecutionStatus.FAILED.name(), 1617365265000L),
        new StatusAndTime(ExecutionStatus.ABORTED.name(), 1617321600000L),
        new StatusAndTime(ExecutionStatus.RUNNING.name(), 1617580800000L),
        new StatusAndTime(ExecutionStatus.FAILED.name(), 1617408000000L),
        new StatusAndTime(ExecutionStatus.ABORTED.name(), 1617235200000L),
        new StatusAndTime(ExecutionStatus.EXPIRED.name(), 1617302739000L));

    doReturn(statusAndTime)
        .when(pipelineDashboardQueryService)
        .getPipelineExecutionStatusAndTime(
            anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong(), anyString());

    DashboardPipelineExecutionInfo dashboardPipelineExecutionInfo =
        pipelineDashboardService.getDashboardPipelineExecutionInfo(
            "ac", "or", "pr", "pip", startInterval, endInterval, "CI");

    List<PipelineExecutionInfo> pipelineExecutionInfoList = new ArrayList<>();
    pipelineExecutionInfoList.add(
        PipelineExecutionInfo.builder()
            .date(1617235200000L)
            .count(PipelineCountInfo.builder().total(2).success(0).failure(0).aborted(1).expired(1).build())
            .build());
    pipelineExecutionInfoList.add(
        PipelineExecutionInfo.builder()
            .date(1617321600000L)
            .count(PipelineCountInfo.builder().total(4).success(2).failure(1).aborted(1).build())
            .build());
    pipelineExecutionInfoList.add(PipelineExecutionInfo.builder()
                                      .date(1617408000000L)
                                      .count(PipelineCountInfo.builder().total(1).success(0).failure(1).build())
                                      .build());
    pipelineExecutionInfoList.add(PipelineExecutionInfo.builder()
                                      .date(1617494400000L)
                                      .count(PipelineCountInfo.builder().total(0).success(0).failure(0).build())
                                      .build());
    pipelineExecutionInfoList.add(PipelineExecutionInfo.builder()
                                      .date(1617580800000L)
                                      .count(PipelineCountInfo.builder().total(1).success(0).failure(0).build())
                                      .build());
    assertThat(dashboardPipelineExecutionInfo.getPipelineExecutionInfoList()).isEqualTo(pipelineExecutionInfoList);
  }
}
