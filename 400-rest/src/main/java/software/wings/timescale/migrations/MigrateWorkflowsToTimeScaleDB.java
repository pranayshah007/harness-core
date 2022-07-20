/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Workflow.WorkflowKeys;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Workflow;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;

@Slf4j
@Singleton
public class MigrateWorkflowsToTimeScaleDB {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String insert_statement =
      "INSERT INTO CG_WORKFLOWS (ID,NAME,ACCOUNT_ID,ORCHESTRATION_WORKFLOW_TYPE,ENV_ID,APP_ID,SERVICE_IDS,DEPLOYMENT_TYPE,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

  private static final String update_statement =
      "UPDATE CG_WORKFLOWS SET NAME=?, ACCOUNT_ID=?, ORCHESTRATION_WORKFLOW_TYPE=?, ENV_ID=?, APP_ID=?, SERVICE_IDS=?, DEPLOYMENT_TYPE=?, CREATED_AT=?, LAST_UPDATED_AT=?, CREATED_BY=?, LAST_UPDATED_BY=? WHERE ID=?";

  private static final String query_statement = "SELECT * FROM CG_WORKFLOWS WHERE ID=?";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions_workflows = new FindOptions();
      findOptions_workflows.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<Workflow> iterator = new HIterator<>(wingsPersistence.createQuery(Workflow.class, excludeAuthority)
                                                              .field(WorkflowKeys.accountId)
                                                              .equal(accountId)
                                                              .fetch(findOptions_workflows))) {
        while (iterator.hasNext()) {
          Workflow workflow = iterator.next();
          prepareTimeScaleQueries(workflow);
          count++;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to complete migration", e);
      return false;
    } finally {
      log.info("Completed migrating [{}] records", count);
    }
    return true;
  }

  private void prepareTimeScaleQueries(Workflow workflow) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;

      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement queryStatement = connection.prepareStatement(query_statement);
           PreparedStatement updateStatement = connection.prepareStatement(update_statement);
           PreparedStatement insertStatement = connection.prepareStatement(insert_statement)) {
        queryStatement.setString(1, workflow.getUuid());
        queryResult = queryStatement.executeQuery();
        if (queryResult != null && queryResult.next()) {
          log.info("Workflow found in the timescaleDB:[{}],updating it", workflow.getUuid());
          updateDataInTimeScaleDB(workflow, connection, updateStatement);
        } else {
          log.info("Workflow not found in the timescaleDB:[{}],inserting it", workflow.getUuid());
          insertDataInTimeScaleDB(workflow, connection, insertStatement);
        }
        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to save workflow,[{}]", workflow.getUuid(), e);
        } else {
          log.info("Failed to save workflow,[{}],retryCount=[{}]", workflow.getUuid(), retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save workflow,[{}]", workflow.getUuid(), e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info("Total time =[{}] for workflow:[{}]", System.currentTimeMillis() - startTime, workflow.getUuid());
      }
    }
  }

  private void insertDataInTimeScaleDB(
      Workflow workflow, Connection connection, PreparedStatement insertPreparedStatement) throws SQLException {
    insertPreparedStatement.setString(1, workflow.getUuid());
    insertPreparedStatement.setString(2, workflow.getName());
    insertPreparedStatement.setString(3, workflow.getAccountId());
    insertPreparedStatement.setString(4, workflow.getOrchestration().getOrchestrationWorkflowType().toString());
    insertPreparedStatement.setString(5, workflow.getEnvId());
    insertPreparedStatement.setString(6, workflow.getAppId());

    insertArrayData(7, connection, insertPreparedStatement, workflow.getOrchestration().getServiceIds());

    List<String> deploymentTypes = new LinkedList<>();
    CanaryOrchestrationWorkflow coWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestration();
    if (coWorkflow != null && coWorkflow.getWorkflowPhaseIdMap() != null) {
      coWorkflow.getWorkflowPhaseIdMap().values().forEach(workflowPhase -> {
        if (workflowPhase.getDeploymentType() != null) {
          deploymentTypes.add(workflowPhase.getDeploymentType().getDisplayName());
        }
      });
    }
    insertArrayData(8, connection, insertPreparedStatement, deploymentTypes);

    insertPreparedStatement.setLong(9, workflow.getCreatedAt());
    insertPreparedStatement.setLong(10, workflow.getLastUpdatedAt());

    String created_by = null;
    if (workflow.getCreatedBy() != null) {
      created_by = workflow.getCreatedBy().getName();
    }
    insertPreparedStatement.setString(11, created_by);

    String last_updated_by = null;
    if (workflow.getLastUpdatedBy() != null) {
      last_updated_by = workflow.getLastUpdatedBy().getName();
    }
    insertPreparedStatement.setString(12, last_updated_by);

    insertPreparedStatement.execute();
  }

  private void updateDataInTimeScaleDB(Workflow workflow, Connection connection, PreparedStatement updateStatement)
      throws SQLException {
    updateStatement.setString(1, workflow.getName());
    updateStatement.setString(2, workflow.getAccountId());
    updateStatement.setString(3, workflow.getOrchestration().getOrchestrationWorkflowType().toString());
    updateStatement.setString(4, workflow.getEnvId());
    updateStatement.setString(5, workflow.getAppId());

    insertArrayData(6, connection, updateStatement, workflow.getOrchestration().getServiceIds());

    List<String> deploymentTypes = new LinkedList<>();
    CanaryOrchestrationWorkflow coWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestration();
    if (coWorkflow != null && coWorkflow.getWorkflowPhaseIdMap() != null) {
      coWorkflow.getWorkflowPhaseIdMap().values().forEach(workflowPhase -> {
        if (workflowPhase.getDeploymentType() != null) {
          deploymentTypes.add(workflowPhase.getDeploymentType().getDisplayName());
        }
      });
    }
    insertArrayData(7, connection, updateStatement, deploymentTypes);

    updateStatement.setLong(8, workflow.getCreatedAt());
    updateStatement.setLong(9, workflow.getLastUpdatedAt());

    String created_by = null;
    if (workflow.getCreatedBy() != null) {
      created_by = workflow.getCreatedBy().getName();
    }
    updateStatement.setString(10, created_by);

    String last_updated_by = null;
    if (workflow.getLastUpdatedBy() != null) {
      last_updated_by = workflow.getLastUpdatedBy().getName();
    }
    updateStatement.setString(11, last_updated_by);
    updateStatement.setString(12, workflow.getUuid());
    updateStatement.execute();
  }

  private void insertArrayData(
      int index, Connection dbConnection, PreparedStatement preparedStatement, List<String> data) throws SQLException {
    if (!isEmpty(data)) {
      Array array = dbConnection.createArrayOf("text", data.toArray());
      preparedStatement.setArray(index, array);
    } else {
      preparedStatement.setArray(index, null);
    }
  }
}
