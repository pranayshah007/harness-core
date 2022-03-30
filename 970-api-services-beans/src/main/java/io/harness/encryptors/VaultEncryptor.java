/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretText;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
public interface VaultEncryptor {
  EncryptedRecord createSecret(@NotEmpty String accountId, @NotEmpty String name, @NotEmpty String plaintext,
      @NotNull EncryptionConfig encryptionConfig);

  EncryptedRecord updateSecret(@NotEmpty String accountId, @NotEmpty String name, @NotEmpty String plaintext,
      @NotNull EncryptedRecord existingRecord, @NotNull EncryptionConfig encryptionConfig);

  EncryptedRecord renameSecret(@NotEmpty String accountId, @NotEmpty String name,
      @NotNull EncryptedRecord existingRecord, @NotNull EncryptionConfig encryptionConfig);

  default EncryptedRecord createSecret(
      @NotEmpty String accountId, @NotNull SecretText secretText, @NotNull EncryptionConfig encryptionConfig) {
    return createSecret(accountId, secretText.getName(), secretText.getValue(), encryptionConfig);
  }

  default EncryptedRecord updateSecret(@NotEmpty String accountId, @NotNull SecretText secretText,
      @NotNull EncryptedRecord existingRecord, @NotNull EncryptionConfig encryptionConfig) {
    return updateSecret(accountId, secretText.getName(), secretText.getValue(), existingRecord, encryptionConfig);
  }

  default EncryptedRecord renameSecret(@NotEmpty String accountId, @NotNull SecretText secretText,
      @NotNull EncryptedRecord existingRecord, @NotNull EncryptionConfig encryptionConfig) {
    return renameSecret(accountId, secretText.getName(), existingRecord, encryptionConfig);
  }

  default boolean validateReference(
      @NotEmpty String accountId, @NotNull SecretText secretText, @NotNull EncryptionConfig encryptionConfig) {
    return validateReference(accountId, secretText.getPath(), encryptionConfig);
  }

  default boolean validateSecretManagerConfiguration(
      @NotEmpty String accountId, @NotNull EncryptionConfig encryptionConfig) {
    throw new UnsupportedOperationException(
        "Validating SecretManager Configuration on Delegate in not available yet for:" + encryptionConfig);
  }

  boolean deleteSecret(
      @NotEmpty String accountId, @NotNull EncryptedRecord existingRecord, @NotNull EncryptionConfig encryptionConfig);

  boolean validateReference(
      @NotEmpty String accountId, @NotEmpty String path, @NotNull EncryptionConfig encryptionConfig);

  char[] fetchSecretValue(
      @NotEmpty String accountId, @NotNull EncryptedRecord encryptedRecord, @NotNull EncryptionConfig encryptionConfig);
}
