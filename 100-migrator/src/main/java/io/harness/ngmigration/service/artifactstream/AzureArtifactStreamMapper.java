/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.Map;
import java.util.Set;

public class AzureArtifactStreamMapper implements ArtifactStreamMapper {
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
                  .primaryArtifact(true)
                  .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
                  .feed(ParameterField.<String>builder().value(azureArtifactsArtifactStream.getFeed()).build())
                  .packageName(
                      ParameterField.<String>builder().value(azureArtifactsArtifactStream.getPackageName()).build())
                  .scope(ParameterField.<String>builder().value(scope).build())
                  .project(ParameterField.<String>builder().value(azureArtifactsArtifactStream.getProject()).build())
                  //                        skipping identifier like other artifacts
                  //                        .identifier()
                  .packageType(ParameterField.<String>builder()
                                   .value(azureArtifactsArtifactStream.getProtocolType().toLowerCase())
                                   .build())
                  .build())
        .build();
  }
}
