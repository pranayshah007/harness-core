/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.DataTypeConstants.FLOAT64;
import static io.harness.ccm.commons.constants.DataTypeConstants.STRING;
import static io.harness.ccm.views.utils.ClusterTableKeys.BILLING_AMOUNT;
import static io.harness.ccm.views.utils.ClusterTableKeys.COST;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_GRID_ENTRY_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.clickHouse.ClickHouseService;
import io.harness.ccm.commons.beans.config.ClickHouseConfig;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.CostTarget;
import io.harness.ccm.views.entities.ViewPreferences;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.helper.ClickHouseQueryResponseHelper;
import io.harness.ccm.views.helper.ViewBillingServiceHelper;
import io.harness.ccm.views.helper.ViewParametersHelper;
import io.harness.ccm.views.service.DataResponseService;
import io.harness.timescaledb.DBUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgLimitClause;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgOffsetClause;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class ClickHouseDataResponseServiceImpl implements DataResponseService {
  private static final int MAX_LIMIT_VALUE = 10_000;

  @Inject @Nullable @Named("clickHouseConfig") private ClickHouseConfig clickHouseConfig;
  @Inject private ClickHouseService clickHouseService;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject private ViewBillingServiceHelper viewBillingServiceHelper;
  @Inject private ViewParametersHelper viewParametersHelper;
  @Inject private ClickHouseQueryResponseHelper clickHouseQueryResponseHelper;

  @Override
  public Map<String, Double> getCostBucketEntityCost(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewAggregation> aggregateFunction,
      final String cloudProviderTableName, final ViewQueryParams queryParams, final boolean skipRoundOff,
      final BusinessMapping sharedCostBusinessMapping, final Map<String, String> labelsKeyAndColumnMapping,
      final ViewPreferences viewPreferences) {
    final Map<String, Double> entityCosts = new HashMap<>();
    final List<QLCEViewGroupBy> businessMappingGroupBy =
        viewsQueryHelper.createBusinessMappingGroupBy(sharedCostBusinessMapping);
    final List<QLCEViewFilterWrapper> modifiedFilters =
        viewsQueryHelper.removeBusinessMappingFilter(filters, sharedCostBusinessMapping.getUuid());
    final SelectQuery query = viewBillingServiceHelper.getQuery(modifiedFilters, groupBy, businessMappingGroupBy,
        aggregateFunction, Collections.emptyList(), cloudProviderTableName, queryParams, sharedCostBusinessMapping,
        Collections.emptyList(), labelsKeyAndColumnMapping, viewPreferences);
    query.addCustomization(new PgLimitClause(MAX_LIMIT_VALUE));
    query.addCustomization(new PgOffsetClause(0));
    log.info("Query for shared cost (with limit as {}): {}", MAX_LIMIT_VALUE, query);
    ResultSet resultSet = null;
    try (Connection connection = clickHouseService.getConnection(clickHouseConfig);
         Statement statement = connection.createStatement()) {
      resultSet = statement.executeQuery(query.toString());

      final List<String> costTargetBucketNames =
          sharedCostBusinessMapping.getCostTargets().stream().map(CostTarget::getName).collect(Collectors.toList());

      int totalColumns = clickHouseQueryResponseHelper.getTotalColumnsCount(resultSet);
      final String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
      while (resultSet != null && resultSet.next()) {
        int columnIndex = 1;
        String name = DEFAULT_GRID_ENTRY_NAME;
        Double cost = null;

        while (columnIndex <= totalColumns) {
          final String columnName = resultSet.getMetaData().getColumnName(columnIndex);
          final String columnType = resultSet.getMetaData().getColumnTypeName(columnIndex);
          if (columnType.toUpperCase(Locale.ROOT).contains(STRING)) {
            name = clickHouseQueryResponseHelper.fetchStringValue(resultSet, columnName, fieldName);
          } else if (columnType.toUpperCase(Locale.ROOT).contains(FLOAT64)
              && (columnName.equalsIgnoreCase(COST) || columnName.equals(BILLING_AMOUNT))) {
            cost = clickHouseQueryResponseHelper.fetchNumericValue(resultSet, columnName, skipRoundOff);
          }
          columnIndex++;
        }
        if (Objects.nonNull(cost) && costTargetBucketNames.contains(name)) {
          entityCosts.put(name, entityCosts.getOrDefault(name, 0.0D) + cost);
        }
      }
      return entityCosts;
    } catch (final SQLException e) {
      log.error("Failed to getCostBucketEntityCost for query {}", query, e);
    } finally {
      DBUtils.close(resultSet);
    }
    return null;
  }
}
