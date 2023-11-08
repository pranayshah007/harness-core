/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.utils.NGPipelineSettingsConstant.MAX_STAGE_TIMEOUT;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMode;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.PlanNode.PlanNodeBuilder;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.utils.SdkTimeoutObtainmentUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@TargetModule(HarnessModule._882_PMS_SDK_CORE)
public abstract class AbstractStagePlanCreator<T extends AbstractStageNode> extends ChildrenPlanCreator<T> {
  @Inject private KryoSerializer kryoSerializer;

  public abstract Set<String> getSupportedStageTypes();

  public abstract StepType getStepType(T stageNode);

  public abstract SpecParameters getSpecParameters(String childNodeId, PlanCreationContext ctx, T stageNode);

  public abstract Class<T> getFieldClass();

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    Set<String> stageTypes = getSupportedStageTypes();
    if (EmptyPredicate.isEmpty(stageTypes)) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(YAMLFieldNameConstants.STAGE, stageTypes);
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, T config) {
    PlanCreationResponse finalResponse = PlanCreationResponse.builder().build();
    String startingNodeId = getStartingNodeId(config);
    if (EmptyPredicate.isNotEmpty(startingNodeId)) {
      finalResponse.setStartingNodeId(startingNodeId);
    }

    LinkedHashMap<String, PlanCreationResponse> childrenResponses = createPlanForChildrenNodes(ctx, config);
    List<String> childrenNodeIds = new LinkedList<>();
    for (Map.Entry<String, PlanCreationResponse> entry : childrenResponses.entrySet()) {
      finalResponse.merge(entry.getValue());
      childrenNodeIds.add(entry.getKey());
    }

    PlanNode stageNode = createPlanForParentNode(ctx, config, childrenNodeIds);
    PlanNodeBuilder planNodeBuilder =
        stageNode.toBuilder()
            .advisorObtainmentForExecutionMode(ExecutionMode.PIPELINE_ROLLBACK, stageNode.getAdviserObtainments())
            .advisorObtainmentForExecutionMode(
                ExecutionMode.POST_EXECUTION_ROLLBACK, stageNode.getAdviserObtainments());
    ParameterField<Timeout> timeoutParameterField =
        SdkTimeoutObtainmentUtils.getTimeout(config.getTimeout(), ctx.getTimeoutDuration(MAX_STAGE_TIMEOUT.getName()),
            ctx.getFeatureFlagValue(FeatureName.CDS_DISABLE_MAX_TIMEOUT_CONFIG.toString()));
    planNodeBuilder = setStageTimeoutObtainment(timeoutParameterField, planNodeBuilder);
    if ((!ParameterField.isBlank(timeoutParameterField))
        && stageNode.getStepParameters() instanceof StageElementParameters) {
      StageElementParameters stageElementParameters = (StageElementParameters) stageNode.getStepParameters();
      planNodeBuilder.stepParameters(
          stageElementParameters.toBuilder()
              .timeout(ParameterField.createValueField(timeoutParameterField.getValue().getTimeoutString()))
              .build());
    }
    finalResponse.addNode(planNodeBuilder.build());
    finalResponse.setGraphLayoutResponse(getLayoutNodeInfo(ctx, config));
    return finalResponse;
  }

  @Override
  public abstract PlanNode createPlanForParentNode(PlanCreationContext ctx, T stageNode, List<String> childrenNodeIds);

  /**
   * Adds the nextStageAdviser to the given node if it is not the end stage
   */
  protected List<AdviserObtainment> getAdviserObtainmentFromMetaData(YamlField stageField, Dependency dependency) {
    return StrategyUtils.getAdviserObtainments(stageField, kryoSerializer, true, dependency);
  }

  /**
   * Adds a strategy node as a dependency of the stage if present.
   * Please note that strategy uses uuid of the stage node because the stage is using the uuid of strategy field as we
   * want to wrap stage around strategy.
   *
   * @param ctx
   * @param field
   * @param dependenciesNodeMap
   * @param metadataMap
   */
  public void addStrategyFieldDependencyIfPresent(PlanCreationContext ctx, AbstractStageNode field,
      Map<String, YamlField> dependenciesNodeMap, Map<String, ByteString> metadataMap) {
    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, field.getUuid(), field.getIdentifier(),
        field.getName(), dependenciesNodeMap, metadataMap,
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false), true);
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, T config) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField stageYamlField = context.getCurrentField();
    if (StrategyUtils.isWrappedUnderStrategy(context.getCurrentField())) {
      stageYamlFieldMap = StrategyUtils.modifyStageLayoutNodeGraph(stageYamlField);
    }
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }

  /**
   * Adds a strategy node as a dependency of the stage if present.
   * Please note that strategy uses uuid of the stage node because the stage is using the uuid of strategy field as we
   * want to wrap stage around strategy.
   *
   * @param ctx
   * @param field
   * @param metadataMap
   */
  protected void addStrategyFieldDependencyIfPresent(PlanCreationContext ctx, AbstractStageNode field,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, Map<String, ByteString> metadataMap) {
    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, field.getUuid(), field.getName(),
        field.getIdentifier(), planCreationResponseMap, metadataMap,
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false), true);
  }
}
