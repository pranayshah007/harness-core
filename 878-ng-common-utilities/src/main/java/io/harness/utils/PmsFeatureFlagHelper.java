/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.remote.client.CGRestUtils;

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
  @Inject AccountClient accountClient;

  private static final int CACHE_EVICTION_TIME_MINUTES = 5;
  private static final String DEPLOY_MODE = System.getenv("DEPLOY_MODE");
  private static final String DEPLOY_VERSION = System.getenv("DEPLOY_VERSION");

  private final LoadingCache<FeatureNameAndAccountId, Boolean> featureFlagCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(CACHE_EVICTION_TIME_MINUTES, TimeUnit.MINUTES)
          .build(new CacheLoader<FeatureNameAndAccountId, Boolean>() {
            @Override
            public Boolean load(
                @org.jetbrains.annotations.NotNull final FeatureNameAndAccountId featureNameAndAccountId) {
              return isFlagEnabledForAccountId(featureNameAndAccountId);
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

  private boolean isFlagEnabledForAccountId(FeatureNameAndAccountId featureNameAndAccountId) {
    if (checkIfEnvOnPremOrCommunity()) {
      return CGRestUtils.getResponse(accountClient.isFeatureFlagEnabled(
          featureNameAndAccountId.getFeatureName().name(), featureNameAndAccountId.getAccountId()));
    }
    return featureFlagService.isEnabled(
        featureNameAndAccountId.getFeatureName(), featureNameAndAccountId.getAccountId());
  }

  private boolean checkIfEnvOnPremOrCommunity() {
    return (DEPLOY_MODE != null && (DEPLOY_MODE.equals("ONPREM") || DEPLOY_MODE.equals("KUBERNETES_ONPREM")))
        || (DEPLOY_VERSION != null && DEPLOY_VERSION.equals("COMMUNITY"));
  }

  @Getter
  @Builder
  @EqualsAndHashCode
  static class FeatureNameAndAccountId {
    String accountId;
    FeatureName featureName;
  }
}
