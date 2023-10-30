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
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.HarnessStruct;
import io.harness.pms.contracts.plan.HarnessValue;
import io.harness.pms.contracts.steps.SkipType;
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

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.PIPELINE)
public class StepsPlanCreatorV1 extends ChildrenPlanCreator<YamlField> {
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, YamlField config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> stages = getStepYamlFields(config);

    if (EmptyPredicate.isEmpty(stages)) {
      return responseMap;
    }
    int i;
    YamlField curr;

    // TODO : Figure out corresponding failure stages and put that here as well
    for (i = 0; i < stages.size() - 1; i++) {
      curr = getStepField(stages.get(i));
      String version = getYamlVersionFromStepField(curr);
      String nextId = getStepField(stages.get(i + 1)).getUuid();
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
                              .setNodeMetadata(HarnessStruct.newBuilder()
                                                   .putData(PlanCreatorConstants.NEXT_ID,
                                                       HarnessValue.newBuilder().setStringValue(nextId).build())
                                                   .build())
                              .setParentInfo(
                                  HarnessStruct.newBuilder()
                                      .putData(YAML_VERSION, HarnessValue.newBuilder().setStringValue(version).build())
                                      .build())
                              .build())
                      .build())
              .build());
    }

    curr = getStepField(stages.get(i));
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
    return currField;
  }

  private String getYamlVersionFromStepField(YamlField currField) {
    if (currField.getNode().getField(YAMLFieldNameConstants.STEP) != null
        || YAMLFieldNameConstants.STEP.equals(currField.getNode().getFieldName())) {
      return HarnessYamlVersion.V0;
    }
    return HarnessYamlVersion.V1;
  }
  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, YamlField config, List<String> childrenNodeIds) {
    StepParameters stepParameters = NGSectionStepParameters.builder().childNodeId(childrenNodeIds.get(0)).build();
    return PlanNode.builder()
        .uuid(config.getUuid())
        .identifier(YAMLFieldNameConstants.STEPS)
        .stepType(NGSectionStep.STEP_TYPE)
        .name(YAMLFieldNameConstants.STEPS)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
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
}
