/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.connector.entities.Connector.ConnectorKeys;

import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(CDP)
public class ConnectorsChangeDataHandler extends AbstractChangeDataHandler {
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

    if (dbObject.get(ConnectorKeys.accountIdentifier) != null) {
      columnValueMapping.put("account_id", dbObject.get(ConnectorKeys.accountIdentifier).toString());
    }

    if (dbObject.get(ConnectorKeys.orgIdentifier) != null) {
      columnValueMapping.put("org_identifier", dbObject.get(ConnectorKeys.orgIdentifier).toString());
    }

    if (dbObject.get(ConnectorKeys.projectIdentifier) != null) {
      columnValueMapping.put("project_identifier", dbObject.get(ConnectorKeys.projectIdentifier).toString());
    }

    if (dbObject.get(ConnectorKeys.identifier) != null) {
      columnValueMapping.put("identifier", dbObject.get(ConnectorKeys.identifier).toString());
    }

    if (dbObject.get(ConnectorKeys.name) != null) {
      columnValueMapping.put("name", dbObject.get(ConnectorKeys.name).toString());
    }

    if (dbObject.get(ConnectorKeys.scope) != null) {
      columnValueMapping.put("scope", dbObject.get(ConnectorKeys.scope).toString());
    }

    if (dbObject.get(ConnectorKeys.createdAt) != null) {
      columnValueMapping.put("created_at", dbObject.get(ConnectorKeys.createdAt).toString());
    }

    if (dbObject.get(ConnectorKeys.createdBy) != null) {
      DBObject createdByObject = (DBObject) dbObject.get(ConnectorKeys.createdBy);
      if (createdByObject.get("name") != null) {
        columnValueMapping.put("created_by", createdByObject.get("name").toString());
      }
    }

    if (dbObject.get(ConnectorKeys.lastModifiedAt) != null) {
      columnValueMapping.put("last_modified_at", dbObject.get(ConnectorKeys.lastModifiedAt).toString());
    }

    if (dbObject.get(ConnectorKeys.lastUpdatedBy) != null) {
      DBObject lastUpdatedByObject = (DBObject) dbObject.get(ConnectorKeys.lastUpdatedBy);
      if (lastUpdatedByObject.get("name") != null) {
        columnValueMapping.put("last_updated_by", lastUpdatedByObject.get("name").toString());
      }
    }

    if (dbObject.get(ConnectorKeys.type) != null) {
      columnValueMapping.put("type", dbObject.get(ConnectorKeys.type).toString());
    }

    if (dbObject.get(ConnectorKeys.categories) != null) {
      columnValueMapping.put("categories", dbObject.get(ConnectorKeys.categories).toString());
    }

    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id");
  }
}
