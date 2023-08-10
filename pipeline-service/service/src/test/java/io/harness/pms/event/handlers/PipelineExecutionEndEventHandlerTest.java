/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.handlers;

import static io.harness.rule.OwnerRule.ARCHIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.OrchestrationEventType;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExecutionEndEventHandlerTest extends CategoryTest {
  @Mock PlanService planService;

  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanExpansionService planExpansionService;
  @Mock PmsGraphStepDetailsService pmsGraphStepDetailsService;
  @Mock PmsOutcomeService pmsOutcomeService;
  @Mock PmsSweepingOutputService pmsSweepingOutputService;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock PlanExecutionService planExecutionService;
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;

  @InjectMocks PipelineExecutionEndEventHandler pipelineExecutionEndEventHandler;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestHandleEvent() {
    OrchestrationEvent event = OrchestrationEvent.builder()
                                   .ambiance(Ambiance.newBuilder()
                                                 .setPlanExecutionId(UUIDGenerator.generateUuid())
                                                 .setPlanId(UUIDGenerator.generateUuid())
                                                 .build())
                                   .eventType(OrchestrationEventType.ORCHESTRATION_END)
                                   .build();
    pipelineExecutionEndEventHandler.handleEvent(event);
    verify(planService, times(1)).updateTTLForNodesForGivenPlanId(any(), any());
    verify(planService, times(1)).updateTTLForPlans(any(), any());
    verify(nodeExecutionService, times(1)).updateTTLForNodeExecution(any(), any());
    verify(planExpansionService, times(1)).updateTTL(any(), any());
    verify(pmsExecutionSummaryService, times(1)).updateTTL(any(), any());
    verify(planExecutionMetadataService, times(1)).updateTTL(any(), any());
    verify(planExecutionService, times(1)).updateTTL(any(), any());
    verify(pmsGraphStepDetailsService, times(1)).updateTTLForNodesForGivenPlanExecutionId(any(), any());
    verify(pmsOutcomeService, times(1)).updateTTL(any(), any());
    verify(pmsSweepingOutputService, times(1)).updateTTL(any(), any());
  }
}