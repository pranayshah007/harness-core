/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import io.harness.ChangeHandler;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DBObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Strings;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CE)
public class TimeScaleDBChangeHandler implements ChangeHandler {
  private static final int MAX_RETRY_COUNT = 5;
  @Inject private TimeScaleDBService timeScaleDBService;

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent, String tableName, String[] fields) {
    log.trace("In TimeScale Change Handler: {}, {}, {}", changeEvent, tableName, fields);
    switch (changeEvent.getChangeType()) {
      case INSERT:
        dbOperation(insertSQL(tableName, getColumnValueMapping(changeEvent, fields)));
        break;
      case UPDATE:
        dbOperation(updateSQL(tableName, getColumnValueMapping(changeEvent, fields),
            Collections.singletonMap("UUID", changeEvent.getUuid())));
        break;
      case DELETE:
        dbOperation(deleteSQL(tableName, Collections.singletonMap("UUID", changeEvent.getUuid())));
        break;
      default:
        log.info("Change Event Type not Handled: {}", changeEvent.getChangeType());
    }
    return true;
  }

  public boolean dbOperation(String query) {
    boolean successfulOperation = false;
    log.trace("In dbOperation, Query: {}", query);
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulOperation && retryCount < MAX_RETRY_COUNT) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(query)) {
          statement.execute();
          successfulOperation = true;
        } catch (SQLException e) {
          log.error("Failed to save/update/delete data Query = {},retryCount=[{}], Exception: ", query, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.warn("TimeScale Down");
    }
    return successfulOperation;
  }

  private Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();
    columnValueMapping.put(Strings.toUpperCase("uuid"), changeEvent.getUuid());
    for (String field : fields) {
      columnValueMapping.put(Strings.toUpperCase(field), dbObject.get(field).toString());
    }
    return columnValueMapping;
  }

  // https://www.codeproject.com/articles/779373/generic-functions-to-generate-insert-update-delete Generic Function
  // Adapted from here
  public static String insertSQL(String tableName, Map<String, String> columnValueMappingForInsert) {
    StringBuilder insertSQLBuilder = new StringBuilder();

    /**
     * Removing column that holds NULL value or Blank value...
     */
    if (!columnValueMappingForInsert.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForInsert.entrySet()) {
        if (entry.getValue() == null || entry.getValue().equals("")) {
          columnValueMappingForInsert.remove(entry.getKey());
        }
      }
    }

    /* Making the INSERT Query... */
    insertSQLBuilder.append(String.format("INSERT INTO %s (", tableName));

    if (!columnValueMappingForInsert.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForInsert.entrySet()) {
        insertSQLBuilder.append(String.format("%s,", entry.getKey()));
      }
    }

    insertSQLBuilder = new StringBuilder(insertSQLBuilder.subSequence(0, insertSQLBuilder.length() - 1));
    insertSQLBuilder.append(") VALUES(");

    if (!columnValueMappingForInsert.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForInsert.entrySet()) {
        insertSQLBuilder.append(String.format("'%s',", entry.getValue()));
      }
    }

    insertSQLBuilder = new StringBuilder(insertSQLBuilder.subSequence(0, insertSQLBuilder.length() - 1));
    insertSQLBuilder.append(')');

    // Returning the generated INSERT SQL Query as a String...
    return insertSQLBuilder.toString();
  }

  public static String updateSQL(String tableName, Map<String, String> columnValueMappingForSet,
      Map<String, String> columnValueMappingForCondition) {
    StringBuilder updateQueryBuilder = new StringBuilder();

    /**
     * Removing column that holds NULL value or Blank value...
     */
    if (!columnValueMappingForSet.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForSet.entrySet()) {
        if (entry.getValue() == null || entry.getValue().equals("")) {
          columnValueMappingForSet.remove(entry.getKey());
        }
      }
    }

    /**
     * Removing column that holds NULL value or Blank value...
     */
    if (!columnValueMappingForCondition.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForCondition.entrySet()) {
        if (entry.getValue() == null || entry.getValue().equals("")) {
          columnValueMappingForCondition.remove(entry.getKey());
        }
      }
    }

    /* Making the UPDATE Query */
    updateQueryBuilder.append(String.format("UPDATE %s SET ", tableName));

    if (!columnValueMappingForSet.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForSet.entrySet()) {
        updateQueryBuilder.append(String.format("%s=%s,", entry.getKey(), String.format("'%s'", entry.getValue())));
      }
    }

    updateQueryBuilder = new StringBuilder(updateQueryBuilder.subSequence(0, updateQueryBuilder.length() - 1));
    updateQueryBuilder.append(" WHERE ");

    if (!columnValueMappingForCondition.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForCondition.entrySet()) {
        updateQueryBuilder.append(String.format("%s=%s,", entry.getKey(), String.format("'%s'", entry.getValue())));
      }
    }

    updateQueryBuilder = new StringBuilder(updateQueryBuilder.subSequence(0, updateQueryBuilder.length() - 1));

    // Returning the generated UPDATE SQL Query as a String...
    return updateQueryBuilder.toString();
  }

  public static String deleteSQL(String tableName, Map<String, String> columnValueMappingForCondition) {
    StringBuilder deleteSQLBuilder = new StringBuilder();

    /**
     * Removing column that holds NULL value or Blank value...
     */
    if (!columnValueMappingForCondition.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForCondition.entrySet()) {
        if (entry.getValue() == null || entry.getValue().equals("")) {
          columnValueMappingForCondition.remove(entry.getKey());
        }
      }
    }

    /* Making the DELETE Query */
    deleteSQLBuilder.append(String.format("DELETE FROM %s WHERE ", tableName));

    if (!columnValueMappingForCondition.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForCondition.entrySet()) {
        deleteSQLBuilder.append(String.format("%s=%s AND ", entry.getKey(), String.format("'%s'", entry.getValue())));
      }
    }

    deleteSQLBuilder = new StringBuilder(deleteSQLBuilder.subSequence(0, deleteSQLBuilder.length() - 5));

    // Returning the generated DELETE SQL Query as a String...
    return deleteSQLBuilder.toString();
  }
}
