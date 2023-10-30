/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SQLOperationHelper {
  public static boolean shouldRemoveColumn(Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof String && value.equals("")) {
      return true;
    }

    if (value instanceof List && ((List) value).size() == 0) {
      return true;
    }

    return false;
  }

  public static String insertSQL(String tableName, Map<String, Object> columnValueMappingForInsert) {
    StringBuilder insertSQLBuilder = new StringBuilder();

    if (!columnValueMappingForInsert.isEmpty()) {
      Set<Map.Entry<String, Object>> setOfEntries = columnValueMappingForInsert.entrySet();
      Iterator<Map.Entry<String, Object>> iterator = setOfEntries.iterator();

      while (iterator.hasNext()) {
        Map.Entry<String, Object> entry = iterator.next();
        Object value = entry.getValue();
        if (shouldRemoveColumn(value)) {
          iterator.remove();
        }
      }
    }

    // Creating the INSERT SQL query
    insertSQLBuilder.append(String.format("INSERT INTO %s (", tableName));

    if (!columnValueMappingForInsert.isEmpty()) {
      for (Map.Entry<String, Object> entry : columnValueMappingForInsert.entrySet()) {
        insertSQLBuilder.append(String.format("%s,", entry.getKey()));
      }
    }

    insertSQLBuilder = new StringBuilder(insertSQLBuilder.subSequence(0, insertSQLBuilder.length() - 1));
    insertSQLBuilder.append(") VALUES(");

    if (!columnValueMappingForInsert.isEmpty()) {
      for (Map.Entry<String, Object> entry : columnValueMappingForInsert.entrySet()) {
        if (entry.getValue() instanceof List) {
          insertSQLBuilder.append(String.format("'%s',", getArrayString((List) entry.getValue())));

        } else {
          insertSQLBuilder.append(String.format("'%s',", escapeSql(entry.getValue().toString())));
        }
      }
    }

    insertSQLBuilder = new StringBuilder(insertSQLBuilder.subSequence(0, insertSQLBuilder.length() - 1));
    insertSQLBuilder.append(')');

    log.debug(insertSQLBuilder.toString());
    // Returning the INSERT SQL query
    return insertSQLBuilder.toString();
  }

  public static String updateSQL(String tableName, Map<String, Object> columnValueMappingForSet,
      Map<String, Object> columnValueMappingForCondition, List<String> primaryKeys) {
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
      Set<Map.Entry<String, Object>> setOfEntries = columnValueMappingForSet.entrySet();
      Iterator<Map.Entry<String, Object>> iterator = setOfEntries.iterator();
      while (iterator.hasNext()) {
        Map.Entry<String, Object> entry = iterator.next();
        Object value = entry.getValue();
        if (shouldRemoveColumn(value)) {
          iterator.remove();
        }
      }
    }

    /**
     * Removing column that holds NULL value or Blank value...
     */
    if (!columnValueMappingForCondition.isEmpty()) {
      Set<Map.Entry<String, Object>> setOfEntries = columnValueMappingForCondition.entrySet();
      Iterator<Map.Entry<String, Object>> iterator = setOfEntries.iterator();
      while (iterator.hasNext()) {
        Map.Entry<String, Object> entry = iterator.next();
        Object value = entry.getValue();
        if (shouldRemoveColumn(value)) {
          iterator.remove();
        }
      }
    }

    /* Making the UPDATE Query */
    updateQueryBuilder.append(String.format("UPDATE  SET "));

    if (!columnValueMappingForSet.isEmpty()) {
      for (Map.Entry<String, Object> entry : columnValueMappingForSet.entrySet()) {
        if (entry.getValue() instanceof List) {
          updateQueryBuilder.append(
              String.format("%s=%s,", entry.getKey(), String.format("'%s'", getArrayString((List) entry.getValue()))));
        } else {
          updateQueryBuilder.append(
              String.format("%s=%s,", entry.getKey(), String.format("'%s'", escapeSql(entry.getValue().toString()))));
        }
      }
    }

    updateQueryBuilder = new StringBuilder(updateQueryBuilder.subSequence(0, updateQueryBuilder.length() - 1));

    log.debug(updateQueryBuilder.toString());
    // Returning the generated UPDATE SQL Query as a String...
    return updateQueryBuilder.toString();
  }

  public static String deleteSQL(String tableName, Map<String, Object> columnValueMappingForCondition) {
    StringBuilder deleteSQLBuilder = new StringBuilder();

    /**
     * Removing column that holds NULL value or Blank value...
     */
    if (!columnValueMappingForCondition.isEmpty()) {
      Set<Map.Entry<String, Object>> setOfEntries = columnValueMappingForCondition.entrySet();
      Iterator<Map.Entry<String, Object>> iterator = setOfEntries.iterator();
      while (iterator.hasNext()) {
        Map.Entry<String, Object> entry = iterator.next();
        Object value = entry.getValue();
        if (shouldRemoveColumn(value)) {
          iterator.remove();
        }
      }
    }

    /* Making the DELETE Query */
    deleteSQLBuilder.append(String.format("UPDATE %s SET IS_DELETED = true WHERE ", tableName));

    if (!columnValueMappingForCondition.isEmpty()) {
      for (Map.Entry<String, Object> entry : columnValueMappingForCondition.entrySet()) {
        deleteSQLBuilder.append(
            String.format("%s=%s AND ", entry.getKey(), String.format("'%s'", escapeSql(entry.getValue().toString()))));
      }
    }

    deleteSQLBuilder = new StringBuilder(deleteSQLBuilder.subSequence(0, deleteSQLBuilder.length() - 5));

    log.debug(deleteSQLBuilder.toString());
    // Returning the generated DELETE SQL Query as a String...
    return deleteSQLBuilder.toString();
  }

  private static String getArrayString(List<Object> array) {
    StringBuilder arrayValues = new StringBuilder("{");
    if (!array.isEmpty()) {
      for (Object item : array) {
        arrayValues.append(String.format("\"%s\",", escapeSql(item.toString())));
      }
      arrayValues = new StringBuilder(arrayValues.subSequence(0, arrayValues.length() - 1));
    }
    arrayValues.append('}');
    return arrayValues.toString();
  }

  private static String escapeSql(String str) {
    if (str == null) {
      return null;
    }
    return str.replace("'", "''");
  }
}
