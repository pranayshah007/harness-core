/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.impl;

import io.harness.delegate.beans.DelegateRing;
import io.harness.delegate.beans.DelegateRing.DelegateRingKeys;
import io.harness.delegate.service.intfc.DelegateRingService;
import io.harness.persistence.HPersistence;

import software.wings.service.intfc.AccountService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DelegateRingServiceImpl implements DelegateRingService {
  private final HPersistence persistence;
  private final AccountService accountService;
  private final Cache<String, DelegateRing> delegateRingCache =
      Caffeine.newBuilder().maximumSize(10).expireAfterWrite(5, java.util.concurrent.TimeUnit.MINUTES).build();

  @Override
  public String getDelegateImageTag(final String accountId) {
    return getDelegateRing(accountId).getDelegateImageTag();
  }

  @Override
  public String getUpgraderImageTag(final String accountId) {
    return getDelegateRing(accountId).getUpgraderImageTag();
  }

  @Override
  public List<String> getDelegateVersions(final String accountId) {
    return getDelegateRing(accountId).getDelegateVersions();
  }

  @Override
  public List<String> getDelegateVersionsForRing(String ringName, boolean skipCache) {
    if (!skipCache) {
      DelegateRing ring = delegateRingCache.getIfPresent(ringName);
      if (ring != null) {
        return ring.getDelegateVersions();
      }
    }
    DelegateRing ringFromDB =
        persistence.createQuery(DelegateRing.class).filter(DelegateRingKeys.ringName, ringName).get();
    if (!skipCache) {
      delegateRingCache.put(ringName, ringFromDB);
    }

    return ringFromDB.getDelegateVersions();
  }

  @Override
  public String getWatcherVersions(final String accountId) {
    return getDelegateRing(accountId).getWatcherVersions();
  }

  @Override
  public String getWatcherVersionsForRing(String ringName, boolean skipCache) {
    if (!skipCache) {
      DelegateRing ring = delegateRingCache.getIfPresent(ringName);
      if (ring != null) {
        return ring.getWatcherVersions();
      }
    }
    DelegateRing ringFromDB =
        persistence.createQuery(DelegateRing.class).filter(DelegateRingKeys.ringName, ringName).get();
    if (!skipCache) {
      delegateRingCache.put(ringName, ringFromDB);
    }
    return ringFromDB.getWatcherVersions();
  }

  private DelegateRing getDelegateRing(String accountId) {
    return persistence.createQuery(DelegateRing.class)
        .filter(DelegateRingKeys.ringName, accountService.get(accountId).getRingName())
        .get();
  }
}
