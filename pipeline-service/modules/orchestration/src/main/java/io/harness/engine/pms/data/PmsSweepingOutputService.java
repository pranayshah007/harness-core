/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.data;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.ExecutionSweepingOutputInstance;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.refobjects.RefObject;

import java.util.Date;
import java.util.List;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public interface PmsSweepingOutputService extends Resolver {
  RawOptionalSweepingOutput resolveOptional(Ambiance ambiance, RefObject refObject);

  List<RawOptionalSweepingOutput> findOutputsUsingNodeId(Ambiance ambiance, String name, List<String> nodeIds);

  List<RawOptionalSweepingOutput> findOutputsUsingExecutionIds(Ambiance ambiance, String name, List<String> nodeIds);

  List<ExecutionSweepingOutputInstance> fetchOutcomeInstanceByRuntimeId(String runtimeId);

  List<String> cloneForRetryExecution(Ambiance ambiance, String originalNodeExecutionUuid);

  /**
   * Delete all sweeping output instances for given planExecutionIds
   * Uses - unique_levelRuntimeIdUniqueIdx2
   * @param planExecutionIds
   */
  void deleteAllSweepingOutputInstances(Set<String> planExecutionIds);

  /**
   * Updates all sweeping output instances for given planExecutionId
   * Uses - unique_levelRuntimeIdUniqueIdx2
   * @param planExecutionId
   */
  void updateTTL(String planExecutionId, Date ttlDate);
}
