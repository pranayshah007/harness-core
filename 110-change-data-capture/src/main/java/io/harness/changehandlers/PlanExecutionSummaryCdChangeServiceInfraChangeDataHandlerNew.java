/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PlanExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew implements ChangeHandler {
  private static final int MAX_RETRY_COUNT = 5;
  @Inject private TimeScaleDBService timeScaleDBService;
  private static String SERVICE_STARTTS = "service_startts";
  private static String SERVICE_ENDTS = "service_endts";
  // These set of keys we can use to populate data to 'artifact_image' in service_infra_info
  private static List<String> artifactPathNameSet = Arrays.asList("imagePath", "artifactPath", "bucketName", "jobName");
  // These set of keys we can use to populate data to 'tag' in service_infra_info.
  // Passing artifactPath as both tag and artifact_image in case of ArtifactoryGenericArtifactSummary. Have put in end
  // to avoid conflict for other ArtifactSummary
  private static List<String> tagNameSet = Arrays.asList("tag", "version", "build", "artifactPath");

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent, String tableName, String[] fields) {
    log.trace("In TimeScale Change Handler: {}, {}, {}", changeEvent, tableName, fields);
    List<Map<String, String>> columnValueMapping = null;
    try {
      columnValueMapping = getColumnValueMapping(changeEvent, fields);
      switch (changeEvent.getChangeType()) {
        case INSERT:
          if (columnValueMapping != null && columnValueMapping.size() > 0) {
            columnValueMapping.forEach(column -> {
              if (column.containsKey(SERVICE_STARTTS) && !column.get(SERVICE_STARTTS).equals("")) {
                dbOperation(insertSQL(tableName, column));
              }
            });
          }
          break;
        case UPDATE:
          if (columnValueMapping != null && columnValueMapping.size() > 0) {
            columnValueMapping.forEach(column -> {
              if (column.containsKey(SERVICE_STARTTS) && !column.get(SERVICE_STARTTS).equals("")) {
                dbOperation(updateSQL(tableName, column, Collections.singletonMap("id", changeEvent.getUuid())));
              }
            });
          }
          break;
        default:
          log.info("Change Event Type not Handled: {}", changeEvent.getChangeType());
      }
    } catch (Exception e) {
      log.info(String.format("Not able to parse this event %s", changeEvent));
    }
    return true;
  }

  public List<Map<String, String>> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    List<Map<String, String>> nodeMap = new ArrayList<>();
    DBObject dbObject = changeEvent.getFullDocument();
    String accountId = null;
    String orgIdentifier = null;
    String projectIdentifier = null;

    if (dbObject == null) {
      return nodeMap;
    }

    if (dbObject.get("accountId") != null) {
      accountId = dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.accountId).toString();
    }
    if (dbObject.get("orgIdentifier") != null) {
      orgIdentifier = dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.orgIdentifier).toString();
    }
    if (dbObject.get("projectIdentifier") != null) {
      projectIdentifier =
          dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.projectIdentifier).toString();
    }

    // if moduleInfo is null, no need to push data in this table
    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.moduleInfo) == null) {
      return null;
    }

    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap) == null) {
      return null;
    }

    Set<Map.Entry<String, Object>> layoutNodeMap =
        ((BasicDBObject) dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.layoutNodeMap))
            .entrySet();

    Iterator<Map.Entry<String, Object>> iterator = layoutNodeMap.iterator();
    while (iterator.hasNext()) {
      Map<String, String> columnValueMapping = new HashMap<>();
      Map.Entry<String, Object> iteratorObject = iterator.next();
      String id = iteratorObject.getKey();
      columnValueMapping.put("pipeline_execution_summary_cd_id", changeEvent.getUuid());
      columnValueMapping.put("id", id);
      columnValueMapping.put("service_status", "");

      // stage - status
      if (((BasicDBObject) iteratorObject.getValue()).get("status") != null) {
        String service_status = ((BasicDBObject) iteratorObject.getValue()).get("status").toString();
        if (service_status != null) {
          columnValueMapping.put("service_status", service_status);
        }
      }

      // service_startts
      if (((BasicDBObject) iteratorObject.getValue()).get("startTs") != null) {
        String service_startts =
            String.valueOf(Long.parseLong(((BasicDBObject) iteratorObject.getValue()).get("startTs").toString()));
        if (service_startts != null) {
          columnValueMapping.put("service_startts", service_startts);
        }
      } else {
        columnValueMapping.put("service_startts", "");
      }

      // service_endts
      if (((BasicDBObject) iteratorObject.getValue()).get("endTs") != null) {
        String service_endts =
            String.valueOf(Long.parseLong(((BasicDBObject) iteratorObject.getValue()).get("endTs").toString()));
        if (service_endts != null) {
          columnValueMapping.put("service_endts", service_endts);
        }
      } else {
        columnValueMapping.put("service_endts", "");
      }

      columnValueMapping.put("service_name", "");
      columnValueMapping.put("service_id", "");
      columnValueMapping.put("accountId", "");
      columnValueMapping.put("orgIdentifier", "");
      columnValueMapping.put("projectIdentifier", "");
      columnValueMapping.put("deployment_type", "");
      columnValueMapping.put("env_name", "");
      columnValueMapping.put("env_id", "");
      columnValueMapping.put("env_type", "");

      DBObject moduleInfoObject = (DBObject) ((DBObject) iteratorObject.getValue()).get("moduleInfo");
      if (moduleInfoObject != null) {
        DBObject cdObject = (DBObject) moduleInfoObject.get("cd");
        if (cdObject != null) {
          // serviceInfo
          if (cdObject.get("serviceInfo") != null) {
            DBObject serviceInfoObject = (DBObject) cdObject.get("serviceInfo");
            // service_name
            String serviceName = serviceInfoObject.get("displayName").toString();
            columnValueMapping.put("service_name", serviceName);

            // service_id
            String serviceId = serviceInfoObject.get("identifier").toString();
            columnValueMapping.put("service_id", serviceId);

            // accountId
            columnValueMapping.put("accountId", accountId);

            // orgIdentifier
            columnValueMapping.put("orgIdentifier", orgIdentifier);

            // projectIdentifier
            columnValueMapping.put("projectIdentifier", projectIdentifier);

            // gitOpsEnabled
            if (serviceInfoObject.get("gitOpsEnabled") != null) {
              String gitOpsEnabled = serviceInfoObject.get("gitOpsEnabled").toString();
              columnValueMapping.put("gitOpsEnabled", gitOpsEnabled);
            }

            // deploymentType
            String deploymentType = serviceInfoObject.get("deploymentType").toString();
            columnValueMapping.put("deployment_type", deploymentType);

            // artifact - tag
            if (serviceInfoObject.get("artifacts") != null) {
              DBObject artifacts = (DBObject) serviceInfoObject.get("artifacts");
              // Add artifacts here
              String tag = "";
              String imagePath = "";
              if (artifacts.get("primary") != null) {
                DBObject primary = (DBObject) artifacts.get("primary");

                for (String tagName : tagNameSet) {
                  if (primary.get(tagName) != null) {
                    tag = primary.get(tagName).toString();
                    break;
                  }
                }
                for (String artifactPath : artifactPathNameSet) {
                  if (primary.get(artifactPath) != null) {
                    imagePath = primary.get(artifactPath).toString();
                    break;
                  }
                }
                columnValueMapping.put("tag", tag);
                columnValueMapping.put("artifact_image", imagePath);
              }
            }

            // env info
            if (cdObject.get("infraExecutionSummary") != null) {
              DBObject infraExecutionSummaryObject = (DBObject) cdObject.get("infraExecutionSummary");
              if (infraExecutionSummaryObject.get("name") != null) {
                String envName = infraExecutionSummaryObject.get("name").toString();
                columnValueMapping.put("env_name", envName);
              }

              if (infraExecutionSummaryObject.get("identifier") != null
                  && infraExecutionSummaryObject.get("identifier").toString().length() != 0) {
                String envIdentifier = infraExecutionSummaryObject.get("identifier").toString();
                columnValueMapping.put("env_id", envIdentifier);
              }

              if (infraExecutionSummaryObject.get("infrastructureIdentifier") != null
                  && infraExecutionSummaryObject.get("infrastructureIdentifier").toString().length() != 0) {
                String infrastructureIdentifier =
                    infraExecutionSummaryObject.get("infrastructureIdentifier").toString();
                columnValueMapping.put("infrastructureIdentifier", infrastructureIdentifier);
              }

              if (infraExecutionSummaryObject.get("infrastructureName") != null
                  && infraExecutionSummaryObject.get("infrastructureName").toString().length() != 0) {
                String infrastructureName = infraExecutionSummaryObject.get("infrastructureName").toString();
                columnValueMapping.put("infrastructureName", infrastructureName);
              }

              if (infraExecutionSummaryObject.get("type") != null
                  && infraExecutionSummaryObject.get("type").toString().length() != 0) {
                String envType = infraExecutionSummaryObject.get("type").toString();
                columnValueMapping.put("env_type", envType);
              }
            }
          }
        }
      }
      nodeMap.add(columnValueMapping);
    }

    return nodeMap;
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
      Map<String, String> columnValueMappingForCondition) {
    StringBuilder updateQueryBuilder = new StringBuilder(2048);

    /**
     * Adding insert statement
     */
    if (insertSQL(tableName, columnValueMappingForSet) != null) {
      updateQueryBuilder.append(insertSQL(tableName, columnValueMappingForSet));
    }
    // On conflict condition
    updateQueryBuilder.append(" ON CONFLICT (id,service_startts) Do ");

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
}
