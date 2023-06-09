/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dms.service;

import io.harness.beans.SecretText;
import io.harness.delegate.authenticator.DelegateSecretManager;
import io.harness.dms.client.DelegateSecretManagerClient;
import io.harness.ff.FeatureFlagService;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DMSSecretManagerImpl extends DelegateSecretManager {
  private final DelegateSecretManagerClient delegateSecretManagerClient;

  @Inject
  public DMSSecretManagerImpl(
      DelegateSecretManagerClient delegateSecretManagerClient, FeatureFlagService featureFlagService) {
    super(featureFlagService);
    this.delegateSecretManagerClient = delegateSecretManagerClient;
  }

  @Override
  public String fetchSecretValue(String accountId, String secretRecordId) {
    return CGRestUtils.getResponse(delegateSecretManagerClient.fetchSecretValue(accountId, secretRecordId));
  }

  @Override
  public String encryptSecretUsingGlobalSM(String accountId, SecretText secretText, boolean validateScopes) {
    return null;
  }
}
