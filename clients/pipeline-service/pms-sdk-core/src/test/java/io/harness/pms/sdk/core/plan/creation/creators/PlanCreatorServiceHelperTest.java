/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.creators;

import static io.harness.pms.contracts.plan.RollbackModeBehaviour.PRESERVE;
import static io.harness.pms.contracts.plan.RollbackModeBehaviour.UNDEFINED_BEHAVIOUR;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.MergePlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.protobuf.ByteString;
import io.fabric8.utils.Lists;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanCreatorServiceHelperTest extends PmsSdkCoreTestBase {
  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testFindPlanCreator() throws IOException {
    List<PartialPlanCreator<?>> planCreators = Lists.newArrayList(new DummyChildrenPlanCreator());
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.extractPipelineField(YamlUtils.injectUuid(yamlContent));
    Optional<PartialPlanCreator<?>> partialPlanCreatorOptional =
        PlanCreatorServiceHelper.findPlanCreator(planCreators, yamlField, PipelineVersion.V0);
    assertThat(partialPlanCreatorOptional.isPresent()).isTrue();
    assertThat(partialPlanCreatorOptional.get().getClass()).isEqualTo(DummyChildrenPlanCreator.class);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testIsEmptyDependencies() {
    Dependencies dependencies = Dependencies.newBuilder().putDependencies("test", "test").build();
    assertThat(PlanCreatorServiceHelper.isEmptyDependencies(dependencies)).isFalse();
    assertThat(PlanCreatorServiceHelper.isEmptyDependencies(null)).isTrue();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testRemoveInitialDepdendencies() {
    Dependencies dependencies = Dependencies.newBuilder().putDependencies("test", "test").build();
    Dependencies initialDependencies = Dependencies.newBuilder().putDependencies("test", "test").build();

    assertThat(PlanCreatorServiceHelper.removeInitialDependencies(dependencies, initialDependencies))
        .isEqualTo(Dependencies.newBuilder().build());
    assertThat(
        PlanCreatorServiceHelper.removeInitialDependencies(Dependencies.newBuilder().build(), initialDependencies))
        .isEqualTo(Dependencies.newBuilder().build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandlePlanCreationResponsesHavingError() throws IOException {
    PlanCreationResponse planCreationResponse =
        PlanCreationResponse.builder().errorMessage("The plan creation has errored").build();
    MergePlanCreationResponse finalResponse = MergePlanCreationResponse.builder().build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    Dependencies dependencies =
        PlanCreatorServiceHelper.handlePlanCreationResponses(Lists.newArrayList(planCreationResponse), finalResponse,
            yamlContent, Dependencies.newBuilder().build(), new ArrayList<>());
    assertThat(dependencies).isEqualTo(Dependencies.newBuilder().build());
    assertThat(finalResponse.getErrorMessages().size()).isEqualTo(1);
    assertThat(finalResponse.getErrorMessages().get(0)).isEqualTo("The plan creation has errored");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandlePlanCreationResponses() throws IOException {
    PlanCreationResponse planCreationResponse = PlanCreationResponse.builder().build();
    MergePlanCreationResponse finalResponse = MergePlanCreationResponse.builder().build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    Dependencies deps =
        Dependencies.newBuilder().setYaml(yamlContent).putDependencies("test", "pipeline.stages").build();
    List<Map.Entry<String, String>> dependenciesList = new ArrayList<>(deps.getDependenciesMap().entrySet());

    Dependencies dependencies =
        PlanCreatorServiceHelper.handlePlanCreationResponses(Lists.newArrayList(planCreationResponse), finalResponse,
            yamlContent, Dependencies.newBuilder().build(), dependenciesList);
    assertThat(dependencies).isEqualTo(Dependencies.newBuilder().setYaml(yamlContent).build());
    assertThat(finalResponse.getErrorMessages().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandlePlanCreationResponsesNullResponse() throws IOException {
    MergePlanCreationResponse finalResponse = MergePlanCreationResponse.builder().build();
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    Dependencies deps =
        Dependencies.newBuilder().setYaml(yamlContent).putDependencies("test", "pipeline.stages").build();
    List<Map.Entry<String, String>> dependenciesList = new ArrayList<>(deps.getDependenciesMap().entrySet());
    List<PlanCreationResponse> planCreationResponses = new ArrayList<>();
    planCreationResponses.add(null);

    Dependencies dependencies = PlanCreatorServiceHelper.handlePlanCreationResponses(
        planCreationResponses, finalResponse, yamlContent, Dependencies.newBuilder().build(), dependenciesList);
    assertThat(dependencies).isEqualTo(Dependencies.newBuilder().setYaml(yamlContent).build());
    assertThat(finalResponse.getErrorMessages().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testFindPlanCreatorWithUnsupportedVersion() throws IOException {
    List<PartialPlanCreator<?>> planCreators = Lists.newArrayList(new DummyChildrenPlanCreatorV2());
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline.yaml");
    String yamlContent = Resources.toString(testFile, Charsets.UTF_8);
    YamlField yamlField = YamlUtils.extractPipelineField(YamlUtils.injectUuid(yamlContent));
    Optional<PartialPlanCreator<?>> partialPlanCreatorOptional =
        PlanCreatorServiceHelper.findPlanCreator(planCreators, yamlField, PipelineVersion.V1);
    assertThat(partialPlanCreatorOptional.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testDecorateResponseWithRollbackModeBehaviour() {
    PlanNode planNode1 = PlanNode.builder().uuid("u1").build();
    PlanNode planNode2 = PlanNode.builder().uuid("u2").build();
    PlanNode planNode3 = PlanNode.builder().uuid("u3").build();
    PlanCreationResponse noDependencies =
        PlanCreationResponse.builder().planNode(planNode1).node("u2", planNode2).node("u3", planNode3).build();
    PlanCreationResponse noDependenciesCopy =
        PlanCreationResponse.builder().planNode(planNode1).node("u2", planNode2).node("u3", planNode3).build();

    PlanCreatorServiceHelper.decorateResponseWithRollbackModeBehaviour(null, noDependencies);
    assertThat(noDependencies).isEqualTo(noDependenciesCopy);

    PlanCreatorServiceHelper.decorateResponseWithRollbackModeBehaviour(
        Dependency.newBuilder().setRollbackModeBehaviour(UNDEFINED_BEHAVIOUR).build(), noDependencies);
    assertThat(noDependencies).isEqualTo(noDependenciesCopy);

    PlanCreationResponse noDependenciesAndPlanNode =
        PlanCreationResponse.builder().node("u2", planNode2).node("u3", planNode3).build();
    PlanCreationResponse result = PlanCreationResponse.builder()
                                      .node("u2", planNode2)
                                      .node("u3", planNode3)
                                      .preservedNodesInRollbackMode(Arrays.asList("u2", "u3"))
                                      .build();
    PlanCreatorServiceHelper.decorateResponseWithRollbackModeBehaviour(
        Dependency.newBuilder().setRollbackModeBehaviour(PRESERVE).build(), noDependenciesAndPlanNode);
    assertThat(noDependenciesAndPlanNode).isEqualTo(result);

    ByteString randomByteString = ByteString.copyFromUtf8("random");
    PlanCreationResponse withDependencies =
        PlanCreationResponse.builder()
            .planNode(planNode1)
            .node("u2", planNode2)
            .node("u3", planNode3)
            .dependencies(Dependencies.newBuilder()
                              .putDependencies("du1", "fqn1")
                              .putDependencies("du2", "fqn2")
                              .putDependencyMetadata(
                                  "du1", Dependency.newBuilder().putMetadata("something", randomByteString).build())
                              .build())
            .build();
    PlanCreationResponse withDependenciesCopy =
        PlanCreationResponse.builder()
            .planNode(planNode1)
            .node("u2", planNode2)
            .node("u3", planNode3)
            .dependencies(Dependencies.newBuilder()
                              .putDependencies("du1", "fqn1")
                              .putDependencies("du2", "fqn2")
                              .putDependencyMetadata(
                                  "du1", Dependency.newBuilder().putMetadata("something", randomByteString).build())
                              .build())
            .build();

    PlanCreatorServiceHelper.decorateResponseWithRollbackModeBehaviour(null, withDependencies);
    assertThat(withDependencies).isEqualTo(withDependenciesCopy);

    PlanCreatorServiceHelper.decorateResponseWithRollbackModeBehaviour(
        Dependency.newBuilder().setRollbackModeBehaviour(UNDEFINED_BEHAVIOUR).build(), withDependencies);
    assertThat(withDependencies).isEqualTo(withDependenciesCopy);

    PlanCreatorServiceHelper.decorateResponseWithRollbackModeBehaviour(
        Dependency.newBuilder().setRollbackModeBehaviour(PRESERVE).build(), withDependencies);
    assertThat(withDependencies.getPreservedNodesInRollbackMode()).containsExactlyInAnyOrder("u1", "u2", "u3");
    Map<String, Dependency> dependencyMetadataMap = withDependencies.getDependencies().getDependencyMetadataMap();
    assertThat(dependencyMetadataMap.keySet()).containsExactly("du1", "du2");
    assertThat(dependencyMetadataMap.get("du1").getRollbackModeBehaviour()).isEqualTo(PRESERVE);
    assertThat(dependencyMetadataMap.get("du1").getMetadataMap().get("something")).isEqualTo(randomByteString);
    assertThat(dependencyMetadataMap.get("du2").getRollbackModeBehaviour()).isEqualTo(PRESERVE);
  }
}
