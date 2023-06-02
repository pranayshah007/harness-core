/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRANJAL;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.service.impl.verification.CVConfigurationServiceImplTestBase.createCustomLogsConfig;
import static software.wings.utils.StackDriverUtils.createStackDriverConfig;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.VerificationOperationException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.time.Timestamp;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.APMFetchConfig;
import software.wings.WingsBaseTest;
import software.wings.beans.APMValidateCollectorConfig;
import software.wings.beans.APMVerificationConfig;
import software.wings.beans.ApmMetricCollectionInfo;
import software.wings.beans.ApmResponseMapping;
import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.AwsConfig;
import software.wings.beans.DatadogConfig;
import software.wings.beans.Environment;
import software.wings.beans.GcpConfig;
import software.wings.beans.LogCollectionInfo;
import software.wings.beans.LogResponseMapping;
import software.wings.beans.NewRelicConfig;
import software.wings.beans.PrometheusConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.apm.Method;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.MetricType;
import software.wings.service.impl.apm.APMDataCollectionInfo;
import software.wings.service.impl.apm.APMSetupTestNodeData;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.impl.appdynamics.AppdynamicsDataCollectionInfo;
import software.wings.service.impl.cloudwatch.CloudWatchDataCollectionInfo;
import software.wings.service.impl.datadog.DataDogSetupTestNodeData;
import software.wings.service.impl.log.CustomLogSetupTestNodeData;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfo;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.CloudWatchService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.datadog.DatadogService;
import software.wings.service.intfc.prometheus.PrometheusAnalysisService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.sm.StateType;
import software.wings.sm.states.DatadogState;
import software.wings.verification.appdynamics.AppDynamicsCVServiceConfiguration;
import software.wings.verification.cloudwatch.CloudWatchCVServiceConfiguration;
import software.wings.verification.datadog.DatadogCVServiceConfiguration;
import software.wings.verification.log.CustomLogCVServiceConfiguration;
import software.wings.verification.newrelic.NewRelicCVServiceConfiguration;
import software.wings.verification.prometheus.PrometheusCVServiceConfiguration;
import software.wings.verification.stackdriver.StackDriverMetricCVConfiguration;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @author Praveen 9/6/18
 */
public class APMVerificationServiceImplTest extends WingsBaseTest {
  @Mock private SettingsService mockSettingsService;
  @Mock private DelegateProxyFactory mockDelegateProxyFactory;
  @Mock private APMDelegateService mockAPMDelegateService;
  @Mock private DelegateService mockDelegateService;
  @Mock private WaitNotifyEngine mockWaitNotifyEngine;
  @Mock private SecretManager mockSecretManager;
  @Mock private CloudWatchService cloudWatchService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private CVActivityLogService cvActivityLogService;
  @Mock private DatadogService datadogService;
  @Mock private EnvironmentService environmentService;
  @Mock private AccountService accountService;
  @Mock MLServiceUtils mlServiceUtils;
  @InjectMocks ContinuousVerificationServiceImpl service;
  @Inject WingsPersistence wingsPersistence;
  @Inject private APMDelegateService apmDelegateService;
  @Inject private PrometheusAnalysisService prometheusAnalysisService;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    FieldUtils.writeField(service, "wingsPersistence", wingsPersistence, true);
    FieldUtils.writeField(service, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(service, "cvActivityLogService", cvActivityLogService, true);
    FieldUtils.writeField(service, "prometheusAnalysisService", prometheusAnalysisService, true);
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(cvActivityLogService.getLoggerByStateExecutionId(any(), any())).thenReturn(mock(CVActivityLogger.class));
    when(cvActivityLogService.getLoggerByCVConfigId(any(), any(), anyLong())).thenReturn(mock(CVActivityLogger.class));
    when(environmentService.get(any(), any()))
        .thenReturn(Environment.Builder.anEnvironment().environmentType(EnvironmentType.PROD).build());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeDataValidCase() {
    APMVerificationConfig config = new APMVerificationConfig();
    config.setValidationUrl("this is a testurl");
    config.setUrl("this is a test url");
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(config);

    String dummyResponseString = "{ \"key1\":1.45, \"time\":1234567}";

    APMFetchConfig fetchConfig = APMFetchConfig.builder().url("testFetchURL.com").build();

    APMSetupTestNodeData nodeData = APMSetupTestNodeData.builder()
                                        .fetchConfig(fetchConfig)
                                        .apmMetricCollectionInfo(ApmMetricCollectionInfo.builder()
                                                                     .metricName("name")
                                                                     .collectionUrl("testURL")
                                                                     .responseMapping(ApmResponseMapping.builder()
                                                                                          .metricValueJsonPath("key1")
                                                                                          .timestampJsonPath("time")
                                                                                          .txnNameFieldValue("txnName")
                                                                                          .build())
                                                                     .build())
                                        .build();

    // setup
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(mockAPMDelegateService);
    when(mockAPMDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenReturn(dummyResponseString);

    // execute
    VerificationNodeDataSetupResponse response =
        service.getDataForNode("accountId", "serverConfigId", nodeData, DelegateStateType.APM_VERIFICATION);

    // verify
    assertThat(response).isNotNull();
    assertThat(response.getLoadResponse().isLoadPresent()).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeDataDatadog() throws Exception {
    DataDogSetupTestNodeData nodeData =
        DataDogSetupTestNodeData.builder()
            .deploymentType("KUBERNETES")
            .metrics("docker.mem.rss")
            .fromTime(Timestamp.currentMinuteBoundary())
            .toTime(Timestamp.currentMinuteBoundary())
            .instanceElement(SetupTestNodeData.Instance.builder()
                                 .instanceDetails(InstanceDetails.builder().hostName("sampleHostname").build())
                                 .build())
            .stateType(DelegateStateType.DATA_DOG)
            .guid(generateUuid())
            .build();
    when(mlServiceUtils.getHostName(eq(nodeData))).thenReturn("sampleHostname");
    DatadogConfig ddConfig = DatadogConfig.builder().url("sampleUrl.com").build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(ddConfig);

    String textLoad = FileUtils.readFileToString(
        new File("400-rest/src/test/resources/apm/datadog_sample_response_load.json"), Charsets.UTF_8);

    // setup
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(mockAPMDelegateService);
    when(mockAPMDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenReturn(textLoad);
    when(datadogService.getConcatenatedListOfMetricsForValidation(any(), any(), any(), any()))
        .thenReturn("docker.mem.rss");
    // execute
    VerificationNodeDataSetupResponse response =
        service.getDataForNode("accountId", "serverConfigId", nodeData, DelegateStateType.DATA_DOG);

    assertThat(response).isNotNull();
    assertThat(response.isProviderReachable()).isTrue();
    assertThat(response.getLoadResponse()).isNotNull();
    assertThat(response.getLoadResponse().isLoadPresent()).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeDataDatadogEmptyResponse() throws Exception {
    DataDogSetupTestNodeData nodeData =
        DataDogSetupTestNodeData.builder()
            .deploymentType("KUBERNETES")
            .metrics("docker.mem.rss")
            .fromTime(Timestamp.currentMinuteBoundary())
            .toTime(Timestamp.currentMinuteBoundary())
            .instanceElement(SetupTestNodeData.Instance.builder()
                                 .instanceDetails(InstanceDetails.builder().hostName("sampleHostname").build())
                                 .build())
            .stateType(DelegateStateType.DATA_DOG)
            .guid(generateUuid())
            .build();
    when(mlServiceUtils.getHostName(eq(nodeData))).thenReturn("sampleHostname");
    DatadogConfig ddConfig = DatadogConfig.builder().url("sampleUrl.com").build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(ddConfig);

    String textLoad = Resources.toString(
        APMVerificationServiceImplTest.class.getResource("/apm/datadog-emptyResponse.json"), Charsets.UTF_8);

    // setup
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(mockAPMDelegateService);
    when(mockAPMDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenReturn(textLoad);
    when(datadogService.getConcatenatedListOfMetricsForValidation(any(), any(), any(), any()))
        .thenReturn("docker.mem.rss");
    // execute
    VerificationNodeDataSetupResponse response =
        service.getDataForNode("accountId", "serverConfigId", nodeData, DelegateStateType.DATA_DOG);

    assertThat(response).isNotNull();
    assertThat(response.isProviderReachable()).isTrue();
    assertThat(response.getLoadResponse()).isNotNull();
    assertThat(response.getLoadResponse().isLoadPresent()).isFalse();
  }

  @Test(expected = VerificationOperationException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeDataDatadog_invalidMetricsSetup() throws Exception {
    DataDogSetupTestNodeData nodeData =
        DataDogSetupTestNodeData.builder()
            .deploymentType("KUBERNETES")
            .metrics("docker.mem.rss")
            .fromTime(Timestamp.currentMinuteBoundary())
            .toTime(Timestamp.currentMinuteBoundary())
            .instanceElement(SetupTestNodeData.Instance.builder()
                                 .instanceDetails(InstanceDetails.builder().hostName("sampleHostname").build())
                                 .build())
            .stateType(DelegateStateType.DATA_DOG)
            .guid(generateUuid())
            .build();
    Map<String, Set<DatadogState.Metric>> customMetric = new HashMap<>();
    customMetric.put("pod_name",
        Sets.newHashSet(DatadogState.Metric.builder().displayName("docker mem").metricName("docker.mem.rss").build()));

    nodeData.setCustomMetrics(customMetric);
    when(mlServiceUtils.getHostName(eq(nodeData))).thenReturn("sampleHostname");
    DatadogConfig ddConfig = DatadogConfig.builder().url("sampleUrl.com").build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(ddConfig);
    // setup
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(datadogService.getConcatenatedListOfMetricsForValidation(any(), any(), any(), any()))
        .thenReturn("docker.mem.rss");
    // execute
    VerificationNodeDataSetupResponse response =
        service.getDataForNode("accountId", "serverConfigId", nodeData, DelegateStateType.DATA_DOG);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeDataValidNoLoad() {
    APMVerificationConfig config = new APMVerificationConfig();
    config.setValidationUrl("this is a testurl");
    config.setUrl("this is a test url");
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(config);

    String dummyResponseString = "{}";

    APMFetchConfig fetchConfig = APMFetchConfig.builder().url("testFetchURL.com").build();
    APMSetupTestNodeData nodeData =
        APMSetupTestNodeData.builder()
            .fetchConfig(fetchConfig)
            .apmMetricCollectionInfo(
                ApmMetricCollectionInfo.builder().responseMapping(ApmResponseMapping.builder().build()).build())
            .build();
    nodeData.setGuid(generateUuid());

    // setup
    ThirdPartyApiCallLog apiCallLog = ThirdPartyApiCallLog.builder().build();
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(mockAPMDelegateService);
    when(mockAPMDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenAnswer(invocation -> {
          Object[] args = invocation.getArguments();
          apiCallLog.setStateExecutionId(((ThirdPartyApiCallLog) args[1]).getStateExecutionId());
          return dummyResponseString;
        });

    // execute
    VerificationNodeDataSetupResponse response =
        service.getDataForNode("accountId", "serverConfigId", nodeData, DelegateStateType.APM_VERIFICATION);

    // verify
    assertThat(response).isNotNull();
    assertThat(response.isConfigurationCorrect()).isFalse();
    assertThat(response.getLoadResponse()).isNull();
    assertThat(apiCallLog.getStateExecutionId()).isEqualTo(nodeData.getGuid());
  }

  @Test(expected = WingsException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeDataNullServerConfigId() {
    APMVerificationConfig config = new APMVerificationConfig();
    config.setValidationUrl("this is a testurl");
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(config);

    String dummyResponseString = "{}";

    APMFetchConfig fetchConfig = APMFetchConfig.builder().url("testFetchURL.com").build();

    // setup
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(mockAPMDelegateService);
    when(mockAPMDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenReturn(dummyResponseString);

    // execute
    VerificationNodeDataSetupResponse response =
        service.getDataForNode("accountId", null, fetchConfig, DelegateStateType.APM_VERIFICATION);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeDataNullFetchConfig() {
    APMVerificationConfig config = new APMVerificationConfig();
    config.setValidationUrl("this is a testurl");
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(config);

    String dummyResponseString = "{}";

    APMFetchConfig fetchConfig = APMFetchConfig.builder().url("testFetchURL.com").build();

    // setup
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(mockAPMDelegateService);
    when(mockAPMDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenReturn(dummyResponseString);

    // execute
    VerificationNodeDataSetupResponse response =
        service.getDataForNode("accountId", "serverId", null, DelegateStateType.APM_VERIFICATION);
  }

  @Test(expected = WingsException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeDataExceptionWhileFetch() {
    APMVerificationConfig config = new APMVerificationConfig();
    config.setValidationUrl("this is a testurl");
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(config);

    String dummyResponseString = "{}";

    APMFetchConfig fetchConfig = APMFetchConfig.builder().url("testFetchURL.com").build();

    // setup
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(mockAPMDelegateService);
    when(mockAPMDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenThrow(new WingsException(""));

    // execute
    VerificationNodeDataSetupResponse response =
        service.getDataForNode("accountId", "serverId", null, DelegateStateType.APM_VERIFICATION);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateDelegateTaskDatadog() {
    // Setup
    DatadogConfig dConfg = DatadogConfig.builder().accountId("accountId").url("datadogUrl").build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(dConfg);

    Map<String, String> dockerMetrics = new HashMap<>();
    dockerMetrics.put("service_name:harness", "docker.cpu.usage, docker.mem.rss");

    DatadogCVServiceConfiguration config = DatadogCVServiceConfiguration.builder().dockerMetrics(dockerMetrics).build();
    config.setConnectorId("connectorId");
    config.setUuid("cvConfigId");
    config.setAppId("appId");
    config.setServiceId("serviceId");
    config.setStateType(StateType.DATA_DOG);
    wingsPersistence.save(config);

    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockWaitNotifyEngine.waitForAllOn(any(), any(), any())).thenReturn("waitId");
    when(mockSecretManager.getEncryptionDetails(dConfg, "appId", null)).thenReturn(new ArrayList<>());

    // execute behavior
    boolean response = service.collect247Data("cvConfigId", StateType.DATA_DOG, 1540419553000l, 1540420454000l);

    // verify
    assertThat(response).isTrue();
    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);

    verify(mockWaitNotifyEngine).waitForAllOn(any(), any(), any());
    verify(mockDelegateService).queueTaskV2(taskCaptor.capture());
    assertThat(TaskType.APM_24_7_METRIC_DATA_COLLECTION_TASK.name())
        .isEqualTo(taskCaptor.getValue().getData().getTaskType());
    APMDataCollectionInfo dataCollectionInfo =
        (APMDataCollectionInfo) taskCaptor.getValue().getData().getParameters()[0];
    assertThat(DelegateStateType.DATA_DOG).isEqualTo(dataCollectionInfo.getStateType());
    assertThat(1540419553000l).isEqualTo(dataCollectionInfo.getStartTime());
    assertThat(15).isEqualTo(dataCollectionInfo.getDataCollectionTotalTime());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateAppD24x7Task() {
    // Setup
    AppDynamicsConfig appDynamicsConfig = AppDynamicsConfig.builder().build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(appDynamicsConfig);

    AppDynamicsCVServiceConfiguration config =
        AppDynamicsCVServiceConfiguration.builder().appDynamicsApplicationId("1234").tierId("5678").build();
    config.setConnectorId("connectorId");
    config.setUuid("cvConfigId");
    config.setAppId("appId");
    config.setServiceId("serviceId");
    config.setStateType(StateType.APP_DYNAMICS);
    wingsPersistence.save(config);

    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockWaitNotifyEngine.waitForAllOn(any(), any(), any())).thenReturn("waitId");
    when(mockSecretManager.getEncryptionDetails(appDynamicsConfig, "appId", null)).thenReturn(new ArrayList<>());

    // execute behavior
    boolean response = service.collect247Data("cvConfigId", StateType.APP_DYNAMICS, 1540419553000l, 1540420454000l);

    // verify
    assertThat(response).isTrue();
    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);

    verify(mockWaitNotifyEngine).waitForAllOn(any(), any(), any());
    verify(mockDelegateService).queueTaskV2(taskCaptor.capture());
    assertThat(TaskType.APPDYNAMICS_COLLECT_24_7_METRIC_DATA.name())
        .isEqualTo(taskCaptor.getValue().getData().getTaskType());
    AppdynamicsDataCollectionInfo dataCollectionInfo =
        (AppdynamicsDataCollectionInfo) taskCaptor.getValue().getData().getParameters()[0];
    assertThat(appDynamicsConfig).isEqualTo(dataCollectionInfo.getAppDynamicsConfig());
    assertThat(15).isEqualTo(dataCollectionInfo.getCollectionTime());
    assertThat(1540419553000l).isEqualTo(dataCollectionInfo.getStartTime());
    assertThat(TimeSeriesMlAnalysisType.PREDICTIVE).isEqualTo(dataCollectionInfo.getTimeSeriesMlAnalysisType());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateNewRelic24x7Task() {
    NewRelicConfig nrConfig = NewRelicConfig.builder().build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(nrConfig);

    NewRelicCVServiceConfiguration config = NewRelicCVServiceConfiguration.builder().applicationId("1234").build();
    config.setConnectorId("connectorId");
    config.setUuid("cvConfigId");
    config.setAppId("appId");
    config.setServiceId("serviceId");
    config.setStateType(StateType.NEW_RELIC);
    wingsPersistence.save(config);

    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockWaitNotifyEngine.waitForAllOn(any(), any(), any())).thenReturn("waitId");
    when(mockSecretManager.getEncryptionDetails(nrConfig, "appId", null)).thenReturn(new ArrayList<>());

    // execute behavior
    boolean response = service.collect247Data("cvConfigId", StateType.NEW_RELIC, 1540419553000l, 1540420454000l);

    // verify
    assertThat(response).isTrue();
    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);

    verify(mockWaitNotifyEngine).waitForAllOn(any(), any(), any());
    verify(mockDelegateService).queueTaskV2(taskCaptor.capture());
    assertThat(TaskType.NEWRELIC_COLLECT_24_7_METRIC_DATA.name())
        .isEqualTo(taskCaptor.getValue().getData().getTaskType());
    NewRelicDataCollectionInfo dataCollectionInfo =
        (NewRelicDataCollectionInfo) taskCaptor.getValue().getData().getParameters()[0];
    assertThat(nrConfig).isEqualTo(dataCollectionInfo.getNewRelicConfig());
    assertThat(15).isEqualTo(dataCollectionInfo.getCollectionTime());
    assertThat(1540419553000l).isEqualTo(dataCollectionInfo.getStartTime());
    assertThat(TimeSeriesMlAnalysisType.PREDICTIVE).isEqualTo(dataCollectionInfo.getTimeSeriesMlAnalysisType());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreate24x7TaskBadState() {
    boolean response = service.collect247Data("cvConfigId", StateType.HTTP, 1540419553000l, 1540420454000l);
    assertThat(response).isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreate24x7TaskPrometheus() {
    PrometheusConfig nrConfig = PrometheusConfig.builder().build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(nrConfig);

    PrometheusCVServiceConfiguration config = PrometheusCVServiceConfiguration.builder().build();
    config.setConnectorId("connectorId");
    config.setUuid("cvConfigId");
    config.setAppId("appId");
    config.setServiceId("serviceId");
    config.setStateType(StateType.PROMETHEUS);
    config.setTimeSeriesToAnalyze(Lists.newArrayList(TimeSeries.builder()
                                                         .txnName(generateUUID())
                                                         .metricType(MetricType.INFRA.name())
                                                         .url("jvm_memory_max_bytes{pod_name=\"$hostName\"}")
                                                         .build()));
    wingsPersistence.save(config);

    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockWaitNotifyEngine.waitForAllOn(any(), any(), any())).thenReturn("waitId");
    when(mockSecretManager.getEncryptionDetails(nrConfig, "appId", null)).thenReturn(new ArrayList<>());

    // execute behavior
    boolean response = service.collect247Data("cvConfigId", StateType.PROMETHEUS, 1540419553000l, 1540420454000l);

    // verify
    assertThat(response).isTrue();
    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);

    verify(mockWaitNotifyEngine).waitForAllOn(any(), any(), any());
    verify(mockDelegateService).queueTaskV2(taskCaptor.capture());
    assertThat(TaskType.APM_24_7_METRIC_DATA_COLLECTION_TASK.name())
        .isEqualTo(taskCaptor.getValue().getData().getTaskType());
    APMDataCollectionInfo dataCollectionInfo =
        (APMDataCollectionInfo) taskCaptor.getValue().getData().getParameters()[0];
    assertThat(15).isEqualTo(dataCollectionInfo.getDataCollectionTotalTime());
    assertThat(1540419553000l).isEqualTo(dataCollectionInfo.getStartTime());
    assertThat(AnalysisComparisonStrategy.PREDICTIVE).isEqualTo(dataCollectionInfo.getStrategy());
  }

  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  public void testCreate24x7TaskCloudWatch() {
    AwsConfig awsConfig = AwsConfig.builder().build();
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(awsConfig);
    CloudWatchCVServiceConfiguration config = CloudWatchCVServiceConfiguration.builder().build();
    config.setConnectorId("connectorId");
    config.setUuid("cvConfigId");
    config.setAppId("appId");
    config.setServiceId("serviceId");
    config.setStateType(StateType.CLOUD_WATCH);
    wingsPersistence.save(config);
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockWaitNotifyEngine.waitForAllOn(any(), any(), any())).thenReturn("waitId");
    when(mockSecretManager.getEncryptionDetails(awsConfig, "appId", null)).thenReturn(new ArrayList<>());
    when(cloudWatchService.createLambdaFunctionNames(anyList())).thenReturn(new HashMap());
    // execute behavior
    boolean response = service.collect247Data("cvConfigId", StateType.CLOUD_WATCH, 1540419553000l, 1540420454000l);
    // verify
    assertThat(response).isTrue();
    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockWaitNotifyEngine).waitForAllOn(any(), any(), any());
    verify(mockDelegateService).queueTaskV2(taskCaptor.capture());
    assertThat(TaskType.CLOUD_WATCH_COLLECT_24_7_METRIC_DATA.name())
        .isEqualTo(taskCaptor.getValue().getData().getTaskType());
    CloudWatchDataCollectionInfo dataCollectionInfo =
        (CloudWatchDataCollectionInfo) taskCaptor.getValue().getData().getParameters()[0];
    assertThat(15).isEqualTo(dataCollectionInfo.getCollectionTime());
    assertThat(1540419553000l).isEqualTo(dataCollectionInfo.getStartTime());
    assertThat(AnalysisComparisonStrategy.PREDICTIVE).isEqualTo(dataCollectionInfo.getAnalysisComparisonStrategy());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateStackDriver24x7Task() throws Exception {
    String accountId = generateUuid();
    StackDriverMetricCVConfiguration cvConfiguration = createStackDriverConfig(accountId);
    cvConfiguration.setMetricFilters();
    wingsPersistence.save(cvConfiguration);

    GcpConfig gcpConfig = GcpConfig.builder().build();
    SettingAttribute attribute = SettingAttribute.Builder.aSettingAttribute()
                                     .withValue(gcpConfig)
                                     .withUuid(cvConfiguration.getConnectorId())
                                     .build();
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockWaitNotifyEngine.waitForAllOn(any(), any(), any())).thenReturn("waitId");
    when(mockSecretManager.getEncryptionDetails(gcpConfig, cvConfiguration.getAppId(), null))
        .thenReturn(new ArrayList<>());

    // execute behavior
    boolean response =
        service.collect247Data(cvConfiguration.getUuid(), StateType.STACK_DRIVER, 1540419553000l, 1540420454000l);
    // verify
    assertThat(response).isTrue();
    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockWaitNotifyEngine).waitForAllOn(any(), any(), any());
    verify(mockDelegateService).queueTaskV2(taskCaptor.capture());

    assertThat(TaskType.STACKDRIVER_COLLECT_24_7_METRIC_DATA.name())
        .isEqualTo(taskCaptor.getValue().getData().getTaskType());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateCustomLogs24x7Task() throws Exception {
    String accountId = generateUuid();
    CustomLogCVServiceConfiguration cvConfiguration = createCustomLogsConfig(accountId);
    wingsPersistence.save(cvConfiguration);

    APMVerificationConfig apmConfig = new APMVerificationConfig();
    SettingAttribute attribute = SettingAttribute.Builder.aSettingAttribute()
                                     .withValue(apmConfig)
                                     .withUuid(cvConfiguration.getConnectorId())
                                     .build();
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockWaitNotifyEngine.waitForAllOn(any(), any(), any())).thenReturn("waitId");
    when(mockSecretManager.getEncryptionDetails(apmConfig, cvConfiguration.getAppId(), null))
        .thenReturn(new ArrayList<>());

    // execute behavior
    boolean response =
        service.collect247Data(cvConfiguration.getUuid(), StateType.LOG_VERIFICATION, 1540419553000l, 1540420454000l);
    // verify
    assertThat(response).isTrue();
    ArgumentCaptor<DelegateTask> taskCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockWaitNotifyEngine).waitForAllOn(any(), any(), any());
    verify(mockDelegateService).queueTaskV2(taskCaptor.capture());

    assertThat(TaskType.CUSTOM_COLLECT_24_7_LOG_DATA.name()).isEqualTo(taskCaptor.getValue().getData().getTaskType());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNodeDataCustomLogsValidCase() {
    APMVerificationConfig config = new APMVerificationConfig();
    config.setValidationUrl("this is a testurl");
    config.setUrl("this is a test url");
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(config);

    String dummyResponseString =
        "{ \"logMessage\":\"this is a log message example\",\"host\":\"sample.host.ame\",\"key1\":1.45, \"time\":\"12345612227\"}";

    CustomLogSetupTestNodeData nodeData =
        CustomLogSetupTestNodeData.builder()
            .logCollectionInfo(LogCollectionInfo.builder()
                                   .collectionUrl("testURL")
                                   .method(Method.GET)
                                   .responseMapping(LogResponseMapping.builder()
                                                        .hostJsonPath("host")
                                                        .logMessageJsonPath("logMessage")
                                                        .timestampJsonPath("time")
                                                        .build())
                                   .build())
            .build();

    // setup
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(mockAPMDelegateService);
    when(mockAPMDelegateService.fetch(any(APMValidateCollectorConfig.class), any(ThirdPartyApiCallLog.class)))
        .thenReturn(dummyResponseString);

    // execute
    VerificationNodeDataSetupResponse response =
        service.getDataForNode("accountId", "serverConfigId", nodeData, DelegateStateType.LOG_VERIFICATION);

    // verify
    assertThat(response).isNotNull();
    assertThat(response.getLoadResponse().isLoadPresent()).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void test_getDataForNode_unresolvedVariable() {
    APMVerificationConfig config = new APMVerificationConfig();
    config.setValidationUrl("this is a testurl");
    config.setUrl("https://google.com");
    SettingAttribute attribute = new SettingAttribute();
    attribute.setValue(config);

    APMFetchConfig fetchConfig = APMFetchConfig.builder().url("testFetchURL.com").body("${host}").build();
    APMSetupTestNodeData nodeData =
        APMSetupTestNodeData.builder()
            .fetchConfig(fetchConfig)
            .apmMetricCollectionInfo(ApmMetricCollectionInfo.builder().method(Method.POST).build())
            .build();

    // setup
    ThirdPartyApiCallLog apiCallLog = ThirdPartyApiCallLog.builder().build();
    when(mockSettingsService.get(any())).thenReturn(attribute);
    when(mockDelegateProxyFactory.getV2(any(), any())).thenReturn(apmDelegateService);

    try {
      service.getDataForNode("accountId", "serverConfigId", nodeData, DelegateStateType.APM_VERIFICATION);
      fail("should throw an exception");
    } catch (VerificationOperationException e) {
      assertThat(e.getParams().get("reason")).isEqualTo("Could not resolve \"$ {host}\" provided in input");
    }
  }
}
