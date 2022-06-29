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
import static io.harness.ccm.views.graphql.QLCEViewAggregateOperation.SUM;
import static io.harness.ccm.views.graphql.QLCEViewTimeGroupType.DAY;
import static io.harness.ccm.views.graphql.ViewsMetaDataFields.LABEL_KEY;
import static io.harness.ccm.views.graphql.ViewsMetaDataFields.LABEL_KEY_UN_NESTED;
import static io.harness.ccm.views.graphql.ViewsMetaDataFields.LABEL_VALUE_UN_NESTED;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLOUD_SERVICE_NAME;
import static io.harness.ccm.views.utils.ClusterTableKeys.CLUSTER_TABLE;
import static io.harness.ccm.views.utils.ClusterTableKeys.COUNT;
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
import static io.harness.timescaledb.Tables.ANOMALIES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.businessMapping.entities.SharedCost;
import io.harness.ccm.views.businessMapping.entities.UnallocatedCostStrategy;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.dao.ViewCustomFieldDao;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewCustomField;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.entities.ViewTimeGranularity;
import io.harness.ccm.views.utils.ViewFieldUtils;
import io.harness.exception.InvalidRequestException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CaseStatement;
import com.healthmarketscience.sqlbuilder.ComboCondition;
import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.Converter;
import com.healthmarketscience.sqlbuilder.CustomCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UnaryCondition;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgLimitClause;
import com.healthmarketscience.sqlbuilder.custom.postgresql.PgOffsetClause;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(CE)
public class ViewsQueryBuilder {
  @Inject ViewCustomFieldDao viewCustomFieldDao;
  @Inject BusinessMappingService businessMappingService;

  public static final String K8S_NODE = "K8S_NODE";
  public static final String K8S_POD = "K8S_POD";
  public static final String K8S_POD_FARGATE = "K8S_POD_FARGATE";
  public static final String K8S_PV = "K8S_PV";
  public static final String ECS_TASK_FARGATE = "ECS_TASK_FARGATE";
  public static final String ECS_TASK_EC2 = "ECS_TASK_EC2";
  public static final String ECS_CONTAINER_INSTANCE = "ECS_CONTAINER_INSTANCE";
  private static final String distinct = " DISTINCT(%s)";
  private static final String count = "COUNT(*)";
  private static final String aliasStartTimeMaxMin = "%s_%s";
  private static final String searchFilter = "REGEXP_CONTAINS( LOWER(%s), LOWER('%s') )";
  private static final String regexFilter = "REGEXP_CONTAINS( %s, r'%s' )";
  private static final String labelsSubQuery = "(SELECT value FROM UNNEST(labels) WHERE KEY='%s')";
  private static final String leftJoinLabels = " LEFT JOIN UNNEST(labels) as labelsUnnested";
  private static final String leftJoinSelectiveLabels =
      " LEFT JOIN UNNEST(labels) as labelsUnnested ON labelsUnnested.key IN (%s)";
  private static final ImmutableSet<String> podInfoImmutableSet =
      ImmutableSet.of("namespace", "workloadName", "appId", "envId", "serviceId", "parentInstanceId");
  private static final ImmutableSet<String> clusterFilterImmutableSet =
      ImmutableSet.of("product", "region", "PROVIDERS");
  private static final ImmutableList<String> applicationGroupBys =
      ImmutableList.of(GROUP_BY_APPLICATION, GROUP_BY_SERVICE, GROUP_BY_ENVIRONMENT);
  private static final String CLOUD_PROVIDERS_CUSTOM_GROUPING = "PROVIDERS";
  private static final String CLOUD_PROVIDERS_CUSTOM_GROUPING_QUERY =
      "CASE WHEN cloudProvider = 'CLUSTER' THEN 'CLUSTER' ELSE 'CLOUD' END";

  public SelectQuery getQuery(List<ViewRule> rules, List<QLCEViewFilter> filters, List<QLCEViewTimeFilter> timeFilters,
      List<QLCEViewGroupBy> groupByList, List<QLCEViewAggregation> aggregations,
      List<QLCEViewSortCriteria> sortCriteriaList, String cloudProviderTableName) {
    return getQuery(
        rules, filters, timeFilters, groupByList, aggregations, sortCriteriaList, cloudProviderTableName, 0);
  }

  public SelectQuery getQuery(List<ViewRule> rules, List<QLCEViewFilter> filters, List<QLCEViewTimeFilter> timeFilters,
      List<QLCEViewGroupBy> groupByList, List<QLCEViewAggregation> aggregations,
      List<QLCEViewSortCriteria> sortCriteriaList, String cloudProviderTableName, int timeOffsetInDays) {
    SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(cloudProviderTableName);
    List<QLCEViewFieldInput> groupByEntity = getGroupByEntity(groupByList);
    QLCEViewTimeTruncGroupBy groupByTime = getGroupByTime(groupByList);
    boolean isClusterTable = isClusterTable(cloudProviderTableName);

    List<ViewField> customFields =
        collectFieldListByIdentifier(rules, filters, groupByEntity, ViewFieldIdentifier.CUSTOM);

    List<ViewField> businessMapping = collectFieldListByIdentifier(rules, filters, groupByEntity, BUSINESS_MAPPING);
    if ((!isApplicationQuery(groupByList) || !isClusterTable) && !isInstanceQuery(groupByList)) {
      modifyQueryWithInstanceTypeFilter(rules, filters, groupByEntity, customFields, businessMapping, selectQuery);
    }

    if (!rules.isEmpty()) {
      selectQuery.addCondition(getConsolidatedRuleCondition(rules));
    }

    if (!filters.isEmpty()) {
      decorateQueryWithFilters(selectQuery, filters);
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(selectQuery, timeFilters, isClusterTable);
    }

    if (!groupByEntity.isEmpty()) {
      for (QLCEViewFieldInput groupBy : groupByEntity) {
        Object sqlObjectFromField = getSQLObjectFromField(groupBy);
        if (groupBy.getIdentifier() != ViewFieldIdentifier.CUSTOM && groupBy.getIdentifier() != BUSINESS_MAPPING
            && groupBy.getIdentifier() != ViewFieldIdentifier.LABEL) {
          selectQuery.addCustomColumns(sqlObjectFromField);
          selectQuery.addCustomGroupings(sqlObjectFromField);
        } else if (groupBy.getIdentifier() == ViewFieldIdentifier.LABEL) {
          String labelSubQuery = String.format(labelsSubQuery, groupBy.getFieldName());
          selectQuery.addCustomGroupings(ViewsMetaDataFields.LABEL_VALUE.getAlias());
          selectQuery.addCustomColumns(
              Converter.toCustomColumnSqlObject(labelSubQuery, ViewsMetaDataFields.LABEL_VALUE.getAlias()));
        } else {
          // Will handle both Custom and Business Mapping Cases
          selectQuery.addAliasedColumn(
              sqlObjectFromField, modifyStringToComplyRegex(getColumnName(groupBy.getFieldName())));
          selectQuery.addCustomGroupings(modifyStringToComplyRegex(groupBy.getFieldName()));
        }
      }
    }

    if (groupByTime != null) {
      if (timeOffsetInDays == 0) {
        decorateQueryWithGroupByTime(selectQuery, groupByTime, isClusterTable);
      } else {
        decorateQueryWithGroupByTimeWithOffset(selectQuery, groupByTime, isClusterTable, timeOffsetInDays);
      }
    }

    if (!aggregations.isEmpty()) {
      // TODO: Add Shared Cost Aggregations
      decorateQueryWithAggregations(selectQuery, aggregations);
      decorateQueryWithSharedCostAggregations(selectQuery, groupByEntity);
    }

    if (!sortCriteriaList.isEmpty()) {
      decorateQueryWithSortCriteria(selectQuery, sortCriteriaList);
    }

    log.info("Query for view {}", selectQuery.toString());
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

    log.info("Information schema query for table {}", selectQuery.toString());
    return selectQuery;
  }

  public SelectQuery getCostByProvidersOverviewQuery(List<QLCEViewTimeFilter> timeFilters,
      List<QLCEViewGroupBy> groupByList, List<QLCEViewAggregation> aggregations, String cloudProviderTableName) {
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
      decorateQueryWithAggregations(selectQuery, aggregations);
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(selectQuery, timeFilters, false);
    }

    if (groupByTime != null) {
      decorateQueryWithGroupByTime(selectQuery, groupByTime, false);
    }

    log.info("Query for Overview cost by providers {}", selectQuery.toString());
    return selectQuery;
  }

  public SelectQuery getTotalCountQuery(List<ViewRule> rules, List<QLCEViewFilter> filters,
      List<QLCEViewTimeFilter> timeFilters, List<QLCEViewGroupBy> groupByList, String cloudProviderTableName) {
    SelectQuery selectQueryInner = new SelectQuery();
    SelectQuery selectQueryOuter = new SelectQuery();
    selectQueryInner.addCustomFromTable(cloudProviderTableName);
    List<QLCEViewFieldInput> groupByEntity = getGroupByEntity(groupByList);
    QLCEViewTimeTruncGroupBy groupByTime = getGroupByTime(groupByList);
    boolean isClusterTable = isClusterTable(cloudProviderTableName);

    selectQueryInner.addCustomColumns(Converter.toCustomColumnSqlObject(count, COUNT_INNER));

    List<ViewField> customFields =
        collectFieldListByIdentifier(rules, filters, groupByEntity, ViewFieldIdentifier.CUSTOM);
    List<ViewField> businessMapping = collectFieldListByIdentifier(rules, filters, groupByEntity, BUSINESS_MAPPING);
    if ((!isApplicationQuery(groupByList) || !isClusterTable) && !isInstanceQuery(groupByList)) {
      modifyQueryWithInstanceTypeFilter(rules, filters, groupByEntity, customFields, businessMapping, selectQueryInner);
    }

    if (!rules.isEmpty()) {
      selectQueryInner.addCondition(getConsolidatedRuleCondition(rules));
    }

    if (!filters.isEmpty()) {
      decorateQueryWithFilters(selectQueryInner, filters);
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(selectQueryInner, timeFilters, isClusterTable);
    }

    if (!groupByEntity.isEmpty()) {
      for (QLCEViewFieldInput groupBy : groupByEntity) {
        Object sqlObjectFromField = getSQLObjectFromField(groupBy);
        if (groupBy.getIdentifier() != ViewFieldIdentifier.CUSTOM
            && groupBy.getIdentifier() != ViewFieldIdentifier.LABEL) {
          selectQueryInner.addCustomGroupings(sqlObjectFromField);
        } else if (groupBy.getIdentifier() == ViewFieldIdentifier.LABEL) {
          String labelSubQuery = String.format(labelsSubQuery, groupBy.getFieldName());
          selectQueryInner.addCustomGroupings(ViewsMetaDataFields.LABEL_VALUE.getAlias());
          selectQueryInner.addCustomColumns(
              Converter.toCustomColumnSqlObject(labelSubQuery, ViewsMetaDataFields.LABEL_VALUE.getAlias()));
        } else {
          selectQueryInner.addAliasedColumn(
              sqlObjectFromField, modifyStringToComplyRegex(getColumnName(groupBy.getFieldName())));
          selectQueryInner.addCustomGroupings(modifyStringToComplyRegex(groupBy.getFieldName()));
        }
      }
    }

    if (groupByTime != null) {
      decorateQueryWithGroupByTime(selectQueryInner, groupByTime, isClusterTable);
    }

    selectQueryOuter.addCustomFromTable("(" + selectQueryInner.toString() + ")");
    selectQueryOuter.addCustomColumns(Converter.toCustomColumnSqlObject(count, COUNT));

    log.info("Total count query for view {}", selectQueryOuter.toString());
    return selectQueryOuter;
  }

  public String getAnomalyQuery(
      List<ViewRule> rules, List<QLCEViewFilter> filters, List<QLCEViewTimeFilter> timeFilters) {
    SelectQuery selectQuery = new SelectQuery();
    selectQuery.addCustomFromTable(ANOMALIES.getName());
    selectQuery.addAllColumns();

    if (!rules.isEmpty()) {
      selectQuery.addCondition(getConsolidatedRuleCondition(rules));
    }

    if (!filters.isEmpty()) {
      decorateQueryWithFilters(selectQuery, filters);
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(selectQuery, timeFilters, false);
    }

    return selectQuery.toString();
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
      conditionList.add(UnaryCondition.isNull(new CustomSql(ViewsMetaDataFields.INSTANCE_TYPE.getFieldName())));
      conditionList.add(new InCondition(
          new CustomSql(ViewsMetaDataFields.INSTANCE_TYPE.getFieldName()), (Object[]) instancetypeStringArray));
      selectQuery.addCondition(getSqlOrCondition(conditionList));
    }
  }

  public ViewsQueryMetadata getFilterValuesQuery(List<ViewRule> rules, List<QLCEViewFilter> filters,
      List<QLCEViewTimeFilter> timeFilters, String cloudProviderTableName, Integer limit, Integer offset) {
    List<QLCEViewFieldInput> fields = new ArrayList<>();
    SelectQuery query = new SelectQuery();
    query.addCustomization(new PgLimitClause(limit));
    query.addCustomization(new PgOffsetClause(offset));
    query.addCustomFromTable(cloudProviderTableName);

    boolean isClusterTable = isClusterTable(cloudProviderTableName);

    boolean isLabelsPresent = false;
    List<ViewField> customFields =
        collectFieldListByIdentifier(rules, filters, Collections.EMPTY_LIST, ViewFieldIdentifier.CUSTOM);
    List<ViewField> businessMappings =
        collectFieldListByIdentifier(rules, filters, Collections.EMPTY_LIST, BUSINESS_MAPPING);
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
    if (isLabelsPresent || evaluateLabelsPresent(rules, filters)) {
      decorateQueryWithLabelsMetadata(query, true, labelKeysList, getIsLabelsKeyFilterQuery(filters));
    }

    if (!rules.isEmpty()) {
      query.addCondition(getConsolidatedRuleCondition(rules));
    }

    if (!timeFilters.isEmpty()) {
      decorateQueryWithTimeFilters(query, timeFilters, isClusterTable);
    }

    for (QLCEViewFilter filter : filters) {
      QLCEViewFieldInput viewFieldInput = filter.getField();
      String searchString = "";
      if (filter.getValues().length != 0) {
        searchString = filter.getValues()[0];
      }
      switch (viewFieldInput.getIdentifier()) {
        case AWS:
        case GCP:
        case AZURE:
        case CLUSTER:
        case COMMON:
          query.addAliasedColumn(
              new CustomSql(String.format(distinct, viewFieldInput.getFieldId())), viewFieldInput.getFieldId());
          if (AWS_ACCOUNT_FIELD.equals(viewFieldInput.getFieldName()) && filter.getValues().length != 1) {
            // Skipping the first string for InCondition that client is passing in the search filter
            // Considering only the AWS account Ids
            query.addCondition(ComboCondition.or(new InCondition(new CustomSql(viewFieldInput.getFieldId()),
                                                     Arrays.stream(filter.getValues()).skip(1).toArray(Object[] ::new)),
                new CustomCondition(String.format(searchFilter, viewFieldInput.getFieldId(), searchString))));
          } else {
            query.addCondition(
                new CustomCondition(String.format(searchFilter, viewFieldInput.getFieldId(), searchString)));
          }
          break;
        case LABEL:
          if (viewFieldInput.getFieldId().equals(LABEL_KEY.getFieldName())) {
            query.addCustomGroupings(LABEL_KEY_UN_NESTED.getAlias());
            query.addAliasedColumn(new CustomSql(String.format(distinct, LABEL_KEY_UN_NESTED.getFieldName())),
                LABEL_KEY_UN_NESTED.getAlias());
            query.addCondition(
                new CustomCondition(String.format(searchFilter, LABEL_KEY_UN_NESTED.getFieldName(), searchString)));
          } else {
            query.addCustomGroupings(LABEL_VALUE_UN_NESTED.getAlias());
            query.addCondition(getCondition(getLabelKeyFilter(new String[] {viewFieldInput.getFieldName()})));
            query.addAliasedColumn(new CustomSql(String.format(distinct, LABEL_VALUE_UN_NESTED.getFieldName())),
                LABEL_VALUE_UN_NESTED.getAlias());
            query.addCondition(
                new CustomCondition(String.format(searchFilter, LABEL_VALUE_UN_NESTED.getFieldName(), searchString)));
          }
          break;
        case CUSTOM:
          query = new SelectQuery();
          query.addCustomization(new PgLimitClause(limit));
          query.addCustomization(new PgOffsetClause(offset));
          query.addCustomFromTable(cloudProviderTableName);
          if (!customFields.isEmpty()) {
            modifyQueryForCustomFields(query, customFields);
          }
          ViewCustomField customField = viewCustomFieldDao.getById(viewFieldInput.getFieldId());
          query.addAliasedColumn(new CustomSql(String.format(distinct, customField.getSqlFormula())),
              modifyStringToComplyRegex(customField.getName()));
          query.addCondition(
              new CustomCondition(String.format(searchFilter, customField.getSqlFormula(), searchString)));
          break;
        case BUSINESS_MAPPING:
          query = new SelectQuery();
          query.addCustomization(new PgLimitClause(limit));
          query.addCustomization(new PgOffsetClause(offset));
          query.addCustomFromTable(cloudProviderTableName);
          if (!businessMappings.isEmpty()) {
            modifyQueryForBusinessMapping(query, businessMappings, false);
          }
          BusinessMapping businessMapping = businessMappingService.get(viewFieldInput.getFieldId());
          query.addAliasedColumn(
              new CustomSql(String.format(distinct,
                  getSQLCaseStatementBusinessMapping(businessMappingService.get(viewFieldInput.getFieldId())))),
              modifyStringToComplyRegex(businessMapping.getName()));
          query.addCondition(new CustomCondition(String.format(searchFilter,
              getSQLCaseStatementBusinessMapping(businessMappingService.get(viewFieldInput.getFieldId())),
              searchString)));
          break;
        default:
          throw new InvalidRequestException("Invalid View Field Identifier " + viewFieldInput.getIdentifier());
      }
      fields.add(filter.getField());
    }
    log.info("Query for view filter {}", query.toString());

    return ViewsQueryMetadata.builder().query(query).fields(fields).build();
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
        selectQuery.addCustomJoin(leftJoinLabels);
      } else {
        selectQuery.addCustomJoin(String.format(leftJoinSelectiveLabels, processLabelKeyList(labelKeyList)));
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

  private void decorateQueryWithAggregations(SelectQuery selectQuery, List<QLCEViewAggregation> aggregations) {
    for (QLCEViewAggregation aggregation : aggregations) {
      decorateQueryWithAggregation(selectQuery, aggregation);
    }
  }

  private void decorateQueryWithAggregation(SelectQuery selectQuery, QLCEViewAggregation aggregation) {
    FunctionCall functionCall = getFunctionCallType(aggregation.getOperationType());
    if (aggregation.getColumnName().equals(ViewsMetaDataFields.START_TIME.getFieldName())) {
      selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
          functionCall.addCustomParams(new CustomSql(ViewsMetaDataFields.START_TIME.getFieldName())),
          String.format(
              aliasStartTimeMaxMin, ViewsMetaDataFields.START_TIME.getFieldName(), aggregation.getOperationType())));
    } else {
      selectQuery.addCustomColumns(
          Converter.toCustomColumnSqlObject(functionCall.addCustomParams(new CustomSql(aggregation.getColumnName())),
              getAliasNameForAggregation(aggregation.getColumnName())));
    }
  }

  private void decorateQueryWithSharedCostAggregations(
      SelectQuery selectQuery, List<QLCEViewFieldInput> groupByEntity) {
    List<QLCEViewFieldInput> groupByBusinessMapping =
        groupByEntity.stream()
            .filter(groupBy -> groupBy.getIdentifier() == BUSINESS_MAPPING)
            .collect(Collectors.toList());

    if (groupByBusinessMapping.size() > 1) {
      throw new InvalidRequestException("Invalid request: Cannot group by multiple cost categories.");
    } else if (groupByBusinessMapping.size() == 1) {
      BusinessMapping businessMapping = businessMappingService.get(groupByBusinessMapping.get(0).getFieldId());
      List<SharedCost> sharedCosts = businessMapping.getSharedCosts();
      if (sharedCosts != null) {
        sharedCosts.forEach(sharedCost -> decorateQueryWithSharedCostAggregation(selectQuery, sharedCost));
      }
    }
  }

  private void decorateQueryWithSharedCostAggregation(SelectQuery selectQuery, SharedCost sharedCost) {
    FunctionCall functionCall = getFunctionCallType(SUM);
    selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
        new CoalesceExpression(
            functionCall.addCustomParams(getSQLCaseStatementBusinessMappingSharedCost(sharedCost.getRules())),
            Collections.singletonList(0)),
        modifyStringToComplyRegex(sharedCost.getName())));
  }

  private CustomSql getSQLCaseStatementBusinessMappingSharedCost(List<ViewRule> sharedCostRules) {
    CaseStatement caseStatement = new CaseStatement();
    caseStatement.addWhen(
        getConsolidatedRuleCondition(sharedCostRules), new CustomSql(ViewsMetaDataFields.COST.getAlias()));
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

  private void decorateQueryWithGroupByTime(
      SelectQuery selectQuery, QLCEViewTimeTruncGroupBy groupByTime, boolean isTimeInEpochMillis) {
    if (isTimeInEpochMillis) {
      selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
          new TimeTruncatedExpression(
              new TimestampMillisExpression(new CustomSql(ViewsMetaDataFields.START_TIME.getFieldName())),
              groupByTime.getResolution()),
          ViewsMetaDataFields.TIME_GRANULARITY.getFieldName()));
    } else {
      selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
          new TimeTruncatedExpression(
              new CustomSql(ViewsMetaDataFields.START_TIME.getFieldName()), groupByTime.getResolution()),
          ViewsMetaDataFields.TIME_GRANULARITY.getFieldName()));
    }

    selectQuery.addCustomGroupings(ViewsMetaDataFields.TIME_GRANULARITY.getFieldName());
    selectQuery.addCustomOrdering(ViewsMetaDataFields.TIME_GRANULARITY.getFieldName(), OrderObject.Dir.ASCENDING);
  }

  private void decorateQueryWithGroupByTimeWithOffset(SelectQuery selectQuery, QLCEViewTimeTruncGroupBy groupByTime,
      boolean isTimeInEpochMillis, int timeOffsetInDays) {
    if (isTimeInEpochMillis) {
      selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
          new TimeTruncatedExpression(new TimestampDiffExpression(new TimestampMillisExpression(new CustomSql(
                                                                      ViewsMetaDataFields.START_TIME.getFieldName())),
                                          timeOffsetInDays, DAY),
              groupByTime.getResolution()),
          ViewsMetaDataFields.TIME_GRANULARITY.getFieldName()));
    } else {
      selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
          new TimeTruncatedExpression(
              new TimestampDiffExpression(
                  new CustomSql(ViewsMetaDataFields.START_TIME.getFieldName()), timeOffsetInDays, DAY),
              groupByTime.getResolution()),
          ViewsMetaDataFields.TIME_GRANULARITY.getFieldName()));
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

  private Condition getConsolidatedRuleCondition(List<ViewRule> rules) {
    List<Condition> conditionList = new ArrayList<>();
    for (ViewRule rule : rules) {
      conditionList.add(getPerRuleCondition(rule));
    }
    return getSqlOrCondition(conditionList);
  }

  private Condition getPerRuleCondition(ViewRule rule) {
    List<Condition> conditionList = new ArrayList<>();
    for (ViewCondition condition : rule.getViewConditions()) {
      conditionList.add(getCondition(mapConditionToFilter((ViewIdCondition) condition)));
    }
    return getSqlAndCondition(conditionList);
  }

  // Used in PerspectiveToAnomalyQueryHelper
  public QLCEViewFilter mapConditionToFilter(ViewIdCondition condition) {
    return QLCEViewFilter.builder()
        .field(getViewFieldInput(condition.getViewField()))
        .operator(mapViewIdOperatorToQLCEViewFilterOperator(condition.getViewOperator()))
        .values(getStringArray(condition.getValues()))
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

  public QLCEViewTimeGroupType mapViewTimeGranularityToQLCEViewTimeGroupType(ViewTimeGranularity timeGranularity) {
    if (timeGranularity.equals(ViewTimeGranularity.DAY)) {
      return DAY;
    } else if (timeGranularity.equals(ViewTimeGranularity.MONTH)) {
      return QLCEViewTimeGroupType.MONTH;
    }
    return null;
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

  private void decorateQueryWithFilters(SelectQuery selectQuery, List<QLCEViewFilter> filters) {
    for (QLCEViewFilter filter : filters) {
      selectQuery.addCondition(getCondition(filter));
    }
  }

  private void decorateQueryWithTimeFilters(
      SelectQuery selectQuery, List<QLCEViewTimeFilter> timeFilters, boolean isClusterTable) {
    for (QLCEViewTimeFilter timeFilter : timeFilters) {
      selectQuery.addCondition(getCondition(timeFilter, isClusterTable));
    }
  }

  private Condition getCondition(QLCEViewFilter filter) {
    Object conditionKey = getSQLObjectFromField(filter.getField());
    if (conditionKey.toString().equals(ViewsMetaDataFields.LABEL_VALUE.getFieldName())) {
      String labelKey = filter.getField().getFieldName();
      String labelSubQuery = String.format(labelsSubQuery, labelKey);
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

    switch (operator) {
      case EQUALS:
        return BinaryCondition.equalTo(conditionKey, filter.getValues()[0]);
      case IN:
        return new InCondition(conditionKey, (Object[]) filter.getValues());
      case NOT_IN:
        return new InCondition(conditionKey, (Object[]) filter.getValues()).setNegate(true);
      case NOT_NULL:
        return UnaryCondition.isNotNull(conditionKey);
      case NULL:
        return UnaryCondition.isNull(conditionKey);
      case LIKE:
        return new CustomCondition(String.format(regexFilter, conditionKey, filter.getValues()[0]));
      default:
        throw new InvalidRequestException("Invalid View Filter operator: " + operator);
    }
  }

  // Change it back
  private Condition getCondition(QLCEViewTimeFilter timeFilter, boolean addLongValueConditions) {
    Object conditionKey = getSQLObjectFromField(timeFilter.getField());
    QLCEViewTimeFilterOperator operator = timeFilter.getOperator();

    switch (operator) {
      case BEFORE:
        return addLongValueConditions
            ? BinaryCondition.lessThanOrEq(conditionKey, timeFilter.getValue().longValue())
            : BinaryCondition.lessThanOrEq(conditionKey, Instant.ofEpochMilli(timeFilter.getValue().longValue()));
      case AFTER:
        return addLongValueConditions
            ? BinaryCondition.greaterThanOrEq(conditionKey, timeFilter.getValue().longValue())
            : BinaryCondition.greaterThanOrEq(conditionKey, Instant.ofEpochMilli(timeFilter.getValue().longValue()));
      default:
        throw new InvalidRequestException("Invalid View TimeFilter operator: " + operator);
    }
  }

  private Object getSQLObjectFromField(QLCEViewFieldInput field) {
    switch (field.getIdentifier()) {
      case AWS:
      case GCP:
      case AZURE:
      case CLUSTER:
      case COMMON:
      case LABEL:
        return new CustomSql(field.getFieldId());
      case BUSINESS_MAPPING:
        return getSQLCaseStatementBusinessMapping(businessMappingService.get(field.getFieldId()));
      case CUSTOM:
        return new CustomSql(viewCustomFieldDao.getById(field.getFieldId()).getSqlFormula());
      default:
        throw new InvalidRequestException("Invalid View Field Identifier " + field.getIdentifier());
    }
  }

  private CustomSql getSQLCaseStatementBusinessMapping(BusinessMapping businessMapping) {
    CaseStatement caseStatement = new CaseStatement();
    for (CostTarget costTarget : businessMapping.getCostTargets()) {
      caseStatement.addWhen(getConsolidatedRuleCondition(costTarget.getRules()), costTarget.getName());
    }
    if (Objects.nonNull(businessMapping.getUnallocatedCost())
        && businessMapping.getUnallocatedCost().getStrategy() == UnallocatedCostStrategy.DISPLAY_NAME) {
      caseStatement.addElse(businessMapping.getUnallocatedCost().getLabel());
    } else {
      caseStatement.addElse(ViewFieldUtils.getBusinessMappingUnallocatedCostDefaultName());
    }
    return new CustomSql(caseStatement);
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

  private String getColumnName(String value) {
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

  private String getAliasNameForAggregation(String value) {
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
}
