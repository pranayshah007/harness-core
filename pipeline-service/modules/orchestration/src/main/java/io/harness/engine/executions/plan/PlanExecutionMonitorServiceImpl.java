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
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionMonitorServiceImpl implements PlanExecutionMonitorService {
  private static final String ACTIVE_EXECUTION_COUNT_METRIC_NAME = "active_execution_count";

  @Inject private PlanExecutionService planExecutionService;
  @Inject private MetricService metricService;

  @Override
  public void registerActiveExecutionMetrics() {
    List<PlanExecution> planExecutions = planExecutionService.findByStatusWithProjections(
        StatusUtils.activeStatuses(), ImmutableSet.of(PlanExecutionKeys.setupAbstractions, PlanExecutionKeys.metadata));
    Map<PlanExecutionMetric, Integer> metricMap = new HashMap<>();

    for (PlanExecution planExecution : planExecutions) {
      PlanExecutionMetric planExecutionMetric =
          PlanExecutionMetric.builder()
              .accountId(planExecution.getSetupAbstractions().get(SetupAbstractionKeys.accountId))
              .orgIdentifier(planExecution.getSetupAbstractions().get(SetupAbstractionKeys.orgIdentifier))
              .projectId(planExecution.getSetupAbstractions().get(SetupAbstractionKeys.projectIdentifier))
              .build();

      metricMap.put(planExecutionMetric, metricMap.getOrDefault(planExecutionMetric, 0) + 1);
    }

    for (Map.Entry<PlanExecutionMetric, Integer> entry : metricMap.entrySet()) {
      Map<String, String> metricContextMap =
          ImmutableMap.<String, String>builder()
              .put(PmsEventMonitoringConstants.ACCOUNT_ID, entry.getKey().getAccountId())
              .put(PmsEventMonitoringConstants.ORG_ID, entry.getKey().getOrgIdentifier())
              .put(PmsEventMonitoringConstants.PROJECT_ID, entry.getKey().getProjectId())
              .build();

      try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
        metricService.recordMetric(ACTIVE_EXECUTION_COUNT_METRIC_NAME, entry.getValue());
      }
    }
  }
}
