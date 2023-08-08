/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.repositories.EnforcementSummaryRepo;
import io.harness.ssca.beans.Artifact;
import io.harness.ssca.enforcement.constants.EnforcementStatus;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity;

import com.google.inject.Inject;
import java.util.List;

public class EnforcementSummaryServiceImpl implements EnforcementSummaryService {
  @Inject EnforcementSummaryRepo enforcementSummaryRepo;
  @Override
  public String persistEnforcementSummary(String enforcementId, List<EnforcementResultEntity> denyListResult,
      List<EnforcementResultEntity> allowListResult, ArtifactEntity artifact) {
    String status = EnforcementStatus.ENFORCEMENT_STATUS_PASS.getValue();
    if (!denyListResult.isEmpty() || !allowListResult.isEmpty()) {
      status = EnforcementStatus.ENFORCEMENT_STATUS_FAIL.getValue();
    }
    EnforcementSummaryEntity summary = EnforcementSummaryEntity.builder()
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
                                           .build();

    EnforcementSummaryEntity savedEntity = enforcementSummaryRepo.save(summary);
    return savedEntity.getStatus();
  }
}
