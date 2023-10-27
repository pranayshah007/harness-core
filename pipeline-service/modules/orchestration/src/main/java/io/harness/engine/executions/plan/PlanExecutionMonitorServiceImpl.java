/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.metrics.service.api.MetricService;
import io.harness.monitoring.ExecutionCountWithAccountResult;
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
public class PlanExecutionMonitorServiceImpl implements PlanExecutionMonitorService {
  private static final String PIPELINE_EXECUTION_ACTIVE_COUNT = "pipeline_execution_active_count";
  private final PlanExecutionService planExecutionService;
  private final MetricService metricService;
  private final Cache<String, Integer> metricsCache;

  @Inject
  public PlanExecutionMonitorServiceImpl(PlanExecutionService planExecutionService, MetricService metricService,
      @Named("pmsMetricsCache") Cache<String, Integer> metricsCache) {
    this.planExecutionService = planExecutionService;
    this.metricService = metricService;
    this.metricsCache = metricsCache;
  }

  @Override
  public void registerActiveExecutionMetrics() {
    boolean alreadyMetricPublished = !metricsCache.putIfAbsent(PIPELINE_EXECUTION_ACTIVE_COUNT, 1);
    if (alreadyMetricPublished) {
      return;
    }

    for (ExecutionCountWithAccountResult accountResult :
        planExecutionService.aggregateRunningExecutionCountPerAccount()) {
      Map<String, String> metricContextMap =
          ImmutableMap.<String, String>builder()
              .put(PmsEventMonitoringConstants.ACCOUNT_ID, accountResult.getAccountId())
              .build();

      try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
        metricService.recordMetric(PIPELINE_EXECUTION_ACTIVE_COUNT, accountResult.getCount());
      }
    }
  }
}
