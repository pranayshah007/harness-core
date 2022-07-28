/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helper.generator.manifests;

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
import io.harness.ngtriggers.buildtriggers.helpers.generator.S3HelmPollingItemGenerator;
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
public class HelmS3PollingItemGeneratorTest extends CategoryTest {
  BuildTriggerHelper buildTriggerHelper = new BuildTriggerHelper(null);
  @InjectMocks NGTriggerElementMapper ngTriggerElementMapper;
  S3HelmPollingItemGenerator s3HelmPollingItemGenerator = new S3HelmPollingItemGenerator(buildTriggerHelper);
  String helm_s3_pipeline_manifest_snippet_runtime_all;
  String helm_s3_pipeline_manifest_snippet_runtime_tagonly;
  String ng_trigger_manifest_helm_s3;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ClassLoader classLoader = getClass().getClassLoader();
    helm_s3_pipeline_manifest_snippet_runtime_all = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("helm_s3_pipeline_manifest_snippet_runtime_all.yaml")),
        StandardCharsets.UTF_8);
    helm_s3_pipeline_manifest_snippet_runtime_tagonly = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("helm_s3_pipeline_manifest_snippet_runtime_tagonly.yaml")),
        StandardCharsets.UTF_8);

    ng_trigger_manifest_helm_s3 = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-manifest-helm-s3.yaml")), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testHelmS3PollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ng_trigger_manifest_helm_s3);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForManifest(
        triggerDetails, helm_s3_pipeline_manifest_snippet_runtime_tagonly);
    PollingItem pollingItem = s3HelmPollingItemGenerator.generatePollingItem(buildTriggerOpsData);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.MANIFEST);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("conn");
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload().getBucketName()).isEqualTo("bucket");
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload().getChartName()).isEqualTo("chart");
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload().getRegion()).isEqualTo("us-east-1");
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload().getFolderPath()).isEqualTo("path");
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload().getHelmVersion().name()).isEqualTo("V2");

    // As All data is already prepared, Testing buildTriggerHelper.validateBuildType
    PollingItemGeneratorTestHelper.validateBuildType(buildTriggerOpsData, buildTriggerHelper);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testHelmS3PollingItemGeneration_pipelineContainsAllRuntimeInputs() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ng_trigger_manifest_helm_s3);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForManifest(
        triggerDetails, helm_s3_pipeline_manifest_snippet_runtime_all);
    PollingItem pollingItem = s3HelmPollingItemGenerator.generatePollingItem(buildTriggerOpsData);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.MANIFEST);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("account.conn");
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload().getBucketName()).isEqualTo("bucket1");
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload().getChartName()).isEqualTo("chart1");
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload().getRegion()).isEqualTo("us-west-1");
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload().getFolderPath()).isEqualTo("path1");
    assertThat(pollingItem.getPollingPayloadData().getS3HelmPayload().getHelmVersion().name()).isEqualTo("V3");

    // As All data is already prepared, Testing buildTriggerHelper.validateBuildType
    PollingItemGeneratorTestHelper.validateBuildType(buildTriggerOpsData, buildTriggerHelper);
  }
}
