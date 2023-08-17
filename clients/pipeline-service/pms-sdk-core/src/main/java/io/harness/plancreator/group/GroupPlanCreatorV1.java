/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.group;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.group.GroupStepParametersV1;
import io.harness.steps.group.GroupStepV1;
import io.harness.when.utils.v1.RunInfoUtilsV1;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class GroupPlanCreatorV1 extends ChildrenPlanCreator<YamlField> {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap("group", Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField config) {
    YamlNode specNode = config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode();
    YamlField childrenField = null;
    if (specNode.getField(YAMLFieldNameConstants.STAGES) != null) {
      childrenField = specNode.getField(YAMLFieldNameConstants.STAGES);
    } else {
      childrenField = specNode.getField(YAMLFieldNameConstants.STEPS);
    }
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();

    Map<String, YamlField> yamlFieldMap = new HashMap<>();
    yamlFieldMap.put(childrenField.getUuid(), childrenField);
    responseMap.put(childrenField.getUuid(),
        PlanCreationResponse.builder().dependencies(DependenciesUtils.toDependenciesProto(yamlFieldMap)).build());

    return responseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    YamlField childrenField;
    YamlNode specNode = config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode();
    if (specNode.getField(YAMLFieldNameConstants.STAGES) != null) {
      childrenField = specNode.getField(YAMLFieldNameConstants.STAGES);
    } else {
      childrenField = specNode.getField(YAMLFieldNameConstants.STEPS);
    }
    YamlNode currentNode = config.getNode();
    return PlanNode.builder()
        .uuid(currentNode.getUuid())
        .name(YAMLFieldNameConstants.GROUP)
        .identifier(YAMLFieldNameConstants.GROUP + currentNode.getUuid())
        .stepType(GroupStepV1.STEP_TYPE)
        .stepParameters(GroupStepParametersV1.builder().childNodeID(childrenField.getNode().getUuid()).build())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .adviserObtainments(getAdviserObtainmentFromMetaData(ctx, config))
        .whenCondition(RunInfoUtilsV1.getStageWhenCondition(config))
        .skipExpressionChain(true)
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
    String nextNodeId = null;
    if (ctx.getDependency() != null && ctx.getDependency().getMetadataMap().get("nextId") != null) {
      nextNodeId = (String) kryoSerializer.asObject(ctx.getDependency().getMetadataMap().get("nextId").toByteArray());
    }
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
    // Setting the nodeType as parallel for now so that UI shows the graph. Need to change the nodeType to Group. And UI
    // also need to handle the new nodeType.
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
    String nextNodeId = null;
    if (ctx.getDependency() != null && ctx.getDependency().getMetadataMap().get("nextId") != null) {
      nextNodeId = (String) kryoSerializer.asObject(ctx.getDependency().getMetadataMap().get("nextId").toByteArray());
    }

    if (currentField != null && currentField.getNode() != null) {
      if (nextNodeId != null) {
        AdviserObtainment adviserObtainment =
            AdviserObtainment.newBuilder()
                .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
                .setParameters(ByteString.copyFrom(
                    kryoSerializer.asBytes(NextStepAdviserParameters.builder().nextNodeId(nextNodeId).build())))
                .build();
        adviserObtainments.add(adviserObtainment);
      }
    }
    return adviserObtainments;
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V1);
  }
}
