/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.changehandlers.helper.ChangeHandlerHelper;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.changestreamsframework.ChangeType;
import io.harness.execution.stage.StageExecutionEntity.StageExecutionEntityKeys;

import com.google.inject.Inject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

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

    columnValueMapping.put("id", dbObject.get(StageExecutionEntityKeys.stageExecutionId).toString());
    BasicDBObject failureInfo = (BasicDBObject) dbObject.get(StageExecutionEntityKeys.failureInfo);
    if (failureInfo.get("errorMessage") != null) {
      changeHandlerHelper.addKeyValuePairToMapFromDBObject(
          failureInfo, columnValueMapping, "errorMessage", "failure_message");
    } else if (failureInfo.get("failureData") != null) {
      BasicDBList failureData = (BasicDBList) failureInfo.get("failureData");
      if (!isEmpty(failureData)) {
        if (((BasicDBObject) failureData.get(0)).get("message") != null) {
          changeHandlerHelper.addKeyValuePairToMapFromDBObject(
              (BasicDBObject) failureData.get(0), columnValueMapping, "message", "failure_message");
        }
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
    return dbObject.get(StageExecutionEntityKeys.failureInfo) == null;
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
}
