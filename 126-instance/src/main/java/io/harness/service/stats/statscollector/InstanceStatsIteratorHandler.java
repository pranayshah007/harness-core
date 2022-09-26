/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.stats.statscollector;

import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.DeploymentAccounts;
import io.harness.entities.DeploymentAccounts.DeploymentAccountsKeys;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.DX)
public class InstanceStatsIteratorHandler implements Handler<DeploymentAccounts> {
  public static final String LOCK_KEY = "INSTANCE_STATS_ITERATOR:";

  private PersistenceIteratorFactory persistenceIteratorFactory;
  private MorphiaPersistenceProvider<DeploymentAccounts> persistenceProvider;
  private PersistentLocker persistentLocker;
  private InstanceStatsCollectorImpl instanceStatsCollector;

  public void registerIterators() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PersistenceIteratorFactory.PumpExecutorOptions.builder()
            .name("InstanceStatsMetricsPublisher")
            .poolSize(5)
            .interval(ofMinutes(30))
            .build(),
        InstanceStatsIteratorHandler.class,
        MongoPersistenceIterator.<DeploymentAccounts, MorphiaFilterExpander<DeploymentAccounts>>builder()
            .clazz(DeploymentAccounts.class)
            .fieldName(DeploymentAccountsKeys.instanceStatsMetricsPublisherIteration)
            .targetInterval(ofMinutes(30))
            .acceptableExecutionTime(ofSeconds(30))
            .acceptableNoAlertDelay(ofSeconds(30))
            .handler(this)
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(DeploymentAccounts deploymentAccounts) {
    String accountId = deploymentAccounts.getAccountIdentifier();
    try (AutoLogContext ignore = new AccountLogContext(accountId, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      log.info("Running instance stats metrics iterator");
      createStats(accountId);
    } catch (Exception exception) {
      log.error("Failed to publish instance stats metrics", exception);
    }
  }

  // ------------------- PRIVATE METHODS -----------------

  private void createStats(final String accountId) {
    try (AcquiredLock lock = persistentLocker.tryToAcquireLock(LOCK_KEY + accountId, Duration.ofSeconds(120))) {
      if (lock == null) {
        log.error("Unable to acquire lock for creating instance stats");
        return;
      }

      Stopwatch sw = Stopwatch.createStarted();
      boolean success = instanceStatsCollector.createStats(accountId);
      if (success) {
        log.info(
            "Successfully published instance stats to timescale, time taken : {}", sw.elapsed(TimeUnit.MILLISECONDS));
      } else {
        log.error("Unable to publish instance stats to timescale, time taken : {}", sw.elapsed(TimeUnit.MILLISECONDS));
      }
    }
  }
}
