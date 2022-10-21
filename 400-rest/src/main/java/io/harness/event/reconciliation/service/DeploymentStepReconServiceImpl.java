package io.harness.event.reconciliation.service;

import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.*;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.beans.ExecutionStatus;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecordRepository;
import io.harness.event.timeseries.processor.DeploymentStepEventProcessor;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.api.DeploymentStepTimeSeriesEvent;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.search.framework.ExecutionEntity;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;

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
public class DeploymentStepReconServiceImpl implements DeploymentReconService {
  @Inject HPersistence persistence;
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private UsageMetricsEventPublisher usageMetricsEventPublisher;
  @Inject private DataFetcherUtils utils;
  @Inject private DeploymentReconRecordRepository deploymentReconRecordRepository;
  @Inject private DeploymentStepEventProcessor deploymentStepEventProcessor;

  private static final String FIND_DEPLOYMENT_STEP_IN_TSDB = "SELECT ID,START_TIME FROM DEPLOYMENT_STEP WHERE ID=?";

  @Override
  public ReconciliationStatus performReconciliation(
      String accountId, long durationStartTs, long durationEndTs, ExecutionEntity executionEntity) {
    return performReconciliationHelper(accountId, durationStartTs, durationEndTs, timeScaleDBService,
        deploymentReconRecordRepository, persistence, persistentLocker, utils, executionEntity);
  }

  @Override
  public long getWFExecCountFromMongoDB(String accountId, long durationStartTs, long durationEndTs) {
    long finishedWFExecutionCount = persistence.createQuery(StateExecutionInstance.class)
                                        .field(StateExecutionInstanceKeys.accountId)
                                        .equal(accountId)
                                        .field(StateExecutionInstanceKeys.startTs)
                                        .exists()
                                        .field(StateExecutionInstanceKeys.endTs)
                                        .greaterThanOrEq(durationStartTs)
                                        .field(StateExecutionInstanceKeys.endTs)
                                        .lessThanOrEq(durationEndTs)
                                        .field(StateExecutionInstanceKeys.status)
                                        .in(ExecutionStatus.finalStatuses())
                                        .count();

    long runningWFExecutionCount = persistence.createQuery(StateExecutionInstance.class)
                                       .field(StateExecutionInstanceKeys.accountId)
                                       .equal(accountId)
                                       .field(StateExecutionInstanceKeys.startTs)
                                       .greaterThanOrEq(durationStartTs)
                                       .field(StateExecutionInstanceKeys.startTs)
                                       .lessThanOrEq(durationEndTs)
                                       .field(StateExecutionInstanceKeys.status)
                                       .in(ExecutionStatus.persistedActiveStatuses())
                                       .count();
    return finishedWFExecutionCount + runningWFExecutionCount;
  }

  @Override
  public boolean isStatusMismatchedAndUpdated(Map<String, String> tsdbRunningWFs) {
    boolean statusMismatch = false;
    Query<StateExecutionInstance> query = persistence.createQuery(StateExecutionInstance.class, excludeAuthority)
                                              .field(StateExecutionInstanceKeys.uuid)
                                              .hasAnyOf(tsdbRunningWFs.keySet());

    try (HIterator<StateExecutionInstance> iterator = new HIterator<>(query.fetch())) {
      for (StateExecutionInstance stateExecutionInstance : iterator) {
        if (isStatusMismatchedInMongoAndTSDB(
                tsdbRunningWFs, stateExecutionInstance.getUuid(), stateExecutionInstance.getStatus().toString())) {
          log.info(
              "Status mismatch in MongoDB and TSDB for StateExecutionInstance: [{}]", stateExecutionInstance.getUuid());
          updateRunningWFsFromTSDB(stateExecutionInstance);
          statusMismatch = true;
        }
      }
    }
    return statusMismatch;
  }

  public void updateRunningWFsFromTSDB(StateExecutionInstance stateExecutionInstance) {
    DeploymentStepTimeSeriesEvent deploymentStepTimeSeriesEvent =
        usageMetricsEventPublisher.constructDeploymentStepTimeSeriesEvent(
            stateExecutionInstance.getAccountId(), stateExecutionInstance);
    log.info("UPDATING RECORD for accountID:[{}], [{}]", stateExecutionInstance.getAccountId(),
        deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo());
    try {
      deploymentStepEventProcessor.processEvent(deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo());
    } catch (Exception ex) {
      log.error("Failed to process DeploymentTimeSeriesEvent : [{}]",
          deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo(), ex);
    }
  }

  @Override
  public void insertMissingRecords(String accountId, long durationStartTs, long durationEndTs) {
    Query<StateExecutionInstance> query = persistence.createQuery(StateExecutionInstance.class, excludeAuthority)
                                              .order(Sort.descending(StateExecutionInstanceKeys.createdAt))
                                              .filter(StateExecutionInstanceKeys.accountId, accountId)
                                              .field(StateExecutionInstanceKeys.startTs)
                                              .exists();

    addTimeQuery(
        query, durationStartTs, durationEndTs, StateExecutionInstanceKeys.startTs, StateExecutionInstanceKeys.endTs);

    try (HIterator<StateExecutionInstance> iterator = new HIterator<>(query.fetch())) {
      for (StateExecutionInstance stateExecutionInstance : iterator) {
        checkAndAddIfRequired(stateExecutionInstance);
      }
    }
  }

  private void checkAndAddIfRequired(@NotNull StateExecutionInstance stateExecutionInstance) {
    int totalTries = 0;
    boolean successfulInsert = false;
    while (totalTries <= 3 && !successfulInsert) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(FIND_DEPLOYMENT_STEP_IN_TSDB)) {
        statement.setString(1, stateExecutionInstance.getUuid());
        resultSet = statement.executeQuery();
        if (resultSet.next()) {
          return;
        } else {
          DeploymentStepTimeSeriesEvent deploymentStepTimeSeriesEvent =
              usageMetricsEventPublisher.constructDeploymentStepTimeSeriesEvent(
                  stateExecutionInstance.getAccountId(), stateExecutionInstance);
          log.info("ADDING MISSING RECORD for accountID:[{}], [{}]", stateExecutionInstance.getAccountId(),
              deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo());
          deploymentStepEventProcessor.processEvent(deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo());
          successfulInsert = true;
        }

      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to query stateExecutionInstance from TimescaleDB for stateExecutionInstance:[{}], totalTries:[{}]",
            stateExecutionInstance.getUuid(), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
  }
}
