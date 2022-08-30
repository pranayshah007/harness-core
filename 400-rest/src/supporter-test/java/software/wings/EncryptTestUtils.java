/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings;

import io.harness.beans.EncryptedData;
import io.harness.encryptors.clients.AwsKmsEncryptor;
import io.harness.eraro.ErrorCode;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.KmsConfig;
import software.wings.beans.VaultConfig;

import java.io.IOException;
import javax.crypto.spec.SecretKeySpec;

public class EncryptTestUtils {
  private static final String plainTextKey = "1234567890123456";

  public static EncryptedData encrypt(String accountId, char[] value, KmsConfig kmsConfig) throws Exception {
    if (kmsConfig.getAccessKey().equals("invalidKey")) {
      throw new SecretManagementException(
          ErrorCode.SECRET_MANAGEMENT_ERROR, "Invalid credentials", WingsException.USER);
    }
    char[] encryptedValue = value == null
        ? null
        : AwsKmsEncryptor.encrypt(new String(value), new SecretKeySpec(plainTextKey.getBytes(), "AES"));

    return EncryptedData.builder()
        .encryptionKey(plainTextKey)
        .encryptedValue(encryptedValue)
        .encryptionType(EncryptionType.KMS)
        .kmsId(kmsConfig.getUuid())
        .enabled(true)
        .accountId(accountId)
        .build();
  }

  public static char[] decrypt(EncryptedRecord data, KmsConfig kmsConfig) throws Exception {
    return AwsKmsEncryptor.decrypt(data.getEncryptedValue(), new SecretKeySpec(plainTextKey.getBytes(), "AES"))
        .toCharArray();
  }

  public static EncryptedData encrypt(String accountId, String name, String value, VaultConfig vaultConfig,
      EncryptedData savedEncryptedData) throws IOException {
    if (vaultConfig.getAuthToken().equals("invalidKey")) {
      throw new SecretManagementException("invalidKey");
    }

    return EncryptedData.builder()
        .encryptionKey(name)
        .encryptedValue(value == null ? null : value.toCharArray())
        .encryptionType(EncryptionType.VAULT)
        .enabled(true)
        .accountId(accountId)
        .kmsId(vaultConfig.getUuid())
        .build();
  }

  public static char[] decrypt(EncryptedRecord data, VaultConfig vaultConfig) throws IOException {
    if (data.getEncryptedValue() == null) {
      return null;
    }
    return data.getEncryptedValue();
  }
}
