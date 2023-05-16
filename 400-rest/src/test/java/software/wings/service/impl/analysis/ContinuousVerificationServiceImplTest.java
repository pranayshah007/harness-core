/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.analysis;

import static io.harness.beans.ExecutionStatus.ERROR;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRANJAL;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SOWMYA;
import static io.harness.rule.OwnerRule.SRIRAM;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static software.wings.common.VerificationConstants.DATADOG_END_TIME_PLACEHOLDER;
import static software.wings.common.VerificationConstants.DATADOG_START_TIME_PLACEHOLDER;
import static software.wings.service.impl.analysis.LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE;
import static software.wings.sm.StateType.ELK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.PageResponse.PageResponseBuilder;
import io.harness.category.element.UnitTests;
import io.harness.cv.WorkflowVerificationResult;
import io.harness.cv.api.WorkflowVerificationResultService;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.YamlUtils;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.WingsBaseTest;
import software.wings.beans.APMValidateCollectorConfig;
import software.wings.beans.ApmMetricCollectionInfo;
import software.wings.beans.DatadogConfig;
import software.wings.beans.Environment;
import software.wings.beans.GcpConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SplunkConfig;
import software.wings.beans.TaskType;
import software.wings.beans.User;
import software.wings.beans.dto.ThirdPartyApiCallLog;
import software.wings.common.VerificationConstants;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.MetricType;
import software.wings.security.AppPermissionSummary;
import software.wings.security.AppPermissionSummary.EnvInfo;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.UserPermissionInfo;
import software.wings.service.impl.CloudWatchServiceImpl;
import software.wings.service.impl.apm.APMMetricInfo;
import software.wings.service.impl.apm.MLServiceUtils;
import software.wings.service.impl.appdynamics.AppdynamicsTimeSeries;
import software.wings.service.impl.cloudwatch.AwsNameSpace;
import software.wings.service.impl.cloudwatch.CloudWatchMetric;
import software.wings.service.impl.datadog.DataDogSetupTestNodeData;
import software.wings.service.impl.newrelic.NewRelicMetricValueDefinition;
import software.wings.service.impl.splunk.SplunkDataCollectionInfoV2;
import software.wings.service.impl.stackdriver.StackDriverLogDataCollectionInfo;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.AuthService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.datadog.DatadogService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.StateType;
import software.wings.sm.states.DatadogState;
import software.wings.sm.states.DatadogState.Metric;
import software.wings.verification.HeatMapResolution;
import software.wings.verification.apm.APMCVServiceConfiguration;
import software.wings.verification.appdynamics.AppDynamicsCVServiceConfiguration;
import software.wings.verification.cloudwatch.CloudWatchCVServiceConfiguration;
import software.wings.verification.datadog.DatadogCVServiceConfiguration;
import software.wings.verification.dynatrace.DynaTraceCVServiceConfiguration;
import software.wings.verification.newrelic.NewRelicCVServiceConfiguration;
import software.wings.verification.prometheus.PrometheusCVServiceConfiguration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Created by Praveen on 5/31/2018
 */
public class ContinuousVerificationServiceImplTest extends WingsBaseTest {
  private String accountId;
  private String appId;
  private String stateExecutionId;
  private String workflowId;
  private String workflowExecutionId;
  private String serviceId;
  private String envId;
  private User user;

  @Mock private AuthService mockAuthService;
  @Mock private WingsPersistence mockWingsPersistence;
  @Mock private UserPermissionInfo mockUserPermissionInfo;
  @Mock private CVConfigurationService cvConfigurationService;
  @Mock private SettingsService settingsService;
  @Mock private SecretManager secretManager;
  @Mock private DelegateProxyFactory delegateProxyFactory;
  @Mock private APMDelegateService apmDelegateService;
  @Mock private DatadogService datadogService;
  @Mock private MLServiceUtils mlServiceUtils;
  @Inject private HPersistence persistence;
  @Mock private DelegateService delegateService;
  @InjectMocks private ContinuousVerificationServiceImpl continuousVerificationService;
  @Mock private CVActivityLogService cvActivityLogService;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Inject private WorkflowVerificationResultService workflowVerificationResultService;

  private CVActivityLogger logger = mock(CVActivityLogger.class);

  @Before
  public void setupMocks() throws IllegalAccessException {
    accountId = UUID.randomUUID().toString();
    appId = UUID.randomUUID().toString();
    stateExecutionId = UUID.randomUUID().toString();
    workflowId = UUID.randomUUID().toString();
    workflowExecutionId = UUID.randomUUID().toString();
    serviceId = UUID.randomUUID().toString();
    envId = UUID.randomUUID().toString();
    user = new User();

    MockitoAnnotations.initMocks(this);

    PageResponse<ContinuousVerificationExecutionMetaData> r =
        PageResponseBuilder.aPageResponse().withResponse(Arrays.asList(getExecutionMetadata())).build();
    PageResponse<ContinuousVerificationExecutionMetaData> rEmpty = PageResponseBuilder.aPageResponse().build();
    when(mockWingsPersistence.query(any(), any(PageRequest.class))).thenReturn(r).thenReturn(rEmpty);

    when(mockUserPermissionInfo.getAppPermissionMapInternal()).thenReturn(new HashMap<String, AppPermissionSummary>() {
      { put(appId, buildAppPermissionSummary()); }
    });

    when(cvActivityLogService.getLoggerByStateExecutionId(any(), any())).thenReturn(logger);

    when(featureFlagService.isEnabled(FeatureName.DISABLE_LOGML_NEURAL_NET, accountId)).thenReturn(false);

    when(appService.getAccountIdByAppId(anyString())).thenReturn(accountId);

    when(mockAuthService.getUserPermissionInfo(accountId, user, false)).thenReturn(mockUserPermissionInfo);
    FieldUtils.writeField(continuousVerificationService, "cvConfigurationService", cvConfigurationService, true);
    FieldUtils.writeField(continuousVerificationService, "settingsService", settingsService, true);
    FieldUtils.writeField(continuousVerificationService, "secretManager", secretManager, true);
    FieldUtils.writeField(continuousVerificationService, "delegateProxyFactory", delegateProxyFactory, true);
    FieldUtils.writeField(continuousVerificationService, "cvActivityLogService", cvActivityLogService, true);
    FieldUtils.writeField(continuousVerificationService, "waitNotifyEngine", waitNotifyEngine, true);
    FieldUtils.writeField(continuousVerificationService, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(continuousVerificationService, "appService", appService, true);
    FieldUtils.writeField(continuousVerificationService, "environmentService", environmentService, true);
    FieldUtils.writeField(
        continuousVerificationService, "workflowVerificationResultService", workflowVerificationResultService, true);

    when(environmentService.get(any(), any()))
        .thenReturn(Environment.Builder.anEnvironment().environmentType(EnvironmentType.PROD).build());
  }

  private ContinuousVerificationExecutionMetaData getExecutionMetadata() {
    return ContinuousVerificationExecutionMetaData.builder()
        .accountId(accountId)
        .applicationId(appId)
        .appName("dummy")
        .artifactName("cv dummy artifact")
        .envName("cv dummy env")
        .envId(envId)
        .phaseName("dummy phase")
        .pipelineName("dummy pipeline")
        .workflowName("dummy workflow")
        .pipelineStartTs(1519200000000L)
        .workflowStartTs(1519200000000L)
        .serviceId(serviceId)
        .serviceName("dummy service")
        .stateType(StateType.APM_VERIFICATION)
        .workflowId(workflowId)
        .workflowExecutionId(workflowExecutionId)
        .build();
  }

  private AppPermissionSummary buildAppPermissionSummary() {
    Map<Action, Set<String>> servicePermissions = new HashMap<Action, Set<String>>() {
      { put(Action.READ, Sets.newHashSet(serviceId)); }
    };
    Map<Action, Set<EnvInfo>> envPermissions = new HashMap<Action, Set<EnvInfo>>() {
      {
        put(Action.READ, Sets.newHashSet(EnvInfo.builder().envId(envId).envType(EnvironmentType.PROD.name()).build()));
      }
    };
    Map<Action, Set<String>> pipelinePermissions = new HashMap<Action, Set<String>>() {
      { put(Action.READ, Sets.newHashSet()); }
    };
    Map<Action, Set<String>> workflowPermissions = new HashMap<Action, Set<String>>() {
      { put(Action.READ, Sets.newHashSet(workflowId)); }
    };

    return AppPermissionSummary.builder()
        .servicePermissions(servicePermissions)
        .envPermissions(envPermissions)
        .workflowPermissions(workflowPermissions)
        .pipelinePermissions(pipelinePermissions)
        .build();
  }
  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void testNullUser() throws ParseException, IllegalAccessException {
    setupMocks();
    LinkedHashMap<Long,
        LinkedHashMap<String,
            LinkedHashMap<String,
                LinkedHashMap<String, LinkedHashMap<String, List<ContinuousVerificationExecutionMetaData>>>>>>
        execData =
            continuousVerificationService.getCVExecutionMetaData(accountId, 1519200000000L, 1519200000001L, null);

    assertThat(execData).isNotNull();
    assertThat(execData).hasSize(0);
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void testAllValidPermissions() throws ParseException {
    LinkedHashMap<Long,
        LinkedHashMap<String,
            LinkedHashMap<String,
                LinkedHashMap<String, LinkedHashMap<String, List<ContinuousVerificationExecutionMetaData>>>>>>
        execData =
            continuousVerificationService.getCVExecutionMetaData(accountId, 1519200000000L, 1519200000001L, user);

    assertThat(execData).isNotNull();
    assertThat(execData).hasSize(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testNoPermissionsForEnvironment() throws ParseException {
    AppPermissionSummary permissionSummary = buildAppPermissionSummary();
    permissionSummary.setEnvPermissions(new HashMap<>());
    when(mockUserPermissionInfo.getAppPermissionMapInternal()).thenReturn(new HashMap<String, AppPermissionSummary>() {
      { put(appId, permissionSummary); }
    });

    LinkedHashMap<Long,
        LinkedHashMap<String,
            LinkedHashMap<String,
                LinkedHashMap<String, LinkedHashMap<String, List<ContinuousVerificationExecutionMetaData>>>>>>
        execData =
            continuousVerificationService.getCVExecutionMetaData(accountId, 1519200000000L, 1519200000001L, user);

    assertThat(execData).isNotNull();
    assertThat(execData).isEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testNoPermissionsForService() throws ParseException {
    AppPermissionSummary permissionSummary = buildAppPermissionSummary();
    permissionSummary.setServicePermissions(new HashMap<>());
    when(mockUserPermissionInfo.getAppPermissionMapInternal()).thenReturn(new HashMap<String, AppPermissionSummary>() {
      { put(appId, permissionSummary); }
    });
    LinkedHashMap<Long,
        LinkedHashMap<String,
            LinkedHashMap<String,
                LinkedHashMap<String, LinkedHashMap<String, List<ContinuousVerificationExecutionMetaData>>>>>>
        execData =
            continuousVerificationService.getCVExecutionMetaData(accountId, 1519200000000L, 1519200000001L, user);
    assertThat(execData).isNotNull();
    assertThat(execData).hasSize(0);
  }

  @Test
  @Owner(developers = PRANJAL)
  @Category(UnitTests.class)
  public void testDataDogMetricEndPointCreation() {
    String expectedDockerCPUMetricURL =
        "query?api_key=${apiKey}&application_key=${applicationKey}&from=${start_time_seconds}&to=${end_time_seconds}&query=docker.cpu.usage{cluster-name:harness-test}.rollup(avg,60)";
    String expectedDockerMEMMetricURL =
        "query?api_key=${apiKey}&application_key=${applicationKey}&from=${start_time_seconds}&to=${end_time_seconds}&query=docker.mem.rss{cluster-name:harness-test}.rollup(avg,60)/docker.mem.limit{cluster-name:harness-test}.rollup(avg,60)*100";
    Map<String, String> dockerMetrics = new HashMap<>();
    dockerMetrics.put("cluster-name:harness-test", "docker.cpu.usage,docker.mem.rss");

    String expectedECSMetricURL =
        "query?api_key=${apiKey}&application_key=${applicationKey}&from=${start_time_seconds}&to=${end_time_seconds}&query=ecs.fargate.cpu.user{cluster-name:sdktest}.rollup(avg,60)";
    Map<String, String> ecsMetrics = new HashMap<>();
    ecsMetrics.put("cluster-name:sdktest", "ecs.fargate.cpu.user");

    String expectedCustomMetricURL =
        "query?api_key=${apiKey}&application_key=${applicationKey}&from=${start_time_seconds}&to=${end_time_seconds}&query=ec2.cpu{service_name:harness}.rollup(avg,60)";
    Map<String, Set<Metric>> customMetricsMap = new HashMap<>();
    Set<Metric> metrics = new HashSet<>();
    metrics.add(Metric.builder()
                    .metricName("ec2.cpu")
                    .displayName("ec2 cpu")
                    .mlMetricType("VALUE")
                    .datadogMetricType("Custom")
                    .build());
    customMetricsMap.put("service_name:harness", metrics);
    Map<String, List<APMMetricInfo>> metricEndPoints =
        continuousVerificationService.createDatadogMetricEndPointMap(dockerMetrics, ecsMetrics, null, customMetricsMap);

    assertThat(4).isEqualTo(metricEndPoints.size());
    assertThat(metricEndPoints.keySet()).contains(expectedDockerCPUMetricURL);
    assertThat(metricEndPoints.keySet()).contains(expectedDockerMEMMetricURL);
    assertThat(metricEndPoints.keySet()).contains(expectedECSMetricURL);
    assertThat(metricEndPoints.keySet()).contains(expectedCustomMetricURL);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testHeatMapResolutionEnum() {
    long endTime = System.currentTimeMillis();

    long twelveHours = endTime - TimeUnit.HOURS.toMillis(12);
    HeatMapResolution heatMapResolution = HeatMapResolution.getResolution(twelveHours, endTime);
    assertThat(heatMapResolution).isEqualTo(HeatMapResolution.TWELVE_HOURS);

    int twelveHoursResolutionDurationInMinutes = VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES;
    assertThat(heatMapResolution.getDurationOfHeatMapUnit(heatMapResolution))
        .isEqualTo(twelveHoursResolutionDurationInMinutes);

    assertThat(heatMapResolution.getEventsPerHeatMapUnit(heatMapResolution))
        .isEqualTo(twelveHoursResolutionDurationInMinutes / VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES);

    long oneDay = endTime - TimeUnit.DAYS.toMillis(1);
    heatMapResolution = HeatMapResolution.getResolution(oneDay, endTime);
    assertThat(heatMapResolution).isEqualTo(HeatMapResolution.ONE_DAY);

    int oneDayResolutionDurationInMinutes = 2 * VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES;
    assertThat(heatMapResolution.getDurationOfHeatMapUnit(heatMapResolution))
        .isEqualTo(oneDayResolutionDurationInMinutes);

    assertThat(heatMapResolution.getEventsPerHeatMapUnit(heatMapResolution))
        .isEqualTo(oneDayResolutionDurationInMinutes / VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES);

    long sevenDays = endTime - TimeUnit.DAYS.toMillis(7);
    heatMapResolution = HeatMapResolution.getResolution(sevenDays, endTime);
    assertThat(heatMapResolution).isEqualTo(HeatMapResolution.SEVEN_DAYS);

    int sevenDayResolutionDurationInMinutes = 16 * VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES;
    assertThat(heatMapResolution.getDurationOfHeatMapUnit(heatMapResolution))
        .isEqualTo(sevenDayResolutionDurationInMinutes);

    assertThat(heatMapResolution.getEventsPerHeatMapUnit(heatMapResolution))
        .isEqualTo(sevenDayResolutionDurationInMinutes / VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES);

    long thirtyDays = endTime - TimeUnit.DAYS.toMillis(30);
    heatMapResolution = HeatMapResolution.getResolution(thirtyDays, endTime);
    assertThat(heatMapResolution).isEqualTo(HeatMapResolution.THIRTY_DAYS);

    int thirtyDayResolutionDurationInMinutes = 48 * VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES;
    assertThat(heatMapResolution.getDurationOfHeatMapUnit(heatMapResolution))
        .isEqualTo(thirtyDayResolutionDurationInMinutes);

    assertThat(heatMapResolution.getEventsPerHeatMapUnit(heatMapResolution))
        .isEqualTo(thirtyDayResolutionDurationInMinutes / VerificationConstants.CRON_POLL_INTERVAL_IN_MINUTES);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCollectCVData_withAllCorrectParams() throws IllegalAccessException {
    String cvTaskId = generateUuid();
    DataCollectionInfoV2 dataCollectionInfoV2 = SplunkDataCollectionInfoV2.builder()
                                                    .accountId(accountId)
                                                    .connectorId(generateUuid())
                                                    .stateExecutionId(generateUuid())
                                                    .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                                                    .endTime(Instant.now())
                                                    .applicationId(generateUuid())
                                                    .query("query")
                                                    .hostnameField("hostnameField")
                                                    .build();
    SplunkConfig splunkConfig = mock(SplunkConfig.class);
    DelegateService delegateService = mock(DelegateService.class);
    SecretManager secretManager = mock(SecretManager.class);
    SettingsService settingsService = mock(SettingsService.class);
    FieldUtils.writeField(continuousVerificationService, "delegateService", delegateService, true);
    FieldUtils.writeField(continuousVerificationService, "secretManager", secretManager, true);
    FieldUtils.writeField(continuousVerificationService, "settingsService", settingsService, true);
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    when(settingsService.get(eq(dataCollectionInfoV2.getConnectorId()))).thenReturn(settingAttribute);
    when(settingAttribute.getValue()).thenReturn(splunkConfig);
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(mock(EncryptedDataDetail.class));
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(encryptedDataDetails);

    continuousVerificationService.collectCVData(cvTaskId, dataCollectionInfoV2);
    ArgumentCaptor<DelegateTask> argumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).queueTaskV2(argumentCaptor.capture());
    DelegateTask delegateTask = argumentCaptor.getValue();
    assertThat(delegateTask.getAccountId()).isEqualTo(dataCollectionInfoV2.getAccountId());
    assertThat(delegateTask.getData().getParameters()).hasSize(1);
    SplunkDataCollectionInfoV2 params = (SplunkDataCollectionInfoV2) delegateTask.getData().getParameters()[0];
    assertThat(params).isEqualTo(dataCollectionInfoV2);
    assertThat(delegateTask.getData().getTaskType()).isEqualTo(TaskType.SPLUNK_COLLECT_LOG_DATAV2.toString());
    assertThat(params.getCvTaskId()).isEqualTo(cvTaskId);
    assertThat(params.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(params.getSplunkConfig()).isEqualTo(splunkConfig);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCollectCVData_validationFailures() throws IllegalAccessException {
    String cvTaskId = generateUuid();
    DataCollectionInfoV2 dataCollectionInfoV2 = SplunkDataCollectionInfoV2.builder()
                                                    .accountId(accountId)
                                                    .connectorId(generateUuid())
                                                    .stateExecutionId(generateUuid())
                                                    .startTime(Instant.now().minus(10, ChronoUnit.MINUTES))
                                                    .endTime(Instant.now())
                                                    .applicationId(generateUuid())
                                                    .hostnameField("hostnameField")
                                                    .build();
    SplunkConfig splunkConfig = mock(SplunkConfig.class);
    DelegateService delegateService = mock(DelegateService.class);
    SecretManager secretManager = mock(SecretManager.class);
    SettingsService settingsService = mock(SettingsService.class);
    FieldUtils.writeField(continuousVerificationService, "delegateService", delegateService, true);
    FieldUtils.writeField(continuousVerificationService, "secretManager", secretManager, true);
    FieldUtils.writeField(continuousVerificationService, "settingsService", settingsService, true);
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    when(settingsService.get(eq(dataCollectionInfoV2.getConnectorId()))).thenReturn(settingAttribute);
    when(settingAttribute.getValue()).thenReturn(splunkConfig);
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(mock(EncryptedDataDetail.class));
    when(secretManager.getEncryptionDetails(any(), anyString(), anyString())).thenReturn(encryptedDataDetails);

    assertThatThrownBy(() -> continuousVerificationService.collectCVData(cvTaskId, dataCollectionInfoV2))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetAppdynamicsMetricType() {
    AppDynamicsCVServiceConfiguration cvConfig = AppDynamicsCVServiceConfiguration.builder()
                                                     .appDynamicsApplicationId(generateUuid())
                                                     .tierId(generateUuid())
                                                     .build();
    cvConfig.setStateType(StateType.APP_DYNAMICS);
    assertThat(
        continuousVerificationService.getMetricType(cvConfig, AppdynamicsTimeSeries.RESPONSE_TIME_95.getMetricName()))
        .isEqualTo(MetricType.RESP_TIME.name());
    assertThat(
        continuousVerificationService.getMetricType(cvConfig, AppdynamicsTimeSeries.CALLS_PER_MINUTE.getMetricName()))
        .isEqualTo(MetricType.THROUGHPUT.name());
    assertThat(
        continuousVerificationService.getMetricType(cvConfig, AppdynamicsTimeSeries.ERRORS_PER_MINUTE.getMetricName()))
        .isEqualTo(MetricType.ERROR.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, AppdynamicsTimeSeries.STALL_COUNT.getMetricName()))
        .isEqualTo(MetricType.ERROR.name());
    assertThat(
        continuousVerificationService.getMetricType(cvConfig, AppdynamicsTimeSeries.AVG_RESPONSE_TIME.getMetricName()))
        .isEqualTo(MetricType.RESP_TIME.name());
    assertThat(continuousVerificationService.getMetricType(
                   cvConfig, AppdynamicsTimeSeries.NUMBER_OF_SLOW_CALLS.getMetricName()))
        .isEqualTo(MetricType.ERROR.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, generateUuid())).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetNewRelicMetricType() {
    NewRelicCVServiceConfiguration cvConfig =
        NewRelicCVServiceConfiguration.builder().applicationId(generateUuid()).build();
    cvConfig.setStateType(StateType.NEW_RELIC);
    assertThat(continuousVerificationService.getMetricType(cvConfig, NewRelicMetricValueDefinition.REQUSET_PER_MINUTE))
        .isEqualTo(MetricType.THROUGHPUT.name());
    assertThat(
        continuousVerificationService.getMetricType(cvConfig, NewRelicMetricValueDefinition.AVERAGE_RESPONSE_TIME))
        .isEqualTo(MetricType.RESP_TIME.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, NewRelicMetricValueDefinition.ERROR))
        .isEqualTo(MetricType.ERROR.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, NewRelicMetricValueDefinition.APDEX_SCORE))
        .isEqualTo(MetricType.APDEX.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, generateUuid())).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetDynatraceMetricType() {
    DynaTraceCVServiceConfiguration cvConfig = DynaTraceCVServiceConfiguration.builder().build();
    cvConfig.setStateType(StateType.DYNA_TRACE);
    assertThat(
        continuousVerificationService.getMetricType(cvConfig, NewRelicMetricValueDefinition.CLIENT_SIDE_FAILURE_RATE))
        .isEqualTo(MetricType.ERROR.name());
    assertThat(
        continuousVerificationService.getMetricType(cvConfig, NewRelicMetricValueDefinition.ERROR_COUNT_HTTP_4XX))
        .isEqualTo(MetricType.ERROR.name());
    assertThat(
        continuousVerificationService.getMetricType(cvConfig, NewRelicMetricValueDefinition.ERROR_COUNT_HTTP_5XX))
        .isEqualTo(MetricType.ERROR.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, NewRelicMetricValueDefinition.REQUEST_PER_MINUTE))
        .isEqualTo(MetricType.THROUGHPUT.name());
    assertThat(
        continuousVerificationService.getMetricType(cvConfig, NewRelicMetricValueDefinition.SERVER_SIDE_FAILURE_RATE))
        .isEqualTo(MetricType.ERROR.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, generateUuid())).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetPrometheusMetricType() {
    PrometheusCVServiceConfiguration cvConfig =
        PrometheusCVServiceConfiguration.builder()
            .timeSeriesToAnalyze(Lists.newArrayList(TimeSeries.builder()
                                                        .txnName(generateUuid())
                                                        .metricName("metric1")
                                                        .metricType(MetricType.ERROR.name())
                                                        .url(generateUuid())
                                                        .build(),
                TimeSeries.builder()
                    .txnName(generateUuid())
                    .metricName("metric2")
                    .metricType(MetricType.THROUGHPUT.name())
                    .url(generateUuid())
                    .build()))
            .build();
    cvConfig.setStateType(StateType.PROMETHEUS);
    assertThat(continuousVerificationService.getMetricType(cvConfig, "metric1")).isEqualTo(MetricType.ERROR.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, "metric2"))
        .isEqualTo(MetricType.THROUGHPUT.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, generateUuid())).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetAPMMetricType() {
    APMCVServiceConfiguration cvConfig =
        APMCVServiceConfiguration.builder()
            .metricCollectionInfos(Lists.newArrayList(
                ApmMetricCollectionInfo.builder().metricName("metric1").metricType(MetricType.ERROR).build(),
                ApmMetricCollectionInfo.builder().metricName("metric2").metricType(MetricType.THROUGHPUT).build()))
            .build();
    cvConfig.setStateType(StateType.APM_VERIFICATION);
    assertThat(continuousVerificationService.getMetricType(cvConfig, "metric1")).isEqualTo(MetricType.ERROR.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, "metric2"))
        .isEqualTo(MetricType.THROUGHPUT.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, generateUuid())).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetCloudWatchMetricType() {
    final Map<AwsNameSpace, List<CloudWatchMetric>> awsNameSpaceMetrics = CloudWatchServiceImpl.fetchMetrics();
    CloudWatchCVServiceConfiguration cvConfig =
        CloudWatchCVServiceConfiguration.builder()
            .ec2Metrics(awsNameSpaceMetrics.get(AwsNameSpace.EC2))
            .ecsMetrics(Collections.singletonMap(generateUuid(), awsNameSpaceMetrics.get(AwsNameSpace.ECS)))
            .loadBalancerMetrics(Collections.singletonMap(generateUuid(), awsNameSpaceMetrics.get(AwsNameSpace.ELB)))
            .lambdaFunctionsMetrics(
                Collections.singletonMap(generateUuid(), awsNameSpaceMetrics.get(AwsNameSpace.LAMBDA)))
            .build();
    cvConfig.setStateType(StateType.CLOUD_WATCH);
    awsNameSpaceMetrics.forEach(
        (awsNameSpace, metrics)
            -> metrics.forEach(cloudWatchMetric
                -> assertThat(continuousVerificationService.getMetricType(cvConfig, cloudWatchMetric.getMetricName()))
                       .isEqualTo(cloudWatchMetric.getMetricType())));
    assertThat(continuousVerificationService.getMetricType(cvConfig, generateUuid())).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetDatadogetricType() throws IOException {
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

    DatadogCVServiceConfiguration cvConfig =
        DatadogCVServiceConfiguration.builder()
            .dockerMetrics(Collections.singletonMap(generateUuid(), dockerMetrics.toString()))
            .ecsMetrics(Collections.singletonMap(generateUuid(), ecsMetrics.toString()))
            .customMetrics(Collections.singletonMap(generateUuid(),
                Sets.newHashSet(
                    Metric.builder().displayName("metric1").mlMetricType(MetricType.THROUGHPUT.name()).build(),
                    Metric.builder().displayName("metric2").mlMetricType(MetricType.ERROR.name()).build())))
            .build();

    cvConfig.setStateType(StateType.DATA_DOG);
    metricsMap.get("Docker").forEach(metric
        -> assertThat(continuousVerificationService.getMetricType(cvConfig, metric.getMetricName()))
               .isEqualTo(MetricType.INFRA.name()));
    metricsMap.get("ECS").forEach(metric
        -> assertThat(continuousVerificationService.getMetricType(cvConfig, metric.getMetricName()))
               .isEqualTo(MetricType.INFRA.name()));
    assertThat(continuousVerificationService.getMetricType(cvConfig, "metric1"))
        .isEqualTo(MetricType.THROUGHPUT.name());
    assertThat(continuousVerificationService.getMetricType(cvConfig, "metric2")).isEqualTo(MetricType.ERROR.name());

    assertThat(continuousVerificationService.getMetricType(cvConfig, generateUuid())).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetCVExecutionMetaData_NoEntries() throws IllegalAccessException {
    FieldUtils.writeField(continuousVerificationService, "wingsPersistence", persistence, true);
    ContinuousVerificationExecutionMetaData cvMetaData =
        continuousVerificationService.getCVExecutionMetaData(stateExecutionId);
    assertThat(cvMetaData).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetCVExecutionMetaData_NonNullEntries() throws IllegalAccessException {
    FieldUtils.writeField(continuousVerificationService, "wingsPersistence", persistence, true);
    ContinuousVerificationExecutionMetaData savedData =
        ContinuousVerificationExecutionMetaData.builder().stateExecutionId(stateExecutionId).build();
    savedData.setUuid(generateUuid());
    persistence.save(savedData);
    ContinuousVerificationExecutionMetaData cvMetaData =
        continuousVerificationService.getCVExecutionMetaData(stateExecutionId);
    assertThat(cvMetaData).isNotNull();
    assertThat(cvMetaData.getUuid()).isEqualTo(savedData.getUuid());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetDataForNode() {
    String serverConfigId = generateUuid();
    DataDogSetupTestNodeData testNodeData = DataDogSetupTestNodeData.builder()
                                                .stateType(DelegateStateType.DATA_DOG)
                                                .workflowId(generateUuid())
                                                .settingId(serverConfigId)
                                                .metrics("kubernetes.cpu.usage.total")
                                                .datadogServiceName("datadog")
                                                .build();

    DatadogConfig datadogConfig = DatadogConfig.builder().url("http://api.datadog.com/metrics").build();
    SettingAttribute settingAttribute = new SettingAttribute();
    settingAttribute.setValue(datadogConfig);

    when(settingsService.get(serverConfigId)).thenReturn(settingAttribute);
    when(secretManager.getEncryptionDetails(any(), any(), any())).thenReturn(new ArrayList<>());
    when(delegateProxyFactory.getV2(any(), any())).thenReturn(apmDelegateService);
    when(apmDelegateService.fetch(any(), any())).thenReturn("{}");
    when(datadogService.getConcatenatedListOfMetricsForValidation(anyString(), any(), any(), any()))
        .thenReturn("kubernetes.cpu.usage.total");
    continuousVerificationService.getDataForNode(accountId, serverConfigId, testNodeData, DelegateStateType.DATA_DOG);
    ArgumentCaptor<APMValidateCollectorConfig> validateCollectorConfigArgumentCaptor =
        ArgumentCaptor.forClass(APMValidateCollectorConfig.class);
    ArgumentCaptor<ThirdPartyApiCallLog> thirdPartyApiCallLogArgumentCaptor =
        ArgumentCaptor.forClass(ThirdPartyApiCallLog.class);
    verify(apmDelegateService, times(1))
        .fetch(validateCollectorConfigArgumentCaptor.capture(), thirdPartyApiCallLogArgumentCaptor.capture());

    APMValidateCollectorConfig apmValidateCollectorConfig = validateCollectorConfigArgumentCaptor.getValue();
    ThirdPartyApiCallLog thirdPartyApiCallLog = thirdPartyApiCallLogArgumentCaptor.getValue();

    assertThat(apmValidateCollectorConfig).isNotNull();
    assertThat(apmValidateCollectorConfig.getBaseUrl()).isEqualTo(datadogConfig.getUrl());
    assertThat(apmValidateCollectorConfig.getUrl()).doesNotContain(DATADOG_START_TIME_PLACEHOLDER);
    assertThat(apmValidateCollectorConfig.getUrl()).doesNotContain(DATADOG_END_TIME_PLACEHOLDER);
    assertThat(apmValidateCollectorConfig.getUrl()).contains("${apiKey}");
    assertThat(apmValidateCollectorConfig.getUrl()).contains("${applicationKey}");

    assertThat(thirdPartyApiCallLog).isNotNull();
    assertThat(thirdPartyApiCallLog.getAccountId()).isEqualTo(accountId);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testNotifyWorkflowVerificationState_nullAnalysisContext() throws IllegalAccessException {
    FieldUtils.writeField(continuousVerificationService, "wingsPersistence", persistence, true);
    boolean notifyStatus =
        continuousVerificationService.notifyWorkflowVerificationState(appId, stateExecutionId, SUCCESS);
    assertThat(notifyStatus).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testNotifyWorkflowVerificationState_logAnalysisWithSuccessState() throws IllegalAccessException {
    FieldUtils.writeField(continuousVerificationService, "wingsPersistence", persistence, true);

    AnalysisContext context =
        AnalysisContext.builder().stateExecutionId(stateExecutionId).stateType(ELK).accountId(accountId).build();
    persistence.save(context);
    boolean notifyStatus =
        continuousVerificationService.notifyWorkflowVerificationState(appId, stateExecutionId, SUCCESS);

    assertThat(notifyStatus).isTrue();

    LogMLAnalysisRecord mlAnalysisRecord = persistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).get();
    assertThat(mlAnalysisRecord).isNotNull();
    assertThat(mlAnalysisRecord.getAccountId()).isEqualTo(accountId);
    assertThat(mlAnalysisRecord.getAnalysisStatus()).isEqualTo(FEEDBACK_ANALYSIS_COMPLETE);

    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(logger, times(1)).info(stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue()).isEqualTo("The state was marked success");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testNotifyWorkflowVerificationState_logAnalysisWithErrorState() throws IllegalAccessException {
    FieldUtils.writeField(continuousVerificationService, "wingsPersistence", persistence, true);

    AnalysisContext context =
        AnalysisContext.builder().stateExecutionId(stateExecutionId).stateType(ELK).accountId(accountId).build();
    persistence.save(context);
    boolean notifyStatus =
        continuousVerificationService.notifyWorkflowVerificationState(appId, stateExecutionId, ERROR);

    assertThat(notifyStatus).isTrue();

    LogMLAnalysisRecord mlAnalysisRecord = persistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).get();
    assertThat(mlAnalysisRecord).isNotNull();
    assertThat(mlAnalysisRecord.getAccountId()).isEqualTo(accountId);
    assertThat(mlAnalysisRecord.getAnalysisStatus()).isEqualTo(FEEDBACK_ANALYSIS_COMPLETE);

    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(logger, times(0)).info(any());
    verify(logger, times(1)).error(stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue()).isEqualTo("The state was marked error");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testNotifyWorkflowVerificationState_logAnalysisWithSuccessStateButNotifyFailed()
      throws IllegalAccessException {
    FieldUtils.writeField(continuousVerificationService, "wingsPersistence", persistence, true);
    when(waitNotifyEngine.doneWith(any(), any())).thenThrow(new IllegalArgumentException(""));

    AnalysisContext context =
        AnalysisContext.builder().stateExecutionId(stateExecutionId).stateType(ELK).accountId(accountId).build();
    persistence.save(context);
    boolean notifyStatus =
        continuousVerificationService.notifyWorkflowVerificationState(appId, stateExecutionId, SUCCESS);

    assertThat(notifyStatus).isFalse();

    LogMLAnalysisRecord mlAnalysisRecord = persistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).get();
    assertThat(mlAnalysisRecord).isNotNull();
    assertThat(mlAnalysisRecord.getAccountId()).isEqualTo(accountId);
    assertThat(mlAnalysisRecord.getAnalysisStatus()).isEqualTo(FEEDBACK_ANALYSIS_COMPLETE);

    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(logger, times(1)).info(stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getValue()).isEqualTo("The state was marked success");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testDeepCopy() throws Exception {
    String textLoad = Resources.toString(
        ContinuousVerificationServiceImplTest.class.getResource("/apm/sampleStackdriverDatacollectionInfo.json"),
        Charsets.UTF_8);
    final Gson gson = new Gson();
    Type type = new TypeToken<StackDriverLogDataCollectionInfo>() {}.getType();
    StackDriverLogDataCollectionInfo dataCollectionInfo = gson.fromJson(textLoad, type);
    StackDriverLogDataCollectionInfo copied =
        gson.fromJson(gson.toJson(dataCollectionInfo), StackDriverLogDataCollectionInfo.class);
    copied.setHosts(Sets.newHashSet("host1", "host3", "host2"));
    assertThat(dataCollectionInfo.getHosts()).isNotSameAs(copied.getHosts());
    assertThat(dataCollectionInfo.getGcpConfig().toString()).isEqualTo(copied.getGcpConfig().toString());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCollectCVDataForWorkflow_moreThanFiveHosts() throws Exception {
    FieldUtils.writeField(continuousVerificationService, "wingsPersistence", persistence, true);
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(mock(EncryptedDataDetail.class));
    when(secretManager.getEncryptionDetails(any(), anyString(), anyString())).thenReturn(encryptedDataDetails);
    when(settingsService.get(anyString()))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withValue(GcpConfig.builder().build()).build());
    String textLoad = Resources.toString(
        ContinuousVerificationServiceImplTest.class.getResource("/apm/sampleAnalysisContext.json"), Charsets.UTF_8);
    final Gson gson = new Gson();
    Type type = new TypeToken<AnalysisContext>() {}.getType();
    AnalysisContext context = gson.fromJson(textLoad, type);
    context.setUuid("sampleUuid");
    textLoad = Resources.toString(
        ContinuousVerificationServiceImplTest.class.getResource("/apm/sampleStackdriverDatacollectionInfo.json"),
        Charsets.UTF_8);

    type = new TypeToken<StackDriverLogDataCollectionInfo>() {}.getType();
    StackDriverLogDataCollectionInfo dataCollectionInfo = gson.fromJson(textLoad, type);
    context.setDataCollectionInfo(dataCollectionInfo);
    persistence.save(context);
    continuousVerificationService.collectCVDataForWorkflow("sampleUuid", 26452678);
    ArgumentCaptor<DelegateTask> taskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService, times(4)).queueTaskV2(taskArgumentCaptor.capture());
    assertThat(taskArgumentCaptor.getAllValues().size()).isEqualTo(4);
    Set<String> hosts = context.getTestNodes().keySet();
    List<DelegateTask> tasks = taskArgumentCaptor.getAllValues();
    int i = 0;
    for (List<String> hostBatch : Lists.partition(new ArrayList<>(hosts), 5)) {
      assertThat(hostBatch).containsExactlyInAnyOrderElementsOf(
          ((LogDataCollectionInfo) tasks.get(i++).getData().getParameters()[0]).getHosts());
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNotifyWorkflowVerificationState_updatesWorkflowVerificationResult() throws IllegalAccessException {
    FieldUtils.writeField(continuousVerificationService, "wingsPersistence", persistence, true);

    AnalysisContext context =
        AnalysisContext.builder().stateExecutionId(stateExecutionId).stateType(ELK).accountId(accountId).build();
    persistence.save(context);

    workflowVerificationResultService.addWorkflowVerificationResult(WorkflowVerificationResult.builder()
                                                                        .accountId(accountId)
                                                                        .appId(appId)
                                                                        .stateExecutionId(stateExecutionId)
                                                                        .serviceId(serviceId)
                                                                        .envId(envId)
                                                                        .workflowId(workflowId)
                                                                        .stateType("PROMETHEUS")
                                                                        .executionStatus(ExecutionStatus.RUNNING)
                                                                        .build());
    WorkflowVerificationResult workflowVerificationResult =
        persistence.createQuery(WorkflowVerificationResult.class, excludeAuthority).get();
    assertThat(workflowVerificationResult.getMessage()).isNull();
    assertThat(workflowVerificationResult.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    assertThat(workflowVerificationResult.isAnalyzed()).isFalse();
    assertThat(workflowVerificationResult.isRollback()).isFalse();

    boolean notifyStatus =
        continuousVerificationService.notifyWorkflowVerificationState(appId, stateExecutionId, FAILED);

    assertThat(notifyStatus).isTrue();

    workflowVerificationResult = persistence.createQuery(WorkflowVerificationResult.class, excludeAuthority).get();
    assertThat(workflowVerificationResult.getMessage()).isEqualTo("The state was marked failed");
    assertThat(workflowVerificationResult.getExecutionStatus()).isEqualTo(FAILED);
    assertThat(workflowVerificationResult.isAnalyzed()).isFalse();
    assertThat(workflowVerificationResult.isRollback()).isFalse();
  }
}
