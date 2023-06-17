/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.yaml.handler.inframappings;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.ServiceTemplate.Builder.aServiceTemplate;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.yaml.YamlConstants.PATH_DELIMITER;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.HOST_CONN_ATTR_ID;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.PROVISIONER_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.TEMPLATE_ID;

import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.yaml.BaseYaml;

import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.yaml.ChangeContext;
import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.YamlType;
import software.wings.service.impl.yaml.handler.BaseYamlHandler;
import software.wings.service.impl.yaml.handler.YamlHandlerFactory;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.utils.ArtifactType;
import software.wings.yaml.handler.YamlHandlerTestBase;

import dev.morphia.Key;
import java.util.Optional;
import org.mockito.Mock;

public class BaseInfraMappingYamlHandlerTestBase extends YamlHandlerTestBase {
  protected Service service =
      Service.builder().name(SERVICE_NAME).appId(APP_ID).uuid(SERVICE_ID).artifactType(ArtifactType.DOCKER).build();

  protected InfrastructureProvisioner infrastructureProvisioner =
      TerraformInfrastructureProvisioner.builder().name(PROVISIONER_NAME).appId(APP_ID).uuid(PROVISIONER_ID).build();

  protected Environment environment = anEnvironment().name(ENV_NAME).appId(APP_ID).uuid(ENV_ID).build();

  @Mock protected YamlHelper yamlHelper;
  @Mock protected YamlHandlerFactory yamlHandlerFactory;

  @Mock protected AppService appService;
  @Mock protected ServiceResourceService serviceResourceService;
  @Mock protected ServiceTemplateService serviceTemplateService;
  @Mock protected EnvironmentService environmentService;
  @Mock protected SettingsService settingsService;
  @Mock protected InfrastructureProvisionerService infrastructureProvisionerService;

  private SettingAttribute setting = aSettingAttribute()
                                         .withUuid(SETTING_ID)
                                         .withAccountId(ACCOUNT_ID)
                                         .withName("aws")
                                         .withValue(AwsConfig.builder().build())
                                         .build();

  private SettingAttribute connAttr =
      aSettingAttribute().withUuid(HOST_CONN_ATTR_ID).withAccountId(ACCOUNT_ID).withName("Wings Key").build();

  protected void setup(String yamlFilePath, String infraMappingName) {
    when(appService.getAppByName(anyString(), anyString()))
        .thenReturn(anApplication().name(APP_NAME).uuid(APP_ID).build());

    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(APP_ID);
    when(yamlHelper.getApplicationIfPresent(anyString(), anyString()))
        .thenReturn(Optional.of(anApplication().uuid(APP_ID).build()));
    when(yamlHelper.getEnvIfPresent(anyString(), anyString()))
        .thenReturn(Optional.of(anEnvironment().uuid(ENV_ID).build()));
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(ENV_ID);
    when(yamlHelper.getNameFromYamlFilePath(yamlFilePath)).thenReturn(infraMappingName);
    when(yamlHelper.extractEntityNameFromYamlPath(
             YamlType.INFRA_MAPPING.getPathExpression(), yamlFilePath, PATH_DELIMITER))
        .thenReturn(infraMappingName);

    when(environmentService.getEnvironmentByName(anyString(), anyString())).thenReturn(environment);
    when(environmentService.get(APP_ID, ENV_ID, false)).thenReturn(environment);

    when(serviceResourceService.getServiceByName(anyString(), anyString())).thenReturn(service);
    when(serviceResourceService.getWithDetails(anyString(), anyString())).thenReturn(service);
    when(serviceTemplateService.getTemplateRefKeysByService(APP_ID, SERVICE_ID, ENV_ID))
        .thenReturn(singletonList(new Key<>(ServiceTemplate.class, "serviceTemplate", TEMPLATE_ID)));
    when(serviceTemplateService.get(APP_ID, TEMPLATE_ID)).thenReturn(aServiceTemplate().withUuid(TEMPLATE_ID).build());

    when(infrastructureProvisionerService.getByName(anyString(), anyString())).thenReturn(infrastructureProvisioner);
    when(infrastructureProvisionerService.get(APP_ID, PROVISIONER_ID)).thenReturn(infrastructureProvisioner);

    when(settingsService.getByName(ACCOUNT_ID, APP_ID, "aws")).thenReturn(setting);
    when(settingsService.get(SETTING_ID)).thenReturn(setting);
    when(settingsService.getSettingAttributeByName(ACCOUNT_ID, "Wings Key")).thenReturn(connAttr);
    when(settingsService.get(HOST_CONN_ATTR_ID)).thenReturn(connAttr);
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
