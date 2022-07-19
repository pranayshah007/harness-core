/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.redisConsumer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.plan.execution.PipelineExecutionSummaryKeys;
import io.harness.redisHandler.RedisAbstractHandler;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.Tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExecutionSummaryCDChangeEventHandler extends RedisAbstractHandler {
  @Inject private DSLContext dsl;

  @SneakyThrows
  public Record createRecord(String value, String id) {
    JsonNode node = objectMapper.readTree(value);
    JsonNode moduleInfo = node.get(PipelineExecutionSummaryKeys.moduleInfo);
    if (moduleInfo == null || moduleInfo.get("cd") == null) {
      return null;
    }
    Record record = dsl.newRecord(Tables.PIPELINE_EXECUTION_SUMMARY_CD);
    record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ID, id);
    record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_TYPE, "CD");

    if (node.get(PipelineExecutionSummaryKeys.accountId) != null) {
      record.set(
          Tables.PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID, node.get(PipelineExecutionSummaryKeys.accountId).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.orgIdentifier) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER,
          node.get(PipelineExecutionSummaryKeys.orgIdentifier).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.pipelineIdentifier) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER,
          node.get(PipelineExecutionSummaryKeys.pipelineIdentifier).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.projectIdentifier) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER,
          node.get(PipelineExecutionSummaryKeys.projectIdentifier).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.planExecutionId) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID,
          node.get(PipelineExecutionSummaryKeys.planExecutionId).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.name) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.NAME, node.get(PipelineExecutionSummaryKeys.name).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.status) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STATUS, node.get(PipelineExecutionSummaryKeys.status).asText());
    }

    if (node.get(PipelineExecutionSummaryKeys.startTs) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS, node.get(PipelineExecutionSummaryKeys.startTs).asLong());
    }
    if (node.get(PipelineExecutionSummaryKeys.endTs) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS, node.get(PipelineExecutionSummaryKeys.endTs).asLong());
    }
    if (node.get(PipelineExecutionSummaryKeys.executionTriggerInfo) != null) {
      if (node.get(PipelineExecutionSummaryKeys.executionTriggerInfo).get(PipelineExecutionSummaryKeys.triggerType)
          != null) {
        record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.TRIGGER_TYPE,
            node.get(PipelineExecutionSummaryKeys.executionTriggerInfo)
                .get(PipelineExecutionSummaryKeys.triggerType)
                .asText());
      }
      if (node.get(PipelineExecutionSummaryKeys.executionTriggerInfo).get(PipelineExecutionSummaryKeys.triggeredBy)
              != null
          && node.get(PipelineExecutionSummaryKeys.executionTriggerInfo)
                  .get(PipelineExecutionSummaryKeys.triggeredBy)
                  .get(PipelineExecutionSummaryKeys.executionTriggerInfoIdentifier)
              != null) {
        record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_AUTHOR_ID,
            node.get(PipelineExecutionSummaryKeys.executionTriggerInfo)
                .get(PipelineExecutionSummaryKeys.triggeredBy)
                .get(PipelineExecutionSummaryKeys.executionTriggerInfoIdentifier)
                .asText());
      }
    }

    if (node.get(PipelineExecutionSummaryKeys.moduleInfo).get("ci") != null) {
      JsonNode ciObject = node.get(PipelineExecutionSummaryKeys.moduleInfo).get("ci");
      JsonNode ciExecutionInfo = ciObject.get(PipelineExecutionSummaryKeys.ciExecutionInfoDTO);
      if (ciObject.get(PipelineExecutionSummaryKeys.repoName) != null) {
        record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_REPOSITORY,
            ciObject.get(PipelineExecutionSummaryKeys.repoName).toString());
      }

      if (ciObject.get(PipelineExecutionSummaryKeys.branch) != null) {
        record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_NAME,
            ciObject.get(PipelineExecutionSummaryKeys.branch).toString());
      }

      if (ciExecutionInfo != null) {
        DBObject branch = (DBObject) (ciExecutionInfo.get(PipelineExecutionSummaryKeys.branch));

        HashMap firstCommit;
        String commits = PipelineExecutionSummaryKeys.commits;
        if (branch != null && branch.get(commits) != null && ((List) branch.get(commits)).size() > 0) {
          firstCommit = (HashMap) ((List) branch.get(commits)).get(0);
          if (firstCommit != null) {
            if (firstCommit.get(PipelineExecutionSummaryKeys.commitId) != null) {
              record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_ID,
                  firstCommit.get(PipelineExecutionSummaryKeys.commitId).toString());
            }
            if (firstCommit.get(PipelineExecutionSummaryKeys.commitMessage) != null) {
              record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_MESSAGE,
                  firstCommit.get(PipelineExecutionSummaryKeys.commitMessage).toString());
            }
          }
        } else if (ciExecutionInfo.get(PipelineExecutionSummaryKeys.pullRequest) != null) {
          DBObject pullRequestObject = (DBObject) ciExecutionInfo.get(PipelineExecutionSummaryKeys.pullRequest);

          if (pullRequestObject.get(PipelineExecutionSummaryKeys.sourceBranch) != null) {
            record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.SOURCE_BRANCH,
                pullRequestObject.get(PipelineExecutionSummaryKeys.sourceBranch).toString());
          }

          if (pullRequestObject.get(commits) != null && ((List) pullRequestObject.get(commits)).size() > 0) {
            firstCommit = (HashMap) ((List) pullRequestObject.get(commits)).get(0);
            if (firstCommit != null) {
              if (firstCommit.get(PipelineExecutionSummaryKeys.commitId) != null) {
                record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_ID,
                    firstCommit.get(PipelineExecutionSummaryKeys.commitId).toString());
              }
              if (firstCommit.get(PipelineExecutionSummaryKeys.commitMessage) != null) {
                record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_MESSAGE,
                    firstCommit.get(PipelineExecutionSummaryKeys.commitMessage).toString());
              }
            }
          }
        }
        DBObject author = (DBObject) (ciExecutionInfo.get(PipelineExecutionSummaryKeys.author));
        if (author != null) {
          record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_AUTHOR_ID,
              author.get(PipelineExecutionSummaryKeys.commitId).toString());
          record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.AUTHOR_NAME,
              author.get(PipelineExecutionSummaryKeys.name).toString());
          record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.AUTHOR_AVATAR,
              author.get(PipelineExecutionSummaryKeys.avatar).toString());
        }
        if (ciExecutionInfo.get(PipelineExecutionSummaryKeys.event) != null) {
          record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_EVENT,
              ciExecutionInfo.get(PipelineExecutionSummaryKeys.event).toString());
        }
      }
    }
    return record;
  }

  @Override
  public boolean handleCreateEvent(String id, String value) {
    Record record = createRecord(value, id);
    if (record == null) {
      return true;
    }
    try {
      dsl.insertInto(Tables.PIPELINE_EXECUTION_SUMMARY_CD)
          .set(record)
          .onConflict(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ID, Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS)
          .doNothing()
          .execute();
      log.info("Successfully inserted data for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while inserting data", ex);
      if (DBUtils.isConnectionError(ex)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    try {
      dsl.delete(Tables.PIPELINE_EXECUTION_SUMMARY_CD).where(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ID.eq(id)).execute();
      log.info("Successfully deleted data for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while deleting data", ex);
      if (DBUtils.isConnectionError(ex)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean handleUpdateEvent(String id, String value) {
    Record record = createRecord(value, id);
    if (record == null) {
      return true;
    }
    try {
      dsl.insertInto(Tables.PIPELINE_EXECUTION_SUMMARY_CD)
          .set(record)
          .onConflict(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ID, Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS)
          .doUpdate()
          .set(record)
          .execute();
      log.info("Successfully updated data for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while updating data", ex);
      if (DBUtils.isConnectionError(ex)) {
        return false;
      }
    }
    return true;
  }
}
