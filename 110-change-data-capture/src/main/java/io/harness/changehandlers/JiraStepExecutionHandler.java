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
import io.harness.execution.step.StepExecutionEntity.StepExecutionEntityKeys;
import io.harness.execution.step.jira.create.JiraCreateStepExecutionDetails.JiraCreateStepExecutionDetailsKeys;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@Slf4j
public class JiraStepExecutionHandler extends AbstractChangeDataHandler {
  private static final Set<String> JIRA_STEPS_TO_UPDATE = Sets.newHashSet(
      StepSpecTypeConstants.JIRA_APPROVAL, StepSpecTypeConstants.JIRA_UPDATE, StepSpecTypeConstants.JIRA_CREATE);
  @Inject ChangeHandlerHelper changeHandlerHelper;

  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (isInvalidEvent(changeEvent)) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.stepExecutionId, "id");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        dbObject, columnValueMapping, StepExecutionEntityKeys.stepType, "type");
    BasicDBObject executionDetails = (BasicDBObject) dbObject.get(StepExecutionEntityKeys.executionDetails);
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        executionDetails, columnValueMapping, JiraCreateStepExecutionDetailsKeys.url, "jira_url");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        executionDetails, columnValueMapping, JiraCreateStepExecutionDetailsKeys.issueType, "issue_type");
    changeHandlerHelper.addKeyValuePairToMapFromDBObject(
        executionDetails, columnValueMapping, JiraCreateStepExecutionDetailsKeys.ticketStatus, "ticket_status");

    return columnValueMapping;
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

  private boolean isInvalidEvent(ChangeEvent<?> changeEvent) {
    if (changeEvent == null) {
      return true;
    }
    DBObject dbObject = changeEvent.getFullDocument();
    if (dbObject == null) {
      return true;
    }
    if (dbObject.get(StepExecutionEntityKeys.stepType) == null) {
      return true;
    }
    if (!JIRA_STEPS_TO_UPDATE.contains(dbObject.get(StepExecutionEntityKeys.stepType).toString())) {
      return true;
    }
    BasicDBObject executionDetails = (BasicDBObject) dbObject.get(StepExecutionEntityKeys.executionDetails);
    return executionDetails == null;
  }
}
