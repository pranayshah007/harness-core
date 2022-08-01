/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.ngmigration.NGMigrationEntityType.ARTIFACT_STREAM;
import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;
import static software.wings.ngmigration.NGMigrationEntityType.SERVICE;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYaml;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.encryption.Scope;
import io.harness.exception.UnsupportedOperationException;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.BaseInputDefinition;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.MigratorInputType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.ServiceSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.expressions.MigratorExpressionUtils;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.pms.yaml.ParameterField;

import software.wings.api.DeploymentType;
import software.wings.beans.Service;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDC)
public class ServiceMigrationService extends NgMigrationService {
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private ManifestMigrationService manifestMigrationService;
  @Inject private MigratorExpressionUtils migratorExpressionUtils;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    throw new IllegalAccessError("Mapping not allowed for Service entities");
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
    Set<CgEntityId> children = new HashSet<>();
    if (isNotEmpty(artifactStreams)) {
      children.addAll(
          artifactStreams.stream()
              .map(artifactStream -> CgEntityId.builder().id(artifactStream.getUuid()).type(ARTIFACT_STREAM).build())
              .collect(Collectors.toSet()));
    }
    return DiscoveryNode.builder().entityNode(serviceEntityNode).children(children).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(serviceResourceService.get(entityId));
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

  private PrimaryArtifact getPrimaryArtifact(
      ArtifactStream artifactStream, Map<CgEntityId, NgEntityDetail> migratedEntities) {
    if (artifactStream instanceof DockerArtifactStream) {
      DockerArtifactStream dockerArtifactStream = (DockerArtifactStream) artifactStream;
      NgEntityDetail connector =
          migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(dockerArtifactStream.getSettingId()).build());
      return PrimaryArtifact.builder()
          .sourceType(ArtifactSourceType.DOCKER_REGISTRY)
          .spec(DockerHubArtifactConfig.builder()
                    .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
                    .imagePath(ParameterField.createValueField(dockerArtifactStream.getImageName()))
                    .tag(ParameterField.createValueField("<+input>"))
                    .build())
          .build();
    }
    throw new UnsupportedOperationException("Only Docker Artifact Streams are supported");
  }

  public ServiceConfig getServiceConfig(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities,
      Set<CgEntityId> manifests) {
    Service service = (Service) entities.get(entityId).getEntity();
    migratorExpressionUtils.render(service);
    PrimaryArtifact primaryArtifact = null;
    if (isNotEmpty(graph.get(entityId)) && graph.get(entityId).stream().anyMatch(e -> e.getType() == ARTIFACT_STREAM)) {
      CgEntityId artifactStreamId =
          graph.get(entityId)
              .stream()
              .filter(e -> e.getType() == ARTIFACT_STREAM)
              .findFirst()
              .orElseThrow(() -> new UnsupportedOperationException("This should not be thrown"));
      ArtifactStream artifactStream = (ArtifactStream) entities.get(artifactStreamId).getEntity();
      migratorExpressionUtils.render(artifactStream);
      primaryArtifact = getPrimaryArtifact(artifactStream, migratedEntities);
    }

    List<ManifestConfigWrapper> manifestConfigWrapperList =
        manifestMigrationService.getManifests(manifests, inputDTO, entities, graph, migratedEntities);
    ServiceDefinition serviceDefinition =
        ServiceDefinition.builder()
            .type(ServiceDefinitionType.KUBERNETES)
            .serviceSpec(KubernetesServiceSpec.builder()
                             .variables(new ArrayList<>())
                             .artifacts(ArtifactListConfig.builder().primary(primaryArtifact).build())
                             .manifests(manifestConfigWrapperList)
                             .build())
            .build();

    ServiceYaml serviceYaml = ServiceYaml.builder()
                                  .name(MigratorUtility.generateIdentifier(service.getName()))
                                  .identifier(service.getName())
                                  .description(ParameterField.createValueField(service.getDescription()))
                                  .tags(null)
                                  .build();
    return ServiceConfig.builder().service(serviceYaml).serviceDefinition(serviceDefinition).build();
  }

  @Override
  public void migrate(String auth, NGClient ngClient, PmsClient pmsClient, MigrationInputDTO inputDTO,
      NGYamlFile yamlFile) throws IOException {}

  @Override
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities,
      NgEntityDetail ngEntityDetail) {
    return new ArrayList<>();
  }

  @Override
  protected YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return false;
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
