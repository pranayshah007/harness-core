/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.security.encryption.EncryptionType.AZURE_VAULT;
import static io.harness.security.encryption.EncryptionType.VAULT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.CustomSecretManagerHelper;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.ng.core.dao.NGEncryptedDataDao;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultConfigDTO;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.secrets.SecretsFileService;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import software.wings.beans.AzureVaultConfig;
import software.wings.beans.BaseVaultConfig;
import software.wings.service.impl.security.GlobalEncryptDecryptClient;
import software.wings.settings.SettingVariableTypes;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@OwnedBy(PL)
public class NGEncryptedDataServiceImplTest extends CategoryTest {
  private NGEncryptedDataServiceImpl ngEncryptedDataService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private SecretManagerClient secretManagerClient;
  @Mock private NGEncryptedDataDao encryptedDataDao;
  @Mock private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  @Mock private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  @Mock private SecretsFileService secretsFileService;
  @Mock private GlobalEncryptDecryptClient globalEncryptDecryptClient;
  NGConnectorSecretManagerService ngConnectorSecretManagerService;
  @Mock private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Mock private VaultEncryptor vaultEncryptor;
  @Mock private CustomEncryptorsRegistry customEncryptorsRegistry;
  @Mock private CustomSecretManagerHelper customSecretManagerHelper;
  public static final String HTTP_VAULT_URL = "http://vault.com";

  @Before
  public void setup() {
    initMocks(this);
    ngConnectorSecretManagerService = mock(NGConnectorSecretManagerService.class);
    ngEncryptedDataService =
        spy(new NGEncryptedDataServiceImpl(encryptedDataDao, kmsEncryptorsRegistry, vaultEncryptorsRegistry,
            secretsFileService, secretManagerClient, globalEncryptDecryptClient, ngConnectorSecretManagerService,
            ngFeatureFlagHelperService, customEncryptorsRegistry, customSecretManagerHelper));
    when(vaultEncryptorsRegistry.getVaultEncryptor(any())).thenReturn(vaultEncryptor);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateSecret_withVault_doNotRenewAppRoleToken_FF_disabled() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder()
                                            .secretManagerIdentifier(identifier)
                                            .valueType(ValueType.Inline)
                                            .value("value")
                                            .build())
                                  .build();
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .build();
    when(encryptedDataDao.get(any(), any(), any(), any())).thenReturn(null);
    when(encryptedDataDao.save(any())).thenReturn(encryptedDataDTO);
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(false);
    SecretManagerConfigDTO vaultConfigDTO = VaultConfigDTO.builder()
                                                .accountIdentifier(accountIdentifier)
                                                .appRoleId("appRoleId")
                                                .secretId("secretId")
                                                .renewAppRoleToken(true)
                                                .build();
    vaultConfigDTO.setEncryptionType(VAULT);
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    ArgumentCaptor<SecretManagerConfig> argumentCaptor = ArgumentCaptor.forClass(SecretManagerConfig.class);
    when(vaultEncryptor.createSecret(any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(NGEncryptedData.builder()
                        .name("name")
                        .encryptedValue("encryptedValue".toCharArray())
                        .encryptionKey("encryptionKey")
                        .build());
    NGEncryptedData result = ngEncryptedDataService.createSecretText(accountIdentifier, secretDTOV2);
    assertThat(result).isNotNull();
    verify(ngFeatureFlagHelperService, times(1)).isEnabled(any(), any());
    BaseVaultConfig vaultConfig = (BaseVaultConfig) argumentCaptor.getValue();
    assertThat(vaultConfig.getRenewAppRoleToken()).isEqualTo(true);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateSecret_withVault_doNotRenewAppRoleToken_FF_enabled() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder()
                                            .secretManagerIdentifier(identifier)
                                            .valueType(ValueType.Inline)
                                            .value("value")
                                            .build())
                                  .build();
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .build();
    when(encryptedDataDao.get(any(), any(), any(), any())).thenReturn(null);
    when(encryptedDataDao.save(any())).thenReturn(encryptedDataDTO);
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(true);
    SecretManagerConfigDTO vaultConfigDTO = VaultConfigDTO.builder()
                                                .accountIdentifier(accountIdentifier)
                                                .appRoleId("appRoleId")
                                                .secretId("secretId")
                                                .renewAppRoleToken(true)
                                                .build();
    vaultConfigDTO.setEncryptionType(VAULT);
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    ArgumentCaptor<SecretManagerConfig> argumentCaptor = ArgumentCaptor.forClass(SecretManagerConfig.class);
    when(vaultEncryptor.createSecret(any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(NGEncryptedData.builder()
                        .name("name")
                        .encryptedValue("encryptedValue".toCharArray())
                        .encryptionKey("encryptionKey")
                        .build());
    NGEncryptedData result = ngEncryptedDataService.createSecretText(accountIdentifier, secretDTOV2);
    assertThat(result).isNotNull();
    verify(ngFeatureFlagHelperService, times(1)).isEnabled(any(), any());
    BaseVaultConfig vaultConfig = (BaseVaultConfig) argumentCaptor.getValue();
    assertThat(vaultConfig.getRenewAppRoleToken()).isEqualTo(false);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSecret_appRoleBased_doNotRenewToken_ff_enabled() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String encryptedValue = randomAlphabetic(10);
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .encryptedValue(encryptedValue.toCharArray())
                                           .secretManagerIdentifier(identifier)
                                           .build();
    when(encryptedDataDao.get(any(), any(), any(), any())).thenReturn(encryptedDataDTO);
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(true);
    SecretManagerConfigDTO vaultConfigDTO = VaultConfigDTO.builder()
                                                .accountIdentifier(accountIdentifier)
                                                .appRoleId("appRoleId")
                                                .secretId("secretId")
                                                .renewAppRoleToken(true)
                                                .build();
    vaultConfigDTO.setEncryptionType(VAULT);
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    ArgumentCaptor<SecretManagerConfig> argumentCaptor = ArgumentCaptor.forClass(SecretManagerConfig.class);
    when(vaultEncryptor.deleteSecret(any(), any(), argumentCaptor.capture())).thenReturn(true);
    when(encryptedDataDao.delete(any(), any(), any(), any())).thenReturn(true);
    boolean deleted = ngEncryptedDataService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    verify(ngFeatureFlagHelperService, times(1)).isEnabled(any(), any());
    BaseVaultConfig vaultConfig = (BaseVaultConfig) argumentCaptor.getValue();
    assertThat(vaultConfig.getRenewAppRoleToken()).isEqualTo(false);
    assertThat(deleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSecret_appRoleBased_doNotRenewToken_ff_disabled() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String encryptedValue = randomAlphabetic(10);
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .encryptedValue(encryptedValue.toCharArray())
                                           .secretManagerIdentifier(identifier)
                                           .build();
    when(encryptedDataDao.get(any(), any(), any(), any())).thenReturn(encryptedDataDTO);
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(false);
    SecretManagerConfigDTO vaultConfigDTO = VaultConfigDTO.builder()
                                                .accountIdentifier(accountIdentifier)
                                                .appRoleId("appRoleId")
                                                .secretId("secretId")
                                                .renewAppRoleToken(true)
                                                .build();
    vaultConfigDTO.setEncryptionType(VAULT);
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    ArgumentCaptor<SecretManagerConfig> argumentCaptor = ArgumentCaptor.forClass(SecretManagerConfig.class);
    when(vaultEncryptor.deleteSecret(any(), any(), argumentCaptor.capture())).thenReturn(true);
    when(encryptedDataDao.delete(any(), any(), any(), any())).thenReturn(true);
    boolean deleted = ngEncryptedDataService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    verify(ngFeatureFlagHelperService, times(1)).isEnabled(any(), any());
    BaseVaultConfig vaultConfig = (BaseVaultConfig) argumentCaptor.getValue();
    assertThat(vaultConfig.getRenewAppRoleToken()).isEqualTo(true);
    assertThat(deleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateSecret_azureVault() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .spec(SecretTextSpecDTO.builder()
                                            .secretManagerIdentifier(identifier)
                                            .valueType(ValueType.Inline)
                                            .value("value")
                                            .build())
                                  .build();
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .build();
    when(encryptedDataDao.get(any(), any(), any(), any())).thenReturn(null);
    when(encryptedDataDao.save(any())).thenReturn(encryptedDataDTO);
    AzureKeyVaultConfigDTO vaultConfigDTO = AzureKeyVaultConfigDTO.builder()
                                                .clientId("cliendId")
                                                .secretKey("secretKey")
                                                .tenantId("tenantId")
                                                .subscription("subscription")
                                                .build();
    vaultConfigDTO.setEncryptionType(AZURE_VAULT);
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    ArgumentCaptor<SecretManagerConfig> argumentCaptor = ArgumentCaptor.forClass(SecretManagerConfig.class);
    when(vaultEncryptor.createSecret(any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(NGEncryptedData.builder()
                        .name("name")
                        .encryptedValue("encryptedValue".toCharArray())
                        .encryptionKey("encryptionKey")
                        .build());
    NGEncryptedData result = ngEncryptedDataService.createSecretText(accountIdentifier, secretDTOV2);
    assertThat(result).isNotNull();
    verify(ngFeatureFlagHelperService, times(0)).isEnabled(any(), any());
    SecretManagerConfig secretManagerConfig = argumentCaptor.getValue();
    assertThat(secretManagerConfig instanceof AzureVaultConfig).isEqualTo(true);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSecret_azureVault() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String encryptedValue = randomAlphabetic(10);
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .encryptedValue(encryptedValue.toCharArray())
                                           .secretManagerIdentifier(identifier)
                                           .build();
    when(encryptedDataDao.get(any(), any(), any(), any())).thenReturn(encryptedDataDTO);
    AzureKeyVaultConfigDTO vaultConfigDTO = AzureKeyVaultConfigDTO.builder()
                                                .clientId("cliendId")
                                                .secretKey("secretKey")
                                                .tenantId("tenantId")
                                                .subscription("subscription")
                                                .build();
    vaultConfigDTO.setEncryptionType(AZURE_VAULT);
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    ArgumentCaptor<SecretManagerConfig> argumentCaptor = ArgumentCaptor.forClass(SecretManagerConfig.class);
    when(vaultEncryptor.deleteSecret(any(), any(), argumentCaptor.capture())).thenReturn(true);
    when(encryptedDataDao.delete(any(), any(), any(), any())).thenReturn(true);
    boolean deleted = ngEncryptedDataService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    verify(ngFeatureFlagHelperService, times(0)).isEnabled(any(), any());
    SecretManagerConfig secretManagerConfig = argumentCaptor.getValue();
    assertThat(secretManagerConfig instanceof AzureVaultConfig).isEqualTo(true);
    assertThat(deleted).isEqualTo(true);
  }
}
