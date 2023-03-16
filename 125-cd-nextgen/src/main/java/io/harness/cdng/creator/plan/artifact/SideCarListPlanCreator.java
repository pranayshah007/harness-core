/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.SidecarsListWrapper;
import io.harness.cdng.artifact.steps.beans.ArtifactStepParameters;
import io.harness.cdng.artifact.steps.constants.SidecarsStepConstants;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.utilities.SideCarsListArtifactsUtility;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse.PlanCreationResponseBuilder;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.fork.ForkStepParameters;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.CDC)
public class SideCarListPlanCreator extends ChildrenPlanCreator<SidecarsListWrapper> {
  @Inject KryoSerializer kryoSerializer;

  @Override
  public Class<SidecarsListWrapper> getFieldClass() {
    return SidecarsListWrapper.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YamlTypes.SIDECARS_ARTIFACT_CONFIG, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, SidecarsListWrapper sidecarsListWrapper) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();
    YamlField sideCarsYamlField = ctx.getCurrentField();

    Map<String, ArtifactStepParameters> sideCarsParametersMap =
        (Map<String, ArtifactStepParameters>) kryoSerializer.asInflatedObject(
            ctx.getDependency().getMetadataMap().get(PlanCreatorConstants.SIDECARS_PARAMETERS_MAP).toByteArray());

    List<YamlNode> yamlNodes = Optional.of(sideCarsYamlField.getNode().asArray()).orElse(Collections.emptyList());
    Map<String, YamlNode> sidecarIdentifierToYamlNodeMap = yamlNodes.stream().collect(
        Collectors.toMap(e -> e.getField(YamlTypes.SIDECAR_ARTIFACT_CONFIG).getNode().getIdentifier(), k -> k));

    for (Map.Entry<String, ArtifactStepParameters> entry : sideCarsParametersMap.entrySet()) {
      addDependenciesForIndividualSideCar(
          sideCarsYamlField, entry.getKey(), entry.getValue(), sidecarIdentifierToYamlNodeMap, planCreationResponseMap);
    }

    return planCreationResponseMap;
  }

  public String addDependenciesForIndividualSideCar(YamlField sideCarsYamlField, String sideCarIdentifier,
      ArtifactStepParameters stepParameters, Map<String, YamlNode> sidecarIdentifierToYamlNodeMap,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap) {
    YamlField individualSideCar = SideCarsListArtifactsUtility.fetchIndividualSideCarYamlField(
        sideCarsYamlField, sideCarIdentifier, sidecarIdentifierToYamlNodeMap);

    String individualSideCarPlanNodeId = UUIDGenerator.generateUuid();
    Map<String, ByteString> metadataDependency =
        prepareMetadataForIndividualSideCarListPlanCreator(individualSideCarPlanNodeId, stepParameters);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(individualSideCarPlanNodeId, individualSideCar);
    PlanCreationResponseBuilder individualSideCarPlanResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                individualSideCarPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    planCreationResponseMap.put(individualSideCarPlanNodeId, individualSideCarPlanResponse.build());
    return individualSideCarPlanNodeId;
  }

  public Map<String, ByteString> prepareMetadataForIndividualSideCarListPlanCreator(
      String individualSideCarPlanNodeId, ArtifactStepParameters stepParameters) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(
        YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(individualSideCarPlanNodeId)));
    metadataDependency.put(PlanCreatorConstants.SIDECAR_STEP_PARAMETERS,
        ByteString.copyFrom(kryoSerializer.asDeflatedBytes(stepParameters)));
    return metadataDependency;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, SidecarsListWrapper config, List<String> childrenNodeIds) {
    String sideCarsPlanNodeId = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.UUID).toByteArray());

    ForkStepParameters stepParameters = ForkStepParameters.builder().parallelNodeIds(childrenNodeIds).build();
    return PlanNode.builder()
        .uuid(sideCarsPlanNodeId)
        .stepType(SidecarsStepConstants.STEP_TYPE)
        .name(PlanCreatorConstants.SIDECARS_NODE_NAME)
        .identifier(YamlTypes.SIDECARS_ARTIFACT_CONFIG)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                .build())
        .skipExpressionChain(false)
        .build();
  }
}