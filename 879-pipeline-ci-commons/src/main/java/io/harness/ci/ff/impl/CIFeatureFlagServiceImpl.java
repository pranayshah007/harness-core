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
import io.harness.beans.FeatureName;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ff.FeatureFlagService;
import io.harness.remote.client.CGRestUtils;
import io.harness.utils.system.SystemWrapper;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CI)
@Slf4j
@Singleton
public class CIFeatureFlagServiceImpl implements CIFeatureFlagService {
  // Keeping this @Nullable since featureFlagService can be initialised as null by some CI dependant modules.
  // Eg: STO, IACM
  @Inject @Nullable private FeatureFlagService featureFlagService;
  @Inject private AccountClient accountClient;

  private static final int CACHE_EVICTION_TIME_MINUTES = 10;

  private final LoadingCache<FeatureNameAndAccountId, Boolean> featureFlagCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(CACHE_EVICTION_TIME_MINUTES, TimeUnit.MINUTES)
          .build(new CacheLoader<>() {
            @org.jetbrains.annotations.NotNull
            @Override
            public Boolean load(
                @org.jetbrains.annotations.NotNull final FeatureNameAndAccountId featureNameAndAccountId) {
              return isFlagEnabledForAccountId(featureNameAndAccountId);
            }
          });

  public boolean isEnabled(@NotNull FeatureName featureName, String accountId) {
    try {
      return featureFlagCache.get(
          FeatureNameAndAccountId.builder().accountId(accountId).featureName(featureName).build());
    } catch (Exception e) {
      log.error("Error getting FF {} for account {} with error {}", featureName, accountId, e);
      return false;
    }
  }

  private boolean isFlagEnabledForAccountId(FeatureNameAndAccountId featureNameAndAccountId) {
    if (featureFlagService == null || SystemWrapper.checkIfEnvOnPremOrCommunity()) {
      return CGRestUtils.getResponse(accountClient.isFeatureFlagEnabled(
          featureNameAndAccountId.getFeatureName().name(), featureNameAndAccountId.getAccountId()));
    }
    return featureFlagService.isEnabled(
        featureNameAndAccountId.getFeatureName(), featureNameAndAccountId.getAccountId());
  }

  @Getter
  @Builder
  @EqualsAndHashCode
  static class FeatureNameAndAccountId {
    String accountId;
    FeatureName featureName;
  }
}
