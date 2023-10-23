/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.mongo.MongoConfig;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus;
import io.harness.ng.core.entities.migration.NGManagerUniqueIdParentIdMigrationStatus.NGManagerUniqueIdParentIdMigrationStatusKeys;
import io.harness.persistence.UniqueIdAccess;
import io.harness.persistence.UniqueIdAware;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AddUniqueIdParentIdToEntitiesTask implements Runnable {
  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;
  private static final String LOCK_NAME_PREFIX = "NGEntitiesPeriodicMigrationTaskLock";
  private static final String NG_MANAGER_ENTITIES_MIGRATION_LOG = "[NGManagerAddUniqueIdAndParentIdToEntitiesTask]:";
  private static final int BATCH_SIZE = 500;
  private static final Set<Class<? extends UniqueIdAware>> entitiesSet = Set.of(Organization.class, Project.class);

  @Inject
  public AddUniqueIdParentIdToEntitiesTask(MongoTemplate mongoTemplate, PersistentLocker persistentLocker) {
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
  }

  @Override
  public void run() {
    log.info(format("%s starting...", NG_MANAGER_ENTITIES_MIGRATION_LOG));

    for (Class<? extends UniqueIdAware> clazz : entitiesSet) {
      NGManagerUniqueIdParentIdMigrationStatus foundEntity = mongoTemplate.findOne(
          new Query(Criteria.where(NGManagerUniqueIdParentIdMigrationStatusKeys.entityClassName).is(clazz.getName())),
          NGManagerUniqueIdParentIdMigrationStatus.class);
      if (foundEntity == null) {
        foundEntity = NGManagerUniqueIdParentIdMigrationStatus.builder()
                          .entityClassName(clazz.getName())
                          .parentIdMigrationCompleted(Boolean.FALSE)
                          .uniqueIdMigrationCompleted(Boolean.FALSE)
                          .build();
      }

      if (foundEntity.getUniqueIdMigrationCompleted()) {
        log.info(format("%s job for uniqueId on Entity Type: [%s] already completed.",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
      } else {
        performUniqueIdMigrationTask(foundEntity, clazz);
      }

      if (foundEntity.getParentIdMigrationCompleted()) {
        log.info(format("%s job for parentId on Entity Type: [%s] already completed.",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
      } else {
        performParentIdMigrationTask(foundEntity, clazz);
      }
    }
  }

  private void performUniqueIdMigrationTask(
      NGManagerUniqueIdParentIdMigrationStatus migrationStatusEntity, final Class<? extends UniqueIdAware> clazz) {
    log.info(format(
        "%s Starting uniqueId migration for entity: [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));

    int migratedCounter = 0;
    int batchSizeCounter = 0;
    int toUpdateCounter = 0;

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(format("%s failed to acquire lock for Entity type: [%s] during uniqueId migration task",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
        return;
      }
      try {
        Query documentQuery = new Query(new Criteria());
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);
        String idValue = null;
        try (CloseableIterator<? extends UniqueIdAware> iterator =
                 mongoTemplate.stream(documentQuery.limit(MongoConfig.NO_LIMIT).maxTimeMsec(MAX_VALUE), clazz)) {
          while (iterator.hasNext()) {
            UniqueIdAware entity = iterator.next();
            if (isEmpty(entity.getUniqueId())) {
              if (entity instanceof Project) {
                idValue = ((Project) entity).getId();
              } else if (entity instanceof Organization) {
                idValue = ((Organization) entity).getId();
              }
              if (isNotEmpty(idValue)) {
                toUpdateCounter++;
                batchSizeCounter++;
                Update update = new Update().set(UniqueIdAccess.UNIQUE_ID_KEY, UUIDGenerator.generateUuid());
                bulkOperations.updateOne(new Query(Criteria.where("_id").is(idValue)), update);
                if (batchSizeCounter == BATCH_SIZE) {
                  migratedCounter += bulkOperations.execute().getModifiedCount();
                  bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);
                  batchSizeCounter = 0;
                }
              }
            }
          }
          if (batchSizeCounter > 0) { // for the last remaining batch of entities
            migratedCounter += bulkOperations.execute().getModifiedCount();
          }
        } catch (Exception e) {
          log.error(format("%s job for uniqueId failed to iterate over entities of Entity Type [%s]",
                        NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
              e);
          return;
        }
      } catch (Exception exc) {
        log.error(format("%s job for uniqueId failed on Entity Type [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG,
                      clazz.getSimpleName()),
            exc);
        return;
      }
    }
    log.info(format("%s job on entity [%s] for uniqueId. Documents to Update: [%s], Successful: [%d], Failed: [%d]",
        NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), toUpdateCounter, migratedCounter,
        toUpdateCounter - migratedCounter));
    migrationStatusEntity.setUniqueIdMigrationCompleted(Boolean.TRUE);
    mongoTemplate.save(migrationStatusEntity);
  }

  private void performParentIdMigrationTask(
      NGManagerUniqueIdParentIdMigrationStatus foundEntity, final Class<? extends UniqueIdAware> clazz) {
    if (clazz == Organization.class && foundEntity.getEntityClassName() != null
        && foundEntity.getEntityClassName().equals(Organization.class.getName())) {
      performOrganizationParentIdMigrationTask(foundEntity);
    } else {
      performEntityParentIdMigrationTask(foundEntity, clazz);
    }
  }

  private void performEntityParentIdMigrationTask(
      NGManagerUniqueIdParentIdMigrationStatus foundEntity, final Class<? extends UniqueIdAware> clazz) {
    int migratedCounter = 0;
    int updateCounter = 0;
    int batchSizeCounter = 0;
    final String LOCAL_MAP_DELIMITER = "|";

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(format("%s failed to acquire lock for Entity type: [%s] during parentId migration task",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()));
        return;
      }
      try {
        final Map<String, String> orgIdentifierUniqueIdMap = new HashMap<>();

        Query documentQuery = new Query(new Criteria());
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, clazz);
        // iterate over all Project documents
        try (CloseableIterator<? extends UniqueIdAware> iterator =
                 mongoTemplate.stream(documentQuery.limit(NO_LIMIT).maxTimeMsec(MAX_VALUE), clazz)) {
          while (iterator.hasNext()) {
            UniqueIdAware nextEntity = iterator.next();
            if (nextEntity instanceof Project) {
              Project nextProject = (Project) nextEntity;
              if (isEmpty(nextProject.getParentId())) {
                updateCounter++;
                final String mapKey =
                    nextProject.getAccountIdentifier() + LOCAL_MAP_DELIMITER + nextProject.getOrgIdentifier();
                String uniqueIdOfOrg = null;
                // check if Org with uniqueId is present locally
                if (orgIdentifierUniqueIdMap.containsKey(mapKey)) {
                  uniqueIdOfOrg = orgIdentifierUniqueIdMap.get(mapKey);
                } else {
                  Criteria orgCriteria = Criteria.where("accountIdentifier")
                                             .is(nextProject.getAccountIdentifier())
                                             .and("identifier")
                                             .is(nextProject.getOrgIdentifier());
                  Organization organization = mongoTemplate.findOne(new Query(orgCriteria), Organization.class);
                  if (organization != null && isNotEmpty(organization.getUniqueId())) {
                    uniqueIdOfOrg = organization.getUniqueId();
                    orgIdentifierUniqueIdMap.put(mapKey, uniqueIdOfOrg);
                  }
                }

                if (isNotEmpty(uniqueIdOfOrg)) {
                  batchSizeCounter++;
                  Update update = new Update().set(ProjectKeys.parentId, uniqueIdOfOrg);
                  bulkOperations.updateOne(new Query(Criteria.where("_id").is(nextProject.getId())), update);

                  if (batchSizeCounter == BATCH_SIZE) {
                    migratedCounter += bulkOperations.execute().getModifiedCount();
                    bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Project.class);
                    batchSizeCounter = 0;
                  }
                }
              }
            }
          }
          if (batchSizeCounter > 0) { // for the last remaining batch of entities
            migratedCounter += bulkOperations.execute().getModifiedCount();
          }
        } catch (Exception exc) {
          log.error(format("%s task failed to iterate over entities of Entity Type: [%s]",
                        NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
              exc);
          return;
        }
      } catch (Exception exc) {
        log.error(
            format("%s task failed for Entity Type [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName()),
            exc);
        return;
      }
      log.info(format("%s task on entity [%s] for parentId. Successful: [%d], Failed: [%d]",
          NG_MANAGER_ENTITIES_MIGRATION_LOG, clazz.getSimpleName(), migratedCounter, updateCounter - migratedCounter));
      foundEntity.setParentIdMigrationCompleted(Boolean.TRUE);
      mongoTemplate.save(foundEntity);
    }
  }

  private void performOrganizationParentIdMigrationTask(NGManagerUniqueIdParentIdMigrationStatus foundEntity) {
    int migratedCounter = 0;
    int updateCounter = 0;
    int batchSizeCounter = 0;

    try (AcquiredLock<?> lock =
             persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME_PREFIX, Duration.ofSeconds(5))) {
      if (lock == null) {
        log.info(format("%s failed to acquire lock for Entity type: [%s] during parentId migration task",
            NG_MANAGER_ENTITIES_MIGRATION_LOG, "Organization"));
        return;
      }
      try {
        Query documentQuery = new Query(new Criteria());

        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Organization.class);

        String idValue = null;
        try (CloseableIterator<Organization> iterator =
                 mongoTemplate.stream(documentQuery.limit(NO_LIMIT).maxTimeMsec(MAX_VALUE), Organization.class)) {
          while (iterator.hasNext()) {
            Organization nextOrg = iterator.next();
            if (null != nextOrg && isEmpty(nextOrg.getParentId())) {
              idValue = nextOrg.getId();
              updateCounter++;
              batchSizeCounter++;
              Update update = new Update().set(OrganizationKeys.parentId, nextOrg.getAccountIdentifier());
              bulkOperations.updateOne(new Query(Criteria.where("_id").is(idValue)), update);
              if (batchSizeCounter == 500) {
                migratedCounter += bulkOperations.execute().getModifiedCount();
                bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Organization.class);
                batchSizeCounter = 0;
              }
            }
          }
          if (batchSizeCounter > 0) { // for the last remaining batch of entities
            migratedCounter += bulkOperations.execute().getModifiedCount();
          }
        } catch (Exception exc) {
          log.error(
              format("%s job failed for Entity Type [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG, "Organization"), exc);
          return;
        }
      } catch (Exception exc) {
        log.error(format("%s job failed for Entity Type [%s]", NG_MANAGER_ENTITIES_MIGRATION_LOG, "Organization"), exc);
        return;
      }
    }
    log.info(format("%s task on entity [%s] for parentId. Successful: [%d], Failed: [%d]",
        NG_MANAGER_ENTITIES_MIGRATION_LOG, "Organization", migratedCounter, updateCounter - migratedCounter));
    foundEntity.setParentIdMigrationCompleted(Boolean.TRUE);
    mongoTemplate.save(foundEntity);
  }
}
