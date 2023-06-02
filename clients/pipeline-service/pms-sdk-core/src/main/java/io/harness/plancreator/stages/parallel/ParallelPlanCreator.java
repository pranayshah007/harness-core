/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages.parallel;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.advisers.nextstep.NextStageAdviserParameters;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.strategy.StrategyUtils;
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
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.fork.ForkStepParameters;
import io.harness.steps.fork.NGForkStep;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class ParallelPlanCreator extends ChildrenPlanCreator<YamlField> {
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
    List<YamlField> dependencyNodeIdsList = PlanCreatorUtils.getDependencyNodeIdsForParallelNode(ctx.getCurrentField());

    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    for (YamlField yamlField : dependencyNodeIdsList) {
      Map<String, YamlField> yamlFieldMap = new HashMap<>();
      yamlFieldMap.put(yamlField.getNode().getUuid(), yamlField);
      responseMap.put(yamlField.getNode().getUuid(),
          PlanCreationResponse.builder().dependencies(DependenciesUtils.toDependenciesProto(yamlFieldMap)).build());
    }

    return responseMap;
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    YamlNode currentNode = config.getNode();
    return PlanNode.builder()
        .uuid(currentNode.getUuid())
        .name(YAMLFieldNameConstants.PARALLEL)
        .identifier(YAMLFieldNameConstants.PARALLEL + currentNode.getUuid())
        .stepType(NGForkStep.STEP_TYPE)
        .stepParameters(ForkStepParameters.builder().parallelNodeIds(childrenNodeIds).build())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                .build())
        .adviserObtainments(getAdviserObtainmentFromMetaData(config))
        .skipExpressionChain(true)
        .build();
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext ctx, YamlField config) {
    List<String> possibleSiblings = new ArrayList<>();
    possibleSiblings.add(YAMLFieldNameConstants.STAGE);
    possibleSiblings.add(YAMLFieldNameConstants.PARALLEL);
    YamlField nextSibling =
        ctx.getCurrentField().getNode().nextSiblingFromParentArray(YAMLFieldNameConstants.PARALLEL, possibleSiblings);
    YamlField previousSibling = ctx.getCurrentField().getNode().previousSiblingFromParentArray(
        YAMLFieldNameConstants.PARALLEL, possibleSiblings);

    List<YamlField> children = PlanCreatorUtils.getStageChildFields(ctx.getCurrentField());
    if (children.isEmpty()) {
      return GraphLayoutResponse.builder().build();
    }
    List<String> childrenUuids =
        children.stream().map(YamlField::getNode).map(YamlNode::getUuid).collect(Collectors.toList());

    EdgeLayoutList.Builder stagesEdgesBuilder = EdgeLayoutList.newBuilder().addAllCurrentNodeChildren(childrenUuids);
    String pipelineRollbackStageId = StrategyUtils.getPipelineRollbackStageId(config);
    if (nextSibling != null && !nextSibling.getUuid().equals(pipelineRollbackStageId)) {
      stagesEdgesBuilder.addNextIds(nextSibling.getNode().getUuid());
    }

    Map<String, GraphLayoutNode> layoutNodeMap = children.stream().collect(Collectors.toMap(stageField
        -> stageField.getNode().getUuid(),
        stageField
        -> GraphLayoutNode.newBuilder()
               .setNodeUUID(stageField.getNode().getUuid())
               .setNodeGroup(StepOutcomeGroup.STAGE.name())
               .setName(stageField.getNode().getName())
               .setNodeType(stageField.getNode().getType())
               .setNodeIdentifier(stageField.getNode().getIdentifier())
               .setEdgeLayoutList(EdgeLayoutList.newBuilder().build())
               .build()));

    GraphLayoutNode parallelNode = GraphLayoutNode.newBuilder()
                                       .setNodeUUID(config.getNode().getUuid())
                                       .setNodeType(YAMLFieldNameConstants.PARALLEL)
                                       .setNodeGroup(StepOutcomeGroup.STAGE.name())
                                       .setNodeIdentifier(YAMLFieldNameConstants.PARALLEL + config.getNode().getUuid())
                                       .setEdgeLayoutList(stagesEdgesBuilder.build())
                                       .build();

    layoutNodeMap.put(config.getNode().getUuid(), parallelNode);
    return GraphLayoutResponse.builder().layoutNodes(layoutNodeMap).build();
  }

  private List<AdviserObtainment> getAdviserObtainmentFromMetaData(YamlField currentField) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();

    if (currentField != null && currentField.getNode() != null) {
      YamlField siblingField = currentField.getNode().nextSiblingFromParentArray(currentField.getName(),
          Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.STEP, YAMLFieldNameConstants.STEP_GROUP,
              YAMLFieldNameConstants.PARALLEL));
      String pipelineRollbackStageId = StrategyUtils.getPipelineRollbackStageId(currentField);
      if (siblingField != null && siblingField.getNode().getUuid() != null) {
        AdviserObtainment adviserObtainment;
        YamlNode parallelNodeInStage = YamlUtils.findParentNode(currentField.getNode(), YAMLFieldNameConstants.STAGE);
        if (parallelNodeInStage != null) {
          adviserObtainment = StrategyUtils.getAdviserObtainmentsForParallelStepParent(
              currentField, kryoSerializer, siblingField.getNode().getUuid());
        } else {
          String siblingFieldUuid = siblingField.getNode().getUuid();
          adviserObtainment =
              AdviserObtainment.newBuilder()
                  .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
                  .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                      NextStageAdviserParameters.builder()
                          .nextNodeId(siblingFieldUuid.equals(pipelineRollbackStageId) ? null : siblingFieldUuid)
                          .pipelineRollbackStageId(pipelineRollbackStageId)
                          .build())))
                  .build();
        }
        adviserObtainments.add(adviserObtainment);
      }
    }
    return adviserObtainments;
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V0);
  }
}
