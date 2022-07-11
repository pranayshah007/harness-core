/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.pipeline;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.common.pipeline.PipelineSetupStep;
import io.harness.steps.common.pipeline.PipelineSetupStepParameters;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.utils.TimeoutUtils;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGPipelinePlanCreator extends ChildrenPlanCreator<PipelineInfoConfig> {
  @Override
  public String getStartingNodeId(PipelineInfoConfig field) {
    return field.getUuid();
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, PipelineInfoConfig config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    Map<String, YamlField> dependencies = new HashMap<>();
    YamlField stagesYamlNode = Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField("stages"));
    if (stagesYamlNode.getNode() == null) {
      return responseMap;
    }

    dependencies.put(stagesYamlNode.getNode().getUuid(), stagesYamlNode);
    responseMap.put(stagesYamlNode.getNode().getUuid(),
        PlanCreationResponse.builder().dependencies(DependenciesUtils.toDependenciesProto(dependencies)).build());
    return responseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, PipelineInfoConfig config, List<String> childrenNodeIds) {
    String name = config.getName() != null ? config.getName() : config.getIdentifier();
    YamlNode stagesYamlNode = Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField("stages")).getNode();

    StepParameters stepParameters =
        PipelineSetupStepParameters.getStepParameters(ctx, config, stagesYamlNode.getUuid());

    PlanNodeBuilder planNodeBuilder =
        PlanNode.builder()
            .uuid(config.getUuid())
            .identifier(YAMLFieldNameConstants.PIPELINE)
            .stepType(PipelineSetupStep.STEP_TYPE)
            .group(StepOutcomeGroup.PIPELINE.name())
            .name(name)
            .skipUnresolvedExpressionsCheck(true)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                    .build())
            .skipExpressionChain(false);

    if (!ParameterField.isNull(config.getTimeout())) {
      planNodeBuilder.timeoutObtainment(
          SdkTimeoutObtainment.builder()
              .dimension(AbsoluteTimeoutTrackerFactory.DIMENSION)
              .parameters(AbsoluteSdkTimeoutTrackerParameters.builder()
                              .timeout(TimeoutUtils.getTimeoutParameterFieldString(config.getTimeout()))
                              .build())
              .build());
    }

    return planNodeBuilder.build();
  }

  @Override
  public Class<PipelineInfoConfig> getFieldClass() {
    return PipelineInfoConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.PIPELINE, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }
}
