/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;
import io.harness.delegate.beans.DelegateProfile;
import io.harness.delegate.beans.DelegateProfile.DelegateProfileKeys;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateCache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateCacheImpl implements DelegateCache {
  private static final int MAX_DELEGATE_META_INFO_ENTRIES = 10000;

  @Inject private HPersistence persistence;

  private LoadingCache<String, Optional<Delegate>> delegateCache =
      CacheBuilder.newBuilder()
          .maximumSize(MAX_DELEGATE_META_INFO_ENTRIES)
          .expireAfterWrite(1, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Optional<Delegate>>() {
            @Override
            public Optional<Delegate> load(String delegateId) {
              return Optional.ofNullable(
                  persistence.createQuery(Delegate.class).filter(DelegateKeys.uuid, delegateId).get());
            }
          });

  private LoadingCache<ImmutablePair<String, String>, DelegateGroup> delegateGroupCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(30, TimeUnit.SECONDS)
          .build(new CacheLoader<ImmutablePair<String, String>, DelegateGroup>() {
            @Override
            public DelegateGroup load(ImmutablePair<String, String> delegateGroupKey) {
              return persistence.createQuery(DelegateGroup.class)
                  .filter(DelegateGroupKeys.accountId, delegateGroupKey.getLeft())
                  .filter(DelegateGroupKeys.uuid, delegateGroupKey.getRight())
                  .get();
            }
          });

  private LoadingCache<ImmutablePair<String, String>, DelegateProfile> delegateProfilesCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(30, TimeUnit.SECONDS)
          .build(new CacheLoader<ImmutablePair<String, String>, DelegateProfile>() {
            @Override
            public DelegateProfile load(ImmutablePair<String, String> delegateProfileKey) {
              return persistence.createQuery(DelegateProfile.class)
                  .filter(DelegateProfileKeys.accountId, delegateProfileKey.getLeft())
                  .filter(DelegateProfileKeys.uuid, delegateProfileKey.getRight())
                  .get();
            }
          });

  private LoadingCache<ImmutablePair<String, String>, List<Delegate>> delegatesFromGroupCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(5, TimeUnit.MINUTES)
          .build(new CacheLoader<ImmutablePair<String, String>, List<Delegate>>() {
            @Override
            public List<Delegate> load(ImmutablePair<String, String> delegateGroupKey) {
              return persistence.createQuery(Delegate.class)
                  .filter(DelegateKeys.accountId, delegateGroupKey.getLeft())
                  .filter(DelegateKeys.ng, true)
                  .filter(DelegateKeys.delegateGroupId, delegateGroupKey.getRight())
                  .asList();
            }
          });

  @Override
  public Delegate get(String accountId, String delegateId, boolean forceRefresh) {
    try {
      if (forceRefresh) {
        delegateCache.refresh(delegateId);
      }
      Delegate delegate = delegateCache.get(delegateId).orElse(null);
      if (delegate != null && (delegate.getAccountId() == null || !delegate.getAccountId().equals(accountId))) {
        // TODO: this is serious, we should not return the delegate if the account is not the expected one
        //       just to be on the safe side, make sure that all such scenarios are first fixed
        log.error("Delegate account id mismatch", new Exception(""));
      }
      return delegate;
    } catch (ExecutionException e) {
      log.error("Execution exception", e);
    } catch (UncheckedExecutionException e) {
      log.error("Delegate not found exception", e);
    }
    return null;
  }

  // only for task assignment logic we should fetch from cache, since we process very heavy number of tasks per minute.
  @Override
  public DelegateGroup getDelegateGroup(String accountId, String delegateGroupId) {
    if (isBlank(accountId) || isBlank(delegateGroupId)) {
      return null;
    }

    try {
      return delegateGroupCache.get(ImmutablePair.of(accountId, delegateGroupId));
    } catch (ExecutionException | CacheLoader.InvalidCacheLoadException e) {
      return null;
    }
  }

  @Override
  public DelegateProfile getDelegateProfile(String accountId, String delegateProfileId) {
    if (StringUtils.isBlank(delegateProfileId)) {
      return null;
    }

    try {
      return delegateProfilesCache.get(ImmutablePair.of(accountId, delegateProfileId));
    } catch (ExecutionException | CacheLoader.InvalidCacheLoadException e) {
      return null;
    }
  }

  @Override
  public void invalidateDelegateProfileCache(String accountId, String delegateProfileId) {
    delegateProfilesCache.invalidate(ImmutablePair.of(accountId, delegateProfileId));
  }

  @Override
  public List<Delegate> getDelegatesForGroup(String accountId, String delegateGroupId) {
    if (isBlank(accountId) || isBlank(delegateGroupId)) {
      return null;
    }
    try {
      return delegatesFromGroupCache.get(ImmutablePair.of(accountId, delegateGroupId));
    } catch (ExecutionException | CacheLoader.InvalidCacheLoadException e) {
      log.warn("Unable to getDelegates from cache based on group id");
      return null;
    }
  }
}
