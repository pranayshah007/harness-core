/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PlanExecutionSummaryChangeDataHandlerAllStages extends AbstractChangeDataHandler {
  @Inject private TimeScaleDBService timeScaleDBService;
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
    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.accountId) != null) {
      columnValueMapping.put(
          "accountId", dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.accountId).toString());
    }
    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.orgIdentifier) != null) {
      columnValueMapping.put("orgIdentifier",
          dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.orgIdentifier).toString());
    }
    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.projectIdentifier) != null) {
      columnValueMapping.put("projectIdentifier",
          dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.projectIdentifier).toString());
    }
    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.pipelineIdentifier) != null) {
      columnValueMapping.put("pipelineIdentifier",
          dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.pipelineIdentifier).toString());
    }
    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.planExecutionId) != null) {
      columnValueMapping.put("planExecutionId",
          dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.planExecutionId).toString());
    }
    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.name) != null) {
      columnValueMapping.put(
          "name", dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.name).toString());
    }
    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.status) != null) {
      columnValueMapping.put(
          "status", dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.status).toString());
    }
    columnValueMapping.put("startTs",
        String.valueOf(
            Long.parseLong(dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.startTs).toString())));
    if (dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.endTs) != null) {
      columnValueMapping.put("endTs",
          String.valueOf(dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.endTs).toString()));
    }

    if ((dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.executionTriggerInfo)) != null) {
      DBObject executionTriggerInfoObject =
          (DBObject) dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.executionTriggerInfo);
      if (executionTriggerInfoObject.get("triggerType") != null) {
        columnValueMapping.put("trigger_type", executionTriggerInfoObject.get("triggerType").toString());
      }
      if (executionTriggerInfoObject.get("triggeredBy") != null) {
        DBObject triggeredByObject = (DBObject) executionTriggerInfoObject.get("triggeredBy");
        if (triggeredByObject.get("identifier") != null) {
          columnValueMapping.put("author_id", triggeredByObject.get("identifier").toString());
        }
      }
    }
    if (((BasicDBObject) dbObject.get("moduleInfo")).get("ci") != null) {
      DBObject ciObject = (DBObject) (((BasicDBObject) dbObject.get("moduleInfo")).get("ci"));
      DBObject ciExecutionInfo = (DBObject) ciObject.get("ciExecutionInfoDTO");
      if (ciExecutionInfo != null) {
        DBObject author = (DBObject) (ciExecutionInfo.get("author"));
        if (author != null) {
          columnValueMapping.put("author_name", author.get("name").toString());
          columnValueMapping.put("author_avatar", author.get("avatar").toString());
        }
      }
    }
    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id", "startts");
  }
}
