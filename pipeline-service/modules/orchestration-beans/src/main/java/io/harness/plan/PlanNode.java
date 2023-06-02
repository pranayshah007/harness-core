/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plan;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdvisorObtainmentList;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.timeout.contracts.TimeoutObtainment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.With;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder(toBuilder = true)
@FieldNameConstants(innerTypeName = "PlanNodeKeys")
@OwnedBy(PIPELINE)
@TypeAlias("planNode")
public class PlanNode implements Node {
  // Identifiers
  @NotNull String uuid;
  @NotNull String name;
  @NotNull StepType stepType;
  @NotNull String identifier;
  String group;

  // Input/Outputs
  PmsStepParameters stepParameters;
  String executionInputTemplate;
  // TODO change this to PmsStepInputs
  OrchestrationMap stepInputs;
  @Singular List<RefObject> refObjects;

  // Hooks
  @Singular List<AdviserObtainment> adviserObtainments;
  Map<ExecutionMode, List<AdviserObtainment>> advisorObtainmentsForExecutionMode;
  @Singular List<FacilitatorObtainment> facilitatorObtainments;
  @Singular List<TimeoutObtainment> timeoutObtainments;

  @Deprecated String serviceName;

  // Skip
  String skipCondition;
  String whenCondition;

  // stage fqn
  @NonFinal String stageFqn;

  // Config
  boolean skipExpressionChain;
  @Builder.Default SkipType skipGraphType = SkipType.NOOP;
  @Builder.Default @Deprecated boolean skipUnresolvedExpressionsCheck = true;

  @Builder.Default ExpressionMode expressionMode = ExpressionMode.RETURN_NULL_IF_UNRESOLVED;

  @With @Builder.Default Boolean preserveInRollbackMode = false;

  public static PlanNode fromPlanNodeProto(PlanNodeProto planNodeProto) {
    if (planNodeProto == null) {
      return null;
    }

    return PlanNode.builder()
        .uuid(planNodeProto.getUuid())
        .name(planNodeProto.getName())
        .stageFqn(planNodeProto.getStageFqn())
        .stepType(planNodeProto.getStepType())
        .identifier(planNodeProto.getIdentifier())
        .group(planNodeProto.getGroup())
        .stepParameters(PmsStepParameters.parse(planNodeProto.getStepParameters()))
        .refObjects(planNodeProto.getRebObjectsList())
        .adviserObtainments(planNodeProto.getAdviserObtainmentsList())
        .advisorObtainmentsForExecutionMode(
            buildAdvisorObtainmentsForExecutionMode(planNodeProto.getAdviserObtainmentsForExecutionModeMap()))
        .facilitatorObtainments(planNodeProto.getFacilitatorObtainmentsList())
        .timeoutObtainments(planNodeProto.getTimeoutObtainmentsList())
        .skipCondition(planNodeProto.getSkipCondition())
        .whenCondition(planNodeProto.getWhenCondition())
        .skipExpressionChain(planNodeProto.getSkipExpressionChain())
        .skipGraphType(planNodeProto.getSkipType())
        .skipUnresolvedExpressionsCheck(planNodeProto.getSkipUnresolvedExpressionsCheck())
        .expressionMode(ExpressionModeMapper.fromExpressionModeProto(planNodeProto.getExpressionMode()))
        .serviceName(planNodeProto.getServiceName())
        .stepInputs(OrchestrationMap.parse(planNodeProto.getStepInputs()))
        .executionInputTemplate(planNodeProto.getExecutionInputTemplate())
        .build();
  }

  static Map<ExecutionMode, List<AdviserObtainment>> buildAdvisorObtainmentsForExecutionMode(
      Map<String, AdvisorObtainmentList> advisorObtainmentsForExecutionMode) {
    Map<ExecutionMode, List<AdviserObtainment>> result = new HashMap<>();
    for (Map.Entry<String, AdvisorObtainmentList> entry : advisorObtainmentsForExecutionMode.entrySet()) {
      ExecutionMode executionMode = ExecutionMode.valueOf(entry.getKey());
      result.put(executionMode, entry.getValue().getAdviserObtainmentsList());
    }
    return result;
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.PLAN_NODE;
  }

  @Override
  public String getStageFqn() {
    return this.stageFqn;
  }

  public boolean isPreserveInRollbackMode() {
    if (preserveInRollbackMode == null) {
      return false;
    }
    return preserveInRollbackMode;
  }
}
