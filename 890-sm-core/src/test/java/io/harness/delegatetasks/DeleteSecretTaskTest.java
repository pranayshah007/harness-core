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
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DeleteSecretTaskTest extends CategoryTest {
  private DeleteSecretTask deleteSecretTask;
  private DeleteSecretTaskParameters deleteSecretTaskParameters;
  private VaultEncryptor vaultEncryptor;
  private EncryptionConfig encryptionConfig;
  private EncryptedRecord encryptedRecord;
  private String accountId;

  @Before
  public void setup() throws IllegalAccessException {
    deleteSecretTaskParameters = mock(DeleteSecretTaskParameters.class);
    DelegateTaskPackage delegateTaskPackage = DelegateTaskPackage.builder()
                                                  .delegateId(UUIDGenerator.generateUuid())
                                                  .accountId(UUIDGenerator.generateUuid())
                                                  .data(TaskData.builder()
                                                            .async(false)
                                                            .parameters(new Object[] {deleteSecretTaskParameters})
                                                            .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                                                            .build())
                                                  .build();
    deleteSecretTask = new DeleteSecretTask(delegateTaskPackage, null, notifyResponseData -> {}, () -> true);

    VaultEncryptorsRegistry vaultEncryptorsRegistry = mock(VaultEncryptorsRegistry.class);
    vaultEncryptor = mock(VaultEncryptor.class);
    when(vaultEncryptorsRegistry.getVaultEncryptor(any())).thenReturn(vaultEncryptor);
    FieldUtils.writeField(deleteSecretTask, "vaultEncryptorsRegistry", vaultEncryptorsRegistry, true);

    encryptedRecord = mock(EncryptedRecord.class);
    encryptionConfig = mock(EncryptionConfig.class);
    accountId = UUIDGenerator.generateUuid();
    when(encryptionConfig.getAccountId()).thenReturn(accountId);
    when(deleteSecretTaskParameters.getEncryptionConfig()).thenReturn(encryptionConfig);
    when(deleteSecretTaskParameters.getExistingRecord()).thenReturn(encryptedRecord);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRunDeleteSecretTask() {
    when(vaultEncryptor.deleteSecret(accountId, encryptedRecord, encryptionConfig)).thenReturn(true);
    DeleteSecretTaskResponse deleteSecretTaskResponse =
        (DeleteSecretTaskResponse) deleteSecretTask.run(deleteSecretTaskParameters);
    assertThat(deleteSecretTaskResponse).isNotNull();
    assertThat(deleteSecretTaskResponse.isDeleted()).isEqualTo(true);
  }
}
