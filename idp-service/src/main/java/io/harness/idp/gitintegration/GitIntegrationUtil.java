/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.gitintegration;

import io.harness.beans.DecryptedSecretValue;
import io.harness.exception.InvalidRequestException;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GitIntegrationUtil {
  public EnvironmentSecret getEnvironmentSecret(SecretManagerClientService ngSecretService, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String tokenSecretIdentifier, String connectorIdentifier,
      String tokenType) {
    EnvironmentSecret environmentSecret = new EnvironmentSecret();
    environmentSecret.secretIdentifier(tokenSecretIdentifier);
    environmentSecret.setEnvName(tokenType);
    DecryptedSecretValue decryptedSecretValue = ngSecretService.getDecryptedSecretValue(
        accountIdentifier, orgIdentifier, projectIdentifier, tokenSecretIdentifier);
    if (decryptedSecretValue == null) {
      throw new InvalidRequestException(String.format(
          "Secret not found for identifier : [%s], accountId: [%s]", connectorIdentifier, accountIdentifier));
    }
    environmentSecret.setDecryptedValue(decryptedSecretValue.getDecryptedValue());
    return environmentSecret;
  }
}
