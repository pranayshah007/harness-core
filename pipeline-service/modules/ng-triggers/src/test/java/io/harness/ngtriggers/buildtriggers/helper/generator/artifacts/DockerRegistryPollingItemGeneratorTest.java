/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helper.generator.artifacts;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.PollingConfig;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.buildtriggers.helper.generator.PollingItemGeneratorTestHelper;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.DockerRegistryPollingItemGenerator;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.polling.contracts.PollingItem;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class DockerRegistryPollingItemGeneratorTest extends CategoryTest {
  BuildTriggerHelper buildTriggerHelper = new BuildTriggerHelper(null);
  @InjectMocks NGTriggerElementMapper ngTriggerElementMapper;
  DockerRegistryPollingItemGenerator dockerRegistryPollingItemGenerator =
      new DockerRegistryPollingItemGenerator(buildTriggerHelper);
  String dockerregistry_pipeline_artifact_snippet_runtime_all;
  String dockerregistry_pipeline_artifact_snippet_runtime_tagonly;
  String ngTriggerYaml_artifact_dockerregistry;
  String ngTriggerYaml_multi_region_artifact_dockerregistry;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ClassLoader classLoader = getClass().getClassLoader();
    dockerregistry_pipeline_artifact_snippet_runtime_all = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("dockerregistry_pipeline_artifact_snippet_runtime_all.yaml")),
        StandardCharsets.UTF_8);
    dockerregistry_pipeline_artifact_snippet_runtime_tagonly = Resources.toString(
        Objects.requireNonNull(
            classLoader.getResource("dockerregistry_pipeline_artifact_snippet_runtime_tagonly.yaml")),
        StandardCharsets.UTF_8);

    ngTriggerYaml_artifact_dockerregistry =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-dockerregistry.yaml")),
            StandardCharsets.UTF_8);
    ngTriggerYaml_multi_region_artifact_dockerregistry =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-multi-region-artifact.yaml")),
            StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEcrPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ngTriggerYaml_artifact_dockerregistry, false);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
        triggerDetails, dockerregistry_pipeline_artifact_snippet_runtime_tagonly);
    PollingItem pollingItem = dockerRegistryPollingItemGenerator.generatePollingItem(buildTriggerOpsData);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.ARTIFACT);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("conn");
    assertThat(pollingItem.getPollingPayloadData().getDockerHubPayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getDockerHubPayload().getImagePath()).isEqualTo("test");

    // As All data is already prepared, Testing buildTriggerHelper.validateBuildType
    PollingItemGeneratorTestHelper.validateBuildType(buildTriggerOpsData, buildTriggerHelper);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEcrPollingItemGeneration_pipelineContainsAllRuntimeInputs() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ngTriggerYaml_artifact_dockerregistry, false);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
        triggerDetails, dockerregistry_pipeline_artifact_snippet_runtime_all);
    PollingItem pollingItem = dockerRegistryPollingItemGenerator.generatePollingItem(buildTriggerOpsData);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.ARTIFACT);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("account.conn");
    assertThat(pollingItem.getPollingPayloadData().getDockerHubPayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getDockerHubPayload().getImagePath()).isEqualTo("test1");

    // As All data is already prepared, Testing buildTriggerHelper.validateBuildType
    PollingItemGeneratorTestHelper.validateBuildType(buildTriggerOpsData, buildTriggerHelper);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testPollingItemGenerationForMultiRegionArtifactTrigger() throws Exception {
    TriggerDetails triggerDetails = ngTriggerElementMapper.toTriggerDetails(
        "acc", "org", "proj", ngTriggerYaml_multi_region_artifact_dockerregistry, true);
    triggerDetails.getNgTriggerEntity().getMetadata().getMultiBuildMetadata().get(0).setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    triggerDetails.getNgTriggerEntity().getMetadata().getMultiBuildMetadata().get(1).setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    List<BuildTriggerOpsData> buildTriggerOpsDataList =
        buildTriggerHelper.generateBuildTriggerOpsDataForMultiArtifact(triggerDetails);

    PollingItem pollingItem1 = dockerRegistryPollingItemGenerator.generatePollingItem(buildTriggerOpsDataList.get(0));
    PollingItemGeneratorTestHelper.baseAssert(pollingItem1, io.harness.polling.contracts.Category.ARTIFACT);
    assertThat(pollingItem1.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem1.getPollingPayloadData().getConnectorRef()).isEqualTo("DockerConnectorUs");
    assertThat(pollingItem1.getPollingPayloadData().getDockerHubPayload()).isNotNull();
    assertThat(pollingItem1.getPollingPayloadData().getDockerHubPayload().getImagePath())
        .isEqualTo("v2/hello-world-us");

    PollingItem pollingItem2 = dockerRegistryPollingItemGenerator.generatePollingItem(buildTriggerOpsDataList.get(1));
    PollingItemGeneratorTestHelper.baseAssert(pollingItem2, io.harness.polling.contracts.Category.ARTIFACT);
    assertThat(pollingItem2.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem2.getPollingPayloadData().getConnectorRef()).isEqualTo("DockerConnectorApac");
    assertThat(pollingItem2.getPollingPayloadData().getDockerHubPayload()).isNotNull();
    assertThat(pollingItem2.getPollingPayloadData().getDockerHubPayload().getImagePath())
        .isEqualTo("v2/hello-world-apac");
  }
}
