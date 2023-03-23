/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.redisConsumer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventHandler.DebeziumAbstractRedisEventHandler;
import io.harness.pms.pipeline.TriggerType;
import io.harness.pms.plan.execution.PipelineExecutionSummaryKeys;
import io.harness.timescaledb.Tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.Iterator;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.exception.DataAccessException;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExecutionSummaryCDChangeEventHandler extends DebeziumAbstractRedisEventHandler {
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
          != null) {
        JsonNode triggeredByNode =
            node.get(PipelineExecutionSummaryKeys.executionTriggerInfo).get(PipelineExecutionSummaryKeys.triggeredBy);
        if (triggeredByNode.get(PipelineExecutionSummaryKeys.executionTriggerInfoIdentifier) != null) {
          record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MODULEINFO_AUTHOR_ID,
              triggeredByNode.get(PipelineExecutionSummaryKeys.executionTriggerInfoIdentifier).asText());
        }
        if (node.get(PipelineExecutionSummaryKeys.executionTriggerInfo).get(PipelineExecutionSummaryKeys.triggerType)
            != null) {
          if (TriggerType.MANUAL.toString().equals(node.get(PipelineExecutionSummaryKeys.executionTriggerInfo)
                                                       .get(PipelineExecutionSummaryKeys.triggerType)
                                                       .asText())) {
            if (triggeredByNode.get(PipelineExecutionSummaryKeys.uuid) != null) {
              record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.TRIGGERED_BY_ID,
                  triggeredByNode.get(PipelineExecutionSummaryKeys.uuid).asText());
            }
          } else {
            if (triggeredByNode.get(PipelineExecutionSummaryKeys.executionTriggerInfoIdentifier) != null) {
              record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.TRIGGERED_BY_ID,
                  triggeredByNode.get(PipelineExecutionSummaryKeys.executionTriggerInfoIdentifier).asText());
            }
          }
        }
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
        JsonNode branch = ciExecutionInfo.get(PipelineExecutionSummaryKeys.branch);
        JsonNode commitsNode;
        JsonNode firstCommit;
        String commits = PipelineExecutionSummaryKeys.commits;
        if (branch != null && branch.get(commits) != null && (branch.get(commits)).size() > 0) {
          commitsNode = branch.get(commits);
          firstCommit = commitsNode.get("_0");
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
          JsonNode pullRequestObject = ciExecutionInfo.get(PipelineExecutionSummaryKeys.pullRequest);

          if (pullRequestObject.get(PipelineExecutionSummaryKeys.sourceBranch) != null) {
            record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.SOURCE_BRANCH,
                pullRequestObject.get(PipelineExecutionSummaryKeys.sourceBranch).toString());
          }

          if (pullRequestObject.get(commits) != null && pullRequestObject.get(commits).size() > 0) {
            commitsNode = pullRequestObject.get(commits);
            firstCommit = commitsNode.get("_0");
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
        JsonNode author = ciExecutionInfo.get(PipelineExecutionSummaryKeys.author);
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
    if (node.get(PipelineExecutionSummaryKeys.tags) != null
        && node.get(PipelineExecutionSummaryKeys.status).asText().equals("SUCCESS")) {
      JsonNode tagList = node.get(PipelineExecutionSummaryKeys.tags);
      Iterator<Map.Entry<String, JsonNode>> fields = tagList.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (field.getValue() != null && field.getValue().get("key").asText().equals("reverted_execution_id")) {
          setTimeToRestore(
              record, field.getValue().get("value").asText(), node.get(PipelineExecutionSummaryKeys.endTs).asLong());
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
          .doUpdate()
          .set(record)
          .execute();
      log.debug("Successfully inserted data for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while inserting data", ex);
      return false;
    }
    return true;
  }

  @Override
  public boolean handleDeleteEvent(String id) {
    try {
      dsl.delete(Tables.PIPELINE_EXECUTION_SUMMARY_CD).where(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ID.eq(id)).execute();
      log.debug("Successfully deleted data for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while deleting data", ex);
      return false;
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
      log.debug("Successfully updated data for id {}", id);
    } catch (DataAccessException ex) {
      log.error("Caught Exception while updating data", ex);
      return false;
    }
    return true;
  }

  private void setTimeToRestore(Record record, String originalExecutionId, Long currEndTime) {
    try {
      Result<?> result = dsl.select(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS)
                             .from(Tables.PIPELINE_EXECUTION_SUMMARY_CD)
                             .where(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID.eq(originalExecutionId))
                             .and(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STATUS.eq("SUCCESS"))
                             .fetch();
      if (result.size() == 1 && currEndTime != null) {
        log.debug("Successfully found data for id {}", originalExecutionId);
        Record record1 = result.get(0);
        Long timeToRestore = currEndTime - record1.get(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS);
        record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.IS_REVERT_EXECUTION, true);
        record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ORIGINAL_EXECUTION_ID, originalExecutionId);
        record.set(Tables.PIPELINE_EXECUTION_SUMMARY_CD.MEAN_TIME_TO_RESTORE, timeToRestore);
      }
    } catch (DataAccessException ex) {
      log.error("Caught Exception while finding execution for id {}", originalExecutionId, ex);
    }
  }
}
