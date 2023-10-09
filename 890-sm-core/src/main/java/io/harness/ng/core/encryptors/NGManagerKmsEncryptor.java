/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.encryptors;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.TaskType.ENCRYPT_SECRET;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegatetasks.EncryptSecretTaskParameters;
import io.harness.delegatetasks.EncryptSecretTaskResponse;
import io.harness.delegatetasks.ValidateSecretManagerConfigurationTaskParameters;
import io.harness.encryptors.DelegateTaskUtils;
import io.harness.encryptors.NgCgManagerKmsEncryptor;
import io.harness.exception.SecretManagementException;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import javax.validation.executable.ValidateOnExecution;
import org.apache.commons.lang3.tuple.Pair;

@ValidateOnExecution
@OwnedBy(PL)
@Singleton
public class NGManagerKmsEncryptor implements NgCgManagerKmsEncryptor {
  private final DelegateGrpcClientWrapper delegateService;
  private final NGManagerEncryptorHelper managerEncryptorHelper;

  @Inject
  public NGManagerKmsEncryptor(
      DelegateGrpcClientWrapper delegateService, NGManagerEncryptorHelper managerEncryptorHelper) {
    this.delegateService = delegateService;
    this.managerEncryptorHelper = managerEncryptorHelper;
  }

  @Override
  public EncryptedRecord encryptSecret(String accountId, String value, EncryptionConfig encryptionConfig) {
    EncryptSecretTaskParameters parameters =
        EncryptSecretTaskParameters.builder().value(value).encryptionConfig(encryptionConfig).build();

    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(ENCRYPT_SECRET.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountId)
            .taskSetupAbstractions(managerEncryptorHelper.buildAbstractions(encryptionConfig))
            .build();
    DelegateResponseData delegateResponseData = delegateService.executeSyncTaskV2(delegateTaskRequest);
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
    if (!(delegateResponseData instanceof EncryptSecretTaskResponse)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
    }
    EncryptSecretTaskResponse responseData = (EncryptSecretTaskResponse) delegateResponseData;
    return responseData.getEncryptedRecord();
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

  @Override
  public Pair<String, Boolean> validateKmsConfigurationWithTaskId(String accountId, EncryptionConfig encryptionConfig) {
    ValidateSecretManagerConfigurationTaskParameters parameters =
        ValidateSecretManagerConfigurationTaskParameters.builder().encryptionConfig(encryptionConfig).build();
    return managerEncryptorHelper.validateConfigurationWithTaskId(accountId, parameters);
  }
}
