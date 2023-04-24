/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.artifactstream;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO.ImageType.IMAGE_GALLERY;
import static io.harness.git.model.ChangeType.MODIFY;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static software.wings.beans.artifact.ArtifactStreamType.AZURE_MACHINE_IMAGE;
import static software.wings.beans.artifact.AzureMachineImageArtifactStream.OSType.LINUX;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.AzureMachineImageArtifactStream;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class AzureMachineImageArtifactStreamYamlHandlerTest extends YamlHandlerTestBase {
  @InjectMocks @Inject private AzureMachineImageArtifactStreamYamlHandler yamlHandler;
  @Mock AppService appService;
  @Mock ArtifactStreamService artifactStreamService;
  @Mock SettingsService settingsService;
  @Mock private YamlHelper yamlHelper;

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void toYaml() {
    AzureMachineImageArtifactStream artifactStream =
        AzureMachineImageArtifactStream.builder()
            .settingId(SETTING_ID)
            .subscriptionId("subId")
            .osType(LINUX)
            .imageType(IMAGE_GALLERY)
            .imageDefinition(AzureMachineImageArtifactStream.ImageDefinition.builder()
                                 .resourceGroup("devResourceGroup")
                                 .imageGalleryName("devImageGallery")
                                 .imageDefinitionName("hello-world")
                                 .build())
            .build();
    when(settingsService.get(eq(SETTING_ID)))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).build());
    AzureMachineImageArtifactStream.Yml yaml = yamlHandler.toYaml(artifactStream, APP_ID);
    assertThat(yaml.getImageType()).isEqualTo(IMAGE_GALLERY);
    assertThat(yaml.getSubscriptionId()).isEqualTo("subId");
    assertThat(yaml.getImageDefinition()).isNotNull();
    assertThat(yaml.getImageDefinition().getResourceGroup()).isEqualTo("devResourceGroup");
    assertThat(yaml.getImageDefinition().getImageGalleryName()).isEqualTo("devImageGallery");
    assertThat(yaml.getImageDefinition().getImageDefinitionName()).isEqualTo("hello-world");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void upsertFromYaml() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, "test server")).thenReturn(settingAttribute);
    AzureMachineImageArtifactStream.Yaml baseYaml =
        AzureMachineImageArtifactStream.Yml.builder()
            .subscriptionId("subId")
            .imageType(IMAGE_GALLERY)
            .imageDefinition(AzureMachineImageArtifactStream.ImageDefinition.builder()
                                 .resourceGroup("devResourceGroup")
                                 .imageGalleryName("devImageGallery")
                                 .imageDefinitionName("hello-world")
                                 .build())
            .harnessApiVersion("1.0")
            .serverName("test server")
            .build();
    ChangeContext changeContext = ChangeContext.Builder.aChangeContext()
                                      .withYamlType(YamlType.ARTIFACT_STREAM)
                                      .withYaml(baseYaml)
                                      .withChange(GitFileChange.Builder.aGitFileChange()
                                                      .withFilePath("Setup/Applications/a1/Services/s1/as1/test.yaml")
                                                      .withFileContent("harnessApiVersion: '1.0'\n"
                                                          + "type: AZURE_MACHINE_IMAGE\n"
                                                          + "imageType: IMAGE_GALLERY\n"
                                                          + "imageDefinition:\n"
                                                          + "  resourceGroup: devResourceGroup\n"
                                                          + "  imageGalleryName: devImageGallery\n"
                                                          + "  imageDefinitionName: hello-world\n"
                                                          + "serverName: test server")
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
    final ArgumentCaptor<AzureMachineImageArtifactStream> captor =
        ArgumentCaptor.forClass(AzureMachineImageArtifactStream.class);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    verify(artifactStreamService).createWithBinding(anyString(), captor.capture(), anyBoolean());
    final AzureMachineImageArtifactStream artifactStream = captor.getValue();
    assertThat(artifactStream).isNotNull();
    assertThat(artifactStream.getImageType()).isEqualTo(IMAGE_GALLERY);
    assertThat(artifactStream.getSubscriptionId()).isEqualTo("subId");
    assertThat(artifactStream.getImageDefinition()).isNotNull();
    assertThat(artifactStream.getImageDefinition().getResourceGroup()).isEqualTo("devResourceGroup");
    assertThat(artifactStream.getImageDefinition().getImageGalleryName()).isEqualTo("devImageGallery");
    assertThat(artifactStream.getImageDefinition().getImageDefinitionName()).isEqualTo("hello-world");
    assertThat(artifactStream.getArtifactStreamType()).isEqualTo(AZURE_MACHINE_IMAGE.name());
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testGetYamlClass() {
    assertThat(yamlHandler.getYamlClass()).isEqualTo(AzureMachineImageArtifactStream.Yml.class);
  }
}
