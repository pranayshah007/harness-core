/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.configfile;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;

import static java.util.stream.Collectors.toList;

import io.harness.beans.EncryptedData;
import io.harness.delegate.beans.ChecksumType;
import io.harness.exception.InvalidRequestException;
import io.harness.serializer.JsonUtils;
import io.harness.stream.BoundedInputStream;

import software.wings.beans.Application;
import software.wings.beans.ConfigFile;
import software.wings.beans.ConfigFile.Yaml;
import software.wings.beans.EntityType;
import software.wings.beans.EntityVersion;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariable;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.utils.Utils;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 12/08/17
 */
@Singleton
@Slf4j
public class ConfigFileYamlHandler extends BaseYamlHandler<Yaml, ConfigFile> {
  @Inject private EnvironmentService environmentService;
  @Inject private ConfigService configService;
  @Inject private YamlHelper yamlHelper;
  @Inject private SecretManager secretManager;

  @Override
  public void delete(ChangeContext<Yaml> changeContext) {
    String accountId = changeContext.getChange().getAccountId();
    String yamlFilePath = changeContext.getChange().getFilePath();
    Optional<Application> optionalApplication = yamlHelper.getApplicationIfPresent(accountId, yamlFilePath);
    if (!optionalApplication.isPresent()) {
      return;
    }

    Application application = optionalApplication.get();
    Optional<Service> serviceOptional = yamlHelper.getServiceIfPresent(application.getUuid(), yamlFilePath);
    if (!serviceOptional.isPresent()) {
      return;
    }

    Yaml yaml = changeContext.getYaml();
    String targetFilePath = yaml.getTargetFilePath();
    configService.delete(optionalApplication.get().getUuid(), serviceOptional.get().getUuid(), EntityType.SERVICE,
        targetFilePath, changeContext.getChange().isSyncFromGit());
  }

  @Override
  public Yaml toYaml(ConfigFile bean, String appId) {
    // target environments
    Map<String, EntityVersion> envIdVersionMap = bean.getEnvIdVersionMap();
    List<String> envNameList = Lists.newArrayList();
    if (envIdVersionMap != null) {
      // Find all the envs that are configured to use the default version. If the env is configured to use default
      // version, the value is null.
      List<String> envIdList = envIdVersionMap.entrySet()
                                   .stream()
                                   .filter(entry -> entry.getValue() == null)
                                   .map(Map.Entry::getKey)
                                   .collect(toList());
      if (isNotEmpty(envIdList)) {
        envIdList.forEach(envId -> {
          Environment environment = environmentService.get(appId, envId, false);
          if (environment != null) {
            envNameList.add(environment.getName());
          }
        });
      }
    }

    String fileName;
    if (bean.isEncrypted()) {
      String encryptedFieldRefId = bean.getEncryptedFileId();
      fileName = secretManager.getEncryptedYamlRef(bean.getAccountId(), encryptedFieldRefId);
    } else {
      fileName = Utils.normalize(bean.getRelativeFilePath());
    }

    return ConfigFile.Yaml.builder()
        .description(bean.getDescription())
        .encrypted(bean.isEncrypted())
        .targetEnvs(envNameList)
        .targetToAllEnv(bean.isTargetToAllEnv())
        .targetFilePath(bean.getRelativeFilePath())
        .fileName(fileName)
        .checksum(bean.getChecksum())
        .checksumType(Utils.getStringFromEnum(bean.getChecksumType()))
        .harnessApiVersion(getHarnessApiVersion())
        .build();
  }

  @Override
  public ConfigFile upsertFromYaml(ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) {
    String accountId = changeContext.getChange().getAccountId();
    String yamlFilePath = changeContext.getChange().getFilePath();
    String appId = yamlHelper.getAppId(accountId, yamlFilePath);
    notNullCheck("Invalid Application for the yaml file:" + yamlFilePath, appId, USER);
    String serviceId = yamlHelper.getServiceId(appId, yamlFilePath);
    notNullCheck("Invalid Service for the yaml file:" + yamlFilePath, serviceId, USER);
    String configFileName = yamlHelper.getNameFromYamlFilePath(yamlFilePath);

    Yaml yaml = changeContext.getYaml();
    List<String> envNameList = yaml.getTargetEnvs();
    Map<String, EntityVersion> envIdMap = new HashMap<>();
    if (isNotEmpty(envNameList)) {
      envNameList.forEach(envName -> {
        Environment environment = environmentService.getEnvironmentByName(appId, envName);
        if (environment != null) {
          envIdMap.put(environment.getUuid(), null);
        }
      });
    }

    BoundedInputStream inputStream = null;
    ConfigFile previous = get(accountId, yamlFilePath, changeContext);
    if (!yaml.isEncrypted()) {
      int index = yamlFilePath.lastIndexOf(PATH_DELIMITER);
      if (index != -1) {
        String configFileDirPath = yamlFilePath.substring(0, index);
        String configFilePath = configFileDirPath + PATH_DELIMITER + yaml.getFileName();

        Optional<ChangeContext> contentChangeContext = changeSetContext.stream()
                                                           .filter(changeContext1 -> {
                                                             String filePath = changeContext1.getChange().getFilePath();
                                                             return filePath.equals(configFilePath);
                                                           })
                                                           .findFirst();

        if (contentChangeContext.isPresent()) {
          ChangeContext fileContext = contentChangeContext.get();
          String fileContent = fileContext.getChange().getFileContent();
          inputStream = new BoundedInputStream(new ByteArrayInputStream(fileContent.getBytes(Charsets.UTF_8)));
        } else {
          if (previous == null || previous.getFileUuid() == null) {
            log.error("Could not locate file: " + yaml.getFileName());
            throw new InvalidRequestException("Could not locate file: " + yaml.getFileName());
          }
        }
      }
    }

    ConfigFile configFile = new ConfigFile();
    configFile.setAccountId(accountId);
    configFile.setAppId(appId);
    configFile.setName(configFileName);
    configFile.setFileName(configFileName);
    if (yaml.isEncrypted()) {
      EncryptedData encryptedDataFromYamlRef = secretManager.getEncryptedDataFromYamlRef(yaml.getFileName(), accountId);
      configFile.setEncryptedFileId(encryptedDataFromYamlRef.getUuid());
    } else {
      configFile.setEncryptedFileId("");
      ChecksumType checksumType = Utils.getEnumFromString(ChecksumType.class, yaml.getChecksumType());
      configFile.setFileName(yaml.getFileName());
      configFile.setChecksum(yaml.getChecksum());
      configFile.setChecksumType(checksumType);
    }

    configFile.setSetAsDefault(true);
    configFile.setDescription(yaml.getDescription());
    configFile.setEnvIdVersionMap(envIdMap);
    configFile.setEnvIdVersionMapString(getEnvIdVersionMapString(envIdMap));
    configFile.setEncrypted(yaml.isEncrypted());
    configFile.setEntityType(EntityType.SERVICE);
    configFile.setEntityId(serviceId);
    configFile.setRelativeFilePath(yaml.getTargetFilePath());

    configFile.setTemplateId(ServiceVariable.DEFAULT_TEMPLATE_ID);
    configFile.setTargetToAllEnv(yaml.isTargetToAllEnv());
    configFile.setSyncFromGit(changeContext.getChange().isSyncFromGit());

    if (previous != null) {
      configFile.setUuid(previous.getUuid());
      configService.update(configFile, inputStream);
    } else {
      configService.save(configFile, inputStream);
    }

    return get(accountId, yamlFilePath);
  }

  private String getEnvIdVersionMapString(Map<String, EntityVersion> entityVersionMap) {
    return JsonUtils.asJson(entityVersionMap);
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }

  @Override
  public ConfigFile get(String accountId, String yamlFilePath, ChangeContext<Yaml> changeContext) {
    Yaml yaml = changeContext.getYaml();
    String relativeFilePath = yaml.getTargetFilePath();
    return getConfigFile(accountId, yamlFilePath, relativeFilePath);
  }

  @Override
  public ConfigFile get(String accountId, String yamlFilePath) {
    String relativeFilePath = yamlHelper.getNameFromYamlFilePath(yamlFilePath);
    return getConfigFile(accountId, yamlFilePath, relativeFilePath);
  }

  private ConfigFile getConfigFile(String accountId, String yamlFilePath, String relativeFilePath) {
    String appId = yamlHelper.getAppId(accountId, yamlFilePath);
    notNullCheck("Invalid Application for the yaml file:" + yamlFilePath, appId, USER);
    String serviceId = yamlHelper.getServiceId(appId, yamlFilePath);
    notNullCheck("Invalid Service for the yaml file:" + yamlFilePath, serviceId, USER);
    return configService.get(appId, serviceId, EntityType.SERVICE, relativeFilePath);
  }
}
