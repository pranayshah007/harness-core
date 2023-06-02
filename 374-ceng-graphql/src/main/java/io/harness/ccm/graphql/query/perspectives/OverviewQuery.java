/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.query.perspectives;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.utils.BigQueryHelper.UNIFIED_TABLE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.graphql.core.perspectives.PerspectiveTimeSeriesHelper;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.ccm.graphql.utils.annotations.GraphQLApi;
import io.harness.ccm.views.dto.PerspectiveTimeSeriesData;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.helper.ClickHouseQueryResponseHelper;
import io.harness.ccm.views.service.impl.ClickHouseViewsBillingServiceImpl;
import io.harness.timescaledb.DBUtils;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLEnvironment;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.execution.ResolutionEnvironment;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@GraphQLApi
@OwnedBy(CE)
public class OverviewQuery {
  @Inject private GraphQLUtils graphQLUtils;
  @Inject BigQueryService bigQueryService;
  @Inject BigQueryHelper bigQueryHelper;
  @Inject PerspectiveTimeSeriesHelper perspectiveTimeSeriesHelper;
  @Inject ViewsQueryBuilder viewsQueryBuilder;
  @Inject ClickHouseViewsBillingServiceImpl clickHouseViewsBillingService;
  @Inject @Named("isClickHouseEnabled") boolean isClickHouseEnabled;
  @Inject ClickHouseService clickHouseService;
  @Inject @Nullable @Named("clickHouseConfig") ClickHouseConfig clickHouseConfig;
  @Inject ClickHouseQueryResponseHelper clickHouseQueryResponseHelper;

  private static final String CLICKHOUSE_UNIFIED_TABLE = "ccm.unifiedTable";

  @GraphQLQuery(name = "overviewTimeSeriesStats", description = "Table for perspective")
  public PerspectiveTimeSeriesData overviewTimeSeriesStats(
      @GraphQLArgument(name = "aggregateFunction") List<QLCEViewAggregation> aggregateFunction,
      @GraphQLArgument(name = "filters") List<QLCEViewFilterWrapper> filters,
      @GraphQLArgument(name = "groupBy") List<QLCEViewGroupBy> groupBy,
      @GraphQLEnvironment final ResolutionEnvironment env) {
    final String accountId = graphQLUtils.getAccountIdentifier(env);
    long timePeriod = perspectiveTimeSeriesHelper.getTimePeriod(groupBy);
    List<QLCEViewTimeFilter> timeFilters = filters.stream()
                                               .filter(filter -> filter.getTimeFilter() != null)
                                               .map(QLCEViewFilterWrapper::getTimeFilter)
                                               .collect(Collectors.toList());

    if (!isClickHouseEnabled) {
      TableResult result;
      String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
      BigQuery bigQuery = bigQueryService.get();
      SelectQuery query = viewsQueryBuilder.getCostByProvidersOverviewQuery(
          timeFilters, groupBy, aggregateFunction, cloudProviderTableName);
      QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
      try {
        result = bigQuery.query(queryConfig);
      } catch (InterruptedException e) {
        log.error("Failed to get overviewTimeSeriesStats. {}", e);
        Thread.currentThread().interrupt();
        return null;
      }
      return perspectiveTimeSeriesHelper.fetch(result, timePeriod, groupBy);
    } else {
      ResultSet resultSet = null;
      SelectQuery query = viewsQueryBuilder.getCostByProvidersOverviewQuery(
          timeFilters, groupBy, aggregateFunction, CLICKHOUSE_UNIFIED_TABLE);
      try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
           Statement statement = connection.createStatement()) {
        resultSet = statement.executeQuery(query.toString());
        return clickHouseQueryResponseHelper.convertToTimeSeriesData(
            resultSet, timePeriod, null, null, null, groupBy, null, true);
      } catch (SQLException e) {
        log.error("Failed to getTrendStatsData. {}", e.toString());
      } finally {
        DBUtils.close(resultSet);
      }
      return null;
    }
  }
  public PerspectiveTimeSeriesData totalCostTimeSeriesStats(List<QLCEViewAggregation> aggregateFunction,
      List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy, String accountId) {
    List<QLCEViewTimeFilter> timeFilters = filters.stream()
                                               .filter(filter -> filter.getTimeFilter() != null)
                                               .map(QLCEViewFilterWrapper::getTimeFilter)
                                               .collect(Collectors.toList());

    long timePeriod = perspectiveTimeSeriesHelper.getTimePeriod(groupBy);

    TableResult result;
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(accountId, UNIFIED_TABLE);
    SelectQuery query =
        viewsQueryBuilder.getTotalCostTimeSeriesQuery(timeFilters, groupBy, aggregateFunction, cloudProviderTableName);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    try {
      result = bigQuery.query(queryConfig);
    } catch (InterruptedException e) {
      log.error("Failed to get totalCostTimeSeriesStats. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return perspectiveTimeSeriesHelper.fetch(result, timePeriod, groupBy);
  }
}
