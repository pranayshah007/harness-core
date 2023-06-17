/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static java.util.Arrays.asList;

import io.harness.changestreamsframework.ChangeEvent;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;

import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PipelinesChangeDataHandler extends AbstractChangeDataHandler {
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

    if (dbObject.get(PipelineEntityKeys.accountId) != null) {
      columnValueMapping.put("account_id", dbObject.get(PipelineEntityKeys.accountId).toString());
    }

    if (dbObject.get(PipelineEntityKeys.orgIdentifier) != null) {
      columnValueMapping.put("org_identifier", dbObject.get(PipelineEntityKeys.orgIdentifier).toString());
    }

    if (dbObject.get(PipelineEntityKeys.projectIdentifier) != null) {
      columnValueMapping.put("project_identifier", dbObject.get(PipelineEntityKeys.projectIdentifier).toString());
    }

    if (dbObject.get(PipelineEntityKeys.identifier) != null) {
      columnValueMapping.put("identifier", dbObject.get(PipelineEntityKeys.identifier).toString());
    }

    if (dbObject.get(PipelineEntityKeys.name) != null) {
      columnValueMapping.put("name", dbObject.get(PipelineEntityKeys.name).toString());
    }

    if (dbObject.get(PipelineEntityKeys.deleted) != null) {
      columnValueMapping.put("deleted", dbObject.get(PipelineEntityKeys.deleted).toString());
    }

    if (dbObject.get(PipelineEntityKeys.createdAt) != null) {
      columnValueMapping.put("created_at", dbObject.get(PipelineEntityKeys.createdAt).toString());
    }

    if (dbObject.get(PipelineEntityKeys.lastUpdatedAt) != null) {
      columnValueMapping.put("last_updated_at", dbObject.get(PipelineEntityKeys.lastUpdatedAt).toString());
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
