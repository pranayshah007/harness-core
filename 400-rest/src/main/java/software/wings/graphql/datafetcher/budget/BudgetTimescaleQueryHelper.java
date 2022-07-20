/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.budget;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.budget.entities.BudgetAlertsData;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata.BillingDataMetaDataFields;
import software.wings.graphql.datafetcher.billing.BillingDataQueryMetadata.BillingDataQueryMetadataBuilder;
import software.wings.graphql.datafetcher.billing.BillingDataTableSchema;
import software.wings.graphql.datafetcher.billing.QLBillingAmountData;
import software.wings.graphql.datafetcher.billing.QLCCMAggregateOperation;
import software.wings.graphql.datafetcher.billing.QLCCMAggregationFunction;
import software.wings.graphql.datafetcher.budget.BudgetAlertsQueryMetadata.BudgetAlertsQueryMetadataBuilder;
import software.wings.graphql.schema.type.aggregation.Filter;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilterType;

import com.google.inject.Inject;
import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.Converter;
import com.healthmarketscience.sqlbuilder.FunctionCall;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.InsertQuery;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.dbspec.basic.DbColumn;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class BudgetTimescaleQueryHelper {
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject private TimeUtils utils;
  private BudgetAlertsTableSchema schema = new BudgetAlertsTableSchema();
  private BillingDataTableSchema billingDataTableSchema = new BillingDataTableSchema();
  private static final int MAX_RETRY = 3;

  public void insertAlertEntryInTable(BudgetAlertsData data, String accountId) {
    try {
      if (timeScaleDBService.isValid()) {
        BudgetAlertsQueryMetadata queryMetadata = getInsertQueryForBudgetAlert(data);
        String query = queryMetadata.getQuery();
        ResultSet resultSet = null;
        boolean successful = false;
        int retryCount = 0;
        while (!successful && retryCount < MAX_RETRY) {
          try (Connection connection = timeScaleDBService.getDBConnection();
               Statement statement = connection.createStatement()) {
            statement.execute(query);
            successful = true;
          } catch (SQLException e) {
            retryCount++;
            if (retryCount >= MAX_RETRY) {
              log.error(
                  "Failed to execute query in BudgetTimescaleQueryHelper, max retry count reached, query=[{}],accountId=[{}]",
                  queryMetadata.getQuery(), accountId, e);
            } else {
              log.warn(
                  "Failed to execute query in BudgetTimescaleQueryHelper, query=[{}],accountId=[{}], retryCount=[{}]",
                  queryMetadata.getQuery(), accountId, retryCount);
            }
          } finally {
            DBUtils.close(resultSet);
          }
        }
      } else {
        throw new InvalidRequestException("Cannot process request in BudgetTimescaleQueryHelper");
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Error while fetching budget alerts data {}", e);
    }
  }

  private BudgetAlertsQueryMetadata getInsertQueryForBudgetAlert(BudgetAlertsData data) {
    BudgetAlertsQueryMetadataBuilder queryMetaDataBuilder = BudgetAlertsQueryMetadata.builder();
    InsertQuery insertQuery = new InsertQuery(schema.getBudgetAlertsTable());
    insertQuery.addColumn(schema.getAlertTime(), Instant.ofEpochMilli(data.getTime()));
    insertQuery.addColumn(schema.getBudgetId(), data.getBudgetId());
    insertQuery.addColumn(schema.getAccountId(), data.getAccountId());
    insertQuery.addColumn(schema.getAlertThreshold(), data.getAlertThreshold());
    insertQuery.addColumn(schema.getAlertBasedOn(), data.getAlertBasedOn());
    insertQuery.addColumn(schema.getActualCost(), data.getActualCost());
    insertQuery.addColumn(schema.getBudgetedCost(), data.getBudgetedCost());
    queryMetaDataBuilder.query(insertQuery.toString());
    return queryMetaDataBuilder.build();
  }

  public long getLastAlertTimestamp(BudgetAlertsData data, String accountId) {
    try {
      if (timeScaleDBService.isValid()) {
        BudgetAlertsQueryMetadata queryMetadata = getThresholdCheckForBudgetAlert(data);
        String query = queryMetadata.getQuery();
        ResultSet resultSet = null;
        boolean successful = false;
        int retryCount = 0;
        while (!successful && retryCount < MAX_RETRY) {
          try (Connection connection = timeScaleDBService.getDBConnection();
               Statement statement = connection.createStatement()) {
            resultSet = statement.executeQuery(query);
            successful = true;
            if (!resultSet.next()) {
              return 0L;
            }
            return resultSet.getTimestamp(schema.getAlertTime().getColumnNameSQL(), utils.getDefaultCalendar())
                .getTime();
          } catch (SQLException e) {
            retryCount++;
            if (retryCount >= MAX_RETRY) {
              log.error(
                  "Failed to execute query in BudgetTimescaleQueryHelper, max retry count reached, query=[{}],accountId=[{}]",
                  queryMetadata.getQuery(), accountId, e);
            } else {
              log.warn(
                  "Failed to execute query in BudgetTimescaleQueryHelper, query=[{}],accountId=[{}], retryCount=[{}]",
                  queryMetadata.getQuery(), accountId, retryCount);
            }
          } finally {
            DBUtils.close(resultSet);
          }
        }
        return 0L;
      } else {
        throw new InvalidRequestException("Cannot process request in BudgetTimescaleQueryHelper");
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Error while fetching budget alerts {}", e);
    }
  }

  private BudgetAlertsQueryMetadata getThresholdCheckForBudgetAlert(BudgetAlertsData data) {
    BudgetAlertsQueryMetadataBuilder queryMetaDataBuilder = BudgetAlertsQueryMetadata.builder();
    SelectQuery selectQuery = new SelectQuery();
    selectQuery.setFetchNext(1);
    selectQuery.addCustomFromTable(schema.getBudgetAlertsTable());
    selectQuery.addColumns(schema.getAlertTime());
    selectQuery.addCondition(BinaryCondition.equalTo(schema.getBudgetId(), data.getBudgetId()));
    selectQuery.addCondition(BinaryCondition.equalTo(schema.getAlertThreshold(), data.getAlertThreshold()));
    selectQuery.addCondition(BinaryCondition.equalTo(schema.getAlertBasedOn(), data.getAlertBasedOn()));
    selectQuery.addCustomOrdering(schema.getAlertTime(), OrderObject.Dir.DESCENDING);

    addAccountFilter(selectQuery, data.getAccountId());
    selectQuery.getWhereClause().setDisableParens(true);
    queryMetaDataBuilder.query(selectQuery.toString());
    return queryMetaDataBuilder.build();
  }

  public QLBillingAmountData getBudgetCostData(
      String accountId, QLCCMAggregationFunction aggregateFunction, List<QLBillingDataFilter> filters) {
    try {
      if (timeScaleDBService.isValid()) {
        BillingDataQueryMetadata queryData = formBudgetCostQuery(accountId, filters, aggregateFunction);
        String query = queryData.getQuery();
        ResultSet resultSet = null;
        boolean successful = false;
        int retryCount = 0;
        while (!successful && retryCount < MAX_RETRY) {
          try (Connection connection = timeScaleDBService.getDBConnection();
               Statement statement = connection.createStatement()) {
            resultSet = statement.executeQuery(query);
            successful = true;
            return fetchBillingAmount(resultSet);
          } catch (SQLException e) {
            retryCount++;
            if (retryCount >= MAX_RETRY) {
              log.error("Failed to execute query in BudgetCostData, max retry count reached, query=[{}],accountId=[{}]",
                  queryData.getQuery(), accountId, e);
            } else {
              log.warn("Failed to execute query in BudgetCostData, query=[{}],accountId=[{}], retryCount=[{}]",
                  queryData.getQuery(), accountId, retryCount);
            }
          } finally {
            DBUtils.close(resultSet);
          }
        }
        return null;
      } else {
        throw new InvalidRequestException("Cannot process request in BudgetTimescaleQueryHelper");
      }
    } catch (Exception e) {
      throw new InvalidRequestException("Error while fetching budget alerts data {}", e);
    }
  }

  private QLBillingAmountData fetchBillingAmount(ResultSet resultSet) throws SQLException {
    QLBillingAmountData totalCostData = null;
    while (null != resultSet && resultSet.next()) {
      if (resultSet.getBigDecimal(BillingDataMetaDataFields.SUM.getFieldName()) != null) {
        totalCostData = QLBillingAmountData.builder()
                            .cost(resultSet.getBigDecimal(BillingDataMetaDataFields.SUM.getFieldName()))
                            .minStartTime(resultSet
                                              .getTimestamp(BillingDataMetaDataFields.MIN_STARTTIME.getFieldName(),
                                                  utils.getDefaultCalendar())
                                              .getTime())
                            .maxStartTime(resultSet
                                              .getTimestamp(BillingDataMetaDataFields.MAX_STARTTIME.getFieldName(),
                                                  utils.getDefaultCalendar())
                                              .getTime())
                            .build();
      }
    }
    return totalCostData;
  }

  private BillingDataQueryMetadata formBudgetCostQuery(
      String accountId, List<QLBillingDataFilter> filters, QLCCMAggregationFunction aggregateFunction) {
    BillingDataQueryMetadataBuilder queryMetaDataBuilder = BillingDataQueryMetadata.builder();
    SelectQuery selectQuery = new SelectQuery();
    BillingDataQueryMetadata.ResultType resultType;
    resultType = BillingDataQueryMetadata.ResultType.STACKED_TIME_SERIES;

    queryMetaDataBuilder.resultType(resultType);
    List<BillingDataQueryMetadata.BillingDataMetaDataFields> fieldNames = new ArrayList<>();
    addAggregation(selectQuery, aggregateFunction, fieldNames);
    addMinMaxStartTime(selectQuery, fieldNames);

    selectQuery.addCustomFromTable(billingDataTableSchema.getBillingDataTable());

    if (!isEmpty(filters)) {
      addFilters(selectQuery, filters);
    }

    addAccountFilter(selectQuery, accountId);

    selectQuery.getWhereClause().setDisableParens(true);
    queryMetaDataBuilder.fieldNames(fieldNames);
    queryMetaDataBuilder.query(selectQuery.toString());
    queryMetaDataBuilder.filters(filters);
    return queryMetaDataBuilder.build();
  }

  protected BillingDataQueryMetadata formBudgetAlertsCountQuery(String accountId, List<QLBillingDataFilter> filters) {
    BillingDataQueryMetadataBuilder queryMetaDataBuilder = BillingDataQueryMetadata.builder();
    SelectQuery selectQuery = new SelectQuery();
    BillingDataQueryMetadata.ResultType resultType;
    resultType = BillingDataQueryMetadata.ResultType.STACKED_TIME_SERIES;

    queryMetaDataBuilder.resultType(resultType);
    List<BillingDataMetaDataFields> fieldNames = new ArrayList<>();

    selectQuery.addCustomFromTable(schema.getBudgetAlertsTable());

    selectQuery.addCustomColumns(
        Converter.toColumnSqlObject(FunctionCall.count().addColumnParams(schema.getAlertTime()).setIsDistinct(true),
            BillingDataMetaDataFields.COUNT.getFieldName()));
    fieldNames.add(BillingDataMetaDataFields.COUNT);

    if (!isEmpty(filters)) {
      addFilters(selectQuery, getAlertTimeFilters(filters));
    }

    addAccountFilter(selectQuery, accountId);

    selectQuery.getWhereClause().setDisableParens(true);
    queryMetaDataBuilder.fieldNames(fieldNames);
    queryMetaDataBuilder.query(selectQuery.toString());
    queryMetaDataBuilder.filters(filters);
    return queryMetaDataBuilder.build();
  }

  private void addAggregation(SelectQuery selectQuery, QLCCMAggregationFunction aggregationFunction,
      List<BillingDataMetaDataFields> fieldNames) {
    if (aggregationFunction != null && aggregationFunction.getOperationType() == QLCCMAggregateOperation.SUM) {
      if (aggregationFunction.getColumnName().equals(billingDataTableSchema.getBillingAmount().getColumnNameSQL())) {
        selectQuery.addCustomColumns(
            Converter.toColumnSqlObject(FunctionCall.sum().addColumnParams(billingDataTableSchema.getBillingAmount()),
                BillingDataMetaDataFields.SUM.getFieldName()));
        fieldNames.add(BillingDataMetaDataFields.SUM);
      }
    }
  }

  private void addMinMaxStartTime(SelectQuery selectQuery, List<BillingDataMetaDataFields> fieldNames) {
    selectQuery.addCustomColumns(
        Converter.toColumnSqlObject(FunctionCall.min().addColumnParams(billingDataTableSchema.getStartTime()),
            BillingDataMetaDataFields.MIN_STARTTIME.getFieldName()));
    fieldNames.add(BillingDataMetaDataFields.MIN_STARTTIME);
    selectQuery.addCustomColumns(
        Converter.toColumnSqlObject(FunctionCall.max().addColumnParams(billingDataTableSchema.getStartTime()),
            BillingDataMetaDataFields.MAX_STARTTIME.getFieldName()));
    fieldNames.add(BillingDataMetaDataFields.MAX_STARTTIME);
  }

  private void addFilters(SelectQuery selectQuery, List<QLBillingDataFilter> filters) {
    for (QLBillingDataFilter filter : filters) {
      Set<QLBillingDataFilterType> filterTypes = QLBillingDataFilter.getFilterTypes(filter);
      for (QLBillingDataFilterType type : filterTypes) {
        Filter f = QLBillingDataFilter.getFilter(type, filter);
        if (isIdFilter(f)) {
          addSimpleIdOperator(selectQuery, f, type);
        } else {
          addSimpleTimeFilter(selectQuery, f, type);
        }
      }
    }
  }

  private boolean isIdFilter(Filter f) {
    return f instanceof QLIdFilter;
  }

  private void addSimpleIdOperator(SelectQuery selectQuery, Filter filter, QLBillingDataFilterType type) {
    DbColumn key = getFilterKey(type);
    QLIdOperator operator = (QLIdOperator) filter.getOperator();
    QLIdOperator finalOperator = operator;
    switch (finalOperator) {
      case EQUALS:
        selectQuery.addCondition(BinaryCondition.equalTo(key, filter.getValues()[0]));
        break;
      case IN:
        selectQuery.addCondition(new InCondition(key, (Object[]) filter.getValues()));
        break;
      default:
        throw new InvalidRequestException("String simple operator not supported" + operator);
    }
  }

  private DbColumn getFilterKey(QLBillingDataFilterType type) {
    switch (type) {
      case EndTime:
      case StartTime:
        return billingDataTableSchema.getStartTime();
      case Application:
        return billingDataTableSchema.getAppId();
      case Environment:
        return billingDataTableSchema.getEnvId();
      case Cluster:
        return billingDataTableSchema.getClusterId();
      case InstanceType:
        return billingDataTableSchema.getInstanceType();
      case AlertTime:
        return schema.getAlertTime();
      default:
        throw new InvalidRequestException("Filter type not supported " + type);
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

  private void addAccountFilter(SelectQuery selectQuery, String accountId) {
    selectQuery.addCondition(BinaryCondition.equalTo(schema.getAccountId(), accountId));
  }

  // To change start time and end time filters to alert time filters for budget alerts
  private List<QLBillingDataFilter> getAlertTimeFilters(List<QLBillingDataFilter> filters) {
    List<QLBillingDataFilter> alertTimeFilters = new ArrayList<>();
    for (QLBillingDataFilter filter : filters) {
      if (filter.getStartTime() != null) {
        alertTimeFilters.add(QLBillingDataFilter.builder()
                                 .alertTime(QLTimeFilter.builder()
                                                .operator(filter.getStartTime().getOperator())
                                                .value(filter.getStartTime().getValue())
                                                .build())
                                 .build());
      }
      if (filter.getEndTime() != null) {
        alertTimeFilters.add(QLBillingDataFilter.builder()
                                 .alertTime(QLTimeFilter.builder()
                                                .operator(filter.getEndTime().getOperator())
                                                .value(filter.getEndTime().getValue())
                                                .build())
                                 .build());
      }
    }
    return alertTimeFilters;
  }
}
