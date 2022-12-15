/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.exception.WingsException.SRE;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.yaml.YamlConstants.DEFAULTS_YAML;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.beans.yaml.YamlConstants.YAML_EXTENSION;

import static com.google.common.base.Charsets.UTF_8;

import io.harness.delegate.beans.FileBucket;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.ChangeType;

import software.wings.beans.Application;
import software.wings.beans.Base;
import software.wings.beans.ConfigFile;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.HarnessTag;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.SettingAttribute;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.ManifestFile;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.GitFileChange.Builder;
import software.wings.beans.yaml.YamlConstants;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.yaml.EntityUpdateService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.YamlResourceService;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.Utils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Entity Update Service Implementation.
 *
 * @author bsollish
 */
@Singleton
@Slf4j
public class EntityUpdateServiceImpl implements EntityUpdateService {
  @Inject private YamlResourceService yamlResourceService;
  @Inject private YamlDirectoryService yamlDirectoryService;
  @Inject private AppService appService;
  @Inject private YamlHandlerFactory yamlHandlerFactory;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private EnvironmentService environmentService;
  @Inject private FileService fileService;
  @Inject private FeatureFlagService featureFlagService;

  private GitFileChange createGitFileChange(
      String accountId, String path, String name, String yamlContent, ChangeType changeType, boolean isDirectory) {
    return Builder.aGitFileChange()
        .withAccountId(accountId)
        .withChangeType(changeType)
        .withFileContent(yamlContent)
        .withFilePath(
            changeType == ChangeType.DELETE && isDirectory ? path : path + "/" + name + YamlConstants.YAML_EXTENSION)
        .build();
  }

  private GitFileChange createGitChangeWhenUsingActualFile(
      String accountId, String path, String fileName, String content, ChangeType changeType) {
    return Builder.aGitFileChange()
        .withAccountId(accountId)
        .withChangeType(changeType)
        .withFileContent(content)
        .withFilePath(path + "/" + fileName)
        .build();
  }

  @Override
  public List<GitFileChange> getDefaultVarGitSyncFile(String accountId, String appId, ChangeType changeType) {
    // if (ChangeType.DELETE.equals(changeType)) {
    //   TODO: handle this
    //}
    String yaml = yamlResourceService.getDefaultVariables(accountId, appId).getResource().getYaml();

    if (GLOBAL_APP_ID.equals(appId)) {
      return Lists.newArrayList(createGitFileChange(
          accountId, yamlDirectoryService.getRootPath(), YamlConstants.DEFAULTS, yaml, changeType, false));
    } else {
      Application app = appService.get(appId);
      return Lists.newArrayList(createGitFileChange(
          accountId, yamlDirectoryService.getRootPathByApp(app), YamlConstants.DEFAULTS, yaml, changeType, false));
    }
  }

  @Override
  public GitFileChange getCommandGitSyncFile(
      String accountId, Service service, ServiceCommand serviceCommand, ChangeType changeType) {
    String yaml = null;
    if (changeType != ChangeType.DELETE) {
      yaml = yamlResourceService.getServiceCommand(serviceCommand.getAppId(), serviceCommand.getUuid())
                 .getResource()
                 .getYaml();
    }
    return createGitFileChange(accountId, yamlDirectoryService.getRootPathByServiceCommand(service, serviceCommand),
        serviceCommand.getName(), yaml, changeType, false);
  }

  @Override
  public List<GitFileChange> obtainSettingAttributeRenameChangeSet(
      String accountId, SettingAttribute oldSettingAttribute, SettingAttribute newSettingAttribute) {
    List<GitFileChange> changeSet = new ArrayList<>();
    String oldSettingAttrPath;
    List<GitFileChange> newSettingAttrGitSyncFile;

    if (isStringSettingAttributeType(newSettingAttribute)) {
      oldSettingAttrPath =
          yamlDirectoryService.getRootPathBySettingAttribute(oldSettingAttribute) + PATH_DELIMITER + DEFAULTS_YAML;
      newSettingAttrGitSyncFile =
          getDefaultVarGitSyncFile(accountId, newSettingAttribute.getAppId(), ChangeType.MODIFY);
    } else {
      oldSettingAttrPath = yamlDirectoryService.getRootPathBySettingAttribute(oldSettingAttribute) + PATH_DELIMITER
          + oldSettingAttribute.getName() + YAML_EXTENSION;
      newSettingAttrGitSyncFile =
          obtainEntityGitSyncFileChangeSet(accountId, null, newSettingAttribute, ChangeType.MODIFY);
    }

    changeSet.add(GitFileChange.Builder.aGitFileChange()
                      .withAccountId(accountId)
                      .withChangeType(ChangeType.DELETE)
                      .withFilePath(oldSettingAttrPath)
                      .build());
    changeSet.addAll(newSettingAttrGitSyncFile);
    return changeSet;
  }

  @Override
  public List<GitFileChange> obtainDefaultVariableChangeSet(String accountId, String appId, ChangeType changeType) {
    // The default variables is a special case where one yaml is mapped to a list of setting variables.
    // So, even if a default variable is deleted, we should not delete the whole file.
    // Sending DELETE would do that. So, sending MODIFY
    if (ChangeType.DELETE == changeType) {
      changeType = ChangeType.MODIFY;
    }
    return Lists.newArrayList(getDefaultVarGitSyncFile(accountId, appId, changeType));
  }

  private List<GitFileChange> obtainServiceVariableChangeSet(
      String accountId, ServiceVariable serviceVariable, ChangeType changeType) {
    Object entity = obtainEntity(serviceVariable.getAppId(), serviceVariable.getEnvId(), serviceVariable.getEntityId(),
        serviceVariable.getUuid(), serviceVariable.getEntityType());

    return obtainEntityGitSyncFileChangeSet(accountId, null, entity, changeType);
  }

  @Override
  public <R, T> List<GitFileChange> obtainEntityGitSyncFileChangeSet(
      String accountId, R helperEntity, T entity, ChangeType changeType) {
    // TODO @abhinav: refactor the code here to be generic.
    List<GitFileChange> gitFileChanges = new ArrayList<>();
    String yaml = null;

    // Does special handling for some cases
    if (isStringSettingAttributeType(entity)) {
      return obtainDefaultVariableChangeSet(accountId, ((SettingAttribute) entity).getAppId(), changeType);
    } else if (entity instanceof ServiceVariable) {
      return obtainServiceVariableChangeSet(accountId, (ServiceVariable) entity, changeType);
    } else if (entity instanceof HarnessTag) {
      return getHarnessTagsChangeSet(accountId);
    }

    boolean isNonLeafEntity;
    isNonLeafEntity = yamlHandlerFactory.isNonLeafEntity(entity);
    boolean isEntityNeedsActualFile = yamlHandlerFactory.isEntityNeedsActualFile(entity);
    if (changeType != ChangeType.DELETE && !isEntityNeedsActualFile) {
      yaml = yamlResourceService.obtainEntityYamlVersion(accountId, entity).getResource().getYaml();
    }

    String yamlFileName;
    yamlFileName = yamlHandlerFactory.obtainYamlFileName(entity);

    // For Manifest File "/" is allowed in name
    if (!(entity instanceof ManifestFile)) {
      yamlFileName = Utils.normalize(yamlFileName);
    }

    helperEntity = obtainHelperEntity(helperEntity, entity);
    if (!isEntityNeedsActualFile) {
      gitFileChanges.add(createGitFileChange(accountId, yamlDirectoryService.obtainEntityRootPath(helperEntity, entity),
          yamlFileName, yaml, changeType, isNonLeafEntity));
    }
    gitFileChanges.addAll(obtainAdditionalGitSyncFileChangeSet(accountId, helperEntity, entity, changeType));

    return gitFileChanges;
  }

  @Override
  public <R, T> String getEntityRootFilePath(T entity) {
    R helperEntity = obtainHelperEntity(null, entity);
    return yamlDirectoryService.obtainEntityRootPath(helperEntity, entity);
  }

  private <T> boolean isStringSettingAttributeType(T entity) {
    if (entity instanceof SettingAttribute) {
      SettingAttribute settingAttribute = (SettingAttribute) entity;
      return SettingVariableTypes.STRING.name().equals(settingAttribute.getValue().getType());
    }

    return false;
  }

  private <R, T> R obtainHelperEntity(R helperEntity, T entity) {
    if (entity instanceof ConfigFile) {
      ConfigFile configFile = (ConfigFile) entity;
      return obtainEntity(configFile.getAppId(), configFile.getEnvId(), configFile.getEntityId(), configFile.getUuid(),
          configFile.getEntityType());
    } else if (entity instanceof ServiceCommand) {
      ServiceCommand serviceCommand = (ServiceCommand) entity;
      return obtainEntity(
          serviceCommand.getAppId(), null, serviceCommand.getServiceId(), serviceCommand.getUuid(), EntityType.SERVICE);
    }

    return helperEntity;
  }

  private <R> R obtainEntity(String appId, String envId, String entityId, String uuid, EntityType type) {
    if (type == EntityType.SERVICE) {
      return (R) serviceResourceService.getWithDetails(appId, entityId);
    } else if (type == EntityType.SERVICE_TEMPLATE || type == EntityType.ENVIRONMENT) {
      String finalEnvId = null;

      if (type == EntityType.SERVICE_TEMPLATE) {
        finalEnvId = envId;
      } else if (type == EntityType.ENVIRONMENT) {
        finalEnvId = entityId;
      }

      Environment environment = environmentService.get(appId, finalEnvId, false);
      notNullCheck("Environment not found for the given id:" + finalEnvId, environment);
      return (R) environment;
    } else {
      String msg = "Unsupported type " + type + " for id " + uuid;
      log.error(msg);
      throw new WingsException(msg);
    }
  }

  private <R, T> List<GitFileChange> obtainAdditionalGitSyncFileChangeSet(
      String accountId, R helperEntity, T entity, ChangeType changeType) {
    List<GitFileChange> gitFileChanges = new ArrayList<>();

    if (entity instanceof Service) {
      Service service = (Service) entity;

      if (changeType == ChangeType.ADD && !serviceResourceService.hasInternalCommands(service)) {
        serviceResourceService.getServiceCommands(service.getAppId(), service.getUuid())
            .forEach(serviceCommand
                -> gitFileChanges.add(getCommandGitSyncFile(accountId, service, serviceCommand, ChangeType.ADD)));
      }
    } else if (entity instanceof ConfigFile) {
      ConfigFile configFile = (ConfigFile) entity;
      // When its delete, fileContent would have been already deleted at this point. No point in fetching it
      String fileContent = ChangeType.DELETE == changeType ? StringUtils.EMPTY : loadFileContentIntoString(configFile);
      String fileName = Utils.normalize(configFile.getRelativeFilePath());

      if (fileContent != null) {
        gitFileChanges.add(createGitChangeWhenUsingActualFile(accountId,
            yamlDirectoryService.getRootPathByConfigFile(helperEntity, entity), fileName, fileContent, changeType));
      }
    } else if (entity instanceof ManifestFile) {
      ManifestFile manifestFile = (ManifestFile) entity;
      String path = yamlDirectoryService.getRootPathByManifestFile(manifestFile, (ApplicationManifest) helperEntity);
      gitFileChanges.add(createGitChangeWhenUsingActualFile(
          accountId, path, manifestFile.getFileName(), manifestFile.getFileContent(), changeType));
    }

    return gitFileChanges;
  }

  private String loadFileContentIntoString(ConfigFile configFile) {
    if (!configFile.isEncrypted()) {
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      fileService.downloadToStream(configFile.getFileUuid(), outputStream, FileBucket.CONFIGS);
      return new String(outputStream.toByteArray(), UTF_8);
    }
    return null;
  }

  @Override
  public <T> String obtainAppIdFromEntity(T entity) {
    String appId = null;

    if (entity instanceof Base) {
      appId = ((Base) entity).getAppId();
    } else if (entity instanceof String) { // Special handling for DefaultVariables
      appId = (String) entity;
    } else if (entity instanceof HarnessTag) {
      appId = GLOBAL_APP_ID;
    } else if (entity instanceof ApplicationAccess) {
      appId = ((ApplicationAccess) entity).getAppId();
    }

    notNullCheck("Application id cannot be null", appId, SRE);
    return appId;
  }

  @Override
  public <T> String obtainAccountIdFromEntity(T entity) {
    String appId = null;

    if (entity instanceof Base) {
      appId = ((Base) entity).getAppId();
    } else if (entity instanceof String) { // Special handling for DefaultVariables
      appId = (String) entity;
    }

    notNullCheck("Application id cannot be null", appId, SRE);

    Application application = appService.get(appId);
    return application.getAccountId();
  }

  @Override
  public List<GitFileChange> getHarnessTagsChangeSet(String accountId) {
    // Tags.yaml is a special case where one yaml is mapped to a list of multiple tags.
    // So any CRUD operation on any tag should result in ChangeType.MODIFY

    String yaml = yamlResourceService.getHarnessTags(accountId).getResource().getYaml();
    return Lists.newArrayList(createGitFileChange(
        accountId, yamlDirectoryService.getRootPath(), YamlConstants.TAGS, yaml, ChangeType.MODIFY, false));
  }
}
