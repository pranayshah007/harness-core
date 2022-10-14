/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.Map;
import java.util.Set;

public class AmazonS3ArtifactStreamMapper implements ArtifactStreamMapper {
  @Override
  public PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    AmazonS3ArtifactStream amazonS3ArtifactStream = (AmazonS3ArtifactStream) artifactStream;
    NgEntityDetail connector =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(amazonS3ArtifactStream.getSettingId()).build())
            .getNgEntityDetail();
    return PrimaryArtifact.builder()
        .sourceType(ArtifactSourceType.AMAZONS3)
        .spec(AmazonS3ArtifactConfig.builder()
                  .primaryArtifact(true)
                  .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
                  .bucketName(ParameterField.createValueField(amazonS3ArtifactStream.getJobname()))
                  .filePath(EmptyPredicate.isNotEmpty(amazonS3ArtifactStream.getArtifactPaths())
                          ? ParameterField.createValueField(amazonS3ArtifactStream.getArtifactPaths().get(0))
                          : null)
                  .build())
        .build();
  }
}
