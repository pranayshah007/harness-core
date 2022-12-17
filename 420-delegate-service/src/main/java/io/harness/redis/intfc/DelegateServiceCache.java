package io.harness.redis.intfc;

import io.harness.delegate.beans.Delegate;
import org.redisson.api.RAtomicLong;

import java.util.List;

public interface DelegateServiceCache {
  RAtomicLong getDelegateTaskCache(String delegateId);
  void updateDelegateTaskCache(String delegateId, boolean increment);
  Delegate getDelegateCache(String delegateId);
  List<Delegate> getDelegatesForGroup(String accountId, String delegateGroupId);
}
