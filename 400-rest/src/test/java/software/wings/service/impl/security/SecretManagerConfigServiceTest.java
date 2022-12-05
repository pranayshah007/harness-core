/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.SecretManagerConfig;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.common.EncryptionType;

import software.wings.SecretManagementTestHelper;
import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.KmsConfig;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.security.GcpSecretsManagerService;
import software.wings.service.intfc.security.KmsService;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SecretManagerConfigServiceTest extends WingsBaseTest {
  @Inject WingsPersistence wingsPersistence;
  @Mock KmsService kmsService;
  @Mock GcpSecretsManagerService gcpSecretsManagerService;
  @Inject @InjectMocks SecretManagerConfigService secretManagerConfigService;
  @Inject private SecretManagementTestHelper secretManagementTestHelper;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetGlobalSecretManager_shouldReturnAwsKms() {
    String accountId = "accountId";
    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setAccountId(GLOBAL_ACCOUNT_ID);
    String configId = wingsPersistence.save(kmsConfig);
    kmsConfig.setUuid(configId);

    when(kmsService.getKmsConfig(accountId, configId)).thenReturn(kmsConfig);

    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getGlobalSecretManager(accountId);
    assertThat(secretManagerConfig).isNotNull();
    assertThat(secretManagerConfig.getEncryptionType()).isEqualTo(EncryptionType.KMS);
    assertThat(secretManagerConfig.getAccountId()).isEqualTo(GLOBAL_ACCOUNT_ID);

    verify(kmsService, times(1)).decryptKmsConfigSecrets(accountId, kmsConfig, false);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetGlobalSecretManager_shouldReturnGcpKms() {
    String accountId = "accountId";
    GcpKmsConfig gcpKmsConfig = secretManagementTestHelper.getGcpKmsConfig();
    gcpKmsConfig.setAccountId(GLOBAL_ACCOUNT_ID);
    String configId = wingsPersistence.save(gcpKmsConfig);
    gcpKmsConfig.setUuid(configId);

    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setAccountId(GLOBAL_ACCOUNT_ID);
    String kmsConfigId = wingsPersistence.save(kmsConfig);
    kmsConfig.setUuid(kmsConfigId);

    when(gcpSecretsManagerService.getGcpKmsConfig(accountId, configId)).thenReturn(gcpKmsConfig);

    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getGlobalSecretManager(accountId);
    assertThat(secretManagerConfig).isNotNull();
    assertThat(secretManagerConfig.getEncryptionType()).isEqualTo(EncryptionType.GCP_KMS);
    assertThat(secretManagerConfig.getAccountId()).isEqualTo(GLOBAL_ACCOUNT_ID);

    verify(gcpSecretsManagerService, times(1)).decryptGcpConfigSecrets(gcpKmsConfig, false);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetDefaultGlobalSecretManager() {
    String accountId = "accountId";

    GcpKmsConfig gcpKmsConfig = secretManagementTestHelper.getGcpKmsConfig();
    gcpKmsConfig.setAccountId(GLOBAL_ACCOUNT_ID);
    String gcpKmsConfigId = wingsPersistence.save(gcpKmsConfig);
    gcpKmsConfig.setUuid(gcpKmsConfigId);

    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setAccountId(GLOBAL_ACCOUNT_ID);
    String kmsConfigId = wingsPersistence.save(kmsConfig);
    kmsConfig.setUuid(kmsConfigId);
    List<SecretManagerConfig> secretManagerConfigList = secretManagerConfigService.listSecretManagers(accountId, true);
    assertThat(secretManagerConfigList).isNotNull();
    assertThat(secretManagerConfigList.size()).isEqualTo(1);
    SecretManagerConfig returnedSecretManagerConfig = secretManagerConfigList.get(0);
    assertThat(returnedSecretManagerConfig).isNotNull();
    assertThat(returnedSecretManagerConfig.getAccountId()).isEqualTo(GLOBAL_ACCOUNT_ID);
    assertThat(returnedSecretManagerConfig.getEncryptionType()).isEqualTo(EncryptionType.GCP_KMS);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testListSecresManager_AccountLocalEncryptionEnabled() {
    Account account = getAccount(AccountType.PAID);
    account.setLocalEncryptionEnabled(true);
    wingsPersistence.save(account);

    KmsConfig kmsConfig = secretManagementTestHelper.getKmsConfig();
    kmsConfig.setAccountId(GLOBAL_ACCOUNT_ID);
    String kmsConfigId = wingsPersistence.save(kmsConfig);
    kmsConfig.setUuid(kmsConfigId);

    List<SecretManagerConfig> secretManagerConfigList =
        secretManagerConfigService.listSecretManagers(account.getUuid(), true);
    assertThat(secretManagerConfigList).hasSize(1);
    assertThat(secretManagerConfigList.get(0).getEncryptionType()).isEqualTo(EncryptionType.LOCAL);

    account.setLocalEncryptionEnabled(false);
    wingsPersistence.save(account);

    secretManagerConfigList = secretManagerConfigService.listSecretManagers(account.getUuid(), true);
    assertThat(secretManagerConfigList).hasSize(1);
    assertThat(secretManagerConfigList.get(0).getEncryptionType()).isEqualTo(EncryptionType.KMS);
  }
}
