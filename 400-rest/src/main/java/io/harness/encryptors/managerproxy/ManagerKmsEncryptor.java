/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.managerproxy;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.TaskType.ENCRYPT_SECRET;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.delegatetasks.EncryptSecretTaskParameters;
import io.harness.delegatetasks.EncryptSecretTaskResponse;
import io.harness.delegatetasks.ValidateSecretManagerConfigurationTaskParameters;
import io.harness.encryptors.DelegateTaskUtils;
import io.harness.encryptors.KmsEncryptor;
import io.harness.exception.SecretManagementException;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;

@ValidateOnExecution
@OwnedBy(PL)
@Singleton
public class ManagerKmsEncryptor implements KmsEncryptor {
  private final DelegateService delegateService;
  private final ManagerEncryptorHelper managerEncryptorHelper;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;

  @Inject
  public ManagerKmsEncryptor(DelegateService delegateService, ManagerEncryptorHelper managerEncryptorHelper,
      TaskSetupAbstractionHelper taskSetupAbstractionHelper) {
    this.delegateService = delegateService;
    this.managerEncryptorHelper = managerEncryptorHelper;
    this.taskSetupAbstractionHelper = taskSetupAbstractionHelper;
  }

  @Override
  public EncryptedRecord encryptSecret(String accountId, String value, EncryptionConfig encryptionConfig) {
    EncryptSecretTaskParameters parameters =
        EncryptSecretTaskParameters.builder().value(value).encryptionConfig(encryptionConfig).build();

    DelegateTask delegateTask = DelegateTask.builder()
                                    .data(TaskData.builder()
                                              .async(false)
                                              .taskType(ENCRYPT_SECRET.name())
                                              .parameters(new Object[] {parameters})
                                              .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                                              .build())
                                    .accountId(accountId)
                                    .setupAbstractions(managerEncryptorHelper.buildAbstractions(encryptionConfig))
                                    .build();
    try {
      DelegateResponseData delegateResponseData = delegateService.executeTaskV2(delegateTask);
      DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
      if (!(delegateResponseData instanceof EncryptSecretTaskResponse)) {
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
      }
      EncryptSecretTaskResponse responseData = (EncryptSecretTaskResponse) delegateResponseData;
      return responseData.getEncryptedRecord();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      String message =
          String.format("Interrupted while validating reference with encryption config %s", encryptionConfig.getName());
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    return managerEncryptorHelper.fetchSecretValue(accountId, encryptedRecord, encryptionConfig);
  }

  @Override
  public boolean validateKmsConfiguration(String accountId, EncryptionConfig encryptionConfig) {
    ValidateSecretManagerConfigurationTaskParameters parameters =
        ValidateSecretManagerConfigurationTaskParameters.builder().encryptionConfig(encryptionConfig).build();
    return managerEncryptorHelper.validateConfiguration(accountId, parameters);
  }
}
