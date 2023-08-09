/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.NGMigrationConstants.TRIGGER_TAG_VALUE_DEFAULT;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.ngtriggers.beans.source.artifact.AMIRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.trigger.Trigger;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AmiArtifactStreamMapper implements ArtifactStreamMapper {
  @Override
  public PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    AmiArtifactStream amiArtifactStream = (AmiArtifactStream) artifactStream;
    NgEntityDetail connector =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(amiArtifactStream.getSettingId()).build())
            .getNgEntityDetail();
    List<AMITag> tags = new ArrayList<>();
    List<AMIFilter> filters = new ArrayList<>();
    if (isNotEmpty(amiArtifactStream.getTags())) {
      tags = amiArtifactStream.getTags()
                 .stream()
                 .map(t -> AMITag.builder().name(t.getKey()).value(t.getValue()).build())
                 .collect(Collectors.toList());
    }

    if (isNotEmpty(amiArtifactStream.getFilters())) {
      filters = amiArtifactStream.getFilters()
                    .stream()
                    .map(f -> AMIFilter.builder().name(f.getKey()).value(f.getValue()).build())
                    .collect(Collectors.toList());
    }

    return PrimaryArtifact.builder()
        .sourceType(ArtifactSourceType.AMI)
        .spec(AMIArtifactConfig.builder()
                  .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
                  .region(ParameterField.createValueField(amiArtifactStream.getRegion()))
                  .tags(ParameterField.createValueField(tags))
                  .filters(ParameterField.createValueField(filters))
                  .version(ParameterField.createValueField("<+input>"))
                  .build())
        .build();
  }

  @Override
  public ArtifactType getArtifactType(Map<CgEntityId, NGYamlFile> migratedEntities, ArtifactStream artifactStream) {
    return ArtifactType.AMI;
  }

  @Override
  public ArtifactTypeSpec getTriggerSpec(Map<CgEntityId, CgEntityNode> entities, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities, Trigger trigger) {
    String connectorRef = getConnectorRef(migratedEntities, artifactStream);
    List<TriggerEventDataCondition> eventConditions = Collections.emptyList();
    String region = "us-east-1";
    List<AMITag> tags = Collections.emptyList();
    List<AMIFilter> filters = Collections.emptyList();
    String version = TRIGGER_TAG_VALUE_DEFAULT;

    if (artifactStream != null) {
      AmiArtifactStream amiArtifactStream = (AmiArtifactStream) artifactStream;
      region = amiArtifactStream.getRegion();
      if (isNotEmpty(amiArtifactStream.getTags())) {
        tags = amiArtifactStream.getTags()
                   .stream()
                   .map(t -> AMITag.builder().name(t.getKey()).value(t.getValue()).build())
                   .collect(Collectors.toList());
      }

      if (isNotEmpty(amiArtifactStream.getFilters())) {
        filters = amiArtifactStream.getFilters()
                      .stream()
                      .map(f -> AMIFilter.builder().name(f.getKey()).value(f.getValue()).build())
                      .collect(Collectors.toList());
      }
    }

    return AMIRegistrySpec.builder()
        .connectorRef(connectorRef)
        .eventConditions(eventConditions)
        .region(region)
        .tags(tags)
        .filters(filters)
        .version(version)
        .build();
  }
}
