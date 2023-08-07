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

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.EcrSpec;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.EcrArtifactStream;
import software.wings.beans.trigger.Trigger;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class ECRArtifactStreamMapper implements ArtifactStreamMapper {
  @Override
  public PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    EcrArtifactStream ecrArtifactStream = (EcrArtifactStream) artifactStream;
    NgEntityDetail connector =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(ecrArtifactStream.getSettingId()).build())
            .getNgEntityDetail();
    return PrimaryArtifact.builder()
        .sourceType(ArtifactSourceType.ECR)
        .spec(EcrArtifactConfig.builder()
                  .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
                  .region(ParameterField.createValueField(ecrArtifactStream.getRegion()))
                  .imagePath(ParameterField.createValueField(ecrArtifactStream.getImageName()))
                  .tag(ParameterField.createValueField("<+input>"))
                  .build())
        .build();
  }

  @Override
  public ArtifactType getArtifactType(Map<CgEntityId, NGYamlFile> migratedEntities, ArtifactStream artifactStream) {
    return ArtifactType.ECR;
  }

  @Override
  public ArtifactTypeSpec getTriggerSpec(Map<CgEntityId, CgEntityNode> entities, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities, Trigger trigger) {
    String imagePath = PLEASE_FIX_ME;
    String region = "us-east-1";

    if (artifactStream != null) {
      EcrArtifactStream ecrArtifactStream = (EcrArtifactStream) artifactStream;
      imagePath = ecrArtifactStream.getImageName();
      region = ecrArtifactStream.getRegion();
    }

    return EcrSpec.builder()
        .connectorRef(getConnectorRef(migratedEntities, artifactStream))
        .region(region)
        .imagePath(imagePath)
        .tag(TRIGGER_TAG_VALUE_DEFAULT)
        .eventConditions(Collections.emptyList())
        .build();
  }
}
