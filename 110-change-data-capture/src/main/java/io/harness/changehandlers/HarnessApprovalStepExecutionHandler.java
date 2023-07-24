/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.execution.step.approval.harness.HarnessApprovalStepExecutionDetails.HarnessApprovalExecutionActivity.HarnessApprovalExecutionActivityKeys;
import static io.harness.execution.step.approval.harness.HarnessApprovalStepExecutionDetails.HarnessApprovalStepExecutionDetailsKeys;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser.EmbeddedUserKeys;
import io.harness.changehandlers.helper.ChangeHandlerHelper;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.execution.step.StepExecutionEntity.StepExecutionEntityKeys;
import io.harness.steps.StepSpecTypeConstants;

import com.google.inject.Inject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class HarnessApprovalStepExecutionHandler extends AbstractChangeDataHandler {
  private static final String ID = "id";
  private static final String APPROVED_BY_EMAIL = "approved_by_email";
  @Inject ChangeHandlerHelper changeHandlerHelper;

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent, String tableName, String[] fields) {
    log.trace("In TimeScale Change Handler: {}, {}, {}", changeEvent, tableName, fields);
    List<Map<String, String>> columnValueMapping;
    List<String> primaryKeys;

    if (changeEvent == null) {
      return true;
    }

    try {
      primaryKeys = getPrimaryKeys();
      columnValueMapping = getColumnValueMapping(changeEvent);
    } catch (Exception e) {
      log.info(String.format("Not able to parse this event %s: %s", changeEvent, e));
      return false;
    }

    Map<String, String> keyMap = new HashMap<>();
    keyMap.put(ID, changeEvent.getUuid());

    switch (changeEvent.getChangeType()) {
      case INSERT:
      case UPDATE:
        if (isNotEmpty(columnValueMapping)) {
          for (Map<String, String> column : columnValueMapping) {
            dbOperation(updateSQL(tableName, column, keyMap, primaryKeys));
          }
        }
        break;
      case DELETE:
        dbOperation(deleteSQL(tableName, keyMap));
        break;
      default:
        log.info("Change Event Type not Handled: {}", changeEvent.getChangeType());
    }
    return true;
  }

  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    return new HashMap<>();
  }

  public List<Map<String, String>> getColumnValueMapping(ChangeEvent<?> changeEvent) {
    if (isInvalidEvent(changeEvent)) {
      return Collections.emptyList();
    }
    DBObject dbObject = changeEvent.getFullDocument();
    BasicDBObject executionDetails = (BasicDBObject) dbObject.get(StepExecutionEntityKeys.executionDetails);
    BasicDBList approvalActivities =
        (BasicDBList) executionDetails.get(HarnessApprovalStepExecutionDetailsKeys.approvalActivities);
    List<Map<String, String>> nodeMap = new ArrayList<>();
    for (Object approvalActivity : approvalActivities) {
      Map<String, String> columnValueMapping = new HashMap<>();
      BasicDBObject activity = (BasicDBObject) approvalActivity;
      changeHandlerHelper.addKeyValuePairToMapFromDBObject(
          dbObject, columnValueMapping, StepExecutionEntityKeys.stepExecutionId, "id");
      if (activity.get(HarnessApprovalExecutionActivityKeys.approvedAt) != null) {
        columnValueMapping.put("approved_at",
            String.valueOf(((Date) activity.get(HarnessApprovalExecutionActivityKeys.approvedAt)).getTime()));
      }
      changeHandlerHelper.addKeyValuePairToMapFromDBObject(
          activity, columnValueMapping, HarnessApprovalExecutionActivityKeys.approvalAction, "approval_action");
      changeHandlerHelper.addKeyValuePairToMapFromDBObject(
          activity, columnValueMapping, HarnessApprovalExecutionActivityKeys.comments, "comments");
      if (activity.get(HarnessApprovalExecutionActivityKeys.user) != null) {
        BasicDBObject user = (BasicDBObject) activity.get(HarnessApprovalExecutionActivityKeys.user);
        changeHandlerHelper.addKeyValuePairToMapFromDBObject(
            user, columnValueMapping, EmbeddedUserKeys.email, APPROVED_BY_EMAIL);
        changeHandlerHelper.addKeyValuePairToMapFromDBObject(
            user, columnValueMapping, EmbeddedUserKeys.name, "approved_by_name");
      }
      nodeMap.add(columnValueMapping);
    }
    return nodeMap;
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
    if (!StepSpecTypeConstants.HARNESS_APPROVAL.equals(dbObject.get(StepExecutionEntityKeys.stepType).toString())) {
      return true;
    }
    BasicDBObject executionDetails = (BasicDBObject) dbObject.get(StepExecutionEntityKeys.executionDetails);
    if (executionDetails == null) {
      return true;
    }
    BasicDBList approvalActivities =
        (BasicDBList) executionDetails.get(HarnessApprovalStepExecutionDetailsKeys.approvalActivities);
    return approvalActivities == null || approvalActivities.isEmpty();
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList(ID, APPROVED_BY_EMAIL);
  }
}
