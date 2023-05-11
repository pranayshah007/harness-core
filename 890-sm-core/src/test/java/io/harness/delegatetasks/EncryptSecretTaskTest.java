/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EncryptSecretTaskTest extends CategoryTest {
  private EncryptSecretTask encryptSecretTask;
  private EncryptSecretTaskParameters encryptSecretTaskParameters;
  private KmsEncryptor kmsEncryptor;
  private EncryptionConfig encryptionConfig;
  private EncryptedRecord encryptedRecord;
  private String accountId;
  private String plaintext;

  @Before
  public void setup() throws IllegalAccessException {
    encryptSecretTaskParameters = mock(EncryptSecretTaskParameters.class);
    DelegateTaskPackage delegateTaskPackage = DelegateTaskPackage.builder()
                                                  .delegateId(UUIDGenerator.generateUuid())
                                                  .accountId(UUIDGenerator.generateUuid())
                                                  .data(TaskData.builder()
                                                            .async(false)
                                                            .parameters(new Object[] {encryptSecretTaskParameters})
                                                            .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                                                            .build())
                                                  .build();
    encryptSecretTask = new EncryptSecretTask(delegateTaskPackage, null, notifyResponseData -> {}, () -> true);

    KmsEncryptorsRegistry kmsEncryptorsRegistry = mock(KmsEncryptorsRegistry.class);
    kmsEncryptor = mock(KmsEncryptor.class);
    when(kmsEncryptorsRegistry.getKmsEncryptor(any())).thenReturn(kmsEncryptor);
    FieldUtils.writeField(encryptSecretTask, "kmsEncryptorsRegistry", kmsEncryptorsRegistry, true);

    encryptedRecord = mock(EncryptedRecord.class);
    encryptionConfig = mock(EncryptionConfig.class);
    accountId = UUIDGenerator.generateUuid();
    plaintext = UUIDGenerator.generateUuid();
    when(encryptionConfig.getAccountId()).thenReturn(accountId);
    when(encryptSecretTaskParameters.getValue()).thenReturn(plaintext);
    when(encryptSecretTaskParameters.getEncryptionConfig()).thenReturn(encryptionConfig);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunEncryptTask() {
    when(kmsEncryptor.encryptSecret(accountId, plaintext, encryptionConfig)).thenReturn(encryptedRecord);
    EncryptSecretTaskResponse encryptSecretTaskResponse =
        (EncryptSecretTaskResponse) encryptSecretTask.run(encryptSecretTaskParameters);
    assertThat(encryptSecretTaskResponse).isNotNull();
    assertThat(encryptSecretTaskResponse.getEncryptedRecord()).isEqualTo(encryptedRecord);
  }
}
