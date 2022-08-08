/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.approval;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.ApprovalDetails;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@OwnedBy(HarnessTeam.CDC)
public class ApproveAndRejectPreviousDeploymentsBody {
  private ApprovalDetails approvalDetails;
  private PreviousApprovalDetails previousApprovalDetails;

  public ApproveAndRejectPreviousDeploymentsBody(
      ApprovalDetails approvalDetails, PreviousApprovalDetails previousApprovalDetails) {
    this.approvalDetails = approvalDetails;
    this.previousApprovalDetails = previousApprovalDetails;
  }
}
