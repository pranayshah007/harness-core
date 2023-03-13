/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.EncryptedData.PARENT_ID_KEY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.FileBucket.CONFIGS;
import static io.harness.encryption.EncryptionReflectUtils.getEncryptedFields;
import static io.harness.encryption.EncryptionReflectUtils.getEncryptedRefField;
import static io.harness.eraro.ErrorCode.ENCRYPT_DECRYPT_ERROR;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.persistence.HQuery.excludeCount;
import static io.harness.secrets.SecretsDao.ID_KEY;
import static io.harness.security.SimpleEncryption.CHARSET;
import static io.harness.security.encryption.EncryptionType.CUSTOM;
import static io.harness.security.encryption.EncryptionType.GCP_KMS;
import static io.harness.security.encryption.EncryptionType.KMS;
import static io.harness.security.encryption.EncryptionType.LOCAL;
import static io.harness.security.encryption.EncryptionType.VAULT;
import static io.harness.validation.Validator.equalCheck;

import static software.wings.beans.ServiceVariable.ServiceVariableKeys;
import static software.wings.service.impl.security.AbstractSecretServiceImpl.checkState;
import static software.wings.service.impl.security.AbstractSecretServiceImpl.encryptLocal;

import static dev.morphia.aggregation.Group.grouping;
import static dev.morphia.aggregation.Projection.projection;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.trim;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.HarnessSecret;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SecretChangeLog;
import io.harness.beans.SecretChangeLog.SecretChangeLogKeys;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretMetadata;
import io.harness.beans.SecretText;
import io.harness.beans.SecretUsageLog;
import io.harness.beans.SecretUsageLog.SecretUsageLogKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.SecretManagementException;
import io.harness.exception.WingsException;
import io.harness.persistence.HIterator;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.secrets.SecretService;
import io.harness.secrets.SecretsDao;
import io.harness.secrets.setupusage.SecretSetupUsage;
import io.harness.secrets.setupusage.SecretSetupUsageService;
import io.harness.secrets.yamlhandlers.SecretYamlHandler;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.JsonUtils;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.SecretManagerRuntimeParameters;
import software.wings.beans.SecretManagerRuntimeParameters.SecretManagerRuntimeParametersKeys;
import software.wings.beans.ServiceVariable;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.VaultConfig;
import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.security.UsageRestrictions;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.security.CustomEncryptedDataDetailBuilder;
import software.wings.service.intfc.security.EncryptedSettingAttributes;
import software.wings.service.intfc.security.LocalSecretManagerService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.security.VaultService;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.WingsReflectionUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.AggregationOptions;
import dev.morphia.aggregation.Accumulator;
import dev.morphia.aggregation.AggregationPipeline;
import dev.morphia.query.Query;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
@TargetModule(HarnessModule._440_SECRET_MANAGEMENT_SERVICE)
public class SecretManagerImpl implements SecretManager, EncryptedSettingAttributes {
  static final Set<EncryptionType> ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD = EnumSet.of(LOCAL, GCP_KMS, KMS);

  @Inject private WingsPersistence wingsPersistence;
  @Inject private VaultService vaultService;
  @Inject private GlobalEncryptDecryptClient globalEncryptDecryptClient;
  @Inject private FileService fileService;
  @Inject private LocalSecretManagerService localSecretManagerService;
  @Inject private SecretManagerConfigService secretManagerConfigService;
  @Inject private CustomEncryptedDataDetailBuilder customEncryptedDataDetailBuilder;
  @Inject private SecretSetupUsageService secretSetupUsageService;
  @Inject private SettingServiceHelper settingServiceHelper;
  @Inject private SecretService secretService;
  @Inject private SecretYamlHandler secretYamlHandler;
  @Inject private SecretsDao secretsDao;

  @Override
  public EncryptionType getEncryptionType(String accountId) {
    return secretManagerConfigService.getEncryptionType(accountId);
  }

  @Override
  public List<SecretManagerConfig> listSecretManagers(String accountId) {
    return secretManagerConfigService.listSecretManagers(accountId, true);
  }

  @Override
  public SecretManagerConfig getSecretManager(String accountId, String kmsId) {
    return secretManagerConfigService.getSecretManager(accountId, kmsId, true);
  }

  @Override
  public SecretManagerConfig getSecretManagerByName(String accountId, String name) {
    return secretManagerConfigService.getSecretManagerByName(accountId, name);
  }

  @Override
  public SecretManagerConfig getSecretManager(String accountId, String kmsId, EncryptionType encryptionType) {
    return secretManagerConfigService.getSecretManager(accountId, kmsId, encryptionType);
  }

  @Override
  public void updateUsageRestrictionsForSecretManagerConfig(
      String accountId, String secretManagerId, UsageRestrictions usageRestrictions) {
    secretManagerConfigService.updateUsageRestrictions(accountId, secretManagerId, usageRestrictions);
  }

  @Override
  public void clearDefaultFlagOfSecretManagers(String accountId) {
    secretManagerConfigService.clearDefaultFlagOfSecretManagers(accountId);
  }

  @Override
  public Optional<EncryptedDataDetail> encryptedDataDetails(
      String accountId, String fieldName, String encryptedDataId, String workflowExecutionId) {
    return encryptedDataDetails(accountId, fieldName, encryptedDataId, workflowExecutionId, true);
  }

  @Override
  public Optional<EncryptedDataDetail> encryptedDataDetails(String accountId, String fieldName, String encryptedDataId,
      String workflowExecutionId, boolean updateSecretUsage) {
    EncryptedData encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                                      .filter(EncryptedDataKeys.accountId, accountId)
                                      .filter(EncryptedDataKeys.ID_KEY, encryptedDataId)
                                      .get();
    if (encryptedData == null) {
      encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                          .filter(EncryptedDataKeys.accountId, accountId)
                          .filter(EncryptedDataKeys.name, encryptedDataId)
                          .get();
      if (encryptedData == null) {
        log.info("No encrypted record found neither with UUID nor with name for field {} for id: {}", fieldName,
            encryptedDataId);
        return Optional.empty();
      }
    }
    return getEncryptedDataDetails(accountId, fieldName, encryptedData, workflowExecutionId, updateSecretUsage);
  }

  @Override
  public Optional<EncryptedDataDetail> getEncryptedDataDetails(
      String accountId, String fieldName, EncryptedData encryptedData, String workflowExecutionId) {
    return getEncryptedDataDetails(accountId, fieldName, encryptedData, workflowExecutionId, true);
  }

  @Override
  public Optional<EncryptedDataDetail> getEncryptedDataDetails(String accountId, String fieldName,
      EncryptedData encryptedData, String workflowExecutionId, boolean updateSecretUsage) {
    SecretManagerConfig encryptionConfig = secretManagerConfigService.getSecretManager(
        accountId, encryptedData.getKmsId(), encryptedData.getEncryptionType());

    if (encryptionConfig == null) {
      log.error("No secret manager found with id {} for accountId {}", encryptedData.getKmsId(), accountId);
      return Optional.empty();
    }

    if (encryptionConfig.isTemplatized() && isNotEmpty(workflowExecutionId)) {
      encryptionConfig = updateRuntimeParametersAndGetConfig(workflowExecutionId, encryptionConfig);
    }

    if (encryptedData.getType() == SettingVariableTypes.CONFIG_FILE) {
      setEncryptedValueToFileContent(encryptedData);
    }

    // PL-1836: Need to preprocess global KMS and turn the KMS encryption into a LOCAL encryption.
    EncryptedRecordData encryptedRecordData;
    if (encryptionConfig.isGlobalKms()) {
      encryptedRecordData = globalEncryptDecryptClient.convertEncryptedRecordToLocallyEncrypted(
          encryptedData, accountId, encryptionConfig);

      // The encryption type will be set to LOCAL only if manager was able to decrypt.
      // If the decryption failed, we need to retain the kms encryption config, otherwise delegate task would
      // fail.
      if (encryptedRecordData.getEncryptionType() == LOCAL) {
        encryptionConfig = localSecretManagerService.getEncryptionConfig(accountId);
      }
      // TODO {karan} {piyush} revisit this to check if there is a need to send encryption config if conversion to local
      // failed
    } else {
      encryptedRecordData = SecretManager.buildRecordData(encryptedData);
    }
    //[PL-12731]: Issue with morphia caching logic https://github.com/MorphiaOrg/morphia/issues/281.
    encryptionConfig.setUuid(null);
    EncryptedDataDetail encryptedDataDetail;
    if (encryptionConfig.getEncryptionType() == CUSTOM) {
      encryptedDataDetail = customEncryptedDataDetailBuilder.buildEncryptedDataDetail(
          encryptedData, (CustomSecretsManagerConfig) encryptionConfig);
      encryptedDataDetail.setFieldName(fieldName);
    } else {
      encryptedDataDetail = EncryptedDataDetail.builder()
                                .encryptedData(encryptedRecordData)
                                .encryptionConfig(encryptionConfig)
                                .fieldName(fieldName)
                                .build();
    }
    if (updateSecretUsage) {
      this.updateUsageLogsForSecretText(workflowExecutionId, encryptedData);
    }
    return Optional.ofNullable(encryptedDataDetail);
  }

  @Override
  public List<EncryptedDataDetail> getEncryptionDetails(EncryptableSetting object) {
    return getEncryptionDetails(object, null, null);
  }

  @Override
  public List<EncryptedDataDetail> getEncryptionDetails(
      EncryptableSetting object, String appId, String workflowExecutionId) {
    return getEncryptionDetails(object, appId, workflowExecutionId, true);
  }

  @Override
  public List<EncryptedDataDetail> getEncryptionDetails(
      EncryptableSetting object, String appId, String workflowExecutionId, boolean updateSecretUsage) {
    // NOTE: appId should not used anywhere in this method
    if (object.isDecrypted()) {
      return Collections.emptyList();
    }

    List<Field> encryptedFields = object.getEncryptedFields();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    try {
      for (Field f : encryptedFields) {
        f.setAccessible(true);
        Object fieldValue = f.get(object);
        Field encryptedRefField = getEncryptedRefField(f, object);
        encryptedRefField.setAccessible(true);
        String encryptedRefFieldValue = (String) encryptedRefField.get(object);
        boolean isSetByYaml = WingsReflectionUtils.isSetByYaml(object, encryptedRefField);
        if (fieldValue != null && !isSetByYaml) {
          checkState(encryptedRefFieldValue == null, ENCRYPT_DECRYPT_ERROR,
              "both encrypted and non encrypted field set for " + object);
          encryptedDataDetails.add(EncryptedDataDetail.builder()
                                       .encryptedData(EncryptedRecordData.builder()
                                                          .encryptionType(LOCAL)
                                                          .encryptionKey(object.getAccountId())
                                                          .encryptedValue((char[]) fieldValue)
                                                          .build())
                                       .fieldName(f.getName())
                                       .build());
        } else if (encryptedRefFieldValue != null) {
          // PL-2902: Avoid decryption of null value encrypted fields.
          String id = encryptedRefFieldValue;
          if (isSetByYaml) {
            id = id.substring(id.indexOf(':') + 1);
          }

          Optional<EncryptedDataDetail> encryptedDataDetail =
              encryptedDataDetails(object.getAccountId(), f.getName(), id, workflowExecutionId, updateSecretUsage);
          if (encryptedDataDetail.isPresent()) {
            encryptedDataDetails.add(encryptedDataDetail.get());
          }
        }
      }
    } catch (IllegalAccessException e) {
      throw new SecretManagementException(ENCRYPT_DECRYPT_ERROR, e, USER);
    }

    return encryptedDataDetails;
  }

  private SecretManagerConfig updateRuntimeParametersAndGetConfig(
      String workflowExecutionId, SecretManagerConfig encryptionConfig) {
    Optional<software.wings.beans.dto.SecretManagerRuntimeParameters> secretManagerRuntimeParametersOptional =
        getSecretManagerRuntimeCredentialsForExecution(workflowExecutionId, encryptionConfig.getUuid());
    if (!secretManagerRuntimeParametersOptional.isPresent()) {
      String errorMessage = String.format(
          "The workflow is using secrets from templatized secret manager: %s. Please configure a Templatized Secret Manager step to provide credentials for the secret manager.",
          encryptionConfig.getName());
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, errorMessage, USER);
    }
    Map<String, String> runtimeParameters =
        JsonUtils.asObject(secretManagerRuntimeParametersOptional.get().getRuntimeParameters(),
            new TypeReference<Map<String, String>>() {});
    return secretManagerConfigService.updateRuntimeParameters(encryptionConfig, runtimeParameters, false);
  }

  private void updateUsageLogsForSecretText(String workflowExecutionId, EncryptedData encryptedData) {
    if (isNotEmpty(workflowExecutionId)) {
      WorkflowExecution workflowExecution = wingsPersistence.get(WorkflowExecution.class, workflowExecutionId);
      if (workflowExecution == null) {
        log.warn("No workflow execution with id {} found.", workflowExecutionId);
      } else {
        SecretUsageLog usageLog = SecretUsageLog.builder()
                                      .encryptedDataId(encryptedData.getUuid())
                                      .workflowExecutionId(workflowExecutionId)
                                      .accountId(encryptedData.getAccountId())
                                      .appId(workflowExecution.getAppId())
                                      .envId(workflowExecution.getEnvId())
                                      .build();
        wingsPersistence.save(usageLog);
      }
    }
  }

  @VisibleForTesting
  public void setEncryptedValueToFileContent(EncryptedData encryptedData) {
    if (ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD.contains(encryptedData.getEncryptionType())) {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      fileService.downloadToStream(String.valueOf(encryptedData.getEncryptedValue()), os, CONFIGS);
      encryptedData.setEncryptedValue(CHARSET.decode(ByteBuffer.wrap(os.toByteArray())).array());
    }

    if (isNotEmpty(encryptedData.getBackupEncryptedValue())
        && ENCRYPTION_TYPES_REQUIRING_FILE_DOWNLOAD.contains(encryptedData.getBackupEncryptionType())) {
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      fileService.downloadToStream(String.valueOf(encryptedData.getBackupEncryptedValue()), os, CONFIGS);
      encryptedData.setBackupEncryptedValue(CHARSET.decode(ByteBuffer.wrap(os.toByteArray())).array());
    }
  }

  @Override
  public void maskEncryptedFields(EncryptableSetting object) {
    List<Field> encryptedFields = object.getEncryptedFields();
    try {
      for (Field f : encryptedFields) {
        f.setAccessible(true);
        f.set(object, ENCRYPTED_FIELD_MASK.toCharArray());
      }
    } catch (IllegalAccessException e) {
      throw new SecretManagementException(ENCRYPT_DECRYPT_ERROR, e, USER);
    }
  }

  @Override
  public void resetUnchangedEncryptedFields(EncryptableSetting sourceObject, EncryptableSetting destinationObject) {
    equalCheck(sourceObject.getClass().getName(), destinationObject.getClass().getName());

    List<Field> encryptedFields = sourceObject.getEncryptedFields();
    try {
      for (Field f : encryptedFields) {
        f.setAccessible(true);
        if (java.util.Arrays.equals((char[]) f.get(destinationObject), ENCRYPTED_FIELD_MASK.toCharArray())) {
          f.set(destinationObject, f.get(sourceObject));
        }
      }
    } catch (IllegalAccessException e) {
      throw new SecretManagementException(ENCRYPT_DECRYPT_ERROR, e, USER);
    }
  }

  @Override
  public PageResponse<SecretUsageLog> getUsageLogs(PageRequest<SecretUsageLog> pageRequest, String accountId,
      String entityId, SettingVariableTypes variableType) throws IllegalAccessException {
    List<String> secretIds = getSecretIds(accountId, Lists.newArrayList(entityId), variableType);
    // PL-3298: Some setting attribute doesn't have encrypted fields and therefore no secret Ids associated with it.
    // E.g. PHYSICAL_DATA_CENTER config. An empty response will be returned.
    if (isEmpty(secretIds)) {
      return new PageResponse<>(pageRequest);
    }

    pageRequest.addFilter(SecretChangeLogKeys.encryptedDataId, Operator.IN, secretIds.toArray());
    pageRequest.addFilter(SecretChangeLogKeys.accountId, Operator.EQ, accountId);
    PageResponse<SecretUsageLog> response = wingsPersistence.query(SecretUsageLog.class, pageRequest);
    response.getResponse().forEach(secretUsageLog -> {
      if (isNotEmpty(secretUsageLog.getWorkflowExecutionId())) {
        WorkflowExecution workflowExecution =
            wingsPersistence.get(WorkflowExecution.class, secretUsageLog.getWorkflowExecutionId());
        if (workflowExecution != null) {
          secretUsageLog.setWorkflowExecutionName(workflowExecution.normalizedName());
        }
      }
    });
    return response;
  }

  @Override
  public Set<SecretSetupUsage> getSecretUsage(String accountId, String secretId) {
    return secretSetupUsageService.getSecretUsage(accountId, secretId);
  }

  private Map<String, Long> getUsageLogSizes(
      String accountId, Collection<String> entityIds, SettingVariableTypes variableType) throws IllegalAccessException {
    List<String> secretIds = getSecretIds(accountId, entityIds, variableType);
    Query<SecretUsageLog> query = wingsPersistence.createQuery(SecretUsageLog.class)
                                      .filter(SecretUsageLogKeys.accountId, accountId)
                                      .field(SecretUsageLogKeys.encryptedDataId)
                                      .in(secretIds)
                                      .field(SecretUsageLogKeys.createdAt)
                                      .greaterThan(Instant.now().minus(90, ChronoUnit.DAYS).toEpochMilli());

    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDefaultAnalyticsDatastore(SecretUsageLog.class)
            .createAggregation(SecretUsageLog.class)
            .match(query)
            .group(SecretChangeLogKeys.encryptedDataId, grouping("count", new Accumulator("$sum", 1)))
            .project(projection(SecretChangeLogKeys.encryptedDataId, ID_KEY), projection("count"));

    List<SecretUsageSummary> secretUsageSummaries = new ArrayList<>();
    aggregationPipeline
        .aggregate(SecretUsageSummary.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(SecretUsageLog.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(secretUsageSummaries::add);

    return secretUsageSummaries.stream().collect(
        Collectors.toMap(summary -> summary.encryptedDataId, summary -> summary.count));
  }

  @Override
  public List<SecretChangeLog> getChangeLogs(String accountId, String entityId, SettingVariableTypes variableType)
      throws IllegalAccessException {
    EncryptedData encryptedData = null;
    if (variableType == SettingVariableTypes.SECRET_TEXT) {
      encryptedData = wingsPersistence.get(EncryptedData.class, entityId);
    }

    return getChangeLogsInternal(accountId, entityId, encryptedData, variableType);
  }

  private List<SecretChangeLog> getChangeLogsInternal(String accountId, String entityId, EncryptedData encryptedData,
      SettingVariableTypes variableType) throws IllegalAccessException {
    List<String> secretIds = getSecretIds(accountId, Lists.newArrayList(entityId), variableType);
    List<SecretChangeLog> secretChangeLogs = wingsPersistence.createQuery(SecretChangeLog.class, excludeCount)
                                                 .filter(SecretChangeLogKeys.accountId, accountId)
                                                 .field(SecretChangeLogKeys.encryptedDataId)
                                                 .hasAnyOf(secretIds)
                                                 .order("-" + CREATED_AT_KEY)
                                                 .asList();

    // HAR-7150: Retrieve version history/changelog from Vault if secret text is a path reference.
    if (variableType == SettingVariableTypes.SECRET_TEXT && encryptedData != null) {
      EncryptionType encryptionType = encryptedData.getEncryptionType();
      if (encryptionType == EncryptionType.VAULT && isNotEmpty(encryptedData.getPath())) {
        VaultConfig vaultConfig = (VaultConfig) secretManagerConfigService.getSecretManager(
            accountId, encryptedData.getKmsId(), encryptedData.getEncryptionType());
        secretChangeLogs.addAll(vaultService.getVaultSecretChangeLogs(encryptedData, vaultConfig));
        // Sort the change log by change time in descending order.
        secretChangeLogs.sort(
            (SecretChangeLog o1, SecretChangeLog o2) -> (int) (o2.getLastUpdatedAt() - o1.getLastUpdatedAt()));
      }
    }

    return secretChangeLogs;
  }

  private Map<String, Long> getChangeLogSizes(
      String accountId, Collection<String> entityIds, SettingVariableTypes variableType) throws IllegalAccessException {
    List<String> secretIds = getSecretIds(accountId, entityIds, variableType);
    Query<SecretChangeLog> query = wingsPersistence.createQuery(SecretChangeLog.class)
                                       .filter(SecretChangeLogKeys.accountId, accountId)
                                       .field(SecretChangeLogKeys.encryptedDataId)
                                       .in(secretIds);

    AggregationPipeline aggregationPipeline =
        wingsPersistence.getDefaultAnalyticsDatastore(SecretChangeLog.class)
            .createAggregation(SecretChangeLog.class)
            .match(query)
            .group(SecretChangeLogKeys.encryptedDataId, grouping("count", new Accumulator("$sum", 1)))
            .project(projection(SecretChangeLogKeys.encryptedDataId, ID_KEY), projection("count"));

    List<ChangeLogSummary> changeLogSummaries = new ArrayList<>();
    aggregationPipeline
        .aggregate(ChangeLogSummary.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(SecretChangeLog.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(changeLogSummaries::add);

    return changeLogSummaries.stream().collect(
        Collectors.toMap(summary -> summary.encryptedDataId, summary -> summary.count));
  }

  @Override
  public Collection<SettingAttribute> listEncryptedSettingAttributes(String accountId) {
    return listEncryptedSettingAttributes(accountId,
        Sets.newHashSet(SettingCategory.CLOUD_PROVIDER.name(), SettingCategory.CONNECTOR.name(),
            SettingCategory.SETTING.name(), SettingCategory.HELM_REPO.name(), SettingCategory.AZURE_ARTIFACTS.name()));
  }

  @Override
  public Collection<SettingAttribute> listEncryptedSettingAttributes(String accountId, Set<String> categories) {
    // 1. Fetch all setting attributes belonging to the specified category or all settings categories.
    List<SettingAttribute> settingAttributeList = new ArrayList<>();
    Set<String> settingAttributeIds = new HashSet<>();

    // Exclude STRING type of setting attribute as they are never displayed in secret management section.
    Query<SettingAttribute> categoryQuery = wingsPersistence.createQuery(SettingAttribute.class)
                                                .filter(SettingAttributeKeys.accountId, accountId)
                                                .field(SettingAttributeKeys.category)
                                                .in(categories)
                                                .field(SettingAttributeKeys.value_type)
                                                .notIn(Lists.newArrayList(SettingVariableTypes.STRING));
    loadSettingQueryResult(categoryQuery, settingAttributeIds, settingAttributeList);

    // If SETTING category is included, then make sure WINRM related settings get loaded as it's category field is
    // empty in persistence store and the filter need special handling.
    if (categories.contains(SettingCategory.SETTING.name())) {
      // PL-3318: Some WINRM connection attribute does not have category field set SHOULD be included in the result
      // set.
      Query<SettingAttribute> winRmQuery =
          wingsPersistence.createQuery(SettingAttribute.class)
              .filter(SettingAttributeKeys.accountId, accountId)
              .field(SettingAttributeKeys.category)
              .doesNotExist()
              .field(SettingAttributeKeys.value_type)
              .in(Lists.newArrayList(SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES));
      loadSettingQueryResult(winRmQuery, settingAttributeIds, settingAttributeList);
    }

    // 2. Fetch children encrypted records associated with these setting attributes in a batch
    Map<String, EncryptedData> encryptedDataMap = new HashMap<>();
    try (HIterator<EncryptedData> query = new HIterator<>(wingsPersistence.createQuery(EncryptedData.class)
                                                              .filter(EncryptedDataKeys.accountId, accountId)
                                                              .field(PARENT_ID_KEY)
                                                              .in(settingAttributeIds)
                                                              .fetch())) {
      for (EncryptedData encryptedData : query) {
        for (EncryptedDataParent encryptedDataParent : encryptedData.getParents()) {
          encryptedDataMap.put(encryptedDataParent.getId(), encryptedData);
        }
      }
    }

    // 3. Set 'encryptionType' and 'encryptedBy' field of setting attributes based on children encrypted record
    // association
    List<SettingAttribute> finalList = new ArrayList<>();
    for (SettingAttribute settingAttribute : settingAttributeList) {
      EncryptedData encryptedData = encryptedDataMap.get(settingAttribute.getUuid());
      if (encryptedData != null) {
        settingAttribute.setEncryptionType(encryptedData.getEncryptionType());
        settingAttribute.setEncryptedBy(
            secretManagerConfigService.getSecretManagerName(encryptedData.getKmsId(), accountId));
        finalList.add(settingAttribute);
      } else if (settingAttribute.getCategory() == SettingCategory.SETTING) {
        finalList.add(settingAttribute);
      }
    }

    return finalList;
  }

  private void loadSettingQueryResult(
      Query<SettingAttribute> query, Set<String> settingAttributeIds, List<SettingAttribute> settingAttributeList) {
    try (HIterator<SettingAttribute> queryIter = new HIterator<>(query.fetch())) {
      for (SettingAttribute settingAttribute : queryIter) {
        settingAttributeList.add(settingAttribute);
        settingAttributeIds.add(settingAttribute.getUuid());
        settingServiceHelper.updateSettingAttributeBeforeResponse(settingAttribute, true);
      }
    }
  }

  @Override
  public String getEncryptedYamlRef(String accountId, String secretId) {
    if (isEmpty(accountId) || isEmpty(secretId)) {
      return null;
    }
    return secretYamlHandler.toYaml(accountId, secretId);
  }

  @Override
  public EncryptedData getEncryptedDataFromYamlRef(String encryptedYamlRef, String accountId) {
    checkState(isNotEmpty(encryptedYamlRef), ENCRYPT_DECRYPT_ERROR, "Null encrypted YAML reference");
    return secretYamlHandler.fromYaml(accountId, encryptedYamlRef);
  }

  @Override
  public boolean transitionSecrets(String accountId, EncryptionType fromEncryptionType, String fromSecretManagerId,
      EncryptionType toEncryptionType, String toSecretManagerId,
      Map<String, String> runtimeParametersForSourceSecretManager,
      Map<String, String> runtimeParametersForDestinationSecretManager) {
    SecretManagerConfig sourceSecretManagerConfig =
        secretManagerConfigService.getSecretManager(accountId, fromSecretManagerId, fromEncryptionType);
    SecretManagerConfig destinationSecretManagerConfig =
        secretManagerConfigService.getSecretManager(accountId, toSecretManagerId, toEncryptionType);
    secretManagerConfigService.canTransitionSecrets(
        accountId, sourceSecretManagerConfig, destinationSecretManagerConfig);
    return secretService.migrateSecrets(accountId, sourceSecretManagerConfig, destinationSecretManagerConfig);
  }

  @Override
  public EncryptedData getSecretMappedToAccountByName(String accountId, String name) {
    return secretService.getAccountScopedSecretByName(accountId, name).orElse(null);
  }

  @Override
  public EncryptedData getSecretMappedToAppByName(String accountId, String appId, String envId, String name) {
    return secretService.getSecretByName(accountId, name, appId, envId).orElse(null);
  }

  @Override
  public EncryptedData getSecretById(String accountId, String secretRecordId) {
    return secretService.getSecretById(accountId, secretRecordId).orElse(null);
  }

  @Override
  public EncryptedData getSecretByName(String accountId, String secretName) {
    return secretService.getSecretByName(accountId, secretName).orElse(null);
  }

  @Override
  public List<String> importSecretsViaFile(String accountId, InputStream uploadStream) {
    List<SecretText> secretTexts = new ArrayList<>();

    InputStreamReader inputStreamReader = null;
    BufferedReader reader = null;
    try {
      inputStreamReader = new InputStreamReader(uploadStream, Charset.defaultCharset());
      reader = new BufferedReader(inputStreamReader);
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        String path = parts.length > 2 ? trim(parts[2]) : null;
        SecretText secretText =
            (SecretText) SecretText.builder().value(trim(parts[1])).path(path).name(trim(parts[0])).build();
        secretTexts.add(secretText);
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Error while importing secrets for accountId " + accountId, e);
    } finally {
      try {
        if (reader != null) {
          reader.close();
        }
        if (inputStreamReader != null) {
          inputStreamReader.close();
        }
      } catch (IOException e) {
        // Ignore.
      }
    }
    return importSecrets(accountId, secretTexts);
  }

  @Override
  public List<String> importSecrets(String accountId, List<SecretText> secretTexts) {
    List<String> secretIds = new ArrayList<>();
    for (SecretText secretText : secretTexts) {
      try {
        String secretId = saveSecretText(accountId, secretText, true);
        secretIds.add(secretId);
        log.info("Imported secret '{}' successfully with uid: {}", secretText.getName(), secretId);
      } catch (WingsException e) {
        log.warn("Failed to save import secret '{}' with error: {}", secretText.getName(), e.getMessage());
      }
    }
    return secretIds;
  }

  @Override
  public String saveSecretUsingLocalMode(String accountId, SecretText secretText) {
    secretText.setKmsId(accountId);
    return saveSecretText(accountId, secretText, true);
  }

  @Override
  public boolean transitionAllSecretsToHarnessSecretManager(String accountId) {
    // For now, the default/harness secret manager is the LOCAL secret manager, and it's HIDDEN from end-user
    EncryptionConfig harnessSecretManager = localSecretManagerService.getEncryptionConfig(accountId);
    List<SecretManagerConfig> allEncryptionConfigs = listSecretManagers(accountId);
    for (EncryptionConfig encryptionConfig : allEncryptionConfigs) {
      log.info("Transitioning secret from secret manager {} of type {} into Harness secret manager for account {}",
          encryptionConfig.getUuid(), encryptionConfig.getEncryptionType(), accountId);
      transitionSecrets(accountId, encryptionConfig.getEncryptionType(), encryptionConfig.getUuid(),
          harnessSecretManager.getEncryptionType(), harnessSecretManager.getUuid(), new HashMap<>(), new HashMap<>());
    }
    return true;
  }

  @Override
  public Optional<software.wings.beans.dto.SecretManagerRuntimeParameters>
  getSecretManagerRuntimeCredentialsForExecution(String executionId, String secretManagerId) {
    SecretManagerRuntimeParameters secretManagerRuntimeParameters =
        wingsPersistence.createQuery(SecretManagerRuntimeParameters.class)
            .field(SecretManagerRuntimeParametersKeys.executionId)
            .equal(executionId)
            .field(SecretManagerRuntimeParametersKeys.secretManagerId)
            .equal(secretManagerId)
            .get();
    if (Objects.nonNull(secretManagerRuntimeParameters)) {
      EncryptedData encryptedData =
          wingsPersistence.get(EncryptedData.class, secretManagerRuntimeParameters.getRuntimeParameters());
      secretManagerRuntimeParameters.setRuntimeParameters(
          String.valueOf(secretService.fetchSecretValue(encryptedData)));
      return Optional.of(secretManagerRuntimeParameters.toDto());
    }
    return Optional.empty();
  }

  @Override
  public UsageRestrictions getAllowedUsageScopesToCreateSecret(String accountId, String secretsManagerConfigId) {
    return secretManagerConfigService.getMaximalAllowedScopes(accountId, secretsManagerConfigId);
  }

  @Override
  public software.wings.beans.dto.SecretManagerRuntimeParameters configureSecretManagerRuntimeCredentialsForExecution(
      String accountId, String kmsId, String executionId, Map<String, String> runtimeParameters) {
    String runtimeParametersString = JsonUtils.asJson(runtimeParameters);
    EncryptedData encryptedData = encryptLocal(runtimeParametersString.toCharArray());
    encryptedData.setAccountId(accountId);
    encryptedData.setName(String.format("%s_%s_%s", executionId, kmsId, accountId));
    encryptedData.setEncryptionType(VAULT);
    String encryptedDataId = secretsDao.saveSecret(encryptedData);
    SecretManagerRuntimeParameters secretManagerRuntimeParameters = SecretManagerRuntimeParameters.builder()
                                                                        .executionId(executionId)
                                                                        .accountId(accountId)
                                                                        .secretManagerId(kmsId)
                                                                        .runtimeParameters(encryptedDataId)
                                                                        .build();
    wingsPersistence.save(secretManagerRuntimeParameters);
    return secretManagerRuntimeParameters.toDto();
  }

  @Override
  public boolean updateUsageRestrictionsForSecretOrFile(String accountId, String uuId,
      UsageRestrictions usageRestrictions, boolean scopedToAccount, boolean inheritScopesFromSM) {
    return secretService.updateSecretScopes(accountId, uuId, usageRestrictions, scopedToAccount, inheritScopesFromSM);
  }

  @Override
  public String saveSecretText(String accountId, SecretText secretText, boolean validateScopes) {
    return createHarnessSecret(accountId, secretText, validateScopes);
  }

  @Override
  public EncryptedData encryptSecretUsingGlobalSM(String accountId, SecretText secretText, boolean validateScopes) {
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getGlobalSecretManager(accountId);
    String kmsId = isNull(secretManagerConfig) ? null : secretManagerConfig.getUuid();
    secretText.setKmsId(kmsId);
    return secretService.createSecret(accountId, secretText, validateScopes);
  }

  @Override
  public String fetchSecretValue(String accountId, String secretRecordId) {
    EncryptedData encryptedData = getSecretById(accountId, secretRecordId);
    return String.valueOf(secretService.fetchSecretValue(encryptedData));
  }

  @Override
  public EncryptedData encryptSecret(String accountId, SecretText secret, boolean validateScopes) {
    return secretService.encryptSecret(accountId, secret, validateScopes);
  }

  @Override
  public String saveSecretFile(String accountId, SecretFile secretFile) {
    return createHarnessSecret(accountId, secretFile, true);
  }

  private String createHarnessSecret(String accountId, HarnessSecret secret, boolean validateScopes) {
    return secretService.createSecret(accountId, secret, validateScopes).getUuid();
  }

  @Override
  public boolean updateSecretText(
      String accountId, String existingRecordId, SecretText secretText, boolean validateScopes) {
    return updateHarnessSecret(accountId, existingRecordId, secretText, validateScopes);
  }

  @Override
  public boolean updateSecretFile(String accountId, String existingRecordId, SecretFile secretFile) {
    return updateHarnessSecret(accountId, existingRecordId, secretFile, true);
  }

  private boolean updateHarnessSecret(
      String accountId, String existingRecordId, HarnessSecret secret, boolean validateScopes) {
    return secretService.updateSecret(accountId, secret, existingRecordId, validateScopes);
  }

  @Override
  public boolean deleteSecret(
      String accountId, String existingRecordId, Map<String, String> runtimeParameters, boolean validateScopes) {
    return secretService.deleteSecret(accountId, existingRecordId, validateScopes, runtimeParameters);
  }

  @Override
  public void deleteByAccountId(String accountId) {
    List<EncryptedData> encryptedDataList = wingsPersistence.createQuery(EncryptedData.class)
                                                .filter(EncryptedDataKeys.accountId, accountId)
                                                .field(EncryptedDataKeys.ngMetadata)
                                                .equal(null)
                                                .asList();
    for (EncryptedData encryptedData : encryptedDataList) {
      try {
        deleteSecret(accountId, encryptedData.getUuid(), new HashMap<>(), true);
      } catch (SecretManagementException e) {
        log.error("Could not delete secret due to the following error: {}", e.getMessage(), e);
      }
    }
  }

  @Override
  public File getFile(String accountId, String uuid, File readInto) {
    byte[] fileContent = getFileContents(accountId, uuid);
    if (fileContent == null) {
      return readInto;
    }
    try {
      Files.write(fileContent, readInto);
    } catch (IOException e) {
      throw new SecretManagementException(
          SECRET_MANAGEMENT_ERROR, "Unexpected IO Exception while reading file", e, USER);
    }
    return readInto;
  }

  @Override
  public byte[] getFileContents(String accountId, String uuid) {
    String fileContent = getFileInternal(accountId, uuid);
    if (fileContent == null) {
      return null;
    }
    return fileContent.getBytes(CHARSET);
  }

  private String getFileInternal(String accountId, String uuid) {
    EncryptedData encryptedData =
        secretsDao.getSecretById(accountId, uuid).<SecretManagementException>orElseThrow(() -> {
          String message = String.format("Could not find the secret with secret id %s to get content", uuid);
          throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, message, USER);
        });
    char[] fileContent = secretService.fetchSecretValue(encryptedData);
    if (fileContent == null) {
      return null;
    }
    return new String(fileContent);
  }

  @Override
  public PageResponse<EncryptedData> listSecrets(String accountId, PageRequest<EncryptedData> pageRequest,
      String appIdFromRequest, String envIdFromRequest, boolean details, boolean listHidden, boolean ignoreRunTimeUsage)
      throws IllegalAccessException {
    if (!listHidden) {
      addFilterHideFromListing(pageRequest);
    }

    return listSecrets(accountId, pageRequest, appIdFromRequest, envIdFromRequest, details, ignoreRunTimeUsage);
  }

  private void addFilterHideFromListing(PageRequest<EncryptedData> pageRequest) {
    SearchFilter op1 =
        SearchFilter.builder().fieldName(EncryptedDataKeys.hideFromListing).op(Operator.NOT_EXISTS).build();

    SearchFilter op2 = SearchFilter.builder()
                           .fieldName(EncryptedDataKeys.hideFromListing)
                           .op(Operator.EQ)
                           .fieldValues(new Object[] {Boolean.FALSE})
                           .build();

    pageRequest.addFilter(EncryptedDataKeys.hideFromListing, Operator.OR, op1, op2);
  }

  @Override
  public PageResponse<EncryptedData> listSecrets(String accountId, PageRequest<EncryptedData> pageRequest,
      String appIdFromRequest, String envIdFromRequest, boolean details, boolean ignoreRunTimeUsage)
      throws IllegalAccessException {
    PageResponse<EncryptedData> pageResponse =
        secretService.listSecrets(accountId, pageRequest, appIdFromRequest, envIdFromRequest);
    if (details) {
      fillInDetails(accountId, pageResponse.getResponse(), ignoreRunTimeUsage);
    }
    return pageResponse;
  }

  private void fillInDetails(String accountId, List<EncryptedData> encryptedDataList, boolean ignoreRunTimeUsage)
      throws IllegalAccessException {
    if (isEmpty(encryptedDataList)) {
      return;
    }

    Set<String> encryptedDataIds = encryptedDataList.stream().map(EncryptedData::getUuid).collect(Collectors.toSet());

    Map<String, Long> changeLogSizes = getChangeLogSizes(accountId, encryptedDataIds, SettingVariableTypes.SECRET_TEXT);

    for (EncryptedData encryptedData : encryptedDataList) {
      String entityId = encryptedData.getUuid();

      encryptedData.setEncryptedBy(
          secretManagerConfigService.getSecretManagerName(encryptedData.getKmsId(), accountId));
      int secretUsageSize = encryptedData.getParents().size();
      encryptedData.setSetupUsage(secretUsageSize);

      if (ignoreRunTimeUsage) {
        encryptedData.setRunTimeUsage(null);
      } else {
        Map<String, Long> usageLogSizes =
            getUsageLogSizes(accountId, encryptedDataIds, SettingVariableTypes.SECRET_TEXT);
        if (usageLogSizes.containsKey(entityId)) {
          encryptedData.setRunTimeUsage((long) usageLogSizes.get(entityId).intValue());
        }
      }

      if (changeLogSizes.containsKey(entityId)) {
        encryptedData.setChangeLog(changeLogSizes.get(entityId).intValue());
      }
    }
  }

  @Override
  public PageResponse<EncryptedData> listSecretsMappedToAccount(
      String accountId, PageRequest<EncryptedData> pageRequest, boolean details, boolean ignoreRunTimeUsage)
      throws IllegalAccessException {
    PageResponse<EncryptedData> pageResponse = secretService.listSecretsScopedToAccount(accountId, pageRequest);
    if (details) {
      fillInDetails(accountId, pageResponse.getResponse(), ignoreRunTimeUsage);
    }
    return pageResponse;
  }

  private List<String> getSecretIds(String accountId, Collection<String> entityIds, SettingVariableTypes variableType)
      throws IllegalAccessException {
    List<String> secretIds = new ArrayList<>();
    switch (variableType) {
      case SERVICE_VARIABLE:
        ServiceVariable serviceVariable = wingsPersistence.createQuery(ServiceVariable.class)
                                              .filter(ServiceVariableKeys.accountId, accountId)
                                              .field(ID_KEY)
                                              .in(entityIds)
                                              .get();

        if (serviceVariable != null) {
          List<Field> encryptedFields = getEncryptedFields(serviceVariable.getClass());

          for (Field field : encryptedFields) {
            Field encryptedRefField = getEncryptedRefField(field, serviceVariable);
            encryptedRefField.setAccessible(true);
            secretIds.add((String) encryptedRefField.get(serviceVariable));
          }
        }
        break;

      case CONFIG_FILE:
      case SECRET_TEXT:
        secretIds.addAll(entityIds);
        break;

      default:
        SettingAttribute settingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                                .filter(SettingAttributeKeys.accountId, accountId)
                                                .field(ID_KEY)
                                                .in(entityIds)
                                                .get();

        if (settingAttribute != null) {
          List<Field> encryptedFields = getEncryptedFields(settingAttribute.getValue().getClass());

          for (Field field : encryptedFields) {
            Field encryptedRefField = getEncryptedRefField(field, (EncryptableSetting) settingAttribute.getValue());
            encryptedRefField.setAccessible(true);
            secretIds.add((String) encryptedRefField.get(settingAttribute.getValue()));
          }
        }
    }
    return secretIds;
  }

  public boolean canUseSecretsInAppAndEnv(
      @NonNull Set<String> secretIds, @NonNull String accountId, String appIdFromRequest, String envIdFromRequest) {
    return secretService.hasAccessToReadSecrets(accountId, secretIds, appIdFromRequest, envIdFromRequest);
  }

  @Override
  public List<SecretMetadata> filterSecretIdsByReadPermission(Set<String> secretIds, String accountId,
      String appIdFromRequest, String envIdFromRequest, boolean forUsageInNewApp) {
    return secretService.filterSecretIdsByReadPermission(
        secretIds, accountId, appIdFromRequest, envIdFromRequest, forUsageInNewApp);
  }

  public boolean hasUpdateAccessToSecrets(@NonNull Set<String> secretIds, @NonNull String accountId) {
    return secretService.hasAccessToEditSecrets(accountId, secretIds);
  }

  @Builder
  private static class SecretUsageSummary {
    String encryptedDataId;
    long count;
  }

  @Builder
  private static class ChangeLogSummary {
    String encryptedDataId;
    long count;
  }
}
