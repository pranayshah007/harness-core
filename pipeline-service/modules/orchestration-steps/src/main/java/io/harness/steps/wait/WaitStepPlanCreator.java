/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.wait;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;

import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.AbstractStepPlanCreator;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.plancreator.steps.internal.PmsStepPlanCreatorUtils;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.utils.PlanCreatorUtilsCommon;
import io.harness.when.utils.RunInfoUtils;

import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
public class WaitStepPlanCreator extends AbstractStepPlanCreator<WaitStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.WAIT_STEP);
  }

  @Override
  public Class<WaitStepNode> getFieldClass() {
    return WaitStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, WaitStepNode stepElement) {
    final boolean isStepInsideRollback =
        YamlUtils.findParentNode(ctx.getCurrentField().getNode(), ROLLBACK_STEPS) != null;

    List<AdviserObtainment> adviserObtainmentFromMetaData =
        PmsStepPlanCreatorUtils.getAdviserObtainmentFromMetaData(kryoSerializer, ctx.getCurrentField(), false);
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();
    stepElement.setIdentifier(StrategyUtils.getIdentifierWithExpression(ctx, stepElement.getIdentifier()));
    stepElement.setName(StrategyUtils.getIdentifierWithExpression(ctx, stepElement.getName()));

    StepParameters stepParameters = getStepParameters(ctx, stepElement);
    addStrategyFieldDependencyIfPresent(ctx, stepElement, dependenciesNodeMap, metadataMap);
    PlanNode stepPlanNode =
        PlanNode.builder()
            .uuid(StrategyUtils.getSwappedPlanNodeId(ctx, stepElement.getUuid()))
            .name(PmsStepPlanCreatorUtils.getName(stepElement))
            .identifier(stepElement.getIdentifier())
            .stepType(stepElement.getStepSpecType().getStepType())
            .group(StepOutcomeGroup.STEP.name())
            .stepParameters(stepParameters)
            .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                       .setType(FacilitatorType.newBuilder()
                                                    .setType(stepElement.getStepSpecType().getFacilitatorType())
                                                    .build())
                                       .build())
            .adviserObtainments(adviserObtainmentFromMetaData)
            .skipCondition(SkipInfoUtils.getSkipCondition(stepElement.getSkipCondition()))
            .whenCondition(isStepInsideRollback ? RunInfoUtils.getRunConditionForRollback(stepElement.getWhen())
                                                : RunInfoUtils.getRunConditionForStep(stepElement.getWhen()))
            .skipUnresolvedExpressionsCheck(stepElement.getStepSpecType().skipUnresolvedExpressionsCheck())
            .build();

    return PlanCreationResponse.builder()
        .planNode(stepPlanNode)
        .dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
                          .toBuilder()
                          .putDependencyMetadata(
                              stepElement.getUuid(), Dependency.newBuilder().putAllMetadata(metadataMap).build())
                          .build())
        .build();
  }
  protected StepParameters getStepParameters(PlanCreationContext ctx, WaitStepNode stepElement) {
    if (stepElement.getStepSpecType() instanceof WithStepElementParameters) {
      return ((WaitStepInfo) stepElement.getStepSpecType())
          .getStepParameters(stepElement,
              PlanCreatorUtilsCommon.getRollbackParameters(
                  ctx.getCurrentField(), Collections.emptySet(), RollbackStrategy.UNKNOWN),
              ctx);
    }

    return stepElement.getStepSpecType().getStepParameters();
  }
}