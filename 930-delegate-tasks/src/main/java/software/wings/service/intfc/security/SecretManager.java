/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SecretChangeLog;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretMetadata;
import io.harness.beans.SecretText;
import io.harness.beans.SecretUsageLog;
import io.harness.secrets.setupusage.SecretSetupUsage;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.dto.SecretManagerRuntimeParameters;
import software.wings.security.UsageRestrictions;
import software.wings.service.intfc.ownership.OwnedByAccount;
import software.wings.settings.SettingVariableTypes;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@OwnedBy(PL)
public interface SecretManager extends OwnedByAccount {
  String ENCRYPTED_FIELD_MASK = "*******";
  String CREATED_AT_KEY = "createdAt";

  List<SecretManagerConfig> listSecretManagers(String accountId);

  SecretManagerConfig getSecretManager(String accountId, String kmsId);

  SecretManagerConfig getSecretManagerByName(String accountId, String name);

  SecretManagerConfig getSecretManager(String accountId, String entityId, EncryptionType encryptionType);

  void updateUsageRestrictionsForSecretManagerConfig(
      String accountId, String secretManagerId, UsageRestrictions usageRestrictions);

  EncryptionType getEncryptionType(String accountId);

  void maskEncryptedFields(EncryptableSetting object);

  void resetUnchangedEncryptedFields(EncryptableSetting sourceObject, EncryptableSetting destinationObject);

  PageResponse<SecretUsageLog> getUsageLogs(PageRequest<SecretUsageLog> pageRequest, String accountId, String entityId,
      SettingVariableTypes variableType) throws IllegalAccessException;

  Set<SecretSetupUsage> getSecretUsage(String accountId, String secretId);

  List<SecretChangeLog> getChangeLogs(String accountId, String entityId, SettingVariableTypes variableType)
      throws IllegalAccessException;

  Optional<EncryptedDataDetail> encryptedDataDetails(
      String accountId, String fieldName, String refId, String workflowExecutionId);

  Optional<EncryptedDataDetail> encryptedDataDetails(String accountId, String fieldName, String encryptedDataId,
      String workflowExecutionId, boolean updateSecretUsage);

  Optional<EncryptedDataDetail> getEncryptedDataDetails(
      String accountId, String fieldName, EncryptedData encryptedData, String workflowExecutionId);

  Optional<EncryptedDataDetail> getEncryptedDataDetails(String accountId, String fieldName, EncryptedData encryptedData,
      String workflowExecutionId, boolean updateSecretUsage);

  List<EncryptedDataDetail> getEncryptionDetails(EncryptableSetting object);

  List<EncryptedDataDetail> getEncryptionDetails(EncryptableSetting object, String appId, String workflowExecutionId);

  List<EncryptedDataDetail> getEncryptionDetails(
      EncryptableSetting object, String appId, String workflowExecutionId, boolean updateSecretUsage);

  String getEncryptedYamlRef(String accountId, String secretId);

  EncryptedData getEncryptedDataFromYamlRef(String encryptedYamlRef, String accountId);

  boolean transitionSecrets(String accountId, EncryptionType fromEncryptionType, String fromManagerSecretId,
      EncryptionType toEncryptionType, String toSecretManagerId, Map<String, String> sourceSMRuntimeParams,
      Map<String, String> destinationSMRuntimeParams);

  EncryptedData getSecretMappedToAccountByName(String accountId, String name);

  EncryptedData getSecretMappedToAppByName(String accountId, String appId, String envId, String name);

  EncryptedData getSecretById(String accountId, String secretRecordId);

  EncryptedData getSecretByName(String accountId, String secretName);

  List<String> importSecrets(String accountId, List<SecretText> secretTexts);

  List<String> importSecretsViaFile(String accountId, InputStream uploadStream);

  String saveSecretText(String accountId, SecretText secretText, boolean validateScopes);

  EncryptedData encryptSecret(String accountId, SecretText secretText, boolean validateScopes);
  EncryptedData encryptSecretUsingGlobalSM(String accountId, SecretText secretText, boolean validateScopes);

  String fetchSecretValue(String accountId, String secretRecordId);

  boolean updateSecretText(String accountId, String uuId, SecretText secretText, boolean validateScopes);

  String saveSecretFile(String accountId, SecretFile secretFile);

  boolean updateSecretFile(String accountId, String existingRecordId, SecretFile secretFile);

  boolean deleteSecret(String accountId, String uuId, Map<String, String> runtimeParameters, boolean validateScopes);

  boolean updateUsageRestrictionsForSecretOrFile(String accountId, String uuId, UsageRestrictions usageRestrictions,
      boolean scopedToAccount, boolean inheritScopesFromSM);

  File getFile(String accountId, String uuId, File readInto);

  byte[] getFileContents(String accountId, String uuId);

  PageResponse<EncryptedData> listSecrets(String accountId, PageRequest<EncryptedData> pageRequest,
      String appIdFromRequest, String envIdFromRequest, boolean details, boolean listHidden, boolean ignoreRunTimeUsage)
      throws IllegalAccessException;

  PageResponse<EncryptedData> listSecrets(String accountId, PageRequest<EncryptedData> pageRequest,
      String appIdFromRequest, String envIdFromRequest, boolean details, boolean ignoreRunTimeUsage)
      throws IllegalAccessException;

  PageResponse<EncryptedData> listSecretsMappedToAccount(String accountId, PageRequest<EncryptedData> pageRequest,
      boolean details, boolean ignoreRunTimeUsage) throws IllegalAccessException;

  String saveSecretUsingLocalMode(String accountId, SecretText secretText);

  boolean transitionAllSecretsToHarnessSecretManager(String accountId);

  boolean canUseSecretsInAppAndEnv(
      Set<String> secretIds, String accountId, String appIdFromRequest, String envIdFromRequest);

  List<SecretMetadata> filterSecretIdsByReadPermission(Set<String> secretIds, String accountId, String appIdFromRequest,
      String envIdFromRequest, boolean forUsageInNewApp);

  boolean hasUpdateAccessToSecrets(Set<String> secretIds, String accountId);

  void clearDefaultFlagOfSecretManagers(String accountId);

  static EncryptedRecordData buildRecordData(EncryptedData encryptedData) {
    return EncryptedRecordData.builder()
        .uuid(encryptedData.getUuid())
        .name(encryptedData.getName())
        .path(encryptedData.getPath())
        .parameters(encryptedData.getParameters())
        .encryptionKey(encryptedData.getEncryptionKey())
        .encryptedValue(encryptedData.getEncryptedValue())
        .kmsId(encryptedData.getKmsId())
        .encryptionType(encryptedData.getEncryptionType())
        .base64Encoded(encryptedData.isBase64Encoded())
        .build();
  }

  SecretManagerRuntimeParameters configureSecretManagerRuntimeCredentialsForExecution(
      String accountId, String kmsId, String executionId, Map<String, String> runtimeParameters);

  Optional<SecretManagerRuntimeParameters> getSecretManagerRuntimeCredentialsForExecution(
      String executionId, String secretManagerId);

  UsageRestrictions getAllowedUsageScopesToCreateSecret(String accountId, String secretsManagerConfigId);
}
