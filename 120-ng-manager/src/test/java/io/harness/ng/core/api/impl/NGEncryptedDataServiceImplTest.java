/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.RAGHAV_MURALI;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.security.encryption.EncryptionType.AWS_SECRETS_MANAGER;
import static io.harness.security.encryption.EncryptionType.AZURE_VAULT;
import static io.harness.security.encryption.EncryptionType.GCP_SECRETS_MANAGER;
import static io.harness.security.encryption.EncryptionType.LOCAL;
import static io.harness.security.encryption.EncryptionType.VAULT;

import static software.wings.settings.SettingVariableTypes.CONFIG_FILE;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.fail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.CustomSecretManagerHelper;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefParsedData;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.encryptors.clients.LocalEncryptor;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.core.AdditionalMetadataValidationHelper;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dao.NGEncryptedDataDao;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultConfigDTO;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.secrets.SecretsFileService;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import software.wings.beans.AzureVaultConfig;
import software.wings.beans.BaseVaultConfig;
import software.wings.service.impl.security.GlobalEncryptDecryptClient;
import software.wings.service.impl.security.NGEncryptorService;
import software.wings.settings.SettingVariableTypes;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@OwnedBy(PL)
public class NGEncryptedDataServiceImplTest extends CategoryTest {
  public static final String HASHICORP_VAULT_ENCRYPTION_TYPE_PREFIX = "hashicorpvault://";
  public static final String SECRET_RELATIVE_PATH = "/this/is/some/path#key";
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
  @Mock private NGEncryptorService ngEncryptorService;
  @Mock private LocalEncryptor localEncryptor;
  @Mock private AdditionalMetadataValidationHelper additionalMetadataValidationHelper;
  @Mock private DynamicSecretReferenceHelper dynamicSecretReferenceHelper;
  public static final String HTTP_VAULT_URL = "http://vault.com";
  private String accountIdentifier = randomAlphabetic(10);
  private String orgIdentifier = randomAlphabetic(10);
  private String projectIdentifier = randomAlphabetic(10);
  private String identifier = randomAlphabetic(10);
  private String encryptedValue = randomAlphabetic(10);

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setup() {
    initMocks(this);
    ngConnectorSecretManagerService = mock(NGConnectorSecretManagerService.class);
    ngEncryptedDataService =
        spy(new NGEncryptedDataServiceImpl(encryptedDataDao, kmsEncryptorsRegistry, vaultEncryptorsRegistry,
            secretsFileService, secretManagerClient, globalEncryptDecryptClient, ngConnectorSecretManagerService,
            ngFeatureFlagHelperService, customEncryptorsRegistry, customSecretManagerHelper, ngEncryptorService,
            additionalMetadataValidationHelper, dynamicSecretReferenceHelper));
    when(vaultEncryptorsRegistry.getVaultEncryptor(any())).thenReturn(vaultEncryptor);
    when(kmsEncryptorsRegistry.getKmsEncryptor(any())).thenReturn(localEncryptor);
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
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
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
    when(vaultEncryptor.createSecret(any(), any(), argumentCaptor.capture()))
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
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testCreateSecret_withVault_whereSecretManagerDelegatesAreNotAvailable() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .projectIdentifier(projectIdentifier)
                                  .orgIdentifier(orgIdentifier)
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
                                                .name("secretManager")
                                                .renewAppRoleToken(true)
                                                .build();
    vaultConfigDTO.setEncryptionType(VAULT);
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    ArgumentCaptor<SecretManagerConfig> argumentCaptor = ArgumentCaptor.forClass(SecretManagerConfig.class);
    when(vaultEncryptor.createSecret(any(), any(), argumentCaptor.capture()))
        .thenThrow(new DelegateServiceDriverException("Delegates are down"));
    try {
      ngEncryptedDataService.createSecretText(accountIdentifier, secretDTOV2);
    } catch (WingsException ex) {
      assertThat(ex.getMessage())
          .isEqualTo(String.format(
              "Please make sure that your delegate for Secret Manager with identifier [secretManager] is connected. Refer %s for more information on delegate Installation",
              DocumentLinksConstants.DELEGATE_INSTALLATION_LINK));
    }
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
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
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
    when(vaultEncryptor.createSecret(any(), any(), argumentCaptor.capture()))
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
    boolean deleted =
        ngEncryptedDataService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, false);
    verify(ngFeatureFlagHelperService, times(1)).isEnabled(any(), any());
    BaseVaultConfig vaultConfig = (BaseVaultConfig) argumentCaptor.getValue();
    assertThat(vaultConfig.getRenewAppRoleToken()).isEqualTo(false);
    assertThat(deleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testDeleteSecret_evenWhenDeleteFailsInRemote() {
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
                                                .identifier("testSecret")
                                                .appRoleId("appRoleId")
                                                .secretId("secretId")
                                                .renewAppRoleToken(true)
                                                .build();
    vaultConfigDTO.setEncryptionType(VAULT);
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    ArgumentCaptor<SecretManagerConfig> argumentCaptor = ArgumentCaptor.forClass(SecretManagerConfig.class);
    when(vaultEncryptor.deleteSecret(any(), any(), argumentCaptor.capture())).thenReturn(false);
    when(encryptedDataDao.delete(any(), any(), any(), any())).thenReturn(true);
    boolean deleted =
        ngEncryptedDataService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, false);
    verify(ngFeatureFlagHelperService, times(1)).isEnabled(any(), any());
    BaseVaultConfig vaultConfig = (BaseVaultConfig) argumentCaptor.getValue();
    assertThat(vaultConfig.getRenewAppRoleToken()).isEqualTo(false);
    assertThat(deleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testDeleteSecret_evenWhenDeleteFailsInRemoteWithException() {
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
                                                .identifier("testSecret")
                                                .appRoleId("appRoleId")
                                                .secretId("secretId")
                                                .renewAppRoleToken(true)
                                                .build();
    vaultConfigDTO.setEncryptionType(VAULT);
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(vaultConfigDTO);
    ArgumentCaptor<SecretManagerConfig> argumentCaptor = ArgumentCaptor.forClass(SecretManagerConfig.class);
    when(vaultEncryptor.deleteSecret(any(), any(), argumentCaptor.capture()))
        .thenThrow(new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Delete on remote failed", USER));
    when(encryptedDataDao.delete(any(), any(), any(), any())).thenReturn(true);
    boolean deleted =
        ngEncryptedDataService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, false);
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
    boolean deleted =
        ngEncryptedDataService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, false);
    verify(ngFeatureFlagHelperService, times(1)).isEnabled(any(), any());
    BaseVaultConfig vaultConfig = (BaseVaultConfig) argumentCaptor.getValue();
    assertThat(vaultConfig.getRenewAppRoleToken()).isEqualTo(true);
    assertThat(deleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testHardDelete() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);
    when(encryptedDataDao.hardDelete(any(), any(), any(), argumentCaptor.capture()))
        .thenReturn(NGEncryptedData.builder().build());
    ngEncryptedDataService.hardDelete(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    verify(encryptedDataDao, times(1)).hardDelete(any(), any(), any(), any());
    assertThat(identifier).isEqualTo(argumentCaptor.getValue());
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
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
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
    when(vaultEncryptor.createSecret(any(), any(), argumentCaptor.capture()))
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
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testCreateSecret_withAccountScopedSecretIdentifier_azureVault() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = "account." + randomAlphabetic(10);
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
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
    when(vaultEncryptor.createSecret(any(), any(), argumentCaptor.capture()))
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
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testCreateSecret_withParentSMAzureVault() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = "org." + randomAlphabetic(10);
    String scopedIdentifier = identifier;
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .type(SecretType.SecretText)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .spec(SecretTextSpecDTO.builder()
                                            .secretManagerIdentifier(scopedIdentifier)
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
    when(vaultEncryptor.createSecret(any(), any(), argumentCaptor.capture()))
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
    boolean deleted =
        ngEncryptedDataService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, false);
    verify(ngFeatureFlagHelperService, times(0)).isEnabled(any(), any());
    SecretManagerConfig secretManagerConfig = argumentCaptor.getValue();
    assertThat(secretManagerConfig instanceof AzureVaultConfig).isEqualTo(true);
    assertThat(deleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetFromReferenceExpression() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String secretManagerIdentifier = randomAlphabetic(16);
    String identifier = HASHICORP_VAULT_ENCRYPTION_TYPE_PREFIX + secretManagerIdentifier + SECRET_RELATIVE_PATH;
    when(dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(anyString()))
        .thenReturn(SecretRefParsedData.builder()
                        .encryptionType(VAULT)
                        .relativePath(SECRET_RELATIVE_PATH)
                        .secretManagerIdentifier(secretManagerIdentifier)
                        .build());
    NGEncryptedData ngEncryptedData = ngEncryptedDataService.getFromReferenceExpression(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    assertThat(ngEncryptedData.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(ngEncryptedData.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(ngEncryptedData.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(ngEncryptedData.getIdentifier()).isEqualTo(identifier);
    assertThat(ngEncryptedData.getName()).isEqualTo(identifier);
    assertThat(ngEncryptedData.getType()).isEqualTo(SettingVariableTypes.SECRET_TEXT);
    assertThat(ngEncryptedData.getPath()).isEqualTo(SECRET_RELATIVE_PATH);
    assertThat(ngEncryptedData.getSecretManagerIdentifier()).isEqualTo(secretManagerIdentifier);
    assertThat(ngEncryptedData.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
    assertThat(ngEncryptedData.getAdditionalMetadata()).isNull();
    assertThat(ngEncryptedData.getId()).isNotBlank();
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetFromReferenceExpressionWithAdditionalMetadata() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String secretManagerIdentifier = randomAlphabetic(16);
    String secretName = randomAlphabetic(16);
    String version = "5";
    String identifier = "gcpsecretsmanager://" + secretManagerIdentifier + "/" + secretName + "/" + version;
    AdditionalMetadata additionalMetadata = AdditionalMetadata.builder().value("version", version).build();
    when(dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(anyString()))
        .thenReturn(SecretRefParsedData.builder()
                        .encryptionType(GCP_SECRETS_MANAGER)
                        .relativePath(secretName)
                        .secretManagerIdentifier(secretManagerIdentifier)
                        .additionalMetadata(additionalMetadata)
                        .build());
    NGEncryptedData ngEncryptedData = ngEncryptedDataService.getFromReferenceExpression(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    assertThat(ngEncryptedData.getAccountIdentifier()).isEqualTo(accountIdentifier);
    assertThat(ngEncryptedData.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(ngEncryptedData.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(ngEncryptedData.getIdentifier()).isEqualTo(identifier);
    assertThat(ngEncryptedData.getName()).isEqualTo(identifier);
    assertThat(ngEncryptedData.getType()).isEqualTo(SettingVariableTypes.SECRET_TEXT);
    assertThat(ngEncryptedData.getPath()).isEqualTo(secretName);
    assertThat(ngEncryptedData.getSecretManagerIdentifier()).isEqualTo(secretManagerIdentifier);
    assertThat(ngEncryptedData.getEncryptionType()).isEqualTo(GCP_SECRETS_MANAGER);
    assertThat(ngEncryptedData.getAdditionalMetadata()).isEqualTo(additionalMetadata);
    assertThat(ngEncryptedData.getId()).isNotBlank();
  }

  public Map<String, Boolean> getDataForTestGetEncryptionDetailsForGettingNGEncryptedData() {
    Set<EncryptionType> supportedTypes = EnumSet.of(VAULT, AZURE_VAULT, AWS_SECRETS_MANAGER, GCP_SECRETS_MANAGER);
    return Arrays.stream(EncryptionType.values())
        .collect(Collectors.toMap(EncryptionType::getYamlName, type -> supportedTypes.contains(type)));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetEncryptionDetailsForGettingNGEncryptedData() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    Map<String, Boolean> dataMap = getDataForTestGetEncryptionDetailsForGettingNGEncryptedData();
    dataMap.forEach((encryptionTypeName, isAllowed) -> {
      String secretIdentifier = encryptionTypeName + "://" + randomAlphabetic(10) + "/" + randomAlphabetic(5);
      buildAndCheckEncryptedDataCall(accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier, !isAllowed);
    });
  }

  private void buildAndCheckEncryptedDataCall(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String secretIdentifier, boolean expectedDBCall) {
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();
    SecretVariableDTO secretVariableDTO =
        SecretVariableDTO.builder()
            .name(secretIdentifier)
            .secret(SecretRefData.builder().identifier(secretIdentifier).scope(Scope.PROJECT).build())
            .type(SecretVariableDTO.Type.TEXT)
            .build();
    when(dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(anyString()))
        .thenReturn(SecretRefParsedData.builder().build());
    ngEncryptedDataService.getEncryptionDetails(ngAccess, secretVariableDTO);
    verify(ngEncryptedDataService, times(expectedDBCall ? 1 : 0))
        .get(accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);
    verify(ngEncryptedDataService, times(expectedDBCall ? 0 : 1))
        .getFromReferenceExpression(accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDecryptSecret_Success() {
    String secretManagerIdentifier = randomAlphabetic(10);
    char[] secretValue = randomAlphabetic(10).toCharArray();
    SecretManagerConfigDTO secretManagerConfigDTO =
        LocalConfigDTO.builder().harnessManaged(true).encryptionType(LOCAL).build();
    NGEncryptedData encryptedData = NGEncryptedData.builder().secretManagerIdentifier(secretManagerIdentifier).build();
    EncryptedRecordData encryptedRecordData = EncryptedRecordData.builder().build();
    EncryptionConfig encryptionConfig = SecretManagerConfigMapper.fromDTO(secretManagerConfigDTO);

    when(encryptedDataDao.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier))
        .thenReturn(encryptedData);
    when(ngConnectorSecretManagerService.getUsingIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier, false))
        .thenReturn(secretManagerConfigDTO);
    when(globalEncryptDecryptClient.convertEncryptedRecordToLocallyEncrypted(
             encryptedData, accountIdentifier, encryptionConfig))
        .thenReturn(encryptedRecordData);
    when(localEncryptor.fetchSecretValue(accountIdentifier, encryptedData, encryptionConfig)).thenReturn(secretValue);
    DecryptedSecretValue decryptedSecretValue =
        ngEncryptedDataService.decryptSecret(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    assertEquals(decryptedSecretValue.getDecryptedValue(), String.valueOf(secretValue));
    assertEquals(decryptedSecretValue.getAccountIdentifier(), accountIdentifier);
    assertEquals(decryptedSecretValue.getOrgIdentifier(), orgIdentifier);
    assertEquals(decryptedSecretValue.getProjectIdentifier(), projectIdentifier);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDecryptSecret_secretManagerNotFound() {
    String secretManagerIdentifier = randomAlphabetic(10);
    NGEncryptedData encryptedData = NGEncryptedData.builder().secretManagerIdentifier(secretManagerIdentifier).build();

    when(encryptedDataDao.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier))
        .thenReturn(encryptedData);
    when(ngConnectorSecretManagerService.getUsingIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier, false))
        .thenReturn(null);
    try {
      DecryptedSecretValue decryptedSecretValue =
          ngEncryptedDataService.decryptSecret(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      fail("InvalidRequestException should be thrown as Secret Manager is not found");
    } catch (SecretManagementException ex) {
      assertEquals(ex.getMessage(),
          String.format("No such secret manager found with identifier %s in org: %s and project: %s",
              secretManagerIdentifier, orgIdentifier, projectIdentifier));
    } catch (Exception ex) {
      fail("Unexpected exception occured");
    }
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDecryptSecret_secretManagerNotHarnessManaged() {
    String secretManagerIdentifier = randomAlphabetic(10);
    char[] secretValue = randomAlphabetic(10).toCharArray();
    SecretManagerConfigDTO secretManager = VaultConfigDTO.builder()
                                               .harnessManaged(false)
                                               .encryptionType(VAULT)
                                               .secretId(randomAlphabetic(10))
                                               .accountIdentifier(accountIdentifier)
                                               .authToken(randomAlphabetic(10))
                                               .build();
    NGEncryptedData encryptedData = NGEncryptedData.builder().secretManagerIdentifier(secretManagerIdentifier).build();
    EncryptedRecordData encryptedRecordData = EncryptedRecordData.builder().build();
    EncryptionConfig encryptionConfig = SecretManagerConfigMapper.fromDTO(secretManager);

    when(encryptedDataDao.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier))
        .thenReturn(encryptedData);
    when(ngConnectorSecretManagerService.getUsingIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier, false))
        .thenReturn(secretManager);
    try {
      DecryptedSecretValue decryptedSecretValue =
          ngEncryptedDataService.decryptSecret(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
      fail("InvalidRequestException should be thrown as Secret Manager is not Harness Managed");
    } catch (InvalidRequestException ex) {
      assertEquals(
          ex.getMessage(), "Decryption is supported only for secrets encrypted via harness managed secret managers");
    } catch (Exception ex) {
      fail("Unexpected exception occured");
    }
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDecryptSecretFile_Success() {
    String secretManagerIdentifier = randomAlphabetic(10);
    char[] secretValue = randomAlphabetic(10).toCharArray();
    SecretManagerConfigDTO secretManagerConfigDTO =
        LocalConfigDTO.builder().harnessManaged(true).encryptionType(LOCAL).build();
    NGEncryptedData encryptedData = NGEncryptedData.builder()
                                        .type(CONFIG_FILE)
                                        .encryptionType(LOCAL)
                                        .secretManagerIdentifier(secretManagerIdentifier)
                                        .encryptedValue(secretValue)
                                        .build();
    EncryptedRecordData encryptedRecordData = EncryptedRecordData.builder().build();
    EncryptionConfig encryptionConfig = SecretManagerConfigMapper.fromDTO(secretManagerConfigDTO);

    when(encryptedDataDao.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier))
        .thenReturn(encryptedData);
    when(ngConnectorSecretManagerService.getUsingIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier, false))
        .thenReturn(secretManagerConfigDTO);
    when(globalEncryptDecryptClient.convertEncryptedRecordToLocallyEncrypted(
             encryptedData, accountIdentifier, encryptionConfig))
        .thenReturn(encryptedRecordData);
    when(localEncryptor.fetchSecretValue(accountIdentifier, encryptedData, encryptionConfig)).thenReturn(secretValue);
    DecryptedSecretValue decryptedSecretValue =
        ngEncryptedDataService.decryptSecret(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    assertEquals(decryptedSecretValue.getDecryptedValue(), String.valueOf(secretValue));
    assertEquals(decryptedSecretValue.getAccountIdentifier(), accountIdentifier);
    assertEquals(decryptedSecretValue.getOrgIdentifier(), orgIdentifier);
    assertEquals(decryptedSecretValue.getProjectIdentifier(), projectIdentifier);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDeleteSecret_whenSMIsDeleted_forceDeleteFalse() {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .encryptedValue(encryptedValue.toCharArray())
                                           .secretManagerIdentifier(identifier)
                                           .build();
    when(encryptedDataDao.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier))
        .thenReturn(encryptedDataDTO);
    when(ngFeatureFlagHelperService.isEnabled(any(), any())).thenReturn(true);
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean())).thenReturn(null);
    when(encryptedDataDao.delete(any(), any(), any(), any())).thenReturn(true);
    boolean deleted =
        ngEncryptedDataService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, false);
    assertThat(deleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testDeleteSecret_whenSMIsDeleted_forceDeleteTrue() {
    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .encryptedValue(encryptedValue.toCharArray())
                                           .secretManagerIdentifier(identifier)
                                           .build();
    when(encryptedDataDao.get(any(), any(), any(), any())).thenReturn(encryptedDataDTO);
    when(ngConnectorSecretManagerService.getUsingIdentifier(any(), any(), any(), any(), anyBoolean())).thenReturn(null);
    when(encryptedDataDao.delete(any(), any(), any(), any())).thenReturn(true);
    boolean deleted =
        ngEncryptedDataService.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier, true);
    assertThat(deleted).isEqualTo(true);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testValidateSecretPath_Success() {
    String secretManagerIdentifier = randomAlphabetic(10);
    String secretRefPath = randomAlphabetic(10);
    SecretManagerConfigDTO secretManager = VaultConfigDTO.builder()
                                               .harnessManaged(false)
                                               .encryptionType(VAULT)
                                               .secretId(randomAlphabetic(10))
                                               .accountIdentifier(accountIdentifier)
                                               .authToken(randomAlphabetic(10))
                                               .build();
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .name(randomAlphabetic(10))
                                  .spec(SecretTextSpecDTO.builder()
                                            .secretManagerIdentifier(secretManagerIdentifier)
                                            .valueType(ValueType.Reference)
                                            .value(secretRefPath)
                                            .build())
                                  .build();
    when(ngConnectorSecretManagerService.getUsingIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier, false))
        .thenReturn(secretManager);
    when(vaultEncryptor.validateReference(anyString(), (SecretText) any(), any())).thenReturn(true);
    boolean result =
        ngEncryptedDataService.validateSecretRef(accountIdentifier, orgIdentifier, projectIdentifier, secretDTOV2);
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testValidateSecretPathGCPSMSuccess() {
    String secretManagerIdentifier = randomAlphabetic(10);
    String secretRefPath = randomAlphabetic(10);
    SecretManagerConfigDTO secretManager = VaultConfigDTO.builder()
                                               .harnessManaged(false)
                                               .encryptionType(VAULT)
                                               .secretId(randomAlphabetic(10))
                                               .accountIdentifier(accountIdentifier)
                                               .authToken(randomAlphabetic(10))
                                               .build();
    SecretDTOV2 secretDTOV2 =
        SecretDTOV2.builder()
            .name(randomAlphabetic(10))
            .spec(SecretTextSpecDTO.builder()
                      .secretManagerIdentifier(secretManagerIdentifier)
                      .value(secretRefPath)
                      .valueType(ValueType.Reference)
                      .additionalMetadata(AdditionalMetadata.builder().value("version", 1).build())
                      .build())
            .build();
    when(ngConnectorSecretManagerService.getUsingIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier, false))
        .thenReturn(secretManager);
    when(vaultEncryptor.validateReference(anyString(), (SecretText) any(), any())).thenReturn(true);
    boolean result =
        ngEncryptedDataService.validateSecretRef(accountIdentifier, orgIdentifier, projectIdentifier, secretDTOV2);
    assertThat(result).isEqualTo(true);
  }

  @Test(expected = SecretManagementException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testValidateSecretPath_Failure() {
    String secretManagerIdentifier = randomAlphabetic(10);
    String secretRefPath = randomAlphabetic(10);
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .name(randomAlphabetic(10))
                                  .spec(SecretTextSpecDTO.builder()
                                            .secretManagerIdentifier(secretManagerIdentifier)
                                            .valueType(ValueType.Reference)
                                            .value(secretRefPath)
                                            .build())
                                  .build();
    SecretManagerConfigDTO secretManager = VaultConfigDTO.builder()
                                               .harnessManaged(false)
                                               .encryptionType(VAULT)
                                               .secretId(randomAlphabetic(10))
                                               .accountIdentifier(accountIdentifier)
                                               .authToken(randomAlphabetic(10))
                                               .build();
    when(ngConnectorSecretManagerService.getUsingIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier, false))
        .thenReturn(secretManager);
    when(vaultEncryptor.validateReference(anyString(), (SecretText) any(), any()))
        .thenThrow(new SecretManagementException("not able to resolve path"));
    boolean result =
        ngEncryptedDataService.validateSecretRef(accountIdentifier, orgIdentifier, projectIdentifier, secretDTOV2);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetEncryptionDetails_secretsWithEncryptionTypeLocal() {
    DecryptableEntity decryptableEntity =
        VaultConnectorDTO.builder()
            .basePath("")
            .vaultUrl("https://vaultqa.harness.io")
            .secretEngineName("secret")
            .authToken(SecretRefData.builder().identifier(identifier).scope(Scope.ACCOUNT).build())
            .build();
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();

    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .encryptionType(LOCAL)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .encryptedValue(encryptedValue.toCharArray())
                                           .secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                           .build();
    when(encryptedDataDao.get(accountIdentifier, null, null, identifier)).thenReturn(encryptedDataDTO);
    ngEncryptedDataService.getEncryptionDetails(ngAccess, decryptableEntity);
    verify(ngConnectorSecretManagerService, times(1)).getLocalConfigDTO(accountIdentifier);
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testGetEncryptionDetails_secretsWithEncryptionTypeOtherThanLocal() {
    DecryptableEntity decryptableEntity =
        VaultConnectorDTO.builder()
            .basePath("")
            .vaultUrl("https://vaultqa.harness.io")
            .secretEngineName("secret")
            .authToken(SecretRefData.builder().identifier(identifier).scope(Scope.ACCOUNT).build())
            .build();
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();

    NGEncryptedData encryptedDataDTO = NGEncryptedData.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .encryptionType(EncryptionType.GCP_KMS)
                                           .type(SettingVariableTypes.SECRET_TEXT)
                                           .encryptedValue(encryptedValue.toCharArray())
                                           .secretManagerIdentifier(HARNESS_SECRET_MANAGER_IDENTIFIER)
                                           .build();
    when(encryptedDataDao.get(accountIdentifier, null, null, identifier)).thenReturn(encryptedDataDTO);
    ngEncryptedDataService.getEncryptionDetails(ngAccess, decryptableEntity);
    verify(ngConnectorSecretManagerService, times(0)).getLocalConfigDTO(accountIdentifier);
    verify(ngConnectorSecretManagerService, times(1))
        .getUsingIdentifier(accountIdentifier, null, null, encryptedDataDTO.getSecretManagerIdentifier(), false);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateSecretFile_filesMigrationMethod() {
    String secretManagerIdentifier = randomAlphabetic(10);
    String encryptionKey = randomAlphabetic(10);
    String encryptedValue = randomAlphabetic(10);
    SecretManagerConfigDTO secretManagerConfigDTO =
        LocalConfigDTO.builder().harnessManaged(true).encryptionType(LOCAL).build();
    SecretDTOV2 secretDTOV2 = SecretDTOV2.builder()
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .identifier(identifier)
                                  .spec(SecretFileSpecDTO.builder().build())
                                  .type(SecretType.SecretFile)
                                  .build();
    when(encryptedDataDao.get(any(), any(), any(), any())).thenReturn(null);
    when(ngConnectorSecretManagerService.getUsingIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, secretManagerIdentifier, false))
        .thenReturn(secretManagerConfigDTO);
    ArgumentCaptor<NGEncryptedData> argumentCaptor = ArgumentCaptor.forClass(NGEncryptedData.class);
    when(encryptedDataDao.save(argumentCaptor.capture())).thenReturn(null);
    ngEncryptedDataService.createSecretFile(accountIdentifier, secretDTOV2, encryptionKey, encryptedValue);
    NGEncryptedData createdData = argumentCaptor.getValue();
    verify(encryptedDataDao, times(1)).get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    assertNotNull(createdData);
    assertThat(createdData.isBase64Encoded()).isEqualTo(true);
    assertThat(createdData.getEncryptionKey()).isEqualTo(encryptionKey);
    assertThat(createdData.getEncryptedValue()).isEqualTo(encryptedValue.toCharArray());
  }
}
