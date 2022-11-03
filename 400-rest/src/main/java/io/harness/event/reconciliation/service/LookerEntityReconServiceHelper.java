/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.reconciliation.service;

import static java.time.Duration.ofMinutes;

import io.harness.event.reconciliation.DetectionStatus;
import io.harness.event.reconciliation.ReconcilationAction;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.deployment.DeploymentReconRecord;
import io.harness.event.reconciliation.looker.LookerEntityReconRecord;
import io.harness.event.reconciliation.looker.LookerEntityReconRecord.LookerEntityReconRecordKeys;
import io.harness.event.reconciliation.looker.LookerEntityReconRecordRepository;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.persistence.HPersistence;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.search.framework.TimeScaleEntity;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.client.model.DBCollectionFindOptions;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class LookerEntityReconServiceHelper {
  private static final long COOL_DOWN_INTERVAL = 15 * 60 * 1000; /* 15 MINS COOL DOWN INTERVAL */
  private static final String FETCH_IDS = "SELECT ID FROM %s WHERE ACCOUNT_ID=? AND CREATED_AT>=? AND CREATED_AT<=?;";
  private static final String FETCH_CG_USER_IDS =
      "SELECT ID FROM %s WHERE ? = ANY (ACCOUNT_IDS) AND CREATED_AT>=? AND CREATED_AT<=?;";
  private static final String CG_USERS = "CG_USERS";

  public static void deleteRecords(Set<String> idsToBeDeletedFromTSDB, TimeScaleEntity timeScaleEntity) {
    for (String idToDelete : idsToBeDeletedFromTSDB) {
      timeScaleEntity.deleteFromTimescale(idToDelete);
    }
  }

  public static boolean shouldPerformReconciliation(@NotNull LookerEntityReconRecord record, Long durationEndTs,
      LookerEntityReconRecordRepository lookerEntityReconRecordRepository) {
    if (record.getReconciliationStatus() == ReconciliationStatus.IN_PROGRESS) {
      /***
       * If the latest record in db is older than COOL_DOWN_INTERVAL, mark that reconciliation as failed and move on.
       * This is to prevent a bad record from blocking all further reconciliations
       */
      if (System.currentTimeMillis() - record.getDurationEndTs() > COOL_DOWN_INTERVAL) {
        log.warn("Found an old record in progress for entity [{}]: record: [{}] for accountID:[{}] in duration:[{}-{}]",
            record.getEntityClass(), record.getUuid(), record.getAccountId(), new Date(record.getDurationStartTs()),
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
      log.info("Last recon for entity [{}] accountID:[{}] was run @ [{}], hence not rerunning it again",
          record.getEntityClass(), record.getAccountId(), new Date(record.getReconEndTs()));
      return false;
    }

    return true;
  }

  public static Set<String> getEntityIdsFromTSDB(String accountId, long durationStartTs, long durationEndTs,
      String sourceEntityClass, TimeScaleEntity timeScaleEntity, TimeScaleDBService timeScaleDBService) {
    String tableName = timeScaleEntity.getMigrationClassName();
    String query = CG_USERS.equals(tableName) ? FETCH_CG_USER_IDS : FETCH_IDS;
    query = String.format(query, tableName);
    Set<String> EntityIds = new HashSet<>();
    int totalTries = 0;
    while (totalTries <= 3) {
      ResultSet resultSet = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement statement = connection.prepareStatement(query)) {
        statement.setString(1, accountId);
        statement.setLong(2, durationStartTs);
        statement.setLong(3, durationEndTs);
        resultSet = statement.executeQuery();
        while (resultSet.next()) {
          EntityIds.add(resultSet.getString(1));
        }
        return EntityIds;
      } catch (SQLException ex) {
        totalTries++;
        log.warn(
            "Failed to retrieve Id from {} TimeScaleDB for accountID:[{}] entity:[{}] in duration:[{}-{}], totalTries:[{}]",
            tableName, accountId, sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs), totalTries,
            ex);
      } finally {
        DBUtils.close(resultSet);
      }
    }
    return new HashSet<>();
  }

  public static void insertMissingRecords(
      Set<String> idsMissingInTSDB, TimeScaleEntity timeScaleEntity, HPersistence persistence) {
    List<DBObject> dbObjects = new ArrayList<>();
    final DBCollection collection = persistence.getCollection(timeScaleEntity.getSourceEntityClass());
    DBObject idFilter = new BasicDBObject("_id", new BasicDBObject("$in", idsMissingInTSDB.toArray()));
    int batchSize = 1000;
    DBCursor cursor = collection.find(idFilter, new DBCollectionFindOptions().batchSize(batchSize));
    while (cursor.hasNext()) {
      dbObjects.add(cursor.next());
    }
    for (DBObject dbObject : dbObjects) {
      timeScaleEntity.savetoTimescale(persistence.convertToEntity(timeScaleEntity.getSourceEntityClass(), dbObject));
    }
  }

  public static ReconciliationStatus performReconciliationHelper(String accountId, long durationStartTs,
      long durationEndTs, TimeScaleEntity timeScaleEntity, TimeScaleDBService timeScaleDBService,
      LookerEntityReconRecordRepository lookerEntityReconRecordRepository, PersistentLocker persistentLocker,
      HPersistence persistence) {
    String sourceEntityClass = timeScaleEntity.getSourceEntityClass().getCanonicalName();
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB is not valid, skipping reconciliation for accountID:[{}] entity: [{}] in duration:[{}-{}]",
          accountId, sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs));
      return ReconciliationStatus.SUCCESS;
    }
    LookerEntityReconRecord record =
        lookerEntityReconRecordRepository.getLatestLookerEntityReconRecord(accountId, sourceEntityClass);
    if (record == null || shouldPerformReconciliation(record, durationEndTs, lookerEntityReconRecordRepository)) {
      try (AcquiredLock ignore = persistentLocker.waitToAcquireLock(LookerEntityReconRecord.class,
               "AccountID-" + accountId + "-Entity-" + sourceEntityClass, ofMinutes(1), ofMinutes(5))) {
        record = lookerEntityReconRecordRepository.getLatestLookerEntityReconRecord(accountId, sourceEntityClass);

        if (record != null && !shouldPerformReconciliation(record, durationEndTs, lookerEntityReconRecordRepository)) {
          if (record.getReconciliationStatus() == ReconciliationStatus.IN_PROGRESS) {
            log.info(
                "Reconciliation is in progress, not running it again for accountID:[{}] entity: [{}] in duration:[{}-{}]",
                accountId, sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs));
          } else {
            log.info(
                "Reconciliation was performed recently at [{}], not running it again for accountID:[{}] entity: [{}] in duration:[{}-{}]",
                record.getReconEndTs(), accountId, sourceEntityClass, new Date(durationStartTs),
                new Date(durationEndTs));
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

        boolean deletedRecordDetected = false;
        boolean missingRecordsDetected = false;

        Set<String> primaryIds =
            timeScaleEntity.getReconService().getEntityIdsFromMongoDB(accountId, durationStartTs, durationEndTs);
        Set<String> secondaryIds = getEntityIdsFromTSDB(
            accountId, durationStartTs, durationEndTs, sourceEntityClass, timeScaleEntity, timeScaleDBService);
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
          log.info("Missing entries found for accountID:[{}] entity: [{}] in duration:[{}-{}]", accountId,
              sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs));
          missingRecordsDetected = true;
          insertMissingRecords(idsMissingInTSDB, timeScaleEntity, persistence);
        }
        if (idsToBeDeletedFromTSDB.size() > 0) {
          log.info("Deleted entries found for accountID:[{}] entity: [{}] in duration:[{}-{}]", accountId,
              sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs));
          deletedRecordDetected = true;
          deleteRecords(idsToBeDeletedFromTSDB, timeScaleEntity);
        }
        if (!(idsMissingInTSDB.size() > 0 || idsToBeDeletedFromTSDB.size() > 0)) {
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
        updateOperations.set(LookerEntityReconRecordKeys.detectionStatus, detectionStatus);
        updateOperations.set(LookerEntityReconRecordKeys.entityClass, sourceEntityClass);
        updateOperations.set(LookerEntityReconRecordKeys.reconciliationStatus, ReconciliationStatus.SUCCESS);
        updateOperations.set(LookerEntityReconRecordKeys.reconcilationAction, action);
        updateOperations.set(LookerEntityReconRecordKeys.reconEndTs, System.currentTimeMillis());
        persistence.update(record, updateOperations);

      } catch (Exception e) {
        log.error("Exception occurred while running reconciliation for entity:[{}], accountID:[{}] in duration:[{}-{}]",
            sourceEntityClass, accountId, new Date(durationStartTs), new Date(durationEndTs), e);
        if (record != null) {
          UpdateOperations updateOperations = persistence.createUpdateOperations(DeploymentReconRecord.class);
          updateOperations.set(LookerEntityReconRecordKeys.reconciliationStatus, ReconciliationStatus.FAILED);
          updateOperations.set(LookerEntityReconRecordKeys.reconEndTs, System.currentTimeMillis());
          updateOperations.set(LookerEntityReconRecordKeys.entityClass, sourceEntityClass);
          persistence.update(record, updateOperations);
        }
        return ReconciliationStatus.FAILED;
      }
    } else {
      log.info(
          "Reconciliation task not required for accountId:[{}], entity:[{}], durationStartTs: [{}], durationEndTs:[{}]",
          accountId, sourceEntityClass, new Date(durationStartTs), new Date(durationEndTs));
    }
    return ReconciliationStatus.SUCCESS;
  }
}
