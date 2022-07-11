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
import io.harness.execution.NodeExecution;
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

import java.util.List;
import java.util.Objects;
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
    StepType currentStepType = Objects.requireNonNull(AmbianceUtils.getCurrentStepType(nodeExecution.getAmbiance()));
    return currentStepType.getStepCategory() == StepCategory.STAGE;
  }

  public static boolean isPipelineNode(NodeExecution nodeExecution) {
    StepType currentStepType = Objects.requireNonNull(AmbianceUtils.getCurrentStepType(nodeExecution.getAmbiance()));
    return currentStepType.getStepCategory() == StepCategory.PIPELINE;
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
}
