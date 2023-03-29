/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.config.ConfigFileHandlerImpl;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngmigration.utils.SecretRefUtils;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.ConfigFile;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.ConfigService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ConfigFileMigrationService extends NgMigrationService {
  @Inject private SecretRefUtils secretRefUtils;
  @Inject ConfigService configService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    return null;
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    ConfigFile configFile = (ConfigFile) entity;
    CgEntityId cgEntityId =
        CgEntityId.builder().id(configFile.getUuid()).type(NGMigrationEntityType.CONFIG_FILE).build();
    CgEntityNode cgEntityNode = CgEntityNode.builder()
                                    .entityId(cgEntityId)
                                    .entity(configFile)
                                    .appId(configFile.getAppId())
                                    .id(configFile.getUuid())
                                    .type(NGMigrationEntityType.CONFIG_FILE)
                                    .build();
    Set<CgEntityId> children = new HashSet<>();
    if (configFile.isEncrypted() && StringUtils.isNotBlank(configFile.getEncryptedFileId())) {
      children.add(CgEntityId.builder().id(configFile.getEncryptedFileId()).type(NGMigrationEntityType.SECRET).build());
    }
    byte[] fileContent = getFileContent(configFile);
    if (EmptyPredicate.isNotEmpty(fileContent)) {
      children.addAll(secretRefUtils.getSecretRefFromExpressions(
          configFile.getAccountId(), MigratorExpressionUtils.extractAll(new String(fileContent))));
    }
    return DiscoveryNode.builder().children(children).entityNode(cgEntityNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(configService.get(appId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    return migrateFile(auth, ngClient, inputDTO, yamlFile);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    ConfigFile configFile = (ConfigFile) entities.get(entityId).getEntity();
    if (configFile.isEncrypted()) {
      return null;
    }
    NGYamlFile yamlFile = getYamlFileForConfigFile(migrationContext, configFile, entities);
    if (yamlFile == null) {
      return null;
    }
    return YamlGenerationDetails.builder().yamlFileList(Collections.singletonList(yamlFile)).build();
  }

  private byte[] getFileContent(ConfigFile configFile) {
    try {
      byte[] fileContent = configService.getFileContent(configFile.getAppId(), configFile);
      if (isNotEmpty(fileContent)) {
        return fileContent;
      }
    } catch (Exception e) {
      log.error(String.format("There was an error with reading contents of config file %s", configFile.getUuid()), e);
    }
    return null;
  }

  private NGYamlFile getYamlFileForConfigFile(
      MigrationContext context, ConfigFile configFile, Map<CgEntityId, CgEntityNode> entities) {
    byte[] fileContent = getFileContent(configFile);
    if (EmptyPredicate.isEmpty(fileContent)) {
      return null;
    }
    CgEntityNode serviceNode = null;
    if (EntityType.SERVICE == configFile.getEntityType()) {
      serviceNode =
          entities.get(CgEntityId.builder().type(NGMigrationEntityType.SERVICE).id(configFile.getEntityId()).build());
    }

    CgEntityNode environmentNode = null;
    if (EntityType.ENVIRONMENT == configFile.getEntityType()) {
      environmentNode = entities.get(
          CgEntityId.builder().type(NGMigrationEntityType.ENVIRONMENT).id(configFile.getEntityId()).build());
    }

    String serviceName = "";
    String envName = "";
    if (serviceNode != null && serviceNode.getEntity() != null) {
      Service service = (Service) serviceNode.getEntity();
      serviceName = service.getName();
    }
    if (environmentNode != null && environmentNode.getEntity() != null) {
      Environment environment = (Environment) environmentNode.getEntity();
      envName = environment.getName();
    }
    return getYamlFile(context, configFile, fileContent, envName, serviceName);
  }

  private NGYamlFile getYamlFile(
      MigrationContext migrationContext, ConfigFile configFile, byte[] content, String envName, String serviceName) {
    if (isEmpty(content)) {
      return null;
    }
    ConfigFileHandlerImpl configFileHandler = new ConfigFileHandlerImpl(serviceName, envName, content);
    return NGYamlFile.builder()
        .type(NGMigrationEntityType.CONFIG_FILE)
        .filename(null)
        .yaml(configFileHandler.getFileYamlDTO(migrationContext, configFile))
        .ngEntityDetail(configFileHandler.getNGEntityDetail(migrationContext, configFile))
        .cgBasicInfo(configFileHandler.getCgBasicInfo(configFile))
        .build();
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }

  public List<ConfigFileWrapper> getConfigFiles(MigrationContext migrationContext, Set<CgEntityId> configFileIds) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    if (isEmpty(configFileIds)) {
      return new ArrayList<>();
    }
    List<ConfigFileWrapper> configWrappers = new ArrayList<>();
    for (CgEntityId configEntityId : configFileIds) {
      CgEntityNode configNode = entities.get(configEntityId);
      if (configNode != null) {
        ConfigFile configFile = (ConfigFile) configNode.getEntity();
        NGYamlFile file = getYamlFileForConfigFile(migrationContext, configFile, entities);
        ConfigFileWrapper wrapper = getConfigFileWrapper(migrationContext, configFile, file);
        if (wrapper != null) {
          configWrappers.add(wrapper);
        }
      }
    }
    return configWrappers;
  }

  @Override
  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean migrateAll) {
    return migrateAll || root.getType() == NGMigrationEntityType.SERVICE;
  }

  private ConfigFileWrapper getConfigFileWrapper(
      MigrationContext migrationContext, ConfigFile configFile, NGYamlFile file) {
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    ParameterField<List<String>> files = ParameterField.createValueField(Collections.emptyList());
    List<String> secretFiles = new ArrayList<>();
    if (StringUtils.isBlank(configFile.getEncryptedFileId()) && file == null) {
      return null;
    }
    if (configFile.isEncrypted()) {
      SecretRefData secretRefData = MigratorUtility.getSecretRef(migratedEntities, configFile.getEncryptedFileId());
      secretFiles = Collections.singletonList(secretRefData.toSecretRefStringValue());
    } else {
      files = MigratorUtility.getFileStorePaths(Collections.singletonList(file));
    }
    return ConfigFileWrapper.builder()
        .configFile(io.harness.cdng.configfile.ConfigFile.builder()
                        .identifier(MigratorUtility.generateIdentifier(
                            configFile.getRelativeFilePath(), migrationContext.getInputDTO().getIdentifierCaseFormat()))
                        .spec(ConfigFileAttributes.builder()
                                  .store(ParameterField.createValueField(
                                      StoreConfigWrapper.builder()
                                          .type(StoreConfigType.HARNESS)
                                          .spec(HarnessStore.builder()
                                                    .files(files)
                                                    .secretFiles(ParameterField.createValueField(secretFiles))
                                                    .build())
                                          .build()))
                                  .build())
                        .build())
        .build();
  }
}
