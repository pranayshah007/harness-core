/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.handlers;

import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.PlanStatusUpdateObserver;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.pipeline.metadata.RecentExecutionsInfoHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ExecutionInfoUpdateEventHandler implements PlanStatusUpdateObserver {
  private final PlanExecutionService planExecutionService;
  private final RecentExecutionsInfoHelper recentExecutionsInfoHelper;

  @Inject
  public ExecutionInfoUpdateEventHandler(
      PlanExecutionService planExecutionService, RecentExecutionsInfoHelper recentExecutionsInfoHelper) {
    this.planExecutionService = planExecutionService;
    this.recentExecutionsInfoHelper = recentExecutionsInfoHelper;
  }

  @Override
  public void onPlanStatusUpdate(Ambiance ambiance) {
    String planExecutionId = ambiance.getPlanExecutionId();
    PlanExecution planExecution = planExecutionService.get(planExecutionId);

    recentExecutionsInfoHelper.onExecutionUpdate(ambiance, planExecution);
  }
}
