/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.artifactstream;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.git.model.ChangeType.MODIFY;
import static io.harness.rule.OwnerRule.AADITI;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static software.wings.beans.artifact.ArtifactStreamType.NEXUS;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;
import software.wings.utils.RepositoryFormat;
import software.wings.utils.RepositoryType;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class NexusArtifactStreamYamlHandlerTest extends YamlHandlerTestBase {
  @InjectMocks @Inject private NexusArtifactStreamYamlHandler yamlHandler;
  @Mock AppService appService;
  @Mock ArtifactStreamService artifactStreamService;
  @Mock SettingsService settingsService;
  @Mock private YamlHelper yamlHelper;
  @Mock private FeatureFlagService featureFlagService;

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void upsertFromYamlWithoutRepositoryName() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, "nexus 2 dev")).thenReturn(settingAttribute);
    NexusArtifactStream.Yaml baseYaml = NexusArtifactStream.Yaml.builder()
                                            .repositoryFormat(RepositoryFormat.maven.name())
                                            .groupId("mygroup")
                                            .artifactPaths(asList("todolist"))
                                            .harnessApiVersion("1.0")
                                            .serverName("nexus 2 dev")
                                            .build();
    ChangeContext changeContext = ChangeContext.Builder.aChangeContext()
                                      .withYamlType(YamlType.ARTIFACT_STREAM)
                                      .withYaml(baseYaml)
                                      .withChange(GitFileChange.Builder.aGitFileChange()
                                                      .withFilePath("Setup/Applications/a1/Services/s1/as1/test.yaml")
                                                      .withFileContent("harnessApiVersion: '1.0'\n"
                                                          + "type: NEXUS\n"
                                                          + "artifactPaths:\n"
                                                          + "- todolist\n"
                                                          + "groupId: mygroup\n"
                                                          + "metadataOnly: true\n"
                                                          + "repositoryFormat: maven\n"
                                                          + "repositoryName: \n"
                                                          + "serverName: nexus 2 dev")
                                                      .withAccountId(ACCOUNT_ID)
                                                      .withChangeType(MODIFY)
                                                      .build())
                                      .build();
    Application application = Application.Builder.anApplication().name("a1").uuid(APP_ID).accountId(ACCOUNT_ID).build();
    when(appService.getAppByName(ACCOUNT_ID, "a1")).thenReturn(application);
    when(appService.get(APP_ID)).thenReturn(application);
    when(yamlHelper.getAppId(
             changeContext.getChange().getAccountId(), "Setup/Applications/a1/Services/s1/as1/test.yaml"))
        .thenReturn(APP_ID);
    when(yamlHelper.getServiceId(APP_ID, "Setup/Applications/a1/Services/s1/as1/test.yaml")).thenReturn(SERVICE_ID);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void upsertFromYamlWithoutRepositoryFormat() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, "nexus 2 dev")).thenReturn(settingAttribute);
    NexusArtifactStream.Yaml baseYaml = NexusArtifactStream.Yaml.builder()
                                            .repositoryName("releases")
                                            .groupId("mygroup")
                                            .artifactPaths(asList("todolist"))
                                            .harnessApiVersion("1.0")
                                            .serverName("nexus 2 dev")
                                            .build();
    ChangeContext changeContext = ChangeContext.Builder.aChangeContext()
                                      .withYamlType(YamlType.ARTIFACT_STREAM)
                                      .withYaml(baseYaml)
                                      .withChange(GitFileChange.Builder.aGitFileChange()
                                                      .withFilePath("Setup/Applications/a1/Services/s1/as1/test.yaml")
                                                      .withFileContent("harnessApiVersion: '1.0'\n"
                                                          + "type: NEXUS\n"
                                                          + "artifactPaths:\n"
                                                          + "- todolist\n"
                                                          + "groupId: mygroup\n"
                                                          + "metadataOnly: true\n"
                                                          + "repositoryName: releases\n"
                                                          + "serverName: nexus 2 dev")
                                                      .withAccountId(ACCOUNT_ID)
                                                      .withChangeType(MODIFY)
                                                      .build())
                                      .build();
    Application application = Application.Builder.anApplication().name("a1").uuid(APP_ID).accountId(ACCOUNT_ID).build();
    when(appService.getAppByName(ACCOUNT_ID, "a1")).thenReturn(application);
    when(appService.get(APP_ID)).thenReturn(application);
    when(yamlHelper.getAppId(
             changeContext.getChange().getAccountId(), "Setup/Applications/a1/Services/s1/as1/test.yaml"))
        .thenReturn(APP_ID);
    when(yamlHelper.getServiceId(APP_ID, "Setup/Applications/a1/Services/s1/as1/test.yaml")).thenReturn(SERVICE_ID);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testParameterizedArtifactStreamFromYaml() {
    String appName = "Nexus App";
    String serviceName = "Nexus Service";
    String artifactStreamName = "Nexus Artifact Stream";
    String settingName = "Nexus Artifacts Setting";
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, settingName)).thenReturn(settingAttribute);
    NexusArtifactStream.Yaml baseYaml = NexusArtifactStream.Yaml.builder()
                                            .repositoryFormat(RepositoryFormat.maven.name())
                                            .repositoryName("${repo}")
                                            .groupId("${groupId}")
                                            .artifactPaths(asList("${artifactId}"))
                                            .harnessApiVersion("1.0")
                                            .serverName(settingName)
                                            .metadataOnly(true)
                                            .build();
    ChangeContext changeContext =
        ChangeContext.Builder.aChangeContext()
            .withYamlType(YamlType.ARTIFACT_STREAM)
            .withYaml(baseYaml)
            .withChange(GitFileChange.Builder.aGitFileChange()
                            .withFilePath(format("Setup/Applications/%s/Services/%s/Artifact Servers/%s.yaml", appName,
                                serviceName, artifactStreamName))
                            .withFileContent(format("harnessApiVersion: '1.0'\n"
                                    + "type: NEXUS\n"
                                    + "artifactPaths:\n"
                                    + "- ${artifactId}\n"
                                    + "groupId: ${groupId}\n"
                                    + "metadataOnly: true\n"
                                    + "repositoryFormat: maven\n"
                                    + "repositoryName: ${repo}\n"
                                    + "serverName: %s",
                                settingName))
                            .withAccountId(ACCOUNT_ID)
                            .withChangeType(MODIFY)
                            .build())
            .build();
    Application application =
        Application.Builder.anApplication().name(appName).uuid(APP_ID).accountId(ACCOUNT_ID).build();
    when(appService.getAppByName(ACCOUNT_ID, appName)).thenReturn(application);
    when(appService.get(APP_ID)).thenReturn(application);
    when(yamlHelper.getAppId(changeContext.getChange().getAccountId(),
             format("Setup/Applications/%s/Services/%s/Artifact Servers/%s.yaml", appName, serviceName,
                 artifactStreamName)))
        .thenReturn(APP_ID);
    when(yamlHelper.getServiceId(APP_ID,
             format("Setup/Applications/%s/Services/%s/Artifact Servers/%s.yaml", appName, serviceName,
                 artifactStreamName)))
        .thenReturn(SERVICE_ID);
    final ArgumentCaptor<NexusArtifactStream> captor = ArgumentCaptor.forClass(NexusArtifactStream.class);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    verify(artifactStreamService).createWithBinding(anyString(), captor.capture(), anyBoolean());
    final NexusArtifactStream artifactStream = captor.getValue();
    assertThat(artifactStream).isNotNull();
    assertThat(artifactStream.getRepositoryFormat()).isEqualTo(RepositoryFormat.maven.name());
    assertThat(artifactStream.getArtifactStreamType()).isEqualTo(NEXUS.name());
    assertThat(artifactStream.getJobname()).isEqualTo("${repo}");
    assertThat(artifactStream.getGroupId()).isEqualTo("${groupId}");
    assertThat(artifactStream.getArtifactPaths().get(0)).isEqualTo("${artifactId}");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void toYaml() {
    NexusArtifactStream acrArtifactStream = NexusArtifactStream.builder()
                                                .settingId(SETTING_ID)
                                                .repositoryFormat(RepositoryType.maven.name())
                                                .jobname("harness")
                                                .groupId("com.harness.tgz")
                                                .artifactPaths(Lists.newArrayList("path", "another/path"))
                                                .classifier("classifier")
                                                .extension("rpm")
                                                .build();
    when(settingsService.get(eq(SETTING_ID)))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).build());
    NexusArtifactStream.Yaml yaml = yamlHandler.toYaml(acrArtifactStream, APP_ID);
    assertThat(yaml.getRepositoryFormat()).isEqualTo(RepositoryType.maven.name());
    assertThat(yaml.getRepositoryName()).isEqualTo("harness");
    assertThat(yaml.getGroupId()).isEqualTo("com.harness.tgz");
    assertThat(yaml.getArtifactPaths()).isNotEmpty();
    assertThat(yaml.getArtifactPaths()).hasSize(2);
    assertThat(yaml.getArtifactPaths()).isEqualTo(Lists.newArrayList("path", "another/path"));
    assertThat(yaml.getClassifier()).isEqualTo("classifier");
    assertThat(yaml.getExtension()).isEqualTo("rpm");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetYamlClass() {
    assertThat(yamlHandler.getYamlClass()).isEqualTo(NexusArtifactStream.Yaml.class);
  }
}
