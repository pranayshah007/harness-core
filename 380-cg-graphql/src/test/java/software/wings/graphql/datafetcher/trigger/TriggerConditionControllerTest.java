/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.trigger;

import static io.harness.beans.FeatureName.WEBHOOK_TRIGGER_AUTHORIZATION;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.MILAN;
import static io.harness.rule.OwnerRule.MILOS;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.ROHITKARELIA;

import static software.wings.utils.WingsTestConstants.MANIFEST_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployMode;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.app.MainConfiguration;
import software.wings.app.PortalConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.WebHookToken;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.trigger.ArtifactTriggerCondition;
import software.wings.beans.trigger.GithubAction;
import software.wings.beans.trigger.ManifestTriggerCondition;
import software.wings.beans.trigger.PipelineTriggerCondition;
import software.wings.beans.trigger.ReleaseAction;
import software.wings.beans.trigger.ScheduledTriggerCondition;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.TriggerConditionType;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.beans.trigger.WebhookEventType;
import software.wings.beans.trigger.WebhookSource;
import software.wings.beans.trigger.WebhookSource.BitBucketEventType;
import software.wings.graphql.schema.type.trigger.QLArtifactConditionInput;
import software.wings.graphql.schema.type.trigger.QLBitbucketEvent;
import software.wings.graphql.schema.type.trigger.QLConditionType;
import software.wings.graphql.schema.type.trigger.QLCreateOrUpdateTriggerInput;
import software.wings.graphql.schema.type.trigger.QLCreateOrUpdateTriggerInput.QLCreateOrUpdateTriggerInputBuilder;
import software.wings.graphql.schema.type.trigger.QLGitHubAction;
import software.wings.graphql.schema.type.trigger.QLGitHubEvent;
import software.wings.graphql.schema.type.trigger.QLGitHubEventType;
import software.wings.graphql.schema.type.trigger.QLGitlabEvent;
import software.wings.graphql.schema.type.trigger.QLManifestConditionInput;
import software.wings.graphql.schema.type.trigger.QLOnNewArtifact;
import software.wings.graphql.schema.type.trigger.QLOnNewManifest;
import software.wings.graphql.schema.type.trigger.QLOnPipelineCompletion;
import software.wings.graphql.schema.type.trigger.QLOnSchedule;
import software.wings.graphql.schema.type.trigger.QLOnWebhook;
import software.wings.graphql.schema.type.trigger.QLPipelineConditionInput;
import software.wings.graphql.schema.type.trigger.QLScheduleConditionInput;
import software.wings.graphql.schema.type.trigger.QLTriggerConditionInput;
import software.wings.graphql.schema.type.trigger.QLWebhookConditionInput;
import software.wings.graphql.schema.type.trigger.QLWebhookSource;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class TriggerConditionControllerTest extends CategoryTest {
  public static final String APP_ID = "appId";
  private static final String BRANCH_REGEX = "regex";
  @Mock MainConfiguration mainConfiguration;
  @Mock SettingsService settingsService;
  @Mock ArtifactStreamService artifactStreamService;
  @Mock TriggerActionController triggerActionController;
  @Mock FeatureFlagService featureFlagService;
  @Mock ApplicationManifestService applicationManifestService;

  @InjectMocks TriggerConditionController triggerConditionController = Mockito.spy(new TriggerConditionController());

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnArtifactTriggerCondition() {
    ArtifactTriggerCondition artifactTriggerCondition = ArtifactTriggerCondition.builder()
                                                            .artifactStreamId("sourceId")
                                                            .artifactSourceName("sourceName")
                                                            .artifactFilter("artifactFilter")
                                                            .regex(true)
                                                            .build();
    artifactTriggerCondition.setConditionType(TriggerConditionType.NEW_ARTIFACT);
    Trigger trigger = Trigger.builder().condition(artifactTriggerCondition).build();

    QLOnNewArtifact qlOnNewArtifact =
        (QLOnNewArtifact) triggerConditionController.populateTriggerCondition(trigger, null);

    assertThat(qlOnNewArtifact).isNotNull();
    assertThat(qlOnNewArtifact.getArtifactFilter()).isEqualTo(artifactTriggerCondition.getArtifactFilter());
    assertThat(qlOnNewArtifact.getArtifactSourceId()).isEqualTo(artifactTriggerCondition.getArtifactStreamId());
    assertThat(qlOnNewArtifact.getArtifactSourceName()).isEqualTo(artifactTriggerCondition.getArtifactSourceName());
    assertThat(qlOnNewArtifact.getRegex()).isEqualTo(artifactTriggerCondition.isRegex());
    assertThat(qlOnNewArtifact.getTriggerConditionType().name())
        .isEqualTo(artifactTriggerCondition.getConditionType().name());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnPipelineTriggerCondition() {
    PipelineTriggerCondition pipelineTrigerCondition =
        PipelineTriggerCondition.builder().pipelineId("pipelineId").pipelineName("pipelineName").build();
    pipelineTrigerCondition.setConditionType(TriggerConditionType.PIPELINE_COMPLETION);
    Trigger trigger = Trigger.builder().condition(pipelineTrigerCondition).build();

    QLOnPipelineCompletion qlOnPipelineCompletion =
        (QLOnPipelineCompletion) triggerConditionController.populateTriggerCondition(trigger, null);

    assertThat(qlOnPipelineCompletion).isNotNull();
    assertThat(qlOnPipelineCompletion.getPipelineId()).isEqualTo(pipelineTrigerCondition.getPipelineId());
    assertThat(qlOnPipelineCompletion.getPipelineName()).isEqualTo(pipelineTrigerCondition.getPipelineName());
    assertThat(qlOnPipelineCompletion.getTriggerConditionType().name())
        .isEqualTo(pipelineTrigerCondition.getConditionType().name());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnScheduledTriggerCondition() {
    ScheduledTriggerCondition scheduledTriggerCondition = ScheduledTriggerCondition.builder()
                                                              .cronExpression("cronExpression")
                                                              .cronDescription("cronDescription")
                                                              .onNewArtifactOnly(true)
                                                              .build();
    scheduledTriggerCondition.setConditionType(TriggerConditionType.SCHEDULED);
    Trigger trigger = Trigger.builder().condition(scheduledTriggerCondition).build();

    QLOnSchedule qlOnSchedule = (QLOnSchedule) triggerConditionController.populateTriggerCondition(trigger, null);

    assertThat(qlOnSchedule).isNotNull();
    assertThat(qlOnSchedule.getCronDescription()).isEqualTo(scheduledTriggerCondition.getCronDescription());
    assertThat(qlOnSchedule.getCronExpression()).isEqualTo(scheduledTriggerCondition.getCronExpression());
    assertThat(qlOnSchedule.getOnNewArtifactOnly()).isEqualTo(scheduledTriggerCondition.isOnNewArtifactOnly());
    assertThat(qlOnSchedule.getTriggerConditionType().name())
        .isEqualTo(scheduledTriggerCondition.getConditionType().name());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void
  populateTriggerConditionShouldReturnWebhookTriggerConditionWithGithubWebhookSourceWithPullRequestEventAndAction() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/gateway/api/webhooks/")
        .append(webHookToken.getWebHookToken())
        .append("?accountId=")
        .append(accountId);

    WebHookTriggerCondition webhookTriggerCondition = WebHookTriggerCondition.builder()
                                                          .webHookToken(webHookToken)
                                                          .webhookSource(WebhookSource.GITHUB)
                                                          .eventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST))
                                                          .actions(Arrays.asList(GithubAction.OPENED))
                                                          .branchRegex("regex")
                                                          .branchName("branchName")
                                                          .gitConnectorId("gitConnectorId")
                                                          .filePaths(Arrays.asList("filePath1", "filePath2"))
                                                          .checkFileContentChanged(true)
                                                          .build();
    Trigger trigger = Trigger.builder().condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(webhookTriggerCondition.getWebhookSource().name());
    assertThat(qlOnWebhook.getBranchRegex()).isEqualTo(webhookTriggerCondition.getBranchRegex());
    assertThat(qlOnWebhook.getBranchName()).isEqualTo(webhookTriggerCondition.getBranchName());
    assertThat(qlOnWebhook.getGitConnectorId()).isEqualTo(webhookTriggerCondition.getGitConnectorId());
    assertThat(qlOnWebhook.getGitConnectorName()).isEqualTo(gitConfig.getName());
    assertThat(qlOnWebhook.getFilePaths()).isEqualTo(webhookTriggerCondition.getFilePaths());
    assertThat(qlOnWebhook.getDeployOnlyIfFilesChanged())
        .isEqualTo(webhookTriggerCondition.isCheckFileContentChanged());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader()).isEqualTo("content-type: application/json");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNotNull();
    assertThat(qlOnWebhook.getWebhookEvent().getEvent()).isEqualTo(WebhookEventType.PULL_REQUEST.getValue());
    assertThat(qlOnWebhook.getWebhookEvent().getAction()).isEqualTo(GithubAction.OPENED.getValue());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void
  populateTriggerConditionShouldReturnWebhookTriggerConditionWithGithubWebhookSourceWithPackageEventAndAction() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/gateway/api/webhooks/")
        .append(webHookToken.getWebHookToken())
        .append("?accountId=")
        .append(accountId);

    WebHookTriggerCondition webhookTriggerCondition = WebHookTriggerCondition.builder()
                                                          .webHookToken(webHookToken)
                                                          .webhookSource(WebhookSource.GITHUB)
                                                          .eventTypes(Arrays.asList(WebhookEventType.PACKAGE))
                                                          .actions(Arrays.asList(GithubAction.OPENED))
                                                          .branchRegex("regex")
                                                          .branchName("branchName")
                                                          .gitConnectorId("gitConnectorId")
                                                          .filePaths(Arrays.asList("filePath1", "filePath2"))
                                                          .checkFileContentChanged(true)
                                                          .build();
    Trigger trigger = Trigger.builder().condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(webhookTriggerCondition.getWebhookSource().name());
    assertThat(qlOnWebhook.getBranchRegex()).isEqualTo(webhookTriggerCondition.getBranchRegex());
    assertThat(qlOnWebhook.getBranchName()).isEqualTo(webhookTriggerCondition.getBranchName());
    assertThat(qlOnWebhook.getGitConnectorId()).isEqualTo(webhookTriggerCondition.getGitConnectorId());
    assertThat(qlOnWebhook.getGitConnectorName()).isEqualTo(gitConfig.getName());
    assertThat(qlOnWebhook.getFilePaths()).isEqualTo(webhookTriggerCondition.getFilePaths());
    assertThat(qlOnWebhook.getDeployOnlyIfFilesChanged())
        .isEqualTo(webhookTriggerCondition.isCheckFileContentChanged());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader()).isEqualTo("content-type: application/json");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNotNull();
    assertThat(qlOnWebhook.getWebhookEvent().getEvent()).isEqualTo(WebhookEventType.PACKAGE.getValue());
    assertThat(qlOnWebhook.getWebhookEvent().getAction()).isEqualTo(GithubAction.OPENED.getValue());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnWebhookTriggerConditionWithGithubWebhookSourceWithReleaseAction() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/gateway/api/webhooks/")
        .append(webHookToken.getWebHookToken())
        .append("?accountId=")
        .append(accountId);

    WebHookTriggerCondition webhookTriggerCondition = WebHookTriggerCondition.builder()
                                                          .webHookToken(webHookToken)
                                                          .webhookSource(WebhookSource.GITHUB)
                                                          .eventTypes(Arrays.asList(WebhookEventType.RELEASE))
                                                          .releaseActions(Arrays.asList(ReleaseAction.CREATED))
                                                          .branchRegex("regex")
                                                          .branchName("branchName")
                                                          .gitConnectorId("gitConnectorId")
                                                          .filePaths(Arrays.asList("filePath1", "filePath2"))
                                                          .checkFileContentChanged(true)
                                                          .build();
    Trigger trigger = Trigger.builder().condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(webhookTriggerCondition.getWebhookSource().name());
    assertThat(qlOnWebhook.getBranchRegex()).isEqualTo(webhookTriggerCondition.getBranchRegex());
    assertThat(qlOnWebhook.getBranchName()).isEqualTo(webhookTriggerCondition.getBranchName());
    assertThat(qlOnWebhook.getGitConnectorId()).isEqualTo(webhookTriggerCondition.getGitConnectorId());
    assertThat(qlOnWebhook.getGitConnectorName()).isEqualTo(gitConfig.getName());
    assertThat(qlOnWebhook.getFilePaths()).isEqualTo(webhookTriggerCondition.getFilePaths());
    assertThat(qlOnWebhook.getDeployOnlyIfFilesChanged())
        .isEqualTo(webhookTriggerCondition.isCheckFileContentChanged());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader()).isEqualTo("content-type: application/json");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNotNull();
    assertThat(qlOnWebhook.getWebhookEvent().getEvent()).isEqualTo(WebhookEventType.RELEASE.getValue());
    assertThat(qlOnWebhook.getWebhookEvent().getAction()).isEqualTo(ReleaseAction.CREATED.getValue());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnWebhookTriggerConditionWithGitlabWebhookSource() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/gateway/api/webhooks/")
        .append(webHookToken.getWebHookToken())
        .append("?accountId=")
        .append(accountId);

    WebHookTriggerCondition webhookTriggerCondition = WebHookTriggerCondition.builder()
                                                          .webHookToken(webHookToken)
                                                          .webhookSource(WebhookSource.GITLAB)
                                                          .eventTypes(Arrays.asList(WebhookEventType.PUSH))
                                                          .branchName("branchName")
                                                          .gitConnectorId("gitConnectorId")
                                                          .filePaths(Arrays.asList("filePath1", "filePath2"))
                                                          .checkFileContentChanged(true)
                                                          .build();
    Trigger trigger = Trigger.builder().condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(webhookTriggerCondition.getWebhookSource().name());
    assertThat(qlOnWebhook.getBranchRegex()).isEqualTo(webhookTriggerCondition.getBranchRegex());
    assertThat(qlOnWebhook.getBranchName()).isEqualTo(webhookTriggerCondition.getBranchName());
    assertThat(qlOnWebhook.getGitConnectorId()).isEqualTo(webhookTriggerCondition.getGitConnectorId());
    assertThat(qlOnWebhook.getGitConnectorName()).isEqualTo(gitConfig.getName());
    assertThat(qlOnWebhook.getFilePaths()).isEqualTo(webhookTriggerCondition.getFilePaths());
    assertThat(qlOnWebhook.getDeployOnlyIfFilesChanged())
        .isEqualTo(webhookTriggerCondition.isCheckFileContentChanged());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader()).isEqualTo("content-type: application/json");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNotNull();
    assertThat(qlOnWebhook.getWebhookEvent().getEvent()).isEqualTo(WebhookEventType.PUSH.getValue());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnWebhookTriggerConditionWithGitlabWebhookSourceWithEventType() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);
    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/gateway/api/webhooks/")
        .append(webHookToken.getWebHookToken())
        .append("?accountId=")
        .append(accountId);

    WebHookTriggerCondition webhookTriggerCondition = WebHookTriggerCondition.builder()
                                                          .webHookToken(webHookToken)
                                                          .webhookSource(WebhookSource.GITLAB)
                                                          .eventTypes(null)
                                                          .branchName("branchName")
                                                          .gitConnectorId("gitConnectorId")
                                                          .filePaths(Arrays.asList("filePath1", "filePath2"))
                                                          .checkFileContentChanged(true)
                                                          .build();
    Trigger trigger = Trigger.builder().condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(webhookTriggerCondition.getWebhookSource().name());
    assertThat(qlOnWebhook.getBranchRegex()).isEqualTo(webhookTriggerCondition.getBranchRegex());
    assertThat(qlOnWebhook.getBranchName()).isEqualTo(webhookTriggerCondition.getBranchName());
    assertThat(qlOnWebhook.getGitConnectorId()).isEqualTo(webhookTriggerCondition.getGitConnectorId());
    assertThat(qlOnWebhook.getGitConnectorName()).isEqualTo(gitConfig.getName());
    assertThat(qlOnWebhook.getFilePaths()).isEqualTo(webhookTriggerCondition.getFilePaths());
    assertThat(qlOnWebhook.getDeployOnlyIfFilesChanged())
        .isEqualTo(webhookTriggerCondition.isCheckFileContentChanged());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader()).isEqualTo("content-type: application/json");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNotNull();
    assertThat(qlOnWebhook.getWebhookEvent().getEvent()).isNull();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnWebhookTriggerConditionWithBitBucketWebhookSourceEmptyEventType() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);

    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/gateway/api/webhooks/")
        .append(webHookToken.getWebHookToken())
        .append("?accountId=")
        .append(accountId);

    WebHookTriggerCondition webhookTriggerCondition = WebHookTriggerCondition.builder()
                                                          .webHookToken(webHookToken)
                                                          .webhookSource(WebhookSource.BITBUCKET)
                                                          .bitBucketEvents(null)
                                                          .branchName("branchName")
                                                          .gitConnectorId("gitConnectorId")
                                                          .filePaths(Arrays.asList("filePath1", "filePath2"))
                                                          .checkFileContentChanged(true)
                                                          .build();
    Trigger trigger = Trigger.builder().condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(webhookTriggerCondition.getWebhookSource().name());
    assertThat(qlOnWebhook.getBranchRegex()).isEqualTo(webhookTriggerCondition.getBranchRegex());
    assertThat(qlOnWebhook.getBranchName()).isEqualTo(webhookTriggerCondition.getBranchName());
    assertThat(qlOnWebhook.getGitConnectorId()).isEqualTo(webhookTriggerCondition.getGitConnectorId());
    assertThat(qlOnWebhook.getGitConnectorName()).isEqualTo(gitConfig.getName());
    assertThat(qlOnWebhook.getFilePaths()).isEqualTo(webhookTriggerCondition.getFilePaths());
    assertThat(qlOnWebhook.getDeployOnlyIfFilesChanged())
        .isEqualTo(webhookTriggerCondition.isCheckFileContentChanged());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader()).isEqualTo("content-type: application/json");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNotNull();
    assertThat(qlOnWebhook.getWebhookEvent().getEvent()).isNull();
    assertThat(qlOnWebhook.getWebhookEvent().getAction()).isNull();
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnWebhookTriggerConditionWithBitBucketWebhookSource() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);

    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/gateway/api/webhooks/")
        .append(webHookToken.getWebHookToken())
        .append("?accountId=")
        .append(accountId);

    WebHookTriggerCondition webhookTriggerCondition =
        WebHookTriggerCondition.builder()
            .webHookToken(webHookToken)
            .webhookSource(WebhookSource.BITBUCKET)
            .bitBucketEvents(Arrays.asList(BitBucketEventType.PULL_REQUEST_CREATED))
            .branchName("branchName")
            .gitConnectorId("gitConnectorId")
            .filePaths(Arrays.asList("filePath1", "filePath2"))
            .checkFileContentChanged(true)
            .build();
    Trigger trigger = Trigger.builder().condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(webhookTriggerCondition.getWebhookSource().name());
    assertThat(qlOnWebhook.getBranchRegex()).isEqualTo(webhookTriggerCondition.getBranchRegex());
    assertThat(qlOnWebhook.getBranchName()).isEqualTo(webhookTriggerCondition.getBranchName());
    assertThat(qlOnWebhook.getGitConnectorId()).isEqualTo(webhookTriggerCondition.getGitConnectorId());
    assertThat(qlOnWebhook.getGitConnectorName()).isEqualTo(gitConfig.getName());
    assertThat(qlOnWebhook.getFilePaths()).isEqualTo(webhookTriggerCondition.getFilePaths());
    assertThat(qlOnWebhook.getDeployOnlyIfFilesChanged())
        .isEqualTo(webhookTriggerCondition.isCheckFileContentChanged());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader()).isEqualTo("content-type: application/json");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNotNull();
    assertThat(qlOnWebhook.getWebhookEvent().getEvent()).isEqualTo("pullrequest");
    assertThat(qlOnWebhook.getWebhookEvent().getAction()).isEqualTo("created");
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnWebhookTriggerConditionWithBitBucketWebhookSourceAllEvent() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);

    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/gateway/api/webhooks/")
        .append(webHookToken.getWebHookToken())
        .append("?accountId=")
        .append(accountId);

    WebHookTriggerCondition webhookTriggerCondition = WebHookTriggerCondition.builder()
                                                          .webHookToken(webHookToken)
                                                          .webhookSource(WebhookSource.BITBUCKET)
                                                          .bitBucketEvents(Arrays.asList(BitBucketEventType.ALL))
                                                          .branchName("branchName")
                                                          .gitConnectorId("gitConnectorId")
                                                          .filePaths(Arrays.asList("filePath1", "filePath2"))
                                                          .checkFileContentChanged(true)
                                                          .build();
    Trigger trigger = Trigger.builder().condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(webhookTriggerCondition.getWebhookSource().name());
    assertThat(qlOnWebhook.getBranchRegex()).isEqualTo(webhookTriggerCondition.getBranchRegex());
    assertThat(qlOnWebhook.getBranchName()).isEqualTo(webhookTriggerCondition.getBranchName());
    assertThat(qlOnWebhook.getGitConnectorId()).isEqualTo(webhookTriggerCondition.getGitConnectorId());
    assertThat(qlOnWebhook.getGitConnectorName()).isEqualTo(gitConfig.getName());
    assertThat(qlOnWebhook.getFilePaths()).isEqualTo(webhookTriggerCondition.getFilePaths());
    assertThat(qlOnWebhook.getDeployOnlyIfFilesChanged())
        .isEqualTo(webhookTriggerCondition.isCheckFileContentChanged());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader()).isEqualTo("content-type: application/json");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNotNull();
    assertThat(qlOnWebhook.getWebhookEvent().getEvent()).isEqualTo("all");
    assertThat(qlOnWebhook.getWebhookEvent().getAction()).isNull();
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveTriggerConditionShouldReturnOnNewArtifactConditionType() throws Exception {
    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_NEW_ARTIFACT).build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    ArtifactTriggerCondition artifactTriggerCondition = ArtifactTriggerCondition.builder()
                                                            .artifactSourceName("name")
                                                            .artifactStreamId("id")
                                                            .artifactFilter("filter")
                                                            .regex(true)
                                                            .build();

    Mockito.doReturn(artifactTriggerCondition)
        .when(triggerConditionController)
        .validateAndResolveOnNewArtifactConditionType(ArgumentMatchers.any(QLCreateOrUpdateTriggerInput.class));

    ArtifactTriggerCondition retrievedArtifactTriggerCondition =
        (ArtifactTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateOrUpdateTriggerInput);

    assertThat(retrievedArtifactTriggerCondition.getArtifactStreamId())
        .isEqualTo(artifactTriggerCondition.getArtifactStreamId());
    assertThat(retrievedArtifactTriggerCondition.getArtifactSourceName())
        .isEqualTo(artifactTriggerCondition.getArtifactSourceName());
    assertThat(retrievedArtifactTriggerCondition.getArtifactFilter())
        .isEqualTo(artifactTriggerCondition.getArtifactFilter());
    assertThat(retrievedArtifactTriggerCondition.isRegex()).isEqualTo(artifactTriggerCondition.isRegex());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveTriggerConditionShouldReturnOnPipelineCompletionConditionType() throws Exception {
    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_PIPELINE_COMPLETION).build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    PipelineTriggerCondition pipelineTriggerCondition =
        PipelineTriggerCondition.builder().pipelineId("id").pipelineName("name").build();

    Mockito.doReturn(pipelineTriggerCondition)
        .when(triggerConditionController)
        .validateAndResolveOnPipelineCompletionConditionType(ArgumentMatchers.any(QLCreateOrUpdateTriggerInput.class));

    PipelineTriggerCondition retrievedPipelineTriggerCondition =
        (PipelineTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateOrUpdateTriggerInput);

    assertThat(pipelineTriggerCondition.getPipelineId()).isEqualTo(retrievedPipelineTriggerCondition.getPipelineId());
    assertThat(pipelineTriggerCondition.getPipelineName())
        .isEqualTo(retrievedPipelineTriggerCondition.getPipelineName());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveTriggerConditionShouldReturnOnScheduleConditionType() throws Exception {
    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_SCHEDULE).build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    ScheduledTriggerCondition scheduledTriggerCondition = ScheduledTriggerCondition.builder()
                                                              .cronExpression("expression")
                                                              .cronDescription("description")
                                                              .onNewArtifactOnly(true)
                                                              .build();

    Mockito.doReturn(scheduledTriggerCondition)
        .when(triggerConditionController)
        .validateAndResolveOnScheduleConditionType(ArgumentMatchers.any(QLCreateOrUpdateTriggerInput.class));

    ScheduledTriggerCondition retrievedScheduledTriggerCondition =
        (ScheduledTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput);

    assertThat(retrievedScheduledTriggerCondition.getCronExpression())
        .isEqualTo(scheduledTriggerCondition.getCronExpression());
    assertThat(retrievedScheduledTriggerCondition.getCronDescription())
        .isEqualTo(scheduledTriggerCondition.getCronDescription());
    assertThat(retrievedScheduledTriggerCondition.isOnNewArtifactOnly())
        .isEqualTo(scheduledTriggerCondition.isOnNewArtifactOnly());
  }

  @Test
  @Owner(developers = MILAN)
  @Category(UnitTests.class)
  public void resolveTriggerConditionShouldReturnOnWebhookConditionTypeWithGithubEvent() throws Exception {
    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_WEBHOOK).build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    WebHookToken webHookToken =
        WebHookToken.builder().payload("payload").httpMethod("POST").webHookToken("token").build();

    WebHookTriggerCondition webHookTriggerCondition =
        WebHookTriggerCondition.builder()
            .webHookToken(webHookToken)
            .webhookSource(WebhookSource.GITHUB)
            .eventTypes(Collections.singletonList(WebhookEventType.PULL_REQUEST))
            .actions(Collections.singletonList(GithubAction.OPENED))
            .branchRegex("regex")
            .checkFileContentChanged(true)
            .filePaths(Arrays.asList("filePath1", "filePath2"))
            .gitConnectorId("gitConnectorId")
            .build();

    Mockito.doReturn(webHookTriggerCondition)
        .when(triggerConditionController)
        .validateAndResolveOnWebhookConditionType(ArgumentMatchers.any(QLCreateOrUpdateTriggerInput.class));

    WebHookTriggerCondition retrievedWebHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput);

    assertThat(retrievedWebHookTriggerCondition.getWebHookToken().getPayload())
        .isEqualTo(webHookTriggerCondition.getWebHookToken().getPayload());
    assertThat(retrievedWebHookTriggerCondition.getWebHookToken().getHttpMethod())
        .isEqualTo(webHookTriggerCondition.getWebHookToken().getHttpMethod());
    assertThat(retrievedWebHookTriggerCondition.getWebHookToken().getPayload())
        .isEqualTo(webHookTriggerCondition.getWebHookToken().getPayload());
    assertThat(retrievedWebHookTriggerCondition.getWebhookSource())
        .isEqualTo(webHookTriggerCondition.getWebhookSource());
    assertThat(retrievedWebHookTriggerCondition.getEventTypes().get(0))
        .isEqualTo(webHookTriggerCondition.getEventTypes().get(0));
    assertThat(retrievedWebHookTriggerCondition.getActions().get(0))
        .isEqualTo(webHookTriggerCondition.getActions().get(0));
    assertThat(retrievedWebHookTriggerCondition.getBranchRegex()).isEqualTo(webHookTriggerCondition.getBranchRegex());
    assertThat(retrievedWebHookTriggerCondition.getBranchName()).isEqualTo(webHookTriggerCondition.getBranchName());
    assertThat(retrievedWebHookTriggerCondition.isCheckFileContentChanged())
        .isEqualTo(webHookTriggerCondition.isCheckFileContentChanged());
    assertThat(retrievedWebHookTriggerCondition.getGitConnectorId())
        .isEqualTo(webHookTriggerCondition.getGitConnectorId());
    assertThat(retrievedWebHookTriggerCondition.getFilePaths()).isEqualTo(webHookTriggerCondition.getFilePaths());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnWebhookTriggerForK8sOnPrem() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);

    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES_ONPREM);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/api/webhooks/").append(webHookToken.getWebHookToken()).append("?accountId=").append(accountId);

    WebHookTriggerCondition webhookTriggerCondition = WebHookTriggerCondition.builder()
                                                          .webHookToken(webHookToken)
                                                          .webhookSource(WebhookSource.GITHUB)
                                                          .eventTypes(Arrays.asList(WebhookEventType.PACKAGE))
                                                          .actions(Arrays.asList(GithubAction.OPENED))
                                                          .branchRegex("regex")
                                                          .branchName("branchName")
                                                          .gitConnectorId("gitConnectorId")
                                                          .filePaths(Arrays.asList("filePath1", "filePath2"))
                                                          .checkFileContentChanged(true)
                                                          .build();
    Trigger trigger = Trigger.builder().condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(webhookTriggerCondition.getWebhookSource().name());
    assertThat(qlOnWebhook.getBranchRegex()).isEqualTo(webhookTriggerCondition.getBranchRegex());
    assertThat(qlOnWebhook.getBranchName()).isEqualTo(webhookTriggerCondition.getBranchName());
    assertThat(qlOnWebhook.getGitConnectorId()).isEqualTo(webhookTriggerCondition.getGitConnectorId());
    assertThat(qlOnWebhook.getGitConnectorName()).isEqualTo(gitConfig.getName());
    assertThat(qlOnWebhook.getFilePaths()).isEqualTo(webhookTriggerCondition.getFilePaths());
    assertThat(qlOnWebhook.getDeployOnlyIfFilesChanged())
        .isEqualTo(webhookTriggerCondition.isCheckFileContentChanged());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader()).isEqualTo("content-type: application/json");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNotNull();
    assertThat(qlOnWebhook.getWebhookEvent().getEvent()).isEqualTo(WebhookEventType.PACKAGE.getValue());
    assertThat(qlOnWebhook.getWebhookEvent().getAction()).isEqualTo(GithubAction.OPENED.getValue());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnWebhookTriggerWhenGithubActionIsNullForPullRequests() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);

    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES_ONPREM);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/api/webhooks/").append(webHookToken.getWebHookToken()).append("?accountId=").append(accountId);

    WebHookTriggerCondition webhookTriggerCondition = WebHookTriggerCondition.builder()
                                                          .webHookToken(webHookToken)
                                                          .webhookSource(WebhookSource.GITHUB)
                                                          .eventTypes(Arrays.asList(WebhookEventType.PULL_REQUEST))
                                                          .actions(null)
                                                          .branchRegex("regex")
                                                          .branchName("branchName")
                                                          .gitConnectorId("gitConnectorId")
                                                          .filePaths(Arrays.asList("filePath1", "filePath2"))
                                                          .checkFileContentChanged(true)
                                                          .build();
    Trigger trigger = Trigger.builder().condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(webhookTriggerCondition.getWebhookSource().name());
    assertThat(qlOnWebhook.getBranchRegex()).isEqualTo(webhookTriggerCondition.getBranchRegex());
    assertThat(qlOnWebhook.getBranchName()).isEqualTo(webhookTriggerCondition.getBranchName());
    assertThat(qlOnWebhook.getGitConnectorId()).isEqualTo(webhookTriggerCondition.getGitConnectorId());
    assertThat(qlOnWebhook.getGitConnectorName()).isEqualTo(gitConfig.getName());
    assertThat(qlOnWebhook.getFilePaths()).isEqualTo(webhookTriggerCondition.getFilePaths());
    assertThat(qlOnWebhook.getDeployOnlyIfFilesChanged())
        .isEqualTo(webhookTriggerCondition.isCheckFileContentChanged());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader()).isEqualTo("content-type: application/json");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNotNull();
    assertThat(qlOnWebhook.getWebhookEvent().getEvent()).isEqualTo(WebhookEventType.PULL_REQUEST.getValue());
    assertThat(qlOnWebhook.getWebhookEvent().getAction()).isNull();
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void populateTriggerConditionShouldReturnWebhookTriggerWhenGithubActionIsNullForReleases() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);

    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES_ONPREM);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/api/webhooks/").append(webHookToken.getWebHookToken()).append("?accountId=").append(accountId);

    WebHookTriggerCondition webhookTriggerCondition = WebHookTriggerCondition.builder()
                                                          .webHookToken(webHookToken)
                                                          .webhookSource(WebhookSource.GITHUB)
                                                          .eventTypes(Arrays.asList(WebhookEventType.RELEASE))
                                                          .actions(null)
                                                          .branchRegex("regex")
                                                          .branchName("branchName")
                                                          .gitConnectorId("gitConnectorId")
                                                          .filePaths(Arrays.asList("filePath1", "filePath2"))
                                                          .checkFileContentChanged(true)
                                                          .build();
    Trigger trigger = Trigger.builder().condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(webhookTriggerCondition.getWebhookSource().name());
    assertThat(qlOnWebhook.getBranchRegex()).isEqualTo(webhookTriggerCondition.getBranchRegex());
    assertThat(qlOnWebhook.getBranchName()).isEqualTo(webhookTriggerCondition.getBranchName());
    assertThat(qlOnWebhook.getGitConnectorId()).isEqualTo(webhookTriggerCondition.getGitConnectorId());
    assertThat(qlOnWebhook.getGitConnectorName()).isEqualTo(gitConfig.getName());
    assertThat(qlOnWebhook.getFilePaths()).isEqualTo(webhookTriggerCondition.getFilePaths());
    assertThat(qlOnWebhook.getDeployOnlyIfFilesChanged())
        .isEqualTo(webhookTriggerCondition.isCheckFileContentChanged());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader()).isEqualTo("content-type: application/json");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNotNull();
    assertThat(qlOnWebhook.getWebhookEvent().getEvent()).isEqualTo(WebhookEventType.RELEASE.getValue());
    assertThat(qlOnWebhook.getWebhookEvent().getAction()).isNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveTriggerConditionOnNewArtifactConditionType() {
    QLArtifactConditionInput artifactTriggerCondition =
        QLArtifactConditionInput.builder().artifactSourceId("id").artifactFilter("filter").regex(true).build();

    QLTriggerConditionInput qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                                          .conditionType(QLConditionType.ON_NEW_ARTIFACT)
                                                          .artifactConditionInput(artifactTriggerCondition)
                                                          .build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).applicationId(APP_ID).build();
    when(artifactStreamService.get("id")).thenReturn(CustomArtifactStream.builder().appId(APP_ID).build());

    ArtifactTriggerCondition retrievedArtifactTriggerCondition =
        (ArtifactTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateOrUpdateTriggerInput);

    assertThat(retrievedArtifactTriggerCondition.getArtifactStreamId())
        .isEqualTo(artifactTriggerCondition.getArtifactSourceId());
    assertThat(retrievedArtifactTriggerCondition.getArtifactFilter())
        .isEqualTo(artifactTriggerCondition.getArtifactFilter());
    assertThat(retrievedArtifactTriggerCondition.isRegex()).isEqualTo(artifactTriggerCondition.getRegex());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorOnNewArtifactConditionInvalidCases() {
    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_NEW_ARTIFACT).build();
    QLCreateOrUpdateTriggerInputBuilder qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).applicationId(APP_ID);

    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateOrUpdateTriggerInput.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ArtifactConditionInput must not be null");

    QLArtifactConditionInput artifactTriggerCondition =
        QLArtifactConditionInput.builder().artifactFilter("filter").regex(true).build();

    qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                  .conditionType(QLConditionType.ON_NEW_ARTIFACT)
                                  .artifactConditionInput(artifactTriggerCondition)
                                  .build();

    QLCreateOrUpdateTriggerInputBuilder qlCreateOrUpdateTriggerInput2 =
        qlCreateOrUpdateTriggerInput.condition(qlTriggerConditionInput);
    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateOrUpdateTriggerInput2.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ArtifactSource must not be null nor empty");

    artifactTriggerCondition =
        QLArtifactConditionInput.builder().artifactSourceId("id").artifactFilter("filter").regex(true).build();

    qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                  .conditionType(QLConditionType.ON_NEW_ARTIFACT)
                                  .artifactConditionInput(artifactTriggerCondition)
                                  .build();

    QLCreateOrUpdateTriggerInputBuilder qlCreateOrUpdateTriggerInput3 =
        qlCreateOrUpdateTriggerInput.condition(qlTriggerConditionInput);
    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateOrUpdateTriggerInput3.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Artifact Stream doesn't exist");

    when(artifactStreamService.get("id")).thenReturn(CustomArtifactStream.builder().build());
    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateOrUpdateTriggerInput3.build()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Artifact Stream doesn't belong to this application");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveTriggerConditionOnScheduleConditionType() {
    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_SCHEDULE).build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ScheduleConditionInput must not be null");

    QLScheduleConditionInput scheduledTriggerCondition =
        QLScheduleConditionInput.builder().cronExpression("expression").onNewArtifactOnly(true).build();

    qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                  .conditionType(QLConditionType.ON_SCHEDULE)
                                  .scheduleConditionInput(scheduledTriggerCondition)
                                  .build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    ScheduledTriggerCondition retrievedScheduledTriggerCondition =
        (ScheduledTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput2);

    assertThat(retrievedScheduledTriggerCondition.getCronExpression())
        .isEqualTo(scheduledTriggerCondition.getCronExpression());
    assertThat(retrievedScheduledTriggerCondition.isOnNewArtifactOnly())
        .isEqualTo(scheduledTriggerCondition.getOnNewArtifactOnly());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveTriggerConditionOnPipelineCompletion() {
    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_PIPELINE_COMPLETION).build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("PipelineConditionInput must not be null");

    QLPipelineConditionInput pipelineConditionInput = QLPipelineConditionInput.builder().pipelineId("id").build();

    doNothing().when(triggerActionController).validatePipeline(qlCreateTriggerInput, "id");
    qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                  .conditionType(QLConditionType.ON_PIPELINE_COMPLETION)
                                  .pipelineConditionInput(pipelineConditionInput)
                                  .build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    PipelineTriggerCondition retrievedScheduledTriggerCondition =
        (PipelineTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput2);

    assertThat(retrievedScheduledTriggerCondition.getPipelineId()).isEqualTo(pipelineConditionInput.getPipelineId());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowErrorOnMissingInputWebhookConditionType() {
    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_WEBHOOK).build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("WebhookConditionInput must not be null");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveGitLabTriggerCondition() {
    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder()
            .conditionType(QLConditionType.ON_WEBHOOK)
            .webhookConditionInput(QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.GITLAB).build())
            .build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Gitlab event must not be null");

    QLWebhookConditionInput webhookConditionInput = QLWebhookConditionInput.builder()
                                                        .webhookSourceType(QLWebhookSource.GITLAB)
                                                        .gitlabEvent(QLGitlabEvent.ANY)
                                                        .build();

    qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                  .conditionType(QLConditionType.ON_WEBHOOK)
                                  .webhookConditionInput(webhookConditionInput)
                                  .build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput2);

    assertThat(webHookTriggerCondition.getWebhookSource().name())
        .isEqualTo(webhookConditionInput.getWebhookSourceType().name());

    assertThat(webHookTriggerCondition.getEventTypes().get(0).name())
        .isEqualTo(webhookConditionInput.getGitlabEvent().name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveBitBucketTriggerCondition() {
    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder()
            .conditionType(QLConditionType.ON_WEBHOOK)
            .webhookConditionInput(
                QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.BITBUCKET).build())
            .build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Bitbucket event must not be null");

    QLWebhookConditionInput webhookConditionInput = QLWebhookConditionInput.builder()
                                                        .webhookSourceType(QLWebhookSource.BITBUCKET)
                                                        .bitbucketEvent(QLBitbucketEvent.ANY)
                                                        .build();

    qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                  .conditionType(QLConditionType.ON_WEBHOOK)
                                  .webhookConditionInput(webhookConditionInput)
                                  .build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput2);

    assertThat(webHookTriggerCondition.getWebhookSource().name())
        .isEqualTo(webhookConditionInput.getWebhookSourceType().name());

    assertThat(webHookTriggerCondition.getEventTypes().get(0).name())
        .isEqualTo(webhookConditionInput.getBitbucketEvent().name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveGitHubPullRequestTriggerCondition() {
    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder()
            .conditionType(QLConditionType.ON_WEBHOOK)
            .webhookConditionInput(QLWebhookConditionInput.builder().webhookSourceType(QLWebhookSource.GITHUB).build())
            .build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Github event must not be null");

    QLWebhookConditionInput webhookConditionInput =
        QLWebhookConditionInput.builder()
            .webhookSourceType(QLWebhookSource.GITHUB)
            .deployOnlyIfFilesChanged(true)
            .gitConnectorId("connectorId")
            .repoName("gitRepo")
            .githubEvent(
                QLGitHubEvent.builder().event(QLGitHubEventType.PULL_REQUEST).action(QLGitHubAction.CLOSED).build())
            .build();

    qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                  .conditionType(QLConditionType.ON_WEBHOOK)
                                  .webhookConditionInput(webhookConditionInput)
                                  .build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput2);

    assertThat(webHookTriggerCondition.getWebhookSource().name())
        .isEqualTo(webhookConditionInput.getWebhookSourceType().name());

    assertThat(webHookTriggerCondition.getEventTypes().get(0).name())
        .isEqualTo(webhookConditionInput.getGithubEvent().getEvent().name());
    assertThat(webHookTriggerCondition.getActions().get(0).name())
        .isEqualTo(webhookConditionInput.getGithubEvent().getAction().name());

    assertThat(webHookTriggerCondition.getGitConnectorId()).isEqualTo(webhookConditionInput.getGitConnectorId());
    assertThat(webHookTriggerCondition.getRepoName()).isEqualTo(webhookConditionInput.getRepoName());

    webhookConditionInput =
        QLWebhookConditionInput.builder()
            .webhookSourceType(QLWebhookSource.GITHUB)
            .githubEvent(
                QLGitHubEvent.builder().event(QLGitHubEventType.PULL_REQUEST).action(QLGitHubAction.RELEASED).build())
            .build();

    QLCreateOrUpdateTriggerInput qlCreateTriggerInput3 =
        QLCreateOrUpdateTriggerInput.builder()
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(webhookConditionInput)
                           .build())
            .build();
    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput3))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unsupported GitHub Action");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveGitHubReleaseTriggerCondition() {
    QLWebhookConditionInput webhookConditionInput =
        QLWebhookConditionInput.builder()
            .webhookSourceType(QLWebhookSource.GITHUB)
            .githubEvent(
                QLGitHubEvent.builder().event(QLGitHubEventType.RELEASE).action(QLGitHubAction.PRE_RELEASED).build())
            .build();

    QLTriggerConditionInput qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                                          .conditionType(QLConditionType.ON_WEBHOOK)
                                                          .webhookConditionInput(webhookConditionInput)
                                                          .build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput2);

    assertThat(webHookTriggerCondition.getWebhookSource().name())
        .isEqualTo(webhookConditionInput.getWebhookSourceType().name());

    assertThat(webHookTriggerCondition.getEventTypes().get(0).name())
        .isEqualTo(webhookConditionInput.getGithubEvent().getEvent().name());

    assertThat(webHookTriggerCondition.getReleaseActions().get(0).name())
        .isEqualTo(webhookConditionInput.getGithubEvent().getAction().name());

    webhookConditionInput =
        QLWebhookConditionInput.builder()
            .webhookSourceType(QLWebhookSource.GITHUB)
            .githubEvent(QLGitHubEvent.builder().event(QLGitHubEventType.RELEASE).action(QLGitHubAction.OPENED).build())
            .build();

    QLCreateOrUpdateTriggerInput qlCreateTriggerInput3 =
        QLCreateOrUpdateTriggerInput.builder()
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(webhookConditionInput)
                           .build())
            .build();
    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput3))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unsupported GitHub Release Action");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldResolveGitHubPackageTriggerCondition() {
    QLWebhookConditionInput webhookConditionInput = QLWebhookConditionInput.builder()
                                                        .webhookSourceType(QLWebhookSource.GITHUB)
                                                        .githubEvent(QLGitHubEvent.builder()
                                                                         .event(QLGitHubEventType.PACKAGE)
                                                                         .action(QLGitHubAction.PACKAGE_PUBLISHED)
                                                                         .build())
                                                        .build();

    QLTriggerConditionInput qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                                          .conditionType(QLConditionType.ON_WEBHOOK)
                                                          .webhookConditionInput(webhookConditionInput)
                                                          .build();
    QLCreateOrUpdateTriggerInput qlCreateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();

    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput2);

    assertThat(webHookTriggerCondition.getWebhookSource().name())
        .isEqualTo(webhookConditionInput.getWebhookSourceType().name());

    assertThat(webHookTriggerCondition.getEventTypes().get(0).name())
        .isEqualTo(webhookConditionInput.getGithubEvent().getEvent().name());

    assertThat(webHookTriggerCondition.getActions().get(0).name())
        .isEqualTo(webhookConditionInput.getGithubEvent().getAction().name());

    webhookConditionInput =
        QLWebhookConditionInput.builder()
            .webhookSourceType(QLWebhookSource.GITHUB)
            .githubEvent(QLGitHubEvent.builder().event(QLGitHubEventType.PACKAGE).action(QLGitHubAction.OPENED).build())
            .build();

    QLCreateOrUpdateTriggerInput qlCreateTriggerInput3 =
        QLCreateOrUpdateTriggerInput.builder()
            .condition(QLTriggerConditionInput.builder()
                           .conditionType(QLConditionType.ON_WEBHOOK)
                           .webhookConditionInput(webhookConditionInput)
                           .build())
            .build();
    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateTriggerInput3))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unsupported GitHub Package Action");
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void resolveBitBucketRefsChangedTriggerConditionTest() {
    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(
            getQLCreateOrUpdateTriggerInput(QLBitbucketEvent.REFS_CHANGED, QLWebhookSource.BITBUCKET, BRANCH_REGEX));

    assertThat(webHookTriggerCondition.getWebhookSource().name()).isEqualTo(QLWebhookSource.BITBUCKET.name());
    assertThat(webHookTriggerCondition.getBranchRegex()).isEqualTo(BRANCH_REGEX);
    assertThat(webHookTriggerCondition.getEventTypes().get(0).name()).isEqualTo(WebhookEventType.REPO.name());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getValue()).isEqualTo(WebhookEventType.REPO.getValue());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getDisplayName())
        .isEqualTo(WebhookEventType.REPO.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getDisplayName())
        .isEqualTo(BitBucketEventType.REFS_CHANGED.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getValue())
        .isEqualTo(BitBucketEventType.REFS_CHANGED.getValue());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).name())
        .isEqualTo(BitBucketEventType.REFS_CHANGED.name());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void resolveBitBucketUpdatedTriggerConditionTest() {
    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(
            getQLCreateOrUpdateTriggerInput(QLBitbucketEvent.UPDATED, QLWebhookSource.BITBUCKET, BRANCH_REGEX));

    assertThat(webHookTriggerCondition.getWebhookSource().name()).isEqualTo(QLWebhookSource.BITBUCKET.name());
    assertThat(webHookTriggerCondition.getBranchRegex()).isEqualTo(BRANCH_REGEX);
    assertThat(webHookTriggerCondition.getEventTypes().get(0).name()).isEqualTo(WebhookEventType.REPO.name());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getValue()).isEqualTo(WebhookEventType.REPO.getValue());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getDisplayName())
        .isEqualTo(WebhookEventType.REPO.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getDisplayName())
        .isEqualTo(BitBucketEventType.UPDATED.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getValue())
        .isEqualTo(BitBucketEventType.UPDATED.getValue());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).name()).isEqualTo(BitBucketEventType.UPDATED.name());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void resolveBitBucketPushTriggerConditionTest() {
    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(
            getQLCreateOrUpdateTriggerInput(QLBitbucketEvent.PUSH, QLWebhookSource.BITBUCKET, BRANCH_REGEX));

    assertThat(webHookTriggerCondition.getWebhookSource().name()).isEqualTo(QLWebhookSource.BITBUCKET.name());
    assertThat(webHookTriggerCondition.getBranchRegex()).isEqualTo(BRANCH_REGEX);
    assertThat(webHookTriggerCondition.getEventTypes().get(0).name()).isEqualTo(WebhookEventType.REPO.name());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getValue()).isEqualTo(WebhookEventType.REPO.getValue());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getDisplayName())
        .isEqualTo(WebhookEventType.REPO.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getDisplayName())
        .isEqualTo(BitBucketEventType.PUSH.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getValue())
        .isEqualTo(BitBucketEventType.PUSH.getValue());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).name()).isEqualTo(BitBucketEventType.PUSH.name());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void resolveBitBucketForkTriggerConditionTest() {
    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(
            getQLCreateOrUpdateTriggerInput(QLBitbucketEvent.FORK, QLWebhookSource.BITBUCKET, BRANCH_REGEX));

    assertThat(webHookTriggerCondition.getWebhookSource().name()).isEqualTo(QLWebhookSource.BITBUCKET.name());
    assertThat(webHookTriggerCondition.getBranchRegex()).isEqualTo(BRANCH_REGEX);
    assertThat(webHookTriggerCondition.getEventTypes().get(0).name()).isEqualTo(WebhookEventType.REPO.name());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getValue()).isEqualTo(WebhookEventType.REPO.getValue());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getDisplayName())
        .isEqualTo(WebhookEventType.REPO.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getDisplayName())
        .isEqualTo(BitBucketEventType.FORK.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getValue())
        .isEqualTo(BitBucketEventType.FORK.getValue());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).name()).isEqualTo(BitBucketEventType.FORK.name());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void resolveBitBucketCommitCommentCreatedTriggerConditionTest() {
    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(getQLCreateOrUpdateTriggerInput(
            QLBitbucketEvent.COMMIT_COMMENT_CREATED, QLWebhookSource.BITBUCKET, BRANCH_REGEX));

    assertThat(webHookTriggerCondition.getWebhookSource().name()).isEqualTo(QLWebhookSource.BITBUCKET.name());
    assertThat(webHookTriggerCondition.getBranchRegex()).isEqualTo(BRANCH_REGEX);
    assertThat(webHookTriggerCondition.getEventTypes().get(0).name()).isEqualTo(WebhookEventType.REPO.name());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getValue()).isEqualTo(WebhookEventType.REPO.getValue());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getDisplayName())
        .isEqualTo(WebhookEventType.REPO.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getDisplayName())
        .isEqualTo(BitBucketEventType.COMMIT_COMMENT_CREATED.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getValue())
        .isEqualTo(BitBucketEventType.COMMIT_COMMENT_CREATED.getValue());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).name())
        .isEqualTo(BitBucketEventType.COMMIT_COMMENT_CREATED.name());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void resolveBitBucketBuildStatusCreatedTriggerConditionTest() {
    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(getQLCreateOrUpdateTriggerInput(
            QLBitbucketEvent.BUILD_STATUS_CREATED, QLWebhookSource.BITBUCKET, BRANCH_REGEX));

    assertThat(webHookTriggerCondition.getWebhookSource().name()).isEqualTo(QLWebhookSource.BITBUCKET.name());
    assertThat(webHookTriggerCondition.getBranchRegex()).isEqualTo(BRANCH_REGEX);
    assertThat(webHookTriggerCondition.getEventTypes().get(0).name()).isEqualTo(WebhookEventType.REPO.name());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getValue()).isEqualTo(WebhookEventType.REPO.getValue());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getDisplayName())
        .isEqualTo(WebhookEventType.REPO.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getDisplayName())
        .isEqualTo(BitBucketEventType.BUILD_STATUS_CREATED.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getValue())
        .isEqualTo(BitBucketEventType.BUILD_STATUS_CREATED.getValue());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).name())
        .isEqualTo(BitBucketEventType.BUILD_STATUS_CREATED.name());
  }

  @Test
  @Owner(developers = MILOS)
  @Category(UnitTests.class)
  public void resolveBitBucketBuildStatusUpdatedTriggerConditionTest() {
    WebHookTriggerCondition webHookTriggerCondition =
        (WebHookTriggerCondition) triggerConditionController.resolveTriggerCondition(getQLCreateOrUpdateTriggerInput(
            QLBitbucketEvent.BUILD_STATUS_UPDATED, QLWebhookSource.BITBUCKET, BRANCH_REGEX));

    assertThat(webHookTriggerCondition.getWebhookSource().name()).isEqualTo(QLWebhookSource.BITBUCKET.name());
    assertThat(webHookTriggerCondition.getBranchRegex()).isEqualTo(BRANCH_REGEX);
    assertThat(webHookTriggerCondition.getEventTypes().get(0).name()).isEqualTo(WebhookEventType.REPO.name());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getValue()).isEqualTo(WebhookEventType.REPO.getValue());
    assertThat(webHookTriggerCondition.getEventTypes().get(0).getDisplayName())
        .isEqualTo(WebhookEventType.REPO.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getDisplayName())
        .isEqualTo(BitBucketEventType.BUILD_STATUS_UPDATED.getDisplayName());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).getValue())
        .isEqualTo(BitBucketEventType.BUILD_STATUS_UPDATED.getValue());
    assertThat(webHookTriggerCondition.getBitBucketEvents().get(0).name())
        .isEqualTo(BitBucketEventType.BUILD_STATUS_UPDATED.name());
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testHeaderForCustomTriggerWithFfEnabled() {
    String accountId = "1234";
    PortalConfig portalConfig = Mockito.mock(PortalConfig.class);

    Mockito.when(portalConfig.getUrl()).thenReturn("URL");
    mainConfiguration.setPortal(portalConfig);
    Mockito.when(mainConfiguration.getPortal()).thenReturn(portalConfig);

    when(mainConfiguration.getDeployMode()).thenReturn(DeployMode.KUBERNETES_ONPREM);

    WebHookToken webHookToken =
        WebHookToken.builder().webHookToken("webhookToken").httpMethod("POST").payload("payload").build();
    StringBuilder webhookURL = new StringBuilder(mainConfiguration.getPortal().getUrl());
    webhookURL.append("/api/webhooks/").append(webHookToken.getWebHookToken()).append("?accountId=").append(accountId);

    WebHookTriggerCondition webhookTriggerCondition =
        WebHookTriggerCondition.builder().webHookToken(webHookToken).build();
    Trigger trigger = Trigger.builder().appId(APP_ID).condition(webhookTriggerCondition).build();

    SettingAttribute gitConfig = new SettingAttribute();
    gitConfig.setName("gitConnectorName");
    Mockito.when(settingsService.get(ArgumentMatchers.anyString())).thenReturn(gitConfig);
    when(featureFlagService.isEnabled(WEBHOOK_TRIGGER_AUTHORIZATION, accountId)).thenReturn(true);

    QLOnWebhook qlOnWebhook = (QLOnWebhook) triggerConditionController.populateTriggerCondition(trigger, accountId);

    assertThat(qlOnWebhook).isNotNull();
    assertThat(qlOnWebhook.getWebhookSource().name()).isEqualTo(QLWebhookSource.CUSTOM.name());
    assertThat(qlOnWebhook.getTriggerConditionType().name())
        .isEqualTo(webhookTriggerCondition.getConditionType().name());
    assertThat(qlOnWebhook.getWebhookDetails()).isNotNull();
    assertThat(qlOnWebhook.getWebhookDetails().getHeader())
        .isEqualTo("content-type: application/json, x-api-key: x-api-key_placeholder");
    assertThat(qlOnWebhook.getWebhookDetails().getMethod()).isEqualTo(webHookToken.getHttpMethod());
    assertThat(qlOnWebhook.getWebhookDetails().getPayload()).isEqualTo(webHookToken.getPayload());
    assertThat(qlOnWebhook.getWebhookDetails().getWebhookURL()).isEqualTo(webhookURL.toString());
    assertThat(qlOnWebhook.getWebhookEvent()).isNull();
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldPopulateNewManifestTriggerCondition() {
    ManifestTriggerCondition manifestTriggerCondition = ManifestTriggerCondition.builder()
                                                            .appManifestId("AppManifestId")
                                                            .serviceId("serviceId")
                                                            .versionRegex("Filter")
                                                            .build();
    manifestTriggerCondition.setConditionType(TriggerConditionType.NEW_MANIFEST);
    Trigger trigger = Trigger.builder().appId("appId").condition(manifestTriggerCondition).build();

    QLOnNewManifest qlOnNewManifest =
        (QLOnNewManifest) triggerConditionController.populateTriggerCondition(trigger, null);

    assertThat(qlOnNewManifest).isNotNull();
    assertThat(qlOnNewManifest.getVersionRegex()).isEqualTo(manifestTriggerCondition.getVersionRegex());
    assertThat(qlOnNewManifest.getServiceId()).isEqualTo(manifestTriggerCondition.getServiceId());
    assertThat(qlOnNewManifest.getAppManifestId()).isEqualTo(manifestTriggerCondition.getAppManifestId());
    assertThat(qlOnNewManifest.getTriggerConditionType().name())
        .isEqualTo(manifestTriggerCondition.getConditionType().name());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void resolveTriggerConditionForNewManifest() {
    QLManifestConditionInput manifestTriggerCondition =
        QLManifestConditionInput.builder().appManifestId(MANIFEST_ID).versionRegex("filter").build();

    QLTriggerConditionInput qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                                          .conditionType(QLConditionType.ON_NEW_MANIFEST)
                                                          .manifestConditionInput(manifestTriggerCondition)
                                                          .build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().applicationId(APP_ID).condition(qlTriggerConditionInput).build();

    when(applicationManifestService.getById(APP_ID, MANIFEST_ID))
        .thenReturn(ApplicationManifest.builder().storeType(StoreType.HelmChartRepo).build());

    ManifestTriggerCondition retrievedManifestTriggerCondition =
        (ManifestTriggerCondition) triggerConditionController.resolveTriggerCondition(qlCreateOrUpdateTriggerInput);

    assertThat(retrievedManifestTriggerCondition.getAppManifestId())
        .isEqualTo(manifestTriggerCondition.getAppManifestId());
    assertThat(retrievedManifestTriggerCondition.getVersionRegex())
        .isEqualTo(manifestTriggerCondition.getVersionRegex());
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldFailForTriggerConditionForNewManifestInvalidCases() {
    QLManifestConditionInput manifestTriggerCondition =
        QLManifestConditionInput.builder().appManifestId(MANIFEST_ID).versionRegex("filter").build();

    QLTriggerConditionInput qlTriggerConditionInput =
        QLTriggerConditionInput.builder().conditionType(QLConditionType.ON_NEW_MANIFEST).build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput =
        QLCreateOrUpdateTriggerInput.builder().applicationId(APP_ID).condition(qlTriggerConditionInput).build();

    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateOrUpdateTriggerInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("ManifestConditionInput cannot not be null for On New Manifest Trigger");

    qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                  .conditionType(QLConditionType.ON_NEW_MANIFEST)
                                  .manifestConditionInput(manifestTriggerCondition)
                                  .build();
    QLCreateOrUpdateTriggerInput qlCreateOrUpdateTriggerInput2 =
        QLCreateOrUpdateTriggerInput.builder().applicationId(APP_ID).condition(qlTriggerConditionInput).build();
    when(applicationManifestService.getById(APP_ID, MANIFEST_ID)).thenReturn(null);

    assertThatThrownBy(() -> triggerConditionController.resolveTriggerCondition(qlCreateOrUpdateTriggerInput2))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Application manifest with id MANIFEST_ID not found in given application " + APP_ID);
  }

  private QLCreateOrUpdateTriggerInput getQLCreateOrUpdateTriggerInput(
      QLBitbucketEvent qlBitbucketEvent, QLWebhookSource qlWebhookSource, String branchRegex) {
    QLWebhookConditionInput webhookConditionInput = QLWebhookConditionInput.builder()
                                                        .webhookSourceType(qlWebhookSource)
                                                        .bitbucketEvent(qlBitbucketEvent)
                                                        .branchRegex(branchRegex)
                                                        .build();

    QLTriggerConditionInput qlTriggerConditionInput = QLTriggerConditionInput.builder()
                                                          .conditionType(QLConditionType.ON_WEBHOOK)
                                                          .webhookConditionInput(webhookConditionInput)
                                                          .build();

    return QLCreateOrUpdateTriggerInput.builder().condition(qlTriggerConditionInput).build();
  }
}
