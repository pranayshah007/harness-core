package io.harness.migrations.all;

import static io.harness.ng.DbAliases.DMS;
import static io.harness.ng.DbAliases.HARNESS;

import io.harness.configuration.DeployMode;
import io.harness.delegate.utils.DelegateDBMigrationFailed;
import io.harness.migration.DelegateMigrationFlag;
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

  long startTime;

  // agentMTLS, delegateRing collection are not used smp.
  private final List<String> entityList =
      Arrays.asList("versionOverride", "delegateConnectionResults", "delegateGroups", "delegateProfiles",
          "delegateRing", "delegateScopes", "delegateSequenceConfig", "delegateTokens", "delegates", "perpetualTask",
          "perpetualTaskScheduleConfig", "taskSelectorMaps", "delegateTasks");

  private final String DELEGATE_TASK = "delegateTasks";

  // Do not fail migration if migration of these collection fails.
  private final List<String> failSafeCollection = Arrays.asList("delegateConnectionResults");

  @Override
  public void migrate() throws DelegateDBMigrationFailed {
    // Ignore migration for SAAS
    if (!DeployMode.isOnPrem(DEPLOY_MODE)) {
      return;
    }

    log.info("DMS DB Migration started");

    startTime = System.currentTimeMillis();

    // Check if we already did the migration
    if (persistence.isMigrationEnabled(ON_PREM_MIGRATION_DONE)) {
      return;
    }

    // Create indexes and collections in DMS DB
    indexManager.ensureIndexes(
        IndexManager.Mode.AUTO, persistence.getDatastore(Store.builder().name(DMS).build()), morphia, dmsStore);

    try {
      for (String collection : entityList) {
        log.info("working for entity {}", collection);
        if (collection.equals(DELEGATE_TASK)) {
          if (checkIndexCount(collection)) {
            toggleFlag("delegateTask", true);
            Class<?> collectionClass = getClassForCollectionName(collection);
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
      throw new DelegateDBMigrationFailed(String.format("Delegate DB migration failed, exception %s", ex));
    }
    finishMigration();
  }

  private void migrateCollection(String collection) throws DelegateDBMigrationFailed {
    Class<?> collectionClass = getClassForCollectionName(collection);
    if (persistToNewDatabase(collection)) {
      log.info("Going to toggle flag");
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
      toggleFlag(collectionClass.getCanonicalName(), false);
      // Drop the collection from DMS DB.
      persistence.getCollection(dmsStore, collection).drop();
    }
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

    log.info("verifyWriteOperation, documents in new db and old db {}, {}", documentsInNewDB, insertCount);
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

    log.info("Value of new index and old are {}, {}", newCollection.getIndexInfo().size(),
        oldCollection.getIndexInfo().size());

    if (newCollection.getIndexInfo().size() != oldCollection.getIndexInfo().size()) {
      return false;
    }
    return true;
  }
}
