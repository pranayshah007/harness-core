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
   * @param executionId execution id
   * @param stageStatus stage status
   */
  void updateStatus(Scope scope, String executionId, StageStatus stageStatus);

  /**
   * Update stage execution info.
   *
   * @param scope the scope
   * @param stageExecutionId stage execution id
   * @param updates updates map
   */
  void update(Scope scope, String stageExecutionId, Map<String, Object> updates);

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
   * @param executionId execution id
   * @param limit response limit
   * @return stage execution info
   */
  List<StageExecutionInfo> listLatestSuccessfulStageExecutionInfo(
      ExecutionInfoKey executionInfoKey, String executionId, int limit);
}
