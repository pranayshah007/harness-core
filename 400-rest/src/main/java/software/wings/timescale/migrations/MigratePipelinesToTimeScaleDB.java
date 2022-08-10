/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Pipeline.PipelineKeys;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import io.fabric8.utils.Lists;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;

@Slf4j
@Singleton
public class MigratePipelinesToTimeScaleDB {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String insert_statement =
      "INSERT INTO CG_PIPELINES (ID,NAME,ACCOUNT_ID,APP_ID,ENV_IDS,WORKFLOW_IDS,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?,?,?,?)";

  private static final String update_statement =
      "UPDATE CG_PIPELINES SET NAME=?, ACCOUNT_ID=?, APP_ID=?, ENV_IDS=?, WORKFLOW_IDS=?, CREATED_AT=?, LAST_UPDATED_AT=?, CREATED_BY=?, LAST_UPDATED_BY=? WHERE ID=?";

  private static final String query_statement = "SELECT * FROM CG_PIPELINES WHERE ID=?";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions_pipelines = new FindOptions();
      findOptions_pipelines.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<Pipeline> iterator = new HIterator<>(wingsPersistence.createQuery(Pipeline.class, excludeAuthority)
                                                              .field(PipelineKeys.accountId)
                                                              .equal(accountId)
                                                              .fetch(findOptions_pipelines))) {
        while (iterator.hasNext()) {
          Pipeline pipeline = iterator.next();
          saveToTimeScale(pipeline);
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

  public void saveToTimeScale(Pipeline pipeline) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;

      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement queryStatement = connection.prepareStatement(query_statement);
           PreparedStatement updateStatement = connection.prepareStatement(update_statement);
           PreparedStatement insertStatement = connection.prepareStatement(insert_statement)) {
        queryStatement.setString(1, pipeline.getUuid());
        queryResult = queryStatement.executeQuery();
        if (queryResult != null && queryResult.next()) {
          log.info("Pipeline found in the timescaleDB:[{}],updating it", pipeline.getUuid());
          updateDataInTimeScaleDB(pipeline, connection, updateStatement);
        } else {
          log.info("Pipeline not found in the timescaleDB:[{}],inserting it", pipeline.getUuid());
          insertDataInTimeScaleDB(pipeline, connection, insertStatement);
        }
        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to save pipeline,[{}]", pipeline.getUuid(), e);
        } else {
          log.info("Failed to save pipeline,[{}],retryCount=[{}]", pipeline.getUuid(), retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to save pipeline,[{}]", pipeline.getUuid(), e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info("Total time =[{}] for pipeline:[{}]", System.currentTimeMillis() - startTime, pipeline.getUuid());
      }
    }
  }

  private void insertDataInTimeScaleDB(
      Pipeline pipeline, Connection connection, PreparedStatement insertPreparedStatement) throws SQLException {
    insertPreparedStatement.setString(1, pipeline.getUuid());
    insertPreparedStatement.setString(2, pipeline.getName());
    insertPreparedStatement.setString(3, pipeline.getAccountId());
    insertPreparedStatement.setString(4, pipeline.getAppId());

    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    List<String> envIds = new ArrayList<>();
    Map<String, String> isEnvIdPresent = new HashMap<>();
    List<String> workflowIds = new ArrayList<>();
    Map<String, String> isWorkflowIdPresent = new HashMap<>();
    for (PipelineStage pipelineStage : pipelineStages) {
      List<PipelineStageElement> pipelineStageElements = pipelineStage.getPipelineStageElements();
      for (PipelineStageElement pipelineStageElement : pipelineStageElements) {
        if (pipelineStageElement.getProperties().containsKey("envId")) {
          String envId = pipelineStageElement.getProperties().get("envId").toString();
          if (envId != null) {
            if (!isEnvIdPresent.containsKey(envId)) {
              envIds.add(envId);
              isEnvIdPresent.put(envId, envId);
            }
          }
        }

        if (pipelineStageElement.getProperties().containsKey("workflowId")) {
          String workflowId = pipelineStageElement.getProperties().get("workflowId").toString();
          if (workflowId != null) {
            if (!isWorkflowIdPresent.containsKey(workflowId)) {
              workflowIds.add(workflowId);
              isWorkflowIdPresent.put(workflowId, workflowId);
            }
          }
        }
      }
    }
    insertArrayData(5, connection, insertPreparedStatement, envIds);
    insertArrayData(6, connection, insertPreparedStatement, workflowIds);

    insertPreparedStatement.setLong(7, pipeline.getCreatedAt());
    insertPreparedStatement.setLong(8, pipeline.getLastUpdatedAt());

    String created_by = null;
    if (pipeline.getCreatedBy() != null) {
      created_by = pipeline.getCreatedBy().getName();
    }
    String updated_by = null;
    if (pipeline.getLastUpdatedBy() != null) {
      updated_by = pipeline.getLastUpdatedBy().getName();
    }
    insertPreparedStatement.setString(9, created_by);
    insertPreparedStatement.setString(10, updated_by);

    insertPreparedStatement.execute();
  }

  private void updateDataInTimeScaleDB(Pipeline pipeline, Connection connection, PreparedStatement updateStatement)
      throws SQLException {
    updateStatement.setString(1, pipeline.getName());
    updateStatement.setString(2, pipeline.getAccountId());
    updateStatement.setString(3, pipeline.getAppId());

    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    List<String> envIds = new ArrayList<>();
    Map<String, String> isEnvIdPresent = new HashMap<>();
    List<String> workflowIds = new ArrayList<>();
    Map<String, String> isWorkflowIdPresent = new HashMap<>();
    for (PipelineStage pipelineStage : pipelineStages) {
      List<PipelineStageElement> pipelineStageElements = pipelineStage.getPipelineStageElements();
      for (PipelineStageElement pipelineStageElement : pipelineStageElements) {
        if (pipelineStageElement.getProperties().containsKey("envId")) {
          String envId = pipelineStageElement.getProperties().get("envId").toString();
          if (envId != null) {
            if (!isEnvIdPresent.containsKey(envId)) {
              envIds.add(envId);
              isEnvIdPresent.put(envId, envId);
            }
          }
        }

        if (pipelineStageElement.getProperties().containsKey("workflowId")) {
          String workflowId = pipelineStageElement.getProperties().get("workflowId").toString();
          if (workflowId != null) {
            if (!isWorkflowIdPresent.containsKey(workflowId)) {
              workflowIds.add(workflowId);
              isWorkflowIdPresent.put(workflowId, workflowId);
            }
          }
        }
      }
    }
    insertArrayData(4, connection, updateStatement, envIds);
    insertArrayData(5, connection, updateStatement, workflowIds);

    updateStatement.setLong(6, pipeline.getCreatedAt());
    updateStatement.setLong(7, pipeline.getLastUpdatedAt());

    String created_by = null;
    if (pipeline.getCreatedBy() != null) {
      created_by = pipeline.getCreatedBy().getName();
    }
    String updated_by = null;
    if (pipeline.getLastUpdatedBy() != null) {
      updated_by = pipeline.getLastUpdatedBy().getName();
    }
    updateStatement.setString(8, created_by);
    updateStatement.setString(9, updated_by);
    updateStatement.setString(10, pipeline.getUuid());

    updateStatement.execute();
  }

  private void insertArrayData(
      int index, Connection dbConnection, PreparedStatement preparedStatement, List<String> data) throws SQLException {
    if (!Lists.isNullOrEmpty(data)) {
      Array array = dbConnection.createArrayOf("text", data.toArray());
      preparedStatement.setArray(index, array);
    } else {
      preparedStatement.setArray(index, null);
    }
  }
}
