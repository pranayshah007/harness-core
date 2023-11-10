/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.rule.OwnerRule.SHALINI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.monitoring.ExecutionCountWithAccountResult;
import io.harness.rule.Owner;

import java.util.LinkedList;
import java.util.List;
import javax.cache.Cache;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionMonitorServiceImplTest extends CategoryTest {
  @Mock PlanExecutionService planExecutionService;
  @Mock MetricService metricService;

  @Mock Cache<String, Integer> metricsCache;
  PlanExecutionMonitorService planExecutionMonitorService;

  @Before
  public void beforeTest() {
    MockitoAnnotations.openMocks(this);
    planExecutionMonitorService =
        new PlanExecutionMonitorServiceImpl(planExecutionService, metricService, metricsCache);
  }
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRegisterActiveExecutionMetrics() {
    doReturn(true).when(metricsCache).putIfAbsent(any(), any());

    List<ExecutionCountWithAccountResult> result = new LinkedList<>();
    result.add(ExecutionCountWithAccountResult.builder().metricKey("ABC").count(1).build());
    result.add(ExecutionCountWithAccountResult.builder().metricKey("DEF").count(5).build());

    doReturn(result).when(planExecutionService).aggregateRunningExecutionCountPerAccount();

    planExecutionMonitorService.registerActiveExecutionMetrics();
    verify(metricService, times(2)).recordMetric(anyString(), anyDouble());
  }
}
