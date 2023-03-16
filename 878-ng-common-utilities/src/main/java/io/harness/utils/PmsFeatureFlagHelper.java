/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PmsFeatureFlagHelper implements PmsFeatureFlagService {
  @Inject FeatureFlagService featureFlagService;

  private static final int CACHE_EVICTION_TIME_MINUTES = 5;

  private final LoadingCache<FeatureNameAndAccountId, Boolean> featureFlagCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(CACHE_EVICTION_TIME_MINUTES, TimeUnit.MINUTES)
          .build(new CacheLoader<FeatureNameAndAccountId, Boolean>() {
            @Override
            public Boolean load(
                @org.jetbrains.annotations.NotNull final FeatureNameAndAccountId featureNameAndAccountId) {
              return featureFlagService.isEnabled(
                  featureNameAndAccountId.getFeatureName(), featureNameAndAccountId.getAccountId());
            }
          });

  public boolean isEnabled(String accountId, @NotNull FeatureName featureName) {
    try {
      return featureFlagCache.get(
          FeatureNameAndAccountId.builder().accountId(accountId).featureName(featureName).build());
    } catch (Exception e) {
      log.error("Error getting feature flags for given account: " + accountId);
      return false;
    }
  }

  public boolean isEnabled(String accountId, @NotNull String featureName) {
    try {
      return featureFlagCache.get(
          FeatureNameAndAccountId.builder().accountId(accountId).featureName(FeatureName.valueOf(featureName)).build());
    } catch (Exception e) {
      log.error("Error getting feature flags for given account: " + accountId);
      return false;
    }
  }

  public boolean refreshCacheForGivenAccountId(String accountId) throws InvalidRequestException {
    throw new InvalidRequestException("Cache will be automatically refreshed within 5 mins");
  }

  @Getter
  @Builder
  @EqualsAndHashCode
  static class FeatureNameAndAccountId {
    String accountId;
    FeatureName featureName;
  }
}
