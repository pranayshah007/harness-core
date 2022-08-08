/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration.verification;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRANJAL;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.UNKNOWN;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.sm.StateType.APM_VERIFICATION;
import static software.wings.sm.StateType.APP_DYNAMICS;
import static software.wings.sm.StateType.BUG_SNAG;
import static software.wings.sm.StateType.CLOUD_WATCH;
import static software.wings.sm.StateType.DATA_DOG;
import static software.wings.sm.StateType.DYNA_TRACE;
import static software.wings.sm.StateType.ELK;
import static software.wings.sm.StateType.INSTANA;
import static software.wings.sm.StateType.NEW_RELIC;
import static software.wings.sm.StateType.PROMETHEUS;
import static software.wings.sm.StateType.SPLUNKV2;
import static software.wings.sm.StateType.STACK_DRIVER_LOG;
import static software.wings.sm.StateType.SUMO;
import static software.wings.utils.WingsTestConstants.mockChecker;

import static java.time.Duration.ofMillis;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.when;

import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.limits.LimitCheckerFactory;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.rule.Repeat;
import io.harness.serializer.JsonUtils;

import software.wings.beans.ApmMetricCollectionInfo;
import software.wings.beans.ApmResponseMapping;
import software.wings.beans.ElkConfig;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.Builder;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.apm.Method;
import software.wings.beans.apm.ResponseType;
import software.wings.dl.WingsPersistence;
import software.wings.integration.IntegrationTestBase;
import software.wings.metrics.MetricType;
import software.wings.metrics.Threshold;
import software.wings.metrics.ThresholdComparisonType;
import software.wings.metrics.ThresholdType;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.ElkConnector;
import software.wings.service.impl.analysis.LogDataRecord;
import software.wings.service.impl.analysis.LogDataRecord.LogDataRecordKeys;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.LogMLAnalysisRecord.LogMLAnalysisRecordKeys;
import software.wings.service.impl.analysis.TimeSeries;
import software.wings.service.impl.analysis.TimeSeriesMLTransactionThresholds;
import software.wings.service.impl.analysis.TimeSeriesMLTransactionThresholds.TimeSeriesMLTransactionThresholdKeys;
import software.wings.service.impl.cloudwatch.CloudWatchMetric;
import software.wings.service.impl.instana.InstanaTagFilter;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskKeys;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfiguration.CVConfigurationKeys;
import software.wings.verification.apm.APMCVServiceConfiguration;
import software.wings.verification.appdynamics.AppDynamicsCVServiceConfiguration;
import software.wings.verification.cloudwatch.CloudWatchCVServiceConfiguration;
import software.wings.verification.datadog.DatadogCVServiceConfiguration;
import software.wings.verification.dynatrace.DynaTraceCVServiceConfiguration;
import software.wings.verification.instana.InstanaCVConfiguration;
import software.wings.verification.log.BugsnagCVConfiguration;
import software.wings.verification.log.ElkCVConfiguration;
import software.wings.verification.log.LogsCVConfiguration;
import software.wings.verification.log.SplunkCVConfiguration;
import software.wings.verification.log.StackdriverCVConfiguration;
import software.wings.verification.newrelic.NewRelicCVServiceConfiguration;
import software.wings.verification.prometheus.PrometheusCVServiceConfiguration;

import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
/**
 * @author Vaibhav Tulsyan
 * 05/Oct/2018
 */
@Slf4j
public class CVConfigurationIntegrationTest extends IntegrationTestBase {
  @Rule public ExpectedException thrown = ExpectedException.none();
  private String appId, envId, serviceId, appDynamicsApplicationId;
  List<InstanaTagFilter> instanaTagFilters;

  @Inject private WingsPersistence wingsPersistence;
  @Inject @InjectMocks private AppService appService;
  @Inject @InjectMocks private EnvironmentService environmentService;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  private NewRelicCVServiceConfiguration newRelicCVServiceConfiguration;
  private AppDynamicsCVServiceConfiguration appDynamicsCVServiceConfiguration;
  private DynaTraceCVServiceConfiguration dynaTraceCVServiceConfiguration;
  private PrometheusCVServiceConfiguration prometheusCVServiceConfiguration;
  private DatadogCVServiceConfiguration datadogCVServiceConfiguration;
  private CloudWatchCVServiceConfiguration cloudWatchCVServiceConfiguration;
  private ElkCVConfiguration elkCVConfiguration;
  private SplunkCVConfiguration splunkCVConfiguration;
  private LogsCVConfiguration logsCVConfiguration;
  private BugsnagCVConfiguration bugsnagCVConfiguration;
  private StackdriverCVConfiguration stackdriverCVConfiguration;
  private APMCVServiceConfiguration apmcvServiceConfiguration;
  private InstanaCVConfiguration instanaCVConfiguration;

  private SettingAttribute settingAttribute;
  private String settingAttributeId;
  private Service service;

  @Override
  @Before
  public void setUp() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    loginAdminUser();

    appId = appService.save(anApplication().name(generateUuid()).accountId(accountId).build()).getUuid();
    envId = environmentService.save(Environment.Builder.anEnvironment().appId(appId).name("Developmenet").build())
                .getUuid();
    appDynamicsApplicationId = generateUuid();

    settingAttribute = SettingAttribute.Builder.aSettingAttribute()
                           .withAccountId(accountId)
                           .withName("someSettingAttributeName")
                           .withCategory(SettingCategory.CONNECTOR)
                           .withEnvId(envId)
                           .withAppId(appId)
                           .build();
    settingAttributeId = wingsPersistence.save(settingAttribute);
    settingAttributeId = settingAttribute.getUuid();

    service = Service.builder().name("someServiceName").appId(appId).build();
    wingsPersistence.save(service);
    serviceId = service.getUuid();

    createNewRelicConfig(true);
    createAppDynamicsConfig();
    createInstanaCVConfig(true);
    createDynaTraceConfig();
    createPrometheusConfig();
    createDatadogConfig();
    createCloudWatchConfig();
    createElkCVConfig(true);
    createLogsCVConfig(true);
    createBugSnagCVConfig(true);
    createStackdriverCVConfig(true);
    createSplunkCVConfig(true);
    createAPMCVConfig(true);
  }

  private void createCloudWatchConfig() {
    cloudWatchCVServiceConfiguration = new CloudWatchCVServiceConfiguration();
    cloudWatchCVServiceConfiguration.setName("Config 1");
    cloudWatchCVServiceConfiguration.setAppId(appId);
    cloudWatchCVServiceConfiguration.setEnvId(envId);
    cloudWatchCVServiceConfiguration.setServiceId(serviceId);
    cloudWatchCVServiceConfiguration.setEnabled24x7(true);
    cloudWatchCVServiceConfiguration.setConnectorId(settingAttributeId);
    cloudWatchCVServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.MEDIUM);

    Map<String, List<CloudWatchMetric>> loadBalancerMetricsByLoadBalancer = new HashMap<>();
    List<CloudWatchMetric> loadBalancerMetrics = new ArrayList<>();
    loadBalancerMetrics.add(new CloudWatchMetric(
        "Latency", "Latenc", "LoadBalancerName", "Load balancer name", "ERROR", true, "Sum", StandardUnit.Count));
    loadBalancerMetricsByLoadBalancer.put("init-test", loadBalancerMetrics);
    cloudWatchCVServiceConfiguration.setLoadBalancerMetrics(loadBalancerMetricsByLoadBalancer);
    cloudWatchCVServiceConfiguration.setRegion("us-east-2");

    Map<String, List<CloudWatchMetric>> metricsByLambdaFunction = new HashMap<>();
    List<CloudWatchMetric> lambdaMetrics = new ArrayList<>();
    lambdaMetrics.add(new CloudWatchMetric("Invocations", "Invocations Sum", "FunctionName", "Lambda Function Name",
        "THROUGHPUT", true, "Sum", StandardUnit.Count));
    metricsByLambdaFunction.put("lambda_fn1", lambdaMetrics);

    cloudWatchCVServiceConfiguration.setLambdaFunctionsMetrics(metricsByLambdaFunction);
  }

  private void createNewRelicConfig(boolean enabled24x7) {
    String newRelicApplicationId = generateUuid();

    newRelicCVServiceConfiguration = new NewRelicCVServiceConfiguration();
    newRelicCVServiceConfiguration.setName("Config 1");
    newRelicCVServiceConfiguration.setAppId(appId);
    newRelicCVServiceConfiguration.setEnvId(envId);
    newRelicCVServiceConfiguration.setServiceId(serviceId);
    newRelicCVServiceConfiguration.setEnabled24x7(enabled24x7);
    newRelicCVServiceConfiguration.setApplicationId(newRelicApplicationId);
    newRelicCVServiceConfiguration.setConnectorId(settingAttributeId);
    newRelicCVServiceConfiguration.setMetrics(Collections.singletonList("apdexScore"));
    newRelicCVServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.MEDIUM);
  }

  private void createAppDynamicsConfig() {
    appDynamicsCVServiceConfiguration = new AppDynamicsCVServiceConfiguration();
    appDynamicsCVServiceConfiguration.setAppId(appId);
    appDynamicsCVServiceConfiguration.setEnvId(envId);
    appDynamicsCVServiceConfiguration.setServiceId(serviceId);
    appDynamicsCVServiceConfiguration.setEnabled24x7(true);
    appDynamicsCVServiceConfiguration.setAppDynamicsApplicationId(appDynamicsApplicationId);
    appDynamicsCVServiceConfiguration.setTierId(generateUuid());
    appDynamicsCVServiceConfiguration.setConnectorId(generateUuid());
    appDynamicsCVServiceConfiguration.setStateType(APP_DYNAMICS);
    appDynamicsCVServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.HIGH);
  }

  private void createInstanaCVConfig(boolean cv24x7) {
    instanaCVConfiguration = new InstanaCVConfiguration();
    instanaCVConfiguration.setName("instana-config");
    instanaCVConfiguration.setAppId(appId);
    instanaCVConfiguration.setEnvId(envId);
    instanaCVConfiguration.setServiceId(serviceId);
    instanaCVConfiguration.setEnabled24x7(cv24x7);
    instanaTagFilters = new ArrayList<>();
    instanaTagFilters.add(InstanaTagFilter.builder()
                              .name("kubernetes.cluster.name")
                              .operator(InstanaTagFilter.Operator.EQUALS)
                              .value("harness-test")
                              .build());
    instanaCVConfiguration.setTagFilters(instanaTagFilters);
    instanaCVConfiguration.setConnectorId(generateUuid());
    instanaCVConfiguration.setStateType(INSTANA);
    instanaCVConfiguration.setAnalysisTolerance(AnalysisTolerance.HIGH);
  }

  private void createDynaTraceConfig() {
    dynaTraceCVServiceConfiguration = new DynaTraceCVServiceConfiguration();
    dynaTraceCVServiceConfiguration.setAppId(appId);
    dynaTraceCVServiceConfiguration.setEnvId(envId);
    dynaTraceCVServiceConfiguration.setServiceId(serviceId);
    dynaTraceCVServiceConfiguration.setEnabled24x7(true);
    dynaTraceCVServiceConfiguration.setConnectorId(generateUuid());
    dynaTraceCVServiceConfiguration.setStateType(APP_DYNAMICS);
    dynaTraceCVServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.HIGH);
  }

  private void createPrometheusConfig() {
    List<TimeSeries> timeSeries = Lists.newArrayList(
        TimeSeries.builder()
            .txnName("Hardware")
            .metricName("CPU")
            .metricType(MetricType.INFRA.name())
            .url(
                "/api/v1/query_range?start=$startTime&end=$endTime&step=60s&query=container_cpu_usage_seconds_total{pod_name=\"$hostName\"}")
            .build());

    prometheusCVServiceConfiguration = new PrometheusCVServiceConfiguration();
    prometheusCVServiceConfiguration.setAppId(appId);
    prometheusCVServiceConfiguration.setEnvId(envId);
    prometheusCVServiceConfiguration.setServiceId(serviceId);
    prometheusCVServiceConfiguration.setEnabled24x7(true);
    prometheusCVServiceConfiguration.setTimeSeriesToAnalyze(timeSeries);
    prometheusCVServiceConfiguration.setConnectorId(generateUuid());
    prometheusCVServiceConfiguration.setStateType(PROMETHEUS);
  }

  private void createDatadogConfig() {
    datadogCVServiceConfiguration = new DatadogCVServiceConfiguration();
    datadogCVServiceConfiguration.setName("datadog config");
    datadogCVServiceConfiguration.setAppId(appId);
    datadogCVServiceConfiguration.setEnvId(envId);
    datadogCVServiceConfiguration.setServiceId(serviceId);
    datadogCVServiceConfiguration.setEnabled24x7(true);
    datadogCVServiceConfiguration.setConnectorId(generateUuid());
    datadogCVServiceConfiguration.setStateType(DATA_DOG);
    datadogCVServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.HIGH);
    datadogCVServiceConfiguration.setDatadogServiceName(generateUuid());

    Map<String, String> dockerMetrics = new HashMap<>();
    dockerMetrics.put("service_name:harness", "docker.cpu.usage, docker.mem.rss");
    datadogCVServiceConfiguration.setDockerMetrics(dockerMetrics);
  }

  private void createElkCVConfig(boolean enabled24x7) {
    String elkSettingId =
        wingsPersistence.save(Builder.aSettingAttribute()
                                  .withName(generateUuid())
                                  .withAccountId(accountId)
                                  .withValue(ElkConfig.builder()
                                                 .elkConnector(ElkConnector.ELASTIC_SEARCH_SERVER)
                                                 .elkUrl("http://ec2-34-227-84-170.compute-1.amazonaws.com:9200/")
                                                 .accountId(accountId)
                                                 .build())
                                  .build());
    elkCVConfiguration = new ElkCVConfiguration();
    elkCVConfiguration.setName("Config 1");
    elkCVConfiguration.setAppId(appId);
    elkCVConfiguration.setEnvId(envId);
    elkCVConfiguration.setServiceId(serviceId);
    elkCVConfiguration.setEnabled24x7(enabled24x7);
    elkCVConfiguration.setConnectorId(elkSettingId);
    elkCVConfiguration.setBaselineStartMinute(100);
    elkCVConfiguration.setBaselineEndMinute(200);

    elkCVConfiguration.setQuery("query1");
    elkCVConfiguration.setIndex("filebeat-*");
    elkCVConfiguration.setHostnameField("host1");
    elkCVConfiguration.setMessageField("message1");
    elkCVConfiguration.setTimestampField("timestamp1");
    elkCVConfiguration.setTimestampFormat("timestamp_format1");
  }

  private void createAPMCVConfig(boolean enabled24x7) {
    apmcvServiceConfiguration = new APMCVServiceConfiguration();
    apmcvServiceConfiguration.setName("APM config");
    apmcvServiceConfiguration.setAppId(appId);
    apmcvServiceConfiguration.setEnvId(envId);
    apmcvServiceConfiguration.setServiceId(serviceId);
    apmcvServiceConfiguration.setEnabled24x7(enabled24x7);
    apmcvServiceConfiguration.setConnectorId(generateUuid());
    apmcvServiceConfiguration.setStateType(APM_VERIFICATION);
    apmcvServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.MEDIUM);

    List<ApmMetricCollectionInfo> metricCollectionInfos = new ArrayList<>();
    ApmResponseMapping responseMapping = new ApmResponseMapping("queries[*].results.[0].name", "somejsonpath",
        "sometxnname", "somemetricjsonpath", "hostpath", "hostregex", "timestamppath", "formattimestamp");

    ApmMetricCollectionInfo metricCollectionInfo = new ApmMetricCollectionInfo("metricName", MetricType.INFRA,
        "randomtag", "dummyuri", null, "bodycollection", ResponseType.JSON, responseMapping, Method.POST);

    metricCollectionInfos.add(metricCollectionInfo);
    apmcvServiceConfiguration.setMetricCollectionInfos(metricCollectionInfos);
  }

  private void createLogsCVConfig(boolean enabled24x7) {
    logsCVConfiguration = new LogsCVConfiguration();
    logsCVConfiguration.setName("Config 1");
    logsCVConfiguration.setAppId(appId);
    logsCVConfiguration.setEnvId(envId);
    logsCVConfiguration.setServiceId(serviceId);
    logsCVConfiguration.setEnabled24x7(enabled24x7);
    logsCVConfiguration.setConnectorId(settingAttributeId);
    logsCVConfiguration.setBaselineStartMinute(100);
    logsCVConfiguration.setBaselineEndMinute(200);
    logsCVConfiguration.setAlertEnabled(false);
    logsCVConfiguration.setAlertThreshold(0.1);

    logsCVConfiguration.setQuery("query1");
  }

  private void createBugSnagCVConfig(boolean enabled24x7) {
    bugsnagCVConfiguration = new BugsnagCVConfiguration();
    bugsnagCVConfiguration.setName("Config 1");
    bugsnagCVConfiguration.setAppId(appId);
    bugsnagCVConfiguration.setEnvId(envId);
    bugsnagCVConfiguration.setServiceId(serviceId);
    bugsnagCVConfiguration.setEnabled24x7(enabled24x7);
    bugsnagCVConfiguration.setConnectorId(settingAttributeId);
    bugsnagCVConfiguration.setBaselineStartMinute(100);
    bugsnagCVConfiguration.setBaselineEndMinute(200);
    bugsnagCVConfiguration.setAlertEnabled(false);
    bugsnagCVConfiguration.setAlertThreshold(0.1);
    bugsnagCVConfiguration.setProjectId("development");
    bugsnagCVConfiguration.setOrgId("Harness");
    bugsnagCVConfiguration.setBrowserApplication(true);

    bugsnagCVConfiguration.setQuery("*exception*");
  }

  private void createStackdriverCVConfig(boolean enabled24x7) {
    stackdriverCVConfiguration = new StackdriverCVConfiguration();
    stackdriverCVConfiguration.setQuery("*exception*");
    stackdriverCVConfiguration.setName("Config 1");
    stackdriverCVConfiguration.setAppId(appId);
    stackdriverCVConfiguration.setEnvId(envId);
    stackdriverCVConfiguration.setServiceId(serviceId);
    stackdriverCVConfiguration.setEnabled24x7(enabled24x7);
    stackdriverCVConfiguration.setConnectorId(settingAttributeId);
    stackdriverCVConfiguration.setBaselineStartMinute(100);
    stackdriverCVConfiguration.setBaselineEndMinute(200);
    stackdriverCVConfiguration.setAlertEnabled(false);
    stackdriverCVConfiguration.setAlertThreshold(0.1);
    stackdriverCVConfiguration.setStateType(STACK_DRIVER_LOG);
    stackdriverCVConfiguration.setHostnameField(generateUuid());
    stackdriverCVConfiguration.setMessageField(generateUuid());
  }

  private void createSplunkCVConfig(boolean enabled24x7) {
    splunkCVConfiguration = new SplunkCVConfiguration();
    splunkCVConfiguration.setQuery("*exception*");
    splunkCVConfiguration.setName("Config 1");
    splunkCVConfiguration.setHostnameField("splunk_hostname");
    splunkCVConfiguration.setAdvancedQuery(false);
    splunkCVConfiguration.setAppId(appId);
    splunkCVConfiguration.setEnvId(envId);
    splunkCVConfiguration.setServiceId(serviceId);
    splunkCVConfiguration.setEnabled24x7(enabled24x7);
    splunkCVConfiguration.setConnectorId(settingAttributeId);
    splunkCVConfiguration.setBaselineStartMinute(100);
    splunkCVConfiguration.setBaselineEndMinute(200);
    splunkCVConfiguration.setAlertEnabled(false);
    splunkCVConfiguration.setAlertThreshold(0.1);
    splunkCVConfiguration.setStateType(SPLUNKV2);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testSaveConfiguration() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String id = wingsPersistence.save(newRelicCVServiceConfiguration);
    NewRelicCVServiceConfiguration obj = wingsPersistence.get(NewRelicCVServiceConfiguration.class, id);
    assertThat(obj.getUuid()).isEqualTo(id);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testNewRelicConfiguration() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + NEW_RELIC;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(newRelicCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<NewRelicCVServiceConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<NewRelicCVServiceConfiguration>>() {});
    NewRelicCVServiceConfiguration fetchedObject = getRequestResponse.getResource();

    NewRelicCVServiceConfiguration newRelicCVServiceConfiguration = fetchedObject;
    assertThat(fetchedObject.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(fetchedObject.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedObject.getAppId()).isEqualTo(appId);
    assertThat(fetchedObject.getEnvId()).isEqualTo(envId);
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(NEW_RELIC);
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.MEDIUM);
    assertThat(newRelicCVServiceConfiguration.getConnectorName()).isEqualTo("someSettingAttributeName");
    assertThat(newRelicCVServiceConfiguration.getServiceName()).isEqualTo("someServiceName");

    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId;
    target = client.target(url);

    RestResponse<List<Object>> allConfigResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<Object>>>() {});
    List<Object> allConifgs = allConfigResponse.getResource();

    assertThat(allConifgs).hasSize(1);

    NewRelicCVServiceConfiguration obj =
        JsonUtils.asObject(JsonUtils.asJson(allConifgs.get(0)), NewRelicCVServiceConfiguration.class);

    assertThat(obj.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(obj.getAccountId()).isEqualTo(accountId);
    assertThat(obj.getAppId()).isEqualTo(appId);
    assertThat(obj.getEnvId()).isEqualTo(envId);
    assertThat(obj.getServiceId()).isEqualTo(serviceId);
    assertThat(obj.getStateType()).isEqualTo(NEW_RELIC);
    assertThat(obj.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.MEDIUM);
    assertThat(obj.getName()).isEqualTo("Config 1");

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId + "&appId=" + appId
        + "&stateType=" + NEW_RELIC + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    newRelicCVServiceConfiguration.setName("Config 2");
    newRelicCVServiceConfiguration.setEnabled24x7(false);
    newRelicCVServiceConfiguration.setMetrics(Collections.singletonList("requestsPerMinute"));
    newRelicCVServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.LOW);
    getRequestBuilderWithAuthHeader(target).put(
        entity(newRelicCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<NewRelicCVServiceConfiguration>>() {});
    fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.isEnabled24x7()).isFalse();
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.LOW);
    assertThat(fetchedObject.getName()).isEqualTo("Config 2");

    validateDeleteCVConfiguration(savedObjectUuid);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testAppDynamicsConfiguration() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url =
        API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + APP_DYNAMICS;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(appDynamicsCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);

    RestResponse<AppDynamicsCVServiceConfiguration> getRequestResponse = getRequestBuilderWithAuthHeader(target).get(
        new GenericType<RestResponse<AppDynamicsCVServiceConfiguration>>() {});
    AppDynamicsCVServiceConfiguration fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(fetchedObject.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedObject.getAppId()).isEqualTo(appId);
    assertThat(fetchedObject.getEnvId()).isEqualTo(envId);
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(APP_DYNAMICS);
    assertThat(fetchedObject.getAppDynamicsApplicationId()).isEqualTo(appDynamicsApplicationId);
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.HIGH);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testInstanaConfiguration_create() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + INSTANA;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(instanaCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);

    RestResponse<InstanaCVConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<InstanaCVConfiguration>>() {});
    InstanaCVConfiguration fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(fetchedObject.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedObject.getAppId()).isEqualTo(appId);
    assertThat(fetchedObject.getEnvId()).isEqualTo(envId);
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(INSTANA);
    List<InstanaTagFilter> instanaTagFilters = new ArrayList<>();
    instanaTagFilters.add(InstanaTagFilter.builder()
                              .name("kubernetes.cluster.name")
                              .operator(InstanaTagFilter.Operator.EQUALS)
                              .value("harness-test")
                              .build());

    assertThat(fetchedObject.getTagFilters()).isEqualTo(instanaTagFilters);
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.HIGH);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testInstanaConfiguration_update() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + INSTANA;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(instanaCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId + "&appId=" + appId
        + "&stateType=" + INSTANA + "&serviceConfigurationId=" + savedObjectUuid;
    log.info("PUT " + url);
    target = client.target(url);
    String updateApplicationId = generateUuid();
    instanaCVConfiguration.setTagFilters(new ArrayList<>());
    getRequestBuilderWithAuthHeader(target).put(
        entity(instanaCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});

    // Call GET
    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);

    RestResponse<InstanaCVConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<InstanaCVConfiguration>>() {});
    InstanaCVConfiguration fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(fetchedObject.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedObject.getAppId()).isEqualTo(appId);
    assertThat(fetchedObject.getEnvId()).isEqualTo(envId);
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(INSTANA);
    assertThat(fetchedObject.getTagFilters()).isEmpty();
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.HIGH);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testAPMCVConfiguration() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url =
        API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + APM_VERIFICATION;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(apmcvServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);

    RestResponse<APMCVServiceConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<APMCVServiceConfiguration>>() {});
    APMCVServiceConfiguration fetchedObject = getRequestResponse.getResource();
    assertThat(savedObjectUuid).isEqualTo(fetchedObject.getUuid());
    assertThat(accountId).isEqualTo(fetchedObject.getAccountId());
    assertThat(appId).isEqualTo(fetchedObject.getAppId());
    assertThat(envId).isEqualTo(fetchedObject.getEnvId());
    assertThat(serviceId).isEqualTo(fetchedObject.getServiceId());
    assertThat(APM_VERIFICATION).isEqualTo(fetchedObject.getStateType());
    assertThat(AnalysisTolerance.MEDIUM).isEqualTo(fetchedObject.getAnalysisTolerance());

    List<ApmMetricCollectionInfo> metricCollectionInfos = fetchedObject.getMetricCollectionInfos();
    assertThat(1).isEqualTo(metricCollectionInfos.size());
    ApmMetricCollectionInfo metricCollectionInfo = metricCollectionInfos.get(0);
    assertThat("metricName").isEqualTo(metricCollectionInfo.getMetricName());
    assertThat("dummyuri").isEqualTo(metricCollectionInfo.getCollectionUrl());
    assertThat("bodycollection").isEqualTo(metricCollectionInfo.getCollectionBody());
    assertThat(MetricType.INFRA).isEqualTo(metricCollectionInfo.getMetricType());
    assertThat(Method.POST).isEqualTo(metricCollectionInfo.getMethod());
    assertThat(ResponseType.JSON).isEqualTo(metricCollectionInfo.getResponseType());

    ApmResponseMapping responseMapping = metricCollectionInfo.getResponseMapping();
    assertThat("timestamppath").isEqualTo(responseMapping.getTimestampJsonPath());
    assertThat("queries[*].results.[0].name").isEqualTo(responseMapping.getTxnNameFieldValue());
    assertThat("hostpath").isEqualTo(responseMapping.getHostJsonPath());
    assertThat("hostregex").isEqualTo(responseMapping.getHostRegex());
    assertThat("sometxnname").isEqualTo(responseMapping.getTxnNameRegex());
    assertThat("somemetricjsonpath").isEqualTo(responseMapping.getMetricValueJsonPath());
    assertThat("formattimestamp").isEqualTo(responseMapping.getTimeStampFormat());
    assertThat("somejsonpath").isEqualTo(responseMapping.getTxnNameJsonPath());
  }

  @Test
  @Owner(developers = RAGHU, intermittent = true)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testDatadogConfiguration() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + DATA_DOG;
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(datadogCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();
    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    RestResponse<DatadogCVServiceConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<DatadogCVServiceConfiguration>>() {});
    DatadogCVServiceConfiguration fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(fetchedObject.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedObject.getAppId()).isEqualTo(appId);
    assertThat(fetchedObject.getEnvId()).isEqualTo(envId);
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(DATA_DOG);
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.HIGH);
    assertThat(fetchedObject.getDockerMetrics().values().iterator().next())
        .isEqualTo("docker.cpu.usage, docker.mem.rss");

    // Test PUT API for Datadog
    datadogCVServiceConfiguration.setName("Datadog Config");
    datadogCVServiceConfiguration.setEnabled24x7(false);
    datadogCVServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.MEDIUM);
    Map<String, String> dockerMetrics = new HashMap<>();
    dockerMetrics.put("service_name:harness", "docker.cpu.throttled");
    datadogCVServiceConfiguration.setDockerMetrics(dockerMetrics);

    // Call PUT
    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId + "&appId=" + appId
        + "&stateType=" + DATA_DOG + "&serviceConfigurationId=" + savedObjectUuid;
    log.info("PUT " + url);
    target = client.target(url);
    getRequestBuilderWithAuthHeader(target).put(
        entity(datadogCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});

    // Call GET
    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<DatadogCVServiceConfiguration>>() {});
    fetchedObject = getRequestResponse.getResource();

    // Assert
    assertThat(fetchedObject.getName()).isEqualTo("Datadog Config");
    assertThat(fetchedObject.isEnabled24x7()).isFalse();
    assertThat(fetchedObject.getDockerMetrics().values().iterator().next()).isEqualTo("docker.cpu.throttled");
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.MEDIUM);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testPrometheusConfiguration() {
    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + PROMETHEUS;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(prometheusCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<PrometheusCVServiceConfiguration> getRequestResponse = getRequestBuilderWithAuthHeader(target).get(
        new GenericType<RestResponse<PrometheusCVServiceConfiguration>>() {});
    PrometheusCVServiceConfiguration fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(fetchedObject.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedObject.getAppId()).isEqualTo(appId);
    assertThat(fetchedObject.getEnvId()).isEqualTo(envId);
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(PROMETHEUS);
    assertThat(fetchedObject.getTimeSeriesToAnalyze())
        .isEqualTo(prometheusCVServiceConfiguration.getTimeSeriesToAnalyze());
  }

  @Test
  @Owner(developers = RAGHU, intermittent = true)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testDynaTraceConfiguration() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + DYNA_TRACE;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(dynaTraceCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<DynaTraceCVServiceConfiguration> getRequestResponse = getRequestBuilderWithAuthHeader(target).get(
        new GenericType<RestResponse<DynaTraceCVServiceConfiguration>>() {});
    DynaTraceCVServiceConfiguration fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(fetchedObject.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedObject.getAppId()).isEqualTo(appId);
    assertThat(fetchedObject.getEnvId()).isEqualTo(envId);
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(DYNA_TRACE);
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.HIGH);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testCloudWatchConfiguration() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url =
        API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + CLOUD_WATCH;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(cloudWatchCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<CloudWatchCVServiceConfiguration> getRequestResponse = getRequestBuilderWithAuthHeader(target).get(
        new GenericType<RestResponse<CloudWatchCVServiceConfiguration>>() {});
    CloudWatchCVServiceConfiguration fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(fetchedObject.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedObject.getAppId()).isEqualTo(appId);
    assertThat(fetchedObject.getEnvId()).isEqualTo(envId);
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(CLOUD_WATCH);
    assertThat(fetchedObject.getLoadBalancerMetrics())
        .isEqualTo(cloudWatchCVServiceConfiguration.getLoadBalancerMetrics());
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testCloudWatchConfigurationNoMetricsShouldFail() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url =
        API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + CLOUD_WATCH;
    log.info("POST " + url);
    WebTarget target = client.target(url);

    cloudWatchCVServiceConfiguration.setLambdaFunctionsMetrics(null);
    cloudWatchCVServiceConfiguration.setLoadBalancerMetrics(null);
    cloudWatchCVServiceConfiguration.setEc2InstanceNames(null);
    cloudWatchCVServiceConfiguration.setEcsMetrics(null);
    cloudWatchCVServiceConfiguration.setEc2Metrics(null);
    thrown.expect(Exception.class);
    getRequestBuilderWithAuthHeader(target).post(
        entity(cloudWatchCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testCloudWatchConfigurationWithoutAnyMetric() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url =
        API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + CLOUD_WATCH;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(cloudWatchCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<CloudWatchCVServiceConfiguration> getRequestResponse = getRequestBuilderWithAuthHeader(target).get(
        new GenericType<RestResponse<CloudWatchCVServiceConfiguration>>() {});
    CloudWatchCVServiceConfiguration fetchedObject = getRequestResponse.getResource();

    fetchedObject.setEcsMetrics(null);
    fetchedObject.setEc2InstanceNames(null);
    fetchedObject.setLoadBalancerMetrics(null);
    fetchedObject.setLambdaFunctionsMetrics(null);

    // Call PUT
    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId + "&appId=" + appId
        + "&stateType=" + CLOUD_WATCH + "&serviceConfigurationId=" + savedObjectUuid;
    log.info("PUT " + url);
    target = client.target(url);
    thrown.expect(Exception.class);
    getRequestBuilderWithAuthHeader(target).put(
        entity(fetchedObject, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    restResponse.getResource();
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testCloudWatchConfigurationWithOnlyLamdaMetrics() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url =
        API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + CLOUD_WATCH;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(cloudWatchCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<CloudWatchCVServiceConfiguration> getRequestResponse = getRequestBuilderWithAuthHeader(target).get(
        new GenericType<RestResponse<CloudWatchCVServiceConfiguration>>() {});
    CloudWatchCVServiceConfiguration fetchedObject = getRequestResponse.getResource();

    fetchedObject.setEcsMetrics(null);
    fetchedObject.setEc2InstanceNames(null);
    fetchedObject.setLoadBalancerMetrics(null);

    // Call PUT
    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId + "&appId=" + appId
        + "&stateType=" + CLOUD_WATCH + "&serviceConfigurationId=" + savedObjectUuid;
    log.info("PUT " + url);
    target = client.target(url);
    getRequestBuilderWithAuthHeader(target).put(
        entity(fetchedObject, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    assertThat(restResponse.getResource()).isNotNull();
  }

  @Test
  @Owner(developers = RAGHU, intermittent = true)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testElkConfiguration() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + ELK;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(elkCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<ElkCVConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<ElkCVConfiguration>>() {});
    ElkCVConfiguration fetchedObject = getRequestResponse.getResource();

    ElkCVConfiguration elkCVServiceConfiguration = fetchedObject;
    assertThat(fetchedObject.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(fetchedObject.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedObject.getAppId()).isEqualTo(appId);
    assertThat(fetchedObject.getEnvId()).isEqualTo(envId);
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(ELK);
    assertThat(fetchedObject.getAnalysisTolerance()).isNull();
    assertThat(elkCVServiceConfiguration.getServiceName()).isEqualTo("someServiceName");
    assertThat(fetchedObject.getBaselineStartMinute()).isEqualTo(91);
    assertThat(fetchedObject.getBaselineEndMinute()).isEqualTo(195);

    assertThat(fetchedObject.getQuery()).isEqualTo("query1");
    assertThat(fetchedObject.getIndex()).isEqualTo("filebeat-*");
    assertThat(fetchedObject.getHostnameField()).isEqualTo("host1");
    assertThat(fetchedObject.getMessageField()).isEqualTo("message1");
    assertThat(fetchedObject.getTimestampField()).isEqualTo("timestamp1");
    assertThat(fetchedObject.getTimestampFormat()).isEqualTo("timestamp_format1");

    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId;
    target = client.target(url);

    RestResponse<List<Object>> allConfigResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<Object>>>() {});
    List<Object> allConifgs = allConfigResponse.getResource();

    assertThat(allConifgs).hasSize(1);

    ElkCVConfiguration obj = JsonUtils.asObject(JsonUtils.asJson(allConifgs.get(0)), ElkCVConfiguration.class);

    assertThat(obj.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(obj.getAccountId()).isEqualTo(accountId);
    assertThat(obj.getAppId()).isEqualTo(appId);
    assertThat(obj.getEnvId()).isEqualTo(envId);
    assertThat(obj.getServiceId()).isEqualTo(serviceId);
    assertThat(obj.getStateType()).isEqualTo(ELK);
    assertThat(obj.getAnalysisTolerance()).isNull();
    assertThat(obj.getName()).isEqualTo("Config 1");

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId + "&appId=" + appId
        + "&stateType=" + ELK + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    elkCVServiceConfiguration.setName("Config 2");
    elkCVServiceConfiguration.setEnabled24x7(false);
    elkCVServiceConfiguration.setBaselineStartMinute(135);
    elkCVServiceConfiguration.setBaselineEndMinute(330);

    elkCVServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.LOW);
    elkCVServiceConfiguration.setQuery("query2");
    elkCVServiceConfiguration.setIndex("filebeat-*");
    elkCVServiceConfiguration.setHostnameField("host2");
    elkCVServiceConfiguration.setMessageField("message2");
    elkCVServiceConfiguration.setTimestampField("timestamp2");
    elkCVServiceConfiguration.setTimestampFormat("timestamp_format2");

    restResponse = getRequestBuilderWithAuthHeader(target).put(
        entity(elkCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});

    savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<ElkCVConfiguration>>() {});
    fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.isEnabled24x7()).isFalse();
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.LOW);
    assertThat(fetchedObject.getBaselineStartMinute()).isEqualTo(121);
    assertThat(fetchedObject.getBaselineEndMinute()).isEqualTo(330);
    assertThat(fetchedObject.getName()).isEqualTo("Config 2");
    assertThat(fetchedObject.getQuery()).isEqualTo("query2");
    assertThat(fetchedObject.getIndex()).isEqualTo("filebeat-*");
    assertThat(fetchedObject.getHostnameField()).isEqualTo("host2");
    assertThat(fetchedObject.getMessageField()).isEqualTo("message2");
    assertThat(fetchedObject.getTimestampField()).isEqualTo("timestamp2");
    assertThat(fetchedObject.getTimestampFormat()).isEqualTo("timestamp_format2");

    validateDeleteCVConfiguration(savedObjectUuid);
  }

  @Test
  @Owner(developers = PRANJAL, intermittent = true)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testBugSnagConfiguration() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + BUG_SNAG;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(bugsnagCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<BugsnagCVConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<BugsnagCVConfiguration>>() {});
    BugsnagCVConfiguration fetchedObject = getRequestResponse.getResource();

    BugsnagCVConfiguration cvConfiguration = fetchedObject;

    validateConfiguration(savedObjectUuid, cvConfiguration);

    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId;
    target = client.target(url);

    RestResponse<List<Object>> allConfigResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<Object>>>() {});
    List<Object> allConifgs = allConfigResponse.getResource();

    assertThat(allConifgs).hasSize(1);

    BugsnagCVConfiguration obj = JsonUtils.asObject(JsonUtils.asJson(allConifgs.get(0)), BugsnagCVConfiguration.class);

    assertThat(obj.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(obj.getAccountId()).isEqualTo(accountId);
    assertThat(obj.getAppId()).isEqualTo(appId);
    assertThat(obj.getEnvId()).isEqualTo(envId);
    assertThat(obj.getServiceId()).isEqualTo(serviceId);
    assertThat(obj.getStateType()).isEqualTo(BUG_SNAG);
    assertThat(obj.getAnalysisTolerance()).isNull();
    assertThat(obj.getName()).isEqualTo("Config 1");

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId + "&appId=" + appId
        + "&stateType=" + BUG_SNAG + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    cvConfiguration.setName("Config 2");
    cvConfiguration.setEnabled24x7(false);

    cvConfiguration.setAnalysisTolerance(AnalysisTolerance.LOW);
    cvConfiguration.setQuery("query2");
    cvConfiguration.setBaselineStartMinute(25931716);
    cvConfiguration.setBaselineEndMinute(25931835);

    restResponse = getRequestBuilderWithAuthHeader(target).put(
        entity(cvConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<BugsnagCVConfiguration>>() {});
    fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.isEnabled24x7()).isFalse();
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.LOW);
    assertThat(fetchedObject.getName()).isEqualTo("Config 2");
    assertThat(fetchedObject.getQuery()).isEqualTo("query2");
    assertThat(fetchedObject.getBaselineEndMinute()).isEqualTo(25931835);
    assertThat(fetchedObject.getBaselineStartMinute()).isEqualTo(25931716);

    validateDeleteCVConfiguration(savedObjectUuid);
  }

  @Test
  @Owner(developers = PRANJAL, intermittent = true)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testStackdriverConfiguration() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url =
        API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + STACK_DRIVER_LOG;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(stackdriverCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<StackdriverCVConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<StackdriverCVConfiguration>>() {});
    StackdriverCVConfiguration fetchedObject = getRequestResponse.getResource();

    StackdriverCVConfiguration cvConfiguration = fetchedObject;
    assertThat(fetchedObject.getStateType()).isEqualTo(STACK_DRIVER_LOG);
    validateConfiguration(savedObjectUuid, cvConfiguration);

    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId;
    target = client.target(url);

    RestResponse<List<Object>> allConfigResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<Object>>>() {});
    List<Object> allConifgs = allConfigResponse.getResource();

    assertThat(allConifgs).hasSize(1);

    StackdriverCVConfiguration obj =
        JsonUtils.asObject(JsonUtils.asJson(allConifgs.get(0)), StackdriverCVConfiguration.class);

    assertThat(obj.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(obj.getAccountId()).isEqualTo(accountId);
    assertThat(obj.getAppId()).isEqualTo(appId);
    assertThat(obj.getEnvId()).isEqualTo(envId);
    assertThat(obj.getServiceId()).isEqualTo(serviceId);
    assertThat(obj.getStateType()).isEqualTo(STACK_DRIVER_LOG);
    assertThat(obj.getAnalysisTolerance()).isNull();
    assertThat(obj.getName()).isEqualTo("Config 1");

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId + "&appId=" + appId
        + "&stateType=" + STACK_DRIVER_LOG + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    cvConfiguration.setName("Config 2");
    cvConfiguration.setEnabled24x7(false);

    cvConfiguration.setAnalysisTolerance(AnalysisTolerance.LOW);
    cvConfiguration.setQuery("query2");

    getRequestBuilderWithAuthHeader(target).put(
        entity(cvConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<StackdriverCVConfiguration>>() {});
    fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.isEnabled24x7()).isFalse();
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.LOW);
    assertThat(fetchedObject.getName()).isEqualTo("Config 2");
    assertThat(fetchedObject.getQuery()).isEqualTo("query2");

    validateDeleteCVConfiguration(savedObjectUuid);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testSplunkConfiguration() {
    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + SPLUNKV2;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(splunkCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<SplunkCVConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<SplunkCVConfiguration>>() {});
    SplunkCVConfiguration fetchedObject = getRequestResponse.getResource();

    SplunkCVConfiguration cvConfiguration = fetchedObject;
    assertThat(fetchedObject.getStateType()).isEqualTo(SPLUNKV2);
    validateConfiguration(savedObjectUuid, cvConfiguration);

    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId;
    target = client.target(url);

    RestResponse<List<Object>> allConfigResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<Object>>>() {});
    List<Object> allConifgs = allConfigResponse.getResource();

    assertThat(allConifgs).hasSize(1);

    SplunkCVConfiguration obj = JsonUtils.asObject(JsonUtils.asJson(allConifgs.get(0)), SplunkCVConfiguration.class);

    assertThat(obj.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(obj.getAccountId()).isEqualTo(accountId);
    assertThat(obj.getAppId()).isEqualTo(appId);
    assertThat(obj.getEnvId()).isEqualTo(envId);
    assertThat(obj.getServiceId()).isEqualTo(serviceId);
    assertThat(obj.getStateType()).isEqualTo(SPLUNKV2);
    assertThat(obj.getAnalysisTolerance()).isNull();
    assertThat(obj.getName()).isEqualTo("Config 1");
    assertThat(obj.getHostnameField()).isEqualTo("splunk_hostname");
    assertThat(obj.isAdvancedQuery()).isFalse();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId + "&appId=" + appId
        + "&stateType=" + SPLUNKV2 + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    cvConfiguration.setName("Config 2");
    cvConfiguration.setEnabled24x7(false);

    cvConfiguration.setAnalysisTolerance(AnalysisTolerance.LOW);
    cvConfiguration.setQuery("query2");
    cvConfiguration.setAdvancedQuery(true);

    getRequestBuilderWithAuthHeader(target).put(
        entity(cvConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<SplunkCVConfiguration>>() {});
    fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.isEnabled24x7()).isFalse();
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.LOW);
    assertThat(fetchedObject.getName()).isEqualTo("Config 2");
    assertThat(fetchedObject.getQuery()).isEqualTo("query2");
    assertThat(fetchedObject.isAdvancedQuery()).isTrue();

    validateDeleteCVConfiguration(savedObjectUuid);
  }

  private void validateDeleteCVConfiguration(String objectUuid) {
    WebTarget target;
    String delete_url = API_BASE + "/cv-configuration/" + objectUuid + "?accountId=" + accountId + "&appId=" + appId;
    target = client.target(delete_url);
    RestResponse<Boolean> response = getRequestBuilderWithAuthHeader(target).delete(new GenericType<RestResponse>() {});
    assertThat(response.getResource()).isEqualTo(true);

    delete_url =
        API_BASE + "/cv-configuration/" + UUID.randomUUID().toString() + "?accountId=" + accountId + "&appId=" + appId;
    target = client.target(delete_url);
    response = getRequestBuilderWithAuthHeader(target).delete(new GenericType<RestResponse>() {});
    assertThat(response.getResource()).isEqualTo(false);
  }

  private void validateConfiguration(String savedObjectUuid, LogsCVConfiguration cvConfiguration) {
    assertThat(cvConfiguration.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(cvConfiguration.getAccountId()).isEqualTo(accountId);
    assertThat(cvConfiguration.getAppId()).isEqualTo(appId);
    assertThat(cvConfiguration.getEnvId()).isEqualTo(envId);
    assertThat(cvConfiguration.getServiceId()).isEqualTo(serviceId);
    assertThat(cvConfiguration.getAnalysisTolerance()).isNull();
    assertThat(cvConfiguration.getServiceName()).isEqualTo("someServiceName");

    assertThat(cvConfiguration.getQuery()).isEqualTo("*exception*");
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testLogsConfiguration() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + SUMO;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(logsCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<LogsCVConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<LogsCVConfiguration>>() {});
    LogsCVConfiguration fetchedObject = getRequestResponse.getResource();

    LogsCVConfiguration logsCVConfiguration = fetchedObject;
    assertThat(fetchedObject.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(fetchedObject.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedObject.getAppId()).isEqualTo(appId);
    assertThat(fetchedObject.getEnvId()).isEqualTo(envId);
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(SUMO);
    assertThat(fetchedObject.getAnalysisTolerance()).isNull();
    assertThat(logsCVConfiguration.getConnectorName()).isEqualTo("someSettingAttributeName");
    assertThat(logsCVConfiguration.getServiceName()).isEqualTo("someServiceName");

    assertThat(fetchedObject.getQuery()).isEqualTo("query1");
    assertThat(fetchedObject.getBaselineStartMinute()).isEqualTo(91);
    assertThat(fetchedObject.getBaselineEndMinute()).isEqualTo(195);

    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId;
    target = client.target(url);

    RestResponse<List<Object>> allConfigResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<Object>>>() {});
    List<Object> allConifgs = allConfigResponse.getResource();

    assertThat(allConifgs).hasSize(1);

    LogsCVConfiguration obj = JsonUtils.asObject(JsonUtils.asJson(allConifgs.get(0)), LogsCVConfiguration.class);

    assertThat(obj.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(obj.getAccountId()).isEqualTo(accountId);
    assertThat(obj.getAppId()).isEqualTo(appId);
    assertThat(obj.getEnvId()).isEqualTo(envId);
    assertThat(obj.getServiceId()).isEqualTo(serviceId);
    assertThat(obj.getStateType()).isEqualTo(SUMO);
    assertThat(obj.getAnalysisTolerance()).isNull();
    assertThat(obj.getName()).isEqualTo("Config 1");
    assertThat(obj.getQuery()).isEqualTo("query1");
    assertThat(obj.getBaselineStartMinute()).isEqualTo(91);
    assertThat(obj.getBaselineEndMinute()).isEqualTo(195);

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId + "&appId=" + appId
        + "&stateType=" + SUMO + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    logsCVConfiguration.setName("Config 2");
    logsCVConfiguration.setEnabled24x7(false);

    logsCVConfiguration.setAnalysisTolerance(AnalysisTolerance.LOW);
    logsCVConfiguration.setQuery("query2");
    logsCVConfiguration.setBaselineStartMinute(106);
    logsCVConfiguration.setBaselineEndMinute(210);

    restResponse = getRequestBuilderWithAuthHeader(target).put(
        entity(logsCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});

    savedObjectUuid = restResponse.getResource();
    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId + "&appId=" + appId
        + "&stateType=" + SUMO + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);

    getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<LogsCVConfiguration>>() {});
    fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.isEnabled24x7()).isFalse();
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(AnalysisTolerance.LOW);
    assertThat(fetchedObject.getName()).isEqualTo("Config 2");
    assertThat(fetchedObject.getQuery()).isEqualTo("query2");
    assertThat(fetchedObject.getBaselineStartMinute()).isEqualTo(106);
    assertThat(fetchedObject.getBaselineEndMinute()).isEqualTo(210);

    validateDeleteCVConfiguration(savedObjectUuid);
  }

  @Test
  @Owner(developers = UNKNOWN)
  @Repeat(times = 5, successes = 1)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public <T extends CVConfiguration> void testLogsConfigurationValidUntil() throws InterruptedException {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + SUMO;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(logsCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<LogsCVConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<LogsCVConfiguration>>() {});
    LogsCVConfiguration fetchedObject = getRequestResponse.getResource();

    LogsCVConfiguration responseConfig = fetchedObject;
    assertThat(fetchedObject.getUuid()).isEqualTo(savedObjectUuid);
    assertThat(fetchedObject.getAccountId()).isEqualTo(accountId);
    assertThat(fetchedObject.getAppId()).isEqualTo(appId);
    assertThat(fetchedObject.getEnvId()).isEqualTo(envId);
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(SUMO);
    assertThat(fetchedObject.getAnalysisTolerance()).isNull();
    assertThat(responseConfig.getConnectorName()).isEqualTo("someSettingAttributeName");
    assertThat(responseConfig.getServiceName()).isEqualTo("someServiceName");

    wingsPersistence.updateField(LogsCVConfiguration.class, savedObjectUuid, "validUntil",
        Date.from(OffsetDateTime.now().plusSeconds(1).toInstant()));
    Date validUntil = Date.from(OffsetDateTime.now().plusSeconds(1).toInstant());

    List<CVConfiguration> cvConfigurationList =
        wingsPersistence.createQuery(CVConfiguration.class, excludeAuthority).filter("_id", fetchedObject).asList();

    cvConfigurationList.forEach(configRecords -> {
      if (configRecords.getValidUntil() != null) {
        assertThat(validUntil.getTime() > configRecords.getValidUntil().getTime()).isTrue();
      }
    });
  }

  @Test
  @Owner(developers = RAGHU, intermittent = true)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testListConfig() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String otherEnvId = generateUuid();
    String appId = generateUuid();

    String newRelicApplicationId = generateUuid();
    newRelicCVServiceConfiguration = new NewRelicCVServiceConfiguration();
    newRelicCVServiceConfiguration.setAppId(appId);
    newRelicCVServiceConfiguration.setEnvId(otherEnvId);
    newRelicCVServiceConfiguration.setServiceId(serviceId);
    newRelicCVServiceConfiguration.setEnabled24x7(true);
    newRelicCVServiceConfiguration.setApplicationId(newRelicApplicationId);
    newRelicCVServiceConfiguration.setConnectorId(settingAttributeId);
    newRelicCVServiceConfiguration.setMetrics(Collections.singletonList("apdexScore"));
    newRelicCVServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.MEDIUM);
    newRelicCVServiceConfiguration.setName("name2");

    appDynamicsCVServiceConfiguration = new AppDynamicsCVServiceConfiguration();
    appDynamicsCVServiceConfiguration.setAppId(appId);
    appDynamicsCVServiceConfiguration.setEnvId(otherEnvId);
    appDynamicsCVServiceConfiguration.setServiceId(serviceId);
    appDynamicsCVServiceConfiguration.setEnabled24x7(true);
    appDynamicsCVServiceConfiguration.setAppDynamicsApplicationId(appDynamicsApplicationId);
    appDynamicsCVServiceConfiguration.setTierId(generateUuid());
    appDynamicsCVServiceConfiguration.setConnectorId(generateUuid());
    appDynamicsCVServiceConfiguration.setStateType(APP_DYNAMICS);
    appDynamicsCVServiceConfiguration.setAnalysisTolerance(AnalysisTolerance.HIGH);
    appDynamicsCVServiceConfiguration.setName("name1");

    // Adding a workflow cvConfiguration. And verify this configuration is not returned from the REST API
    AppDynamicsCVServiceConfiguration appDynamicsCVServiceConfigurationFromWorkflow =
        new AppDynamicsCVServiceConfiguration();
    appDynamicsCVServiceConfigurationFromWorkflow.setAppId(appId);
    appDynamicsCVServiceConfigurationFromWorkflow.setEnvId(envId);
    appDynamicsCVServiceConfigurationFromWorkflow.setServiceId(serviceId);
    appDynamicsCVServiceConfigurationFromWorkflow.setEnabled24x7(true);
    appDynamicsCVServiceConfigurationFromWorkflow.setWorkflowConfig(true);
    appDynamicsCVServiceConfigurationFromWorkflow.setAppDynamicsApplicationId(appDynamicsApplicationId);
    appDynamicsCVServiceConfigurationFromWorkflow.setTierId(generateUuid());
    appDynamicsCVServiceConfigurationFromWorkflow.setConnectorId(generateUuid());
    appDynamicsCVServiceConfigurationFromWorkflow.setStateType(APP_DYNAMICS);
    appDynamicsCVServiceConfigurationFromWorkflow.setAnalysisTolerance(AnalysisTolerance.HIGH);
    appDynamicsCVServiceConfigurationFromWorkflow.setName("name");

    // Save 2 cvConfigs with the same appId but different envIds
    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + NEW_RELIC;
    WebTarget target = client.target(url);
    getRequestBuilderWithAuthHeader(target).post(
        entity(newRelicCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + APP_DYNAMICS;
    target = client.target(url);
    getRequestBuilderWithAuthHeader(target).post(
        entity(appDynamicsCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});

    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + APP_DYNAMICS;
    target = client.target(url);
    getRequestBuilderWithAuthHeader(target).post(
        entity(appDynamicsCVServiceConfigurationFromWorkflow, APPLICATION_JSON),
        new GenericType<RestResponse<String>>() {});

    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId;
    target = client.target(url);

    RestResponse<List<Object>> allConfigResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<Object>>>() {});
    List<Object> allConfigs = allConfigResponse.getResource();

    assertThat(allConfigs).hasSize(2);

    NewRelicCVServiceConfiguration obj =
        JsonUtils.asObject(JsonUtils.asJson(allConfigs.get(0)), NewRelicCVServiceConfiguration.class);
    assertThat(obj.getAppId()).isEqualTo(appId);
    assertThat(obj.getEnvId()).isEqualTo(otherEnvId);

    AppDynamicsCVServiceConfiguration appDObject =
        JsonUtils.asObject(JsonUtils.asJson(allConfigs.get(1)), AppDynamicsCVServiceConfiguration.class);
    assertThat(appDObject.getAppId()).isEqualTo(appId);
    assertThat(appDObject.getEnvId()).isEqualTo(otherEnvId);

    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&envId=" + otherEnvId;
    target = client.target(url);

    RestResponse<List<Object>> listOfConfigsByEnvResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<Object>>>() {});
    List<Object> listOfConfigsByEnv = listOfConfigsByEnvResponse.getResource();

    assertThat(listOfConfigsByEnv).hasSize(2);

    obj = JsonUtils.asObject(JsonUtils.asJson(listOfConfigsByEnv.get(0)), NewRelicCVServiceConfiguration.class);
    assertThat(obj.getAppId()).isEqualTo(appId);
    assertThat(obj.getEnvId()).isEqualTo(otherEnvId);

    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&envId=" + otherEnvId;
    target = client.target(url);

    listOfConfigsByEnvResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<Object>>>() {});
    listOfConfigsByEnv = listOfConfigsByEnvResponse.getResource();

    assertThat(listOfConfigsByEnv).hasSize(2);

    appDObject =
        JsonUtils.asObject(JsonUtils.asJson(listOfConfigsByEnv.get(0)), AppDynamicsCVServiceConfiguration.class);
    assertThat(appDObject.getAppId()).isEqualTo(appId);
    assertThat(appDObject.getEnvId()).isEqualTo(otherEnvId);

    // This call to list configs should fetch only the app dynamics config
    url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + APP_DYNAMICS;
    target = client.target(url);

    listOfConfigsByEnvResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<Object>>>() {});
    listOfConfigsByEnv = listOfConfigsByEnvResponse.getResource();

    assertThat(listOfConfigsByEnv).hasSize(1);

    appDObject =
        JsonUtils.asObject(JsonUtils.asJson(listOfConfigsByEnv.get(0)), AppDynamicsCVServiceConfiguration.class);
    assertThat(appDObject.getAppId()).isEqualTo(appId);
    assertThat(appDObject.getEnvId()).isEqualTo(otherEnvId);
    assertThat(appDObject.getStateType()).isEqualTo(APP_DYNAMICS);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testLogsConfigurationResetBaseline() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + SUMO;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(logsCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    LogsCVConfiguration updateBaseline = null;

    url = API_BASE + "/cv-configuration/reset-baseline?cvConfigId=" + generateUuid() + "&accountId=" + accountId
        + "&appId=" + appId;
    target = client.target(url);
    try {
      getRequestBuilderWithAuthHeader(target).post(
          entity(updateBaseline, APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
      fail("Did not fail for invalid cvConfig");
    } catch (Exception e) {
      // exepected
    }

    url = API_BASE + "/cv-configuration/reset-baseline?cvConfigId=" + savedObjectUuid + "&accountId=" + accountId
        + "&appId=" + appId;
    target = client.target(url);
    try {
      getRequestBuilderWithAuthHeader(target).post(
          entity(updateBaseline, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
      fail("Did not fail for null payload");
    } catch (Exception e) {
      // exepected
    }

    updateBaseline = new LogsCVConfiguration();
    target = client.target(url);
    try {
      getRequestBuilderWithAuthHeader(target).post(
          entity(updateBaseline, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
      fail("Did not fail for zero baseline");
    } catch (Exception e) {
      // exepected
    }

    updateBaseline.setBaselineStartMinute(16);
    target = client.target(url);
    try {
      getRequestBuilderWithAuthHeader(target).post(
          entity(updateBaseline, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
      fail("Did not fail for  zero baseline end minute");
    } catch (Exception e) {
      // exepected
    }

    updateBaseline.setBaselineEndMinute(20);
    target = client.target(url);
    try {
      getRequestBuilderWithAuthHeader(target).post(
          entity(updateBaseline, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
      fail("Did not fail for invalid baseline");
    } catch (Exception e) {
      // exepected
    }

    for (int i = 0; i < 100; i++) {
      final LearningEngineAnalysisTask learningEngineAnalysisTask =
          LearningEngineAnalysisTask.builder().cvConfigId(savedObjectUuid).state_execution_id(generateUuid()).build();
      learningEngineAnalysisTask.setAppId(appId);
      wingsPersistence.save(learningEngineAnalysisTask);
      LogDataRecord logDataRecord = new LogDataRecord();
      logDataRecord.setCvConfigId(savedObjectUuid);
      logDataRecord.setAppId(appId);
      logDataRecord.setStateExecutionId(generateUuid());
      wingsPersistence.save(logDataRecord);
      wingsPersistence.save(LogMLAnalysisRecord.builder()
                                .cvConfigId(savedObjectUuid)
                                .appId(appId)
                                .stateExecutionId(generateUuid())
                                .logCollectionMinute(i)
                                .build());
    }

    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                   .filter(LearningEngineAnalysisTaskKeys.cvConfigId, savedObjectUuid)
                   .filter("appId", appId)
                   .asList()
                   .size())
        .isEqualTo(100);

    assertThat(wingsPersistence.createQuery(LogDataRecord.class)
                   .filter(LogDataRecordKeys.cvConfigId, savedObjectUuid)
                   .filter("appId", appId)
                   .asList()
                   .size())
        .isEqualTo(100);

    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class)
                   .filter(LogMLAnalysisRecordKeys.cvConfigId, savedObjectUuid)
                   .filter("appId", appId)
                   .asList()
                   .size())
        .isEqualTo(100);

    updateBaseline.setBaselineEndMinute(38);
    target = client.target(url);
    final RestResponse<String> updateResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(updateBaseline, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    assertThat(savedObjectUuid).isNotEqualTo(updateResponse.getResource());
    savedObjectUuid = updateResponse.getResource();

    sleep(ofMillis(5000));
    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;

    target = client.target(url);
    RestResponse<LogsCVConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<LogsCVConfiguration>>() {});
    assertThat(getRequestResponse.getResource().getBaselineStartMinute())
        .isEqualTo(updateBaseline.getBaselineStartMinute());
    assertThat(getRequestResponse.getResource().getBaselineEndMinute())
        .isEqualTo(updateBaseline.getBaselineEndMinute());

    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                   .filter(LearningEngineAnalysisTaskKeys.cvConfigId, savedObjectUuid)
                   .filter("appId", appId)
                   .asList()
                   .size())
        .isEqualTo(0);

    assertThat(wingsPersistence.createQuery(LogDataRecord.class)
                   .filter(LogDataRecordKeys.cvConfigId, savedObjectUuid)
                   .filter("appId", appId)
                   .asList()
                   .size())
        .isEqualTo(0);

    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class)
                   .filter(LogMLAnalysisRecordKeys.cvConfigId, savedObjectUuid)
                   .filter("appId", appId)
                   .asList()
                   .size())
        .isEqualTo(16);
    wingsPersistence.createQuery(LogMLAnalysisRecord.class)
        .filter(LogMLAnalysisRecordKeys.cvConfigId, savedObjectUuid)
        .filter("appId", appId)
        .asList()
        .forEach(logMLAnalysisRecord -> assertThat(logMLAnalysisRecord.isDeprecated()).isTrue());

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<LogsCVConfiguration>>() {});
    LogsCVConfiguration fetchedObject = getRequestResponse.getResource();

    logsCVConfiguration.setAccountId(accountId);
    logsCVConfiguration.setStateType(StateType.SUMO);

    assertThat(fetchedObject.getName()).isEqualTo(logsCVConfiguration.getName());
    assertThat(fetchedObject.getAccountId()).isEqualTo(logsCVConfiguration.getAccountId());
    assertThat(fetchedObject.getConnectorId()).isEqualTo(logsCVConfiguration.getConnectorId());
    assertThat(fetchedObject.getEnvId()).isEqualTo(logsCVConfiguration.getEnvId());
    assertThat(fetchedObject.getServiceId()).isEqualTo(logsCVConfiguration.getServiceId());
    assertThat(fetchedObject.getStateType()).isEqualTo(logsCVConfiguration.getStateType());
    assertThat(fetchedObject.getAnalysisTolerance()).isEqualTo(logsCVConfiguration.getAnalysisTolerance());
    assertThat(fetchedObject.isEnabled24x7()).isEqualTo(logsCVConfiguration.isEnabled24x7());
    assertThat(fetchedObject.getComparisonStrategy()).isEqualTo(logsCVConfiguration.getComparisonStrategy());
    assertThat(fetchedObject.getContextId()).isEqualTo(logsCVConfiguration.getContextId());
    assertThat(fetchedObject.isWorkflowConfig()).isEqualTo(logsCVConfiguration.isWorkflowConfig());
    assertThat(fetchedObject.isAlertEnabled()).isEqualTo(logsCVConfiguration.isAlertEnabled());
    assertThat(fetchedObject.getAlertThreshold()).isEqualTo(logsCVConfiguration.getAlertThreshold());
    assertThat(fetchedObject.getSnoozeStartTime()).isEqualTo(logsCVConfiguration.getSnoozeStartTime());
    assertThat(fetchedObject.getSnoozeEndTime()).isEqualTo(logsCVConfiguration.getSnoozeEndTime());
    assertThat(fetchedObject.getQuery()).isEqualTo(logsCVConfiguration.getQuery());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testLogsConfigurationUpdateAlert() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=" + appId + "&stateType=" + SUMO;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<String> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(logsCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
    String savedObjectUuid = restResponse.getResource();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    RestResponse<LogsCVConfiguration> getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<LogsCVConfiguration>>() {});
    LogsCVConfiguration fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.isAlertEnabled()).isFalse();
    assertThat(fetchedObject.getAlertThreshold()).isEqualTo(0.1);
    assertThat(fetchedObject.getSnoozeStartTime()).isEqualTo(0);
    assertThat(fetchedObject.getSnoozeEndTime()).isEqualTo(0);

    CVConfiguration cvConfiguration = new CVConfiguration();
    cvConfiguration.setAlertThreshold(0.5);
    cvConfiguration.setAlertEnabled(true);
    cvConfiguration.setSnoozeStartTime(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5));
    cvConfiguration.setSnoozeEndTime(System.currentTimeMillis());

    url = API_BASE + "/cv-configuration/update-alert-setting?accountId=" + accountId + "&cvConfigId=" + savedObjectUuid;
    target = client.target(url);
    RestResponse<Boolean> updateResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(cvConfiguration, APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    assertThat(updateResponse.getResource()).isTrue();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<LogsCVConfiguration>>() {});
    fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.isAlertEnabled()).isTrue();
    assertThat(fetchedObject.getAlertThreshold()).isEqualTo(0.5);
    assertThat(fetchedObject.getSnoozeStartTime()).isEqualTo(0);
    assertThat(fetchedObject.getSnoozeEndTime()).isEqualTo(0);

    url = API_BASE + "/cv-configuration/update-snooze?accountId=" + accountId + "&cvConfigId=" + savedObjectUuid;
    target = client.target(url);
    updateResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(cvConfiguration, APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    assertThat(updateResponse.getResource()).isTrue();

    url = API_BASE + "/cv-configuration/" + savedObjectUuid + "?accountId=" + accountId
        + "&serviceConfigurationId=" + savedObjectUuid;
    target = client.target(url);
    getRequestResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<LogsCVConfiguration>>() {});
    fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject.isAlertEnabled()).isTrue();
    assertThat(fetchedObject.getAlertThreshold()).isEqualTo(0.5);
    assertThat(fetchedObject.getSnoozeStartTime()).isEqualTo(cvConfiguration.getSnoozeStartTime());
    assertThat(fetchedObject.getSnoozeEndTime()).isEqualTo(cvConfiguration.getSnoozeEndTime());
  }

  @Test
  @Owner(developers = RAGHU, intermittent = true)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("Ignoring for now as its flaky")
  public void testListConfigurations() {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());
    String testUuid = generateUuid();
    log.info("testUuid {}", testUuid);
    int numOfApplications = 2;
    int numOfEnvs = 3;

    for (int i = 0; i < numOfApplications; i++) {
      for (int j = 0; j < numOfEnvs; j++) {
        createNewRelicConfig(true);
        newRelicCVServiceConfiguration.setEnvId("env" + j + testUuid);
        newRelicCVServiceConfiguration.setName("NRTestConfig" + testUuid);
        String url = API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=app" + i + testUuid
            + "&stateType=" + NEW_RELIC;
        WebTarget target = client.target(url);
        getRequestBuilderWithAuthHeader(target).post(
            entity(newRelicCVServiceConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
        url =
            API_BASE + "/cv-configuration?accountId=" + accountId + "&appId=app" + i + testUuid + "&stateType=" + SUMO;
        target = client.target(url);
        createLogsCVConfig(true);
        logsCVConfiguration.setEnvId("env" + j + testUuid);
        logsCVConfiguration.setName("SumoTestConfig" + testUuid);
        getRequestBuilderWithAuthHeader(target).post(
            entity(logsCVConfiguration, APPLICATION_JSON), new GenericType<RestResponse<String>>() {});
      }
    }
    assertThat(wingsPersistence.createQuery(CVConfiguration.class)
                   .filter(CVConfigurationKeys.name, "NRTestConfig" + testUuid)
                   .count())
        .isEqualTo(numOfApplications * numOfEnvs);
    assertThat(wingsPersistence.createQuery(CVConfiguration.class)
                   .filter(CVConfigurationKeys.name, "SumoTestConfig" + testUuid)
                   .count())
        .isEqualTo(numOfApplications * numOfEnvs);

    // ask for all the cvConfigs for 1 app
    String url =
        API_BASE + "/cv-configuration/list-cv-configurations?accountId=" + accountId + "&appIds=app0" + testUuid;
    WebTarget target = client.target(url);
    RestResponse<List<CVConfiguration>> restResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<CVConfiguration>>>() {});
    List<CVConfiguration> cvConfigurations = restResponse.getResource();
    assertThat(cvConfigurations).hasSize(numOfEnvs * 2);
    for (int i = 0; i < numOfEnvs; i++) {
      assertThat(cvConfigurations.get(i * 2).getAppId()).isEqualTo("app0" + testUuid);
      assertThat(cvConfigurations.get(i * 2).getEnvId()).isEqualTo("env" + i + testUuid);
      assertThat(cvConfigurations.get(i * 2 + 1).getAppId()).isEqualTo("app0" + testUuid);
      assertThat(cvConfigurations.get(i * 2 + 1).getEnvId()).isEqualTo("env" + i + testUuid);
    }

    // ask for all the cvConfigs for 2 apps
    url = API_BASE + "/cv-configuration/list-cv-configurations?accountId=" + accountId + "&appIds=app0" + testUuid
        + "&appIds=app1" + testUuid;
    target = client.target(url);
    restResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<CVConfiguration>>>() {});
    cvConfigurations = restResponse.getResource();
    assertThat(cvConfigurations).hasSize(numOfEnvs * 2 * 2);
    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < numOfEnvs; j++) {
        final int index = i * numOfEnvs * 2 + j * 2;
        assertThat(cvConfigurations.get(index).getAppId()).isEqualTo("app" + i + testUuid);
        assertThat(cvConfigurations.get(index).getEnvId()).isEqualTo("env" + j + testUuid);
        assertThat(cvConfigurations.get(index + 1).getAppId()).isEqualTo("app" + i + testUuid);
        assertThat(cvConfigurations.get(index + 1).getEnvId()).isEqualTo("env" + j + testUuid);
      }
    }

    // ask for all the cvConfigs for 2 apps and 2 envs
    url = API_BASE + "/cv-configuration/list-cv-configurations?accountId=" + accountId + "&appIds=app0" + testUuid
        + "&appIds=app1" + testUuid + "&envIds=env0" + testUuid + "&envIds=env1" + testUuid;
    target = client.target(url);
    restResponse =
        getRequestBuilderWithAuthHeader(target).get(new GenericType<RestResponse<List<CVConfiguration>>>() {});
    cvConfigurations = restResponse.getResource();
    assertThat(cvConfigurations).hasSize(8);

    for (int i = 0; i < 2; i++) {
      for (int j = 0; j < 2; j++) {
        final int index = i * 2 * 2 + j * 2;
        assertThat(cvConfigurations.get(index).getAppId()).isEqualTo("app" + j + testUuid);
        assertThat(cvConfigurations.get(index).getEnvId()).isEqualTo("env" + i + testUuid);
        assertThat(cvConfigurations.get(index + 1).getAppId()).isEqualTo("app" + j + testUuid);
        assertThat(cvConfigurations.get(index + 1).getEnvId()).isEqualTo("env" + i + testUuid);
      }
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void testCustomThresholdsCrud() throws UnsupportedEncodingException {
    String txnName = URLEncoder.encode("some valid txn / with slash", StandardCharsets.UTF_8.name());
    String metricName = "some valid metric / with slash";
    String cvConfigId = generateUuid();
    final TimeSeriesMetricDefinition timeSeriesMetricDefinition =
        TimeSeriesMetricDefinition.builder()
            .metricType(MetricType.THROUGHPUT)
            .metricName(metricName)
            .tags(Sets.newHashSet("tag1", "tag2"))
            .customThresholds(Lists.newArrayList(Threshold.builder()
                                                     .comparisonType(ThresholdComparisonType.DELTA)
                                                     .thresholdType(ThresholdType.ALERT_WHEN_HIGHER)
                                                     .ml(10.0)
                                                     .build(),
                Threshold.builder()
                    .comparisonType(ThresholdComparisonType.RATIO)
                    .thresholdType(ThresholdType.ALERT_WHEN_LOWER)
                    .ml(5.0)
                    .build()))
            .build();

    String url = API_BASE + "/timeseries/threshold?accountId=" + accountId + "&appId=" + appId + "&stateType="
        + DATA_DOG + "&serviceId=" + serviceId + "&transactionName=" + txnName + "&cvConfigId=" + cvConfigId;
    log.info("POST " + url);
    WebTarget target = client.target(url);
    RestResponse<Boolean> restResponse = getRequestBuilderWithAuthHeader(target).post(
        entity(timeSeriesMetricDefinition, APPLICATION_JSON), new GenericType<RestResponse<Boolean>>() {});
    assertThat(restResponse.getResource()).isTrue();

    url = API_BASE + "/timeseries/threshold?accountId=" + accountId + "&appId=" + appId + "&stateType=" + DATA_DOG
        + "&serviceId=" + serviceId + "&transactionName=" + txnName + "&cvConfigId=" + cvConfigId
        + "&metricName=" + URLEncoder.encode(metricName, StandardCharsets.UTF_8.name());

    target = client.target(url);
    RestResponse<TimeSeriesMLTransactionThresholds> getRequestResponse = getRequestBuilderWithAuthHeader(target).get(
        new GenericType<RestResponse<TimeSeriesMLTransactionThresholds>>() {});
    TimeSeriesMLTransactionThresholds fetchedObject = getRequestResponse.getResource();
    assertThat(fetchedObject).isNotNull();
    assertThat(fetchedObject.getServiceId()).isEqualTo(serviceId);
    assertThat(fetchedObject.getStateType()).isEqualTo(DATA_DOG);
    assertThat(fetchedObject.getTransactionName()).isEqualTo(URLDecoder.decode(txnName, StandardCharsets.UTF_8.name()));
    assertThat(fetchedObject.getMetricName()).isEqualTo(metricName);
    assertThat(fetchedObject.getCvConfigId()).isEqualTo(cvConfigId);
    assertThat(fetchedObject.getThresholds()).isEqualTo(timeSeriesMetricDefinition);

    // delete and get
    target = client.target(url);
    restResponse = getRequestBuilderWithAuthHeader(target).delete(new GenericType<RestResponse<Boolean>>() {});
    assertThat(restResponse.getResource()).isTrue();

    getRequestResponse = getRequestBuilderWithAuthHeader(target).get(
        new GenericType<RestResponse<TimeSeriesMLTransactionThresholds>>() {});
    assertThat(getRequestResponse.getResource()).isNull();
    assertThat(wingsPersistence.createQuery(TimeSeriesMLTransactionThresholds.class, excludeAuthority)
                   .filter(TimeSeriesMLTransactionThresholdKeys.cvConfigId, cvConfigId)
                   .get())
        .isNull();
  }
}
