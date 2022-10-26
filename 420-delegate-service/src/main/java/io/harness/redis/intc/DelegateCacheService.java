package io.harness.redis.intc;

import io.harness.delegate.beans.DelegateTaskCache;

public interface DelegateCacheService {

    DelegateTaskCache getDelegateTaskCache(String delegateId);

    void updateDelegateTaskCache(String delegateId);
}
