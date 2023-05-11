/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jobs;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.NANDAN;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;
import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.COMPARE_WITH_PREVIOUS;
import static software.wings.service.impl.analysis.MLAnalysisType.LOG_ML;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.VerificationBase;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.jobs.workflow.logs.WorkflowLogAnalysisJob;
import io.harness.jobs.workflow.logs.WorkflowLogClusterJob;
import io.harness.jobs.workflow.timeseries.WorkflowTimeSeriesAnalysisJob;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.LogAnalysisService;
import io.harness.service.intfc.TimeSeriesAnalysisService;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.analysis.TimeSeriesMLAnalysisRecord;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskKeys;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.service.intfc.verification.CVActivityLogService;
import software.wings.service.intfc.verification.CVActivityLogger;
import software.wings.sm.StateType;
import software.wings.verification.VerificationDataAnalysisResponse;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import retrofit2.Call;
import retrofit2.Response;

/**
 * Created by Pranjal on 09/18/2018
 */
public class WorkflowAnalysisJobTest extends VerificationBase {
  private String accountId;
  private String appId;
  private String stateExecutionId;
  private String workflowId;
  private String workflowExecutionId;
  private String serviceId;
  private String delegateTaskId;
  private String analysisServerConfigId;
  private String correlationId;
  private String preWorkflowExecutionId;
  private String query;
  private AnalysisContext timeSeriesAnalysisContext;
  private AnalysisContext logAnalysisContext;
  private WorkflowTimeSeriesAnalysisJob workflowTimeSeriesAnalysisJob;
  private WorkflowLogAnalysisJob workflowLogAnalysisJob;
  private WorkflowLogClusterJob workflowLogClusterJob;
  private long logAnalysisClusteringTestMinute = 5;
  private long logAnalysisMinute = 4;

  @Spy private VerificationManagerClientHelper managerClientHelper;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private LearningEngineService learningEngineService;

  @Mock private LogAnalysisService logAnalysisService;
  @Inject private DataStoreService dataStoreService;

  @Mock private VerificationManagerClient verificationManagerClient;
  @Mock private TimeSeriesAnalysisService timeSeriesAnalysisService;
  @Mock private JobExecutionContext timeSeriesContext;
  @Mock private JobExecutionContext logAnalysisExecutionContext;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private CVActivityLogService cvActivityLogService;

  private Map<String, TimeSeriesMlAnalysisGroupInfo> metricGroups;

  @Before
  public void setup() throws IllegalAccessException, IOException {
    MockitoAnnotations.initMocks(this);
    accountId = generateUuid();
    appId = generateUuid();
    stateExecutionId = generateUuid();
    workflowId = generateUuid();
    workflowExecutionId = generateUuid();
    serviceId = generateUuid();
    delegateTaskId = generateUuid();
    analysisServerConfigId = generateUuid();
    correlationId = generateUuid();
    preWorkflowExecutionId = generateUuid();
    query = generateUuid();

    timeSeriesAnalysisContext = getAnalysisContext(StateType.APP_DYNAMICS);
    wingsPersistence.save(timeSeriesAnalysisContext);

    logAnalysisContext = getAnalysisContext(StateType.SUMO);
    wingsPersistence.save(logAnalysisContext);

    JobDataMap jobDataMap = new JobDataMap();
    jobDataMap.put("jobParams", JsonUtils.asJson(timeSeriesAnalysisContext));
    jobDataMap.put("delegateTaskId", delegateTaskId);
    when(timeSeriesContext.getMergedJobDataMap()).thenReturn(jobDataMap);

    jobDataMap = new JobDataMap();
    jobDataMap.put("jobParams", JsonUtils.asJson(logAnalysisContext));
    jobDataMap.put("delegateTaskId", delegateTaskId);
    when(logAnalysisExecutionContext.getMergedJobDataMap()).thenReturn(jobDataMap);

    workflowTimeSeriesAnalysisJob = new WorkflowTimeSeriesAnalysisJob();
    workflowLogAnalysisJob = new WorkflowLogAnalysisJob();
    workflowLogClusterJob = new WorkflowLogClusterJob();

    final Call<RestResponse<Boolean>> featureFlagRestMock = mock(Call.class);
    when(featureFlagRestMock.clone()).thenReturn(featureFlagRestMock);
    when(featureFlagRestMock.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(verificationManagerClient.isFeatureEnabled(FeatureName.DISABLE_LOGML_NEURAL_NET, accountId))
        .thenReturn(featureFlagRestMock);
    doReturn(false).when(managerClientHelper).isFeatureFlagEnabled(FeatureName.OUTAGE_CV_DISABLE, accountId);

    final Call<RestResponse<List<String>>> managerVersionsCall = mock(Call.class);
    when(managerVersionsCall.clone()).thenReturn(managerVersionsCall);
    when(managerVersionsCall.execute()).thenReturn(Response.success(new RestResponse<>(null)));
    when(verificationManagerClient.getListOfPublishedVersions(accountId)).thenReturn(managerVersionsCall);

    final Call<RestResponse<Boolean>> stateValidRestMock = mock(Call.class);
    when(stateValidRestMock.clone()).thenReturn(stateValidRestMock);
    when(stateValidRestMock.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(verificationManagerClient.isStateValid(appId, stateExecutionId)).thenReturn(stateValidRestMock);
    FieldUtils.writeField(learningEngineService, "managerClient", verificationManagerClient, true);
    FieldUtils.writeField(workflowTimeSeriesAnalysisJob, "timeSeriesAnalysisService", timeSeriesAnalysisService, true);
    FieldUtils.writeField(workflowTimeSeriesAnalysisJob, "learningEngineService", learningEngineService, true);
    FieldUtils.writeField(workflowTimeSeriesAnalysisJob, "managerClientHelper", managerClientHelper, true);
    FieldUtils.writeField(workflowTimeSeriesAnalysisJob, "verificationManagerClient", verificationManagerClient, true);

    FieldUtils.writeField(workflowLogAnalysisJob, "managerClient", verificationManagerClient, true);
    FieldUtils.writeField(workflowLogAnalysisJob, "analysisService", logAnalysisService, true);
    FieldUtils.writeField(workflowLogAnalysisJob, "learningEngineService", learningEngineService, true);
    FieldUtils.writeField(workflowLogAnalysisJob, "managerClientHelper", managerClientHelper, true);
    FieldUtils.writeField(workflowLogAnalysisJob, "verificationManagerClient", verificationManagerClient, true);
    FieldUtils.writeField(workflowLogAnalysisJob, "dataStoreService", dataStoreService, true);
    FieldUtils.writeField(workflowLogAnalysisJob, "cvActivityLogService", cvActivityLogService, true);

    FieldUtils.writeField(workflowLogClusterJob, "managerClient", verificationManagerClient, true);
    FieldUtils.writeField(workflowLogClusterJob, "analysisService", logAnalysisService, true);
    FieldUtils.writeField(workflowLogClusterJob, "learningEngineService", learningEngineService, true);
    FieldUtils.writeField(workflowLogClusterJob, "managerClientHelper", managerClientHelper, true);
    FieldUtils.writeField(workflowLogClusterJob, "managerClient", verificationManagerClient, true);
    FieldUtils.writeField(workflowLogClusterJob, "dataStoreService", dataStoreService, true);

    metricGroups = new HashMap<>();
    metricGroups.put("tier3",
        TimeSeriesMlAnalysisGroupInfo.builder()
            .groupName("tier3")
            .mlAnalysisType(TimeSeriesMlAnalysisType.PREDICTIVE)
            .dependencyPath("tier3->tier2->tier1")
            .build());

    metricGroups.put("tier2",
        TimeSeriesMlAnalysisGroupInfo.builder()
            .groupName("tier2")
            .mlAnalysisType(TimeSeriesMlAnalysisType.PREDICTIVE)
            .dependencyPath("tier2->tier1")
            .build());

    metricGroups.put("tier1",
        TimeSeriesMlAnalysisGroupInfo.builder()
            .groupName("tier1")
            .mlAnalysisType(TimeSeriesMlAnalysisType.COMPARATIVE)
            .dependencyPath("tier1")
            .build());

    when(timeSeriesAnalysisService.getMetricGroups(appId, stateExecutionId)).thenReturn(metricGroups);

    for (String groupName : timeSeriesAnalysisService.getMetricGroups(appId, stateExecutionId).keySet()) {
      when(timeSeriesAnalysisService.getAnalysisMinute(timeSeriesAnalysisContext.getStateType(), appId,
               stateExecutionId, workflowExecutionId, serviceId, groupName, accountId))
          .thenReturn(NewRelicMetricDataRecord.builder().dataCollectionMinute(10).build());

      when(timeSeriesAnalysisService.getMinControlMinuteWithData(timeSeriesAnalysisContext.getStateType(), appId,
               serviceId, workflowId, timeSeriesAnalysisContext.getPrevWorkflowExecutionId(), groupName, accountId))
          .thenReturn(2);

      when(timeSeriesAnalysisService.getMaxControlMinuteWithData(timeSeriesAnalysisContext.getStateType(), appId,
               serviceId, workflowId, timeSeriesAnalysisContext.getPrevWorkflowExecutionId(), groupName, accountId))
          .thenReturn(18);
    }

    Set<String> collectedNodes = new HashSet<>();
    collectedNodes.addAll(logAnalysisContext.getTestNodes().keySet());
    when(logAnalysisService.getCollectionMinuteForLevel(
             query, appId, stateExecutionId, StateType.SUMO, ClusterLevel.L1, collectedNodes))
        .thenReturn(logAnalysisClusteringTestMinute);
    when(logAnalysisService.hasDataRecords(query, appId, stateExecutionId, StateType.SUMO, collectedNodes,
             ClusterLevel.L1, logAnalysisClusteringTestMinute))
        .thenReturn(true);
    when(logAnalysisService.getCollectionMinuteForLevel(
             query, appId, stateExecutionId, StateType.SUMO, ClusterLevel.L2, collectedNodes))
        .thenReturn(logAnalysisMinute);

    when(logAnalysisService.getCollectedNodes(any(), any())).thenReturn(Sets.newHashSet("test1", "test2"));
  }

  private AnalysisContext getAnalysisContext(StateType appDynamics) {
    return AnalysisContext.builder()
        .accountId(accountId)
        .appId(appId)
        .workflowId(workflowId)
        .workflowExecutionId(workflowExecutionId)
        .stateExecutionId(stateExecutionId)
        .serviceId(serviceId)
        .controlNodes(ImmutableMap.<String, String>builder()
                          .put("control1", DEFAULT_GROUP_NAME)
                          .put("control2", DEFAULT_GROUP_NAME)
                          .build())
        .testNodes(ImmutableMap.<String, String>builder()
                       .put("test1", DEFAULT_GROUP_NAME)
                       .put("test2", DEFAULT_GROUP_NAME)
                       .build())
        .isSSL(true)
        .appPort(1234)
        .comparisonStrategy(COMPARE_WITH_PREVIOUS)
        .inspectHostsInLogs(true)
        .timeDuration(15)
        .stateType(appDynamics)
        .analysisServerConfigId(analysisServerConfigId)
        .correlationId(correlationId)
        .prevWorkflowExecutionId(preWorkflowExecutionId)
        .query(query)
        .build();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testTimeSeriesCronEnabled() {
    workflowTimeSeriesAnalysisJob.handle(timeSeriesAnalysisContext);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority).asList().size())
        .isEqualTo(metricGroups.size());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testTimeSeriesAnalysis_LETaskPriority() {
    workflowTimeSeriesAnalysisJob.handle(timeSeriesAnalysisContext);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority).asList().size())
        .isEqualTo(metricGroups.size());
    List<LearningEngineAnalysisTask> analysisTasks =
        wingsPersistence.createAuthorizedQuery(LearningEngineAnalysisTask.class).asList();
    assertThat(analysisTasks).isNotEmpty();
    analysisTasks.forEach(task -> assertThat(task.getPriority()).isEqualTo(0));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testTimeSeriesAnalysisJobQueuePreviousWithPredictiveIterator() throws IOException {
    workflowTimeSeriesAnalysisJob.handle(timeSeriesAnalysisContext);
    final AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                                .filter(AnalysisContextKeys.stateType, StateType.APP_DYNAMICS)
                                                .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verifyTimeSeriesQueuedTasks();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testTimeSeriesAnalysisJobFailFast() throws Exception {
    FieldUtils.writeField(managerClientHelper, "managerClient", verificationManagerClient, true);

    final Call<RestResponse<Boolean>> featureFlagRestMock = mock(Call.class);
    when(featureFlagRestMock.clone()).thenReturn(featureFlagRestMock);
    when(featureFlagRestMock.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    // save an analysis record with the fail fast as true
    TimeSeriesMLAnalysisRecord mlAnalysisRecord = TimeSeriesMLAnalysisRecord.builder().build();
    mlAnalysisRecord.setShouldFailFast(true);
    mlAnalysisRecord.setFailFastErrorMsg("Failed due to the transaction T1 and metricName M1");
    mlAnalysisRecord.setStateExecutionId(stateExecutionId);
    mlAnalysisRecord.setAppId(appId);
    wingsPersistence.save(mlAnalysisRecord);
    when(timeSeriesAnalysisService.getFailFastAnalysisRecord(appId, stateExecutionId)).thenReturn(mlAnalysisRecord);
    when(verificationManagerClient.sendNotifyForVerificationState(any(), any(), any())).thenReturn(featureFlagRestMock);

    workflowTimeSeriesAnalysisJob.handle(timeSeriesAnalysisContext);
    final AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                                .filter(AnalysisContextKeys.stateType, StateType.APP_DYNAMICS)
                                                .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    ArgumentCaptor<VerificationDataAnalysisResponse> taskCaptor =
        ArgumentCaptor.forClass(VerificationDataAnalysisResponse.class);
    verify(managerClientHelper).notifyManagerForVerificationAnalysis(any(), taskCaptor.capture());
    assertThat(taskCaptor.getValue().getExecutionStatus().name()).isEqualTo(ExecutionStatus.ERROR.name());
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testTimeSeriesAnalysisJobHandleWhenControlNodesIsNull() {
    timeSeriesAnalysisContext.setControlNodes(null);
    workflowTimeSeriesAnalysisJob.handle(timeSeriesAnalysisContext);
    final AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                                .filter(AnalysisContextKeys.stateType, StateType.APP_DYNAMICS)
                                                .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    final List<LearningEngineAnalysisTask> analysisTasks =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority).asList();
    assertThat(analysisTasks.size()).isEqualTo(metricGroups.size());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testTimeSeriesAnalysisJobSuccess() throws IOException {
    final Call<RestResponse<Boolean>> stateValidRestMock = mock(Call.class);
    when(stateValidRestMock.clone()).thenReturn(stateValidRestMock);
    when(stateValidRestMock.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(verificationManagerClient.isStateValid(appId, stateExecutionId)).thenReturn(stateValidRestMock);

    workflowTimeSeriesAnalysisJob.handle(timeSeriesAnalysisContext);
    final AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                                .filter(AnalysisContextKeys.stateType, StateType.APP_DYNAMICS)
                                                .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testLogAnalysisCronEnabled() {
    workflowLogAnalysisJob.handle(logAnalysisContext);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority).count()).isEqualTo(2);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
                   .filter(LearningEngineAnalysisTaskKeys.ml_analysis_type, MLAnalysisType.LOG_CLUSTER)
                   .count())
        .isEqualTo(1);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
                   .filter(LearningEngineAnalysisTaskKeys.ml_analysis_type, MLAnalysisType.LOG_ML)
                   .count())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testLogAnalysisJobIterator() {
    workflowLogAnalysisJob.handle(logAnalysisContext);
    final AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                                .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                                                .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    verifyLogAnalysisQueuedTasks();
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testLogAnalysisJobSuccess() throws IOException {
    final Call<RestResponse<Boolean>> stateValidRestMock = mock(Call.class);
    when(stateValidRestMock.clone()).thenReturn(stateValidRestMock);
    when(stateValidRestMock.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(verificationManagerClient.isStateValid(appId, stateExecutionId)).thenReturn(stateValidRestMock);

    workflowLogAnalysisJob.handle(logAnalysisContext);
    final AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                                .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                                                .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testLogClusterCronEnabled() {
    workflowLogClusterJob.handle(logAnalysisContext);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority).asList()).isEmpty();
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testHandle_LogAnalysisIteratorWhenDisableLogmlNeuralNetIsEnabled() throws IOException {
    Set<String> collectedNodes = new HashSet<>();
    collectedNodes.addAll(logAnalysisContext.getTestNodes().keySet());
    when(logAnalysisService.getCollectionMinuteForLevel(
             query, appId, stateExecutionId, StateType.SUMO, ClusterLevel.L2, collectedNodes))
        .thenReturn(0L);
    final Call<RestResponse<Boolean>> featureFlagRestMock = mock(Call.class);
    when(featureFlagRestMock.clone()).thenReturn(featureFlagRestMock);
    when(featureFlagRestMock.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(verificationManagerClient.isFeatureEnabled(FeatureName.DISABLE_LOGML_NEURAL_NET, accountId))
        .thenReturn(featureFlagRestMock);
    workflowLogAnalysisJob.handle(logAnalysisContext);
    List<LearningEngineAnalysisTask> analysisTasks =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
            .filter(LearningEngineAnalysisTaskKeys.ml_analysis_type, LOG_ML)
            .asList();
    assertThat(analysisTasks.size()).isEqualTo(1);
    assertThat(analysisTasks.get(0).getFeedback_url()).isNotEmpty();
    assertThat(analysisTasks.get(0).getFeedback_url())
        .isEqualTo("/verification/logml/user-feedback"
            + "?accountId=" + accountId + "&appId=" + appId + "&serviceId=" + serviceId + "&workflowId=" + workflowId
            + "&workflowExecutionId=" + workflowExecutionId);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testHandle_LogAnalysisIteratorSendWhenDisableLogmlNeuralNetIsEnabledSendNotification()
      throws IOException, IllegalAccessException {
    FieldUtils.writeField(managerClientHelper, "managerClient", verificationManagerClient, true);
    FieldUtils.writeField(managerClientHelper, "cvActivityLogService", cvActivityLogService, true);
    when(logAnalysisService.isProcessingComplete(query, appId, stateExecutionId, StateType.SUMO, 15, 0, accountId))
        .thenReturn(true);

    final Call<RestResponse<Boolean>> featureFlagRestMock = mock(Call.class);
    when(featureFlagRestMock.clone()).thenReturn(featureFlagRestMock);
    when(featureFlagRestMock.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    when(verificationManagerClient.isFeatureEnabled(FeatureName.DISABLE_LOGML_NEURAL_NET, accountId))
        .thenReturn(featureFlagRestMock);
    when(verificationManagerClient.sendNotifyForVerificationState(any(), any(), any())).thenReturn(featureFlagRestMock);

    Call<RestResponse<List<String>>> versionsCall = mock(Call.class);
    when(versionsCall.clone()).thenReturn(versionsCall);
    when(versionsCall.execute()).thenReturn(Response.success(new RestResponse<>(Lists.newArrayList())));
    when(verificationManagerClient.getListOfPublishedVersions(anyString())).thenReturn(versionsCall);

    when(cvActivityLogService.getLoggerByStateExecutionId(anyString(), anyString()))
        .thenReturn(mock(CVActivityLogger.class));

    workflowLogAnalysisJob.handle(logAnalysisContext);

    final AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                                .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                                                .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    ArgumentCaptor<VerificationDataAnalysisResponse> taskCaptor =
        ArgumentCaptor.forClass(VerificationDataAnalysisResponse.class);
    verify(managerClientHelper).notifyManagerForVerificationAnalysis(any(), taskCaptor.capture());
    assertThat(taskCaptor.getValue().getExecutionStatus().name()).isEqualTo(ExecutionStatus.SUCCESS.name());
  }

  private void verifyTimeSeriesQueuedTasks() {
    final List<LearningEngineAnalysisTask> analysisTasks =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority).asList();
    assertThat(analysisTasks.size()).isEqualTo(metricGroups.size());

    for (int i = 0; i < metricGroups.size(); i++) {
      final String groupName = "tier" + (metricGroups.size() - i);
      assertThat(analysisTasks.get(i).getState_execution_id()).isEqualTo(stateExecutionId);
      assertThat(analysisTasks.get(i).getWorkflow_execution_id()).isEqualTo(workflowExecutionId);
      assertThat(analysisTasks.get(i).getService_id()).isEqualTo(serviceId);
      assertThat(analysisTasks.get(i).getGroup_name()).isEqualTo(groupName);
      assertThat(analysisTasks.get(i).getTime_series_ml_analysis_type())
          .isEqualTo(
              groupName.equals("tier1") ? TimeSeriesMlAnalysisType.COMPARATIVE : TimeSeriesMlAnalysisType.PREDICTIVE);
      assertThat(analysisTasks.get(i).getMl_analysis_type()).isEqualTo(MLAnalysisType.TIME_SERIES);
      assertThat(analysisTasks.get(i).getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
      assertThat(analysisTasks.get(i).getAppId()).isEqualTo(appId);
      assertThat(analysisTasks.get(i).is24x7Task()).isEqualTo(false);
      assertThat(analysisTasks.get(i).getTest_nodes())
          .isEqualTo(groupName.equals("tier1") ? Sets.newHashSet("test1", "test2") : Sets.newHashSet(groupName));
      assertThat(analysisTasks.get(i).getControl_nodes()).isEqualTo(Sets.newHashSet("control1", "control2"));
      assertThat(analysisTasks.get(i).getTest_input_url())
          .isEqualTo("/verification/timeseries/get-metrics?accountId=" + accountId + "&appId=" + appId
              + "&stateExecutionId=" + stateExecutionId + "&groupName=" + groupName + "&compareCurrent=true");
      assertThat(analysisTasks.get(i).getControl_input_url())
          .isEqualTo("/verification/timeseries/get-metrics?accountId=" + accountId + "&appId=" + appId
              + "&groupName=" + groupName + "&compareCurrent=false&workflowExecutionId=" + preWorkflowExecutionId);
      assertThat(analysisTasks.get(i).getAnalysis_save_url())
          .isEqualTo("/verification/timeseries/save-analysis?accountId=" + accountId + "&applicationId=" + appId
              + "&workflowExecutionId=" + workflowExecutionId + "&stateExecutionId=" + stateExecutionId
              + "&analysisMinute=10&taskId=" + analysisTasks.get(i).getUuid() + "&groupName=" + groupName
              + "&stateType=APP_DYNAMICS&baseLineExecutionId=" + preWorkflowExecutionId);
      assertThat(analysisTasks.get(i).getMetric_template_url())
          .isEqualTo("/verification/timeseries/get-metric-template?accountId=" + accountId + "&appId=" + appId
              + "&stateType=APP_DYNAMICS&stateExecutionId=" + stateExecutionId + "&serviceId=" + serviceId
              + "&groupName=" + groupName);
    }
  }

  private void verifyLogAnalysisQueuedTasks() {
    final List<LearningEngineAnalysisTask> analysisTasks =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority).asList();
    assertThat(analysisTasks.size()).isEqualTo(2);

    final LearningEngineAnalysisTask clusteringTask = analysisTasks.get(0);
    assertThat(clusteringTask.getState_execution_id()).isEqualTo(stateExecutionId);
    assertThat(clusteringTask.getQuery()).isEqualTo(Lists.newArrayList(query));
    assertThat(clusteringTask.getService_id()).isEqualTo(serviceId);
    assertThat(clusteringTask.getWorkflow_id()).isEqualTo(workflowId);
    assertThat(clusteringTask.getWorkflow_execution_id()).isEqualTo(workflowExecutionId);
    assertThat(clusteringTask.getAnalysis_minute()).isEqualTo(logAnalysisClusteringTestMinute);
    assertThat(clusteringTask.getMl_analysis_type()).isEqualTo(MLAnalysisType.LOG_CLUSTER);
    assertThat(clusteringTask.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    assertThat(clusteringTask.getControl_nodes()).isEqualTo(logAnalysisContext.getTestNodes().keySet());
    assertThat(clusteringTask.getTest_nodes()).isNull();
    assertThat(clusteringTask.getControl_input_url())
        .isEqualTo("/verification/logml/get-logs?accountId=" + accountId + "&workflowExecutionId=" + workflowExecutionId
            + "&compareCurrent=true&clusterLevel=L1&stateType=SUMO");
    assertThat(clusteringTask.getAnalysis_save_url())
        .isEqualTo("/verification/logml/save-logs?accountId=" + accountId + "&stateExecutionId=" + stateExecutionId
            + "&workflowId=" + workflowId + "&workflowExecutionId=" + workflowExecutionId + "&serviceId=" + serviceId
            + "&appId=" + appId + "&clusterLevel=L2&stateType=SUMO");

    final LearningEngineAnalysisTask analysisTask = analysisTasks.get(1);

    assertThat(analysisTask.getState_execution_id()).isEqualTo(stateExecutionId);
    assertThat(analysisTask.getQuery()).isEqualTo(Lists.newArrayList(query));
    assertThat(analysisTask.getService_id()).isEqualTo(serviceId);
    assertThat(analysisTask.getWorkflow_id()).isEqualTo(workflowId);
    assertThat(analysisTask.getWorkflow_execution_id()).isEqualTo(workflowExecutionId);
    assertThat(analysisTask.getMl_analysis_type()).isEqualTo(MLAnalysisType.LOG_ML);
    assertThat(analysisTask.getAnalysis_minute()).isEqualTo(logAnalysisMinute);
    assertThat(analysisTask.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    assertThat(analysisTask.getControl_nodes()).isEqualTo(logAnalysisContext.getControlNodes().keySet());
    assertThat(analysisTask.getTest_nodes()).isEqualTo(logAnalysisContext.getTestNodes().keySet());
    assertThat(analysisTask.getControl_input_url())
        .isEqualTo("/verification/logml/get-logs?accountId=" + accountId + "&clusterLevel=L2&workflowExecutionId="
            + preWorkflowExecutionId + "&compareCurrent=false&stateType=SUMO&timeDelta=0");
    assertThat(analysisTask.getTest_input_url())
        .isEqualTo("/verification/logml/get-logs?accountId=" + accountId
            + "&clusterLevel=L2&workflowExecutionId=" + workflowExecutionId + "&compareCurrent=true&stateType=SUMO");
    assertThat(analysisTask.getAnalysis_save_url())
        .isEqualTo("/verification/logml/save-analysis-records?accountId=" + accountId + "&applicationId=" + appId
            + "&stateExecutionId=" + stateExecutionId + "&workflowExecutionId=" + workflowExecutionId
            + "&logCollectionMinute=" + logAnalysisMinute + "&isBaselineCreated=true&taskId=" + analysisTask.getUuid()
            + "&stateType=SUMO&baseLineExecutionId=" + preWorkflowExecutionId);
  }
}
