/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata.BillingDataMetaDataFields;
import software.wings.graphql.datafetcher.billing.QLBillingAmountData.QLBillingAmountDataBuilder;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortCriteria;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMTimeSeriesAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLStatsBreakdownInfo;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BillingDataHelper {
  @Inject protected DataFetcherUtils utils;
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject private BillingDataQueryBuilder billingDataQueryBuilder;
  private static final String TOTAL_COST_DATE_PATTERN = "MMM dd, yyyy";
  private static final String TOTAL_COST_DATE_PATTERN_WITHOUT_YEAR = "MMM dd";
  private static final String DEFAULT_TIME_ZONE = "GMT";
  private static final long ONE_DAY_MILLIS = 86400000;
  private static final long OBSERVATION_PERIOD = 29 * ONE_DAY_MILLIS;
  private static final int MAX_RETRY = 3;
  private static final int IDLE_COST_BASELINE = 30;
  private static final int UNALLOCATED_COST_BASELINE = 5;

  protected double roundingDoubleFieldValue(BillingDataMetaDataFields field, ResultSet resultSet) throws SQLException {
    return roundingDoubleFieldValue(field, resultSet, false);
  }

  protected double roundingDoubleFieldValue(BillingDataMetaDataFields field, ResultSet resultSet, boolean skipRoundOff)
      throws SQLException {
    if (skipRoundOff) {
      return resultSet.getDouble(field.getFieldName());
    }
    return Math.round(resultSet.getDouble(field.getFieldName()) * 100D) / 100D;
  }

  protected double roundingDoubleFieldPercentageValue(BillingDataMetaDataFields field, ResultSet resultSet)
      throws SQLException {
    return Math.round(resultSet.getDouble(field.getFieldName()) * 10000D) / 100D;
  }

  protected String getFormattedDate(Instant instant, String datePattern) {
    return instant.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).format(DateTimeFormatter.ofPattern(datePattern));
  }

  protected double getRoundedDoubleValue(BigDecimal value) {
    return Math.round(value.doubleValue() * 100D) / 100D;
  }

  public double getRoundedDoubleValue(double value) {
    return Math.round(value * 100D) / 100D;
  }

  protected double getRoundedDoublePercentageValue(BigDecimal value) {
    return Math.round(value.doubleValue() * 10000D) / 100D;
  }

  protected QLTimeFilter getStartTimeFilter(List<QLBillingDataFilter> filters) {
    Optional<QLBillingDataFilter> startTimeDataFilter =
        filters.stream().filter(qlBillingDataFilter -> qlBillingDataFilter.getStartTime() != null).findFirst();
    if (startTimeDataFilter.isPresent()) {
      return startTimeDataFilter.get().getStartTime();
    } else {
      throw new InvalidRequestException("Start time cannot be null");
    }
  }

  protected QLTimeFilter getEndTimeFilter(List<QLBillingDataFilter> filters) {
    Optional<QLBillingDataFilter> endTimeDataFilter =
        filters.stream().filter(qlBillingDataFilter -> qlBillingDataFilter.getEndTime() != null).findFirst();
    if (endTimeDataFilter.isPresent()) {
      return endTimeDataFilter.get().getEndTime();
    } else {
      throw new InvalidRequestException("End time cannot be null");
    }
  }

  public String getTotalCostFormattedDate(Instant instant, boolean isYearRequired) {
    if (isYearRequired) {
      return getFormattedDate(instant, TOTAL_COST_DATE_PATTERN);
    } else {
      return getFormattedDate(instant, TOTAL_COST_DATE_PATTERN_WITHOUT_YEAR);
    }
  }

  protected List<QLBillingDataFilter> getTrendFilter(
      List<QLBillingDataFilter> filters, Instant startInstant, Instant endInstant) {
    long diffMillis = Duration.between(startInstant, endInstant).toMillis();
    long trendEndTime = startInstant.toEpochMilli() - 1000;
    long trendStartTime = trendEndTime - diffMillis;
    return updateTimeFilter(filters, trendStartTime, trendEndTime);
  }

  private List<QLBillingDataFilter> updateTimeFilter(List<QLBillingDataFilter> filters, long startTime, long endTime) {
    List<QLBillingDataFilter> entityFilters =
        filters.stream()
            .filter(qlBillingDataFilter
                -> null == qlBillingDataFilter.getStartTime() && null == qlBillingDataFilter.getEndTime())
            .collect(Collectors.toList());
    entityFilters.add(QLBillingDataFilter.builder()
                          .startTime(QLTimeFilter.builder().value(startTime).operator(QLTimeOperator.AFTER).build())
                          .build());
    entityFilters.add(QLBillingDataFilter.builder()
                          .endTime(QLTimeFilter.builder().value(endTime).operator(QLTimeOperator.BEFORE).build())
                          .build());
    return entityFilters;
  }

  protected Double getCostTrendForEntity(ResultSet resultSet, QLBillingAmountData prevBillingAmountData,
      List<QLBillingDataFilter> filters) throws SQLException {
    QLBillingAmountData billingAmountData =
        QLBillingAmountData.builder()
            .cost(resultSet.getBigDecimal(BillingDataMetaDataFields.SUM.getFieldName()))
            .minStartTime(
                resultSet
                    .getTimestamp(BillingDataMetaDataFields.MIN_STARTTIME.getFieldName(), utils.getDefaultCalendar())
                    .getTime())
            .maxStartTime(
                resultSet
                    .getTimestamp(BillingDataMetaDataFields.MAX_STARTTIME.getFieldName(), utils.getDefaultCalendar())
                    .getTime())
            .build();
    BigDecimal forecastCost = getForecastCost(billingAmountData, getEndInstant(filters));

    List<QLBillingDataFilter> trendFilters = getTrendFilter(filters, getStartInstant(filters), getEndInstant(filters));
    Instant filterStartTime = getStartInstant(trendFilters);
    Double trendPercent = 0.0;
    if (prevBillingAmountData != null && prevBillingAmountData.getCost() != null
        && prevBillingAmountData.getCost().compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal prevTotalBillingAmount = prevBillingAmountData.getCost();
      Instant startInstant = Instant.ofEpochMilli(prevBillingAmountData.getMinStartTime());
      BigDecimal amountDifference = billingAmountData.getCost().subtract(prevTotalBillingAmount);
      if (null != forecastCost) {
        amountDifference = forecastCost.subtract(prevTotalBillingAmount);
      }

      if (filterStartTime.plus(1, ChronoUnit.DAYS).isAfter(startInstant)) {
        BigDecimal trendPercentage =
            amountDifference.multiply(BigDecimal.valueOf(100)).divide(prevTotalBillingAmount, 2, RoundingMode.HALF_UP);
        trendPercent = getRoundedDoubleValue(trendPercentage);
      }
    }
    return trendPercent;
  }

  protected Map<String, QLBillingAmountData> getEntityIdToBillingAmountData(ResultSet resultSet,
      List<BillingDataMetaDataFields> listOfFields, List<QLCCMAggregationFunction> aggregateFunction)
      throws SQLException {
    Map<String, QLBillingAmountData> entityIdToBillingAmountData = new HashMap<>();
    Map<String, Boolean> isFieldPresent = getFieldsPresentInAggregation(aggregateFunction);
    while (resultSet != null && resultSet.next()) {
      StringJoiner entityIdAppender = new StringJoiner(":");
      for (BillingDataMetaDataFields field : listOfFields) {
        if (resultSet.getString(field.getFieldName()) != null) {
          entityIdAppender.add(resultSet.getString(field.getFieldName()));
        }
      }
      String entityId = entityIdAppender.toString();
      QLBillingAmountDataBuilder billingAmountDataBuilder = QLBillingAmountData.builder();
      if (isFieldPresent.containsKey(BillingDataMetaDataFields.SUM.getFieldName())
          && resultSet.getBigDecimal(BillingDataMetaDataFields.SUM.getFieldName()) != null) {
        billingAmountDataBuilder.cost(resultSet.getBigDecimal(BillingDataMetaDataFields.SUM.getFieldName()))
            .minStartTime(
                resultSet
                    .getTimestamp(BillingDataMetaDataFields.MIN_STARTTIME.getFieldName(), utils.getDefaultCalendar())
                    .getTime())
            .maxStartTime(
                resultSet
                    .getTimestamp(BillingDataMetaDataFields.MAX_STARTTIME.getFieldName(), utils.getDefaultCalendar())
                    .getTime());
      }
      if (isFieldPresent.containsKey(BillingDataMetaDataFields.IDLECOST.getFieldName())
          && resultSet.getBigDecimal(BillingDataMetaDataFields.IDLECOST.getFieldName()) != null) {
        billingAmountDataBuilder.idleCost(resultSet.getBigDecimal(BillingDataMetaDataFields.IDLECOST.getFieldName()));
      }
      if (isFieldPresent.containsKey(BillingDataMetaDataFields.UNALLOCATEDCOST.getFieldName())
          && resultSet.getBigDecimal(BillingDataMetaDataFields.UNALLOCATEDCOST.getFieldName()) != null) {
        billingAmountDataBuilder.unallocatedCost(
            resultSet.getBigDecimal(BillingDataMetaDataFields.UNALLOCATEDCOST.getFieldName()));
      }
      entityIdToBillingAmountData.put(entityId, billingAmountDataBuilder.build());
    }
    return entityIdToBillingAmountData;
  }

  public BigDecimal getForecastCost(QLBillingAmountData billingAmountData, Instant endInstant) {
    Preconditions.checkNotNull(billingAmountData);
    Instant currentTime = Instant.now();
    if (currentTime.isAfter(endInstant)) {
      return null;
    }

    long maxStartTime = getModifiedMaxStartTime(billingAmountData.getMaxStartTime());
    long billingTimeDiffMillis = ONE_DAY_MILLIS;
    if (maxStartTime != billingAmountData.getMinStartTime()) {
      billingTimeDiffMillis = maxStartTime - billingAmountData.getMinStartTime();
    }

    BigDecimal totalBillingAmount = billingAmountData.getCost();
    long actualTimeDiffMillis = endInstant.toEpochMilli() - billingAmountData.getMinStartTime();
    return totalBillingAmount.multiply(
        new BigDecimal(actualTimeDiffMillis).divide(new BigDecimal(billingTimeDiffMillis), 2, RoundingMode.HALF_UP));
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

  public BigDecimal getNewForecastCost(QLBillingAmountData billingAmountData, Instant endInstant) {
    if (billingAmountData == null) {
      return null;
    }
    Instant currentTime = Instant.now();
    if (currentTime.isAfter(endInstant)) {
      return null;
    }

    BigDecimal totalBillingAmount = billingAmountData.getCost();
    long actualTimeDiffMillis =
        (endInstant.plus(1, ChronoUnit.SECONDS).toEpochMilli()) - billingAmountData.getMaxStartTime();

    long billingTimeDiffMillis = ONE_DAY_MILLIS;
    if (billingAmountData.getMaxStartTime() != billingAmountData.getMinStartTime()) {
      billingTimeDiffMillis =
          billingAmountData.getMaxStartTime() - billingAmountData.getMinStartTime() + ONE_DAY_MILLIS;
    }
    if (billingTimeDiffMillis < OBSERVATION_PERIOD) {
      return null;
    }

    return totalBillingAmount.multiply(
        new BigDecimal(actualTimeDiffMillis).divide(new BigDecimal(billingTimeDiffMillis), 2, RoundingMode.HALF_UP));
  }

  public Instant getEndInstant(List<QLBillingDataFilter> filters) {
    return Instant.ofEpochMilli(getEndTimeFilter(filters).getValue().longValue());
  }

  public Instant getStartInstant(List<QLBillingDataFilter> filters) {
    return Instant.ofEpochMilli(getStartTimeFilter(filters).getValue().longValue());
  }

  protected Map<String, QLBillingAmountData> getBillingAmountDataForEntityCostTrend(String accountId,
      List<QLCCMAggregationFunction> aggregateFunction, List<QLBillingDataFilter> filters,
      List<QLCCMEntityGroupBy> groupByEntityList, QLCCMTimeSeriesAggregation groupByTime,
      List<QLBillingSortCriteria> sortCriteria) {
    return getBillingAmountDataForEntityCostTrend(
        accountId, aggregateFunction, filters, groupByEntityList, groupByTime, sortCriteria, false);
  }

  protected Map<String, QLBillingAmountData> getBillingAmountDataForEntityCostTrend(String accountId,
      List<QLCCMAggregationFunction> aggregateFunction, List<QLBillingDataFilter> filters,
      List<QLCCMEntityGroupBy> groupByEntityList, QLCCMTimeSeriesAggregation groupByTime,
      List<QLBillingSortCriteria> sortCriteria, boolean isEfficiencyStats) {
    List<BillingDataMetaDataFields> entity = getEntityForCostTrendMapping(groupByEntityList);
    List<QLBillingDataFilter> trendFilters = getTrendFilter(filters, getStartInstant(filters), getEndInstant(filters));
    boolean addInstanceTypeFilter = setInstanceTypeBoolean(groupByEntityList);
    BillingDataQueryMetadata queryData = billingDataQueryBuilder.formQuery(accountId, trendFilters, aggregateFunction,
        groupByEntityList, groupByTime, sortCriteria, addInstanceTypeFilter, true, isEfficiencyStats);
    String query = queryData.getQuery();
    log.info("Billing data query for cost trend {}", query);
    ResultSet resultSet = null;
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query);
        successful = true;
        return getEntityIdToBillingAmountData(resultSet, entity, aggregateFunction);
      } catch (SQLException e) {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
          log.error(
              "Failed to execute query in BillingDataHelper for cost trend, max retry count reached, query=[{}],accountId=[{}]",
              queryData.getQuery(), accountId, e);
        } else {
          log.warn(
              "Failed to execute query in BillingDataHelper for cost trend, query=[{}],accountId=[{}], retryCount=[{}]",
              queryData.getQuery(), accountId, retryCount);
        }
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return null;
  }

  private boolean setInstanceTypeBoolean(List<QLCCMEntityGroupBy> groupByEntityList) {
    for (QLCCMEntityGroupBy groupBy : groupByEntityList) {
      if (groupBy == QLCCMEntityGroupBy.WorkloadName || groupBy == QLCCMEntityGroupBy.Namespace
          || groupBy == QLCCMEntityGroupBy.TaskId || groupBy == QLCCMEntityGroupBy.CloudServiceName) {
        return false;
      }
    }
    return true;
  }

  protected List<BillingDataMetaDataFields> getEntityForCostTrendMapping(List<QLCCMEntityGroupBy> groupByEntityList) {
    List<BillingDataMetaDataFields> listOfFields = new ArrayList<>();
    for (QLCCMEntityGroupBy groupByEntity : groupByEntityList) {
      switch (groupByEntity) {
        case Cluster:
          listOfFields.add(BillingDataMetaDataFields.CLUSTERID);
          break;
        case Namespace:
          listOfFields.add(BillingDataMetaDataFields.NAMESPACE);
          break;
        case WorkloadName:
          listOfFields.add(BillingDataMetaDataFields.WORKLOADNAME);
          break;
        case Application:
          listOfFields.add(BillingDataMetaDataFields.APPID);
          break;
        case Service:
          listOfFields.add(BillingDataMetaDataFields.SERVICEID);
          break;
        case Environment:
          listOfFields.add(BillingDataMetaDataFields.ENVID);
          break;
        case CloudProvider:
          listOfFields.add(BillingDataMetaDataFields.CLOUDPROVIDERID);
          break;
        case CloudServiceName:
          listOfFields.add(BillingDataMetaDataFields.CLOUDSERVICENAME);
          break;
        case TaskId:
          listOfFields.add(BillingDataMetaDataFields.TASKID);
          break;
        case LaunchType:
          listOfFields.add(BillingDataMetaDataFields.LAUNCHTYPE);
          break;
        default:
          break;
      }
    }
    return listOfFields;
  }

  public boolean isYearRequired(Instant startInstant, Instant endInstant) {
    LocalDate startDate = LocalDateTime.ofInstant(startInstant, ZoneOffset.UTC).toLocalDate();
    LocalDate endDate = LocalDateTime.ofInstant(endInstant, ZoneOffset.UTC).toLocalDate();
    return startDate.getYear() - endDate.getYear() != 0;
  }

  // To insert commas in given number
  public String formatNumber(Double number) {
    NumberFormat formatter = NumberFormat.getInstance(Locale.US);
    return formatter.format(number);
  }

  protected Map<String, Boolean> getFieldsPresentInAggregation(List<QLCCMAggregationFunction> aggregateFunctions) {
    Map<String, Boolean> isFieldPresent = aggregateFunctions.stream().collect(
        Collectors.toMap(field -> field.getColumnName().toUpperCase(), field -> Boolean.TRUE));
    if (isFieldPresent.containsKey("BILLINGAMOUNT")) {
      isFieldPresent.put("COST", Boolean.TRUE);
    }
    if (isFieldPresent.containsKey("IDLECOST")) {
      isFieldPresent.put("ACTUALIDLECOST", Boolean.TRUE);
    }
    return isFieldPresent;
  }

  public List<String> getElementIdsAfterLimit(Map<String, Double> aggregatedData, Integer limit) {
    List<Map.Entry<String, Double>> list = new ArrayList<>(aggregatedData.entrySet());
    list.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
    list = list.stream().limit(limit).collect(Collectors.toList());
    List<String> topNElementIds = new ArrayList<>();
    list.forEach(entry -> topNElementIds.add(entry.getKey()));
    return topNElementIds;
  }

  public List<QLBillingDataFilter> getFiltersForForecastCost(List<QLBillingDataFilter> filters) {
    List<QLBillingDataFilter> filtersForForecastCost =
        filters.stream()
            .filter(filter -> filter.getEndTime() == null && filter.getStartTime() == null)
            .collect(Collectors.toList());
    long timestampForFilters = getStartOfCurrentDay();
    filtersForForecastCost.add(
        QLBillingDataFilter.builder()
            .endTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(timestampForFilters - 1000).build())
            .build());
    filtersForForecastCost.add(QLBillingDataFilter.builder()
                                   .startTime(QLTimeFilter.builder()
                                                  .operator(QLTimeOperator.AFTER)
                                                  .value(timestampForFilters - 30 * ONE_DAY_MILLIS)
                                                  .build())
                                   .build());
    return filtersForForecastCost;
  }

  public Instant getEndInstantForForecastCost(List<QLBillingDataFilter> filters) {
    QLBillingDataFilter endTimeFilter =
        filters.stream().filter(filter -> filter.getEndTime() != null).findFirst().orElse(null);
    QLBillingDataFilter startTimeFilter =
        filters.stream().filter(filter -> filter.getStartTime() != null).findFirst().orElse(null);
    long currentDay = getStartOfCurrentDay();
    long days = 0;
    if (endTimeFilter != null && startTimeFilter != null) {
      long endTimeFromFilters = endTimeFilter.getEndTime().getValue().longValue();
      long startTimeFromFilters = startTimeFilter.getStartTime().getValue().longValue();
      if (endTimeFromFilters == currentDay - 1000) {
        days = (currentDay - startTimeFromFilters) / ONE_DAY_MILLIS;
      }
      if (endTimeFromFilters == currentDay + ONE_DAY_MILLIS - 1000) {
        days = (currentDay + ONE_DAY_MILLIS - startTimeFromFilters) / ONE_DAY_MILLIS;
      }
    }
    return days != 0 ? Instant.ofEpochMilli(currentDay + (days - 1) * ONE_DAY_MILLIS - 1000)
                     : Instant.ofEpochMilli(currentDay - ONE_DAY_MILLIS);
  }

  public Instant getStartInstantForForecastCost() {
    return Instant.ofEpochMilli(getStartOfCurrentDay());
  }

  public long getStartOfCurrentDay() {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIME_ZONE);
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
    return zdtStart.toEpochSecond() * 1000;
  }

  /**
   *  Efficiency Score rounded to nearest Integer
   * @param  costStats
   * @return int
   */
  public int calculateEfficiencyScore(QLStatsBreakdownInfo costStats) {
    int utilizedBaseline = 100 - IDLE_COST_BASELINE - UNALLOCATED_COST_BASELINE;
    double utilized = costStats.getUtilized().doubleValue();
    double total = costStats.getTotal().doubleValue();
    if (total > 0.0) {
      double utilizedPercentage = utilized / total * 100;
      int efficiencyScore = (int) Math.round((1 - ((utilizedBaseline - utilizedPercentage) / utilizedBaseline)) * 100);
      return Math.min(efficiencyScore, 100);
    }
    return BillingStatsDefaultKeys.EFFICIENCY_SCORE;
  }

  public BigDecimal calculateTrendPercentage(BigDecimal current, BigDecimal previous) {
    if (previous.compareTo(BigDecimal.ZERO) > 0) {
      return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
    }
    return BigDecimal.valueOf(BillingStatsDefaultKeys.EFFICIENCY_SCORE_TREND);
  }

  public Double calculateTrendPercentage(Double current, Double previous) {
    if (previous > 0.0) {
      return calculateTrendPercentage(BigDecimal.valueOf(current), BigDecimal.valueOf(previous)).doubleValue();
    }
    return (double) BillingStatsDefaultKeys.EFFICIENCY_SCORE_TREND;
  }

  // Used by trend stats and forecast cost dataFetchers
  public QLTrendStatsCostData getBillingAmountData(
      String accountId, List<QLCCMAggregationFunction> aggregateFunction, List<QLBillingDataFilter> filters) {
    BillingDataQueryMetadata queryData =
        billingDataQueryBuilder.formTrendStatsQuery(accountId, aggregateFunction, filters);
    String query = queryData.getQuery();
    log.info("Billing data query {}", query);
    ResultSet resultSet = null;
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query);
        successful = true;
        return fetchBillingAmount(queryData, resultSet);
      } catch (SQLException e) {
        retryCount++;
        if (retryCount >= MAX_RETRY) {
          log.error(
              "Failed to execute query in BillingTrendStatsDataFetcher, max retry count reached, query=[{}],accountId=[{}]",
              queryData.getQuery(), accountId, e);
        } else {
          log.warn(
              "Failed to execute query in BillingTrendStatsDataFetcher, query=[{}],accountId=[{}], retryCount=[{}]",
              queryData.getQuery(), accountId, retryCount);
        }
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return null;
  }

  private QLTrendStatsCostData fetchBillingAmount(BillingDataQueryMetadata queryData, ResultSet resultSet)
      throws SQLException {
    QLBillingAmountData totalCostData = null;
    QLBillingAmountData idleCostData = null;
    QLBillingAmountData unallocatedCostData = null;
    QLBillingAmountData systemCostData = null;
    while (null != resultSet && resultSet.next()) {
      for (BillingDataMetaDataFields field : queryData.getFieldNames()) {
        switch (field) {
          case SUM:
            if (resultSet.getBigDecimal(field.getFieldName()) != null) {
              totalCostData =
                  QLBillingAmountData.builder()
                      .cost(resultSet.getBigDecimal(BillingDataMetaDataFields.SUM.getFieldName()))
                      .minStartTime(resultSet
                                        .getTimestamp(BillingDataMetaDataFields.MIN_STARTTIME.getFieldName(),
                                            utils.getDefaultCalendar())
                                        .getTime())
                      .maxStartTime(resultSet
                                        .getTimestamp(BillingDataMetaDataFields.MAX_STARTTIME.getFieldName(),
                                            utils.getDefaultCalendar())
                                        .getTime())
                      .build();
            }
            break;
          case IDLECOST:
            if (resultSet.getBigDecimal(field.getFieldName()) != null) {
              idleCostData = QLBillingAmountData.builder()
                                 .cost(resultSet.getBigDecimal(BillingDataMetaDataFields.IDLECOST.getFieldName()))
                                 .minStartTime(resultSet
                                                   .getTimestamp(BillingDataMetaDataFields.MIN_STARTTIME.getFieldName(),
                                                       utils.getDefaultCalendar())
                                                   .getTime())
                                 .maxStartTime(resultSet
                                                   .getTimestamp(BillingDataMetaDataFields.MAX_STARTTIME.getFieldName(),
                                                       utils.getDefaultCalendar())
                                                   .getTime())
                                 .build();
            }
            break;
          case UNALLOCATEDCOST:
            if (resultSet.getBigDecimal(field.getFieldName()) != null) {
              unallocatedCostData =
                  QLBillingAmountData.builder()
                      .cost(resultSet.getBigDecimal(BillingDataMetaDataFields.UNALLOCATEDCOST.getFieldName()))
                      .minStartTime(resultSet
                                        .getTimestamp(BillingDataMetaDataFields.MIN_STARTTIME.getFieldName(),
                                            utils.getDefaultCalendar())
                                        .getTime())
                      .maxStartTime(resultSet
                                        .getTimestamp(BillingDataMetaDataFields.MAX_STARTTIME.getFieldName(),
                                            utils.getDefaultCalendar())
                                        .getTime())
                      .build();
            }
            break;
          case SYSTEMCOST:
            if (resultSet.getBigDecimal(field.getFieldName()) != null) {
              systemCostData =
                  QLBillingAmountData.builder()
                      .cost(resultSet.getBigDecimal(BillingDataMetaDataFields.SYSTEMCOST.getFieldName()))
                      .minStartTime(resultSet
                                        .getTimestamp(BillingDataMetaDataFields.MIN_STARTTIME.getFieldName(),
                                            utils.getDefaultCalendar())
                                        .getTime())
                      .maxStartTime(resultSet
                                        .getTimestamp(BillingDataMetaDataFields.MAX_STARTTIME.getFieldName(),
                                            utils.getDefaultCalendar())
                                        .getTime())
                      .build();
            }
            break;
          default:
        }
      }
    }
    return QLTrendStatsCostData.builder()
        .totalCostData(totalCostData)
        .idleCostData(idleCostData)
        .unallocatedCostData(unallocatedCostData)
        .systemCostData(systemCostData)
        .build();
  }
}
