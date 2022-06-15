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
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.billing.BillingDataTableSchema;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;

import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class AnomalyDetectionTimescaleDataServiceImpl {
  private TimeScaleDBService dbService;
  private BillingDataTableSchema tableSchema = new BillingDataTableSchema();
  private static final int MAX_RETRY_COUNT = 2;

  @Autowired
  public AnomalyDetectionTimescaleDataServiceImpl(TimeScaleDBService dbService) {
    this.dbService = dbService;
  }

  public List<AnomalyDetectionTimeSeries> readData(TimeSeriesMetaData timeSeriesMetaData) {
    List<AnomalyDetectionTimeSeries> timeSeriesList = new ArrayList<>();
    boolean successfulRead = false;
    String queryStatement = timeSeriesMetaData.getK8sQueryMetaData().getQuery();
    log.info("STEP 1 : Prepared Query Statement for reading Time Series Data :  {} ", queryStatement);

    ResultSet resultSet = null;
    if (dbService.isValid()) {
      int retryCount = 0;
      while (!successfulRead && retryCount < MAX_RETRY_COUNT) {
        try (Connection dbConnection = dbService.getDBConnection();
             Statement statement = dbConnection.createStatement()) {
          resultSet = statement.executeQuery(queryStatement);
          timeSeriesList = readTimeSeriesFromResultSet(resultSet, timeSeriesMetaData);
          successfulRead = true;
        } catch (SQLException e) {
          log.error("Failed to fetch time series for accountId ,[{}],retryCount=[{}], Exception: [{}]",
              timeSeriesMetaData.getAccountId(), retryCount, e);
          retryCount++;
        } finally {
          DBUtils.close(resultSet);
        }
      }
    }
    return timeSeriesList;
  }

  public List<AnomalyDetectionTimeSeries> readTimeSeriesFromResultSet(
      ResultSet resultSet, TimeSeriesMetaData timeSeriesMetaData) throws SQLException {
    List<AnomalyDetectionTimeSeries> listTimeSeries = new ArrayList<>();
    AnomalyDetectionTimeSeries currentTimeSeries = null;
    String previousHash = null;
    String currentHash;
    Instant currentTime;
    double currentValue;

    while (resultSet.next()) {
      currentHash = resultSet.getString("hashcode");
      currentTime = resultSet.getTimestamp(tableSchema.getStartTime().getName()).toInstant();
      currentValue = resultSet.getDouble("cost");

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
        fillMetaInfoToTimeSeries(currentTimeSeries, timeSeriesMetaData, resultSet);
      }
      currentTimeSeries.insert(currentTime, currentValue);
      previousHash = currentHash;
    }

    if (!resultSet.isBeforeFirst() && currentTimeSeries != null) {
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
      TimeSeriesMetaData timeSeriesMetaData, ResultSet resultSet) throws SQLException {
    List<QLCCMEntityGroupBy> groupByList = timeSeriesMetaData.getK8sQueryMetaData().getGroupByList();
    if (groupByList.contains(QLCCMEntityGroupBy.Cluster)) {
      currentTimeSeries.setClusterId(resultSet.getString(tableSchema.getClusterId().getColumnNameSQL()));
      currentTimeSeries.setClusterName(resultSet.getString(tableSchema.getClusterName().getColumnNameSQL()));
    }
    if (groupByList.contains(QLCCMEntityGroupBy.Namespace)) {
      currentTimeSeries.setNamespace(resultSet.getString(tableSchema.getNamespace().getColumnNameSQL()));
    }
    if (groupByList.contains(QLCCMEntityGroupBy.WorkloadName)) {
      currentTimeSeries.setWorkloadName(resultSet.getString(tableSchema.getWorkloadName().getColumnNameSQL()));
    }
    if (groupByList.contains(QLCCMEntityGroupBy.WorkloadType)) {
      currentTimeSeries.setWorkloadType(resultSet.getString(tableSchema.getWorkloadType().getColumnNameSQL()));
    }
  }
}
