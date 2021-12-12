package io.harness.ci.plan.creator.step;

import com.google.protobuf.ByteString;
import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.advisers.rollback.OnFailRollbackParameters.OnFailRollbackParametersBuilder;
import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.CIStepInfo;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.PipelineServiceUtilPlanCreationConstants;
import io.harness.plancreator.steps.AbstractStepPlanCreator;
import io.harness.plancreator.steps.GenericPlanCreatorUtils;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.TimeoutUtils;
import io.harness.when.utils.RunInfoUtils;
import io.harness.yaml.core.timeout.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;

@OwnedBy(HarnessTeam.CI)
public abstract class CIPMSStepPlanCreatorV2<T extends CIAbstractStepNode> extends AbstractStepPlanCreator<T> {
  public abstract Set<String> getSupportedStepTypes();

  @Override public abstract Class<T> getFieldClass();

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, T stepElement) {
    boolean isStepInsideRollback = false;
    if (YamlUtils.findParentNode(ctx.getCurrentField().getNode(), ROLLBACK_STEPS) != null) {
      isStepInsideRollback = true;
    }

    List<AdviserObtainment> adviserObtainmentFromMetaData = getAdviserObtainmentFromMetaData(ctx.getCurrentField());

    StepParameters stepParameters = getStepParameters(ctx, stepElement);
    PlanNode stepPlanNode =
            PlanNode.builder()
                    .uuid(ctx.getCurrentField().getNode().getUuid())
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
    return PlanCreationResponse.builder().node(stepPlanNode.getUuid(), stepPlanNode).build();
  }

  protected List<AdviserObtainment> getAdviserObtainmentFromMetaData(YamlField currentField) {
    boolean isStepInsideRollback = false;
    if (YamlUtils.findParentNode(currentField.getNode(), ROLLBACK_STEPS) != null) {
      isStepInsideRollback = true;
    }

    List<AdviserObtainment> adviserObtainmentList = new ArrayList<>();

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
      if (GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentField)) {
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
      if (GenericPlanCreatorUtils.checkIfStepIsInParallelSection(currentField)) {
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
      return ((CIStepInfo) stepElement.getStepSpecType())
              .getStepParameters(stepElement,
                      getRollbackParameters(ctx.getCurrentField(), Collections.emptySet(), RollbackStrategy.UNKNOWN));
    }

    return stepElement.getStepSpecType().getStepParameters();
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
    rollbackStrategyStringMap.put(RollbackStrategy.STAGE_ROLLBACK,
            stageNodeId + PipelineServiceUtilPlanCreationConstants.COMBINED_ROLLBACK_ID_SUFFIX);
    rollbackStrategyStringMap.put(
            RollbackStrategy.STEP_GROUP_ROLLBACK, GenericPlanCreatorUtils.getStepGroupRollbackStepsNodeId(currentField));
    return rollbackStrategyStringMap;
  }
}