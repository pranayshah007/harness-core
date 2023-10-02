/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PipelineExecutionMetric;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.events.PmsEventMonitoringConstants;
import io.harness.pms.events.base.PmsMetricContextGuard;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.pms.plan.execution.SetupAbstractionKeys;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(HarnessTeam.PIPELINE)
public class NodeExecutionMonitorServiceImpl implements NodeExecutionMonitorService {
  private static final String NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME = "node_execution_active_count";

  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private MetricService metricService;

  @Override
  public void registerActiveExecutionMetrics() {
    Map<PipelineExecutionMetric, Integer> metricMap = new HashMap<>();
    try (CloseableIterator<NodeExecution> iterator =
             nodeExecutionService.fetchAllNodeExecutionsByStatusIteratorFromAnalytics(
                 StatusUtils.activeStatuses(), Set.of(NodeExecutionKeys.accountId))) {
      while (iterator.hasNext()) {
        NodeExecution nodeExecution = iterator.next();
        PipelineExecutionMetric pipelineExecutionMetric =
            PipelineExecutionMetric.builder()
                .accountId(nodeExecution.getAmbiance().getSetupAbstractionsMap().get(SetupAbstractionKeys.accountId))
                .build();

        metricMap.put(pipelineExecutionMetric, metricMap.getOrDefault(pipelineExecutionMetric, 0) + 1);
      }
    }

    for (Map.Entry<PipelineExecutionMetric, Integer> entry : metricMap.entrySet()) {
      Map<String, String> metricContextMap =
          ImmutableMap.<String, String>builder()
              .put(PmsEventMonitoringConstants.ACCOUNT_ID, entry.getKey().getAccountId())
              .build();

      try (PmsMetricContextGuard pmsMetricContextGuard = new PmsMetricContextGuard(metricContextMap)) {
        metricService.recordMetric(NODE_EXECUTION_ACTIVE_EXECUTION_COUNT_METRIC_NAME, entry.getValue());
      }
    }
  }
}
