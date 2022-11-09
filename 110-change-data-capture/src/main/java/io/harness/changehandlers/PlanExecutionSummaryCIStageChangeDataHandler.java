/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class PlanExecutionSummaryCIStageChangeDataHandler extends AbstractChangeDataHandler {
  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();
    columnValueMapping.put("id", changeEvent.getUuid());

    if (dbObject == null) {
      return null;
    }

    if (dbObject.get("accountId") != null) {
      columnValueMapping.put("accountidentifier", dbObject.get("accountId").toString());
    }
    if (dbObject.get("orgIdentifier") != null) {
      columnValueMapping.put("orgidentifier", dbObject.get("orgIdentifier").toString());
    }
    if (dbObject.get("projectIdentifier") != null) {
      columnValueMapping.put("projectidentifier", dbObject.get("projectIdentifier").toString());
    }
    if (dbObject.get("pipelineIdentifier") != null) {
      columnValueMapping.put("pipelineidentifier", dbObject.get("pipelineIdentifier").toString());
    }
    if (dbObject.get("planExecutionId") != null) {
      columnValueMapping.put("planexecutionid", dbObject.get("planExecutionId").toString());
    }
    if (dbObject.get("name") != null) {
      columnValueMapping.put("pipelinename", dbObject.get("name").toString());
    }

    return getCIModuleInfoMapping(columnValueMapping, dbObject);
  }

  private Map<String, String> getCIModuleInfoMapping(Map<String, String> columnValueMapping, DBObject dbObject) {
    if (dbObject.get("moduleInfo") != null) {
      BasicDBObject moduleInfo = (BasicDBObject) dbObject.get("moduleInfo");
      if (moduleInfo != null && moduleInfo.get("ci") != null) {
        BasicDBObject ciModule = (BasicDBObject) moduleInfo.get("ci");
        if (ciModule != null && ciModule.get("ciPipelineStageModuleInfo") != null) {
          BasicDBObject ciPipelineStageModuleInfo = (BasicDBObject) ciModule.get("ciPipelineStageModuleInfo");
          if (ciPipelineStageModuleInfo.get("startTs") != null
              && ciPipelineStageModuleInfo.get("stageExecutionId") != null) {
            columnValueMapping.put(
                "startts", String.valueOf(Long.parseLong(ciPipelineStageModuleInfo.get("startTs").toString())));

            columnValueMapping.put("stageexecutionid", ciPipelineStageModuleInfo.get("stageExecutionId").toString());

            if (ciPipelineStageModuleInfo.get("stageId") != null) {
              columnValueMapping.put("stageidentifier", ciPipelineStageModuleInfo.get("stageId").toString());
            }
            if (ciPipelineStageModuleInfo.get("stageName") != null) {
              columnValueMapping.put("stagename", ciPipelineStageModuleInfo.get("stageName").toString());
            }
            if (ciPipelineStageModuleInfo.get("osType") != null) {
              columnValueMapping.put("ostype", ciPipelineStageModuleInfo.get("osType").toString());
            }
            if (ciPipelineStageModuleInfo.get("infraType") != null) {
              columnValueMapping.put("infratype", ciPipelineStageModuleInfo.get("infraType").toString());
            }
            if (ciPipelineStageModuleInfo.get("osArch") != null) {
              columnValueMapping.put("osarch", ciPipelineStageModuleInfo.get("osArch").toString());
            }
            if (ciPipelineStageModuleInfo.get("osArch") != null) {
              columnValueMapping.put("osarch", ciPipelineStageModuleInfo.get("osArch").toString());
            }
            if (ciPipelineStageModuleInfo.get("cpuTime") != null) {
              columnValueMapping.put(
                  "cputime", String.valueOf(Long.parseLong(ciPipelineStageModuleInfo.get("cpuTime").toString())));
            }
            if (ciPipelineStageModuleInfo.get("stageBuildTime") != null) {
              columnValueMapping.put("stagebuildtime",
                  String.valueOf(Long.parseLong(ciPipelineStageModuleInfo.get("stageBuildTime").toString())));
            }
            if (ciPipelineStageModuleInfo.get("buildMultiplier") != null) {
              columnValueMapping.put("buildmultiplier",
                  String.valueOf(Double.parseDouble(ciPipelineStageModuleInfo.get("buildMultiplier").toString())));
            }
            return columnValueMapping;
          }
        }
      }
    }
    return null;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id", "stageexecutionid", "startts");
  }
}
