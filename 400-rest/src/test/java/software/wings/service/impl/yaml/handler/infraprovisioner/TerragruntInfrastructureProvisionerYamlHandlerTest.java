/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.infraprovisioner;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.KmsConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TerragruntInfrastructureProvisioner;
import software.wings.beans.TerragruntInfrastructureProvisioner.Yaml;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.tag.HarnessTagYamlHelper;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._955_CG_YAML)
public class TerragruntInfrastructureProvisionerYamlHandlerTest extends YamlHandlerTestBase {
  @Mock private YamlHelper mockYamlHelper;
  @Mock private InfrastructureProvisionerService mockInfrastructureProvisionerService;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private SettingsService mockSettingsService;
  @Mock private AppService appService;
  @Mock private HarnessTagYamlHelper harnessTagYamlHelper;
  @Mock private SecretManager secretManager;

  @InjectMocks @Inject private TerragruntInfrastructureProvisionerYamlHandler handler;
  private String validYamlFilePath = "Setup/Applications/APP_NAME/Infrastructure Provisioners/TG_Name.yaml";
  private String validYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: TERRAGRUNT\n"
      + "repoName: REPO_NAME\n"
      + "secretMangerName: SECRET_MANAGER\n"
      + "skipRefreshBeforeApplyingPlan: true\n"
      + "sourceRepoBranch: master\n"
      + "sourceRepoSettingName: TERRAGRUNT_TEST_GIT_REPO\n";

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws IOException {
    ChangeContext<Yaml> changeContext = getChangeContext();
    Yaml yaml = (Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yaml);
    doReturn(APP_ID).when(mockYamlHelper).getAppId(any(), any());
    doReturn(null).when(mockInfrastructureProvisionerService).getByName(any(), any());
    Service service = Service.builder().name("ServiceName").uuid(SERVICE_ID).build();
    doReturn(service).when(mockServiceResourceService).get(any(), any());
    doReturn(service).when(mockServiceResourceService).getServiceByName(any(), any());
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).withName("TERRAGRUNT_TEST_GIT_REPO").build();
    SecretManagerConfig secretManagerConfig = KmsConfig.builder().uuid("KMSID").name("SECRET_MANAGER").build();
    doReturn(settingAttribute).when(mockSettingsService).getSettingAttributeByName(any(), any());
    doReturn(settingAttribute).when(mockSettingsService).get(any(), any());
    doReturn(Application.Builder.anApplication().uuid(APP_ID).build()).when(appService).get(any());
    doReturn(secretManagerConfig).when(secretManager).getSecretManager(any(), any());
    doReturn(secretManagerConfig).when(secretManager).getSecretManagerByName(any(), any());
    handler.upsertFromYaml(changeContext, asList(changeContext));
    ArgumentCaptor<TerragruntInfrastructureProvisioner> captor =
        ArgumentCaptor.forClass(TerragruntInfrastructureProvisioner.class);
    verify(mockInfrastructureProvisionerService).save(captor.capture());
    TerragruntInfrastructureProvisioner provisionerSaved = captor.getValue();
    assertThat(provisionerSaved).isNotNull();
    assertThat("TERRAGRUNT").isEqualTo(provisionerSaved.getInfrastructureProvisionerType());
    assertThat(APP_ID).isEqualTo(provisionerSaved.getAppId());
    assertThat(SETTING_ID).isEqualTo(provisionerSaved.getSourceRepoSettingId());
    assertThat(provisionerSaved.getRepoName()).isEqualTo("REPO_NAME");
    assertThat(provisionerSaved.getSecretManagerId()).isEqualTo("KMSID");

    Yaml yamlFromObject = handler.toYaml(provisionerSaved, APP_ID);
    String yamlContent = getYamlContent(yamlFromObject);
    assertThat(yamlContent).isEqualTo(validYamlContent);

    TerragruntInfrastructureProvisioner provisioner = TerragruntInfrastructureProvisioner.builder()
                                                          .appId(APP_ID)
                                                          .uuid("UUID1")
                                                          .name("Name1")
                                                          .description("Desc1")
                                                          .sourceRepoSettingId(SETTING_ID)
                                                          .sourceRepoBranch("master")
                                                          .repoName("REPO_NAME")
                                                          .secretManagerId("KMSID")
                                                          .skipRefreshBeforeApplyingPlan(true)
                                                          .build();
    TerragruntInfrastructureProvisioner.Yaml yaml1 = handler.toYaml(provisioner, APP_ID);
    assertThat(yaml1).isNotNull();
    assertThat("TERRAGRUNT").isEqualTo(yaml1.getType());
    assertThat("1.0").isEqualTo(yaml1.getHarnessApiVersion());
    assertThat("Desc1").isEqualTo(yaml1.getDescription());
    assertThat("master").isEqualTo(yaml1.getSourceRepoBranch());
    assertThat("SECRET_MANAGER").isEqualTo(yaml1.getSecretMangerName());
    handler.upsertFromYaml(changeContext, null);
    TerragruntInfrastructureProvisioner provisioner1 = captor.getValue();
    assertThat(provisioner).isEqualToIgnoringGivenFields(provisioner1, "uuid", "name", "description");
  }

  private ChangeContext<Yaml> getChangeContext() {
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(ACCOUNT_ID)
                                      .withFilePath(validYamlFilePath)
                                      .withFileContent(validYamlContent)
                                      .build();

    ChangeContext<Yaml> changeContext = new ChangeContext();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.PROVISIONER);
    changeContext.setYamlSyncHandler(handler);
    return changeContext;
  }
}
