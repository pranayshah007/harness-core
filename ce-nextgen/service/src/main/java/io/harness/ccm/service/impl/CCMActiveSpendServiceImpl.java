/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static java.lang.String.format;

import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.clickHouse.ClickHouseService;
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CCMActiveSpendServiceImpl implements CCMActiveSpendService {
  @Inject BigQueryService bigQueryService;
  @Inject CENextGenConfiguration configuration;
  @Inject ViewsQueryHelper viewsQueryHelper;
  @Inject @Named("isClickHouseEnabled") boolean isClickHouseEnabled;
  @Inject @Nullable @Named("clickHouseConfig") ClickHouseConfig clickHouseConfig;
  @Inject ClickHouseService clickHouseService;

  private static final long ONE_DAY_MILLIS = 86400000;
  private static final String SPEND_VALUE = "$%s";
  private static final String ACTIVE_SPEND_LABEL = "Total Cost";
  private static final String FORECASTED_SPEND_LABEL = "Forecast Cost";
  public static final String DATA_SET_NAME = "CE_INTERNAL";
  public static final String TABLE_NAME = "costAggregated";
  public static final String QUERY_TEMPLATE =
      "SELECT SUM(cost) AS cost, TIMESTAMP_TRUNC(day, month) AS month, cloudProvider FROM `%s` "
      + "WHERE day >= TIMESTAMP_MILLIS(@start_time) AND day <= TIMESTAMP_MILLIS(@end_time) AND accountId = @account_id GROUP BY month, cloudProvider";
  public static final String QUERY_TEMPLATE_CLICKHOUSE =
      "SELECT SUM(cost) AS cost, date_trunc('month',day) AS month, cloudProvider FROM %s "
      + "WHERE day >= toDateTime(%s) AND day <= toDateTime(%s) GROUP BY month, cloudProvider";

  @Override
  public CostOverviewDTO getActiveSpendStats(long startTime, long endTime, String accountIdentifier) {
    double activeSpend = Double.valueOf(getActiveSpend(startTime, endTime, accountIdentifier));
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
    long startTimeForForecastedSpend = endTime - 30 * ONE_DAY_MILLIS;
    double currentSpend = Double.valueOf(getActiveSpend(startTimeForForecastedSpend, endTime, accountIdentifier));
    double spendPeriod = 30;
    double forecastPeriod = (endTimeForForecastedSpend - endTime - ONE_DAY_MILLIS) / (double) ONE_DAY_MILLIS;
    double forecastedSpend = getRoundedDoubleValue((currentSpend * forecastPeriod) / spendPeriod);
    return CostOverviewDTO.builder()
        .statsLabel(FORECASTED_SPEND_LABEL)
        .statsValue(String.format(SPEND_VALUE, viewsQueryHelper.formatNumber(forecastedSpend)))
        .value(forecastedSpend)
        .build();
  }

  @Override
  public Long getActiveSpend(long startTime, long endTime, String accountIdentifier) {
    if (isClickHouseEnabled) {
      return getActiveSpendClickHouse(startTime, endTime, accountIdentifier);
    } else {
      return getActiveSpendBigQuery(startTime, endTime, accountIdentifier);
    }
  }

  private Long getActiveSpendBigQuery(long startTime, long endTime, String accountIdentifier) {
    String gcpProjectId = configuration.getGcpConfig().getGcpProjectId();
    String cloudProviderTableName = format("%s.%s.%s", gcpProjectId, DATA_SET_NAME, TABLE_NAME);
    String query = format(QUERY_TEMPLATE, cloudProviderTableName);
    log.info("Query: {}", query);
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
    } catch (InterruptedException e) {
      log.error("Failed to getActiveSpend for Account:{}, {}", accountIdentifier, e);
      Thread.currentThread().interrupt();
      return null;
    }
    return CCMLicenseUsageHelper.computeDeduplicatedActiveSpend(result);
  }

  private Long getActiveSpendClickHouse(long startTime, long endTime, String accountIdentifier) {
    String cloudProviderTableName = format("%s.%s", "ccm", TABLE_NAME);
    String query = format(QUERY_TEMPLATE_CLICKHOUSE, cloudProviderTableName, startTime / 1000, endTime / 1000);
    log.info("Query: {}", query);
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query);
      return CCMLicenseUsageHelper.computeDeduplicatedActiveSpend(resultSet);
    } catch (SQLException e) {
      log.error("Failed to getActiveSpend for Account:{}, {}", accountIdentifier, e);
    } finally {
      DBUtils.close(resultSet);
    }
    return 0L;
  }

  @Override
  public Long getActiveSpendForPreviousPeriod(long startTime, long endTime, String accountIdentifier) {
    long diffMillis = endTime - startTime;
    long endTimeForPreviousPeriod = startTime - 1000;
    long startTimeForPreviousPeriod = endTimeForPreviousPeriod - diffMillis;
    return getActiveSpend(startTimeForPreviousPeriod, endTimeForPreviousPeriod, accountIdentifier);
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
    if (endTime == currentDay - 1000 || endTime == currentDay) {
      days = (currentDay - startTime) / ONE_DAY_MILLIS;
    }
    if (endTime == currentDay + ONE_DAY_MILLIS - 1000 || endTime == currentDay + ONE_DAY_MILLIS) {
      days = (currentDay + ONE_DAY_MILLIS - startTime) / ONE_DAY_MILLIS;
    }
    return days != 0 ? currentDay + (days - 1) * ONE_DAY_MILLIS - 1000 : 0;
  }
}
