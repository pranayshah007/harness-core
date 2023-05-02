/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static java.util.Arrays.asList;

import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.changehandlers.helper.ChangeHandlerHelper;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.changestreamsframework.ChangeType;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StageExecutionHandler extends AbstractChangeDataHandler {
  @Inject ChangeHandlerHelper changeHandlerHelper;

  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    if (dbObject == null) {
      return columnValueMapping;
    }

    addBasicStageLevelDataToColumnValueMap(dbObject, columnValueMapping);

    if (dbObject.get(StageExecutionInfoKeys.startts) != null && dbObject.get(StageExecutionInfoKeys.endts) != null) {
      long startTs = Long.valueOf(dbObject.get(StageExecutionInfoKeys.startts).toString());
      long endTs = Long.valueOf(dbObject.get(StageExecutionInfoKeys.endts).toString());
      long duration = endTs - startTs;
      columnValueMapping.put("duration", Long.toString(duration));
    }

    BasicDBObject executionSummaryDetails =
        (BasicDBObject) dbObject.get(StageExecutionInfoKeys.executionSummaryDetails);
    BasicDBObject serviceInfo = (BasicDBObject) executionSummaryDetails.get("serviceInfo");

    if (serviceInfo != null) {
      boolean gitOpsEnabled = (boolean) serviceInfo.get("gitOpsEnabled");
      if (gitOpsEnabled) {
        columnValueMapping.put("type", "GITOPS");
      } else {
        columnValueMapping.put("type", "CD");
      }
    }

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

  private void addBasicStageLevelDataToColumnValueMap(DBObject dbObject, Map<String, String> columnValueMapping) {
    columnValueMapping.put("id", dbObject.get(StageExecutionInfoKeys.stageExecutionId).toString());

    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionInfoKeys.stageName, "name");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionInfoKeys.planExecutionId, "plan_execution_id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionInfoKeys.stageExecutionId, "stage_execution_id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionInfoKeys.status, "status");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionInfoKeys.accountIdentifier, "account_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionInfoKeys.orgIdentifier, "org_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionInfoKeys.projectIdentifier, "project_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionInfoKeys.pipelineIdentifier, "pipeline_identifier");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionInfoKeys.startts, "start_time");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionInfoKeys.endts, "end_time");
  }
}
