/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifactWrapper;
import io.harness.cdng.manifest.ManifestConfigType;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceUseFromStage;
import io.harness.cdng.service.beans.StageOverridesConfig;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ServiceDefinitionPlanCreatorHelperTest extends CategoryTest {
  @Mock KryoSerializer kryoSerializer;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testPrepareMetadataV1() {
    String uuid = UUIDGenerator.generateUuid();
    ServiceConfig serviceConfig = ServiceConfig.builder().build();
    Mockito.when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());
    Map<String, ByteString> metadataDependency =
        ServiceDefinitionPlanCreatorHelper.prepareMetadata(uuid, serviceConfig, kryoSerializer);
    assertThat(metadataDependency.size()).isEqualTo(2);
    assertThat(metadataDependency.containsKey(YamlTypes.UUID)).isEqualTo(true);
    assertThat(metadataDependency.containsKey(YamlTypes.SERVICE_CONFIG)).isEqualTo(true);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testPrepareMetadataV2() {
    String uuid = UUIDGenerator.generateUuid();
    NGServiceV2InfoConfig serviceConfig = NGServiceV2InfoConfig.builder().build();
    Mockito.when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());
    Map<String, ByteString> metadataDependency =
        ServiceDefinitionPlanCreatorHelper.prepareMetadataV2(uuid, serviceConfig, kryoSerializer);
    assertThat(metadataDependency.size()).isEqualTo(2);
    assertThat(metadataDependency.containsKey(YamlTypes.UUID)).isEqualTo(true);
    assertThat(metadataDependency.containsKey(YamlTypes.SERVICE_ENTITY)).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateCreatePlanNodeForArtifacts() {
    DockerHubArtifactConfig primaryArtifact =
        DockerHubArtifactConfig.builder().primaryArtifact(true).identifier("ARTIFACT1").build();
    DockerHubArtifactConfig sidecarArtifact =
        DockerHubArtifactConfig.builder().primaryArtifact(false).identifier("ARTIFACT2").build();
    ArtifactListConfig artifactListConfig1 =
        ArtifactListConfig.builder()
            .primary(PrimaryArtifact.builder().spec(primaryArtifact).build())
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    // Case1: having both primary and sidecars artifacts
    ServiceConfig serviceConfig1 =
        ServiceConfig.builder()
            .serviceDefinition(ServiceDefinition.builder()
                                   .serviceSpec(KubernetesServiceSpec.builder().artifacts(artifactListConfig1).build())
                                   .build())
            .build();
    boolean result = ServiceDefinitionPlanCreatorHelper.validateCreatePlanNodeForArtifacts(serviceConfig1);
    assertThat(result).isEqualTo(true);

    // Case2: having none primary and sidecars artifacts
    ServiceConfig serviceConfig2 =
        ServiceConfig.builder()
            .serviceDefinition(
                ServiceDefinition.builder()
                    .serviceSpec(
                        KubernetesServiceSpec.builder().artifacts(ArtifactListConfig.builder().build()).build())
                    .build())
            .build();
    result = ServiceDefinitionPlanCreatorHelper.validateCreatePlanNodeForArtifacts(serviceConfig2);
    assertThat(result).isEqualTo(false);

    // Case3: having only sidecars artifacts
    ArtifactListConfig artifactListConfig3 =
        ArtifactListConfig.builder()
            .sidecar(SidecarArtifactWrapper.builder()
                         .sidecar(SidecarArtifact.builder().spec(sidecarArtifact).build())
                         .build())
            .build();

    ServiceConfig serviceConfig3 =
        ServiceConfig.builder()
            .serviceDefinition(ServiceDefinition.builder()
                                   .serviceSpec(KubernetesServiceSpec.builder().artifacts(artifactListConfig3).build())
                                   .build())
            .build();
    result = ServiceDefinitionPlanCreatorHelper.validateCreatePlanNodeForArtifacts(serviceConfig3);
    assertThat(result).isEqualTo(true);

    // Case4: having only primary artifacts
    ArtifactListConfig artifactListConfig4 =
        ArtifactListConfig.builder().primary(PrimaryArtifact.builder().spec(primaryArtifact).build()).build();

    ServiceConfig serviceConfig4 =
        ServiceConfig.builder()
            .serviceDefinition(ServiceDefinition.builder()
                                   .serviceSpec(KubernetesServiceSpec.builder().artifacts(artifactListConfig4).build())
                                   .build())
            .build();
    result = ServiceDefinitionPlanCreatorHelper.validateCreatePlanNodeForArtifacts(serviceConfig4);
    assertThat(result).isEqualTo(true);

    // StageOverride cases

    // Case1: having both primary and sidecars artifacts
    ServiceConfig stageOverrideServiceConfig1 =
        ServiceConfig.builder()
            .serviceDefinition(
                ServiceDefinition.builder()
                    .serviceSpec(
                        KubernetesServiceSpec.builder().artifacts(ArtifactListConfig.builder().build()).build())
                    .build())
            .stageOverrides(StageOverridesConfig.builder().artifacts(artifactListConfig1).build())
            .build();
    result = ServiceDefinitionPlanCreatorHelper.validateCreatePlanNodeForArtifacts(stageOverrideServiceConfig1);
    assertThat(result).isEqualTo(true);

    // Case2: having none primary and sidecars artifacts
    ServiceConfig stageOverrideServiceConfig2 =
        ServiceConfig.builder()
            .serviceDefinition(
                ServiceDefinition.builder()
                    .serviceSpec(
                        KubernetesServiceSpec.builder().artifacts(ArtifactListConfig.builder().build()).build())
                    .build())
            .stageOverrides(StageOverridesConfig.builder().artifacts(ArtifactListConfig.builder().build()).build())
            .build();
    result = ServiceDefinitionPlanCreatorHelper.validateCreatePlanNodeForArtifacts(stageOverrideServiceConfig2);
    assertThat(result).isEqualTo(false);

    // Case3: having only sidecars artifacts
    ServiceConfig stageOverrideServiceConfig3 =
        ServiceConfig.builder()
            .serviceDefinition(
                ServiceDefinition.builder()
                    .serviceSpec(
                        KubernetesServiceSpec.builder().artifacts(ArtifactListConfig.builder().build()).build())
                    .build())
            .stageOverrides(StageOverridesConfig.builder().artifacts(artifactListConfig3).build())
            .build();
    result = ServiceDefinitionPlanCreatorHelper.validateCreatePlanNodeForArtifacts(stageOverrideServiceConfig3);
    assertThat(result).isEqualTo(true);

    // Case4: having only primary artifacts
    ServiceConfig stageOverrideServiceConfig4 =
        ServiceConfig.builder()
            .serviceDefinition(
                ServiceDefinition.builder()
                    .serviceSpec(
                        KubernetesServiceSpec.builder().artifacts(ArtifactListConfig.builder().build()).build())
                    .build())
            .stageOverrides(StageOverridesConfig.builder().artifacts(artifactListConfig4).build())
            .build();
    result = ServiceDefinitionPlanCreatorHelper.validateCreatePlanNodeForArtifacts(stageOverrideServiceConfig4);
    assertThat(result).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForArtifactsWithServiceDefinition() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream("cdng/plan/service_plan_creator_test1.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    ServiceConfig actualServiceConfig = ServiceConfig.builder().build();
    Mockito.when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());
    String nodeUuid = ServiceDefinitionPlanCreatorHelper.addDependenciesForArtifacts(
        serviceField.getNode(), planCreationResponseMap, actualServiceConfig, kryoSerializer);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("serviceDefinition/spec/artifacts");
    assertThat(planCreationResponse1.getYamlUpdates()).isNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForArtifactsWithUseFromStageWithoutStageOverride() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig actualServiceConfig =
        ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage1").build()).build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream("cdng/plan/service_plan_creator_test2.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    Mockito.when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());
    String nodeUuid = ServiceDefinitionPlanCreatorHelper.addDependenciesForArtifacts(
        serviceField.getNode(), planCreationResponseMap, actualServiceConfig, kryoSerializer);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("stageOverrides/artifacts");
    assertThat(planCreationResponse1.getYamlUpdates().getFqnToYamlCount()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForArtifactsWithUseFromStageWithoutArtifacts() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig actualServiceConfig =
        ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage1").build()).build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream("cdng/plan/service_plan_creator_test3.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    Mockito.when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());
    String nodeUuid = ServiceDefinitionPlanCreatorHelper.addDependenciesForArtifacts(
        serviceField.getNode(), planCreationResponseMap, actualServiceConfig, kryoSerializer);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("stageOverrides/artifacts");

    assertThat(planCreationResponse1.getYamlUpdates().getFqnToYamlCount()).isEqualTo(1);
  }

  private void checksForDependencies(PlanCreationResponse planCreationResponse, String nodeUuid) {
    assertThat(planCreationResponse.getDependencies().getDependenciesMap().containsKey(nodeUuid)).isEqualTo(true);
    assertThat(planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().size())
        .isEqualTo(2);
    assertThat(
        planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().containsKey(
            YamlTypes.UUID))
        .isEqualTo(true);
    assertThat(
        planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().containsKey(
            YamlTypes.SERVICE_CONFIG))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForManifestsHavingServiceDefinition() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig actualServiceConfig = ServiceConfig.builder().build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/manifests/manifests_test_with_service_definition.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    Mockito.when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());
    String nodeUuid = ServiceDefinitionPlanCreatorHelper.addDependenciesForManifests(
        serviceField.getNode(), planCreationResponseMap, actualServiceConfig, kryoSerializer);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("serviceDefinition/spec/manifests");

    assertThat(planCreationResponse1.getYamlUpdates()).isNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForManifestsWithStageOverrideHavingEmptyManifests() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig actualServiceConfig =
        ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage1").build()).build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/manifests/manifests_test_with_stage_override_manifests_empty.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    Mockito.when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());
    String nodeUuid = ServiceDefinitionPlanCreatorHelper.addDependenciesForManifests(
        serviceField.getNode(), planCreationResponseMap, actualServiceConfig, kryoSerializer);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("stageOverrides/manifests");

    assertThat(planCreationResponse1.getYamlUpdates()).isNotNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForManifestsWithStageOverrideHavingWithoutManifests() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig actualServiceConfig =
        ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage1").build()).build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/manifests/manifests_test_with_stage_override_without_manifests.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    Mockito.when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());
    String nodeUuid = ServiceDefinitionPlanCreatorHelper.addDependenciesForManifests(
        serviceField.getNode(), planCreationResponseMap, actualServiceConfig, kryoSerializer);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("stageOverrides/manifests");

    assertThat(planCreationResponse1.getYamlUpdates()).isNotNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForManifestsWithoutStageOverride() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig actualServiceConfig =
        ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage1").build()).build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/manifests/manifests_test_without_stage_override.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    Mockito.when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());
    String nodeUuid = ServiceDefinitionPlanCreatorHelper.addDependenciesForManifests(
        serviceField.getNode(), planCreationResponseMap, actualServiceConfig, kryoSerializer);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("stageOverrides/manifests");

    assertThat(planCreationResponse1.getYamlUpdates()).isNotNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForManifestsWithManifestUnderStagesOverrides() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ServiceConfig actualServiceConfig =
        ServiceConfig.builder().useFromStage(ServiceUseFromStage.builder().stage("stage1").build()).build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/manifests/manifests_test_with_manifests_under_stageoverride.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField serviceField = YamlUtils.readTree(yaml);

    Mockito.when(kryoSerializer.asDeflatedBytes(any())).thenReturn("".getBytes());
    String nodeUuid = ServiceDefinitionPlanCreatorHelper.addDependenciesForManifests(
        serviceField.getNode(), planCreationResponseMap, actualServiceConfig, kryoSerializer);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    checksForDependencies(planCreationResponse1, nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid))
        .isEqualTo("stageOverrides/manifests");

    assertThat(planCreationResponse1.getYamlUpdates()).isNull();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testValidateCreatePlanNodeForManifests() {
    ManifestConfigWrapper k8sManifest =
        ManifestConfigWrapper.builder()
            .manifest(ManifestConfig.builder().identifier("test").type(ManifestConfigType.K8_MANIFEST).build())
            .build();
    ManifestConfigWrapper valuesManifest =
        ManifestConfigWrapper.builder()
            .manifest(ManifestConfig.builder().identifier("test").type(ManifestConfigType.VALUES).build())
            .build();

    // Case1: having manifests in service definition
    ServiceConfig serviceConfig =
        ServiceConfig.builder()
            .serviceDefinition(
                ServiceDefinition.builder()
                    .serviceSpec(
                        KubernetesServiceSpec.builder().manifests(Arrays.asList(k8sManifest, valuesManifest)).build())
                    .build())
            .build();
    boolean result = ServiceDefinitionPlanCreatorHelper.shouldCreatePlanNodeForManifests(serviceConfig);
    assertThat(result).isEqualTo(true);

    // Case2: having empty list of manifests
    serviceConfig =
        ServiceConfig.builder()
            .serviceDefinition(ServiceDefinition.builder()
                                   .serviceSpec(KubernetesServiceSpec.builder().manifests(new ArrayList<>()).build())
                                   .build())
            .build();
    result = ServiceDefinitionPlanCreatorHelper.shouldCreatePlanNodeForManifests(serviceConfig);
    assertThat(result).isEqualTo(false);

    // StageOverrides: having non-empty manifests list
    serviceConfig =
        ServiceConfig.builder()
            .serviceDefinition(ServiceDefinition.builder().serviceSpec(KubernetesServiceSpec.builder().build()).build())
            .stageOverrides(
                StageOverridesConfig.builder().manifests(Arrays.asList(k8sManifest, valuesManifest)).build())
            .build();
    result = ServiceDefinitionPlanCreatorHelper.shouldCreatePlanNodeForManifests(serviceConfig);
    assertThat(result).isEqualTo(true);
  }
}
