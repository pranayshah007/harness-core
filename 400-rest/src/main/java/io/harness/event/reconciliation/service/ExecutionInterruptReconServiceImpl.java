/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;
import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.addTimeQuery;
import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.performReconciliationHelper;
import static io.harness.mongo.MongoConfig.NO_LIMIT;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.threading.Morpheus.sleep;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecordRepository;
import io.harness.event.timeseries.processor.ExecutionInterruptProcessor;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.api.ExecutionInterruptTimeSeriesEvent;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.search.framework.ExecutionEntity;
import software.wings.sm.ExecutionInterrupt;
import software.wings.sm.ExecutionInterrupt.ExecutionInterruptKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@Singleton
@Slf4j
public class ExecutionInterruptReconServiceImpl implements DeploymentReconService {
  @Inject HPersistence persistence;
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private UsageMetricsEventPublisher usageMetricsEventPublisher;
  @Inject private DataFetcherUtils utils;
  @Inject private DeploymentReconRecordRepository deploymentReconRecordRepository;
  @Inject private ExecutionInterruptProcessor executionInterruptProcessor;
  @Inject private FeatureFlagService featureFlagService;

  private static final String FIND_EXECUTION_INTERRUPT_IN_TSDB =
      "SELECT ID,CREATED_AT FROM EXECUTION_INTERRUPT WHERE ID=?";

  @Override
  public ReconciliationStatus performReconciliation(
      String accountId, long durationStartTs, long durationEndTs, ExecutionEntity executionEntity) {
    return performReconciliationHelper(accountId, durationStartTs, durationEndTs, timeScaleDBService,
        deploymentReconRecordRepository, persistence, persistentLocker, utils, executionEntity, featureFlagService);
  }

  @Override
  public long getWFExecCountFromMongoDB(String accountId, long durationStartTs, long durationEndTs) {
    return persistence.createQuery(ExecutionInterrupt.class)
        .field(ExecutionInterruptKeys.accountId)
        .equal(accountId)
        .field(ExecutionInterruptKeys.createdAt)
        .exists()
        .field(ExecutionInterruptKeys.lastUpdatedAt)
        .greaterThanOrEq(durationStartTs)
        .field(ExecutionInterruptKeys.lastUpdatedAt)
        .lessThanOrEq(durationEndTs)
        .count();
  }

  @Override
  public boolean isStatusMismatchedAndUpdated(Map<String, String> tsdbRunningWFs) {
    return false;
  }

  @Override
  public boolean isStatusMismatchedAndUpdatedV2(String accountId, long durationStartTs, long durationEndTs,
      String sourceEntityClass, String completedExecutionsQuery, DataFetcherUtils utils) {
    return false;
  }

  @Override
  public void insertMissingRecords(String accountId, long durationStartTs, long durationEndTs) {
    Query<ExecutionInterrupt> query = persistence.createQuery(ExecutionInterrupt.class, excludeAuthority)
                                          .order(Sort.descending(ExecutionInterruptKeys.createdAt))
                                          .filter(ExecutionInterruptKeys.accountId, accountId)
                                          .field(ExecutionInterruptKeys.createdAt)
                                          .exists();

    addTimeQuery(
        query, durationStartTs, durationEndTs, ExecutionInterruptKeys.createdAt, ExecutionInterruptKeys.lastUpdatedAt);

    try (HIterator<ExecutionInterrupt> iterator = new HIterator<>(query.limit(NO_LIMIT).fetch())) {
      for (ExecutionInterrupt executionInterrupt : iterator) {
        checkAndAddIfRequired(executionInterrupt);
      }
    }
  }

  private void checkAndAddIfRequired(@NotNull ExecutionInterrupt executionInterrupt) {
    int totalTries = 0;
    boolean successfulInsert = false;
    while (totalTries <= 3 && !successfulInsert) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(FIND_EXECUTION_INTERRUPT_IN_TSDB)) {
        statement.setString(1, executionInterrupt.getUuid());
        resultSet = statement.executeQuery();
        if (resultSet.next()) {
          return;
        } else {
          ExecutionInterruptTimeSeriesEvent executionInterruptTimeSeriesEvent =
              usageMetricsEventPublisher.constructExecutionInterruptTimeSeriesEvent(
                  executionInterrupt.getAccountId(), executionInterrupt);
          log.info("ADDING MISSING RECORD ExecutionInterrupt for accountID:[{}], [{}]",
              executionInterrupt.getAccountId(), executionInterruptTimeSeriesEvent.getTimeSeriesEventInfo());
          executionInterruptProcessor.processEvent(executionInterruptTimeSeriesEvent.getTimeSeriesEventInfo());
          successfulInsert = true;
        }

      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to query ExecutionInterrupt from TimescaleDB for executionInterrupt:[{}] for accountId: [{}], totalTries:[{}]",
            executionInterrupt.getUuid(), executionInterrupt.getAccountId(), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
  }

  public void migrateDataMongoToTimescale(String accountId, int batchSize, Long intervalStart, Long intervalEnd) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating Execution Interrupt data to TimeScaleDB");
    }

    final DBCollection collection = persistence.getCollection(ExecutionInterrupt.class);
    BasicDBObject objectsToMigrate =
        new BasicDBObject(ExecutionInterruptKeys.accountId, accountId)
            .append(ExecutionInterruptKeys.createdAt, new BasicDBObject("$gte", intervalStart))
            .append(ExecutionInterruptKeys.createdAt, new BasicDBObject("$lte", intervalEnd));
    DBCursor records =
        collection.find(objectsToMigrate).sort(new BasicDBObject(ExecutionInterruptKeys.createdAt, 1)).limit(batchSize);

    int totalRecord = 0;

    try {
      while (records.hasNext()) {
        DBObject record = records.next();
        ExecutionInterrupt executionInterrupt = persistence.convertToEntity(ExecutionInterrupt.class, record);

        totalRecord++;

        ExecutionInterruptTimeSeriesEvent executionInterruptTimeSeriesEvent =
            usageMetricsEventPublisher.constructExecutionInterruptTimeSeriesEvent(
                executionInterrupt.getAccountId(), executionInterrupt);
        executionInterruptProcessor.processEvent(executionInterruptTimeSeriesEvent.getTimeSeriesEventInfo());

        if (totalRecord != 0 && totalRecord % batchSize == 0) {
          log.info("{} records added to EXECUTION_INTERRUPT table", totalRecord);
          sleep(Duration.ofMillis(100));

          records = collection.find(objectsToMigrate)
                        .sort(new BasicDBObject(ExecutionInterruptKeys.createdAt, 1))
                        .skip(totalRecord)
                        .limit(batchSize);
        }
      }
      log.info("{} records added to EXECUTION_INTERRUPT table", totalRecord);

    } catch (Exception e) {
      log.error("Exception occurred migrating Execution Interrupts to timescaleDB", e);
    } finally {
      log.info("Migration of Execution Interrupt data to timescaleDB for accountId: {} successful", accountId);
      records.close();
    }
  }
}
