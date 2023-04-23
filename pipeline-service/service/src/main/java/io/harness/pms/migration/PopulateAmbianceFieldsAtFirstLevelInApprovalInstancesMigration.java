/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.migration.NGMigration;
import io.harness.mongo.MongoConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.springdata.PersistenceUtils;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;

import com.google.inject.Inject;
import java.util.ArrayList;
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
 * Migration to add ambiance fields required in db queries at first level in Approval Instances.
 *
 * Granularity of the migration written according to approval Instances count in production at the time of writing the
 * migration
 *
 */
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class PopulateAmbianceFieldsAtFirstLevelInApprovalInstancesMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;
  private static final String DEBUG_LOG = "[ApprovalInstanceAmbianceMigration]: ";
  private static final int BATCH_SIZE = 500;
  private static final long CREATED_AT_LIMIT = 1650170397000L;
  private final RetryPolicy<Object> updateRetryPolicy = PersistenceUtils.getRetryPolicy(
      String.format("%s [Retrying]: Failed updating ApprovalInstances; attempt: {}", DEBUG_LOG),
      String.format("%s [Failed]: Failed updating ApprovalInstances; attempt: {}", DEBUG_LOG));

  @Override
  public void migrate() {
    int totalApprovalInstancesUpdated = 0;

    try {
      log.info(DEBUG_LOG + "Starting migration to first-level ambiance fields in Approval instances");

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

          if (!isEmpty(approvalInstance.getAccountId()) && !isEmpty(approvalInstance.getOrgIdentifier())
              && !isEmpty(approvalInstance.getProjectIdentifier()) && !isEmpty(approvalInstance.getPlanExecutionId())
              && !isEmpty(approvalInstance.getPipelineIdentifier())) {
            log.info(String.format(
                "%s Skipping since approval instance with identifier %s already has ambiance fields populated at first-level",
                DEBUG_LOG, approvalInstance.getId()));
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
        log.error(String.format("%s Failed trying to fetch approval Instances or executing migration", DEBUG_LOG), e);
      }

      if (EmptyPredicate.isNotEmpty(approvalInstancesToBeUpdatedInCurrentBatch)) {
        totalApprovalInstancesUpdated +=
            updateApprovalInstancesInBatchInternal(bulkOperations, approvalInstancesToBeUpdatedInCurrentBatch.size());
      }

      log.info(String.format(
          "%s Migration to first-level ambiance fields in Approval instances completed, total instances updated: %s",
          DEBUG_LOG, totalApprovalInstancesUpdated));

    } catch (Exception e) {
      log.error(
          String.format(
              "%s Migration to first-level ambiance fields in Approval instances failed, total instances updated: %s",
              DEBUG_LOG, totalApprovalInstancesUpdated),
          e);
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
        log.error(String.format(
            "%s during bulk update, %s instances failed to update", DEBUG_LOG, failedApprovalUpdateCount));
      } else if (failedApprovalUpdateCount == 0) {
        log.info(String.format(
            "%s successfully executed bulkUpdate on %s instances", DEBUG_LOG, successApprovalUpdateCount));
      }
    } catch (Exception ex) {
      log.error(String.format("%s Failed trying to execute bulk update and %s instances required to be updated",
                    DEBUG_LOG, currentBatchSize),
          ex);
    }
    return successApprovalUpdateCount;
  }

  public void addUpdateOperationForApprovalInstance(ApprovalInstance approvalInstance, BulkOperations bulkOps) {
    try {
      if (isNull(approvalInstance) || isNull(bulkOps)) {
        return;
      }
      Criteria idCriteria = Criteria.where(ApprovalInstanceKeys.id).is(approvalInstance.getId());
      Ambiance ambiance = approvalInstance.getAmbiance();

      if (isNull(ambiance)) {
        log.warn(String.format(
            "%s Skipping adding update operation for approval instance with identifier %s as ambiance is absent",
            DEBUG_LOG, approvalInstance.getId()));
        return;
      }

      Update update = new Update();
      update.set(ApprovalInstanceKeys.accountId, AmbianceUtils.getAccountId(ambiance));
      update.set(ApprovalInstanceKeys.orgIdentifier, AmbianceUtils.getOrgIdentifier(ambiance));
      update.set(ApprovalInstanceKeys.projectIdentifier, AmbianceUtils.getProjectIdentifier(ambiance));
      update.set(ApprovalInstanceKeys.planExecutionId, ambiance.getPlanExecutionId());
      update.set(ApprovalInstanceKeys.pipelineIdentifier, AmbianceUtils.getPipelineIdentifier(ambiance));

      bulkOps.updateOne(new Query(idCriteria), update);

    } catch (Exception exception) {
      log.error(String.format("%s Failed trying to add update operation for approval instance with identifier %s",
                    DEBUG_LOG, approvalInstance.getId()),
          exception);
    }
  }
}