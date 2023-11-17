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
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
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
import io.harness.ngmigration.service.config.ElastigroupConfigurationFileHandlerImpl;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngmigration.utils.SecretRefUtils;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.Environment;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.InfrastructureDefinitionService;

import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ElastigroupConfigurationMigrationService extends NgMigrationService {
  @Inject InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private SecretRefUtils secretRefUtils;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    return null;
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    InfrastructureDefinition infrastructureDefinition = (InfrastructureDefinition) entity;
    CgEntityId cgEntityId = CgEntityId.builder()
                                .id(infrastructureDefinition.getUuid())
                                .type(NGMigrationEntityType.ELASTIGROUP_CONFIGURATION)
                                .build();
    CgEntityNode cgEntityNode = CgEntityNode.builder()
                                    .entityId(cgEntityId)
                                    .entity(infrastructureDefinition)
                                    .appId(infrastructureDefinition.getAppId())
                                    .id(infrastructureDefinition.getUuid())
                                    .type(NGMigrationEntityType.ELASTIGROUP_CONFIGURATION)
                                    .build();
    Set<CgEntityId> children = new HashSet<>();
    children.addAll(secretRefUtils.getSecretRefFromExpressions(
        infrastructureDefinition.getAccountId(), MigratorExpressionUtils.getExpressions(infrastructureDefinition)));
    return DiscoveryNode.builder().entityNode(cgEntityNode).children(children).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(infrastructureDefinitionService.get(appId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    return migrateFile(ngClient, inputDTO, yamlFile);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    InfrastructureDefinition infrastructureDefinition = (InfrastructureDefinition) entities.get(entityId).getEntity();
    Map<String, Object> custom =
        MigratorUtility.updateContextVariables(migrationContext, entities, infrastructureDefinition);
    MigratorExpressionUtils.render(migrationContext, infrastructureDefinition, custom);
    NGYamlFile yamlFile = getYamlFile(infrastructureDefinition, inputDTO, migrationContext);
    if (yamlFile == null) {
      return null;
    }
    return YamlGenerationDetails.builder().yamlFileList(Collections.singletonList(yamlFile)).build();
  }

  private NGYamlFile getYamlFile(
      InfrastructureDefinition infrastructureDefinition, MigrationInputDTO inputDTO, MigrationContext context) {
    AwsAmiInfrastructure infrastructure = (AwsAmiInfrastructure) infrastructureDefinition.getInfrastructure();
    if (StringUtils.isBlank(infrastructure.getSpotinstElastiGroupJson())) {
      return null;
    }
    byte[] fileContent = infrastructure.getSpotinstElastiGroupJson().getBytes(StandardCharsets.UTF_8);
    return getYamlFile(inputDTO, infrastructureDefinition, fileContent, infrastructureDefinition.getName(), context);
  }

  private static NGYamlFile getYamlFile(MigrationInputDTO inputDTO, InfrastructureDefinition infrastructureDefinition,
      byte[] content, String infraName, MigrationContext context) {
    if (isEmpty(content)) {
      return null;
    }

    CgEntityNode environmentNode = context.getEntities().get(
        CgEntityId.builder().type(NGMigrationEntityType.ENVIRONMENT).id(infrastructureDefinition.getEnvId()).build());

    String envName = "";
    if (environmentNode != null && environmentNode.getEntity() != null) {
      Environment environment = (Environment) environmentNode.getEntity();
      envName = environment.getName();
    }

    ElastigroupConfigurationFileHandlerImpl handler =
        new ElastigroupConfigurationFileHandlerImpl(null, envName, content);

    return NGYamlFile.builder()
        .type(NGMigrationEntityType.ELASTIGROUP_CONFIGURATION)
        .filename(null)
        .yaml(handler.getFileYamlDTO(context, infrastructureDefinition))
        .ngEntityDetail(handler.getNGEntityDetail(context, infrastructureDefinition))
        .cgBasicInfo(handler.getCgBasicInfo(infrastructureDefinition))
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

  public List<ElastigroupConfiguration> getElastigroupConfigurations(
      MigrationContext migrationContext, Set<CgEntityId> infraSpecIds) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    if (isEmpty(infraSpecIds)) {
      return new ArrayList<>();
    }
    List<ElastigroupConfiguration> elastigroupConfigurations = new ArrayList<>();
    for (CgEntityId configEntityId : infraSpecIds) {
      CgEntityNode configNode = entities.get(configEntityId);
      if (configNode != null) {
        InfrastructureDefinition infrastructureDefinition = (InfrastructureDefinition) configNode.getEntity();
        MigratorExpressionUtils.render(migrationContext, infrastructureDefinition, inputDTO.getCustomExpressions());
        NGYamlFile file = getYamlFile(infrastructureDefinition, inputDTO, migrationContext);
        if (file != null) {
          elastigroupConfigurations.add(getConfigFileWrapper(file));
        }
      }
    }
    return elastigroupConfigurations;
  }

  @Override
  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean migrateAll) {
    return migrateAll || root.getType() == NGMigrationEntityType.INFRA;
  }

  private static ElastigroupConfiguration getConfigFileWrapper(NGYamlFile file) {
    ParameterField<List<String>> files;
    files = MigratorUtility.getFileStorePaths(Collections.singletonList(file));
    return ElastigroupConfiguration.builder()
        .store(StoreConfigWrapper.builder()
                   .type(StoreConfigType.HARNESS)
                   .spec(HarnessStore.builder().files(files).build())
                   .build())
        .build();
  }
}
