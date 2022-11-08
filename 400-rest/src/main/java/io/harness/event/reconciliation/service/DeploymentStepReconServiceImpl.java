/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.addTimeQuery;
import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.isStatusMismatchedInMongoAndTSDB;
import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.performReconciliationHelper;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.threading.Morpheus.sleep;

import io.harness.beans.ExecutionStatus;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecordRepository;
import io.harness.event.timeseries.processor.DeploymentStepEventProcessor;
import io.harness.event.timeseries.processor.StepEventProcessor;
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
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
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
                                        .field(StateExecutionInstanceKeys.stateType)
                                        .notIn(StepEventProcessor.STATE_TYPES)
                                        .count();

    long runningWFExecutionCount = persistence.createQuery(StateExecutionInstance.class)
                                       .field(StateExecutionInstanceKeys.accountId)
                                       .equal(accountId)
                                       .field(StateExecutionInstanceKeys.startTs)
                                       .exists()
                                       .field(StateExecutionInstanceKeys.startTs)
                                       .greaterThanOrEq(durationStartTs)
                                       .field(StateExecutionInstanceKeys.startTs)
                                       .lessThanOrEq(durationEndTs)
                                       .field(StateExecutionInstanceKeys.status)
                                       .in(ExecutionStatus.persistedActiveStatuses())
                                       .field(StateExecutionInstanceKeys.stateType)
                                       .notIn(StepEventProcessor.STATE_TYPES)
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
          log.info("Status mismatch in MongoDB and TSDB for StateExecutionInstance: [{}] accountId: [{}]",
              stateExecutionInstance.getUuid(), stateExecutionInstance.getAccountId());
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
    log.info("UPDATING RECORD for StateExecutionInstance accountID:[{}], [{}]", stateExecutionInstance.getAccountId(),
        deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo());
    try {
      deploymentStepEventProcessor.processEvent(deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo());
    } catch (Exception ex) {
      log.error("Failed to process DeploymentStepTimeSeriesEvent : [{}] for accountId: [{}]",
          deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo(), stateExecutionInstance.getAccountId(), ex);
    }
  }

  @Override
  public void insertMissingRecords(String accountId, long durationStartTs, long durationEndTs) {
    Query<StateExecutionInstance> query = persistence.createQuery(StateExecutionInstance.class, excludeAuthority)
                                              .order(Sort.descending(StateExecutionInstanceKeys.createdAt))
                                              .filter(StateExecutionInstanceKeys.accountId, accountId)
                                              .field(StateExecutionInstanceKeys.startTs)
                                              .exists()
                                              .field(StateExecutionInstanceKeys.stateType)
                                              .notIn(StepEventProcessor.STATE_TYPES);

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
          log.info("ADDING MISSING RECORD for StateExecutionInstance accountID:[{}], [{}]",
              stateExecutionInstance.getAccountId(), deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo());
          deploymentStepEventProcessor.processEvent(deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo());
          successfulInsert = true;
        }

      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to query StateExecutionInstance from TimescaleDB for stateExecutionInstance:[{}] for accountId: [{}], totalTries:[{}]",
            stateExecutionInstance.getUuid(), stateExecutionInstance.getAccountId(), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
  }

  public void migrateDataMongoToTimescale(String accountId, int batchSize, Long intervalStart, Long intervalEnd) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating Deployment Step data to TimeScaleDB");
    }

    final DBCollection collection = persistence.getCollection(StateExecutionInstance.class);
    BasicDBObject objectsToMigrate =
        new BasicDBObject(StateExecutionInstanceKeys.accountId, accountId)
            .append(StateExecutionInstanceKeys.startTs, new BasicDBObject("$ne", null))
            .append(StateExecutionInstanceKeys.startTs, new BasicDBObject("$gte", intervalStart))
            .append(StateExecutionInstanceKeys.startTs, new BasicDBObject("$lte", intervalEnd));
    DBCursor records = collection.find(objectsToMigrate)
                           .sort(new BasicDBObject(StateExecutionInstanceKeys.createdAt, 1))
                           .limit(batchSize);

    int totalRecord = 0;
    int recordAdded = 0;

    try {
      while (records.hasNext()) {
        DBObject record = records.next();
        StateExecutionInstance instance = persistence.convertToEntity(StateExecutionInstance.class, record);
        totalRecord++;

        boolean ignore = false;

        for (String stateType : StepEventProcessor.STATE_TYPES) {
          if (stateType.equals(instance.getStateType())) {
            ignore = true;
            break;
          }
        }

        if (!ignore) {
          recordAdded++;
          DeploymentStepTimeSeriesEvent deploymentStepTimeSeriesEvent =
              usageMetricsEventPublisher.constructDeploymentStepTimeSeriesEvent(instance.getAccountId(), instance);
          deploymentStepEventProcessor.processEvent(deploymentStepTimeSeriesEvent.getTimeSeriesEventInfo());
        }

        if (totalRecord != 0 && totalRecord % batchSize == 0) {
          log.info("{} records added to DEPLOYMENT_STEP table", recordAdded);
          sleep(Duration.ofMillis(100));

          records = collection.find(objectsToMigrate)
                        .sort(new BasicDBObject(StateExecutionInstanceKeys.createdAt, 1))
                        .skip(totalRecord)
                        .limit(batchSize);
        }
      }
      log.info("{} records added to DEPLOYMENT_STEP table", recordAdded);

    } catch (Exception e) {
      log.error("Exception occurred migrating StateExecutionInstance to DEPLOYMENT_STEP table", e);
    } finally {
      log.info(
          "Migration of StateExecutionInstance data to DEPLOYMENT_STEP table for accountId: {} successful", accountId);
      records.close();
    }
  }
}
