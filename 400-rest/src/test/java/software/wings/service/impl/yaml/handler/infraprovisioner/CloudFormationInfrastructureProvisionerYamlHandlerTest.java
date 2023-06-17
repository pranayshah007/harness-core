/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.infraprovisioner;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.IVAN;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.CloudFormationInfrastructureProvisioner.Yaml;
import software.wings.beans.GitFileConfig;
import software.wings.beans.Service;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.impl.yaml.handler.tag.HarnessTagYamlHelper;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.utils.WingsTestConstants;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class CloudFormationInfrastructureProvisionerYamlHandlerTest extends YamlHandlerTestBase {
  @Mock private YamlHelper mockYamlHelper;
  @Mock private InfrastructureProvisionerService mockInfrastructureProvisionerService;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private HarnessTagYamlHelper harnessTagYamlHelper;
  @Mock private AppService mockAppService;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private GitFileConfigHelperService mockGitFileConfigHelperService;

  @InjectMocks @Inject private CloudFormationInfrastructureProvisionerYamlHandler handler;

  private String validYamlFilePath = "Setup/Applications/APP_NAME/Infrastructure Provisioners/CF_Name.yaml";
  private String validYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: CLOUD_FORMATION\n"
      + "description: Desc\n"
      + "sourceType: TEMPLATE_BODY\n"
      + "templateBody: Body\n";

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    ChangeContext<Yaml> changeContext = getChangeContext();
    Yaml yaml = (Yaml) getYaml(validYamlContent, Yaml.class);
    changeContext.setYaml(yaml);
    doReturn(APP_ID).when(mockYamlHelper).getAppId(anyString(), anyString());
    doReturn(null).when(mockInfrastructureProvisionerService).getByName(anyString(), anyString());
    Service service = Service.builder().name("ServiceName").uuid(SERVICE_ID).build();
    doReturn(service).when(mockServiceResourceService).get(anyString(), anyString());
    doReturn(service).when(mockServiceResourceService).getServiceByName(anyString(), anyString());

    handler.upsertFromYaml(changeContext, asList(changeContext));
    ArgumentCaptor<CloudFormationInfrastructureProvisioner> captor =
        ArgumentCaptor.forClass(CloudFormationInfrastructureProvisioner.class);
    verify(mockInfrastructureProvisionerService).save(captor.capture());
    CloudFormationInfrastructureProvisioner provisionerSaved = captor.getValue();
    assertThat(provisionerSaved).isNotNull();
    assertThat("CLOUD_FORMATION").isEqualTo(provisionerSaved.getInfrastructureProvisionerType());
    assertThat("TEMPLATE_BODY").isEqualTo(provisionerSaved.getSourceType());
    assertThat("Body").isEqualTo(provisionerSaved.getTemplateBody());
    assertThat("Desc").isEqualTo(provisionerSaved.getDescription());
    assertThat(APP_ID).isEqualTo(provisionerSaved.getAppId());

    Yaml yamlFromObject = handler.toYaml(provisionerSaved, WingsTestConstants.APP_ID);
    String yamlContent = getYamlContent(yamlFromObject);
    assertThat(yamlContent).isEqualTo(validYamlContent);

    CloudFormationInfrastructureProvisioner provisioner = CloudFormationInfrastructureProvisioner.builder()
                                                              .appId(APP_ID)
                                                              .uuid("UUID1")
                                                              .name("Name1")
                                                              .description("Desc1")
                                                              .sourceType("TEMPLATE_BODY")
                                                              .templateBody("Body1")
                                                              .build();

    Yaml yaml1 = handler.toYaml(provisioner, APP_ID);
    assertThat(yaml1).isNotNull();
    assertThat("CLOUD_FORMATION").isEqualTo(yaml1.getType());
    assertThat("1.0").isEqualTo(yaml1.getHarnessApiVersion());
    assertThat("TEMPLATE_BODY").isEqualTo(yaml1.getSourceType());
    assertThat("Body1").isEqualTo(yaml1.getTemplateBody());
    assertThat("Desc1").isEqualTo(yaml1.getDescription());
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

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGitFileConfigConnectorId() throws Exception {
    ChangeContext<Yaml> changeContext = getChangeContext();
    String cfGitFileConfigWitValidConnectorIdYaml = "harnessApiVersion: '1.0'\n"
        + "type: CLOUD_FORMATION\n"
        + "gitFileConfig:\n"
        + "  branch: main\n"
        + "  connectorId: connectorId\n"
        + "  filePath: /test\n"
        + "  useBranch: true\n"
        + "  useInlineServiceDefinition: false\n"
        + "sourceType: GIT";
    Yaml yaml = (Yaml) getYaml(cfGitFileConfigWitValidConnectorIdYaml, Yaml.class);
    changeContext.setYaml(yaml);
    doReturn(APP_ID).when(mockYamlHelper).getAppId(anyString(), anyString());
    doReturn(null).when(mockInfrastructureProvisionerService).getByName(anyString(), anyString());
    Service service = Service.builder().name("ServiceName").uuid(SERVICE_ID).build();
    doReturn(service).when(mockServiceResourceService).get(anyString(), anyString());
    doReturn(service).when(mockServiceResourceService).getServiceByName(anyString(), anyString());

    handler.upsertFromYaml(changeContext, asList(changeContext));
    ArgumentCaptor<CloudFormationInfrastructureProvisioner> captor =
        ArgumentCaptor.forClass(CloudFormationInfrastructureProvisioner.class);

    verify(mockInfrastructureProvisionerService).save(captor.capture());

    CloudFormationInfrastructureProvisioner provisionerSaved = captor.getValue();
    assertThat(provisionerSaved).isNotNull();
    assertThat("CLOUD_FORMATION").isEqualTo(provisionerSaved.getInfrastructureProvisionerType());
    GitFileConfig savedGitFileConfig = provisionerSaved.getGitFileConfig();
    assertThat(savedGitFileConfig).isNotNull();
    assertThat(savedGitFileConfig.getConnectorId()).isEqualTo("connectorId");
    assertThat(savedGitFileConfig.getBranch()).isEqualTo("main");
    assertThat(savedGitFileConfig.getFilePath()).isEqualTo("/test");
    assertThat(savedGitFileConfig.isUseBranch()).isTrue();
    assertThat(savedGitFileConfig.isUseInlineServiceDefinition()).isFalse();
    assertThat(provisionerSaved.getSourceType()).isEqualTo("GIT");

    provisionerSaved.setAccountId(ACCOUNT_ID);
    doReturn(false).when(mockFeatureFlagService).isEnabled(FeatureName.YAML_GIT_CONNECTOR_NAME, ACCOUNT_ID);
    Yaml yamlFromObject = handler.toYaml(provisionerSaved, APP_ID);

    assertThat(yamlFromObject.getGitFileConfig()).isNotNull();
    // when YAML_GIT_CONNECTOR_NAME FF is turned off connectorId should be returned to UI
    assertThat(yamlFromObject.getGitFileConfig().getConnectorId()).isEqualTo("connectorId");
    assertThat(yamlFromObject.getGitFileConfig().getConnectorName()).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGitFileConfigWithInvalidConnectorId() throws Exception {
    ChangeContext<Yaml> changeContext = getChangeContext();
    String cfGitFileConfigWitInvalidConnectorIdYaml = "harnessApiVersion: '1.0'\n"
        + "type: CLOUD_FORMATION\n"
        + "gitFileConfig:\n"
        + "  branch: main\n"
        + "  connectorId: invalidConnectorId\n"
        + "  filePath: /test\n"
        + "  useBranch: true\n"
        + "  useInlineServiceDefinition: false\n"
        + "sourceType: GIT";
    Yaml yaml = (Yaml) getYaml(cfGitFileConfigWitInvalidConnectorIdYaml, Yaml.class);
    changeContext.setYaml(yaml);
    doReturn(APP_ID).when(mockYamlHelper).getAppId(anyString(), anyString());
    doReturn(null).when(mockInfrastructureProvisionerService).getByName(anyString(), anyString());
    Service service = Service.builder().name("ServiceName").uuid(SERVICE_ID).build();
    doReturn(service).when(mockServiceResourceService).get(anyString(), anyString());
    doReturn(service).when(mockServiceResourceService).getServiceByName(anyString(), anyString());
    doThrow(new InvalidRequestException("Not found connector with connectorId"))
        .when(mockInfrastructureProvisionerService)
        .save(any());

    assertThatThrownBy(() -> handler.upsertFromYaml(changeContext, asList(changeContext)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Not found connector with connectorId");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGitFileConfigConnectorName() throws Exception {
    ChangeContext<Yaml> changeContext = getChangeContext();
    String cfGitFileConfigWitValidConnectorNameYaml = "harnessApiVersion: '1.0'\n"
        + "type: CLOUD_FORMATION\n"
        + "gitFileConfig:\n"
        + "  branch: main\n"
        + "  connectorName: connectorName\n"
        + "  filePath: /test\n"
        + "  useBranch: true\n"
        + "  useInlineServiceDefinition: false\n"
        + "sourceType: GIT";
    Yaml yaml = (Yaml) getYaml(cfGitFileConfigWitValidConnectorNameYaml, Yaml.class);
    changeContext.setYaml(yaml);
    doReturn(APP_ID).when(mockYamlHelper).getAppId(anyString(), anyString());
    doReturn(null).when(mockInfrastructureProvisionerService).getByName(anyString(), anyString());
    Service service = Service.builder().name("ServiceName").uuid(SERVICE_ID).build();
    doReturn(service).when(mockServiceResourceService).get(anyString(), anyString());
    doReturn(service).when(mockServiceResourceService).getServiceByName(anyString(), anyString());
    // GitFileConfig with connectName only from UI
    GitFileConfig yamlGitFileConfigWithConnectorName = yaml.getGitFileConfig();
    // GitFileConfig with connectName and connectorId from DB
    GitFileConfig yamlGitFileConfigWithConnectorIdAndName = GitFileConfig.builder()
                                                                .connectorName("connectorName")
                                                                .connectorId("connectorId")
                                                                .branch("main")
                                                                .filePath("/test")
                                                                .useBranch(true)
                                                                .useInlineServiceDefinition(false)
                                                                .build();
    doReturn(yamlGitFileConfigWithConnectorIdAndName)
        .when(mockGitFileConfigHelperService)
        .getGitFileConfigFromYaml(ACCOUNT_ID, APP_ID, yamlGitFileConfigWithConnectorName);

    handler.upsertFromYaml(changeContext, asList(changeContext));
    ArgumentCaptor<CloudFormationInfrastructureProvisioner> captor =
        ArgumentCaptor.forClass(CloudFormationInfrastructureProvisioner.class);
    verify(mockInfrastructureProvisionerService).save(captor.capture());
    CloudFormationInfrastructureProvisioner provisionerSaved = captor.getValue();
    assertThat(provisionerSaved).isNotNull();
    assertThat("CLOUD_FORMATION").isEqualTo(provisionerSaved.getInfrastructureProvisionerType());
    GitFileConfig savedGitFileConfig = provisionerSaved.getGitFileConfig();
    assertThat(savedGitFileConfig).isNotNull();
    assertThat(savedGitFileConfig.getConnectorId()).isEqualTo("connectorId");
    assertThat(savedGitFileConfig.getConnectorName()).isEqualTo("connectorName");
    assertThat(savedGitFileConfig.getBranch()).isEqualTo("main");
    assertThat(savedGitFileConfig.getFilePath()).isEqualTo("/test");
    assertThat(savedGitFileConfig.isUseBranch()).isTrue();
    assertThat(savedGitFileConfig.isUseInlineServiceDefinition()).isFalse();
    assertThat(provisionerSaved.getSourceType()).isEqualTo("GIT");

    provisionerSaved.setAccountId(ACCOUNT_ID);
    doReturn(true).when(mockFeatureFlagService).isEnabled(FeatureName.YAML_GIT_CONNECTOR_NAME, ACCOUNT_ID);
    doReturn(yamlGitFileConfigWithConnectorName)
        .when(mockGitFileConfigHelperService)
        .getGitFileConfigForToYaml(savedGitFileConfig);
    Yaml yamlFromObject = handler.toYaml(provisionerSaved, APP_ID);

    assertThat(yamlFromObject.getGitFileConfig()).isNotNull();
    // when YAML_GIT_CONNECTOR_NAME FF is turned on connectorName should be returned to UI
    assertThat(yamlFromObject.getGitFileConfig().getConnectorName()).isEqualTo("connectorName");
    assertThat(yamlFromObject.getGitFileConfig().getConnectorId()).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGitFileConfigWithInvalidConnectorName() throws Exception {
    ChangeContext<Yaml> changeContext = getChangeContext();
    String cfGitFileConfigWitInvalidConnectorNameYaml = "harnessApiVersion: '1.0'\n"
        + "type: CLOUD_FORMATION\n"
        + "gitFileConfig:\n"
        + "  branch: main\n"
        + "  connectorName: invalidConnectorName\n"
        + "  filePath: /test\n"
        + "  useBranch: true\n"
        + "  useInlineServiceDefinition: false\n"
        + "sourceType: GIT";
    Yaml yaml = (Yaml) getYaml(cfGitFileConfigWitInvalidConnectorNameYaml, Yaml.class);
    changeContext.setYaml(yaml);
    doReturn(APP_ID).when(mockYamlHelper).getAppId(anyString(), anyString());
    doReturn(null).when(mockInfrastructureProvisionerService).getByName(anyString(), anyString());
    Service service = Service.builder().name("ServiceName").uuid(SERVICE_ID).build();
    doReturn(service).when(mockServiceResourceService).get(anyString(), anyString());
    doReturn(service).when(mockServiceResourceService).getServiceByName(anyString(), anyString());
    // GitFileConfig with connectName only from UI
    GitFileConfig yamlGitFileConfigWithConnectorName = yaml.getGitFileConfig();
    doThrow(new InvalidRequestException("Not found connector with connectorName"))
        .when(mockGitFileConfigHelperService)
        .getGitFileConfigFromYaml(ACCOUNT_ID, APP_ID, yamlGitFileConfigWithConnectorName);

    assertThatThrownBy(() -> handler.upsertFromYaml(changeContext, asList(changeContext)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Not found connector with connectorName");
  }
}
