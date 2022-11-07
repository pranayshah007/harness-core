package io.harness.redis;

import java.util.concurrent.atomic.AtomicInteger;

public interface DelegateCacheService {
  Integer getDelegateTaskCache(String delegateId);

  void updateDelegateTaskCache(String delegateId, boolean increment);

}
