/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.ce.exportData;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.EntityType;
import software.wings.graphql.datafetcher.billing.BillingStatsDefaultKeys;
import software.wings.graphql.datafetcher.ce.exportData.CEExportDataQueryMetadata.CEExportDataMetadataFields;
import software.wings.graphql.datafetcher.ce.exportData.CEExportDataQueryMetadata.CEExportDataQueryMetadataBuilder;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEAggregation;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEAggregationFunction;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEDataEntry.CEDataEntryKeys;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEEcsEntity.CEEcsEntityKeys;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEEntityGroupBy;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEFilter;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEFilterType;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEGroupBy;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEHarnessEntity.CEHarnessEntityKeys;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCEK8sEntity.CEK8sEntityKeys;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCELabelFilter;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCESort;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCESortType;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCETagFilter;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCETagType;
import software.wings.graphql.datafetcher.ce.exportData.dto.QLCETimeAggregation;
import software.wings.graphql.datafetcher.k8sLabel.K8sLabelHelper;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.Filter;
import software.wings.graphql.schema.type.aggregation.QLAggregationKind;
import software.wings.graphql.schema.type.aggregation.QLFilterKind;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLSortOrder;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataLabelFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLTimeGroupType;

import com.google.inject.Inject;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.Converter;
import com.healthmarketscience.sqlbuilder.CustomExpression;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UnaryCondition;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class CEExportDataQueryBuilder {
  private CEExportDataTableSchema schema = new CEExportDataTableSchema();
  private static final String STANDARD_TIME_ZONE = "GMT";
  public static final String BILLING_DATA_HOURLY_TABLE = "billing_data_hourly t0";
  public static final long ONE_DAY_MILLISEC = 86400000L;
  @Inject TagHelper tagHelper;
  @Inject K8sLabelHelper k8sLabelHelper;

  protected CEExportDataQueryMetadata formQuery(String accountId, List<QLCEFilter> filters,
      List<QLCEAggregation> aggregateFunction, List<QLCEEntityGroupBy> groupBy, QLCETimeAggregation groupByTime,
      List<QLCESort> sortCriteria, Integer limit, Integer offset, List<String> selectedFields) {
    CEExportDataQueryMetadataBuilder queryMetaDataBuilder = CEExportDataQueryMetadata.builder();
    SelectQuery selectQuery = new SelectQuery();
    selectQuery.setFetchNext(limit);
    selectQuery.setOffset(offset);

    List<CEExportDataMetadataFields> fieldNames = new ArrayList<>();
    List<CEExportDataMetadataFields> groupByFields = new ArrayList<>();

    if (isGroupByEntityPresent(groupBy, QLCEEntityGroupBy.Cluster) && !isClusterDrillDown(groupBy)) {
      addInstanceTypeFilter(filters);
    }

    // To handle the cases of same workloadNames across different namespaces
    if (isGroupByEntityPresent(groupBy, QLCEEntityGroupBy.Workload)
        && !isGroupByEntityPresent(groupBy, QLCEEntityGroupBy.Namespace)) {
      groupBy.add(0, QLCEEntityGroupBy.Namespace);
    }

    if (isGroupByEntityPresent(groupBy, QLCEEntityGroupBy.Namespace)) {
      filters.add(
          QLCEFilter.builder()
              .instanceType(QLIdFilter.builder().operator(QLIdOperator.NOT_IN).values(new String[] {"K8S_PV"}).build())
              .build());
    }

    decorateQueryWithAggregations(selectQuery, aggregateFunction, fieldNames);

    if (!isGroupByHour(groupByTime)) {
      selectQuery.addCustomFromTable(schema.getBillingDataTable());
    } else {
      selectQuery.addCustomFromTable(BILLING_DATA_HOURLY_TABLE);
    }

    if (isValidGroupByTime(groupByTime)) {
      decorateQueryWithGroupByTime(fieldNames, selectQuery, groupByTime, groupByFields);
    }

    if (isValidGroupBy(groupBy)) {
      decorateQueryWithGroupBy(fieldNames, selectQuery, groupBy, groupByFields, filters);
    }

    if (!isValidGroupByTime(groupByTime) && !isValidGroupBy(groupBy)) {
      addSelectedColumns(selectQuery, fieldNames, selectedFields);
    }

    if (!isEmpty(filters)) {
      filters = processFilterForTagsAndLabels(accountId, filters);
      decorateQueryWithFilters(selectQuery, filters);
    }

    sortCriteria = addSortBasedOnGroupBy(sortCriteria, groupBy);
    List<QLCESort> finalSortCriteria = validateAndAddSortCriteria(selectQuery, sortCriteria, fieldNames);
    addAccountFilter(selectQuery, accountId);
    selectQuery.getWhereClause().setDisableParens(true);
    queryMetaDataBuilder.fieldNames(fieldNames);
    queryMetaDataBuilder.query(selectQuery.toString());
    queryMetaDataBuilder.groupByFields(groupByFields);
    queryMetaDataBuilder.sortCriteria(finalSortCriteria);
    queryMetaDataBuilder.filters(filters);
    return queryMetaDataBuilder.build();
  }

  protected boolean isGroupByEntityPresent(List<QLCEEntityGroupBy> groupByList, QLCEEntityGroupBy entityGroupBy) {
    return groupByList.stream().anyMatch(groupBy -> groupBy == entityGroupBy);
  }

  private void addInstanceTypeFilter(List<QLCEFilter> filters) {
    if (!isInstanceTypeFilterPresent(filters)) {
      List<String> instanceTypeValues = new ArrayList<>();
      instanceTypeValues.add("ECS_TASK_FARGATE");
      instanceTypeValues.add("ECS_CONTAINER_INSTANCE");
      instanceTypeValues.add("K8S_NODE");
      instanceTypeValues.add("K8S_POD_FARGATE");
      addInstanceTypeFilter(filters, instanceTypeValues);
    }
  }

  private boolean isInstanceTypeFilterPresent(List<QLCEFilter> filters) {
    return filters.stream().anyMatch(filter -> filter.getInstanceType() != null);
  }

  private void addInstanceTypeFilter(List<QLCEFilter> filters, List<String> instanceTypeValues) {
    QLCEFilter instanceTypeFilter = QLCEFilter.builder()
                                        .instanceType(QLIdFilter.builder()
                                                          .operator(QLIdOperator.EQUALS)
                                                          .values(instanceTypeValues.toArray(new String[0]))
                                                          .build())
                                        .build();
    filters.add(instanceTypeFilter);
  }

  private boolean isGroupByHour(QLCETimeAggregation groupByTime) {
    return groupByTime != null && groupByTime.getTimePeriod() != null
        && groupByTime.getTimePeriod() == QLTimeGroupType.HOUR;
  }

  private boolean isValidGroupByTime(QLCETimeAggregation groupByTime) {
    return groupByTime != null && groupByTime.getTimePeriod() != null;
  }

  private boolean isValidGroupBy(List<QLCEEntityGroupBy> groupBy) {
    return EmptyPredicate.isNotEmpty(groupBy) && groupBy.size() <= 5;
  }

  protected List<QLCEEntityGroupBy> getGroupByEntity(List<QLCEGroupBy> groupBy) {
    return groupBy != null
        ? groupBy.stream().filter(g -> g.getEntity() != null).map(QLCEGroupBy::getEntity).collect(Collectors.toList())
        : Collections.emptyList();
  }

  protected QLCETimeAggregation getGroupByTime(List<QLCEGroupBy> groupBy) {
    if (groupBy != null) {
      Optional<QLCETimeAggregation> first =
          groupBy.stream().filter(g -> g.getTime() != null).map(QLCEGroupBy::getTime).findFirst();
      return first.orElse(null);
    }
    return null;
  }

  protected double roundingDoubleFieldValue(CEExportDataMetadataFields field, ResultSet resultSet) throws SQLException {
    return Math.round(resultSet.getDouble(field.getFieldName()) * 100D) / 100D;
  }

  // Methods for applying aggregation

  private void decorateQueryWithAggregations(
      SelectQuery selectQuery, List<QLCEAggregation> aggregateFunctions, List<CEExportDataMetadataFields> fieldNames) {
    for (QLCEAggregation aggregationFunction : aggregateFunctions) {
      decorateQueryWithAggregation(selectQuery, aggregationFunction, fieldNames);
    }
  }

  private void decorateQueryWithAggregation(
      SelectQuery selectQuery, QLCEAggregation aggregationFunction, List<CEExportDataMetadataFields> fieldNames) {
    if (aggregationFunction != null && aggregationFunction.getFunction() == QLCEAggregationFunction.SUM) {
      switch (aggregationFunction.getCost()) {
        case TOTALCOST:
          selectQuery.addCustomColumns(
              Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getBillingAmount()),
                  CEExportDataMetadataFields.SUM.getFieldName()));
          fieldNames.add(CEExportDataMetadataFields.SUM);
          break;
        case IDLECOST:
          selectQuery.addCustomColumns(
              Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getIdleCost()),
                  CEExportDataMetadataFields.IDLECOST.getFieldName()));
          fieldNames.add(CEExportDataMetadataFields.IDLECOST);
          break;
        case UNALLOCATEDCOST:
          selectQuery.addCustomColumns(
              Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getUnallocatedCost()),
                  CEExportDataMetadataFields.UNALLOCATEDCOST.getFieldName()));
          fieldNames.add(CEExportDataMetadataFields.UNALLOCATEDCOST);
          break;
        default:
          break;
      }
    } else if (aggregationFunction != null && aggregationFunction.getFunction() == QLCEAggregationFunction.AVG) {
      switch (aggregationFunction.getUtilization()) {
        case CPU_LIMIT:
          selectQuery.addCustomColumns(
              Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getEffectiveCpuLimit()),
                  CEExportDataMetadataFields.AGGREGATEDCPULIMIT.getFieldName()));
          fieldNames.add(CEExportDataMetadataFields.AGGREGATEDCPULIMIT);
          break;
        case CPU_REQUEST:
          selectQuery.addCustomColumns(
              Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getEffectiveCpuRequest()),
                  CEExportDataMetadataFields.AGGREGATEDCPUREQUEST.getFieldName()));
          fieldNames.add(CEExportDataMetadataFields.AGGREGATEDCPUREQUEST);
          break;
        case CPU_UTILIZATION_VALUE:
          selectQuery.addCustomColumns(
              Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getEffectiveCpuUtilizationValue()),
                  CEExportDataMetadataFields.AGGREGATEDCPUUTILIZATIONVALUE.getFieldName()));
          fieldNames.add(CEExportDataMetadataFields.AGGREGATEDCPUUTILIZATIONVALUE);
          break;
        case MEMORY_LIMIT:
          selectQuery.addCustomColumns(
              Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getEffectiveMemoryLimit()),
                  CEExportDataMetadataFields.AGGREGATEDMEMORYLIMIT.getFieldName()));
          fieldNames.add(CEExportDataMetadataFields.AGGREGATEDMEMORYLIMIT);
          break;
        case MEMORY_REQUEST:
          selectQuery.addCustomColumns(
              Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(schema.getEffectiveMemoryRequest()),
                  CEExportDataMetadataFields.AGGREGATEDMEMORYREQUEST.getFieldName()));
          fieldNames.add(CEExportDataMetadataFields.AGGREGATEDMEMORYREQUEST);
          break;
        case MEMORY_UTILIZATION_VALUE:
          selectQuery.addCustomColumns(Converter.toColumnSqlObject(
              FunctionCall.sum().addColumnParams(schema.getEffectiveMemoryUtilizationValue()),
              CEExportDataMetadataFields.AGGREGATEDMEMORYUTILIZATIONVALUE.getFieldName()));
          fieldNames.add(CEExportDataMetadataFields.AGGREGATEDMEMORYUTILIZATIONVALUE);
          break;
        default:
          break;
      }
    }
  }

  // Methods for applying group by
  // 1. Group by time

  private void decorateQueryWithGroupByTime(List<CEExportDataMetadataFields> fieldNames, SelectQuery selectQuery,
      QLCETimeAggregation groupByTime, List<CEExportDataMetadataFields> groupByFields) {
    String timeBucket = getGroupByTimeQueryWithDateTrunc(groupByTime, "starttime");

    selectQuery.addCustomColumns(
        Converter.toCustomColumnSqlObject(new CustomExpression(timeBucket).setDisableParens(true),
            CEExportDataMetadataFields.TIME_SERIES.getFieldName()));
    selectQuery.addCustomGroupings(CEExportDataMetadataFields.TIME_SERIES.getFieldName());
    selectQuery.addCustomOrdering(CEExportDataMetadataFields.TIME_SERIES.getFieldName(), OrderObject.Dir.ASCENDING);
    fieldNames.add(CEExportDataMetadataFields.TIME_SERIES);
    groupByFields.add(CEExportDataMetadataFields.TIME_SERIES);
  }

  public String getGroupByTimeQueryWithDateTrunc(QLCETimeAggregation groupByTime, String dbFieldName) {
    String unit;
    switch (groupByTime.getTimePeriod()) {
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
        log.warn("Unsupported timeGroupType " + groupByTime.getTimePeriod());
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

  // 2. Group by entity

  private void decorateQueryWithGroupBy(List<CEExportDataMetadataFields> fieldNames, SelectQuery selectQuery,
      List<QLCEEntityGroupBy> groupBy, List<CEExportDataMetadataFields> groupByFields, List<QLCEFilter> filters) {
    for (QLCEEntityGroupBy aggregation : groupBy) {
      if (aggregation.getAggregationKind() == QLAggregationKind.SIMPLE) {
        decorateSimpleGroupBy(fieldNames, selectQuery, aggregation, groupByFields, filters);
      }
    }
  }

  // TODO change here
  private void decorateSimpleGroupBy(List<CEExportDataMetadataFields> fieldNames, SelectQuery selectQuery,
      QLCEEntityGroupBy aggregation, List<CEExportDataMetadataFields> groupByFields, List<QLCEFilter> filters) {
    DbColumn groupBy;
    switch (aggregation) {
      case Application:
        groupBy = schema.getAppId();
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
      case Environment:
        groupBy = schema.getEnvId();
        break;
      case EcsService:
        groupBy = schema.getCloudServiceName();
        break;
      case Task:
        groupBy = schema.getTaskId();
        break;
      case LaunchType:
        groupBy = schema.getLaunchType();
        break;
      case Workload:
        groupBy = schema.getWorkloadName();
        break;
      case WorkloadType:
        groupBy = schema.getWorkloadType();
        break;
      case Namespace:
        groupBy = schema.getNamespace();
        break;
      case Pod:
        groupBy = schema.getInstanceId();
        filters.add(QLCEFilter.builder()
                        .instanceType(
                            QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"K8S_POD"}).build())
                        .build());
        break;
      case Node:
        groupBy = schema.getInstanceId();
        filters.add(
            QLCEFilter.builder()
                .instanceType(
                    QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"K8S_NODE"}).build())
                .build());
        break;
      default:
        throw new InvalidRequestException("Invalid groupBy clause");
    }
    selectQuery.addColumns(groupBy);
    selectQuery.addGroupings(groupBy);
    fieldNames.add(CEExportDataMetadataFields.valueOf(groupBy.getName().toUpperCase()));
    selectQuery.addCondition(UnaryCondition.isNotNull(groupBy));
    groupByFields.add(CEExportDataMetadataFields.valueOf(groupBy.getName().toUpperCase()));
    // To fetch instance name from timescaleDb
    if (groupBy == schema.getInstanceId()) {
      selectQuery.addColumns(schema.getInstanceName());
      selectQuery.addGroupings(schema.getInstanceName());
      fieldNames.add(CEExportDataMetadataFields.valueOf(schema.getInstanceName().getName().toUpperCase()));
      selectQuery.addCondition(UnaryCondition.isNotNull(schema.getInstanceName()));
      groupByFields.add(CEExportDataMetadataFields.valueOf(schema.getInstanceName().getName().toUpperCase()));
    }
  }

  // Methods for applying filters

  private void decorateQueryWithFilters(SelectQuery selectQuery, List<QLCEFilter> filters) {
    for (QLCEFilter filter : filters) {
      Set<QLCEFilterType> filterTypes = QLCEFilter.getFilterTypes(filter);
      for (QLCEFilterType type : filterTypes) {
        if (type.getMetaDataFields().getFilterKind() == QLFilterKind.SIMPLE) {
          decorateSimpleFilter(selectQuery, filter, type);
        } else {
          log.error("Failed to apply filter :[{}]", filter);
        }
      }
    }
  }

  private void decorateSimpleFilter(SelectQuery selectQuery, QLCEFilter filter, QLCEFilterType type) {
    Filter f = QLCEFilter.getFilter(type, filter);
    if (checkFilter(f)) {
      if (isIdFilter(f)) {
        addSimpleIdOperator(selectQuery, f, type);
      } else if (isTimeFilter(f)) {
        addSimpleTimeFilter(selectQuery, f, type);
      }
    } else {
      log.info("Not adding filter since it is not valid " + f);
    }
  }

  private void addSimpleTimeFilter(SelectQuery selectQuery, Filter filter, QLCEFilterType type) {
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

  private void addSimpleIdOperator(SelectQuery selectQuery, Filter filter, QLCEFilterType type) {
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

  private DbColumn getFilterKey(QLCEFilterType type) {
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
      case EcsService:
        return schema.getCloudServiceName();
      case LaunchType:
        return schema.getLaunchType();
      case Task:
        return schema.getTaskId();
      case InstanceType:
        return schema.getInstanceType();
      case Workload:
        return schema.getWorkloadName();
      case Namespace:
        return schema.getNamespace();
      case Node:
      case Pod:
        return schema.getInstanceId();
      default:
        throw new InvalidRequestException("Filter type not supported " + type);
    }
  }

  private void addAccountFilter(SelectQuery selectQuery, String accountId) {
    selectQuery.addCondition(BinaryCondition.equalTo(schema.getAccountId(), accountId));
  }

  private List<QLCEFilter> processFilterForTagsAndLabels(String accountId, List<QLCEFilter> filters) {
    List<QLCEFilter> newList = new ArrayList<>();
    for (QLCEFilter filter : filters) {
      Set<QLCEFilterType> filterTypes = QLCEFilter.getFilterTypes(filter);
      for (QLCEFilterType type : filterTypes) {
        if (type == QLCEFilterType.Tag) {
          QLCETagFilter tagFilter = filter.getTag();

          if (tagFilter != null) {
            Set<String> entityIds = tagHelper.getEntityIdsFromTags(
                accountId, tagFilter.getTags(), getEntityType(tagFilter.getEntityType()));
            if (isNotEmpty(entityIds)) {
              switch (tagFilter.getEntityType()) {
                case APPLICATION:
                  newList.add(QLCEFilter.builder()
                                  .application(QLIdFilter.builder()
                                                   .operator(QLIdOperator.IN)
                                                   .values(entityIds.toArray(new String[0]))
                                                   .build())
                                  .build());
                  break;
                case SERVICE:
                  newList.add(QLCEFilter.builder()
                                  .service(QLIdFilter.builder()
                                               .operator(QLIdOperator.IN)
                                               .values(entityIds.toArray(new String[0]))
                                               .build())
                                  .build());
                  break;
                case ENVIRONMENT:
                  newList.add(QLCEFilter.builder()
                                  .environment(QLIdFilter.builder()
                                                   .operator(QLIdOperator.IN)
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
        } else if (type == QLCEFilterType.Label) {
          QLCELabelFilter labelFilter = filter.getLabel();
          String clusterId = getClusterIdFromFilters(filters);
          if (labelFilter != null) {
            Set<String> workloadNamesWithNamespaces = k8sLabelHelper.getWorkloadNamesWithNamespacesFromLabels(
                accountId, clusterId, QLBillingDataLabelFilter.builder().labels(labelFilter.getLabels()).build());
            Set<String> workloadNames = new HashSet<>();
            Set<String> namespaces = new HashSet<>();
            workloadNamesWithNamespaces.forEach(workloadNameWithNamespace -> {
              StringTokenizer tokenizer = new StringTokenizer(workloadNameWithNamespace, BillingStatsDefaultKeys.TOKEN);
              workloadNames.add(tokenizer.nextToken());
              namespaces.add(tokenizer.nextToken());
            });
            if (isNotEmpty(workloadNames)) {
              newList.add(QLCEFilter.builder()
                              .workload(QLIdFilter.builder()
                                            .operator(QLIdOperator.IN)
                                            .values(workloadNames.toArray(new String[0]))
                                            .build())
                              .build());
            }
            if (isNotEmpty(namespaces)) {
              newList.add(QLCEFilter.builder()
                              .namespace(QLIdFilter.builder()
                                             .operator(QLIdOperator.IN)
                                             .values(namespaces.toArray(new String[0]))
                                             .build())
                              .build());
            }
          }
        } else {
          newList.add(filter);
        }
      }
    }

    return newList;
  }

  protected EntityType getEntityType(QLCETagType entityType) {
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

  protected String getClusterIdFromFilters(List<QLCEFilter> filters) {
    for (QLCEFilter filter : filters) {
      if (filter.getCluster() != null) {
        return filter.getCluster().getValues()[0];
      }
    }
    return "";
  }

  // Methods for applying Sorting

  private List<QLCESort> addSortBasedOnGroupBy(List<QLCESort> sortCriteria, List<QLCEEntityGroupBy> groupBy) {
    List<QLCESort> updatedSortCriteria = new ArrayList<>();
    sortCriteria.forEach(sort -> updatedSortCriteria.add(sort));
    for (QLCEEntityGroupBy entityGroupBy : groupBy) {
      switch (entityGroupBy) {
        case Cluster:
          updatedSortCriteria.add(
              QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.CLUSTER).build());
          break;
        case Namespace:
          updatedSortCriteria.add(
              QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.NAMESPACE).build());
          break;
        case Workload:
          updatedSortCriteria.add(
              QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.WORKLOAD).build());
          break;
        case WorkloadType:
          updatedSortCriteria.add(
              QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.WORKLOADTYPE).build());
          break;
        case Pod:
        case Node:
          updatedSortCriteria.add(
              QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.INSTANCE).build());
          break;
        case Application:
          updatedSortCriteria.add(
              QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.APPLICATION).build());
          break;
        case Service:
          updatedSortCriteria.add(
              QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.SERVICE).build());
          break;
        case Environment:
          updatedSortCriteria.add(
              QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.ENVIRONMENT).build());
          break;
        case Task:
          updatedSortCriteria.add(QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.TASK).build());
          break;
        case EcsService:
          updatedSortCriteria.add(
              QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.ECS_SERVICE).build());
          break;
        case LaunchType:
          updatedSortCriteria.add(
              QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.LAUNCHTYPE).build());
          break;
        case Region:
          updatedSortCriteria.add(
              QLCESort.builder().order(QLSortOrder.ASCENDING).sortType(QLCESortType.REGION).build());
          break;
        default:
          break;
      }
    }
    return updatedSortCriteria;
  }

  private List<QLCESort> validateAndAddSortCriteria(
      SelectQuery selectQuery, List<QLCESort> sortCriteria, List<CEExportDataMetadataFields> fieldNames) {
    if (isEmpty(sortCriteria)) {
      return new ArrayList<>();
    }

    sortCriteria.removeIf(qlBillingSortCriteria
        -> qlBillingSortCriteria.getOrder() == null
            || !fieldNames.contains(qlBillingSortCriteria.getSortType().getBillingMetaData()));

    if (EmptyPredicate.isNotEmpty(sortCriteria)) {
      sortCriteria.forEach(s -> addOrderBy(selectQuery, s));
    }
    return sortCriteria;
  }

  private void addOrderBy(SelectQuery selectQuery, QLCESort sortCriteria) {
    QLCESortType sortType = sortCriteria.getSortType();
    OrderObject.Dir dir =
        sortCriteria.getOrder() == QLSortOrder.ASCENDING ? OrderObject.Dir.ASCENDING : OrderObject.Dir.DESCENDING;
    switch (sortType) {
      case TIME:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.STARTTIME.getFieldName(), dir);
        break;
      case TOTALCOST:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.SUM.getFieldName(), dir);
        break;
      case IDLECOST:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.IDLECOST.getFieldName(), dir);
        break;
      case UNALLOCATEDCOST:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.UNALLOCATEDCOST.getFieldName(), dir);
        break;
      case CLUSTER:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.CLUSTERID.getFieldName(), dir);
        break;
      case WORKLOAD:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.WORKLOADNAME.getFieldName(), dir);
        break;
      case NAMESPACE:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.NAMESPACE.getFieldName(), dir);
        break;
      case WORKLOADTYPE:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.WORKLOADTYPE.getFieldName(), dir);
        break;
      case INSTANCE:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.INSTANCEID.getFieldName(), dir);
        break;
      case APPLICATION:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.APPID.getFieldName(), dir);
        break;
      case SERVICE:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.SERVICEID.getFieldName(), dir);
        break;
      case ENVIRONMENT:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.ENVID.getFieldName(), dir);
        break;
      case TASK:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.TASKID.getFieldName(), dir);
        break;
      case ECS_SERVICE:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.CLOUDSERVICENAME.getFieldName(), dir);
        break;
      case LAUNCHTYPE:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.LAUNCHTYPE.getFieldName(), dir);
        break;
      case REGION:
        selectQuery.addCustomOrdering(CEExportDataMetadataFields.REGION.getFieldName(), dir);
        break;
      default:
        throw new InvalidRequestException("Order type not supported " + sortType);
    }
  }

  // Miscellaneous methods

  private void addSelectedColumns(
      SelectQuery selectQuery, List<CEExportDataMetadataFields> fieldNames, List<String> selectedFields) {
    for (String field : selectedFields) {
      if (field.equals(CEHarnessEntityKeys.application)) {
        selectQuery.addColumns(schema.getAppId());
        fieldNames.add(CEExportDataMetadataFields.APPID);
      } else if (field.equals(CEHarnessEntityKeys.service)) {
        selectQuery.addColumns(schema.getServiceId());
        fieldNames.add(CEExportDataMetadataFields.SERVICEID);
      } else if (field.equals(CEHarnessEntityKeys.environment)) {
        selectQuery.addColumns(schema.getEnvId());
        fieldNames.add(CEExportDataMetadataFields.ENVID);
      } else if (field.equals(CEDataEntryKeys.totalCost)) {
        selectQuery.addColumns(schema.getBillingAmount());
        fieldNames.add(CEExportDataMetadataFields.TOTALCOST);
      } else if (field.equals(CEDataEntryKeys.idleCost)) {
        selectQuery.addColumns(schema.getIdleCost());
        fieldNames.add(CEExportDataMetadataFields.IDLECOST);
      } else if (field.equals(CEDataEntryKeys.unallocatedCost)) {
        selectQuery.addColumns(schema.getUnallocatedCost());
        fieldNames.add(CEExportDataMetadataFields.UNALLOCATEDCOST);
      } else if (field.equals(CEDataEntryKeys.systemCost)) {
        selectQuery.addColumns(schema.getSystemCost());
        fieldNames.add(CEExportDataMetadataFields.SYSTEMCOST);
      } else if (field.equals(CEDataEntryKeys.avgCpuUtilization)) {
        selectQuery.addColumns(schema.getAvgCpuUtilization());
        fieldNames.add(CEExportDataMetadataFields.AVGCPUUTILIZATION);
      } else if (field.equals(CEDataEntryKeys.avgMemoryUtilization)) {
        selectQuery.addColumns(schema.getAvgMemoryUtilization());
        fieldNames.add(CEExportDataMetadataFields.AVGMEMORYUTILIZATION);
      } else if (field.equals(CEDataEntryKeys.cpuRequest)) {
        selectQuery.addColumns(schema.getCpuRequest());
        fieldNames.add(CEExportDataMetadataFields.CPUREQUEST);
      } else if (field.equals(CEDataEntryKeys.memoryRequest)) {
        selectQuery.addColumns(schema.getMemoryRequest());
        fieldNames.add(CEExportDataMetadataFields.MEMORYREQUEST);
      } else if (field.equals(CEDataEntryKeys.cpuLimit)) {
        selectQuery.addColumns(schema.getCpuLimit());
        fieldNames.add(CEExportDataMetadataFields.CPULIMIT);
      } else if (field.equals(CEDataEntryKeys.memoryLimit)) {
        selectQuery.addColumns(schema.getMemoryLimit());
        fieldNames.add(CEExportDataMetadataFields.MEMORYLIMIT);
      } else if (field.equals(CEDataEntryKeys.region)) {
        selectQuery.addColumns(schema.getRegion());
        fieldNames.add(CEExportDataMetadataFields.REGION);
      } else if (field.equals(CEEcsEntityKeys.taskId)) {
        selectQuery.addColumns(schema.getTaskId());
        fieldNames.add(CEExportDataMetadataFields.TASKID);
      } else if (field.equals(CEEcsEntityKeys.service)) {
        selectQuery.addColumns(schema.getCloudServiceName());
        fieldNames.add(CEExportDataMetadataFields.CLOUDSERVICENAME);
      } else if (field.equals(CEEcsEntityKeys.launchType)) {
        selectQuery.addColumns(schema.getLaunchType());
        fieldNames.add(CEExportDataMetadataFields.LAUNCHTYPE);
      } else if (field.equals(CEK8sEntityKeys.workload)) {
        selectQuery.addColumns(schema.getWorkloadName());
        fieldNames.add(CEExportDataMetadataFields.WORKLOADNAME);
      } else if (field.equals(CEK8sEntityKeys.namespace)) {
        selectQuery.addColumns(schema.getNamespace());
        fieldNames.add(CEExportDataMetadataFields.NAMESPACE);
      } else if (field.equals(CEK8sEntityKeys.pod) || field.equals(CEK8sEntityKeys.node)) {
        selectQuery.addColumns(schema.getInstanceId());
        fieldNames.add(CEExportDataMetadataFields.INSTANCEID);
      } else if (field.equals(CEDataEntryKeys.cluster)) {
        selectQuery.addColumns(schema.getClusterId());
        fieldNames.add(CEExportDataMetadataFields.CLUSTERID);
      } else if (field.equals(CEDataEntryKeys.clusterType)) {
        selectQuery.addColumns(schema.getClusterType());
        fieldNames.add(CEExportDataMetadataFields.CLUSTERTYPE);
      } else if (field.equals(CEDataEntryKeys.instanceType)) {
        selectQuery.addColumns(schema.getInstanceType());
        fieldNames.add(CEExportDataMetadataFields.INSTANCETYPE);
      } else if (field.equals(CEDataEntryKeys.startTime)) {
        selectQuery.addColumns(schema.getStartTime());
        fieldNames.add(CEExportDataMetadataFields.STARTTIME);
      }
    }
  }

  protected boolean checkTimeFilter(List<QLCEFilter> filters) {
    long startTime = 0L;
    long endTime = Long.MAX_VALUE - 1;
    for (QLCEFilter filter : filters) {
      if (filter.getStartTime() != null) {
        startTime = Math.max(startTime, filter.getStartTime().getValue().longValue());
      }
      if (filter.getEndTime() != null) {
        endTime = Math.min(endTime, filter.getEndTime().getValue().longValue());
      }
    }
    return endTime - startTime <= 7 * ONE_DAY_MILLISEC;
  }

  protected boolean isClusterDrillDown(List<QLCEEntityGroupBy> groupByList) {
    return groupByList.stream().anyMatch(groupBy
        -> groupBy == QLCEEntityGroupBy.Workload || groupBy == QLCEEntityGroupBy.Namespace
            || groupBy == QLCEEntityGroupBy.EcsService || groupBy == QLCEEntityGroupBy.Task
            || groupBy == QLCEEntityGroupBy.LaunchType);
  }
}
