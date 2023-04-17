/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;

import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(PIPELINE)
public class ServicesChangeDataHandler extends AbstractChangeDataHandler {
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

    if (dbObject.get(ServiceEntityKeys.accountId) != null) {
      columnValueMapping.put("account_id", dbObject.get(ServiceEntityKeys.accountId).toString());
    }

    if (dbObject.get(ServiceEntityKeys.orgIdentifier) != null) {
      columnValueMapping.put("org_identifier", dbObject.get(ServiceEntityKeys.orgIdentifier).toString());
    }

    if (dbObject.get(ServiceEntityKeys.projectIdentifier) != null) {
      columnValueMapping.put("project_identifier", dbObject.get(ServiceEntityKeys.projectIdentifier).toString());
    }

    if (dbObject.get(ServiceEntityKeys.identifier) != null) {
      columnValueMapping.put("identifier", dbObject.get(ServiceEntityKeys.identifier).toString());
    }

    if (dbObject.get(ServiceEntityKeys.name) != null) {
      columnValueMapping.put("name", dbObject.get(ServiceEntityKeys.name).toString());
    }

    if (dbObject.get(ServiceEntityKeys.deleted) != null) {
      columnValueMapping.put("deleted", dbObject.get(ServiceEntityKeys.deleted).toString());
    }

    if (dbObject.get(ServiceEntityKeys.createdAt) != null) {
      columnValueMapping.put("created_at", dbObject.get(ServiceEntityKeys.createdAt).toString());
    }

    if (dbObject.get(ServiceEntityKeys.lastModifiedAt) != null) {
      columnValueMapping.put("last_modified_at", dbObject.get(ServiceEntityKeys.lastModifiedAt).toString());
    }

    return columnValueMapping;
  }

  public boolean shouldDelete() {
    return false;
  }

  @Override
  public Map<String, String> getColumnValueMappingForDelete() {
    Map<String, String> columnValueMapping = new HashMap<>();
    columnValueMapping.put("deleted", "true");
    columnValueMapping.put("deleted_at", String.valueOf(System.currentTimeMillis()));
    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id");
  }
}
