/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers.service;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.distribution.barrier.Barrier.State;
import io.harness.plancreator.steps.barrier.BarrierStepNode;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierSetupInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
public interface BarrierService {
  String BARRIER_UPDATE_LOCK = "BARRIER_UPDATE_LOCK_";
  BarrierExecutionInstance save(BarrierExecutionInstance barrierExecutionInstance);
  List<BarrierExecutionInstance> saveAll(List<BarrierExecutionInstance> barrierExecutionInstances);
  BarrierExecutionInstance get(String barrierUuid);
  BarrierExecutionInstance update(BarrierExecutionInstance barrierExecutionInstance);
  BarrierExecutionInstance updateState(String uuid, State state);
  List<BarrierExecutionInstance> updatePosition(String planExecutionId,
      BarrierPositionInfo.BarrierPosition.BarrierPositionType positionType, String positionSetupId,
      String positionExecutionId, String stageExecutionId, String stepGroupExecutionId, boolean isNewBarrierUpdateFlow);
  BarrierExecutionInstance findByIdentifierAndPlanExecutionId(String identifier, String planExecutionId);
  BarrierExecutionInstance findByPlanNodeIdAndPlanExecutionId(String planNodeId, String planExecutionId);
  List<BarrierExecutionInstance> findByStageIdentifierAndPlanExecutionIdAnsStateIn(
      String stageIdentifier, String planExecutionId, Set<State> stateSet);
  List<BarrierSetupInfo> getBarrierSetupInfoList(String yaml);
  Map<String, List<BarrierPositionInfo.BarrierPosition>> getBarrierPositionInfoList(String yaml);

  /**
   * Deletes barrierInstances for given planExecutionIds
   * Uses - planExecutionId_barrierState_stagesIdentifier_idx
   * @param planExecutionIds
   */
  void deleteAllForGivenPlanExecutionId(Set<String> planExecutionIds);
  void upsert(BarrierExecutionInstance barrierExecutionInstance);
  void updateBarrierPositionInfoListAndStrategyConcurrency(String barrierIdentifier, String planExecutionId,
      List<BarrierPositionInfo.BarrierPosition> barrierPositions, String strategyId, int concurrency);
  boolean existsByPlanExecutionIdAndStrategySetupId(String planExecutionId, String strategySetupId);
  List<BarrierExecutionInstance> findManyByPlanExecutionIdAndStrategySetupId(
      String planExecutionId, String strategySetupId);
  void upsertBarrierExecutionInstance(BarrierStepNode field, String planExecutionId, String parentInfoStrategyNodeType,
      String stageId, String stepGroupId, String strategyId, List<String> allStrategyIds);
}
