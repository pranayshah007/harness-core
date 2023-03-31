/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessModule._890_SM_CORE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.PHOENIKX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.NGSecretManagerMetadata;
import io.harness.secretmanagerclient.dto.GcpKmsConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataDTO;
import io.harness.secretmanagerclient.dto.SecretManagerMetadataRequestDTO;
import io.harness.secretmanagerclient.dto.VaultAppRoleCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultAuthTokenCredentialDTO;
import io.harness.secretmanagerclient.dto.VaultConfigUpdateDTO;
import io.harness.secretmanagerclient.dto.VaultMetadataRequestSpecDTO;
import io.harness.secretmanagerclient.dto.VaultMetadataSpecDTO;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.AccessType;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.VaultConfig;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.security.AzureSecretsManagerService;
import software.wings.service.intfc.security.GcpSecretsManagerService;
import software.wings.service.intfc.security.KmsService;
import software.wings.service.intfc.security.LocalSecretManagerService;
import software.wings.service.intfc.security.VaultService;

import java.util.ArrayList;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(PL)
@TargetModule(_890_SM_CORE)
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class NGSecretManagerServiceImplTest extends CategoryTest {
  @Mock private VaultService vaultService;
  @Mock private AzureSecretsManagerService azureSecretsManagerService;
  @Mock private LocalSecretManagerService localSecretManagerService;
  @Mock private GcpSecretsManagerService gcpSecretsManagerService;
  @Mock private KmsService kmsService;
  @Mock private SecretManagerConfigService secretManagerConfigService;
  @Mock private WingsPersistence wingsPersistence;
  private NGSecretManagerServiceImpl ngSecretManagerService;
  private NGSecretManagerServiceImpl ngSecretManagerServiceTest;

  @Before
  public void doSetup() {
    MockitoAnnotations.initMocks(this);
    ngSecretManagerServiceTest = new NGSecretManagerServiceImpl(vaultService, azureSecretsManagerService,
        localSecretManagerService, gcpSecretsManagerService, kmsService, secretManagerConfigService, wingsPersistence);
    ngSecretManagerService = spy(ngSecretManagerServiceTest);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreate() {
    doReturn(false).when(ngSecretManagerService).checkForDuplicate(any(), any(), any(), any());
    when(vaultService.saveOrUpdateVaultConfig(any(), any(), anyBoolean())).thenReturn("abcde");

    SecretManagerConfig secretManagerConfig = ngSecretManagerService.create(
        VaultConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().build()).authToken("abd").build());
    assertThat(secretManagerConfig).isNotNull();
    assertThat(secretManagerConfig.getEncryptionType()).isEqualTo(EncryptionType.VAULT);

    when(gcpSecretsManagerService.saveGcpKmsConfig(any(), any(), anyBoolean())).thenReturn("abcde");
    secretManagerConfig = ngSecretManagerService.create(
        GcpKmsConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().build()).build());
    assertThat(secretManagerConfig).isNotNull();
    assertThat(secretManagerConfig.getEncryptionType()).isEqualTo(EncryptionType.GCP_KMS);

    when(localSecretManagerService.saveLocalEncryptionConfig(any(), any())).thenReturn("abcde");
    secretManagerConfig = ngSecretManagerService.create(
        LocalEncryptionConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().build()).build());
    assertThat(secretManagerConfig).isNotNull();
    assertThat(secretManagerConfig.getEncryptionType()).isEqualTo(EncryptionType.LOCAL);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreate_shouldFailDueToInvalidEncryptionType() {
    doReturn(false).when(ngSecretManagerService).checkForDuplicate(any(), any(), any(), any());

    try {
      ngSecretManagerService.create(
          AwsSecretsManagerConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().build()).build());
      fail("Execution should not reach here");
    } catch (UnsupportedOperationException e) {
      // ignore
    }
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testCreate_shouldFailDueToSecretManagerAlreadyExists() {
    doReturn(true).when(ngSecretManagerService).checkForDuplicate(any(), any(), any(), any());
    try {
      ngSecretManagerService.create(
          VaultConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().build()).build());
    } catch (DuplicateFieldException duplicateFieldException) {
      // ignore
    }
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testTestConnection() {
    doReturn(Optional.ofNullable(VaultConfig.builder().build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    ConnectorValidationResult connectorValidationResult =
        ngSecretManagerService.testConnection("account", null, null, "identifier");
    assertThat(connectorValidationResult).isNotNull();
    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    verify(vaultService).validateVaultConfig(any(), any());

    doReturn(Optional.ofNullable(GcpKmsConfig.builder().build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    connectorValidationResult = ngSecretManagerService.testConnection("account", null, null, "identifier");
    assertThat(connectorValidationResult).isNotNull();
    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    verify(gcpSecretsManagerService).validateSecretsManagerConfig(any(), any());

    doReturn(Optional.ofNullable(LocalEncryptionConfig.builder().build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    connectorValidationResult = ngSecretManagerService.testConnection("account", null, null, "identifier");
    assertThat(connectorValidationResult).isNotNull();
    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
    verify(localSecretManagerService).validateLocalEncryptionConfig(any(), any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testTestConnection_shouldFailDueToSecretManagerNotPresent() {
    doReturn(Optional.empty()).when(ngSecretManagerService).get(any(), any(), any(), any(), eq(true));
    ConnectorValidationResult connectorValidationResult =
        ngSecretManagerService.testConnection("account", null, null, "identifier");
    assertThat(connectorValidationResult.getStatus()).isEqualTo(ConnectivityStatus.FAILURE);
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGetGlobalSecretManager_GCP() {
    when(secretManagerConfigService.getGlobalSecretManager(any())).thenReturn(GcpKmsConfig.builder().build());
    SecretManagerConfig secretManagerConfig = ngSecretManagerService.getGlobalSecretManager("account");
    assertThat(secretManagerConfig).isNotNull();
    assertThat(secretManagerConfig.getEncryptionType()).isEqualTo(EncryptionType.GCP_KMS);
    verify(secretManagerConfigService).getGlobalSecretManager(any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGlobalSecretManager_Local() {
    when(secretManagerConfigService.getGlobalSecretManager(any())).thenReturn(null);
    when(localSecretManagerService.getEncryptionConfig(any())).thenReturn(LocalEncryptionConfig.builder().build());
    SecretManagerConfig secretManagerConfig = ngSecretManagerService.getGlobalSecretManager("account");
    assertThat(secretManagerConfig).isNotNull();
    assertThat(secretManagerConfig.getEncryptionType()).isEqualTo(EncryptionType.LOCAL);
    verify(secretManagerConfigService).getGlobalSecretManager(any());
    verify(localSecretManagerService).getEncryptionConfig(any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdate_Vault() {
    doReturn(
        Optional.ofNullable(
            VaultConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().harnessManaged(false).build()).build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    when(vaultService.saveOrUpdateVaultConfig(any(), any(), anyBoolean())).thenReturn("abcde");
    doReturn(0L).when(ngSecretManagerService).getCountOfSecretsCreatedUsingSecretManager(any(), any(), any(), any());

    SecretManagerConfig secretManagerConfig = ngSecretManagerService.update("account", null, null, "identifier",
        VaultConfigUpdateDTO.builder().encryptionType(EncryptionType.VAULT).build());
    assertThat(secretManagerConfig).isNotNull();
    assertThat(secretManagerConfig.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
    verify(vaultService).saveOrUpdateVaultConfig(any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdate_GCPKMS() {
    doReturn(
        Optional.ofNullable(
            GcpKmsConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().harnessManaged(false).build()).build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    when(gcpSecretsManagerService.updateGcpKmsConfig(any(), any(), anyBoolean())).thenReturn("abcde");
    doReturn(0L).when(ngSecretManagerService).getCountOfSecretsCreatedUsingSecretManager(any(), any(), any(), any());

    SecretManagerConfig secretManagerConfig = ngSecretManagerService.update("account", null, null, "identifier",
        GcpKmsConfigUpdateDTO.builder().encryptionType(EncryptionType.GCP_KMS).build());
    assertThat(secretManagerConfig).isNotNull();
    assertThat(secretManagerConfig.getEncryptionType()).isEqualTo(EncryptionType.GCP_KMS);
    verify(gcpSecretsManagerService).updateGcpKmsConfig(any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdate_shouldFailForHarnessSecretManager() {
    doReturn(
        Optional.ofNullable(
            VaultConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().harnessManaged(true).build()).build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    try {
      ngSecretManagerService.update("account", null, null, "identifier",
          VaultConfigUpdateDTO.builder().encryptionType(EncryptionType.VAULT).build());
      fail("Should not reach here");
    } catch (UnsupportedOperationException e) {
      // ignore
    }
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdate_shouldFailForSecretManagerNotFound() {
    doReturn(Optional.empty()).when(ngSecretManagerService).get(any(), any(), any(), any(), eq(true));
    try {
      ngSecretManagerService.update("account", null, null, "identifier",
          VaultConfigUpdateDTO.builder().encryptionType(EncryptionType.VAULT).build());
      fail("Should not reach here");
    } catch (InvalidRequestException e) {
      // ignore
    }
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGetMetadata_ForExistingVault_withToken() {
    doReturn(Optional.ofNullable(VaultConfig.builder().build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    when(vaultService.listSecretEngines(any())).thenReturn(new ArrayList<>());
    SecretRefData authToken = new SecretRefData("dummy", Scope.ACCOUNT, "authToken".toCharArray());
    SecretManagerMetadataDTO secretManagerMetadataDTO = ngSecretManagerService.getMetadata("account",
        SecretManagerMetadataRequestDTO.builder()
            .encryptionType(EncryptionType.VAULT)
            .identifier("identifier")
            .spec(VaultMetadataRequestSpecDTO.builder()
                      .url("url")
                      .accessType(AccessType.TOKEN)
                      .spec(VaultAuthTokenCredentialDTO.builder().authToken(authToken).build())
                      .build())
            .build());
    assertThat(secretManagerMetadataDTO).isNotNull();
    assertThat(secretManagerMetadataDTO.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
    assertThat(secretManagerMetadataDTO.getSpec()).isInstanceOf(VaultMetadataSpecDTO.class);
    verify(vaultService).listSecretEngines(any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGetMetadata_ForExistingVault_withAppRole() {
    doReturn(Optional.ofNullable(VaultConfig.builder().build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    when(vaultService.listSecretEngines(any())).thenReturn(new ArrayList<>());
    SecretRefData secretId = new SecretRefData("dummy", Scope.ACCOUNT, "secretId".toCharArray());
    SecretManagerMetadataDTO secretManagerMetadataDTO = ngSecretManagerService.getMetadata("account",
        SecretManagerMetadataRequestDTO.builder()
            .encryptionType(EncryptionType.VAULT)
            .identifier("identifier")
            .spec(VaultMetadataRequestSpecDTO.builder()
                      .url("url")
                      .accessType(AccessType.APP_ROLE)
                      .spec(VaultAppRoleCredentialDTO.builder().appRoleId("appRoleId").secretId(secretId).build())
                      .build())
            .build());
    assertThat(secretManagerMetadataDTO).isNotNull();
    assertThat(secretManagerMetadataDTO.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
    assertThat(secretManagerMetadataDTO.getSpec()).isInstanceOf(VaultMetadataSpecDTO.class);
    verify(vaultService).listSecretEngines(any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGetMetadata_ForNewVault() {
    doReturn(Optional.empty()).when(ngSecretManagerService).get(any(), any(), any(), any(), eq(true));
    when(vaultService.listSecretEngines(any())).thenReturn(new ArrayList<>());
    SecretRefData secretId = new SecretRefData("dummy", Scope.ACCOUNT, "secretId".toCharArray());
    SecretManagerMetadataDTO secretManagerMetadataDTO = ngSecretManagerService.getMetadata("account",
        SecretManagerMetadataRequestDTO.builder()
            .encryptionType(EncryptionType.VAULT)
            .identifier("identifier")
            .spec(VaultMetadataRequestSpecDTO.builder()
                      .url("url")
                      .accessType(AccessType.APP_ROLE)
                      .spec(VaultAppRoleCredentialDTO.builder().appRoleId("appRoleId").secretId(secretId).build())
                      .build())
            .build());
    assertThat(secretManagerMetadataDTO).isNotNull();
    assertThat(secretManagerMetadataDTO.getEncryptionType()).isEqualTo(EncryptionType.VAULT);
    assertThat(secretManagerMetadataDTO.getSpec()).isInstanceOf(VaultMetadataSpecDTO.class);
    verify(vaultService).listSecretEngines(any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testGetMetadata_shouldFailDueToInvalidEncryptionType() {
    try {
      ngSecretManagerService.getMetadata(
          "account", SecretManagerMetadataRequestDTO.builder().encryptionType(EncryptionType.GCP_KMS).build());
      fail("Execution should not reach here");
    } catch (UnsupportedOperationException e) {
      // ingore
    }
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testDelete_withSoftDeleteFlagTrue() {
    doReturn(Optional.of(VaultConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().build()).build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    doReturn(0L).when(ngSecretManagerService).getCountOfSecretsCreatedUsingSecretManager(any(), any(), any(), any());
    boolean deleted = ngSecretManagerService.delete("account", null, null, "identifier", true);
    assertThat(deleted).isTrue();
    verify(wingsPersistence).save(any(SecretManagerConfig.class));
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testDelete_withSoftDeleteFlagFalse() {
    doReturn(Optional.of(VaultConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().build()).build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    doReturn(0L).when(ngSecretManagerService).getCountOfSecretsCreatedUsingSecretManager(any(), any(), any(), any());
    when(vaultService.deleteVaultConfig(any(), any())).thenReturn(true);
    boolean deleted = ngSecretManagerService.delete("account", null, null, "identifier", false);
    assertThat(deleted).isTrue();
    verify(vaultService).deleteVaultConfig(any(), any());

    doReturn(Optional.of(GcpKmsConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().build()).build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    when(gcpSecretsManagerService.deleteGcpKmsConfig(any(), any())).thenReturn(true);
    deleted = ngSecretManagerService.delete("account", null, null, "identifier", false);
    assertThat(deleted).isTrue();
    verify(gcpSecretsManagerService).deleteGcpKmsConfig(any(), any());
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testDelete_ForUnsupportedSecretManager() {
    doReturn(
        Optional.of(AwsSecretsManagerConfig.builder().ngMetadata(NGSecretManagerMetadata.builder().build()).build()))
        .when(ngSecretManagerService)
        .get(any(), any(), any(), any(), eq(true));
    doReturn(0L).when(ngSecretManagerService).getCountOfSecretsCreatedUsingSecretManager(any(), any(), any(), any());
    try {
      ngSecretManagerService.delete("account", null, null, "identifier", false);
      fail("Execution should not reach here");
    } catch (UnsupportedOperationException e) {
      // ignore
    }
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testDelete_WhenSecretManagerDoesNotExist() {
    doReturn(Optional.empty()).when(ngSecretManagerService).get(any(), any(), any(), any(), eq(true));
    boolean deleted = ngSecretManagerService.delete("account", null, null, "identifier", false);
    assertThat(deleted).isFalse();
  }

  private void testUpdateWithRestrictedFields(VaultConfig configInDB, VaultConfigUpdateDTO updateDTO) {
    doReturn(Optional.ofNullable(configInDB)).when(ngSecretManagerService).get(any(), any(), any(), any(), eq(true));
    when(vaultService.saveOrUpdateVaultConfig(any(), any(), anyBoolean())).thenReturn("abcde");
    doReturn(1L).when(ngSecretManagerService).getCountOfSecretsCreatedUsingSecretManager(any(), any(), any(), any());

    try {
      ngSecretManagerService.update("account", null, null, "identifier", updateDTO);
      fail("Execution should not reach here");
    } catch (SecretManagementException e) {
      log.info("Message: {}", e.getMessage());
    }
  }

  @Test
  @Owner(developers = PHOENIKX)
  @Category(UnitTests.class)
  public void testUpdate_shouldFailForVaultWhenSecretsPresentAndBasePathUpdated() {
    testUpdateWithRestrictedFields(VaultConfig.builder()
                                       .basePath("xyz")
                                       .ngMetadata(NGSecretManagerMetadata.builder().harnessManaged(false).build())
                                       .build(),
        VaultConfigUpdateDTO.builder().basePath("abcd").encryptionType(EncryptionType.VAULT).build());

    testUpdateWithRestrictedFields(VaultConfig.builder()
                                       .secretEngineName("xyz")
                                       .ngMetadata(NGSecretManagerMetadata.builder().harnessManaged(false).build())
                                       .build(),
        VaultConfigUpdateDTO.builder().secretEngineName("abcd").encryptionType(EncryptionType.VAULT).build());

    testUpdateWithRestrictedFields(VaultConfig.builder()
                                       .secretEngineVersion(0)
                                       .ngMetadata(NGSecretManagerMetadata.builder().harnessManaged(false).build())
                                       .build(),
        VaultConfigUpdateDTO.builder().secretEngineVersion(1).encryptionType(EncryptionType.VAULT).build());

    testUpdateWithRestrictedFields(VaultConfig.builder()
                                       .vaultUrl("xyz")
                                       .ngMetadata(NGSecretManagerMetadata.builder().harnessManaged(false).build())
                                       .build(),
        VaultConfigUpdateDTO.builder().vaultUrl("abcd").encryptionType(EncryptionType.VAULT).build());
  }
}
