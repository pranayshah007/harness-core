/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.verification;

import static io.harness.rule.OwnerRule.SOWMYA;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.analysis.FeedbackPriority;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.StateType;
import software.wings.verification.log.LogsCVConfiguration.LogsCVConfigurationYaml;
import software.wings.verification.log.StackdriverCVConfiguration;
import software.wings.verification.log.StackdriverCVConfiguration.StackdriverCVConfigurationYaml;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StackdriverCVConfigurationYamlHandlerTest extends WingsBaseTest {
  @Mock YamlHelper yamlHelper;
  @Mock CVConfigurationService cvConfigurationService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock AppService appService;
  @Mock SettingsService settingsService;

  StackdriverCVConfigurationYamlHandler yamlHandler = new StackdriverCVConfigurationYamlHandler();

  private String envId;
  private String serviceId;
  private String appId;
  private String connectorId;
  private String accountId;

  private String messageField = "message";
  private String hostField = "hostName";
  private String serviceName = "serviceName";
  private String connectorName = "splunkConnector";
  private String configName = "TestStackDriverConfig";
  private String filePath = "TestSplunkConfig.yaml";

  @Before
  public void setup() throws Exception {
    accountId = generateUUID();
    envId = generateUUID();
    serviceId = generateUUID();
    appId = generateUUID();
    connectorId = generateUUID();

    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(yamlHandler, "yamlHelper", yamlHelper, true);
    FieldUtils.writeField(yamlHandler, "cvConfigurationService", cvConfigurationService, true);
    FieldUtils.writeField(yamlHandler, "appService", appService, true);
    FieldUtils.writeField(yamlHandler, "environmentService", environmentService, true);
    FieldUtils.writeField(yamlHandler, "serviceResourceService", serviceResourceService, true);
    FieldUtils.writeField(yamlHandler, "settingsService", settingsService, true);

    Service service = Service.builder().uuid(serviceId).name(serviceName).build();
    when(serviceResourceService.getWithDetails(appId, serviceId)).thenReturn(service);
    when(serviceResourceService.getServiceByName(appId, serviceName)).thenReturn(service);

    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withName(connectorName).withUuid(connectorId).build();
    when(settingsService.getSettingAttributeByName(accountId, connectorName)).thenReturn(settingAttribute);
    when(settingsService.get(connectorId)).thenReturn(settingAttribute);

    Application app = Application.Builder.anApplication().name(generateUUID()).uuid(appId).build();
    when(appService.get(appId)).thenReturn(app);
  }

  private void setBasicInfo(StackdriverCVConfiguration cvServiceConfiguration) {
    cvServiceConfiguration.setStateType(StateType.STACK_DRIVER_LOG);
    cvServiceConfiguration.setAccountId(accountId);
    cvServiceConfiguration.setServiceId(serviceId);
    cvServiceConfiguration.setConnectorId(connectorId);
    cvServiceConfiguration.setEnvId(envId);
    cvServiceConfiguration.setAppId(appId);
    cvServiceConfiguration.setEnabled24x7(true);
    cvServiceConfiguration.setName(configName);
  }

  private StackdriverCVConfigurationYaml buildYaml() {
    StackdriverCVConfigurationYaml yaml = new StackdriverCVConfigurationYaml();
    yaml.setLogsConfiguration(true);
    yaml.setMessageField(messageField);
    yaml.setHostnameField(hostField);
    yaml.setServiceName(serviceName);
    yaml.setConnectorName(connectorName);
    yaml.setType(StateType.STACK_DRIVER_LOG.name());
    yaml.setAlertPriority(FeedbackPriority.P1.name());
    return yaml;
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToYaml() {
    final String appId = "appId";
    StackdriverCVConfiguration cvServiceConfiguration =
        StackdriverCVConfiguration.builder().hostnameField(hostField).messageField(messageField).build();
    setBasicInfo(cvServiceConfiguration);

    StackdriverCVConfigurationYaml yaml = yamlHandler.toYaml(cvServiceConfiguration, appId);

    assertThat(yaml.getServiceName()).isEqualTo(serviceName);
    assertThat(yaml.getHostnameField()).isEqualTo(hostField);
    assertThat(yaml.getMessageField()).isEqualTo(messageField);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpsert() {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath(filePath)).thenReturn(configName);

    ChangeContext<LogsCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath(filePath).build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml());
    StackdriverCVConfiguration bean = (StackdriverCVConfiguration) yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo(configName);
    assertThat(bean.getAppId()).isEqualTo(appId);
    assertThat(bean.getEnvId()).isEqualTo(envId);
    assertThat(bean.getServiceId()).isEqualTo(serviceId);
    assertThat(bean.getUuid()).isNotNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpsertAlreadyExisting() {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath(filePath)).thenReturn(configName);

    StackdriverCVConfiguration cvConfig = StackdriverCVConfiguration.builder().build();
    cvConfig.setUuid("testUUID");
    when(cvConfigurationService.getConfiguration(configName, appId, envId)).thenReturn(cvConfig);
    ChangeContext<LogsCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath(filePath).build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml());
    changeContext.getYaml().setQuery("test");
    StackdriverCVConfiguration bean = (StackdriverCVConfiguration) yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo(configName);
    assertThat(bean.getUuid()).isEqualTo(cvConfig.getUuid());
    assertThat(bean.getQuery()).isEqualTo("test");
  }
}
