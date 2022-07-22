/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;

import io.harness.beans.FeatureName;
import io.harness.event.timeseries.processor.utils.DateUtils;
import io.harness.exception.DeploymentMigrationException;
import io.harness.ff.FeatureFlagService;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;
import software.wings.utils.FFUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DeploymentEventProcessor implements EventProcessor<TimeSeriesEventInfo> {
  /**
   *    EXECUTIONID TEXT NOT NULL,
   *    STARTTIME TIMESTAMP NOT NULL,
   *    ENDTIME TIMESTAMP NOT NULL,
   *    ACCOUNTID TEXT NOT NULL,
   *    APPID TEXT NOT NULL,
   *    TRIGGERED_BY TEXT,
   *    TRIGGER_ID TEXT,
   *    STATUS VARCHAR(20),
   *    SERVICES TEXT[],
   *    WORKFLOWS TEXT[],
   *    CLOUDPROVIDERS TEXT[],
   *    ENVIRONMENTS TEXT[],
   *    PIPELINE TEXT,
   *    DURATION BIGINT NOT NULL,
   *    ARTIFACTS TEXT[]
   *    ENVTYPES TEXT[]
   *    PARENT_EXECUTION TEXT
   *    STAGENAME TEXT
   *    ROLLBACK_DURATION BIGINT
   */
  private static final Long sqlMinTimestamp = 0L;
  private static final String query_statement = "SELECT * FROM DEPLOYMENT WHERE EXECUTIONID=?";
  private static final String delete_statement = "DELETE FROM DEPLOYMENT WHERE EXECUTIONID=?";
  private static final String insert_statement =

      "INSERT INTO DEPLOYMENT (EXECUTIONID,STARTTIME,ENDTIME,ACCOUNTID,APPID,TRIGGERED_BY,TRIGGER_ID,STATUS,SERVICES,WORKFLOWS,CLOUDPROVIDERS,ENVIRONMENTS,PIPELINE,DURATION,ARTIFACTS,ENVTYPES,PARENT_EXECUTION,FAILURE_DETAILS,FAILED_STEP_NAMES,FAILED_STEP_TYPES,STAGENAME,ROLLBACK_DURATION, INSTANCES_DEPLOYED, TAGS, PARENT_PIPELINE_ID,WORKFLOWS) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  private static final String delete_statement_migration_parent_table =
      "DELETE FROM DEPLOYMENT_PARENT WHERE EXECUTIONID=?";
  private static final String delete_statement_migration_stage_table =
      "DELETE FROM DEPLOYMENT_STAGE WHERE EXECUTIONID=?";
  private static final String insert_statement_migration_parent_table =
      "INSERT INTO DEPLOYMENT_PARENT (EXECUTIONID,STARTTIME,ENDTIME,ACCOUNTID,APPID,TRIGGERED_BY,TRIGGER_ID,STATUS,SERVICES,WORKFLOWS,CLOUDPROVIDERS,ENVIRONMENTS,PIPELINE,DURATION,ARTIFACTS,ENVTYPES,PARENT_EXECUTION,FAILURE_DETAILS,FAILED_STEP_NAMES,FAILED_STEP_TYPES,STAGENAME,ROLLBACK_DURATION, INSTANCES_DEPLOYED, TAGS,PARENT_PIPELINE_ID,WORKFLOWS) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
  private static final String insert_statement_migration_stage_table =
      "INSERT INTO DEPLOYMENT_STAGE (EXECUTIONID,STARTTIME,ENDTIME,ACCOUNTID,APPID,TRIGGERED_BY,TRIGGER_ID,STATUS,SERVICES,WORKFLOWS,CLOUDPROVIDERS,ENVIRONMENTS,PIPELINE,DURATION,ARTIFACTS,ENVTYPES,PARENT_EXECUTION,FAILURE_DETAILS,FAILED_STEP_NAMES,FAILED_STEP_TYPES,STAGENAME,ROLLBACK_DURATION, INSTANCES_DEPLOYED, TAGS,PARENT_PIPELINE_ID,WORKFLOWS) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  private static final String fetch_account_executions_deployment_in_interval =
      "SELECT EXECUTIONID,STARTTIME,ENDTIME,ACCOUNTID,APPID,TRIGGERED_BY,TRIGGER_ID,STATUS,SERVICES,WORKFLOWS,CLOUDPROVIDERS,ENVIRONMENTS,PIPELINE,DURATION,ARTIFACTS,ENVTYPES,PARENT_EXECUTION,STAGENAME,ROLLBACK_DURATION,INSTANCES_DEPLOYED,TAGS FROM DEPLOYMENT WHERE ACCOUNTID = ? AND STARTTIME >= ? AND STARTTIME <= ? ORDER BY STARTTIME DESC OFFSET ? LIMIT ?";
  private static final String fetch_oldest_parent_execution_migration_completed =
      "SELECT * FROM DEPLOYMENT_PARENT WHERE ACCOUNTID = ? ORDER BY STARTTIME LIMIT 1";
  private static final String fetch_oldest_stage_execution_migration_completed =
      "SELECT * FROM DEPLOYMENT_STAGE WHERE ACCOUNTID = ? ORDER BY STARTTIME LIMIT 1";
  private static final String fetch_nearest_older_execution_to_start_time =
      "SELECT * FROM DEPLOYMENT WHERE ACCOUNTID = ? AND STARTTIME < ? ORDER BY STARTTIME DESC LIMIT 1";

  private static final String OPERATION_INSERT = "INSERT";
  private static final String OPERATION_UPDATE = "UPDATE";
  private static final String SQL_DUPLICATE_KEY_EXCEPTION_CODE = "23505";
  public static final Integer DEFAULT_MIGRATION_QUERY_BATCH_SIZE = 100;
  public static final Integer DEFAULT_MIGRATION_ROW_LIMIT = 1000;

  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject DataFetcherUtils utils;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private FFUtils ffUtils;

  @Override
  public void processEvent(TimeSeriesEventInfo eventInfo) {
    if (timeScaleDBService.isValid()) {
      long startTime = System.currentTimeMillis();
      boolean successful = false;
      int retryCount = 0;
      String operation = null;

      while (!successful && retryCount < MAX_RETRY_COUNT) {
        ResultSet queryResult = null;
        try (Connection connection = timeScaleDBService.getDBConnection();
             PreparedStatement queryStatement = connection.prepareStatement(query_statement);
             PreparedStatement deleteStatement = connection.prepareStatement(delete_statement);
             PreparedStatement insertStatement = connection.prepareStatement(insert_statement)) {
          queryResult = queryDataInTimescaleDB(eventInfo, queryStatement);

          if (queryResult != null && queryResult.next()) {
            operation = OPERATION_UPDATE;
            log.info(
                "WorkflowExecution found:[{}],updating it", eventInfo.getStringData().get(EventProcessor.EXECUTIONID));
            deleteDataInTimescaleDB(eventInfo, deleteStatement);
            insertDataInTimescaleDB(eventInfo, connection, insertStatement);
          } else {
            operation = OPERATION_INSERT;
            log.info("WorkflowExecution not found:[{}],inserting it",
                eventInfo.getStringData().get(EventProcessor.EXECUTIONID));
            insertDataInTimescaleDB(eventInfo, connection, insertStatement);
          }

          // handle realtime data migration
          handleRealtimeEventMigration(connection, eventInfo, operation);

          successful = true;
        } catch (SQLException e) {
          log.error("Failed to save deployment data,[{}],retryCount=[{}] ", eventInfo, retryCount++, e);
        } catch (Exception e) {
          log.error("Failed to save deployment data,[{}]", eventInfo, e);
          retryCount = MAX_RETRY_COUNT + 1;
        } finally {
          DBUtils.close(queryResult);
          log.info("Total time=[{}]", System.currentTimeMillis() - startTime);
        }
      }
    } else {
      log.trace("Not processing deployment time series data:[{}]", eventInfo);
    }
  }

  public void doDataMigration(String accountId, Integer dataMigrationIntervalInHours) {
    if (accountId == null) {
      return;
    }

    // Timestamps denoting data migration interval
    long intervalStartTimestamp = DateUtils.getCurrentTime();
    long intervalEndTimestamp = DateUtils.getCurrentTime();
    final int MAX_RETRY_COUNT = 5;
    int retry = 0;
    boolean isItLastMigrationCycle = false;

    while (true) {
      // Fetch the oldest day data aggregation entry from DB for given account id
      try (Connection dbConnection = timeScaleDBService.getDBConnection();
           PreparedStatement fetchOldestParentExecutionMigratedStatement =
               dbConnection.prepareStatement(fetch_oldest_parent_execution_migration_completed);
           PreparedStatement fetchOldestStageExecutionMigratedStatement =
               dbConnection.prepareStatement(fetch_oldest_stage_execution_migration_completed);
           PreparedStatement fetchMigrationIntervalStartExecutionStatement =
               dbConnection.prepareStatement(fetch_nearest_older_execution_to_start_time);) {
        fetchOldestParentExecutionMigratedStatement.setString(1, accountId);
        ResultSet resultSet = fetchOldestParentExecutionMigratedStatement.executeQuery();
        if (resultSet.next()) {
          // If resultSet has a valid record, set its startTime as intervalEndTimestamp
          // Else migrate starting from current timestamp
          intervalEndTimestamp = resultSet.getTimestamp(EventProcessor.STARTTIME).getTime();
        }

        fetchOldestStageExecutionMigratedStatement.setString(1, accountId);
        resultSet = fetchOldestStageExecutionMigratedStatement.executeQuery();
        if (resultSet.next()) {
          // Update intervalEndTimestamp if stage's startTime is older than last parent migrated
          intervalEndTimestamp =
              Math.min(intervalEndTimestamp, resultSet.getTimestamp(EventProcessor.STARTTIME).getTime());
        }

        intervalStartTimestamp = DateUtils.addHours(intervalEndTimestamp, -dataMigrationIntervalInHours).getTime();

        fetchMigrationIntervalStartExecutionStatement.setString(1, accountId);
        fetchMigrationIntervalStartExecutionStatement.setTimestamp(
            2, new Timestamp(intervalStartTimestamp), utils.getDefaultCalendar());
        // Fetch first older deployment nearest to proposed startTime of the migration interval
        resultSet = fetchMigrationIntervalStartExecutionStatement.executeQuery();
        if (resultSet.next()) {
          // If valid result, then just mark its startTime as migration interval start time
          intervalStartTimestamp = resultSet.getTimestamp(EventProcessor.STARTTIME).getTime();
        } else {
          // No more deployments left to migrate older than startTime of the interval
          // This means it is the last migration cycle for this account
          log.info("Processing the last cycle of deployment data migration for account : {}", accountId);
          isItLastMigrationCycle = true;
        }
        break;
      } catch (SQLException exception) {
        if (retry >= MAX_RETRY_COUNT) {
          String errorLog = String.format(
              "MAX RETRY FAILURE : Failed to do deployment data migration process for account : [%s] , error : [%s]",
              accountId, exception.toString());
          throw new DeploymentMigrationException(errorLog, exception);
        }
        log.error("Failed to do deployment data migration process for account : [{}] , retry : [{}] , error : [{}]",
            accountId, retry, exception.toString(), exception);
        retry++;
      }
    }

    // Do migration in PROCESS_BATCH_SIZE hours in reverse order ( most recent to older )
    try {
      handleBatchIntervalMigration(accountId, intervalStartTimestamp, intervalEndTimestamp,
          getMigrationQueryBatchSize(), getMigrationRowLimit());

      if (isItLastMigrationCycle) {
        // This means that there are no more deployments to be migrated, migration is completed
        log.info("DEPLOYMENT_DATA_MIGRATION_SUCCESS for account : {}", accountId);
        try {
          // Disable deployment data migration cron for the account
          ffUtils.updateFeatureFlagForAccount(
              FeatureName.CUSTOM_DASHBOARD_ENABLE_CRON_DEPLOYMENT_DATA_MIGRATION, accountId, false);
          log.info("Deployment data migration feature flag disabled for account : {}", accountId);
        } catch (Exception exception) {
          String errorLog =
              String.format("Error while disabling deployment data migration cron for account id : [%s]", accountId);
          throw new DeploymentMigrationException(errorLog, exception);
        }
      }
    } catch (Exception exception) {
      log.error(exception.toString(), exception);
      // Throw exception in case calling layer is API/Controller layer and it wants to send it as response
      throw exception;
    }
  }

  public void handleBatchIntervalMigration(String accountId, Long intervalStartTimestamp, Long intervalEndTimestamp,
      final Integer batchSize, final Integer rowLimit) {
    // Fetch instance data points in batches and process them
    boolean isAggregationCompleted = false, isRowLimitReached = false;
    int retry = 0, offset = 0, numOfRowsMigrated = 0;
    final int MAX_RETRY_COUNT = 5;
    List<Map<String, Object>> eventInfoList = null;
    long lastEventTimestamp = 0L;

    try {
      while (!isAggregationCompleted && !isRowLimitReached && retry < MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement fetchStatement =
                 dbConnection.prepareStatement(fetch_account_executions_deployment_in_interval)) {
          fetchStatement.setString(1, accountId);
          fetchStatement.setTimestamp(2, new Timestamp(intervalStartTimestamp), utils.getDefaultCalendar());
          fetchStatement.setTimestamp(3, new Timestamp(intervalEndTimestamp), utils.getDefaultCalendar());
          fetchStatement.setInt(4, offset);
          fetchStatement.setInt(5, batchSize);
          ResultSet resultSet = fetchStatement.executeQuery();
          eventInfoList = parseFetchResults(resultSet);
        } catch (SQLException exception) {
          if (retry >= MAX_RETRY_COUNT) {
            String errorLog = "MAX RETRY FAILURE : Failed to fetch deployments within interval";
            throw new DeploymentMigrationException(errorLog, exception);
          }
          log.error(
              "Failed to fetch deployments within interval for deployment data migration process for account : [{}] from startTimestamp : [{}] to endTimestamp : [{}] , retry : [{}]",
              accountId, intervalStartTimestamp, intervalEndTimestamp, retry, exception);
          retry++;
          continue;
        }

        try (Connection dbConnection = timeScaleDBService.getDBConnection()) {
          for (Map<String, Object> eventInfo : eventInfoList) {
            try {
              handleDeploymentEventMigration(dbConnection, eventInfo);
            } catch (SQLException exception) {
              log.error("Deployment data migration Save failure for event : {}", eventInfo, exception);
              // another attempt
              try {
                handleDeploymentEventMigration(dbConnection, eventInfo);
              } catch (SQLException ex) {
                // Consecutive error means either possibly persistent DB issue or issue with this entry
                String errorLog = "Stopping Deployment data migration process due to consecutive migration failures";
                throw new DeploymentMigrationException(errorLog, ex);
              }
            }
            numOfRowsMigrated++;
            lastEventTimestamp = (Long) eventInfo.get(EventProcessor.STARTTIME);
            if (numOfRowsMigrated >= rowLimit) {
              isRowLimitReached = true;
              break;
            }
          }
        }

        // If result size < batchsize, then it means now interval processing is complete
        if (eventInfoList.size() < batchSize) {
          isAggregationCompleted = true;
          continue;
        }

        // Aggregation of current batch got success, so increase offset to fetch next batch of events
        offset += batchSize;
      }
    } catch (Exception ex) {
      String errorLog = String.format(
          "Failed to do deployment data migration process for account : [%s] from startTimestamp : [%d] to endTimestamp : [%d] , error : [%s]",
          accountId, intervalStartTimestamp, intervalEndTimestamp, ex.toString());
      // In case of unknown exception, just halt the processing
      throw new DeploymentMigrationException(errorLog, ex);
    }

    if (isRowLimitReached) {
      log.info(
          "MIGRATION ROW LIMIT REACHED : Deployment data migration completed for account : [{}] from startTimestamp : [{}] to endTimestamp : [{}]",
          accountId, lastEventTimestamp, intervalEndTimestamp);
    } else {
      log.info(
          "Deployment data migration completed for account : [{}] from startTimestamp : [{}] to endTimestamp : [{}]",
          accountId, intervalStartTimestamp, intervalEndTimestamp);
    }
  }

  private void handleDeploymentEventMigration(Connection dbConnection, Map<String, Object> eventInfo)
      throws SQLException {
    String deleteSqlStatement = null, insertSqlStatement = null;
    if (eventInfo.get(EventProcessor.PARENT_EXECUTION) == null) {
      deleteSqlStatement = delete_statement_migration_parent_table;
      insertSqlStatement = insert_statement_migration_parent_table;
    } else {
      deleteSqlStatement = delete_statement_migration_stage_table;
      insertSqlStatement = insert_statement_migration_stage_table;
    }

    // Do delete and insert to make sure any data isn't missed or replicated while migration
    try (PreparedStatement deleteStatement = dbConnection.prepareStatement(deleteSqlStatement);
         PreparedStatement insertStatement = dbConnection.prepareStatement(insertSqlStatement)) {
      deleteStatement.setString(1, (String) eventInfo.get(EventProcessor.EXECUTIONID));
      deleteStatement.execute();
      //      log.info("delete statement : {}", deleteStatement);

      int index = 0;
      insertStatement.setString(++index, (String) eventInfo.get(EventProcessor.EXECUTIONID));
      insertStatement.setTimestamp(
          ++index, new Timestamp((Long) eventInfo.get(EventProcessor.STARTTIME)), utils.getDefaultCalendar());
      insertStatement.setTimestamp(
          ++index, new Timestamp((Long) eventInfo.get(EventProcessor.ENDTIME)), utils.getDefaultCalendar());
      insertStatement.setString(++index, (String) eventInfo.get(EventProcessor.ACCOUNTID));
      insertStatement.setString(++index, (String) eventInfo.get(EventProcessor.APPID));
      insertStatement.setString(++index, (String) eventInfo.get(EventProcessor.TRIGGERED_BY));
      insertStatement.setString(++index, (String) eventInfo.get(EventProcessor.TRIGGER_ID));
      insertStatement.setString(++index, (String) eventInfo.get(EventProcessor.STATUS));
      insertStatement.setArray(++index, (Array) eventInfo.get(EventProcessor.SERVICE_LIST));
      insertStatement.setArray(++index, (Array) eventInfo.get(EventProcessor.WORKFLOW_LIST));
      insertStatement.setArray(++index, (Array) eventInfo.get(EventProcessor.CLOUD_PROVIDER_LIST));
      insertStatement.setArray(++index, (Array) eventInfo.get(EventProcessor.SERVICE_LIST));
      insertStatement.setString(++index, (String) eventInfo.get(EventProcessor.PIPELINE));
      insertStatement.setLong(++index, (Long) eventInfo.get(EventProcessor.DURATION));
      insertStatement.setArray(++index, (Array) eventInfo.get(EventProcessor.ARTIFACT_LIST));
      insertStatement.setArray(++index, (Array) eventInfo.get(EventProcessor.ENVTYPES));
      insertStatement.setString(++index, (String) eventInfo.get(EventProcessor.PARENT_EXECUTION));
      insertStatement.setString(++index, (String) eventInfo.get(EventProcessor.STAGENAME));
      insertStatement.setLong(++index, (Long) eventInfo.get(EventProcessor.ROLLBACK_DURATION));
      insertStatement.setInt(++index, (Integer) eventInfo.get(EventProcessor.INSTANCES_DEPLOYED));
      insertStatement.setObject(++index, eventInfo.get(EventProcessor.TAGS));
      insertStatement.setString(++index, (String) eventInfo.get(EventProcessor.PARENT_PIPELINE_ID));
      insertStatement.setArray(++index, (Array) eventInfo.get(EventProcessor.WORKFLOWS));

      try {
        insertStatement.execute();
        //      log.info("insert statement : {}", insertStatement);
      } catch (SQLException exception) {
        if (SQL_DUPLICATE_KEY_EXCEPTION_CODE.equals(exception.getSQLState())) {
          log.error("Deployment data migration insertion failed due to duplicate key error for statement : {}",
              insertStatement, exception);
        } else {
          throw exception;
        }
      }
    }
  }

  private List<Map<String, Object>> parseFetchResults(ResultSet resultSet) throws SQLException {
    List<Map<String, Object>> eventInfoList = new ArrayList<>();

    while (resultSet.next()) {
      int index = 1;
      Map<String, Object> eventInfo = new HashMap<>();
      eventInfo.put(EventProcessor.EXECUTIONID, resultSet.getString(index++));
      eventInfo.put(EventProcessor.STARTTIME, resultSet.getTimestamp(index++).getTime());
      eventInfo.put(EventProcessor.ENDTIME, resultSet.getTimestamp(index++).getTime());
      eventInfo.put(EventProcessor.ACCOUNTID, resultSet.getString(index++));
      eventInfo.put(EventProcessor.APPID, resultSet.getString(index++));
      eventInfo.put(EventProcessor.TRIGGERED_BY, resultSet.getString(index++));
      eventInfo.put(EventProcessor.TRIGGER_ID, resultSet.getString(index++));
      eventInfo.put(EventProcessor.STATUS, resultSet.getString(index++));
      eventInfo.put(EventProcessor.SERVICE_LIST, resultSet.getArray(index++));
      eventInfo.put(EventProcessor.WORKFLOW_LIST, resultSet.getArray(index++));
      eventInfo.put(EventProcessor.CLOUD_PROVIDER_LIST, resultSet.getArray(index++));
      eventInfo.put(EventProcessor.ENV_LIST, resultSet.getArray(index++));
      eventInfo.put(EventProcessor.PIPELINE, resultSet.getString(index++));
      eventInfo.put(EventProcessor.DURATION, resultSet.getLong(index++));
      eventInfo.put(EventProcessor.ARTIFACT_LIST, resultSet.getArray(index++));
      eventInfo.put(EventProcessor.ENVTYPES, resultSet.getArray(index++));
      eventInfo.put(EventProcessor.PARENT_EXECUTION, resultSet.getString(index++));
      eventInfo.put(EventProcessor.FAILURE_DETAILS, resultSet.getString(index++));
      eventInfo.put(EventProcessor.FAILED_STEP_NAMES, resultSet.getString(index++));
      eventInfo.put(EventProcessor.FAILED_STEP_TYPES, resultSet.getString(index++));
      eventInfo.put(EventProcessor.STAGENAME, resultSet.getString(index++));
      eventInfo.put(EventProcessor.ROLLBACK_DURATION, resultSet.getLong(index++));
      eventInfo.put(EventProcessor.INSTANCES_DEPLOYED, resultSet.getInt(index++));
      eventInfo.put(EventProcessor.TAGS, resultSet.getObject(index++));
      eventInfo.put(EventProcessor.PARENT_PIPELINE_ID, resultSet.getString(index++));
      eventInfo.put(EventProcessor.WORKFLOWS, resultSet.getArray(index++));

      eventInfoList.add(eventInfo);
    }

    return eventInfoList;
  }

  private void handleRealtimeEventMigration(Connection dbConnection, TimeSeriesEventInfo eventInfo, String operation)
      throws SQLException {
    if (!featureFlagService.isEnabled(
            FeatureName.CUSTOM_DASHBOARD_ENABLE_REALTIME_DEPLOYMENT_MIGRATION, eventInfo.getAccountId())) {
      return;
    }

    String deleteSqlStatement = null, insertSqlStatement = null;
    if (getParentExecution(eventInfo) == null) {
      deleteSqlStatement = delete_statement_migration_parent_table;
      insertSqlStatement = insert_statement_migration_parent_table;
    } else {
      deleteSqlStatement = delete_statement_migration_stage_table;
      insertSqlStatement = insert_statement_migration_stage_table;
    }

    if (OPERATION_UPDATE.equals(operation)) {
      PreparedStatement deleteStatement = dbConnection.prepareStatement(deleteSqlStatement);
      deleteDataInTimescaleDB(eventInfo, deleteStatement);
    }

    PreparedStatement insertStatement = dbConnection.prepareStatement(insertSqlStatement);
    try {
      insertDataInTimescaleDB(eventInfo, dbConnection, insertStatement);
    } catch (SQLException exception) {
      if (SQL_DUPLICATE_KEY_EXCEPTION_CODE.equals(exception.getSQLState())) {
        log.error(
            "Deployment insertion failed due to duplicate key error for statement : {}", insertStatement, exception);
      } else {
        throw exception;
      }
    }
  }

  private ResultSet queryDataInTimescaleDB(TimeSeriesEventInfo eventInfo, PreparedStatement queryStatement)
      throws SQLException {
    queryStatement.setString(1, eventInfo.getStringData().get(EventProcessor.EXECUTIONID));
    return queryStatement.executeQuery();
  }

  private void deleteDataInTimescaleDB(TimeSeriesEventInfo eventInfo, PreparedStatement deleteStatement)
      throws SQLException {
    deleteStatement.setString(1, eventInfo.getStringData().get(EventProcessor.EXECUTIONID));
    deleteStatement.execute();
  }

  private void insertDataInTimescaleDB(
      TimeSeriesEventInfo eventInfo, Connection dbConnection, PreparedStatement insertStatement) throws SQLException {
    int index = 0;

    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.EXECUTIONID));
    insertStatement.setTimestamp(
        ++index, new Timestamp(eventInfo.getLongData().get(EventProcessor.STARTTIME)), utils.getDefaultCalendar());

    Long endtime = eventInfo.getLongData().get(EventProcessor.ENDTIME);
    if (endtime != null) {
      insertStatement.setTimestamp(
          ++index, new Timestamp(eventInfo.getLongData().get(EventProcessor.ENDTIME)), utils.getDefaultCalendar());
    } else {
      insertStatement.setTimestamp(++index, new Timestamp(sqlMinTimestamp), utils.getDefaultCalendar());
    }

    insertStatement.setString(++index, eventInfo.getAccountId());
    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.APPID));
    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.TRIGGERED_BY));
    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.TRIGGER_ID));
    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.STATUS));

    if (eventInfo.getListData() == null) {
      log.warn("TimeSeriesEventInfo has listData=null:[{}]", eventInfo);
    }

    insertArrayData(dbConnection, insertStatement, getListData(eventInfo, EventProcessor.SERVICE_LIST), ++index);
    insertArrayData(dbConnection, insertStatement, getListData(eventInfo, EventProcessor.WORKFLOW_LIST), ++index);
    insertArrayData(dbConnection, insertStatement, getListData(eventInfo, EventProcessor.CLOUD_PROVIDER_LIST), ++index);
    insertArrayData(dbConnection, insertStatement, getListData(eventInfo, EventProcessor.ENV_LIST), ++index);

    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.PIPELINE));

    Long duration = getDuration(eventInfo);
    insertStatement.setLong(++index, duration);

    insertArrayData(dbConnection, insertStatement, getListData(eventInfo, EventProcessor.ARTIFACT_LIST), ++index);
    insertArrayData(dbConnection, insertStatement, getListData(eventInfo, EventProcessor.ENVTYPES), ++index);

    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.PARENT_EXECUTION));
    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.FAILURE_DETAILS));
    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.FAILED_STEP_NAMES));
    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.FAILED_STEP_TYPES));
    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.STAGENAME));

    Long rollbackDuration = getRollbackDuration(eventInfo);
    insertStatement.setLong(++index, rollbackDuration);

    Integer instancesDeployed = getInstancesDeployed(eventInfo);
    insertStatement.setInt(++index, instancesDeployed);
    insertStatement.setObject(++index, eventInfo.getData().get(EventProcessor.TAGS));

    insertStatement.setString(++index, eventInfo.getStringData().get(EventProcessor.PARENT_PIPELINE_ID));
    insertArrayData(dbConnection, insertStatement, getListData(eventInfo, EventProcessor.WORKFLOWS), ++index);

    insertStatement.execute();
  }

  private Long getDuration(TimeSeriesEventInfo eventInfo) {
    Long duration = eventInfo.getLongData().get(EventProcessor.DURATION);
    if (duration == null) {
      duration = 0L;
    }
    return duration;
  }

  private Long getRollbackDuration(TimeSeriesEventInfo eventInfo) {
    Long rollbackDuration = eventInfo.getLongData().get(EventProcessor.ROLLBACK_DURATION);
    if (rollbackDuration == null) {
      rollbackDuration = 0L;
    }
    return rollbackDuration;
  }

  private Integer getInstancesDeployed(TimeSeriesEventInfo eventInfo) {
    Integer instancesDeployed = eventInfo.getIntegerData().get(EventProcessor.INSTANCES_DEPLOYED);
    if (instancesDeployed == null) {
      instancesDeployed = 0;
    }
    return instancesDeployed;
  }

  private List<String> getListData(TimeSeriesEventInfo eventInfo, String key) {
    return eventInfo.getListData() != null ? eventInfo.getListData().get(key) : new ArrayList<>();
  }

  private void insertArrayData(Connection dbConnection, PreparedStatement insertPreparedStatement, List<String> data,
      int index) throws SQLException {
    if (!Lists.isNullOrEmpty(data)) {
      Array array = dbConnection.createArrayOf("text", data.toArray());
      insertPreparedStatement.setArray(index, array);
    } else {
      insertPreparedStatement.setArray(index, null);
    }
  }

  private String getParentExecution(TimeSeriesEventInfo eventInfo) {
    return eventInfo.getStringData().get(EventProcessor.PARENT_EXECUTION);
  }

  private Integer getMigrationRowLimit() {
    return timeScaleDBService.getTimeScaleDBConfig().getDeploymentDataMigrationRowLimit() > 0
        ? timeScaleDBService.getTimeScaleDBConfig().getDeploymentDataMigrationRowLimit()
        : DEFAULT_MIGRATION_ROW_LIMIT;
  }

  private Integer getMigrationQueryBatchSize() {
    return timeScaleDBService.getTimeScaleDBConfig().getDeploymentDataMigrationQueryBatchSize() > 0
        ? timeScaleDBService.getTimeScaleDBConfig().getDeploymentDataMigrationQueryBatchSize()
        : DEFAULT_MIGRATION_QUERY_BATCH_SIZE;
  }
}
