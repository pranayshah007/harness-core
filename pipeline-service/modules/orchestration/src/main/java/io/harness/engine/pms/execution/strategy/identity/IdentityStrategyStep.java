/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import static io.harness.steps.SdkCoreStepUtils.createStepResponseFromChildResponse;

import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.steps.identity.IdentityStepParameters;
import io.harness.execution.NodeExecution;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;
import io.harness.utils.ExecutionModeUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.CloseableIterator;

@Slf4j
public class IdentityStrategyStep implements ChildrenExecutable<IdentityStepParameters> {
  @Inject NodeExecutionService nodeExecutionService;
  @Inject PlanService planService;
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(NGCommonUtilPlanCreationConstants.IDENTITY_STRATEGY)
                                               .setStepCategory(StepCategory.STRATEGY)
                                               .build();

  @Override
  public ChildrenExecutableResponse obtainChildren(
      Ambiance ambiance, IdentityStepParameters stepParameters, StepInputPackage inputPackage) {
    NodeExecution originalStrategyNodeExecution = nodeExecutionService.getWithFieldsIncluded(
        stepParameters.getOriginalNodeExecutionId(), NodeProjectionUtils.fieldsForIdentityStrategyStep);
    List<NodeExecution> childrenNodeExecutions = new ArrayList<>();
    try (CloseableIterator<NodeExecution> iterator =
             // Use original planExecutionId that belongs to the originalNodeExecutionId and not current
             // planExecutionId(ambiance.getPlanExecutionId)
        nodeExecutionService.fetchChildrenNodeExecutionsIterator(
            originalStrategyNodeExecution.getAmbiance().getPlanExecutionId(),
            stepParameters.getOriginalNodeExecutionId(), NodeProjectionUtils.fieldsForIdentityStrategyStep)) {
      while (iterator.hasNext()) {
        NodeExecution next = iterator.next();
        // Don't want to include retried nodeIds
        if (Boolean.FALSE.equals(next.getOldRetry())) {
          childrenNodeExecutions.add(next);
        }
      }
    }

    List<ChildrenExecutableResponse.Child> children = getChildrenFromNodeExecutions(childrenNodeExecutions, ambiance);
    long maxConcurrency =
        originalStrategyNodeExecution.getExecutableResponses().get(0).getChildren().getMaxConcurrency();

    return ChildrenExecutableResponse.newBuilder().addAllChildren(children).setMaxConcurrency(maxConcurrency).build();
  }

  @Override
  public Class<IdentityStepParameters> getStepParametersClass() {
    return IdentityStepParameters.class;
  }

  @Override
  public StepResponse handleChildrenResponse(
      Ambiance ambiance, IdentityStepParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    log.info("Completed  execution for Strategy Identity Step [{}]", stepParameters);
    return createStepResponseFromChildResponse(responseDataMap);
  }

  private List<ChildrenExecutableResponse.Child> getChildrenFromNodeExecutions(
      List<NodeExecution> childrenNodeExecutions, Ambiance ambiance) {
    String planId = ambiance.getPlanId();
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    List<Node> identityNodesToBeCreated = new ArrayList<>();
    for (NodeExecution nodeExecution : childrenNodeExecutions) {
      // Current node (if failed) needs to be added into children as execution node only if its part of the retry stage.
      // If we see a failed step that isn't part of the retry stage, it should be added as an identity stage.
      // This allows us to create an identity node for all such executions and not just use the same IdentityPlanNode
      // pointing to one of the executions (hence copying the status).
      Node node = planService.fetchNode(nodeExecution.getNodeId());
      if ((ExecutionModeUtils.isRollbackMode(ambiance.getMetadata().getExecutionMode())
              || !(node instanceof IdentityPlanNode))
          && StatusUtils.brokeAndAbortedStatuses().contains(nodeExecution.getStatus())) {
        children.add(ChildrenExecutableResponse.Child.newBuilder()
                         .setChildNodeId(nodeExecution.getNodeId())
                         .setStrategyMetadata(
                             AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()).getStrategyMetadata())
                         .build());
      } else {
        // Copy identifier from nodeExecution.
        Node identityNode = IdentityPlanNode.mapPlanNodeToIdentityNode(UUIDGenerator.generateUuid(), node,
            nodeExecution.getIdentifier(), nodeExecution.getName(), nodeExecution.getStepType(),
            nodeExecution.getUuid());
        children.add(ChildrenExecutableResponse.Child.newBuilder()
                         .setChildNodeId(identityNode.getUuid())
                         .setStrategyMetadata(
                             AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()).getStrategyMetadata())
                         .build());
        identityNodesToBeCreated.add(identityNode);
      }
    }

    planService.saveIdentityNodesForMatrix(identityNodesToBeCreated, planId);
    return children;
  }
}
