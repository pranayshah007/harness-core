/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.repositories.EnforcementSummaryRepo;
import io.harness.spec.server.ssca.v1.model.EnforcementSummaryDTO;
import io.harness.ssca.beans.Artifact;
import io.harness.ssca.enforcement.constants.EnforcementStatus;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity.EnforcementSummaryEntityKeys;
import io.harness.ssca.transformers.EnforcementSummaryTransformer;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Criteria;

public class EnforcementSummaryServiceImpl implements EnforcementSummaryService {
  @Inject EnforcementSummaryRepo enforcementSummaryRepo;
  @Override
  public String persistEnforcementSummary(String enforcementId, List<EnforcementResultEntity> denyListResult,
      List<EnforcementResultEntity> allowListResult, ArtifactEntity artifact, String pipelineExecutionId) {
    String status = EnforcementStatus.ENFORCEMENT_STATUS_PASS.getValue();
    if (!denyListResult.isEmpty() || !allowListResult.isEmpty()) {
      status = EnforcementStatus.ENFORCEMENT_STATUS_FAIL.getValue();
    }
    EnforcementSummaryEntity summary = EnforcementSummaryEntity.builder()
                                           .accountId(artifact.getAccountId())
                                           .orgIdentifier(artifact.getOrgId())
                                           .projectIdentifier(artifact.getProjectId())
                                           .pipelineExecutionId(pipelineExecutionId)
                                           .enforcementId(enforcementId)
                                           .artifact(Artifact.builder()
                                                         .artifactId(artifact.getArtifactId())
                                                         .name(artifact.getName())
                                                         .tag(artifact.getTag())
                                                         .type(artifact.getType())
                                                         .url(artifact.getUrl())
                                                         .build())
                                           .orchestrationId(artifact.getOrchestrationId())
                                           .denyListViolationCount(denyListResult.size())
                                           .allowListViolationCount(allowListResult.size())
                                           .status(status)
                                           .createdAt(Instant.now().toEpochMilli())
                                           .build();

    EnforcementSummaryEntity savedEntity = enforcementSummaryRepo.save(summary);
    return savedEntity.getStatus();
  }

  @Override
  public Optional<EnforcementSummaryEntity> getEnforcementSummary(
      String accountId, String orgIdentifier, String projectIdentifier, String enforcementId) {
    return enforcementSummaryRepo.findByEnforcementId(enforcementId);
  }

  @Override
  public EnforcementSummaryEntity getEnforcementSummaryByPipelineExecution(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineExecutionId) {
    Criteria criteria = Criteria.where(EnforcementSummaryEntityKeys.pipelineExecutionId)
                            .is(pipelineExecutionId)
                            .and(EnforcementSummaryEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(EnforcementSummaryEntityKeys.projectIdentifier)
                            .is(projectIdentifier);
    return enforcementSummaryRepo.findOne(accountId, criteria);
  }

  @Override
  public void create(EnforcementSummaryDTO enforcementSummaryDTO) {
    enforcementSummaryRepo.save(EnforcementSummaryTransformer.toEntity(enforcementSummaryDTO));
  }
}
