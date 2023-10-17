/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.rsa;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Objects.isNull;

import io.harness.beans.DecryptedSecretValue;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretRequestWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.oidc.entities.OidcJwks;
import io.harness.oidc.jwks.OidcJwksUtility;
import io.harness.rsa.RSAKeyPairPEM;
import io.harness.rsa.RSAKeysUtils;
import io.harness.rsa.RsaKeyPair;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OidcRsaKeyService {
  @Inject @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;
  @Inject RSAKeysUtils rsaKeysUtils;
  @Inject OidcJwksUtility oidcJwksUtility;

  public String getOicJwksPrivateKeyPEM(String accountId) {
    OidcJwks oidcJwks = oidcJwksUtility.getJwksKeys(accountId);

    if (isNull(oidcJwks)) {
      oidcJwks = generateOidcJwks(accountId);
      oidcJwksUtility.saveOidcJwks(oidcJwks);
    }

    RsaKeyPair rsaKeyPair = oidcJwks.getRsaKeyPair();

    if (isNull(rsaKeyPair)) {
      log.error("RSA key pair not present for Oidc JWKS config for account- {}", accountId);
      return null;
    }

    String privateKeyRef = rsaKeyPair.getPrivateKeyRef();

    if (isNotEmpty(privateKeyRef)) {
      SecretRefData privateKeyRefData = SecretRefHelper.createSecretRef(privateKeyRef);

      DecryptedSecretValue decryptedSecretValue =
          ngSecretService.getDecryptedSecretValue(accountId, null, null, privateKeyRefData.getIdentifier());

      if (!isNull(decryptedSecretValue)) {
        return decryptedSecretValue.getDecryptedValue();
      }
    }
    return null;
  }

  private String encryptPrivateKey(String accountId, String privateKeyPem) {
    String privateKeyIdentifier = "__INTERNAL_" + accountId + "_oidc_privateKey_PEM__";
    SecretRequestWrapper secretRequestWrapper =
        SecretRequestWrapper.builder()
            .secret(SecretDTOV2.builder()
                        .identifier(privateKeyIdentifier)
                        .name(privateKeyIdentifier)
                        .orgIdentifier(null)
                        .projectIdentifier(null)
                        .type(SecretType.SecretText)
                        .spec(SecretTextSpecDTO.builder()
                                  .secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                  .valueType(ValueType.Inline)
                                  .value(privateKeyPem)
                                  .build())
                        .build())
            .build();
    // TODO- The ngSecretService REST call will be replaced by a service call to NGEncryptedDataService after package
    // refactoring
    ngSecretService.create(accountId, null, null, false, secretRequestWrapper);
    return SecretRefHelper.getSecretConfigString(
        SecretRefData.builder().identifier(privateKeyIdentifier).scope(Scope.ACCOUNT).build());
  }

  private OidcJwks generateOidcJwks(String accountId) {
    return OidcJwks.builder().accountId(accountId).rsaKeyPair(generateRsaKeyPair(accountId)).build();
  }

  private RsaKeyPair generateRsaKeyPair(String accountId) {
    RSAKeyPairPEM rsaKeyPairPEM = rsaKeysUtils.generateKeyPairPEM();
    String privateKeyRef = encryptPrivateKey(accountId, rsaKeyPairPEM.getPrivateKeyPem());
    return RsaKeyPair.builder().publicKey(rsaKeyPairPEM.getPublicKeyPem()).privateKeyRef(privateKeyRef).build();
  }
}
