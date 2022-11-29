/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService;

import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.core.beans.params.TimeRangeParams;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
public class HistoricalTrend {
  List<RiskData> healthScores;

  @Builder
  public HistoricalTrend(int size, Instant trendStartTime, Instant trendEndTime) {
    Preconditions.checkState(size != 0, "Cannot create historical trend with 0 Health Scores");
    Preconditions.checkState(trendStartTime != null && trendEndTime != null,
        "Cannot create historical trend with start time or end time as null");

    healthScores = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      healthScores.add(RiskData.builder().riskStatus(Risk.NO_DATA).healthScore(null).build());
    }

    long windowInMillis = (trendEndTime.toEpochMilli() - trendStartTime.toEpochMilli()) / size;

    for (RiskData riskData : healthScores) {
      TimeRangeParams timeRangeParams = TimeRangeParams.builder()
                                            .startTime(trendStartTime)
                                            .endTime(trendStartTime.plusMillis(windowInMillis))
                                            .build();
      riskData.setTimeRangeParams(timeRangeParams);
      riskData.setStartTime(trendStartTime.toEpochMilli());
      riskData.setEndTime(trendStartTime.plusMillis(windowInMillis).toEpochMilli());
      trendStartTime = trendStartTime.plusMillis(windowInMillis);
    }
  }

  public void reduceHealthScoreDataToXPoints(int x) {
    Preconditions.checkState(healthScores != null, "HealthScores cannot be null");
    Preconditions.checkState(healthScores.size() != 0 && healthScores.size() % x == 0,
        String.format("cannot group historical trend with size %s to %s points", this.healthScores.size(), x));

    List<RiskData> reducedHealthScores = new ArrayList<>();
    final List<List<RiskData>> batch = Lists.partition(healthScores, x);

    for (List<RiskData> list : batch) {
      RiskData maxRiskData = RiskData.builder().riskStatus(Risk.NO_DATA).healthScore(null).build();
      for (RiskData riskData : list) {
        RiskData tempMaxRiskData = Collections.max(Arrays.asList(maxRiskData, riskData));
        maxRiskData.setRiskStatus(tempMaxRiskData.getRiskStatus());
        maxRiskData.setHealthScore(tempMaxRiskData.getHealthScore());
      }
      long startTime = list.get(0).getStartTime();
      long endTime = list.get(list.size() - 1).getEndTime();
      maxRiskData.setTimeRangeParams(TimeRangeParams.builder()
                                         .startTime(Instant.ofEpochMilli(startTime))
                                         .endTime(Instant.ofEpochMilli(endTime))
                                         .build());
      maxRiskData.setStartTime(startTime);
      maxRiskData.setEndTime(endTime);
      reducedHealthScores.add(maxRiskData);
    }
    this.setHealthScores(reducedHealthScores);
  }
}
