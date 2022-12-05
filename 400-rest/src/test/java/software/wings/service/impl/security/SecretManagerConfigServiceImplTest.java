/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.UTKARSH;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.LocalEncryptionConfig.HARNESS_DEFAULT_SECRET_MANAGER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.SecretManagementException;
import io.harness.rule.Owner;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.common.EncryptionType;

import software.wings.WingsBaseTest;
import software.wings.beans.GcpKmsConfig;

import com.google.inject.Inject;
import java.util.HashMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class SecretManagerConfigServiceImplTest extends WingsBaseTest {
  private String accountId = "accountId";
  @Inject private SecretManagerConfigService secretManagerConfigService;

  @Test(expected = SecretManagementException.class)
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_getEncryptionBySecretManagerId_whenNOValuePresent() {
    secretManagerConfigService.getEncryptionBySecretManagerId(UUIDGenerator.generateUuid(), accountId);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_getEncryptionBySecretManagerId() {
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();
    SecretManagerConfig secretManagerConfig =
        new GcpKmsConfig("name", "projectId", "region", "keyRing", "keyName", credentials, null);
    secretManagerConfig.setAccountId(accountId);
    String configId = secretManagerConfigService.save(secretManagerConfig);
    EncryptionType encryptionType = secretManagerConfigService.getEncryptionBySecretManagerId(configId, accountId);
    assertThat(encryptionType).isEqualTo(EncryptionType.GCP_KMS);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getSecretManagerName_GlobalSecretManager() {
    String secretManagerName = "secretManagerName";
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();
    SecretManagerConfig secretManagerConfig =
        new GcpKmsConfig(secretManagerName, "projectId", "region", "keyRing", "keyName", credentials, null);
    secretManagerConfig.setAccountId(GLOBAL_ACCOUNT_ID);
    String configId = secretManagerConfigService.save(secretManagerConfig);
    String name = secretManagerConfigService.getSecretManagerName(configId, accountId);
    assertThat(name).isEqualTo(secretManagerName);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getSecretManagerName_LocalSecretManager() {
    String name = secretManagerConfigService.getSecretManagerName(accountId, accountId);
    assertThat(name).isEqualTo(HARNESS_DEFAULT_SECRET_MANAGER);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getSecretManagerName_CustomerSecretManager() {
    String secretManagerName = "secretManagerName";
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();
    SecretManagerConfig secretManagerConfig =
        new GcpKmsConfig(secretManagerName, "projectId", "region", "keyRing", "keyName", credentials, null);
    secretManagerConfig.setAccountId(accountId);
    String configId = secretManagerConfigService.save(secretManagerConfig);
    String name = secretManagerConfigService.getSecretManagerName(configId, accountId);
    assertThat(name).isEqualTo(secretManagerName);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void test_getSecretManagerName_shouldReturnNull() {
    String secretManagerName = "secretManagerName";
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();
    SecretManagerConfig secretManagerConfig =
        new GcpKmsConfig(secretManagerName, "projectId", "region", "keyRing", "keyName", credentials, null);
    secretManagerConfig.setAccountId(UUIDGenerator.generateUuid());
    String configId = secretManagerConfigService.save(secretManagerConfig);
    String name = secretManagerConfigService.getSecretManagerName(configId, accountId);
    assertThat(name).isEqualTo(null);
  }

  @Test(expected = SecretManagementException.class)
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void test_getSecretManager_NullSecretManager() {
    String secretManagerName = "secretManagerName";
    char[] credentials = "{\"credentials\":\"abc\"}".toCharArray();
    SecretManagerConfig secretManagerConfig =
        new GcpKmsConfig(secretManagerName, "projectId", "region", "keyRing", "keyName", credentials, null);
    secretManagerConfig.setAccountId(accountId);
    String configId = secretManagerConfigService.save(secretManagerConfig);
    EncryptionType encryptionType = secretManagerConfigService.getEncryptionBySecretManagerId(configId, accountId);
    secretManagerConfigService.getSecretManager(accountId, "randomConfigId", encryptionType, new HashMap<>());
  }
}
