/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static io.harness.changehandlers.constants.StageExecutionHandlerConstants.CUSTOM_STAGE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.changehandlers.helper.ChangeHandlerHelper;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.changestreamsframework.ChangeType;
import io.harness.execution.stage.StageExecutionEntity;
import io.harness.execution.stage.StageExecutionEntity.StageExecutionEntityKeys;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Slf4j
public class CustomStageExecutionHandler extends AbstractChangeDataHandler {
  @Inject ChangeHandlerHelper changeHandlerHelper;

  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (isInvalidEvent(changeEvent)) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    String stageExecutionId = getStageExecutionId(changeEvent, dbObject);
    if (stageExecutionId == null) {
      return null;
    }
    columnValueMapping.put("id", stageExecutionId);

    if (changeEvent.getEntityType() == StageExecutionEntity.class) {
      BasicDBObject failureInfo = (BasicDBObject) dbObject.get(StageExecutionEntityKeys.failureInfo);
      changeHandlerHelper.parseFailureMessageFromFailureInfo(failureInfo, columnValueMapping, "failure_message");
    } else if (changeEvent.getEntityType() == StageExecutionInfo.class) {
      BasicDBObject executionSummaryDetails =
          (BasicDBObject) dbObject.get(StageExecutionInfoKeys.executionSummaryDetails);
      if (executionSummaryDetails == null) {
        return null;
      }

      BasicDBObject infraExecutionSummary = (BasicDBObject) executionSummaryDetails.get("infraExecutionSummary");
      if (infraExecutionSummary != null) {
        addInfraInfoDetailsToTimescale(infraExecutionSummary, columnValueMapping);
      }

      BasicDBObject failureInfo = (BasicDBObject) executionSummaryDetails.get("failureInfo");
      if (failureInfo != null) {
        changeHandlerHelper.addKeyValuePairToMapFromDBObject(
            failureInfo, columnValueMapping, "errorMessage_", "failure_message");
      }
    }

    return columnValueMapping;
  }

  private boolean isInvalidEvent(ChangeEvent<?> changeEvent) {
    if (changeEvent == null) {
      return true;
    }
    DBObject dbObject = changeEvent.getFullDocument();
    if (dbObject == null) {
      return true;
    }
    if ((changeEvent.getEntityType() == StageExecutionEntity.class)
        && (dbObject.get(StageExecutionEntityKeys.failureInfo) == null)) {
      return true;
    }

    String stageType = null;
    if ((changeEvent.getEntityType() == StageExecutionEntity.class)
        && (dbObject.get(StageExecutionEntityKeys.stageType) != null)) {
      stageType = dbObject.get(StageExecutionEntityKeys.stageType).toString();
    } else if ((changeEvent.getEntityType() == StageExecutionInfo.class)
        && (dbObject.get(StageExecutionInfoKeys.stageType) != null)) {
      stageType = dbObject.get(StageExecutionInfoKeys.stageType).toString();
    }
    if (!(CUSTOM_STAGE.equals(stageType))) {
      return true;
    }
    return false;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return List.of("id");
  }

  @Override
  public boolean changeEventHandled(ChangeType changeType) {
    switch (changeType) {
      case INSERT:
      case UPDATE:
        return true;
      default:
        log.info("Change Event Type not Handled: {}", changeType);
        return false;
    }
  }

  private String getStageExecutionId(ChangeEvent<?> changeEvent, DBObject dbObject) {
    if (changeEvent.getEntityType() == StageExecutionInfo.class
        && dbObject.get(StageExecutionInfoKeys.stageExecutionId) != null) {
      return dbObject.get(StageExecutionInfoKeys.stageExecutionId).toString();
    } else if (changeEvent.getEntityType() == StageExecutionEntity.class
        && dbObject.get(StageExecutionEntityKeys.stageExecutionId) != null) {
      return dbObject.get(StageExecutionEntityKeys.stageExecutionId).toString();
    }
    return null;
  }

  private void addInfraInfoDetailsToTimescale(DBObject infraExecutionSummary, Map<String, String> columnValueMapping) {
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        infraExecutionSummary, columnValueMapping, "identifier", "env_id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(infraExecutionSummary, columnValueMapping, "name", "env_name");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(infraExecutionSummary, columnValueMapping, "type", "env_type");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        infraExecutionSummary, columnValueMapping, "infrastructureIdentifier", "infra_id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        infraExecutionSummary, columnValueMapping, "infrastructureName", "infra_name");
  }
}
