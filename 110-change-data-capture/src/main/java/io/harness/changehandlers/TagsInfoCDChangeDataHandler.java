/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Arrays.asList;

import io.harness.changestreamsframework.ChangeEvent;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagsInfoCDChangeDataHandler extends AbstractChangeDataHandler {
  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    if (dbObject == null) {
      return columnValueMapping;
    }

    String parent_identifier = TagsInfoCDChangeDataHandlerHelper.getParentIdentifier(changeEvent, dbObject);
    String parentType = TagsInfoCDChangeDataHandlerHelper.getParentType(changeEvent);
    BasicDBList tags = TagsInfoCDChangeDataHandlerHelper.getTags(changeEvent, dbObject);

    if (parent_identifier == null || isEmpty(tags)) {
      return null;
    }

    BasicDBObject[] tagArray = tags.toArray(new BasicDBObject[tags.size()]);
    String tagString = TagsInfoCDChangeDataHandlerHelper.getTagString(tagArray);

    columnValueMapping.put("id", parent_identifier);
    columnValueMapping.put("parent_type", parentType);
    columnValueMapping.put("tags", tagString);

    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id");
  }
}
