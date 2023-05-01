/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static io.harness.changehandlers.AbstractChangeDataHandler.escapeSql;

import io.harness.ChangeHandler;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ApprovalStageExecutionDataHandler implements ChangeHandler {
  @Inject private TimeScaleDBService timeScaleDBService;
  private static final int MAX_RETRY_COUNT = 5;

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent, String tableName, String[] fields) {
    log.trace("In TimeScale Change Handler: {}, {}, {}", changeEvent, tableName, fields);
    List<Map<String, String>> columnValueMapping;
    try {
      columnValueMapping = getColumnValueMapping(changeEvent);
      switch (changeEvent.getChangeType()) {
        case INSERT:
          if (columnValueMapping != null && columnValueMapping.size() > 0) {
            columnValueMapping.forEach(column -> { dbOperation(insertSQL(tableName, column)); });
          }
          break;
        case UPDATE:
          if (columnValueMapping != null && columnValueMapping.size() > 0) {
            columnValueMapping.forEach(column -> {
              dbOperation(updateSQL(tableName, column, Collections.singletonMap("id", changeEvent.getUuid())));
            });
          }
          break;
        default:
          log.info("Change Event Type not Handled: {}", changeEvent.getChangeType());
      }
    } catch (Exception e) {
      log.info(String.format("Not able to parse this event %s", changeEvent), e);
    }
    return true;
  }

  public List<Map<String, String>> getColumnValueMapping(ChangeEvent<?> changeEvent) {
    if (changeEvent == null) {
      return null;
    }
    List<Map<String, String>> nodeMap = new ArrayList<>();
    DBObject dbObject = changeEvent.getFullDocument();
    String accountId = null;
    String orgIdentifier = null;
    String projectIdentifier = null;
    String planExecutionId = null;
    String pipelineIdentifier = null;

    if (dbObject == null) {
      return nodeMap;
    }

    if (dbObject.get(PlanExecutionSummaryCDConstants.ACCOUNT_ID_KEY) != null) {
      accountId = dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.accountId).toString();
    }
    if (dbObject.get(PlanExecutionSummaryCDConstants.ORG_IDENTIFIER_KEY) != null) {
      orgIdentifier = dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.orgIdentifier).toString();
    }
    if (dbObject.get(PlanExecutionSummaryCDConstants.PROJECT_IDENTIFIER_KEY) != null) {
      projectIdentifier =
          dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.projectIdentifier).toString();
    }
    if (dbObject.get(PlanExecutionSummaryCDConstants.PLAN_EXECUTION_ID) != null) {
      planExecutionId =
          dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.planExecutionId).toString();
    }
    if (dbObject.get(PlanExecutionSummaryCDConstants.PIPELINE_ID) != null) {
      pipelineIdentifier =
          dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.pipelineIdentifier).toString();
    }

    // if moduleInfo is null, no need to push data in this table
    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.moduleInfo) == null) {
      return null;
    }

    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap) == null) {
      return null;
    }

    if (((BasicDBObject) dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.moduleInfo)).get("pms")
        == null) {
      return null;
    }

    Set<Map.Entry<String, Object>> layoutNodeMap =
        ((BasicDBObject) dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap))
            .entrySet();

    for (Map.Entry<String, Object> stageExecutionNode : layoutNodeMap) {
      BasicDBObject approvalObject = (BasicDBObject) stageExecutionNode.getValue();
      if (!approvalObject.get("nodeType").equals("Approval")) {
        continue;
      }
      Map<String, String> columnValueMapping = new HashMap<>();
      if (approvalObject.get("nodeExecutionId") == null) {
        continue;
      }
      columnValueMapping.put("id", approvalObject.get("nodeExecutionId").toString());
      if (approvalObject.get("status") != null) {
        columnValueMapping.put("status", approvalObject.get("status").toString());
      }
      if (approvalObject.get("nodeType") != null) {
        columnValueMapping.put("type", approvalObject.get("nodeType").toString());
      }
      if (approvalObject.get("name") != null) {
        columnValueMapping.put("name", approvalObject.get("name").toString());
      }
      if (approvalObject.get("startTs") != null) {
        columnValueMapping.put("start_time", approvalObject.get("startTs").toString());
      }
      if (approvalObject.get("endTs") != null) {
        columnValueMapping.put("end_time", approvalObject.get("endTs").toString());
      }
      if (approvalObject.get("startTs") != null && approvalObject.get("endTs") != null) {
        Long duration = Long.valueOf(approvalObject.get("endTs").toString())
            - Long.valueOf(approvalObject.get("startTs").toString());
        columnValueMapping.put("duration", duration.toString());
      }
      columnValueMapping.put("stage_execution_id", approvalObject.get("nodeExecutionId").toString());
      columnValueMapping.put("project_identifier", projectIdentifier);
      columnValueMapping.put("org_identifier", orgIdentifier);
      columnValueMapping.put("account_identifier", accountId);
      columnValueMapping.put("pipeline_identifier", pipelineIdentifier);
      columnValueMapping.put("plan_execution_id", planExecutionId);
      if (EmptyPredicate.isNotEmpty(columnValueMapping)) {
        nodeMap.add(columnValueMapping);
      }
    }

    return nodeMap;
  }

  private boolean dbOperation(String query) {
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

  private static String insertSQL(String tableName, Map<String, String> columnValueMappingForInsert) {
    StringBuilder insertSQLBuilder = new StringBuilder();

    // Removing column that holds NULL value or Blank value...
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

  private static String updateSQL(String tableName, Map<String, String> columnValueMappingForSet,
      Map<String, String> columnValueMappingForCondition) {
    StringBuilder updateQueryBuilder = new StringBuilder(2048);

    /**
     * Adding insert statement
     */
    if (insertSQL(tableName, columnValueMappingForSet) != null) {
      updateQueryBuilder.append(insertSQL(tableName, columnValueMappingForSet));
    }

    // On conflict condition and Making the UPDATE Query
    updateQueryBuilder.append(" ON CONFLICT (id) Do UPDATE  SET ");

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
}
