/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.graphql;

import static io.harness.ccm.commons.constants.ViewFieldConstants.NONE_FIELD;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.AFTER;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.BEFORE;

import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewQueryParams;

import com.sun.istack.internal.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ViewsQueryHelper {
  private static final String TOTAL_COST_DATE_PATTERN = "MMM dd, yyyy";
  private static final String TOTAL_COST_DATE_PATTERN_WITHOUT_YEAR = "MMM dd";
  private static final String DEFAULT_TIME_ZONE = "GMT";
  private static final long ONE_DAY_MILLIS = 86400000;
  private static final int IDLE_COST_BASELINE = 30;
  private static final int UNALLOCATED_COST_BASELINE = 5;
  private static final int DEFAULT_EFFICIENCY_SCORE = -1;
  private static final double DEFAULT_DOUBLE_VALUE = 0;
  private static final String EFFICIENCY_SCORE_LABEL = "Efficiency Score";
  private static final long OBSERVATION_PERIOD = 29 * ONE_DAY_MILLIS;

  public boolean isYearRequired(Instant startInstant, Instant endInstant) {
    LocalDate endDate = LocalDateTime.ofInstant(endInstant, ZoneOffset.UTC).toLocalDate();
    LocalDate startDate = LocalDateTime.ofInstant(startInstant, ZoneOffset.UTC).toLocalDate();
    return startDate.getYear() - endDate.getYear() != 0;
  }

  public String getTotalCostFormattedDate(Instant instant, boolean isYearRequired) {
    if (isYearRequired) {
      return getFormattedDate(instant, TOTAL_COST_DATE_PATTERN);
    } else {
      return getFormattedDate(instant, TOTAL_COST_DATE_PATTERN_WITHOUT_YEAR);
    }
  }

  // To insert commas in given number
  public String formatNumber(Double number) {
    NumberFormat formatter = NumberFormat.getInstance(Locale.US);
    return formatter.format(number);
  }

  protected String getFormattedDate(Instant instant, String datePattern) {
    return instant.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).format(DateTimeFormatter.ofPattern(datePattern));
  }

  public double getRoundedDoubleValue(double value) {
    return Math.round(value * 100D) / 100D;
  }

  public double getRoundedDoublePercentageValue(double value) {
    return Math.round(value * 10000D) / 100D;
  }

  public double getForecastCost(ViewCostData costData, Instant endInstant) {
    if (costData == null) {
      return DEFAULT_DOUBLE_VALUE;
    }
    Instant currentTime = Instant.now();
    if (currentTime.isAfter(endInstant)) {
      return DEFAULT_DOUBLE_VALUE;
    }

    double totalCost = costData.getCost();

    long actualTimeDiffMillis =
        (endInstant.plus(1, ChronoUnit.DAYS).toEpochMilli()) - (costData.getMaxStartTime() / 1000);

    long billingTimeDiffMillis = ONE_DAY_MILLIS;
    if (costData.getMaxStartTime() != costData.getMinStartTime()) {
      billingTimeDiffMillis = ((costData.getMaxStartTime() - costData.getMinStartTime()) / 1000) + ONE_DAY_MILLIS;
    }

    if (billingTimeDiffMillis < OBSERVATION_PERIOD) {
      return DEFAULT_DOUBLE_VALUE;
    }

    return totalCost
        * (new BigDecimal(actualTimeDiffMillis).divide(new BigDecimal(billingTimeDiffMillis), 2, RoundingMode.HALF_UP))
              .doubleValue();
  }

  private Long getModifiedMaxStartTime(long maxStartTime) {
    Instant instant = Instant.ofEpochMilli(maxStartTime);
    Instant dayTruncated = instant.truncatedTo(ChronoUnit.DAYS);
    Instant hourlyTruncated = instant.truncatedTo(ChronoUnit.HOURS);
    if (dayTruncated.equals(hourlyTruncated)) {
      return dayTruncated.plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.SECONDS).toEpochMilli();
    }
    return hourlyTruncated.plus(1, ChronoUnit.HOURS).minus(1, ChronoUnit.SECONDS).toEpochMilli();
  }

  public Double getBillingTrend(
      double totalBillingAmount, double forecastCost, ViewCostData prevCostData, Instant trendFilterStartTime) {
    Double trendCostValue = 0.0;
    if (prevCostData != null && prevCostData.getCost() > 0) {
      double prevTotalBillingAmount = prevCostData.getCost();
      Instant startInstant = Instant.ofEpochMilli(prevCostData.getMinStartTime() / 1000);
      double amountDifference = totalBillingAmount - prevTotalBillingAmount;
      if (Double.valueOf(0) != forecastCost) {
        amountDifference = forecastCost - prevTotalBillingAmount;
      }
      if (trendFilterStartTime.plus(1, ChronoUnit.DAYS).isAfter(startInstant)) {
        double trendPercentage = amountDifference / prevTotalBillingAmount * 100;

        trendCostValue = getRoundedDoubleValue(trendPercentage);
      }
    }
    return trendCostValue;
  }

  public List<QLCEViewFilterWrapper> getFiltersForForecastCost(List<QLCEViewFilterWrapper> filters) {
    List<QLCEViewFilterWrapper> filtersForForecastCost =
        filters.stream().filter(filter -> filter.getTimeFilter() == null).collect(Collectors.toList());
    long timestampForFilters = getStartOfCurrentDay();
    filtersForForecastCost.add(getPerspectiveTimeFilter(timestampForFilters - 1000, BEFORE));
    filtersForForecastCost.add(getPerspectiveTimeFilter(timestampForFilters - 30 * ONE_DAY_MILLIS, AFTER));
    return filtersForForecastCost;
  }

  public Instant getEndInstantForForecastCost(List<QLCEViewFilterWrapper> filters) {
    List<QLCEViewTimeFilter> timeFilters = getTimeFilters(filters);
    QLCEViewTimeFilter endTimeFilter = null;
    QLCEViewTimeFilter startTimeFilter = null;
    for (QLCEViewTimeFilter timeFilter : timeFilters) {
      if (timeFilter.getOperator() == AFTER) {
        startTimeFilter = timeFilter;
      } else {
        endTimeFilter = timeFilter;
      }
    }
    long currentDay = getStartOfCurrentDay();
    long days = 0;
    if (endTimeFilter != null && startTimeFilter != null) {
      long endTimeFromFilters = endTimeFilter.getValue().longValue();
      long startTimeFromFilters = startTimeFilter.getValue().longValue();
      if (endTimeFromFilters == currentDay - 1000 || endTimeFromFilters == currentDay) {
        days = (currentDay - startTimeFromFilters) / ONE_DAY_MILLIS;
      }
      if (endTimeFromFilters == currentDay + ONE_DAY_MILLIS - 1000
          || endTimeFromFilters == currentDay + ONE_DAY_MILLIS) {
        days = (currentDay + ONE_DAY_MILLIS - startTimeFromFilters) / ONE_DAY_MILLIS;
      }
    }
    return days != 0 ? Instant.ofEpochMilli(currentDay + (days - 1) * ONE_DAY_MILLIS - 1000)
                     : Instant.ofEpochMilli(currentDay - ONE_DAY_MILLIS);
  }

  public List<QLCEViewTimeFilter> getTimeFilters(List<QLCEViewFilterWrapper> filters) {
    return filters.stream()
        .filter(f -> f.getTimeFilter() != null)
        .map(QLCEViewFilterWrapper::getTimeFilter)
        .collect(Collectors.toList());
  }

  public List<QLCEViewFilter> getIdFilters(List<QLCEViewFilterWrapper> filters) {
    return filters.stream()
        .filter(f -> f.getIdFilter() != null)
        .map(QLCEViewFilterWrapper::getIdFilter)
        .collect(Collectors.toList());
  }

  public long getStartOfCurrentDay() {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIME_ZONE);
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
    return zdtStart.toEpochSecond() * 1000;
  }

  public int calculateEfficiencyScore(double totalCost, double idleCost, double unallocatedCost) {
    int utilizedBaseline = 100 - IDLE_COST_BASELINE - UNALLOCATED_COST_BASELINE;
    double utilizedCost = totalCost - idleCost - unallocatedCost;
    if (totalCost > 0.0) {
      double utilizedPercentage = utilizedCost / totalCost * 100;
      int efficiencyScore = (int) Math.round((1 - ((utilizedBaseline - utilizedPercentage) / utilizedBaseline)) * 100);
      return Math.min(efficiencyScore, 100);
    }
    return DEFAULT_EFFICIENCY_SCORE;
  }

  public EfficiencyScoreStats getEfficiencyScoreStats(ViewCostData currentCostData, ViewCostData previousCostData) {
    int currentEfficiencyScore = DEFAULT_EFFICIENCY_SCORE;
    int previousEfficiencyScore;
    double efficiencyTrend = DEFAULT_EFFICIENCY_SCORE;

    // Calculating efficiency score for current period
    if (currentCostData != null && currentCostData.getCost() > 0 && currentCostData.getIdleCost() != null) {
      double unallocatedCost =
          currentCostData.getUnallocatedCost() != null ? currentCostData.getUnallocatedCost() : 0.0;
      currentEfficiencyScore =
          calculateEfficiencyScore(currentCostData.getCost(), currentCostData.getIdleCost(), unallocatedCost);
    }

    // Calculating efficiency score for previous period
    if (previousCostData != null && previousCostData.getCost() > 0 && previousCostData.getIdleCost() != null) {
      double unallocatedCost =
          previousCostData.getUnallocatedCost() != null ? previousCostData.getUnallocatedCost() : 0.0;
      previousEfficiencyScore =
          calculateEfficiencyScore(previousCostData.getCost(), previousCostData.getIdleCost(), unallocatedCost);

      if (previousEfficiencyScore > 0) {
        efficiencyTrend = getRoundedDoubleValue(
            ((double) (currentEfficiencyScore - previousEfficiencyScore) / previousEfficiencyScore) * 100);
      }
    }

    return EfficiencyScoreStats.builder()
        .statsLabel(EFFICIENCY_SCORE_LABEL)
        .statsValue(String.valueOf(currentEfficiencyScore))
        .statsTrend(efficiencyTrend)
        .build();
  }

  public QLCEViewFilterWrapper getPerspectiveTimeFilter(long timestamp, QLCEViewTimeFilterOperator operator) {
    return QLCEViewFilterWrapper.builder()
        .timeFilter(QLCEViewTimeFilter.builder()
                        .field(QLCEViewFieldInput.builder()
                                   .fieldId("startTime")
                                   .fieldName("startTime")
                                   .identifier(ViewFieldIdentifier.COMMON)
                                   .identifierName(ViewFieldIdentifier.COMMON.getDisplayName())
                                   .build())
                        .operator(operator)
                        .value(timestamp)
                        .build())
        .build();
  }

  public QLCEViewFilterWrapper getViewMetadataFilter(String viewId) {
    return QLCEViewFilterWrapper.builder()
        .viewMetadataFilter(QLCEViewMetadataFilter.builder().viewId(viewId).isPreview(false).build())
        .build();
  }

  public List<QLCEViewAggregation> getPerspectiveTotalCostAggregation() {
    return Collections.singletonList(
        QLCEViewAggregation.builder().columnName("cost").operationType(QLCEViewAggregateOperation.SUM).build());
  }

  public ViewQueryParams buildQueryParams(String accountId, boolean isTimeTruncGroupByRequired,
      boolean isUsedByTimeSeriesStats, boolean isClusterQuery, boolean isTotalCountQuery, int timeOffsetInDays) {
    return ViewQueryParams.builder()
        .accountId(accountId)
        .isClusterQuery(isClusterQuery)
        .isUsedByTimeSeriesStats(isUsedByTimeSeriesStats)
        .isTimeTruncGroupByRequired(isTimeTruncGroupByRequired)
        .isTotalCountQuery(isTotalCountQuery)
        .timeOffsetInDays(timeOffsetInDays)
        .build();
  }

  public ViewQueryParams buildQueryParams(String accountId, boolean isTimeTruncGroupByRequired,
      boolean isUsedByTimeSeriesStats, boolean isClusterQuery, boolean isTotalCountQuery) {
    return buildQueryParams(
        accountId, isTimeTruncGroupByRequired, isUsedByTimeSeriesStats, isClusterQuery, isTotalCountQuery, 0);
  }

  public ViewQueryParams buildQueryParams(String accountId, boolean isClusterQuery) {
    return buildQueryParams(accountId, false, false, isClusterQuery, false);
  }

  public ViewQueryParams buildQueryParams(String accountId, boolean isClusterQuery, boolean skipRoundOff) {
    return ViewQueryParams.builder()
        .accountId(accountId)
        .isClusterQuery(isClusterQuery)
        .skipRoundOff(skipRoundOff)
        .isUsedByTimeSeriesStats(false)
        .isTimeTruncGroupByRequired(false)
        .isTotalCountQuery(false)
        .timeOffsetInDays(0)
        .build();
  }

  @Nullable
  public static Optional<String> getPerspectiveIdFromMetadataFilter(List<QLCEViewFilterWrapper> filters) {
    return filters.stream()
        .filter(f -> f.getViewMetadataFilter() != null)
        .findFirst()
        .map(x -> x.getViewMetadataFilter().getViewId());
  }

  public Boolean isGroupByNonePresent(List<QLCEViewGroupBy> groupByList) {
    return groupByList.stream().anyMatch(
        groupBy -> groupBy.getEntityGroupBy() != null && groupBy.getEntityGroupBy().getFieldName().equals(NONE_FIELD));
  }

  public Boolean isGroupByFieldPresent(List<QLCEViewGroupBy> groupByList, String fieldName) {
    return groupByList.stream().anyMatch(
        groupBy -> groupBy.getEntityGroupBy() != null && groupBy.getEntityGroupBy().getFieldName().equals(fieldName));
  }

  public Boolean isGroupByFieldIdPresent(List<QLCEViewGroupBy> groupByList, String fieldId) {
    return groupByList.stream().anyMatch(
        groupBy -> groupBy.getEntityGroupBy() != null && groupBy.getEntityGroupBy().getFieldId().equals(fieldId));
  }

  public List<QLCEViewGroupBy> removeGroupByNone(List<QLCEViewGroupBy> groupByList) {
    return groupByList.stream()
        .filter(groupBy
            -> groupBy.getEntityGroupBy() == null || !groupBy.getEntityGroupBy().getFieldName().equals(NONE_FIELD))
        .collect(Collectors.toList());
  }

  public String getBusinessMappingIdFromGroupBy(List<QLCEViewGroupBy> groupByList) {
    Optional<QLCEViewGroupBy> businessMappingGroupBy =
        groupByList.stream()
            .filter(groupBy
                -> groupBy.getEntityGroupBy() != null
                    && groupBy.getEntityGroupBy().getIdentifier() == ViewFieldIdentifier.BUSINESS_MAPPING)
            .findFirst();

    return businessMappingGroupBy.map(groupBy -> groupBy.getEntityGroupBy().getFieldId()).orElse(null);
  }

  public String getBusinessMappingIdFromFilters(List<QLCEViewFilterWrapper> filters) {
    Optional<QLCEViewFilterWrapper> businessMappingGroupBy =
        filters.stream()
            .filter(filter
                -> filter.getIdFilter() != null
                    && filter.getIdFilter().getField().getIdentifier() == ViewFieldIdentifier.BUSINESS_MAPPING)
            .findFirst();

    return businessMappingGroupBy.map(filter -> filter.getIdFilter().getField().getFieldId()).orElse(null);
  }
}
