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
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import java.util.Map;

// Todo: Rename to NodeExecutionInfoService
@OwnedBy(HarnessTeam.PIPELINE)
public interface PmsGraphStepDetailsService {
  void addStepDetail(String nodeExecutionId, String planExecutionId, PmsStepDetails stepDetails, String name);
  void addStepInputs(String nodeExecutionId, String planExecutionId, PmsStepParameters stepParameters);

  PmsStepParameters getStepInputs(String planExecutionId, String nodeExecutionId);

  NodeExecutionsInfo getNodeExecutionsInfo(String nodeExecutionId);

  Map<String, PmsStepDetails> getStepDetails(String planExecutionId, String nodeExecutionId);

  void copyStepDetailsForRetry(String planExecutionId, String originalNodeExecutionId, String newNodeExecutionId);

  void addConcurrentChildInformation(ConcurrentChildInstance concurrentChildInstance, String nodeExecutionId);

  ConcurrentChildInstance incrementCursor(String nodeExecutionId);
}
