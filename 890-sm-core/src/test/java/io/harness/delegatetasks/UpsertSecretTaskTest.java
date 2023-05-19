/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class UpsertSecretTaskTest extends CategoryTest {
  private UpsertSecretTask upsertSecretTask;
  private UpsertSecretTaskParameters upsertSecretTaskParameters;
  private VaultEncryptor vaultEncryptor;
  private EncryptionConfig encryptionConfig;
  private EncryptedRecord encryptedRecord;
  private String accountId;
  private String name;
  private String plaintext;

  @Before
  public void setup() throws IllegalAccessException {
    upsertSecretTaskParameters = mock(UpsertSecretTaskParameters.class);
    DelegateTaskPackage delegateTaskPackage = DelegateTaskPackage.builder()
                                                  .delegateId(UUIDGenerator.generateUuid())
                                                  .accountId(UUIDGenerator.generateUuid())
                                                  .data(TaskData.builder()
                                                            .async(false)
                                                            .parameters(new Object[] {upsertSecretTaskParameters})
                                                            .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                                                            .build())
                                                  .build();
    upsertSecretTask = new UpsertSecretTask(delegateTaskPackage, null, notifyResponseData -> {}, () -> true);

    VaultEncryptorsRegistry vaultEncryptorsRegistry = mock(VaultEncryptorsRegistry.class);
    vaultEncryptor = mock(VaultEncryptor.class);
    when(vaultEncryptorsRegistry.getVaultEncryptor(any())).thenReturn(vaultEncryptor);
    FieldUtils.writeField(upsertSecretTask, "vaultEncryptorsRegistry", vaultEncryptorsRegistry, true);

    encryptedRecord = mock(EncryptedRecord.class);
    encryptionConfig = mock(EncryptionConfig.class);
    accountId = UUIDGenerator.generateUuid();
    plaintext = UUIDGenerator.generateUuid();
    name = UUIDGenerator.generateUuid();
    when(encryptionConfig.getAccountId()).thenReturn(accountId);
    when(upsertSecretTaskParameters.getEncryptionConfig()).thenReturn(encryptionConfig);
    when(upsertSecretTaskParameters.getExistingRecord()).thenReturn(encryptedRecord);
    when(upsertSecretTaskParameters.getPlaintext()).thenReturn(plaintext);
    when(upsertSecretTaskParameters.getName()).thenReturn(name);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunUpdateTask() {
    when(upsertSecretTaskParameters.getTaskType()).thenReturn(UpsertSecretTaskType.UPDATE);
    when(vaultEncryptor.updateSecret(
             accountId, SecretText.builder().name(name).value(plaintext).build(), encryptedRecord, encryptionConfig))
        .thenReturn(encryptedRecord);
    UpsertSecretTaskResponse upsertSecretTaskResponse =
        (UpsertSecretTaskResponse) upsertSecretTask.run(upsertSecretTaskParameters);
    assertThat(upsertSecretTaskResponse).isNotNull();
    assertThat(upsertSecretTaskResponse.getEncryptedRecord()).isEqualTo(encryptedRecord);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunCreateTask() {
    when(upsertSecretTaskParameters.getTaskType()).thenReturn(UpsertSecretTaskType.CREATE);
    when(vaultEncryptor.createSecret(
             accountId, SecretText.builder().name(name).value(plaintext).build(), encryptionConfig))
        .thenReturn(encryptedRecord);
    UpsertSecretTaskResponse upsertSecretTaskResponse =
        (UpsertSecretTaskResponse) upsertSecretTask.run(upsertSecretTaskParameters);
    assertThat(upsertSecretTaskResponse).isNotNull();
    assertThat(upsertSecretTaskResponse.getEncryptedRecord()).isEqualTo(encryptedRecord);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunRenameTask() {
    when(upsertSecretTaskParameters.getTaskType()).thenReturn(UpsertSecretTaskType.RENAME);
    when(vaultEncryptor.renameSecret(accountId, name, encryptedRecord, encryptionConfig)).thenReturn(encryptedRecord);
    UpsertSecretTaskResponse upsertSecretTaskResponse =
        (UpsertSecretTaskResponse) upsertSecretTask.run(upsertSecretTaskParameters);
    assertThat(upsertSecretTaskResponse).isNotNull();
    assertThat(upsertSecretTaskResponse.getEncryptedRecord()).isEqualTo(encryptedRecord);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunTask_throwError() {
    when(upsertSecretTaskParameters.getTaskType()).thenReturn(null);
    try {
      upsertSecretTask.run(upsertSecretTaskParameters);
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage()).isEqualTo("Unknown upsert secret task type");
    }
  }
}
