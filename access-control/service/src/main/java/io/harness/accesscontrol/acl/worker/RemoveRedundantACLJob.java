/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.worker;

import static io.harness.authorization.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;

import static java.lang.Integer.MAX_VALUE;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.by;
import static org.springframework.data.mongodb.core.BulkOperations.BulkMode.UNORDERED;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.ACL.ACLKeys;
import io.harness.accesscontrol.acl.persistence.ACLOptimizationMigrationOffset;
import io.harness.accesscontrol.acl.persistence.ACLOptimizationMigrationOffset.ACLOptimizationMigrationOffsetKey;
import io.harness.accesscontrol.acl.persistence.RemoveRedundantACLJobState;
import io.harness.accesscontrol.acl.persistence.RemoveRedundantACLJobState.RemoveRedundantACLJobStateKey;
import io.harness.accesscontrol.permissions.persistence.repositories.InMemoryPermissionRepository;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class RemoveRedundantACLJob implements Runnable {
  private static final int BATCH_SIZE = 1000;
  public static final String REFERENCE_TIMESTAMP = "000000000000000000000000";
  private static final String DEBUG_MESSAGE = "[RemoveRedundantACLJob] ";
  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;
  private final InMemoryPermissionRepository inMemoryPermissionRepository;
  private static final String LOCK_NAME = "RemoveRedundantACLJob";

  @Inject
  public RemoveRedundantACLJob(MongoTemplate mongoTemplate, PersistentLocker persistentLocker,
      InMemoryPermissionRepository inMemoryPermissionRepository) {
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
    this.inMemoryPermissionRepository = inMemoryPermissionRepository;
  }

  @Override
  public void run() {
    log.info(DEBUG_MESSAGE + "started...");
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, ofSeconds(5))) {
      if (lock == null) {
        log.info(DEBUG_MESSAGE + "failed to acquire lock");
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(ACCESS_CONTROL_SERVICE.getServiceId()));
        log.info(DEBUG_MESSAGE + "Setting SecurityContext completed.");
        execute();
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
        log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
    log.info(DEBUG_MESSAGE + " completed...");
  }

  @VisibleForTesting
  void execute() {
    try {
      // Check if Job already ran successfully
      if (isJobAlreadyExecutedSuccessfully()) {
        return;
      }

      // First remove all disabled ACLs
      try (CloseableIterator<ACL> iterator = runQueryWithBatchForDisabledAcls()) {
        String offset = null;
        int totalRemoved = 0;
        BulkOperations bulkOperations = mongoTemplate.bulkOps(UNORDERED, ACL.class);

        while (iterator.hasNext()) {
          ACL acl = iterator.next();
          offset = acl.getId();

          // Create a query to remove the ACL
          Query query = new Query();
          query.addCriteria(where(ACLKeys.id).is(acl.getId()));
          bulkOperations.remove(query);

          totalRemoved++;

          // Check if it's time to execute the bulk removal
          if (totalRemoved % BATCH_SIZE == 0) {
            log.info(DEBUG_MESSAGE + "Removing disabled ACLs. total: {}, removed: {}", bulkOperations.execute(),
                totalRemoved);

            // Reset bulkOperations for the next batch
            bulkOperations = mongoTemplate.bulkOps(UNORDERED, ACL.class);

            // Update the offset
            updateOffset(offset);

            // Take a pause
            Thread.sleep(1000);
          }
        }

        // Execute any remaining removals
        if (totalRemoved % BATCH_SIZE != 0) {
          log.info(
              DEBUG_MESSAGE + "Removing disabled ACLs. total: {}, removed: {}", bulkOperations.execute(), totalRemoved);
        }

        // Update the offset
        if (offset != null) {
          updateOffset(offset);
        }
      }

      // We already marked ACLs disabled and have offset for last disabled ACL
      // Using that offset to start removing redundant ACLs which got created after this offset
      try (CloseableIterator<ACL> iterator = runQueryWithBatch()) {
        String offset = null;
        int totalRemoved = 0;
        BulkOperations bulkOperations = mongoTemplate.bulkOps(UNORDERED, ACL.class);

        while (iterator.hasNext()) {
          ACL acl = iterator.next();
          offset = acl.getId();

          Query query = new Query().addCriteria(where(ACLKeys.id).is(acl.getId()));

          if (!inMemoryPermissionRepository.isPermissionCompatibleWithResourceSelector(
                  acl.getPermissionIdentifier(), acl.getResourceSelector())) {
            bulkOperations.remove(query);
            totalRemoved++;
          }

          if (totalRemoved != 0 && totalRemoved % BATCH_SIZE == 0) {
            log.info(DEBUG_MESSAGE + "Removing redundant ACLs. total: {}, removed: {}", bulkOperations.execute(),
                totalRemoved);
            bulkOperations = mongoTemplate.bulkOps(UNORDERED, ACL.class);

            updateOffset(offset);
            Thread.sleep(1000);
          }
        }

        if (totalRemoved % BATCH_SIZE != 0) {
          log.info(DEBUG_MESSAGE + "Removing redundant ACLs. total: {}, removed: {}", bulkOperations.execute(),
              totalRemoved);
        }

        if (offset != null) {
          updateOffset(offset);
        }

        markJobAsSuccessful();
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + "Exception occurred while processing ACLs ", ex);
    }
  }

  private void updateOffset(String lastProcessedAclId) {
    Update update = new Update().set(RemoveRedundantACLJobStateKey.offset, lastProcessedAclId);
    mongoTemplate.findAndModify(new Query(), update, RemoveRedundantACLJobState.class);
  }

  private void markJobAsSuccessful() {
    Update update = new Update().set(RemoveRedundantACLJobStateKey.jobCompleted, true);
    mongoTemplate.findAndModify(new Query(), update, RemoveRedundantACLJobState.class);
  }

  private boolean isJobAlreadyExecutedSuccessfully() {
    RemoveRedundantACLJobState state = mongoTemplate.findOne(
        new Query().with(by(ASC, RemoveRedundantACLJobStateKey.id)).limit(1), RemoveRedundantACLJobState.class);
    if (nonNull(state)) {
      return state.isJobCompleted();
    }
    return false;
  }

  private CloseableIterator<ACL> runQueryWithBatch() {
    String offset = REFERENCE_TIMESTAMP;
    ACLOptimizationMigrationOffset state = mongoTemplate.findOne(
        new Query().with(by(ASC, ACLOptimizationMigrationOffsetKey.id)).limit(1), ACLOptimizationMigrationOffset.class);

    if (isNull(state)) {
      ACL firstAcl = mongoTemplate.findOne(new Query().with(by(ASC, ACLKeys.id)).limit(1), ACL.class);

      if (nonNull(firstAcl)) {
        offset = firstAcl.getId();
      }
      mongoTemplate.save(RemoveRedundantACLJobState.builder().offset(offset).build());
    } else {
      offset = state.getOffset();
    }

    Query aclQuery = new Query();
    aclQuery.addCriteria(where(ACLKeys.id).gte(new ObjectId(offset)));
    aclQuery.cursorBatchSize(BATCH_SIZE);
    aclQuery.maxTimeMsec(MAX_VALUE);
    return mongoTemplate.stream(aclQuery, ACL.class);
  }

  private CloseableIterator<ACL> runQueryWithBatchForDisabledAcls() {
    String offset = REFERENCE_TIMESTAMP;
    RemoveRedundantACLJobState state = mongoTemplate.findOne(
        new Query().with(by(ASC, RemoveRedundantACLJobStateKey.id)).limit(1), RemoveRedundantACLJobState.class);

    if (isNull(state)) {
      ACL firstAcl = mongoTemplate.findOne(new Query().with(by(ASC, ACLKeys.id)).limit(1), ACL.class);

      if (nonNull(firstAcl)) {
        offset = firstAcl.getId();
      }
      mongoTemplate.save(RemoveRedundantACLJobState.builder().offset(offset).build());
    } else {
      offset = state.getOffset();
    }

    Query aclQuery = new Query();
    aclQuery.addCriteria(where(ACLKeys.id).gte(new ObjectId(offset)).and(ACLKeys.enabled).is(false));
    aclQuery.cursorBatchSize(BATCH_SIZE);
    aclQuery.maxTimeMsec(MAX_VALUE);
    return mongoTemplate.stream(aclQuery, ACL.class);
  }
}
