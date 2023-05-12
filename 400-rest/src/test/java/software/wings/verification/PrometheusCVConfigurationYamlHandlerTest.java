/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.verification;

import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.metrics.MetricType;
import software.wings.service.impl.analysis.TimeSeries;
import software.wings.service.impl.yaml.service.YamlHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.StateType;
import software.wings.verification.prometheus.PrometheusCVServiceConfiguration;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PrometheusCVConfigurationYamlHandlerTest extends WingsBaseTest {
  @Mock YamlHelper yamlHelper;
  @Mock CVConfigurationService cvConfigurationService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock AppService appService;
  @Mock SettingsService settingsService;

  PrometheusCVConfigurationYamlHandler yamlHandler = new PrometheusCVConfigurationYamlHandler();

  private String envId;
  private String serviceId;
  private String appId;
  private String connectorId;
  private String accountId;

  private String serviceName = "serviceName";
  private String connectorName = "prometheusConnector";

  @Before
  public void setup() throws Exception {
    accountId = generateUUID();
    envId = generateUUID();
    serviceId = generateUUID();
    appId = generateUUID();
    connectorId = generateUUID();

    MockitoAnnotations.initMocks(this);

    Service service = Service.builder().uuid(serviceId).name(serviceName).build();
    when(serviceResourceService.getWithDetails(appId, serviceId)).thenReturn(service);
    when(serviceResourceService.getServiceByName(appId, serviceName)).thenReturn(service);

    SettingAttribute settingAttribute =
        SettingAttribute.Builder.aSettingAttribute().withName(connectorName).withUuid(connectorId).build();
    when(settingsService.getSettingAttributeByName(accountId, connectorName)).thenReturn(settingAttribute);
    when(settingsService.get(connectorId)).thenReturn(settingAttribute);

    FieldUtils.writeField(yamlHandler, "environmentService", environmentService, true);
    FieldUtils.writeField(yamlHandler, "serviceResourceService", serviceResourceService, true);
    FieldUtils.writeField(yamlHandler, "settingsService", settingsService, true);
    FieldUtils.writeField(yamlHandler, "yamlHelper", yamlHelper, true);
    FieldUtils.writeField(yamlHandler, "cvConfigurationService", cvConfigurationService, true);
    FieldUtils.writeField(yamlHandler, "appService", appService, true);

    Application app = Application.Builder.anApplication().name(generateUUID()).uuid(appId).build();
    when(appService.get(appId)).thenReturn(app);
  }

  private void setBasicInfo(PrometheusCVServiceConfiguration cvServiceConfiguration) {
    cvServiceConfiguration.setStateType(StateType.PROMETHEUS);
    cvServiceConfiguration.setAccountId(accountId);
    cvServiceConfiguration.setServiceId(serviceId);
    cvServiceConfiguration.setConnectorId(connectorId);
    cvServiceConfiguration.setEnvId(envId);
    cvServiceConfiguration.setAppId(appId);
    cvServiceConfiguration.setEnabled24x7(true);
    cvServiceConfiguration.setName("TestPrometheusConfig");
  }

  private PrometheusCVServiceConfiguration.PrometheusCVConfigurationYaml buildYaml(List<TimeSeries> timeSeriesList) {
    PrometheusCVServiceConfiguration.PrometheusCVConfigurationYaml yaml =
        PrometheusCVServiceConfiguration.PrometheusCVConfigurationYaml.builder().timeSeriesList(timeSeriesList).build();
    yaml.setServiceName(serviceName);
    yaml.setConnectorName(connectorName);
    return yaml;
  }

  private void mockYamlHelper() {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestPrometheusConfig.yaml")).thenReturn("TestPrometheusConfig");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testToYaml() {
    final String appId = "appId";
    List<TimeSeries> timeSeriesList = new ArrayList<>();
    String url = "http://35.247.2.110:8080?hostname=$hostName&startTime=$startTime&endTime=$endTime";
    timeSeriesList.add(
        TimeSeries.builder().metricName("Metric1").metricType("Error").txnName("Test1").url(url).build());

    PrometheusCVServiceConfiguration cvServiceConfiguration =
        PrometheusCVServiceConfiguration.builder().timeSeriesToAnalyze(timeSeriesList).build();
    setBasicInfo(cvServiceConfiguration);

    PrometheusCVServiceConfiguration.PrometheusCVConfigurationYaml yaml =
        yamlHandler.toYaml(cvServiceConfiguration, appId);

    assertThat(yaml.getServiceName()).isEqualTo(serviceName);
    assertThat(yaml.getTimeSeriesList()).isEqualTo(timeSeriesList);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testUpsert() throws Exception {
    mockYamlHelper();

    List<TimeSeries> timeSeriesList = new ArrayList<>();
    String url = "container_cpu_usage_seconds_total{container_name=\"harness-example\"}";
    timeSeriesList.add(TimeSeries.builder()
                           .metricName("Metric1")
                           .metricType(MetricType.ERROR.name())
                           .txnName("Test1")
                           .url(url)
                           .build());

    ChangeContext<PrometheusCVServiceConfiguration.PrometheusCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestPrometheusConfig.yaml").build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml(timeSeriesList));
    try {
      yamlHandler.upsertFromYaml(changeContext, null);
      fail("parsed invalid metric list");
    } catch (WingsException e) {
      assertThat(e.getMessage())
          .isEqualTo("Test1 has error metrics [Metric1] and/or response time metrics [] but no throughput metrics.\n");
    }

    timeSeriesList.add(TimeSeries.builder()
                           .metricName("Metric2")
                           .metricType(MetricType.THROUGHPUT.name())
                           .txnName("Test1")
                           .url(url)
                           .build());

    changeContext = new ChangeContext<>();
    c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestPrometheusConfig.yaml").build();
    changeContext.setChange(c);
    changeContext.setYaml(buildYaml(timeSeriesList));
    PrometheusCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);
    assertThat(bean.getName()).isEqualTo("TestPrometheusConfig");
    assertThat(bean.getAppId()).isEqualTo(appId);
    assertThat(bean.getEnvId()).isEqualTo(envId);
    assertThat(bean.getServiceId()).isEqualTo(serviceId);
    assertThat(bean.getTimeSeriesToAnalyze()).isEqualTo(timeSeriesList);
    assertThat(bean.getUuid()).isNotNull();
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpsertBadMetricsEmptyMetrics() throws Exception {
    mockYamlHelper();

    List<TimeSeries> timeSeriesList = new ArrayList<>();

    ChangeContext<PrometheusCVServiceConfiguration.PrometheusCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestPrometheusConfig.yaml").build();
    changeContext.setChange(c);
    PrometheusCVServiceConfiguration.PrometheusCVConfigurationYaml yaml = buildYaml(timeSeriesList);
    changeContext.setYaml(yaml);
    yamlHandler.upsertFromYaml(changeContext, null);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpsertBadMetricsBadUrl() throws Exception {
    mockYamlHelper();

    List<TimeSeries> timeSeriesList = new ArrayList<>();
    String url = "http://35.247.2.110:8080?hostname=$hostName";
    timeSeriesList.add(
        TimeSeries.builder().metricName("Metric1").metricType("Error").txnName("Test1").url(url).build());

    ChangeContext<PrometheusCVServiceConfiguration.PrometheusCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestPrometheusConfig.yaml").build();
    changeContext.setChange(c);
    PrometheusCVServiceConfiguration.PrometheusCVConfigurationYaml yaml = buildYaml(timeSeriesList);
    changeContext.setYaml(yaml);
    yamlHandler.upsertFromYaml(changeContext, null);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpsertBadMetricsDuplicate() throws Exception {
    mockYamlHelper();

    List<TimeSeries> timeSeriesList = new ArrayList<>();
    String url = "http://35.247.2.110:8080?hostname=$hostName&startTime=$startTime&endTime=$endTime";
    timeSeriesList.add(
        TimeSeries.builder().metricName("Metric1").metricType("Error").txnName("Test1").url(url).build());
    timeSeriesList.add(TimeSeries.builder().metricName("Metric1").metricType("Logs").txnName("Test2").url(url).build());

    ChangeContext<PrometheusCVServiceConfiguration.PrometheusCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestPrometheusConfig.yaml").build();
    changeContext.setChange(c);
    PrometheusCVServiceConfiguration.PrometheusCVConfigurationYaml yaml = buildYaml(timeSeriesList);
    changeContext.setYaml(yaml);
    yamlHandler.upsertFromYaml(changeContext, null);
  }
}
