/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.security.encryption.common.EncryptionType.LOCAL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.exception.DelegateRetryableException;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.common.EncryptedRecord;
import io.harness.security.encryption.common.EncryptionConfig;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class GlobalEncryptDecryptClient {
  @Inject private KmsEncryptorsRegistry kmsEncryptorsRegistry;

  public EncryptedRecordData convertEncryptedRecordToLocallyEncrypted(
      EncryptedRecord encryptedRecord, String accountId, EncryptionConfig encryptionConfig) {
    try {
      char[] decryptedValue = kmsEncryptorsRegistry.getKmsEncryptor(encryptionConfig)
                                  .fetchSecretValue(accountId, encryptedRecord, encryptionConfig);
      String randomEncryptionKey = UUIDGenerator.generateUuid();
      char[] reEncryptedValue = new SimpleEncryption(randomEncryptionKey).encryptChars(decryptedValue);

      return EncryptedRecordData.builder()
          .uuid(encryptedRecord.getUuid())
          .name(encryptedRecord.getName())
          .encryptionType(LOCAL)
          .encryptionKey(randomEncryptionKey)
          .encryptedValue(reEncryptedValue)
          .base64Encoded(encryptedRecord.isBase64Encoded())
          .build();
    } catch (DelegateRetryableException | SecretManagementDelegateException e) {
      log.warn("Failed to decrypt secret {} with secret manager {}. Falling back to decrypt this secret using delegate",
          encryptedRecord.getUuid(), encryptionConfig.getUuid(), e);
      // This means we are falling back to use delegate to decrypt.
      return buildEncryptedRecordData(encryptedRecord);
    }
  }

  private EncryptedRecordData buildEncryptedRecordData(EncryptedRecord encryptedRecord) {
    return EncryptedRecordData.builder()
        .uuid(encryptedRecord.getUuid())
        .name(encryptedRecord.getName())
        .path(encryptedRecord.getPath())
        .parameters(encryptedRecord.getParameters())
        .encryptionKey(encryptedRecord.getEncryptionKey())
        .encryptedValue(encryptedRecord.getEncryptedValue())
        .kmsId(encryptedRecord.getKmsId())
        .encryptionType(encryptedRecord.getEncryptionType())
        .base64Encoded(encryptedRecord.isBase64Encoded())
        .build();
  }
}
