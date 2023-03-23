package io.harness.service.impl;

import io.harness.beans.SecretText;
import io.harness.delegate.authenticator.DelegateSecretManager;

import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Singleton
public class DelegateSecretManagerImpl extends DelegateSecretManager {
  private final SecretManager secretManager;

  @Override
  public String fetchSecretValue(String accountId, String secretRecordId) {
    return secretManager.fetchSecretValue(accountId, secretRecordId);
  }

  @Override
  public String encryptSecretUsingGlobalSM(String accountId, SecretText secretText, boolean validateScopes) {
    return secretManager.encryptSecretUsingGlobalSM(accountId, secretText, false).getUuid();
  }
}
