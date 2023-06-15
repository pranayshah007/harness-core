/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.steps.approval.step.beans.ApprovalInstanceDetailsDTO;
import io.harness.steps.approval.step.beans.ApprovalUserGroupDTO;

import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("HarnessApprovalInstanceDetails")
@Schema(name = "HarnessApprovalInstanceDetails", description = "This contains details of Harness Approval Instance")
public class HarnessApprovalInstanceDetailsDTO implements ApprovalInstanceDetailsDTO {
  @NotNull String approvalMessage;
  boolean includePipelineExecutionHistory;
  @NotNull ApproversDTO approvers;
  List<HarnessApprovalActivity> approvalActivities;
  List<ApproverInputInfoDTO> approverInputs;
  List<ApprovalUserGroupDTO> validatedApprovalUserGroups;
  boolean isAutoRejectEnabled;
}
