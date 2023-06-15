/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.lock.noop;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;

import io.dropwizard.lifecycle.Managed;
import java.time.Duration;

@OwnedBy(PL)
public class PersistentNoopLocker implements PersistentLocker, Managed {
  @Override
  public AcquiredLock acquireLock(String name, Duration timeout) {
    return new AcquiredNoopLock();
  }

  @Override
  public AcquiredLock acquireEphemeralLock(String name, Duration timeout) {
    return new AcquiredNoopLock();
  }

  @Override
  public AcquiredLock acquireLock(Class entityClass, String entityId, Duration timeout) {
    return new AcquiredNoopLock();
  }

  @Override
  public AcquiredLock tryToAcquireLock(Class entityClass, String entityId, Duration timeout) {
    return new AcquiredNoopLock();
  }

  @Override
  public AcquiredLock tryToAcquireLock(String name, Duration timeout) {
    return new AcquiredNoopLock();
  }

  @Override
  public AcquiredLock tryToAcquireInfiniteLockWithPeriodicRefresh(String name, Duration waitTime) {
    return new AcquiredNoopLock();
  }

  @Override
  public AcquiredLock waitToAcquireLockOptional(String name, Duration lockTimeout, Duration waitTimeout) {
    return new AcquiredNoopLock();
  }

  @Override
  public AcquiredLock waitToAcquireLock(
      Class entityClass, String entityId, Duration lockTimeout, Duration waitTimeout) {
    return new AcquiredNoopLock();
  }

  @Override
  public AcquiredLock waitToAcquireLock(String name, Duration lockTimeout, Duration waitTimeout) {
    return new AcquiredNoopLock();
  }

  @Override
  public void destroy(AcquiredLock acquiredLock) {
    // Nothing to do
  }

  @Override
  public void start() throws Exception {
    // Nothing to do
  }

  @Override
  public void stop() throws Exception {
    // Nothing to do
  }
}
