/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.internal;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;

import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.AbstractStepPlanCreator;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.PlanCreatorUtilsCommon;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
@OwnedBy(PIPELINE)
@Slf4j
public abstract class PMSStepPlanCreatorV2<T extends PmsAbstractStepNode> extends AbstractStepPlanCreator<T> {
  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, T stepElement) {
    boolean isStepInsideRollback = false;
    if (YamlUtils.findParentNode(ctx.getCurrentField().getNode(), ROLLBACK_STEPS) != null) {
      isStepInsideRollback = true;
    }

    List<AdviserObtainment> adviserObtainmentFromMetaData =
        PmsStepPlanCreatorUtils.getAdviserObtainmentFromMetaData(kryoSerializer, ctx.getCurrentField(), false);
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();
    stepElement.setIdentifier(StrategyUtils.getIdentifierWithExpression(ctx, stepElement.getIdentifier()));
    stepElement.setName(StrategyUtils.getIdentifierWithExpression(ctx, stepElement.getName()));

    StepParameters stepParameters = getStepParameters(ctx, stepElement);
    addStrategyFieldDependencyIfPresent(ctx, stepElement, dependenciesNodeMap, metadataMap);
    PlanCreationContextValue planCreationContextValue = ctx.getGlobalContext().get("metadata");
    ExecutionMode executionMode = planCreationContextValue.getMetadata().getExecutionMode();
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
            .whenCondition(isStepInsideRollback
                    ? RunInfoUtils.getRunConditionForRollback(stepElement.getWhen(), executionMode)
                    : RunInfoUtils.getRunConditionForStep(stepElement.getWhen()))
            .timeoutObtainment(
                SdkTimeoutObtainment.builder()
                    .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                    .parameters(
                        AbsoluteSdkTimeoutTrackerParameters.builder().timeout(getTimeoutString(stepElement)).build())
                    .build())
            .skipUnresolvedExpressionsCheck(stepElement.getStepSpecType().skipUnresolvedExpressionsCheck())
            .expressionMode(stepElement.getStepSpecType().getExpressionMode())
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

  protected ParameterField<String> getTimeoutString(T stepElement) {
    ParameterField<Timeout> timeout = TimeoutUtils.getTimeout(stepElement.getTimeout());
    if (timeout.isExpression()) {
      return ParameterField.createExpressionField(
          true, timeout.getExpressionValue(), timeout.getInputSetValidator(), true);
    } else {
      return ParameterField.createValueField(timeout.getValue().getTimeoutString());
    }
  }
  protected StepParameters getStepParameters(PlanCreationContext ctx, T stepElement) {
    if (stepElement.getStepSpecType() instanceof WithStepElementParameters) {
      stepElement.setTimeout(TimeoutUtils.getTimeout(stepElement.getTimeout()));
      return ((PMSStepInfo) stepElement.getStepSpecType())
          .getStepParameters(stepElement,
              PlanCreatorUtilsCommon.getRollbackParameters(
                  ctx.getCurrentField(), Collections.emptySet(), RollbackStrategy.UNKNOWN),
              ctx);
    }

    return stepElement.getStepSpecType().getStepParameters();
  }
}
