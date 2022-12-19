package io.harness.redis.impl;

import static io.harness.serializer.DelegateServiceCacheRegistrar.TASK_CACHE;

import io.harness.cache.DelegateRedissonCacheManager;
import io.harness.delegate.beans.Delegate;
import io.harness.redis.intfc.DelegateServiceCache;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLocalCachedMap;

@Slf4j
public class DelegateServiceCacheImpl implements DelegateServiceCache {
  @Inject DelegateRedissonCacheManager delegateRedissonCacheManager;
  @Inject @Named(TASK_CACHE) RLocalCachedMap<String, AtomicInteger> taskCache;
  public enum UpdateOperation { INCREMENT, DECREMENT }
  ;

  @Override
  public AtomicInteger getDelegateTaskCache(String delegateId) {
    return taskCache.getCachedMap().get(delegateId);
  }

  @Override
  public void updateDelegateTaskCache(String delegateId, UpdateOperation updateOperation) {
    if (taskCache.getCachedMap() == null || delegateId == null) {
      return;
    }
    taskCache.fastPutIfAbsent(delegateId, new AtomicInteger(0));
    if (taskCache.get(delegateId).get() <= 0 && updateOperation.equals(UpdateOperation.DECREMENT)) {
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
