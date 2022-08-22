/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HPersistence.DEFAULT_STORE;
import static io.harness.threading.Morpheus.sleep;

import io.harness.beans.CreatedByType;
import io.harness.beans.ExecutionCause;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.WorkflowType;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.WorkflowExecutionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.Key;

@Slf4j
@Singleton
public class DeploymentsMigrationHelper {
  public static final String COLLECTION_NAME = "workflowExecutions";
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject WingsPersistence wingsPersistence;
  @Inject WorkflowExecutionService workflowExecutionService;

  public void setParentPipelineForAccountIds(
      List<String> accountIds, String debugLine, int batchLimit, String update_statement) {
    Map<String, Set<String>> accountIdToAppIdMap = prepareAppIdAccountIdMap(accountIds, debugLine);
    for (Map.Entry<String, Set<String>> entry : accountIdToAppIdMap.entrySet()) {
      for (String appId : entry.getValue()) {
        bulkSetParentPipelineId(entry.getKey(), COLLECTION_NAME, appId, debugLine, batchLimit, update_statement);
      }
    }
  }

  public void setFailureDetailsForAccountIds(
      List<String> accountIds, String debugLine, int batchLimit, String update_statement) {
    Map<String, Set<String>> accountIdToAppIdMap = prepareAppIdAccountIdMap(accountIds, debugLine);
    for (Map.Entry<String, Set<String>> entry : accountIdToAppIdMap.entrySet()) {
      for (String appId : entry.getValue()) {
        bulkSetFailureDetails(entry.getKey(), COLLECTION_NAME, appId, debugLine, batchLimit, update_statement);
      }
    }
  }

  private Map<String, Set<String>> prepareAppIdAccountIdMap(List<String> accountIds, String debugLine) {
    Map<String, Set<String>> accountIdToAppIdMap = new HashMap<>();
    for (String accountId : accountIds) {
      try {
        List<Key<Application>> appIdKeyList =
            wingsPersistence.createQuery(Application.class).filter(ApplicationKeys.accountId, accountId).asKeyList();

        if (isNotEmpty(appIdKeyList)) {
          Set<String> appIdSet =
              appIdKeyList.stream().map(applicationKey -> (String) applicationKey.getId()).collect(Collectors.toSet());
          accountIdToAppIdMap.put(accountId, appIdSet);
        }
      } catch (Exception e) {
        log.error(
            debugLine + "Exception occurred migrating parent pipeline id to timescale deployments for accountId {}",
            accountId, e);
      }
    }
    return accountIdToAppIdMap;
  }

  public void bulkSetParentPipelineId(String accountId, String collectionName, String appId, String debugLine,
      int batchLimit, String update_statement) {
    log.info(debugLine + "Migrating all workflowExecutions for account " + accountId);
    final DBCollection collection = wingsPersistence.getCollection(DEFAULT_STORE, collectionName);

    BasicDBObject objectsToBeUpdated = new BasicDBObject("accountId", accountId).append("appId", appId);
    BasicDBObject projection = new BasicDBObject("_id", Boolean.TRUE)
                                   .append(WorkflowExecutionKeys.pipelineSummary, Boolean.TRUE)
                                   .append(WorkflowExecutionKeys.workflowIds, Boolean.TRUE)
                                   .append(WorkflowExecutionKeys.workflowType, Boolean.TRUE)
                                   .append(WorkflowExecutionKeys.pipelineExecutionId, Boolean.TRUE)
                                   .append(WorkflowExecutionKeys.createdByType, Boolean.TRUE)
                                   .append(WorkflowExecutionKeys.deploymentTriggerId, Boolean.TRUE)
                                   .append(WorkflowExecutionKeys.triggeredBy, Boolean.TRUE);

    DBCursor dataRecords = collection.find(objectsToBeUpdated, projection)
                               .sort(new BasicDBObject().append(WorkflowExecutionKeys.createdAt, -1))
                               .limit(batchLimit);

    int updated = 0;
    List<DBObject> workflowExecutionObjects = new ArrayList<>();
    try {
      while (dataRecords.hasNext()) {
        DBObject record = dataRecords.next();
        workflowExecutionObjects.add(record);
        updated++;

        if (updated != 0 && updated % batchLimit == 0) {
          executeTimeScaleParentPipelineQueries(update_statement, workflowExecutionObjects);
          sleep(Duration.ofMillis(100));
          dataRecords = collection.find(objectsToBeUpdated, projection)
                            .sort(new BasicDBObject().append(WorkflowExecutionKeys.createdAt, -1))
                            .skip(updated)
                            .limit(batchLimit);
          log.info(debugLine + "Number of records updated for {} is: {}", collectionName, updated);
        }
      }

      if (updated % batchLimit != 0) {
        executeTimeScaleParentPipelineQueries(update_statement, workflowExecutionObjects);
        log.info(debugLine + "Number of records updated for {} is: {}", collectionName, updated);
      }
    } catch (Exception e) {
      log.error(debugLine
              + "Exception occurred migrating parent pipeline id to timescale deployments for accountId {}, appId {}",
          accountId, appId, e);
    } finally {
      dataRecords.close();
    }
  }

  private void executeTimeScaleParentPipelineQueries(String update_statement, List<DBObject> workflowExecutionObjects)
      throws SQLException {
    try (Connection connection = timeScaleDBService.getDBConnection();
         PreparedStatement updateStatement = connection.prepareStatement(update_statement)) {
      for (DBObject executionRecord : workflowExecutionObjects) {
        String uuId = (String) executionRecord.get("_id");
        List<String> workflowIds = (List<String>) executionRecord.get(WorkflowExecutionKeys.workflowIds);
        String workflowType = (String) executionRecord.get(WorkflowExecutionKeys.workflowType);
        String parentPipelineId = null;
        DBObject pipelineSummary = (DBObject) executionRecord.get(WorkflowExecutionKeys.pipelineSummary);
        if (pipelineSummary != null && WorkflowType.ORCHESTRATION.name().equals(workflowType)) {
          parentPipelineId = (String) pipelineSummary.get("pipelineId");
        }
        String cause = getCause(executionRecord);

        updateStatement.setString(1, parentPipelineId);
        Array array = null;
        if (workflowIds != null && WorkflowType.PIPELINE.name().equals(workflowType)) {
          array = connection.createArrayOf("text", workflowIds.toArray());
        }
        updateStatement.setArray(2, array);
        updateStatement.setString(3, cause);
        updateStatement.setString(4, uuId);
        updateStatement.addBatch();
      }
      int[] affectedRecords = updateStatement.executeBatch();
    }
  }

  public static String getCause(DBObject record) {
    if (record.get(WorkflowExecutionKeys.pipelineExecutionId) != null) {
      return ExecutionCause.ExecutedAlongPipeline.name();
    } else {
      String createdByType = (String) record.get(WorkflowExecutionKeys.createdByType);
      if (CreatedByType.API_KEY.name().equals(createdByType)) {
        return ExecutionCause.ExecutedByAPIKey.name();
      } else if (record.get(WorkflowExecutionKeys.deploymentTriggerId) != null) {
        return ExecutionCause.ExecutedByTrigger.name();
      } else if (record.get(WorkflowExecutionKeys.triggeredBy) != null) {
        return ExecutionCause.ExecutedByUser.name();
      }
    }
    return null;
  }

  private void bulkSetFailureDetails(String accountId, String collectionName, String appId, String debugLine,
      int batchLimit, String update_statement) {
    log.info(debugLine + "Migrating all workflowExecutions for account " + accountId);

    final DBCollection collection = wingsPersistence.getCollection(DEFAULT_STORE, collectionName);

    BasicDBObject objectsToBeUpdated =
        new BasicDBObject("accountId", accountId)
            .append("appId", appId)
            .append(WorkflowExecutionKeys.status,
                new BasicDBObject(
                    "$in", ExecutionStatus.resumableStatuses.stream().map(Enum::name).collect(Collectors.toList())));
    BasicDBObject projection = new BasicDBObject("_id", Boolean.TRUE);
    DBCursor dataRecords = collection.find(objectsToBeUpdated, projection)
                               .sort(new BasicDBObject().append(WorkflowExecutionKeys.createdAt, -1))
                               .limit(batchLimit);

    List<WorkflowExecution> workflowExecutions = new ArrayList<>();

    int updated = 0;
    try {
      while (dataRecords.hasNext()) {
        DBObject record = dataRecords.next();
        String uuId = (String) record.get("_id");
        workflowExecutions.add(WorkflowExecution.builder().uuid(uuId).build());
        updated++;

        if (updated != 0 && updated % batchLimit == 0) {
          List<WorkflowExecution> workflowExecutionsWithFailureDetails =
              workflowExecutionService.getWorkflowExecutionsWithFailureDetails(appId, workflowExecutions);
          try (Connection connection = timeScaleDBService.getDBConnection();
               PreparedStatement updateStatement = connection.prepareStatement(update_statement)) {
            for (WorkflowExecution workflowExecution : workflowExecutionsWithFailureDetails) {
              updateStatement.setString(1, workflowExecution.getFailureDetails());
              updateStatement.setString(2, workflowExecution.getFailedStepNames());
              updateStatement.setString(3, workflowExecution.getFailedStepTypes());
              updateStatement.setString(4, workflowExecution.getUuid());
              updateStatement.addBatch();
            }
            int[] affectedRecords = updateStatement.executeBatch();
          }
          sleep(Duration.ofMillis(100));
          dataRecords = collection.find(objectsToBeUpdated, projection)
                            .sort(new BasicDBObject().append(WorkflowExecutionKeys.createdAt, -1))
                            .skip(updated)
                            .limit(batchLimit);
          log.info(debugLine + "Number of records updated for {} is: {}", collectionName, updated);
        }
      }

      if (updated % batchLimit != 0) {
        List<WorkflowExecution> workflowExecutionsWithFailureDetails =
            workflowExecutionService.getWorkflowExecutionsWithFailureDetails(appId, workflowExecutions);
        try (Connection connection = timeScaleDBService.getDBConnection();
             PreparedStatement updateStatement = connection.prepareStatement(update_statement)) {
          for (WorkflowExecution workflowExecution : workflowExecutionsWithFailureDetails) {
            updateStatement.setString(1, workflowExecution.getFailureDetails());
            updateStatement.setString(2, workflowExecution.getFailedStepNames());
            updateStatement.setString(3, workflowExecution.getFailedStepTypes());
            updateStatement.setString(4, workflowExecution.getUuid());
            updateStatement.addBatch();
          }
          int[] affectedRecords = updateStatement.executeBatch();
        }
        log.info(debugLine + "Number of records updated for {} is: {}", collectionName, updated);
      }
    } catch (Exception e) {
      log.error(debugLine
              + "Exception occurred migrating parent pipeline id to timescale deployments for accountId {}, appId {}",
          accountId, appId, e);
    }
  }
}
