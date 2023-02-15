package io.harness.repositories.planExecutionJson;

import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepparameters.PmsStepParameters;

public interface PlanExpansionService {
  void addInputsToJson(Ambiance ambiance, PmsStepParameters stepInputs);

  void addBasicInformationToJson(NodeExecution nodeExecution);

  void addOutcomesToJson(Ambiance ambiance, String name, PmsOutcome stepInputs);

  void createPlanExpansionEntity(String planExecutionId);

  String resolveExpression(String planExecutionId, String expression);
}
