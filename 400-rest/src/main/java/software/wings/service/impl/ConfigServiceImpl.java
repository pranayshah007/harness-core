/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.FileBucket.CONFIGS;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.ConfigFile.DEFAULT_TEMPLATE_ID;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.EntityType.SERVICE_TEMPLATE;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SecretUsageLog;
import io.harness.delegate.beans.FileBucket;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptionType;
import io.harness.stream.BoundedInputStream;

import software.wings.beans.Activity;
import software.wings.beans.ConfigFile;
import software.wings.beans.ConfigFile.ConfigFileKeys;
import software.wings.beans.EntityType;
import software.wings.beans.EntityVersion;
import software.wings.beans.EntityVersion.ChangeType;
import software.wings.beans.Event.Type;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EntityVersionService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.yaml.YamlPushService;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.validation.executable.ValidateOnExecution;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

/**
 * Created by anubhaw on 4/25/16.
 */
@ValidateOnExecution
@Singleton
public class ConfigServiceImpl implements ConfigService {
  /**
   * The Executor service.
   */
  @Inject ExecutorService executorService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private FileService fileService;
  @Inject private HostService hostService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private EntityVersionService entityVersionService;
  @Inject private EnvironmentService environmentService;
  @Inject private SecretManager secretManager;
  @Inject private ActivityService activityService;
  @Inject private YamlPushService yamlPushService;

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ConfigService#list(software.wings.dl.PageRequest)
   */
  @Override
  public PageResponse<ConfigFile> list(PageRequest<ConfigFile> request) {
    PageResponse<ConfigFile> response = wingsPersistence.query(ConfigFile.class, request);
    response.getResponse().forEach(configFile -> {
      if (configFile.isEncrypted()) {
        EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, configFile.getEncryptedFileId());
        Preconditions.checkNotNull(encryptedData, "No encrypted record found for " + configFile.getUuid());
        configFile.setSecretFileName(encryptedData.getName());
      }
    });
    return response;
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ConfigService#save(software.wings.beans.ConfigFile, java.io.InputStream)
   */
  @Override
  public String save(ConfigFile configFile, BoundedInputStream inputStream) {
    validateEntity(configFile.getAppId(), configFile.getEntityId(), configFile.getEntityType());
    String envId = configFile.getEntityType() == SERVICE || configFile.getEntityType() == ENVIRONMENT
        ? GLOBAL_ENV_ID
        : serviceTemplateService.get(configFile.getAppId(), configFile.getTemplateId()).getEnvId();

    configFile.setEnvId(envId);
    configFile.setRelativeFilePath(validateAndResolveFilePath(configFile.getRelativeFilePath()));
    configFile.setDefaultVersion(1);
    String fileId = null;
    if (configFile.isEncrypted()) {
      EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, configFile.getEncryptedFileId());
      Preconditions.checkNotNull(encryptedData, "No encrypted record found " + configFile);
      // HAR-7996: Only KMS encrypted record it's encrypted value is the file ID.
      if (encryptedData.getEncryptionType() == EncryptionType.KMS) {
        fileId = String.valueOf(encryptedData.getEncryptedValue());
        configFile.setFileUuid(fileId);
      }
      configFile.setSize(encryptedData.getFileSize());
    } else {
      fileId = fileService.saveFile(configFile, inputStream, CONFIGS);
      configFile.setSize(inputStream.getTotalBytesRead()); // set this only after saving file to gridfs
      configFile.setFileUuid(fileId);
    }

    String id = wingsPersistence.save(configFile);
    entityVersionService.newEntityVersion(configFile.getAppId(), EntityType.CONFIG, configFile.getUuid(),
        configFile.getEntityId(), configFile.getFileName(), ChangeType.CREATED, configFile.getNotes());

    if (isNotEmpty(fileId)) {
      fileService.updateParentEntityIdAndVersion(null, id, 1, fileId, null, CONFIGS);
    }

    if (configFile.isEncrypted()) {
      updateParentForEncryptedData(configFile);
    }

    ConfigFile configFileFromDB = get(configFile.getAppId(), id);
    yamlPushService.pushYamlChangeSet(
        configFile.getAccountId(), null, configFileFromDB, Type.CREATE, configFile.isSyncFromGit(), false);

    return id;
  }

  private void updateParentForEncryptedData(ConfigFile configFile) {
    if (isNotBlank(configFile.getEncryptedFileId())) {
      EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, configFile.getEncryptedFileId());
      EncryptedDataParent encryptedDataParent = new EncryptedDataParent(
          configFile.getUuid(), configFile.getSettingType(), configFile.getSettingType().toString());
      encryptedData.addParent(encryptedDataParent);
      wingsPersistence.save(encryptedData);
    }
  }

  private void validateEntity(String appId, String entityId, EntityType entityType) {
    boolean entityExist;
    if (EntityType.SERVICE == entityType) {
      entityExist = serviceResourceService.exist(appId, entityId);
    } else if (EntityType.ENVIRONMENT == entityType) {
      entityExist = environmentService.exist(appId, entityId);
    } else if (EntityType.HOST == entityType) {
      entityExist = hostService.exist(appId, entityId);
    } else if (EntityType.SERVICE_TEMPLATE == entityType) {
      entityExist = serviceTemplateService.exist(appId, entityId);
    } else {
      throw new WingsException(INVALID_ARGUMENT)
          .addParam("args", "Config upload not supported for entityType " + entityType);
    }
    if (!entityExist) {
      throw new InvalidRequestException("Node identifier and node type do not match");
    }
  }

  @Override
  public String validateAndResolveFilePath(String relativePath) {
    try {
      Path path = Paths.get(relativePath);
      if (path.isAbsolute()) {
        throw new WingsException(INVALID_ARGUMENT).addParam("args", "Relative path can not be absolute");
      }
      return path.normalize().toString();
    } catch (InvalidPathException ex) {
      throw new WingsException(INVALID_ARGUMENT).addParam("args", "Invalid relativePath");
    }
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ConfigService#get(java.lang.String)
   */
  @Override
  public ConfigFile get(String appId, String configId) {
    ConfigFile configFile = wingsPersistence.getWithAppId(ConfigFile.class, appId, configId);
    if (configFile == null) {
      throw new WingsException(INVALID_ARGUMENT).addParam("args", "ConfigFile not found");
    }
    return configFile;
  }

  @Override
  public ConfigFile get(String appId, String entityId, EntityType entityType, String relativeFilePath) {
    if (entityType != SERVICE && entityType != SERVICE_TEMPLATE && entityType != ENVIRONMENT) {
      return null;
    }

    Query<ConfigFile> query = wingsPersistence.createQuery(ConfigFile.class);
    return query.filter(ConfigFileKeys.entityId, entityId)
        .filter(ConfigFile.APP_ID_KEY2, appId)
        .filter(ConfigFileKeys.relativeFilePath, relativeFilePath)
        .get();
  }

  @Override
  public List<ConfigFile> getConfigFileByTemplate(String appId, String envId, String serviceTemplateId) {
    return wingsPersistence.createQuery(ConfigFile.class)
        .filter("appId", appId)
        .filter(ConfigFileKeys.envId, envId)
        .filter(ConfigFileKeys.templateId, serviceTemplateId)
        .asList();
  }

  @Override
  public File download(String appId, String configId) {
    ConfigFile configFile = get(appId, configId);
    File file = new File(Files.createTempDir(), new File(configFile.getRelativeFilePath()).getName());
    if (configFile.isEncrypted()) {
      file = getDecryptedFile(configFile, file, appId, null);
    } else {
      fileService.download(configFile.getFileUuid(), file, CONFIGS);
    }
    return file;
  }

  @Override
  public File download(String appId, String configId, Integer version) {
    ConfigFile configFile = get(appId, configId);
    int fileVersion = (version == null) ? configFile.getDefaultVersion() : version;
    String fileId = fileService.getFileIdByVersion(configId, fileVersion, CONFIGS);

    // if default version is already set 0, fileId may be null or not latest.
    if (isEmpty(fileId) || !fileService.getLatestFileId(configId, CONFIGS).equals(fileId)) {
      fileId = configFile.getFileUuid();
    }

    File file = new File(Files.createTempDir(), new File(configFile.getRelativeFilePath()).getName());
    if (configFile.isEncrypted()) {
      file = getDecryptedFile(configFile, file, appId, null);
    } else {
      fileService.download(fileId, file, CONFIGS);
    }
    return file;
  }

  @Override
  public File downloadForActivity(String appId, String configId, String activityId) {
    ConfigFile configFile = get(appId, configId);
    File file = new File(Files.createTempDir(), new File(configFile.getRelativeFilePath()).getName());
    if (configFile.isEncrypted()) {
      file = getDecryptedFile(configFile, file, appId, activityId);
    } else {
      fileService.download(configFile.getFileUuid(), file, CONFIGS);
    }
    return file;
  }

  @Override
  public byte[] getFileContent(String appId, ConfigFile configFile) {
    if (configFile.isEncrypted()) {
      return secretManager.getFileContents(configFile.getAccountId(), configFile.getEncryptedFileId());
    } else {
      try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        InputStream inputStream = fileService.openDownloadStream(configFile.getFileUuid(), FileBucket.CONFIGS);
        outputStream.write(ByteStreams.toByteArray(inputStream));
        outputStream.flush();
        return outputStream.toByteArray();
      } catch (IOException e) {
        throw new WingsException(INVALID_ARGUMENT, e)
            .addParam("args", "Failed to get configFile content: " + configFile.getName());
      }
    }
  }

  private File getDecryptedFile(ConfigFile configFile, File file, String appId, String activityId) {
    EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, configFile.getEncryptedFileId());
    Preconditions.checkNotNull(encryptedData);
    if (isNotBlank(activityId)) {
      Activity activity = activityService.get(activityId, appId);
      Preconditions.checkNotNull(activity, "Could not find activity " + activityId + " for app " + appId);
      SecretUsageLog secretUsageLog = SecretUsageLog.builder()
                                          .encryptedDataId(encryptedData.getUuid())
                                          .workflowExecutionId(activity.getWorkflowExecutionId())
                                          .appId(configFile.getAppId())
                                          .envId(activity.getEnvironmentId())
                                          .accountId(encryptedData.getAccountId())
                                          .build();
      wingsPersistence.save(secretUsageLog);
    }

    return secretManager.getFile(encryptedData.getAccountId(), configFile.getEncryptedFileId(), file);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ConfigService#update(software.wings.beans.ConfigFile, java.io.InputStream)
   */
  @Override
  public void update(ConfigFile inputConfigFile, BoundedInputStream uploadedInputStream) {
    ConfigFile savedConfigFile = get(inputConfigFile.getAppId(), inputConfigFile.getUuid());
    notNullCheck("Configuration File", savedConfigFile);

    checkDuplicateNames(savedConfigFile, inputConfigFile);

    if ((savedConfigFile.getEntityType() == SERVICE || savedConfigFile.getEntityType() == ENVIRONMENT)
        && !savedConfigFile.getRelativeFilePath().equals(inputConfigFile.getRelativeFilePath())) {
      updateRelativeFilePathForServiceAndAllOverrideFiles(savedConfigFile, inputConfigFile.getRelativeFilePath());
    }

    Map<String, Object> updateMap = new HashMap<>();

    inputConfigFile.setEntityType(savedConfigFile.getEntityType());
    String fileId = null;
    if (inputConfigFile.isEncrypted()) {
      EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, inputConfigFile.getEncryptedFileId());
      Preconditions.checkNotNull(encryptedData, "No encrypted record found " + inputConfigFile);
      fileId = String.valueOf(encryptedData.getEncryptedValue());
      updateMap.put("encryptedFileId", inputConfigFile.getEncryptedFileId());
      updateMap.put("size", encryptedData.getFileSize());
      updateParentForEncryptedData(inputConfigFile);
    } else if (uploadedInputStream != null) {
      fileId = fileService.saveFile(inputConfigFile, uploadedInputStream, CONFIGS);
      updateMap.put("encryptedFileId", "");

      // HAR-8064: some config file entries could have null 'fileUuid/checksum' field. UpdateOperations will reject
      // update of null value.
      if (inputConfigFile.getFileUuid() != null) {
        updateMap.put("fileUuid", inputConfigFile.getFileUuid());
      }
      if (inputConfigFile.getChecksum() != null) {
        updateMap.put("checksum", inputConfigFile.getChecksum());
      }
      updateMap.put("size", uploadedInputStream.getTotalBytesRead());
      updateMap.put("fileName", inputConfigFile.getFileName());
    }
    updateMap.put("encrypted", inputConfigFile.isEncrypted());

    if (isNotBlank(fileId)) {
      EntityVersion entityVersion = entityVersionService.newEntityVersion(inputConfigFile.getAppId(), EntityType.CONFIG,
          inputConfigFile.getUuid(), savedConfigFile.getEntityId(), inputConfigFile.getFileName(), ChangeType.UPDATED,
          inputConfigFile.getNotes());
      if (inputConfigFile.isSetAsDefault()) {
        inputConfigFile.setDefaultVersion(entityVersion.getVersion());
      }

      if (secretManager.getEncryptionType(inputConfigFile.getAccountId()) != EncryptionType.VAULT) {
        fileService.updateParentEntityIdAndVersion(
            null, inputConfigFile.getUuid(), entityVersion.getVersion(), fileId, null, CONFIGS);
      }
    }
    if (inputConfigFile.getDescription() != null) {
      updateMap.put("description", inputConfigFile.getDescription());
    }

    if (inputConfigFile.getEnvIdVersionMap() != null) {
      updateMap.put("envIdVersionMap", inputConfigFile.getEnvIdVersionMap());
    }

    if (inputConfigFile.getEntityType() != SERVICE && inputConfigFile.getConfigOverrideType() != null) {
      updateMap.put("configOverrideType", inputConfigFile.getConfigOverrideType());
    }

    updateMap.put("defaultVersion", savedConfigFile.getDefaultVersion() + 1);

    if (inputConfigFile.getConfigOverrideExpression() != null) {
      updateMap.put("configOverrideExpression", inputConfigFile.getConfigOverrideExpression());
    }

    wingsPersistence.updateFields(ConfigFile.class, inputConfigFile.getUuid(), updateMap);
    ConfigFile updatedConfigFile = get(inputConfigFile.getAppId(), inputConfigFile.getUuid());
    yamlPushService.pushYamlChangeSet(savedConfigFile.getAccountId(), savedConfigFile, updatedConfigFile, Type.UPDATE,
        inputConfigFile.isSyncFromGit(), !savedConfigFile.getFileName().equals(updatedConfigFile.getFileName()));
  }

  /**
   * Update relative file path for service and all override files.
   *
   * @param existingConfigFile  the existing config file
   * @param newRelativeFilePath the new relative file path
   */
  private void updateRelativeFilePathForServiceAndAllOverrideFiles(
      ConfigFile existingConfigFile, String newRelativeFilePath) {
    String resolvedFilePath = validateAndResolveFilePath(newRelativeFilePath);

    List<Object> templateIds =
        serviceTemplateService
            .getTemplateRefKeysByService(existingConfigFile.getAppId(), existingConfigFile.getEntityId(), null)
            .stream()
            .map(Key::getId)
            .collect(toList());

    Query<ConfigFile> query = wingsPersistence.createQuery(ConfigFile.class)
                                  .filter("appId", existingConfigFile.getAppId())
                                  .filter(ConfigFileKeys.relativeFilePath, existingConfigFile.getRelativeFilePath());
    query.or(query.criteria("entityId").equal(existingConfigFile.getEntityId()),
        query.criteria("templateId").in(templateIds));

    UpdateOperations<ConfigFile> updateOperations =
        wingsPersistence.createUpdateOperations(ConfigFile.class).set("relativeFilePath", resolvedFilePath);
    wingsPersistence.update(query, updateOperations);
  }

  /**
   * Checks for duplicate names by searching for an existing config file with the same name and different ID
   *
   * @param savedConfigFile
   * @param inputConfigFile
   */
  private void checkDuplicateNames(ConfigFile savedConfigFile, ConfigFile inputConfigFile) {
    ConfigFile existingConfigFile = get(savedConfigFile.getAppId(), savedConfigFile.getEntityId(),
        savedConfigFile.getEntityType(), inputConfigFile.getRelativeFilePath());
    if (existingConfigFile != null && !existingConfigFile.getUuid().equals(inputConfigFile.getUuid())) {
      throw new InvalidRequestException("Duplicate name " + existingConfigFile.getRelativeFilePath());
    }
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ConfigService#delete(java.lang.String)
   */
  @Override
  public void delete(String appId, String configId) {
    // TODO: migrate to prune pattern

    Query<ConfigFile> query =
        wingsPersistence.createQuery(ConfigFile.class).filter(ConfigFile.APP_ID_KEY2, appId).filter(ID_KEY, configId);
    ConfigFile configFile = query.get();
    if (configFile == null) {
      return;
    }
    boolean deleted = wingsPersistence.delete(query);
    if (deleted) {
      configFileDeletionFollowUp(appId, configId, configFile);
    }
  }

  private void configFileDeletionFollowUp(String appId, String configId, ConfigFile configFile) {
    yamlPushService.pushYamlChangeSet(
        configFile.getAccountId(), configFile, null, Type.DELETE, configFile.isSyncFromGit(), false);
    if (configFile.isEncrypted()) {
      EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, configFile.getEncryptedFileId());
      if (encryptedData != null) {
        EncryptedDataParent encryptedDataParent = new EncryptedDataParent(
            configFile.getUuid(), configFile.getSettingType(), configFile.getSettingType().toString());
        encryptedData.removeParent(encryptedDataParent);
        wingsPersistence.save(encryptedData);
      }
    }

    List<ConfigFile> configFiles = wingsPersistence.createQuery(ConfigFile.class)
                                       .filter(ConfigFile.APP_ID_KEY2, appId)
                                       .filter(ConfigFileKeys.parentConfigFileId, configId)
                                       .asList();
    if (!configFiles.isEmpty()) {
      configFiles.forEach(childConfigFile -> delete(appId, childConfigFile.getUuid()));
    }
    if (!configFile.isEncrypted()) {
      executorService.submit(() -> fileService.deleteAllFilesForEntity(configId, CONFIGS));
    }
  }

  @Override
  public void delete(String appId, String entityId, EntityType entityType, String configFileName) {
    delete(appId, entityId, entityType, configFileName, false);
  }

  @Override
  public void delete(String appId, String entityId, EntityType entityType, String configFileName, boolean syncFromGit) {
    ConfigFile configFile = wingsPersistence.createQuery(ConfigFile.class)
                                .filter(ConfigFile.APP_ID_KEY2, appId)
                                .filter(ConfigFileKeys.entityType, entityType.name())
                                .filter(ConfigFileKeys.entityId, entityId)
                                .filter(ConfigFileKeys.relativeFilePath, configFileName)
                                .get();
    configFile.setSyncFromGit(syncFromGit);

    boolean deleted = wingsPersistence.delete(ConfigFile.class, configFile.getUuid());
    if (deleted) {
      configFileDeletionFollowUp(configFile.getAppId(), configFile.getUuid(), configFile);
    }
  }

  @Override
  public List<ConfigFile> getConfigFilesForEntity(String appId, String templateId, String entityId) {
    return list(aPageRequest()
                    .addFilter("appId", Operator.EQ, appId)
                    .addFilter("templateId", Operator.EQ, templateId)
                    .addFilter("entityId", Operator.EQ, entityId)
                    .build())
        .getResponse();
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.ConfigService#getConfigFilesForEntity(java.lang.String, java.lang.String)
   */
  @Override
  public List<ConfigFile> getConfigFilesForEntity(String appId, String templateId, String entityId, String envId) {
    return list(aPageRequest()
                    .addFilter("appId", Operator.EQ, appId)
                    .addFilter("templateId", Operator.EQ, templateId)
                    .addFilter("entityId", Operator.EQ, entityId)
                    .addFilter(SearchFilter.builder().build())
                    .build())
        .getResponse();
  }

  @Override
  public List<ConfigFile> getConfigFileOverridesForEnv(String appId, String envId) {
    // All service overrides
    List<ConfigFile> configFiles = new ArrayList<>();

    List allServiceOverrideList = list(aPageRequest()
                                           .addFilter("appId", Operator.EQ, appId)
                                           .addFilter("entityType", Operator.EQ, EntityType.ENVIRONMENT)
                                           .addFilter("entityId", Operator.EQ, envId)
                                           .addFilter(SearchFilter.builder().build())
                                           .build())
                                      .getResponse();

    // service override
    List overrideList = list(aPageRequest()
                                 .addFilter("appId", Operator.EQ, appId)
                                 .addFilter("entityType", Operator.EQ, EntityType.SERVICE_TEMPLATE)
                                 .addFilter("envId", Operator.EQ, envId)
                                 .addFilter(SearchFilter.builder().build())
                                 .build())
                            .getResponse();

    configFiles.addAll(allServiceOverrideList);
    configFiles.addAll(overrideList);

    return configFiles;
  }

  @Override
  public void deleteByEntityId(String appId, String templateId, String entityId) {
    List<ConfigFile> configFiles = getConfigFilesForEntity(appId, templateId, entityId);
    if (configFiles != null) {
      configFiles.forEach(configFile -> delete(appId, configFile.getUuid()));
    }
  }

  @Override
  public void pruneByService(String appId, String entityId) {
    List<ConfigFile> configFiles = getConfigFilesForEntity(appId, DEFAULT_TEMPLATE_ID, entityId);
    if (configFiles != null) {
      configFiles.forEach(configFile -> { delete(appId, configFile.getUuid()); });
    }
  }

  @Override
  public void pruneByEnvironment(String appId, String envId) {
    pruneByService(appId, envId);
  }

  @Override
  public void deleteByTemplateId(String appId, String serviceTemplateId) {
    wingsPersistence.createQuery(ConfigFile.class)
        .filter("appId", appId)
        .filter(ConfigFileKeys.templateId, serviceTemplateId)
        .asList()
        .forEach(configFile -> delete(appId, configFile.getUuid()));
  }

  @Override
  public void deleteByEntityId(String appId, String entityId) {
    wingsPersistence.createQuery(ConfigFile.class)
        .filter("appId", appId)
        .filter(ConfigFileKeys.entityId, entityId)
        .asList()
        .forEach(configFile -> delete(appId, configFile.getUuid()));
  }

  @Override
  public void pruneByHost(String appId, String hostId) {
    deleteByEntityId(appId, hostId);
  }

  @Override
  public ConfigFile getConfigFileForEntityByRelativeFilePath(
      String appId, String templateId, String entityId, String envId, String relativeFilePath) {
    return wingsPersistence.createQuery(ConfigFile.class)
        .filter(ConfigFile.APP_ID_KEY2, appId)
        .filter(ConfigFileKeys.templateId, templateId)
        .filter(ConfigFileKeys.entityId, entityId)
        .filter(ConfigFileKeys.relativeFilePath, relativeFilePath)
        .get();
  }
}
