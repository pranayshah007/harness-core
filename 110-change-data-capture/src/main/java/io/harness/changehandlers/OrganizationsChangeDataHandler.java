/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static java.util.Arrays.asList;

import io.harness.changestreamsframework.ChangeEvent;
import io.harness.ng.core.entities.Organization.OrganizationKeys;

import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrganizationsChangeDataHandler extends AbstractChangeDataHandler {
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
    if (dbObject.get(OrganizationKeys.identifier) != null) {
      columnValueMapping.put("identifier", dbObject.get(OrganizationKeys.identifier).toString());
    }

    if (dbObject.get(OrganizationKeys.name) != null) {
      columnValueMapping.put("name", dbObject.get(OrganizationKeys.name).toString());
    }

    if (dbObject.get(OrganizationKeys.deleted) != null) {
      columnValueMapping.put("deleted", dbObject.get(OrganizationKeys.deleted).toString());
    }

    if (dbObject.get(OrganizationKeys.harnessManaged) != null) {
      columnValueMapping.put("harness_managed", dbObject.get(OrganizationKeys.harnessManaged).toString());
    }

    if (dbObject.get(OrganizationKeys.lastModifiedAt) != null) {
      columnValueMapping.put("last_modified_at", dbObject.get(OrganizationKeys.lastModifiedAt).toString());
    }

    if (dbObject.get(OrganizationKeys.accountIdentifier) != null) {
      columnValueMapping.put("account_identifier", dbObject.get(OrganizationKeys.accountIdentifier).toString());
    }

    if (dbObject.get(OrganizationKeys.createdAt) != null) {
      columnValueMapping.put("created_at", dbObject.get(OrganizationKeys.createdAt).toString());
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
