/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import io.harness.spec.server.ssca.v1.model.EnforcementSummaryDTO;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.EnforcementResultEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity;

import java.util.List;
import java.util.Optional;

public interface EnforcementSummaryService {
  String persistEnforcementSummary(String enforcementId, List<EnforcementResultEntity> denyListResult,
      List<EnforcementResultEntity> allowListResult, ArtifactEntity artifact, String pipelineExecutionId);

  Optional<EnforcementSummaryEntity> getEnforcementSummary(
      String accountId, String orgIdentifier, String projectIdentifier, String enforcementId);

  EnforcementSummaryEntity getEnforcementSummaryByPipelineExecution(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineExecutionId);

  void create(EnforcementSummaryDTO enforcementSummaryDTO);
}
