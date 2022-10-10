/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan;

import static io.harness.rule.OwnerRule.SHALINI;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.execution.PlanExecution;
import io.harness.metrics.service.api.MetricService;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionMonitorServiceImplTest extends OrchestrationTestBase {
  @Mock PlanExecutionService planExecutionService;
  @Mock MetricService metricService;
  @Inject @InjectMocks PlanExecutionMonitorService planExecutionMonitorService;
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRegisterActiveExecutionMetrics() {
    List<PlanExecution> planExecutions = new ArrayList<>();
    planExecutions.add(
        PlanExecution.builder()
            .uuid("UUID1")
            .setupAbstractions(ImmutableMap.of(SetupAbstractionKeys.accountId, "accId1",
                SetupAbstractionKeys.orgIdentifier, "orgId1", SetupAbstractionKeys.projectIdentifier, "projId1"))
            .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("PiD1").build())
            .build());
    planExecutions.add(
        PlanExecution.builder()
            .uuid("UUID2")
            .setupAbstractions(ImmutableMap.of(SetupAbstractionKeys.accountId, "accId2",
                SetupAbstractionKeys.orgIdentifier, "orgId2", SetupAbstractionKeys.projectIdentifier, "projId2"))
            .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("PiD2").build())
            .build());
    planExecutions.add(
        PlanExecution.builder()
            .uuid("UUID1")
            .setupAbstractions(ImmutableMap.of(SetupAbstractionKeys.accountId, "accId3",
                SetupAbstractionKeys.orgIdentifier, "orgId3", SetupAbstractionKeys.projectIdentifier, "projId3"))
            .metadata(ExecutionMetadata.newBuilder().setPipelineIdentifier("PiD3").build())
            .build());
    doReturn(planExecutions).when(planExecutionService).findByStatusWithProjections(any(), any());
    planExecutionMonitorService.registerActiveExecutionMetrics();
    verify(metricService, times(3)).incCounter(anyString());
  }
}
