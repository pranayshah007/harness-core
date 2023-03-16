/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;

import io.harness.timescaledb.TimeScaleDBService;

import software.wings.common.VerificationConstants;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ServiceGuardSetupEventProcessor {
  private static final int MAX_RETRY_COUNT = 5;

  /**
   ACCOUNT_ID TEXT NOT NULL,
   VERIFICATION_PROVIDER_TYPE TEXT NOT NULL,
   NUM_OF_CONFIGS INTEGER NOT NULL,
   NUM_OF_ALERTS INTEGER NOT NULL,
   CREATION_TIME TIMESTAMP NOT NULL,
   LICENSE_TYPE VARCHAR(20),
   ACCOUNT_NAME TEXT,
   ENVIRONMENT_TYPE VARCHAR(20),
   ENABLED BOOLEAN,
   LAST_EXECUTION_TIME TIMESTAMP,
   HAS_DATA BOOLEAN
   */
  private String insertSQLStatement = "INSERT INTO CV_CONFIGURATIONS (ACCOUNT_ID, VERIFICATION_PROVIDER_TYPE, "
      + "NUM_OF_CONFIGS, NUM_OF_ALERTS, CREATION_TIME, LICENSE_TYPE, ACCOUNT_NAME, ENVIRONMENT_TYPE, ENABLED, LAST_EXECUTION_TIME, HAS_DATA) VALUES (?,?,?,?,?,?,?,?,?,?,?)";

  @Inject private TimeScaleDBService timeScaleDBService;

  public void processEvent(Map<String, String> properties) {
    if (timeScaleDBService.isValid()) {
      String accountId = properties.get("accountId");
      String verificationProviderType = properties.get("verificationProviderType");
      String numOfConfigsString = properties.get("configs");
      String numOfAlertsString = properties.get("alerts");
      String licenseType = properties.get("licenseType");
      String accountName = properties.get("accountName");
      String environmentType = properties.get("environmentType");
      String enabledString = properties.get("enabled");
      String lastExecutionTimeString = properties.get("lastExecutionTime");

      Preconditions.checkNotNull(accountId);
      Preconditions.checkNotNull(verificationProviderType);
      Preconditions.checkNotNull(numOfConfigsString);
      Preconditions.checkNotNull(numOfAlertsString);
      Preconditions.checkNotNull(accountName);
      Preconditions.checkNotNull(environmentType);
      Preconditions.checkNotNull(enabledString);

      boolean enabled = Boolean.parseBoolean(enabledString);
      Boolean hasData = null;

      if (lastExecutionTimeString != null) {
        String hasDataString = properties.get("hasData");
        Preconditions.checkNotNull(hasDataString);
        hasData = Boolean.valueOf(hasDataString);
      }

      int retryCount = 0;
      long startTime = System.currentTimeMillis();
      boolean successfulInsert = false;
      while (!successfulInsert && retryCount < MAX_RETRY_COUNT) {
        try (Connection connection = timeScaleDBService.getDBConnection();
             PreparedStatement insertPreparedStatement = connection.prepareStatement(insertSQLStatement)) {
          insertPreparedStatement.setString(1, accountId);
          insertPreparedStatement.setString(2, verificationProviderType);
          insertPreparedStatement.setInt(3, Integer.parseInt(numOfConfigsString));
          insertPreparedStatement.setInt(4, Integer.parseInt(numOfAlertsString));
          insertPreparedStatement.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
          if (licenseType != null) {
            insertPreparedStatement.setString(6, licenseType);
          } else {
            insertPreparedStatement.setNull(6, VerificationConstants.TIMESCALEDB_STRING_DATATYPE);
          }
          insertPreparedStatement.setString(7, accountName);
          insertPreparedStatement.setString(8, environmentType);
          insertPreparedStatement.setBoolean(9, enabled);
          insertPreparedStatement.setTimestamp(
              10, lastExecutionTimeString != null ? new Timestamp(Long.parseLong(lastExecutionTimeString)) : null);
          if (hasData == null) {
            insertPreparedStatement.setNull(11, VerificationConstants.TIMESCALEDB_BOOLEAN_DATATYPE);
          } else {
            insertPreparedStatement.setBoolean(11, hasData);
          }

          insertPreparedStatement.execute();
          successfulInsert = true;
        } catch (SQLException e) {
          retryCount++;
          if (retryCount >= MAX_RETRY_COUNT) {
            log.error("Failed to save deployment data,[{}]", properties, e);
          } else {
            log.info("Failed to save deployment data,[{}],retryCount=[{}]", properties, retryCount);
          }
        } finally {
          log.info("Total time=[{}],retryCount=[{}]", System.currentTimeMillis() - startTime, retryCount);
        }
      }
    } else {
      log.trace("Not processing data:[{}]", properties);
    }
  }
}
