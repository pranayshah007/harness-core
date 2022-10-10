/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.ngmigration.NGMigrationEntityType.ARTIFACT_STREAM;
import static software.wings.ngmigration.NGMigrationEntityType.MANIFEST;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET;
import static software.wings.ngmigration.NGMigrationEntityType.SERVICE;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.BaseInputDefinition;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.MigratorInputType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.ServiceSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.ngmigration.service.servicev2.ServiceV2Factory;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;

import software.wings.api.DeploymentType;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceMigrationService extends NgMigrationService {
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ManifestMigrationService manifestMigrationService;
  @Inject private MigratorExpressionUtils migratorExpressionUtils;
  @Inject private ApplicationManifestService applicationManifestService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    NGServiceV2InfoConfig serviceConfig = ((NGServiceConfig) yamlFile.getYaml()).getNgServiceV2InfoConfig();
    String orgIdentifier = yamlFile.getNgEntityDetail().getOrgIdentifier();
    String projectIdentifier = yamlFile.getNgEntityDetail().getProjectIdentifier();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(SERVICE.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .identifier(serviceConfig.getIdentifier())
        .scope(MigratorMappingService.getScope(orgIdentifier, projectIdentifier))
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(
            basicInfo.getAccountId(), orgIdentifier, projectIdentifier, serviceConfig.getIdentifier()))
        .build();
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Map<String, Long> artifactTypeSummary = entities.stream()
                                                .map(entity -> ((Service) entity.getEntity()).getArtifactType())
                                                .filter(Objects::nonNull)
                                                .collect(groupingBy(ArtifactType::name, counting()));
    Map<String, Long> deploymentTypeSummary = entities.stream()
                                                  .map(entity -> ((Service) entity.getEntity()).getDeploymentType())
                                                  .filter(Objects::nonNull)
                                                  .collect(groupingBy(DeploymentType::name, counting()));
    return new ServiceSummary(entities.size(), deploymentTypeSummary, artifactTypeSummary);
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    Service service = (Service) entity;
    String serviceId = service.getUuid();
    CgEntityId serviceEntityId = CgEntityId.builder().type(SERVICE).id(serviceId).build();
    CgEntityNode serviceEntityNode =
        CgEntityNode.builder().id(serviceId).type(SERVICE).entityId(serviceEntityId).entity(service).build();
    List<ArtifactStream> artifactStreams =
        artifactStreamService.getArtifactStreamsForService(service.getAppId(), serviceId);
    List<ApplicationManifest> applicationManifests =
        applicationManifestService.listAppManifests(service.getAppId(), serviceId);
    Set<CgEntityId> children = new HashSet<>();
    if (isNotEmpty(artifactStreams)) {
      children.addAll(
          artifactStreams.stream()
              .map(artifactStream -> CgEntityId.builder().id(artifactStream.getUuid()).type(ARTIFACT_STREAM).build())
              .collect(Collectors.toSet()));
    }
    if (isNotEmpty(applicationManifests)) {
      children.addAll(applicationManifests.stream()
                          .map(manifest -> CgEntityId.builder().id(manifest.getUuid()).type(MANIFEST).build())
                          .collect(Collectors.toList()));
    }
    if (isNotEmpty(service.getServiceVariables())) {
      children.addAll(
          service.getServiceVariables()
              .stream()
              .filter(serviceVariable -> serviceVariable.getType().equals(ServiceVariableType.ENCRYPTED_TEXT))
              .map(serviceVariable -> CgEntityId.builder().id(serviceVariable.getEncryptedValue()).type(SECRET).build())
              .collect(Collectors.toList()));
    }
    return DiscoveryNode.builder().entityNode(serviceEntityNode).children(children).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(serviceResourceService.getWithDetails(appId, entityId));
  }

  @Override
  public NGMigrationStatus canMigrate(NGMigrationEntity entity) {
    Service service = (Service) entity;
    if (!ArtifactType.DOCKER.equals(service.getArtifactType())) {
      return NGMigrationStatus.builder()
          .status(false)
          .reasons(Collections.singletonList(String.format(
              "%s service of artifact type %s is not supported", service.getName(), service.getArtifactType())))
          .build();
    }
    if (!DeploymentType.KUBERNETES.equals(service.getDeploymentType())) {
      return NGMigrationStatus.builder()
          .status(false)
          .reasons(Collections.singletonList(String.format(
              "%s service of deployment type %s is not supported", service.getName(), service.getDeploymentType())))
          .build();
    }
    return NGMigrationStatus.builder().status(true).build();
  }

  public ServiceConfig getServiceConfig(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    NGYamlFile entityDetail = migratedEntities.get(entityId);
    return ServiceConfig.builder()
        .serviceRef(ParameterField.createValueField(entityDetail.getNgEntityDetail().getIdentifier()))
        .build();
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    if (yamlFile.isExists()) {
      log.info("Skipping creation of service as it already exists");
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(ImportError.builder()
                                                .message("Service was not migrated as it was already imported before")
                                                .entity(yamlFile.getCgBasicInfo())
                                                .build()))
          .build();
    }
    ServiceRequestDTO serviceRequestDTO =
        ServiceRequestDTO.builder()
            .description(null)
            .identifier(yamlFile.getNgEntityDetail().getIdentifier())
            .name(((NGServiceConfig) yamlFile.getYaml()).getNgServiceV2InfoConfig().getName())
            .orgIdentifier(yamlFile.getNgEntityDetail().getOrgIdentifier())
            .projectIdentifier(yamlFile.getNgEntityDetail().getProjectIdentifier())
            .yaml(getYamlString(yamlFile))
            .build();
    Response<ResponseDTO<ServiceResponse>> resp =
        ngClient.createService(auth, inputDTO.getAccountIdentifier(), JsonUtils.asTree(serviceRequestDTO)).execute();
    log.info("Service creation Response details {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  @Override
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities,
      NgEntityDetail ngEntityDetail) {
    Service service = (Service) entities.get(entityId).getEntity();
    String name = service.getName();
    String identifier = MigratorUtility.generateIdentifier(name);

    if (inputDTO.getInputs() != null && inputDTO.getInputs().containsKey(entityId)) {
      // TODO: @deepakputhraya We should handle if the service needs to be reused.
      BaseProvidedInput input = inputDTO.getInputs().get(entityId);
      identifier = StringUtils.isNotBlank(input.getIdentifier()) ? input.getIdentifier() : identifier;
      name = StringUtils.isNotBlank(input.getIdentifier()) ? input.getName() : name;
    }

    migratorExpressionUtils.render(service);
    Set<CgEntityId> manifests =
        graph.get(entityId).stream().filter(cgEntityId -> cgEntityId.getType() == MANIFEST).collect(Collectors.toSet());
    List<ManifestConfigWrapper> manifestConfigWrapperList =
        manifestMigrationService.getManifests(manifests, inputDTO, entities, graph, migratedEntities);

    NGServiceConfig serviceYaml =
        NGServiceConfig.builder()
            .ngServiceV2InfoConfig(
                NGServiceV2InfoConfig.builder()
                    .name(name)
                    .description(service.getDescription())
                    .gitOpsEnabled(false)
                    .identifier(identifier)
                    .tags(new HashMap<>())
                    .useFromStage(null)
                    .serviceDefinition(ServiceV2Factory.getService2Mapper(service).getServiceDefinition(
                        inputDTO, entities, graph, service, migratedEntities, manifestConfigWrapperList))
                    .build())
            .build();
    NGYamlFile ngYamlFile = NGYamlFile.builder()
                                .filename(String.format("service/%s/%s.yaml", service.getAppId(), service.getName()))
                                .yaml(serviceYaml)
                                .type(SERVICE)
                                .ngEntityDetail(NgEntityDetail.builder()
                                                    .identifier(identifier)
                                                    .orgIdentifier(inputDTO.getOrgIdentifier())
                                                    .projectIdentifier(inputDTO.getProjectIdentifier())
                                                    .build())
                                .cgBasicInfo(CgBasicInfo.builder()
                                                 .accountId(service.getAccountId())
                                                 .appId(service.getAppId())
                                                 .id(service.getUuid())
                                                 .type(SERVICE)
                                                 .build())
                                .build();
    migratedEntities.putIfAbsent(entityId, ngYamlFile);
    return Collections.singletonList(ngYamlFile);
  }

  @Override
  protected YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }

  @Override
  public BaseEntityInput generateInput(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {
    Service service = (Service) entities.get(entityId).getEntity();
    return BaseEntityInput.builder()
        .migrationStatus(MigratorInputType.CREATE_NEW)
        .identifier(BaseInputDefinition.buildIdentifier(MigratorUtility.generateIdentifier(service.getName())))
        .name(BaseInputDefinition.buildName(service.getName()))
        .scope(BaseInputDefinition.buildScope(Scope.PROJECT))
        .spec(null)
        .build();
  }
}
