/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.beans.DataSourceType.APP_DYNAMICS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.CVConstants;
import io.harness.cvng.analysis.beans.LogClusterDTO;
import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.cvng.analysis.entities.ClusteredLog;
import io.harness.cvng.analysis.entities.ClusteredLog.ClusteredLogKeys;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskKeys;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.LogClusterLearningEngineTask;
import io.harness.cvng.analysis.entities.TimeSeriesLearningEngineTask;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.LogRecord;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;

public class LogClusterServiceImplTest extends CvNextGenTestBase {
  private String serviceGuardVerificationTaskId;
  private String cvConfigId;
  @Mock LearningEngineTaskService fakeLearningEngineTaskService;
  @Inject LearningEngineTaskService learningEngineTaskService;
  @Inject HPersistence hPersistence;
  @Inject LogClusterServiceImpl logClusterService;
  @Inject CVConfigService cvConfigService;
  @Inject VerificationTaskService verificationTaskService;
  @Inject VerificationJobInstanceService verificationJobInstanceService;
  @Mock FeatureFlagService featureFlagService;
  private String verificationJobIdentifier;
  private Instant now;
  private String projectIdentifier;
  private String orgIdentifier;
  private BuilderFactory builderFactory;
  private String accountId;

  @Before
  public void setup() throws IllegalAccessException {
    now = Instant.parse("2020-07-27T10:44:11.000Z");
    builderFactory = BuilderFactory.builder().clock(Clock.fixed(now, ZoneOffset.UTC)).build();
    accountId = builderFactory.getContext().getAccountId();
    CVConfig cvConfig = createCVConfig();
    cvConfigService.save(cvConfig);
    verificationJobIdentifier = generateUuid();

    serviceGuardVerificationTaskId =
        verificationTaskService.getServiceGuardVerificationTaskId(cvConfig.getAccountId(), cvConfig.getUuid());
    cvConfigId = cvConfig.getUuid();
    projectIdentifier = generateUuid();
    orgIdentifier = generateUuid();
    when(featureFlagService.isFeatureFlagEnabled(
             accountId, FeatureName.CV_USE_SEPARATE_LE_TASK_TYPE_FOR_LOG_CLUSTERING.name()))
        .thenReturn(false);
    FieldUtils.writeField(logClusterService, "featureFlagService", featureFlagService, true);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testScheduleL1ClusteringTasks_l1Cluster() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    createLogDataRecords(5, start, end);
    AnalysisInput input = AnalysisInput.builder()
                              .accountId(accountId)
                              .verificationTaskId(serviceGuardVerificationTaskId)
                              .startTime(start)
                              .endTime(end)
                              .build();
    logClusterService.scheduleL1ClusteringTasks(input, true);

    List<LearningEngineTask> tasks =
        hPersistence.createQuery(LearningEngineTask.class, excludeAuthority)
            .filter(LearningEngineTaskKeys.verificationTaskId, serviceGuardVerificationTaskId)
            .asList();
    assertThat(tasks.size()).isEqualTo(5);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testScheduleServiceGuardL2ClusteringTask_l2Cluster() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    List<ClusteredLog> logRecords = createClusteredLogRecords(serviceGuardVerificationTaskId, 5, start, end);
    hPersistence.save(logRecords);
    AnalysisInput input = AnalysisInput.builder()
                              .accountId(accountId)
                              .verificationTaskId(serviceGuardVerificationTaskId)
                              .startTime(start)
                              .endTime(end)
                              .build();
    logClusterService.scheduleServiceGuardL2ClusteringTask(input);

    List<LearningEngineTask> tasks =
        hPersistence.createQuery(LearningEngineTask.class, excludeAuthority)
            .filter(LearningEngineTaskKeys.verificationTaskId, serviceGuardVerificationTaskId)
            .asList();
    assertThat(tasks.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testScheduleDeploymentL2ClusteringTask_emptyClusteredLogs() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    VerificationJobInstance verificationJobInstance = newVerificationJobInstanceDTO();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);
    AnalysisInput input = AnalysisInput.builder()
                              .accountId(accountId)
                              .verificationTaskId(verificationTaskId)
                              .startTime(start)
                              .endTime(end)
                              .build();
    logClusterService.scheduleDeploymentL2ClusteringTask(input);
    List<LearningEngineTask> tasks = hPersistence.createQuery(LearningEngineTask.class, excludeAuthority)
                                         .filter(LearningEngineTaskKeys.verificationTaskId, verificationTaskId)
                                         .asList();
    assertThat(tasks.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testScheduleDeploymentL2ClusteringTask_validClustedLogsCanary() {
    Instant start = now.truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(15, ChronoUnit.MINUTES);
    VerificationJobInstance verificationJobInstance =
        builderFactory.verificationJobInstanceBuilder()
            .resolvedJob(builderFactory.canaryVerificationJobBuilder().build())
            .build();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    VerificationJobInstance instance = hPersistence.get(VerificationJobInstance.class, verificationJobInstanceId);
    hPersistence.save(instance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    List<ClusteredLog> logRecords = createClusteredLogRecords(verificationTaskId, 5, start, end);
    hPersistence.save(logRecords);
    AnalysisInput input = AnalysisInput.builder()
                              .accountId(accountId)
                              .verificationTaskId(verificationTaskId)
                              .startTime(start)
                              .endTime(end)
                              .build();
    logClusterService.scheduleDeploymentL2ClusteringTask(input);
    List<LearningEngineTask> tasks = hPersistence.createQuery(LearningEngineTask.class, excludeAuthority)
                                         .filter(LearningEngineTaskKeys.verificationTaskId, verificationTaskId)
                                         .asList();
    assertThat(tasks.size()).isEqualTo(1);
    LogClusterLearningEngineTask logCanaryAnalysisLearningEngineTask = (LogClusterLearningEngineTask) tasks.get(0);
    assertThat(logCanaryAnalysisLearningEngineTask.getTestDataUrl())
        .isEqualTo(CVConstants.SERVICE_BASE_URL + "/log-cluster/test-data?verificationTaskId=" + verificationTaskId
            + "&startTime=1595845920000&endTime=1595847540000&clusterLevel=L2");
    assertThat(logCanaryAnalysisLearningEngineTask.getVerificationTaskId()).isEqualTo(verificationTaskId);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testScheduleDeploymentL2ClusteringTask_validClustedLogsTest() {
    Instant start = now.truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(15, ChronoUnit.MINUTES);
    VerificationJob verificationJob = builderFactory.testVerificationJobBuilder().build();
    VerificationJobInstance verificationJobInstance = newVerificationJobInstanceDTO();
    String verificationJobInstanceId = verificationJobInstanceService.create(verificationJobInstance);
    VerificationJobInstance instance = hPersistence.get(VerificationJobInstance.class, verificationJobInstanceId);
    instance.setResolvedJob(verificationJob);
    hPersistence.save(instance);
    String verificationTaskId = verificationTaskService.createDeploymentVerificationTask(
        accountId, cvConfigId, verificationJobInstanceId, APP_DYNAMICS);

    List<ClusteredLog> logRecords = createClusteredLogRecords(verificationTaskId, 5, start, end);
    hPersistence.save(logRecords);
    AnalysisInput input = AnalysisInput.builder()
                              .accountId(accountId)
                              .verificationTaskId(verificationTaskId)
                              .startTime(start)
                              .endTime(end)
                              .build();
    logClusterService.scheduleDeploymentL2ClusteringTask(input);
    List<LearningEngineTask> tasks = hPersistence.createQuery(LearningEngineTask.class, excludeAuthority)
                                         .filter(LearningEngineTaskKeys.verificationTaskId, verificationTaskId)
                                         .asList();
    assertThat(tasks.size()).isEqualTo(1);
    LogClusterLearningEngineTask testLogClusterLearningEngineTask = (LogClusterLearningEngineTask) tasks.get(0);
    assertThat(testLogClusterLearningEngineTask.getTestDataUrl()).isNotEmpty();
    assertThat(testLogClusterLearningEngineTask.getVerificationTaskId()).isEqualTo(verificationTaskId);
  }
  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testScheduleServiceGuardL2ClusteringTask_l2ClusterNoL1Records() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    AnalysisInput input = AnalysisInput.builder()
                              .accountId(accountId)
                              .verificationTaskId(serviceGuardVerificationTaskId)
                              .startTime(start)
                              .endTime(end)
                              .build();
    logClusterService.scheduleServiceGuardL2ClusteringTask(input);

    List<LearningEngineTask> tasks =
        hPersistence.createQuery(LearningEngineTask.class, excludeAuthority)
            .filter(LearningEngineTaskKeys.verificationTaskId, serviceGuardVerificationTaskId)
            .asList();
    assertThat(tasks.size()).isNotEqualTo(0);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetTaskStatus() throws Exception {
    FieldUtils.writeField(logClusterService, "learningEngineTaskService", fakeLearningEngineTaskService, true);
    Set<String> taskIds = new HashSet<>();
    taskIds.add("task1");
    taskIds.add("task2");
    logClusterService.getTaskStatus(taskIds);

    Mockito.verify(fakeLearningEngineTaskService).getTaskStatus(taskIds);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetDataForLogCluster_l1() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    createLogDataRecords(5, start, end);
    List<LogClusterDTO> recordsTobeClustered = logClusterService.getDataForLogCluster(
        serviceGuardVerificationTaskId, start, start.plus(Duration.ofMinutes(1)), "host-0", LogClusterLevel.L1);

    assertThat(recordsTobeClustered).isNotNull();
    assertThat(recordsTobeClustered.size()).isEqualTo(5);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetDataForLogCluster_l2() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    List<ClusteredLog> logRecords = createClusteredLogRecords(serviceGuardVerificationTaskId, 5, start, end);
    hPersistence.save(logRecords);
    List<LogClusterDTO> logClusters =
        logClusterService.getDataForLogCluster(serviceGuardVerificationTaskId, start, end, null, LogClusterLevel.L2);

    assertThat(logClusters).isNotNull();
    assertThat(logClusters.size()).isEqualTo(25);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testSaveClusteredData() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);

    List<LogClusterDTO> clusterDTOList = buildLogClusterDtos(5, start, end);
    LearningEngineTask taskToSave = TimeSeriesLearningEngineTask.builder().build();
    taskToSave.setUuid(generateUuid());
    taskToSave.setAccountId(accountId);
    taskToSave.setVerificationTaskId(serviceGuardVerificationTaskId);
    taskToSave.setAnalysisStartTime(Instant.parse("2020-07-27T10:45:00.000Z"));
    taskToSave.setAnalysisEndTime(Instant.parse("2020-07-27T10:50:00.000Z"));
    taskToSave.setPickedAt(Instant.parse("2020-07-27T10:53:00.000Z"));
    learningEngineTaskService.createLearningEngineTask(taskToSave);
    logClusterService.saveClusteredData(
        clusterDTOList, serviceGuardVerificationTaskId, end, taskToSave.getUuid(), LogClusterLevel.L2);
    List<ClusteredLog> clusteredLogList =
        hPersistence.createQuery(ClusteredLog.class)
            .filter(ClusteredLogKeys.verificationTaskId, serviceGuardVerificationTaskId)
            .asList();
    assertThat(clusteredLogList).isNotNull();
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetL1TestVerificationTestData_noBaselineData() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    String verificationTaskId = generateUuid();
    List<ClusteredLog> logRecords = createClusteredLogRecords(verificationTaskId, 5, start, end);
    hPersistence.save(logRecords);
    List<LogClusterDTO> logClusters =
        logClusterService.getL1TestVerificationTestData(null, verificationTaskId, start, end);

    assertThat(logClusters).isNotNull();
    assertThat(logClusters.size()).isEqualTo(25);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetL1TestVerificationTestData_withBaseline() {
    Instant start = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    Instant end = start.plus(5, ChronoUnit.MINUTES);
    String verificationTaskId = generateUuid();
    String baselineVerificationTaskId = generateUuid();
    List<ClusteredLog> logRecords = createClusteredLogRecords(verificationTaskId, 5, start, end);
    List<ClusteredLog> baselineLogRecords = createClusteredLogRecords(baselineVerificationTaskId, 3, start, end);
    hPersistence.save(logRecords);
    hPersistence.save(baselineLogRecords);
    List<LogClusterDTO> logClusters =
        logClusterService.getL1TestVerificationTestData(baselineVerificationTaskId, verificationTaskId, start, end);

    assertThat(logClusters).isNotNull();
    assertThat(logClusters.size()).isEqualTo(40);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateLogClusterLearningEngineTask_notDeploymentTask_FFIsDisabled() {
    when(featureFlagService.isFeatureFlagEnabled(
             accountId, FeatureName.CV_USE_SEPARATE_LE_TASK_TYPE_FOR_LOG_CLUSTERING.name()))
        .thenReturn(false);
    LogClusterLearningEngineTask logClusterLearningEngineTask = logClusterService.createLogClusterLearningEngineTask(
        accountId, "verificationTaskId", Instant.now(), Instant.now(), LogClusterLevel.L2, "testDataUrl", false);
    assertThat(logClusterLearningEngineTask.getType()).isEqualTo(LearningEngineTaskType.LOG_CLUSTER);
    assertThat(logClusterLearningEngineTask.getAnalysisType()).isEqualTo(LearningEngineTaskType.LOG_CLUSTER);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateLogClusterLearningEngineTask_notDeploymentTask_FFIsEnabled() {
    when(featureFlagService.isFeatureFlagEnabled(
             accountId, FeatureName.CV_USE_SEPARATE_LE_TASK_TYPE_FOR_LOG_CLUSTERING.name()))
        .thenReturn(true);
    LogClusterLearningEngineTask logClusterLearningEngineTask = logClusterService.createLogClusterLearningEngineTask(
        accountId, "verificationTaskId", Instant.now(), Instant.now(), LogClusterLevel.L2, "testDataUrl", false);
    assertThat(logClusterLearningEngineTask.getType()).isEqualTo(LearningEngineTaskType.LOG_CLUSTER);
    assertThat(logClusterLearningEngineTask.getAnalysisType()).isEqualTo(LearningEngineTaskType.LOG_CLUSTER);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateLogClusterLearningEngineTask_isDeploymentTask_FFIsEnabled() {
    when(featureFlagService.isFeatureFlagEnabled(
             accountId, FeatureName.CV_USE_SEPARATE_LE_TASK_TYPE_FOR_LOG_CLUSTERING.name()))
        .thenReturn(true);
    LogClusterLearningEngineTask logClusterLearningEngineTask = logClusterService.createLogClusterLearningEngineTask(
        accountId, "verificationTaskId", Instant.now(), Instant.now(), LogClusterLevel.L2, "testDataUrl", true);
    assertThat(logClusterLearningEngineTask.getType()).isEqualTo(LearningEngineTaskType.CV_LOG_CLUSTER);
    assertThat(logClusterLearningEngineTask.getAnalysisType()).isEqualTo(LearningEngineTaskType.CV_LOG_CLUSTER);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testCreateLogClusterLearningEngineTask_isDeploymentTask_FFIsDisabled() {
    when(featureFlagService.isFeatureFlagEnabled(
             accountId, FeatureName.CV_USE_SEPARATE_LE_TASK_TYPE_FOR_LOG_CLUSTERING.name()))
        .thenReturn(false);
    LogClusterLearningEngineTask logClusterLearningEngineTask = logClusterService.createLogClusterLearningEngineTask(
        accountId, "verificationTaskId", Instant.now(), Instant.now(), LogClusterLevel.L2, "testDataUrl", true);
    assertThat(logClusterLearningEngineTask.getType()).isEqualTo(LearningEngineTaskType.LOG_CLUSTER);
    assertThat(logClusterLearningEngineTask.getAnalysisType()).isEqualTo(LearningEngineTaskType.LOG_CLUSTER);
  }

  private List<LogClusterDTO> buildLogClusterDtos(int numHosts, Instant startTime, Instant endTime) {
    List<ClusteredLog> clusteredLogs =
        createClusteredLogRecords(serviceGuardVerificationTaskId, numHosts, startTime, endTime);
    List<LogClusterDTO> clusterDTOList = new ArrayList<>();
    clusteredLogs.forEach(logObject -> clusterDTOList.add(logObject.toDTO()));
    return clusterDTOList;
  }

  private List<ClusteredLog> createClusteredLogRecords(
      String verificationTaskId, int numHosts, Instant startTime, Instant endTime) {
    List<ClusteredLog> logRecords = new ArrayList<>();
    for (int i = 0; i < numHosts; i++) {
      Instant timestamp = startTime;
      while (timestamp.isBefore(endTime)) {
        ClusteredLog record = ClusteredLog.builder()
                                  .verificationTaskId(verificationTaskId)
                                  .timestamp(timestamp)
                                  .host("host-" + i)
                                  .log("sample log record")
                                  .clusterLabel("1")
                                  .clusterCount(4)
                                  .clusterLevel(LogClusterLevel.L1)
                                  .build();
        logRecords.add(record);
        timestamp = timestamp.plus(1, ChronoUnit.MINUTES);
      }
    }

    return logRecords;
  }

  private void createLogDataRecords(int numHosts, Instant startTime, Instant endTime) {
    List<LogRecord> logRecords = new ArrayList<>();
    for (int i = 0; i < numHosts; i++) {
      Instant timestamp = startTime;
      while (timestamp.isBefore(endTime)) {
        LogRecord record = LogRecord.builder()
                               .verificationTaskId(serviceGuardVerificationTaskId)
                               .timestamp(timestamp)
                               .host("host-" + i)
                               .log("sample log record")
                               .build();
        logRecords.add(record);
        timestamp = timestamp.plus(1, ChronoUnit.MINUTES);
      }
    }
    hPersistence.save(logRecords);
  }

  private CVConfig createCVConfig() {
    return builderFactory.splunkCVConfigBuilder().build();
  }
  private VerificationJobInstance newVerificationJobInstanceDTO() {
    return builderFactory.verificationJobInstanceBuilder().build();
  }
}
