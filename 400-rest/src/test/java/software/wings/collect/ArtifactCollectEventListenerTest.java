/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.collect;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.TaskType.AZURE_ARTIFACTS_COLLECTION;
import static software.wings.beans.TaskType.BAMBOO_COLLECTION;
import static software.wings.beans.TaskType.JENKINS_COLLECTION;
import static software.wings.beans.artifact.Artifact.Builder.anArtifact;
import static software.wings.beans.artifact.Artifact.ContentStatus.DOWNLOADING;
import static software.wings.beans.artifact.Artifact.Status.APPROVED;
import static software.wings.beans.artifact.Artifact.Status.RUNNING;
import static software.wings.collect.CollectEvent.Builder.aCollectEvent;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_PATH;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_NAME;
import static software.wings.utils.WingsTestConstants.AZURE_DEVOPS_URL;
import static software.wings.utils.WingsTestConstants.JENKINS_URL;
import static software.wings.utils.WingsTestConstants.JOB_NAME;
import static software.wings.utils.WingsTestConstants.PASSWORD;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.USER_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.BambooConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;
import software.wings.beans.artifact.BambooArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.command.JenkinsTaskParams;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class ArtifactCollectEventListenerTest extends WingsBaseTest {
  private final Application APP = anApplication().uuid(APP_ID).accountId(ACCOUNT_ID).build();

  @InjectMocks @Inject private ArtifactCollectEventListener artifactCollectEventListener;

  @Mock private AppService appService;

  @Mock private ArtifactService artifactService;

  @Mock private DelegateService delegateService;

  @Mock private SettingsService settingsService;

  @Mock private ArtifactStreamService artifactStreamService;

  @Before
  public void setupMocks() {
    when(appService.get(APP_ID)).thenReturn(APP);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSendJenkinsTask() {
    SettingAttribute SETTING_ATTRIBUTE = aSettingAttribute()
                                             .withValue(JenkinsConfig.builder()
                                                            .jenkinsUrl(JENKINS_URL)
                                                            .username(USER_NAME)
                                                            .password(PASSWORD)
                                                            .accountId(ACCOUNT_ID)
                                                            .build())
                                             .build();
    JenkinsTaskParams jenkinsTaskParams = JenkinsTaskParams.builder()
                                              .jenkinsConfig((JenkinsConfig) SETTING_ATTRIBUTE.getValue())
                                              .jobName(JOB_NAME)
                                              .artifactPaths(Collections.singletonList(ARTIFACT_PATH))
                                              .metaData(Collections.emptyMap())
                                              .build();
    when(settingsService.get(SETTING_ID)).thenReturn(SETTING_ATTRIBUTE);

    sendTaskHelper(JenkinsArtifactStream.builder()
                       .sourceName(ARTIFACT_STREAM_NAME)
                       .appId(APP_ID)
                       .settingId(SETTING_ID)
                       .jobname(JOB_NAME)
                       .artifactPaths(Collections.singletonList(ARTIFACT_PATH))
                       .build());

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(delegateTaskArgumentCaptor.capture());
    DelegateTask delegateTask = delegateTaskArgumentCaptor.getValue();
    assertThat(delegateTask).isNotNull().hasFieldOrPropertyWithValue("data.taskType", JENKINS_COLLECTION.name());
    assertThat(delegateTask.getData().getParameters()[0])
        .isEqualToIgnoringGivenFields(jenkinsTaskParams, "encryptedDataDetails");
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldSendBambooTask() {
    SettingAttribute SETTING_ATTRIBUTE =
        aSettingAttribute()
            .withValue(BambooConfig.builder().bambooUrl(JENKINS_URL).username(USER_NAME).password(PASSWORD).build())
            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(SETTING_ATTRIBUTE);

    sendTaskHelper(BambooArtifactStream.builder()
                       .sourceName(ARTIFACT_STREAM_NAME)
                       .appId(APP_ID)
                       .settingId(SETTING_ID)
                       .jobname(JOB_NAME)
                       .artifactPaths(Collections.singletonList(ARTIFACT_PATH))
                       .build());

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue())
        .isNotNull()
        .hasFieldOrPropertyWithValue("data.taskType", BAMBOO_COLLECTION.name());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldSendAzureArtifactsTask() {
    SettingAttribute SETTING_ATTRIBUTE =
        aSettingAttribute()
            .withValue(AzureArtifactsPATConfig.builder().azureDevopsUrl(AZURE_DEVOPS_URL).pat(PASSWORD).build())
            .build();
    when(settingsService.get(SETTING_ID)).thenReturn(SETTING_ATTRIBUTE);

    sendTaskHelper(AzureArtifactsArtifactStream.builder()
                       .sourceName(ARTIFACT_STREAM_NAME)
                       .appId(APP_ID)
                       .settingId(SETTING_ID)
                       .protocolType(AzureArtifactsArtifactStreamProtocolType.maven.name())
                       .project(null)
                       .feed("FEED")
                       .packageId("PKG_ID")
                       .packageName("GROUP_ID:ARTIFACT_ID")
                       .build());

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTask(delegateTaskArgumentCaptor.capture());
    assertThat(delegateTaskArgumentCaptor.getValue())
        .isNotNull()
        .hasFieldOrPropertyWithValue("data.taskType", AZURE_ARTIFACTS_COLLECTION.name());
  }

  private void sendTaskHelper(ArtifactStream artifactStream) {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(artifactStream);

    artifactCollectEventListener.onMessage(aCollectEvent()
                                               .withArtifact(anArtifact()
                                                                 .withUuid(ARTIFACT_ID)
                                                                 .withAccountId(ACCOUNT_ID)
                                                                 .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                                                 .withArtifactStreamType("DOCKER")
                                                                 .withAppId(APP_ID)
                                                                 .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                                                 .build())
                                               .build());

    verify(artifactService).updateStatus(ARTIFACT_ID, ACCOUNT_ID, RUNNING, DOWNLOADING);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFailToCollectArtifactWhenSourceIsMissing() throws Exception {
    artifactCollectEventListener.onMessage(aCollectEvent()
                                               .withArtifact(anArtifact()
                                                                 .withUuid(ARTIFACT_ID)
                                                                 .withArtifactStreamId(ARTIFACT_STREAM_ID)
                                                                 .withArtifactStreamType("DOCKER")
                                                                 .withAccountId(ACCOUNT_ID)
                                                                 .withAppId(APP_ID)
                                                                 .build())
                                               .build());

    verify(artifactService).updateStatus(ARTIFACT_ID, ACCOUNT_ID, RUNNING, DOWNLOADING);
    verify(artifactService).updateStatus(ARTIFACT_ID, ACCOUNT_ID, APPROVED, Artifact.ContentStatus.FAILED);
  }
}
