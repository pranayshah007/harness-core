/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.data.structure.EmptyPredicate;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.YamlGenerationDetails;
import io.harness.ngmigration.beans.summary.ArtifactStreamSummary;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.service.NgMigrationService;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.ArtifactStreamService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactStreamMigrationService extends NgMigrationService {
  @Inject private ArtifactStreamService artifactStreamService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    throw new IllegalAccessError("Mapping not allowed for ArtifactStream Service");
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Map<String, Long> typeSummary = entities.stream()
                                        .map(entity -> (ArtifactStream) entity.getEntity())
                                        .collect(groupingBy(ArtifactStream::getArtifactStreamType, counting()));
    return new ArtifactStreamSummary(entities.size(), typeSummary);
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    ArtifactStream artifactStream = (ArtifactStream) entity;
    String entityId = artifactStream.getUuid();
    CgEntityId artifactStreamEntityId =
        CgEntityId.builder().type(NGMigrationEntityType.ARTIFACT_STREAM).id(entityId).build();
    CgEntityNode artifactStreamNode = CgEntityNode.builder()
                                          .id(entityId)
                                          .appId(artifactStream.getAppId())
                                          .type(NGMigrationEntityType.ARTIFACT_STREAM)
                                          .entityId(artifactStreamEntityId)
                                          .entity(artifactStream)
                                          .build();
    Set<CgEntityId> children = Collections.emptySet();
    // There is no connector for CUSTOM ARTIFACT SOURCE
    if (!ArtifactStreamType.CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      children = Collections.singleton(
          CgEntityId.builder().type(NGMigrationEntityType.CONNECTOR).id(artifactStream.getSettingId()).build());
    } else {
      if (null != artifactStream.getTemplateUuid()) {
        children = Collections.singleton(
            CgEntityId.builder().type(NGMigrationEntityType.TEMPLATE).id(artifactStream.getTemplateUuid()).build());
      }
    }

    return DiscoveryNode.builder().children(children).entityNode(artifactStreamNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(artifactStreamService.get(entityId));
  }

  @Override
  public YamlGenerationDetails generateYaml(MigrationContext migrationContext, CgEntityId entityId) {
    return null;
  }

  @Override
  protected YamlDTO getNGEntity(Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, NGYamlFile> migratedEntities,
      CgEntityNode cgEntityNode, NgEntityDetail ngEntityDetail, String accountIdentifier) {
    return null;
  }

  @Override
  protected boolean isNGEntityExists() {
    return false;
  }

  @Override
  public boolean canMigrate(CgEntityId id, CgEntityId root, boolean migrateAll) {
    return migrateAll || root.getType().equals(NGMigrationEntityType.SERVICE);
  }
}
