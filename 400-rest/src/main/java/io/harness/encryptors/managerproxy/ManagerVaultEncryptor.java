/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors.managerproxy;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.delegatetasks.UpsertSecretTaskType.CREATE;
import static io.harness.delegatetasks.UpsertSecretTaskType.RENAME;
import static io.harness.delegatetasks.UpsertSecretTaskType.UPDATE;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.TaskType.DELETE_SECRET;
import static software.wings.beans.TaskType.UPSERT_SECRET;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.SecretText;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegatetasks.DeleteSecretTaskParameters;
import io.harness.delegatetasks.DeleteSecretTaskResponse;
import io.harness.delegatetasks.UpsertSecretTaskParameters;
import io.harness.delegatetasks.UpsertSecretTaskResponse;
import io.harness.delegatetasks.ValidateSecretManagerConfigurationTaskParameters;
import io.harness.delegatetasks.ValidateSecretReferenceTaskParameters;
import io.harness.encryptors.DelegateTaskUtils;
import io.harness.encryptors.VaultEncryptor;
import io.harness.exception.SecretManagementException;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;

@ValidateOnExecution
@OwnedBy(PL)
@Singleton
@TargetModule(HarnessModule._890_SM_CORE)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
@BreakDependencyOn("io.harness.beans.DelegateTask")
public class ManagerVaultEncryptor implements VaultEncryptor {
  private final DelegateService delegateService;
  private final ManagerEncryptorHelper managerEncryptorHelper;

  @Inject
  public ManagerVaultEncryptor(DelegateService delegateService, ManagerEncryptorHelper managerEncryptorHelper) {
    this.delegateService = delegateService;
    this.managerEncryptorHelper = managerEncryptorHelper;
  }

  @Override
  public EncryptedRecord createSecret(
      String accountId, String name, String plaintext, EncryptionConfig encryptionConfig) {
    UpsertSecretTaskParameters parameters = UpsertSecretTaskParameters.builder()
                                                .name(name)
                                                .plaintext(plaintext)
                                                .encryptionConfig(encryptionConfig)
                                                .taskType(CREATE)
                                                .build();

    return upsertSecret(accountId, parameters);
  }

  @Override
  public EncryptedRecord updateSecret(String accountId, String name, String plaintext, EncryptedRecord existingRecord,
      EncryptionConfig encryptionConfig) {
    UpsertSecretTaskParameters parameters = UpsertSecretTaskParameters.builder()
                                                .name(name)
                                                .plaintext(plaintext)
                                                .existingRecord(existingRecord)
                                                .encryptionConfig(encryptionConfig)
                                                .taskType(UPDATE)
                                                .build();
    return upsertSecret(accountId, parameters);
  }

  @Override
  public EncryptedRecord renameSecret(
      String accountId, String name, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    UpsertSecretTaskParameters parameters = UpsertSecretTaskParameters.builder()
                                                .name(name)
                                                .existingRecord(existingRecord)
                                                .encryptionConfig(encryptionConfig)
                                                .taskType(RENAME)
                                                .build();
    return upsertSecret(accountId, parameters);
  }

  private EncryptedRecord upsertSecret(String accountId, UpsertSecretTaskParameters parameters) {
    DelegateTask delegateTask =
        DelegateTask.builder()
            .data(TaskData.builder()
                      .async(false)
                      .taskType(UPSERT_SECRET.name())
                      .parameters(new Object[] {parameters})
                      .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                      .build())
            .accountId(accountId)
            .setupAbstractions(managerEncryptorHelper.buildAbstractions(parameters.getEncryptionConfig()))
            .build();

    try {
      DelegateResponseData delegateResponseData = delegateService.executeTaskV2(delegateTask);
      DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
      if (!(delegateResponseData instanceof UpsertSecretTaskResponse)) {
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
      }
      UpsertSecretTaskResponse responseData = (UpsertSecretTaskResponse) delegateResponseData;
      return responseData.getEncryptedRecord();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      String message = String.format("Interrupted while validating reference with encryption config %s",
          parameters.getEncryptionConfig().getName());
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }
  }

  @Override
  public boolean deleteSecret(String accountId, EncryptedRecord existingRecord, EncryptionConfig encryptionConfig) {
    DeleteSecretTaskParameters parameters =
        DeleteSecretTaskParameters.builder().existingRecord(existingRecord).encryptionConfig(encryptionConfig).build();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .data(TaskData.builder()
                      .async(false)
                      .taskType(DELETE_SECRET.name())
                      .parameters(new Object[] {parameters})
                      .timeout(TaskData.DEFAULT_SYNC_CALL_TIMEOUT)
                      .build())
            .accountId(accountId)
            .setupAbstractions(managerEncryptorHelper.buildAbstractions(parameters.getEncryptionConfig()))
            .build();
    try {
      DelegateResponseData delegateResponseData = delegateService.executeTaskV2(delegateTask);
      DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
      if (!(delegateResponseData instanceof DeleteSecretTaskResponse)) {
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
      }
      DeleteSecretTaskResponse responseData = (DeleteSecretTaskResponse) delegateResponseData;
      return responseData.isDeleted();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      String message = String.format("Interrupted while validating reference with encryption config %s",
          parameters.getEncryptionConfig().getName());
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }
  }

  @Override
  public boolean validateReference(String accountId, String path, EncryptionConfig encryptionConfig) {
    ValidateSecretReferenceTaskParameters parameters =
        ValidateSecretReferenceTaskParameters.builder()
            .encryptedRecord(EncryptedRecordData.builder().path(path).build())
            .encryptionConfig(encryptionConfig)
            .build();

    return managerEncryptorHelper.validateReference(accountId, parameters);
  }

  @Override
  public boolean validateReference(String accountId, SecretText secretText, EncryptionConfig encryptionConfig) {
    ValidateSecretReferenceTaskParameters parameters =
        ValidateSecretReferenceTaskParameters.builder()
            .encryptedRecord(
                EncryptedRecordData.builder().path(secretText.getPath()).name(secretText.getName()).build())
            .encryptionConfig(encryptionConfig)
            .build();

    return managerEncryptorHelper.validateReference(accountId, parameters);
  }

  @Override
  public boolean validateSecretManagerConfiguration(String accountId, EncryptionConfig encryptionConfig) {
    ValidateSecretManagerConfigurationTaskParameters parameters =
        ValidateSecretManagerConfigurationTaskParameters.builder().encryptionConfig(encryptionConfig).build();
    return managerEncryptorHelper.validateConfiguration(accountId, parameters);
  }

  @Override
  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    return managerEncryptorHelper.fetchSecretValue(accountId, encryptedRecord, encryptionConfig);
  }

  @Override
  public EncryptedRecord createSecret(String accountId, SecretText secretText, EncryptionConfig encryptionConfig) {
    UpsertSecretTaskParameters parameters = UpsertSecretTaskParameters.builder()
                                                .name(secretText.getName())
                                                .plaintext(secretText.getValue())
                                                .additionalMetadata(secretText.getAdditionalMetadata())
                                                .encryptionConfig(encryptionConfig)
                                                .taskType(CREATE)
                                                .build();
    return upsertSecret(accountId, parameters);
  }
}
