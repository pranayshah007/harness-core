/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.sdk.core.steps.executables.ChildExecutable;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.util.CloseableIterator;

/**
 * This step is used during retry-failed-pipeline for running any step/stage that is inside the strategy.
 * In retry-failed-pipeline, we normally convert the PlanNode into IdentityNode.
 * But in case of strategy, we have multiple executions for the same node. So some combinations might have failed in
 * previous execution and some might have passed. But we can not convert the planNode into IdentityNode during plan
 * creation because we might need the original planNode during the execution,(To run the failed combination) But we also
 * want that successful matrix combinations should not run. And the above step comes into picture at this moment. This
 * step will check the status from previous execution, if its positive status then it will create an IdentityNode for
 * the provided planNode. And return the identityNode ids as child/children. And if status was negative, then simply
 * return the planNode id as child/children ids.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class IdentityStrategyInternalStep
    implements ChildExecutable<IdentityStepParameters>, ChildrenExecutable<IdentityStepParameters> {
  @Inject PlanService planService;
  @Inject private PmsOutcomeService pmsOutcomeService;
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.IDENTITY_STRATEGY_INTERNAL)
                                               .setStepCategory(StepCategory.STRATEGY)
                                               .build();

  @Inject private NodeExecutionService nodeExecutionService;
  @Override
  public ChildExecutableResponse obtainChild(
      Ambiance ambiance, IdentityStepParameters identityParams, StepInputPackage inputPackage) {
    NodeExecution originalNodeExecution = nodeExecutionService.getWithFieldsIncluded(
        identityParams.getOriginalNodeExecutionId(), NodeProjectionUtils.fieldsForIdentityStrategyStep);
    NodeExecution childNodeExecution = null;
    try (CloseableIterator<NodeExecution> iterator =
             // Use original planExecutionId that belongs to the originalNodeExecutionId and not current
             // planExecutionId(ambiance.getPlanExecutionId)
        nodeExecutionService.fetchChildrenNodeExecutionsIterator(
            originalNodeExecution.getAmbiance().getPlanExecutionId(), identityParams.getOriginalNodeExecutionId(),
            Direction.ASC, NodeProjectionUtils.fieldsForIdentityStrategyStep)) {
      while (iterator.hasNext()) {
        NodeExecution next = iterator.next();
        if (Boolean.FALSE.equals(next.getOldRetry())) {
          // Getting first child with oldRetry false
          childNodeExecution = next;
          break;
        }
      }
    }

    return getChildFromNodeExecutions(childNodeExecution, originalNodeExecution, ambiance.getPlanId());
  }

  @Override
  public StepResponse handleChildResponse(
      Ambiance ambiance, IdentityStepParameters identityParams, Map<String, ResponseData> responseDataMap) {
    NodeExecution originalNodeExecution = nodeExecutionService.get(identityParams.getOriginalNodeExecutionId());
    // Copying the outcomes
    pmsOutcomeService.cloneForRetryExecution(ambiance, originalNodeExecution.getUuid());
    return StepResponse.builder().status(originalNodeExecution.getStatus()).build();
  }

  @Override
  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, IdentityStepParameters identityParams, StepInputPackage inputPackage) {
    NodeExecution originalStrategyNodeExecution = nodeExecutionService.getWithFieldsIncluded(
        identityParams.getOriginalNodeExecutionId(), NodeProjectionUtils.fieldsForIdentityStrategyStep);
    List<NodeExecution> childrenNodeExecutions = new ArrayList<>();

    try (CloseableIterator<NodeExecution> iterator =
             // Use original planExecutionId that belongs to the originalNodeExecutionId and not current
             // planExecutionId(ambiance.getPlanExecutionId)
        nodeExecutionService.fetchChildrenNodeExecutionsIterator(
            originalStrategyNodeExecution.getAmbiance().getPlanExecutionId(),
            identityParams.getOriginalNodeExecutionId(), NodeProjectionUtils.fieldsForIdentityStrategyStep)) {
      while (iterator.hasNext()) {
        NodeExecution next = iterator.next();
        // Don't want to include retried nodeIds
        if (Boolean.FALSE.equals(next.getOldRetry())) {
          childrenNodeExecutions.add(next);
        }
      }
    }

    List<ChildrenExecutableResponse.Child> children =
        getChildrenFromNodeExecutions(childrenNodeExecutions, ambiance.getPlanId());
    long maxConcurrency =
        originalStrategyNodeExecution.getExecutableResponses().get(0).getChildren().getMaxConcurrency();

    return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
  }

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, IdentityStepParameters identityParams, Map<String, ResponseData> responseDataMap) {
    return createStepResponseFromChildResponse(responseDataMap);
  }

  @Override
  public Class<IdentityStepParameters> getStepParametersClass() {
    return IdentityStepParameters.class;
  }

  private List<ChildrenExecutableResponse.Child> getChildrenFromNodeExecutions(
      List<NodeExecution> childrenNodeExecutions, String planId) {
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    List<Node> identityNodesToBeCreated = new ArrayList<>();
    for (NodeExecution nodeExecution : childrenNodeExecutions) {
      if (nodeExecution.getNode() instanceof PlanNode) {
        Node node = IdentityPlanNode.mapPlanNodeToIdentityNode(UUIDGenerator.generateUuid(), nodeExecution.getNode(),
            nodeExecution.getIdentifier(), nodeExecution.getName(), nodeExecution.getNode().getStepType(),
            nodeExecution.getUuid());
        children.add(ChildrenExecutableResponse.Child.newBuilder()
                         .setChildNodeId(node.getUuid())
                         .setStrategyMetadata(
                             AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()).getStrategyMetadata())
                         .build());
        identityNodesToBeCreated.add(node);
      } else {
        children.add(ChildrenExecutableResponse.Child.newBuilder()
                         .setChildNodeId(nodeExecution.getNode().getUuid())
                         .setStrategyMetadata(
                             AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()).getStrategyMetadata())
                         .build());
      }
    }
    planService.saveIdentityNodesForMatrix(identityNodesToBeCreated, planId);
    return children;
  }

  private ChildExecutableResponse getChildFromNodeExecutions(
      NodeExecution childNodeExecution, NodeExecution originalNodeExecution, String planId) {
    Node node = childNodeExecution.getNode();
    if (node instanceof PlanNode) {
      IdentityPlanNode identityPlanNode = IdentityPlanNode.mapPlanNodeToIdentityNode(UUIDGenerator.generateUuid(), node,
          childNodeExecution.getIdentifier(), childNodeExecution.getName(), node.getStepType(),
          childNodeExecution.getUuid());
      planService.saveIdentityNodesForMatrix(Collections.singletonList(identityPlanNode), planId);
      return ChildExecutableResponse.newBuilder().setChildNodeId(identityPlanNode.getUuid()).build();
    }
    return originalNodeExecution.getExecutableResponses().get(0).getChild();
  }
}
