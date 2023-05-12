/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.verification;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.NANDAN;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SOWMYA;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.common.VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES;
import static software.wings.common.VerificationConstants.SERVICE_GUAARD_LIMIT;
import static software.wings.service.impl.verification.CVConfigurationServiceImplTestBase.createCustomLogsConfig;
import static software.wings.sm.StateType.APM_VERIFICATION;
import static software.wings.utils.StackDriverUtils.createStackDriverConfig;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.VerificationOperationException;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.YamlUtils;

import software.wings.WingsBaseTest;
import software.wings.alerts.AlertStatus;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.Account;
import software.wings.beans.ApmMetricCollectionInfo;
import software.wings.beans.ApmResponseMapping;
import software.wings.beans.Application;
import software.wings.beans.DatadogConfig;
import software.wings.beans.Environment;
import software.wings.beans.Event;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.Alert.AlertKeys;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.UsageLimitExceededAlert;
import software.wings.beans.alert.cv.ContinuousVerificationAlertData;
import software.wings.beans.alert.cv.ContinuousVerificationDataCollectionAlert;
import software.wings.beans.apm.Method;
import software.wings.beans.apm.ResponseType;
import software.wings.metrics.MetricType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.ContinuousVerificationService;
import software.wings.service.impl.analysis.FeedbackPriority;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.analysis.TimeSeries;
import software.wings.service.impl.analysis.TimeSeriesKeyTransactions;
import software.wings.service.impl.analysis.TimeSeriesMetricTemplates;
import software.wings.service.impl.analysis.TimeSeriesMetricTemplates.TimeSeriesMetricTemplatesKeys;
import software.wings.service.impl.cloudwatch.CloudWatchMetric;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.NewRelicMetricValueDefinition;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.sm.StateType;
import software.wings.sm.states.DatadogState;
import software.wings.sm.states.DatadogState.Metric;
import software.wings.sm.states.StackDriverState;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfiguration.CVConfigurationKeys;
import software.wings.verification.apm.APMCVServiceConfiguration;
import software.wings.verification.cloudwatch.CloudWatchCVServiceConfiguration;
import software.wings.verification.datadog.DatadogCVServiceConfiguration;
import software.wings.verification.log.CustomLogCVServiceConfiguration;
import software.wings.verification.log.LogsCVConfiguration;
import software.wings.verification.prometheus.PrometheusCVServiceConfiguration;
import software.wings.verification.stackdriver.StackDriverMetricCVConfiguration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import io.fabric8.utils.Lists;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@Slf4j
public class CVConfigurationServiceImplTest extends WingsBaseTest {
  @Mock private FeatureFlagService featureFlagService;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject private AlertService alertService;
  @Inject private ContinuousVerificationService continuousVerificationService;
  @Inject private SettingsService settingsService;
  @Inject private AccountService accountService;
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceResourceService serviceResourceService;
  @Mock private YamlPushService yamlPushService;
  @Inject private HPersistence persistence;

  private String accountId;
  private String appId;

  @Before
  public void setupTest() throws Exception {
    accountId = persistence.save(anAccount().withAccountName(generateUuid()).withCompanyName(generateUuid()).build());
    appId = persistence.save(Application.Builder.anApplication().name(generateUuid()).accountId(accountId).build());
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(cvConfigurationService, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(cvConfigurationService, "yamlPushService", yamlPushService, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricTemplateAppDynamics() {
    Map<String, TimeSeriesMetricDefinition> actualDefinitions =
        cvConfigurationService.getMetricDefinitionMap(StateType.APP_DYNAMICS, null);
    Map<String, TimeSeriesMetricDefinition> expectedDefinitions =
        NewRelicMetricValueDefinition.APP_DYNAMICS_24X7_VALUES_TO_ANALYZE;
    assertThat(expectedDefinitions).isEqualTo(actualDefinitions);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricTemplateNewRelic() {
    Map<String, TimeSeriesMetricDefinition> actualDefinitions =
        cvConfigurationService.getMetricDefinitionMap(StateType.NEW_RELIC, null);
    assertThat(actualDefinitions).isEmpty();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricTemplateDynaTrace() {
    Map<String, TimeSeriesMetricDefinition> actualDefinitions =
        cvConfigurationService.getMetricDefinitionMap(StateType.DYNA_TRACE, null);
    assertThat(actualDefinitions).isEmpty();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricTemplatePrometheus() {
    List<TimeSeries> timeSeriesToAnalyze = new ArrayList<>();

    timeSeriesToAnalyze.add(
        TimeSeries.builder().metricName("cpu").metricType(MetricType.INFRA.name()).txnName("cpu").url("url1").build());

    timeSeriesToAnalyze.add(TimeSeries.builder()
                                .metricName("error")
                                .metricType(MetricType.ERROR.name())
                                .txnName("error")
                                .url("url2")
                                .build());

    timeSeriesToAnalyze.add(TimeSeries.builder()
                                .metricName("throughput")
                                .metricType(MetricType.THROUGHPUT.name())
                                .txnName("throughput")
                                .url("url3")
                                .build());

    PrometheusCVServiceConfiguration cvServiceConfiguration =
        PrometheusCVServiceConfiguration.builder().timeSeriesToAnalyze(timeSeriesToAnalyze).build();

    Map<String, TimeSeriesMetricDefinition> actualDefinitions =
        cvConfigurationService.getMetricDefinitionMap(StateType.PROMETHEUS, cvServiceConfiguration);
    assertThat(actualDefinitions).hasSize(3);

    timeSeriesToAnalyze.forEach(timeSeries -> {
      TimeSeriesMetricDefinition metricDefinition = actualDefinitions.get(timeSeries.getMetricName());
      assertThat(metricDefinition).isNotNull();
      assertThat(metricDefinition.getMetricName()).isEqualTo(timeSeries.getMetricName());
      assertThat(metricDefinition.getMetricType().name()).isEqualTo(timeSeries.getMetricType());
    });
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateConfiguration_prometheusWithoutError() {
    List<TimeSeries> timeSeriesToAnalyze = new ArrayList<>();

    timeSeriesToAnalyze.add(TimeSeries.builder()
                                .metricName("cpu")
                                .metricType(MetricType.INFRA.name())
                                .txnName("test")
                                .url("url1{pod_name=x}")
                                .build());

    timeSeriesToAnalyze.add(TimeSeries.builder()
                                .metricName("error")
                                .metricType(MetricType.ERROR.name())
                                .txnName("test")
                                .url("url2{pod_name=x}")
                                .build());

    timeSeriesToAnalyze.add(TimeSeries.builder()
                                .metricName("throughput")
                                .metricType(MetricType.THROUGHPUT.name())
                                .txnName("test")
                                .url("url3{pod_name=x}")
                                .build());

    PrometheusCVServiceConfiguration cvServiceConfiguration =
        PrometheusCVServiceConfiguration.builder().timeSeriesToAnalyze(timeSeriesToAnalyze).build();
    addBasePropertiesToCVConfig(cvServiceConfiguration, StateType.PROMETHEUS);
    persistence.save(cvServiceConfiguration);

    timeSeriesToAnalyze.removeIf(ts -> ts.getMetricType().equals(MetricType.INFRA.name()));

    cvServiceConfiguration.setTimeSeriesToAnalyze(timeSeriesToAnalyze);
    cvConfigurationService.updateConfiguration(
        accountId, appId, StateType.PROMETHEUS, cvServiceConfiguration, cvServiceConfiguration.getUuid());

    PrometheusCVServiceConfiguration savedConfig =
        (PrometheusCVServiceConfiguration) persistence.get(CVConfiguration.class, cvServiceConfiguration.getUuid());
    assertThat(savedConfig.getTimeSeriesToAnalyze()).isEqualTo(timeSeriesToAnalyze);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateConfiguration_pushYamlChangeSet() {
    List<TimeSeries> timeSeriesToAnalyze = new ArrayList<>();

    timeSeriesToAnalyze.add(TimeSeries.builder()
                                .metricName("cpu")
                                .metricType(MetricType.INFRA.name())
                                .txnName("test")
                                .url("url1{pod_name=x}")
                                .build());

    timeSeriesToAnalyze.add(TimeSeries.builder()
                                .metricName("error")
                                .metricType(MetricType.ERROR.name())
                                .txnName("test")
                                .url("url2{pod_name=x}")
                                .build());

    timeSeriesToAnalyze.add(TimeSeries.builder()
                                .metricName("throughput")
                                .metricType(MetricType.THROUGHPUT.name())
                                .txnName("test")
                                .url("url3{pod_name=x}")
                                .build());

    PrometheusCVServiceConfiguration cvServiceConfiguration =
        PrometheusCVServiceConfiguration.builder().timeSeriesToAnalyze(timeSeriesToAnalyze).build();
    addBasePropertiesToCVConfig(cvServiceConfiguration, StateType.PROMETHEUS);
    persistence.save(cvServiceConfiguration);

    PrometheusCVServiceConfiguration newConfig = (PrometheusCVServiceConfiguration) cvServiceConfiguration.deepCopy();
    newConfig.setEnabled24x7(true);
    cvConfigurationService.updateConfiguration(
        accountId, appId, StateType.PROMETHEUS, newConfig, cvServiceConfiguration.getUuid());

    verify(yamlPushService, Mockito.times(1))
        .pushYamlChangeSet(
            eq(accountId), eq(cvServiceConfiguration), eq(newConfig), eq(Event.Type.UPDATE), eq(false), eq(false));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateConfiguration_prometheusWithError() {
    List<TimeSeries> timeSeriesToAnalyze = new ArrayList<>();

    timeSeriesToAnalyze.add(TimeSeries.builder()
                                .metricName("cpu")
                                .metricType(MetricType.INFRA.name())
                                .txnName("test")
                                .url("url1{pod_name=x}")
                                .build());

    timeSeriesToAnalyze.add(TimeSeries.builder()
                                .metricName("error")
                                .metricType(MetricType.ERROR.name())
                                .txnName("test")
                                .url("url2{pod_name=x}")
                                .build());

    timeSeriesToAnalyze.add(TimeSeries.builder()
                                .metricName("throughput")
                                .metricType(MetricType.THROUGHPUT.name())
                                .txnName("test")
                                .url("url3{pod_name=x}")
                                .build());

    PrometheusCVServiceConfiguration cvServiceConfiguration =
        PrometheusCVServiceConfiguration.builder().timeSeriesToAnalyze(timeSeriesToAnalyze).build();
    addBasePropertiesToCVConfig(cvServiceConfiguration, StateType.PROMETHEUS);
    persistence.save(cvServiceConfiguration);

    timeSeriesToAnalyze.removeIf(ts -> ts.getMetricType().equals(MetricType.THROUGHPUT.name()));

    cvServiceConfiguration.setTimeSeriesToAnalyze(timeSeriesToAnalyze);
    assertThatThrownBy(()
                           -> cvConfigurationService.updateConfiguration(accountId, appId, StateType.PROMETHEUS,
                               cvServiceConfiguration, cvServiceConfiguration.getUuid()))
        .isInstanceOf(VerificationOperationException.class);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricTemplateDatadog() {
    Map<String, String> dockerMetrics = new HashMap<>();
    dockerMetrics.put("filter1", "docker.cpu.usage, docker.mem.rss");
    dockerMetrics.put("filter2", "docker.cpu.throttled");

    Map<String, String> ecsMetrics = new HashMap<>();
    ecsMetrics.put("filter3", "ecs.fargate.cpu.user, ecs.fargate.mem.rss, ecs.fargate.mem.usage");

    DatadogCVServiceConfiguration cvServiceConfiguration = DatadogCVServiceConfiguration.builder()
                                                               .dockerMetrics(dockerMetrics)
                                                               .ecsMetrics(ecsMetrics)
                                                               .customMetrics(new HashMap<>())
                                                               .build();

    Map<String, TimeSeriesMetricDefinition> actualDefinitions =
        cvConfigurationService.getMetricDefinitionMap(StateType.DATA_DOG, cvServiceConfiguration);

    assertThat(actualDefinitions).hasSize(6);

    List<String> expectedKeySet = Lists.newArrayList("Docker CPU Usage", "Docker RSS Memory (%)",
        "Docker CPU Throttled", "ECS Container CPU Usage", "ECS Container RSS Memory", "ECS Container Memory Usage");
    Collections.sort(expectedKeySet);

    List<String> actualKeySet = new ArrayList<>(actualDefinitions.keySet());
    Collections.sort(actualKeySet);

    assertThat(expectedKeySet).isEqualTo(actualKeySet);

    expectedKeySet.forEach(key -> {
      TimeSeriesMetricDefinition definition = actualDefinitions.get(key);

      assertThat(definition.getMetricName()).isEqualTo(key);
      assertThat(definition.getMetricType()).isEqualTo(MetricType.INFRA);
    });
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetMetricsCloudWatch() {
    List<CloudWatchMetric> cloudWatchMetrics = new ArrayList<>();

    cloudWatchMetrics.add(CloudWatchMetric.builder().metricName("Latency").metricType(MetricType.ERROR.name()).build());

    cloudWatchMetrics.add(
        CloudWatchMetric.builder().metricName("RequestCount").metricType(MetricType.THROUGHPUT.name()).build());

    cloudWatchMetrics.add(
        CloudWatchMetric.builder().metricName("EstimatedProcessedBytes").metricType(MetricType.VALUE.name()).build());

    cloudWatchMetrics.add(
        CloudWatchMetric.builder().metricName("CPUReservation").metricType(MetricType.VALUE.name()).build());

    cloudWatchMetrics.add(
        CloudWatchMetric.builder().metricName("CPUUtilization").metricType(MetricType.VALUE.name()).build());

    cloudWatchMetrics.add(
        CloudWatchMetric.builder().metricName("DiskReadBytes").metricType(MetricType.VALUE.name()).build());

    Map<String, List<CloudWatchMetric>> loadBalancerMetric = new HashMap<>();
    loadBalancerMetric.put("filter1", cloudWatchMetrics.subList(0, 2));
    loadBalancerMetric.put("filter2", cloudWatchMetrics.subList(2, 3));

    Map<String, List<CloudWatchMetric>> ecsMetrics = new HashMap<>();
    ecsMetrics.put("filter3", cloudWatchMetrics.subList(3, 4));

    List<CloudWatchMetric> ec2Metrics = cloudWatchMetrics.subList(4, 6);

    CloudWatchCVServiceConfiguration cvServiceConfiguration = CloudWatchCVServiceConfiguration.builder()
                                                                  .loadBalancerMetrics(loadBalancerMetric)
                                                                  .ecsMetrics(ecsMetrics)
                                                                  .ec2Metrics(ec2Metrics)
                                                                  .build();

    Map<String, TimeSeriesMetricDefinition> actualDefinitions =
        cvConfigurationService.getMetricDefinitionMap(StateType.CLOUD_WATCH, cvServiceConfiguration);

    assertThat(actualDefinitions).hasSize(6);

    cloudWatchMetrics.forEach(metric -> {
      TimeSeriesMetricDefinition definition = actualDefinitions.get(metric.getMetricName());
      assertThat(definition.getMetricName()).isEqualTo(metric.getMetricName());
      assertThat(definition.getMetricType().name()).isEqualTo(metric.getMetricType());
    });
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testInvalidCvConfig() {
    cvConfigurationService.resetBaseline(generateUuid(), generateUuid(), new LogsCVConfiguration());
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNullCvConfig() {
    final LogsCVConfiguration logsCVConfig = createLogsCVConfig(false);
    String cvConfigId = persistence.save(logsCVConfig);
    cvConfigurationService.resetBaseline(logsCVConfig.getAppId(), cvConfigId, null);
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testUpdateWithNoBaselineSet() {
    final LogsCVConfiguration logsCVConfig = createLogsCVConfig(true);
    String cvConfigId = persistence.save(logsCVConfig);

    final LogsCVConfiguration updatedConfig = createLogsCVConfig(true);
    updatedConfig.setBaselineStartMinute(0);
    updatedConfig.setBaselineEndMinute(0);

    cvConfigurationService.resetBaseline(logsCVConfig.getAppId(), cvConfigId, updatedConfig);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateStackDriverMetrics() throws Exception {
    StackDriverMetricCVConfiguration configuration = createStackDriverConfig(accountId);

    String cvConfigId = cvConfigurationService.saveConfiguration(
        accountId, "LQWs27mPS7OrwCwDmT8aBA", StateType.STACK_DRIVER, configuration);

    CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(cvConfigId);
    StackDriverMetricCVConfiguration stackDriverMetricCVConfiguration =
        (StackDriverMetricCVConfiguration) cvConfiguration;
    assertThat(stackDriverMetricCVConfiguration).isNotNull();
    assertThat(stackDriverMetricCVConfiguration.getMetricDefinitions()).isNotNull();
    assertThat(stackDriverMetricCVConfiguration.getMetricDefinitions().size()).isEqualTo(3);
    assertThat(stackDriverMetricCVConfiguration.getMetricDefinitions().get(0).getFilter()).isNotBlank();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetConfiguration_returnsNullForInvalidCVConfigId() {
    CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(generateUuid());
    assertThat(cvConfiguration).isNull();
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateStackDriverMetricsWithInvalidFields() throws Exception {
    StackDriverMetricCVConfiguration configuration = createStackDriverConfig(accountId);
    configuration.getMetricDefinitions().get(0).setMetricType("ERROR");
    String cvConfigId = cvConfigurationService.saveConfiguration(
        accountId, "LQWs27mPS7OrwCwDmT8aBA", StateType.STACK_DRIVER, configuration);

    CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(cvConfigId);
    StackDriverMetricCVConfiguration stackDriverMetricCVConfiguration =
        (StackDriverMetricCVConfiguration) cvConfiguration;
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testResetWithBaselineSet() {
    final LogsCVConfiguration logsCVConfig = createLogsCVConfig(true);
    String cvConfigId = persistence.save(logsCVConfig);

    final LogsCVConfiguration updatedConfig = createLogsCVConfig(false);
    updatedConfig.setBaselineStartMinute(301);
    updatedConfig.setBaselineEndMinute(360);

    LogsCVConfiguration fetchedConfig = persistence.get(LogsCVConfiguration.class, cvConfigId);
    assertThat(fetchedConfig.isEnabled24x7()).isTrue();

    cvConfigId = cvConfigurationService.resetBaseline(logsCVConfig.getAppId(), cvConfigId, updatedConfig);
    fetchedConfig = persistence.get(LogsCVConfiguration.class, cvConfigId);
    assertThat(fetchedConfig.isEnabled24x7()).isTrue();
    assertThat(fetchedConfig.getBaselineStartMinute()).isEqualTo(updatedConfig.getBaselineStartMinute());
    assertThat(fetchedConfig.getBaselineEndMinute()).isEqualTo(updatedConfig.getBaselineEndMinute());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpdateStackDriverMetrics() throws Exception {
    testCreateStackDriverMetrics();
    StackDriverMetricCVConfiguration configuration = persistence.createQuery(StackDriverMetricCVConfiguration.class)
                                                         .filter(CVConfigurationKeys.stateType, StateType.STACK_DRIVER)
                                                         .get();

    configuration.getMetricDefinitions().get(0).setMetricName("UpdatedMetricName");

    cvConfigurationService.updateConfiguration(
        accountId, "LQWs27mPS7OrwCwDmT8aBA", StateType.STACK_DRIVER, configuration, configuration.getUuid());

    StackDriverMetricCVConfiguration updatedConfig =
        persistence.get(StackDriverMetricCVConfiguration.class, configuration.getUuid());

    assertThat(updatedConfig).isNotNull();
    assertThat(updatedConfig.getMetricDefinitions()).isNotNull();
    assertThat(updatedConfig.getMetricDefinitions().get(0).getMetricName()).isEqualTo("UpdatedMetricName");
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpdateStackDriverMetricsWithInvalidFields() throws Exception {
    testCreateStackDriverMetrics();
    StackDriverMetricCVConfiguration configuration = persistence.createQuery(StackDriverMetricCVConfiguration.class)
                                                         .filter(CVConfigurationKeys.stateType, StateType.STACK_DRIVER)
                                                         .get();

    configuration.getMetricDefinitions().get(0).setMetricType("THROUGHPUT");
    cvConfigurationService.updateConfiguration(
        accountId, "LQWs27mPS7OrwCwDmT8aBA", StateType.STACK_DRIVER, configuration, configuration.getUuid());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testResetBaselineWithData() {
    final LogsCVConfiguration logsCVConfig = createLogsCVConfig(true);
    String cvConfigId = persistence.save(logsCVConfig);
    int numOfAnalysisRecords = 17;
    for (int i = 0; i < numOfAnalysisRecords * CRON_POLL_INTERVAL_IN_MINUTES; i += CRON_POLL_INTERVAL_IN_MINUTES) {
      persistence.save(
          LogMLAnalysisRecord.builder().logCollectionMinute(i).cvConfigId(cvConfigId).accountId(accountId).build());
      persistence.save(LearningEngineAnalysisTask.builder().cvConfigId(cvConfigId).build());
    }

    assertThat(persistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority).count())
        .isEqualTo(numOfAnalysisRecords);
    assertThat(persistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).count())
        .isEqualTo(numOfAnalysisRecords);
    int numOfAnalysisToKeep = 8;

    final LogsCVConfiguration updatedConfig = createLogsCVConfig(true);
    updatedConfig.setBaselineStartMinute(numOfAnalysisToKeep * CRON_POLL_INTERVAL_IN_MINUTES);
    updatedConfig.setBaselineEndMinute(numOfAnalysisRecords * CRON_POLL_INTERVAL_IN_MINUTES);

    String newCvConfigId = cvConfigurationService.resetBaseline(logsCVConfig.getAppId(), cvConfigId, updatedConfig);
    LogsCVConfiguration fetchedConfig = persistence.get(LogsCVConfiguration.class, newCvConfigId);
    assertThat(fetchedConfig.isEnabled24x7()).isTrue();
    assertThat(fetchedConfig.getBaselineStartMinute()).isEqualTo(updatedConfig.getBaselineStartMinute());
    assertThat(fetchedConfig.getBaselineEndMinute()).isEqualTo(updatedConfig.getBaselineEndMinute());

    assertThat(persistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority).count()).isEqualTo(0);
    assertThat(persistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).count())
        .isEqualTo(numOfAnalysisToKeep);

    sleep(ofSeconds(2));
    persistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).asList().forEach(logMLAnalysisRecord -> {
      assertThat(logMLAnalysisRecord.getCvConfigId()).isEqualTo(newCvConfigId);
      assertThat(logMLAnalysisRecord.isDeprecated()).isTrue();
    });
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateDefinitionsForMetricStackdriver() throws Exception {
    StackDriverMetricCVConfiguration configuration = createStackDriverConfig(accountId);
    configuration.setMetricFilters();
    Map<String, String> invalidFields =
        StackDriverState.validateMetricDefinitions(configuration.getMetricDefinitions(), true);
    assertThat(invalidFields).isEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateDefinitionsForMetricStackdriverInvalidSetup() throws Exception {
    StackDriverMetricCVConfiguration configuration = createStackDriverConfig(accountId);
    configuration.getMetricDefinitions().get(0).setMetricType("ERROR");
    configuration.setMetricFilters();
    Map<String, String> invalidFields =
        StackDriverState.validateMetricDefinitions(configuration.getMetricDefinitions(), true);
    assertThat(invalidFields).isNotEmpty();
    assertThat(invalidFields.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateDefinitionsForMetricStackdriverNoFilterJson() throws Exception {
    StackDriverMetricCVConfiguration configuration = createStackDriverConfig(accountId);
    Map<String, String> invalidFields =
        StackDriverState.validateMetricDefinitions(configuration.getMetricDefinitions(), true);
    assertThat(invalidFields).isNotEmpty();
    assertThat(invalidFields.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetMetricTypeForMetricStackdriver() throws Exception {
    StackDriverMetricCVConfiguration configuration = createStackDriverConfig(accountId);
    String metricType = StackDriverState.getMetricTypeForMetric(configuration, "metricName");
    assertThat(metricType).isEqualTo(MetricType.INFRA.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateCustomLogsConfig() throws Exception {
    CustomLogCVServiceConfiguration configuration = createCustomLogsConfig(accountId);

    String cvConfigId = cvConfigurationService.saveConfiguration(
        accountId, "LQWs27mPS7OrwCwDmT8aBA", StateType.LOG_VERIFICATION, configuration);

    CVConfiguration cvConfiguration = cvConfigurationService.getConfiguration(cvConfigId);
    CustomLogCVServiceConfiguration customLogCVServiceConfiguration = (CustomLogCVServiceConfiguration) cvConfiguration;
    assertThat(customLogCVServiceConfiguration).isNotNull();
    assertThat(customLogCVServiceConfiguration.getLogCollectionInfo()).isNotNull();
    assertThat(customLogCVServiceConfiguration.getQuery()).isNotNull();
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateCustomLogsConfigNoTimeData() throws Exception {
    CustomLogCVServiceConfiguration configuration = createCustomLogsConfig(accountId);

    configuration.getLogCollectionInfo().setCollectionUrl("testUrl");
    String cvConfigId = cvConfigurationService.saveConfiguration(
        accountId, "LQWs27mPS7OrwCwDmT8aBA", StateType.LOG_VERIFICATION, configuration);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testUpdateCustomLogConfig() throws Exception {
    testCreateCustomLogsConfig();
    CustomLogCVServiceConfiguration configuration =
        persistence.createQuery(CustomLogCVServiceConfiguration.class)
            .filter(CVConfigurationKeys.stateType, StateType.LOG_VERIFICATION)
            .get();

    configuration.getLogCollectionInfo().setCollectionUrl("updated url ${start_time} ${end_time}");

    cvConfigurationService.updateConfiguration(configuration, configuration.getAppId());

    CustomLogCVServiceConfiguration updatedConfig = cvConfigurationService.getConfiguration(configuration.getUuid());
    assertThat(updatedConfig).isNotNull();
    assertThat(updatedConfig.getLogCollectionInfo()).isNotNull();
    assertThat(updatedConfig.getQuery()).isNotNull();
    assertThat(updatedConfig.getLogCollectionInfo().getCollectionUrl())
        .isEqualTo("updated url ${start_time} ${end_time}");
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testUpdateConfiguration_checkIfAlertPriorityUpdated() throws Exception {
    LogsCVConfiguration logsCVConfiguration = createLogsCVConfig(true);
    logsCVConfiguration.setUuid(generateUuid());
    cvConfigurationService.saveToDatabase(logsCVConfiguration, true);

    logsCVConfiguration.setAlertPriority(FeedbackPriority.P3);
    cvConfigurationService.updateConfiguration(logsCVConfiguration, logsCVConfiguration.getAppId());

    LogsCVConfiguration updatedConfig = cvConfigurationService.getConfiguration(logsCVConfiguration.getUuid());
    assertThat(updatedConfig).isNotNull();
    assertThat(updatedConfig.getAlertPriority()).isEqualTo(FeedbackPriority.P3);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testUpdateConfiguration_UpdateMetrics() throws Exception {
    StackDriverMetricCVConfiguration stackDriverConfig = createStackDriverConfig(accountId);
    stackDriverConfig.setUuid(generateUuid());
    CVConfiguration savedConfig = cvConfigurationService.saveToDatabase(stackDriverConfig, true);

    String updatedMetricName = "UpdatedMetricName";
    stackDriverConfig.getMetricDefinitions().get(0).setMetricName(updatedMetricName);
    stackDriverConfig.setUuid(savedConfig.getUuid());
    cvConfigurationService.updateConfiguration(stackDriverConfig, stackDriverConfig.getAppId());

    StackDriverMetricCVConfiguration updatedConfig =
        (StackDriverMetricCVConfiguration) persistence.get(CVConfiguration.class, savedConfig.getUuid());
    assertThat(updatedConfig).isNotNull();
    assertThat(updatedConfig.getMetricDefinitions().size()).isEqualTo(3);
    assertThat(updatedConfig.getMetricDefinitions().get(0).getMetricName()).isEqualTo(updatedMetricName);

    TimeSeriesMetricTemplates definition = persistence.createQuery(TimeSeriesMetricTemplates.class, excludeAuthority)
                                               .filter(TimeSeriesMetricTemplatesKeys.cvConfigId, savedConfig.getUuid())
                                               .get();

    assertThat(definition).isNotNull();
    assertThat(definition.getMetricTemplates().size()).isEqualTo(3);
    assertThat(definition.getMetricTemplates().keySet())
        .isEqualTo(new HashSet<>(Lists.newArrayList("metricName2", "metricName3", updatedMetricName)));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateDDCustomConfigWithDots() throws Exception {
    DatadogCVServiceConfiguration configuration = createDatadogCVConfiguration(true, true);
    cvConfigurationService.saveConfiguration(
        configuration.getAccountId(), configuration.getAppId(), StateType.DATA_DOG, configuration);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testDeleteAlertDuringPrune() throws Exception {
    String cvConfigId = generateUuid();
    DatadogCVServiceConfiguration configuration = createDatadogCVConfiguration(true, true);
    configuration.setUuid(cvConfigId);
    persistence.save(configuration);

    alertService.openAlert(accountId, appId, AlertType.CONTINUOUS_VERIFICATION_DATA_COLLECTION_ALERT,
        ContinuousVerificationDataCollectionAlert.builder()
            .cvConfiguration(configuration)
            .message("test alert message")
            .build());

    Alert alert = persistence.createQuery(Alert.class)
                      .filter(AlertKeys.type, AlertType.CONTINUOUS_VERIFICATION_DATA_COLLECTION_ALERT)
                      .get();

    assertThat(alert).isNotNull();
    assertThat(alert.getStatus()).isEqualTo(AlertStatus.Open);

    // test behavior
    cvConfigurationService.pruneByEnvironment(appId, configuration.getEnvId());

    // verify
    CVConfiguration cvConfiguration = persistence.get(CVConfiguration.class, cvConfigId);

    assertThat(cvConfiguration).isNull();

    alert = persistence.createQuery(Alert.class)
                .filter(AlertKeys.type, AlertType.CONTINUOUS_VERIFICATION_DATA_COLLECTION_ALERT)
                .get();

    assertThat(alert.getStatus()).isEqualTo(AlertStatus.Closed);
  }

  private LogsCVConfiguration createLogsCVConfig(boolean enabled24x7) {
    LogsCVConfiguration logsCVConfiguration = new LogsCVConfiguration();
    logsCVConfiguration.setName("Config 1");
    logsCVConfiguration.setAccountId(accountId);
    logsCVConfiguration.setAppId(generateUuid());
    logsCVConfiguration.setEnvId(generateUuid());
    logsCVConfiguration.setServiceId(generateUuid());
    logsCVConfiguration.setEnabled24x7(enabled24x7);
    logsCVConfiguration.setConnectorId(generateUuid());
    logsCVConfiguration.setBaselineStartMinute(100);
    logsCVConfiguration.setBaselineEndMinute(200);
    logsCVConfiguration.setAlertEnabled(false);
    logsCVConfiguration.setAlertThreshold(0.1);
    logsCVConfiguration.setQuery(generateUuid());
    logsCVConfiguration.setStateType(StateType.SUMO);
    logsCVConfiguration.setAlertPriority(FeedbackPriority.P5);
    return logsCVConfiguration;
  }

  private void addBasePropertiesToCVConfig(CVConfiguration cvConfiguration, StateType stateType) {
    cvConfiguration.setName(generateUuid());
    cvConfiguration.setAccountId(accountId);
    cvConfiguration.setConnectorId(generateUuid());
    cvConfiguration.setEnvId(generateUuid());
    cvConfiguration.setServiceId(generateUuid());
    cvConfiguration.setStateType(stateType);
    cvConfiguration.setAnalysisTolerance(AnalysisTolerance.LOW);
    cvConfiguration.setAppId(appId);
  }

  private DatadogCVServiceConfiguration createDatadogCVConfiguration(boolean enabled24x7, boolean withDot)
      throws Exception {
    YamlUtils yamlUtils = new YamlUtils();
    URL url = DatadogState.class.getResource("/apm/datadog_metrics.yml");
    String yaml = Resources.toString(url, Charsets.UTF_8);
    Map<String, List<Metric>> metricsMap = yamlUtils.read(yaml, new TypeReference<Map<String, List<Metric>>>() {});

    StringBuilder dockerMetrics = new StringBuilder();
    metricsMap.get("Docker").forEach(metric -> dockerMetrics.append(metric.getMetricName()).append(","));
    dockerMetrics.deleteCharAt(dockerMetrics.lastIndexOf(","));

    StringBuilder ecsMetrics = new StringBuilder();
    metricsMap.get("ECS").forEach(metric -> ecsMetrics.append(metric.getMetricName()).append(","));
    ecsMetrics.deleteCharAt(ecsMetrics.lastIndexOf(","));

    DatadogCVServiceConfiguration configuration =
        DatadogCVServiceConfiguration.builder()
            .dockerMetrics(Collections.singletonMap(generateUuid(), dockerMetrics.toString()))
            .ecsMetrics(Collections.singletonMap(generateUuid(), ecsMetrics.toString()))
            .customMetrics(Collections.singletonMap(generateUuid(),
                Sets.newHashSet(Metric.builder()
                                    .displayName(withDot ? "metric1.name" : "metric1")
                                    .mlMetricType(MetricType.THROUGHPUT.name())
                                    .build(),
                    Metric.builder()
                        .displayName(withDot ? "metric2.name" : "metric2")
                        .mlMetricType(MetricType.ERROR.name())
                        .build())))
            .build();
    configuration.setAccountId(accountId);
    configuration.setStateType(StateType.DATA_DOG);
    configuration.setEnvId(generateUuid());
    configuration.setName("DD-Config");
    configuration.setConnectorId(generateUuid());
    configuration.setServiceId(generateUuid());
    configuration.setEnabled24x7(enabled24x7);
    return configuration;
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCloneServiceGuardConfigs() {
    String oldEnvId = "EnvId";
    String newEnvId = "NewEnvId";

    List<TimeSeries> timeSeries = new ArrayList<>();
    timeSeries.add(TimeSeries.builder().metricName("metric1").metricType("INFRA").txnName("txn1").build());

    PrometheusCVServiceConfiguration config = new PrometheusCVServiceConfiguration();
    config.setAccountId(accountId);
    config.setEnvId(oldEnvId);
    config.setTimeSeriesToAnalyze(timeSeries);
    config.setStateType(StateType.PROMETHEUS);
    persistence.save(config);

    cvConfigurationService.cloneServiceGuardConfigs(oldEnvId, newEnvId);

    List<CVConfiguration> configs = persistence.createQuery(CVConfiguration.class, excludeAuthority)
                                        .filter(CVConfigurationKeys.accountId, accountId)
                                        .asList();

    assertThat(configs.size()).isEqualTo(2);
    Optional<CVConfiguration> oldConfig = configs.stream().filter(c -> c.getEnvId().equals(oldEnvId)).findAny();
    Optional<CVConfiguration> newConfig = configs.stream().filter(c -> c.getEnvId().equals(newEnvId)).findAny();

    assertThat(oldConfig).isPresent();
    assertThat(newConfig).isPresent();

    // Assert that templates are also copied along with cv configuration
    if (newConfig.isPresent()) {
      List<TimeSeriesMetricTemplates> definitions =
          persistence.createQuery(TimeSeriesMetricTemplates.class, excludeAuthority)
              .filter(TimeSeriesMetricTemplatesKeys.cvConfigId, newConfig.get().getUuid())
              .asList();
      assertThat(definitions.size()).isEqualTo(1);
      assertThat(definitions.get(0).getMetricTemplates().size()).isEqualTo(1);
    }
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateUniqueTxnMetricNameCombination() {
    APMCVServiceConfiguration apmcvServiceConfiguration = createAPMCVConfig(true, appId, accountId);
    assertThat(apmcvServiceConfiguration.validate()).isTrue();
    assertThat(apmcvServiceConfiguration.validateUniqueMetricTxnCombination(
                   apmcvServiceConfiguration.getMetricCollectionInfos()))
        .isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateUniqueTxnMetricNameCombinationInvalidCaseTxnFieldValue() {
    APMCVServiceConfiguration apmcvServiceConfiguration = createAPMCVConfigWithInvalidCollectionInfo(true);
    assertThat(apmcvServiceConfiguration.validate()).isTrue();
    assertThat(apmcvServiceConfiguration.validateUniqueMetricTxnCombination(
                   apmcvServiceConfiguration.getMetricCollectionInfos()))
        .isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testValidateUniqueTxnMetricNameCombinationInvalidCaseTxnJsonPath() {
    APMCVServiceConfiguration apmcvServiceConfiguration = createAPMCVConfig(true, appId, accountId);

    List<ApmMetricCollectionInfo> metricCollectionInfos = new ArrayList<>();
    ApmResponseMapping responseMapping = new ApmResponseMapping(null, "sometxnName", "sometxnname",
        "somemetricjsonpath", "hostpath", "hostregex", "timestamppath", "formattimestamp");

    ApmMetricCollectionInfo metricCollectionInfo =
        new ApmMetricCollectionInfo("metricName", MetricType.INFRA, "randomtag", "dummyuri", null,
            "bodycollection ${start_time} ${end_time}", ResponseType.JSON, responseMapping, Method.POST);

    ApmResponseMapping responseMapping2 = new ApmResponseMapping(null, "differentJsonPath", "sometxnname",
        "somemetricjsonpath", "hostpath", "hostregex", "timestamppath", "formattimestamp");

    ApmMetricCollectionInfo metricCollectionInfo2 =
        new ApmMetricCollectionInfo("metricName", MetricType.INFRA, "randomtag", "dummyuri ${start_time} ${end_time}",
            null, "bodycollection", ResponseType.JSON, responseMapping2, Method.POST);

    metricCollectionInfos.add(metricCollectionInfo);
    metricCollectionInfos.add(metricCollectionInfo2);
    apmcvServiceConfiguration.setMetricCollectionInfos(metricCollectionInfos);

    assertThat(apmcvServiceConfiguration.validate()).isTrue();
    assertThat(apmcvServiceConfiguration.validateUniqueMetricTxnCombination(
                   apmcvServiceConfiguration.getMetricCollectionInfos()))
        .isFalse();
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveInvalidCustomMetricConfig() {
    APMCVServiceConfiguration apmcvServiceConfiguration = createAPMCVConfigWithInvalidCollectionInfo(true);
    cvConfigurationService.saveConfiguration(apmcvServiceConfiguration.getAccountId(),
        apmcvServiceConfiguration.getAppId(), StateType.APM_VERIFICATION, apmcvServiceConfiguration);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetMetricTxnCombination() {
    APMCVServiceConfiguration apmcvServiceConfiguration = createAPMCVConfig(true, appId, accountId);
    String cvConfigId = generateUuid();
    apmcvServiceConfiguration.setUuid(cvConfigId);
    persistence.save(apmcvServiceConfiguration);
    Map<String, String> metricTxnMap = cvConfigurationService.getTxnMetricPairsForAPMCVConfig(cvConfigId);
    assertThat(metricTxnMap).isNotEmpty();
    assertThat(metricTxnMap.size()).isEqualTo(1);
    assertThat(metricTxnMap.containsKey("metricName,INFRA")).isTrue();
    assertThat(metricTxnMap.get("metricName,INFRA")).isEqualTo("myhardcodedtxnName");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetMetricTxnCombinationBadState() throws Exception {
    StackDriverMetricCVConfiguration cvConfiguration = createStackDriverConfig(accountId);
    String cvConfigId = generateUuid();
    cvConfiguration.setUuid(cvConfigId);
    persistence.save(cvConfiguration);
    Map<String, String> metricTxnMap = cvConfigurationService.getTxnMetricPairsForAPMCVConfig(cvConfigId);
    assertThat(metricTxnMap).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetMetricTxnCombinationBadCvConfigId() throws Exception {
    Map<String, String> metricTxnMap = cvConfigurationService.getTxnMetricPairsForAPMCVConfig(generateUuid());
    assertThat(metricTxnMap).isNull();
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveKeyTransactionsNullInput() throws Exception {
    cvConfigurationService.saveKeyTransactionsForCVConfiguration(accountId, generateUuid(), null);
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveKeyTransactionsNullInputTransactions() throws Exception {
    StackDriverMetricCVConfiguration cvConfiguration = createStackDriverConfig(accountId);
    String cvConfigId = generateUuid();
    cvConfiguration.setUuid(cvConfigId);
    persistence.save(cvConfiguration);
    cvConfigurationService.saveKeyTransactionsForCVConfiguration(accountId, cvConfigId, null);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveKeyTransactionsHappyCase() throws Exception {
    StackDriverMetricCVConfiguration cvConfiguration = createStackDriverConfig(accountId);
    String cvConfigId = generateUuid();
    cvConfiguration.setUuid(cvConfigId);
    persistence.save(cvConfiguration);
    boolean saved = cvConfigurationService.saveKeyTransactionsForCVConfiguration(
        accountId, cvConfigId, Arrays.asList("transaction1", "tranasaction2"));
    assertThat(saved).isTrue();
    TimeSeriesKeyTransactions keyTxns = cvConfigurationService.getKeyTransactionsForCVConfiguration(cvConfigId);
    assertThat(keyTxns).isNotNull();
    assertThat(keyTxns.getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(keyTxns.getKeyTransactions().size()).isEqualTo(2);
    assertThat(keyTxns.getKeyTransactions().containsAll(Arrays.asList("transaction1", "tranasaction2"))).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveKeyTransactionsHappyCaseAddtoKeyTxns() throws Exception {
    StackDriverMetricCVConfiguration cvConfiguration = createStackDriverConfig(accountId);
    String cvConfigId = generateUuid();
    cvConfiguration.setUuid(cvConfigId);
    persistence.save(cvConfiguration);
    boolean saved = cvConfigurationService.saveKeyTransactionsForCVConfiguration(
        accountId, cvConfigId, Arrays.asList("transaction1", "transaction2"));
    assertThat(saved).isTrue();
    TimeSeriesKeyTransactions keyTxns = cvConfigurationService.getKeyTransactionsForCVConfiguration(cvConfigId);
    assertThat(keyTxns).isNotNull();
    assertThat(keyTxns.getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(keyTxns.getKeyTransactions().size()).isEqualTo(2);
    assertThat(keyTxns.getKeyTransactions().containsAll(Arrays.asList("transaction1", "transaction2"))).isTrue();
    saved = cvConfigurationService.addToKeyTransactionsForCVConfiguration(
        accountId, cvConfigId, Arrays.asList("transaction3"));

    assertThat(saved).isTrue();
    keyTxns = cvConfigurationService.getKeyTransactionsForCVConfiguration(cvConfigId);
    assertThat(keyTxns).isNotNull();
    assertThat(keyTxns.getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(keyTxns.getKeyTransactions().size()).isEqualTo(3);
    assertThat(keyTxns.getKeyTransactions().containsAll(Arrays.asList("transaction1", "transaction2", "transaction3")))
        .isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testRemoveFromKeyTransactionsHappyCase() throws Exception {
    StackDriverMetricCVConfiguration cvConfiguration = createStackDriverConfig(accountId);
    String cvConfigId = generateUuid();
    cvConfiguration.setUuid(cvConfigId);
    persistence.save(cvConfiguration);
    boolean saved = cvConfigurationService.saveKeyTransactionsForCVConfiguration(
        accountId, cvConfigId, Arrays.asList("transaction1", "transaction2"));
    assertThat(saved).isTrue();
    TimeSeriesKeyTransactions keyTxns = cvConfigurationService.getKeyTransactionsForCVConfiguration(cvConfigId);
    assertThat(keyTxns).isNotNull();
    assertThat(keyTxns.getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(keyTxns.getKeyTransactions().size()).isEqualTo(2);
    assertThat(keyTxns.getKeyTransactions().containsAll(Arrays.asList("transaction1", "transaction2"))).isTrue();
    saved =
        cvConfigurationService.removeFromKeyTransactionsForCVConfiguration(cvConfigId, Arrays.asList("transaction2"));

    assertThat(saved).isTrue();
    keyTxns = cvConfigurationService.getKeyTransactionsForCVConfiguration(cvConfigId);
    assertThat(keyTxns).isNotNull();
    assertThat(keyTxns.getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(keyTxns.getKeyTransactions().size()).isEqualTo(1);
    assertThat(keyTxns.getKeyTransactions().containsAll(Arrays.asList("transaction1"))).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testRemoveFromKeyTransactionsOnlyOne() throws Exception {
    StackDriverMetricCVConfiguration cvConfiguration = createStackDriverConfig(accountId);
    String cvConfigId = generateUuid();
    cvConfiguration.setUuid(cvConfigId);
    persistence.save(cvConfiguration);
    boolean saved = cvConfigurationService.saveKeyTransactionsForCVConfiguration(
        accountId, cvConfigId, Arrays.asList("transaction1"));
    assertThat(saved).isTrue();
    TimeSeriesKeyTransactions keyTxns = cvConfigurationService.getKeyTransactionsForCVConfiguration(cvConfigId);
    assertThat(keyTxns).isNotNull();
    assertThat(keyTxns.getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(keyTxns.getKeyTransactions().size()).isEqualTo(1);
    assertThat(keyTxns.getKeyTransactions().containsAll(Arrays.asList("transaction1"))).isTrue();
    saved =
        cvConfigurationService.removeFromKeyTransactionsForCVConfiguration(cvConfigId, Arrays.asList("transaction1"));

    assertThat(saved).isTrue();
    TimeSeriesKeyTransactions transactions =
        persistence.createQuery(TimeSeriesKeyTransactions.class).filter("cvConfigId", cvConfigId).get();
    assertThat(transactions).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testWhenDeleteConfiguration_DeletesAlerts() {
    final APMVerificationConfig apmVerificationConfig = new APMVerificationConfig();
    apmVerificationConfig.setAccountId(accountId);
    final String connectorId = persistence.save(aSettingAttribute()
                                                    .withAccountId(accountId)
                                                    .withAppId(appId)
                                                    .withName(generateUuid())
                                                    .withValue(apmVerificationConfig)
                                                    .build());
    APMCVServiceConfiguration config1 = createAPMCVConfig(true, appId, accountId);
    config1.setConnectorId(connectorId);
    APMCVServiceConfiguration config2 = createAPMCVConfig(true, appId, accountId);
    config2.setConnectorId(connectorId);

    final String configId1 = cvConfigurationService.saveConfiguration(
        config1.getAccountId(), config1.getAppId(), StateType.APM_VERIFICATION, config1);
    final String configId2 = cvConfigurationService.saveConfiguration(
        config2.getAccountId(), config2.getAppId(), StateType.APM_VERIFICATION, config2);

    int numOfAlertsForCvConfig1 = 7;
    int numOfAlertsForCvConfig2 = 5;
    int numOfNonCvAlert = 9;

    for (int i = 0; i < numOfAlertsForCvConfig1; i++) {
      continuousVerificationService.openAlert(configId1,
          ContinuousVerificationAlertData.builder()
              .alertStatus(AlertStatus.Open)
              .mlAnalysisType(MLAnalysisType.TIME_SERIES)
              .cvConfiguration(config1)
              .accountId(accountId)
              .analysisEndTime(TimeUnit.DAYS.toMillis(i))
              .build());
    }

    for (int i = 0; i < numOfAlertsForCvConfig2; i++) {
      continuousVerificationService.openAlert(configId2,
          ContinuousVerificationAlertData.builder()
              .alertStatus(AlertStatus.Open)
              .mlAnalysisType(MLAnalysisType.TIME_SERIES)
              .cvConfiguration(config2)
              .accountId(accountId)
              .analysisEndTime(TimeUnit.DAYS.toMillis(i))
              .build());
    }

    for (int i = 0; i < numOfNonCvAlert; i++) {
      alertService.openAlert(accountId, generateUuid(), AlertType.USAGE_LIMIT_EXCEEDED,
          new UsageLimitExceededAlert(accountId, new StaticLimit(10), generateUuid()));
    }

    long numOfOpenAlerts = waitTillAlertsSteady(numOfAlertsForCvConfig1 + numOfAlertsForCvConfig2 + numOfNonCvAlert);
    assertThat(numOfOpenAlerts).isEqualTo(numOfAlertsForCvConfig1 + numOfAlertsForCvConfig2 + numOfNonCvAlert);
    assertThat(
        persistence.createQuery(Alert.class, excludeAuthority).filter(AlertKeys.status, AlertStatus.Closed).count())
        .isEqualTo(0);

    cvConfigurationService.deleteConfiguration(accountId, appId, configId1);

    numOfOpenAlerts = waitTillAlertsSteady(numOfAlertsForCvConfig2 + numOfNonCvAlert);
    assertThat(numOfOpenAlerts).isEqualTo(numOfAlertsForCvConfig2 + numOfNonCvAlert);
    assertThat(
        persistence.createQuery(Alert.class, excludeAuthority).filter(AlertKeys.status, AlertStatus.Closed).count())
        .isEqualTo(numOfAlertsForCvConfig1);

    cvConfigurationService.deleteConfiguration(accountId, appId, configId2);
    numOfOpenAlerts = waitTillAlertsSteady(numOfNonCvAlert);
    assertThat(numOfOpenAlerts).isEqualTo(numOfNonCvAlert);
    assertThat(
        persistence.createQuery(Alert.class, excludeAuthority).filter(AlertKeys.status, AlertStatus.Closed).count())
        .isEqualTo(numOfAlertsForCvConfig1 + numOfAlertsForCvConfig2);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testDeleteSettingAttribute_whenUsedInServiceGuard() throws Exception {
    String connectorName = generateUuid();
    final String connectorId =
        persistence.save(aSettingAttribute()
                             .withName(connectorName)
                             .withAccountId(accountId)
                             .withValue(DatadogConfig.builder().url(generateUuid()).accountId(accountId).build())
                             .build());

    final DatadogCVServiceConfiguration datadogCVConfiguration = createDatadogCVConfiguration(false, false);
    datadogCVConfiguration.setConnectorId(connectorId);
    final String cvConfigId = persistence.save(datadogCVConfiguration);
    assertThatThrownBy(() -> settingsService.delete(appId, connectorId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Connector " + connectorName + " is referenced by 1 Service Guard(s). Source ["
            + datadogCVConfiguration.getName() + "].");

    assertThat(persistence.get(SettingAttribute.class, connectorId)).isNotNull();

    persistence.delete(CVConfiguration.class, cvConfigId);
    settingsService.delete(appId, connectorId);
    assertThat(persistence.get(SettingAttribute.class, connectorId)).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void test_saveCVConfigLimitReached() {
    for (int i = 0; i < SERVICE_GUAARD_LIMIT; i++) {
      cvConfigurationService.saveConfiguration(
          accountId, appId, APM_VERIFICATION, createAPMCVConfig(true, appId, accountId));
    }

    // adding new should throw error
    assertThatThrownBy(()
                           -> cvConfigurationService.saveConfiguration(
                               accountId, appId, APM_VERIFICATION, createAPMCVConfig(true, appId, accountId)))
        .isInstanceOf(VerificationOperationException.class)
        .hasMessage(ErrorCode.APM_CONFIGURATION_ERROR.name());

    // disabled service guards should be saved
    APMCVServiceConfiguration apmcvConfigToUpdate = createAPMCVConfig(false, appId, accountId);
    String cvConfigId =
        cvConfigurationService.saveConfiguration(accountId, appId, APM_VERIFICATION, apmcvConfigToUpdate);

    // enabling the service guard should fail
    apmcvConfigToUpdate.setEnabled24x7(true);
    apmcvConfigToUpdate.setUuid(cvConfigId);
    assertThatThrownBy(() -> cvConfigurationService.updateConfiguration(apmcvConfigToUpdate, appId))
        .isInstanceOf(VerificationOperationException.class)
        .hasMessage(ErrorCode.APM_CONFIGURATION_ERROR.name());

    // change the limit and save again
    Account account = accountService.get(accountId);
    account.setServiceGuardLimit(SERVICE_GUAARD_LIMIT + 2);
    accountService.update(account);

    cvConfigurationService.saveConfiguration(
        accountId, appId, APM_VERIFICATION, createAPMCVConfig(true, appId, accountId));

    // update should succeed too
    cvConfigurationService.updateConfiguration(apmcvConfigToUpdate, appId);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testDisableConfig() {
    String cvConfigId = generateUuid();
    LogsCVConfiguration logsCVConfiguration = createLogsCVConfig(true);
    logsCVConfiguration.setUuid(cvConfigId);
    assertThat(logsCVConfiguration.isEnabled24x7()).isTrue();
    persistence.save(logsCVConfiguration);

    cvConfigurationService.disableConfig(cvConfigId);
    logsCVConfiguration = (LogsCVConfiguration) persistence.get(CVConfiguration.class, cvConfigId);

    assertThat(logsCVConfiguration.isEnabled24x7()).isFalse();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testDeleteCvConfig_whenEnvironmentDeleted() {
    Environment environment =
        environmentService.save(anEnvironment().appId(appId).accountId(accountId).name(generateUuid()).build());
    APMCVServiceConfiguration apmcvConfig = createAPMCVConfig(true, appId, accountId);
    apmcvConfig.setEnvId(environment.getUuid());
    cvConfigurationService.saveConfiguration(accountId, appId, APM_VERIFICATION, apmcvConfig);
    apmcvConfig = createAPMCVConfig(true, appId, accountId);
    apmcvConfig.setEnvId(environment.getUuid());
    cvConfigurationService.saveConfiguration(accountId, appId, APM_VERIFICATION, apmcvConfig);
    List<CVConfiguration> cvConfigurations = cvConfigurationService.listConfigurations(accountId);
    assertThat(cvConfigurations.size()).isEqualTo(2);

    // delete env and ensure that cvConfigs have been deleted
    environmentService.delete(appId, environment.getUuid());
    cvConfigurations = cvConfigurationService.listConfigurations(accountId);
    assertThat(cvConfigurations.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testDeleteCvConfig_whenServiceDeleted() {
    Service service =
        serviceResourceService.save(Service.builder().appId(appId).accountId(accountId).name(generateUuid()).build());
    APMCVServiceConfiguration apmcvConfig = createAPMCVConfig(true, appId, accountId);
    apmcvConfig.setServiceId(service.getUuid());
    cvConfigurationService.saveConfiguration(accountId, appId, APM_VERIFICATION, apmcvConfig);

    // delete service and ensure throws exception
    assertThatThrownBy(() -> serviceResourceService.delete(appId, service.getUuid()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Service [" + service.getName()
            + "] couldn't be deleted. Remove Service reference from the following service guards ["
            + apmcvConfig.getName() + "] ");
  }

  private long waitTillAlertsSteady(long numOfExpectedAlerts) {
    long numOfOpenAlerts;
    int numOfTrials = 0;
    do {
      numOfOpenAlerts =
          persistence.createQuery(Alert.class, excludeAuthority).filter(AlertKeys.status, AlertStatus.Open).count();
      numOfTrials++;
      log.info("trial: {} numOfAlerts: {}", numOfTrials, numOfOpenAlerts);
      sleep(ofMillis(100));
    } while (numOfTrials < 50 && numOfOpenAlerts != numOfExpectedAlerts);
    return numOfOpenAlerts;
  }

  public static APMCVServiceConfiguration createAPMCVConfig(boolean enabled24x7, String appId, String accountId) {
    APMCVServiceConfiguration apmcvServiceConfiguration = new APMCVServiceConfiguration();
    apmcvServiceConfiguration.setName("APM config " + generateUuid());
    apmcvServiceConfiguration.setAccountId(accountId);
    apmcvServiceConfiguration.setAppId(appId);
    apmcvServiceConfiguration.setEnvId(generateUuid());
    apmcvServiceConfiguration.setServiceId(generateUuid());
    apmcvServiceConfiguration.setEnabled24x7(enabled24x7);
    apmcvServiceConfiguration.setConnectorId(generateUuid());
    apmcvServiceConfiguration.setStateType(APM_VERIFICATION);
    apmcvServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.MEDIUM);

    List<ApmMetricCollectionInfo> metricCollectionInfos = new ArrayList<>();
    ApmResponseMapping responseMapping = new ApmResponseMapping("myhardcodedtxnName", null, "sometxnname",
        "series[*].pointlist[*].[1]", null, null, "series[*].pointlist[*].[0]", null);

    ApmMetricCollectionInfo metricCollectionInfo =
        new ApmMetricCollectionInfo("metricName", MetricType.INFRA, "randomtag", "dummyuri ${start_time} ${end_time}",
            null, "{\"bodycollection\":\"body\"}", ResponseType.JSON, responseMapping, Method.POST);

    metricCollectionInfos.add(metricCollectionInfo);
    apmcvServiceConfiguration.setMetricCollectionInfos(metricCollectionInfos);
    return apmcvServiceConfiguration;
  }

  private APMCVServiceConfiguration createAPMCVConfigWithInvalidCollectionInfo(boolean enabled24x7) {
    APMCVServiceConfiguration apmcvServiceConfiguration = createAPMCVConfig(true, appId, accountId);

    List<ApmMetricCollectionInfo> metricCollectionInfos = new ArrayList<>();
    ApmResponseMapping responseMapping = new ApmResponseMapping("sometxnName", null, "sometxnname",
        "somemetricjsonpath", "hostpath", "hostregex", "timestamppath", "formattimestamp");

    ApmMetricCollectionInfo metricCollectionInfo =
        new ApmMetricCollectionInfo("metricName", MetricType.INFRA, "randomtag", "dummyuri ${start_time} ${end_time}",
            null, "bodycollection", ResponseType.JSON, responseMapping, Method.POST);

    ApmMetricCollectionInfo metricCollectionInfo2 =
        new ApmMetricCollectionInfo("metricName", MetricType.INFRA, "randomtag", "dummyuri ${start_time} ${end_time}",
            null, "bodycollection", ResponseType.JSON, responseMapping, Method.POST);

    metricCollectionInfos.add(metricCollectionInfo);
    metricCollectionInfos.add(metricCollectionInfo2);
    apmcvServiceConfiguration.setMetricCollectionInfos(metricCollectionInfos);
    return apmcvServiceConfiguration;
  }
}
