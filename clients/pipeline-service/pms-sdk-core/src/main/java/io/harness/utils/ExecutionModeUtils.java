package io.harness.utils;

import io.harness.pms.contracts.plan.ExecutionMode;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ExecutionModeUtils {
  public boolean isRollbackMode(ExecutionMode executionMode) {
    return executionMode == ExecutionMode.POST_EXECUTION_ROLLBACK || executionMode == ExecutionMode.PIPELINE_ROLLBACK;
  }
}
