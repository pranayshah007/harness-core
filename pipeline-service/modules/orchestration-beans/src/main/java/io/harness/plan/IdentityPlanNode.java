/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.PIPELINE)
@Value
@Builder
@FieldNameConstants(innerTypeName = "IdentityPlanNodeKeys")
@TypeAlias("identityPlanNode")
public class IdentityPlanNode implements Node {
  @NotNull String uuid;
  @NotNull String name;
  @NotNull String identifier;
  String group;
  boolean isSkipExpressionChain;
  String whenCondition;
  String skipCondition;
  @Builder.Default SkipType skipGraphType = SkipType.NOOP;
  String stageFqn;
  StepType stepType;
  // todo: use list of execution IDs in retry failed pipeline as well
  @NonFinal String originalNodeExecutionId;
  // one plan node can map to multiple node executions because of introduction of strategy. Hence, it is better to take
  // a list instead of a single node if multiple node executions for a plan node are found
  @NonFinal List<String> allOriginalNodeExecutionIds;
  String serviceName;
  String executionInputTemplate;
  // Saving the advisorObtainmentsForExecutionMode so that when the postProdRollback is triggered 2nd time for other
  // stages, the advisorObtainment info is available for transforming the plan even when the planNode was converted into
  // IdentityNode is 1st postProdRollback.
  Map<ExecutionMode, List<AdviserObtainment>> advisorObtainmentsForExecutionMode;

  // if true, the advisor response from the previous execution will be ignored and adviserObtainments will be used
  @With @Builder.Default Boolean useAdviserObtainments = false;
  @With List<AdviserObtainment> adviserObtainments;

  @Override
  public String getStageFqn() {
    return this.stageFqn;
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.IDENTITY_PLAN_NODE;
  }

  @Override
  public PmsStepParameters getStepParameters() {
    PmsStepParameters stepParameters = new PmsStepParameters();
    stepParameters.put(IdentityPlanNodeKeys.originalNodeExecutionId, originalNodeExecutionId);
    return stepParameters;
  }

  @Override
  public boolean isSkipExpressionChain() {
    return this.isSkipExpressionChain;
  }

  @Override
  public String getWhenCondition() {
    return this.whenCondition;
  }

  @Override
  public String getSkipCondition() {
    return this.skipCondition;
  }

  @Override
  public SkipType getSkipGraphType() {
    return this.skipGraphType;
  }

  @Override
  public boolean isSkipUnresolvedExpressionsCheck() {
    return true;
  }

  public static IdentityPlanNode mapPlanNodeToIdentityNode(
      Node node, StepType stepType, String originalNodeExecutionUuid) {
    return mapPlanNodeToIdentityNode(node, stepType, originalNodeExecutionUuid, false);
  }

  public static IdentityPlanNode mapPlanNodeToIdentityNode(
      Node node, StepType stepType, String originalNodeExecutionUuid, boolean alwaysSkipGraph) {
    return IdentityPlanNode.builder()
        .uuid(node.getUuid())
        .name(node.getName())
        .identifier(node.getIdentifier())
        .group(node.getGroup())
        .skipGraphType(alwaysSkipGraph ? SkipType.SKIP_NODE : node.getSkipGraphType())
        .stepType(stepType)
        .isSkipExpressionChain(node.isSkipExpressionChain())
        .serviceName(node.getServiceName())
        .stageFqn(node.getStageFqn())
        .advisorObtainmentsForExecutionMode(node.getAdvisorObtainmentsForExecutionMode())
        .adviserObtainments(node.getAdviserObtainments())
        .whenCondition(node.getWhenCondition())
        .originalNodeExecutionId(originalNodeExecutionUuid)
        .build();
  }

  public static IdentityPlanNode mapPlanNodeToIdentityNode(String newUuid, Node node, String nodeIdentifier,
      String nodeName, StepType stepType, String originalNodeExecutionUuid) {
    return IdentityPlanNode.builder()
        .uuid(newUuid != null ? newUuid : node.getUuid())
        .name(nodeName)
        .identifier(nodeIdentifier)
        .group(node.getGroup())
        .skipGraphType(node.getSkipGraphType())
        .stepType(stepType)
        .isSkipExpressionChain(node.isSkipExpressionChain())
        .serviceName(node.getServiceName())
        .stageFqn(node.getStageFqn())
        .whenCondition(node.getWhenCondition())
        .originalNodeExecutionId(originalNodeExecutionUuid)
        .build();
  }

  public void convertToListOfOGNodeExecIds(String nodeExecId) {
    if (EmptyPredicate.isEmpty(allOriginalNodeExecutionIds)) {
      allOriginalNodeExecutionIds = new ArrayList<>();
      allOriginalNodeExecutionIds.add(originalNodeExecutionId);
      originalNodeExecutionId = null;
    }
    allOriginalNodeExecutionIds.add(nodeExecId);
  }
}
