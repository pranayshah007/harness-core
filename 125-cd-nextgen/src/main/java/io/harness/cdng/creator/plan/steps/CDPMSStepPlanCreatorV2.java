/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.pms.yaml.YAMLFieldNameConstants.EXECUTION;
import static io.harness.pms.yaml.YAMLFieldNameConstants.FAILURE_STRATEGIES;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PARALLEL;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.SPEC;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP_GROUP;

import io.harness.advisers.manualIntervention.ManualInterventionAdviserRollbackParameters;
import io.harness.advisers.manualIntervention.ManualInterventionAdviserWithRollback;
import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.advisers.retry.RetryAdviserRollbackParameters;
import io.harness.advisers.retry.RetryAdviserWithRollback;
import io.harness.advisers.rollback.OnFailRollbackAdviser;
import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.advisers.rollback.OnFailRollbackParameters.OnFailRollbackParametersBuilder;
import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.cdng.pipeline.CdAbstractStepNode;
import io.harness.cdng.pipeline.PipelineInfrastructure.PipelineInfrastructureKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.govern.Switch;
import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.plancreator.steps.AbstractStepPlanCreator;
import io.harness.plancreator.steps.FailureStrategiesUtils;
import io.harness.plancreator.steps.GenericPlanCreatorUtils;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.plancreator.strategy.StageStrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.abort.OnAbortAdviser;
import io.harness.pms.sdk.core.adviser.abort.OnAbortAdviserParameters;
import io.harness.pms.sdk.core.adviser.ignore.IgnoreAdviser;
import io.harness.pms.sdk.core.adviser.ignore.IgnoreAdviserParameters;
import io.harness.pms.sdk.core.adviser.marksuccess.OnMarkSuccessAdviser;
import io.harness.pms.sdk.core.adviser.marksuccess.OnMarkSuccessAdviserParameters;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.core.failurestrategy.FailureStrategyActionConfig;
import io.harness.yaml.core.failurestrategy.FailureStrategyConfig;
import io.harness.yaml.core.failurestrategy.NGFailureActionType;
import io.harness.yaml.core.failurestrategy.manualintervention.ManualInterventionFailureActionConfig;
import io.harness.yaml.core.failurestrategy.retry.RetryFailureActionConfig;
import io.harness.yaml.core.timeout.Timeout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDC)
public abstract class CDPMSStepPlanCreatorV2<T extends CdAbstractStepNode> extends AbstractStepPlanCreator<T> {
  public abstract Set<String> getSupportedStepTypes();

  @Override public abstract Class<T> getFieldClass();

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, T stepElement) {
    boolean isStepInsideRollback = false;
    if (YamlUtils.findParentNode(ctx.getCurrentField().getNode(), ROLLBACK_STEPS) != null) {
      isStepInsideRollback = true;
    }

    List<AdviserObtainment> adviserObtainmentFromMetaData = getAdviserObtainmentFromMetaData(ctx.getCurrentField());
    Map<String, YamlField> dependenciesNodeMap = new HashMap<>();
    Map<String, ByteString> metadataMap = new HashMap<>();
    StepParameters stepParameters = getStepParameters(ctx, stepElement);
    // Adds the strategy field as dependency if present
    addStrategyFieldDependencyIfPresent(ctx, stepElement, dependenciesNodeMap, metadataMap);
    // We are swapping the uuid with strategy node if present.
    PlanNode stepPlanNode =
        PlanNode.builder()
            .uuid(StageStrategyUtils.getSwappedPlanNodeId(ctx, stepElement))
            .name(getName(stepElement))
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
                                                : RunInfoUtils.getRunCondition(stepElement.getWhen()))
            .timeoutObtainment(
                SdkTimeoutObtainment.builder()
                    .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
                    .parameters(
                        AbsoluteSdkTimeoutTrackerParameters.builder().timeout(getTimeoutString(stepElement)).build())
                    .build())
            .skipUnresolvedExpressionsCheck(stepElement.getStepSpecType().skipUnresolvedExpressionsCheck())
            .build();
    // Add a dependency of strategy if present
    return PlanCreationResponse.builder().planNode(stepPlanNode).dependencies(DependenciesUtils.toDependenciesProto(dependenciesNodeMap)
            .toBuilder()
            .putDependencyMetadata(stepElement.getUuid(), Dependency.newBuilder().putAllMetadata(metadataMap).build())
            .build()).build();
  }

  protected List<AdviserObtainment> getAdviserObtainmentFromMetaData(YamlField currentField) {
    boolean isStepInsideRollback = false;
    if (YamlUtils.findParentNode(currentField.getNode(), ROLLBACK_STEPS) != null) {
      isStepInsideRollback = true;
    }

    // Adding adviser obtainment list from the failure strategy.
    List<AdviserObtainment> adviserObtainmentList =
        new ArrayList<>(getAdviserObtainmentForFailureStrategy(currentField, isStepInsideRollback));

    /*
     * Adding OnSuccess adviser if step is inside rollback section else adding NextStep adviser for when condition to
     * work.
     */
    if (isStepInsideRollback) {
      AdviserObtainment onSuccessAdviserObtainment = getOnSuccessAdviserObtainment(currentField);
      if (onSuccessAdviserObtainment != null) {
        adviserObtainmentList.add(onSuccessAdviserObtainment);
      }
    } else {
      // Always add nextStep adviser at last, as its priority is less than, Do not change the order.
      AdviserObtainment nextStepAdviserObtainment = getNextStepAdviserObtainment(currentField);
      if (nextStepAdviserObtainment != null) {
        adviserObtainmentList.add(nextStepAdviserObtainment);
      }
    }

    return adviserObtainmentList;
  }
  private AdviserObtainment getNextStepAdviserObtainment(YamlField currentField) {
    if (currentField != null && currentField.getNode() != null) {
      //If it is wrapped under parallel or strategy then we should not add next step as the next step adviser would be on strategy node
      if (GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentField) || StageStrategyUtils.isWrappedUnderStrategy(currentField)) {
        return null;
      }
      YamlField siblingField = GenericPlanCreatorUtils.obtainNextSiblingField(currentField);
      if (siblingField != null && siblingField.getNode().getUuid() != null) {
        return AdviserObtainment.newBuilder()
            .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STEP.name()).build())
            .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                NextStepAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
            .build();
      }
    }
    return null;
  }

  private AdviserObtainment getOnSuccessAdviserObtainment(YamlField currentField) {
    if (currentField != null && currentField.getNode() != null) {
      //If it is wrapped under parallel or strategy then we should not add next step as the next step adviser would be
      // on strategy node or parallel node
      if (GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentField) || StageStrategyUtils.isWrappedUnderStrategy(currentField)) {
        return null;
      }
      YamlField siblingField = GenericPlanCreatorUtils.obtainNextSiblingField(currentField);
      if (siblingField != null && siblingField.getNode().getUuid() != null) {
        return AdviserObtainment.newBuilder()
            .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
            .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                OnSuccessAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
            .build();
      }
    }
    return null;
  }

  protected StepParameters getStepParameters(PlanCreationContext ctx, T stepElement) {
    if (stepElement.getStepSpecType() instanceof WithStepElementParameters) {
      stepElement.setTimeout(TimeoutUtils.getTimeout(stepElement.getTimeout()));
      return ((CDStepInfo) stepElement.getStepSpecType())
          .getStepParameters(stepElement,
              getRollbackParameters(ctx.getCurrentField(), Collections.emptySet(), RollbackStrategy.UNKNOWN), ctx);
    }

    return stepElement.getStepSpecType().getStepParameters();
  }

  protected AdviserObtainment getRetryAdviserObtainment(Set<FailureType> failureTypes, String nextNodeUuid,
      AdviserObtainment.Builder adviserObtainmentBuilder, RetryFailureActionConfig retryAction,
      ParameterField<Integer> retryCount, FailureStrategyActionConfig actionUnderRetry, YamlField currentField) {
    return adviserObtainmentBuilder.setType(RetryAdviserWithRollback.ADVISER_TYPE)
        .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
            RetryAdviserRollbackParameters.builder()
                .applicableFailureTypes(failureTypes)
                .nextNodeId(nextNodeUuid)
                .repairActionCodeAfterRetry(GenericPlanCreatorUtils.toRepairAction(actionUnderRetry))
                .retryCount(retryCount.getValue())
                .strategyToUuid(getRollbackStrategyMap(currentField))
                .waitIntervalList(retryAction.getSpecConfig()
                                      .getRetryIntervals()
                                      .getValue()
                                      .stream()
                                      .map(s -> (int) TimeoutUtils.getTimeoutInSeconds(s, 0))
                                      .collect(Collectors.toList()))
                .build())))
        .build();
  }

  protected AdviserObtainment getManualInterventionAdviserObtainment(Set<FailureType> failureTypes,
      AdviserObtainment.Builder adviserObtainmentBuilder, ManualInterventionFailureActionConfig actionConfig,
      FailureStrategyActionConfig actionUnderManualIntervention) {
    return adviserObtainmentBuilder.setType(ManualInterventionAdviserWithRollback.ADVISER_TYPE)
        .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
            ManualInterventionAdviserRollbackParameters.builder()
                .applicableFailureTypes(failureTypes)
                .timeoutAction(GenericPlanCreatorUtils.toRepairAction(actionUnderManualIntervention))
                .timeout((int) TimeoutUtils.getTimeoutInSeconds(actionConfig.getSpecConfig().getTimeout(), 0))
                .build())))
        .build();
  }

  private List<FailureStrategyConfig> getFieldFailureStrategies(
      YamlField currentField, String fieldName, boolean isStepInsideRollback) {
    YamlNode fieldNode = YamlUtils.getGivenYamlNodeFromParentPath(currentField.getNode(), fieldName);
    if (isStepInsideRollback && fieldNode != null) {
      // Check if found fieldNode is within rollbackSteps section
      YamlNode rollbackNode = YamlUtils.findParentNode(fieldNode, ROLLBACK_STEPS);
      if (rollbackNode == null) {
        return Collections.emptyList();
      }
    }
    if (fieldNode != null) {
      return getFailureStrategies(fieldNode);
    }
    return Collections.emptyList();
  }

  private List<FailureStrategyConfig> getFailureStrategies(YamlNode node) {
    YamlField failureStrategy = node.getField(FAILURE_STRATEGIES);
    List<FailureStrategyConfig> failureStrategyConfigs = null;

    try {
      if (failureStrategy != null) {
        failureStrategyConfigs =
            YamlUtils.read(failureStrategy.getNode().toString(), new TypeReference<List<FailureStrategyConfig>>() {});
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Invalid yaml", e);
    }
    return failureStrategyConfigs;
  }

  protected List<AdviserObtainment> getAdviserObtainmentForFailureStrategy(
      YamlField currentField, boolean isStepInsideRollback) {
    List<AdviserObtainment> adviserObtainmentList = new ArrayList<>();
    List<FailureStrategyConfig> stageFailureStrategies =
        getFieldFailureStrategies(currentField, STAGE, isStepInsideRollback);
    List<FailureStrategyConfig> stepGroupFailureStrategies =
        getFieldFailureStrategies(currentField, STEP_GROUP, isStepInsideRollback);
    List<FailureStrategyConfig> stepFailureStrategies = getFailureStrategies(currentField.getNode());

    Map<FailureStrategyActionConfig, Collection<FailureType>> actionMap;
    FailureStrategiesUtils.priorityMergeFailureStrategies(
        stepFailureStrategies, stepGroupFailureStrategies, stageFailureStrategies);

    actionMap = FailureStrategiesUtils.priorityMergeFailureStrategies(
        stepFailureStrategies, stepGroupFailureStrategies, stageFailureStrategies);

    for (Map.Entry<FailureStrategyActionConfig, Collection<FailureType>> entry : actionMap.entrySet()) {
      FailureStrategyActionConfig action = entry.getKey();
      Set<FailureType> failureTypes = new HashSet<>(entry.getValue());
      NGFailureActionType actionType = action.getType();

      String nextNodeUuid = null;
      YamlField siblingField = GenericPlanCreatorUtils.obtainNextSiblingField(currentField);
      // Check if step is in parallel section or inside strategy section then dont have nextNodeUUid set.
      if (siblingField != null && !GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentField) && !StageStrategyUtils.isWrappedUnderStrategy(currentField)) {
        nextNodeUuid = siblingField.getNode().getUuid();
      }

      AdviserObtainment.Builder adviserObtainmentBuilder = AdviserObtainment.newBuilder();
      if (isStepInsideRollback) {
        if (actionType == NGFailureActionType.STAGE_ROLLBACK || actionType == NGFailureActionType.STEP_GROUP_ROLLBACK) {
          throw new InvalidRequestException("Step inside rollback section cannot have Rollback as failure strategy.");
        }
      }

      switch (actionType) {
        case IGNORE:
          adviserObtainmentList.add(
              adviserObtainmentBuilder.setType(IgnoreAdviser.ADVISER_TYPE)
                  .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(IgnoreAdviserParameters.builder()
                                                                                .applicableFailureTypes(failureTypes)
                                                                                .nextNodeId(nextNodeUuid)
                                                                                .build())))
                  .build());
          break;
        case RETRY:
          RetryFailureActionConfig retryAction = (RetryFailureActionConfig) action;
          FailureStrategiesUtils.validateRetryFailureAction(retryAction);
          ParameterField<Integer> retryCount = retryAction.getSpecConfig().getRetryCount();
          FailureStrategyActionConfig actionUnderRetry = retryAction.getSpecConfig().getOnRetryFailure().getAction();
          adviserObtainmentList.add(getRetryAdviserObtainment(failureTypes, nextNodeUuid, adviserObtainmentBuilder,
              retryAction, retryCount, actionUnderRetry, currentField));
          break;
        case MARK_AS_SUCCESS:
          adviserObtainmentList.add(
              adviserObtainmentBuilder.setType(OnMarkSuccessAdviser.ADVISER_TYPE)
                  .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(OnMarkSuccessAdviserParameters.builder()
                                                                                .applicableFailureTypes(failureTypes)
                                                                                .nextNodeId(nextNodeUuid)
                                                                                .build())))
                  .build());

          break;
        case ABORT:
          adviserObtainmentList.add(
              adviserObtainmentBuilder.setType(OnAbortAdviser.ADVISER_TYPE)
                  .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                      OnAbortAdviserParameters.builder().applicableFailureTypes(failureTypes).build())))
                  .build());
          break;
        case STAGE_ROLLBACK:
          OnFailRollbackParameters rollbackParameters =
              getRollbackParameters(currentField, failureTypes, RollbackStrategy.STAGE_ROLLBACK);
          adviserObtainmentList.add(adviserObtainmentBuilder.setType(OnFailRollbackAdviser.ADVISER_TYPE)
                                        .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(rollbackParameters)))
                                        .build());
          break;
        case MANUAL_INTERVENTION:
          ManualInterventionFailureActionConfig actionConfig = (ManualInterventionFailureActionConfig) action;
          FailureStrategiesUtils.validateManualInterventionFailureAction(actionConfig);
          FailureStrategyActionConfig actionUnderManualIntervention =
              actionConfig.getSpecConfig().getOnTimeout().getAction();
          adviserObtainmentList.add(getManualInterventionAdviserObtainment(
              failureTypes, adviserObtainmentBuilder, actionConfig, actionUnderManualIntervention));
          break;
        default:
          Switch.unhandled(actionType);
      }
    }
    return adviserObtainmentList;
  }

  protected String getName(T stepElement) {
    String nodeName;
    if (EmptyPredicate.isEmpty(stepElement.getName())) {
      nodeName = stepElement.getIdentifier();
    } else {
      nodeName = stepElement.getName();
    }
    return nodeName;
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

  protected OnFailRollbackParameters getRollbackParameters(
      YamlField currentField, Set<FailureType> failureTypes, RollbackStrategy rollbackStrategy) {
    OnFailRollbackParametersBuilder rollbackParametersBuilder = OnFailRollbackParameters.builder();
    rollbackParametersBuilder.applicableFailureTypes(failureTypes);
    rollbackParametersBuilder.strategy(rollbackStrategy);
    rollbackParametersBuilder.strategyToUuid(getRollbackStrategyMap(currentField));
    return rollbackParametersBuilder.build();
  }

  protected Map<RollbackStrategy, String> getRollbackStrategyMap(YamlField currentField) {
    String stageNodeId = GenericPlanCreatorUtils.getStageNodeId(currentField);
    Map<RollbackStrategy, String> rollbackStrategyStringMap = new HashMap<>();
    rollbackStrategyStringMap.put(
        RollbackStrategy.STAGE_ROLLBACK, stageNodeId + NGCommonUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX);
    rollbackStrategyStringMap.put(
        RollbackStrategy.STEP_GROUP_ROLLBACK, GenericPlanCreatorUtils.getStepGroupRollbackStepsNodeId(currentField));
    return rollbackStrategyStringMap;
  }

  protected String getExecutionStepFqn(YamlField currentField, String stepNodeType) {
    String stepFqn = null;
    YamlNode execution = YamlUtils.findParentNode(currentField.getNode(), EXECUTION);
    List<YamlNode> steps = execution.getField(STEPS).getNode().asArray();
    for (YamlNode stepsNode : steps) {
      YamlNode stepGroup = getStepGroup(stepsNode);
      stepFqn = stepGroup != null ? getStepFqnFromStepGroup(stepGroup, stepNodeType)
                                  : getStepFqnFromStepNode(stepsNode, stepNodeType);
      if (stepFqn != null) {
        return stepFqn;
      }
    }

    return stepFqn;
  }

  protected List<YamlNode> findStepsBeforeCurrentStep(YamlField currentStep, Predicate<YamlNode> filter) {
    YamlNode stages = YamlUtils.findParentNode(currentStep.getNode(), YAMLFieldNameConstants.STAGES);
    YamlNode currentStage = YamlUtils.findParentNode(currentStep.getNode(), STAGE);
    if (stages == null || currentStage == null) {
      return Collections.emptyList();
    }

    List<YamlNode> steps = new ArrayList<>();
    String currentStageIdentifier = currentStage.getIdentifier();
    for (YamlNode stageNode : stages.asArray()) {
      YamlNode stage = stageNode.getField(STAGE).getNode();
      if (stage == null) {
        continue;
      }

      steps.addAll(findExecutionStepsFromStage(stage, filter));
      steps.addAll(findProvisionerStepsFromStage(stage, filter));

      if (currentStageIdentifier.equals(stage.getIdentifier())) {
        break;
      }
    }

    return steps;
  }

  private String getStepFqnFromStepGroup(YamlNode stepGroup, String stepNodeType) {
    String stepFqn = null;
    List<YamlNode> stepsInsideStepGroup = stepGroup.getField(STEPS).getNode().asArray();
    for (YamlNode stepsNodeInsideStepGroup : stepsInsideStepGroup) {
      YamlNode parallelStepNode = getParallelStep(stepsNodeInsideStepGroup);
      stepFqn = parallelStepNode != null ? getStepFqnFromParallelNode(parallelStepNode, stepNodeType)
                                         : getFqnFromStepNode(stepsNodeInsideStepGroup, stepNodeType);
      if (stepFqn != null) {
        return stepFqn;
      }
    }

    return stepFqn;
  }

  private String getStepFqnFromStepNode(YamlNode stepsNode, String stepNodeType) {
    YamlNode parallelStepNode = getParallelStep(stepsNode);
    return parallelStepNode != null ? getStepFqnFromParallelNode(parallelStepNode, stepNodeType)
                                    : getFqnFromStepNode(stepsNode, stepNodeType);
  }

  private String getStepFqnFromParallelNode(YamlNode parallelStepNode, String stepNodeType) {
    String stepFqn = null;
    List<YamlNode> stepsInParallelNode = parallelStepNode.asArray();
    for (YamlNode stepInParallelNode : stepsInParallelNode) {
      stepFqn = getFqnFromStepNode(stepInParallelNode, stepNodeType);
      if (stepFqn != null) {
        return stepFqn;
      }
    }

    return stepFqn;
  }

  private String getFqnFromStepNode(YamlNode stepsNode, String stepNodeType) {
    YamlNode stepNode = stepsNode.getField(STEP).getNode();
    if (stepNodeType.equals(stepNode.getType())) {
      return YamlUtils.getFullyQualifiedName(stepNode);
    }

    return null;
  }

  private YamlNode getStepGroup(YamlNode stepsNode) {
    try {
      return stepsNode.getField(STEP_GROUP).getNode();
    } catch (Exception ex) {
      return null;
    }
  }

  private YamlNode getParallelStep(YamlNode stepsNode) {
    try {
      return stepsNode.getField(PARALLEL).getNode();
    } catch (Exception ex) {
      return null;
    }
  }

  private List<YamlNode> findExecutionStepsFromStage(YamlNode stageNode, Predicate<YamlNode> filter) {
    Optional<YamlField> stepsField = Optional.ofNullable(stageNode.getField(SPEC))
                                         .map(specField -> specField.getNode().getField(EXECUTION))
                                         .map(executionField -> executionField.getNode().getField(STEPS));

    return stepsField.map(yamlField -> findStepNodesFromStepsField(yamlField, filter)).orElse(Collections.emptyList());
  }

  private List<YamlNode> findProvisionerStepsFromStage(YamlNode stageNode, Predicate<YamlNode> filter) {
    Optional<YamlField> stepsField =
        Optional.ofNullable(stageNode.getField(SPEC))
            .map(specField -> specField.getNode().getField(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE))
            .map(YamlField::getNode)
            .flatMap(infraNode -> {
              if (infraNode.getField(PipelineInfrastructureKeys.useFromStage) != null) {
                return Optional.empty();
              }

              return Optional.ofNullable(infraNode.getField(PipelineInfrastructureKeys.infrastructureDefinition));
            })
            .map(infraDefField -> infraDefField.getNode().getField(YAMLFieldNameConstants.PROVISIONER))
            .map(provisionerField -> provisionerField.getNode().getField(STEPS));

    return stepsField.map(yamlField -> findStepNodesFromStepsField(yamlField, filter)).orElse(Collections.emptyList());
  }

  private List<YamlNode> findStepNodesFromStepsField(YamlField stepsField, Predicate<YamlNode> filter) {
    List<YamlNode> steps = new ArrayList<>();
    for (YamlNode stepNode : stepsField.getNode().asArray()) {
      YamlNode stepGroupNode = getStepGroup(stepNode);
      if (stepGroupNode != null) {
        addStepsFromStepGroup(stepGroupNode, filter, steps);
      } else {
        addStepsFromParallelOrInlineStepNode(stepNode, filter, steps);
      }
    }

    return steps;
  }

  private void addStepsFromStepGroup(YamlNode stepGroupNode, Predicate<YamlNode> filter, List<YamlNode> result) {
    YamlField groupStepsField = stepGroupNode.getField(STEPS);
    if (groupStepsField == null) {
      return;
    }

    for (YamlNode stepNode : groupStepsField.getNode().asArray()) {
      addStepsFromParallelOrInlineStepNode(stepNode, filter, result);
    }
  }

  private void addStepsFromParallelOrInlineStepNode(
      YamlNode stepNode, Predicate<YamlNode> filter, List<YamlNode> result) {
    YamlNode parallelStepNode = getParallelStep(stepNode);
    if (parallelStepNode != null) {
      for (YamlNode stepInParallelNode : parallelStepNode.asArray()) {
        addStepsFromStepNode(stepInParallelNode, filter, result);
      }
      return;
    }

    addStepsFromStepNode(stepNode, filter, result);
  }

  private void addStepsFromStepNode(YamlNode stepNode, Predicate<YamlNode> filter, List<YamlNode> result) {
    YamlField stepNodeField = stepNode.getField(STEP);
    if (stepNodeField == null) {
      return;
    }

    if (filter.test(stepNodeField.getNode())) {
      result.add(stepNodeField.getNode());
    }
  }
}
