/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.artifact;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.steps.beans.ArtifactStepParameters;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactPlanCreatorTest extends CDNGTestBase {
  @Inject @InjectMocks ArtifactsPlanCreator artifactsPlanCreator;

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testPrepareMetadataForPrimaryArtifactsPlanCreator() {
    String uuid = UUIDGenerator.generateUuid();
    ArtifactStepParameters artifactStepParameters = ArtifactStepParameters.builder().build();
    Map<String, ByteString> metadataDependency =
        artifactsPlanCreator.prepareMetadataForPrimaryArtifactPlanCreator(uuid, artifactStepParameters);
    assertThat(metadataDependency.size()).isEqualTo(2);
    assertThat(metadataDependency.containsKey(YamlTypes.UUID)).isEqualTo(true);
    assertThat(metadataDependency.containsKey(PlanCreatorConstants.PRIMARY_STEP_PARAMETERS)).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(artifactsPlanCreator.getFieldClass()).isEqualTo(ArtifactListConfig.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = artifactsPlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YamlTypes.ARTIFACT_LIST_CONFIG)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.ARTIFACT_LIST_CONFIG).size()).isEqualTo(1);
    assertThat(supportedTypes.get(YamlTypes.ARTIFACT_LIST_CONFIG).contains(PlanCreatorUtils.ANY_TYPE)).isEqualTo(true);
  }

  private void checkForPrimaryMetadataDependency(PlanCreationResponse planCreationResponse, String nodeUuid) {
    assertThat(planCreationResponse.getDependencies().getDependenciesMap().containsKey(nodeUuid)).isEqualTo(true);
    assertThat(planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().size())
        .isEqualTo(2);
    assertThat(
        planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().containsKey(
            YamlTypes.UUID))
        .isEqualTo(true);
    assertThat(
        planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().containsKey(
            PlanCreatorConstants.PRIMARY_STEP_PARAMETERS))
        .isEqualTo(true);
  }

  private void checkForSidecarsMetadataDependency(PlanCreationResponse planCreationResponse, String nodeUuid) {
    assertThat(planCreationResponse.getDependencies().getDependenciesMap().containsKey(nodeUuid)).isEqualTo(true);
    assertThat(planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().size())
        .isEqualTo(2);
    assertThat(
        planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().containsKey(
            YamlTypes.UUID))
        .isEqualTo(true);
    assertThat(
        planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().containsKey(
            PlanCreatorConstants.SIDECARS_PARAMETERS_MAP))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForArtifactsHavingPrimaryYamlField() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/artifact/artifact_yaml_with_primary_yamlField.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField artifactField = YamlUtils.readTree(yaml);

    PlanCreationContext ctx = PlanCreationContext.builder().currentField(artifactField).build();
    ArtifactStepParameters artifactStepParameters = ArtifactStepParameters.builder().build();
    String nodeUuid = artifactsPlanCreator.addDependenciesForPrimaryNode(
        ctx.getCurrentField(), artifactStepParameters, planCreationResponseMap);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid)).isEqualTo("primary");
    assertThat(planCreationResponse1.getYamlUpdates()).isNull();
    checkForPrimaryMetadataDependency(planCreationResponse1, nodeUuid);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForArtifactsWithoutPrimaryYamlField() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/artifact/artifact_yaml_without_primary_yamlField.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField artifactField = YamlUtils.readTree(yaml);

    PlanCreationContext ctx = PlanCreationContext.builder().currentField(artifactField).build();
    ArtifactStepParameters artifactStepParameters = ArtifactStepParameters.builder().build();
    String nodeUuid = artifactsPlanCreator.addDependenciesForPrimaryNode(
        ctx.getCurrentField(), artifactStepParameters, planCreationResponseMap);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid)).isEqualTo("primary");
    assertThat(planCreationResponse1.getYamlUpdates()).isNotNull();
    checkForPrimaryMetadataDependency(planCreationResponse1, nodeUuid);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForSidecarsListsHavingEmptySideCarYamlField() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/artifact/artifact_yaml_with_empty_sidecar_yamlField.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField artifactField = YamlUtils.readTree(yaml);

    Map<String, ArtifactsPlanCreator.ArtifactInfo> sidecarArtifactInfo = new HashMap<>();
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(artifactField).build();
    String nodeUuid = artifactsPlanCreator.addDependenciesForSideCarList(
        ctx.getCurrentField(), artifactField.getNode().getUuid(), sidecarArtifactInfo, planCreationResponseMap);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid)).isEqualTo("sidecars");
    assertThat(planCreationResponse1.getYamlUpdates()).isNotNull();
    checkForSidecarsMetadataDependency(planCreationResponse1, nodeUuid);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForSidecarsListsWithoutSideCarYamlField() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/artifact/artifact_yaml_with_primary_yamlField.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField artifactField = YamlUtils.readTree(yaml);

    Map<String, ArtifactsPlanCreator.ArtifactInfo> sidecarArtifactInfo = new HashMap<>();
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(artifactField).build();
    String nodeUuid = artifactsPlanCreator.addDependenciesForSideCarList(
        ctx.getCurrentField(), artifactField.getNode().getUuid(), sidecarArtifactInfo, planCreationResponseMap);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid)).isEqualTo("sidecars");
    assertThat(planCreationResponse1.getYamlUpdates()).isNotNull();
    checkForSidecarsMetadataDependency(planCreationResponse1, nodeUuid);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForSidecarsListsWithSideCarYamlField() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile =
        classLoader.getResourceAsStream("cdng/plan/artifact/artifact_yaml_with_sidecars_yamlField.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField artifactField = YamlUtils.readTree(yaml);

    Map<String, ArtifactsPlanCreator.ArtifactInfo> sidecarArtifactInfo = new HashMap<>();
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(artifactField).build();
    String nodeUuid = artifactsPlanCreator.addDependenciesForSideCarList(
        ctx.getCurrentField(), artifactField.getNode().getUuid(), sidecarArtifactInfo, planCreationResponseMap);
    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid)).isEqualTo("sidecars");
    assertThat(planCreationResponse1.getYamlUpdates()).isNull();
    checkForSidecarsMetadataDependency(planCreationResponse1, nodeUuid);
  }
}