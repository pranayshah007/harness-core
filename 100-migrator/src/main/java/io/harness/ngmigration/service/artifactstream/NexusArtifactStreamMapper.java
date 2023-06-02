/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.Nexus2RegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryConfigSpec;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryDockerConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryMavenConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNpmConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.NexusRegistryNugetConfig;
import io.harness.connector.ConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.trigger.Trigger;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class NexusArtifactStreamMapper implements ArtifactStreamMapper {
  private static final String VERSION_2 = "2.x";
  public static final String DOCKER = "docker";
  public static final String MAVEN = "maven";
  public static final String NPM = "npm";

  @Override
  public PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    NexusArtifactStream nexusArtifactStream = (NexusArtifactStream) artifactStream;
    NgEntityDetail ngConnector =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(nexusArtifactStream.getSettingId()).build())
            .getNgEntityDetail();
    CgEntityNode connector =
        entities.get(CgEntityId.builder().type(CONNECTOR).id(nexusArtifactStream.getSettingId()).build());
    SettingAttribute settingAttribute = (SettingAttribute) connector.getEntity();
    NexusConfig nexusConfig = (NexusConfig) settingAttribute.getValue();

    NexusRegistryConfigSpec nexusRegistryConfigSpec = getNexusRegistryConfigSpec(nexusArtifactStream);
    return PrimaryArtifact.builder()
        .sourceType(VERSION_2.equals(nexusConfig.getVersion()) ? ArtifactSourceType.NEXUS2_REGISTRY
                                                               : ArtifactSourceType.NEXUS3_REGISTRY)
        .spec(VERSION_2.equals(nexusConfig.getVersion())
                ? getNexus2RegistryArtifactConfig(nexusArtifactStream, nexusRegistryConfigSpec, ngConnector)
                : getNexus3ArtifactConfig(nexusArtifactStream, nexusRegistryConfigSpec, ngConnector))
        .build();
  }

  @Override
  public ArtifactType getArtifactType(Map<CgEntityId, NGYamlFile> migratedEntities, ArtifactStream artifactStream) {
    if (artifactStream == null) {
      return ArtifactType.NEXUS3_REGISTRY;
    }
    NGYamlFile ngYamlFile =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(artifactStream.getSettingId()).build());
    if (ngYamlFile == null) {
      return ArtifactType.NEXUS3_REGISTRY;
    }
    ConnectorDTO connectorDTO = (ConnectorDTO) ngYamlFile.getYaml();
    NexusConnectorDTO connector = (NexusConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
    return "3.x".equals(connector.getVersion()) ? ArtifactType.NEXUS3_REGISTRY : ArtifactType.NEXUS2_REGISTRY;
  }

  @Override
  public ArtifactTypeSpec getTriggerSpec(Map<CgEntityId, CgEntityNode> entities, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities, Trigger trigger) {
    return null;
  }

  private static NexusRegistryConfigSpec getNexusRegistryConfigSpec(NexusArtifactStream nexusArtifactStream) {
    switch (nexusArtifactStream.getRepositoryFormat()) {
      case DOCKER:
        return NexusRegistryDockerConfig.builder()
            .repositoryPort(nexusArtifactStream.getDockerPort() == null
                    ? null
                    : ParameterField.createValueField(nexusArtifactStream.getDockerPort()))
            .repositoryUrl(nexusArtifactStream.getDockerRegistryUrl() == null
                    ? null
                    : ParameterField.createValueField(nexusArtifactStream.getDockerRegistryUrl()))
            .artifactPath(ParameterField.createValueField(nexusArtifactStream.getImageName()))
            .build();
      case MAVEN:
        return NexusRegistryMavenConfig.builder()
            .artifactId(ParameterField.createValueField(nexusArtifactStream.getArtifactPaths().get(0)))
            .groupId(ParameterField.createValueField(nexusArtifactStream.getGroupId()))
            .extension(StringUtils.isBlank(nexusArtifactStream.getExtension())
                    ? null
                    : ParameterField.createValueField(nexusArtifactStream.getExtension()))
            .classifier(StringUtils.isBlank(nexusArtifactStream.getClassifier())
                    ? null
                    : ParameterField.createValueField(nexusArtifactStream.getClassifier()))
            .build();
      case NPM:
        return NexusRegistryNpmConfig.builder()
            .packageName(ParameterField.createValueField(nexusArtifactStream.getPackageName()))
            .build();
      default:
        return NexusRegistryNugetConfig.builder()
            .packageName(ParameterField.createValueField(nexusArtifactStream.getPackageName()))
            .build();
    }
  }

  private static ArtifactConfig getNexus3ArtifactConfig(NexusArtifactStream nexusArtifactStream,
      NexusRegistryConfigSpec nexusRegistryConfigSpec, NgEntityDetail ngConnector) {
    return NexusRegistryArtifactConfig.builder()
        .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(ngConnector)))
        .repository(ParameterField.createValueField(nexusArtifactStream.getJobname()))
        .repositoryFormat(ParameterField.createValueField(nexusArtifactStream.getRepositoryFormat()))
        .nexusRegistryConfigSpec(nexusRegistryConfigSpec)
        .tag(ParameterField.createValueField("<+input>"))
        .build();
  }

  private static Nexus2RegistryArtifactConfig getNexus2RegistryArtifactConfig(NexusArtifactStream nexusArtifactStream,
      NexusRegistryConfigSpec nexusRegistryConfigSpec, NgEntityDetail ngConnector) {
    return Nexus2RegistryArtifactConfig.builder()
        .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(ngConnector)))
        .repository(ParameterField.createValueField(nexusArtifactStream.getJobname()))
        .repositoryFormat(ParameterField.createValueField(nexusArtifactStream.getRepositoryFormat()))
        .nexusRegistryConfigSpec(nexusRegistryConfigSpec)
        .tag(ParameterField.createValueField("<+input>"))
        .build();
  }
}
