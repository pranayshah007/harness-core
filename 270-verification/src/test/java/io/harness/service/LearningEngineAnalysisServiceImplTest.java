/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service;

import static io.harness.logging.Misc.replaceDotWithUnicode;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.NANDAN;
import static io.harness.rule.OwnerRule.PARNIAN;
import static io.harness.rule.OwnerRule.PRAVEEN;
import static io.harness.rule.OwnerRule.SOWMYA;

import static software.wings.service.impl.newrelic.LearningEngineAnalysisTask.TIME_SERIES_ANALYSIS_TASK_TIME_OUT;

import static org.apache.cxf.ws.addressing.ContextUtils.generateUUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.VerificationBase;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.rule.Owner;
import io.harness.service.intfc.LearningEngineService;
import io.harness.version.ServiceApiVersion;

import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.analysis.SupervisedTrainingStatus;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskKeys;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask.LearningEngineExperimentalAnalysisTaskKeys;
import software.wings.service.impl.newrelic.MLExperiments;
import software.wings.service.intfc.analysis.ClusterLevel;
import software.wings.sm.ContextElement;
import software.wings.sm.StateExecutionInstance;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class LearningEngineAnalysisServiceImplTest extends VerificationBase {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private LearningEngineService learningEngineService;
  @Mock private VerificationManagerClientHelper managerClientHelper;

  private final String stateExecutionId = "stateExecutionId";
  private final String workflowExecutionId = "workflowExecutionId";
  private final String appId = "appId";
  private final String taskId = "taskId";
  private final String cvConfigId = "cvConfigId";
  private final ServiceApiVersion serviceApiVersion = ServiceApiVersion.V1;
  private final String experimentName = "ts";
  private final String serviceId = "serviceId";
  private final String accountId = "accountId";

  private LearningEngineAnalysisTask analysisTask;
  private LearningEngineExperimentalAnalysisTask experimentalAnalysisTask;

  private LearningEngineAnalysisTask getLEAnalysisTask() {
    return LearningEngineAnalysisTask.builder()
        .state_execution_id(stateExecutionId)
        .workflow_execution_id(workflowExecutionId)
        .executionStatus(ExecutionStatus.QUEUED)
        .analysis_minute(0)
        .build();
  }

  private LearningEngineExperimentalAnalysisTask getLEExperimentalAnalysisTask() {
    return LearningEngineExperimentalAnalysisTask.builder()
        .state_execution_id(stateExecutionId)
        .workflow_execution_id(workflowExecutionId)
        .executionStatus(ExecutionStatus.QUEUED)
        .analysis_minute(0)
        .version(serviceApiVersion)
        .build();
  }

  private List<MLAnalysisType> getMLAnalysisTypeList() {
    return Lists.newArrayList(MLAnalysisType.LOG_CLUSTER, MLAnalysisType.LOG_ML, MLAnalysisType.TIME_SERIES,
        MLAnalysisType.FEEDBACK_ANALYSIS);
  }

  private void setUpCreateMLAnalysisTaks() {
    List<MLAnalysisType> mlAnalysisTypeList = getMLAnalysisTypeList();
    for (MLAnalysisType mlAnalysisType : mlAnalysisTypeList) {
      LearningEngineAnalysisTask learningEngineAnalysisTask = LearningEngineAnalysisTask.builder()
                                                                  .state_execution_id(generateUUID())
                                                                  .workflow_execution_id(generateUUID())
                                                                  .executionStatus(ExecutionStatus.QUEUED)
                                                                  .ml_analysis_type(mlAnalysisType)
                                                                  .is24x7Task(true)
                                                                  .build();
      learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);
    }

    for (MLAnalysisType mlAnalysisType : mlAnalysisTypeList) {
      LearningEngineAnalysisTask learningEngineAnalysisTask = LearningEngineAnalysisTask.builder()
                                                                  .state_execution_id(generateUUID())
                                                                  .workflow_execution_id(generateUUID())
                                                                  .executionStatus(ExecutionStatus.QUEUED)
                                                                  .ml_analysis_type(mlAnalysisType)
                                                                  .is24x7Task(false)
                                                                  .build();
      learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);
    }
  }

  @Before
  public void setUp() throws IllegalAccessException {
    analysisTask = getLEAnalysisTask();
    experimentalAnalysisTask = getLEExperimentalAnalysisTask();

    FieldUtils.writeField(learningEngineService, "managerClientHelper", managerClientHelper, true);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testAddLearningEngineAnalysisTask_ForWorkflow() {
    boolean result = learningEngineService.addLearningEngineAnalysisTask(analysisTask);
    assertThat(result).isTrue();

    LearningEngineAnalysisTask savedTask =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .get();

    assertThat(savedTask).isNotNull();
    assertThat(savedTask.getWorkflow_execution_id()).isEqualTo(workflowExecutionId);
    assertThat(savedTask.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    assertThat(savedTask.getCluster_level()).isEqualTo(ClusterLevel.HF.getLevel());
    assertThat(savedTask.is24x7Task()).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testAddLearningEngineAnalysisTask_ForWorkflowWithTags() {
    analysisTask.setTag("default");

    boolean result = learningEngineService.addLearningEngineAnalysisTask(analysisTask);
    assertThat(result).isTrue();

    LearningEngineAnalysisTask savedTask =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .get();

    assertThat(savedTask).isNotNull();
    assertThat(savedTask.getWorkflow_execution_id()).isEqualTo(workflowExecutionId);
    assertThat(savedTask.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    assertThat(savedTask.getCluster_level()).isEqualTo(ClusterLevel.HF.getLevel());
    assertThat(savedTask.is24x7Task()).isFalse();
    assertThat(savedTask.getTag()).isEqualTo("default");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testAddLearningEngineAnalysisTask_ForServiceGuard() {
    analysisTask.set24x7Task(true);

    boolean result = learningEngineService.addLearningEngineAnalysisTask(analysisTask);
    assertThat(result).isTrue();

    LearningEngineAnalysisTask savedTask =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .get();

    assertThat(savedTask).isNotNull();
    assertThat(savedTask.getWorkflow_execution_id()).isEqualTo(workflowExecutionId);
    assertThat(savedTask.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    assertThat(savedTask.getCluster_level()).isEqualTo(ClusterLevel.HF.getLevel());
    assertThat(savedTask.is24x7Task()).isTrue();
  }

  @Test
  @Owner(developers = PARNIAN)
  @Category(UnitTests.class)
  public void testAddLearningEngineAnalysisTask_AlreadyQueued() {
    int numOfTasks = 5;
    for (int i = 0; i < numOfTasks; i++) {
      LearningEngineAnalysisTask learningEngineAnalysisTask = LearningEngineAnalysisTask.builder()
                                                                  .state_execution_id(stateExecutionId)
                                                                  .workflow_execution_id(workflowExecutionId)
                                                                  .executionStatus(ExecutionStatus.QUEUED)
                                                                  .analysis_minute(0)
                                                                  .build();
      learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);
    }

    List<LearningEngineAnalysisTask> learningEngineAnalysisTasks =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority).asList();
    assertThat(learningEngineAnalysisTasks).hasSize(1);
    LearningEngineAnalysisTask analysisTask = learningEngineAnalysisTasks.get(0);
    assertThat(analysisTask.getWorkflow_execution_id()).isEqualTo(workflowExecutionId);
    assertThat(analysisTask.getState_execution_id()).isEqualTo(stateExecutionId);
    assertThat(analysisTask.getAnalysis_minute()).isEqualTo(0);
    assertThat(analysisTask.getRetry()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PARNIAN)
  @Category(UnitTests.class)
  public void testAddLearningEngineAnalysisTask_QueueWithTimeOut() throws InterruptedException {
    LearningEngineAnalysisTask.TIME_SERIES_ANALYSIS_TASK_TIME_OUT = TimeUnit.SECONDS.toMillis(5);
    long startTime = System.currentTimeMillis();
    int numOfTasks = 10;
    for (int i = 0; i < numOfTasks; i++) {
      LearningEngineAnalysisTask learningEngineAnalysisTask = LearningEngineAnalysisTask.builder()
                                                                  .state_execution_id(generateUUID())
                                                                  .workflow_execution_id(generateUUID())
                                                                  .executionStatus(ExecutionStatus.RUNNING)
                                                                  .build();
      learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);
    }
    Thread.sleep(TimeUnit.SECONDS.toMillis(10));

    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class).count()).isEqualTo(numOfTasks);

    for (int i = 1; i <= numOfTasks; i++) {
      LearningEngineAnalysisTask analysisTask = learningEngineService.getNextLearningEngineAnalysisTask(
          ServiceApiVersion.V1, Optional.empty(), Optional.empty());
      assertThat(analysisTask.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);

      assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
                     .field(LearningEngineAnalysisTask.LAST_UPDATED_AT_KEY2)
                     .greaterThan(startTime)
                     .filter("retry", 0)
                     .asList()
                     .size())
          .isEqualTo(numOfTasks - i);
      assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
                     .field(LearningEngineAnalysisTask.LAST_UPDATED_AT_KEY2)
                     .greaterThan(startTime)
                     .filter("retry", 1)
                     .asList()
                     .size())
          .isEqualTo(i);
    }
  }

  @Test
  @Owner(developers = PARNIAN)
  @Category(UnitTests.class)
  public void testAddLearningEngineAnalysisTask_AlreadyQueuedForMinute() {
    LearningEngineAnalysisTask learningEngineAnalysisTask = LearningEngineAnalysisTask.builder()
                                                                .state_execution_id(stateExecutionId)
                                                                .workflow_execution_id(workflowExecutionId)
                                                                .executionStatus(ExecutionStatus.QUEUED)
                                                                .analysis_minute(0)
                                                                .build();
    learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);

    learningEngineAnalysisTask =
        LearningEngineAnalysisTask.builder()
            .state_execution_id(stateExecutionId)
            .workflow_execution_id(workflowExecutionId)
            .executionStatus(ExecutionStatus.QUEUED)
            .analysis_minute((int) (TimeUnit.MILLISECONDS.toMinutes(TIME_SERIES_ANALYSIS_TASK_TIME_OUT)))
            .build();
    assertThat(learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask)).isFalse();
  }

  @Test
  @Owner(developers = PARNIAN)
  @Category(UnitTests.class)
  public void testAddLearningEngineAnalysisTask_QueueWithStatus24x7Task() {
    int numOfTasks = 100;
    for (int i = 0; i < numOfTasks; i++) {
      LearningEngineAnalysisTask learningEngineAnalysisTask = LearningEngineAnalysisTask.builder()
                                                                  .state_execution_id(generateUUID())
                                                                  .workflow_execution_id(generateUUID())
                                                                  .executionStatus(ExecutionStatus.QUEUED)
                                                                  .is24x7Task(true)
                                                                  .build();
      learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);
    }

    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class).count()).isEqualTo(numOfTasks);

    for (int i = 1; i <= numOfTasks; i++) {
      LearningEngineAnalysisTask task = learningEngineService.getNextLearningEngineAnalysisTask(
          ServiceApiVersion.V1, Optional.of("true"), Optional.empty());
      assertThat(task.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);

      assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
                     .filter("executionStatus", ExecutionStatus.QUEUED)
                     .filter("retry", 0)
                     .asList()
                     .size())
          .isEqualTo(numOfTasks - i);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testAddLearningEngineAnalysisTask_QueueWithAnalysisType() {
    int numOfTasks = 100;
    for (int i = 0; i < numOfTasks; i++) {
      LearningEngineAnalysisTask learningEngineAnalysisTask = LearningEngineAnalysisTask.builder()
                                                                  .state_execution_id(generateUUID())
                                                                  .workflow_execution_id(generateUUID())
                                                                  .executionStatus(ExecutionStatus.QUEUED)
                                                                  .ml_analysis_type(MLAnalysisType.LOG_CLUSTER)
                                                                  .is24x7Task(true)
                                                                  .build();
      learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);
    }

    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class).count()).isEqualTo(numOfTasks);

    LearningEngineAnalysisTask task = learningEngineService.getNextLearningEngineAnalysisTask(ServiceApiVersion.V1,
        Optional.of("true"), Optional.of(Lists.newArrayList(MLAnalysisType.TIME_SERIES, MLAnalysisType.LOG_ML)));
    assertThat(task).isNull();

    task = learningEngineService.getNextLearningEngineAnalysisTask(ServiceApiVersion.V1, Optional.of("true"),
        Optional.of(Lists.newArrayList(MLAnalysisType.TIME_SERIES, MLAnalysisType.LOG_CLUSTER, MLAnalysisType.LOG_ML)));
    assertThat(task).isNotNull();
    assertThat(task.getMl_analysis_type()).isEqualTo(MLAnalysisType.LOG_CLUSTER);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                   .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.QUEUED)
                   .count())
        .isEqualTo(numOfTasks - 1);

    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.of("true"), Optional.empty());
    assertThat(task).isNotNull();
    assertThat(task.getMl_analysis_type()).isEqualTo(MLAnalysisType.LOG_CLUSTER);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                   .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.QUEUED)
                   .count())
        .isEqualTo(numOfTasks - 2);

    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.of("true"), Optional.of(Lists.newArrayList()));
    assertThat(task).isNotNull();
    assertThat(task.getMl_analysis_type()).isEqualTo(MLAnalysisType.LOG_CLUSTER);
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                   .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.QUEUED)
                   .count())
        .isEqualTo(numOfTasks - 3);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testAddLearningEngineExperimentalAnalysisTask_WithoutDuplicate() {
    boolean result = learningEngineService.addLearningEngineExperimentalAnalysisTask(experimentalAnalysisTask);
    assertThat(result).isTrue();

    LearningEngineExperimentalAnalysisTask savedTask =
        wingsPersistence.createQuery(LearningEngineExperimentalAnalysisTask.class)
            .filter(LearningEngineExperimentalAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .get();

    assertThat(savedTask).isNotNull();
    assertThat(savedTask.getWorkflow_execution_id()).isEqualTo(workflowExecutionId);
    assertThat(savedTask.getExecutionStatus()).isEqualTo(ExecutionStatus.QUEUED);
    assertThat(savedTask.getRetry()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testAddLearningEngineExperimentalAnalysisTask_WithDuplicates() {
    learningEngineService.addLearningEngineExperimentalAnalysisTask(experimentalAnalysisTask);

    boolean result = learningEngineService.addLearningEngineExperimentalAnalysisTask(experimentalAnalysisTask);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetNextLearningEngineAnalysisTask_WhenPresent() {
    learningEngineService.addLearningEngineAnalysisTask(analysisTask);

    LearningEngineAnalysisTask nextTask =
        learningEngineService.getNextLearningEngineAnalysisTask(serviceApiVersion, Optional.empty(), Optional.empty());
    assertThat(nextTask).isNotNull();
    assertThat(nextTask.getState_execution_id()).isEqualTo(stateExecutionId);
    assertThat(nextTask.getAnalysis_minute()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetNextLearningEngineAnalysisTask_WithSpecificAnalysisType() {
    analysisTask.setMl_analysis_type(MLAnalysisType.LOG_ML);
    learningEngineService.addLearningEngineAnalysisTask(analysisTask);

    LearningEngineAnalysisTask nextTask = learningEngineService.getNextLearningEngineAnalysisTask(
        serviceApiVersion, Optional.empty(), Optional.of(Collections.singletonList(MLAnalysisType.LOG_ML)));
    assertThat(nextTask).isNotNull();
    assertThat(nextTask.getState_execution_id()).isEqualTo(stateExecutionId);
    assertThat(nextTask.getAnalysis_minute()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetNextLearningEngineAnalysisTask_WhenAbsent() {
    learningEngineService.addLearningEngineAnalysisTask(analysisTask);

    LearningEngineAnalysisTask nextTask = learningEngineService.getNextLearningEngineAnalysisTask(
        serviceApiVersion, Optional.of("true"), Optional.empty());
    assertThat(nextTask).isNull();
  }

  @Test
  @Owner(developers = PARNIAN)
  @Category(UnitTests.class)
  public void testRetryExceeded() {
    LearningEngineAnalysisTask learningEngineAnalysisTask = LearningEngineAnalysisTask.builder()
                                                                .state_execution_id(stateExecutionId)
                                                                .workflow_execution_id(workflowExecutionId)
                                                                .executionStatus(ExecutionStatus.QUEUED)
                                                                .retry(LearningEngineAnalysisTask.RETRIES)
                                                                .build();
    wingsPersistence.updateField(LearningEngineAnalysisTask.class, learningEngineAnalysisTask.getUuid(), "retry",
        LearningEngineAnalysisTask.RETRIES);
    assertThat(learningEngineService.getNextLearningEngineAnalysisTask(
                   ServiceApiVersion.V1, Optional.empty(), Optional.empty()))
        .isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetNextLearningEngineExperimentalAnalysisTask_WhenPresent() {
    experimentalAnalysisTask.setExperiment_name(experimentName);
    learningEngineService.addLearningEngineExperimentalAnalysisTask(experimentalAnalysisTask);

    LearningEngineExperimentalAnalysisTask nextTask =
        learningEngineService.getNextLearningEngineExperimentalAnalysisTask(
            serviceApiVersion, experimentName, Optional.empty());
    assertThat(nextTask).isNotNull();
    assertThat(nextTask.getState_execution_id()).isEqualTo(stateExecutionId);
    assertThat(nextTask.getAnalysis_minute()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetNextLearningEngineExperimentalAnalysisTask_WhenAbsent() {
    experimentalAnalysisTask.setExperiment_name(experimentName);
    experimentalAnalysisTask.setMl_analysis_type(MLAnalysisType.LOG_ML);
    learningEngineService.addLearningEngineExperimentalAnalysisTask(experimentalAnalysisTask);

    LearningEngineExperimentalAnalysisTask nextTask =
        learningEngineService.getNextLearningEngineExperimentalAnalysisTask(
            serviceApiVersion, experimentName, Optional.of(Collections.singletonList(MLAnalysisType.LOG_CLUSTER)));
    assertThat(nextTask).isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testHasAnalysisTimedOut_False() {
    analysisTask.setAppId(appId);
    learningEngineService.addLearningEngineAnalysisTask(analysisTask);

    boolean timedOut = learningEngineService.hasAnalysisTimedOut(appId, workflowExecutionId, stateExecutionId);
    assertThat(timedOut).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testHasAnalysisTimedOut_True() {
    analysisTask.setAppId(appId);
    analysisTask.setExecutionStatus(ExecutionStatus.RUNNING);
    analysisTask.setRetry(4);
    wingsPersistence.save(analysisTask);

    boolean timedOut = learningEngineService.hasAnalysisTimedOut(appId, workflowExecutionId, stateExecutionId);
    assertThat(timedOut).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testMarkCompleted() {
    analysisTask.setMl_analysis_type(MLAnalysisType.LOG_ML);
    analysisTask.setExecutionStatus(ExecutionStatus.RUNNING);
    analysisTask.setCluster_level(ClusterLevel.L0.getLevel());

    wingsPersistence.save(analysisTask);

    learningEngineService.markCompleted(
        accountId, workflowExecutionId, stateExecutionId, 0, MLAnalysisType.LOG_ML, ClusterLevel.L0);

    LearningEngineAnalysisTask analysisTask =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .get();

    assertThat(analysisTask).isNotNull();
    assertThat(analysisTask.getExecutionStatus()).isEqualByComparingTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testMarkCompleted_WithTaskId() {
    analysisTask.setMl_analysis_type(MLAnalysisType.LOG_ML);
    analysisTask.setExecutionStatus(ExecutionStatus.RUNNING);
    analysisTask.setCluster_level(ClusterLevel.L0.getLevel());
    analysisTask.setUuid(taskId);

    wingsPersistence.save(analysisTask);

    learningEngineService.markCompleted(taskId);

    LearningEngineAnalysisTask analysisTask =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
            .filter(LearningEngineAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .get();

    assertThat(analysisTask).isNotNull();
    assertThat(analysisTask.getExecutionStatus()).isEqualByComparingTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testMarkExpTaskCompleted() {
    experimentalAnalysisTask.setMl_analysis_type(MLAnalysisType.LOG_ML);
    experimentalAnalysisTask.setExecutionStatus(ExecutionStatus.RUNNING);
    experimentalAnalysisTask.setCluster_level(ClusterLevel.L0.getLevel());
    experimentalAnalysisTask.setUuid(taskId);

    wingsPersistence.save(experimentalAnalysisTask);

    learningEngineService.markExpTaskCompleted(taskId);

    LearningEngineExperimentalAnalysisTask savedTask =
        wingsPersistence.createQuery(LearningEngineExperimentalAnalysisTask.class)
            .filter(LearningEngineExperimentalAnalysisTaskKeys.state_execution_id, stateExecutionId)
            .get();

    assertThat(savedTask).isNotNull();
    assertThat(savedTask.getExecutionStatus()).isEqualByComparingTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetExperiments() {
    MLExperiments exp = MLExperiments.builder().ml_analysis_type(MLAnalysisType.LOG_ML).experimentName("log").build();
    wingsPersistence.save(exp);

    List<MLExperiments> experiments = learningEngineService.getExperiments(MLAnalysisType.LOG_ML);
    assertThat(experiments.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetNextVerificationAnalysisTask() {
    Map<String, String> nodes = new HashMap<>();
    nodes.put("host1", "value1");
    nodes.put(replaceDotWithUnicode("host.key"), "value2");

    AnalysisContext analysisContext = AnalysisContext.builder()
                                          .executionStatus(ExecutionStatus.QUEUED)
                                          .controlNodes(nodes)
                                          .testNodes(new HashMap<>())
                                          .build();

    analysisContext.setRetry(0);
    analysisContext.setVersion(serviceApiVersion);

    wingsPersistence.save(analysisContext);

    AnalysisContext savedContext = wingsPersistence.createQuery(AnalysisContext.class).get();

    AnalysisContext updatedContext = learningEngineService.getNextVerificationAnalysisTask(serviceApiVersion);

    assertThat(updatedContext).isNotNull();
    assertThat(updatedContext.getExecutionStatus()).isEqualByComparingTo(ExecutionStatus.RUNNING);
    assertThat(updatedContext.getLastUpdatedAt()).isGreaterThan(savedContext.getLastUpdatedAt());
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testMarkJobScheduled() {
    Map<String, String> nodes = new HashMap<>();
    nodes.put("host1", "value1");
    nodes.put(replaceDotWithUnicode("host.key"), "value2");

    AnalysisContext analysisContext = AnalysisContext.builder()
                                          .executionStatus(ExecutionStatus.QUEUED)
                                          .controlNodes(nodes)
                                          .testNodes(new HashMap<>())
                                          .build();

    analysisContext.setRetry(0);
    analysisContext.setVersion(serviceApiVersion);

    wingsPersistence.save(analysisContext);

    AnalysisContext savedContext = wingsPersistence.createQuery(AnalysisContext.class).get();

    learningEngineService.markJobStatus(savedContext, ExecutionStatus.SUCCESS);

    AnalysisContext updatedContext = wingsPersistence.createQuery(AnalysisContext.class).get();

    assertThat(updatedContext.getExecutionStatus()).isEqualByComparingTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testCheckAndUpdateFailedLETask() {
    analysisTask.setRetry(2);
    analysisTask.setExecutionStatus(ExecutionStatus.RUNNING);

    wingsPersistence.save(analysisTask);

    learningEngineService.checkAndUpdateFailedLETask(stateExecutionId, 0);

    LearningEngineAnalysisTask updatedTask = wingsPersistence.createQuery(LearningEngineAnalysisTask.class).get();

    assertThat(updatedTask.getState_execution_id()).contains("-retry-");
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testNotifyFailure_AfterMaxRetries() {
    analysisTask.setRetry(3);
    analysisTask.setExecutionStatus(ExecutionStatus.RUNNING);
    analysisTask.setUuid(taskId);
    analysisTask.setCluster_level(ClusterLevel.L0.getLevel());

    wingsPersistence.save(analysisTask);

    AnalysisContext analysisContext = AnalysisContext.builder()
                                          .stateExecutionId(stateExecutionId)
                                          .appId(appId)
                                          .executionStatus(ExecutionStatus.RUNNING)
                                          .build();

    wingsPersistence.save(analysisContext);

    LearningEngineError error = LearningEngineError.builder().analysisMinute(0).errorMsg("Task failed").build();

    boolean result = learningEngineService.notifyFailure(taskId, error);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testNotifyFailure_BeforeMaxRetries() {
    analysisTask.setRetry(1);
    analysisTask.setExecutionStatus(ExecutionStatus.RUNNING);
    analysisTask.setUuid(taskId);
    analysisTask.setCluster_level(ClusterLevel.L0.getLevel());

    wingsPersistence.save(analysisTask);

    AnalysisContext analysisContext = AnalysisContext.builder()
                                          .stateExecutionId(stateExecutionId)
                                          .appId(appId)
                                          .executionStatus(ExecutionStatus.RUNNING)
                                          .build();

    wingsPersistence.save(analysisContext);

    LearningEngineError error = LearningEngineError.builder().analysisMinute(0).errorMsg("Task failed").build();

    boolean result = learningEngineService.notifyFailure(taskId, error);
    assertThat(result).isTrue();

    LearningEngineAnalysisTask task = wingsPersistence.createQuery(LearningEngineAnalysisTask.class).get();
    assertThat(task.getExecutionStatus()).isEqualByComparingTo(ExecutionStatus.QUEUED);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testNotifyFailure_WithMultipleTasks() {
    LearningEngineAnalysisTask learningEngineAnalysisTask = LearningEngineAnalysisTask.builder()
                                                                .state_execution_id(stateExecutionId)
                                                                .workflow_execution_id(workflowExecutionId)
                                                                .executionStatus(ExecutionStatus.QUEUED)
                                                                .build();
    learningEngineService.addLearningEngineAnalysisTask(learningEngineAnalysisTask);
    wingsPersistence.save(AnalysisContext.builder().stateExecutionId(stateExecutionId).build());

    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                   .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.QUEUED)
                   .count())
        .isEqualTo(1);

    for (int i = 0; i < LearningEngineAnalysisTask.RETRIES - 1; i++) {
      LearningEngineAnalysisTask leTask = learningEngineService.getNextLearningEngineAnalysisTask(
          ServiceApiVersion.V1, Optional.of("false"), Optional.empty());
      assertThat(leTask.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);

      assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                     .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.QUEUED)
                     .count())
          .isEqualTo(0);

      assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                     .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.RUNNING)
                     .count())
          .isEqualTo(1);

      learningEngineService.notifyFailure(leTask.getUuid(), LearningEngineError.builder().build());
      assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                     .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.QUEUED)
                     .count())
          .isEqualTo(1);

      assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                     .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.RUNNING)
                     .count())
          .isEqualTo(0);
    }

    LearningEngineAnalysisTask leTask = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.of("false"), Optional.empty());
    assertThat(leTask.getExecutionStatus()).isEqualTo(ExecutionStatus.RUNNING);

    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                   .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.QUEUED)
                   .count())
        .isEqualTo(0);

    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                   .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.RUNNING)
                   .count())
        .isEqualTo(1);

    learningEngineService.notifyFailure(leTask.getUuid(), LearningEngineError.builder().build());
    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                   .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.QUEUED)
                   .count())
        .isEqualTo(0);

    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                   .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.RUNNING)
                   .count())
        .isEqualTo(0);

    assertThat(wingsPersistence.createQuery(LearningEngineAnalysisTask.class)
                   .filter(LearningEngineAnalysisTaskKeys.executionStatus, ExecutionStatus.FAILED)
                   .count())
        .isEqualTo(1);

    assertThat(learningEngineService.getNextLearningEngineAnalysisTask(
                   ServiceApiVersion.V1, Optional.of("false"), Optional.empty()))
        .isNull();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testShouldUseSupervisedModel_WithServiceId() {
    SupervisedTrainingStatus status =
        SupervisedTrainingStatus.builder().serviceId(serviceId).isSupervisedReady(true).build();

    wingsPersistence.save(status);

    boolean result = learningEngineService.shouldUseSupervisedModel("serviceId", serviceId);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testShouldUseSupervisedModel_WithStateExecutionId() {
    StateExecutionInstance instance = new StateExecutionInstance();
    instance.setUuid(stateExecutionId);

    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(serviceId).build()).build();
    LinkedList<ContextElement> contextElements = new LinkedList<>();
    contextElements.add(phaseElement);
    instance.setContextElements(contextElements);

    wingsPersistence.save(instance);

    SupervisedTrainingStatus status =
        SupervisedTrainingStatus.builder().serviceId(serviceId).isSupervisedReady(true).build();

    wingsPersistence.save(status);

    boolean result = learningEngineService.shouldUseSupervisedModel("stateExecutionId", stateExecutionId);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testShouldUseSupervisedModel_EmptyList() {
    boolean result = learningEngineService.shouldUseSupervisedModel("serviceId", serviceId);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testShouldUseSupervisedModel_WithDuplicates() {
    SupervisedTrainingStatus status =
        SupervisedTrainingStatus.builder().serviceId(serviceId).isSupervisedReady(true).build();

    wingsPersistence.save(status);

    SupervisedTrainingStatus status2 =
        SupervisedTrainingStatus.builder().serviceId(serviceId).isSupervisedReady(true).build();

    wingsPersistence.save(status2);

    boolean result = learningEngineService.shouldUseSupervisedModel("serviceId", serviceId);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetServiceIdFromStateExecutionId() {
    StateExecutionInstance instance = new StateExecutionInstance();
    instance.setUuid(stateExecutionId);

    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(serviceId).build()).build();
    LinkedList<ContextElement> contextElements = new LinkedList<>();
    contextElements.add(phaseElement);
    instance.setContextElements(contextElements);

    wingsPersistence.save(instance);

    String fetchedServiceId = learningEngineService.getServiceIdFromStateExecutionId(stateExecutionId);

    assertThat(fetchedServiceId).isEqualTo(serviceId);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetNextServiceGuardBackoffCountPreviousTask_Null() {
    int result =
        learningEngineService.getNextServiceGuardBackoffCount(stateExecutionId, cvConfigId, 0, MLAnalysisType.LOG_ML);
    assertThat(result).isEqualTo(1);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetNextServiceGuardBackoffCountPreviousTask_Backoff0() {
    MLAnalysisType mlAnalysisType = MLAnalysisType.LOG_ML;
    analysisTask.setService_guard_backoff_count(0);
    analysisTask.setState_execution_id(stateExecutionId + "-retry-0");
    analysisTask.setMl_analysis_type(mlAnalysisType);
    wingsPersistence.save(analysisTask);

    int result =
        learningEngineService.getNextServiceGuardBackoffCount(stateExecutionId, cvConfigId, 0, MLAnalysisType.LOG_ML);
    assertThat(result).isEqualTo(1);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetNextServiceGuardBackoffCountPreviousTask_BackoffUnderLimit() {
    MLAnalysisType mlAnalysisType = MLAnalysisType.LOG_ML;
    analysisTask.setService_guard_backoff_count(3);
    analysisTask.setState_execution_id(stateExecutionId + "-retry-3");
    analysisTask.setMl_analysis_type(mlAnalysisType);
    wingsPersistence.save(analysisTask);

    int result = learningEngineService.getNextServiceGuardBackoffCount(stateExecutionId, cvConfigId, 0, mlAnalysisType);
    assertThat(result).isEqualTo(5);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testGetNextServiceGuardBackoffCountPreviousTask_BackoffOverLimit() {
    MLAnalysisType mlAnalysisType = MLAnalysisType.LOG_ML;
    analysisTask.setService_guard_backoff_count(9);
    analysisTask.setState_execution_id(stateExecutionId + "-retry-9");
    analysisTask.setMl_analysis_type(mlAnalysisType);
    wingsPersistence.save(analysisTask);

    int result = learningEngineService.getNextServiceGuardBackoffCount(stateExecutionId, cvConfigId, 0, mlAnalysisType);
    assertThat(result).isEqualTo(10);
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testIsTaskRunningOrQueued_NoTasks() {
    boolean result = learningEngineService.isTaskRunningOrQueued(cvConfigId, 1054);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testIsTaskRunningOrQueued_NoTasksNoTimeParam() {
    boolean result = learningEngineService.isTaskRunningOrQueued(cvConfigId);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testIsTaskRunningOrQueued_WithQueuedTask() {
    analysisTask.setCvConfigId(cvConfigId);
    analysisTask.setAnalysis_minute(10564);
    wingsPersistence.save(analysisTask);

    boolean result = learningEngineService.isTaskRunningOrQueued(cvConfigId, 1054);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testIsTaskRunningOrQueued_NoParamWithQueuedTask() {
    analysisTask.setCvConfigId(cvConfigId);
    analysisTask.setAnalysis_minute(10564);
    wingsPersistence.save(analysisTask);

    boolean result = learningEngineService.isTaskRunningOrQueued(cvConfigId);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testIsEligibleToCreateTask_PreviousTaskNull() {
    boolean result =
        learningEngineService.isEligibleToCreateTask(stateExecutionId, cvConfigId, 0, MLAnalysisType.LOG_ML);
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testIsEligibleToCreateTask_WithPreviousTask() {
    MLAnalysisType mlAnalysisType = MLAnalysisType.LOG_ML;
    analysisTask.setService_guard_backoff_count(3);
    analysisTask.setState_execution_id(stateExecutionId + "-retry-3");
    analysisTask.setMl_analysis_type(mlAnalysisType);
    wingsPersistence.save(analysisTask);

    boolean result =
        learningEngineService.isEligibleToCreateTask(stateExecutionId, cvConfigId, 0, MLAnalysisType.LOG_ML);
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testGetNextLearningEngineAnalysisTaskWhenIs24x7isTrueTaskTypesEmpty() {
    setUpCreateMLAnalysisTaks();
    List<MLAnalysisType> mlAnalysisTypeList = getMLAnalysisTypeList();
    for (MLAnalysisType mlAnalysisType : mlAnalysisTypeList) {
      LearningEngineAnalysisTask task = learningEngineService.getNextLearningEngineAnalysisTask(
          ServiceApiVersion.V1, Optional.of("true"), Optional.of(Lists.newArrayList()));
      assertThat(task.is24x7Task()).isEqualTo(Boolean.TRUE);
      assertThat(task.getMl_analysis_type()).isEqualTo(mlAnalysisType);
    }

    LearningEngineAnalysisTask task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.of("true"), Optional.of(Lists.newArrayList()));

    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testGetNextLearningEngineAnalysisTaskWhenIs24x7isTrueTaskTypesNotEmpty() {
    setUpCreateMLAnalysisTaks();

    LearningEngineAnalysisTask task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.of("true"), Optional.of(Lists.newArrayList(MLAnalysisType.FEEDBACK_ANALYSIS)));

    assertThat(task.is24x7Task()).isEqualTo(Boolean.TRUE);
    assertThat(task.getMl_analysis_type()).isEqualTo(MLAnalysisType.FEEDBACK_ANALYSIS);

    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.of("true"), Optional.of(Lists.newArrayList(MLAnalysisType.FEEDBACK_ANALYSIS)));

    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testGetNextLearningEngineAnalysisTaskWhenIs24x7isFalseTaskTypesEmpty() {
    setUpCreateMLAnalysisTaks();
    List<MLAnalysisType> mlAnalysisTypeList = getMLAnalysisTypeList();
    for (MLAnalysisType mlAnalysisType : mlAnalysisTypeList) {
      LearningEngineAnalysisTask task = learningEngineService.getNextLearningEngineAnalysisTask(
          ServiceApiVersion.V1, Optional.of("false"), Optional.of(Lists.newArrayList()));
      assertThat(task.is24x7Task()).isEqualTo(Boolean.FALSE);
      assertThat(task.getMl_analysis_type()).isEqualTo(mlAnalysisType);
    }

    LearningEngineAnalysisTask task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.of("false"), Optional.of(Lists.newArrayList()));

    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testGetNextLearningEngineAnalysisTaskWhenIs24x7isFalseTaskTypesNotEmpty() {
    setUpCreateMLAnalysisTaks();

    LearningEngineAnalysisTask task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.of("false"), Optional.of(Lists.newArrayList(MLAnalysisType.TIME_SERIES)));

    assertThat(task.is24x7Task()).isEqualTo(Boolean.FALSE);
    assertThat(task.getMl_analysis_type()).isEqualTo(MLAnalysisType.TIME_SERIES);

    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.of("false"), Optional.of(Lists.newArrayList(MLAnalysisType.TIME_SERIES)));

    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testGetNextLearningEngineAnalysisTaskWhenIs24x7isEmptyTaskTypesEmpty() {
    setUpCreateMLAnalysisTaks();

    List<MLAnalysisType> mlAnalysisTypeList = getMLAnalysisTypeList();
    for (MLAnalysisType mlAnalysisType : mlAnalysisTypeList) {
      LearningEngineAnalysisTask task = learningEngineService.getNextLearningEngineAnalysisTask(
          ServiceApiVersion.V1, Optional.empty(), Optional.of(Lists.newArrayList()));
      assertThat(task.is24x7Task()).isEqualTo(Boolean.TRUE);
      assertThat(task.getMl_analysis_type()).isEqualTo(mlAnalysisType);
    }

    for (MLAnalysisType mlAnalysisType : mlAnalysisTypeList) {
      LearningEngineAnalysisTask task = learningEngineService.getNextLearningEngineAnalysisTask(
          ServiceApiVersion.V1, Optional.empty(), Optional.of(Lists.newArrayList()));
      assertThat(task.is24x7Task()).isEqualTo(Boolean.FALSE);
      assertThat(task.getMl_analysis_type()).isEqualTo(mlAnalysisType);
    }
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void testGetNextLearningEngineAnalysisTaskWhenIs24x7isEmptyTaskTypesNotEmpty() {
    setUpCreateMLAnalysisTaks();

    LearningEngineAnalysisTask task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.empty(), Optional.of(Lists.newArrayList(MLAnalysisType.TIME_SERIES)));

    assertThat(task.is24x7Task()).isEqualTo(Boolean.TRUE);
    assertThat(task.getMl_analysis_type()).isEqualTo(MLAnalysisType.TIME_SERIES);

    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.empty(), Optional.of(Lists.newArrayList(MLAnalysisType.TIME_SERIES)));

    assertThat(task.is24x7Task()).isEqualTo(Boolean.FALSE);
    assertThat(task.getMl_analysis_type()).isEqualTo(MLAnalysisType.TIME_SERIES);

    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.of("false"), Optional.of(Lists.newArrayList(MLAnalysisType.TIME_SERIES)));

    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLETask_WithSamePriority() {
    LearningEngineAnalysisTask task1 = getLEAnalysisTask();
    task1.setPriority(0);
    task1.setUuid("task1ID");
    wingsPersistence.save(task1);

    LearningEngineAnalysisTask task = null;
    while (task == null) {
      task = wingsPersistence.get(LearningEngineAnalysisTask.class, "task1ID");
    }
    // we've made sure that task1 is in the DB now. So it has an earlier createdAt date.

    LearningEngineAnalysisTask task2 = getLEAnalysisTask();
    task2.setPriority(0);
    task2.setUuid("task2ID");
    wingsPersistence.save(task2);

    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.empty(), Optional.empty());
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("task1ID");

    // now we should fetch task 2 since 1 has been fetched already
    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.empty(), Optional.empty());
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("task2ID");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLETask_WithDifferentPriority() {
    LearningEngineAnalysisTask task1 = getLEAnalysisTask();
    task1.setPriority(1);
    task1.setUuid("task1ID");
    wingsPersistence.save(task1);

    LearningEngineAnalysisTask task = null;
    while (task == null) {
      task = wingsPersistence.get(LearningEngineAnalysisTask.class, "task1ID");
    }
    // we've made sure that task1 is in the DB now. So it has an earlier createdAt date.

    LearningEngineAnalysisTask task2 = getLEAnalysisTask();
    task2.setPriority(0);
    task2.setUuid("task2ID");
    wingsPersistence.save(task2);

    // verify that task 2 is picked up first since it has higher priority
    // even though it has later createdAt date.

    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.empty(), Optional.empty());
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("task2ID");

    // now we should fetch task 1 since task2 has been fetched already
    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.empty(), Optional.empty());
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("task1ID");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetLETask_OneWithPrioAndOneWithout() {
    LearningEngineAnalysisTask task1 = getLEAnalysisTask();
    task1.setUuid("task1ID");
    wingsPersistence.save(task1);

    LearningEngineAnalysisTask task = null;
    while (task == null) {
      task = wingsPersistence.get(LearningEngineAnalysisTask.class, "task1ID");
    }
    // we've made sure that task1 is in the DB now. So it has an earlier createdAt date.

    LearningEngineAnalysisTask task2 = getLEAnalysisTask();
    task2.setPriority(0);
    task2.setUuid("task2ID");
    wingsPersistence.save(task2);

    // verify that task 2 is picked up first since it has higher priority
    // even though it has later createdAt date.

    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.empty(), Optional.empty());
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("task2ID");

    // now we should fetch task 1 since task2 has been fetched already
    task = learningEngineService.getNextLearningEngineAnalysisTask(
        ServiceApiVersion.V1, Optional.empty(), Optional.empty());
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("task1ID");
  }
}
