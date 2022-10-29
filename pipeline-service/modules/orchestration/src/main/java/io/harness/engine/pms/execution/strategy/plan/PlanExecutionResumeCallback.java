/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.engine.pms.execution.strategy.plan;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.PlanExecution;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.pms.contracts.execution.Status;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.PIPELINE)
@Data
@Builder
public class PlanExecutionResumeCallback implements OldNotifyCallback {
  private static final String PLAN_EXECUTION_START_CALLBACK_PREFIX = "PLAN_EXECUTION_START_CALLBACK%s/%s/%s/%s";

  String accountIdIdentifier;
  String projectIdentifier;
  String orgIdentifier;
  String pipelineIdentifier;
  @Inject private PlanExecutionService planExecutionService;
  @Inject private PlanService planService;
  @Inject private PlanExecutionStrategy planExecutionStrategy;

  @Inject PersistentLocker persistentLocker;

  @Override
  public void notify(Map<String, ResponseData> response) {
    notifyError(response);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    String lockName = String.format(PLAN_EXECUTION_START_CALLBACK_PREFIX, accountIdIdentifier, orgIdentifier,
        projectIdentifier, pipelineIdentifier);
    try (AcquiredLock<?> lock =
             persistentLocker.waitToAcquireLock(lockName, Duration.ofSeconds(10), Duration.ofSeconds(30))) {
      PlanExecution planExecution = planExecutionService.findNextExecutionToRun(
          accountIdIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
      if (planExecution != null) {
        planExecutionService.updateStatus(planExecution.getUuid(), Status.RUNNING);
        planExecutionStrategy.startPlanExecution(
            planService.fetchPlan(planExecution.getPlanId()), planExecution.getAmbiance());
      }
    }
  }
}
