/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers.event;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.distribution.barrier.Barrier;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.observers.BarrierInitializeRequest;
import io.harness.engine.observers.BarrierInitializeWithinStrategyObserver;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.observer.AsyncInformObserver;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.steps.barriers.service.visitor.BarrierVisitor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierWithinStrategyInitializer implements AsyncInformObserver, BarrierInitializeWithinStrategyObserver {
  @Inject @Named("OrchestrationVisualizationExecutorService") ExecutorService executorService;
  @Inject private BarrierService barrierService;
  @Inject private PlanExecutionMetadataService planExecutionMetadataService;

  @Override
  public void onInitializeRequest(BarrierInitializeRequest barrierInitializeRequest) {
    Ambiance ambiance = barrierInitializeRequest.getAmbiance();
    PlanExecutionMetadata planExecutionMetadata = planExecutionMetadataService.findByPlanExecutionId(ambiance.getPlanExecutionId()).get();
    String version = AmbianceUtils.getPipelineVersion(ambiance);
    String planExecutionId = ambiance.getPlanExecutionId();
    try {
      switch (version) {
        case PipelineVersion.V1:
          // TODO: Barrier support
          break;
        case PipelineVersion.V0:
          BarrierVisitor barriersInfo = barrierService.getBarrierInfo(planExecutionMetadata.getProcessedYaml());
          Map<String, BarrierSetupInfo> barrierIdentifierSetupInfoMap = new ArrayList<>(barriersInfo.getBarrierIdentifierMap().values())
                  .stream()
                  .collect(Collectors.toMap(BarrierSetupInfo::getIdentifier, Function.identity()));
          Map<String, List<BarrierPositionInfo.BarrierPosition>> barrierPositionInfoMap = barriersInfo.getBarrierPositionInfoMap();
          for (String barrierId : barrierPositionInfoMap.keySet()) {
            for (BarrierPositionInfo.BarrierPosition positionInfo : barrierPositionInfoMap.get(barrierId)) {
              positionInfo.setStageRuntimeId(ambiance.getStageExecutionId());
            }
          }
          List<BarrierExecutionInstance> barriers =
                  barrierPositionInfoMap.entrySet()
                          .stream()
                          .filter(entry -> !entry.getValue().isEmpty())
                          // Filter out barriers that are within a "strategy" node.
                          .filter(entry -> barrierInitializeRequest.getStrategySetupId().equals(barrierIdentifierSetupInfoMap.get(entry.getKey()).getStrategySetupId()))
                          .map(entry
                                  -> BarrierExecutionInstance.builder()
                                  .uuid(generateUuid())
                                  .setupInfo(barrierIdentifierSetupInfoMap.get(entry.getKey()))
                                  .positionInfo(BarrierPositionInfo.builder()
                                          .planExecutionId(planExecutionId)
                                          .barrierPositionList(entry.getValue())
                                          .build())
                                  .name(barrierIdentifierSetupInfoMap.get(entry.getKey()).getName())
                                  .barrierState(Barrier.State.STANDING)
                                  .identifier(entry.getKey())
                                  .planExecutionId(planExecutionId)
                                  .strategyExecutionId(barrierInitializeRequest.getStrategyExecutionId())
                                  .build())
                          .collect(Collectors.toList());

          barrierService.saveAll(barriers);
          break;
        default:
          throw new IllegalStateException("version not supported");
      }
    } catch (Exception e) {
      log.error("Barrier initialization failed for planExecutionId: [{}]", planExecutionId);
      throw e;
    }
  }

  @Override
  public ExecutorService getInformExecutorService() {
    return executorService;
  }
}
