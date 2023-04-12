/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.cdng.elastigroup.config.yaml.StartupScriptConfiguration;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ngmigration.beans.FileYamlDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.Service;
import software.wings.beans.container.UserDataSpecification;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.ServiceResourceService;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class AmiStartupScriptMigrationService extends NgMigrationService {
  @Inject ServiceResourceService serviceResourceService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    return null;
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    UserDataSpecification userDataSpecification = (UserDataSpecification) entity;
    CgEntityId cgEntityId =
        CgEntityId.builder().id(userDataSpecification.getUuid()).type(NGMigrationEntityType.AMI_STARTUP_SCRIPT).build();
    CgEntityNode cgEntityNode = CgEntityNode.builder()
                                    .entityId(cgEntityId)
                                    .entity(userDataSpecification)
                                    .appId(userDataSpecification.getAppId())
                                    .id(userDataSpecification.getUuid())
                                    .type(NGMigrationEntityType.AMI_STARTUP_SCRIPT)
                                    .build();
    return DiscoveryNode.builder().entityNode(cgEntityNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(serviceResourceService.getUserDataSpecificationById(appId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    return migrateFile(ngClient, inputDTO, yamlFile);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    UserDataSpecification userDataSpecification =
        (UserDataSpecification) migrationContext.getEntities().get(entityId).getEntity();
    NGYamlFile yamlFile = getYamlFile(migrationContext, userDataSpecification);
    if (yamlFile == null) {
      return null;
    }
    return YamlGenerationDetails.builder().yamlFileList(Collections.singletonList(yamlFile)).build();
  }

  private NGYamlFile getYamlFile(MigrationContext migrationContext, UserDataSpecification userDataSpecification) {
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    if (StringUtils.isBlank(userDataSpecification.getData())) {
      return null;
    }
    byte[] fileContent = userDataSpecification.getData().getBytes(StandardCharsets.UTF_8);
    CgEntityNode serviceNode = entities.get(
        CgEntityId.builder().type(NGMigrationEntityType.SERVICE).id(userDataSpecification.getServiceId()).build());
    Service service = (Service) serviceNode.getEntity();
    return getYamlFile(inputDTO, userDataSpecification, fileContent, service.getName());
  }

  private static NGYamlFile getYamlFile(
      MigrationInputDTO inputDTO, UserDataSpecification userDataSpecification, byte[] content, String serviceName) {
    if (isEmpty(content)) {
      return null;
    }
    String prefix = serviceName + ' ';
    String fileUsage = FileUsage.CONFIG.name();
    String projectIdentifier = MigratorUtility.getProjectIdentifier(Scope.PROJECT, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(Scope.PROJECT, inputDTO);
    String identifier =
        MigratorUtility.generateManifestIdentifier(prefix + "StartupScriptSpec", inputDTO.getIdentifierCaseFormat());
    String name = identifier + ".sh";
    return NGYamlFile.builder()
        .type(NGMigrationEntityType.AMI_STARTUP_SCRIPT)
        .filename(null)
        .yaml(FileYamlDTO.builder()
                  .identifier(identifier)
                  .fileUsage(fileUsage)
                  .name(name)
                  .content(new String(content))
                  .rootIdentifier("Root")
                  .depth(Integer.MAX_VALUE)
                  .filePath("")
                  .orgIdentifier(orgIdentifier)
                  .projectIdentifier(projectIdentifier)
                  .build())
        .ngEntityDetail(NgEntityDetail.builder()
                            .entityType(NGMigrationEntityType.FILE_STORE)
                            .identifier(identifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build())
        .cgBasicInfo(userDataSpecification.getCgBasicInfo())
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

  public List<StartupScriptConfiguration> getStartupScriptConfiguration(
      MigrationContext migrationContext, Set<CgEntityId> serviceSpecIds) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    if (isEmpty(serviceSpecIds)) {
      return new ArrayList<>();
    }
    List<StartupScriptConfiguration> scriptConfigurations = new ArrayList<>();
    for (CgEntityId configEntityId : serviceSpecIds) {
      CgEntityNode configNode = entities.get(configEntityId);
      if (configNode != null) {
        UserDataSpecification specification = (UserDataSpecification) configNode.getEntity();
        NGYamlFile file = getYamlFile(migrationContext, specification);
        if (file != null) {
          scriptConfigurations.add(getConfigFileWrapper(file));
        }
      }
    }
    return scriptConfigurations;
  }

  @Override
  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean migrateAll) {
    return migrateAll || root.getType() == NGMigrationEntityType.SERVICE;
  }

  private static StartupScriptConfiguration getConfigFileWrapper(NGYamlFile file) {
    ParameterField<List<String>> files;
    files = MigratorUtility.getFileStorePaths(Collections.singletonList(file));
    return StartupScriptConfiguration.builder()
        .store(StoreConfigWrapper.builder()
                   .type(StoreConfigType.HARNESS)
                   .spec(HarnessStore.builder().files(files).build())
                   .build())
        .build();
  }
}
