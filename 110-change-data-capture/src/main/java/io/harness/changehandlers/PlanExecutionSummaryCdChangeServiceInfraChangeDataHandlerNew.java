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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.bson.BasicBSONObject;
import org.bson.types.BasicBSONList;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PlanExecutionSummaryCdChangeServiceInfraChangeDataHandlerNew implements ChangeHandler {
  private static final int MAX_RETRY_COUNT = 5;

  // These set of keys we can use to populate data to 'artifact_image' in service_infra_info
  private static final List<String> artifactPathNameSet = List.of("imagePath", "artifactPath", "bucketName", "jobName");
  // These set of keys we can use to populate data to 'tag' in service_infra_info.
  // Passing artifactPath as both tag and artifact_image in case of ArtifactoryGenericArtifactSummary. Have put in end
  // to avoid conflict for other ArtifactSummary
  private static final List<String> tagNameSet = Arrays.asList("tag", "version", "build", "artifactPath");
  static final String ENV_GROUP_IDENTIFIER = "envGroupIdentifier";
  static final String ARTIFACT_DISPLAY_NAME = "artifactDisplayName";

  @Inject private TimeScaleDBService timeScaleDBService;

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent, String tableName, String[] fields) {
    log.trace("In TimeScale Change Handler: {}, {}, {}", changeEvent, tableName, fields);
    List<Map<String, String>> columnValueMapping;
    try {
      columnValueMapping = getColumnValueMapping(changeEvent);
      switch (changeEvent.getChangeType()) {
        case INSERT:
          if (columnValueMapping != null && columnValueMapping.size() > 0) {
            columnValueMapping.forEach(column -> {
              if (column.containsKey(PlanExecutionSummaryCDConstants.SERVICE_START_TS)
                  && !column.get(PlanExecutionSummaryCDConstants.SERVICE_START_TS).equals("")) {
                dbOperation(insertSQL(tableName, column));
              }
            });
          }
          break;
        case UPDATE:
          if (columnValueMapping != null && columnValueMapping.size() > 0) {
            columnValueMapping.forEach(column -> {
              if (column.containsKey(PlanExecutionSummaryCDConstants.SERVICE_START_TS)
                  && !column.get(PlanExecutionSummaryCDConstants.SERVICE_START_TS).equals("")) {
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

  public List<Map<String, String>> getColumnValueMapping(ChangeEvent<?> changeEvent) {
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

    for (Map.Entry<String, Object> stageExecutionNode : layoutNodeMap) {
      // columnValueMappingList: for the case when single stageExecution contains multiple environments ( gitops )
      final List<Map<String, String>> columnValueMappingList = new ArrayList<>();
      Map<String, String> columnValueMapping = new HashMap<>();
      String id = stageExecutionNode.getKey();
      columnValueMapping.put("pipeline_execution_summary_cd_id", changeEvent.getUuid());
      columnValueMapping.put("id", id);
      columnValueMapping.put("service_status", "");

      // stage - status
      if (((BasicDBObject) stageExecutionNode.getValue()).get("status") != null) {
        String service_status = ((BasicDBObject) stageExecutionNode.getValue()).get("status").toString();
        if (service_status != null) {
          columnValueMapping.put("service_status", service_status);
        }
      }

      if (((BasicDBObject) stageExecutionNode.getValue()).get("startTs") != null) {
        String service_start_ts =
            String.valueOf(Long.parseLong(((BasicDBObject) stageExecutionNode.getValue()).get("startTs").toString()));
        columnValueMapping.put(PlanExecutionSummaryCDConstants.SERVICE_START_TS, service_start_ts);
      } else {
        columnValueMapping.put(PlanExecutionSummaryCDConstants.SERVICE_START_TS, "");
      }

      // service_endts
      if (((BasicDBObject) stageExecutionNode.getValue()).get("endTs") != null) {
        String service_end_ts =
            String.valueOf(Long.parseLong(((BasicDBObject) stageExecutionNode.getValue()).get("endTs").toString()));
        columnValueMapping.put(PlanExecutionSummaryCDConstants.SERVICE_END_TS, service_end_ts);
      } else {
        columnValueMapping.put(PlanExecutionSummaryCDConstants.SERVICE_END_TS, "");
      }

      columnValueMapping.put("service_name", "");
      columnValueMapping.put("service_id", "");
      columnValueMapping.put(PlanExecutionSummaryCDConstants.ACCOUNT_ID_KEY, "");
      columnValueMapping.put(PlanExecutionSummaryCDConstants.ORG_IDENTIFIER_KEY, "");
      columnValueMapping.put(PlanExecutionSummaryCDConstants.PROJECT_IDENTIFIER_KEY, "");
      columnValueMapping.put("deployment_type", "");
      columnValueMapping.put("env_name", "");
      columnValueMapping.put("env_id", "");
      columnValueMapping.put("env_type", "");
      columnValueMapping.put("rollback_duration", "");

      DBObject moduleInfoObject = (DBObject) ((DBObject) stageExecutionNode.getValue()).get("moduleInfo");
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
            String serviceId = serviceInfoObject.get(PlanExecutionSummaryCDConstants.IDENTIFIER_KEY).toString();
            columnValueMapping.put("service_id", serviceId);

            // accountId
            columnValueMapping.put(PlanExecutionSummaryCDConstants.ACCOUNT_ID_KEY, accountId);

            // orgIdentifier
            columnValueMapping.put(PlanExecutionSummaryCDConstants.ORG_IDENTIFIER_KEY, orgIdentifier);

            // projectIdentifier
            columnValueMapping.put(PlanExecutionSummaryCDConstants.PROJECT_IDENTIFIER_KEY, projectIdentifier);

            // gitOpsEnabled
            if (serviceInfoObject.get(PlanExecutionSummaryCDConstants.GITOPS_ENABLED_KEY) != null) {
              String gitOpsEnabled =
                  serviceInfoObject.get(PlanExecutionSummaryCDConstants.GITOPS_ENABLED_KEY).toString();
              columnValueMapping.put(PlanExecutionSummaryCDConstants.GITOPS_ENABLED_KEY, gitOpsEnabled);
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

              if (artifacts.get(ARTIFACT_DISPLAY_NAME) != null) {
                columnValueMapping.put("artifact_display_name", artifacts.get(ARTIFACT_DISPLAY_NAME).toString());
              }
            }

            // infraExecutionSummary
            columnValueMapping.putAll(generateColumnValueMappingFromInfraExecSummary(cdObject));
            generateEnvMappingFromGitOpsExecSummary(cdObject).forEach(incomingMapping -> {
              Map<String, String> copyOfMapping = new HashMap<>(columnValueMapping);
              copyOfMapping.putAll(incomingMapping);
              columnValueMappingList.add(copyOfMapping);
            });
          }

          // rollback_duration
          if (cdObject.get("rollbackDuration") != null && !cdObject.get("rollbackDuration").toString().isEmpty()) {
            String rollbackDuration = cdObject.get("rollbackDuration").toString();
            columnValueMapping.put("rollback_duration", rollbackDuration);
          }
        }
      }
      if (EmptyPredicate.isNotEmpty(columnValueMappingList)) {
        nodeMap.addAll(columnValueMappingList);
      } else {
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
    updateQueryBuilder.append(" ON CONFLICT (id,service_startts) Do UPDATE  SET ");

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

  private Map<String, String> generateColumnValueMappingFromInfraExecSummary(DBObject cdObject) {
    Map<String, String> columnValueMapping = new HashMap<>();
    if (cdObject.get("infraExecutionSummary") != null) {
      DBObject infraExecutionSummaryObject = (DBObject) cdObject.get("infraExecutionSummary");
      if (infraExecutionSummaryObject.get("name") != null) {
        String envName = infraExecutionSummaryObject.get("name").toString();
        columnValueMapping.put("env_name", envName);
      }

      if (infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.IDENTIFIER_KEY) != null
          && infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.IDENTIFIER_KEY).toString().length() != 0) {
        String envIdentifier =
            infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.IDENTIFIER_KEY).toString();
        columnValueMapping.put("env_id", envIdentifier);
      }

      if (infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.INFRASTRUCTURE_IDENTIFIER_KEY) != null
          && infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.INFRASTRUCTURE_IDENTIFIER_KEY)
                  .toString()
                  .length()
              != 0) {
        String infrastructureIdentifier =
            infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.INFRASTRUCTURE_IDENTIFIER_KEY).toString();
        columnValueMapping.put(PlanExecutionSummaryCDConstants.INFRASTRUCTURE_IDENTIFIER_KEY, infrastructureIdentifier);
      }

      if (infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.INFRASTRUCTURE_NAME_KEY) != null
          && infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.INFRASTRUCTURE_NAME_KEY)
                  .toString()
                  .length()
              != 0) {
        String infrastructureName =
            infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.INFRASTRUCTURE_NAME_KEY).toString();
        columnValueMapping.put(PlanExecutionSummaryCDConstants.INFRASTRUCTURE_NAME_KEY, infrastructureName);
      }

      if (infraExecutionSummaryObject.get("type") != null
          && infraExecutionSummaryObject.get("type").toString().length() != 0) {
        String envType = infraExecutionSummaryObject.get("type").toString();
        columnValueMapping.put("env_type", envType);
      }

      if (infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.ENV_GROUP_ID) != null
          && infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.ENV_GROUP_ID).toString().length() > 0) {
        String envGroupId = infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.ENV_GROUP_ID).toString();
        columnValueMapping.put("env_group_ref", envGroupId);
      }

      if (infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.ENV_GROUP_NAME) != null
          && infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.ENV_GROUP_NAME).toString().length() > 0) {
        String envGroupName =
            infraExecutionSummaryObject.get(PlanExecutionSummaryCDConstants.ENV_GROUP_NAME).toString();
        columnValueMapping.put("env_group_name", envGroupName);
      }
    }
    return columnValueMapping;
  }

  private List<Map<String, String>> generateEnvMappingFromGitOpsExecSummary(DBObject cdObject) {
    final List<Map<String, String>> result = new ArrayList<>();
    if (cdObject.containsField("gitopsExecutionSummary")) {
      BasicBSONObject gitopsExecutionSummary = (BasicBSONObject) cdObject.get("gitopsExecutionSummary");
      if (gitopsExecutionSummary.containsField("environments")) {
        BasicBSONList environments = (BasicBSONList) gitopsExecutionSummary.get("environments");
        for (Object environment : environments) {
          Map<String, String> columnMappingForSingleEnv = new HashMap<>();
          if (environment instanceof BasicBSONObject) {
            BasicBSONObject environmentObject = (BasicBSONObject) environment;
            if (environmentObject.get("name") != null) {
              String envName = environmentObject.get("name").toString();
              columnMappingForSingleEnv.put("env_name", envName);
            }
            if (environmentObject.get(PlanExecutionSummaryCDConstants.IDENTIFIER_KEY) != null
                && environmentObject.get(PlanExecutionSummaryCDConstants.IDENTIFIER_KEY).toString().length() != 0) {
              String envIdentifier = environmentObject.get(PlanExecutionSummaryCDConstants.IDENTIFIER_KEY).toString();
              columnMappingForSingleEnv.put("env_id", envIdentifier);
            }

            if (environmentObject.get("type") != null && environmentObject.get("type").toString().length() != 0) {
              String envType = environmentObject.get("type").toString();
              columnMappingForSingleEnv.put("env_type", envType);
            }

            if (environmentObject.get(ENV_GROUP_IDENTIFIER) != null
                && environmentObject.get(ENV_GROUP_IDENTIFIER).toString().length() > 0) {
              String envGroupId = environmentObject.get(ENV_GROUP_IDENTIFIER).toString();
              columnMappingForSingleEnv.put("env_group_ref", envGroupId);
            }

            if (environmentObject.get(PlanExecutionSummaryCDConstants.ENV_GROUP_NAME) != null
                && environmentObject.get(PlanExecutionSummaryCDConstants.ENV_GROUP_NAME).toString().length() > 0) {
              String envGroupName = environmentObject.get(PlanExecutionSummaryCDConstants.ENV_GROUP_NAME).toString();
              columnMappingForSingleEnv.put("env_group_name", envGroupName);
            }
          }
          result.add(columnMappingForSingleEnv);
        }
      }
    }
    return result;
  }
}
