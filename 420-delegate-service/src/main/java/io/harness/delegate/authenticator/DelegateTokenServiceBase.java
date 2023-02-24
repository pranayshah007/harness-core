/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.authenticator;
import static io.harness.data.encoding.EncodingUtils.decodeBase64ToString;

import io.harness.beans.EncryptedData;
import io.harness.beans.FeatureName;
import io.harness.beans.SecretText;
import io.harness.delegate.beans.DelegateToken;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.secrets.SecretService;

import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.UUID;

@Singleton
public class DelegateTokenServiceBase {
  @Inject private FeatureFlagService featureFlagService;
  @Inject private SecretManager secretManager;
  @Inject private SecretService secretService;
  @Inject private HPersistence persistence;

  public String getDelegateTokenValue(DelegateToken delegateToken) {
    if (featureFlagService.isEnabled(FeatureName.READ_ENCRYPTED_DELEGATE_TOKEN, delegateToken.getAccountId())) {
      return decrypt(delegateToken);
    }
    return delegateToken.isNg() ? decodeBase64ToString(delegateToken.getValue()) : delegateToken.getValue();
  }

  public String encryptedTokenId(String accountId, String token) {
    SecretText secretText = SecretText.builder()
                                .value(token)
                                .hideFromListing(true)
                                .name(UUID.randomUUID().toString())
                                .scopedToAccount(true)
                                .kmsId(accountId)
                                .build();
    return secretManager.encryptSecretUsingGlobalSM(accountId, secretText, false).getUuid();
  }

  public String decrypt(DelegateToken delegateToken) {
    String encrypedValue = delegateToken.getEncryptedTokenId();
    if (delegateToken.getEncryptedTokenId() == null) {
      encrypedValue = encryptedTokenId(delegateToken.getAccountId(), delegateToken.getValue());
      delegateToken.setEncryptedTokenId(encrypedValue);
      persistence.save(delegateToken);
    }
    EncryptedData encryptedData = secretManager.getSecretById(delegateToken.getAccountId(), encrypedValue);
    return delegateToken.isNg() ? decodeBase64ToString(String.valueOf(secretService.fetchSecretValue(encryptedData)))
                                : String.valueOf(secretService.fetchSecretValue(encryptedData));
  }
}
