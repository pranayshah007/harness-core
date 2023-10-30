/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.encryptors;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.utils.DelegateOwner.NG_DELEGATE_ENABLED_CONSTANT;
import static io.harness.utils.DelegateOwner.NG_DELEGATE_OWNER_CONSTANT;

import static software.wings.beans.TaskType.FETCH_CUSTOM_SECRET;
import static software.wings.beans.TaskType.FETCH_SECRET;
import static software.wings.beans.TaskType.RESOLVE_CUSTOM_SM_CONFIG;
import static software.wings.beans.TaskType.VALIDATE_CUSTOM_SECRET_MANAGER_SECRET_REFERENCE;
import static software.wings.beans.TaskType.VALIDATE_SECRET_MANAGER_CONFIGURATION;
import static software.wings.beans.TaskType.VALIDATE_SECRET_REFERENCE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.utils.TaskSetupAbstractionHelper;
import io.harness.delegatetasks.FetchCustomSecretTaskParameters;
import io.harness.delegatetasks.FetchCustomSecretTaskResponse;
import io.harness.delegatetasks.FetchSecretTaskParameters;
import io.harness.delegatetasks.FetchSecretTaskResponse;
import io.harness.delegatetasks.ValidateCustomSecretManagerSecretReferenceTaskParameters;
import io.harness.delegatetasks.ValidateCustomSecretManagerSecretReferenceTaskResponse;
import io.harness.delegatetasks.ValidateSecretManagerConfigurationTaskParameters;
import io.harness.delegatetasks.ValidateSecretManagerConfigurationTaskResponse;
import io.harness.delegatetasks.ValidateSecretReferenceTaskParameters;
import io.harness.delegatetasks.ValidateSecretReferenceTaskResponse;
import io.harness.encryptors.DelegateTaskUtils;
import io.harness.exception.SecretManagementException;
import io.harness.ng.core.NGAccess;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.service.DelegateGrpcClientWrapper;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(PL)
@Slf4j
public class NGManagerEncryptorHelper {
  private final DelegateGrpcClientWrapper delegateService;
  private final TaskSetupAbstractionHelper taskSetupAbstractionHelper;
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";

  @Inject
  public NGManagerEncryptorHelper(
      DelegateGrpcClientWrapper delegateService, TaskSetupAbstractionHelper taskSetupAbstractionHelper) {
    this.delegateService = delegateService;
    this.taskSetupAbstractionHelper = taskSetupAbstractionHelper;
  }

  public Map<String, String> buildAbstractions(EncryptionConfig encryptionConfig) {
    Map<String, String> abstractions = new HashMap<>(2);
    String owner = null;
    if (encryptionConfig instanceof NGAccess) {
      NGAccess ngAccess = (NGAccess) encryptionConfig;
      // Verify if its a Task from NG
      owner = taskSetupAbstractionHelper.getOwner(
          encryptionConfig.getAccountId(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
      if (isNotEmpty(owner)) {
        abstractions.put(NG_DELEGATE_OWNER_CONSTANT, owner);
      }
      abstractions.put(NG_DELEGATE_ENABLED_CONSTANT, "true");
      if (isNotEmpty(ngAccess.getOrgIdentifier())) {
        abstractions.put(ORG_IDENTIFIER, ngAccess.getOrgIdentifier());
      }
      if (isNotEmpty(ngAccess.getProjectIdentifier())) {
        abstractions.put(PROJECT_IDENTIFIER, ngAccess.getProjectIdentifier());
      }
    }
    return abstractions;
  }

  public char[] fetchSecretValue(String accountId, EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    FetchSecretTaskParameters parameters =
        FetchSecretTaskParameters.builder().encryptedRecord(encryptedRecord).encryptionConfig(encryptionConfig).build();
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(FETCH_SECRET.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountId)
            .taskSetupAbstractions(buildAbstractions(parameters.getEncryptionConfig()))
            .build();
    DelegateResponseData delegateResponseData = delegateService.executeSyncTaskV2(delegateTaskRequest);
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
    if (!(delegateResponseData instanceof FetchSecretTaskResponse)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
    }
    FetchSecretTaskResponse responseData = (FetchSecretTaskResponse) delegateResponseData;
    return responseData.getSecretValue();
  }

  public boolean validateReference(String accountId, ValidateSecretReferenceTaskParameters parameters) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(VALIDATE_SECRET_REFERENCE.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountId)
            .taskSetupAbstractions(buildAbstractions(parameters.getEncryptionConfig()))
            .build();
    DelegateResponseData delegateResponseData = delegateService.executeSyncTaskV2(delegateTaskRequest);
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
    if (!(delegateResponseData instanceof ValidateSecretReferenceTaskResponse)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
    }
    ValidateSecretReferenceTaskResponse responseData = (ValidateSecretReferenceTaskResponse) delegateResponseData;
    return responseData.isReferenceValid();
  }

  public boolean validateConfiguration(String accountId, ValidateSecretManagerConfigurationTaskParameters parameters) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(VALIDATE_SECRET_MANAGER_CONFIGURATION.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountId)
            .taskSetupAbstractions(buildAbstractions(parameters.getEncryptionConfig()))
            .build();
    DelegateResponseData delegateResponseData = delegateService.executeSyncTaskV2(delegateTaskRequest);
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
    if (!(delegateResponseData instanceof ValidateSecretManagerConfigurationTaskResponse)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
    }
    ValidateSecretManagerConfigurationTaskResponse responseData =
        (ValidateSecretManagerConfigurationTaskResponse) delegateResponseData;
    return responseData.isConfigurationValid();
  }

  public Pair<String, Boolean> validateConfigurationWithTaskId(
      String accountId, ValidateSecretManagerConfigurationTaskParameters parameters) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(VALIDATE_SECRET_MANAGER_CONFIGURATION.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountId)
            .taskSetupAbstractions(buildAbstractions(parameters.getEncryptionConfig()))
            .build();
    var responseEntry = delegateService.executeSyncTaskV2ReturnTaskId(delegateTaskRequest);
    DelegateResponseData delegateResponseData = responseEntry.getValue();
    String taskId = responseEntry.getKey();
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
    if (!(delegateResponseData instanceof ValidateSecretManagerConfigurationTaskResponse)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
    }
    ValidateSecretManagerConfigurationTaskResponse responseData =
        (ValidateSecretManagerConfigurationTaskResponse) delegateResponseData;
    return Pair.of(taskId, responseData.isConfigurationValid());
  }

  public char[] fetchSecretValue(String accountId, String script, int expressionFunctorToken,
      EncryptedRecord encryptedRecord, EncryptionConfig encryptionConfig) {
    FetchCustomSecretTaskParameters parameters = FetchCustomSecretTaskParameters.builder()
                                                     .encryptedRecord(encryptedRecord)
                                                     .encryptionConfig(encryptionConfig)
                                                     .script(script)
                                                     .build();
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(FETCH_CUSTOM_SECRET.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountId)
            .taskSetupAbstractions(buildAbstractions(parameters.getEncryptionConfig()))
            .expressionFunctorToken(expressionFunctorToken)
            .build();
    DelegateResponseData delegateResponseData = delegateService.executeSyncTaskV2(delegateTaskRequest);
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
    if (!(delegateResponseData instanceof FetchCustomSecretTaskResponse)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
    }
    FetchCustomSecretTaskResponse fetchCustomSecretTaskResponse = (FetchCustomSecretTaskResponse) delegateResponseData;
    return fetchCustomSecretTaskResponse.getSecretValue();
  }

  public boolean validateCustomSecretManagerSecretReference(String accountId, int expressionFunctorToken,
      ValidateCustomSecretManagerSecretReferenceTaskParameters parameters) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(VALIDATE_CUSTOM_SECRET_MANAGER_SECRET_REFERENCE.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountId)
            .taskSetupAbstractions(buildAbstractions(parameters.getEncryptionConfig()))
            .expressionFunctorToken(expressionFunctorToken)
            .build();
    DelegateResponseData delegateResponseData = delegateService.executeSyncTaskV2(delegateTaskRequest);
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
    if (!(delegateResponseData instanceof ValidateCustomSecretManagerSecretReferenceTaskResponse)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
    }
    ValidateCustomSecretManagerSecretReferenceTaskResponse responseData =
        (ValidateCustomSecretManagerSecretReferenceTaskResponse) delegateResponseData;
    return responseData.isReferenceValid();
  }

  public Pair<String, Boolean> validateCustomSecretManagerSecretReferenceWithTaskId(String accountId,
      int expressionFunctorToken, ValidateCustomSecretManagerSecretReferenceTaskParameters parameters) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(VALIDATE_CUSTOM_SECRET_MANAGER_SECRET_REFERENCE.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountId)
            .taskSetupAbstractions(buildAbstractions(parameters.getEncryptionConfig()))
            .expressionFunctorToken(expressionFunctorToken)
            .build();
    var responseEntry = delegateService.executeSyncTaskV2ReturnTaskId(delegateTaskRequest);
    DelegateResponseData delegateResponseData = responseEntry.getValue();
    String taskId = responseEntry.getKey();
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
    if (!(delegateResponseData instanceof ValidateCustomSecretManagerSecretReferenceTaskResponse)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
    }
    ValidateCustomSecretManagerSecretReferenceTaskResponse responseData =
        (ValidateCustomSecretManagerSecretReferenceTaskResponse) delegateResponseData;
    return Pair.of(taskId, responseData.isReferenceValid());
  }

  public String resolveSecretManagerConfig(String accountId, int expressionFunctorToken,
      ValidateCustomSecretManagerSecretReferenceTaskParameters parameters) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .taskType(RESOLVE_CUSTOM_SM_CONFIG.name())
            .taskParameters(parameters)
            .executionTimeout(Duration.ofMillis(TaskData.DEFAULT_SYNC_CALL_TIMEOUT))
            .accountId(accountId)
            .taskSetupAbstractions(buildAbstractions(parameters.getEncryptionConfig()))
            .expressionFunctorToken(expressionFunctorToken)
            .build();
    DelegateResponseData delegateResponseData = delegateService.executeSyncTaskV2(delegateTaskRequest);
    DelegateTaskUtils.validateDelegateTaskResponse(delegateResponseData);
    if (!(delegateResponseData instanceof ValidateCustomSecretManagerSecretReferenceTaskResponse)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unknown Response from delegate", USER);
    }
    ValidateCustomSecretManagerSecretReferenceTaskResponse responseData =
        (ValidateCustomSecretManagerSecretReferenceTaskResponse) delegateResponseData;
    return responseData.getScript();
  }
}
