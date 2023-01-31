/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.redis.impl;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.serializer.DelegateServiceCacheRegistrar.DELEGATES_FROM_GROUP_CACHE;
import static io.harness.serializer.DelegateServiceCacheRegistrar.DELEGATE_CACHE;
import static io.harness.serializer.DelegateServiceCacheRegistrar.DELEGATE_GROUP_CACHE;
import static io.harness.serializer.DelegateServiceCacheRegistrar.PERPETUAL_TASK_COUNT_DELEGATE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;
import io.harness.perpetualtask.internal.PerpetualTaskRecordDao;
import io.harness.persistence.HPersistence;
import io.harness.redis.intfc.DelegateRedissonCacheManager;
import io.harness.redis.intfc.DelegateServiceCache;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLocalCachedMap;

@Slf4j
@OwnedBy(DEL)
public class DelegateServiceCacheImpl implements DelegateServiceCache {
  @Inject @Named(DELEGATE_CACHE) RLocalCachedMap<String, Delegate> delegateCache;
  @Inject @Named(DELEGATE_GROUP_CACHE) RLocalCachedMap<String, DelegateGroup> delegateGroupCache;
  @Inject @Named(DELEGATES_FROM_GROUP_CACHE) RLocalCachedMap<String, List<Delegate>> delegatesFromGroupCache;
  @Inject @Named(PERPETUAL_TASK_COUNT_DELEGATE) RLocalCachedMap<String, Integer> perpetualTaskCountAccountCache;

  @Inject DelegateRedissonCacheManager delegateRedissonCacheManager;

  @Inject private HPersistence persistence;
  @Inject private PerpetualTaskRecordDao perpetualTaskRecordDao;

  @Override
  public Delegate getDelegate(String delegateId) {
    if (delegateCache.get(delegateId) == null) {
      Delegate delegate = persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegateId).get();
      delegateCache.put(delegateId, delegate);
    }
    return delegateCache.get(delegateId);
  }

  @Override
  public DelegateGroup getDelegateGroup(String accountId, String delegateGroupId) {
    if (delegateGroupCache.get(delegateGroupId) == null) {
      DelegateGroup delegateGroup = persistence.createQuery(DelegateGroup.class)
                                        .filter(DelegateGroupKeys.accountId, accountId)
                                        .filter(DelegateGroupKeys.uuid, delegateGroupId)
                                        .get();
      delegateGroupCache.put(delegateGroupId, delegateGroup);
    }
    return delegateGroupCache.get(delegateGroupId);
  }

  @Override
  public List<Delegate> getDelegatesForGroup(String accountId, String delegateGroupId) {
    if (delegateGroupCache.get(delegateGroupId) == null) {
      List<Delegate> delegateList = persistence.createQuery(Delegate.class)
                                        .filter(DelegateKeys.accountId, accountId)
                                        .filter(DelegateKeys.ng, true)
                                        .filter(DelegateKeys.delegateGroupId, delegateGroupId)
                                        .asList();
      delegatesFromGroupCache.put(delegateGroupId, delegateList);
    }
    return delegatesFromGroupCache.get(delegateGroupId);
  }

  @Override
  public Integer getNumberOfPerpetualTaskAssignedCount(String accountId, String delegateId) {
    if (perpetualTaskCountAccountCache.get(delegateId) == null) {
      perpetualTaskCountAccountCache.put(
          delegateId, perpetualTaskRecordDao.listAssignedTasks(delegateId, accountId).size());
    }
    return perpetualTaskCountAccountCache.get(delegateId);
  }
}
