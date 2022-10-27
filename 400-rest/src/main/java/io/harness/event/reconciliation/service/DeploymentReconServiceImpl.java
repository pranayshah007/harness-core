/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.addTimeQuery;
import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.isStatusMismatchedInMongoAndTSDB;
import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.performReconciliationHelper;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.beans.ExecutionStatus;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecordRepository;
import io.harness.event.timeseries.processor.DeploymentEventProcessor;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.api.DeploymentTimeSeriesEvent;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.search.framework.ExecutionEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Singleton
@Slf4j
public class DeploymentReconServiceImpl implements DeploymentReconService {
  @Inject HPersistence persistence;
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private UsageMetricsEventPublisher usageMetricsEventPublisher;
  @Inject private DeploymentEventProcessor deploymentEventProcessor;
  @Inject private DataFetcherUtils utils;
  @Inject private DeploymentReconRecordRepository deploymentReconRecordRepository;

  private static final String FIND_DEPLOYMENT_IN_TSDB =
      "SELECT EXECUTIONID,STARTTIME FROM DEPLOYMENT WHERE EXECUTIONID=?";

  @Override
  public ReconciliationStatus performReconciliation(
      String accountId, long durationStartTs, long durationEndTs, ExecutionEntity executionEntity) {
    return performReconciliationHelper(accountId, durationStartTs, durationEndTs, timeScaleDBService,
        deploymentReconRecordRepository, persistence, persistentLocker, utils, executionEntity);
  }

  public long getWFExecCountFromMongoDB(String accountId, long durationStartTs, long durationEndTs) {
    long finishedWFExecutionCount = persistence.createQuery(WorkflowExecution.class)
                                        .field(WorkflowExecutionKeys.accountId)
                                        .equal(accountId)
                                        .field(WorkflowExecutionKeys.startTs)
                                        .exists()
                                        .field(WorkflowExecutionKeys.endTs)
                                        .greaterThanOrEq(durationStartTs)
                                        .field(WorkflowExecutionKeys.endTs)
                                        .lessThanOrEq(durationEndTs)
                                        .field(WorkflowExecutionKeys.pipelineExecutionId)
                                        .doesNotExist()
                                        .field(WorkflowExecutionKeys.status)
                                        .in(ExecutionStatus.finalStatuses())
                                        .count();

    long runningWFExecutionCount = persistence.createQuery(WorkflowExecution.class)
                                       .field(WorkflowExecutionKeys.accountId)
                                       .equal(accountId)
                                       .field(WorkflowExecutionKeys.startTs)
                                       .greaterThanOrEq(durationStartTs)
                                       .field(WorkflowExecutionKeys.startTs)
                                       .lessThanOrEq(durationEndTs)
                                       .field(WorkflowExecutionKeys.pipelineExecutionId)
                                       .doesNotExist()
                                       .field(WorkflowExecutionKeys.status)
                                       .in(ExecutionStatus.persistedActiveStatuses())
                                       .count();
    return finishedWFExecutionCount + runningWFExecutionCount;
  }

  public boolean isStatusMismatchedAndUpdated(Map<String, String> tsdbRunningWFs) {
    boolean statusMismatch = false;
    Query<WorkflowExecution> query = persistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                         .field(WorkflowExecutionKeys.uuid)
                                         .hasAnyOf(tsdbRunningWFs.keySet())
                                         .project(WorkflowExecutionKeys.serviceExecutionSummaries, false);

    try (HIterator<WorkflowExecution> iterator = new HIterator<>(query.fetch())) {
      for (WorkflowExecution workflowExecution : iterator) {
        if (isStatusMismatchedInMongoAndTSDB(
                tsdbRunningWFs, workflowExecution.getUuid(), workflowExecution.getStatus().toString())) {
          log.info("Status mismatch in MongoDB and TSDB for WorkflowExecution: [{}] for accountId: [{}]",
              workflowExecution.getUuid(), workflowExecution.getAccountId());
          updateRunningWFsFromTSDB(workflowExecution);
          statusMismatch = true;
        }
      }
    }
    return statusMismatch;
  }

  public void updateRunningWFsFromTSDB(WorkflowExecution workflowExecution) {
    DeploymentTimeSeriesEvent deploymentTimeSeriesEvent = usageMetricsEventPublisher.constructDeploymentTimeSeriesEvent(
        workflowExecution.getAccountId(), workflowExecution);
    log.info("UPDATING RECORD for WorkflowExecution accountID:[{}], [{}]", workflowExecution.getAccountId(),
        deploymentTimeSeriesEvent.getTimeSeriesEventInfo());
    try {
      deploymentEventProcessor.processEvent(deploymentTimeSeriesEvent.getTimeSeriesEventInfo());
    } catch (Exception ex) {
      log.error("Failed to process DeploymentTimeSeriesEvent : [{}] for accountId: [{}]",
          deploymentTimeSeriesEvent.getTimeSeriesEventInfo(), workflowExecution.getAccountId(), ex);
    }
  }

  public void insertMissingRecords(String accountId, long durationStartTs, long durationEndTs) {
    Query<WorkflowExecution> query = persistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                         .order(Sort.descending(WorkflowExecutionKeys.createdAt))
                                         .filter(WorkflowExecutionKeys.accountId, accountId)
                                         .field(WorkflowExecutionKeys.startTs)
                                         .exists()
                                         .project(WorkflowExecutionKeys.serviceExecutionSummaries, false);

    addTimeQuery(query, durationStartTs, durationEndTs, WorkflowExecutionKeys.startTs, WorkflowExecutionKeys.endTs);

    try (HIterator<WorkflowExecution> iterator = new HIterator<>(query.fetch())) {
      for (WorkflowExecution workflowExecution : iterator) {
        checkAndAddIfRequired(workflowExecution);
      }
    }
  }

  private void checkAndAddIfRequired(@NotNull WorkflowExecution workflowExecution) {
    int totalTries = 0;
    boolean successfulInsert = false;
    while (totalTries <= 3 && !successfulInsert) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(FIND_DEPLOYMENT_IN_TSDB)) {
        statement.setString(1, workflowExecution.getUuid());
        resultSet = statement.executeQuery();
        if (resultSet.next()) {
          return;
        } else {
          DeploymentTimeSeriesEvent deploymentTimeSeriesEvent =
              usageMetricsEventPublisher.constructDeploymentTimeSeriesEvent(
                  workflowExecution.getAccountId(), workflowExecution);
          log.info("ADDING MISSING RECORD for WorkflowExecution accountID:[{}], [{}]", workflowExecution.getAccountId(),
              deploymentTimeSeriesEvent.getTimeSeriesEventInfo());
          deploymentEventProcessor.processEvent(deploymentTimeSeriesEvent.getTimeSeriesEventInfo());
          successfulInsert = true;
        }

      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to query WorkflowExecution from TimescaleDB for workflowExecution:[{}] for accountId: [{}], totalTries:[{}]",
            workflowExecution.getUuid(), workflowExecution.getAccountId(), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
  }
}
