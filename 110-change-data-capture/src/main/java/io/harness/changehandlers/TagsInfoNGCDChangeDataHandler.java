/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.execution.stage.StageExecutionEntity;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Slf4j
public class TagsInfoNGCDChangeDataHandler extends AbstractChangeDataHandler {
  private static final String TAGS = "tags";
  private static final String PARENT_TYPE = "parent_type";
  private static final String ID = "id";

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent, String tableName, String[] fields) {
    log.trace("In TimeScale Change Handler: {}, {}, {}", changeEvent, tableName, fields);
    Map<String, String> columnValueMapping = null;
    Map<String, List<String>> arrayColumnValueMapping = null;
    List<String> primaryKeys = null;

    if (changeEvent == null) {
      return true;
    }

    try {
      primaryKeys = getPrimaryKeys();
      columnValueMapping = getColumnValueMapping(changeEvent, fields);
      arrayColumnValueMapping = getArrayColumnValueMapping(changeEvent, fields);
    } catch (Exception e) {
      log.info(String.format("Not able to parse this event %s", changeEvent));
      return false;
    }

    if (!tableName.equals("pipeline_execution_summary_ci") && columnValueMapping != null) {
      columnValueMapping.remove("moduleinfo_is_private");
      columnValueMapping.remove("pr");
    }

    Map<String, String> keyMap = new HashMap<>();
    keyMap.put(PARENT_TYPE, TagsInfoCDChangeDataHandlerHelper.getParentType(changeEvent));
    keyMap.put(ID, changeEvent.getUuid());

    switch (changeEvent.getChangeType()) {
      case INSERT:
      case UPDATE:
        if (isNotEmpty(columnValueMapping)) {
          dbOperation(updateSQL(tableName, columnValueMapping, arrayColumnValueMapping, keyMap, primaryKeys));
        }
        break;
      case DELETE:
        dbOperation(deleteSQL(tableName, keyMap));
        break;
      default:
        log.info("Change Event Type not Handled: {}", changeEvent.getChangeType());
    }
    return true;
  }

  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    if (dbObject == null) {
      return null;
    }

    String id = changeEvent.getUuid();
    String accountId = TagsInfoCDChangeDataHandlerHelper.getAccountIdentifier(changeEvent, dbObject);
    String orgId = TagsInfoCDChangeDataHandlerHelper.getOrgIdentifier(changeEvent, dbObject);
    String projectId = TagsInfoCDChangeDataHandlerHelper.getProjectIdentifier(changeEvent, dbObject);
    String parentIdentifier = TagsInfoCDChangeDataHandlerHelper.getParentIdentifier(changeEvent, dbObject);
    String parentType = TagsInfoCDChangeDataHandlerHelper.getParentType(changeEvent);
    if (id == null || accountId == null || parentType == null || parentIdentifier == null) {
      return null;
    }
    BasicDBList tags = TagsInfoCDChangeDataHandlerHelper.getTags(changeEvent, dbObject);
    if (isEmpty(tags)) {
      return null;
    }
    columnValueMapping.put(ID, id);
    columnValueMapping.put("account_id", accountId);
    columnValueMapping.put("org_id", orgId);
    columnValueMapping.put("project_id", projectId);
    columnValueMapping.put(PARENT_TYPE, parentType);
    columnValueMapping.put("parent_id", parentIdentifier);

    return columnValueMapping;
  }

  public Map<String, List<String>> getArrayColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    Map<String, List<String>> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    if (dbObject == null) {
      return null;
    }

    List<String> tagList;
    BasicDBList tags = TagsInfoCDChangeDataHandlerHelper.getTags(changeEvent, dbObject);
    if (isEmpty(tags)) {
      return null;
    }
    if (changeEvent.getEntityType().equals(StageExecutionInfo.class)
        || changeEvent.getEntityType().equals(StageExecutionEntity.class)) {
      tagList = Arrays.asList(tags.toArray(new String[0]));
    } else {
      BasicDBObject[] tagArray = tags.toArray(new BasicDBObject[0]);
      tagList = TagsInfoCDChangeDataHandlerHelper.getTagsList(tagArray);
    }
    columnValueMapping.put(TAGS, tagList);
    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList(ID, PARENT_TYPE);
  }

  public static String updateSQL(String tableName, Map<String, String> columnValueMappingForSet,
      Map<String, List<String>> arrayColumnValueMappingForSet, Map<String, String> columnValueMappingForCondition,
      List<String> primaryKeys) {
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
    if (insertSQL(tableName, columnValueMappingForSet, arrayColumnValueMappingForSet) != null) {
      updateQueryBuilder.append(insertSQL(tableName, columnValueMappingForSet, arrayColumnValueMappingForSet));
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
    if (!isEmpty(arrayColumnValueMappingForSet)) {
      for (Map.Entry<String, List<String>> entry : arrayColumnValueMappingForSet.entrySet()) {
        StringBuilder arrayValueSql = new StringBuilder();
        for (String value : entry.getValue()) {
          arrayValueSql.append(String.format("'%s',", escapeSql(value)));
        }
        arrayValueSql = new StringBuilder(arrayValueSql.subSequence(0, arrayValueSql.length() - 1));
        updateQueryBuilder.append(String.format("%s=ARRAY[%s],", entry.getKey(), arrayValueSql));
      }
    }

    updateQueryBuilder = new StringBuilder(updateQueryBuilder.subSequence(0, updateQueryBuilder.length() - 1));

    // Returning the generated UPDATE SQL Query as a String...
    return updateQueryBuilder.toString();
  }

  public static String insertSQL(String tableName, Map<String, String> columnValueMappingForInsert,
      Map<String, List<String>> arrayColumnValueMappingForInsert) {
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

    if (!isEmpty(arrayColumnValueMappingForInsert)) {
      Set<Map.Entry<String, List<String>>> setOfEntries = arrayColumnValueMappingForInsert.entrySet();
      setOfEntries.removeIf(entry -> isEmpty(entry.getValue()));
    }

    /* Making the INSERT Query... */
    insertSQLBuilder.append(String.format("INSERT INTO %s (", tableName));
    StringBuilder columnNameSql = new StringBuilder();
    StringBuilder columnValueSql = new StringBuilder();
    if (!columnValueMappingForInsert.isEmpty()) {
      for (Map.Entry<String, String> entry : columnValueMappingForInsert.entrySet()) {
        columnNameSql.append(String.format("%s,", entry.getKey()));
        columnValueSql.append(String.format("'%s',", escapeSql(entry.getValue())));
      }
    }
    if (!isEmpty(arrayColumnValueMappingForInsert)) {
      for (Map.Entry<String, List<String>> entry : arrayColumnValueMappingForInsert.entrySet()) {
        StringBuilder arrayValueSql = new StringBuilder();
        for (String value : entry.getValue()) {
          arrayValueSql.append(String.format("'%s',", escapeSql(value)));
        }
        arrayValueSql = new StringBuilder(arrayValueSql.subSequence(0, arrayValueSql.length() - 1));
        if (arrayValueSql.length() > 0) {
          columnNameSql.append(String.format("%s,", entry.getKey()));
          columnValueSql.append(String.format("ARRAY[%s],", arrayValueSql));
        }
      }
    }
    columnNameSql = new StringBuilder(columnNameSql.subSequence(0, columnNameSql.length() - 1));
    columnValueSql = new StringBuilder(columnValueSql.subSequence(0, columnValueSql.length() - 1));
    insertSQLBuilder.append(columnNameSql).append(") VALUES(").append(columnValueSql).append(')');

    // Returning the generated INSERT SQL Query as a String...
    return insertSQLBuilder.toString();
  }
}
