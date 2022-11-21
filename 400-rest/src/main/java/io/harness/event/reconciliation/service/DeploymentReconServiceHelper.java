/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.time.Duration.ofMinutes;

import io.harness.datacollection.utils.EmptyPredicate;
import io.harness.event.reconciliation.DetectionStatus;
import io.harness.event.reconciliation.ReconcilationAction;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecord;
import io.harness.event.reconciliation.deployment.DeploymentReconRecord.DeploymentReconRecordKeys;
import io.harness.event.reconciliation.deployment.DeploymentReconRecordRepository;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.search.framework.ExecutionEntity;

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
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Criteria;
import org.mongodb.morphia.query.CriteriaContainer;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class DeploymentReconServiceHelper {
  protected static final long COOL_DOWN_INTERVAL = 15 * 60 * 1000; /* 15 MINS COOL DOWN INTERVAL */

  public static boolean shouldPerformReconciliation(@NotNull DeploymentReconRecord record, Long durationEndTs,
      HPersistence persistence, DeploymentReconRecordRepository deploymentReconRecordRepository) {
    if (record.getReconciliationStatus() == ReconciliationStatus.IN_PROGRESS) {
      /***
       * If the latest record in db is older than COOL_DOWN_INTERVAL, mark that reconciliation as failed and move on.
       * This is to prevent a bad record from blocking all further reconciliations
       */
      if (System.currentTimeMillis() - record.getDurationEndTs() > COOL_DOWN_INTERVAL) {
        log.warn("Found an old record in progress: record: [{}] entity: [{}] for accountID:[{}] in duration:[{}-{}]",
            record.getUuid(), record.getEntityClass(), record.getAccountId(), new Date(record.getDurationStartTs()),
            new Date(record.getDurationEndTs()));
        UpdateOperations updateOperations = persistence.createUpdateOperations(DeploymentReconRecord.class);
        updateOperations.set(DeploymentReconRecordKeys.reconciliationStatus, ReconciliationStatus.FAILED);
        updateOperations.set(DeploymentReconRecordKeys.reconEndTs, System.currentTimeMillis());
        deploymentReconRecordRepository.updateDeploymentReconRecord(record, updateOperations);
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
      log.info("Last recon for entity: [{}] accountID:[{}] was run @ [{}], hence not rerunning it again",
          record.getEntityClass(), record.getAccountId(), new Date(record.getReconEndTs()));
      return false;
    }

    return true;
  }

  public static Criteria getCriteria(String key, long durationStartTs, long durationEndTs, CriteriaContainer query) {
    Criteria criteria;
    criteria = query.criteria(key).lessThanOrEq(durationEndTs);
    criteria.attach(query.criteria(key).greaterThanOrEq(durationStartTs));
    return criteria;
  }

  public static void addTimeQuery(
      Query query, long durationStartTs, long durationEndTs, String startTsKey, String endTsKey) {
    CriteriaContainer orQuery = query.or();
    CriteriaContainer startTimeQuery = query.and();
    CriteriaContainer endTimeQuery = query.and();

    startTimeQuery.and(getCriteria(startTsKey, durationStartTs, durationEndTs, startTimeQuery));
    endTimeQuery.and(getCriteria(endTsKey, durationStartTs, durationEndTs, endTimeQuery));

    orQuery.add(startTimeQuery, endTimeQuery);
    query.and(orQuery);
  }
  public static boolean isStatusMismatchedInMongoAndTSDB(Map<String, String> tsdbRunningWFs, String id, String status) {
    if (status.equals(tsdbRunningWFs.get(id))) {
      return false;
    } else {
      return true;
    }
  }

  public static ReconciliationStatus performReconciliationHelper(String accountId, long durationStartTs,
      long durationEndTs, TimeScaleDBService timeScaleDBService,
      DeploymentReconRecordRepository deploymentReconRecordRepository, HPersistence persistence,
      PersistentLocker persistentLocker, DataFetcherUtils utils, ExecutionEntity executionEntity) {
    String sourceEntityClass = executionEntity.getSourceEntityClass().getCanonicalName();

    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB is not valid, skipping reconciliation for entity: [{}] accountID:[{}] in duration:[{}-{}]",
          sourceEntityClass, accountId, new Date(durationStartTs), new Date(durationEndTs));
      return ReconciliationStatus.SUCCESS;
    }

    DeploymentReconRecord record =
        deploymentReconRecordRepository.getLatestDeploymentReconRecord(accountId, sourceEntityClass);
    if (record == null
        || shouldPerformReconciliation(record, durationEndTs, persistence, deploymentReconRecordRepository)) {
      try (AcquiredLock ignore = persistentLocker.waitToAcquireLock(DeploymentReconRecord.class,
               "AccountID-" + accountId + "-Entity-" + sourceEntityClass, ofMinutes(1), ofMinutes(5))) {
        record = deploymentReconRecordRepository.getLatestDeploymentReconRecord(accountId, sourceEntityClass);

        if (record != null
            && !shouldPerformReconciliation(record, durationEndTs, persistence, deploymentReconRecordRepository)) {
          if (record.getReconciliationStatus() == ReconciliationStatus.IN_PROGRESS) {
            log.info(
                "Reconciliation is in progress, not running it again for entity: [{}] accountID:[{}] in duration:[{}-{}]",
                sourceEntityClass, accountId, new Date(durationStartTs), new Date(durationEndTs));
          } else {
            log.info(
                "Reconciliation was performed recently at [{}], not running it again for entity: [{}] accountID:[{}] in duration:[{}-{}]",
                record.getReconEndTs(), sourceEntityClass, accountId, new Date(durationStartTs),
                new Date(durationEndTs));
          }
          return ReconciliationStatus.SUCCESS;
        }

        record = DeploymentReconRecord.builder()
                     .accountId(accountId)
                     .entityClass(sourceEntityClass)
                     .reconciliationStatus(ReconciliationStatus.IN_PROGRESS)
                     .reconStartTs(System.currentTimeMillis())
                     .durationStartTs(durationStartTs)
                     .durationEndTs(durationEndTs)
                     .build();
        String id = deploymentReconRecordRepository.saveDeploymentReconRecord(record);
        log.info("Inserted new deploymentReconRecord for entity: [{}] accountId:[{}],uuid:[{}]", sourceEntityClass,
            accountId, id);
        record = fetchRecord(id, persistence);

        boolean duplicatesDetected = false;
        boolean missingRecordsDetected = false;
        boolean statusMismatchDetected;

        List<String> executionIDs = checkForDuplicates(accountId, durationStartTs, durationEndTs, timeScaleDBService,
            executionEntity.getDuplicatesQuery(), utils, sourceEntityClass);
        if (isNotEmpty(executionIDs)) {
          duplicatesDetected = true;
          log.warn("Duplicates detected for entity: [{}] accountId:[{}] in duration:[{}-{}], executionIDs:[{}]",
              sourceEntityClass, accountId, new Date(durationStartTs), new Date(durationEndTs), executionIDs);
          deleteDuplicates(accountId, durationStartTs, durationEndTs, executionIDs, timeScaleDBService,
              executionEntity.getDeleteSetQuery(), sourceEntityClass);
        }

        long primaryCount =
            executionEntity.getReconService().getWFExecCountFromMongoDB(accountId, durationStartTs, durationEndTs);
        long secondaryCount = getWFExecutionCountFromTSDB(accountId, durationStartTs, durationEndTs, timeScaleDBService,
            executionEntity.getEntityCountQuery(), utils, sourceEntityClass);
        if (primaryCount > secondaryCount) {
          missingRecordsDetected = true;
          executionEntity.getReconService().insertMissingRecords(accountId, durationStartTs, durationEndTs);
        } else if (primaryCount == secondaryCount) {
          log.info("Everything is fine, no action required for entity: [{}] accountID:[{}] in duration:[{}-{}]",
              sourceEntityClass, accountId, new Date(durationStartTs), new Date(durationEndTs));
        } else {
          log.error("Duplicates found again for entity: [{}] accountID:[{}] in duration:[{}-{}]", sourceEntityClass,
              accountId, new Date(durationStartTs), new Date(durationEndTs));
        }

        Map<String, String> tsdbRunningWFs = getRunningWFsFromTSDB(accountId, durationStartTs, durationEndTs,
            timeScaleDBService, executionEntity.getRunningExecutionQuery(), sourceEntityClass);
        statusMismatchDetected = executionEntity.getReconService().isStatusMismatchedAndUpdated(tsdbRunningWFs);

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
        deploymentReconRecordRepository.updateDeploymentReconRecord(record, updateOperations);

      } catch (Exception e) {
        log.error("Exception occurred while running reconciliation for entity: [{}] accountID:[{}] in duration:[{}-{}]",
            sourceEntityClass, accountId, new Date(durationStartTs), new Date(durationEndTs), e);
        if (record != null) {
          UpdateOperations updateOperations = persistence.createUpdateOperations(DeploymentReconRecord.class);
          updateOperations.set(DeploymentReconRecordKeys.reconciliationStatus, ReconciliationStatus.FAILED);
          updateOperations.set(DeploymentReconRecordKeys.reconEndTs, System.currentTimeMillis());
          deploymentReconRecordRepository.updateDeploymentReconRecord(record, updateOperations);
          return ReconciliationStatus.FAILED;
        }
      }
    } else {
      log.info(
          "Reconciliation task not required for entity: [{}] accountId:[{}], durationStartTs: [{}], durationEndTs:[{}]",
          sourceEntityClass, accountId, new Date(durationStartTs), new Date(durationEndTs));
    }
    return ReconciliationStatus.SUCCESS;
  }

  public static DeploymentReconRecord fetchRecord(String uuid, HPersistence persistence) {
    return persistence.get(DeploymentReconRecord.class, uuid);
  }

  public static void deleteDuplicates(String accountId, long durationStartTs, long durationEndTs,
      List<String> executionIDs, TimeScaleDBService timeScaleDBService, String query, String sourceEntityClass) {
    int totalTries = 0;
    String[] executionIdsArray = executionIDs.toArray(new String[executionIDs.size()]);
    while (totalTries <= 3) {
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        Array array = connection.createArrayOf("text", executionIdsArray);
        statement.setArray(1, array);
        statement.executeUpdate();
        return;
      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to delete duplicates for entity: [{}] accountID:[{}] in duration:[{}-{}], executionIDs:[{}], totalTries:[{}]",
            sourceEntityClass, accountId, new Date(durationStartTs), new Date(durationEndTs), executionIDs, totalTries,
            ex);
      }
    }
  }

  public static List<String> checkForDuplicates(String accountId, long durationStartTs, long durationEndTs,
      TimeScaleDBService timeScaleDBService, String query, DataFetcherUtils utils, String sourceEntityClass) {
    int totalTries = 0;
    List<String> duplicates = new ArrayList<>();
    while (totalTries <= 3) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
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
            "Failed to check for duplicates from TimeScaleDB for entity: [{}] accountID:[{}] in duration:[{}-{}], totalTries:[{}]",
            sourceEntityClass, accountId, new Date(durationStartTs), new Date(durationEndTs), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return duplicates;
  }

  public static long getWFExecutionCountFromTSDB(String accountId, long durationStartTs, long durationEndTs,
      TimeScaleDBService timeScaleDBService, String query, DataFetcherUtils utils, String sourceEntityClass) {
    int totalTries = 0;
    while (totalTries <= 3) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
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
            "Failed to retrieve execution count from TimeScaleDB for entity: [{}] accountID:[{}] in duration:[{}-{}], totalTries:[{}]",
            sourceEntityClass, accountId, new Date(durationStartTs), new Date(durationEndTs), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return 0;
  }

  public static Map<String, String> getRunningWFsFromTSDB(String accountId, long durationStartTs, long durationEndTs,
      TimeScaleDBService timeScaleDBService, String query, String sourceEntityClass) {
    Map<String, String> runningWFs = new HashMap<>();
    if (EmptyPredicate.isEmpty(query)) {
      return runningWFs;
    }
    int totalTries = 0;
    while (totalTries <= 3) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountId);
        resultSet = statement.executeQuery();
        while (resultSet.next()) {
          runningWFs.put(resultSet.getString(1), resultSet.getString(2));
        }
        return runningWFs;

      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to retrieve running executions from TimeScaleDB for entity: [{}] accountID:[{}] in duration:[{}-{}], totalTries:[{}]",
            sourceEntityClass, accountId, new Date(durationStartTs), new Date(durationEndTs), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return runningWFs;
  }
}
