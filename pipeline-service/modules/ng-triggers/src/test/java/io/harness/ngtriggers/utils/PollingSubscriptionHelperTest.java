/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookInfo;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.generator.DockerRegistryPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GeneratorFactory;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GitPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.HttpHelmPollingItemGenerator;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.polling.contracts.HelmVersion;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.Type;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.webhook.WebhookConfigProvider;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PollingSubscriptionHelperTest extends CategoryTest {
  @Mock private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Mock private WebhookConfigProvider webhookConfigProvider;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @InjectMocks @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Mock private PipelineServiceClient pipelineServiceClient;
  private DockerRegistryPollingItemGenerator dockerRegistryPollingItemGenerator;
  private HttpHelmPollingItemGenerator httpHelmPollingItemGenerator;
  private GitPollingItemGenerator gitPollingItemGenerator;
  @Mock private GeneratorFactory generatorFactory;
  private BuildTriggerHelper buildTriggerHelper;
  private PollingSubscriptionHelper pollingSubscriptionHelper;

  private String ngTriggerYaml_artifact_dockerregistry;
  private String ngTriggerYaml_manifest;
  private String ngTriggerYaml_gitpolling;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    ngTriggerYaml_artifact_dockerregistry = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-dockerregistry-v2.yaml")),
        StandardCharsets.UTF_8);
    ngTriggerYaml_manifest =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-manifest-helm-http-v2.yaml")),
            StandardCharsets.UTF_8);
    ngTriggerYaml_gitpolling = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-gitpolling-v2.yaml")), StandardCharsets.UTF_8);
    buildTriggerHelper = spy(new BuildTriggerHelper(pipelineServiceClient));
    pollingSubscriptionHelper =
        new PollingSubscriptionHelper(buildTriggerHelper, ngTriggerElementMapper, generatorFactory);
    dockerRegistryPollingItemGenerator = new DockerRegistryPollingItemGenerator(buildTriggerHelper);
    httpHelmPollingItemGenerator = new HttpHelmPollingItemGenerator(buildTriggerHelper);
    gitPollingItemGenerator = new GitPollingItemGenerator(buildTriggerHelper);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGeneratePollingItemForArtifactTrigger() throws Exception {
    NGTriggerEntity ngTriggerEntity = ngTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "first_trigger", ngTriggerYaml_artifact_dockerregistry, true);
    doReturn(Optional.of("")).when(buildTriggerHelper).fetchResolvedTemplatesPipelineForTrigger(any());
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(dockerRegistryPollingItemGenerator);
    PollingItem pollingItem = pollingSubscriptionHelper.generatePollingItem(ngTriggerEntity);
    assertThat(pollingItem.getCategory()).isEqualTo(io.harness.polling.contracts.Category.ARTIFACT);
    assertThat(pollingItem.getQualifier().getAccountId()).isEqualTo(ngTriggerEntity.getAccountId());
    assertThat(pollingItem.getQualifier().getOrganizationId()).isEqualTo(ngTriggerEntity.getOrgIdentifier());
    assertThat(pollingItem.getQualifier().getProjectId()).isEqualTo(ngTriggerEntity.getProjectIdentifier());
    assertThat(pollingItem.getSignature())
        .isEqualTo(ngTriggerEntity.getMetadata().getBuildMetadata().getPollingConfig().getSignature());
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("account.conn");
    assertThat(pollingItem.getPollingPayloadData().getType()).isEqualTo(Type.DOCKER_HUB);
    assertThat(pollingItem.getPollingPayloadData().getDockerHubPayload().getImagePath()).isEqualTo("test1");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGeneratePollingItemForManifestTrigger() throws Exception {
    NGTriggerEntity ngTriggerEntity =
        ngTriggerElementMapper.toTriggerEntity("account", "org", "proj", "first_trigger", ngTriggerYaml_manifest, true);
    doReturn(Optional.of("")).when(buildTriggerHelper).fetchResolvedTemplatesPipelineForTrigger(any());
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(httpHelmPollingItemGenerator);
    PollingItem pollingItem = pollingSubscriptionHelper.generatePollingItem(ngTriggerEntity);
    assertThat(pollingItem.getCategory()).isEqualTo(io.harness.polling.contracts.Category.MANIFEST);
    assertThat(pollingItem.getQualifier().getAccountId()).isEqualTo(ngTriggerEntity.getAccountId());
    assertThat(pollingItem.getQualifier().getOrganizationId()).isEqualTo(ngTriggerEntity.getOrgIdentifier());
    assertThat(pollingItem.getQualifier().getProjectId()).isEqualTo(ngTriggerEntity.getProjectIdentifier());
    assertThat(pollingItem.getSignature())
        .isEqualTo(ngTriggerEntity.getMetadata().getBuildMetadata().getPollingConfig().getSignature());
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("account.conn");
    assertThat(pollingItem.getPollingPayloadData().getType()).isEqualTo(Type.HTTP_HELM);
    assertThat(pollingItem.getPollingPayloadData().getHttpHelmPayload().getChartName()).isEqualTo("chart1");
    assertThat(pollingItem.getPollingPayloadData().getHttpHelmPayload().getHelmVersion())
        .isEqualTo(HelmVersion.valueOf("V3"));
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGeneratePollingItemForGithubTrigger() throws Exception {
    when(pmsFeatureFlagService.isEnabled("account", FeatureName.CD_GIT_WEBHOOK_POLLING)).thenReturn(true);
    NGTriggerEntity ngTriggerEntity = ngTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "first_trigger", ngTriggerYaml_gitpolling, true);
    ngTriggerEntity.setTriggerStatus(
        TriggerStatus.builder().webhookInfo(WebhookInfo.builder().webhookId("123").build()).build());
    System.out.println("hello");
    System.out.println(ngTriggerEntity.getPollInterval());
    doReturn(Optional.of("")).when(buildTriggerHelper).fetchResolvedTemplatesPipelineForTrigger(any());
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(gitPollingItemGenerator);
    PollingItem pollingItem = pollingSubscriptionHelper.generatePollingItem(ngTriggerEntity);
    assertThat(pollingItem.getCategory()).isEqualTo(io.harness.polling.contracts.Category.GITPOLLING);
    assertThat(pollingItem.getQualifier().getAccountId()).isEqualTo(ngTriggerEntity.getAccountId());
    assertThat(pollingItem.getQualifier().getOrganizationId()).isEqualTo(ngTriggerEntity.getOrgIdentifier());
    assertThat(pollingItem.getQualifier().getProjectId()).isEqualTo(ngTriggerEntity.getProjectIdentifier());
    assertThat(pollingItem.getSignature())
        .isEqualTo(ngTriggerEntity.getMetadata().getBuildMetadata().getPollingConfig().getSignature());
    assertThat(pollingItem.getPollingPayloadData().getConnectorRef()).isEqualTo("conn");
    assertThat(pollingItem.getPollingPayloadData().getType()).isEqualTo(Type.GIT_POLL);
    assertThat(pollingItem.getPollingPayloadData().getGitPollPayload().getPollInterval()).isEqualTo(2);
    assertThat(pollingItem.getPollingPayloadData().getGitPollPayload().getWebhookId()).isEqualTo("123");
  }
}
