/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.source.artifact.AmazonS3RegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.trigger.Trigger;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.List;
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
                  .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
                  .bucketName(ParameterField.createValueField(amazonS3ArtifactStream.getJobname()))
                  .filePath(EmptyPredicate.isNotEmpty(amazonS3ArtifactStream.getArtifactPaths())
                          ? ParameterField.createValueField(amazonS3ArtifactStream.getArtifactPaths().get(0))
                          : null)
                  .build())
        .build();
  }

  @Override
  public ArtifactType getArtifactType(Map<CgEntityId, NGYamlFile> migratedEntities, ArtifactStream artifactStream) {
    return ArtifactType.AMAZON_S3;
  }

  @Override
  public ArtifactTypeSpec getTriggerSpec(Map<CgEntityId, CgEntityNode> entities, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities, Trigger trigger) {
    String connectorRef = getConnectorRef(migratedEntities, artifactStream);
    List<TriggerEventDataCondition> eventConditions = getEventConditions(trigger);
    String bucketName = PLEASE_FIX_ME;
    String filePathRegex = PLEASE_FIX_ME;

    if (artifactStream != null) {
      AmazonS3ArtifactStream s3ArtifactStream = (AmazonS3ArtifactStream) artifactStream;
      bucketName = s3ArtifactStream.getJobname();
      if (EmptyPredicate.isNotEmpty(s3ArtifactStream.getArtifactPaths())) {
        filePathRegex = s3ArtifactStream.getArtifactPaths().get(0);
      }
    }

    return AmazonS3RegistrySpec.builder()
        .filePathRegex(filePathRegex)
        .bucketName(bucketName)
        .eventConditions(eventConditions)
        .connectorRef(connectorRef)
        .build();
  }
}
