/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.trigger.Trigger;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public interface ArtifactStreamMapper {
  PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities);

  ArtifactType getArtifactType(Map<CgEntityId, NGYamlFile> migratedEntities, ArtifactStream artifactStream);

  ArtifactTypeSpec getTriggerSpec(Map<CgEntityId, CgEntityNode> entities, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities, Trigger trigger);

  default String getConnectorRef(Map<CgEntityId, NGYamlFile> migratedEntities, ArtifactStream artifactStream) {
    if (artifactStream == null) {
      return PLEASE_FIX_ME;
    }
    NgEntityDetail connector =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(artifactStream.getSettingId()).build())
            .getNgEntityDetail();
    if (connector != null) {
      return MigratorUtility.getIdentifierWithScope(connector);
    }
    return PLEASE_FIX_ME;
  }
}
