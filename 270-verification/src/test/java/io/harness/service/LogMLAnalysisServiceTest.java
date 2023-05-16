/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.data.encoding.EncodingUtils.compressString;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.NANDAN;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.rule.OwnerRule.SRIRAM;
import static io.harness.service.LearningEngineAnalysisServiceImpl.BACKOFF_LIMIT;

import static software.wings.common.VerificationConstants.NON_HOST_PREVIOUS_ANALYSIS;
import static software.wings.sm.StateType.ELK;
import static software.wings.sm.StateType.SPLUNKV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.VerificationBase;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SortOrder.OrderType;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.service.intfc.ContinuousVerificationService;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.LogAnalysisService;

import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.metrics.RiskLevel;
import software.wings.service.impl.MongoDataStoreServiceImpl;
import software.wings.service.impl.WorkflowExecutionServiceImpl;
import software.wings.service.impl.analysis.AnalysisComparisonStrategy;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.AnalysisServiceImpl;
import software.wings.service.impl.analysis.AnalysisTolerance;
import software.wings.service.impl.analysis.ContinuousVerificationExecutionMetaData;
import software.wings.service.impl.analysis.ExperimentalLogMLAnalysisRecord;
import software.wings.service.impl.analysis.LogDataRecord;
import software.wings.service.impl.analysis.LogDataRecord.LogDataRecordKeys;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.analysis.LogMLAnalysisRecord;
import software.wings.service.impl.analysis.LogMLAnalysisRecord.LogMLAnalysisRecordKeys;
import software.wings.service.impl.analysis.LogMLAnalysisStatus;
import software.wings.service.impl.analysis.LogMLAnalysisSummary;
import software.wings.service.impl.analysis.LogMLClusterSummary;
import software.wings.service.impl.analysis.LogMLFeedback;
import software.wings.service.impl.analysis.LogMLFeedbackRecord;
import software.wings.service.impl.analysis.LogRequest;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskKeys;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask;
import software.wings.service.impl.splunk.FrequencyPattern;
import software.wings.service.impl.splunk.LogAnalysisResult;
import software.wings.service.impl.splunk.SplunkAnalysisCluster;
import software.wings.service.impl.splunk.SplunkAnalysisCluster.MessageFrequency;
import software.wings.service.impl.verification.CV24x7DashboardServiceImpl;
import software.wings.service.impl.verification.CVConfigurationServiceImpl;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.analysis.AnalysisService;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.verification.CV24x7DashboardService;
import software.wings.service.intfc.verification.CVConfigurationService;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateType;
import software.wings.sm.states.ElkAnalysisState;
import software.wings.verification.log.LogsCVConfiguration;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import dev.morphia.Datastore;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by rsingh on 9/27/17.
 */
@Slf4j
public class LogMLAnalysisServiceTest extends VerificationBase {
  private String accountId;
  private String appId;
  private String stateExecutionId;
  private String workflowId;
  private String workflowExecutionId;
  private String serviceId;
  private String delegateTaskId;
  private Random r;
  private LogsCVConfiguration logsCVConfiguration;

  @Mock private HarnessMetricRegistry metricRegistry;
  @Mock ContinuousVerificationService continuousVerificationService;
  @Inject private LogAnalysisService analysisService;
  @Inject private LearningEngineService learningEngineService;
  @Inject private WingsPersistence wingsPersistence;
  private DataStoreService dataStoreService;
  private CV24x7DashboardService cv24x7DashboardService;
  private AnalysisService managerAnalysisService;
  private CVConfigurationService cvConfigurationService;
  @Mock VerificationManagerClient verificationManagerClient;
  @Mock AppService appService;
  @Mock FeatureFlagService featureFlagService;

  @Before
  public void setup() throws IOException, IllegalAccessException {
    long seed = System.currentTimeMillis();
    log.info("random seed: " + seed);
    r = new Random(seed);
    accountId = UUID.randomUUID().toString();
    appId = UUID.randomUUID().toString();
    stateExecutionId = UUID.randomUUID().toString();
    workflowId = UUID.randomUUID().toString();
    workflowExecutionId = UUID.randomUUID().toString();
    serviceId = UUID.randomUUID().toString();
    delegateTaskId = UUID.randomUUID().toString();
    MockitoAnnotations.initMocks(this);

    Call<RestResponse<Boolean>> managerCall = mock(Call.class);
    when(managerCall.clone()).thenReturn(managerCall);
    when(managerCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(verificationManagerClient.isStateValid(appId, stateExecutionId)).thenReturn(managerCall);

    Call<RestResponse<Boolean>> managerCallFeedbacks = mock(Call.class);
    when(managerCallFeedbacks.clone()).thenReturn(managerCallFeedbacks);
    when(managerCallFeedbacks.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(verificationManagerClient.isFeatureEnabled(FeatureName.DISABLE_LOGML_NEURAL_NET, accountId))
        .thenReturn(managerCallFeedbacks);

    when(appService.getAccountIdByAppId(appId)).thenReturn(accountId);
    when(featureFlagService.isEnabled(FeatureName.DISABLE_LOGML_NEURAL_NET, accountId)).thenReturn(true);

    FieldUtils.writeDeclaredField(analysisService, "managerClient", verificationManagerClient, true);
    FieldUtils.writeDeclaredField(learningEngineService, "managerClient", verificationManagerClient, true);
    FieldUtils.writeDeclaredField(analysisService, "learningEngineService", learningEngineService, true);

    FieldUtils.writeField(analysisService, "managerClient", verificationManagerClient, true);

    dataStoreService = new MongoDataStoreServiceImpl(wingsPersistence);
    managerAnalysisService = new AnalysisServiceImpl();
    cv24x7DashboardService = new CV24x7DashboardServiceImpl();
    cvConfigurationService = new CVConfigurationServiceImpl();
    WorkflowExecutionService workflowExecutionService = new WorkflowExecutionServiceImpl();
    FieldUtils.writeField(workflowExecutionService, "wingsPersistence", wingsPersistence, true);
    FieldUtils.writeField(managerAnalysisService, "dataStoreService", dataStoreService, true);
    FieldUtils.writeField(managerAnalysisService, "appService", appService, true);
    FieldUtils.writeField(managerAnalysisService, "featureFlagService", featureFlagService, true);
    FieldUtils.writeField(analysisService, "dataStoreService", dataStoreService, true);
    FieldUtils.writeField(analysisService, "continuousVerificationService", continuousVerificationService, true);

    FieldUtils.writeField(managerAnalysisService, "wingsPersistence", wingsPersistence, true);
    FieldUtils.writeField(managerAnalysisService, "workflowExecutionService", workflowExecutionService, true);
    FieldUtils.writeField(managerAnalysisService, "metricRegistry", metricRegistry, true);
    FieldUtils.writeField(managerAnalysisService, "cv24x7DashboardService", cv24x7DashboardService, true);

    FieldUtils.writeField(cv24x7DashboardService, "wingsPersistence", wingsPersistence, true);
    FieldUtils.writeField(cv24x7DashboardService, "analysisService", managerAnalysisService, true);
    FieldUtils.writeField(cv24x7DashboardService, "cvConfigurationService", cvConfigurationService, true);

    FieldUtils.writeField(cvConfigurationService, "wingsPersistence", wingsPersistence, true);

    AnalysisContext context =
        AnalysisContext.builder().serviceId(serviceId).stateExecutionId(stateExecutionId).appId(appId).build();
    wingsPersistence.save(context);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void saveLogDataWithNoState() throws Exception {
    boolean status = analysisService.saveLogData(StateType.SPLUNKV2, accountId, appId, null, stateExecutionId,
        workflowId, workflowExecutionId, serviceId, ClusterLevel.L1, delegateTaskId,
        Collections.singletonList(new LogElement()));

    assertThat(status).isFalse();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void saveLogDataWithInvalidState() throws Exception {
    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setAppId(appId);
    stateExecutionInstance.setUuid(stateExecutionId);
    stateExecutionInstance.setStatus(ExecutionStatus.ABORTED);
    wingsPersistence.save(stateExecutionInstance);
    boolean status = analysisService.saveLogData(StateType.SPLUNKV2, accountId, appId, null, stateExecutionId,
        workflowId, workflowExecutionId, serviceId, ClusterLevel.L1, delegateTaskId,
        Collections.singletonList(new LogElement()));

    assertThat(status).isFalse();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void saveLogDataNoHeartbeat() throws Exception {
    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setAppId(appId);
    stateExecutionInstance.setUuid(stateExecutionId);
    stateExecutionInstance.setStatus(ExecutionStatus.RUNNING);
    wingsPersistence.save(stateExecutionInstance);
    boolean status = analysisService.saveLogData(StateType.SPLUNKV2, accountId, appId, null, stateExecutionId,
        workflowId, workflowExecutionId, serviceId, ClusterLevel.L1, delegateTaskId,
        Collections.singletonList(new LogElement()));

    assertThat(status).isFalse();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)

  @Ignore("TODO: Legacy test failures")
  public void saveLogDataValid() throws Exception {
    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setAppId(appId);
    stateExecutionInstance.setUuid(stateExecutionId);
    stateExecutionInstance.setStatus(ExecutionStatus.RUNNING);
    wingsPersistence.save(stateExecutionInstance);

    List<LogElement> logElements = new ArrayList<>();

    String query = UUID.randomUUID().toString();
    String host = UUID.randomUUID().toString();
    final int logCollectionMinute = 3;
    LogElement splunkHeartBeatElement = new LogElement();
    splunkHeartBeatElement.setQuery(query);
    splunkHeartBeatElement.setClusterLabel("-3");
    splunkHeartBeatElement.setHost(host);
    splunkHeartBeatElement.setCount(0);
    splunkHeartBeatElement.setLogMessage("");
    splunkHeartBeatElement.setTimeStamp(0);
    splunkHeartBeatElement.setLogCollectionMinute(logCollectionMinute);

    logElements.add(splunkHeartBeatElement);

    LogElement logElement = new LogElement(query, "0", host, 0, 0, UUID.randomUUID().toString(), logCollectionMinute);
    logElements.add(logElement);

    LogRequest logRequest = new LogRequest(
        query, appId, stateExecutionId, workflowId, serviceId, Collections.singleton(host), logCollectionMinute, false);

    Set<LogDataRecord> logDataRecords = analysisService.getLogData(
        logRequest, true, workflowExecutionId, ClusterLevel.L1, StateType.SPLUNKV2, accountId);
    assertThat(logDataRecords.isEmpty()).isTrue();

    boolean status = analysisService.saveLogData(StateType.SPLUNKV2, accountId, appId, null, stateExecutionId,
        workflowId, workflowExecutionId, serviceId, ClusterLevel.L1, delegateTaskId, logElements);

    assertThat(status).isTrue();

    logDataRecords = analysisService.getLogData(
        logRequest, true, workflowExecutionId, ClusterLevel.L1, StateType.SPLUNKV2, accountId);
    assertThat(logDataRecords).hasSize(1);
    LogDataRecord logDataRecord = logDataRecords.iterator().next();
    assertThat(logDataRecord.getLogMessage()).isEqualTo(logElement.getLogMessage());
    assertThat(logDataRecord.getQuery()).isEqualTo(logElement.getQuery());
    assertThat(logDataRecord.getClusterLabel()).isEqualTo(logElement.getClusterLabel());
    assertThat(logDataRecord.getClusterLevel()).isEqualTo(ClusterLevel.L1);
    assertThat(logDataRecord.getLogCollectionMinute()).isEqualTo(logElement.getLogCollectionMinute());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)

  @Ignore("TODO: Legacy test failures")
  public void getLogDataNoPreviousAnalysis() throws Exception {
    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    assertThat(logMLAnalysisRecord).isNotNull();
    logMLAnalysisRecord.setWorkflowExecutionId(generateUuid());
    wingsPersistence.save(logMLAnalysisRecord);

    LogRequest logRequest =
        new LogRequest(generateUuid(), appId, stateExecutionId, workflowId, serviceId, null, -1, false);

    Set<LogDataRecord> logData = analysisService.getLogData(
        logRequest, false, workflowExecutionId, ClusterLevel.L1, StateType.SPLUNKV2, accountId);
    assertThat(logData).isEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)

  @Ignore("TODO: Legacy test failures")
  public void getLogDataPreviousAnalysis() throws Exception {
    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    assertThat(logMLAnalysisRecord).isNotNull();
    logMLAnalysisRecord.setBaseLineCreated(true);
    logMLAnalysisRecord.setWorkflowExecutionId(workflowExecutionId);
    wingsPersistence.save(logMLAnalysisRecord);

    LogRequest logRequest =
        new LogRequest(generateUuid(), appId, stateExecutionId, workflowId, serviceId, null, -1, false);

    Set<LogDataRecord> logData = analysisService.getLogData(
        logRequest, false, workflowExecutionId, ClusterLevel.L1, StateType.SPLUNKV2, accountId);
    assertThat(logData.isEmpty()).isFalse();
    Set<LogDataRecord> expectedLogs = new HashSet<>();
    logMLAnalysisRecord.getTest_events().forEach((s, analysisClusters) -> {
      analysisClusters.forEach(analysisCluster -> {
        MessageFrequency messageFrequency = analysisCluster.getMessage_frequencies().get(0);
        expectedLogs.add(LogDataRecord.builder()
                             .stateType(logMLAnalysisRecord.getStateType())
                             .workflowExecutionId(logMLAnalysisRecord.getWorkflowExecutionId())
                             .stateExecutionId(logMLAnalysisRecord.getStateExecutionId())
                             .query(logMLAnalysisRecord.getQuery())
                             .clusterLabel(Integer.toString(analysisCluster.getCluster_label()))
                             .host(messageFrequency.getHost())
                             .timeStamp(messageFrequency.getTime())
                             .logMessage(analysisCluster.getText())
                             .count(messageFrequency.getCount())
                             .build());
      });
    });

    assertThat(logData).isEqualTo(expectedLogs);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)

  @Ignore("TODO: Legacy test failures")
  public void getLogDataPreviousAnalysis_noPreviousBaseline() throws Exception {
    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    assertThat(logMLAnalysisRecord).isNotNull();
    logMLAnalysisRecord.setBaseLineCreated(false);
    logMLAnalysisRecord.setControl_events(logMLAnalysisRecord.getTest_events());
    logMLAnalysisRecord.setTest_events(null);
    logMLAnalysisRecord.setWorkflowExecutionId(workflowExecutionId);
    wingsPersistence.save(logMLAnalysisRecord);

    LogRequest logRequest =
        new LogRequest(generateUuid(), appId, stateExecutionId, workflowId, serviceId, null, -1, false);

    Set<LogDataRecord> logData = analysisService.getLogData(
        logRequest, false, workflowExecutionId, ClusterLevel.L1, StateType.SPLUNKV2, accountId);
    assertThat(logData.isEmpty()).isFalse();
    Set<LogDataRecord> expectedLogs = new HashSet<>();
    logMLAnalysisRecord.getControl_events().forEach((s, analysisClusters) -> {
      analysisClusters.forEach(analysisCluster -> {
        MessageFrequency messageFrequency = analysisCluster.getMessage_frequencies().get(0);
        expectedLogs.add(LogDataRecord.builder()
                             .stateType(logMLAnalysisRecord.getStateType())
                             .workflowExecutionId(logMLAnalysisRecord.getWorkflowExecutionId())
                             .stateExecutionId(logMLAnalysisRecord.getStateExecutionId())
                             .query(logMLAnalysisRecord.getQuery())
                             .clusterLabel(Integer.toString(analysisCluster.getCluster_label()))
                             .host(messageFrequency.getHost())
                             .timeStamp(messageFrequency.getTime())
                             .logMessage(analysisCluster.getText())
                             .count(messageFrequency.getCount())
                             .build());
      });
    });

    assertThat(logData).isEqualTo(expectedLogs);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)

  @Ignore("TODO: Legacy test failures")
  public void getLogDataPreviousAnalysis_hasPreviousBaselineNoData() throws Exception {
    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    assertThat(logMLAnalysisRecord).isNotNull();
    logMLAnalysisRecord.setBaseLineCreated(true);
    logMLAnalysisRecord.setControl_events(logMLAnalysisRecord.getTest_events());
    logMLAnalysisRecord.setTest_events(null);
    logMLAnalysisRecord.setWorkflowExecutionId(workflowExecutionId);
    wingsPersistence.save(logMLAnalysisRecord);

    LogRequest logRequest =
        new LogRequest(generateUuid(), appId, stateExecutionId, workflowId, serviceId, null, -1, false);

    Set<LogDataRecord> logData = analysisService.getLogData(
        logRequest, false, workflowExecutionId, ClusterLevel.L1, StateType.SPLUNKV2, accountId);
    assertThat(logData).isEmpty();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)

  @Ignore("TODO: Legacy test failures")
  public void getLogDataPreviousAnalysis_noPreviousBaselineNoControlData() throws Exception {
    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    assertThat(logMLAnalysisRecord).isNotNull();
    logMLAnalysisRecord.setBaseLineCreated(false);
    logMLAnalysisRecord.setControl_events(null);
    logMLAnalysisRecord.setTest_events(null);
    logMLAnalysisRecord.setWorkflowExecutionId(workflowExecutionId);
    wingsPersistence.save(logMLAnalysisRecord);

    LogRequest logRequest =
        new LogRequest(generateUuid(), appId, stateExecutionId, workflowId, serviceId, null, -1, false);

    Set<LogDataRecord> logData = analysisService.getLogData(
        logRequest, false, workflowExecutionId, ClusterLevel.L1, StateType.SPLUNKV2, accountId);
    assertThat(logData).isEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)

  @Ignore("TODO: Legacy test failures")
  public void getLogDataSuccessfulWorkflowExecution() throws Exception {
    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setAppId(appId);
    stateExecutionInstance.setUuid(stateExecutionId);
    stateExecutionInstance.setStatus(ExecutionStatus.RUNNING);
    wingsPersistence.save(stateExecutionInstance);

    WorkflowExecution workflowExecution = WorkflowExecution.builder().build();
    workflowExecution.setAppId(appId);
    workflowExecution.setUuid(workflowExecutionId);
    workflowExecution.setWorkflowId(workflowId);
    workflowExecution.setStatus(ExecutionStatus.SUCCESS);
    wingsPersistence.save(workflowExecution);

    List<LogElement> logElements = new ArrayList<>();

    String query = UUID.randomUUID().toString();
    String host = UUID.randomUUID().toString();
    final int logCollectionMinute = 3;
    LogElement splunkHeartBeatElement = new LogElement();
    splunkHeartBeatElement.setQuery(query);
    splunkHeartBeatElement.setClusterLabel("-3");
    splunkHeartBeatElement.setHost(host);
    splunkHeartBeatElement.setCount(0);
    splunkHeartBeatElement.setLogMessage("");
    splunkHeartBeatElement.setTimeStamp(0);
    splunkHeartBeatElement.setLogCollectionMinute(logCollectionMinute);

    logElements.add(splunkHeartBeatElement);

    LogElement logElement = new LogElement(query, "0", host, 0, 0, UUID.randomUUID().toString(), logCollectionMinute);
    logElements.add(logElement);

    LogRequest logRequest = new LogRequest(
        query, appId, stateExecutionId, workflowId, serviceId, Collections.singleton(host), logCollectionMinute, false);

    boolean status = analysisService.saveLogData(StateType.SPLUNKV2, accountId, appId, null, stateExecutionId,
        workflowId, workflowExecutionId, serviceId, ClusterLevel.L1, delegateTaskId, logElements);

    assertThat(status).isTrue();

    Set<LogDataRecord> logDataRecords = analysisService.getLogData(
        logRequest, true, workflowExecutionId, ClusterLevel.L1, StateType.SPLUNKV2, accountId);
    assertThat(logDataRecords).hasSize(1);
    LogDataRecord logDataRecord = logDataRecords.iterator().next();
    assertThat(logDataRecord.getLogMessage()).isEqualTo(logElement.getLogMessage());
    assertThat(logDataRecord.getQuery()).isEqualTo(logElement.getQuery());
    assertThat(logDataRecord.getClusterLabel()).isEqualTo(logElement.getClusterLabel());
    assertThat(logDataRecord.getClusterLevel()).isEqualTo(ClusterLevel.L1);
    assertThat(logDataRecord.getLogCollectionMinute()).isEqualTo(logElement.getLogCollectionMinute());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)

  @Ignore("TODO: Legacy test failures")
  public void testBumpClusterLevel() throws Exception {
    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setAppId(appId);
    stateExecutionInstance.setUuid(stateExecutionId);
    stateExecutionInstance.setStatus(ExecutionStatus.RUNNING);
    wingsPersistence.save(stateExecutionInstance);

    List<LogElement> logElements = new ArrayList<>();

    String query = UUID.randomUUID().toString();
    String host = UUID.randomUUID().toString();
    final int logCollectionMinute = 3;
    LogElement splunkHeartBeatElement = new LogElement();
    splunkHeartBeatElement.setQuery(query);
    splunkHeartBeatElement.setClusterLabel("-3");
    splunkHeartBeatElement.setHost(host);
    splunkHeartBeatElement.setCount(0);
    splunkHeartBeatElement.setLogMessage("");
    splunkHeartBeatElement.setTimeStamp(0);
    splunkHeartBeatElement.setLogCollectionMinute(logCollectionMinute);

    logElements.add(splunkHeartBeatElement);

    LogElement logElement = new LogElement(query, "0", host, 0, 0, UUID.randomUUID().toString(), logCollectionMinute);
    logElements.add(logElement);

    boolean status = analysisService.saveLogData(StateType.SPLUNKV2, accountId, appId, null, stateExecutionId,
        workflowId, workflowExecutionId, serviceId, ClusterLevel.L1, delegateTaskId, logElements);

    assertThat(status).isTrue();

    LogRequest logRequest = new LogRequest(
        query, appId, stateExecutionId, workflowId, serviceId, Collections.singleton(host), logCollectionMinute, false);
    Set<LogDataRecord> logDataRecords = analysisService.getLogData(
        logRequest, true, workflowExecutionId, ClusterLevel.L1, StateType.SPLUNKV2, accountId);
    assertThat(logDataRecords).hasSize(1);
    LogDataRecord logDataRecord = logDataRecords.iterator().next();
    assertThat(logDataRecord.getLogMessage()).isEqualTo(logElement.getLogMessage());
    assertThat(logDataRecord.getQuery()).isEqualTo(logElement.getQuery());
    assertThat(logDataRecord.getClusterLabel()).isEqualTo(logElement.getClusterLabel());
    assertThat(logDataRecord.getClusterLevel()).isEqualTo(ClusterLevel.L1);
    assertThat(logDataRecord.getLogCollectionMinute()).isEqualTo(logElement.getLogCollectionMinute());

    analysisService.bumpClusterLevel(StateType.SPLUNKV2, stateExecutionId, appId, query, Collections.singleton(host),
        logCollectionMinute, ClusterLevel.L1, ClusterLevel.L2);

    logDataRecords = analysisService.getLogData(
        logRequest, true, workflowExecutionId, ClusterLevel.L1, StateType.SPLUNKV2, accountId);
    assertThat(logDataRecords.isEmpty()).isTrue();

    logDataRecords = analysisService.getLogData(
        logRequest, true, workflowExecutionId, ClusterLevel.L2, StateType.SPLUNKV2, accountId);
    assertThat(logDataRecords).hasSize(1);
    logDataRecord = logDataRecords.iterator().next();
    assertThat(logDataRecord.getLogMessage()).isEqualTo(logElement.getLogMessage());
    assertThat(logDataRecord.getQuery()).isEqualTo(logElement.getQuery());
    assertThat(logDataRecord.getClusterLabel()).isEqualTo(logElement.getClusterLabel());
    assertThat(logDataRecord.getClusterLevel()).isEqualTo(ClusterLevel.L2);
    assertThat(logDataRecord.getLogCollectionMinute()).isEqualTo(logElement.getLogCollectionMinute());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)

  @Ignore("TODO: Legacy test failures")
  public void testIsLogDataCollected() throws Exception {
    String query = UUID.randomUUID().toString();
    String host = UUID.randomUUID().toString();
    final int logCollectionMinute = 3;

    assertThat(
        analysisService.isLogDataCollected(appId, stateExecutionId, query, logCollectionMinute, StateType.SPLUNKV2))
        .isFalse();

    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setAppId(appId);
    stateExecutionInstance.setUuid(stateExecutionId);
    stateExecutionInstance.setStatus(ExecutionStatus.RUNNING);
    wingsPersistence.save(stateExecutionInstance);

    List<LogElement> logElements = new ArrayList<>();

    LogElement splunkHeartBeatElement = new LogElement();
    splunkHeartBeatElement.setQuery(query);
    splunkHeartBeatElement.setClusterLabel("-3");
    splunkHeartBeatElement.setHost(host);
    splunkHeartBeatElement.setCount(0);
    splunkHeartBeatElement.setLogMessage("");
    splunkHeartBeatElement.setTimeStamp(0);
    splunkHeartBeatElement.setLogCollectionMinute(logCollectionMinute);

    logElements.add(splunkHeartBeatElement);

    LogElement logElement = new LogElement(query, "0", host, 0, 0, UUID.randomUUID().toString(), logCollectionMinute);
    logElements.add(logElement);

    analysisService.saveLogData(StateType.SPLUNKV2, accountId, appId, null, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, ClusterLevel.L1, delegateTaskId, logElements);

    assertThat(
        analysisService.isLogDataCollected(appId, stateExecutionId, query, logCollectionMinute, StateType.SPLUNKV2))
        .isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldSaveLogCollectionMinuteMinusOne() throws Exception {
    int numOfUnknownClusters = 2 + r.nextInt(10);
    List<SplunkAnalysisCluster> clusterEvents = new ArrayList<>();
    Map<String, Map<String, SplunkAnalysisCluster>> unknownClusters = new HashMap<>();
    Set<String> hosts = new HashSet<>();
    for (int i = 0; i < numOfUnknownClusters; i++) {
      SplunkAnalysisCluster cluster = getRandomClusterEvent();
      clusterEvents.add(cluster);
      Map<String, SplunkAnalysisCluster> hostMap = new HashMap<>();
      String host = UUID.randomUUID().toString() + ".harness.com";
      hostMap.put(host, cluster);
      hosts.add(host);
      unknownClusters.put(UUID.randomUUID().toString(), hostMap);
    }

    LogMLAnalysisRecord record = new LogMLAnalysisRecord();
    record.setStateExecutionId(stateExecutionId);
    record.setAppId(appId);
    record.setStateType(StateType.SPLUNKV2);
    record.setLogCollectionMinute(-1);
    record.setAnalysisSummaryMessage("This is a -1 test");
    record.setQuery(UUID.randomUUID().toString());
    analysisService.saveLogAnalysisRecords(record, StateType.SPLUNKV2, Optional.empty(), Optional.empty());

    LogMLAnalysisSummary analysisSummary =
        managerAnalysisService.getAnalysisSummary(stateExecutionId, appId, StateType.SPLUNKV2);
    assertThat(analysisSummary).isNotNull();
    assertThat(analysisSummary.getAnalysisSummaryMessage()).isEqualTo("This is a -1 test");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void shouldNotSaveEmptyControlAndTestEvents() throws Exception {
    int numOfUnknownClusters = 2 + r.nextInt(10);
    List<SplunkAnalysisCluster> clusterEvents = new ArrayList<>();
    Map<String, Map<String, SplunkAnalysisCluster>> unknownClusters = new HashMap<>();
    Set<String> hosts = new HashSet<>();
    for (int i = 0; i < numOfUnknownClusters; i++) {
      SplunkAnalysisCluster cluster = getRandomClusterEvent();
      clusterEvents.add(cluster);
      Map<String, SplunkAnalysisCluster> hostMap = new HashMap<>();
      String host = UUID.randomUUID().toString() + ".harness.com";
      hostMap.put(host, cluster);
      hosts.add(host);
      unknownClusters.put(UUID.randomUUID().toString(), hostMap);
    }

    LogMLAnalysisRecord record = new LogMLAnalysisRecord();
    record.setStateExecutionId(stateExecutionId);
    record.setAppId(appId);
    record.setStateType(StateType.SPLUNKV2);
    record.setLogCollectionMinute(0);
    record.setAnalysisSummaryMessage("This is a -1 test");
    record.setQuery(UUID.randomUUID().toString());
    analysisService.saveLogAnalysisRecords(record, StateType.SPLUNKV2, Optional.empty(), Optional.empty());

    LogMLAnalysisSummary analysisSummary =
        managerAnalysisService.getAnalysisSummary(stateExecutionId, appId, StateType.SPLUNKV2);
    assertThat(analysisSummary).isNull();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAnalysisSummaryUnknownClusters() throws Exception {
    int numOfUnknownClusters = 2 + r.nextInt(10);
    List<SplunkAnalysisCluster> clusterEvents = new ArrayList<>();
    Map<String, Map<String, SplunkAnalysisCluster>> unknownClusters = new HashMap<>();
    Map<String, List<SplunkAnalysisCluster>> controlEvents = new HashMap<>();
    controlEvents.put("xyz", Lists.newArrayList(getRandomClusterEvent()));
    Set<String> hosts = new HashSet<>();
    for (int i = 0; i < numOfUnknownClusters; i++) {
      SplunkAnalysisCluster cluster = getRandomClusterEvent();
      clusterEvents.add(cluster);
      Map<String, SplunkAnalysisCluster> hostMap = new HashMap<>();
      String host = UUID.randomUUID().toString() + ".harness.com";
      hostMap.put(host, cluster);
      hosts.add(host);
      unknownClusters.put(UUID.randomUUID().toString(), hostMap);
    }

    LogMLAnalysisRecord record = new LogMLAnalysisRecord();
    record.setStateExecutionId(stateExecutionId);
    record.setAppId(appId);
    record.setStateType(StateType.SPLUNKV2);
    record.setLogCollectionMinute(0);
    record.setQuery(UUID.randomUUID().toString());
    record.setControl_events(controlEvents);
    record.setUnknown_clusters(unknownClusters);
    analysisService.saveLogAnalysisRecords(record, StateType.SPLUNKV2, Optional.empty(), Optional.empty());

    LogMLAnalysisSummary analysisSummary =
        managerAnalysisService.getAnalysisSummary(stateExecutionId, appId, StateType.SPLUNKV2);
    assertThat(analysisSummary).isNotNull();
    assertThat(analysisSummary.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
    assertThat(analysisSummary.getUnknownClusters()).hasSize(numOfUnknownClusters);
    assertThat(analysisSummary.getTestClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getControlClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getAnalysisSummaryMessage())
        .isEqualTo(numOfUnknownClusters + " anomalous clusters found");
    for (LogMLClusterSummary logMLClusterSummary : analysisSummary.getUnknownClusters()) {
      for (String hostname : logMLClusterSummary.getHostSummary().keySet()) {
        assert hosts.contains(hostname);
        hosts.remove(hostname);
      }
    }
    assert hosts.isEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAnalysisSummaryCompression() throws Exception {
    ArrayList<List<SplunkAnalysisCluster>> unknownEvents = Lists.newArrayList(getEvents(1 + r.nextInt(10)).values());
    Map<String, List<SplunkAnalysisCluster>> testEvents = getEvents(1 + r.nextInt(10));
    Map<String, List<SplunkAnalysisCluster>> controlEvents = getEvents(1 + r.nextInt(10));

    Map<String, Map<String, SplunkAnalysisCluster>> controlClusters = createClusters(1 + r.nextInt(10));
    Map<String, Map<String, SplunkAnalysisCluster>> unknownClusters = createClusters(1 + r.nextInt(10));
    Map<String, Map<String, SplunkAnalysisCluster>> testClusters = createClusters(1 + r.nextInt(10));
    Map<String, Map<String, SplunkAnalysisCluster>> ignoreClusters = createClusters(1 + r.nextInt(10));
    LogMLAnalysisRecord record = new LogMLAnalysisRecord();
    record.setStateExecutionId(stateExecutionId);
    record.setAppId(appId);
    record.setStateType(StateType.SPLUNKV2);
    record.setLogCollectionMinute(0);
    record.setQuery(UUID.randomUUID().toString());
    record.setUnknown_events(unknownEvents);
    record.setTest_events(testEvents);
    record.setControl_events(controlEvents);
    record.setControl_clusters(controlClusters);
    record.setUnknown_clusters(unknownClusters);
    record.setTest_clusters(testClusters);
    record.setIgnore_clusters(ignoreClusters);

    assertThat(analysisService.saveLogAnalysisRecords(record, StateType.SPLUNKV2, Optional.empty(), Optional.empty()))
        .isTrue();

    LogMLAnalysisRecord logMLAnalysisRecord = wingsPersistence.createQuery(LogMLAnalysisRecord.class)
                                                  .filter("appId", appId)
                                                  .filter(LogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId)
                                                  .get();
    assertThat(logMLAnalysisRecord).isNotNull();
    assertThat(logMLAnalysisRecord.getUnknown_events()).isNull();
    assertThat(logMLAnalysisRecord.getTest_events()).isNull();
    assertThat(logMLAnalysisRecord.getControl_events()).isNull();
    assertThat(logMLAnalysisRecord.getControl_clusters()).isNull();
    assertThat(logMLAnalysisRecord.getUnknown_clusters()).isNull();
    assertThat(logMLAnalysisRecord.getTest_clusters()).isNull();
    assertThat(logMLAnalysisRecord.getIgnore_clusters()).isNull();
    assertThat(isNotEmpty(logMLAnalysisRecord.getProtoSerializedAnalyisDetails())).isTrue();
    assertThat(logMLAnalysisRecord.getAnalysisDetailsCompressedJson()).isNull();

    LogMLAnalysisRecord logAnalysisRecord = analysisService.getLogAnalysisRecords(
        LogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId, record.getLogCollectionMinute(), false);

    assertThat(logAnalysisRecord.getUnknown_events()).isEqualTo(unknownEvents);
    assertThat(logAnalysisRecord.getTest_events()).isEqualTo(testEvents);
    assertThat(logAnalysisRecord.getControl_events()).isEqualTo(controlEvents);
    assertThat(logAnalysisRecord.getControl_clusters()).isEqualTo(controlClusters);
    assertThat(logAnalysisRecord.getUnknown_clusters()).isEqualTo(unknownClusters);
    assertThat(logAnalysisRecord.getTest_clusters()).isEqualTo(testClusters);
    assertThat(logAnalysisRecord.getIgnore_clusters()).isEqualTo(ignoreClusters);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAnalysisSummaryTestClusters() throws Exception {
    int numOfTestClusters = 1 + r.nextInt(10);
    List<SplunkAnalysisCluster> clusterEvents = new ArrayList<>();
    Map<String, Map<String, SplunkAnalysisCluster>> testClusters = new HashMap<>();
    Set<String> hosts = new HashSet<>();
    Map<String, List<SplunkAnalysisCluster>> controlEvents = new HashMap<>();
    controlEvents.put("xyz", Lists.newArrayList(getRandomClusterEvent()));
    for (int i = 0; i < numOfTestClusters; i++) {
      SplunkAnalysisCluster cluster = getRandomClusterEvent();
      clusterEvents.add(cluster);
      Map<String, SplunkAnalysisCluster> hostMap = new HashMap<>();
      String host = UUID.randomUUID().toString() + ".harness.com";
      hostMap.put(host, cluster);
      hosts.add(host);
      testClusters.put(UUID.randomUUID().toString(), hostMap);
    }

    LogMLAnalysisRecord record = new LogMLAnalysisRecord();
    record.setStateExecutionId(stateExecutionId);
    record.setAppId(appId);
    record.setStateType(StateType.SPLUNKV2);
    record.setLogCollectionMinute(0);
    record.setQuery(UUID.randomUUID().toString());
    record.setControl_events(controlEvents);
    record.setTest_clusters(testClusters);
    analysisService.saveLogAnalysisRecords(record, StateType.SPLUNKV2, Optional.empty(), Optional.empty());

    int numOfUnexpectedFreq = 0;
    for (SplunkAnalysisCluster cluster : clusterEvents) {
      if (cluster.isUnexpected_freq()) {
        numOfUnexpectedFreq++;
      }
    }
    LogMLAnalysisSummary analysisSummary =
        managerAnalysisService.getAnalysisSummary(stateExecutionId, appId, StateType.SPLUNKV2);
    assertThat(analysisSummary).isNotNull();
    assertThat(analysisSummary.getRiskLevel()).isEqualTo(numOfUnexpectedFreq > 0 ? RiskLevel.HIGH : RiskLevel.NA);
    assertThat(analysisSummary.getTestClusters()).hasSize(numOfTestClusters);
    assertThat(analysisSummary.getUnknownClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getControlClusters().isEmpty()).isTrue();
    String message;
    if (numOfUnexpectedFreq == 0) {
      message = "No baseline data for the given query was found.";
    } else if (numOfUnexpectedFreq == 1) {
      message = numOfUnexpectedFreq + " anomalous cluster found";
    } else {
      message = numOfUnexpectedFreq + " anomalous clusters found";
    }
    assertThat(analysisSummary.getAnalysisSummaryMessage()).isEqualTo(message);

    for (LogMLClusterSummary logMLClusterSummary : analysisSummary.getTestClusters()) {
      for (String hostname : logMLClusterSummary.getHostSummary().keySet()) {
        assert hosts.contains(hostname);
        hosts.remove(hostname);
      }
    }
    assert hosts.isEmpty();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAnalysisSummaryControlClusters() throws Exception {
    int numOfControlClusters = 1 + r.nextInt(10);
    List<SplunkAnalysisCluster> clusterEvents = new ArrayList<>();
    Map<String, Map<String, SplunkAnalysisCluster>> controlClusters = new HashMap<>();
    Set<String> hosts = new HashSet<>();
    Map<String, List<SplunkAnalysisCluster>> controlEvents = new HashMap<>();
    controlEvents.put("xyz", Lists.newArrayList(getRandomClusterEvent()));
    for (int i = 0; i < numOfControlClusters; i++) {
      SplunkAnalysisCluster cluster = getRandomClusterEvent();
      clusterEvents.add(cluster);
      Map<String, SplunkAnalysisCluster> hostMap = new HashMap<>();
      String host = UUID.randomUUID().toString() + ".harness.com";
      hostMap.put(host, cluster);
      hosts.add(host);
      controlClusters.put(UUID.randomUUID().toString(), hostMap);
    }

    LogMLAnalysisRecord record = new LogMLAnalysisRecord();
    record.setStateExecutionId(stateExecutionId);
    record.setAppId(appId);
    record.setStateType(StateType.SPLUNKV2);
    record.setLogCollectionMinute(0);
    record.setQuery(UUID.randomUUID().toString());
    record.setControl_clusters(controlClusters);
    record.setControl_events(controlEvents);
    analysisService.saveLogAnalysisRecords(record, StateType.SPLUNKV2, Optional.empty(), Optional.empty());
    LogMLAnalysisRecord logMLAnalysisRecord = wingsPersistence.createQuery(LogMLAnalysisRecord.class)
                                                  .filter("appId", appId)
                                                  .filter(LogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId)
                                                  .get();
    assertThat(logMLAnalysisRecord).isNotNull();
    assertThat(logMLAnalysisRecord.getUnknown_events()).isNull();
    assertThat(logMLAnalysisRecord.getTest_events()).isNull();
    assertThat(logMLAnalysisRecord.getControl_events()).isNull();
    assertThat(logMLAnalysisRecord.getControl_clusters()).isNull();
    assertThat(logMLAnalysisRecord.getUnknown_clusters()).isNull();
    assertThat(logMLAnalysisRecord.getTest_clusters()).isNull();
    assertThat(logMLAnalysisRecord.getIgnore_clusters()).isNull();

    LogMLAnalysisSummary analysisSummary =
        managerAnalysisService.getAnalysisSummary(stateExecutionId, appId, StateType.SPLUNKV2);
    assertThat(analysisSummary).isNotNull();
    assertThat(analysisSummary.getRiskLevel()).isEqualTo(RiskLevel.NA);
    assertThat(analysisSummary.getControlClusters()).hasSize(numOfControlClusters);
    assertThat(analysisSummary.getUnknownClusters().isEmpty()).isTrue();
    assertThat(analysisSummary.getTestClusters().isEmpty()).isTrue();
    String message = "No new data for the given queries. Showing baseline data if any.";

    assertThat(analysisSummary.getAnalysisSummaryMessage()).isEqualTo(message);

    for (LogMLClusterSummary logMLClusterSummary : analysisSummary.getControlClusters()) {
      for (String hostname : logMLClusterSummary.getHostSummary().keySet()) {
        assert hosts.contains(hostname);
        hosts.remove(hostname);
      }
    }
    assert hosts.isEmpty();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void getCollectionMinuteForL1NoRecords() throws Exception {
    assertThat(analysisService.getCollectionMinuteForLevel(UUID.randomUUID().toString(), appId, stateExecutionId,
                   StateType.SPLUNKV2, ClusterLevel.L1, Collections.emptySet()))
        .isEqualTo(-1);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void getCollectionMinuteForL1PartialRecords() throws Exception {
    String query = UUID.randomUUID().toString();
    int numOfHosts = 2 + r.nextInt(10);
    int logCollectionMinute = 1 + r.nextInt(10);

    List<LogDataRecord> logDataRecords = new ArrayList<>();
    Set<String> hosts = new HashSet<>();
    for (int i = 0; i < numOfHosts; i++) {
      String host = UUID.randomUUID().toString();
      hosts.add(host);

      LogDataRecord logDataRecord = new LogDataRecord();
      logDataRecord.setAppId(appId);
      logDataRecord.setStateExecutionId(stateExecutionId);
      logDataRecord.setStateType(StateType.SPLUNKV2);
      logDataRecord.setClusterLevel(ClusterLevel.H1);
      logDataRecord.setQuery(query);
      logDataRecord.setLogCollectionMinute(logCollectionMinute);
      logDataRecord.setLogMessage(UUID.randomUUID().toString());
      logDataRecord.setHost(host);

      logDataRecords.add(logDataRecord);
    }

    // save all but one record

    for (int i = 1; i < numOfHosts; i++) {
      wingsPersistence.save(logDataRecords.get(i));
    }

    assertThat(analysisService.getCollectionMinuteForLevel(
                   query, appId, stateExecutionId, StateType.SPLUNKV2, ClusterLevel.L1, hosts))
        .isEqualTo(-1);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void getCollectionMinuteForL1AllRecords() throws Exception {
    String query = UUID.randomUUID().toString();
    int numOfHosts = 1 + r.nextInt(10);
    int logCollectionMinute = 1 + r.nextInt(10);

    Set<LogDataRecord> logDataRecords = new HashSet<>();
    Set<String> hosts = new HashSet<>();
    for (int i = 0; i < numOfHosts; i++) {
      String host = UUID.randomUUID().toString();
      hosts.add(host);

      LogDataRecord logDataRecord = new LogDataRecord();
      logDataRecord.setAppId(appId);
      logDataRecord.setStateExecutionId(stateExecutionId);
      logDataRecord.setStateType(StateType.SPLUNKV2);
      logDataRecord.setClusterLevel(ClusterLevel.H1);
      logDataRecord.setQuery(query);
      logDataRecord.setLogCollectionMinute(logCollectionMinute);
      logDataRecord.setLogMessage(UUID.randomUUID().toString());
      logDataRecord.setHost(host);

      logDataRecords.add(logDataRecord);
    }

    wingsPersistence.save(Lists.newArrayList(logDataRecords));

    assertThat(analysisService.getCollectionMinuteForLevel(
                   query, appId, stateExecutionId, StateType.SPLUNKV2, ClusterLevel.L1, hosts))
        .isEqualTo(logCollectionMinute);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)

  @Ignore("TODO: Legacy test failures")
  public void hasDataRecords() throws Exception {
    String query = UUID.randomUUID().toString();
    assertThat(analysisService.hasDataRecords(query, appId, stateExecutionId, StateType.SPLUNKV2,
                   Collections.singleton("some-host"), ClusterLevel.L1, 0))
        .isFalse();
    int numOfHosts = 1 + r.nextInt(10);
    int logCollectionMinute = 1 + r.nextInt(10);

    Set<LogDataRecord> logDataRecords = new HashSet<>();
    Set<String> hosts = new HashSet<>();
    for (int i = 0; i < numOfHosts; i++) {
      String host = UUID.randomUUID().toString();
      hosts.add(host);

      LogDataRecord logDataRecord = new LogDataRecord();
      logDataRecord.setAppId(appId);
      logDataRecord.setStateExecutionId(stateExecutionId);
      logDataRecord.setStateType(StateType.SPLUNKV2);
      logDataRecord.setClusterLevel(ClusterLevel.L1);
      logDataRecord.setQuery(query);
      logDataRecord.setLogCollectionMinute(logCollectionMinute);
      logDataRecord.setLogMessage(UUID.randomUUID().toString());
      logDataRecord.setHost(host);

      logDataRecords.add(logDataRecord);
    }

    wingsPersistence.save(Lists.newArrayList(logDataRecords));
    assertThat(analysisService.hasDataRecords(
                   query, appId, stateExecutionId, StateType.SPLUNKV2, hosts, ClusterLevel.L1, logCollectionMinute))
        .isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void getLogDataRecordForL0() throws Exception {
    String query = UUID.randomUUID().toString();
    assertThat(analysisService.getHearbeatRecordForL0(appId, stateExecutionId, StateType.SPLUNKV2, null).isPresent())
        .isFalse();
    int numOfHosts = 1 + r.nextInt(10);
    int logCollectionMinute = 1 + r.nextInt(10);

    Set<LogDataRecord> logDataRecords = new HashSet<>();
    Set<String> hosts = new HashSet<>();
    for (int i = 0; i < numOfHosts; i++) {
      String host = UUID.randomUUID().toString();
      hosts.add(host);

      LogDataRecord logDataRecord = new LogDataRecord();
      logDataRecord.setAppId(appId);
      logDataRecord.setStateExecutionId(stateExecutionId);
      logDataRecord.setStateType(StateType.SPLUNKV2);
      logDataRecord.setClusterLevel(ClusterLevel.H0);
      logDataRecord.setQuery(query);
      logDataRecord.setLogCollectionMinute(logCollectionMinute);
      logDataRecord.setLogMessage(UUID.randomUUID().toString());
      logDataRecord.setHost(host);

      logDataRecords.add(logDataRecord);
    }

    wingsPersistence.save(Lists.newArrayList(logDataRecords));
    assertThat(
        analysisService.getHearbeatRecordForL0(appId, stateExecutionId, StateType.SPLUNKV2, hosts.iterator().next())
            .isPresent())
        .isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)

  @Ignore("TODO: Legacy test failures")
  public void deleteClusterLevel() throws Exception {
    String query = UUID.randomUUID().toString();
    int numOfHosts = 1 + r.nextInt(10);
    int logCollectionMinute = 1 + r.nextInt(10);

    List<LogDataRecord> logDataRecords = new ArrayList<>();
    Set<String> hosts = new HashSet<>();
    for (int i = 0; i < numOfHosts; i++) {
      String host = UUID.randomUUID().toString();
      hosts.add(host);

      LogDataRecord logDataRecord = new LogDataRecord();
      logDataRecord.setAppId(appId);
      logDataRecord.setStateExecutionId(stateExecutionId);
      logDataRecord.setStateType(StateType.SPLUNKV2);
      logDataRecord.setClusterLevel(ClusterLevel.H0);
      logDataRecord.setQuery(query);
      logDataRecord.setLogCollectionMinute(logCollectionMinute);
      logDataRecord.setLogMessage(UUID.randomUUID().toString());
      logDataRecord.setHost(host);

      logDataRecords.add(logDataRecord);
    }

    wingsPersistence.save(logDataRecords);
    assertThat(wingsPersistence.createQuery(LogDataRecord.class)
                   .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                   .count())
        .isEqualTo(numOfHosts);
    analysisService.deleteClusterLevel(
        StateType.SPLUNKV2, stateExecutionId, appId, query, hosts, logCollectionMinute, ClusterLevel.H0);
    assertThat(wingsPersistence.createQuery(LogDataRecord.class)
                   .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                   .count())
        .isEqualTo(0);
  }

  private SplunkAnalysisCluster getRandomClusterEvent() {
    SplunkAnalysisCluster analysisCluster = new SplunkAnalysisCluster();
    analysisCluster.setCluster_label(r.nextInt(100));
    analysisCluster.setAnomalous_counts(Lists.newArrayList(r.nextInt(100), r.nextInt(100), r.nextInt(100)));
    analysisCluster.setText(UUID.randomUUID().toString());
    analysisCluster.setTags(
        Lists.newArrayList(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()));
    analysisCluster.setDiff_tags(
        Lists.newArrayList(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()));
    analysisCluster.setX(r.nextDouble());
    analysisCluster.setY(r.nextDouble());
    analysisCluster.setUnexpected_freq(r.nextBoolean());
    List<MessageFrequency> frequencyMapList = new ArrayList<>();
    for (int i = 0; i < 1 + r.nextInt(10); i++) {
      frequencyMapList.add(MessageFrequency.builder().count(r.nextInt(100)).build());
    }

    analysisCluster.setMessage_frequencies(frequencyMapList);
    return analysisCluster;
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void loadPythonResponse() throws IOException {
    InputStream is = getClass().getClassLoader().getResourceAsStream("verification/LogAnalysisRecord.json");
    String jsonTxt = IOUtils.toString(is, Charset.defaultCharset());
    LogMLAnalysisRecord records = JsonUtils.asObject(jsonTxt, LogMLAnalysisRecord.class);
    assertThat(records.getUnknown_events()).hasSize(7);
    assertThat(records.getTest_events()).hasSize(33);
    assertThat(records.getControl_events()).hasSize(31);
    assertThat(records.getControl_clusters()).hasSize(31);
    assertThat(records.getTest_clusters()).hasSize(26);
    assertThat(records.getUnknown_clusters()).hasSize(4);
    assertThat(records.getCluster_scores().getTest()).isEmpty();
    assertThat(records.getCluster_scores().getUnknown()).hasSize(4);
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void checkClusterScores() throws IOException {
    InputStream is = new FileInputStream(new File("270-verification/src"
        + "/test/resources/verification/LogAnalysisRecord.json"));
    String jsonTxt = IOUtils.toString(is, Charset.defaultCharset());
    LogMLAnalysisRecord records = JsonUtils.asObject(jsonTxt, LogMLAnalysisRecord.class);
    records.setStateType(ELK);
    records.setAppId(appId);
    String stateExecutionId = UUID.randomUUID().toString();
    records.setStateExecutionId(stateExecutionId);
    records.setAnalysisSummaryMessage("10");
    AnalysisContext context =
        AnalysisContext.builder().appId(appId).stateExecutionId(stateExecutionId).serviceId(serviceId).build();
    wingsPersistence.save(context);
    analysisService.saveLogAnalysisRecords(records, StateType.SPLUNKV2, Optional.empty(), Optional.empty());
    LogMLAnalysisSummary analysisSummary =
        managerAnalysisService.getAnalysisSummary(stateExecutionId, appId, StateType.SPLUNKV2);
    assertThat(Double.compare(analysisSummary.getScore(), 0.23477964144180682 * 100)).isEqualTo(0);
    for (LogMLClusterSummary clusterSummary : analysisSummary.getUnknownClusters()) {
      assert clusterSummary.getScore() > 0;
    }
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void testUserFeedback() throws Exception {
    InputStream is = getClass().getClassLoader().getResourceAsStream("verification/LogAnalysisRecord.json");
    String jsonTxt = IOUtils.toString(is, Charset.defaultCharset());
    StateExecutionInstance stateExecutionInstance = new StateExecutionInstance();
    stateExecutionInstance.setAppId(appId);
    stateExecutionInstance.setUuid(stateExecutionId);
    stateExecutionInstance.setWorkflowId(workflowId);
    stateExecutionInstance.setExecutionUuid(workflowExecutionId);
    stateExecutionInstance.setStatus(ExecutionStatus.ABORTED);
    stateExecutionInstance.getContextElements().push(
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(serviceId).build()).build());
    wingsPersistence.save(stateExecutionInstance);

    WorkflowExecution execution =
        WorkflowExecution.builder().uuid(workflowExecutionId).envId("envId").appId(appId).build();
    wingsPersistence.save(execution);

    LogMLAnalysisRecord records = JsonUtils.asObject(jsonTxt, LogMLAnalysisRecord.class);
    records.setStateType(ELK);
    records.setAppId(appId);
    records.setStateExecutionId(stateExecutionId);
    records.setAnalysisSummaryMessage("10");
    analysisService.saveLogAnalysisRecords(records, ELK, Optional.empty(), Optional.empty());

    LogMLFeedback logMLFeedback = LogMLFeedback.builder()
                                      .appId(appId)
                                      .clusterLabel(0)
                                      .clusterType(AnalysisServiceImpl.CLUSTER_TYPE.UNKNOWN)
                                      .comment("excellent!!")
                                      .logMLFeedbackType(AnalysisServiceImpl.LogMLFeedbackType.IGNORE_ALWAYS)
                                      .stateExecutionId(stateExecutionId)
                                      .build();

    managerAnalysisService.saveFeedback(logMLFeedback, ELK);
    List<LogMLFeedbackRecord> mlFeedback =
        managerAnalysisService.getMLFeedback(appId, serviceId, workflowId, workflowExecutionId);
    assertThat(mlFeedback.isEmpty()).isFalse();
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void logQueryTrim() {
    ElkAnalysisState elkAnalysisState = new ElkAnalysisState("some name");
    elkAnalysisState.setQuery(" er ror ");
    assertThat(elkAnalysisState.getQuery()).isEqualTo("er ror");
  }

  @Test
  @Owner(developers = SRIRAM)
  @Category(UnitTests.class)
  public void formatDate() {
    ZonedDateTime zdt = ZonedDateTime.parse("2018-05-10T16:35:27.044Z");
    log.info("" + zdt.toEpochSecond());

    zdt = ZonedDateTime.parse("2018-04-27T23:11:23.628Z");
    log.info("" + zdt.toEpochSecond());

    DateTimeFormatter df1 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
    log.info("" + Instant.from(df1.parse("2018-04-27T23:11:23.628Z")).toEpochMilli());

    df1 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSX");
    log.info("" + Instant.from(df1.parse("2018-04-27T23:11:23.628456789Z")).toEpochMilli());

    DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX");
    log.info("" + Instant.from(df.parse("2018-05-03T00:15:12.618905414+00:00")).toEpochMilli());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testReadLogMLRecordFromDB() throws Exception {
    ArrayList<List<SplunkAnalysisCluster>> unknownEvents = Lists.newArrayList(getEvents(1 + r.nextInt(10)).values());
    Map<String, List<SplunkAnalysisCluster>> testEvents = getEvents(1 + r.nextInt(10));
    Map<String, List<SplunkAnalysisCluster>> controlEvents = getEvents(1 + r.nextInt(10));

    Map<String, Map<String, SplunkAnalysisCluster>> controlClusters = createClusters(1 + r.nextInt(10));
    Map<String, Map<String, SplunkAnalysisCluster>> unknownClusters = createClusters(1 + r.nextInt(10));
    Map<String, Map<String, SplunkAnalysisCluster>> testClusters = createClusters(1 + r.nextInt(10));
    Map<String, Map<String, SplunkAnalysisCluster>> ignoreClusters = createClusters(1 + r.nextInt(10));
    LogMLAnalysisRecord record = LogMLAnalysisRecord.builder()
                                     .stateExecutionId(stateExecutionId)
                                     .appId(appId)
                                     .accountId(accountId)
                                     .stateType(StateType.SPLUNKV2)
                                     .logCollectionMinute(0)
                                     .query(UUID.randomUUID().toString())
                                     .unknown_events(unknownEvents)
                                     .test_events(testEvents)
                                     .control_events(controlEvents)
                                     .control_clusters(controlClusters)
                                     .unknown_clusters(unknownClusters)
                                     .test_clusters(testClusters)
                                     .ignore_clusters(ignoreClusters)
                                     .build();
    String logAnalysisRecordId = wingsPersistence.save(record);
    LogMLAnalysisRecord savedRecord = wingsPersistence.get(LogMLAnalysisRecord.class, logAnalysisRecordId);

    assertThat(savedRecord.getUnknown_events()).isEqualTo(unknownEvents);
    assertThat(savedRecord.getTest_events()).isEqualTo(testEvents);
    assertThat(savedRecord.getControl_events()).isEqualTo(controlEvents);
    assertThat(savedRecord.getControl_clusters()).isEqualTo(controlClusters);
    assertThat(savedRecord.getUnknown_clusters()).isEqualTo(unknownClusters);
    assertThat(savedRecord.getTest_clusters()).isEqualTo(testClusters);
    assertThat(savedRecord.getIgnore_clusters()).isEqualTo(ignoreClusters);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCleanup() {
    wingsPersistence.delete(wingsPersistence.createQuery(AnalysisContext.class)
                                .filter(AnalysisContextKeys.stateExecutionId, stateExecutionId));
    int numOfRecords = 10;
    for (int i = 0; i < numOfRecords; i++) {
      LogDataRecord logDataRecord = new LogDataRecord();
      logDataRecord.setStateExecutionId(stateExecutionId);
      logDataRecord.setAppId(appId);
      logDataRecord.setLogCollectionMinute(i);
      wingsPersistence.save(logDataRecord);
      wingsPersistence.save(LogMLAnalysisRecord.builder()
                                .stateExecutionId(stateExecutionId)
                                .accountId(accountId)
                                .logCollectionMinute(i)
                                .appId(appId)
                                .build());
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
          AnalysisContext.builder().stateExecutionId(stateExecutionId).serviceId("service-" + i).build());
    }

    assertThat(wingsPersistence.createQuery(LogDataRecord.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class).count())
        .isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(LearningEngineExperimentalAnalysisTask.class).count())
        .isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(ExperimentalLogMLAnalysisRecord.class).count()).isEqualTo(numOfRecords);
    assertThat(wingsPersistence.createQuery(AnalysisContext.class).count()).isEqualTo(numOfRecords);

    managerAnalysisService.cleanUpForLogRetry(stateExecutionId);
    assertThat(wingsPersistence.createQuery(LogDataRecord.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(ContinuousVerificationExecutionMetaData.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(LearningEngineExperimentalAnalysisTask.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(ExperimentalLogMLAnalysisRecord.class).count()).isEqualTo(0);
    assertThat(wingsPersistence.createQuery(AnalysisContext.class).count()).isEqualTo(0);
  }

  private Map<String, Map<String, SplunkAnalysisCluster>> createClusters(int numOfClusters) {
    Map<String, Map<String, SplunkAnalysisCluster>> rv = new HashMap<>();
    for (int i = 0; i < numOfClusters; i++) {
      SplunkAnalysisCluster cluster = getRandomClusterEvent();
      Map<String, SplunkAnalysisCluster> hostMap = new HashMap<>();
      String host = UUID.randomUUID().toString();
      hostMap.put(host, cluster);
      rv.put(UUID.randomUUID().toString(), hostMap);
    }
    return rv;
  }

  private Map<String, List<SplunkAnalysisCluster>> getEvents(int numOfEvents) {
    Map<String, List<SplunkAnalysisCluster>> rv = new HashMap<>();
    for (int i = 0; i < numOfEvents; i++) {
      rv.put(generateUuid(),
          Lists.newArrayList(getRandomClusterEvent(), getRandomClusterEvent(), getRandomClusterEvent()));
    }
    return rv;
  }

  private void createLogsCVConfig(boolean enabled24x7) {
    logsCVConfiguration = new LogsCVConfiguration();
    logsCVConfiguration.setName("Config 1");
    logsCVConfiguration.setAppId(appId);
    logsCVConfiguration.setEnvId(UUID.randomUUID().toString());
    logsCVConfiguration.setStateType(ELK);
    logsCVConfiguration.setServiceId(serviceId);
    logsCVConfiguration.setEnabled24x7(enabled24x7);
    logsCVConfiguration.setConnectorId(UUID.randomUUID().toString());
    logsCVConfiguration.setAnalysisTolerance(AnalysisTolerance.MEDIUM);
    logsCVConfiguration.setBaselineStartMinute(100);
    logsCVConfiguration.setBaselineEndMinute(200);

    logsCVConfiguration.setQuery("query1");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testCompressionLogMlAnalysisRecord() throws IOException {
    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    assertThat(logMLAnalysisRecord).isNotNull();
    assertThat(logMLAnalysisRecord.getUnknown_events().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getTest_events().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getControl_events().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getControl_clusters().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getUnknown_clusters().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getTest_clusters().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getIgnore_clusters().isEmpty()).isFalse();

    LogMLAnalysisRecord compressedLogMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      compressedLogMLAnalysisRecord = gson.fromJson(br, type);
    }
    assertThat(compressedLogMLAnalysisRecord).isNotNull();

    compressedLogMLAnalysisRecord.compressLogAnalysisRecord();
    assertThat(compressedLogMLAnalysisRecord.getProtoSerializedAnalyisDetails()).isNotNull();
    assertThat(compressedLogMLAnalysisRecord.getUnknown_events()).isNull();
    assertThat(compressedLogMLAnalysisRecord.getTest_events()).isNull();
    assertThat(compressedLogMLAnalysisRecord.getControl_events()).isNull();
    assertThat(compressedLogMLAnalysisRecord.getControl_clusters()).isNull();
    assertThat(compressedLogMLAnalysisRecord.getTest_clusters()).isNull();
    assertThat(compressedLogMLAnalysisRecord.getUnknown_clusters()).isNull();
    assertThat(compressedLogMLAnalysisRecord.getIgnore_clusters()).isNull();

    compressedLogMLAnalysisRecord.decompressLogAnalysisRecord();
    assertThat(compressedLogMLAnalysisRecord.getProtoSerializedAnalyisDetails()).isNull();
    assertThat(compressedLogMLAnalysisRecord.getUnknown_events()).isNotNull();
    assertThat(compressedLogMLAnalysisRecord.getTest_events()).isNotNull();
    assertThat(compressedLogMLAnalysisRecord.getControl_events()).isNotNull();
    assertThat(compressedLogMLAnalysisRecord.getControl_clusters()).isNotNull();
    assertThat(compressedLogMLAnalysisRecord.getTest_clusters()).isNotNull();
    assertThat(compressedLogMLAnalysisRecord.getUnknown_clusters()).isNotNull();
    assertThat(compressedLogMLAnalysisRecord.getIgnore_clusters()).isNotNull();

    assertThat(compressedLogMLAnalysisRecord.getAnalysisStatus()).isEqualTo(logMLAnalysisRecord.getAnalysisStatus());
    assertThat(compressedLogMLAnalysisRecord.getUnknown_events()).isEqualTo(logMLAnalysisRecord.getUnknown_events());
    assertThat(compressedLogMLAnalysisRecord.getTest_events()).isEqualTo(logMLAnalysisRecord.getTest_events());
    assertThat(compressedLogMLAnalysisRecord.getControl_events()).isEqualTo(logMLAnalysisRecord.getControl_events());
    assertThat(compressedLogMLAnalysisRecord.getTest_clusters()).isEqualTo(logMLAnalysisRecord.getTest_clusters());
    assertThat(compressedLogMLAnalysisRecord.getControl_clusters())
        .isEqualTo(logMLAnalysisRecord.getControl_clusters());
    assertThat(compressedLogMLAnalysisRecord.getUnknown_clusters())
        .isEqualTo(logMLAnalysisRecord.getUnknown_clusters());
    assertThat(compressedLogMLAnalysisRecord.getIgnore_clusters()).isEqualTo(logMLAnalysisRecord.getIgnore_clusters());
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testCompressionLogMlAnalysisRecordOnDemand() throws IOException {
    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    assertThat(logMLAnalysisRecord).isNotNull();
    assertThat(logMLAnalysisRecord.getUnknown_events().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getTest_events().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getControl_events().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getControl_clusters().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getUnknown_clusters().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getTest_clusters().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getIgnore_clusters().isEmpty()).isFalse();
    assertThat(logMLAnalysisRecord.getAnalysisDetailsCompressedJson()).isNull();
    assertThat(logMLAnalysisRecord.getProtoSerializedAnalyisDetails()).isNull();

    // save using json compression
    LogMLAnalysisRecord logAnalysisDetails = LogMLAnalysisRecord.builder()
                                                 .accountId(accountId)
                                                 .unknown_events(logMLAnalysisRecord.getUnknown_events())
                                                 .test_events(logMLAnalysisRecord.getTest_events())
                                                 .control_events(logMLAnalysisRecord.getControl_events())
                                                 .control_clusters(logMLAnalysisRecord.getControl_clusters())
                                                 .unknown_clusters(logMLAnalysisRecord.getUnknown_clusters())
                                                 .test_clusters(logMLAnalysisRecord.getTest_clusters())
                                                 .ignore_clusters(logMLAnalysisRecord.getIgnore_clusters())
                                                 .build();

    logMLAnalysisRecord.setAnalysisDetailsCompressedJson(compressString(JsonUtils.asJson(logAnalysisDetails)));
    logMLAnalysisRecord.setUnknown_events(null);
    logMLAnalysisRecord.setTest_events(null);
    logMLAnalysisRecord.setControl_events(null);
    logMLAnalysisRecord.setControl_clusters(null);
    logMLAnalysisRecord.setUnknown_clusters(null);
    logMLAnalysisRecord.setTest_clusters(null);
    logMLAnalysisRecord.setIgnore_clusters(null);
    wingsPersistence.save(logMLAnalysisRecord);

    LogMLAnalysisRecord savedMlAnalysisRecord =
        wingsPersistence.get(LogMLAnalysisRecord.class, logMLAnalysisRecord.getUuid());
    assertThat(savedMlAnalysisRecord.getUnknown_events()).isNull();
    assertThat(savedMlAnalysisRecord.getTest_events()).isNull();
    assertThat(savedMlAnalysisRecord.getControl_events()).isNull();
    assertThat(savedMlAnalysisRecord.getControl_clusters()).isNull();
    assertThat(savedMlAnalysisRecord.getUnknown_clusters()).isNull();
    assertThat(savedMlAnalysisRecord.getTest_clusters()).isNull();
    assertThat(savedMlAnalysisRecord.getIgnore_clusters()).isNull();
    assertThat(savedMlAnalysisRecord.getProtoSerializedAnalyisDetails()).isNull();
    assertThat(savedMlAnalysisRecord.getAnalysisDetailsCompressedJson()).isNotNull();

    analysisService.getLogAnalysisRecords(LogMLAnalysisRecordKeys.cvConfigId, logMLAnalysisRecord.getCvConfigId(),
        logMLAnalysisRecord.getLogCollectionMinute(), true);
    savedMlAnalysisRecord = wingsPersistence.get(LogMLAnalysisRecord.class, logMLAnalysisRecord.getUuid());
    assertThat(savedMlAnalysisRecord.getUnknown_events()).isNull();
    assertThat(savedMlAnalysisRecord.getTest_events()).isNull();
    assertThat(savedMlAnalysisRecord.getControl_events()).isNull();
    assertThat(savedMlAnalysisRecord.getControl_clusters()).isNull();
    assertThat(savedMlAnalysisRecord.getUnknown_clusters()).isNull();
    assertThat(savedMlAnalysisRecord.getTest_clusters()).isNull();
    assertThat(savedMlAnalysisRecord.getIgnore_clusters()).isNull();
    assertThat(savedMlAnalysisRecord.getAnalysisDetailsCompressedJson()).isNull();
    assertThat(savedMlAnalysisRecord.getProtoSerializedAnalyisDetails()).isNotNull();

    LogMLAnalysisRecord logMLAnalysisRecordToCompare;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecordToCompare = gson.fromJson(br, type);
    }
    assertThat(logMLAnalysisRecordToCompare).isNotNull();
    assertThat(logMLAnalysisRecordToCompare.getUnknown_events()).isNotNull();
    assertThat(logMLAnalysisRecordToCompare.getTest_events()).isNotNull();
    assertThat(logMLAnalysisRecordToCompare.getControl_events()).isNotNull();
    assertThat(logMLAnalysisRecordToCompare.getControl_clusters()).isNotNull();
    assertThat(logMLAnalysisRecordToCompare.getTest_clusters()).isNotNull();
    assertThat(logMLAnalysisRecordToCompare.getUnknown_clusters()).isNotNull();
    assertThat(logMLAnalysisRecordToCompare.getIgnore_clusters()).isNotNull();
    assertThat(logMLAnalysisRecordToCompare.getAnalysisDetailsCompressedJson()).isNull();
    assertThat(logMLAnalysisRecordToCompare.getProtoSerializedAnalyisDetails()).isNull();

    logMLAnalysisRecord = analysisService.getLogAnalysisRecords(LogMLAnalysisRecordKeys.cvConfigId,
        logMLAnalysisRecord.getCvConfigId(), logMLAnalysisRecord.getLogCollectionMinute(), true);
    assertThat(logMLAnalysisRecord.getUnknown_events()).isNull();
    assertThat(logMLAnalysisRecord.getTest_events()).isNull();
    assertThat(logMLAnalysisRecord.getControl_events()).isNull();
    assertThat(logMLAnalysisRecord.getControl_clusters()).isNull();
    assertThat(logMLAnalysisRecord.getUnknown_clusters()).isNull();
    assertThat(logMLAnalysisRecord.getTest_clusters()).isNull();
    assertThat(logMLAnalysisRecord.getIgnore_clusters()).isNull();
    assertThat(logMLAnalysisRecord.getAnalysisDetailsCompressedJson()).isNull();
    assertThat(logMLAnalysisRecord.getProtoSerializedAnalyisDetails()).isNotNull();

    logMLAnalysisRecord.decompressLogAnalysisRecord();
    assertThat(logMLAnalysisRecordToCompare.getAnalysisStatus()).isEqualTo(logMLAnalysisRecord.getAnalysisStatus());
    assertThat(logMLAnalysisRecordToCompare.getUnknown_events()).isEqualTo(logMLAnalysisRecord.getUnknown_events());
    assertThat(logMLAnalysisRecordToCompare.getTest_events()).isEqualTo(logMLAnalysisRecord.getTest_events());
    assertThat(logMLAnalysisRecordToCompare.getControl_events()).isEqualTo(logMLAnalysisRecord.getControl_events());
    assertThat(logMLAnalysisRecordToCompare.getTest_clusters()).isEqualTo(logMLAnalysisRecord.getTest_clusters());
    assertThat(logMLAnalysisRecordToCompare.getControl_clusters()).isEqualTo(logMLAnalysisRecord.getControl_clusters());
    assertThat(logMLAnalysisRecordToCompare.getUnknown_clusters()).isEqualTo(logMLAnalysisRecord.getUnknown_clusters());
    assertThat(logMLAnalysisRecordToCompare.getIgnore_clusters()).isEqualTo(logMLAnalysisRecord.getIgnore_clusters());

    logMLAnalysisRecord = analysisService.getLogAnalysisRecords(LogMLAnalysisRecordKeys.cvConfigId,
        logMLAnalysisRecord.getCvConfigId(), logMLAnalysisRecord.getLogCollectionMinute(), false);
    assertThat(logMLAnalysisRecord.getUnknown_events()).isNotNull();
    assertThat(logMLAnalysisRecord.getTest_events()).isNotNull();
    assertThat(logMLAnalysisRecord.getControl_events()).isNotNull();
    assertThat(logMLAnalysisRecord.getControl_clusters()).isNotNull();
    assertThat(logMLAnalysisRecord.getUnknown_clusters()).isNotNull();
    assertThat(logMLAnalysisRecord.getTest_clusters()).isNotNull();
    assertThat(logMLAnalysisRecord.getIgnore_clusters()).isNotNull();
    assertThat(logMLAnalysisRecord.getAnalysisDetailsCompressedJson()).isNull();
    assertThat(logMLAnalysisRecord.getProtoSerializedAnalyisDetails()).isNull();

    assertThat(logMLAnalysisRecordToCompare.getAnalysisStatus()).isEqualTo(logMLAnalysisRecord.getAnalysisStatus());
    assertThat(logMLAnalysisRecordToCompare.getUnknown_events()).isEqualTo(logMLAnalysisRecord.getUnknown_events());
    assertThat(logMLAnalysisRecordToCompare.getTest_events()).isEqualTo(logMLAnalysisRecord.getTest_events());
    assertThat(logMLAnalysisRecordToCompare.getControl_events()).isEqualTo(logMLAnalysisRecord.getControl_events());
    assertThat(logMLAnalysisRecordToCompare.getTest_clusters()).isEqualTo(logMLAnalysisRecord.getTest_clusters());
    assertThat(logMLAnalysisRecordToCompare.getControl_clusters()).isEqualTo(logMLAnalysisRecord.getControl_clusters());
    assertThat(logMLAnalysisRecordToCompare.getUnknown_clusters()).isEqualTo(logMLAnalysisRecord.getUnknown_clusters());
    assertThat(logMLAnalysisRecordToCompare.getIgnore_clusters()).isEqualTo(logMLAnalysisRecord.getIgnore_clusters());
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testCompressionLogMLAnalysisRecordFrequencyPattern() throws IOException {
    File file = new File("270-verification/src/test/resources/sumo/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord compressedLogMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      compressedLogMLAnalysisRecord = gson.fromJson(br, type);
    }
    assertThat(compressedLogMLAnalysisRecord).isNotNull();

    Map<Integer, FrequencyPattern> frequencyPatterns = compressedLogMLAnalysisRecord.getFrequency_patterns();

    compressedLogMLAnalysisRecord.compressLogAnalysisRecord();
    assertThat(compressedLogMLAnalysisRecord.getFrequency_patterns()).isNull();

    compressedLogMLAnalysisRecord.decompressLogAnalysisRecord();
    assertThat(compressedLogMLAnalysisRecord.getFrequency_patterns()).isNotNull();
    assertThat(compressedLogMLAnalysisRecord.getFrequency_patterns()).isEqualTo(frequencyPatterns);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testCompressionLogMLAnalysisRecordLogAnalysisResult() throws IOException {
    File file = new File("270-verification/src/test/resources/sumo/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord compressedLogMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      compressedLogMLAnalysisRecord = gson.fromJson(br, type);
    }
    assertThat(compressedLogMLAnalysisRecord).isNotNull();

    Map<Integer, LogAnalysisResult> logAnalysisResult = compressedLogMLAnalysisRecord.getLog_analysis_result();

    compressedLogMLAnalysisRecord.compressLogAnalysisRecord();
    assertThat(compressedLogMLAnalysisRecord.getLog_analysis_result()).isNull();

    compressedLogMLAnalysisRecord.decompressLogAnalysisRecord();
    assertThat(compressedLogMLAnalysisRecord.getLog_analysis_result()).isNotNull();
    assertThat(compressedLogMLAnalysisRecord.getLog_analysis_result()).isEqualTo(logAnalysisResult);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetAnalysisForBaseline() throws IOException {
    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }
    LogsCVConfiguration logsCVConfiguration = new LogsCVConfiguration();
    logsCVConfiguration.setUuid(logMLAnalysisRecord.getCvConfigId());
    wingsPersistence.save(logsCVConfiguration);

    int numOfRecords = 10;
    for (int i = 0; i < numOfRecords; i++) {
      logMLAnalysisRecord.setUuid(null);
      logMLAnalysisRecord.setLogCollectionMinute(i + 1);
      logMLAnalysisRecord.setAnalysisStatus(LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
      wingsPersistence.save(logMLAnalysisRecord);
    }

    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).asList().size())
        .isEqualTo(numOfRecords);

    LogMLAnalysisRecord logAnalysisRecord = analysisService.getLogAnalysisRecords(
        LogMLAnalysisRecordKeys.cvConfigId, logMLAnalysisRecord.getCvConfigId(), numOfRecords, false);

    assertThat(logAnalysisRecord.getLogCollectionMinute()).isEqualTo(numOfRecords);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveDuplicate() throws IOException, IllegalAccessException {
    Call<RestResponse<Boolean>> managerCallFeedbacks = mock(Call.class);
    when(managerCallFeedbacks.clone()).thenReturn(managerCallFeedbacks);
    when(managerCallFeedbacks.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(verificationManagerClient.isFeatureEnabled(any(), any())).thenReturn(managerCallFeedbacks);
    FieldUtils.writeDeclaredField(analysisService, "managerClient", verificationManagerClient, true);

    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    LogsCVConfiguration logsCVConfiguration = new LogsCVConfiguration();
    logsCVConfiguration.setUuid(logMLAnalysisRecord.getCvConfigId());
    wingsPersistence.save(logsCVConfiguration);

    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).asList()).isEmpty();
    analysisService.save24X7LogAnalysisRecords(logMLAnalysisRecord.getAppId(), logMLAnalysisRecord.getCvConfigId(),
        logMLAnalysisRecord.getLogCollectionMinute(), logMLAnalysisRecord, Optional.empty(), Optional.empty());

    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).asList()).hasSize(1);

    analysisService.save24X7LogAnalysisRecords(logMLAnalysisRecord.getAppId(), logMLAnalysisRecord.getCvConfigId(),
        logMLAnalysisRecord.getLogCollectionMinute(), logMLAnalysisRecord, Optional.empty(), Optional.empty());

    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).asList()).hasSize(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testLogAnalysisAlert_featureFlagDisabled() throws Exception {
    Call<RestResponse<Boolean>> managerCallFeedbacks = mock(Call.class);
    when(managerCallFeedbacks.clone()).thenReturn(managerCallFeedbacks);
    when(managerCallFeedbacks.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(verificationManagerClient.isFeatureEnabled(any(), any())).thenReturn(managerCallFeedbacks);
    FieldUtils.writeDeclaredField(analysisService, "managerClient", verificationManagerClient, true);
    doNothing().when(continuousVerificationService).triggerLogAnalysisAlertIfNecessary(any(), any(), anyInt());

    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    LogsCVConfiguration logsCVConfiguration = new LogsCVConfiguration();
    logsCVConfiguration.setUuid(logMLAnalysisRecord.getCvConfigId());
    wingsPersistence.save(logsCVConfiguration);

    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).asList()).isEmpty();
    analysisService.save24X7LogAnalysisRecords(logMLAnalysisRecord.getAppId(), logMLAnalysisRecord.getCvConfigId(),
        logMLAnalysisRecord.getLogCollectionMinute(), logMLAnalysisRecord, Optional.empty(), Optional.of(true));
    verify(continuousVerificationService, times(1)).triggerLogAnalysisAlertIfNecessary(any(), any(), anyInt());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testLogAnalysisAlert_featureFlagEnabled() throws Exception {
    Call<RestResponse<Boolean>> managerCallFeedbacks = mock(Call.class);
    when(managerCallFeedbacks.clone()).thenReturn(managerCallFeedbacks);
    when(managerCallFeedbacks.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(verificationManagerClient.isFeatureEnabled(any(), any())).thenReturn(managerCallFeedbacks);
    FieldUtils.writeDeclaredField(analysisService, "managerClient", verificationManagerClient, true);
    doNothing().when(continuousVerificationService).triggerLogAnalysisAlertIfNecessary(any(), any(), anyInt());

    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    LogsCVConfiguration logsCVConfiguration = new LogsCVConfiguration();
    logsCVConfiguration.setUuid(logMLAnalysisRecord.getCvConfigId());
    wingsPersistence.save(logsCVConfiguration);

    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).asList()).isEmpty();
    analysisService.save24X7LogAnalysisRecords(logMLAnalysisRecord.getAppId(), logMLAnalysisRecord.getCvConfigId(),
        logMLAnalysisRecord.getLogCollectionMinute(), logMLAnalysisRecord, Optional.empty(), Optional.empty());
    verify(continuousVerificationService, never()).triggerLogAnalysisAlertIfNecessary(any(), any(), anyInt());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testServiceGuardBackoffCountNewTask() {
    // Setup with sample failed task
    int analysisMinute = 1234567;
    // test behavior
    boolean isEligible =
        learningEngineService.isEligibleToCreateTask(stateExecutionId, "", analysisMinute, MLAnalysisType.LOG_ML);
    assertThat(isEligible).isTrue();
    int nextCount = learningEngineService.getNextServiceGuardBackoffCount(
        stateExecutionId, "", analysisMinute, MLAnalysisType.LOG_ML);
    assertThat(nextCount).isEqualTo(1);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testServiceGuardBackoffCount() {
    // Setup with sample failed task
    int analysisMinute = 1234567;
    createLETaskForBackoffTest(analysisMinute, 1);

    // test behavior
    boolean isEligible =
        learningEngineService.isEligibleToCreateTask(stateExecutionId, "", analysisMinute, MLAnalysisType.LOG_ML);
    assertThat(isEligible).isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testServiceGuardMaxBackoffCount() {
    // Setup with sample failed task
    int analysisMinute = 1234567;

    createLETaskForBackoffTest(analysisMinute, 8);
    Datastore datastore = wingsPersistence.getDatastore(LearningEngineAnalysisTask.class);

    Query<LearningEngineAnalysisTask> taskQuery =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId + "-retry-1234");
    UpdateOperations<LearningEngineAnalysisTask> updateOperations =
        wingsPersistence.createUpdateOperations(LearningEngineAnalysisTask.class)
            .set(LearningEngineAnalysisTask.LAST_UPDATED_AT_KEY2,
                System.currentTimeMillis() - TimeUnit.HOURS.toMillis(4));
    datastore.update(taskQuery, updateOperations);

    // test behavior
    boolean isEligibleForTask =
        learningEngineService.isEligibleToCreateTask(stateExecutionId, "", analysisMinute, MLAnalysisType.LOG_ML);
    assertThat(isEligibleForTask).isTrue();
    int nextCount = learningEngineService.getNextServiceGuardBackoffCount(
        stateExecutionId, "", analysisMinute, MLAnalysisType.LOG_ML);

    // verify
    assertThat(nextCount).isEqualTo(BACKOFF_LIMIT);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateFeedbackAnalysis() {
    String cvConfigId = generateUuid();
    LogMLAnalysisRecord analysisRecord = LogMLAnalysisRecord.builder()
                                             .logCollectionMinute(10)
                                             .appId(appId)
                                             .accountId(accountId)
                                             .cvConfigId(cvConfigId)
                                             .uuid("uuid1")
                                             .build();
    analysisRecord.setAnalysisStatus(LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
    wingsPersistence.save(analysisRecord);

    boolean created =
        analysisService.createAndUpdateFeedbackAnalysis(LogMLAnalysisRecordKeys.cvConfigId, cvConfigId, 10);

    LogMLAnalysisRecord feedbackRecord =
        wingsPersistence.createQuery(LogMLAnalysisRecord.class)
            .filter(LogMLAnalysisRecordKeys.cvConfigId, cvConfigId)
            .filter(LogMLAnalysisRecordKeys.logCollectionMinute, 10)
            .filter(LogMLAnalysisRecordKeys.analysisStatus, LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE)
            .get();

    List<LogMLAnalysisRecord> records = wingsPersistence.createQuery(LogMLAnalysisRecord.class)
                                            .filter(LogMLAnalysisRecordKeys.cvConfigId, cvConfigId)
                                            .filter(LogMLAnalysisRecordKeys.logCollectionMinute, 10)
                                            .asList();

    assertThat(created).isTrue();
    assertThat(feedbackRecord).isNotNull();
    assertThat(records).isNotNull();
    assertThat(records).hasSize(2);
    assertThat("uuid1").isNotEqualTo(feedbackRecord.getUuid());
    verify(continuousVerificationService, times(1)).triggerLogAnalysisAlertIfNecessary(any(), any(), anyInt());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetEndTimeForLogAnalysisForSplunk() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis());
    AnalysisContext analysisContext = AnalysisContext.builder()
                                          .timeDuration(10)
                                          .startDataCollectionMinute(minute)
                                          .stateType(SPLUNKV2)
                                          .accountId(accountId)
                                          .build();
    int endTime = analysisService.getEndTimeForLogAnalysis(analysisContext);
    assertThat(minute + 10 - 1).isEqualTo(endTime);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetEndTimeForLogAnalysis_forELK() {
    long minute = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis());
    AnalysisContext analysisContext = AnalysisContext.builder()
                                          .timeDuration(10)
                                          .startDataCollectionMinute(minute)
                                          .stateType(ELK)
                                          .accountId(accountId)
                                          .build();
    int endTime = analysisService.getEndTimeForLogAnalysis(analysisContext);
    assertThat(minute + 10 - 1).isEqualTo(endTime);
  }
  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateFeedbackAnalysisNoLEAnalysis() {
    String cvConfigId = generateUuid();

    boolean created =
        analysisService.createAndUpdateFeedbackAnalysis(LogMLAnalysisRecordKeys.cvConfigId, cvConfigId, 10);

    LogMLAnalysisRecord feedbackRecord =
        wingsPersistence.createQuery(LogMLAnalysisRecord.class)
            .filter(LogMLAnalysisRecordKeys.cvConfigId, cvConfigId)
            .filter(LogMLAnalysisRecordKeys.logCollectionMinute, 10)
            .filter(LogMLAnalysisRecordKeys.analysisStatus, LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE)
            .get();

    List<LogMLAnalysisRecord> records = wingsPersistence.createQuery(LogMLAnalysisRecord.class)
                                            .filter(LogMLAnalysisRecordKeys.cvConfigId, cvConfigId)
                                            .filter(LogMLAnalysisRecordKeys.logCollectionMinute, 10)
                                            .asList();

    assertThat(created).isFalse();
    assertThat(feedbackRecord).isNull();
    assertThat(isEmpty(records)).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveClusteredLogDataNoCvConfig() {
    assertThat(analysisService.saveClusteredLogData(appId, generateUuid(), ClusterLevel.L1, 0, generateUuid(), null))
        .isFalse();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveClusteredLogDataL0ToL1() {
    createLogsCVConfig(true);
    String cvConfigId = wingsPersistence.save(logsCVConfiguration);

    int numOfHosts = 1 + r.nextInt(10);
    int numOfL0Records = 1 + r.nextInt(10);
    int numOfMinutes = 1 + r.nextInt(5);
    int logCollectionMinuteBase = 1 + r.nextInt(5000);
    log.info("numOfHosts: {}, numOfL0Records: {} numOfMinutes: {}", numOfHosts, numOfL0Records, numOfMinutes);

    for (int logCollectionMinute = logCollectionMinuteBase;
         logCollectionMinute < logCollectionMinuteBase + numOfMinutes; logCollectionMinute++) {
      for (int hostNum = 0; hostNum < numOfHosts; hostNum++) {
        for (int l1 = 0; l1 < numOfL0Records; l1++) {
          wingsPersistence.save(LogDataRecord.builder()
                                    .cvConfigId(cvConfigId)
                                    .logMessage(generateUuid())
                                    .clusterLevel(ClusterLevel.L0)
                                    .host("host-" + hostNum)
                                    .logCollectionMinute(logCollectionMinute)
                                    .build());
        }

        wingsPersistence.save(LearningEngineAnalysisTask.builder()
                                  .cvConfigId(cvConfigId)
                                  .state_execution_id("LOGS_CLUSTER_L1_" + cvConfigId + "_" + logCollectionMinute)
                                  .analysis_minute(logCollectionMinute)
                                  .ml_analysis_type(MLAnalysisType.LOG_CLUSTER)
                                  .cluster_level(ClusterLevel.L1.getLevel())
                                  .executionStatus(ExecutionStatus.RUNNING)
                                  .build());
      }
    }

    for (int logCollectionMinute = logCollectionMinuteBase;
         logCollectionMinute < logCollectionMinuteBase + numOfMinutes; logCollectionMinute++) {
      for (int hostNum = 0; hostNum < numOfHosts; hostNum++) {
        List<LogElement> logElements = Lists.newArrayList(LogElement.builder()
                                                              .host("host-" + hostNum)
                                                              .logCollectionMinute(logCollectionMinute)
                                                              .logMessage(generateUuid())
                                                              .count(r.nextInt())
                                                              .clusterLabel("2")
                                                              .build());

        assertThat(wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                       .filter(LogDataRecordKeys.host, "host-" + hostNum)
                       .filter(LogDataRecordKeys.clusterLevel, ClusterLevel.L0)
                       .filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute)
                       .count())
            .isEqualTo(numOfL0Records);

        boolean status = analysisService.saveClusteredLogData(
            appId, cvConfigId, ClusterLevel.L1, logCollectionMinute, "host-" + hostNum, logElements);
        assertThat(status).isTrue();
        assertThat(wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                       .filter(LogDataRecordKeys.host, "host-" + hostNum)
                       .filter(LogDataRecordKeys.clusterLevel, ClusterLevel.L0)
                       .filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute)
                       .count())
            .isEqualTo(0);

        assertThat(wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                       .filter(LogDataRecordKeys.host, "host-" + hostNum)
                       .filter(LogDataRecordKeys.clusterLevel, ClusterLevel.L1)
                       .filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute)
                       .count())
            .isEqualTo(logElements.size());

        LearningEngineAnalysisTask analysisL1Task =
            wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
                .filter(LearningEngineAnalysisTaskKeys.state_execution_id,
                    "LOGS_CLUSTER_L1_" + cvConfigId + "_" + logCollectionMinute)
                .get();

        if (hostNum == numOfHosts - 1) {
          assertThat(analysisL1Task.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        } else {
          assertThat(analysisL1Task.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
        }
      }
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testSaveClusteredLogDataL1ToL2() {
    createLogsCVConfig(true);
    String cvConfigId = wingsPersistence.save(logsCVConfiguration);

    int numOfHosts = 1 + r.nextInt(10);
    int numOfL1Records = 1 + r.nextInt(10);
    int numOfMinutes = 1 + r.nextInt(5);
    int logCollectionMinuteBase = 1 + r.nextInt(5000);
    log.info("numOfHosts: {}, numOfL0Records: {} numOfL1Records: {} numOfMinutes: {}", numOfHosts, numOfL1Records,
        numOfMinutes);

    for (int logCollectionMinute = logCollectionMinuteBase;
         logCollectionMinute < logCollectionMinuteBase + numOfMinutes; logCollectionMinute++) {
      for (int hostNum = 0; hostNum < numOfHosts; hostNum++) {
        for (int l2 = 0; l2 < numOfL1Records; l2++) {
          wingsPersistence.save(LogDataRecord.builder()
                                    .cvConfigId(cvConfigId)
                                    .logMessage(generateUuid())
                                    .clusterLevel(ClusterLevel.L1)
                                    .host("host-" + hostNum)
                                    .logCollectionMinute(logCollectionMinute)
                                    .build());
        }

        wingsPersistence.save(LearningEngineAnalysisTask.builder()
                                  .cvConfigId(cvConfigId)
                                  .state_execution_id("LOGS_CLUSTER_L2_" + cvConfigId + "_" + logCollectionMinute)
                                  .analysis_minute(logCollectionMinute)
                                  .ml_analysis_type(MLAnalysisType.LOG_CLUSTER)
                                  .cluster_level(ClusterLevel.L2.getLevel())
                                  .executionStatus(ExecutionStatus.RUNNING)
                                  .build());
      }
    }

    for (int logCollectionMinute = logCollectionMinuteBase;
         logCollectionMinute < logCollectionMinuteBase + numOfMinutes; logCollectionMinute++) {
      assertThat(wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                     .filter(LogDataRecordKeys.clusterLevel, ClusterLevel.L1)
                     .filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute)
                     .count())
          .isEqualTo(numOfL1Records * numOfHosts);
      List<LogElement> logElements = Lists.newArrayList(LogElement.builder()
                                                            .host("host")
                                                            .logCollectionMinute(logCollectionMinute)
                                                            .logMessage(generateUuid())
                                                            .count(r.nextInt())
                                                            .clusterLabel("2")
                                                            .build());

      boolean status = analysisService.saveClusteredLogData(
          appId, cvConfigId, ClusterLevel.L2, logCollectionMinute, null, logElements);
      assertThat(status).isTrue();
      assertThat(wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                     .filter(LogDataRecordKeys.clusterLevel, ClusterLevel.L1)
                     .filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute)
                     .count())
          .isEqualTo(0);

      assertThat(wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                     .filter(LogDataRecordKeys.clusterLevel, ClusterLevel.L2)
                     .filter(LogDataRecordKeys.logCollectionMinute, logCollectionMinute)
                     .count())
          .isEqualTo(logElements.size());
      LearningEngineAnalysisTask analysisL1Task =
          wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
              .filter(LearningEngineAnalysisTaskKeys.state_execution_id,
                  "LOGS_CLUSTER_L2_" + cvConfigId + "_" + logCollectionMinute)
              .get();
      assertThat(analysisL1Task.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    }
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetLogDataServiceGuard() {
    createLogsCVConfig(true);
    String cvConfigId = wingsPersistence.save(logsCVConfiguration);
    int numOfMinutes = 100;
    int numOfHosts = 5;

    for (int logCollectionMinute = 0; logCollectionMinute < numOfMinutes; logCollectionMinute++) {
      for (int hostNum = 0; hostNum < numOfHosts; hostNum++) {
        wingsPersistence.save(LogDataRecord.builder()
                                  .cvConfigId(cvConfigId)
                                  .logMessage(generateUuid())
                                  .clusterLevel(ClusterLevel.L0)
                                  .host("host-" + hostNum)
                                  .logCollectionMinute(logCollectionMinute)
                                  .build());
      }
    }

    // ask for wrong time boundaries
    try {
      analysisService.getLogData(appId, cvConfigId, ClusterLevel.L0, 5, 3, 6, null);
      fail("passed for invalid times");
    } catch (Exception e) {
      // expected
    }

    // ask for wrong labels host combination
    try {
      analysisService.getLogData(appId, cvConfigId, ClusterLevel.L0, 5, 0, 0, LogRequest.builder().build());
      fail("passed for invalid times");
    } catch (Exception e) {
      // expected
    }

    assertThat(analysisService
                   .getLogData(appId, cvConfigId, ClusterLevel.L0, 5, 0, 0,
                       LogRequest.builder().nodes(Sets.newHashSet("host-0", "host-1")).build())
                   .size())
        .isEqualTo(2);

    assertThat(analysisService
                   .getLogData(appId, cvConfigId, ClusterLevel.L0, 0, 10, 19,
                       LogRequest.builder().nodes(Sets.newHashSet("host-0", "host-1")).build())
                   .size())
        .isEqualTo(20);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulWorkflowExecutionIdWithLogs() {
    int numOfWorkflowExecutions = 20;
    String query = generateUuid();
    assertThat(analysisService.getLastSuccessfulWorkflowExecutionIdWithLogs(
                   StateType.SUMO, appId, serviceId, workflowId, query))
        .isNull();

    for (int i = 0; i < numOfWorkflowExecutions; i++) {
      wingsPersistence.save(ContinuousVerificationExecutionMetaData.builder()
                                .workflowId(workflowId)
                                .stateType(StateType.SUMO)
                                .executionStatus(i % 2 == 0 ? ExecutionStatus.FAILED : ExecutionStatus.SUCCESS)
                                .workflowStartTs(i)
                                .workflowExecutionId("execution-" + i)
                                .build());
    }

    assertThat(analysisService.getLastSuccessfulWorkflowExecutionIdWithLogs(
                   StateType.SUMO, appId, serviceId, workflowId, query))
        .isEqualTo("execution-19");

    wingsPersistence.save(
        LogDataRecord.builder().workflowExecutionId("execution-15").clusterLevel(ClusterLevel.L2).query(query).build());
    assertThat(analysisService.getLastSuccessfulWorkflowExecutionIdWithLogs(
                   StateType.SUMO, appId, serviceId, workflowId, query))
        .isEqualTo("execution-15");
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetMaxCVCollectionMinute() {
    String cvConfigId = generateUuid();
    assertThat(analysisService.getMaxCVCollectionMinute(appId, cvConfigId)).isNegative();
    for (int i = 1; i <= 100; i++) {
      wingsPersistence.save(LogDataRecord.builder().cvConfigId(cvConfigId).logCollectionMinute(i).build());
    }
    assertThat(analysisService.getMaxCVCollectionMinute(appId, cvConfigId)).isEqualTo(100);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetLogRecordMinute() {
    String cvConfigId = generateUuid();
    assertThat(analysisService.getLogRecordMinute(appId, cvConfigId, ClusterLevel.L0, OrderType.ASC)).isNegative();
    for (int i = 1; i <= 100; i++) {
      wingsPersistence.save(
          LogDataRecord.builder().cvConfigId(cvConfigId).logCollectionMinute(i).clusterLevel(ClusterLevel.L0).build());
    }
    assertThat(analysisService.getLogRecordMinute(appId, cvConfigId, ClusterLevel.L0, OrderType.ASC)).isEqualTo(1);
    assertThat(analysisService.getLogRecordMinute(appId, cvConfigId, ClusterLevel.L0, OrderType.DESC)).isEqualTo(100);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetLastCVAnalysisMinute() {
    createLogsCVConfig(true);
    String cvConfigId = wingsPersistence.save(logsCVConfiguration);
    assertThat(analysisService.getLastCVAnalysisMinute(appId, cvConfigId, LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE))
        .isEqualTo(-1);

    for (int i = 1; i <= 100; i++) {
      LogMLAnalysisRecord mlAnalysisRecord =
          LogMLAnalysisRecord.builder().accountId(accountId).cvConfigId(cvConfigId).logCollectionMinute(i).build();
      mlAnalysisRecord.setAnalysisStatus(LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE);
      wingsPersistence.save(mlAnalysisRecord);
    }

    assertThat(analysisService.getLastCVAnalysisMinute(appId, cvConfigId, LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE))
        .isEqualTo(-1);
    assertThat(
        analysisService.getLastCVAnalysisMinute(appId, cvConfigId, LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE))
        .isEqualTo(100);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetLastWorkflowAnalysisMinute() {
    assertThat(analysisService.getLastWorkflowAnalysisMinute(
                   appId, stateExecutionId, LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE))
        .isEqualTo(-1);

    for (int i = 1; i <= 100; i++) {
      LogMLAnalysisRecord mlAnalysisRecord = LogMLAnalysisRecord.builder()
                                                 .accountId(accountId)
                                                 .stateExecutionId(stateExecutionId)
                                                 .logCollectionMinute(i)
                                                 .build();
      mlAnalysisRecord.setAnalysisStatus(LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE);
      wingsPersistence.save(mlAnalysisRecord);
    }

    assertThat(analysisService.getLastWorkflowAnalysisMinute(
                   appId, stateExecutionId, LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE))
        .isEqualTo(-1);
    assertThat(analysisService.getLastWorkflowAnalysisMinute(
                   appId, stateExecutionId, LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE))
        .isEqualTo(100);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetLastLogDataCollectedMinute() {
    String query = generateUuid();
    assertThat(analysisService.getLastLogDataCollectedMinute(query, appId, stateExecutionId, StateType.SUMO))
        .isEqualTo(-1);

    for (int i = 1; i <= 100; i++) {
      wingsPersistence.save(LogDataRecord.builder().stateExecutionId(stateExecutionId).logCollectionMinute(i).build());
    }

    assertThat(analysisService.getLastLogDataCollectedMinute(query, appId, stateExecutionId, StateType.SUMO))
        .isEqualTo(100);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testIsAnalysisPresent() {
    assertThat(analysisService.isAnalysisPresent(stateExecutionId, appId)).isFalse();
    wingsPersistence.save(LogMLAnalysisRecord.builder()
                              .accountId(accountId)
                              .stateExecutionId(stateExecutionId)
                              .logCollectionMinute(100)
                              .build());
    assertThat(analysisService.isAnalysisPresent(stateExecutionId, appId)).isTrue();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testCreateAndSaveSummary() {
    String query = generateUuid();
    String message = generateUuid();
    analysisService.createAndSaveSummary(StateType.SUMO, appId, stateExecutionId, query, message, accountId);
    LogMLAnalysisRecord logMLAnalysisRecord = wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority)
                                                  .filter(LogMLAnalysisRecordKeys.stateExecutionId, stateExecutionId)
                                                  .get();

    assertThat(logMLAnalysisRecord.getQuery()).isEqualTo(query);
    assertThat(logMLAnalysisRecord.getAnalysisSummaryMessage()).isEqualTo(message);
    assertThat(logMLAnalysisRecord.getStateType()).isEqualTo(StateType.SUMO);
    assertThat(logMLAnalysisRecord.getAccountId()).isEqualTo(accountId);
  }

  private void createLETaskForBackoffTest(int analysisMinute, int backoffCount) {
    LearningEngineAnalysisTask failedTask = LearningEngineAnalysisTask.builder()
                                                .state_execution_id(stateExecutionId + "-retry-1234")
                                                .analysis_minute(analysisMinute)
                                                .service_guard_backoff_count(backoffCount)
                                                .ml_analysis_type(MLAnalysisType.LOG_ML)
                                                .build();

    failedTask.setUuid("failedUUID");

    wingsPersistence.save(failedTask);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testIsAnalysisPresentForMinute_IsPresent() {
    Instant oldMinute = Instant.parse("2020-02-10T20:20:00.00Z");
    String cvConfigId = generateUuid();
    int minute = (int) TimeUnit.MILLISECONDS.toMinutes(oldMinute.toEpochMilli());
    LogMLAnalysisRecord oldLogAnalysisRecord = LogMLAnalysisRecord.builder()
                                                   .appId(appId)
                                                   .accountId(accountId)
                                                   .cvConfigId(cvConfigId)
                                                   .logCollectionMinute(minute)
                                                   .build();
    oldLogAnalysisRecord.setAnalysisStatus(LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
    wingsPersistence.save(oldLogAnalysisRecord);

    assertThat(analysisService.isAnalysisPresentForMinute(cvConfigId, minute, LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE))
        .isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testIsAnalysisPresentForMinute_IsPresentButDifferentStatus() {
    Instant oldMinute = Instant.parse("2020-02-10T20:20:00.00Z");
    String cvConfigId = generateUuid();
    int minute = (int) TimeUnit.MILLISECONDS.toMinutes(oldMinute.toEpochMilli());
    LogMLAnalysisRecord oldLogAnalysisRecord = LogMLAnalysisRecord.builder()
                                                   .appId(appId)
                                                   .accountId(accountId)
                                                   .cvConfigId(cvConfigId)
                                                   .logCollectionMinute(minute)
                                                   .build();
    oldLogAnalysisRecord.setAnalysisStatus(LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE);
    wingsPersistence.save(oldLogAnalysisRecord);

    assertThat(
        analysisService.isAnalysisPresentForMinute(cvConfigId, minute, LogMLAnalysisStatus.FEEDBACK_ANALYSIS_COMPLETE))
        .isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testIsAnalysisPresentForMinute_IsNotPresent() {
    Instant oldMinute = Instant.parse("2020-02-10T20:20:00.00Z");
    String cvConfigId = generateUuid();
    int minute = (int) TimeUnit.MILLISECONDS.toMinutes(oldMinute.toEpochMilli());
    assertThat(analysisService.isAnalysisPresentForMinute(cvConfigId, minute, LogMLAnalysisStatus.LE_ANALYSIS_COMPLETE))
        .isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetCollectednNodes_CompareCurrent() {
    Map<String, String> testNodes = new HashMap<>();
    testNodes.put("testNode1", "group1");
    testNodes.put("testNode2", "group1");

    Map<String, String> controlNodes = new HashMap<>();
    controlNodes.put("controlNode1", "group1");
    controlNodes.put("controlNode2", "group1");

    AnalysisContext context = AnalysisContext.builder()
                                  .comparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_CURRENT)
                                  .testNodes(testNodes)
                                  .controlNodes(controlNodes)
                                  .serviceId(serviceId)
                                  .stateExecutionId(stateExecutionId)
                                  .appId(appId)
                                  .build();
    wingsPersistence.save(context);

    Set<String> nodes = analysisService.getCollectedNodes(context, null);
    assertThat(nodes).isNotNull();
    assertThat(nodes.size()).isEqualTo(4);
    assertThat(nodes.containsAll(Arrays.asList("testNode1", "testNode2", "controlNode2", "controlNode2"))).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetCollectednNodes_ComparePreviousInspectHosts() {
    Map<String, String> testNodes = new HashMap<>();
    testNodes.put("testNode1", "group1");
    testNodes.put("testNode2", "group1");

    Map<String, String> controlNodes = new HashMap<>();
    controlNodes.put("controlNode1", "group1");
    controlNodes.put("controlNode2", "group1");

    AnalysisContext context = AnalysisContext.builder()
                                  .comparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS)
                                  .inspectHostsInLogs(true)
                                  .testNodes(testNodes)
                                  .controlNodes(controlNodes)
                                  .serviceId(serviceId)
                                  .stateExecutionId(stateExecutionId)
                                  .appId(appId)
                                  .build();
    wingsPersistence.save(context);

    Set<String> nodes = analysisService.getCollectedNodes(context, null);
    assertThat(nodes).isNotNull();
    assertThat(nodes.size()).isEqualTo(2);
    assertThat(nodes.containsAll(Arrays.asList("testNode1", "testNode2"))).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetCollectednNodes_ComparePreviousNoInspectHostsL0() {
    Map<String, String> testNodes = new HashMap<>();
    testNodes.put("testNode1", "group1");
    testNodes.put("testNode2", "group1");

    Map<String, String> controlNodes = new HashMap<>();
    testNodes.put("controlNode1", "group1");
    testNodes.put("controlNode2", "group1");

    AnalysisContext context = AnalysisContext.builder()
                                  .comparisonStrategy(AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS)
                                  .inspectHostsInLogs(false)
                                  .testNodes(testNodes)
                                  .controlNodes(controlNodes)
                                  .serviceId(serviceId)
                                  .stateExecutionId(stateExecutionId)
                                  .appId(appId)
                                  .build();
    wingsPersistence.save(context);
    for (int i = 0; i < 10; i++) {
      LogDataRecord record = LogDataRecord.builder()
                                 .logCollectionMinute(1)
                                 .stateExecutionId(stateExecutionId)
                                 .clusterLevel(ClusterLevel.H0)
                                 .host("host" + i)
                                 .build();
      wingsPersistence.save(record);
    }
    LogDataRecord record = LogDataRecord.builder()
                               .logCollectionMinute(1)
                               .stateExecutionId(stateExecutionId)
                               .clusterLevel(ClusterLevel.H0)
                               .host(NON_HOST_PREVIOUS_ANALYSIS)
                               .build();
    wingsPersistence.save(record);

    Set<String> nodes = analysisService.getCollectedNodes(context, ClusterLevel.L0);
    assertThat(nodes).isNotNull();
    assertThat(nodes.size()).isEqualTo(11);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void
  testsave24X7LogAnalysisRecords_checkIfTriggerLogAnalysisAlertIfNecessaryCalledWhenDisableLogmlNeuralNetIsEnabled()
      throws IOException, IllegalAccessException {
    Call<RestResponse<Boolean>> managerCallFeedbacks = mock(Call.class);
    when(managerCallFeedbacks.clone()).thenReturn(managerCallFeedbacks);
    when(managerCallFeedbacks.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(verificationManagerClient.isFeatureEnabled(FeatureName.DISABLE_SERVICEGUARD_LOG_ALERTS, accountId))
        .thenReturn(managerCallFeedbacks);

    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    LogsCVConfiguration logsCVConfiguration = new LogsCVConfiguration();
    logsCVConfiguration.setUuid(logMLAnalysisRecord.getCvConfigId());
    logsCVConfiguration.setAccountId(accountId);
    wingsPersistence.save(logsCVConfiguration);

    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).asList()).isEmpty();
    analysisService.save24X7LogAnalysisRecords(logMLAnalysisRecord.getAppId(), logMLAnalysisRecord.getCvConfigId(),
        logMLAnalysisRecord.getLogCollectionMinute(), logMLAnalysisRecord, Optional.empty(), Optional.of(false));
    verify(continuousVerificationService, times(1)).triggerLogAnalysisAlertIfNecessary(any(), any(), anyInt());
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void
  testsave24X7LogAnalysisRecords_checkIfTriggerLogAnalysisAlertIfNecessaryCalledWhenFeedbackAnalysisComplete()
      throws IOException {
    Call<RestResponse<Boolean>> managerCallFeedbacks = mock(Call.class);
    when(managerCallFeedbacks.clone()).thenReturn(managerCallFeedbacks);
    when(managerCallFeedbacks.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(verificationManagerClient.isFeatureEnabled(any(), any())).thenReturn(managerCallFeedbacks);

    File file = new File("270-verification/src/test/resources/elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    LogsCVConfiguration logsCVConfiguration = new LogsCVConfiguration();
    logsCVConfiguration.setUuid(logMLAnalysisRecord.getCvConfigId());
    wingsPersistence.save(logsCVConfiguration);

    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).asList()).isEmpty();
    analysisService.save24X7LogAnalysisRecords(logMLAnalysisRecord.getAppId(), logMLAnalysisRecord.getCvConfigId(),
        logMLAnalysisRecord.getLogCollectionMinute(), logMLAnalysisRecord, Optional.empty(), Optional.of(true));
    verify(continuousVerificationService, times(1)).triggerLogAnalysisAlertIfNecessary(any(), any(), anyInt());
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testsave24X7LogAnalysisRecords_checkIfTriggerLogAnalysisAlertIfNecessaryNotCalledWhenLeAnalysisComplete()
      throws IOException {
    Call<RestResponse<Boolean>> managerCallFeedbacks = mock(Call.class);
    when(managerCallFeedbacks.clone()).thenReturn(managerCallFeedbacks);
    when(managerCallFeedbacks.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(verificationManagerClient.isFeatureEnabled(any(), any())).thenReturn(managerCallFeedbacks);

    File file = new File("270-verification/src/test/resources//elk/logml_data_record.json");

    Gson gson = new Gson();
    LogMLAnalysisRecord logMLAnalysisRecord;
    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
      Type type = new TypeToken<LogMLAnalysisRecord>() {}.getType();
      logMLAnalysisRecord = gson.fromJson(br, type);
    }

    LogsCVConfiguration logsCVConfiguration = new LogsCVConfiguration();
    logsCVConfiguration.setUuid(logMLAnalysisRecord.getCvConfigId());
    wingsPersistence.save(logsCVConfiguration);

    assertThat(wingsPersistence.createQuery(LogMLAnalysisRecord.class, excludeAuthority).asList()).isEmpty();
    analysisService.save24X7LogAnalysisRecords(logMLAnalysisRecord.getAppId(), logMLAnalysisRecord.getCvConfigId(),
        logMLAnalysisRecord.getLogCollectionMinute(), logMLAnalysisRecord, Optional.empty(), Optional.of(false));
    verify(continuousVerificationService, times(0)).triggerLogAnalysisAlertIfNecessary(any(), any(), anyInt());
  }
}
