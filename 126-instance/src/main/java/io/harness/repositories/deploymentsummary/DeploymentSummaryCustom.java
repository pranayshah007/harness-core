/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.deploymentsummary;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.entities.DeploymentSummary;

import java.util.Optional;

@OwnedBy(HarnessTeam.DX)
public interface DeploymentSummaryCustom {
  Optional<DeploymentSummary> fetchNthRecordFromNow(
      int N, String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO);

  Optional<DeploymentSummary> fetchLatestByInstanceKeyAndPipelineExecutionIdNot(
      String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO, String pipelineExecutionId);

  boolean delete(String accountId, String org, String project);
}
