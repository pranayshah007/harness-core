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
import io.harness.cdng.artifact.bean.yaml.SidecarsListWrapper;
import io.harness.cdng.artifact.steps.beans.ArtifactStepParameters;
import io.harness.cdng.artifact.steps.constants.SidecarsStepConstants;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.fork.ForkStepParameters;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@OwnedBy(HarnessTeam.CDC)
public class SideCarListPlanCreatorTest extends CDNGTestBase {
  @Inject KryoSerializer kryoSerializer;
  @Inject @InjectMocks SideCarListPlanCreator sidecarListPlanCreator;

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testPrepareMetadataForPrimaryArtifactsPlanCreator() {
    String uuid = UUIDGenerator.generateUuid();
    ArtifactStepParameters artifactStepParameters = ArtifactStepParameters.builder().build();
    Map<String, ByteString> metadataDependency =
        sidecarListPlanCreator.prepareMetadataForIndividualSideCarListPlanCreator(uuid, artifactStepParameters);
    assertThat(metadataDependency.size()).isEqualTo(2);
    assertThat(metadataDependency.containsKey(YamlTypes.UUID)).isEqualTo(true);
    assertThat(metadataDependency.containsKey(PlanCreatorConstants.SIDECAR_STEP_PARAMETERS)).isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(sidecarListPlanCreator.getFieldClass()).isEqualTo(SidecarsListWrapper.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    Map<String, Set<String>> supportedTypes = sidecarListPlanCreator.getSupportedTypes();
    assertThat(supportedTypes.containsKey(YamlTypes.SIDECARS_ARTIFACT_CONFIG)).isEqualTo(true);
    assertThat(supportedTypes.get(YamlTypes.SIDECARS_ARTIFACT_CONFIG).size()).isEqualTo(1);
    assertThat(supportedTypes.get(YamlTypes.SIDECARS_ARTIFACT_CONFIG).contains(PlanCreatorUtils.ANY_TYPE))
        .isEqualTo(true);
  }

  private void checkForIndividualSideCarsMetadataDependency(
      PlanCreationResponse planCreationResponse, String nodeUuid) {
    assertThat(planCreationResponse.getDependencies().getDependenciesMap().containsKey(nodeUuid)).isEqualTo(true);
    assertThat(planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().size())
        .isEqualTo(2);
    assertThat(
        planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().containsKey(
            YamlTypes.UUID))
        .isEqualTo(true);
    assertThat(
        planCreationResponse.getDependencies().getDependencyMetadataMap().get(nodeUuid).getMetadataMap().containsKey(
            PlanCreatorConstants.SIDECAR_STEP_PARAMETERS))
        .isEqualTo(true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testAddDependenciesForIndividualSideCars() throws IOException {
    LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream("cdng/plan/artifact/sidecars_list_yamlField.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField sidecarsListYamlField = YamlUtils.readTree(yaml);

    List<YamlNode> yamlNodes = Optional.of(sidecarsListYamlField.getNode().asArray()).orElse(Collections.emptyList());
    Map<String, YamlNode> mapIdentifierWithYamlNode = yamlNodes.stream().collect(
        Collectors.toMap(e -> e.getField(YamlTypes.SIDECAR_ARTIFACT_CONFIG).getNode().getIdentifier(), k -> k));

    String sideCarIdentifier = yamlNodes.get(0).getField("sidecar").getNode().getIdentifier();
    PlanCreationContext ctx = PlanCreationContext.builder().currentField(sidecarsListYamlField).build();
    ArtifactStepParameters artifactStepParameters = ArtifactStepParameters.builder().build();
    String nodeUuid = sidecarListPlanCreator.addDependenciesForIndividualSideCar(ctx.getCurrentField(),
        sideCarIdentifier, artifactStepParameters, mapIdentifierWithYamlNode, planCreationResponseMap);

    assertThat(planCreationResponseMap.size()).isEqualTo(1);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    PlanCreationResponse planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid)).isEqualTo("[0]/sidecar");
    assertThat(planCreationResponse1.getYamlUpdates()).isNull();
    checkForIndividualSideCarsMetadataDependency(planCreationResponse1, nodeUuid);

    // For yamlNode1
    sideCarIdentifier = yamlNodes.get(1).getField("sidecar").getNode().getIdentifier();
    nodeUuid = sidecarListPlanCreator.addDependenciesForIndividualSideCar(ctx.getCurrentField(), sideCarIdentifier,
        artifactStepParameters, mapIdentifierWithYamlNode, planCreationResponseMap);

    assertThat(planCreationResponseMap.size()).isEqualTo(2);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid)).isEqualTo("[1]/sidecar");
    assertThat(planCreationResponse1.getYamlUpdates()).isNull();
    checkForIndividualSideCarsMetadataDependency(planCreationResponse1, nodeUuid);

    // case3: yamlNode which is not present in map
    nodeUuid = sidecarListPlanCreator.addDependenciesForIndividualSideCar(ctx.getCurrentField(), "identifier",
        artifactStepParameters, mapIdentifierWithYamlNode, planCreationResponseMap);

    assertThat(planCreationResponseMap.size()).isEqualTo(3);
    assertThat(planCreationResponseMap.containsKey(nodeUuid)).isEqualTo(true);
    planCreationResponse1 = planCreationResponseMap.get(nodeUuid);
    assertThat(planCreationResponse1.getDependencies().getDependenciesMap().get(nodeUuid)).isEqualTo("[0]/sidecar");
    assertThat(planCreationResponse1.getYamlUpdates()).isNull();
    checkForIndividualSideCarsMetadataDependency(planCreationResponse1, nodeUuid);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetParentNode() {
    List<String> childrenNodeId = Arrays.asList("child1", "child2");
    HashMap<String, ByteString> metadataDependency = new HashMap<>();
    String uuid = UUIDGenerator.generateUuid();
    metadataDependency.put(YamlTypes.UUID, ByteString.copyFrom(kryoSerializer.asDeflatedBytes(uuid)));
    Dependency dependency = Dependency.newBuilder().putAllMetadata(metadataDependency).build();
    PlanCreationContext ctx = PlanCreationContext.builder().dependency(dependency).build();
    PlanNode planForParentNode = sidecarListPlanCreator.createPlanForParentNode(ctx, null, childrenNodeId);
    assertThat(planForParentNode.getUuid()).isEqualTo(uuid);
    assertThat(planForParentNode.getStepType()).isEqualTo(SidecarsStepConstants.STEP_TYPE);
    assertThat(planForParentNode.getName()).isEqualTo(PlanCreatorConstants.SIDECARS_NODE_NAME);
    assertThat(planForParentNode.getStepParameters())
        .isEqualTo(ForkStepParameters.builder().parallelNodeIds(childrenNodeId).build());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNode() throws IOException {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream("cdng/plan/artifact/sidecars_list_yamlField.yml");
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    yaml = YamlUtils.injectUuid(yaml);
    YamlField sidecarsListYamlField = YamlUtils.readTree(yaml);

    HashMap<String, ByteString> metadataDependency = new HashMap<>();
    Map<String, ArtifactStepParameters> sideCarsParametersMap = new HashMap<>();
    sideCarsParametersMap.put("sidecar1", ArtifactStepParameters.builder().identifier("sidecar1").build());
    sideCarsParametersMap.put("sidecar2", ArtifactStepParameters.builder().identifier("sidecar2").build());
    sideCarsParametersMap.put("sidecar3", ArtifactStepParameters.builder().identifier("sidecar3").build());
    metadataDependency.put(PlanCreatorConstants.SIDECARS_PARAMETERS_MAP,
        ByteString.copyFrom(kryoSerializer.asDeflatedBytes(sideCarsParametersMap)));
    Dependency dependency = Dependency.newBuilder().putAllMetadata(metadataDependency).build();
    PlanCreationContext ctx =
        PlanCreationContext.builder().currentField(sidecarsListYamlField).dependency(dependency).build();

    LinkedHashMap<String, PlanCreationResponse> planForChildrenNodes =
        sidecarListPlanCreator.createPlanForChildrenNodes(ctx, null);
    assertThat(planForChildrenNodes.size()).isEqualTo(3);
  }
}