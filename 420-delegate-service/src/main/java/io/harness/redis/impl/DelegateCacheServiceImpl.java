package io.harness.redis.impl;

import com.google.inject.Inject;
import io.harness.delegate.beans.DelegateTaskCache;
import io.harness.redis.intc.DelegateCacheService;
import io.harness.redis.intc.DelegateRedissonCacheManager;

import java.util.concurrent.atomic.AtomicInteger;

public class DelegateCacheServiceImpl implements DelegateCacheService {

    @Inject
    DelegateRedissonCacheManager delegateRedissonCacheManager;

    @Override
    public AtomicInteger getDelegateTaskCache(String delegateId) {
        return delegateRedissonCacheManager.getCache("delegatetaskassignment", String.class, AtomicInteger.class,null).getCachedMap().get(delegateId);
    }

    @Override
    public void updateDelegateTaskCache(String delegateId, AtomicInteger count) {
       delegateRedissonCacheManager.getCache("delegatetaskassignment", String.class, Integer.class,null).getCachedMap().put(delegateId,count.intValue());
    }

}
