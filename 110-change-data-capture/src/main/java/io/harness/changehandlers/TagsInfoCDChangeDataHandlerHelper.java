/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.execution.stage.StageExecutionEntity;
import io.harness.execution.stage.StageExecutionEntity.StageExecutionEntityKeys;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@UtilityClass
public class TagsInfoCDChangeDataHandlerHelper {
  // REGEX pattern is used to escape the quotes " present in the tag key or value
  // otherwise it creates issues while saving to timescale
  private final String REGEX_MATCH_PATTERN = "(?<!\\\\)\\\"";
  private final String REGEX_REPLACE_STRING = "\\\\\"";

  public String getParentIdentifier(ChangeEvent<?> changeEvent, DBObject dbObject) {
    if (changeEvent.getEntityType() == PipelineEntity.class && dbObject.get(PipelineEntityKeys.identifier) != null) {
      return dbObject.get(PipelineEntityKeys.identifier).toString();
    } else if (changeEvent.getEntityType() == Organization.class && dbObject.get(OrganizationKeys.identifier) != null) {
      return dbObject.get(OrganizationKeys.identifier).toString();
    } else if (changeEvent.getEntityType() == Project.class && dbObject.get(ProjectKeys.identifier) != null) {
      return dbObject.get(ProjectKeys.identifier).toString();
    } else if (
        changeEvent.getEntityType() == ServiceEntity.class && dbObject.get(ServiceEntityKeys.identifier) != null) {
      return dbObject.get(ServiceEntityKeys.identifier).toString();
    } else if (changeEvent.getEntityType() == PipelineExecutionSummaryEntity.class
        && dbObject.get(PlanExecutionSummaryKeys.planExecutionId) != null) {
      return dbObject.get(PlanExecutionSummaryKeys.planExecutionId).toString();
    } else if (changeEvent.getEntityType() == StageExecutionInfo.class
        && dbObject.get(StageExecutionInfoKeys.stageExecutionId) != null) {
      return dbObject.get(StageExecutionInfoKeys.stageExecutionId).toString();
    } else if (changeEvent.getEntityType() == StageExecutionEntity.class
        && dbObject.get(StageExecutionEntityKeys.stageExecutionId) != null) {
      return dbObject.get(StageExecutionEntityKeys.stageExecutionId).toString();
    }
    return null;
  }

  public String getAccountIdentifier(ChangeEvent<?> changeEvent, DBObject dbObject) {
    if (changeEvent.getEntityType() == PipelineEntity.class && dbObject.get(PipelineEntityKeys.accountId) != null) {
      return dbObject.get(PipelineEntityKeys.accountId).toString();
    } else if (
        changeEvent.getEntityType() == Organization.class && dbObject.get(OrganizationKeys.accountIdentifier) != null) {
      return dbObject.get(OrganizationKeys.accountIdentifier).toString();
    } else if (changeEvent.getEntityType() == Project.class && dbObject.get(ProjectKeys.accountIdentifier) != null) {
      return dbObject.get(ProjectKeys.accountIdentifier).toString();
    } else if (
        changeEvent.getEntityType() == ServiceEntity.class && dbObject.get(ServiceEntityKeys.accountId) != null) {
      return dbObject.get(ServiceEntityKeys.accountId).toString();
    } else if (changeEvent.getEntityType() == PipelineExecutionSummaryEntity.class
        && dbObject.get(PlanExecutionSummaryKeys.accountId) != null) {
      return dbObject.get(PlanExecutionSummaryKeys.accountId).toString();
    } else if (changeEvent.getEntityType() == StageExecutionInfo.class
        && dbObject.get(StageExecutionInfoKeys.accountIdentifier) != null) {
      return dbObject.get(StageExecutionInfoKeys.accountIdentifier).toString();
    } else if (changeEvent.getEntityType() == StageExecutionEntity.class
        && dbObject.get(StageExecutionEntityKeys.accountIdentifier) != null) {
      return dbObject.get(StageExecutionEntityKeys.accountIdentifier).toString();
    }
    return null;
  }

  public String getOrgIdentifier(ChangeEvent<?> changeEvent, DBObject dbObject) {
    if (changeEvent.getEntityType() == PipelineEntity.class && dbObject.get(PipelineEntityKeys.orgIdentifier) != null) {
      return dbObject.get(PipelineEntityKeys.orgIdentifier).toString();
    } else if (changeEvent.getEntityType() == Organization.class && dbObject.get(OrganizationKeys.identifier) != null) {
      return dbObject.get(OrganizationKeys.identifier).toString();
    } else if (changeEvent.getEntityType() == Project.class && dbObject.get(ProjectKeys.orgIdentifier) != null) {
      return dbObject.get(ProjectKeys.orgIdentifier).toString();
    } else if (
        changeEvent.getEntityType() == ServiceEntity.class && dbObject.get(ServiceEntityKeys.orgIdentifier) != null) {
      return dbObject.get(ServiceEntityKeys.orgIdentifier).toString();
    } else if (changeEvent.getEntityType() == PipelineExecutionSummaryEntity.class
        && dbObject.get(PlanExecutionSummaryKeys.orgIdentifier) != null) {
      return dbObject.get(PlanExecutionSummaryKeys.orgIdentifier).toString();
    } else if (changeEvent.getEntityType() == StageExecutionInfo.class
        && dbObject.get(StageExecutionInfoKeys.orgIdentifier) != null) {
      return dbObject.get(StageExecutionInfoKeys.orgIdentifier).toString();
    } else if (changeEvent.getEntityType() == StageExecutionEntity.class
        && dbObject.get(StageExecutionEntityKeys.orgIdentifier) != null) {
      return dbObject.get(StageExecutionEntityKeys.orgIdentifier).toString();
    }
    return null;
  }

  public String getProjectIdentifier(ChangeEvent<?> changeEvent, DBObject dbObject) {
    if (changeEvent.getEntityType() == PipelineEntity.class
        && dbObject.get(PipelineEntityKeys.projectIdentifier) != null) {
      return dbObject.get(PipelineEntityKeys.projectIdentifier).toString();
    } else if (changeEvent.getEntityType() == Project.class && dbObject.get(ProjectKeys.identifier) != null) {
      return dbObject.get(ProjectKeys.identifier).toString();
    } else if (changeEvent.getEntityType() == ServiceEntity.class
        && dbObject.get(ServiceEntityKeys.projectIdentifier) != null) {
      return dbObject.get(ServiceEntityKeys.projectIdentifier).toString();
    } else if (changeEvent.getEntityType() == PipelineExecutionSummaryEntity.class
        && dbObject.get(PlanExecutionSummaryKeys.projectIdentifier) != null) {
      return dbObject.get(PlanExecutionSummaryKeys.projectIdentifier).toString();
    } else if (changeEvent.getEntityType() == StageExecutionInfo.class
        && dbObject.get(StageExecutionInfoKeys.projectIdentifier) != null) {
      return dbObject.get(StageExecutionInfoKeys.projectIdentifier).toString();
    } else if (changeEvent.getEntityType() == StageExecutionEntity.class
        && dbObject.get(StageExecutionEntityKeys.projectIdentifier) != null) {
      return dbObject.get(StageExecutionEntityKeys.projectIdentifier).toString();
    }
    return null;
  }

  // returns type of entity based on entity class fetched from changeEvent

  public String getParentType(ChangeEvent<?> changeEvent) {
    if (changeEvent.getEntityType() == PipelineEntity.class) {
      return "PIPELINE";
    } else if (changeEvent.getEntityType() == Organization.class) {
      return "ORGANIZATION";
    } else if (changeEvent.getEntityType() == Project.class) {
      return "PROJECT";
    } else if (changeEvent.getEntityType() == ServiceEntity.class) {
      return "SERVICE";
    } else if (changeEvent.getEntityType() == PipelineExecutionSummaryEntity.class) {
      return "EXECUTION";
    } else if (changeEvent.getEntityType() == StageExecutionInfo.class) {
      return "STAGE_EXECUTION";
    } else if (changeEvent.getEntityType() == StageExecutionEntity.class) {
      return "STAGE_EXECUTION";
    }
    return null;
  }

  /*
  if entityType of changeEvent is one of expected then,
  returns BasicDBList object that contains tag details fetched from dbObject which is document received from changeEvent
  else,
  returns null
   */

  public BasicDBList getTags(ChangeEvent<?> changeEvent, DBObject dbObject) {
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
    } else if (changeEvent.getEntityType() == PipelineExecutionSummaryEntity.class
        && dbObject.get(PlanExecutionSummaryKeys.planExecutionId) != null) {
      if (dbObject.get(PlanExecutionSummaryKeys.tags) != null) {
        return (BasicDBList) dbObject.get(PlanExecutionSummaryKeys.tags);
      }
    } else if (changeEvent.getEntityType() == StageExecutionInfo.class
        && dbObject.get(StageExecutionInfoKeys.stageExecutionId) != null) {
      if (dbObject.get(StageExecutionInfoKeys.tags) != null) {
        return (BasicDBList) dbObject.get(StageExecutionInfoKeys.tags);
      }
    } else if (changeEvent.getEntityType() == StageExecutionEntity.class
        && dbObject.get(StageExecutionEntityKeys.stageExecutionId) != null) {
      if (dbObject.get(StageExecutionEntityKeys.tags) != null) {
        return (BasicDBList) dbObject.get(StageExecutionEntityKeys.tags);
      }
    }
    return null;
  }

  public String getStageExecutionTags(BasicDBList tagsList) {
    String[] tagArray = tagsList.toArray(new String[tagsList.size()]);
    StringBuilder tagString = new StringBuilder("{");
    for (String tag : tagArray) {
      tag = tag.replaceAll(REGEX_MATCH_PATTERN, REGEX_REPLACE_STRING);
      tagString.append(tag);
      tagString.append(',');
    }
    tagString = new StringBuilder(tagString.subSequence(0, tagString.length() - 1));
    tagString.append('}');
    return tagString.toString();
  }

  /*
  input - array of BasicDBObject containing tag details
  example - [{"key":"a", "value":"b"},{"key":"c", "value":"d"}]

  output - returns string with comma separated tag key-value pair
  example - {a:b,c:d}
   */

  public String getTagString(BasicDBObject[] tagArray) {
    StringBuilder tagString = new StringBuilder("{");
    for (BasicDBObject tag : tagArray) {
      if (tag.get(NGTagKeys.key) == null) {
        continue;
      }

      String tagKey = tag.get(NGTagKeys.key).toString();
      String tagValue = tag.get(NGTagKeys.value) == null ? "" : tag.get(NGTagKeys.value).toString();
      tagKey = tagKey.replaceAll(REGEX_MATCH_PATTERN, REGEX_REPLACE_STRING);
      tagValue = tagValue.replaceAll(REGEX_MATCH_PATTERN, REGEX_REPLACE_STRING);
      tagString.append(tagKey);
      tagString.append(':');
      tagString.append(tagValue);
      tagString.append(',');
    }
    tagString = new StringBuilder(tagString.subSequence(0, tagString.length() - 1));
    tagString.append('}');
    return tagString.toString();
  }

  public List<String> getTagsList(BasicDBObject[] tagArray) {
    List<String> tags = new ArrayList<>();
    for (BasicDBObject tag : tagArray) {
      if (tag.get(NGTagKeys.key) == null) {
        continue;
      }

      String tagKey = tag.get(NGTagKeys.key).toString();
      String tagValue = tag.get(NGTagKeys.value) == null ? "" : tag.get(NGTagKeys.value).toString();
      tagKey = tagKey.replaceAll(REGEX_MATCH_PATTERN, REGEX_REPLACE_STRING);
      tagValue = tagValue.replaceAll(REGEX_MATCH_PATTERN, REGEX_REPLACE_STRING);
      String tagString = tagKey + ':' + tagValue;
      tags.add(tagString);
    }
    return tags;
  }
}
