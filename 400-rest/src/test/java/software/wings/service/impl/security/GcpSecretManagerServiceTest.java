/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.alerts.AlertStatus.Pending;
import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.exception.SecretManagementException;
import io.harness.expression.SecretString;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptionType;

import software.wings.WingsBaseTest;
import software.wings.alerts.AlertStatus;
import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.User;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.KmsSetupAlert;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.HarnessUserGroupService;
import software.wings.service.intfc.security.GcpSecretsManagerService;
import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(PL)
@Slf4j
public class GcpSecretManagerServiceTest extends WingsBaseTest {
  @Mock HarnessUserGroupService harnessUserGroupService;
  @Mock AccountService accountService;
  @Mock KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Mock KmsEncryptor kmsEncryptor;
  @Inject @InjectMocks AlertService alertService;
  @Inject @InjectMocks GcpSecretsManagerService gcpSecretsManagerService;
  @Inject private HPersistence persistence;
  @Mock private MainConfiguration configuration;

  private Account account;

  public void setup() {
    account = getAccount(AccountType.PAID);
    account.setLocalEncryptionEnabled(false);
    persistence.save(account);
    List<Account> accounts = new ArrayList<>();
    accounts.add(account);
    User user = User.Builder.anUser()
                    .name("Hello")
                    .uuid(UUIDGenerator.generateUuid())
                    .email("hello@harness.io")
                    .accounts(accounts)
                    .build();
    UserThreadLocal.set(user);
    when(harnessUserGroupService.isHarnessSupportUser(user.getUuid())).thenReturn(true);
    when(kmsEncryptorsRegistry.getKmsEncryptor(any())).thenReturn(kmsEncryptor);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSaveGcpKmsConfig() {
    setup();
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);
    String result = gcpSecretsManagerService.saveGcpKmsConfig(account.getUuid(), gcpKmsConfig, true);
    assertThat(result).isNotNull();

    verify(accountService, times(1)).get(account.getUuid());
    verify(kmsEncryptor, times(1)).encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig));
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSaveGcpKmsConfig_globalKms() {
    setup();
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(GLOBAL_ACCOUNT_ID);
    gcpKmsConfig.setDefault(true);

    when(accountService.get(any())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);
    when(configuration.isUseGlobalKMSAsBaseAlgo()).thenReturn(false);
    String result = gcpSecretsManagerService.saveGcpKmsConfig(GLOBAL_ACCOUNT_ID, gcpKmsConfig, false);
    assertThat(result).isNotNull();
    // the following means we are calling encryptLocal instead useBaseAlgo when encrypting the kms creds.
    verify(configuration, times(0)).isUseGlobalKMSAsBaseAlgo();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSaveGcpKmsConfig_nonGlobalKms() {
    setup();
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);
    when(configuration.isUseGlobalKMSAsBaseAlgo()).thenReturn(false);
    String result = gcpSecretsManagerService.saveGcpKmsConfig(account.getUuid(), gcpKmsConfig, false);
    assertThat(result).isNotNull();
    // the following means we are calling useBaseAlgo when encrypting the kms creds.
    verify(configuration, times(1)).isUseGlobalKMSAsBaseAlgo();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSaveGcpKmsConfig_ShouldFail1() {
    setup();
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name", "projectId@!#)(*@!", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String result = null;
    try {
      result = gcpSecretsManagerService.saveGcpKmsConfig(account.getUuid(), gcpKmsConfig, true);
    } catch (SecretManagementException e) {
      assertThat(e).isNotNull();
    }
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSaveGcpKmsConfig_ShouldFail2() {
    setup();
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name)(*&@!#^)", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String result = null;
    try {
      result = gcpSecretsManagerService.saveGcpKmsConfig(account.getUuid(), gcpKmsConfig, true);
    } catch (SecretManagementException e) {
      assertThat(e).isNotNull();
    }
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSaveGcpKmsConfig_ShouldFail3() {
    setup();
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name", "projectId", "region!@#*(&$!@", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String result = null;
    try {
      result = gcpSecretsManagerService.saveGcpKmsConfig(account.getUuid(), gcpKmsConfig, true);
    } catch (SecretManagementException e) {
      assertThat(e).isNotNull();
    }
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSaveGcpKmsConfig_ShouldFail4() {
    setup();
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name", "projectId", "region", "keyRingOP@#!I#!@UO", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String result = null;
    try {
      result = gcpSecretsManagerService.saveGcpKmsConfig(account.getUuid(), gcpKmsConfig, true);
    } catch (SecretManagementException e) {
      assertThat(e).isNotNull();
    }
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSaveGcpKmsConfig_ShouldFail5() {
    setup();
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name", "projectId", "region", "keyRing", "keyName)_@!#(*", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String result = null;
    try {
      result = gcpSecretsManagerService.saveGcpKmsConfig(account.getUuid(), gcpKmsConfig, true);
    } catch (SecretManagementException e) {
      assertThat(e).isNotNull();
    }
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSaveGcpKmsConfig_ShouldFail6() {
    setup();
    char[] credentials = "{\"credentials\":".toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String result = null;
    try {
      result = gcpSecretsManagerService.saveGcpKmsConfig(account.getUuid(), gcpKmsConfig, true);
    } catch (SecretManagementException e) {
      assertThat(e).isNotNull();
      log.error(e.getMessage(), e);
    }
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSaveGcpKmsConfig_ShouldFail7() {
    setup();
    char[] credentials = "".toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String result = null;
    try {
      result = gcpSecretsManagerService.saveGcpKmsConfig(account.getUuid(), gcpKmsConfig, true);
    } catch (SecretManagementException e) {
      assertThat(e).isNotNull();
      log.error(e.getMessage(), e);
    }
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testSaveGcpKmsConfig_ShouldFail8() {
    setup();
    char[] credentials = "".toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String result = null;
    try {
      result = gcpSecretsManagerService.saveGcpKmsConfig(UUIDGenerator.generateUuid(), gcpKmsConfig, true);
    } catch (SecretManagementException e) {
      assertThat(e).isNotNull();
      log.error(e.getMessage(), e);
    }
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateGcpKmsConfig() {
    setup();
    String credentialsString = "{\"credentials\":\"abc\"}";
    char[] credentials = credentialsString.toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name1", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String configId = gcpSecretsManagerService.saveGcpKmsConfig(account.getUuid(), gcpKmsConfig, true);
    assertThat(configId).isNotNull();

    GcpKmsConfig savedGcpKmsConfig = gcpSecretsManagerService.getGcpKmsConfig(account.getUuid(), configId);
    assertThat(savedGcpKmsConfig.getName()).isEqualTo(gcpKmsConfig.getName());
    assertThat(String.valueOf(savedGcpKmsConfig.getCredentials())).isEqualTo(String.valueOf(credentials));
    assertThat(savedGcpKmsConfig.getProjectId()).isEqualTo(gcpKmsConfig.getProjectId());

    GcpKmsConfig updateGcpKmsConfig = new GcpKmsConfig(
        "name2", "projectId1", "region1", "keyRing1", "keyName1", SecretString.SECRET_MASK.toCharArray(), null);
    updateGcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    updateGcpKmsConfig.setAccountId(account.getUuid());
    updateGcpKmsConfig.setDefault(false);
    updateGcpKmsConfig.setUuid(savedGcpKmsConfig.getUuid());

    String updatedConfigId = gcpSecretsManagerService.updateGcpKmsConfig(account.getUuid(), updateGcpKmsConfig);
    assertThat(updatedConfigId).isEqualTo(configId);

    GcpKmsConfig updatedGcpKmsConfig = gcpSecretsManagerService.getGcpKmsConfig(account.getUuid(), updatedConfigId);
    assertThat(updatedGcpKmsConfig.getName()).isEqualTo(updateGcpKmsConfig.getName());
    assertThat(updatedGcpKmsConfig.isDefault()).isEqualTo(updateGcpKmsConfig.isDefault());
    assertThat(updatedGcpKmsConfig.getProjectId()).isEqualTo(gcpKmsConfig.getProjectId());
    assertThat(String.valueOf(updatedGcpKmsConfig.getCredentials())).isEqualTo(credentialsString);

    String newCredentialsString = "{\"credentials\":\"abcd\"}";
    updateGcpKmsConfig.setCredentials(newCredentialsString.toCharArray());
    updateGcpKmsConfig.setDefault(true);

    updatedConfigId = gcpSecretsManagerService.updateGcpKmsConfig(account.getUuid(), updateGcpKmsConfig);
    assertThat(updatedConfigId).isEqualTo(configId);

    updatedGcpKmsConfig = gcpSecretsManagerService.getGcpKmsConfig(account.getUuid(), updatedConfigId);
    assertThat(updatedGcpKmsConfig.getName()).isEqualTo(updateGcpKmsConfig.getName());
    assertThat(updatedGcpKmsConfig.isDefault()).isEqualTo(updateGcpKmsConfig.isDefault());
    assertThat(updatedGcpKmsConfig.getProjectId()).isEqualTo(gcpKmsConfig.getProjectId());
    assertThat(String.valueOf(updatedGcpKmsConfig.getCredentials())).isEqualTo(newCredentialsString);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateGcpKmsConfig_ShouldFail1() {
    setup();
    String credentialsString = "{\"credentials\":\"abc\"}";
    char[] credentials = credentialsString.toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name1", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String configId = null;
    try {
      configId = gcpSecretsManagerService.updateGcpKmsConfig(account.getUuid(), gcpKmsConfig);
    } catch (SecretManagementException e) {
      assertThat(e).isNotNull();
    }
    assertThat(configId).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateGcpKmsConfig_ShouldFail2() {
    setup();
    String credentialsString = "{\"credentials\":\"abc\"}";
    char[] credentials = credentialsString.toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name1", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);
    gcpKmsConfig.setUuid(UUIDGenerator.generateUuid());

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String configId = null;
    try {
      configId = gcpSecretsManagerService.updateGcpKmsConfig(account.getUuid(), gcpKmsConfig);
    } catch (SecretManagementException e) {
      assertThat(e).isNotNull();
    }
    assertThat(configId).isNull();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testDeleteGcpKmsConfig() {
    setup();
    String credentialsString = "{\"credentials\":\"abc\"}";
    char[] credentials = credentialsString.toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name1", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(account.getUuid())).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String configId = gcpSecretsManagerService.saveGcpKmsConfig(account.getUuid(), gcpKmsConfig, true);
    assertThat(configId).isNotNull();

    EncryptedData encryptedData = EncryptedData.builder()
                                      .accountId(account.getUuid())
                                      .enabled(true)
                                      .kmsId(configId)
                                      .encryptionType(EncryptionType.GCP_KMS)
                                      .encryptionKey("Dummy Key")
                                      .encryptedValue("Dummy Value".toCharArray())
                                      .base64Encoded(false)
                                      .name("Dummy record")
                                      .type(SettingVariableTypes.GCP_KMS)
                                      .build();
    String encryptedRecordId = persistence.save(encryptedData);

    boolean result = false;
    String alertId = UUIDGenerator.generateUuid();
    try {
      KmsSetupAlert alertData = KmsSetupAlert.builder().kmsId(gcpKmsConfig.getUuid()).build();
      Alert alert = Alert.builder()
                        .uuid(alertId)
                        .appId(GLOBAL_APP_ID)
                        .accountId(account.getUuid())
                        .type(AlertType.InvalidKMS)
                        .status(Pending)
                        .alertData(alertData)
                        .build();
      persistence.save(alert);
      result = gcpSecretsManagerService.deleteGcpKmsConfig(account.getUuid(), configId);
    } catch (SecretManagementException e) {
      assertThat(e).isNotNull();
    } finally {
      assertThat(result).isFalse();
    }

    persistence.delete(EncryptedData.class, encryptedRecordId);
    result = gcpSecretsManagerService.deleteGcpKmsConfig(account.getUuid(), configId);
    assertThat(result).isTrue();
    Alert alert = persistence.get(Alert.class, alertId);
    assertThat(alert.getStatus()).isEqualTo(AlertStatus.Closed);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetGlobalKmsConfig() {
    setup();
    String credentialsString = "{\"credentials\":\"abc\"}";
    char[] credentials = credentialsString.toCharArray();

    GcpKmsConfig gcpKmsConfig =
        new GcpKmsConfig("name1", "projectId", "region", "keyRing", "keyName", credentials, null);
    gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
    gcpKmsConfig.setAccountId(account.getUuid());
    gcpKmsConfig.setDefault(true);

    when(accountService.get(GLOBAL_ACCOUNT_ID)).thenReturn(account);
    when(kmsEncryptor.encryptSecret(eq(account.getUuid()), any(), eq(gcpKmsConfig))).thenReturn(null);

    String result = gcpSecretsManagerService.saveGcpKmsConfig(GLOBAL_ACCOUNT_ID, gcpKmsConfig, true);
    assertThat(result).isNotNull();
    gcpKmsConfig.setUuid(result);

    GcpKmsConfig returnedGcpKmsConfig = gcpSecretsManagerService.getGlobalKmsConfig();
    assertThat(returnedGcpKmsConfig).isNotNull();
    assertThat(returnedGcpKmsConfig.getCredentials()).isEqualTo(credentials);
  }
}
