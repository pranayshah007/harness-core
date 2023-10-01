/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.changehandlers.helper.ChangeHandlerHelper;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.changestreamsframework.ChangeType;
import io.harness.execution.stage.StageExecutionEntity.StageExecutionEntityKeys;

import com.google.inject.Inject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Slf4j
public class PipelineStageExecutionHandler extends AbstractChangeDataHandler {
  @Inject ChangeHandlerHelper changeHandlerHelper;

  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (isInvalidEvent(changeEvent)) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    addBasicStageLevelDataToColumnValueMap(dbObject, columnValueMapping);

    if (dbObject.get(StageExecutionEntityKeys.startts) != null
        && dbObject.get(StageExecutionEntityKeys.endts) != null) {
      long startTs = Long.parseLong(dbObject.get(StageExecutionEntityKeys.startts).toString());
      long endTs = Long.parseLong(dbObject.get(StageExecutionEntityKeys.endts).toString());
      long duration = endTs - startTs;
      columnValueMapping.put("duration", Long.toString(duration));
    }

    if (dbObject.get(StageExecutionEntityKeys.stageType) != null) {
      if ("CUSTOM_STAGE".equals(dbObject.get(StageExecutionEntityKeys.stageType).toString())) {
        columnValueMapping.put("type", "Custom");
      } else {
        columnValueMapping.put("type", dbObject.get(StageExecutionEntityKeys.stageType).toString());
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
    return dbObject.get(StageExecutionEntityKeys.startts) == null;
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

  private void addBasicStageLevelDataToColumnValueMap(DBObject dbObject, Map<String, String> columnValueMapping) {
    columnValueMapping.put("id", dbObject.get(StageExecutionEntityKeys.stageExecutionId).toString());

    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionEntityKeys.stageName, "name");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionEntityKeys.planExecutionId, "plan_execution_id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionEntityKeys.stageExecutionId, "stage_execution_id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionEntityKeys.status, "status");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionEntityKeys.accountIdentifier, "account_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionEntityKeys.orgIdentifier, "org_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionEntityKeys.projectIdentifier, "project_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionEntityKeys.pipelineIdentifier, "pipeline_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionEntityKeys.startts, "start_time");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionEntityKeys.endts, "end_time");
  }
}
