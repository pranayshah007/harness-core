/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.api.CloudProviderType.AWS;
import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;
import static software.wings.ngmigration.NGMigrationEntityType.ELASTIGROUP_CONFIGURATION;
import static software.wings.ngmigration.NGMigrationEntityType.ENVIRONMENT;
import static software.wings.ngmigration.NGMigrationEntityType.TEMPLATE;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.cdng.elastigroup.ElastigroupConfiguration;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.infrastructure.InfrastructureResourceClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponse;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGSkipDetail;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.InfraDefSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.infra.InfraDefMapper;
import io.harness.ngmigration.service.infra.InfraMapperFactory;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.serializer.JsonUtils;

import software.wings.api.CloudProviderType;
import software.wings.api.DeploymentType;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.InfrastructureDefinitionService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class InfraMigrationService extends NgMigrationService {
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private ElastigroupConfigurationMigrationService elastigroupConfigurationMigrationService;
  @Inject InfrastructureResourceClient infrastructureResourceClient;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    InfrastructureConfig infrastructureConfig = (InfrastructureConfig) yamlFile.getYaml();
    String identifier = infrastructureConfig.getInfrastructureDefinitionConfig().getIdentifier();
    String orgIdentifier = infrastructureConfig.getInfrastructureDefinitionConfig().getOrgIdentifier();
    String projectIdentifier = infrastructureConfig.getInfrastructureDefinitionConfig().getOrgIdentifier();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(NGMigrationEntityType.INFRA.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(identifier)
        .scope(Scope.PROJECT)
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(
            basicInfo.getAccountId(), orgIdentifier, projectIdentifier, identifier))
        .build();
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Map<String, Long> deploymentTypeSummary =
        entities.stream()
            .map(entity -> ((InfrastructureDefinition) entity.getEntity()).getDeploymentType())
            .collect(groupingBy(DeploymentType::name, counting()));
    Map<String, Long> cloudProviderType =
        entities.stream()
            .map(entity -> ((InfrastructureDefinition) entity.getEntity()).getCloudProviderType())
            .collect(groupingBy(CloudProviderType::name, counting()));
    return new InfraDefSummary(entities.size(), deploymentTypeSummary, cloudProviderType);
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    InfrastructureDefinition infra = (InfrastructureDefinition) entity;
    String entityId = infra.getUuid();
    CgEntityId infraEntityId = CgEntityId.builder().type(NGMigrationEntityType.INFRA).id(entityId).build();
    CgEntityNode infraNode = CgEntityNode.builder()
                                 .id(entityId)
                                 .appId(infra.getAppId())
                                 .type(NGMigrationEntityType.INFRA)
                                 .entityId(infraEntityId)
                                 .entity(infra)
                                 .build();

    Set<CgEntityId> children = new HashSet<>();
    children.add(CgEntityId.builder().id(infra.getInfrastructure().getCloudProviderId()).type(CONNECTOR).build());

    List<String> connectorIds = InfraMapperFactory.getInfraDefMapper(infra).getConnectorIds(infra);
    if (isNotEmpty(connectorIds)) {
      children.addAll(connectorIds.stream()
                          .filter(StringUtils::isNotBlank)
                          .map(connectorId -> CgEntityId.builder().id(connectorId).type(CONNECTOR).build())
                          .collect(Collectors.toList()));
    }

    if (isNotEmpty(infra.getProvisionerId())) {
      children.add(
          CgEntityId.builder().id(infra.getProvisionerId()).type(NGMigrationEntityType.INFRA_PROVISIONER).build());
    }

    if (infra.getCloudProviderType() == AWS) {
      // AMI
      if (infra.getInfrastructure() instanceof AwsAmiInfrastructure) {
        AwsAmiInfrastructure awsInfra = (AwsAmiInfrastructure) infra.getInfrastructure();
        if (isNotEmpty(awsInfra.getSpotinstCloudProvider())) {
          children.add(CgEntityId.builder().id(infra.getUuid()).type(ELASTIGROUP_CONFIGURATION).build());
        }
      }
      // To Add for Traditional Deployments
    }

    if (infra.getDeploymentType() == DeploymentType.CUSTOM) {
      children.add(CgEntityId.builder().id(infra.getDeploymentTypeTemplateId()).type(TEMPLATE).build());
    }

    return DiscoveryNode.builder().children(children).entityNode(infraNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(infrastructureDefinitionService.get(appId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    if (yamlFile.isExists()) {
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(
              ImportError.builder()
                  .message("Infrastructure was not migrated as it was already imported before")
                  .entity(yamlFile.getCgBasicInfo())
                  .build()))
          .build();
    }
    InfrastructureDefinitionConfig infraConfig =
        ((InfrastructureConfig) yamlFile.getYaml()).getInfrastructureDefinitionConfig();
    InfrastructureRequestDTO infraReqDTO = InfrastructureRequestDTO.builder()
                                               .identifier(infraConfig.getIdentifier())
                                               .type(infraConfig.getType())
                                               .orgIdentifier(infraConfig.getOrgIdentifier())
                                               .projectIdentifier(infraConfig.getProjectIdentifier())
                                               .name(infraConfig.getName())
                                               .tags(infraConfig.getTags())
                                               .type(infraConfig.getType())
                                               .environmentRef(infraConfig.getEnvironmentRef())
                                               .description(infraConfig.getDescription())
                                               .yaml(getYamlString(yamlFile))
                                               .build();
    Response<ResponseDTO<ConnectorResponseDTO>> resp =
        ngClient.createInfrastructure(auth, inputDTO.getAccountIdentifier(), JsonUtils.asTree(infraReqDTO)).execute();
    log.info("Infrastructure creation Response details {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    InfrastructureDefinition infra = (InfrastructureDefinition) entities.get(entityId).getEntity();
    MigratorExpressionUtils.render(entities, migratedEntities, infra, inputDTO.getCustomExpressions());
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, infra.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(inputDTO.getOverrides(), entityId, name);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(Scope.PROJECT, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(Scope.PROJECT, inputDTO);
    InfraDefMapper infraDefMapper = InfraMapperFactory.getInfraDefMapper(infra);
    NGYamlFile envNgYamlFile =
        migratedEntities.get(CgEntityId.builder().id(infra.getEnvId()).type(ENVIRONMENT).build());
    Set<CgEntityId> infraSpecIds = graph.get(entityId)
                                       .stream()
                                       .filter(cgEntityId -> cgEntityId.getType() == ELASTIGROUP_CONFIGURATION)
                                       .collect(Collectors.toSet());
    List<ElastigroupConfiguration> elastigroupConfigurations =
        elastigroupConfigurationMigrationService.getElastigroupConfigurations(
            infraSpecIds, inputDTO, entities, migratedEntities);

    Infrastructure infraSpec =
        infraDefMapper.getSpec(inputDTO, infra, migratedEntities, entities, elastigroupConfigurations);
    if (infraSpec == null) {
      log.error(String.format("We could not migrate the infra %s", infra.getUuid()));
      return YamlGenerationDetails.builder()
          .skipDetails(Collections.singletonList(NGSkipDetail.builder()
                                                     .type(entityId.getType())
                                                     .cgBasicInfo(infra.getCgBasicInfo())
                                                     .reason("Unknown infra type or Failed to migrate the infra")
                                                     .build()))
          .build();
    }
    InfrastructureConfig infrastructureConfig =
        InfrastructureConfig.builder()
            .infrastructureDefinitionConfig(
                InfrastructureDefinitionConfig.builder()
                    .name(name)
                    .orgIdentifier(orgIdentifier)
                    .projectIdentifier(projectIdentifier)
                    .identifier(identifier)
                    .environmentRef(MigratorUtility.getIdentifierWithScope(envNgYamlFile.getNgEntityDetail()))
                    .spec(infraSpec)
                    .type(infraDefMapper.getInfrastructureType(infra))
                    .deploymentType(infraDefMapper.getServiceDefinition(infra))
                    .build())
            .build();

    List<NGYamlFile> files = new ArrayList<>();
    NGYamlFile ngYamlFile =
        NGYamlFile.builder()
            .filename(String.format("infra/%s/%s.yaml",
                ((NGEnvironmentConfig) envNgYamlFile.getYaml()).getNgEnvironmentInfoConfig().getName(), name))
            .yaml(infrastructureConfig)
            .ngEntityDetail(NgEntityDetail.builder()
                                .identifier(identifier)
                                .orgIdentifier(orgIdentifier)
                                .projectIdentifier(projectIdentifier)
                                .build())
            .type(NGMigrationEntityType.INFRA)
            .cgBasicInfo(CgBasicInfo.builder()
                             .accountId(infra.getAccountId())
                             .appId(infra.getAppId())
                             .id(infra.getUuid())
                             .name(infra.getName())
                             .type(NGMigrationEntityType.INFRA)
                             .build())
            .build();
    files.add(ngYamlFile);
    migratedEntities.putIfAbsent(entityId, ngYamlFile);
    return YamlGenerationDetails.builder().yamlFileList(files).build();
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
      InfrastructureDefinition entity = (InfrastructureDefinition) cgEntityNode.getEntity();
      String envId = entity.getEnvId();
      CgEntityId env = CgEntityId.builder().id(envId).type(ENVIRONMENT).build();
      if (!migratedEntities.containsKey(env)) {
        return null;
      }
      String envIdentifier = migratedEntities.get(env).getNgEntityDetail().getIdentifier();
      return getInfra(accountIdentifier, ngEntityDetail, envIdentifier);
    } catch (Exception ex) {
      log.warn("Failed to retrieve the infra. ", ex);
    }
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }

  @Override
  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean migrateAll) {
    return migrateAll || root.getType().equals(NGMigrationEntityType.ENVIRONMENT);
  }

  private YamlDTO getInfra(String accountId, NgEntityDetail ngEntityDetail, String envIdentifier) {
    try {
      InfrastructureResponse response =
          NGRestUtils.getResponse(infrastructureResourceClient.getInfra(ngEntityDetail.getIdentifier(), accountId,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier(), envIdentifier));
      if (response == null || StringUtils.isBlank(response.getInfrastructure().getYaml())) {
        return null;
      }
      return YamlUtils.read(response.getInfrastructure().getYaml(), InfrastructureConfig.class);
    } catch (Exception ex) {
      log.warn("Error when getting infra - ", ex);
      return null;
    }
  }
}
