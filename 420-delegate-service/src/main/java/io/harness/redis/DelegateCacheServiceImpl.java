package io.harness.redis;

import static io.harness.serializer.DelegateServiceCacheRegistrar.TASK_CACHE;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import io.harness.cache.DelegateRedissonCacheManager;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLocalCachedMap;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class DelegateCacheServiceImpl implements DelegateCacheService {
  @Inject
  DelegateRedissonCacheManager delegateRedissonCacheManager;

  @Inject @Named(TASK_CACHE) RLocalCachedMap<String, Integer> taskCache;

  @Override
  public Integer getDelegateTaskCache(String delegateId) {
    return taskCache.getCachedMap().get(delegateId);
  }

  @Override @Synchronized
  public void updateDelegateTaskCache(String delegateId, boolean increment) {
    Integer count = taskCache.getCachedMap()!=null && taskCache.getCachedMap().get(delegateId)!=null ? taskCache.getCachedMap().get(delegateId) : 0;
    synchronized (count) {
      if (increment) {
        taskCache.put(delegateId, ++count);
      } else {
        taskCache.put(delegateId, --count);
      }
    }
    log.info("Cache values: {}", taskCache.getCachedMap());
  }
}
