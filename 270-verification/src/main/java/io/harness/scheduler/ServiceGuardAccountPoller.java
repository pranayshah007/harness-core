/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.scheduler;

import static io.harness.beans.ExecutionStatus.QUEUED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.common.VerificationConstants.LEARNING_ENGINE_TASKS_METRIC_LIST;
import static software.wings.service.impl.analysis.MLAnalysisType.FEEDBACK_ANALYSIS;
import static software.wings.service.impl.analysis.MLAnalysisType.LOG_CLUSTER;
import static software.wings.service.impl.analysis.MLAnalysisType.LOG_ML;

import io.harness.managerclient.VerificationManagerClient;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.service.intfc.ContinuousVerificationService;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.MLAnalysisType;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask;
import software.wings.service.impl.newrelic.LearningEngineAnalysisTask.LearningEngineAnalysisTaskKeys;
import software.wings.service.intfc.DataStoreService;
import software.wings.service.intfc.verification.CVConfigurationService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ServiceGuardAccountPoller {
  private static final int POLL_INTIAL_DELAY_SEOONDS = 60;
  @Inject @Named("verificationServiceExecutor") protected ScheduledExecutorService executorService;

  @Inject private VerificationManagerClient verificationManagerClient;
  @Inject private VerificationManagerClientHelper verificationManagerClientHelper;
  @Inject private CVConfigurationService cvConfigurationService;
  @Inject private ContinuousVerificationService continuousVerificationService;
  @Inject private DataStoreService dataStoreService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private HarnessMetricRegistry metricRegistry;

  public void scheduleAdministrativeTasks() {
    long initialDelay = new SecureRandom().nextInt(60);
    executorService.scheduleAtFixedRate(
        () -> recordQueuedTaskMetric(), initialDelay, POLL_INTIAL_DELAY_SEOONDS, TimeUnit.SECONDS);
    executorService.scheduleAtFixedRate(() -> dataStoreService.purgeOlderRecords(), initialDelay, 60, TimeUnit.MINUTES);
    executorService.scheduleAtFixedRate(
        () -> cvConfigurationService.deleteStaleConfigs(), initialDelay, 10, TimeUnit.MINUTES);
  }

  private void record(
      String env, List<MLAnalysisType> analysisTypeList, String metricNameSubstring, @Nullable Boolean is24x7Task) {
    Query<LearningEngineAnalysisTask> baseQuery =
        wingsPersistence.createQuery(LearningEngineAnalysisTask.class, excludeAuthority)
            .filter(LearningEngineAnalysisTaskKeys.executionStatus, QUEUED);
    if (!analysisTypeList.isEmpty()) {
      baseQuery = baseQuery.field(LearningEngineAnalysisTaskKeys.ml_analysis_type).in(analysisTypeList);
    }
    if (is24x7Task != null) {
      baseQuery = baseQuery.filter(LearningEngineAnalysisTaskKeys.is24x7Task, is24x7Task);
    }
    LearningEngineAnalysisTask lastQueued = baseQuery.order(Sort.ascending("createdAt")).get();
    long timeDiffInSec = lastQueued != null
        ? TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - lastQueued.getCreatedAt())
        : 0;
    long count = baseQuery.count();

    String time_in_seconds_metric_name = "learning_engine_" + metricNameSubstring + "task_queued_time_in_seconds";
    String count_metric_name = "learning_engine_" + metricNameSubstring + "task_queued_count";

    Preconditions.checkState(
        LEARNING_ENGINE_TASKS_METRIC_LIST.contains(time_in_seconds_metric_name), "metric is not registered");
    Preconditions.checkState(LEARNING_ENGINE_TASKS_METRIC_LIST.contains(count_metric_name), "metric is not registered");

    metricRegistry.recordGaugeValue(getMetricName(env, time_in_seconds_metric_name), null, timeDiffInSec);
    metricRegistry.recordGaugeValue(getMetricName(env, count_metric_name), null, count);
  }

  @VisibleForTesting
  void recordQueuedTaskMetric() {
    String env = System.getenv("ENV");
    if (isNotEmpty(env)) {
      env = env.replaceAll("-", "_").toLowerCase();
    }
    List<MLAnalysisType> analysisTasksFilters = Lists.newArrayList(LOG_ML, MLAnalysisType.TIME_SERIES);
    List<MLAnalysisType> clusteringFilter = Lists.newArrayList(LOG_CLUSTER);
    List<MLAnalysisType> feedbackFilter = Lists.newArrayList(FEEDBACK_ANALYSIS);
    record(env, Collections.emptyList(), "", null);
    record(env, analysisTasksFilters, "analysis_", null);
    record(env, clusteringFilter, "clustering_", null);
    record(env, feedbackFilter, "feedback_", null);

    record(env, Collections.emptyList(), "workflow_", false);
    record(env, analysisTasksFilters, "workflow_analysis_", false);
    record(env, clusteringFilter, "workflow_clustering_", false);
    record(env, feedbackFilter, "workflow_feedback_", false);

    record(env, Collections.emptyList(), "service_guard_", true);
    record(env, analysisTasksFilters, "service_guard_analysis_", true);
    record(env, clusteringFilter, "service_guard_clustering_", true);
    record(env, feedbackFilter, "service_guard_feedback_", true);
  }

  private String getMetricName(String env, String metricName) {
    if (isEmpty(env)) {
      return metricName;
    }

    return env + "_" + metricName;
  }
}
