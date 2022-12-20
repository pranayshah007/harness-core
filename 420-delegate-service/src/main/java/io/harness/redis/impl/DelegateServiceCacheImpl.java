/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.redis.impl;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.serializer.DelegateServiceCacheRegistrar.TASK_CACHE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.redis.intfc.DelegateServiceCache;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLocalCachedMap;

@Slf4j
@OwnedBy(DEL)
public class DelegateServiceCacheImpl implements DelegateServiceCache {
  @Inject @Named(TASK_CACHE) RLocalCachedMap<String, AtomicInteger> taskCache;
  public enum UpdateOperation { INCREMENT, DECREMENT }

  @Override
  public AtomicInteger getDelegateTaskCache(String delegateId) {
    return taskCache.getCachedMap().get(delegateId);
  }

  @Override
  public void updateDelegateTaskCache(@NotNull String delegateId, UpdateOperation updateOperation) {
    if (taskCache.getCachedMap() == null) {
      log.error("Unable to fetch delegate task cache from redis cache");
      return;
    }
    taskCache.fastPutIfAbsent(delegateId, new AtomicInteger(0));
    if (taskCache.get(delegateId).get() <= 0 && updateOperation.equals(UpdateOperation.DECREMENT)) {
      log.error("Wrong value fetching for delegate task redis cache");
      // should never come here
      return;
    }
    updateCache(delegateId, updateOperation);
  }

  @Override
  public Delegate getDelegateCache(String delegateId) {
    // redis delegate cache implementation, TBD
    return null;
  }

  @Override
  public List<Delegate> getDelegatesForGroup(String accountId, String delegateGroupId) {
    // redis delegate group cache implementation, TBD
    return null;
  }

  @Synchronized
  private void updateCache(String delegateId, UpdateOperation updateOperation) {
    if (updateOperation.equals(UpdateOperation.INCREMENT)) {
      taskCache.get(delegateId).getAndIncrement();
    }
    if (updateOperation.equals(UpdateOperation.DECREMENT)) {
      taskCache.get(delegateId).getAndDecrement();
    }
  }
}
