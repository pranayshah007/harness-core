/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.ActiveSpendResultSetDTO;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.utils.CCMLicenseUsageHelper;
import io.harness.ccm.remote.beans.CostOverviewDTO;
import io.harness.ccm.service.intf.CCMActiveSpendService;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.timescaledb.DBUtils;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
public class CCMActiveSpendServiceImpl implements CCMActiveSpendService {
  private static final int THOUSAND = 1000;
  private static final long ONE_DAY_MILLIS = 86400000;
  private static final long OBSERVATION_PERIOD = 29 * ONE_DAY_MILLIS;
  private static final String SPEND_VALUE = "$%s";
  private static final String ACTIVE_SPEND_LABEL = "Total Cost";
  private static final String FORECASTED_SPEND_LABEL = "Forecast Cost";
  private static final String DATA_SET_NAME = "CE_INTERNAL";
  private static final String TABLE_NAME = "costAggregated";
  private static final String QUERY_TEMPLATE =
      "SELECT SUM(cost) AS cost, TIMESTAMP_TRUNC(day, month) AS month, cloudProvider, max(day) as max_day, "
      + "min(day) as min_day FROM `%s` WHERE day >= TIMESTAMP_MILLIS(@start_time) AND "
      + "day <= TIMESTAMP_MILLIS(@end_time) AND accountId = @account_id GROUP BY month, cloudProvider";
  private static final String QUERY_TEMPLATE_CLICKHOUSE =
      "SELECT SUM(cost) AS cost, date_trunc('month',day) AS month, cloudProvider, max(day) as max_day,"
      + " min(day) as min_day FROM %s WHERE day >= toDateTime(%s) AND day <= toDateTime(%s) "
      + "GROUP BY month, cloudProvider";

  @Inject private BigQueryService bigQueryService;
  @Inject private CENextGenConfiguration configuration;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject @Named("isClickHouseEnabled") private boolean isClickHouseEnabled;
  @Inject @Nullable @Named("clickHouseConfig") private ClickHouseConfig clickHouseConfig;
  @Inject private ClickHouseService clickHouseService;

  @Override
  public CostOverviewDTO getActiveSpendStats(long startTime, long endTime, String accountIdentifier) {
    ActiveSpendDTO activeSpendDTO = getActiveSpend(startTime, endTime, accountIdentifier);
    double activeSpend = Double.valueOf(activeSpendDTO.getCost());
    double previousPeriodActiveSpend =
        Double.valueOf(getActiveSpendForPreviousPeriod(startTime, endTime, accountIdentifier));
    double trend = getTrendForActiveSpend(activeSpend, previousPeriodActiveSpend);
    return CostOverviewDTO.builder()
        .statsLabel(ACTIVE_SPEND_LABEL)
        .statsTrend(trend)
        .statsValue(String.format(SPEND_VALUE, viewsQueryHelper.formatNumber(activeSpend)))
        .value(activeSpend)
        .build();
  }

  @Override
  public CostOverviewDTO getForecastedSpendStats(long startTime, long endTime, String accountIdentifier) {
    long endTimeForForecastedSpend = getEndTimeForForecastedSpend(startTime, endTime);
    if (endTimeForForecastedSpend == 0) {
      return CostOverviewDTO.builder().statsLabel(FORECASTED_SPEND_LABEL).statsValue("").build();
    }
    long startOfCurrentDay = viewsQueryHelper.getStartOfCurrentDay();
    long startTimeForForecastedCost = startOfCurrentDay - 30 * ONE_DAY_MILLIS;
    long endTimeForForecastedCost = startOfCurrentDay - THOUSAND;
    ActiveSpendDTO activeSpendDTO =
        getActiveSpend(startTimeForForecastedCost, endTimeForForecastedCost, accountIdentifier);
    double currentSpend = Double.valueOf(activeSpendDTO.getCost());
    long spendPeriod = ONE_DAY_MILLIS;
    if (!activeSpendDTO.getMaxDay().equals(activeSpendDTO.getMinDay())) {
      spendPeriod = (activeSpendDTO.getMaxDay() - activeSpendDTO.getMinDay()) / THOUSAND + ONE_DAY_MILLIS;
    }
    long forecastPeriod = endTimeForForecastedSpend + THOUSAND - (activeSpendDTO.getMaxDay() / THOUSAND);
    double forecastedSpend = getRoundedDoubleValue(currentSpend
        * (new BigDecimal(forecastPeriod).divide(new BigDecimal(spendPeriod), 2, RoundingMode.HALF_UP)).doubleValue());
    if (spendPeriod < OBSERVATION_PERIOD) {
      forecastedSpend = 0D;
    }
    return CostOverviewDTO.builder()
        .statsLabel(FORECASTED_SPEND_LABEL)
        .statsValue(String.format(SPEND_VALUE, viewsQueryHelper.formatNumber(forecastedSpend)))
        .value(forecastedSpend)
        .build();
  }

  private ActiveSpendDTO getActiveSpend(long startTime, long endTime, String accountIdentifier) {
    if (isClickHouseEnabled) {
      return getActiveSpendClickHouse(startTime, endTime, accountIdentifier);
    } else {
      return getActiveSpendBigQuery(startTime, endTime, accountIdentifier);
    }
  }

  private ActiveSpendDTO getActiveSpendBigQuery(long startTime, long endTime, String accountIdentifier) {
    String gcpProjectId = configuration.getGcpConfig().getGcpProjectId();
    String cloudProviderTableName = format("%s.%s.%s", gcpProjectId, DATA_SET_NAME, TABLE_NAME);
    String query = format(QUERY_TEMPLATE, cloudProviderTableName);
    log.info("Query for active spend: {}", query);
    BigQuery bigQuery = bigQueryService.get();
    QueryJobConfiguration queryConfig =
        QueryJobConfiguration.newBuilder(query)
            .addNamedParameter("start_time", QueryParameterValue.int64(startTime))
            .addNamedParameter("end_time", QueryParameterValue.int64(endTime))
            .addNamedParameter("account_id", QueryParameterValue.string(accountIdentifier))
            .build();
    TableResult result;
    try {
      result = bigQuery.query(queryConfig);
      return getActiveSpendDTO(CCMLicenseUsageHelper.getActiveSpendResultSetDTOsWithMinAndMaxDay(result));
    } catch (InterruptedException e) {
      log.error("Failed to getActiveSpend for Account:{}, {}", accountIdentifier, e);
      Thread.currentThread().interrupt();
    }
    return ActiveSpendDTO.builder().cost(0L).maxDay(0L).minDay(0L).build();
  }

  private ActiveSpendDTO getActiveSpendClickHouse(long startTime, long endTime, String accountIdentifier) {
    String cloudProviderTableName = format("%s.%s", "ccm", TABLE_NAME);
    String query = format(QUERY_TEMPLATE_CLICKHOUSE, cloudProviderTableName, startTime / THOUSAND, endTime / THOUSAND);
    log.info("Query for active spend: {}", query);
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query);
      return getActiveSpendDTO(CCMLicenseUsageHelper.getActiveSpendResultSetDTOsWithMinAndMaxDay(resultSet));
    } catch (SQLException e) {
      log.error("Failed to getActiveSpend for Account:{}, {}", accountIdentifier, e);
    } finally {
      DBUtils.close(resultSet);
    }
    return ActiveSpendDTO.builder().cost(0L).maxDay(0L).minDay(0L).build();
  }

  private static ActiveSpendDTO getActiveSpendDTO(List<ActiveSpendResultSetDTO> activeSpendResultSet) {
    long maxDay = 0L;
    long minDay = 0L;
    for (ActiveSpendResultSetDTO activeSpendResultSetDTO : activeSpendResultSet) {
      maxDay =
          maxDay == 0L ? activeSpendResultSetDTO.getMaxDay() : Math.max(maxDay, activeSpendResultSetDTO.getMaxDay());
      minDay =
          minDay == 0L ? activeSpendResultSetDTO.getMinDay() : Math.min(minDay, activeSpendResultSetDTO.getMinDay());
    }

    return ActiveSpendDTO.builder()
        .cost(CCMLicenseUsageHelper.computeDeduplicatedActiveSpend(activeSpendResultSet))
        .maxDay(maxDay)
        .minDay(minDay)
        .build();
  }

  private Long getActiveSpendForPreviousPeriod(long startTime, long endTime, String accountIdentifier) {
    long diffMillis = endTime - startTime;
    long endTimeForPreviousPeriod = startTime - THOUSAND;
    long startTimeForPreviousPeriod = endTimeForPreviousPeriod - diffMillis;
    return getActiveSpend(startTimeForPreviousPeriod, endTimeForPreviousPeriod, accountIdentifier).getCost();
  }

  private double getTrendForActiveSpend(double activeSpend, double previousPeriodActiveSpend) {
    if (previousPeriodActiveSpend == 0.0D) {
      return 0.0D;
    }
    double trend = (activeSpend - previousPeriodActiveSpend) / previousPeriodActiveSpend;
    return getRoundedDoubleValue(100 * trend);
  }

  public double getRoundedDoubleValue(double value) {
    return Math.round(value * 100D) / 100D;
  }

  private long getEndTimeForForecastedSpend(long startTime, long endTime) {
    long currentDay = viewsQueryHelper.getStartOfCurrentDay();
    long days = 0;
    if (endTime == currentDay - THOUSAND || endTime == currentDay) {
      days = (currentDay - startTime) / ONE_DAY_MILLIS;
    }
    if (endTime == currentDay + ONE_DAY_MILLIS - THOUSAND || endTime == currentDay + ONE_DAY_MILLIS) {
      days = (currentDay + ONE_DAY_MILLIS - startTime) / ONE_DAY_MILLIS;
    }
    return days != 0 ? (currentDay + (days - 1) * ONE_DAY_MILLIS) - THOUSAND : currentDay - ONE_DAY_MILLIS;
  }

  @Value
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  @EqualsAndHashCode(callSuper = false)
  private static class ActiveSpendDTO {
    Long cost;
    Long maxDay;
    Long minDay;
  }
}
