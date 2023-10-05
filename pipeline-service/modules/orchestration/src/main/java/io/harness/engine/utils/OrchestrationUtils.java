/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.utils;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.pms.timeout.SdkTimeoutTrackerParameters;
import io.harness.serializer.KryoSerializer;
import io.harness.timeout.TimeoutParameters;
import io.harness.timeout.contracts.TimeoutObtainment;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class OrchestrationUtils {
  public Status calculateStatus(List<NodeExecution> nodeExecutions, String planExecutionId) {
    List<Status> statuses = nodeExecutions.stream().map(NodeExecution::getStatus).collect(Collectors.toList());
    return StatusUtils.calculateStatus(statuses, planExecutionId);
  }

  public Status calculateStatusForPlanExecution(List<Status> statuses, String planExecutionId) {
    Status calculatedStatus = StatusUtils.calculateStatus(statuses, planExecutionId);
    if (Status.QUEUED == calculatedStatus) {
      return Status.RUNNING;
    }
    return calculatedStatus;
  }

  public static boolean isStageNode(NodeExecution nodeExecution) {
    if (nodeExecution == null) {
      return false;
    }
    StepType currentStepType = AmbianceUtils.getCurrentStepType(nodeExecution.getAmbiance());
    return currentStepType != null && currentStepType.getStepCategory() == StepCategory.STAGE;
  }

  public static boolean isStageOrParallelStageNode(NodeExecution nodeExecution) {
    StepType currentStepType = AmbianceUtils.getCurrentStepType(nodeExecution.getAmbiance());
    if (currentStepType != null && currentStepType.getStepCategory() == StepCategory.STAGE) {
      return true;
    }
    StepType parentStepType = AmbianceUtils.getParentStepType(nodeExecution.getAmbiance());
    if (currentStepType == null || parentStepType == null) {
      return false;
    }
    return currentStepType.getStepCategory() == StepCategory.FORK
        && parentStepType.getStepCategory() == StepCategory.STAGES;
  }

  public static boolean isPipelineNode(NodeExecution nodeExecution) {
    if (nodeExecution == null) {
      return false;
    }
    StepType currentStepType = AmbianceUtils.getCurrentStepType(nodeExecution.getAmbiance());
    return currentStepType != null && currentStepType.getStepCategory() == StepCategory.PIPELINE;
  }

  public static NodeType currentNodeType(Ambiance ambiance) {
    Level level = AmbianceUtils.obtainCurrentLevel(ambiance);
    if (level == null) {
      return NodeType.PLAN;
    }
    // TODO: Remove this in next release
    if (isEmpty(level.getNodeType())) {
      return NodeType.PLAN_NODE;
    }
    return NodeType.valueOf(level.getNodeType());
  }

  public static String getOriginalNodeExecutionId(Node node) {
    if (node.getNodeType() == NodeType.IDENTITY_PLAN_NODE) {
      return ((IdentityPlanNode) node).getOriginalNodeExecutionId();
    }
    return null;
  }

  public TimeoutParameters buildTimeoutParameters(
      KryoSerializer kryoSerializer, EngineExpressionEvaluator evaluator, TimeoutObtainment timeoutObtainment) {
    // TODO (prashant) : Change this this should not be kryo we should trat then exactly like step parameters. Should be
    // json string bytes Evaluate timeout expressions and convert sdk timeout parameters to timeout engine specific
    // parameters.
    SdkTimeoutTrackerParameters sdkTimeoutTrackerParameters =
        (SdkTimeoutTrackerParameters) kryoSerializer.asObject(timeoutObtainment.getParameters().toByteArray());

    if (sdkTimeoutTrackerParameters == null) {
      throw new InvalidRequestException(
          String.format("sdkTimeoutTrackerParameters cannot be set to null. Please recheck for %s",
              timeoutObtainment.getParameters()));
    } else {
      sdkTimeoutTrackerParameters = resolve(evaluator, sdkTimeoutTrackerParameters);
      return sdkTimeoutTrackerParameters.prepareTimeoutParameters();
    }
  }

  private <T> T resolve(EngineExpressionEvaluator evaluator, T o) {
    if (o == null) {
      return null;
    }

    Class<?> cls = o.getClass();
    Map<String, Object> m = NodeExecutionUtils.extractObject(RecastOrchestrationUtils.toJson(o));
    String json = RecastOrchestrationUtils.toJson(evaluator.resolve(m, false));
    return (T) RecastOrchestrationUtils.fromJson(json, cls);
  }
}
