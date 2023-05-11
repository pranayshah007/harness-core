/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.beans.BambooConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.settings.azureartifacts.AzureArtifactsPATConfig;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDC)
public class ArtifactStreamSettingAttributePTaskManagerTest extends CategoryTest {
  private static final String PERPETUAL_TASK_ID = "PERPETUAL_TASK_ID";

  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private PerpetualTaskService perpetualTaskService;

  @Inject @InjectMocks ArtifactStreamSettingAttributePTaskManager manager;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    enableFeatureFlag();
  }

  @Test(expected = Test.None.class)
  @Owner(developers = OwnerRule.GARVIT)
  @Category(UnitTests.class)
  public void testOnSaved() {
    manager.onSaved(prepareSettingAttribute());
  }

  @Test
  @Owner(developers = OwnerRule.GARVIT)
  @Category(UnitTests.class)
  public void testOnUpdated() {
    SettingAttribute settingAttribute = prepareSettingAttribute();
    ArtifactStream artifactStream = prepareArtifactStream();
    artifactStream.setPerpetualTaskId(PERPETUAL_TASK_ID);
    when(artifactStreamService.listAllBySettingId(SETTING_ID))
        .thenReturn(asList(artifactStream, prepareArtifactStream()));

    disableFeatureFlag();
    manager.onUpdated(settingAttribute, settingAttribute);
    verify(perpetualTaskService, never()).resetTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID), eq(null));

    enableFeatureFlag();
    manager.onUpdated(settingAttribute, settingAttribute);
    verify(perpetualTaskService, times(1)).resetTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID), eq(null));

    when(artifactStreamService.listAllBySettingId(SETTING_ID)).thenReturn(Collections.emptyList());
    manager.onUpdated(settingAttribute, settingAttribute);
    verify(perpetualTaskService, times(1)).resetTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID), eq(null));
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testOnUpdatedForDocker() {
    SettingAttribute newSetting = prepareSettingAttribute();
    SettingAttribute oldSetting =
        prepareSettingAttribute(DockerConfig.builder().dockerRegistryUrl("http://registry.hub.docker.com/v2/").build());
    ArtifactStream artifactStream = prepareArtifactStream();
    artifactStream.setPerpetualTaskId(PERPETUAL_TASK_ID);
    when(artifactStreamService.listAllBySettingId(SETTING_ID)).thenReturn(Collections.singletonList(artifactStream));
    disableFeatureFlag();

    manager.onUpdated(oldSetting, newSetting);
    manager.onUpdated(newSetting, newSetting);
    verify(perpetualTaskService, never()).resetTask(any(), any(), any());
    verify(artifactStreamService, times(1)).deleteArtifacts(ACCOUNT_ID, artifactStream);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testOnUpdatedForArtifactory() {
    SettingAttribute newSetting =
        prepareSettingAttribute(ArtifactoryConfig.builder().artifactoryUrl("https://harness.jfrog.io").build());
    SettingAttribute oldSetting =
        prepareSettingAttribute(ArtifactoryConfig.builder().artifactoryUrl("http://harness.jfrog.io").build());
    ArtifactStream artifactStream = prepareArtifactStream();
    artifactStream.setPerpetualTaskId(PERPETUAL_TASK_ID);
    when(artifactStreamService.listAllBySettingId(SETTING_ID)).thenReturn(Collections.singletonList(artifactStream));
    disableFeatureFlag();

    manager.onUpdated(oldSetting, newSetting);
    manager.onUpdated(newSetting, newSetting);
    verify(perpetualTaskService, never()).resetTask(any(), any(), any());
    verify(artifactStreamService, times(1)).deleteArtifacts(ACCOUNT_ID, artifactStream);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testOnUpdatedForJenkins() {
    SettingAttribute newSetting =
        prepareSettingAttribute(JenkinsConfig.builder().jenkinsUrl("https://jenkins.harness.io").build());
    SettingAttribute oldSetting =
        prepareSettingAttribute(JenkinsConfig.builder().jenkinsUrl("http://jenkins.dev.harness.io").build());
    ArtifactStream artifactStream = prepareArtifactStream();
    artifactStream.setPerpetualTaskId(PERPETUAL_TASK_ID);
    when(artifactStreamService.listAllBySettingId(SETTING_ID)).thenReturn(Collections.singletonList(artifactStream));
    disableFeatureFlag();

    manager.onUpdated(oldSetting, newSetting);
    manager.onUpdated(newSetting, newSetting);
    verify(perpetualTaskService, never()).resetTask(any(), any(), any());
    verify(artifactStreamService, times(1)).deleteArtifacts(ACCOUNT_ID, artifactStream);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testOnUpdatedForAzureArtifactsConfig() {
    SettingAttribute newSetting =
        prepareSettingAttribute(AzureArtifactsPATConfig.builder().azureDevopsUrl("https://devops.azure.io").build());
    SettingAttribute oldSetting =
        prepareSettingAttribute(AzureArtifactsPATConfig.builder().azureDevopsUrl("http://devops.azure.io").build());
    ArtifactStream artifactStream = prepareArtifactStream();
    artifactStream.setPerpetualTaskId(PERPETUAL_TASK_ID);
    when(artifactStreamService.listAllBySettingId(SETTING_ID)).thenReturn(Collections.singletonList(artifactStream));
    disableFeatureFlag();

    manager.onUpdated(oldSetting, newSetting);
    manager.onUpdated(newSetting, newSetting);
    verify(perpetualTaskService, never()).resetTask(any(), any(), any());
    verify(artifactStreamService, times(1)).deleteArtifacts(ACCOUNT_ID, artifactStream);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testOnUpdatedForBamboo() {
    SettingAttribute newSetting =
        prepareSettingAttribute(BambooConfig.builder().bambooUrl("https://harness.bamboo.io").build());
    SettingAttribute oldSetting =
        prepareSettingAttribute(BambooConfig.builder().bambooUrl("http://harness.bamboo.io").build());
    ArtifactStream artifactStream = prepareArtifactStream();
    artifactStream.setPerpetualTaskId(PERPETUAL_TASK_ID);
    when(artifactStreamService.listAllBySettingId(SETTING_ID)).thenReturn(Collections.singletonList(artifactStream));
    disableFeatureFlag();

    manager.onUpdated(oldSetting, newSetting);
    manager.onUpdated(newSetting, newSetting);
    verify(perpetualTaskService, never()).resetTask(any(), any(), any());
    verify(artifactStreamService, times(1)).deleteArtifacts(ACCOUNT_ID, artifactStream);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testOnUpdatedForNexus() {
    SettingAttribute newSetting =
        prepareSettingAttribute(NexusConfig.builder().version("3.x").nexusUrl("https://nexus").build());
    SettingAttribute oldSettingVersion2 =
        prepareSettingAttribute(NexusConfig.builder().version("2.x").nexusUrl("https://nexus").build());
    SettingAttribute oldSettingDifferentUrl =
        prepareSettingAttribute(NexusConfig.builder().version("3.x").nexusUrl("https://nexus3").build());
    ArtifactStream artifactStream = NexusArtifactStream.builder()
                                        .accountId(ACCOUNT_ID)
                                        .appId(APP_ID)
                                        .uuid(ARTIFACT_STREAM_ID)
                                        .settingId(SETTING_ID)
                                        .autoPopulate(true)
                                        .serviceId(SERVICE_ID)
                                        .build();
    artifactStream.setPerpetualTaskId(PERPETUAL_TASK_ID);
    when(artifactStreamService.listAllBySettingId(SETTING_ID)).thenReturn(Collections.singletonList(artifactStream));
    disableFeatureFlag();

    manager.onUpdated(oldSettingVersion2, newSetting);
    manager.onUpdated(oldSettingDifferentUrl, newSetting);
    manager.onUpdated(newSetting, newSetting);
    verify(perpetualTaskService, never()).resetTask(any(), any(), any());
    verify(artifactStreamService, times(2)).deleteArtifacts(any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.GARVIT)
  @Category(UnitTests.class)
  public void testOnDeleted() {
    manager.onDeleted(prepareSettingAttribute());
  }

  private void enableFeatureFlag() {
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, ACCOUNT_ID)).thenReturn(true);
  }

  private void disableFeatureFlag() {
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, ACCOUNT_ID)).thenReturn(false);
  }

  private static SettingAttribute prepareSettingAttribute(SettingValue value) {
    return aSettingAttribute().withUuid(SETTING_ID).withValue(value).withAccountId(ACCOUNT_ID).build();
  }

  private static SettingAttribute prepareSettingAttribute() {
    return prepareSettingAttribute(
        DockerConfig.builder().dockerRegistryUrl("https://registry.hub.docker.com/v2/").build());
  }

  private ArtifactStream prepareArtifactStream() {
    return DockerArtifactStream.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .uuid(ARTIFACT_STREAM_ID)
        .settingId(SETTING_ID)
        .imageName("wingsplugins/todolist")
        .autoPopulate(true)
        .serviceId(SERVICE_ID)
        .build();
  }
}
