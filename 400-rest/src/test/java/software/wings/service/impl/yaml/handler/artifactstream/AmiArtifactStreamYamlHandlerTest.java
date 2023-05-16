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

import static software.wings.beans.artifact.ArtifactStreamType.AMI;
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
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.NameValuePair;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.AmiArtifactStream;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.SettingsService;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class AmiArtifactStreamYamlHandlerTest extends YamlHandlerTestBase {
  @InjectMocks @Inject private AmiArtifactStreamYamlHandler yamlHandler;
  @Mock AppService appService;
  @Mock ArtifactStreamService artifactStreamService;
  @Mock SettingsService settingsService;
  @Mock private YamlHelper yamlHelper;

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void toYaml() {
    AmiArtifactStream.FilterClass f1 = new AmiArtifactStream.FilterClass();
    f1.setKey("ami-image-id");
    f1.setValue("ami-023385617116e27c0");
    AmiArtifactStream.Tag t1 = new AmiArtifactStream.Tag();
    t1.setKey("image_version");
    t1.setValue("1.0.0");
    AmiArtifactStream amiArtifactStream = AmiArtifactStream.builder()
                                              .region("us-east-1")
                                              .tags(asList(t1))
                                              .filters(asList(f1))
                                              .settingId(SETTING_ID)
                                              .build();
    when(settingsService.get(eq(SETTING_ID)))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).build());
    AmiArtifactStream.Yaml yaml = yamlHandler.toYaml(amiArtifactStream, APP_ID);
    assertThat(yaml.getRegion()).isEqualTo("us-east-1");
    assertThat(yaml.getAmiTags().size()).isEqualTo(1);
    assertThat(yaml.getAmiTags().get(0).getName()).isEqualTo("image_version");
    assertThat(yaml.getAmiTags().get(0).getValue()).isEqualTo("1.0.0");
    assertThat(yaml.getAmiFilters().size()).isEqualTo(1);
    assertThat(yaml.getAmiFilters().get(0).getName()).isEqualTo("ami-image-id");
    assertThat(yaml.getAmiFilters().get(0).getValue()).isEqualTo("ami-023385617116e27c0");
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void upsertFromYaml() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, "test server")).thenReturn(settingAttribute);
    List<NameValuePair.Yaml> amiTags =
        asList(NameValuePair.Yaml.builder().name("image_version").value("1.0.0").build());
    List<NameValuePair.Yaml> amiFilter =
        asList(NameValuePair.Yaml.builder().name("ami-image-id").value("ami-023385617116e27c0").build());
    AmiArtifactStream.Yaml baseYaml = AmiArtifactStream.Yaml.builder()
                                          .region("us-east-1")
                                          .amiTags(amiTags)
                                          .amiFilters(amiFilter)
                                          .harnessApiVersion("1.0")
                                          .serverName("test server")
                                          .build();
    ChangeContext changeContext = ChangeContext.Builder.aChangeContext()
                                      .withYamlType(YamlType.ARTIFACT_STREAM)
                                      .withYaml(baseYaml)
                                      .withChange(GitFileChange.Builder.aGitFileChange()
                                                      .withFilePath("Setup/Applications/a1/Services/s1/as1/test.yaml")
                                                      .withFileContent("harnessApiVersion: '1.0'\n"
                                                          + "type: AMI\n"
                                                          + "amiFilters:\n"
                                                          + "- name: ami-image-id\n"
                                                          + "  value: ami-023385617116e27c0\n"
                                                          + "amiTags:\n"
                                                          + "- name: image_version\n"
                                                          + "  value: 1.0.0\n"
                                                          + "region: us-east-1\n"
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
    final ArgumentCaptor<AmiArtifactStream> captor = ArgumentCaptor.forClass(AmiArtifactStream.class);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    verify(artifactStreamService).createWithBinding(anyString(), captor.capture(), anyBoolean());
    final AmiArtifactStream artifactStream = captor.getValue();
    assertThat(artifactStream).isNotNull();
    assertThat(artifactStream.getRegion()).isEqualTo("us-east-1");
    assertThat(artifactStream.getTags().size()).isEqualTo(1);
    assertThat(artifactStream.getTags().get(0).getKey()).isEqualTo("image_version");
    assertThat(artifactStream.getTags().get(0).getValue()).isEqualTo("1.0.0");
    assertThat(artifactStream.getFilters().size()).isEqualTo(1);
    assertThat(artifactStream.getFilters().get(0).getKey()).isEqualTo("ami-image-id");
    assertThat(artifactStream.getFilters().get(0).getValue()).isEqualTo("ami-023385617116e27c0");
    assertThat(artifactStream.getArtifactStreamType()).isEqualTo(AMI.name());
  }

  @Test
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void testGetYamlClass() {
    assertThat(yamlHandler.getYamlClass()).isEqualTo(AmiArtifactStream.Yaml.class);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = AADITI)
  @Category(UnitTests.class)
  public void upsertFromYamlWithoutRegion() {
    SettingAttribute settingAttribute = SettingAttribute.Builder.aSettingAttribute().withAccountId(ACCOUNT_ID).build();
    when(settingsService.get(SETTING_ID)).thenReturn(settingAttribute);
    when(settingsService.getByName(ACCOUNT_ID, APP_ID, "test server")).thenReturn(settingAttribute);
    List<NameValuePair.Yaml> amiTags =
        asList(NameValuePair.Yaml.builder().name("image_version").value("1.0.0").build());
    List<NameValuePair.Yaml> amiFilter =
        asList(NameValuePair.Yaml.builder().name("ami-image-id").value("ami-023385617116e27c0").build());
    AmiArtifactStream.Yaml baseYaml = AmiArtifactStream.Yaml.builder()
                                          .amiTags(amiTags)
                                          .amiFilters(amiFilter)
                                          .harnessApiVersion("1.0")
                                          .serverName("test server")
                                          .build();
    ChangeContext changeContext = ChangeContext.Builder.aChangeContext()
                                      .withYamlType(YamlType.ARTIFACT_STREAM)
                                      .withYaml(baseYaml)
                                      .withChange(GitFileChange.Builder.aGitFileChange()
                                                      .withFilePath("Setup/Applications/a1/Services/s1/as1/test.yaml")
                                                      .withFileContent("harnessApiVersion: '1.0'\n"
                                                          + "type: AMI\n"
                                                          + "amiFilters:\n"
                                                          + "- name: ami-image-id\n"
                                                          + "  value: ami-023385617116e27c0\n"
                                                          + "amiTags:\n"
                                                          + "- name: image_version\n"
                                                          + "  value: 1.0.0\n"
                                                          + "region: \n"
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
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
  }
}
