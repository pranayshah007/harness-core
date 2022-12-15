/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.featureFlag;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class CDFeatureFlagHelper {
  @Inject private AccountClient accountClient;

  public boolean isEnabled(String accountId, @NotNull FeatureName featureName) {
    return CGRestUtils.getResponse(accountClient.isFeatureFlagEnabled(featureName.name(), accountId));
  }
}
