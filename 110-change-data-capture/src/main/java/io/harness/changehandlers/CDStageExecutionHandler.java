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
public class CDStageExecutionHandler extends AbstractChangeDataHandler {
  @Inject ChangeHandlerHelper changeHandlerHelper;

  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    if (dbObject == null) {
      return null;
    }

    BasicDBObject executionSummaryDetails =
        (BasicDBObject) dbObject.get(StageExecutionInfoKeys.executionSummaryDetails);
    if (executionSummaryDetails == null) {
      return null;
    }
    BasicDBObject serviceInfo = (BasicDBObject) executionSummaryDetails.get("serviceInfo");
    if (serviceInfo == null) {
      return null;
    }
    BasicDBObject artifactInfo = (BasicDBObject) serviceInfo.get("artifacts");
    BasicDBObject infraExecutionSummary = (BasicDBObject) executionSummaryDetails.get("infraExecutionSummary");
    BasicDBObject failureInfo = (BasicDBObject) executionSummaryDetails.get("failureInfo");
    boolean gitOpsEnabled = (boolean) serviceInfo.get("gitOpsEnabled");
    if (gitOpsEnabled) {
      return null;
    }

    columnValueMapping.put("id", dbObject.get(StageExecutionInfoKeys.stageExecutionId).toString());
    addServiceInfoDetailsToTimescale(serviceInfo, columnValueMapping);
    if (artifactInfo != null) {
      changeHandlerHelper.addKeyValuePairToMapFromDBObject(
          artifactInfo, columnValueMapping, "artifactDisplayName", "artifact_display_name");
    }
    if (infraExecutionSummary != null) {
      addInfraInfoDetailsToTimescale(infraExecutionSummary, columnValueMapping);
    }
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StageExecutionInfoKeys.rollbackDuration, "rollback_duration");
    if (failureInfo != null) {
      changeHandlerHelper.addKeyValuePairToMapFromDBObject(
          failureInfo, columnValueMapping, "errorMessage_", "failure_message");
    }

    return columnValueMapping;
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

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id");
  }

  private void addServiceInfoDetailsToTimescale(DBObject serviceInfo, Map<String, String> columnValueMapping) {
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(serviceInfo, columnValueMapping, "identifier", "service_id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        serviceInfo, columnValueMapping, "displayName", "service_name");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        serviceInfo, columnValueMapping, "deploymentType", "deployment_type");
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
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        infraExecutionSummary, columnValueMapping, "envGroupId", "env_group_id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        infraExecutionSummary, columnValueMapping, "envGroupName", "env_group_name");
  }
}
