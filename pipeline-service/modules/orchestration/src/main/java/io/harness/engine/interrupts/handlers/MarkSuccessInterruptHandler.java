/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.pms.contracts.execution.Status.SUCCEEDED;

import io.harness.annotations.dev.OwnedBy;
import io.harness.interrupts.Interrupt;
import io.harness.pms.contracts.execution.Status;

import java.util.EnumSet;
import javax.validation.Valid;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class MarkSuccessInterruptHandler extends MarkStatusInterruptHandler {
  @Override
  public Interrupt handleInterruptForNodeExecution(
      @NonNull @Valid Interrupt interrupt, @NonNull String nodeExecutionId) {
    return super.handleInterruptStatus(interrupt, nodeExecutionId, SUCCEEDED,
        EnumSet.of(
            Status.FAILED, Status.EXPIRED, Status.ERRORED, Status.INTERVENTION_WAITING, Status.APPROVAL_REJECTED));
  }
}
