/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.cdng.artifact.bean.yaml.JenkinsArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.JenkinsRegistrySpec;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.trigger.Trigger;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class JenkinsArtifactStreamMapper implements ArtifactStreamMapper {
  @Override
  public PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    JenkinsArtifactStream jenkinsArtifactStream = (JenkinsArtifactStream) artifactStream;
    NgEntityDetail connector =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(jenkinsArtifactStream.getSettingId()).build())
            .getNgEntityDetail();
    return PrimaryArtifact.builder()
        .sourceType(ArtifactSourceType.JENKINS)
        .spec(JenkinsArtifactConfig.builder()
                  .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
                  .jobName(ParameterField.createValueField(jenkinsArtifactStream.getJobname()))
                  .artifactPath(jenkinsArtifactStream.getArtifactPaths() != null
                          ? ParameterField.createValueField(jenkinsArtifactStream.getArtifactPaths().get(0))
                          : null)
                  .build(ParameterField.createValueField("<+input>"))
                  .build())
        .build();
  }

  @Override
  public ArtifactType getArtifactType(Map<CgEntityId, NGYamlFile> migratedEntities, ArtifactStream artifactStream) {
    return ArtifactType.JENKINS;
  }

  @Override
  public ArtifactTypeSpec getTriggerSpec(Map<CgEntityId, CgEntityNode> entities, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities, Trigger trigger) {
    String artifactPath = PLEASE_FIX_ME;
    String jobName = PLEASE_FIX_ME;
    if (artifactStream != null) {
      JenkinsArtifactStream jenkinsArtifactStream = (JenkinsArtifactStream) artifactStream;
      jobName = jenkinsArtifactStream.getJobname();
      if (EmptyPredicate.isNotEmpty(jenkinsArtifactStream.getArtifactPaths())) {
        artifactPath = jenkinsArtifactStream.getArtifactPaths().get(0);
      }
    }
    return JenkinsRegistrySpec.builder()
        .connectorRef(getConnectorRef(migratedEntities, artifactStream))
        .jobName(jobName)
        .eventConditions(Collections.emptyList())
        .artifactPath(artifactPath)
        .build(PLEASE_FIX_ME)
        .build();
  }
}
