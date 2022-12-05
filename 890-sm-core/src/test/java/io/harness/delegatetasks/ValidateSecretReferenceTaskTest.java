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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.SecretText;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.CustomEncryptorsRegistry;
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

public class ValidateSecretReferenceTaskTest extends CategoryTest {
  private VaultEncryptorsRegistry vaultEncryptorsRegistry;
  private CustomEncryptorsRegistry customEncryptorsRegistry;
  private ValidateSecretReferenceTaskParameters validateSecretReferenceTaskParameters;
  private ValidateSecretReferenceTask validateSecretReferenceTask;

  @Before
  public void setup() throws IllegalAccessException {
    EncryptedRecord encryptedRecord = mock(EncryptedRecord.class);
    EncryptionConfig encryptionConfig = mock(EncryptionConfig.class);
    validateSecretReferenceTaskParameters = ValidateSecretReferenceTaskParameters.builder()
                                                .encryptedRecord(encryptedRecord)
                                                .encryptionConfig(encryptionConfig)
                                                .build();
    DelegateTaskPackage delegateTaskPackage =
        DelegateTaskPackage.builder()
            .delegateId(UUIDGenerator.generateUuid())
            .accountId(UUIDGenerator.generateUuid())
            .data(TaskData.builder()
                      .async(false)
                      .parameters(new Object[] {validateSecretReferenceTaskParameters})
                      .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                      .build())
            .build();

    vaultEncryptorsRegistry = mock(VaultEncryptorsRegistry.class);
    customEncryptorsRegistry = mock(CustomEncryptorsRegistry.class);
    validateSecretReferenceTask =
        new ValidateSecretReferenceTask(delegateTaskPackage, null, notifyResponseData -> {}, () -> true);
    FieldUtils.writeField(validateSecretReferenceTask, "vaultEncryptorsRegistry", vaultEncryptorsRegistry, true);
    FieldUtils.writeField(validateSecretReferenceTask, "customEncryptorsRegistry", customEncryptorsRegistry, true);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunVaultTask() {
    VaultEncryptor vaultEncryptor = mock(VaultEncryptor.class);
    String accountId = UUIDGenerator.generateUuid();
    EncryptionType encryptionType = EncryptionType.VAULT;
    when(validateSecretReferenceTaskParameters.getEncryptionConfig().getEncryptionType()).thenReturn(encryptionType);
    when(validateSecretReferenceTaskParameters.getEncryptionConfig().getAccountId()).thenReturn(accountId);
    when(validateSecretReferenceTaskParameters.getEncryptionConfig().getType()).thenReturn(SecretManagerType.VAULT);
    when(vaultEncryptorsRegistry.getVaultEncryptor(encryptionType)).thenReturn(vaultEncryptor);
    when(vaultEncryptor.validateReference(accountId,
             SecretText.builder()
                 .path(validateSecretReferenceTaskParameters.getEncryptedRecord().getPath())
                 .name(validateSecretReferenceTaskParameters.getEncryptedRecord().getName())
                 .build(),
             validateSecretReferenceTaskParameters.getEncryptionConfig()))
        .thenReturn(true);
    ValidateSecretReferenceTaskResponse validateSecretReferenceTaskResponse =
        (ValidateSecretReferenceTaskResponse) validateSecretReferenceTask.run(validateSecretReferenceTaskParameters);
    assertThat(validateSecretReferenceTaskResponse).isNotNull();
    assertThat(validateSecretReferenceTaskResponse.isReferenceValid()).isEqualTo(true);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunCustomTask() {
    CustomEncryptor customEncryptor = mock(CustomEncryptor.class);
    String accountId = UUIDGenerator.generateUuid();
    EncryptionType encryptionType = EncryptionType.CUSTOM;
    when(validateSecretReferenceTaskParameters.getEncryptionConfig().getEncryptionType()).thenReturn(encryptionType);
    when(validateSecretReferenceTaskParameters.getEncryptionConfig().getAccountId()).thenReturn(accountId);
    when(validateSecretReferenceTaskParameters.getEncryptionConfig().getType()).thenReturn(SecretManagerType.CUSTOM);
    when(customEncryptorsRegistry.getCustomEncryptor(encryptionType)).thenReturn(customEncryptor);
    when(customEncryptor.validateReference(accountId,
             validateSecretReferenceTaskParameters.getEncryptedRecord().getParameters(),
             validateSecretReferenceTaskParameters.getEncryptionConfig()))
        .thenReturn(true);
    ValidateSecretReferenceTaskResponse fetchSecretTaskResponse =
        (ValidateSecretReferenceTaskResponse) validateSecretReferenceTask.run(validateSecretReferenceTaskParameters);
    assertThat(fetchSecretTaskResponse).isNotNull();
    assertThat(fetchSecretTaskResponse.isReferenceValid()).isEqualTo(true);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunTask_throwError() {
    when(validateSecretReferenceTaskParameters.getEncryptionConfig().getType()).thenReturn(null);
    try {
      validateSecretReferenceTask.run(validateSecretReferenceTaskParameters);
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getCode()).isEqualTo(SECRET_MANAGEMENT_ERROR);
      assertThat(e.getMessage())
          .isEqualTo("Encryptor for validate reference task for encryption config null not configured");
    }
  }
}
