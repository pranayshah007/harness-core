/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.PlanCreationParentInfoConstants;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
  public void populateParentInfo(PlanCreationContext ctx, Map<String, PlanCreationResponse> childrenResponses) {
    String uuid = ctx.getCurrentField().getUuid();
    boolean isStrategyFieldPresent = StrategyUtils.isStrategyFieldPresent(ctx);
    for (String childKey : childrenResponses.keySet()) {
      PlanCreatorUtilsV1.putParentInfo(childrenResponses.get(childKey), PlanCreationParentInfoConstants.STAGE_ID, uuid);
      if (isStrategyFieldPresent) {
        PlanCreatorUtilsV1.putParentInfo(
            childrenResponses.get(childKey), PlanCreationParentInfoConstants.STRATEGY_ID, uuid);
      }
    }
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
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false));
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
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false));
  }
}
