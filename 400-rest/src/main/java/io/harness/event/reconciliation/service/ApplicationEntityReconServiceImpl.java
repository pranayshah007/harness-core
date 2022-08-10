/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HPersistence.DEFAULT_STORE;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofMinutes;

import io.harness.event.reconciliation.DetectionStatus;
import io.harness.event.reconciliation.ReconcilationAction;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecord;
import io.harness.event.reconciliation.looker.LookerEntityReconRecord;
import io.harness.event.reconciliation.looker.LookerEntityReconRecordRepository;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Application;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.search.framework.TimeScaleEntity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.client.model.DBCollectionFindOptions;
import java.io.IOException;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.Date;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.MorphiaKeyIterator;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
public class ApplicationEntityReconServiceImpl implements LookerEntityReconService {
  @Inject TimeScaleDBService timeScaleDBService;

  @Inject LookerEntityReconRecordRepository lookerEntityReconRecordRepository;

  @Inject private PersistentLocker persistentLocker;

  @Inject HPersistence persistence;

  protected static final long COOL_DOWN_INTERVAL = 15 * 60 * 1000; /* 15 MINS COOL DOWN INTERVAL */

  private static final String FETCH_IDS =
      "SELECT ID FROM CG_APPLICATIONS WHERE ACCOUNT_ID=? AND CREATED_AT>=? AND CREATED_AT<=?;";

  private static final String DELETE_DUPLICATE = "DELETE FROM CG_APPLICATIONS WHERE ID = ANY (?);";

  private static final String FIND_DEPLOYMENT_IN_TSDB = "SELECT ID,CREATED_AT FROM CG_APPLICATIONS WHERE ID=?";

  @Override
  public ReconciliationStatus performReconciliation(
      String accountId, long durationStartTs, long durationEndTs, TimeScaleEntity timeScaleEntity) {
    String sourceEntityClass = timeScaleEntity.getSourceEntityClass().getCanonicalName();
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB is not valid, skipping reconciliation for accountID:[{}] entity: [{}] in duration:[{}-{}]",
          accountId, sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs));
      return ReconciliationStatus.SUCCESS;
    }
    LookerEntityReconRecord record =
        lookerEntityReconRecordRepository.getLatestLookerEntityReconRecord(accountId, sourceEntityClass);
    if (record == null || shouldPerformReconciliation(record, durationEndTs)) {
      try (AcquiredLock ignore = persistentLocker.waitToAcquireLock(LookerEntityReconRecord.class,
               "AccountID-" + accountId + "-Entity-" + sourceEntityClass, ofMinutes(1), ofMinutes(5))) {
        record = lookerEntityReconRecordRepository.getLatestLookerEntityReconRecord(accountId, sourceEntityClass);

        if (record != null && !shouldPerformReconciliation(record, durationEndTs)) {
          if (record.getReconciliationStatus() == ReconciliationStatus.IN_PROGRESS) {
            log.info(
                "Reconciliation is in progress, not running it again for accountID:[{}] entity: [{}] in duration:[{}-{}]",
                accountId, sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs));
          } else {
            log.info(
                "Reconciliation was performed recently at [{}], not running it again for accountID:[{}] entity: [{}] in duration:[{}-{}]",
                accountId, sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs));
          }
          return ReconciliationStatus.SUCCESS;
        }

        record = LookerEntityReconRecord.builder()
                     .accountId(accountId)
                     .entityClass(sourceEntityClass)
                     .reconciliationStatus(ReconciliationStatus.IN_PROGRESS)
                     .reconStartTs(System.currentTimeMillis())
                     .durationStartTs(durationStartTs)
                     .durationEndTs(durationEndTs)
                     .build();
        String id = persistence.save(record);
        log.info("Inserted new lookerEntityReconRecord for accountId:[{}] entity: [{}] ,uuid:[{}]", accountId,
            sourceEntityClass, id);
        record = fetchRecord(id);

        boolean deletedRecordDetected = false;
        boolean missingRecordsDetected = false;

        Set<String> primaryIds = getApplicationIdsFromMongoDB(accountId, durationStartTs, durationEndTs);
        Set<String> secondaryIds =
            getApplicationIdsFromTSDB(accountId, durationStartTs, durationEndTs, sourceEntityClass);
        Set<String> allAppIds = new HashSet<>();
        Set<String> idsMissingInTSDB = new HashSet<>();
        Set<String> idsToBeDeletedFromTSDB = new HashSet<>();
        allAppIds.addAll(primaryIds);
        allAppIds.addAll(secondaryIds);
        for (String appId : allAppIds) {
          if (!secondaryIds.contains(appId)) {
            idsMissingInTSDB.add(appId);
          } else if (!primaryIds.contains(appId)) {
            idsToBeDeletedFromTSDB.add(appId);
          }
        }
        if (idsMissingInTSDB.size() > 0) {
          missingRecordsDetected = true;
          insertMissingRecords(idsMissingInTSDB, timeScaleEntity);
        } else if (idsToBeDeletedFromTSDB.size() > 0) {
          log.error("Deleted entries found again for accountID:[{}] entity: [{}] in duration:[{}-{}]", accountId,
              sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs));
          deletedRecordDetected = true;
          deleteRecords(idsToBeDeletedFromTSDB, timeScaleEntity);
        } else {
          log.info("Everything is fine, no action required for accountID:[{}] entity: [{}] in duration:[{}-{}]",
              accountId, sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs));
        }

        DetectionStatus detectionStatus;
        ReconcilationAction action;

        if (missingRecordsDetected && deletedRecordDetected) {
          detectionStatus = DetectionStatus.DELETED_RECORDS_DETECTED_MISSING_RECORDS_DETECTED;
          action = ReconcilationAction.DELETED_REMOVAL_ADD_MISSING_RECORDS;
        } else if (deletedRecordDetected) {
          detectionStatus = DetectionStatus.DELETED_RECORDS_DETECTED;
          action = ReconcilationAction.DELETED_REMOVAL;
        } else if (missingRecordsDetected) {
          detectionStatus = DetectionStatus.MISSING_RECORDS_DETECTED;
          action = ReconcilationAction.ADD_MISSING_RECORDS;
        } else {
          detectionStatus = DetectionStatus.SUCCESS;
          action = ReconcilationAction.NONE;
        }

        UpdateOperations updateOperations = persistence.createUpdateOperations(LookerEntityReconRecord.class);
        updateOperations.set(LookerEntityReconRecord.LookerEntityReconRecordKeys.detectionStatus, detectionStatus);
        updateOperations.set(LookerEntityReconRecord.LookerEntityReconRecordKeys.entityClass, sourceEntityClass);
        updateOperations.set(
            LookerEntityReconRecord.LookerEntityReconRecordKeys.reconciliationStatus, ReconciliationStatus.SUCCESS);
        updateOperations.set(LookerEntityReconRecord.LookerEntityReconRecordKeys.reconcilationAction, action);
        updateOperations.set(
            LookerEntityReconRecord.LookerEntityReconRecordKeys.reconEndTs, System.currentTimeMillis());
        persistence.update(record, updateOperations);

      } catch (Exception e) {
        log.error("Exception occurred while running reconciliation for accountID:[{}] in duration:[{}-{}]", accountId,
            new Date(durationStartTs), new Date(durationEndTs), e);
        if (record != null) {
          UpdateOperations updateOperations = persistence.createUpdateOperations(DeploymentReconRecord.class);
          updateOperations.set(
              LookerEntityReconRecord.LookerEntityReconRecordKeys.reconciliationStatus, ReconciliationStatus.FAILED);
          updateOperations.set(
              LookerEntityReconRecord.LookerEntityReconRecordKeys.reconEndTs, System.currentTimeMillis());
          updateOperations.set(LookerEntityReconRecord.LookerEntityReconRecordKeys.entityClass, sourceEntityClass);
          persistence.update(record, updateOperations);
          return ReconciliationStatus.FAILED;
        }
      }
    } else {
      log.info(
          "Reconciliation task not required for accountId:[{}], entity:[{}], durationStartTs: [{}], durationEndTs:[{}]",
          accountId, sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs));
    }
    return ReconciliationStatus.SUCCESS;
  }

  private void deleteRecords(Set<String> idsToBeDeletedFromTSDB, TimeScaleEntity timeScaleEntity) {
    for (String idToDelete : idsToBeDeletedFromTSDB) {
      timeScaleEntity.deleteFromTimescale(idToDelete);
    }
  }

  private void insertMissingRecords(Set<String> idsMissingInTSDB, TimeScaleEntity timeScaleEntity) {
    List<Application> applications = new LinkedList<>();
    final DBCollection collection = persistence.getCollection(timeScaleEntity.getSourceEntityClass());
    DBObject idFilter = new BasicDBObject("_id", new BasicDBObject("$in", idsMissingInTSDB.toArray()));
    int batchSize = 1000;
    DBCursor cursor = collection.find(idFilter, new DBCollectionFindOptions().batchSize(batchSize));
    while (cursor.hasNext()) {
      Application application = (Application) cursor.next();
      applications.add(application);
    }
    for (Application application : applications) {
      timeScaleEntity.savetoTimescale(application);
    }
  }

  protected boolean shouldPerformReconciliation(@NotNull LookerEntityReconRecord record, Long durationEndTs) {
    if (record.getReconciliationStatus() == ReconciliationStatus.IN_PROGRESS) {
      /***
       * If the latest record in db is older than COOL_DOWN_INTERVAL, mark that reconciliation as failed and move on.
       * This is to prevent a bad record from blocking all further reconciliations
       */
      if (System.currentTimeMillis() - record.getDurationEndTs() > COOL_DOWN_INTERVAL) {
        log.warn("Found an old record in progress: record: [{}] for accountID:[{}] in duration:[{}-{}]",
            record.getUuid(), record.getAccountId(), new Date(record.getDurationStartTs()),
            new Date(record.getDurationEndTs()));
        lookerEntityReconRecordRepository.updateReconStatus(record, ReconciliationStatus.FAILED);
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

  private LookerEntityReconRecord fetchRecord(String uuid) {
    return persistence.get(LookerEntityReconRecord.class, uuid);
  }

  protected Set<String> getApplicationIdsFromMongoDB(String accountId, long durationStartTs, long durationEndTs) {
    Set<String> appIds = new HashSet<>();
    MorphiaKeyIterator<Application> applications = persistence.createQuery(Application.class)
                                                       .field(WorkflowExecution.WorkflowExecutionKeys.accountId)
                                                       .equal(accountId)
                                                       .field(WorkflowExecution.WorkflowExecutionKeys.createdAt)
                                                       .exists()
                                                       .field(WorkflowExecution.WorkflowExecutionKeys.createdAt)
                                                       .greaterThanOrEq(durationStartTs)
                                                       .field(WorkflowExecution.WorkflowExecutionKeys.createdAt)
                                                       .lessThanOrEq(durationEndTs)
                                                       .fetchKeys();
    applications.forEachRemaining(applicationKey -> appIds.add((String) applicationKey.getId()));

    return appIds;
  }

  private Set<String> getApplicationIdsFromTSDB(
      String accountId, long durationStartTs, long durationEndTs, String sourceEntityClass) {
    int totalTries = 0;
    while (totalTries <= 3) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(FETCH_IDS)) {
        Set<String> applicationIds = new HashSet<>();
        statement.setString(1, accountId);
        statement.setLong(2, durationStartTs);
        statement.setLong(3, durationEndTs);
        resultSet = statement.executeQuery();
        while (resultSet.next()) {
          applicationIds.add(resultSet.getString(1));
        }
        return applicationIds;
      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to retrieve execution count from TimeScaleDB for accountID:[{}] entity:[{}] in duration:[{}-{}], totalTries:[{}]",
            accountId, sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs), totalTries, ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return new HashSet<>();
  }
}
