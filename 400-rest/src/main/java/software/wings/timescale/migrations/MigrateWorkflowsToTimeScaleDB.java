/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Workflow.WorkflowKeys;
import static software.wings.timescale.migrations.TimescaleEntityMigrationHelper.insertArrayData;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.Workflow;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.sql.Connection;
import java.sql.PreparedStatement;
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

  private static final String upsert_statement =
      "INSERT INTO CG_WORKFLOWS (ID,NAME,ACCOUNT_ID,ORCHESTRATION_WORKFLOW_TYPE,ENV_ID,APP_ID,SERVICE_IDS,DEPLOYMENT_TYPE,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(ID) DO UPDATE SET NAME = excluded.NAME,ACCOUNT_ID = excluded.ACCOUNT_ID,ORCHESTRATION_WORKFLOW_TYPE = excluded.ORCHESTRATION_WORKFLOW_TYPE,ENV_ID = excluded.ENV_ID,APP_ID = excluded.APP_ID,SERVICE_IDS = excluded.SERVICE_IDS,DEPLOYMENT_TYPE = excluded.DEPLOYMENT_TYPE,CREATED_AT = excluded.CREATED_AT,LAST_UPDATED_AT = excluded.LAST_UPDATED_AT,CREATED_BY = excluded.CREATED_BY,LAST_UPDATED_BY = excluded.LAST_UPDATED_BY;";

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
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement upsertStatement = connection.prepareStatement(upsert_statement)) {
        upsertDataInTimeScaleDB(workflow, connection, upsertStatement);
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
        log.info("Total time =[{}] for workflow:[{}]", System.currentTimeMillis() - startTime, workflow.getUuid());
      }
    }
  }

  private void upsertDataInTimeScaleDB(
      Workflow workflow, Connection connection, PreparedStatement upsertPreparedStatement) throws SQLException {
    upsertPreparedStatement.setString(1, workflow.getUuid());
    upsertPreparedStatement.setString(2, workflow.getName());
    upsertPreparedStatement.setString(3, workflow.getAccountId());
    upsertPreparedStatement.setString(4, workflow.getOrchestration().getOrchestrationWorkflowType().toString());
    upsertPreparedStatement.setString(5, workflow.getEnvId());
    upsertPreparedStatement.setString(6, workflow.getAppId());

    insertArrayData(7, connection, upsertPreparedStatement, workflow.getOrchestration().getServiceIds());

    List<String> deploymentTypes = new LinkedList<>();
    CanaryOrchestrationWorkflow coWorkflow = (CanaryOrchestrationWorkflow) workflow.getOrchestration();
    if (coWorkflow != null && coWorkflow.getWorkflowPhaseIdMap() != null) {
      coWorkflow.getWorkflowPhaseIdMap().values().forEach(workflowPhase -> {
        if (workflowPhase.getDeploymentType() != null) {
          deploymentTypes.add(workflowPhase.getDeploymentType().getDisplayName());
        }
      });
    }
    insertArrayData(8, connection, upsertPreparedStatement, deploymentTypes);

    upsertPreparedStatement.setLong(9, workflow.getCreatedAt());
    upsertPreparedStatement.setLong(10, workflow.getLastUpdatedAt());

    String created_by = null;
    if (workflow.getCreatedBy() != null) {
      created_by = workflow.getCreatedBy().getName();
    }
    upsertPreparedStatement.setString(11, created_by);

    String last_updated_by = null;
    if (workflow.getLastUpdatedBy() != null) {
      last_updated_by = workflow.getLastUpdatedBy().getName();
    }
    upsertPreparedStatement.setString(12, last_updated_by);

    upsertPreparedStatement.execute();
  }
}
