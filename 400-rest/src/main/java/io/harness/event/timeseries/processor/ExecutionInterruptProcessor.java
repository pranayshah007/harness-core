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
public class ExecutionInterruptProcessor implements StepEventProcessor<TimeSeriesEventInfo> {
  @Inject private TimeScaleDBService timeScaleDBService;
  @Inject DataFetcherUtils utils;

  private static final String upsert_statement =
      "INSERT INTO EXECUTION_INTERRUPT (ID,ACCOUNT_ID,STATE_EXECUTION_INSTANCE_ID,TYPE,EXECUTION_ID,APP_ID,CREATED_BY,CREATED_AT,LAST_UPDATED_BY,LAST_UPDATED_AT) VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT (ID,CREATED_AT) DO UPDATE SET ACCOUNT_ID = EXCLUDED.ACCOUNT_ID,STATE_EXECUTION_INSTANCE_ID = EXCLUDED.STATE_EXECUTION_INSTANCE_ID,TYPE = EXCLUDED.TYPE,EXECUTION_ID = EXCLUDED.EXECUTION_ID,APP_ID = EXCLUDED.APP_ID,CREATED_BY = EXCLUDED.CREATED_BY,LAST_UPDATED_BY = EXCLUDED.LAST_UPDATED_BY,LAST_UPDATED_AT = EXCLUDED.LAST_UPDATED_AT";

  @Override
  public void processEvent(TimeSeriesEventInfo eventInfo) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not saving execution interrupt data to TimeScaleDB");
      return;
    }
    if (eventInfo.getAccountId() == null || eventInfo.getLongData() == null || eventInfo.getStringData() == null) {
      log.info("Invalid TimeSeriesEventInfo [{}] , not saving execution interrupt data to TimeScaleDB", eventInfo);
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
          log.error("Failed to save execution interrupt data,[{}],retryCount=[{}] ", eventInfo, retryCount++, e);
        } else {
          log.error("Failed to save execution interrupt data,[{}]", eventInfo, e);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save execution interrupt data,[{}]", eventInfo, e);
        retryCount = MAX_RETRY + 1;
      } finally {
        log.info("Total time=[{}]", System.currentTimeMillis() - startTime);
      }
    }
  }

  public void upsertDataToTimescaleDB(TimeSeriesEventInfo eventInfo, PreparedStatement upsertStatement)
      throws SQLException {
    int index = 0;
    upsertStatement.setString(++index, eventInfo.getStringData().get(EXECUTION_INTERRUPT_ID));
    upsertStatement.setString(++index, eventInfo.getAccountId());
    upsertStatement.setString(++index, eventInfo.getStringData().get(ID));
    upsertStatement.setString(++index, eventInfo.getStringData().get(EXECUTION_INTERRUPT_TYPE));
    upsertStatement.setString(++index, eventInfo.getStringData().get(EXECUTION_ID));
    upsertStatement.setString(++index, eventInfo.getStringData().get(APP_ID));
    upsertStatement.setString(++index, eventInfo.getStringData().get(EXECUTION_INTERRUPT_CREATED_BY));

    ++index;
    ProcessorHelper.setTimeStamp(EXECUTION_INTERRUPT_CREATED_AT, index, eventInfo, upsertStatement, utils);

    upsertStatement.setString(++index, eventInfo.getStringData().get(EXECUTION_INTERRUPT_UPDATED_BY));

    ++index;
    ProcessorHelper.setTimeStamp(EXECUTION_INTERRUPT_UPDATED_AT, index, eventInfo, upsertStatement, utils);

    upsertStatement.execute();
  }
}
