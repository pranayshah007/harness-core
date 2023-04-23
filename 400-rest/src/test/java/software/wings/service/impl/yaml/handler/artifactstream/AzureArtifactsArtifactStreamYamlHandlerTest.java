/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.artifactstream;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.git.model.ChangeType.MODIFY;
import static io.harness.rule.OwnerRule.GARVIT;

import static software.wings.beans.artifact.ArtifactStreamType.AZURE_ARTIFACTS;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStreamProtocolType;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class AzureArtifactsArtifactStreamYamlHandlerTest extends WingsBaseTest {
  private static final String PROJECT = "PROJECT";
  private static final String FEED = "FEED";
  private static final String PACKAGE_ID = "PACKAGE_ID";
  private static final String PACKAGE_NAME = "GROUP_ID:ARTIFACT_ID";

  @InjectMocks @Inject private AzureArtifactsArtifactStreamYamlHandler yamlHandler;
  @Mock private SettingsService settingsService;
  @Mock private AppService appService;
  @Mock protected YamlHelper yamlHelper;
  @Mock protected ArtifactStreamService artifactStreamService;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testToYaml() {
    AzureArtifactsArtifactStream azureArtifactsArtifactStream =
        AzureArtifactsArtifactStream.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .settingId(SETTING_ID)
            .protocolType(AzureArtifactsArtifactStreamProtocolType.maven.name())
            .project(PROJECT)
            .feed(FEED)
            .packageId(PACKAGE_ID)
            .packageName(PACKAGE_NAME)
            .autoPopulate(true)
            .serviceId(SERVICE_ID)
            .build();

    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);

    AzureArtifactsArtifactStream.Yaml yaml = yamlHandler.toYaml(azureArtifactsArtifactStream, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getPackageType()).isEqualTo(AzureArtifactsArtifactStreamProtocolType.maven.name());
    assertThat(yaml.getProject()).isEqualTo(PROJECT);
    assertThat(yaml.getFeed()).isEqualTo(FEED);
    assertThat(yaml.getPackageId()).isEqualTo(PACKAGE_ID);
    assertThat(yaml.getPackageName()).isEqualTo(PACKAGE_NAME);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFromYaml() throws Exception {
    String appName = "Azure Artifact App";
    String serviceName = "Azure Artifact Service";
    String artifactStreamName = "Azure Artifact Artifact Stream";
    String settingName = "Azure Artifacts Setting";
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, settingName)).thenReturn(settingAttribute);
    AzureArtifactsArtifactStream.Yaml baseYaml = AzureArtifactsArtifactStream.Yaml.builder()
                                                     .packageType(AzureArtifactsArtifactStreamProtocolType.maven.name())
                                                     .project(PROJECT)
                                                     .feed(FEED)
                                                     .packageId(PACKAGE_ID)
                                                     .packageName(PACKAGE_NAME)
                                                     .serverName(settingName)
                                                     .harnessApiVersion("1.0")
                                                     .build();
    ChangeContext changeContext =
        ChangeContext.Builder.aChangeContext()
            .withYamlType(YamlType.ARTIFACT_STREAM)
            .withYaml(baseYaml)
            .withChange(GitFileChange.Builder.aGitFileChange()
                            .withFilePath(format("Setup/Applications/%s/Services/%s/Artifact Servers/%s.yaml", appName,
                                serviceName, artifactStreamName))
                            .withFileContent(format("harnessApiVersion: '1.0'\n"
                                    + "type: AZURE_ARTIFACTS_PAT\n"
                                    + "protocolType: %s\n"
                                    + "project: %s\n"
                                    + "feed: %s\n"
                                    + "packageId: %s\n"
                                    + "packageName: %s\n"
                                    + "serverName: %s",
                                AzureArtifactsArtifactStreamProtocolType.maven.name(), PROJECT, FEED, PACKAGE_ID,
                                PACKAGE_NAME, settingName))
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
    final ArgumentCaptor<AzureArtifactsArtifactStream> captor =
        ArgumentCaptor.forClass(AzureArtifactsArtifactStream.class);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    verify(artifactStreamService).createWithBinding(anyString(), captor.capture(), anyBoolean());
    final AzureArtifactsArtifactStream artifactStream = captor.getValue();
    assertThat(artifactStream).isNotNull();
    assertThat(artifactStream.getProtocolType()).isEqualTo(AzureArtifactsArtifactStreamProtocolType.maven.name());
    assertThat(artifactStream.getArtifactStreamType()).isEqualTo(AZURE_ARTIFACTS.name());
    assertThat(artifactStream.getProject()).isEqualTo(PROJECT);
    assertThat(artifactStream.getFeed()).isEqualTo(FEED);
    assertThat(artifactStream.getPackageId()).isEqualTo(PACKAGE_ID);
    assertThat(artifactStream.getPackageName()).isEqualTo(PACKAGE_NAME);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetYamlClass() {
    assertThat(yamlHandler.getYamlClass()).isEqualTo(AzureArtifactsArtifactStream.Yaml.class);
  }
}
