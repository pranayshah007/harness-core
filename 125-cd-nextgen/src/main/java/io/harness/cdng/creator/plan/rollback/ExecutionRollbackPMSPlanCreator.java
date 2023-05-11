/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.rollback;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.beans.RollbackNode;
import io.harness.cdng.pipeline.beans.RollbackOptionalChildChainStepParameters;
import io.harness.cdng.pipeline.beans.RollbackOptionalChildChainStepParameters.RollbackOptionalChildChainStepParametersBuilder;
import io.harness.cdng.pipeline.steps.RollbackOptionalChildChainStep;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.RollbackModeBehaviour;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class ExecutionRollbackPMSPlanCreator {
  public static PlanCreationResponse createExecutionRollbackPlanNode(YamlNode executionField) {
    if (executionField == null) {
      return PlanCreationResponse.builder().build();
    }
    Map<String, YamlField> dependencies = new HashMap<>();
    YamlField executionStepsField = executionField.getField(YAMLFieldNameConstants.STEPS);

    if (executionStepsField == null || executionStepsField.getNode().asArray().size() == 0) {
      return PlanCreationResponse.builder().build();
    }
    RollbackOptionalChildChainStepParametersBuilder stepParametersBuilder =
        RollbackOptionalChildChainStepParameters.builder();

    String executionNodeFullIdentifier =
        YamlUtils.getQualifiedNameTillGivenField(executionField, YAMLFieldNameConstants.STAGES);
    YamlField executionRollbackSteps = executionField.getField(YAMLFieldNameConstants.ROLLBACK_STEPS);

    if (executionRollbackSteps != null && executionRollbackSteps.getNode() != null
        && executionRollbackSteps.getNode().asArray().size() > 0) {
      // Adding dependencies
      dependencies.put(
          executionRollbackSteps.getNode().getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STEPS_NODE_ID_SUFFIX,
          executionRollbackSteps);

      stepParametersBuilder.childNode(RollbackNode.builder()
                                          .nodeId(executionRollbackSteps.getNode().getUuid()
                                              + NGCommonUtilPlanCreationConstants.ROLLBACK_STEPS_NODE_ID_SUFFIX)
                                          .dependentNodeIdentifier(executionNodeFullIdentifier)
                                          .build());
    }

    if (EmptyPredicate.isEmpty(stepParametersBuilder.build().getChildNodes())) {
      return PlanCreationResponse.builder().build();
    }

    String executionRollbackNodeUuid =
        executionStepsField.getNode().getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_EXECUTION_NODE_ID_SUFFIX;
    PlanNode deploymentStageRollbackNode =
        PlanNode.builder()
            .uuid(executionRollbackNodeUuid)
            .name(NGCommonUtilPlanCreationConstants.EXECUTION_NODE_NAME + " "
                + NGCommonUtilPlanCreationConstants.ROLLBACK_NODE_NAME)
            .identifier(YAMLFieldNameConstants.ROLLBACK_STEPS)
            .stepType(RollbackOptionalChildChainStep.STEP_TYPE)
            .stepParameters(stepParametersBuilder.build())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD_CHAIN).build())
                    .build())
            .skipExpressionChain(true)
            .build();

    return PlanCreationResponse.builder()
        .node(deploymentStageRollbackNode.getUuid(), deploymentStageRollbackNode)
        .dependencies(
            DependenciesUtils.toDependenciesProtoWithRollbackMode(dependencies, RollbackModeBehaviour.PRESERVE))
        .preservedNodesInRollbackMode(Collections.singletonList(executionRollbackNodeUuid))
        .build();
  }
}
