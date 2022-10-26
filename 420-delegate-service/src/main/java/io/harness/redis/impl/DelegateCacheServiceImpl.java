package io.harness.redis.impl;

import com.google.inject.Inject;
import io.harness.delegate.beans.DelegateTaskCache;
import io.harness.redis.intc.DelegateCacheService;
import io.harness.redis.intc.DelegateRedissonCacheManager;

public class DelegateCacheServiceImpl implements DelegateCacheService {

    @Inject
    DelegateRedissonCacheManager delegateRedissonCacheManager;

    @Override
    public DelegateTaskCache getDelegateTaskCache(String delegateId) {
        return delegateRedissonCacheManager.getCache("delegatetask", String.class, DelegateTaskCache.class,null).getCachedMap().get(0);
    }

    @Override
    public void updateDelegateTaskCache(String delegateId) {

    }

}
