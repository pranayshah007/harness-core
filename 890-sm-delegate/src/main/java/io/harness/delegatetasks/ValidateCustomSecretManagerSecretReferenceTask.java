/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.security.encryption.EncryptedRecord;

import software.wings.beans.CustomSecretNGManagerConfig;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(PL)
public class ValidateCustomSecretManagerSecretReferenceTask extends AbstractDelegateRunnableTask {
  @Inject CustomEncryptorsRegistry customEncryptorsRegistry;

  public ValidateCustomSecretManagerSecretReferenceTask(DelegateTaskPackage delegateTaskPackage,
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
    ValidateCustomSecretManagerSecretReferenceTaskParameters validateCustomSecretManagerSecretReferenceTaskParameters =
        (ValidateCustomSecretManagerSecretReferenceTaskParameters) parameters;
    EncryptedRecord encryptedRecord = validateCustomSecretManagerSecretReferenceTaskParameters.getEncryptedRecord();
    CustomSecretNGManagerConfig encryptionConfig =
        (CustomSecretNGManagerConfig) validateCustomSecretManagerSecretReferenceTaskParameters.getEncryptionConfig();
    String resolvedScript = validateCustomSecretManagerSecretReferenceTaskParameters.getScript();
    encryptionConfig.setScript(resolvedScript);
    CustomEncryptor customEncryptor = customEncryptorsRegistry.getCustomEncryptor(encryptionConfig.getEncryptionType());
    boolean isReferenceValid = customEncryptor.validateReference(
        encryptionConfig.getAccountId(), encryptedRecord.getParameters(), encryptionConfig);
    return ValidateCustomSecretManagerSecretReferenceTaskResponse.builder().isReferenceValid(isReferenceValid).build();
  }
}
