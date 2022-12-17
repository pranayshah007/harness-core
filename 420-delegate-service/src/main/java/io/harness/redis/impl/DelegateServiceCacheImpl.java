package io.harness.redis.impl;

import static io.harness.serializer.DelegateServiceCacheRegistrar.TASK_CACHE;

import io.harness.cache.DelegateRedissonCacheManager;
import io.harness.delegate.beans.Delegate;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;

import io.harness.redis.intfc.DelegateServiceCache;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLocalCachedMap;

@Slf4j
public class DelegateServiceCacheImpl implements DelegateServiceCache {
  @Inject DelegateRedissonCacheManager delegateRedissonCacheManager;

  @Inject @Named(TASK_CACHE) RLocalCachedMap<String, RAtomicLong> taskCache;

  @Override
  public RAtomicLong getDelegateTaskCache(String delegateId) {
    return taskCache.getCachedMap().get(delegateId);
  }

  @Override
  @Synchronized
  public void updateDelegateTaskCache(String delegateId, boolean increment) {
    if (taskCache.getCachedMap() == null) {
      return;
    }
    //taskCache.getCachedMap().putIfAbsent(delegateId,0l)
    long l = increment ? taskCache.getCachedMap().get(delegateId).incrementAndGet()
                       : taskCache.getCachedMap().get(delegateId).decrementAndGet();
    log.info("updated value {} , Cache values: {}", l, taskCache.getCachedMap());
  }

  @Override
  public Delegate getDelegateCache(String delegateId) {
    // redis cache implementation, TBD
    return null;
  }

  @Override
  public List<Delegate> getDelegatesForGroup(String accountId, String delegateGroupId) {
    // redis cache implementation, TBD
    return null;
  }
}
