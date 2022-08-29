/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection.service.impl;

import io.harness.batch.processing.anomalydetection.AnomalyDetectionTimeSeries;
import io.harness.batch.processing.anomalydetection.TimeSeriesMetaData;
import io.harness.batch.processing.anomalydetection.helpers.AnomalyDetectionHelper;
import io.harness.batch.processing.anomalydetection.helpers.TimeSeriesUtils;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.billing.graphql.CloudBillingGroupBy;
import io.harness.ccm.billing.graphql.CloudEntityGroupBy;
import io.harness.ccm.billing.preaggregated.PreAggregatedTableSchema;
import io.harness.exception.InvalidArgumentsException;

import software.wings.graphql.datafetcher.billing.CloudBillingHelper;

import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.google.cloud.bigquery.TableResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class AnomalyDetectionBigQueryServiceImpl {
  private static final String preAggregated = "preAggregated";
  private BatchMainConfig config;
  private BigQueryService bigQueryService;
  private CloudBillingHelper cloudBillingHelper;

  @Autowired
  @Inject
  public AnomalyDetectionBigQueryServiceImpl(
      BatchMainConfig config, BigQueryService bigQueryService, CloudBillingHelper cloudBillingHelper) {
    this.config = config;
    this.cloudBillingHelper = cloudBillingHelper;
    this.bigQueryService = bigQueryService;
  }

  public List<String> getBatchMetaData(TimeSeriesMetaData timeSeriesMetaData) {
    List<String> hashCodes = new ArrayList<>();
    if (!isTableExists(timeSeriesMetaData.getAccountId())) {
      return hashCodes;
    }

    String queryStatement = timeSeriesMetaData.getCloudQueryMetaData().getMetaDataQuery();
    queryStatement = queryStatement.replace("<Project>.<DataSet>.<TableName>",
        cloudBillingHelper.getCloudProviderTableName(
            config.getBillingDataPipelineConfig().getGcpProjectId(), timeSeriesMetaData.getAccountId(), preAggregated));
    log.info("Step 1 : query statement prepared for meta data : {}", queryStatement);

    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(queryStatement).build();
    try {
      TableResult result = bigQueryService.get().query(queryConfig);
      hashCodes = extractHashCodes(result);
    } catch (Exception e) {
      log.error("failed to fetch batch meta data for account {} , query : {} , exception : {}",
          timeSeriesMetaData.getAccountId(), queryStatement, e);
    }
    return hashCodes;
  }

  private boolean isTableExists(String accountId) {
    try {
      String datasetName = cloudBillingHelper.getDataSetId(accountId);
      Table table = bigQueryService.get().getTable(TableId.of(datasetName, preAggregated));
      if (table == null) {
        return false;
      }
      if (table.exists()) {
        return true;
      }
    } catch (BigQueryException e) {
      log.info("Skipping account {} since gcp/aws connector doesn't exist", accountId);
    }
    return false;
  }

  public List<String> extractHashCodes(TableResult result) {
    List<String> hashCodeList = new ArrayList<>();

    String code;
    for (FieldValueList row : result.iterateAll()) {
      code = row.get("hashcode").getStringValue();
      if (code != null) {
        hashCodeList.add(code);
      }
    }
    return hashCodeList;
  }

  public List<AnomalyDetectionTimeSeries> readBatchData(
      TimeSeriesMetaData timeSeriesMetaData, List<String> batchHashCodes) {
    List<AnomalyDetectionTimeSeries> listTimeSeries = new ArrayList<>();

    String queryStatement = timeSeriesMetaData.getCloudQueryMetaData().getQuery(batchHashCodes);
    queryStatement = queryStatement.replace("<Project>.<DataSet>.<TableName>",
        cloudBillingHelper.getCloudProviderTableName(
            config.getBillingDataPipelineConfig().getGcpProjectId(), timeSeriesMetaData.getAccountId(), preAggregated));
    log.info("STEP 2 : query statement prepared for reading batch data : {}", queryStatement);

    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(queryStatement).build();
    TableResult result = null;

    try {
      result = bigQueryService.get().query(queryConfig);
      listTimeSeries = readTimeSeriesFromResultSet(result, timeSeriesMetaData);
    } catch (InterruptedException | SQLException e) {
      log.error("Failed to fetch Batch data for account {}, exception:{}", timeSeriesMetaData.getAccountId(), e);
    }

    return listTimeSeries;
  }

  public List<AnomalyDetectionTimeSeries> readTimeSeriesFromResultSet(
      TableResult resultSet, TimeSeriesMetaData timeSeriesMetaData) throws SQLException {
    List<AnomalyDetectionTimeSeries> listTimeSeries = new ArrayList<>();
    Iterator<FieldValueList> resultSetIterator = resultSet.iterateAll().iterator();

    if (!resultSetIterator.hasNext()) {
      return listTimeSeries;
    }

    FieldValueList row;
    AnomalyDetectionTimeSeries currentTimeSeries = null;
    String previousHash = null;
    String currentHash;
    Instant currentTime;
    Double currentValue;

    while (resultSetIterator.hasNext()) {
      row = resultSetIterator.next();
      currentHash = row.get("hashcode").getStringValue();
      currentTime = Instant.ofEpochMilli(
          row.get(PreAggregatedTableSchema.startTime.getColumnNameSQL()).getTimestampValue() / 1000);
      currentValue = row.get(timeSeriesMetaData.getCloudQueryMetaData().getAggerateColumnName()).getDoubleValue();
      if (previousHash == null || !previousHash.equals(currentHash)) {
        if (currentTimeSeries != null) {
          if (TimeSeriesUtils.validate(currentTimeSeries, timeSeriesMetaData)) {
            AnomalyDetectionHelper.logValidTimeSeries(currentTimeSeries);
            listTimeSeries.add(currentTimeSeries);
          } else {
            AnomalyDetectionHelper.logInvalidTimeSeries(currentTimeSeries);
          }
        }
        currentTimeSeries = AnomalyDetectionTimeSeries.initialiseNewTimeSeries(timeSeriesMetaData);
        fillMetaInfoToTimeSeries(currentTimeSeries, timeSeriesMetaData, row);
      }
      currentTimeSeries.insert(currentTime, currentValue);
      previousHash = currentHash;
    }
    if (currentTimeSeries != null) {
      if (TimeSeriesUtils.validate(currentTimeSeries, timeSeriesMetaData)) {
        AnomalyDetectionHelper.logValidTimeSeries(currentTimeSeries);
        listTimeSeries.add(currentTimeSeries);
      } else {
        AnomalyDetectionHelper.logInvalidTimeSeries(currentTimeSeries);
      }
    }
    return listTimeSeries;
  }

  private void fillMetaInfoToTimeSeries(AnomalyDetectionTimeSeries currentTimeSeries,
      TimeSeriesMetaData timeSeriesMetaData, FieldValueList row) throws SQLException {
    currentTimeSeries.setAccountId(timeSeriesMetaData.getAccountId());
    List<CloudBillingGroupBy> groupByList = timeSeriesMetaData.getCloudQueryMetaData().getGroupByList();

    for (CloudBillingGroupBy groupBy : groupByList) {
      CloudEntityGroupBy type = groupBy.getEntityGroupBy();
      if (type != null) {
        switch (type) {
          case projectId:
            currentTimeSeries.setGcpProject(
                row.get(PreAggregatedTableSchema.gcpProjectId.getColumnNameSQL()).getStringValue());
            break;
          case product:
            currentTimeSeries.setGcpProduct(
                row.get(PreAggregatedTableSchema.gcpProduct.getColumnNameSQL()).getStringValue());
            break;
          case skuId:
            currentTimeSeries.setGcpSKUId(
                row.get(PreAggregatedTableSchema.gcpSkuId.getColumnNameSQL()).getStringValue());
            break;
          case sku:
            currentTimeSeries.setGcpSKUDescription(
                row.get(PreAggregatedTableSchema.gcpSkuDescription.getColumnNameSQL()).getStringValue());
            break;
          case awsLinkedAccount:
            currentTimeSeries.setAwsAccount(
                row.get(PreAggregatedTableSchema.awsUsageAccountId.getColumnNameSQL()).getStringValue());
            break;
          case awsService:
            currentTimeSeries.setAwsService(
                row.get(PreAggregatedTableSchema.awsServiceCode.getColumnNameSQL()).getStringValue());
            break;
          case awsUsageType:
            currentTimeSeries.setAwsUsageType(
                row.get(PreAggregatedTableSchema.awsUsageType.getColumnNameSQL()).getStringValue());
            break;
          default:
            log.error("Not valid entity type : {} ", type);
            throw new InvalidArgumentsException("Invalid Entity encountered");
        }
      }
    }
  }
}
