/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.ROLLBACK_STEPS;

import io.harness.advisers.nextstep.NextStepAdviserParameters;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ssh.CommandStepNode;
import io.harness.cdng.ssh.CommandStepParameters;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.timeout.AbsoluteSdkTimeoutTrackerParameters;
import io.harness.pms.timeout.SdkTimeoutObtainment;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.fork.ForkStepParameters;
import io.harness.steps.fork.NGForkStep;
import io.harness.timeout.trackers.absolute.AbsoluteTimeoutTrackerFactory;
import io.harness.when.utils.RunInfoUtils;

import com.google.common.collect.Sets;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(CDP)
public class CommandStepPlanCreator extends CDPMSStepPlanCreatorV2<CommandStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.COMMAND);
  }

  @Override
  public Class<CommandStepNode> getFieldClass() {
    return CommandStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, CommandStepNode stepElement) {
    PlanCreationResponse finalResponse = PlanCreationResponse.builder().build();
    List<String> hosts = extractHostData(ctx);
    LinkedHashMap<String, PlanCreationResponse> childrenResponses = createPlanForChildrenNodes(ctx, stepElement, hosts);
    List<String> childrenNodeIds = new LinkedList<>();
    for (Map.Entry<String, PlanCreationResponse> entry : childrenResponses.entrySet()) {
      finalResponse.merge(entry.getValue());
      childrenNodeIds.add(entry.getKey());
    }

    finalResponse.addNode(createPlanForParentNode(ctx, childrenNodeIds));
    finalResponse.setGraphLayoutResponse(getLayoutNodeInfo(ctx));
    return finalResponse;
  }

  private List<String> extractHostData(PlanCreationContext ctx) {
    YamlNode infrastructureNode =
        YamlUtils.getGivenYamlNodeFromParentPath(ctx.getCurrentField().getNode(), "infrastructure");
    if (infrastructureNode == null) {
      throw new InvalidRequestException("Infrastructure definition is missing");
    }

    YamlField infrastructureDefinitionYamlField = infrastructureNode.getField("infrastructureDefinition");
    if (infrastructureDefinitionYamlField == null) {
      throw new InvalidRequestException("Infrastructure definition is missing");
    }

    YamlNode infrastructureDefinitionNode = infrastructureDefinitionYamlField.getNode();
    String infraDefinitionType = infrastructureDefinitionNode.getStringValue("type");
    if (EmptyPredicate.isEmpty(infraDefinitionType) || !"Pdc".equals(infraDefinitionType)) {
      throw new InvalidRequestException(
          String.format("Infrastructure definition type %s is not supported for Command Step", infraDefinitionType));
    }

    YamlField hostsYamlField = infrastructureDefinitionNode.getField("spec").getNode().getField("hosts");
    YamlField connectorRefYamlField = infrastructureDefinitionNode.getField("spec").getNode().getField("connectorRef");
    if (connectorRefYamlField == null) {
      // retrieve host information from infra definition spec
      return hostsYamlField.getNode()
          .getChildrenToWalk()
          .getVisitableChildList()
          .stream()
          .map(visitableChild -> visitableChild.getValue().toString().replaceAll("\"", ""))
          .collect(Collectors.toList());
    } else {
      // retrieve host information from connector
      throw new InvalidRequestException("Host configured part of PDC connector are no supported yet");
    }
  }

  public PlanNode createPlanForParentNode(PlanCreationContext ctx, List<String> childrenNodeIds) {
    YamlNode currentNode = ctx.getCurrentField().getNode();
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
        .adviserObtainments(getAdviserObtainmentFromMetaData(ctx.getCurrentField()))
        .skipExpressionChain(true)
        .build();
  }

  @Override
  protected List<AdviserObtainment> getAdviserObtainmentFromMetaData(YamlField currentField) {
    List<AdviserObtainment> adviserObtainments = new ArrayList<>();
    if (currentField != null && currentField.getNode() != null) {
      YamlField siblingField = currentField.getNode().nextSiblingFromParentArray(currentField.getName(),
          Arrays.asList(YAMLFieldNameConstants.STAGE, YAMLFieldNameConstants.STEP, YAMLFieldNameConstants.STEP_GROUP,
              YAMLFieldNameConstants.PARALLEL));
      if (siblingField != null && siblingField.getNode().getUuid() != null) {
        AdviserObtainment adviserObtainment;
        YamlNode parallelNodeInStage = YamlUtils.findParentNode(currentField.getNode(), YAMLFieldNameConstants.STAGE);
        if (parallelNodeInStage != null) {
          adviserObtainment =
              AdviserObtainment.newBuilder()
                  .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STEP.name()).build())
                  .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                      NextStepAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
                  .build();
        } else {
          adviserObtainment =
              AdviserObtainment.newBuilder()
                  .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.NEXT_STAGE.name()).build())
                  .setParameters(ByteString.copyFrom(kryoSerializer.asBytes(
                      NextStepAdviserParameters.builder().nextNodeId(siblingField.getNode().getUuid()).build())))
                  .build();
        }
        adviserObtainments.add(adviserObtainment);
      }
    }
    return adviserObtainments;
  }

  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, CommandStepNode stepElement, List<String> hosts) {
    YamlField yamlField = ctx.getCurrentField();
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    for (String host : hosts) {
      responseMap.put(yamlField.getNode().getUuid() + host, createPlanForField(ctx, stepElement, host));
    }

    return responseMap;
  }

  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, CommandStepNode stepElement, String host) {
    boolean isStepInsideRollback = false;
    if (YamlUtils.findParentNode(ctx.getCurrentField().getNode(), ROLLBACK_STEPS) != null) {
      isStepInsideRollback = true;
    }

    StepParameters stepParameters = getStepParameters(ctx, stepElement);
    StepElementParameters newStepParamsWithHost = ((StepElementParameters) stepParameters).cloneParameters(true, true);
    ((CommandStepParameters) newStepParamsWithHost.getSpec()).setHost(host);
    PlanNode stepPlanNode =
        PlanNode.builder()
            .uuid(ctx.getCurrentField().getNode().getUuid() + host)
            .name(getName(stepElement))
            .identifier(stepElement.getIdentifier() + host)
            .stepType(stepElement.getStepSpecType().getStepType())
            .group(StepOutcomeGroup.STEP.name())
            .stepParameters(newStepParamsWithHost)
            .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                       .setType(FacilitatorType.newBuilder()
                                                    .setType(stepElement.getStepSpecType().getFacilitatorType())
                                                    .build())
                                       .build())
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
    return PlanCreationResponse.builder().planNode(stepPlanNode).build();
  }

  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext ctx) {
    List<String> possibleSiblings = new ArrayList<>();
    possibleSiblings.add(YAMLFieldNameConstants.STAGE);
    possibleSiblings.add(YAMLFieldNameConstants.PARALLEL);
    YamlField nextSibling =
        ctx.getCurrentField().getNode().nextSiblingFromParentArray(YAMLFieldNameConstants.PARALLEL, possibleSiblings);

    List<YamlField> children = PlanCreatorUtils.getStageChildFields(ctx.getCurrentField());
    if (children.isEmpty()) {
      return GraphLayoutResponse.builder().build();
    }
    List<String> childrenUuids =
        children.stream().map(YamlField::getNode).map(YamlNode::getUuid).collect(Collectors.toList());
    EdgeLayoutList.Builder stagesEdgesBuilder = EdgeLayoutList.newBuilder().addAllCurrentNodeChildren(childrenUuids);
    if (nextSibling != null) {
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
    GraphLayoutNode parallelNode =
        GraphLayoutNode.newBuilder()
            .setNodeUUID(ctx.getCurrentField().getNode().getUuid())
            .setNodeType(YAMLFieldNameConstants.PARALLEL)
            .setNodeGroup(StepOutcomeGroup.STAGE.name())
            .setNodeIdentifier(YAMLFieldNameConstants.PARALLEL + ctx.getCurrentField().getNode().getUuid())
            .setEdgeLayoutList(stagesEdgesBuilder.build())
            .build();
    layoutNodeMap.put(ctx.getCurrentField().getNode().getUuid(), parallelNode);
    return GraphLayoutResponse.builder().layoutNodes(layoutNodeMap).build();
  }
}
