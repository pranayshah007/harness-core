/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.timeseries.processor;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Slf4j
public class TimescaleDataCleanup {
  @Inject private TimeScaleDBService timeScaleDBService;
  private static final int MAX_RETRY_COUNT = 5;
  private static final String DELETE_TABLE_ACCOUNT_ID_UNDERLINE = "DELETE FROM table_name WHERE ACCOUNT_ID=?";
  private String deleteStatement = "DELETE FROM table_name WHERE ACCOUNTID=?";
  private String errorMessage = "Error while deleting the timescale data for account: {}";

  public void cleanupChurnedAccountData(String accountId) {
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY_COUNT) {
      try {
        Connection connection = timeScaleDBService.getDBConnection();
        List<PreparedStatement> queriesToExecute = prepareQueriesForDeletion(connection, accountId);
        queriesToExecute.forEach(statement -> {
          try {
            statement.execute();
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

  @VisibleForTesting
  List<PreparedStatement> prepareQueriesForDeletion(Connection connection, String accountId) throws SQLException {
    List<PreparedStatement> forDeletion = new ArrayList<>();
    Pair<List<String>, List<String>> toDelete = initialiseTables();

    for (String tableName : toDelete.getLeft()) {
      forDeletion.add(createPreparedStatement(connection, accountId, DELETE_TABLE_ACCOUNT_ID_UNDERLINE, tableName));
    }
    for (String tableName : toDelete.getRight()) {
      forDeletion.add(createPreparedStatement(connection, accountId, deleteStatement, tableName));
    }

    return forDeletion;
  }

  private PreparedStatement createPreparedStatement(
      Connection connection, String accountId, String templateStatement, String tableName) throws SQLException {
    PreparedStatement statement = connection.prepareStatement(templateStatement.replace("table_name", tableName));
    statement.setString(1, accountId);
    return statement;
  }

  @VisibleForTesting
  Pair<List<String>, List<String>> initialiseTables() {
    // ADD THE TIMESCALE TABLES HERE, ONCE ADDED HERE DATA WILL BE DELETED IF THE ACCOUNT IS CHURNED.
    //
    // WE HAVE TABLES WITH ACCOUNT_ID COLUMN AND OTHERS WITH ACCOUNTID, THEN WE NEED TWO ROUNDS OF DELETE OPERATIONS.
    //
    List<String> tablesAccount_Id = new ArrayList<>();
    tablesAccount_Id.add("deployment_step");
    tablesAccount_Id.add("execution_interrupt");

    List<String> tablesAccountId = new ArrayList<>();
    tablesAccountId.add("deployment");
    tablesAccountId.add("deployment_stage");
    tablesAccountId.add("deployment_parent");
    tablesAccountId.add("instance_stats");
    tablesAccountId.add("instance_stats_day");
    tablesAccountId.add("instance_stats_hour");
    tablesAccountId.add("ng_instance_stats");
    tablesAccountId.add("ng_instance_stats_day");
    tablesAccountId.add("ng_instance_stats_hour");

    return Pair.of(tablesAccount_Id, tablesAccountId);
  }
}
