/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.rule.Owner;
import io.harness.security.encryption.common.EncryptedRecord;
import io.harness.security.encryption.common.EncryptionConfig;
import io.harness.security.encryption.common.EncryptionType;
import io.harness.security.encryption.common.SecretManagerType;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FetchSecretTaskTest extends CategoryTest {
  private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  private KmsEncryptorsRegistry kmsEncryptorsRegistry;
  private CustomEncryptorsRegistry customEncryptorsRegistry;
  private FetchSecretTaskParameters fetchSecretTaskParameters;
  private FetchSecretTask fetchSecretTask;

  @Before
  public void setup() throws IllegalAccessException {
    EncryptedRecord encryptedRecord = mock(EncryptedRecord.class);
    EncryptionConfig encryptionConfig = mock(EncryptionConfig.class);
    fetchSecretTaskParameters =
        FetchSecretTaskParameters.builder().encryptedRecord(encryptedRecord).encryptionConfig(encryptionConfig).build();
    DelegateTaskPackage delegateTaskPackage = DelegateTaskPackage.builder()
                                                  .delegateId(UUIDGenerator.generateUuid())
                                                  .accountId(UUIDGenerator.generateUuid())
                                                  .data(TaskData.builder()
                                                            .async(false)
                                                            .parameters(new Object[] {fetchSecretTaskParameters})
                                                            .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                                                            .build())
                                                  .build();

    vaultEncryptorsRegistry = mock(VaultEncryptorsRegistry.class);
    kmsEncryptorsRegistry = mock(KmsEncryptorsRegistry.class);
    customEncryptorsRegistry = mock(CustomEncryptorsRegistry.class);
    fetchSecretTask = new FetchSecretTask(delegateTaskPackage, null, notifyResponseData -> {}, () -> true);
    FieldUtils.writeField(fetchSecretTask, "vaultEncryptorsRegistry", vaultEncryptorsRegistry, true);
    FieldUtils.writeField(fetchSecretTask, "kmsEncryptorsRegistry", kmsEncryptorsRegistry, true);
    FieldUtils.writeField(fetchSecretTask, "customEncryptorsRegistry", customEncryptorsRegistry, true);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunKmsTask() {
    KmsEncryptor kmsEncryptor = mock(KmsEncryptor.class);
    String accountId = UUIDGenerator.generateUuid();
    String value = UUIDGenerator.generateUuid();
    when(fetchSecretTaskParameters.getEncryptionConfig().getAccountId()).thenReturn(accountId);
    when(fetchSecretTaskParameters.getEncryptionConfig().getType()).thenReturn(SecretManagerType.KMS);
    when(kmsEncryptorsRegistry.getKmsEncryptor(fetchSecretTaskParameters.getEncryptionConfig()))
        .thenReturn(kmsEncryptor);
    when(kmsEncryptor.fetchSecretValue(accountId, fetchSecretTaskParameters.getEncryptedRecord(),
             fetchSecretTaskParameters.getEncryptionConfig()))
        .thenReturn(value.toCharArray());
    FetchSecretTaskResponse fetchSecretTaskResponse =
        (FetchSecretTaskResponse) fetchSecretTask.run(fetchSecretTaskParameters);
    assertThat(fetchSecretTaskResponse).isNotNull();
    assertThat(fetchSecretTaskResponse.getSecretValue()).isEqualTo(value.toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunVaultTask() {
    VaultEncryptor vaultEncryptor = mock(VaultEncryptor.class);
    String accountId = UUIDGenerator.generateUuid();
    String value = UUIDGenerator.generateUuid();
    EncryptionType encryptionType = EncryptionType.VAULT;
    when(fetchSecretTaskParameters.getEncryptionConfig().getEncryptionType()).thenReturn(encryptionType);
    when(fetchSecretTaskParameters.getEncryptionConfig().getAccountId()).thenReturn(accountId);
    when(fetchSecretTaskParameters.getEncryptionConfig().getType()).thenReturn(SecretManagerType.VAULT);
    when(vaultEncryptorsRegistry.getVaultEncryptor(encryptionType)).thenReturn(vaultEncryptor);
    when(vaultEncryptor.fetchSecretValue(accountId, fetchSecretTaskParameters.getEncryptedRecord(),
             fetchSecretTaskParameters.getEncryptionConfig()))
        .thenReturn(value.toCharArray());
    FetchSecretTaskResponse fetchSecretTaskResponse =
        (FetchSecretTaskResponse) fetchSecretTask.run(fetchSecretTaskParameters);
    assertThat(fetchSecretTaskResponse).isNotNull();
    assertThat(fetchSecretTaskResponse.getSecretValue()).isEqualTo(value.toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunCustomTask() {
    CustomEncryptor customEncryptor = mock(CustomEncryptor.class);
    String accountId = UUIDGenerator.generateUuid();
    String value = UUIDGenerator.generateUuid();
    EncryptionType encryptionType = EncryptionType.CUSTOM;
    when(fetchSecretTaskParameters.getEncryptionConfig().getEncryptionType()).thenReturn(encryptionType);
    when(fetchSecretTaskParameters.getEncryptionConfig().getAccountId()).thenReturn(accountId);
    when(fetchSecretTaskParameters.getEncryptionConfig().getType()).thenReturn(SecretManagerType.CUSTOM);
    when(customEncryptorsRegistry.getCustomEncryptor(encryptionType)).thenReturn(customEncryptor);
    when(customEncryptor.fetchSecretValue(accountId, fetchSecretTaskParameters.getEncryptedRecord(),
             fetchSecretTaskParameters.getEncryptionConfig()))
        .thenReturn(value.toCharArray());
    FetchSecretTaskResponse fetchSecretTaskResponse =
        (FetchSecretTaskResponse) fetchSecretTask.run(fetchSecretTaskParameters);
    assertThat(fetchSecretTaskResponse).isNotNull();
    assertThat(fetchSecretTaskResponse.getSecretValue()).isEqualTo(value.toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunTask_throwError() {
    when(fetchSecretTaskParameters.getEncryptionConfig().getType()).thenReturn(null);
    try {
      fetchSecretTask.run(fetchSecretTaskParameters);
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Encryptor for fetch secret task for encryption config null not configured");
    }
  }
}
