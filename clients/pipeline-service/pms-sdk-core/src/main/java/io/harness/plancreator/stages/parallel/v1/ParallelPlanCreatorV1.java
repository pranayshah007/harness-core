/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages.parallel.v1;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.NGSectionStep;
import io.harness.steps.common.NGSectionStepParameters;

import com.cronutils.utils.Preconditions;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class ParallelPlanCreatorV1 extends ChildrenPlanCreator<YamlField> {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap("parallel", Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField config) {
    YamlField specNode = Preconditions.checkNotNull(config.getNode().getField(YAMLFieldNameConstants.SPEC));
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    if (specNode.getNode() == null) {
      return responseMap;
    }
    Map<String, YamlField> dependencies = new HashMap<>();
    dependencies.put(specNode.getNode().getUuid(), specNode);
    responseMap.put(specNode.getNode().getUuid(),
        PlanCreationResponse.builder()
            .dependencies(DependenciesUtils.toDependenciesProto(dependencies)
                              .toBuilder()
                              .putDependencyMetadata(specNode.getUuid(),
                                  Dependency.newBuilder()
                                      .setNodeMetadata(HarnessStruct.newBuilder().putData(
                                          PlanCreatorConstants.IS_INSIDE_PARALLEL_NODE,
                                          HarnessValue.newBuilder().setBoolValue(true).build()))
                                      .build())
                              .build())
            .build());

    return responseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    validateUnsupportedProperties(config);
    return PlanNode.builder()
        .uuid(config.getUuid())
        .name(config.getNodeName())
        .identifier(config.getId())
        .stepType(NGSectionStep.STEP_TYPE)
        .stepParameters(NGSectionStepParameters.builder()
                            .childNodeId(childrenNodeIds.get(0))
                            .name(config.getNodeName())
                            .id(config.getId())
                            .logMessage("Parallel node")
                            .build())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .adviserObtainments(getAdviserObtainmentFromMetaData(ctx, config))
        .skipExpressionChain(false)
        .build();
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext ctx, YamlField config) {
    YamlNode specNode = config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode();
    if (specNode.getField(YAMLFieldNameConstants.STAGES) == null) {
      return GraphLayoutResponse.builder().build();
    }
    List<YamlField> children = specNode.getField(YAMLFieldNameConstants.STAGES)
                                   .getNode()
                                   .asArray()
                                   .stream()
                                   .map(YamlField::new)
                                   .collect(Collectors.toList());
    if (children.isEmpty()) {
      return GraphLayoutResponse.builder().build();
    }
    String nextNodeId = PlanCreatorUtilsV1.getNextNodeUuid(kryoSerializer, ctx.getDependency());
    List<String> childrenUuids =
        children.stream().map(YamlField::getNode).map(YamlNode::getUuid).collect(Collectors.toList());
    EdgeLayoutList.Builder stagesEdgesBuilder = EdgeLayoutList.newBuilder().addAllCurrentNodeChildren(childrenUuids);
    if (nextNodeId != null) {
      stagesEdgesBuilder.addNextIds(nextNodeId);
    }
    Map<String, GraphLayoutNode> layoutNodeMap = children.stream().collect(Collectors.toMap(stageField
        -> stageField.getNode().getUuid(),
        stageField
        -> GraphLayoutNode.newBuilder()
               .setNodeUUID(stageField.getNode().getUuid())
               .setNodeGroup(StepOutcomeGroup.STAGE.name())
               .setName(stageField.getNodeName())
               .setNodeType(stageField.getNode().getType())
               .setNodeIdentifier(stageField.getId())
               .setEdgeLayoutList(EdgeLayoutList.newBuilder().build())
               .build()));
    GraphLayoutNode parallelNode = GraphLayoutNode.newBuilder()
                                       .setNodeUUID(config.getUuid())
                                       .setNodeType(YAMLFieldNameConstants.PARALLEL)
                                       .setNodeGroup(StepOutcomeGroup.STAGE.name())
                                       .setNodeIdentifier(YAMLFieldNameConstants.PARALLEL + config.getNode().getUuid())
                                       .setEdgeLayoutList(stagesEdgesBuilder.build())
                                       .build();
    layoutNodeMap.put(config.getNode().getUuid(), parallelNode);
    return GraphLayoutResponse.builder().layoutNodes(layoutNodeMap).build();
  }

  private List<AdviserObtainment> getAdviserObtainmentFromMetaData(PlanCreationContext ctx, YamlField currentField) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    AdviserObtainment nextStepAdviser = PlanCreatorUtilsV1.getNextStepAdviser(kryoSerializer, ctx.getDependency());
    if (nextStepAdviser != null) {
      adviserObtainments.add(nextStepAdviser);
    }
    return adviserObtainments;
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }

  private void validateUnsupportedProperties(YamlField config) {
    if (config.getNode().getField(YAMLFieldNameConstants.WHEN) != null) {
      throw new InvalidRequestException("When condition not supported for parallel node");
    }
    if (config.getNode().getField("failure") != null) {
      throw new InvalidRequestException("Failure strategies not supported for parallel node");
    }
    if (config.getNode().getField(YAMLFieldNameConstants.STRATEGY) != null) {
      throw new InvalidRequestException("Looping strategies not supported for parallel node");
    }
  }
}
