/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.utilities.ArtifactsUtility;
import io.harness.cdng.utilities.ManifestsUtility;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse.PlanCreationResponseBuilder;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;

import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

/**
 * Contains method useful for serviceDefinition plan creator
 */
@UtilityClass
public class ServiceDefinitionPlanCreatorHelper {
  public Map<String, ByteString> prepareMetadata(
      String planNodeId, ServiceConfig actualServiceConfig, KryoSerializer kryoSerializer) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(planNodeId)));
    // TODO: Find an efficient way to not pass whole service config
    metadataDependency.put(
        YamlTypes.SERVICE_CONFIG, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(actualServiceConfig)));
    return metadataDependency;
  }

  public Map<String, ByteString> prepareMetadataV2(
      String planNodeId, NGServiceV2InfoConfig serviceV2Config, KryoSerializer kryoSerializer) {
    Map<String, ByteString> metadataDependency = new HashMap<>();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(planNodeId)));
    // TODO: Find an efficient way to not pass whole service entity
    metadataDependency.put(
        YamlTypes.SERVICE_ENTITY, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(serviceV2Config)));
    return metadataDependency;
  }

  boolean validateCreatePlanNodeForArtifacts(ServiceConfig actualServiceConfig) {
    ArtifactListConfig artifactListConfig = actualServiceConfig.getServiceDefinition().getServiceSpec().getArtifacts();

    // Contains either primary artifacts or side-car artifacts
    if (artifactListConfig != null) {
      if (artifactListConfig.getPrimary() != null || EmptyPredicate.isNotEmpty(artifactListConfig.getSidecars())) {
        return true;
      }
    }

    if (actualServiceConfig.getStageOverrides() != null
        && actualServiceConfig.getStageOverrides().getArtifacts() != null) {
      return actualServiceConfig.getStageOverrides().getArtifacts().getPrimary() != null
          || EmptyPredicate.isNotEmpty(actualServiceConfig.getStageOverrides().getArtifacts().getSidecars());
    }

    return false;
  }

  boolean validateCreatePlanNodeForArtifactsV2(NGServiceV2InfoConfig serviceConfig) {
    ArtifactListConfig artifactListConfig = serviceConfig.getServiceDefinition().getServiceSpec().getArtifacts();

    // Contains either primary artifacts or side-car artifacts
    if (artifactListConfig != null) {
      return artifactListConfig.getPrimary() != null || EmptyPredicate.isNotEmpty(artifactListConfig.getSidecars());
    }
    return false;
  }

  String addDependenciesForArtifacts(YamlNode serviceConfigNode,
      Map<String, PlanCreationResponse> planCreationResponseMap, ServiceConfig actualServiceConfig,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    boolean isUseFromStage = actualServiceConfig.getUseFromStage() != null;
    YamlField artifactYamlField =
        ArtifactsUtility.fetchArtifactYamlFieldAndSetYamlUpdates(serviceConfigNode, isUseFromStage, yamlUpdates);
    String artifactsPlanNodeId = UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency =
        prepareMetadata(artifactsPlanNodeId, actualServiceConfig, kryoSerializer);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(artifactsPlanNodeId, artifactYamlField);
    PlanCreationResponseBuilder artifactPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                artifactsPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      artifactPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(artifactsPlanNodeId, artifactPlanCreationResponse.build());
    return artifactsPlanNodeId;
  }

  String addDependenciesForArtifactsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField artifactYamlField =
        ArtifactsUtility.fetchArtifactYamlFieldAndSetYamlUpdates(serviceV2Node, false, yamlUpdates);
    String artifactsPlanNodeId = UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency =
        prepareMetadataV2(artifactsPlanNodeId, serviceV2Config, kryoSerializer);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(artifactsPlanNodeId, artifactYamlField);
    PlanCreationResponseBuilder artifactPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                artifactsPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      artifactPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(artifactsPlanNodeId, artifactPlanCreationResponse.build());
    return artifactsPlanNodeId;
  }

  String addDependenciesForManifests(YamlNode serviceConfigNode,
      Map<String, PlanCreationResponse> planCreationResponseMap, ServiceConfig actualServiceConfig,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    boolean isUseFromStage = actualServiceConfig.getUseFromStage() != null;
    YamlField manifestsYamlField =
        ManifestsUtility.fetchManifestsYamlFieldAndSetYamlUpdates(serviceConfigNode, isUseFromStage, yamlUpdates);
    String manifestsPlanNodeId = "manifests-" + UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency =
        prepareMetadata(manifestsPlanNodeId, actualServiceConfig, kryoSerializer);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(manifestsPlanNodeId, manifestsYamlField);
    PlanCreationResponseBuilder manifestsPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                manifestsPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      manifestsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(manifestsPlanNodeId, manifestsPlanCreationResponse.build());
    return manifestsPlanNodeId;
  }

  String addDependenciesForManifestsV2(YamlNode serviceV2Node,
      Map<String, PlanCreationResponse> planCreationResponseMap, NGServiceV2InfoConfig serviceV2Config,
      KryoSerializer kryoSerializer) {
    YamlUpdates.Builder yamlUpdates = YamlUpdates.newBuilder();
    YamlField manifestsYamlField =
        ManifestsUtility.fetchManifestsYamlFieldAndSetYamlUpdates(serviceV2Node, false, yamlUpdates);
    String manifestsPlanNodeId = "manifests-" + UUIDGenerator.generateUuid();

    Map<String, ByteString> metadataDependency =
        prepareMetadataV2(manifestsPlanNodeId, serviceV2Config, kryoSerializer);

    Map<String, YamlField> dependenciesMap = new HashMap<>();
    dependenciesMap.put(manifestsPlanNodeId, manifestsYamlField);
    PlanCreationResponseBuilder manifestsPlanCreationResponse = PlanCreationResponse.builder().dependencies(
        DependenciesUtils.toDependenciesProto(dependenciesMap)
            .toBuilder()
            .putDependencyMetadata(
                manifestsPlanNodeId, Dependency.newBuilder().putAllMetadata(metadataDependency).build())
            .build());
    if (yamlUpdates.getFqnToYamlCount() > 0) {
      manifestsPlanCreationResponse.yamlUpdates(yamlUpdates.build());
    }
    planCreationResponseMap.put(manifestsPlanNodeId, manifestsPlanCreationResponse.build());
    return manifestsPlanNodeId;
  }

  boolean shouldCreatePlanNodeForManifests(ServiceConfig actualServiceConfig) {
    List<ManifestConfigWrapper> manifests = actualServiceConfig.getServiceDefinition().getServiceSpec().getManifests();

    // Contains either manifests or overrides or nothing.
    if (EmptyPredicate.isNotEmpty(manifests)) {
      return true;
    }

    return actualServiceConfig.getStageOverrides() != null
        && actualServiceConfig.getStageOverrides().getManifests() != null
        && EmptyPredicate.isNotEmpty(actualServiceConfig.getStageOverrides().getManifests());
  }

  boolean shouldCreatePlanNodeForManifestsV2(NGServiceV2InfoConfig serviceV2InfoConfig) {
    List<ManifestConfigWrapper> manifests = serviceV2InfoConfig.getServiceDefinition().getServiceSpec().getManifests();

    // Contains either manifests or not.
    return EmptyPredicate.isNotEmpty(manifests);
  }
}
