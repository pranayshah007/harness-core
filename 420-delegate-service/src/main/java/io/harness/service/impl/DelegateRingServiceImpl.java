/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.utils.DelegateRingConstants.RING_NAME_1;
import static io.harness.delegate.utils.DelegateRingConstants.RING_NAME_2;
import static io.harness.delegate.utils.DelegateRingConstants.RING_NAME_3;
import static io.harness.delegate.utils.DelegateRingConstants.RING_NAME_4;

import static java.util.Collections.singletonList;

import io.harness.delegate.beans.DelegateRing;
import io.harness.delegate.beans.DelegateRing.DelegateRingKeys;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.AccountDataProvider;
import io.harness.service.intfc.DelegateRingService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class DelegateRingServiceImpl implements DelegateRingService {
  private final HPersistence persistence;
  private final Cache<String, DelegateRing> delegateRingCache =
      Caffeine.newBuilder().maximumSize(10).expireAfterWrite(5, TimeUnit.MINUTES).build();

  private final AccountDataProvider accountDataProvider;
  @Override
  public String getDelegateImageTag(final String accountId) {
    return accountDataProvider.getDelegateRing(accountId).getDelegateImageTag();
  }

  @Override
  public String getUpgraderImageTag(final String accountId) {
    return accountDataProvider.getDelegateRing(accountId).getUpgraderImageTag();
  }

  @Override
  public List<String> getDelegateVersions(final String accountId) {
    return accountDataProvider.getDelegateRing(accountId).getDelegateVersions();
  }

  @Override
  public Map<String, List<String>> getDelegateVersionsForAllRings(boolean skipCache) {
    return Arrays.asList(RING_NAME_1, RING_NAME_2, RING_NAME_3, RING_NAME_4)
        .stream()
        .collect(Collectors.toMap(ringName
            -> ringName,
            ringName -> Optional.ofNullable(getDelegateVersionsForRing(ringName, false)).orElse(singletonList("N/A"))));
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
    return accountDataProvider.getDelegateRing(accountId).getWatcherVersions();
  }

  public String getJREVersion(final String accountId, final boolean isDelegate) {
    String jreVersionFromRing = "";
    try {
      if (isDelegate) {
        jreVersionFromRing = accountDataProvider.getDelegateRing(accountId).getDelegateJREVersion();
      } else {
        jreVersionFromRing = accountDataProvider.getDelegateRing(accountId).getWatcherJREVersion();
      }
      if (isNotEmpty(jreVersionFromRing)) {
        return jreVersionFromRing;
      }
    } catch (Exception ex) {
      log.error("Unable to fetch jre version from ring", ex);
      throw new IllegalStateException("Unable to fetch jre version");
    }
    return jreVersionFromRing;
  }

  @Override
  public Map<String, String> getWatcherVersionsAllRings(boolean skipCache) {
    return Arrays.asList(RING_NAME_1, RING_NAME_2, RING_NAME_3, RING_NAME_4)
        .stream()
        .collect(Collectors.toMap(ringName
            -> ringName,
            ringName -> Optional.ofNullable(getWatcherVersionsForRing(ringName, false)).orElse("N/A")));
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
}
