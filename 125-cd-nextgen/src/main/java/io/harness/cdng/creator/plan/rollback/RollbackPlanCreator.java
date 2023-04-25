/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.rollback;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.infrastructure.InfraRollbackPMSPlanCreator;
import io.harness.cdng.pipeline.beans.RollbackNode;
import io.harness.cdng.pipeline.beans.RollbackOptionalChildChainStepParameters;
import io.harness.cdng.pipeline.beans.RollbackOptionalChildChainStepParameters.RollbackOptionalChildChainStepParametersBuilder;
import io.harness.cdng.pipeline.steps.CombinedRollbackStep;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import java.util.Collections;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDC)
public class RollbackPlanCreator {
  public PlanCreationResponse createPlanForRollback(PlanCreationContext ctx, YamlField executionField) {
    YamlField executionStepsField = executionField.getNode().getField(YAMLFieldNameConstants.STEPS);

    if (executionStepsField == null || executionStepsField.getNode().asArray().size() == 0) {
      return PlanCreationResponse.builder().build();
    }
    YamlNode stageNode =
        YamlUtils.getGivenYamlNodeFromParentPath(executionField.getNode(), YAMLFieldNameConstants.STAGE);
    RollbackOptionalChildChainStepParametersBuilder stepParametersBuilder =
        RollbackOptionalChildChainStepParameters.builder();

    // Infra rollback
    YamlField infraField = executionField.getNode().nextSiblingNodeFromParentObject(YamlTypes.PIPELINE_INFRASTRUCTURE);
    PlanCreationResponse infraRollbackPlan = InfraRollbackPMSPlanCreator.createInfraRollbackPlan(infraField);
    if (isNotEmpty(infraRollbackPlan.getNodes())) {
      String infraNodeFullIdentifier =
          YamlUtils.getQualifiedNameTillGivenField(infraField.getNode(), YAMLFieldNameConstants.STAGES);
      stepParametersBuilder.childNode(
          RollbackNode.builder()
              .nodeId(infraField.getNode().getUuid() + InfraRollbackPMSPlanCreator.INFRA_ROLLBACK_NODE_ID_SUFFIX)
              .dependentNodeIdentifier(infraNodeFullIdentifier)
              .build());
    } else {
      YamlField environmentField = executionField.getNode().nextSiblingNodeFromParentObject(YamlTypes.ENVIRONMENT_YAML);
      infraRollbackPlan = InfraRollbackPMSPlanCreator.createProvisionerRollbackPlan(environmentField);
      if (isNotEmpty(infraRollbackPlan.getNodes())) {
        String infraNodeFullIdentifier =
            YamlUtils.getQualifiedNameTillGivenField(environmentField.getNode(), YAMLFieldNameConstants.STAGES);
        stepParametersBuilder.childNode(RollbackNode.builder()
                                            .nodeId(environmentField.getNode().getUuid()
                                                + InfraRollbackPMSPlanCreator.INFRA_ROLLBACK_NODE_ID_SUFFIX)
                                            .dependentNodeIdentifier(infraNodeFullIdentifier)
                                            .build());
      }
    }

    // ExecutionRollback
    PlanCreationResponse executionRollbackPlanNode =
        ExecutionRollbackPMSPlanCreator.createExecutionRollbackPlanNode(executionField.getNode());
    if (EmptyPredicate.isNotEmpty(executionRollbackPlanNode.getNodes())) {
      String executionRollbackUuid =
          executionStepsField.getNode().getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_EXECUTION_NODE_ID_SUFFIX;
      String executionNodeFullIdentifier =
          YamlUtils.getQualifiedNameTillGivenField(executionField.getNode(), YAMLFieldNameConstants.STAGES);
      stepParametersBuilder.childNode(RollbackNode.builder()
                                          .nodeId(executionRollbackUuid)
                                          .dependentNodeIdentifier(executionNodeFullIdentifier)
                                          .build());
    }

    String combinedRollbackNodeUuid =
        stageNode.getUuid() + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX;
    PlanNode deploymentStageRollbackNode =
        PlanNode.builder()
            .uuid(combinedRollbackNodeUuid)
            .name(NGCommonUtilPlanCreationConstants.ROLLBACK_NODE_NAME)
            .identifier(YAMLFieldNameConstants.ROLLBACK_STEPS)
            .stepType(CombinedRollbackStep.STEP_TYPE)
            .stepParameters(stepParametersBuilder.build())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD_CHAIN).build())
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_NODE)
            .build();

    PlanCreationResponse finalResponse =
        PlanCreationResponse.builder()
            .node(deploymentStageRollbackNode.getUuid(), deploymentStageRollbackNode)
            .preservedNodesInRollbackMode(Collections.singletonList(combinedRollbackNodeUuid))
            .build();
    finalResponse.merge(executionRollbackPlanNode);
    finalResponse.merge(infraRollbackPlan);

    return finalResponse;
  }
}
