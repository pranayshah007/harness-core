/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.PL)
public class DeleteSecretTask extends AbstractDelegateRunnableTask {
  @Inject VaultEncryptorsRegistry vaultEncryptorsRegistry;

  public DeleteSecretTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    DeleteSecretTaskParameters deleteSecretTaskParameters = (DeleteSecretTaskParameters) parameters;
    EncryptionConfig encryptionConfig = deleteSecretTaskParameters.getEncryptionConfig();
    EncryptedRecord encryptedRecord = deleteSecretTaskParameters.getExistingRecord();
    VaultEncryptor vaultEncryptor = vaultEncryptorsRegistry.getVaultEncryptor(encryptionConfig.getEncryptionType());
    boolean deleted = vaultEncryptor.deleteSecret(encryptionConfig.getAccountId(), encryptedRecord, encryptionConfig);
    return DeleteSecretTaskResponse.builder().deleted(deleted).build();
  }
}
