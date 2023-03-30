/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SLODashboardWidget {
  @NotNull String sloIdentifier;
  @NotNull String title;
  String monitoredServiceIdentifier;
  String monitoredServiceName;
  String healthSourceIdentifier;
  String healthSourceName;
  String serviceIdentifier;
  String serviceName;
  String environmentIdentifier;
  String environmentName;
  List<MonitoredServiceDetail> monitoredServiceDetails;
  Map<String, String> tags;
  ServiceLevelIndicatorType type;
  SLIEvaluationType evaluationType;
  @NotNull ServiceLevelObjectiveType sloType;
  @NotNull BurnRate burnRate;
  @NotNull int timeRemainingDays;
  @NotNull double errorBudgetRemainingPercentage;
  @NotNull
  public ErrorBudgetRisk getErrorBudgetRisk() {
    return ErrorBudgetRisk.getFromPercentage(errorBudgetRemainingPercentage);
  }
  @NotNull long errorBudgetRemaining;
  @NotNull int totalErrorBudget;
  @NotNull SLOTargetType sloTargetType;
  @NotNull int currentPeriodLengthDays;
  @NotNull long currentPeriodStartTime;
  @NotNull long currentPeriodEndTime;
  @NotNull double sloTargetPercentage;
  @NotNull List<Point> errorBudgetBurndown;
  @NotNull List<Point> sloPerformanceTrend;
  @NotNull boolean isRecalculatingSLI;
  @NotNull boolean isCalculatingSLI;
  @Value
  @Builder
  public static class BurnRate {
    @NotNull double currentRatePercentage; // rate per day for the current period
  }

  @Value
  @Builder
  public static class Point {
    long timestamp;
    double value;
    boolean enabled;
  }

  @Value
  @Builder
  public static class SLOGraphData {
    double errorBudgetRemainingPercentage;
    long errorBudgetRemaining;
    List<Point> errorBudgetBurndown;
    List<Point> sloPerformanceTrend;
    boolean isRecalculatingSLI;
    boolean isCalculatingSLI;
    @JsonIgnore long errorBudgetBurned;
    @JsonIgnore double sliStatusPercentage;
    @JsonIgnore SLIEvaluationType evaluationType;
    public double errorBudgetSpentPercentage() {
      return 100 - errorBudgetRemainingPercentage;
    }

    public double dailyBurnRate(ZoneId zoneId) {
      if (isEmpty(sloPerformanceTrend)) {
        return 0;
      } else {
        Instant startTime = Instant.ofEpochMilli(sloPerformanceTrend.get(0).getTimestamp());
        Instant endTime = Instant.ofEpochMilli(sloPerformanceTrend.get(sloPerformanceTrend.size() - 1).getTimestamp());
        long days =
            ChronoUnit.DAYS.between(startTime.atZone(zoneId).toLocalDate(), endTime.atZone(zoneId).toLocalDate()) + 1;
        return (errorBudgetSpentPercentage()) / days;
      }
    }
  }

  public static SLODashboardWidgetBuilder withGraphData(SLOGraphData sloGraphData) {
    return SLODashboardWidget.builder()
        .isRecalculatingSLI(sloGraphData.isRecalculatingSLI())
        .isCalculatingSLI(sloGraphData.isCalculatingSLI)
        .errorBudgetRemaining(sloGraphData.getErrorBudgetRemaining())
        .errorBudgetRemainingPercentage(sloGraphData.getErrorBudgetRemainingPercentage())
        .errorBudgetBurndown(sloGraphData.getErrorBudgetBurndown())
        .sloPerformanceTrend(sloGraphData.getSloPerformanceTrend());
  }
}
