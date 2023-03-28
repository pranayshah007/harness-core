/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.rollback.RollbackPlanCreator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.steps.common.NGSectionStepWithRollbackInfo;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@OwnedBy(CDC)
public class CDExecutionPMSPlanCreator extends ChildrenPlanCreator<ExecutionElementConfig> {
  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, ExecutionElementConfig config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> stepYamlFields = ctx.getStepYamlFields();

    // Add Steps Node
    if (EmptyPredicate.isNotEmpty(stepYamlFields)) {
      YamlField stepsField =
          Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STEPS));
      Map<String, YamlField> stepsYamlFieldMap = new HashMap<>();
      stepsYamlFieldMap.put(stepsField.getNode().getUuid(), stepsField);
      responseMap.put(stepsField.getNode().getUuid(),
          PlanCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(stepsYamlFieldMap))
              .build());
    }

    // TODO: NEED TO CREATE A ROLLBACK PLAN CREATOR
    // add rollback dependency
    YamlField executionStepsField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STEPS);
    PlanCreationResponse planForRollback = RollbackPlanCreator.createPlanForRollback(ctx, ctx.getCurrentField());
    if (EmptyPredicate.isNotEmpty(planForRollback.getNodes())) {
      responseMap.put(
          Objects.requireNonNull(executionStepsField).getNode().getUuid() + "_combinedRollback", planForRollback);
    }

    return responseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, ExecutionElementConfig config, List<String> childrenNodeIds) {
    String childNodeId = "";
    if (ctx.getMetadata().getMetadata().getExecutionMode() == ExecutionMode.PIPELINE_ROLLBACK) {
      YamlNode stageNode =
          YamlUtils.getGivenYamlNodeFromParentPath(ctx.getCurrentField().getNode(), YAMLFieldNameConstants.STAGE);
      childNodeId = stageNode.getUuid() + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX;
    } else {
      YamlField stepsField =
          Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STEPS));
      childNodeId = stepsField.getNode().getUuid();
    }
    StepParameters stepParameters =
        NGSectionStepParameters.builder().childNodeId(childNodeId).logMessage("Execution Element").build();
    return PlanNode.builder()
        .uuid(ctx.getCurrentField().getNode().getUuid())
        .identifier(NGCommonUtilPlanCreationConstants.EXECUTION_NODE_IDENTIFIER)
        .stepType(NGSectionStepWithRollbackInfo.STEP_TYPE)
        .group(StepOutcomeGroup.EXECUTION.name())
        .name(NGCommonUtilPlanCreationConstants.EXECUTION_NODE_NAME)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipExpressionChain(false)
        .build();
  }

  @Override
  public Class<ExecutionElementConfig> getFieldClass() {
    return ExecutionElementConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.EXECUTION, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }
}
