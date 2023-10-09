/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.clients;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.RAGHAV_MURALI;
import static io.harness.rule.OwnerRule.SHASHANK;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static java.lang.String.valueOf;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.exception.runtime.hashicorp.HashiCorpVaultRuntimeException;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;
import io.harness.helpers.vault.NGVaultTaskHelper;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.VaultConfig;
import software.wings.helpers.ext.vault.VaultK8sLoginResult;
import software.wings.helpers.ext.vault.VaultReadResponse;
import software.wings.helpers.ext.vault.VaultReadResponseV2;
import software.wings.helpers.ext.vault.VaultRestClient;
import software.wings.helpers.ext.vault.VaultRestClientFactory;
import software.wings.helpers.ext.vault.VaultRestClientFactory.V1Impl;
import software.wings.helpers.ext.vault.VaultRestClientFactory.V2Impl;
import software.wings.helpers.ext.vault.VaultRestClientFactory.VaultPathAndKey;
import software.wings.helpers.ext.vault.VaultRestClientV1;
import software.wings.helpers.ext.vault.VaultRestClientV2;
import software.wings.helpers.ext.vault.VaultSecretValue;
import software.wings.helpers.ext.vault.VaultSysAuthRestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockedStatic;
import retrofit2.Call;
import retrofit2.Response;

@Slf4j
@OwnedBy(PL)
public class HashicorpVaultEncryptorTest extends CategoryTest {
  private HashicorpVaultEncryptor hashicorpVaultEncryptor;
  private VaultConfig vaultConfig;
  private VaultRestClient vaultRestClient;
  private VaultSysAuthRestClient vaultSysAuthRestClient;
  private MockedStatic<VaultRestClientFactory> vaultRestClientFactoryMockedStatic;
  private MockedStatic<NGVaultTaskHelper> ngVaultTaskHelperMockedStatic;
  public static final String AWS_IAM_TOKEN = "awsIamToken";
  public static final String K8s_AUTH_TOKEN = "k8sAuthToken";

  @Before
  public void setup() {
    vaultRestClient = mock(VaultRestClient.class);
    vaultConfig = VaultConfig.builder()
                      .uuid(UUIDGenerator.generateUuid())
                      .name(UUIDGenerator.generateUuid())
                      .accountId(UUIDGenerator.generateUuid())
                      .vaultUrl(UUIDGenerator.generateUuid())
                      .authToken(UUIDGenerator.generateUuid())
                      .encryptionType(EncryptionType.VAULT)
                      .isDefault(false)
                      .basePath(UUIDGenerator.generateUuid())
                      .secretEngineName(UUIDGenerator.generateUuid())
                      .secretEngineVersion(1)
                      .namespace(UUIDGenerator.generateUuid())
                      .build();
    // Mocking static is supported only in the running thread. Any other thread will call real methods. Since vault
    // encryptor spawns a new thread it was calling real methods. In tests we don't need multi threading so use
    // newDirectExecutorService to create executor service which uses the main thread to execute the task.
    ExecutorService executorService = MoreExecutors.newDirectExecutorService();
    hashicorpVaultEncryptor = new HashicorpVaultEncryptor(HTimeLimiter.create(executorService));
    vaultRestClientFactoryMockedStatic = mockStatic(VaultRestClientFactory.class);
    when(VaultRestClientFactory.create(vaultConfig)).thenReturn(vaultRestClient);
    when(VaultRestClientFactory.getFullPath(eq(vaultConfig.getBasePath()), anyString()))
        .thenAnswer(invocationOnMock -> {
          String path = (String) invocationOnMock.getArguments()[1];
          return vaultConfig.getBasePath() + "/" + path;
        });
    ngVaultTaskHelperMockedStatic = mockStatic(NGVaultTaskHelper.class);
    VaultAppRoleLoginResult loginResult = VaultAppRoleLoginResult.builder()
                                              .clientToken(AWS_IAM_TOKEN)
                                              .leaseDuration(10L)
                                              .accessor("accessor")
                                              .renewable(true)
                                              .build();
    when(NGVaultTaskHelper.getVaultAwmIamAuthLoginResult(vaultConfig)).thenReturn(loginResult);
    when(NGVaultTaskHelper.getToken(vaultConfig)).thenReturn(vaultConfig.authToken);
    VaultK8sLoginResult vaultK8sLoginResult = VaultK8sLoginResult.builder()
                                                  .clientToken(K8s_AUTH_TOKEN)
                                                  .policies(new ArrayList<>())
                                                  .accessor("accessor")
                                                  .build();
    when(NGVaultTaskHelper.getVaultK8sAuthLoginResult(vaultConfig)).thenReturn(vaultK8sLoginResult);
  }

  @After
  public void cleanup() {
    vaultRestClientFactoryMockedStatic.close();
    ngVaultTaskHelperMockedStatic.close();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateSecret() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    when(vaultRestClient.writeSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> true);
    EncryptedRecord encryptedRecord =
        hashicorpVaultEncryptor.createSecret(vaultConfig.getAccountId(), name, plainText, vaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(name.toCharArray());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testCreateSecretAwsIam() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    performAwsIamLoginResult();
    when(vaultRestClient.writeSecret(
             AWS_IAM_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> true);
    when(NGVaultTaskHelper.getToken(vaultConfig)).thenReturn(AWS_IAM_TOKEN);
    EncryptedRecord encryptedRecord =
        hashicorpVaultEncryptor.createSecret(vaultConfig.getAccountId(), name, plainText, vaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(name.toCharArray());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateSecretViaK8sAuth() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    performK8sAuthLoginResult();
    when(vaultRestClient.writeSecret(
             K8s_AUTH_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> true);
    when(NGVaultTaskHelper.getToken(vaultConfig)).thenReturn(K8s_AUTH_TOKEN);
    EncryptedRecord encryptedRecord =
        hashicorpVaultEncryptor.createSecret(vaultConfig.getAccountId(), name, plainText, vaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(name.toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateSecret_throwIOException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    when(vaultRestClient.writeSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenThrow(new IOException("Dummy error"));
    try {
      hashicorpVaultEncryptor.createSecret(vaultConfig.getAccountId(), name, plainText, vaultConfig);
      fail("Create Secret should fail");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCause()).isOfAnyClassIn(IOException.class);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateSecret_throwSecretManagementDelegateException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    when(vaultRestClient.writeSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> false);
    try {
      hashicorpVaultEncryptor.createSecret(vaultConfig.getAccountId(), name, plainText, vaultConfig);
      fail("Create Secret should fail");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).isEqualTo("After 3 tries, encryption for vault secret " + name + " failed.");
    }
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testCreateSecretAwsIam_throwSecretManagementDelegateException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    performAwsIamLoginResult();
    when(vaultRestClient.writeSecret(
             AWS_IAM_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> false);
    try {
      hashicorpVaultEncryptor.createSecret(vaultConfig.getAccountId(), name, plainText, vaultConfig);
      fail("Create Secret should fail");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).isEqualTo("After 3 tries, encryption for vault secret " + name + " failed.");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateSecretViaK8sAuth_throwSecretManagementDelegateException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    performK8sAuthLoginResult();
    when(vaultRestClient.writeSecret(
             K8s_AUTH_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> false);
    try {
      hashicorpVaultEncryptor.createSecret(vaultConfig.getAccountId(), name, plainText, vaultConfig);
      fail("Create Secret should fail");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).isEqualTo("After 3 tries, encryption for vault secret " + name + " failed.");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateSecret() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    when(vaultRestClient.writeSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> true);
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    EncryptedRecord encryptedRecord =
        hashicorpVaultEncryptor.updateSecret(vaultConfig.getAccountId(), name, plainText, oldRecord, vaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(name.toCharArray());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testUpdateSecretAwsIam() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    performAwsIamLoginResult();
    when(vaultRestClient.writeSecret(
             AWS_IAM_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> true);
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(NGVaultTaskHelper.getToken(vaultConfig)).thenReturn(AWS_IAM_TOKEN);
    EncryptedRecord encryptedRecord =
        hashicorpVaultEncryptor.updateSecret(vaultConfig.getAccountId(), name, plainText, oldRecord, vaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(name.toCharArray());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUpdateSecretViaK8sAuth() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    performK8sAuthLoginResult();
    when(vaultRestClient.writeSecret(
             K8s_AUTH_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> true);
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(NGVaultTaskHelper.getToken(vaultConfig)).thenReturn(K8s_AUTH_TOKEN);
    EncryptedRecord encryptedRecord =
        hashicorpVaultEncryptor.updateSecret(vaultConfig.getAccountId(), name, plainText, oldRecord, vaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(name.toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateSecret_throwIOException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(vaultRestClient.writeSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenThrow(new IOException("Dummy error"));
    try {
      hashicorpVaultEncryptor.updateSecret(vaultConfig.getAccountId(), name, plainText, oldRecord, vaultConfig);
      fail("Update Secret should fail");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCause()).isOfAnyClassIn(IOException.class);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateSecret_throwSecretManagementDelegateException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(vaultRestClient.writeSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> false);
    try {
      hashicorpVaultEncryptor.updateSecret(vaultConfig.getAccountId(), name, plainText, oldRecord, vaultConfig);
      fail("Update Secret should fail");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).isEqualTo("After 3 tries, encryption for vault secret " + name + " failed.");
    }
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testUpdateSecretAwsIam_throwSecretManagementDelegateException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    performAwsIamLoginResult();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(vaultRestClient.writeSecret(
             AWS_IAM_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> false);
    try {
      hashicorpVaultEncryptor.updateSecret(vaultConfig.getAccountId(), name, plainText, oldRecord, vaultConfig);
      fail("Update Secret should fail");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).isEqualTo("After 3 tries, encryption for vault secret " + name + " failed.");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUpdateSecretViaK8sAuth_throwSecretManagementDelegateException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    performK8sAuthLoginResult();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(vaultRestClient.writeSecret(
             K8s_AUTH_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> false);
    try {
      hashicorpVaultEncryptor.updateSecret(vaultConfig.getAccountId(), name, plainText, oldRecord, vaultConfig);
      fail("Update Secret should fail");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).isEqualTo("After 3 tries, encryption for vault secret " + name + " failed.");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenameSecret() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    when(vaultRestClient.writeSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> true);
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(vaultRestClient.readSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(),
             vaultConfig.getBasePath() + "/" + oldRecord.getEncryptionKey() + "#value"))
        .thenAnswer(invocationOnMock -> plainText);
    EncryptedRecord encryptedRecord =
        hashicorpVaultEncryptor.renameSecret(vaultConfig.getAccountId(), name, oldRecord, vaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(name.toCharArray());
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testRenameSecretAwsIam() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    performAwsIamLoginResult();
    when(vaultRestClient.writeSecret(
             AWS_IAM_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> true);
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(vaultRestClient.readSecret(AWS_IAM_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(),
             vaultConfig.getBasePath() + "/" + oldRecord.getEncryptionKey() + "#value"))
        .thenAnswer(invocationOnMock -> plainText);
    when(NGVaultTaskHelper.getToken(vaultConfig)).thenReturn(AWS_IAM_TOKEN);
    EncryptedRecord encryptedRecord =
        hashicorpVaultEncryptor.renameSecret(vaultConfig.getAccountId(), name, oldRecord, vaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(name.toCharArray());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testRenameSecretViaK8sAuth() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    performK8sAuthLoginResult();
    when(vaultRestClient.writeSecret(
             K8s_AUTH_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> true);
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(vaultRestClient.readSecret(K8s_AUTH_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(),
             vaultConfig.getBasePath() + "/" + oldRecord.getEncryptionKey() + "#value"))
        .thenAnswer(invocationOnMock -> plainText);
    when(NGVaultTaskHelper.getToken(vaultConfig)).thenReturn(K8s_AUTH_TOKEN);
    EncryptedRecord encryptedRecord =
        hashicorpVaultEncryptor.renameSecret(vaultConfig.getAccountId(), name, oldRecord, vaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(name.toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenameSecret_throwSecretManagementDelegateException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(vaultRestClient.readSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(), vaultConfig.getBasePath() + "/" + oldRecord.getEncryptionKey()))
        .thenAnswer(invocationOnMock -> "");
    try {
      hashicorpVaultEncryptor.renameSecret(vaultConfig.getAccountId(), name, oldRecord, vaultConfig);
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).isEqualTo("After 3 tries, encryption for vault secret " + name + " failed.");
    }
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testRenameSecretAwsIam_throwSecretManagementDelegateException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    performAwsIamLoginResult();
    when(vaultRestClient.readSecret(AWS_IAM_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(),
             vaultConfig.getBasePath() + "/" + oldRecord.getEncryptionKey()))
        .thenAnswer(invocationOnMock -> "");
    try {
      hashicorpVaultEncryptor.renameSecret(vaultConfig.getAccountId(), name, oldRecord, vaultConfig);
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).isEqualTo("After 3 tries, encryption for vault secret " + name + " failed.");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testRenameSecretViaK8sAuth_throwSecretManagementDelegateException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    performK8sAuthLoginResult();
    when(vaultRestClient.readSecret(K8s_AUTH_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(),
             vaultConfig.getBasePath() + "/" + oldRecord.getEncryptionKey() + "#value"))
        .thenAnswer(invocationOnMock -> "");
    try {
      hashicorpVaultEncryptor.renameSecret(vaultConfig.getAccountId(), name, oldRecord, vaultConfig);
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).isEqualTo("After 3 tries, encryption for vault secret " + name + " failed.");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenameSecret_throwIOException() throws IOException {
    String name = UUIDGenerator.generateUuid();
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(vaultRestClient.readSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(),
             vaultConfig.getBasePath() + "/" + oldRecord.getEncryptionKey() + "#value"))
        .thenThrow(new IOException("dummy error"));
    try {
      hashicorpVaultEncryptor.renameSecret(vaultConfig.getAccountId(), name, oldRecord, vaultConfig);
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCause()).isOfAnyClassIn(IOException.class);
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testRenameSecret_withoutDeletePermissions_shouldPass() throws IOException {
    String name = UUIDGenerator.generateUuid();
    String plainText = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + name;
    when(vaultRestClient.writeSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(), fullPath, plainText))
        .thenAnswer(invocationOnMock -> true);
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .build();
    when(vaultRestClient.readSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(),
             vaultConfig.getBasePath() + "/" + oldRecord.getEncryptionKey() + "#value"))
        .thenAnswer(invocationOnMock -> plainText);
    when(vaultRestClient.deleteSecretPermanentely(
             vaultConfig.getAuthToken(), vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath))
        .thenThrow(new HashiCorpVaultRuntimeException("error: Permission denied"));
    EncryptedRecord encryptedRecord =
        hashicorpVaultEncryptor.renameSecret(vaultConfig.getAccountId(), name, oldRecord, vaultConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(name);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(name.toCharArray());
    verify(vaultRestClient, times(1)).deleteSecretPermanentely(any(), eq(vaultConfig.getNamespace()), any(), any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSecret_withDeletePermissions_shouldPass() throws IOException {
    String encryptionKey = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + encryptionKey;
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(encryptionKey)
                                    .build();
    when(vaultRestClient.deleteSecretPermanentely(
             vaultConfig.getAuthToken(), vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath))
        .thenReturn(true);
    boolean deleted = hashicorpVaultEncryptor.deleteSecret(vaultConfig.getAccountId(), oldRecord, vaultConfig);
    assertThat(deleted).isEqualTo(true);
    verify(vaultRestClient, times(1)).deleteSecretPermanentely(any(), eq(vaultConfig.getNamespace()), any(), any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSecret_withoutDeletePermissions_shouldPass() throws IOException {
    String encryptionKey = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + encryptionKey;
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(encryptionKey)
                                    .build();
    when(vaultRestClient.deleteSecretPermanentely(
             vaultConfig.getAuthToken(), vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath))
        .thenThrow(new HashiCorpVaultRuntimeException("error: Permission denied"));
    assertThatThrownBy(() -> hashicorpVaultEncryptor.deleteSecret(vaultConfig.getAccountId(), oldRecord, vaultConfig))
        .isInstanceOf(HashiCorpVaultRuntimeException.class);
    verify(vaultRestClient, times(1)).deleteSecretPermanentely(any(), eq(vaultConfig.getNamespace()), any(), any());
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void testDeleteSecretShouldThrowHashicorpException() throws IOException {
    String encryptionKey = UUIDGenerator.generateUuid();
    String fullPath = vaultConfig.getBasePath() + "/" + encryptionKey;
    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .encryptionKey(encryptionKey)
                                    .build();
    when(vaultRestClient.deleteSecretPermanentely(
             vaultConfig.getAuthToken(), vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), fullPath))
        .thenThrow(new HashiCorpVaultRuntimeException("error: Permission denied"));
    assertThatThrownBy(() -> hashicorpVaultEncryptor.deleteSecret(vaultConfig.getAccountId(), oldRecord, vaultConfig))
        .isInstanceOf(HashiCorpVaultRuntimeException.class);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testFetchSecretV2() throws IOException {
    Map<String, Object> data = Map.of("key.with.dot", "value-for-key-with-dot");

    EncryptedRecord encryptedRecord = setupJsonResponseMockingV2(data, "key.with.dot");
    char[] value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value)).isEqualTo("value-for-key-with-dot");

    data = Map.of("key-1", "value-1", "key-2", Map.of("key-21", "value-21"), "key-3",
        Map.of("key-31", Map.of("key-311", "value-311")));

    encryptedRecord = setupJsonResponseMockingV2(data, "key-1");
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value)).isEqualTo("value-1");

    encryptedRecord = setupJsonResponseMockingV2(data, "key-2");
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(valueOf(value)).isEqualTo("{\"key-21\":\"value-21\"}");
    assertThat(valueOf(value)).isEqualTo(objectMapper.writeValueAsString(data.get("key-2")));

    encryptedRecord = setupJsonResponseMockingV2(data, EMPTY);
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value)).isEqualTo(objectMapper.writeValueAsString(data));

    encryptedRecord = setupJsonResponseMockingV2(data, "key-3");
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value)).isEqualTo(objectMapper.writeValueAsString(data.get("key-3")));

    encryptedRecord = setupJsonResponseMockingV2(data, "key-3.key-31");
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value))
        .isEqualTo(objectMapper.writeValueAsString(((Map<String, Object>) data.get("key-3")).get("key-31")));

    encryptedRecord = setupJsonResponseMockingV2(data, "key-3.key-31.key-311");
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value)).isEqualTo("value-311");

    encryptedRecord = setupJsonResponseMockingV2(Map.of("list-key", List.of("value1", "value2", "value3")), "list-key");
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value)).isEqualTo("[\"value1\",\"value2\",\"value3\"]");
  }

  @Test(expected = SecretManagementDelegateException.class)
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testFetchSecretV2_throwsExceptionForInvalidKey() throws IOException {
    Map<String, Object> data = Map.of("key-1", "value-1", "key-2", Map.of("key-21", "value-21"), "key-3",
        Map.of("key-31", Map.of("key-311", "value-311")));

    EncryptedRecord encryptedRecord = setupJsonResponseMockingV2(data, "invalidKey");
    hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
  }

  private EncryptedRecord setupJsonResponseMockingV2(Map<String, Object> responseMap, String key) throws IOException {
    Call<VaultReadResponseV2> call = mock(Call.class);
    VaultRestClientV2 vaultRestClientV2 = mock(VaultRestClientV2.class);
    V2Impl vaultRestClientV2Impl = new V2Impl(vaultRestClientV2);
    when(VaultRestClientFactory.create(vaultConfig)).thenReturn(vaultRestClientV2Impl);

    VaultReadResponseV2 vaultReadResponseV2 =
        VaultReadResponseV2.builder().data(VaultSecretValue.builder().data(responseMap).build()).build();
    String path = "harness";
    String fullPath = path;
    if (key != null) {
      fullPath = fullPath + "#" + key;
    }
    VaultPathAndKey pathAndKey = VaultPathAndKey.builder().path(path).keyName(key).build();
    when(VaultRestClientFactory.parseFullPath(fullPath)).thenAnswer(answer -> pathAndKey);
    when(VaultRestClientFactory.parseFullPath(fullPath, EMPTY)).thenAnswer(answer -> pathAndKey);
    when(call.execute()).thenReturn(Response.success(vaultReadResponseV2));
    when(vaultRestClientV2.readSecret(
             vaultConfig.getAuthToken(), vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), path))
        .thenAnswer(answer -> call);
    return EncryptedRecordData.builder().path(fullPath).build();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testFetchSecretV1() throws IOException {
    Map<String, Object> data = Map.of("key-1", "value-1", "key-2", Map.of("key-21", "value-21"), "key-3",
        Map.of("key-31", Map.of("key-311", "value-311")));

    EncryptedRecord encryptedRecord = setupJsonResponseMockingV1(data, "key-1");
    char[] value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value)).isEqualTo("value-1");

    encryptedRecord = setupJsonResponseMockingV1(data, "key-2");
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    ObjectMapper objectMapper = new ObjectMapper();
    assertThat(valueOf(value)).isEqualTo("{\"key-21\":\"value-21\"}");
    assertThat(valueOf(value)).isEqualTo(objectMapper.writeValueAsString(data.get("key-2")));

    encryptedRecord = setupJsonResponseMockingV1(data, EMPTY);
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value)).isEqualTo(objectMapper.writeValueAsString(data));

    encryptedRecord = setupJsonResponseMockingV1(data, "key-3");
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value)).isEqualTo(objectMapper.writeValueAsString(data.get("key-3")));

    encryptedRecord = setupJsonResponseMockingV1(data, "key-3.key-31");
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value))
        .isEqualTo(objectMapper.writeValueAsString(((Map<String, Object>) data.get("key-3")).get("key-31")));

    encryptedRecord = setupJsonResponseMockingV1(data, "key-3.key-31.key-311");
    value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
    assertThat(valueOf(value)).isEqualTo("value-311");
  }

  @Test(expected = SecretManagementDelegateException.class)
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testFetchSecretV1_throwsExceptionForInvalidKey() throws IOException {
    Map<String, Object> data = Map.of("key-1", "value-1", "key-2", Map.of("key-21", "value-21"), "key-3",
        Map.of("key-31", Map.of("key-311", "value-311")));

    EncryptedRecord encryptedRecord = setupJsonResponseMockingV1(data, "invalidKey");
    hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), encryptedRecord, vaultConfig);
  }

  private EncryptedRecord setupJsonResponseMockingV1(Map<String, Object> responseMap, String key) throws IOException {
    Call<VaultReadResponse> call = mock(Call.class);
    VaultRestClientV1 vaultRestClientV1 = mock(VaultRestClientV1.class);
    V1Impl vaultRestClientV1Impl = new V1Impl(vaultRestClientV1);
    when(VaultRestClientFactory.create(vaultConfig)).thenReturn(vaultRestClientV1Impl);

    VaultReadResponse vaultReadResponse = VaultReadResponse.builder().data(responseMap).build();
    String path = "harness";
    String fullPath = path;
    if (key != null) {
      fullPath = fullPath + "#" + key;
    }
    VaultPathAndKey pathAndKey = VaultPathAndKey.builder().path(path).keyName(key).build();
    when(VaultRestClientFactory.parseFullPath(fullPath)).thenAnswer(answer -> pathAndKey);
    when(VaultRestClientFactory.parseFullPath(fullPath, EMPTY)).thenAnswer(answer -> pathAndKey);
    when(call.execute()).thenReturn(Response.success(vaultReadResponse));
    when(vaultRestClientV1.readSecret(
             vaultConfig.getAuthToken(), vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), path))
        .thenAnswer(answer -> call);
    return EncryptedRecordData.builder().path(fullPath).build();
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testFetchSecretAwsIam() throws IOException {
    performAwsIamLoginResult();
    String plainText = "plainText";
    EncryptedRecord record =
        EncryptedRecordData.builder().path(UUIDGenerator.generateUuid() + "#" + UUIDGenerator.generateUuid()).build();
    when(vaultRestClient.readSecret(
             AWS_IAM_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), record.getPath()))
        .thenAnswer(invocationOnMock -> plainText);
    when(NGVaultTaskHelper.getToken(vaultConfig)).thenReturn(AWS_IAM_TOKEN);
    char[] value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), record, vaultConfig);
    assertThat(value).isEqualTo(plainText.toCharArray());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testFetchSecretViaK8sAuth() throws IOException {
    performK8sAuthLoginResult();
    String plainText = "plainText";
    EncryptedRecord record =
        EncryptedRecordData.builder().path(UUIDGenerator.generateUuid() + "#" + UUIDGenerator.generateUuid()).build();
    when(vaultRestClient.readSecret(
             K8s_AUTH_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(), record.getPath()))
        .thenAnswer(invocationOnMock -> plainText);
    when(NGVaultTaskHelper.getToken(vaultConfig)).thenReturn(K8s_AUTH_TOKEN);
    char[] value = hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), record, vaultConfig);
    assertThat(value).isEqualTo(plainText.toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testFetchSecret_throwIOException() throws IOException {
    EncryptedRecord record = EncryptedRecordData.builder()
                                 .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                 .encryptionKey(UUIDGenerator.generateUuid())
                                 .build();
    when(vaultRestClient.readSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(), vaultConfig.getBasePath() + "/" + record.getEncryptionKey() + "#value"))
        .thenThrow(new IOException("dummy error"));
    try {
      hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), record, vaultConfig);
      fail("Fetch secret should throw exception");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCause()).isOfAnyClassIn(IOException.class);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testFetchSecret_throwSecretMangementDelegateException() throws IOException {
    EncryptedRecord record = EncryptedRecordData.builder()
                                 .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                 .encryptionKey(UUIDGenerator.generateUuid())
                                 .build();
    when(vaultRestClient.readSecret(vaultConfig.getAuthToken(), vaultConfig.getNamespace(),
             vaultConfig.getSecretEngineName(), vaultConfig.getBasePath() + "/" + record.getEncryptionKey()))
        .thenAnswer(invocationOnMock -> "");
    try {
      hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), record, vaultConfig);
      fail("Fetch secret should throw exception");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage())
          .isEqualTo("Decryption failed after 3 retries for secret " + record.getEncryptionKey() + " or path null");
    }
  }

  @Test
  @Owner(developers = SHASHANK)
  @Category(UnitTests.class)
  public void testFetchSecretAwsIam_throwSecretMangementDelegateException() throws IOException {
    EncryptedRecord record = EncryptedRecordData.builder()
                                 .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                 .encryptionKey(UUIDGenerator.generateUuid())
                                 .build();
    performAwsIamLoginResult();
    when(vaultRestClient.readSecret(AWS_IAM_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(),
             vaultConfig.getBasePath() + "/" + record.getEncryptionKey()))
        .thenAnswer(invocationOnMock -> "");
    try {
      hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), record, vaultConfig);
      fail("Fetch secret should throw exception");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage())
          .isEqualTo("Decryption failed after 3 retries for secret " + record.getEncryptionKey() + " or path null");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testFetchSecretViaK8sAuth_throwSecretManagementDelegateException() throws IOException {
    EncryptedRecord record = EncryptedRecordData.builder()
                                 .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                 .encryptionKey(UUIDGenerator.generateUuid())
                                 .build();
    performK8sAuthLoginResult();
    when(vaultRestClient.readSecret(K8s_AUTH_TOKEN, vaultConfig.getNamespace(), vaultConfig.getSecretEngineName(),
             vaultConfig.getBasePath() + "/" + record.getEncryptionKey()))
        .thenAnswer(invocationOnMock -> "");
    try {
      hashicorpVaultEncryptor.fetchSecretValue(vaultConfig.getAccountId(), record, vaultConfig);
      fail("Fetch secret should throw exception");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage())
          .isEqualTo("Decryption failed after 3 retries for secret " + record.getEncryptionKey() + " or path null");
    }
  }

  @NotNull
  private void performAwsIamLoginResult() {
    vaultConfig.setUseAwsIam(true);
    vaultConfig.setVaultAwsIamRole("dev");
    vaultConfig.setXVaultAwsIamServerId("header");
  }

  @NotNull
  private void performK8sAuthLoginResult() {
    vaultConfig.setUseK8sAuth(true);
    vaultConfig.setVaultK8sAuthRole("role");
    vaultConfig.setServiceAccountTokenPath("serviceAccountTokenPath");
  }
}
