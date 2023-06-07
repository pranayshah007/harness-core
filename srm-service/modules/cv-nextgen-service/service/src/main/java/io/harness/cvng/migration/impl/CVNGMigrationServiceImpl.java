/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.impl;

import static io.harness.concurrent.HTimeLimiter.callInterruptible21;
import static io.harness.cvng.migration.beans.CVNGSchema.CVNGMigrationStatus.PENDING;
import static io.harness.cvng.migration.beans.CVNGSchema.SCHEMA_ID;

import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.migration.CVNGBackgroundMigrationList;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.CVNGSchema;
import io.harness.cvng.migration.beans.CVNGSchema.CVNGSchemaKeys;
import io.harness.cvng.migration.service.CVNGMigrationService;
import io.harness.persistence.HPersistence;

import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@Slf4j
public class CVNGMigrationServiceImpl implements CVNGMigrationService {
  @Inject private HPersistence hPersistence;
  @Inject private Injector injector;
  @Inject private ExecutorService executorService;
  @Inject private TimeLimiter timeLimiter;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public void runMigrations() {
    Map<Integer, Class<? extends CVNGMigration>> backgroundMigrations =
        CVNGBackgroundMigrationList.getMigrations().stream().collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    int maxBackgroundVersion = backgroundMigrations.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

    int cvngSchemaVersion = initializeGlobalDbEntriesIfNeededAndGetVersion();

    log.info("[Migration] - Checking for new backgroundMigrations");
    if (cvngSchemaVersion < maxBackgroundVersion) {
      hPersistence.update(hPersistence.createQuery(CVNGSchema.class),
          hPersistence.createUpdateOperations(CVNGSchema.class)
              .set(CVNGSchemaKeys.cvngMigrationStatus, CVNGSchema.CVNGMigrationStatus.RUNNING));
      log.info("Enqueuing CVNGSchema");
      executorService.submit(() -> {
        try {
          callInterruptible21(timeLimiter, Duration.ofHours(2), () -> {
            log.info("[Migration] - Updating schema version from {} to {}", cvngSchemaVersion, maxBackgroundVersion);
            for (int i = cvngSchemaVersion + 1; i <= maxBackgroundVersion; i++) {
              if (backgroundMigrations.containsKey(i)) {
                Class<? extends CVNGMigration> migration = backgroundMigrations.get(i);
                log.info("[Migration] - Migrating to version {}: {} ...", i, migration.getSimpleName());
                try {
                  injector.getInstance(migration).migrate();
                } catch (Exception ex) {
                  log.error("Error while running migration {}", migration.getSimpleName(), ex);
                  onFailure();
                }
                final UpdateOperations<CVNGSchema> updateOperations =
                    hPersistence.createUpdateOperations(CVNGSchema.class);
                updateOperations.set(CVNGSchema.VERSION, i);
                hPersistence.update(hPersistence.createQuery(CVNGSchema.class), updateOperations);
              }
            }
            onCompletion();
            log.info("[Migration] - Migration complete");
            return true;
          });
        } catch (Exception ex) {
          log.warn("background work", ex);
          onFailure();
        }
      });
    }
  }

  private void onCompletion() {
    final UpdateOperations<CVNGSchema> updateOperations = hPersistence.createUpdateOperations(CVNGSchema.class);
    updateOperations.set(CVNGSchemaKeys.cvngMigrationStatus, CVNGSchema.CVNGMigrationStatus.SUCCESS);
    hPersistence.update(hPersistence.createQuery(CVNGSchema.class), updateOperations);
    log.info("Done enqueuing CVNGSchema");
  }

  private void onFailure() {
    final UpdateOperations<CVNGSchema> updateOperations = hPersistence.createUpdateOperations(CVNGSchema.class);
    updateOperations.set(CVNGSchemaKeys.cvngMigrationStatus, CVNGSchema.CVNGMigrationStatus.ERROR);
    hPersistence.update(hPersistence.createQuery(CVNGSchema.class), updateOperations);
    log.info("Done enqueuing CVNGSchema");
  }

  private int initializeGlobalDbEntriesIfNeededAndGetVersion() {
    try {
      Query<CVNGSchema> globalSchemaQuery = hPersistence.createQuery(CVNGSchema.class).filter("_id", SCHEMA_ID);
      CVNGSchema globalSchema = globalSchemaQuery.get();
      if (globalSchema == null) {
        log.info("[Migration] - Initializing Global DB Entries");
        hPersistence.save(CVNGSchema.builder().uuid(SCHEMA_ID).version(0).cvngMigrationStatus(PENDING).build());
        return 0;
      }
      return globalSchema.getVersion();
    } catch (Exception e) {
      log.error("[Migration] - initializeGlobalDbEntriesIfNeeded failed.", e);
      throw e;
    }
  }
}
