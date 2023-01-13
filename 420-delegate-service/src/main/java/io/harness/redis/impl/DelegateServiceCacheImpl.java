/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.redis.impl;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.serializer.DelegateServiceCacheRegistrar.DELEGATES_FROM_GROUP_CACHE;
import static io.harness.serializer.DelegateServiceCacheRegistrar.DELEGATE_CACHE;
import static io.harness.serializer.DelegateServiceCacheRegistrar.DELEGATE_GROUP_CACHE;
import static io.harness.serializer.DelegateServiceCacheRegistrar.PTS_COUNT_ACCOUNT_CACHE;
import static io.harness.serializer.DelegateServiceCacheRegistrar.TASK_CACHE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;
import io.harness.perpetualtask.internal.PerpetualTaskRecordDao;
import io.harness.persistence.HPersistence;
import io.harness.redis.intfc.DelegateServiceCache;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLocalCachedMap;

@Slf4j
@OwnedBy(DEL)
public class DelegateServiceCacheImpl implements DelegateServiceCache {
  @Inject @Named(TASK_CACHE) RLocalCachedMap<String, AtomicInteger> taskCache;
  @Inject @Named(DELEGATE_CACHE) RLocalCachedMap<String, Delegate> delegateCache;
  @Inject @Named(DELEGATE_GROUP_CACHE) RLocalCachedMap<String, DelegateGroup> delegateGroupCache;
  @Inject @Named(DELEGATES_FROM_GROUP_CACHE) RLocalCachedMap<String, List<Delegate>> delegatesFromGroupCache;
  @Inject @Named(PTS_COUNT_ACCOUNT_CACHE) RLocalCachedMap<String, Integer> perpetualTaskCountAccountCache;

  public enum UpdateOperation { INCREMENT, DECREMENT }

  @Inject private HPersistence persistence;
  @Inject private PerpetualTaskRecordDao perpetualTaskRecordDao;

  @Override
  public AtomicInteger getDelegateTaskCache(String delegateId) {
   /*  if (taskCache.getCachedMap() != null && taskCache.getCachedMap().get(delegateId) != null) {
       taskCache.getCachedMap().get(delegateId);
     }*/
    if (taskCache.get(delegateId) != null) {
      log.info("Get delegate task count from cache {}. ", taskCache.get(delegateId));
    }
    return taskCache.get(delegateId) != null ? taskCache.get(delegateId) : new AtomicInteger(0);
  }

  @Override
  public void updateDelegateTaskCache(@NotNull String delegateId, UpdateOperation updateOperation) {
    log.info("DelegateServiceCache: Before update {} for operation {} ", getDelegateTaskCache(delegateId),
        updateOperation.toString());
    if (taskCache.getCachedMap() == null || isEmpty(delegateId)) {
      log.error("Unable to fetch delegate task cache from redis cache");
      return;
    }
    taskCache.fastPutIfAbsent(delegateId, new AtomicInteger(0));
    if (taskCache.get(delegateId).get() <= 0 && updateOperation.equals(UpdateOperation.DECREMENT)) {
      log.error("Wrong value fetching for delegate task redis cache");
      // should never come here
      return;
    }
    updateCache(delegateId, updateOperation);
    log.info("DelegateServiceCache: After update {} for operation {} ", getDelegateTaskCache(delegateId),
        updateOperation.toString());
  }

  @Override
  public Delegate getDelegateCache(String delegateId) {
    if (delegateCache.get(delegateId) == null) {
      Delegate delegate = persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegateId).get();
      delegateCache.put(delegateId, delegate);
    }
    return delegateCache.get(delegateId);
  }

  @Override
  public DelegateGroup getDelegateGroupCache(String accountId, String delegateGroupId) {
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

  private void updateCache(String delegateId, UpdateOperation updateOperation) {
    if (updateOperation.equals(UpdateOperation.INCREMENT)) {
      taskCache.get(delegateId).getAndIncrement();
    }
    if (updateOperation.equals(UpdateOperation.DECREMENT)) {
      taskCache.get(delegateId).getAndDecrement();
    }
  }
}
