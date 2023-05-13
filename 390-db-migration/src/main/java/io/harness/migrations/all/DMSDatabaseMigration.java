/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.ng.DbAliases.DMS;
import static io.harness.ng.DbAliases.HARNESS;

import io.harness.configuration.DeployMode;
import io.harness.delegate.utils.DelegateDBMigrationFailed;
import io.harness.migration.DelegateMigrationFlag;
import io.harness.migration.DelegateMigrationFlag.DelegateMigrationFlagKeys;
import io.harness.migrations.Migration;
import io.harness.migrations.SeedDataMigration;
import io.harness.mongo.IndexManager;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.store.Store;

import com.google.inject.Inject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import dev.morphia.Morphia;
import dev.morphia.query.Query;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DMSDatabaseMigration implements Migration, SeedDataMigration {
  @Inject private HPersistence persistence;
  @Inject IndexManager indexManager;
  @Inject Morphia morphia;

  final Store dmsStore = Store.builder().name(DMS).build();
  final Store harnessStore = Store.builder().name(HARNESS).build();
  private static final String ON_PREM_MIGRATION_DONE = "onPremMigrationDone";

  private static final String MIGRATION_FAIL_EXCEPTION_FORMAT = "Delegate DB migration failed for collection: %s";

  private static final String DEPLOY_MODE = System.getenv(DeployMode.DEPLOY_MODE);

  private long startTime;

  private final List<String> entityList = Arrays.asList("agentMtlsEndpoint", "versionOverride", "taskSelectorMaps",
      "perpetualTaskScheduleConfig", "delegateConnectionResults", "delegateProfiles", "delegateRing", "delegateScopes",
      "delegateSequenceConfig", "delegateTokens", "delegates", "perpetualTask", "delegateGroups", "delegateTasks");

  private final String DELEGATE_TASK = "delegateTasks";

  // Do not fail migration if migration of these collection fails.
  private final List<String> failSafeCollection = Arrays.asList("delegateConnectionResults");

  @Override
  public void migrate() {
    //     Ignore migration for SAAS
    if (!DeployMode.isOnPrem(DEPLOY_MODE)) {
      return;
    }

    log.info("DMS DB Migration started");

    startTime = System.currentTimeMillis();

    // Check if we already did the migration
    if (persistence.isMigrationEnabled(ON_PREM_MIGRATION_DONE)) {
      return;
    }

    try {
      // Create indexes and collections in DMS DB
      // If we enter wrong DMS_MONGO_URI in config, ensureIndexes function will fail to create indexes.
      indexManager.ensureIndexes(
          IndexManager.Mode.AUTO, persistence.getDatastore(Store.builder().name(DMS).build()), morphia, dmsStore);
      for (String collection : entityList) {
        log.info("working for entity {}", collection);
        // Check if the migratian if already toggled. If yes, don't proceed.
        Class<?> collectionClass = getClassForCollectionName(collection);
        DelegateMigrationFlag delegateMigrationFlag =
            persistence.createQuery(DelegateMigrationFlag.class)
                .filter(DelegateMigrationFlagKeys.className, collectionClass.getCanonicalName())
                .get();
        if (delegateMigrationFlag != null && delegateMigrationFlag.isEnabled()) {
          continue;
        }
        if (collection.equals(DELEGATE_TASK)) {
          if (checkIndexCount(collection)) {
            toggleFlag("delegateTask", true);
            if (!postToggleCorrectness(collectionClass)) {
              throw new DelegateDBMigrationFailed(String.format(MIGRATION_FAIL_EXCEPTION_FORMAT, collection));
            }
          } else {
            throw new DelegateDBMigrationFailed(String.format(MIGRATION_FAIL_EXCEPTION_FORMAT, collection));
          }
        } else {
          try {
            // Retry once per collection migration failure.
            migrateCollection(collection);
          } catch (Exception ex) {
            log.warn("Migration for collection {} failed in attempt 1 with exception {}", collection, ex);
            migrateCollection(collection);
          }
        }
      }
    } catch (Exception ex) {
      rollback();
      log.error(String.format("Delegate DB migration failed, exception %s", ex));
      System.exit(1);
    }
    finishMigration();
  }

  private void migrateCollection(String collection) throws DelegateDBMigrationFailed {
    Class<?> collectionClass = getClassForCollectionName(collection);
    if (persistToNewDatabase(collection)) {
      log.info("Toggling flag for collection {}", collection);
      toggleFlag(collectionClass.getCanonicalName(), true);
      if (!postToggleCorrectness(collectionClass)) {
        throw new DelegateDBMigrationFailed(String.format(MIGRATION_FAIL_EXCEPTION_FORMAT, collection));
      }
    } else {
      // only throw exception if collection is not fail safe.
      if (!failSafeCollection.contains(collection)) {
        throw new DelegateDBMigrationFailed(String.format(MIGRATION_FAIL_EXCEPTION_FORMAT, collection));
      }
    }
  }

  private void finishMigration() {
    // Migration is done, set on_prem flag as true.
    DelegateMigrationFlag onPremMigrationFlag = new DelegateMigrationFlag(ON_PREM_MIGRATION_DONE, true);
    persistence.save(onPremMigrationFlag);
    log.info("time taken to finish migration {}", System.currentTimeMillis() - startTime);
  }

  private void rollback() {
    log.info("Initiating rollback for db migration");
    for (String collection : entityList) {
      Class<?> collectionClass = getClassForCollectionName(collection);
      // This is because for delegate task we don't save canonical name in migrationFlag database.
      if (collection.equals(DELEGATE_TASK)) {
        toggleFlag("delegateTask", false);
      } else {
        toggleFlag(collectionClass.getCanonicalName(), false);
      }
      // Drop the collection from DMS DB.
      persistence.getCollection(dmsStore, collection).drop();
    }
    // reverting ON_PREM_MIGRATION_DONE flag to false.
    DelegateMigrationFlag onPremMigrationFlag = new DelegateMigrationFlag(ON_PREM_MIGRATION_DONE, false);
    persistence.save(onPremMigrationFlag);
  }

  private boolean persistToNewDatabase(String collection) {
    BulkWriteOperation bulkWriteOperation =
        persistence.getCollection(dmsStore, collection).initializeUnorderedBulkOperation();

    Query<PersistentEntity> query = persistence.createQueryForCollection(collection);
    long documentCountBeforeMigration = query.count();
    int insertDocCount = 0;

    try (HIterator<PersistentEntity> records = new HIterator<>(query.fetch())) {
      for (PersistentEntity record : records) {
        insertDocCount++;
        try {
          bulkWriteOperation.insert(morphia.toDBObject(record));
          if (insertDocCount % 1000 == 0) {
            bulkWriteOperation.execute();
            // re-intialize bulk write object
            bulkWriteOperation = persistence.getCollection(dmsStore, collection).initializeUnorderedBulkOperation();
          }
        } catch (Exception ex) {
          log.warn("Exception occured while copying data", ex);
        }
      }
    }
    if (insertDocCount % 1000 != 0) {
      try {
        bulkWriteOperation.execute();
      } catch (Exception ex) {
        // This will have many duplicate key exception during bulkwrite.execute by different pods but it's expected.
        log.warn("Exception occured while copying data", ex);
      }
    }
    return verifyWriteOperation(documentCountBeforeMigration, collection);
  }

  private boolean postToggleCorrectness(Class<?> cls) {
    // invalidate cache and ensure that data is coming from new DB.
    persistence.invalidateCacheAndPut(cls.getCanonicalName());
    Store store = persistence.getStore(cls);
    log.info("post toggle correctness for collection {} is {}", cls.getCanonicalName(), store.getName().equals(DMS));
    return store.getName().equals(DMS);
  }

  private void toggleFlag(String cls, boolean value) {
    log.info("Toggling flag to {} for {}", value, cls);
    DelegateMigrationFlag flag = new DelegateMigrationFlag(cls, value);
    persistence.save(flag);
  }

  private boolean verifyWriteOperation(long insertCount, String collection) {
    // Check if entire data is written
    // dont check based on bulkWriteResult because now copy is running on multiple machines, so this value will vary.

    long documentsInNewDB = persistence.getCollection(dmsStore, collection).count();

    log.info("documents in new db and old db {}, {} for collection {}", documentsInNewDB, insertCount, collection);
    boolean insertSuccessful = documentsInNewDB == insertCount;
    boolean isIndexCountSame = checkIndexCount(collection);

    if (!insertSuccessful || !isIndexCountSame) {
      return false;
    }
    // Check that data is coming from old DB
    Store store = persistence.getStore(getClassForCollectionName(collection));

    log.info("Value of store in collection {}", store.getName());

    return store.getName().equals(HARNESS);
  }

  Class<?> getClassForCollectionName(String collectionName) {
    return morphia.getMapper().getClassFromCollection(collectionName);
  }

  private boolean checkIndexCount(String collection) {
    final DBCollection newCollection = persistence.getCollection(dmsStore, collection);
    final DBCollection oldCollection = persistence.getCollection(harnessStore, collection);

    log.info("Value of new index and old are {}, {} for collection {}", newCollection.getIndexInfo().size(),
        oldCollection.getIndexInfo().size(), collection);

    return newCollection.getIndexInfo().size() == oldCollection.getIndexInfo().size();
  }
}
