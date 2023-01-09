/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.redis.intfc;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.redis.impl.DelegateServiceCacheImpl;
import io.harness.redis.impl.DelegateServiceCacheImpl.UpdateOperation;

import com.google.inject.ImplementedBy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@OwnedBy(DEL)
@ImplementedBy(DelegateServiceCacheImpl.class)
public interface DelegateServiceCache {
  AtomicInteger getDelegateTaskCache(String delegateId);

  void updateDelegateTaskCache(String delegateId, UpdateOperation updateOperation);

  Delegate getDelegateCache(String delegateId);

  DelegateGroup getDelegateGroupCache(String accountId, String delegateGroupId);

  List<Delegate> getDelegatesForGroup(String accountId, String delegateGroupId);

  Integer getNumberOfPerpetualTaskAssignedCount(String accountId, String delegateId);
}
