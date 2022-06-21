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
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.strategy.StageStrategyUtils;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.matrix.StrategyConstants;
import io.harness.steps.matrix.StrategyMetadata;

import com.google.common.collect.ImmutableMap;
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
  public abstract PlanNode createPlanForParentNode(PlanCreationContext ctx, T stageNode, List<String> childrenNodeIds);

  /**
   * Adds the nextStageAdviser to the given node if it is not the end stage
   */
  protected List<AdviserObtainment> getAdviserObtainmentFromMetaData(YamlField stageField) {
    return StageStrategyUtils.getAdviserObtainments(stageField, kryoSerializer, true);
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
  protected void addStrategyFieldDependencyIfPresent(PlanCreationContext ctx, AbstractStageNode field,
      Map<String, YamlField> dependenciesNodeMap, Map<String, ByteString> metadataMap) {
    YamlField strategyField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY);
    if (strategyField != null) {
      dependenciesNodeMap.put(field.getUuid(), strategyField);
      // This is mandatory because it is the parent's responsibility to pass the nodeId and the childNodeId to the
      // strategy node
      metadataMap.put(StrategyConstants.STRATEGY_METADATA + strategyField.getNode().getUuid(),
          ByteString.copyFrom(
              kryoSerializer.asDeflatedBytes(StrategyMetadata.builder()
                                                 .strategyNodeId(field.getUuid())
                                                 .adviserObtainments(StageStrategyUtils.getAdviserObtainments(
                                                     ctx.getCurrentField(), kryoSerializer, false))
                                                 .childNodeId(strategyField.getNode().getUuid())
                                                 .strategyNodeName(field.getName())
                                                 .strategyNodeIdentifier(field.getIdentifier())
                                                 .build())));
    }
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, T config) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField stageYamlField = context.getCurrentField();
    if (StageStrategyUtils.isWrappedUnderStrategy(context.getCurrentField())) {
      stageYamlFieldMap = StageStrategyUtils.modifyStageLayoutNodeGraph(stageYamlField);
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
    YamlField strategyField = ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.STRATEGY);
    if (strategyField != null) {
      // This is mandatory because it is the parent's responsibility to pass the nodeId and the childNodeId to the
      // strategy node
      metadataMap.put(StrategyConstants.STRATEGY_METADATA + strategyField.getNode().getUuid(),
          ByteString.copyFrom(
              kryoSerializer.asDeflatedBytes(StrategyMetadata.builder()
                                                 .strategyNodeId(field.getUuid())
                                                 .adviserObtainments(StageStrategyUtils.getAdviserObtainments(
                                                     ctx.getCurrentField(), kryoSerializer, false))
                                                 .childNodeId(strategyField.getNode().getUuid())
                                                 .strategyNodeName(field.getName())
                                                 .strategyNodeIdentifier(field.getIdentifier())
                                                 .build())));
      planCreationResponseMap.put(field.getUuid(),
          PlanCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(ImmutableMap.of(field.getUuid(), strategyField))
                                .toBuilder()
                                .putDependencyMetadata(
                                    field.getUuid(), Dependency.newBuilder().putAllMetadata(metadataMap).build())
                                .build())
              .build());
    }
  }
}
