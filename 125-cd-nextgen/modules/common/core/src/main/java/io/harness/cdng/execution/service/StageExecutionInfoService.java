/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.StageExecutionInfoUpdateDTO;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.utils.StageStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@OwnedBy(CDP)
public interface StageExecutionInfoService {
  /**
   * Save stage execution info.
   *
   * @param stageExecutionInfo the stage execution info
   * @return stage execution info
   */
  StageExecutionInfo save(StageExecutionInfo stageExecutionInfo);

  /**
   * Update stage execution status.
   *
   * @param scope the scope
   * @param stageExecutionId execution id
   * @param stageStatus stage status
   */
  void updateStatus(Scope scope, String stageExecutionId, StageStatus stageStatus);

  /**
   * Update stage execution info.
   *
   * @param scope the scope
   * @param stageExecutionId stage execution id
   * @param updates updates map
   */
  void update(Scope scope, String stageExecutionId, Map<String, Object> updates);

  /**
   * Update stage execution info only once per stage execution id.
   *
   * @param scope the scope
   * @param stageExecutionId stage execution id
   * @param updates updates map
   */
  void updateOnce(Scope scope, String stageExecutionId, Map<String, Object> updates);

  /**
   * Delete stage status keys lock. This method works in correlation with updateOnce.
   * When calling updateOnce method, the lock in the concurrent map will be created and needs to be deleted when it is
   * not needed anymore. However, if the caller forgets to call the following method the lock will be auto-deleted after
   * the expired time set on the map
   *
   * @param scope the scope
   * @param stageExecutionId stage execution id
   */
  void deleteStageStatusKeyLock(Scope scope, String stageExecutionId);

  /**
   *  Get the latest successful stage execution info.
   *
   * @param executionInfoKey the stage execution key
   * @param executionId execution id
   * @return stage execution info
   */
  Optional<StageExecutionInfo> getLatestSuccessfulStageExecutionInfo(
      ExecutionInfoKey executionInfoKey, String executionId);

  /**
   *  List the latest successful stage execution info.
   *
   * @param executionInfoKey the stage execution key
   * @param stageExecutionId execution id
   * @param limit response limit
   * @return stage execution info
   */
  List<StageExecutionInfo> listLatestSuccessfulStageExecutionInfo(
      ExecutionInfoKey executionInfoKey, String stageExecutionId, int limit);

  /**
   * Delete stage execution by scope
   * @param scope
   */
  void deleteAtAllScopes(Scope scope);

  StageExecutionInfo findStageExecutionInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String stageExecutionId);

  StageExecutionInfo createStageExecutionInfo(
      Ambiance ambiance, StageElementParameters stageElementParameters, Level stageLevel);

  StageExecutionInfo updateStageExecutionInfo(
      Ambiance ambiance, StageExecutionInfoUpdateDTO stageExecutionInfoUpdateDTO);

  Optional<StageExecutionInfo> findById(String id);

  void delete(String id);
}
