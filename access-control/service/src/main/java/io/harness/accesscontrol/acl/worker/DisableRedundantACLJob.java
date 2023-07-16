/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.worker;

import static io.harness.authorization.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.by;
import static org.springframework.data.mongodb.core.BulkOperations.BulkMode.UNORDERED;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.ACL.ACLKeys;
import io.harness.accesscontrol.acl.persistence.ACLOptimizationMigrationOffset;
import io.harness.accesscontrol.acl.persistence.ACLOptimizationMigrationOffset.ACLOptimizationMigrationOffsetKey;
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
public class DisableRedundantACLJob implements Runnable {
  private static final int BATCH_SIZE = 1000;
  public static final String REFERENCE_TIMESTAMP = "000000000000000000000000";
  private static final String DEBUG_MESSAGE = "[DisableRedundantACLJob] ";
  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;
  private final InMemoryPermissionRepository inMemoryPermissionRepository;
  private static final String LOCK_NAME = "DisableRedundantACLJob";

  @Inject
  public DisableRedundantACLJob(MongoTemplate mongoTemplate, PersistentLocker persistentLocker,
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
      try (CloseableIterator<ACL> iterator = runQueryWithBatch()) {
        String offset = REFERENCE_TIMESTAMP;
        int totalUpdated = 0;
        int totalDisabled = 0;
        int totalEnabled = 0;
        BulkOperations bulkOperations = mongoTemplate.bulkOps(UNORDERED, ACL.class);
        while (iterator.hasNext()) {
          ACL acl = iterator.next();
          offset = acl.getId();
          Query query = new Query();
          query.addCriteria(where(ACLKeys.id).is(acl.getId()));
          if (!inMemoryPermissionRepository.isPermissionCompatibleWithResourceSelector(
                  acl.getPermissionIdentifier(), acl.getResourceSelector())) {
            bulkOperations.updateOne(query, update(ACLKeys.enabled, false));
            totalDisabled++;
          } else {
            bulkOperations.updateOne(query, update(ACLKeys.enabled, true));
            totalEnabled++;
          }

          totalUpdated++;
          if (totalUpdated >= BATCH_SIZE) {
            log.info("Updated total {} ACLs. disabled: {}, enabled: {}", bulkOperations.execute(), totalDisabled,
                totalEnabled);
            bulkOperations = mongoTemplate.bulkOps(UNORDERED, ACL.class);
            totalUpdated = 0;
            updateOffset(offset);
          }
        }
        if (totalUpdated != 0) {
          log.info(
              "Updated total {} ACLs disabled: {}, enabled: {}", bulkOperations.execute(), totalDisabled, totalEnabled);
          updateOffset(offset);
        }
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + "Exception occurred while processing ACLs ", ex);
    }
  }

  private void updateOffset(String lastProcessedAclId) {
    Update update = new Update().set(ACLOptimizationMigrationOffsetKey.offset, lastProcessedAclId);
    mongoTemplate.findAndModify(new Query(), update, ACLOptimizationMigrationOffset.class);
  }

  private CloseableIterator<ACL> runQueryWithBatch() {
    String offset = REFERENCE_TIMESTAMP;
    ACLOptimizationMigrationOffset existingOffset = mongoTemplate.findOne(
        new Query().with(by(ASC, ACLOptimizationMigrationOffsetKey.id)).limit(1), ACLOptimizationMigrationOffset.class);

    if (isNull(existingOffset)) {
      ACL firstAcl = mongoTemplate.findOne(new Query().with(by(ASC, ACLKeys.id)).limit(1), ACL.class);

      if (nonNull(firstAcl)) {
        offset = firstAcl.getId();
      }
      mongoTemplate.save(ACLOptimizationMigrationOffset.builder().offset(offset).build());
    } else {
      offset = existingOffset.getOffset();
    }

    Query aclQuery = new Query();
    aclQuery.addCriteria(where(ACLKeys.id).gt(new ObjectId(offset)));
    aclQuery.cursorBatchSize(BATCH_SIZE);
    return mongoTemplate.stream(aclQuery, ACL.class);
  }
}
