/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migration;

import static io.harness.migration.MigrationJobInstance.Status.BASELINE;
import static io.harness.migration.MigrationJobInstance.Status.PENDING;

import io.harness.persistence.HPersistence;
import io.harness.persistence.store.Store;
import io.harness.threading.Poller;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Set;

@Singleton
public class AppMigrationService {
  @Inject private HPersistence persistence;

  private void upsertMigrationJobInstance(MigrationJob job) {
    persistence.insert(
        MigrationJobInstance.builder().id(job.getId()).metadata(job.getMetadata()).status(PENDING).build());

    // It was there from before
    // if (inserted == null) {
    // TODO: remove the allowances that are no longer acceptable
    //}
  }

  public boolean initIfFirstTime() {
    final Set<String> collectionNames =
        persistence.getDatastore(MigrationJobInstance.class).getDB().getCollectionNames();

    if (collectionNames.contains(MigrationJobInstance.COLLECTION_NAME)) {
      return false;
    }

    for (MigrationJob job : MigrationList.jobs.values()) {
      persistence.save(
          MigrationJobInstance.builder().id(job.getId()).metadata(job.getMetadata()).status(BASELINE).build());
    }

    return true;
  }

  public MigrationJob updateStoreMigrationJobInstances(Store store) {
    if (initIfFirstTime()) {
      return null;
    }
    MigrationJob lastJob = null;
    for (MigrationJob job : MigrationList.jobs.values()) {
      final boolean anyMatch = job.getMetadata()
                                   .getChannels()
                                   .stream()
                                   .filter(channel -> channel instanceof MongoCollectionMigrationChannel)
                                   .map(channel -> (MongoCollectionMigrationChannel) channel)
                                   .anyMatch(channel -> channel.getStore().getName().equals(store.getName()));

      if (anyMatch) {
        lastJob = job;
        upsertMigrationJobInstance(job);
      }
    }

    return lastJob;
  }

  public void waitForMongoSchema(Store store) {
    final MigrationJob lastJob = updateStoreMigrationJobInstances(store);

    Poller.pollFor(Duration.ofMinutes(10), Duration.ofSeconds(10), () -> {
      final MigrationJobInstance migrationJobInstance = persistence.get(MigrationJobInstance.class, lastJob.getId());
      return MigrationJobInstance.Status.isFinalStatus(migrationJobInstance.getStatus());
    });
  }
}
