/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.handlers;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.pms.data.PmsOutcomeService;
import io.harness.engine.pms.data.PmsSweepingOutputService;
import io.harness.execution.expansion.PlanExpansionService;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;

import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineExecutionEndEventHandler implements OrchestrationEventHandler {
  // Considering 32 days though max we support 30 days, added buffer of 2 days to prevent any discrepancy
  public static final long TTL_DAYS = 32;
  @Inject PlanService planService;

  @Inject NodeExecutionService nodeExecutionService;
  @Inject PlanExpansionService planExpansionService;
  @Inject PmsGraphStepDetailsService pmsGraphStepDetailsService;
  @Inject PmsOutcomeService pmsOutcomeService;
  @Inject PmsSweepingOutputService pmsSweepingOutputService;
  @Inject PlanExecutionMetadataService planExecutionMetadataService;
  @Inject PlanExecutionService planExecutionService;
  @Inject PmsExecutionSummaryService pmsExecutionSummaryService;

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Date ttlExpiryDate = Date.from(OffsetDateTime.now().plusDays(TTL_DAYS).toInstant());

    String planExecutionId = event.getAmbiance().getPlanExecutionId();
    String planId = event.getAmbiance().getPlanId();
    planService.updateTTLForNodesForGivenPlanId(planId, ttlExpiryDate);
    planService.updateTTLForPlans(planId, ttlExpiryDate);

    pmsOutcomeService.updateTTL(planExecutionId, ttlExpiryDate);
    pmsSweepingOutputService.updateTTL(planExecutionId, ttlExpiryDate);
    nodeExecutionService.updateTTLForNodeExecution(planExecutionId, ttlExpiryDate);

    planExpansionService.updateTTL(planExecutionId, ttlExpiryDate);
    pmsGraphStepDetailsService.updateTTLForNodesForGivenPlanExecutionId(planExecutionId, ttlExpiryDate);

    planExecutionService.updateTTL(planExecutionId, ttlExpiryDate);
    planExecutionMetadataService.updateTTL(planExecutionId, ttlExpiryDate);
    pmsExecutionSummaryService.updateTTL(planExecutionId, ttlExpiryDate);
  }
}
