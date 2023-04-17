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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public abstract class AbstractChangeDataHandler implements ChangeHandler {
  private static final int MAX_RETRY_COUNT = 5;
  @Inject private TimeScaleDBService timeScaleDBService;

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent, String tableName, String[] fields) {
    log.trace("In TimeScale Change Handler: {}, {}, {}", changeEvent, tableName, fields);
    Map<String, String> columnValueMapping = null;
    List<String> primaryKeys = null;
    try {
      primaryKeys = getPrimaryKeys();
      columnValueMapping = getColumnValueMapping(changeEvent, fields);
    } catch (Exception e) {
      log.info(String.format("Not able to parse this event %s", changeEvent));
    }

    if (!tableName.equals("pipeline_execution_summary_ci") && columnValueMapping != null) {
      columnValueMapping.remove("moduleinfo_is_private");
      columnValueMapping.remove("pr");
    }

    switch (changeEvent.getChangeType()) {
      case INSERT:
        if (columnValueMapping != null) {
          dbOperation(insertSQL(tableName, columnValueMapping));
        }
        break;
      case UPDATE:
        if (columnValueMapping != null) {
          dbOperation(updateSQL(
              tableName, columnValueMapping, Collections.singletonMap("id", changeEvent.getUuid()), primaryKeys));
        }
        break;
      case DELETE:
        if (shouldDelete()) {
          dbOperation(deleteSQL(tableName, Collections.singletonMap("id", changeEvent.getUuid())));
        } else {
          if (columnValueMapping != null) {
            dbOperation(updateDeletedFieldsSQL(tableName, getColumnValueMappingForDelete(), changeEvent.getUuid()));
          }
        }
        break;
      default:
        log.info("Change Event Type not Handled: {}", changeEvent.getChangeType());
    }
    return true;
  }

  public static String escapeSql(String str) {
    if (str == null) {
      return null;
    }
    return str.replace("'", "''");
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

  public abstract Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields);

  public Map<String, String> getColumnValueMappingForDelete() {
    return Collections.emptyMap();
  }

  public boolean shouldDelete() {
    return true;
  }

  public abstract List<String> getPrimaryKeys();

  // https://www.codeproject.com/articles/779373/generic-functions-to-generate-insert-update-delete Generic Function
  // Adapted from here
  public static String insertSQL(String tableName, Map<String, String> columnValueMappingForInsert) {
    StringBuilder insertSQLBuilder = new StringBuilder();

    /**
     * Removing column that holds NULL value or Blank value...
     */
    if (!columnValueMappingForInsert.isEmpty()) {
      Set<Map.Entry<String, String>> setOfEntries = columnValueMappingForInsert.entrySet();
      Iterator<Map.Entry<String, String>> iterator = setOfEntries.iterator();

      while (iterator.hasNext()) {
        Map.Entry<String, String> entry = iterator.next();
        String value = entry.getValue();
        if (value == null || value.equals("")) {
          iterator.remove();
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
        insertSQLBuilder.append(String.format("'%s',", escapeSql(entry.getValue())));
      }
    }

    insertSQLBuilder = new StringBuilder(insertSQLBuilder.subSequence(0, insertSQLBuilder.length() - 1));
    insertSQLBuilder.append(')');

    // Returning the generated INSERT SQL Query as a String...
    return insertSQLBuilder.toString();
  }

  public static String updateSQL(String tableName, Map<String, String> columnValueMappingForSet,
      Map<String, String> columnValueMappingForCondition, List<String> primaryKeys) {
    StringBuilder updateQueryBuilder = new StringBuilder(2048);

    StringBuilder primaryKeysBuilder = new StringBuilder("");
    for (String primaryKey : primaryKeys) {
      primaryKeysBuilder.append(primaryKey);
      primaryKeysBuilder.append(',');
    }
    primaryKeysBuilder = new StringBuilder(primaryKeysBuilder.subSequence(0, primaryKeysBuilder.length() - 1));

    /**
     * Adding insert statement
     */
    if (insertSQL(tableName, columnValueMappingForSet) != null) {
      updateQueryBuilder.append(insertSQL(tableName, columnValueMappingForSet));
    }
    // On conflict condition
    updateQueryBuilder.append(" ON CONFLICT (").append(primaryKeysBuilder.toString()).append(") Do ");

    if (!columnValueMappingForSet.isEmpty()) {
      Set<Map.Entry<String, String>> setOfEntries = columnValueMappingForSet.entrySet();
      Iterator<Map.Entry<String, String>> iterator = setOfEntries.iterator();
      while (iterator.hasNext()) {
        Map.Entry<String, String> entry = iterator.next();
        String value = entry.getValue();
        if (value == null || value.equals("")) {
          iterator.remove();
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
    updateQueryBuilder.append(String.format("UPDATE  SET "));

    if (!columnValueMappingForSet.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForSet.entrySet()) {
        updateQueryBuilder.append(
            String.format("%s=%s,", entry.getKey(), String.format("'%s'", escapeSql(entry.getValue()))));
      }
    }

    updateQueryBuilder = new StringBuilder(updateQueryBuilder.subSequence(0, updateQueryBuilder.length() - 1));

    // Returning the generated UPDATE SQL Query as a String...
    return updateQueryBuilder.toString();
  }

  public static String updateDeletedFieldsSQL(String tableName, Map<String, String> columnValueMapping, String id) {
    StringBuilder updateQueryBuilder = new StringBuilder(2048);

    /* Making the UPDATE Query */
    updateQueryBuilder.append(String.format("UPDATE %s SET ", tableName));

    if (!columnValueMapping.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMapping.entrySet()) {
        updateQueryBuilder.append(
            String.format("%s=%s,", entry.getKey(), String.format("'%s'", escapeSql(entry.getValue()))));
      }
    }

    updateQueryBuilder = new StringBuilder(updateQueryBuilder.subSequence(0, updateQueryBuilder.length() - 1));
    /* Making the UPDATE Query */
    updateQueryBuilder.append(String.format(" WHERE id = '%s'", id));

    // Returning the generated UPDATE SQL Query as a String...
    return updateQueryBuilder.toString();
  }

  public static String deleteSQL(String tableName, Map<String, String> columnValueMappingForCondition) {
    StringBuilder deleteSQLBuilder = new StringBuilder();

    /**
     * Removing column that holds NULL value or Blank value...
     */
    if (!columnValueMappingForCondition.isEmpty()) {
      Set<Map.Entry<String, String>> setOfEntries = columnValueMappingForCondition.entrySet();
      Iterator<Map.Entry<String, String>> iterator = setOfEntries.iterator();
      while (iterator.hasNext()) {
        Map.Entry<String, String> entry = iterator.next();
        String value = entry.getValue();
        if (value == null || value.equals("")) {
          iterator.remove();
        }
      }
    }

    /* Making the DELETE Query */
    deleteSQLBuilder.append(String.format("DELETE FROM %s WHERE ", tableName));

    if (!columnValueMappingForCondition.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForCondition.entrySet()) {
        deleteSQLBuilder.append(
            String.format("%s=%s AND ", entry.getKey(), String.format("'%s'", escapeSql(entry.getValue()))));
      }
    }

    deleteSQLBuilder = new StringBuilder(deleteSQLBuilder.subSequence(0, deleteSQLBuilder.length() - 5));

    // Returning the generated DELETE SQL Query as a String...
    return deleteSQLBuilder.toString();
  }
}
