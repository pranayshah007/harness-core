/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChangeHandlerHelper {
  public void addKeyValuePairToMapFromDBObject(
      DBObject dbObject, Map<String, String> columnValueMapping, String dbObjectKey, String timescaleObjectKey) {
    if (dbObject.get(dbObjectKey) != null) {
      columnValueMapping.put(timescaleObjectKey, dbObject.get(dbObjectKey).toString());
    }
  }
  public void parseFailureMessageFromFailureInfo(
      BasicDBObject failureInfo, Map<String, String> columnValueMapping, String timescaleObjectKey) {
    if (isNull(failureInfo)) {
      return;
    }
    if (failureInfo.get("errorMessage") != null) {
      addKeyValuePairToMapFromDBObject(failureInfo, columnValueMapping, "errorMessage", timescaleObjectKey);
    } else if (failureInfo.get("failureData") != null) {
      BasicDBList failureData = (BasicDBList) failureInfo.get("failureData");
      if (!isEmpty(failureData) && (((BasicDBObject) failureData.get(0)).get("message") != null)) {
        addKeyValuePairToMapFromDBObject(
            (BasicDBObject) failureData.get(0), columnValueMapping, "message", timescaleObjectKey);
      }
    }
  }
}
