/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static java.util.Arrays.asList;

import io.harness.changehandlers.helper.ChangeHandlerHelper;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.changestreamsframework.ChangeType;
import io.harness.execution.step.StepExecutionEntity.StepExecutionEntityKeys;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StepExecutionHandler extends AbstractChangeDataHandler {
  @Inject ChangeHandlerHelper changeHandlerHelper;

  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (isInvalidEvent(changeEvent)) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    columnValueMapping.put("id", changeEvent.getUuid());
    addBasicStepLevelDataToColumnValueMap(dbObject, columnValueMapping);

    if (dbObject.get(StepExecutionEntityKeys.startts) != null && dbObject.get(StepExecutionEntityKeys.endts) != null) {
      long startTs = Long.parseLong(dbObject.get(StepExecutionEntityKeys.startts).toString());
      long endTs = Long.parseLong(dbObject.get(StepExecutionEntityKeys.endts).toString());
      long duration = endTs - startTs;
      columnValueMapping.put("duration", Long.toString(duration));
    }
    BasicDBObject failureInfo = (BasicDBObject) dbObject.get(StepExecutionEntityKeys.failureInfo);
    changeHandlerHelper.parseFailureMessageFromFailureInfo(failureInfo, columnValueMapping, "failure_message");
    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id");
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

  private void addBasicStepLevelDataToColumnValueMap(DBObject dbObject, Map<String, String> columnValueMapping) {
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.stepName, "name");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.planExecutionId, "plan_execution_id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.stageExecutionId, "stage_execution_id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.stepExecutionId, "step_execution_id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.stepIdentifier, "step_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.status, "status");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.accountIdentifier, "account_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.orgIdentifier, "org_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.projectIdentifier, "project_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.pipelineIdentifier, "pipeline_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.startts, "start_time");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.endts, "end_time");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.stepType, "type");
  }

  private boolean isInvalidEvent(ChangeEvent<?> changeEvent) {
    if (changeEvent == null) {
      return true;
    }
    DBObject dbObject = changeEvent.getFullDocument();
    if (dbObject == null) {
      return true;
    }
    return dbObject.get(StepExecutionEntityKeys.startts) == null;
  }
}
