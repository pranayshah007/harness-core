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
import io.harness.ff.FeatureFlagService;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
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

  private static final String DEPLOY_MODE = System.getenv("DEPLOY_MODE");
  private static final String DEPLOY_VERSION = System.getenv("DEPLOY_VERSION");

  public boolean isEnabled(String accountId, @NotNull FeatureName featureName) {
    try {
      return isFlagEnabledForAccountId(
          FeatureNameAndAccountId.builder().accountId(accountId).featureName(featureName).build());
    } catch (Exception e) {
      log.error("Error getting feature flags for given account: " + accountId);
      return false;
    }
  }

  public boolean isEnabled(String accountId, @NotNull String featureName) {
    try {
      return isFlagEnabledForAccountId(
          FeatureNameAndAccountId.builder().accountId(accountId).featureName(FeatureName.valueOf(featureName)).build());
    } catch (Exception e) {
      log.error("Error getting feature flags for given account: " + accountId);
      return false;
    }
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
