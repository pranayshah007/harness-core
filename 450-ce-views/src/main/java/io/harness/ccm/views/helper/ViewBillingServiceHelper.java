/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.commons.constants.ViewFieldConstants.CLOUD_SERVICE_NAME_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.LAUNCH_TYPE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.NAMESPACE_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.TASK_FIELD_ID;
import static io.harness.ccm.commons.constants.ViewFieldConstants.WORKLOAD_NAME_FIELD_ID;
import static io.harness.ccm.views.businessMapping.entities.SharingStrategy.PROPORTIONAL;
import static io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator.AFTER;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE_AGGREGRATED;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE_HOURLY;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE_HOURLY_AGGREGRATED;

import static java.lang.String.format;

import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.currency.Currency;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.businessMapping.entities.SharedCost;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ClusterData;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewTimeGranularity;
import io.harness.ccm.views.entities.ViewVisualization;
import io.harness.ccm.views.graphql.QLCEInExpressionFilter;
import io.harness.ccm.views.graphql.QLCEViewAggregation;
import io.harness.ccm.views.graphql.QLCEViewDataPoint;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint;
import io.harness.ccm.views.graphql.QLCEViewEntityStatsDataPoint.QLCEViewEntityStatsDataPointBuilder;
import io.harness.ccm.views.graphql.QLCEViewFieldInput;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewFilterWrapper;
import io.harness.ccm.views.graphql.QLCEViewGridData;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewMetadataFilter;
import io.harness.ccm.views.graphql.QLCEViewRule;
import io.harness.ccm.views.graphql.QLCEViewSortCriteria;
import io.harness.ccm.views.graphql.QLCEViewTimeFilter;
import io.harness.ccm.views.graphql.QLCEViewTimeFilterOperator;
import io.harness.ccm.views.graphql.QLCEViewTimeSeriesData;
import io.harness.ccm.views.graphql.QLCEViewTrendInfo;
import io.harness.ccm.views.graphql.ViewCostData;
import io.harness.ccm.views.graphql.ViewsQueryBuilder;
import io.harness.ccm.views.graphql.ViewsQueryHelper;
import io.harness.ccm.views.graphql.ViewsQueryMetadata;
import io.harness.ccm.views.service.CEViewService;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SetOperationQuery;
import com.healthmarketscience.sqlbuilder.UnionQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ViewBillingServiceHelper {
  @Inject private ViewsQueryBuilder viewsQueryBuilder;
  @Inject private CEViewService viewService;
  @Inject private ViewsQueryHelper viewsQueryHelper;
  @Inject private InstanceDetailsHelper instanceDetailsHelper;
  @Inject private EntityMetadataService entityMetadataService;
  @Inject private BusinessMappingService businessMappingService;
  @Inject private AwsAccountFieldHelper awsAccountFieldHelper;
  @Inject private BusinessMappingDataSourceHelper businessMappingDataSourceHelper;
  @Inject private CEMetadataRecordDao ceMetadataRecordDao;
  @Inject private ViewParametersHelper viewParametersHelper;
  @Inject @Named("isClickHouseEnabled") boolean isClickHouseEnabled;

  private static final String COST_DESCRIPTION = "of %s - %s";
  private static final String OTHER_COST_DESCRIPTION = "%s of total";
  private static final String COST_VALUE = "%s%s";
  private static final String TOTAL_COST_LABEL = "Total Cost";
  private static final String TOTAL_CLUSTER_COST_LABEL = "Total Cluster Cost";
  private static final String FORECAST_COST_LABEL = "Forecasted Cost";
  private static final String IDLE_COST_LABEL = "Idle Cost";
  private static final String UNALLOCATED_COST_LABEL = "Unallocated Cost";
  private static final String UTILIZED_COST_LABEL = "Utilized Cost";
  private static final String SYSTEM_COST_LABEL = "System Cost";
  private static final String EMPTY_VALUE = "-";
  private static final String DATE_PATTERN_FOR_CHART = "MMM dd";
  private static final long ONE_DAY_MILLIS = 86400000L;
  private static final long OBSERVATION_PERIOD = 29 * ONE_DAY_MILLIS;
  private static final List<String> UNALLOCATED_COST_CLUSTER_FIELDS = ImmutableList.of(
      NAMESPACE_FIELD_ID, WORKLOAD_NAME_FIELD_ID, CLOUD_SERVICE_NAME_FIELD_ID, TASK_FIELD_ID, LAUNCH_TYPE_FIELD_ID);

  // ----------------------------------------------------------------------------------------------------------------
  // Query for filter values
  // ----------------------------------------------------------------------------------------------------------------
  public ViewsQueryMetadata getFilterValueStatsQuery(List<QLCEViewFilterWrapper> filters, String cloudProviderTableName,
      Integer limit, Integer offset, ViewQueryParams queryParams, final BusinessMapping sharedCostBusinessMapping) {
    boolean isClusterQuery = viewParametersHelper.isClusterTableQuery(filters, Collections.emptyList(), queryParams);

    List<ViewRule> viewRuleList = new ArrayList<>();
    Optional<QLCEViewFilterWrapper> viewMetadataFilter = viewParametersHelper.getViewMetadataFilter(filters);

    List<QLCEViewFilter> idFilters = getIdFiltersForFilterValues(filters, queryParams);

    if (viewMetadataFilter.isPresent()) {
      QLCEViewMetadataFilter metadataFilter = viewMetadataFilter.get().getViewMetadataFilter();
      final String viewId = metadataFilter.getViewId();
      if (!metadataFilter.isPreview()) {
        CEView ceView = viewService.get(viewId);
        viewRuleList = ceView.getViewRules();
      }
    }

    if (sharedCostBusinessMapping != null) {
      for (SharedCost sharedCost : sharedCostBusinessMapping.getSharedCosts()) {
        if (sharedCost.getRules() != null) {
          viewRuleList.addAll(sharedCost.getRules());
        }
      }
    }

    // account id is not passed in current gen queries
    if (queryParams.getAccountId() != null) {
      cloudProviderTableName = getUpdatedCloudProviderTableName(
          filters, null, null, queryParams.getAccountId(), cloudProviderTableName, isClusterQuery);
    }

    boolean isLimitRequired = !viewParametersHelper.isDataFilteredByAwsAccount(idFilters);

    return viewsQueryBuilder.getFilterValuesQuery(viewRuleList, idFilters, viewsQueryHelper.getTimeFilters(filters),
        cloudProviderTableName, limit, offset, isLimitRequired, false);
  }

  private List<QLCEViewFilter> getIdFiltersForFilterValues(
      final List<QLCEViewFilterWrapper> filters, final ViewQueryParams queryParams) {
    // In case of AWS Account filter, we might get multiple values for QLCEViewFilter if user is filtering on AWS
    // Account name
    // First value is the original filter string, the others are awsAccountIds
    return awsAccountFieldHelper.addAccountIdsByAwsAccountNameFilter(
        viewParametersHelper.getIdFilters(filters), queryParams.getAccountId());
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Query for Shared Cost
  // ----------------------------------------------------------------------------------------------------------------
  public UnionQuery getSharedCostUnionQuery(final List<QLCEViewFilterWrapper> filters,
      final List<QLCEViewGroupBy> groupBy, final List<QLCEViewAggregation> aggregateFunction,
      final String cloudProviderTableName, final ViewQueryParams queryParams,
      final BusinessMapping sharedCostBusinessMapping, final Map<String, Double> entityCosts, final double totalCost,
      final Set<String> selectedCostTargets, final boolean isClusterPerspective) {
    final UnionQuery unionQuery = new UnionQuery(SetOperationQuery.Type.UNION_ALL);
    List<QLCEViewAggregation> modifiedAggregateFunction = aggregateFunction;
    List<QLCEViewGroupBy> modifiedGroupBy = groupBy;
    if (viewsQueryHelper.isGroupByNonePresent(modifiedGroupBy)) {
      modifiedGroupBy = viewsQueryHelper.removeGroupByNone(modifiedGroupBy);
    }
    if (isClusterPerspective) {
      modifiedGroupBy = viewParametersHelper.addAdditionalRequiredGroupBy(modifiedGroupBy);
      // Changes column name for cost to billingAmount
      modifiedAggregateFunction = viewParametersHelper.getModifiedAggregations(aggregateFunction);
    }
    for (final CostTarget costTarget : sharedCostBusinessMapping.getCostTargets()) {
      if (selectedCostTargets.contains(costTarget.getName())) {
        for (final SharedCost sharedCost : sharedCostBusinessMapping.getSharedCosts()) {
          if (shouldAddSharedCostQuery(entityCosts, totalCost, costTarget, sharedCost)) {
            final BusinessMapping businessMapping = sharedCostBusinessMapping.toDTO();
            businessMapping.setSharedCosts(List.of(sharedCost));
            final SelectQuery selectQuery =
                viewsQueryBuilder.getSharedCostQuery(modifiedGroupBy, modifiedAggregateFunction, entityCosts, totalCost,
                    costTarget, sharedCost, businessMapping, cloudProviderTableName, isClusterPerspective);
            final SelectQuery subQuery = getQuery(filters, groupBy, aggregateFunction, Collections.emptyList(),
                cloudProviderTableName, queryParams, businessMapping, Collections.emptyList());
            selectQuery.addCustomFromTable(String.format("(%s)", subQuery.toString()));
            unionQuery.addQueries(String.format("(%s)", selectQuery));
          }
        }
      }
    }
    return unionQuery;
  }

  private boolean shouldAddSharedCostQuery(final Map<String, Double> entityCosts, final double totalCost,
      final CostTarget costTarget, final SharedCost sharedCost) {
    return !(sharedCost.getStrategy() == PROPORTIONAL && Double.compare(totalCost, 0.0D) <= 0
        && Double.compare(entityCosts.getOrDefault(costTarget.getName(), 0.0D), 0.0D) <= 0);
  }

  public ViewsQueryMetadata getFilterValueSharedCostOuterQuery(final List<QLCEViewFilterWrapper> filters,
      final Integer limit, final Integer offset, final ViewQueryParams queryParams, final UnionQuery unionQuery) {
    final List<QLCEViewFilter> idFilters = getIdFiltersForFilterValues(filters, queryParams);
    final boolean isLimitRequired = !viewParametersHelper.isDataFilteredByAwsAccount(idFilters);
    return viewsQueryBuilder.getFilterValuesQuery(Collections.emptyList(), idFilters, Collections.emptyList(),
        String.format("(%s)", unionQuery), limit, offset, isLimitRequired, true);
  }

  public SelectQuery getSharedCostOuterQuery(final List<QLCEViewGroupBy> groupBy,
      final List<QLCEViewAggregation> aggregateFunction, final List<QLCEViewSortCriteria> sort,
      final boolean isClusterPerspective, final UnionQuery unionQuery, final String cloudProviderTableName) {
    List<QLCEViewAggregation> modifiedAggregateFunction = aggregateFunction;
    List<QLCEViewSortCriteria> modifiedSort = sort;
    List<QLCEViewGroupBy> modifiedGroupBy = groupBy;
    if (viewsQueryHelper.isGroupByNonePresent(modifiedGroupBy)) {
      modifiedGroupBy = viewsQueryHelper.removeGroupByNone(groupBy);
    }
    if (isClusterPerspective) {
      modifiedGroupBy = viewParametersHelper.addAdditionalRequiredGroupBy(modifiedGroupBy);
      // Changes column name for cost to billingAmount
      modifiedAggregateFunction = viewParametersHelper.getModifiedAggregations(aggregateFunction);
      modifiedSort = viewParametersHelper.getModifiedSort(sort);
    }
    return viewsQueryBuilder.getSharedCostOuterQuery(modifiedGroupBy, modifiedAggregateFunction, modifiedSort,
        unionQuery, cloudProviderTableName, isClusterPerspective);
  }

  public SelectQuery getTotalCountSharedCostOuterQuery(final List<QLCEViewGroupBy> groupBy, final UnionQuery unionQuery,
      final String cloudProviderTableName, final boolean isClusterPerspective) {
    List<QLCEViewGroupBy> modifiedGroupBy = groupBy;
    if (viewsQueryHelper.isGroupByNonePresent(modifiedGroupBy)) {
      modifiedGroupBy = viewsQueryHelper.removeGroupByNone(groupBy);
    }
    if (isClusterPerspective) {
      modifiedGroupBy = viewParametersHelper.addAdditionalRequiredGroupBy(modifiedGroupBy);
    }
    return viewsQueryBuilder.getTotalCountSharedCostOuterQuery(modifiedGroupBy, unionQuery, cloudProviderTableName);
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Query for Chart
  // ----------------------------------------------------------------------------------------------------------------
  public List<QLCEViewFilterWrapper> getModifiedFiltersForTimeSeriesStats(
      List<QLCEViewFilterWrapper> filters, final QLCEViewGridData gridData, final List<QLCEViewGroupBy> entityGroupBy) {
    final List<QLCEViewFilterWrapper> modifiedFilters = new ArrayList<>();
    if (filters != null) {
      modifiedFilters.addAll(filters);
    }
    if (gridData != null) {
      final List<String> fields = gridData.getFields();
      final List<List<String>> inValues = getInValuesList(gridData, fields);
      if (!inValues.isEmpty()) {
        final List<QLCEViewFieldInput> qlCEViewFieldInputs = viewParametersHelper.getInFieldsList(fields);
        final String nullValueField = viewParametersHelper.getNullValueField(entityGroupBy, inValues);
        modifiedFilters.add(QLCEViewFilterWrapper.builder()
                                .inExpressionFilter(QLCEInExpressionFilter.builder()
                                                        .fields(qlCEViewFieldInputs)
                                                        .values(inValues)
                                                        .nullValueField(nullValueField)
                                                        .build())
                                .build());
      }
    }
    return modifiedFilters;
  }

  @NotNull
  public List<List<String>> getInValuesList(final QLCEViewGridData gridData, final List<String> fields) {
    final List<List<String>> inValues = new ArrayList<>();

    final Map<String, java.lang.reflect.Field> clusterDataFields = new HashMap<>();
    for (final java.lang.reflect.Field clusterField : ClusterData.class.getDeclaredFields()) {
      clusterDataFields.put(clusterField.getName().toLowerCase(), clusterField);
    }

    for (final QLCEViewEntityStatsDataPoint dataPoint : gridData.getData()) {
      final ClusterData clusterData = dataPoint.getClusterData();
      final List<String> values = new ArrayList<>();
      for (final String field : fields) {
        if (Objects.nonNull(clusterData)) {
          final java.lang.reflect.Field clusterField = clusterDataFields.get(field.toLowerCase());
          if (Objects.nonNull(clusterField)) {
            clusterField.setAccessible(true);
            try {
              values.add((String) clusterField.get(clusterData));
            } catch (final IllegalAccessException e) {
              values.add("");
              log.error("Unable to fetch field {} value for clusterData: {}", field, clusterData, e);
            }
          } else {
            values.add(clusterData.getId());
          }
        } else {
          values.add(dataPoint.getId());
        }
      }
      inValues.add(values);
    }
    return inValues;
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods for post processing of chart response
  // ----------------------------------------------------------------------------------------------------------------
  public Map<Long, List<QLCEViewDataPoint>> modifyTimeSeriesDataPointsMap(
      final Map<Long, List<QLCEViewDataPoint>> timeSeriesDataPointsMap, final Set<String> awsAccounts,
      final String accountId) {
    Map<Long, List<QLCEViewDataPoint>> updatedTimeSeriesDataPointsMap = timeSeriesDataPointsMap;
    if (!awsAccounts.isEmpty()) {
      final Map<String, String> entityIdToName =
          entityMetadataService.getEntityIdToNameMapping(new ArrayList<>(awsAccounts), accountId, AWS_ACCOUNT_FIELD);
      updatedTimeSeriesDataPointsMap =
          timeSeriesDataPointsMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
              timeSeriesDataPoint -> getUpdatedTimeSeriesDataPoints(entityIdToName, timeSeriesDataPoint.getValue())));
    }
    return updatedTimeSeriesDataPointsMap;
  }

  private List<QLCEViewDataPoint> getUpdatedTimeSeriesDataPoints(
      final Map<String, String> entityIdToName, final List<QLCEViewDataPoint> timeSeriesDataPoints) {
    final List<QLCEViewDataPoint> updatedTimeSeriesDataPoints = new ArrayList<>();
    timeSeriesDataPoints.forEach(timeSeriesDataPoint
        -> updatedTimeSeriesDataPoints.add(
            QLCEViewDataPoint.builder()
                .id(timeSeriesDataPoint.getId())
                .value(timeSeriesDataPoint.getValue())
                .name(AwsAccountFieldHelper.mergeAwsAccountIdAndName(
                    timeSeriesDataPoint.getId(), entityIdToName.get(timeSeriesDataPoint.getId())))
                .build()));
    return updatedTimeSeriesDataPoints;
  }

  public List<QLCEViewTimeSeriesData> convertTimeSeriesPointsMapToList(
      Map<Long, List<QLCEViewDataPoint>> timeSeriesDataPointsMap) {
    return timeSeriesDataPointsMap.entrySet()
        .stream()
        .map(e
            -> QLCEViewTimeSeriesData.builder()
                   .time(e.getKey())
                   .date(
                       viewParametersHelper.getFormattedDate(Instant.ofEpochMilli(e.getKey()), DATE_PATTERN_FOR_CHART))
                   .values(e.getValue())
                   .build())
        .sorted(Comparator.comparing(QLCEViewTimeSeriesData::getTime))
        .collect(Collectors.toList());
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Query for Cost summary
  // ----------------------------------------------------------------------------------------------------------------
  public SelectQuery getTrendStatsQuery(List<QLCEViewFilterWrapper> filters, List<QLCEViewFilter> idFilters,
      List<QLCEViewTimeFilter> timeFilters, List<QLCEViewGroupBy> groupBy, List<QLCEViewAggregation> aggregateFunction,
      List<ViewRule> viewRuleList, String cloudProviderTableName, ViewQueryParams queryParams,
      List<BusinessMapping> sharedCostBusinessMappings) {
    Optional<QLCEViewFilterWrapper> viewMetadataFilter = viewParametersHelper.getViewMetadataFilter(filters);
    if (viewMetadataFilter.isPresent()) {
      final String viewId = viewMetadataFilter.get().getViewMetadataFilter().getViewId();
      CEView ceView = viewService.get(viewId);
      viewRuleList = ceView.getViewRules();
    }
    // account id is not passed in current gen queries
    if (queryParams.getAccountId() != null) {
      if (viewParametersHelper.isClusterPerspective(filters, groupBy)) {
        // Changes column name for cost to billingamount
        aggregateFunction = viewParametersHelper.getModifiedAggregations(aggregateFunction);
      }
      cloudProviderTableName = getUpdatedCloudProviderTableName(
          filters, null, aggregateFunction, "", cloudProviderTableName, queryParams.isClusterQuery());
    }
    return viewsQueryBuilder.getQuery(viewRuleList, idFilters, timeFilters, groupBy, aggregateFunction,
        Collections.emptyList(), cloudProviderTableName, sharedCostBusinessMappings);
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods related to cost overview and forecast cost
  // ----------------------------------------------------------------------------------------------------------------
  public Double getForecastCost(ViewCostData costData, Instant endInstant) {
    if (costData == null) {
      return null;
    }
    Instant currentTime = Instant.now();
    if (currentTime.isAfter(endInstant)) {
      return null;
    }

    double totalCost = costData.getCost();
    long actualTimeDiffMillis =
        (endInstant.plus(1, ChronoUnit.SECONDS).toEpochMilli()) - (costData.getMaxStartTime() / 1000);

    long billingTimeDiffMillis = ONE_DAY_MILLIS;
    if (costData.getMaxStartTime() != costData.getMinStartTime()) {
      billingTimeDiffMillis = (costData.getMaxStartTime() - costData.getMinStartTime()) / 1000 + ONE_DAY_MILLIS;
    }
    if (billingTimeDiffMillis < OBSERVATION_PERIOD) {
      return null;
    }

    return totalCost
        * (new BigDecimal(actualTimeDiffMillis).divide(new BigDecimal(billingTimeDiffMillis), 2, RoundingMode.HALF_UP))
              .doubleValue();
  }

  public QLCEViewTrendInfo getForecastCostBillingStats(
      Double forecastCost, Double totalCost, Instant startInstant, Instant endInstant, Currency currency) {
    String forecastCostDescription = "";
    String forecastCostValue = "";
    double statsTrend = 0.0;

    if (forecastCost != null) {
      boolean isYearRequired = viewsQueryHelper.isYearRequired(startInstant, endInstant);
      String startInstantFormat = viewsQueryHelper.getTotalCostFormattedDate(startInstant, isYearRequired);
      String endInstantFormat = viewsQueryHelper.getTotalCostFormattedDate(endInstant, isYearRequired);
      forecastCostDescription = format(COST_DESCRIPTION, startInstantFormat, endInstantFormat);
      forecastCostValue = format(COST_VALUE, currency != null ? currency.getSymbol() : "$",
          viewsQueryHelper.formatNumber(viewsQueryHelper.getRoundedDoubleValue(forecastCost)));
      statsTrend = viewsQueryHelper.getRoundedDoubleValue(((forecastCost - totalCost) / totalCost) * 100);
    } else {
      forecastCost = 0.0;
    }

    return QLCEViewTrendInfo.builder()
        .statsLabel(FORECAST_COST_LABEL)
        .statsTrend(statsTrend)
        .statsDescription(forecastCostDescription)
        .statsValue(forecastCostValue)
        .value(forecastCost)
        .build();
  }

  public QLCEViewTrendInfo getOtherCostBillingStats(ViewCostData costData, String costLabel, Currency currency) {
    if (costData == null) {
      return null;
    }
    Double otherCost;
    double totalCost = costData.getCost();
    String otherCostDescription = EMPTY_VALUE;
    String otherCostValue = EMPTY_VALUE;
    switch (costLabel) {
      case IDLE_COST_LABEL:
        otherCost = costData.getIdleCost();
        break;
      case UNALLOCATED_COST_LABEL:
        otherCost = costData.getUnallocatedCost();
        break;
      case UTILIZED_COST_LABEL:
        otherCost = costData.getUtilizedCost();
        break;
      case SYSTEM_COST_LABEL:
        otherCost = costData.getSystemCost();
        break;
      default:
        return null;
    }
    if (otherCost != null) {
      otherCostValue = String.format(COST_VALUE, currency != null ? currency.getSymbol() : "$",
          viewsQueryHelper.formatNumber(viewsQueryHelper.getRoundedDoubleValue(otherCost)));
      if (totalCost != 0) {
        double percentageOfTotalCost = viewsQueryHelper.getRoundedDoublePercentageValue(otherCost / totalCost);
        otherCostDescription = String.format(OTHER_COST_DESCRIPTION, percentageOfTotalCost + "%");
      }
    }

    return QLCEViewTrendInfo.builder()
        .statsLabel(costLabel)
        .statsDescription(otherCostDescription)
        .statsValue(otherCostValue)
        .value(otherCost)
        .build();
  }

  public double getCostTrendForEntity(Double currentCost, ViewCostData prevCostData, long startTimeForTrend) {
    if (prevCostData != null && prevCostData.getCost() != 0 && currentCost != null) {
      double prevCost = prevCostData.getCost();
      long startTimeForPrevCost = prevCostData.getMinStartTime() / 1000;
      double costDifference = currentCost - prevCost;
      if (startTimeForTrend + ONE_DAY_MILLIS > startTimeForPrevCost) {
        return Math.round((costDifference * 100 / prevCost) * 100D) / 100D;
      }
    }
    return 0;
  }

  public QLCEViewTrendInfo getCostBillingStats(ViewCostData costData, ViewCostData prevCostData,
      List<QLCEViewTimeFilter> filters, Instant trendFilterStartTime, boolean isClusterTableQuery, Currency currency) {
    Instant startInstant = Instant.ofEpochMilli(viewsQueryHelper.getTimeFilter(filters, AFTER).getValue().longValue());
    Instant endInstant = Instant.ofEpochMilli(costData.getMaxStartTime() / 1000);
    if (isClickHouseEnabled && !isClusterTableQuery) {
      endInstant = Instant.ofEpochMilli(costData.getMaxStartTime());
    }
    if (costData.getMaxStartTime() == 0) {
      endInstant = Instant.ofEpochMilli(
          viewsQueryHelper.getTimeFilter(filters, QLCEViewTimeFilterOperator.BEFORE).getValue().longValue());
    }
    boolean isYearRequired = viewsQueryHelper.isYearRequired(startInstant, endInstant);
    String startInstantFormat = viewsQueryHelper.getTotalCostFormattedDate(startInstant, isYearRequired);
    String endInstantFormat = viewsQueryHelper.getTotalCostFormattedDate(endInstant, isYearRequired);
    String totalCostDescription = format(COST_DESCRIPTION, startInstantFormat, endInstantFormat);
    String totalCostValue = format(COST_VALUE, currency != null ? currency.getSymbol() : "$",
        viewsQueryHelper.formatNumber(viewsQueryHelper.getRoundedDoubleValue(costData.getCost())));

    double forecastCost = viewsQueryHelper.getForecastCost(ViewCostData.builder()
                                                               .cost(costData.getCost())
                                                               .minStartTime(costData.getMinStartTime() / 1000)
                                                               .maxStartTime(costData.getMaxStartTime() / 1000)
                                                               .build(),
        Instant.ofEpochMilli(
            viewsQueryHelper.getTimeFilter(filters, QLCEViewTimeFilterOperator.BEFORE).getValue().longValue()));

    return QLCEViewTrendInfo.builder()
        .statsLabel(isClusterTableQuery ? TOTAL_CLUSTER_COST_LABEL : TOTAL_COST_LABEL)
        .statsDescription(totalCostDescription)
        .statsValue(totalCostValue)
        .statsTrend(
            viewsQueryHelper.getBillingTrend(costData.getCost(), forecastCost, prevCostData, trendFilterStartTime))
        .value(costData.getCost())
        .build();
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods to modify perspective query parameters and obtain query
  // ----------------------------------------------------------------------------------------------------------------
  // Current gen
  public SelectQuery getQuery(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, String cloudProviderTableName,
      boolean isTimeTruncGroupByRequired) {
    return getQuery(filters, groupBy, aggregateFunction, sort, cloudProviderTableName,
        viewsQueryHelper.buildQueryParams(null, isTimeTruncGroupByRequired, false, false, false),
        Collections.emptyList());
  }

  // Next-gen
  public SelectQuery getQuery(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, String cloudProviderTableName,
      ViewQueryParams queryParams, List<BusinessMapping> sharedCostBusinessMappings) {
    return getQuery(filters, groupBy, aggregateFunction, sort, cloudProviderTableName, queryParams, null,
        sharedCostBusinessMappings);
  }

  // Next-gen
  public SelectQuery getQuery(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, List<QLCEViewSortCriteria> sort, String cloudProviderTableName,
      ViewQueryParams queryParams, BusinessMapping sharedCostBusinessMapping,
      List<BusinessMapping> sharedCostBusinessMappings) {
    List<ViewRule> viewRuleList = new ArrayList<>();

    // Removing group by none if present
    boolean skipDefaultGroupBy = queryParams.isSkipDefaultGroupBy();
    if (viewsQueryHelper.isGroupByNonePresent(groupBy)) {
      skipDefaultGroupBy = true;
      groupBy = viewsQueryHelper.removeGroupByNone(groupBy);
    }

    List<QLCEViewGroupBy> modifiedGroupBy = groupBy != null ? new ArrayList<>(groupBy) : new ArrayList<>();
    Optional<QLCEViewFilterWrapper> viewMetadataFilter = viewParametersHelper.getViewMetadataFilter(filters);

    List<QLCEViewRule> rules =
        AwsAccountFieldHelper.removeAccountNameFromAWSAccountRuleFilter(viewParametersHelper.getRuleFilters(filters));
    if (!rules.isEmpty()) {
      for (QLCEViewRule rule : rules) {
        viewRuleList.add(viewParametersHelper.convertQLCEViewRuleToViewRule(rule));
      }
    }

    if (viewMetadataFilter.isPresent()) {
      QLCEViewMetadataFilter metadataFilter = viewMetadataFilter.get().getViewMetadataFilter();
      final String viewId = metadataFilter.getViewId();
      if (!metadataFilter.isPreview()) {
        CEView ceView = viewService.get(viewId);
        viewRuleList = ceView.getViewRules();
        if (ceView.getViewVisualization() != null) {
          ViewVisualization viewVisualization = ceView.getViewVisualization();
          ViewField defaultGroupByField = viewVisualization.getGroupBy();
          ViewTimeGranularity defaultTimeGranularity = viewVisualization.getGranularity();
          if (defaultTimeGranularity == null) {
            defaultTimeGranularity = ViewTimeGranularity.DAY;
          }
          modifiedGroupBy = viewParametersHelper.getModifiedGroupBy(groupBy, defaultGroupByField,
              defaultTimeGranularity, queryParams.isTimeTruncGroupByRequired(), skipDefaultGroupBy);
        }
      }
    }
    List<QLCEViewFilter> idFilters =
        AwsAccountFieldHelper.removeAccountNameFromAWSAccountIdFilter(viewParametersHelper.getIdFilters(filters));
    List<QLCEViewTimeFilter> timeFilters = viewsQueryHelper.getTimeFilters(filters);

    // account id is not passed in current gen queries
    if (queryParams.getAccountId() != null) {
      boolean isPodQuery = false;
      if (viewParametersHelper.isClusterPerspective(filters, groupBy) || queryParams.isClusterQuery()) {
        isPodQuery = viewParametersHelper.isPodQuery(modifiedGroupBy);
        if (viewParametersHelper.isInstanceDetailsQuery(modifiedGroupBy)) {
          idFilters.add(viewParametersHelper.getFilterForInstanceDetails(modifiedGroupBy));
        }
        modifiedGroupBy = viewParametersHelper.addAdditionalRequiredGroupBy(modifiedGroupBy);
        // Changes column name for product to clusterName in case of cluster perspective
        idFilters = viewParametersHelper.getModifiedIdFilters(
            viewParametersHelper.addNotNullFilters(idFilters, modifiedGroupBy), true);
        viewRuleList = viewParametersHelper.getModifiedRuleFilters(viewRuleList);
        // Changes column name for cost to billingAmount
        aggregateFunction = viewParametersHelper.getModifiedAggregations(aggregateFunction);
        sort = viewParametersHelper.getModifiedSort(sort);
      }
      cloudProviderTableName = getUpdatedCloudProviderTableName(filters, modifiedGroupBy, aggregateFunction,
          queryParams.getAccountId(), cloudProviderTableName, queryParams.isClusterQuery(), isPodQuery);
    }

    // This indicates that the query is to calculate shared cost
    if (sharedCostBusinessMapping != null) {
      viewRuleList = viewParametersHelper.removeSharedCostRules(viewRuleList, sharedCostBusinessMapping);
    }

    if (queryParams.isTotalCountQuery()) {
      return viewsQueryBuilder.getTotalCountQuery(
          viewRuleList, idFilters, timeFilters, modifiedGroupBy, cloudProviderTableName);
    }
    return viewsQueryBuilder.getQuery(viewRuleList, idFilters, timeFilters,
        viewParametersHelper.getInExpressionFilters(filters), modifiedGroupBy, aggregateFunction, sort,
        cloudProviderTableName, queryParams.getTimeOffsetInDays(), sharedCostBusinessMapping,
        sharedCostBusinessMappings);
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Cloud provider table name related methods
  // ----------------------------------------------------------------------------------------------------------------
  public String getUpdatedCloudProviderTableName(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, String accountId, String cloudProviderTableName,
      boolean isClusterQuery) {
    return getUpdatedCloudProviderTableName(
        filters, groupBy, aggregateFunction, accountId, cloudProviderTableName, isClusterQuery, false);
  }

  public String getUpdatedCloudProviderTableName(List<QLCEViewFilterWrapper> filters, List<QLCEViewGroupBy> groupBy,
      List<QLCEViewAggregation> aggregateFunction, String accountId, String cloudProviderTableName,
      boolean isClusterQuery, boolean isPodQuery) {
    if (!viewParametersHelper.isClusterPerspective(filters, groupBy) && !isClusterQuery) {
      return cloudProviderTableName;
    }
    String[] tableNameSplit = cloudProviderTableName.split("\\.");
    String tableName;
    List<QLCEViewFieldInput> entityGroupBy = new ArrayList<>();
    if (groupBy != null) {
      entityGroupBy = groupBy.stream()
                          .filter(entry -> entry.getEntityGroupBy() != null)
                          .map(QLCEViewGroupBy::getEntityGroupBy)
                          .collect(Collectors.toList());
    }
    if (aggregateFunction == null) {
      aggregateFunction = new ArrayList<>();
    }

    if (!isPodQuery && viewParametersHelper.areAggregationsValidForPreAggregation(aggregateFunction)
        && viewParametersHelper.isValidGroupByForPreAggregation(entityGroupBy)) {
      tableName = viewParametersHelper.isGroupByHour(groupBy)
              || viewParametersHelper.shouldUseHourlyData(viewsQueryHelper.getTimeFilters(filters))
          ? CLUSTER_TABLE_HOURLY_AGGREGRATED
          : CLUSTER_TABLE_AGGREGRATED;
    } else {
      tableName = viewParametersHelper.isGroupByHour(groupBy)
              || viewParametersHelper.shouldUseHourlyData(viewsQueryHelper.getTimeFilters(filters))
          ? CLUSTER_TABLE_HOURLY
          : CLUSTER_TABLE;
    }

    if (isClickHouseEnabled) {
      return format("%s.%s", "ccm", tableName);
    } else {
      return format("%s.%s.%s", tableNameSplit[0], tableNameSplit[1], tableName);
    }
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Miscellaneous methods
  // ----------------------------------------------------------------------------------------------------------------
  public boolean shouldShowUnallocatedCost(final List<QLCEViewGroupBy> groupByList) {
    boolean shouldShowUnallocatedCost = false;
    for (final String unallocatedCostClusterField : UNALLOCATED_COST_CLUSTER_FIELDS) {
      shouldShowUnallocatedCost = viewsQueryHelper.isGroupByFieldIdPresent(groupByList, unallocatedCostClusterField);
      if (shouldShowUnallocatedCost) {
        break;
      }
    }
    return shouldShowUnallocatedCost;
  }

  public List<QLCEViewEntityStatsDataPoint> getUpdatedDataPoints(
      List<QLCEViewEntityStatsDataPoint> entityStatsDataPoints, List<String> entityIds, String harnessAccountId,
      String fieldName) {
    Map<String, String> entityIdToName =
        entityMetadataService.getEntityIdToNameMapping(entityIds, harnessAccountId, fieldName);
    List<QLCEViewEntityStatsDataPoint> updatedDataPoints = new ArrayList<>();
    entityStatsDataPoints.forEach(dataPoint -> {
      final QLCEViewEntityStatsDataPointBuilder qlceViewEntityStatsDataPointBuilder =
          QLCEViewEntityStatsDataPoint.builder();
      qlceViewEntityStatsDataPointBuilder.id(dataPoint.getId())
          .name(entityIdToName.getOrDefault(dataPoint.getName(), dataPoint.getName()))
          .cost(dataPoint.getCost())
          .costTrend(dataPoint.getCostTrend());
      if (AWS_ACCOUNT_FIELD.equals(fieldName)) {
        qlceViewEntityStatsDataPointBuilder.name(AwsAccountFieldHelper.mergeAwsAccountIdAndName(
            dataPoint.getName(), entityIdToName.get(dataPoint.getName())));
      }
      updatedDataPoints.add(qlceViewEntityStatsDataPointBuilder.build());
    });
    return updatedDataPoints;
  }
}
