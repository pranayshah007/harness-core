/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.scheduler;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAGHU;
import static io.harness.threading.Morpheus.sleep;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.VerificationBase;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.rule.Owner;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineExperimentalAnalysisTask;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.HashSet;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class LearningEngineQueuedTasksMetricTests extends VerificationBase {
  @Inject private ServiceGuardAccountPoller serviceGuardAccountPoller;
  @Inject private WingsPersistence wingsPersistence;
  @Mock private HarnessMetricRegistry harnessMetricRegistry;

  @Before
  public void setup() throws IllegalAccessException {
    doNothing().when(harnessMetricRegistry).recordGaugeValue(any(), any(String[].class), anyDouble());
    FieldUtils.writeField(serviceGuardAccountPoller, "metricRegistry", harnessMetricRegistry, true);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testNoQueuedTasks() {
    serviceGuardAccountPoller.recordQueuedTaskMetric();
    ArgumentCaptor<String> taskCaptorName = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String[]> taskCaptorParams = ArgumentCaptor.forClass(String[].class);
    ArgumentCaptor<Double> taskCaptorValue = ArgumentCaptor.forClass(Double.class);

    verify(harnessMetricRegistry, times(24))
        .recordGaugeValue(taskCaptorName.capture(), taskCaptorParams.capture(), taskCaptorValue.capture());

    verifyMetricsPublished(taskCaptorName);

    taskCaptorValue.getAllValues().forEach(value -> assertThat(value).isEqualTo(0.0));
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testQueuedTasks() {
    wingsPersistence.save(LearningEngineAnalysisTask.builder()
                              .cvConfigId(generateUuid())
                              .executionStatus(ExecutionStatus.QUEUED)
                              .ml_analysis_type(MLAnalysisType.TIME_SERIES)
                              .is24x7Task(true)
                              .build());
    wingsPersistence.save(LearningEngineAnalysisTask.builder()
                              .cvConfigId(generateUuid())
                              .executionStatus(ExecutionStatus.QUEUED)
                              .ml_analysis_type(MLAnalysisType.LOG_CLUSTER)
                              .is24x7Task(true)
                              .build());
    wingsPersistence.save(LearningEngineAnalysisTask.builder()
                              .cvConfigId(generateUuid())
                              .executionStatus(ExecutionStatus.QUEUED)
                              .ml_analysis_type(MLAnalysisType.FEEDBACK_ANALYSIS)
                              .is24x7Task(true)
                              .build());
    wingsPersistence.save(LearningEngineAnalysisTask.builder()
                              .executionStatus(ExecutionStatus.QUEUED)
                              .ml_analysis_type(MLAnalysisType.TIME_SERIES)
                              .is24x7Task(false)
                              .build());
    wingsPersistence.save(LearningEngineExperimentalAnalysisTask.builder()
                              .executionStatus(ExecutionStatus.QUEUED)
                              .ml_analysis_type(MLAnalysisType.TIME_SERIES)
                              .is24x7Task(false)
                              .build());
    wingsPersistence.save(LearningEngineAnalysisTask.builder()
                              .executionStatus(ExecutionStatus.QUEUED)
                              .ml_analysis_type(MLAnalysisType.LOG_CLUSTER)
                              .is24x7Task(false)
                              .build());
    wingsPersistence.save(LearningEngineAnalysisTask.builder()
                              .cvConfigId(generateUuid())
                              .executionStatus(ExecutionStatus.QUEUED)
                              .ml_analysis_type(MLAnalysisType.FEEDBACK_ANALYSIS)
                              .is24x7Task(false)
                              .build());

    sleep(ofSeconds(1));

    serviceGuardAccountPoller.recordQueuedTaskMetric();
    ArgumentCaptor<String> taskCaptorName = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String[]> taskCaptorParams = ArgumentCaptor.forClass(String[].class);
    ArgumentCaptor<Double> taskCaptorValue = ArgumentCaptor.forClass(Double.class);

    verify(harnessMetricRegistry, times(24))
        .recordGaugeValue(taskCaptorName.capture(), taskCaptorParams.capture(), taskCaptorValue.capture());

    verifyMetricsPublished(taskCaptorName);

    taskCaptorValue.getAllValues().forEach(value -> assertThat(value).isGreaterThan(0.0));
  }

  private void verifyMetricsPublished(ArgumentCaptor<String> taskCaptorName) {
    assertThat(new HashSet<>(taskCaptorName.getAllValues()))
        .isEqualTo(new HashSet<>(Lists.newArrayList("learning_engine_task_queued_time_in_seconds",
            "learning_engine_task_queued_count", "learning_engine_analysis_task_queued_time_in_seconds",
            "learning_engine_analysis_task_queued_count", "learning_engine_clustering_task_queued_time_in_seconds",
            "learning_engine_clustering_task_queued_count", "learning_engine_feedback_task_queued_time_in_seconds",
            "learning_engine_feedback_task_queued_count", "learning_engine_workflow_task_queued_time_in_seconds",
            "learning_engine_workflow_task_queued_count",
            "learning_engine_workflow_analysis_task_queued_time_in_seconds",
            "learning_engine_workflow_analysis_task_queued_count",
            "learning_engine_workflow_clustering_task_queued_time_in_seconds",
            "learning_engine_workflow_clustering_task_queued_count",
            "learning_engine_workflow_feedback_task_queued_time_in_seconds",
            "learning_engine_workflow_feedback_task_queued_count",
            "learning_engine_service_guard_task_queued_time_in_seconds",
            "learning_engine_service_guard_task_queued_count",
            "learning_engine_service_guard_analysis_task_queued_time_in_seconds",
            "learning_engine_service_guard_analysis_task_queued_count",
            "learning_engine_service_guard_clustering_task_queued_time_in_seconds",
            "learning_engine_service_guard_clustering_task_queued_count",
            "learning_engine_service_guard_feedback_task_queued_time_in_seconds",
            "learning_engine_service_guard_feedback_task_queued_count")));
  }
}
