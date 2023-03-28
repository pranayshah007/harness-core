/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helper.generator.artifacts;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.PollingConfig;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.buildtriggers.helper.generator.PollingItemGeneratorTestHelper;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.BambooPollingItemGenerator;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.polling.contracts.PollingItem;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
@OwnedBy(PIPELINE)
public class BambooPollingItemGeneratorTest extends CategoryTest {
  BuildTriggerHelper buildTriggerHelper = new BuildTriggerHelper(null);
  @InjectMocks NGTriggerElementMapper ngTriggerElementMapper;
  BambooPollingItemGenerator bambooPollingItemGenerator = new BambooPollingItemGenerator(buildTriggerHelper);
  String bamboo_pipeline_artifact_snippet_runtime_all;
  String bamboo_pipeline_artifact_snippet_runtime_tagonly;
  String ngTriggerYaml_artifact_bamboo;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ClassLoader classLoader = getClass().getClassLoader();
    bamboo_pipeline_artifact_snippet_runtime_all = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("bamboo_pipeline_artifact_snippet_runtime_all.yaml")),
        StandardCharsets.UTF_8);
    bamboo_pipeline_artifact_snippet_runtime_tagonly = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("bamboo_pipeline_artifact_snippet_runtime_tagonly.yaml")),
        StandardCharsets.UTF_8);

    ngTriggerYaml_artifact_bamboo = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ngTriggerYaml_artifact_bamboo.yaml")), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testBambooPollingItemGeneration_pipelineContainsFixedValuesExceptTest() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ngTriggerYaml_artifact_bamboo, false);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
        triggerDetails, bamboo_pipeline_artifact_snippet_runtime_tagonly);
    PollingItem pollingItem = bambooPollingItemGenerator.generatePollingItem(buildTriggerOpsData);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.ARTIFACT);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("conn");
    assertThat(pollingItem.getPollingPayloadData().getBambooPayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getBambooPayload().getPlanKey()).isEqualTo("test");

    // As All data is already prepared, Testing buildTriggerHelper.validateBuildType
    PollingItemGeneratorTestHelper.validateBuildType(buildTriggerOpsData, buildTriggerHelper);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testBambooPollingItemGeneration_pipelineContainsAllRuntimeInputsTest() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ngTriggerYaml_artifact_bamboo, false);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
        triggerDetails, bamboo_pipeline_artifact_snippet_runtime_all);
    PollingItem pollingItem = bambooPollingItemGenerator.generatePollingItem(buildTriggerOpsData);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.ARTIFACT);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("account.conn");
    assertThat(pollingItem.getPollingPayloadData().getDockerHubPayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getBambooPayload().getPlanKey()).isEqualTo("test1");

    // As All data is already prepared, Testing buildTriggerHelper.validateBuildType
    PollingItemGeneratorTestHelper.validateBuildType(buildTriggerOpsData, buildTriggerHelper);
  }
}
