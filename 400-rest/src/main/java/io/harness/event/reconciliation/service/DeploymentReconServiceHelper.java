package io.harness.event.reconciliation.service;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.time.Duration.ofMinutes;

import io.harness.event.reconciliation.DetectionStatus;
import io.harness.event.reconciliation.ReconcilationAction;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecord;
import io.harness.event.reconciliation.deployment.DeploymentReconRecordRepository;
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
import software.wings.graphql.datafetcher.DataFetcherUtils;

import java.sql.*;
import java.util.*;
import java.util.Date;
import javax.management.openmbean.InvalidKeyException;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.*;

@Slf4j
public class DeploymentReconServiceHelper {
  protected static final long COOL_DOWN_INTERVAL = 15 * 60 * 1000; /* 15 MINS COOL DOWN INTERVAL */

  public static boolean shouldPerformReconciliation(
      @NotNull DeploymentReconRecord record, Long durationEndTs, HPersistence persistence) {
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
        updateOperations.set(
            DeploymentReconRecord.DeploymentReconRecordKeys.reconciliationStatus, ReconciliationStatus.FAILED);
        updateOperations.set(DeploymentReconRecord.DeploymentReconRecordKeys.reconEndTs, System.currentTimeMillis());
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

  public static Criteria getCriteria(String key, long durationStartTs, long durationEndTs, CriteriaContainer query) {
    Criteria startTimeCriteria;
    Criteria endTimeCriteria;

    if (key.equals(WorkflowExecution.WorkflowExecutionKeys.startTs)) {
      startTimeCriteria = query.criteria(key).lessThanOrEq(durationEndTs);
      startTimeCriteria.attach(query.criteria(key).greaterThanOrEq(durationStartTs));
      return startTimeCriteria;
    } else if (key.equals(WorkflowExecution.WorkflowExecutionKeys.endTs)) {
      endTimeCriteria = query.criteria(key).lessThanOrEq(durationEndTs);
      endTimeCriteria.attach(query.criteria(key).greaterThanOrEq(durationStartTs));
      return endTimeCriteria;
    } else {
      throw new InvalidKeyException("Unknown Time key " + key);
    }
  }

  public static void addTimeQuery(Query query, long durationStartTs, long durationEndTs) {
    CriteriaContainer orQuery = query.or();
    CriteriaContainer startTimeQuery = query.and();
    CriteriaContainer endTimeQuery = query.and();

    startTimeQuery.and(
        getCriteria(WorkflowExecution.WorkflowExecutionKeys.startTs, durationStartTs, durationEndTs, startTimeQuery));
    endTimeQuery.and(
        getCriteria(WorkflowExecution.WorkflowExecutionKeys.endTs, durationStartTs, durationEndTs, endTimeQuery));

    orQuery.add(startTimeQuery, endTimeQuery);
    query.and(orQuery);
  }
  public static boolean isStatusMismatchedInMongoAndTSDB(
      Map<String, String> tsdbRunningWFs, WorkflowExecution workflowExecution) {
    return tsdbRunningWFs.entrySet().stream().anyMatch(entry
        -> entry.getKey().equals(workflowExecution.getUuid())
            && !entry.getValue().equals(workflowExecution.getStatus().toString()));
  }

  public static ReconciliationStatus performReconciliationHelper(String accountId, long durationStartTs,
      long durationEndTs, TimeScaleDBService timeScaleDBService,
      DeploymentReconRecordRepository deploymentReconRecordRepository, HPersistence persistence,
      PersistentLocker persistentLocker, DeploymentReconService deploymentReconService, DataFetcherUtils utils) {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB is not valid, skipping reconciliation for accountID:[{}] in duration:[{}-{}]", accountId,
          new Date(durationStartTs), new Date(durationEndTs));
      return ReconciliationStatus.SUCCESS;
    }

    DeploymentReconRecord record = deploymentReconRecordRepository.getLatestDeploymentReconRecord(accountId);
    if (record == null || shouldPerformReconciliation(record, durationEndTs, persistence)) {
      try (AcquiredLock ignore = persistentLocker.waitToAcquireLock(
               DeploymentReconRecord.class, "AccountID-" + accountId, ofMinutes(1), ofMinutes(5))) {
        record = deploymentReconRecordRepository.getLatestDeploymentReconRecord(accountId);

        if (record != null && !shouldPerformReconciliation(record, durationEndTs, persistence)) {
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
        record = fetchRecord(id, persistence);

        boolean duplicatesDetected = false;
        boolean missingRecordsDetected = false;
        boolean statusMismatchDetected;

        List<String> executionIDs =
            checkForDuplicates(accountId, durationStartTs, durationEndTs, timeScaleDBService, "", utils);
        if (isNotEmpty(executionIDs)) {
          duplicatesDetected = true;
          log.warn("Duplicates detected for accountId:[{}] in duration:[{}-{}], executionIDs:[{}]", accountId,
              new Date(durationStartTs), new Date(durationEndTs), executionIDs);
          deleteDuplicates(accountId, durationStartTs, durationEndTs, executionIDs, timeScaleDBService, "");
        }

        long primaryCount = deploymentReconService.getWFExecCountFromMongoDB(accountId, durationStartTs, durationEndTs);
        long secondaryCount =
            getWFExecutionCountFromTSDB(accountId, durationStartTs, durationEndTs, timeScaleDBService, "", utils);
        if (primaryCount > secondaryCount) {
          missingRecordsDetected = true;
          deploymentReconService.insertMissingRecords(accountId, durationStartTs, durationEndTs);
        } else if (primaryCount == secondaryCount) {
          log.info("Everything is fine, no action required for accountID:[{}] in duration:[{}-{}]", accountId,
              new Date(durationStartTs), new Date(durationEndTs));
        } else {
          log.error("Duplicates found again for accountID:[{}] in duration:[{}-{}]", accountId,
              new Date(durationStartTs), new Date(durationEndTs));
        }

        Map<String, String> tsdbRunningWFs =
            getRunningWFsFromTSDB(accountId, durationStartTs, durationEndTs, timeScaleDBService, "");
        statusMismatchDetected = deploymentReconService.isStatusMismatchedAndUpdated(tsdbRunningWFs);

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
        updateOperations.set(DeploymentReconRecord.DeploymentReconRecordKeys.detectionStatus, detectionStatus);
        updateOperations.set(
            DeploymentReconRecord.DeploymentReconRecordKeys.reconciliationStatus, ReconciliationStatus.SUCCESS);
        updateOperations.set(DeploymentReconRecord.DeploymentReconRecordKeys.reconcilationAction, action);
        updateOperations.set(DeploymentReconRecord.DeploymentReconRecordKeys.reconEndTs, System.currentTimeMillis());
        persistence.update(record, updateOperations);

      } catch (Exception e) {
        log.error("Exception occurred while running reconciliation for accountID:[{}] in duration:[{}-{}]", accountId,
            new Date(durationStartTs), new Date(durationEndTs), e);
        if (record != null) {
          UpdateOperations updateOperations = persistence.createUpdateOperations(DeploymentReconRecord.class);
          updateOperations.set(
              DeploymentReconRecord.DeploymentReconRecordKeys.reconciliationStatus, ReconciliationStatus.FAILED);
          updateOperations.set(DeploymentReconRecord.DeploymentReconRecordKeys.reconEndTs, System.currentTimeMillis());
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
  public static DeploymentReconRecord fetchRecord(String uuid, HPersistence persistence) {
    return persistence.get(DeploymentReconRecord.class, uuid);
  }

  public static void deleteDuplicates(String accountId, long durationStartTs, long durationEndTs,
      List<String> executionIDs, TimeScaleDBService timeScaleDBService, String DELETE_DUPLICATE) {
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

  public static List<String> checkForDuplicates(String accountId, long durationStartTs, long durationEndTs,
      TimeScaleDBService timeScaleDBService, String CHECK_DUPLICATE_DATA_QUERY, DataFetcherUtils utils) {
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

  public static long getWFExecutionCountFromTSDB(String accountId, long durationStartTs, long durationEndTs,
      TimeScaleDBService timeScaleDBService, String CHECK_MISSING_DATA_QUERY, DataFetcherUtils utils) {
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

  public static Map<String, String> getRunningWFsFromTSDB(String accountId, long durationStartTs, long durationEndTs,
      TimeScaleDBService timeScaleDBService, String RUNNING_DEPLOYMENTS) {
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
}
