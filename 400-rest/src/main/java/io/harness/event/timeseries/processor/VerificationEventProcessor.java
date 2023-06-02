/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.common.VerificationConstants;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.analysis.ContinuousVerificationService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class VerificationEventProcessor {
  @Inject private ContinuousVerificationService continuousVerificationService;

  private static final int MAX_RETRY_COUNT = 5;

  /**
   ACCOUNT_ID TEXT NOT NULL,
   APP_ID TEXT NOT NULL,
   SERVICE_ID TEXT NOT NULL,
   WORKFLOW_ID TEXT,
   WORKFLOW_EXECUTION_ID TEXT,
   STATE_EXECUTION_ID TEXT,
   CV_CONFIG_ID TEXT,
   STATE_TYPE TEXT NOT NULL,
   START_TIME TIMESTAMP,
   END_TIME TIMESTAMP NOT NULL,
   STATUS VARCHAR(20),
   IS_247 BOOL,
   HAS_DATA BOOL,
   IS_ROLLED_BACK,
   ACCOUNT_NAME TEXT NOT NULL,
   LICENSE_TYPE VARCHAR(20) NOT NULL
   */
  String insert_prepared_statement_sql =
      "INSERT INTO VERIFICATION_WORKFLOW_STATS (ACCOUNT_ID, APP_ID, SERVICE_ID, WORKFLOW_ID, WORKFLOW_EXECUTION_ID,"
      + "STATE_EXECUTION_ID, CV_CONFIG_ID, STATE_TYPE, START_TIME, END_TIME, STATUS, IS_247, HAS_DATA, IS_ROLLED_BACK,"
      + "ENVIRONMENT_TYPE, WORKFLOW_STATUS, ROLLBACK_TYPE, VERIFICATION_PROVIDER_TYPE, ACCOUNT_NAME, LICENSE_TYPE) "
      + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  @Inject private TimeScaleDBService timeScaleDBService;

  public void processEvent(Map<String, String> properties) {
    if (timeScaleDBService.isValid()) {
      Preconditions.checkNotNull(properties.get("accountId"));
      Preconditions.checkNotNull(properties.get("workflowExecutionId"));
      Preconditions.checkNotNull(properties.get("rollback"));
      Preconditions.checkNotNull(properties.get("accountName"));

      PageRequest<ContinuousVerificationExecutionMetaData> cvPageRequest =
          aPageRequest()
              .addFilter("accountId", SearchFilter.Operator.IN, properties.get("accountId"))
              .addFilter("workflowExecutionId", SearchFilter.Operator.EQ, properties.get("workflowExecutionId"))
              .build();
      List<ContinuousVerificationExecutionMetaData> cvExecutionMetaDataList =
          continuousVerificationService.getCVDeploymentData(cvPageRequest);

      if (!isEmpty(cvExecutionMetaDataList)) {
        boolean rolledback = Boolean.valueOf(properties.get("rollback"));
        String workflowStatus = properties.get("workflowStatus");
        String rollbackType = properties.get("rollbackType");
        String envType = properties.get("envType");
        String accountName = properties.get("accountName");
        String licenseType = properties.get("licenseType");
        boolean successfulInsert = false;
        int retryCount = 0;
        long startTime = System.currentTimeMillis();
        while (!successfulInsert && retryCount < MAX_RETRY_COUNT) {
          try (Connection connection = timeScaleDBService.getDBConnection();
               PreparedStatement insertPreparedStatement = connection.prepareStatement(insert_prepared_statement_sql)) {
            for (ContinuousVerificationExecutionMetaData cvExecutionMetaData : cvExecutionMetaDataList) {
              insertPreparedStatement.setString(1, cvExecutionMetaData.getAccountId());
              insertPreparedStatement.setString(2, cvExecutionMetaData.getAppId());
              insertPreparedStatement.setString(3, cvExecutionMetaData.getServiceId());
              insertPreparedStatement.setString(4, cvExecutionMetaData.getWorkflowId());
              insertPreparedStatement.setString(5, cvExecutionMetaData.getWorkflowExecutionId());
              insertPreparedStatement.setString(6, cvExecutionMetaData.getStateExecutionId());
              insertPreparedStatement.setString(7, "");
              insertPreparedStatement.setString(8, cvExecutionMetaData.getStateType().getName());
              insertPreparedStatement.setString(11, cvExecutionMetaData.getExecutionStatus().name());
              insertPreparedStatement.setBoolean(12, false);
              insertPreparedStatement.setBoolean(13, !cvExecutionMetaData.isNoData());
              insertPreparedStatement.setBoolean(
                  14, rolledback && cvExecutionMetaData.getExecutionStatus() == ExecutionStatus.FAILED);
              insertPreparedStatement.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
              insertPreparedStatement.setTimestamp(10, new Timestamp(System.currentTimeMillis()));
              insertPreparedStatement.setString(15, envType);
              insertPreparedStatement.setString(16, workflowStatus);
              insertPreparedStatement.setString(17, rollbackType);
              insertPreparedStatement.setString(18,
                  VerificationConstants.getLogAnalysisStates().contains(cvExecutionMetaData.getStateType())
                      ? "LOGS"
                      : "METRICS");
              insertPreparedStatement.setString(19, accountName);
              if (licenseType != null) {
                insertPreparedStatement.setString(20, licenseType);
              } else {
                insertPreparedStatement.setNull(20, VerificationConstants.TIMESCALEDB_STRING_DATATYPE);
              }
              insertPreparedStatement.addBatch();
            }
            insertPreparedStatement.executeBatch();
            successfulInsert = true;
          } catch (SQLException e) {
            if (retryCount >= MAX_RETRY_COUNT) {
              log.error("Failed to save deployment data,[{}]", properties, e);
            } else {
              log.info("Failed to save deployment data,[{}],retryCount=[{}]", properties, retryCount);
            }
            retryCount++;
          } finally {
            log.info("Total time=[{}],retryCount=[{}]", System.currentTimeMillis() - startTime, retryCount);
          }
        }
      }
    } else {
      log.trace("Not processing data:[{}]", properties);
    }
  }
}
