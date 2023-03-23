/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.graphql;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.BUSINESS_MAPPING;
import static io.harness.ccm.views.entities.ViewFieldIdentifier.COMMON;
import static io.harness.ccm.views.graphql.QLCEViewAggregateOperation.SUM;
import static io.harness.ccm.views.graphql.QLCEViewTimeGroupType.DAY;
import static io.harness.ccm.views.graphql.ViewsMetaDataFields.LABEL_KEY;
import static io.harness.ccm.views.graphql.ViewsMetaDataFields.LABEL_KEY_UN_NESTED;
import static io.harness.ccm.views.graphql.ViewsMetaDataFields.LABEL_VALUE_UN_NESTED;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLOUD_SERVICE_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE;
import static io.harness.ccm.views.utils.ClusterTableKeys.COUNT_INNER;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_CPU_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_CPU_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_CPU_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_MEMORY_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_MEMORY_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.EFFECTIVE_MEMORY_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_APPLICATION;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_LAUNCH_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_SERVICE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ECS_TASK;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_ENVIRONMENT;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_INSTANCE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_NODE;
import static io.harness.ccm.views.utils.ClusterTableKeys.GROUP_BY_SERVICE;
import static io.harness.ccm.views.utils.ClusterTableKeys.INSTANCE_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.LAUNCH_TYPE;
import static io.harness.ccm.views.utils.ClusterTableKeys.TASK_ID;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_CPU_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_CPU_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_CPU_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_MEMORY_LIMIT;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_MEMORY_REQUEST;
import static io.harness.ccm.views.utils.ClusterTableKeys.TIME_AGGREGATED_MEMORY_UTILIZATION_VALUE;
import static io.harness.ccm.views.utils.ClusterTableKeys.WORKLOAD_NAME;
import static io.harness.timescaledb.Tables.ANOMALIES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.businessMapping.entities.SharedCost;
import io.harness.ccm.views.businessMapping.entities.SharedCostSplit;
import io.harness.ccm.views.businessMapping.entities.UnallocatedCostStrategy;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dao.ViewCustomFieldDao;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewCustomField;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewQueryParams;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.utils.ClickHouseConstants;
import io.harness.ccm.views.utils.ClusterTableKeys;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.exception.InvalidRequestException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CaseStatement;
import com.healthmarketscience.sqlbuilder.ComboCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.Converter;
import com.healthmarketscience.sqlbuilder.CustomCondition;
import com.healthmarketscience.sqlbuilder.CustomExpression;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UnaryCondition;
import com.healthmarketscience.sqlbuilder.UnionQuery;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgLimitClause;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgOffsetClause;
import io.fabric8.utils.Lists;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(CE)
public class ViewsQueryBuilder {
  @Inject private ViewCustomFieldDao viewCustomFieldDao;
  @Inject private BusinessMappingService businessMappingService;
  @Inject @Named("isClickHouseEnabled") private boolean isClickHouseEnabled;

  public static final String K8S_NODE = "K8S_NODE";
  public static final String K8S_POD = "K8S_POD";
  public static final String K8S_POD_FARGATE = "K8S_POD_FARGATE";
  public static final String K8S_PV = "K8S_PV";
  public static final String ECS_TASK_FARGATE = "ECS_TASK_FARGATE";
  public static final String ECS_TASK_EC2 = "ECS_TASK_EC2";
  public static final String ECS_CONTAINER_INSTANCE = "ECS_CONTAINER_INSTANCE";
  public static final String UNNESTED_LABEL_KEY_COLUMN = "labelsUnnested.key";
  public static final String UNNESTED_LABEL_VALUE_COLUMN = "labelsUnnested.value";
  public static final String CLICKHOUSE_LABEL_VALUE_COLUMN = "labels['%s']";
  public static final String LABEL_KEY_ALIAS = "labels_key";
  public static final String LABEL_VALUE_ALIAS = "labels_value";
  public static final String aliasStartTimeMaxMin = "%s_%s";
  private static final String DISTINCT = " DISTINCT(%s)";
  private static final String LOWER = "LOWER(%s)";
  private static final String COALESCE = " COALESCE(%s, %s)";
  private static final String SHARED_COST_SPLIT_COLUMN = "(%s * (%s / %s))";
  private static final String COUNT = "COUNT(*)";
  private static final String searchFilter = "REGEXP_CONTAINS( LOWER(%s), LOWER('%s') )";
  private static final String searchFilterClickHouse = "%s LIKE %s";
  private static final String regexFilter = "REGEXP_CONTAINS( %s, r'%s' )";
  private static final String labelsSubQuery = "(SELECT value FROM UNNEST(labels) WHERE KEY='%s')";
  private static final String leftJoinLabels = " LEFT JOIN UNNEST(labels) as labelsUnnested";
  private static final String leftJoinSelectiveLabels =
      " LEFT JOIN UNNEST(labels) as labelsUnnested ON labelsUnnested.key IN (%s)";
  private static final ImmutableSet<String> podInfoImmutableSet = ImmutableSet.of("namespace", "workloadName", "appId",
      "envId", "serviceId", "parentInstanceId", "cloudServiceName", "taskId", "launchType");
  private static final ImmutableSet<String> clusterFilterImmutableSet =
      ImmutableSet.of("product", "region", "PROVIDERS");
  private static final ImmutableList<String> applicationGroupBys =
      ImmutableList.of(GROUP_BY_APPLICATION, GROUP_BY_SERVICE, GROUP_BY_ENVIRONMENT);
  private static final String CLOUD_PROVIDERS_CUSTOM_GROUPING = "PROVIDERS";
  private static final String CLOUD_PROVIDERS_CUSTOM_GROUPING_QUERY =
      "CASE WHEN cloudProvider = 'CLUSTER' THEN 'CLUSTER' ELSE 'CLOUD' END";
  private static final String MULTI_IF_STATEMENT_OPENING = "multiIf(";

  public SelectQuery getQuery(List<ViewRule> rules, List<QLCEViewFilter> filters, List<QLCEViewTimeFilter> timeFilters,
      List<QLCEViewGroupBy> groupByList, List<QLCEViewAggregation> aggregations,
      List<QLCEViewSortCriteria> sortCriteriaList, String cloudProviderTableName, ViewQueryParams queryParams,
      List<BusinessMapping> sharedCostBusinessMappings) {
    return getQuery(rules, filters, timeFilters, Collections.emptyList(), groupByList, Collections.emptyList(),
        aggregations, sortCriteriaList, cloudProviderTableName, queryParams, null, sharedCostBusinessMappings);
  }

  /**
   * Gets the select query
   * @param rules perspective rules
   * @param filters filters applied
   * @param timeFilters time filters applied
   * @param inExpressionFilters to fetch top entries, used only in timeSeriesStats
   * @param groupByList groupBys applied
   * @param sharedCostGroupBy shared bucket business mapping groupBy, used only for decoration
   * @param aggregations aggregations applied
   * @param sortCriteriaList sort criteria applied
   * @param cloudProviderTableName cloud provider table name
   * @param queryParams query parameters
   * @param sharedCostBusinessMapping used to apply shared bucket rules in the union query
   * @param sharedCostBusinessMappings negating shared cost buckets for the base query to handle duplicate cost
   * @return SelectQuery
   */
  public SelectQuery getQuery(List<ViewRule> rules, List<QLCEViewFilter> filters, List<QLCEViewTimeFilter> timeFilters,
      List<QLCEInExpressionFilter> inExpressionFilters, List<QLCEViewGroupBy> groupByList,
      List<QLCEViewGroupBy> sharedCostGroupBy, List<QLCEViewAggregation> aggregations,
      List<QLCEViewSortCriteria> sortCriteriaList, String cloudProviderTableName, ViewQueryParams queryParams,
      BusinessMapping sharedCostBusinessMapping, List<BusinessMapping> sharedCostBusinessMappings) {
    SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(cloudProviderTableName);
    List<QLCEViewFieldInput> groupByEntity = getGroupByEntity(groupByList);
    List<QLCEViewFieldInput> sharedCostGroupByEntity = getGroupByEntity(sharedCostGroupBy);
    QLCEViewTimeTruncGroupBy groupByTime = getGroupByTime(groupByList);
    boolean isClusterTable = isClusterTable(cloudProviderTableName);
    String tableIdentifier = getTableIdentifier(cloudProviderTableName);

    List<ViewField> customFields =
        collectFieldListByIdentifier(rules, filters, groupByEntity, ViewFieldIdentifier.CUSTOM);

    List<ViewField> businessMapping = collectFieldListByIdentifier(rules, filters, groupByEntity, BUSINESS_MAPPING);

    if ((!isApplicationQuery(groupByList) || !isClusterTable) && !isInstanceQuery(groupByList)) {
      modifyQueryWithInstanceTypeFilter(rules, filters, groupByEntity, customFields, businessMapping, selectQuery);
    }

    // This indicates that the query is to calculate shared cost
    if (sharedCostBusinessMapping != null) {
      rules = removeSharedCostRules(rules, sharedCostBusinessMapping);
    }

    if (!rules.isEmpty()) {
      selectQuery.addCondition(getConsolidatedRuleCondition(rules, tableIdentifier));
    }

    if (!filters.isEmpty()) {
      decorateQueryWithFilters(selectQuery, filters, tableIdentifier);
    }

    if (!inExpressionFilters.isEmpty()) {
      decorateQueryWithInExpressionFilters(selectQuery, inExpressionFilters, groupByEntity, tableIdentifier);
    }

    if (!Lists.isNullOrEmpty(sharedCostBusinessMappings)) {
      decorateQueryWithNegateSharedCosts(selectQuery, sharedCostBusinessMappings, tableIdentifier);
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(selectQuery, timeFilters, isClusterTable, tableIdentifier);
    }

    if (!queryParams.isSkipGroupBy()) {
      if (!Lists.isNullOrEmpty(sharedCostGroupByEntity)) {
        decorateQueryWithGroupByAndColumns(selectQuery, sharedCostGroupByEntity, tableIdentifier);
      } else {
        decorateQueryWithGroupByAndColumns(selectQuery, groupByEntity, tableIdentifier);
      }
    }

    if (groupByTime != null) {
      if (queryParams.getTimeOffsetInDays() == 0) {
        decorateQueryWithGroupByTime(selectQuery, groupByTime, isClusterTable, tableIdentifier, false);
      } else {
        decorateQueryWithGroupByTimeWithOffset(
            selectQuery, groupByTime, isClusterTable, queryParams.getTimeOffsetInDays(), tableIdentifier);
      }
    }

    if (!aggregations.isEmpty()) {
      decorateQueryWithAggregations(selectQuery, aggregations, tableIdentifier, false);
    }

    decorateQueryWithSharedCostAggregations(
        selectQuery, sharedCostGroupByEntity, isClusterTable, tableIdentifier, sharedCostBusinessMapping);

    if (!sortCriteriaList.isEmpty()) {
      decorateQueryWithSortCriteria(selectQuery, sortCriteriaList);
    }

    log.info("Query for view {}", selectQuery);
    return selectQuery;
  }

  private List<ViewRule> removeSharedCostRules(List<ViewRule> viewRules, BusinessMapping sharedCostBusinessMapping) {
    if (sharedCostBusinessMapping != null) {
      List<ViewRule> updatedViewRules = new ArrayList<>();
      viewRules.forEach(rule -> {
        List<ViewCondition> updatedViewConditions =
            removeSharedCostRulesFromViewConditions(rule.getViewConditions(), sharedCostBusinessMapping);
        if (!updatedViewConditions.isEmpty()) {
          updatedViewRules.add(ViewRule.builder().viewConditions(updatedViewConditions).build());
        }
      });
      return updatedViewRules;
    }
    return viewRules;
  }

  private List<ViewCondition> removeSharedCostRulesFromViewConditions(
      List<ViewCondition> viewConditions, BusinessMapping sharedCostBusinessMapping) {
    List<ViewCondition> updatedViewConditions = new ArrayList<>();
    for (ViewCondition condition : viewConditions) {
      if (!((ViewIdCondition) condition).getViewField().getFieldId().equals(sharedCostBusinessMapping.getUuid())) {
        updatedViewConditions.add(condition);
      }
    }
    return updatedViewConditions;
  }

  private void decorateQueryWithNegateSharedCosts(final SelectQuery selectQuery,
      final List<BusinessMapping> sharedCostBusinessMappings, final String tableIdentifier) {
    for (final BusinessMapping businessMapping : sharedCostBusinessMappings) {
      final CustomSql conditionKey = isClickHouseQuery()
          ? getClickHouseSQLCaseStatementForSharedCost(businessMapping, tableIdentifier)
          : getSQLCaseStatementForSharedCost(businessMapping, tableIdentifier);
      final Condition condition =
          new InCondition(conditionKey, (Object[]) getSharedBucketNames(businessMapping)).setNegate(true);
      selectQuery.addCondition(condition);
    }
  }

  private String[] getSharedBucketNames(final BusinessMapping businessMapping) {
    List<String> sharedBucketNames = new ArrayList<>();
    if (Objects.nonNull(businessMapping.getSharedCosts())) {
      sharedBucketNames =
          businessMapping.getSharedCosts().stream().map(SharedCost::getName).collect(Collectors.toList());
    }
    return sharedBucketNames.toArray(new String[0]);
  }

  private void decorateQueryWithGroupByAndColumns(
      SelectQuery selectQuery, List<QLCEViewFieldInput> groupByEntity, String tableIdentifier) {
    if (!groupByEntity.isEmpty()) {
      for (QLCEViewFieldInput groupBy : groupByEntity) {
        CustomSql sqlObjectFromField = getSQLObjectFromField(groupBy, tableIdentifier);
        if (groupBy.getIdentifier() != ViewFieldIdentifier.CUSTOM && groupBy.getIdentifier() != BUSINESS_MAPPING
            && groupBy.getIdentifier() != ViewFieldIdentifier.LABEL) {
          selectQuery.addCustomColumns(sqlObjectFromField);
          selectQuery.addCustomGroupings(sqlObjectFromField);
        } else if (groupBy.getIdentifier() == ViewFieldIdentifier.LABEL) {
          if (!isClickHouseQuery()) {
            String labelSubQuery = String.format(labelsSubQuery, groupBy.getFieldName());
            selectQuery.addCustomGroupings(ViewsMetaDataFields.LABEL_VALUE.getAlias());
            selectQuery.addCustomColumns(
                Converter.toCustomColumnSqlObject(labelSubQuery, ViewsMetaDataFields.LABEL_VALUE.getAlias()));
          } else {
            String labelColumn = String.format(CLICKHOUSE_LABEL_VALUE_COLUMN, groupBy.getFieldName());
            selectQuery.addCustomGroupings(ViewsMetaDataFields.LABEL_VALUE.getAlias());
            selectQuery.addCustomColumns(
                Converter.toCustomColumnSqlObject(labelColumn, ViewsMetaDataFields.LABEL_VALUE.getAlias()));
          }
        } else {
          // Will handle both Custom and Business Mapping Cases
          selectQuery.addAliasedColumn(
              sqlObjectFromField, modifyStringToComplyRegex(getColumnName(groupBy.getFieldName())));
          selectQuery.addCustomGroupings(modifyStringToComplyRegex(groupBy.getFieldName()));
        }
      }
    }
  }

  private void decorateQueryWithGroupByColumns(
      final SelectQuery selectQuery, final List<QLCEViewFieldInput> groupByEntity, final String tableIdentifier) {
    if (!groupByEntity.isEmpty()) {
      for (final QLCEViewFieldInput groupBy : groupByEntity) {
        final CustomSql sqlObjectFromField = getSQLObjectFromField(groupBy, tableIdentifier);
        if (groupBy.getIdentifier() != ViewFieldIdentifier.CUSTOM && groupBy.getIdentifier() != BUSINESS_MAPPING
            && groupBy.getIdentifier() != ViewFieldIdentifier.LABEL) {
          selectQuery.addCustomColumns(sqlObjectFromField);
        } else if (groupBy.getIdentifier() == ViewFieldIdentifier.LABEL) {
          selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(ViewsMetaDataFields.LABEL_VALUE.getAlias()));
        } else {
          // Will handle both Custom and Business Mapping Cases
          selectQuery.addCustomColumns(
              Converter.toCustomColumnSqlObject(modifyStringToComplyRegex(getColumnName(groupBy.getFieldName()))));
        }
      }
    }
  }

  private void decorateQueryWithGroupBy(
      final SelectQuery selectQuery, final List<QLCEViewFieldInput> groupByEntity, final String tableIdentifier) {
    if (!groupByEntity.isEmpty()) {
      for (final QLCEViewFieldInput groupBy : groupByEntity) {
        final CustomSql sqlObjectFromField = getSQLObjectFromField(groupBy, tableIdentifier);
        if (groupBy.getIdentifier() != ViewFieldIdentifier.CUSTOM && groupBy.getIdentifier() != BUSINESS_MAPPING
            && groupBy.getIdentifier() != ViewFieldIdentifier.LABEL) {
          selectQuery.addCustomGroupings(sqlObjectFromField);
        } else if (groupBy.getIdentifier() == ViewFieldIdentifier.LABEL) {
          selectQuery.addCustomGroupings(Converter.toCustomColumnSqlObject(ViewsMetaDataFields.LABEL_VALUE.getAlias()));
        } else {
          // Will handle both Custom and Business Mapping Cases
          selectQuery.addCustomGroupings(
              Converter.toCustomColumnSqlObject(modifyStringToComplyRegex(getColumnName(groupBy.getFieldName()))));
        }
      }
    }
  }

  public SelectQuery getTotalCountSharedCostOuterQuery(
      final List<QLCEViewGroupBy> groupBy, final UnionQuery unionQuery, final String cloudProviderTableName) {
    final SelectQuery outerQuery = new SelectQuery();
    final SelectQuery query = new SelectQuery();
    final String tableIdentifier = getTableIdentifier(cloudProviderTableName);
    if (!Lists.isNullOrEmpty(groupBy)) {
      decorateQueryWithGroupBy(query, getGroupByEntity(groupBy), tableIdentifier);
    }
    query.addCustomFromTable(String.format("(%s)", unionQuery));
    query.addCustomColumns(Converter.toCustomColumnSqlObject(COUNT, COUNT_INNER));

    outerQuery.addCustomFromTable(String.format("(%s)", query));
    outerQuery.addCustomColumns(Converter.toCustomColumnSqlObject(COUNT, ClusterTableKeys.COUNT));
    log.info("Total count query for shared cost data: {}", outerQuery);
    return outerQuery;
  }

  private void decorateSharedCostQueryGroupBy(final List<QLCEViewGroupBy> groupBy, final boolean isClusterPerspective,
      final SelectQuery query, final String tableIdentifier) {
    if (!Lists.isNullOrEmpty(groupBy)) {
      // Handling label groupBy separately
      final List<QLCEViewFieldInput> groupByEntity = getGroupByEntity(groupBy);
      final List<QLCEViewFieldInput> groupByLabel = getLabelGroupBy(groupByEntity);
      final QLCEViewTimeTruncGroupBy groupByTime = getGroupByTime(groupBy);
      if (!groupByLabel.isEmpty()) {
        decorateQueryWithGroupByColumns(query, groupByLabel, tableIdentifier);
        query.addCustomGroupings(ViewsMetaDataFields.LABEL_VALUE.getAlias());
      } else {
        decorateQueryWithGroupByAndColumns(query, groupByEntity, tableIdentifier);
      }
      if (Objects.nonNull(groupByTime)) {
        decorateQueryWithGroupByTime(query, groupByTime, isClusterPerspective, tableIdentifier, true);
      }
    }
  }

  public SelectQuery getSharedCostOuterQuery(final List<QLCEViewGroupBy> groupBy,
      final List<QLCEViewAggregation> aggregateFunction, final List<QLCEViewSortCriteria> sort,
      final UnionQuery unionQuery, final String cloudProviderTableName, final boolean isClusterPerspective) {
    final SelectQuery query = new SelectQuery();
    final String tableIdentifier = getTableIdentifier(cloudProviderTableName);
    decorateSharedCostQueryGroupBy(groupBy, isClusterPerspective, query, tableIdentifier);
    if (!Lists.isNullOrEmpty(aggregateFunction)) {
      decorateQueryWithAggregations(query, aggregateFunction, tableIdentifier, true);
    }
    if (!Lists.isNullOrEmpty(sort)) {
      decorateQueryWithSortCriteria(query, sort);
    }
    query.addCustomFromTable(String.format("(%s)", unionQuery));
    return query;
  }

  @NotNull
  public SelectQuery getSharedCostQuery(final List<QLCEViewGroupBy> groupBy,
      final List<QLCEViewAggregation> aggregateFunction, final Map<String, Double> entityCosts, final double totalCost,
      final CostTarget costTarget, final SharedCost sharedCost, final BusinessMapping businessMapping,
      final String cloudProviderTableName, final boolean isClusterPerspective) {
    final SelectQuery selectQuery = new SelectQuery();
    final String tableIdentifier = getTableIdentifier(cloudProviderTableName);
    decorateSharedCostQueryGroupBy(groupBy, isClusterPerspective, selectQuery, tableIdentifier);
    switch (sharedCost.getStrategy()) {
      case PROPORTIONAL:
        decorateSharedCostQueryWithAggregations(selectQuery, aggregateFunction, tableIdentifier,
            entityCosts.getOrDefault(costTarget.getName(), 0.0D), totalCost);
        break;
      case EQUAL:
        decorateSharedCostQueryWithAggregations(
            selectQuery, aggregateFunction, tableIdentifier, 1, businessMapping.getCostTargets().size());
        break;
      case FIXED:
        for (final SharedCostSplit sharedCostSplit : sharedCost.getSplits()) {
          if (costTarget.getName().equals(sharedCostSplit.getCostTargetName())) {
            decorateSharedCostQueryWithAggregations(
                selectQuery, aggregateFunction, tableIdentifier, sharedCostSplit.getPercentageContribution(), 100.0D);
            break;
          }
        }
        break;
      default:
        log.error("Invalid shared cost strategy for business mapping: {}", businessMapping);
        break;
    }
    return selectQuery;
  }

  public SelectQuery getWorkloadAndCloudServiceNamesForLabels(
      final List<QLCEViewFilter> filters, final List<QLCEViewTimeFilter> timeFilters, final String table) {
    final SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(table);
    String tableIdentifier = "clusterData";

    if (!filters.isEmpty()) {
      decorateQueryWithFilters(selectQuery, filters, tableIdentifier);
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(selectQuery, timeFilters, true, tableIdentifier);
    }

    selectQuery.addAliasedColumn(
        new CustomSql(String.format(DISTINCT, String.format(COALESCE, "workloadName", "cloudServiceName"))),
        "resourceName");
    selectQuery.addCustomColumns(new CustomSql("instanceType"));

    log.info("Query for labels recommendation: {}", selectQuery);
    return selectQuery;
  }

  // Query to get columns of a bq table
  public SelectQuery getInformationSchemaQueryForColumns(String informationSchemaView, String table) {
    SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(informationSchemaView);

    // Adding group by column
    selectQuery.addCustomColumns(new CustomSql("column_name"));
    selectQuery.addCustomGroupings(new CustomSql("column_name"));

    // Adding table name filter
    // Note that condition is 'like'
    selectQuery.addCondition(BinaryCondition.like(new CustomSql("table_name"), table + "%"));

    log.info("Information schema query for table {}", selectQuery);
    return selectQuery;
  }

  public SelectQuery getCostByProvidersOverviewQuery(List<QLCEViewTimeFilter> timeFilters,
      List<QLCEViewGroupBy> groupByList, List<QLCEViewAggregation> aggregations, String cloudProviderTableName) {
    String tableIdentifier = getTableIdentifier(cloudProviderTableName);
    SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(cloudProviderTableName);
    QLCEViewTimeTruncGroupBy groupByTime = getGroupByTime(groupByList);
    List<QLCEViewFieldInput> groupByEntity = Collections.singletonList(QLCEViewFieldInput.builder()
                                                                           .fieldId(CLOUD_PROVIDERS_CUSTOM_GROUPING)
                                                                           .fieldName(CLOUD_PROVIDERS_CUSTOM_GROUPING)
                                                                           .identifier(ViewFieldIdentifier.COMMON)
                                                                           .build());

    selectQuery.addAliasedColumn(new CustomSql(CLOUD_PROVIDERS_CUSTOM_GROUPING_QUERY), CLOUD_PROVIDERS_CUSTOM_GROUPING);
    selectQuery.addCustomGroupings(CLOUD_PROVIDERS_CUSTOM_GROUPING);

    // Adding instance type filters
    modifyQueryWithInstanceTypeFilter(Collections.emptyList(), Collections.emptyList(), groupByEntity,
        Collections.emptyList(), Collections.emptyList(), selectQuery);

    if (!aggregations.isEmpty()) {
      decorateQueryWithAggregations(selectQuery, aggregations, tableIdentifier, false);
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(selectQuery, timeFilters, false, tableIdentifier);
    }

    if (groupByTime != null) {
      decorateQueryWithGroupByTime(selectQuery, groupByTime, false, tableIdentifier, false);
    }

    log.info("Query for Overview cost by providers {}", selectQuery);
    return selectQuery;
  }

  public SelectQuery getTotalCountQuery(List<ViewRule> rules, List<QLCEViewFilter> filters,
      List<QLCEViewTimeFilter> timeFilters, List<QLCEViewGroupBy> groupByList, String cloudProviderTableName) {
    String tableIdentifier = getTableIdentifier(cloudProviderTableName);
    SelectQuery selectQueryInner = new SelectQuery();
    SelectQuery selectQueryOuter = new SelectQuery();
    selectQueryInner.addCustomFromTable(cloudProviderTableName);
    List<QLCEViewFieldInput> groupByEntity = getGroupByEntity(groupByList);
    QLCEViewTimeTruncGroupBy groupByTime = getGroupByTime(groupByList);
    boolean isClusterTable = isClusterTable(cloudProviderTableName);

    selectQueryInner.addCustomColumns(Converter.toCustomColumnSqlObject(COUNT, COUNT_INNER));

    List<ViewField> customFields =
        collectFieldListByIdentifier(rules, filters, groupByEntity, ViewFieldIdentifier.CUSTOM);
    List<ViewField> businessMapping = collectFieldListByIdentifier(rules, filters, groupByEntity, BUSINESS_MAPPING);
    if ((!isApplicationQuery(groupByList) || !isClusterTable) && !isInstanceQuery(groupByList)) {
      modifyQueryWithInstanceTypeFilter(rules, filters, groupByEntity, customFields, businessMapping, selectQueryInner);
    }

    if (!rules.isEmpty()) {
      selectQueryInner.addCondition(getConsolidatedRuleCondition(rules, tableIdentifier));
    }

    if (!filters.isEmpty()) {
      decorateQueryWithFilters(selectQueryInner, filters, tableIdentifier);
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(selectQueryInner, timeFilters, isClusterTable, tableIdentifier);
    }

    decorateQueryWithGroupByAndColumns(selectQueryInner, groupByEntity, tableIdentifier);

    if (groupByTime != null) {
      decorateQueryWithGroupByTime(selectQueryInner, groupByTime, isClusterTable, tableIdentifier, false);
    }

    selectQueryOuter.addCustomFromTable(String.format("(%s)", selectQueryInner));
    selectQueryOuter.addCustomColumns(Converter.toCustomColumnSqlObject(COUNT, ClusterTableKeys.COUNT));

    log.info("Total count query for view {}", selectQueryOuter);
    return selectQueryOuter;
  }

  public String getAnomalyQuery(
      List<ViewRule> rules, List<QLCEViewFilter> filters, List<QLCEViewTimeFilter> timeFilters) {
    String tableIdentifier = "unifiedTable";
    SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(ANOMALIES.getName());
    selectQuery.addAllColumns();

    if (!rules.isEmpty()) {
      selectQuery.addCondition(getConsolidatedRuleCondition(rules, tableIdentifier));
    }

    if (!filters.isEmpty()) {
      decorateQueryWithFilters(selectQuery, filters, tableIdentifier);
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(selectQuery, timeFilters, false, tableIdentifier);
    }

    return selectQuery.toString();
  }

  public SelectQuery getLabelsForWorkloadsQuery(
      String cloudProviderTableName, List<QLCEViewFilter> filters, List<QLCEViewTimeFilter> timeFilters) {
    String tableIdentifier = getTableIdentifier(cloudProviderTableName);
    SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(cloudProviderTableName);

    selectQuery.addAliasedColumn(new CustomSql(UNNESTED_LABEL_KEY_COLUMN), LABEL_KEY_ALIAS);
    selectQuery.addAliasedColumn(new CustomSql(UNNESTED_LABEL_VALUE_COLUMN), LABEL_VALUE_ALIAS);
    selectQuery.addAliasedColumn(new CustomSql(WORKLOAD_NAME), WORKLOAD_NAME);

    selectQuery.addCustomJoin(leftJoinLabels);

    selectQuery.addCustomGroupings(LABEL_KEY_ALIAS);
    selectQuery.addCustomGroupings(LABEL_VALUE_ALIAS);
    selectQuery.addCustomGroupings(WORKLOAD_NAME);

    selectQuery.addCondition(new CustomCondition(getSearchCondition(UNNESTED_LABEL_KEY_COLUMN, "")));

    if (!filters.isEmpty()) {
      decorateQueryWithFilters(selectQuery, filters, tableIdentifier);
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(selectQuery, timeFilters, true, tableIdentifier);
    }

    return selectQuery;
  }

  private List<ViewField> collectFieldListByIdentifier(List<ViewRule> rules, List<QLCEViewFilter> filters,
      List<QLCEViewFieldInput> groupByEntity, ViewFieldIdentifier identifier) {
    List<ViewField> customFieldLists = new ArrayList<>();
    for (ViewRule rule : rules) {
      for (ViewCondition condition : rule.getViewConditions()) {
        ViewIdCondition viewIdCondition = (ViewIdCondition) condition;
        ViewFieldIdentifier viewFieldIdentifier = viewIdCondition.getViewField().getIdentifier();
        if (viewFieldIdentifier.equals(identifier)) {
          customFieldLists.add(((ViewIdCondition) condition).getViewField());
        }
      }
    }

    for (QLCEViewFilter filter : filters) {
      if (filter.getField().getIdentifier().equals(identifier)) {
        customFieldLists.add(getViewField(filter.getField()));
      }
    }

    for (QLCEViewFieldInput groupByField : groupByEntity) {
      if (groupByField.getIdentifier().equals(identifier)) {
        customFieldLists.add(getViewField(groupByField));
      }
    }

    return customFieldLists;
  }

  public ViewField getViewField(QLCEViewFieldInput field) {
    return ViewField.builder()
        .fieldId(field.getFieldId())
        .fieldName(field.getFieldName())
        .identifier(field.getIdentifier())
        .identifierName(field.getIdentifier().getDisplayName())
        .build();
  }

  private void modifyQueryWithInstanceTypeFilter(List<ViewRule> incomingRules, List<QLCEViewFilter> filters,
      List<QLCEViewFieldInput> groupByEntity, List<ViewField> customFields, List<ViewField> businessMappings,
      SelectQuery selectQuery) {
    boolean isClusterConditionOrFilterPresent = false;
    boolean isPodFilterPresent = false;
    boolean isLabelsOperationPresent = false;
    if (incomingRules.isEmpty() && filters.isEmpty() && groupByEntity.isEmpty() && customFields.isEmpty()) {
      isClusterConditionOrFilterPresent = true;
    }

    List<ViewRule> rules = new ArrayList<>(incomingRules);

    for (ViewField field : businessMappings) {
      BusinessMapping businessMapping = businessMappingService.get(field.getFieldId());
      List<CostTarget> costTargets =
          businessMapping.getCostTargets() != null ? businessMapping.getCostTargets() : Collections.emptyList();
      List<SharedCost> sharedCosts =
          businessMapping.getSharedCosts() != null ? businessMapping.getSharedCosts() : Collections.emptyList();
      for (CostTarget costTarget : costTargets) {
        rules.addAll(costTarget.getRules());
      }
      for (SharedCost sharedCost : sharedCosts) {
        rules.addAll(sharedCost.getRules());
      }
    }

    for (ViewRule rule : rules) {
      for (ViewCondition condition : rule.getViewConditions()) {
        ViewIdCondition viewIdCondition = (ViewIdCondition) condition;
        ViewFieldIdentifier viewFieldIdentifier = viewIdCondition.getViewField().getIdentifier();
        if (viewFieldIdentifier.equals(ViewFieldIdentifier.CLUSTER)) {
          isClusterConditionOrFilterPresent = true;
          String fieldId = viewIdCondition.getViewField().getFieldId();
          if (podInfoImmutableSet.contains(fieldId)) {
            isPodFilterPresent = true;
          }
        }

        if (viewFieldIdentifier.equals(ViewFieldIdentifier.COMMON)) {
          String fieldId = viewIdCondition.getViewField().getFieldId();
          if (clusterFilterImmutableSet.contains(fieldId)) {
            isClusterConditionOrFilterPresent = true;
          }
        }

        if (viewFieldIdentifier.equals(ViewFieldIdentifier.LABEL)) {
          isLabelsOperationPresent = true;
        }
      }
    }

    for (QLCEViewFilter filter : filters) {
      ViewFieldIdentifier viewFieldIdentifier = filter.getField().getIdentifier();
      if (viewFieldIdentifier.equals(ViewFieldIdentifier.CLUSTER)) {
        isClusterConditionOrFilterPresent = true;
        String fieldId = filter.getField().getFieldId();
        if (podInfoImmutableSet.contains(fieldId)) {
          isPodFilterPresent = true;
        }
      }

      if (viewFieldIdentifier.equals(ViewFieldIdentifier.LABEL)) {
        isLabelsOperationPresent = true;
      }
    }

    for (QLCEViewFieldInput groupBy : groupByEntity) {
      if (groupBy.getIdentifier().equals(ViewFieldIdentifier.CLUSTER)) {
        isClusterConditionOrFilterPresent = true;
        if (podInfoImmutableSet.contains(groupBy.getFieldId())) {
          isPodFilterPresent = true;
        }
      }

      if (groupBy.getIdentifier().equals(ViewFieldIdentifier.COMMON)) {
        String fieldId = groupBy.getFieldId();
        if (clusterFilterImmutableSet.contains(fieldId)) {
          isClusterConditionOrFilterPresent = true;
        }
      }

      if (groupBy.getIdentifier().equals(ViewFieldIdentifier.LABEL)) {
        isLabelsOperationPresent = true;
      }
    }

    for (ViewField field : customFields) {
      ViewCustomField customField = viewCustomFieldDao.getById(field.getFieldId());
      List<ViewField> customFieldViewFields = customField.getViewFields();
      for (ViewField viewField : customFieldViewFields) {
        if (viewField.getIdentifier().equals(ViewFieldIdentifier.CLUSTER)) {
          isClusterConditionOrFilterPresent = true;
          if (podInfoImmutableSet.contains(viewField.getFieldId())) {
            isPodFilterPresent = true;
          }
        }

        if (viewField.getIdentifier().equals(ViewFieldIdentifier.COMMON)) {
          if (clusterFilterImmutableSet.contains(viewField.getFieldId())) {
            isClusterConditionOrFilterPresent = true;
          }
        }

        if (viewField.getIdentifier().equals(ViewFieldIdentifier.LABEL)) {
          isLabelsOperationPresent = true;
        }
      }
    }

    if (isClusterConditionOrFilterPresent) {
      List<String> instancetypeList =
          ImmutableList.of(K8S_NODE, K8S_PV, K8S_POD_FARGATE, ECS_TASK_FARGATE, ECS_CONTAINER_INSTANCE);

      if (isPodFilterPresent || isLabelsOperationPresent) {
        instancetypeList = ImmutableList.of(K8S_POD, K8S_POD_FARGATE, ECS_TASK_FARGATE, ECS_TASK_EC2);
      }

      String[] instancetypeStringArray = instancetypeList.toArray(new String[instancetypeList.size()]);

      List<Condition> conditionList = new ArrayList<>();
      if (isClickHouseQuery()) {
        conditionList.add(BinaryCondition.equalTo(new CustomSql(ViewsMetaDataFields.INSTANCE_TYPE.getFieldName()), ""));
      }
      conditionList.add(UnaryCondition.isNull(new CustomSql(ViewsMetaDataFields.INSTANCE_TYPE.getFieldName())));
      conditionList.add(new InCondition(
          new CustomSql(ViewsMetaDataFields.INSTANCE_TYPE.getFieldName()), (Object[]) instancetypeStringArray));
      selectQuery.addCondition(getSqlOrCondition(conditionList));
    }
  }

  public ViewsQueryMetadata getFilterValuesQuery(List<ViewRule> rules, List<QLCEViewFilter> filters,
      List<QLCEViewTimeFilter> timeFilters, String cloudProviderTableName, Integer limit, Integer offset,
      boolean isLimitRequired, boolean isSharedCostOuterQuery) {
    String tableIdentifier = getTableIdentifier(cloudProviderTableName);
    List<QLCEViewFieldInput> fields = new ArrayList<>();
    SelectQuery query = new SelectQuery();
    if (isLimitRequired) {
      query.addCustomization(new PgLimitClause(limit));
      query.addCustomization(new PgOffsetClause(offset));
    }
    query.addCustomFromTable(cloudProviderTableName);

    boolean isClusterTable = isClusterTable(cloudProviderTableName);

    boolean isLabelsPresent = false;
    List<ViewField> customFields =
        collectFieldListByIdentifier(rules, filters, Collections.emptyList(), ViewFieldIdentifier.CUSTOM);
    List<ViewField> businessMappings =
        collectFieldListByIdentifier(rules, filters, Collections.emptyList(), BUSINESS_MAPPING);
    List<String> labelKeysList = new ArrayList<>();

    if (!customFields.isEmpty()) {
      List<String> labelKeysListInCustomFields = modifyQueryForCustomFieldsFilterValues(query, customFields);
      labelKeysList.addAll(labelKeysListInCustomFields);
      isLabelsPresent = !labelKeysListInCustomFields.isEmpty();
    }

    if (!businessMappings.isEmpty()) {
      List<String> labelKeysListInBusinessMappings =
          modifyQueryForBusinessMappingFilterValues(query, businessMappings, false);
      labelKeysList.addAll(labelKeysListInBusinessMappings);
      isLabelsPresent = !labelKeysListInBusinessMappings.isEmpty();
    }

    labelKeysList.addAll(collectLabelKeysList(rules, filters));
    if ((isLabelsPresent || evaluateLabelsPresent(rules, filters)) && !isSharedCostOuterQuery) {
      decorateQueryWithLabelsMetadata(query, true, labelKeysList, getIsLabelsKeyFilterQuery(filters));
    }

    if (!rules.isEmpty()) {
      query.addCondition(getConsolidatedRuleCondition(rules, tableIdentifier));
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(query, timeFilters, isClusterTable, tableIdentifier);
    }

    for (QLCEViewFilter filter : filters) {
      QLCEViewFieldInput viewFieldInput = getModifiedQLCEViewFieldInput(filter.getField(), isClusterTable);
      String searchString = "";
      String sortKey = "";
      if (filter.getValues().length != 0) {
        searchString = filter.getValues()[0];
      }
      switch (viewFieldInput.getIdentifier()) {
        case AWS:
        case GCP:
        case AZURE:
        case CLUSTER:
        case COMMON:
          if (isSharedCostOuterQuery) {
            query.addAliasedColumn(
                new CustomSql(String.format(DISTINCT, viewFieldInput.getFieldId())), viewFieldInput.getFieldId());
          } else {
            query.addAliasedColumn(new CustomSql(String.format(
                                       DISTINCT, getColumnNameForField(tableIdentifier, viewFieldInput.getFieldId()))),
                viewFieldInput.getFieldId());
            if (AWS_ACCOUNT_FIELD.equals(viewFieldInput.getFieldName()) && filter.getValues().length != 1) {
              // Skipping the first string for InCondition that client is passing in the search filter
              // Considering only the AWS account Ids
              query.addCondition(ComboCondition.or(
                  new InCondition(new CustomSql(getColumnNameForField(tableIdentifier, viewFieldInput.getFieldId())),
                      Arrays.stream(filter.getValues()).skip(1).toArray(Object[] ::new)),
                  getSearchCondition(
                      getColumnNameForField(tableIdentifier, viewFieldInput.getFieldId()), searchString)));
            } else {
              query.addCondition(getSearchCondition(
                  getColumnNameForField(tableIdentifier, viewFieldInput.getFieldId()), searchString));
            }
          }
          sortKey = viewFieldInput.getFieldId();
          break;
        case LABEL:
          if (viewFieldInput.getFieldId().equals(LABEL_KEY.getFieldName())) {
            if (isSharedCostOuterQuery) {
              query.addAliasedColumn(new CustomSql(String.format(DISTINCT, LABEL_KEY_UN_NESTED.getAlias())),
                  LABEL_KEY_UN_NESTED.getAlias());
            } else {
              if (!isClickHouseQuery()) {
                query.addCustomGroupings(LABEL_KEY_UN_NESTED.getAlias());
                query.addAliasedColumn(new CustomSql(String.format(DISTINCT, LABEL_KEY_UN_NESTED.getFieldName())),
                    LABEL_KEY_UN_NESTED.getAlias());
                query.addCondition(
                    new CustomCondition(getSearchCondition(LABEL_KEY_UN_NESTED.getFieldName(), searchString)));
              } else {
                query.addAliasedColumn(
                    new CustomSql(String.format("distinct arrayJoin(%s)", "labels.keys")), LABEL_KEY.getAlias());
              }
            }
            sortKey = LABEL_KEY_UN_NESTED.getAlias();
          } else {
            if (isSharedCostOuterQuery) {
              query.addAliasedColumn(new CustomSql(String.format(DISTINCT, LABEL_VALUE_UN_NESTED.getAlias())),
                  LABEL_VALUE_UN_NESTED.getAlias());
            } else {
              if (!isClickHouseQuery()) {
                query.addCustomGroupings(LABEL_VALUE_UN_NESTED.getAlias());
                query.addCondition(
                    getCondition(getLabelKeyFilter(new String[] {viewFieldInput.getFieldName()}), tableIdentifier));
                query.addAliasedColumn(new CustomSql(String.format(DISTINCT, LABEL_VALUE_UN_NESTED.getFieldName())),
                    LABEL_VALUE_UN_NESTED.getAlias());
                query.addCondition(
                    new CustomCondition(getSearchCondition(LABEL_VALUE_UN_NESTED.getFieldName(), searchString)));
              }
            }
            sortKey = LABEL_VALUE_UN_NESTED.getAlias();
          }
          break;
        case CUSTOM:
          ViewCustomField customField = viewCustomFieldDao.getById(viewFieldInput.getFieldId());
          if (isSharedCostOuterQuery) {
            query.addAliasedColumn(
                new CustomSql(String.format(DISTINCT, modifyStringToComplyRegex(customField.getName()))),
                modifyStringToComplyRegex(customField.getName()));
          } else {
            query = new SelectQuery();
            query.addCustomization(new PgLimitClause(limit));
            query.addCustomization(new PgOffsetClause(offset));
            query.addCustomFromTable(cloudProviderTableName);
            if (!customFields.isEmpty()) {
              modifyQueryForCustomFields(query, customFields);
            }
            query.addAliasedColumn(new CustomSql(String.format(DISTINCT, customField.getSqlFormula())),
                modifyStringToComplyRegex(customField.getName()));
            query.addCondition(new CustomCondition(getSearchCondition(customField.getSqlFormula(), searchString)));
          }
          sortKey = modifyStringToComplyRegex(customField.getName());
          break;
        case BUSINESS_MAPPING:
          BusinessMapping businessMapping = businessMappingService.get(viewFieldInput.getFieldId());
          if (isSharedCostOuterQuery) {
            query.addAliasedColumn(
                new CustomSql(String.format(DISTINCT, modifyStringToComplyRegex(businessMapping.getName()))),
                modifyStringToComplyRegex(businessMapping.getName()));
          } else {
            query = new SelectQuery();
            query.addCustomization(new PgLimitClause(limit));
            query.addCustomization(new PgOffsetClause(offset));
            query.addCustomFromTable(cloudProviderTableName);
            if (!businessMappings.isEmpty()) {
              modifyQueryForBusinessMapping(query, businessMappings, false);
            }
            query.addAliasedColumn(new CustomSql(String.format(
                                       DISTINCT, getSQLCaseStatementBusinessMapping(businessMapping, tableIdentifier))),
                modifyStringToComplyRegex(businessMapping.getName()));
            query.addCondition(new CustomCondition(getSearchCondition(
                getSQLCaseStatementBusinessMapping(businessMapping, tableIdentifier).toString(), searchString)));
          }
          sortKey = modifyStringToComplyRegex(businessMapping.getName());
          break;
        default:
          throw new InvalidRequestException("Invalid View Field Identifier " + viewFieldInput.getIdentifier());
      }
      fields.add(filter.getField());
      if (!sortKey.isEmpty()) {
        sortKey = String.format(LOWER, sortKey);
      }
      query.addCustomOrdering(sortKey, OrderObject.Dir.ASCENDING);
    }
    log.info("Query for view filter {}", query);

    return ViewsQueryMetadata.builder().query(query).fields(fields).build();
  }

  public QLCEViewFieldInput getModifiedQLCEViewFieldInput(
      final QLCEViewFieldInput viewFieldInput, final boolean isClusterTable) {
    QLCEViewFieldInput modifiedQLCEViewFieldInput = viewFieldInput;
    if (isClusterTable && COMMON.equals(viewFieldInput.getIdentifier())
        && "product".equals(viewFieldInput.getFieldId())) {
      modifiedQLCEViewFieldInput = QLCEViewFieldInput.builder()
                                       .fieldId("clustername")
                                       .fieldName("Cluster Name")
                                       .identifier(COMMON)
                                       .identifierName("Common")
                                       .build();
    }
    return modifiedQLCEViewFieldInput;
  }

  private boolean getIsLabelsKeyFilterQuery(List<QLCEViewFilter> filters) {
    for (QLCEViewFilter filter : filters) {
      QLCEViewFieldInput viewFieldInput = filter.getField();
      if (viewFieldInput.getFieldId().equals(LABEL_KEY.getFieldName())) {
        return true;
      }
    }
    return false;
  }

  private void decorateQueryWithLabelsMetadata(
      SelectQuery selectQuery, boolean isLabelsPresent, List<String> labelKeyList, boolean isLabelsKeyFilterQuery) {
    if (isLabelsPresent) {
      if (isLabelsKeyFilterQuery || labelKeyList.isEmpty()
          || (labelKeyList.size() == 1 && labelKeyList.get(0).equals(""))) {
        if (!isClickHouseQuery()) {
          selectQuery.addCustomJoin(leftJoinLabels);
        }
      } else {
        if (!isClickHouseQuery()) {
          selectQuery.addCustomJoin(String.format(leftJoinSelectiveLabels, processLabelKeyList(labelKeyList)));
        }
      }
    }
  }

  private String processLabelKeyList(List<String> labelKeyList) {
    labelKeyList.replaceAll(labelKey -> String.format("'%s'", labelKey));
    return String.join(",", labelKeyList);
  }

  private List<String> collectLabelKeysList(List<ViewRule> rules, List<QLCEViewFilter> filters) {
    List<ViewCondition> labelConditions = new ArrayList<>();
    List<QLCEViewFilter> labelFilters = filters.stream()
                                            .filter(f -> f.getField().getIdentifier() == ViewFieldIdentifier.LABEL)
                                            .collect(Collectors.toList());

    for (ViewRule rule : rules) {
      labelConditions.addAll(
          rule.getViewConditions()
              .stream()
              .filter(c -> ((ViewIdCondition) c).getViewField().getIdentifier() == ViewFieldIdentifier.LABEL)
              .collect(Collectors.toList()));
    }

    List<String> labelKeyList = new ArrayList<>();
    for (QLCEViewFilter labelFilter : labelFilters) {
      if (labelFilter.getField().getFieldId().equals(LABEL_KEY.getFieldName())) {
        labelKeyList.addAll(Arrays.asList(labelFilter.getValues()));
      } else {
        labelKeyList.add(labelFilter.getField().getFieldName());
      }
    }

    for (ViewCondition labelCondition : labelConditions) {
      if (((ViewIdCondition) labelCondition).getViewField().getFieldId().equals(LABEL_KEY.getFieldName())) {
        labelKeyList.addAll(((ViewIdCondition) labelCondition).getValues());
      } else {
        labelKeyList.add(((ViewIdCondition) labelCondition).getViewField().getFieldName());
      }
    }

    return labelKeyList;
  }

  private List<String> modifyQueryForCustomFieldsFilterValues(SelectQuery selectQuery, List<ViewField> customFields) {
    List<String> labelsKeysListAcrossCustomFields = new ArrayList<>();
    List<String> listOfNotNullEntities = new ArrayList<>();
    for (ViewField field : customFields) {
      ViewCustomField customField = viewCustomFieldDao.getById(field.getFieldId());
      final List<String> labelsKeysList = getLabelsKeyList(customField);
      labelsKeysListAcrossCustomFields.addAll(labelsKeysList);
      if (!labelsKeysList.isEmpty()) {
        List<ViewField> customFieldViewFields = customField.getViewFields();
        for (ViewField viewField : customFieldViewFields) {
          if (viewField.getIdentifier() != ViewFieldIdentifier.LABEL) {
            listOfNotNullEntities.add(viewField.getFieldId());
          }
        }
      }
    }
    if (!labelsKeysListAcrossCustomFields.isEmpty()) {
      String[] labelsKeysListAcrossCustomFieldsStringArray =
          labelsKeysListAcrossCustomFields.toArray(new String[labelsKeysListAcrossCustomFields.size()]);

      List<Condition> conditionList = new ArrayList<>();
      for (String fieldId : listOfNotNullEntities) {
        conditionList.add(UnaryCondition.isNotNull(new CustomSql(fieldId)));
      }
      conditionList.add(new InCondition(
          new CustomSql(LABEL_KEY_UN_NESTED.getFieldName()), (Object[]) labelsKeysListAcrossCustomFieldsStringArray));
      selectQuery.addCondition(getSqlOrCondition(conditionList));
    }
    return labelsKeysListAcrossCustomFields;
  }

  private List<String> modifyQueryForBusinessMappingFilterValues(
      SelectQuery selectQuery, List<ViewField> businessMappings, boolean includeSharedCostRules) {
    List<String> labelsKeysListAcrossCustomFields = new ArrayList<>();
    List<String> listOfNotNullEntities = new ArrayList<>();
    for (ViewField field : businessMappings) {
      BusinessMapping businessMapping = businessMappingService.get(field.getFieldId());
      final List<String> labelsKeysList = getLabelsKeyListFromBusinessMapping(businessMapping, includeSharedCostRules);
      labelsKeysListAcrossCustomFields.addAll(labelsKeysList);
      if (!labelsKeysList.isEmpty()) {
        List<ViewField> viewFieldsFromBusinessMapping =
            getViewFieldsFromBusinessMapping(businessMapping, includeSharedCostRules);
        for (ViewField viewField : viewFieldsFromBusinessMapping) {
          if (viewField.getIdentifier() != ViewFieldIdentifier.LABEL) {
            listOfNotNullEntities.add(viewField.getFieldId());
          }
        }
      }
    }
    if (!labelsKeysListAcrossCustomFields.isEmpty()) {
      String[] labelsKeysListAcrossCustomFieldsStringArray =
          labelsKeysListAcrossCustomFields.toArray(new String[labelsKeysListAcrossCustomFields.size()]);

      List<Condition> conditionList = new ArrayList<>();
      for (String fieldId : listOfNotNullEntities) {
        conditionList.add(UnaryCondition.isNotNull(new CustomSql(fieldId)));
      }
      conditionList.add(new InCondition(
          new CustomSql(LABEL_KEY_UN_NESTED.getFieldName()), (Object[]) labelsKeysListAcrossCustomFieldsStringArray));
      selectQuery.addCondition(getSqlOrCondition(conditionList));
    }
    return labelsKeysListAcrossCustomFields;
  }

  private QLCEViewFilter getLabelKeyFilter(String[] values) {
    return QLCEViewFilter.builder()
        .field(QLCEViewFieldInput.builder()
                   .fieldId(LABEL_KEY_UN_NESTED.getFieldName())
                   .identifier(ViewFieldIdentifier.LABEL)
                   .identifierName(ViewFieldIdentifier.LABEL.getDisplayName())
                   .build())
        .operator(QLCEViewFilterOperator.IN)
        .values(values)
        .build();
  }

  private boolean evaluateLabelsPresent(List<ViewRule> rules, List<QLCEViewFilter> filters) {
    boolean labelFilterPresent =
        filters.stream().anyMatch(f -> f.getField().getIdentifier() == ViewFieldIdentifier.LABEL);
    boolean labelConditionPresent = false;

    for (ViewRule rule : rules) {
      labelConditionPresent = labelConditionPresent
          || rule.getViewConditions().stream().anyMatch(
              c -> ((ViewIdCondition) c).getViewField().getIdentifier() == ViewFieldIdentifier.LABEL);
    }

    return labelFilterPresent || labelConditionPresent;
  }

  private boolean modifyQueryForCustomFields(SelectQuery selectQuery, List<ViewField> customFields) {
    List<Condition> conditionList = new ArrayList<>();
    for (ViewField field : customFields) {
      ViewCustomField customField = viewCustomFieldDao.getById(field.getFieldId());
      final List<String> labelsKeysList = getLabelsKeyList(customField);
      if (!labelsKeysList.isEmpty()) {
        List<ViewField> customFieldViewFields = customField.getViewFields();
        for (ViewField viewField : customFieldViewFields) {
          if (viewField.getIdentifier() != ViewFieldIdentifier.LABEL) {
            conditionList.add(UnaryCondition.isNotNull(new CustomSql(viewField.getFieldId())));
          }
        }
      }
    }
    if (!conditionList.isEmpty()) {
      selectQuery.addCondition(getSqlOrCondition(conditionList));
      return true;
    }
    return false;
  }

  private boolean modifyQueryForBusinessMapping(
      SelectQuery selectQuery, List<ViewField> businessMappings, boolean includeSharedCostRules) {
    List<Condition> conditionList = new ArrayList<>();
    for (ViewField field : businessMappings) {
      BusinessMapping businessMapping = businessMappingService.get(field.getFieldId());
      final List<String> labelsKeysList = getLabelsKeyListFromBusinessMapping(businessMapping, includeSharedCostRules);
      if (!labelsKeysList.isEmpty()) {
        List<ViewField> viewFieldsFromBusinessMapping =
            getViewFieldsFromBusinessMapping(businessMapping, includeSharedCostRules);
        for (ViewField viewField : viewFieldsFromBusinessMapping) {
          if (viewField.getIdentifier() != ViewFieldIdentifier.LABEL) {
            conditionList.add(UnaryCondition.isNotNull(new CustomSql(viewField.getFieldId())));
          }
        }
      }
    }
    if (!conditionList.isEmpty()) {
      selectQuery.addCondition(getSqlOrCondition(conditionList));
      return true;
    }
    return false;
  }

  private List<String> getLabelsKeyList(ViewCustomField customField) {
    return customField.getViewFields()
        .stream()
        .filter(f -> f.getIdentifier() == ViewFieldIdentifier.LABEL)
        .map(ViewField::getFieldName)
        .collect(Collectors.toList());
  }

  private List<String> getLabelsKeyListFromBusinessMapping(
      BusinessMapping businessMapping, boolean includeSharedCostRules) {
    List<ViewField> businessMappingViewFields =
        getViewFieldsFromBusinessMapping(businessMapping, includeSharedCostRules);
    return businessMappingViewFields.stream()
        .filter(f -> f.getIdentifier() == ViewFieldIdentifier.LABEL)
        .map(ViewField::getFieldName)
        .collect(Collectors.toList());
  }

  @NotNull
  private List<ViewField> getViewFieldsFromBusinessMapping(
      BusinessMapping businessMapping, boolean includeSharedCostRules) {
    List<ViewField> businessMappingViewFields = new ArrayList<>();
    for (CostTarget costTarget : businessMapping.getCostTargets()) {
      for (ViewRule rule : costTarget.getRules()) {
        for (ViewCondition condition : rule.getViewConditions()) {
          businessMappingViewFields.add(((ViewIdCondition) condition).getViewField());
        }
      }
    }
    if (includeSharedCostRules) {
      for (SharedCost sharedCost : businessMapping.getSharedCosts()) {
        for (ViewRule rule : sharedCost.getRules()) {
          for (ViewCondition condition : rule.getViewConditions()) {
            businessMappingViewFields.add(((ViewIdCondition) condition).getViewField());
          }
        }
      }
    }
    return businessMappingViewFields;
  }

  private void decorateQueryWithSortCriteria(SelectQuery selectQuery, List<QLCEViewSortCriteria> sortCriteriaList) {
    for (QLCEViewSortCriteria sortCriteria : sortCriteriaList) {
      addOrderBy(selectQuery, sortCriteria);
    }
  }

  private void addOrderBy(SelectQuery selectQuery, QLCEViewSortCriteria sortCriteria) {
    Object sortKey;
    switch (sortCriteria.getSortType()) {
      case COST:
        sortKey = ViewsMetaDataFields.COST.getAlias();
        break;
      case CLUSTER_COST:
        sortKey = ViewsMetaDataFields.CLUSTER_COST.getAlias();
        break;
      case TIME:
        sortKey = ViewsMetaDataFields.START_TIME.getAlias();
        break;
      default:
        throw new InvalidRequestException("Sort type not supported");
    }
    OrderObject.Dir dir =
        sortCriteria.getSortOrder() == QLCESortOrder.ASCENDING ? OrderObject.Dir.ASCENDING : OrderObject.Dir.DESCENDING;
    selectQuery.addCustomOrdering(sortKey, dir);
  }

  private void decorateQueryWithAggregations(SelectQuery selectQuery, List<QLCEViewAggregation> aggregations,
      String tableIdentifier, boolean isSharedCostQuery) {
    for (QLCEViewAggregation aggregation : aggregations) {
      decorateQueryWithAggregation(selectQuery, aggregation, tableIdentifier, isSharedCostQuery);
    }
  }

  private void decorateQueryWithAggregation(
      SelectQuery selectQuery, QLCEViewAggregation aggregation, String tableIdentifier, boolean isSharedCostQuery) {
    FunctionCall functionCall = getFunctionCallType(aggregation.getOperationType());
    String columnName = getColumnNameForField(tableIdentifier, aggregation.getColumnName());
    if (aggregation.getColumnName().equals(ViewsMetaDataFields.START_TIME.getFieldName())) {
      final String aliasedStartTimeColumnName = String.format(
          aliasStartTimeMaxMin, ViewsMetaDataFields.START_TIME.getFieldName(), aggregation.getOperationType());
      final String startTimeColumnName = isSharedCostQuery ? aliasedStartTimeColumnName : columnName;
      selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
          Objects.requireNonNull(functionCall).addCustomParams(new CustomSql(startTimeColumnName)),
          aliasedStartTimeColumnName));
    } else {
      selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
          Objects.requireNonNull(functionCall).addCustomParams(new CustomSql(columnName)),
          getAliasNameForAggregation(aggregation.getColumnName())));
    }
  }

  private void decorateSharedCostQueryWithAggregations(final SelectQuery selectQuery,
      final List<QLCEViewAggregation> aggregations, final String tableIdentifier, final double numeratorValue,
      final double denominatorValue) {
    if (Objects.nonNull(aggregations)) {
      for (final QLCEViewAggregation aggregation : aggregations) {
        decorateSharedCostQueryWithAggregation(
            selectQuery, aggregation, tableIdentifier, numeratorValue, denominatorValue);
      }
    }
  }

  private void decorateSharedCostQueryWithAggregation(final SelectQuery selectQuery,
      final QLCEViewAggregation aggregation, final String tableIdentifier, final double numeratorValue,
      final double denominatorValue) {
    final FunctionCall functionCall = getFunctionCallType(aggregation.getOperationType());
    final String columnName = getColumnNameForField(tableIdentifier, aggregation.getColumnName());
    if (aggregation.getColumnName().equals(ViewsMetaDataFields.START_TIME.getFieldName())) {
      final String aliasedStartTimeColumnName = String.format(
          aliasStartTimeMaxMin, ViewsMetaDataFields.START_TIME.getFieldName(), aggregation.getOperationType());
      selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
          Objects.requireNonNull(functionCall).addCustomParams(new CustomSql(aliasedStartTimeColumnName)),
          aliasedStartTimeColumnName));
    } else {
      selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
          String.format(SHARED_COST_SPLIT_COLUMN,
              Objects.requireNonNull(functionCall).addCustomParams(new CustomSql(columnName)), numeratorValue,
              denominatorValue),
          getAliasNameForAggregation(aggregation.getColumnName())));
    }
  }

  private void decorateQueryWithSharedCostAggregations(SelectQuery selectQuery, List<QLCEViewFieldInput> groupByEntity,
      boolean isClusterTable, String tableIdentifier, BusinessMapping sharedCostBusinessMapping) {
    List<QLCEViewFieldInput> groupByBusinessMapping =
        groupByEntity.stream()
            .filter(groupBy -> groupBy.getIdentifier() == BUSINESS_MAPPING)
            .collect(Collectors.toList());

    if (groupByBusinessMapping.size() > 1) {
      throw new InvalidRequestException("Invalid request: Cannot group by multiple cost categories.");
    }
    if (groupByBusinessMapping.size() == 1) {
      BusinessMapping businessMapping = businessMappingService.get(groupByBusinessMapping.get(0).getFieldId());
      if (businessMapping != null) {
        List<SharedCost> sharedCosts = businessMapping.getSharedCosts();
        if (sharedCosts != null) {
          sharedCosts.forEach(sharedCost
              -> decorateQueryWithSharedCostAggregation(selectQuery, sharedCost, isClusterTable, tableIdentifier));
        }
      }
    } else if (sharedCostBusinessMapping != null) {
      // Group by other than cost category
      List<SharedCost> sharedCosts = sharedCostBusinessMapping.getSharedCosts();
      if (sharedCosts != null) {
        sharedCosts.forEach(sharedCost
            -> selectQuery.addCondition(getConsolidatedRuleCondition(sharedCost.getRules(), tableIdentifier)));
      }
    }
  }

  private void decorateQueryWithSharedCostAggregation(
      SelectQuery selectQuery, SharedCost sharedCost, boolean isClusterTable, String tableIdentifier) {
    FunctionCall functionCall = getFunctionCallType(SUM);
    selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
        new CoalesceExpression(Objects.requireNonNull(functionCall)
                                   .addCustomParams(getSQLCaseStatementBusinessMappingSharedCost(
                                       sharedCost.getRules(), isClusterTable, tableIdentifier)),
            Collections.singletonList(0)),
        modifyStringToComplyRegex(sharedCost.getName())));
  }

  private CustomSql getSQLCaseStatementBusinessMappingSharedCost(
      List<ViewRule> sharedCostRules, boolean isClusterTable, String tableIdentifier) {
    String columnName =
        isClusterTable ? ViewsMetaDataFields.CLUSTER_COST.getAlias() : ViewsMetaDataFields.COST.getAlias();
    if (isClickHouseQuery()) {
      columnName = isClusterTable
          ? String.format("%s.%s", ClickHouseConstants.CLICKHOUSE_CLUSTER_DATA_TABLE, columnName)
          : String.format("%s.%s", ClickHouseConstants.CLICKHOUSE_UNIFIED_TABLE, columnName);
    }
    CaseStatement caseStatement = new CaseStatement();
    caseStatement.addWhen(getConsolidatedRuleCondition(sharedCostRules, tableIdentifier), new CustomSql(columnName));
    caseStatement.addElseNull();
    return new CustomSql(caseStatement);
  }

  private FunctionCall getFunctionCallType(QLCEViewAggregateOperation operationType) {
    switch (operationType) {
      case SUM:
        return FunctionCall.sum();
      case MAX:
        return FunctionCall.max();
      case MIN:
        return FunctionCall.min();
      case AVG:
        return FunctionCall.avg();
      default:
        return null;
    }
  }

  private void decorateQueryWithGroupByTime(SelectQuery selectQuery, QLCEViewTimeTruncGroupBy groupByTime,
      boolean isTimeInEpochMillis, String tableIdentifier, boolean isSharedCostQuery) {
    String startTimeColumnName =
        getColumnNameForField(tableIdentifier, ViewsMetaDataFields.START_TIME.getFieldName().toLowerCase(Locale.ROOT));
    if (isClickHouseQuery()) {
      decorateClickHouseQueryWithGroupByTime(
          selectQuery, groupByTime, isTimeInEpochMillis, isSharedCostQuery, startTimeColumnName);
    } else {
      decorateBigQueryWithGroupByTime(
          selectQuery, groupByTime, isTimeInEpochMillis, isSharedCostQuery, startTimeColumnName);
    }

    selectQuery.addCustomGroupings(ViewsMetaDataFields.TIME_GRANULARITY.getFieldName());
    selectQuery.addCustomOrdering(ViewsMetaDataFields.TIME_GRANULARITY.getFieldName(), OrderObject.Dir.ASCENDING);
  }

  private void decorateClickHouseQueryWithGroupByTime(final SelectQuery selectQuery,
      final QLCEViewTimeTruncGroupBy groupByTime, final boolean isTimeInEpochMillis, final boolean isSharedCostQuery,
      final String startTimeColumnName) {
    final String aliasedTimeGranularityColumn = ViewsMetaDataFields.TIME_GRANULARITY.getFieldName();
    if (isTimeInEpochMillis) {
      String toDateTimeColumn = "toDateTime(" + startTimeColumnName + "/1000)";
      String timeBucket = getGroupByTimeQueryWithDateTrunc(groupByTime, toDateTimeColumn);
      final Object timeGranularityColumn =
          isSharedCostQuery ? aliasedTimeGranularityColumn : new CustomExpression(timeBucket).setDisableParens(true);
      selectQuery.addCustomColumns(
          Converter.toCustomColumnSqlObject(timeGranularityColumn, aliasedTimeGranularityColumn));
    } else {
      String timeBucket = getGroupByTimeQueryWithDateTrunc(groupByTime, startTimeColumnName);
      final Object timeGranularityColumn =
          isSharedCostQuery ? aliasedTimeGranularityColumn : new CustomExpression(timeBucket).setDisableParens(true);
      selectQuery.addCustomColumns(
          Converter.toCustomColumnSqlObject(timeGranularityColumn, aliasedTimeGranularityColumn));
    }
  }

  private void decorateBigQueryWithGroupByTime(final SelectQuery selectQuery,
      final QLCEViewTimeTruncGroupBy groupByTime, final boolean isTimeInEpochMillis, final boolean isSharedCostQuery,
      final String startTimeColumnName) {
    final String aliasedTimeGranularityColumn = ViewsMetaDataFields.TIME_GRANULARITY.getFieldName();
    final Object timeGranularityColumn = isSharedCostQuery ? aliasedTimeGranularityColumn
        : isTimeInEpochMillis
        ? new TimeTruncatedExpression(
            new TimestampMillisExpression(new CustomSql(startTimeColumnName)), groupByTime.getResolution())
        : new TimeTruncatedExpression(new CustomSql(startTimeColumnName), groupByTime.getResolution());
    selectQuery.addCustomColumns(
        Converter.toCustomColumnSqlObject(timeGranularityColumn, aliasedTimeGranularityColumn));
  }

  public String getGroupByTimeQueryWithDateTrunc(QLCEViewTimeTruncGroupBy groupByTime, String dbFieldName) {
    String unit;
    switch (groupByTime.getResolution()) {
      case HOUR:
        unit = "hour";
        break;
      case DAY:
        unit = "day";
        break;
      case WEEK:
        unit = "week";
        break;
      case MONTH:
        unit = "month";
        break;
      default:
        log.warn("Unsupported timeGroupType " + groupByTime.getResolution());
        throw new InvalidRequestException("Cant apply time group by");
    }

    return new StringBuilder("date_trunc('").append(unit).append("',").append(dbFieldName).append(")").toString();
  }

  private void decorateQueryWithGroupByTimeWithOffset(SelectQuery selectQuery, QLCEViewTimeTruncGroupBy groupByTime,
      boolean isTimeInEpochMillis, int timeOffsetInDays, String tableIdentifier) {
    String startTimeColumnName = getColumnNameForField(tableIdentifier, ViewsMetaDataFields.START_TIME.getFieldName());
    if (isClickHouseQuery()) {
      if (isTimeInEpochMillis) {
        String toDateTimeColumn = "toDateTime(" + startTimeColumnName + "/1000)";
        toDateTimeColumn += String.format(" - toIntervalDay(%s)", timeOffsetInDays);
        String timeBucket = getGroupByTimeQueryWithDateTrunc(groupByTime, toDateTimeColumn);
        selectQuery.addCustomColumns(
            Converter.toCustomColumnSqlObject(new CustomExpression(timeBucket).setDisableParens(true),
                ViewsMetaDataFields.TIME_GRANULARITY.getFieldName()));
      } else {
        String timeBucket = getGroupByTimeQueryWithDateTrunc(
            groupByTime, startTimeColumnName + String.format(" - toIntervalDay(%s)", timeOffsetInDays));
        selectQuery.addCustomColumns(
            Converter.toCustomColumnSqlObject(new CustomExpression(timeBucket).setDisableParens(true),
                ViewsMetaDataFields.TIME_GRANULARITY.getFieldName()));
      }
    } else {
      if (isTimeInEpochMillis) {
        selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
            new TimeTruncatedExpression(
                new TimestampDiffExpression(
                    new TimestampMillisExpression(new CustomSql(startTimeColumnName)), timeOffsetInDays, DAY),
                groupByTime.getResolution()),
            ViewsMetaDataFields.TIME_GRANULARITY.getFieldName()));
      } else {
        selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
            new TimeTruncatedExpression(
                new TimestampDiffExpression(new CustomSql(startTimeColumnName), timeOffsetInDays, DAY),
                groupByTime.getResolution()),
            ViewsMetaDataFields.TIME_GRANULARITY.getFieldName()));
      }
    }

    selectQuery.addCustomGroupings(ViewsMetaDataFields.TIME_GRANULARITY.getFieldName());
    selectQuery.addCustomOrdering(ViewsMetaDataFields.TIME_GRANULARITY.getFieldName(), OrderObject.Dir.ASCENDING);
  }

  protected List<QLCEViewFieldInput> getGroupByEntity(List<QLCEViewGroupBy> groupBy) {
    return groupBy != null ? groupBy.stream()
                                 .filter(g -> g.getEntityGroupBy() != null)
                                 .map(QLCEViewGroupBy::getEntityGroupBy)
                                 .collect(Collectors.toList())
                           : Collections.emptyList();
  }

  private List<QLCEViewFieldInput> getLabelGroupBy(List<QLCEViewFieldInput> groupBy) {
    return groupBy != null
        ? groupBy.stream()
              .filter(qlCEViewFieldInput -> qlCEViewFieldInput.getIdentifier() == ViewFieldIdentifier.LABEL)
              .collect(Collectors.toList())
        : Collections.emptyList();
  }

  public QLCEViewTimeTruncGroupBy getGroupByTime(List<QLCEViewGroupBy> groupBy) {
    if (groupBy != null) {
      Optional<QLCEViewTimeTruncGroupBy> first = groupBy.stream()
                                                     .filter(g -> g.getTimeTruncGroupBy() != null)
                                                     .map(QLCEViewGroupBy::getTimeTruncGroupBy)
                                                     .findFirst();
      return first.orElse(null);
    }
    return null;
  }

  private Condition getConsolidatedRuleCondition(List<ViewRule> rules, String tableIdentifier) {
    List<Condition> conditionList = new ArrayList<>();
    for (ViewRule rule : rules) {
      conditionList.add(getPerRuleCondition(rule, tableIdentifier));
    }
    return getSqlOrCondition(conditionList);
  }

  private Condition getPerRuleCondition(ViewRule rule, String tableIdentifier) {
    List<Condition> conditionList = new ArrayList<>();
    for (ViewCondition condition : rule.getViewConditions()) {
      conditionList.add(getCondition(mapConditionToFilter((ViewIdCondition) condition), tableIdentifier));
    }
    return getSqlAndCondition(conditionList);
  }

  // Used in PerspectiveToAnomalyQueryHelper
  public QLCEViewFilter mapConditionToFilter(ViewIdCondition condition) {
    return QLCEViewFilter.builder()
        .field(getViewFieldInput(condition.getViewField()))
        .operator(mapViewIdOperatorToQLCEViewFilterOperator(condition.getViewOperator()))
        .values(getStringArray(Objects.isNull(condition.getValues()) ? ImmutableList.of("") : condition.getValues()))
        .build();
  }

  private String[] getStringArray(List<String> values) {
    return values.toArray(new String[values.size()]);
  }

  private QLCEViewFilterOperator mapViewIdOperatorToQLCEViewFilterOperator(ViewIdOperator operator) {
    QLCEViewFilterOperator qlCEViewFilterOperator = null;
    if (operator.equals(ViewIdOperator.IN)) {
      qlCEViewFilterOperator = QLCEViewFilterOperator.IN;
    } else if (operator.equals(ViewIdOperator.NOT_IN)) {
      qlCEViewFilterOperator = QLCEViewFilterOperator.NOT_IN;
    } else if (operator.equals(ViewIdOperator.NOT_NULL)) {
      qlCEViewFilterOperator = QLCEViewFilterOperator.NOT_NULL;
    } else if (operator.equals(ViewIdOperator.NULL)) {
      qlCEViewFilterOperator = QLCEViewFilterOperator.NULL;
    } else if (operator.equals(ViewIdOperator.EQUALS)) {
      qlCEViewFilterOperator = QLCEViewFilterOperator.EQUALS;
    } else if (operator.equals(ViewIdOperator.LIKE)) {
      qlCEViewFilterOperator = QLCEViewFilterOperator.LIKE;
    }
    return qlCEViewFilterOperator;
  }

  public QLCEViewFieldInput getViewFieldInput(ViewField field) {
    return QLCEViewFieldInput.builder()
        .fieldId(field.getFieldId())
        .fieldName(field.getFieldName())
        .identifier(field.getIdentifier())
        .identifierName(field.getIdentifier().getDisplayName())
        .build();
  }

  public static Condition getSqlAndCondition(List<Condition> conditionList) {
    switch (conditionList.size()) {
      case 2:
        return ComboCondition.and(conditionList.get(0), conditionList.get(1));
      case 3:
        return ComboCondition.and(conditionList.get(0), conditionList.get(1), conditionList.get(2));
      case 4:
        return ComboCondition.and(
            conditionList.get(0), conditionList.get(1), conditionList.get(2), conditionList.get(3));
      case 5:
        return ComboCondition.and(conditionList.get(0), conditionList.get(1), conditionList.get(2),
            conditionList.get(3), conditionList.get(4));
      case 6:
        return ComboCondition.and(conditionList.get(0), conditionList.get(1), conditionList.get(2),
            conditionList.get(3), conditionList.get(4), conditionList.get(5));
      case 7:
        return ComboCondition.and(conditionList.get(0), conditionList.get(1), conditionList.get(2),
            conditionList.get(3), conditionList.get(4), conditionList.get(5), conditionList.get(6));
      case 8:
        return ComboCondition.and(conditionList.get(0), conditionList.get(1), conditionList.get(2),
            conditionList.get(3), conditionList.get(4), conditionList.get(5), conditionList.get(6),
            conditionList.get(7));
      case 9:
        return ComboCondition.and(conditionList.get(0), conditionList.get(1), conditionList.get(2),
            conditionList.get(3), conditionList.get(4), conditionList.get(5), conditionList.get(6),
            conditionList.get(7), conditionList.get(8));
      case 10:
        return ComboCondition.and(conditionList.get(0), conditionList.get(1), conditionList.get(2),
            conditionList.get(3), conditionList.get(4), conditionList.get(5), conditionList.get(6),
            conditionList.get(7), conditionList.get(8), conditionList.get(9));
      default:
        return conditionList.get(0);
    }
  }

  public static Condition getSqlOrCondition(List<Condition> conditionList) {
    switch (conditionList.size()) {
      case 2:
        return ComboCondition.or(conditionList.get(0), conditionList.get(1));
      case 3:
        return ComboCondition.or(conditionList.get(0), conditionList.get(1), conditionList.get(2));
      case 4:
        return ComboCondition.or(
            conditionList.get(0), conditionList.get(1), conditionList.get(2), conditionList.get(3));
      case 5:
        return ComboCondition.or(conditionList.get(0), conditionList.get(1), conditionList.get(2), conditionList.get(3),
            conditionList.get(4));
      case 6:
        return ComboCondition.or(conditionList.get(0), conditionList.get(1), conditionList.get(2), conditionList.get(3),
            conditionList.get(4), conditionList.get(5));
      case 7:
        return ComboCondition.or(conditionList.get(0), conditionList.get(1), conditionList.get(2), conditionList.get(3),
            conditionList.get(4), conditionList.get(5), conditionList.get(6));
      case 8:
        return ComboCondition.or(conditionList.get(0), conditionList.get(1), conditionList.get(2), conditionList.get(3),
            conditionList.get(4), conditionList.get(5), conditionList.get(6), conditionList.get(7));
      case 9:
        return ComboCondition.or(conditionList.get(0), conditionList.get(1), conditionList.get(2), conditionList.get(3),
            conditionList.get(4), conditionList.get(5), conditionList.get(6), conditionList.get(7),
            conditionList.get(8));
      case 10:
        return ComboCondition.or(conditionList.get(0), conditionList.get(1), conditionList.get(2), conditionList.get(3),
            conditionList.get(4), conditionList.get(5), conditionList.get(6), conditionList.get(7),
            conditionList.get(8), conditionList.get(9));
      default:
        return conditionList.get(0);
    }
  }

  private void decorateQueryWithFilters(SelectQuery selectQuery, List<QLCEViewFilter> filters, String tableIdentifier) {
    for (QLCEViewFilter filter : filters) {
      selectQuery.addCondition(getCondition(filter, tableIdentifier));
    }
  }

  private void decorateQueryWithTimeFilters(
      SelectQuery selectQuery, List<QLCEViewTimeFilter> timeFilters, boolean isClusterTable, String tableIdentifier) {
    for (QLCEViewTimeFilter timeFilter : timeFilters) {
      selectQuery.addCondition(getCondition(timeFilter, isClusterTable, tableIdentifier));
    }
  }

  private void decorateQueryWithInExpressionFilters(SelectQuery selectQuery, List<QLCEInExpressionFilter> filters,
      List<QLCEViewFieldInput> groupByEntity, String tableIdentifier) {
    final Optional<QLCEViewFieldInput> groupBy = groupByEntity.stream().filter(Objects::nonNull).findFirst();
    final Optional<QLCEInExpressionFilter> filter = filters.stream().filter(Objects::nonNull).findFirst();
    if (filter.isPresent() && groupBy.isPresent()) {
      final CustomSql sqlObjectFromField = getSQLObjectFromField(groupBy.get(), tableIdentifier);
      final ViewFieldIdentifier groupByIdentifier = groupBy.get().getIdentifier();
      if (groupByIdentifier == BUSINESS_MAPPING) {
        selectQuery.addCondition(getCondition(filter.get(), sqlObjectFromField));
      } else if (groupByIdentifier == ViewFieldIdentifier.LABEL) {
        String labelSubQuery = isClickHouseQuery()
            ? String.format(CLICKHOUSE_LABEL_VALUE_COLUMN, groupBy.get().getFieldName())
            : String.format(labelsSubQuery, groupBy.get().getFieldName());
        selectQuery.addCondition(getCondition(filter.get(), labelSubQuery));
      } else {
        selectQuery.addCondition(getCondition(filter.get()));
      }
    }
  }

  private Condition getCondition(QLCEViewFilter filter, String tableIdentifier) {
    Condition condition;
    CustomSql conditionKey = getSQLObjectFromField(filter.getField(), tableIdentifier);
    if (conditionKey.toString().equals(ViewsMetaDataFields.LABEL_VALUE.getFieldName())) {
      String labelKey = filter.getField().getFieldName();
      String labelSubQuery = isClickHouseQuery() ? String.format(CLICKHOUSE_LABEL_VALUE_COLUMN, labelKey)
                                                 : String.format(labelsSubQuery, labelKey);
      conditionKey = new CustomSql(labelSubQuery);

      if (filter.getOperator() == QLCEViewFilterOperator.NOT_NULL) {
        return UnaryCondition.isNotNull(conditionKey);
      }

      if (filter.getOperator() == QLCEViewFilterOperator.NULL) {
        return UnaryCondition.isNull(conditionKey);
      }
    }
    QLCEViewFilterOperator operator = filter.getOperator();

    if (filter.getValues().length > 0 && operator == QLCEViewFilterOperator.EQUALS) {
      operator = QLCEViewFilterOperator.IN;
    }

    ViewFieldIdentifier viewFieldIdentifier = filter.getField().getIdentifier();
    BusinessMapping businessMapping = null;
    if (viewFieldIdentifier == BUSINESS_MAPPING) {
      businessMapping = businessMappingService.get(filter.getField().getFieldId());
    }

    switch (operator) {
      case EQUALS:
        condition = BinaryCondition.equalTo(conditionKey, filter.getValues()[0]);
        break;
      case IN:
        condition = new InCondition(conditionKey, (Object[]) filter.getValues());
        break;
      case NOT_IN:
        condition = new InCondition(conditionKey, (Object[]) filter.getValues()).setNegate(true);
        break;
      case NOT_NULL:
        condition = UnaryCondition.isNotNull(conditionKey);
        if (viewFieldIdentifier == BUSINESS_MAPPING) {
          condition = new InCondition(conditionKey, businessMapping.getUnallocatedCost().getLabel()).setNegate(true);
        }
        break;
      case NULL:
        if (isClickHouseQuery()) {
          condition = BinaryCondition.equalTo(conditionKey, "");
        } else {
          condition = UnaryCondition.isNull(conditionKey);
        }
        if (viewFieldIdentifier == BUSINESS_MAPPING) {
          condition = new InCondition(conditionKey, businessMapping.getUnallocatedCost().getLabel());
        }
        break;
      case LIKE:
        condition = new CustomCondition(String.format(regexFilter, conditionKey, filter.getValues()[0]));
        break;
      case SEARCH:
        // Searching capability for idFilters only
        condition = new CustomCondition(getSearchCondition(conditionKey.toString(), filter.getValues()[0]));
        break;
      default:
        throw new InvalidRequestException("Invalid View Filter operator: " + operator);
    }

    return condition;
  }

  // Change it back
  private Condition getCondition(
      QLCEViewTimeFilter timeFilter, boolean addLongValueConditions, String tableIdentifier) {
    CustomSql conditionKey = getSQLObjectFromField(timeFilter.getField(), tableIdentifier);
    QLCEViewTimeFilterOperator operator = timeFilter.getOperator();

    switch (operator) {
      case BEFORE:
        return addLongValueConditions ? BinaryCondition.lessThanOrEq(conditionKey, timeFilter.getValue().longValue())
            : !isClickHouseQuery()
            ? BinaryCondition.lessThanOrEq(conditionKey, Instant.ofEpochMilli(timeFilter.getValue().longValue()))
            : BinaryCondition.lessThanOrEq(conditionKey, getConvertedDate(timeFilter.getValue().longValue()));
      case AFTER:
        return addLongValueConditions ? BinaryCondition.greaterThanOrEq(conditionKey, timeFilter.getValue().longValue())
            : !isClickHouseQuery()
            ? BinaryCondition.greaterThanOrEq(conditionKey, Instant.ofEpochMilli(timeFilter.getValue().longValue()))
            : BinaryCondition.greaterThanOrEq(conditionKey, getConvertedDate(timeFilter.getValue().longValue()));
      default:
        throw new InvalidRequestException("Invalid View TimeFilter operator: " + operator);
    }
  }

  private String getConvertedDate(long milliseconds) {
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
    return formatter.format(milliseconds);
  }

  private Condition getCondition(QLCEInExpressionFilter filter) {
    Condition condition = new InCondition(Converter.toCustomColumnSqlObject(new InFieldsExpression(filter.getFields())),
        Converter.toCustomColumnSqlObject(new InValuesExpression(filter.getValues())));
    if (Objects.nonNull(filter.getNullValueField())) {
      condition = ComboCondition.or(condition, UnaryCondition.isNull(new CustomSql(filter.getNullValueField())));
    }
    return condition;
  }

  private Condition getCondition(QLCEInExpressionFilter filter, Object sqlObjectFromField) {
    Condition condition = new InCondition(Converter.toCustomColumnSqlObject(sqlObjectFromField),
        Converter.toCustomColumnSqlObject(new InValuesExpression(filter.getValues())));
    if (Objects.nonNull(filter.getNullValueField())) {
      condition = ComboCondition.or(condition, UnaryCondition.isNull(new CustomSql(sqlObjectFromField)));
    }
    return condition;
  }

  private CustomSql getSQLObjectFromField(QLCEViewFieldInput field, String tableIdentifier) {
    switch (field.getIdentifier()) {
      case CLUSTER:
      case AWS:
      case GCP:
      case AZURE:
      case COMMON:
      case LABEL:
        return new CustomSql(getColumnNameForField(tableIdentifier, field.getFieldId()));
      case BUSINESS_MAPPING:
        if (!isClickHouseQuery()) {
          return getSQLCaseStatementBusinessMapping(businessMappingService.get(field.getFieldId()), tableIdentifier);
        } else {
          return getClickHouseSQLCaseStatementBusinessMapping(
              businessMappingService.get(field.getFieldId()), tableIdentifier);
        }
      case CUSTOM:
        return new CustomSql(viewCustomFieldDao.getById(field.getFieldId()).getSqlFormula());
      default:
        throw new InvalidRequestException("Invalid View Field Identifier " + field.getIdentifier());
    }
  }

  public CustomSql getSQLCaseStatementBusinessMapping(BusinessMapping businessMapping, String tableIdentifier) {
    CaseStatement caseStatement = new CaseStatement();
    if (Objects.nonNull(businessMapping.getCostTargets())) {
      for (CostTarget costTarget : businessMapping.getCostTargets()) {
        caseStatement.addWhen(
            getConsolidatedRuleCondition(costTarget.getRules(), tableIdentifier), costTarget.getName());
      }
      if (Objects.nonNull(businessMapping.getUnallocatedCost())
          && businessMapping.getUnallocatedCost().getStrategy() == UnallocatedCostStrategy.DISPLAY_NAME) {
        caseStatement.addElse(businessMapping.getUnallocatedCost().getLabel());
      } else {
        caseStatement.addElse(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName());
      }
    } else {
      String unallocatedCostLabel = ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName();
      if (Objects.nonNull(businessMapping.getUnallocatedCost())
          && businessMapping.getUnallocatedCost().getStrategy() == UnallocatedCostStrategy.DISPLAY_NAME) {
        unallocatedCostLabel = businessMapping.getUnallocatedCost().getLabel();
      }
      return new CustomSql(String.format("'%s'", unallocatedCostLabel));
    }
    return new CustomSql(caseStatement);
  }

  private CustomSql getClickHouseSQLCaseStatementBusinessMapping(
      BusinessMapping businessMapping, String tableIdentifier) {
    StringBuilder multiIfStatement = new StringBuilder();
    multiIfStatement.append(MULTI_IF_STATEMENT_OPENING);
    if (Objects.nonNull(businessMapping.getCostTargets())) {
      for (CostTarget costTarget : businessMapping.getCostTargets()) {
        multiIfStatement.append(getConsolidatedRuleCondition(costTarget.getRules(), tableIdentifier))
            .append(',')
            .append(String.format("'%s'", costTarget.getName()))
            .append(',');
      }
      if (Objects.nonNull(businessMapping.getUnallocatedCost())
          && businessMapping.getUnallocatedCost().getStrategy() == UnallocatedCostStrategy.DISPLAY_NAME) {
        multiIfStatement.append(String.format("'%s'", businessMapping.getUnallocatedCost().getLabel())).append(')');
      } else {
        multiIfStatement.append(String.format("'%s'", ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName()))
            .append(')');
      }
    } else {
      String unallocatedCostLabel = ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName();
      if (Objects.nonNull(businessMapping.getUnallocatedCost())
          && businessMapping.getUnallocatedCost().getStrategy() == UnallocatedCostStrategy.DISPLAY_NAME) {
        unallocatedCostLabel = businessMapping.getUnallocatedCost().getLabel();
      }
      return new CustomSql(String.format("'%s'", unallocatedCostLabel));
    }
    return new CustomSql(multiIfStatement.toString());
  }

  private CustomSql getSQLCaseStatementForSharedCost(
      final BusinessMapping businessMapping, final String tableIdentifier) {
    final CaseStatement caseStatement = new CaseStatement();
    if (Objects.nonNull(businessMapping.getSharedCosts())) {
      for (final SharedCost sharedCost : businessMapping.getSharedCosts()) {
        caseStatement.addWhen(
            getConsolidatedRuleCondition(sharedCost.getRules(), tableIdentifier), sharedCost.getName());
      }
      caseStatement.addElse(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName());
    }
    return new CustomSql(caseStatement);
  }

  private CustomSql getClickHouseSQLCaseStatementForSharedCost(
      final BusinessMapping businessMapping, final String tableIdentifier) {
    final StringBuilder multiIfStatement = new StringBuilder();
    multiIfStatement.append(MULTI_IF_STATEMENT_OPENING);
    if (Objects.nonNull(businessMapping.getSharedCosts())) {
      for (final SharedCost sharedCost : businessMapping.getSharedCosts()) {
        multiIfStatement.append(getConsolidatedRuleCondition(sharedCost.getRules(), tableIdentifier))
            .append(',')
            .append(String.format("'%s'", sharedCost.getName()))
            .append(',');
      }
    }
    multiIfStatement.append(String.format("'%s'", ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName()))
        .append(')');
    return new CustomSql(multiIfStatement.toString());
  }

  public String getAliasFromField(QLCEViewFieldInput field) {
    switch (field.getIdentifier()) {
      case AWS:
      case GCP:
      case AZURE:
      case CLUSTER:
      case COMMON:
        return field.getFieldId();
      case LABEL:
        if (field.getFieldId().equals(LABEL_KEY.getFieldName())) {
          return LABEL_KEY.getAlias();
        } else {
          return ViewsMetaDataFields.LABEL_VALUE.getAlias();
        }
      case CUSTOM:
      case BUSINESS_MAPPING:
        return modifyStringToComplyRegex(field.getFieldName());
      default:
        throw new InvalidRequestException("Invalid View Field Identifier " + field.getIdentifier());
    }
  }

  public String modifyStringToComplyRegex(String value) {
    return value.toLowerCase().replaceAll("[^a-z0-9]", "_");
  }

  public String getColumnName(String value) {
    switch (value) {
      case GROUP_BY_NODE:
        return INSTANCE_ID;
      case GROUP_BY_ECS_SERVICE:
        return CLOUD_SERVICE_NAME;
      case GROUP_BY_ECS_LAUNCH_TYPE:
        return LAUNCH_TYPE;
      case GROUP_BY_ECS_TASK:
        return TASK_ID;
      default:
        return value;
    }
  }

  private boolean isClusterTable(String cloudProviderTableName) {
    return cloudProviderTableName.contains(CLUSTER_TABLE);
  }

  private boolean isApplicationQuery(List<QLCEViewGroupBy> groupByList) {
    return groupByList.stream()
        .filter(entry -> entry.getEntityGroupBy() != null)
        .anyMatch(entry -> applicationGroupBys.contains(entry.getEntityGroupBy().getFieldName()));
  }

  private boolean isInstanceQuery(List<QLCEViewGroupBy> groupByList) {
    return groupByList.stream()
        .filter(entry -> entry.getEntityGroupBy() != null)
        .anyMatch(entry -> entry.getEntityGroupBy().getFieldName().equals(GROUP_BY_INSTANCE_ID));
  }

  public String getAliasNameForAggregation(String value) {
    switch (value) {
      case EFFECTIVE_CPU_LIMIT:
        return TIME_AGGREGATED_CPU_LIMIT;
      case EFFECTIVE_CPU_REQUEST:
        return TIME_AGGREGATED_CPU_REQUEST;
      case EFFECTIVE_CPU_UTILIZATION_VALUE:
        return TIME_AGGREGATED_CPU_UTILIZATION_VALUE;
      case EFFECTIVE_MEMORY_LIMIT:
        return TIME_AGGREGATED_MEMORY_LIMIT;
      case EFFECTIVE_MEMORY_REQUEST:
        return TIME_AGGREGATED_MEMORY_REQUEST;
      case EFFECTIVE_MEMORY_UTILIZATION_VALUE:
        return TIME_AGGREGATED_MEMORY_UTILIZATION_VALUE;
      default:
        return value;
    }
  }

  // ----------------------------------------------------------------------------------------------------------------
  // Methods for query building for clickHouse and Big Query
  // ----------------------------------------------------------------------------------------------------------------
  private CustomCondition getSearchCondition(String fieldId, String searchString) {
    if (isClickHouseQuery()) {
      return new CustomCondition(
          String.format(searchFilterClickHouse, fieldId, getSearchStringForLikeOperator(searchString)));
    } else {
      return new CustomCondition(String.format(searchFilter, fieldId, searchString));
    }
  }

  private String getSearchStringForLikeOperator(String searchString) {
    if (searchString == null || searchString.equals("")) {
      return "'%'";
    } else {
      return "'%" + searchString + "%'";
    }
  }

  private boolean isClickHouseQuery() {
    return isClickHouseEnabled;
  }

  public String getTableIdentifier(String cloudProviderTableName) {
    StringTokenizer tokenizer = new StringTokenizer(cloudProviderTableName, ".");
    String tableIdentifier = "";
    while (tokenizer.hasMoreTokens()) {
      tableIdentifier = tokenizer.nextToken();
    }
    return tableIdentifier;
  }

  public String getColumnNameForField(String tableIdentifier, String field) {
    String key = String.format(ViewFieldUtils.COLUMN_MAPPING_KEY, tableIdentifier, field.toLowerCase(Locale.ROOT));
    if (ViewFieldUtils.getClickHouseColumnMapping().containsKey(key)) {
      return ViewFieldUtils.getClickHouseColumnMapping().get(key);
    }
    return field;
  }
}
