/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretText;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.PL)
public class UpsertSecretTask extends AbstractDelegateRunnableTask {
  @Inject VaultEncryptorsRegistry vaultEncryptorsRegistry;

  public UpsertSecretTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    UpsertSecretTaskParameters upsertSecretTaskParameters = (UpsertSecretTaskParameters) parameters;
    return run(upsertSecretTaskParameters, vaultEncryptorsRegistry);
  }

  protected static UpsertSecretTaskResponse run(
      UpsertSecretTaskParameters parameters, VaultEncryptorsRegistry vaultEncryptorsRegistry) {
    EncryptedRecord existingRecord = parameters.getExistingRecord();
    EncryptionConfig encryptionConfig = parameters.getEncryptionConfig();
    AdditionalMetadata additionalMetadata = parameters.getAdditionalMetadata();
    VaultEncryptor vaultEncryptor = vaultEncryptorsRegistry.getVaultEncryptor(encryptionConfig.getEncryptionType());
    EncryptedRecord encryptedRecord;
    if (parameters.getTaskType() == UpsertSecretTaskType.UPDATE) {
      encryptedRecord = vaultEncryptor.updateSecret(encryptionConfig.getAccountId(),
          SecretText.builder()
              .name(parameters.getName())
              .value(parameters.getPlaintext())
              .additionalMetadata(additionalMetadata)
              .build(),
          existingRecord, encryptionConfig);
    } else if (parameters.getTaskType() == UpsertSecretTaskType.CREATE) {
      encryptedRecord = vaultEncryptor.createSecret(encryptionConfig.getAccountId(),
          SecretText.builder()
              .name(parameters.getName())
              .value(parameters.getPlaintext())
              .additionalMetadata(additionalMetadata)
              .build(),
          encryptionConfig);
    } else if (parameters.getTaskType() == UpsertSecretTaskType.RENAME) {
      encryptedRecord = vaultEncryptor.renameSecret(encryptionConfig.getAccountId(),
          SecretText.builder()
              .name(parameters.getName())
              .additionalMetadata(parameters.getAdditionalMetadata())
              .build(),
          existingRecord, encryptionConfig);
    } else {
      throw new SecretManagementDelegateException(SECRET_MANAGEMENT_ERROR, "Unknown upsert secret task type", USER);
    }
    return UpsertSecretTaskResponse.builder().encryptedRecord(encryptedRecord).build();
  }
}
