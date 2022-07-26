/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.NGDateUtils.DAY_IN_MS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.Dashboard.DashboardPipelineExecutionInfo;
import io.harness.pms.Dashboard.DashboardPipelineHealthInfo;
import io.harness.pms.Dashboard.MeanMedianInfo;
import io.harness.pms.Dashboard.PipelineCountInfo;
import io.harness.pms.Dashboard.PipelineExecutionInfo;
import io.harness.pms.Dashboard.PipelineHealthInfo;
import io.harness.pms.Dashboard.StatusAndTime;
import io.harness.pms.Dashboard.SuccessHealthInfo;
import io.harness.pms.Dashboard.TotalHealthInfo;
import io.harness.pms.execution.ExecutionStatus;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class PipelineDashboardServiceImpl implements PipelineDashboardService {
  @Inject PipelineDashboardQueryService pipelineDashboardQueryService;

  private String tableName_default = "pipeline_execution_summary_ci";
  private String CI_TableName = "pipeline_execution_summary_ci";
  private String CD_TableName = "pipeline_execution_summary_cd";
  private List<String> failedList = Arrays.asList(ExecutionStatus.FAILED.name(), ExecutionStatus.ABORTED.name(),
      ExecutionStatus.EXPIRED.name(), ExecutionStatus.IGNOREFAILED.name(), ExecutionStatus.ERRORED.name());

  public double getRate(long current, long previous) {
    double rate = 0.0;
    if (previous != 0) {
      rate = (current - previous) / (double) previous;
    }
    rate = rate * 100.0;
    return rate;
  }

  public double successPercent(long success, long total) {
    double rate = 0.0;
    if (total != 0) {
      rate = success / (double) total;
    }
    rate = rate * 100.0;
    return rate;
  }

  public String selectTableFromModuleInfo(String moduleInfo) {
    String tableName = tableName_default;
    if (moduleInfo.equalsIgnoreCase("CI")) {
      tableName = CI_TableName;
    } else if (moduleInfo.equalsIgnoreCase("CD")) {
      tableName = CD_TableName;
    }
    return tableName;
  }

  @Override
  public DashboardPipelineHealthInfo getDashboardPipelineHealthInfo(String accountId, String orgId, String projectId,
      String pipelineId, long startInterval, long endInterval, long previousStartInterval, String moduleInfo) {
    startInterval = getStartingDateEpochValue(startInterval);
    endInterval = getStartingDateEpochValue(endInterval);
    previousStartInterval = getStartingDateEpochValue(previousStartInterval);

    endInterval = endInterval + DAY_IN_MS;

    String tableName = selectTableFromModuleInfo(moduleInfo);

    List<StatusAndTime> statusAndTime = pipelineDashboardQueryService.getPipelineExecutionStatusAndTime(
        accountId, orgId, projectId, pipelineId, previousStartInterval, endInterval, tableName);

    long currentTotal = 0, currentSuccess = 0;
    long previousTotal = 0, previousSuccess = 0;
    for (int i = 0; i < statusAndTime.size(); i++) {
      long variableEpoch = statusAndTime.get(i).getStartts();
      if (variableEpoch >= startInterval && variableEpoch < endInterval) {
        currentTotal++;
        if (statusAndTime.get(i).getStatus().contentEquals(ExecutionStatus.SUCCESS.name())) {
          currentSuccess++;
        }
      }

      // previous interval record
      if (previousStartInterval <= variableEpoch && startInterval > variableEpoch) {
        previousTotal++;
        if (statusAndTime.get(i).getStatus().contentEquals(ExecutionStatus.SUCCESS.name())) {
          previousSuccess++;
        }
      }
    }

    // mean calculation
    long currentMean = pipelineDashboardQueryService.getPipelineExecutionMeanDuration(
        accountId, orgId, projectId, pipelineId, startInterval, endInterval, tableName);

    long previousMean = pipelineDashboardQueryService.getPipelineExecutionMeanDuration(
        accountId, orgId, projectId, pipelineId, previousStartInterval, startInterval, tableName);

    // Median calculation
    long currentMedian = pipelineDashboardQueryService.getPipelineExecutionMedianDuration(
        accountId, orgId, projectId, pipelineId, startInterval, endInterval, tableName);

    long previousMedian = pipelineDashboardQueryService.getPipelineExecutionMedianDuration(
        accountId, orgId, projectId, pipelineId, previousStartInterval, startInterval, tableName);

    return DashboardPipelineHealthInfo.builder()
        .executions(
            PipelineHealthInfo.builder()
                .total(TotalHealthInfo.builder().count(currentTotal).rate(getRate(currentTotal, previousTotal)).build())
                .success(SuccessHealthInfo.builder()
                             .rate(getRate(currentSuccess, previousSuccess))
                             .percent(successPercent(currentSuccess, currentTotal))
                             .build())
                .meanInfo(MeanMedianInfo.builder().duration(currentMean).rate(currentMean - previousMean).build())
                .medianInfo(
                    MeanMedianInfo.builder().duration(currentMedian).rate(currentMedian - previousMedian).build())
                .build())
        .build();
  }

  @Override
  public DashboardPipelineExecutionInfo getDashboardPipelineExecutionInfo(String accountId, String orgId,
      String projectId, String pipelineId, long startInterval, long endInterval, String moduleInfo) {
    startInterval = getStartingDateEpochValue(startInterval);
    endInterval = getStartingDateEpochValue(endInterval);

    endInterval = endInterval + DAY_IN_MS;

    String tableName = selectTableFromModuleInfo(moduleInfo);

    List<StatusAndTime> statusAndTime = pipelineDashboardQueryService.getPipelineExecutionStatusAndTime(
        accountId, orgId, projectId, pipelineId, startInterval, endInterval, tableName);

    List<PipelineExecutionInfo> pipelineExecutionInfoList = new ArrayList<>();

    while (startInterval < endInterval) {
      long total = 0, success = 0, failed = 0;
      for (int i = 0; i < statusAndTime.size(); i++) {
        if (startInterval == getStartingDateEpochValue(statusAndTime.get(i).getStartts())) {
          total++;
          if (statusAndTime.get(i).getStatus().contentEquals(ExecutionStatus.SUCCESS.name())) {
            success++;
          } else if (failedList.contains(statusAndTime.get(i).getStatus())) {
            failed++;
          }
        }
      }
      pipelineExecutionInfoList.add(
          PipelineExecutionInfo.builder()
              .date(startInterval)
              .count(PipelineCountInfo.builder().total(total).success(success).failure(failed).build())
              .build());
      startInterval = startInterval + DAY_IN_MS;
    }

    return DashboardPipelineExecutionInfo.builder().pipelineExecutionInfoList(pipelineExecutionInfoList).build();
  }

  public long getStartingDateEpochValue(long epochValue) {
    return epochValue - epochValue % DAY_IN_MS;
  }
}
