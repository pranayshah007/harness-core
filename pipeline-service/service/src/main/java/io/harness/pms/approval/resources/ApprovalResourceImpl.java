/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.resources;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.approval.ApprovalResourceService;
import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalInstanceAuthorizationDTO;

import com.google.inject.Inject;
import java.io.IOException;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@PipelineServiceAuth
@Slf4j
public class ApprovalResourceImpl implements ApprovalResource {
  private final ApprovalResourceService approvalResourceService;

  public static final String APPROVAL_PARAM_MESSAGE = "Approval Identifier for the entity";

  @Inject
  public ApprovalResourceImpl(ApprovalResourceService approvalResourceService) {
    this.approvalResourceService = approvalResourceService;
  }

  public ResponseDTO<ApprovalInstanceResponseDTO> getApprovalInstance(@NotEmpty String approvalInstanceId) {
    return ResponseDTO.newResponse(approvalResourceService.get(approvalInstanceId));
  }

  public ResponseDTO<ApprovalInstanceResponseDTO> addHarnessApprovalActivity(@AccountIdentifier String accountId,
      @NotEmpty String approvalInstanceId, @NotNull @Valid HarnessApprovalActivityRequestDTO request) {
    return ResponseDTO.newResponse(approvalResourceService.addHarnessApprovalActivity(approvalInstanceId, request));
  }

  public ResponseDTO<HarnessApprovalInstanceAuthorizationDTO> getHarnessApprovalInstanceAuthorization(
      @NotEmpty String approvalInstanceId) {
    return ResponseDTO.newResponse(
        approvalResourceService.getHarnessApprovalInstanceAuthorization(approvalInstanceId, false));
  }

  public ResponseDTO<String> getInitialStageYamlSnippet(@NotNull ApprovalType approvalType, String routingId)
      throws IOException {
    return ResponseDTO.newResponse(approvalResourceService.getYamlSnippet(approvalType, routingId));
  }
}
