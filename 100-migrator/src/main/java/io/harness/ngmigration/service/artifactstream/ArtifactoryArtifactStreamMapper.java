/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;
import static io.harness.ngmigration.utils.NGMigrationConstants.TRIGGER_TAG_VALUE_DEFAULT;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;
import static software.wings.utils.RepositoryType.docker;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.connector.ConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.ArtifactoryRegistrySpec;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.trigger.Trigger;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactoryArtifactStreamMapper implements ArtifactStreamMapper {
  @Override
  public PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    ArtifactoryArtifactStream artifactoryArtifactStream = (ArtifactoryArtifactStream) artifactStream;
    NGYamlFile connector =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(artifactoryArtifactStream.getSettingId()).build());
    return PrimaryArtifact.builder()
        .sourceType(ArtifactSourceType.ARTIFACTORY_REGISTRY)
        .spec(docker.name().equals(artifactoryArtifactStream.getRepositoryType())
                ? generateDockerConfig(artifactoryArtifactStream, connector)
                : generateGeneticConfig(artifactoryArtifactStream, connector))
        .build();
  }

  @Override
  public ArtifactType getArtifactType(Map<CgEntityId, NGYamlFile> migratedEntities, ArtifactStream artifactStream) {
    return ArtifactType.ARTIFACTORY_REGISTRY;
  }

  @Override
  public ArtifactTypeSpec getTriggerSpec(Map<CgEntityId, CgEntityNode> entities, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities, Trigger trigger) {
    String connectorRef = getConnectorRef(migratedEntities, artifactStream);
    List<TriggerEventDataCondition> eventConditions = Collections.emptyList();
    String imagePath = PLEASE_FIX_ME;
    String repository = PLEASE_FIX_ME;
    String format = docker.name();
    String repositoryUrl = "";
    if (artifactStream != null) {
      ArtifactoryArtifactStream stream = (ArtifactoryArtifactStream) artifactStream;
      imagePath = stream.getImageName();
      repository = stream.getJobname();
      format = docker.name().equals(stream.getRepositoryType()) ? docker.name() : "generic";
      repositoryUrl = stream.getDockerRepositoryServer();
    }

    return ArtifactoryRegistrySpec.builder()
        .connectorRef(connectorRef)
        .eventConditions(eventConditions)
        .repositoryFormat(format)
        .repository(repository)
        .repositoryUrl(repositoryUrl)
        .artifactDirectory(imagePath)
        .artifactPath(TRIGGER_TAG_VALUE_DEFAULT)
        .build();
  }

  private ArtifactoryRegistryArtifactConfig generateDockerConfig(
      ArtifactoryArtifactStream artifactStream, NGYamlFile connector) {
    String repoUrl = artifactStream.getDockerRepositoryServer();
    if (StringUtils.isBlank(repoUrl)) {
      ConnectorDTO connectorDTO = (ConnectorDTO) connector.getYaml();
      ArtifactoryConnectorDTO artifactoryConnectorDTO =
          (ArtifactoryConnectorDTO) connectorDTO.getConnectorInfo().getConnectorConfig();
      String url = artifactoryConnectorDTO.getArtifactoryServerUrl();
      repoUrl = URI.create(url).getHost();
    }

    return ArtifactoryRegistryArtifactConfig.builder()
        .connectorRef(
            ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector.getNgEntityDetail())))
        .repository(ParameterField.createValueField(artifactStream.getJobname()))
        .repositoryUrl(ParameterField.createValueField(repoUrl))
        .artifactPath(ParameterField.createValueField(artifactStream.getImageName()))
        .repositoryFormat(ParameterField.createValueField("docker"))
        .tag(ParameterField.createValueField("<+input>"))
        .build();
  }

  private ArtifactoryRegistryArtifactConfig generateGeneticConfig(
      ArtifactoryArtifactStream artifactStream, NGYamlFile connector) {
    return ArtifactoryRegistryArtifactConfig.builder()
        .connectorRef(
            ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector.getNgEntityDetail())))
        .repository(ParameterField.createValueField(artifactStream.getJobname()))
        .repositoryFormat(ParameterField.createValueField("generic"))
        .artifactDirectory(ParameterField.createValueField(artifactStream.getArtifactPattern()))
        .artifactPath(ParameterField.createValueField("<+input>"))
        .build();
  }
}
