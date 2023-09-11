/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.stage;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.Scope;
import io.harness.execution.stage.StageExecutionEntity;
import io.harness.execution.stage.StageExecutionEntityUpdateDTO;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;

import java.util.Map;
import javax.validation.constraints.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_DASHBOARD})
@OwnedBy(CDP)
public interface StageExecutionEntityService {
  /**
   * Create stage execution entity.
   *
   * @param ambiance ambiance
   * @param stageElementParameters stage parameters
   * @return stage execution entity
   */
  StageExecutionEntity createStageExecutionEntity(Ambiance ambiance, StageElementParameters stageElementParameters);

  /**
   * Update stage execution entity.
   *
   * @param ambiance ambiance
   * @param stageExecutionEntityUpdateDTO the update details
   * @return stage execution entity
   */
  StageExecutionEntity updateStageExecutionEntity(
      Ambiance ambiance, StageExecutionEntityUpdateDTO stageExecutionEntityUpdateDTO);

  /**
   * Find stage execution entity by stageExecutionId
   *
   * @param accountIdentifier account id
   * @param orgIdentifier org id
   * @param projectIdentifier project id
   * @param stageExecutionId The stageExecutionId
   * @return stage execution entity
   */
  StageExecutionEntity findStageExecutionEntity(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String stageExecutionId);

  /**
   * Update stage execution entity.
   *
   * Uses- unique_stage_execution_entity_idx index
   *
   * @param scope scope
   * @param stageExecutionId stage execution id
   * @param updates updates
   */
  void update(Scope scope, String stageExecutionId, Map<String, Object> updates);

  /**
   * Update stage execution entity status
   *
   * Uses- unique_stage_execution_entity_idx index
   *
   * @param scope scope
   * @param stageExecutionId stage execution id
   * @param status stage status
   */
  void updateStatus(@NotNull Scope scope, @NotNull String stageExecutionId, Status status);
}
