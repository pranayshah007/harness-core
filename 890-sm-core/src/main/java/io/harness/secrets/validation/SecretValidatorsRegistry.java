/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.validation;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.secrets.validation.SecretValidators.AWS_SECRET_MANAGAER_VALIDATOR;
import static io.harness.secrets.validation.SecretValidators.AZURE_SECRET_MANAGER_VALIDATOR;
import static io.harness.secrets.validation.SecretValidators.COMMON_SECRET_MANAGER_VALIDATOR;
import static io.harness.secrets.validation.SecretValidators.GCP_SECRET_MANAGER_VALIDATOR;
import static io.harness.secrets.validation.SecretValidators.VAULT_SECRET_MANAGER_VALIDATOR;
import static io.harness.security.encryption.EncryptionType.AWS_SECRETS_MANAGER;
import static io.harness.security.encryption.EncryptionType.AZURE_VAULT;
import static io.harness.security.encryption.EncryptionType.CUSTOM;
import static io.harness.security.encryption.EncryptionType.GCP_KMS;
import static io.harness.security.encryption.EncryptionType.GCP_SECRETS_MANAGER;
import static io.harness.security.encryption.EncryptionType.KMS;
import static io.harness.security.encryption.EncryptionType.LOCAL;
import static io.harness.security.encryption.EncryptionType.VAULT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.SecretManagementException;
import io.harness.security.encryption.EncryptionType;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@OwnedBy(PL)
public class SecretValidatorsRegistry {
  private final Map<EncryptionType, SecretValidators> registeredSecretValidators;
  private final Injector injector;

  @Inject
  public SecretValidatorsRegistry(Injector injector) {
    this.injector = injector;
    this.registeredSecretValidators = new EnumMap<>(EncryptionType.class);
    registeredSecretValidators.put(LOCAL, COMMON_SECRET_MANAGER_VALIDATOR);
    registeredSecretValidators.put(KMS, COMMON_SECRET_MANAGER_VALIDATOR);
    registeredSecretValidators.put(GCP_KMS, COMMON_SECRET_MANAGER_VALIDATOR);
    registeredSecretValidators.put(GCP_SECRETS_MANAGER, GCP_SECRET_MANAGER_VALIDATOR);
    registeredSecretValidators.put(VAULT, VAULT_SECRET_MANAGER_VALIDATOR);
    registeredSecretValidators.put(AZURE_VAULT, AZURE_SECRET_MANAGER_VALIDATOR);
    registeredSecretValidators.put(AWS_SECRETS_MANAGER, AWS_SECRET_MANAGAER_VALIDATOR);
    registeredSecretValidators.put(CUSTOM, COMMON_SECRET_MANAGER_VALIDATOR);
  }

  public SecretValidator getSecretValidator(EncryptionType encryptionType) {
    return Optional.ofNullable(registeredSecretValidators.get(encryptionType))
        .flatMap(type -> Optional.of(injector.getInstance(Key.get(SecretValidator.class, Names.named(type.getName())))))
        .<SecretManagementException>orElseThrow(() -> {
          throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
              String.format("No encryptor registered against the encryption type %s", encryptionType), USER);
        });
  }
}
