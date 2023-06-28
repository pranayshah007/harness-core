/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helper.generator.artifacts;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.PollingConfig;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.buildtriggers.helper.generator.PollingItemGeneratorTestHelper;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.AMIPollingItemGenerator;
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
public class AMIPollingItemGeneratorTest extends CategoryTest {
  BuildTriggerHelper buildTriggerHelper = new BuildTriggerHelper(null);
  @InjectMocks NGTriggerElementMapper ngTriggerElementMapper;
  AMIPollingItemGenerator amiPollingItemGenerator = new AMIPollingItemGenerator(buildTriggerHelper);
  String ami_pipeline_artifact_snippet_runtime_all;
  String ami_pipeline_artifact_snippet_runtime_tagonly;
  String ngTriggerYaml_artifact_ami;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ClassLoader classLoader = getClass().getClassLoader();
    ami_pipeline_artifact_snippet_runtime_all = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ami_pipeline_artifact_snippet_runtime_all.yaml")),
        StandardCharsets.UTF_8);
    ami_pipeline_artifact_snippet_runtime_tagonly = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ami_pipeline_artifact_snippet_runtime_tagonly.yaml")),
        StandardCharsets.UTF_8);

    ngTriggerYaml_artifact_ami = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-ami.yaml")), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testAMIPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ngTriggerYaml_artifact_ami, false);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
        triggerDetails, ami_pipeline_artifact_snippet_runtime_tagonly);
    PollingItem pollingItem = amiPollingItemGenerator.generatePollingItem(buildTriggerOpsData);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.ARTIFACT);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("AWSSri");
    assertThat(pollingItem.getPollingPayloadData().getAmiPayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getAmiPayload().getRegion()).isEqualTo("us-east-1");

    /* TODO: Fix after verifying https://harness.atlassian.net/browse/CDS-70545
      assertThat(pollingItem.getPollingPayloadData().getAmiPayload().getFiltersCount()).isEqualTo(1);
      assertThat(pollingItem.getPollingPayloadData().getAmiPayload().getTagsCount()).isEqualTo(1);
    }

    @Test
    @Owner(developers = SRIDHAR)
    @Category(UnitTests.class)
    public void testAMIPollingItemGeneration_pipelineContainsAllRuntimeInputs() throws Exception {
      TriggerDetails triggerDetails =
          ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ngTriggerYaml_artifact_ami, false);
      triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
          PollingConfig.builder().signature("sig1").build());
      BuildTriggerOpsData buildTriggerOpsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(
          triggerDetails, ami_pipeline_artifact_snippet_runtime_all);
      PollingItem pollingItem = amiPollingItemGenerator.generatePollingItem(buildTriggerOpsData);

      PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.ARTIFACT);

      assertThat(pollingItem.getPollingPayloadData()).isNotNull();
      assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("conn");
      assertThat(pollingItem.getPollingPayloadData().getAmiPayload()).isNotNull();
      assertThat(pollingItem.getPollingPayloadData().getAmiPayload().getRegion()).isEqualTo("us-east-1");

      /* TODO: Fix after verifying https://harness.atlassian.net/browse/CDS-70545
      assertThat(pollingItem.getPollingPayloadData().getAmiPayload().getFilters(0)).isEqualTo("22");
      assertThat(pollingItem.getPollingPayloadData().getAmiPayload().getTags(0)).isEqualTo("123");
      */
  }
}
