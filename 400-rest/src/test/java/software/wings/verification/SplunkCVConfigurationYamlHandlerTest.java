/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.verification;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

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
import software.wings.verification.log.LogsCVConfiguration;
import software.wings.verification.log.LogsCVConfiguration.LogsCVConfigurationYaml;
import software.wings.verification.log.SplunkCVConfiguration;
import software.wings.verification.log.SplunkCVConfiguration.SplunkCVConfigurationYaml;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SplunkCVConfigurationYamlHandlerTest extends CategoryTest {
  @Mock YamlHelper yamlHelper;
  @Mock CVConfigurationService cvConfigurationService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock AppService appService;
  @Mock SettingsService settingsService;

  private String envId;
  private String serviceId;
  private String appId;
  private String connectorId;
  private String accountId;

  private String serviceName = "serviceName";
  private String connectorName = "splunkConnector";

  SplunkCVConfigurationYamlHandler yamlHandler = new SplunkCVConfigurationYamlHandler();

  @Before
  public void setup() throws IllegalAccessException {
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

  private void setBasicInfo(LogsCVConfiguration cvServiceConfiguration) {
    cvServiceConfiguration.setStateType(StateType.SPLUNKV2);
    cvServiceConfiguration.setAccountId(accountId);
    cvServiceConfiguration.setServiceId(serviceId);
    cvServiceConfiguration.setConnectorId(connectorId);
    cvServiceConfiguration.setEnvId(envId);
    cvServiceConfiguration.setAppId(appId);
    cvServiceConfiguration.setEnabled24x7(true);
    cvServiceConfiguration.setName("TestAppDConfig");
  }

  private SplunkCVConfigurationYaml buildYaml() {
    SplunkCVConfigurationYaml yaml = new SplunkCVConfigurationYaml();
    yaml.setType(StateType.SPLUNKV2.name());
    yaml.setServiceName(serviceName);
    yaml.setConnectorName(connectorName);
    yaml.setQuery("query1");
    yaml.setBaselineStartMinute(16L);
    yaml.setBaselineEndMinute(30L);
    yaml.setAdvancedQuery(true);
    yaml.setHostnameField("hostName1");
    yaml.setAlertPriority(FeedbackPriority.P5.name());
    return yaml;
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testToYaml() {
    final String appId = "appId";
    SplunkCVConfiguration cvServiceConfiguration = new SplunkCVConfiguration();
    setBasicInfo(cvServiceConfiguration);
    cvServiceConfiguration.setQuery(generateUUID());
    cvServiceConfiguration.setBaselineStartMinute(16);
    cvServiceConfiguration.setBaselineEndMinute(30);
    cvServiceConfiguration.setAdvancedQuery(true);
    cvServiceConfiguration.setHostnameField(generateUUID());
    cvServiceConfiguration.setAlertPriority(FeedbackPriority.P5);

    SplunkCVConfigurationYaml yaml = (SplunkCVConfigurationYaml) yamlHandler.toYaml(cvServiceConfiguration, appId);

    assertThat(yaml.getServiceName()).isEqualTo(serviceName);
    assertThat(yaml.getQuery()).isEqualTo(cvServiceConfiguration.getQuery());
    assertThat(yaml.getBaselineStartMinute()).isEqualTo(cvServiceConfiguration.getBaselineStartMinute());
    assertThat(yaml.getBaselineEndMinute()).isEqualTo(cvServiceConfiguration.getBaselineEndMinute());
    assertThat(yaml.getHostnameField()).isEqualTo(cvServiceConfiguration.getHostnameField());
    assertThat(yaml.isAdvancedQuery()).isEqualTo(cvServiceConfiguration.isAdvancedQuery());
    assertThat(yaml.getAlertPriority()).isEqualTo(cvServiceConfiguration.getAlertPriority().name());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpsert() {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestAppDConfig.yaml")).thenReturn("TestAppDConfig");

    ChangeContext<LogsCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestAppDConfig.yaml").build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml());
    SplunkCVConfiguration bean = (SplunkCVConfiguration) yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo("TestAppDConfig");
    assertThat(bean.getAppId()).isEqualTo(appId);
    assertThat(bean.getEnvId()).isEqualTo(envId);
    assertThat(bean.getServiceId()).isEqualTo(serviceId);
    assertThat(bean.getQuery()).isEqualTo("query1");
    assertThat(bean.getBaselineStartMinute()).isEqualTo(16);
    assertThat(bean.getBaselineEndMinute()).isEqualTo(30);
    assertThat(bean.getHostnameField()).isEqualTo("hostName1");
    assertThat(bean.isAdvancedQuery()).isEqualTo(true);
    assertThat(bean.getAlertPriority()).isEqualTo(FeedbackPriority.P5);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpsertAlreadyExisting() {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestAppDConfig.yaml")).thenReturn("TestAppDConfig");

    SplunkCVConfiguration cvConfig = new SplunkCVConfiguration();
    cvConfig.setUuid("testUUID");
    when(cvConfigurationService.getConfiguration("TestAppDConfig", appId, envId)).thenReturn(cvConfig);
    ChangeContext<LogsCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestAppDConfig.yaml").build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml());
    SplunkCVConfiguration bean = (SplunkCVConfiguration) yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo("TestAppDConfig");
    assertThat(bean.getUuid()).isEqualTo(cvConfig.getUuid());
  }
}
