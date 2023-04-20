/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.alert.AlertType.InvalidKMS;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.SecretManagementTestHelper;
import software.wings.WingsBaseTest;
import software.wings.beans.VaultConfig;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.security.VaultService;

import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class VaultSecretManagerRenewalHandlerTest extends WingsBaseTest {
  @Mock private VaultService vaultService;
  @Mock private AlertService alertService;
  @Inject @InjectMocks private VaultSecretManagerRenewalHandler vaultSecretManagerRenewalHandler;
  @Inject private SecretManagementTestHelper secretManagementTestHelper;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenewalForAppRoleVaultConfig_customInterval_shouldSucceed() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole("appRoleId", "secretId");
    vaultConfig.setAccountId("accountId");
    vaultConfig.setRenewalInterval(5);
    vaultConfig.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(5).toMillis());
    vaultSecretManagerRenewalHandler.handle(vaultConfig);
    verify(vaultService, times(1)).renewAppRoleClientToken(vaultConfig);
    verify(vaultService, times(0)).renewToken(any());
    verifySuccessAlertInteraction(vaultConfig.getAccountId());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenewalForAppRoleVaultConfig_customInterval_shouldNotHappen() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole("appRoleId", "secretId");
    vaultConfig.setAccountId("accountId");
    vaultConfig.setRenewalInterval(5);
    vaultConfig.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(2).toMillis());
    vaultSecretManagerRenewalHandler.handle(vaultConfig);
    verifyInteractionWithNoMocks();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenewalForAppRoleVaultConfig_disabled_shouldNotHappen() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole("appRoleId", "secretId");
    vaultConfig.setAccountId("accountId");
    vaultConfig.setRenewalInterval(0);
    vaultConfig.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(15).toMillis());
    vaultSecretManagerRenewalHandler.handle(vaultConfig);
    verifyInteractionWithNoMocks();
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testRenewalForAppRoleVaultConfig_withRenewAppRoleTokenSetToFalse_shouldNotPass() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole("appRoleId", "secretId");
    vaultConfig.setAccountId("accountId");
    vaultConfig.setRenewalInterval(5);
    vaultConfig.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(5).toMillis());
    vaultConfig.setRenewAppRoleToken(false);
    vaultSecretManagerRenewalHandler.handle(vaultConfig);
    verify(vaultService, times(0)).renewAppRoleClientToken(any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testRenewalForAppRoleVaultConfig_withRenewAppRoleSetToTrue_shouldPass() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole("appRoleId", "secretId");
    vaultConfig.setAccountId("accountId");
    vaultConfig.setRenewalInterval(5);
    vaultConfig.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(5).toMillis());
    vaultConfig.setRenewAppRoleToken(true);
    vaultSecretManagerRenewalHandler.handle(vaultConfig);
    verify(vaultService, times(1)).renewAppRoleClientToken(vaultConfig);
    verify(vaultService, times(0)).renewToken(any());
    verifySuccessAlertInteraction(vaultConfig.getAccountId());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenewalForTokenVaultConfig_disabled_shouldNotHappen() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken("authToken");
    vaultSecretManagerRenewalHandler.handle(vaultConfig);
    verifyInteractionWithNoMocks();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenewalForTokenVaultConfig_alreadyRenewed_shouldNotHappen() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken("authToken");
    vaultConfig.setRenewalInterval(5);
    vaultConfig.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(2).toMillis());
    vaultSecretManagerRenewalHandler.handle(vaultConfig);
    verifyInteractionWithNoMocks();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenewalForTokenVaultConfig_shouldSucceed() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken("authToken");
    vaultConfig.setAccountId("accountId");
    vaultConfig.setRenewalInterval(5);
    vaultConfig.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(10).toMillis());
    vaultSecretManagerRenewalHandler.handle(vaultConfig);
    verify(vaultService, times(0)).renewAppRoleClientToken(any());
    verify(vaultService, times(1)).renewToken(vaultConfig);
    verifySuccessAlertInteraction(vaultConfig.getAccountId());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenewalForTokenVaultConfig_shouldFail() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAuthToken("authToken");
    vaultConfig.setAccountId("accountId");
    vaultConfig.setRenewalInterval(5);
    vaultConfig.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(10).toMillis());
    doThrow(new RuntimeException()).when(vaultService).renewToken(vaultConfig);
    vaultSecretManagerRenewalHandler.handle(vaultConfig);
    verify(vaultService, times(0)).renewAppRoleClientToken(any());
    verify(vaultService, times(1)).renewToken(vaultConfig);
    verifyFailureAlertInteraction(vaultConfig.getAccountId());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenewalForAppRoleConfig_shouldFail() {
    VaultConfig vaultConfig = secretManagementTestHelper.getVaultConfigWithAppRole("appRole", "secretId");
    vaultConfig.setAccountId("accountId");
    vaultConfig.setRenewalInterval(5);
    vaultConfig.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(10).toMillis());
    doThrow(new RuntimeException()).when(vaultService).renewAppRoleClientToken(vaultConfig);
    vaultSecretManagerRenewalHandler.handle(vaultConfig);
    verify(vaultService, times(1)).renewAppRoleClientToken(any());
    verify(vaultService, times(0)).renewToken(vaultConfig);
    verifyFailureAlertInteraction(vaultConfig.getAccountId());
  }

  private void verifyInteractionWithNoMocks() {
    verify(vaultService, times(0)).renewAppRoleClientToken(any());
    verify(vaultService, times(0)).renewToken(any());
    verify(alertService, times(0)).closeAlert(any(), any(), any(), any());
    verify(alertService, times(0)).openAlert(any(), any(), any(), any());
  }

  private void verifySuccessAlertInteraction(String accountId) {
    verify(alertService, times(1)).closeAlert(eq(accountId), eq(GLOBAL_APP_ID), eq(InvalidKMS), any());
    verify(alertService, times(0)).openAlert(any(), any(), any(), any());
  }

  private void verifyFailureAlertInteraction(String accountId) {
    verify(alertService, times(1)).openAlert(eq(accountId), eq(GLOBAL_APP_ID), eq(InvalidKMS), any());
    verify(alertService, times(0)).closeAlert(any(), any(), any(), any());
  }
}
