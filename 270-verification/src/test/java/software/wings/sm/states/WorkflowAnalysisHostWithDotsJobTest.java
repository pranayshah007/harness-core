/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.service.impl.analysis.AnalysisComparisonStrategy.COMPARE_WITH_CURRENT;
import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.VerificationBase;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.jobs.workflow.logs.WorkflowLogAnalysisJob;
import io.harness.jobs.workflow.logs.WorkflowLogClusterJob;
import io.harness.jobs.workflow.timeseries.WorkflowTimeSeriesAnalysisJob;
import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.service.intfc.LearningEngineService;
import io.harness.service.intfc.LogAnalysisService;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.AnalysisContext.AnalysisContextKeys;
import software.wings.service.impl.analysis.LogDataRecord;
import software.wings.service.impl.analysis.LogDataRecord.LogDataRecordKeys;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup;
import software.wings.service.impl.analysis.TimeSeriesMetricGroup.TimeSeriesMlAnalysisGroupInfo;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskKeys;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.sm.StateType;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import retrofit2.Call;
import retrofit2.Response;

public class WorkflowAnalysisHostWithDotsJobTest extends VerificationBase {
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
  private AnalysisContext logAnalysisContext;
  private WorkflowTimeSeriesAnalysisJob workflowTimeSeriesAnalysisJob;
  private WorkflowLogAnalysisJob workflowLogAnalysisJob;
  private WorkflowLogClusterJob workflowLogClusterJob;
  private AbstractAnalysisState abstractAnalysisState;
  private Set<String> controlNodes = Sets.newHashSet("harness.todolist.control1", "harness.todolist.control2");
  private Set<String> testNodes = Sets.newHashSet("harness.todolist.test1", "harness.todolist.test2");

  @Spy private VerificationManagerClientHelper managerClientHelper;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private LearningEngineService learningEngineService;

  @Inject private LogAnalysisService logAnalysisService;
  @Inject private DataStoreService dataStoreService;

  @Mock private VerificationManagerClient verificationManagerClient;

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

    logAnalysisContext = getAnalysisContext(StateType.SUMO);

    workflowLogAnalysisJob = new WorkflowLogAnalysisJob();
    workflowLogClusterJob = new WorkflowLogClusterJob();

    final Call<RestResponse<Boolean>> featureFlagTrueMock = mock(Call.class);
    when(featureFlagTrueMock.clone()).thenReturn(featureFlagTrueMock);
    when(featureFlagTrueMock.execute()).thenReturn(Response.success(new RestResponse<>(true)));

    final Call<RestResponse<Boolean>> featureFlagFalseMock = mock(Call.class);
    when(featureFlagFalseMock.clone()).thenReturn(featureFlagFalseMock);
    when(featureFlagFalseMock.execute()).thenReturn(Response.success(new RestResponse<>(false)));
    when(verificationManagerClient.isFeatureEnabled(FeatureName.DISABLE_LOGML_NEURAL_NET, accountId))
        .thenReturn(featureFlagFalseMock);
    doReturn(false).when(managerClientHelper).isFeatureFlagEnabled(FeatureName.OUTAGE_CV_DISABLE, accountId);

    final Call<RestResponse<List<String>>> managerVersionsCall = mock(Call.class);
    when(managerVersionsCall.clone()).thenReturn(managerVersionsCall);
    when(managerVersionsCall.execute()).thenReturn(Response.success(new RestResponse<>(null)));
    when(verificationManagerClient.getListOfPublishedVersions(accountId)).thenReturn(managerVersionsCall);
    when(verificationManagerClient.isStateValid(appId, stateExecutionId)).thenReturn(featureFlagTrueMock);

    abstractAnalysisState = mock(AbstractAnalysisState.class, CALLS_REAL_METHODS);
    FieldUtils.writeField(abstractAnalysisState, "wingsPersistence", wingsPersistence, true);

    logAnalysisContext = getAnalysisContext(StateType.SUMO);
    abstractAnalysisState.scheduleAnalysisCronJob(logAnalysisContext, delegateTaskId);

    workflowTimeSeriesAnalysisJob = new WorkflowTimeSeriesAnalysisJob();
    workflowLogAnalysisJob = new WorkflowLogAnalysisJob();
    workflowLogClusterJob = new WorkflowLogClusterJob();

    FieldUtils.writeField(learningEngineService, "managerClient", verificationManagerClient, true);
    FieldUtils.writeField(logAnalysisService, "learningEngineService", learningEngineService, true);
    FieldUtils.writeField(workflowTimeSeriesAnalysisJob, "learningEngineService", learningEngineService, true);
    FieldUtils.writeField(workflowTimeSeriesAnalysisJob, "managerClientHelper", managerClientHelper, true);
    FieldUtils.writeField(workflowTimeSeriesAnalysisJob, "verificationManagerClient", verificationManagerClient, true);

    FieldUtils.writeField(workflowLogAnalysisJob, "managerClient", verificationManagerClient, true);
    FieldUtils.writeField(workflowLogAnalysisJob, "analysisService", logAnalysisService, true);
    FieldUtils.writeField(workflowLogAnalysisJob, "learningEngineService", learningEngineService, true);
    FieldUtils.writeField(workflowLogAnalysisJob, "managerClientHelper", managerClientHelper, true);
    FieldUtils.writeField(workflowLogAnalysisJob, "verificationManagerClient", verificationManagerClient, true);
    FieldUtils.writeField(workflowLogAnalysisJob, "dataStoreService", dataStoreService, true);

    FieldUtils.writeField(workflowLogClusterJob, "managerClient", verificationManagerClient, true);
    FieldUtils.writeField(workflowLogClusterJob, "analysisService", logAnalysisService, true);
    FieldUtils.writeField(workflowLogClusterJob, "learningEngineService", learningEngineService, true);
    FieldUtils.writeField(workflowLogClusterJob, "managerClientHelper", managerClientHelper, true);
    FieldUtils.writeField(workflowLogClusterJob, "managerClient", verificationManagerClient, true);
    FieldUtils.writeField(workflowLogClusterJob, "dataStoreService", dataStoreService, true);

    wingsPersistence.save(TimeSeriesMetricGroup.builder()
                              .stateExecutionId(stateExecutionId)
                              .appId(appId)
                              .groups(Collections.singletonMap(DEFAULT_GROUP_NAME,
                                  TimeSeriesMlAnalysisGroupInfo.builder()
                                      .groupName(DEFAULT_GROUP_NAME)
                                      .mlAnalysisType(TimeSeriesMlAnalysisType.COMPARATIVE)
                                      .build()))
                              .build());
    // distinct query fails if the collection doesn't exists, as a workaround initialize logDataRecords collection
    wingsPersistence.save(LogDataRecord.builder().build());
  }

  private AnalysisContext getAnalysisContext(StateType appDynamics) {
    Map<String, String> control = new HashMap<>();
    controlNodes.forEach(host -> control.put(host, DEFAULT_GROUP_NAME));

    Map<String, String> test = new HashMap<>();
    testNodes.forEach(host -> test.put(host, DEFAULT_GROUP_NAME));
    return AnalysisContext.builder()
        .accountId(accountId)
        .appId(appId)
        .workflowId(workflowId)
        .workflowExecutionId(workflowExecutionId)
        .stateExecutionId(stateExecutionId)
        .serviceId(serviceId)
        .controlNodes(control)
        .testNodes(test)
        .isSSL(true)
        .appPort(1234)
        .comparisonStrategy(COMPARE_WITH_CURRENT)
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
  public void testLogClusterBump() {
    AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                          .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                                          .get();
    List<LogElement> logData = new ArrayList<>();
    controlNodes.forEach(host
        -> logData.add(LogElement.builder()
                           .query(query)
                           .clusterLabel("-3")
                           .host(host)
                           .count(0)
                           .logMessage("")
                           .timeStamp(0)
                           .logCollectionMinute(10)
                           .build()));
    testNodes.forEach(host
        -> logData.add(LogElement.builder()
                           .query(query)
                           .clusterLabel("-3")
                           .host(host)
                           .count(0)
                           .logMessage("")
                           .timeStamp(0)
                           .logCollectionMinute(10)
                           .build()));
    logAnalysisService.saveLogData(StateType.SUMO, accountId, appId, null, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, ClusterLevel.L0, delegateTaskId, logData);
    List<LogDataRecord> logDataRecords = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                             .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                                             .asList();
    assertThat(logDataRecords.size()).isEqualTo(controlNodes.size() + testNodes.size());
    logDataRecords.forEach(logDataRecord -> assertThat(logDataRecord.getClusterLevel()).isEqualTo(ClusterLevel.H0));
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    workflowLogClusterJob.handle(logAnalysisContext);
    analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                          .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                          .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    logDataRecords = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                         .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                         .asList();
    assertThat(logDataRecords.size()).isEqualTo(controlNodes.size() + testNodes.size());
    logDataRecords.forEach(logDataRecord -> assertThat(logDataRecord.getClusterLevel()).isEqualTo(ClusterLevel.H1));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testLogClusterCreateTasks() {
    AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                          .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                                          .get();
    List<LogElement> logData = new ArrayList<>();
    controlNodes.forEach(host -> {
      logData.add(LogElement.builder()
                      .query(query)
                      .clusterLabel("-3")
                      .host(host)
                      .count(0)
                      .logMessage("")
                      .timeStamp(0)
                      .logCollectionMinute(10)
                      .build());
      logData.add(LogElement.builder()
                      .query(query)
                      .clusterLabel("1")
                      .host(host)
                      .count(0)
                      .logMessage(generateUuid())
                      .timeStamp(0)
                      .logCollectionMinute(10)
                      .build());
    });
    logAnalysisService.saveLogData(StateType.SUMO, accountId, appId, null, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, ClusterLevel.L0, delegateTaskId, logData);
    List<LogDataRecord> logDataRecords = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                             .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                                             .asList();
    assertThat(logDataRecords.size()).isEqualTo(controlNodes.size() * 2);
    logDataRecords = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                         .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                         .filter(LogDataRecordKeys.clusterLevel, ClusterLevel.H0)
                         .asList();
    assertThat(logDataRecords.size()).isEqualTo(controlNodes.size());
    logDataRecords.forEach(logDataRecord -> assertThat(logDataRecord.getClusterLevel()).isEqualTo(ClusterLevel.H0));
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    workflowLogClusterJob.handle(logAnalysisContext);
    analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                          .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                          .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    final List<LearningEngineAnalysisTask> learningEngineAnalysisTasks =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .asList();

    assertThat(learningEngineAnalysisTasks.size()).isEqualTo(controlNodes.size());
    learningEngineAnalysisTasks.forEach(learningEngineAnalysisTask -> {
      assertThat(controlNodes).containsAll(learningEngineAnalysisTask.getControl_nodes());
      assertThat(learningEngineAnalysisTask.getControl_nodes().size()).isEqualTo(1);
      assertThat(learningEngineAnalysisTask.getTest_nodes()).isNull();
      assertThat(learningEngineAnalysisTask.getMl_analysis_type()).isEqualTo(MLAnalysisType.LOG_CLUSTER);
    });
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testLogAnalysisClusterBump() {
    AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                          .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                                          .get();
    List<LogElement> logData = new ArrayList<>();
    controlNodes.forEach(host
        -> logData.add(LogElement.builder()
                           .query(query)
                           .clusterLabel("-3")
                           .host(host)
                           .count(0)
                           .logMessage("")
                           .timeStamp(0)
                           .logCollectionMinute(10)
                           .build()));
    testNodes.forEach(host
        -> logData.add(LogElement.builder()
                           .query(query)
                           .clusterLabel("-3")
                           .host(host)
                           .count(0)
                           .logMessage("")
                           .timeStamp(0)
                           .logCollectionMinute(10)
                           .build()));

    logAnalysisService.saveLogData(StateType.SUMO, accountId, appId, null, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, ClusterLevel.L1, delegateTaskId, logData);
    List<LogDataRecord> logDataRecords = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                                             .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                                             .asList();
    assertThat(logDataRecords.size()).isEqualTo(controlNodes.size() + testNodes.size());
    logDataRecords.forEach(logDataRecord -> assertThat(logDataRecord.getClusterLevel()).isEqualTo(ClusterLevel.H1));
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    workflowLogAnalysisJob.handle(logAnalysisContext);
    analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                          .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                          .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    logDataRecords = wingsPersistence.createQuery(LogDataRecord.class, excludeAuthority)
                         .filter(LogDataRecordKeys.stateExecutionId, stateExecutionId)
                         .asList();
    assertThat(logDataRecords.size()).isEqualTo(controlNodes.size() + testNodes.size());
    logDataRecords.forEach(logDataRecord -> assertThat(logDataRecord.getClusterLevel()).isEqualTo(ClusterLevel.H2));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testLogAnalysisCreateClusterTasks() {
    AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                          .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                                          .get();
    List<LogElement> logData = new ArrayList<>();
    controlNodes.forEach(host -> {
      logData.add(LogElement.builder()
                      .query(query)
                      .clusterLabel("-3")
                      .host(host)
                      .count(0)
                      .logMessage("")
                      .timeStamp(0)
                      .logCollectionMinute(10)
                      .build());
      logData.add(LogElement.builder()
                      .query(query)
                      .clusterLabel("1")
                      .host(host)
                      .count(0)
                      .logMessage(generateUuid())
                      .timeStamp(0)
                      .logCollectionMinute(10)
                      .build());
    });

    testNodes.forEach(host -> {
      logData.add(LogElement.builder()
                      .query(query)
                      .clusterLabel("-3")
                      .host(host)
                      .count(0)
                      .logMessage("")
                      .timeStamp(0)
                      .logCollectionMinute(10)
                      .build());
      logData.add(LogElement.builder()
                      .query(query)
                      .clusterLabel("1")
                      .host(host)
                      .count(0)
                      .logMessage(generateUuid())
                      .timeStamp(0)
                      .logCollectionMinute(10)
                      .build());
    });
    logAnalysisService.saveLogData(StateType.SUMO, accountId, appId, null, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, ClusterLevel.L1, delegateTaskId, logData);
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    workflowLogAnalysisJob.handle(logAnalysisContext);
    analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                          .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                          .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    final List<LearningEngineAnalysisTask> learningEngineAnalysisTasks =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .asList();

    assertThat(learningEngineAnalysisTasks.size()).isEqualTo(1);
    final LearningEngineAnalysisTask learningEngineAnalysisTask = learningEngineAnalysisTasks.get(0);
    assertThat(learningEngineAnalysisTask.getTest_nodes()).isNull();
    assertThat(learningEngineAnalysisTask.getControl_nodes()).containsAll(controlNodes);
    assertThat(learningEngineAnalysisTask.getControl_nodes()).containsAll(testNodes);
    assertThat(learningEngineAnalysisTask.getMl_analysis_type()).isEqualTo(MLAnalysisType.LOG_CLUSTER);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testLogAnalysisCreateAnalysisTasks() {
    AnalysisContext analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                                          .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                                          .get();
    List<LogElement> logData = new ArrayList<>();
    controlNodes.forEach(host -> {
      logData.add(LogElement.builder()
                      .query(query)
                      .clusterLabel("-3")
                      .host(host)
                      .count(0)
                      .logMessage("")
                      .timeStamp(0)
                      .logCollectionMinute(10)
                      .build());
      logData.add(LogElement.builder()
                      .query(query)
                      .clusterLabel("1")
                      .host(host)
                      .count(0)
                      .logMessage(generateUuid())
                      .timeStamp(0)
                      .logCollectionMinute(10)
                      .build());
    });

    testNodes.forEach(host -> {
      logData.add(LogElement.builder()
                      .query(query)
                      .clusterLabel("-3")
                      .host(host)
                      .count(0)
                      .logMessage("")
                      .timeStamp(0)
                      .logCollectionMinute(10)
                      .build());
      logData.add(LogElement.builder()
                      .query(query)
                      .clusterLabel("1")
                      .host(host)
                      .count(0)
                      .logMessage(generateUuid())
                      .timeStamp(0)
                      .logCollectionMinute(10)
                      .build());
    });
    logAnalysisService.saveLogData(StateType.SUMO, accountId, appId, null, stateExecutionId, workflowId,
        workflowExecutionId, serviceId, ClusterLevel.L2, delegateTaskId, logData);
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    workflowLogAnalysisJob.handle(logAnalysisContext);
    analysisContext = wingsPersistence.createQuery(AnalysisContext.class, excludeAuthority)
                          .filter(AnalysisContextKeys.stateType, StateType.SUMO)
                          .get();
    assertThat(analysisContext.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);
    final List<LearningEngineAnalysisTask> learningEngineAnalysisTasks =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .asList();

    assertThat(learningEngineAnalysisTasks.size()).isEqualTo(1);
    final LearningEngineAnalysisTask learningEngineAnalysisTask = learningEngineAnalysisTasks.get(0);
    assertThat(learningEngineAnalysisTask.getControl_nodes()).isEqualTo(controlNodes);
    assertThat(learningEngineAnalysisTask.getTest_nodes()).isEqualTo(testNodes);
    assertThat(learningEngineAnalysisTask.getMl_analysis_type()).isEqualTo(MLAnalysisType.LOG_ML);
  }
}
