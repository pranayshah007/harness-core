/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
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
import io.harness.security.encryption.EncryptionType;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.SettingAttribute;
import software.wings.beans.dto.SecretManagerRuntimeParameters;
import software.wings.security.UsageRestrictions;
import software.wings.service.intfc.security.EncryptedSettingAttributes;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingVariableTypes;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Created by rsingh on 9/7/18.
 */
@OwnedBy(PL)
@TargetModule(HarnessModule._440_SECRET_MANAGEMENT_SERVICE)
public class NoOpSecretManagerImpl implements SecretManager, EncryptedSettingAttributes {
  @Override
  public List<SecretManagerConfig> listSecretManagers(String accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SecretManagerConfig getSecretManager(String accountId, String kmsId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SecretManagerConfig getSecretManagerByName(String accountId, String name) {
    throw new UnsupportedOperationException();
  }

  public SecretManagerConfig getSecretManager(String accountId, String entityId, EncryptionType encryptionType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void updateUsageRestrictionsForSecretManagerConfig(
      String accountId, String secretManagerId, UsageRestrictions usageRestrictions) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptionType getEncryptionType(String accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void maskEncryptedFields(EncryptableSetting object) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void resetUnchangedEncryptedFields(EncryptableSetting sourceObject, EncryptableSetting destinationObject) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PageResponse<SecretUsageLog> getUsageLogs(PageRequest<SecretUsageLog> pageRequest, String accountId,
      String entityId, SettingVariableTypes variableType) throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<SecretSetupUsage> getSecretUsage(String accountId, String secretId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<SecretChangeLog> getChangeLogs(String accountId, String entityId, SettingVariableTypes variableType)
      throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<EncryptedDataDetail> encryptedDataDetails(
      String accountId, String fieldName, String refId, String workflowExecutionId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<EncryptedDataDetail> encryptedDataDetails(String accountId, String fieldName, String encryptedDataId,
      String workflowExecutionId, boolean updateSecretUsage) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<EncryptedDataDetail> getEncryptedDataDetails(
      String accountId, String fieldName, EncryptedData encryptedData, String workflowExecutionId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<EncryptedDataDetail> getEncryptedDataDetails(String accountId, String fieldName,
      EncryptedData encryptedData, String workflowExecutionId, boolean updateSecretUsage) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<EncryptedDataDetail> getEncryptionDetails(EncryptableSetting object) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<EncryptedDataDetail> getEncryptionDetails(
      EncryptableSetting object, String appId, String workflowExecutionId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<EncryptedDataDetail> getEncryptionDetails(
      EncryptableSetting object, String appId, String workflowExecutionId, boolean updateSecretUsage) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<SettingAttribute> listEncryptedSettingAttributes(String accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<SettingAttribute> listEncryptedSettingAttributes(String accountId, Set<String> categories) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getEncryptedYamlRef(String accountId, String secretId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData getEncryptedDataFromYamlRef(String encryptedYamlRef, String accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean transitionSecrets(String accountId, EncryptionType fromEncryptionType, String fromManagerSecretId,
      EncryptionType toEncryptionType, String toSecretManagerId, Map<String, String> sourceSMRuntimeParams,
      Map<String, String> destinationSMRuntimeParams) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData getSecretMappedToAccountByName(String accountId, String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData getSecretMappedToAppByName(String accountId, String appId, String envId, String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData getSecretById(String accountId, String secretRecordId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData getSecretByName(String accountId, String secretName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> importSecrets(String accountId, List<SecretText> secretTexts) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<String> importSecretsViaFile(String accountId, InputStream uploadStream) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String saveSecretText(String accountId, SecretText secretText, boolean validateScopes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData encryptSecret(String accountId, SecretText secretText, boolean validateScopes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public EncryptedData encryptSecretUsingGlobalSM(String accountId, SecretText secretText, boolean validateScopes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String fetchSecretValue(String accountId, String secretRecordId) {
    return null;
  }

  @Override
  public boolean updateSecretText(String accountId, String uuId, SecretText secretText, boolean validateScopes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String saveSecretFile(String accountId, SecretFile secretFile) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean updateSecretFile(String accountId, String existingRecordId, SecretFile secretFile) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean deleteSecret(
      String accountId, String uuId, Map<String, String> runtimeParameters, boolean validateScopes) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean updateUsageRestrictionsForSecretOrFile(String accountId, String uuId,
      UsageRestrictions usageRestrictions, boolean scopedToAccount, boolean inheritScopesFromSM) {
    throw new UnsupportedOperationException();
  }

  @Override
  public File getFile(String accountId, String uuId, File readInto) {
    throw new UnsupportedOperationException();
  }

  @Override
  public byte[] getFileContents(String accountId, String uuId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PageResponse<EncryptedData> listSecrets(String accountId, PageRequest<EncryptedData> pageRequest,
      String appIdFromRequest, String envIdFromRequest, boolean details, boolean listHidden, boolean ignoreRunTimeUsage)
      throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PageResponse<EncryptedData> listSecrets(String accountId, PageRequest<EncryptedData> pageRequest,
      String appIdFromRequest, String envIdFromRequest, boolean details, boolean ignoreRunTimeUsage)
      throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PageResponse<EncryptedData> listSecretsMappedToAccount(
      String accountId, PageRequest<EncryptedData> pageRequest, boolean details, boolean ignoreRunTimeUsage)
      throws IllegalAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public String saveSecretUsingLocalMode(String accountId, SecretText secretText) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean transitionAllSecretsToHarnessSecretManager(String accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean canUseSecretsInAppAndEnv(
      Set<String> secretIds, String accountId, String appIdFromRequest, String envIdFromRequest) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean hasUpdateAccessToSecrets(Set<String> secretIds, String accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clearDefaultFlagOfSecretManagers(String accountId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SecretManagerRuntimeParameters configureSecretManagerRuntimeCredentialsForExecution(
      String accountId, String kmsId, String executionId, Map<String, String> runtimeParameters) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<SecretManagerRuntimeParameters> getSecretManagerRuntimeCredentialsForExecution(
      String executionId, String secretManagerId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public UsageRestrictions getAllowedUsageScopesToCreateSecret(String accountId, String secretsManagerConfigId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteByAccountId(String accountId) {
    // no-op
  }

  @Override
  public List<SecretMetadata> filterSecretIdsByReadPermission(Set<String> secretIds, String accountId,
      String appIdFromRequest, String envIdFromRequest, boolean forUsageInNewApp) {
    throw new UnsupportedOperationException();
  }
}
