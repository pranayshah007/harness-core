/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.search.entities.pipeline;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.PipelineStage.PipelineStageElement;

import static java.util.Arrays.asList;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.mongo.changestreams.ChangeEvent;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Pipeline;
import software.wings.beans.PipelineStage;
import software.wings.search.SQLOperationHelper;
import software.wings.search.framework.ChangeHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Slf4j
@Singleton
public class PipelineTimescaleChangeDataHandler implements ChangeHandler {
  @Inject private TimeScaleDBService timeScaleDBService;

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent) {
    String tableName = "cg_pipelines";

    switch (changeEvent.getChangeType()) {
      case INSERT:
        dbOperation(SQLOperationHelper.insertSQL(tableName, getColumnValueMapping(changeEvent)));
        break;
      case UPDATE:
        dbOperation(SQLOperationHelper.updateSQL(tableName, getColumnValueMapping(changeEvent),
            Collections.singletonMap("id", changeEvent.getUuid()), getPrimaryKeys()));
        break;
      case DELETE:
        dbOperation(SQLOperationHelper.deleteSQL(tableName, Collections.singletonMap("id", changeEvent.getUuid())));
        break;
      default:
        return false;
    }
    return true;
  }

  public List<String> getPrimaryKeys() {
    return asList("id");
  }

  public boolean dbOperation(String query) {
    boolean successfulOperation = false;
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulOperation && retryCount < 5) {
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

  public Map<String, Object> getColumnValueMapping(ChangeEvent<?> changeEvent) {
    Map<String, Object> columnValueMapping = new HashMap<>();
    Pipeline pipeline = (Pipeline) changeEvent.getFullDocument();

    if (changeEvent == null) {
      return null;
    }

    if (pipeline == null) {
      return columnValueMapping;
    }

    // id
    if (pipeline.getUuid() != null) {
      columnValueMapping.put("id", pipeline.getUuid());
    }

    // name
    if (pipeline.getName() != null) {
      columnValueMapping.put("name", pipeline.getName());
    }

    // account_id
    if (pipeline.getAccountId() != null) {
      columnValueMapping.put("account_id", pipeline.getAccountId());
    }

    // app_id
    if (pipeline.getAppId() != null) {
      columnValueMapping.put("app_id", pipeline.getAppId());
    }

    // created_at
    columnValueMapping.put("created_at", pipeline.getCreatedAt());

    // created_by
    if (pipeline.getCreatedBy() != null) {
      columnValueMapping.put("created_by", pipeline.getCreatedBy().getName());
    }

    // last_updated_by
    if (pipeline.getLastUpdatedBy() != null) {
      columnValueMapping.put("last_updated_by", pipeline.getLastUpdatedBy().getName());
    }

    // last_updated_by
    columnValueMapping.put("last_updated_at", pipeline.getLastUpdatedAt());

    List<PipelineStage> pipelineStages =
        pipeline.getPipelineStages() != null ? pipeline.getPipelineStages() : new LinkedList<>();
    List<String> envIds = new ArrayList<>();
    Map<String, String> isEnvIdPresent = new HashMap<>();
    List<String> workflowIds = new ArrayList<>();
    Map<String, String> isWorkflowIdPresent = new HashMap<>();
    for (PipelineStage pipelineStage : pipelineStages) {
      List<PipelineStageElement> pipelineStageElements = pipelineStage.getPipelineStageElements() != null
          ? pipelineStage.getPipelineStageElements()
          : new LinkedList<>();
      for (PipelineStageElement pipelineStageElement : pipelineStageElements) {
        if (pipelineStageElement.getProperties() != null && pipelineStageElement.getProperties().containsKey("envId")
            && !isNull(pipelineStageElement.getProperties().get("envId"))) {
          String envId = pipelineStageElement.getProperties().get("envId").toString();
          if (envId != null) {
            if (!isEnvIdPresent.containsKey(envId)) {
              envIds.add(envId);
              isEnvIdPresent.put(envId, envId);
            }
          }
        }

        if (pipelineStageElement.getProperties() != null
            && pipelineStageElement.getProperties().containsKey("workflowId")
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

    // created_by
    if (!isEmpty(envIds)) {
      columnValueMapping.put("env_ids", envIds);
    }

    // created_by
    if (!isEmpty(workflowIds)) {
      columnValueMapping.put("workflow_ids", workflowIds);
    }

    return columnValueMapping;
  }
}
