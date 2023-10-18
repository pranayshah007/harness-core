/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers.event;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.Math.min;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.observers.BarrierExpandObserver;
import io.harness.engine.observers.BarrierExpandRequest;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierPositionInfo.BarrierPosition.BarrierPositionType;
import io.harness.steps.barriers.service.BarrierService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierWithinStrategyExpander implements BarrierExpandObserver {
  private static final String BARRIER_WITHIN_STRATEGY_EXPANDER_LOCK = "BARRIER_WITHIN_STRATEGY_EXPANDER_LOCK_";

  @Inject private BarrierService barrierService;
  @Inject private PersistentLocker persistentLocker;

  @Override
  public void onInitializeRequest(BarrierExpandRequest barrierExpandRequest) {
    String planExecutionId = barrierExpandRequest.getPlanExecutionId();
    String strategySetupId = barrierExpandRequest.getStrategySetupId();
    try {
      if (!barrierService.existsByPlanExecutionIdAndStrategySetupId(planExecutionId, strategySetupId)) {
        // Nothing to do if there are no barrierExecutionInstances with this planId and strategySetupId.
        return;
      }
      try (AcquiredLock<?> ignore =
               persistentLocker.waitToAcquireLock(BARRIER_WITHIN_STRATEGY_EXPANDER_LOCK + planExecutionId,
                   Duration.ofSeconds(20), Duration.ofSeconds(60))) {
        /* This lock is necessary to avoid race conditions upon updating a BarrierExecutionInstance in the DB.
           For example: BarrierStep1 is inside strategy1 and BarrierStep2 is inside strategy2. Both barrier steps
           referencing the same barrier ID. If strategy1 and strategy2 are being spawn at the same time, we will have
           race conditions when expanding the BarrierPositions in this class. */
        List<BarrierExecutionInstance> barrierExecutionInstances =
            barrierService.findManyByPlanExecutionIdAndStrategySetupId(planExecutionId, strategySetupId);
        for (BarrierExecutionInstance barrierExecutionInstance : barrierExecutionInstances) {
          List<BarrierPositionInfo.BarrierPosition> positionList =
              barrierExecutionInstance.getPositionInfo().getBarrierPositionList();
          /* Expand Barrier steps that are within this strategy. i.e.: Create one BarrierPosition for each BarrierStep
             which is child of this strategy. */
          List<BarrierPositionInfo.BarrierPosition> expandedPositionList =
              expandPositionInfoList(positionList, barrierExpandRequest);
          if (isNotEmpty(expandedPositionList)) {
            // Update the BarrierExecutionInstance.positionInfo list in DB.
            barrierService.updateBarrierPositionInfoList(
                barrierExecutionInstance.getIdentifier(), planExecutionId, expandedPositionList);
          }
        }
      }
    } catch (Exception e) {
      log.error("Barrier initialization within strategy failed for planExecutionId: [{}], strategySetupId: [{}]",
          planExecutionId, strategySetupId);
      throw e;
    }
  }

  private List<BarrierPositionInfo.BarrierPosition> expandPositionInfoList(
      List<BarrierPositionInfo.BarrierPosition> positionList, BarrierExpandRequest barrierExpandRequest) {
    String strategySetupId = barrierExpandRequest.getStrategySetupId();
    String stageExecutionId = barrierExpandRequest.getStageExecutionId();
    int maxConcurrency = barrierExpandRequest.getMaxConcurrency();
    List<String> childrenSetupIds = barrierExpandRequest.getChildrenSetupIds();
    List<String> childrenRuntimeIds = barrierExpandRequest.getChildrenRuntimeIds();
    List<BarrierPositionInfo.BarrierPosition> expandedPositionList = new ArrayList<>();
    for (BarrierPositionInfo.BarrierPosition position : positionList) {
      if (!strategySetupId.equals(position.getStrategySetupId())) {
        // If strategySetupId doesn't match the current strategy, just keep the same positionInfo.
        expandedPositionList.add(position);
      } else {
        // If strategySetupId matches the current strategy, we need to expand this position entry.
        // i.e. We need to create one entry for each strategy's child that will be spawn.
        addExpandedPositionsToList(
            expandedPositionList, childrenSetupIds, childrenRuntimeIds, position, stageExecutionId, maxConcurrency);
      }
    }
    return expandedPositionList;
  }

  private void addExpandedPositionsToList(List<BarrierPositionInfo.BarrierPosition> expandedPositionList,
      List<String> childrenSetupIds, List<String> childrenRuntimeIds, BarrierPositionInfo.BarrierPosition positionInfo,
      String stageExecutionId, int maxConcurrency) {
    /* Here we create one BarrierPosition for each child BarrierStep that will be spawn by the strategy.
       If the strategy is using maxConcurrency, we only create at most `maxConcurrency` new positions.
       If we don't impose this limitation, it would be impossible to proceed the strategy execution
       after the first `maxConcurrency` steps are spawn (Barrier would be waiting for still non-spawn steps to be
       achieved before proceeding). */
    for (int childIndex = 0; childIndex < min(childrenSetupIds.size(), maxConcurrency); childIndex++) {
      BarrierPositionInfo.BarrierPosition.BarrierPositionBuilder newBarrierPositionBuilder =
          positionInfo.toBuilder().stageRuntimeId(stageExecutionId);
      if (BarrierPositionType.STAGE.equals(positionInfo.getStrategyNodeType())) {
        newBarrierPositionBuilder.stageSetupId(childrenSetupIds.get(childIndex));
        newBarrierPositionBuilder.stageRuntimeId(childrenRuntimeIds.get(childIndex));
      } else if (BarrierPositionType.STEP_GROUP.equals(positionInfo.getStrategyNodeType())) {
        newBarrierPositionBuilder.stepGroupSetupId(childrenSetupIds.get(childIndex));
        newBarrierPositionBuilder.stepGroupRuntimeId(childrenRuntimeIds.get(childIndex));
      } else {
        throw new InvalidRequestException(
            String.format("Unsupported strategy node type: %s", positionInfo.getStrategyNodeType()));
      }
      expandedPositionList.add(newBarrierPositionBuilder.build());
    }
  }
}