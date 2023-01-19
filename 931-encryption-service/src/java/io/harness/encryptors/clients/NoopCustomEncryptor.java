/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import io.harness.encryptors.CustomEncryptor;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import java.util.Set;

public class NoopCustomEncryptor implements CustomEncryptor {
  @Override
  public boolean validateReference(
      final String accountId, final Set<EncryptedDataParams> params, final EncryptionConfig encryptionConfig) {
    throw new UnsupportedOperationException("Unsupported custom encryptor");
  }

  @Override
  public char[] fetchSecretValue(
      final String accountId, final EncryptedRecord encryptedRecord, final EncryptionConfig encryptionConfig) {
    throw new UnsupportedOperationException("Unsupported custom encryptor");
  }
}
