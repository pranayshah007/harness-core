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
    for (PlanExecution planExecution : planExecutions) {
      Map<String, String> metricContextMap =
          ImmutableMap.<String, String>builder()
              .put(PmsEventMonitoringConstants.ACCOUNT_ID,
                  planExecution.getSetupAbstractions().get(SetupAbstractionKeys.accountId))
              .put(PmsEventMonitoringConstants.ORG_ID,
                  planExecution.getSetupAbstractions().get(SetupAbstractionKeys.orgIdentifier))
              .put(PmsEventMonitoringConstants.PROJECT_ID,
                  planExecution.getSetupAbstractions().get(SetupAbstractionKeys.projectIdentifier))
              .put(PmsEventMonitoringConstants.PIPELINE_IDENTIFIER, planExecution.getMetadata().getPipelineIdentifier())
              .build();
      try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
        metricService.incCounter(ACTIVE_EXECUTION_COUNT_METRIC_NAME);
      }
    }
  }
}
