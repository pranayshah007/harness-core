/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.servicev2;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.ngmigration.NGMigrationEntityType.ARTIFACT_STREAM;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;

import software.wings.beans.Service;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDC)
public interface ServiceV2Mapper {
  ServiceDefinition getServiceDefinition(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, Service service, Map<CgEntityId, NGYamlFile> migratedEntities,
      List<ManifestConfigWrapper> manifests);

  default List<ArtifactStream> getArtifactStream(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, Service service) {
    CgEntityId cgEntityId = CgEntityId.builder().id(service.getUuid()).type(NGMigrationEntityType.SERVICE).build();
    if (isNotEmpty(graph.get(cgEntityId))
        && graph.get(cgEntityId).stream().anyMatch(e -> e.getType() == ARTIFACT_STREAM)) {
      return graph.get(cgEntityId)
          .stream()
          .filter(e -> e.getType() == ARTIFACT_STREAM)
          .map(entityId -> (ArtifactStream) entities.get(entityId).getEntity())
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }
}
