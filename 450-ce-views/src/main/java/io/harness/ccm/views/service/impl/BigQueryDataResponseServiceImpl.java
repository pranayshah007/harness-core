/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.views.utils.ClusterTableKeys.BILLING_AMOUNT;
import static io.harness.ccm.views.utils.ClusterTableKeys.COST;
import static io.harness.ccm.views.utils.ClusterTableKeys.DEFAULT_GRID_ENTRY_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.helper.ViewBillingServiceHelper;
import io.harness.ccm.views.helper.ViewParametersHelper;
import io.harness.ccm.views.service.DataResponseService;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgLimitClause;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgOffsetClause;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class BigQueryDataResponseServiceImpl implements DataResponseService {
  private static final int MAX_LIMIT_VALUE = 10_000;

  @Inject private BigQueryService bigQueryService;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject private ViewBillingServiceHelper viewBillingServiceHelper;
  @Inject private ViewParametersHelper viewParametersHelper;

  @Override
  public Map<String, Double> getCostBucketEntityCost(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewAggregation> aggregateFunction,
      final String cloudProviderTableName, final ViewQueryParams queryParams, final boolean skipRoundOff,
      final BusinessMapping sharedCostBusinessMapping) {
    BigQuery bigQuery = bigQueryService.get();
    final Map<String, Double> entityCosts = new HashMap<>();
    final List<QLCEViewGroupBy> businessMappingGroupBy =
        viewsQueryHelper.createBusinessMappingGroupBy(sharedCostBusinessMapping);
    final List<QLCEViewFilterWrapper> modifiedFilters =
        viewsQueryHelper.removeBusinessMappingFilter(filters, sharedCostBusinessMapping.getUuid());
    final SelectQuery query = viewBillingServiceHelper.getQuery(modifiedFilters, groupBy, businessMappingGroupBy,
        aggregateFunction, Collections.emptyList(), cloudProviderTableName, queryParams, sharedCostBusinessMapping,
        Collections.emptyList());
    final TableResult result = getTableResultWithLimitAndOffset(bigQuery, query);

    if (Objects.isNull(result)) {
      return null;
    }

    final List<String> costTargetBucketNames =
        sharedCostBusinessMapping.getCostTargets().stream().map(CostTarget::getName).collect(Collectors.toList());

    final Schema schema = result.getSchema();
    final FieldList fields = schema.getFields();
    final String fieldName = viewParametersHelper.getEntityGroupByFieldName(groupBy);
    for (final FieldValueList row : result.iterateAll()) {
      String name = DEFAULT_GRID_ENTRY_NAME;
      Double cost = null;
      for (Field field : fields) {
        switch (field.getType().getStandardType()) {
          case STRING:
            name = fetchStringValue(row, field, fieldName);
            break;
          case FLOAT64:
            if (field.getName().equalsIgnoreCase(COST) || field.getName().equalsIgnoreCase(BILLING_AMOUNT)) {
              cost = getNumericValue(row, field, skipRoundOff);
            }
            break;
          default:
            break;
        }
      }
      if (Objects.nonNull(cost) && costTargetBucketNames.contains(name)) {
        entityCosts.put(name, entityCosts.getOrDefault(name, 0.0D) + cost);
      }
    }
    return entityCosts;
  }

  private TableResult getTableResultWithLimitAndOffset(final BigQuery bigQuery, final SelectQuery query) {
    query.addCustomization(new PgLimitClause(MAX_LIMIT_VALUE));
    query.addCustomization(new PgOffsetClause(0));
    final QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query.toString()).build();
    log.info("Query for shared cost (with limit as {}): {}", MAX_LIMIT_VALUE, query);
    TableResult result = null;
    try {
      result = bigQuery.query(queryConfig);
    } catch (final InterruptedException e) {
      log.error("Failed to get query result", e);
      Thread.currentThread().interrupt();
    }
    return result;
  }

  private double getNumericValue(final FieldValueList row, final Field field, final boolean skipRoundOff) {
    final FieldValue value = row.get(field.getName());
    if (!value.isNull()) {
      return skipRoundOff ? value.getNumericValue().doubleValue()
                          : Math.round(value.getNumericValue().doubleValue() * 100D) / 100D;
    }
    return 0;
  }

  private String fetchStringValue(final FieldValueList row, final Field field, final String fieldName) {
    final Object value = row.get(field.getName()).getValue();
    if (value != null) {
      return value.toString();
    }
    return fieldName;
  }
}
