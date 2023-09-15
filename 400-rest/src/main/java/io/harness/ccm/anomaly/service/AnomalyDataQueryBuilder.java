/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.anomaly.service;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.anomaly.entities.AnomalyEntity.AnomaliesDataTableSchema;
import io.harness.ccm.anomaly.graphql.AnomaliesFilter;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;
import io.harness.ccm.billing.graphql.CloudBillingIdFilter;
import io.harness.ccm.billing.graphql.CloudEntityGroupBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;

import software.wings.graphql.schema.type.aggregation.Filter;
import software.wings.graphql.schema.type.aggregation.QLFilterKind;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.anomaly.QLAnomalyInput;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter.QLBillingDataFilterBuilder;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilterType;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.UnaryCondition;
import com.healthmarketscience.sqlbuilder.UpdateQuery;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class AnomalyDataQueryBuilder {
  private static void addAccountFilter(SelectQuery selectQuery, String accountId) {
    selectQuery.addCondition(BinaryCondition.equalTo(AnomaliesDataTableSchema.accountId, accountId));
  }

  public static void addValidAnomaliesFilter(SelectQuery query) {
    query.addCondition(BinaryCondition.equalTo(AnomaliesDataTableSchema.newEntity, Boolean.FALSE.toString()));
  }

  public static String formAnomalyFetchQuery(String accountId, QLAnomalyInput input) {
    SelectQuery query = new SelectQuery();
    query.addAllTableColumns(AnomaliesDataTableSchema.table);
    addAccountFilter(query, accountId);
    addValidAnomaliesFilter(query);
    query.addCondition(BinaryCondition.equalTo(AnomaliesDataTableSchema.id, input.getAnomalyId()));

    return query.toString();
  }

  public static String formAnomalyUpdateQuery(String accountId, QLAnomalyInput input) {
    if (input.getAnomalyId() == null) {
      log.error(
          "Mutation request to update anomaly has anomalyid equal to null, cannot process mutation request id : {}",
          input.getClientMutationId());
      throw new InvalidArgumentsException("Mutation request cannot be processed since anomalyid is null");
    }

    UpdateQuery query = new UpdateQuery(AnomaliesDataTableSchema.table);
    query.addCondition(BinaryCondition.equalTo(AnomaliesDataTableSchema.accountId, accountId));
    query.addCondition(BinaryCondition.equalTo(AnomaliesDataTableSchema.id, input.getAnomalyId()));

    if (input.getNote() != null) {
      query.addSetClause(AnomaliesDataTableSchema.note, input.getNote());
    }

    if (input.getUserFeedback() != null) {
      query.addCustomSetClause(AnomaliesDataTableSchema.feedBack, input.getUserFeedback().toString());
    }

    return query.validate().toString();
  }

  public static String overviewQuery(String accountId, List<QLBillingDataFilter> filters) {
    filters = new ArrayList<>(filters);

    SelectQuery query = new SelectQuery();
    addAccountFilter(query, accountId);
    addValidAnomaliesFilter(query);
    query.addAllTableColumns(AnomaliesDataTableSchema.table);
    query.addAliasedColumn(new CustomSql(AnomaliesDataTableSchema.actualCost.getColumnNameSQL() + " - "
                               + AnomaliesDataTableSchema.expectedCost.getColumnNameSQL()),
        "difference");
    decorateK8SQueryWithFilters(query, filters);
    query.addOrdering(AnomaliesDataTableSchema.anomalyTime, OrderObject.Dir.ASCENDING);
    query.addCustomOrdering(new CustomSql("difference"), OrderObject.Dir.DESCENDING);
    return query.toString();
  }

  public static String formK8SQuery(String accountId, List<QLBillingDataFilter> filters, List<QLCCMGroupBy> groupBy) {
    filters = new ArrayList<>(filters);

    SelectQuery query = new SelectQuery();
    addAccountFilter(query, accountId);
    addValidAnomaliesFilter(query);
    query.addAllTableColumns(AnomaliesDataTableSchema.table);
    query.addAliasedColumn(new CustomSql(AnomaliesDataTableSchema.actualCost.getColumnNameSQL() + " - "
                               + AnomaliesDataTableSchema.expectedCost.getColumnNameSQL()),
        "difference");

    convertK8SGroupByAndAddToFilter(groupBy, filters);
    decorateK8SQueryWithFilters(query, filters);
    query.addOrdering(AnomaliesDataTableSchema.anomalyTime, OrderObject.Dir.ASCENDING);
    query.addCustomOrdering(new CustomSql("difference"), OrderObject.Dir.DESCENDING);
    return query.toString();
  }

  protected static List<QLCCMEntityGroupBy> getK8SGroupByEntity(List<QLCCMGroupBy> groupBy) {
    return groupBy != null ? groupBy.stream()
                                 .filter(g -> g.getEntityGroupBy() != null)
                                 .map(QLCCMGroupBy::getEntityGroupBy)
                                 .collect(Collectors.toList())
                           : Collections.emptyList();
  }

  private static void convertK8SGroupByAndAddToFilter(List<QLCCMGroupBy> groupBy, List<QLBillingDataFilter> filters) {
    List<QLCCMEntityGroupBy> entityGroupBy = getK8SGroupByEntity(groupBy);
    for (QLCCMEntityGroupBy singleGroupBy : entityGroupBy) {
      filters.add(convertGroupByToFilter(singleGroupBy));
    }
  }

  private static QLBillingDataFilter convertGroupByToFilter(QLCCMEntityGroupBy groupBy) {
    QLBillingDataFilterBuilder filter = QLBillingDataFilter.builder();
    String[] values = new String[] {""};
    switch (groupBy) {
      case Cluster:
        filter.cluster(QLIdFilter.builder().operator(QLIdOperator.NOT_NULL).values(values).build());
        break;
      case Namespace:
        filter.namespace(QLIdFilter.builder().operator(QLIdOperator.NOT_NULL).values(values).build());
        break;
      case WorkloadName:
        filter.workloadName(QLIdFilter.builder().operator(QLIdOperator.NOT_NULL).values(values).build());
        break;
      case WorkloadType:
      case CloudServiceName:
      case Application:
      case Service:
      case StartTime:
      case Region:
      case Environment:
      case TaskId:
      case LaunchType:
      case ClusterType:
      case ClusterName:
      case InstanceType:
      case InstanceName:
      case CloudProvider:
      case Node:
      case Pod:
      case PV:
        break;
      default:
        log.error("Groupby clause not supported in AnomalyDataQueryBuilder");
        throw new InvalidArgumentsException("Entity-Groupby clause not supported");
    }
    return filter.build();
  }

  private static void decorateK8SQueryWithFilters(SelectQuery selectQuery, List<QLBillingDataFilter> filters) {
    List<QLBillingDataFilterType> listTypes = Arrays.asList(QLBillingDataFilterType.LabelSearch,
        QLBillingDataFilterType.TagSearch, QLBillingDataFilterType.Tag, QLBillingDataFilterType.Label,
        QLBillingDataFilterType.EnvironmentType, QLBillingDataFilterType.AlertTime, QLBillingDataFilterType.View);

    for (QLBillingDataFilter filter : filters) {
      Set<QLBillingDataFilterType> filterTypes = QLBillingDataFilter.getFilterTypes(filter);
      for (QLBillingDataFilterType type : filterTypes) {
        if (listTypes.contains(type)) {
          continue;
        }
        if (type.getMetaDataFields().getFilterKind() == QLFilterKind.SIMPLE) {
          decorateSimpleFilter(selectQuery, filter, type);
        } else {
          log.error("Failed to apply K8S filter :[{}]", filter);
        }
      }
    }
  }

  private static void decorateSimpleFilter(
      SelectQuery selectQuery, QLBillingDataFilter filter, QLBillingDataFilterType type) {
    Filter f = QLBillingDataFilter.getFilter(type, filter);
    if (checkFilter(f)) {
      if (isIdFilter(f)) {
        addSimpleIdOperator(selectQuery, f, type);
      } else if (isTimeFilter(f)) {
        addSimpleTimeFilter(selectQuery, f, type);
      }
    } else {
      log.info("Not adding K8S filter since it is not valid " + f);
    }
  }

  private static void addSimpleTimeFilter(SelectQuery selectQuery, Filter filter, QLBillingDataFilterType type) {
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

  private static void addSimpleIdOperator(SelectQuery selectQuery, Filter filter, QLBillingDataFilterType type) {
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

  private static boolean isIdFilter(Filter f) {
    return f instanceof QLIdFilter;
  }

  private static boolean isTimeFilter(Filter f) {
    return f instanceof QLTimeFilter;
  }

  private static boolean checkFilter(Filter f) {
    return f.getOperator() != null && EmptyPredicate.isNotEmpty(f.getValues());
  }

  private static DbColumn getFilterKey(QLBillingDataFilterType type) {
    switch (type) {
      case EndTime:
      case StartTime:
        return AnomaliesDataTableSchema.anomalyTime;
      case Cluster:
        return AnomaliesDataTableSchema.clusterId;
      case WorkloadName:
        return AnomaliesDataTableSchema.workloadType;
      case Namespace:
        return AnomaliesDataTableSchema.namespace;
      case Application:
      case Service:
      case Environment:
      case CloudServiceName:
      case LaunchType:
      case TaskId:
      case InstanceType:
      case InstanceName:
      case CloudProvider:
      case NodeInstanceId:
      case PodInstanceId:
      case ParentInstanceId:
      case LabelSearch:
      case TagSearch:
      case Tag:
      case Label:
      case EnvironmentType:
      case AlertTime:
        break;
      default:
        throw new InvalidArgumentsException("Filter type not supported " + type);
    }
    return null;
  }

  //----------------- Cloud -------------

  public static String formCloudQuery(
      String accountId, List<CloudBillingFilter> filters, List<CloudBillingGroupBy> groupBy) {
    filters = new ArrayList<CloudBillingFilter>(filters);
    SelectQuery query = new SelectQuery();
    addAccountFilter(query, accountId);
    addValidAnomaliesFilter(query);
    query.addAllTableColumns(AnomaliesDataTableSchema.table);
    query.addAliasedColumn(new CustomSql(AnomaliesDataTableSchema.actualCost.getColumnNameSQL() + " - "
                               + AnomaliesDataTableSchema.expectedCost.getColumnNameSQL()),
        "difference");
    convertCloudGroupByAndAddToFilter(filters, groupBy);
    decorateCloudQueryWithFilters(query, filters);
    query.addOrdering(AnomaliesDataTableSchema.anomalyTime, OrderObject.Dir.ASCENDING);
    query.addCustomOrdering(new CustomSql("difference"), OrderObject.Dir.DESCENDING);
    return query.toString();
  }

  private static void convertCloudGroupByAndAddToFilter(
      List<CloudBillingFilter> filters, List<CloudBillingGroupBy> groupBy) {
    List<CloudEntityGroupBy> entityGroupBy = getCloudGroupByEntity(groupBy);
    for (CloudEntityGroupBy singleGroupBy : entityGroupBy) {
      CloudBillingFilter ConvertedGroupBy = convertGroupByToFilter(singleGroupBy);
      if (ConvertedGroupBy != null) {
        filters.add(ConvertedGroupBy);
      }
    }
  }
  protected List<CloudEntityGroupBy> getCloudGroupByEntity(List<CloudBillingGroupBy> groupBy) {
    return groupBy != null ? groupBy.stream()
                                 .filter(g -> g.getEntityGroupBy() != null)
                                 .map(CloudBillingGroupBy::getEntityGroupBy)
                                 .collect(Collectors.toList())
                           : Collections.emptyList();
  }

  private static CloudBillingFilter convertGroupByToFilter(CloudEntityGroupBy groupBy) {
    CloudBillingFilter filter = new CloudBillingFilter();
    String[] values = new String[] {""};
    switch (groupBy) {
      // --- GCP ---
      case projectId:
        filter.setProjectId(CloudBillingIdFilter.builder()
                                .operator(QLIdOperator.NOT_NULL)
                                .variable(CloudBillingFilter.BILLING_GCP_PROJECT)
                                .values(values)
                                .build());
        break;
      case skuId:
        filter.setSku(CloudBillingIdFilter.builder()
                          .operator(QLIdOperator.NOT_NULL)
                          .variable(CloudBillingFilter.BILLING_GCP_SKU)
                          .values(values)
                          .build());
        break;
      case product:
        filter.setProduct(CloudBillingIdFilter.builder()
                              .operator(QLIdOperator.NOT_NULL)
                              .variable(CloudBillingFilter.BILLING_GCP_PRODUCT)
                              .values(values)
                              .build());
        break;
      case sku:
        return null;
      // -- aws --
      case awsLinkedAccount:
        filter.setAwsLinkedAccount(CloudBillingIdFilter.builder()
                                       .operator(QLIdOperator.NOT_NULL)
                                       .variable(CloudBillingFilter.BILLING_AWS_LINKED_ACCOUNT)
                                       .values(values)
                                       .build());
        break;
      case awsService:
        filter.setAwsService(CloudBillingIdFilter.builder()
                                 .operator(QLIdOperator.NOT_NULL)
                                 .variable(CloudBillingFilter.BILLING_AWS_SERVICE)
                                 .values(values)
                                 .build());
        break;
      case billingAccountId:
      case awsUsageType:
      case awsInstanceType:
      case cloudProvider:
      case labelsKey:
      case projectNumber:
      case labelsValue:
      case tagsKey:
      case tagsValue:
      default:
        log.error("Cloud Groupby clause not supported in AnomalyDataQueryBuilder");
        throw new InvalidArgumentsException("Entity-Groupby clause not supported");
    }
    return filter;
  }

  private static void decorateCloudQueryWithFilters(SelectQuery selectQuery, List<CloudBillingFilter> filters) {
    AnomaliesFilter cloudFilter;
    for (CloudBillingFilter filter : filters) {
      try {
        cloudFilter = AnomaliesFilter.convertFromCloudBillingFilter(filter);
        selectQuery.addCondition(cloudFilter.toCondition());
      } catch (Exception e) {
        log.error("Not adding filter since it is not valid , Exception :{}", e);
      }
    }
  }

  public static String getAllAnomaliesQuery(String accountId, Instant fromDate, Instant toDate) {
    SelectQuery query = new SelectQuery();
    query.addAllTableColumns(AnomaliesDataTableSchema.table);
    query.addAliasedColumn(new CustomSql(AnomaliesDataTableSchema.actualCost.getColumnNameSQL() + " - "
                               + AnomaliesDataTableSchema.expectedCost.getColumnNameSQL()),
        "difference");
    query.addCondition(
        BinaryCondition.lessThanOrEq(AnomaliesDataTableSchema.anomalyTime, toDate.truncatedTo(ChronoUnit.DAYS)));
    query.addCondition(
        BinaryCondition.greaterThanOrEq(AnomaliesDataTableSchema.anomalyTime, fromDate.truncatedTo(ChronoUnit.DAYS)));
    addAccountFilter(query, accountId);
    addValidAnomaliesFilter(query);
    query.addCustomOrdering(new CustomSql("difference"), OrderObject.Dir.DESCENDING);
    return query.validate().toString();
  }
}
