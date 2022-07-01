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

import io.harness.beans.FeatureName;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import com.google.inject.Singleton;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;

@Singleton
@Slf4j
public class DebeziumController<T extends MongoCollectionChangeConsumer> implements Runnable {
  private final CfClient cfClient;
  private final T changeConsumer;
  private final Properties props;
  private final ExecutorService executorService;
  private final PersistentLocker locker;
  private final AtomicBoolean shouldStop;

  public DebeziumController(
      Properties props, T changeConsumer, PersistentLocker locker, ExecutorService executorService, CfClient cfClient) {
    this.props = props;
    this.changeConsumer = changeConsumer;
    this.executorService = executorService;
    this.locker = locker;
    this.shouldStop = new AtomicBoolean(false);
    this.cfClient = cfClient;
  }

  @Override
  public void run() {
    while (!shouldStop.get()) {
      DebeziumEngine<ChangeEvent<String, String>> debeziumEngine = null;
      try (AcquiredLock<?> aggregatorLock = acquireLock(true)) {
        if (aggregatorLock == null) {
          TimeUnit.SECONDS.sleep(10);
          continue;
        }
        RLock rLock = (RLock) aggregatorLock.getLock();
        boolean debeziumEnabled =
            cfClient.boolVariation(FeatureName.DEBEZIUM_ENABLED.toString(), Target.builder().build(), false);
        while (!debeziumEnabled) {
          log.info("FF {} is false", FeatureName.DEBEZIUM_ENABLED.toString());
          debeziumEnabled =
              cfClient.boolVariation(FeatureName.DEBEZIUM_ENABLED.toString(), Target.builder().build(), false);
          TimeUnit.SECONDS.sleep(10);
        }
        debeziumEngine = getEngine(props);
        Future<?> future = executorService.submit(debeziumEngine);
        log.info("Starting Debezium Engine for Collection {} ...", changeConsumer.getCollection());
        while (!future.isDone() && rLock.isHeldByCurrentThread()) {
          log.info("primary lock remaining ttl {}, isHeldByCurrentThread {}, holdCount {}, name {}",
              rLock.remainTimeToLive(), rLock.isHeldByCurrentThread(), rLock.getHoldCount(), rLock.getName());
          debeziumEnabled =
              cfClient.boolVariation(FeatureName.DEBEZIUM_ENABLED.toString(), Target.builder().build(), false);
          if (!debeziumEnabled) {
            log.info("FF {} is false.. Closing the Engine.", FeatureName.DEBEZIUM_ENABLED.toString());
            debeziumEngine.close();
            break;
          }
          TimeUnit.SECONDS.sleep(30);
        }
      } catch (InterruptedException e) {
        shouldStop.set(true);
        log.warn("Thread interrupted, stopping controller for {}", changeConsumer.getCollection(), e);
      } catch (Exception e) {
        log.error("Primary sync stopped due to exception", e);
      } finally {
        try {
          if (debeziumEngine != null) {
            debeziumEngine.close();
            TimeUnit.SECONDS.sleep(10);
          }
        } catch (IOException e) {
          log.error("Failed to close debezium engine due to IO exception", e);
        } catch (InterruptedException e) {
          log.warn("Interrupted while waiting for debezium engine to close", e);
        } catch (Exception e) {
          log.error("Failed to close debezium engine due to unexpected exception", e);
        }
      }
    }
  }

  protected DebeziumEngine<ChangeEvent<String, String>> getEngine(Properties props) {
    return DebeziumEngine.create(Json.class).using(props).notifying(changeConsumer).build();
  }

  private AcquiredLock<?> acquireLock(boolean retryIndefinitely) throws InterruptedException {
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

  private String getLockName() {
    return DEBEZIUM_LOCK_PREFIX + props.get(DebeziumConfiguration.CONNECTOR_NAME) + "-"
        + changeConsumer.getCollection();
  }
}
