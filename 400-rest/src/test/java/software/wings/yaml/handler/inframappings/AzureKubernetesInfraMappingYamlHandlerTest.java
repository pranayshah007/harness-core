/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.inframappings;

import static io.harness.rule.OwnerRule.PUNEET;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.yaml.BaseYaml;

import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.helpers.ext.container.ContainerMasterUrlHelper;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.inframapping.AzureKubernetesInfraMappingYamlHandler;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ContainerService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.utils.ArtifactType;
import software.wings.yaml.handler.YamlHandlerTestBase;

import com.google.inject.Inject;
import dev.morphia.Key;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class AzureKubernetesInfraMappingYamlHandlerTest extends YamlHandlerTestBase {
  @Mock protected SettingsService settingsService;
  @Mock protected ServiceResourceService serviceResourceService;
  @Mock protected ServiceTemplateService serviceTemplateService;
  @Mock protected AppService appService;
  @Mock protected EnvironmentService environmentService;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private ContainerService containerService;
  @Mock private YamlDirectoryService yamlDirectoryService;

  @InjectMocks @Inject protected YamlHelper yamlHelper;
  @InjectMocks @Inject protected InfrastructureMappingService infrastructureMappingService;
  @InjectMocks @Inject protected AzureKubernetesInfraMappingYamlHandler azureKubernetesInfraMappingYamlHandler;
  @InjectMocks @Inject protected ContainerMasterUrlHelper containerMasterUrlHelper;

  private String validYamlContent = "harnessApiVersion: '1.0'\n"
      + "type: AZURE_KUBERNETES\n"
      + "cluster: test-aks\n"
      + "computeProviderName: azure-infra\n"
      + "computeProviderType: AZURE\n"
      + "deploymentType: KUBERNETES\n"
      + "infraMappingType: AZURE_KUBERNETES\n"
      + "namespace: default\n"
      + "resourceGroup: test-aks\n"
      + "serviceName: dockersvc\n"
      + "subscriptionId: 52d2db62-5aa9-471d-84bb-faa489b3e319";

  private String invalidYamlContent = "resourceGroup: test-aks\n"
      + "namespace: default\n"
      + "harnessApiVersion: '1.0'\n"
      + "type: AZURE_KUBERNETES";

  private String validYamlFilePath =
      "Setup/Applications/APP_NAME/Environments/ENVIRONMENT_NAME/Service Infrastructure/azure_kubernetes.yaml";
  private String infraMappingName = "azure_kubernetes";
  private String serviceName = "dockersvc";
  private String computeProviderName = "azure-infra";
  private ServiceTemplate serviceTemplate =
      ServiceTemplate.Builder.aServiceTemplate().withUuid("uuid").withName("name").build();
  private SettingAttribute settingAttribute = getSettingAttribute();

  @InjectMocks @Inject private AzureKubernetesInfraMappingYamlHandler yamlHandler;
  @Before
  public void runBeforeTest() {
    setup();
  }

  private void setup() {
    MockitoAnnotations.initMocks(this);

    when(settingsService.getByName(anyString(), anyString(), anyString())).thenReturn(settingAttribute);
    when(settingsService.get(anyString())).thenReturn(settingAttribute);
    when(appService.get(anyString())).thenReturn(getApplication());
    when(appService.getAppByName(anyString(), anyString())).thenReturn(getApplication());
    when(environmentService.getEnvironmentByName(anyString(), anyString())).thenReturn(getEnvironment());
    when(containerService.validate(any(), anyBoolean())).thenReturn(true);
    when(delegateProxyFactory.getV2(any(), any())).thenReturn(containerService);
    when(serviceResourceService.getServiceByName(anyString(), anyString())).thenReturn(getService());
    when(serviceResourceService.getWithDetails(anyString(), anyString())).thenReturn(getService());
    when(serviceTemplateService.getTemplateRefKeysByService(anyString(), anyString(), anyString()))
        .thenReturn(asList(new Key(ServiceTemplate.class, "serviceTemplates", SERVICE_ID)));
    when(serviceTemplateService.get(anyString(), anyString())).thenReturn(serviceTemplate);
  }

  private Service getService() {
    return Service.builder().name(serviceName).appId(APP_ID).uuid(SERVICE_ID).artifactType(ArtifactType.DOCKER).build();
  }

  private Environment getEnvironment() {
    return Environment.Builder.anEnvironment().uuid("ANY_UUID").name("ENV_NAME").build();
  }

  private Application getApplication() {
    return Application.Builder.anApplication().uuid("ANY_UUID").name(APP_NAME).accountId(ACCOUNT_ID).build();
  }

  private SettingAttribute getSettingAttribute() {
    return aSettingAttribute()
        .withName(computeProviderName)
        .withUuid(SETTING_ID)
        .withValue(AzureConfig.builder().clientId("ClientId").tenantId("tenantId").key("key".toCharArray()).build())
        .build();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testCRUDAndGet() throws Exception {
    when(containerMasterUrlHelper.fetchMasterUrl(any(ContainerServiceParams.class), any(SyncTaskContext.class)))
        .thenReturn("master_url");

    ChangeContext<AzureKubernetesInfrastructureMapping.Yaml> changeContext =
        getChangeContext(validYamlContent, validYamlFilePath, yamlHandler);

    AzureKubernetesInfrastructureMapping.Yaml yamlObject = (AzureKubernetesInfrastructureMapping.Yaml) getYaml(
        validYamlContent, AzureKubernetesInfrastructureMapping.Yaml.class);
    changeContext.setYaml(yamlObject);

    AzureKubernetesInfrastructureMapping azureInfraMapping =
        yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
    assertThat(azureInfraMapping).isNotNull();
    assertThat(infraMappingName).isEqualTo(azureInfraMapping.getName());

    AzureKubernetesInfrastructureMapping.Yaml yaml = yamlHandler.toYaml(azureInfraMapping, APP_ID);
    assertThat(yaml).isNotNull();
    assertThat(yaml.getType()).isEqualTo("AZURE_KUBERNETES");

    String yamlContent = getYamlContent(yaml);
    assertThat(yamlContent).isNotNull();
    yamlContent = yamlContent.substring(0, yamlContent.length() - 1);
    assertThat(yamlContent).isEqualTo(validYamlContent);

    InfrastructureMapping infraMapping = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(infraMapping).isNotNull();
    assertThat(infraMappingName).isEqualTo(infraMapping.getName());

    yamlHandler.delete(changeContext);

    InfrastructureMapping deletedInfraMapping = yamlHandler.get(ACCOUNT_ID, validYamlFilePath);
    assertThat(deletedInfraMapping).isNull();
  }

  @Test
  @Owner(developers = PUNEET)
  @Category(UnitTests.class)
  public void testFailures() throws Exception {
    ChangeContext<AzureKubernetesInfrastructureMapping.Yaml> changeContext =
        getChangeContext(invalidYamlContent, validYamlFilePath, yamlHandler);

    AzureKubernetesInfrastructureMapping.Yaml yamlObject = (AzureKubernetesInfrastructureMapping.Yaml) getYaml(
        validYamlContent, AzureKubernetesInfrastructureMapping.Yaml.class);
    changeContext.setYaml(yamlObject);

    yamlObject = (AzureKubernetesInfrastructureMapping.Yaml) getYaml(
        invalidYamlContent, AzureKubernetesInfrastructureMapping.Yaml.class);
    changeContext.setYaml(yamlObject);
    thrown.expect(Exception.class);
    yamlHandler.upsertFromYaml(changeContext, asList(changeContext));
  }

  protected <Y extends BaseYaml, H extends BaseYamlHandler> ChangeContext<Y> getChangeContext(
      String yamlContent, String yamlFilePath, H yamlHandler) {
    // Invalid yaml path
    GitFileChange gitFileChange = GitFileChange.Builder.aGitFileChange()
                                      .withAccountId(ACCOUNT_ID)
                                      .withFilePath(yamlFilePath)
                                      .withFileContent(yamlContent)
                                      .build();

    ChangeContext<Y> changeContext = new ChangeContext();
    changeContext.setChange(gitFileChange);
    changeContext.setYamlType(YamlType.INFRA_MAPPING);
    changeContext.setYamlSyncHandler(yamlHandler);
    return changeContext;
  }
}
