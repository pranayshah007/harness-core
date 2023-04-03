/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.budget;

import static io.harness.ccm.budget.BudgetPeriod.DAILY;
import static io.harness.ccm.budget.utils.BudgetUtils.MONTHS;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.AFTER;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.BEFORE;
import static io.harness.ccm.views.graphql.QLCEViewTimeGroupType.DAY;
import static io.harness.ccm.views.graphql.QLCEViewTimeGroupType.MONTH;
import static io.harness.ccm.views.graphql.QLCEViewTimeGroupType.QUARTER;
import static io.harness.ccm.views.graphql.QLCEViewTimeGroupType.WEEK;
import static io.harness.ccm.views.graphql.QLCEViewTimeGroupType.YEAR;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static org.joda.time.Days.daysBetween;
import static org.joda.time.Months.monthsBetween;

import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.ccm.commons.entities.budget.BudgetCostData;
import io.harness.ccm.commons.entities.budget.BudgetData;
import io.harness.ccm.graphql.core.perspectives.PerspectiveTimeSeriesHelper;
import io.harness.ccm.views.dto.TimeSeriesDataPoints;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewTimeGroupType;
import io.harness.ccm.views.graphql.QLCEViewTimeTruncGroupBy;
import io.harness.ccm.views.graphql.ViewCostData;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.service.ViewsBillingService;
import io.harness.ccm.views.service.impl.ClickHouseViewsBillingServiceImpl;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.joda.time.DateTime;

@Slf4j
public class BudgetCostServiceImpl implements BudgetCostService {
  @Inject ViewsBillingService viewsBillingService;
  @Inject ViewsQueryHelper viewsQueryHelper;
  @Inject PerspectiveTimeSeriesHelper perspectiveTimeSeriesHelper;
  @Inject @Nullable @Named("clickHouseConfig") ClickHouseConfig clickHouseConfig;
  @Inject ClickHouseService clickHouseService;
  @Inject ClickHouseViewsBillingServiceImpl clickHouseViewsBillingService;
  @Inject @Named("isClickHouseEnabled") boolean isClickHouseEnabled;

  @Override
  public double getActualCost(Budget budget) {
    return getActualCost(budget.getAccountId(), BudgetUtils.getPerspectiveIdForBudget(budget),
        BudgetUtils.getBudgetStartTime(budget.getStartTime(), budget.getPeriod()), budget.getPeriod());
  }

  @Override
  public double getActualCost(String accountId, String perspectiveId, long startOfPeriod, BudgetPeriod period) {
    long endTime = BudgetUtils.getEndTimeForBudget(startOfPeriod, period) - BudgetUtils.ONE_DAY_MILLIS;
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(viewsQueryHelper.getViewMetadataFilter(perspectiveId));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(startOfPeriod, AFTER));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(endTime, BEFORE));
    return getCostForPerspectiveBudget(filters, accountId);
  }

  @Override
  public double getForecastCost(Budget budget) {
    return getForecastCost(budget.getAccountId(), BudgetUtils.getPerspectiveIdForBudget(budget), budget.getStartTime(),
        budget.getPeriod());
  }

  @Override
  public double getForecastCost(String accountId, String perspectiveId, long startOfPeriod, BudgetPeriod period) {
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    long startTime = BudgetUtils.getStartTimeForForecasting();
    long endTime = BudgetUtils.getEndTimeForBudget(startOfPeriod, period) - BudgetUtils.ONE_DAY_MILLIS;
    filters.add(viewsQueryHelper.getViewMetadataFilter(perspectiveId));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(startTime, AFTER));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(endTime, BEFORE));
    ViewCostData costDataForForecast =
        ViewCostData.builder()
            .cost(viewsBillingService
                      .getCostData(filters, viewsQueryHelper.getPerspectiveTotalCostAggregation(),
                          viewsQueryHelper.buildQueryParams(accountId, false))
                      .getCost())
            .minStartTime(1000 * startTime)
            .maxStartTime(1000 * BudgetUtils.getStartOfCurrentDay() - BudgetUtils.ONE_DAY_MILLIS)
            .build();

    if (period == DAILY) {
      return viewsQueryHelper.getRoundedDoubleValue(costDataForForecast.getCost()
          / (Double.valueOf((costDataForForecast.getMaxStartTime() - costDataForForecast.getMinStartTime())
              / (1000 * (double) BudgetUtils.ONE_DAY_MILLIS))));
    }

    double costTillNow = getActualCost(accountId, perspectiveId, startOfPeriod, period);
    return viewsQueryHelper.getRoundedDoubleValue(
        costTillNow + viewsQueryHelper.getForecastCost(costDataForForecast, Instant.ofEpochMilli(endTime)));
  }

  @Override
  public double getLastPeriodCost(Budget budget) {
    return getLastPeriodCost(budget.getAccountId(), budget.getScope().getEntityIds().get(0),
        BudgetUtils.getBudgetStartTime(budget.getStartTime(), budget.getPeriod()), budget.getPeriod());
  }

  @Override
  public double getLastPeriodCost(String accountId, String perspectiveId, long startOfPeriod, BudgetPeriod period) {
    long startTime = BudgetUtils.getStartOfLastPeriod(startOfPeriod, period);
    long endTime = startOfPeriod - BudgetUtils.ONE_DAY_MILLIS;
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(viewsQueryHelper.getViewMetadataFilter(perspectiveId));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(startTime, AFTER));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(endTime, BEFORE));
    return getCostForPerspectiveBudget(filters, accountId);
  }

  @Override
  public Double[] getLastYearMonthlyCost(Budget budget) {
    return getLastYearMonthlyCost(budget.getAccountId(), budget.getScope().getEntityIds().get(0),
        BudgetUtils.getBudgetStartTime(budget.getStartTime(), budget.getPeriod()), budget.getPeriod());
  }

  @Override
  public Double[] getLastYearMonthlyCost(String accountId, String perspectiveId, long startTime, BudgetPeriod period) {
    long updatedStartTime = BudgetUtils.getStartOfMonthGivenTime(startTime);
    long endTime = updatedStartTime - BudgetUtils.ONE_DAY_MILLIS;
    updatedStartTime = BudgetUtils.getStartOfLastPeriod(updatedStartTime, period);
    List<ValueDataPoint> result =
        actualCostGroupedByPeriod(accountId, BudgetPeriod.MONTHLY, perspectiveId, updatedStartTime, endTime);

    Double[] lastYearMonthlyCost = new Double[MONTHS];
    Arrays.fill(lastYearMonthlyCost, 0.0D);
    int monthDiff = 0;
    if (result.size() > 0) {
      monthDiff = monthsBetween(new DateTime(updatedStartTime), new DateTime(result.get(0).getTime())).getMonths();
    }
    for (ValueDataPoint valueDataPoint : result) {
      lastYearMonthlyCost[monthDiff] = valueDataPoint.getValue();
      monthDiff++;
    }
    return lastYearMonthlyCost;
  }

  @Override
  public Double[] getActualMonthlyCost(Budget budget) {
    return getActualMonthlyCost(budget.getAccountId(), BudgetUtils.getPerspectiveIdForBudget(budget),
        BudgetUtils.getBudgetStartTime(budget.getStartTime(), budget.getPeriod()), budget.getPeriod());
  }

  @Override
  public Double[] getActualMonthlyCost(
      String accountId, String perspectiveId, long startOfPeriod, BudgetPeriod period) {
    long startTime = BudgetUtils.getStartOfMonthGivenTime(startOfPeriod);
    long endTime = BudgetUtils.getEndTimeForBudget(startTime, period) - BudgetUtils.ONE_DAY_MILLIS;
    List<ValueDataPoint> result =
        actualCostGroupedByPeriod(accountId, BudgetPeriod.MONTHLY, perspectiveId, startTime, endTime);

    Double[] actualMonthlyCost = new Double[MONTHS];
    Arrays.fill(actualMonthlyCost, 0.0D);
    for (int i = 0; i < result.size(); i++) {
      actualMonthlyCost[i] = result.get(i).getValue();
    }
    return actualMonthlyCost;
  }

  @Override
  public Double[] getForecastMonthlyCost(Budget budget) {
    return getForecastMonthlyCost(budget.getAccountId(), BudgetUtils.getPerspectiveIdForBudget(budget),
        budget.getStartTime(), budget.getPeriod());
  }

  @Override
  public Double[] getForecastMonthlyCost(
      String accountId, String perspectiveId, long startOfPeriod, BudgetPeriod period) {
    SimpleRegression regression = new SimpleRegression();

    // Getting last year actual cost data and feeding it to our model
    // We will fit last year, first month of budget period cost on -12
    // Second month on -11, and so on upto -1...
    Double[] lastYearCost = getLastYearMonthlyCost(accountId, perspectiveId, startOfPeriod, period);
    for (int lastYearMonth = 0; lastYearMonth < MONTHS; lastYearMonth++) {
      regression.addData(-(MONTHS - lastYearMonth), lastYearCost[lastYearMonth]);
    }

    // Filling result with actual cost
    Double[] result = getActualMonthlyCost(accountId, perspectiveId, startOfPeriod, period);

    // In case of future budgets where startOfPeriod is set in some time future
    long forecastTime = max(BudgetUtils.getStartOfCurrentDay(), BudgetUtils.getStartOfMonthGivenTime(startOfPeriod));

    // Find months difference between startOfPeriod & forecastTime/currentTime
    int monthDiff =
        monthsBetween(new DateTime(BudgetUtils.getStartOfMonthGivenTime(startOfPeriod)), new DateTime(forecastTime))
            .getMonths();

    // Fitting data points which are available that is actual cost
    // We will fit current year, first month of budget period cost on 0
    // Second month on 1, and so on up to 11 till we have actual cost.
    for (int currentYearMonth = 0; currentYearMonth < monthDiff && monthDiff < MONTHS; currentYearMonth++) {
      regression.addData(currentYearMonth, result[currentYearMonth]);
    }

    long startOfMonthForecastTime = BudgetUtils.getStartOfMonthGivenTime(forecastTime);

    // Total number of days between start of month for forecast time & actual forecast Time
    int daysDiff = daysBetween(new DateTime(startOfMonthForecastTime), new DateTime(forecastTime)).getDays();

    // Total number of days in month in which we are where we are forecasting
    int days = daysBetween(new DateTime(startOfMonthForecastTime),
        new DateTime(BudgetUtils.getEndTimeForBudget(startOfMonthForecastTime, BudgetPeriod.MONTHLY)))
                   .getDays();

    // Forecasting for months for which we don't have actual cost
    for (int currentYearMonth = monthDiff; currentYearMonth < MONTHS; currentYearMonth++) {
      if (daysDiff > 0 && daysDiff < days) {
        result[currentYearMonth] +=
            BudgetUtils.getRoundedValue((abs(regression.predict(currentYearMonth)) / days) * (days - daysDiff));
        daysDiff = 0;
      } else {
        result[currentYearMonth] = BudgetUtils.getRoundedValue(abs(regression.predict(currentYearMonth)));
      }
    }
    return result;
  }

  @Override
  public BudgetData getBudgetTimeSeriesStats(Budget budget, BudgetBreakdown breakdown) {
    if (budget == null) {
      throw new InvalidRequestException(BudgetUtils.INVALID_BUDGET_ID_EXCEPTION);
    }
    List<BudgetCostData> budgetCostDataList = new ArrayList<>();
    Double budgetedAmount = budget.getBudgetAmount();
    if (budgetedAmount == null) {
      budgetedAmount = 0.0;
    }

    if (budget.getPeriod() == BudgetPeriod.YEARLY && breakdown == BudgetBreakdown.MONTHLY) {
      Double[] actualCost = budget.getBudgetMonthlyBreakdown().getActualMonthlyCost();
      if (actualCost == null || actualCost.length != MONTHS) {
        log.error("Missing 12 entries in actualCost of yearly budget with id:" + budget.getUuid());
        throw new InvalidRequestException(BudgetUtils.MISSING_BUDGET_DATA_EXCEPTION);
      }

      Double[] budgetCost =
          BudgetUtils.getYearlyMonthWiseValues(budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount());
      if (budgetCost == null || budgetCost.length != MONTHS) {
        log.error("Missing 12 entries in budgetCost of yearly budget with id:" + budget.getUuid());
        throw new InvalidRequestException(BudgetUtils.MISSING_BUDGET_DATA_EXCEPTION);
      }

      Double[] forecastMonthlyCost = budget.getBudgetMonthlyBreakdown().getForecastMonthlyCost();
      if (forecastMonthlyCost == null || forecastMonthlyCost.length != MONTHS) {
        log.error("Missing 12 entries in forecastCost of yearly budget with id:" + budget.getUuid());
        throw new InvalidRequestException(BudgetUtils.MISSING_BUDGET_DATA_EXCEPTION);
      }

      long startTime = budget.getStartTime();
      for (int month = 0; month < MONTHS; month++) {
        long endTime = BudgetUtils.getEndTimeForBudget(startTime, BudgetPeriod.MONTHLY) - BudgetUtils.ONE_DAY_MILLIS;
        double budgetVariance = BudgetUtils.getBudgetVariance(budgetCost[month], forecastMonthlyCost[month]);
        double budgetVariancePercentage = BudgetUtils.getBudgetVariancePercentage(budgetVariance, budgetCost[month]);
        BudgetCostData budgetCostData =
            BudgetCostData.builder()
                .actualCost(viewsQueryHelper.getRoundedDoubleValue(actualCost[month]))
                .forecastCost(viewsQueryHelper.getRoundedDoubleValue(forecastMonthlyCost[month]))
                .budgeted(viewsQueryHelper.getRoundedDoubleValue(budgetCost[month]))
                .budgetVariance(viewsQueryHelper.getRoundedDoubleValue(budgetVariance))
                .budgetVariancePercentage(viewsQueryHelper.getRoundedDoubleValue(budgetVariancePercentage))
                .time(startTime)
                .endTime(endTime)
                .build();
        budgetCostDataList.add(budgetCostData);
        startTime = endTime + BudgetUtils.ONE_DAY_MILLIS;
      }
    } else {
      String viewId = budget.getScope().getEntityIds().get(0);
      long timeFilterValue = BudgetUtils.getStartTimeForCostGraph(
          BudgetUtils.getBudgetStartTime(budget.getStartTime(), budget.getPeriod()),
          BudgetUtils.getBudgetPeriod(budget));
      int timeOffsetInDays = BudgetUtils.getTimeOffsetInDays(budget.getStartTime(), budget.getPeriod());
      try {
        List<TimeSeriesDataPoints> monthlyCostData =
            getPerspectiveBudgetTimeSeriesCostData(viewId, budget.getAccountId(), timeFilterValue,
                getTimeResolutionForBudget(budget.getPeriod()), timeOffsetInDays, 0L);
        for (TimeSeriesDataPoints data : monthlyCostData) {
          double actualCost =
              data.getValues().stream().map(dataPoint -> dataPoint.getValue().doubleValue()).reduce(0D, Double::sum);
          double budgetVariance = BudgetUtils.getBudgetVariance(budgetedAmount, actualCost);
          double budgetVariancePercentage = BudgetUtils.getBudgetVariancePercentage(budgetVariance, budgetedAmount);
          long startTime = data.getTime() + timeOffsetInDays * BudgetUtils.ONE_DAY_MILLIS;
          long endTime = BudgetUtils.getEndTimeForBudget(startTime, BudgetUtils.getBudgetPeriod(budget))
              - BudgetUtils.ONE_DAY_MILLIS;
          BudgetCostData budgetCostData =
              BudgetCostData.builder()
                  .actualCost(viewsQueryHelper.getRoundedDoubleValue(actualCost))
                  .budgeted(viewsQueryHelper.getRoundedDoubleValue(budgetedAmount))
                  .budgetVariance(viewsQueryHelper.getRoundedDoubleValue(budgetVariance))
                  .budgetVariancePercentage(viewsQueryHelper.getRoundedDoubleValue(budgetVariancePercentage))
                  .time(startTime)
                  .endTime(endTime)
                  .build();
          budgetCostDataList.add(budgetCostData);
        }
      } catch (Exception e) {
        log.error("Error in generating data for budget : {}", budget.getUuid());
      }
    }
    return BudgetData.builder().costData(budgetCostDataList).forecastCost(budget.getForecastCost()).build();
  }

  private double getCostForPerspectiveBudget(List<QLCEViewFilterWrapper> filters, String accountId) {
    ViewCostData trendData = viewsBillingService.getCostData(filters,
        viewsQueryHelper.getPerspectiveTotalCostAggregation(), viewsQueryHelper.buildQueryParams(accountId, false));
    return trendData.getCost();
  }

  private List<TimeSeriesDataPoints> getPerspectiveBudgetTimeSeriesCostData(String viewId, String accountId,
      long startTime, QLCEViewTimeGroupType period, int timeOffsetInDays, long endTime) {
    List<QLCEViewAggregation> aggregationFunction = viewsQueryHelper.getPerspectiveTotalCostAggregation();
    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(QLCEViewGroupBy.builder()
                    .timeTruncGroupBy(QLCEViewTimeTruncGroupBy.builder().resolution(period).build())
                    .build());
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(viewsQueryHelper.getViewMetadataFilter(viewId));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(startTime, AFTER));
    if (endTime != 0L) {
      filters.add(viewsQueryHelper.getPerspectiveTimeFilter(endTime, BEFORE));
    }

    if (isClickHouseEnabled) {
      return clickHouseViewsBillingService
          .getClickHouseTimeSeriesStatsNg(filters, groupBy, aggregationFunction, Collections.emptyList(), true, 100,
              viewsQueryHelper.buildQueryParams(accountId, true, false, false, false, timeOffsetInDays, false, false),
              perspectiveTimeSeriesHelper.getTimePeriod(groupBy), null, null, null, false)
          .getStats();
    } else {
      return perspectiveTimeSeriesHelper
          .fetch(viewsBillingService.getTimeSeriesStatsNg(filters, groupBy, aggregationFunction,
                     Collections.emptyList(), true, 100,
                     viewsQueryHelper.buildQueryParams(
                         accountId, true, false, false, false, timeOffsetInDays, false, false)),
              perspectiveTimeSeriesHelper.getTimePeriod(groupBy), groupBy)
          .getStats();
    }
  }

  private QLCEViewTimeGroupType getTimeResolutionForBudget(BudgetPeriod period) {
    try {
      switch (period) {
        case DAILY:
          return DAY;
        case WEEKLY:
          return WEEK;
        case QUARTERLY:
          return QUARTER;
        case YEARLY:
          return YEAR;
        case MONTHLY:
        default:
          return MONTH;
      }
    } catch (Exception e) {
      return MONTH;
    }
  }

  @Override
  public HashMap<Long, BudgetCostData> getBudgetHistory(Budget budget) {
    return getBudgetHistory(budget.getAccountId(), budget.getScope().getEntityIds().get(0),
        BudgetUtils.getBudgetStartTime(budget.getStartTime(), budget.getPeriod()), budget.getPeriod(),
        budget.getBudgetAmount());
  }

  @Override
  public HashMap<Long, BudgetCostData> getBudgetHistory(
      String accountId, String perspectiveId, long startTime, BudgetPeriod period, Double budgetAmount) {
    HashMap<Long, BudgetCostData> budgetHistory = new HashMap<>();
    long endTime = startTime - BudgetUtils.ONE_DAY_MILLIS;
    long updatedStartTime = BudgetUtils.getHistoryStartTime(startTime, period);
    int timeOffsetInDays = BudgetUtils.getTimeOffsetInDays(startTime, period);
    try {
      List<TimeSeriesDataPoints> monthlyCostData = getPerspectiveBudgetTimeSeriesCostData(
          perspectiveId, accountId, updatedStartTime, getTimeResolutionForBudget(period), timeOffsetInDays, endTime);
      for (TimeSeriesDataPoints data : monthlyCostData) {
        double actualCost =
            data.getValues().stream().map(dataPoint -> dataPoint.getValue().doubleValue()).reduce(0D, Double::sum);
        double budgetVariance = BudgetUtils.getBudgetVariance(budgetAmount, actualCost);
        double budgetVariancePercentage = BudgetUtils.getBudgetVariancePercentage(budgetVariance, budgetAmount);
        long startTimeData = data.getTime() + timeOffsetInDays * BudgetUtils.ONE_DAY_MILLIS;
        long endTimeData = BudgetUtils.getEndTimeForBudget(startTimeData, period) - BudgetUtils.ONE_DAY_MILLIS;
        BudgetCostData budgetCostData =
            BudgetCostData.builder()
                .actualCost(viewsQueryHelper.getRoundedDoubleValue(actualCost))
                .budgeted(viewsQueryHelper.getRoundedDoubleValue(budgetAmount))
                .budgetVariance(viewsQueryHelper.getRoundedDoubleValue(budgetVariance))
                .budgetVariancePercentage(viewsQueryHelper.getRoundedDoubleValue(budgetVariancePercentage))
                .time(startTimeData)
                .endTime(endTimeData)
                .build();
        budgetHistory.put(startTimeData, budgetCostData);
      }
    } catch (Exception e) {
      log.error("Error in generating data for budget having perspective Id: {}", perspectiveId);
    }
    return budgetHistory;
  }

  private List<ValueDataPoint> actualCostGroupedByPeriod(
      String accountId, BudgetPeriod period, String perspectiveId, long startTime, long endTime) {
    List<QLCEViewGroupBy> groupBy = new ArrayList<>();
    groupBy.add(
        QLCEViewGroupBy.builder()
            .timeTruncGroupBy(QLCEViewTimeTruncGroupBy.builder().resolution(getTimeResolutionForBudget(period)).build())
            .build());
    List<QLCEViewFilterWrapper> filters = new ArrayList<>();
    filters.add(viewsQueryHelper.getViewMetadataFilter(perspectiveId));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(startTime, AFTER));
    filters.add(viewsQueryHelper.getPerspectiveTimeFilter(endTime, BEFORE));
    ViewQueryParams viewQueryParams =
        viewsQueryHelper.buildQueryParamsWithSkipGroupBy(viewsQueryHelper.buildQueryParams(accountId, false), true);
    return viewsBillingService.getActualCostGroupedByPeriod(
        filters, groupBy, viewsQueryHelper.getPerspectiveTotalCostAggregation(), viewQueryParams);
  }
}
