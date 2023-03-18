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

import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.UUID;

@Singleton
public class DelegateTokenEncryptDecrypt {
  private FeatureFlagService featureFlagService;
  private SecretManager secretManager;

  @Inject
  public DelegateTokenEncryptDecrypt(FeatureFlagService featureFlagService, SecretManager secretManager) {
    this.featureFlagService = featureFlagService;
    this.secretManager = secretManager;
  }

  public String getDelegateTokenValue(DelegateToken delegateToken) {
    if (featureFlagService.isEnabled(FeatureName.READ_ENCRYPTED_DELEGATE_TOKEN, delegateToken.getAccountId())) {
      return decrypt(delegateToken);
    }
    return delegateToken.isNg() ? decodeBase64ToString(delegateToken.getValue()) : delegateToken.getValue();
  }

  // this flow doesn't need to decodeBase64, it used for to display token in UI or yaml
  public String getBase64EncodedTokenValue(DelegateToken delegateToken) {
    if (featureFlagService.isEnabled(FeatureName.READ_ENCRYPTED_DELEGATE_TOKEN, delegateToken.getAccountId())) {
      return secretManager.fetchSecretValue(delegateToken.getAccountId(), delegateToken.getEncryptedTokenId());
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
    return secretManager.encryptSecretUsingGlobalSM(accountId, secretText, false).getUuid();
  }

  public String decrypt(DelegateToken delegateToken) {
    return delegateToken.isNg()
        ? decodeBase64ToString(
            secretManager.fetchSecretValue(delegateToken.getAccountId(), delegateToken.getEncryptedTokenId()))
        : secretManager.fetchSecretValue(delegateToken.getAccountId(), delegateToken.getEncryptedTokenId());
  }
}
