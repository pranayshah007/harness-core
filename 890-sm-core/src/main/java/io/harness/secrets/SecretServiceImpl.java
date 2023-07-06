/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.data.encoding.EncodingUtils.encodeBase64ToByteArray;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.RESOURCE_NOT_FOUND;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.eraro.ErrorCode.USER_NOT_AUTHORIZED_DUE_TO_USAGE_RESTRICTIONS;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.security.SimpleEncryption.CHARSET;
import static io.harness.security.encryption.SecretManagerType.CUSTOM;
import static io.harness.security.encryption.SecretManagerType.KMS;
import static io.harness.security.encryption.SecretManagerType.VAULT;

import static software.wings.settings.SettingVariableTypes.CONFIG_FILE;
import static software.wings.settings.SettingVariableTypes.SECRET_TEXT;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataBuilder;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.HarnessSecret;
import io.harness.beans.MigrateSecretTask;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretMetadata;
import io.harness.beans.SecretScopeMetadata;
import io.harness.beans.SecretState;
import io.harness.beans.SecretText;
import io.harness.beans.SecretUpdateData;
import io.harness.data.encoding.EncodingUtils;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.persistence.HIterator;
import io.harness.queue.QueuePublisher;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.secrets.setupusage.SecretSetupUsage;
import io.harness.secrets.setupusage.SecretSetupUsageService;
import io.harness.secrets.validation.SecretValidatorsRegistry;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;
import io.harness.security.encryption.SecretManagerType;
import io.harness.serializer.KryoSerializer;

import software.wings.security.UsageRestrictions;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DuplicateKeyException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@ValidateOnExecution
@Singleton
@OwnedBy(PL)
@Slf4j
public class SecretServiceImpl implements SecretService {
  private final SecretsDao secretsDao;
  private final SecretsRBACService secretsRBACService;
  private final SecretSetupUsageService secretSetupUsageService;
  private final SecretsAuditService secretsAuditService;
  private final SecretsFileService secretsFileService;
  private final SecretManagerConfigService secretManagerConfigService;
  private final SecretValidatorsRegistry secretValidatorsRegistry;
  private final KmsEncryptorsRegistry kmsRegistry;
  private final VaultEncryptorsRegistry vaultRegistry;
  private final CustomEncryptorsRegistry customRegistry;
  private final KryoSerializer kryoSerializer;
  private final QueuePublisher<MigrateSecretTask> secretsMigrationProcessor;

  @Inject
  public SecretServiceImpl(KryoSerializer kryoSerializer, SecretsDao secretsDao, SecretsRBACService secretsRBACService,
      SecretSetupUsageService secretSetupUsageService, SecretsFileService secretsFileService,
      SecretManagerConfigService secretManagerConfigService, SecretValidatorsRegistry secretValidatorsRegistry,
      SecretsAuditService secretsAuditService, KmsEncryptorsRegistry kmsRegistry, VaultEncryptorsRegistry vaultRegistry,
      CustomEncryptorsRegistry customRegistry, QueuePublisher<MigrateSecretTask> secretsMigrationProcessor) {
    this.kryoSerializer = kryoSerializer;
    this.secretsDao = secretsDao;
    this.secretsRBACService = secretsRBACService;
    this.secretManagerConfigService = secretManagerConfigService;
    this.secretValidatorsRegistry = secretValidatorsRegistry;
    this.secretSetupUsageService = secretSetupUsageService;
    this.secretsAuditService = secretsAuditService;
    this.secretsFileService = secretsFileService;
    this.kmsRegistry = kmsRegistry;
    this.vaultRegistry = vaultRegistry;
    this.customRegistry = customRegistry;
    this.secretsMigrationProcessor = secretsMigrationProcessor;
  }

  @Override
  public EncryptedData createSecret(String accountId, HarnessSecret secret, boolean validateScopes) {
    SecretManagerConfig secretManagerConfig =
        secretManagerConfigService.getSecretManager(accountId, secret.getKmsId(), null, secret.getRuntimeParameters());
    secret.setKmsId(secretManagerConfig.getUuid());
    secretValidatorsRegistry.getSecretValidator(secretManagerConfig.getEncryptionType())
        .validateSecret(accountId, secret, secretManagerConfig);
    if (validateScopes) {
      SecretScopeMetadata secretScopeMetadata = SecretScopeMetadata.builder()
                                                    .secretScopes(secret)
                                                    .inheritScopesFromSM(secret.isInheritScopesFromSM())
                                                    .secretsManagerScopes(secretManagerConfig)
                                                    .build();
      secretsRBACService.canSetPermissions(accountId, secretScopeMetadata);
    }
    EncryptedDataBuilder encryptedDataBuilder = EncryptedData.builder()
                                                    .accountId(accountId)
                                                    .kmsId(secretManagerConfig.getUuid())
                                                    .encryptionType(secretManagerConfig.getEncryptionType())
                                                    .name(secret.getName())
                                                    .enabled(true)
                                                    .searchTags(new HashMap<>())
                                                    .hideFromListing(secret.isHideFromListing())
                                                    .scopedToAccount(secret.isScopedToAccount())
                                                    .usageRestrictions(secret.getUsageRestrictions())
                                                    .additionalMetadata(secret.getAdditionalMetadata())
                                                    .inheritScopesFromSM(secret.isInheritScopesFromSM());

    EncryptedData encryptedData;
    if (secret instanceof SecretText) {
      encryptedData = buildSecretText(accountId, (SecretText) secret, secretManagerConfig, encryptedDataBuilder);
    } else {
      encryptedData = buildSecretFile(accountId, (SecretFile) secret, secretManagerConfig, encryptedDataBuilder);
    }
    encryptedData.addSearchTag(encryptedData.getName());
    try {
      String encryptedDataId = secretsDao.saveSecret(encryptedData);
      encryptedData.setUuid(encryptedDataId);
      secretsAuditService.logSecretCreateEvent(encryptedData);
      return secretsDao.getSecretById(accountId, encryptedDataId).<SecretManagementException>orElseThrow(() -> {
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Failed to save secret please try again.", USER);
      });
    } catch (DuplicateKeyException e) {
      String reason = "Secret with " + encryptedData.getName() + " already exists";
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, reason, e, USER);
    }
  }
  @Override
  public EncryptedData encryptSecret(String accountId, HarnessSecret secret, boolean validateScopes) {
    String kmsId = (null != secret && isNotEmpty(secret.getKmsId())) ? secret.getKmsId() : accountId;
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getSecretManager(accountId, kmsId);
    secret.setKmsId(secretManagerConfig.getUuid());
    secretValidatorsRegistry.getSecretValidator(secretManagerConfig.getEncryptionType())
        .validateSecret(accountId, secret, secretManagerConfig);
    if (validateScopes) {
      SecretScopeMetadata secretScopeMetadata = SecretScopeMetadata.builder()
                                                    .secretScopes(secret)
                                                    .inheritScopesFromSM(secret.isInheritScopesFromSM())
                                                    .secretsManagerScopes(secretManagerConfig)
                                                    .build();
      secretsRBACService.canSetPermissions(accountId, secretScopeMetadata);
    }
    EncryptedDataBuilder encryptedDataBuilder = EncryptedData.builder()
                                                    .accountId(accountId)
                                                    .kmsId(secretManagerConfig.getUuid())
                                                    .encryptionType(secretManagerConfig.getEncryptionType())
                                                    .name(secret.getName())
                                                    .enabled(true)
                                                    .searchTags(new HashMap<>())
                                                    .hideFromListing(secret.isHideFromListing())
                                                    .scopedToAccount(secret.isScopedToAccount())
                                                    .usageRestrictions(secret.getUsageRestrictions())
                                                    .additionalMetadata(secret.getAdditionalMetadata())
                                                    .inheritScopesFromSM(secret.isInheritScopesFromSM());

    EncryptedData encryptedData;
    if (secret instanceof SecretText) {
      encryptedData = buildSecretText(accountId, (SecretText) secret, secretManagerConfig, encryptedDataBuilder);
    } else {
      encryptedData = buildSecretFile(accountId, (SecretFile) secret, secretManagerConfig, encryptedDataBuilder);
    }
    encryptedData.addSearchTag(encryptedData.getName());
    secretsAuditService.logSecretCreateEvent(encryptedData);
    return encryptedData;
  }

  @Override
  public boolean updateSecret(String accountId, HarnessSecret secret, String existingRecordId, boolean validateScopes) {
    EncryptedData existingRecord =
        secretsDao.getSecretById(accountId, existingRecordId).<SecretManagementException>orElseThrow(() -> {
          throw new SecretManagementException(RESOURCE_NOT_FOUND,
              format("Could not find secret with id %s in account %s", existingRecordId, accountId), USER);
        });
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getSecretManager(
        accountId, existingRecord.getKmsId(), existingRecord.getEncryptionType(), secret.getRuntimeParameters());
    secret.setKmsId(secretManagerConfig.getUuid());
    EncryptedData oldRecord = kryoSerializer.clone(existingRecord);
    secretValidatorsRegistry.getSecretValidator(secretManagerConfig.getEncryptionType())
        .validateSecretUpdate(secret, existingRecord, secretManagerConfig);
    if (validateScopes) {
      SecretScopeMetadata oldScopeMetadata = SecretScopeMetadata.builder()
                                                 .secretId(existingRecordId)
                                                 .secretScopes(existingRecord)
                                                 .secretsManagerScopes(secretManagerConfig)
                                                 .inheritScopesFromSM(existingRecord.isInheritScopesFromSM())
                                                 .build();
      SecretScopeMetadata newScopeMetadata = SecretScopeMetadata.builder()
                                                 .secretId(existingRecordId)
                                                 .secretScopes(secret)
                                                 .secretsManagerScopes(secretManagerConfig)
                                                 .inheritScopesFromSM(secret.isInheritScopesFromSM())
                                                 .build();
      secretsRBACService.canReplacePermissions(accountId, newScopeMetadata, oldScopeMetadata, true);
    }
    SecretUpdateData secretUpdateData = new SecretUpdateData(secret, existingRecord);
    EncryptedRecord updatedEncryptedData;
    if (secret instanceof SecretText) {
      updatedEncryptedData = updateSecretText(accountId, secretUpdateData, secretManagerConfig);
    } else {
      updatedEncryptedData = updateSecretFile(accountId, secretUpdateData, secretManagerConfig);
    }
    try {
      EncryptedData updatedRecord = secretsDao.updateSecret(secretUpdateData, updatedEncryptedData);
      secretsAuditService.logSecretUpdateEvent(oldRecord, updatedRecord, secretUpdateData);
      return updatedRecord != null;
    } catch (DuplicateKeyException e) {
      String reason = "Secret with name " + secret.getName() + " already exists";
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, reason, e, USER);
    }
  }

  @Override
  public boolean updateSecretScopes(String accountId, String existingRecordId, UsageRestrictions usageRestrictions,
      boolean scopedToAccount, boolean inheritScopesFromSM) {
    EncryptedData existingRecord =
        secretsDao.getSecretById(accountId, existingRecordId).<SecretManagementException>orElseThrow(() -> {
          throw new SecretManagementException(RESOURCE_NOT_FOUND,
              format("Could not find secret with id %s in account %s", existingRecordId, accountId), USER);
        });
    EncryptedData oldRecord = kryoSerializer.clone(existingRecord);
    HarnessSecret harnessSecret;
    if (inheritScopesFromSM) {
      harnessSecret = HarnessSecret.builder()
                          .name(existingRecord.getName())
                          .kmsId(existingRecord.getKmsId())
                          .usageRestrictions(null)
                          .scopedToAccount(false)
                          .inheritScopesFromSM(true)
                          .build();
    } else {
      harnessSecret = HarnessSecret.builder()
                          .name(existingRecord.getName())
                          .kmsId(existingRecord.getKmsId())
                          .usageRestrictions(usageRestrictions)
                          .scopedToAccount(scopedToAccount)
                          .inheritScopesFromSM(false)
                          .build();
    }
    SecretUpdateData secretUpdateData = new SecretUpdateData(harnessSecret, existingRecord);
    EncryptedData updatedRecord = secretsDao.updateSecret(secretUpdateData, null);
    secretsAuditService.logSecretUpdateEvent(oldRecord, updatedRecord, secretUpdateData);
    return updatedRecord != null;
  }

  @Override
  public void updateConflictingSecretsToInheritScopes(String accountId, SecretManagerConfig secretManagerConfig) {
    try (HIterator<EncryptedData> iterator =
             new HIterator<>(secretsDao.listSecretsBySecretManager(accountId, secretManagerConfig.getUuid(), false))) {
      for (EncryptedData encryptedData : iterator) {
        if (secretsRBACService.isScopeInConflict(encryptedData, secretManagerConfig)) {
          updateSecretScopes(accountId, encryptedData.getUuid(), null, false, true);
        }
      }
    }
  }

  @Override
  public boolean deleteSecret(
      String accountId, String secretRecordId, boolean validateScopes, Map<String, String> runtimeParameters) {
    EncryptedData existingRecord =
        secretsDao.getSecretById(accountId, secretRecordId).<SecretManagementException>orElseThrow(() -> {
          throw new SecretManagementException(RESOURCE_NOT_FOUND,
              format("Could not find secret with id %s in account %s", secretRecordId, accountId), USER);
        });
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getSecretManager(
        accountId, existingRecord.getKmsId(), existingRecord.getEncryptionType(), runtimeParameters);
    if (validateScopes) {
      SecretScopeMetadata secretScopeMetadata = SecretScopeMetadata.builder()
                                                    .secretsManagerScopes(secretManagerConfig)
                                                    .inheritScopesFromSM(existingRecord.isInheritScopesFromSM())
                                                    .secretScopes(existingRecord)
                                                    .secretId(existingRecord.getUuid())
                                                    .build();
      if (!secretsRBACService.hasAccessToEditSecret(accountId, secretScopeMetadata)) {
        throw new SecretManagementException(
            USER_NOT_AUTHORIZED_DUE_TO_USAGE_RESTRICTIONS, "Does not have permission to delete the secret", USER);
      }
    }
    Set<SecretSetupUsage> secretSetupUsages = secretSetupUsageService.getSecretUsage(accountId, secretRecordId);
    if (!secretSetupUsages.isEmpty()) {
      String reason = "Can not delete secret because it is still being used. See setup usage(s) of the secret.";
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, reason, USER);
    }
    String errorMessage = format(
        "Deleting secret %s from %s failed unexpectedly.", existingRecord.getName(), secretManagerConfig.getName());

    if (secretManagerConfig.getType().equals(VAULT) && existingRecord.isInlineSecret()) {
      boolean isDeleted = deleteSecret(accountId, existingRecord, secretManagerConfig);
      if (!isDeleted) {
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
            errorMessage.concat("Please check your secret manager configuration and try again."), USER);
      }
    } else if (existingRecord.getType() == CONFIG_FILE && secretManagerConfig.getType().equals(KMS)) {
      secretsFileService.deleteFile(existingRecord.getEncryptedValue());
    }

    boolean isDeleted = secretsDao.deleteSecret(accountId, existingRecord.getUuid());
    if (!isDeleted) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, errorMessage, USER);
    }
    secretsAuditService.logSecretDeleteEvent(existingRecord);
    return true;
  }

  private EncryptedData buildSecretText(String accountId, SecretText secretText,
      SecretManagerConfig secretManagerConfig, EncryptedDataBuilder encryptedDataBuilder) {
    SecretManagerType secretManagerType = secretManagerConfig.getType();
    if (secretText.isInlineSecret()) {
      EncryptedRecord encryptedRecord;
      if (secretManagerType.equals(KMS)) {
        encryptedRecord = encryptSecret(accountId, secretText.getValue(), secretManagerConfig);
      } else {
        encryptedRecord = upsertSecret(accountId, secretText, null, secretManagerConfig);
      }
      encryptedDataBuilder.encryptionKey(encryptedRecord.getEncryptionKey())
          .encryptedValue(encryptedRecord.getEncryptedValue());
    } else if (secretText.isReferencedSecret()) {
      validateReference(accountId, secretText, secretManagerConfig);
      encryptedDataBuilder.path(secretText.getPath());
    } else if (secretManagerType.equals(CUSTOM)) {
      validateParameters(accountId, secretText.getParameters(), secretManagerConfig);
      encryptedDataBuilder.parameters(secretText.getParameters());
    }
    return encryptedDataBuilder.type(SECRET_TEXT).build();
  }

  private EncryptedData buildSecretFile(String accountId, SecretFile secretFile,
      SecretManagerConfig secretManagerConfig, EncryptedDataBuilder encryptedDataBuilder) {
    SecretManagerType secretManagerType = secretManagerConfig.getType();
    String fileContent =
        new String(CHARSET.decode(ByteBuffer.wrap(encodeBase64ToByteArray(secretFile.getFileContent()))).array());
    EncryptedRecord encryptedRecord;
    if (secretManagerType.equals(KMS)) {
      encryptedRecord = encryptSecret(accountId, fileContent, secretManagerConfig);
      String encryptedFileId =
          secretsFileService.createFile(secretFile.getName(), accountId, encryptedRecord.getEncryptedValue());
      encryptedRecord = EncryptedRecordData.builder()
                            .encryptedValue(encryptedFileId.toCharArray())
                            .encryptionKey(encryptedRecord.getEncryptionKey())
                            .additionalMetadata(secretFile.getAdditionalMetadata())
                            .build();
    } else {
      //   encryptedRecord = upsertSecret(accountId, secretFile.getName(), fileContent, null, secretManagerConfig);
      encryptedRecord = upsertSecret(accountId,
          SecretText.builder()
              .name(secretFile.getName())
              .additionalMetadata(secretFile.getAdditionalMetadata())
              .value(fileContent)
              .build(),
          null, secretManagerConfig);
    }
    return encryptedDataBuilder.type(CONFIG_FILE)
        .base64Encoded(true)
        .fileSize(secretFile.getFileContent().length)
        .encryptionKey(encryptedRecord.getEncryptionKey())
        .encryptedValue(encryptedRecord.getEncryptedValue())
        .build();
  }

  private EncryptedRecord updateSecretText(
      String accountId, SecretUpdateData secretUpdateData, SecretManagerConfig secretManagerConfig) {
    SecretManagerType secretManagerType = secretManagerConfig.getType();
    SecretText secretText = (SecretText) secretUpdateData.getUpdatedSecret();
    EncryptedData existingRecord = secretUpdateData.getExistingRecord();
    EncryptedRecord updatedEncryptedData = null;
    if (secretUpdateData.shouldRencryptUsingKms(secretManagerType)) {
      updatedEncryptedData = encryptSecret(accountId, secretText.getValue(), secretManagerConfig);
    } else if (secretUpdateData.shouldRencryptUsingVault(secretManagerType)) {
      updatedEncryptedData = upsertSecret(accountId, secretText, existingRecord, secretManagerConfig);
    } else if (secretUpdateData.validateReferenceUsingVault(secretManagerType)) {
      validateReference(accountId, secretText, secretManagerConfig);
      if (existingRecord.isInlineSecret()) {
        deleteSecret(accountId, existingRecord, secretManagerConfig);
      }
    } else if (secretUpdateData.validateCustomReference(secretManagerType)) {
      validateParameters(accountId, secretText.getParameters(), secretManagerConfig);
    }
    return updatedEncryptedData;
  }

  private EncryptedRecord updateSecretFile(
      String accountId, SecretUpdateData secretUpdateData, SecretManagerConfig secretManagerConfig) {
    SecretManagerType secretManagerType = secretManagerConfig.getType();
    SecretFile secretFile = (SecretFile) secretUpdateData.getUpdatedSecret();
    EncryptedData existingRecord = secretUpdateData.getExistingRecord();
    EncryptedRecord updatedEncryptedData = null;
    String updatedFileContent = isEmpty(secretFile.getFileContent())
        ? null
        : new String(CHARSET.decode(ByteBuffer.wrap(encodeBase64ToByteArray(secretFile.getFileContent()))).array());
    if (secretUpdateData.shouldRencryptUsingKms(secretManagerType)) {
      updatedEncryptedData = encryptSecret(accountId, updatedFileContent, secretManagerConfig);
      String encryptedFileId = secretsFileService.createFile(
          secretFile.getName(), existingRecord.getAccountId(), updatedEncryptedData.getEncryptedValue());
      updatedEncryptedData = EncryptedRecordData.builder()
                                 .encryptedValue(encryptedFileId.toCharArray())
                                 .encryptionKey(updatedEncryptedData.getEncryptionKey())
                                 .build();
      secretsFileService.deleteFile(existingRecord.getEncryptedValue());
    } else if (secretUpdateData.shouldRencryptUsingVault(secretManagerType)) {
      /*updatedEncryptedData = upsertSecret(accountId, secretUpdateData.getUpdatedSecret().getName(),
         updatedFileContent, existingRecord, secretManagerConfig);*/
      updatedEncryptedData = upsertSecret(accountId,
          SecretText.builder().name(secretUpdateData.getUpdatedSecret().getName()).value(updatedFileContent).build(),
          existingRecord, secretManagerConfig);
    }
    return updatedEncryptedData;
  }

  private EncryptedRecord encryptSecret(String accountId, String value, SecretManagerConfig secretManagerConfig) {
    KmsEncryptor kmsEncryptor = kmsRegistry.getKmsEncryptor(secretManagerConfig);
    EncryptedRecord encryptedRecord = kmsEncryptor.encryptSecret(accountId, value, secretManagerConfig);
    validateEncryptedData(encryptedRecord);
    return encryptedRecord;
  }

  private EncryptedRecord upsertSecret(String accountId, SecretText secretText, EncryptedRecord existingRecord,
      SecretManagerConfig secretManagerConfig) {
    VaultEncryptor vaultEncryptor = vaultRegistry.getVaultEncryptor(secretManagerConfig.getEncryptionType());
    EncryptedRecord encryptedRecord;
    if (existingRecord == null) {
      encryptedRecord = vaultEncryptor.createSecret(accountId, secretText, secretManagerConfig);
    } else if (secretText.getValue() != null && !secretText.getValue().equals(SECRET_MASK)) {
      encryptedRecord = vaultEncryptor.updateSecret(accountId, secretText, existingRecord, secretManagerConfig);
    } else {
      encryptedRecord = vaultEncryptor.renameSecret(accountId, secretText, existingRecord, secretManagerConfig);
    }
    validateEncryptedData(encryptedRecord);
    return encryptedRecord;
  }

  private void validateReference(String accountId, SecretText secretText, SecretManagerConfig secretManagerConfig) {
    VaultEncryptor vaultEncryptor = vaultRegistry.getVaultEncryptor(secretManagerConfig.getEncryptionType());
    boolean isValidReference = vaultEncryptor.validateReference(accountId, secretText, secretManagerConfig);
    if (!isValidReference) {
      String message = format("Could not find the secret at the path %s or the secret was empty", secretText.getPath());
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }
  }

  private void validateParameters(
      String accountId, Set<EncryptedDataParams> parameters, SecretManagerConfig secretManagerConfig) {
    CustomEncryptor customEncryptor = customRegistry.getCustomEncryptor(secretManagerConfig.getEncryptionType());
    boolean isValidReference = customEncryptor.validateReference(accountId, parameters, secretManagerConfig);
    if (!isValidReference) {
      String message =
          format("Could not find any secret with the parameters %s or the secret fetched was empty", parameters);
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }
  }

  private char[] fetchSecretValue(
      String accountId, EncryptedData encryptedData, SecretManagerConfig secretManagerConfig) {
    char[] value;
    EncryptedData encryptedRecord = kryoSerializer.clone(encryptedData);
    if (encryptedData.getType() == CONFIG_FILE && secretManagerConfig.getType() == KMS) {
      char[] fileId = encryptedRecord.getEncryptedValue();
      encryptedRecord.setEncryptedValue(secretsFileService.getFileContents(String.valueOf(fileId)));
    }
    if (secretManagerConfig.getType() == KMS) {
      KmsEncryptor kmsEncryptor = kmsRegistry.getKmsEncryptor(secretManagerConfig);
      value = kmsEncryptor.fetchSecretValue(accountId, encryptedRecord, secretManagerConfig);
    } else if (secretManagerConfig.getType() == VAULT) {
      VaultEncryptor vaultEncryptor = vaultRegistry.getVaultEncryptor(secretManagerConfig.getEncryptionType());
      value = vaultEncryptor.fetchSecretValue(accountId, encryptedRecord, secretManagerConfig);
    } else {
      CustomEncryptor customEncryptor = customRegistry.getCustomEncryptor(secretManagerConfig.getEncryptionType());
      value = customEncryptor.fetchSecretValue(accountId, encryptedRecord, secretManagerConfig);
    }
    if (encryptedRecord.isBase64Encoded()) {
      byte[] decodedBytes = EncodingUtils.decodeBase64(value);
      value = CHARSET.decode(ByteBuffer.wrap(decodedBytes)).array();
    }
    if (isEmpty(value)) {
      String message = format("Empty or null value returned. Could not migrate secret %s", encryptedRecord.getName());
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }
    return value;
  }

  private boolean deleteSecret(
      String accountId, EncryptedRecord existingRecord, SecretManagerConfig secretManagerConfig) {
    VaultEncryptor vaultEncryptor = vaultRegistry.getVaultEncryptor(secretManagerConfig.getEncryptionType());
    return vaultEncryptor.deleteSecret(accountId, existingRecord, secretManagerConfig);
  }

  private void validateEncryptedData(EncryptedRecord encryptedRecord) {
    if (encryptedRecord == null || isEmpty(encryptedRecord.getEncryptionKey())
        || isEmpty(encryptedRecord.getEncryptedValue())) {
      String message =
          "Encryption of secret failed unexpectedly. Please check your secret manager configuration and try again.";
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }
  }

  @Override
  public boolean migrateSecrets(
      String accountId, SecretManagerConfig fromSecretManagerConfig, SecretManagerConfig toSecretManagerConfig) {
    try (HIterator<EncryptedData> iterator = new HIterator<>(
             secretsDao.listSecretsBySecretManager(accountId, fromSecretManagerConfig.getUuid(), false))) {
      for (EncryptedData dataToTransition : iterator) {
        secretsMigrationProcessor.send(MigrateSecretTask.builder()
                                           .accountId(accountId)
                                           .secretId(dataToTransition.getUuid())
                                           .fromConfig(fromSecretManagerConfig)
                                           .toConfig(toSecretManagerConfig)
                                           .build());
      }
    }
    return true;
  }

  @Override
  public void migrateSecret(MigrateSecretTask migrateSecretTask) {
    EncryptedData encryptedData =
        secretsDao.getSecretById(migrateSecretTask.getAccountId(), migrateSecretTask.getSecretId())
            .<SecretManagementException>orElseThrow(() -> {
              String message = String.format(
                  "Could not find the secret with secret id %s to migrate", migrateSecretTask.getSecretId());
              throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
            });
    if (!encryptedData.isInlineSecret()) {
      log.info("Secret in account {} with id {} cannot be migrated as it is not an inline secret",
          migrateSecretTask.getAccountId(), migrateSecretTask.getSecretId());
      return;
    }
    String value = String.valueOf(
        fetchSecretValue(migrateSecretTask.getAccountId(), encryptedData, migrateSecretTask.getFromConfig()));

    SecretManagerConfig toConfig = migrateSecretTask.getToConfig();
    EncryptedDataBuilder encryptedDataBuilder = EncryptedData.builder();
    EncryptedData updatedEncryptedData;
    if (encryptedData.getType() == CONFIG_FILE) {
      byte[] bytesToEncrypt = value.getBytes(CHARSET);
      SecretFile secretFile = SecretFile.builder().fileContent(bytesToEncrypt).name(encryptedData.getName()).build();
      updatedEncryptedData = buildSecretFile(encryptedData.getAccountId(), secretFile, toConfig, encryptedDataBuilder);
    } else {
      SecretText secretText = SecretText.builder().value(value).name(encryptedData.getName()).build();
      updatedEncryptedData = buildSecretText(encryptedData.getAccountId(), secretText, toConfig, encryptedDataBuilder);
    }
    updatedEncryptedData.setKmsId(toConfig.getUuid());
    updatedEncryptedData.setEncryptionType(toConfig.getEncryptionType());

    if (migrateSecretTask.getFromConfig().getType().equals(VAULT)) {
      deleteSecret(migrateSecretTask.getAccountId(), encryptedData, migrateSecretTask.getFromConfig());
    } else if (encryptedData.getType() == CONFIG_FILE && migrateSecretTask.getFromConfig().getType().equals(KMS)) {
      secretsFileService.deleteFile(encryptedData.getEncryptedValue());
    }
    secretsDao.migrateSecret(encryptedData.getAccountId(), encryptedData.getUuid(), updatedEncryptedData);
  }

  @Override
  public char[] fetchSecretValue(EncryptedData encryptedData) {
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getSecretManager(
        encryptedData.getAccountId(), encryptedData.getKmsId(), encryptedData.getEncryptionType());
    return fetchSecretValue(encryptedData.getAccountId(), encryptedData, secretManagerConfig);
  }

  @Override
  public Optional<EncryptedData> getSecretById(String accountId, String secretRecordId) {
    Optional<EncryptedData> encryptedDataOptional = secretsDao.getSecretById(accountId, secretRecordId);
    if (encryptedDataOptional.isPresent()) {
      EncryptedData encryptedData = encryptedDataOptional.get();
      SecretManagerConfig secretManagerConfig = secretManagerConfigService.getSecretManager(
          accountId, encryptedData.getKmsId(), encryptedData.getEncryptionType());
      SecretScopeMetadata secretScopeMetadata = SecretScopeMetadata.builder()
                                                    .secretId(secretRecordId)
                                                    .secretScopes(encryptedData)
                                                    .inheritScopesFromSM(encryptedData.isInheritScopesFromSM())
                                                    .secretsManagerScopes(secretManagerConfig)
                                                    .build();
      if (secretsRBACService.hasAccessToReadSecret(accountId, secretScopeMetadata, null, null)) {
        return encryptedDataOptional;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<EncryptedData> getSecretById(String accountId, String secretRecordId, String appId, String envId) {
    Optional<EncryptedData> encryptedDataOptional = secretsDao.getSecretById(accountId, secretRecordId);
    if (encryptedDataOptional.isPresent()) {
      EncryptedData encryptedData = encryptedDataOptional.get();
      SecretManagerConfig secretManagerConfig = secretManagerConfigService.getSecretManager(
          accountId, encryptedData.getKmsId(), encryptedData.getEncryptionType());
      SecretScopeMetadata secretScopeMetadata = SecretScopeMetadata.builder()
                                                    .secretId(secretRecordId)
                                                    .secretScopes(encryptedData)
                                                    .inheritScopesFromSM(encryptedData.isInheritScopesFromSM())
                                                    .secretsManagerScopes(secretManagerConfig)
                                                    .build();
      if (secretsRBACService.hasAccessToReadSecret(accountId, secretScopeMetadata, appId, envId)) {
        return encryptedDataOptional;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<EncryptedData> getAccountScopedSecretById(String accountId, String secretRecordId) {
    Optional<EncryptedData> encryptedData = secretsDao.getSecretById(accountId, secretRecordId);
    if (encryptedData.isPresent() && encryptedData.get().isScopedToAccount()) {
      return encryptedData;
    }
    return Optional.empty();
  }

  @Override
  public Optional<EncryptedData> getSecretByName(String accountId, String secretName) {
    Optional<EncryptedData> encryptedDataOptional = secretsDao.getSecretByName(accountId, secretName);
    if (encryptedDataOptional.isPresent()) {
      EncryptedData encryptedData = encryptedDataOptional.get();
      SecretManagerConfig secretManagerConfig = secretManagerConfigService.getSecretManager(
          accountId, encryptedData.getKmsId(), encryptedData.getEncryptionType());
      SecretScopeMetadata secretScopeMetadata = SecretScopeMetadata.builder()
                                                    .secretId(encryptedData.getUuid())
                                                    .secretScopes(encryptedData)
                                                    .inheritScopesFromSM(encryptedData.isInheritScopesFromSM())
                                                    .secretsManagerScopes(secretManagerConfig)
                                                    .build();
      if (secretsRBACService.hasAccessToReadSecret(accountId, secretScopeMetadata, null, null)) {
        return encryptedDataOptional;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<EncryptedData> getSecretByName(String accountId, String secretName, String appId, String envId) {
    Optional<EncryptedData> encryptedDataOptional = secretsDao.getSecretByName(accountId, secretName);
    if (encryptedDataOptional.isPresent()) {
      EncryptedData encryptedData = encryptedDataOptional.get();
      SecretManagerConfig secretManagerConfig = secretManagerConfigService.getSecretManager(
          accountId, encryptedData.getKmsId(), encryptedData.getEncryptionType());
      SecretScopeMetadata secretScopeMetadata = SecretScopeMetadata.builder()
                                                    .secretId(encryptedData.getUuid())
                                                    .secretScopes(encryptedData)
                                                    .inheritScopesFromSM(encryptedData.isInheritScopesFromSM())
                                                    .secretsManagerScopes(secretManagerConfig)
                                                    .build();
      if (secretsRBACService.hasAccessToReadSecret(accountId, secretScopeMetadata, appId, envId)) {
        return encryptedDataOptional;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<EncryptedData> getAccountScopedSecretByName(String accountId, String secretName) {
    Optional<EncryptedData> encryptedData = secretsDao.getSecretByName(accountId, secretName);
    if (encryptedData.isPresent() && encryptedData.get().isScopedToAccount()) {
      return encryptedData;
    }
    return Optional.empty();
  }

  @Override
  public Optional<EncryptedData> getSecretByKeyOrPath(
      String accountId, EncryptionType encryptionType, String encryptionKey, String path) {
    Optional<EncryptedData> encryptedDataOptional =
        secretsDao.getSecretByKeyOrPath(accountId, encryptionType, encryptionKey, path);
    if (encryptedDataOptional.isPresent()) {
      EncryptedData encryptedData = encryptedDataOptional.get();
      SecretManagerConfig secretManagerConfig = secretManagerConfigService.getSecretManager(
          accountId, encryptedData.getKmsId(), encryptedData.getEncryptionType());
      SecretScopeMetadata secretScopeMetadata = SecretScopeMetadata.builder()
                                                    .secretId(encryptedData.getUuid())
                                                    .secretScopes(encryptedData)
                                                    .inheritScopesFromSM(encryptedData.isInheritScopesFromSM())
                                                    .secretsManagerScopes(secretManagerConfig)
                                                    .build();
      if (secretsRBACService.hasAccessToReadSecret(accountId, secretScopeMetadata, null, null)) {
        return encryptedDataOptional;
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean hasAccessToReadSecrets(String accountId, Set<String> secretIds, String appId, String envId) {
    Set<SecretScopeMetadata> secretScopeMetadataSet = new HashSet<>();
    buildSecretScopeMetadataSet(accountId, secretIds, secretScopeMetadataSet);
    return secretsRBACService.hasAccessToReadSecrets(accountId, secretScopeMetadataSet, appId, envId);
  }

  @Override
  public List<SecretMetadata> filterSecretIdsByReadPermission(Set<String> secretIds, String accountId,
      String appIdFromRequest, String envIdFromRequest, boolean forUsageInNewApp) {
    Set<SecretScopeMetadata> secretScopeMetadataSet = new HashSet<>();
    buildSecretScopeMetadataSet(accountId, secretIds, secretScopeMetadataSet);
    Set<String> foundInDatabase =
        secretScopeMetadataSet.stream().map(SecretScopeMetadata::getSecretId).collect(Collectors.toSet());
    // collect not available Secret Ids
    Set<String> notFoundInDatabase = new HashSet<>(secretIds);
    notFoundInDatabase.removeAll(foundInDatabase);
    // collect readable secret Ids
    Set<String> readableSecretIds =
        secretsRBACService
            .filterSecretsByReadPermission(accountId, new ArrayList<>(secretScopeMetadataSet), appIdFromRequest,
                envIdFromRequest, forUsageInNewApp)
            .stream()
            .map(SecretScopeMetadata::getSecretId)
            .collect(Collectors.toSet());
    // collect not readable Secret Ids
    Set<String> notReadableSecretIds = new HashSet<>(secretIds);
    notReadableSecretIds.removeAll(notFoundInDatabase);
    notReadableSecretIds.removeAll(readableSecretIds);

    return buildAndGetResult(notFoundInDatabase, notReadableSecretIds, readableSecretIds);
  }

  @NotNull
  private List<SecretMetadata> buildAndGetResult(
      Set<String> notFoundInDatabase, Set<String> notReadableSecretIds, Set<String> readableSecretIds) {
    List<SecretMetadata> result = new ArrayList<>();
    result.addAll(notFoundInDatabase.stream()
                      .map(secretId -> {
                        return SecretMetadata.builder().secretId(secretId).secretState(SecretState.NOT_FOUND).build();
                      })
                      .collect(Collectors.toList()));
    result.addAll(notReadableSecretIds.stream()
                      .map(secretId -> {
                        return SecretMetadata.builder().secretId(secretId).secretState(SecretState.CANNOT_READ).build();
                      })
                      .collect(Collectors.toList()));
    result.addAll(readableSecretIds.stream()
                      .map(secretId -> {
                        return SecretMetadata.builder().secretId(secretId).secretState(SecretState.CAN_READ).build();
                      })
                      .collect(Collectors.toList()));
    return result;
  }

  @Override
  public boolean hasAccessToEditSecrets(String accountId, Set<String> secretIds) {
    Set<SecretScopeMetadata> secretScopeMetadataSet = new HashSet<>();
    buildSecretScopeMetadataSet(accountId, secretIds, secretScopeMetadataSet);
    return secretsRBACService.hasAccessToEditSecrets(accountId, secretScopeMetadataSet);
  }

  @Override
  public PageResponse<EncryptedData> listSecrets(
      String accountId, PageRequest<EncryptedData> pageRequest, String appId, String envId) {
    List<EncryptedData> filteredEncryptedDataList = Lists.newArrayList();

    try {
      PageRequest<EncryptedData> copiedPageRequest = pageRequest.copy();
      int offset = copiedPageRequest.getStart();
      int limit = copiedPageRequest.getPageSize();

      copiedPageRequest.setOffset("0");
      copiedPageRequest.setLimit(String.valueOf(PageRequest.DEFAULT_UNLIMITED));
      PageResponse<EncryptedData> batchPageResponse = secretsDao.listSecrets(copiedPageRequest);
      List<EncryptedData> encryptedDataList = batchPageResponse.getResponse();
      filterSecreteDataBasedOnUsageRestrictions(accountId, appId, envId, encryptedDataList, filteredEncryptedDataList);
      List<EncryptedData> response;
      if (isNotEmpty(filteredEncryptedDataList) && filteredEncryptedDataList.size() > offset) {
        int endIdx = Math.min(offset + limit, filteredEncryptedDataList.size());
        response = filteredEncryptedDataList.subList(offset, endIdx);
      } else {
        response = Collections.emptyList();
      }

      return aPageResponse()
          .withResponse(response)
          .withTotal(filteredEncryptedDataList.size())
          .withOffset(pageRequest.getOffset())
          .withLimit(pageRequest.getLimit())
          .build();
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private void buildSecretScopeMetadataSet(
      String accountId, Set<String> secretIds, Set<SecretScopeMetadata> secretScopeMetadataSet) {
    if (isEmpty(secretIds)) {
      return;
    }
    Map<String, SecretManagerConfig> secretManagerConfigCache = new HashMap<>();
    String secretManagerCacheKey = "%s.%s";

    long secretsCount = secretIds.size();
    long batches = (secretsCount + 100) / 101;
    long slow = Duration.ofMillis(secretsCount + 500 * (batches + 1)).toMillis();
    long dangerouslySlow = Duration.ofMillis(slow * 3).toMillis();

    try (HIterator<EncryptedData> iterator =
             new HIterator<>(secretsDao.listSecretsBySecretIds(accountId, secretIds), slow, dangerouslySlow)) {
      while (iterator.hasNext()) {
        EncryptedData encryptedData = iterator.next();
        SecretManagerConfig secretManagerConfig =
            secretManagerConfigCache.get(String.format(secretManagerCacheKey, accountId, encryptedData.getKmsId()));
        if (secretManagerConfig == null) {
          secretManagerConfig = secretManagerConfigService.getSecretManager(
              accountId, encryptedData.getKmsId(), encryptedData.getEncryptionType());
          secretManagerConfigCache.put(
              String.format(secretManagerCacheKey, accountId, encryptedData.getKmsId()), secretManagerConfig);
        }
        secretScopeMetadataSet.add(SecretScopeMetadata.builder()
                                       .secretId(encryptedData.getUuid())
                                       .secretScopes(encryptedData)
                                       .inheritScopesFromSM(encryptedData.isInheritScopesFromSM())
                                       .secretsManagerScopes(secretManagerConfig)
                                       .build());
      }
    }
  }

  @Override
  public PageResponse<EncryptedData> listSecretsScopedToAccount(
      String accountId, PageRequest<EncryptedData> pageRequest) {
    boolean hasAccessToAccountScopedSecrets = secretsRBACService.hasAccessToAccountScopedSecrets(accountId);

    if (!hasAccessToAccountScopedSecrets) {
      return aPageResponse().withResponse(Collections.emptyList()).build();
    }

    pageRequest.addFilter(EncryptedDataKeys.usageRestrictions, SearchFilter.Operator.NOT_EXISTS);

    PageResponse<EncryptedData> pageResponse = secretsDao.listSecrets(pageRequest);
    List<EncryptedData> encryptedDataList = pageResponse.getResponse();

    for (EncryptedData encryptedData : encryptedDataList) {
      encryptedData.setEncryptedValue(SECRET_MASK.toCharArray());
      encryptedData.setEncryptionKey(SECRET_MASK);
      encryptedData.setBackupEncryptedValue(SECRET_MASK.toCharArray());
      encryptedData.setBackupEncryptionKey(SECRET_MASK);
    }

    pageResponse.setResponse(encryptedDataList);
    pageResponse.setTotal((long) encryptedDataList.size());
    return pageResponse;
  }

  private void filterSecreteDataBasedOnUsageRestrictions(String accountId, String appIdFromRequest,
      String envIdFromRequest, List<EncryptedData> encryptedDataList, List<EncryptedData> filteredEncryptedDataList) {
    List<SecretScopeMetadata> secretScopeMetadataList =
        encryptedDataList.stream()
            .map(encryptedData -> {
              SecretManagerConfig secretManagerConfig = secretManagerConfigService.getSecretManager(
                  accountId, encryptedData.getKmsId(), encryptedData.getEncryptionType());
              return SecretScopeMetadata.builder()
                  .secretId(encryptedData.getUuid())
                  .secretScopes(encryptedData)
                  .inheritScopesFromSM(encryptedData.isInheritScopesFromSM())
                  .secretsManagerScopes(secretManagerConfig)
                  .build();
            })
            .collect(Collectors.toList());

    List<SecretScopeMetadata> filteredEntities = secretsRBACService.filterSecretsByReadPermission(
        accountId, secretScopeMetadataList, appIdFromRequest, envIdFromRequest, false);

    filteredEntities.forEach(filteredEntity -> {
      EncryptedData encryptedData = (EncryptedData) filteredEntity.getSecretScopes();
      encryptedData.setEncryptedValue(SECRET_MASK.toCharArray());
      encryptedData.setEncryptionKey(SECRET_MASK);
      encryptedData.setBackupEncryptedValue(SECRET_MASK.toCharArray());
      encryptedData.setBackupEncryptionKey(SECRET_MASK);
      filteredEncryptedDataList.add(encryptedData);
    });
  }
}
