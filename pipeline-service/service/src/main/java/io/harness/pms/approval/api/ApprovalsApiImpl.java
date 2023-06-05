/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static java.util.Objects.isNull;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.approval.ApprovalResourceService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.spec.server.pipeline.v1.ApprovalsApi;
import io.harness.spec.server.pipeline.v1.model.ApprovalInstanceResponseBody;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class ApprovalsApiImpl implements ApprovalsApi {
  private final ApprovalResourceService approvalResourceService;

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public Response getApprovalInstancesByExecutionId(@OrgIdentifier String org, @ProjectIdentifier String project,
      String executionId, @AccountIdentifier String harnessAccount, String approvalStatus, String approvalType,
      String nodeExecutionId) {
    if (isNull(harnessAccount)) {
      // harnessAccount is required to passed when using bearer token for rbac check
      throw new InvalidRequestException("harnessAccount param value is required");
    }
    log.info(String.format("Retrieving approval instances for execution-id %s in project %s, org %s, account %s",
        executionId, project, org, harnessAccount));
    ApprovalType approvalTypeEnum = null;
    ApprovalStatus approvalStatusEnum = null;
    if (!isNull(approvalType)) {
      approvalTypeEnum = ApprovalType.fromName(approvalType);
      if (isNull(approvalTypeEnum)) {
        throw new InvalidRequestException(
            String.format("approval_type param value should be one of %s", Arrays.toString(ApprovalType.values())));
      }
    }

    if (!isNull(approvalStatus)) {
      try {
        approvalStatusEnum = ApprovalStatus.valueOf(approvalStatus);
      } catch (IllegalArgumentException ex) {
        log.warn(ExceptionUtils.getMessage(ex));
        throw new InvalidRequestException(
            String.format("approval_status param value should be one of %s", Arrays.toString(ApprovalStatus.values())));
      }
    }
    List<ApprovalInstanceResponseDTO> approvalInstances = approvalResourceService.getApprovalInstancesByExecutionId(
        executionId, approvalStatusEnum, approvalTypeEnum, nodeExecutionId);

    List<ApprovalInstanceResponseBody> approvalInstanceResponseBodyList =
        approvalInstances.stream().map(ApprovalsAPIUtils::toApprovalInstanceResponseBody).collect(Collectors.toList());

    return Response.ok().entity(approvalInstanceResponseBodyList).build();
  }
}
