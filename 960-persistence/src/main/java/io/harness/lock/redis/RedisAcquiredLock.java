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
  boolean isSentinelMode;

  @Override
  public void release() {
    if (isSentinelMode) {
      releaseInSentinelMode();
    } else if (lock != null && (lock.isLocked() || isLeaseInfinite)) {
      lock.unlock();
    }
  }

  @Override
  public void close() {
    release();
  }

  /**
   * This is implemented only as workaround for handling race condition in sentinel mode.
   * In sentinel mode after redisson library upgrade from 3.13.3 to 3.17.7 we started facing issue where lock was
   * hanging forever on lock.unlock() method call. During our investigation we found open bugs found on Redisson lib
   * with similar behaviour See below for more details
   * https://harness.atlassian.net/jira/software/c/projects/PL/issues/PL-31692
   * https://github.com/redisson/redisson/issues/4822
   * https://github.com/redisson/redisson/issues/4878
   * Based on multiple trials we found that checking lock.isHeldByCurrentThread() along with lock.forceUnlock() is a
   * feasible workaround considering Factors like Senitinel mode only being used in SMP, Load in SMP etc.
   */
  private void releaseInSentinelMode() {
    if (lock != null && (lock.isHeldByCurrentThread() || isLeaseInfinite)) {
      lock.forceUnlock();
    }
  }
}
