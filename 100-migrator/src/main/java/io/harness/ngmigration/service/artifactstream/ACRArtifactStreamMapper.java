/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static io.harness.ngmigration.utils.NGMigrationConstants.PLEASE_FIX_ME;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.source.artifact.AcrSpec;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.artifact.AcrArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.trigger.Trigger;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ACRArtifactStreamMapper implements ArtifactStreamMapper {
  @Override
  public PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    AcrArtifactStream acrArtifactStream = (AcrArtifactStream) artifactStream;
    NgEntityDetail connector =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(acrArtifactStream.getSettingId()).build())
            .getNgEntityDetail();
    return PrimaryArtifact.builder()
        .sourceType(ArtifactSourceType.ACR)
        .spec(AcrArtifactConfig.builder()
                  .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
                  .subscriptionId(ParameterField.createValueField(acrArtifactStream.getSubscriptionId()))
                  .registry(ParameterField.createValueField(acrArtifactStream.getRegistryName()))
                  .repository(ParameterField.createValueField(acrArtifactStream.getRepositoryName()))
                  .subscriptionId(ParameterField.createValueField(acrArtifactStream.getSubscriptionId()))
                  .tag(ParameterField.createValueField("<+input>"))
                  .build())
        .build();
  }

  @Override
  public ArtifactType getArtifactType(Map<CgEntityId, NGYamlFile> migratedEntities, ArtifactStream artifactStream) {
    return ArtifactType.ACR;
  }

  @Override
  public ArtifactTypeSpec getTriggerSpec(Map<CgEntityId, CgEntityNode> entities, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities, Trigger trigger) {
    String connectorRef = getConnectorRef(migratedEntities, artifactStream);
    List<TriggerEventDataCondition> eventConditions = Collections.emptyList();
    String registry = PLEASE_FIX_ME;
    String repository = PLEASE_FIX_ME;
    String subscriptionId = PLEASE_FIX_ME;
    String tag = PLEASE_FIX_ME;

    if (artifactStream != null) {
      AcrArtifactStream acrArtifactStream = (AcrArtifactStream) artifactStream;
      registry = acrArtifactStream.getRegistryName();
      repository = acrArtifactStream.getRepositoryName();
      subscriptionId = acrArtifactStream.getSubscriptionId();
    }

    return AcrSpec.builder()
        .subscriptionId(subscriptionId)
        .tag(tag)
        .registry(registry)
        .repository(repository)
        .eventConditions(eventConditions)
        .connectorRef(connectorRef)
        .build();
  }
}
