/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.verification;

import static io.harness.rule.OwnerRule.SOWMYA;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.beans.ApmMetricCollectionInfo;
import software.wings.beans.ApmResponseMapping;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.apm.Method;
import software.wings.beans.apm.ResponseType;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.metrics.MetricType;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.StateType;
import software.wings.verification.apm.APMCVConfigurationYaml;
import software.wings.verification.apm.APMCVServiceConfiguration;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class APMCVConfigurationYamlHandlerTest extends CategoryTest {
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
  private String connectorName = "apmConnector";

  APMCVConfigurationYamlHandler yamlHandler = new APMCVConfigurationYamlHandler();

  @Before
  public void setup() throws IllegalAccessException {
    appId = generateUUID();
    connectorId = generateUUID();
    envId = generateUUID();
    serviceId = generateUUID();
    accountId = generateUUID();

    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(yamlHandler, "appService", appService, true);
    FieldUtils.writeField(yamlHandler, "yamlHelper", yamlHelper, true);
    FieldUtils.writeField(yamlHandler, "cvConfigurationService", cvConfigurationService, true);
    FieldUtils.writeField(yamlHandler, "serviceResourceService", serviceResourceService, true);
    FieldUtils.writeField(yamlHandler, "settingsService", settingsService, true);
    FieldUtils.writeField(yamlHandler, "environmentService", environmentService, true);

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

  private void setBasicInfo(APMCVServiceConfiguration cvServiceConfiguration) {
    cvServiceConfiguration.setStateType(StateType.APM_VERIFICATION);
    cvServiceConfiguration.setAccountId(accountId);
    cvServiceConfiguration.setServiceId(serviceId);
    cvServiceConfiguration.setConnectorId(connectorId);
    cvServiceConfiguration.setEnvId(envId);
    cvServiceConfiguration.setAppId(appId);
    cvServiceConfiguration.setEnabled24x7(true);
    cvServiceConfiguration.setName("TestAPMConfig");
  }

  private APMCVConfigurationYaml buildYaml() {
    List<ApmMetricCollectionInfo> metrics = new ArrayList<>();
    metrics.add(getMetricCollectionInfo());

    APMCVConfigurationYaml yaml = APMCVConfigurationYaml.builder().metricCollectionInfos(metrics).build();
    yaml.setServiceName(serviceName);
    yaml.setConnectorName(connectorName);
    return yaml;
  }

  private ApmMetricCollectionInfo getMetricCollectionInfo() {
    return ApmMetricCollectionInfo.builder()
        .metricName("throughput")
        .metricType(MetricType.THROUGHPUT)
        .tag("cluster-name:harness-test")
        .collectionUrl("url")
        .collectionBody("body")
        .responseType(ResponseType.JSON)
        .responseMapping(ApmResponseMapping.builder().build())
        .method(Method.GET)
        .build();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToYaml() {
    final String appId = "appId";
    List<ApmMetricCollectionInfo> metrics = new ArrayList<>();
    metrics.add(getMetricCollectionInfo());

    APMCVServiceConfiguration cvServiceConfiguration =
        APMCVServiceConfiguration.builder().metricCollectionInfos(metrics).build();
    setBasicInfo(cvServiceConfiguration);

    APMCVConfigurationYaml yaml = yamlHandler.toYaml(cvServiceConfiguration, appId);

    assertThat(yaml.getServiceName()).isEqualTo(serviceName);
    assertThat(yaml.getMetricCollectionInfos()).isEqualTo(cvServiceConfiguration.getMetricCollectionInfos());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpsert() throws Exception {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestAPMConfig.yaml")).thenReturn("TestAPMConfig");

    ChangeContext<APMCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestAPMConfig.yaml").build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml());
    APMCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo("TestAPMConfig");
    assertThat(bean.getAppId()).isEqualTo(appId);
    assertThat(bean.getEnvId()).isEqualTo(envId);
    assertThat(bean.getServiceId()).isEqualTo(serviceId);
    assertThat(bean.getUuid()).isNotNull();
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpsertEmptyMetrics() throws Exception {
    when(yamlHelper.getNameFromYamlFilePath("TestAPMConfig.yaml")).thenReturn("TestAPMConfig");
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);

    APMCVConfigurationYaml yaml = buildYaml();
    yaml.setMetricCollectionInfos(new ArrayList<>());

    ChangeContext<APMCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestAPMConfig.yaml").build();
    changeContext.setChange(c);
    changeContext.setYaml(yaml);

    yamlHandler.upsertFromYaml(changeContext, null);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpsertAlreadyExisting() throws Exception {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getNameFromYamlFilePath("TestAPMConfig.yaml")).thenReturn("TestAPMConfig");
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);

    APMCVServiceConfiguration cvConfig = APMCVServiceConfiguration.builder().build();
    cvConfig.setUuid("testUUID");
    when(cvConfigurationService.getConfiguration("TestAPMConfig", appId, envId)).thenReturn(cvConfig);
    ChangeContext<APMCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestAPMConfig.yaml").build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml());
    APMCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo("TestAPMConfig");
    assertThat(bean.getUuid()).isEqualTo(cvConfig.getUuid());
  }
}
