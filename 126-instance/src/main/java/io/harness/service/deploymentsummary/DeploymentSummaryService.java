/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.deploymentsummary;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;

import java.util.Optional;

@OwnedBy(HarnessTeam.DX)
public interface DeploymentSummaryService {
  DeploymentSummaryDTO save(DeploymentSummaryDTO deploymentSummaryDTO);

  Optional<DeploymentSummaryDTO> getByDeploymentSummaryId(String deploymentSummaryId);

  Optional<DeploymentSummaryDTO> getNthDeploymentSummaryFromNow(
      int N, String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO);

  Optional<DeploymentSummaryDTO> getNthDeploymentSummaryFromNow(
      int N, String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO, Boolean isRollbackDeployment);

  Optional<DeploymentSummaryDTO> getLatestByInstanceKeyAndPipelineExecutionIdNot(
      String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO, String pipelineExecutionId);

  Optional<DeploymentSummaryDTO> getLatestByInstanceKey(
      String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO);
}
