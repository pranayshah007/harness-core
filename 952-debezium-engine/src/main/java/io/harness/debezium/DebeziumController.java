/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 * This class manages the lifecycle of Debezium Engine thread.
 */

package io.harness.debezium;

import static io.harness.debezium.DebeziumConstants.DEBEZIUM_LOCK_PREFIX;

import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.time.Duration;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;

@Singleton
@Slf4j
public class DebeziumController<T extends MongoCollectionChangeConsumer> implements Runnable {
  private final T changeConsumer;
  private final Properties props;
  private final ExecutorService executorService;
  private final PersistentLocker locker;
  private final AtomicBoolean shouldStop;
  private final DebeziumService debeziumService;

  public DebeziumController(Properties props, T changeConsumer, PersistentLocker locker,
      ExecutorService executorService, DebeziumService debeziumService) {
    this.props = props;
    this.changeConsumer = changeConsumer;
    this.executorService = executorService;
    this.locker = locker;
    this.shouldStop = new AtomicBoolean(false);
    this.debeziumService = debeziumService;
  }

  @Override
  public void run() {
    while (!shouldStop.get()) {
      DebeziumEngine<ChangeEvent<String, String>> debeziumEngine = null;
      try (AcquiredLock<?> aggregatorLock = acquireLock(true)) {
        long start = System.currentTimeMillis();
        if (aggregatorLock == null) {
          TimeUnit.SECONDS.sleep(10);
          continue;
        }
        RLock rLock = (RLock) aggregatorLock.getLock();
        debeziumEngine = debeziumService.getEngine(props, changeConsumer, changeConsumer.getCollection(), this);
        Future<?> future = executorService.submit(debeziumEngine);
        log.info("Starting Debezium Engine for Collection {} at {}", changeConsumer.getCollection(),
            System.currentTimeMillis());
        while (!future.isDone() && rLock.isHeldByCurrentThread()) {
          log.info("primary lock remaining ttl {}, isHeldByCurrentThread {}, holdCount {}, name {}",
              rLock.remainTimeToLive(), rLock.isHeldByCurrentThread(), rLock.getHoldCount(), rLock.getName());
          TimeUnit.SECONDS.sleep(30);
          // Randomly releasing lock after 25-30 mins so that load can be distributed among the pods
          int result = new Random().nextInt(5) + 25;
          if (System.currentTimeMillis() - start >= result * 60 * 1000) {
            log.info("releasing lock after {} minutes", result);
            break;
          }
        }
      } catch (InterruptedException e) {
        shouldStop.set(true);
        log.warn("Thread interrupted, stopping controller for {}", changeConsumer.getCollection(), e);
      } catch (Exception e) {
        log.error("Primary sync stopped due to exception", e);
      } finally {
        try {
          debeziumService.closeEngine(debeziumEngine, changeConsumer.getCollection());
          TimeUnit.SECONDS.sleep(20);
        } catch (InterruptedException e) {
          shouldStop.set(true);
          log.warn("Thread interrupted, stopping controller for {}", changeConsumer.getCollection(), e);
        } catch (Exception e) {
          log.error("Failed to close debezium engine due to exception", e);
        }
      }
    }
  }

  @VisibleForTesting
  AcquiredLock<?> acquireLock(boolean retryIndefinitely) throws InterruptedException {
    AcquiredLock<?> aggregatorLock = null;
    String lockIdentifier = getLockName();
    do {
      try {
        log.info("Trying to acquire {} lock with 5 seconds timeout", lockIdentifier);
        aggregatorLock = locker.tryToAcquireInfiniteLockWithPeriodicRefresh(lockIdentifier, Duration.ofSeconds(5));
      } catch (Exception ex) {
        log.warn("Unable to get {} lock, due to the exception. Will retry again", lockIdentifier, ex);
      }
      if (aggregatorLock == null) {
        TimeUnit.SECONDS.sleep(10);
      }
    } while (aggregatorLock == null && retryIndefinitely);
    return aggregatorLock;
  }

  String getLockName() {
    return DEBEZIUM_LOCK_PREFIX + props.get(DebeziumConfiguration.CONNECTOR_NAME) + "-"
        + changeConsumer.getCollection();
  }

  public void stopDebeziumController() {
    shouldStop.set(true);
  }
}
