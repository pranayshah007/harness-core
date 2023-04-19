/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service;

import static io.harness.filesystem.FileIo.acquireLock;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.RAGHAV_MURALI;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.EncryptedData;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.TaskData;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.rule.Owner;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.KmsConfig;

import com.google.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import retrofit2.Call;

public class DelegateAgentServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private DelegateAgentManagerClient delegateAgentManagerClient;
  @Mock private Call<DelegateTaskPackage> delegatePackageCall;
  @Mock private DelegateDecryptionService delegateDecryptionService;

  @InjectMocks @Inject DelegateAgentServiceImpl delegateService;
  private final AtomicBoolean executingProfile = Mockito.mock(AtomicBoolean.class);

  @Before
  public void setUp() {
    when(delegateAgentManagerClient.acquireTask(ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
             ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
        .thenReturn(delegatePackageCall);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotApplyFunctorIfNoSecrets() {
    String delegateTaskId = UUIDGenerator.generateUuid();
    String accountId = UUIDGenerator.generateUuid();

    DelegateTaskPackage delegateTaskPackage = DelegateTaskPackage.builder()
                                                  .accountId(accountId)
                                                  .delegateTaskId(delegateTaskId)
                                                  .data(TaskData.builder().async(true).taskType("HTTP").build())
                                                  .build();

    delegateService.applyDelegateSecretFunctor(delegateTaskPackage);
    verify(delegateDecryptionService, times(0)).decrypt(any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldApplyFunctorForSecrets() {
    String delegateTaskId = UUIDGenerator.generateUuid();
    String accountId = UUIDGenerator.generateUuid();

    Map<String, EncryptionConfig> encryptionConfigMap = new HashMap<>();
    KmsConfig kmsConfig = KmsConfig.builder().build();
    kmsConfig.setUuid("KMS_CONFIG_UUID");
    encryptionConfigMap.put("KMS_CONFIG_UUID", kmsConfig);

    Map<String, SecretDetail> secretDetails = new HashMap<>();
    SecretDetail secretDetail =
        SecretDetail.builder()
            .configUuid("KMS_CONFIG_UUID")
            .encryptedRecord(EncryptedData.builder().uuid("ENC_UUID").accountId("ACCOUNT_ID").build())
            .build();

    secretDetails.put("SECRET_UUID", secretDetail);

    DelegateTaskPackage delegateTaskPackage = DelegateTaskPackage.builder()
                                                  .accountId(accountId)
                                                  .delegateTaskId(delegateTaskId)
                                                  .data(TaskData.builder().async(true).taskType("HTTP").build())
                                                  .encryptionConfigs(encryptionConfigMap)
                                                  .secretDetails(secretDetails)
                                                  .build();

    Map<String, char[]> decryptedRecords = new HashMap<>();
    decryptedRecords.put("ENC_UUID", "test".toCharArray());
    when(delegateDecryptionService.decrypt(any())).thenReturn(decryptedRecords);

    delegateService.applyDelegateSecretFunctor(delegateTaskPackage);
    verify(delegateDecryptionService, times(1)).decrypt(any());
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void applyDelegateFunctorForSecretsThrowsException() {
    String delegateTaskId = UUIDGenerator.generateUuid();
    String accountId = UUIDGenerator.generateUuid();

    Map<String, EncryptionConfig> encryptionConfigMap = new HashMap<>();
    KmsConfig kmsConfig = KmsConfig.builder().build();
    kmsConfig.setUuid("KMS_CONFIG_UUID");
    encryptionConfigMap.put("KMS_CONFIG_UUID", kmsConfig);

    Map<String, SecretDetail> secretDetails = new HashMap<>();
    SecretDetail secretDetail = SecretDetail.builder().configUuid("KMS_CONFIG_UUID").encryptedRecord(null).build();

    secretDetails.put("SECRET_UUID", secretDetail);

    DelegateTaskPackage delegateTaskPackage = DelegateTaskPackage.builder()
                                                  .accountId(accountId)
                                                  .delegateTaskId(delegateTaskId)
                                                  .data(TaskData.builder().async(true).taskType("HTTP").build())
                                                  .encryptionConfigs(encryptionConfigMap)
                                                  .secretDetails(secretDetails)
                                                  .build();

    Map<String, char[]> decryptedRecords = new HashMap<>();
    decryptedRecords.put("ENC_UUID", "test".toCharArray());
    when(delegateDecryptionService.decrypt(any())).thenReturn(decryptedRecords);

    assertThatThrownBy(() -> delegateService.applyDelegateSecretFunctor(delegateTaskPackage))
        .isInstanceOf(NullPointerException.class)
        .hasStackTraceContaining("applyDelegateSecretFunctor");
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testReleaseProfileLock() {
    File profileFile = new File("profile");
    acquireLock(profileFile, ofMinutes(1));
    File lockFile = new File(profileFile.getPath() + ".lock");
    assertThat(lockFile).exists();
    delegateService.checkForProfile();
    assertThat(lockFile).doesNotExist();
  }
}
