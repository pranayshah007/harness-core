package io.harness.repositories;

import io.harness.execution.PlanExecution;

import java.util.List;

public interface PlanExecutionRepositoryCustom {
  PlanExecution getWithProjectionsWithoutUuid(String planExecutionId, List<String> fieldNames);
}
