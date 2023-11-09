/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import io.harness.changestreamsframework.ChangeEvent;

import com.mongodb.DBObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class BasicEntityToColumnsChangeDataHandler<T extends Enum<T>> extends AbstractChangeDataHandler {
  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }

    DBObject dbObject = changeEvent.getFullDocument();
    if (dbObject == null) {
      return null;
    }

    Map<String, String> mapping = mapEventToColumnByKeys(dbObject, getKeysToMap(), getExcludedKeys());

    Map<String, String> columnValueMapping = new HashMap<>();
    columnValueMapping.put("id", changeEvent.getUuid());
    columnValueMapping.putAll(mapping);

    return columnValueMapping;
  }

  private Map<String, String> mapEventToColumnByKeys(DBObject dbObject, T[] keys, List<T> excludeKeys) {
    return Arrays.stream(keys)
        .filter(key -> !excludeKeys.contains(key))
        .map(Enum::toString)
        .filter(key -> dbObject.get(key) != null)
        .collect(Collectors.toMap(key -> key, key -> dbObject.get(key).toString()));
  }

  protected abstract T[] getKeysToMap();

  protected abstract List<T> getExcludedKeys();
}
