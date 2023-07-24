/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import static io.harness.pms.yaml.YAMLFieldNameConstants.PARALLEL;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.GenericPlanCreatorUtils;
import io.harness.plancreator.steps.InitContainerStepPlanCreater;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.plancreator.steps.internal.PmsAbstractStepNode;
import io.harness.plancreator.steps.internal.PmsStepPlanCreatorUtils;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.common.steps.stepgroup.StepGroupStep;
import io.harness.steps.common.steps.stepgroup.StepGroupStepParameters;
import io.harness.steps.plugin.ContainerStepSpec;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.PlanCreatorUtilsCommon;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.NGFailureActionType;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractContainerStepPlanCreator<T extends PmsAbstractStepNode> extends ChildrenPlanCreator<T> {
  @Inject KryoSerializer kryoSerializer;

  @Override public abstract Map<String, Set<String>> getSupportedTypes();

  @Override public abstract Class<T> getFieldClass();

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, PmsAbstractStepNode config) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    YamlField containerStep = ctx.getCurrentField();

    String initStepNodeId = "init-" + containerStep.getNode().getUuid();
    String stepNodeId = "step-" + containerStep.getNode().getUuid();

    ByteString advisorParametersInitStep = ByteString.copyFrom(
        kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(stepNodeId).build()));

    StepParameters stepParameters = getStepParameters(config, ctx);
    if (stepParameters instanceof StepElementParameters) {
      StepElementParameters stepElementParameters = (StepElementParameters) stepParameters;
      if (stepElementParameters.getSpec() instanceof ContainerStepSpec) {
        ((ContainerStepSpec) stepElementParameters.getSpec()).setName(config.getName());
        ((ContainerStepSpec) stepElementParameters.getSpec()).setIdentifier(config.getIdentifier());
      }
    }
    PlanNode initPlanNode = InitContainerStepPlanCreater.createPlanForField(
        initStepNodeId, stepParameters, advisorParametersInitStep, StepSpecTypeConstants.INIT_CONTAINER_STEP);
    PlanNode stepPlanNode = createPlanForStep(stepNodeId, stepParameters, getAdviserObtainmentsForStepNode(ctx));

    planCreationResponseMap.put(initPlanNode.getUuid(), PlanCreationResponse.builder().planNode(initPlanNode).build());
    planCreationResponseMap.put(stepPlanNode.getUuid(), PlanCreationResponse.builder().planNode(stepPlanNode).build());

    addStrategyFieldDependencyIfPresent(
        kryoSerializer, ctx, config.getUuid(), config.getName(), config.getIdentifier(), planCreationResponseMap);

    return planCreationResponseMap;
  }

  private List<AdviserObtainment> getAdviserObtainmentsForStepNode(PlanCreationContext ctx) {
    final YamlField currentField = ctx.getCurrentField();
    final boolean isStepInsideRollback = isStepInsideRollback(currentField);
    final List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionMap =
        PmsStepPlanCreatorUtils.getPriorityMergedFailureStrategies(currentField, isStepInsideRollback);
    for (Map.Entry<FailureStrategyActionConfig, Collection<FailureType>> entry : actionMap.entrySet()) {
      FailureStrategyActionConfig action = entry.getKey();
      Set<FailureType> failureTypes = new HashSet<>(entry.getValue());
      NGFailureActionType actionType = action.getType();

      if (isStepInsideRollback) {
        if (actionType == NGFailureActionType.STAGE_ROLLBACK || actionType == NGFailureActionType.STEP_GROUP_ROLLBACK) {
          throw new InvalidRequestException("Step inside rollback section cannot have Rollback as failure strategy.");
        }
      }

      AdviserObtainment.Builder adviserObtainmentBuilder = AdviserObtainment.newBuilder();
      PmsStepPlanCreatorUtils.adviserForActionTypeWithNoNextNode(
          kryoSerializer, currentField, adviserObtainments, action, failureTypes, actionType, adviserObtainmentBuilder);
    }
    return adviserObtainments;
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, T config, List<String> childrenNodeIds) {
    config.setIdentifier(StrategyUtils.getIdentifierWithExpression(ctx, config.getIdentifier()));
    config.setName(config.getName());

    StepGroupStepParameters stepGroupStepParameters =
        StepGroupStepParameters.builder()
            .identifier(config.getIdentifier())
            .name(config.getName())
            .skipCondition(config.getSkipCondition())
            .when(config.getWhen() != null ? config.getWhen().getValue() : null)
            .failureStrategies(config.getFailureStrategies() != null ? config.getFailureStrategies().getValue() : null)
            .childNodeID(childrenNodeIds.get(0))
            .build();

    return PlanNode.builder()
        .name(config.getName())
        .uuid(StrategyUtils.getSwappedPlanNodeId(ctx, config.getUuid()))
        .identifier(config.getIdentifier())
        .stepType(StepGroupStep.STEP_TYPE)
        .group(StepCategory.STEP_GROUP.name())
        .skipCondition(SkipInfoUtils.getSkipCondition(config.getSkipCondition()))
        .stepParameters(stepGroupStepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .adviserObtainments(getAdviserObtainmentFromMetaData(
            ctx.getCurrentField(), StrategyUtils.isWrappedUnderStrategy(ctx.getCurrentField())))
        .whenCondition(isStepInsideRollback(ctx.getCurrentField())
                ? RunInfoUtils.getRunConditionForRollback(config.getWhen())
                : RunInfoUtils.getRunConditionForStep(config.getWhen()))
        .timeoutObtainment(
            SdkTimeoutObtainment.builder()
                .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                .parameters(AbsoluteSdkTimeoutTrackerParameters.builder()
                                .timeout(TimeoutUtils.getTimeoutParameterFieldString(config.getTimeout()))
                                .build())
                .build())
        .skipExpressionChain(false)
        .build();
  }

  protected StepParameters getStepParameters(PmsAbstractStepNode stepElement, PlanCreationContext ctx) {
    if (stepElement.getStepSpecType() instanceof WithStepElementParameters) {
      stepElement.setTimeout(TimeoutUtils.getTimeout(stepElement.getTimeout()));
      return ((PMSStepInfo) stepElement.getStepSpecType())
          .getStepParameters(stepElement,
              PlanCreatorUtilsCommon.getRollbackParameters(
                  ctx.getCurrentField(), Collections.emptySet(), RollbackStrategy.UNKNOWN),
              ctx);
    }
    stepElement.setTimeout(TimeoutUtils.getTimeout(stepElement.getTimeout()));
    return stepElement.getStepSpecType().getStepParameters();
  }

  public abstract PlanNode createPlanForStep(
      String stepNodeId, StepParameters stepParameters, List<AdviserObtainment> adviserObtainments);

  private void addStrategyFieldDependencyIfPresent(KryoSerializer kryoSerializer, PlanCreationContext ctx, String uuid,
      String name, String identifier, LinkedHashMap<String, PlanCreationResponse> responseMap) {
    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, uuid, name, identifier, responseMap,
        new HashMap<>(), getAdviserObtainmentFromMetaData(ctx.getCurrentField(), false), false);
  }

  private List<AdviserObtainment> getAdviserObtainmentFromMetaData(YamlField currentField, boolean checkForStrategy) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (checkForStrategy) {
      return adviserObtainments;
    }

    /*
     * Adding OnSuccess adviser if stepGroup is inside rollback section else adding NextStep adviser for when condition
     * to work.
     */
    if (currentField != null && currentField.getNode() != null) {
      // Check if step is inside RollbackStep
      if (isStepInsideRollback(currentField)) {
        addOnSuccessAdviser(currentField, adviserObtainments);
      } else {
        // Adding NextStep Adviser at last due to giving priority to Failure strategy more. DO NOT CHANGE.
        addNextStepAdviser(currentField, adviserObtainments);
      }
    }

    return adviserObtainments;
  }

  private void addOnSuccessAdviser(YamlField currentField, List<AdviserObtainment> adviserObtainments) {
    if (GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentField)) {
      return;
    }
    YamlField siblingField = currentField.getNode().nextSiblingFromParentArray(
        currentField.getName(), Arrays.asList(YAMLFieldNameConstants.STEP, PARALLEL, STEP_GROUP));
    if (siblingField != null && siblingField.getNode().getUuid() != null) {
      adviserObtainments.add(
          AdviserObtainment.newBuilder()
              .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
              .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                  OnSuccessAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
              .build());
    }
  }

  private void addNextStepAdviser(YamlField currentField, List<AdviserObtainment> adviserObtainments) {
    if (GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentField)) {
      return;
    }
    YamlField siblingField = currentField.getNode().nextSiblingFromParentArray(
        currentField.getName(), Arrays.asList(YAMLFieldNameConstants.STEP, PARALLEL, STEP_GROUP));
    if (siblingField != null && siblingField.getNode().getUuid() != null) {
      adviserObtainments.add(
          AdviserObtainment.newBuilder()
              .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STEP.name()).build())
              .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                  NextStepAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
              .build());
    }
  }

  private boolean isStepInsideRollback(YamlField currentField) {
    return YamlUtils.findParentNode(currentField.getNode(), ROLLBACK_STEPS) != null;
  }
}
