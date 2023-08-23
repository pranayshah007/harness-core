/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.utils.transformers;

import io.harness.spec.server.ssca.v1.model.EnforcementSummaryDTO;
import io.harness.ssca.entities.EnforcementSummaryEntity;

import org.modelmapper.TypeMap;

public class EnforcementSummaryTransformer implements Transformer<EnforcementSummaryEntity, EnforcementSummaryDTO> {
  @Override
  public EnforcementSummaryEntity toEntity(EnforcementSummaryDTO dto) {
    TypeMap<EnforcementSummaryDTO, EnforcementSummaryEntity> propertyMap =
        modelMapper.createTypeMap(dto, EnforcementSummaryEntity.class);
    // propertyMap.addMappings();
    return modelMapper.map(dto, EnforcementSummaryEntity.class);
  }

  @Override
  public EnforcementSummaryDTO toDTO(EnforcementSummaryEntity entity) {
    return modelMapper.map(entity, EnforcementSummaryDTO.class);
  }

  /*public static EnforcementSummaryEntity toEntity(EnforcementSummaryDTO dto) {
    return EnforcementSummaryEntity.builder()
        .status(dto.getStatus())
        .allowListViolationCount(dto.getAllowListViolationCount().intValue())
        .enforcementId(dto.getEnforcementId())
        .denyListViolationCount(dto.getDenyListViolationCount().intValue())
        .orchestrationId(dto.getOrchestrationId())
        .artifact(Artifact.builder()
                      .url(dto.getArtifact().getRegistryUrl())
                      .type(dto.getArtifact().getType())
                      .name(dto.getArtifact().getName())
                      .tag(dto.getArtifact().getTag())
                      .artifactId(dto.getArtifact().getId())
                      .build())
        .build();
  }

  public static EnforcementSummaryDTO toDTO(EnforcementSummaryEntity entity) {
    return new EnforcementSummaryDTO()
        .enforcementId(entity.getEnforcementId())
        .allowListViolationCount(new BigDecimal(entity.getAllowListViolationCount()))
        .denyListViolationCount(new BigDecimal(entity.getDenyListViolationCount()))
        .orchestrationId(entity.getOrchestrationId())
        .status(entity.getStatus())
        .artifact(new io.harness.spec.server.ssca.v1.model.Artifact()
                      .id(entity.getArtifact().getArtifactId())
                      .name(entity.getArtifact().getName())
                      .tag(entity.getArtifact().getTag())
                      .type(entity.getArtifact().getType())
                      .registryUrl(entity.getArtifact().getUrl()));
  }*/
}
