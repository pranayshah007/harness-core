package io.harness.cvng.servicelevelobjective.beans;

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
  @NotNull String monitoredServiceIdentifier;
  @NotNull String monitoredServiceName;
  @NotNull String healthSourceIdentifier;
  @NotNull String healthSourceName;
  Map<String, String> tags;
  @NotNull ServiceLevelIndicatorType type;
  @NotNull BurnRate burnRate;
  @NotNull int timeRemainingDays;
  @NotNull double errorBudgetRemainingPercentage;
  @NotNull int errorBudgetRemaining;
  @NotNull int totalErrorBudget;
  @NotNull SLOTargetType sloTargetType;
  @NotNull int currentPeriodLengthDays;
  @NotNull long currentPeriodStartTime;
  @NotNull long currentPeriodEndTime;
  @NotNull double sloTargetPercentage;
  @NotNull List<Point> errorBudgetBurndown;
  @NotNull List<Point> sloPerformanceTrend;
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
  }

  public static SLODashboardWidgetBuilder withGraphData(SLOGraphData sloGraphData) {
    return SLODashboardWidget.builder()
        .errorBudgetRemaining(sloGraphData.getErrorBudgetRemaining())
        .errorBudgetRemainingPercentage(sloGraphData.getErrorBudgetRemainingPercentage())
        .errorBudgetBurndown(sloGraphData.getErrorBudgetBurndown())
        .sloPerformanceTrend(sloGraphData.getSloPerformanceTrend());
  }
  @Value
  @Builder
  public static class SLOGraphData {
    double errorBudgetRemainingPercentage;
    int errorBudgetRemaining;
    List<Point> errorBudgetBurndown;
    List<Point> sloPerformanceTrend;
    public double errorBudgetSpentPercentage() {
      return 100 - errorBudgetRemainingPercentage;
    }
  }
}
