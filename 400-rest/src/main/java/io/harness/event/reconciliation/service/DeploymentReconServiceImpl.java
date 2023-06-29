/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.addTimeQuery;
import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.getCompletedExecutionsFromTSDB;
import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.isStatusMismatchedInMongoAndTSDB;
import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.performReconciliationHelper;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.beans.ExecutionStatus;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecordRepository;
import io.harness.event.timeseries.processor.DeploymentEventProcessor;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
import io.harness.ff.FeatureFlagService;
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
import dev.morphia.query.CountOptions;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

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
  @Inject private FeatureFlagService featureFlagService;

  private static final String FIND_DEPLOYMENT_IN_TSDB =
      "SELECT EXECUTIONID,STARTTIME FROM DEPLOYMENT WHERE EXECUTIONID=?";

  private static final String FIND_DEPLOYMENT_WITH_STATUS_IN_TSDB =
      "SELECT EXECUTIONID,STATUS FROM DEPLOYMENT WHERE EXECUTIONID=?";

  private static final String HINT_CONCILIATION_ENDTS = "accountId_endTs_status_pipelineExecutionId";
  private static final String HINT_CONCILIATION_STARTTS = "accountId_startTs_status_pipelineExecutionId";

  @Override
  public ReconciliationStatus performReconciliation(
      String accountId, long durationStartTs, long durationEndTs, ExecutionEntity executionEntity) {
    return performReconciliationHelper(accountId, durationStartTs, durationEndTs, timeScaleDBService,
        deploymentReconRecordRepository, persistence, persistentLocker, utils, executionEntity, featureFlagService);
  }

  public long getWFExecCountFromMongoDB(String accountId, long durationStartTs, long durationEndTs) {
    Query<WorkflowExecution> finishedWFExecutionCountQuery = persistence.createQuery(WorkflowExecution.class)
                                                                 .field(WorkflowExecutionKeys.accountId)
                                                                 .equal(accountId)
                                                                 .field(WorkflowExecutionKeys.startTs)
                                                                 .exists()
                                                                 .field(WorkflowExecutionKeys.endTs)
                                                                 .greaterThanOrEq(durationStartTs)
                                                                 .field(WorkflowExecutionKeys.endTs)
                                                                 .lessThanOrEq(durationEndTs)
                                                                 .field(WorkflowExecutionKeys.status)
                                                                 .in(ExecutionStatus.finalStatuses())
                                                                 .field(WorkflowExecutionKeys.pipelineExecutionId)
                                                                 .doesNotExist();

    Query<WorkflowExecution> runningWFExecutionCountQuery = persistence.createQuery(WorkflowExecution.class)
                                                                .field(WorkflowExecutionKeys.accountId)
                                                                .equal(accountId)
                                                                .field(WorkflowExecutionKeys.startTs)
                                                                .greaterThanOrEq(durationStartTs)
                                                                .field(WorkflowExecutionKeys.startTs)
                                                                .lessThanOrEq(durationEndTs)
                                                                .field(WorkflowExecutionKeys.status)
                                                                .in(ExecutionStatus.persistedActiveStatuses())
                                                                .field(WorkflowExecutionKeys.pipelineExecutionId)
                                                                .doesNotExist();

    CountOptions countOptionsEnd = new CountOptions();
    CountOptions countOptionsStart = new CountOptions();
    countOptionsEnd.hint(HINT_CONCILIATION_ENDTS);
    countOptionsStart.hint(HINT_CONCILIATION_STARTTS);

    long finishedWFExecutionCount = finishedWFExecutionCountQuery.count(countOptionsEnd);
    long runningWFExecutionCount = runningWFExecutionCountQuery.count(countOptionsStart);

    return finishedWFExecutionCount + runningWFExecutionCount;
  }

  public boolean isStatusMismatchedAndUpdated(Map<String, String> tsdbRunningWFs) {
    boolean statusMismatch = false;
    FindOptions options = persistence.analyticNodePreferenceOptions();
    Query<WorkflowExecution> query = persistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                         .field(WorkflowExecutionKeys.uuid)
                                         .hasAnyOf(tsdbRunningWFs.keySet())
                                         .project(WorkflowExecutionKeys.serviceExecutionSummaries, false);

    try (HIterator<WorkflowExecution> iterator = new HIterator<>(query.limit(NO_LIMIT).fetch(options))) {
      for (WorkflowExecution workflowExecution : iterator) {
        if (isStatusMismatchedInMongoAndTSDB(
                tsdbRunningWFs, workflowExecution.getUuid(), workflowExecution.getStatus().toString())) {
          log.info("Status mismatch in MongoDB and TSDB for WorkflowExecution: [{}] for accountId: [{}]",
              workflowExecution.getUuid(), workflowExecution.getAccountId());
          updateWFsFromTSDB(workflowExecution);
          statusMismatch = true;
        }
      }
    }
    return statusMismatch;
  }

  @Override
  public boolean isStatusMismatchedAndUpdatedV2(String accountId, long durationStartTs, long durationEndTs,
      String sourceEntityClass, String completedExecutionsQuery, DataFetcherUtils utils) {
    FindOptions options = persistence.analyticNodePreferenceOptions();
    // TODO: add projections in this query when we are fetching only status and executionId and only have an update
    // call. For now have not done that to see if there is a poibility that the create entry itself doesn't exist at
    // this moment.
    Query<WorkflowExecution> query = persistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                         .field(WorkflowExecutionKeys.accountId)
                                         .equal(accountId)
                                         .field(WorkflowExecutionKeys.endTs)
                                         .greaterThanOrEq(durationStartTs)
                                         .field(WorkflowExecutionKeys.endTs)
                                         .lessThanOrEq(durationEndTs)
                                         .field(WorkflowExecutionKeys.status)
                                         .in(ExecutionStatus.finalStatuses())
                                         .project(WorkflowExecutionKeys.serviceExecutionSummaries, false);
    long totalCompletedExecutionsInPrimary = query.count();
    long totalCompletedExecutionsInSecondary = getCompletedExecutionsFromTSDB(accountId, durationStartTs, durationEndTs,
        timeScaleDBService, sourceEntityClass, completedExecutionsQuery, utils);

    // TODO: We can do updates in timescaleDb based on status rather than executionId.
    // We know that status will be limited and if we can construct a map of status to execution ids from mongo then we
    // can fire update query for those executionIds.

    if (totalCompletedExecutionsInSecondary != totalCompletedExecutionsInPrimary) {
      try (HIterator<WorkflowExecution> iterator = new HIterator<>(query.limit(NO_LIMIT).fetch(options))) {
        for (WorkflowExecution workflowExecution : iterator) {
          updateTimeScaleDbStatuses(workflowExecution, timeScaleDBService);
        }
      }
      log.info("Status mismatch job performed for the class: [{}] and accountId: [{}]", sourceEntityClass, accountId);
      return true;
    }
    return false;
  }

  private void updateTimeScaleDbStatuses(WorkflowExecution workflowExecution, TimeScaleDBService timeScaleDBService) {
    int totalTries = 0;
    while (totalTries <= 3) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(FIND_DEPLOYMENT_WITH_STATUS_IN_TSDB)) {
        statement.setString(1, workflowExecution.getUuid());
        resultSet = statement.executeQuery();
        if (resultSet.next()) {
          String secondaryStatus = resultSet.getString(2);
          if (!workflowExecution.getStatus().name().equals(secondaryStatus)) {
            updateWFsFromTSDB(workflowExecution);
            log.info(
                "WorkflowExecution with id: [{}] status did not match in timescale and mongo. The status were as follows: [{}] and [{}]",
                workflowExecution.getUuid(), secondaryStatus, workflowExecution.getStatus().name());
          }
        } else {
          log.warn("Entry for the execution with id: [{}] does not exists in timescale", workflowExecution.getUuid());
        }
        return;
      } catch (SQLException ex) {
        totalTries++;
        log.warn("Failed to retrieve executions from TimeScaleDB for entity: [{}] accountID:[{}], totalTries:[{}]",
            WorkflowExecution.class.getCanonicalName(), ex, totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
  }

  public void updateWFsFromTSDB(WorkflowExecution workflowExecution) {
    DeploymentTimeSeriesEvent deploymentTimeSeriesEvent = usageMetricsEventPublisher.constructDeploymentTimeSeriesEvent(
        workflowExecution.getAccountId(), workflowExecution, Collections.emptyMap());
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
    FindOptions options = persistence.analyticNodePreferenceOptions();
    Query<WorkflowExecution> query = persistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                         .filter(WorkflowExecutionKeys.accountId, accountId)
                                         .field(WorkflowExecutionKeys.startTs)
                                         .exists()
                                         .project(WorkflowExecutionKeys.serviceExecutionSummaries, false);

    addTimeQuery(query, durationStartTs, durationEndTs, WorkflowExecutionKeys.startTs, WorkflowExecutionKeys.endTs);

    try (HIterator<WorkflowExecution> iterator = new HIterator<>(query.limit(NO_LIMIT).fetch(options))) {
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
                  workflowExecution.getAccountId(), workflowExecution, Collections.emptyMap());
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
