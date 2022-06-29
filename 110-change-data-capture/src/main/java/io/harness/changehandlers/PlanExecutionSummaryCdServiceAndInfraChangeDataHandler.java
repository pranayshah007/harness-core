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
public class PlanExecutionSummaryCdServiceAndInfraChangeDataHandler implements ChangeHandler {
  private static final int MAX_RETRY_COUNT = 5;
  @Inject private TimeScaleDBService timeScaleDBService;
  private static String SERVICE_STARTTS = "service_startts";
  private static String SERVICE_ENDTS = "service_endts";

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent, String tableName, String[] fields) {
    log.trace("In TimeScale Change Handler: {}, {}, {}", changeEvent, tableName, fields);
    Map<String, List<String>> columnValueMapping = null;
    try {
      columnValueMapping = getColumnValueMapping(changeEvent, fields);
      switch (changeEvent.getChangeType()) {
        case INSERT:
          if (columnValueMapping != null && columnValueMapping.size() > 0) {
            List<String> idList = columnValueMapping.get("id");
            Set<String> keyObject = columnValueMapping.keySet();
            Map<String, String> newColumnValueMapping = new HashMap<>();
            for (int i = 0; i < idList.size(); i++) {
              for (String entry : keyObject) {
                if (columnValueMapping.get(entry).size() > i) {
                  newColumnValueMapping.put(entry, columnValueMapping.get(entry).get(i));
                } else {
                  newColumnValueMapping.put(entry, "");
                }
              }
              if (newColumnValueMapping.containsKey(SERVICE_STARTTS)
                  && !newColumnValueMapping.get(SERVICE_STARTTS).equals("")) {
                dbOperation(insertSQL(tableName, newColumnValueMapping));
              }
            }
          }
          break;
        case UPDATE:
          if (columnValueMapping != null && columnValueMapping.size() > 0) {
            List<String> idList = columnValueMapping.get("id");
            Set<String> keyObject = columnValueMapping.keySet();
            Map<String, String> newColumnValueMapping = new HashMap<>();
            for (int i = 0; i < idList.size(); i++) {
              for (String entry : keyObject) {
                if (columnValueMapping.get(entry).size() > i) {
                  newColumnValueMapping.put(entry, columnValueMapping.get(entry).get(i));
                } else {
                  newColumnValueMapping.put(entry, "");
                }
              }
              if (newColumnValueMapping.containsKey(SERVICE_STARTTS)
                  && !newColumnValueMapping.get(SERVICE_STARTTS).equals("")) {
                dbOperation(
                    updateSQL(tableName, newColumnValueMapping, Collections.singletonMap("id", changeEvent.getUuid())));
              }
              newColumnValueMapping.clear();
            }
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

  public Map<String, List<String>> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    Map<String, List<String>> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();
    String accountId = null;
    String orgIdentifier = null;
    String projectIdentifier = null;

    if (dbObject == null) {
      return columnValueMapping;
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
      Map.Entry<String, Object> iteratorObject = iterator.next();
      String id = iteratorObject.getKey();
      if (((BasicDBObject) iteratorObject.getValue())
                  .get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.moduleInfo)
              == null
          || ((BasicDBObject) iteratorObject.getValue())
                  .get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.startTs)
              == null
          || ((BasicDBObject) iteratorObject.getValue()).get("nodeType") == null
          || !((BasicDBObject) iteratorObject.getValue()).get("nodeType").equals("Deployment")) {
        continue;
      }
      if (columnValueMapping.containsKey("pipeline_execution_summary_cd_id")) {
        columnValueMapping.get("pipeline_execution_summary_cd_id").add(changeEvent.getUuid());
      } else {
        List<String> executionIdList = new ArrayList<>();
        executionIdList.add(changeEvent.getUuid());
        columnValueMapping.put("pipeline_execution_summary_cd_id", executionIdList);
      }

      if (columnValueMapping.containsKey("id")) {
        columnValueMapping.get("id").add(id);
      } else {
        List<String> idList = new ArrayList<>();
        idList.add(id);
        columnValueMapping.put("id", idList);
      }

      // stage - status
      if (((BasicDBObject) iteratorObject.getValue()).get("status") != null) {
        String service_status = ((BasicDBObject) iteratorObject.getValue()).get("status").toString();
        if (service_status != null) {
          if (columnValueMapping.containsKey("service_status")) {
            columnValueMapping.get("service_status").add(service_status);
          } else {
            List<String> service_status_list = new ArrayList<>();
            service_status_list.add(service_status);
            columnValueMapping.put("service_status", service_status_list);
          }
        }
      }
      // service_startts
      if (((BasicDBObject) iteratorObject.getValue()).get("startTs") != null) {
        String service_startts =
            String.valueOf(Long.parseLong(((BasicDBObject) iteratorObject.getValue()).get("startTs").toString()));
        if (service_startts != null) {
          if (columnValueMapping.containsKey("service_startts")) {
            columnValueMapping.get("service_startts").add(service_startts);
          } else {
            List<String> service_startts_list = new ArrayList<>();
            service_startts_list.add(service_startts);
            columnValueMapping.put("service_startts", service_startts_list);
          }
        }
      } else {
        if (columnValueMapping.containsKey(SERVICE_STARTTS)) {
          columnValueMapping.get(SERVICE_STARTTS).add("");
        } else {
          List<String> service_startts_list = new ArrayList<>();
          service_startts_list.add("");
          columnValueMapping.put(SERVICE_STARTTS, service_startts_list);
        }
      }
      // service_endts
      if (((BasicDBObject) iteratorObject.getValue()).get("endTs") != null) {
        String service_endts =
            String.valueOf(Long.parseLong(((BasicDBObject) iteratorObject.getValue()).get("endTs").toString()));
        if (service_endts != null) {
          if (columnValueMapping.containsKey("service_endts")) {
            columnValueMapping.get("service_endts").add(service_endts);
          } else {
            List<String> service_endts_list = new ArrayList<>();
            service_endts_list.add(service_endts);
            columnValueMapping.put("service_endts", service_endts_list);
          }
        }
      } else {
        if (columnValueMapping.containsKey(SERVICE_ENDTS)) {
          columnValueMapping.get(SERVICE_ENDTS).add("");
        } else {
          List<String> service_startts_list = new ArrayList<>();
          service_startts_list.add("");
          columnValueMapping.put(SERVICE_ENDTS, service_startts_list);
        }
      }
      DBObject moduleInfoObject = (DBObject) ((DBObject) iteratorObject.getValue()).get("moduleInfo");
      if (moduleInfoObject != null) {
        DBObject cdObject = (DBObject) moduleInfoObject.get("cd");
        if (cdObject != null) {
          // serviceInfo
          if (cdObject.get("serviceInfo") != null) {
            DBObject serviceInfoObject = (DBObject) cdObject.get("serviceInfo");
            // service_name
            String serviceName = serviceInfoObject.get("displayName").toString();
            if (columnValueMapping.containsKey("service_name")) {
              columnValueMapping.get("service_name").add(serviceName);
            } else {
              List<String> serviceList = new ArrayList<>();
              serviceList.add(serviceName);
              columnValueMapping.put("service_name", serviceList);
            }

            // service_id
            String serviceId = serviceInfoObject.get("identifier").toString();
            if (columnValueMapping.containsKey("service_id")) {
              columnValueMapping.get("service_id").add(serviceId);
            } else {
              List<String> serviceIdList = new ArrayList<>();
              serviceIdList.add(serviceId);
              columnValueMapping.put("service_id", serviceIdList);
            }

            // accountId
            if (columnValueMapping.containsKey("accountId")) {
              columnValueMapping.get("accountId").add(accountId);
            } else {
              List<String> accountIdList = new ArrayList<>();
              accountIdList.add(accountId);
              columnValueMapping.put("accountId", accountIdList);
            }
            // orgIdentifier
            if (columnValueMapping.containsKey("orgIdentifier")) {
              columnValueMapping.get("orgIdentifier").add(orgIdentifier);
            } else {
              List<String> orgIdList = new ArrayList<>();
              orgIdList.add(orgIdentifier);
              columnValueMapping.put("orgIdentifier", orgIdList);
            }

            // projectIdentifier
            if (columnValueMapping.containsKey("projectIdentifier")) {
              columnValueMapping.get("projectIdentifier").add(projectIdentifier);
            } else {
              List<String> projectIdList = new ArrayList<>();
              projectIdList.add(projectIdentifier);
              columnValueMapping.put("projectIdentifier", projectIdList);
            }

            // deploymentType

            String deploymentType = serviceInfoObject.get("deploymentType").toString();
            if (columnValueMapping.containsKey("deployment_type")) {
              columnValueMapping.get("deployment_type").add(deploymentType);
            } else {
              List<String> deploymentTypeList = new ArrayList<>();
              deploymentTypeList.add(deploymentType);
              columnValueMapping.put("deployment_type", deploymentTypeList);
            }

            // artifact - tag
            if (serviceInfoObject.get("artifacts") != null) {
              DBObject artifacts = (DBObject) serviceInfoObject.get("artifacts");
              // Add artifacts here
              String tag = "";
              String imagePath = "";
              if (artifacts.get("primary") != null) {
                DBObject primary = (DBObject) artifacts.get("primary");
                if (primary.get("tag") != null || primary.get("version") != null) {
                  tag = primary.get("tag") == null ? primary.get("version").toString() : primary.get("tag").toString();
                }
                if (primary.get("imagePath") != null) {
                  imagePath = primary.get("imagePath").toString();
                }
              }
              if (columnValueMapping.containsKey("tag")) {
                columnValueMapping.get("tag").add(tag);
              } else {
                List<String> tagList = new ArrayList<>();
                tagList.add(tag);
                columnValueMapping.put("tag", tagList);
              }
              if (columnValueMapping.containsKey("artifact_image")) {
                columnValueMapping.get("artifact_image").add(imagePath);
              } else {
                List<String> tagList = new ArrayList<>();
                tagList.add(imagePath);
                columnValueMapping.put("artifact_image", tagList);
              }
            }
          }

          // env info
          if (cdObject.get("infraExecutionSummary") != null) {
            DBObject infraExecutionSummaryObject = (DBObject) cdObject.get("infraExecutionSummary");
            String envName = "";
            String envIdentifier = "";
            String envType = "";
            if (infraExecutionSummaryObject.get("name") != null) {
              envName = infraExecutionSummaryObject.get("name").toString();
            }

            if (infraExecutionSummaryObject.get("identifier") != null) {
              envIdentifier = infraExecutionSummaryObject.get("identifier").toString();
            }

            if (infraExecutionSummaryObject.get("type") != null) {
              envType = infraExecutionSummaryObject.get("type").toString();
            }

            if (envName != null && envName.length() != 0) {
              if (columnValueMapping.containsKey("env_name")) {
                columnValueMapping.get("env_name").add(envName);
              } else {
                List<String> envNameList = new ArrayList<>();
                envNameList.add(envName);
                columnValueMapping.put("env_name", envNameList);
              }
            }

            if (envIdentifier != null && envIdentifier.length() != 0) {
              if (columnValueMapping.containsKey("env_id")) {
                columnValueMapping.get("env_id").add(envIdentifier);
              } else {
                List<String> envIdList = new ArrayList<>();
                envIdList.add(envIdentifier);
                columnValueMapping.put("env_id", envIdList);
              }
            }

            if (envType != null && envType.length() != 0) {
              if (columnValueMapping.containsKey("env_type")) {
                columnValueMapping.get("env_type").add(envType);
              } else {
                List<String> envIdList = new ArrayList<>();
                envIdList.add(envType);
                columnValueMapping.put("env_type", envIdList);
              }
            }
          }

        } else {
          continue;
        }
      } else {
        continue;
      }
    }

    return columnValueMapping;
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
        updateQueryBuilder.append(String.format("%s=%s,", entry.getKey(), String.format("'%s'", entry.getValue())));
      }
    }

    updateQueryBuilder = new StringBuilder(updateQueryBuilder.subSequence(0, updateQueryBuilder.length() - 1));

    // Returning the generated UPDATE SQL Query as a String...
    return updateQueryBuilder.toString();
  }
}
