/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.graph.stepDetail.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.concurrency.ConcurrentChildInstance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import java.util.Date;
import java.util.Map;
import java.util.Set;

// Todo: Rename to NodeExecutionInfoService
@OwnedBy(HarnessTeam.PIPELINE)
public interface PmsGraphStepDetailsService {
  void addStepDetail(String nodeExecutionId, String planExecutionId, PmsStepDetails stepDetails, String name);
  void saveNodeExecutionInfo(String nodeExecutionId, String planExecutionId, PmsStepParameters stepParameters);

  PmsStepParameters getStepInputs(String planExecutionId, String nodeExecutionId);

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
}
