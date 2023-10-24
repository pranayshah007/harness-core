/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.transformers;

import io.harness.spec.server.ssca.v1.model.EnforcementSummaryDTO;
import io.harness.ssca.beans.Artifact;
import io.harness.ssca.entities.EnforcementSummaryEntity;

import java.math.BigDecimal;

public class EnforcementSummaryTransformer {
  public static EnforcementSummaryEntity toEntity(EnforcementSummaryDTO dto) {
    return EnforcementSummaryEntity.builder()
        .accountId(dto.getAccountId())
        .orgIdentifier(dto.getOrgIdentifier())
        .projectIdentifier(dto.getProjectIdentifier())
        .pipelineExecutionId(dto.getPipelineExecutionId())
        .status(dto.getStatus())
        .allowListViolationCount(dto.getAllowListViolationCount().intValue())
        .enforcementId(dto.getEnforcementId())
        .denyListViolationCount(dto.getDenyListViolationCount().intValue())
        .orchestrationId(dto.getOrchestrationId())
        .createdAt(dto.getCreated().longValue())
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
        .accountId(entity.getAccountId())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .pipelineExecutionId(entity.getPipelineExecutionId())
        .created(new BigDecimal(entity.getCreatedAt()))
        .enforcementId(entity.getEnforcementId())
        .allowListViolationCount(new BigDecimal(entity.getAllowListViolationCount()))
        .enforcementId(entity.getEnforcementId())
        .denyListViolationCount(new BigDecimal(entity.getDenyListViolationCount()))
        .orchestrationId(entity.getOrchestrationId())
        .status(entity.getStatus())
        .artifact(new io.harness.spec.server.ssca.v1.model.Artifact()
                      .id(entity.getArtifact().getArtifactId())
                      .name(entity.getArtifact().getName())
                      .tag(entity.getArtifact().getTag())
                      .type(entity.getArtifact().getType())
                      .registryUrl(entity.getArtifact().getUrl()));
  }
}
