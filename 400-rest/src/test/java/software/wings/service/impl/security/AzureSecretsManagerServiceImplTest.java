/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.eraro.ErrorCode.AZURE_KEY_VAULT_OPERATION_ERROR;
import static io.harness.rule.OwnerRule.ANKIT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.exception.AzureKeyVaultOperationException;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoSerializerWrapper;

import software.wings.SecretManagementTestHelper;
import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.AzureVaultConfig;
import software.wings.features.api.PremiumFeature;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.AzureSecretsManagerService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AzureSecretsManagerServiceImplTest extends WingsBaseTest {
  @Inject private SecretManager secretManager;
  @Inject private HPersistence persistence;
  @Inject private KryoSerializerWrapper kryoSerializerWrapper;
  @Inject private SecretManagementTestHelper secretManagementTestHelper;

  @Inject @InjectMocks private AzureSecretsManagerService azureSecretsManagerService;
  @Mock private AccountService accountService;
  @Mock private PremiumFeature secretsManagementFeature;

  private String accountId;

  @Before
  public void setup() throws IOException, NoSuchFieldException, IllegalAccessException {
    initMocks(this);

    Account account = getAccount(AccountType.PAID);
    accountId = account.getUuid();
    when(accountService.get(accountId)).thenReturn(account);

    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(true);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void saveAzureVaultConfig_shouldPass() {
    AzureVaultConfig azureVaultConfig = secretManagementTestHelper.getAzureVaultConfig();
    azureVaultConfig.setAccountId(accountId);

    String savedConfigId = azureSecretsManagerService.saveAzureSecretsManagerConfig(accountId, azureVaultConfig);
    assertEquals(
        azureVaultConfig.getName(), azureSecretsManagerService.getEncryptionConfig(accountId, savedConfigId).getName());
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void saveAzureVaultConfigIfFeatureNotAvailable_shouldThrowException() {
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(false);

    AzureVaultConfig azureVaultConfig = secretManagementTestHelper.getAzureVaultConfig();
    azureVaultConfig.setAccountId(accountId);

    try {
      azureSecretsManagerService.saveAzureSecretsManagerConfig(accountId, azureVaultConfig);
      fail("Azure Vault Config Saved when Secrets Management Feature is Unavailable !!");
    } catch (Exception ex) {
      assertTrue(ex instanceof InvalidRequestException);
    }
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void updateAzureVaultConfigNonSecretKey_shouldPass() {
    AzureVaultConfig azureVaultConfig = secretManagementTestHelper.getAzureVaultConfig();
    azureVaultConfig.setAccountId(accountId);

    String savedConfigId = azureSecretsManagerService.saveAzureSecretsManagerConfig(
        accountId, kryoSerializerWrapper.clone(azureVaultConfig));

    AzureVaultConfig updatedAzureVaultConfig = azureSecretsManagerService.getEncryptionConfig(accountId, savedConfigId);

    updatedAzureVaultConfig.setUuid(savedConfigId);
    updatedAzureVaultConfig.setName("UpdatedConfig");
    updatedAzureVaultConfig.maskSecrets();

    azureSecretsManagerService.saveAzureSecretsManagerConfig(
        accountId, kryoSerializerWrapper.clone(updatedAzureVaultConfig));

    assertEquals("UpdatedConfig", azureSecretsManagerService.getEncryptionConfig(accountId, savedConfigId).getName());
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void updateAzureVaultConfigSecretKey_shouldPass() {
    AzureVaultConfig azureVaultConfig = secretManagementTestHelper.getAzureVaultConfig();
    azureVaultConfig.setAccountId(accountId);

    String savedConfigId = azureSecretsManagerService.saveAzureSecretsManagerConfig(
        accountId, kryoSerializerWrapper.clone(azureVaultConfig));

    AzureVaultConfig savedAzureVaultConfig = azureSecretsManagerService.getEncryptionConfig(accountId, savedConfigId);

    savedAzureVaultConfig.setUuid(savedConfigId);
    savedAzureVaultConfig.setSecretKey("UpdatedSecretKey");

    azureSecretsManagerService.saveAzureSecretsManagerConfig(
        accountId, kryoSerializerWrapper.clone(savedAzureVaultConfig));

    assertEquals(
        "UpdatedSecretKey", azureSecretsManagerService.getEncryptionConfig(accountId, savedConfigId).getSecretKey());
  }

  @Test(expected = NullPointerException.class)
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void deleteAzureVaultConfigWithNoEncryptedSecrets_shouldPass() {
    AzureVaultConfig azureVaultConfig = secretManagementTestHelper.getAzureVaultConfig();
    azureVaultConfig.setAccountId(accountId);

    String savedConfigId = azureSecretsManagerService.saveAzureSecretsManagerConfig(
        accountId, kryoSerializerWrapper.clone(azureVaultConfig));

    assertNotNull(azureSecretsManagerService.getEncryptionConfig(accountId, savedConfigId));

    azureSecretsManagerService.deleteConfig(accountId, savedConfigId);

    // This would result in NPE, as the secret manager config is deleted
    azureSecretsManagerService.getEncryptionConfig(accountId, savedConfigId);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void deleteAzureVaultConfigWithEncryptedSecrets_shouldFail() {
    AzureVaultConfig azureVaultConfig = secretManagementTestHelper.getAzureVaultConfig();
    azureVaultConfig.setAccountId(accountId);

    String savedConfigId = azureSecretsManagerService.saveAzureSecretsManagerConfig(
        accountId, kryoSerializerWrapper.clone(azureVaultConfig));

    persistence.save(EncryptedData.builder()
                         .accountId(accountId)
                         .encryptionType(EncryptionType.AZURE_VAULT)
                         .kmsId(savedConfigId)
                         .build());

    try {
      azureSecretsManagerService.deleteConfig(accountId, savedConfigId);
      fail("Azure Vault Config Containing Secrets Deleted");
    } catch (AzureKeyVaultOperationException ex) {
      assertEquals(AZURE_KEY_VAULT_OPERATION_ERROR, ex.getCode());
    }
  }
}
