/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import io.harness.changestreamsframework.ChangeEvent;
import io.harness.entities.subscriptions.ChaosExperiments.ChaosExperimentsKeys;

import com.mongodb.DBObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChaosExperimentsTagsChangeDataHandler extends AbstractChangeDataHandler {
  public static final String PARENT_ID = "parent_id";
  public static final String TAG = "tag";

  @Override
  public List<Map<String, String>> getColumnValueMappings(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }

    DBObject dbObject = changeEvent.getFullDocument();
    if (dbObject == null) {
      return null;
    }

    Object tagsValue = dbObject.get(ChaosExperimentsKeys.tags.toString());
    if (tagsValue == null) {
      return null;
    }

    if (!(tagsValue instanceof List)) {
      return null;
    }

    List<?> tagStrings = (List<?>) tagsValue;

    return tagStrings.stream()
        .map(Object::toString)
        .map(s -> new HashMap<>(Map.of(PARENT_ID, changeEvent.getUuid(), TAG, s)))
        .distinct()
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    throw new UnsupportedOperationException("This method should not be called, use getColumnValueMappings instead");
  }

  @Override
  public List<String> getPrimaryKeys() {
    return List.of(PARENT_ID, TAG);
  }

  @Override
  public Map<String, String> getColumnValueMappingsForWhereClause(ChangeEvent<?> changeEvent) {
    return Collections.singletonMap(PARENT_ID, changeEvent.getUuid());
  }

  @Override
  protected void beforeAllChangesHook(
      ChangeEvent<?> changeEvent, String tableName, List<Map<String, String>> columnValueMappings) {
    String id = escapeSql(changeEvent.getUuid());
    String query = String.format("DELETE FROM %s WHERE %s='%s';", tableName, PARENT_ID, id);
    dbOperation(query);
  }
}
