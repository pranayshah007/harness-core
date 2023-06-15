/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import io.harness.beans.SecretText;
import io.harness.delegate.authenticator.DelegateSecretManager;
import io.harness.ff.FeatureFlagService;

import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DelegateSecretManagerImpl extends DelegateSecretManager {
  private final SecretManager secretManager;

  @Inject
  public DelegateSecretManagerImpl(SecretManager secretManager, FeatureFlagService featureFlagService) {
    super(featureFlagService);
    this.secretManager = secretManager;
  }

  @Override
  public String fetchSecretValue(String accountId, String secretRecordId) {
    return secretManager.fetchSecretValue(accountId, secretRecordId);
  }

  @Override
  public String encryptSecretUsingGlobalSM(String accountId, SecretText secretText, boolean validateScopes) {
    return secretManager.encryptSecretUsingGlobalSM(accountId, secretText, false).getUuid();
  }
}
