/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionMonitorServiceImpl implements PlanExecutionMonitorService {
  private static final String PLAN_EXECUTION_ACTIVE_COUNT = "plan_execution_active_count";

  @Inject private PlanExecutionService planExecutionService;
  @Inject private MetricService metricService;

  @Override
  public void registerActiveExecutionMetrics() {
    Map<PipelineExecutionMetric, Integer> metricMap = new HashMap<>();
    try (CloseableIterator<PlanExecution> iterator = planExecutionService.fetchPlanExecutionsByStatusFromAnalytics(
             StatusUtils.activeStatuses(), ImmutableSet.of(PlanExecutionKeys.accountId))) {
      while (iterator.hasNext()) {
        PlanExecution planExecution = iterator.next();
        PipelineExecutionMetric planExecutionMetric =
            PipelineExecutionMetric.builder()
                .accountId(planExecution.getSetupAbstractions().get(SetupAbstractionKeys.accountId))
                .build();
        metricMap.put(planExecutionMetric, metricMap.getOrDefault(planExecutionMetric, 0) + 1);
      }
    }

    for (Map.Entry<PipelineExecutionMetric, Integer> entry : metricMap.entrySet()) {
      Map<String, String> metricContextMap =
          ImmutableMap.<String, String>builder()
              .put(PmsEventMonitoringConstants.ACCOUNT_ID, entry.getKey().getAccountId())
              .build();

      try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
        metricService.recordMetric(PLAN_EXECUTION_ACTIVE_COUNT, entry.getValue());
      }
    }
  }
}
