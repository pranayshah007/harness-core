/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;

import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.service.impl.event.timeseries.TimeSeriesEventInfo;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DeploymentStepEventProcessor implements StepEventProcessor<TimeSeriesEventInfo> {
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject DataFetcherUtils utils;

  private static final String upsert_statement =
      "INSERT INTO DEPLOYMENT_STEP (ID,ACCOUNT_ID,APP_ID,STEP_NAME,STEP_TYPE,STATUS,FAILURE_DETAILS,START_TIME,END_TIME,DURATION,STAGE_NAME,EXECUTION_ID,APPROVED_BY,APPROVAL_TYPE,APPROVED_AT,APPROVAL_COMMENT,APPROVAL_EXPIRY,MANUAL_INTERVENTION) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (ID,START_TIME) DO UPDATE SET ACCOUNT_ID = excluded.ACCOUNT_ID,APP_ID = excluded.APP_ID,STEP_NAME = excluded.STEP_NAME,STEP_TYPE = excluded.STEP_TYPE,STATUS = excluded.STATUS,FAILURE_DETAILS = excluded.FAILURE_DETAILS,END_TIME = excluded.END_TIME,DURATION = excluded.DURATION,STAGE_NAME = excluded.STAGE_NAME,EXECUTION_ID = excluded.EXECUTION_ID,APPROVED_BY = excluded.APPROVED_BY,APPROVAL_TYPE = excluded.APPROVAL_TYPE,APPROVED_AT = excluded.APPROVED_AT,APPROVAL_COMMENT = excluded.APPROVAL_COMMENT,APPROVAL_EXPIRY = excluded.APPROVAL_EXPIRY,MANUAL_INTERVENTION = excluded.MANUAL_INTERVENTION";

  @Override
  public void processEvent(TimeSeriesEventInfo eventInfo) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not saving step deployment data to TimeScaleDB");
      return;
    }
    if (eventInfo.getAccountId() == null || eventInfo.getLongData() == null || eventInfo.getStringData() == null
        || eventInfo.getBooleanData() == null) {
      log.info("Invalid TimeSeriesEventInfo [{}] , not saving step deployment data to TimeScaleDB", eventInfo);
      return;
    }
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;

    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement upsertStatement = connection.prepareStatement(upsert_statement)) {
        upsertDataToTimescaleDB(eventInfo, upsertStatement);
        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to save step deployment data,[{}],retryCount=[{}] ", eventInfo, retryCount++, e);
        } else {
          log.error("Failed to save step deployment data,[{}]", eventInfo, e);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save step deployment data,[{}]", eventInfo, e);
        retryCount = MAX_RETRY + 1;
      } finally {
        log.info("Total time=[{}]", System.currentTimeMillis() - startTime);
      }
    }
  }

  public void upsertDataToTimescaleDB(TimeSeriesEventInfo eventInfo, PreparedStatement upsertStatement)
      throws SQLException {
    int index = 0;
    upsertStatement.setString(++index, eventInfo.getStringData().get(ID));
    upsertStatement.setString(++index, eventInfo.getAccountId());
    upsertStatement.setString(++index, eventInfo.getStringData().get(APP_ID));
    upsertStatement.setString(++index, eventInfo.getStringData().get(STEP_NAME));
    upsertStatement.setString(++index, eventInfo.getStringData().get(STEP_TYPE));
    upsertStatement.setString(++index, eventInfo.getStringData().get(STATUS));
    upsertStatement.setString(++index, eventInfo.getStringData().get(FAILURE_DETAILS));

    ++index;
    ProcessorHelper.setTimeStamp(START_TIME, index, eventInfo, upsertStatement, utils);

    ++index;
    ProcessorHelper.setTimeStamp(END_TIME, index, eventInfo, upsertStatement, utils);

    upsertStatement.setLong(++index, ProcessorHelper.getLongValue(DURATION, eventInfo));
    upsertStatement.setString(++index, eventInfo.getStringData().get(STAGE_NAME));
    upsertStatement.setString(++index, eventInfo.getStringData().get(EXECUTION_ID));
    upsertStatement.setString(++index, eventInfo.getStringData().get(APPROVED_BY));
    upsertStatement.setString(++index, eventInfo.getStringData().get(APPROVAL_TYPE));

    ++index;
    ProcessorHelper.setTimeStamp(APPROVED_AT, index, eventInfo, upsertStatement, utils);

    upsertStatement.setString(++index, eventInfo.getStringData().get(APPROVAL_COMMENT));

    ++index;
    ProcessorHelper.setTimeStamp(APPROVAL_EXPIRY, index, eventInfo, upsertStatement, utils);

    upsertStatement.setBoolean(++index, ProcessorHelper.getBooleanValue(MANUAL_INTERVENTION, eventInfo));
    upsertStatement.execute();
  }
}
