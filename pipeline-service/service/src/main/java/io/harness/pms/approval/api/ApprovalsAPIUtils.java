/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.pipeline.v1.model.ApprovalInstanceResponseBody;
import io.harness.spec.server.pipeline.v1.model.ApprovalInstanceResponseBody.StatusEnum;
import io.harness.spec.server.pipeline.v1.model.ApprovalInstanceResponseBody.TypeEnum;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;

import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class ApprovalsAPIUtils {
  public ApprovalInstanceResponseBody toApprovalInstanceResponseBody(
      ApprovalInstanceResponseDTO approvalInstanceResponseDTO) {
    if (isNull(approvalInstanceResponseDTO)) {
      return null;
    }

    ApprovalInstanceResponseBody approvalInstanceResponseBody = new ApprovalInstanceResponseBody();
    approvalInstanceResponseBody.setId(approvalInstanceResponseDTO.getId());
    approvalInstanceResponseBody.setType(toTypeEnum(approvalInstanceResponseDTO.getType()));
    approvalInstanceResponseBody.setStatus(toStatusEnum(approvalInstanceResponseDTO.getStatus()));
    approvalInstanceResponseBody.setDeadline(approvalInstanceResponseDTO.getDeadline());
    approvalInstanceResponseBody.setDetails(approvalInstanceResponseDTO.getDetails());
    approvalInstanceResponseBody.setCreated(approvalInstanceResponseDTO.getCreatedAt());
    approvalInstanceResponseBody.setUpdated(approvalInstanceResponseDTO.getLastModifiedAt());
    approvalInstanceResponseBody.setErrorMessage(approvalInstanceResponseDTO.getErrorMessage());

    return approvalInstanceResponseBody;
  }

  public TypeEnum toTypeEnum(ApprovalType approvalType) {
    switch (approvalType) {
      case HARNESS_APPROVAL:
        return TypeEnum.HARNESSAPPROVAL;
      case JIRA_APPROVAL:
        return TypeEnum.JIRAAPPROVAL;
      case SERVICENOW_APPROVAL:
        return TypeEnum.SERVICENOWAPPROVAL;
      case CUSTOM_APPROVAL:
        return TypeEnum.CUSTOMAPPROVAL;
      default:
        return null;
    }
  }

  public StatusEnum toStatusEnum(ApprovalStatus approvalStatus) {
    switch (approvalStatus) {
      case WAITING:
        return StatusEnum.WAITING;
      case APPROVED:
        return StatusEnum.APPROVED;
      case REJECTED:
        return StatusEnum.REJECTED;
      case FAILED:
        return StatusEnum.FAILED;
      case ABORTED:
        return StatusEnum.ABORTED;
      case EXPIRED:
        return StatusEnum.EXPIRED;
      default:
        return null;
    }
  }
}
