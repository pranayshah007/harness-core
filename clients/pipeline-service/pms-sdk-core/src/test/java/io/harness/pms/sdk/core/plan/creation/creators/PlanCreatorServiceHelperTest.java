/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.creators;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.plan.creation.beans.MergePlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.fabric8.utils.Lists;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
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
        PlanCreatorServiceHelper.findPlanCreator(planCreators, yamlField);
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
}
