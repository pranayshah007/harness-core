/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.OrchestrationGraph;
import io.harness.dto.OrchestrationGraphDTO;
import io.harness.execution.NodeExecution;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import java.util.List;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public interface GraphGenerationService {
  OrchestrationGraph getCachedOrchestrationGraph(String planExecutionId);

  OrchestrationGraph getCachedOrchestrationGraphFromSecondary(String planExecutionId);

  void cacheOrchestrationGraph(OrchestrationGraph adjacencyListInternal);

  OrchestrationGraphDTO generateOrchestrationGraphV2(String planExecutionId);

  OrchestrationGraphDTO generatePartialOrchestrationGraphFromSetupNodeIdAndExecutionId(
      String startingSetupNodeId, String planExecutionId, String startingExecutionId);
  void sendUpdateEventIfAny(PipelineExecutionSummaryEntity executionSummaryEntity);
  OrchestrationGraph buildOrchestrationGraph(String planExecutionId);

  OrchestrationGraph buildOrchestrationGraphForNodeExecution(
      String planExecutionId, String nodeExecutionId, List<NodeExecution> nodeExecutions);

  boolean updateGraph(String planExecutionId);

  boolean updateGraphWithWaitLock(String planExecutionId);

  /**
   * Delete all GraphMetadata for given planExecutionIds
   * It will delete all related OrchestrationEventLog, cacheEntities
   * @param planExecutionIds
   */
  void deleteAllGraphMetadataForGivenExecutionIds(Set<String> planExecutionIds);
}
