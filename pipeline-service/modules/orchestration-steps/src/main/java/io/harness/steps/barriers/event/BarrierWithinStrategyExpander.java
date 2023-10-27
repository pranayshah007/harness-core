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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/* This class is responsible for "expanding" barrier positions (`BarrierPosition`) related to barrier steps within
     strategy loops ("Matrix", "Parallelism" and "Repeat" blocks).

     Initially, barrier positions that are within strategy loops are stored as dummy positions in
     `BarrierExecutionInstance` (`isDummyPosition` = true). Whenever a strategy execution kicks in, this class is
     called. Here we make 2 updates to BarrierExecutionInstances which are related to the strategy:

      1 - We "expand" the dummy barrier positions related to this strategy. This means we create one new barrier
          position for each of the children this strategy is spawning. For example, if the strategy is spawning 3
          children, we create 3 new barrier positions, one for each of the barrier steps being spawn in the execution
          graph.

      2 - Update the `strategyConcurrencyMap` field with the number of iterations of this strategy. This is necessary
          because we can only delete a dummy barrier position after it is expanded for all the strategies encapsulating
          it. We use this number to calculate how many expanded barrier positions should be in the
          BarrierExecutionInstance. If the number doesn't match, we keep the dummy position until all expanded
          positions are there.
  */
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierWithinStrategyExpander implements BarrierExpandObserver {
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
      try (AcquiredLock<?> ignore = persistentLocker.waitToAcquireLock(
               BarrierService.BARRIER_UPDATE_LOCK + planExecutionId, Duration.ofSeconds(20), Duration.ofSeconds(60))) {
        /* This lock is necessary to avoid race conditions upon updating a BarrierExecutionInstance in the DB.
           For example: BarrierStep1 is inside strategy1 and BarrierStep2 is inside strategy2. Both barrier steps
           referencing the same barrier ID. If strategy1 and strategy2 are being spawn at the same time, we will have
           race conditions when expanding the BarrierPositions in this class. */
        List<BarrierExecutionInstance> barrierExecutionInstances =
            barrierService.findManyByPlanExecutionIdAndStrategySetupId(planExecutionId, strategySetupId);
        for (BarrierExecutionInstance barrierExecutionInstance : barrierExecutionInstances) {
          /* Expand Barrier steps that are within this strategy. i.e.: Create one BarrierPosition for each BarrierStep
             which is child of this strategy. */
          List<BarrierPositionInfo.BarrierPosition> expandedPositionList =
              expandPositionInfoList(barrierExecutionInstance, barrierExpandRequest);
          if (isNotEmpty(expandedPositionList)) {
            /* Update the BarrierExecutionInstance.positionInfo list in DB.
               Also update the strategyConcurrencyMap with the number of strategy iterations relevant to
               barrier expansion. */
            barrierService.updateBarrierPositionInfoListAndStrategyConcurrency(barrierExecutionInstance.getIdentifier(),
                planExecutionId, expandedPositionList, strategySetupId,
                getNumberOfStrategyIterations(barrierExpandRequest));
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
      BarrierExecutionInstance barrierExecutionInstance, BarrierExpandRequest barrierExpandRequest) {
    List<BarrierPositionInfo.BarrierPosition> positionList =
        barrierExecutionInstance.getPositionInfo().getBarrierPositionList();
    String strategySetupId = barrierExpandRequest.getStrategySetupId();
    String stageExecutionId = barrierExpandRequest.getStageExecutionId();
    int numberOfIterations = getNumberOfStrategyIterations(barrierExpandRequest);
    List<String> childrenSetupIds = barrierExpandRequest.getChildrenSetupIds();
    List<String> childrenRuntimeIds = barrierExpandRequest.getChildrenRuntimeIds();
    List<BarrierPositionInfo.BarrierPosition> expandedPositionList = new ArrayList<>();
    for (BarrierPositionInfo.BarrierPosition position : positionList) {
      if (strategySetupId.equals(position.getStrategySetupId()) && Boolean.TRUE.equals(position.getIsDummyPosition())) {
        // If a dummy position's strategySetupId matches the current strategy, we need to expand this position entry.
        // i.e. We need to create one entry for each strategy's child that will be spawn.
        addExpandedPositionsToList(
            expandedPositionList, childrenSetupIds, childrenRuntimeIds, position, stageExecutionId, numberOfIterations);
        if (shouldKeepDummyPosition(position, expandedPositionList,
                barrierExecutionInstance.getSetupInfo().getStrategyConcurrencyMap(), strategySetupId,
                numberOfIterations, barrierExecutionInstance.getPlanExecutionId(),
                barrierExecutionInstance.getIdentifier())) {
          /* Here we calculate how many expanded barrier positions should be there for this dummy position.
             For example, if we have a barrier step wrapped inside 2 nested "repeat" blocks (both with "times" = 2),
             the expanded barrier positions number should be 2 * 2 = 4. If we still haven't expanded all of them, we
             need to keep the dummy position, so it can be expanded the next time this class gets called. */
          expandedPositionList.add(position);
        }
      } else {
        expandedPositionList.add(position);
      }
    }
    return expandedPositionList;
  }

  private int getNumberOfStrategyIterations(BarrierExpandRequest barrierExpandRequest) {
    /* Here we calculate the number of strategy iterations we should consider for barrier expansion.
       The `maxConcurrency` of the strategy needs to be taken into account: If we are spawning 3 barrier steps and the
       strategy has maxConcurrency = 2, we can only create 2 barrier positions, otherwise the first 2 barrier steps
       will go into a deadlock waiting forever for the third one to be spawn.
     */
    List<String> childrenSetupIds = barrierExpandRequest.getChildrenSetupIds();
    int maxConcurrency = barrierExpandRequest.getMaxConcurrency();
    return min(childrenSetupIds.size(), maxConcurrency);
  }

  private void addExpandedPositionsToList(List<BarrierPositionInfo.BarrierPosition> expandedPositionList,
      List<String> childrenSetupIds, List<String> childrenRuntimeIds, BarrierPositionInfo.BarrierPosition positionInfo,
      String stageExecutionId, int numberOfStrategyIterations) {
    /* Here we create one BarrierPosition for each child BarrierStep that will be spawn by the strategy.
       If the strategy is using maxConcurrency, we only create at most `maxConcurrency` new positions.
       If we don't impose this limitation, it would be impossible to proceed the strategy execution
       after the first `maxConcurrency` steps are spawn (Barrier would be waiting for still non-spawn steps to be
       achieved before proceeding). */
    for (int childIndex = 0; childIndex < numberOfStrategyIterations; childIndex++) {
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
      newBarrierPositionBuilder.isDummyPosition(false);
      expandedPositionList.add(newBarrierPositionBuilder.build());
    }
  }

  private boolean shouldKeepDummyPosition(BarrierPositionInfo.BarrierPosition dummyPosition,
      List<BarrierPositionInfo.BarrierPosition> expandedPositionList, Map<String, Integer> strategyConcurrencyMap,
      String currentStrategyId, int numberOfStrategyIterations, String planExecutionId, String barrierIdentifier) {
    // Checks if the number of expanded positions matches the expected number of iterations this barrier will be in.
    if (strategyConcurrencyMap == null) {
      // strategyConcurrencyMap is initially null,
      // since we don't store it the first time we create a BarrierExecutionInstance.
      strategyConcurrencyMap = new HashMap<>();
    }
    strategyConcurrencyMap.put(currentStrategyId, numberOfStrategyIterations);
    // Put the current strategy iterations number in the concurrency map.
    long expectedExpandedPositionsCount = 1;
    for (String strategyId : dummyPosition.getAllStrategySetupIds()) {
      Integer iterations = strategyConcurrencyMap.get(strategyId);
      if (iterations == null) {
        /* This is an unexpected case which should never happen. It means that we have nested strategies and one of the
           parent strategies has not been spawn before its child strategy.
           By default, we keep the dummy barrier in this case. */
        log.error(
            "null entry in strategyConcurrencyMap for strategyId: [{}], planExecutionId: [{}], currentStrategyId: [{}], barrierIdentifier: [{}]",
            strategyId, planExecutionId, currentStrategyId, barrierIdentifier);
        return true;
      }
      /* Accumulate number of total expected iterations by multiplying.
         If we have a barrier inside 3 nested strategies, all with 3 repetitions, the total amount of barrier
          positions we need after expansion is 3 * 3 * 3 = 27. */
      expectedExpandedPositionsCount *= iterations;
    }
    // Count how many positions we have already created for this dummy position.
    long expandedPositionsCount =
        expandedPositionList.stream()
            .filter(position -> dummyPosition.getStepSetupId().equals(position.getStepSetupId()))
            .count();
    /* If `expandedPositionsCount` is less than `expectedExpandedPositionsCount`, we still have to keep the dummy
       position to be used for further expansion. */
    return expandedPositionsCount < expectedExpandedPositionsCount;
  }
}