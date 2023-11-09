/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps.v1;

import static io.harness.pms.plan.creation.PlanCreatorConstants.YAML_VERSION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.PlanCreatorUtilsV1;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorConstants;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.common.NGSectionStep;
import io.harness.steps.common.NGSectionStepParameters;
import io.harness.steps.fork.ForkStepParameters;
import io.harness.steps.fork.NGForkStep;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.PIPELINE)
public class StepsPlanCreatorV1 extends ChildrenPlanCreator<YamlField> {
  @Inject KryoSerializer kryoSerializer;
  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> steps = getStepYamlFields(config);
    if (EmptyPredicate.isEmpty(steps)) {
      return responseMap;
    }
    int i;
    YamlField curr;
    boolean isInsideParallel = isInsideParallelNode(ctx);
    for (i = 0; i < steps.size() - 1; i++) {
      curr = getStepField(steps.get(i));
      String version = getYamlVersionFromStepField(curr);
      String nextId = getStepField(steps.get(i + 1)).getUuid();
      Dependency dependency =
          Dependency.newBuilder()
              .setParentInfo(HarnessStruct.newBuilder()
                                 .putData(YAML_VERSION, HarnessValue.newBuilder().setStringValue(version).build())
                                 .build())
              .build();
      // If Steps is not inside parallel only then nextIds need to be added, else they'll be executed parallelly
      if (!isInsideParallel) {
        dependency =
            Dependency.newBuilder()
                .setParentInfo(HarnessStruct.newBuilder()
                                   .putData(YAML_VERSION, HarnessValue.newBuilder().setStringValue(version).build())
                                   .build())
                .setNodeMetadata(
                    HarnessStruct.newBuilder()
                        .putData(PlanCreatorConstants.NEXT_ID, HarnessValue.newBuilder().setStringValue(nextId).build())
                        .build())
                .build();
      }
      responseMap.put(curr.getUuid(),
          PlanCreationResponse.builder()
              .dependencies(Dependencies.newBuilder()
                                .putDependencies(curr.getUuid(), curr.getYamlPath())
                                .putDependencyMetadata(curr.getUuid(), dependency)
                                .build())
              .build());
    }

    curr = getStepField(steps.get(i));
    String version = getYamlVersionFromStepField(curr);
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

  private YamlField getStepField(YamlField currField) {
    if (currField.getNode().getField(YAMLFieldNameConstants.STEP) != null) {
      return currField.getNode().getField(YAMLFieldNameConstants.STEP);
    }
    if (currField.getNode().getField(YAMLFieldNameConstants.STEP_GROUP) != null) {
      return currField.getNode().getField(YAMLFieldNameConstants.STEP_GROUP);
    }
    return currField;
  }

  private String getYamlVersionFromStepField(YamlField currField) {
    if ((currField.getNode().getField(YAMLFieldNameConstants.STEP) != null
            || YAMLFieldNameConstants.STEP.equals(currField.getNode().getFieldName()))
        || (currField.getNode().getField(YAMLFieldNameConstants.STEP_GROUP) != null
            || YAMLFieldNameConstants.STEP_GROUP.equals(currField.getNode().getFieldName()))) {
      return HarnessYamlVersion.V0;
    }
    return HarnessYamlVersion.V1;
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    // If Steps is inside parallel node, it needs to be executed via NGForkStep with children strategy
    // else, NGSectionStep with child strategy will be used
    String facilitatorType = OrchestrationFacilitatorType.CHILD;
    StepType stepType = NGSectionStep.STEP_TYPE;
    StepParameters stepParameters = NGSectionStepParameters.builder().childNodeId(childrenNodeIds.get(0)).build();
    if (isInsideParallelNode(ctx)) {
      facilitatorType = OrchestrationFacilitatorType.CHILDREN;
      stepType = NGForkStep.STEP_TYPE;
      stepParameters = ForkStepParameters.builder().parallelNodeIds(childrenNodeIds).build();
    }
    return PlanNode.builder()
        .uuid(config.getUuid())
        .identifier(YAMLFieldNameConstants.STEPS)
        .stepType(stepType)
        .name(YAMLFieldNameConstants.STEPS)
        .stepParameters(stepParameters)
        .facilitatorObtainment(FacilitatorObtainment.newBuilder()
                                   .setType(FacilitatorType.newBuilder().setType(facilitatorType).build())
                                   .build())
        .skipGraphType(SkipType.SKIP_NODE)
        .build();
  }

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.STEPS, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V1);
  }

  private List<YamlField> getStepYamlFields(YamlField yamlField) {
    List<YamlNode> yamlNodes = Optional.of(yamlField.getNode().asArray()).orElse(Collections.emptyList());
    return yamlNodes.stream().map(YamlField::new).collect(Collectors.toList());
  }

  private boolean isInsideParallelNode(PlanCreationContext ctx) {
    Optional<Object> value = PlanCreatorUtilsV1.getDeserializedObjectFromDependency(
        ctx.getDependency(), kryoSerializer, PlanCreatorConstants.IS_INSIDE_PARALLEL_NODE, false);
    return value.isPresent() && (boolean) value.get();
  }
}
