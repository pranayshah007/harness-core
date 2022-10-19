/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.*;
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
  @Inject private DeploymentReconServiceImpl deploymentReconService;

  private static final String FIND_DEPLOYMENT_IN_TSDB =
      "SELECT EXECUTIONID,STARTTIME FROM DEPLOYMENT WHERE EXECUTIONID=?";
  protected static final long COOL_DOWN_INTERVAL = 15 * 60 * 1000; /* 15 MINS COOL DOWN INTERVAL */

  private static final String CHECK_MISSING_DATA_QUERY =
      "SELECT COUNT(DISTINCT(EXECUTIONID)) FROM DEPLOYMENT WHERE ACCOUNTID=? AND ((STARTTIME>=? AND STARTTIME<=?) OR (ENDTIME>=? AND ENDTIME<=?)) AND PARENT_EXECUTION IS NULL;";

  private static final String CHECK_DUPLICATE_DATA_QUERY =
      "SELECT DISTINCT(D.EXECUTIONID) FROM DEPLOYMENT D,(SELECT COUNT(EXECUTIONID), EXECUTIONID FROM DEPLOYMENT A WHERE ACCOUNTID = ? AND ((STARTTIME>=? AND STARTTIME<=?) OR (ENDTIME>=? AND ENDTIME<=?)) GROUP BY EXECUTIONID HAVING COUNT(EXECUTIONID) > 1) AS B WHERE D.EXECUTIONID = B.EXECUTIONID;";

  private static final String DELETE_DUPLICATE = "DELETE FROM DEPLOYMENT WHERE EXECUTIONID = ANY (?);";

  private static final String RUNNING_DEPLOYMENTS =
      "SELECT EXECUTIONID,STATUS FROM DEPLOYMENT WHERE ACCOUNTID=? AND STATUS IN ('RUNNING','PAUSED')";

  @Override
  public ReconciliationStatus performReconciliation(String accountId, long durationStartTs, long durationEndTs) {
    return performReconciliationHelper(accountId, durationStartTs, durationEndTs, timeScaleDBService,
        deploymentReconRecordRepository, persistence, persistentLocker, deploymentReconService, utils);
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
        if (isStatusMismatchedInMongoAndTSDB(tsdbRunningWFs, workflowExecution)) {
          log.info("Status mismatch in MongoDB and TSDB for WorkflowExecution: [{}]", workflowExecution.getUuid());
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
    log.info("UPDATING RECORD for accountID:[{}], [{}]", workflowExecution.getAccountId(),
        deploymentTimeSeriesEvent.getTimeSeriesEventInfo());
    try {
      deploymentEventProcessor.processEvent(deploymentTimeSeriesEvent.getTimeSeriesEventInfo());
    } catch (Exception ex) {
      log.error(
          "Failed to process DeploymentTimeSeriesEvent : [{}]", deploymentTimeSeriesEvent.getTimeSeriesEventInfo(), ex);
    }
  }

  public void insertMissingRecords(String accountId, long durationStartTs, long durationEndTs) {
    Query<WorkflowExecution> query =
        persistence.createQuery(WorkflowExecution.class, excludeAuthority)
            .order(Sort.descending(WorkflowExecution.WorkflowExecutionKeys.createdAt))
            .filter(WorkflowExecution.WorkflowExecutionKeys.accountId, accountId)
            .field(WorkflowExecution.WorkflowExecutionKeys.startTs)
            .exists()
            .project(WorkflowExecution.WorkflowExecutionKeys.serviceExecutionSummaries, false);

    addTimeQuery(query, durationStartTs, durationEndTs);

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
          log.info("ADDING MISSING RECORD for accountID:[{}], [{}]", workflowExecution.getAccountId(),
              deploymentTimeSeriesEvent.getTimeSeriesEventInfo());
          deploymentEventProcessor.processEvent(deploymentTimeSeriesEvent.getTimeSeriesEventInfo());
          successfulInsert = true;
        }

      } catch (SQLException ex) {
        totalTries++;
        log.warn("Failed to query workflowExecution from TimescaleDB for workflowExecution:[{}], totalTries:[{}]",
            workflowExecution.getUuid(), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
  }
}
