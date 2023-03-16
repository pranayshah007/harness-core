/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;

import io.harness.exception.WingsException;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimescaleDataCleanup {
  @Inject private TimeScaleDBService timeScaleDBService;
  private static final int MAX_RETRY_COUNT = 5;
  private String deleteStatement = "DELETE FROM table_name WHERE ACCOUNTID=?";
  private String errorMessage = "Error while deleting the timescale data for account: {}";

  public void cleanupChurnedAccountData(String accountId) {
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY_COUNT) {
      try {
        Connection connection = timeScaleDBService.getDBConnection();
        List<PreparedStatement> queriesToExecute = prepareQueriesForDeletion(connection, accountId);
        queriesToExecute.forEach(deleteStatement -> {
          try {
            deleteStatement.execute();
          } catch (SQLException e) {
            log.error(errorMessage, accountId, e);
          }
        });
        successful = true;
        log.info("TimeScale DB data is deleted successfully for account: {}", accountId);
      } catch (Exception e) {
        retryCount += 1;
        log.error(errorMessage, accountId, e);
      }
    }
  }

  private List<PreparedStatement> prepareQueriesForDeletion(Connection connection, String accountId)
      throws SQLException {
    List<String> tablesToBeDeleted = initialiseTables();
    return tablesToBeDeleted.stream()
        .map(tableName -> {
          try {
            PreparedStatement statement = connection.prepareStatement(deleteStatement.replace("table_name", tableName));
            statement.setString(1, accountId);
            return statement;
          } catch (SQLException e) {
            log.error(errorMessage, accountId, e);
            throw new WingsException(e);
          }
        })
        .collect(Collectors.toList());
  }

  private List<String> initialiseTables() {
    // Add the TimeScale Tables here, once added here data will be deleted if the account is churned.
    List<String> tablesToBeDeleted = new ArrayList<>();
    tablesToBeDeleted.add("deployment");
    tablesToBeDeleted.add("deployment_step");
    tablesToBeDeleted.add("deployment_stage");
    tablesToBeDeleted.add("deployment_parent");
    tablesToBeDeleted.add("execution_interrupt");
    tablesToBeDeleted.add("instance_stats");
    tablesToBeDeleted.add("instance_stats_day");
    tablesToBeDeleted.add("instance_stats_hour");
    tablesToBeDeleted.add("ng_instance_stats");
    tablesToBeDeleted.add("ng_instance_stats_day");
    tablesToBeDeleted.add("ng_instance_stats_hour");
    return tablesToBeDeleted;
  }
}
