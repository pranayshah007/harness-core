/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.execution.strategy.NodeExecutionStrategy;
import io.harness.engine.pms.execution.strategy.NodeExecutionStrategyFactory;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.PmsNodeExecution;
import io.harness.execution.PmsNodeExecutionMetadata;
import io.harness.plan.Node;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.contracts.execution.events.InitiateMode;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.contracts.resume.ResponseDataProto;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Please do not use this class outside of orchestration module. All the interactions with engine must be done via
 * {@link OrchestrationService}. This is for the internal workings of the engine
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class OrchestrationEngine {
  @Inject private PlanService planService;
  @Inject private NodeExecutionStrategyFactory strategyFactory;

  public <T extends PmsNodeExecution> T runNode(
      @NonNull Ambiance ambiance, @NonNull Node node, PmsNodeExecutionMetadata metadata) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(node.getNodeType());
    return (T) strategy.runNode(ambiance, node, metadata);
  }

  public <T extends PmsNodeExecution> T initiateNode(@NonNull Ambiance ambiance, @NonNull String nodeId,
      @NonNull String runtimeId, PmsNodeExecutionMetadata metadata) {
    Node node = planService.fetchNode(ambiance.getPlanId(), nodeId);
    Ambiance clonedAmbiance = AmbianceUtils.cloneForChild(ambiance, PmsLevelUtils.buildLevelFromNode(runtimeId, node));
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(node.getNodeType());
    return (T) strategy.runNode(clonedAmbiance, node, metadata);
  }

  public <T extends PmsNodeExecution> T initiateNode(@NonNull Ambiance ambiance, @NonNull String nodeId,
      @NonNull String runtimeId, PmsNodeExecutionMetadata metadata, StrategyMetadata strategyMetadata,
      InitiateMode initiateMode) {
    Node node = planService.fetchNode(ambiance.getPlanId(), nodeId);
    Ambiance clonedAmbiance = AmbianceUtils.cloneForChild(ambiance,
        PmsLevelUtils.buildLevelFromNode(
            runtimeId, node, strategyMetadata, AmbianceUtils.shouldUseMatrixFieldName(ambiance)));
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(node.getNodeType());
    return (T) strategy.runNode(clonedAmbiance, node, metadata, initiateMode);
  }

  public <T extends PmsNodeExecution> T runNextNode(
      @NonNull Ambiance ambiance, @NonNull Node node, T previousExecution, PmsNodeExecutionMetadata metadata) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(node.getNodeType());
    return (T) strategy.runNextNode(ambiance, node, previousExecution, metadata);
  }

  public void startNodeExecution(Ambiance ambiance) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.startExecution(ambiance);
  }

  public void processFacilitatorResponse(Ambiance ambiance, FacilitatorResponseProto facilitatorResponse) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.processFacilitationResponse(ambiance, facilitatorResponse);
  }

  public void processStepResponse(@NonNull Ambiance ambiance, @NonNull StepResponseProto stepResponse) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.processStepResponse(ambiance, stepResponse);
  }

  public void resumeNodeExecution(Ambiance ambiance, Map<String, ResponseDataProto> response, boolean asyncError) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.resumeNodeExecution(ambiance, response, asyncError);
  }

  public void processAdviserResponse(Ambiance ambiance, AdviserResponse adviserResponse) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.processAdviserResponse(ambiance, adviserResponse);
  }

  public void handleError(Ambiance ambiance, Exception exception) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.handleError(ambiance, exception);
  }

  public void concludeNodeExecution(
      Ambiance ambiance, Status toStatus, Status fromStatus, EnumSet<Status> overrideStatusSet) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.concludeExecution(ambiance, toStatus, fromStatus, overrideStatusSet);
  }

  public void endNodeExecution(Ambiance ambiance) {
    NodeExecutionStrategy strategy = strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(ambiance));
    strategy.endNodeExecution(ambiance);
  }

  public void handleSdkResponseEvent(SdkResponseEventProto event) {
    NodeExecutionStrategy strategy =
        strategyFactory.obtainStrategy(OrchestrationUtils.currentNodeType(event.getAmbiance()));
    strategy.handleSdkResponseEvent(event);
  }
}
