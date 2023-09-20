/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.commons.dao.CEMetadataRecordDao;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.EntityType;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata.BillingDataMetaDataFields;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata.BillingDataQueryMetadataBuilder;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata.ResultType;
import software.wings.graphql.datafetcher.ce.activePods.CeActivePodCountQueryMetadata;
import software.wings.graphql.datafetcher.ce.activePods.CeActivePodCountQueryMetadata.CeActivePodCountMetaDataFields;
import software.wings.graphql.datafetcher.ce.activePods.CeActivePodCountQueryMetadata.CeActivePodCountQueryMetadataBuilder;
import software.wings.graphql.datafetcher.ce.activePods.CeActivePodCountTableSchema;
import software.wings.graphql.datafetcher.k8sLabel.K8sLabelHelper;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.Filter;
import software.wings.graphql.schema.type.aggregation.QLAggregationKind;
import software.wings.graphql.schema.type.aggregation.QLFilterKind;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNumberFilter;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilterType;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataLabelAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataLabelFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataTagAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataTagFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataTagType;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortCriteria;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingSortType;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMTimeSeriesAggregation;
import software.wings.graphql.schema.type.aggregation.billing.QLCEEnvironmentTypeFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLTimeGroupType;
import software.wings.service.impl.EnvironmentServiceImpl;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.healthmarketscience.sqlbuilder.AliasedObject;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.Converter;
import com.healthmarketscience.sqlbuilder.CustomExpression;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.OrderObject.Dir;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SqlObject;
import com.healthmarketscience.sqlbuilder.UnaryCondition;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import io.fabric8.utils.Lists;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BillingDataQueryBuilder {
  private BillingDataTableSchema schema = new BillingDataTableSchema();
  private CeActivePodCountTableSchema podTableSchema = new CeActivePodCountTableSchema();
  private static final String STANDARD_TIME_ZONE = "GMT";
  private static final String DEFAULT_ENVIRONMENT_TYPE = "ALL";
  public static final String BILLING_DATA_HOURLY_TABLE = "billing_data_hourly t0";
  public static final String BILLING_DATA_PRE_AGGREGATED_TABLE = "billing_data_aggregated t0";
  public static final String BILLING_DATA_HOURLY_PRE_AGGREGATED_TABLE = "billing_data_hourly_aggregated t0";
  private static final long ONE_DAY_MILLIS = 86400000;
  private static final String EMPTY = "";
  protected static final String INVALID_FILTER_MSG = "Invalid combination of group by and filters";
  private static final String UNALLOCATED = "Unallocated";
  @Inject TagHelper tagHelper;
  @Inject K8sLabelHelper k8sLabelHelper;
  @Inject EnvironmentServiceImpl environmentService;
  @Inject FeatureFlagService featureFlagService;
  @Inject CEMetadataRecordDao ceMetadataRecordDao;

  protected BillingDataQueryMetadata formQuery(String accountId, List<QLBillingDataFilter> filters,
      List<QLCCMAggregationFunction> aggregateFunction, List<QLCCMEntityGroupBy> groupBy,
      QLCCMTimeSeriesAggregation groupByTime, List<QLBillingSortCriteria> sortCriteria, boolean addInstanceTypeFilter) {
    return formQuery(
        accountId, filters, aggregateFunction, groupBy, groupByTime, sortCriteria, addInstanceTypeFilter, false);
  }

  protected BillingDataQueryMetadata formQuery(String accountId, List<QLBillingDataFilter> filters,
      List<QLCCMAggregationFunction> aggregateFunction, List<QLCCMEntityGroupBy> groupBy,
      QLCCMTimeSeriesAggregation groupByTime, List<QLBillingSortCriteria> sortCriteria, boolean addInstanceTypeFilter,
      boolean isCostTrendQuery) {
    return formQuery(accountId, filters, aggregateFunction, groupBy, groupByTime, sortCriteria, addInstanceTypeFilter,
        isCostTrendQuery, false);
  }

  protected BillingDataQueryMetadata formQuery(String accountId, List<QLBillingDataFilter> filters,
      List<QLCCMAggregationFunction> aggregateFunction, List<QLCCMEntityGroupBy> groupBy,
      QLCCMTimeSeriesAggregation groupByTime, List<QLBillingSortCriteria> sortCriteria, boolean addInstanceTypeFilter,
      boolean isCostTrendQuery, boolean isEfficiencyStatsQuery) {
    BillingDataQueryMetadataBuilder queryMetaDataBuilder = BillingDataQueryMetadata.builder();
    SelectQuery selectQuery = new SelectQuery();

    ResultType resultType;
    resultType = ResultType.STACKED_TIME_SERIES;

    queryMetaDataBuilder.resultType(resultType);
    List<BillingDataMetaDataFields> fieldNames = new ArrayList<>();
    List<BillingDataMetaDataFields> groupByFields = new ArrayList<>();

    if (addInstanceTypeFilter && !checkForAdditionalFilterInClusterDrillDown(filters)
        && ((isGroupByClusterPresent(groupBy) && !isClusterDrilldown(groupBy))
            || isNoneGroupBySelectedWithoutFilterInClusterView(groupBy, filters) || isEfficiencyStatsQuery)) {
      addInstanceTypeFilter(filters);
    }

    // To handle the cases of same workloadNames across different namespaces
    if (isGroupByEntityPresent(groupBy, QLCCMEntityGroupBy.WorkloadName)
        && !isGroupByEntityPresent(groupBy, QLCCMEntityGroupBy.Namespace)) {
      groupBy.add(0, QLCCMEntityGroupBy.Namespace);
    } else if (isGroupByEntityPresent(groupBy, QLCCMEntityGroupBy.Namespace)) {
      filters.add(
          QLBillingDataFilter.builder()
              .instanceType(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(new String[] {"K8S_PV"}).build())
              .build());
    }
    // Reorder group by entity list to first group by cluster/application
    groupBy = getGroupByOrderedByDrillDown(groupBy);

    if (!isGroupByHour(groupByTime) && !shouldUseHourlyData(filters, accountId)) {
      if (isValidGroupByForPreAggregation(groupBy) && areFiltersValidForPreAggregation(filters)
          && areAggregationsValidForPreAggregation(aggregateFunction)) {
        selectQuery.addCustomFromTable(BILLING_DATA_PRE_AGGREGATED_TABLE);
        aggregateFunction = getSupportedAggregations(aggregateFunction);
      } else {
        selectQuery.addCustomFromTable(schema.getBillingDataTable());
      }
    } else {
      if (isValidGroupByForPreAggregation(groupBy) && areFiltersValidForPreAggregation(filters)
          && areAggregationsValidForPreAggregation(aggregateFunction)) {
        selectQuery.addCustomFromTable(BILLING_DATA_HOURLY_PRE_AGGREGATED_TABLE);
        aggregateFunction = getSupportedAggregations(aggregateFunction);
      } else {
        selectQuery.addCustomFromTable(BILLING_DATA_HOURLY_TABLE);
      }
    }

    decorateQueryWithAggregations(selectQuery, aggregateFunction, fieldNames);
    if (isCostTrendQuery) {
      decorateQueryWithMinMaxStartTime(selectQuery, fieldNames);
    }

    if (isValidGroupByTime(groupByTime)) {
      decorateQueryWithGroupByTime(fieldNames, selectQuery, groupByTime, groupByFields);
    }

    if (isValidGroupBy(groupBy)) {
      decorateQueryWithGroupBy(fieldNames, selectQuery, groupBy, groupByFields);
      decorateQueryWithNodeOrPodGroupBy(fieldNames, selectQuery, groupBy, groupByFields, filters);
    }

    // To change node instance id filter to parent instance id filter in case of group by namespace/workload
    filters = getUpdatedInstanceIdFilter(filters, groupBy);

    if (isGroupByEntityPresent(groupBy, QLCCMEntityGroupBy.WorkloadName) && !isWorkloadTypeFilterPresent(filters)) {
      addWorkloadTypeFilter(filters);
    }

    if (!Lists.isNullOrEmpty(filters)) {
      filters = processFilterForTagsAndLabels(accountId, filters);
      decorateQueryWithFilters(selectQuery, filters);
    }

    List<QLBillingSortCriteria> finalSortCriteria = validateAndAddSortCriteria(selectQuery, sortCriteria, fieldNames);
    addAccountFilter(selectQuery, accountId);
    selectQuery.getWhereClause().setDisableParens(true);
    queryMetaDataBuilder.fieldNames(fieldNames);
    queryMetaDataBuilder.query(selectQuery.toString());
    queryMetaDataBuilder.groupByFields(groupByFields);
    queryMetaDataBuilder.sortCriteria(finalSortCriteria);
    queryMetaDataBuilder.filters(filters);
    return queryMetaDataBuilder.build();
  }

  protected BillingDataQueryMetadata formTrendStatsQuery(
      String accountId, List<QLCCMAggregationFunction> aggregateFunction, List<QLBillingDataFilter> filters) {
    BillingDataQueryMetadataBuilder queryMetaDataBuilder = BillingDataQueryMetadata.builder();
    SelectQuery selectQuery = new SelectQuery();

    List<BillingDataMetaDataFields> fieldNames = new ArrayList<>();

    if (!shouldUseHourlyData(filters, accountId)) {
      if (areFiltersValidForPreAggregation(filters)) {
        selectQuery.addCustomFromTable(BILLING_DATA_PRE_AGGREGATED_TABLE);
        aggregateFunction = getSupportedAggregations(aggregateFunction);
      } else {
        selectQuery.addCustomFromTable(schema.getBillingDataTable());
      }
    } else {
      if (areFiltersValidForPreAggregation(filters)) {
        selectQuery.addCustomFromTable(BILLING_DATA_HOURLY_PRE_AGGREGATED_TABLE);
        aggregateFunction = getSupportedAggregations(aggregateFunction);
      } else {
        selectQuery.addCustomFromTable(BILLING_DATA_HOURLY_TABLE);
      }
    }

    decorateQueryWithAggregations(selectQuery, aggregateFunction, fieldNames);

    decorateQueryWithMinMaxStartTime(selectQuery, fieldNames);

    if (isClusterFilterPresent(filters) && !checkForAdditionalFilterInClusterDrillDown(filters)) {
      addInstanceTypeFilter(filters);
    }

    if (!Lists.isNullOrEmpty(filters)) {
      filters = processFilterForTagsAndLabels(accountId, filters);
      decorateQueryWithFilters(selectQuery, filters);
    }

    addAccountFilter(selectQuery, accountId);

    selectQuery.getWhereClause().setDisableParens(true);
    queryMetaDataBuilder.fieldNames(fieldNames);
    queryMetaDataBuilder.query(selectQuery.toString());
    queryMetaDataBuilder.filters(filters);
    return queryMetaDataBuilder.build();
  }

  protected BillingDataQueryMetadata formFilterValuesQuery(String accountId, List<QLBillingDataFilter> filters,
      List<QLCCMEntityGroupBy> groupBy, List<QLBillingSortCriteria> sortCriteria, Integer limit, Integer offset) {
    BillingDataQueryMetadataBuilder queryMetaDataBuilder = BillingDataQueryMetadata.builder();
    SelectQuery selectQuery = new SelectQuery();
    ResultType resultType;
    resultType = ResultType.STACKED_TIME_SERIES;
    selectQuery.setFetchNext(limit);
    selectQuery.setOffset(offset);

    queryMetaDataBuilder.resultType(resultType);
    List<BillingDataMetaDataFields> fieldNames = new ArrayList<>();
    List<BillingDataMetaDataFields> groupByFields = new ArrayList<>();

    if (!shouldUseHourlyData(filters, accountId)) {
      if (isValidGroupByForPreAggregation(groupBy) && areFiltersValidForPreAggregation(filters)) {
        selectQuery.addCustomFromTable(BILLING_DATA_PRE_AGGREGATED_TABLE);
      } else {
        selectQuery.addCustomFromTable(schema.getBillingDataTable());
      }
    } else {
      if (isValidGroupByForPreAggregation(groupBy) && areFiltersValidForPreAggregation(filters)) {
        selectQuery.addCustomFromTable(BILLING_DATA_HOURLY_PRE_AGGREGATED_TABLE);
      } else {
        selectQuery.addCustomFromTable(BILLING_DATA_HOURLY_TABLE);
      }
    }

    if (isGroupByClusterPresent(groupBy)) {
      if (!isGroupByClusterTypePresent(groupBy)) {
        addClusterTypeGroupBy(groupBy);
      }
    }

    if (isValidGroupBy(groupBy)) {
      decorateQueryWithGroupBy(fieldNames, selectQuery, groupBy, groupByFields);
      decorateQueryWithNodeOrPodGroupBy(fieldNames, selectQuery, groupBy, groupByFields, filters);
    }

    addFiltersToExcludeUnallocatedRows(filters, groupBy);

    if (isGroupByEntityPresent(groupBy, QLCCMEntityGroupBy.WorkloadName) && !isWorkloadTypeFilterPresent(filters)) {
      addWorkloadTypeFilter(filters);
    }

    if (!Lists.isNullOrEmpty(filters)) {
      filters = processFilterForTagsAndLabels(accountId, filters);
      filters = filters.stream()
                    .filter(filter -> filter.getLabelSearch() == null && filter.getTagSearch() == null)
                    .collect(Collectors.toList());
      decorateQueryWithFilters(selectQuery, filters);
    }

    addAccountFilter(selectQuery, accountId);

    List<QLBillingSortCriteria> finalSortCriteria = validateAndAddSortCriteria(selectQuery, sortCriteria, fieldNames);

    selectQuery.getWhereClause().setDisableParens(true);
    queryMetaDataBuilder.fieldNames(fieldNames);
    queryMetaDataBuilder.query(selectQuery.toString());
    queryMetaDataBuilder.groupByFields(groupByFields);
    queryMetaDataBuilder.sortCriteria(finalSortCriteria);
    queryMetaDataBuilder.filters(filters);
    return queryMetaDataBuilder.build();
  }

  public BillingDataQueryMetadata formBudgetInsightQuery(String accountId, List<QLBillingDataFilter> filters,
      QLCCMAggregationFunction aggregateFunction, QLCCMTimeSeriesAggregation groupBy,
      List<QLBillingSortCriteria> sortCriteria) {
    BillingDataQueryMetadataBuilder queryMetaDataBuilder = BillingDataQueryMetadata.builder();
    SelectQuery selectQuery = new SelectQuery();
    ResultType resultType;
    resultType = ResultType.STACKED_TIME_SERIES;

    queryMetaDataBuilder.resultType(resultType);
    List<BillingDataMetaDataFields> fieldNames = new ArrayList<>();
    decorateQueryWithAggregation(selectQuery, aggregateFunction, fieldNames);

    selectQuery.addCustomFromTable(schema.getBillingDataTable());

    if (!Lists.isNullOrEmpty(filters)) {
      decorateQueryWithFilters(selectQuery, filters);
    }

    if (isValidGroupByTime(groupBy)) {
      List<BillingDataMetaDataFields> groupByFields = new ArrayList<>();
      decorateQueryWithGroupByTime(fieldNames, selectQuery, groupBy, groupByFields);
    }

    List<QLBillingSortCriteria> finalSortCriteria = validateAndAddSortCriteria(selectQuery, sortCriteria, fieldNames);

    addAccountFilter(selectQuery, accountId);

    selectQuery.getWhereClause().setDisableParens(true);
    queryMetaDataBuilder.fieldNames(fieldNames);
    queryMetaDataBuilder.query(selectQuery.toString());
    queryMetaDataBuilder.sortCriteria(finalSortCriteria);
    queryMetaDataBuilder.filters(filters);
    return queryMetaDataBuilder.build();
  }

  protected BillingDataQueryMetadata formNodeAndPodDetailsQuery(String accountId, List<QLBillingDataFilter> filters,
      List<QLCCMAggregationFunction> aggregateFunction, List<QLCCMEntityGroupBy> groupBy,
      QLCCMTimeSeriesAggregation groupByTime, List<QLBillingSortCriteria> sortCriteria, Integer limit, Integer offset) {
    BillingDataQueryMetadataBuilder queryMetaDataBuilder = BillingDataQueryMetadata.builder();
    SelectQuery selectQuery = new SelectQuery();

    ResultType resultType;
    resultType = ResultType.NODE_AND_POD_DETAILS;
    selectQuery.setFetchNext(limit);
    selectQuery.setOffset(offset);

    queryMetaDataBuilder.resultType(resultType);
    List<BillingDataMetaDataFields> fieldNames = new ArrayList<>();
    List<BillingDataMetaDataFields> groupByFields = new ArrayList<>();

    // To fetch clusterName from timescaleDb
    if (isGroupByEntityPresent(groupBy, QLCCMEntityGroupBy.Cluster)) {
      groupBy.add(QLCCMEntityGroupBy.ClusterName);
    }

    if (!isGroupByHour(groupByTime) && !shouldUseHourlyData(filters, accountId)) {
      if (isValidGroupByForPreAggregation(groupBy) && areFiltersValidForPreAggregation(filters)
          && areAggregationsValidForPreAggregation(aggregateFunction)) {
        selectQuery.addCustomFromTable(BILLING_DATA_PRE_AGGREGATED_TABLE);
        aggregateFunction = getSupportedAggregations(aggregateFunction);
      } else {
        selectQuery.addCustomFromTable(schema.getBillingDataTable());
      }
    } else {
      if (isValidGroupByForPreAggregation(groupBy) && areFiltersValidForPreAggregation(filters)
          && areAggregationsValidForPreAggregation(aggregateFunction)) {
        selectQuery.addCustomFromTable(BILLING_DATA_HOURLY_PRE_AGGREGATED_TABLE);
        aggregateFunction = getSupportedAggregations(aggregateFunction);
      } else {
        selectQuery.addCustomFromTable(BILLING_DATA_HOURLY_TABLE);
      }
    }

    decorateQueryWithAggregations(selectQuery, aggregateFunction, fieldNames);

    if (isValidGroupByTime(groupByTime)) {
      decorateQueryWithGroupByTime(fieldNames, selectQuery, groupByTime, groupByFields);
    }

    if (isValidGroupBy(groupBy)) {
      decorateQueryWithGroupBy(fieldNames, selectQuery, groupBy, groupByFields);
      decorateQueryWithNodeOrPodGroupBy(fieldNames, selectQuery, groupBy, groupByFields, filters);
    }

    if (!Lists.isNullOrEmpty(filters)) {
      filters = processFilterForTagsAndLabels(accountId, filters);
      decorateQueryWithFilters(selectQuery, filters);
    }

    List<QLBillingSortCriteria> finalSortCriteria = validateAndAddSortCriteria(selectQuery, sortCriteria, fieldNames);

    addAccountFilter(selectQuery, accountId);

    selectQuery.getWhereClause().setDisableParens(true);
    queryMetaDataBuilder.fieldNames(fieldNames);
    queryMetaDataBuilder.query(selectQuery.toString());
    queryMetaDataBuilder.groupByFields(groupByFields);
    queryMetaDataBuilder.sortCriteria(finalSortCriteria);
    queryMetaDataBuilder.filters(filters);
    return queryMetaDataBuilder.build();
  }

  protected BillingDataQueryMetadata formTotalCountQuery(
      String accountId, List<QLBillingDataFilter> filters, List<QLCCMEntityGroupBy> groupBy) {
    BillingDataQueryMetadataBuilder queryMetaDataBuilder = BillingDataQueryMetadata.builder();
    SelectQuery selectQuery = new SelectQuery();

    List<BillingDataMetaDataFields> fieldNames = new ArrayList<>();
    if (!shouldUseHourlyData(filters, accountId)) {
      if (isValidGroupByForPreAggregation(groupBy) && areFiltersValidForPreAggregation(filters)) {
        selectQuery.addCustomFromTable(BILLING_DATA_PRE_AGGREGATED_TABLE);
      } else {
        selectQuery.addCustomFromTable(schema.getBillingDataTable());
      }
    } else {
      if (isValidGroupByForPreAggregation(groupBy) && areFiltersValidForPreAggregation(filters)) {
        selectQuery.addCustomFromTable(BILLING_DATA_HOURLY_PRE_AGGREGATED_TABLE);
      } else {
        selectQuery.addCustomFromTable(BILLING_DATA_HOURLY_TABLE);
      }
    }

    if (isValidGroupByForFilterValues(groupBy)) {
      DbColumn column = getColumnAssociatedWithGroupBy(groupBy.get(0));
      selectQuery.addCustomColumns(
          Converter.toColumnSqlObject(FunctionCall.count().addColumnParams(column).setIsDistinct(true),
              BillingDataMetaDataFields.COUNT.getFieldName()));
      fieldNames.add(BillingDataMetaDataFields.COUNT);
    }

    List<QLBillingDataFilter> timeFilters = getTimeFilters(filters);

    if (isGroupByEntityPresent(groupBy, QLCCMEntityGroupBy.WorkloadName) && !isWorkloadTypeFilterPresent(filters)) {
      addWorkloadTypeFilter(filters);
    }

    if (!Lists.isNullOrEmpty(timeFilters)) {
      decorateQueryWithFilters(selectQuery, timeFilters);
    }

    addAccountFilter(selectQuery, accountId);

    selectQuery.getWhereClause().setDisableParens(true);
    queryMetaDataBuilder.fieldNames(fieldNames);
    queryMetaDataBuilder.query(selectQuery.toString());
    queryMetaDataBuilder.filters(filters);
    return queryMetaDataBuilder.build();
  }

  public CeActivePodCountQueryMetadata formPodCountQuery(
      String accountId, List<QLBillingDataFilter> filters, List<QLBillingSortCriteria> sortCriteria) {
    CeActivePodCountQueryMetadataBuilder queryMetaDataBuilder = CeActivePodCountQueryMetadata.builder();
    SelectQuery selectQuery = new SelectQuery();

    List<CeActivePodCountMetaDataFields> fieldNames = new ArrayList<>();
    selectQuery.addCustomFromTable(podTableSchema.getActivePodCountTable());

    addFieldsForPodCountDataFetcher(selectQuery, fieldNames);

    if (!Lists.isNullOrEmpty(filters)) {
      decorateQueryWithFilters(selectQuery, filters);
    }

    List<QLBillingSortCriteria> finalSortCriteria = validateAndAddSortCriteria(selectQuery, sortCriteria);
    addAccountFilter(selectQuery, accountId);

    selectQuery.getWhereClause().setDisableParens(true);
    queryMetaDataBuilder.sortCriteria(finalSortCriteria);
    queryMetaDataBuilder.fieldNames(fieldNames);
    queryMetaDataBuilder.query(selectQuery.toString());
    queryMetaDataBuilder.filters(filters);
    return queryMetaDataBuilder.build();
  }

  private void addAccountFilter(SelectQuery selectQuery, String accountId) {
    selectQuery.addCondition(BinaryCondition.equalTo(schema.getAccountId(), accountId));
  }

  private void decorateQueryWithAggregations(SelectQuery selectQuery, List<QLCCMAggregationFunction> aggregateFunctions,
      List<BillingDataMetaDataFields> fieldNames) {
    for (QLCCMAggregationFunction aggregationFunction : aggregateFunctions) {
      decorateQueryWithAggregation(selectQuery, aggregationFunction, fieldNames);
    }
  }

  // TODO: Refactor; use reflection as implemented below this method for all schema fields matching with DB fields.
  public void decorateQueryWithAggregation(SelectQuery selectQuery, QLCCMAggregationFunction aggregationFunction,
      List<BillingDataMetaDataFields> fieldNames) {
    if (aggregationFunction != null && aggregationFunction.getOperationType() == QLCCMAggregateOperation.SUM) {
      if (aggregationFunction.getColumnName().equals(schema.getBillingAmount().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getBillingAmount()),
                BillingDataMetaDataFields.SUM.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.SUM);
      } else if (aggregationFunction.getColumnName().equalsIgnoreCase(schema.getIdleCost().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getActualIdleCost()),
                BillingDataMetaDataFields.IDLECOST.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.IDLECOST);
      } else if (aggregationFunction.getColumnName().equals(schema.getNetworkCost().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getNetworkCost()),
                BillingDataMetaDataFields.NETWORKCOST.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.NETWORKCOST);
      } else if (aggregationFunction.getColumnName().equals(schema.getUnallocatedCost().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getUnallocatedCost()),
                BillingDataMetaDataFields.UNALLOCATEDCOST.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.UNALLOCATEDCOST);
      } else if (aggregationFunction.getColumnName().equalsIgnoreCase(schema.getCpuIdleCost().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getCpuActualIdleCost()),
                BillingDataMetaDataFields.CPUIDLECOST.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.CPUIDLECOST);
      } else if (aggregationFunction.getColumnName().equalsIgnoreCase(schema.getMemoryIdleCost().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getMemoryActualIdleCost()),
                BillingDataMetaDataFields.MEMORYIDLECOST.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.MEMORYIDLECOST);
      } else if (aggregationFunction.getColumnName().equals(schema.getCpuBillingAmount().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getCpuBillingAmount()),
                BillingDataMetaDataFields.CPUBILLINGAMOUNT.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.CPUBILLINGAMOUNT);
      } else if (aggregationFunction.getColumnName().equals(schema.getMemoryBillingAmount().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getMemoryBillingAmount()),
                BillingDataMetaDataFields.MEMORYBILLINGAMOUNT.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.MEMORYBILLINGAMOUNT);
      } else if (aggregationFunction.getColumnName().equals(schema.getSystemCost().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getSystemCost()),
                BillingDataMetaDataFields.SYSTEMCOST.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.SYSTEMCOST);
      } else if (aggregationFunction.getColumnName().equals(schema.getEffectiveCpuLimit().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getEffectiveCpuLimit()),
                BillingDataMetaDataFields.AGGREGATEDCPULIMIT.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.AGGREGATEDCPULIMIT);
      } else if (aggregationFunction.getColumnName().equals(schema.getEffectiveMemoryLimit().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getEffectiveMemoryLimit()),
                BillingDataMetaDataFields.AGGREGATEDMEMORYLIMIT.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.AGGREGATEDMEMORYLIMIT);
      } else if (aggregationFunction.getColumnName().equals(schema.getEffectiveCpuRequest().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getEffectiveCpuRequest()),
                BillingDataMetaDataFields.AGGREGATEDCPUREQUEST.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.AGGREGATEDCPUREQUEST);
      } else if (aggregationFunction.getColumnName().equals(schema.getEffectiveMemoryRequest().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getEffectiveMemoryRequest()),
                BillingDataMetaDataFields.AGGREGATEDMEMORYREQUEST.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.AGGREGATEDMEMORYREQUEST);
      } else if (aggregationFunction.getColumnName().equals(
                     schema.getEffectiveCpuUtilizationValue().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getEffectiveCpuUtilizationValue()),
                BillingDataMetaDataFields.AGGREGATEDCPUUTILIZATIONVALUE.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.AGGREGATEDCPUUTILIZATIONVALUE);
      } else if (aggregationFunction.getColumnName().equals(
                     schema.getEffectiveMemoryUtilizationValue().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getEffectiveMemoryUtilizationValue()),
                BillingDataMetaDataFields.AGGREGATEDMEMORYUTILIZATIONVALUE.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.AGGREGATEDMEMORYUTILIZATIONVALUE);
      } else if (aggregationFunction.getColumnName().equals(schema.getCpuRequest().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getCpuRequest()),
                BillingDataMetaDataFields.CPUREQUEST.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.CPUREQUEST);
      } else if (aggregationFunction.getColumnName().equals(schema.getMemoryRequest().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getMemoryRequest()),
                BillingDataMetaDataFields.MEMORYREQUEST.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.MEMORYREQUEST);
      } else {
        addSumOnColumn(aggregationFunction.getColumnName(), selectQuery, fieldNames);
      }
    } else if (aggregationFunction != null && aggregationFunction.getOperationType() == QLCCMAggregateOperation.MAX) {
      if (aggregationFunction.getColumnName().equals(schema.getMaxCpuUtilization().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.max().addColumnParams(schema.getMaxCpuUtilization()),
                BillingDataMetaDataFields.MAXCPUUTILIZATION.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.MAXCPUUTILIZATION);
      } else if (aggregationFunction.getColumnName().equals(schema.getMaxMemoryUtilization().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.max().addColumnParams(schema.getMaxMemoryUtilization()),
                BillingDataMetaDataFields.MAXMEMORYUTILIZATION.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.MAXMEMORYUTILIZATION);
      } else if (aggregationFunction.getColumnName().equals(schema.getMaxCpuUtilizationValue().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.max().addColumnParams(schema.getMaxCpuUtilizationValue()),
                BillingDataMetaDataFields.MAXCPUUTILIZATIONVALUE.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.MAXCPUUTILIZATIONVALUE);
      } else if (aggregationFunction.getColumnName().equals(schema.getMaxMemoryUtilizationValue().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.max().addColumnParams(schema.getMaxMemoryUtilizationValue()),
                BillingDataMetaDataFields.MAXMEMORYUTILIZATIONVALUE.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.MAXMEMORYUTILIZATIONVALUE);
      } else {
        addMaxOnColumn(aggregationFunction.getColumnName(), selectQuery, fieldNames);
      }
    } else if (aggregationFunction != null && aggregationFunction.getOperationType() == QLCCMAggregateOperation.AVG) {
      if (aggregationFunction.getColumnName().equals(schema.getAvgCpuUtilization().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.avg().addColumnParams(schema.getAvgCpuUtilization()),
                BillingDataMetaDataFields.AVGCPUUTILIZATION.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.AVGCPUUTILIZATION);
      } else if (aggregationFunction.getColumnName().equals(schema.getAvgMemoryUtilization().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.avg().addColumnParams(schema.getAvgMemoryUtilization()),
                BillingDataMetaDataFields.AVGMEMORYUTILIZATION.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.AVGMEMORYUTILIZATION);
      } else if (aggregationFunction.getColumnName().equals(schema.getAvgCpuUtilizationValue().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.avg().addColumnParams(schema.getAvgCpuUtilizationValue()),
                BillingDataMetaDataFields.AVGCPUUTILIZATIONVALUE.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.AVGCPUUTILIZATIONVALUE);
      } else if (aggregationFunction.getColumnName().equals(schema.getAvgMemoryUtilizationValue().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.avg().addColumnParams(schema.getAvgMemoryUtilizationValue()),
                BillingDataMetaDataFields.AVGMEMORYUTILIZATIONVALUE.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.AVGMEMORYUTILIZATIONVALUE);
      } else {
        addAvgOnColumn(aggregationFunction.getColumnName(), selectQuery, fieldNames);
      }
    } else if (aggregationFunction != null && aggregationFunction.getOperationType() == QLCCMAggregateOperation.COUNT) {
      selectQuery.addCustomColumns(
          Converter.toColumnSqlObject(FunctionCall.count().addColumnParams(schema.getInstanceId()),
              BillingDataMetaDataFields.COUNT.getFieldName()));
      fieldNames.add(BillingDataMetaDataFields.COUNT);
    }
  }

  @SneakyThrows
  private DbColumn getCorrespondingDBColumn(@NotNull String columnName) {
    try {
      Field field = schema.getClass().getDeclaredField(columnName);
      if (field.getType().equals(DbColumn.class)) {
        field.setAccessible(true);
        return (DbColumn) field.get(schema);
      }
    } catch (NoSuchFieldException e) {
      log.warn("No DbColumn field with name {} in BillingDataTableSchema graphql schema", columnName);
      // find by ignorecase if exact camelCase is not found.
      for (Field field : schema.getClass().getDeclaredFields()) {
        if (field.getType().equals(DbColumn.class) && field.getName().equalsIgnoreCase(columnName)) {
          field.setAccessible(true);
          return (DbColumn) field.get(schema);
        }
      }
      throw new InvalidRequestException("can't find column '" + columnName + "' in graphql schema");
    } catch (Exception e) {
      log.error("unknown exception from decorateQueryWithAggregation ", e);
    }
    throw new InvalidRequestException("can't find column " + columnName);
  }

  private void addAvgOnColumn(String columnName, SelectQuery selectQuery, List<BillingDataMetaDataFields> fieldNames) {
    DbColumn dbColumn = getCorrespondingDBColumn(columnName);
    BillingDataMetaDataFields field = BillingDataMetaDataFields.valueOf(dbColumn.getColumnNameSQL().toUpperCase());
    selectQuery.addCustomColumns(
        Converter.toColumnSqlObject(FunctionCall.avg().addColumnParams(dbColumn), field.getFieldName()));
    fieldNames.add(field);
  }

  private void addSumOnColumn(String columnName, SelectQuery selectQuery, List<BillingDataMetaDataFields> fieldNames) {
    DbColumn dbColumn = getCorrespondingDBColumn(columnName);
    BillingDataMetaDataFields field = BillingDataMetaDataFields.valueOf(dbColumn.getColumnNameSQL().toUpperCase());
    selectQuery.addCustomColumns(
        Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(dbColumn), field.getFieldName()));
    fieldNames.add(field);
  }

  private void addMaxOnColumn(String columnName, SelectQuery selectQuery, List<BillingDataMetaDataFields> fieldNames) {
    DbColumn dbColumn = getCorrespondingDBColumn(columnName);
    BillingDataMetaDataFields field = BillingDataMetaDataFields.valueOf(dbColumn.getColumnNameSQL().toUpperCase());
    selectQuery.addCustomColumns(
        Converter.toColumnSqlObject(FunctionCall.max().addColumnParams(dbColumn), field.getFieldName()));
    fieldNames.add(field);
  }

  private void decorateQueryWithMinMaxStartTime(SelectQuery selectQuery, List<BillingDataMetaDataFields> fieldNames) {
    selectQuery.addCustomColumns(Converter.toColumnSqlObject(FunctionCall.min().addColumnParams(schema.getStartTime()),
        BillingDataMetaDataFields.MIN_STARTTIME.getFieldName()));
    fieldNames.add(BillingDataMetaDataFields.MIN_STARTTIME);
    selectQuery.addCustomColumns(Converter.toColumnSqlObject(FunctionCall.max().addColumnParams(schema.getStartTime()),
        BillingDataMetaDataFields.MAX_STARTTIME.getFieldName()));
    fieldNames.add(BillingDataMetaDataFields.MAX_STARTTIME);
  }

  private void decorateQueryWithFilters(SelectQuery selectQuery, List<QLBillingDataFilter> filters) {
    for (QLBillingDataFilter filter : filters) {
      Set<QLBillingDataFilterType> filterTypes = QLBillingDataFilter.getFilterTypes(filter);
      for (QLBillingDataFilterType type : filterTypes) {
        if (type.getMetaDataFields().getFilterKind() == QLFilterKind.SIMPLE) {
          decorateSimpleFilter(selectQuery, filter, type);
        } else {
          log.error("Failed to apply filter :[{}]", filter);
        }
      }
    }
  }

  private void decorateSimpleFilter(SelectQuery selectQuery, QLBillingDataFilter filter, QLBillingDataFilterType type) {
    Filter f = QLBillingDataFilter.getFilter(type, filter);
    if (checkFilter(f)) {
      if (isIdFilter(f)) {
        addSimpleIdOperator(selectQuery, f, type);
      } else if (isTimeFilter(f)) {
        addSimpleTimeFilter(selectQuery, f, type);
      } else if (f instanceof QLNumberFilter) {
        addHavingOnNumberFilter(selectQuery, f, type);
      }
    } else {
      log.info("Not adding filter since it is not valid " + f);
    }
  }

  private void addHavingOnNumberFilter(SelectQuery selectQuery, Filter filter, QLBillingDataFilterType type) {
    Preconditions.checkState(filter.getValues().length > 0, "filter.getValues().length == 0");

    DbColumn key = getFilterKey(type);
    QLNumberFilter numberFilter = (QLNumberFilter) filter;

    FunctionCall aggregationFn = FunctionCall.sum();

    if (type == QLBillingDataFilterType.StorageUtilizationValue) {
      aggregationFn = FunctionCall.max();
    }

    switch (numberFilter.getOperator()) {
      case GREATER_THAN:
        selectQuery.addHaving(BinaryCondition.greaterThan(aggregationFn.addColumnParams(key), filter.getValues()[0]));
        break;
      case LESS_THAN:
        selectQuery.addHaving(BinaryCondition.lessThan(aggregationFn.addColumnParams(key), filter.getValues()[0]));
        break;
      case GREATER_THAN_OR_EQUALS:
        selectQuery.addHaving(
            BinaryCondition.greaterThanOrEq(aggregationFn.addColumnParams(key), filter.getValues()[0]));
        break;
      case LESS_THAN_OR_EQUALS:
        selectQuery.addHaving(BinaryCondition.lessThanOrEq(aggregationFn.addColumnParams(key), filter.getValues()[0]));
        break;
      case EQUALS:
        selectQuery.addHaving(BinaryCondition.equalTo(aggregationFn.addColumnParams(key), filter.getValues()[0]));
        break;
      case NOT_EQUALS:
        selectQuery.addHaving(BinaryCondition.notEqualTo(aggregationFn.addColumnParams(key), filter.getValues()[0]));
        break;
      default:
        throw new InvalidRequestException("Invalid NumberFilter: " + filter.getOperator());
    }
  }

  private void addSimpleTimeFilter(SelectQuery selectQuery, Filter filter, QLBillingDataFilterType type) {
    DbColumn key = getFilterKey(type);
    QLTimeFilter timeFilter = (QLTimeFilter) filter;
    switch (timeFilter.getOperator()) {
      case BEFORE:
        selectQuery.addCondition(BinaryCondition.lessThanOrEq(key, Instant.ofEpochMilli((Long) timeFilter.getValue())));
        break;
      case AFTER:
        selectQuery.addCondition(
            BinaryCondition.greaterThanOrEq(key, Instant.ofEpochMilli((Long) timeFilter.getValue())));
        break;
      default:
        throw new InvalidRequestException("Invalid TimeFilter operator: " + filter.getOperator());
    }
  }

  private void addSimpleIdOperator(SelectQuery selectQuery, Filter filter, QLBillingDataFilterType type) {
    DbColumn key = getFilterKey(type);
    QLIdOperator operator = (QLIdOperator) filter.getOperator();
    QLIdOperator finalOperator = operator;
    if (filter.getValues().length > 0) {
      if (operator == QLIdOperator.EQUALS) {
        finalOperator = QLIdOperator.IN;
        log.info("Changing simpleStringOperator from [{}] to [{}]", operator, finalOperator);
      } else {
        finalOperator = operator;
      }
    }
    switch (finalOperator) {
      case EQUALS:
        selectQuery.addCondition(BinaryCondition.equalTo(key, filter.getValues()[0]));
        break;
      case IN:
        selectQuery.addCondition(new InCondition(key, (Object[]) filter.getValues()));
        break;
      case NOT_NULL:
        selectQuery.addCondition(UnaryCondition.isNotNull(key));
        break;
      case NOT_IN:
        InCondition inCondition = new InCondition(key, (Object[]) filter.getValues());
        inCondition.setNegate(true);
        selectQuery.addCondition(inCondition);
        break;
      case LIKE:
        selectQuery.addCondition(BinaryCondition.like(key, "%" + filter.getValues()[0] + "%"));
        break;
      default:
        throw new InvalidRequestException("String simple operator not supported" + operator);
    }
  }

  private boolean isIdFilter(Filter f) {
    return f instanceof QLIdFilter;
  }

  private boolean isTimeFilter(Filter f) {
    return f instanceof QLTimeFilter;
  }

  private boolean checkFilter(Filter f) {
    return f.getOperator() != null && EmptyPredicate.isNotEmpty(f.getValues());
  }

  private DbColumn getFilterKey(QLBillingDataFilterType type) {
    switch (type) {
      case EndTime:
      case StartTime:
        return schema.getStartTime();
      case Application:
        return schema.getAppId();
      case Service:
        return schema.getServiceId();
      case Environment:
        return schema.getEnvId();
      case Cluster:
        return schema.getClusterId();
      case CloudServiceName:
        return schema.getCloudServiceName();
      case LaunchType:
        return schema.getLaunchType();
      case TaskId:
        return schema.getTaskId();
      case InstanceType:
        return schema.getInstanceType();
      case InstanceName:
        return schema.getInstanceName();
      case WorkloadName:
        return schema.getWorkloadName();
      case WorkloadType:
        return schema.getWorkloadType();
      case Namespace:
        return schema.getNamespace();
      case CloudProvider:
        return schema.getCloudProviderId();
      case NodeInstanceId:
      case PodInstanceId:
        return schema.getInstanceId();
      case ParentInstanceId:
        return schema.getParentInstanceId();
      case StorageUtilizationValue:
        return schema.getStorageUtilizationValue();
      case LabelSearch:
      case TagSearch:
        return null;
      default:
        throw new InvalidRequestException("Filter type not supported " + type);
    }
  }

  private void decorateQueryWithGroupBy(List<BillingDataMetaDataFields> fieldNames, SelectQuery selectQuery,
      List<QLCCMEntityGroupBy> groupBy, List<BillingDataMetaDataFields> groupByFields) {
    for (QLCCMEntityGroupBy aggregation : groupBy) {
      if (aggregation.getAggregationKind() == QLAggregationKind.SIMPLE) {
        decorateSimpleGroupBy(fieldNames, selectQuery, aggregation, groupByFields);
      }
    }
  }

  private void decorateSimpleGroupBy(List<BillingDataMetaDataFields> fieldNames, SelectQuery selectQuery,
      QLCCMEntityGroupBy aggregation, List<BillingDataMetaDataFields> groupByFields) {
    DbColumn groupBy;
    switch (aggregation) {
      case Application:
        groupBy = schema.getAppId();
        break;
      case StartTime:
        groupBy = schema.getStartTime();
        break;
      case Service:
        groupBy = schema.getServiceId();
        break;
      case Region:
        groupBy = schema.getRegion();
        break;
      case Cluster:
        groupBy = schema.getClusterId();
        break;
      case ClusterName:
        groupBy = schema.getClusterName();
        break;
      case Environment:
        groupBy = schema.getEnvId();
        break;
      case InstanceType:
        groupBy = schema.getInstanceType();
        break;
      case CloudServiceName:
        groupBy = schema.getCloudServiceName();
        break;
      case TaskId:
        groupBy = schema.getTaskId();
        break;
      case LaunchType:
        groupBy = schema.getLaunchType();
        break;
      case WorkloadName:
        groupBy = schema.getWorkloadName();
        break;
      case WorkloadType:
        groupBy = schema.getWorkloadType();
        break;
      case Namespace:
        groupBy = schema.getNamespace();
        break;
      case ClusterType:
        groupBy = schema.getClusterType();
        break;
      case CloudProvider:
        groupBy = schema.getCloudProviderId();
        break;
      case InstanceName:
        groupBy = schema.getInstanceName();
        break;
      case Pod:
      case Node:
      case PV:
        return;
      default:
        throw new InvalidRequestException("Invalid groupBy clause");
    }

    addGroupByColumn(selectQuery, groupByFields, fieldNames, groupBy, true);
  }

  private void decorateQueryWithNodeOrPodGroupBy(List<BillingDataMetaDataFields> fieldNames, SelectQuery selectQuery,
      List<QLCCMEntityGroupBy> groupBy, List<BillingDataMetaDataFields> groupByFields,
      List<QLBillingDataFilter> filters) {
    List<String> instanceTypes = new ArrayList<>();
    boolean isNodeAndPodQuery = false;
    boolean isNodeQuery = false;
    for (QLCCMEntityGroupBy aggregation : groupBy) {
      switch (aggregation) {
        case Node:
          instanceTypes.add("K8S_NODE");
          isNodeAndPodQuery = true;
          isNodeQuery = true;
          break;
        case Pod:
          instanceTypes.add("K8S_POD");
          instanceTypes.add("K8S_POD_FARGATE");
          isNodeAndPodQuery = true;
          break;
        case PV:
          instanceTypes.add("K8S_PV");
          isNodeAndPodQuery = true;
          break;
        default:
          break;
      }
    }
    if (isNodeAndPodQuery) {
      filters.add(
          QLBillingDataFilter.builder()
              .instanceType(
                  QLIdFilter.builder().operator(QLIdOperator.IN).values(instanceTypes.toArray(new String[0])).build())
              .build());
      // Adding groupBy with corresponding selectField
      addGroupByColumn(selectQuery, groupByFields, fieldNames, schema.getInstanceId(), true);
      addGroupByColumn(selectQuery, groupByFields, fieldNames, schema.getInstanceType(), false);
      addGroupByColumn(selectQuery, groupByFields, fieldNames, schema.getClusterName(), false);

      if (!isNodeQuery) {
        addGroupByColumn(selectQuery, groupByFields, fieldNames, schema.getInstanceName(), true);
        addGroupByColumn(selectQuery, groupByFields, fieldNames, schema.getNamespace(), false);
        addGroupByColumn(selectQuery, groupByFields, fieldNames, schema.getCloudProvider(), false);
        addGroupByColumn(selectQuery, groupByFields, fieldNames, schema.getClusterId(), false);
        addGroupByColumn(selectQuery, groupByFields, fieldNames, schema.getWorkloadName(), false);
      }
    }
  }

  private void addGroupByColumn(SelectQuery selectQuery, List<BillingDataMetaDataFields> groupByFields,
      List<BillingDataMetaDataFields> fieldNames, DbColumn dbColumn, boolean notNull) {
    selectQuery.addColumns(dbColumn);
    selectQuery.addGroupings(dbColumn);
    fieldNames.add(BillingDataMetaDataFields.valueOf(dbColumn.getName().toUpperCase()));
    if (notNull) {
      selectQuery.addCondition(UnaryCondition.isNotNull(dbColumn));
    }
    groupByFields.add(BillingDataMetaDataFields.valueOf(dbColumn.getName().toUpperCase()));
  }

  protected List<QLCCMEntityGroupBy> getGroupByEntity(List<QLCCMGroupBy> groupBy) {
    return groupBy != null ? groupBy.stream()
                                 .filter(g -> g.getEntityGroupBy() != null)
                                 .map(QLCCMGroupBy::getEntityGroupBy)
                                 .collect(Collectors.toList())
                           : Collections.emptyList();
  }

  private boolean isValidGroupBy(List<QLCCMEntityGroupBy> groupBy) {
    return EmptyPredicate.isNotEmpty(groupBy) && groupBy.size() <= 5;
  }

  private boolean isValidGroupByForFilterValues(List<QLCCMEntityGroupBy> groupBy) {
    return EmptyPredicate.isNotEmpty(groupBy) && groupBy.size() <= 1;
  }

  private List<QLBillingSortCriteria> validateAndAddSortCriteria(
      SelectQuery selectQuery, List<QLBillingSortCriteria> sortCriteria, List<BillingDataMetaDataFields> fieldNames) {
    if (isEmpty(sortCriteria)) {
      return new ArrayList<>();
    }

    sortCriteria.removeIf(qlBillingSortCriteria
        -> qlBillingSortCriteria.getSortOrder() == null
            || !fieldNames.contains(qlBillingSortCriteria.getSortType().getBillingMetaData()));

    if (EmptyPredicate.isNotEmpty(sortCriteria)) {
      sortCriteria.forEach(s -> addOrderBy(selectQuery, s));
    }
    return sortCriteria;
  }

  // For podCountDataFetcher
  private List<QLBillingSortCriteria> validateAndAddSortCriteria(
      SelectQuery selectQuery, List<QLBillingSortCriteria> sortCriteria) {
    if (isEmpty(sortCriteria)) {
      return new ArrayList<>();
    }

    sortCriteria.removeIf(qlBillingSortCriteria -> qlBillingSortCriteria.getSortOrder() == null);

    if (EmptyPredicate.isNotEmpty(sortCriteria)) {
      sortCriteria.forEach(s -> addOrderBy(selectQuery, s));
    }
    return sortCriteria;
  }

  private void addOrderBy(SelectQuery selectQuery, QLBillingSortCriteria sortCriteria) {
    QLBillingSortType sortType = sortCriteria.getSortType();
    OrderObject.Dir dir = sortCriteria.getSortOrder() == QLSortOrder.ASCENDING ? Dir.ASCENDING : Dir.DESCENDING;
    switch (sortType) {
      case Time:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.STARTTIME.getFieldName(), dir);
        break;
      case Amount:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.SUM.getFieldName(), dir);
        break;
      case IdleCost:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.IDLECOST.getFieldName(), dir);
        break;
      case LaunchType:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.LAUNCHTYPE.getFieldName(), dir);
        break;
      case Service:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.SERVICEID.getFieldName(), dir);
        break;
      case CloudServiceName:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.CLOUDSERVICENAME.getFieldName(), dir);
        break;
      case Application:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.APPID.getFieldName(), dir);
        break;
      case TaskId:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.TASKID.getFieldName(), dir);
        break;
      case Namespace:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.NAMESPACE.getFieldName(), dir);
        break;
      case Cluster:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.CLUSTERID.getFieldName(), dir);
        break;
      case Node:
      case Pod:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.INSTANCEID.getFieldName(), dir);
        break;
      case Environment:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.ENVID.getFieldName(), dir);
        break;
      case Workload:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.WORKLOADNAME.getFieldName(), dir);
        break;
      case CloudProvider:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.CLOUDPROVIDERID.getFieldName(), dir);
        break;
      case storageCost:
        selectQuery.addCustomOrdering(BillingDataMetaDataFields.STORAGECOST.getFieldName(), dir);
        break;
      default:
        throw new InvalidRequestException("Order type not supported " + sortType);
    }
  }

  private boolean isGroupByClusterPresent(List<QLCCMEntityGroupBy> groupByList) {
    return groupByList.stream().anyMatch(groupBy -> groupBy == QLCCMEntityGroupBy.Cluster);
  }

  private boolean isGroupByClusterTypePresent(List<QLCCMEntityGroupBy> groupByList) {
    return groupByList.stream().anyMatch(groupBy -> groupBy == QLCCMEntityGroupBy.ClusterType);
  }

  public void addClusterTypeGroupBy(List<QLCCMEntityGroupBy> groupByList) {
    groupByList.add(QLCCMEntityGroupBy.ClusterType);
  }

  private boolean isGroupByStartTimePresent(List<QLCCMEntityGroupBy> groupByList) {
    return groupByList.stream().anyMatch(groupBy -> groupBy == QLCCMEntityGroupBy.StartTime);
  }

  private boolean isNoneGroupBySelectedWithoutFilterInClusterView(
      List<QLCCMEntityGroupBy> groupByList, List<QLBillingDataFilter> filters) {
    return isClusterFilterPresent(filters) && !checkForAdditionalFilterInClusterDrillDown(filters)
        && isGroupByNonePresent(groupByList);
  }

  private boolean isGroupByNonePresent(List<QLCCMEntityGroupBy> groupByList) {
    return isEmpty(groupByList) || (groupByList.size() == 1 && isGroupByStartTimePresent(groupByList));
  }

  private boolean checkForAdditionalFilterInClusterDrillDown(List<QLBillingDataFilter> filters) {
    for (QLBillingDataFilter filter : filters) {
      Set<QLBillingDataFilterType> filterTypes = QLBillingDataFilter.getFilterTypes(filter);
      if (!filterTypes.isEmpty()
          && (!(filter.getCluster() != null || filter.getStartTime() != null || filter.getEndTime() != null))) {
        return true;
      }
    }
    return false;
  }

  private boolean isWorkloadTypeFilterPresent(List<QLBillingDataFilter> filters) {
    return filters.stream().anyMatch(filter -> filter.getWorkloadType() != null);
  }

  private boolean isClusterFilterPresent(List<QLBillingDataFilter> filters) {
    return filters.stream().anyMatch(filter -> filter.getCluster() != null);
  }

  protected boolean isUnallocatedCostAggregationPresent(List<QLCCMAggregationFunction> aggregationFunctions) {
    return aggregationFunctions.stream().anyMatch(agg -> agg.getColumnName().equals("unallocatedcost"));
  }

  private void addWorkloadTypeFilter(List<QLBillingDataFilter> filters) {
    QLBillingDataFilter workloadTypeFilter =
        QLBillingDataFilter.builder()
            .workloadType(QLIdFilter.builder().operator(QLIdOperator.NOT_NULL).values(new String[] {""}).build())
            .build();
    filters.add(workloadTypeFilter);
  }

  protected void addInstanceTypeFilter(List<QLBillingDataFilter> filters) {
    if (!isInstanceTypeFilterPresent(filters)) {
      List<String> instanceTypeValues = new ArrayList<>();
      instanceTypeValues.add("ECS_TASK_FARGATE");
      instanceTypeValues.add("K8S_POD_FARGATE");
      instanceTypeValues.add("ECS_CONTAINER_INSTANCE");
      instanceTypeValues.add("K8S_NODE");
      instanceTypeValues.add("K8S_PV");
      addInstanceTypeFilter(filters, instanceTypeValues);
    }
  }

  private void addInstanceTypeFilter(List<QLBillingDataFilter> filters, List<String> instanceTypeValues) {
    QLBillingDataFilter instanceTypeFilter = QLBillingDataFilter.builder()
                                                 .instanceType(QLIdFilter.builder()
                                                                   .operator(QLIdOperator.EQUALS)
                                                                   .values(instanceTypeValues.toArray(new String[0]))
                                                                   .build())
                                                 .build();
    filters.add(instanceTypeFilter);
  }

  private boolean isInstanceTypeFilterPresent(List<QLBillingDataFilter> filters) {
    return filters.stream().anyMatch(filter -> filter.getInstanceType() != null);
  }

  protected QLCCMTimeSeriesAggregation getGroupByTime(List<QLCCMGroupBy> groupBy) {
    if (groupBy != null) {
      Optional<QLCCMTimeSeriesAggregation> first = groupBy.stream()
                                                       .filter(g -> g.getTimeAggregation() != null)
                                                       .map(QLCCMGroupBy::getTimeAggregation)
                                                       .findFirst();
      return first.orElse(null);
    }
    return null;
  }

  private void decorateQueryWithGroupByTime(List<BillingDataMetaDataFields> fieldNames, SelectQuery selectQuery,
      QLCCMTimeSeriesAggregation groupByTime, List<BillingDataMetaDataFields> groupByFields) {
    String timeBucket = getGroupByTimeQueryWithDateTrunc(groupByTime, "starttime");

    selectQuery.addCustomColumns(Converter.toCustomColumnSqlObject(
        new CustomExpression(timeBucket).setDisableParens(true), BillingDataMetaDataFields.TIME_SERIES.getFieldName()));
    selectQuery.addCustomGroupings(BillingDataMetaDataFields.TIME_SERIES.getFieldName());
    selectQuery.addCustomOrdering(BillingDataMetaDataFields.TIME_SERIES.getFieldName(), Dir.ASCENDING);
    fieldNames.add(BillingDataMetaDataFields.TIME_SERIES);
    groupByFields.add(BillingDataMetaDataFields.TIME_SERIES);
  }

  private boolean isValidGroupByTime(QLCCMTimeSeriesAggregation groupByTime) {
    return groupByTime != null && groupByTime.getTimeGroupType() != null;
  }

  public String getGroupByTimeQueryWithDateTrunc(QLCCMTimeSeriesAggregation groupByTime, String dbFieldName) {
    String unit;
    switch (groupByTime.getTimeGroupType()) {
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
        log.warn("Unsupported timeGroupType " + groupByTime.getTimeGroupType());
        throw new InvalidRequestException("Cant apply time group by");
    }

    return new StringBuilder("date_trunc('")
        .append(unit)
        .append("',")
        .append(dbFieldName)
        .append(" at time zone '")
        .append(STANDARD_TIME_ZONE)
        .append("')")
        .toString();
  }

  protected QLTimeFilter getStartTimeFilter(List<QLBillingDataFilter> filters) {
    Optional<QLBillingDataFilter> startTimeDataFilter =
        filters.stream().filter(qlBillingDataFilter -> qlBillingDataFilter.getStartTime() != null).findFirst();
    if (startTimeDataFilter.isPresent()) {
      return startTimeDataFilter.get().getStartTime();
    } else {
      throw new InvalidRequestException("Start time cannot be null");
    }
  }

  protected QLTimeFilter getEndTimeFilter(List<QLBillingDataFilter> filters) {
    Optional<QLBillingDataFilter> endTimeDataFilter =
        filters.stream().filter(qlBillingDataFilter -> qlBillingDataFilter.getEndTime() != null).findFirst();
    if (endTimeDataFilter.isPresent()) {
      return endTimeDataFilter.get().getEndTime();
    } else {
      throw new InvalidRequestException("Start time cannot be null");
    }
  }

  protected QLBillingDataFilter getInstanceTypeFilter() {
    String[] instanceTypeIdFilterValues = new String[] {"CLUSTER_UNALLOCATED"};
    QLIdFilter instanceTypeFilter =
        QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(instanceTypeIdFilterValues).build();
    return QLBillingDataFilter.builder().instanceType(instanceTypeFilter).build();
  }

  protected List<QLBillingDataFilter> prepareFiltersForUnallocatedCostData(List<QLBillingDataFilter> filters) {
    List<QLBillingDataFilter> modifiedFilterList =
        filters.stream()
            .filter(qlBillingDataFilter -> qlBillingDataFilter.getInstanceType() == null)
            .collect(Collectors.toList());

    modifiedFilterList.add(getInstanceTypeFilter());
    return modifiedFilterList;
  }

  private List<QLBillingDataFilter> processFilterForTagsAndLabels(String accountId, List<QLBillingDataFilter> filters) {
    List<QLBillingDataFilter> newList = new ArrayList<>();
    for (QLBillingDataFilter filter : filters) {
      Set<QLBillingDataFilterType> filterTypes = QLBillingDataFilter.getFilterTypes(filter);
      for (QLBillingDataFilterType type : filterTypes) {
        if (type == QLBillingDataFilterType.Tag) {
          QLBillingDataTagFilter tagFilter = filter.getTag();
          if (tagFilter != null) {
            QLIdOperator operator = tagFilter.getOperator();
            List<QLBillingDataTagType> tagEntityTypes = new ArrayList<>();
            if (tagFilter.getEntityType() == null) {
              tagEntityTypes.add(QLBillingDataTagType.APPLICATION);
            } else {
              tagEntityTypes.add(tagFilter.getEntityType());
            }
            for (QLBillingDataTagType tagEntityType : tagEntityTypes) {
              Set<String> entityIds =
                  tagHelper.getEntityIdsFromTags(accountId, tagFilter.getTags(), getEntityType(tagEntityType));
              if (isNotEmpty(entityIds)) {
                switch (tagEntityType) {
                  case APPLICATION:
                    newList.add(QLBillingDataFilter.builder()
                                    .application(QLIdFilter.builder()
                                                     .operator(operator)
                                                     .values(entityIds.toArray(new String[0]))
                                                     .build())
                                    .build());
                    break;
                  case SERVICE:
                    newList.add(QLBillingDataFilter.builder()
                                    .service(QLIdFilter.builder()
                                                 .operator(operator)
                                                 .values(entityIds.toArray(new String[0]))
                                                 .build())
                                    .build());
                    break;
                  case ENVIRONMENT:
                    newList.add(QLBillingDataFilter.builder()
                                    .environment(QLIdFilter.builder()
                                                     .operator(operator)
                                                     .values(entityIds.toArray(new String[0]))
                                                     .build())
                                    .build());
                    break;
                  default:
                    log.error("EntityType {} not supported in query", tagFilter.getEntityType());
                    throw new InvalidRequestException("Error while compiling query", WingsException.USER);
                }
              }
            }
          }
        } else if (type == QLBillingDataFilterType.Label) {
          QLBillingDataLabelFilter labelFilter = filter.getLabel();
          newList.addAll(getEntityFiltersFromLabelFilter(accountId, filters, labelFilter));
        } else if (type == QLBillingDataFilterType.EnvironmentType) {
          newList.add(getEnvironmentIdFilter(filters, filter.getEnvType()));
        } else {
          newList.add(filter);
        }
      }
    }

    return newList;
  }

  private List<QLBillingDataFilter> getEntityFiltersFromLabelFilter(
      String accountId, List<QLBillingDataFilter> filters, QLBillingDataLabelFilter labelFilter) {
    List<QLBillingDataFilter> timeFilters = getTimeFilters(filters);
    long startTime = 0L;
    long endTime = Long.MAX_VALUE;
    for (QLBillingDataFilter filter : timeFilters) {
      if (filter.getStartTime() != null) {
        startTime = Math.max(startTime, filter.getStartTime().getValue().longValue());
      }
      if (filter.getEndTime() != null) {
        endTime = Math.min(endTime, filter.getEndTime().getValue().longValue());
      }
    }

    String clusterId = getClusterIdFromFilters(filters);
    List<QLBillingDataFilter> entityFilters = new ArrayList<>();

    if (labelFilter != null) {
      QLIdOperator operator = labelFilter.getOperator();
      Set<String> workloadNamesWithNamespaces;
      if (!clusterId.equals(EMPTY)) {
        workloadNamesWithNamespaces =
            k8sLabelHelper.getWorkloadNamesWithNamespacesFromLabels(accountId, clusterId, labelFilter);
      } else {
        workloadNamesWithNamespaces =
            k8sLabelHelper.getWorkloadNamesWithNamespacesFromLabels(accountId, startTime, endTime, labelFilter);
      }
      Set<String> workloadNames = new HashSet<>();
      Set<String> namespaces = new HashSet<>();
      workloadNamesWithNamespaces.forEach(workloadNameWithNamespace -> {
        StringTokenizer tokenizer = new StringTokenizer(workloadNameWithNamespace, BillingStatsDefaultKeys.TOKEN);
        workloadNames.add(tokenizer.nextToken());
        namespaces.add(tokenizer.nextToken());
      });
      if (isNotEmpty(workloadNames)) {
        entityFilters.add(
            QLBillingDataFilter.builder()
                .workloadName(
                    QLIdFilter.builder().operator(operator).values(workloadNames.toArray(new String[0])).build())
                .build());
      }
      if (isNotEmpty(namespaces)) {
        entityFilters.add(
            QLBillingDataFilter.builder()
                .namespace(QLIdFilter.builder().operator(operator).values(namespaces.toArray(new String[0])).build())
                .build());
      }
    }
    return entityFilters;
  }

  private QLBillingDataFilter getEnvironmentIdFilter(
      List<QLBillingDataFilter> filters, QLCEEnvironmentTypeFilter environmentTypeFilter) {
    String envType = DEFAULT_ENVIRONMENT_TYPE;
    if (environmentTypeFilter.getValues() != null) {
      envType = (String) environmentTypeFilter.getValues()[0];
    }
    List<String> envIds = environmentService.getEnvIdsByAppsAndType(getAppIdsFromFilter(filters), envType);
    return QLBillingDataFilter.builder()
        .environment(QLIdFilter.builder().operator(QLIdOperator.IN).values(envIds.toArray(new String[0])).build())
        .build();
  }

  private List<String> getAppIdsFromFilter(List<QLBillingDataFilter> filters) {
    for (QLBillingDataFilter filter : filters) {
      if (filter.getApplication() != null) {
        return Arrays.asList(filter.getApplication().getValues());
      }
    }
    return new ArrayList<>();
  }

  public EntityType getEntityType(QLBillingDataTagType entityType) {
    if (entityType == null) {
      return EntityType.APPLICATION;
    }
    switch (entityType) {
      case APPLICATION:
        return EntityType.APPLICATION;
      case SERVICE:
        return EntityType.SERVICE;
      case ENVIRONMENT:
        return EntityType.ENVIRONMENT;
      default:
        log.error("Unsupported entity type {} for tag ", entityType);
        throw new InvalidRequestException("Unsupported entity type " + entityType);
    }
  }

  protected QLCCMEntityGroupBy getGroupByEntityFromTag(QLBillingDataTagAggregation groupByTag) {
    if (groupByTag.getEntityType() == null) {
      return QLCCMEntityGroupBy.Application;
    }
    switch (groupByTag.getEntityType()) {
      case APPLICATION:
        return QLCCMEntityGroupBy.Application;
      case SERVICE:
        return QLCCMEntityGroupBy.Service;
      case ENVIRONMENT:
        return QLCCMEntityGroupBy.Environment;
      default:
        log.warn("Unsupported tag entity type {}", groupByTag.getEntityType());
        throw new InvalidRequestException("Unsupported entity type " + groupByTag.getEntityType());
    }
  }

  protected QLCCMEntityGroupBy getGroupByEntityFromLabel(QLBillingDataLabelAggregation groupByLabel) {
    return QLCCMEntityGroupBy.WorkloadName;
  }

  protected boolean isGroupByEntityPresent(List<QLCCMEntityGroupBy> groupByList, QLCCMEntityGroupBy entityGroupBy) {
    return groupByList.stream().anyMatch(groupBy -> groupBy == entityGroupBy);
  }

  protected String getClusterIdFromFilters(List<QLBillingDataFilter> filters) {
    for (QLBillingDataFilter filter : filters) {
      if (filter.getCluster() != null) {
        return filter.getCluster().getValues()[0];
      }
    }
    return EMPTY;
  }

  private boolean isGroupByHour(QLCCMTimeSeriesAggregation groupByTime) {
    return groupByTime != null && groupByTime.getTimeGroupType() != null
        && groupByTime.getTimeGroupType() == QLTimeGroupType.HOUR;
  }

  private DbColumn getColumnAssociatedWithGroupBy(QLCCMEntityGroupBy groupBy) {
    switch (groupBy) {
      case Application:
        return schema.getAppId();
      case Environment:
        return schema.getEnvId();
      case Service:
        return schema.getServiceId();
      case Node:
      case Pod:
        return schema.getInstanceName();
      case Cluster:
      case ClusterName:
        return schema.getClusterName();
      case ClusterType:
        return schema.getClusterType();
      case WorkloadName:
        return schema.getWorkloadName();
      case Namespace:
        return schema.getNamespace();
      case TaskId:
        return schema.getTaskId();
      case CloudServiceName:
        return schema.getCloudServiceName();
      case LaunchType:
        return schema.getLaunchType();
      case Region:
        return schema.getRegion();
      case CloudProvider:
        return schema.getCloudProviderId();
      default:
        throw new InvalidRequestException("Group by entity not supported in filter values");
    }
  }

  private boolean shouldUseHourlyData(List<QLBillingDataFilter> filters, String accountId) {
    CEMetadataRecord ceMetadataRecord = ceMetadataRecordDao.getByAccountId(accountId);
    if (null != ceMetadataRecord && null != ceMetadataRecord.getAwsDataPresent()
        && ceMetadataRecord.getAwsDataPresent()) {
      return false;
    }
    if (null != ceMetadataRecord && null != ceMetadataRecord.getAzureDataPresent()
        && ceMetadataRecord.getAzureDataPresent()) {
      return false;
    }

    List<QLBillingDataFilter> validFilters =
        filters.stream().filter(filter -> filter.getStartTime() != null).collect(Collectors.toList());
    if (!validFilters.isEmpty()) {
      List<QLTimeFilter> startTimeFilters =
          validFilters.stream().map(QLBillingDataFilter::getStartTime).collect(Collectors.toList());
      List<Number> startTimes =
          startTimeFilters.stream().sorted().map(QLTimeFilter::getValue).collect(Collectors.toList());
      long startTime = startTimes.get(0).longValue();

      ZoneId zoneId = ZoneId.of(STANDARD_TIME_ZONE);
      LocalDate today = LocalDate.now(zoneId);
      ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
      long cutoffTime = zdtStart.toEpochSecond() * 1000 - 7 * ONE_DAY_MILLIS;

      if (startTime >= cutoffTime) {
        return true;
      }
    }
    return false;
  }

  private List<QLBillingDataFilter> getTimeFilters(List<QLBillingDataFilter> filters) {
    return filters.stream()
        .filter(qlBillingDataFilter
            -> qlBillingDataFilter.getStartTime() != null || qlBillingDataFilter.getEndTime() != null)
        .collect(Collectors.toList());
  }

  private void addFieldsForPodCountDataFetcher(
      SelectQuery selectQuery, List<CeActivePodCountMetaDataFields> fieldNames) {
    selectQuery.addColumns(podTableSchema.getStartTime());
    fieldNames.add(CeActivePodCountMetaDataFields.STARTTIME);
    selectQuery.addColumns(podTableSchema.getClusterId());
    fieldNames.add(CeActivePodCountMetaDataFields.CLUSTERID);
    selectQuery.addColumns(podTableSchema.getInstanceId());
    fieldNames.add(CeActivePodCountMetaDataFields.INSTANCEID);
    selectQuery.addColumns(podTableSchema.getPodCount());
    fieldNames.add(CeActivePodCountMetaDataFields.PODCOUNT);
  }

  protected boolean isClusterDrilldown(List<QLCCMEntityGroupBy> groupByList) {
    return groupByList.stream().anyMatch(groupBy
        -> groupBy == QLCCMEntityGroupBy.WorkloadName || groupBy == QLCCMEntityGroupBy.Namespace
            || groupBy == QLCCMEntityGroupBy.CloudServiceName || groupBy == QLCCMEntityGroupBy.TaskId
            || groupBy == QLCCMEntityGroupBy.LaunchType);
  }

  protected boolean isApplicationDrillDown(List<QLCCMEntityGroupBy> groupByList) {
    return groupByList.stream().anyMatch(
        groupBy -> groupBy == QLCCMEntityGroupBy.Service || groupBy == QLCCMEntityGroupBy.Environment);
  }

  protected boolean showUnallocatedCost(List<QLCCMEntityGroupBy> groupBy, List<QLBillingDataFilter> filters) {
    boolean isClusterDrillDown = isClusterDrilldown(groupBy);
    boolean showUnallocated = false;
    boolean filterPresent = false;
    List<String> values = new ArrayList<>();
    for (QLBillingDataFilter filter : filters) {
      if (filter.getWorkloadName() != null) {
        values.addAll(Arrays.asList(filter.getWorkloadName().getValues()));
        // For workload drill-down
        if ((filter.getWorkloadName().getOperator() == QLIdOperator.IN
                || filter.getWorkloadName().getOperator() == QLIdOperator.EQUALS)
            && filter.getWorkloadName().getValues().length != 0) {
          filterPresent = true;
        }
      }
      if (filter.getNamespace() != null) {
        values.addAll(Arrays.asList(filter.getNamespace().getValues()));
      }
      if (filter.getCloudServiceName() != null) {
        values.addAll(Arrays.asList(filter.getCloudServiceName().getValues()));
      }
      if (filter.getLaunchType() != null) {
        values.addAll(Arrays.asList(filter.getLaunchType().getValues()));
      }
      if (filter.getTaskId() != null) {
        values.addAll(Arrays.asList(filter.getTaskId().getValues()));
      }
    }
    showUnallocated = !values.contains(UNALLOCATED);
    return isClusterDrillDown && showUnallocated && !filterPresent;
  }

  protected List<QLCCMEntityGroupBy> getGroupByOrderedByDrillDown(List<QLCCMEntityGroupBy> groupByEntityList) {
    List<QLCCMEntityGroupBy> reorderedList = new ArrayList<>();

    // This is to reorder group by entity list if group by cloudServiceName/launchType/task is present
    // to obtain relevant ID in response
    List<QLCCMEntityGroupBy> ecsEntitiesReorderedList = new ArrayList<>();
    boolean isCloudServiceNamePresent = false;
    boolean isLaunchTypePresent = false;
    boolean isTaskIdPresent = false;

    for (QLCCMEntityGroupBy entityGroupBy : groupByEntityList) {
      switch (entityGroupBy) {
        case LaunchType:
          isLaunchTypePresent = true;
          break;
        case CloudServiceName:
          isCloudServiceNamePresent = true;
          break;
        case TaskId:
          isTaskIdPresent = true;
          break;
        default:
          ecsEntitiesReorderedList.add(entityGroupBy);
          break;
      }
    }

    if (isLaunchTypePresent) {
      ecsEntitiesReorderedList.add(QLCCMEntityGroupBy.LaunchType);
    }
    if (isCloudServiceNamePresent) {
      ecsEntitiesReorderedList.add(QLCCMEntityGroupBy.CloudServiceName);
    }
    if (isTaskIdPresent) {
      ecsEntitiesReorderedList.add(QLCCMEntityGroupBy.TaskId);
    }

    // This is to first group by cluster/ application
    if (ecsEntitiesReorderedList.contains(QLCCMEntityGroupBy.Cluster)) {
      reorderedList.add(QLCCMEntityGroupBy.Cluster);
      for (QLCCMEntityGroupBy groupBy : ecsEntitiesReorderedList) {
        if (groupBy != QLCCMEntityGroupBy.Cluster) {
          reorderedList.add(groupBy);
        }
      }
    } else if (ecsEntitiesReorderedList.contains(QLCCMEntityGroupBy.Application)) {
      reorderedList.add(QLCCMEntityGroupBy.Application);
      for (QLCCMEntityGroupBy groupBy : ecsEntitiesReorderedList) {
        if (groupBy != QLCCMEntityGroupBy.Application) {
          reorderedList.add(groupBy);
        }
      }
    } else {
      reorderedList = ecsEntitiesReorderedList;
    }
    return reorderedList;
  }

  // In case of group by namespace/workload/labels, change instanceId filter to parentInstanceId filter
  private List<QLBillingDataFilter> getUpdatedInstanceIdFilter(
      List<QLBillingDataFilter> filters, List<QLCCMEntityGroupBy> groupBy) {
    List<QLBillingDataFilter> updatedFilters = new ArrayList<>();
    if (groupBy.stream().anyMatch(
            entry -> entry == QLCCMEntityGroupBy.WorkloadName || entry == QLCCMEntityGroupBy.Namespace)) {
      for (QLBillingDataFilter filter : filters) {
        if (filter.getNodeInstanceId() != null) {
          QLIdFilter filterValues = filter.getNodeInstanceId();
          updatedFilters.add(QLBillingDataFilter.builder()
                                 .parentInstanceId(QLIdFilter.builder()
                                                       .operator(filterValues.getOperator())
                                                       .values(filterValues.getValues())
                                                       .build())
                                 .build());
        } else {
          updatedFilters.add(filter);
        }
      }
      log.info("Updated filters {}", updatedFilters);
      return updatedFilters;
    }
    return filters;
  }

  protected boolean isFilterCombinationValid(List<QLBillingDataFilter> filters, List<QLCCMEntityGroupBy> groupBy) {
    if (groupBy.stream().anyMatch(
            entry -> entry == QLCCMEntityGroupBy.WorkloadName || entry == QLCCMEntityGroupBy.Namespace)) {
      return !filters.stream().anyMatch(filter
          -> filter.getCloudServiceName() != null || filter.getLaunchType() != null || filter.getTaskId() != null);

    } else if (groupBy.stream().anyMatch(entry -> entry == QLCCMEntityGroupBy.Node)) {
      return !filters.stream().anyMatch(filter
          -> filter.getNodeInstanceId() == null && filter.getCluster() == null && filter.getInstanceType() == null
              && filter.getStartTime() == null && filter.getEndTime() == null);

    } else if (groupBy.stream().anyMatch(entry
                   -> entry == QLCCMEntityGroupBy.CloudServiceName || entry == QLCCMEntityGroupBy.TaskId
                       || entry == QLCCMEntityGroupBy.LaunchType)) {
      return !filters.stream().anyMatch(filter
          -> filter.getWorkloadName() != null || filter.getNamespace() != null || filter.getLabel() != null
              || filter.getNodeInstanceId() != null);
    } else if (groupBy.stream().anyMatch(entry
                   -> entry == QLCCMEntityGroupBy.Application || entry == QLCCMEntityGroupBy.Service
                       || entry == QLCCMEntityGroupBy.Environment || entry == QLCCMEntityGroupBy.CloudProvider)) {
      return !filters.stream().anyMatch(filter -> filter.getNodeInstanceId() != null);
    }
    return true;
  }

  protected void addFiltersToExcludeUnallocatedRows(
      List<QLBillingDataFilter> filters, List<QLCCMEntityGroupBy> groupBy) {
    String[] values = {UNALLOCATED};
    for (QLCCMEntityGroupBy entityGroupBy : groupBy) {
      switch (entityGroupBy) {
        case WorkloadName:
          filters.add(QLBillingDataFilter.builder()
                          .workloadName(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(values).build())
                          .build());
          break;
        case Namespace:
          filters.add(QLBillingDataFilter.builder()
                          .namespace(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(values).build())
                          .build());
          break;
        case CloudServiceName:
          filters.add(QLBillingDataFilter.builder()
                          .cloudServiceName(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(values).build())
                          .build());
          break;
        case TaskId:
          filters.add(QLBillingDataFilter.builder()
                          .taskId(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(values).build())
                          .build());
          break;
        case LaunchType:
          filters.add(QLBillingDataFilter.builder()
                          .launchType(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(values).build())
                          .build());
          break;
        default:
          break;
      }
    }
  }

  protected List<QLBillingDataFilter> removeInstanceTypeFilter(List<QLBillingDataFilter> filters) {
    return filters.stream().filter(filter -> filter.getInstanceType() == null).collect(Collectors.toList());
  }

  // Checking if any non supported group by for pre-aggregation is present
  private boolean isValidGroupByForPreAggregation(List<QLCCMEntityGroupBy> entityGroupBy) {
    return !entityGroupBy.stream().anyMatch(groupBy
        -> groupBy == QLCCMEntityGroupBy.Pod || groupBy == QLCCMEntityGroupBy.PV
            || groupBy == QLCCMEntityGroupBy.CloudServiceName || groupBy == QLCCMEntityGroupBy.TaskId
            || groupBy == QLCCMEntityGroupBy.LaunchType);
  }

  private boolean areFiltersValidForPreAggregation(List<QLBillingDataFilter> filters) {
    return !filters.stream().anyMatch(filter
        -> filter.getNodeInstanceId() != null || filter.getTaskId() != null || filter.getLaunchType() != null
            || filter.getCloudServiceName() != null || filter.getParentInstanceId() != null
            || filter.getInstanceName() != null);
  }

  private boolean areAggregationsValidForPreAggregation(List<QLCCMAggregationFunction> aggregateFunctions) {
    return !aggregateFunctions.stream().anyMatch(aggregationFunction
        -> aggregationFunction.getColumnName().equals(schema.getEffectiveCpuLimit().getColumnNameSQL())
            || aggregationFunction.getColumnName().equals(schema.getEffectiveCpuRequest().getColumnNameSQL())
            || aggregationFunction.getColumnName().equals(schema.getEffectiveMemoryLimit().getColumnNameSQL())
            || aggregationFunction.getColumnName().equals(schema.getEffectiveMemoryRequest().getColumnNameSQL())
            || aggregationFunction.getColumnName().equals(schema.getEffectiveCpuUtilizationValue().getColumnNameSQL())
            || aggregationFunction.getColumnName().equals(
                schema.getEffectiveMemoryUtilizationValue().getColumnNameSQL()));
  }

  private List<QLCCMAggregationFunction> getSupportedAggregations(List<QLCCMAggregationFunction> aggregationFunctions) {
    return aggregationFunctions.stream()
        .filter(aggregationFunction
            -> aggregationFunction.getColumnName().equalsIgnoreCase(schema.getBillingAmount().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(schema.getIdleCost().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(schema.getUnallocatedCost().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(schema.getCpuBillingAmount().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(schema.getCpuIdleCost().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(
                    schema.getCpuActualIdleCost().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(
                    schema.getCpuUnallocatedCost().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(schema.getStorageCost().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(
                    schema.getStorageActualIdleCost().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(
                    schema.getStorageUnallocatedCost().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(
                    schema.getMemoryBillingAmount().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(schema.getMemoryIdleCost().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(
                    schema.getMemoryActualIdleCost().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(
                    schema.getMemoryUnallocatedCost().getColumnNameSQL())
                || aggregationFunction.getColumnName().equalsIgnoreCase(schema.getSystemCost().getColumnNameSQL()))
        .collect(Collectors.toList());
  }

  //-------------- Anomaly Detection -------------

  public String formADQuery(String accountId, List<QLBillingDataFilter> filters,
      List<QLCCMAggregationFunction> aggregateFunction, List<QLCCMEntityGroupBy> groupBy,
      List<QLBillingSortCriteria> sortCriteria, List<DbColumn> selectColumns) {
    SelectQuery query = new SelectQuery();

    query.addCustomColumns(getHashColumn(groupBy));
    query.addCustomColumns(
        AliasedObject.toAliasedObject(FunctionCall.sum().addColumnParams(schema.getBillingAmount()), "cost"));
    addAccountFilter(query, accountId);

    for (DbColumn column : selectColumns) {
      query.addColumns(column);
    }

    List<BillingDataQueryMetadata.BillingDataMetaDataFields> fieldNames = new ArrayList<>();
    List<BillingDataQueryMetadata.BillingDataMetaDataFields> groupByFields = new ArrayList<>();

    decorateQueryWithAggregations(query, aggregateFunction, fieldNames);

    if (isValidGroupBy(groupBy)) {
      decorateQueryWithGroupBy(fieldNames, query, groupBy, groupByFields);
    }

    if (!Lists.isNullOrEmpty(filters)) {
      decorateQueryWithFilters(query, filters);
    }

    validateAndAddSortCriteria(query, sortCriteria, fieldNames);

    return query.toString();
  }

  public SqlObject getHashColumn(List<QLCCMEntityGroupBy> groupByList) {
    FunctionCall concatFunction = new FunctionCall(new CustomSql("CONCAT"));
    FunctionCall md5HashFunction = new FunctionCall(new CustomSql("MD5"));

    DbColumn column;

    for (QLCCMEntityGroupBy groupBy : groupByList) {
      column = convertEntityGroupByToDbColumn(groupBy);
      if (column != null) {
        concatFunction.addColumnParams(column);
      }
    }
    md5HashFunction.addCustomParams(new CustomSql(concatFunction.toString()));
    return AliasedObject.toAliasedObject(md5HashFunction, "hashcode");
  }

  public DbColumn convertEntityGroupByToDbColumn(QLCCMEntityGroupBy groupBy) {
    switch (groupBy) {
      case Cluster:
        return schema.getClusterId();
      case Namespace:
        return schema.getNamespace();
      case WorkloadName:
        return schema.getWorkloadName();
      case Service:
        return schema.getServiceId();
      case ClusterName:
      case StartTime:
        return null;
      default:
        log.warn("group by : {} is not used for hashing while query building", groupBy);
        throw new InvalidArgumentsException("GroupBy not supported in conversion of hash");
    }
  }
}
