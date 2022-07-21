/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.timescaledb.data;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.beans.ExecutionStatus;
import io.harness.migrations.TimeScaleDBDataMigration;
import io.harness.persistence.HIterator;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.artifact.Artifact;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Sort;

/**
 * This will migrate the last 30 days of top level executions to TimeScaleDB
 */
@Slf4j
@Singleton
public class MigrateWorkflowsToTimeScaleDB implements TimeScaleDBDataMigration {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String insert_statement =
      "INSERT INTO DEPLOYMENT (EXECUTIONID,STARTTIME,ENDTIME,ACCOUNTID,APPID,TRIGGERED_BY,TRIGGER_ID,STATUS,SERVICES,WORKFLOWS,CLOUDPROVIDERS,ENVIRONMENTS,PIPELINE,DURATION,ARTIFACTS,ENVTYPES,PARENT_EXECUTION,STAGENAME) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

  private static final String update_statement =
      "UPDATE DEPLOYMENT SET CLOUDPROVIDERS=?, ENVIRONMENTS=?, ARTIFACTS=?, ENVTYPES=? WHERE EXECUTIONID=?";

  private static final String query_statement = "SELECT * FROM DEPLOYMENT WHERE EXECUTIONID=?";

  @Override
  public boolean migrate() {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB");
      return false;
    }
    int count = 0;
    long lastTimeStamp = 0L;
    try {
      FindOptions findOptions = new FindOptions();
      findOptions.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<WorkflowExecution> iterator =
               new HIterator<>(wingsPersistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                   .field(WorkflowExecutionKeys.createdAt)
                                   .greaterThanOrEq(System.currentTimeMillis() - (30 * 24 * 3600 * 1000L))
                                   .field(WorkflowExecutionKeys.pipelineExecutionId)
                                   .doesNotExist()
                                   .field(WorkflowExecutionKeys.startTs)
                                   .exists()
                                   .field(WorkflowExecutionKeys.endTs)
                                   .exists()
                                   .field(WorkflowExecutionKeys.accountId)
                                   .exists()
                                   .field(WorkflowExecutionKeys.status)
                                   .in(ExecutionStatus.finalStatuses())
                                   .order(Sort.descending(WorkflowExecutionKeys.createdAt))
                                   .fetch(findOptions))) {
        while (iterator.hasNext()) {
          WorkflowExecution workflowExecution = iterator.next();
          lastTimeStamp = workflowExecution.getCreatedAt();
          checkWorkflowExecution(workflowExecution);
          count++;
          if (count % 100 == 0) {
            log.info("Completed migrating [{}] records", count);
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to complete migration", e);
      return false;
    } finally {
      log.info("Completed migrating [{}] records, lastTimeStamp=[{}]", count, lastTimeStamp);
    }
    return true;
  }

  private void checkWorkflowExecution(WorkflowExecution workflowExecution) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement queryStatement = connection.prepareStatement(query_statement);
           PreparedStatement updateStatement = connection.prepareStatement(update_statement);
           PreparedStatement insertStatement = connection.prepareStatement(insert_statement)) {
        queryStatement.setString(1, workflowExecution.getUuid());
        queryResult = queryStatement.executeQuery();
        if (queryResult != null && queryResult.next()) {
          log.info("WorkflowExecution found:[{}],updating it", workflowExecution.getUuid());
          updateDataInTimescaleDB(workflowExecution, connection, updateStatement);
        } else {
          log.info("WorkflowExecution not found:[{}],inserting it", workflowExecution.getUuid());
          insertDataInTimescaleDB(workflowExecution, connection, insertStatement);
        }

        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to save workflowExecution,[{}]", workflowExecution.getUuid(), e);
        } else {
          log.info("Failed to save workflowExecution,[{}],retryCount=[{}]", workflowExecution.getUuid(), retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save workflowExecution,[{}]", workflowExecution.getUuid(), e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info("Total time =[{}] for workflowExecution:[{}]", System.currentTimeMillis() - startTime,
            workflowExecution.getUuid());
      }
    }
  }

  private void insertDataInTimescaleDB(WorkflowExecution workflowExecution, Connection dbConnection,
      PreparedStatement insertPreparedStatement) throws SQLException {
    insertPreparedStatement.setString(1, workflowExecution.getUuid());
    insertPreparedStatement.setTimestamp(2, new Timestamp(workflowExecution.getStartTs()), getDefaultCalendar());
    insertPreparedStatement.setTimestamp(3, new Timestamp(workflowExecution.getEndTs()), getDefaultCalendar());
    insertPreparedStatement.setString(4, workflowExecution.getAccountId());
    insertPreparedStatement.setString(5, workflowExecution.getAppId());
    insertPreparedStatement.setString(
        6, workflowExecution.getTriggeredBy() != null ? workflowExecution.getTriggeredBy().getUuid() : null);
    insertPreparedStatement.setString(7, workflowExecution.getDeploymentTriggerId());
    insertPreparedStatement.setString(8, workflowExecution.getStatus().name());

    insertArrayData(9, dbConnection, insertPreparedStatement, workflowExecution.getServiceIds());

    insertArrayData(11, dbConnection, insertPreparedStatement, workflowExecution.getCloudProviderIds());

    if (workflowExecution.getPipelineExecution() != null) {
      insertPreparedStatement.setString(13, workflowExecution.getPipelineExecution().getPipelineId());
      insertArrayData(10, dbConnection, insertPreparedStatement, null);

    } else {
      insertPreparedStatement.setString(13, null);
      insertArrayData(10, dbConnection, insertPreparedStatement, Arrays.asList(workflowExecution.getWorkflowId()));
    }

    insertArrayData(12, dbConnection, insertPreparedStatement, workflowExecution.getEnvIds());

    insertPreparedStatement.setLong(14, workflowExecution.getDuration());

    insertArrayData(15, dbConnection, insertPreparedStatement, getArtifactBuildNumbers(workflowExecution));

    insertArrayData(16, dbConnection, insertPreparedStatement, getEnvTypes(workflowExecution));

    insertPreparedStatement.setString(17, null);

    insertPreparedStatement.setString(18, null);

    insertPreparedStatement.execute();
  }

  private void updateDataInTimescaleDB(WorkflowExecution workflowExecution, Connection connection,
      PreparedStatement updateStatement) throws SQLException {
    insertArrayData(1, connection, updateStatement, workflowExecution.getCloudProviderIds());
    insertArrayData(2, connection, updateStatement, workflowExecution.getEnvIds());
    insertArrayData(3, connection, updateStatement, getArtifactBuildNumbers(workflowExecution));
    insertArrayData(4, connection, updateStatement, getEnvTypes(workflowExecution));
    updateStatement.setString(5, workflowExecution.getUuid());
    updateStatement.execute();
  }

  @NotNull
  private List<String> getArtifactBuildNumbers(WorkflowExecution workflowExecution) {
    List<String> artifacts = new ArrayList<>();
    if (workflowExecution.getArtifacts() != null) {
      artifacts = workflowExecution.getArtifacts().stream().map(Artifact::getBuildNo).collect(Collectors.toList());
    }
    return artifacts;
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

  private List<String> getEnvTypes(WorkflowExecution workflowExecution) {
    if (!isEmpty(workflowExecution.getEnvironments())) {
      return workflowExecution.getEnvironments()
          .stream()
          .map(envSummary -> envSummary.getEnvironmentType().name())
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }

  public Calendar getDefaultCalendar() {
    return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
  }
}
