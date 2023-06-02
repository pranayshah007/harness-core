/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.infraprovisioner;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.AKHIL_PANDEY;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.RAGHVENDRA;

import static software.wings.service.impl.yaml.handler.infraprovisioner.TestConstants.APP_ID;
import static software.wings.service.impl.yaml.handler.infraprovisioner.TestConstants.SERVICE_ID;
import static software.wings.service.impl.yaml.handler.infraprovisioner.TestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.beans.Application;
import software.wings.beans.KmsConfig;
import software.wings.beans.NameValuePair;
import software.wings.beans.Service;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.TerraformInfrastructureProvisioner.Yaml;
import software.wings.beans.TerraformSourceType;
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
import software.wings.utils.WingsTestConstants;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._955_CG_YAML)
public class TerraformInfrastructureProvisionerYamlHandlerTest extends YamlHandlerTestBase {
  @Mock private YamlHelper mockYamlHelper;
  @Mock private InfrastructureProvisionerService mockInfrastructureProvisionerService;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private SettingsService mockSettingsService;
  @Mock private AppService appService;
  @Mock private HarnessTagYamlHelper harnessTagYamlHelper;
  @Mock private SecretManager secretManager;

  @InjectMocks @Inject private TerraformInfrastructureProvisionerYamlHandler handler;
  private String validYamlFilePath = "Setup/Applications/APP_NAME/Infrastructure Provisioners/TF_Name.yaml";
  private String validS3YamlFilePath = "Setup/Applications/APP_NAME/Infrastructure Provisioners/TF_S3_Name.yaml";

  private String validS3SourceURI = "s3://iis-website-quickstart/terraform-manifest/variableOutputScript/";

  private String inValidYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: TERRAFORM\n"
      + "backendConfigs:\n"
      + "- name: access_key\n"
      + "  valueType: TEXT\n"
      + "- name: secret_key\n"
      + "  valueType: ENCRYPTED_TEXT\n"
      + "repoName: REPO_NAME\n"
      + "secretMangerName: SECRET_MANAGER\n"
      + "skipRefreshBeforeApplyingPlan: true\n"
      + "sourceRepoBranch: master\n"
      + "sourceType: GIT\n"
      + "sourceRepoSettingName: TERRAFORM_TEST_GIT_REPO\n"
      + "variables:\n"
      + "- name: access_key\n"
      + "  valueType: TEXT\n"
      + "- name: secret_key\n"
      + "  valueType: ENCRYPTED_TEXT\n";
  private String validYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: TERRAFORM\n"
      + "repoName: REPO_NAME\n"
      + "secretMangerName: SECRET_MANAGER\n"
      + "skipRefreshBeforeApplyingPlan: true\n"
      + "sourceRepoBranch: master\n"
      + "sourceRepoSettingName: TERRAFORM_TEST_GIT_REPO\n"
      + "sourceType: GIT\n";

  private String validS3SourceYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: TERRAFORM\n"
      + "awsSourceConfigName: S3_AWS_CONFIG\n"
      + "s3URI: s3://iis-website-quickstart/terraform-manifest/variableOutputScript/\n"
      + "skipRefreshBeforeApplyingPlan: true\n"
      + "sourceType: S3\n";

  @Test
  @Owner(developers = GEORGE)
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
        SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).withName("TERRAFORM_TEST_GIT_REPO").build();
    SecretManagerConfig secretManagerConfig = KmsConfig.builder().uuid("KMSID").name("SECRET_MANAGER").build();
    doReturn(settingAttribute).when(mockSettingsService).getSettingAttributeByName(any(), any());
    doReturn(settingAttribute).when(mockSettingsService).get(any(), any());
    doReturn(Application.Builder.anApplication().uuid(APP_ID).build()).when(appService).get(any());
    doReturn(secretManagerConfig).when(secretManager).getSecretManager(any(), any());
    doReturn(secretManagerConfig).when(secretManager).getSecretManagerByName(any(), any());
    handler.upsertFromYaml(changeContext, asList(changeContext));
    ArgumentCaptor<TerraformInfrastructureProvisioner> captor =
        ArgumentCaptor.forClass(TerraformInfrastructureProvisioner.class);
    verify(mockInfrastructureProvisionerService).save(captor.capture());
    TerraformInfrastructureProvisioner provisionerSaved = captor.getValue();
    assertThat(provisionerSaved).isNotNull();
    assertThat("TERRAFORM").isEqualTo(provisionerSaved.getInfrastructureProvisionerType());
    assertThat(APP_ID).isEqualTo(provisionerSaved.getAppId());
    assertThat(TestConstants.SETTING_ID).isEqualTo(provisionerSaved.getSourceRepoSettingId());
    assertThat(provisionerSaved.getRepoName()).isEqualTo("REPO_NAME");
    assertThat(provisionerSaved.getKmsId()).isEqualTo("KMSID");
    Yaml yamlFromObject = handler.toYaml(provisionerSaved, APP_ID);
    String yamlContent = getYamlContent(yamlFromObject);
    assertThat(yamlContent).isEqualTo(validYamlContent);

    List<NameValuePair> variables = Arrays.asList(
        NameValuePair.builder().name("access_key").valueType(ServiceVariableType.TEXT.toString()).build(),
        NameValuePair.builder().name("secret_key").valueType(ServiceVariableType.ENCRYPTED_TEXT.toString()).build());
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .uuid("UUID1")
                                                         .name("Name1")
                                                         .description("Desc1")
                                                         .sourceRepoSettingId(SETTING_ID)
                                                         .sourceRepoBranch("master")
                                                         .terraformSourceType(TerraformSourceType.GIT)
                                                         .repoName("REPO_NAME")
                                                         .kmsId("KMSID")
                                                         .skipRefreshBeforeApplyingPlan(true)
                                                         .build();
    TerraformInfrastructureProvisioner.Yaml yaml1 = handler.toYaml(provisioner, APP_ID);
    assertThat(yaml1).isNotNull();
    assertThat(yaml1.getVariables()).isNull();
    assertThat(yaml1.getBackendConfigs()).isNull();
    assertThat("TERRAFORM").isEqualTo(yaml1.getType());
    assertThat("1.0").isEqualTo(yaml1.getHarnessApiVersion());
    assertThat("Desc1").isEqualTo(yaml1.getDescription());
    assertThat("master").isEqualTo(yaml1.getSourceRepoBranch());
    assertThat("SECRET_MANAGER").isEqualTo(yaml1.getSecretMangerName());
    handler.upsertFromYaml(changeContext, null);
    TerraformInfrastructureProvisioner provisioner1 = captor.getValue();
    assertThat(provisioner).isEqualToIgnoringGivenFields(provisioner1, "uuid", "name", "description");

    doReturn(null).when(secretManager).getSecretManager(any(), any());
    assertThatThrownBy(() -> handler.toYaml(provisioner, APP_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No secret manager found");
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testCRUDAndGetWithS3SourceType() throws IOException {
    ChangeContext<Yaml> changeContext = getS3ChangeContext();
    Yaml yaml = (Yaml) getYaml(validS3SourceYamlContent, Yaml.class);
    changeContext.setYaml(yaml);
    doReturn(APP_ID).when(mockYamlHelper).getAppId(any(), any());
    doReturn(null).when(mockInfrastructureProvisionerService).getByName(any(), any());
    Service service = Service.builder().name("ServiceName").uuid(SERVICE_ID).build();
    doReturn(service).when(mockServiceResourceService).get(any(), any());
    doReturn(service).when(mockServiceResourceService).getServiceByName(any(), any());
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).withName("S3_AWS_CONFIG").build();

    doReturn(settingAttribute).when(mockSettingsService).getSettingAttributeByName(any(), any());
    doReturn(settingAttribute).when(mockSettingsService).get(any(), any());
    doReturn(Application.Builder.anApplication().uuid(APP_ID).build()).when(appService).get(any());

    handler.upsertFromYaml(changeContext, asList(changeContext));
    ArgumentCaptor<TerraformInfrastructureProvisioner> captor =
        ArgumentCaptor.forClass(TerraformInfrastructureProvisioner.class);
    verify(mockInfrastructureProvisionerService).save(captor.capture());
    TerraformInfrastructureProvisioner provisionerSaved = captor.getValue();
    assertThat(provisionerSaved).isNotNull();
    assertThat("TERRAFORM").isEqualTo(provisionerSaved.getInfrastructureProvisionerType());
    assertThat(APP_ID).isEqualTo(provisionerSaved.getAppId());
    assertThat(TestConstants.SETTING_ID).isEqualTo(provisionerSaved.getAwsConfigId());
  }

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testToYAMLWithS3() throws IOException {
    Service service = Service.builder().name("ServiceName").uuid(SERVICE_ID).build();

    doReturn(APP_ID).when(mockYamlHelper).getAppId(any(), any());
    doReturn(null).when(mockInfrastructureProvisionerService).getByName(any(), any());
    doReturn(service).when(mockServiceResourceService).get(any(), any());
    doReturn(service).when(mockServiceResourceService).getServiceByName(any(), any());
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).withName("S3_AWS_CONFIG").build();

    doReturn(settingAttribute).when(mockSettingsService).getSettingAttributeByName(any(), any());
    doReturn(settingAttribute).when(mockSettingsService).get(any(), any());
    doReturn(Application.Builder.anApplication().uuid(APP_ID).build()).when(appService).get(any());

    ChangeContext<Yaml> changeContext = getS3ChangeContext();
    Yaml yaml = (Yaml) getYaml(validS3SourceYamlContent, Yaml.class);
    changeContext.setYaml(yaml);
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .uuid("UUID1")
                                                         .name("Name1")
                                                         .description("Desc1")
                                                         .terraformSourceType(TerraformSourceType.S3)
                                                         .s3URI(validS3SourceURI)
                                                         .awsConfigId("SETTING_ID")
                                                         .skipRefreshBeforeApplyingPlan(true)
                                                         .build();
    TerraformInfrastructureProvisioner.Yaml yaml1 = handler.toYaml(provisioner, APP_ID);
    ArgumentCaptor<TerraformInfrastructureProvisioner> captor =
        ArgumentCaptor.forClass(TerraformInfrastructureProvisioner.class);
    assertThat(yaml1).isNotNull();
    assertThat(yaml1.getVariables()).isNull();
    assertThat(yaml1.getBackendConfigs()).isNull();
    assertThat("TERRAFORM").isEqualTo(yaml1.getType());
    assertThat("1.0").isEqualTo(yaml1.getHarnessApiVersion());
    assertThat(TerraformSourceType.S3).isEqualTo(yaml1.getSourceType());
    assertThat(validS3SourceURI).isEqualTo(yaml1.getS3URI());
    assertThat("S3_AWS_CONFIG").isEqualTo(yaml1.getAwsSourceConfigName());
    handler.upsertFromYaml(changeContext, asList(changeContext));
    verify(mockInfrastructureProvisionerService).save(captor.capture());
    TerraformInfrastructureProvisioner provisioner1 = captor.getValue();
    assertThat(provisioner).isEqualToIgnoringGivenFields(provisioner1, "uuid", "name", "description", "kmsId");
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testCRUDAndGetWithException() throws IOException {
    ChangeContext<Yaml> changeContext = getChangeContext();
    Yaml yaml = (Yaml) getYaml(inValidYamlContent, Yaml.class);
    changeContext.setYaml(yaml);
    doReturn(APP_ID).when(mockYamlHelper).getAppId(any(), any());
    doReturn(null).when(mockInfrastructureProvisionerService).getByName(any(), any());
    Service service = Service.builder().name("ServiceName").uuid(SERVICE_ID).build();
    doReturn(service).when(mockServiceResourceService).get(any(), any());
    doReturn(service).when(mockServiceResourceService).getServiceByName(any(), any());
    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withUuid(SETTING_ID).withName("TERRAFORM_TEST_GIT_REPO").build();
    SecretManagerConfig secretManagerConfig = KmsConfig.builder().uuid("KMSID").name("SECRET_MANAGER").build();
    doReturn(settingAttribute).when(mockSettingsService).getSettingAttributeByName(any(), any());
    doReturn(settingAttribute).when(mockSettingsService).get(any(), any());
    doReturn(Application.Builder.anApplication().uuid(APP_ID).build()).when(appService).get(any());
    doReturn(secretManagerConfig).when(secretManager).getSecretManager(any(), any());
    doReturn(secretManagerConfig).when(secretManager).getSecretManagerByName(any(), any());

    handler.upsertFromYaml(changeContext, asList(changeContext));

    TerraformInfrastructureProvisioner provisionerSaved =
        TerraformInfrastructureProvisioner.builder()
            .variables(asList(NameValuePair.builder().name("region").valueType("TEXT").value("us-east-1").build()))
            .repoName("REPO_NAME")
            .terraformSourceType(TerraformSourceType.GIT)
            .build();

    Yaml tfYaml = handler.toYaml(provisionerSaved, WingsTestConstants.APP_ID);
    assertThat(tfYaml.getVariables()).isNull();
    assertThat(tfYaml.getBackendConfigs()).isNull();
    assertThat(tfYaml.getEnvironmentVariables()).isNull();

    List<NameValuePair> envVariables = Arrays.asList(
        NameValuePair.builder().name("access_key").valueType(ServiceVariableType.TEXT.toString()).build(),
        NameValuePair.builder().name("secret_key").valueType(ServiceVariableType.ENCRYPTED_TEXT.toString()).build());
    TerraformInfrastructureProvisioner provisioner = TerraformInfrastructureProvisioner.builder()
                                                         .appId(APP_ID)
                                                         .uuid("UUID1")
                                                         .name("Name1")
                                                         .description("Desc1")
                                                         .sourceRepoSettingId(SETTING_ID)
                                                         .sourceRepoBranch("master")
                                                         .repoName("REPO_NAME")
                                                         .environmentVariables(envVariables)
                                                         .backendConfigs(envVariables)
                                                         .kmsId("KMSID")
                                                         .skipRefreshBeforeApplyingPlan(true)
                                                         .build();

    tfYaml = handler.toYaml(provisioner, APP_ID);
    assertThat(tfYaml.getVariables()).isNull();
    assertThat(tfYaml.getBackendConfigs()).isNull();
    assertThat(tfYaml.getEnvironmentVariables()).isNull();

    handler.upsertFromYaml(changeContext, null);
    ArgumentCaptor<TerraformInfrastructureProvisioner> captor =
        ArgumentCaptor.forClass(TerraformInfrastructureProvisioner.class);
    verify(mockInfrastructureProvisionerService, times(2)).save(captor.capture());
    List<TerraformInfrastructureProvisioner> tfList = captor.getAllValues();
    assertThat(tfList.size()).isEqualTo(2);

    assertThat(tfList.get(0).getVariables()).isNull();
    assertThat(tfList.get(0).getBackendConfigs()).isNull();
    assertThat(tfList.get(0).getEnvironmentVariables()).isNull();

    assertThat(tfList.get(1).getVariables()).isNull();
    assertThat(tfList.get(1).getBackendConfigs()).isNull();
    assertThat(tfList.get(1).getEnvironmentVariables()).isNull();

    doReturn(null).when(secretManager).getSecretManagerByName(any(), any());
    assertThatThrownBy(() -> handler.upsertFromYaml(changeContext, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No secret manager found");
  }

  private ChangeContext<Yaml> getChangeContext() {
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(TestConstants.ACCOUNT_ID)
                                      .withFilePath(validYamlFilePath)
                                      .withFileContent(validYamlContent)
                                      .build();

    ChangeContext<Yaml> changeContext = new ChangeContext();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.PROVISIONER);
    changeContext.setYamlSyncHandler(handler);
    return changeContext;
  }

  private ChangeContext<Yaml> getS3ChangeContext() {
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(TestConstants.ACCOUNT_ID)
                                      .withFilePath(validS3YamlFilePath)
                                      .withFileContent(validS3SourceYamlContent)
                                      .build();

    ChangeContext<Yaml> changeContext = new ChangeContext();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.PROVISIONER);
    changeContext.setYamlSyncHandler(handler);
    return changeContext;
  }
}
