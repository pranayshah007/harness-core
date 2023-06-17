/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.logging.LogLevel;
import io.harness.migration.NGMigration;
import io.harness.mongo.MongoConfig;
import io.harness.springdata.PersistenceUtils;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

/**
 * Migration to populate ttl field in Approval Instances. Also deleting old instances which are incompatible with entity
 * (can't be read).
 *
 * Granularity of the migration written according to approval Instances count in production at the time of writing the
 * migration
 *
 */
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PopulateTTLFieldAndDeleteOldInApprovalInstancesMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;
  private static final String DEBUG_LOG = "[ApprovalInstanceTTLMigration]: ";
  private static final int BATCH_SIZE = 500;
  private static final long CREATED_AT_LIMIT = 1650170397000L;
  private final RetryPolicy<Object> updateRetryPolicy = PersistenceUtils.getRetryPolicy(
      String.format("%s [Retrying]: Failed updating ApprovalInstances; attempt: {}", DEBUG_LOG),
      String.format("%s [Failed]: Failed updating ApprovalInstances; attempt: {}", DEBUG_LOG));

  @Override
  public void migrate() {
    int totalApprovalInstancesUpdated = 0;
    try {
      decorateWithDebugStringAndLog("Starting migration of ttl field in Approval instances", INFO, null);

      Query deleteQuery =
          new Query(Criteria.where(ApprovalInstanceKeys.createdAt).lt(CREATED_AT_LIMIT)).limit(MongoConfig.NO_LIMIT);
      DeleteResult deleteResult;
      try {
        deleteResult = mongoTemplate.remove(deleteQuery, ApprovalInstance.class);
        decorateWithDebugStringAndLog(
            String.format("Successfully deleted old incompatible records: %s", deleteResult.getDeletedCount()), INFO,
            null);

      } catch (Exception ex) {
        decorateWithDebugStringAndLog("Deletion of old incompatible records in approvalInstances failed", ERROR, ex);
      }

      // only BATCH_SIZE records in memory at once
      Query query =
          new Query(Criteria.where(ApprovalInstanceKeys.createdAt).gte(CREATED_AT_LIMIT)).limit(MongoConfig.NO_LIMIT);
      query.cursorBatchSize(BATCH_SIZE);

      List<ApprovalInstance> approvalInstancesToBeUpdatedInCurrentBatch = new ArrayList<>();

      // saveAll in mongo will lead to n update operations in this case, hence bulkOps is being used.
      BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ApprovalInstance.class);

      try (CloseableIterator<ApprovalInstance> approvalInstancesIterator =
               mongoTemplate.stream(query, ApprovalInstance.class)) {
        while (approvalInstancesIterator.hasNext()) {
          ApprovalInstance approvalInstance = approvalInstancesIterator.next();

          if (!isNull(approvalInstance.getValidUntil())) {
            decorateWithDebugStringAndLog(
                String.format(
                    "Skipping since approval instance with identifier %s already has ttl field populated at first-level",
                    approvalInstance.getId()),
                INFO, null);
            continue;
          }

          addUpdateOperationForApprovalInstance(approvalInstance, bulkOperations);
          approvalInstancesToBeUpdatedInCurrentBatch.add(approvalInstance);

          // If max update batch is reached, execute bulkUpdate for all entities in current batch
          // also clear the batch and bulkOps
          if (approvalInstancesToBeUpdatedInCurrentBatch.size() >= BATCH_SIZE) {
            totalApprovalInstancesUpdated += updateApprovalInstancesInBatchInternal(
                bulkOperations, approvalInstancesToBeUpdatedInCurrentBatch.size());
            approvalInstancesToBeUpdatedInCurrentBatch.clear();
            bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, ApprovalInstance.class);
          }
        }
      } catch (Exception e) {
        decorateWithDebugStringAndLog("Failed trying to fetch approval Instances or executing migration", ERROR, e);
      }

      if (EmptyPredicate.isNotEmpty(approvalInstancesToBeUpdatedInCurrentBatch)) {
        totalApprovalInstancesUpdated +=
            updateApprovalInstancesInBatchInternal(bulkOperations, approvalInstancesToBeUpdatedInCurrentBatch.size());
      }

      decorateWithDebugStringAndLog(
          String.format("Migration of ttl field in Approval instances completed, total instances updated: %s",
              totalApprovalInstancesUpdated),
          INFO, null);

    } catch (Exception e) {
      decorateWithDebugStringAndLog(
          String.format("Migration of ttl field in Approval instances failed, total instances updated: %s",
              totalApprovalInstancesUpdated),
          ERROR, e);
    }
  }

  public int updateApprovalInstancesInBatchInternal(BulkOperations bulkOperations, int currentBatchSize) {
    int successApprovalUpdateCount = 0;
    try {
      // retrying because update operation is idempotent
      successApprovalUpdateCount =
          Failsafe.with(updateRetryPolicy).get(() -> bulkOperations.execute().getModifiedCount());
      int failedApprovalUpdateCount = currentBatchSize - successApprovalUpdateCount;
      if (failedApprovalUpdateCount > 0) {
        decorateWithDebugStringAndLog(
            String.format("during bulk update, %s instances failed to update", failedApprovalUpdateCount), WARN, null);
      } else if (failedApprovalUpdateCount == 0) {
        decorateWithDebugStringAndLog(
            String.format("successfully executed bulkUpdate on %s instances", successApprovalUpdateCount), INFO, null);
      }
    } catch (Exception ex) {
      decorateWithDebugStringAndLog(
          String.format(
              "Failed trying to execute bulk update and %s instances required to be updated", currentBatchSize),
          ERROR, ex);
    }
    return successApprovalUpdateCount;
  }

  public void addUpdateOperationForApprovalInstance(ApprovalInstance approvalInstance, BulkOperations bulkOps) {
    try {
      if (isNull(approvalInstance) || isNull(bulkOps)) {
        return;
      }
      Criteria idCriteria = Criteria.where(ApprovalInstanceKeys.id).is(approvalInstance.getId());
      Long createdAt = approvalInstance.getCreatedAt();

      if (isNull(createdAt)) {
        decorateWithDebugStringAndLog(
            String.format(
                "Skipping adding update operation for approval instance with identifier %s as created_at is absent",
                approvalInstance.getId()),
            WARN, null);
        return;
      }
      // implementation done to ensure OffsetDateTime's plusMonths is called
      // similar toh when populating validUntil field

      Date validUntil = Date.from(
          Instant.ofEpochMilli(createdAt).atOffset(ZoneOffset.UTC).plusMonths(ApprovalInstance.TTL_MONTHS).toInstant());

      Update update = new Update();
      update.set(ApprovalInstanceKeys.validUntil, validUntil);

      bulkOps.updateOne(new Query(idCriteria), update);

    } catch (Exception exception) {
      decorateWithDebugStringAndLog(
          String.format("Failed trying to add update operation for approval instance with identifier %s",
              approvalInstance.getId()),
          ERROR, exception);
    }
  }

  private void decorateWithDebugStringAndLog(String logLine, LogLevel logLevel, Exception ex) {
    String logFormat = "%s %s";
    switch (logLevel) {
      case INFO:
        log.info(String.format(logFormat, DEBUG_LOG, logLine));
        break;
      case WARN:
        log.warn(String.format(logFormat, DEBUG_LOG, logLine));
        break;
      case ERROR:
        log.error(String.format(logFormat, DEBUG_LOG, logLine), ex);
        break;
      default:
    }
  }
}