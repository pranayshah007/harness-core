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
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PlanExecutionSummaryCdChangeDataHandler extends AbstractChangeDataHandler {
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
    if (dbObject.get(PlanExecutionSummaryKeys.accountId) != null) {
      columnValueMapping.put("accountId", dbObject.get(PlanExecutionSummaryKeys.accountId).toString());
    }
    if (dbObject.get(PlanExecutionSummaryKeys.orgIdentifier) != null) {
      columnValueMapping.put("orgIdentifier", dbObject.get(PlanExecutionSummaryKeys.orgIdentifier).toString());
    }
    if (dbObject.get(PlanExecutionSummaryKeys.projectIdentifier) != null) {
      columnValueMapping.put("projectIdentifier", dbObject.get(PlanExecutionSummaryKeys.projectIdentifier).toString());
    }
    if (dbObject.get(PlanExecutionSummaryKeys.pipelineIdentifier) != null) {
      columnValueMapping.put(
          "pipelineIdentifier", dbObject.get(PlanExecutionSummaryKeys.pipelineIdentifier).toString());
    }
    if (dbObject.get(PlanExecutionSummaryKeys.planExecutionId) != null) {
      columnValueMapping.put("planExecutionId", dbObject.get(PlanExecutionSummaryKeys.planExecutionId).toString());
    }
    if (dbObject.get(PlanExecutionSummaryKeys.name) != null) {
      columnValueMapping.put("name", dbObject.get(PlanExecutionSummaryKeys.name).toString());
    }
    if (dbObject.get(PlanExecutionSummaryKeys.status) != null) {
      columnValueMapping.put("status", dbObject.get(PlanExecutionSummaryKeys.status).toString());
    }

    if (dbObject.get("moduleInfo") == null) {
      return null;
    }

    // if moduleInfo is not null
    if (((BasicDBObject) dbObject.get("moduleInfo")).get("cd") != null) {
      columnValueMapping.put("moduleInfo_type", "CD");
      // this is a cd deployment pipeline

      // TriggerTypeInfo
      PlanExecutionSummaryChangeDataHandler.commonHandlerTriggerInfo(columnValueMapping, dbObject);
      if ((dbObject.get(PlanExecutionSummaryKeys.executionTriggerInfo)) != null) {
        DBObject executionTriggerInfoObject = (DBObject) dbObject.get(PlanExecutionSummaryKeys.executionTriggerInfo);
        if (executionTriggerInfoObject.get("triggeredBy") != null) {
          DBObject triggeredByObject = (DBObject) executionTriggerInfoObject.get("triggeredBy");
          if (executionTriggerInfoObject.get("triggerType") != null) {
            if (TriggerType.MANUAL.toString().equals(executionTriggerInfoObject.get("triggerType").toString())) {
              columnValueMapping.put("triggered_by_id", triggeredByObject.get("uuid").toString());
            } else {
              columnValueMapping.put("triggered_by_id", triggeredByObject.get("identifier").toString());
            }
          }
        }
      }

      // CI-relatedInfo
      if (((BasicDBObject) dbObject.get("moduleInfo")).get("ci") != null) {
        ciRepoInfo(columnValueMapping, dbObject);
      }
    } else {
      return null;
    }

    columnValueMapping.put(
        "startTs", String.valueOf(Long.parseLong(dbObject.get(PlanExecutionSummaryKeys.startTs).toString())));
    if (dbObject.get(PlanExecutionSummaryKeys.endTs) != null) {
      columnValueMapping.put("endTs", String.valueOf(dbObject.get(PlanExecutionSummaryKeys.endTs).toString()));
    }

    if (dbObject.get("tags") != null && dbObject.get(PlanExecutionSummaryKeys.status).equals("SUCCESS")) {
      List<BasicDBObject> tags = (List<BasicDBObject>) dbObject.get("tags");
      for (BasicDBObject tag : tags) {
        if (tag.get("key").equals("reverted_execution_id")) {
          String originalExecutionId = tag.get("value").toString();
          String query = "SELECT endTs FROM pipeline_execution_summary_cd WHERE planexecutionid = ? AND status = ?";
          Long currEndTime = (Long) dbObject.get(PlanExecutionSummaryKeys.endTs);
          calculateMeanTimeToRestore(query, columnValueMapping, originalExecutionId, currEndTime);
        }
      }
    }
    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id", "startts");
  }

  public static void ciRepoInfo(Map<String, String> columnValueMapping, DBObject dbObject) {
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
    }
  }

  private void calculateMeanTimeToRestore(
      String query, Map<String, String> columnValueMapping, String originalExecutionId, Long currEndTime) {
    boolean successfulOperation = false;
    log.trace("In dbOperation, Query: {}", query);
    List<Long> endTs = new ArrayList<>();
    if (timeScaleDBService.isValid()) {
      int retryCount = 0;
      while (!successfulOperation && retryCount < 5) {
        try (Connection dbConnection = timeScaleDBService.getDBConnection();
             PreparedStatement statement = dbConnection.prepareStatement(query)) {
          statement.setString(1, originalExecutionId);
          statement.setString(2, "SUCCESS");
          ResultSet resultSet = statement.executeQuery();
          while (resultSet.next()) {
            endTs.add(resultSet.getLong(1));
          }
          if (endTs.size() == 1) {
            log.info("Found execution with planExecutionId = {}", originalExecutionId);
            successfulOperation = true;
          } else {
            break;
          }
        } catch (SQLException e) {
          log.error("Failed to fetch execution with planExecutionId = {},retryCount=[{}], Exception: ",
              originalExecutionId, retryCount, e);
          retryCount++;
        }
      }
    } else {
      log.error("TimeScale Down");
    }
    if (successfulOperation) {
      columnValueMapping.put("is_revert_execution", "true");
      columnValueMapping.put("original_execution_id", originalExecutionId);
      if (currEndTime != null) {
        Long timeToRestore = currEndTime - endTs.get(0);
        columnValueMapping.put("mean_time_to_restore", String.valueOf(Long.parseLong(timeToRestore.toString())));
      }
    }
  }
}
