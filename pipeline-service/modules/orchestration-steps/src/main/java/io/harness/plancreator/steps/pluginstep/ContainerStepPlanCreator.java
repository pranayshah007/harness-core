/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.advisers.rollback.RollbackStrategy;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.plancreator.steps.internal.PMSStepInfo;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
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
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.common.steps.stepgroup.StepGroupStep;
import io.harness.steps.common.steps.stepgroup.StepGroupStepParameters;
import io.harness.steps.plugin.ContainerStepNode;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.PlanCreatorUtilsCommon;
import io.harness.utils.TimeoutUtils;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepPlanCreator extends ChildrenPlanCreator<ContainerStepNode> {
  public static final String CONTAINER_STEP_GROUP = "Container Step Group";
  @Inject KryoSerializer kryoSerializer;

  @Override
  public Class<ContainerStepNode> getFieldClass() {
    return ContainerStepNode.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(STEP, Collections.singleton(StepSpecTypeConstants.CONTAINER_STEP));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, ContainerStepNode config) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    YamlField containerStep = ctx.getCurrentField();

    String initStepNodeId = "init-" + containerStep.getNode().getUuid();
    String runStepNodeId = "run-" + containerStep.getNode().getUuid();

    ByteString advisorParametersInitStep = ByteString.copyFrom(
        kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(runStepNodeId).build()));

    PlanNode runStepPlanNode =
        RunContainerStepPlanCreater.createPlanForField(runStepNodeId, getStepParameters(config, ctx));
    PlanNode initPlanNode = InitContainerStepPlanCreater.createPlanForField(
        initStepNodeId, getStepParameters(config, ctx), advisorParametersInitStep);

    planCreationResponseMap.put(
        initPlanNode.getUuid(), PlanCreationResponse.builder().node(initPlanNode.getUuid(), initPlanNode).build());
    planCreationResponseMap.put(runStepPlanNode.getUuid(),
        PlanCreationResponse.builder().node(runStepPlanNode.getUuid(), runStepPlanNode).build());

    addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, config.getUuid(), config.getName(), config.getIdentifier(),
        planCreationResponseMap, new HashMap<>(), getAdviserObtainmentFromMetaData(ctx.getCurrentField()));

    return planCreationResponseMap;
  }

  public void addStrategyFieldDependencyIfPresent(KryoSerializer kryoSerializer, PlanCreationContext ctx, String uuid,
      String name, String identifier, LinkedHashMap<String, PlanCreationResponse> responseMap,
      HashMap<Object, Object> objectObjectHashMap, List<AdviserObtainment> adviserObtainmentFromMetaData) {
    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, uuid, name, identifier, responseMap,
        new HashMap<>(), getAdviserObtainmentFromMetaData(ctx.getCurrentField()));
  }
  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, ContainerStepNode config, List<String> childrenNodeIds) {
    config.setIdentifier(StrategyUtils.getIdentifierWithExpression(ctx, config.getIdentifier()));
    config.setName(CONTAINER_STEP_GROUP);

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
        .adviserObtainments(getAdviserObtainmentFromMetaData(ctx.getCurrentField()))
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

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ContainerStepNode field) {
    field.getContainerStepInfo().setIdentifier(field.getIdentifier());
    field.getContainerStepInfo().setName(field.getName());
    return super.createPlanForField(ctx, field);
  }

  @Override
  public String getExecutionInputTemplateAndModifyYamlField(YamlField yamlField) {
    return super.getExecutionInputTemplateAndModifyYamlField(yamlField);
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V0);
  }

  protected List<AdviserObtainment> getAdviserObtainmentFromMetaData(YamlField currentField) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    addNextStepAdviser(currentField, adviserObtainments);
    return adviserObtainments;
  }

  private void addNextStepAdviser(YamlField currentField, List<AdviserObtainment> adviserObtainments) {
    YamlField siblingField = currentField.getNode().nextSiblingFromParentArray(
        currentField.getName(), Collections.singletonList(YAMLFieldNameConstants.STEP));
    if (siblingField != null && siblingField.getNode().getUuid() != null) {
      adviserObtainments.add(
          AdviserObtainment.newBuilder()
              .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STEP.name()).build())
              .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                  NextStepAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
              .build());
    }
  }

  protected StepParameters getStepParameters(ContainerStepNode stepElement, PlanCreationContext ctx) {
    if (stepElement.getStepSpecType() instanceof WithStepElementParameters) {
      stepElement.setTimeout(TimeoutUtils.getTimeout(stepElement.getTimeout()));
      return ((PMSStepInfo) stepElement.getStepSpecType())
          .getStepParameters(stepElement,
              PlanCreatorUtilsCommon.getRollbackParameters(
                  ctx.getCurrentField(), Collections.emptySet(), RollbackStrategy.UNKNOWN),
              ctx);
    }
    stepElement.setTimeout(TimeoutUtils.getTimeout(stepElement.getTimeout()));
    stepElement.getContainerStepInfo().setIdentifier(stepElement.getIdentifier());
    stepElement.getContainerStepInfo().setName(stepElement.getName());
    return stepElement.getStepSpecType().getStepParameters();
  }
}
