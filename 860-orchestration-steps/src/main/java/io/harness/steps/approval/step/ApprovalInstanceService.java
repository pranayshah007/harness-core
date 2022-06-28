/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.servicenow.TicketNG;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@OwnedBy(CDC)
public interface ApprovalInstanceService {
  ApprovalInstance save(@NotNull ApprovalInstance instance);

  ApprovalInstance get(@NotNull String approvalInstanceId);

  HarnessApprovalInstance getHarnessApprovalInstance(@NotNull String approvalInstanceId);

  void delete(@NotNull String approvalInstanceId);

  void resetNextIterations(@NotNull String approvalInstanceId, List<Long> nextIterations);

  void expireByNodeExecutionId(@NotNull String approvalInstanceId);

  void markExpiredInstances();

  void finalizeStatus(@NotNull String approvalInstanceId, ApprovalStatus status, String errorMessage);

  void finalizeStatus(@NotNull String approvalInstanceId, ApprovalStatus status, TicketNG ticketNG);

  void finalizeStatus(@NotNull String approvalInstanceId, ApprovalStatus status);

  void finalizeStatus(
      @NotNull String approvalInstanceId, ApprovalStatus status, String errorMessage, TicketNG ticketNG);

  HarnessApprovalInstance addHarnessApprovalActivity(@NotNull String approvalInstanceId, @NotNull EmbeddedUser user,
      @NotNull @Valid HarnessApprovalActivityRequestDTO request);
}
