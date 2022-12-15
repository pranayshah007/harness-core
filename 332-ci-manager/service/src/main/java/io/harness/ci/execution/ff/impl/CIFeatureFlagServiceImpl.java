/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.ff.impl;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureName;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.remote.client.CGRestUtils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CI)
@Slf4j
@Singleton
public class CIFeatureFlagServiceImpl implements CIFeatureFlagService {
  @Inject private AccountClient accountClient;

  private static final int CACHE_EVICTION_TIME_MINUTES = 10;

  private final LoadingCache<String, Set<String>> featureFlagCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(CACHE_EVICTION_TIME_MINUTES, TimeUnit.MINUTES)
          .build(new CacheLoader<String, Set<String>>() {
            @Override
            public Set<String> load(@org.jetbrains.annotations.NotNull final String accountId) {
              return listAllEnabledFeatureFlagsForAccount(accountId);
            }
          });

  public boolean isEnabled(@NotNull FeatureName featureName, String accountId) {
    try {
      return featureFlagCache.get(accountId).contains(featureName.name());
    } catch (Exception e) {
      log.error("Error getting FF {} for account {} with error {}", featureName, accountId, e);
      return false;
    }
  }

  private Set<String> listAllEnabledFeatureFlagsForAccount(String accountId) {
    log.info("Getting all FFs for account: {}", accountId);
    return CGRestUtils.getResponse(accountClient.listAllFeatureFlagsForAccount(accountId))
        .stream()
        .filter(FeatureFlag::isEnabled)
        .map(FeatureFlag::getName)
        .collect(Collectors.toSet());
  }
}
