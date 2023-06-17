/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.views.businessmapping.entities.SharingStrategy.PROPORTIONAL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.SharedCost;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.graphql.ViewsQueryMetadata;
import io.harness.ccm.views.service.DataResponseService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SetOperationQuery;
import com.healthmarketscience.sqlbuilder.UnionQuery;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class BusinessMappingSharedCostHelper {
  @Inject private ViewBillingServiceHelper viewBillingServiceHelper;
  @Inject private ViewParametersHelper viewParametersHelper;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject private DataResponseService dataResponseService;

  public ViewsQueryMetadata getFilterValueStatsSharedCostQuery(final List<QLCEViewFilterWrapper> filters,
      final String cloudProviderTableName, final Integer limit, final Integer offset, final ViewQueryParams queryParams,
      final List<BusinessMapping> sharedCostBusinessMappings) {
    final UnionQuery unionQuery = new UnionQuery(SetOperationQuery.Type.UNION_ALL);
    final SelectQuery baseQuery =
        viewBillingServiceHelper
            .getFilterValueStatsQuery(filters, cloudProviderTableName, limit, offset, queryParams, null)
            .getQuery();
    unionQuery.addQueries(String.format("(%s)", baseQuery));

    final UnionQuery sharedCostUnionQuery = getFilterValueSharedCostUnionQuery(
        filters, cloudProviderTableName, limit, offset, queryParams, sharedCostBusinessMappings);

    if (!sharedCostUnionQuery.toString().isEmpty()) {
      unionQuery.addQueries(sharedCostUnionQuery);
    }

    return viewBillingServiceHelper.getFilterValueSharedCostOuterQuery(filters, limit, offset, queryParams, unionQuery);
  }

  public SelectQuery getTimeSeriesStatsSharedCostDataQuery(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewAggregation> aggregateFunction,
      final List<QLCEViewSortCriteria> sort, final String cloudProviderTableName, final ViewQueryParams queryParams,
      final List<BusinessMapping> sharedCostBusinessMappings, final List<ViewRule> viewRules,
      final Map<String, String> labelsKeyAndColumnMapping) {
    final UnionQuery unionQuery = new UnionQuery(SetOperationQuery.Type.UNION_ALL);
    final SelectQuery baseQuery =
        viewBillingServiceHelper.getQuery(filters, groupBy, aggregateFunction, Collections.emptyList(),
            cloudProviderTableName, queryParams, sharedCostBusinessMappings, labelsKeyAndColumnMapping);
    unionQuery.addQueries(String.format("(%s)", baseQuery));

    final boolean isClusterPerspective =
        viewParametersHelper.isClusterPerspective(filters, groupBy) || queryParams.isClusterQuery();

    final UnionQuery sharedCostUnionQuery =
        getSharedCostUnionQuery(filters, groupBy, aggregateFunction, cloudProviderTableName, queryParams,
            sharedCostBusinessMappings, viewRules, isClusterPerspective, labelsKeyAndColumnMapping);

    if (Objects.isNull(sharedCostUnionQuery)) {
      return null;
    }

    if (!sharedCostUnionQuery.toString().isEmpty()) {
      unionQuery.addQueries(sharedCostUnionQuery);
    }

    return viewBillingServiceHelper.getSharedCostOuterQuery(groupBy, aggregateFunction, sort, isClusterPerspective,
        unionQuery, cloudProviderTableName, queryParams, labelsKeyAndColumnMapping);
  }

  public SelectQuery getEntityStatsSharedCostDataQueryForCostTrend(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewAggregation> aggregateFunction,
      final List<QLCEViewSortCriteria> sort, final String cloudProviderTableName, final ViewQueryParams queryParams,
      final List<BusinessMapping> sharedCostBusinessMappings, final List<ViewRule> viewRules,
      final Map<String, String> labelsKeyAndColumnMapping) {
    final UnionQuery unionQuery = new UnionQuery(SetOperationQuery.Type.UNION_ALL);
    final List<QLCEViewFilterWrapper> modifiedFilters = viewParametersHelper.getFiltersForEntityStatsCostTrend(filters);
    final List<QLCEViewAggregation> modifiedAggregateFunctions =
        viewParametersHelper.getAggregationsForEntityStatsCostTrend(aggregateFunction);
    final SelectQuery baseQuery =
        viewBillingServiceHelper.getQuery(modifiedFilters, groupBy, modifiedAggregateFunctions, Collections.emptyList(),
            cloudProviderTableName, queryParams, sharedCostBusinessMappings, labelsKeyAndColumnMapping);
    unionQuery.addQueries(String.format("(%s)", baseQuery));

    final boolean isClusterPerspective =
        viewParametersHelper.isClusterPerspective(filters, groupBy) || queryParams.isClusterQuery();

    final UnionQuery sharedCostUnionQuery =
        getSharedCostUnionQuery(modifiedFilters, groupBy, modifiedAggregateFunctions, cloudProviderTableName,
            queryParams, sharedCostBusinessMappings, viewRules, isClusterPerspective, labelsKeyAndColumnMapping);

    if (Objects.isNull(sharedCostUnionQuery)) {
      return null;
    }

    if (!sharedCostUnionQuery.toString().isEmpty()) {
      unionQuery.addQueries(sharedCostUnionQuery);
    }

    return viewBillingServiceHelper.getSharedCostOuterQuery(groupBy, modifiedAggregateFunctions, sort,
        isClusterPerspective, unionQuery, cloudProviderTableName, queryParams, labelsKeyAndColumnMapping);
  }

  public SelectQuery getEntityStatsSharedCostDataQuery(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewAggregation> aggregateFunction,
      final List<QLCEViewSortCriteria> sort, final String cloudProviderTableName, final ViewQueryParams queryParams,
      final List<BusinessMapping> sharedCostBusinessMappings, final List<ViewRule> viewRules,
      final Map<String, String> labelsKeyAndColumnMapping) {
    final UnionQuery unionQuery = new UnionQuery(SetOperationQuery.Type.UNION_ALL);
    final SelectQuery baseQuery =
        viewBillingServiceHelper.getQuery(filters, groupBy, aggregateFunction, Collections.emptyList(),
            cloudProviderTableName, queryParams, sharedCostBusinessMappings, labelsKeyAndColumnMapping);
    unionQuery.addQueries(String.format("(%s)", baseQuery));

    final boolean isClusterPerspective =
        viewParametersHelper.isClusterPerspective(filters, groupBy) || queryParams.isClusterQuery();

    final UnionQuery sharedCostUnionQuery =
        getSharedCostUnionQuery(filters, groupBy, aggregateFunction, cloudProviderTableName, queryParams,
            sharedCostBusinessMappings, viewRules, isClusterPerspective, labelsKeyAndColumnMapping);

    if (Objects.isNull(sharedCostUnionQuery)) {
      return null;
    }

    if (!sharedCostUnionQuery.toString().isEmpty()) {
      unionQuery.addQueries(sharedCostUnionQuery);
    }

    return viewBillingServiceHelper.getSharedCostOuterQuery(groupBy, aggregateFunction, sort, isClusterPerspective,
        unionQuery, cloudProviderTableName, queryParams, labelsKeyAndColumnMapping);
  }

  public SelectQuery getTotalCountSharedCostDataQuery(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final String cloudProviderTableName, final ViewQueryParams queryParams,
      final List<BusinessMapping> sharedCostBusinessMappings, final List<ViewRule> viewRules,
      final Map<String, String> labelsKeyAndColumnMapping) {
    final UnionQuery unionQuery = new UnionQuery(SetOperationQuery.Type.UNION_ALL);

    final boolean isClusterPerspective =
        viewParametersHelper.isClusterPerspective(filters, groupBy) || queryParams.isClusterQuery();

    final List<QLCEViewAggregation> aggregateFunction = viewParametersHelper.getCostAggregation(isClusterPerspective);

    final SelectQuery baseQuery =
        viewBillingServiceHelper.getQuery(filters, groupBy, aggregateFunction, Collections.emptyList(),
            cloudProviderTableName, queryParams, sharedCostBusinessMappings, labelsKeyAndColumnMapping);
    unionQuery.addQueries(String.format("(%s)", baseQuery));

    final UnionQuery sharedCostUnionQuery =
        getSharedCostUnionQuery(filters, groupBy, aggregateFunction, cloudProviderTableName, queryParams,
            sharedCostBusinessMappings, viewRules, isClusterPerspective, labelsKeyAndColumnMapping);

    if (Objects.isNull(sharedCostUnionQuery)) {
      return null;
    }

    if (!sharedCostUnionQuery.toString().isEmpty()) {
      unionQuery.addQueries(sharedCostUnionQuery);
    }

    return viewBillingServiceHelper.getTotalCountSharedCostOuterQuery(
        groupBy, unionQuery, cloudProviderTableName, isClusterPerspective, queryParams, labelsKeyAndColumnMapping);
  }

  private UnionQuery getFilterValueSharedCostUnionQuery(final List<QLCEViewFilterWrapper> filters,
      final String cloudProviderTableName, final Integer limit, final Integer offset, final ViewQueryParams queryParams,
      final List<BusinessMapping> sharedCostBusinessMappings) {
    final UnionQuery unionQuery = new UnionQuery(SetOperationQuery.Type.UNION_ALL);
    for (final BusinessMapping sharedCostBusinessMapping : sharedCostBusinessMappings) {
      final List<QLCEViewFilterWrapper> removedBusinessMappingFilters =
          viewsQueryHelper.removeBusinessMappingFilter(filters, sharedCostBusinessMapping.getUuid());
      final SelectQuery sharedCostQuery =
          viewBillingServiceHelper
              .getFilterValueStatsQuery(removedBusinessMappingFilters, cloudProviderTableName, limit, offset,
                  queryParams, sharedCostBusinessMapping)
              .getQuery();
      if (!sharedCostQuery.toString().isEmpty()) {
        unionQuery.addQueries(String.format("(%s)", sharedCostQuery));
      }
    }
    return unionQuery;
  }

  private UnionQuery getSharedCostUnionQuery(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewAggregation> aggregateFunction,
      final String cloudProviderTableName, final ViewQueryParams queryParams,
      final List<BusinessMapping> sharedCostBusinessMappings, final List<ViewRule> viewRules,
      final boolean isClusterPerspective, final Map<String, String> labelsKeyAndColumnMapping) {
    final UnionQuery unionQuery = new UnionQuery(SetOperationQuery.Type.UNION_ALL);
    for (final BusinessMapping sharedCostBusinessMapping : sharedCostBusinessMappings) {
      Map<String, Double> entityCosts = new HashMap<>();
      double totalCost = 0.0;
      if (isProportionalSharingStrategyPresent(sharedCostBusinessMapping)) {
        entityCosts =
            dataResponseService.getCostBucketEntityCost(filters, groupBy, aggregateFunction, cloudProviderTableName,
                queryParams, queryParams.isSkipRoundOff(), sharedCostBusinessMapping, labelsKeyAndColumnMapping);
        if (Objects.isNull(entityCosts)) {
          return null;
        }
        for (final Double cost : entityCosts.values()) {
          totalCost += cost;
        }
      }

      final List<String> selectedCostTargetList =
          viewsQueryHelper.getSelectedCostTargetsFromFilters(filters, viewRules, sharedCostBusinessMapping);
      final Set<String> selectedCostTargets = new HashSet<>(selectedCostTargetList);
      final List<QLCEViewFilterWrapper> removedBusinessMappingFilters =
          viewsQueryHelper.removeBusinessMappingFilter(filters, sharedCostBusinessMapping.getUuid());

      final UnionQuery sharedCostUnionQuery =
          viewBillingServiceHelper.getSharedCostUnionQuery(removedBusinessMappingFilters, groupBy, aggregateFunction,
              cloudProviderTableName, queryParams, sharedCostBusinessMapping, entityCosts, totalCost,
              selectedCostTargets, isClusterPerspective, labelsKeyAndColumnMapping);
      if (!sharedCostUnionQuery.toString().isEmpty()) {
        unionQuery.addQueries(String.format("(%s)", sharedCostUnionQuery));
      }
    }
    return unionQuery;
  }

  private boolean isProportionalSharingStrategyPresent(final BusinessMapping businessMapping) {
    boolean sharingStrategyPresent = false;
    if (Objects.nonNull(businessMapping) && Objects.nonNull(businessMapping.getSharedCosts())) {
      for (final SharedCost sharedCost : businessMapping.getSharedCosts()) {
        if (PROPORTIONAL == sharedCost.getStrategy()) {
          sharingStrategyPresent = true;
          break;
        }
      }
    }
    return sharingStrategyPresent;
  }
}
