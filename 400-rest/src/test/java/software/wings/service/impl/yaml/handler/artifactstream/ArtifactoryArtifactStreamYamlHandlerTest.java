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
import static io.harness.rule.OwnerRule.INDER;

import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class ArtifactoryArtifactStreamYamlHandlerTest extends WingsBaseTest {
  @InjectMocks @Inject private ArtifactoryArtifactStreamYamlHandler yamlHandler;
  @Mock private SettingsService settingsService;
  @Mock private AppService appService;
  @Mock protected YamlHelper yamlHelper;
  @Mock protected ArtifactStreamService artifactStreamService;

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testToYamlArtifactoryDocker() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .repositoryType("docker")
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .imageName("busybox")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    ArtifactoryArtifactStream.Yaml yaml = yamlHandler.toYaml(artifactoryArtifactStream, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getRepositoryName()).isEqualTo("generic-repo");
    assertThat(yaml.getImageName()).isEqualTo("busybox");
    assertThat(yaml.getRepositoryType()).isEqualTo("docker");
    assertThat(yaml.getImageName()).isEqualTo("busybox");
    assertThat(yaml.isMetadataOnly()).isTrue();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testToYamlArtifactoryAny() {
    ArtifactoryArtifactStream artifactoryArtifactStream = ArtifactoryArtifactStream.builder()
                                                              .accountId(ACCOUNT_ID)
                                                              .appId(APP_ID)
                                                              .repositoryType("any")
                                                              .settingId(SETTING_ID)
                                                              .jobname("generic-repo")
                                                              .artifactPattern("io/harness/todolist/todolist*")
                                                              .autoPopulate(true)
                                                              .serviceId(SERVICE_ID)
                                                              .build();
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    ArtifactoryArtifactStream.Yaml yaml = yamlHandler.toYaml(artifactoryArtifactStream, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getRepositoryName()).isEqualTo("generic-repo");
    assertThat(yaml.getArtifactPattern()).isEqualTo("io/harness/todolist/todolist*");
    assertThat(yaml.getRepositoryType()).isEqualTo("any");
    assertThat(yaml.isMetadataOnly()).isFalse();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFromYamlArtifactoryDocker() throws Exception {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, "Harness Artifactory")).thenReturn(settingAttribute);
    ArtifactoryArtifactStream.Yaml baseYaml = ArtifactoryArtifactStream.Yaml.builder()
                                                  .repositoryName("conn-onprem")
                                                  .imageName("busybox")
                                                  .repositoryType("docker")
                                                  .serverName("Harness Artifactory")
                                                  .harnessApiVersion("1.0")
                                                  .build();
    ChangeContext changeContext =
        ChangeContext.Builder.aChangeContext()
            .withYamlType(YamlType.ARTIFACT_STREAM)
            .withYaml(baseYaml)
            .withChange(
                GitFileChange.Builder.aGitFileChange()
                    .withFilePath(
                        "Setup/Applications/art docker test/Services/s1/Artifact Servers/conn-onprem_busybox.yaml")
                    .withFileContent("harnessApiVersion: '1.0'\n"
                        + "type: ARTIFACTORY\n"
                        + "imageName: busybox\n"
                        + "metadataOnly: true\n"
                        + "repositoryName: conn-onprem\n"
                        + "repositoryType: docker\n"
                        + "serverName: Harness Artifactory")
                    .withAccountId(ACCOUNT_ID)
                    .withChangeType(MODIFY)
                    .build())
            .build();
    Application application =
        Application.Builder.anApplication().name("art docker test").uuid(APP_ID).accountId(ACCOUNT_ID).build();
    when(appService.getAppByName(ACCOUNT_ID, "art docker test")).thenReturn(application);
    when(appService.get(APP_ID)).thenReturn(application);
    when(yamlHelper.getAppId(changeContext.getChange().getAccountId(),
             "Setup/Applications/art docker test/Services/s1/Artifact Servers/conn-onprem_busybox.yaml"))
        .thenReturn(APP_ID);
    when(yamlHelper.getServiceId(
             APP_ID, "Setup/Applications/art docker test/Services/s1/Artifact Servers/conn-onprem_busybox.yaml"))
        .thenReturn(SERVICE_ID);
    final ArgumentCaptor<ArtifactoryArtifactStream> captor = ArgumentCaptor.forClass(ArtifactoryArtifactStream.class);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    verify(artifactStreamService).createWithBinding(anyString(), captor.capture(), anyBoolean());
    final ArtifactoryArtifactStream artifactStream = captor.getValue();
    assertThat(artifactStream).isNotNull();
    assertThat(artifactStream.getJobname()).isEqualTo("conn-onprem");
    assertThat(artifactStream.getImageName()).isEqualTo("busybox");
    assertThat(artifactStream.getRepositoryType()).isEqualTo("docker");
    assertThat(artifactStream.getArtifactStreamType()).isEqualTo(ARTIFACTORY.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testFromYamlArtifactoryAny() throws Exception {
    mocksSetup();
    ChangeContext changeContext = getChangeContext();

    when(yamlHelper.getAppId(changeContext.getChange().getAccountId(),
             "Setup/Applications/copy artifact app/Services/s1/Artifact Servers/test-app.yaml"))
        .thenReturn(APP_ID);
    when(yamlHelper.getServiceId(
             APP_ID, "Setup/Applications/copy artifact app/Services/s1/Artifact Servers/test-app.yaml"))
        .thenReturn(SERVICE_ID);
    final ArgumentCaptor<ArtifactoryArtifactStream> captor = ArgumentCaptor.forClass(ArtifactoryArtifactStream.class);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    verify(artifactStreamService).createWithBinding(anyString(), captor.capture(), anyBoolean());
    final ArtifactoryArtifactStream artifactStream = captor.getValue();
    assertThat(artifactStream).isNotNull();
    assertThat(artifactStream.getJobname()).isEqualTo("harness-maven");
    assertThat(artifactStream.getArtifactPattern()).isEqualTo("todolist-*.war");
    assertThat(artifactStream.getRepositoryType()).isEqualTo("any");
    assertThat(artifactStream.getArtifactStreamType()).isEqualTo(ARTIFACTORY.name());
  }

  private void mocksSetup() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, "Harness Artifactory")).thenReturn(settingAttribute);

    Application application =
        Application.Builder.anApplication().name("copy artifact app").uuid(APP_ID).accountId(ACCOUNT_ID).build();
    when(appService.getAppByName(ACCOUNT_ID, "copy artifact app")).thenReturn(application);
    when(appService.get(APP_ID)).thenReturn(application);

    when(yamlHelper.getApplicationIfPresent(anyString(), anyString())).thenReturn(Optional.of(application));
    when(yamlHelper.getServiceIfPresent(anyString(), anyString()))
        .thenReturn(Optional.of(Service.builder().uuid(SERVICE_ID).build()));
  }

  private ChangeContext getChangeContext() {
    ArtifactoryArtifactStream.Yaml baseYaml = ArtifactoryArtifactStream.Yaml.builder()
                                                  .repositoryName("harness-maven")
                                                  .artifactPattern("todolist-*.war")
                                                  .repositoryType("any")
                                                  .serverName("Harness Artifactory")
                                                  .harnessApiVersion("1.0")
                                                  .build();
    return ChangeContext.Builder.aChangeContext()
        .withYamlType(YamlType.ARTIFACT_STREAM)
        .withYaml(baseYaml)
        .withChange(GitFileChange.Builder.aGitFileChange()
                        .withFilePath("Setup/Applications/copy artifact app/Services/s1/Artifact Servers/test-app.yaml")
                        .withFileContent("harnessApiVersion: '1.0'\n"
                            + "type: ARTIFACTORY\n"
                            + "artifactPattern: todolist-*.war\n"
                            + "metadataOnly: true\n"
                            + "repositoryName: harness-maven\n"
                            + "repositoryType: any\n"
                            + "serverName: Harness Artifactory\n")
                        .withAccountId(ACCOUNT_ID)
                        .withChangeType(MODIFY)
                        .build())
        .build();
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetYamlClass() {
    assertThat(yamlHandler.getYamlClass()).isEqualTo(ArtifactoryArtifactStream.Yaml.class);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testGitSyncFlagOnCRUDFromYaml() {
    mocksSetup();
    ChangeContext changeContext = getChangeContext();
    changeContext.getChange().setSyncFromGit(true);

    when(yamlHelper.getAppId(changeContext.getChange().getAccountId(),
             "Setup/Applications/copy artifact app/Services/s1/Artifact Servers/test-app.yaml"))
        .thenReturn(APP_ID);
    when(yamlHelper.getServiceId(
             APP_ID, "Setup/Applications/copy artifact app/Services/s1/Artifact Servers/test-app.yaml"))
        .thenReturn(SERVICE_ID);
    final ArgumentCaptor<ArtifactoryArtifactStream> captor = ArgumentCaptor.forClass(ArtifactoryArtifactStream.class);

    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    verify(artifactStreamService).createWithBinding(anyString(), captor.capture(), anyBoolean());
    final ArtifactoryArtifactStream artifactStream = captor.getValue();
    assertThat(artifactStream).isNotNull();
    assertThat(artifactStream.isSyncFromGit()).isTrue();

    when(yamlHelper.getArtifactStream(eq(APP_ID), eq(SERVICE_ID), anyString())).thenReturn(artifactStream);
    yamlHandler.delete(changeContext);
    verify(artifactStreamService).deleteWithBinding(APP_ID, null, false, true);
  }
}
