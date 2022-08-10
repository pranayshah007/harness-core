/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.time.Duration.ofMinutes;

import io.harness.beans.ExecutionStatus;
import io.harness.event.reconciliation.DetectionStatus;
import io.harness.event.reconciliation.ReconcilationAction;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecord;
import io.harness.event.reconciliation.deployment.DeploymentReconRecord.DeploymentReconRecordKeys;
import io.harness.event.timeseries.processor.DeploymentEventProcessor;
import io.harness.event.usagemetrics.UsageMetricsEventPublisher;
import io.harness.lock.AcquiredLock;
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
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.management.openmbean.InvalidKeyException;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Criteria;
import org.mongodb.morphia.query.CriteriaContainer;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
public class DeploymentReconServiceImpl implements DeploymentReconService {
  @Inject HPersistence persistence;
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject private PersistentLocker persistentLocker;
  @Inject private UsageMetricsEventPublisher usageMetricsEventPublisher;
  @Inject private DeploymentEventProcessor deploymentEventProcessor;
  @Inject private DataFetcherUtils utils;
  protected static final long COOL_DOWN_INTERVAL = 15 * 60 * 1000; /* 15 MINS COOL DOWN INTERVAL */

  private static final String CHECK_MISSING_DATA_QUERY =
      "SELECT COUNT(DISTINCT(EXECUTIONID)) FROM DEPLOYMENT WHERE ACCOUNTID=? AND ((STARTTIME>=? AND STARTTIME<=?) OR (ENDTIME>=? AND ENDTIME<=?)) AND PARENT_EXECUTION IS NULL;";

  private static final String CHECK_DUPLICATE_DATA_QUERY =
      "SELECT DISTINCT(D.EXECUTIONID) FROM DEPLOYMENT D,(SELECT COUNT(EXECUTIONID), EXECUTIONID FROM DEPLOYMENT A WHERE ACCOUNTID = ? AND ((STARTTIME>=? AND STARTTIME<=?) OR (ENDTIME>=? AND ENDTIME<=?)) GROUP BY EXECUTIONID HAVING COUNT(EXECUTIONID) > 1) AS B WHERE D.EXECUTIONID = B.EXECUTIONID;";

  private static final String DELETE_DUPLICATE = "DELETE FROM DEPLOYMENT WHERE EXECUTIONID = ANY (?);";

  private static final String FIND_DEPLOYMENT_IN_TSDB =
      "SELECT EXECUTIONID,STARTTIME FROM DEPLOYMENT WHERE EXECUTIONID=?";

  private static final String RUNNING_DEPLOYMENTS =
      "SELECT EXECUTIONID,STATUS FROM DEPLOYMENT WHERE ACCOUNTID=? AND STATUS IN ('RUNNING','PAUSED')";

  @Override
  public ReconciliationStatus performReconciliation(String accountId, long durationStartTs, long durationEndTs) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB is not valid, skipping reconciliation for accountID:[{}] in duration:[{}-{}]", accountId,
          new Date(durationStartTs), new Date(durationEndTs));
      return ReconciliationStatus.SUCCESS;
    }

    DeploymentReconRecord record = getLatestDeploymentReconRecord(accountId);
    if (record == null || shouldPerformReconciliation(record, durationEndTs)) {
      try (AcquiredLock ignore = persistentLocker.waitToAcquireLock(
               DeploymentReconRecord.class, "AccountID-" + accountId, ofMinutes(1), ofMinutes(5))) {
        record = getLatestDeploymentReconRecord(accountId);

        if (record != null && !shouldPerformReconciliation(record, durationEndTs)) {
          if (record.getReconciliationStatus() == ReconciliationStatus.IN_PROGRESS) {
            log.info("Reconciliation is in progress, not running it again for accountID:[{}] in duration:[{}-{}]",
                accountId, new Date(durationStartTs), new Date(durationEndTs));
          } else {
            log.info(
                "Reconciliation was performed recently at [{}], not running it again for accountID:[{}] in duration:[{}-{}]",
                accountId, new Date(durationStartTs), new Date(durationEndTs));
          }
          return ReconciliationStatus.SUCCESS;
        }

        record = DeploymentReconRecord.builder()
                     .accountId(accountId)
                     .reconciliationStatus(ReconciliationStatus.IN_PROGRESS)
                     .reconStartTs(System.currentTimeMillis())
                     .durationStartTs(durationStartTs)
                     .durationEndTs(durationEndTs)
                     .build();
        String id = persistence.save(record);
        log.info("Inserted new deploymentReconRecord for accountId:[{}],uuid:[{}]", accountId, id);
        record = fetchRecord(id);

        boolean duplicatesDetected = false;
        boolean missingRecordsDetected = false;
        boolean statusMismatchDetected;

        List<String> executionIDs = checkForDuplicates(accountId, durationStartTs, durationEndTs);
        if (isNotEmpty(executionIDs)) {
          duplicatesDetected = true;
          log.warn("Duplicates detected for accountId:[{}] in duration:[{}-{}], executionIDs:[{}]", accountId,
              new Date(durationStartTs), new Date(durationEndTs), executionIDs);
          deleteDuplicates(accountId, durationStartTs, durationEndTs, executionIDs);
        }

        long primaryCount = getWFExecCountFromMongoDB(accountId, durationStartTs, durationEndTs);
        long secondaryCount = getWFExecutionCountFromTSDB(accountId, durationStartTs, durationEndTs);
        if (primaryCount > secondaryCount) {
          missingRecordsDetected = true;
          insertMissingRecords(accountId, durationStartTs, durationEndTs);
        } else if (primaryCount == secondaryCount) {
          log.info("Everything is fine, no action required for accountID:[{}] in duration:[{}-{}]", accountId,
              new Date(durationStartTs), new Date(durationEndTs));
        } else {
          log.error("Duplicates found again for accountID:[{}] in duration:[{}-{}]", accountId,
              new Date(durationStartTs), new Date(durationEndTs));
        }

        Map<String, String> tsdbRunningWFs = getRunningWFsFromTSDB(accountId, durationStartTs, durationEndTs);
        statusMismatchDetected = isStatusMismatchedAndUpdated(tsdbRunningWFs);

        DetectionStatus detectionStatus;
        ReconcilationAction action;

        if (!statusMismatchDetected) {
          if (missingRecordsDetected && duplicatesDetected) {
            detectionStatus = DetectionStatus.DUPLICATE_DETECTED_MISSING_RECORDS_DETECTED;
            action = ReconcilationAction.DUPLICATE_REMOVAL_ADD_MISSING_RECORDS;
          } else if (duplicatesDetected) {
            detectionStatus = DetectionStatus.DUPLICATE_DETECTED;
            action = ReconcilationAction.DUPLICATE_REMOVAL;
          } else if (missingRecordsDetected) {
            detectionStatus = DetectionStatus.MISSING_RECORDS_DETECTED;
            action = ReconcilationAction.ADD_MISSING_RECORDS;
          } else {
            detectionStatus = DetectionStatus.SUCCESS;
            action = ReconcilationAction.NONE;
          }
        } else {
          if (missingRecordsDetected && duplicatesDetected) {
            detectionStatus = DetectionStatus.DUPLICATE_DETECTED_MISSING_RECORDS_DETECTED_STATUS_MISMATCH_DETECTED;
            action = ReconcilationAction.DUPLICATE_REMOVAL_ADD_MISSING_RECORDS_STATUS_RECONCILIATION;
          } else if (duplicatesDetected) {
            detectionStatus = DetectionStatus.DUPLICATE_DETECTED_STATUS_MISMATCH_DETECTED;
            action = ReconcilationAction.DUPLICATE_REMOVAL_STATUS_RECONCILIATION;
          } else if (missingRecordsDetected) {
            detectionStatus = DetectionStatus.MISSING_RECORDS_DETECTED_STATUS_MISMATCH_DETECTED;
            action = ReconcilationAction.ADD_MISSING_RECORDS_STATUS_RECONCILIATION;
          } else {
            detectionStatus = DetectionStatus.STATUS_MISMATCH_DETECTED;
            action = ReconcilationAction.STATUS_RECONCILIATION;
          }
        }

        UpdateOperations updateOperations = persistence.createUpdateOperations(DeploymentReconRecord.class);
        updateOperations.set(DeploymentReconRecordKeys.detectionStatus, detectionStatus);
        updateOperations.set(DeploymentReconRecordKeys.reconciliationStatus, ReconciliationStatus.SUCCESS);
        updateOperations.set(DeploymentReconRecordKeys.reconcilationAction, action);
        updateOperations.set(DeploymentReconRecordKeys.reconEndTs, System.currentTimeMillis());
        persistence.update(record, updateOperations);

      } catch (Exception e) {
        log.error("Exception occurred while running reconciliation for accountID:[{}] in duration:[{}-{}]", accountId,
            new Date(durationStartTs), new Date(durationEndTs), e);
        if (record != null) {
          UpdateOperations updateOperations = persistence.createUpdateOperations(DeploymentReconRecord.class);
          updateOperations.set(DeploymentReconRecordKeys.reconciliationStatus, ReconciliationStatus.FAILED);
          updateOperations.set(DeploymentReconRecordKeys.reconEndTs, System.currentTimeMillis());
          persistence.update(record, updateOperations);
          return ReconciliationStatus.FAILED;
        }
      }
    } else {
      log.info("Reconciliation task not required for accountId:[{}], durationStartTs: [{}], durationEndTs:[{}]",
          accountId, new Date(durationStartTs), new Date(durationEndTs));
    }
    return ReconciliationStatus.SUCCESS;
  }

  private void insertMissingRecords(String accountId, long durationStartTs, long durationEndTs) {
    Query<WorkflowExecution> query = persistence.createQuery(WorkflowExecution.class, excludeAuthority)
                                         .order(Sort.descending(WorkflowExecutionKeys.createdAt))
                                         .filter(WorkflowExecutionKeys.accountId, accountId)
                                         .field(WorkflowExecutionKeys.startTs)
                                         .exists()
                                         .project(WorkflowExecutionKeys.serviceExecutionSummaries, false);

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

  private long getWFExecutionCountFromTSDB(String accountId, long durationStartTs, long durationEndTs) {
    int totalTries = 0;
    while (totalTries <= 3) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(CHECK_MISSING_DATA_QUERY)) {
        statement.setString(1, accountId);
        statement.setTimestamp(2, new Timestamp(durationStartTs), utils.getDefaultCalendar());
        statement.setTimestamp(3, new Timestamp(durationEndTs), utils.getDefaultCalendar());
        statement.setTimestamp(4, new Timestamp(durationStartTs), utils.getDefaultCalendar());
        statement.setTimestamp(5, new Timestamp(durationEndTs), utils.getDefaultCalendar());
        resultSet = statement.executeQuery();
        if (resultSet.next()) {
          return resultSet.getLong(1);
        } else {
          return 0;
        }
      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to retrieve execution count from TimeScaleDB for accountID:[{}] in duration:[{}-{}], totalTries:[{}]",
            accountId, new Date(durationStartTs), new Date(durationEndTs), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return 0;
  }

  private void deleteDuplicates(String accountId, long durationStartTs, long durationEndTs, List<String> executionIDs) {
    int totalTries = 0;
    String[] executionIdsArray = executionIDs.toArray(new String[executionIDs.size()]);
    while (totalTries <= 3) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(DELETE_DUPLICATE)) {
        Array array = connection.createArrayOf("text", executionIdsArray);
        statement.setArray(1, array);
        statement.executeUpdate();
        return;
      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to delete duplicates for accountID:[{}] in duration:[{}-{}], executionIDs:[{}], totalTries:[{}]",
            accountId, new Date(durationStartTs), new Date(durationEndTs), executionIDs, totalTries, ex);
      }
    }
  }

  private List<String> checkForDuplicates(String accountId, long durationStartTs, long durationEndTs) {
    int totalTries = 0;
    List<String> duplicates = new ArrayList<>();
    while (totalTries <= 3) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(CHECK_DUPLICATE_DATA_QUERY)) {
        statement.setString(1, accountId);
        statement.setTimestamp(2, new Timestamp(durationStartTs), utils.getDefaultCalendar());
        statement.setTimestamp(3, new Timestamp(durationEndTs), utils.getDefaultCalendar());
        statement.setTimestamp(4, new Timestamp(durationStartTs), utils.getDefaultCalendar());
        statement.setTimestamp(5, new Timestamp(durationEndTs), utils.getDefaultCalendar());
        resultSet = statement.executeQuery();
        while (resultSet.next()) {
          duplicates.add(resultSet.getString(1));
        }
        return duplicates;

      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to check for duplicates from TimeScaleDB for accountID:[{}] in duration:[{}-{}], totalTries:[{}]",
            accountId, new Date(durationStartTs), new Date(durationEndTs), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return duplicates;
  }

  private DeploymentReconRecord fetchRecord(String uuid) {
    return persistence.get(DeploymentReconRecord.class, uuid);
  }

  protected DeploymentReconRecord getLatestDeploymentReconRecord(@NotNull String accountId) {
    try (HIterator<DeploymentReconRecord> iterator =
             new HIterator<>(persistence.createQuery(DeploymentReconRecord.class)
                                 .field(DeploymentReconRecordKeys.accountId)
                                 .equal(accountId)
                                 .order(Sort.descending(DeploymentReconRecordKeys.durationEndTs))
                                 .fetch())) {
      if (!iterator.hasNext()) {
        return null;
      }
      return iterator.next();
    }
  }

  protected long getWFExecCountFromMongoDB(String accountId, long durationStartTs, long durationEndTs) {
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

  protected Map<String, String> getRunningWFsFromTSDB(String accountId, long durationStartTs, long durationEndTs) {
    int totalTries = 0;
    Map<String, String> runningWFs = new HashMap<>();
    while (totalTries <= 3) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(RUNNING_DEPLOYMENTS)) {
        statement.setString(1, accountId);
        resultSet = statement.executeQuery();
        while (resultSet.next()) {
          runningWFs.put(resultSet.getString("executionId"), resultSet.getString("status"));
        }
        return runningWFs;

      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to retrieve running executions from TimeScaleDB for accountID:[{}] in duration:[{}-{}], totalTries:[{}]",
            accountId, new Date(durationStartTs), new Date(durationEndTs), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return runningWFs;
  }

  protected boolean isStatusMismatchedAndUpdated(Map<String, String> tsdbRunningWFs) {
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

  private boolean isStatusMismatchedInMongoAndTSDB(
      Map<String, String> tsdbRunningWFs, WorkflowExecution workflowExecution) {
    return tsdbRunningWFs.entrySet().stream().anyMatch(entry
        -> entry.getKey().equals(workflowExecution.getUuid())
            && !entry.getValue().equals(workflowExecution.getStatus().toString()));
  }

  protected void updateRunningWFsFromTSDB(WorkflowExecution workflowExecution) {
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

  private void addTimeQuery(Query query, long durationStartTs, long durationEndTs) {
    CriteriaContainer orQuery = query.or();
    CriteriaContainer startTimeQuery = query.and();
    CriteriaContainer endTimeQuery = query.and();

    startTimeQuery.and(getCriteria(WorkflowExecutionKeys.startTs, durationStartTs, durationEndTs, startTimeQuery));
    endTimeQuery.and(getCriteria(WorkflowExecutionKeys.endTs, durationStartTs, durationEndTs, endTimeQuery));

    orQuery.add(startTimeQuery, endTimeQuery);
    query.and(orQuery);
  }

  private Criteria getCriteria(String key, long durationStartTs, long durationEndTs, CriteriaContainer query) {
    Criteria startTimeCriteria;
    Criteria endTimeCriteria;

    if (key.equals(WorkflowExecutionKeys.startTs)) {
      startTimeCriteria = query.criteria(key).lessThanOrEq(durationEndTs);
      startTimeCriteria.attach(query.criteria(key).greaterThanOrEq(durationStartTs));
      return startTimeCriteria;
    } else if (key.equals(WorkflowExecutionKeys.endTs)) {
      endTimeCriteria = query.criteria(key).lessThanOrEq(durationEndTs);
      endTimeCriteria.attach(query.criteria(key).greaterThanOrEq(durationStartTs));
      return endTimeCriteria;
    } else {
      throw new InvalidKeyException("Unknown Time key " + key);
    }
  }

  protected boolean shouldPerformReconciliation(@NotNull DeploymentReconRecord record, Long durationEndTs) {
    if (record.getReconciliationStatus() == ReconciliationStatus.IN_PROGRESS) {
      /***
       * If the latest record in db is older than COOL_DOWN_INTERVAL, mark that reconciliation as failed and move on.
       * This is to prevent a bad record from blocking all further reconciliations
       */
      if (System.currentTimeMillis() - record.getDurationEndTs() > COOL_DOWN_INTERVAL) {
        log.warn("Found an old record in progress: record: [{}] for accountID:[{}] in duration:[{}-{}]",
            record.getUuid(), record.getAccountId(), new Date(record.getDurationStartTs()),
            new Date(record.getDurationEndTs()));
        UpdateOperations updateOperations = persistence.createUpdateOperations(DeploymentReconRecord.class);
        updateOperations.set(DeploymentReconRecordKeys.reconciliationStatus, ReconciliationStatus.FAILED);
        updateOperations.set(DeploymentReconRecordKeys.reconEndTs, System.currentTimeMillis());
        persistence.update(record, updateOperations);
        return true;
      }

      /**
       * If a reconciliation is in progress, do not kick off another reconciliation.
       * This is to prevent managers from stamping on each other
       */

      return false;
    }

    /**
     * If reconciliation was run recently AND if the duration for which it was run was in the recent time interval,
     * lets not run it again.
     */

    final long currentTime = System.currentTimeMillis();
    if (((currentTime - record.getReconEndTs()) < COOL_DOWN_INTERVAL)
        && (durationEndTs < currentTime && durationEndTs > (currentTime - COOL_DOWN_INTERVAL))) {
      log.info("Last recon for accountID:[{}] was run @ [{}], hence not rerunning it again", record.getAccountId(),
          new Date(record.getReconEndTs()));
      return false;
    }

    return true;
  }
}
