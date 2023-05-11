/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.apm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rest.RestResponse.Builder.aRestResponse;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SOWMYA;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.common.VerificationConstants.DEFAULT_GROUP_NAME;
import static software.wings.common.VerificationConstants.DEMO_FAILURE_TS_STATE_EXECUTION_ID;
import static software.wings.common.VerificationConstants.DEMO_SUCCESS_TS_STATE_EXECUTION_ID;
import static software.wings.service.impl.analysis.MetricDataAnalysisServiceImpl.DEFAULT_PAGE_SIZE;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.VerificationBase;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.event.usagemetrics.UsageMetricsHelper;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.service.intfc.ContinuousVerificationService;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.TimeSeriesAnalysisService;

import software.wings.alerts.AlertStatus;
import software.wings.beans.Application;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.Alert.AlertKeys;
import software.wings.beans.alert.cv.ContinuousVerificationAlertData;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.RiskLevel;
import software.wings.metrics.TimeSeriesDataRecord;
import software.wings.service.impl.SettingsServiceImpl;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.analysis.DeploymentTimeSeriesAnalysis;
import software.wings.service.impl.analysis.ExperimentalLogMLAnalysisRecord;
import software.wings.service.impl.analysis.MetricDataAnalysisServiceImpl;
import software.wings.service.impl.analysis.TimeSeriesMLAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesMLScores;
import software.wings.service.impl.analysis.TimeSeriesMLTxnSummary;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup;
import software.wings.service.impl.analysis.TimeSeriesMetricTemplates;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord;
import software.wings.service.impl.newrelic.NewRelicMetricAnalysisRecord.NewRelicMetricAnalysis;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.impl.newrelic.NewRelicMetricHostAnalysisValue;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.MetricDataAnalysisService;
import software.wings.service.intfc.SettingsService;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.verification.CVConfiguration;
import software.wings.verification.VerificationStateAnalysisExecutionData;
import software.wings.verification.appdynamics.AppDynamicsCVServiceConfiguration;
import software.wings.verification.newrelic.NewRelicCVServiceConfiguration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by rsingh on 9/7/18.
 */
public class MetricDataAnalysisServiceTest extends VerificationBase {
  private String accountId;
  private String appId;
  private String stateExecutionId;
  private String workflowExecutionId;
  private String serviceId;
  private String cvConfigId;
  private String groupName;
  @Mock private VerificationManagerClientHelper managerClientHelper;
  @Mock private UsageMetricsHelper usageMetricsHelper;
  @Mock private AppService appService;
  @Mock private VerificationManagerClient verificationManagerClient;

  @Inject private DataStoreService dataStoreService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject @InjectMocks private TimeSeriesAnalysisService metricDataAnalysisService;
  @Inject @InjectMocks private ContinuousVerificationService continuousVerificationService;
  @Inject @InjectMocks private LearningEngineService learningEngineService;
  private MetricDataAnalysisService managerAnalysisService;
  private ContinuousVerificationService spiedContinuousVerificationService;

  @Before
  public void setUp() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    when(managerClientHelper.callManagerWithRetry(any())).thenReturn(aRestResponse().withResource(false).build());
    when(usageMetricsHelper.getCVConfig(anyString())).thenReturn(mockCVConfig());
    when(usageMetricsHelper.getApplication(anyString())).thenReturn(mockApplication());
    accountId = wingsPersistence.save(anAccount().withAccountName(generateUuid()).build());
    appId = wingsPersistence.save(anApplication().name(generateUuid()).accountId(accountId).build());
    stateExecutionId = generateUuid();
    workflowExecutionId = generateUuid();
    serviceId = generateUuid();
    cvConfigId = wingsPersistence.save(new CVConfiguration());
    groupName = "groupName-";
    managerAnalysisService = new MetricDataAnalysisServiceImpl();
    SettingsService settingsService = new SettingsServiceImpl();
    FieldUtils.writeField(managerAnalysisService, "wingsPersistence", wingsPersistence, true);
    FieldUtils.writeField(settingsService, "wingsPersistence", wingsPersistence, true);
    FieldUtils.writeField(managerAnalysisService, "settingsService", settingsService, true);
    spiedContinuousVerificationService = spy(continuousVerificationService);
    FieldUtils.writeField(
        metricDataAnalysisService, "continuousVerificationService", spiedContinuousVerificationService, true);
    FieldUtils.writeField(managerAnalysisService, "appService", appService, true);
    FieldUtils.writeField(managerAnalysisService, "dataStoreService", dataStoreService, true);

    wingsPersistence.save(StateExecutionInstance.Builder.aStateExecutionInstance()
                              .uuid(stateExecutionId)
                              .displayName("name")
                              .stateExecutionMap(new HashMap<String, StateExecutionData>(
                                  ImmutableMap.of("name", new VerificationStateAnalysisExecutionData())))
                              .build());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveAnalysisRecordsML() {
    int numOfGroups = 5;
    int numOfMinutes = 10;
    LearningEngineAnalysisTask learningEngineAnalysisTask = getLearningEngineAnalysisTask();
    for (int i = 0; i < numOfGroups; i++) {
      for (int j = 1; j <= numOfMinutes; j++) {
        TimeSeriesMLAnalysisRecord mlAnalysisResponse = TimeSeriesMLAnalysisRecord.builder().build();
        mlAnalysisResponse.setCreatedAt(j);
        mlAnalysisResponse.setAccountId(accountId);
        mlAnalysisResponse.setTransactions(Collections.EMPTY_MAP);
        mlAnalysisResponse.setOverallMetricScores(new HashMap<String, Double>() {
          {
            put("key1", 0.76);
            put("key2", 0.5);
          }
        });
        metricDataAnalysisService.saveAnalysisRecordsML(accountId, StateType.DYNA_TRACE, appId, stateExecutionId,
            workflowExecutionId, groupName + i, j, learningEngineAnalysisTask.getUuid(), "-1", cvConfigId,
            mlAnalysisResponse, null);
      }
    }

    assertThat(wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class)
                   .filter("stateExecutionId", stateExecutionId)
                   .filter("appId", appId)
                   .asList()
                   .size())
        .isEqualTo(numOfGroups * numOfMinutes);
    Set<NewRelicMetricAnalysisRecord> resultList =
        managerAnalysisService.getMetricsAnalysis(appId, stateExecutionId, workflowExecutionId);

    assertThat(resultList).isNotNull();
    assertThat(resultList).hasSize(numOfGroups);
    resultList.forEach(record -> assertThat(record.getAnalysisMinute()).isEqualTo(numOfMinutes));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSorting() throws IOException {
    final Gson gson = new Gson();
    File file = new File("270-verification/src/test/resources/ts_sorting_record.json");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<TimeSeriesMLAnalysisRecord>() {}.getType();
      timeSeriesMLAnalysisRecord = gson.fromJson(br, type);

      timeSeriesMLAnalysisRecord.bundleAsJosnAndCompress();
      wingsPersistence.save(timeSeriesMLAnalysisRecord);
    }

    final Set<NewRelicMetricAnalysisRecord> metricsAnalysis =
        managerAnalysisService.getMetricsAnalysis(timeSeriesMLAnalysisRecord.getAppId(),
            timeSeriesMLAnalysisRecord.getStateExecutionId(), timeSeriesMLAnalysisRecord.getStateExecutionId());

    assertThat(metricsAnalysis).isNotNull();
    assertThat(metricsAnalysis).hasSize(1);
    assertThat(isNotEmpty(metricsAnalysis.iterator().next().getMetricAnalyses())).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAnalysisTag() throws IOException {
    File file = new File("270-verification/src/test/resources/time_series_todolist_analysis_record.json.zip");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord =
        JsonUtils.asObject(readZippedContents(file), TimeSeriesMLAnalysisRecord.class);

    int total = timeSeriesMLAnalysisRecord.getTransactions().size();
    for (int i = 0; i < total; i++) {
      final TimeSeriesMLTxnSummary timeSeriesMLTxnSummary =
          timeSeriesMLAnalysisRecord.getTransactions().get(String.valueOf(i));
      timeSeriesMLTxnSummary.setTxn_name("txn-" + (100 + i));
      timeSeriesMLTxnSummary.getMetrics().forEach((s, timeSeriesSummary) -> {
        timeSeriesSummary.setMax_risk(0);
        timeSeriesSummary.setTest_avg(0.0);
      });
    }
    timeSeriesMLAnalysisRecord.bundleAsJosnAndCompress();
    timeSeriesMLAnalysisRecord.setStateExecutionId(stateExecutionId);
    timeSeriesMLAnalysisRecord.setAppId(appId);
    timeSeriesMLAnalysisRecord.setUuid(null);
    wingsPersistence.save(timeSeriesMLAnalysisRecord);

    verifyAnalysisOffsetPageSizeAndSorting(total);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testLocalAnalysis() throws IOException {
    File file = new File("270-verification/src/test/resources/time_series_todolist_local_analysis_record.json.zip");
    NewRelicMetricAnalysisRecord newRelicMetricAnalysisRecord =
        JsonUtils.asObject(readZippedContents(file), NewRelicMetricAnalysisRecord.class);

    int total = newRelicMetricAnalysisRecord.getMetricAnalyses().size();
    for (int i = 0; i < total; i++) {
      final NewRelicMetricAnalysis newRelicMetricAnalysis = newRelicMetricAnalysisRecord.getMetricAnalyses().get(i);
      newRelicMetricAnalysis.setRiskLevel(RiskLevel.LOW);
      newRelicMetricAnalysis.setMetricValues(Lists.newArrayList());
      newRelicMetricAnalysis.setMetricName("txn-" + (100 + i));
    }

    newRelicMetricAnalysisRecord.setStateExecutionId(stateExecutionId);
    newRelicMetricAnalysisRecord.setAppId(appId);
    newRelicMetricAnalysisRecord.setUuid(null);
    wingsPersistence.save(newRelicMetricAnalysisRecord);

    verifyAnalysisOffsetPageSizeAndSorting(total);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveAnalysisRecordsML_savesRiskScore() throws IOException {
    File file = new File("270-verification/src/test/resources/247_analysis_record_with_risk.json.zip");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord =
        JsonUtils.asObject(readZippedContents(file), TimeSeriesMLAnalysisRecord.class);
    assertThat(timeSeriesMLAnalysisRecord.getRiskScore()).isNull();
    double riskScore = timeSeriesMLAnalysisRecord.getOverallMetricScores()
                           .values()
                           .stream()
                           .mapToDouble(score -> score)
                           .max()
                           .orElse(0.0);
    final NewRelicCVServiceConfiguration newRelicCVServiceConfiguration =
        NewRelicCVServiceConfiguration.builder().build();
    final String cvConfigId = wingsPersistence.save(newRelicCVServiceConfiguration);
    final LearningEngineAnalysisTask learningEngineAnalysisTask =
        LearningEngineAnalysisTask.builder().state_execution_id(generateUuid()).cluster_level(0).build();
    final String taskId = wingsPersistence.save(learningEngineAnalysisTask);
    timeSeriesMLAnalysisRecord.setCvConfigId(cvConfigId);
    metricDataAnalysisService.saveAnalysisRecordsML(accountId, StateType.NEW_RELIC, appId,
        learningEngineAnalysisTask.getState_execution_id(), null, DEFAULT_GROUP_NAME, 10, taskId, null, cvConfigId,
        timeSeriesMLAnalysisRecord, null);
    final TimeSeriesMLAnalysisRecord savedAnalysisRecord =
        wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class, excludeAuthority).get();
    assertThat(savedAnalysisRecord.getRiskScore()).isEqualTo(riskScore, offset(0.0001));
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testSaveAnalysisRecordsML_savesRiskScoreWhenNoTestMetrics() throws IOException {
    File file = new File("270-verification/src/test/resources/247_analysis_record_with_risk.json.zip");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord =
        JsonUtils.asObject(readZippedContents(file), TimeSeriesMLAnalysisRecord.class);
    assertThat(timeSeriesMLAnalysisRecord.getRiskScore()).isNull();
    timeSeriesMLAnalysisRecord.setOverallMetricScores(new HashMap<>());
    final NewRelicCVServiceConfiguration newRelicCVServiceConfiguration =
        NewRelicCVServiceConfiguration.builder().build();
    final String cvConfigId = wingsPersistence.save(newRelicCVServiceConfiguration);
    final LearningEngineAnalysisTask learningEngineAnalysisTask =
        LearningEngineAnalysisTask.builder().state_execution_id(generateUuid()).cluster_level(0).build();
    final String taskId = wingsPersistence.save(learningEngineAnalysisTask);
    timeSeriesMLAnalysisRecord.setCvConfigId(cvConfigId);
    metricDataAnalysisService.saveAnalysisRecordsML(accountId, StateType.NEW_RELIC, appId,
        learningEngineAnalysisTask.getState_execution_id(), null, DEFAULT_GROUP_NAME, 10, taskId, null, cvConfigId,
        timeSeriesMLAnalysisRecord, null);
    final TimeSeriesMLAnalysisRecord savedAnalysisRecord =
        wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class, excludeAuthority).get();
    assertThat(savedAnalysisRecord.getRiskScore()).isEqualTo(-1.0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveAnalysisRecordsML_throwsAlerts() throws IOException {
    when(verificationManagerClient.triggerCVAlert(anyString(), any(ContinuousVerificationAlertData.class)))
        .then(invocation -> {
          Object[] args = invocation.getArguments();
          wingsPersistence.save(Alert.builder().alertData((ContinuousVerificationAlertData) args[1]).build());
          Call<RestResponse<Boolean>> restCall = mock(Call.class);
          when(restCall.clone()).thenReturn(restCall);
          when(restCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
          return restCall;
        });
    File file = new File("270-verification/src/test/resources/247_analysis_record_with_risk.json.zip");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord =
        JsonUtils.asObject(readZippedContents(file), TimeSeriesMLAnalysisRecord.class);
    double riskScore = timeSeriesMLAnalysisRecord.getOverallMetricScores()
                           .values()
                           .stream()
                           .mapToDouble(score -> score)
                           .max()
                           .orElse(0.0);
    final NewRelicCVServiceConfiguration newRelicCVServiceConfiguration =
        NewRelicCVServiceConfiguration.builder().build();
    newRelicCVServiceConfiguration.setAlertEnabled(true);
    newRelicCVServiceConfiguration.setAlertThreshold(0.0);
    final String cvConfigId = wingsPersistence.save(newRelicCVServiceConfiguration);
    final LearningEngineAnalysisTask learningEngineAnalysisTask =
        LearningEngineAnalysisTask.builder().state_execution_id(generateUuid()).cluster_level(0).build();
    final String taskId = wingsPersistence.save(learningEngineAnalysisTask);
    timeSeriesMLAnalysisRecord.setCvConfigId(cvConfigId);
    metricDataAnalysisService.saveAnalysisRecordsML(accountId, StateType.NEW_RELIC, appId,
        learningEngineAnalysisTask.getState_execution_id(), null, DEFAULT_GROUP_NAME, 10, taskId, null, cvConfigId,
        timeSeriesMLAnalysisRecord, null);
    verify(spiedContinuousVerificationService, times(1)).triggerTimeSeriesAlertIfNecessary(cvConfigId, riskScore, 10);
  }

  private void waitForAlert(int expectedNumOfAlerts, Optional<AlertStatus> alertStatus) {
    int tryCount = 0;
    long numOfAlerts;
    do {
      Query<Alert> alertQuery = wingsPersistence.createQuery(Alert.class, excludeAuthority);
      if (alertStatus.isPresent()) {
        alertQuery.filter(AlertKeys.status, alertStatus.get());
      }
      numOfAlerts = alertQuery.count();
      tryCount++;
      sleep(ofMillis(500));
    } while (numOfAlerts < expectedNumOfAlerts && tryCount < 20);
  }

  private void verifyAnalysisOffsetPageSizeAndSorting(int total) {
    wingsPersistence.save(AnalysisContext.builder().stateExecutionId(stateExecutionId).timeDuration(5).build());

    DeploymentTimeSeriesAnalysis metricsAnalysis =
        managerAnalysisService.getMetricsAnalysis(stateExecutionId, Optional.empty(), Optional.empty(), false);
    assertThat(metricsAnalysis).isNotNull();
    assertThat(metricsAnalysis.getStateExecutionId()).isEqualTo(stateExecutionId);
    assertThat(metricsAnalysis.getBaseLineExecutionId()).isNull();
    assertThat(metricsAnalysis.getTotal()).isEqualTo(total);
    assertThat(metricsAnalysis.getMetricAnalyses().size()).isEqualTo(DEFAULT_PAGE_SIZE);

    List<NewRelicMetricAnalysis> metricAnalyses = metricsAnalysis.getMetricAnalyses();

    // ask with page size
    metricsAnalysis =
        managerAnalysisService.getMetricsAnalysis(stateExecutionId, Optional.empty(), Optional.of(10), false);
    assertThat(metricsAnalysis).isNotNull();
    assertThat(metricsAnalysis.getStateExecutionId()).isEqualTo(stateExecutionId);
    assertThat(metricsAnalysis.getBaseLineExecutionId()).isNull();
    assertThat(metricsAnalysis.getTotal()).isEqualTo(total);
    assertThat(metricsAnalysis.getMetricAnalyses().size()).isEqualTo(DEFAULT_PAGE_SIZE);
    assertThat(metricsAnalysis.getMetricAnalyses()).isEqualTo(metricAnalyses);
    for (int i = 0; i < DEFAULT_PAGE_SIZE; i++) {
      assertThat(metricsAnalysis.getMetricAnalyses().get(i).getMetricName()).isEqualTo("txn-" + (100 + i));
    }

    // test with offset and page size
    int offset = 0;
    while (offset < total) {
      metricsAnalysis = managerAnalysisService.getMetricsAnalysis(
          stateExecutionId, Optional.of(offset), Optional.of(DEFAULT_PAGE_SIZE), false);

      for (int i = 0; i < metricsAnalysis.getMetricAnalyses().size(); i++) {
        assertThat(metricsAnalysis.getMetricAnalyses().get(i).getMetricName()).isEqualTo("txn-" + (offset + 100 + i));
      }
      offset += metricsAnalysis.getMetricAnalyses().size();
    }

    // test with offset beyond the size
    metricsAnalysis = managerAnalysisService.getMetricsAnalysis(
        stateExecutionId, Optional.of(total), Optional.of(DEFAULT_PAGE_SIZE), false);
    assertThat(metricsAnalysis.getMetricAnalyses().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAnalysisNodeData() throws IOException {
    File file = new File("270-verification/src/test/resources/time_series_todolist_analysis_record.json.zip");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord =
        JsonUtils.asObject(readZippedContents(file), TimeSeriesMLAnalysisRecord.class);

    timeSeriesMLAnalysisRecord.bundleAsJosnAndCompress();
    timeSeriesMLAnalysisRecord.setStateExecutionId(stateExecutionId);
    timeSeriesMLAnalysisRecord.setAppId(appId);
    timeSeriesMLAnalysisRecord.setUuid(null);
    wingsPersistence.save(timeSeriesMLAnalysisRecord);

    wingsPersistence.save(AnalysisContext.builder().stateExecutionId(stateExecutionId).timeDuration(10).build());

    DeploymentTimeSeriesAnalysis metricsAnalysis =
        managerAnalysisService.getMetricsAnalysis(stateExecutionId, Optional.empty(), Optional.empty(), false);
    assertThat(metricsAnalysis).isNotNull();
    assertThat(metricsAnalysis.getStateExecutionId()).isEqualTo(stateExecutionId);
    assertThat(metricsAnalysis.getBaseLineExecutionId()).isNull();
    assertThat(metricsAnalysis.getMetricAnalyses().size()).isEqualTo(DEFAULT_PAGE_SIZE);
    verifyNodeData(metricsAnalysis, Optional.of("error"));
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testAnalysisNodeDataCustomThresholdBands() throws IOException {
    final Gson gson = new Gson();
    File file = new File("270-verification/src/test/resources/timeseries_analysis_custom_failfast.json");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<TimeSeriesMLAnalysisRecord>() {}.getType();
      timeSeriesMLAnalysisRecord = gson.fromJson(br, type);

      timeSeriesMLAnalysisRecord.setStateExecutionId(stateExecutionId);
      timeSeriesMLAnalysisRecord.setAppId(appId);
      timeSeriesMLAnalysisRecord.setUuid(null);
      timeSeriesMLAnalysisRecord.bundleAsJosnAndCompress();
      wingsPersistence.save(timeSeriesMLAnalysisRecord);
    }

    wingsPersistence.save(AnalysisContext.builder().stateExecutionId(stateExecutionId).timeDuration(10).build());

    DeploymentTimeSeriesAnalysis metricsAnalysis =
        managerAnalysisService.getMetricsAnalysis(stateExecutionId, Optional.empty(), Optional.empty(), false);
    assertThat(metricsAnalysis).isNotNull();
    assertThat(metricsAnalysis.getStateExecutionId()).isEqualTo(stateExecutionId);
    assertThat(metricsAnalysis.getMetricAnalyses()
                   .get(0)
                   .getMetricValues()
                   .get(0)
                   .getHostAnalysisValues()
                   .get(0)
                   .getUpperThresholds())
        .isNotEmpty();
    assertThat(metricsAnalysis.getMetricAnalyses()
                   .get(0)
                   .getMetricValues()
                   .get(0)
                   .getHostAnalysisValues()
                   .get(0)
                   .getLowerThresholds())
        .isNotEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testAnalysisNodeData_FailFastRiskLevel() throws IOException {
    final Gson gson = new Gson();
    File file = new File("270-verification/src/test/resources/timeseries_analysis_custom_failfast.json");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<TimeSeriesMLAnalysisRecord>() {}.getType();
      timeSeriesMLAnalysisRecord = gson.fromJson(br, type);

      timeSeriesMLAnalysisRecord.setStateExecutionId(stateExecutionId);
      timeSeriesMLAnalysisRecord.setAppId(appId);
      timeSeriesMLAnalysisRecord.setUuid(null);
      timeSeriesMLAnalysisRecord.bundleAsJosnAndCompress();
      wingsPersistence.save(timeSeriesMLAnalysisRecord);
    }

    wingsPersistence.save(AnalysisContext.builder().stateExecutionId(stateExecutionId).timeDuration(10).build());

    DeploymentTimeSeriesAnalysis metricsAnalysis =
        managerAnalysisService.getMetricsAnalysis(stateExecutionId, Optional.empty(), Optional.empty(), false);
    assertThat(metricsAnalysis).isNotNull();
    assertThat(metricsAnalysis.getStateExecutionId()).isEqualTo(stateExecutionId);
    assertThat(metricsAnalysis.getMetricAnalyses()
                   .get(0)
                   .getMetricValues()
                   .get(0)
                   .getHostAnalysisValues()
                   .get(0)
                   .getRiskLevel()
                   .name())
        .isEqualTo(RiskLevel.HIGH.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testAnalysisNodeData_FailFastRiskLevelLow() throws IOException {
    final Gson gson = new Gson();
    File file = new File("270-verification/src/test/resources/timeseries_analysis_custom_dont_failfast.json");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<TimeSeriesMLAnalysisRecord>() {}.getType();
      timeSeriesMLAnalysisRecord = gson.fromJson(br, type);

      timeSeriesMLAnalysisRecord.setStateExecutionId(stateExecutionId);
      timeSeriesMLAnalysisRecord.setAppId(appId);
      timeSeriesMLAnalysisRecord.setUuid(null);
      timeSeriesMLAnalysisRecord.bundleAsJosnAndCompress();
      wingsPersistence.save(timeSeriesMLAnalysisRecord);
    }

    wingsPersistence.save(AnalysisContext.builder().stateExecutionId(stateExecutionId).timeDuration(10).build());

    DeploymentTimeSeriesAnalysis metricsAnalysis =
        managerAnalysisService.getMetricsAnalysis(stateExecutionId, Optional.empty(), Optional.empty(), false);
    assertThat(metricsAnalysis).isNotNull();
    assertThat(metricsAnalysis.getStateExecutionId()).isEqualTo(stateExecutionId);
    assertThat(metricsAnalysis.getMetricAnalyses()
                   .get(0)
                   .getMetricValues()
                   .get(0)
                   .getHostAnalysisValues()
                   .get(0)
                   .getRiskLevel()
                   .name())
        .isEqualTo(RiskLevel.LOW.name());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAnalysisDemoFailure() throws IOException {
    File file = new File("270-verification/src/test/resources/time_series_todolist_demo_failure_analysis.json.zip");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord =
        JsonUtils.asObject(readZippedContents(file), TimeSeriesMLAnalysisRecord.class);

    timeSeriesMLAnalysisRecord.bundleAsJosnAndCompress();
    timeSeriesMLAnalysisRecord.setAppId(appId);
    wingsPersistence.save(timeSeriesMLAnalysisRecord);

    wingsPersistence.save(AnalysisContext.builder().stateExecutionId(stateExecutionId).timeDuration(10).build());
    final StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                              .uuid(stateExecutionId)
                                                              .status(ExecutionStatus.FAILED)
                                                              .stateType(StateType.APP_DYNAMICS.name())
                                                              .displayName("abc")
                                                              .build();
    final String settingAttributeId = wingsPersistence.save(aSettingAttribute().withName("prod").build());
    Map<String, StateExecutionData> stateExecutionMap = new HashMap<>();
    stateExecutionMap.put(stateExecutionInstance.getDisplayName(),
        VerificationStateAnalysisExecutionData.builder().serverConfigId(settingAttributeId).build());
    stateExecutionInstance.setStateExecutionMap(stateExecutionMap);
    wingsPersistence.save(stateExecutionInstance);

    DeploymentTimeSeriesAnalysis metricsAnalysis =
        managerAnalysisService.getMetricsAnalysisForDemo(stateExecutionId, Optional.empty(), Optional.empty());
    assertThat(metricsAnalysis).isNotNull();
    assertThat(metricsAnalysis.getStateExecutionId())
        .isEqualTo(DEMO_FAILURE_TS_STATE_EXECUTION_ID + StateType.APP_DYNAMICS);
    verifyNodeData(metricsAnalysis, Optional.empty());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAnalysisDemoSuccess() throws IOException {
    File file = new File("270-verification/src/test/resources/time_series_todolist_demo_success_analysis.json.zip");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord =
        JsonUtils.asObject(readZippedContents(file), TimeSeriesMLAnalysisRecord.class);

    timeSeriesMLAnalysisRecord.bundleAsJosnAndCompress();
    timeSeriesMLAnalysisRecord.setAppId(appId);
    wingsPersistence.save(timeSeriesMLAnalysisRecord);

    wingsPersistence.save(AnalysisContext.builder().stateExecutionId(stateExecutionId).timeDuration(10).build());
    final StateExecutionInstance stateExecutionInstance = aStateExecutionInstance()
                                                              .uuid(stateExecutionId)
                                                              .status(ExecutionStatus.SUCCESS)
                                                              .stateType(StateType.APP_DYNAMICS.name())
                                                              .displayName("abc")
                                                              .build();
    final String settingAttributeId = wingsPersistence.save(aSettingAttribute().withName("dev").build());
    Map<String, StateExecutionData> stateExecutionMap = new HashMap<>();
    stateExecutionMap.put(stateExecutionInstance.getDisplayName(),
        VerificationStateAnalysisExecutionData.builder().serverConfigId(settingAttributeId).build());
    stateExecutionInstance.setStateExecutionMap(stateExecutionMap);
    wingsPersistence.save(stateExecutionInstance);

    DeploymentTimeSeriesAnalysis metricsAnalysis =
        managerAnalysisService.getMetricsAnalysisForDemo(stateExecutionId, Optional.empty(), Optional.empty());
    assertThat(metricsAnalysis).isNotNull();
    assertThat(metricsAnalysis.getStateExecutionId())
        .isEqualTo(DEMO_SUCCESS_TS_STATE_EXECUTION_ID + StateType.APP_DYNAMICS);
    assertThat(metricsAnalysis.getMetricAnalyses().size()).isEqualTo(DEFAULT_PAGE_SIZE);
    verifyNodeData(metricsAnalysis, Optional.empty());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testLocalAnalysisNodeData() throws IOException {
    File file = new File("270-verification/src/test/resources/time_series_todolist_local_analysis_record.json.zip");
    NewRelicMetricAnalysisRecord newRelicMetricAnalysisRecord =
        JsonUtils.asObject(readZippedContents(file), NewRelicMetricAnalysisRecord.class);

    newRelicMetricAnalysisRecord.setStateExecutionId(stateExecutionId);
    newRelicMetricAnalysisRecord.setAppId(appId);
    newRelicMetricAnalysisRecord.setUuid(null);
    wingsPersistence.save(newRelicMetricAnalysisRecord);

    wingsPersistence.save(AnalysisContext.builder().stateExecutionId(stateExecutionId).timeDuration(5).build());

    DeploymentTimeSeriesAnalysis metricsAnalysis =
        managerAnalysisService.getMetricsAnalysis(stateExecutionId, Optional.empty(), Optional.empty(), false);
    assertThat(metricsAnalysis).isNotNull();
    assertThat(metricsAnalysis.getStateExecutionId()).isEqualTo(stateExecutionId);
    assertThat(metricsAnalysis.getBaseLineExecutionId()).isNull();
    assertThat(metricsAnalysis.getMetricAnalyses().size()).isEqualTo(DEFAULT_PAGE_SIZE);
    verifyNodeData(metricsAnalysis, Optional.of("error"));
  }

  private void verifyNodeData(DeploymentTimeSeriesAnalysis metricsAnalysis, Optional<String> metricName) {
    for (int i = 0; i < DEFAULT_PAGE_SIZE; i++) {
      final NewRelicMetricAnalysis metricAnalysis = metricsAnalysis.getMetricAnalyses().get(i);
      metricAnalysis.getMetricValues().forEach(metricValue -> {
        if (metricName.isPresent() && !metricValue.getName().equals(metricName)) {
          return;
        }
        final List<NewRelicMetricHostAnalysisValue> hostAnalysisValues = metricValue.getHostAnalysisValues();
        assertThat(hostAnalysisValues.size()).isGreaterThan(0);
        hostAnalysisValues.forEach(hostAnalysisValue -> {
          assertThat(hostAnalysisValue.getRiskLevel()).isNotNull();
          assertThat(hostAnalysisValue.getTestHostName()).isNotNull();
          assertThat(hostAnalysisValue.getControlHostName()).isNotNull();
          assertThat(hostAnalysisValue.getTestValues().size()).isGreaterThan(0);
          assertThat(hostAnalysisValue.getControlValues().size()).isGreaterThan(0);
        });
      });
    }
  }

  private LearningEngineAnalysisTask getLearningEngineAnalysisTask() {
    LearningEngineAnalysisTask learningEngineAnalysisTask = LearningEngineAnalysisTask.builder()
                                                                .state_execution_id(stateExecutionId)
                                                                .workflow_execution_id(workflowExecutionId)
                                                                .executionStatus(ExecutionStatus.QUEUED)
                                                                .build();
    learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);
    return learningEngineAnalysisTask;
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetHistoricalAnalysis() {
    // setup
    int sampleMinute = 1000000;
    TimeSeriesMLAnalysisRecord testRecord = TimeSeriesMLAnalysisRecord.builder().build();
    testRecord.setAnalysisMinute(sampleMinute);

    for (int j = 1; j <= 5; j++) {
      testRecord.setCreatedAt(j);
      testRecord.setTransactions(Collections.EMPTY_MAP);
      testRecord.setAppId(appId);
      testRecord.setCvConfigId(cvConfigId);
      testRecord.setAnalysisMinute(sampleMinute);

      wingsPersistence.save(testRecord);
      sampleMinute = sampleMinute - (int) TimeUnit.DAYS.toMinutes(7);
      testRecord = TimeSeriesMLAnalysisRecord.builder().build();
    }

    // test behavior
    List<TimeSeriesMLAnalysisRecord> historicalRecords =
        metricDataAnalysisService.getHistoricalAnalysis(accountId, appId, serviceId, cvConfigId, 1000000, null);

    // verify
    assertThat(historicalRecords).isNotNull();
    assertThat(historicalRecords).hasSize(4);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetHistoricalAnalysisNoHistorical() {
    // setup
    int sampleMinute = 1000000;
    TimeSeriesMLAnalysisRecord testRecord = TimeSeriesMLAnalysisRecord.builder().build();
    testRecord.setAnalysisMinute(sampleMinute);

    for (int j = 1; j <= 5; j++) {
      testRecord.setCreatedAt(j);
      testRecord.setTransactions(Collections.EMPTY_MAP);
      testRecord.setAppId(appId);
      testRecord.setCvConfigId(cvConfigId);
      testRecord.setAnalysisMinute(sampleMinute);

      wingsPersistence.save(testRecord);
      sampleMinute = sampleMinute - (int) TimeUnit.DAYS.toMinutes(7);
      testRecord = TimeSeriesMLAnalysisRecord.builder().build();
    }

    // test behavior
    List<TimeSeriesMLAnalysisRecord> historicalRecords =
        metricDataAnalysisService.getHistoricalAnalysis(accountId, appId, serviceId, cvConfigId, 1000, null);

    // verify
    assertThat(historicalRecords).isNotNull();
    assertThat(historicalRecords).hasSize(0);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetHistoricalAnalysisBadAppIdCvConfigId() {
    // setup
    int sampleMinute = 1000000;
    TimeSeriesMLAnalysisRecord testRecord = TimeSeriesMLAnalysisRecord.builder().build();
    testRecord.setAnalysisMinute(sampleMinute);

    for (int j = 1; j <= 5; j++) {
      testRecord.setCreatedAt(j);
      testRecord.setTransactions(Collections.EMPTY_MAP);
      testRecord.setAppId(appId);
      testRecord.setCvConfigId(cvConfigId);
      testRecord.setAnalysisMinute(sampleMinute);

      wingsPersistence.save(testRecord);
      sampleMinute = sampleMinute - (int) TimeUnit.DAYS.toMinutes(7);
      testRecord = TimeSeriesMLAnalysisRecord.builder().build();
    }

    List<TimeSeriesMLAnalysisRecord> historicalRecordsBadCvConfigId = metricDataAnalysisService.getHistoricalAnalysis(
        accountId, appId, serviceId, cvConfigId + "-bad", 1000000, null);

    // verify
    assertThat(historicalRecordsBadCvConfigId).isNotNull();
    assertThat(historicalRecordsBadCvConfigId).hasSize(0);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCompression() throws IOException {
    long analysisMinute = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis());
    final CVConfiguration cvConfiguration = new CVConfiguration();
    cvConfiguration.setAppId(appId);
    String cvConfigId = wingsPersistence.save(cvConfiguration);
    LearningEngineAnalysisTask learningEngineAnalysisTask = getLearningEngineAnalysisTask();
    final Gson gson = new Gson();
    File file = new File("270-verification/src/test/resources/ts_analysis_record.json");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<TimeSeriesMLAnalysisRecord>() {}.getType();
      timeSeriesMLAnalysisRecord = gson.fromJson(br, type);
      timeSeriesMLAnalysisRecord.setAppId(appId);
      timeSeriesMLAnalysisRecord.setCvConfigId(generateUuid());
      timeSeriesMLAnalysisRecord.setAnalysisMinute((int) analysisMinute);
      timeSeriesMLAnalysisRecord.setOverallMetricScores(new HashMap<String, Double>() {
        {
          put("key1", 0.76);
          put("key2", 0.5);
        }
      });
    }
    assertThat(isEmpty(timeSeriesMLAnalysisRecord.getTransactions())).isFalse();
    assertThat(timeSeriesMLAnalysisRecord.getTransactionsCompressedJson()).isNull();
    metricDataAnalysisService.saveAnalysisRecordsML(accountId, StateType.APP_DYNAMICS, appId, stateExecutionId,
        workflowExecutionId, generateUuid(), (int) analysisMinute, learningEngineAnalysisTask.getUuid(), generateUuid(),
        cvConfigId, timeSeriesMLAnalysisRecord, null);

    final TimeSeriesMLAnalysisRecord savedRecord = wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class)
                                                       .filter("appId", appId)
                                                       .filter("stateExecutionId", stateExecutionId)
                                                       .get();
    assertThat(savedRecord.getTransactions()).isNull();
    assertThat(timeSeriesMLAnalysisRecord.getTransactionsCompressedJson()).isNull();
    assertThat(timeSeriesMLAnalysisRecord.getMetricAnalysisValuesCompressed()).isNotEmpty();
    TimeSeriesMLAnalysisRecord readRecord =
        metricDataAnalysisService.getPreviousAnalysis(appId, cvConfigId, analysisMinute, null);
    assertThat(readRecord.getTransactions()).isNotEmpty();
    assertThat(readRecord.getMetricAnalysisValuesCompressed()).isNull();
    assertThat(readRecord.getTransactionsCompressedJson()).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCompression_withKeyTxns() throws IOException {
    File file = new File("270-verification/src/test/resources/ts_analysis_record_key_txn.json.zip");
    TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord =
        JsonUtils.asObject(readZippedContents(file), TimeSeriesMLAnalysisRecord.class);
    assertThat(timeSeriesMLAnalysisRecord.getTransactions()).isNotEmpty();
    assertThat(timeSeriesMLAnalysisRecord.getOverallMetricScores()).isNotEmpty();
    assertThat(timeSeriesMLAnalysisRecord.getKeyTransactionMetricScores()).isNotEmpty();
    assertThat(timeSeriesMLAnalysisRecord.getAnomalies()).isNotEmpty();
    assertThat(timeSeriesMLAnalysisRecord.getTransactionMetricSums()).isNotEmpty();
    assertThat(timeSeriesMLAnalysisRecord.getTransactionsCompressedJson()).isNull();
    assertThat(timeSeriesMLAnalysisRecord.getMetricAnalysisValuesCompressed()).isNull();

    timeSeriesMLAnalysisRecord.bundleAsJosnAndCompress();
    assertThat(timeSeriesMLAnalysisRecord.getTransactions()).isNull();
    assertThat(timeSeriesMLAnalysisRecord.getOverallMetricScores()).isNull();
    assertThat(timeSeriesMLAnalysisRecord.getKeyTransactionMetricScores()).isNull();
    assertThat(timeSeriesMLAnalysisRecord.getAnomalies()).isNull();
    assertThat(timeSeriesMLAnalysisRecord.getTransactionMetricSums()).isNull();
    assertThat(timeSeriesMLAnalysisRecord.getTransactionsCompressedJson()).isNull();
    assertThat(timeSeriesMLAnalysisRecord.getMetricAnalysisValuesCompressed()).isNotEmpty();

    TimeSeriesMLAnalysisRecord expectedAfterDecompression =
        JsonUtils.asObject(readZippedContents(file), TimeSeriesMLAnalysisRecord.class);
    timeSeriesMLAnalysisRecord.decompress(false);

    assertThat(timeSeriesMLAnalysisRecord).isEqualTo(expectedAfterDecompression);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCleanup() {
    int numOfRecords = 10;
    for (int i = 0; i < numOfRecords; i++) {
      wingsPersistence.save(
          TimeSeriesMetricTemplates.builder().stateExecutionId(stateExecutionId).cvConfigId("cv" + i).build());
      NewRelicMetricDataRecord newRelicMetricDataRecord = new NewRelicMetricDataRecord();
      newRelicMetricDataRecord.setStateExecutionId(stateExecutionId);
      newRelicMetricDataRecord.setAppId(appId);
      newRelicMetricDataRecord.setDataCollectionMinute(i);
      wingsPersistence.save(newRelicMetricDataRecord);
      wingsPersistence.save(NewRelicMetricAnalysisRecord.builder()
                                .stateExecutionId(stateExecutionId)
                                .analysisMinute(i)
                                .appId(appId)
                                .build());
      TimeSeriesMLAnalysisRecord timeSeriesMLAnalysisRecord = TimeSeriesMLAnalysisRecord.builder().build();
      timeSeriesMLAnalysisRecord.setStateExecutionId(stateExecutionId);
      timeSeriesMLAnalysisRecord.setAnalysisMinute(i);
      wingsPersistence.save(timeSeriesMLAnalysisRecord);
      wingsPersistence.save(TimeSeriesMLScores.builder().stateExecutionId(stateExecutionId).analysisMinute(i).build());
      wingsPersistence.save(
          ContinuousVerificationExecutionMetaData.builder().stateExecutionId(stateExecutionId).build());
      wingsPersistence.save(
          LearningEngineAnalysisTask.builder().state_execution_id(stateExecutionId).analysis_minute(i).build());
      wingsPersistence.save(LearningEngineExperimentalAnalysisTask.builder()
                                .state_execution_id(stateExecutionId)
                                .analysis_minute(i)
                                .build());
      ExperimentalLogMLAnalysisRecord experimentalLogMLAnalysisRecord = new ExperimentalLogMLAnalysisRecord();
      experimentalLogMLAnalysisRecord.setStateExecutionId(stateExecutionId);
      experimentalLogMLAnalysisRecord.setLogCollectionMinute(i);
      wingsPersistence.save(experimentalLogMLAnalysisRecord);

      wingsPersistence.save(
          TimeSeriesMetricGroup.builder().stateExecutionId(stateExecutionId).stateType(StateType.values()[i]).build());
      wingsPersistence.save(
          AnalysisContext.builder().stateExecutionId(stateExecutionId).serviceId("service-" + i).build());
      wingsPersistence.save(TimeSeriesDataRecord.builder().stateExecutionId(stateExecutionId).build());
    }

    assertThat(wingsPersistence.createQuery(TimeSeriesMetricTemplates.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(NewRelicMetricDataRecord.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(NewRelicMetricAnalysisRecord.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(TimeSeriesMLScores.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class).count())
        .isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(TimeSeriesMetricGroup.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(AnalysisContext.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(TimeSeriesDataRecord.class).count()).isEqualTo(numOfRecords);

    managerAnalysisService.cleanUpForMetricRetry(stateExecutionId);
    assertThat(wingsPersistence.createQuery(TimeSeriesMetricTemplates.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(NewRelicMetricDataRecord.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(NewRelicMetricAnalysisRecord.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(TimeSeriesMLAnalysisRecord.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(TimeSeriesMLScores.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(TimeSeriesMetricGroup.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(AnalysisContext.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(TimeSeriesDataRecord.class).count()).isEqualTo(0);
  }

  private Application mockApplication() {
    Application app = new Application();
    app.setUuid(APP_ID);
    app.setName(APP_NAME);
    return app;
  }

  private CVConfiguration mockCVConfig() {
    AppDynamicsCVServiceConfiguration config =
        AppDynamicsCVServiceConfiguration.builder().appDynamicsApplicationId("1234").tierId("5678").build();
    config.setConnectorId("connectorId");
    config.setName("test Config");
    config.setUuid("cvConfigId");
    config.setAppId("appId");
    config.setServiceId("serviceId");
    return config;
  }

  private String readZippedContents(File file) throws IOException {
    ZipInputStream in = new ZipInputStream(new FileInputStream(file));

    // Get the first entry
    ZipEntry entry = in.getNextEntry();

    ByteArrayOutputStream out = new ByteArrayOutputStream();

    // Transfer bytes from the ZIP file to the output file
    byte[] buf = new byte[1024];
    int len;
    while ((len = in.read(buf)) > 0) {
      out.write(buf, 0, len);
    }

    out.close();
    in.close();

    return new String(out.toByteArray());
  }
}
