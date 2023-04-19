/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static software.wings.ngmigration.NGMigrationEntityType.CONFIG_FILE;
import static software.wings.ngmigration.NGMigrationEntityType.ENVIRONMENT;
import static software.wings.ngmigration.NGMigrationEntityType.MANIFEST;
import static software.wings.ngmigration.NGMigrationEntityType.SERVICE;
import static software.wings.ngmigration.NGMigrationEntityType.SERVICE_VARIABLE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.core.variables.NGVariable;

import software.wings.beans.EntityType;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceVariableService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class ServiceVariableMigrationService extends NgMigrationService {
  @Inject private ServiceVariableService serviceVariableService;
  @Inject private ServiceTemplateService serviceTemplateService;

  private static Set<NGMigrationEntityType> overrideTypes = Sets.newHashSet(SERVICE_VARIABLE, MANIFEST, CONFIG_FILE);

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    return null;
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    return null;
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    ServiceVariable serviceVariable = (ServiceVariable) entity;
    // We will only handle for service variables overrides that have both evironment & services.
    if (!EntityType.SERVICE_TEMPLATE.equals(serviceVariable.getEntityType())) {
      return null;
    }
    String entityId = serviceVariable.getUuid();
    CgEntityId variableEntityId = CgEntityId.builder().type(SERVICE_VARIABLE).id(entityId).build();
    CgEntityNode variableNode = CgEntityNode.builder()
                                    .id(entityId)
                                    .type(SERVICE_VARIABLE)
                                    .entityId(variableEntityId)
                                    .entity(serviceVariable)
                                    .build();
    Set<CgEntityId> children = new HashSet<>();
    if (StringUtils.isNotBlank(serviceVariable.getEncryptedValue())) {
      children.add(
          CgEntityId.builder().id(serviceVariable.getEncryptedValue()).type(NGMigrationEntityType.SECRET).build());
    }
    ServiceTemplate serviceTemplate =
        serviceTemplateService.get(serviceVariable.getAppId(), serviceVariable.getTemplateId());
    String serviceId = serviceTemplate.getServiceId();
    serviceVariable.setServiceId(serviceId);
    children.add(CgEntityId.builder().type(SERVICE).id(serviceId).build());
    children.add(CgEntityId.builder().type(ENVIRONMENT).id(serviceVariable.getEnvId()).build());
    return DiscoveryNode.builder().children(children).entityNode(variableNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(serviceVariableService.get(appId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(NGClient ngClient, PmsClient pmsClient, TemplateClient templateClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    NGServiceOverrideInfoConfig serviceOverrideInfoConfig =
        ((NGServiceOverrideConfig) yamlFile.getYaml()).getServiceOverrideInfoConfig();
    String yaml = getYamlString(yamlFile);
    ServiceOverrideRequestDTO requestDTO = ServiceOverrideRequestDTO.builder()
                                               .serviceIdentifier(serviceOverrideInfoConfig.getServiceRef())
                                               .environmentIdentifier(serviceOverrideInfoConfig.getEnvironmentRef())
                                               .orgIdentifier(yamlFile.getNgEntityDetail().getOrgIdentifier())
                                               .projectIdentifier(yamlFile.getNgEntityDetail().getProjectIdentifier())
                                               .yaml(yaml)
                                               .build();

    Response<ResponseDTO<ServiceOverrideResponseDTO>> resp =
        ngClient
            .upsertServiceOverride(inputDTO.getDestinationAuthToken(), inputDTO.getDestinationAccountIdentifier(),
                JsonUtils.asTree(requestDTO))
            .execute();
    log.info("Service variables creation Response details {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  public boolean doReferenceExists(Map<CgEntityId, NGYamlFile> migratedEntities, String envId, String serviceId) {
    return migratedEntities.containsKey(CgEntityId.builder().type(ENVIRONMENT).id(envId).build())
        && migratedEntities.containsKey(CgEntityId.builder().type(SERVICE).id(serviceId).build());
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    ServiceVariable serviceVariable = (ServiceVariable) entities.get(entityId).getEntity();
    MigratorExpressionUtils.render(
        migrationContext, serviceVariable, migrationContext.getInputDTO().getCustomExpressions());
    List<NGYamlFile> files = new ArrayList<>();

    if (!doReferenceExists(migratedEntities, serviceVariable.getEnvId(), serviceVariable.getServiceId())) {
      return YamlGenerationDetails.builder().yamlFileList(files).build();
    }

    NGYamlFile yamlFile = getBlankServiceOverride(
        migrationContext, serviceVariable.getEnvId(), serviceVariable.getServiceId(), serviceVariable.getCgBasicInfo());
    boolean reused = false;
    // Check if we already have some migrated entity for service/environment combo
    NGYamlFile existingOverride =
        findExistingOverride(migrationContext, serviceVariable.getEnvId(), serviceVariable.getServiceId());
    if (existingOverride != null) {
      yamlFile = existingOverride;
      reused = true;
    }
    NGVariable ngVariable = MigratorUtility.getNGVariable(migrationContext, serviceVariable);
    NGServiceOverrideInfoConfig serviceOverrideInfoConfig =
        ((NGServiceOverrideConfig) yamlFile.getYaml()).getServiceOverrideInfoConfig();
    if (ngVariable != null) {
      serviceOverrideInfoConfig.getVariables().add(ngVariable);
    }
    if (!reused) {
      files.add(yamlFile);
      migratedEntities.putIfAbsent(entityId, yamlFile);
    }
    return YamlGenerationDetails.builder().yamlFileList(files).build();
  }

  public static NGYamlFile findExistingOverride(MigrationContext migrationContext, String envId, String serviceId) {
    Map<CgEntityId, CgEntityNode> entities = migrationContext.getEntities();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    if (EmptyPredicate.isNotEmpty(migratedEntities)
        && migratedEntities.keySet().stream().anyMatch(migrated -> overrideTypes.contains(migrated.getType()))) {
      List<CgEntityId> alreadyMigratedOverrides = migratedEntities.keySet()
                                                      .stream()
                                                      .filter(migrated -> overrideTypes.contains(migrated.getType()))
                                                      .collect(Collectors.toList());
      for (CgEntityId cgEntityId : alreadyMigratedOverrides) {
        String migratedServiceId = "";
        String migratedEnvId = "";
        if (cgEntityId.getType() == SERVICE_VARIABLE) {
          ServiceVariable variable = (ServiceVariable) entities.get(cgEntityId).getEntity();
          migratedServiceId = variable.getServiceId();
          migratedEnvId = variable.getEnvId();
        }
        if (cgEntityId.getType() == MANIFEST) {
          ApplicationManifest manifest = (ApplicationManifest) entities.get(cgEntityId).getEntity();
          migratedServiceId = manifest.getServiceId();
          migratedEnvId = manifest.getEnvId();
        }
        if (cgEntityId.getType() == CONFIG_FILE) {
          migratedServiceId = ConfigFileMigrationService.getServiceId(migrationContext, cgEntityId);
          migratedEnvId = ConfigFileMigrationService.getEnvId(migrationContext, cgEntityId);
        }
        // If we already migrated variable/manifest which had the same env & service. We then just merge them
        if (Objects.equals(migratedEnvId, envId) && Objects.equals(migratedServiceId, serviceId)) {
          return migratedEntities.get(cgEntityId);
        }
      }
    }
    return null;
  }

  public static NGYamlFile getBlankServiceOverride(
      MigrationContext migrationContext, String envId, String serviceId, CgBasicInfo cgBasicInfo) {
    MigrationInputDTO inputDTO = migrationContext.getInputDTO();
    Map<CgEntityId, NGYamlFile> migratedEntities = migrationContext.getMigratedEntities();
    String projectIdentifier = MigratorUtility.getProjectIdentifier(Scope.PROJECT, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(Scope.PROJECT, inputDTO);
    NgEntityDetail ngEntityDetail = NgEntityDetail.builder()
                                        .entityType(SERVICE_VARIABLE)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .build();
    String environmentRef = MigratorUtility.getIdentifierWithScope(migratedEntities, envId, ENVIRONMENT);
    String serviceRef = MigratorUtility.getIdentifierWithScope(migratedEntities, serviceId, SERVICE);
    NGServiceOverrideConfig serviceOverride = NGServiceOverrideConfig.builder()
                                                  .serviceOverrideInfoConfig(NGServiceOverrideInfoConfig.builder()
                                                                                 .environmentRef(environmentRef)
                                                                                 .serviceRef(serviceRef)
                                                                                 .variables(new ArrayList<>())
                                                                                 .manifests(new ArrayList<>())
                                                                                 .configFiles(new ArrayList<>())
                                                                                 .build())
                                                  .build();
    return NGYamlFile.builder()
        .type(SERVICE_VARIABLE)
        .filename("service-variables/" + environmentRef + ".yaml")
        .yaml(serviceOverride)
        .ngEntityDetail(ngEntityDetail)
        .cgBasicInfo(cgBasicInfo)
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
}
