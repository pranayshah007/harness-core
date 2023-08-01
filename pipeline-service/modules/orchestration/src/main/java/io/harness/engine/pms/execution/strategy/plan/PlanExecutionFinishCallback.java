package io.harness.engine.pms.execution.strategy.plan;

import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import java.util.Map;
import lombok.Builder;

@Builder
public class PlanExecutionFinishCallback implements OldNotifyCallback {
  @Inject private PlanExecutionStrategy planExecutionStrategy;

  Ambiance ambiance;
  @Override
  public void notify(Map<String, ResponseData> response) {
    planExecutionStrategy.endNodeExecution(ambiance);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    planExecutionStrategy.endNodeExecution(ambiance);
  }
}
