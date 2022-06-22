/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.redisConsumer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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
    JsonNode moduleInfo = node.get("moduleInfo");
    if (moduleInfo == null || moduleInfo.get("cd") == null) {
      return null;
    }
    Record record = dsl.newRecord(Tables.PIPELINE_EXECUTION_SUMMARY_CD);
    record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ID, id);
    record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_TYPE, "CD");

    if (node.get("accountId") != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID, node.get("accountId").asText());
    }
    if (node.get("orgIdentifier") != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER, node.get("orgIdentifier").asText());
    }
    if (node.get("pipelineIdentifier") != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER, node.get("pipelineIdentifier").asText());
    }
    if (node.get("projectIdentifier") != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER, node.get("projectIdentifier").asText());
    }
    if (node.get("planExecutionId") != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID, node.get("planExecutionId").asText());
    }
    if (node.get("name") != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.NAME, node.get("name").asText());
    }
    if (node.get("status") != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STATUS, node.get("status").asText());
    }

    if (node.get("startTs") != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS, node.get("startTs").asLong());
    }
    if (node.get("endTs") != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS, node.get("endTs").asLong());
    }
    if (node.get("executionTriggerInfo") != null) {
      if (node.get("executionTriggerInfo").get("triggerType") != null) {
        record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.TRIGGER_TYPE,
            node.get("executionTriggerInfo").get("triggerType").asText());
      }
      if (node.get("executionTriggerInfo").get("triggeredBy") != null
          && node.get("executionTriggerInfo").get("triggeredBy").get("identifier") != null) {
        record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_AUTHOR_ID,
            node.get("executionTriggerInfo").get("triggeredBy").get("identifier").asText());
      }
    }

    if (node.get("moduleInfo").get("ci") != null) {
      JsonNode ciObject = node.get("moduleInfo").get("ci");
      JsonNode ciExecutionInfo = ciObject.get("ciExecutionInfoDTO");
      if (ciObject.get("repoName") != null) {
        record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_REPOSITORY, ciObject.get("repoName").toString());
      }

      if (ciObject.get("branch") != null) {
        record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_NAME, ciObject.get("branch").toString());
      }

      //            if (ciObject.get("isPrivateRepo") != null) {
      //              fields.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MOd)
      //              columnValueMapping.put("moduleinfo_is_private", ciObject.get("isPrivateRepo").toString());
      //            }

      if (ciExecutionInfo != null) {
        DBObject branch = (DBObject) (ciExecutionInfo.get("branch"));

        HashMap firstCommit;
        String commits = "commits";
        if (branch != null && branch.get(commits) != null && ((List) branch.get(commits)).size() > 0) {
          firstCommit = (HashMap) ((List) branch.get(commits)).get(0);
          if (firstCommit != null) {
            if (firstCommit.get("id") != null) {
              record.set(
                  Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_ID, firstCommit.get("id").toString());
            }
            if (firstCommit.get("message") != null) {
              record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_MESSAGE,
                  firstCommit.get("message").toString());
            }
          }
        } else if (ciExecutionInfo.get("pullRequest") != null) {
          DBObject pullRequestObject = (DBObject) ciExecutionInfo.get("pullRequest");

          if (pullRequestObject.get("sourceBranch") != null) {
            record.set(
                Tables.PIPELINE_EXECUTION_SUMMARY_CD.SOURCE_BRANCH, pullRequestObject.get("sourceBranch").toString());
          }

          //          if (pullRequestObject.get("id") != null) {
          //            fields.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PR)
          //            columnValueMapping.put("pr",
          //            String.valueOf(Long.parseLong(pullRequestObject.get("id").toString())));
          //          }

          if (pullRequestObject.get(commits) != null && ((List) pullRequestObject.get(commits)).size() > 0) {
            firstCommit = (HashMap) ((List) pullRequestObject.get(commits)).get(0);
            if (firstCommit != null) {
              if (firstCommit.get("id") != null) {
                record.set(
                    Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_ID, firstCommit.get("id").toString());
              }
              if (firstCommit.get("message") != null) {
                record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_BRANCH_COMMIT_MESSAGE,
                    firstCommit.get("message").toString());
              }
            }
          }
        }
        DBObject author = (DBObject) (ciExecutionInfo.get("author"));
        if (author != null) {
          record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_AUTHOR_ID, author.get("id").toString());
          record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.AUTHOR_NAME, author.get("name").toString());
          record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.AUTHOR_AVATAR, author.get("avatar").toString());
        }
        if (ciExecutionInfo.get("event") != null) {
          record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_EVENT, ciExecutionInfo.get("event").toString());
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
