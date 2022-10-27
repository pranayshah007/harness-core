package io.harness.engine.pms.execution.strategy.plan;

import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.execution.PlanExecution;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

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
        planExecutionStrategy.startPlanExecution(
            planService.fetchPlan(planExecution.getPlanId()), planExecution.getAmbiance());
      }
    }
  }
}
