/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.timeseries.processor.instanceeventprocessor.instancereconservice;

import io.harness.beans.FeatureName;
import io.harness.event.timeseries.processor.EventProcessor;
import io.harness.event.timeseries.processor.instanceeventprocessor.InstanceEventAggregator;
import io.harness.event.timeseries.processor.utils.DateUtils;
import io.harness.exception.InstanceAggregationException;
import io.harness.exception.InstanceMigrationException;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.service.impl.event.timeseries.TimeSeriesBatchEventInfo;
import software.wings.utils.FFUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class InstanceReconServiceImpl implements IInstanceReconService {
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject private DataFetcherUtils utils;
  @Inject private InstanceEventAggregator instanceEventAggregator;
  @Inject private FFUtils ffUtils;

  /**
   * Do data migration of existing instance stats data into aggregated form
   * It does migration of data *dataMigrationIntervalHours* older than last complete aggregated hour (including)
   * for given account id
   *
   * @param accountId
   * @param dataMigrationIntervalInHours
   * @throws Exception
   */
  @Override
  public void doDataMigration(String accountId, Integer dataMigrationIntervalInHours) throws Exception {
    if (accountId == null) {
      return;
    }

    // Timestamps denoting data migration interval
    long intervalStartTimestamp = DateUtils.getCurrentTime();
    long intervalEndTimestamp = DateUtils.getCurrentTime();
    long retry = 0;
    boolean isItLastMigrationCycle = false;

    while (true) {
      // Fetch the oldest day data aggregation entry from DB for given account id
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           PreparedStatement fetchOldestCompleteHourRecordStatement =
               dbConnection.prepareStatement(InstanceReconConstants.FETCH_INSTANCE_STATS_HOUR_OLDEST_COMPLETE_RECORD);
           PreparedStatement fetchNearstInstanceDataPointStatement = dbConnection.prepareStatement(
               InstanceReconConstants.FETCH_NEAREST_OLDER_INSTANCE_STATS_DATA_POINT_TO_REPORTED_AT)) {
        fetchOldestCompleteHourRecordStatement.setString(1, accountId);
        ResultSet resultSet = fetchOldestCompleteHourRecordStatement.executeQuery();
        if (resultSet.next()) {
          // Fetched last complete hour processed/aggregated successfully
          // If resultSet has a valid record, set its reportedAt as intervalEndTimestamp and start aggregation
          // Else aggregate starting from current timestamp
          intervalEndTimestamp = resultSet.getTimestamp(EventProcessor.REPORTEDAT).getTime();
        }

        // Find interval start timestamp acc to the data migration interval
        intervalStartTimestamp = DateUtils.addHours(intervalEndTimestamp, -dataMigrationIntervalInHours).getTime();

        fetchNearstInstanceDataPointStatement.setString(1, accountId);
        fetchNearstInstanceDataPointStatement.setTimestamp(
            2, new Timestamp(intervalStartTimestamp), utils.getDefaultCalendar());
        resultSet = fetchNearstInstanceDataPointStatement.executeQuery();
        if (resultSet.next()) {
          // Include this data point in migration interval to ensure we migrate over a non-empty interval
          intervalStartTimestamp = resultSet.getTimestamp(EventProcessor.REPORTEDAT).getTime();
        } else {
          // If we don't find any data points older than current migration interval, this is the last migration cycle
          log.info("Processing the last cycle of instance data migration for account : {}", accountId);
          isItLastMigrationCycle = true;
        }
        break;
      } catch (SQLException exception) {
        if (retry >= InstanceReconConstants.MAX_RETRY_COUNT) {
          String errorLog = String.format(
              "MAX RETRY FAILURE : Failed while fetching data for getting instance data migration interval for account : [%s] , error : [%s]",
              accountId, exception.toString());
          throw new InstanceMigrationException(errorLog, exception);
        }
        log.error(
            "Failed while fetching data for getting instance data migration interval for account : [{}] , retry : [{}] , error : [{}]",
            accountId, retry, exception.toString(), exception);
        retry++;
      }
    }

    // Do migration in PROCESS_BATCH_SIZE hours in reverse order ( most recent to older )
    try {
      // It seems to be redundant aggregation starting from last successful hour aggregation timestamp
      // Its required to make sure all instance events for given timestamp were processed for the given account
      aggregateEventsForGivenInterval(accountId, intervalStartTimestamp, intervalEndTimestamp,
          getMigrationQueryBatchSize(), getMigrationEventsLimit());
      if (isItLastMigrationCycle) {
        // This means that there are no more deployments to be migrated, migration is completed
        log.info("INSTANCE_DATA_MIGRATION_SUCCESS for account : {}", accountId);
        try {
          // Disable data migration for the account
          ffUtils.updateFeatureFlagForAccount(
              FeatureName.CUSTOM_DASHBOARD_ENABLE_CRON_INSTANCE_DATA_MIGRATION, accountId, false);
          log.info("Instance data migration cron feature flag disabled for account : {}", accountId);
        } catch (Exception exception) {
          String errorLog =
              String.format("Error while disabling instance data migration cron for account id : [%s]", accountId);
          throw new InstanceMigrationException(errorLog, exception);
        }
      }
    } catch (Exception exception) {
      log.error(exception.toString(), exception);
      // Throw exception in case calling layer is API/Controller layer and it wants to send it as response
      throw exception;
    }
  }

  /**
   * Aggregate event from instance stats table for given account id for given interval [interval start, interval end]
   * Aggregation done in batches, and in reverse order of time starting for latest to oldest records
   * Aggregation stops if there are no records found for a batch query or if row limit is reached
   *
   * @param accountId
   * @param intervalStartTimestamp
   * @param intervalEndTimestamp
   * @param batchSize limit on num of instance stats records to migrate in single go, used for throttling purpose
   * @param eventsLimit limit on num of events to migrate : a single event corresponds to all instance stats for a given
   *                    timestamp
   * @throws InstanceMigrationException
   */
  public void aggregateEventsForGivenInterval(String accountId, Long intervalStartTimestamp, Long intervalEndTimestamp,
      final Integer batchSize, final Integer eventsLimit) throws InstanceMigrationException {
    // Fetch instance data points in batches and process them
    boolean isAggregationCompleted = false, isEventsLimitReached = false;
    int retry = 0;
    int offset = 0;
    Set<Long> eventsProcessed = new HashSet<>();
    List<TimeSeriesBatchEventInfo> eventInfoList = null;
    long lastEventTimestamp = 0L;

    try {
      while (!isAggregationCompleted && !isEventsLimitReached && retry < InstanceReconConstants.MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement fetchStatement =
                 dbConnection.prepareStatement(InstanceReconConstants.FETCH_INSTANCE_DATA_POINTS_INTERVAL_BATCH_SQL)) {
          fetchStatement.setString(1, accountId);
          fetchStatement.setTimestamp(2, new Timestamp(intervalStartTimestamp), utils.getDefaultCalendar());
          fetchStatement.setTimestamp(3, new Timestamp(intervalEndTimestamp), utils.getDefaultCalendar());
          fetchStatement.setInt(4, offset);
          fetchStatement.setInt(5, batchSize);
          ResultSet resultSet = fetchStatement.executeQuery();

          eventInfoList = parseResults(resultSet, accountId);
        } catch (SQLException exception) {
          if (retry >= InstanceReconConstants.MAX_RETRY_COUNT) {
            String errorLog = "MAX RETRY FAILURE : Failed to fetch instance data points within interval";
            throw new InstanceMigrationException(errorLog, exception);
          }
          log.error(
              "Failed to fetch instance data points during instance data migration process for account : [{}] from startTimestamp : [{}] to endTimestamp : [{}], retry : [{}]",
              accountId, intervalStartTimestamp, intervalEndTimestamp, retry, exception);
          retry++;
          continue;
        }

        // Check if eventInfo is null/empty, then complete interval processing has been done
        if (eventInfoList.isEmpty()) {
          isAggregationCompleted = true;
          continue;
        }

        for (TimeSeriesBatchEventInfo eventInfo : eventInfoList) {
          try {
            instanceEventAggregator.doHourlyAggregation(eventInfo);
            eventsProcessed.add(eventInfo.getTimestamp());
            lastEventTimestamp = eventInfo.getTimestamp();
            if (eventsProcessed.size() >= eventsLimit) {
              isEventsLimitReached = true;
              break;
            }
          } catch (InstanceAggregationException exception) {
            // As we stop the recon process midway, there are chances that some events got successfully
            // processed while others were left incomplete for a given timestamp
            // So as backup, need to make sure in data migration that this timestamp is processed again
            String errorLog = "Stopping instance data migration due to agregation failure";
            throw new InstanceMigrationException(errorLog, exception);
          }
        }

        // Aggregation of current batch got success, so increase offset to fetch next batch of events
        offset += batchSize;
      }
    } catch (Exception ex) {
      String errorLog = String.format(
          "Unchecked Exception : Failed to do instance data migration process for account : [%s] from startTimestamp : [%d] to endTimestamp : [%d], error : [%s]",
          accountId, intervalStartTimestamp, intervalEndTimestamp, ex.toString());
      // In case of unknown exception, just halt the processing
      throw new InstanceMigrationException(errorLog, ex);
    }

    if (isEventsLimitReached) {
      log.info(
          "MIGRATION EVENTS LIMIT REACHED : Instance data migration completed for account : [{}] from startTimestamp : [{}] to endTimestamp : [{}]",
          accountId, lastEventTimestamp, intervalEndTimestamp);
    } else {
      log.info("Instance data migration completed for account : [{}] from startTimestamp : [{}] to endTimestamp : [{}]",
          accountId, intervalStartTimestamp, intervalEndTimestamp);
    }
  }

  // ------------- PRIVATE METHODS --------------

  // Parse resultSet and prepare eventInfo list containing all data points for given account id and timestamp
  // combination in sorted order starting from latest to oldest
  private List<TimeSeriesBatchEventInfo> parseResults(ResultSet resultSet, String accountId) throws SQLException {
    List<TimeSeriesBatchEventInfo> eventInfoList = new ArrayList<>();
    List<TimeSeriesBatchEventInfo.DataPoint> dataPoints = new ArrayList<>();
    Long prevReportedAt = null;

    while (resultSet.next()) {
      Timestamp reportedAt = resultSet.getTimestamp(EventProcessor.REPORTEDAT);
      if (prevReportedAt == null) {
        // init once
        prevReportedAt = reportedAt.getTime();
      }

      if (!prevReportedAt.equals(reportedAt.getTime())) {
        // Its a new timestamp, so create a new event info object
        eventInfoList.add(TimeSeriesBatchEventInfo.builder()
                              .accountId(accountId)
                              .timestamp(prevReportedAt)
                              .dataPointList(dataPoints)
                              .build());
        dataPoints = new ArrayList<>();
        prevReportedAt = reportedAt.getTime();
      }

      Map<String, Object> dataMap = new HashMap<>();
      dataMap.put(EventProcessor.APPID, resultSet.getString(EventProcessor.APPID));
      dataMap.put(EventProcessor.SERVICEID, resultSet.getString(EventProcessor.SERVICEID));
      dataMap.put(EventProcessor.ENVID, resultSet.getString(EventProcessor.ENVID));
      dataMap.put(EventProcessor.CLOUDPROVIDERID, resultSet.getString(EventProcessor.CLOUDPROVIDERID));
      dataMap.put(EventProcessor.INSTANCETYPE, resultSet.getString(EventProcessor.INSTANCETYPE));
      dataMap.put(EventProcessor.ARTIFACTID, resultSet.getString(EventProcessor.ARTIFACTID));
      dataMap.put(EventProcessor.INSTANCECOUNT, resultSet.getString(EventProcessor.INSTANCECOUNT));
      dataPoints.add(TimeSeriesBatchEventInfo.DataPoint.builder().data(dataMap).build());
    }

    if (!dataPoints.isEmpty()) {
      // Its a new timestamp, so create a new event info object
      eventInfoList.add(TimeSeriesBatchEventInfo.builder()
                            .accountId(accountId)
                            .timestamp(prevReportedAt)
                            .dataPointList(dataPoints)
                            .build());
    }

    return eventInfoList;
  }

  private Integer getMigrationEventsLimit() {
    return timeScaleDBService.getTimeScaleDBConfig().getInstanceStatsMigrationEventsLimit() > 0
        ? timeScaleDBService.getTimeScaleDBConfig().getInstanceStatsMigrationEventsLimit()
        : InstanceReconConstants.DEFAULT_EVENTS_LIMIT;
  }

  private Integer getMigrationQueryBatchSize() {
    return timeScaleDBService.getTimeScaleDBConfig().getInstanceStatsMigrationQueryBatchSize() > 0
        ? timeScaleDBService.getTimeScaleDBConfig().getInstanceStatsMigrationQueryBatchSize()
        : InstanceReconConstants.DEFAULT_QUERY_BATCH_SIZE;
  }
}
