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
import java.util.List;
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
  private String ngTriggerYaml_multi_region_artifact;

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
    ngTriggerYaml_multi_region_artifact =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-multi-region-artifact.yaml")),
            StandardCharsets.UTF_8);
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
    PollingItem pollingItem = pollingSubscriptionHelper.generatePollingItems(ngTriggerEntity).get(0);
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
    PollingItem pollingItem = pollingSubscriptionHelper.generatePollingItems(ngTriggerEntity).get(0);
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
    doReturn(Optional.of("")).when(buildTriggerHelper).fetchResolvedTemplatesPipelineForTrigger(any());
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(gitPollingItemGenerator);
    PollingItem pollingItem = pollingSubscriptionHelper.generatePollingItems(ngTriggerEntity).get(0);
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

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGeneratePollingItemForMultiRegionArtifactTrigger() throws Exception {
    when(pmsFeatureFlagService.isEnabled("account", FeatureName.CDS_NG_TRIGGER_MULTI_ARTIFACTS)).thenReturn(true);
    NGTriggerEntity ngTriggerEntity = ngTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "multiRegionArtifactTrigger", ngTriggerYaml_multi_region_artifact, true);
    doReturn(Optional.of("")).when(buildTriggerHelper).fetchResolvedTemplatesPipelineForTrigger(any());
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(dockerRegistryPollingItemGenerator);

    PollingItem pollingItem1 = pollingSubscriptionHelper.generatePollingItems(ngTriggerEntity).get(0);
    assertThat(pollingItem1.getCategory()).isEqualTo(io.harness.polling.contracts.Category.ARTIFACT);
    assertThat(pollingItem1.getQualifier().getAccountId()).isEqualTo(ngTriggerEntity.getAccountId());
    assertThat(pollingItem1.getQualifier().getOrganizationId()).isEqualTo(ngTriggerEntity.getOrgIdentifier());
    assertThat(pollingItem1.getQualifier().getProjectId()).isEqualTo(ngTriggerEntity.getProjectIdentifier());
    assertThat(pollingItem1.getSignature())
        .isEqualTo(ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(0).getPollingConfig().getSignature());
    assertThat(pollingItem1.getPollingPayloadData().getConnectorRef()).isEqualTo("DockerConnectorUs");
    assertThat(pollingItem1.getPollingPayloadData().getType()).isEqualTo(Type.DOCKER_HUB);
    assertThat(pollingItem1.getPollingPayloadData().getDockerHubPayload().getImagePath())
        .isEqualTo("v2/hello-world-us");

    PollingItem pollingItem2 = pollingSubscriptionHelper.generatePollingItems(ngTriggerEntity).get(1);
    assertThat(pollingItem2.getCategory()).isEqualTo(io.harness.polling.contracts.Category.ARTIFACT);
    assertThat(pollingItem2.getQualifier().getAccountId()).isEqualTo(ngTriggerEntity.getAccountId());
    assertThat(pollingItem2.getQualifier().getOrganizationId()).isEqualTo(ngTriggerEntity.getOrgIdentifier());
    assertThat(pollingItem2.getQualifier().getProjectId()).isEqualTo(ngTriggerEntity.getProjectIdentifier());
    assertThat(pollingItem2.getSignature())
        .isEqualTo(ngTriggerEntity.getMetadata().getMultiBuildMetadata().get(1).getPollingConfig().getSignature());
    assertThat(pollingItem2.getPollingPayloadData().getConnectorRef()).isEqualTo("DockerConnectorApac");
    assertThat(pollingItem2.getPollingPayloadData().getType()).isEqualTo(Type.DOCKER_HUB);
    assertThat(pollingItem2.getPollingPayloadData().getDockerHubPayload().getImagePath())
        .isEqualTo("v2/hello-world-apac");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGenerateMultiArtifactPollingItemsToUnsubscribe() throws Exception {
    when(pmsFeatureFlagService.isEnabled("account", FeatureName.CDS_NG_TRIGGER_MULTI_ARTIFACTS)).thenReturn(true);
    NGTriggerEntity ngTriggerEntity = ngTriggerElementMapper.toTriggerEntity(
        "account", "org", "proj", "multiRegionArtifactTrigger", ngTriggerYaml_multi_region_artifact, true);
    List<String> signatures = List.of("sig1", "sig2");
    ngTriggerEntity.getMetadata().setSignatures(signatures);
    List<PollingItem> pollingItemsToUnsubscribe =
        pollingSubscriptionHelper.generateMultiArtifactPollingItemsToUnsubscribe(ngTriggerEntity);
    assertThat(pollingItemsToUnsubscribe.size()).isEqualTo(2);

    assertThat(pollingItemsToUnsubscribe.get(0).getQualifier().getAccountId()).isEqualTo("account");
    assertThat(pollingItemsToUnsubscribe.get(0).getQualifier().getOrganizationId()).isEqualTo("org");
    assertThat(pollingItemsToUnsubscribe.get(0).getQualifier().getProjectId()).isEqualTo("proj");
    assertThat(pollingItemsToUnsubscribe.get(0).getCategory())
        .isEqualTo(io.harness.polling.contracts.Category.ARTIFACT);
    assertThat(pollingItemsToUnsubscribe.get(0).getSignature()).isEqualTo("sig1");

    assertThat(pollingItemsToUnsubscribe.get(1).getQualifier().getAccountId()).isEqualTo("account");
    assertThat(pollingItemsToUnsubscribe.get(1).getQualifier().getOrganizationId()).isEqualTo("org");
    assertThat(pollingItemsToUnsubscribe.get(1).getQualifier().getProjectId()).isEqualTo("proj");
    assertThat(pollingItemsToUnsubscribe.get(1).getCategory())
        .isEqualTo(io.harness.polling.contracts.Category.ARTIFACT);
    assertThat(pollingItemsToUnsubscribe.get(1).getSignature()).isEqualTo("sig2");
  }
}
