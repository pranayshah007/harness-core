/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.client.RequestExecutor;
import io.harness.cvng.client.VerificationManagerClient;
import io.harness.cvng.core.services.api.FeatureFlagService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
@OwnedBy(HarnessTeam.CV)
public class FeatureFlagServiceImpl implements FeatureFlagService {
  @Inject private VerificationManagerClient verificationManagerClient;
  @Inject private RequestExecutor requestExecutor;
  @Override
  public boolean isFeatureFlagEnabled(String accountId, String featureFlagName) {
    Preconditions.checkNotNull(accountId);
    Preconditions.checkNotNull(featureFlagName);
    return requestExecutor.execute(verificationManagerClient.isFeatureEnabled(featureFlagName, accountId))
        .getResource();
  }

  public boolean isGlobalFlagEnabled(String featureFlagName) {
    Preconditions.checkNotNull(featureFlagName);
    return requestExecutor.execute(verificationManagerClient.getFeatureFlag(featureFlagName)).getResource().isEnabled();
  }
}
