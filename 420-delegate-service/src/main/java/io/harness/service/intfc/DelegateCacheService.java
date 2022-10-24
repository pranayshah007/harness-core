package io.harness.service.intfc;

import io.harness.delegate.beans.DelegateTaskCache;

public interface DelegateCacheService {
    DelegateTaskCache get(String delegateId);
}
