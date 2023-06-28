/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.security.encryption.SecretManagerType.CUSTOM;
import static io.harness.security.encryption.SecretManagerType.VAULT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretText;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(PL)
public class ValidateSecretReferenceTask extends AbstractDelegateRunnableTask {
  @Inject VaultEncryptorsRegistry vaultEncryptorsRegistry;
  @Inject CustomEncryptorsRegistry customEncryptorsRegistry;

  public ValidateSecretReferenceTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    ValidateSecretReferenceTaskParameters validateSecretReferenceTaskParameters =
        (ValidateSecretReferenceTaskParameters) parameters;
    EncryptionConfig encryptionConfig = validateSecretReferenceTaskParameters.getEncryptionConfig();

    if (encryptionConfig.getType() == VAULT) {
      return runVaultTask(validateSecretReferenceTaskParameters);
    } else if (encryptionConfig.getType() == CUSTOM) {
      return runCustomTask(validateSecretReferenceTaskParameters);
    }

    throw new SecretManagementDelegateException(SECRET_MANAGEMENT_ERROR,
        String.format("Encryptor for validate reference task for encryption config %s not configured",
            encryptionConfig.getName()),
        USER);
  }

  private ValidateSecretReferenceTaskResponse runVaultTask(ValidateSecretReferenceTaskParameters parameters) {
    EncryptedRecord encryptedRecord = parameters.getEncryptedRecord();
    EncryptionConfig encryptionConfig = parameters.getEncryptionConfig();
    VaultEncryptor vaultEncryptor = vaultEncryptorsRegistry.getVaultEncryptor(encryptionConfig.getEncryptionType());
    boolean isReferenceValid = vaultEncryptor.validateReference(encryptionConfig.getAccountId(),
        SecretText.builder()
            .path(encryptedRecord.getPath())
            .name(encryptedRecord.getName())
            .additionalMetadata(encryptedRecord.getAdditionalMetadata())
            .build(),
        encryptionConfig);
    return ValidateSecretReferenceTaskResponse.builder().isReferenceValid(isReferenceValid).build();
  }

  private ValidateSecretReferenceTaskResponse runCustomTask(ValidateSecretReferenceTaskParameters parameters) {
    EncryptedRecord encryptedRecord = parameters.getEncryptedRecord();
    EncryptionConfig encryptionConfig = parameters.getEncryptionConfig();
    CustomEncryptor customEncryptor = customEncryptorsRegistry.getCustomEncryptor(encryptionConfig.getEncryptionType());
    boolean isReferenceValid = customEncryptor.validateReference(
        encryptionConfig.getAccountId(), encryptedRecord.getParameters(), encryptionConfig);
    return ValidateSecretReferenceTaskResponse.builder().isReferenceValid(isReferenceValid).build();
  }
}
