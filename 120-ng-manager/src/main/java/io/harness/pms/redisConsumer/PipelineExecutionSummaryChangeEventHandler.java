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
import io.harness.timescaledb.Tables;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.exception.DataAccessException;

@Slf4j
@OwnedBy(HarnessTeam.SPG)
public class PipelineExecutionSummaryChangeEventHandler extends RedisAbstractHandler {
  @Inject private DSLContext dsl;

  @SneakyThrows
  public Record createRecord(String value, String id) {
    JsonNode node = objectMapper.readTree(value);
    JsonNode moduleInfo = node.get(PipelineExecutionSummaryKeys.moduleInfo);
    if (moduleInfo == null) {
      return null;
    }
    Record record = dsl.newRecord(Tables.PIPELINE_EXECUTION_SUMMARY);
    record.set(Tables.PIPELINE_EXECUTION_SUMMARY.ID, id);

    if (node.get(PipelineExecutionSummaryKeys.accountId) != null) {
      record.set(
          Tables.PIPELINE_EXECUTION_SUMMARY.ACCOUNTID, node.get(PipelineExecutionSummaryKeys.accountId).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.orgIdentifier) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY.ORGIDENTIFIER,
          node.get(PipelineExecutionSummaryKeys.orgIdentifier).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.pipelineIdentifier) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY.PIPELINEIDENTIFIER,
          node.get(PipelineExecutionSummaryKeys.pipelineIdentifier).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.projectIdentifier) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY.PROJECTIDENTIFIER,
          node.get(PipelineExecutionSummaryKeys.projectIdentifier).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.planExecutionId) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY.PLANEXECUTIONID,
          node.get(PipelineExecutionSummaryKeys.planExecutionId).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.name) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY.NAME, node.get(PipelineExecutionSummaryKeys.name).asText());
    }
    if (node.get(PipelineExecutionSummaryKeys.startTs) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY.STARTTS, node.get(PipelineExecutionSummaryKeys.startTs).asLong());
    }
    if (node.get(PipelineExecutionSummaryKeys.endTs) != null) {
      record.set(Tables.PIPELINE_EXECUTION_SUMMARY.ENDTS, node.get(PipelineExecutionSummaryKeys.endTs).asLong());
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
      dsl.insertInto(Tables.PIPELINE_EXECUTION_SUMMARY)
          .set(record)
          .onConflict(Tables.PIPELINE_EXECUTION_SUMMARY.ID, Tables.PIPELINE_EXECUTION_SUMMARY.STARTTS)
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
      dsl.delete(Tables.PIPELINE_EXECUTION_SUMMARY).where(Tables.PIPELINE_EXECUTION_SUMMARY.ID.eq(id)).execute();
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
      dsl.insertInto(Tables.PIPELINE_EXECUTION_SUMMARY)
          .set(record)
          .onConflict(Tables.PIPELINE_EXECUTION_SUMMARY.ID, Tables.PIPELINE_EXECUTION_SUMMARY.STARTTS)
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
}
