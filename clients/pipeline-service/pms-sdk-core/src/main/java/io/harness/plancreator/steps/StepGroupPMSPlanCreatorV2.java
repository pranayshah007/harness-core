/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;

import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.steps.stepgroup.StepGroupStep;
import io.harness.steps.common.steps.stepgroup.StepGroupStepParameters;
import io.harness.when.utils.RunInfoUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StepGroupPMSPlanCreatorV2 extends StepGroupPMSPlanCreator {
  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, StepGroupElementConfig config, List<String> childrenNodeIds) {
    YamlField stepsField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STEPS));
    config.setIdentifier(StrategyUtils.getIdentifierWithExpression(ctx, config.getIdentifier()));
    config.setName(StrategyUtils.getIdentifierWithExpression(ctx, config.getName()));
    StepParameters stepParameters = StepGroupStepParameters.getStepParameters(config, stepsField.getNode().getUuid());

    boolean isStepGroupInsideRollback = false;
    if (YamlUtils.findParentNode(ctx.getCurrentField().getNode(), ROLLBACK_STEPS) != null) {
      isStepGroupInsideRollback = true;
    }
    PlanCreationContextValue planCreationContextValue = ctx.getGlobalContext().get("metadata");
    ExecutionMode executionMode = planCreationContextValue.getMetadata().getExecutionMode();
    return PlanNode.builder()
        .name(config.getName())
        .uuid(StrategyUtils.getSwappedPlanNodeId(ctx, config.getUuid()))
        .identifier(config.getIdentifier())
        .stepType(StepGroupStep.STEP_TYPE)
        .group(StepCategory.STEP_GROUP.name())
        .skipCondition(SkipInfoUtils.getSkipCondition(config.getSkipCondition()))
        // We Should add default when condition as StageFailure if stepGroup is inside rollback
        .whenCondition(isStepGroupInsideRollback
                ? RunInfoUtils.getRunConditionForRollback(config.getWhen(), executionMode)
                : RunInfoUtils.getRunConditionForStep(config.getWhen()))
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .adviserObtainments(getAdviserObtainmentFromMetaData(
            ctx.getCurrentField(), StrategyUtils.isWrappedUnderStrategy(ctx.getCurrentField())))
        .skipExpressionChain(false)
        .build();
  }

  @Override
  public Class<StepGroupElementConfig> getFieldClass() {
    return StepGroupElementConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP_GROUP, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Inject private KryoSerializer kryoSerializer;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, StepGroupElementConfig config) {
    List<YamlField> dependencyNodeIdsList = ctx.getStepYamlFields();

    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    // Add Steps Node
    if (EmptyPredicate.isNotEmpty(dependencyNodeIdsList)) {
      YamlField stepsField =
          Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STEPS));
      String stepsNodeId = stepsField.getNode().getUuid();
      Map<String, YamlField> stepsYamlFieldMap = new HashMap<>();
      stepsYamlFieldMap.put(stepsNodeId, stepsField);
      responseMap.put(stepsNodeId,
          PlanCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(stepsYamlFieldMap))
              .build());
    }
    addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, config.getUuid(), config.getName(), config.getIdentifier(),
        responseMap, new HashMap<>(), getAdviserObtainmentFromMetaData(ctx.getCurrentField(), false));

    return responseMap;
  }
}
