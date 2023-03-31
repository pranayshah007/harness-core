/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.lock.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.AcquiredLock;

import lombok.Builder;
import lombok.Value;
import org.redisson.api.RLock;

@OwnedBy(PL)
@Value
@Builder
public class RedisAcquiredLock implements AcquiredLock<RLock> {
  RLock lock;
  boolean isLeaseInfinite;

  @Override
  public void release() {
    if (lock != null && (lock.isLocked() || isLeaseInfinite)) {
      lock.unlock();
    }
  }

  @Override
  public void close() {
    release();
  }
}
