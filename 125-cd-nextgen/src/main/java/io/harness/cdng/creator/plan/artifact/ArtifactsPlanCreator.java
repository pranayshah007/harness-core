/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifactWrapper;
import io.harness.cdng.artifact.steps.ArtifactStepParameters;
import io.harness.cdng.artifact.steps.ArtifactStepParameters.ArtifactStepParametersBuilder;
import io.harness.cdng.artifact.steps.ArtifactsStep;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.StageOverridesConfig;
import io.harness.cdng.utilities.PrimaryArtifactsUtility;
import io.harness.cdng.utilities.SideCarsListArtifactsUtility;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse.PlanCreationResponseBuilder;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.fork.ForkStepParameters;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactsPlanCreator extends ChildrenPlanCreator<ArtifactListConfig> {
  @Inject KryoSerializer kryoSerializer;

  @Override
  public Class<ArtifactListConfig> getFieldClass() {
    return ArtifactListConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.ARTIFACT_LIST_CONFIG, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  public Map<String, ByteString> prepareMetadataForPrimaryArtifactPlanCreator(
      String primaryId, ArtifactStepParameters params) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(primaryId)));
    metadataDependency.put(
        PlanCreatorConstants.PRIMARY_STEP_PARAMETERS, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(params)));
    return metadataDependency;
  }

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, ArtifactListConfig config) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    String artifactsId = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.UUID).toByteArray());
    // service v1
    if (ctx.getDependency().getMetadataMap().containsKey(YamlTypes.SERVICE_CONFIG)) {
      ServiceConfig serviceConfig = (ServiceConfig) kryoSerializer.asInflatedObject(
          ctx.getDependency().getMetadataMap().get(YamlTypes.SERVICE_CONFIG).toByteArray());
      ArtifactListConfig artifactListConfig = serviceConfig.getServiceDefinition().getServiceSpec().getArtifacts();
      ArtifactListBuilder artifactListBuilder = new ArtifactListBuilder(artifactListConfig);
      artifactListBuilder.addStageOverrides(serviceConfig.getStageOverrides());
      ArtifactList artifactList = artifactListBuilder.build();
      if (artifactList.getPrimary() == null && EmptyPredicate.isEmpty(artifactList.getSidecars())) {
        return planCreationResponseMap;
      }

      if (artifactList.getPrimary() != null) {
        addDependenciesForPrimaryNode(
            ctx.getCurrentField(), artifactList.getPrimary().getParams(), planCreationResponseMap);
      }
      if (EmptyPredicate.isNotEmpty(artifactList.getSidecars())) {
        addDependenciesForSideCarList(
            ctx.getCurrentField(), artifactsId, artifactList.getSidecars(), planCreationResponseMap);
      }
    } else if (ctx.getDependency().getMetadataMap().containsKey(YamlTypes.SERVICE_ENTITY)) {
      NGServiceV2InfoConfig serviceV2InfoConfig = (NGServiceV2InfoConfig) kryoSerializer.asInflatedObject(
          ctx.getDependency().getMetadataMap().get(YamlTypes.SERVICE_ENTITY).toByteArray());
      ArtifactListConfig artifactListConfig =
          serviceV2InfoConfig.getServiceDefinition().getServiceSpec().getArtifacts();
      ArtifactListBuilder artifactListBuilder = new ArtifactListBuilder(artifactListConfig);
      ArtifactList artifactList = artifactListBuilder.build();
      if (artifactList.getPrimary() == null && EmptyPredicate.isEmpty(artifactList.getSidecars())) {
        return planCreationResponseMap;
      }

      if (artifactList.getPrimary() != null) {
        addDependenciesForPrimaryNode(
            ctx.getCurrentField(), artifactList.getPrimary().getParams(), planCreationResponseMap);
      }
      if (EmptyPredicate.isNotEmpty(artifactList.getSidecars())) {
        addDependenciesForSideCarList(
            ctx.getCurrentField(), artifactsId, artifactList.getSidecars(), planCreationResponseMap);
      }
    }
    return planCreationResponseMap;
  }

  public String addDependenciesForSideCarList(YamlField artifactField, String artifactsId,
      Map<String, ArtifactInfo> sideCarsInfo, Map<String, PlanCreationResponse> planCreationResponseMap) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField sideCarsListYamlField =
        SideCarsListArtifactsUtility.createSideCarsArtifactYamlFieldAndSetYamlUpdate(artifactField, yamlUpdates);

    Map<String, ArtifactStepParameters> sideCarsParametersMap =
        sideCarsInfo.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, k -> k.getValue().getParams()));
    String sideCarsListPlanNodeId = "sidecars-" + artifactsId;
    Map<String, ByteString> metadataDependency =
        prepareMetadataForSideCarListArtifactPlanCreator(sideCarsListPlanNodeId, sideCarsParametersMap);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(sideCarsListPlanNodeId, sideCarsListYamlField);
    PlanCreationResponseBuilder primaryPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                sideCarsListPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      primaryPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(sideCarsListPlanNodeId, primaryPlanCreationResponse.build());
    return sideCarsListPlanNodeId;
  }

  private Map<String, ByteString> prepareMetadataForSideCarListArtifactPlanCreator(
      String sideCarsListPlanNodeId, Map<String, ArtifactStepParameters> sideCarsParametersMap) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(sideCarsListPlanNodeId)));
    metadataDependency.put(PlanCreatorConstants.SIDECARS_PARAMETERS_MAP,
        ByteString.copyFrom(kryoSerializer.asDeflatedBytes(sideCarsParametersMap)));
    return metadataDependency;
  }

  public String addDependenciesForPrimaryNode(YamlField artifactField, ArtifactStepParameters artifactStepParameters,
      Map<String, PlanCreationResponse> planCreationResponseMap) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField primaryYamlField =
        PrimaryArtifactsUtility.createPrimaryArtifactYamlFieldAndSetYamlUpdate(artifactField, yamlUpdates);

    String primaryId = primaryYamlField.getNode().getUuid();
    Map<String, ByteString> metadataDependency =
        prepareMetadataForPrimaryArtifactPlanCreator(primaryId, artifactStepParameters);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(primaryId, primaryYamlField);
    PlanCreationResponseBuilder primaryPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(primaryId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      primaryPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(primaryId, primaryPlanCreationResponse.build());
    return primaryId;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, ArtifactListConfig config, List<String> childrenNodeIds) {
    String artifactsId = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.UUID).toByteArray());

    ForkStepParameters stepParameters = ForkStepParameters.builder().parallelNodeIds(childrenNodeIds).build();
    return PlanNode.builder()
        .uuid(artifactsId)
        .stepType(ArtifactsStep.STEP_TYPE)
        .name(PlanCreatorConstants.ARTIFACTS_NODE_NAME)
        .identifier(YamlTypes.ARTIFACT_LIST_CONFIG)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                .build())
        .skipExpressionChain(false)
        .build();
  }

  @Value
  private static class ArtifactList {
    ArtifactInfo primary;
    Map<String, ArtifactInfo> sidecars;
  }

  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class ArtifactListBuilder {
    ArtifactInfoBuilder primary;
    Map<String, ArtifactInfoBuilder> sidecars;

    ArtifactListBuilder(ArtifactListConfig artifactListConfig) {
      if (artifactListConfig == null) {
        this.primary = null;
        this.sidecars = null;
        return;
      }

      PrimaryArtifact primarySpecWrapper = artifactListConfig.getPrimary();
      if (primarySpecWrapper != null) {
        this.primary = new ArtifactInfoBuilder(ArtifactStepParameters.builder()
                                                   .type(primarySpecWrapper.getSourceType())
                                                   .spec(primarySpecWrapper.getSpec()));
      } else {
        this.primary = null;
      }

      this.sidecars = new HashMap<>();
      if (EmptyPredicate.isNotEmpty(artifactListConfig.getSidecars())) {
        artifactListConfig.getSidecars().forEach(sc -> {
          SidecarArtifact sidecar = sc.getSidecar();
          this.sidecars.put(sidecar.getIdentifier(),
              new ArtifactInfoBuilder(ArtifactStepParameters.builder()
                                          .identifier(sidecar.getIdentifier())
                                          .type(sidecar.getSourceType())
                                          .spec(sidecar.getSpec())));
        });
      }
    }

    ArtifactList build() {
      return new ArtifactList(primary == null ? null : primary.build(),
          sidecars == null
              ? null
              : sidecars.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build())));
    }

    void addStageOverrides(StageOverridesConfig stageOverrides) {
      if (stageOverrides == null || stageOverrides.getArtifacts() == null) {
        return;
      }
      ArtifactListConfig artifactListConfig = stageOverrides.getArtifacts();
      addOverrides(artifactListConfig, ArtifactStepParametersBuilder::stageOverride);
    }

    private void addOverrides(
        ArtifactListConfig artifactListConfig, BiConsumer<ArtifactStepParametersBuilder, ArtifactConfig> consumer) {
      PrimaryArtifact primarySpecWrapper = artifactListConfig.getPrimary();
      if (primarySpecWrapper != null) {
        if (primary == null) {
          primary = new ArtifactInfoBuilder(ArtifactStepParameters.builder());
        }
        consumer.accept(primary.getBuilder(), primarySpecWrapper.getSpec());
      }

      if (EmptyPredicate.isNotEmpty(artifactListConfig.getSidecars())) {
        for (SidecarArtifactWrapper sidecarWrapper : artifactListConfig.getSidecars()) {
          SidecarArtifact sidecar = sidecarWrapper.getSidecar();
          ArtifactInfoBuilder artifactInfoBuilder = sidecars.computeIfAbsent(sidecar.getIdentifier(),
              identifier -> new ArtifactInfoBuilder(ArtifactStepParameters.builder().identifier(identifier)));
          consumer.accept(artifactInfoBuilder.getBuilder(), sidecar.getSpec());
        }
      }
    }
  }

  @Value
  public static class ArtifactInfo {
    ArtifactStepParameters params;
  }

  @Value
  private static class ArtifactInfoBuilder {
    ArtifactStepParametersBuilder builder;

    ArtifactInfo build() {
      return new ArtifactInfo(builder.build());
    }
  }
}