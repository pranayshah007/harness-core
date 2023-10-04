/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.execution.NodeExecution.NodeExecutionKeys;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.expressions.NodeExecutionsCache;
import io.harness.engine.expressions.OrchestrationConstants;
import io.harness.engine.pms.data.OutcomeException;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.engine.pms.data.SweepingOutputException;
import io.harness.engine.utils.FunctorUtils;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.LateBindingMap;
import io.harness.graph.stepDetail.service.NodeExecutionInfoService;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.jexl3.JexlEngine;

/**
 * NodeExecutionMap resolves expressions for a single node execution.
 *
 * Suppose the current node has identifier `node1` and we see an expression `node1.child1`:
 * 1. We first try to find a child with identifier `child1`
 * 2. Then we try to find a property of node1's step parameters with name `child1`
 * 3. Then we try to find an outcome in node1's scope with name `child1`
 * 4. Then we try to find an sweeping output in node1's scope with name `child1`
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
@Value
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NodeExecutionMap extends LateBindingMap {
  transient NodeExecutionsCache nodeExecutionsCache;
  transient PmsOutcomeService pmsOutcomeService;
  transient PmsSweepingOutputService pmsSweepingOutputService;
  transient NodeExecutionInfoService nodeExecutionInfoService;
  transient Ambiance ambiance;
  transient NodeExecution nodeExecution;
  transient Set<NodeExecutionEntityType> entityTypes;
  transient Map<String, Object> children;
  transient JexlEngine engine;
  public static final String RETRY_COUNT = "retryCount";

  @Builder
  NodeExecutionMap(NodeExecutionsCache nodeExecutionsCache, PmsOutcomeService pmsOutcomeService,
      PmsSweepingOutputService pmsSweepingOutputService, Ambiance ambiance, NodeExecution nodeExecution,
      Set<NodeExecutionEntityType> entityTypes, Map<String, Object> children, JexlEngine engine,
      NodeExecutionInfoService nodeExecutionInfoService) {
    this.nodeExecutionsCache = nodeExecutionsCache;
    this.pmsOutcomeService = pmsOutcomeService;
    this.pmsSweepingOutputService = pmsSweepingOutputService;
    this.ambiance = ambiance;
    this.nodeExecution = nodeExecution;
    this.entityTypes = entityTypes == null ? NodeExecutionEntityType.allEntities() : entityTypes;
    if (children == null) {
      this.children = Collections.emptyMap();
    } else {
      this.children = new LateBindingMap();
      this.children.putAll(children);
    }
    this.engine = engine;
    this.nodeExecutionInfoService = nodeExecutionInfoService;
  }

  @Override
  public synchronized Object get(Object key) {
    if (!(key instanceof String)) {
      return null;
    }

    return FunctorUtils.fetchFirst(
        asList(this::fetchCurrentStatus, this::fetchExecutionUrl, this::fetchCurrentStatusIncludingChildOfStrategy,
            this::fetchChild, this::fetchNodeExecutionField, this::fetchStepParameters, this::fetchOutcomeOrOutput,
            this::fetchStrategyData),
        (String) key);
  }

  private Optional<Object> fetchChild(String key) {
    return children.containsKey(key) ? Optional.of(children.get(key)) : Optional.empty();
  }

  // This function calculates final status of the node TILL now.
  private Optional<Object> fetchCurrentStatus(String key) {
    if (!key.equals(OrchestrationConstants.CURRENT_STATUS)) {
      return Optional.empty();
    }
    if (nodeExecution == null) {
      return Optional.empty();
    }
    List<Status> childStatuses = nodeExecutionsCache.findAllTerminalChildrenStatusOnly(nodeExecution.getUuid(), false);
    return Optional.of(StatusUtils.calculateStatus(childStatuses, ambiance.getPlanExecutionId()).name());
  }

  // This function calculates executionUrl of the node TILL now.
  Optional<Object> fetchExecutionUrl(String key) {
    if (!key.equals(OrchestrationConstants.EXECUTION_URL)) {
      return Optional.empty();
    }
    // if Pipeline Node then skip as it would be resolved via PipelineExecutionFunctor
    if (nodeExecution == null || OrchestrationUtils.isPipelineNode(nodeExecution)) {
      return Optional.empty();
    }

    /*
     * Following cases exists -
     * 1. Step execution url
     * a) Inside Normal stage (inside a matrix step/step-group is same as normal)
     * b) Inside a matrix stage
     * c) Inside a child pipeline stage -> for this output child execution url, thus same as 1.a case
     *
     * 2. Stage Execution url
     * a) Normal stage
     * b) Matrix stage
     * c) a Pipeline Stage -> which is same as normal stage
     */
    String pipelineExecutionUrl = "<+pipeline." + OrchestrationConstants.EXECUTION_URL + ">";
    // Todo: Fetch Ambiance on demand
    Ambiance nodeAmbiance = nodeExecution.getAmbiance();
    boolean currentLevelInsideStage = AmbianceUtils.isCurrentLevelInsideStage(nodeAmbiance);
    // If any other node expression is called, then return pipeline execution url.
    if (!currentLevelInsideStage) {
      return Optional.of(pipelineExecutionUrl);
    }

    String stageSetupId = AmbianceUtils.getStageSetupIdAmbiance(nodeAmbiance);
    String stageExecutionUrl = "<+" + pipelineExecutionUrl + String.format("+'?stage=%s", stageSetupId);

    // Check for stage if under matrix
    boolean currentStrategyLevelAtStage = AmbianceUtils.isCurrentNodeUnderStageStrategy(nodeAmbiance);
    if (currentStrategyLevelAtStage) {
      String stageRuntimeId = nodeAmbiance.getStageExecutionId();
      stageExecutionUrl += String.format("\\&stageExecId=%s", stageRuntimeId);
    }

    boolean currentLevelAtStep = AmbianceUtils.isCurrentLevelAtStep(nodeAmbiance);
    if (currentLevelAtStep) {
      String stepId = AmbianceUtils.obtainCurrentRuntimeId(nodeAmbiance);
      String stepUrl = stageExecutionUrl + String.format("\\&step=%s'>", stepId);
      return Optional.of(stepUrl);
    }

    stageExecutionUrl += "'>";

    return Optional.of(stageExecutionUrl);
  }

  // This function calculates final status of the node TILL now.
  private Optional<Object> fetchCurrentStatusIncludingChildOfStrategy(String key) {
    if (!key.equals(OrchestrationConstants.LIVE_STATUS)) {
      return Optional.empty();
    }
    if (nodeExecution == null) {
      return Optional.empty();
    }
    List<Status> childStatuses = nodeExecutionsCache.findAllTerminalChildrenStatusOnly(nodeExecution.getUuid(), true);
    return Optional.of(StatusUtils.calculateStatus(childStatuses, ambiance.getPlanExecutionId()).name());
  }

  private Optional<Object> fetchNodeExecutionField(String key) {
    if (nodeExecution == null || !entityTypes.contains(NodeExecutionEntityType.NODE_EXECUTION_FIELDS)) {
      return Optional.empty();
    }

    if (NodeExecutionKeys.status.equals(key)) {
      return nodeExecution.getStatus() == null ? Optional.empty() : Optional.of(nodeExecution.getStatus().name());
    } else if (NodeExecutionKeys.startTs.equals(key)) {
      return Optional.ofNullable(nodeExecution.getStartTs());
    } else if (NodeExecutionKeys.endTs.equals(key)) {
      return Optional.ofNullable(nodeExecution.getEndTs());
    } else if (RETRY_COUNT.equals(key)) {
      return Optional.ofNullable(nodeExecution.getRetryIds() != null ? nodeExecution.getRetryIds().size() : 0);
    } else {
      return Optional.empty();
    }
  }

  private Optional<Object> fetchStepParameters(String key) {
    if (nodeExecution == null || !entityTypes.contains(NodeExecutionEntityType.STEP_PARAMETERS)) {
      return Optional.empty();
    } // ulWguxJGTZe0rN_cFSTclQ
    return ExpressionEvaluatorUtils.fetchField(
        engine, extractFinalStepParameters(nodeExecution, nodeExecutionsCache), key);
  }

  private Optional<Object> fetchStrategyData(String key) {
    if (nodeExecution == null || !entityTypes.contains(NodeExecutionEntityType.STRATEGY)) {
      return Optional.empty();
    }
    return ExpressionEvaluatorUtils.fetchField(engine, extractStrategyMetadata(nodeExecution), key);
  }

  private Optional<Object> fetchOutcomeOrOutput(String key) {
    if (nodeExecution == null
        || (!entityTypes.contains(NodeExecutionEntityType.OUTCOME)
            && !entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT))) {
      return Optional.empty();
    }

    List<String> levelRuntimeIdx = nodeExecution.getLevelRuntimeIdx();
    if (levelRuntimeIdx == null) {
      return Optional.empty();
    }

    Optional<Object> value = fetchOutcome(ambiance.getPlanExecutionId(), levelRuntimeIdx, key);
    if (!value.isPresent()) {
      value = fetchSweepingOutput(ambiance.getPlanExecutionId(), levelRuntimeIdx, key);
    }
    return value;
  }

  private Optional<Object> fetchOutcome(String planExecutionId, List<String> levelRuntimeIdIdx, String key) {
    if (!entityTypes.contains(NodeExecutionEntityType.OUTCOME)) {
      return Optional.empty();
    }

    try {
      return jsonToObject(pmsOutcomeService.resolveUsingLevelRuntimeIdx(
          planExecutionId, levelRuntimeIdIdx, RefObjectUtils.getOutcomeRefObject(key)));
    } catch (OutcomeException ignored) {
      return Optional.empty();
    }
  }

  private Optional<Object> fetchSweepingOutput(String planExecutionId, List<String> levelRuntimeIdIdx, String key) {
    if (!entityTypes.contains(NodeExecutionEntityType.SWEEPING_OUTPUT)) {
      return Optional.empty();
    }

    try {
      return jsonToObject(pmsSweepingOutputService.resolveUsingLevelRuntimeIdx(
          planExecutionId, levelRuntimeIdIdx, RefObjectUtils.getSweepingOutputRefObject(key)));
    } catch (SweepingOutputException ignored) {
      return Optional.empty();
    }
  }

  private static Map<String, Object> extractFinalStepParameters(
      NodeExecution nodeExecution, NodeExecutionsCache nodeExecutionsCache) {
    if (nodeExecution.getResolvedStepParameters() != null) {
      Map<String, Object> stepParameters =
          (Map<String, Object>) NodeExecutionUtils.resolveObject(nodeExecution.getResolvedStepParameters());
      if (stepParameters != null) {
        return stepParameters;
      }
    }
    Node node = nodeExecutionsCache.fetchNode(nodeExecution.getNodeId());
    return (Map<String, Object>) NodeExecutionUtils.resolveObject(node.getStepParameters());
  }

  private Map<String, Object> extractStrategyMetadata(NodeExecution nodeExecution) {
    if (nodeExecution.getAmbiance() != null) {
      Level currentLevel = AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance());
      if (currentLevel != null) {
        return nodeExecutionInfoService.fetchStrategyObjectMap(
            currentLevel, AmbianceUtils.shouldUseMatrixFieldName(nodeExecution.getAmbiance()));
      }
    }
    return new HashMap<>();
  }

  private static Optional<Object> jsonToObject(String json) {
    return Optional.ofNullable(NodeExecutionUtils.extractAndProcessObject(json));
  }
}
