/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.barriers.service;

import static io.harness.distribution.barrier.Barrier.State.STANDING;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.pms.barriers.beans.BarrierExecutionInfo;
import io.harness.pms.barriers.beans.BarrierExecutionInfo.BarrierExecutionInfoBuilder;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.repositories.TimeoutInstanceRepository;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.service.BarrierService;
import io.harness.timeout.TimeoutInstance;

import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PMSBarrierServiceImpl implements PMSBarrierService {
  private final NodeExecutionService nodeExecutionService;
  private final BarrierService barrierService;
  private final TimeoutInstanceRepository timeoutInstanceRepository;

  @Override
  public List<BarrierSetupInfo> getBarrierSetupInfoList(String yaml) {
    return barrierService.getBarrierSetupInfoList(yaml);
  }

  @Override
  public List<BarrierExecutionInfo> getBarrierExecutionInfoList(String stageSetupId, String planExecutionId) {
    NodeExecution stageNodeExecution = nodeExecutionService.getByPlanNodeUuid(stageSetupId, planExecutionId);
    Level currentLevel = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(stageNodeExecution.getAmbiance()));
    List<BarrierExecutionInstance> barrierInstances = barrierService.findByStageIdentifierAndPlanExecutionIdAnsStateIn(
        currentLevel.getIdentifier(), planExecutionId, Sets.newHashSet(STANDING));

    return barrierInstances.stream()
        .map(instance
            -> instance.getPositionInfo()
                   .getBarrierPositionList()
                   .stream()
                   .filter(position -> EmptyPredicate.isNotEmpty(position.getStepRuntimeId()))
                   .map(position -> {
                     BarrierExecutionInfoBuilder builder = BarrierExecutionInfo.builder()
                                                               .name(instance.getName())
                                                               .identifier(instance.getIdentifier())
                                                               .stages(instance.getSetupInfo().getStages());

                     try {
                       NodeExecution barrierNode = nodeExecutionService.get(position.getStepRuntimeId());
                       builder.started(true)
                           .startedAt(barrierNode.getStartTs())
                           .timeoutIn(obtainTimeoutIn(barrierNode));
                     } catch (InvalidRequestException ignore) {
                       builder.started(false);
                     }
                     return builder.build();
                   })
                   .collect(Collectors.toList()))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  @Override
  public BarrierExecutionInfo getBarrierExecutionInfo(String barrierSetupId, String planExecutionId) {
    BarrierExecutionInstance barrierInstance =
        barrierService.findByPlanNodeIdAndPlanExecutionId(barrierSetupId, planExecutionId);
    NodeExecution barrierNode = nodeExecutionService.getByPlanNodeUuid(barrierSetupId, planExecutionId);
    return BarrierExecutionInfo.builder()
        .name(barrierInstance.getName())
        .identifier(barrierInstance.getIdentifier())
        .stages(barrierInstance.getSetupInfo().getStages())
        .timeoutIn(obtainTimeoutIn(barrierNode))
        .build();
  }

  private long obtainTimeoutIn(NodeExecution barrierNode) {
    Iterable<TimeoutInstance> timeoutInstances =
        timeoutInstanceRepository.findAllById(barrierNode.getTimeoutInstanceIds());
    long expiryTime = Streams.stream(timeoutInstances)
                          .mapToLong(timeoutInstance -> timeoutInstance.getTracker().getExpiryTime())
                          .max()
                          .orElse(-1L);
    return expiryTime < 0 ? expiryTime : expiryTime - barrierNode.getStartTs();
  }
}
