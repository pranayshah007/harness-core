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
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CI)
@Slf4j
public class PlanExecutionSummaryChangeDataHandler extends AbstractChangeDataHandler {
  public static void commonHandlerTriggerInfo(Map<String, String> columnValueMapping, DBObject dbObject) {
    if ((dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.executionTriggerInfo)) != null) {
      DBObject executionTriggerInfoObject =
          (DBObject) dbObject.get(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.executionTriggerInfo);
      if (executionTriggerInfoObject.get("triggerType") != null) {
        columnValueMapping.put("trigger_type", executionTriggerInfoObject.get("triggerType").toString());
      }
      if (executionTriggerInfoObject.get("triggeredBy") != null) {
        DBObject triggeredByObject = (DBObject) executionTriggerInfoObject.get("triggeredBy");
        if (triggeredByObject.get("identifier") != null) {
          columnValueMapping.put("moduleInfo_author_id", triggeredByObject.get("identifier").toString());
        }
      }
    }
  }

  public static void commonHandlerRepoInfo(Map<String, String> columnValueMapping, DBObject dbObject) {
    DBObject ciObject = (DBObject) (((BasicDBObject) dbObject.get("moduleInfo")).get("ci"));
    DBObject ciExecutionInfo = (DBObject) ciObject.get("ciExecutionInfoDTO");

    if (ciObject.get("repoName") != null) {
      columnValueMapping.put("moduleInfo_repository", ciObject.get("repoName").toString());
    }

    if (ciObject.get("branch") != null) {
      columnValueMapping.put("moduleinfo_branch_name", ciObject.get("branch").toString());
    }

    if (ciObject.get("isPrivateRepo") != null) {
      columnValueMapping.put("moduleinfo_is_private", ciObject.get("isPrivateRepo").toString());
    }

    if (ciExecutionInfo != null) {
      DBObject branch = (DBObject) (ciExecutionInfo.get("branch"));

      HashMap firstCommit = null;
      String commits = "commits";
      if (branch != null && branch.get(commits) != null && ((List) branch.get(commits)).size() > 0) {
        firstCommit = (HashMap) ((List) branch.get(commits)).get(0);
        if (firstCommit != null) {
          if (firstCommit.get("id") != null) {
            columnValueMapping.put("moduleInfo_branch_commit_id", firstCommit.get("id").toString());
          }
          if (firstCommit.get("message") != null) {
            columnValueMapping.put("moduleInfo_branch_commit_message", firstCommit.get("message").toString());
          }
        }
      } else if (ciExecutionInfo.get("pullRequest") != null) {
        DBObject pullRequestObject = (DBObject) ciExecutionInfo.get("pullRequest");

        if (pullRequestObject.get("sourceBranch") != null) {
          columnValueMapping.put("source_branch", pullRequestObject.get("sourceBranch").toString());
        }

        if (pullRequestObject.get("id") != null) {
          columnValueMapping.put("pr", String.valueOf(Long.parseLong(pullRequestObject.get("id").toString())));
        }

        if (pullRequestObject.get(commits) != null && ((List) pullRequestObject.get(commits)).size() > 0) {
          firstCommit = (HashMap) ((List) pullRequestObject.get(commits)).get(0);
          if (firstCommit != null) {
            if (firstCommit.get("id") != null) {
              columnValueMapping.put("moduleInfo_branch_commit_id", firstCommit.get("id").toString());
            }
            if (firstCommit.get("message") != null) {
              columnValueMapping.put("moduleInfo_branch_commit_message", firstCommit.get("message").toString());
            }
          }
        }
      }
      DBObject author = (DBObject) (ciExecutionInfo.get("author"));
      if (author != null) {
        columnValueMapping.put("moduleInfo_author_id", author.get("id").toString());
        columnValueMapping.put("author_name", author.get("name").toString());
        columnValueMapping.put("author_avatar", author.get("avatar").toString());
      }
      if (ciExecutionInfo.get("event") != null) {
        columnValueMapping.put("moduleInfo_event", ciExecutionInfo.get("event").toString());
      }
      if (ciExecutionInfo.get("userSource") != null) {
        columnValueMapping.put("user_source", ciExecutionInfo.get("userSource").toString());
      }
    }
  }

  private void addCommonInfo(Map<String, String> columnValueMapping, DBObject dbObject) {
    DBObject ciObject = (DBObject) (((BasicDBObject) dbObject.get("moduleInfo")).get("ci"));
    if (ciObject.get("ciLicenseType") != null) {
      columnValueMapping.put("ciLicenseType", ciObject.get("ciLicenseType").toString());
    }
    if (ciObject.get("ciEditionType") != null) {
      columnValueMapping.put("ciEditionType", ciObject.get("ciEditionType").toString());
    }
  }

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

    if (dbObject.get("accountId") != null) {
      columnValueMapping.put("accountId", dbObject.get("accountId").toString());
    }
    if (dbObject.get("orgIdentifier") != null) {
      columnValueMapping.put("orgIdentifier", dbObject.get("orgIdentifier").toString());
    }
    if (dbObject.get("projectIdentifier") != null) {
      columnValueMapping.put("projectIdentifier", dbObject.get("projectIdentifier").toString());
    }
    if (dbObject.get("pipelineIdentifier") != null) {
      columnValueMapping.put("pipelineIdentifier", dbObject.get("pipelineIdentifier").toString());
    }
    if (dbObject.get("planExecutionId") != null) {
      columnValueMapping.put("planExecutionId", dbObject.get("planExecutionId").toString());
    }
    if (dbObject.get("name") != null) {
      columnValueMapping.put("name", dbObject.get("name").toString());
    }
    if (dbObject.get("status") != null) {
      columnValueMapping.put("status", dbObject.get("status").toString());
    }

    if (dbObject.get("executionErrorInfo") != null) {
      DBObject executionErrorInfo = (DBObject) dbObject.get("executionErrorInfo");
      if (executionErrorInfo.get("message") != null) {
        columnValueMapping.put("errorMessage", executionErrorInfo.get("message").toString());
      }
    }

    // if moduleInfo is not null
    if (dbObject.get("moduleInfo") != null) {
      if (((BasicDBObject) dbObject.get("moduleInfo")).get("ci") != null) {
        columnValueMapping.put("moduleInfo_type", "CI");
        // TriggerTypeInfo
        commonHandlerTriggerInfo(columnValueMapping, dbObject);

        // repoInfo
        commonHandlerRepoInfo(columnValueMapping, dbObject);

        // commonInfo
        addCommonInfo(columnValueMapping, dbObject);
      } else {
        return null;
      }
    } else {
      // no information mention related to moduleInfo
      return null;
    }
    columnValueMapping.put("startTs", String.valueOf(Long.parseLong(dbObject.get("startTs").toString())));
    if (dbObject.get("endTs") != null) {
      columnValueMapping.put("endTs", String.valueOf(Long.parseLong(dbObject.get("endTs").toString())));
    }

    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id", "startts");
  }
}
