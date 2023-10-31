/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.metrics.services.impl;

import static io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus.QUEUED;
import static io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus.RUNNING;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANSUMAN;
import static io.harness.rule.OwnerRule.DHRUVX;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.analysis.beans.LogClusterLevel;
import io.harness.cvng.analysis.entities.CanaryLogAnalysisLearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskKeys;
import io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType;
import io.harness.cvng.analysis.entities.LogClusterLearningEngineTask;
import io.harness.cvng.analysis.entities.VerificationTaskBase.VerificationTaskBaseKeys;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.cvng.cdng.entities.CVNGStepTask;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.metrics.service.api.MetricService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CVNGMetricsPublisherTest extends CvNextGenTestBase {
  @Inject private CVNGMetricsPublisher cvngMetricsPublisher;
  @Inject private CVNGStepTaskService cvngStepTaskService;
  @Mock private MetricService metricService;
  @Inject private HPersistence hPersistence;

  @Inject private Clock clock;

  String verificationTaskId;

  @Inject private LearningEngineTaskService learningEngineTaskService;
  BuilderFactory builderFactory;
  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    builderFactory = BuilderFactory.getDefault();
    FieldUtils.writeField(cvngMetricsPublisher, "metricService", metricService, true);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testRecordMetrics() {
    CVNGStepTask cvngStepTask = builderFactory.cvngStepTaskBuilder().build();
    cvngStepTaskService.create(cvngStepTask);
    cvngMetricsPublisher.sendTaskStatusMetrics();
    Mockito.verify(metricService).recordMetric(eq("cvng_step_task_non_final_status_count"), eq(1.0));
    Mockito.verify(metricService).recordMetric(eq("cvng_step_task_in_progress_count"), eq(1.0));
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testsendLEAutoscaleMetrics() throws IllegalAccessException {
    clock = Clock.fixed(clock.instant().plus(1, ChronoUnit.MINUTES), ZoneOffset.UTC);
    verificationTaskId = "verificationTaskId";
    VerificationTask verificationTask = VerificationTask.builder()
                                            .accountId(builderFactory.getContext().getAccountId())
                                            .taskInfo(VerificationTask.DeploymentInfo.builder()
                                                          .cvConfigId("cvConfigId")
                                                          .verificationJobInstanceId("verificationJobInstanceId")
                                                          .build())
                                            .uuid(verificationTaskId)
                                            .createdAt(clock.millis())
                                            .build();
    hPersistence.save(verificationTask);
    FieldUtils.writeField(learningEngineTaskService, "clock", clock, true);
    CanaryLogAnalysisLearningEngineTask canaryLogAnalysisLearningEngineTask1 =
        canaryLogAnalysisLearningEngineTask(clock, "verificationTaskId");
    learningEngineTaskService.createLearningEngineTask(canaryLogAnalysisLearningEngineTask1);

    clock = Clock.fixed(clock.instant().plus(3, ChronoUnit.MINUTES), ZoneOffset.UTC);
    FieldUtils.writeField(cvngMetricsPublisher, "clock", clock, true);
    UpdateOperations<LearningEngineTask> updateOperations =
        hPersistence.createUpdateOperations(LearningEngineTask.class)
            .set(VerificationTaskBaseKeys.lastUpdatedAt, clock.instant().minus(5, ChronoUnit.MINUTES).toEpochMilli());
    Query<LearningEngineTask> learningEngineTaskQuery =
        hPersistence.createQuery(LearningEngineTask.class)
            .filter(LearningEngineTaskKeys.taskStatus, QUEUED)
            .order(Sort.ascending(VerificationTaskBaseKeys.lastUpdatedAt));
    hPersistence.findAndModifySystemData(learningEngineTaskQuery, updateOperations, new FindAndModifyOptions());
    cvngMetricsPublisher.sendLEAutoscaleMetrics();
    Mockito.verify(metricService).recordMetric(eq("learning_engine_max_queued_time_localhost"), eq(300000.0d));
    Mockito.verify(metricService)
        .recordMetric(eq("learning_engine_deployment_max_queued_time_localhost"), eq(300000.0d));
    Mockito.verify(metricService)
        .recordMetric(eq("learning_engine_service_health_max_queued_time_localhost"), eq(0.0d));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testSendLETaskStatusMetrics() {
    LearningEngineTask deploymentLearningEngineTaskQueued = LogClusterLearningEngineTask.builder()
                                                                .clusterLevel(LogClusterLevel.L1)
                                                                .host("host")
                                                                .testDataUrl("testDataUrl")
                                                                .build();
    deploymentLearningEngineTaskQueued.setTaskStatus(QUEUED);
    deploymentLearningEngineTaskQueued.setAnalysisType(LearningEngineTaskType.CV_LOG_CLUSTER);

    LearningEngineTask serviceHealthLearningEngineTaskQueued = LogClusterLearningEngineTask.builder()
                                                                   .clusterLevel(LogClusterLevel.L1)
                                                                   .host("host")
                                                                   .testDataUrl("testDataUrl")
                                                                   .build();
    serviceHealthLearningEngineTaskQueued.setTaskStatus(QUEUED);
    serviceHealthLearningEngineTaskQueued.setAnalysisType(LearningEngineTaskType.LOG_CLUSTER);

    LearningEngineTask deploymentLearningEngineTaskRunning = LogClusterLearningEngineTask.builder()
                                                                 .clusterLevel(LogClusterLevel.L1)
                                                                 .host("host")
                                                                 .testDataUrl("testDataUrl")
                                                                 .build();
    deploymentLearningEngineTaskRunning.setTaskStatus(RUNNING);
    deploymentLearningEngineTaskRunning.setAnalysisType(LearningEngineTaskType.CV_LOG_CLUSTER);

    LearningEngineTask serviceHealthLearningEngineTaskRunning = LogClusterLearningEngineTask.builder()
                                                                    .clusterLevel(LogClusterLevel.L1)
                                                                    .host("host")
                                                                    .testDataUrl("testDataUrl")
                                                                    .build();
    serviceHealthLearningEngineTaskRunning.setTaskStatus(RUNNING);
    serviceHealthLearningEngineTaskRunning.setAnalysisType(LearningEngineTaskType.LOG_CLUSTER);

    hPersistence.save(deploymentLearningEngineTaskQueued);
    hPersistence.save(serviceHealthLearningEngineTaskQueued);
    hPersistence.save(deploymentLearningEngineTaskRunning);
    hPersistence.save(serviceHealthLearningEngineTaskRunning);
    cvngMetricsPublisher.recordMetrics();
    verify(metricService).recordMetric("learning_engine_service_health_task_non_final_status_count", 2.0);
    verify(metricService).recordMetric("learning_engine_deployment_task_non_final_status_count", 2.0);

    verify(metricService).recordMetric("learning_engine_deployment_task_running_count", 1.0);
    verify(metricService).recordMetric("learning_engine_service_health_task_running_count", 1.0);

    verify(metricService).recordMetric("learning_engine_deployment_task_queued_count", 1.0);
    verify(metricService).recordMetric("learning_engine_service_health_task_queued_count", 1.0);

    verify(metricService).recordMetric("learning_engine_service_health_task_non_final_status_localhost_count", 2.0);
    verify(metricService).recordMetric("learning_engine_deployment_task_non_final_status_localhost_count", 2.0);

    verify(metricService).recordMetric("learning_engine_deployment_task_running_localhost_count", 1.0);
    verify(metricService).recordMetric("learning_engine_service_health_task_running_localhost_count", 1.0);

    verify(metricService).recordMetric("learning_engine_deployment_task_queued_localhost_count", 1.0);
    verify(metricService).recordMetric("learning_engine_service_health_task_queued_localhost_count", 1.0);
  }
  private CanaryLogAnalysisLearningEngineTask canaryLogAnalysisLearningEngineTask(
      Clock clock, String verificationTaskId) {
    return CanaryLogAnalysisLearningEngineTask.builder()
        .createdAt(clock.instant().minus(3, ChronoUnit.MINUTES).toEpochMilli())
        .accountId("accountId")
        .uuid(generateUuid())
        .analysisEndTime(clock.instant())
        .analysisStartTime(clock.instant())
        .analysisType(LearningEngineTaskType.CANARY_LOG_ANALYSIS)
        .taskStatus(ExecutionStatus.QUEUED)
        .analysisSaveUrl("saveUrl")
        .controlDataUrl("controlDataUrl")
        .pickedAt(clock.instant())
        .failureUrl("failureUrl")
        .previousAnalysisUrl("previousAnalysisUrl")
        .validUntil(Date.from(clock.instant().plus(30, ChronoUnit.MINUTES)))
        .verificationTaskId(verificationTaskId)
        .build();
  }
}
