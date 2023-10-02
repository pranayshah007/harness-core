/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.manifest;
import static io.harness.cdng.manifest.ManifestType.HELM_SUPPORTED_MANIFEST_TYPES;
import static io.harness.cdng.manifest.ManifestType.K8S_SUPPORTED_MANIFEST_TYPES;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.manifest.ManifestsListConfigWrapper;
import io.harness.cdng.manifest.steps.ManifestStepParameters;
import io.harness.cdng.manifest.steps.ManifestStepParameters.ManifestStepParametersBuilder;
import io.harness.cdng.manifest.steps.ManifestsStep;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.StageOverridesConfig;
import io.harness.cdng.utilities.ManifestsUtility;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
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
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(HarnessTeam.CDC)
public class ManifestsPlanCreator extends ChildrenPlanCreator<ManifestsListConfigWrapper> {
  @Inject KryoSerializer kryoSerializer;

  public static final String SERVICE_ENTITY_DEFINITION_TYPE_KEY = "SERVICE_ENTITY_DEFINITION_TYPE_KEY";

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, ManifestsListConfigWrapper config) {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ManifestList manifestList = new ManifestList(new HashMap<>());
    // This is the actual service config passed from service plan creator
    // service v1
    ServiceDefinitionType serviceDefinitionType = null;
    if (ctx.getDependency().getMetadataMap().containsKey(YamlTypes.SERVICE_CONFIG)) {
      ServiceConfig serviceConfig = (ServiceConfig) kryoSerializer.asInflatedObject(
          ctx.getDependency().getMetadataMap().get(YamlTypes.SERVICE_CONFIG).toByteArray());

      List<ManifestConfigWrapper> manifestListConfig =
          serviceConfig.getServiceDefinition().getServiceSpec().getManifests();
      serviceDefinitionType = serviceConfig.getServiceDefinition().getType();
      ManifestListBuilder manifestListBuilder = new ManifestListBuilder(manifestListConfig);
      manifestListBuilder.addStageOverrides(serviceConfig.getStageOverrides());
      manifestList = manifestListBuilder.build();

    } else if (ctx.getDependency().getMetadataMap().containsKey(SERVICE_ENTITY_DEFINITION_TYPE_KEY)) {
      serviceDefinitionType = (ServiceDefinitionType) kryoSerializer.asInflatedObject(
          ctx.getDependency().getMetadataMap().get(SERVICE_ENTITY_DEFINITION_TYPE_KEY).toByteArray());
      final List<ManifestConfigWrapper> manifestListConfig =
          (List<ManifestConfigWrapper>) kryoSerializer.asInflatedObject(
              ctx.getDependency().getMetadataMap().get(YamlTypes.MANIFEST_LIST_CONFIG).toByteArray());

      ManifestListBuilder manifestListBuilder = new ManifestListBuilder(manifestListConfig);
      manifestList = manifestListBuilder.build();
    } else if (ctx.getDependency().getMetadataMap().containsKey(YamlTypes.SERVICE_ENTITY)) {
      NGServiceV2InfoConfig serviceV2InfoConfig = (NGServiceV2InfoConfig) kryoSerializer.asInflatedObject(
          ctx.getDependency().getMetadataMap().get(YamlTypes.SERVICE_ENTITY).toByteArray());

      List<ManifestConfigWrapper> manifestListConfig =
          serviceV2InfoConfig.getServiceDefinition().getServiceSpec().getManifests();
      serviceDefinitionType = serviceV2InfoConfig.getServiceDefinition().getType();
      ManifestListBuilder manifestListBuilder = new ManifestListBuilder(manifestListConfig);
      manifestList = manifestListBuilder.build();
    }

    if (EmptyPredicate.isEmpty(manifestList.getManifests())) {
      return planCreationResponseMap;
    }

    validateManifestList(serviceDefinitionType, manifestList);
    YamlField manifestsYamlField = ctx.getCurrentField();

    List<YamlNode> yamlNodes = Optional.of(manifestsYamlField.getNode().asArray()).orElse(Collections.emptyList());
    Map<String, YamlNode> manifestIdentifierToYamlNodeMap = yamlNodes.stream().collect(
        Collectors.toMap(e -> e.getField(YamlTypes.MANIFEST_CONFIG).getNode().getIdentifier(), k -> k));

    for (Map.Entry<String, ManifestInfo> identifierToManifestInfoEntry : manifestList.getManifests().entrySet()) {
      addDependenciesForIndividualManifest(manifestsYamlField, identifierToManifestInfoEntry.getKey(),
          identifierToManifestInfoEntry.getValue().getParams(), manifestIdentifierToYamlNodeMap,
          planCreationResponseMap);
    }

    return planCreationResponseMap;
  }
  /*
     addDependenciesForIndividualManifest() -> adds dependencies for a particular manifests yaml field. Manifest yaml
     field is fetched with the help of manifestIdentifier and manifestIdentifierToYamlNodeMap. If the identifier is not
     present in manifestIdentifierToYamlNodeMap, we will return the fqn of the first manifest element in manifests
     array. This can happen in case of useFromStage.
   */
  public String addDependenciesForIndividualManifest(YamlField manifestsYamlField, String manifestIdentifier,
      ManifestStepParameters stepParameters, Map<String, YamlNode> manifestIdentifierToYamlNodeMap,
      Map<String, PlanCreationResponse> planCreationResponseMap) {
    YamlField individualManifest = ManifestsUtility.fetchIndividualManifestYamlField(
        manifestsYamlField, manifestIdentifier, manifestIdentifierToYamlNodeMap);

    String individualManifestPlanNodeId = UUIDGenerator.generateUuid();
    Map<String, ByteString> metadataDependency =
        prepareMetadataForIndividualManifestPlanCreator(individualManifestPlanNodeId, stepParameters);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(individualManifestPlanNodeId, individualManifest);
    PlanCreationResponseBuilder individualManifestPlanResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                individualManifestPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    planCreationResponseMap.put(individualManifestPlanNodeId, individualManifestPlanResponse.build());
    return individualManifestPlanNodeId;
  }

  public Map<String, ByteString> prepareMetadataForIndividualManifestPlanCreator(
      String individualManifestPlanNodeId, ManifestStepParameters stepParameters) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(
        YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(individualManifestPlanNodeId)));
    metadataDependency.put(PlanCreatorConstants.MANIFEST_STEP_PARAMETER,
        ByteString.copyFrom(kryoSerializer.asDeflatedBytes(stepParameters)));
    return metadataDependency;
  }

  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, ManifestsListConfigWrapper config, List<String> childrenNodeIds) {
    String manifestsId = (String) kryoSerializer.asInflatedObject(
        ctx.getDependency().getMetadataMap().get(YamlTypes.UUID).toByteArray());
    ForkStepParameters stepParameters = ForkStepParameters.builder().parallelNodeIds(childrenNodeIds).build();
    return PlanNode.builder()
        .uuid(manifestsId)
        .stepType(ManifestsStep.STEP_TYPE)
        .name(PlanCreatorConstants.MANIFESTS_NODE_NAME)
        .identifier(YamlTypes.MANIFEST_LIST_CONFIG)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                .build())
        .skipExpressionChain(false)
        .build();
  }

  @Override
  public Class<ManifestsListConfigWrapper> getFieldClass() {
    return ManifestsListConfigWrapper.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YamlTypes.MANIFEST_LIST_CONFIG, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  private void validateManifestList(ServiceDefinitionType serviceDefinitionType, ManifestList manifestList) {
    if (serviceDefinitionType == null) {
      return;
    }

    switch (serviceDefinitionType) {
      case KUBERNETES:
        validateDuplicateManifests(
            manifestList, K8S_SUPPORTED_MANIFEST_TYPES, ServiceDefinitionType.KUBERNETES.getYamlName());
        break;
      case NATIVE_HELM:
        validateDuplicateManifests(
            manifestList, HELM_SUPPORTED_MANIFEST_TYPES, ServiceDefinitionType.NATIVE_HELM.getYamlName());
        break;
      default:
    }
  }

  private void validateDuplicateManifests(ManifestList manifestList, Set<String> supported, String deploymentType) {
    Map<String, String> manifestIdTypeMap =
        manifestList.getManifests()
            .entrySet()
            .stream()
            .filter(entry -> supported.contains(entry.getValue().getParams().getType()))
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getParams().getType()));

    SvcEnvV2ManifestValidator.throwMultipleManifestsExceptionIfApplicable(manifestIdTypeMap, deploymentType, supported);
  }

  @Value
  private static class ManifestList {
    Map<String, ManifestInfo> manifests;
  }

  @Data
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private static class ManifestListBuilder {
    Map<String, ManifestInfoBuilder> manifests;

    ManifestListBuilder(List<ManifestConfigWrapper> manifestConfigList) {
      if (manifestConfigList == null) {
        this.manifests = null;
        return;
      }

      this.manifests = new HashMap<>();
      if (EmptyPredicate.isNotEmpty(manifestConfigList)) {
        manifestConfigList.forEach(mcw -> {
          ManifestConfig mc = mcw.getManifest();
          if (mc.getIdentifier() == null) {
            throw new InvalidRequestException("Service manifests identifier cannot be empty.");
          }
          if (manifests.containsKey(mc.getIdentifier())) {
            throw new InvalidRequestException(
                String.format("Duplicate identifier: [%s] in manifests", mc.getIdentifier()));
          }
          this.manifests.put(mc.getIdentifier(),
              new ManifestInfoBuilder(ManifestStepParameters.builder()
                                          .identifier(mc.getIdentifier())
                                          .type(mc.getType().getDisplayName())
                                          .spec(mc.getSpec())
                                          .order(this.manifests.size())));
        });
      }
    }

    ManifestList build() {
      return new ManifestList(manifests == null
              ? null
              : manifests.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build())));
    }

    void addStageOverrides(StageOverridesConfig stageOverrides) {
      if (stageOverrides == null || stageOverrides.getManifests() == null) {
        return;
      }
      List<ManifestConfigWrapper> manifestConfigList = stageOverrides.getManifests();
      addOverrides(manifestConfigList, ManifestStepParametersBuilder::stageOverride);
    }

    private void addOverrides(List<ManifestConfigWrapper> manifestConfigList,
        BiConsumer<ManifestStepParametersBuilder, ManifestAttributes> consumer) {
      if (EmptyPredicate.isEmpty(manifestConfigList)) {
        return;
      }

      for (ManifestConfigWrapper manifestConfigWrapper : manifestConfigList) {
        ManifestConfig mc = manifestConfigWrapper.getManifest();
        ManifestInfoBuilder manifestInfoBuilder = manifests.computeIfAbsent(mc.getIdentifier(),
            identifier
            -> new ManifestInfoBuilder(
                ManifestStepParameters.builder().order(manifests.size()).identifier(identifier)));
        consumer.accept(manifestInfoBuilder.getBuilder(), mc.getSpec());
      }
    }
  }

  @Value
  private static class ManifestInfo {
    ManifestStepParameters params;
  }

  @Value
  private static class ManifestInfoBuilder {
    ManifestStepParametersBuilder builder;

    ManifestInfo build() {
      return new ManifestInfo(builder.build());
    }
  }
}
