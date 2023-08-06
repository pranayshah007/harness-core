/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.authenticator;

import static io.harness.data.encoding.EncodingUtils.decodeBase64ToString;

import io.harness.beans.FeatureName;
import io.harness.beans.SecretText;
import io.harness.delegate.beans.DelegateToken;
import io.harness.ff.FeatureFlagService;

import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class DelegateSecretManager {
  final FeatureFlagService featureFlagService;

  public String getDelegateTokenValue(DelegateToken delegateToken) {
    if (featureFlagService.isEnabled(FeatureName.READ_ENCRYPTED_DELEGATE_TOKEN, delegateToken.getAccountId())) {
      return decodeBase64ToString(decrypt(delegateToken));
    }
    return delegateToken.isNg() ? decodeBase64ToString(delegateToken.getValue()) : delegateToken.getValue();
  }

  public String getBase64EncodedTokenValue(DelegateToken delegateToken) {
    if (featureFlagService.isEnabled(FeatureName.READ_ENCRYPTED_DELEGATE_TOKEN, delegateToken.getAccountId())) {
      return decrypt(delegateToken);
    }
    return delegateToken.getValue();
  }

  public String encrypt(String accountId, String tokenValue, String tokenName) {
    String name = String.format("%s_%s", tokenName, UUID.randomUUID());
    SecretText secretText = SecretText.builder()
                                .value(tokenValue)
                                .hideFromListing(true)
                                .name(name)
                                .scopedToAccount(true)
                                .kmsId(accountId)
                                .build();
    return encryptSecretUsingGlobalSM(accountId, secretText, false);
  }

  public String decrypt(DelegateToken delegateToken) {
    return fetchSecretValue(delegateToken.getAccountId(), delegateToken.getEncryptedTokenId());
  }

  protected abstract String fetchSecretValue(String accountId, String ecryptedTokenId);
  protected abstract String encryptSecretUsingGlobalSM(String accountId, SecretText secretText, boolean validateScopes);
}
