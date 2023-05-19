/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.AWS_SECRETS_MANAGER_OPERATION_ERROR;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.PIYUSH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.KryoSerializer;

import software.wings.SecretManagementTestHelper;
import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.features.api.PremiumFeature;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.AwsSecretsManagerService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsSecretsManagerServiceImplTest extends WingsBaseTest {
  @Inject private HPersistence persistence;

  @Inject @Spy @InjectMocks private AwsSecretsManagerService awsSecretsManagerService;
  @Mock private AccountService accountService;
  @Mock private PremiumFeature secretsManagementFeature;
  @Inject private SecretManagementTestHelper secretManagementTestHelper;
  @Inject private SecretManager secretManager;

  @Inject KryoSerializer kryoSerializer;

  private String accountId;

  @Rule public TemporaryFolder tempDirectory = new TemporaryFolder();

  @Before
  public void setup() throws IOException, NoSuchFieldException, IllegalAccessException {
    initMocks(this);
    Account account = getAccount(AccountType.PAID);
    accountId = account.getUuid();
    when(accountService.get(accountId)).thenReturn(account);

    doNothing().when(awsSecretsManagerService).validateSecretsManagerConfig(any(), any());
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(true);
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void saveAwsSecretManagerConfig_shouldPass() {
    AwsSecretsManagerConfig awsSecretManagerConfig = secretManagementTestHelper.getAwsSecretManagerConfig();
    awsSecretManagerConfig.setAccountId(accountId);

    String savedConfigId = awsSecretsManagerService.saveAwsSecretsManagerConfig(accountId, awsSecretManagerConfig);
    assertEquals(awsSecretManagerConfig.getName(),
        awsSecretsManagerService.getAwsSecretsManagerConfig(accountId, savedConfigId).getName());
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void saveAwsSecretManagerConfig_AssumeIAMRole_shouldPass() {
    AwsSecretsManagerConfig awsSecretManagerConfig = secretManagementTestHelper.getAwsSecretManagerConfig();
    awsSecretManagerConfig.setAccountId(accountId);
    awsSecretManagerConfig.setAssumeIamRoleOnDelegate(true);
    awsSecretManagerConfig.setSecretKey(null);
    awsSecretManagerConfig.setAccessKey(null);
    String savedConfigId = awsSecretsManagerService.saveAwsSecretsManagerConfig(accountId, awsSecretManagerConfig);
    assertEquals(awsSecretManagerConfig.getName(),
        awsSecretsManagerService.getAwsSecretsManagerConfig(accountId, savedConfigId).getName());
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void saveAwsSecretManagerConfig_AssumeSTSRole_shouldPass() {
    AwsSecretsManagerConfig awsSecretManagerConfig = secretManagementTestHelper.getAwsSecretManagerConfig();
    awsSecretManagerConfig.setAccountId(accountId);
    awsSecretManagerConfig.setAssumeStsRoleOnDelegate(true);
    awsSecretManagerConfig.setSecretKey(null);
    awsSecretManagerConfig.setAccessKey(null);
    String savedConfigId = awsSecretsManagerService.saveAwsSecretsManagerConfig(accountId, awsSecretManagerConfig);
    assertEquals(awsSecretManagerConfig.getName(),
        awsSecretsManagerService.getAwsSecretsManagerConfig(accountId, savedConfigId).getName());
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void saveAwsSecretManagerConfigIfFeatureNotAvailable_shouldThrowException() {
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(false);

    AwsSecretsManagerConfig awsSecretManagerConfig = secretManagementTestHelper.getAwsSecretManagerConfig();
    awsSecretManagerConfig.setAccountId(accountId);

    try {
      awsSecretsManagerService.saveAwsSecretsManagerConfig(accountId, awsSecretManagerConfig);
      fail("Aws Secret Manager Config Saved when Secrets Management Feature is Unavailable !!");
    } catch (Exception ex) {
      assertTrue(ex instanceof InvalidRequestException);
    }
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void updateAwsSecretManagerConfigNonSecretKey_shouldPass() {
    AwsSecretsManagerConfig awsSecretManagerConfig = secretManagementTestHelper.getAwsSecretManagerConfig();
    awsSecretManagerConfig.setAccountId(accountId);

    String savedConfigId =
        awsSecretsManagerService.saveAwsSecretsManagerConfig(accountId, kryoSerializer.clone(awsSecretManagerConfig));

    AwsSecretsManagerConfig updatedAwsSecretManagerConfig =
        awsSecretsManagerService.getAwsSecretsManagerConfig(accountId, savedConfigId);

    updatedAwsSecretManagerConfig.setUuid(savedConfigId);
    updatedAwsSecretManagerConfig.setName("UpdatedConfig");
    updatedAwsSecretManagerConfig.maskSecrets();

    awsSecretsManagerService.saveAwsSecretsManagerConfig(
        accountId, kryoSerializer.clone(updatedAwsSecretManagerConfig));

    assertEquals(
        "UpdatedConfig", awsSecretsManagerService.getAwsSecretsManagerConfig(accountId, savedConfigId).getName());
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void updateAwsSecretManagerConfigSecretKey_shouldPass() {
    AwsSecretsManagerConfig awsSecretManagerConfig = secretManagementTestHelper.getAwsSecretManagerConfig();
    awsSecretManagerConfig.setAccountId(accountId);

    String savedConfigId =
        awsSecretsManagerService.saveAwsSecretsManagerConfig(accountId, kryoSerializer.clone(awsSecretManagerConfig));

    AwsSecretsManagerConfig savedAwsSecretManagerConfig =
        awsSecretsManagerService.getAwsSecretsManagerConfig(accountId, savedConfigId);

    savedAwsSecretManagerConfig.setUuid(savedConfigId);
    savedAwsSecretManagerConfig.setSecretKey("UpdatedSecretKey");

    awsSecretsManagerService.saveAwsSecretsManagerConfig(accountId, kryoSerializer.clone(savedAwsSecretManagerConfig));

    assertEquals("UpdatedSecretKey",
        awsSecretsManagerService.getAwsSecretsManagerConfig(accountId, savedConfigId).getSecretKey());
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void deleteAwsSecretManagerConfigWithNoEncryptedSecrets_shouldPass() {
    AwsSecretsManagerConfig awsSecretManagerConfig = secretManagementTestHelper.getAwsSecretManagerConfig();
    awsSecretManagerConfig.setAccountId(accountId);

    String savedConfigId =
        awsSecretsManagerService.saveAwsSecretsManagerConfig(accountId, kryoSerializer.clone(awsSecretManagerConfig));

    assertNotNull(awsSecretsManagerService.getAwsSecretsManagerConfig(accountId, savedConfigId));

    awsSecretsManagerService.deleteAwsSecretsManagerConfig(accountId, savedConfigId);

    assertNull(awsSecretsManagerService.getAwsSecretsManagerConfig(accountId, savedConfigId));
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void deleteAwsSecretManagerConfigWithEncryptedSecrets_shouldFail() {
    AwsSecretsManagerConfig awsSecretManagerConfig = secretManagementTestHelper.getAwsSecretManagerConfig();
    awsSecretManagerConfig.setAccountId(accountId);

    String savedConfigId =
        awsSecretsManagerService.saveAwsSecretsManagerConfig(accountId, kryoSerializer.clone(awsSecretManagerConfig));

    persistence.save(EncryptedData.builder()
                         .accountId(accountId)
                         .encryptionType(EncryptionType.AWS_SECRETS_MANAGER)
                         .kmsId(savedConfigId)
                         .build());

    try {
      awsSecretsManagerService.deleteAwsSecretsManagerConfig(accountId, savedConfigId);
      fail("Aws Secret Manager Config Containing Secrets Deleted");
    } catch (WingsException ex) {
      assertEquals(AWS_SECRETS_MANAGER_OPERATION_ERROR, ex.getCode());
    }
  }
}
