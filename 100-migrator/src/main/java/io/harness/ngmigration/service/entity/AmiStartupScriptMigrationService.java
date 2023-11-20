/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.MigratedEntityMapping;
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
import io.harness.ngmigration.service.config.AsgStartupScriptFileHandlerImpl;

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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
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
    MigratorExpressionUtils.render(migrationContext, userDataSpecification, inputDTO.getCustomExpressions());
    byte[] fileContent = userDataSpecification.getData().getBytes(StandardCharsets.UTF_8);
    CgEntityNode serviceNode = entities.get(
        CgEntityId.builder().type(NGMigrationEntityType.SERVICE).id(userDataSpecification.getServiceId()).build());
    Service service = (Service) serviceNode.getEntity();
    return getYamlFile(userDataSpecification, fileContent, service.getName(), migrationContext);
  }

  private static NGYamlFile getYamlFile(UserDataSpecification userDataSpecification, byte[] content, String serviceName,
      MigrationContext migrationContext) {
    if (isEmpty(content)) {
      return null;
    }
    AsgStartupScriptFileHandlerImpl fileHandler = new AsgStartupScriptFileHandlerImpl(serviceName, null, content);

    return NGYamlFile.builder()
        .type(NGMigrationEntityType.AMI_STARTUP_SCRIPT)
        .filename(null)
        .yaml(fileHandler.getFileYamlDTO(migrationContext, userDataSpecification))
        .ngEntityDetail(fileHandler.getNGEntityDetail(migrationContext, userDataSpecification))
        .cgBasicInfo(fileHandler.getCgBasicInfo(userDataSpecification))
        .build();
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists(MigrationContext migrationContext) {
    return true;
  }

  public List<NGYamlFile> getStartupScriptConfiguration(
      MigrationContext migrationContext, Set<CgEntityId> serviceSpecIds) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    if (isEmpty(serviceSpecIds)) {
      return new ArrayList<>();
    }
    List<NGYamlFile> scriptConfigurationFiles = new ArrayList<>();
    for (CgEntityId configEntityId : serviceSpecIds) {
      CgEntityNode configNode = entities.get(configEntityId);
      if (configNode != null) {
        UserDataSpecification specification = (UserDataSpecification) configNode.getEntity();
        NGYamlFile file = getYamlFile(migrationContext, specification);
        if (file != null) {
          scriptConfigurationFiles.add(file);
        }
      }
    }
    return scriptConfigurationFiles;
  }

  @Override
  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean migrateAll) {
    return migrateAll || root.getType() == NGMigrationEntityType.SERVICE;
  }
}
