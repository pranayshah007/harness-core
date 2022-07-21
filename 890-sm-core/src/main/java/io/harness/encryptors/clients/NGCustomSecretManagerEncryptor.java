/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.encryptors.CustomEncryptor;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import java.util.Set;

public class NGCustomSecretManagerEncryptor implements CustomEncryptor {
  @Override
  public boolean validateReference(
      String accountId, Set<EncryptedDataParams> params, EncryptionConfig encryptionConfig) {
    return isNotEmpty(
        fetchSecretValue(accountId, EncryptedRecordData.builder().parameters(params).build(), encryptionConfig));
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    return new char[0];
  }
}
