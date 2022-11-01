package io.harness.redis.intc;

import java.util.concurrent.atomic.AtomicInteger;

public interface DelegateCacheService {
  AtomicInteger getDelegateTaskCache(String delegateId);

  void updateDelegateTaskCache(String delegateId, AtomicInteger count);
}
