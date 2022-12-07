/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class SyncPipelineExecutionsWithWrongStatusInTimescale implements NGMigration {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  private static final String FETCH_PLAN_EXECUTION_ID_FROM_PIPELINE_EXECUTION_SUMMARY =
      "SELECT PLANEXECUTIONID, STATUS FROM pipeline_execution_summary_cd  WHERE status IN ('APPROVAL_WAITING', 'APPROVALWAITING', 'WAITSTEPRUNNING', 'INTERVENTION_WAITING', 'INTERVENTIONWAITING', 'RUNNING', 'ASYNCWAITING', 'TASKWAITING', 'TIMEDWAITING', 'PAUSED', 'PAUSING') ORDER BY STARTTS DESC OFFSET ? LIMIT ?";
  private static final String UPDATE_STATEMENT =
      "UPDATE pipeline_execution_summary_cd SET status = ?, endts = ? WHERE planexecutionid = ?";
  private static final String debugLine = "PIPELINE EXECUTION WITH WRONG STATUS: ";

  @Override
  public void migrate() {
    if (timeScaleDBService.isValid()) {
      int offset = 0;
      int batchSize = 500;
      boolean isMigrationCompleted = false;
      int retry = 0;
      final int MAX_RETRY_COUNT = 5;
      try {
        log.info(debugLine + "Migration began to correct the status of non final status execution in timescale");
        while (!isMigrationCompleted && retry < MAX_RETRY_COUNT) {
          Map<String, String> planExecutionIdToStatusMap = new HashMap<>();
          List<String> planExecutionIds = new ArrayList<>();
          try (Connection connection = timeScaleDBService.getDBConnection();
               PreparedStatement fetchStatement =
                   connection.prepareStatement(FETCH_PLAN_EXECUTION_ID_FROM_PIPELINE_EXECUTION_SUMMARY)) {
            fetchStatement.setInt(1, offset);
            fetchStatement.setInt(2, batchSize);
            ResultSet resultSet = fetchStatement.executeQuery();
            while (resultSet.next()) {
              planExecutionIdToStatusMap.put(resultSet.getString(1), resultSet.getString(2));
              planExecutionIds.add(resultSet.getString(1));
            }
          } catch (Exception e) {
            log.error(debugLine + "Failed to fetch pipeline Execution Summary Ids from timescale", e);
            retry++;
          }
          try (Connection connection = timeScaleDBService.getDBConnection()) {
            checkAndSyncStatusInTimeScale(connection, planExecutionIds, planExecutionIdToStatusMap);
          } catch (Exception e) {
            log.error(debugLine + "Exception caught while migration for planExecutionIds: ", e);
          }

          if (planExecutionIdToStatusMap.size() < batchSize) {
            isMigrationCompleted = true;
            continue;
          }
          offset += batchSize;
        }
      } catch (Exception e) {
        log.error(debugLine + "Exception caught while migration", e);
      }
    } else {
      log.info(debugLine + "TIMESCALEDBSERVICE NOT AVAILABLE");
    }
  }

  private void checkAndSyncStatusInTimeScale(
      Connection connection, List<String> planExecutionIds, Map<String, String> planExecutionIdToStatusMap) {
    Criteria criteria =
        Criteria.where(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.planExecutionId).in(planExecutionIds);
    Pageable pageable = PageUtils.getPageRequest(0, 1000, new ArrayList<>());
    List<String> projections = new ArrayList<>();
    projections.add(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.status);
    projections.add(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.endTs);
    List<PipelineExecutionSummaryEntity> pipelineExecutionSummaryEntityList =
        pmsExecutionSummaryRepository.findAllWithRequiredProjection(criteria, pageable, projections);

    try (PreparedStatement updateStatement = connection.prepareStatement(UPDATE_STATEMENT)) {
      boolean update = false;
      for (PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity : pipelineExecutionSummaryEntityList) {
        String status = planExecutionIdToStatusMap.get(pipelineExecutionSummaryEntity.getPlanExecutionId());
        if (!status.equals(pipelineExecutionSummaryEntity.getStatus().name())) {
          update = true;
          Long endTs = 0L;
          if (pipelineExecutionSummaryEntity.getStatus() != null) {
            status = pipelineExecutionSummaryEntity.getStatus().name();
          }
          if (pipelineExecutionSummaryEntity.getEndTs() != null) {
            endTs = pipelineExecutionSummaryEntity.getEndTs();
          }
          updateStatement.setString(1, status);
          updateStatement.setLong(2, endTs);
          updateStatement.setString(3, pipelineExecutionSummaryEntity.getPlanExecutionId());
          updateStatement.addBatch();
        }
      }
      if (update) {
        updateStatement.executeBatch();
      }
    } catch (SQLException e) {
      log.error(debugLine + "Exception occurred while updating value in timescale", e);
    }
  }
}
