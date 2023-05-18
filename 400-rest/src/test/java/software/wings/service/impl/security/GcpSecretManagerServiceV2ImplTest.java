/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.rule.OwnerRule.PIYUSH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.SecretManagementException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.GcpSecretsManagerConfig;
import software.wings.features.api.PremiumFeature;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.GcpSecretsManagerServiceV2;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class GcpSecretManagerServiceV2ImplTest extends WingsBaseTest {
  @Inject private HPersistence persistence;
  @Inject @Spy @InjectMocks private GcpSecretsManagerServiceV2 gcpSecretsManagerService;

  private String accountId;
  @Mock private AccountService accountService;
  @Mock private PremiumFeature secretsManagementFeature;

  @Inject KryoSerializer kryoSerializer;

  @Before
  public void setup() throws IOException, NoSuchFieldException, IllegalAccessException {
    initMocks(this);
    Account account = getAccount(AccountType.PAID);
    accountId = account.getUuid();
    when(accountService.get(accountId)).thenReturn(account);
    // doNothing().when(gcpSecretsManagerService).validateSecretsManagerConfig(anyString(),any());
    when(secretsManagementFeature.isAvailableForAccount(accountId)).thenReturn(true);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldCreateSecretManagerSuccessFully() {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = getGcpSecretsManagerConfig();
    gcpSecretsManagerConfig.setAccountId(accountId);
    String savedConfigId =
        gcpSecretsManagerService.saveGcpSecretsManagerConfig(accountId, gcpSecretsManagerConfig, false);
    assertEquals(gcpSecretsManagerConfig.getName(),
        gcpSecretsManagerService.getGcpSecretsManagerConfig(accountId, savedConfigId).getName());
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldThrowExceptionWhileCreatingSecretForWrongCredentials() {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = getGcpSecretsManagerConfig();
    gcpSecretsManagerConfig.setAccountId(accountId);
    try {
      String savedConfigId =
          gcpSecretsManagerService.saveGcpSecretsManagerConfig(accountId, gcpSecretsManagerConfig, true);
      Assert.fail("Created config with invalid credentials");
    } catch (SecretManagementException e) {
      assertEquals(
          "Was not able to reach GCP Secrets Manager using given credentials. Please check your credentials and try again",
          e.getMessage());
    }
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldUpdateSecretManagerNameSuccessFully() {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = getGcpSecretsManagerConfig();
    gcpSecretsManagerConfig.setAccountId(accountId);

    String savedConfigId = gcpSecretsManagerService.saveGcpSecretsManagerConfig(
        accountId, kryoSerializer.clone(gcpSecretsManagerConfig), false);

    GcpSecretsManagerConfig gcpSecretManagerToUpdate =
        gcpSecretsManagerService.getGcpSecretsManagerConfig(accountId, savedConfigId);

    gcpSecretManagerToUpdate.setUuid(savedConfigId);
    gcpSecretManagerToUpdate.setName("UpdatedConfig");
    gcpSecretManagerToUpdate.maskSecrets();

    gcpSecretsManagerService.saveGcpSecretsManagerConfig(
        accountId, kryoSerializer.clone(gcpSecretManagerToUpdate), false);

    assertEquals(
        "UpdatedConfig", gcpSecretsManagerService.getGcpSecretsManagerConfig(accountId, savedConfigId).getName());
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldUpdateSecretManagerCredentialsSuccessFully() {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = getGcpSecretsManagerConfig();
    gcpSecretsManagerConfig.setAccountId(accountId);
    doNothing().when(gcpSecretsManagerService).validateSecretsManagerConfig(anyString(), any());
    String savedConfigId = gcpSecretsManagerService.saveGcpSecretsManagerConfig(
        accountId, kryoSerializer.clone(gcpSecretsManagerConfig), false);

    GcpSecretsManagerConfig gcpSecretManagerToUpdate =
        gcpSecretsManagerService.getGcpSecretsManagerConfig(accountId, savedConfigId);

    gcpSecretManagerToUpdate.setUuid(savedConfigId);
    gcpSecretManagerToUpdate.setCredentials("UpdatedCredentials".toCharArray());

    String updatedConfigId = gcpSecretsManagerService.updateGcpSecretsManagerConfig(
        accountId, kryoSerializer.clone(gcpSecretManagerToUpdate));

    assertEquals(savedConfigId, updatedConfigId);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldDeleteSecretManagerSuccessFully() {
    doNothing().when(gcpSecretsManagerService).validateSecretsManagerConfig(anyString(), any());
    GcpSecretsManagerConfig gcpSecretsManagerConfig = getGcpSecretsManagerConfig();
    gcpSecretsManagerConfig.setAccountId(accountId);
    String savedConfigId =
        gcpSecretsManagerService.saveGcpSecretsManagerConfig(accountId, gcpSecretsManagerConfig, false);
    boolean deleted = gcpSecretsManagerService.deleteGcpSecretsManagerConfig(accountId, savedConfigId);
    assertTrue(deleted);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldThrowErrorOnDeleteForNonExistentId() {
    doNothing().when(gcpSecretsManagerService).validateSecretsManagerConfig(anyString(), any());
    GcpSecretsManagerConfig gcpSecretsManagerConfig = getGcpSecretsManagerConfig();
    gcpSecretsManagerConfig.setAccountId(accountId);
    String savedConfigId =
        gcpSecretsManagerService.saveGcpSecretsManagerConfig(accountId, gcpSecretsManagerConfig, false);
    try {
      boolean deleted = gcpSecretsManagerService.deleteGcpSecretsManagerConfig(accountId, "randomId");
      Assert.fail("Deleted config with invalid id");
    } catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void test_itShouldDecryptSecretManagerConfigSuccessFully() {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = getGcpSecretsManagerConfig();
    gcpSecretsManagerConfig.setAccountId(accountId);
    String savedConfigId =
        gcpSecretsManagerService.saveGcpSecretsManagerConfig(accountId, gcpSecretsManagerConfig, false);
    GcpSecretsManagerConfig savedConfig = gcpSecretsManagerService.getGcpSecretsManagerConfig(accountId, savedConfigId);
    gcpSecretsManagerService.decryptGcpConfigSecrets(savedConfig, false);
    assertEquals(savedConfig.getUuid(), gcpSecretsManagerConfig.getUuid());
  }

  public GcpSecretsManagerConfig getGcpSecretsManagerConfig() {
    GcpSecretsManagerConfig gcpSecretsManagerConfig = GcpSecretsManagerConfig.builder()
                                                          .name("TestGcpSecretManager")
                                                          .credentials(UUIDGenerator.generateUuid().toCharArray())
                                                          .build();
    return gcpSecretsManagerConfig;
  }
}
