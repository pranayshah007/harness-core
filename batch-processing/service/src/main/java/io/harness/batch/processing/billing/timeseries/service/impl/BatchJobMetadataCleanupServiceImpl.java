/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.timeseries.service.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@OwnedBy(HarnessTeam.CE)
@Slf4j
public class BatchJobMetadataCleanupServiceImpl {
  @Autowired private TimeScaleDBService timeScaleDBService;

  private static final int DELETE_MAX_RETRY_COUNT = 2;

  static final String SQL_DELETE_OLD_BATCH_STEP_EXECUTION_CONTEXT =
      "DELETE FROM BATCH_STEP_EXECUTION_CONTEXT WHERE STEP_EXECUTION_ID IN (SELECT STEP_EXECUTION_ID FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID IN (SELECT JOB_EXECUTION_ID FROM  BATCH_JOB_EXECUTION where CREATE_TIME < ?))";
  private static final String SQL_DELETE_BATCH_STEP_EXECUTION =
      "DELETE FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID IN (SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION where CREATE_TIME < ?)";
  private static final String SQL_DELETE_BATCH_JOB_EXECUTION_CONTEXT =
      "DELETE FROM BATCH_JOB_EXECUTION_CONTEXT WHERE JOB_EXECUTION_ID IN (SELECT JOB_EXECUTION_ID FROM  BATCH_JOB_EXECUTION where CREATE_TIME < ?)";
  private static final String SQL_DELETE_BATCH_JOB_EXECUTION_PARAMS =
      "DELETE FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID IN (SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION where CREATE_TIME < ?)";
  private static final String SQL_DELETE_BATCH_JOB_EXECUTION = "DELETE FROM BATCH_JOB_EXECUTION where CREATE_TIME < ?";
  private static final String SQL_DELETE_BATCH_JOB_INSTANCE =
      "DELETE FROM BATCH_JOB_INSTANCE WHERE JOB_INSTANCE_ID NOT IN (SELECT JOB_INSTANCE_ID FROM BATCH_JOB_EXECUTION)";

  public boolean deleteOldBatchStepExecutionContext(int days) {
    Timestamp createTimestamp = getTimestampFromDays(days);
    log.info("Deleting BATCH_STEP_EXECUTION_CONTEXT older than {}", createTimestamp.toString());

    boolean successfulUpdate = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulUpdate && retryCount < DELETE_MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(SQL_DELETE_OLD_BATCH_STEP_EXECUTION_CONTEXT)) {
          statement.setTimestamp(1, createTimestamp);
          log.info("Deleting existing BATCH_STEP_EXECUTION_CONTEXT data: {} ", statement);
          statement.execute();
          successfulUpdate = true;
        } catch (SQLException e) {
          log.error(
              "Failed to delete BATCH_STEP_EXECUTION_CONTEXT data for entries older than CREATE_TIME:{}, retryCount=[{}], Exception: ",
              createTimestamp, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.warn("Couldn't delete older BATCH_STEP_EXECUTION_CONTEXT data.");
    }
    return successfulUpdate;
  }

  public boolean deleteOldBatchStepExecution(int days) {
    Timestamp createTimestamp = getTimestampFromDays(days);
    log.info("Deleting BATCH_STEP_EXECUTION older than {}", createTimestamp.toString());

    boolean successfulUpdate = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulUpdate && retryCount < DELETE_MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(SQL_DELETE_BATCH_STEP_EXECUTION)) {
          statement.setTimestamp(1, createTimestamp);
          log.info("Deleting existing BATCH_STEP_EXECUTION_CONTEXT data: {} ", statement);
          statement.execute();
          successfulUpdate = true;
        } catch (SQLException e) {
          log.error(
              "Failed to delete BATCH_STEP_EXECUTION data for entries older than CREATE_TIME:{}, retryCount=[{}], Exception: ",
              createTimestamp, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.warn("Couldn't delete older BATCH_STEP_EXECUTION data.");
    }
    return successfulUpdate;
  }

  public boolean deleteOldBatchJobExecutionContext(int days) {
    Timestamp createTimestamp = getTimestampFromDays(days);
    log.info("Deleting BATCH_JOB_EXECUTION_CONTEXT older than {}", createTimestamp.toString());

    boolean successfulUpdate = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulUpdate && retryCount < DELETE_MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(SQL_DELETE_BATCH_JOB_EXECUTION_CONTEXT)) {
          statement.setTimestamp(1, createTimestamp);
          log.info("Deleting existing BATCH_JOB_EXECUTION_CONTEXT data: {} ", statement);
          statement.execute();
          successfulUpdate = true;
        } catch (SQLException e) {
          log.error(
              "Failed to delete BATCH_JOB_EXECUTION_CONTEXT data for entries older than CREATE_TIME:{}, retryCount=[{}], Exception: ",
              createTimestamp, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.warn("Couldn't delete older BATCH_JOB_EXECUTION_CONTEXT data.");
    }
    return successfulUpdate;
  }

  public boolean deleteOldBatchJobExecutionParams(int days) {
    Timestamp createTimestamp = getTimestampFromDays(days);
    log.info("Deleting BATCH_JOB_EXECUTION_PARAMS older than {}", createTimestamp.toString());

    boolean successfulUpdate = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulUpdate && retryCount < DELETE_MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(SQL_DELETE_BATCH_JOB_EXECUTION_PARAMS)) {
          statement.setTimestamp(1, createTimestamp);
          log.info("Deleting existing BATCH_JOB_EXECUTION_PARAMS data: {} ", statement);
          statement.execute();
          successfulUpdate = true;
        } catch (SQLException e) {
          log.error(
              "Failed to delete BATCH_JOB_EXECUTION_PARAMS data for entries older than CREATE_TIME:{}, retryCount=[{}], Exception: ",
              createTimestamp, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.warn("Couldn't delete older BATCH_JOB_EXECUTION_PARAMS data.");
    }
    return successfulUpdate;
  }

  public boolean deleteOldBatchJobExecution(int days) {
    Timestamp createTimestamp = getTimestampFromDays(days);
    log.info("Deleting BATCH_JOB_EXECUTION older than {}", createTimestamp.toString());

    boolean successfulUpdate = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulUpdate && retryCount < DELETE_MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(SQL_DELETE_BATCH_JOB_EXECUTION)) {
          statement.setTimestamp(1, createTimestamp);
          log.info("Deleting existing BATCH_JOB_EXECUTION data: {} ", statement);
          statement.execute();
          successfulUpdate = true;
        } catch (SQLException e) {
          log.error(
              "Failed to delete BATCH_JOB_EXECUTION data for entries older than CREATE_TIME:{}, retryCount=[{}], Exception: ",
              createTimestamp, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.warn("Couldn't delete older BATCH_JOB_EXECUTION data.");
    }
    return successfulUpdate;
  }

  public boolean deleteOldBatchJobInstance(int days) {
    Timestamp createTimestamp = getTimestampFromDays(days);
    log.info("Deleting BATCH_JOB_INSTANCE older than {}", createTimestamp);

    boolean successfulUpdate = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulUpdate && retryCount < DELETE_MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(SQL_DELETE_BATCH_JOB_INSTANCE)) {
          log.info("Deleting existing BATCH_JOB_EXECUTION data: {} ", statement);
          statement.execute();
          successfulUpdate = true;
        } catch (SQLException e) {
          log.error(
              "Failed to delete BATCH_JOB_INSTANCE data for entries older than CREATE_TIME:{}, retryCount=[{}], Exception: ",
              createTimestamp, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.warn("Couldn't delete older BATCH_JOB_INSTANCE data.");
    }
    return successfulUpdate;
  }

  @NotNull
  private static Timestamp getTimestampFromDays(int days) {
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);

    Date dateObj = DateUtils.addDays(calendar.getTime(), -days);
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    Timestamp createTimestamp = new Timestamp(dateObj.getTime());
    return createTimestamp;
  }
}
