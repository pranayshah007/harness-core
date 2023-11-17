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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Map;
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

  @Inject
  public NodeExecutionMonitorServiceImpl(NodeExecutionService nodeExecutionService, MetricService metricService,
      @Named("pmsMetricsCache") Cache<String, Integer> metricsCache) {
    this.nodeExecutionService = nodeExecutionService;
    this.metricService = metricService;
    this.metricsCache = metricsCache;
  }

  @Override
  public void registerActiveExecutionMetrics() {
    boolean alreadyMetricPublished = !metricsCache.putIfAbsent(NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME, 1);
    if (alreadyMetricPublished) {
      return;
    }

    ExecutionStatistics executionStatistics = nodeExecutionService.aggregateRunningNodesCount().get(0);

    for (ExecutionCountWithAccountResult accountResult : executionStatistics.getAccountStats()) {
      Map<String, String> metricContextMap =
          ImmutableMap.<String, String>builder()
              .put(PmsEventMonitoringConstants.ACCOUNT_ID, accountResult.getAccountId())
              .build();

      try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
        metricService.recordMetric(NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME, accountResult.getCount());
      }
    }

    for (ExecutionCountWithModuleResult moduleResult : executionStatistics.getModuleStats()) {
      Map<String, String> metricContextMap = ImmutableMap.<String, String>builder()
                                                 .put(PmsEventMonitoringConstants.MODULE, moduleResult.getModule())
                                                 .build();

      try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
        metricService.recordMetric(
            NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_MODULE_METRIC_NAME, moduleResult.getCount());
      }
    }

    for (ExecutionCountWithStepTypeResult stepTypeResult : executionStatistics.getStepTypeStats()) {
      Map<String, String> metricContextMap =
          ImmutableMap.<String, String>builder()
              .put(PmsEventMonitoringConstants.STEP_TYPE, stepTypeResult.getStepType())
              .build();

      try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
        metricService.recordMetric(
            NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_PER_STEP_TYPE_METRIC_NAME, stepTypeResult.getCount());
      }
    }
  }
}
