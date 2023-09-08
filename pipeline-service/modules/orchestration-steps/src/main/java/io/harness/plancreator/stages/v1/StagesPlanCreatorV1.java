/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages.v1;

import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.pms.plan.creation.PlanCreatorConstants.YAML_VERSION;

import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
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
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StagesStep;
import io.harness.steps.common.NGSectionStepParameters;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class StagesPlanCreatorV1 extends ChildrenPlanCreator<YamlField> {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> stages = getStageYamlFields(config);

    if (EmptyPredicate.isEmpty(stages)) {
      return responseMap;
    }
    int i;
    YamlField curr;

    // TODO : Figure out corresponding failure stages and put that here as well
    for (i = 0; i < stages.size() - 1; i++) {
      curr = getStageField(stages.get(i));
      String version = getYamlVersionFromStageField(curr);
      String nextId = getStageField(stages.get(i + 1)).getUuid();
      // Both metadata and nodeMetadata contain the same metadata, the first one's value will be kryo serialized bytes
      // while second one can have values in their primitive form like strings, int, etc. and will have kryo serialized
      // bytes for complex objects. We will deprecate the first one in v1
      responseMap.put(curr.getUuid(),
          PlanCreationResponse.builder()
              .dependencies(
                  Dependencies.newBuilder()
                      .putDependencies(curr.getUuid(), curr.getYamlPath())
                      .putDependencyMetadata(curr.getUuid(),
                          Dependency.newBuilder()
                              .putMetadata(
                                  PlanCreatorConstants.NEXT_ID, ByteString.copyFrom(kryoSerializer.asBytes(nextId)))
                              .setNodeMetadata(HarnessStruct.newBuilder().putData(PlanCreatorConstants.NEXT_ID,
                                  HarnessValue.newBuilder().setStringValue(nextId).build()))
                              .setParentInfo(
                                  HarnessStruct.newBuilder()
                                      .putData(YAML_VERSION, HarnessValue.newBuilder().setStringValue(version).build())
                                      .build())
                              .build())
                      .build())
              .build());
    }

    curr = getStageField(stages.get(i));
    String version = getYamlVersionFromStageField(curr);
    if (curr.getNode().getField(YAMLFieldNameConstants.STAGE) != null) {
      curr = curr.getNode().getField(YAMLFieldNameConstants.STAGE);
      version = PipelineVersion.V0;
    }
    responseMap.put(curr.getUuid(),
        PlanCreationResponse.builder()
            .dependencies(Dependencies.newBuilder()
                              .putDependencies(curr.getUuid(), curr.getYamlPath())
                              .putDependencyMetadata(curr.getUuid(),
                                  Dependency.newBuilder()
                                      .setParentInfo(HarnessStruct.newBuilder()
                                                         .putData(YAML_VERSION,
                                                             HarnessValue.newBuilder().setStringValue(version).build())
                                                         .build())
                                      .build())
                              .build())
            .build());
    return responseMap;
  }

  private YamlField getStageField(YamlField currField) {
    if (currField.getNode().getField(YAMLFieldNameConstants.STAGE) != null) {
      return currField.getNode().getField(YAMLFieldNameConstants.STAGE);
    }
    return currField;
  }

  private String getYamlVersionFromStageField(YamlField currField) {
    if (currField.getNode().getField(YAMLFieldNameConstants.STAGE) != null
        || YAMLFieldNameConstants.STAGE.equals(currField.getNode().getFieldName())) {
      return PipelineVersion.V0;
    }
    return PipelineVersion.V1;
  }
  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext ctx, YamlField config) {
    // Create graphLayout only if stages node is child of parent.(Return empty if its child of parallel.spec)
    if (EmptyPredicate.isEmpty(config.getNode().getParentNode().getType())
        || !config.getNode().getParentNode().getType().equals(YAMLFieldNameConstants.PIPELINE)) {
      return GraphLayoutResponse.builder().build();
    }
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    List<YamlField> stagesYamlField =
        getStageYamlFields(config).stream().map(this::getStageField).collect(Collectors.toList());
    List<EdgeLayoutList> edgeLayoutLists = new ArrayList<>();
    for (YamlField stageYamlField : stagesYamlField) {
      EdgeLayoutList.Builder stageEdgesBuilder = EdgeLayoutList.newBuilder();
      stageEdgesBuilder.addNextIds(stageYamlField.getNode().getUuid());
      edgeLayoutLists.add(stageEdgesBuilder.build());
    }
    for (int i = 0; i < edgeLayoutLists.size(); i++) {
      YamlField stageYamlField = stagesYamlField.get(i);
      if (stageYamlField.getType().equals("parallel")) {
        continue;
      }
      stageYamlFieldMap.put(stageYamlField.getNode().getUuid(),
          GraphLayoutNode.newBuilder()
              .setNodeUUID(stageYamlField.getNode().getUuid())
              .setNodeType(stageYamlField.getNode().getType())
              .setName(emptyIfNull(stageYamlField.getNode().getName()))
              .setNodeGroup(StepOutcomeGroup.STAGE.name())
              .setNodeIdentifier(emptyIfNull(stageYamlField.getNode().getIdentifier()))
              .setEdgeLayoutList(
                  i + 1 < edgeLayoutLists.size() ? edgeLayoutLists.get(i + 1) : EdgeLayoutList.newBuilder().build())
              .build());
    }
    return GraphLayoutResponse.builder()
        .layoutNodes(stageYamlFieldMap)
        .startingNodeId(stagesYamlField.get(0).getNode().getUuid())
        .build();
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    return PlanNode.builder()
        .uuid(ctx.getCurrentField().getNode().getUuid())
        .identifier(YAMLFieldNameConstants.STAGES)
        .stepType(StagesStep.STEP_TYPE)
        .group(StepOutcomeGroup.STAGES.name())
        .name(YAMLFieldNameConstants.STAGES)
        .stepParameters(
            NGSectionStepParameters.builder().childNodeId(childrenNodeIds.get(0)).logMessage("Stages").build())
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipExpressionChain(false)
        .build();
  }

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap("stages", Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  private List<YamlField> getStageYamlFields(YamlField yamlField) {
    List<YamlNode> yamlNodes = Optional.of(yamlField.getNode().asArray()).orElse(Collections.emptyList());
    return yamlNodes.stream().map(YamlField::new).collect(Collectors.toList());
  }
}
