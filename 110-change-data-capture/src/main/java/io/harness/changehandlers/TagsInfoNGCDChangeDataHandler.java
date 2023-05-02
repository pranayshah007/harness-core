/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Arrays.asList;

import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.changestreamsframework.ChangeEvent;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TagsInfoNGCDChangeDataHandler extends AbstractChangeDataHandler {
  private static final String TAGS = "tags";
  private static final String PARENT_TYPE = "parent_type";
  private static final String ID = "id";

  @Override
  public boolean handleChange(ChangeEvent<?> changeEvent, String tableName, String[] fields) {
    log.trace("In TimeScale Change Handler: {}, {}, {}", changeEvent, tableName, fields);
    Map<String, String> columnValueMapping = null;
    List<String> primaryKeys = null;

    if (changeEvent == null) {
      return true;
    }

    try {
      primaryKeys = getPrimaryKeys();
      columnValueMapping = getColumnValueMapping(changeEvent, fields);
    } catch (Exception e) {
      log.info(String.format("Not able to parse this event %s", changeEvent));
      return false;
    }

    if (!tableName.equals("pipeline_execution_summary_ci") && columnValueMapping != null) {
      columnValueMapping.remove("moduleinfo_is_private");
      columnValueMapping.remove("pr");
    }

    Map<String, String> keyMap = new HashMap<>();
    keyMap.put(PARENT_TYPE, TagsInfoCDChangeDataHandlerHelper.getParentType(changeEvent));
    keyMap.put(ID, changeEvent.getUuid());

    switch (changeEvent.getChangeType()) {
      case INSERT:
        if (isNotEmpty(columnValueMapping)) {
          dbOperation(updateSQL(tableName, columnValueMapping, keyMap, primaryKeys));
        }
        break;
      case UPDATE:
        if (isNotEmpty(columnValueMapping)) {
          dbOperation(updateSQL(tableName, columnValueMapping, keyMap, primaryKeys));
        } else if (columnValueMapping != null) {
          dbOperation(deleteSQL(tableName, keyMap));
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
    if (changeEvent == null) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    if (dbObject == null) {
      return null;
    }

    String id = changeEvent.getUuid();
    String accountId = TagsInfoCDChangeDataHandlerHelper.getAccountIdentifier(changeEvent, dbObject);
    String orgId = TagsInfoCDChangeDataHandlerHelper.getOrgIdentifier(changeEvent, dbObject);
    String projectId = TagsInfoCDChangeDataHandlerHelper.getProjectIdentifier(changeEvent, dbObject);
    String parentIdentifier = TagsInfoCDChangeDataHandlerHelper.getParentIdentifier(changeEvent, dbObject);
    String parentType = TagsInfoCDChangeDataHandlerHelper.getParentType(changeEvent);
    if (id == null || accountId == null || parentType == null || parentIdentifier == null) {
      return null;
    }
    String tagString;
    BasicDBList tags = TagsInfoCDChangeDataHandlerHelper.getTags(changeEvent, dbObject);
    if (isEmpty(tags)) {
      return null;
    }
    if (changeEvent.getEntityType().equals(StageExecutionInfo.class)) {
      tagString = TagsInfoCDChangeDataHandlerHelper.getStageExecutionTags(tags);
    } else {
      BasicDBObject[] tagArray = tags.toArray(new BasicDBObject[tags.size()]);
      tagString = TagsInfoCDChangeDataHandlerHelper.getTagString(tagArray);
    }

    columnValueMapping.put(ID, id);
    columnValueMapping.put("account_id", accountId);
    columnValueMapping.put("org_id", orgId);
    columnValueMapping.put("project_id", projectId);
    columnValueMapping.put(PARENT_TYPE, parentType);
    columnValueMapping.put("parent_id", parentIdentifier);
    columnValueMapping.put(TAGS, tagString);

    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList(ID, PARENT_TYPE);
  }
}
