/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.step;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.execution.step.StepExecutionDetails;
import io.harness.execution.step.StepExecutionEntity;
import io.harness.execution.step.StepExecutionEntityUpdateDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;

import java.util.Map;
import javax.validation.constraints.NotNull;

@OwnedBy(CDP)
public interface StepExecutionEntityService {
  /**
   * Create step execution entity.
   *
   * @param ambiance ambiance
   * @param status step status
   * @return step execution entity
   */
  StepExecutionEntity createStepExecutionEntity(Ambiance ambiance, Status status);

  /**
   * Check if step execution entity exists.
   *
   * @param ambiance ambiance
   * @return boolean
   */
  boolean checkIfStepExecutionEntityExists(Ambiance ambiance);

  /**
   * Update step execution entity.
   *
   * @param ambiance ambiance
   * @param failureInfo failure Info
   * @param stepExecutionDetails execution details
   * @param stepName step name
   * @param status step status
   * @return step execution entity
   */
  StepExecutionEntity updateStepExecutionEntity(Ambiance ambiance, FailureInfo failureInfo,
      StepExecutionDetails stepExecutionDetails, String stepName, Status status);

  /**
   * Update step execution entity.
   *
   * @param ambiance ambiance
   * @param stepExecutionEntityUpdateDTO the update details
   * @param status step status
   * @return step execution entity
   */

  StepExecutionEntity updateStepExecutionEntity(
      Ambiance ambiance, StepExecutionEntityUpdateDTO stepExecutionEntityUpdateDTO, Status status);

  /**
   * Find step execution entity by stepExecutionId
   *
   * @param accountIdentifier account id
   * @param orgIdentifier org id
   * @param projectIdentifier project id
   * @param stepExecutionId The stepExecutionId
   * @return step execution entity
   */
  StepExecutionEntity findStepExecutionEntity(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String stepExecutionId);

  /**
   * Update step execution entity.
   *
   * Uses- unique_step_execution_info_idx index
   *
   * @param scope scope
   * @param stepExecutionId step execution id
   * @param updates updates
   */
  void update(Scope scope, String stepExecutionId, Map<String, Object> updates);

  /**
   * Update step execution entity status
   *
   * Uses- unique_step_execution_info_idx index
   *
   * @param scope scope
   * @param stepExecutionId step execution id
   * @param status step status
   */
  void updateStatus(@NotNull Scope scope, @NotNull String stepExecutionId, Status status);
}
