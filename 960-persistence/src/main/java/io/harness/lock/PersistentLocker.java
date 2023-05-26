/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.lock;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import java.time.Duration;

/**
 * Locker interface to acquire and release locks.
 *
 * @author Rishi
 */
@OwnedBy(PL)
public interface PersistentLocker {
  /**
   * Acquire lock.
   *
   * @param name    the lock name
   * @param timeout for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock acquireLock(String name, Duration timeout);

  /**
   * Acquire ephemeral lock.
   *
   * @param name    the lock name
   * @param timeout for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock acquireEphemeralLock(String name, Duration timeout);

  /**
   * Acquire lock.
   *
   * @param entityClass the entity class
   * @param entityId    the entity id
   * @param timeout     for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock acquireLock(Class entityClass, String entityId, Duration timeout);

  /**
   * Try to acquire lock.
   *
   * @param entityClass the entity class
   * @param entityId    the entity id
   * @param timeout     for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock tryToAcquireLock(Class entityClass, String entityId, Duration timeout);

  /**
   * Try to acquire lock.
   *
   * @param name    the lock name
   * @param timeout for how long to keep the lock if the app crashes
   * @return AcquiredLock object
   */
  AcquiredLock tryToAcquireLock(String name, Duration timeout);

  /**
   * Try to acquire an infinite lock with periodic lease refreshing after every 30 seconds
   *
   * @param name    the lock name
   * @param waitTime wait time to acquire lock
   * @return AcquiredLock object
   */
  AcquiredLock tryToAcquireInfiniteLockWithPeriodicRefresh(String name, Duration waitTime);

  /**
   * Acquire lock.
   *
   * @param entityClass the entity class
   * @param entityId    the entity id
   * @param lockTimeout for how long to keep the lock if the app crashes
   * @param waitTimeout  how long to wait to acquire the lock
   * @return AcquiredLock object
   */
  AcquiredLock waitToAcquireLock(Class entityClass, String entityId, Duration lockTimeout, Duration waitTimeout);

  AcquiredLock waitToAcquireLock(String name, Duration lockTimeout, Duration waitTimeout);

  /**
   * Try to acquire a lock by waiting for the specified wait duration.
   * @param name    name for the lock
   * @param lockTimeout for how long to keep the lock if the app crashes
   * @param waitTimeout  how long to wait to acquire the lock
   * @return AcquiredLock object or Null
   */
  AcquiredLock waitToAcquireLockOptional(String name, Duration lockTimeout, Duration waitTimeout);

  /**
   * Destroy lock.
   *
   * @param acquiredLock  already acquired lock
   */
  void destroy(AcquiredLock acquiredLock);
}
