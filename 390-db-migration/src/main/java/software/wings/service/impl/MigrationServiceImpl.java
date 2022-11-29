/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.maintenance.MaintenanceController.getMaintenanceFlag;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.Schema.SCHEMA_ID;

import static java.time.Duration.ofMinutes;
import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.concurrent.HTimeLimiter;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.migrations.Migration;
import io.harness.migrations.MigrationBackgroundList;
import io.harness.migrations.MigrationList;
import io.harness.migrations.OnPrimaryManagerMigration;
import io.harness.migrations.OnPrimaryManagerMigrationList;
import io.harness.migrations.SeedDataMigration;
import io.harness.migrations.SeedDataMigrationList;
import io.harness.migrations.TimeScaleDBDataMigration;
import io.harness.migrations.TimeScaleDBMigration;
import io.harness.migrations.TimescaleDBDataMigrationList;
import io.harness.migrations.TimescaleDBMigrationList;

import software.wings.beans.Account;
import software.wings.beans.Schema;
import software.wings.core.managerConfiguration.ConfigurationController;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.MigrationService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class MigrationServiceImpl implements MigrationService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private PersistentLocker persistentLocker;
  @Inject private Injector injector;
  @Inject private ExecutorService executorService;
  @Inject private TimeLimiter timeLimiter;
  @Inject private ConfigurationController configurationController;

  @Override
  public void runMigrations() {
    Map<Integer, Class<? extends Migration>> migrations =
        MigrationList.getMigrations().stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    int maxVersion = migrations.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

    Map<Integer, Class<? extends Migration>> backgroundMigrations =
        MigrationBackgroundList.getMigrations().stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    int maxBackgroundVersion = backgroundMigrations.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

    Map<Integer, Class<? extends SeedDataMigration>> seedDataMigrations =
        SeedDataMigrationList.getMigrations().stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    int maxSeedDataVersion = seedDataMigrations.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

    Map<Integer, Class<? extends TimeScaleDBMigration>> timescaleDBMigrations =
        TimescaleDBMigrationList.getMigrations().stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    int maxTimeScaleDBMigration = timescaleDBMigrations.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

    Map<Integer, Class<? extends TimeScaleDBDataMigration>> backgroundTimeScaleDBDataMigrations =
        TimescaleDBDataMigrationList.getMigrations().stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    int maxTimeScaleDBDataMigration =
        backgroundTimeScaleDBDataMigrations.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

    final Map<Integer, Class<? extends OnPrimaryManagerMigration>> onPrimaryManagerMigrationMap =
        OnPrimaryManagerMigrationList.getMigrations().stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    int maxOnPrimaryManagerMDBDataMigration =
        onPrimaryManagerMigrationMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

    try (
        AcquiredLock lock = persistentLocker.waitToAcquireLock(Schema.class, SCHEMA_ID, ofMinutes(25), ofMinutes(27))) {
      if (lock == null) {
        throw new WingsException(ErrorCode.GENERAL_ERROR)
            .addParam("message", "The persistent lock was not acquired. That very unlikely, but yet it happened.");
      }
      log.info("[Migration] - Initializing Global DB Entries");
      initializeGlobalDbEntriesIfNeeded();

      log.info("[Migration] - Checking for new migrations");
      Schema schema = wingsPersistence.createQuery(Schema.class).get();

      if (schema == null) {
        schema = Schema.builder()
                     .version(maxVersion)
                     .backgroundVersion(maxBackgroundVersion)
                     .seedDataVersion(0)
                     .timescaleDbVersion(0)
                     .timescaleDBDataVersion(0)
                     .onPrimaryManagerVersion(0)
                     .build();
        wingsPersistence.save(schema);
      }

      scheduleOnPrimaryMigrations(schema, maxOnPrimaryManagerMDBDataMigration, onPrimaryManagerMigrationMap);

      int currentBackgroundVersion = schema.getBackgroundVersion();
      if (currentBackgroundVersion < maxBackgroundVersion) {
        executorService.submit(() -> {
          try (AcquiredLock ignore =
                   persistentLocker.acquireLock(Schema.class, "Background-" + SCHEMA_ID, ofMinutes(120 + 1))) {
            HTimeLimiter.<Boolean>callInterruptible21(timeLimiter, Duration.ofHours(2), () -> {
              log.info("[Migration] - Updating schema background version from {} to {}", currentBackgroundVersion,
                  maxBackgroundVersion);

              for (int i = currentBackgroundVersion + 1; i <= maxBackgroundVersion; i++) {
                if (!backgroundMigrations.containsKey(i)) {
                  continue;
                }
                Class<? extends Migration> migration = backgroundMigrations.get(i);
                log.info("[Migration] - Migrating to background version {}: {} ...", i, migration.getSimpleName());
                try {
                  injector.getInstance(migration).migrate();
                } catch (Exception ex) {
                  log.error("Error while running migration {}", migration.getSimpleName(), ex);
                  break;
                }

                final UpdateOperations<Schema> updateOperations = wingsPersistence.createUpdateOperations(Schema.class);
                updateOperations.set(Schema.BACKGROUND_VERSION, i);
                wingsPersistence.update(wingsPersistence.createQuery(Schema.class), updateOperations);
              }

              log.info("[Migration] - Background migration complete");
              return true;
            });
          } catch (Exception ex) {
            log.warn("background work", ex);
          }
        });
      } else if (currentBackgroundVersion > maxBackgroundVersion) {
        // If the current version is bigger than the max version we are downgrading. Restore to the previous version
        log.info("[Migration] - Rolling back schema background version from {} to {}", currentBackgroundVersion,
            maxBackgroundVersion);
        final UpdateOperations<Schema> updateOperations = wingsPersistence.createUpdateOperations(Schema.class);
        updateOperations.set(Schema.BACKGROUND_VERSION, maxBackgroundVersion);
        wingsPersistence.update(wingsPersistence.createQuery(Schema.class), updateOperations);
      } else {
        log.info("[Migration] - Schema background is up to date");
      }

      if (schema.getVersion() < maxVersion) {
        log.info("[Migration] - Updating schema version from {} to {}", schema.getVersion(), maxVersion);
        for (int i = schema.getVersion() + 1; i <= maxVersion; i++) {
          if (migrations.containsKey(i)) {
            Class<? extends Migration> migration = migrations.get(i);
            log.info("[Migration] - Migrating to version {}: {} ...", i, migration.getSimpleName());
            injector.getInstance(migration).migrate();

            final UpdateOperations<Schema> updateOperations = wingsPersistence.createUpdateOperations(Schema.class);
            updateOperations.set(Schema.VERSION, i);
            wingsPersistence.update(wingsPersistence.createQuery(Schema.class), updateOperations);
          }
        }

        log.info("[Migration] - Migration complete");

      } else if (schema.getVersion() > maxVersion) {
        // If the current version is bigger than the max version we are downgrading. Restore to the previous version
        log.info("[Migration] - Rolling back schema version from {} to {}", schema.getVersion(), maxVersion);
        final UpdateOperations<Schema> updateOperations = wingsPersistence.createUpdateOperations(Schema.class);
        updateOperations.set(Schema.VERSION, maxVersion);
        wingsPersistence.update(wingsPersistence.createQuery(Schema.class), updateOperations);
      } else {
        log.info("[Migration] - Schema is up to date");
      }

      /*
       * We run the seed data migration only once, so even in case of a rollback, we do not reset the seedDataVersion to
       * the previous version This has been done on purpose to simplify the migrations, where the migrations do not have
       * any special logic to check if they have already been run or not.
       */
      if (schema.getSeedDataVersion() < maxSeedDataVersion) {
        log.info("[SeedDataMigration] - Updating schema version from {} to {}", schema.getSeedDataVersion(),
            maxSeedDataVersion);
        for (int i = schema.getSeedDataVersion() + 1; i <= maxSeedDataVersion; i++) {
          if (seedDataMigrations.containsKey(i)) {
            Class<? extends SeedDataMigration> seedDataMigration = seedDataMigrations.get(i);
            log.info("[SeedDataMigration] - Migrating to version {}: {} ...", i, seedDataMigration.getSimpleName());
            injector.getInstance(seedDataMigration).migrate();

            final UpdateOperations<Schema> updateOperations = wingsPersistence.createUpdateOperations(Schema.class);
            updateOperations.set(Schema.SEED_DATA_VERSION, i);
            wingsPersistence.update(wingsPersistence.createQuery(Schema.class), updateOperations);
          }
        }
      } else {
        log.info("[SeedDataMigration] - Schema is up to date");
      }

      if (schema.getTimescaleDbVersion() < maxTimeScaleDBMigration) {
        log.info("[TimescaleDBMigration] - Forward migrating schema version from {} to {}",
            schema.getTimescaleDbVersion(), maxTimeScaleDBMigration);
        for (int i = schema.getTimescaleDbVersion() + 1; i <= maxTimeScaleDBMigration; i++) {
          if (timescaleDBMigrations.containsKey(i)) {
            Class<? extends TimeScaleDBMigration> timescaleDBMigration = timescaleDBMigrations.get(i);
            log.info("[TimescaleDBMigration] - Forward Migrating to version {}: {} ...", i,
                timescaleDBMigration.getSimpleName());
            boolean result = injector.getInstance(timescaleDBMigration).migrate();
            if (!result) {
              log.info("Forward TimescaleDBMigration did not run successfully");
            } else {
              final UpdateOperations<Schema> updateOperations = wingsPersistence.createUpdateOperations(Schema.class);
              updateOperations.set(Schema.TIMESCALEDB_VERSION, i);
              wingsPersistence.update(wingsPersistence.createQuery(Schema.class), updateOperations);
            }
          }
        }
      } else {
        log.info("No TimescaleDB migration required, schema version is valid : [{}]", schema.getTimescaleDbVersion());
      }

      int currentTimeScaleDBDataMigration = schema.getTimescaleDBDataVersion();
      if (currentTimeScaleDBDataMigration < maxTimeScaleDBDataMigration) {
        executorService.submit(() -> {
          try (AcquiredLock ignore = persistentLocker.acquireLock(
                   Schema.class, "TimeScaleDBBackground-" + SCHEMA_ID, ofMinutes(120 + 1))) {
            HTimeLimiter.<Boolean>callInterruptible21(timeLimiter, Duration.ofHours(2), () -> {
              log.info("[TimeScaleDBDataMigration] - Updating schema background version from {} to {}",
                  currentTimeScaleDBDataMigration, maxTimeScaleDBDataMigration);

              boolean successfulMigration = true;

              for (int i = currentTimeScaleDBDataMigration + 1; i <= maxTimeScaleDBDataMigration; i++) {
                if (!backgroundTimeScaleDBDataMigrations.containsKey(i)) {
                  continue;
                }
                Class<? extends TimeScaleDBDataMigration> migration = backgroundTimeScaleDBDataMigrations.get(i);
                log.info("[TimeScaleDBDataMigration] - Migrating to background version {}: {} ...", i,
                    migration.getSimpleName());
                try {
                  successfulMigration = successfulMigration && injector.getInstance(migration).migrate();
                } catch (Exception ex) {
                  log.error("Error while running timeScaleDBDataMigration {}", migration.getSimpleName(), ex);
                  break;
                }
                if (successfulMigration) {
                  final UpdateOperations<Schema> updateOperations =
                      wingsPersistence.createUpdateOperations(Schema.class);
                  updateOperations.set(Schema.TIMESCALEDB_DATA_VERSION, i);
                  wingsPersistence.update(wingsPersistence.createQuery(Schema.class), updateOperations);
                } else {
                  break;
                }
              }
              if (successfulMigration) {
                log.info("TimeScaleDBData migration was successfully completed");
              } else {
                log.info("TimeScaleDBData migration was not successful ");
              }
              return true;
            });
          } catch (Exception ex) {
            log.warn("timescaledbData background work", ex);
          }
        });
      } else {
        log.info("No TimescaleDB Data migration required, schema version is valid : [{}]",
            schema.getTimescaleDBDataVersion());
      }

    } catch (RuntimeException e) {
      log.error("[Migration] - Migration failed.", e);
    }
  }

  private void initializeGlobalDbEntriesIfNeeded() {
    try {
      Query<Account> globalAccountQuery = wingsPersistence.createQuery(Account.class).filter("_id", GLOBAL_ACCOUNT_ID);
      Account globalAccount = globalAccountQuery.get();
      if (globalAccount == null) {
        wingsPersistence.save(Account.Builder.anAccount()
                                  .withUuid(GLOBAL_ACCOUNT_ID)
                                  .withCompanyName("Global")
                                  .withAccountName("Global")
                                  .withDelegateConfiguration(
                                      DelegateConfiguration.builder().delegateVersions(asList("1.0.0-dev")).build())
                                  .build());
      } else if (globalAccount.getDelegateConfiguration() == null) {
        UpdateOperations<Account> ops = wingsPersistence.createUpdateOperations(Account.class);
        setUnset(ops, "delegateConfiguration",
            DelegateConfiguration.builder().delegateVersions(asList("1.0.0-dev")).build());
        wingsPersistence.update(globalAccountQuery, ops);
      }
    } catch (Exception e) {
      log.error("[Migration] - initializeGlobalDbEntriesIfNeeded failed.", e);
    }
  }

  @VisibleForTesting
  void runMigrationWhenNewManagerIsPrimary(final int maxOnPrimaryManagerMigrationVersion,
      final Map<Integer, Class<? extends OnPrimaryManagerMigration>> onPrimaryManagerDataMigrations) {
    log.info("This is primary manager. Queuing OnPrimaryManager Migration Job");
    executorService.submit(() -> {
      try (AcquiredLock ignore =
               persistentLocker.acquireLock(Schema.class, "OnPrimaryManager-" + SCHEMA_ID, ofMinutes(120 + 1))) {
        HTimeLimiter.<Boolean>callInterruptible21(timeLimiter, Duration.ofHours(2), () -> {
          final int currentOnPrimaryMigrationVersion = getCurrentOnPrimaryMigrationVersion();
          if (currentOnPrimaryMigrationVersion < maxOnPrimaryManagerMigrationVersion) {
            log.info("[Migration] - Updating schema primary manager version from {} to {}",
                currentOnPrimaryMigrationVersion, maxOnPrimaryManagerMigrationVersion);

            for (int i = currentOnPrimaryMigrationVersion + 1; i <= maxOnPrimaryManagerMigrationVersion; i++) {
              if (!onPrimaryManagerDataMigrations.containsKey(i)) {
                continue;
              }
              Class<? extends OnPrimaryManagerMigration> migration = onPrimaryManagerDataMigrations.get(i);
              log.info("[Migration] - Migrating to primary manager version {}: {} ...", i, migration.getSimpleName());
              try {
                injector.getInstance(migration).migrate();
              } catch (Exception ex) {
                log.error("Error while running migration {}", migration.getSimpleName(), ex);
                break;
              }

              final UpdateOperations<Schema> updateOperations = wingsPersistence.createUpdateOperations(Schema.class);
              updateOperations.set(Schema.ON_PRIMARY_MANAGER_VERSION, i);
              wingsPersistence.update(wingsPersistence.createQuery(Schema.class), updateOperations);
            }
            log.info("[Migration] - Primary manager migration complete");
          } else {
            log.info("[Migration] - Schema primary manager is up to date");
          }
          return true;
        });
      } catch (Exception ex) {
        log.warn("primary manager work", ex);
      }
    });
  }

  private int getCurrentOnPrimaryMigrationVersion() {
    final Schema schema = wingsPersistence.createQuery(Schema.class).get();
    return schema != null ? schema.getOnPrimaryManagerVersion() : 0;
  }

  @VisibleForTesting
  boolean isNotUnderMaintenance() {
    return !getMaintenanceFlag();
  }

  @VisibleForTesting
  void scheduleOnPrimaryMigrations(Schema schema, int maxOnPrimaryManagerMigrationVersion,
      Map<Integer, Class<? extends OnPrimaryManagerMigration>> onPrimaryManagerMigrations) {
    final int currentOnPrimaryManagerVersion = schema.getOnPrimaryManagerVersion();
    if (currentOnPrimaryManagerVersion < maxOnPrimaryManagerMigrationVersion) {
      startOnPrimaryMigrationScheduledJob(
          () -> runMigrationWhenNewManagerIsPrimary(maxOnPrimaryManagerMigrationVersion, onPrimaryManagerMigrations));

    } else if (currentOnPrimaryManagerVersion > maxOnPrimaryManagerMigrationVersion) {
      // If the current version is bigger than the max version we are downgrading. Restore to the previous version
      log.info("[Migration] - Rolling back schema primary manager version from {} to {}",
          currentOnPrimaryManagerVersion, maxOnPrimaryManagerMigrationVersion);
      final UpdateOperations<Schema> updateOperations = wingsPersistence.createUpdateOperations(Schema.class);
      updateOperations.set(Schema.ON_PRIMARY_MANAGER_VERSION, maxOnPrimaryManagerMigrationVersion);
      wingsPersistence.update(wingsPersistence.createQuery(Schema.class), updateOperations);
    } else {
      log.info("[Migration] - Schema primary manager is up to date");
    }
  }
  private void startOnPrimaryMigrationScheduledJob(Runnable runnable) {
    final AtomicBoolean shouldRun = new AtomicBoolean(true);
    final AtomicInteger runCount = new AtomicInteger(0);
    final int maxRunCount = 10;
    executorService.submit(() -> {
      while (shouldRun.get()) {
        waitForSometime();
        final int currentRunCount = runCount.incrementAndGet();
        log.info(
            "Started next run of OnPrimaryMigrationScheduledJob with isNotUnderMaintenance =[{}] , isPrimary= [{}], runCount=[{}]",
            isNotUnderMaintenance(), configurationController.isPrimary(), currentRunCount);
        if (currentRunCount > maxRunCount) {
          shouldRun.set(false);
          log.info("Exceeded max run count of {}. Skip run ", maxRunCount);
        } else if (isNotUnderMaintenance() && configurationController.isPrimary()) {
          try {
            runnable.run();
          } catch (Exception e) {
            log.error("error while running OnPrimaryMigrationScheduledJob", e);
          }
          shouldRun.set(false);
        }
        log.info("Completed run of OnPrimaryMigrationScheduledJob with shouldRun= [{}]", shouldRun.get());
      }
      log.info("Stopping OnPrimaryMigrationScheduledJob");
    });
    log.info("Scheduled OnPrimaryMigrationScheduledJob");
  }

  private void waitForSometime() {
    sleep(Duration.ofMinutes(10));
  }
}
