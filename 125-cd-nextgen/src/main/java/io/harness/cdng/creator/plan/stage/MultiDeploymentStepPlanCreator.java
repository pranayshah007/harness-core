/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.stage;

import io.harness.cdng.pipeline.steps.MultiDeploymentSpawnerStep;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class MultiDeploymentStepPlanCreator {
  public PlanNode createPlan(MultiDeploymentMetadata metadata) {
    String childNodeId = metadata.getMultiDeploymentStepParameters().getChildNodeId();
    String multiDeploymentNodeId = metadata.getMultiDeploymentNodeId();
    if (EmptyPredicate.isEmpty(childNodeId) || EmptyPredicate.isEmpty(multiDeploymentNodeId)) {
      log.error("childNodeId and multiDeploymentNodeId not passed from parent. Please pass it.");
      throw new InvalidRequestException("Invalid use of strategy field. Please check");
    }
    StepParameters stepParameters = metadata.getMultiDeploymentStepParameters();
    // Returning expressionMode = RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED, because multiDeployment planNode might have
    // service inputs and those can be execution time input. So those will be converted to expressions that can be
    // resolved at execution time. Passing this expressionMode will make sure that expression remain as is if not
    // resolved instead of null during the stepParameters resolution.
    return PlanNode.builder()
        .uuid(multiDeploymentNodeId)
        .identifier(metadata.getStrategyNodeIdentifier())
        .stepType(MultiDeploymentSpawnerStep.STEP_TYPE)
        .group(StepOutcomeGroup.STRATEGY.name())
        .name(metadata.getStrategyNodeName())
        .stepParameters(stepParameters)
        .expressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                .build())
        .skipExpressionChain(true)
        .advisorObtainmentForExecutionMode(ExecutionMode.PIPELINE_ROLLBACK, metadata.getAdviserObtainments())
        .advisorObtainmentForExecutionMode(ExecutionMode.POST_EXECUTION_ROLLBACK, metadata.getAdviserObtainments())
        .adviserObtainments(metadata.getAdviserObtainments())
        .build();
  }
}
