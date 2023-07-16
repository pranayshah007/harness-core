/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.encoding.EncodingUtils.encodeBase64ToByteArray;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.encryption.SecretRefParsedData.SECRET_REFERENCE_DATA_ROOT_PREFIX;
import static io.harness.encryption.SecretRefParsedData.SECRET_REFERENCE_EXPRESSION_DELIMITER;
import static io.harness.eraro.ErrorCode.ENCRYPT_DECRYPT_ERROR;
import static io.harness.eraro.ErrorCode.INVALID_REQUEST;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.SRE;
import static io.harness.exception.WingsException.USER;
import static io.harness.helpers.GlobalSecretManagerUtils.isNgHarnessSecretManager;
import static io.harness.secretmanagerclient.SecretType.SecretFile;
import static io.harness.secretmanagerclient.SecretType.SecretText;
import static io.harness.secretmanagerclient.ValueType.CustomSecretManagerValues;
import static io.harness.secretmanagerclient.ValueType.Inline;
import static io.harness.secretmanagerclient.ValueType.Reference;
import static io.harness.security.SimpleEncryption.CHARSET;
import static io.harness.security.encryption.AccessType.APP_ROLE;
import static io.harness.security.encryption.EncryptionType.GCP_KMS;
import static io.harness.security.encryption.EncryptionType.LOCAL;
import static io.harness.security.encryption.SecretManagerType.KMS;
import static io.harness.security.encryption.SecretManagerType.VAULT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DecryptedSecretValue;
import io.harness.beans.FeatureName;
import io.harness.beans.SecretManagerConfig;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.helper.CustomSecretManagerHelper;
import io.harness.connector.services.NGConnectorSecretManagerService;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerConnectorDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefParsedData;
import io.harness.encryptors.CustomEncryptorsRegistry;
import io.harness.encryptors.KmsEncryptorsRegistry;
import io.harness.encryptors.VaultEncryptorsRegistry;
import io.harness.exception.DelegateNotAvailableException;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.DocumentLinksConstants;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.core.AdditionalMetadataValidationHelper;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.dao.NGEncryptedDataDao;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.entities.NGEncryptedData;
import io.harness.ng.core.entities.NGEncryptedData.NGEncryptedDataBuilder;
import io.harness.pms.yaml.YamlUtils;
import io.harness.secretmanagerclient.dto.CustomSecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.LocalConfigDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.VaultConfigDTO;
import io.harness.secretmanagerclient.remote.SecretManagerClient;
import io.harness.secrets.SecretsFileService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;
import io.harness.security.encryption.SecretManagerType;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import software.wings.beans.BaseVaultConfig;
import software.wings.beans.CustomSecretNGManagerConfig;
import software.wings.beans.NameValuePairWithDefault;
import software.wings.service.impl.security.GlobalEncryptDecryptClient;
import software.wings.service.impl.security.NGEncryptorService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Singleton
@Slf4j
public class NGEncryptedDataServiceImpl implements NGEncryptedDataService {
  private static final Set<EncryptionType> ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD =
      EnumSet.of(LOCAL, GCP_KMS, EncryptionType.KMS);
  private static final String READ_ONLY_SECRET_MANAGER_ERROR =
      "Cannot create an Inline secret in read only secret manager";
  private final NGEncryptedDataDao encryptedDataDao;
  private final KmsEncryptorsRegistry kmsEncryptorsRegistry;
  private final VaultEncryptorsRegistry vaultEncryptorsRegistry;
  private final SecretsFileService secretsFileService;
  private final GlobalEncryptDecryptClient globalEncryptDecryptClient;
  private final NGConnectorSecretManagerService ngConnectorSecretManagerService;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;
  private final CustomEncryptorsRegistry customEncryptorsRegistry;
  private final CustomSecretManagerHelper customSecretManagerHelper;
  private final NGEncryptorService ngEncryptorService;
  private final AdditionalMetadataValidationHelper additionalMetadataValidationHelper;
  private final DynamicSecretReferenceHelper dynamicSecretReferenceHelper;

  @Inject
  public NGEncryptedDataServiceImpl(NGEncryptedDataDao encryptedDataDao, KmsEncryptorsRegistry kmsEncryptorsRegistry,
      VaultEncryptorsRegistry vaultEncryptorsRegistry, SecretsFileService secretsFileService,
      SecretManagerClient secretManagerClient, GlobalEncryptDecryptClient globalEncryptDecryptClient,
      NGConnectorSecretManagerService ngConnectorSecretManagerService,
      NGFeatureFlagHelperService ngFeatureFlagHelperService, CustomEncryptorsRegistry customEncryptorsRegistry,
      CustomSecretManagerHelper customSecretManagerHelper, NGEncryptorService ngEncryptorService,
      AdditionalMetadataValidationHelper additionalMetadataValidationHelper,
      DynamicSecretReferenceHelper dynamicSecretReferenceHelper) {
    this.encryptedDataDao = encryptedDataDao;
    this.kmsEncryptorsRegistry = kmsEncryptorsRegistry;
    this.vaultEncryptorsRegistry = vaultEncryptorsRegistry;
    this.secretsFileService = secretsFileService;
    this.globalEncryptDecryptClient = globalEncryptDecryptClient;
    this.ngConnectorSecretManagerService = ngConnectorSecretManagerService;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
    this.customEncryptorsRegistry = customEncryptorsRegistry;
    this.customSecretManagerHelper = customSecretManagerHelper;
    this.ngEncryptorService = ngEncryptorService;
    this.additionalMetadataValidationHelper = additionalMetadataValidationHelper;
    this.dynamicSecretReferenceHelper = dynamicSecretReferenceHelper;
  }

  @Override
  public NGEncryptedData createSecretText(String accountIdentifier, SecretDTOV2 dto) {
    validateSecretDoesNotExist(
        accountIdentifier, dto.getOrgIdentifier(), dto.getProjectIdentifier(), dto.getIdentifier());
    SecretTextSpecDTO secret = (SecretTextSpecDTO) dto.getSpec();
    SecretManagerConfigDTO secretManager = getSecretManagerOrThrow(accountIdentifier, dto.getOrgIdentifier(),
        dto.getProjectIdentifier(), secret.getSecretManagerIdentifier(), false);

    validateAdditionalMetadata(secretManager, secret);
    NGEncryptedData encryptedData = buildNGEncryptedData(accountIdentifier, dto, secretManager);

    switch (secret.getValueType()) {
      case Inline:
        if (isReadOnlySecretManager(secretManager)) {
          throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, READ_ONLY_SECRET_MANAGER_ERROR, USER);
        }
        encrypt(encryptedData, secret.getValue(), SecretManagerConfigMapper.fromDTO(secretManager));
        break;
      case Reference:
        validatePath(encryptedData.getPath(), encryptedData.getEncryptionType());
        break;
      case CustomSecretManagerValues:
        break;
      default:
        throw new RuntimeException("Secret value type is unknown");
    }
    return encryptedDataDao.save(encryptedData);
  }

  private void validateAdditionalMetadata(SecretManagerConfigDTO secretManager, SecretSpecDTO secret) {
    switch (secretManager.getEncryptionType()) {
      case GCP_SECRETS_MANAGER:
        additionalMetadataValidationHelper.validateAdditionalMetadataForGcpSecretManager(secret);
        break;
      case AZURE_VAULT:
        additionalMetadataValidationHelper.validateAdditionalMetadataForAzureValue(secret);
        break;
      default:
        break;
    }
  }

  private void validateCustomSecretManagerPathValue(
      String accountIdentifier, NGEncryptedData encryptedData, SecretManagerConfigDTO secretManager, String value) {
    CustomSecretManagerConfigDTO customSecretManagerConfigDTO = (CustomSecretManagerConfigDTO) secretManager;
    ConnectorDTO connectorDTO = ngConnectorSecretManagerService.getConnectorDTO(
        customSecretManagerConfigDTO.getAccountIdentifier(), customSecretManagerConfigDTO.getOrgIdentifier(),
        customSecretManagerConfigDTO.getProjectIdentifier(), customSecretManagerConfigDTO.getIdentifier());
    CustomSecretManagerConnectorDTO customSecretManagerConnectorDTO =
        (CustomSecretManagerConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    Map<String, List<NameValuePairWithDefault>> inputValues = customSecretManagerHelper.mergeStringInInputValues(
        value, customSecretManagerConnectorDTO.getTemplate().getTemplateInputs());
    customSecretManagerConnectorDTO.getTemplate().setTemplateInputs(inputValues);
    Set<EncryptedDataParams> encryptedDataParamsSet = customSecretManagerHelper.prepareEncryptedDataParamsSet(
        customSecretManagerConfigDTO, YamlUtils.writeYamlString(connectorDTO));
    if (encryptedData.getParameters() == null) {
      encryptedData.setParameters(encryptedDataParamsSet);
    } else {
      encryptedData.getParameters().addAll(encryptedDataParamsSet);
    }
    EncryptionConfig encryptionConfig = SecretManagerConfigMapper.fromDTO(secretManager);
    boolean isReferenceValid = customEncryptorsRegistry.getCustomEncryptor(encryptedData.getEncryptionType())
                                   .validateReference(accountIdentifier, encryptedDataParamsSet, encryptionConfig);
    if (!isReferenceValid) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Failed to fetch the secret value.", USER);
    }
  }

  private void validateSecretDoesNotExist(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    NGEncryptedData encryptedData = get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (encryptedData != null) {
      throw new InvalidRequestException(
          String.format("Secret with identifier %s already exists in this scope", identifier));
    }
  }

  @Override
  public NGEncryptedData createSecretFile(String accountIdentifier, SecretDTOV2 dto, InputStream inputStream) {
    validateSecretDoesNotExist(
        accountIdentifier, dto.getOrgIdentifier(), dto.getProjectIdentifier(), dto.getIdentifier());
    SecretFileSpecDTO secret = (SecretFileSpecDTO) dto.getSpec();
    SecretManagerConfigDTO secretManager = getSecretManagerOrThrow(accountIdentifier, dto.getOrgIdentifier(),
        dto.getProjectIdentifier(), secret.getSecretManagerIdentifier(), false);
    validateAdditionalMetadata(secretManager, secret);
    NGEncryptedData encryptedData = buildNGEncryptedData(accountIdentifier, dto, secretManager);

    if (isReadOnlySecretManager(secretManager)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, READ_ONLY_SECRET_MANAGER_ERROR, USER);
    }
    byte[] inputBytes = getInputBytes(inputStream);
    String fileContent = getFileContent(inputBytes);

    encrypt(encryptedData, fileContent, SecretManagerConfigMapper.fromDTO(secretManager));
    encryptedData.setBase64Encoded(true);
    if (ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD.contains(encryptedData.getEncryptionType())
        && isNotEmpty(fileContent)) {
      String encryptedFileId = secretsFileService.createFile(
          encryptedData.getName(), encryptedData.getAccountIdentifier(), encryptedData.getEncryptedValue());
      encryptedData.setEncryptedValue(encryptedFileId.toCharArray());
    }
    return encryptedDataDao.save(encryptedData);
  }

  @Override
  public NGEncryptedData createSecretFile(
      String accountIdentifier, SecretDTOV2 dto, String encryptionKey, String encryptedValue) {
    validateSecretDoesNotExist(
        accountIdentifier, dto.getOrgIdentifier(), dto.getProjectIdentifier(), dto.getIdentifier());
    SecretFileSpecDTO secret = (SecretFileSpecDTO) dto.getSpec();
    SecretManagerConfigDTO secretManager = getSecretManagerOrThrow(accountIdentifier, dto.getOrgIdentifier(),
        dto.getProjectIdentifier(), secret.getSecretManagerIdentifier(), false);
    validateAdditionalMetadata(secretManager, secret);
    NGEncryptedData encryptedData = buildNGEncryptedData(accountIdentifier, dto, secretManager);

    if (isReadOnlySecretManager(secretManager)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, READ_ONLY_SECRET_MANAGER_ERROR, USER);
    }

    encryptedData.setPath(null);
    encryptedData.setEncryptionKey(encryptionKey);
    encryptedData.setEncryptedValue(encryptedValue.toCharArray());
    encryptedData.setBase64Encoded(true);
    return encryptedDataDao.save(encryptedData);
  }

  private NGEncryptedData buildNGEncryptedData(
      String accountIdentifier, SecretDTOV2 dto, SecretManagerConfigDTO secretManager) {
    NGEncryptedDataBuilder builder = NGEncryptedData.builder();
    String secretManagerIdentifierRef = secretManager.getIdentifier();
    if (dto.getSpec() instanceof SecretTextSpecDTO) {
      SecretTextSpecDTO secretTextSpecDTO = (SecretTextSpecDTO) dto.getSpec();
      secretManagerIdentifierRef = secretTextSpecDTO.getSecretManagerIdentifier();
    }
    if (dto.getSpec() instanceof SecretFileSpecDTO) {
      SecretFileSpecDTO secretTextSpecDTO = (SecretFileSpecDTO) dto.getSpec();
      secretManagerIdentifierRef = secretTextSpecDTO.getSecretManagerIdentifier();
    }
    builder.accountIdentifier(accountIdentifier)
        .orgIdentifier(dto.getOrgIdentifier())
        .projectIdentifier(dto.getProjectIdentifier())
        .identifier(dto.getIdentifier())
        .name(dto.getName());
    builder.secretManagerIdentifier(secretManagerIdentifierRef).encryptionType(secretManager.getEncryptionType());
    if (SecretText.equals(dto.getType())) {
      SecretTextSpecDTO secret = (SecretTextSpecDTO) dto.getSpec();
      if (Reference.equals(secret.getValueType()) || CustomSecretManagerValues.equals(secret.getValueType())) {
        builder.path(secret.getValue());
      }
      builder.type(SettingVariableTypes.SECRET_TEXT);
      builder.additionalMetadata(secret.getAdditionalMetadata());
    } else if (SecretFile.equals(dto.getType())) {
      builder.type(SettingVariableTypes.CONFIG_FILE);
      SecretFileSpecDTO secretFileSpecDTO = (SecretFileSpecDTO) dto.getSpec();
      builder.additionalMetadata(secretFileSpecDTO.getAdditionalMetadata());
    }
    return builder.build();
  }

  private void encrypt(NGEncryptedData encryptedData, String value, SecretManagerConfig secretManagerConfig) {
    if (isEmpty(value)) {
      return;
    }
    SecretManagerType secretManagerType = secretManagerConfig.getType();
    EncryptedRecord encryptedRecord;
    try {
      if (KMS.equals(secretManagerType)) {
        encryptedRecord = kmsEncryptorsRegistry.getKmsEncryptor(secretManagerConfig)
                              .encryptSecret(encryptedData.getAccountIdentifier(), value, secretManagerConfig);
        validateEncryptedRecord(encryptedRecord);
      } else if (VAULT.equals(secretManagerType)) {
        if (EncryptionType.VAULT.equals(secretManagerConfig.getEncryptionType())
            && APP_ROLE.equals(((BaseVaultConfig) secretManagerConfig).getAccessType())
            && (ngFeatureFlagHelperService.isEnabled(
                encryptedData.getAccountIdentifier(), FeatureName.DO_NOT_RENEW_APPROLE_TOKEN))) {
          ((BaseVaultConfig) secretManagerConfig).setRenewAppRoleToken(false);
        }
        encryptedRecord = vaultEncryptorsRegistry.getVaultEncryptor(secretManagerConfig.getEncryptionType())
                              .createSecret(encryptedData.getAccountIdentifier(),
                                  io.harness.beans.SecretText.builder()
                                      .name(encryptedData.getName())
                                      .value(value)
                                      .additionalMetadata(encryptedData.getAdditionalMetadata())
                                      .build(),
                                  secretManagerConfig);
        validateEncryptedRecord(encryptedRecord);
      } else {
        throw new UnsupportedOperationException("Secret Manager type not supported: " + secretManagerType);
      }
    } catch (DelegateServiceDriverException ex) {
      throw buildDelegateNotAvailableHintException(ex.getMessage(), secretManagerConfig.getName());
    }
    encryptedData.setPath(null);
    encryptedData.setEncryptionKey(encryptedRecord.getEncryptionKey());
    encryptedData.setEncryptedValue(encryptedRecord.getEncryptedValue());
    encryptedData.setBase64Encoded(encryptedRecord.isBase64Encoded());
  }

  private void encrypt(NGEncryptedData encryptedData, String value, NGEncryptedData existingEncryptedData,
      SecretManagerConfig secretManagerConfig) {
    SecretManagerType secretManagerType = secretManagerConfig.getType();
    EncryptedRecord encryptedRecord;
    try {
      if (KMS.equals(secretManagerType)) {
        if (isEmpty(value)) {
          encryptedRecord = existingEncryptedData;
        } else {
          encryptedRecord = kmsEncryptorsRegistry.getKmsEncryptor(secretManagerConfig)
                                .encryptSecret(encryptedData.getAccountIdentifier(), value, secretManagerConfig);
          validateEncryptedRecord(encryptedRecord);
        }

      } else if (VAULT.equals(secretManagerType)) {
        if (EncryptionType.VAULT.equals(secretManagerConfig.getEncryptionType())
            && APP_ROLE.equals(((BaseVaultConfig) secretManagerConfig).getAccessType())
            && (ngFeatureFlagHelperService.isEnabled(
                encryptedData.getAccountIdentifier(), FeatureName.DO_NOT_RENEW_APPROLE_TOKEN))) {
          ((BaseVaultConfig) secretManagerConfig).setRenewAppRoleToken(false);
        }
        if (!Optional.ofNullable(existingEncryptedData.getPath()).isPresent()) {
          // Existing one is Inline Secret
          if (isEmpty(value)) {
            if (isEmpty(existingEncryptedData.getEncryptedValue())) {
              encryptedRecord = existingEncryptedData;
            } else {
              encryptedRecord = vaultEncryptorsRegistry.getVaultEncryptor(secretManagerConfig.getEncryptionType())
                                    .renameSecret(encryptedData.getAccountIdentifier(),
                                        io.harness.beans.SecretText.builder()
                                            .name(encryptedData.getName())
                                            .value(value)
                                            .additionalMetadata(encryptedData.getAdditionalMetadata())
                                            .build(),
                                        existingEncryptedData, secretManagerConfig);
              validateEncryptedRecord(encryptedRecord);
            }
          } else {
            encryptedRecord = vaultEncryptorsRegistry.getVaultEncryptor(secretManagerConfig.getEncryptionType())
                                  .updateSecret(encryptedData.getAccountIdentifier(),
                                      io.harness.beans.SecretText.builder()
                                          .name(encryptedData.getName())
                                          .value(value)
                                          .additionalMetadata(encryptedData.getAdditionalMetadata())
                                          .build(),
                                      existingEncryptedData, secretManagerConfig);
            validateEncryptedRecord(encryptedRecord);
          }
        } else {
          // Existing one is Reference Secret
          if (isNotEmpty(value)) {
            encryptedRecord = vaultEncryptorsRegistry.getVaultEncryptor(secretManagerConfig.getEncryptionType())
                                  .createSecret(encryptedData.getAccountIdentifier(),
                                      io.harness.beans.SecretText.builder()
                                          .name(encryptedData.getName())
                                          .value(value)
                                          .additionalMetadata(encryptedData.getAdditionalMetadata())
                                          .build(),
                                      secretManagerConfig);
            validateEncryptedRecord(encryptedRecord);
          } else {
            encryptedRecord = existingEncryptedData;
          }
        }
      } else {
        throw new UnsupportedOperationException("Secret Manager type not supported: " + secretManagerType);
      }
    } catch (DelegateServiceDriverException ex) {
      throw buildDelegateNotAvailableHintException(ex.getMessage(), secretManagerConfig.getName());
    }
    encryptedData.setPath(null);
    encryptedData.setEncryptionKey(encryptedRecord.getEncryptionKey());
    encryptedData.setEncryptedValue(encryptedRecord.getEncryptedValue());
    encryptedData.setBase64Encoded(encryptedRecord.isBase64Encoded());
  }

  @Override
  public NGEncryptedData get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return encryptedDataDao.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public NGEncryptedData getFromReferenceExpression(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String secretIdentifier) {
    SecretRefParsedData secretRefParsedData =
        dynamicSecretReferenceHelper.validateAndGetSecretRefParsedData(secretIdentifier);
    return NGEncryptedData.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(secretIdentifier)
        .id(generateUuid())
        .name(secretIdentifier)
        .type(SettingVariableTypes.SECRET_TEXT)
        .path(secretRefParsedData.getRelativePath())
        .secretManagerIdentifier(secretRefParsedData.getSecretManagerIdentifier())
        .encryptionType(secretRefParsedData.getEncryptionType())
        .additionalMetadata(secretRefParsedData.getAdditionalMetadata())
        .build();
  }

  @Override
  public NGEncryptedData updateSecretText(String accountIdentifier, SecretDTOV2 dto) {
    SecretTextSpecDTO secret = (SecretTextSpecDTO) dto.getSpec();

    NGEncryptedData existingEncryptedData = getWithFileContentOrThrow(
        accountIdentifier, dto.getOrgIdentifier(), dto.getProjectIdentifier(), dto.getIdentifier());
    validateUpdateRequest(existingEncryptedData, dto);

    SecretManagerConfigDTO secretManager = getSecretManagerOrThrow(accountIdentifier, dto.getOrgIdentifier(),
        dto.getProjectIdentifier(), secret.getSecretManagerIdentifier(), false);
    validateAdditionalMetadata(secretManager, secret);
    NGEncryptedData encryptedData = buildNGEncryptedData(accountIdentifier, dto, secretManager);
    if (Inline.equals(secret.getValueType())) {
      if (isReadOnlySecretManager(secretManager)) {
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, READ_ONLY_SECRET_MANAGER_ERROR, USER);
      }
      encrypt(
          encryptedData, secret.getValue(), existingEncryptedData, SecretManagerConfigMapper.fromDTO(secretManager));
      existingEncryptedData.setEncryptionKey(encryptedData.getEncryptionKey());
      existingEncryptedData.setEncryptedValue(encryptedData.getEncryptedValue());
      existingEncryptedData.setPath(null);
    } else {
      validatePath(encryptedData.getPath(), encryptedData.getEncryptionType());
      if (!isReadOnlySecretManager(secretManager) && !existingEncryptedData.getName().equals(dto.getName())
          && !Optional.ofNullable(existingEncryptedData.getPath()).isPresent()
          && Optional.ofNullable(existingEncryptedData.getEncryptedValue()).isPresent()) {
        deleteSecretInSecretManager(
            accountIdentifier, existingEncryptedData, SecretManagerConfigMapper.fromDTO(secretManager));
      }
      existingEncryptedData.setEncryptionKey(null);
      existingEncryptedData.setEncryptedValue(null);
      existingEncryptedData.setPath(encryptedData.getPath());
    }
    existingEncryptedData.setName(encryptedData.getName());
    return encryptedDataDao.save(existingEncryptedData);
  }

  private void validateUpdateRequest(NGEncryptedData existingEncryptedData, SecretDTOV2 dto) {
    if (existingEncryptedData.getType() == SettingVariableTypes.CONFIG_FILE && !SecretFile.equals(dto.getType())) {
      throw new InvalidRequestException("Cannot change secret type after creation.", INVALID_REQUEST, USER);
    }
    if (existingEncryptedData.getType() == SettingVariableTypes.SECRET_TEXT && !SecretText.equals(dto.getType())) {
      throw new InvalidRequestException("Cannot change secret type after creation.", INVALID_REQUEST, USER);
    }
    String secretManagerIdentifier = getSecretManagerIdentifier(dto);
    if ((existingEncryptedData.getSecretManagerIdentifier() != null
            && !existingEncryptedData.getSecretManagerIdentifier().equals(secretManagerIdentifier))
        || (secretManagerIdentifier != null
            && !secretManagerIdentifier.equals(existingEncryptedData.getSecretManagerIdentifier()))) {
      throw new InvalidRequestException("Cannot change secret manager after creation.", INVALID_REQUEST, USER);
    }
  }

  private String getSecretManagerIdentifier(SecretDTOV2 dto) {
    if (SecretText.equals(dto.getType())) {
      return ((SecretTextSpecDTO) dto.getSpec()).getSecretManagerIdentifier();
    } else if (SecretFile.equals(dto.getType())) {
      return ((SecretFileSpecDTO) dto.getSpec()).getSecretManagerIdentifier();
    }
    return null;
  }

  private NGEncryptedData getWithFileContentOrThrow(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    NGEncryptedData encryptedData = get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (encryptedData != null) {
      if (encryptedData.getType() == SettingVariableTypes.CONFIG_FILE
          && ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD.contains(encryptedData.getEncryptionType())
          && Optional.ofNullable(encryptedData.getEncryptedValue()).isPresent()) {
        char[] fileContent = secretsFileService.getFileContents(String.valueOf(encryptedData.getEncryptedValue()));
        encryptedData.setEncryptedValue(fileContent);
      }
      return encryptedData;
    }
    throw new InvalidRequestException(
        String.format("Secret with identifier %s does not exist in this scope", identifier));
  }

  @Override
  public NGEncryptedData updateSecretFile(String accountIdentifier, SecretDTOV2 dto, InputStream inputStream) {
    SecretFileSpecDTO secret = (SecretFileSpecDTO) dto.getSpec();
    NGEncryptedData existingEncryptedData =
        get(accountIdentifier, dto.getOrgIdentifier(), dto.getProjectIdentifier(), dto.getIdentifier());
    if (existingEncryptedData == null) {
      throw new InvalidRequestException(
          String.format("Secret with identifier %s does not exist in this scope", dto.getIdentifier()));
    }
    validateUpdateRequest(existingEncryptedData, dto);
    SecretManagerConfigDTO secretManager = getSecretManagerOrThrow(accountIdentifier, dto.getOrgIdentifier(),
        dto.getProjectIdentifier(), secret.getSecretManagerIdentifier(), false);
    validateAdditionalMetadata(secretManager, secret);
    NGEncryptedData encryptedData = buildNGEncryptedData(accountIdentifier, dto, secretManager);

    if (isReadOnlySecretManager(secretManager)) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, READ_ONLY_SECRET_MANAGER_ERROR, USER);
    }
    char[] existingFileId = existingEncryptedData.getEncryptedValue();
    byte[] inputBytes = getInputBytes(inputStream);
    String fileContent = getFileContent(inputBytes);

    encrypt(encryptedData, fileContent, existingEncryptedData, SecretManagerConfigMapper.fromDTO(secretManager));
    if (isNotEmpty(fileContent) && Optional.ofNullable(existingFileId).isPresent()
        && ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD.contains(existingEncryptedData.getEncryptionType())) {
      String encryptedFileId = secretsFileService.createFile(
          encryptedData.getName(), encryptedData.getAccountIdentifier(), encryptedData.getEncryptedValue());
      encryptedData.setEncryptedValue(encryptedFileId.toCharArray());
    }

    existingEncryptedData.setName(encryptedData.getName());
    existingEncryptedData.setEncryptionKey(encryptedData.getEncryptionKey());
    existingEncryptedData.setEncryptedValue(encryptedData.getEncryptedValue());
    if (isNotEmpty(fileContent)) {
      existingEncryptedData.setBase64Encoded(true);
    }
    NGEncryptedData updatedEncryptedData = encryptedDataDao.save(existingEncryptedData);
    if (isNotEmpty(fileContent) && Optional.ofNullable(existingFileId).isPresent()
        && ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD.contains(existingEncryptedData.getEncryptionType())) {
      secretsFileService.deleteFile(existingFileId);
    }
    return updatedEncryptedData;
  }

  @Override
  public DecryptedSecretValue decryptSecret(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    NGEncryptedData encryptedData =
        getWithFileContentOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    SecretManagerConfigDTO secretManagerConfigDTO = getSecretManagerOrThrow(
        accountIdentifier, orgIdentifier, projectIdentifier, encryptedData.getSecretManagerIdentifier(), false);

    SecretManagerConfig secretManagerConfig = SecretManagerConfigMapper.fromDTO(secretManagerConfigDTO);
    if (isNgHarnessSecretManager(secretManagerConfig.getNgMetadata())) {
      String decryptedValue =
          String.valueOf(kmsEncryptorsRegistry.getKmsEncryptor(secretManagerConfig)
                             .fetchSecretValue(accountIdentifier, encryptedData, secretManagerConfig));
      return DecryptedSecretValue.builder()
          .identifier(identifier)
          .accountIdentifier(accountIdentifier)
          .orgIdentifier(orgIdentifier)
          .projectIdentifier(projectIdentifier)
          .decryptedValue(decryptedValue)
          .build();
    } else {
      throw new InvalidRequestException(
          "Decryption is supported only for secrets encrypted via harness managed secret managers");
    }
  }

  @Override
  public boolean validateSecretRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SecretDTOV2 secretDTO) {
    if (!(secretDTO.getSpec() instanceof SecretTextSpecDTO)) {
      throw new InvalidRequestException("Validating referenced secrets is only supported for type Secret Text");
    }
    SecretTextSpecDTO specDTO = (SecretTextSpecDTO) secretDTO.getSpec();
    if (((SecretTextSpecDTO) secretDTO.getSpec()).getValueType().equals(Inline)) {
      throw new InvalidRequestException(
          "Path of referenced secrets stored in secret managers can only can be validated, Inline secrets are not applicable");
    }
    SecretManagerConfigDTO secretManager = getSecretManagerOrThrow(
        accountIdentifier, orgIdentifier, projectIdentifier, specDTO.getSecretManagerIdentifier(), false);
    io.harness.beans.SecretText secretText = io.harness.beans.SecretText.builder()
                                                 .name(secretDTO.getName())
                                                 .path(specDTO.getValue())
                                                 .additionalMetadata(specDTO.getAdditionalMetadata())
                                                 .build();
    switch (secretManager.getEncryptionType()) {
      case CUSTOM_NG:
        throw new InvalidRequestException("Validating referenced secrets for Custom Secret Manager is not supported.");
      case GCP_KMS:
      case KMS:
        throw new InvalidRequestException(
            "Validating referenced secrets for Key Management Systems such as GCP_KMS and AWS KMS is not supported since they do not store secrets.");
      default:
        break;
    }
    return vaultEncryptorsRegistry
        .getVaultEncryptor(SecretManagerConfigMapper.fromDTO(secretManager).getEncryptionType())
        .validateReference(accountIdentifier, secretText, SecretManagerConfigMapper.fromDTO(secretManager));
  }

  @Override
  public boolean isSecretManagerReadOnly(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String secretManagerId) {
    SecretManagerConfigDTO secretManager;

    secretManager =
        getSecretManagerOrThrow(accountIdentifier, orgIdentifier, projectIdentifier, secretManagerId, false);
    return isReadOnlySecretManager(secretManager);
  }

  private byte[] getInputBytes(InputStream inputStream) {
    byte[] inputBytes = new byte[0];
    if (inputStream != null) {
      try {
        inputBytes = ByteStreams.toByteArray(inputStream);
      } catch (IOException exception) {
        throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, "Unable to convert input stream to bytes", SRE);
      }
    }
    return inputBytes;
  }

  private String getFileContent(byte[] inputBytes) {
    return new String(CHARSET.decode(ByteBuffer.wrap(encodeBase64ToByteArray(inputBytes))).array());
  }

  @Override
  public boolean delete(String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier,
      boolean forceDelete) {
    NGEncryptedData encryptedData = get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    if (encryptedData == null) {
      return false;
    }
    SecretManagerConfigDTO secretManager = null;
    try {
      secretManager = getSecretManager(
          accountIdentifier, orgIdentifier, projectIdentifier, encryptedData.getSecretManagerIdentifier(), false);
    } catch (Exception e) {
      log.warn(
          "Secret Manager with identifier {} not found while deleting secret with identifier {} in org {}, project {} not found ",
          encryptedData.getSecretManagerIdentifier(), identifier, orgIdentifier, projectIdentifier);
    }

    if (isReadOnlySecretManager(secretManager) && !Optional.ofNullable(encryptedData.getPath()).isPresent()
        && Optional.ofNullable(encryptedData.getEncryptedValue()).isPresent()) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Cannot delete an Inline secret in read only secret manager", USER);
    }
    if (secretManager != null && !Optional.ofNullable(encryptedData.getPath()).isPresent()
        && Optional.ofNullable(encryptedData.getEncryptedValue()).isPresent()) {
      deleteSecretInSecretManager(accountIdentifier, encryptedData, SecretManagerConfigMapper.fromDTO(secretManager));
    }
    if (encryptedData.getType() == SettingVariableTypes.CONFIG_FILE
        && Optional.ofNullable(encryptedData.getEncryptedValue()).isPresent()
        && ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD.contains(encryptedData.getEncryptionType())) {
      secretsFileService.deleteFile(encryptedData.getEncryptedValue());
    }
    // Note - Ideally the return below should be a logical AND of whether secret was deleted
    // in remote vault in deleteSecretInSecretManager and deletion in encrypted data DAO below.
    // But for now to ensure local deletion of record goes through, just returning the boolean
    // value of encrypted data DAO record irrespective of whether it was deleted on remote or not.
    return encryptedDataDao.delete(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  @Override
  public NGEncryptedData hardDelete(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    return encryptedDataDao.hardDelete(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
  }

  private void deleteSecretInSecretManager(
      String accountIdentifier, NGEncryptedData encryptedData, SecretManagerConfig secretManagerConfig) {
    SecretManagerType secretManagerType = secretManagerConfig.getType();
    if (VAULT.equals(secretManagerType)) {
      if (EncryptionType.VAULT.equals(secretManagerConfig.getEncryptionType())
          && APP_ROLE.equals(((BaseVaultConfig) secretManagerConfig).getAccessType())
          && (ngFeatureFlagHelperService.isEnabled(
              encryptedData.getAccountIdentifier(), FeatureName.DO_NOT_RENEW_APPROLE_TOKEN))) {
        ((BaseVaultConfig) secretManagerConfig).setRenewAppRoleToken(false);
      }
      try {
        if (!vaultEncryptorsRegistry.getVaultEncryptor(secretManagerConfig.getEncryptionType())
                 .deleteSecret(accountIdentifier, encryptedData, secretManagerConfig)) {
          log.error("Secret was not deleted on the remote vault for account : {}, secret : {}", accountIdentifier,
              secretManagerConfig.getIdentifier());
        }
      } catch (SecretManagementException ex) {
        // Logging the exception here and not throwing it further
        // to ensure the record gets deleted locally even if it
        // fails on the remote vault.
        log.error("Exception received while deleting secret : {}", ex.toString());
      }
    }
  }

  @Override
  public List<EncryptedDataDetail> getEncryptionDetails(NGAccess ngAccess, DecryptableEntity object) {
    // if object is already decrypted, return empty list
    if (object.isDecrypted()) {
      return Collections.emptyList();
    }
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    List<Field> encryptedFields = object.getSecretReferenceFields();

    // iterate over all the fields with @SecretReference annotation
    for (Field field : encryptedFields) {
      try {
        field.setAccessible(true);

        // type cast the field to SecretRefData, if the type casted value is not present, continue
        SecretRefData secretRefData = (SecretRefData) field.get(object);
        if (!Optional.ofNullable(secretRefData).isPresent()) {
          continue;
        }
        String secretIdentifier = secretRefData.getIdentifier();
        Scope secretScope = secretRefData.getScope();

        // if sufficient information is there to process this field, try to process it
        if (Optional.ofNullable(secretIdentifier).isPresent() && Optional.ofNullable(secretScope).isPresent()) {
          String accountIdentifier = ngAccess.getAccountIdentifier();
          String orgIdentifier = getOrgIdentifier(ngAccess.getOrgIdentifier(), secretScope);
          String projectIdentifier = getProjectIdentifier(ngAccess.getProjectIdentifier(), secretScope);

          NGEncryptedData encryptedData = null;
          if (isSecretIdentifierAPathReference(secretIdentifier)) {
            encryptedData =
                getFromReferenceExpression(accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);
          } else {
            // get encrypted data from DB
            encryptedData = get(accountIdentifier, orgIdentifier, projectIdentifier, secretIdentifier);
          }
          if (encryptedData != null) {
            // if type is file and file is saved elsewhere, download and save contents in encryptedValue
            if (encryptedData.getType() == SettingVariableTypes.CONFIG_FILE
                && ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD.contains(encryptedData.getEncryptionType())
                && Optional.ofNullable(encryptedData.getEncryptedValue()).isPresent()) {
              char[] fileContent =
                  secretsFileService.getFileContents(String.valueOf(encryptedData.getEncryptedValue()));
              encryptedData.setEncryptedValue(fileContent);
            }
            SecretManagerConfigDTO secretManager;
            // get secret manager with which this was secret was encrypted
            if (encryptedData.getEncryptionType() == LOCAL) {
              secretManager = ngConnectorSecretManagerService.getLocalConfigDTO(accountIdentifier);
            } else {
              secretManager = getSecretManager(accountIdentifier, orgIdentifier, projectIdentifier,
                  encryptedData.getSecretManagerIdentifier(), false);
            }

            if (secretManager != null) {
              EncryptionConfig encryptionConfig = SecretManagerConfigMapper.fromDTO(secretManager);
              EncryptedRecordData encryptedRecordData;

              if (secretManager.isHarnessManaged()
                  || HARNESS_SECRET_MANAGER_IDENTIFIER.equals(encryptedData.getSecretManagerIdentifier())) {
                encryptedRecordData = globalEncryptDecryptClient.convertEncryptedRecordToLocallyEncrypted(
                    encryptedData, accountIdentifier, encryptionConfig);
                if (LOCAL.equals(encryptedRecordData.getEncryptionType())) {
                  encryptionConfig = SecretManagerConfigMapper.fromDTO(getLocalEncryptionConfig(accountIdentifier));
                } else {
                  log.error("Failed to decrypt secret {} with {} harness secret manager", encryptedData.getUuid(),
                      encryptionConfig.getEncryptionType());
                  continue;
                }
              } else {
                encryptedRecordData = buildEncryptedRecordData(encryptedData);
              }
              if (secretManager.getEncryptionType() == EncryptionType.CUSTOM_NG) {
                ngConnectorSecretManagerService.resolveSecretManagerScriptSecrets(accountIdentifier,
                    encryptedRecordData.getPath(), (CustomSecretNGManagerConfig) encryptionConfig, secretManager);
              }
              encryptedDataDetails.add(EncryptedDataDetail.builder()
                                           .encryptedData(encryptedRecordData)
                                           .encryptionConfig(encryptionConfig)
                                           .fieldName(field.getName())
                                           .build());
            }
          }
        }
      } catch (IllegalAccessException exception) {
        throw new SecretManagementException(ENCRYPT_DECRYPT_ERROR, exception, USER);
      }
    }
    return encryptedDataDetails;
  }

  private boolean isSecretIdentifierAPathReference(String secretIdentifier) {
    for (EncryptionType allowedType :
        DynamicSecretReferenceHelper.ENCRYPTION_TYPES_ALLOWED_FOR_DIRECT_SECRET_REFERENCE) {
      StringBuilder stringBuilder = new StringBuilder(allowedType.getYamlName())
                                        .append(SECRET_REFERENCE_EXPRESSION_DELIMITER)
                                        .append(SECRET_REFERENCE_DATA_ROOT_PREFIX);
      if (secretIdentifier.startsWith(stringBuilder.toString())) {
        return true;
      }
    }
    return false;
  }

  private LocalConfigDTO getLocalEncryptionConfig(String accountIdentifier) {
    return LocalConfigDTO.builder().accountIdentifier(accountIdentifier).identifier(null).encryptionType(LOCAL).build();
  }

  private EncryptedRecordData buildEncryptedRecordData(NGEncryptedData encryptedData) {
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
        .additionalMetadata(encryptedData.getAdditionalMetadata())
        .build();
  }

  private void validateEncryptedRecord(EncryptedRecord encryptedRecord) {
    if (encryptedRecord == null || !Optional.ofNullable(encryptedRecord.getEncryptionKey()).isPresent()
        || !Optional.ofNullable(encryptedRecord.getEncryptedValue()).isPresent()) {
      String message =
          "Encryption of secret failed unexpectedly. Please check your secret manager configuration and try again.";
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
    }
  }

  private void validatePath(String path, EncryptionType encryptionType) {
    if (path != null && encryptionType == EncryptionType.VAULT && path.indexOf('#') < 0) {
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR,
          "Secret path need to include the # sign with the the key name after. E.g. /foo/bar/my-secret#my-key.", USER);
    }
    // check if reference secrets are allowed based on EncryptionType
    if (Arrays.asList(GCP_KMS, KMS).contains(encryptionType)) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Reference secrets are not allowed in KMS type Secret Managers", USER);
    }
  }

  private SecretManagerConfigDTO getSecretManagerOrThrow(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, boolean maskSecrets) {
    SecretManagerConfigDTO secretManager =
        getSecretManager(accountIdentifier, orgIdentifier, projectIdentifier, identifier, maskSecrets);
    if (secretManager == null) {
      String message = String.format("No such secret manager found with identifier %s ", identifier);
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, formNotFoundMessage(message, orgIdentifier, projectIdentifier), USER);
    }
    return secretManager;
  }

  private SecretManagerConfigDTO getSecretManager(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String identifier, boolean maskSecrets) {
    if (identifier == null) {
      return getLocalEncryptionConfig(accountIdentifier);
    }
    return ngConnectorSecretManagerService.getUsingIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, identifier, maskSecrets);
  }

  private boolean isReadOnlySecretManager(SecretManagerConfigDTO secretManager) {
    if (secretManager == null) {
      return false;
    }
    if (EncryptionType.VAULT.equals(secretManager.getEncryptionType())) {
      return ((VaultConfigDTO) secretManager).isReadOnly();
    }
    return false;
  }

  private String formNotFoundMessage(String baseMessage, String orgIdentifier, String projectIdentifier) {
    if (!StringUtils.isEmpty(orgIdentifier)) {
      baseMessage += String.format("in org: %s", orgIdentifier);
      if (!StringUtils.isEmpty(projectIdentifier)) {
        baseMessage += String.format(" and project: %s", projectIdentifier);
      }
    } else if (!StringUtils.isEmpty(projectIdentifier)) {
      baseMessage += "in project: %s" + projectIdentifier;
    } else {
      baseMessage += "in this scope.";
    }
    return baseMessage;
  }

  private String getOrgIdentifier(String parentOrgIdentifier, @NotNull Scope scope) {
    if (scope != Scope.ACCOUNT) {
      return parentOrgIdentifier;
    }
    return null;
  }

  private String getProjectIdentifier(String parentProjectIdentifier, @NotNull Scope scope) {
    if (scope == Scope.PROJECT) {
      return parentProjectIdentifier;
    }
    return null;
  }

  private HintException buildDelegateNotAvailableHintException(
      String delegateDownErrorMessage, String secretManagerIdentifier) {
    return new HintException(
        String.format(
            "Please make sure that your delegate for Secret Manager with identifier [%s] is connected. Refer %s for more information on delegate Installation",
            secretManagerIdentifier, DocumentLinksConstants.DELEGATE_INSTALLATION_LINK),
        new DelegateNotAvailableException(delegateDownErrorMessage, WingsException.USER));
  }
}
