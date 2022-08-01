/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.rollback.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.rollback.RollbackData;
import io.harness.utils.StageStatus;

import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@OwnedBy(CDP)
public interface RollbackDataService {
  /**
   * Save rollback data.
   *
   * @param rollbackData rollback deployment info
   * @return rollback data
   */
  RollbackData save(RollbackData rollbackData);

  /**
   * Get the latest rollback data based on stage status.
   *
   * @param rollbackDeploymentInfoKey rollback key
   * @param stageStatus stage status
   * @return rollback data
   */
  Optional<RollbackData> getLatestRollbackData(String rollbackDeploymentInfoKey, StageStatus stageStatus);

  /**
   * List rollback data.
   *
   * @param key rollback deployment info key
   * @param stageStatus stage status
   * @param limit result limit
   * @return rollback RollbackData
   */
  List<RollbackData> listLatestRollbackData(String key, StageStatus stageStatus, int limit);

  /**
   * Get rollback deployment info.
   *
   * @param executionId key
   * @param stageStatus stage status
   */
  void updateStatus(String executionId, StageStatus stageStatus);
}
