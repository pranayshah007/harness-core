/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;

@OwnedBy(PL)
public class FeatureFlagHelper {
  @Inject AccountClient accountClient;

  public boolean isEnabled(String accountId, FeatureName featureName) {
    return CGRestUtils.getResponse(accountClient.isFeatureFlagEnabled(featureName.name(), accountId));
  }
}
