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
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;

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

    String parent_identifier = getParentIdentifier(changeEvent, dbObject);
    String parentType = getParentType(changeEvent, dbObject);
    BasicDBList tags = getTags(changeEvent, dbObject);

    if (parent_identifier == null || isEmpty(tags)) {
      return null;
    }

    BasicDBObject[] tagArray = tags.toArray(new BasicDBObject[tags.size()]);
    String tagString = getTagString(tagArray);

    columnValueMapping.put("id", parent_identifier);
    columnValueMapping.put("parent_type", parentType);
    columnValueMapping.put("tags", tagString);

    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id");
  }

  private String getParentIdentifier(ChangeEvent<?> changeEvent, DBObject dbObject) {
    if (changeEvent.getEntityType() == PipelineEntity.class && dbObject.get(PipelineEntityKeys.identifier) != null) {
      return dbObject.get(PipelineEntityKeys.identifier).toString();
    } else if (changeEvent.getEntityType() == Organization.class && dbObject.get(OrganizationKeys.identifier) != null) {
      return dbObject.get(OrganizationKeys.identifier).toString();
    } else if (changeEvent.getEntityType() == Project.class && dbObject.get(ProjectKeys.identifier) != null) {
      return dbObject.get(ProjectKeys.identifier).toString();
    } else if (
        changeEvent.getEntityType() == ServiceEntity.class && dbObject.get(ServiceEntityKeys.identifier) != null) {
      return dbObject.get(ServiceEntityKeys.identifier).toString();
    }
    return null;
  }

  private String getParentType(ChangeEvent<?> changeEvent, DBObject dbObject) {
    if (changeEvent.getEntityType() == PipelineEntity.class && dbObject.get(PipelineEntityKeys.identifier) != null) {
      return "PIPELINE";
    } else if (changeEvent.getEntityType() == Organization.class && dbObject.get(OrganizationKeys.identifier) != null) {
      return "ORGANIZATION";
    } else if (changeEvent.getEntityType() == Project.class && dbObject.get(ProjectKeys.identifier) != null) {
      return "PROJECT";
    } else if (
        changeEvent.getEntityType() == ServiceEntity.class && dbObject.get(ServiceEntityKeys.identifier) != null) {
      return "SERVICE";
    }
    return null;
  }

  private BasicDBList getTags(ChangeEvent<?> changeEvent, DBObject dbObject) {
    if (changeEvent.getEntityType() == PipelineEntity.class && dbObject.get(PipelineEntityKeys.identifier) != null) {
      if (dbObject.get(PipelineEntityKeys.tags) != null) {
        return (BasicDBList) dbObject.get(PipelineEntityKeys.tags);
      }
    } else if (changeEvent.getEntityType() == Organization.class && dbObject.get(OrganizationKeys.identifier) != null) {
      if (dbObject.get(OrganizationKeys.tags) != null) {
        return (BasicDBList) dbObject.get(OrganizationKeys.tags);
      }
    } else if (changeEvent.getEntityType() == Project.class && dbObject.get(ProjectKeys.identifier) != null) {
      if (dbObject.get(ProjectKeys.tags) != null) {
        return (BasicDBList) dbObject.get(ProjectKeys.tags);
      }
    } else if (
        changeEvent.getEntityType() == ServiceEntity.class && dbObject.get(ServiceEntityKeys.identifier) != null) {
      if (dbObject.get(ServiceEntityKeys.tags) != null) {
        return (BasicDBList) dbObject.get(ServiceEntityKeys.tags);
      }
    }
    return null;
  }

  private String getTagString(BasicDBObject[] tagArray) {
    StringBuilder tagString = new StringBuilder("{");
    for (BasicDBObject tag : tagArray) {
      String tagKey = tag.get(NGTagKeys.key).toString();
      String tagValue = tag.get(NGTagKeys.value).toString();
      tagValue = tagValue == null ? "" : tagValue;
      tagString.append(tagKey);
      tagString.append(':');
      tagString.append(tagValue);
      tagString.append(',');
    }
    tagString = new StringBuilder(tagString.subSequence(0, tagString.length() - 1));
    tagString.append('}');
    return tagString.toString();
  }
}
