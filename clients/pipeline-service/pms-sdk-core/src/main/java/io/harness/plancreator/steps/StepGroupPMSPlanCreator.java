/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PARALLEL;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.advisers.retry.RetryAdviserRollbackParameters;
import io.harness.advisers.retry.RetryStepGroupAdvisor;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.govern.Switch;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.steps.stepgroup.StepGroupStep;
import io.harness.steps.common.steps.stepgroup.StepGroupStepParameters;
import io.harness.utils.PlanCreatorUtilsCommon;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.NGFailureActionType;
import io.harness.yaml.core.failurestrategy.retry.RetrySGFailureActionConfig;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
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
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class StepGroupPMSPlanCreator extends ChildrenPlanCreator<StepGroupElementConfig> {
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
        responseMap, new HashMap<>(), getAdviserObtainmentFromMetaData(kryoSerializer, ctx.getCurrentField(), false));

    return responseMap;
  }

  public void addStrategyFieldDependencyIfPresent(KryoSerializer kryoSerializer, PlanCreationContext ctx, String uuid,
      String name, String identifier, LinkedHashMap<String, PlanCreationResponse> responseMap,
      HashMap<Object, Object> objectObjectHashMap, List<AdviserObtainment> adviserObtainmentFromMetaData) {
    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, uuid, name, identifier, responseMap,
        new HashMap<>(), getAdviserObtainmentFromMetaData(kryoSerializer, ctx.getCurrentField(), false));
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, StepGroupElementConfig config, List<String> childrenNodeIds) {
    YamlField stepsField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STEPS));
    config.setIdentifier(StrategyUtils.getIdentifierWithExpression(ctx, config.getIdentifier()));
    config.setName(StrategyUtils.getIdentifierWithExpression(ctx, config.getName()));
    StepParameters stepParameters = StepGroupStepParameters.getStepParameters(config, stepsField.getNode().getUuid());

    final boolean isStepGroupInsideRollback =
        YamlUtils.findParentNode(ctx.getCurrentField().getNode(), ROLLBACK_STEPS) != null;
    return PlanNode.builder()
        .name(config.getName())
        .uuid(StrategyUtils.getSwappedPlanNodeId(ctx, config.getUuid()))
        .identifier(config.getIdentifier())
        .stepType(StepGroupStep.STEP_TYPE)
        .group(StepCategory.STEP_GROUP.name())
        .skipCondition(SkipInfoUtils.getSkipCondition(config.getSkipCondition()))
        // We Should add default when condition as StageFailure if stepGroup is inside rollback
        .whenCondition(isStepGroupInsideRollback ? RunInfoUtils.getRunConditionForRollback(config.getWhen())
                                                 : RunInfoUtils.getRunConditionForStep(config.getWhen()))
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .adviserObtainments(getAdviserObtainmentFromMetaData(
            kryoSerializer, ctx.getCurrentField(), StrategyUtils.isWrappedUnderStrategy(ctx.getCurrentField())))
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

  protected List<AdviserObtainment> getAdviserObtainmentFromMetaData(
      KryoSerializer kryoSerializer, YamlField currentField, boolean checkForStrategy) {
    List<AdviserObtainment> adviserObtainments =
        new ArrayList<>(getAdviserObtainmentForFailureStrategy(kryoSerializer, currentField));
    if (checkForStrategy) {
      return adviserObtainments;
    }

    /*
     * Adding OnSuccess adviser if stepGroup is inside rollback section else adding NextStep adviser for when condition
     * to work.
     */
    if (currentField != null && currentField.getNode() != null) {
      // Check if step is inside RollbackStep
      if (YamlUtils.findParentNode(currentField.getNode(), ROLLBACK_STEPS) != null) {
        addOnSuccessAdviser(currentField, adviserObtainments);
      } else {
        // Adding NextStep Adviser at last due to giving priority to Failure strategy more. DO NOT CHANGE.
        addNextStepAdviser(currentField, adviserObtainments);
      }
    }
    return adviserObtainments;
  }

  public List<AdviserObtainment> getAdviserObtainmentForFailureStrategy(
      KryoSerializer kryoSerializer, YamlField currentField) {
    List<AdviserObtainment> adviserObtainmentList = new ArrayList<>();
    Map<FailureStrategyActionConfig, Collection<FailureType>> actionMap;
    boolean isStepInsideRollback = YamlUtils.findParentNode(currentField.getNode(), ROLLBACK_STEPS) != null;
    actionMap = PlanCreatorUtilsCommon.getPriorityWiseMergedActionMapForFailureStrategiesForStepStageAndStepGroup(
        currentField, STEP_GROUP, isStepInsideRollback);
    return getAdviserObtainments(kryoSerializer, currentField, adviserObtainmentList, actionMap, isStepInsideRollback);
  }

  private List<AdviserObtainment> getAdviserObtainments(KryoSerializer kryoSerializer, YamlField currentField,
      List<AdviserObtainment> adviserObtainmentList,
      Map<FailureStrategyActionConfig, Collection<FailureType>> actionMap, boolean isStepInsideRollback) {
    for (Map.Entry<FailureStrategyActionConfig, Collection<FailureType>> entry : actionMap.entrySet()) {
      FailureStrategyActionConfig action = entry.getKey();
      Set<FailureType> failureTypes = new HashSet<>(entry.getValue());
      NGFailureActionType actionType = action.getType();

      String nextNodeUuid = null;
      YamlField siblingField;
      siblingField = GenericPlanCreatorUtils.obtainNextSiblingField(currentField);

      // Check if step is in parallel section then dont have nextNodeUUid set.
      if (siblingField != null && !GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentField)
          && !StrategyUtils.isWrappedUnderStrategy(currentField)) {
        nextNodeUuid = siblingField.getNode().getUuid();
      }

      if (isStepInsideRollback) {
        if (actionType == NGFailureActionType.STAGE_ROLLBACK || actionType == NGFailureActionType.STEP_GROUP_ROLLBACK) {
          throw new InvalidRequestException("Step inside rollback section cannot have Rollback as failure strategy.");
        }
      }

      AdviserObtainment.Builder adviserObtainmentBuilder = AdviserObtainment.newBuilder();
      adviserForActionType(kryoSerializer, currentField, adviserObtainmentList, action, failureTypes, actionType,
          nextNodeUuid, adviserObtainmentBuilder);
    }
    return adviserObtainmentList;
  }

  private void adviserForActionType(KryoSerializer kryoSerializer, YamlField currentField,
      List<AdviserObtainment> adviserObtainmentList, FailureStrategyActionConfig action, Set<FailureType> failureTypes,
      NGFailureActionType actionType, String nextNodeUuid, AdviserObtainment.Builder adviserObtainmentBuilder) {
    switch (actionType) {
      case RETRY_STEP_GROUP:
        RetrySGFailureActionConfig retrySGAction = (RetrySGFailureActionConfig) action;
        FailureStrategiesUtils.validateRetrySGFailureAction(retrySGAction);
        ParameterField<Integer> retrySGCount = retrySGAction.getSpecConfig().getRetryCount();
        adviserObtainmentList.add(getRetryStepGroupAdviserObtainment(kryoSerializer, failureTypes, nextNodeUuid,
            adviserObtainmentBuilder, retrySGAction, retrySGCount, currentField));
        break;
      default:
        Switch.unhandled(actionType);
    }
  }

  @VisibleForTesting
  AdviserObtainment getRetryStepGroupAdviserObtainment(KryoSerializer kryoSerializer, Set<FailureType> failureTypes,
      String nextNodeUuid, AdviserObtainment.Builder adviserObtainmentBuilder, RetrySGFailureActionConfig retryAction,
      ParameterField<Integer> retryCount, YamlField currentField) {
    return adviserObtainmentBuilder.setType(RetryStepGroupAdvisor.ADVISER_TYPE)
        .setParameters(ByteString.copyFrom(
            kryoSerializer.asBytes(RetryAdviserRollbackParameters.builder()
                                       .applicableFailureTypes(failureTypes)
                                       .nextNodeId(nextNodeUuid)
                                       .retryCount(retryCount.getValue())
                                       .strategyToUuid(PlanCreatorUtilsCommon.getRollbackStrategyMap(currentField))
                                       .waitIntervalList(retryAction.getSpecConfig()
                                                             .getRetryIntervals()
                                                             .getValue()
                                                             .stream()
                                                             .map(s -> (int) TimeoutUtils.getTimeoutInSeconds(s, 0))
                                                             .collect(Collectors.toList()))
                                       .build())))
        .build();
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
}
