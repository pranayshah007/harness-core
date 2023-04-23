/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.lock.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.FAILED_TO_ACQUIRE_PERSISTENT_LOCK;
import static io.harness.exception.WingsException.SRE;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.PersistentLockException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.health.HealthMonitor;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@OwnedBy(PL)
@Singleton
@Slf4j
public class RedisPersistentLocker implements PersistentLocker, HealthMonitor, Managed {
  private RedissonClient client;
  private String lockNamespace;
  private static final String LOCK_PREFIX = "locks";
  private static final String ERROR_MESSAGE = "Failed to acquire distributed lock for %s";

  @Inject
  RedisPersistentLocker(@Named("lock") RedisConfig redisLockConfig) {
    this.client = RedissonClientFactory.getClient(redisLockConfig);
    String envNamespace = redisLockConfig.getEnvNamespace();
    this.lockNamespace = EmptyPredicate.isEmpty(envNamespace) ? LOCK_PREFIX.concat(":")
                                                              : String.format("%s:%s:", envNamespace, LOCK_PREFIX);
  }

  private String getLockName(String name) {
    return lockNamespace.concat(name);
  }

  @Override
  public AcquiredLock acquireLock(String name, Duration timeout) {
    try {
      name = getLockName(name);
      RLock lock = client.getLock(name);
      boolean locked;
      if (client.getConfig().isSentinelConfig()) {
        log.debug("[RedisSentinelMode]: Trying Async lock");
        locked = lock.tryLockAsync(0, timeout.toMillis(), TimeUnit.MILLISECONDS).get(1, TimeUnit.SECONDS);
        log.debug("[RedisSentinelMode]: Async lock acquired successfully");
      } else {
        locked = lock.tryLock(0, timeout.toMillis(), TimeUnit.MILLISECONDS);
      }
      if (locked) {
        log.debug("Lock acquired on {} for timeout {}", name, timeout);
        return RedisAcquiredLock.builder().lock(lock).isSentinelMode(client.getConfig().isSentinelConfig()).build();
      }
    } catch (InterruptedException iex) {
      log.error(format(ERROR_MESSAGE, name), iex);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      throw new UnexpectedException(format(ERROR_MESSAGE, name), ex);
    }
    throw new PersistentLockException(format(ERROR_MESSAGE, name), FAILED_TO_ACQUIRE_PERSISTENT_LOCK, SRE);
  }

  @Override
  public AcquiredLock acquireEphemeralLock(String name, Duration timeout) {
    return acquireLock(name, timeout);
  }

  @Override
  public AcquiredLock acquireLock(Class entityClass, String entityId, Duration timeout) {
    return acquireLock(entityClass.getName() + "-" + entityId, timeout);
  }

  @Override
  public AcquiredLock tryToAcquireLock(Class entityClass, String entityId, Duration timeout) {
    try {
      return acquireLock(entityClass, entityId, timeout);
    } catch (WingsException exception) {
      return null;
    }
  }

  @Override
  public AcquiredLock tryToAcquireEphemeralLock(Class entityClass, String entityId, Duration timeout) {
    return tryToAcquireLock(entityClass, entityId, timeout);
  }

  @Override
  public AcquiredLock tryToAcquireLock(String name, Duration timeout) {
    try {
      return acquireLock(name, timeout);
    } catch (WingsException exception) {
      return null;
    }
  }

  @Override
  public AcquiredLock tryToAcquireInfiniteLockWithPeriodicRefresh(String name, Duration waitTime) {
    try {
      name = getLockName(name);
      RLock lock = client.getLock(name);
      boolean locked;
      if (client.getConfig().isSentinelConfig()) {
        locked = lock.tryLockAsync(waitTime.toMillis(), -1, TimeUnit.MILLISECONDS)
                     .get(waitTime.toMillis(), TimeUnit.MILLISECONDS);
      } else {
        locked = lock.tryLock(waitTime.toMillis(), -1, TimeUnit.MILLISECONDS);
      }
      if (locked) {
        return RedisAcquiredLock.builder()
            .lock(lock)
            .isLeaseInfinite(true)
            .isSentinelMode(client.getConfig().isSentinelConfig())
            .build();
      }
    } catch (InterruptedException iex) {
      log.error(format(ERROR_MESSAGE, name), iex);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      throw new UnexpectedException(format(ERROR_MESSAGE, name), ex);
    }
    throw new PersistentLockException(format(ERROR_MESSAGE, name), FAILED_TO_ACQUIRE_PERSISTENT_LOCK, SRE);
  }

  @Override
  public AcquiredLock tryToAcquireEphemeralLock(String name, Duration timeout) {
    return tryToAcquireLock(name, timeout);
  }

  @Override
  public AcquiredLock waitToAcquireLock(
      Class entityClass, String entityId, Duration lockTimeout, Duration waitTimeout) {
    return waitToAcquireLock(entityClass.getName() + "-" + entityId, lockTimeout, waitTimeout);
  }

  @Override
  public AcquiredLock waitToAcquireLock(String name, Duration lockTimeout, Duration waitTimeout) {
    try {
      name = getLockName(name);
      RLock lock = client.getLock(name);
      boolean locked;
      if (client.getConfig().isSentinelConfig()) {
        locked = lock.tryLockAsync(waitTimeout.toMillis(), lockTimeout.toMillis(), TimeUnit.MILLISECONDS)
                     .get(lockTimeout.toMillis(), TimeUnit.MILLISECONDS);
      } else {
        locked = lock.tryLock(waitTimeout.toMillis(), lockTimeout.toMillis(), TimeUnit.MILLISECONDS);
      }
      if (locked) {
        log.debug("Acquired lock on {} for {} having a wait Timeout of {}", name, lockTimeout, waitTimeout);
        return RedisAcquiredLock.builder().lock(lock).isSentinelMode(client.getConfig().isSentinelConfig()).build();
      }
    } catch (InterruptedException iex) {
      log.error(format(ERROR_MESSAGE, name), iex);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      throw new UnexpectedException(format(ERROR_MESSAGE, name), ex);
    }
    throw new PersistentLockException(format(ERROR_MESSAGE, name), FAILED_TO_ACQUIRE_PERSISTENT_LOCK, SRE);
  }

  @Override
  public void destroy(AcquiredLock acquiredLock) {
    acquiredLock.close();
  }

  @Override
  public Duration healthExpectedResponseTimeout() {
    return ofSeconds(10);
  }

  @Override
  public Duration healthValidFor() {
    return ofSeconds(15);
  }

  @Override
  public void isHealthy() {
    try (AcquiredLock dummy = acquireEphemeralLock("HEALTH_CHECK - " + generateUuid(), ofSeconds(1))) {
      // nothing to do
    }
  }

  @Override
  public void start() throws Exception {
    // Nothing to do here
  }

  @Override
  public void stop() throws Exception {
    if (client != null) {
      client.shutdown();
    }
  }
}
