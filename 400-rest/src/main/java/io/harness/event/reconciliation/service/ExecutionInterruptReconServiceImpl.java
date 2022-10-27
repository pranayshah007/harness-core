package io.harness.event.reconciliation.service;

import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.addTimeQuery;
import static io.harness.event.reconciliation.service.DeploymentReconServiceHelper.performReconciliationHelper;
import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecordRepository;
import io.harness.event.timeseries.processor.ExecutionInterruptProcessor;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
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
public class ExecutionInterruptReconServiceImpl implements DeploymentReconService {
  @Inject HPersistence persistence;
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private UsageMetricsEventPublisher usageMetricsEventPublisher;
  @Inject private DataFetcherUtils utils;
  @Inject private DeploymentReconRecordRepository deploymentReconRecordRepository;
  @Inject private ExecutionInterruptProcessor executionInterruptProcessor;

  private static final String FIND_EXECUTION_INTERRUPT_IN_TSDB =
      "SELECT ID,CREATED_AT FROM EXECUTION_INTERRUPT WHERE ID=?";

  @Override
  public ReconciliationStatus performReconciliation(
      String accountId, long durationStartTs, long durationEndTs, ExecutionEntity executionEntity) {
    return performReconciliationHelper(accountId, durationStartTs, durationEndTs, timeScaleDBService,
        deploymentReconRecordRepository, persistence, persistentLocker, utils, executionEntity);
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
  public void insertMissingRecords(String accountId, long durationStartTs, long durationEndTs) {
    Query<ExecutionInterrupt> query = persistence.createQuery(ExecutionInterrupt.class, excludeAuthority)
                                          .order(Sort.descending(ExecutionInterruptKeys.createdAt))
                                          .filter(ExecutionInterruptKeys.accountId, accountId)
                                          .field(ExecutionInterruptKeys.createdAt)
                                          .exists();

    addTimeQuery(
        query, durationStartTs, durationEndTs, ExecutionInterruptKeys.createdAt, ExecutionInterruptKeys.lastUpdatedAt);

    try (HIterator<ExecutionInterrupt> iterator = new HIterator<>(query.fetch())) {
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
}
