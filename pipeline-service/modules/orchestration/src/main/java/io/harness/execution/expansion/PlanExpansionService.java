/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.execution.expansion;

import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepparameters.PmsStepParameters;

import java.util.List;
import java.util.Map;

public interface PlanExpansionService {
  /**
   * Adds stepParameters to the execution json
   * @param ambiance
   * @param stepInputs
   */
  void addStepInputs(Ambiance ambiance, PmsStepParameters stepInputs);

  /**
   * Adds outcome for a given node in its execution json
   * @param ambiance
   * @param name
   * @param stepInputs
   */
  void addOutcomes(Ambiance ambiance, String name, PmsOutcome stepInputs);

  /**
   * Creates a skeleton for planExpansionEntity
   * @param planExecutionId
   */
  void create(String planExecutionId);

  Map<String, Object> resolveExpressions(Ambiance ambiance, List<String> expressions);

  /**
   * Updates the status for given nodeExecution in Ambiance
   * @param ambiance
   * @param status
   */
  void updateStatus(Ambiance ambiance, Status status);
}
