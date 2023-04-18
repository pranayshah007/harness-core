package io.harness.migrations.all;

import static io.harness.ng.DbAliases.DMS;
import static io.harness.ng.DbAliases.HARNESS;

import io.harness.configuration.DeployMode;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.VersionOverride;
import io.harness.delegate.beans.VersionOverrideType;
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
import com.mongodb.BulkWriteResult;
import com.mongodb.DBCollection;
import dev.morphia.Morphia;
import dev.morphia.query.Query;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DMSDatabaseMigration implements Migration, SeedDataMigration {
  @Inject private HPersistence persistence;

  @Inject IndexManager indexManager;

  // At some places we need collection Name which is entity Name
  // At some places we need class path, esp when we toggle flag in migration collection.

  @Inject Morphia morphia;

  @Inject private ExecutorService executorService;

  final Store dmsStore = Store.builder().name(DMS).build();
  private static final String ON_PREM_MIGRATION = "onPremMigration";

  private static final String DEPLOY_MODE = System.getenv(DeployMode.DEPLOY_MODE);

  //  private final List<String> classesHavingMigrationEnabled =
  //          Arrays.asList(
  //                  "io.harness.delegate.beans.VersionOverride");

  private final List<String> entityList = Arrays.asList("versionOverride");

  //  private final List<Class<?>> classesHavingMigrationEnabled =
  //      Arrays.asList(DelegateConnectionResult.class, DelegateGroup.class, DelegateProfile.class, DelegateRing.class,
  //          DelegateScope.class, DelegateSequenceConfig.class, DelegateToken.class, Delegate.class,
  //          PerpetualTaskRecord.class, PerpetualTaskScheduleConfig.class, VersionOverride.class,
  //          TaskSelectorMap.class);

  //  private final List<Class> classesHavingMigrationEnabled = Arrays.asList(VersionOverride.class);

  @Override
  public void migrate() throws DelegateDBMigrationFailed {
    // Ignore migration for SAAS
    //    if (!DeployMode.isOnPrem(DEPLOY_MODE)) {
    //      return;
    //    }

    log.info("DMS DB Migration started");

    // Putting some entries to VersionOverride collection for testing.
    for (int i = 0; i < 50; i++) {
      String override = "a";
      VersionOverride versionOverride = VersionOverride.builder("AccountID")
                                            .overrideType(VersionOverrideType.DELEGATE_IMAGE_TAG)
                                            .version(override.concat(Integer.toString(i)))
                                            .build();
      log.info(" Added entry to db for override {}", persistence.save(versionOverride));
    }

    // We need to ensure that all pods end in same state after migration runs.
    // It shouln't happen that migration succeeded for one pod and failed for another.
    // Migration should be atomic for all pods.

    //    // spawning one more thread to call migrate there too.
    //    executorService.submit(() -> {
    //      try {
    //        migrate();
    //      } catch (Exception e) {
    //        log.error("Failed to Migrate");
    //      }
    //    });

    // Check if we already did the migration
    // This is for case if we run the migration again.
    if (persistence.isMigrationEnabled(ON_PREM_MIGRATION)) {
      return;
    }

    // Create indexes and collections in DMS DB
    indexManager.ensureIndexes(
        IndexManager.Mode.AUTO, persistence.getDatastore(Store.builder().name(DMS).build()), morphia, dmsStore);

    for (String collection : entityList) {
      // Only proceed if migration is not started by another process.
      // I think we don't need this, duplicate records will not be inserted anyways in DB and bulk write will not throw
      // exception on duplicate writes.

      //      if (!smpMigrationStartedForCollection(collection)) {
      //        // Set migration started flag in DB
      //        DelegateMigrationFlag delegateMigrationFlagToSave =
      //            new DelegateMigrationFlag(collection.getName(), false, true);
      //        persistence.save(delegateMigrationFlagToSave);

      // Write to collection in DMS DB
      // If write is successful, follow next set to steps.
      // In case of rollback, drop the created collection.
      log.info("About to copy data to new collection");
      if (persistToNewDatabase(collection)) {
        log.info("Going to toggle flag");
        // String className = classesHavingMigrationEnabled.get(0);
        Class<?> collectionClass = getClassForCollectionName(collection);
        toggleFlag(collectionClass.getCanonicalName(), true, true);
        // Check if we reading data from DMS DB after toggle.
        // If we are still reading from old db, initiate rollback.
        if (!postToggleCorrectness(collectionClass)) {
          rollback();
          throw new DelegateDBMigrationFailed(
              String.format("Delegate DB migration failed for collection: {}", collection));
        }
      } else {
        // writing data failed, hence drop the collection and end migration.
        rollback();
        throw new DelegateDBMigrationFailed(
            String.format("Delegate DB migration failed for collection: {}", collection));
      }
    }
    // Migration is done, set on_prem flag as true.
    DelegateMigrationFlag onPremMigrationFlag = new DelegateMigrationFlag(ON_PREM_MIGRATION, true, true);
    persistence.save(onPremMigrationFlag);
  }

  private void rollback() {
    for (String collection : entityList) {
      Class<?> collectionClass = getClassForCollectionName(collection);
      toggleFlag(collectionClass.getCanonicalName(), false, false);
      // Drop the collection from DMS DB.
      persistence.getCollection(dmsStore, collection).drop();
    }
  }

  private boolean persistToNewDatabase(String collection) {
    BulkWriteOperation bulkWriteOperation =
        persistence.getCollection(dmsStore, collection).initializeUnorderedBulkOperation();

    Query<PersistentEntity> query = persistence.createQueryForCollection(collection);
    int insertDocCount = 0;

    try (HIterator<PersistentEntity> records = new HIterator<>(query.fetch())) {
      for (PersistentEntity record : records) {
        insertDocCount++;
        log.info("Delegate migration info {}", record.toString());
        bulkWriteOperation.insert(morphia.toDBObject(record));
      }
    }
    return verifyWriteOperation(bulkWriteOperation.execute(), insertDocCount);
  }

  private boolean postToggleCorrectness(Class<?> cls) {
    // invalidate cache and ensure that data is coming from new DB.
    persistence.invalidateCacheAndPut(cls.getCanonicalName());
    Store store = persistence.getStore(cls);
    if (!store.equals(DMS)) {
      return false;
    }
    return true;
  }

  private void toggleFlag(String cls, boolean value, boolean smpMigrationEnabled) {
    DelegateMigrationFlag flag = new DelegateMigrationFlag(cls, value, smpMigrationEnabled);
    persistence.save(flag);
  }

  private boolean verifyWriteOperation(BulkWriteResult bulkWriteResult, int insertCount) {
    // Check if entire data is written
    // dont check based on bulkWriteResult because now copy is running on multiple machines, so this value will vary.
    boolean insertSuccessful = bulkWriteResult.isAcknowledged() && bulkWriteResult.getInsertedCount() == insertCount;
    if (!insertSuccessful) {
      return false;
    }

    // Check index count
    final DBCollection newCollection = persistence.getCollection(dmsStore, "delegates");
    final DBCollection oldCollection = persistence.getCollection(Delegate.class);
    if (newCollection.getIndexInfo().size() != oldCollection.getIndexInfo().size()) {
      return false;
    }

    // Check that data is coming from old DB
    Store store = persistence.getStore(Delegate.class);
    if (!store.equals(HARNESS)) {
      return false;
    }

    return true;
  }

  private boolean smpMigrationStartedForCollection(Class<?> collection) {
    DelegateMigrationFlag delegateMigrationFlag =
        persistence.createQuery(DelegateMigrationFlag.class)
            .filter(DelegateMigrationFlag.DelegateMigrationFlagKeys.className, collection.getName())
            .get();
    return delegateMigrationFlag.isOnPremMigrationTriggered();
  }

  Class<?> getClassForCollectionName(String collectionName) {
    return morphia.getMapper().getClassFromCollection(collectionName);
  }
}
