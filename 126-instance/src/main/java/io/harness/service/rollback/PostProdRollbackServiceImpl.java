/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.rollback;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.rollback.PostProdRollbackCheckDTO;
import io.harness.dtos.rollback.PostProdRollbackResponseDTO;

@OwnedBy(HarnessTeam.CDP)
public class PostProdRollbackServiceImpl implements PostProdRollbackService {
  @Override
  public PostProdRollbackCheckDTO checkIfRollbackAllowed(String instanceUuid) {
    return PostProdRollbackCheckDTO.builder().isRollbackAllowed(true).build();
  }

  @Override
  public PostProdRollbackResponseDTO triggerRollback(String instanceUuid) {
    return PostProdRollbackResponseDTO.builder()
        .isRollbackTriggered(true)
        .instanceUuid(instanceUuid)
        .planExecutionId("planExecutionId")
        .build();
  }
}
