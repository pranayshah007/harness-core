/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.timescaledb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimescalePersistence implements SQLPersistence {
  TimeScaleDBService timeScaleDBService;

  public TimescalePersistence(TimeScaleDBService timeScaleDBService) {
    this.timeScaleDBService = timeScaleDBService;
  }

  public void executePaginatedQuery(String sqlQuery, int pageSize, int maxRetries, PaginatedQueryCallback callback,
      ModifyPreparedStatement modifyPreparedStatement) {
    ResultSet resultSet = null;
    PreparedStatement cursorStmt = null;
    PreparedStatement fetchStmt = null;
    int retries = 0;

    while (retries < maxRetries) {
      try (Connection connection = timeScaleDBService.getDBConnection()) {
        connection.setAutoCommit(false);
        String requestId = UUID.randomUUID().toString();
        String cursorName = "cursor_" + requestId.replace("-", "_");
        String cursorDeclaration = "DECLARE " + cursorName + " CURSOR FOR " + sqlQuery;
        String fetchQuery = "FETCH " + pageSize + " FROM " + cursorName;

        cursorStmt = connection.prepareStatement(cursorDeclaration);
        modifyPreparedStatement.setPreparedStatementParameters(cursorStmt, connection);
        cursorStmt.execute();

        fetchStmt = connection.prepareStatement(fetchQuery);

        while (true) {
          resultSet = fetchStmt.executeQuery();
          int rowCount = 0;

          while (resultSet.next()) {
            callback.processRow(resultSet);
            // Process each row of data here

            rowCount++;
          }

          if (rowCount == 0) {
            break; // No more rows to fetch
          }
        }

        connection.commit();
        // Successful execution, exit the retry loop
        break;
      } catch (SQLException e) {
        log.error(String.format("Exception occurred while trying to fetch data for Query %s", cursorStmt), e);
        retries++;

        if (retries >= maxRetries) {
          log.error("Max retries reached, giving up.");
          break; // Max retries reached, exit the loop
        }
      } finally {
        DBUtils.close(resultSet);
        DBUtils.close(cursorStmt);
        DBUtils.close(fetchStmt);
      }
    }
  }
}
