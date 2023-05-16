/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.handler;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.SHASHANK;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector;
import io.harness.connector.entities.embedded.vaultconnector.VaultConnector.VaultConnectorBuilder;
import io.harness.connector.services.NGVaultService;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.rule.Owner;
import io.harness.security.encryption.AccessType;

import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(PL)
public class NGVaultSecretManagerRenewalHandlerTest extends CategoryTest {
  @Mock private NGVaultService ngVaultService;
  @Mock MongoTemplate mongoTemplate;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  private NGVaultSecretManagerRenewalHandler vaultSecretManagerRenewalHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    vaultSecretManagerRenewalHandler =
        new NGVaultSecretManagerRenewalHandler(persistenceIteratorFactory, mongoTemplate, ngVaultService);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testRenewalForTokenVaultConfig_customInterval_shouldSucceed() {
    VaultConnector vaultConnector = getVaultConnectorWithAccessType(AccessType.TOKEN);
    vaultConnector.setAccountIdentifier("sampleAccount");
    vaultConnector.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(15L).toMillis());
    when(mongoTemplate.findById(any(), any())).thenReturn(vaultConnector);
    vaultSecretManagerRenewalHandler.handle(vaultConnector);
    verify(ngVaultService, times(1)).renewToken(vaultConnector);
    verify(ngVaultService, times(0)).renewAppRoleClientToken(any());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testRenewalForAppRoleVaultConfig_customInterval_shouldSucceed() {
    VaultConnector vaultConnector = getVaultConnectorWithAccessType(AccessType.APP_ROLE);
    vaultConnector.setAccountIdentifier("sampleAccount");
    vaultConnector.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(15L).toMillis());
    when(mongoTemplate.findById(any(), any())).thenReturn(vaultConnector);
    vaultSecretManagerRenewalHandler.handle(vaultConnector);
    verify(ngVaultService, times(0)).renewToken(any());
    verify(ngVaultService, times(1)).renewAppRoleClientToken(vaultConnector);
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testRenewalForVaultAgentVaultConfig_customInterval_shouldSucceed() {
    VaultConnector vaultConnector = getVaultConnectorVaultAgent(AccessType.VAULT_AGENT);
    vaultConnector.setAccountIdentifier("sampleAccount");
    vaultConnector.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(15L).toMillis());
    when(mongoTemplate.findById(any(), any())).thenReturn(vaultConnector);
    vaultSecretManagerRenewalHandler.handle(vaultConnector);
    verify(ngVaultService, times(0)).renewToken(any());
    verify(ngVaultService, times(0)).renewAppRoleClientToken(any());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testRenewalForAwsIamVaultConfig_customInterval_Pass() {
    VaultConnector vaultConnector = getVaultConnectorAwsIam(AccessType.AWS_IAM);
    vaultConnector.setAccountIdentifier("sampleAccount");
    vaultConnector.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(15L).toMillis());
    when(mongoTemplate.findById(any(), any())).thenReturn(vaultConnector);
    vaultSecretManagerRenewalHandler.handle(vaultConnector);
    verify(ngVaultService, times(0)).renewToken(any());
    verify(ngVaultService, times(0)).renewAppRoleClientToken(any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testRenewalForAppRole_doNotRenew_doNotRenewAppRoleSetToTrue() {
    VaultConnector vaultConnector = getAppRoleBasedVault();
    vaultConnector.setRenewAppRoleToken(false);
    vaultConnector.setAccountIdentifier("accountId");
    when(mongoTemplate.findById(any(), any())).thenReturn(vaultConnector);
    vaultSecretManagerRenewalHandler.handle(vaultConnector);
    verify(ngVaultService, times(0)).renewToken(any());
    verify(ngVaultService, times(0)).renewAppRoleClientToken(any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testRenewal_forSoftDeletedVault_shouldNotHappen() {
    VaultConnector vaultConnector = getVaultConnectorWithAccessType(AccessType.TOKEN);
    vaultConnector.setAccountIdentifier("sampleAccount");
    vaultConnector.setRenewedAt(System.currentTimeMillis() - Duration.ofMinutes(15L).toMillis());
    vaultConnector.setDeleted(true);
    when(mongoTemplate.findById(any(), any())).thenReturn(vaultConnector);
    vaultSecretManagerRenewalHandler.handle(vaultConnector);
    verify(ngVaultService, times(0)).renewToken(vaultConnector);
    verify(ngVaultService, times(0)).renewAppRoleClientToken(any());
  }

  private VaultConnector getAppRoleBasedVault() {
    return VaultConnector.builder()
        .accessType(AccessType.APP_ROLE)
        .vaultUrl("vaultUrl")
        .secretEngineVersion(2)
        .secretEngineManuallyConfigured(false)
        .renewalIntervalMinutes(10L)
        .renewedAt(System.currentTimeMillis())
        .appRoleId("appRoleId")
        .secretIdRef("sampleSecretId")
        .basePath("/harness")
        .sinkPath("/sinkPath")
        .build();
  }

  private VaultConnector getVaultConnectorWithAccessType(AccessType accessType) {
    return getVaultConnectorBuilder(accessType).useVaultAgent(false).useAwsIam(false).build();
  }

  private VaultConnectorBuilder getVaultConnectorBuilder(AccessType accessType) {
    return VaultConnector.builder()
        .authTokenRef("authToken")
        .accessType(accessType)
        .isDefault(false)
        .isReadOnly(false)
        .secretEngineName("secretEngineName")
        .vaultUrl("vaultUrl")
        .secretEngineVersion(2)
        .secretEngineManuallyConfigured(false)
        .renewalIntervalMinutes(10L)
        .renewedAt(System.currentTimeMillis())
        .appRoleId("appRoleId")
        .basePath("/harness")
        .sinkPath("/sinkPath")
        .awsRegion("us-east-1")
        .vaultAwsIamRoleRef("devRole")
        .xVaultAwsIamServerIdRef("sampleHeader")
        .secretIdRef("secretId");
  }

  private VaultConnector getVaultConnectorAwsIam(AccessType accessType) {
    return getVaultConnectorBuilder(accessType).useVaultAgent(false).useAwsIam(true).build();
  }

  private VaultConnector getVaultConnectorVaultAgent(AccessType accessType) {
    return getVaultConnectorBuilder(accessType).useAwsIam(true).useVaultAgent(false).build();
  }
}