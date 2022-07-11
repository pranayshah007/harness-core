/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helper.generator.artifacts;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADWAIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.PollingConfig;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.buildtriggers.helper.generator.PollingItemGeneratorTestHelper;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.EcrPollingItemGenerator;
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
public class EcrPollingItemGeneratorTest extends CategoryTest {
  BuildTriggerHelper buildTriggerHelper = new BuildTriggerHelper(null);
  @InjectMocks NGTriggerElementMapper ngTriggerElementMapper;
  EcrPollingItemGenerator ecrPollingItemGenerator = new EcrPollingItemGenerator(buildTriggerHelper);
  String ecr_pipeline_artifact_snippet_runtime_all;
  String ecr_pipeline_artifact_snippet_runtime_tagonly;
  String ngTriggerYaml_artifact_ecr;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ClassLoader classLoader = getClass().getClassLoader();
    ecr_pipeline_artifact_snippet_runtime_all = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ecr_pipeline_artifact_snippet_runtime_all.yaml")),
        StandardCharsets.UTF_8);
    ecr_pipeline_artifact_snippet_runtime_tagonly = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ecr_pipeline_artifact_snippet_runtime_tagonly.yaml")),
        StandardCharsets.UTF_8);

    ngTriggerYaml_artifact_ecr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-ecr.yaml")), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEcrPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ngTriggerYaml_artifact_ecr);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
        triggerDetails, ecr_pipeline_artifact_snippet_runtime_tagonly);
    PollingItem pollingItem = ecrPollingItemGenerator.generatePollingItem(buildTriggerOpsData);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.ARTIFACT);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("conn");
    assertThat(pollingItem.getPollingPayloadData().getEcrPayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getEcrPayload().getImagePath()).isEqualTo("test");
    assertThat(pollingItem.getPollingPayloadData().getEcrPayload().getRegion()).isEqualTo("us-west-1");

    // As All data is already prepared, Testing buildTriggerHelper.validateBuildType
    PollingItemGeneratorTestHelper.validateBuildType(buildTriggerOpsData, buildTriggerHelper);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEcrPollingItemGeneration_pipelineContainsAllRuntimeInputs() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ngTriggerYaml_artifact_ecr);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
        triggerDetails, ecr_pipeline_artifact_snippet_runtime_all);
    PollingItem pollingItem = ecrPollingItemGenerator.generatePollingItem(buildTriggerOpsData);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.ARTIFACT);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("account.conn");
    assertThat(pollingItem.getPollingPayloadData().getGcrPayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getEcrPayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getEcrPayload().getImagePath()).isEqualTo("test1");
    assertThat(pollingItem.getPollingPayloadData().getEcrPayload().getRegion()).isEqualTo("us-east-1");

    // As All data is already prepared, Testing buildTriggerHelper.validateBuildType
    PollingItemGeneratorTestHelper.validateBuildType(buildTriggerOpsData, buildTriggerHelper);
  }
}
