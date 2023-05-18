/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.verification;

import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;
import io.harness.serializer.YamlUtils;

import software.wings.beans.yaml.Change;
import software.wings.beans.yaml.ChangeContext;
import software.wings.metrics.MetricType;
import software.wings.service.intfc.datadog.DatadogService;
import software.wings.sm.StateType;
import software.wings.sm.states.DatadogState.Metric;
import software.wings.verification.datadog.DatadogCVConfigurationYaml;
import software.wings.verification.datadog.DatadogCVServiceConfiguration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class DatadogCVConfigurationYamlHandlerTest extends CVConfigurationYamlHandlerTestBase {
  @Inject private DatadogService datadogService;
  DatadogCvConfigurationYamlHandler yamlHandler = new DatadogCvConfigurationYamlHandler();

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    setupTests(yamlHandler);
    FieldUtils.writeField(yamlHandler, "datadogService", datadogService, true);
  }

  private void setBasicInfo(DatadogCVServiceConfiguration cvServiceConfiguration) {
    cvServiceConfiguration.setStateType(StateType.NEW_RELIC);
    cvServiceConfiguration.setAccountId(accountId);
    cvServiceConfiguration.setServiceId(serviceId);
    cvServiceConfiguration.setConnectorId(connectorId);
    cvServiceConfiguration.setEnvId(envId);
    cvServiceConfiguration.setAppId(appId);
    cvServiceConfiguration.setEnabled24x7(true);
    cvServiceConfiguration.setName("TestDDConfig");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testToYaml() {
    final String appId = "appId";
    Map<String, String> dockerMetrics = new HashMap<>();
    dockerMetrics.put("cluster:harness-test", "kubernetes.cpu.usage.total");

    DatadogCVServiceConfiguration cvServiceConfiguration =
        DatadogCVServiceConfiguration.builder().dockerMetrics(dockerMetrics).build();
    setBasicInfo(cvServiceConfiguration);

    DatadogCVConfigurationYaml yaml = yamlHandler.toYaml(cvServiceConfiguration, appId);

    assertThat(yaml.getServiceName()).isEqualTo(serviceName);
    assertThat(yaml.getDockerMetrics().keySet().iterator().next())
        .isEqualTo(cvServiceConfiguration.getDockerMetrics().keySet().iterator().next());
    assertThat(yaml.getDockerMetrics().values()).isEqualTo(cvServiceConfiguration.getDockerMetrics().values());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsert() throws Exception {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestDDConfig.yaml")).thenReturn("TestDDConfig");

    ChangeContext<DatadogCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestDDConfig.yaml").build();
    changeContext.setChange(c);
    Map<String, String> dockerMetrics = new HashMap<>();
    dockerMetrics.put("cluster:harness-test", "kubernetes.cpu.usage.total");

    DatadogCVConfigurationYaml yaml = DatadogCVConfigurationYaml.builder().dockerMetrics(dockerMetrics).build();
    buildYaml(yaml);
    changeContext.setYaml(yaml);
    DatadogCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo("TestDDConfig");
    assertThat(bean.getAppId()).isEqualTo(appId);
    assertThat(bean.getEnvId()).isEqualTo(envId);
    assertThat(bean.getServiceId()).isEqualTo(serviceId);
    assertThat(bean.getUuid()).isNotNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsertDDCustomMetric() throws Exception {
    String yamlString = FileUtils.readFileToString(
        new File("400-rest/src/test/resources/verification/datadogCVConfigCustomYaml.yaml"), Charsets.UTF_8);
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestDDConfig.yaml")).thenReturn("TestDDConfig");

    ChangeContext<DatadogCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestDDConfig.yaml").build();
    changeContext.setChange(c);
    YamlUtils yamlUtils = new YamlUtils();
    DatadogCVConfigurationYaml yaml = yamlUtils.read(yamlString, new TypeReference<DatadogCVConfigurationYaml>() {});
    buildYaml(yaml);
    changeContext.setYaml(yaml);
    DatadogCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo("TestDDConfig");
    assertThat(bean.getAppId()).isEqualTo(appId);
    assertThat(bean.getEnvId()).isEqualTo(envId);
    assertThat(bean.getServiceId()).isEqualTo(serviceId);
    assertThat(bean.getUuid()).isNotNull();
    assertThat(bean.getCustomMetrics().size()).isEqualTo(1);
    assertThat(bean.getCustomMetrics().values().size()).isEqualTo(1);
    Set<Metric> metrics = bean.getCustomMetrics().values().iterator().next();
    assertThat(metrics.iterator().next().getMetricName()).isIn(Arrays.asList("testMetric.test2", "testMetric.test"));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpsertDDCustomMetric_invalidMetrics() throws Exception {
    String yamlString = FileUtils.readFileToString(
        new File("400-rest/src/test/resources/verification/datadogCVConfigCustomYaml.yaml"), Charsets.UTF_8);
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestDDC\nonfig.yaml")).thenReturn("TestDDConfig");

    ChangeContext<DatadogCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestDDConfig.yaml").build();
    changeContext.setChange(c);
    YamlUtils yamlUtils = new YamlUtils();
    DatadogCVConfigurationYaml yaml = yamlUtils.read(yamlString, new TypeReference<DatadogCVConfigurationYaml>() {});
    buildYaml(yaml);
    yaml.getCustomMetrics().get("service:test").get(0).setMlMetricType(MetricType.ERROR.name());
    changeContext.setYaml(yaml);
    assertThatThrownBy(() -> yamlHandler.upsertFromYaml(changeContext, null))
        .isInstanceOf(VerificationOperationException.class);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsertMissingMetrics() throws Exception {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestAppDConfig.yaml")).thenReturn("TestAppDConfig");

    ChangeContext<DatadogCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestAppDConfig.yaml").build();
    changeContext.setChange(c);
    Map<String, String> dockerMetrics = new HashMap<>();
    dockerMetrics.put("cluster:harness-test", "kubernetes.cpu.usage.total");

    DatadogCVConfigurationYaml yaml = DatadogCVConfigurationYaml.builder().dockerMetrics(null).build();
    buildYaml(yaml);
    changeContext.setYaml(yaml);
    DatadogCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsertIncorrectMetrics() throws Exception {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestAppDConfig.yaml")).thenReturn("TestAppDConfig");

    ChangeContext<DatadogCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestAppDConfig.yaml").build();
    changeContext.setChange(c);

    Map<String, String> dockerMetrics = new HashMap<>();
    dockerMetrics.put("cluster:harness-test", "kubernetes.cpu.usage123");

    DatadogCVConfigurationYaml yaml = DatadogCVConfigurationYaml.builder().dockerMetrics(dockerMetrics).build();
    buildYaml(yaml);

    changeContext.setYaml(yaml);
    DatadogCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsertMissingAppFilterHasServiceName() throws Exception {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestAppDConfig.yaml")).thenReturn("TestAppDConfig");

    ChangeContext<DatadogCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestAppDConfig.yaml").build();
    changeContext.setChange(c);
    Map<String, String> dockerMetrics = new HashMap<>();
    dockerMetrics.put("cluster:harness-test", "kubernetes.cpu.usage.total");

    DatadogCVConfigurationYaml yaml = DatadogCVConfigurationYaml.builder().dockerMetrics(dockerMetrics).build();
    buildYaml(yaml);
    changeContext.setYaml(yaml);
    dockerMetrics = new HashMap<>();
    dockerMetrics.put("cluster:harness-test", "kubernetes.cpu.usage.total");
    yaml.setDockerMetrics(dockerMetrics);
    yaml.setDatadogServiceName("todolist");
    DatadogCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getDatadogServiceName()).isEqualTo("todolist");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpsertAlreadyExisting() throws Exception {
    when(yamlHelper.getAppId(anyString(), anyString())).thenReturn(appId);
    when(yamlHelper.getEnvironmentId(anyString(), anyString())).thenReturn(envId);
    when(yamlHelper.getNameFromYamlFilePath("TestDDConfig.yaml")).thenReturn("TestDDConfig");

    DatadogCVServiceConfiguration cvConfig = DatadogCVServiceConfiguration.builder().build();
    cvConfig.setUuid("testUUID");
    when(cvConfigurationService.getConfiguration("TestDDConfig", appId, envId)).thenReturn(cvConfig);
    ChangeContext<DatadogCVConfigurationYaml> changeContext = new ChangeContext<>();
    Change c = Change.Builder.aFileChange().withAccountId(accountId).withFilePath("TestDDConfig.yaml").build();
    changeContext.setChange(c);
    Map<String, String> dockerMetrics = new HashMap<>();
    dockerMetrics.put("cluster:harness-test", "kubernetes.cpu.usage.total");

    DatadogCVConfigurationYaml yaml = DatadogCVConfigurationYaml.builder().dockerMetrics(dockerMetrics).build();
    buildYaml(yaml);
    changeContext.setYaml(yaml);
    DatadogCVServiceConfiguration bean = yamlHandler.upsertFromYaml(changeContext, null);

    assertThat(bean.getName()).isEqualTo("TestDDConfig");
    assertThat(bean.getUuid()).isEqualTo(cvConfig.getUuid());
  }
}
