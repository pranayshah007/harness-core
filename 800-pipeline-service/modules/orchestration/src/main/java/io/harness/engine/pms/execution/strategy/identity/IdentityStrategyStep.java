/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import static io.harness.steps.StepUtils.createStepResponseFromChildResponse;

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
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.sdk.core.steps.executables.ChildrenExecutable;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

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
    List<NodeExecution> nodeExecutions = nodeExecutionService.fetchNodeExecutionsByParentIdWithAmbianceAndNode(
        stepParameters.getOriginalNodeExecutionId(), true, true);

    NodeExecution strategyNodeExecution = null;
    List<NodeExecution> childrenNodeExecutions = new ArrayList<>();
    for (NodeExecution nodeExecution : nodeExecutions) {
      if (nodeExecution.getUuid().equals(stepParameters.getOriginalNodeExecutionId())) {
        strategyNodeExecution = nodeExecution;
      } else {
        childrenNodeExecutions.add(nodeExecution);
      }
    }

    String childNodeId =
        strategyNodeExecution.getExecutableResponses().get(0).getChildren().getChildren(0).getChildNodeId();

    List<ChildrenExecutableResponse.Child> children =
        getChildrenFromNodeExecutions(childrenNodeExecutions, childNodeId, ambiance.getPlanId());
    long maxConcurrency = strategyNodeExecution.getExecutableResponses().get(0).getChildren().getMaxConcurrency();

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
      List<NodeExecution> childrenNodeExecutions, String childNodeId, String planId) {
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    List<Node> identityNodesToBeCreated = new ArrayList<>();
    for (NodeExecution nodeExecution : childrenNodeExecutions) {
      if (StatusUtils.brokeAndAbortedStatuses().contains(nodeExecution.getStatus())) {
        children.add(ChildrenExecutableResponse.Child.newBuilder()
                         .setChildNodeId(childNodeId)
                         .setStrategyMetadata(
                             AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()).getStrategyMetadata())
                         .build());
      } else {
        // Copy identifier from nodeExecution.
        Node node = IdentityPlanNode.mapPlanNodeToIdentityNode(UUIDGenerator.generateUuid(), nodeExecution.getNode(),
            nodeExecution.getIdentifier(), nodeExecution.getName(), nodeExecution.getNode().getStepType(),
            nodeExecution.getUuid());
        children.add(ChildrenExecutableResponse.Child.newBuilder()
                         .setChildNodeId(node.getUuid())
                         .setStrategyMetadata(
                             AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()).getStrategyMetadata())
                         .build());
        identityNodesToBeCreated.add(node);
      }
    }

    planService.saveIdentityNodesForMatrix(identityNodesToBeCreated, planId);
    return children;
  }
}
