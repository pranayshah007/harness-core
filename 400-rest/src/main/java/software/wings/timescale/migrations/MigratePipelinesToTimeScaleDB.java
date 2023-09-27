/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.timescale.migrations;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.Pipeline.PipelineKeys;
import static software.wings.timescale.migrations.TimescaleEntityMigrationHelper.deleteFromTimescaleDB;
import static software.wings.timescale.migrations.TimescaleEntityMigrationHelper.insertArrayData;

import static java.util.Objects.isNull;

import io.harness.persistence.HIterator;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class MigratePipelinesToTimeScaleDB implements TimeScaleEntityMigrationInterface {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject WingsPersistence wingsPersistence;

  private static final int MAX_RETRY = 5;

  private static final String upsert_statement =
      "INSERT INTO CG_PIPELINES (ID,NAME,ACCOUNT_ID,APP_ID,ENV_IDS,WORKFLOW_IDS,CREATED_AT,LAST_UPDATED_AT,CREATED_BY,LAST_UPDATED_BY) VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(ID) DO UPDATE SET NAME = excluded.NAME,ACCOUNT_ID = excluded.ACCOUNT_ID,APP_ID = excluded.APP_ID,ENV_IDS = excluded.ENV_IDS,WORKFLOW_IDS = excluded.WORKFLOW_IDS,CREATED_AT = excluded.CREATED_AT,LAST_UPDATED_AT = excluded.LAST_UPDATED_AT,CREATED_BY = excluded.CREATED_BY,LAST_UPDATED_BY = excluded.LAST_UPDATED_BY;";

  private static final String TABLE_NAME = "CG_PIPELINES";

  public boolean runTimeScaleMigration(String accountId) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating data to TimeScaleDB for CG_PIPELINES");
      return false;
    }
    int count = 0;
    try {
      FindOptions findOptions_pipelines = new FindOptions();
      findOptions_pipelines.readPreference(ReadPreference.secondaryPreferred());

      try (HIterator<Pipeline> iterator =
               new HIterator<>(wingsPersistence.createAnalyticsQuery(Pipeline.class, excludeAuthority)
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
      log.warn("Failed to complete migration for CG_PIPELINES", e);
      return false;
    } finally {
      log.info("Completed migrating [{}] records for CG_PIPELINES", count);
    }
    return true;
  }

  public void saveToTimeScale(Pipeline pipeline) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    while (!successful && retryCount < MAX_RETRY) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement upsertStatement = connection.prepareStatement(upsert_statement)) {
        upsertDataInTimeScaleDB(pipeline, connection, upsertStatement);
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
        log.info("Total time =[{}] for pipeline:[{}]", System.currentTimeMillis() - startTime, pipeline.getUuid());
      }
    }
  }

  private void upsertDataInTimeScaleDB(
      Pipeline pipeline, Connection connection, PreparedStatement upsertPreparedStatement) throws SQLException {
    upsertPreparedStatement.setString(1, pipeline.getUuid());
    upsertPreparedStatement.setString(2, pipeline.getName());
    upsertPreparedStatement.setString(3, pipeline.getAccountId());
    upsertPreparedStatement.setString(4, pipeline.getAppId());

    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    List<String> envIds = new ArrayList<>();
    Map<String, String> isEnvIdPresent = new HashMap<>();
    List<String> workflowIds = new ArrayList<>();
    Map<String, String> isWorkflowIdPresent = new HashMap<>();
    for (PipelineStage pipelineStage : pipelineStages) {
      List<PipelineStageElement> pipelineStageElements = pipelineStage.getPipelineStageElements();
      for (PipelineStageElement pipelineStageElement : pipelineStageElements) {
        if (pipelineStageElement.getProperties().containsKey("envId")
            && !isNull(pipelineStageElement.getProperties().get("envId"))) {
          String envId = pipelineStageElement.getProperties().get("envId").toString();
          if (envId != null) {
            if (!isEnvIdPresent.containsKey(envId)) {
              envIds.add(envId);
              isEnvIdPresent.put(envId, envId);
            }
          }
        }

        if (pipelineStageElement.getProperties().containsKey("workflowId")
            && !isNull(pipelineStageElement.getProperties().get("workflowId"))) {
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
    insertArrayData(5, connection, upsertPreparedStatement, envIds);
    insertArrayData(6, connection, upsertPreparedStatement, workflowIds);

    upsertPreparedStatement.setLong(7, pipeline.getCreatedAt());
    upsertPreparedStatement.setLong(8, pipeline.getLastUpdatedAt());

    String created_by = null;
    if (pipeline.getCreatedBy() != null) {
      created_by = pipeline.getCreatedBy().getName();
    }
    String updated_by = null;
    if (pipeline.getLastUpdatedBy() != null) {
      updated_by = pipeline.getLastUpdatedBy().getName();
    }
    upsertPreparedStatement.setString(9, created_by);
    upsertPreparedStatement.setString(10, updated_by);

    upsertPreparedStatement.execute();
  }

  public void deleteFromTimescale(String id) {
    deleteFromTimescaleDB(id, timeScaleDBService, MAX_RETRY, TABLE_NAME);
  }

  public String getTimescaleDBClass() {
    return TABLE_NAME;
  }
}
