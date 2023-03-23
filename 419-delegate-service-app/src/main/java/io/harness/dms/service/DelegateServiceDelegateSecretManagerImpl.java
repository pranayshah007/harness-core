package io.harness.dms.service;

import io.harness.beans.SecretText;
import io.harness.delegate.authenticator.DelegateSecretManager;
import io.harness.dms.client.DelegateSecretManagerClient;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Singleton
public class DelegateServiceDelegateSecretManagerImpl extends DelegateSecretManager {
  DelegateSecretManagerClient delegateSecretManagerClient;

  @Override
  public String fetchSecretValue(String accountId, String secretRecordId) {
    return CGRestUtils.getResponse(delegateSecretManagerClient.fetchSecretValue(accountId, secretRecordId));
  }

  @Override
  public String encryptSecretUsingGlobalSM(String accountId, SecretText secretText, boolean validateScopes) {
    return null;
  }
}
