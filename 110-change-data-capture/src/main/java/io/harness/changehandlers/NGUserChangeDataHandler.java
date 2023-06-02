/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static java.util.Arrays.asList;

import io.harness.changestreamsframework.ChangeEvent;
import io.harness.ng.core.user.entities.UserMetadata.UserMetadataKeys;

import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NGUserChangeDataHandler extends AbstractChangeDataHandler {
  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    columnValueMapping.put("id", changeEvent.getUuid());

    if (dbObject == null) {
      return columnValueMapping;
    }

    if (dbObject.get(UserMetadataKeys.name) != null) {
      columnValueMapping.put("name", dbObject.get(UserMetadataKeys.name).toString());
    }

    if (dbObject.get(UserMetadataKeys.createdAt) != null) {
      columnValueMapping.put("created_at", dbObject.get(UserMetadataKeys.createdAt).toString());
    }

    if (dbObject.get(UserMetadataKeys.lastModifiedAt) != null) {
      columnValueMapping.put("last_modified_at", dbObject.get(UserMetadataKeys.lastModifiedAt).toString());
    }

    if (dbObject.get(UserMetadataKeys.email) != null) {
      columnValueMapping.put("email", dbObject.get(UserMetadataKeys.email).toString());
    }

    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id");
  }
}
