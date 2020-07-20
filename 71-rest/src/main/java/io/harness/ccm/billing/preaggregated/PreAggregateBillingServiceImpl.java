package io.harness.ccm.billing.preaggregated;

import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.healthmarketscience.sqlbuilder.Condition;
import com.healthmarketscience.sqlbuilder.SelectQuery;
import com.healthmarketscience.sqlbuilder.SqlObject;
import io.harness.ccm.billing.RawBillingTableSchema;
import io.harness.ccm.billing.bigquery.BigQueryService;
import io.harness.ccm.billing.graphql.CloudBillingFilter;
import io.harness.ccm.setup.CECloudAccountDao;
import lombok.extern.slf4j.Slf4j;
import software.wings.beans.ce.CECloudAccount;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class PreAggregateBillingServiceImpl implements PreAggregateBillingService {
  private BigQueryService bigQueryService;
  private PreAggregatedBillingDataHelper dataHelper;
  private CECloudAccountDao ceCloudAccountDao;

  private static final String TOTAL_COST_LABEL = "Total Cost";
  private static final String awsRawTable = "awscur";

  @Inject
  PreAggregateBillingServiceImpl(
      BigQueryService bigQueryService, PreAggregatedBillingDataHelper dataHelper, CECloudAccountDao ceCloudAccountDao) {
    this.bigQueryService = bigQueryService;
    this.dataHelper = dataHelper;
    this.ceCloudAccountDao = ceCloudAccountDao;
  }

  @Override
  public PreAggregateBillingTimeSeriesStatsDTO getPreAggregateBillingTimeSeriesStats(List<SqlObject> aggregateFunction,
      List<Object> groupByObjects, List<Condition> conditions, List<SqlObject> sort, String tableName,
      SqlObject leftJoin) {
    Preconditions.checkNotNull(
        groupByObjects, "Queries to getPreAggregateBillingTimeSeriesStats need at least one groupBy");
    SelectQuery query = dataHelper.getQuery(
        aggregateFunction, groupByObjects, conditions, sort, true, leftJoin != null, tableName.contains(awsRawTable));
    if (leftJoin != null) {
      query.addFromTable(RawBillingTableSchema.table);
      query.addCustomJoin(leftJoin);
    }
    // Replacing the Default Table with the Table in the context
    String timeSeriesDataQuery = query.toString();
    timeSeriesDataQuery = timeSeriesDataQuery.replaceAll(PreAggregatedTableSchema.defaultTableName, tableName);
    logger.info("getPreAggregateBillingTimeSeriesStats Query {}", timeSeriesDataQuery);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(timeSeriesDataQuery).build();
    TableResult result = null;
    try {
      result = bigQueryService.get().query(queryConfig);
    } catch (InterruptedException e) {
      logger.error("Failed to get getPreAggregateBillingTimeSeriesStats. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return dataHelper.convertToPreAggregatesTimeSeriesData(result);
  }

  @Override
  public PreAggregateBillingEntityStatsDTO getPreAggregateBillingEntityStats(String accountId,
      List<SqlObject> aggregateFunction, List<Object> groupByObjects, List<Condition> conditions, List<SqlObject> sort,
      String queryTableName, List<CloudBillingFilter> filters, SqlObject leftJoin) {
    Preconditions.checkNotNull(
        groupByObjects, "Queries to getPreAggregateBillingEntityStats need at least one groupBy");
    SelectQuery query = dataHelper.getQuery(aggregateFunction, groupByObjects, conditions, sort, false);
    if (leftJoin != null) {
      query.addFromTable(RawBillingTableSchema.table);
      query.addCustomJoin(leftJoin);
    }
    // Replacing the Default Table with the Table in the context
    String entityDataQuery = query.toString();
    entityDataQuery = entityDataQuery.replaceAll(PreAggregatedTableSchema.defaultTableName, queryTableName);
    Map<String, String> linkedAccountMap =
        isAWSLinkedAccountGroupByPresent(groupByObjects) ? linkedAccountsMap(accountId) : Collections.emptyMap();

    List<CloudBillingFilter> trendFilters = dataHelper.getTrendFilters(filters);
    Instant trendStartInstant =
        Instant.ofEpochMilli(dataHelper.getStartTimeFilter(trendFilters).getValue().longValue());
    SelectQuery prevQuery = dataHelper.getQuery(aggregateFunction, groupByObjects,
        dataHelper.filtersToConditions(trendFilters, leftJoin != null, queryTableName.contains(awsRawTable)), sort,
        false);
    if (leftJoin != null) {
      prevQuery.addFromTable(RawBillingTableSchema.table);
      prevQuery.addCustomJoin(leftJoin);
    }
    String prevEntityDataQuery = prevQuery.toString();
    prevEntityDataQuery = prevEntityDataQuery.replaceAll(PreAggregatedTableSchema.defaultTableName, queryTableName);
    Map<String, PreAggregatedCostData> idToPrevBillingAmountData = getPrevAggregatedEntityData(prevEntityDataQuery);

    logger.info("getPreAggregateBillingEntityStats Query {}", entityDataQuery);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(entityDataQuery).build();
    TableResult result = null;
    try {
      result = bigQueryService.get().query(queryConfig);
    } catch (InterruptedException e) {
      logger.error("Failed to get getPreAggregateBillingEntityStats. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return dataHelper.convertToPreAggregatesEntityData(
        result, linkedAccountMap, idToPrevBillingAmountData, filters, trendStartInstant);
  }

  private Map<String, PreAggregatedCostData> getPrevAggregatedEntityData(String prevEntityDataQuery) {
    logger.info("getPreviousPreAggregateBillingEntityStats Query {}", prevEntityDataQuery);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(prevEntityDataQuery).build();
    TableResult result = null;
    try {
      result = bigQueryService.get().query(queryConfig);
    } catch (InterruptedException e) {
      logger.error("Failed to get getPreviousPreAggregateBillingEntityStats. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return dataHelper.convertToIdToPrevBillingData(result);
  }

  @Override
  public PreAggregateBillingTrendStatsDTO getPreAggregateBillingTrendStats(List<SqlObject> aggregateFunction,
      List<Condition> conditions, String queryTableName, List<CloudBillingFilter> filters, SqlObject leftJoin) {
    PreAggregatedCostDataStats preAggregatedCostDataStats =
        getAggregatedCostData(aggregateFunction, conditions, queryTableName, leftJoin);
    List<CloudBillingFilter> trendFilters = dataHelper.getTrendFilters(filters);
    Instant trendStartInstant =
        Instant.ofEpochMilli(dataHelper.getStartTimeFilter(trendFilters).getValue().longValue());
    PreAggregatedCostDataStats prevPreAggregatedCostDataStats = getAggregatedCostData(aggregateFunction,
        dataHelper.filtersToConditions(trendFilters, leftJoin != null, queryTableName.contains(awsRawTable)),
        queryTableName, leftJoin);
    if (preAggregatedCostDataStats != null) {
      return PreAggregateBillingTrendStatsDTO.builder()
          .blendedCost(dataHelper.getCostBillingStats(preAggregatedCostDataStats.getBlendedCost(),
              prevPreAggregatedCostDataStats.getBlendedCost(), filters, TOTAL_COST_LABEL, trendStartInstant))
          .unBlendedCost(dataHelper.getCostBillingStats(preAggregatedCostDataStats.getUnBlendedCost(),
              prevPreAggregatedCostDataStats.getUnBlendedCost(), filters, TOTAL_COST_LABEL, trendStartInstant))
          .cost(dataHelper.getCostBillingStats(preAggregatedCostDataStats.getCost(),
              prevPreAggregatedCostDataStats.getCost(), filters, TOTAL_COST_LABEL, trendStartInstant))
          .build();
    } else {
      return PreAggregateBillingTrendStatsDTO.builder().build();
    }
  }

  @Override
  public PreAggregateFilterValuesDTO getPreAggregateFilterValueStats(String accountId, List<Object> groupByObjects,
      List<Condition> conditions, String queryTableName, SqlObject leftJoin) {
    SelectQuery query = dataHelper.getQuery(null, groupByObjects, conditions, null, false);
    if (leftJoin != null) {
      query.addFromTable(RawBillingTableSchema.table);
      query.addCustomJoin(leftJoin);
    }
    // Replacing the Default Table with the Table in the context
    String filterValueQuery = query.toString();
    filterValueQuery = filterValueQuery.replaceAll(PreAggregatedTableSchema.defaultTableName, queryTableName);
    logger.info("getPreAggregateFilterValueStats Query {}", filterValueQuery);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(filterValueQuery).build();
    TableResult result = null;
    try {
      result = bigQueryService.get().query(queryConfig);
    } catch (InterruptedException e) {
      logger.error("Failed to get getPreAggregateBillingEntityStats. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return dataHelper.convertToPreAggregatesFilterValue(result, linkedAccountsMap(accountId));
  }

  public PreAggregatedCostDataStats getAggregatedCostData(
      List<SqlObject> aggregateFunction, List<Condition> conditions, String queryTableName, SqlObject leftJoin) {
    SelectQuery query = dataHelper.getQuery(aggregateFunction, null, conditions, null, false);
    if (leftJoin != null) {
      query.addFromTable(RawBillingTableSchema.table);
      query.addCustomJoin(leftJoin);
    }
    // Replacing the Default Table with the Table in the context
    String trendStatsQuery = query.toString();
    trendStatsQuery = trendStatsQuery.replaceAll(PreAggregatedTableSchema.defaultTableName, queryTableName);
    logger.info("getAggregatedCostData Query {}", trendStatsQuery);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(trendStatsQuery).build();
    TableResult result = null;
    try {
      result = bigQueryService.get().query(queryConfig);
    } catch (InterruptedException e) {
      logger.error("Failed to get AggregatedCostData. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return dataHelper.convertToAggregatedCostData(result);
  }

  @Override
  public PreAggregateCloudOverviewDataDTO getPreAggregateBillingOverview(List<SqlObject> aggregateFunction,
      List<Object> groupByObjects, List<Condition> conditions, List<SqlObject> sort, String queryTableName,
      List<CloudBillingFilter> filters, SqlObject leftJoin) {
    SelectQuery query = dataHelper.getQuery(aggregateFunction, groupByObjects, conditions, sort, false);
    if (leftJoin != null) {
      query.addFromTable(RawBillingTableSchema.table);
      query.addCustomJoin(leftJoin);
    }
    // Replacing the Default Table with the Table in the context
    String cloudOverviewQuery = query.toString();
    cloudOverviewQuery = cloudOverviewQuery.replaceAll(PreAggregatedTableSchema.defaultTableName, queryTableName);
    logger.info("getPreAggregateBillingOverview Query {}", cloudOverviewQuery);

    List<CloudBillingFilter> trendFilters = dataHelper.getTrendFilters(filters);
    Instant trendStartInstant =
        Instant.ofEpochMilli(dataHelper.getStartTimeFilter(trendFilters).getValue().longValue());
    SelectQuery prevQuery = dataHelper.getQuery(
        aggregateFunction, groupByObjects, dataHelper.filtersToConditions(trendFilters), sort, false);
    if (leftJoin != null) {
      query.addFromTable(RawBillingTableSchema.table);
      prevQuery.addCustomJoin(leftJoin);
    }
    String prevCloudOverviewQuery = prevQuery.toString();
    prevCloudOverviewQuery =
        prevCloudOverviewQuery.replaceAll(PreAggregatedTableSchema.defaultTableName, queryTableName);
    Map<String, PreAggregatedCostData> idToPrevOverviewAmountData = getPrevOverviewData(prevCloudOverviewQuery);

    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(cloudOverviewQuery).build();
    TableResult result = null;
    try {
      result = bigQueryService.get().query(queryConfig);
    } catch (InterruptedException e) {
      logger.error("Failed to get PreAggregateBillingOverview. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return dataHelper.convertToPreAggregatesOverview(result, idToPrevOverviewAmountData, filters, trendStartInstant);
  }

  private Map<String, PreAggregatedCostData> getPrevOverviewData(String prevCloudOverviewQuery) {
    logger.info("getPreviousPreAggregateBillingOverview Query {}", prevCloudOverviewQuery);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(prevCloudOverviewQuery).build();
    TableResult result = null;
    try {
      result = bigQueryService.get().query(queryConfig);
    } catch (InterruptedException e) {
      logger.error("Failed to get getPreviousPreAggregateBillingOverview. {}", e);
      Thread.currentThread().interrupt();
      return null;
    }
    return dataHelper.convertIdToPrevOverviewBillingData(result);
  }

  private Map<String, String> linkedAccountsMap(String harnessAccountId) {
    List<CECloudAccount> awsCloudAccountList = ceCloudAccountDao.getByAWSAccountId(harnessAccountId);
    return awsCloudAccountList.stream().collect(Collectors.toMap(CECloudAccount::getInfraAccountId,
        CECloudAccount::getAccountName, (existingAccountName, newAccountName) -> existingAccountName));
  }

  private boolean isAWSLinkedAccountGroupByPresent(List<Object> groupByObjects) {
    return groupByObjects.stream().anyMatch(groupBy -> groupBy == PreAggregatedTableSchema.awsUsageAccountId);
  }
}
