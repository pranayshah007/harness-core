/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.service;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.views.entities.ViewPreferences;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGridData;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.QLCEViewTrendData;
import io.harness.ccm.views.graphql.QLCEViewTrendInfo;
import io.harness.ccm.views.graphql.ViewCostData;

import com.google.cloud.Timestamp;
import com.google.cloud.bigquery.TableResult;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(CE)
public interface ViewsBillingService {
  List<String> getFilterValueStats(List<QLCEViewFilterWrapper> filters, Integer limit, Integer offset);

  List<QLCEViewEntityStatsDataPoint> getEntityStatsDataPoints(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      Integer limit, Integer offset);

  TableResult getTimeSeriesStats(String accountId, List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort);

  QLCEViewTrendInfo getTrendStatsData(List<QLCEViewFilterWrapper> filters, List<QLCEViewAggregation> aggregateFunction);

  List<String> getColumnsForTable(String informationSchemaView, String table);

  boolean isClusterPerspective(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy);

  // For NG perspective queries
  QLCEViewGridData getEntityStatsDataPointsNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, Integer limit, Integer offset,
      ViewPreferences viewPreferences, ViewQueryParams queryParams);

  List<String> getFilterValueStatsNg(
      List<QLCEViewFilterWrapper> filters, Integer limit, Integer offset, ViewQueryParams queryParams);

  QLCEViewTrendData getTrendStatsDataNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, ViewPreferences viewPreferences, ViewQueryParams queryParams);

  TableResult getTimeSeriesStatsNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, boolean includeOthers,
      Integer limit, ViewPreferences viewPreferences, ViewQueryParams queryParams);

  Map<Long, Double> getUnallocatedCostDataNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewSortCriteria> sort, ViewPreferences viewPreferences, ViewQueryParams queryParams);

  Map<Long, Double> getOthersTotalCostDataNg(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewSortCriteria> sort, ViewPreferences viewPreferences, ViewQueryParams queryParams);

  QLCEViewTrendInfo getForecastCostData(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, ViewPreferences viewPreferences, ViewQueryParams queryParams);

  ViewCostData getCostData(
      List<QLCEViewFilterWrapper> filters, List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams);

  ViewCostData getCostData(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, ViewPreferences viewPreferences, ViewQueryParams queryParams);

  List<ValueDataPoint> getActualCostGroupedByPeriod(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, ViewQueryParams queryParams);

  Integer getTotalCountForQuery(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      ViewPreferences viewPreferences, ViewQueryParams queryParams);

  boolean isDataGroupedByAwsAccount(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy);

  Map<String, Map<String, String>> getLabelsForWorkloads(
      String accountId, Set<String> workloads, List<QLCEViewFilterWrapper> filters);

  Map<String, Map<Timestamp, Double>> getSharedCostPerTimestampFromFilters(List<QLCEViewFilterWrapper> filters,
      List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort,
      ViewPreferences viewPreferences, ViewQueryParams queryParams, boolean skipRoundOff);

  List<ViewRule> getViewRules(List<QLCEViewFilterWrapper> filters);
}
