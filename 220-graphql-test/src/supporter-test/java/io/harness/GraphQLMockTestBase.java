/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.beans.EncryptedData;
import io.harness.encryptors.clients.AwsKmsEncryptor;
import io.harness.exception.SecretManagementException;
import io.harness.rule.GraphQLWithWingsRule;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.AccountType;
import software.wings.beans.KmsConfig;
import software.wings.beans.LicenseInfo;
import software.wings.beans.VaultConfig;
import software.wings.beans.account.AccountStatus;
import software.wings.settings.SettingVariableTypes;

import java.io.IOException;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public abstract class GraphQLMockTestBase extends CategoryTest implements MockableTestMixin {
  private static final String plainTextKey = "1234567890123456";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  // I am not absolutely sure why, but there is dependency between wings io.harness.rule and
  // MockitoJUnit io.harness.rule and they have to be listed in these order
  @Rule public GraphQLWithWingsRule wingsRule = new GraphQLWithWingsRule();

  protected EncryptedData encrypt(String accountId, char[] value, KmsConfig kmsConfig) throws Exception {
    if (kmsConfig.getAccessKey().equals("invalidKey")) {
      throw new SecretManagementException("Invalid credentials");
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

  protected char[] decrypt(EncryptedData data, KmsConfig kmsConfig) throws Exception {
    return AwsKmsEncryptor.decrypt(data.getEncryptedValue(), new SecretKeySpec(plainTextKey.getBytes(), "AES"))
        .toCharArray();
  }

  protected EncryptedData encrypt(String name, String value, String accountId, SettingVariableTypes settingType,
      VaultConfig vaultConfig, EncryptedData savedEncryptedData) throws IOException {
    if (vaultConfig.getAuthToken().equals("invalidKey")) {
      throw new SecretManagementException("invalidKey");
    }
    String keyUrl = settingType + "/" + name;
    if (savedEncryptedData != null) {
      savedEncryptedData.setEncryptionKey(keyUrl);
      savedEncryptedData.setEncryptedValue(value == null ? keyUrl.toCharArray() : value.toCharArray());
      return savedEncryptedData;
    }

    return EncryptedData.builder()
        .encryptionKey(keyUrl)
        .encryptedValue(value == null ? null : value.toCharArray())
        .encryptionType(EncryptionType.VAULT)
        .enabled(true)
        .accountId(accountId)
        .kmsId(vaultConfig.getUuid())
        .build();
  }

  protected char[] decrypt(EncryptedData data, VaultConfig vaultConfig) throws IOException {
    if (data.getEncryptedValue() == null) {
      return null;
    }
    return data.getEncryptedValue();
  }

  protected VaultConfig getVaultConfig() {
    return getVaultConfig(generateUuid());
  }

  protected VaultConfig getVaultConfig(String authToken) {
    VaultConfig vaultConfig = VaultConfig.builder()
                                  .vaultUrl("http://127.0.0.1:8200")
                                  .authToken(authToken)
                                  .name("myVault")
                                  .secretEngineVersion(1)
                                  .build();
    vaultConfig.setDefault(true);
    return vaultConfig;
  }

  protected KmsConfig getKmsConfig() {
    final KmsConfig kmsConfig = new KmsConfig();
    kmsConfig.setName("myKms");
    kmsConfig.setDefault(true);
    kmsConfig.setKmsArn(generateUuid());
    kmsConfig.setAccessKey(generateUuid());
    kmsConfig.setSecretKey(generateUuid());
    return kmsConfig;
  }

  protected LicenseInfo getLicenseInfo() {
    LicenseInfo licenseInfo = new LicenseInfo();
    licenseInfo.setAccountStatus(AccountStatus.ACTIVE);
    licenseInfo.setAccountType(AccountType.PAID);
    licenseInfo.setLicenseUnits(100);
    licenseInfo.setExpireAfterDays(15);
    return licenseInfo;
  }
}
