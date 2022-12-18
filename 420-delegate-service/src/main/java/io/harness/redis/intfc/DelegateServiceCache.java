package io.harness.redis.intfc;

import io.harness.delegate.beans.Delegate;
import io.harness.redis.impl.DelegateServiceCacheImpl;
import io.harness.redis.impl.DelegateServiceCacheImpl.UpdateOperation;

import com.google.inject.ImplementedBy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@ImplementedBy(DelegateServiceCacheImpl.class)
public interface DelegateServiceCache {
  AtomicInteger getDelegateTaskCache(String delegateId);
  void updateDelegateTaskCache(String delegateId, UpdateOperation updateOperation);
  Delegate getDelegateCache(String delegateId);
  List<Delegate> getDelegatesForGroup(String accountId, String delegateGroupId);
}
