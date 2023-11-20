/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.metrics.service.api.MetricService;
import io.harness.monitoring.ExecutionCountWithAccountResult;
import io.harness.monitoring.ExecutionCountWithModuleResult;
import io.harness.monitoring.ExecutionCountWithStepTypeResult;
import io.harness.monitoring.ExecutionStatistics;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.cache.Cache;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class NodeExecutionMonitorServiceImpl implements NodeExecutionMonitorService {
  private static final String NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME = "node_execution_active_count";
  private static final String NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_MODULE_METRIC_NAME =
      "node_execution_active_count_per_module";
  private static final String NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_STEP_TYPE_METRIC_NAME =
      "node_execution_active_count_per_stepType";
  private final NodeExecutionService nodeExecutionService;
  private final MetricService metricService;
  private final Cache<String, Integer> metricsCache;
  private final LoadingCache<String, Set<String>>
      metricsLoadingCache; // Cache to avoid retaining count in prometheus for a key in case count is 0 now
  @Inject
  public NodeExecutionMonitorServiceImpl(NodeExecutionService nodeExecutionService, MetricService metricService,
      @Named("pmsMetricsCache") Cache<String, Integer> metricsCache,
      @Named("pmsMetricsLoadingCache") LoadingCache<String, Set<String>> metricsLoadingCache) {
    this.nodeExecutionService = nodeExecutionService;
    this.metricService = metricService;
    this.metricsCache = metricsCache;
    this.metricsLoadingCache = metricsLoadingCache;
  }

  @Override
  public void registerActiveExecutionMetrics() {
    boolean alreadyMetricPublished = !metricsCache.putIfAbsent(NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME, 1);
    if (alreadyMetricPublished) {
      return;
    }

    ExecutionStatistics executionStatistics = nodeExecutionService.aggregateRunningNodeExecutionsCount();
    if (executionStatistics == null) {
      return;
    }

    for (ExecutionCountWithAccountResult accountResult : executionStatistics.getAccountStats()) {
      populateMetric(PmsEventMonitoringConstants.ACCOUNT_ID, accountResult.getAccountId(),
          NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME, accountResult.getCount());
    }

    for (ExecutionCountWithModuleResult moduleResult : executionStatistics.getModuleStats()) {
      populateMetric(PmsEventMonitoringConstants.MODULE, moduleResult.getModule(),
          NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_MODULE_METRIC_NAME, moduleResult.getCount());
    }

    for (ExecutionCountWithStepTypeResult stepTypeResult : executionStatistics.getStepTypeStats()) {
      populateMetric(PmsEventMonitoringConstants.STEP_TYPE, stepTypeResult.getStepType(),
          NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_STEP_TYPE_METRIC_NAME, stepTypeResult.getCount());
    }

    populateCountForMissingKeysInCurrentExecutionStats(executionStatistics);
  }

  private void populateCountForMissingKeysInCurrentExecutionStats(ExecutionStatistics executionStatistics) {
    Set<String> currentAccountIds = new HashSet<>();
    for (ExecutionCountWithAccountResult accountResult : executionStatistics.getAccountStats()) {
      currentAccountIds.add(accountResult.getAccountId());
    }
    populateZeroCount(
        currentAccountIds, PmsEventMonitoringConstants.ACCOUNT_ID, NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME);

    Set<String> currentModules = new HashSet<>();
    for (ExecutionCountWithModuleResult moduleResult : executionStatistics.getModuleStats()) {
      currentModules.add(moduleResult.getModule());
    }
    populateZeroCount(currentModules, PmsEventMonitoringConstants.MODULE,
        NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_MODULE_METRIC_NAME);

    Set<String> currentStepTypes = new HashSet<>();
    for (ExecutionCountWithStepTypeResult stepTypeResult : executionStatistics.getStepTypeStats()) {
      currentStepTypes.add(stepTypeResult.getStepType());
    }
    populateZeroCount(currentStepTypes, PmsEventMonitoringConstants.STEP_TYPE,
        NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_STEP_TYPE_METRIC_NAME);
  }

  private void populateZeroCount(
      Set<String> currentKeys, String metricKey, String nodeExecutionActiveExecutionCountMetricName) {
    Set<String> cachedKeys = metricsLoadingCache.get(metricKey);
    Set<String> zeroCountKeys = Sets.difference(cachedKeys, currentKeys);
    for (String key : zeroCountKeys) {
      populateMetric(metricKey, key, nodeExecutionActiveExecutionCountMetricName, 0);
    }
    cachedKeys.addAll(currentKeys);
  }

  private void populateMetric(String key, String keyValue, String metricName, Integer metricValue) {
    Map<String, String> metricContextMap = ImmutableMap.<String, String>builder().put(key, keyValue).build();

    try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
      metricService.recordMetric(metricName, metricValue);
    }
  }
}
