/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helper.generator;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SRIDHAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.dto.PollingConfig;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookInfo;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GitPollingItemGenerator;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.polling.contracts.PollingItem;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class GitPollingItemGeneratorTest extends CategoryTest {
  BuildTriggerHelper buildTriggerHelper = new BuildTriggerHelper(null);
  @InjectMocks NGTriggerElementMapper ngTriggerElementMapper;
  GitPollingItemGenerator gitPollingItemGeneratorTest = new GitPollingItemGenerator(buildTriggerHelper);
  String ngTriggerYaml_artifact_github;

  @Mock PmsFeatureFlagService pmsFeatureFlagService;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);

    ClassLoader classLoader = getClass().getClassLoader();

    ngTriggerYaml_artifact_github = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-gitpolling.yaml")), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGithubPollingItemGeneration_pipelineContainsFixedValuesExceptTag() throws Exception {
    when(pmsFeatureFlagService.isEnabled(eq("acc"), eq(FeatureName.CD_GIT_WEBHOOK_POLLING))).thenReturn(true);
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("acc", "org", "proj", ngTriggerYaml_artifact_github, false);
    triggerDetails.getNgTriggerEntity().getMetadata().getBuildMetadata().setPollingConfig(
        PollingConfig.builder().signature("sig1").build());
    triggerDetails.getNgTriggerEntity().setTriggerStatus(TriggerStatus.builder().build());
    triggerDetails.getNgTriggerEntity().getTriggerStatus().setWebhookInfo(WebhookInfo.builder().build());
    triggerDetails.getNgTriggerEntity().getTriggerStatus().getWebhookInfo().setWebhookId("webhook_id");
    BuildTriggerOpsData buildTriggerOpsData =
        buildTriggerHelper.generateBuildTriggerOpsDataForGitPolling(triggerDetails);
    PollingItem pollingItem = gitPollingItemGeneratorTest.generatePollingItem(buildTriggerOpsData);

    PollingItemGeneratorTestHelper.baseAssert(pollingItem, io.harness.polling.contracts.Category.GITPOLLING);

    assertThat(pollingItem.getPollingPayloadData()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("GithubRepo");
    assertThat(pollingItem.getPollingPayloadData().getGitPollPayload()).isNotNull();
    assertThat(pollingItem.getPollingPayloadData().getGitPollPayload().getPollInterval()).isEqualTo(2);
    assertThat(pollingItem.getPollingPayloadData().getGitPollPayload().getWebhookId()).isEqualTo("webhook_id");
  }
}
