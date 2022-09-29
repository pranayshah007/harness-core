/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.persistence.HPersistence;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.beans.AzureVaultConfig;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.KmsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.VaultConfig;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.LocalSecretManagerService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@OwnedBy(PL)
public class SecretManagementTestHelper {
  @Inject protected SecretManager secretManager;
  @Inject protected HPersistence persistence;
  @Inject protected EncryptionService encryptionService;
  @Inject protected SettingsService settingsService;
  @Inject private LocalSecretManagerService localSecretManagerService;

  public static AzureVaultConfig getAzureVaultConfig() {
    AzureVaultConfig azureVaultConfig = new AzureVaultConfig();
    azureVaultConfig.setName("myAzureVault");
    azureVaultConfig.setSecretKey(generateUuid());
    azureVaultConfig.setDefault(true);
    azureVaultConfig.setVaultName(generateUuid());
    azureVaultConfig.setClientId(generateUuid());
    azureVaultConfig.setSubscription(generateUuid());
    azureVaultConfig.setTenantId(generateUuid());
    azureVaultConfig.setEncryptionType(EncryptionType.AZURE_VAULT);
    return azureVaultConfig;
  }

  public static AwsSecretsManagerConfig getAwsSecretManagerConfig() {
    AwsSecretsManagerConfig secretsManagerConfig = AwsSecretsManagerConfig.builder()
                                                       .name("myAwsSecretManager")
                                                       .accessKey(generateUuid())
                                                       .secretKey(generateUuid())
                                                       .region("us-east-1")
                                                       .secretNamePrefix(generateUuid())
                                                       .build();
    secretsManagerConfig.setDefault(true);
    secretsManagerConfig.setEncryptionType(EncryptionType.AWS_SECRETS_MANAGER);

    return secretsManagerConfig;
  }

  public static AppDynamicsConfig getAppDynamicsConfig(String accountId, String password) {
    return getAppDynamicsConfig(accountId, password, null);
  }

  public static AppDynamicsConfig getAppDynamicsConfig(String accountId, String password, String encryptedPassword) {
    char[] passwordChars = password == null ? null : password.toCharArray();
    return AppDynamicsConfig.builder()
        .accountId(accountId)
        .controllerUrl(UUID.randomUUID().toString())
        .username(UUID.randomUUID().toString())
        .password(passwordChars)
        .encryptedPassword(encryptedPassword)
        .accountname(UUID.randomUUID().toString())
        .build();
  }

  public static List<SettingAttribute> getSettingAttributes(String accountId, int numOfSettingAttributes) {
    List<SettingAttribute> settingAttributes = new ArrayList<>();
    for (int i = 0; i < numOfSettingAttributes; i++) {
      String password = "password_" + i;
      final AppDynamicsConfig appDynamicsConfig = getAppDynamicsConfig(accountId, password);
      settingAttributes.add(getSettingAttribute(appDynamicsConfig));
    }
    return settingAttributes;
  }

  public static SettingAttribute getSettingAttribute(AppDynamicsConfig appDynamicsConfig) {
    return SettingAttribute.Builder.aSettingAttribute()
        .withAccountId(appDynamicsConfig.getAccountId())
        .withValue(appDynamicsConfig)
        .withAppId(UUID.randomUUID().toString())
        .withCategory(SettingAttribute.SettingCategory.CONNECTOR)
        .withEnvId(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .build();
  }

  public VaultConfig getCommonVaultConfig() {
    VaultConfig vaultConfig =
        VaultConfig.builder().vaultUrl("http://127.0.0.1:8200").name("myVault").secretEngineVersion(1).build();
    vaultConfig.setDefault(true);
    vaultConfig.setReadOnly(false);
    vaultConfig.setEncryptionType(EncryptionType.VAULT);
    vaultConfig.setSecretEngineName("secret");
    vaultConfig.setSecretEngineVersion(2);
    vaultConfig.setUsageRestrictions(
        localSecretManagerService.getEncryptionConfig(generateUuid()).getUsageRestrictions());
    return vaultConfig;
  }

  public VaultConfig getVaultConfigWithAuthToken() {
    return getVaultConfigWithAuthToken(generateUuid());
  }

  public VaultConfig getVaultConfigWithAuthToken(String authToken) {
    VaultConfig vaultConfig = getCommonVaultConfig();
    vaultConfig.setAuthToken(authToken);
    return vaultConfig;
  }

  public VaultConfig getVaultConfigWithAppRole(String appRoleId, String secretId) {
    VaultConfig vaultConfig = getCommonVaultConfig();
    vaultConfig.setAppRoleId(appRoleId);
    vaultConfig.setSecretId(secretId);
    return vaultConfig;
  }

  public KmsConfig getKmsConfig() {
    final KmsConfig kmsConfig = new KmsConfig();
    kmsConfig.setName("myKms");
    kmsConfig.setDefault(true);
    kmsConfig.setKmsArn(generateUuid());
    kmsConfig.setAccessKey(generateUuid());
    kmsConfig.setSecretKey(generateUuid());
    kmsConfig.setEncryptionType(EncryptionType.KMS);
    kmsConfig.setUsageRestrictions(
        localSecretManagerService.getEncryptionConfig(generateUuid()).getUsageRestrictions());
    return kmsConfig;
  }

  public GcpKmsConfig getGcpKmsConfig() {
    GcpKmsConfig gcpKmsConfig = new GcpKmsConfig(
        "gcpKms", "projectId", "region", "keyRing", "keyName", "{\"abc\": \"value\"}".toCharArray(), null);
    gcpKmsConfig.setDefault(true);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setUsageRestrictions(
        localSecretManagerService.getEncryptionConfig(generateUuid()).getUsageRestrictions());
    return gcpKmsConfig;
  }

  public void validateSettingAttributes(List<SettingAttribute> settingAttributes, int expectedNumOfEncryptedRecords) {
    int numOfSettingAttributes = settingAttributes.size();
    assertThat(persistence.createQuery(SettingAttribute.class).count()).isEqualTo(numOfSettingAttributes);
    assertThat(persistence.createQuery(EncryptedData.class).count()).isEqualTo(expectedNumOfEncryptedRecords);

    for (int i = 0; i < numOfSettingAttributes; i++) {
      SettingAttribute settingAttribute = settingAttributes.get(i);
      String id = settingAttribute.getUuid();
      String appId = settingAttribute.getAppId();
      SettingAttribute savedAttribute = persistence.get(SettingAttribute.class, id);
      assertThat(savedAttribute).isEqualTo(settingAttributes.get(i));
      AppDynamicsConfig appDynamicsConfig = (AppDynamicsConfig) settingAttributes.get(i).getValue();
      assertThat(appDynamicsConfig.getPassword()).isNull();

      encryptionService.decrypt(
          appDynamicsConfig, secretManager.getEncryptionDetails(appDynamicsConfig, null, appId), false);
      assertThat(new String(appDynamicsConfig.getPassword())).isEqualTo("password_" + i);

      persistence.delete(SettingAttribute.class, settingAttribute.getUuid());
    }

    assertThat(persistence.createQuery(SettingAttribute.class).count()).isEqualTo(0);
    assertThat(persistence.createQuery(EncryptedData.class).count()).isEqualTo(expectedNumOfEncryptedRecords);
  }
}
