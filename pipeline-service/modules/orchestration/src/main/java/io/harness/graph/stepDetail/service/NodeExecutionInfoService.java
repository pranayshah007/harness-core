/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.graph.stepDetail.service;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
public interface NodeExecutionInfoService {
  void addStepDetail(String nodeExecutionId, String planExecutionId, PmsStepDetails stepDetails, String name);

  // TODO: Make this better this should be called from no where else
  void saveNodeExecutionInfo(String nodeExecutionId, String planExecutionId, StrategyMetadata metadata);

  void addStepInputs(String nodeExecutionId, PmsStepParameters resolvedInputs, String planExecutionId);

  PmsStepParameters getStepInputs(String planExecutionId, String nodeExecutionId);

  PmsStepParameters getStepInputsRecasterPruned(String planExecutionId, String nodeExecutionId);

  NodeExecutionsInfo getNodeExecutionsInfo(String nodeExecutionId);

  Map<String, PmsStepDetails> getStepDetails(String planExecutionId, String nodeExecutionId);

  void copyStepDetailsForRetry(String planExecutionId, String originalNodeExecutionId, String newNodeExecutionId);

  void addConcurrentChildInformation(ConcurrentChildInstance concurrentChildInstance, String nodeExecutionId);

  ConcurrentChildInstance incrementCursor(String nodeExecutionId, Status status);

  ConcurrentChildInstance fetchConcurrentChildInstance(String nodeExecutionId);

  /**
   * Delete all nodeExecutionInfo for given nodeExecutionIds
   * Uses - nodeExecutionId_unique_idx index
   * @param nodeExecutionIds
   */
  void deleteNodeExecutionInfoForGivenIds(Set<String> nodeExecutionIds);

  /**
   * Updates TTL for all nodeExecutionInfo for given planExecutionId
   * Uses - nodeExecutionId_unique_idx index
   * @param planExecutionId
   */
  void updateTTLForNodesForGivenPlanExecutionId(String planExecutionId, Date ttlDate);

  Map<String, Object> fetchStrategyObjectMap(Level level, boolean useMatrixFieldName);

  Map<String, Object> fetchStrategyObjectMap(List<Level> levelsWithStrategyMetadata, boolean useMatrixFieldName);

  Map<String, StrategyMetadata> fetchStrategyMetadata(List<String> nodeExecutionIds);

  StrategyMetadata getStrategyMetadata(Ambiance ambiance);
  StrategyMetadata getStrategyMetadata(Level level);
}
