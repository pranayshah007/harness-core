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
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.kinds.EcsServiceDefinitionManifest;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.filestore.FileUsage;
import io.harness.ngmigration.beans.FileYamlDTO;
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
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.Service;
import software.wings.beans.container.EcsServiceSpecification;
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
public class EcsServiceSpecMigrationService extends NgMigrationService {
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
    EcsServiceSpecification serviceSpecification = (EcsServiceSpecification) entity;
    CgEntityId cgEntityId =
        CgEntityId.builder().id(serviceSpecification.getUuid()).type(NGMigrationEntityType.ECS_SERVICE_SPEC).build();
    CgEntityNode cgEntityNode = CgEntityNode.builder()
                                    .entityId(cgEntityId)
                                    .entity(serviceSpecification)
                                    .appId(serviceSpecification.getAppId())
                                    .id(serviceSpecification.getUuid())
                                    .type(NGMigrationEntityType.ECS_SERVICE_SPEC)
                                    .build();
    return DiscoveryNode.builder().entityNode(cgEntityNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(serviceResourceService.getEcsServiceSpecificationById(appId, entityId));
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    return migrateFile(auth, ngClient, inputDTO, yamlFile);
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    EcsServiceSpecification serviceSpecification = (EcsServiceSpecification) entities.get(entityId).getEntity();
    NGYamlFile yamlFile = getYamlFile(serviceSpecification, inputDTO, entities, migratedEntities);
    if (yamlFile == null) {
      return null;
    }
    return YamlGenerationDetails.builder().yamlFileList(Collections.singletonList(yamlFile)).build();
  }

  private NGYamlFile getYamlFile(EcsServiceSpecification serviceSpecification, MigrationInputDTO inputDTO,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    if (StringUtils.isBlank(serviceSpecification.getServiceSpecJson())) {
      return null;
    }
    MigratorExpressionUtils.render(entities, migratedEntities, serviceSpecification, inputDTO.getCustomExpressions(),
        inputDTO.getIdentifierCaseFormat());
    byte[] fileContent = serviceSpecification.getServiceSpecJson().getBytes(StandardCharsets.UTF_8);
    CgEntityNode serviceNode = entities.get(
        CgEntityId.builder().type(NGMigrationEntityType.SERVICE).id(serviceSpecification.getServiceId()).build());
    Service service = (Service) serviceNode.getEntity();
    return getYamlFile(inputDTO, serviceSpecification, fileContent, service.getName());
  }

  private static NGYamlFile getYamlFile(
      MigrationInputDTO inputDTO, EcsServiceSpecification serviceSpecification, byte[] content, String serviceName) {
    if (isEmpty(content)) {
      return null;
    }
    String prefix = serviceName + ' ';
    String fileUsage = FileUsage.MANIFEST_FILE.name();
    String projectIdentifier = MigratorUtility.getProjectIdentifier(Scope.PROJECT, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(Scope.PROJECT, inputDTO);
    String identifier =
        MigratorUtility.generateManifestIdentifier(prefix + "EcsServiceSpec", inputDTO.getIdentifierCaseFormat());
    String name = identifier + ".json";
    return NGYamlFile.builder()
        .type(NGMigrationEntityType.ECS_SERVICE_SPEC)
        .filename(null)
        .yaml(FileYamlDTO.builder()
                  .identifier(identifier)
                  .fileUsage(fileUsage)
                  .name(name)
                  .content(new String(content))
                  .orgIdentifier(orgIdentifier)
                  .projectIdentifier(projectIdentifier)
                  .build())
        .ngEntityDetail(NgEntityDetail.builder()
                            .identifier(identifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build())
        .cgBasicInfo(serviceSpecification.getCgBasicInfo())
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

  public List<ManifestConfigWrapper> getServiceSpec(Set<CgEntityId> serviceSpecIds, MigrationInputDTO inputDTO,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    if (isEmpty(serviceSpecIds)) {
      return new ArrayList<>();
    }
    List<ManifestConfigWrapper> manifestConfigWrappers = new ArrayList<>();
    for (CgEntityId configEntityId : serviceSpecIds) {
      CgEntityNode configNode = entities.get(configEntityId);
      if (configNode != null) {
        EcsServiceSpecification specification = (EcsServiceSpecification) configNode.getEntity();
        NGYamlFile file = getYamlFile(specification, inputDTO, entities, migratedEntities);
        if (file != null) {
          manifestConfigWrappers.add(getConfigFileWrapper(specification, file));
        }
      }
    }
    return manifestConfigWrappers;
  }

  @Override
  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean migrateAll) {
    return migrateAll || root.getType() == NGMigrationEntityType.SERVICE;
  }

  private static ManifestConfigWrapper getConfigFileWrapper(EcsServiceSpecification configFile, NGYamlFile file) {
    ParameterField<List<String>> files;
    files = MigratorUtility.getFileStorePaths(Collections.singletonList(file));
    return ManifestConfigWrapper.builder()
        .manifest(
            ManifestConfig.builder()
                .type(ManifestConfigType.ECS_SERVICE_DEFINITION)
                .identifier(configFile.getUuid())
                .spec(EcsServiceDefinitionManifest.builder()
                          .identifier(ManifestConfigType.ECS_SERVICE_DEFINITION.getDisplayName())
                          .store(ParameterField.createValueField(StoreConfigWrapper.builder()
                                                                     .type(StoreConfigType.HARNESS)
                                                                     .spec(HarnessStore.builder().files(files).build())
                                                                     .build()))
                          .build())
                .build())
        .build();
  }
}
