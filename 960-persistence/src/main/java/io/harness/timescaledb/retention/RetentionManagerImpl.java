/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.timescaledb.retention;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class RetentionManagerImpl implements RetentionManager {
  private static final String ADD_RETENTION_POLICY_QUERY_PATTERN = "SELECT add_retention_policy('%s', INTERVAL '%s');";
  private static final String REMOVE_RETENTION_POLICY_QUERY_PATTERN = "SELECT remove_retention_policy(?);";
  private static final String FETCH_SCHEDULED_JOB_CONFIG = "SELECT config FROM timescaledb_information.jobs j "
      + "WHERE j.proc_name = 'policy_retention' AND j.hypertable_name = ?;";
  private static final String RETENTION_PERIOD_PATTERN = "\\d+ (day|month|year)s?";
  @Inject TimeScaleDBService timeScaleDBService;

  public void addPolicy(String table, String retentionPeriod) {
    if (!retentionPeriod.matches(RETENTION_PERIOD_PATTERN)) {
      log.warn("Unsupported retention period {} for table {}", retentionPeriod, table);
      return;
    }
    Optional<String> currentRetentionPeriodOpt = getCurrentRetentionPeriod(table);
    if (currentRetentionPeriodOpt.isPresent()) {
      if (currentRetentionPeriodOpt.get().equals(retentionPeriod)) {
        log.info("Retention policy {} already present for table {}", retentionPeriod, table);
        return;
      } else {
        removeRetentionPolicy(table);
      }
    }
    addPolicyInternal(table, retentionPeriod);
  }

  private void addPolicyInternal(String table, String retentionPeriod) {
    log.info("Adding retention policy {} for table {}", retentionPeriod, table);
    String query = String.format(ADD_RETENTION_POLICY_QUERY_PATTERN, table, retentionPeriod);
    try (Connection dbConnection = timeScaleDBService.getDBConnection();
         Statement statement = dbConnection.createStatement()) {
      statement.executeQuery(query);
    } catch (SQLException e) {
      log.error("Error while adding retention policy {} for timescale table {}", retentionPeriod, table, e);
    }
  }

  private void removeRetentionPolicy(String table) {
    log.info("Removing old retention policy for table {}", table);
    try (Connection dbConnection = timeScaleDBService.getDBConnection();
         PreparedStatement statement = dbConnection.prepareStatement(REMOVE_RETENTION_POLICY_QUERY_PATTERN)) {
      statement.setString(1, table);
      statement.executeQuery();
    } catch (SQLException e) {
      log.error("Error while removing old retention policy for table {}", table, e);
    }
  }

  private Optional<String> getCurrentRetentionPeriod(String table) {
    log.info("Fetching retention policy for table {}", table);
    try (Connection dbConnection = timeScaleDBService.getDBConnection();
         PreparedStatement statement = dbConnection.prepareStatement(FETCH_SCHEDULED_JOB_CONFIG)) {
      statement.setString(1, table);
      ResultSet resultSet = statement.executeQuery();
      if (resultSet != null && resultSet.next()) {
        String configString = resultSet.getString(1);
        if (StringUtils.isNotBlank(configString)) {
          JSONObject config = new JSONObject(configString);
          String retentionPeriod = String.valueOf(config.get("drop_after"));
          log.info("Table {}, Current Retention Period {}", table, retentionPeriod);
          return Optional.of(retentionPeriod.replace("mon", "month"));
        }
      }
    } catch (SQLException e) {
      log.error("Error while fetching retention policy for table {}", table, e);
    }
    return Optional.empty();
  }
}
