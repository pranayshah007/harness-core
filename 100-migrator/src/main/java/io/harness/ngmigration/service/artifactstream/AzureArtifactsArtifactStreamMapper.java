/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;
import static io.harness.ngmigration.utils.NGMigrationConstants.TRIGGER_TAG_VALUE_DEFAULT;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.AzureArtifactsRegistrySpec;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.beans.trigger.Trigger;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AzureArtifactsArtifactStreamMapper implements ArtifactStreamMapper {
  @Override
  public PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    AzureArtifactsArtifactStream azureArtifactsArtifactStream = (AzureArtifactsArtifactStream) artifactStream;
    NgEntityDetail connector =
        migratedEntities
            .get(CgEntityId.builder().type(CONNECTOR).id(azureArtifactsArtifactStream.getSettingId()).build())
            .getNgEntityDetail();
    String scope = "org";
    if (isNotEmpty(azureArtifactsArtifactStream.getProject())) {
      scope = "project";
    }
    return PrimaryArtifact.builder()
        .sourceType(ArtifactSourceType.AZURE_ARTIFACTS)
        .spec(AzureArtifactsConfig.builder()
                  .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
                  .packageType(ParameterField.createValueField(azureArtifactsArtifactStream.getProtocolType()))
                  .packageName(ParameterField.createValueField(azureArtifactsArtifactStream.getPackageName()))
                  .project(ParameterField.createValueField(azureArtifactsArtifactStream.getProject()))
                  .feed(ParameterField.createValueField(azureArtifactsArtifactStream.getFeed()))
                  .scope(ParameterField.createValueField(scope))
                  .version(MigratorUtility.RUNTIME_INPUT)
                  .build())
        .build();
  }

  @Override
  public ArtifactType getArtifactType(Map<CgEntityId, NGYamlFile> migratedEntities, ArtifactStream artifactStream) {
    return ArtifactType.AZURE_ARTIFACTS;
  }

  @Override
  public ArtifactTypeSpec getTriggerSpec(Map<CgEntityId, CgEntityNode> entities, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities, Trigger trigger) {
    String connectorRef = getConnectorRef(migratedEntities, artifactStream);
    String project = PLEASE_FIX_ME;
    String feed = PLEASE_FIX_ME;
    String packageName = PLEASE_FIX_ME;
    String packageType = PLEASE_FIX_ME;
    String version = TRIGGER_TAG_VALUE_DEFAULT;
    if (artifactStream != null) {
      AzureArtifactsArtifactStream stream = (AzureArtifactsArtifactStream) artifactStream;
      project = stream.getProject();
      feed = stream.getFeed();
      packageName = stream.getPackageName();
      packageType = stream.getProtocolType();
    }
    List<TriggerEventDataCondition> eventConditions = Collections.emptyList();

    return AzureArtifactsRegistrySpec.builder()
        .packageType(packageType)
        .project(project)
        .feed(feed)
        .connectorRef(connectorRef)
        .packageName(packageName)
        .version(version)
        .eventConditions(eventConditions)
        .build();
  }
}
