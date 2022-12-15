/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.ARTIFACT_PERPETUAL_TASK;
import static io.harness.beans.FeatureName.ARTIFACT_PERPETUAL_TASK_MIGRATION;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactCollectionResponseHandler;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.dl.WingsPersistence;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.lifecycle.Managed;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

@OwnedBy(CDC)
@Slf4j
@Singleton
public class ArtifactStreamPTaskMigrationJob implements Managed {
  private static final String LOCK_NAME = "ArtifactStreamPTaskMigrationJob";
  private static final int BATCH_SIZE = 100;

  @Inject private ArtifactStreamPTaskHelper artifactStreamPTaskHelper;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private PersistentLocker persistentLocker;
  @Inject private FeatureFlagService featureFlagService;

  private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
      new ThreadFactoryBuilder().setNameFormat("artifact-stream-perpetual-task-migration-job").build());
  private Future<?> artifactStreamPTaskJobFuture;

  @Override
  public void start() {
    artifactStreamPTaskJobFuture = executorService.scheduleWithFixedDelay(this::run, 1, 10, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws InterruptedException {
    if (artifactStreamPTaskJobFuture != null) {
      artifactStreamPTaskJobFuture.cancel(true);
    }

    executorService.shutdown();
    executorService.awaitTermination(30, TimeUnit.SECONDS);
  }

  @VisibleForTesting
  public void run() {
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireLock(LOCK_NAME, Duration.ofMinutes(15))) {
      if (lock == null) {
        log.info("Couldn't acquire lock");
        return;
      }

      log.info("Artifact stream perpetual task migration job started");
      try {
        runInternal();
      } catch (Exception ex) {
        log.error("Error migrating artifact streams to perpetual task", ex);
      }

      log.info("Artifact stream perpetual task migration job completed");
    }
  }

  private void runInternal() {
    boolean mainFFOn = featureFlagService.isEnabledForAllAccounts(ARTIFACT_PERPETUAL_TASK);
    boolean migrationFFOn = featureFlagService.isEnabledForAllAccounts(ARTIFACT_PERPETUAL_TASK_MIGRATION);
    if (mainFFOn && migrationFFOn) {
      createPerpetualTasks(null);
      return;
    }

    Set<String> accountIds;
    if (mainFFOn) {
      accountIds = getAccountIds(ARTIFACT_PERPETUAL_TASK_MIGRATION);
    } else if (migrationFFOn) {
      accountIds = getAccountIds(ARTIFACT_PERPETUAL_TASK);
    } else {
      accountIds =
          Sets.intersection(getAccountIds(ARTIFACT_PERPETUAL_TASK), getAccountIds(ARTIFACT_PERPETUAL_TASK_MIGRATION));
    }

    if (EmptyPredicate.isNotEmpty(accountIds)) {
      createPerpetualTasks(accountIds);
    } else {
      log.info("Not migrating artifact streams to perpetual task for any accounts");
    }
  }

  private void createPerpetualTasks(Set<String> accountIds) {
    Query<ArtifactStream> query;
    if (EmptyPredicate.isEmpty(accountIds)) {
      log.info("Migrating artifact streams to perpetual task for all accounts");
      query = wingsPersistence.createQuery(ArtifactStream.class, excludeAuthority);
    } else {
      log.info(format("Migrating artifact streams to perpetual task for %d accounts", accountIds.size()));
      query = wingsPersistence.createQuery(ArtifactStream.class).field(ArtifactStreamKeys.accountId).in(accountIds);
    }

    List<ArtifactStream> artifactStreams = query.field(ArtifactStreamKeys.perpetualTaskId)
                                               .doesNotExist()
                                               .field(ArtifactStreamKeys.failedCronAttempts)
                                               .lessThan(ArtifactCollectionResponseHandler.MAX_FAILED_ATTEMPTS)
                                               .project(ArtifactStreamKeys.accountId, true)
                                               .project(ArtifactStreamKeys.uuid, true)
                                               .asList(new FindOptions().limit(BATCH_SIZE));
    if (EmptyPredicate.isEmpty(artifactStreams)) {
      log.info("No eligible artifact streams for perpetual task migration");
      return;
    }

    log.info(format("Migrating %d artifact streams to perpetual task", artifactStreams.size()));
    artifactStreams.forEach(artifactStream -> artifactStreamPTaskHelper.createPerpetualTask(artifactStream));
  }

  private Set<String> getAccountIds(FeatureName featureName) {
    Set<String> accountIds = featureFlagService.getAccountIds(featureName);
    return EmptyPredicate.isEmpty(accountIds) ? Collections.emptySet() : accountIds;
  }
}
